package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersion
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.ln

/** Exact immutable execution semantics consumed by one N-BIO-7C capability stream. */
data class NonDynamicProfileSemantics(
    val executionProfileVersionId: ExecutionProfileVersionId,
    val executionProfileId: ExecutionProfileId,
    val metricFamily: MetricFamily,
    val resistanceModel: ResistanceModel,
    val entryBasis: EntryBasis,
    val lateralityMode: LateralityMode,
) {
    init {
        require(metricFamily in NonDynamicCapabilityV1.supportedFamilies)
    }

    companion object {
        fun from(profile: ExecutionProfileVersion): NonDynamicProfileSemantics = NonDynamicProfileSemantics(
            executionProfileVersionId = profile.id,
            executionProfileId = profile.executionProfileId,
            metricFamily = profile.metricFamily,
            resistanceModel = profile.resistanceModel,
            entryBasis = profile.entryBasis,
            lateralityMode = profile.lateralityMode,
        )
    }
}

enum class NonDynamicReferencePolicy(val storageValue: String) {
    FIXED_30_SECONDS_V1("fixed_30_seconds_v1"),
    NO_REFERENCE_COORDINATE_V1("no_reference_coordinate_v1"),
    MEDIAN_OBSERVED_LOWER_V1("median_observed_lower_v1"),
}

enum class NonDynamicCadencePolicy(val storageValue: String) {
    NOT_APPLICABLE("not_applicable"),
    PRESERVE_FIXED_CONTEXT_FAIL_ON_VARIATION_V1("preserve_fixed_context_fail_on_variation_v1"),
}

enum class NonDynamicDurationContextPolicy(val storageValue: String) {
    MODEL_COORDINATE("model_coordinate"),
    MODEL_OUTPUT("model_output"),
    PRESERVE_NOT_INDEPENDENT_PARAMETER_V1("preserve_not_independent_parameter_v1"),
}

enum class NonDynamicBodyMassContextPolicy(val storageValue: String) {
    RESOLVER_INPUT_WHEN_EXPLICIT("resolver_input_when_explicit"),
    PRESERVE_NOT_MODEL_DRIVING_V1("preserve_not_model_driving_v1"),
}

enum class NonDynamicSuccessfulObservationSemantics(val storageValue: String) {
    LOWER_BOUND_DEMONSTRATION("lower_bound_demonstration"),
}

enum class NonDynamicExclusionReason(val storageValue: String) {
    PROFILE_VERSION_MISMATCH("profile_version_mismatch"),
    METRIC_FAMILY_INELIGIBLE("metric_family_ineligible"),
    WARM_UP_EXCLUDED("warm_up_excluded"),
    MISSING_SESSION_ID("missing_session_id"),
    LATERALITY_INCOMPATIBLE("laterality_incompatible"),
    UNKNOWN_LATERALITY_PROVENANCE_INELIGIBLE("unknown_laterality_provenance_ineligible"),
    MISSING_DURATION("missing_duration"),
    NON_POSITIVE_DURATION("non_positive_duration"),
    MISSING_CYCLES("missing_cycles"),
    NON_POSITIVE_CYCLES("non_positive_cycles"),
    INVALID_CADENCE("invalid_cadence"),
    UNSUPPORTED_METRIC_COMBINATION("unsupported_metric_combination"),
    INVALID_RESISTANCE_SEMANTICS("invalid_resistance_semantics"),
    MISSING_EXTERNAL_LOAD("missing_external_load"),
    MISSING_ASSISTANCE("missing_assistance"),
    MISSING_BODY_MASS("missing_body_mass"),
    NON_POSITIVE_RESISTANCE_COORDINATE("non_positive_resistance_coordinate"),
}

data class NonDynamicEvidenceExclusion(
    val observationId: String,
    val reason: NonDynamicExclusionReason,
)

