package dev.kian.mymettle.inference

import dev.kian.mymettle.data.local.dao.NBio7EDao
import dev.kian.mymettle.data.local.entity.NBio7EContextModuleStateEntity
import dev.kian.mymettle.data.local.entity.NBio7EContextModuleStatusEntity
import dev.kian.mymettle.data.local.entity.NBio7EContextSignalEntity
import dev.kian.mymettle.data.local.entity.NBio7ERunEntity
import dev.kian.mymettle.data.local.entity.NBio7ETemporalStateEntity
import dev.kian.mymettle.domain.context.CONTEXT_MODULE_PROTOCOL_VERSION
import dev.kian.mymettle.domain.context.CONTEXT_SIGNAL_SCHEMA_VERSION
import dev.kian.mymettle.domain.context.ContextEvidenceMaturity
import dev.kian.mymettle.domain.context.ContextFeatureKey
import dev.kian.mymettle.domain.context.ContextModuleFailureV7E
import dev.kian.mymettle.domain.context.ContextModulePhase
import dev.kian.mymettle.domain.context.ContextModuleRegistryV7E
import dev.kian.mymettle.domain.context.ContextModuleStateV7E
import dev.kian.mymettle.domain.context.ContextScope
import dev.kian.mymettle.domain.context.ContextScopeKind
import dev.kian.mymettle.domain.context.ContextSignalEffectRepresentation
import dev.kian.mymettle.domain.context.ContextSignalStatus
import dev.kian.mymettle.domain.context.ContextSignalTarget
import dev.kian.mymettle.domain.context.ContextSignalV1
import dev.kian.mymettle.domain.context.ContextSignalValidatorV1
import dev.kian.mymettle.domain.inference.TemporalCandidateLayer
import dev.kian.mymettle.domain.inference.TemporalCovariance3
import dev.kian.mymettle.domain.inference.TemporalStatePosteriorV1
import java.time.Instant

data class NBio7ETemporalStateRecordV1(
    val layer: TemporalCandidateLayer,
    val scope: ContextScope,
    val posterior: TemporalStatePosteriorV1,
)

data class NBio7EShadowRunV1(
    val id: String,
    val userProfileId: String,
    val sourceInferenceRunId: String,
    val temporalModelConfigId: String,
    val solverIdentity: String = "deterministic-robust-gaussian-filter-v1",
    val executionMode: String = "shadow_candidate",
    val pd001Status: String = "OPEN",
    val pd002Status: String = "OPEN",
    val pd003Status: String = "OPEN",
    val calculatedAt: Instant,
    val temporalStates: List<NBio7ETemporalStateRecordV1>,
    val moduleStates: Map<String, ContextModuleStateV7E>,
    val moduleEvidenceThrough: Map<String, Instant?>,
    val signals: List<ContextSignalV1>,
    val failures: List<ContextModuleFailureV7E>,
) {
    init {
        require(id.isNotBlank() && userProfileId.isNotBlank() && sourceInferenceRunId.isNotBlank())
        require(temporalModelConfigId.isNotBlank() && solverIdentity.isNotBlank())
        require(executionMode == "shadow_candidate") { "7E has no product-authority execution mode." }
        require(pd001Status == "OPEN" && pd002Status == "OPEN" && pd003Status == "OPEN")
        require(moduleStates.keys == moduleEvidenceThrough.keys)
    }
}

/**
 * Atomic Room boundary for replayable 7E state. Compatibility is checked before any decoded
 * module state or signal can re-enter the inference host.
 */
