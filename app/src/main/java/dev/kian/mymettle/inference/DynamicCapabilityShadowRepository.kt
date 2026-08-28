package dev.kian.mymettle.inference

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.CapabilityParameterStateEntity
import dev.kian.mymettle.data.local.entity.CapabilityStateEntity
import dev.kian.mymettle.data.local.entity.InferenceModelManifestEntity
import dev.kian.mymettle.data.local.entity.InferenceModelManifestEntryEntity
import dev.kian.mymettle.data.local.entity.InferenceRunEntity
import dev.kian.mymettle.data.local.entity.ModelConfigDefinitionEntity
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierConfig
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
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
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.BenchmarkV0ModelManifestFactory
import dev.kian.mymettle.engine.inference.NeutralPriorMuscleStateUpdater
import dev.kian.mymettle.engine.inference.ObservedPerformanceTranslationModel
import dev.kian.mymettle.engine.inference.WeightedWorkingSetStimulusEstimator
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import java.time.Instant
import java.util.UUID

/**
 * Room14 persistence boundary for non-authoritative 7B dynamic-capability shadow state.
 * Normal workout reads remain unchanged because InferenceDao.latestInferenceRun() filters benchmark_v0.
 */
class DynamicCapabilityShadowRepository(
    private val database: MyMettleDatabase,
    private val model: DynamicStochasticFrontierModel = DynamicStochasticFrontierModel(),
    private val clock: () -> Instant = Instant::now,
    private val idFactory: () -> String = { "inference_shadow_7b_${UUID.randomUUID()}" },
) {
    private val dao get() = database.inferenceDao()

    suspend fun persist(
        userProfileId: String,
        fit: DynamicStochasticFrontierFit,
        semanticsMode: InferenceSemanticsMode = InferenceSemanticsMode.HISTORICAL_SEMANTICS,
    ): InferenceRunId = database.withTransaction {
        require(semanticsMode == InferenceSemanticsMode.HISTORICAL_SEMANTICS) {
            "7B.3/4 primary shadow persistence uses explicit historical semantics."
        }
        require(userProfileId in dao.userProfileIds()) { "User profile $userProfileId does not exist." }
        val reference = dao.latestReferenceProfile() ?: throw InferenceException("Runtime reference profile has not been seeded.")
        val candidateConfig = candidateConfig()
        require(fit.modelConfigId == candidateConfig.id) { "Fit/config identity mismatch; refusing shadow persistence." }
        val manifestBundle = candidateManifest(reference.modelVersion, candidateConfig)
        persistConfigsAndManifest(manifestBundle.configs, manifestBundle.manifest)

        val runId = InferenceRunId(idFactory())
        val calculatedAt = clock()
        val run = InferenceRunEntity(
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
        )
        dao.insertInferenceRun(run)
        dao.insertCapabilityStates(
            listOf(
                CapabilityStateEntity(
                    inferenceRunId = runId.value,
                    executionProfileVersionId = fit.executionProfileVersionId.value,
                    side = fit.side.storageValue,
                    capabilityFamily = CAPABILITY_FAMILY,
                    canonicalUnit = UnitId.KILOGRAM.storageValue,
                    posterior = fit.frontierAtReference.toPosteriorColumns(),
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
                    parameterSchemaVersion = DynamicCapabilityParameterCodec.SCHEMA_VERSION,
                    encodedParameters = DynamicCapabilityParameterCodec.encode(fit),
                    modelConfigId = candidateConfig.id.value,
                ),
            ),
        )
        runId
    }

    suspend fun load(runId: InferenceRunId): DynamicStochasticFrontierFit = database.withTransaction {
        val run = dao.inferenceRun(runId.value) ?: throw InferenceException("Missing inference run ${runId.value}.")
        require(run.executionMode in setOf(InferenceExecutionMode.SHADOW.storageValue, InferenceExecutionMode.CANDIDATE_V7.storageValue)) {
            "Run ${run.id} is not candidate/shadow capability state."
        }
        val state = dao.capabilityStates(run.id).singleOrNull { it.capabilityFamily == CAPABILITY_FAMILY }
            ?: throw InferenceException("Run ${run.id} does not contain exactly one dynamic-resistance capability state.")
        val parameters = dao.capabilityParameterStates(run.id).singleOrNull {
            it.capabilityFamily == CAPABILITY_FAMILY &&
                it.executionProfileVersionId == state.executionProfileVersionId &&
                it.side == state.side
        } ?: throw InferenceException("Run ${run.id} is missing matching dynamic capability parameter state.")
        require(state.modelConfigId == parameters.modelConfigId) { "Capability and parameter config ids disagree." }
        val expectedConfig = candidateConfig()
        require(state.modelConfigId == expectedConfig.id.value) {
            "Persisted capability uses an unsupported model config; recomputation with its implementation is required."
        }
        val manifest = dao.inferenceModelManifest(run.modelManifestId)
            ?: throw InferenceException("Run ${run.id} references a missing model manifest.")
        val manifestEntries = dao.inferenceModelManifestEntries(manifest.id)
        require(manifestEntries.any {
            it.component == InferenceModelComponent.DYNAMIC_CAPABILITY.storageValue && it.modelConfigId == state.modelConfigId
        }) { "Run manifest does not bind the persisted dynamic-capability config." }
        val provenance = ModelOutputProvenance(
            modelConfigId = ModelConfigId(state.modelConfigId),
            modelManifestId = ModelManifestId(run.modelManifestId),
            inferenceRunId = runId,
            evidenceThrough = run.evidenceThrough?.let(Instant::parse),
        )
        val frontier = state.posterior.toPosteriorEstimate(provenance)
        DynamicCapabilityParameterCodec.decode(
            parameterSchemaVersion = parameters.parameterSchemaVersion,
            encodedParameters = parameters.encodedParameters,
            frontierAtReference = frontier,
            executionProfileVersionId = dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId(state.executionProfileVersionId),
            side = Laterality.fromStorage(state.side),
            modelConfigId = ModelConfigId(state.modelConfigId),
        )
    }

    suspend fun predictReloaded(runId: InferenceRunId, repetitions: Double): PosteriorEstimate =
        model.predictFrontier(load(runId), repetitions)

    /** Cascade deletes candidate derived state only; canonical workout/performance evidence has no FK to inference_run. */
    suspend fun discard(runId: InferenceRunId) {
        database.withTransaction { dao.deleteInferenceRun(runId.value) }
    }

    private fun candidateConfig(): ModelConfigDefinition = model.config.toModelConfig(CANDIDATE_CONFIG_CREATED_AT)

    private fun candidateManifest(referenceModelVersion: String, candidateConfig: ModelConfigDefinition): CandidateManifestBundle {
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
        const val SHADOW_RUN_MODEL_VERSION = "n-bio-7b34-dynamic-capability-shadow-v1"
        val CANDIDATE_CONFIG_CREATED_AT: Instant = Instant.parse("2026-08-27T00:00:00Z")
    }
}
