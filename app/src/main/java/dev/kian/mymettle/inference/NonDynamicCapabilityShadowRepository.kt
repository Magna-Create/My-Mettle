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
import dev.kian.mymettle.domain.inference.InferenceExecutionMode
import dev.kian.mymettle.domain.inference.InferenceRunId
import dev.kian.mymettle.domain.inference.InferenceSemanticsMode
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.domain.inference.ModelConfigId
import dev.kian.mymettle.domain.inference.ModelManifest
import dev.kian.mymettle.domain.inference.ModelManifestId
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFit
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityQuery
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.inference.BenchmarkV0ModelManifestFactory
import dev.kian.mymettle.engine.inference.NeutralPriorMuscleStateUpdater
import dev.kian.mymettle.engine.inference.ObservedPerformanceTranslationModel
import dev.kian.mymettle.engine.inference.WeightedWorkingSetStimulusEstimator
import dev.kian.mymettle.engine.performance.NonDynamicCapabilitySolver
import java.time.Instant
import java.util.UUID

/** Disposable SHADOW persistence boundary for all N-BIO-7C capability families using existing Room14 tables. */
class NonDynamicCapabilityShadowRepository(
    private val database: MyMettleDatabase,
    private val solver: NonDynamicCapabilitySolver,
    private val clock: () -> Instant = Instant::now,
    private val idFactory: () -> String = { "inference_shadow_7c_${solver.familyConfig.family.storageValue}_${UUID.randomUUID()}" },
) {
    private val dao get() = database.inferenceDao()

    suspend fun persist(
        userProfileId: String,
        fit: NonDynamicCapabilityFit,
        semanticsMode: InferenceSemanticsMode = InferenceSemanticsMode.HISTORICAL_SEMANTICS,
    ): InferenceRunId = database.withTransaction {
        require(semanticsMode == InferenceSemanticsMode.HISTORICAL_SEMANTICS)
        require(userProfileId in dao.userProfileIds())
        require(fit.family == solver.familyConfig.family)
        require(fit.mathematicalModelIdentity == solver.familyConfig.mathematicalModelIdentity) {
            "7C fit mathematical identity differs from selected solver family config."
        }
        require(fit.solverDiagnostics.solverIdentity == solver.solverConfig.solverIdentity) {
            "7C fit solver identity differs from selected solver."
        }
        val config = candidateConfig()
        require(fit.modelConfigId == config.id) { "7C fit/config mismatch; refusing persistence." }
        val reference = dao.latestReferenceProfile() ?: throw InferenceException("Runtime reference profile has not been seeded.")
        val manifestBundle = candidateManifest(reference.modelVersion, config)
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
                    capabilityFamily = fit.family.storageValue,
                    canonicalUnit = fit.canonicalUnit.storageValue,
                    posterior = fit.frontierAtReference.toPosteriorColumns(),
                    modelConfigId = config.id.value,
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
                    capabilityFamily = fit.family.storageValue,
                    parameterSchemaVersion = NonDynamicCapabilityParameterCodec.SCHEMA_VERSION,
                    encodedParameters = NonDynamicCapabilityParameterCodec.encode(fit),
                    modelConfigId = config.id.value,
                ),
            ),
        )
        runId
    }

    suspend fun load(runId: InferenceRunId): NonDynamicCapabilityFit = database.withTransaction {
        val run = dao.inferenceRun(runId.value) ?: throw InferenceException("Missing 7C inference run ${runId.value}.")
        require(run.executionMode == InferenceExecutionMode.SHADOW.storageValue)
        require(run.modelVersion == SHADOW_RUN_MODEL_VERSION)
        val family = solver.familyConfig.family.storageValue
        val state = dao.capabilityStates(run.id).singleOrNull { it.capabilityFamily == family }
            ?: throw InferenceException("Run ${run.id} does not contain exactly one $family capability state.")
        val parameters = dao.capabilityParameterStates(run.id).singleOrNull {
            it.capabilityFamily == family && it.executionProfileVersionId == state.executionProfileVersionId && it.side == state.side
        } ?: throw InferenceException("Run ${run.id} is missing matching $family joint parameter state.")
        require(state.modelConfigId == parameters.modelConfigId)
        val expectedConfig = candidateConfig()
        require(state.modelConfigId == expectedConfig.id.value) { "Persisted 7C state uses another mathematical config." }
        val manifest = dao.inferenceModelManifest(run.modelManifestId) ?: throw InferenceException("Missing 7C model manifest.")
        require(dao.inferenceModelManifestEntries(manifest.id).any {
            it.component == solver.familyConfig.component.storageValue && it.modelConfigId == state.modelConfigId
        }) { "7C manifest does not bind the family capability config." }
        val provenance = ModelOutputProvenance(
            modelConfigId = ModelConfigId(state.modelConfigId),
            modelManifestId = ModelManifestId(run.modelManifestId),
            inferenceRunId = runId,
            evidenceThrough = run.evidenceThrough?.let(Instant::parse),
        )
        val fit = NonDynamicCapabilityParameterCodec.decode(
            parameterSchemaVersion = parameters.parameterSchemaVersion,
            encodedParameters = parameters.encodedParameters,
            frontierAtReference = state.posterior.toPosteriorEstimate(provenance),
            executionProfileVersionId = ExecutionProfileVersionId(state.executionProfileVersionId),
            side = Laterality.fromStorage(state.side),
            modelConfigId = ModelConfigId(state.modelConfigId),
        )
        require(fit.mathematicalModelIdentity == solver.familyConfig.mathematicalModelIdentity)
        require(fit.solverDiagnostics.solverIdentity == solver.solverConfig.solverIdentity)
        fit
    }

    suspend fun predictReloaded(runId: InferenceRunId, query: NonDynamicCapabilityQuery): PosteriorEstimate =
        solver.predict(load(runId), query)

    suspend fun discard(runId: InferenceRunId) {
        database.withTransaction { dao.deleteInferenceRun(runId.value) }
    }

    private fun candidateConfig(): ModelConfigDefinition = solver.familyConfig.toModelConfig(CANDIDATE_CONFIG_CREATED_AT)

    private fun candidateManifest(referenceModelVersion: String, candidateConfig: ModelConfigDefinition): ManifestBundle {
        val benchmark = BenchmarkV0ModelManifestFactory.create(
            referenceModelVersion = referenceModelVersion,
            recruitmentModelVersion = RoomInferenceRepository.RECRUITMENT_MODEL_VERSION,
            exposureModelVersion = WeightedWorkingSetStimulusEstimator.MODEL_VERSION,
            muscleStateModelVersion = NeutralPriorMuscleStateUpdater.MODEL_VERSION,
            translationModelVersion = ObservedPerformanceTranslationModel.MODEL_VERSION,
        )
        val configs = benchmark.configs.filterNot { it.component == candidateConfig.component } + candidateConfig
        return ManifestBundle(ModelManifest.create(configs.associate { it.component to it.id }), configs)
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
        val manifestEntity = InferenceModelManifestEntity(manifest.id.value, configs.minOf { it.createdAt }.toString())
        val entries = manifest.entries.map { (component, configId) ->
            InferenceModelManifestEntryEntity(manifest.id.value, component.storageValue, configId.value)
        }.sortedBy { it.component }
        val existing = dao.inferenceModelManifest(manifest.id.value)
        if (existing == null) {
            dao.insertInferenceModelManifest(manifestEntity)
            dao.insertInferenceModelManifestEntries(entries)
        } else {
            require(existing == manifestEntity && dao.inferenceModelManifestEntries(manifest.id.value) == entries)
        }
    }

    private data class ManifestBundle(val manifest: ModelManifest, val configs: List<ModelConfigDefinition>)

    companion object {
        const val SHADOW_RUN_MODEL_VERSION = "n-bio-7c-non-dynamic-capability-shadow-v1"
        val CANDIDATE_CONFIG_CREATED_AT: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
