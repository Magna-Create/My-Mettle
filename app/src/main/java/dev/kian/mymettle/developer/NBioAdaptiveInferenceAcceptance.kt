package dev.kian.mymettle.developer

import android.content.Context
import android.os.Build
import android.os.Debug
import dev.kian.mymettle.BuildConfig
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.engine.inference.CandidateV2SequentialReuseAssessment
import dev.kian.mymettle.engine.inference.DynamicTrendAdaptiveSparseSolver
import dev.kian.mymettle.engine.inference.DynamicTrendCandidateV2Solver
import dev.kian.mymettle.engine.inference.DynamicTrendConditionalLaplaceSolverAdapter
import dev.kian.mymettle.engine.inference.DynamicTrendDenseReferenceSolverAdapter
import dev.kian.mymettle.engine.inference.DynamicTrendPosteriorFidelity
import dev.kian.mymettle.engine.inference.DynamicTrendPosteriorFidelityResult
import dev.kian.mymettle.engine.inference.DynamicTrendSequentialReuseAssessment
import dev.kian.mymettle.engine.inference.DynamicTrendSolverHistoricalBakeoffCorrected
import dev.kian.mymettle.engine.inference.DynamicTrendSolverHistoricalBakeoffResult
import dev.kian.mymettle.engine.inference.HistoricalObservationRevisionSelector
import dev.kian.mymettle.engine.inference.InferenceSolverRuntimeSummary
import dev.kian.mymettle.engine.inference.InferenceSolverSubstrateBenchmark
import dev.kian.mymettle.engine.inference.InferenceSolverSubstrateBenchmarkResult
import dev.kian.mymettle.engine.inference.LowRankPosteriorScreenResult
import dev.kian.mymettle.engine.performance.DynamicResistanceEvidenceProjector
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import dev.kian.mymettle.inference.DynamicTrendCapabilityParameterCodec
import dev.kian.mymettle.inference.DynamicTrendCapabilityShadowRepository
import java.time.Instant
import kotlin.system.measureTimeMillis
import org.json.JSONArray
import org.json.JSONObject

data class NBioAdaptiveAppIdentity(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val debug: Boolean,
)

data class NBioAdaptiveDeviceIdentity(
    val manufacturer: String,
    val model: String,
    val device: String,
    val sdkInt: Int,
)

/** Privacy-bounded installed-device report for the single N-BIO-7B.X consolidation mission. */
data class NBioAdaptiveProfileResult(
    val executionProfileVersionId: String,
    val label: String,
    val side: String,
    val eligibleObservationCount: Int,
    val independentSessionCount: Int,
    val chronologicalFitCount: Int,
    val bakeoff: DynamicTrendSolverHistoricalBakeoffResult,
    val denseVsSparsePosteriorFidelity: DynamicTrendPosteriorFidelityResult?,
    val denseVsLaplacePosteriorFidelity: DynamicTrendPosteriorFidelityResult?,
    val densePersistReloadEquivalent: Boolean?,
    val sparsePersistReloadEquivalent: Boolean?,
    val laplacePersistReloadEquivalent: Boolean?,
    val denseReplayEquivalent: Boolean?,
    val sparseReplayEquivalent: Boolean?,
    val laplaceReplayEquivalent: Boolean?,
    val currentFitElapsedMillisDense: Long?,
    val currentFitElapsedMillisSparse: Long?,
    val currentFitElapsedMillisLaplace: Long?,
    val limitations: List<String>,
)

