package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DurationOnlyEvidence
import dev.kian.mymettle.domain.inference.LoadedHoldEvidence
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityEvidence
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import dev.kian.mymettle.domain.inference.NonDynamicEvidenceExclusion
import dev.kian.mymettle.domain.inference.NonDynamicEvidencePolicy
import dev.kian.mymettle.domain.inference.NonDynamicEvidenceProjection
import dev.kian.mymettle.domain.inference.NonDynamicExclusionReason
import dev.kian.mymettle.domain.inference.NonDynamicProfileSemantics
import dev.kian.mymettle.domain.inference.NonDynamicResistanceCoordinate
import dev.kian.mymettle.domain.inference.RepeatedContractionEvidence
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.ResistanceInputs
import dev.kian.mymettle.domain.performance.ResistanceResolver
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId

object NonDynamicCapabilityEvidenceProjector {
    fun project(
        profile: NonDynamicProfileSemantics,
        side: Laterality,
        evidence: Collection<CompletedSetEvidence>,
        policy: NonDynamicEvidencePolicy = NonDynamicCapabilityV1.evidencePolicy,
    ): NonDynamicEvidenceProjection {
        val projected = mutableListOf<NonDynamicCapabilityEvidence>()
        val exclusions = mutableListOf<NonDynamicEvidenceExclusion>()
        evidence.sortedWith(compareBy<CompletedSetEvidence> { it.completedAt }.thenBy { it.observationId }).forEach { candidate ->
            val pre = preExclusion(profile, side, candidate, policy)
            if (pre != null) {
                exclusions += NonDynamicEvidenceExclusion(candidate.observationId, pre)
                return@forEach
            }
            val result = when (profile.metricFamily) {
                MetricFamily.LOADED_HOLD -> loadedHold(profile, candidate, policy)
                MetricFamily.DURATION_ONLY -> durationOnly(profile, candidate, policy)
                MetricFamily.REPEATED_CONTRACTION -> repeatedContraction(profile, candidate, policy)
                else -> ProjectionResult.Excluded(NonDynamicExclusionReason.METRIC_FAMILY_INELIGIBLE)
            }
            when (result) {
                is ProjectionResult.Included -> projected += result.evidence
                is ProjectionResult.Excluded -> exclusions += NonDynamicEvidenceExclusion(candidate.observationId, result.reason)
            }
        }

        val reference = when (profile.metricFamily) {
            MetricFamily.LOADED_HOLD -> 30.0.takeIf { projected.isNotEmpty() }
            MetricFamily.DURATION_ONLY -> null
            MetricFamily.REPEATED_CONTRACTION -> projected.filterIsInstance<RepeatedContractionEvidence>()
                .map { it.cycles }.sorted().takeIf { it.isNotEmpty() }?.let { it[(it.size - 1) / 2].toDouble() }
            else -> null
        }
        return NonDynamicEvidenceProjection(profile, side, projected, exclusions, reference, policy)
    }

    private fun loadedHold(
        profile: NonDynamicProfileSemantics,
        candidate: CompletedSetEvidence,
        policy: NonDynamicEvidencePolicy,
    ): ProjectionResult {
        val metrics = candidate.metricValues.map { it.metric }.toSet()
        if (PerformanceMetric.REPETITIONS in metrics || PerformanceMetric.CADENCE in metrics) {
            return ProjectionResult.Excluded(NonDynamicExclusionReason.UNSUPPORTED_METRIC_COMBINATION)
        }
        val duration = positiveDuration(candidate) ?: return ProjectionResult.Excluded(
            if (candidate.metric(PerformanceMetric.DURATION) == null) NonDynamicExclusionReason.MISSING_DURATION
            else NonDynamicExclusionReason.NON_POSITIVE_DURATION,
        )
        val resistance = when (val resolution = resolveResistance(profile, candidate)) {
            is ResistanceResolution.Excluded -> return ProjectionResult.Excluded(resolution.reason)
            is ResistanceResolution.Resolved -> resolution.value
        }
        return ProjectionResult.Included(
            LoadedHoldEvidence(
                observationId = candidate.observationId,
                setRecordId = candidate.setRecordId,
                sessionId = requireNotNull(candidate.sessionId),
                executionProfileVersionId = candidate.executionProfileVersionId,
                side = candidate.laterality,
                completedAt = candidate.completedAt,
                resistance = resistance,
                durationSeconds = duration,
                bodyMassContextKg = candidate.bodyMassContextKg,
                evidencePolicyIdentity = policy.identity,
            ),
        )
    }

