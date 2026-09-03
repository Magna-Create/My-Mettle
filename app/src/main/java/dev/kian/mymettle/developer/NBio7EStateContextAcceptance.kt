package dev.kian.mymettle.developer

import android.content.Context
import android.os.Build
import dev.kian.mymettle.BuildConfig
import dev.kian.mymettle.context.modules.ProductionContextModuleRegistryV7E
import dev.kian.mymettle.data.backup.NativeFullBackupRepository
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.context.*
import dev.kian.mymettle.domain.inference.*
import dev.kian.mymettle.inference.NBio7EShadowRepository
import dev.kian.mymettle.inference.NBio7EShadowRunV1
import dev.kian.mymettle.inference.NBio7ETemporalStateRecordV1
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

data class NBio7EPredictiveMetrics(
    val evaluableSessions: Int,
    val meanLogPredictiveScore: Double?,
    val meanCrps: Double?,
    val coverage90: Double?,
    val meanSharpness90: Double?,
    val signedBias: Double?,
    val meanAbsoluteError: Double?,
    val availabilityRate: Double,
)

data class NBio7ERealHistoryAudit(
    val residualSessions: Int,
    val profileObservations: Int,
    val contextTagCounts: Map<String, Int>,
    val independentEpisodeCounts: Map<String, Int>,
    val moduleSupportStates: Map<String, String>,
    val sourceDoseRunId: String?,
    val doseSessions: Int,
    val capabilityBaseline: NBio7EPredictiveMetrics,
    val temporalBase: NBio7EPredictiveMetrics,
    val doseTemporal: NBio7EPredictiveMetrics,
    val contextTemporal: NBio7EPredictiveMetrics,
)

data class NBio7EPersistenceAcceptance(
    val persistReloadEquivalent: Boolean,
    val moduleStateDeletionConfirmed: Boolean,
    val signalDeletionConfirmed: Boolean,
    val derivedRunDeletionConfirmed: Boolean,
    val fullReplayEquivalent: Boolean,
)

enum class NBio7EStructuralVerdict { PASS, FAIL }