data class NonDynamicEvidencePolicy(
    val semanticVersion: String = "n-bio-7c-non-dynamic-evidence-v1",
    val eligibleHistoricalUnknownSources: Set<String> = setOf("corrected_lite_import", "lite_legacy_v6_import"),
    val successfulObservationSemantics: NonDynamicSuccessfulObservationSemantics =
        NonDynamicSuccessfulObservationSemantics.LOWER_BOUND_DEMONSTRATION,
    val warmUpsExcluded: Boolean = true,
    val currentNonSupersededOnly: Boolean = true,
) {
    init {
        require(semanticVersion.isNotBlank())
        require(eligibleHistoricalUnknownSources.all { it.isNotBlank() })
        require(warmUpsExcluded && currentNonSupersededOnly)
    }

    val identity: String by lazy {
        sha256(
            listOf(
                semanticVersion,
                eligibleHistoricalUnknownSources.sorted().joinToString(","),
                successfulObservationSemantics.storageValue,
                "warmups=exclude",
                "corrections=current_as_of_cutoff",
                "profileVersion=exact",
                "laterality=exact_unknown_remains_unknown",
            ).joinToString("|"),
        )
    }
}

data class NonDynamicResistanceCoordinate(
    val valueKg: Double,
    val resistanceSemantics: ResistanceSemantics,
    val entryBasis: EntryBasis,
    val resistanceModelVersion: String,
) {
    init {
        require(valueKg.isFinite() && valueKg > 0.0)
        require(resistanceModelVersion.isNotBlank())
    }
}

sealed interface NonDynamicCapabilityEvidence {
    val observationId: String
    val setRecordId: String
    val sessionId: String
    val executionProfileVersionId: ExecutionProfileVersionId
    val side: Laterality
    val completedAt: Instant
    val evidencePolicyIdentity: String
}

data class LoadedHoldEvidence(
    override val observationId: String,
    override val setRecordId: String,
    override val sessionId: String,
    override val executionProfileVersionId: ExecutionProfileVersionId,
    override val side: Laterality,
    override val completedAt: Instant,
    val resistance: NonDynamicResistanceCoordinate,
    val durationSeconds: Double,
    val bodyMassContextKg: Double?,
    override val evidencePolicyIdentity: String,
) : NonDynamicCapabilityEvidence {
    init {
        require(durationSeconds.isFinite() && durationSeconds > 0.0)
        require(bodyMassContextKg == null || bodyMassContextKg > 0.0)
    }
}

data class DurationOnlyEvidence(
    override val observationId: String,
    override val setRecordId: String,
    override val sessionId: String,
    override val executionProfileVersionId: ExecutionProfileVersionId,
    override val side: Laterality,
    override val completedAt: Instant,
    val durationSeconds: Double,
    val bodyMassContextKg: Double?,
    override val evidencePolicyIdentity: String,
) : NonDynamicCapabilityEvidence {
    init {
        require(durationSeconds.isFinite() && durationSeconds > 0.0)
        require(bodyMassContextKg == null || bodyMassContextKg > 0.0)
    }
}

data class RepeatedContractionEvidence(
    override val observationId: String,
    override val setRecordId: String,
    override val sessionId: String,
    override val executionProfileVersionId: ExecutionProfileVersionId,
    override val side: Laterality,
    override val completedAt: Instant,
    val resistance: NonDynamicResistanceCoordinate,
    val cycles: Int,
    val cadencePerMinute: Double?,
    val durationSeconds: Double?,
    val bodyMassContextKg: Double?,
    override val evidencePolicyIdentity: String,
) : NonDynamicCapabilityEvidence {
    init {
        require(cycles > 0)
        require(cadencePerMinute == null || cadencePerMinute.isFinite() && cadencePerMinute > 0.0)
        require(durationSeconds == null || durationSeconds.isFinite() && durationSeconds > 0.0)
        require(bodyMassContextKg == null || bodyMassContextKg > 0.0)
    }
}

