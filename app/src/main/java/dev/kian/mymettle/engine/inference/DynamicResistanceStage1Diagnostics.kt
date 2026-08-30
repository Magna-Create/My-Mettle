package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationPolicy
import dev.kian.mymettle.domain.inference.DynamicHeldOutEvaluation
import dev.kian.mymettle.domain.inference.DynamicHeldOutStatus
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidencePolicy
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.performance.DynamicResistanceEvidenceProjector
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

data class WeightedPredictivePoint(val value: Double, val weight: Double) {
    init {
        require(value.isFinite())
        require(weight.isFinite() && weight >= 0.0)
    }
}

data class DeterministicCrpsDistribution internal constructor(
    private val values: DoubleArray,
    private val weights: DoubleArray,
    private val halfPairwiseAbsoluteExpectation: Double,
) {
    fun score(observed: Double): Double {
        require(observed.isFinite())
        var first = 0.0
        for (index in values.indices) first += weights[index] * abs(values[index] - observed)
        return max(0.0, first - halfPairwiseAbsoluteExpectation)
    }
}

object DeterministicWeightedCrps {
    fun distribution(points: List<WeightedPredictivePoint>): DeterministicCrpsDistribution {
        require(points.isNotEmpty())
        val total = points.sumOf { it.weight }
        require(total.isFinite() && total > 0.0)
        val sorted = points.sortedBy { it.value }
        val values = DoubleArray(sorted.size)
        val weights = DoubleArray(sorted.size)
        var prefixWeight = 0.0
        var prefixWeightedValue = 0.0
        var halfPairwise = 0.0
        sorted.forEachIndexed { index, point ->
            val weight = point.weight / total
            values[index] = point.value
            weights[index] = weight
            halfPairwise += weight * (point.value * prefixWeight - prefixWeightedValue)
            prefixWeight += weight
            prefixWeightedValue += weight * point.value
        }
        return DeterministicCrpsDistribution(values, weights, max(0.0, halfPairwise))
    }
}

enum class DynamicRecentTrendDirection(val storageValue: String) {
    UPWARD("upward"), STABLE("stable"), DOWNWARD("downward"), INSUFFICIENT("insufficient")
}

enum class DynamicRepDomainPosition(val storageValue: String) {
    INSIDE("inside"), BELOW("below"), ABOVE("above"), UNKNOWN("unknown")
}

enum class DynamicStage1TemporalLagVerdict(val storageValue: String) {
    TEMPORAL_LAG_SUPPORTED("temporal_lag_supported"),
    TEMPORAL_LAG_PLAUSIBLE_BUT_NOT_ISOLATED("temporal_lag_plausible_but_not_isolated"),
    TEMPORAL_LAG_NOT_SUPPORTED("temporal_lag_not_supported"),
    INSUFFICIENT_DIAGNOSTIC_EVIDENCE("insufficient_diagnostic_evidence"),
}

data class DynamicStage1DiagnosticPolicy(
    val semanticVersion: String = "n-bio-7b-candidate-v1-temporal-diagnostic-v2",
    val recentTrendSessionWindow: Int = 4,
    val minimumComparableTrendSessions: Int = 3,
    val stableTrendAbsoluteLogPerSession: Double = 0.01,
    val minimumGlobalEvaluableEvents: Int = 30,
    val minimumTrendClassifiedEvents: Int = 16,
    val minimumUpwardEvents: Int = 6,
    val minimumStableEvents: Int = 6,
    val minimumTrendCorrelationPairs: Int = 8,
    val minimumSerialPairs: Int = 8,
    val supportUpwardMedianResidual: Double = 0.02,
    val supportUpwardPositiveRate: Double = 0.65,
    val supportHighPitRate: Double = 0.50,
    val supportTrendResidualCorrelation: Double = 0.15,
    val supportStableResidualContrast: Double = 0.015,
    val supportStableHighPitContrast: Double = 0.10,
    val supportPositivePositiveAdjacentRate: Double = 0.40,
    val supportSerialLag1ResidualCorrelation: Double = 0.15,
) {
    init {
        require(semanticVersion.isNotBlank())
        require(minimumComparableTrendSessions >= 2)
        require(recentTrendSessionWindow >= minimumComparableTrendSessions)
        require(stableTrendAbsoluteLogPerSession.isFinite() && stableTrendAbsoluteLogPerSession >= 0.0)
        require(minimumGlobalEvaluableEvents > 0)
        require(minimumTrendClassifiedEvents > 0)
        require(minimumUpwardEvents > 0)
        require(minimumStableEvents > 0)
        require(minimumTrendCorrelationPairs >= 2)
        require(minimumSerialPairs >= 1)
        require(supportUpwardMedianResidual.isFinite())
        require(supportUpwardPositiveRate in 0.0..1.0)
        require(supportHighPitRate in 0.0..1.0)
        require(supportTrendResidualCorrelation in -1.0..1.0)
        require(supportStableResidualContrast.isFinite())
        require(supportStableHighPitContrast in 0.0..1.0)
        require(supportPositivePositiveAdjacentRate in 0.0..1.0)
        require(supportSerialLag1ResidualCorrelation in -1.0..1.0)
    }
}

