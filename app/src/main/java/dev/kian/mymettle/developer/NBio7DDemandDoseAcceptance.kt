package dev.kian.mymettle.developer

import android.content.Context
import android.os.Build
import dev.kian.mymettle.BuildConfig
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.PosteriorColumns
import dev.kian.mymettle.domain.inference.InferenceRunId
import dev.kian.mymettle.domain.inference.NBio7DConfig
import dev.kian.mymettle.domain.inference.NBio7DModelConfigs
import dev.kian.mymettle.domain.inference.NBio7DSessionResult
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import dev.kian.mymettle.domain.inference.SessionDoseResolution
import dev.kian.mymettle.domain.inference.SetDemandStructuralSupport
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.engine.performance.NonDynamicAdaptiveSparseSolver
import dev.kian.mymettle.inference.NBio7DShadowRepository
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

enum class NBio7DStructuralVerdict(val storageValue: String) {
    PASS("PASS"),
    FAIL("FAIL"),
}

enum class NBio7DEmpiricalCalibrationStatus(val storageValue: String) {
    EMPIRICAL_CALIBRATION_PENDING("EMPIRICAL_CALIBRATION_PENDING"),
}

enum class NBio7DOverallVerdict(val storageValue: String) {
    READY_FOR_7D_STRUCTURAL_CLOSURE_EMPIRICAL_CALIBRATION_PENDING(
        "READY_FOR_7D_STRUCTURAL_CLOSURE_EMPIRICAL_CALIBRATION_PENDING",
    ),
    STRUCTURAL_PREVALIDATION_FAILED("STRUCTURAL_PREVALIDATION_FAILED"),
}

data class NBio7DDistributionAudit(
    val count: Int,
    val minimum: Double?,
    val median: Double?,
    val maximum: Double?,
)

data class NBio7DRealHistoryAudit(
    val plannedSessions: Int,
    val plannedWorkingSets: Int,
    val evaluatedSessions: Int,
    val evaluatedSets: Int,
    val historicalMuscleExposures: Int,
    val resolvedEffectiveDoses: Int,
    val unresolvedEffectiveDoses: Int,
    val muscleSessionDoses: Int,
    val familySetCounts: Map<String, Int>,
    val demandSupportCounts: Map<String, Int>,
    val sessionResolutionCounts: Map<String, Int>,
    val streamFailureReasonCounts: Map<String, Int>,
    val solverIdentities: List<String>,
    val pd001DependentSetCount: Int,
    val frontierGapWidth: NBio7DDistributionAudit,
    val highDemandProbability: NBio7DDistributionAudit,
    val rawSessionDoseMedian: NBio7DDistributionAudit,
    val concaveSessionDoseMedian: NBio7DDistributionAudit,
    val streamFitTotalMillis: Long,
    val streamFitMaxMillis: Long,
)

data class NBio7DSessionPersistenceAcceptance(
    val ordinal: Int,
    val setCount: Int,
    val exposureCount: Int,
    val capabilitySnapshotCount: Int,
    val persistReloadEquivalent: Boolean,
    val deleteDerivedConfirmed: Boolean,
)