data class NonDynamicEvidenceProjection(
    val profile: NonDynamicProfileSemantics,
    val side: Laterality,
    val evidence: List<NonDynamicCapabilityEvidence>,
    val exclusions: List<NonDynamicEvidenceExclusion>,
    val referenceCoordinate: Double?,
    val policy: NonDynamicEvidencePolicy,
) {
    init {
        require(evidence.all { it.executionProfileVersionId == profile.executionProfileVersionId && it.side == side })
        when (profile.metricFamily) {
            MetricFamily.LOADED_HOLD -> require(referenceCoordinate == 30.0 || evidence.isEmpty() && referenceCoordinate == null)
            MetricFamily.DURATION_ONLY -> require(referenceCoordinate == null)
            MetricFamily.REPEATED_CONTRACTION -> require(referenceCoordinate == null || referenceCoordinate > 0.0)
            else -> error("Unsupported 7C family ${profile.metricFamily}")
        }
    }

    val independentSessionCount: Int get() = evidence.map { it.sessionId }.distinct().size
    val durationDomain: ClosedFloatingPointRange<Double>?
        get() = evidence.mapNotNull {
            when (it) {
                is LoadedHoldEvidence -> it.durationSeconds
                is DurationOnlyEvidence -> it.durationSeconds
                is RepeatedContractionEvidence -> it.durationSeconds
            }
        }.takeIf { it.isNotEmpty() }?.let { it.min()..it.max() }
    val cycleDomain: IntRange?
        get() = evidence.filterIsInstance<RepeatedContractionEvidence>().map { it.cycles }
            .takeIf { it.isNotEmpty() }?.let { it.min()..it.max() }
    val resistanceDomainKg: ClosedFloatingPointRange<Double>?
        get() = evidence.mapNotNull {
            when (it) {
                is LoadedHoldEvidence -> it.resistance.valueKg
                is RepeatedContractionEvidence -> it.resistance.valueKg
                is DurationOnlyEvidence -> null
            }
        }.takeIf { it.isNotEmpty() }?.let { it.min()..it.max() }
    val cadenceValues: Set<Double>
        get() = evidence.filterIsInstance<RepeatedContractionEvidence>().mapNotNull { it.cadencePerMinute }.toSet()
}

enum class NonDynamicFitFailureReason(val storageValue: String) {
    NO_ELIGIBLE_EVIDENCE("no_eligible_evidence"),
    INSUFFICIENT_IDENTIFIABILITY("insufficient_identifiability"),
    MODEL_CONFIG_MISMATCH("model_config_mismatch"),
    EVIDENCE_POLICY_MISMATCH("evidence_policy_mismatch"),
    UNSUPPORTED_METRIC_COMBINATION("unsupported_metric_combination"),
    INVALID_RESISTANCE_SEMANTICS("invalid_resistance_semantics"),
    UNSUPPORTED_CONTEXT("unsupported_context"),
    NUMERICAL_BUDGET_EXCEEDED("numerical_budget_exceeded"),
    NON_FINITE_POSTERIOR("non_finite_posterior"),
    DEGENERATE_POSTERIOR("degenerate_posterior"),
    SOLVER_FIDELITY_REJECTED("solver_fidelity_rejected"),
}

class NonDynamicCapabilityFitException(
    val reason: NonDynamicFitFailureReason,
    message: String,
) : IllegalStateException(message)