data class DynamicStage1EventDiagnostic(
    val sessionOrdinal: Int,
    val repetitions: Int,
    val observedResistanceKg: Double,
    val priorIndependentSessionCount: Int,
    val priorRepMin: Int?,
    val priorRepMax: Int?,
    val repDomainPosition: DynamicRepDomainPosition,
    val predictiveP05Kg: Double,
    val predictiveP50Kg: Double,
    val predictiveP95Kg: Double,
    val predictiveLogWidth: Double,
    val pit: Double,
    val logPredictiveDensity: Double,
    val crpsLogResistance: Double,
    val frontierP05Kg: Double,
    val frontierP50Kg: Double,
    val frontierP95Kg: Double,
    val signedLogResidual: Double,
    val recentTrendLogPerSession: Double?,
    val recentTrendDirection: DynamicRecentTrendDirection,
    val recentTrendComparableSessions: Int,
    val previousSessionMedianSignedLogResiduals: List<Double>,
    val priorPositiveResidualSessionStreak: Int,
    val coveredByPredictiveInterval: Boolean,
    val catastrophicFrontierContradiction: Boolean,
)

data class DynamicStage1TrendGroupSummary(
    val count: Int,
    val meanSignedLogResidual: Double?,
    val medianSignedLogResidual: Double?,
    val positiveResidualProportion: Double?,
    val pitLowCount: Int,
    val pitMiddleCount: Int,
    val pitHighCount: Int,
    val highPitRate: Double?,
    val predictiveCoverage: Double?,
    val catastrophicContradictionRate: Double?,
    val meanPredictiveLogWidth: Double?,
    val meanCrpsLogResistance: Double?,
)

data class DynamicStage1SerialSummary(
    val profileSessionCount: Int,
    val adjacentPairCount: Int,
    val sameSignAdjacentRate: Double?,
    val positivePositiveAdjacentRate: Double?,
    val lag1ResidualCorrelation: Double?,
    val longestPositiveRun: Int,
)

data class DynamicStage1DiagnosticSummary(
    val policyId: String,
    val evaluableEventCount: Int,
    val meanSignedLogResidual: Double?,
    val medianSignedLogResidual: Double?,
    val positiveResidualProportion: Double?,
    val meanPredictiveLogWidth: Double?,
    val meanCrpsLogResistance: Double?,
    val trendClassifiedEventCount: Int,
    val trendResidualCorrelation: Double?,
    val byTrend: Map<DynamicRecentTrendDirection, DynamicStage1TrendGroupSummary>,
    val serial: DynamicStage1SerialSummary,
    val verdict: DynamicStage1TemporalLagVerdict,
    val limitations: List<String>,
)

data class DynamicStage1ProfileDiagnostics(
    val executionProfileVersionId: String,
    val side: String,
    val events: List<DynamicStage1EventDiagnostic>,
    val summary: DynamicStage1DiagnosticSummary,
)