data class NBioAdaptiveInferenceAcceptanceReport(
    val generatedAt: Instant,
    val roomSchemaVersion: Int,
    val appIdentity: NBioAdaptiveAppIdentity,
    val deviceIdentity: NBioAdaptiveDeviceIdentity,
    val sequentialReuseAssessment: CandidateV2SequentialReuseAssessment,
    val rawFingerprintBefore: NBio7BRawEvidenceFingerprint,
    val rawFingerprintAfter: NBio7BRawEvidenceFingerprint,
    val prescriptionBefore: NBio7BPrescriptionStateFingerprint,
    val prescriptionAfter: NBio7BPrescriptionStateFingerprint,
    val benchmarkRunIdBefore: String?,
    val benchmarkRunIdAfter: String?,
    val profiles: List<NBioAdaptiveProfileResult>,
    val solverSubstrateBenchmark: InferenceSolverSubstrateBenchmarkResult,
    val backupRoundTrip: NBio7BBackupRoundTripResult,
    val javaHeapUsedBeforeBytes: Long,
    val javaHeapUsedAfterBytes: Long,
    val nativeHeapBeforeBytes: Long,
    val nativeHeapAfterBytes: Long,
    val totalElapsedMillis: Long,
) {
    val rawEvidenceUnchanged get() = rawFingerprintBefore == rawFingerprintAfter
    val prescriptionsUnchanged get() = prescriptionBefore == prescriptionAfter
    val benchmarkAuthorityUnchanged get() = benchmarkRunIdBefore == benchmarkRunIdAfter
    val safetyPassed get() = rawEvidenceUnchanged && prescriptionsUnchanged && benchmarkAuthorityUnchanged && backupRoundTrip.passed

    fun toJson(): String = JSONObject()
        .put("format", "my-mettle-n-bio-adaptive-inference-acceptance")
        .put("formatVersion", 3)
        .put("generatedAt", generatedAt.toString())
        .put("mission", "N-BIO-7B.X_ADAPTIVE_INFERENCE_ARCHITECTURE_CONSOLIDATION")
        .put("evidenceClass", "RETROSPECTIVE_DEVELOPMENT")
        .put("freshConfirmationRequired", true)
        .put("roomSchemaVersion", roomSchemaVersion)
        .put("app", appIdentity.toJson())
        .put("device", deviceIdentity.toJson())
        .put("contextConsumption", DynamicTrendFrontierV2.config.contextConsumption)
        .put("candidateV1Status", "FROZEN_REJECTED_EMPIRICAL_CALIBRATION")
        .put("candidateV2Status", "DEVELOPMENT_CANDIDATE_NOT_PRODUCT_AUTHORITY")
        .put(
            "candidateV2MathematicalModel",
            JSONObject()
                .put("family", DynamicTrendFrontierV2.mathematicalModelIdentity.family)
                .put("semanticVersion", DynamicTrendFrontierV2.mathematicalModelIdentity.semanticVersion)
                .put("definition", DynamicTrendFrontierV2.mathematicalModelIdentity.definition),
        )
        .put(
            "candidateV2Solvers",
            JSONArray(
                listOf(
                    DynamicTrendDenseReferenceSolverAdapter().solverIdentity.toJson(),
                    DynamicTrendAdaptiveSparseSolver().solverIdentity.toJson(),
                    DynamicTrendConditionalLaplaceSolverAdapter().solverIdentity.toJson(),
                ),
            ),
        )
        .put("candidateV2SequentialReuseAssessment", sequentialReuseAssessment.toJson())
        .put(
            "rawEvidence",
            JSONObject()
                .put("beforeSha256", rawFingerprintBefore.sha256)
                .put("afterSha256", rawFingerprintAfter.sha256)
                .put("unchanged", rawEvidenceUnchanged)
                .put("tableRowCounts", JSONObject(rawFingerprintAfter.tableRowCounts)),
        )
        .put(
            "prescriptionState",
            JSONObject()
                .put("beforeSha256", prescriptionBefore.sha256)
                .put("afterSha256", prescriptionAfter.sha256)
                .put("unchanged", prescriptionsUnchanged)
                .put("tableRowCounts", JSONObject(prescriptionAfter.tableRowCounts)),
        )
        .put(
            "benchmarkAuthority",
            JSONObject()
                .put("beforeRunId", benchmarkRunIdBefore ?: JSONObject.NULL)
                .put("afterRunId", benchmarkRunIdAfter ?: JSONObject.NULL)
                .put("unchanged", benchmarkAuthorityUnchanged)
                .put("authority", "BENCHMARK_V0"),
        )
        .put(
            "memory",
            JSONObject()
                .put("javaHeapUsedBeforeBytes", javaHeapUsedBeforeBytes)
                .put("javaHeapUsedAfterBytes", javaHeapUsedAfterBytes)
                .put("nativeHeapBeforeBytes", nativeHeapBeforeBytes)
                .put("nativeHeapAfterBytes", nativeHeapAfterBytes)
                .put("note", "Process snapshots bound the acceptance harness; per-kernel peak RAM requires profiler/device instrumentation."),
        )
        .put("performance", JSONObject().put("totalElapsedMillis", totalElapsedMillis))
        .put("profiles", JSONArray(profiles.map { it.toJson() }))
        .put("solverSubstrateBenchmark", solverSubstrateBenchmark.toJson())
        .put(
            "nativeBackupRoundTrip",
            JSONObject()
                .put("schemaVersion", backupRoundTrip.schemaVersion)
                .put("rawEvidenceMatches", backupRoundTrip.rawEvidenceMatches)
                .put("prescriptionStateMatches", backupRoundTrip.prescriptionStateMatches)
                .put("candidateRowsMatch", backupRoundTrip.candidateRowsMatch)
                .put("foreignKeysClean", backupRoundTrip.foreignKeysClean)
                .put("passed", backupRoundTrip.passed),
        )
        .put(
            "architectureSubstrates",
            JSONObject()
                .put("denseCandidateV2Reference", "IMPLEMENTED_CURRENT_HISTORY_DEVICE_ACCEPTANCE")
                .put("adaptiveSparseCandidateV2", "IMPLEMENTED_CURRENT_HISTORY_DEVICE_ACCEPTANCE_SAME_MATHEMATICS_APPROXIMATION")
                .put("conditionalLaplaceCandidateV2", "IMPLEMENTED_CURRENT_HISTORY_DEVICE_ACCEPTANCE_APPROXIMATION_CHALLENGER")
                .put("denseSequentialTensor", "IMPLEMENTED_DEVICE_BENCHMARKED_GENERIC_SUBSTRATE;NOT_EQUIVALENT_TO_CURRENT_V2_BATCH_REFERENCE")
                .put("lowRank", "VIABILITY_SCREEN_DEVICE_BENCHMARKED_NOT_AUTOMATICALLY_PRODUCTION")
                .put("sigmaPoint", "IMPLEMENTED_AND_DEVICE_BENCHMARKED_ON_SHARED_DYNAMIC_FIXTURE_NOT_YET_CANDIDATE_V2_ADAPTER")
                .put("factorDependencyIndex", "IMPLEMENTED_MINIMUM_INVALIDATION_ABSTRACTION")
                .put("nativeCpu", "DEVICE_PROFILE_CANDIDATE_V2_FIRST;NO_KERNEL_PORT_WITHOUT_HOTSPOT_EVIDENCE")
                .put("vulkan", "NOT_RUN_UNTIL_CPU_PROFILE_JUSTIFIES_DATA_PARALLEL_KERNEL")
                .put("liteRtNpu", "NOT_CURRENTLY_JUSTIFIED_FOR_ARBITRARY_BAYESIAN_KERNELS;MODEL_GRAPH_REQUIRED"),
        )
        .put("safetyPassed", safetyPassed)
        .put("productAuthorityChanged", false)
        .put("normalWorkoutBehaviourChanged", false)
        .put("nBio7CStarted", false)
        .toString(2)
}

