package dev.kian.mymettle.developer

import android.content.Context
import dev.kian.mymettle.data.local.MyMettleDatabase
import org.json.JSONArray
import org.json.JSONObject

data class NBio7DCompleteAcceptanceReport(
    val core: NBio7DDemandDoseAcceptanceReport,
    val sensitivity: NBio7DSensitivityValidationReport,
    val correctionBoundary: NBio7DCorrectionBoundaryValidationReport,
    val downstreamFidelity: NBio7DDownstreamFidelityValidationReport,
    val benchmarkV0RunIdBefore: String?,
    val benchmarkV0RunIdAfter: String?,
) {
    val validationBundlePassed: Boolean get() = sensitivity.passed && correctionBoundary.passed && downstreamFidelity.passed
    val benchmarkV0AuthorityUnchanged: Boolean get() = benchmarkV0RunIdBefore != null && benchmarkV0RunIdBefore == benchmarkV0RunIdAfter
    val structuralVerdict: NBio7DStructuralVerdict get() = if (
        core.structuralVerdict == NBio7DStructuralVerdict.PASS && validationBundlePassed && benchmarkV0AuthorityUnchanged
    ) NBio7DStructuralVerdict.PASS else NBio7DStructuralVerdict.FAIL
    val empiricalCalibrationStatus: NBio7DEmpiricalCalibrationStatus get() = core.empiricalCalibrationStatus
    val overallVerdict: NBio7DOverallVerdict get() = if (structuralVerdict == NBio7DStructuralVerdict.PASS) {
        NBio7DOverallVerdict.READY_FOR_7D_STRUCTURAL_CLOSURE_EMPIRICAL_CALIBRATION_PENDING
    } else NBio7DOverallVerdict.STRUCTURAL_PREVALIDATION_FAILED

    fun toJson(): String {
        val root = JSONObject(core.toJson())
        root.put("formatVersion", 2)
        root.put("sensitivity", sensitivity.toJson7dComplete())
        root.put("correctionBoundary", correctionBoundary.toJson7dComplete())
        root.put("downstreamSolverFidelity", downstreamFidelity.toJson7dComplete())
        root.put("validationBundlePassed", validationBundlePassed)
        root.put("benchmarkV0Authority", JSONObject()
            .put("runIdBefore", benchmarkV0RunIdBefore ?: JSONObject.NULL)
            .put("runIdAfter", benchmarkV0RunIdAfter ?: JSONObject.NULL)
            .put("unchanged", benchmarkV0AuthorityUnchanged))
        root.put("verdicts", JSONObject()
            .put("structural", structuralVerdict.storageValue)
            .put("empiricalCalibration", empiricalCalibrationStatus.storageValue)
            .put("overall7D", overallVerdict.storageValue))
        root.put("nBio7EStarted", false)
        return root.toString(2)
    }
}