data class NonDynamicFamilyConfig(
    val family: MetricFamily,
    val semanticVersion: String,
    val referencePolicy: NonDynamicReferencePolicy,
    val fixedReferenceCoordinate: Double?,
    val slopePriorMedian: Double?,
    val slopePriorLogSd: Double?,
    val trajectoryPriorSd: Double,
    val slackScalePriorMedian: Double,
    val slackScalePriorLogSd: Double,
    val noiseScalePriorMedian: Double,
    val noiseScalePriorLogSd: Double,
    val studentTDegreesOfFreedom: Double = 5.0,
    val trajectoryLearningMinimumSessions: Int = 3,
    val slopePartialMinimumSessions: Int = 3,
    val slopeDataInformedMinimumSessions: Int = 6,
    val slopePartialMinimumLogSpan: Double = ln(1.5),
    val slopeDataInformedMinimumLogSpan: Double = ln(2.0),
    val nuisanceLearningMinimumSessions: Int = 8,
    val nuisanceLearningMinimumObservations: Int = 12,
    val nuisanceDataInformedMinimumSessions: Int = 10,
    val nuisanceDataInformedMinimumObservations: Int = 20,
    val recentIndependentSessionWindow: Int = 12,
    val outputPriorMinimum: Double,
    val outputPriorMaximum: Double,
    val inputExtrapolationLogSdPerLogUnit: Double,
    val outputExtrapolationLogSdPerLogUnit: Double,
    val processLogSdPerSqrtSession: Double,
    val cadencePolicy: NonDynamicCadencePolicy,
    val durationContextPolicy: NonDynamicDurationContextPolicy,
    val bodyMassContextPolicy: NonDynamicBodyMassContextPolicy,
    val evidencePolicyIdentity: String = NonDynamicCapabilityV1.evidencePolicy.identity,
    val hierarchyMode: HierarchicalPoolingMode = HierarchicalPoolingMode.NO_POOLING,
) {
    init {
        require(family in NonDynamicCapabilityV1.supportedFamilies)
        require(semanticVersion.isNotBlank())
        require((slopePriorMedian == null) == (slopePriorLogSd == null))
        require(slopePriorMedian == null || slopePriorMedian > 0.0)
        require(slopePriorLogSd == null || slopePriorLogSd > 0.0)
        require(trajectoryPriorSd > 0.0)
        require(slackScalePriorMedian > 0.0 && slackScalePriorLogSd > 0.0)
        require(noiseScalePriorMedian > 0.0 && noiseScalePriorLogSd > 0.0)
        require(studentTDegreesOfFreedom > 2.0)
        require(recentIndependentSessionWindow > 0)
        require(outputPriorMinimum > 0.0 && outputPriorMaximum > outputPriorMinimum)
        require(inputExtrapolationLogSdPerLogUnit >= 0.0 && outputExtrapolationLogSdPerLogUnit >= 0.0)
        require(processLogSdPerSqrtSession >= 0.0)
        require(hierarchyMode == HierarchicalPoolingMode.NO_POOLING) { "7C v1 does not enable cross-profile pooling." }
        when (family) {
            MetricFamily.LOADED_HOLD -> {
                require(referencePolicy == NonDynamicReferencePolicy.FIXED_30_SECONDS_V1 && fixedReferenceCoordinate == 30.0)
                require(slopePriorMedian != null)
            }
            MetricFamily.DURATION_ONLY -> {
                require(referencePolicy == NonDynamicReferencePolicy.NO_REFERENCE_COORDINATE_V1 && fixedReferenceCoordinate == null)
                require(slopePriorMedian == null)
            }
            MetricFamily.REPEATED_CONTRACTION -> {
                require(referencePolicy == NonDynamicReferencePolicy.MEDIAN_OBSERVED_LOWER_V1 && fixedReferenceCoordinate == null)
                require(slopePriorMedian != null)
            }
            else -> error("Unsupported 7C family")
        }
    }

    val component: InferenceModelComponent
        get() = when (family) {
            MetricFamily.LOADED_HOLD -> InferenceModelComponent.HOLD_CAPABILITY
            MetricFamily.DURATION_ONLY -> InferenceModelComponent.DURATION_CAPABILITY
            MetricFamily.REPEATED_CONTRACTION -> InferenceModelComponent.REPEATED_CONTRACTION_CAPABILITY
            else -> error("Unsupported 7C family")
        }

    val mathematicalModelIdentity: InferenceMathematicalModelIdentity
        get() = InferenceMathematicalModelIdentity(
            family = "${family.storageValue}_profile_local_dynamic_frontier",
            semanticVersion = semanticVersion,
            definition = listOf(
                "family=${family.storageValue}",
                "form=${if (slopePriorMedian == null) "y=c+g*z-u+epsilon" else "y=c+g*z-b*x-u+epsilon"}",
                "reference=${referencePolicy.storageValue}:${fixedReferenceCoordinate ?: "fit_selected"}",
                "slopePrior=${slopePriorMedian?.let { "lognormal($it,$slopePriorLogSd)" } ?: "none"}",
                "trajectoryPrior=normal(0,$trajectoryPriorSd)",
                "slack=half_normal_prior_median_$slackScalePriorMedian",
                "noise=student_t_df_$studentTDegreesOfFreedom",
                "window=$recentIndependentSessionWindow",
                "hierarchy=${hierarchyMode.storageValue}",
                "cadence=${cadencePolicy.storageValue}",
                "durationContext=${durationContextPolicy.storageValue}",
                "bodyMassContext=${bodyMassContextPolicy.storageValue}",
                "extrapolation=$inputExtrapolationLogSdPerLogUnit,$outputExtrapolationLogSdPerLogUnit,$processLogSdPerSqrtSession",
                "evidence=$evidencePolicyIdentity",
            ).joinToString(";"),
        )

    fun toModelConfig(createdAt: Instant): ModelConfigDefinition = ModelConfigDefinition.create(
        component = component,
        modelFamily = "n_bio_7c_${family.storageValue}_frontier",
        modelName = "profile_local_lower_bound_dynamic_frontier",
        semanticVersion = semanticVersion,
        configSchemaVersion = 1,
        parameters = mapOf(
            "mathematicalModelIdentity" to mathematicalModelIdentity.identity,
            "family" to family.storageValue,
            "referencePolicy" to referencePolicy.storageValue,
            "fixedReferenceCoordinate" to (fixedReferenceCoordinate?.toString() ?: "none"),
            "slopePrior" to (slopePriorMedian?.let { "lognormal(median=$it,logSd=$slopePriorLogSd)" } ?: "none"),
            "trajectoryPrior" to "normal(mean=0,sd=$trajectoryPriorSd)",
            "trajectoryLearningMinimumSessions" to trajectoryLearningMinimumSessions.toString(),
            "slackScalePrior" to "lognormal(median=$slackScalePriorMedian,logSd=$slackScalePriorLogSd)",
            "noiseScalePrior" to "lognormal(median=$noiseScalePriorMedian,logSd=$noiseScalePriorLogSd)",
            "studentTDegreesOfFreedom" to studentTDegreesOfFreedom.toString(),
            "slopePartialIdentification" to "sessions>=$slopePartialMinimumSessions,logSpan>=$slopePartialMinimumLogSpan",
            "slopeDataInformedIdentification" to "sessions>=$slopeDataInformedMinimumSessions,logSpan>=$slopeDataInformedMinimumLogSpan",
            "nuisanceLearningUnlock" to "sessions>=$nuisanceLearningMinimumSessions,observations>=$nuisanceLearningMinimumObservations",
            "nuisanceDataInformed" to "sessions>=$nuisanceDataInformedMinimumSessions,observations>=$nuisanceDataInformedMinimumObservations",
            "recentIndependentSessionWindow" to recentIndependentSessionWindow.toString(),
            "outputPriorDomain" to "$outputPriorMinimum..$outputPriorMaximum",
            "inputExtrapolationLogSdPerLogUnit" to inputExtrapolationLogSdPerLogUnit.toString(),
            "outputExtrapolationLogSdPerLogUnit" to outputExtrapolationLogSdPerLogUnit.toString(),
            "processLogSdPerSqrtSession" to processLogSdPerSqrtSession.toString(),
            "cadencePolicy" to cadencePolicy.storageValue,
            "durationContextPolicy" to durationContextPolicy.storageValue,
            "bodyMassContextPolicy" to bodyMassContextPolicy.storageValue,
            "hierarchyMode" to hierarchyMode.storageValue,
            "successfulObservationSemantics" to NonDynamicSuccessfulObservationSemantics.LOWER_BOUND_DEMONSTRATION.storageValue,
            "evidencePolicyIdentity" to evidencePolicyIdentity,
            "actionPolicy" to "UNMODELLED",
        ),
        createdAt = createdAt,
    )
}

