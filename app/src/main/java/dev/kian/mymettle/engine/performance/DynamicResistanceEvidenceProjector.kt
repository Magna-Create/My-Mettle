package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicMetricEvidenceAudit
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceExclusion
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidencePolicy
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceExclusionReason
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceReferenceRepPolicy
import dev.kian.mymettle.domain.inference.DynamicResistanceV1Contract
import dev.kian.mymettle.domain.inference.ProfileLocalResistanceCoordinate
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.PerformanceObservation
import dev.kian.mymettle.domain.performance.ResistanceInputs
import dev.kian.mymettle.domain.performance.ResistanceResolver
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import kotlin.math.exp
import kotlin.math.ln

/**
 * Strict 7B adapter over the established N-BIO-6 resistance equation. The generic resolver remains
 * canonical for resistance arithmetic; this layer validates profile semantics and rejects any null,
 * non-physical or non-positive result rather than accepting its bookkeeping zero clamp as ln(R).
 */
object DynamicResistanceCoordinateResolver {
    fun resolve(
        profile: DynamicResistanceProfileSemantics,
        evidence: CompletedSetEvidence,
        policy: DynamicResistanceEvidencePolicy = DynamicResistanceV1Contract.evidencePolicy,
    ): ResistanceCoordinateResolution {
        val model = profile.resistanceModel
        if (!policy.acceptsResistanceSemantics(profile.metricFamily, model.semantics)) {
            return unresolved(DynamicResistanceExclusionReason.METRIC_FAMILY_RESISTANCE_SEMANTICS_INCOMPATIBLE)
        }

        val external = evidence.metric(PerformanceMetric.EXTERNAL_LOAD)?.canonicalMassKg()
        val assistance = evidence.metric(PerformanceMetric.ASSISTANCE)?.canonicalMassKg()
        val bodyMass = evidence.bodyMassContextKg

        val semanticProblem = when (model.semantics) {
            ResistanceSemantics.EXTERNAL ->
                model.bodyweightCoefficient != 0.0 ||
                    model.assistanceCoefficient != 0.0 ||
                    model.externalLoadCoefficient <= 0.0

            ResistanceSemantics.ASSISTANCE ->
                model.bodyweightCoefficient <= 0.0 || model.assistanceCoefficient <= 0.0

            ResistanceSemantics.BODYWEIGHT ->
                model.bodyweightCoefficient <= 0.0 ||
                    model.externalLoadCoefficient != 0.0 ||
                    model.assistanceCoefficient != 0.0

            ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL ->
                model.bodyweightCoefficient <= 0.0 ||
                    model.externalLoadCoefficient <= 0.0 ||
                    model.assistanceCoefficient != 0.0

            ResistanceSemantics.DEVICE_ORDINAL ->
                return unresolved(DynamicResistanceExclusionReason.DEVICE_ORDINAL_NOT_PHYSICAL)

            ResistanceSemantics.NONE ->
                return unresolved(DynamicResistanceExclusionReason.UNSUPPORTED_RESISTANCE_SEMANTICS)
        }
        if (semanticProblem) {
            return unresolved(DynamicResistanceExclusionReason.INCONSISTENT_RESISTANCE_MODEL)
        }

        if (model.bodyweightCoefficient > 0.0 && bodyMass == null) {
            return unresolved(DynamicResistanceExclusionReason.MISSING_BODY_MASS)
        }
        if (model.externalLoadCoefficient > 0.0 && external == null) {
            return unresolved(DynamicResistanceExclusionReason.MISSING_EXTERNAL_LOAD)
        }
        if (model.assistanceCoefficient > 0.0 && assistance == null) {
            return unresolved(DynamicResistanceExclusionReason.MISSING_ASSISTANCE)
        }

        val resolved = ResistanceResolver.resolve(
            model = model,
            inputs = ResistanceInputs(
                bodyMassKg = bodyMass,
                externalLoadKg = external,
                assistanceKg = assistance,
            ),
        ) ?: return unresolved(DynamicResistanceExclusionReason.UNSUPPORTED_RESISTANCE_SEMANTICS)

        if (!resolved.coordinate.isFinite() || resolved.coordinate <= 0.0) {
            return unresolved(DynamicResistanceExclusionReason.NON_POSITIVE_RESISTANCE_COORDINATE)
        }
        if (resolved.unit != UnitId.KILOGRAM) {
            return unresolved(DynamicResistanceExclusionReason.UNSUPPORTED_RESISTANCE_SEMANTICS)
        }

        return ResistanceCoordinateResolution.Resolved(
            ProfileLocalResistanceCoordinate(
                value = resolved.coordinate,
                unit = resolved.unit,
                resistanceSemantics = model.semantics,
                entryBasis = profile.entryBasis,
                resistanceModelVersion = resolved.modelVersion,
                resolverVersion = policy.resistanceCoordinateResolverVersion,
            ),
        )
    }

