package dev.kian.mymettle.developer

import android.content.Context
import android.os.Debug
import android.os.Process
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.inference.DynamicTrendDenseReferenceSolverAdapter
import dev.kian.mymettle.engine.inference.DynamicTrendLaplaceSolverAdapter
import dev.kian.mymettle.engine.inference.DynamicTrendPosteriorFidelity
import dev.kian.mymettle.engine.inference.DynamicTrendPosteriorFidelityResult
import dev.kian.mymettle.engine.inference.DynamicTrendSolverHistoricalBakeoff
import dev.kian.mymettle.engine.inference.DynamicTrendSolverHistoricalBakeoffResult
import dev.kian.mymettle.engine.inference.HistoricalObservationRevisionSelector
import dev.kian.mymettle.engine.performance.DynamicResistanceEvidenceProjector
import dev.kian.mymettle.inference.DynamicTrendCapabilityParameterCodec
import dev.kian.mymettle.inference.DynamicTrendCapabilityShadowRepository
import java.time.Instant
import kotlin.system.measureTimeMillis
import org.json.JSONArray
import org.json.JSONObject

/** Privacy-bounded installed-device report for the single N-BIO-7B.X consolidation mission. */
data class NBioAdaptiveProfileResult(
    val executionProfileVersionId: String,
    val label: String,
    val side: String,
    val eligibleObservationCount: Int,
    val independentSessionCount: Int,
    val chronologicalFitCount: Int,
    val bakeoff: DynamicTrendSolverHistoricalBakeoffResult,
    val currentPosteriorFidelity: DynamicTrendPosteriorFidelityResult?,
    val densePersistReloadEquivalent: Boolean?,
    val laplacePersistReloadEquivalent: Boolean?,
    val denseReplayEquivalent: Boolean?,
    val laplaceReplayEquivalent: Boolean?,
    val currentFitElapsedMillisDense: Long?,
    val currentFitElapsedMillisLaplace: Long?,
    val limitations: List<String>,
)