data class NBio7EStateContextAcceptanceReport(
    val generatedAt: Instant,
    val app: NBioAdaptiveAppIdentity,
    val device: NBioAdaptiveDeviceIdentity,
    val roomSchemaVersion: Int,
    val temporalConfig: TemporalStateConfigV1,
    val synthetic: NBio7ESyntheticValidationReport,
    val registeredModules: List<ContextModuleDescriptor>,
    val history: NBio7ERealHistoryAudit,
    val persistence: NBio7EPersistenceAcceptance,
    val nativeBackupRoundTrip: Boolean,
    val rawFingerprintBefore: NBio7BRawEvidenceFingerprint,
    val rawFingerprintAfter: NBio7BRawEvidenceFingerprint,
    val contextFingerprintBefore: String,
    val contextFingerprintAfter: String,
    val prescriptionBefore: NBio7BPrescriptionStateFingerprint,
    val prescriptionAfter: NBio7BPrescriptionStateFingerprint,
    val benchmarkRunIdBefore: String?,
    val benchmarkRunIdAfter: String?,
    val foreignKeysClean: Boolean,
    val runtimeMillis: Long,
) {
    val structuralVerdict: NBio7EStructuralVerdict get() = if (
        roomSchemaVersion == 15 && synthetic.allPassed && persistence.persistReloadEquivalent &&
        persistence.moduleStateDeletionConfirmed && persistence.signalDeletionConfirmed &&
        persistence.derivedRunDeletionConfirmed && persistence.fullReplayEquivalent && nativeBackupRoundTrip &&
        rawFingerprintBefore == rawFingerprintAfter && contextFingerprintBefore == contextFingerprintAfter &&
        prescriptionBefore == prescriptionAfter && benchmarkRunIdBefore == benchmarkRunIdAfter && foreignKeysClean
    ) NBio7EStructuralVerdict.PASS else NBio7EStructuralVerdict.FAIL

    fun toJson(): String = JSONObject()
        .put("format", "my-mettle-n-bio-7e-state-context-acceptance")
        .put("formatVersion", 1)
        .put("generatedAt", generatedAt.toString())
        .put("mission", "N-BIO-7E")
        .put("app", JSONObject().put("applicationId", app.applicationId).put("versionName", app.versionName).put("versionCode", app.versionCode).put("buildType", app.buildType).put("debug", app.debug))
        .put("device", JSONObject().put("manufacturer", device.manufacturer).put("model", device.model).put("device", device.device).put("sdkInt", device.sdkInt))
        .put("roomSchemaVersion", roomSchemaVersion)
        .put("modelIdentity", temporalConfig.semanticVersion)
        .put("modelConfig", temporalConfig.canonicalPayload)
        .put("solverIdentity", "deterministic-robust-gaussian-filter-v1")
        .put("postponedDevelopment", JSONObject()
            .put("PD-001", JSONObject().put("status", "OPEN").put("scope", "7C_CAPABILITY_EMPIRICAL_ACCURACY"))
            .put("PD-002", JSONObject().put("status", "OPEN").put("scope", "7D_DEMAND_DOSE_EMPIRICAL_CALIBRATION"))
            .put("PD-003", JSONObject().put("status", "OPEN").put("scope", "7E_TEMPORAL_CONTEXT_EMPIRICAL_CALIBRATION")))
        .put("synthetic", JSONObject()
            .put("temporal", JSONArray(synthetic.temporalCases.map { it.toJson() }))
            .put("contextModules", JSONArray(synthetic.contextModuleCases.map { it.toJson() }))
            .put("futureDataLeakageGuard", synthetic.futureDataLeakageGuardPassed))
        .put("registeredModules", JSONArray(registeredModules.map { it.toJson() }))
        .put("contextTargets", JSONObject()
            .put("implemented", JSONArray(ContextSignalTargetPolicyV1.implementedTargets.map { it.name }.sorted()))
            .put("reserved", JSONArray(listOf("OBSERVATION_RELIABILITY", "PROCESS_VOLATILITY", "RECOVERY_DYNAMICS", "EXECUTION_CONTEXT")))
            .put("laterPhase", JSONArray(listOf("CAPABILITY_CONDITIONING", "EQUIPMENT_TRANSLATION", "RECRUITMENT_CONTEXT"))))
        .put("realHistory", history.toJson())
        .put("persistence", JSONObject()
            .put("persistReloadEquivalent", persistence.persistReloadEquivalent)
            .put("moduleStateDeletionConfirmed", persistence.moduleStateDeletionConfirmed)
            .put("signalDeletionConfirmed", persistence.signalDeletionConfirmed)
            .put("derivedRunDeletionConfirmed", persistence.derivedRunDeletionConfirmed)
            .put("fullReplayEquivalent", persistence.fullReplayEquivalent))
        .put("nativeBackupRestore", nativeBackupRoundTrip)
        .put("integrity", JSONObject()
            .put("rawEvidenceBeforeSha256", rawFingerprintBefore.sha256)
            .put("rawEvidenceAfterSha256", rawFingerprintAfter.sha256)
            .put("contextEvidenceBeforeSha256", contextFingerprintBefore)
            .put("contextEvidenceAfterSha256", contextFingerprintAfter)
            .put("prescriptionBeforeSha256", prescriptionBefore.sha256)
            .put("prescriptionAfterSha256", prescriptionAfter.sha256)
            .put("benchmarkRunIdBefore", benchmarkRunIdBefore ?: JSONObject.NULL)
            .put("benchmarkRunIdAfter", benchmarkRunIdAfter ?: JSONObject.NULL)
            .put("foreignKeysClean", foreignKeysClean))
        .put("normalBehaviourUnchanged", true)
        .put("benchmarkV0Authority", "UNCHANGED")
        .put("nBio7FStarted", false)
        .put("nBio7GStarted", false)
        .put("nBio8Started", false)
        .put("nBio9ProductIntegrationPulledForward", false)
        .put("runtimeMillis", runtimeMillis)
        .put("structuralVerdict", structuralVerdict.name)
        .put("empiricalCalibration", "PD-003_OPEN")
        .toString(2)
}

