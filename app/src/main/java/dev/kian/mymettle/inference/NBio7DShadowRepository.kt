package dev.kian.mymettle.inference

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.CapabilityParameterStateEntity
import dev.kian.mymettle.data.local.entity.CapabilityStateEntity
import dev.kian.mymettle.data.local.entity.InferenceModelManifestEntity
import dev.kian.mymettle.data.local.entity.InferenceModelManifestEntryEntity
import dev.kian.mymettle.data.local.entity.InferenceRunEntity
import dev.kian.mymettle.data.local.entity.ModelConfigDefinitionEntity
import dev.kian.mymettle.data.local.entity.MuscleSessionDoseEntity
import dev.kian.mymettle.data.local.entity.MuscleSetDoseEntity
import dev.kian.mymettle.data.local.entity.PosteriorColumns
import dev.kian.mymettle.data.local.entity.SetDemandEstimateEntity
import dev.kian.mymettle.domain.inference.EvidenceSupport
import dev.kian.mymettle.domain.inference.InferenceExecutionMode
import dev.kian.mymettle.domain.inference.InferenceModelComponent
import dev.kian.mymettle.domain.inference.InferenceRunId
import dev.kian.mymettle.domain.inference.InferenceSemanticsMode
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.domain.inference.ModelManifest
import dev.kian.mymettle.domain.inference.NBio7DConfig
import dev.kian.mymettle.domain.inference.NBio7DModelConfigs
import dev.kian.mymettle.domain.inference.NBio7DSessionResult
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import java.time.Instant
import java.util.UUID

/**
 * Room14 persistence boundary for N-BIO-7D SHADOW state.
 *
 * The target session is evaluated against causal pre-session capability snapshots. Those snapshots
 * are copied into the same derived inference run, including their complete encoded parameter state,
 * so SetDemand/EffectiveDose can be replayed without refitting capability or reading future sets.
 * Canonical workout observations and historical recruitment versions remain the source evidence and
 * are never copied or modified here.
 *
 * Room14 stores compact posterior summaries. Full SetDemand/EffectiveDose joint nodes are rebuilt
 * deterministically from the persisted capability parameter snapshot + canonical raw set evidence +
 * immutable 7D model config. The raw muscle-session posterior is canonical persisted session dose;
 * the concave posterior is deterministically rebuilt from the raw joint substrate and immutable tau.
 */
