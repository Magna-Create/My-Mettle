package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.context.ContextConsumptionPolicy
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersion
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

/**
 * Immutable profile semantics needed by the dynamic-capability evidence boundary.
 * This deliberately does not copy recruitment, workout, prescription, or UI state.
 */
data class DynamicResistanceProfileSemantics(
    val executionProfileVersionId: ExecutionProfileVersionId,
    val executionProfileId: ExecutionProfileId,
    val metricFamily: MetricFamily,
    val resistanceModel: ResistanceModel,
    val entryBasis: EntryBasis,
    val lateralityMode: LateralityMode,
) {
    companion object {
        fun from(profile: ExecutionProfileVersion): DynamicResistanceProfileSemantics = DynamicResistanceProfileSemantics(
            executionProfileVersionId = profile.id,
            executionProfileId = profile.executionProfileId,
            metricFamily = profile.metricFamily,
            resistanceModel = profile.resistanceModel,
            entryBasis = profile.entryBasis,
            lateralityMode = profile.lateralityMode,
        )
    }
}

enum class DynamicResistanceWarmUpPolicy(val storageValue: String) {
    EXCLUDE("exclude"),
}

enum class DynamicResistanceSuccessfulSetSemantics(val storageValue: String) {
    LOWER_BOUND_DEMONSTRATION("lower_bound_demonstration"),
}

enum class DynamicResistanceReferenceRepPolicy(val storageValue: String) {
    MEDIAN_OBSERVED_LOWER_V1("median_observed_lower_v1"),
}

enum class DynamicResistanceTemporalPolicy(val storageValue: String) {
    INFERENCE_HORIZON_SNAPSHOT_NO_BIOLOGICAL_DECAY_V1("inference_horizon_snapshot_no_biological_decay_v1"),
}

/**
 * Behaviour-driving 7B.1 preparation policy. It contains no stochastic-frontier hyperparameters.
 * Those belong to 7B.2 and must create new config identity when introduced.
 */
data class DynamicResistanceEvidencePolicy(
    val semanticVersion: String,
    val eligibleMetricFamilies: Set<MetricFamily>,
    val warmUpPolicy: DynamicResistanceWarmUpPolicy,
    val resistanceCoordinateResolverVersion: String,
    val canonicalUnitPolicyVersion: String,
    val referenceRepPolicy: DynamicResistanceReferenceRepPolicy,
    val contextConsumptionPolicy: ContextConsumptionPolicy,
    val successfulSetSemantics: DynamicResistanceSuccessfulSetSemantics,
    val temporalPolicy: DynamicResistanceTemporalPolicy,
    val unresolvedAssistancePolicy: String,
    val unresolvedBodyweightPolicy: String,
    val deviceOrdinalPolicy: String,
) {
    init {
        require(semanticVersion.isNotBlank())
        require(eligibleMetricFamilies.isNotEmpty())
        require(resistanceCoordinateResolverVersion.isNotBlank())
        require(canonicalUnitPolicyVersion.isNotBlank())
        require(unresolvedAssistancePolicy.isNotBlank())
        require(unresolvedBodyweightPolicy.isNotBlank())
        require(deviceOrdinalPolicy.isNotBlank())
    }

    /** Stable policy fingerprint suitable for attaching to projected modelling evidence. */
    val identity: String by lazy {
        sha256(
            buildString {
                append("dynamic-resistance-evidence|").append(semanticVersion).append('\n')
                append("families=").append(eligibleMetricFamilies.map { it.storageValue }.sorted().joinToString(",")).append('\n')
                append("warmup=").append(warmUpPolicy.storageValue).append('\n')
                append("resolver=").append(resistanceCoordinateResolverVersion).append('\n')
                append("canonicalUnits=").append(canonicalUnitPolicyVersion).append('\n')
                append("referenceRep=").append(referenceRepPolicy.storageValue).append('\n')
                append("context=").append(contextConsumptionPolicy.identity).append('\n')
                append("successfulSet=").append(successfulSetSemantics.storageValue).append('\n')
                append("temporal=").append(temporalPolicy.storageValue).append('\n')
                append("assistance=").append(unresolvedAssistancePolicy).append('\n')
                append("bodyweight=").append(unresolvedBodyweightPolicy).append('\n')
                append("ordinal=").append(deviceOrdinalPolicy)
            },
        )
    }
}