object DynamicStage1DiagnosticAnalyzer {
    fun profile(
        profile: DynamicResistanceProfileSemantics,
        side: Laterality,
        observations: List<DynamicHeldOutEvaluation>,
        revisions: Collection<HistoricalCompletedSetEvidenceRevision>,
        evidencePolicy: DynamicResistanceEvidencePolicy,
        policy: DynamicStage1DiagnosticPolicy = DynamicStage1DiagnosticPolicy(),
    ): DynamicStage1ProfileDiagnostics {
        val evaluable = observations.filter { it.status == DynamicHeldOutStatus.EVALUABLE }
        val orderedSessions = observations.map { it.sessionId }.distinct().sortedBy { sessionId ->
            revisions.filter { it.evidence.sessionId == sessionId }.maxOfOrNull { it.sessionCompletedAt }
        }
        val ordinals = orderedSessions.withIndex().associate { it.value to it.index + 1 }
        val base = evaluable.map { event ->
            val cutoff = revisions.filter { it.evidence.sessionId == event.sessionId }
                .maxOf { it.sessionCompletedAt }
            val trainingRaw = HistoricalObservationRevisionSelector.currentAsOf(
                revisions.filter { it.evidence.sessionId in event.trainingSessionIds }, cutoff,
            ).filter {
                it.executionProfileVersionId == profile.executionProfileVersionId && it.laterality == side
            }
            val projection = DynamicResistanceEvidenceProjector.project(
                profile = profile, side = side, evidence = trainingRaw, policy = evidencePolicy,
            )
            val trendEvidence = projection.evidence
                .filter { it.repetitions == event.repetitions }
                .groupBy { it.sessionId }
                .map { (_, values) ->
                    values.maxOf { it.completedAt } to median(values.map { ln(it.resistance.value) })
                }
                .sortedBy { it.first }
                .takeLast(policy.recentTrendSessionWindow)
                .map { it.second }
            val trend = if (trendEvidence.size >= policy.minimumComparableTrendSessions) theilSen(trendEvidence) else null
            val direction = classifyTrend(trend, trendEvidence.size, policy)
            val predictive = requireNotNull(event.candidatePredictive)
            val frontier = requireNotNull(event.candidateFrontierAtRepetitions)
            val crps = requireNotNull(event.candidateCrpsLogResistance)
            val residual = ln(event.observedResistanceKg / predictive.p50ResistanceKg)
            val repDomain = projection.repDomain
            val domainPosition = when {
                repDomain == null -> DynamicRepDomainPosition.UNKNOWN
                event.repetitions < repDomain.first -> DynamicRepDomainPosition.BELOW
                event.repetitions > repDomain.last -> DynamicRepDomainPosition.ABOVE
                else -> DynamicRepDomainPosition.INSIDE
            }
            DynamicStage1EventDiagnostic(
                sessionOrdinal = requireNotNull(ordinals[event.sessionId]),
                repetitions = event.repetitions,
                observedResistanceKg = event.observedResistanceKg,
                priorIndependentSessionCount = projection.independentSessionCount,
                priorRepMin = repDomain?.first,
                priorRepMax = repDomain?.last,
                repDomainPosition = domainPosition,
                predictiveP05Kg = predictive.p05ResistanceKg,
                predictiveP50Kg = predictive.p50ResistanceKg,
                predictiveP95Kg = predictive.p95ResistanceKg,
                predictiveLogWidth = ln(predictive.p95ResistanceKg / predictive.p05ResistanceKg),
                pit = predictive.observedCdf,
                logPredictiveDensity = predictive.logPredictiveDensity,
                crpsLogResistance = crps,
                frontierP05Kg = frontier.p05,
                frontierP50Kg = frontier.p50,
                frontierP95Kg = frontier.p95,
                signedLogResidual = residual,
                recentTrendLogPerSession = trend,
                recentTrendDirection = direction,
                recentTrendComparableSessions = trendEvidence.size,
                previousSessionMedianSignedLogResiduals = emptyList(),
                priorPositiveResidualSessionStreak = 0,
                coveredByPredictiveInterval = predictive.contains(event.observedResistanceKg),
                catastrophicFrontierContradiction = (event.frontierAtOrAboveObservedProbability ?: 1.0) <
                    DynamicCapabilityValidationPolicy().descriptiveFrontierContradictionProbability,
            )
        }
        val sessionResiduals = base.groupBy { it.sessionOrdinal }
            .mapValues { (_, events) -> median(events.map { it.signedLogResidual }) }
            .toSortedMap()
        val enriched = base.map { event ->
            val previous = sessionResiduals.filterKeys { it < event.sessionOrdinal }.values.toList().takeLast(3)
            event.copy(
                previousSessionMedianSignedLogResiduals = previous,
                priorPositiveResidualSessionStreak = positiveTailStreak(previous),
            )
        }
        val summary = summarize(listOf(enriched), policy)
        return DynamicStage1ProfileDiagnostics(
            executionProfileVersionId = profile.executionProfileVersionId.value,
            side = side.storageValue,
            events = enriched.sortedWith(compareBy<DynamicStage1EventDiagnostic> { it.sessionOrdinal }.thenBy { it.repetitions }),
            summary = summary,
        )
    }

