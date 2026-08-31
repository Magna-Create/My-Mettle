package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import java.time.Instant

/**
 * Mathematical model identity is deliberately independent of the numerical solver/backend.
 * Changing state equations, priors, likelihoods, pooling, or policy relationships requires a new identity.
 */
data class InferenceMathematicalModelIdentity(
    val family: String,
    val semanticVersion: String,
    val definition: String,
) {
    init {
        require(family.isNotBlank() && semanticVersion.isNotBlank() && definition.isNotBlank())
    }

    val identity: String = "$family|$semanticVersion|$definition"
}

enum class InferenceSolverFamily(val storageValue: String) {
    DENSE_TENSOR_REFERENCE("dense_tensor_reference"),
    SEQUENTIAL_TENSOR("sequential_tensor"),
    ADAPTIVE_SPARSE_TENSOR("adaptive_sparse_tensor"),
    LOW_RANK_TENSOR("low_rank_tensor"),
    MOMENT_MATCHING("moment_matching"),
    SIGMA_POINT("sigma_point"),
    SEQUENTIAL_LAPLACE("sequential_laplace"),
    PARTICLE_MIXTURE("particle_mixture"),
}

enum class InferenceComputeBackend(val storageValue: String) {
    KOTLIN_JVM("kotlin_jvm"),
    NATIVE_CPP_SCALAR("native_cpp_scalar"),
    NATIVE_CPP_VECTORIZED("native_cpp_vectorized"),
    NATIVE_CPP_MULTICORE("native_cpp_multicore"),
    VULKAN_COMPUTE("vulkan_compute"),
    LITERT_ACCELERATOR("litert_accelerator"),
}

data class InferenceSolverIdentity(
    val solverFamily: InferenceSolverFamily,
    val semanticVersion: String,
    val computeBackend: InferenceComputeBackend,
    val deterministicReplay: Boolean,
    val approximationDefinition: String,
) {
    init {
        require(semanticVersion.isNotBlank() && approximationDefinition.isNotBlank())
    }

    val identity: String = listOf(
        solverFamily.storageValue,
        semanticVersion,
        computeBackend.storageValue,
        deterministicReplay.toString(),
        approximationDefinition,
    ).joinToString("|")
}

enum class InferencePosteriorRepresentation(val storageValue: String) {
    WEIGHTED_DENSE_NODES("weighted_dense_nodes"),
    WEIGHTED_SPARSE_NODES("weighted_sparse_nodes"),
    LOW_RANK_FACTORS("low_rank_factors"),
    GAUSSIAN_MOMENTS("gaussian_moments"),
    SIGMA_POINTS("sigma_points"),
    PARTICLES("particles"),
    MIXTURE("mixture"),
}

data class InferenceSolverDiagnostics(
    val solverIdentity: InferenceSolverIdentity,
    val posteriorRepresentation: InferencePosteriorRepresentation,
    val evaluatedNodeCount: Long? = null,
    val effectiveNodeCount: Double? = null,
    val updateRuntimeNanos: Long? = null,
    val peakWorkingBytes: Long? = null,
    val approximationFailure: String? = null,
    val notes: Set<String> = emptySet(),
) {
    init {
        require(evaluatedNodeCount == null || evaluatedNodeCount >= 0)
        require(effectiveNodeCount == null || effectiveNodeCount.isFinite() && effectiveNodeCount > 0.0)
        require(updateRuntimeNanos == null || updateRuntimeNanos >= 0)
        require(peakWorkingBytes == null || peakWorkingBytes >= 0)
        require(approximationFailure == null || approximationFailure.isNotBlank())
        require(notes.all { it.isNotBlank() })
    }
}

enum class DynamicStateTransitionFamily(val storageValue: String) {
    STATIONARY("stationary"),
    LINEAR_TREND("linear_trend"),
    RANDOM_WALK("random_walk"),
    LOCAL_LINEAR_TREND("local_linear_trend"),
    ROBUST_STATE_SPACE("robust_state_space"),
    SWITCHING_OR_CHANGEPOINT("switching_or_changepoint"),
}

enum class DynamicStateParameterRole(val storageValue: String) {
    DYNAMIC_STATE("dynamic_state"),
    STATIC_PERSISTENT_PARAMETER("static_persistent_parameter"),
    PROCESS_UNCERTAINTY("process_uncertainty"),
    OBSERVATION_UNCERTAINTY("observation_uncertainty"),
    ACTION_POLICY_PARAMETER("action_policy_parameter"),
    NUISANCE_PARAMETER("nuisance_parameter"),
}

