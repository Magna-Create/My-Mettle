package dev.kian.mymettle.inference

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.CapabilityParameterStateEntity
import dev.kian.mymettle.data.local.entity.CapabilityStateEntity
import dev.kian.mymettle.data.local.entity.InferenceModelManifestEntity
import dev.kian.mymettle.data.local.entity.InferenceModelManifestEntryEntity
import dev.kian.mymettle.data.local.entity.InferenceRunEntity
import dev.kian.mymettle.data.local.entity.ModelConfigDefinitionEntity
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.InferenceExecutionMode
import dev.kian.mymettle.domain.inference.InferenceModelComponent
import dev.kian.mymettle.domain.inference.InferenceRunId
import dev.kian.mymettle.domain.inference.InferenceSemanticsMode
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.domain.inference.ModelConfigId
import dev.kian.mymettle.domain.inference.ModelManifest
import dev.kian.mymettle.domain.inference.ModelManifestId
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.BenchmarkV0ModelManifestFactory
import dev.kian.mymettle.engine.inference.DynamicTrendCandidateV2Solver
import dev.kian.mymettle.engine.inference.NeutralPriorMuscleStateUpdater
import dev.kian.mymettle.engine.inference.ObservedPerformanceTranslationModel
import dev.kian.mymettle.engine.inference.WeightedWorkingSetStimulusEstimator
import java.time.Instant
import java.util.UUID

/**
 * Room14 persistence boundary for N-BIO-7B.X Candidate-v2 SHADOW state.
 *
 * This is deliberately separate from [DynamicCapabilityShadowRepository]. Candidate v1 keeps its
 * historical codec/repository contract unchanged, while Candidate v2 persists solver-aware joint
 * frontier/trajectory state through the generic capability_state + capability_parameter_state
 * tables that already exist in Room14.
 *
 * The scalar capability_state posterior is the profile-local frontier at the latest selected
 * session (z=0). The complete joint posterior, mathematical-model identity, solver identity and
 * solver diagnostics are encoded by [DynamicTrendCapabilityParameterCodec]. Raw observations are
 * never copied into derived state.
 */