    private fun PerformanceMetricValue.canonicalMassKg(): Double? =
        canonical.takeIf { it.unit == UnitId.KILOGRAM && it.value.isFinite() }?.value

    private fun unresolved(reason: DynamicResistanceExclusionReason) = ResistanceCoordinateResolution.Unresolved(reason)
}

sealed interface ResistanceCoordinateResolution {
    data class Resolved(val coordinate: ProfileLocalResistanceCoordinate) : ResistanceCoordinateResolution
    data class Unresolved(val reason: DynamicResistanceExclusionReason) : ResistanceCoordinateResolution
}

object DynamicResistanceEvidenceProjector {
    fun project(
        profile: DynamicResistanceProfileSemantics,
        side: Laterality,
        evidence: Collection<CompletedSetEvidence>,
        policy: DynamicResistanceEvidencePolicy = DynamicResistanceV1Contract.evidencePolicy,
    ): DynamicResistanceEvidenceProjection {
        val projected = mutableListOf<DynamicResistanceEvidence>()
        val exclusions = mutableListOf<DynamicResistanceEvidenceExclusion>()

        evidence.sortedWith(compareBy<CompletedSetEvidence> { it.completedAt }.thenBy { it.observationId }).forEach { candidate ->
            val reason = preCoordinateExclusion(profile, side, candidate, policy)
            if (reason != null) {
                exclusions += DynamicResistanceEvidenceExclusion(candidate.observationId, reason)
                return@forEach
            }

            val repetitionsValue = candidate.metric(PerformanceMetric.REPETITIONS)
            val repetitions = repetitionsValue?.canonical?.value?.toInt()
            if (repetitionsValue == null) {
                exclusions += DynamicResistanceEvidenceExclusion(
                    candidate.observationId,
                    DynamicResistanceExclusionReason.MISSING_REPETITIONS,
                )
                return@forEach
            }
            if (
                repetitions == null || repetitions <= 0 || repetitionsValue.canonical.value != repetitions.toDouble() ||
                repetitionsValue.canonical.unit != UnitId.REPETITION
            ) {
                exclusions += DynamicResistanceEvidenceExclusion(
                    candidate.observationId,
                    DynamicResistanceExclusionReason.NON_POSITIVE_REPETITIONS,
                )
                return@forEach
            }

            when (val resolution = DynamicResistanceCoordinateResolver.resolve(profile, candidate, policy)) {
                is ResistanceCoordinateResolution.Unresolved -> {
                    exclusions += DynamicResistanceEvidenceExclusion(candidate.observationId, resolution.reason)
                }
                is ResistanceCoordinateResolution.Resolved -> {
                    projected += DynamicResistanceEvidence(
                        observationId = candidate.observationId,
                        setRecordId = candidate.setRecordId,
                        sessionId = requireNotNull(candidate.sessionId),
                        executionProfileVersionId = candidate.executionProfileVersionId,
                        side = candidate.laterality,
                        completedAt = candidate.completedAt,
                        repetitions = repetitions,
                        resistance = resolution.coordinate,
                        metricEvidence = candidate.metricValues.map { metric ->
                            DynamicMetricEvidenceAudit(
                                metric = metric.metric,
                                entered = metric.entered,
                                canonical = metric.canonical,
                                acquisitionMethod = metric.evidenceQuality.acquisitionMethod.storageValue,
                                evidenceGranularity = metric.evidenceQuality.granularity.storageValue,
                            )
                        },
                        warmUp = false,
                        setKind = candidate.kind,
                        evidencePolicyIdentity = policy.identity,
                    )
                }
            }
        }

        return DynamicResistanceEvidenceProjection(
            profile = profile,
            side = side,
            evidence = projected,
            exclusions = exclusions,
            referenceRepetitions = DynamicReferenceRepSelector.select(projected, policy.referenceRepPolicy),
            policy = policy,
        )
    }