data class DynamicStateParameterSupport(
    val parameterName: String,
    val role: DynamicStateParameterRole,
    val identification: DynamicParameterIdentification,
    val poolingMode: HierarchicalPoolingMode = HierarchicalPoolingMode.NO_POOLING,
    val semanticDefinition: String,
) {
    init {
        require(parameterName.isNotBlank() && semanticDefinition.isNotBlank())
    }
}

data class DynamicInferenceStateContract(
    val mathematicalModel: InferenceMathematicalModelIdentity,
    val transitionFamily: DynamicStateTransitionFamily,
    val inferenceHorizon: Instant,
    val previousPosteriorAvailable: Boolean,
    val stateParameters: List<DynamicStateParameterSupport>,
    val contextConsumption: String,
) {
    init {
        require(stateParameters.map { it.parameterName }.distinct().size == stateParameters.size)
        require(contextConsumption.isNotBlank())
    }
}

enum class SemanticRegimeContinuity(val storageValue: String) {
    VERIFIED_CONTINUITY("verified_continuity"),
    ASSUMED_CONTINUITY("assumed_continuity"),
    DERIVED_DISCONTINUITY_SUSPECTED("derived_discontinuity_suspected"),
    KNOWN_BOUNDARY("known_boundary"),
}

enum class SemanticBoundarySource(val storageValue: String) {
    IMMUTABLE_PROFILE_VERSION("immutable_profile_version"),
    EXPLICIT_MIGRATION_CORRECTION("explicit_migration_correction"),
    EQUIPMENT_OR_SETUP_METADATA("equipment_or_setup_metadata"),
    LATERALITY_OR_ENTRY_BASIS("laterality_or_entry_basis"),
    STATISTICAL_SUSPICION_ONLY("statistical_suspicion_only"),
}

data class SemanticRegimeDecision(
    val regimeId: String,
    val executionProfileVersionId: ExecutionProfileVersionId,
    val side: Laterality,
    val continuity: SemanticRegimeContinuity,
    val source: SemanticBoundarySource,
    val effectiveAt: Instant?,
    val derivedOnly: Boolean,
    val reason: String,
) {
    init {
        require(regimeId.isNotBlank() && reason.isNotBlank())
        if (source == SemanticBoundarySource.STATISTICAL_SUSPICION_ONLY) {
            require(derivedOnly) { "Statistical changepoint suspicion cannot become canonical history automatically." }
            require(continuity == SemanticRegimeContinuity.DERIVED_DISCONTINUITY_SUSPECTED)
        }
        if (continuity == SemanticRegimeContinuity.KNOWN_BOUNDARY) {
            require(source != SemanticBoundarySource.STATISTICAL_SUSPICION_ONLY) {
                "Known semantic boundaries require metadata or explicit correction, not statistics alone."
            }
        }
    }
}

enum class HierarchicalPoolingMode(val storageValue: String) {
    NO_POOLING("no_pooling"),
    USER_LEVEL_WEAK_POOLING("user_level_weak_pooling"),
    SEMANTIC_FAMILY_WEAK_POOLING("semantic_family_weak_pooling"),
    PROFILE_SPECIFIC("profile_specific"),
}

enum class HierarchicalParameterKind(val storageValue: String, val permitsCrossProfilePooling: Boolean) {
    ABSOLUTE_RESISTANCE_CAPABILITY("absolute_resistance_capability", false),
    EQUIPMENT_SPECIFIC_COORDINATE("equipment_specific_coordinate", false),
    SIDE_SPECIFIC_CAPABILITY("side_specific_capability", false),
    REP_RANGE_SLOPE("rep_range_slope", true),
    OBSERVATION_VARIABILITY("observation_variability", true),
    PROCESS_VOLATILITY("process_volatility", true),
    ACTION_POLICY_BEHAVIOUR("action_policy_behaviour", true),
    OUTLIER_PREVALENCE("outlier_prevalence", true),
    NUISANCE_PARAMETER("nuisance_parameter", true),
}

data class HierarchicalPriorContract(
    val parameterKind: HierarchicalParameterKind,
    val poolingMode: HierarchicalPoolingMode,
    val familyMappingVersion: String? = null,
    val priorSourceIdentity: String? = null,
) {
    init {
        if (!parameterKind.permitsCrossProfilePooling) {
            require(poolingMode in setOf(HierarchicalPoolingMode.NO_POOLING, HierarchicalPoolingMode.PROFILE_SPECIFIC)) {
                "Raw physical capability/coordinate may not be pooled across profiles."
            }
        }
        if (poolingMode == HierarchicalPoolingMode.SEMANTIC_FAMILY_WEAK_POOLING) {
            require(!familyMappingVersion.isNullOrBlank()) { "Semantic-family pooling requires explicit versioned mapping." }
        }
    }
}