data class NBioAdaptiveInferenceAcceptanceReport(
    val generatedAt: Instant,
    val roomSchemaVersion: Int,
    val rawFingerprintBefore: NBio7BRawEvidenceFingerprint,
    val rawFingerprintAfter: NBio7BRawEvidenceFingerprint,
    val prescriptionBefore: NBio7BPrescriptionStateFingerprint,
    val prescriptionAfter: NBio7BPrescriptionStateFingerprint,
    val benchmarkRunIdBefore: String?,
    val benchmarkRunIdAfter: String?,
    val profiles: List<NBioAdaptiveProfileResult>,
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
        .put("formatVersion", 1)
        .put("generatedAt", generatedAt.toString())
        .put("mission", "N-BIO-7B.X_ADAPTIVE_INFERENCE_ARCHITECTURE_CONSOLIDATION")
        .put("evidenceClass", "RETROSPECTIVE_DEVELOPMENT")
        .put("freshConfirmationRequired", true)
        .put("roomSchemaVersion", roomSchemaVersion)
        .put("contextConsumption", DynamicTrendFrontierV2.config.contextConsumption)
        .put("candidateV1Status", "FROZEN_REJECTED_EMPIRICAL_CALIBRATION")
        .put("candidateV2Status", "DEVELOPMENT_CANDIDATE_NOT_PRODUCT_AUTHORITY")
        .put("candidateV2MathematicalModel", JSONObject()
            .put("family", DynamicTrendFrontierV2.mathematicalModelIdentity.family)
            .put("semanticVersion", DynamicTrendFrontierV2.mathematicalModelIdentity.semanticVersion)
            .put("definition", DynamicTrendFrontierV2.mathematicalModelIdentity.definition))
        .put("solvers", JSONArray(listOf(
            DynamicTrendDenseReferenceSolverAdapter().solverIdentity.toJson(),
            DynamicTrendLaplaceSolverAdapter().solverIdentity.toJson(),
        )))
        .put("rawEvidence", JSONObject()
            .put("beforeSha256", rawFingerprintBefore.sha256)
            .put("afterSha256", rawFingerprintAfter.sha256)
            .put("unchanged", rawEvidenceUnchanged)
            .put("tableRowCounts", JSONObject(rawFingerprintAfter.tableRowCounts)))
        .put("prescriptionState", JSONObject()
            .put("beforeSha256", prescriptionBefore.sha256)
            .put("afterSha256", prescriptionAfter.sha256)
            .put("unchanged", prescriptionsUnchanged)
            .put("tableRowCounts", JSONObject(prescriptionAfter.tableRowCounts)))
        .put("benchmarkAuthority", JSONObject()
            .put("beforeRunId", benchmarkRunIdBefore ?: JSONObject.NULL)
            .put("afterRunId", benchmarkRunIdAfter ?: JSONObject.NULL)
            .put("unchanged", benchmarkAuthorityUnchanged)
            .put("authority", "BENCHMARK_V0"))
        .put("memory", JSONObject()
            .put("javaHeapUsedBeforeBytes", javaHeapUsedBeforeBytes)
            .put("javaHeapUsedAfterBytes", javaHeapUsedAfterBytes)
            .put("nativeHeapBeforeBytes", nativeHeapBeforeBytes)
            .put("nativeHeapAfterBytes", nativeHeapAfterBytes)
            .put("note", "process-level snapshots bound the acceptance harness; per-kernel peak RAM requires profiler/device instrumentation"))
        .put("performance", JSONObject().put("totalElapsedMillis", totalElapsedMillis))
        .put("profiles", JSONArray(profiles.map { it.toJson() }))
        .put("nativeBackupRoundTrip", JSONObject()
            .put("schemaVersion", backupRoundTrip.schemaVersion)
            .put("rawEvidenceMatches", backupRoundTrip.rawEvidenceMatches)
            .put("prescriptionStateMatches", backupRoundTrip.prescriptionStateMatches)
            .put("candidateRowsMatch", backupRoundTrip.candidateRowsMatch)
            .put("foreignKeysClean", backupRoundTrip.foreignKeysClean)
            .put("passed", backupRoundTrip.passed))
        .put("architectureSubstrates", JSONObject()
            .put("denseSequentialTensor", "IMPLEMENTED_GENERIC_REFERENCE_SUBSTRATE")
            .put("adaptiveSparseTensor", "IMPLEMENTED_GENERIC_CHALLENGER_SUBSTRATE")
            .put("lowRank", "VIABILITY_SCREEN_IMPLEMENTED_NOT_AUTOMATICALLY_PRODUCTION")
            .put("sigmaPoint", "IMPLEMENTED_GENERIC_FAST_CHALLENGER_NOT_YET_CANDIDATE_V2_ADAPTER")
            .put("factorDependencyIndex", "IMPLEMENTED_MINIMUM_INVALIDATION_ABSTRACTION")
            .put("nativeCpu", "DEVICE_PROFILING_REQUIRED_BEFORE_KERNEL_PORT")
            .put("vulkan", "NOT_RUN_UNTIL_CPU_PROFILE_JUSTIFIES_DATA_PARALLEL_KERNEL")
            .put("liteRtNpu", "PLATFORM_AUDIT_REQUIRED_NOT_ASSUMED_GENERAL_BAYES_BACKEND"))
        .put("safetyPassed", safetyPassed)
        .put("productAuthorityChanged", false)
        .put("normalWorkoutBehaviourChanged", false)
        .put("nBio7CStarted", false)
        .toString(2)
}