class DynamicTrendCapabilityShadowRepository(
    private val database: MyMettleDatabase,
    private val solver: DynamicTrendCandidateV2Solver,
    private val clock: () -> Instant = Instant::now,
    private val idFactory: () -> String = { "inference_shadow_7bx_v2_${UUID.randomUUID()}" },
) {
    private val dao get() = database.inferenceDao()

    suspend fun persist(
        userProfileId: String,
        fit: DynamicTrendFrontierFit,
        semanticsMode: InferenceSemanticsMode = InferenceSemanticsMode.HISTORICAL_SEMANTICS,
    ): InferenceRunId = database.withTransaction {
        require(semanticsMode == InferenceSemanticsMode.HISTORICAL_SEMANTICS) {
            "Candidate-v2 development shadow persistence requires explicit historical semantics."
        }
        require(userProfileId in dao.userProfileIds()) { "User profile $userProfileId does not exist." }
        require(fit.mathematicalModelIdentity == solver.mathematicalModelIdentity) {
            "Fit mathematical identity does not match the selected Candidate-v2 solver."
        }
        require(fit.solverDiagnostics.solverIdentity == solver.solverIdentity) {
            "Fit solver identity does not match the selected Candidate-v2 solver."
        }
        val reference = dao.latestReferenceProfile()
            ?: throw InferenceException("Runtime reference profile has not been seeded.")
        val candidateConfig = candidateConfig()
        require(fit.modelConfigId == candidateConfig.id) {
            "Candidate-v2 fit/config identity mismatch; refusing shadow persistence."
        }
        val manifestBundle = candidateManifest(reference.modelVersion, candidateConfig)
        persistConfigsAndManifest(manifestBundle.configs, manifestBundle.manifest)

        val runId = InferenceRunId(idFactory())
        val calculatedAt = clock()
        dao.insertInferenceRun(
            InferenceRunEntity(
                id = runId.value,
                userProfileId = userProfileId,
                modelVersion = SHADOW_RUN_MODEL_VERSION,
                referenceProfileId = reference.id,
                referenceProfileVersion = reference.version,
                referenceModelVersion = reference.modelVersion,
                recruitmentModelVersion = RoomInferenceRepository.RECRUITMENT_MODEL_VERSION,
                stimulusModelVersion = WeightedWorkingSetStimulusEstimator.MODEL_VERSION,
                muscleStateModelVersion = NeutralPriorMuscleStateUpdater.MODEL_VERSION,
                exerciseTranslationModelVersion = ObservedPerformanceTranslationModel.MODEL_VERSION,
                modelManifestId = manifestBundle.manifest.id.value,
                executionMode = InferenceExecutionMode.SHADOW.storageValue,
                semanticsMode = semanticsMode.storageValue,
                calculatedAt = calculatedAt.toString(),
                evidenceThrough = fit.support.lastEvidenceAt?.toString(),
                evidenceSetCount = fit.support.observationCount,
                evidenceObservationCount = fit.support.observationCount,
                effectiveIndependentSessionCount = fit.support.effectiveIndependentSessionCount,
            ),
        )
        dao.insertCapabilityStates(
            listOf(
                CapabilityStateEntity(
                    inferenceRunId = runId.value,
                    executionProfileVersionId = fit.executionProfileVersionId.value,
                    side = fit.side.storageValue,
                    capabilityFamily = CAPABILITY_FAMILY,
                    canonicalUnit = UnitId.KILOGRAM.storageValue,
                    posterior = fit.frontierAtLatestSession.toPosteriorColumns(),
                    modelConfigId = candidateConfig.id.value,
                    updatedAt = calculatedAt.toString(),
                ),
            ),
        )
        dao.insertCapabilityParameterStates(
            listOf(
                CapabilityParameterStateEntity(
                    inferenceRunId = runId.value,
                    executionProfileVersionId = fit.executionProfileVersionId.value,
                    side = fit.side.storageValue,
                    capabilityFamily = CAPABILITY_FAMILY,
                    parameterSchemaVersion = DynamicTrendCapabilityParameterCodec.SCHEMA_VERSION,
                    encodedParameters = DynamicTrendCapabilityParameterCodec.encode(fit),
                    modelConfigId = candidateConfig.id.value,
                ),
            ),
        )
        runId
    }

    suspend fun load(runId: InferenceRunId): DynamicTrendFrontierFit = database.withTransaction {
        val run = dao.inferenceRun(runId.value)
            ?: throw InferenceException("Missing inference run ${runId.value}.")
        require(run.executionMode in setOf(
            InferenceExecutionMode.SHADOW.storageValue,
            InferenceExecutionMode.CANDIDATE_V7.storageValue,
        )) { "Run ${run.id} is not candidate/shadow state." }
        require(run.modelVersion == SHADOW_RUN_MODEL_VERSION) {
            "Run ${run.id} is not a Candidate-v2 adaptive-inference shadow run."
        }
        val state = dao.capabilityStates(run.id).singleOrNull { it.capabilityFamily == CAPABILITY_FAMILY }
            ?: throw InferenceException("Run ${run.id} does not contain exactly one Candidate-v2 capability state.")
        val parameters = dao.capabilityParameterStates(run.id).singleOrNull {
            it.capabilityFamily == CAPABILITY_FAMILY &&
                it.executionProfileVersionId == state.executionProfileVersionId &&
                it.side == state.side
        } ?: throw InferenceException("Run ${run.id} is missing matching Candidate-v2 parameter state.")
        require(state.modelConfigId == parameters.modelConfigId)
        val expectedConfig = candidateConfig()
        require(state.modelConfigId == expectedConfig.id.value) {
            "Persisted Candidate-v2 state uses a different model/solver config; use its matching implementation."
        }
        val manifest = dao.inferenceModelManifest(run.modelManifestId)
            ?: throw InferenceException("Run ${run.id} references a missing model manifest.")
        require(dao.inferenceModelManifestEntries(manifest.id).any {
            it.component == InferenceModelComponent.DYNAMIC_CAPABILITY.storageValue &&
                it.modelConfigId == state.modelConfigId
        }) { "Run manifest does not bind the persisted Candidate-v2 config." }

        val provenance = ModelOutputProvenance(
            modelConfigId = ModelConfigId(state.modelConfigId),
            modelManifestId = ModelManifestId(run.modelManifestId),
            inferenceRunId = runId,
            evidenceThrough = run.evidenceThrough?.let(Instant::parse),
        )
        val frontier = state.posterior.toPosteriorEstimate(provenance)
        val fit = DynamicTrendCapabilityParameterCodec.decode(
            parameterSchemaVersion = parameters.parameterSchemaVersion,
            encodedParameters = parameters.encodedParameters,
            frontierAtLatestSession = frontier,
            executionProfileVersionId = ExecutionProfileVersionId(state.executionProfileVersionId),
            side = Laterality.fromStorage(state.side),
            modelConfigId = ModelConfigId(state.modelConfigId),
        )
        require(fit.mathematicalModelIdentity == solver.mathematicalModelIdentity)
        require(fit.solverDiagnostics.solverIdentity == solver.solverIdentity)
        fit
    }

    /** Prediction after reload at the next independent-session horizon, preserving joint c/g state. */
    suspend fun predictReloadedNextSession(runId: InferenceRunId, repetitions: Double): PosteriorEstimate {
        val projected = solver.projectToNextSession(load(runId))
        return solverBasePrediction(projected, repetitions)
    }

    /** Cascade removes derived Candidate-v2 state only; canonical workout/performance evidence is not owned by the run. */
    suspend fun discard(runId: InferenceRunId) {
        database.withTransaction { dao.deleteInferenceRun(runId.value) }
    }

    private fun solverBasePrediction(
        projected: dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit,
        repetitions: Double,
    ): PosteriorEstimate = dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel(solver.baseConfig)
        .predictFrontier(projected, repetitions)

    private fun candidateConfig(): ModelConfigDefinition = solver.modelConfig(CANDIDATE_CONFIG_CREATED_AT)

    private fun candidateManifest(
        referenceModelVersion: String,
        candidateConfig: ModelConfigDefinition,
    ): CandidateManifestBundle {
        val benchmark = BenchmarkV0ModelManifestFactory.create(
            referenceModelVersion = referenceModelVersion,
            recruitmentModelVersion = RoomInferenceRepository.RECRUITMENT_MODEL_VERSION,
            exposureModelVersion = WeightedWorkingSetStimulusEstimator.MODEL_VERSION,
            muscleStateModelVersion = NeutralPriorMuscleStateUpdater.MODEL_VERSION,
            translationModelVersion = ObservedPerformanceTranslationModel.MODEL_VERSION,
        )
        val configs = benchmark.configs.filterNot { it.component == InferenceModelComponent.DYNAMIC_CAPABILITY } + candidateConfig
        return CandidateManifestBundle(
            manifest = ModelManifest.create(configs.associate { it.component to it.id }),
            configs = configs,
        )
    }

    private suspend fun persistConfigsAndManifest(configs: List<ModelConfigDefinition>, manifest: ModelManifest) {
        configs.forEach { config ->
            val entity = ModelConfigDefinitionEntity(
                id = config.id.value,
                component = config.component.storageValue,
                modelFamily = config.modelFamily,
                modelName = config.modelName,
                semanticVersion = config.semanticVersion,
                configSchemaVersion = config.configSchemaVersion,
                canonicalConfigPayload = config.canonicalConfigPayload,
                createdAt = config.createdAt.toString(),
                effectiveAt = config.effectiveAt?.toString(),
            )
            val existing = dao.modelConfigDefinition(entity.id)
            when {
                existing == null -> dao.insertModelConfigDefinition(entity)
                existing != entity -> throw InferenceException("Immutable model config ${entity.id} differs from persisted definition.")
            }
        }
        val manifestEntity = InferenceModelManifestEntity(
            id = manifest.id.value,
            createdAt = configs.minOf { it.createdAt }.toString(),
        )
        val entries = manifest.entries.map { (component, configId) ->
            InferenceModelManifestEntryEntity(manifest.id.value, component.storageValue, configId.value)
        }.sortedBy { it.component }
        val existing = dao.inferenceModelManifest(manifest.id.value)
        if (existing == null) {
            dao.insertInferenceModelManifest(manifestEntity)
            dao.insertInferenceModelManifestEntries(entries)
        } else {
            require(existing == manifestEntity && dao.inferenceModelManifestEntries(manifest.id.value) == entries) {
                "Immutable model manifest ${manifest.id.value} differs from persisted definition."
            }
        }
    }

    private data class CandidateManifestBundle(
        val manifest: ModelManifest,
        val configs: List<ModelConfigDefinition>,
    )

    companion object {
        const val CAPABILITY_FAMILY = "dynamic_resistance"
        const val SHADOW_RUN_MODEL_VERSION = "n-bio-7bx-dynamic-trend-capability-shadow-v2"
        val CANDIDATE_CONFIG_CREATED_AT: Instant = Instant.parse("2026-08-31T00:00:00Z")
    }
}
