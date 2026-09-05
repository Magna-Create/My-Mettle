package dev.kian.lab2b.vlm

import org.junit.Assert.*
import org.junit.Test

class DownloadStateTest {
    @Test fun completedNetworkSetStillRequiresHashVerification() {
        val result = DownloadState.reduce(listOf(Transfer(TransferPhase.SUCCESSFUL, 50), Transfer(TransferPhase.SUCCESSFUL, 50)), 100)
        assertEquals(InstallationPhase.VERIFYING, result.phase)
        assertNotEquals(InstallationPhase.INSTALLED, result.phase)
    }
    @Test fun processDeathDuringQueueingDoesNotBecomeReady() {
        val result = DownloadState.reduce(listOf(Transfer(TransferPhase.SUCCESSFUL, 50), null), 100)
        assertEquals(InstallationPhase.FAILED, result.phase)
        assertTrue(result.message!!.contains("resumes missing"))
    }
    @Test fun failedAndPausedDownloadsKeepReasonAndProgress() {
        val failed = DownloadState.reduce(listOf(Transfer(TransferPhase.FAILED, 12, 1006)), 100)
        assertEquals(InstallationPhase.FAILED, failed.phase)
        assertTrue(failed.message!!.contains("1006"))
        val paused = DownloadState.reduce(listOf(Transfer(TransferPhase.SUCCESSFUL, 50), Transfer(TransferPhase.PAUSED, 12, 2)), 100)
        assertEquals(InstallationPhase.DOWNLOADING, paused.phase)
        assertEquals(62L, paused.bytes)
        assertTrue(paused.message!!.contains("paused"))
    }
}