object DynamicResistanceV1Contract {
    const val EVIDENCE_POLICY_VERSION = "n-bio-7b1-dynamic-resistance-evidence-v1"
    const val RESISTANCE_RESOLVER_VERSION = "n-bio-7b1-profile-local-positive-resistance-v1"
    const val CANONICAL_UNIT_POLICY_VERSION = "n-bio-6-metric-canonical-units-v1"
    const val CAPABILITY_STATE_SEMANTICS =
        "posterior frontier resistance at the model reference rep count for this execution-profile version and side at the inference horizon"
    const val CANDIDATE_POSTERIOR_STATUS = "NOT_YET_FIT_7B1"

    val contextPolicy = ContextConsumptionPolicy(
        semanticVersion = "dynamic-capability-context-none-v1",
        allowedTagIds = emptySet(),
    )

    val evidencePolicy = DynamicResistanceEvidencePolicy(
        semanticVersion = EVIDENCE_POLICY_VERSION,
        eligibleMetricFamilies = setOf(MetricFamily.DYNAMIC_RESISTANCE, MetricFamily.BODYWEIGHT_RESISTANCE),
        warmUpPolicy = DynamicResistanceWarmUpPolicy.EXCLUDE,
        resistanceCoordinateResolverVersion = RESISTANCE_RESOLVER_VERSION,
        canonicalUnitPolicyVersion = CANONICAL_UNIT_POLICY_VERSION,
        referenceRepPolicy = DynamicResistanceReferenceRepPolicy.MEDIAN_OBSERVED_LOWER_V1,
        contextConsumptionPolicy = contextPolicy,
        successfulSetSemantics = DynamicResistanceSuccessfulSetSemantics.LOWER_BOUND_DEMONSTRATION,
        temporalPolicy = DynamicResistanceTemporalPolicy.INFERENCE_HORIZON_SNAPSHOT_NO_BIOLOGICAL_DECAY_V1,
        unresolvedAssistancePolicy = "exclude_when_profile_semantics_cannot_resolve_positive_coordinate",
        unresolvedBodyweightPolicy = "exclude_when_profile_semantics_cannot_resolve_positive_coordinate",
        deviceOrdinalPolicy = "exclude_from_physical_log_resistance_frontier",
    )

    /**
     * Real behaviour-driving config definitions for 7B.1. They deliberately contain no fitter,
     * slope, slack, or noise hyperparameters because 7B.2 has not begun.
     */
    fun modelConfigs(createdAt: Instant): List<ModelConfigDefinition> = listOf(
        ModelConfigDefinition.create(
            component = InferenceModelComponent.PERFORMANCE_NORMALISATION,
            modelFamily = "dynamic_resistance_evidence",
            modelName = "strict_profile_local_evidence_projection",
            semanticVersion = EVIDENCE_POLICY_VERSION,
            configSchemaVersion = 1,
            parameters = mapOf(
                "eligibleMetricFamilies" to evidencePolicy.eligibleMetricFamilies.map { it.storageValue }.sorted().joinToString(","),
                "warmUpPolicy" to evidencePolicy.warmUpPolicy.storageValue,
                "canonicalUnitPolicy" to evidencePolicy.canonicalUnitPolicyVersion,
                "correctionPolicy" to "current_non_superseded_only",
                "profileVersionIsolation" to "exact",
                "lateralityIsolation" to "exact",
            ),
            createdAt = createdAt,
        ),
        ModelConfigDefinition.create(
            component = InferenceModelComponent.RESISTANCE,
            modelFamily = "dynamic_resistance_coordinate",
            modelName = "profile_local_positive_physical_challenge",
            semanticVersion = RESISTANCE_RESOLVER_VERSION,
            configSchemaVersion = 1,
            parameters = mapOf(
                "resolverVersion" to evidencePolicy.resistanceCoordinateResolverVersion,
                "externalEntryBasis" to "preserve_profile_entry_basis",
                "nonPositiveCoordinate" to "exclude_no_clamp_or_offset",
                "deviceOrdinal" to evidencePolicy.deviceOrdinalPolicy,
                "unresolvedAssistance" to evidencePolicy.unresolvedAssistancePolicy,
                "unresolvedBodyweight" to evidencePolicy.unresolvedBodyweightPolicy,
            ),
            createdAt = createdAt,
        ),
        ModelConfigDefinition.create(
            component = InferenceModelComponent.DYNAMIC_CAPABILITY,
            modelFamily = "stochastic_frontier_contract",
            modelName = "centred_log_rep_frontier_pre_fit_contract",
            semanticVersion = "n-bio-7b1-dynamic-capability-contract-v1",
            configSchemaVersion = 1,
            parameters = mapOf(
                "capabilityStateSemantics" to CAPABILITY_STATE_SEMANTICS,
                "referenceRepPolicy" to evidencePolicy.referenceRepPolicy.storageValue,
                "successfulSetSemantics" to evidencePolicy.successfulSetSemantics.storageValue,
                "contextConsumption" to "NONE:${contextPolicy.identity}",
                "temporalPolicy" to evidencePolicy.temporalPolicy.storageValue,
                "fittingStage" to "not_implemented_until_7b2",
            ),
            createdAt = createdAt,
        ),
    )
}