data class NBio7DDemandDoseAcceptanceReport(
    val generatedAt: Instant,
    val app: NBioAdaptiveAppIdentity,
    val device: NBioAdaptiveDeviceIdentity,
    val roomSchemaVersion: Int,
    val config: NBio7DConfig,
    val modelConfigIds: Map<String, String>,
    val solverIdentities: Map<String, String>,
    val synthetic: NBio7DSyntheticValidationReport,
    val history: NBio7DRealHistoryAudit,
    val sessionPersistence: List<NBio7DSessionPersistenceAcceptance>,
    val representativeFullReplayEquivalent: Boolean,
    val backupRoundTrip: NBio7DBackupRoundTripResult,
    val rawFingerprintBefore: NBio7BRawEvidenceFingerprint,
    val rawFingerprintAfter: NBio7BRawEvidenceFingerprint,
    val prescriptionBefore: NBio7BPrescriptionStateFingerprint,
    val prescriptionAfter: NBio7BPrescriptionStateFingerprint,
    val benchmarkRunIdBefore: String?,
    val benchmarkRunIdAfter: String?,
    val foreignKeysCleanAfter: Boolean,
    val prunedPrior7DShadowRuns: Int,
    val totalElapsedMillis: Long,
) {
    val rawEvidenceUnchanged: Boolean get() = rawFingerprintBefore == rawFingerprintAfter
    val prescriptionStateUnchanged: Boolean get() = prescriptionBefore == prescriptionAfter
    val benchmarkAuthorityUnchanged: Boolean get() = benchmarkRunIdBefore == benchmarkRunIdAfter
    val persistenceChecksPass: Boolean get() = sessionPersistence.isNotEmpty() && sessionPersistence.all {
        it.persistReloadEquivalent && it.deleteDerivedConfirmed
    }
    val structuralVerdict: NBio7DStructuralVerdict get() = if (
        roomSchemaVersion == 14 && synthetic.allPassed && history.evaluatedSets > 0 &&
        persistenceChecksPass && representativeFullReplayEquivalent && backupRoundTrip.passed &&
        rawEvidenceUnchanged && prescriptionStateUnchanged && benchmarkAuthorityUnchanged && foreignKeysCleanAfter
    ) NBio7DStructuralVerdict.PASS else NBio7DStructuralVerdict.FAIL
    val empiricalCalibrationStatus = NBio7DEmpiricalCalibrationStatus.EMPIRICAL_CALIBRATION_PENDING
    val overallVerdict: NBio7DOverallVerdict get() = if (structuralVerdict == NBio7DStructuralVerdict.PASS) {
        NBio7DOverallVerdict.READY_FOR_7D_STRUCTURAL_CLOSURE_EMPIRICAL_CALIBRATION_PENDING
    } else NBio7DOverallVerdict.STRUCTURAL_PREVALIDATION_FAILED

    fun toJson(): String = JSONObject()
        .put("format", "my-mettle-n-bio-7d-demand-dose-acceptance")
        .put("formatVersion", 1)
        .put("generatedAt", generatedAt.toString())
        .put("mission", "N-BIO-7D")
        .put("app", app.toJson7d())
        .put("device", device.toJson7d())
        .put("roomSchemaVersion", roomSchemaVersion)
        .put("normalProductAuthority", "BENCHMARK_V0_UNCHANGED")
        .put("modelConfigIds", JSONObject(modelConfigIds))
        .put("solverIdentities", JSONObject(solverIdentities))
        .put("config", JSONObject()
            .put("dynamicResistanceDeltaLog", config.dynamicResistanceDeltaLog)
            .put("loadedHoldDeltaLog", config.loadedHoldDeltaLog)
            .put("repeatedContractionDeltaLog", config.repeatedContractionDeltaLog)
            .put("durationOnlyDeltaLog", config.durationOnlyDeltaLog)
            .put("contradictionProbabilityThreshold", config.contradictionProbabilityThreshold)
            .put("tau", config.tau)
            .put("maxIndependentConvolutionNodes", config.maxIndependentConvolutionNodes))
        .put("postponedDevelopment", JSONObject()
            .put("PD-001", JSONObject()
                .put("status", "OPEN")
                .put("scope", "7C_CAPABILITY_EMPIRICAL_ACCURACY")
                .put("dependentTargetSetCount", history.pd001DependentSetCount))
            .put("PD-002", JSONObject()
                .put("status", "OPEN")
                .put("scope", "7D_SET_DEMAND_EFFECTIVE_DOSE_EMPIRICAL_CALIBRATION")
                .put("empiricalCalibrationStatus", empiricalCalibrationStatus.storageValue)))
        .put("syntheticStructuralAcceptance", synthetic.toJson7d())
        .put("realHistoryShadowAudit", history.toJson7d())
        .put("persistence", JSONArray(sessionPersistence.map { it.toJson7d() }))
        .put("representativeFullReplayEquivalent", representativeFullReplayEquivalent)
        .put("nativeBackupRoundTrip", backupRoundTrip.toJson7d())
        .put("integrity", JSONObject()
            .put("rawEvidenceBeforeSha256", rawFingerprintBefore.sha256)
            .put("rawEvidenceAfterSha256", rawFingerprintAfter.sha256)
            .put("rawEvidenceUnchanged", rawEvidenceUnchanged)
            .put("prescriptionBeforeSha256", prescriptionBefore.sha256)
            .put("prescriptionAfterSha256", prescriptionAfter.sha256)
            .put("prescriptionStateUnchanged", prescriptionStateUnchanged)
            .put("benchmarkRunIdBefore", benchmarkRunIdBefore ?: JSONObject.NULL)
            .put("benchmarkRunIdAfter", benchmarkRunIdAfter ?: JSONObject.NULL)
            .put("benchmarkAuthorityUnchanged", benchmarkAuthorityUnchanged)
            .put("foreignKeysCleanAfter", foreignKeysCleanAfter)
            .put("prunedPrior7DShadowRuns", prunedPrior7DShadowRuns))
        .put("runtimeMillis", totalElapsedMillis)
        .put("verdicts", JSONObject()
            .put("structural", structuralVerdict.storageValue)
            .put("empiricalCalibration", empiricalCalibrationStatus.storageValue)
            .put("overall7D", overallVerdict.storageValue))
        .put("quarantine", JSONArray(listOf(
            "SHADOW_CANDIDATE_ONLY",
            "NO_NORMAL_WORKOUT_PRESCRIPTION_OR_PREFILL_AUTHORITY",
            "NO_RIR_RPE_FAILURE_PROBABILITY_RECONSTRUCTION",
            "NO_FATIGUE_RECOVERY_READINESS_DEVELOPMENT_SKILL_OR_DECAY",
            "NO_NOTES_VIBE_FORM_COMFORT_SLEEP_HR_HRV_CONTEXT",
            "PD_001_AND_PD_002_REMAIN_OPEN",
        )))
        .put("nBio7EStarted", false)
        .toString(2)
}