data class NonDynamicSolverConfig(
    val familyConfig: NonDynamicFamilyConfig,
    val solverFamily: InferenceSolverFamily,
    val retainedBasePosteriorMass: Double = 0.9995,
    val minimumRetainedBaseNodes: Int = 32,
    val maximumRetainedBaseNodes: Int = 1024,
    val frontierGridPoints: Int = 17,
    val slopeGridPoints: Int = 11,
    val trajectoryGridPoints: Int = 11,
    val nuisanceGridPoints: Int = 3,
    val slackQuadraturePoints: Int = 12,
    val slackQuadratureMaximumSd: Double = 6.0,
    val approximationVersion: String = "n-bio-7c-base-grid-trend-expansion-v1",
) {
    init {
        require(solverFamily in setOf(InferenceSolverFamily.DENSE_TENSOR_REFERENCE, InferenceSolverFamily.ADAPTIVE_SPARSE_TENSOR))
        require(retainedBasePosteriorMass in 0.95..1.0)
        require(minimumRetainedBaseNodes > 0 && maximumRetainedBaseNodes >= minimumRetainedBaseNodes)
        require(frontierGridPoints >= 9 && frontierGridPoints % 2 == 1)
        require(slopeGridPoints >= 7 && slopeGridPoints % 2 == 1)
        require(trajectoryGridPoints >= 7 && trajectoryGridPoints % 2 == 1)
        require(nuisanceGridPoints >= 3 && nuisanceGridPoints % 2 == 1)
        require(slackQuadraturePoints >= 8 && slackQuadratureMaximumSd >= 4.0)
    }

    val solverIdentity: InferenceSolverIdentity
        get() = InferenceSolverIdentity(
            solverFamily = solverFamily,
            semanticVersion = if (solverFamily == InferenceSolverFamily.DENSE_TENSOR_REFERENCE) {
                "n-bio-7c-dense-reference-v1"
            } else {
                "n-bio-7c-adaptive-sparse-v1"
            },
            computeBackend = InferenceComputeBackend.KOTLIN_JVM,
            deterministicReplay = true,
            approximationDefinition = listOf(
                approximationVersion,
                "frontierPoints=$frontierGridPoints",
                "slopePoints=$slopeGridPoints",
                "trajectoryPoints=$trajectoryGridPoints",
                "nuisancePoints=$nuisanceGridPoints",
                "slackQuadrature=$slackQuadraturePoints@$slackQuadratureMaximumSd",
                if (solverFamily == InferenceSolverFamily.ADAPTIVE_SPARSE_TENSOR) {
                    "retainedBaseMass=$retainedBasePosteriorMass|minBase=$minimumRetainedBaseNodes|maxBase=$maximumRetainedBaseNodes"
                } else "fullBaseSupport",
            ).joinToString("|"),
        )
}