/** One foreground developer action; no normal workout path invokes this runner. */
class NBio7EStateContextAcceptanceRunner(
    private val context: Context,
    private val database: MyMettleDatabase,
    private val temporalConfig: TemporalStateConfigV1 = TemporalStateConfigV1(),
) {
    suspend fun run(onProgress: (NBio7BAcceptanceProgress) -> Unit = {}): NBio7EStateContextAcceptanceReport {
        val started = System.nanoTime()
        val inferenceDao = database.inferenceDao()
        val userProfileId = inferenceDao.userProfileIds().singleOrNull()
            ?: error("N-BIO-7E acceptance requires exactly one Native user profile.")
        val sourceRun = inferenceDao.inferenceRuns(userProfileId).firstOrNull()
            ?: error("Run the existing biological recomputation before N-BIO-7E acceptance.")
        database.nBio7EDao().deleteDerivedForUser(userProfileId)
        val rawBefore = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionBefore = NBio7BPrescriptionStateFingerprinter.capture(database)
        val benchmarkBefore = inferenceDao.latestInferenceRun(userProfileId)?.id

        onProgress(NBio7BAcceptanceProgress(0, 5, "N-BIO-7E · 17 temporal + 25 module synthetic cases"))
        val synthetic = NBio7ESyntheticValidation.run()
        val evidenceBefore = NBio7EHistoricalEvidenceReader(database).read()
        val contextBefore = fingerprint(evidenceBefore.currentContextEvidenceFingerprintInputs)

        onProgress(NBio7BAcceptanceProgress(1, 5, "N-BIO-7E · causal context-free/dose/context replay"))
        val first = compute(evidenceBefore)
        val registry = ProductionContextModuleRegistryV7E.create()
        val repository = NBio7EShadowRepository(database.nBio7EDao(), registry)
        val runId = "n-bio-7e-${UUID.randomUUID()}"
        val run = first.toPersistedRun(runId, userProfileId, sourceRun.id, temporalConfig)

        onProgress(NBio7BAcceptanceProgress(2, 5, "N-BIO-7E · persist/reload/delete-derived/full replay"))
        repository.save(run)
        val loaded = repository.load(runId)
        val persistReload = loaded == run
        repository.deleteModuleMemory(runId)
        val moduleDeleted = repository.load(runId)?.moduleStates?.isEmpty() == true
        repository.deleteSignals(runId)
        val signalsDeleted = repository.load(runId)?.signals?.isEmpty() == true
        repository.deleteDerivedRun(runId)
        val runDeleted = repository.load(runId) == null
        val replay = compute(NBio7EHistoricalEvidenceReader(database).read())
        val replayEquivalent = first == replay
        val retainedId = "n-bio-7e-${UUID.randomUUID()}"
        repository.save(replay.toPersistedRun(retainedId, userProfileId, sourceRun.id, temporalConfig))

        onProgress(NBio7BAcceptanceProgress(3, 5, "N-BIO-7E · isolated Native backup/restore"))
        val backupPass = NBio7EBackupRoundTripVerifier(context, database).verify(retainedId)

        onProgress(NBio7BAcceptanceProgress(4, 5, "N-BIO-7E · privacy/product-authority fingerprints"))
        val evidenceAfter = NBio7EHistoricalEvidenceReader(database).read()
        val rawAfter = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionAfter = NBio7BPrescriptionStateFingerprinter.capture(database)
        val benchmarkAfter = inferenceDao.latestInferenceRun(userProfileId)?.id
        val foreignKeys = database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { !it.moveToFirst() }
        onProgress(NBio7BAcceptanceProgress(5, 5, "N-BIO-7E State & Context Acceptance complete"))
        return NBio7EStateContextAcceptanceReport(
            generatedAt = Instant.now(),
            app = NBioAdaptiveAppIdentity(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE, BuildConfig.DEBUG),
            device = NBioAdaptiveDeviceIdentity(Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.VERSION.SDK_INT),
            roomSchemaVersion = database.openHelper.readableDatabase.query("PRAGMA user_version").use { it.moveToFirst(); it.getInt(0) },
            temporalConfig = temporalConfig,
            synthetic = synthetic,
            registeredModules = registry.modules.map { it.descriptor },
            history = first.audit,
            persistence = NBio7EPersistenceAcceptance(persistReload, moduleDeleted, signalsDeleted, runDeleted, replayEquivalent),
            nativeBackupRoundTrip = backupPass,
            rawFingerprintBefore = rawBefore,
            rawFingerprintAfter = rawAfter,
            contextFingerprintBefore = contextBefore,
            contextFingerprintAfter = fingerprint(evidenceAfter.currentContextEvidenceFingerprintInputs),
            prescriptionBefore = prescriptionBefore,
            prescriptionAfter = prescriptionAfter,
            benchmarkRunIdBefore = benchmarkBefore,
            benchmarkRunIdAfter = benchmarkAfter,
            foreignKeysClean = foreignKeys,
            runtimeMillis = (System.nanoTime() - started) / 1_000_000L,
        )
    }

    private data class Computation(
        val audit: NBio7ERealHistoryAudit,
        val finalStates: Map<TemporalCandidateLayer, TemporalStatePosteriorV1>,
        val moduleStates: Map<String, ContextModuleStateV7E>,
        val signals: List<ContextSignalV1>,
        val failures: List<ContextModuleFailureV7E>,
        val evidenceThrough: Instant,
    )

    private fun compute(evidence: NBio7EHistoricalEvidenceV1): Computation {
        val registry = ProductionContextModuleRegistryV7E.create()
        val runtime = ContextModuleRuntimeV7E(registry)
        val filter = NeutralTemporalStateFilterV1(temporalConfig)
        val firstAt = evidence.residuals.firstOrNull()?.at ?: Instant.EPOCH
        val states = TemporalCandidateLayer.entries.filter { it != TemporalCandidateLayer.CAPABILITY_BASELINE }
            .associateWith { filter.initial(firstAt) }.toMutableMap()
        val metrics = TemporalCandidateLayer.entries.associateWith { MetricAccumulator() }
        var moduleStates: Map<String, ContextModuleStateV7E> = emptyMap()
        val failures = mutableListOf<ContextModuleFailureV7E>()
        val processedContextSessions = mutableSetOf<String>()
        val frozenPredictionBySession = mutableMapOf<String, FrozenContextPrediction>()
        val learningResidualBySession = mutableMapOf<String, Double>()
        val priorDoses = mutableListOf<DatedSessionDose>()
        var latestSignals = emptyList<ContextSignalV1>()

        fun applyContextEvents(through: Instant, inclusive: Boolean, onlySessionId: String? = null) {
            fun isVisible(at: Instant): Boolean = at.isBefore(through) || inclusive && at == through
            evidence.contextBySession.entries
                .filter { (sessionId, items) ->
                    sessionId !in processedContextSessions &&
                        (onlySessionId == null || sessionId == onlySessionId) &&
                        items.any { isVisible(it.observedAt) }
                }
                .sortedBy { (_, items) -> items.minOf { it.observedAt } }
                .forEach { (sessionId, items) ->
                    val visible = items.filter { isVisible(it.observedAt) }
                    val eventAt = visible.maxOf { it.observedAt }
                    val result = runtime.evaluate(moduleStates) { descriptor ->
                        ContextReadViewV1(
                            phase = ContextModulePhase.POST_SESSION_UPDATE,
                            horizon = eventAt,
                            scope = ContextScope(ContextScopeKind.SESSION, sessionId),
                            grantedCapabilities = descriptor.requiredReadCapabilities,
                            ownFeatureEvidence = visible.filter { it.featureKey in descriptor.consumedFeatures },
                            frozenPrediction = frozenPredictionBySession[sessionId],
                            realisedPostSessionResidual = learningResidualBySession[sessionId],
                        )
                    }
                    moduleStates = result.states
                    latestSignals = result.signals
                    failures += result.failures
                    if (visible.size == items.size) processedContextSessions += sessionId
                }
        }

        evidence.residuals.forEach { session ->
            // Evidence tied to this session is deliberately excluded until after every candidate
            // prediction has been frozen and scored. Equal timestamps do not imply causal order.
            applyContextEvents(session.at, inclusive = false)
            val baseState = states.getValue(TemporalCandidateLayer.TEMPORAL_BASE)
            val basePrediction = filter.predict(baseState, session.at, TemporalCandidateLayer.TEMPORAL_BASE).second
            frozenPredictionBySession[session.sessionId] = FrozenContextPrediction(
                predictionId = "frozen:${session.sessionId}",
                predictedAt = session.at,
                evidenceThrough = basePrediction.evidenceThrough,
                meanLogResidual = basePrediction.mean,
                variance = basePrediction.variance,
                modelIdentity = temporalConfig.semanticVersion,
            )
            val pre = runtime.evaluate(moduleStates) { descriptor ->
                ContextReadViewV1(
                    ContextModulePhase.PRE_SESSION_PUBLICATION, session.at, ContextScope.SYSTEMIC,
                    descriptor.requiredReadCapabilities, emptyList(),
                    FrozenContextPrediction("frozen:${session.sessionId}", session.at, basePrediction.evidenceThrough, basePrediction.mean, basePrediction.variance, temporalConfig.semanticVersion),
                )
            }
            moduleStates = pre.states
            failures += pre.failures
            latestSignals = pre.signals
            val arbitrated = ContextSignalArbitratorV1.arbitrate(pre.signals, session.at)
            val systemic = arbitrated.firstOrNull { it.target == ContextSignalTarget.SYSTEMIC_TRANSIENT_STATE && it.scope == ContextScope.SYSTEMIC }
            val observationVariance = arbitrated.firstOrNull { it.target == ContextSignalTarget.OBSERVATION_VARIANCE }
            val adjustment = TemporalContextAdjustment(
                locationMean = systemic?.locationMean ?: 0.0,
                locationVariance = systemic?.variance ?: 0.0,
                observationLogVarianceShift = observationVariance?.locationMean ?: 0.0,
            )
            val recentDose = RecentDoseCovariateV1.calculate(priorDoses, session.at, temporalConfig)
            metrics.getValue(TemporalCandidateLayer.CAPABILITY_BASELINE).add(0.0, session.capabilityPredictiveVariance, session.observedLogResidual)
            listOf(TemporalCandidateLayer.TEMPORAL_BASE, TemporalCandidateLayer.DOSE_TEMPORAL, TemporalCandidateLayer.CONTEXT_TEMPORAL).forEach { layer ->
                val prediction = filter.predict(
                    states.getValue(layer), session.at, layer,
                    if (layer == TemporalCandidateLayer.TEMPORAL_BASE) null else recentDose,
                    if (layer == TemporalCandidateLayer.CONTEXT_TEMPORAL) adjustment else TemporalContextAdjustment(),
                ).second
                metrics.getValue(layer).add(prediction.mean, prediction.variance, session.observedLogResidual)
                states[layer] = filter.update(
                    states.getValue(layer), session.at, session.observedLogResidual, layer,
                    if (layer == TemporalCandidateLayer.TEMPORAL_BASE) null else recentDose,
                    if (layer == TemporalCandidateLayer.CONTEXT_TEMPORAL) adjustment else TemporalContextAdjustment(),
                ).posterior
            }
            learningResidualBySession[session.sessionId] = session.observedLogResidual - basePrediction.mean
            evidence.doseBySession[session.sessionId]?.let(priorDoses::add)
            applyContextEvents(session.at, inclusive = true, onlySessionId = session.sessionId)
        }
        val lastHorizon = maxOf(
            evidence.residuals.lastOrNull()?.at ?: firstAt,
            evidence.contextBySession.values.flatten().maxOfOrNull { it.observedAt } ?: firstAt,
        )
        applyContextEvents(lastHorizon, inclusive = true)
        val finalPre = runtime.evaluate(moduleStates) { descriptor ->
            ContextReadViewV1(ContextModulePhase.PRE_SESSION_PUBLICATION, lastHorizon, ContextScope.SYSTEMIC, descriptor.requiredReadCapabilities)
        }
        moduleStates = finalPre.states
        failures += finalPre.failures
        latestSignals = finalPre.signals
        val episodeCounts = moduleStates.mapValues { (_, state) ->
            (state as? dev.kian.mymettle.context.modules.EpisodeAssociationStateV2)?.independentEpisodeCount ?: 0
        }
        val support = latestSignals.associate { it.sourceModuleId to it.evidenceMaturity.name }.toMutableMap()
        registry.modules.forEach { support.putIfAbsent(it.descriptor.moduleId, "NO_EVIDENCE") }
        return Computation(
            audit = NBio7ERealHistoryAudit(
                residualSessions = evidence.residuals.size,
                profileObservations = evidence.residuals.sumOf { it.profileObservationCount },
                contextTagCounts = evidence.contextTagCounts,
                independentEpisodeCounts = episodeCounts,
                moduleSupportStates = support.toSortedMap(),
                sourceDoseRunId = evidence.sourceDoseRunId,
                doseSessions = evidence.doseBySession.size,
                capabilityBaseline = metrics.getValue(TemporalCandidateLayer.CAPABILITY_BASELINE).result(evidence.residuals.size),
                temporalBase = metrics.getValue(TemporalCandidateLayer.TEMPORAL_BASE).result(evidence.residuals.size),
                doseTemporal = metrics.getValue(TemporalCandidateLayer.DOSE_TEMPORAL).result(evidence.residuals.size),
                contextTemporal = metrics.getValue(TemporalCandidateLayer.CONTEXT_TEMPORAL).result(evidence.residuals.size),
            ),
            finalStates = states,
            moduleStates = moduleStates,
            signals = latestSignals,
            failures = failures.distinct(),
            evidenceThrough = lastHorizon,
        )
    }

    private fun Computation.toPersistedRun(id: String, userId: String, sourceRunId: String, config: TemporalStateConfigV1) = NBio7EShadowRunV1(
        id = id,
        userProfileId = userId,
        sourceInferenceRunId = sourceRunId,
        temporalModelConfigId = config.semanticVersion,
        calculatedAt = evidenceThrough,
        temporalStates = finalStates.map { (layer, state) -> NBio7ETemporalStateRecordV1(layer, ContextScope.SYSTEMIC, state) }
            .sortedBy { it.layer.storageValue },
        moduleStates = moduleStates,
        moduleEvidenceThrough = moduleStates.keys.associateWith { evidenceThrough },
        signals = signals.sortedBy { it.signalId },
        failures = failures,
    )

    private inner class MetricAccumulator {
        private var count = 0
        private var logScore = 0.0
        private var crps = 0.0
        private var covered = 0
        private var width = 0.0
        private var bias = 0.0
        private var mae = 0.0

        fun add(mean: Double, variance: Double, observed: Double) {
            if (!mean.isFinite() || !variance.isFinite() || variance <= 0.0 || !observed.isFinite()) return
            val sd = sqrt(variance)
            val z = (observed - mean) / sd
            val lower = mean - NORMAL_90_Z * sd
            val upper = mean + NORMAL_90_Z * sd
            count++
            logScore += -0.5 * (ln(2.0 * PI * variance) + z * z)
            crps += sd * (z * (2.0 * normalCdf(z) - 1.0) + 2.0 * normalPdf(z) - 1.0 / sqrt(PI))
            if (observed in lower..upper) covered++
            width += upper - lower
            bias += mean - observed
            mae += abs(mean - observed)
        }

        fun result(total: Int) = NBio7EPredictiveMetrics(
            count,
            if (count == 0) null else logScore / count,
            if (count == 0) null else crps / count,
            if (count == 0) null else covered.toDouble() / count,
            if (count == 0) null else width / count,
            if (count == 0) null else bias / count,
            if (count == 0) null else mae / count,
            if (total == 0) 0.0 else count.toDouble() / total,
        )
    }

    private fun fingerprint(values: List<String>): String = MessageDigest.getInstance("SHA-256")
        .digest(values.joinToString("\n").toByteArray()).joinToString("") { "%02x".format(it) }

    private fun normalPdf(z: Double): Double = exp(-0.5 * z * z) / sqrt(2.0 * PI)
    private fun normalCdf(z: Double): Double {
        val sign = if (z < 0.0) -1.0 else 1.0
        val x = abs(z) / sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * x)
        val erf = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
        return 0.5 * (1.0 + sign * erf)
    }

    companion object { private const val NORMAL_90_Z = 1.6448536269514722 }
}