class NBio7DShadowRepository(
    private val database: MyMettleDatabase,
    private val clock: () -> Instant = Instant::now,
    private val idFactory: () -> String = { "inference_shadow_7d_${UUID.randomUUID()}" },
) {
    private val dao get() = database.inferenceDao()

    data class CapabilitySnapshot(
        val streamKey: String,
        val executionProfileVersionId: String,
        val side: String,
        val capabilityFamily: String,
        val canonicalUnit: String?,
        val posterior: PosteriorEstimate,
        val parameterSchemaVersion: Int,
        val encodedParameters: String,
        val modelConfig: ModelConfigDefinition,
    ) {
        init {
            require(streamKey.isNotBlank())
            require(executionProfileVersionId.isNotBlank())
            require(side.isNotBlank())
            require(capabilityFamily.isNotBlank())
            require(parameterSchemaVersion > 0)
            require(encodedParameters.isNotBlank())
            require(modelConfig.component in CAPABILITY_COMPONENTS)
            require(posterior.provenance.modelConfigId == modelConfig.id)
        }
    }

    data class SetPersistenceInput(
        val sessionId: String,
        val setObservationId: String,
        val executionProfileVersionId: String,
        val side: String,
        val completedAt: Instant,
    ) {
        init {
            require(sessionId.isNotBlank())
            require(setObservationId.isNotBlank())
            require(executionProfileVersionId.isNotBlank())
            require(side.isNotBlank())
        }
    }

    data class PersistedRun(
        val run: InferenceRunEntity,
        val capabilityStates: List<CapabilityStateEntity>,
        val capabilityParameterStates: List<CapabilityParameterStateEntity>,
        val setDemandEstimates: List<SetDemandEstimateEntity>,
        val muscleSetDoses: List<MuscleSetDoseEntity>,
        val muscleSessionDoses: List<MuscleSessionDoseEntity>,
    )

    suspend fun persist(
        userProfileId: String,
        sessionResult: NBio7DSessionResult,
        setInputs: List<SetPersistenceInput>,
        capabilitySnapshots: List<CapabilitySnapshot>,
        config: NBio7DConfig = NBio7DConfig(),
        semanticsMode: InferenceSemanticsMode = InferenceSemanticsMode.HISTORICAL_SEMANTICS,
    ): InferenceRunId = database.withTransaction {
        require(semanticsMode == InferenceSemanticsMode.HISTORICAL_SEMANTICS) {
            "N-BIO-7D causal persistence requires historical semantics."
        }
        require(userProfileId in dao.userProfileIds()) { "User profile $userProfileId does not exist." }
        require(setInputs.map { it.setObservationId }.distinct().size == setInputs.size)
        require(sessionResult.setResults.map { it.setObservationId }.toSet() == setInputs.map { it.setObservationId }.toSet()) {
            "7D persistence metadata must exactly cover evaluated set observations."
        }
        require(capabilitySnapshots.map { it.streamKey }.distinct().size == capabilitySnapshots.size)

        val metadataBySet = setInputs.associateBy { it.setObservationId }
        val capabilityByStream = capabilitySnapshots.associateBy { it.streamKey }
        sessionResult.setResults.forEach { result ->
            if (result.demand.frontierGapSummary != null) {
                require(capabilityByStream[result.capabilityStreamKey] != null) {
                    "Resolved 7D demand requires its persisted pre-session capability stream ${result.capabilityStreamKey}."
                }
            }
        }

        val reference = dao.latestReferenceProfile()
            ?: throw InferenceException("Runtime reference profile has not been seeded.")
        val sevenDConfigs = NBio7DModelConfigs.definitions(config)
        val allConfigs = canonicalConfigs(capabilitySnapshots.map { it.modelConfig } + sevenDConfigs)
        val manifest = ModelManifest.create(allConfigs.associate { it.component to it.id })
        persistConfigsAndManifest(allConfigs, manifest)

        val runId = InferenceRunId(idFactory())
        val calculatedAt = clock()
        val evidenceThrough = setInputs.maxOfOrNull { it.completedAt }
            ?: capabilitySnapshots.mapNotNull { it.posterior.support.lastEvidenceAt }.maxOrNull()
        dao.insertInferenceRun(
            InferenceRunEntity(
                id = runId.value,
                userProfileId = userProfileId,
                modelVersion = SHADOW_RUN_MODEL_VERSION,
                referenceProfileId = reference.id,
                referenceProfileVersion = reference.version,
                referenceModelVersion = reference.modelVersion,
                recruitmentModelVersion = RoomInferenceRepository.RECRUITMENT_MODEL_VERSION,
                stimulusModelVersion = NBio7DModelConfigs.definitions(config).first { it.component == InferenceModelComponent.EFFECTIVE_DOSE }.semanticVersion,
                muscleStateModelVersion = NO_7E_STATE_MODEL_VERSION,
                exerciseTranslationModelVersion = NO_TRANSLATION_MODEL_VERSION,
                modelManifestId = manifest.id.value,
                executionMode = InferenceExecutionMode.SHADOW.storageValue,
                semanticsMode = semanticsMode.storageValue,
                calculatedAt = calculatedAt.toString(),
                evidenceThrough = evidenceThrough?.toString(),
                evidenceSetCount = setInputs.size,
                evidenceObservationCount = setInputs.size,
                effectiveIndependentSessionCount = if (setInputs.isEmpty()) 0 else 1,
            ),
        )

        val capabilityStates = capabilitySnapshots.map { snapshot ->
            CapabilityStateEntity(
                inferenceRunId = runId.value,
                executionProfileVersionId = snapshot.executionProfileVersionId,
                side = snapshot.side,
                capabilityFamily = snapshot.capabilityFamily,
                canonicalUnit = snapshot.canonicalUnit,
                posterior = snapshot.posterior.toPosteriorColumns(),
                modelConfigId = snapshot.modelConfig.id.value,
                updatedAt = calculatedAt.toString(),
            )
        }
        val parameterStates = capabilitySnapshots.map { snapshot ->
            CapabilityParameterStateEntity(
                inferenceRunId = runId.value,
                executionProfileVersionId = snapshot.executionProfileVersionId,
                side = snapshot.side,
                capabilityFamily = snapshot.capabilityFamily,
                parameterSchemaVersion = snapshot.parameterSchemaVersion,
                encodedParameters = snapshot.encodedParameters,
                modelConfigId = snapshot.modelConfig.id.value,
            )
        }
        if (capabilityStates.isNotEmpty()) dao.insertCapabilityStates(capabilityStates)
        if (parameterStates.isNotEmpty()) dao.insertCapabilityParameterStates(parameterStates)

        val demandConfig = sevenDConfigs.single { it.component == InferenceModelComponent.SET_DEMAND }
        val exposureConfig = sevenDConfigs.single { it.component == InferenceModelComponent.EXPOSURE }
        val effectiveDoseConfig = sevenDConfigs.single { it.component == InferenceModelComponent.EFFECTIVE_DOSE }
        val sessionDoseConfig = sevenDConfigs.single { it.component == InferenceModelComponent.SESSION_DOSE }

        val demandRows = sessionResult.setResults.map { result ->
            val metadata = requireNotNull(metadataBySet[result.setObservationId])
            val support = capabilityByStream[result.capabilityStreamKey]?.posterior?.support ?: EMPTY_SUPPORT
            SetDemandEstimateEntity(
                inferenceRunId = runId.value,
                setObservationId = result.setObservationId,
                executionProfileVersionId = metadata.executionProfileVersionId,
                side = metadata.side,
                posterior = result.demand.frontierGapSummary.toColumns(
                    support = support,
                    evidenceFamily = "n_bio_7d_set_demand:${result.demand.family.storageValue}:${result.demand.structuralSupport.name}",
                ),
                modelConfigId = demandConfig.id.value,
            )
        }
        if (demandRows.isNotEmpty()) dao.insertSetDemandEstimates(demandRows)

        val muscleSetRows = sessionResult.setResults.flatMap { result ->
            val metadata = requireNotNull(metadataBySet[result.setObservationId])
            val support = capabilityByStream[result.capabilityStreamKey]?.posterior?.support ?: EMPTY_SUPPORT
            result.muscleDoses.map { dose ->
                MuscleSetDoseEntity(
                    inferenceRunId = runId.value,
                    setObservationId = result.setObservationId,
                    executionProfileVersionId = metadata.executionProfileVersionId,
                    recruitmentProfileVersionId = dose.exposure.historicalRecruitmentProfileVersionId,
                    muscleSegmentId = dose.exposure.muscleSegmentId,
                    side = dose.exposure.side,
                    recruitmentWeight = dose.exposure.recruitmentWeight,
                    conservativeExposure = dose.exposure.conservativeExposure,
                    effectiveDose = dose.summary?.toColumns(
                        support = support,
                        evidenceFamily = "n_bio_7d_effective_dose:${dose.structuralSupport.name}",
                    ),
                    exposureModelConfigId = exposureConfig.id.value,
                    effectiveDoseModelConfigId = if (dose.summary == null) null else effectiveDoseConfig.id.value,
                )
            }
        }
        if (muscleSetRows.isNotEmpty()) dao.insertMuscleSetDoses(muscleSetRows)

        val sessionIds = setInputs.map { it.sessionId }.distinct()
        require(sessionIds.size <= 1) { "A 7D shadow run is session-scoped." }
        val sessionId = sessionIds.singleOrNull()
        val completedAt = setInputs.maxOfOrNull { it.completedAt }
        val sessionRows = if (sessionId == null) emptyList() else sessionResult.muscleResults.map { result ->
            MuscleSessionDoseEntity(
                inferenceRunId = runId.value,
                sessionId = sessionId,
                muscleSegmentId = result.key.muscleSegmentId,
                side = result.key.side,
                posterior = result.dose.rawSummary.toColumns(
                    support = EvidenceSupport(
                        observationCount = result.dose.contributingSetCount,
                        effectiveIndependentSessionCount = if (result.dose.contributingSetCount == 0) 0 else 1,
                        firstEvidenceAt = completedAt,
                        lastEvidenceAt = completedAt,
                        evidenceFamily = dev.kian.mymettle.domain.inference.EvidenceFamily(
                            "n_bio_7d_session_dose:${result.dose.resolution.name}:unresolved=${result.dose.unresolvedSetCount}:cross_stream_independence=${result.dose.crossStreamIndependenceApproximation}",
                        ),
                    ),
                    evidenceFamily = "n_bio_7d_session_dose:${result.dose.resolution.name}:unresolved=${result.dose.unresolvedSetCount}:cross_stream_independence=${result.dose.crossStreamIndependenceApproximation}",
                ),
                sessionDoseModelConfigId = sessionDoseConfig.id.value,
            )
        }
        if (sessionRows.isNotEmpty()) dao.insertMuscleSessionDoses(sessionRows)
        runId
    }

    suspend fun load(runId: InferenceRunId): PersistedRun = database.withTransaction {
        val run = dao.inferenceRun(runId.value) ?: throw InferenceException("Missing N-BIO-7D inference run ${runId.value}.")
        require(run.modelVersion == SHADOW_RUN_MODEL_VERSION) { "Run ${run.id} is not an N-BIO-7D shadow run." }
        require(run.executionMode == InferenceExecutionMode.SHADOW.storageValue)
        require(run.semanticsMode == InferenceSemanticsMode.HISTORICAL_SEMANTICS.storageValue)
        PersistedRun(
            run = run,
            capabilityStates = dao.capabilityStates(run.id),
            capabilityParameterStates = dao.capabilityParameterStates(run.id),
            setDemandEstimates = dao.setDemandEstimates(run.id),
            muscleSetDoses = dao.muscleSetDoses(run.id),
            muscleSessionDoses = dao.muscleSessionDoses(run.id),
        )
    }

    /** Cascade deletes only derived 7D run state. Canonical observations/recruitment history remain. */
    suspend fun discard(runId: InferenceRunId) {
        database.withTransaction { dao.deleteInferenceRun(runId.value) }
    }

    private fun canonicalConfigs(configs: List<ModelConfigDefinition>): List<ModelConfigDefinition> {
        val byComponent = configs.groupBy { it.component }
        byComponent.forEach { (component, definitions) ->
            require(definitions.map { it.id }.distinct().size == 1) {
                "A single 7D run cannot bind multiple immutable configs for component ${component.storageValue}."
            }
        }
        return byComponent.values.map { it.first() }.sortedBy { it.component.storageValue }
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
            require(existing == manifestEntity && dao.inferenceModelManifestEntries(manifest.id.value) == entries) {
                "Immutable model manifest ${manifest.id.value} differs from persisted definition."
            }
        }
    }

    private fun PosteriorSummary?.toColumns(
        support: EvidenceSupport,
        evidenceFamily: String,
    ): PosteriorColumns = PosteriorColumns(
        p05 = this?.credibleLower05,
        p50 = this?.estimateMedian,
        p95 = this?.credibleUpper95,
        variance = this?.posteriorVariance,
        observationCount = support.observationCount,
        independentSessionCount = support.effectiveIndependentSessionCount,
        firstEvidenceAt = support.firstEvidenceAt?.toString(),
        lastEvidenceAt = support.lastEvidenceAt?.toString(),
        evidenceFamily = evidenceFamily,
    )

    companion object {
        const val SHADOW_RUN_MODEL_VERSION = "n-bio-7d-demand-dose-shadow-v1"
        const val NO_7E_STATE_MODEL_VERSION = "n-bio-7d-no-adaptive-muscle-state"
        const val NO_TRANSLATION_MODEL_VERSION = "n-bio-7d-no-translation"
        private val CAPABILITY_COMPONENTS = setOf(
            InferenceModelComponent.DYNAMIC_CAPABILITY,
            InferenceModelComponent.HOLD_CAPABILITY,
            InferenceModelComponent.DURATION_CAPABILITY,
            InferenceModelComponent.REPEATED_CONTRACTION_CAPABILITY,
        )
        private val EMPTY_SUPPORT = EvidenceSupport(
            observationCount = 0,
            effectiveIndependentSessionCount = 0,
            firstEvidenceAt = null,
            lastEvidenceAt = null,
            evidenceFamily = dev.kian.mymettle.domain.inference.EvidenceFamily("n_bio_7d_no_pre_session_capability"),
        )
    }
}