data class DynamicMetricEvidenceAudit(
    val metric: PerformanceMetric,
    val entered: Quantity,
    val canonical: Quantity,
    val acquisitionMethod: String,
    val evidenceGranularity: String,
) {
    init {
        require(entered.unit.dimension == metric.dimension)
        require(canonical.unit == metric.canonicalUnit)
    }
}

data class ProfileLocalResistanceCoordinate(
    val value: Double,
    val unit: UnitId,
    val resistanceSemantics: ResistanceSemantics,
    val entryBasis: EntryBasis,
    val resistanceModelVersion: String,
    val resolverVersion: String,
) {
    init {
        require(value.isFinite() && value > 0.0) { "Dynamic log-resistance coordinate must be finite and strictly positive." }
        require(unit == UnitId.KILOGRAM) { "7B v1 physical resistance coordinate is canonical kilograms." }
        require(resistanceModelVersion.isNotBlank())
        require(resolverVersion.isNotBlank())
    }
}

/** Strongly typed modelling input projected from canonical N-BIO-6 evidence. */
data class DynamicResistanceEvidence(
    val observationId: String,
    val setRecordId: String,
    val sessionId: String,
    val executionProfileVersionId: ExecutionProfileVersionId,
    val side: Laterality,
    val completedAt: Instant,
    val repetitions: Int,
    val resistance: ProfileLocalResistanceCoordinate,
    val metricEvidence: List<DynamicMetricEvidenceAudit>,
    val warmUp: Boolean,
    val setKind: String,
    val evidencePolicyIdentity: String,
) {
    init {
        require(observationId.isNotBlank() && setRecordId.isNotBlank() && sessionId.isNotBlank())
        require(repetitions > 0)
        require(metricEvidence.isNotEmpty())
        require(!warmUp) { "Warm-ups are excluded from 7B v1 frontier evidence." }
        require(setKind.isNotBlank())
        require(evidencePolicyIdentity.isNotBlank())
    }
}

enum class DynamicResistanceExclusionReason(val storageValue: String) {
    PROFILE_VERSION_MISMATCH("profile_version_mismatch"),
    METRIC_FAMILY_INELIGIBLE("metric_family_ineligible"),
    WARM_UP_EXCLUDED("warm_up_excluded"),
    LATERALITY_INCOMPATIBLE("laterality_incompatible"),
    MISSING_SESSION_ID("missing_session_id"),
    MISSING_REPETITIONS("missing_repetitions"),
    NON_POSITIVE_REPETITIONS("non_positive_repetitions"),
    MISSING_EXTERNAL_LOAD("missing_external_load"),
    MISSING_ASSISTANCE("missing_assistance"),
    MISSING_BODY_MASS("missing_body_mass"),
    INCONSISTENT_RESISTANCE_MODEL("inconsistent_resistance_model"),
    UNSUPPORTED_RESISTANCE_SEMANTICS("unsupported_resistance_semantics"),
    DEVICE_ORDINAL_NOT_PHYSICAL("device_ordinal_not_physical"),
    NON_POSITIVE_RESISTANCE_COORDINATE("non_positive_resistance_coordinate"),
}