private fun NBio7ESyntheticCase.toJson() = JSONObject().put("id", id).put("passed", passed).put("detail", detail ?: JSONObject.NULL)

private fun ContextModuleDescriptor.toJson() = JSONObject()
    .put("moduleId", moduleId).put("protocolVersion", protocolVersion).put("learnerFamily", learnerFamily)
    .put("modelVersion", modelVersion).put("configId", configId).put("stateSchemaVersion", stateSchemaVersion)
    .put("consumedFeatures", JSONArray(consumedFeatures.map { it.canonical }.sorted()))
    .put("requiredReadCapabilities", JSONArray(requiredReadCapabilities.map { it.name }.sorted()))
    .put("allowedTargets", JSONArray(allowedTargets.map { it.name }.sorted()))
    .put("deterministicReplay", deterministicReplay)

private fun NBio7ERealHistoryAudit.toJson() = JSONObject()
    .put("residualSessions", residualSessions).put("profileObservations", profileObservations)
    .put("contextTagCounts", JSONObject(contextTagCounts)).put("independentEpisodeCounts", JSONObject(independentEpisodeCounts))
    .put("moduleSupportStates", JSONObject(moduleSupportStates)).put("sourceDoseRunId", sourceDoseRunId ?: JSONObject.NULL)
    .put("doseSessions", doseSessions)
    .put("capabilityBaseline", capabilityBaseline.toJson()).put("temporalBase", temporalBase.toJson())
    .put("doseTemporal", doseTemporal.toJson()).put("contextTemporal", contextTemporal.toJson())