    private fun preCoordinateExclusion(
        profile: DynamicResistanceProfileSemantics,
        side: Laterality,
        evidence: CompletedSetEvidence,
        policy: DynamicResistanceEvidencePolicy,
    ): DynamicResistanceExclusionReason? = when {
        evidence.executionProfileVersionId != profile.executionProfileVersionId ->
            DynamicResistanceExclusionReason.PROFILE_VERSION_MISMATCH
        evidence.metricFamily != profile.metricFamily || evidence.metricFamily !in policy.eligibleMetricFamilies ->
            DynamicResistanceExclusionReason.METRIC_FAMILY_INELIGIBLE
        !policy.acceptsResistanceSemantics(profile.metricFamily, profile.resistanceModel.semantics) ->
            DynamicResistanceExclusionReason.METRIC_FAMILY_RESISTANCE_SEMANTICS_INCOMPATIBLE
        policy.warmUpPolicy == dev.kian.mymettle.domain.inference.DynamicResistanceWarmUpPolicy.EXCLUDE && evidence.warmUp ->
            DynamicResistanceExclusionReason.WARM_UP_EXCLUDED
        evidence.sessionId == null -> DynamicResistanceExclusionReason.MISSING_SESSION_ID
        !isSideCompatible(profile.lateralityMode, side, evidence.laterality) ->
            DynamicResistanceExclusionReason.LATERALITY_INCOMPATIBLE
        else -> null
    }

    private fun isSideCompatible(mode: LateralityMode, requestedSide: Laterality, observedSide: Laterality): Boolean {
        if (requestedSide != observedSide) return false
        return when (mode) {
            LateralityMode.BILATERAL_ONLY -> observedSide == Laterality.BILATERAL
            LateralityMode.UNILATERAL -> observedSide in setOf(Laterality.LEFT, Laterality.RIGHT)
            LateralityMode.ALTERNATING_ALLOWED -> observedSide in setOf(
                Laterality.LEFT,
                Laterality.RIGHT,
                Laterality.BILATERAL,
                Laterality.ALTERNATING,
            )
            LateralityMode.NOT_APPLICABLE -> observedSide == Laterality.NOT_APPLICABLE
            LateralityMode.UNKNOWN -> false
        }
    }
}

/** Domain-level equivalent of the DAO current-observation predicate, useful for replay/tests. */
object CurrentPerformanceObservationSelector {
    fun current(observations: Collection<PerformanceObservation>): List<PerformanceObservation> {
        val supersededIds = observations.mapNotNull { it.supersedesObservationId }.toSet()
        return observations
            .filterNot { it.id in supersededIds }
            .sortedWith(compareBy<PerformanceObservation> { it.completedAt }.thenBy { it.ordinal }.thenBy { it.id })
    }
}

object DynamicReferenceRepSelector {
    fun select(
        evidence: Collection<DynamicResistanceEvidence>,
        policy: DynamicResistanceReferenceRepPolicy = DynamicResistanceReferenceRepPolicy.MEDIAN_OBSERVED_LOWER_V1,
    ): Double? {
        if (evidence.isEmpty()) return null
        return when (policy) {
            DynamicResistanceReferenceRepPolicy.MEDIAN_OBSERVED_LOWER_V1 -> {
                val sorted = evidence.map { it.repetitions }.sorted()
                sorted[(sorted.size - 1) / 2].toDouble()
            }
        }
    }
}

/** Algebraically equivalent centred coordinates for the future stochastic-frontier fitter. */
object DynamicResistanceLogCoordinates {
    fun logResistance(coordinate: ProfileLocalResistanceCoordinate): Double = ln(coordinate.value)

    fun resistanceFromLog(
        logResistance: Double,
        template: ProfileLocalResistanceCoordinate,
    ): ProfileLocalResistanceCoordinate = template.copy(value = exp(logResistance))

    fun centredLogRep(repetitions: Double, referenceRepetitions: Double): Double {
        require(repetitions.isFinite() && repetitions > 0.0)
        require(referenceRepetitions.isFinite() && referenceRepetitions > 0.0)
        return ln(repetitions / referenceRepetitions)
    }
}
