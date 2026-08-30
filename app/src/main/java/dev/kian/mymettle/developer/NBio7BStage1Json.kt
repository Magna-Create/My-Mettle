package dev.kian.mymettle.developer

import dev.kian.mymettle.engine.inference.DynamicStage1DiagnosticSummary
import dev.kian.mymettle.engine.inference.DynamicStage1EventDiagnostic
import dev.kian.mymettle.engine.inference.DynamicStage1ProfileDiagnostics
import dev.kian.mymettle.engine.inference.DynamicStage1SerialSummary
import dev.kian.mymettle.engine.inference.DynamicStage1TrendGroupSummary
import org.json.JSONArray
import org.json.JSONObject

internal fun DynamicStage1ProfileDiagnostics.toStage1Json(): JSONObject = JSONObject()
    .put("executionProfileVersionId", executionProfileVersionId)
    .put("side", side)
    .put("summary", summary.toStage1Json())
    .put("events", JSONArray(events.map { it.toStage1Json() }))

internal fun DynamicStage1DiagnosticSummary.toStage1Json(): JSONObject = JSONObject()
    .put("policyId", policyId)
    .put("evaluableEventCount", evaluableEventCount)
    .put("meanSignedLogResidual", meanSignedLogResidual ?: JSONObject.NULL)
    .put("medianSignedLogResidual", medianSignedLogResidual ?: JSONObject.NULL)
    .put("positiveResidualProportion", positiveResidualProportion ?: JSONObject.NULL)
    .put("meanPredictiveLogWidth", meanPredictiveLogWidth ?: JSONObject.NULL)
    .put("meanCrpsLogResistance", meanCrpsLogResistance ?: JSONObject.NULL)
    .put("trendClassifiedEventCount", trendClassifiedEventCount)
    .put("trendResidualCorrelation", trendResidualCorrelation ?: JSONObject.NULL)
    .put("byTrend", JSONObject().also { root -> byTrend.forEach { (key, value) -> root.put(key.storageValue, value.toJson()) } })
    .put("serial", serial.toJson())
    .put("verdict", verdict.storageValue)
    .put("limitations", JSONArray(limitations))

private fun DynamicStage1EventDiagnostic.toStage1Json(): JSONObject = JSONObject()
    .put("sessionOrdinal", sessionOrdinal)
    .put("repetitions", repetitions)
    .put("observedResistanceKg", observedResistanceKg)
    .put("priorIndependentSessionCount", priorIndependentSessionCount)
    .put("priorRepMin", priorRepMin ?: JSONObject.NULL)
    .put("priorRepMax", priorRepMax ?: JSONObject.NULL)
    .put("repDomainPosition", repDomainPosition.storageValue)
    .put("prediction", JSONObject()
        .put("p05Kg", predictiveP05Kg).put("p50Kg", predictiveP50Kg).put("p95Kg", predictiveP95Kg)
        .put("logWidth", predictiveLogWidth).put("pit", pit)
        .put("logPredictiveDensity", logPredictiveDensity).put("crpsLogResistance", crpsLogResistance))
    .put("frontier", JSONObject().put("p05Kg", frontierP05Kg).put("p50Kg", frontierP50Kg).put("p95Kg", frontierP95Kg))
    .put("signedLogResidual", signedLogResidual)
    .put("recentTrend", JSONObject()
        .put("logPerSession", recentTrendLogPerSession ?: JSONObject.NULL)
        .put("direction", recentTrendDirection.storageValue)
        .put("comparablePriorSessions", recentTrendComparableSessions))
    .put("previousSessionMedianSignedLogResiduals", JSONArray(previousSessionMedianSignedLogResiduals))
    .put("priorPositiveResidualSessionStreak", priorPositiveResidualSessionStreak)
    .put("coveredByPredictiveInterval", coveredByPredictiveInterval)
    .put("catastrophicFrontierContradiction", catastrophicFrontierContradiction)

private fun DynamicStage1TrendGroupSummary.toJson(): JSONObject = JSONObject()
    .put("count", count)
    .put("meanSignedLogResidual", meanSignedLogResidual ?: JSONObject.NULL)
    .put("medianSignedLogResidual", medianSignedLogResidual ?: JSONObject.NULL)
    .put("positiveResidualProportion", positiveResidualProportion ?: JSONObject.NULL)
    .put("pit", JSONObject().put("low", pitLowCount).put("middle", pitMiddleCount).put("high", pitHighCount).put("highRate", highPitRate ?: JSONObject.NULL))
    .put("predictiveCoverage", predictiveCoverage ?: JSONObject.NULL)
    .put("catastrophicContradictionRate", catastrophicContradictionRate ?: JSONObject.NULL)
    .put("meanPredictiveLogWidth", meanPredictiveLogWidth ?: JSONObject.NULL)
    .put("meanCrpsLogResistance", meanCrpsLogResistance ?: JSONObject.NULL)

private fun DynamicStage1SerialSummary.toJson(): JSONObject = JSONObject()
    .put("profileSessionCount", profileSessionCount)
    .put("adjacentPairCount", adjacentPairCount)
    .put("sameSignAdjacentRate", sameSignAdjacentRate ?: JSONObject.NULL)
    .put("positivePositiveAdjacentRate", positivePositiveAdjacentRate ?: JSONObject.NULL)
    .put("lag1ResidualCorrelation", lag1ResidualCorrelation ?: JSONObject.NULL)
    .put("longestPositiveRun", longestPositiveRun)
