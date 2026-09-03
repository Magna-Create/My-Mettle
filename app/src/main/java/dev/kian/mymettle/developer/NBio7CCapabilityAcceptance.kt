package dev.kian.mymettle.developer

import android.content.Context
import android.os.Build
import dev.kian.mymettle.BuildConfig
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.inference.DurationOnlyCapabilityQuery
import dev.kian.mymettle.domain.inference.LoadedHoldCapabilityQuery
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFit
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityQuery
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import dev.kian.mymettle.domain.inference.NonDynamicFitFailureReason
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.RepeatedContractionCapabilityQuery
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.engine.inference.HistoricalObservationRevisionSelector
import dev.kian.mymettle.engine.inference.NonDynamicPosteriorFidelity
import dev.kian.mymettle.engine.inference.NonDynamicPosteriorFidelityResult
import dev.kian.mymettle.engine.performance.NonDynamicAdaptiveSparseSolver
import dev.kian.mymettle.engine.performance.NonDynamicCapabilityEvidenceProjector
import dev.kian.mymettle.engine.performance.NonDynamicCapabilitySolver
import dev.kian.mymettle.engine.performance.NonDynamicDenseReferenceSolver
import dev.kian.mymettle.inference.NonDynamicCapabilityParameterCodec
import dev.kian.mymettle.inference.NonDynamicCapabilityShadowRepository
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

enum class NBio7CEmpiricalAccuracyStatus(val storageValue: String) {
    EMPIRICAL_ACCURACY_PENDING("EMPIRICAL_ACCURACY_PENDING"),
    NOT_EVALUATED_REAL_HISTORY("NOT_EVALUATED_REAL_HISTORY"),
}

enum class NBio7CStructuralVerdict(val storageValue: String) {
    PASS("PASS"),
    FAIL("FAIL"),
}

enum class NBio7COverallVerdict(val storageValue: String) {
    READY_FOR_7C_STRUCTURAL_CLOSURE_EMPIRICAL_ACCURACY_PENDING("READY_FOR_7C_STRUCTURAL_CLOSURE_EMPIRICAL_ACCURACY_PENDING"),
    STRUCTURAL_PREVALIDATION_FAILED("STRUCTURAL_PREVALIDATION_FAILED"),
}

data class NBio7CProfileAcceptance(
    val family: MetricFamily,
    val executionProfileVersionId: String,
    val label: String,
    val side: String,
    val eligibleObservations: Int,
    val independentSessions: Int,
    val exclusionReasonCounts: Map<String, Int>,
    val durationMinSeconds: Double?,
    val durationMaxSeconds: Double?,
    val resistanceMinKg: Double?,
    val resistanceMaxKg: Double?,
    val cycleMin: Int?,
    val cycleMax: Int?,
    val cadenceValuesPerMinute: List<Double>,
    val referenceCoordinate: Double?,
    val sparseFitElapsedMillis: Long?,
    val denseFitElapsedMillis: Long?,
    val sparseRetainedNodes: Int?,
    val denseRetainedNodes: Int?,
    val denseVsSparseFidelity: NonDynamicPosteriorFidelityResult?,
    val persistReloadEquivalent: Boolean?,
    val deleteDerivedConfirmed: Boolean?,
    val fullReplayEquivalent: Boolean?,
    val persistenceReplayElapsedMillis: Long?,
    val empiricalAccuracyStatus: NBio7CEmpiricalAccuracyStatus,
    val numericalFailure: String?,
    val limitations: List<String>,
) {
    val structuralPassed: Boolean get() = when {
        eligibleObservations == 0 -> true
        numericalFailure != null -> numericalFailure.startsWith("EXPECTED_TYPED_UNSUPPORTED:")
        persistReloadEquivalent != true || deleteDerivedConfirmed != true || fullReplayEquivalent != true -> false
        denseVsSparseFidelity == null -> false
        denseVsSparseFidelity.queries.any { it.p50RelativeError > 0.10 } -> false
        denseVsSparseFidelity.maximumQueryTailRelativeError > 0.30 -> false
        denseVsSparseFidelity.positiveTrajectoryProbabilityAbsoluteError > 0.15 -> false
        else -> true
    }
}

data class NBio7CFamilyAcceptance(
    val family: MetricFamily,
    val definedExecutionProfileVersions: Int,
    val realHistoryProfileSides: Int,
    val rawObservationRevisions: Int,
    val currentEligibleObservations: Int,
    val independentSessions: Int,
    val sideStreams: List<String>,
    val exclusionReasonCounts: Map<String, Int>,
    val empiricalAccuracyStatus: NBio7CEmpiricalAccuracyStatus,
    val structuralVerdict: NBio7CStructuralVerdict,
)

