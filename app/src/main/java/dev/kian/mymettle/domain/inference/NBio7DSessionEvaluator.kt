package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.performance.MetricFamily
import java.time.Instant

/** Immutable N-BIO-7D v1 config definitions, pre-registered before real-history output inspection. */
object NBio7DModelConfigs {
    val CREATED_AT: Instant = Instant.parse("2026-09-02T00:00:00Z")

    fun definitions(
        config: NBio7DConfig = NBio7DConfig(),
        createdAt: Instant = CREATED_AT,
    ): List<ModelConfigDefinition> = listOf(
        ModelConfigDefinition.create(
            component = InferenceModelComponent.SET_DEMAND,
            modelFamily = "conditional_frontier_gap",
            modelName = "pre_session_joint_posterior_frontier_gap",
            semanticVersion = NBio7DModelIdentity.DEMAND,
            configSchemaVersion = 1,
            parameters = mapOf(
                "semanticDefinition" to "conditional_on_performed_set_distance_from_contemporaneous_profile_capability_frontier",
                "dynamicResistanceDeltaLog" to config.dynamicResistanceDeltaLog.toString(),
                "loadedHoldDeltaLog" to config.loadedHoldDeltaLog.toString(),
                "repeatedContractionDeltaLog" to config.repeatedContractionDeltaLog.toString(),
                "durationOnlyDeltaLog" to config.durationOnlyDeltaLog.toString(),
                "contradictionProbabilityThreshold" to config.contradictionProbabilityThreshold.toString(),
                "capabilityReference" to "PRE_SESSION_CAUSAL_POSTERIOR_SHARED_WITHIN_SESSION",
                "targetSessionCoordinate" to "NEXT_PROFILE_LOCAL_INDEPENDENT_SESSION_PLUS_ONE",
                "actionPolicy" to "UNMODELLED",
                "genericSlackAsWorkingSetPolicy" to "FORBIDDEN",
                "rirRpeFailureReconstruction" to "FORBIDDEN_WITHOUT_EXPLICIT_FUTURE_TYPED_EVIDENCE_MODEL",
                "retrospectiveSmoothing" to "NOT_IMPLEMENTED_V1",
                "empiricalCalibration" to NBio7DModelIdentity.EMPIRICAL_STATUS,
            ),
            createdAt = createdAt,
        ),
        ModelConfigDefinition.create(
            component = InferenceModelComponent.EXPOSURE,
            modelFamily = "historical_recruitment_projection",
            modelName = "exact_independent_recruitment_weight",
            semanticVersion = NBio7DModelIdentity.EXPOSURE,
            configSchemaVersion = 1,
            parameters = mapOf(
                "formula" to "Exposure_sm=historicalRecruitmentWeight_em",
                "historicalRecruitmentVersion" to "EXACT_EXECUTION_PROFILE_BOUND_VERSION",
                "normaliseAcrossMuscles" to "false",
                "conserveAcrossMuscles" to "false",
                "roleMultiplier" to "none",
                "externalLoadContribution" to "none",
                "warmups" to "exclude",
                "draftOrSuperseded" to "exclude",
            ),
            createdAt = createdAt,
        ),
        ModelConfigDefinition.create(
            component = InferenceModelComponent.EFFECTIVE_DOSE,
            modelFamily = "recruitment_weighted_task_demand_projection",
            modelName = "posterior_high_demand_band_exposure_projection",
            semanticVersion = NBio7DModelIdentity.EFFECTIVE_DOSE,
            configSchemaVersion = 1,
            parameters = mapOf(
                "nodeFormula" to "EffectiveDose_smj=Exposure_sm*I(frontierGap_sj<=delta_family)",
                "distributionPropagation" to "TRANSFORM_SHARED_POSTERIOR_NODES_NOT_MEDIAN",
                "taskDemandLocalMuscleSemantics" to "MODELLED_PROXY_NOT_LOCAL_FAILURE_OR_FIBRE_STIMULUS",
                "unsupportedDemand" to "NULL_UNRESOLVED",
                "frontierContradiction" to "NULL_UNRESOLVED",
                "broadDemand" to "PRESERVE_BROAD_POSTERIOR",
                "empiricalCalibration" to NBio7DModelIdentity.EMPIRICAL_STATUS,
            ),
            createdAt = createdAt,
        ),
        ModelConfigDefinition.create(
            component = InferenceModelComponent.SESSION_DOSE,
            modelFamily = "muscle_local_shared_posterior_session_aggregation",
            modelName = "raw_sum_plus_log_concave_candidate",
            semanticVersion = NBio7DModelIdentity.SESSION_DOSE,
            configSchemaVersion = 1,
            parameters = mapOf(
                "rawFormula" to "RawSessionDose_m=sum_s(EffectiveDose_sm)",
                "withinStreamDependence" to "SHARED_CAPABILITY_POSTERIOR_NODEWISE",
                "crossStreamDependence" to "INDEPENDENCE_APPROXIMATION_AFTER_WITHIN_STREAM_JOINT_AGGREGATION",
                "maxIndependentConvolutionNodes" to config.maxIndependentConvolutionNodes.toString(),
                "supportCompression" to "DETERMINISTIC_EQUAL_MASS_IF_REQUIRED",
                "concaveFormula" to "tau*ln(1+X/tau)",
                "tau" to config.tau.toString(),
                "tauSemantics" to "ENGINEERING_CANDIDATE_NOT_MRV_OR_FAILURE_CAPACITY",
                "persistence" to "ROOM14_RAW_SESSION_POSTERIOR_PLUS_DETERMINISTIC_CONCAVE_REBUILD",
                "partiallyResolved" to "RESOLVED_SUBTOTAL_TYPED_NOT_COMPLETE_TOTAL",
                "unresolvedSet" to "NEVER_ZERO_FILL",
                "acrossSessionBiology" to "NONE",
                "empiricalCalibration" to NBio7DModelIdentity.EMPIRICAL_STATUS,
            ),
            createdAt = createdAt,
        ),
    )
}