/** One consolidated installed-device N-BIO-7D structural acceptance action. */
class NBio7DDemandDoseAcceptanceRunner(
    context: Context,
    private val database: MyMettleDatabase,
    private val config: NBio7DConfig = NBio7DConfig(),
    private val backupVerifier: NBio7DBackupRoundTripVerifier = NBio7DBackupRoundTripVerifier(context, database),
) {
    suspend fun run(
        onProgress: (NBio7BAcceptanceProgress) -> Unit = {},
    ): NBio7DDemandDoseAcceptanceReport {
        val started = System.nanoTime()
        val pruned = prunePrior7DShadowRuns()
        val dao = database.inferenceDao()
        val userProfileId = dao.userProfileIds().singleOrNull()
            ?: error("N-BIO-7D acceptance requires exactly one Native user profile.")
        val rawBefore = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionBefore = NBio7BPrescriptionStateFingerprinter.capture(database)
        val benchmarkBefore = dao.latestInferenceRun(userProfileId)?.id

        onProgress(NBio7BAcceptanceProgress(0, 4, "N-BIO-7D · structural synthetic acceptance"))
        val synthetic = NBio7DSyntheticValidation.run(config)

        onProgress(NBio7BAcceptanceProgress(1, 4, "N-BIO-7D · causal real-history shadow replay"))
        val dynamicHistory = NBio7BRawHistoryReader(database).read()
        val nonDynamicHistory = NBio7CRawHistoryReader(database).read()
        val historicalInputs = NBio7DHistoricalInputReader(database).read()
        val replayKnowledgeAt = Instant.now()
        val plan = NBio7DHistoricalReplayPlanner.plan(dynamicHistory, nonDynamicHistory, historicalInputs, replayKnowledgeAt)
        val executor = NBio7DHistoricalReplayExecutor(config)
        val execution = executor.execute(plan, dynamicHistory, nonDynamicHistory)
        val historyAudit = historyAudit(plan, execution)

        onProgress(NBio7BAcceptanceProgress(2, 4, "N-BIO-7D · Room14 persist/reload/delete/replay"))
        val repository = NBio7DShadowRepository(database)
        val retainedForBackup = mutableListOf<InferenceRunId>()
        val persistenceReports = mutableListOf<NBio7DSessionPersistenceAcceptance>()
        try {
            execution.sessions.forEachIndexed { index, session ->
                val firstRun = repository.persist(
                    userProfileId = userProfileId,
                    sessionResult = session.result,
                    setInputs = session.persistenceInputs,
                    capabilitySnapshots = session.capabilitySnapshots,
                    config = config,
                )
                val loaded = repository.load(firstRun)
                val equivalent = persistedEquivalent(session, loaded)
                repository.discard(firstRun)
                val deleted = runCatching { repository.load(firstRun) }.isFailure
                persistenceReports += NBio7DSessionPersistenceAcceptance(
                    ordinal = index + 1,
                    setCount = session.result.setResults.size,
                    exposureCount = session.result.exposureCount,
                    capabilitySnapshotCount = session.capabilitySnapshots.size,
                    persistReloadEquivalent = equivalent,
                    deleteDerivedConfirmed = deleted,
                )
                val backupRun = repository.persist(
                    userProfileId = userProfileId,
                    sessionResult = session.result,
                    setInputs = session.persistenceInputs,
                    capabilitySnapshots = session.capabilitySnapshots,
                    config = config,
                )
                retainedForBackup += backupRun
            }

            val representativePlan = plan.sessions.lastOrNull { planned ->
                execution.sessions.any { it.sessionId == planned.session.sessionId }
            }?.let { plan.copy(sessions = listOf(it)) }
            val representativeOriginal = representativePlan?.let { selected ->
                val id = selected.sessions.single().session.sessionId
                execution.sessions.singleOrNull { it.sessionId == id }?.result
            }
            val representativeReplay = representativePlan?.let {
                executor.execute(it, dynamicHistory, nonDynamicHistory).sessions.singleOrNull()?.result
            }
            val replayEquivalent = when {
                representativeOriginal == null -> false
                representativeReplay == null -> false
                else -> sessionResultsEquivalent(representativeOriginal, representativeReplay)
            }

            onProgress(NBio7BAcceptanceProgress(3, 4, "N-BIO-7D · isolated Native backup round-trip"))
            val backup = backupVerifier.verify()

            retainedForBackup.forEach { runCatching { repository.discard(it) } }
            retainedForBackup.clear()
            val rawAfter = NBio7BRawEvidenceFingerprinter.capture(database)
            val prescriptionAfter = NBio7BPrescriptionStateFingerprinter.capture(database)
            val benchmarkAfter = dao.latestInferenceRun(userProfileId)?.id
            val foreignKeysClean = database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { !it.moveToFirst() }
            onProgress(NBio7BAcceptanceProgress(4, 4, "N-BIO-7D acceptance complete"))

            val modelConfigs = NBio7DModelConfigs.definitions(config)
            return NBio7DDemandDoseAcceptanceReport(
                generatedAt = Instant.now(),
                app = NBioAdaptiveAppIdentity(
                    BuildConfig.APPLICATION_ID,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                    BuildConfig.BUILD_TYPE,
                    BuildConfig.DEBUG,
                ),
                device = NBioAdaptiveDeviceIdentity(Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.VERSION.SDK_INT),
                roomSchemaVersion = roomSchemaVersion(),
                config = config,
                modelConfigIds = modelConfigs.associate { it.component.storageValue to it.id.value },
                solverIdentities = solverIdentities(),
                synthetic = synthetic,
                history = historyAudit,
                sessionPersistence = persistenceReports,
                representativeFullReplayEquivalent = replayEquivalent,
                backupRoundTrip = backup,
                rawFingerprintBefore = rawBefore,
                rawFingerprintAfter = rawAfter,
                prescriptionBefore = prescriptionBefore,
                prescriptionAfter = prescriptionAfter,
                benchmarkRunIdBefore = benchmarkBefore,
                benchmarkRunIdAfter = benchmarkAfter,
                foreignKeysCleanAfter = foreignKeysClean,
                prunedPrior7DShadowRuns = pruned,
                totalElapsedMillis = elapsedMillis(started),
            )
        } finally {
            retainedForBackup.forEach { runCatching { repository.discard(it) } }
        }
    }

    private fun historyAudit(
        plan: NBio7DHistoricalReplayPlan,
        execution: NBio7DHistoricalReplayExecution,
    ): NBio7DRealHistoryAudit {
        val sets = execution.sessions.flatMap { it.result.setResults }
        val muscles = execution.sessions.flatMap { it.result.muscleResults }
        val gapWidths = sets.mapNotNull { it.demand.frontierGapSummary?.let { summary -> summary.credibleUpper95 - summary.credibleLower05 } }
        val qValues = sets.mapNotNull { it.demand.probabilityAtOrWithinDelta }
        val rawMedians = muscles.mapNotNull { it.dose.rawSummary?.estimateMedian }
        val concaveMedians = muscles.mapNotNull { it.dose.concaveSummary?.estimateMedian }
        val streamFailures = execution.sessions.flatMap { it.streamFailures.values }
            .groupingBy { it }.eachCount().toSortedMap()
        val fitMillis = execution.sessions.flatMap { it.streamFitElapsedMillis.values }
        return NBio7DRealHistoryAudit(
            plannedSessions = plan.sessions.size,
            plannedWorkingSets = plan.targetWorkingSetCount,
            evaluatedSessions = execution.sessions.size,
            evaluatedSets = execution.evaluatedSetCount,
            historicalMuscleExposures = execution.exposureCount,
            resolvedEffectiveDoses = execution.resolvedEffectiveDoseCount,
            unresolvedEffectiveDoses = execution.unresolvedEffectiveDoseCount,
            muscleSessionDoses = muscles.size,
            familySetCounts = sets.groupingBy { it.demand.family.storageValue }.eachCount().toSortedMap(),
            demandSupportCounts = sets.groupingBy { it.demand.structuralSupport.name }.eachCount().toSortedMap(),
            sessionResolutionCounts = muscles.groupingBy { it.dose.resolution.name }.eachCount().toSortedMap(),
            streamFailureReasonCounts = streamFailures,
            solverIdentities = execution.sessions.flatMap { it.streamSolverIdentities.values }.distinct().sorted(),
            pd001DependentSetCount = sets.count {
                it.demand.family in setOf(
                    MetricFamily.LOADED_HOLD,
                    MetricFamily.DURATION_ONLY,
                    MetricFamily.REPEATED_CONTRACTION,
                )
            },
            frontierGapWidth = distribution(gapWidths),
            highDemandProbability = distribution(qValues),
            rawSessionDoseMedian = distribution(rawMedians),
            concaveSessionDoseMedian = distribution(concaveMedians),
            streamFitTotalMillis = fitMillis.sum(),
            streamFitMaxMillis = fitMillis.maxOrNull() ?: 0L,
        )
    }

    private fun persistedEquivalent(
        session: NBio7DExecutedSession,
        persisted: NBio7DShadowRepository.PersistedRun,
    ): Boolean {
        if (persisted.run.modelVersion != NBio7DShadowRepository.SHADOW_RUN_MODEL_VERSION) return false
        if (persisted.run.muscleStateModelVersion != NBio7DShadowRepository.NO_7E_STATE_MODEL_VERSION) return false
        if (persisted.capabilityStates.size != session.capabilitySnapshots.size) return false
        if (persisted.capabilityParameterStates.size != session.capabilitySnapshots.size) return false
        val demandBySet = persisted.setDemandEstimates.associateBy { it.setObservationId }
        if (demandBySet.size != session.result.setResults.size) return false
        for (set in session.result.setResults) {
            val row = demandBySet[set.setObservationId] ?: return false
            if (!columnsEquivalent(row.posterior, set.demand.frontierGapSummary)) return false
        }
        val doseRows = persisted.muscleSetDoses.associateBy { Triple(it.setObservationId, it.muscleSegmentId, it.side) }
        if (doseRows.size != session.result.exposureCount) return false
        session.result.setResults.forEach { set ->
            set.muscleDoses.forEach { dose ->
                val row = doseRows[Triple(set.setObservationId, dose.exposure.muscleSegmentId, dose.exposure.side)] ?: return false
                if (!close(row.recruitmentWeight, dose.exposure.recruitmentWeight) ||
                    !close(row.conservativeExposure, dose.exposure.conservativeExposure) ||
                    row.recruitmentProfileVersionId != dose.exposure.historicalRecruitmentProfileVersionId ||
                    !columnsEquivalent(row.effectiveDose, dose.summary)
                ) return false
            }
        }
        val sessionRows = persisted.muscleSessionDoses.associateBy { it.muscleSegmentId to it.side }
        if (sessionRows.size != session.result.muscleResults.size) return false
        return session.result.muscleResults.all { muscle ->
            val row = sessionRows[muscle.key.muscleSegmentId to muscle.key.side] ?: return@all false
            columnsEquivalent(row.posterior, muscle.dose.rawSummary)
        }
    }

    private fun columnsEquivalent(
        columns: PosteriorColumns?,
        summary: dev.kian.mymettle.domain.inference.PosteriorSummary?,
    ): Boolean {
        val columnsResolved = columns?.let { listOf(it.p05, it.p50, it.p95, it.variance).all { value -> value != null } } == true
        if (summary == null) return columns == null || !columnsResolved
        if (!columnsResolved) return false
        return close(requireNotNull(columns?.p05), summary.credibleLower05) &&
            close(requireNotNull(columns?.p50), summary.estimateMedian) &&
            close(requireNotNull(columns?.p95), summary.credibleUpper95) &&
            close(requireNotNull(columns?.variance), summary.posteriorVariance)
    }

    private fun sessionResultsEquivalent(left: NBio7DSessionResult, right: NBio7DSessionResult): Boolean {
        if (left.setResults.size != right.setResults.size || left.muscleResults.size != right.muscleResults.size) return false
        val rightSets = right.setResults.associateBy { it.setObservationId }
        left.setResults.forEach { set ->
            val other = rightSets[set.setObservationId] ?: return false
            if (set.capabilityStreamKey != other.capabilityStreamKey || set.demand.structuralSupport != other.demand.structuralSupport) return false
            if (!closeNullable(set.demand.probabilityAtOrWithinDelta, other.demand.probabilityAtOrWithinDelta) ||
                !closeNullable(set.demand.contradictionProbability, other.demand.contradictionProbability)
            ) return false
            if (!summaryEquivalent(set.demand.frontierGapSummary, other.demand.frontierGapSummary)) return false
            if (set.muscleDoses.size != other.muscleDoses.size) return false
        }
        val rightMuscles = right.muscleResults.associateBy { it.key }
        return left.muscleResults.all { muscle ->
            val other = rightMuscles[muscle.key] ?: return@all false
            muscle.dose.resolution == other.dose.resolution &&
                muscle.dose.unresolvedSetCount == other.dose.unresolvedSetCount &&
                muscle.dose.crossStreamIndependenceApproximation == other.dose.crossStreamIndependenceApproximation &&
                summaryEquivalent(muscle.dose.rawSummary, other.dose.rawSummary) &&
                summaryEquivalent(muscle.dose.concaveSummary, other.dose.concaveSummary)
        }
    }

    private fun summaryEquivalent(
        left: dev.kian.mymettle.domain.inference.PosteriorSummary?,
        right: dev.kian.mymettle.domain.inference.PosteriorSummary?,
    ): Boolean {
        if (left == null || right == null) return left == null && right == null
        return close(left.credibleLower05, right.credibleLower05) &&
            close(left.estimateMedian, right.estimateMedian) &&
            close(left.credibleUpper95, right.credibleUpper95) &&
            close(left.posteriorVariance, right.posteriorVariance)
    }

    private fun solverIdentities(): Map<String, String> = buildMap {
        put("dynamic_resistance", NBioCorrectedCandidateV2Bundle.sparseSolver().solverIdentity.identity)
        NonDynamicCapabilityV1.supportedFamilies.sortedBy { it.storageValue }.forEach { family ->
            put(family.storageValue, NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.configFor(family)).solverConfig.solverIdentity.identity)
        }
    }

    private fun prunePrior7DShadowRuns(): Int {
        val sqlite = database.openHelper.writableDatabase
        val before = sqlite.query(
            "SELECT COUNT(*) FROM inference_run WHERE executionMode = 'shadow' AND modelVersion = ?",
            arrayOf(NBio7DShadowRepository.SHADOW_RUN_MODEL_VERSION),
        ).use { cursor -> check(cursor.moveToFirst()); cursor.getInt(0) }
        sqlite.execSQL(
            "DELETE FROM inference_run WHERE executionMode = 'shadow' AND modelVersion = ?",
            arrayOf<Any>(NBio7DShadowRepository.SHADOW_RUN_MODEL_VERSION),
        )
        return before
    }

    private fun roomSchemaVersion(): Int = database.openHelper.readableDatabase.query("PRAGMA user_version").use {
        check(it.moveToFirst())
        it.getInt(0)
    }

    private fun distribution(values: List<Double>): NBio7DDistributionAudit {
        if (values.isEmpty()) return NBio7DDistributionAudit(0, null, null, null)
        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        return NBio7DDistributionAudit(sorted.size, sorted.first(), median, sorted.last())
    }

    private fun closeNullable(left: Double?, right: Double?): Boolean = when {
        left == null || right == null -> left == null && right == null
        else -> close(left, right)
    }

    private fun close(left: Double, right: Double): Boolean = abs(left - right) <= 1e-10 * max(1.0, max(abs(left), abs(right)))
    private fun elapsedMillis(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L
}