data class NBio7CCapabilityAcceptanceReport(
    val generatedAt: Instant,
    val app: NBioAdaptiveAppIdentity,
    val device: NBioAdaptiveDeviceIdentity,
    val roomSchemaVersion: Int,
    val evidencePolicyIdentity: String,
    val familyModelIdentities: Map<String, String>,
    val familyModelConfigIds: Map<String, String>,
    val adaptiveSparseSolverIdentities: Map<String, String>,
    val denseSolverIdentities: Map<String, String>,
    val familyReports: List<NBio7CFamilyAcceptance>,
    val profileReports: List<NBio7CProfileAcceptance>,
    val synthetic: NBio7CSyntheticValidation.Report,
    val rawFingerprintBefore: NBio7BRawEvidenceFingerprint,
    val rawFingerprintAfter: NBio7BRawEvidenceFingerprint,
    val prescriptionBefore: NBio7BPrescriptionStateFingerprint,
    val prescriptionAfter: NBio7BPrescriptionStateFingerprint,
    val benchmarkRunIdBefore: String?,
    val benchmarkRunIdAfter: String?,
    val backupRoundTrip: NBio7CBackupRoundTripResult,
    val prunedPrior7CShadowRuns: Int,
    val totalElapsedMillis: Long,
) {
    val rawEvidenceUnchanged: Boolean get() = rawFingerprintBefore == rawFingerprintAfter
    val prescriptionStateUnchanged: Boolean get() = prescriptionBefore == prescriptionAfter
    val benchmarkAuthorityUnchanged: Boolean get() = benchmarkRunIdBefore == benchmarkRunIdAfter
    val allRealPersistenceReplayChecksPass: Boolean get() = profileReports.all { it.structuralPassed }
    val structuralVerdict: NBio7CStructuralVerdict get() = if (
        roomSchemaVersion == 15 &&
        synthetic.passed &&
        allRealPersistenceReplayChecksPass &&
        rawEvidenceUnchanged &&
        prescriptionStateUnchanged &&
        benchmarkAuthorityUnchanged &&
        backupRoundTrip.passed
    ) NBio7CStructuralVerdict.PASS else NBio7CStructuralVerdict.FAIL
    val overallVerdict: NBio7COverallVerdict get() = if (structuralVerdict == NBio7CStructuralVerdict.PASS) {
        NBio7COverallVerdict.READY_FOR_7C_STRUCTURAL_CLOSURE_EMPIRICAL_ACCURACY_PENDING
    } else NBio7COverallVerdict.STRUCTURAL_PREVALIDATION_FAILED
    val empiricalAccuracyStatus: NBio7CEmpiricalAccuracyStatus
        get() = if (profileReports.any { it.eligibleObservations > 0 }) {
            NBio7CEmpiricalAccuracyStatus.EMPIRICAL_ACCURACY_PENDING
        } else NBio7CEmpiricalAccuracyStatus.NOT_EVALUATED_REAL_HISTORY

    fun toJson(): String = JSONObject()
        .put("format", "my-mettle-n-bio-7c-capability-acceptance")
        .put("formatVersion", 1)
        .put("generatedAt", generatedAt.toString())
        .put("mission", "N-BIO-7C")
        .put("postponedDevelopment", JSONObject()
            .put("registerEntry", "PD-001")
            .put("status", "OPEN_POSTPONED_UNTIL_SUFFICIENT_REAL_OR_EXTERNAL_LONGITUDINAL_EVIDENCE")
            .put("empiricalAccuracyStatus", empiricalAccuracyStatus.storageValue)
            .put("quarantine", JSONArray(listOf(
                "BENCHMARK_V0_REMAINS_NORMAL_PRODUCT_AUTHORITY",
                "NO_7C_NORMAL_USER_PRESCRIPTION_OR_PREFILL_AUTHORITY",
                "DOWNSTREAM_MAY_CONSUME_CONTRACTS_NOT_EMPIRICALLY_VALIDATED_NUMERIC_TRUTH",
                "DOWNSTREAM_7C_DERIVED_SCIENTIFIC_CLAIMS_REMAIN_SYNTHETIC_STRUCTURAL_OR_EMPIRICAL_ACCURACY_PENDING",
                "NO_TUNING_AROUND_SPARSE_PERSONAL_7C_NUMERIC_ACCURACY",
                "RAW_EVIDENCE_AND_MODEL_VERSIONS_REMAIN_REPLAYABLE",
            ))))
        .put("app", app.toJson7c())
        .put("device", device.toJson7c())
        .put("roomSchemaVersion", roomSchemaVersion)
        .put("normalProductAuthority", "BENCHMARK_V0_UNCHANGED")
        .put("candidateV2DynamicResistanceFoundation", "FROZEN_7BX_DECISION_NOT_REOPENED")
        .put("evidencePolicyIdentity", evidencePolicyIdentity)
        .put("familyMathematicalModelIdentities", JSONObject(familyModelIdentities))
        .put("familyModelConfigIds", JSONObject(familyModelConfigIds))
        .put("adaptiveSparseSolverIdentities", JSONObject(adaptiveSparseSolverIdentities))
        .put("denseReferenceSolverIdentities", JSONObject(denseSolverIdentities))
        .put("families", JSONArray(familyReports.map { it.toJson7c() }))
        .put("profiles", JSONArray(profileReports.map { it.toJson7c() }))
        .put("syntheticLatentTruth", synthetic.toJson7c())
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
            .put("prunedPrior7CShadowRuns", prunedPrior7CShadowRuns))
        .put("nativeBackupRoundTrip", backupRoundTrip.toJson7c())
        .put("totalElapsedMillis", totalElapsedMillis)
        .put("verdicts", JSONObject()
            .put("structuralPrevalidation", structuralVerdict.storageValue)
            .put("empiricalAccuracy", empiricalAccuracyStatus.storageValue)
            .put("overall7C", overallVerdict.storageValue))
        .put("nBio7DStarted", false)
        .toString(2)
}