    private fun durationOnly(
        profile: NonDynamicProfileSemantics,
        candidate: CompletedSetEvidence,
        policy: NonDynamicEvidencePolicy,
    ): ProjectionResult {
        if (profile.resistanceModel.semantics != ResistanceSemantics.NONE) {
            return ProjectionResult.Excluded(NonDynamicExclusionReason.INVALID_RESISTANCE_SEMANTICS)
        }
        val metrics = candidate.metricValues.map { it.metric }.toSet()
        if (metrics.any { it in setOf(PerformanceMetric.EXTERNAL_LOAD, PerformanceMetric.ASSISTANCE, PerformanceMetric.REPETITIONS, PerformanceMetric.CADENCE) }) {
            return ProjectionResult.Excluded(NonDynamicExclusionReason.UNSUPPORTED_METRIC_COMBINATION)
        }
        val duration = positiveDuration(candidate) ?: return ProjectionResult.Excluded(
            if (candidate.metric(PerformanceMetric.DURATION) == null) NonDynamicExclusionReason.MISSING_DURATION
            else NonDynamicExclusionReason.NON_POSITIVE_DURATION,
        )
        return ProjectionResult.Included(
            DurationOnlyEvidence(
                observationId = candidate.observationId,
                setRecordId = candidate.setRecordId,
                sessionId = requireNotNull(candidate.sessionId),
                executionProfileVersionId = candidate.executionProfileVersionId,
                side = candidate.laterality,
                completedAt = candidate.completedAt,
                durationSeconds = duration,
                bodyMassContextKg = candidate.bodyMassContextKg,
                evidencePolicyIdentity = policy.identity,
            ),
        )
    }

    private fun repeatedContraction(
        profile: NonDynamicProfileSemantics,
        candidate: CompletedSetEvidence,
        policy: NonDynamicEvidencePolicy,
    ): ProjectionResult {
        val cyclesMetric = candidate.metric(PerformanceMetric.REPETITIONS)
            ?: return ProjectionResult.Excluded(NonDynamicExclusionReason.MISSING_CYCLES)
        val rawCycles = cyclesMetric.canonical.value
        if (!rawCycles.isFinite() || rawCycles <= 0.0 || rawCycles % 1.0 != 0.0 || cyclesMetric.canonical.unit != UnitId.REPETITION) {
            return ProjectionResult.Excluded(NonDynamicExclusionReason.NON_POSITIVE_CYCLES)
        }
        val resistance = when (val resolution = resolveResistance(profile, candidate)) {
            is ResistanceResolution.Excluded -> return ProjectionResult.Excluded(resolution.reason)
            is ResistanceResolution.Resolved -> resolution.value
        }
        val durationMetric = candidate.metric(PerformanceMetric.DURATION)
        val duration = durationMetric?.canonical?.value
        if (durationMetric != null && (duration == null || !duration.isFinite() || duration <= 0.0 || durationMetric.canonical.unit != UnitId.SECOND)) {
            return ProjectionResult.Excluded(NonDynamicExclusionReason.NON_POSITIVE_DURATION)
        }
        val cadenceMetric = candidate.metric(PerformanceMetric.CADENCE)
        val cadence = cadenceMetric?.canonical?.value
        if (cadenceMetric != null && (cadence == null || !cadence.isFinite() || cadence <= 0.0)) {
            return ProjectionResult.Excluded(NonDynamicExclusionReason.INVALID_CADENCE)
        }
        return ProjectionResult.Included(
            RepeatedContractionEvidence(
                observationId = candidate.observationId,
                setRecordId = candidate.setRecordId,
                sessionId = requireNotNull(candidate.sessionId),
                executionProfileVersionId = candidate.executionProfileVersionId,
                side = candidate.laterality,
                completedAt = candidate.completedAt,
                resistance = resistance,
                cycles = rawCycles.toInt(),
                cadencePerMinute = cadence,
                durationSeconds = duration,
                bodyMassContextKg = candidate.bodyMassContextKg,
                evidencePolicyIdentity = policy.identity,
            ),
        )
    }

    private fun positiveDuration(candidate: CompletedSetEvidence): Double? {
        val metric = candidate.metric(PerformanceMetric.DURATION) ?: return null
        val value = metric.canonical.value
        return value.takeIf { it.isFinite() && it > 0.0 && metric.canonical.unit == UnitId.SECOND }
    }