/** Capability answers what is plausibly available. It never implies what action the user will choose. */
data class CapabilityPosteriorQuery(
    val executionProfileVersionId: ExecutionProfileVersionId,
    val side: Laterality,
    val inferenceHorizon: Instant,
    val repetitions: Double,
) {
    init { require(repetitions.isFinite() && repetitions > 0.0) }
}

/**
 * Policy inputs are factual/declared constraints. Unknown fields remain null; no RIR/RPE is fabricated.
 * The contract is intentionally model-agnostic and non-authoritative in N-BIO-7B.X.
 */
data class TrainingActionPolicyInput(
    val executionProfileVersionId: ExecutionProfileVersionId,
    val side: Laterality,
    val inferenceHorizon: Instant,
    val previousSuccessfulLoadKg: Double? = null,
    val previousSuccessfulRepetitions: Int? = null,
    val prescribedLoadKg: Double? = null,
    val targetRepLower: Int? = null,
    val targetRepUpper: Int? = null,
    val setOrdinal: Int? = null,
    val exerciseOrder: Int? = null,
    val manualOverridePresent: Boolean = false,
    val feasibleLoadKg: List<Double> = emptyList(),
    val programmeIntentId: String? = null,
) {
    init {
        require(previousSuccessfulLoadKg == null || previousSuccessfulLoadKg > 0.0)
        require(previousSuccessfulRepetitions == null || previousSuccessfulRepetitions > 0)
        require(prescribedLoadKg == null || prescribedLoadKg > 0.0)
        require(targetRepLower == null || targetRepLower > 0)
        require(targetRepUpper == null || targetRepUpper > 0)
        require(targetRepLower == null || targetRepUpper == null || targetRepLower <= targetRepUpper)
        require(setOrdinal == null || setOrdinal >= 0)
        require(exerciseOrder == null || exerciseOrder >= 0)
        require(feasibleLoadKg.all { it.isFinite() && it > 0.0 })
    }
}

data class PerformedActionPrediction(
    val loadKg: PosteriorSummary?,
    val repetitions: PosteriorSummary?,
    val modelIdentity: String,
    val evidenceStatus: DynamicParameterIdentification,
) {
    init { require(modelIdentity.isNotBlank()) }
}

interface TrainingActionPolicyModel {
    val mathematicalModelIdentity: InferenceMathematicalModelIdentity
    fun predict(input: TrainingActionPolicyInput, capability: PosteriorEstimate): PerformedActionPrediction
}

/** Explicit N-BIO-7B.X placeholder: architecture exists, behaviour does not. */
object UnmodelledTrainingActionPolicy : TrainingActionPolicyModel {
    override val mathematicalModelIdentity = InferenceMathematicalModelIdentity(
        family = "training_action_policy",
        semanticVersion = "unmodelled-v1",
        definition = "no performed-action distribution; capability slack is not treated as user action policy",
    )

    override fun predict(input: TrainingActionPolicyInput, capability: PosteriorEstimate): PerformedActionPrediction =
        PerformedActionPrediction(
            loadKg = null,
            repetitions = null,
            modelIdentity = mathematicalModelIdentity.identity,
            evidenceStatus = DynamicParameterIdentification.FIXED_BY_CONFIG,
        )
}

enum class InferenceLearningLevel(val storageValue: String) {
    LEVEL_1_PERSONAL_ADAPTATION("level_1_personal_adaptation"),
    LEVEL_2_MODEL_IMPROVEMENT("level_2_model_improvement"),
}

data class InferenceDependencyNode(
    val id: String,
    val kind: String,
    val sourceIds: Set<String>,
    val modelIdentity: String?,
    val solverIdentity: String?,
) {
    init {
        require(id.isNotBlank() && kind.isNotBlank())
        require(sourceIds.all { it.isNotBlank() })
    }
}

/** Minimal factor/dependency abstraction: supports local invalidation without requiring a factor-graph library. */
class InferenceDependencyIndex(nodes: Collection<InferenceDependencyNode>) {
    private val byId = nodes.associateBy { it.id }
    private val dependants = buildMap<String, MutableSet<String>> {
        nodes.forEach { node -> node.sourceIds.forEach { source -> getOrPut(source) { linkedSetOf() }.add(node.id) } }
    }

    init { require(byId.size == nodes.size) { "Dependency node ids must be unique." } }

    fun invalidatedBy(sourceId: String): Set<String> {
        val invalidated = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(sourceId)
        while (queue.isNotEmpty()) {
            val source = queue.removeFirst()
            dependants[source].orEmpty().forEach { dependant ->
                if (invalidated.add(dependant)) queue.add(dependant)
            }
        }
        return invalidated
    }

    fun node(id: String): InferenceDependencyNode? = byId[id]
}