private fun NBioAdaptiveAppIdentity.toJson7d(): JSONObject = JSONObject()
    .put("applicationId", applicationId)
    .put("versionName", versionName)
    .put("versionCode", versionCode)
    .put("buildType", buildType)
    .put("debug", debug)

private fun NBioAdaptiveDeviceIdentity.toJson7d(): JSONObject = JSONObject()
    .put("manufacturer", manufacturer)
    .put("model", model)
    .put("device", device)
    .put("sdkInt", sdkInt)

private fun NBio7DSyntheticValidationReport.toJson7d(): JSONObject = JSONObject()
    .put("caseCount", cases.size)
    .put("passedCount", passedCount)
    .put("failedCount", failedCount)
    .put("allPassed", allPassed)
    .put("cases", JSONArray(cases.map { JSONObject().put("id", it.id).put("passed", it.passed).put("detail", it.detail) }))

private fun NBio7DRealHistoryAudit.toJson7d(): JSONObject = JSONObject()
    .put("plannedSessions", plannedSessions)
    .put("plannedWorkingSets", plannedWorkingSets)
    .put("evaluatedSessions", evaluatedSessions)
    .put("evaluatedSets", evaluatedSets)
    .put("historicalMuscleExposures", historicalMuscleExposures)
    .put("resolvedEffectiveDoses", resolvedEffectiveDoses)
    .put("unresolvedEffectiveDoses", unresolvedEffectiveDoses)
    .put("muscleSessionDoses", muscleSessionDoses)
    .put("familySetCounts", JSONObject(familySetCounts))
    .put("demandSupportCounts", JSONObject(demandSupportCounts))
    .put("sessionResolutionCounts", JSONObject(sessionResolutionCounts))
    .put("streamFailureReasonCounts", JSONObject(streamFailureReasonCounts))
    .put("solverIdentities", JSONArray(solverIdentities))
    .put("pd001DependentSetCount", pd001DependentSetCount)
    .put("frontierGapWidth", frontierGapWidth.toJson7d())
    .put("highDemandProbability", highDemandProbability.toJson7d())
    .put("rawSessionDoseMedian", rawSessionDoseMedian.toJson7d())
    .put("concaveSessionDoseMedian", concaveSessionDoseMedian.toJson7d())
    .put("streamFitTotalMillis", streamFitTotalMillis)
    .put("streamFitMaxMillis", streamFitMaxMillis)