data class DynamicResistanceEvidenceExclusion(
    val observationId: String,
    val reason: DynamicResistanceExclusionReason,
) {
    init { require(observationId.isNotBlank()) }
}

data class DynamicResistanceEvidenceProjection(
    val profile: DynamicResistanceProfileSemantics,
    val side: Laterality,
    val evidence: List<DynamicResistanceEvidence>,
    val exclusions: List<DynamicResistanceEvidenceExclusion>,
    val referenceRepetitions: Double?,
    val policy: DynamicResistanceEvidencePolicy,
) {
    init {
        require(evidence.all { it.executionProfileVersionId == profile.executionProfileVersionId })
        require(evidence.all { it.side == side })
        require(referenceRepetitions == null || referenceRepetitions > 0.0)
        if (evidence.isEmpty()) require(referenceRepetitions == null)
    }

    val independentSessionCount: Int get() = evidence.map { it.sessionId }.distinct().size
    val repDomain: IntRange? get() = if (evidence.isEmpty()) null else evidence.minOf { it.repetitions }..evidence.maxOf { it.repetitions }
    val resistanceRange: ClosedFloatingPointRange<Double>? get() = if (evidence.isEmpty()) null else {
        evidence.minOf { it.resistance.value }..evidence.maxOf { it.resistance.value }
    }
}

/**
 * Capability-state contract for DYNAMIC_RESISTANCE. The persisted posterior value is resistance at
 * referenceRepetitions, not a one-repetition maximum and not a development or fatigue state.
 */
data class DynamicResistanceCapabilityStateContract(
    val executionProfileVersionId: ExecutionProfileVersionId,
    val side: Laterality,
    val referenceRepetitions: Double,
    val canonicalUnit: UnitId = UnitId.KILOGRAM,
    val semanticDefinition: String = DynamicResistanceV1Contract.CAPABILITY_STATE_SEMANTICS,
) {
    init {
        require(referenceRepetitions > 0.0)
        require(canonicalUnit == UnitId.KILOGRAM)
        require(!semanticDefinition.contains("e1rm", ignoreCase = true))
        require(!semanticDefinition.contains("one-rep", ignoreCase = true))
    }
}

/** No implementation exists in 7B.1. 7B.2 must supply a real stochastic fit. */
interface DynamicCapabilityFit {
    val executionProfileVersionId: ExecutionProfileVersionId
    val side: Laterality
    val inferenceHorizon: Instant
    val referenceRepetitions: Double
    val modelConfigId: ModelConfigId
}

data class DynamicCapabilityFitRequest(
    val projection: DynamicResistanceEvidenceProjection,
    val inferenceHorizon: Instant,
    val modelConfig: ModelConfigDefinition,
) {
    init {
        require(modelConfig.component == InferenceModelComponent.DYNAMIC_CAPABILITY)
    }
}

interface DynamicCapabilityModel<F : DynamicCapabilityFit> {
    val modelVersion: String
    fun fit(request: DynamicCapabilityFitRequest): F
    fun predictFrontier(fit: F, repetitions: Double): PosteriorEstimate
}

enum class DynamicCapabilityValidationQuestion(val storageValue: String) {
    FUTURE_SUCCESS_BELOW_FRONTIER_PROBABILITY("future_success_below_frontier_probability"),
    LOWER_BOUND_EXCEEDANCE_CALIBRATION("lower_bound_exceedance_calibration"),
    OUT_OF_DOMAIN_UNCERTAINTY_WIDENING("out_of_domain_uncertainty_widening"),
    FUTURE_FRONTIER_IMPROVEMENT_PROBABILITY("future_frontier_improvement_probability"),
    FRONTIER_BASELINE_COMPARISON("frontier_baseline_comparison"),
}

data class DynamicCapabilityValidationContract(
    val successfulSetSemantics: DynamicResistanceSuccessfulSetSemantics =
        DynamicResistanceSuccessfulSetSemantics.LOWER_BOUND_DEMONSTRATION,
    val naiveChosenLoadMaeIsCapabilityMetric: Boolean = false,
    val questions: Set<DynamicCapabilityValidationQuestion> = DynamicCapabilityValidationQuestion.entries.toSet(),
) {
    init {
        require(!naiveChosenLoadMaeIsCapabilityMetric) {
            "User-chosen successful training load is not an observed maximum-capability target."
        }
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