class NBio7EShadowRepository(
    private val dao: NBio7EDao,
    private val registry: ContextModuleRegistryV7E,
) {
    suspend fun save(run: NBio7EShadowRunV1) {
        validateAgainstRegistry(run)
        dao.insertCompleteRun(
            run = run.toEntity(),
            temporalStates = run.temporalStates.map { it.toEntity(run.id) },
            moduleStates = run.moduleStates.map { (moduleId, state) ->
                val module = requireNotNull(registry.module(moduleId))
                NBio7EContextModuleStateEntity(
                    runId = run.id,
                    moduleId = moduleId,
                    moduleModelVersion = module.descriptor.modelVersion,
                    moduleConfigId = module.descriptor.configId,
                    stateSchemaVersion = module.stateCodec.schemaVersion,
                    encodedState = module.stateCodec.encode(state),
                    evidenceThrough = run.moduleEvidenceThrough.getValue(moduleId)?.toString(),
                    updatedAt = run.calculatedAt.toString(),
                )
            },
            signals = run.signals.map { it.toEntity(run.id) },
            statuses = statuses(run),
        )
    }

    suspend fun load(runId: String): NBio7EShadowRunV1? {
        val run = dao.run(runId) ?: return null
        require(run.contextProtocolVersion == CONTEXT_MODULE_PROTOCOL_VERSION) { "Unsupported persisted module protocol." }
        require(run.signalSchemaVersion == CONTEXT_SIGNAL_SCHEMA_VERSION) { "Unsupported persisted signal schema." }
        val moduleRows = dao.moduleStates(runId)
        val states = moduleRows.associate { row ->
            val module = requireNotNull(registry.module(row.moduleId)) { "Persisted module is absent: ${row.moduleId}" }
            require(row.moduleModelVersion == module.descriptor.modelVersion) { "Stale model version: ${row.moduleId}" }
            require(row.moduleConfigId == module.descriptor.configId) { "Stale module config: ${row.moduleId}" }
            require(row.stateSchemaVersion == module.stateCodec.schemaVersion) { "Unknown state codec: ${row.moduleId}" }
            row.moduleId to module.stateCodec.decode(row.encodedState)
        }
        val signals = dao.signals(runId).map { row ->
            val signal = row.toDomain()
            val module = requireNotNull(registry.module(signal.sourceModuleId))
            ContextSignalValidatorV1.validate(signal, module.descriptor, signal.publishedAt)
            signal
        }
        return NBio7EShadowRunV1(
            id = run.id,
            userProfileId = run.userProfileId,
            sourceInferenceRunId = run.sourceInferenceRunId,
            temporalModelConfigId = run.temporalModelConfigId,
            solverIdentity = run.solverIdentity,
            executionMode = run.executionMode,
            pd001Status = run.pd001Status,
            pd002Status = run.pd002Status,
            pd003Status = run.pd003Status,
            calculatedAt = Instant.parse(run.calculatedAt),
            temporalStates = dao.temporalStates(runId).map { it.toDomain() },
            moduleStates = states,
            moduleEvidenceThrough = moduleRows.associate { it.moduleId to it.evidenceThrough?.let(Instant::parse) },
            signals = signals,
            failures = dao.moduleStatuses(runId).mapNotNull { it.toFailureOrNull() },
        )
    }

    suspend fun deleteDerivedRun(runId: String) = dao.deleteRun(runId)
    suspend fun deleteDerivedForUser(userProfileId: String) = dao.deleteDerivedForUser(userProfileId)
    suspend fun deleteModuleMemory(runId: String) = dao.deleteModuleStates(runId)
    suspend fun deleteSignals(runId: String) = dao.deleteSignals(runId)

    private fun validateAgainstRegistry(run: NBio7EShadowRunV1) {
        require(run.moduleStates.keys.all { registry.module(it) != null })
        require(run.signals.map { it.signalId }.distinct().size == run.signals.size)
        run.signals.forEach { signal ->
            val module = requireNotNull(registry.module(signal.sourceModuleId))
            ContextSignalValidatorV1.validate(signal, module.descriptor, signal.publishedAt)
        }
    }

    private fun statuses(run: NBio7EShadowRunV1): List<NBio7EContextModuleStatusEntity> {
        val failures = run.failures.associateBy { it.moduleId to it.phase }
        return registry.modules.flatMap { module ->
            ContextModulePhase.entries.map { phase ->
                val failure = failures[module.descriptor.moduleId to phase]
                NBio7EContextModuleStatusEntity(
                    runId = run.id,
                    moduleId = module.descriptor.moduleId,
                    phase = phase.name,
                    status = if (failure == null) "OK" else "FAILED_CLOSED",
                    failureCode = failure?.code,
                    failureSummary = failure?.message?.take(MAX_FAILURE_SUMMARY_LENGTH),
                    recordedAt = run.calculatedAt.toString(),
                )
            }
        }
    }

    companion object { private const val MAX_FAILURE_SUMMARY_LENGTH = 240 }
}

private fun NBio7EShadowRunV1.toEntity() = NBio7ERunEntity(
    id = id,
    userProfileId = userProfileId,
    sourceInferenceRunId = sourceInferenceRunId,
    temporalModelConfigId = temporalModelConfigId,
    contextProtocolVersion = CONTEXT_MODULE_PROTOCOL_VERSION,
    signalSchemaVersion = CONTEXT_SIGNAL_SCHEMA_VERSION,
    solverIdentity = solverIdentity,
    executionMode = executionMode,
    pd001Status = pd001Status,
    pd002Status = pd002Status,
    pd003Status = pd003Status,
    calculatedAt = calculatedAt.toString(),
)

private fun NBio7ETemporalStateRecordV1.toEntity(runId: String) = NBio7ETemporalStateEntity(
    runId = runId,
    candidateLayer = layer.storageValue,
    scopeKind = scope.kind.name,
    scopeId = scope.id.orEmpty(),
    stateSchemaVersion = 1,
    persistentMean = posterior.persistentMean,
    transientMean = posterior.transientMean,
    doseCoefficientMean = posterior.doseCoefficientMean,
    covariancePp = posterior.covariance.pp,
    covariancePt = posterior.covariance.pt,
    covariancePd = posterior.covariance.pd,
    covarianceTt = posterior.covariance.tt,
    covarianceTd = posterior.covariance.td,
    covarianceDd = posterior.covariance.dd,
    horizon = posterior.horizon.toString(),
    observationCount = posterior.observationCount,
    independentSessionCount = posterior.independentSessionCount,
)