/**
 * One foreground Biological Developer action over installed Room14 history.
 *
 * Historical predictive results are DEVELOPMENT evidence. Dense Candidate-v2 is the same-math
 * reference; adaptive-sparse is a same-mathematics support-pruning challenger and conditional-Laplace
 * is the continuous approximation challenger. Every current-state comparison receives the exact same
 * deterministic frozen-v1 posterior. All persistence is SHADOW-only and immediately replay-checked.
 * Normal prescriptions and BENCHMARK_V0 are never updated by this runner.
 */
class NBioAdaptiveInferenceAcceptanceRunner(
    private val context: Context,
    private val database: MyMettleDatabase,
    private val historyReader: NBio7BRawHistoryReader = NBio7BRawHistoryReader(database),
    private val backupVerifier: NBio7BBackupRoundTripVerifier = NBio7BBackupRoundTripVerifier(context, database),
) {
    suspend fun run(
        onProgress: (NBio7BAcceptanceProgress) -> Unit = {},
    ): NBioAdaptiveInferenceAcceptanceReport {
        val wallStart = System.nanoTime()
        val rawBefore = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionBefore = NBio7BPrescriptionStateFingerprinter.capture(database)
        val userProfileId = database.inferenceDao().userProfileIds().singleOrNull()
            ?: error("Adaptive inference acceptance requires exactly one Native user profile.")
        val benchmarkBefore = database.inferenceDao().latestInferenceRun(userProfileId)?.id
        val javaBefore = usedJavaHeap()
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        val raw = historyReader.read()
        val currentAsKnown = HistoricalObservationRevisionSelector.currentAsOf(raw.revisions, Instant.MAX)
        val groups = raw.profiles.values.flatMap { descriptor ->
            currentAsKnown
                .filter { it.executionProfileVersionId == descriptor.semantics.executionProfileVersionId }
                .map { it.laterality }
                .distinct()
                .map { side -> descriptor to side }
        }
        val denseSolver = DynamicTrendDenseReferenceSolverAdapter()
        val sparseSolver = DynamicTrendAdaptiveSparseSolver()
        val laplaceSolver = DynamicTrendConditionalLaplaceSolverAdapter()
        val results = mutableListOf<NBioAdaptiveProfileResult>()

        groups.forEachIndexed { index, (descriptor, side) ->
            onProgress(
                NBio7BAcceptanceProgress(
                    index,
                    groups.size + 2,
                    "Adaptive inference · ${descriptor.label} · ${side.storageValue}",
                ),
            )
            val projection = DynamicResistanceEvidenceProjector.project(
                descriptor.semantics,
                side,
                currentAsKnown,
                DynamicResistanceV2Contract.evidencePolicy,
            )
            val bakeoff = DynamicTrendSolverHistoricalBakeoffCorrected(
                listOf(denseSolver, sparseSolver, laplaceSolver),
            ).evaluate(
                descriptor.semantics,
                side,
                raw.revisions,
            )
            val current = evaluateCurrentProfile(
                userProfileId = userProfileId,
                projection = projection,
                denseSolver = denseSolver,
                sparseSolver = sparseSolver,
                laplaceSolver = laplaceSolver,
            )
            results += NBioAdaptiveProfileResult(
                executionProfileVersionId = descriptor.semantics.executionProfileVersionId.value,
                label = descriptor.label,
                side = side.storageValue,
                eligibleObservationCount = projection.evidence.size,
                independentSessionCount = projection.independentSessionCount,
                chronologicalFitCount = bakeoff.chronologicalFitCount,
                bakeoff = bakeoff,
                denseVsSparsePosteriorFidelity = current.sparseFidelity,
                denseVsLaplacePosteriorFidelity = current.laplaceFidelity,
                densePersistReloadEquivalent = current.densePersist,
                sparsePersistReloadEquivalent = current.sparsePersist,
                laplacePersistReloadEquivalent = current.laplacePersist,
                denseReplayEquivalent = current.denseReplay,
                sparseReplayEquivalent = current.sparseReplay,
                laplaceReplayEquivalent = current.laplaceReplay,
                currentFitElapsedMillisDense = current.denseElapsed,
                currentFitElapsedMillisSparse = current.sparseElapsed,
                currentFitElapsedMillisLaplace = current.laplaceElapsed,
                limitations = current.limitations,
            )
        }

        onProgress(NBio7BAcceptanceProgress(groups.size, groups.size + 2, "Benchmarking inference solver substrates"))
        val solverSubstrateBenchmark = InferenceSolverSubstrateBenchmark.run()
        onProgress(NBio7BAcceptanceProgress(groups.size + 1, groups.size + 2, "Verifying Native backup and safety fingerprints"))
        val backup = backupVerifier.verify()
        val rawAfter = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionAfter = NBio7BPrescriptionStateFingerprinter.capture(database)
        val benchmarkAfter = database.inferenceDao().latestInferenceRun(userProfileId)?.id
        return NBioAdaptiveInferenceAcceptanceReport(
            generatedAt = Instant.now(),
            roomSchemaVersion = currentRoomSchemaVersion(),
            appIdentity = currentAppIdentity(),
            deviceIdentity = currentDeviceIdentity(),
            sequentialReuseAssessment = DynamicTrendSequentialReuseAssessment.assess(),
            rawFingerprintBefore = rawBefore,
            rawFingerprintAfter = rawAfter,
            prescriptionBefore = prescriptionBefore,
            prescriptionAfter = prescriptionAfter,
            benchmarkRunIdBefore = benchmarkBefore,
            benchmarkRunIdAfter = benchmarkAfter,
            profiles = results,
            solverSubstrateBenchmark = solverSubstrateBenchmark,
            backupRoundTrip = backup,
            javaHeapUsedBeforeBytes = javaBefore,
            javaHeapUsedAfterBytes = usedJavaHeap(),
            nativeHeapBeforeBytes = nativeBefore,
            nativeHeapAfterBytes = Debug.getNativeHeapAllocatedSize(),
            totalElapsedMillis = (System.nanoTime() - wallStart) / 1_000_000L,
        )
    }

    private suspend fun evaluateCurrentProfile(
        userProfileId: String,
        projection: DynamicResistanceEvidenceProjection,
        denseSolver: DynamicTrendCandidateV2Solver,
        sparseSolver: DynamicTrendCandidateV2Solver,
        laplaceSolver: DynamicTrendCandidateV2Solver,
    ): CurrentProfileEvaluation {
        if (projection.evidence.isEmpty()) {
            return CurrentProfileEvaluation(
                limitations = listOf("No current eligible dynamic-resistance evidence for this profile/side under evidence-policy v2."),
            )
        }
        val horizon = projection.evidence.maxOf { it.completedAt }
        val frozenV1 = runCatching { fitFrozenV1(projection, horizon) }.getOrElse {
            return CurrentProfileEvaluation(
                limitations = listOf("Current frozen Candidate-v1 proposal failed; Candidate-v2 current-state fidelity/persistence/replay are NOT_EVALUATED: ${it.message}"),
            )
        }
        val requestDense = DynamicCapabilityFitRequest(projection, horizon, denseSolver.modelConfig(CONFIG_CREATED_AT))
        val requestSparse = DynamicCapabilityFitRequest(projection, horizon, sparseSolver.modelConfig(CONFIG_CREATED_AT))
        val requestLaplace = DynamicCapabilityFitRequest(projection, horizon, laplaceSolver.modelConfig(CONFIG_CREATED_AT))

        var denseElapsed: Long? = null
        var sparseElapsed: Long? = null
        var laplaceElapsed: Long? = null
        var denseFit: DynamicTrendFrontierFit? = null
        var sparseFit: DynamicTrendFrontierFit? = null
        var laplaceFit: DynamicTrendFrontierFit? = null
        val limitations = mutableListOf<String>()
        runCatching {
            denseElapsed = measureTimeMillis { denseFit = denseSolver.fitFromFrozenV1(requestDense, frozenV1) }
        }.onFailure { limitations += "Dense-reference current fit failed: ${it.message}" }
        runCatching {
            sparseElapsed = measureTimeMillis { sparseFit = sparseSolver.fitFromFrozenV1(requestSparse, frozenV1) }
        }.onFailure { limitations += "Adaptive-sparse current fit failed: ${it.message}" }
        runCatching {
            laplaceElapsed = measureTimeMillis { laplaceFit = laplaceSolver.fitFromFrozenV1(requestLaplace, frozenV1) }
        }.onFailure { limitations += "Conditional-Laplace current fit failed: ${it.message}" }

        val dense = denseFit
        if (dense == null) {
            limitations += "Dense Candidate-v2 reference is unavailable; approximation fidelity/persistence/replay are NOT_EVALUATED rather than vacuous PASS."
            return CurrentProfileEvaluation(
                denseElapsed = denseElapsed,
                sparseElapsed = sparseElapsed,
                laplaceElapsed = laplaceElapsed,
                limitations = limitations,
            )
        }

        val reps = dense.referenceRepetitions
        val sparse = sparseFit
        val laplace = laplaceFit
        val densePersist = runCatching { persistReloadEquivalent(userProfileId, denseSolver, dense, reps) }
            .onFailure { limitations += "Dense persist/reload check failed: ${it.message}" }
            .getOrNull()
        val sparsePersist = sparse?.let { fit ->
            runCatching { persistReloadEquivalent(userProfileId, sparseSolver, fit, reps) }
                .onFailure { limitations += "Adaptive-sparse persist/reload check failed: ${it.message}" }
                .getOrNull()
        }
        val laplacePersist = laplace?.let { fit ->
            runCatching { persistReloadEquivalent(userProfileId, laplaceSolver, fit, reps) }
                .onFailure { limitations += "Conditional-Laplace persist/reload check failed: ${it.message}" }
                .getOrNull()
        }
        val denseReplay = runCatching { replayEquivalent(denseSolver, projection, horizon, dense, reps) }
            .onFailure { limitations += "Dense full replay check failed: ${it.message}" }
            .getOrNull()
        val sparseReplay = sparse?.let { fit ->
            runCatching { replayEquivalent(sparseSolver, projection, horizon, fit, reps) }
                .onFailure { limitations += "Adaptive-sparse full replay check failed: ${it.message}" }
                .getOrNull()
        }
        val laplaceReplay = laplace?.let { fit ->
            runCatching { replayEquivalent(laplaceSolver, projection, horizon, fit, reps) }
                .onFailure { limitations += "Conditional-Laplace full replay check failed: ${it.message}" }
                .getOrNull()
        }

        return CurrentProfileEvaluation(
            sparseFidelity = sparse?.let { runCatching { DynamicTrendPosteriorFidelity.compare(dense, it) }.getOrNull() },
            laplaceFidelity = laplace?.let { runCatching { DynamicTrendPosteriorFidelity.compare(dense, it) }.getOrNull() },
            densePersist = densePersist,
            sparsePersist = sparsePersist,
            laplacePersist = laplacePersist,
            denseReplay = denseReplay,
            sparseReplay = sparseReplay,
            laplaceReplay = laplaceReplay,
            denseElapsed = denseElapsed,
            sparseElapsed = sparseElapsed,
            laplaceElapsed = laplaceElapsed,
            limitations = limitations,
        )
    }

    private fun fitFrozenV1(
        projection: DynamicResistanceEvidenceProjection,
        horizon: Instant,
    ): DynamicStochasticFrontierFit {
        val model = DynamicStochasticFrontierModel(DynamicTrendFrontierV2.config.baseConfig)
        return model.fit(
            DynamicCapabilityFitRequest(
                projection = projection,
                inferenceHorizon = horizon,
                modelConfig = model.config.toModelConfig(CONFIG_CREATED_AT),
            ),
        )
    }

    private suspend fun persistReloadEquivalent(
        userProfileId: String,
        solver: DynamicTrendCandidateV2Solver,
        fit: DynamicTrendFrontierFit,
        repetitions: Double,
    ): Boolean {
        val repository = DynamicTrendCapabilityShadowRepository(database, solver)
        val before = solverPrediction(solver, fit, repetitions)
        val runId = repository.persist(userProfileId, fit)
        return try {
            val loaded = repository.load(runId)
            val after = solverPrediction(solver, loaded, repetitions)
            DynamicTrendCapabilityParameterCodec.encode(fit) == DynamicTrendCapabilityParameterCodec.encode(loaded) &&
                summariesEquivalent(before, after)
        } finally {
            repository.discard(runId)
        }
    }

    private fun replayEquivalent(
        solver: DynamicTrendCandidateV2Solver,
        projection: DynamicResistanceEvidenceProjection,
        horizon: Instant,
        original: DynamicTrendFrontierFit,
        repetitions: Double,
    ): Boolean {
        val frozenV1 = fitFrozenV1(projection, horizon)
        val replayed = solver.fitFromFrozenV1(
            DynamicCapabilityFitRequest(
                projection = projection,
                inferenceHorizon = horizon,
                modelConfig = solver.modelConfig(CONFIG_CREATED_AT),
            ),
            frozenV1,
        )
        return DynamicTrendCapabilityParameterCodec.encode(original) == DynamicTrendCapabilityParameterCodec.encode(replayed) &&
            summariesEquivalent(solverPrediction(solver, original, repetitions), solverPrediction(solver, replayed, repetitions))
    }

    private fun solverPrediction(
        solver: DynamicTrendCandidateV2Solver,
        fit: DynamicTrendFrontierFit,
        repetitions: Double,
    ): PosteriorEstimate = DynamicStochasticFrontierModel(solver.baseConfig)
        .predictFrontier(solver.projectToNextSession(fit), repetitions)

    private fun summariesEquivalent(left: PosteriorEstimate, right: PosteriorEstimate): Boolean {
        val a = left.summary ?: return right.summary == null
        val b = right.summary ?: return false
        return listOf(a.p05 to b.p05, a.p50 to b.p50, a.p95 to b.p95, a.posteriorVariance to b.posteriorVariance)
            .all { (x, y) -> kotlin.math.abs(x - y) <= 1e-10 * kotlin.math.max(1.0, kotlin.math.abs(x)) }
    }

    private fun currentRoomSchemaVersion(): Int = database.openHelper.readableDatabase
        .query("PRAGMA user_version")
        .use { cursor ->
            check(cursor.moveToFirst()) { "Room database did not report PRAGMA user_version." }
            cursor.getInt(0)
        }

    private fun usedJavaHeap(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun currentAppIdentity(): NBioAdaptiveAppIdentity = NBioAdaptiveAppIdentity(
        applicationId = BuildConfig.APPLICATION_ID,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildType = BuildConfig.BUILD_TYPE,
        debug = BuildConfig.DEBUG,
    )

    private fun currentDeviceIdentity(): NBioAdaptiveDeviceIdentity = NBioAdaptiveDeviceIdentity(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        device = Build.DEVICE,
        sdkInt = Build.VERSION.SDK_INT,
    )

    private data class CurrentProfileEvaluation(
        val sparseFidelity: DynamicTrendPosteriorFidelityResult? = null,
        val laplaceFidelity: DynamicTrendPosteriorFidelityResult? = null,
        val densePersist: Boolean? = null,
        val sparsePersist: Boolean? = null,
        val laplacePersist: Boolean? = null,
        val denseReplay: Boolean? = null,
        val sparseReplay: Boolean? = null,
        val laplaceReplay: Boolean? = null,
        val denseElapsed: Long? = null,
        val sparseElapsed: Long? = null,
        val laplaceElapsed: Long? = null,
        val limitations: List<String> = emptyList(),
    )

    companion object {
        private val CONFIG_CREATED_AT = Instant.parse("2026-08-31T00:00:00Z")
    }
}

private fun NBioAdaptiveAppIdentity.toJson(): JSONObject = JSONObject()
    .put("applicationId", applicationId)
    .put("versionName", versionName)
    .put("versionCode", versionCode)
    .put("buildType", buildType)
    .put("debug", debug)

private fun NBioAdaptiveDeviceIdentity.toJson(): JSONObject = JSONObject()
    .put("manufacturer", manufacturer)
    .put("model", model)
    .put("device", device)
    .put("sdkInt", sdkInt)

private fun CandidateV2SequentialReuseAssessment.toJson(): JSONObject = JSONObject()
    .put("version", version)
    .put("stateReanchorIdentity", stateReanchorIdentity)
    .put("currentReferenceUsesMovingRecentSessionWindow", currentReferenceUsesMovingRecentSessionWindow)
    .put("oldestLikelihoodMayNeedRemoval", oldestLikelihoodMayNeedRemoval)
    .put("referenceRepCoordinateMayChange", referenceRepCoordinateMayChange)
    .put("nuisanceLearningRegimeMayChange", nuisanceLearningRegimeMayChange)
    .put("frozenV1NumericalGridMayChange", frozenV1NumericalGridMayChange)
    .put("exactFactorRemovalStatePersisted", exactFactorRemovalStatePersisted)
    .put("verdict", verdict.storageValue)
    .put("conclusion", conclusion)

private fun dev.kian.mymettle.domain.inference.InferenceSolverIdentity.toJson(): JSONObject = JSONObject()
    .put("family", solverFamily.storageValue)
    .put("semanticVersion", semanticVersion)
    .put("computeBackend", computeBackend.storageValue)
    .put("deterministicReplay", deterministicReplay)
    .put("approximationDefinition", approximationDefinition)

private fun NBioAdaptiveProfileResult.toJson(): JSONObject = JSONObject()
    .put("executionProfileVersionId", executionProfileVersionId)
    .put("label", label)
    .put("side", side)
    .put("eligibleObservationCount", eligibleObservationCount)
    .put("independentSessionCount", independentSessionCount)
    .put("chronologicalFitCount", chronologicalFitCount)
    .put("retrospectiveProtocolVersion", bakeoff.protocolVersion)
    .put(
        "currentFitRuntimeMillis",
        JSONObject()
            .put("denseReference", currentFitElapsedMillisDense ?: JSONObject.NULL)
            .put("adaptiveSparse", currentFitElapsedMillisSparse ?: JSONObject.NULL)
            .put("conditionalLaplace", currentFitElapsedMillisLaplace ?: JSONObject.NULL),
    )
    .put(
        "persistenceReplay",
        JSONObject()
            .put("densePersistReloadEquivalent", densePersistReloadEquivalent ?: JSONObject.NULL)
            .put("sparsePersistReloadEquivalent", sparsePersistReloadEquivalent ?: JSONObject.NULL)
            .put("laplacePersistReloadEquivalent", laplacePersistReloadEquivalent ?: JSONObject.NULL)
            .put("denseFullReplayEquivalent", denseReplayEquivalent ?: JSONObject.NULL)
            .put("sparseFullReplayEquivalent", sparseReplayEquivalent ?: JSONObject.NULL)
            .put("laplaceFullReplayEquivalent", laplaceReplayEquivalent ?: JSONObject.NULL),
    )
    .put("v1", bakeoff.v1PredictiveMetrics.toJson(bakeoff.v1ValidationSummary, bakeoff.v1Verdict.storageValue, bakeoff.v1FitElapsedMillis))
    .put(
        "candidateV2Solvers",
        JSONArray(
            bakeoff.candidates.map { candidate ->
                candidate.predictiveMetrics
                    .toJson(
                        candidate.validationSummary,
                        candidate.developmentComparisonAgainstV1.verdict.storageValue,
                        candidate.extensionWallElapsedMillis,
                    )
                    .put("solver", candidate.solverIdentity.toJson())
                    .put("absoluteValidationVerdict", candidate.absoluteValidationVerdict.storageValue)
                    .put(
                        "developmentComparison",
                        JSONObject()
                            .put("crpsRelativeImprovement", candidate.developmentComparisonAgainstV1.crpsRelativeImprovement ?: JSONObject.NULL)
                            .put("absoluteSignedBiasRelativeImprovement", candidate.developmentComparisonAgainstV1.absoluteSignedBiasRelativeImprovement ?: JSONObject.NULL)
                            .put("predictiveLogWidthRatio", candidate.developmentComparisonAgainstV1.predictiveLogWidthRatio ?: JSONObject.NULL)
                            .put("medianMaeRatio", candidate.developmentComparisonAgainstV1.medianMaeRatio ?: JSONObject.NULL),
                    )
            },
        ),
    )
    .put(
        "currentPosteriorFidelity",
        JSONObject()
            .put("denseVsAdaptiveSparse", denseVsSparsePosteriorFidelity?.toJson() ?: JSONObject.NULL)
            .put("denseVsConditionalLaplace", denseVsLaplacePosteriorFidelity?.toJson() ?: JSONObject.NULL),
    )
    .put("limitations", JSONArray(limitations))

private fun dev.kian.mymettle.engine.inference.DynamicTrendSolverPredictiveMetrics.toJson(
    validation: dev.kian.mymettle.domain.inference.DynamicCapabilityValidationSummary,
    verdict: String,
    fitRuntimeMillis: Long,
): JSONObject = JSONObject()
    .put("verdict", verdict)
    .put("evaluableCount", distribution.evaluableCount)
    .put("modelFailureRate", distribution.modelFailureRate)
    .put("meanSignedLogResidual", distribution.meanSignedLogResidual ?: JSONObject.NULL)
    .put("medianSignedLogResidual", distribution.medianSignedLogResidual ?: JSONObject.NULL)
    .put("positiveResidualProportion", distribution.positiveResidualProportion ?: JSONObject.NULL)
    .put("coverage", distribution.coverage ?: JSONObject.NULL)
    .put("pitHighRate", distribution.pitHighRate ?: JSONObject.NULL)
    .put(
        "pitBins",
        JSONObject()
            .put("low", validation.candidatePitCalibration.lowCount)
            .put("middle", validation.candidatePitCalibration.middleCount)
            .put("high", validation.candidatePitCalibration.highCount),
    )
    .put("catastrophicContradictionRate", distribution.catastrophicContradictionRate ?: JSONObject.NULL)
    .put("meanCrpsLogResistance", distribution.meanCrpsLogResistance ?: JSONObject.NULL)
    .put("meanWeightedIntervalScoreLogResistance", meanWeightedIntervalScoreLogResistance ?: JSONObject.NULL)
    .put("medianWeightedIntervalScoreLogResistance", medianWeightedIntervalScoreLogResistance ?: JSONObject.NULL)
    .put("meanPredictiveLogWidth", distribution.meanPredictiveLogWidth ?: JSONObject.NULL)
    .put("demonstrationMedianMaeKg", distribution.demonstrationMedianMaeKg ?: JSONObject.NULL)
    .put("meanLogPredictiveDensity", distribution.meanLogPredictiveDensity ?: JSONObject.NULL)
    .put("fitRuntimeMillis", fitRuntimeMillis)

private fun DynamicTrendPosteriorFidelityResult.toJson(): JSONObject = JSONObject()
    .put("referenceSolver", referenceSolver)
    .put("candidateSolver", candidateSolver)
    .put("referenceNodeCount", referenceNodeCount)
    .put("candidateNodeCount", candidateNodeCount)
    .put("trendPositiveProbabilityAbsoluteError", trendPositiveProbabilityAbsoluteError)
    .put("nextFrontierMedianRelativeError", nextFrontierMedianRelativeError)
    .put("maxStandardisedMarginalWasserstein1", maxStandardisedMarginalWasserstein1)
    .put("maxCovarianceCorrelationScaleError", maxCovarianceCorrelationScaleError ?: JSONObject.NULL)
    .put(
        "marginals",
        JSONArray(
            marginals.map { marginal ->
                JSONObject()
                    .put("parameter", marginal.parameter)
                    .put("referenceP05", marginal.referenceP05)
                    .put("referenceP50", marginal.referenceP50)
                    .put("referenceP95", marginal.referenceP95)
                    .put("candidateP05", marginal.candidateP05)
                    .put("candidateP50", marginal.candidateP50)
                    .put("candidateP95", marginal.candidateP95)
                    .put("referenceVariance", marginal.referenceVariance)
                    .put("candidateVariance", marginal.candidateVariance)
                    .put("quantileWasserstein1", marginal.quantileWasserstein1)
                    .put("standardisedQuantileWasserstein1", marginal.standardisedQuantileWasserstein1)
            },
        ),
    )

private fun InferenceSolverSubstrateBenchmarkResult.toJson(): JSONObject = JSONObject()
    .put("benchmarkVersion", benchmarkVersion)
    .put("evidenceClass", "SOLVER_SUBSTRATE_SYNTHETIC_NOT_CANDIDATE_V2_VALIDATION")
    .put("mathematicalModelIdentity", mathematicalModelIdentity)
    .put("gridNodeCount", gridNodeCount)
    .put("observationCount", observationCount)
    .put("denseSequentialRuntime", denseRuntime.toJson())
    .put("adaptiveSparseRuntime", sparseRuntime.toJson())
    .put("sigmaPointRuntime", sigmaPointRuntime.toJson())
    .put("denseIncrementalReplayEquivalent", denseIncrementalReplayEquivalent)
    .put("sparseRetainedNodeCount", sparseRetainedNodeCount)
    .put("sparseLevelQuantileMaxAbsoluteError", sparseLevelQuantileMaxAbsoluteError)
    .put("sparseDriftQuantileMaxAbsoluteError", sparseDriftQuantileMaxAbsoluteError)
    .put("sparseMeanMaxAbsoluteError", sparseMeanMaxAbsoluteError)
    .put("sparseCovarianceMaxAbsoluteError", sparseCovarianceMaxAbsoluteError)
    .put("sigmaPointMeanMaxAbsoluteError", sigmaPointMeanMaxAbsoluteError)
    .put("sigmaPointCovarianceMaxAbsoluteError", sigmaPointCovarianceMaxAbsoluteError)
    .put("denseEffectiveNodeCount", denseEffectiveNodeCount)
    .put("sparseEffectiveNodeCount", sparseEffectiveNodeCount)
    .put("sigmaPointEffectiveNodeCount", sigmaPointEffectiveNodeCount)
    .put("lowRankScreens", JSONArray(lowRankScreens.map { it.toJson() }))

private fun InferenceSolverRuntimeSummary.toJson(): JSONObject = JSONObject()
    .put("medianNanos", medianNanos)
    .put("p95Nanos", p95Nanos)
    .put("repetitions", repetitions)

private fun LowRankPosteriorScreenResult.toJson(): JSONObject = JSONObject()
    .put("rows", rows)
    .put("columns", columns)
    .put("requestedRank", requestedRank)
    .put("compressionRatio", compressionRatio)
    .put("l1ProbabilityError", l1ProbabilityError)
    .put("maxRowMarginalError", maxRowMarginalError)
    .put("maxColumnMarginalError", maxColumnMarginalError)
    .put("klDenseToApprox", klDenseToApprox)
    .put("useful", useful)