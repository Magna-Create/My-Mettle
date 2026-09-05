package dev.kian.lab2b.vlm

/** Network completion only permits verification; it never declares an installation ready. */
enum class TransferPhase { ACTIVE, PAUSED, SUCCESSFUL, FAILED }
data class Transfer(val phase: TransferPhase, val bytes: Long, val reason: Int = 0)
object DownloadState {
    fun reduce(required: List<Transfer?>, total: Long): Installation {
        require(required.isNotEmpty() && total > 0)
        val bytes = required.filterNotNull().sumOf { it.bytes.coerceAtLeast(0) }.coerceAtMost(total)
        required.filterNotNull().firstOrNull { it.phase == TransferPhase.FAILED }?.let {
            return Installation(InstallationPhase.FAILED, bytes, total, "Android DownloadManager reason=${it.reason}; Download retries failed files")
        }
        if (required.any { it == null }) return Installation(InstallationPhase.FAILED, bytes, total,
            "Download interrupted before all files queued; Download resumes missing files")
        if (required.all { it?.phase == TransferPhase.SUCCESSFUL }) return Installation(InstallationPhase.VERIFYING, bytes, total)
        val paused = required.filterNotNull().firstOrNull { it.phase == TransferPhase.PAUSED }
        return Installation(InstallationPhase.DOWNLOADING, bytes, total, paused?.let { "Android paused download; reason=${it.reason}" })
    }
}