private fun NBio7ETemporalStateEntity.toDomain() = NBio7ETemporalStateRecordV1(
    layer = TemporalCandidateLayer.entries.single { it.storageValue == candidateLayer },
    scope = ContextScope(ContextScopeKind.valueOf(scopeKind), scopeId.ifBlank { null }),
    posterior = TemporalStatePosteriorV1(
        persistentMean = persistentMean,
        transientMean = transientMean,
        doseCoefficientMean = doseCoefficientMean,
        covariance = TemporalCovariance3(covariancePp, covariancePt, covariancePd, covarianceTt, covarianceTd, covarianceDd),
        horizon = Instant.parse(horizon),
        observationCount = observationCount,
        independentSessionCount = independentSessionCount,
    ),
)

private fun ContextSignalV1.toEntity(runId: String) = NBio7EContextSignalEntity(
    runId = runId,
    signalId = signalId,
    signalSchemaVersion = signalSchemaVersion,
    sourceModuleId = sourceModuleId,
    moduleModelVersion = moduleModelVersion,
    moduleConfigId = moduleConfigId,
    sourceFeatureId = sourceFeatureKey.featureId,
    sourceFeatureSchemaVersion = sourceFeatureKey.schemaVersion,
    target = target.name,
    scopeKind = scope.kind.name,
    scopeId = scope.id.orEmpty(),
    effectiveFrom = effectiveFrom.toString(),
    effectiveUntil = effectiveUntil?.toString(),
    effectRepresentation = effectRepresentation.name,
    locationMean = locationMean,
    variance = variance,
    evidenceRowCount = evidenceRowCount,
    independentSessionCount = independentSessionCount,
    independentEpisodeCount = independentEpisodeCount,
    evidenceMaturity = evidenceMaturity.name,
    correlationGroupId = correlationGroupId,
    episodeId = episodeId,
    encodedSourceEvidenceIds = StableStringSetCodecV1.encode(sourceEvidenceIds),
    encodedUpstreamModelIdentities = StableStringSetCodecV1.encode(upstreamModelIdentities),
    publishedAt = publishedAt.toString(),
    status = status.name,
    failureCode = failureCode,
)

private fun NBio7EContextSignalEntity.toDomain() = ContextSignalV1(
    signalId = signalId,
    signalSchemaVersion = signalSchemaVersion,
    sourceModuleId = sourceModuleId,
    moduleModelVersion = moduleModelVersion,
    moduleConfigId = moduleConfigId,
    sourceFeatureKey = ContextFeatureKey(sourceFeatureId, sourceFeatureSchemaVersion),
    target = ContextSignalTarget.valueOf(target),
    scope = ContextScope(ContextScopeKind.valueOf(scopeKind), scopeId.ifBlank { null }),
    effectiveFrom = Instant.parse(effectiveFrom),
    effectiveUntil = effectiveUntil?.let(Instant::parse),
    effectRepresentation = ContextSignalEffectRepresentation.valueOf(effectRepresentation),
    locationMean = locationMean,
    variance = variance,
    evidenceRowCount = evidenceRowCount,
    independentSessionCount = independentSessionCount,
    independentEpisodeCount = independentEpisodeCount,
    evidenceMaturity = ContextEvidenceMaturity.valueOf(evidenceMaturity),
    correlationGroupId = correlationGroupId,
    episodeId = episodeId,
    sourceEvidenceIds = StableStringSetCodecV1.decode(encodedSourceEvidenceIds),
    upstreamModelIdentities = StableStringSetCodecV1.decode(encodedUpstreamModelIdentities),
    publishedAt = Instant.parse(publishedAt),
    status = ContextSignalStatus.valueOf(status),
    failureCode = failureCode,
)

private fun NBio7EContextModuleStatusEntity.toFailureOrNull(): ContextModuleFailureV7E? =
    if (status == "OK") null else ContextModuleFailureV7E(
        moduleId = moduleId,
        phase = ContextModulePhase.valueOf(phase),
        code = requireNotNull(failureCode),
        message = requireNotNull(failureSummary),
    )

/** Deterministic and delimiter-safe without adding a persistence-format dependency. */
object StableStringSetCodecV1 {
    fun encode(values: Set<String>): String = values.sorted().joinToString(separator = "") { "${it.length}:$it" }

    fun decode(encoded: String): Set<String> {
        var offset = 0
        val result = linkedSetOf<String>()
        while (offset < encoded.length) {
            val delimiter = encoded.indexOf(':', offset)
            require(delimiter > offset) { "Malformed length-prefixed set." }
            val length = encoded.substring(offset, delimiter).toIntOrNull()
            require(length != null && length >= 0) { "Malformed length-prefixed set length." }
            val start = delimiter + 1
            val end = start + length
            require(end <= encoded.length) { "Truncated length-prefixed set." }
            require(result.add(encoded.substring(start, end))) { "Duplicate set item." }
            offset = end
        }
        return result
    }
}