    private fun resolveResistance(
        profile: NonDynamicProfileSemantics,
        candidate: CompletedSetEvidence,
    ): ResistanceResolution {
        val model = profile.resistanceModel
        val allowed = model.semantics in setOf(
            ResistanceSemantics.EXTERNAL,
            ResistanceSemantics.ASSISTANCE,
            ResistanceSemantics.BODYWEIGHT,
            ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL,
        )
        if (!allowed) return ResistanceResolution.Excluded(NonDynamicExclusionReason.INVALID_RESISTANCE_SEMANTICS)
        val semanticProblem = when (model.semantics) {
            ResistanceSemantics.EXTERNAL -> model.externalLoadCoefficient <= 0.0 || model.bodyweightCoefficient != 0.0 || model.assistanceCoefficient != 0.0
            ResistanceSemantics.ASSISTANCE -> model.bodyweightCoefficient <= 0.0 || model.assistanceCoefficient <= 0.0
            ResistanceSemantics.BODYWEIGHT -> model.bodyweightCoefficient <= 0.0 || model.externalLoadCoefficient != 0.0 || model.assistanceCoefficient != 0.0
            ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL -> model.bodyweightCoefficient <= 0.0 || model.externalLoadCoefficient <= 0.0 || model.assistanceCoefficient != 0.0
            else -> true
        }
        if (semanticProblem) return ResistanceResolution.Excluded(NonDynamicExclusionReason.INVALID_RESISTANCE_SEMANTICS)

        val external = candidate.metric(PerformanceMetric.EXTERNAL_LOAD)?.canonical?.takeIf { it.unit == UnitId.KILOGRAM }?.value
        val assistance = candidate.metric(PerformanceMetric.ASSISTANCE)?.canonical?.takeIf { it.unit == UnitId.KILOGRAM }?.value
        val bodyMass = candidate.bodyMassContextKg
        if (model.externalLoadCoefficient > 0.0 && external == null) return ResistanceResolution.Excluded(NonDynamicExclusionReason.MISSING_EXTERNAL_LOAD)
        if (model.assistanceCoefficient > 0.0 && assistance == null) return ResistanceResolution.Excluded(NonDynamicExclusionReason.MISSING_ASSISTANCE)
        if (model.bodyweightCoefficient > 0.0 && bodyMass == null) return ResistanceResolution.Excluded(NonDynamicExclusionReason.MISSING_BODY_MASS)
        val resolved = ResistanceResolver.resolve(
            model,
            ResistanceInputs(bodyMassKg = bodyMass, externalLoadKg = external, assistanceKg = assistance),
        ) ?: return ResistanceResolution.Excluded(NonDynamicExclusionReason.INVALID_RESISTANCE_SEMANTICS)
        if (resolved.unit != UnitId.KILOGRAM || !resolved.coordinate.isFinite() || resolved.coordinate <= 0.0) {
            return ResistanceResolution.Excluded(NonDynamicExclusionReason.NON_POSITIVE_RESISTANCE_COORDINATE)
        }
        return ResistanceResolution.Resolved(
            NonDynamicResistanceCoordinate(
                valueKg = resolved.coordinate,
                resistanceSemantics = model.semantics,
                entryBasis = profile.entryBasis,
                resistanceModelVersion = resolved.modelVersion,
            ),
        )
    }

    private fun preExclusion(
        profile: NonDynamicProfileSemantics,
        side: Laterality,
        evidence: CompletedSetEvidence,
        policy: NonDynamicEvidencePolicy,
    ): NonDynamicExclusionReason? {
        if (evidence.executionProfileVersionId != profile.executionProfileVersionId) return NonDynamicExclusionReason.PROFILE_VERSION_MISMATCH
        if (evidence.metricFamily != profile.metricFamily || evidence.metricFamily !in NonDynamicCapabilityV1.supportedFamilies) {
            return NonDynamicExclusionReason.METRIC_FAMILY_INELIGIBLE
        }
        if (evidence.warmUp) return NonDynamicExclusionReason.WARM_UP_EXCLUDED
        if (evidence.sessionId == null) return NonDynamicExclusionReason.MISSING_SESSION_ID
        if (evidence.laterality != side) return NonDynamicExclusionReason.LATERALITY_INCOMPATIBLE
        if (side == Laterality.UNKNOWN) {
            if (profile.lateralityMode != LateralityMode.UNKNOWN) return NonDynamicExclusionReason.LATERALITY_INCOMPATIBLE
            if (evidence.observationSource !in policy.eligibleHistoricalUnknownSources) {
                return NonDynamicExclusionReason.UNKNOWN_LATERALITY_PROVENANCE_INELIGIBLE
            }
            return null
        }
        val compatible = when (profile.lateralityMode) {
            LateralityMode.BILATERAL_ONLY -> side == Laterality.BILATERAL
            LateralityMode.UNILATERAL -> side in setOf(Laterality.LEFT, Laterality.RIGHT)
            LateralityMode.ALTERNATING_ALLOWED -> side in setOf(Laterality.LEFT, Laterality.RIGHT, Laterality.BILATERAL, Laterality.ALTERNATING)
            LateralityMode.NOT_APPLICABLE -> side == Laterality.NOT_APPLICABLE
            LateralityMode.UNKNOWN -> false
        }
        return if (compatible) null else NonDynamicExclusionReason.LATERALITY_INCOMPATIBLE
    }

    private sealed interface ProjectionResult {
        data class Included(val evidence: NonDynamicCapabilityEvidence) : ProjectionResult
        data class Excluded(val reason: NonDynamicExclusionReason) : ProjectionResult
    }

    private sealed interface ResistanceResolution {
        data class Resolved(val value: NonDynamicResistanceCoordinate) : ResistanceResolution
        data class Excluded(val reason: NonDynamicExclusionReason) : ResistanceResolution
    }
}