    fun aggregate(
        profiles: List<DynamicStage1ProfileDiagnostics>,
        policy: DynamicStage1DiagnosticPolicy = DynamicStage1DiagnosticPolicy(),
    ): DynamicStage1DiagnosticSummary = summarize(profiles.map { it.events }, policy)

    fun classifyTrend(
        slope: Double?,
        comparableSessions: Int,
        policy: DynamicStage1DiagnosticPolicy = DynamicStage1DiagnosticPolicy(),
    ): DynamicRecentTrendDirection = when {
        slope == null || comparableSessions < policy.minimumComparableTrendSessions -> DynamicRecentTrendDirection.INSUFFICIENT
        slope > policy.stableTrendAbsoluteLogPerSession -> DynamicRecentTrendDirection.UPWARD
        slope < -policy.stableTrendAbsoluteLogPerSession -> DynamicRecentTrendDirection.DOWNWARD
        else -> DynamicRecentTrendDirection.STABLE
    }

    fun theilSen(values: List<Double>): Double {
        require(values.size >= 2)
        val slopes = mutableListOf<Double>()
        for (left in 0 until values.lastIndex) {
            for (right in left + 1 until values.size) {
                slopes += (values[right] - values[left]) / (right - left).toDouble()
            }
        }
        return median(slopes)
    }

    private fun summarize(
        profileEvents: List<List<DynamicStage1EventDiagnostic>>,
        policy: DynamicStage1DiagnosticPolicy,
    ): DynamicStage1DiagnosticSummary {
        val events = profileEvents.flatten()
        val byTrend = DynamicRecentTrendDirection.entries.associateWith { direction ->
            trendSummary(events.filter { it.recentTrendDirection == direction })
        }
        val classified = events.filter { it.recentTrendDirection != DynamicRecentTrendDirection.INSUFFICIENT }
        val sessionPairs = profileEvents.flatMap { profile ->
            profile.groupBy { it.sessionOrdinal }.toSortedMap().map { (ordinal, items) ->
                Triple(ordinal, median(items.map { it.signedLogResidual }), items.mapNotNull { it.recentTrendLogPerSession }.medianOrNull())
            }
        }
        val trendPairs = sessionPairs.mapNotNull { (_, residual, trend) -> trend?.let { it to residual } }
        val serial = serialSummary(profileEvents, policy)
        val correlation = pearson(
            trendPairs.map { it.first },
            trendPairs.map { it.second },
            policy.minimumTrendCorrelationPairs,
        )
        val summary = DynamicStage1DiagnosticSummary(
            policyId = policy.semanticVersion,
            evaluableEventCount = events.size,
            meanSignedLogResidual = events.map { it.signedLogResidual }.meanOrNull(),
            medianSignedLogResidual = events.map { it.signedLogResidual }.medianOrNull(),
            positiveResidualProportion = proportion(events) { it.signedLogResidual > 0.0 },
            meanPredictiveLogWidth = events.map { it.predictiveLogWidth }.meanOrNull(),
            meanCrpsLogResistance = events.map { it.crpsLogResistance }.meanOrNull(),
            trendClassifiedEventCount = classified.size,
            trendResidualCorrelation = correlation,
            byTrend = byTrend,
            serial = serial,
            verdict = DynamicStage1TemporalLagVerdict.INSUFFICIENT_DIAGNOSTIC_EVIDENCE,
            limitations = emptyList(),
        )
        val verdict = verdict(summary, policy)
        val limitations = buildList {
            if (events.size < policy.minimumGlobalEvaluableEvents) add("Too few evaluable held-out demonstrations for the global diagnostic gate.")
            if (classified.size < policy.minimumTrendClassifiedEvents) add("Too few held-out demonstrations had at least three comparable prior same-rep sessions for trend classification.")
            if ((byTrend[DynamicRecentTrendDirection.STABLE]?.count ?: 0) < policy.minimumStableEvents) add("Stable-history comparison support is limited; temporal lag cannot be cleanly isolated from interval/noise misspecification.")
            if (correlation == null) add("Trend/residual correlation is not reported because profile-session support is below the versioned correlation threshold.")
            add("Recent trend is diagnostic only: Theil-Sen slope of prior same-rep session median log resistance, maximum four sessions, with no held-out/future evidence.")
            add("CRPS uses natural-log resistance and is a deterministic approximation to the candidate predictive mixture; BENCHMARK_V0 has no CRPS because it has no probabilistic predictive distribution.")
        }
        return summary.copy(verdict = verdict, limitations = limitations)
    }

