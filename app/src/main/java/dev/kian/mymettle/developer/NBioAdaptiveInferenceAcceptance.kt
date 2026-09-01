package dev.kian.mymettle.developer

import android.content.Context
import android.os.Build
import android.os.Debug
import dev.kian.mymettle.BuildConfig
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.engine.inference.CandidateV2SequentialReuseAssessment
import dev.kian.mymettle.engine.inference.DynamicHistoricalAvailabilityV3
import dev.kian.mymettle.engine.inference.DynamicTrendCandidateV2Solver
import dev.kian.mymettle.engine.inference.DynamicTrendPosteriorFidelity
import dev.kian.mymettle.engine.inference.DynamicTrendPosteriorFidelityResult
import dev.kian.mymettle.engine.inference.DynamicTrendSequentialReuseAssessment
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
import kotlin.math.abs
import kotlin.math.ln
import kotlin.system.measureTimeMillis
import org.json.JSONArray
import org.json.JSONObject

private const val DENSE_REFERENCE_SAMPLE_LIMIT = 3

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
    val workloadOrder: Int,
    val executionProfileVersionId: String,
    val label: String,
    val side: String,
    val eligibleObservationCount: Int,
    val independentSessionCount: Int,
    val chronologicalFitCount: Int,
    val denseReferenceSampled: Boolean,
    val denseReferenceSelectionReason: String?,
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
    val timing: NBioAdaptiveProfileTiming,
    val thermalBefore: NBioAdaptiveThermalSnapshot,
    val thermalAfter: NBioAdaptiveThermalSnapshot,
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
    val runtimeInfo: NBioAdaptiveRuntimeInfo,
    val thermalTimeline: List<NBioAdaptiveThermalSnapshot>,
    val initialPreparationElapsedMillis: Long,
    val solverSubstrateBenchmarkElapsedMillis: Long,
    val backupRoundTripElapsedMillis: Long,
    val postSafetyFingerprintElapsedMillis: Long,
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
    val scientificEvaluationNonVacuous get() = profiles.any { it.eligibleObservationCount > 0 }

    private fun performanceJson(): JSONObject {
        val retrospectiveV1Fit = profiles.sumOf { it.bakeoff.v1FitElapsedMillis }
        val retrospectiveCandidateFit = profiles.sumOf { profile -> profile.bakeoff.candidates.sumOf { it.extensionWallElapsedMillis } }
        val retrospectiveScoring = profiles.sumOf { profile ->
            profile.bakeoff.v1PredictiveScoringElapsedMillis +
                profile.bakeoff.candidates.sumOf { it.predictiveScoringElapsedMillis }
        }
        val historicalWall = profiles.sumOf { it.timing.historicalBakeoffWallElapsedMillis }
        val currentWall = profiles.sumOf { it.timing.currentEvaluationWallElapsedMillis }
        val currentFit = profiles.sumOf { profile ->
            listOfNotNull(
                profile.currentFitElapsedMillisDense,
                profile.currentFitElapsedMillisSparse,
                profile.currentFitElapsedMillisLaplace,
            ).sum()
        }
        val denseCurrentFit = profiles.sumOf { it.currentFitElapsedMillisDense ?: 0L }
        val persistenceReload = profiles.sumOf { it.timing.persistenceReloadTotalMillis }
        val replay = profiles.sumOf { it.timing.replayTotalMillis }
        val fidelity = profiles.sumOf { it.timing.fidelityTotalMillis }
        val namedTopLevel = initialPreparationElapsedMillis + historicalWall + currentWall +
            solverSubstrateBenchmarkElapsedMillis + backupRoundTripElapsedMillis + postSafetyFingerprintElapsedMillis
        val other = (totalElapsedMillis - namedTopLevel).coerceAtLeast(0L)
        return JSONObject()
            .put("totalElapsedMillis", totalElapsedMillis)
            .put("initialPreparationElapsedMillis", initialPreparationElapsedMillis)
            .put("historicalBakeoffWallElapsedMillis", historicalWall)
            .put("historicalFitRuntimeMillis", JSONObject()
                .put("frozenV1", retrospectiveV1Fit)
                .put("candidateV2Extensions", retrospectiveCandidateFit))
            .put("historicalPredictiveScoringElapsedMillis", retrospectiveScoring)
            .put("currentEvaluationWallElapsedMillis", currentWall)
            .put("currentFitRuntimeMillis", currentFit)
            .put("denseReferenceCurrentFitElapsedMillis", denseCurrentFit)
            .put("persistenceReloadElapsedMillis", persistenceReload)
            .put("deterministicReplayElapsedMillis", replay)
            .put("posteriorFidelityElapsedMillis", fidelity)
            .put("solverSubstrateBenchmarkElapsedMillis", solverSubstrateBenchmarkElapsedMillis)
            .put("nativeBackupRoundTripElapsedMillis", backupRoundTripElapsedMillis)
            .put("postSafetyFingerprintElapsedMillis", postSafetyFingerprintElapsedMillis)
            .put("otherAcceptanceOverheadMillis", other)
            .put("interpretation", "Physical wall time is diagnostic only; use run order plus thermal snapshots before comparing solver runtimes.")
    }

    fun toJson(): String = JSONObject()
        .put("format", "my-mettle-n-bio-adaptive-inference-acceptance")
        .put("formatVersion", 7)
        .put("generatedAt", generatedAt.toString())
        .put("mission", "N-BIO-7B.X_ADAPTIVE_INFERENCE_ARCHITECTURE_CONSOLIDATION")
        .put("evidenceClass", "RETROSPECTIVE_DEVELOPMENT")
        .put("freshConfirmationRequired", true)
        .put("roomSchemaVersion", roomSchemaVersion)
        .put("app", appIdentity.toJson())
        .put("device", deviceIdentity.toJson())
        .put("contextConsumption", NBioCorrectedCandidateV2Bundle.mathematicalConfig.contextConsumption)
        .put("candidateV1Status", "FROZEN_REJECTED_EMPIRICAL_CALIBRATION")
        .put("candidateV2Status", "DEVELOPMENT_CANDIDATE_NOT_PRODUCT_AUTHORITY")
        .put(
            "evidencePolicy",
            JSONObject()
                .put("semanticVersion", NBioCorrectedCandidateV2Bundle.evidencePolicy.semanticVersion)
                .put("identity", NBioCorrectedCandidateV2Bundle.evidencePolicy.identity)
                .put("historicalAvailabilityPolicyId", DynamicHistoricalAvailabilityV3.POLICY_ID)
                .put(
                    "eligibleHistoricalUnknownSources",
                    JSONArray(NBioCorrectedCandidateV2Bundle.evidencePolicy.eligibleHistoricalUnknownSources.sorted()),
                ),
        )
        .put(
            "candidateV2MathematicalModel",
            JSONObject()
                .put("family", NBioCorrectedCandidateV2Bundle.mathematicalModelIdentity.family)
                .put("semanticVersion", NBioCorrectedCandidateV2Bundle.mathematicalModelIdentity.semanticVersion)
                .put("definition", NBioCorrectedCandidateV2Bundle.mathematicalModelIdentity.definition),
        )
        .put(
            "candidateV2Solvers",
            JSONArray(
                listOf(
                    NBioCorrectedCandidateV2Bundle.denseSolver().solverIdentity.toJson(),
                    NBioCorrectedCandidateV2Bundle.sparseSolver().solverIdentity.toJson(),
                    NBioCorrectedCandidateV2Bundle.laplaceSolver().solverIdentity.toJson(),
                ),
            ),
        )
        .put(
            "denseReferenceSampling",
            JSONObject()
                .put("strategy", "REPRESENTATIVE_CURRENT_POSTERIOR_STABLE_PROGRESSING_NUMERICAL_STRESS_V2")
                .put("maximumSampledProfileSides", DENSE_REFERENCE_SAMPLE_LIMIT)
                .put("selectionHeuristic", "diagnostic-only raw session log-resistance trajectory; prioritises stable, strongest progressing, and known/practical numerical-stress history; not used by model likelihood or priors")
                .put("historicalDenseScoring", false)
                .put("reason", "Dense Candidate-v2 remains the high-fidelity oracle, but full-history dense scoring is intentionally bounded after physical alpha25 exposed multi-minute single-profile runtime. Full chronology uses same-mathematics adaptive-sparse plus conditional-Laplace; dense fidelity is sampled on the richest current profile/side posteriors."),
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
        .put("runtime", runtimeInfo.toJson())
        .put("thermalTimeline", JSONArray(thermalTimeline.map { it.toJson() }))
        .put("performance", performanceJson())
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
                .put("denseCandidateV2Reference", "IMPLEMENTED_BOUNDED_CURRENT_POSTERIOR_ORACLE;FULL_HISTORICAL_DENSE_SCORING_DISABLED_AFTER_DEVICE_RUNTIME_EVIDENCE")
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
        .put("scientificEvaluationNonVacuous", scientificEvaluationNonVacuous)
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
    private val historyReader: NBio7BRawHistoryReader = NBio7BRawHistoryReader(
        database,
        DynamicHistoricalAvailabilityV3::resolve,
    ),
    private val backupVerifier: NBio7BBackupRoundTripVerifier = NBio7BBackupRoundTripVerifier(context, database),
) {
    suspend fun run(
        onProgress: (NBio7BAcceptanceProgress) -> Unit = {},
    ): NBioAdaptiveInferenceAcceptanceReport {
        val wallStart = System.nanoTime()
        val runtimeTelemetry = NBioAdaptiveRuntimeTelemetry(context)
        val runtimeInfo = runtimeTelemetry.runtimeInfo()
        val thermalTimeline = mutableListOf<NBioAdaptiveThermalSnapshot>()
        var thermalSequence = 0
        fun thermal(stage: String): NBioAdaptiveThermalSnapshot =
            runtimeTelemetry.snapshot(thermalSequence++, stage).also(thermalTimeline::add)

        thermal("run_start")
        val initialStart = System.nanoTime()
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
        val denseSolver = NBioCorrectedCandidateV2Bundle.denseSolver()
        val sparseSolver = NBioCorrectedCandidateV2Bundle.sparseSolver()
        val laplaceSolver = NBioCorrectedCandidateV2Bundle.laplaceSolver()
        val projectedGroups = groups.map { (descriptor, side) ->
            Triple(
                descriptor,
                side,
                DynamicResistanceEvidenceProjector.project(
                    descriptor.semantics,
                    side,
                    currentAsKnown,
                    NBioCorrectedCandidateV2Bundle.evidencePolicy,
                ),
            )
        }
        val projectedEligibleCount = projectedGroups.sumOf { it.third.evidence.size }
        if (currentAsKnown.isNotEmpty() && projectedGroups.isNotEmpty() && projectedEligibleCount == 0) {
            val sourceCounts = currentAsKnown.groupingBy { it.observationSource }.eachCount().toSortedMap()
            val exclusionCounts = projectedGroups
                .flatMap { it.third.exclusions }
                .groupingBy { it.reason.storageValue }
                .eachCount()
                .toSortedMap()
            error(
                "Adaptive inference scientific evaluation is vacuous: ${currentAsKnown.size} current raw observations " +
                    "across ${projectedGroups.size} profile/side groups projected to zero eligible evidence under " +
                    "${NBioCorrectedCandidateV2Bundle.evidencePolicy.semanticVersion}; " +
                    "sources=$sourceCounts exclusions=$exclusionCounts",
            )
        }
        val denseReferenceReasons = selectDenseReferenceReasons(projectedGroups)
        val denseReferenceKeys = denseReferenceReasons.keys
        // Run inexpensive/full-history challengers first so physical progress is visible quickly;
        // representative bounded dense-oracle samples remain at the tail and run order is exported.
        val orderedGroups = projectedGroups.sortedBy { group ->
            if (profileKey(group.first, group.second) in denseReferenceKeys) 1 else 0
        }
        val initialPreparationElapsedMillis = elapsedMillis(initialStart)
        thermal("initial_preparation_complete")

        val results = mutableListOf<NBioAdaptiveProfileResult>()
        orderedGroups.forEachIndexed { index, (descriptor, side, projection) ->
            val key = profileKey(descriptor, side)
            val includeDenseReference = key in denseReferenceKeys
            val thermalBefore = thermal("profile_${index}_start:$key")
            onProgress(
                NBio7BAcceptanceProgress(
                    index,
                    orderedGroups.size + 2,
                    "Adaptive inference · ${descriptor.label} · ${side.storageValue} · full-history sparse + Laplace",
                ),
            )
            val historicalStart = System.nanoTime()
            val bakeoff = NBioCorrectedCandidateV2Bundle.evaluateHistorical(
                solvers = listOf(sparseSolver, laplaceSolver),
                profile = descriptor.semantics,
                side = side,
                revisions = raw.revisions,
            )
            val historicalWall = elapsedMillis(historicalStart)
            onProgress(
                NBio7BAcceptanceProgress(
                    index,
                    orderedGroups.size + 2,
                    if (includeDenseReference) {
                        "Adaptive inference · ${descriptor.label} · ${side.storageValue} · current posterior + representative dense oracle"
                    } else {
                        "Adaptive inference · ${descriptor.label} · ${side.storageValue} · current posterior checks"
                    },
                ),
            )
            val currentStart = System.nanoTime()
            val current = evaluateCurrentProfile(
                userProfileId = userProfileId,
                projection = projection,
                denseSolver = denseSolver,
                sparseSolver = sparseSolver,
                laplaceSolver = laplaceSolver,
                includeDenseReference = includeDenseReference,
            )
            val currentWall = elapsedMillis(currentStart)
            val thermalAfter = thermal("profile_${index}_complete:$key")
            results += NBioAdaptiveProfileResult(
                workloadOrder = index,
                executionProfileVersionId = descriptor.semantics.executionProfileVersionId.value,
                label = descriptor.label,
                side = side.storageValue,
                eligibleObservationCount = projection.evidence.size,
                independentSessionCount = projection.independentSessionCount,
                chronologicalFitCount = bakeoff.chronologicalFitCount,
                denseReferenceSampled = includeDenseReference,
                denseReferenceSelectionReason = denseReferenceReasons[key],
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
                timing = NBioAdaptiveProfileTiming(
                    historicalBakeoffWallElapsedMillis = historicalWall,
                    currentEvaluationWallElapsedMillis = currentWall,
                    frozenV1FitElapsedMillis = current.frozenV1FitElapsedMillis,
                    replayFrozenV1FitElapsedMillis = current.replayFrozenV1FitElapsedMillis,
                    densePersistenceReloadElapsedMillis = current.densePersistElapsedMillis,
                    sparsePersistenceReloadElapsedMillis = current.sparsePersistElapsedMillis,
                    laplacePersistenceReloadElapsedMillis = current.laplacePersistElapsedMillis,
                    denseReplayElapsedMillis = current.denseReplayElapsedMillis,
                    sparseReplayElapsedMillis = current.sparseReplayElapsedMillis,
                    laplaceReplayElapsedMillis = current.laplaceReplayElapsedMillis,
                    sparseFidelityElapsedMillis = current.sparseFidelityElapsedMillis,
                    laplaceFidelityElapsedMillis = current.laplaceFidelityElapsedMillis,
                ),
                thermalBefore = thermalBefore,
                thermalAfter = thermalAfter,
                limitations = current.limitations,
            )
        }

        onProgress(NBio7BAcceptanceProgress(orderedGroups.size, orderedGroups.size + 2, "Benchmarking inference solver substrates"))
        thermal("solver_substrate_start")
        val substrateStart = System.nanoTime()
        val solverSubstrateBenchmark = InferenceSolverSubstrateBenchmark.run()
        val solverSubstrateBenchmarkElapsedMillis = elapsedMillis(substrateStart)
        thermal("solver_substrate_complete")

        onProgress(NBio7BAcceptanceProgress(orderedGroups.size + 1, orderedGroups.size + 2, "Verifying Native backup and safety fingerprints"))
        thermal("native_backup_round_trip_start")
        val backupStart = System.nanoTime()
        val backup = backupVerifier.verify()
        val backupRoundTripElapsedMillis = elapsedMillis(backupStart)
        thermal("native_backup_round_trip_complete")

        val postSafetyStart = System.nanoTime()
        val rawAfter = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionAfter = NBio7BPrescriptionStateFingerprinter.capture(database)
        val benchmarkAfter = database.inferenceDao().latestInferenceRun(userProfileId)?.id
        val postSafetyFingerprintElapsedMillis = elapsedMillis(postSafetyStart)
        thermal("post_safety_fingerprints_complete")
        thermal("run_complete")

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
            runtimeInfo = runtimeInfo,
            thermalTimeline = thermalTimeline,
            initialPreparationElapsedMillis = initialPreparationElapsedMillis,
            solverSubstrateBenchmarkElapsedMillis = solverSubstrateBenchmarkElapsedMillis,
            backupRoundTripElapsedMillis = backupRoundTripElapsedMillis,
            postSafetyFingerprintElapsedMillis = postSafetyFingerprintElapsedMillis,
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
        includeDenseReference: Boolean,
    ): CurrentProfileEvaluation {
        if (projection.evidence.isEmpty()) {
            return CurrentProfileEvaluation(
                limitations = listOf("No current eligible dynamic-resistance evidence for this profile/side under corrected evidence-policy v3."),
            )
        }
        val limitations = mutableListOf<String>()
        val horizon = projection.evidence.maxOf { it.completedAt }
        val frozenOutcome = timed { fitFrozenV1(projection, horizon) }
        val frozenV1 = frozenOutcome.value ?: return CurrentProfileEvaluation(
            frozenV1FitElapsedMillis = frozenOutcome.elapsedMillis,
            limitations = listOf(
                "Current frozen Candidate-v1 proposal failed; Candidate-v2 current-state fidelity/persistence/replay are NOT_EVALUATED: ${frozenOutcome.failure?.message}",
            ),
        )
        val requestSparse = DynamicCapabilityFitRequest(projection, horizon, sparseSolver.modelConfig(CONFIG_CREATED_AT))
        val requestLaplace = DynamicCapabilityFitRequest(projection, horizon, laplaceSolver.modelConfig(CONFIG_CREATED_AT))
        val requestDense = if (includeDenseReference) {
            DynamicCapabilityFitRequest(projection, horizon, denseSolver.modelConfig(CONFIG_CREATED_AT))
        } else null

        val sparseOutcome = timed { sparseSolver.fitFromFrozenV1(requestSparse, frozenV1) }
        sparseOutcome.failure?.let { limitations += "Adaptive-sparse current fit failed: ${it.message}" }
        val laplaceOutcome = timed { laplaceSolver.fitFromFrozenV1(requestLaplace, frozenV1) }
        laplaceOutcome.failure?.let { limitations += "Conditional-Laplace current fit failed: ${it.message}" }
        val denseOutcome = if (includeDenseReference) {
            timed { denseSolver.fitFromFrozenV1(requireNotNull(requestDense), frozenV1) }
                .also { outcome -> outcome.failure?.let { limitations += "Dense-reference sampled current fit failed: ${it.message}" } }
        } else null
        if (!includeDenseReference) {
            limitations += "Dense reference intentionally not run for this profile/side; physical acceptance samples only $DENSE_REFERENCE_SAMPLE_LIMIT representative trend-enabled current posteriors."
        }

        val dense = denseOutcome?.value
        val sparse = sparseOutcome.value
        val laplace = laplaceOutcome.value
        val reps = (dense ?: sparse ?: laplace)?.referenceRepetitions ?: frozenV1.referenceRepetitions

        val densePersistOutcome = dense?.let { fit -> timedSuspend { persistReloadEquivalent(userProfileId, denseSolver, fit, reps) } }
        densePersistOutcome?.failure?.let { limitations += "Dense persist/reload check failed: ${it.message}" }
        val sparsePersistOutcome = sparse?.let { fit -> timedSuspend { persistReloadEquivalent(userProfileId, sparseSolver, fit, reps) } }
        sparsePersistOutcome?.failure?.let { limitations += "Adaptive-sparse persist/reload check failed: ${it.message}" }
        val laplacePersistOutcome = laplace?.let { fit -> timedSuspend { persistReloadEquivalent(userProfileId, laplaceSolver, fit, reps) } }
        laplacePersistOutcome?.failure?.let { limitations += "Conditional-Laplace persist/reload check failed: ${it.message}" }

        val replayFrozenOutcome = timed { fitFrozenV1(projection, horizon) }
        replayFrozenOutcome.failure?.let { limitations += "Shared Candidate-v1 replay proposal failed: ${it.message}" }
        val replayFrozenV1 = replayFrozenOutcome.value
        val denseReplayOutcome = if (dense != null && replayFrozenV1 != null) {
            timed { replayEquivalent(denseSolver, projection, horizon, dense, reps, replayFrozenV1) }
                .also { outcome -> outcome.failure?.let { limitations += "Dense full replay check failed: ${it.message}" } }
        } else null
        val sparseReplayOutcome = if (sparse != null && replayFrozenV1 != null) {
            timed { replayEquivalent(sparseSolver, projection, horizon, sparse, reps, replayFrozenV1) }
                .also { outcome -> outcome.failure?.let { limitations += "Adaptive-sparse full replay check failed: ${it.message}" } }
        } else null
        val laplaceReplayOutcome = if (laplace != null && replayFrozenV1 != null) {
            timed { replayEquivalent(laplaceSolver, projection, horizon, laplace, reps, replayFrozenV1) }
                .also { outcome -> outcome.failure?.let { limitations += "Conditional-Laplace full replay check failed: ${it.message}" } }
        } else null

        if (includeDenseReference && dense == null) {
            limitations += "Selected dense oracle fit is unavailable; approximation fidelity is NOT_EVALUATED rather than vacuous PASS."
        }
        val sparseFidelityOutcome = if (dense != null && sparse != null) {
            timed { DynamicTrendPosteriorFidelity.compare(dense, sparse) }
                .also { outcome -> outcome.failure?.let { limitations += "Adaptive-sparse posterior-fidelity comparison failed: ${it.message}" } }
        } else null
        val laplaceFidelityOutcome = if (dense != null && laplace != null) {
            timed { DynamicTrendPosteriorFidelity.compare(dense, laplace) }
                .also { outcome -> outcome.failure?.let { limitations += "Conditional-Laplace posterior-fidelity comparison failed: ${it.message}" } }
        } else null

        return CurrentProfileEvaluation(
            sparseFidelity = sparseFidelityOutcome?.value,
            laplaceFidelity = laplaceFidelityOutcome?.value,
            densePersist = densePersistOutcome?.value,
            sparsePersist = sparsePersistOutcome?.value,
            laplacePersist = laplacePersistOutcome?.value,
            denseReplay = denseReplayOutcome?.value,
            sparseReplay = sparseReplayOutcome?.value,
            laplaceReplay = laplaceReplayOutcome?.value,
            denseElapsed = denseOutcome?.elapsedMillis,
            sparseElapsed = sparseOutcome.elapsedMillis,
            laplaceElapsed = laplaceOutcome.elapsedMillis,
            frozenV1FitElapsedMillis = frozenOutcome.elapsedMillis,
            replayFrozenV1FitElapsedMillis = replayFrozenOutcome.elapsedMillis,
            densePersistElapsedMillis = densePersistOutcome?.elapsedMillis,
            sparsePersistElapsedMillis = sparsePersistOutcome?.elapsedMillis,
            laplacePersistElapsedMillis = laplacePersistOutcome?.elapsedMillis,
            denseReplayElapsedMillis = denseReplayOutcome?.elapsedMillis,
            sparseReplayElapsedMillis = sparseReplayOutcome?.elapsedMillis,
            laplaceReplayElapsedMillis = laplaceReplayOutcome?.elapsedMillis,
            sparseFidelityElapsedMillis = sparseFidelityOutcome?.elapsedMillis,
            laplaceFidelityElapsedMillis = laplaceFidelityOutcome?.elapsedMillis,
            limitations = limitations,
        )
    }

    private fun selectDenseReferenceReasons(
        projectedGroups: List<Triple<NBio7BProfileDescriptor, dev.kian.mymettle.domain.performance.Laterality, DynamicResistanceEvidenceProjection>>,
    ): Map<String, String> {
        val eligible = projectedGroups.filter {
            it.third.independentSessionCount >= NBioCorrectedCandidateV2Bundle.mathematicalConfig.trendMinimumIndependentSessionsToLearn &&
                it.third.evidence.isNotEmpty()
        }
        if (eligible.isEmpty()) return emptyMap()
        val selected = linkedMapOf<String, String>()
        fun add(group: Triple<NBio7BProfileDescriptor, dev.kian.mymettle.domain.performance.Laterality, DynamicResistanceEvidenceProjection>?, reason: String) {
            if (group == null) return
            val key = profileKey(group.first, group.second)
            val existing = selected[key]
            if (existing != null) {
                if (reason !in existing.split('|')) selected[key] = "$existing|$reason"
            } else if (selected.size < DENSE_REFERENCE_SAMPLE_LIMIT) {
                selected[key] = reason
            }
        }
        val trajectory = eligible.associateWith { observedTrajectoryScore(it.third) }
        add(eligible.minByOrNull { abs(trajectory.getValue(it)) }, "STABLE_OBSERVED_TRAJECTORY")
        add(eligible.maxByOrNull { trajectory.getValue(it) }, "STRONGLY_PROGRESSING_OBSERVED_TRAJECTORY")
        val knownDifficult = eligible.firstOrNull {
            it.first.label.contains("neutral-grip lat pulldown", ignoreCase = true)
        }
        add(
            knownDifficult ?: eligible.maxByOrNull { numericalStressScore(it.third) },
            if (knownDifficult != null) "PREVIOUS_NUMERICAL_STRESS_REGRESSION" else "NUMERICAL_STRESS_HEURISTIC",
        )
        eligible
            .sortedWith(compareByDescending<Triple<NBio7BProfileDescriptor, dev.kian.mymettle.domain.performance.Laterality, DynamicResistanceEvidenceProjection>> {
                it.third.independentSessionCount
            }.thenByDescending { it.third.evidence.size })
            .forEach { add(it, "SUPPORT_RICHNESS_FILL") }
        return selected
    }

    private fun profileKey(
        descriptor: NBio7BProfileDescriptor,
        side: dev.kian.mymettle.domain.performance.Laterality,
    ): String = "${descriptor.semantics.executionProfileVersionId.value}|${side.storageValue}"

    /** Diagnostic-only sampling score; never enters Candidate-v2 priors, likelihoods or evidence policy. */
    private fun observedTrajectoryScore(projection: DynamicResistanceEvidenceProjection): Double {
        val medians = sessionLogResistanceMedians(projection)
        if (medians.size < 2) return 0.0
        return (medians.last() - medians.first()) / (medians.size - 1).toDouble()
    }

    private fun numericalStressScore(projection: DynamicResistanceEvidenceProjection): Double {
        val medians = sessionLogResistanceMedians(projection)
        if (medians.isEmpty()) return 0.0
        return (medians.maxOrNull()!! - medians.minOrNull()!!) + abs(observedTrajectoryScore(projection))
    }

    private fun sessionLogResistanceMedians(projection: DynamicResistanceEvidenceProjection): List<Double> =
        projection.evidence
            .groupBy { it.sessionId }
            .values
            .sortedBy { session -> session.minOf { it.completedAt } }
            .map { session -> median(session.map { ln(it.resistance.value) }) }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        if (sorted.isEmpty()) return 0.0
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private fun fitFrozenV1(
        projection: DynamicResistanceEvidenceProjection,
        horizon: Instant,
    ): DynamicStochasticFrontierFit {
        val model = DynamicStochasticFrontierModel(NBioCorrectedCandidateV2Bundle.baseConfig)
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
            DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(fit, loaded) &&
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
        frozenV1: DynamicStochasticFrontierFit,
    ): Boolean {
        val replayed = solver.fitFromFrozenV1(
            DynamicCapabilityFitRequest(
                projection = projection,
                inferenceHorizon = horizon,
                modelConfig = solver.modelConfig(CONFIG_CREATED_AT),
            ),
            frozenV1,
        )
        return DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(original, replayed) &&
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
        val frozenV1FitElapsedMillis: Long? = null,
        val replayFrozenV1FitElapsedMillis: Long? = null,
        val densePersistElapsedMillis: Long? = null,
        val sparsePersistElapsedMillis: Long? = null,
        val laplacePersistElapsedMillis: Long? = null,
        val denseReplayElapsedMillis: Long? = null,
        val sparseReplayElapsedMillis: Long? = null,
        val laplaceReplayElapsedMillis: Long? = null,
        val sparseFidelityElapsedMillis: Long? = null,
        val laplaceFidelityElapsedMillis: Long? = null,
        val limitations: List<String> = emptyList(),
    )

    private data class TimedOutcome<T>(
        val value: T?,
        val elapsedMillis: Long,
        val failure: Throwable?,
    )

    private fun <T> timed(block: () -> T): TimedOutcome<T> {
        val start = System.nanoTime()
        return try {
            TimedOutcome(block(), elapsedMillis(start), null)
        } catch (failure: Throwable) {
            TimedOutcome(null, elapsedMillis(start), failure)
        }
    }

    private suspend fun <T> timedSuspend(block: suspend () -> T): TimedOutcome<T> {
        val start = System.nanoTime()
        return try {
            TimedOutcome(block(), elapsedMillis(start), null)
        } catch (failure: Throwable) {
            TimedOutcome(null, elapsedMillis(start), failure)
        }
    }

    private fun elapsedMillis(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

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
    .put("workloadOrder", workloadOrder)
    .put("executionProfileVersionId", executionProfileVersionId)
    .put("label", label)
    .put("side", side)
    .put("eligibleObservationCount", eligibleObservationCount)
    .put("independentSessionCount", independentSessionCount)
    .put("chronologicalFitCount", chronologicalFitCount)
    .put("denseReferenceSampled", denseReferenceSampled)
    .put("denseReferenceSelectionReason", denseReferenceSelectionReason ?: JSONObject.NULL)
    .put("retrospectiveProtocolVersion", bakeoff.protocolVersion)
    .put("timing", timing.toJson())
    .put("thermalBefore", thermalBefore.toJson())
    .put("thermalAfter", thermalAfter.toJson())
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
    .put(
        "v1",
        bakeoff.v1PredictiveMetrics.toJson(
            bakeoff.v1ValidationSummary,
            bakeoff.v1Verdict.storageValue,
            bakeoff.v1FitElapsedMillis,
            bakeoff.v1PredictiveScoringElapsedMillis,
        ),
    )
    .put(
        "candidateV2Solvers",
        JSONArray(
            bakeoff.candidates.map { candidate ->
                candidate.predictiveMetrics
                    .toJson(
                        candidate.validationSummary,
                        candidate.developmentComparisonAgainstV1.verdict.storageValue,
                        candidate.extensionWallElapsedMillis,
                        candidate.predictiveScoringElapsedMillis,
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
    predictiveScoringRuntimeMillis: Long,
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
    .put("predictiveScoringRuntimeMillis", predictiveScoringRuntimeMillis)

private fun DynamicTrendPosteriorFidelityResult.toJson(): JSONObject = JSONObject()
    .put("referenceSolver", referenceSolver)
    .put("candidateSolver", candidateSolver)
    .put("referenceNodeCount", referenceNodeCount)
    .put("candidateNodeCount", candidateNodeCount)
    .put("referenceTrendPositiveProbability", referenceTrendPositiveProbability)
    .put("candidateTrendPositiveProbability", candidateTrendPositiveProbability)
    .put("trendPositiveProbabilityAbsoluteError", trendPositiveProbabilityAbsoluteError)
    .put("referenceNextFrontierP05Kg", referenceNextFrontierP05Kg)
    .put("referenceNextFrontierP50Kg", referenceNextFrontierP50Kg)
    .put("referenceNextFrontierP95Kg", referenceNextFrontierP95Kg)
    .put("candidateNextFrontierP05Kg", candidateNextFrontierP05Kg)
    .put("candidateNextFrontierP50Kg", candidateNextFrontierP50Kg)
    .put("candidateNextFrontierP95Kg", candidateNextFrontierP95Kg)
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
    .put(
        "covariances",
        JSONArray(
            covariances.map { covariance ->
                JSONObject()
                    .put("leftParameter", covariance.leftParameter)
                    .put("rightParameter", covariance.rightParameter)
                    .put("referenceCovariance", covariance.referenceCovariance)
                    .put("candidateCovariance", covariance.candidateCovariance)
                    .put("absoluteError", covariance.absoluteError)
                    .put("correlationScaleError", covariance.correlationScaleError ?: JSONObject.NULL)
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