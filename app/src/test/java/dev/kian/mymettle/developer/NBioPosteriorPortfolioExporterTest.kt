package dev.kian.mymettle.developer

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NBioPosteriorPortfolioExporterTest {
    @Test
    fun sparseHistoryKeepsOnlyObservedSessionFinalisations() {
        val cutoffs = (0..3).map { Instant.parse("2026-08-01T00:00:00Z").plusSeconds(it * 86_400L) }
        assertEquals(cutoffs, NBioPosteriorPortfolioExporter.selectHistoricalCutoffs(cutoffs.reversed()))
    }

    @Test
    fun longHistoryUsesSixRealCutoffsIncludingBothEnds() {
        val cutoffs = (0..10).map { Instant.parse("2026-08-01T00:00:00Z").plusSeconds(it * 86_400L) }
        val result = NBioPosteriorPortfolioExporter.selectHistoricalCutoffs(cutoffs)
        assertEquals(listOf(0, 2, 4, 6, 8, 10).map { cutoffs[it] }, result)
        assertTrue(result.all { it in cutoffs })
    }

    @Test
    fun duplicateSessionCutoffsNeverGenerateExtraSnapshots() {
        val time = Instant.parse("2026-08-01T00:00:00Z")
        assertEquals(listOf(time), NBioPosteriorPortfolioExporter.selectHistoricalCutoffs(List(12) { time }))
    }
}