/**
 * One foreground Biological Developer action over the installed Room14 history.
 *
 * It intentionally labels all historical predictive results DEVELOPMENT evidence. Dense Candidate-v2
 * is the same-math reference; conditional-Laplace is a solver challenger. Both are persisted only as
 * SHADOW derived state and immediately replay-checked. The action never updates normal prescriptions
 * or BENCHMARK_V0 product authority.
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
            currentAsKnown.filter { it.executionProfileVersionId == descriptor.semantics.executionProfileVersionId }
                .map { it.laterality }
                .distinct()
                .map { side -> descriptor to side }
        }
        val denseSolver = DynamicTrendDenseReferenceSolverAdapter()
        val laplaceSolver = DynamicTrendLaplaceSolverAdapter()
        val results = mutableListOf<NBioAdaptiveProfileResult>()
        groups.forEachIndexed { index, (descriptor, side) ->
            onProgress(NBio7BAcceptanceProgress(index, groups.size, "Adaptive inference · ${descriptor.label} · ${side.storageValue}"))
            val projection = DynamicResistanceEvidenceProjector.project(
                descriptor.semantics,
                side,
                currentAsKnown,
                DynamicResistanceV2Contract.evidencePolicy,
            )
            val bakeoff = DynamicTrendSolverHistoricalBakeoff(listOf(denseSolver, laplaceSolver)).evaluate(
                descriptor.semantics,
                side,
                raw.revisions,
            )
            var denseFitElapsed: Long? = null
            var laplaceFitElapsed: Long? = null
            var fidelity: DynamicTrendPosteriorFidelityResult? = null
            var densePersist: Boolean? = null
            var laplacePersist: Boolean? = null
            var denseReplay: Boolean? = null
            var laplaceReplay: Boolean? = null
            val limitations = mutableListOf<String>()
            if (projection.evidence.isNotEmpty()) {
                val horizon = projection.evidence.maxOf { it.completedAt }
                val denseFit = runCatching {
                    lateinit var fit: dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
                    denseFitElapsed = measureTimeMillis {
                        fit = denseSolver.fit(
                            dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest(
                                projection,
                                horizon,
                                denseSolver.modelConfig(CONFIG_CREATED_AT),
                            ),
                        )
                    }
                    fit
                }.getOrNull()
                val laplaceFit = runCatching {
                    lateinit var fit: dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
                    laplaceFitElapsed = measureTimeMillis {
                        fit = laplaceSolver.fit(
                            dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest(
                                projection,
                                horizon,
                                laplaceSolver.modelConfig(CONFIG_CREATED_AT),
                            ),
                        )
                    }
                    fit
                }.getOrNull()
                if (denseFit != null && laplaceFit != null) {
                    fidelity = DynamicTrendPosteriorFidelity.compare(denseFit, laplaceFit)
                    val reps = denseFit.referenceRepetitions
                    densePersist = persistReloadEquivalent(userProfileId, denseSolver, denseFit, reps)
                    laplacePersist = persistReloadEquivalent(userProfileId, laplaceSolver, laplaceFit, reps)
                    denseReplay = replayEquivalent(denseSolver, projection, horizon, denseFit, reps)
                    laplaceReplay = replayEquivalent(laplaceSolver, projection, horizon, laplaceFit, reps)
                } else {
                    limitations += "One or both Candidate-v2 current fits failed; inspect historical bake-off diagnostics rather than treating missing state as a pass."
                }
            } else {
                limitations += "No current eligible dynamic-resistance evidence for this profile/side under evidence-policy v2."
            }
            results += NBioAdaptiveProfileResult(
                executionProfileVersionId = descriptor.semantics.executionProfileVersionId.value,
                label = descriptor.label,
                side = side.storageValue,
                eligibleObservationCount = projection.evidence.size,
                independentSessionCount = projection.independentSessionCount,
                chronologicalFitCount = bakeoff.chronologicalFitCount,
                bakeoff = bakeoff,
                currentPosteriorFidelity = fidelity,
                densePersistReloadEquivalent = densePersist,
                laplacePersistReloadEquivalent = laplacePersist,
                denseReplayEquivalent = denseReplay,
                laplaceReplayEquivalent = laplaceReplay,
                currentFitElapsedMillisDense = denseFitElapsed,
                currentFitElapsedMillisLaplace = laplaceFitElapsed,
                limitations = limitations,
            )
        }
        onProgress(NBio7BAcceptanceProgress(groups.size, groups.size, "Verifying Native backup and safety fingerprints"))
        val backup = backupVerifier.verify()
        val rawAfter = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionAfter = NBio7BPrescriptionStateFingerprinter.capture(database)
        val benchmarkAfter = database.inferenceDao().latestInferenceRun(userProfileId)?.id
        return NBioAdaptiveInferenceAcceptanceReport(
            generatedAt = Instant.now(),
            roomSchemaVersion = MyMettleDatabase.SCHEMA_VERSION,
            rawFingerprintBefore = rawBefore,
            rawFingerprintAfter = rawAfter,
            prescriptionBefore = prescriptionBefore,
            prescriptionAfter = prescriptionAfter,
            benchmarkRunIdBefore = benchmarkBefore,
            benchmarkRunIdAfter = benchmarkAfter,
            profiles = results,
            backupRoundTrip = backup,
            javaHeapUsedBeforeBytes = javaBefore,
            javaHeapUsedAfterBytes = usedJavaHeap(),
            nativeHeapBeforeBytes = nativeBefore,
            nativeHeapAfterBytes = Debug.getNativeHeapAllocatedSize(),
            totalElapsedMillis = (System.nanoTime() - wallStart) / 1_000_000L,
        )
    }

    private suspend fun persistReloadEquivalent(
        userProfileId: String,
        solver: dev.kian.mymettle.engine.inference.DynamicTrendCandidateV2Solver,
        fit: dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit,
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
        solver: dev.kian.mymettle.engine.inference.DynamicTrendCandidateV2Solver,
        projection: dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection,
        horizon: Instant,
        original: dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit,
        repetitions: Double,
    ): Boolean {
        val replayed = solver.fit(
            dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest(
                projection,
                horizon,
                solver.modelConfig(CONFIG_CREATED_AT),
            ),
        )
        return DynamicTrendCapabilityParameterCodec.encode(original) == DynamicTrendCapabilityParameterCodec.encode(replayed) &&
            summariesEquivalent(solverPrediction(solver, original, repetitions), solverPrediction(solver, replayed, repetitions))
    }

    private fun solverPrediction(
        solver: dev.kian.mymettle.engine.inference.DynamicTrendCandidateV2Solver,
        fit: dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit,
        repetitions: Double,
    ) = dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel(solver.baseConfig)
        .predictFrontier(solver.projectToNextSession(fit), repetitions)

    private fun summariesEquivalent(
        left: dev.kian.mymettle.domain.inference.PosteriorEstimate,
        right: dev.kian.mymettle.domain.inference.PosteriorEstimate,
    ): Boolean {
        val a = left.summary ?: return right.summary == null
        val b = right.summary ?: return false
        return listOf(a.p05 to b.p05, a.p50 to b.p50, a.p95 to b.p95, a.posteriorVariance to b.posteriorVariance)
            .all { (x, y) -> kotlin.math.abs(x - y) <= 1e-10 * kotlin.math.max(1.0, kotlin.math.abs(x)) }
    }

    private fun usedJavaHeap(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    companion object {
        private val CONFIG_CREATED_AT = Instant.parse("2026-08-31T00:00:00Z")
    }
}

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
    .put("currentFitRuntimeMillis", JSONObject()
        .put("denseReference", currentFitElapsedMillisDense ?: JSONObject.NULL)
        .put("conditionalLaplace", currentFitElapsedMillisLaplace ?: JSONObject.NULL))
    .put("persistenceReplay", JSONObject()
        .put("densePersistReloadEquivalent", densePersistReloadEquivalent ?: JSONObject.NULL)
        .put("laplacePersistReloadEquivalent", laplacePersistReloadEquivalent ?: JSONObject.NULL)
        .put("denseFullReplayEquivalent", denseReplayEquivalent ?: JSONObject.NULL)
        .put("laplaceFullReplayEquivalent", laplaceReplayEquivalent ?: JSONObject.NULL))
    .put("v1", bakeoff.v1PredictiveMetrics.toJson(bakeoff.v1ValidationSummary, bakeoff.v1Verdict.storageValue, bakeoff.v1FitElapsedMillis))
    .put("candidateV2Solvers", JSONArray(bakeoff.candidates.map { candidate ->
        candidate.predictiveMetrics.toJson(
            candidate.validationSummary,
            candidate.developmentComparisonAgainstV1.verdict.storageValue,
            candidate.extensionWallElapsedMillis,
        ).put("solver", candidate.solverIdentity.toJson())
            .put("absoluteValidationVerdict", candidate.absoluteValidationVerdict.storageValue)
            .put("developmentComparison", JSONObject()
                .put("crpsRelativeImprovement", candidate.developmentComparisonAgainstV1.crpsRelativeImprovement ?: JSONObject.NULL)
                .put("absoluteBiasRelativeImprovement", candidate.developmentComparisonAgainstV1.absoluteBiasRelativeImprovement ?: JSONObject.NULL)
                .put("widthRatio", candidate.developmentComparisonAgainstV1.widthRatio ?: JSONObject.NULL)
                .put("maeRatio", candidate.developmentComparisonAgainstV1.maeRatio ?: JSONObject.NULL))
    }))
    .put("currentPosteriorFidelity", currentPosteriorFidelity?.toJson() ?: JSONObject.NULL)
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
    .put("pitBins", JSONObject()
        .put("low", validation.candidatePitCalibration.lowCount)
        .put("middle", validation.candidatePitCalibration.middleCount)
        .put("high", validation.candidatePitCalibration.highCount))
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
    .put("marginals", JSONArray(marginals.map { marginal ->
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
    }))