data class NonDynamicPosteriorNode(
    val logFrontierAtReference: Double,
    val slope: Double?,
    val trajectory: Double,
    val slackScale: Double,
    val noiseScale: Double,
    val posteriorWeight: Double,
) {
    init {
        require(logFrontierAtReference.isFinite())
        require(slope == null || slope.isFinite() && slope > 0.0)
        require(trajectory.isFinite())
        require(slackScale.isFinite() && slackScale > 0.0)
        require(noiseScale.isFinite() && noiseScale > 0.0)
        require(posteriorWeight.isFinite() && posteriorWeight >= 0.0)
    }
}

data class NonDynamicParameterPosterior(
    val summary: PosteriorSummary,
    val identification: DynamicParameterIdentification,
    val semanticUnit: String,
)

data class NonDynamicCapabilityFit(
    val executionProfileVersionId: ExecutionProfileVersionId,
    val side: Laterality,
    val family: MetricFamily,
    val inferenceHorizon: Instant,
    val referenceCoordinate: Double?,
    val canonicalUnit: UnitId,
    val modelConfigId: ModelConfigId,
    val mathematicalModelIdentity: InferenceMathematicalModelIdentity,
    val solverDiagnostics: InferenceSolverDiagnostics,
    val evidencePolicyIdentity: String,
    val support: EvidenceSupport,
    val observedInputMin: Double?,
    val observedInputMax: Double?,
    val observedOutputMin: Double,
    val observedOutputMax: Double,
    val frontierAtReference: PosteriorEstimate,
    val slope: NonDynamicParameterPosterior?,
    val trajectory: NonDynamicParameterPosterior,
    val slackScale: NonDynamicParameterPosterior,
    val noiseScale: NonDynamicParameterPosterior,
    val posteriorNodes: List<NonDynamicPosteriorNode>,
    val selectedObservationIds: List<String>,
    val selectedSessionIds: List<String>,
    val originalBaseNodeCount: Int,
    val retainedBaseNodeCount: Int,
    val warnings: Set<String>,
) {
    init {
        require(family in NonDynamicCapabilityV1.supportedFamilies)
        require(support.observationCount > 0)
        require(observedOutputMin > 0.0 && observedOutputMax >= observedOutputMin)
        require(frontierAtReference.summary != null)
        require(posteriorNodes.isNotEmpty())
        require(originalBaseNodeCount > 0 && retainedBaseNodeCount in 1..originalBaseNodeCount)
        require(kotlin.math.abs(posteriorNodes.sumOf { it.posteriorWeight } - 1.0) <= 1e-8)
        if (family == MetricFamily.DURATION_ONLY) require(referenceCoordinate == null && slope == null)
        if (family != MetricFamily.DURATION_ONLY) require(referenceCoordinate != null && referenceCoordinate > 0.0 && slope != null)
    }

    val positiveTrajectoryProbability: Double
        get() = posteriorNodes.filter { it.trajectory > 0.0 }.sumOf { it.posteriorWeight }.coerceIn(0.0, 1.0)
}