private fun NBio7DDistributionAudit.toJson7d(): JSONObject = JSONObject()
    .put("count", count)
    .put("minimum", minimum ?: JSONObject.NULL)
    .put("median", median ?: JSONObject.NULL)
    .put("maximum", maximum ?: JSONObject.NULL)

private fun NBio7DSessionPersistenceAcceptance.toJson7d(): JSONObject = JSONObject()
    .put("ordinal", ordinal)
    .put("setCount", setCount)
    .put("exposureCount", exposureCount)
    .put("capabilitySnapshotCount", capabilitySnapshotCount)
    .put("persistReloadEquivalent", persistReloadEquivalent)
    .put("deleteDerivedConfirmed", deleteDerivedConfirmed)

private fun NBio7DBackupRoundTripResult.toJson7d(): JSONObject = JSONObject()
    .put("schemaVersion", schemaVersion)
    .put("tableCount", tableCount)
    .put("rowCount", rowCount)
    .put("rawEvidenceMatches", rawEvidenceMatches)
    .put("prescriptionStateMatches", prescriptionStateMatches)
    .put("candidateRowsMatch", candidateRowsMatch)
    .put("candidateRowsPresent", candidateRowsPresent)
    .put("sevenEStateEmpty", sevenEStateEmpty)
    .put("foreignKeysClean", foreignKeysClean)
    .put("sourceCandidateCounts", sourceCandidateCounts.toJson7d())
    .put("restoredCandidateCounts", restoredCandidateCounts.toJson7d())
    .put("passed", passed)

private fun NBio7DBackupCandidateCounts.toJson7d(): JSONObject = JSONObject()
    .put("shadowRuns", shadowRuns)
    .put("capabilityStates", capabilityStates)
    .put("capabilityParameterStates", capabilityParameterStates)
    .put("setDemandEstimates", setDemandEstimates)
    .put("muscleSetDoses", muscleSetDoses)
    .put("muscleSessionDoses", muscleSessionDoses)
    .put("adaptiveMuscleStates", adaptiveMuscleStates)
    .put("skillStates", skillStates)
