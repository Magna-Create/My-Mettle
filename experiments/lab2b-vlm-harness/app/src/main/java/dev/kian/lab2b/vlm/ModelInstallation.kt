package dev.kian.lab2b.vlm

import java.io.File
import java.security.MessageDigest

enum class InstallationPhase { NOT_INSTALLED, DOWNLOADING, VERIFYING, INSTALLED, FAILED, ROUTE_UNAVAILABLE }
data class Installation(val phase: InstallationPhase, val bytes: Long = 0, val totalBytes: Long = 0, val message: String? = null)
object Hashing {
    fun sha256(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return hex(digest.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    fun verify(file: File, asset: ModelAsset): Boolean = file.isFile && file.length() == asset.sizeBytes && sha256(file) == asset.sha256
}
class ModelInstallation(private val root: File) {
    init { check(root.isDirectory || root.mkdirs()) }
    fun directory(model: HarnessModelSpec) = File(root, model.id)
    fun staging(model: HarnessModelSpec) = File(root, "${model.id}.partial")
    fun installed(model: HarnessModelSpec): Boolean {
        val directory = directory(model)
        return File(directory, "VERIFIED").takeIf { it.isFile }?.readText() == model.fingerprint &&
            model.files.all { File(directory, it.name).let { f -> f.isFile && f.length() == it.sizeBytes } }
    }
    fun verifyAll(model: HarnessModelSpec, folder: File = directory(model)) {
        model.files.forEach { require(Hashing.verify(File(folder, it.name), it)) { "Integrity check failed: ${it.name}" } }
    }
    fun activate(model: HarnessModelSpec) {
        val stage = staging(model)
        verifyAll(model, stage)
        File(stage, "VERIFIED").writeText(model.fingerprint)
        check(!directory(model).exists()) { "Existing installation must be removed explicitly" }
        check(stage.renameTo(directory(model))) { "Atomic installation rename failed" }
    }
    fun remove(model: HarnessModelSpec) {
        check(directory(model).deleteRecursively() && staging(model).deleteRecursively()) { "Could not remove all model files" }
    }
}
