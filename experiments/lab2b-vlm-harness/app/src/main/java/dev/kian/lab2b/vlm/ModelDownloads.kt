package dev.kian.lab2b.vlm

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

/** DownloadManager owns network work across process death. Only verified complete sets activate. */
class ModelDownloads(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(DownloadManager::class.java)
    val root = File(requireNotNull(app.getExternalFilesDir(null)), "lab2b/models").apply { mkdirs() }
    val installation = ModelInstallation(root)
    private val errors = app.getSharedPreferences("lab2b-download-errors", Context.MODE_PRIVATE)
    private data class Row(val id: Long, val status: Int, val bytes: Long, val reason: Int)

    private fun rows(): Map<String, Row> = buildMap {
        manager.query(DownloadManager.Query())?.use { cursor ->
            while (cursor.moveToNext()) {
                fun number(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) ?: continue
                val path = Uri.parse(uri).path ?: continue
                if (path.startsWith(root.absolutePath + "/")) put(path, Row(number(DownloadManager.COLUMN_ID),
                    number(DownloadManager.COLUMN_STATUS).toInt(), number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR).coerceAtLeast(0),
                    number(DownloadManager.COLUMN_REASON).toInt()))
            }
        }
    }
    @Synchronized fun start(model: HarnessModelSpec) {
        require(model.routeUnavailable == null) { model.routeUnavailable ?: "Route unavailable" }
        if (installation.installed(model)) return
        require(!installation.directory(model).exists()) { "Incomplete/corrupt installation; Remove then Download" }
        val stage = installation.staging(model).apply { mkdirs() }
        val existing = rows()
        val remaining = model.files.sumOf { asset -> (asset.sizeBytes - (existing[File(stage, asset.name).absolutePath]?.bytes ?: 0L)).coerceAtLeast(0) }
        require(root.usableSpace >= remaining + 256L * 1024 * 1024) { "Not enough free storage: need $remaining bytes plus 256 MB reserve" }
        errors.edit().remove(model.id).commit()
        try {
            model.files.forEach { asset ->
                val file = File(stage, asset.name)
                val row = existing[file.absolutePath]
                if (row != null && row.status != DownloadManager.STATUS_FAILED) return@forEach
                if (row != null) manager.remove(row.id)
                file.delete()
                val request = DownloadManager.Request(Uri.parse(model.url(asset)))
                    .setTitle("LAB-2B ${model.displayName}: ${asset.name}")
                    .setDescription("Pinned local model download; may be several GB")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationUri(Uri.fromFile(file))
                manager.enqueue(request)
            }
        } catch (e: Exception) {
            errors.edit().putString(model.id, "Download start failed: ${e.message}").commit()
            throw e
        }
    }
    @Synchronized fun refresh(model: HarnessModelSpec, onVerifying: () -> Unit = {}): Installation {
        if (model.routeUnavailable != null) return Installation(InstallationPhase.ROUTE_UNAVAILABLE, message = model.routeUnavailable)
        if (installation.installed(model)) return Installation(InstallationPhase.INSTALLED, model.sizeBytes, model.sizeBytes)
        errors.getString(model.id, null)?.let { return Installation(InstallationPhase.FAILED, totalBytes = model.sizeBytes, message = it) }
        if (installation.directory(model).exists()) return Installation(InstallationPhase.FAILED, message = "Installation marker or file sizes invalid; Remove then Download")
        val stage = installation.staging(model)
        if (!stage.exists()) return Installation(InstallationPhase.NOT_INSTALLED, totalBytes = model.sizeBytes)
        val downloads = rows()
        val required = model.files.map { downloads[File(stage, it.name).absolutePath] }
        val state = DownloadState.reduce(required.map { row -> row?.let {
            Transfer(when (it.status) {
                DownloadManager.STATUS_FAILED -> TransferPhase.FAILED
                DownloadManager.STATUS_SUCCESSFUL -> TransferPhase.SUCCESSFUL
                DownloadManager.STATUS_PAUSED -> TransferPhase.PAUSED
                else -> TransferPhase.ACTIVE
            }, it.bytes, it.reason)
        } }, model.sizeBytes)
        if (state.phase == InstallationPhase.VERIFYING) {
            onVerifying()
            return try {
                installation.activate(model)
                // Do not remove completed DownloadManager rows here: that can delete their files.
                Installation(InstallationPhase.INSTALLED, model.sizeBytes, model.sizeBytes)
            } catch (e: Exception) {
                errors.edit().putString(model.id, "Verification failed: ${e.message}; Remove then Download").commit()
                Installation(InstallationPhase.FAILED, state.bytes, model.sizeBytes, e.message)
            }
        }
        return state
    }
    @Synchronized fun remove(model: HarnessModelSpec) {
        val prefixes = listOf(installation.directory(model).absolutePath + "/", installation.staging(model).absolutePath + "/")
        rows().filterKeys { path -> prefixes.any(path::startsWith) }.values.forEach { manager.remove(it.id) }
        installation.remove(model)
        errors.edit().remove(model.id).commit()
    }
}
