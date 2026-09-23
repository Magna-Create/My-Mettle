package dev.kian.mymettle.developer

import dev.kian.mymettle.engine.inference.DynamicTransferContinuousPredictiveScore
import dev.kian.mymettle.engine.inference.DynamicTransferPitReliabilitySummary
import dev.kian.mymettle.engine.inference.NBio7FPrequentialAggregationV1

/**
 * Descriptive N0-only diagnostics for installed-history development replay.
 *
 * This layer never changes the frozen N0 candidate, its evidence policy, its solver or any product
 * authority. It only stratifies already-scored held-out observations so later N0-vs-M0 comparisons
 * can distinguish support depth and destination-repetition extrapolation.
 */
data class NBio7FN0ScoredObservationAudit(
    val observationId: String,
    val repetitions: Double,
    val resistanceKg: Double,
    val insideDestinationTrainingRepDomain: Boolean?,
    val score: DynamicTransferContinuousPredictiveScore,
) {
    init {
        require(observationId.isNotBlank())
        require(repetitions.isFinite() && repetitions > 0.0)
        require(resistanceKg.isFinite() && resistanceKg > 0.0)
    }
}

data class NBio7FN0CrossProfileAggregate(
    val count: Int,
    val meanNegativeLogScore: Double,
    val meanCrpsLogResistance: Double,
    val meanWeightedIntervalScoreLogResistance: Double,
    val coverage90: Double,
    val meanIntervalLogWidth: Double,
    val meanSignedLogResidual: Double,
) {
    init {
        require(count > 0)
        require(
            listOf(
                meanNegativeLogScore,
                meanCrpsLogResistance,
                meanWeightedIntervalScoreLogResistance,
                coverage90,
                meanIntervalLogWidth,
                meanSignedLogResidual,
            ).all { it.isFinite() },
        )
        require(coverage90 in 0.0..1.0)
    }
}

data class NBio7FN0DiagnosticGroup(
    val eventCount: Int,
    val observationCount: Int,
    val aggregate: NBio7FN0CrossProfileAggregate,
    val pitReliability: DynamicTransferPitReliabilitySummary,
) {
    init {
        require(eventCount > 0)
        require(observationCount > 0)
        require(aggregate.count == observationCount)
        require(pitReliability.count == observationCount)
    }
}

data class NBio7FN0RepetitionDomainDiagnostics(
    val insideTrainingDomain: NBio7FN0DiagnosticGroup?,
    val outsideTrainingDomain: NBio7FN0DiagnosticGroup?,
    val unknownTrainingDomainObservationCount: Int,
) {
    init {
        require(unknownTrainingDomainObservationCount >= 0)
    }
}

data class NBio7FN0InstalledHistoryDiagnostics(
    val overall: NBio7FN0DiagnosticGroup?,
    val repetitionDomain: NBio7FN0RepetitionDomainDiagnostics,
    val historyDepth: Map<String, NBio7FN0DiagnosticGroup>,
)

internal data class NBio7FN0DiagnosticEntry(
    val eventIdentity: String,
    val selectedIndependentSessionCount: Int,
    val observation: NBio7FN0ScoredObservationAudit,
) {
    init {
        require(eventIdentity.isNotBlank())
        require(selectedIndependentSessionCount > 0)
    }
}

internal object NBio7FN0InstalledHistoryAggregator {
    fun fromEvents(events: List<NBio7FInstalledDestinationEventAudit>): NBio7FN0InstalledHistoryDiagnostics =
        aggregate(
            events.flatMap { event ->
                val eventIdentity = listOf(
                    event.sessionId,
                    event.destinationExecutionProfileVersionId,
                    event.side,
                ).joinToString("|")
                event.n0ScoredObservations.map { observation ->
                    NBio7FN0DiagnosticEntry(
                        eventIdentity = eventIdentity,
                        selectedIndependentSessionCount = event.n0SelectedIndependentSessionCount,
                        observation = observation,
                    )
                }
            },
        )