sealed interface NonDynamicCapabilityQuery {
    val independentSessionOffset: Int
}

data class LoadedHoldCapabilityQuery(
    val durationSeconds: Double,
    override val independentSessionOffset: Int = 0,
) : NonDynamicCapabilityQuery {
    init { require(durationSeconds.isFinite() && durationSeconds > 0.0) }
}

data class DurationOnlyCapabilityQuery(
    override val independentSessionOffset: Int = 0,
) : NonDynamicCapabilityQuery

data class RepeatedContractionCapabilityQuery(
    val cycles: Int,
    override val independentSessionOffset: Int = 0,
) : NonDynamicCapabilityQuery {
    init { require(cycles > 0) }
}

object NonDynamicCapabilityV1 {
    val supportedFamilies = setOf(MetricFamily.LOADED_HOLD, MetricFamily.DURATION_ONLY, MetricFamily.REPEATED_CONTRACTION)
    val evidencePolicy = NonDynamicEvidencePolicy()

    val loadedHold = NonDynamicFamilyConfig(
        family = MetricFamily.LOADED_HOLD,
        semanticVersion = "n-bio-7c-loaded-hold-frontier-v1",
        referencePolicy = NonDynamicReferencePolicy.FIXED_30_SECONDS_V1,
        fixedReferenceCoordinate = 30.0,
        slopePriorMedian = 0.55,
        slopePriorLogSd = 0.75,
        trajectoryPriorSd = 0.05,
        slackScalePriorMedian = 0.15,
        slackScalePriorLogSd = 0.65,
        noiseScalePriorMedian = 0.06,
        noiseScalePriorLogSd = 0.55,
        outputPriorMinimum = 0.05,
        outputPriorMaximum = 5_000.0,
        inputExtrapolationLogSdPerLogUnit = 0.28,
        outputExtrapolationLogSdPerLogUnit = 0.18,
        processLogSdPerSqrtSession = 0.05,
        cadencePolicy = NonDynamicCadencePolicy.NOT_APPLICABLE,
        durationContextPolicy = NonDynamicDurationContextPolicy.MODEL_COORDINATE,
        bodyMassContextPolicy = NonDynamicBodyMassContextPolicy.RESOLVER_INPUT_WHEN_EXPLICIT,
    )