    private fun verdict(
        summary: DynamicStage1DiagnosticSummary,
        policy: DynamicStage1DiagnosticPolicy,
    ): DynamicStage1TemporalLagVerdict {
        if (summary.evaluableEventCount < policy.minimumGlobalEvaluableEvents ||
            summary.trendClassifiedEventCount < policy.minimumTrendClassifiedEvents
        ) return DynamicStage1TemporalLagVerdict.INSUFFICIENT_DIAGNOSTIC_EVIDENCE
        val upward = requireNotNull(summary.byTrend[DynamicRecentTrendDirection.UPWARD])
        val stable = requireNotNull(summary.byTrend[DynamicRecentTrendDirection.STABLE])
        if (upward.count < policy.minimumUpwardEvents) return DynamicStage1TemporalLagVerdict.INSUFFICIENT_DIAGNOSTIC_EVIDENCE
        val upwardSupport = (upward.medianSignedLogResidual ?: Double.NEGATIVE_INFINITY) >= policy.supportUpwardMedianResidual &&
            (upward.positiveResidualProportion ?: 0.0) >= policy.supportUpwardPositiveRate &&
            (upward.highPitRate ?: 0.0) >= policy.supportHighPitRate
        val correlationSupport = (summary.trendResidualCorrelation ?: Double.NEGATIVE_INFINITY) >= policy.supportTrendResidualCorrelation
        val stableContrast = stable.count >= policy.minimumStableEvents &&
            (upward.medianSignedLogResidual ?: 0.0) - (stable.medianSignedLogResidual ?: 0.0) >= policy.supportStableResidualContrast &&
            (upward.highPitRate ?: 0.0) - (stable.highPitRate ?: 0.0) >= policy.supportStableHighPitContrast
        val serialSupport = summary.serial.adjacentPairCount >= policy.minimumSerialPairs &&
            ((summary.serial.positivePositiveAdjacentRate ?: 0.0) >= policy.supportPositivePositiveAdjacentRate ||
                (summary.serial.lag1ResidualCorrelation ?: 0.0) >= policy.supportSerialLag1ResidualCorrelation)
        return when {
            upwardSupport && correlationSupport && stableContrast && serialSupport -> DynamicStage1TemporalLagVerdict.TEMPORAL_LAG_SUPPORTED
            upwardSupport && (correlationSupport || stableContrast || serialSupport) -> DynamicStage1TemporalLagVerdict.TEMPORAL_LAG_PLAUSIBLE_BUT_NOT_ISOLATED
            stable.count >= policy.minimumStableEvents && !upwardSupport && (summary.trendResidualCorrelation ?: 0.0) <= 0.0 -> DynamicStage1TemporalLagVerdict.TEMPORAL_LAG_NOT_SUPPORTED
            else -> DynamicStage1TemporalLagVerdict.TEMPORAL_LAG_PLAUSIBLE_BUT_NOT_ISOLATED
        }
    }

