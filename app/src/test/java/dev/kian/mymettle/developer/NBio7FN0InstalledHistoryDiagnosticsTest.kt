package dev.kian.mymettle.developer

import dev.kian.mymettle.engine.inference.DynamicTransferContinuousPredictiveScore
import kotlin.test.Test
import kotlin.test.assertEquals

class NBio7FN0InstalledHistoryDiagnosticsTest {
    @Test
    fun `diagnostics preserve observation weighting domain split and history depth`() {
        val entries = listOf(
            entry(
                event = "event-a",
                sessions = 1,
                inside = true,
                score = score(nls = 1.0, pit = 0.10, covered = true),
                observationId = "observation-1",
            ),
            entry(
                event = "event-a",
                sessions = 1,
                inside = false,
                score = score(nls = 3.0, pit = 0.50, covered = false),
                observationId = "observation-2",
            ),
            entry(
                event = "event-b",
                sessions = 4,
                inside = true,
                score = score(nls = 5.0, pit = 0.90, covered = true),
                observationId = "observation-3",
            ),
        )

        val result = NBio7FN0InstalledHistoryAggregator.aggregate(entries)

        val overall = requireNotNull(result.overall)
        assertEquals(2, overall.eventCount)
        assertEquals(3, overall.observationCount)
        assertEquals(3.0, overall.aggregate.meanNegativeLogScore, 1e-12)
        assertEquals(2.0 / 3.0, overall.aggregate.coverage90, 1e-12)
        assertEquals(1, overall.pitReliability.lowThirdCount)
        assertEquals(1, overall.pitReliability.middleThirdCount)
        assertEquals(1, overall.pitReliability.highThirdCount)
        assertEquals(0.0, overall.pitReliability.meanAbsoluteThreeBinDeviation, 1e-12)

        val inside = requireNotNull(result.repetitionDomain.insideTrainingDomain)
        assertEquals(2, inside.eventCount)
        assertEquals(2, inside.observationCount)
        assertEquals(3.0, inside.aggregate.meanNegativeLogScore, 1e-12)

        val outside = requireNotNull(result.repetitionDomain.outsideTrainingDomain)
        assertEquals(1, outside.eventCount)
        assertEquals(1, outside.observationCount)
        assertEquals(3.0, outside.aggregate.meanNegativeLogScore, 1e-12)
        assertEquals(0, result.repetitionDomain.unknownTrainingDomainObservationCount)

        assertEquals(setOf("1", "4-5"), result.historyDepth.keys)
        assertEquals(2, result.historyDepth.getValue("1").observationCount)
        assertEquals(2.0, result.historyDepth.getValue("1").aggregate.meanNegativeLogScore, 1e-12)
        assertEquals(1, result.historyDepth.getValue("4-5").observationCount)
        assertEquals(5.0, result.historyDepth.getValue("4-5").aggregate.meanNegativeLogScore, 1e-12)
    }

    private fun entry(
        event: String,
        sessions: Int,
        inside: Boolean?,
        score: DynamicTransferContinuousPredictiveScore,
        observationId: String,
    ) = NBio7FN0DiagnosticEntry(
        eventIdentity = event,
        selectedIndependentSessionCount = sessions,
        observation = NBio7FN0ScoredObservationAudit(
            observationId = observationId,
            repetitions = 8.0,
            resistanceKg = 50.0,
            insideDestinationTrainingRepDomain = inside,
            score = score,
        ),
    )

    private fun score(
        nls: Double,
        pit: Double,
        covered: Boolean,
    ) = DynamicTransferContinuousPredictiveScore(
        p05ResistanceKg = 40.0,
        p50ResistanceKg = 50.0,
        p95ResistanceKg = 60.0,
        pit = pit,
        logPredictiveDensity = -nls,
        negativeLogScore = nls,
        crpsLogResistance = nls.coerceAtLeast(0.0) / 10.0,
        weightedIntervalScoreLogResistance = nls.coerceAtLeast(0.0) / 20.0,
        coverage90 = covered,
        intervalLogWidth = 0.4,
        medianAbsoluteErrorKg = 2.0,
        signedLogResidual = 0.1,
    )
}