    val durationOnly = NonDynamicFamilyConfig(
        family = MetricFamily.DURATION_ONLY,
        semanticVersion = "n-bio-7c-duration-only-frontier-v1",
        referencePolicy = NonDynamicReferencePolicy.NO_REFERENCE_COORDINATE_V1,
        fixedReferenceCoordinate = null,
        slopePriorMedian = null,
        slopePriorLogSd = null,
        trajectoryPriorSd = 0.06,
        slackScalePriorMedian = 0.20,
        slackScalePriorLogSd = 0.70,
        noiseScalePriorMedian = 0.08,
        noiseScalePriorLogSd = 0.60,
        outputPriorMinimum = 1.0,
        outputPriorMaximum = 86_400.0,
        inputExtrapolationLogSdPerLogUnit = 0.0,
        outputExtrapolationLogSdPerLogUnit = 0.0,
        processLogSdPerSqrtSession = 0.06,
        cadencePolicy = NonDynamicCadencePolicy.NOT_APPLICABLE,
        durationContextPolicy = NonDynamicDurationContextPolicy.MODEL_OUTPUT,
        bodyMassContextPolicy = NonDynamicBodyMassContextPolicy.PRESERVE_NOT_MODEL_DRIVING_V1,
    )

    val repeatedContraction = NonDynamicFamilyConfig(
        family = MetricFamily.REPEATED_CONTRACTION,
        semanticVersion = "n-bio-7c-repeated-contraction-frontier-v1",
        referencePolicy = NonDynamicReferencePolicy.MEDIAN_OBSERVED_LOWER_V1,
        fixedReferenceCoordinate = null,
        slopePriorMedian = 0.30,
        slopePriorLogSd = 0.90,
        trajectoryPriorSd = 0.05,
        slackScalePriorMedian = 0.15,
        slackScalePriorLogSd = 0.75,
        noiseScalePriorMedian = 0.07,
        noiseScalePriorLogSd = 0.65,
        outputPriorMinimum = 0.05,
        outputPriorMaximum = 5_000.0,
        inputExtrapolationLogSdPerLogUnit = 0.28,
        outputExtrapolationLogSdPerLogUnit = 0.18,
        processLogSdPerSqrtSession = 0.05,
        cadencePolicy = NonDynamicCadencePolicy.PRESERVE_FIXED_CONTEXT_FAIL_ON_VARIATION_V1,
        durationContextPolicy = NonDynamicDurationContextPolicy.PRESERVE_NOT_INDEPENDENT_PARAMETER_V1,
        bodyMassContextPolicy = NonDynamicBodyMassContextPolicy.RESOLVER_INPUT_WHEN_EXPLICIT,
    )

    fun configFor(family: MetricFamily): NonDynamicFamilyConfig = when (family) {
        MetricFamily.LOADED_HOLD -> loadedHold
        MetricFamily.DURATION_ONLY -> durationOnly
        MetricFamily.REPEATED_CONTRACTION -> repeatedContraction
        else -> throw IllegalArgumentException("Unsupported N-BIO-7C capability family ${family.storageValue}")
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