/** Single installed-device action spanning every N-BIO-7D structural acceptance layer. */
class NBio7DCompleteAcceptanceRunner(
    context: Context,
    private val database: MyMettleDatabase,
    private val coreRunner: NBio7DDemandDoseAcceptanceRunner = NBio7DDemandDoseAcceptanceRunner(context, database),
) {
    suspend fun run(
        onProgress: (NBio7BAcceptanceProgress) -> Unit = {},
    ): NBio7DCompleteAcceptanceReport {
        val benchmarkBefore = latestBenchmarkV0RunId()
        onProgress(NBio7BAcceptanceProgress(0, 7, "N-BIO-7D · delta/tau sensitivity"))
        val sensitivity = NBio7DSensitivityValidation.run()
        onProgress(NBio7BAcceptanceProgress(1, 7, "N-BIO-7D · causal correction boundaries"))
        val correction = NBio7DCorrectionBoundaryValidation.run()
        onProgress(NBio7BAcceptanceProgress(2, 7, "N-BIO-7D · downstream Dense/Adaptive-Sparse fidelity"))
        val fidelity = NBio7DDownstreamFidelityValidation.run()
        val core = coreRunner.run { progress ->
            onProgress(
                NBio7BAcceptanceProgress(
                    completedGroups = (progress.completedGroups + 3).coerceAtMost(7),
                    totalGroups = 7,
                    label = progress.label,
                ),
            )
        }
        val benchmarkAfter = latestBenchmarkV0RunId()
        return NBio7DCompleteAcceptanceReport(
            core = core,
            sensitivity = sensitivity,
            correctionBoundary = correction,
            downstreamFidelity = fidelity,
            benchmarkV0RunIdBefore = benchmarkBefore,
            benchmarkV0RunIdAfter = benchmarkAfter,
        )
    }

    private fun latestBenchmarkV0RunId(): String? = database.openHelper.readableDatabase.query(
        "SELECT id FROM inference_run WHERE executionMode = 'benchmark_v0' ORDER BY calculatedAt DESC, id DESC LIMIT 1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}

private fun NBio7DSensitivityValidationReport.toJson7dComplete(): JSONObject = JSONObject()
    .put("deltaGridLog", JSONArray(deltaPoints.map { it.deltaLog }))
    .put("deltaPoints", JSONArray(deltaPoints.map { point -> JSONObject()
        .put("deltaLog", point.deltaLog)
        .put("highDemandProbability", point.highDemandProbability)
        .put("effectiveDoseMedian", point.effectiveDoseMedian) }))
    .put("tauGrid", JSONArray(tauPoints.map { it.tau }))
    .put("tauPoints", JSONArray(tauPoints.map { point -> JSONObject()
        .put("tau", point.tau)
        .put("rawDoseMedian", point.rawDoseMedian)
        .put("concaveDoseMedian", point.concaveDoseMedian) }))
    .put("deltaMonotonic", deltaMonotonic)
    .put("deltaLeavesGapAndExposureUnchanged", deltaLeavesGapAndExposureUnchanged)
    .put("tauLeavesRawDoseUnchanged", tauLeavesRawDoseUnchanged)
    .put("largerTauApproachesRawDose", largerTauApproachesRawDose)
    .put("passed", passed)

private fun NBio7DCorrectionBoundaryValidationReport.toJson7dComplete(): JSONObject = JSONObject()
    .put("latePriorCorrectionDoesNotLeakBackward", latePriorCorrectionDoesNotLeakBackward)
    .put("targetCorrectionRepairsTargetWithoutChangingBaseline", targetCorrectionRepairsTargetWithoutChangingBaseline)
    .put("priorCorrectionEntersLaterSessionBaseline", priorCorrectionEntersLaterSessionBaseline)
    .put("simultaneousSessionExcludedFromPriorBaseline", simultaneousSessionExcludedFromPriorBaseline)
    .put("passed", passed)

private fun NBio7DDownstreamFidelityValidationReport.toJson7dComplete(): JSONObject = JSONObject()
    .put("dynamic", JSONArray(dynamicScenarios.map { result -> JSONObject()
        .put("name", result.name)
        .put("maximumGapQuantileAbsoluteError", result.maximumGapQuantileAbsoluteError)
        .put("maximumDemandProbabilityAbsoluteError", result.maximumDemandProbabilityAbsoluteError)
        .put("maximumEffectiveDoseQuantileAbsoluteError", result.maximumEffectiveDoseQuantileAbsoluteError)
        .put("sessionRawP50AbsoluteError", result.sessionRawP50AbsoluteError)
        .put("sessionConcaveP50AbsoluteError", result.sessionConcaveP50AbsoluteError)
        .put("denseElapsedMillis", result.denseElapsedMillis)
        .put("adaptiveSparseElapsedMillis", result.sparseElapsedMillis)
        .put("passed", result.passed) }))
    .put("loadedHold7C", JSONObject()
        .put("family", nonDynamicLoadedHold.family)
        .put("gapP05AbsoluteError", nonDynamicLoadedHold.gapP05AbsoluteError)
        .put("gapP50AbsoluteError", nonDynamicLoadedHold.gapP50AbsoluteError)
        .put("gapP95AbsoluteError", nonDynamicLoadedHold.gapP95AbsoluteError)
        .put("demandProbabilityAbsoluteError", nonDynamicLoadedHold.demandProbabilityAbsoluteError)
        .put("exposureUnchanged", nonDynamicLoadedHold.exposureUnchanged)
        .put("denseElapsedMillis", nonDynamicLoadedHold.denseElapsedMillis)
        .put("adaptiveSparseElapsedMillis", nonDynamicLoadedHold.sparseElapsedMillis)
        .put("pd001", "EMPIRICAL_ACCURACY_PENDING")
        .put("pd002", "EMPIRICAL_CALIBRATION_PENDING")
        .put("passed", nonDynamicLoadedHold.passed))
    .put("passed", passed)