/**
 * One consolidated installed-device action for all N-BIO-7C families.
 *
 * It deliberately does not claim real-history empirical calibration while PD-001 remains open.
 * Real history is audited and used for persistence/replay/numerical integrity only where support exists.
 */
class NBio7CCapabilityAcceptanceRunner(
    context: Context,
    private val database: MyMettleDatabase,
    private val backupVerifier: NBio7CBackupRoundTripVerifier = NBio7CBackupRoundTripVerifier(context, database),
) {
    suspend fun run(
        onProgress: (NBio7BAcceptanceProgress) -> Unit = {},
    ): NBio7CCapabilityAcceptanceReport {
        val started = System.nanoTime()
        val pruned = prunePrior7CShadowRuns()
        val inferenceDao = database.inferenceDao()
        val userProfileId = inferenceDao.userProfileIds().singleOrNull()
            ?: error("N-BIO-7C acceptance requires exactly one Native user profile.")
        val rawBefore = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionBefore = NBio7BPrescriptionStateFingerprinter.capture(database)
        val benchmarkBefore = inferenceDao.latestInferenceRun(userProfileId)?.id

        onProgress(NBio7BAcceptanceProgress(0, 0, "Running N-BIO-7C synthetic latent-truth pre-validation"))
        val synthetic = NBio7CSyntheticValidation.run()
        val history = NBio7CRawHistoryReader(database).read()
        val currentHeads = HistoricalObservationRevisionSelector.currentAsOf(history.revisions, Instant.MAX)
        val groupDescriptors = history.profiles.values.flatMap { descriptor ->
            val sides = history.revisions
                .filter { it.evidence.executionProfileVersionId == descriptor.semantics.executionProfileVersionId }
                .map { it.evidence.laterality }
                .distinct()
                .sortedBy { it.storageValue }
            sides.map { side -> descriptor to side }
        }.sortedWith(
            compareBy<Pair<NBio7CProfileDescriptor, Laterality>> { it.first.semantics.metricFamily.storageValue }
                .thenBy { it.first.label }
                .thenBy { it.second.storageValue },
        )
        val profileReports = mutableListOf<NBio7CProfileAcceptance>()
        val retainedForBackup = mutableListOf<Pair<NonDynamicCapabilityShadowRepository, dev.kian.mymettle.domain.inference.InferenceRunId>>()

        groupDescriptors.forEachIndexed { index, (descriptor, side) ->
            onProgress(NBio7BAcceptanceProgress(index, groupDescriptors.size + 1, "N-BIO-7C · ${descriptor.label} · ${side.storageValue}"))
            val family = descriptor.semantics.metricFamily
            val config = NonDynamicCapabilityV1.configFor(family)
            val sparseSolver = NonDynamicAdaptiveSparseSolver(config)
            val denseSolver = NonDynamicDenseReferenceSolver(config)
            val candidates = currentHeads.filter { it.executionProfileVersionId == descriptor.semantics.executionProfileVersionId }
            val projection = NonDynamicCapabilityEvidenceProjector.project(descriptor.semantics, side, candidates)
            val exclusionCounts = projection.exclusions.groupingBy { it.reason.storageValue }.eachCount().toSortedMap()
            val limitations = mutableListOf<String>()
            var sparseFit: NonDynamicCapabilityFit? = null
            var sparseElapsed: Long? = null
            var denseElapsed: Long? = null
            var fidelity: NonDynamicPosteriorFidelityResult? = null
            var persistReload: Boolean? = null
            var deleteDerived: Boolean? = null
            var replayEquivalent: Boolean? = null
            var persistenceElapsed: Long? = null
            var numericalFailure: String? = null

            if (projection.evidence.isEmpty()) {
                limitations += "No eligible real-history evidence remains for this exact profile-version/side."
            } else {
                val horizon = projection.evidence.maxOf { it.completedAt }
                try {
                    val sparseStart = System.nanoTime()
                    sparseFit = sparseSolver.fit(projection, horizon, NonDynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
                    sparseElapsed = elapsedMillis(sparseStart)
                    val denseStart = System.nanoTime()
                    val denseFit = denseSolver.fit(projection, horizon, NonDynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
                    denseElapsed = elapsedMillis(denseStart)
                    val queries = representativeQueries(sparseFit)
                    fidelity = NonDynamicPosteriorFidelity.compare(denseSolver, denseFit, sparseSolver, sparseFit, queries)
                    if (fidelity.maximumQueryTailRelativeError > 0.30 ||
                        fidelity.queries.any { it.p50RelativeError > 0.10 } ||
                        fidelity.positiveTrajectoryProbabilityAbsoluteError > 0.15
                    ) {
                        numericalFailure = "SOLVER_FIDELITY_REJECTED:Adaptive Sparse exceeded the pre-registered Dense fidelity envelope on installed history."
                    }

                    val persistenceStart = System.nanoTime()
                    val repository = NonDynamicCapabilityShadowRepository(database, sparseSolver)
                    val temporaryRunId = repository.persist(userProfileId, sparseFit)
                    val reloaded = repository.load(temporaryRunId)
                    persistReload = equivalentFitAndPredictions(sparseSolver, sparseFit, reloaded, queries.map { it.second })
                    repository.discard(temporaryRunId)
                    deleteDerived = runCatching { repository.load(temporaryRunId) }.isFailure
                    val replay = sparseSolver.fit(projection, horizon, NonDynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
                    replayEquivalent = equivalentFitAndPredictions(sparseSolver, sparseFit, replay, queries.map { it.second })
                    val backupRunId = repository.persist(userProfileId, replay)
                    retainedForBackup += repository to backupRunId
                    persistenceElapsed = elapsedMillis(persistenceStart)
                } catch (failure: NonDynamicCapabilityFitException) {
                    numericalFailure = if (failure.reason in EXPECTED_REAL_HISTORY_FAIL_CLOSED_REASONS) {
                        "EXPECTED_TYPED_UNSUPPORTED:${failure.reason.storageValue}:${failure.message}"
                    } else {
                        "${failure.reason.storageValue}:${failure.message}"
                    }
                    limitations += "Installed-history fit did not produce an authoritative empirical result; ${failure.reason.storageValue} was reported explicitly."
                } catch (failure: Throwable) {
                    numericalFailure = "UNEXPECTED:${failure::class.java.simpleName}:${failure.message ?: "unknown"}"
                }
            }

            if (projection.evidence.isNotEmpty()) {
                limitations += "PD-001 is open: installed-history numerical output is pre-validation evidence only, not empirical accuracy/calibration PASS."
            }
            profileReports += NBio7CProfileAcceptance(
                family = family,
                executionProfileVersionId = descriptor.semantics.executionProfileVersionId.value,
                label = descriptor.label,
                side = side.storageValue,
                eligibleObservations = projection.evidence.size,
                independentSessions = projection.independentSessionCount,
                exclusionReasonCounts = exclusionCounts,
                durationMinSeconds = projection.durationDomain?.start,
                durationMaxSeconds = projection.durationDomain?.endInclusive,
                resistanceMinKg = projection.resistanceDomainKg?.start,
                resistanceMaxKg = projection.resistanceDomainKg?.endInclusive,
                cycleMin = projection.cycleDomain?.first,
                cycleMax = projection.cycleDomain?.last,
                cadenceValuesPerMinute = projection.cadenceValues.sorted(),
                referenceCoordinate = projection.referenceCoordinate,
                sparseFitElapsedMillis = sparseElapsed,
                denseFitElapsedMillis = denseElapsed,
                sparseRetainedNodes = sparseFit?.retainedBaseNodeCount,
                denseRetainedNodes = fidelity?.referenceRetainedBaseNodeCount,
                denseVsSparseFidelity = fidelity,
                persistReloadEquivalent = persistReload,
                deleteDerivedConfirmed = deleteDerived,
                fullReplayEquivalent = replayEquivalent,
                persistenceReplayElapsedMillis = persistenceElapsed,
                empiricalAccuracyStatus = if (projection.evidence.isEmpty()) {
                    NBio7CEmpiricalAccuracyStatus.NOT_EVALUATED_REAL_HISTORY
                } else NBio7CEmpiricalAccuracyStatus.EMPIRICAL_ACCURACY_PENDING,
                numericalFailure = numericalFailure,
                limitations = limitations,
            )
        }

        onProgress(NBio7BAcceptanceProgress(groupDescriptors.size, groupDescriptors.size + 1, "N-BIO-7C · Native Room15 backup round-trip"))
        val backup = try {
            backupVerifier.verify()
        } finally {
            retainedForBackup.forEach { (repository, runId) -> runCatching { repository.discard(runId) } }
        }
        val rawAfter = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionAfter = NBio7BPrescriptionStateFingerprinter.capture(database)
        val benchmarkAfter = inferenceDao.latestInferenceRun(userProfileId)?.id
        val familyReports = NonDynamicCapabilityV1.supportedFamilies.sortedBy { it.storageValue }.map { family ->
            val definitions = history.profiles.values.filter { it.semantics.metricFamily == family }
            val familyProfiles = profileReports.filter { it.family == family }
            val familyRevisionCount = history.revisions.count { it.evidence.metricFamily == family }
            NBio7CFamilyAcceptance(
                family = family,
                definedExecutionProfileVersions = definitions.size,
                realHistoryProfileSides = familyProfiles.size,
                rawObservationRevisions = familyRevisionCount,
                currentEligibleObservations = familyProfiles.sumOf { it.eligibleObservations },
                independentSessions = history.revisions.filter { it.evidence.metricFamily == family }
                    .mapNotNull { it.evidence.sessionId }.distinct().size,
                sideStreams = familyProfiles.map { it.side }.distinct().sorted(),
                exclusionReasonCounts = familyProfiles.flatMap { it.exclusionReasonCounts.entries }
                    .groupBy({ it.key }, { it.value }).mapValues { (_, counts) -> counts.sum() }.toSortedMap(),
                empiricalAccuracyStatus = if (familyProfiles.any { it.eligibleObservations > 0 }) {
                    NBio7CEmpiricalAccuracyStatus.EMPIRICAL_ACCURACY_PENDING
                } else NBio7CEmpiricalAccuracyStatus.NOT_EVALUATED_REAL_HISTORY,
                structuralVerdict = if (familyProfiles.all { it.structuralPassed } &&
                    synthetic.familyPassed[family] == true
                ) NBio7CStructuralVerdict.PASS else NBio7CStructuralVerdict.FAIL,
            )
        }

        return NBio7CCapabilityAcceptanceReport(
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
            evidencePolicyIdentity = NonDynamicCapabilityV1.evidencePolicy.identity,
            familyModelIdentities = NonDynamicCapabilityV1.supportedFamilies.associate { family ->
                family.storageValue to NonDynamicCapabilityV1.configFor(family).mathematicalModelIdentity.identity
            },
            familyModelConfigIds = NonDynamicCapabilityV1.supportedFamilies.associate { family ->
                family.storageValue to NonDynamicCapabilityV1.configFor(family)
                    .toModelConfig(NonDynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT).id.value
            },
            adaptiveSparseSolverIdentities = NonDynamicCapabilityV1.supportedFamilies.associate { family ->
                family.storageValue to NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.configFor(family)).solverConfig.solverIdentity.identity
            },
            denseSolverIdentities = NonDynamicCapabilityV1.supportedFamilies.associate { family ->
                family.storageValue to NonDynamicDenseReferenceSolver(NonDynamicCapabilityV1.configFor(family)).solverConfig.solverIdentity.identity
            },
            familyReports = familyReports,
            profileReports = profileReports,
            synthetic = synthetic,
            rawFingerprintBefore = rawBefore,
            rawFingerprintAfter = rawAfter,
            prescriptionBefore = prescriptionBefore,
            prescriptionAfter = prescriptionAfter,
            benchmarkRunIdBefore = benchmarkBefore,
            benchmarkRunIdAfter = benchmarkAfter,
            backupRoundTrip = backup,
            prunedPrior7CShadowRuns = pruned,
            totalElapsedMillis = elapsedMillis(started),
        )
    }

    private fun representativeQueries(fit: NonDynamicCapabilityFit): List<Pair<String, NonDynamicCapabilityQuery>> = when (fit.family) {
        MetricFamily.LOADED_HOLD -> listOf(
            "reference" to LoadedHoldCapabilityQuery(requireNotNull(fit.referenceCoordinate)),
            "observed_short" to LoadedHoldCapabilityQuery(requireNotNull(fit.observedInputMin)),
            "observed_long" to LoadedHoldCapabilityQuery(requireNotNull(fit.observedInputMax)),
        ).distinctBy { (it.second as LoadedHoldCapabilityQuery).durationSeconds }
        MetricFamily.DURATION_ONLY -> listOf(
            "current" to DurationOnlyCapabilityQuery(),
            "future_3_sessions" to DurationOnlyCapabilityQuery(3),
        )
        MetricFamily.REPEATED_CONTRACTION -> listOf(
            "reference" to RepeatedContractionCapabilityQuery(requireNotNull(fit.referenceCoordinate).toInt()),
            "observed_low" to RepeatedContractionCapabilityQuery(requireNotNull(fit.observedInputMin).toInt()),
            "observed_high" to RepeatedContractionCapabilityQuery(requireNotNull(fit.observedInputMax).toInt()),
        ).distinctBy { (it.second as RepeatedContractionCapabilityQuery).cycles }
        else -> error("Unsupported N-BIO-7C family")
    }

    private fun equivalentFitAndPredictions(
        solver: NonDynamicCapabilitySolver,
        expected: NonDynamicCapabilityFit,
        actual: NonDynamicCapabilityFit,
        queries: List<NonDynamicCapabilityQuery>,
    ): Boolean {
        if (!NonDynamicCapabilityParameterCodec.scientificallyEquivalent(expected, actual)) return false
        return queries.all { query -> summariesEquivalent(solver.predict(expected, query), solver.predict(actual, query)) }
    }

    private fun summariesEquivalent(left: PosteriorEstimate, right: PosteriorEstimate): Boolean {
        val a = left.summary ?: return right.summary == null
        val b = right.summary ?: return false
        return listOf(a.p05 to b.p05, a.p50 to b.p50, a.p95 to b.p95, a.posteriorVariance to b.posteriorVariance)
            .all { (x, y) -> abs(x - y) <= 1e-10 * max(1.0, max(abs(x), abs(y))) }
    }

    private fun prunePrior7CShadowRuns(): Int {
        val sqlite = database.openHelper.writableDatabase
        val before = sqlite.query(
            "SELECT COUNT(*) FROM inference_run WHERE executionMode = 'shadow' AND modelVersion = ?",
            arrayOf(NonDynamicCapabilityShadowRepository.SHADOW_RUN_MODEL_VERSION),
        ).use { cursor -> check(cursor.moveToFirst()); cursor.getInt(0) }
        sqlite.execSQL(
            "DELETE FROM inference_run WHERE executionMode = 'shadow' AND modelVersion = ?",
            arrayOf<Any>(NonDynamicCapabilityShadowRepository.SHADOW_RUN_MODEL_VERSION),
        )
        return before
    }

    private fun roomSchemaVersion(): Int = database.openHelper.readableDatabase.query("PRAGMA user_version").use {
        check(it.moveToFirst())
        it.getInt(0)
    }

    private fun elapsedMillis(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

    private companion object {
        val EXPECTED_REAL_HISTORY_FAIL_CLOSED_REASONS = setOf(
            NonDynamicFitFailureReason.INSUFFICIENT_IDENTIFIABILITY,
            NonDynamicFitFailureReason.UNSUPPORTED_CONTEXT,
            NonDynamicFitFailureReason.UNSUPPORTED_METRIC_COMBINATION,
            NonDynamicFitFailureReason.INVALID_RESISTANCE_SEMANTICS,
        )
    }
}

private fun NBioAdaptiveAppIdentity.toJson7c(): JSONObject = JSONObject()
    .put("applicationId", applicationId)
    .put("versionName", versionName)
    .put("versionCode", versionCode)
    .put("buildType", buildType)
    .put("debug", debug)

private fun NBioAdaptiveDeviceIdentity.toJson7c(): JSONObject = JSONObject()
    .put("manufacturer", manufacturer)
    .put("model", model)
    .put("device", device)
    .put("sdkInt", sdkInt)

private fun NBio7CFamilyAcceptance.toJson7c(): JSONObject = JSONObject()
    .put("family", family.storageValue)
    .put("definedExecutionProfileVersions", definedExecutionProfileVersions)
    .put("realHistoryProfileSides", realHistoryProfileSides)
    .put("rawObservationRevisions", rawObservationRevisions)
    .put("currentEligibleObservations", currentEligibleObservations)
    .put("independentSessions", independentSessions)
    .put("sideStreams", JSONArray(sideStreams))
    .put("exclusionReasonCounts", JSONObject(exclusionReasonCounts))
    .put("empiricalAccuracyStatus", empiricalAccuracyStatus.storageValue)
    .put("structuralVerdict", structuralVerdict.storageValue)

private fun NBio7CProfileAcceptance.toJson7c(): JSONObject = JSONObject()
    .put("family", family.storageValue)
    .put("executionProfileVersionId", executionProfileVersionId)
    .put("label", label)
    .put("side", side)
    .put("eligibleObservations", eligibleObservations)
    .put("independentSessions", independentSessions)
    .put("exclusionReasonCounts", JSONObject(exclusionReasonCounts))
    .put("domains", JSONObject()
        .put("durationMinSeconds", durationMinSeconds ?: JSONObject.NULL)
        .put("durationMaxSeconds", durationMaxSeconds ?: JSONObject.NULL)
        .put("resistanceMinKg", resistanceMinKg ?: JSONObject.NULL)
        .put("resistanceMaxKg", resistanceMaxKg ?: JSONObject.NULL)
        .put("cycleMin", cycleMin ?: JSONObject.NULL)
        .put("cycleMax", cycleMax ?: JSONObject.NULL)
        .put("cadenceValuesPerMinute", JSONArray(cadenceValuesPerMinute)))
    .put("referenceCoordinate", referenceCoordinate ?: JSONObject.NULL)
    .put("runtimeMillis", JSONObject()
        .put("adaptiveSparseFit", sparseFitElapsedMillis ?: JSONObject.NULL)
        .put("denseReferenceFit", denseFitElapsedMillis ?: JSONObject.NULL)
        .put("persistenceReloadDeleteReplay", persistenceReplayElapsedMillis ?: JSONObject.NULL))
    .put("retainedBaseNodes", JSONObject()
        .put("adaptiveSparse", sparseRetainedNodes ?: JSONObject.NULL)
        .put("denseReference", denseRetainedNodes ?: JSONObject.NULL))
    .put("denseVsAdaptiveSparse", denseVsSparseFidelity?.toJson7c() ?: JSONObject.NULL)
    .put("persistenceReloadEquivalent", persistReloadEquivalent ?: JSONObject.NULL)
    .put("deleteDerivedConfirmed", deleteDerivedConfirmed ?: JSONObject.NULL)
    .put("fullReplayEquivalent", fullReplayEquivalent ?: JSONObject.NULL)
    .put("empiricalAccuracyStatus", empiricalAccuracyStatus.storageValue)
    .put("numericalFailure", numericalFailure ?: JSONObject.NULL)
    .put("structuralPassed", structuralPassed)
    .put("limitations", JSONArray(limitations))

private fun NBio7CSyntheticValidation.Report.toJson7c(): JSONObject = JSONObject()
    .put("protocolVersion", protocolVersion)
    .put("evidenceClass", "SYNTHETIC_LATENT_TRUTH_PREVALIDATION_NOT_EMPIRICAL_HUMAN_ACCURACY")
    .put("passed", passed)
    .put("familyPassed", JSONObject(familyPassed.mapKeys { it.key.storageValue }))
    .put("cases", JSONArray(cases.map { case ->
        JSONObject()
            .put("family", case.family.storageValue)
            .put("scenario", case.scenario)
            .put("independentSessions", case.independentSessions)
            .put("truth", JSONObject()
                .put("frontierAtReference", case.truthFrontierAtReference)
                .put("slope", case.truthSlope ?: JSONObject.NULL)
                .put("trajectory", case.truthTrajectory))
            .put("adaptiveSparse", JSONObject()
                .put("frontier", case.sparseFrontier.toJson7c())
                .put("slope", case.sparseSlope?.toJson7c() ?: JSONObject.NULL)
                .put("trajectory", case.sparseTrajectory.toJson7c())
                .put("fitElapsedMillis", case.runtimeMillisSparse))
            .put("denseReference", JSONObject()
                .put("frontier", case.denseFrontier.toJson7c())
                .put("slope", case.denseSlope?.toJson7c() ?: JSONObject.NULL)
                .put("trajectory", case.denseTrajectory.toJson7c())
                .put("fitElapsedMillis", case.runtimeMillisDense))
            .put("fidelity", case.fidelity.toJson7c())
            .put("inDomainPrediction", case.inDomainPrediction.toJson7c())
            .put("stressPrediction", case.stressPrediction.toJson7c())
            .put("recoveryChecks", JSONObject(case.recoveryChecks))
            .put("numericalFailure", case.numericalFailure ?: JSONObject.NULL)
            .put("passed", case.passed)
    }))

private fun NonDynamicPosteriorFidelityResult.toJson7c(): JSONObject = JSONObject()
    .put("referenceSolver", referenceSolver)
    .put("candidateSolver", candidateSolver)
    .put("referenceNodeCount", referenceNodeCount)
    .put("candidateNodeCount", candidateNodeCount)
    .put("referenceRetainedBaseNodeCount", referenceRetainedBaseNodeCount)
    .put("candidateRetainedBaseNodeCount", candidateRetainedBaseNodeCount)
    .put("referencePositiveTrajectoryProbability", referencePositiveTrajectoryProbability)
    .put("candidatePositiveTrajectoryProbability", candidatePositiveTrajectoryProbability)
    .put("positiveTrajectoryProbabilityAbsoluteError", positiveTrajectoryProbabilityAbsoluteError)
    .put("maximumQueryTailRelativeError", maximumQueryTailRelativeError)
    .put("marginals", JSONArray(marginals.map { marginal -> JSONObject()
        .put("parameter", marginal.parameter)
        .put("referenceP05", marginal.referenceP05)
        .put("referenceP50", marginal.referenceP50)
        .put("referenceP95", marginal.referenceP95)
        .put("referenceVariance", marginal.referenceVariance)
        .put("candidateP05", marginal.candidateP05)
        .put("candidateP50", marginal.candidateP50)
        .put("candidateP95", marginal.candidateP95)
        .put("candidateVariance", marginal.candidateVariance)
    }))
    .put("covariances", JSONArray(covariances.map { covariance -> JSONObject()
        .put("leftParameter", covariance.leftParameter)
        .put("rightParameter", covariance.rightParameter)
        .put("referenceCovariance", covariance.referenceCovariance)
        .put("candidateCovariance", covariance.candidateCovariance)
        .put("correlationScaleError", covariance.correlationScaleError ?: JSONObject.NULL)
    }))
    .put("queries", JSONArray(queries.map { query -> JSONObject()
        .put("label", query.queryLabel)
        .put("referenceP05", query.referenceP05)
        .put("referenceP50", query.referenceP50)
        .put("referenceP95", query.referenceP95)
        .put("candidateP05", query.candidateP05)
        .put("candidateP50", query.candidateP50)
        .put("candidateP95", query.candidateP95)
        .put("p05RelativeError", query.p05RelativeError)
        .put("p50RelativeError", query.p50RelativeError)
        .put("p95RelativeError", query.p95RelativeError)
    }))

private fun dev.kian.mymettle.domain.inference.PosteriorSummary.toJson7c(): JSONObject = JSONObject()
    .put("p05", p05)
    .put("p50", p50)
    .put("p95", p95)
    .put("variance", posteriorVariance)

private fun NBio7CBackupRoundTripResult.toJson7c(): JSONObject = JSONObject()
    .put("schemaVersion", schemaVersion)
    .put("tableCount", tableCount)
    .put("rowCount", rowCount)
    .put("rawEvidenceMatches", rawEvidenceMatches)
    .put("prescriptionStateMatches", prescriptionStateMatches)
    .put("candidateRowsMatch", candidateRowsMatch)
    .put("candidateRowsPresent", candidateRowsPresent)
    .put("foreignKeysClean", foreignKeysClean)
    .put("sourceCandidateCounts", sourceCandidateCounts.toJson7c())
    .put("restoredCandidateCounts", restoredCandidateCounts.toJson7c())
    .put("passed", passed)

private fun NBio7CBackupCandidateCounts.toJson7c(): JSONObject = JSONObject()
    .put("shadowRuns", shadowRuns)
    .put("capabilityStates", capabilityStates)
    .put("capabilityParameterStates", capabilityParameterStates)
    .put("capabilityStatesByFamily", JSONObject(capabilityStatesByFamily))
    .put("capabilityParameterStatesByFamily", JSONObject(capabilityParameterStatesByFamily))