    private fun trendSummary(events: List<DynamicStage1EventDiagnostic>): DynamicStage1TrendGroupSummary {
        val low = events.count { it.pit < 1.0 / 3.0 }
        val middle = events.count { it.pit >= 1.0 / 3.0 && it.pit < 2.0 / 3.0 }
        val high = events.size - low - middle
        return DynamicStage1TrendGroupSummary(
            count = events.size,
            meanSignedLogResidual = events.map { it.signedLogResidual }.meanOrNull(),
            medianSignedLogResidual = events.map { it.signedLogResidual }.medianOrNull(),
            positiveResidualProportion = proportion(events) { it.signedLogResidual > 0.0 },
            pitLowCount = low,
            pitMiddleCount = middle,
            pitHighCount = high,
            highPitRate = if (events.isEmpty()) null else high.toDouble() / events.size,
            predictiveCoverage = proportion(events) { it.coveredByPredictiveInterval },
            catastrophicContradictionRate = proportion(events) { it.catastrophicFrontierContradiction },
            meanPredictiveLogWidth = events.map { it.predictiveLogWidth }.meanOrNull(),
            meanCrpsLogResistance = events.map { it.crpsLogResistance }.meanOrNull(),
        )
    }

    private fun serialSummary(
        profileEvents: List<List<DynamicStage1EventDiagnostic>>,
        policy: DynamicStage1DiagnosticPolicy,
    ): DynamicStage1SerialSummary {
        val sequences = profileEvents.map { profile ->
            profile.groupBy { it.sessionOrdinal }.toSortedMap().values.map { items ->
                median(items.map { it.signedLogResidual })
            }
        }.filter { it.isNotEmpty() }
        val pairs = sequences.flatMap { sequence -> sequence.zipWithNext() }
        val same = pairs.count { (a, b) -> a * b > 0.0 }
        val positivePositive = pairs.count { (a, b) -> a > 0.0 && b > 0.0 }
        val lag = if (pairs.size >= policy.minimumSerialPairs) {
            pearson(pairs.map { it.first }, pairs.map { it.second }, policy.minimumSerialPairs)
        } else null
        return DynamicStage1SerialSummary(
            profileSessionCount = sequences.sumOf { it.size },
            adjacentPairCount = pairs.size,
            sameSignAdjacentRate = if (pairs.isEmpty()) null else same.toDouble() / pairs.size,
            positivePositiveAdjacentRate = if (pairs.isEmpty()) null else positivePositive.toDouble() / pairs.size,
            lag1ResidualCorrelation = lag,
            longestPositiveRun = sequences.maxOfOrNull { sequence -> longestPositiveRun(sequence) } ?: 0,
        )
    }

    private fun longestPositiveRun(values: List<Double>): Int {
        var best = 0
        var current = 0
        values.forEach { value ->
            current = if (value > 0.0) current + 1 else 0
            if (current > best) best = current
        }
        return best
    }

    private fun positiveTailStreak(values: List<Double>): Int {
        var count = 0
        for (index in values.indices.reversed()) {
            if (values[index] > 0.0) count += 1 else break
        }
        return count
    }

    private fun pearson(left: List<Double>, right: List<Double>, minimum: Int): Double? {
        if (left.size != right.size || left.size < minimum) return null
        val meanLeft = left.average()
        val meanRight = right.average()
        var covariance = 0.0
        var varianceLeft = 0.0
        var varianceRight = 0.0
        for (index in left.indices) {
            val dl = left[index] - meanLeft
            val dr = right[index] - meanRight
            covariance += dl * dr
            varianceLeft += dl * dl
            varianceRight += dr * dr
        }
        if (varianceLeft <= 0.0 || varianceRight <= 0.0) return null
        return (covariance / sqrt(varianceLeft * varianceRight)).coerceIn(-1.0, 1.0)
    }

    private fun median(values: List<Double>): Double = requireNotNull(values.medianOrNull())
    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
    private fun List<Double>.meanOrNull(): Double? = if (isEmpty()) null else average()
    private fun <T> proportion(values: List<T>, predicate: (T) -> Boolean): Double? =
        if (values.isEmpty()) null else values.count(predicate).toDouble() / values.size
}