data class NBio7DSetInput(
    val setObservationId: String,
    val capabilityStreamKey: String,
    val family: MetricFamily,
    /** Family-appropriate observed output already expressed in the accepted log coordinate. */
    val logObservedPerformance: Double,
    /** Null/empty means no contemporaneous pre-session capability is available. */
    val logFrontierNodes: List<WeightedScalarNode>?,
    val inheritedDemandSupport: SetDemandStructuralSupport,
    /** Exact historical recruitment allocations. They are independent and are never normalised. */
    val exposures: List<MuscleExposure>,
) {
    init {
        require(setObservationId.isNotBlank())
        require(capabilityStreamKey.isNotBlank())
        require(logObservedPerformance.isFinite())
        require(exposures.map { it.muscleSegmentId to it.side }.distinct().size == exposures.size)
    }
}

data class NBio7DSetResult(
    val setObservationId: String,
    val capabilityStreamKey: String,
    val demand: SetDemandPosterior,
    val muscleDoses: List<EffectiveDosePosterior>,
)

data class NBio7DMuscleSessionKey(
    val muscleSegmentId: String,
    val side: String,
) {
    init {
        require(muscleSegmentId.isNotBlank())
        require(side.isNotBlank())
    }
}

data class NBio7DMuscleSessionResult(
    val key: NBio7DMuscleSessionKey,
    val dose: SessionDosePosterior,
)

data class NBio7DSessionResult(
    val setResults: List<NBio7DSetResult>,
    val muscleResults: List<NBio7DMuscleSessionResult>,
) {
    val demandSupportCounts: Map<SetDemandStructuralSupport, Int>
        get() = setResults.groupingBy { it.demand.structuralSupport }.eachCount()
    val exposureCount: Int get() = setResults.sumOf { it.muscleDoses.size }
    val effectiveDoseResolvedCount: Int get() = setResults.sumOf { result -> result.muscleDoses.count { it.isResolvedEnoughToAggregate } }
    val effectiveDoseUnresolvedCount: Int get() = exposureCount - effectiveDoseResolvedCount
}

/**
 * Pure one-session N-BIO-7D evaluator. It consumes already-projected pre-session capability nodes
 * and exact historical recruitment weights. It never fits/updates capability and has no 7E state.
 */
class NBio7DSessionEvaluator(
    private val config: NBio7DConfig = NBio7DConfig(),
) {
    fun evaluate(inputs: List<NBio7DSetInput>): NBio7DSessionResult {
        require(inputs.map { it.setObservationId }.distinct().size == inputs.size) {
            "A session cannot evaluate the same set observation twice."
        }
        val setResults = inputs.map { input ->
            val demand = if (input.logFrontierNodes.isNullOrEmpty() ||
                input.inheritedDemandSupport == SetDemandStructuralSupport.UNSUPPORTED
            ) {
                NBio7DPosteriorMath.unsupportedDemand(input.family, config)
            } else {
                NBio7DPosteriorMath.setDemandFromLogFrontier(
                    family = input.family,
                    logFrontierNodes = input.logFrontierNodes,
                    logObservedPerformance = input.logObservedPerformance,
                    inheritedSupport = input.inheritedDemandSupport,
                    config = config,
                )
            }
            NBio7DSetResult(
                setObservationId = input.setObservationId,
                capabilityStreamKey = input.capabilityStreamKey,
                demand = demand,
                muscleDoses = input.exposures.map { NBio7DPosteriorMath.effectiveDose(it, demand) },
            )
        }

        val doseContributions = buildList {
            setResults.forEach { set ->
                set.muscleDoses.forEach { dose ->
                    add(
                        DoseContribution(
                            streamKey = set.capabilityStreamKey,
                            setObservationId = set.setObservationId,
                            key = NBio7DMuscleSessionKey(dose.exposure.muscleSegmentId, dose.exposure.side),
                            dose = dose,
                        ),
                    )
                }
            }
        }
        val muscleResults = doseContributions
            .groupBy(DoseContribution::key)
            .entries
            .sortedWith(compareBy({ it.key.muscleSegmentId }, { it.key.side }))
            .map { (key, contributions) ->
                val resolved = contributions.filter { it.dose.isResolvedEnoughToAggregate }
                val streamNodes = resolved
                    .groupBy(DoseContribution::streamKey)
                    .entries
                    .sortedBy { it.key }
                    .map { (_, stream) ->
                        NBio7DPosteriorMath.aggregateSharedStream(stream.map { it.dose })
                    }
                NBio7DMuscleSessionResult(
                    key = key,
                    dose = NBio7DPosteriorMath.sessionDose(
                        resolvedStreamNodes = streamNodes,
                        contributingSetCount = contributions.size,
                        unresolvedSetCount = contributions.size - resolved.size,
                        config = config,
                    ),
                )
            }

        return NBio7DSessionResult(
            setResults = setResults.sortedBy { it.setObservationId },
            muscleResults = muscleResults,
        )
    }

    private data class DoseContribution(
        val streamKey: String,
        val setObservationId: String,
        val key: NBio7DMuscleSessionKey,
        val dose: EffectiveDosePosterior,
    )
}