private fun NBio7EPredictiveMetrics.toJson() = JSONObject()
    .put("evaluableSessions", evaluableSessions).put("meanLogPredictiveScore", meanLogPredictiveScore ?: JSONObject.NULL)
    .put("meanCrps", meanCrps ?: JSONObject.NULL).put("coverage90", coverage90 ?: JSONObject.NULL)
    .put("meanSharpness90", meanSharpness90 ?: JSONObject.NULL).put("signedBias", signedBias ?: JSONObject.NULL)
    .put("meanAbsoluteError", meanAbsoluteError ?: JSONObject.NULL).put("availabilityRate", availabilityRate)

private class NBio7EBackupRoundTripVerifier(
    private val context: Context,
    private val source: MyMettleDatabase,
) {
    suspend fun verify(retainedRunId: String): Boolean {
        val backup = NativeFullBackupRepository(source).exportJson(pretty = false)
        val restored = androidx.room.Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        return try {
            val result = NativeFullBackupRepository(restored).restoreJson(backup)
            val sourceRun = source.nBio7EDao().run(retainedRunId)
            val restoredRun = restored.nBio7EDao().run(retainedRunId)
            result.schemaVersion == 15 && sourceRun == restoredRun &&
                source.nBio7EDao().temporalStates(retainedRunId) == restored.nBio7EDao().temporalStates(retainedRunId) &&
                source.nBio7EDao().moduleStates(retainedRunId) == restored.nBio7EDao().moduleStates(retainedRunId) &&
                source.nBio7EDao().signals(retainedRunId) == restored.nBio7EDao().signals(retainedRunId) &&
                restored.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { !it.moveToFirst() }
        } finally {
            restored.close()
        }
    }
}