    internal fun aggregate(entries: List<NBio7FN0DiagnosticEntry>): NBio7FN0InstalledHistoryDiagnostics {
        val inside = entries.filter { it.observation.insideDestinationTrainingRepDomain == true }
        val outside = entries.filter { it.observation.insideDestinationTrainingRepDomain == false }
        val unknown = entries.count { it.observation.insideDestinationTrainingRepDomain == null }

        val depth = entries
            .groupBy { historyDepthBucket(it.selectedIndependentSessionCount) }
            .toSortedMap(compareBy(::historyDepthSortKey))
            .mapValues { (_, values) -> requireNotNull(summarise(values)) }

        return NBio7FN0InstalledHistoryDiagnostics(
            overall = summarise(entries),
            repetitionDomain = NBio7FN0RepetitionDomainDiagnostics(
                insideTrainingDomain = summarise(inside),
                outsideTrainingDomain = summarise(outside),
                unknownTrainingDomainObservationCount = unknown,
            ),
            historyDepth = depth,
        )
    }

    private fun historyDepthBucket(sessionCount: Int): String = when (sessionCount) {
        1 -> "1"
        2 -> "2"
        3 -> "3"
        in 4..5 -> "4-5"
        else -> "6+"
    }

    private fun historyDepthSortKey(value: String): Int = when (value) {
        "1" -> 1
        "2" -> 2
        "3" -> 3
        "4-5" -> 4
        "6+" -> 6
        else -> Int.MAX_VALUE
    }

    private fun summarise(entries: List<NBio7FN0DiagnosticEntry>): NBio7FN0DiagnosticGroup? {
        if (entries.isEmpty()) return null
        val scores = entries.map { it.observation.score }
        return NBio7FN0DiagnosticGroup(
            eventCount = entries.map { it.eventIdentity }.distinct().size,
            observationCount = scores.size,
            aggregate = NBio7FN0CrossProfileAggregate(
                count = scores.size,
                meanNegativeLogScore = scores.map { it.negativeLogScore }.average(),
                meanCrpsLogResistance = scores.map { it.crpsLogResistance }.average(),
                meanWeightedIntervalScoreLogResistance =
                    scores.map { it.weightedIntervalScoreLogResistance }.average(),
                coverage90 = scores.count { it.coverage90 }.toDouble() / scores.size,
                meanIntervalLogWidth = scores.map { it.intervalLogWidth }.average(),
                meanSignedLogResidual = scores.map { it.signedLogResidual }.average(),
            ),
            pitReliability = pitReliability(scores),
        )
    }

    private fun pitReliability(
        scores: List<DynamicTransferContinuousPredictiveScore>,
    ): DynamicTransferPitReliabilitySummary {
        val pits = scores.map { it.pit }
        val low = pits.count { it < 1.0 / 3.0 }
        val middle = pits.count { it >= 1.0 / 3.0 && it < 2.0 / 3.0 }
        val high = pits.size - low - middle
        val lowRate = low.toDouble() / pits.size
        val middleRate = middle.toDouble() / pits.size
        val highRate = high.toDouble() / pits.size
        val expected = 1.0 / NBio7FPrequentialAggregationV1.PIT_RELIABILITY_BINS
        return DynamicTransferPitReliabilitySummary(
            count = pits.size,
            lowThirdCount = low,
            middleThirdCount = middle,
            highThirdCount = high,
            lowThirdRate = lowRate,
            middleThirdRate = middleRate,
            highThirdRate = highRate,
            meanPit = pits.average(),
            meanAbsoluteThreeBinDeviation =
                (kotlin.math.abs(lowRate - expected) +
                    kotlin.math.abs(middleRate - expected) +
                    kotlin.math.abs(highRate - expected)) / 3.0,
        )
    }
}

data class NBio7FCanonicalEquipmentCoverageAudit(
    val equipmentInstanceCount: Int,
    val equipmentFactVersionCount: Int,
    val sessionActualEquipmentBindingCount: Int,
    val observationEquipmentOverrideCount: Int,
    val observationLoadSemanticsCount: Int,
) {
    init {
        require(
            listOf(
                equipmentInstanceCount,
                equipmentFactVersionCount,
                sessionActualEquipmentBindingCount,
                observationEquipmentOverrideCount,
                observationLoadSemanticsCount,
            ).all { it >= 0 },
        )
    }
}

internal fun NBio7FHistoricalEquipmentHistory.coverageAudit() = NBio7FCanonicalEquipmentCoverageAudit(
    equipmentInstanceCount = equipmentCreatedAt.size,
    equipmentFactVersionCount = factsByEquipment.values.sumOf { it.size },
    sessionActualEquipmentBindingCount = sessionBindings.size,
    observationEquipmentOverrideCount = observationOverrides.size,
    observationLoadSemanticsCount = loadSemantics.size,
)
