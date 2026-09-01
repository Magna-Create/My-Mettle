from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'anchor not found in {path}: {old[:120]!r}')
    p.write_text(text.replace(old, new, 1))


def replace_between(path: str, start_marker: str, end_marker: str, replacement: str) -> None:
    p = Path(path)
    text = p.read_text()
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f'start marker not found in {path}: {start_marker!r}')
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f'end marker not found in {path}: {end_marker!r}')
    p.write_text(text[:start] + replacement + text[end:])


# ---------------------------------------------------------------------------
# 1) Candidate-v2 identity propagation: mathematical identity and solver
# identity are explicit, config-derived and cannot silently fall back.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/dev/kian/mymettle/domain/inference/DynamicTrendFrontierV2.kt'
replace_once(
    path,
    '''    val conditionalLaplaceSolverIdentity = InferenceSolverIdentity(\n        solverFamily = InferenceSolverFamily.SEQUENTIAL_LAPLACE,\n        semanticVersion = "candidate-v2-conditional-laplace-v1",\n        computeBackend = InferenceComputeBackend.KOTLIN_JVM,\n        deterministicReplay = true,\n        approximationDefinition = config.approximationVersion,\n    )\n\n    fun mathematicalIdentity(value: DynamicTrendFrontierConfig): InferenceMathematicalModelIdentity =\n''',
    '''    fun conditionalLaplaceSolverIdentity(value: DynamicTrendFrontierConfig): InferenceSolverIdentity = InferenceSolverIdentity(\n        solverFamily = InferenceSolverFamily.SEQUENTIAL_LAPLACE,\n        semanticVersion = "candidate-v2-conditional-laplace-v1",\n        computeBackend = InferenceComputeBackend.KOTLIN_JVM,\n        deterministicReplay = true,\n        approximationDefinition = value.approximationVersion,\n    )\n\n    val conditionalLaplaceSolverIdentity: InferenceSolverIdentity = conditionalLaplaceSolverIdentity(config)\n\n    fun mathematicalIdentity(value: DynamicTrendFrontierConfig): InferenceMathematicalModelIdentity =\n''',
)
replace_once(
    path,
    '''    val posteriorNodes: List<DynamicTrendFrontierPosteriorNode>,\n    val mathematicalModelIdentity: InferenceMathematicalModelIdentity = DynamicTrendFrontierV2.mathematicalModelIdentity,\n    val solverDiagnostics: InferenceSolverDiagnostics = InferenceSolverDiagnostics(\n        solverIdentity = DynamicTrendFrontierV2.conditionalLaplaceSolverIdentity,\n        posteriorRepresentation = InferencePosteriorRepresentation.WEIGHTED_DENSE_NODES,\n    ),\n) : DynamicCapabilityFit {\n''',
    '''    val posteriorNodes: List<DynamicTrendFrontierPosteriorNode>,\n    /** Required explicitly: mathematical candidate identity is independent of numerical solver identity. */\n    val mathematicalModelIdentity: InferenceMathematicalModelIdentity,\n    /** Required explicitly: no default solver identity may masquerade as the implementation that produced this fit. */\n    val solverDiagnostics: InferenceSolverDiagnostics,\n) : DynamicCapabilityFit {\n''',
)

path = 'app/src/main/java/dev/kian/mymettle/engine/performance/DynamicTrendFrontierModel.kt'
replace_once(
    path,
    'import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2\n',
    'import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2\nimport dev.kian.mymettle.domain.inference.InferencePosteriorRepresentation\nimport dev.kian.mymettle.domain.inference.InferenceSolverDiagnostics\n',
)
replace_once(
    path,
    '''class DynamicTrendFrontierModel(\n    val config: DynamicTrendFrontierConfig = DynamicTrendFrontierV2.config,\n    private val baseModel: DynamicStochasticFrontierModel = DynamicStochasticFrontierModel(config.baseConfig),\n) : DynamicCapabilityModel<DynamicTrendFrontierFit> {\n    override val modelVersion: String get() = config.semanticVersion\n''',
    '''class DynamicTrendFrontierModel(\n    val config: DynamicTrendFrontierConfig = DynamicTrendFrontierV2.config,\n    private val baseModel: DynamicStochasticFrontierModel = DynamicStochasticFrontierModel(config.baseConfig),\n) : DynamicCapabilityModel<DynamicTrendFrontierFit> {\n    override val modelVersion: String get() = config.semanticVersion\n    val mathematicalModelIdentity get() = DynamicTrendFrontierV2.mathematicalIdentity(config)\n    val solverIdentity get() = DynamicTrendFrontierV2.conditionalLaplaceSolverIdentity(config)\n''',
)
replace_once(
    path,
    '''    fun fitFromFrozenV1(\n        request: DynamicCapabilityFitRequest,\n        baseFit: DynamicStochasticFrontierFit,\n    ): DynamicTrendFrontierFit {\n        validateRequest(request)\n''',
    '''    fun fitFromFrozenV1(\n        request: DynamicCapabilityFitRequest,\n        baseFit: DynamicStochasticFrontierFit,\n    ): DynamicTrendFrontierFit {\n        val solverStart = System.nanoTime()\n        validateRequest(request)\n''',
)
replace_once(
    path,
    '''            posteriorEffectiveNodeCount = effectiveNodeCount,\n            warnings = warnings,\n            posteriorNodes = posteriorNodes,\n        )\n''',
    '''            posteriorEffectiveNodeCount = effectiveNodeCount,\n            warnings = warnings,\n            posteriorNodes = posteriorNodes,\n            mathematicalModelIdentity = mathematicalModelIdentity,\n            solverDiagnostics = InferenceSolverDiagnostics(\n                solverIdentity = solverIdentity,\n                posteriorRepresentation = InferencePosteriorRepresentation.WEIGHTED_DENSE_NODES,\n                evaluatedNodeCount = rawNodes.size.toLong(),\n                effectiveNodeCount = effectiveNodeCount,\n                updateRuntimeNanos = System.nanoTime() - solverStart,\n                notes = setOf(\n                    "same_candidate_v2_mathematics_as_dense_reference",\n                    "conditional_laplace_solver",\n                ),\n            ),\n        )\n''',
)

path = 'app/src/main/java/dev/kian/mymettle/engine/inference/DynamicTrendSolverAdapters.kt'
replace_once(
    path,
    '''    override val mathematicalModelIdentity: InferenceMathematicalModelIdentity\n        get() = dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2.mathematicalIdentity(model.config)\n    override val solverIdentity: InferenceSolverIdentity\n        get() = dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2.conditionalLaplaceSolverIdentity\n''',
    '''    override val mathematicalModelIdentity: InferenceMathematicalModelIdentity get() = model.mathematicalModelIdentity\n    override val solverIdentity: InferenceSolverIdentity get() = model.solverIdentity\n''',
)

# ---------------------------------------------------------------------------
# 2) Canonical scientific equality deliberately excludes runtime/operational
# diagnostics while retaining model config, mathematical identity, solver
# identity, posterior representation and all posterior/evidence state.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/dev/kian/mymettle/inference/DynamicTrendCapabilityParameterCodec.kt'
replace_once(
    path,
    '''    fun encode(fit: DynamicTrendFrontierFit): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(\n        deflate(encodePlain(fit).toByteArray(Charsets.UTF_8)),\n    )\n\n    fun decode(\n''',
    '''    fun encode(fit: DynamicTrendFrontierFit): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(\n        deflate(encodePlain(fit).toByteArray(Charsets.UTF_8)),\n    )\n\n    /**\n     * Canonical deterministic scientific equality for replay/persistence checks.\n     *\n     * Runtime duration, evaluated-node counters, peak working memory, worker/thread notes and future\n     * hardware diagnostics are operational telemetry. They must never participate in scientific\n     * equality or hashing. Mathematical model identity and numerical solver identity remain explicit\n     * and independently compared because multiple solvers may evaluate the same candidate model.\n     */\n    fun scientificallyEquivalent(left: DynamicTrendFrontierFit, right: DynamicTrendFrontierFit): Boolean =\n        scientificCanonicalForm(left) == scientificCanonicalForm(right)\n\n    fun scientificCanonicalForm(fit: DynamicTrendFrontierFit): String = buildString {\n        line("executionProfileVersionId", text(fit.executionProfileVersionId.value))\n        line("side", fit.side.storageValue)\n        line("inferenceHorizon", fit.inferenceHorizon.toString())\n        line("referenceRepetitions", fit.referenceRepetitions.toString())\n        line("modelConfigId", text(fit.modelConfigId.value))\n        line("modelVersion", text(fit.modelVersion))\n        line("evidencePolicyIdentity", text(fit.evidencePolicyIdentity))\n        line("supportObservationCount", fit.support.observationCount.toString())\n        line("supportIndependentSessionCount", fit.support.effectiveIndependentSessionCount.toString())\n        line("supportFirstEvidenceAt", fit.support.firstEvidenceAt?.toString() ?: "-")\n        line("supportLastEvidenceAt", fit.support.lastEvidenceAt?.toString() ?: "-")\n        line("supportEvidenceFamily", text(fit.support.evidenceFamily.value))\n        line("observedRepMin", fit.observedRepMin.toString())\n        line("observedRepMax", fit.observedRepMax.toString())\n        line("observedResistanceMinKg", fit.observedResistanceMinKg.toString())\n        line("observedResistanceMaxKg", fit.observedResistanceMaxKg.toString())\n        val frontier = requireNotNull(fit.frontierAtLatestSession.summary)\n        line("frontierSummary", listOf(frontier.p05, frontier.p50, frontier.p95, frontier.posteriorVariance).joinToString(","))\n        line("slope", parameter(fit.slope))\n        line("frontierTrend", parameter(fit.frontierTrend))\n        line("slackScale", parameter(fit.slackScale))\n        line("noiseScale", parameter(fit.noiseScale))\n        line("approximationVersion", text(fit.approximationVersion))\n        line("laplaceValidBasePosteriorMass", optionalDouble(fit.laplaceValidBasePosteriorMass))\n        line("laplaceFiniteDifferenceStep", optionalDouble(fit.laplaceFiniteDifferenceStep))\n        line("posteriorEffectiveNodeCount", fit.posteriorEffectiveNodeCount.toString())\n        line("warnings", fit.warnings.sorted().joinToString(",") { text(it) })\n        line("selectedObservationIds", fit.selectedObservationIds.joinToString(",") { text(it) })\n        line("selectedSessionIds", fit.selectedSessionIds.joinToString(",") { text(it) })\n        line("mathFamily", text(fit.mathematicalModelIdentity.family))\n        line("mathVersion", text(fit.mathematicalModelIdentity.semanticVersion))\n        line("mathDefinition", text(fit.mathematicalModelIdentity.definition))\n        val diagnostics = fit.solverDiagnostics\n        line("solverFamily", diagnostics.solverIdentity.solverFamily.storageValue)\n        line("solverVersion", text(diagnostics.solverIdentity.semanticVersion))\n        line("computeBackend", diagnostics.solverIdentity.computeBackend.storageValue)\n        line("deterministicReplay", diagnostics.solverIdentity.deterministicReplay.toString())\n        line("solverApproximation", text(diagnostics.solverIdentity.approximationDefinition))\n        line("posteriorRepresentation", diagnostics.posteriorRepresentation.storageValue)\n        line("approximationFailure", diagnostics.approximationFailure?.let(::text) ?: "-")\n        line("posteriorNodes", fit.posteriorNodes.joinToString(";") { node ->\n            listOf(\n                node.logFrontierAtLatestSession,\n                node.slope,\n                node.frontierTrend,\n                node.slackScale,\n                node.noiseScale,\n                node.posteriorWeight,\n            ).joinToString(",")\n        })\n        line("observationSlack", fit.observationSlack.joinToString(";") { encodeSlack(it) })\n    }.trimEnd('\\n')\n\n    fun decode(\n''',
)

# ---------------------------------------------------------------------------
# 3) Best-effort platform thermal/runtime telemetry, kept outside inference
# state and deterministic equality.
# ---------------------------------------------------------------------------
Path('app/src/main/java/dev/kian/mymettle/developer/NBioAdaptiveRuntimeTelemetry.kt').write_text(r'''package dev.kian.mymettle.developer

import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Diagnostic-only runtime context for one physical N-BIO adaptive-acceptance run. */
data class NBioAdaptiveRuntimeInfo(
    val supportedAbis: List<String>,
    val availableProcessors: Int,
    val maxJavaHeapBytes: Long,
    val initialPowerSaveMode: Boolean?,
    val thermalHeadroomThresholds: Map<String, Double>?,
    val thermalHeadroomThresholdsUnavailableReason: String?,
)

data class NBioAdaptiveThermalSnapshot(
    val sequence: Int,
    val stage: String,
    val capturedAt: Instant,
    val thermalStatus: Int?,
    val thermalStatusLabel: String?,
    val thermalHeadroom: Double?,
    val thermalHeadroomUnavailableReason: String?,
    val powerSaveMode: Boolean?,
    val interactive: Boolean?,
)

data class NBioAdaptiveProfileTiming(
    val historicalBakeoffWallElapsedMillis: Long,
    val currentEvaluationWallElapsedMillis: Long,
    val frozenV1FitElapsedMillis: Long?,
    val replayFrozenV1FitElapsedMillis: Long?,
    val densePersistenceReloadElapsedMillis: Long?,
    val sparsePersistenceReloadElapsedMillis: Long?,
    val laplacePersistenceReloadElapsedMillis: Long?,
    val denseReplayElapsedMillis: Long?,
    val sparseReplayElapsedMillis: Long?,
    val laplaceReplayElapsedMillis: Long?,
    val sparseFidelityElapsedMillis: Long?,
    val laplaceFidelityElapsedMillis: Long?,
) {
    val persistenceReloadTotalMillis: Long
        get() = listOfNotNull(
            densePersistenceReloadElapsedMillis,
            sparsePersistenceReloadElapsedMillis,
            laplacePersistenceReloadElapsedMillis,
        ).sum()
    val replayTotalMillis: Long
        get() = listOfNotNull(denseReplayElapsedMillis, sparseReplayElapsedMillis, laplaceReplayElapsedMillis).sum()
    val fidelityTotalMillis: Long
        get() = listOfNotNull(sparseFidelityElapsedMillis, laplaceFidelityElapsedMillis).sum()
}

class NBioAdaptiveRuntimeTelemetry(context: Context) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var lastHeadroomSampleNanos: Long = Long.MIN_VALUE

    fun runtimeInfo(): NBioAdaptiveRuntimeInfo {
        val thresholds: Map<String, Double>?
        val thresholdFailure: String?
        if (Build.VERSION.SDK_INT < 35) {
            thresholds = null
            thresholdFailure = "API_LT_35"
        } else if (powerManager == null) {
            thresholds = null
            thresholdFailure = "POWER_MANAGER_UNAVAILABLE"
        } else {
            val result = runCatching {
                powerManager.thermalHeadroomThresholds
                    .toSortedMap()
                    .mapKeys { thermalStatusLabel(it.key) }
                    .mapValues { it.value.toDouble() }
            }
            thresholds = result.getOrNull()
            thresholdFailure = result.exceptionOrNull()?.let { "${it::class.java.simpleName}:${it.message ?: "unknown"}" }
        }
        return NBioAdaptiveRuntimeInfo(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            maxJavaHeapBytes = Runtime.getRuntime().maxMemory(),
            initialPowerSaveMode = powerManager?.isPowerSaveMode,
            thermalHeadroomThresholds = thresholds,
            thermalHeadroomThresholdsUnavailableReason = thresholdFailure,
        )
    }

    fun snapshot(sequence: Int, stage: String): NBioAdaptiveThermalSnapshot {
        val status = if (Build.VERSION.SDK_INT >= 29 && powerManager != null) {
            runCatching { powerManager.currentThermalStatus }.getOrNull()
        } else null

        val nowNanos = System.nanoTime()
        val headroomResult: Result<Double>? = when {
            Build.VERSION.SDK_INT < 30 -> null
            powerManager == null -> null
            lastHeadroomSampleNanos != Long.MIN_VALUE && nowNanos - lastHeadroomSampleNanos < 1_000_000_000L -> null
            else -> {
                lastHeadroomSampleNanos = nowNanos
                runCatching { powerManager.getThermalHeadroom(0).toDouble() }
            }
        }
        val rawHeadroom = headroomResult?.getOrNull()
        val headroom = rawHeadroom?.takeIf { it.isFinite() }
        val headroomReason = when {
            Build.VERSION.SDK_INT < 30 -> "API_LT_30"
            powerManager == null -> "POWER_MANAGER_UNAVAILABLE"
            headroomResult == null -> "NOT_SAMPLED_PLATFORM_RATE_GUIDANCE"
            headroomResult.isFailure -> headroomResult.exceptionOrNull()?.let { "${it::class.java.simpleName}:${it.message ?: "unknown"}" }
            rawHeadroom == null || !rawHeadroom.isFinite() -> "UNSUPPORTED_OR_NAN"
            else -> null
        }
        return NBioAdaptiveThermalSnapshot(
            sequence = sequence,
            stage = stage,
            capturedAt = Instant.now(),
            thermalStatus = status,
            thermalStatusLabel = status?.let(::thermalStatusLabel),
            thermalHeadroom = headroom,
            thermalHeadroomUnavailableReason = headroomReason,
            powerSaveMode = powerManager?.isPowerSaveMode,
            interactive = powerManager?.isInteractive,
        )
    }

    private fun thermalStatusLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "UNKNOWN_$status"
    }
}

internal fun NBioAdaptiveRuntimeInfo.toJson(): JSONObject = JSONObject()
    .put("supportedAbis", JSONArray(supportedAbis))
    .put("availableProcessors", availableProcessors)
    .put("maxJavaHeapBytes", maxJavaHeapBytes)
    .put("initialPowerSaveMode", initialPowerSaveMode ?: JSONObject.NULL)
    .put(
        "thermalHeadroomThresholds",
        thermalHeadroomThresholds?.let(::JSONObject) ?: JSONObject.NULL,
    )
    .put(
        "thermalHeadroomThresholdsUnavailableReason",
        thermalHeadroomThresholdsUnavailableReason ?: JSONObject.NULL,
    )

internal fun NBioAdaptiveThermalSnapshot.toJson(): JSONObject = JSONObject()
    .put("sequence", sequence)
    .put("stage", stage)
    .put("capturedAt", capturedAt.toString())
    .put("thermalStatus", thermalStatus ?: JSONObject.NULL)
    .put("thermalStatusLabel", thermalStatusLabel ?: JSONObject.NULL)
    .put("thermalHeadroom", thermalHeadroom ?: JSONObject.NULL)
    .put("thermalHeadroomUnavailableReason", thermalHeadroomUnavailableReason ?: JSONObject.NULL)
    .put("powerSaveMode", powerSaveMode ?: JSONObject.NULL)
    .put("interactive", interactive ?: JSONObject.NULL)

internal fun NBioAdaptiveProfileTiming.toJson(): JSONObject = JSONObject()
    .put("historicalBakeoffWallElapsedMillis", historicalBakeoffWallElapsedMillis)
    .put("currentEvaluationWallElapsedMillis", currentEvaluationWallElapsedMillis)
    .put("frozenV1FitElapsedMillis", frozenV1FitElapsedMillis ?: JSONObject.NULL)
    .put("replayFrozenV1FitElapsedMillis", replayFrozenV1FitElapsedMillis ?: JSONObject.NULL)
    .put("densePersistenceReloadElapsedMillis", densePersistenceReloadElapsedMillis ?: JSONObject.NULL)
    .put("sparsePersistenceReloadElapsedMillis", sparsePersistenceReloadElapsedMillis ?: JSONObject.NULL)
    .put("laplacePersistenceReloadElapsedMillis", laplacePersistenceReloadElapsedMillis ?: JSONObject.NULL)
    .put("persistenceReloadTotalMillis", persistenceReloadTotalMillis)
    .put("denseReplayElapsedMillis", denseReplayElapsedMillis ?: JSONObject.NULL)
    .put("sparseReplayElapsedMillis", sparseReplayElapsedMillis ?: JSONObject.NULL)
    .put("laplaceReplayElapsedMillis", laplaceReplayElapsedMillis ?: JSONObject.NULL)
    .put("replayTotalMillis", replayTotalMillis)
    .put("sparseFidelityElapsedMillis", sparseFidelityElapsedMillis ?: JSONObject.NULL)
    .put("laplaceFidelityElapsedMillis", laplaceFidelityElapsedMillis ?: JSONObject.NULL)
    .put("fidelityTotalMillis", fidelityTotalMillis)
''')

# ---------------------------------------------------------------------------
# 4) Acceptance report: richer timing decomposition, run order, representative
# dense sampling and thermal snapshots. No candidate mathematics are changed.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/dev/kian/mymettle/developer/NBioAdaptiveInferenceAcceptance.kt'
p = Path(path)
s = p.read_text()
s = s.replace('private const val DENSE_REFERENCE_SAMPLE_LIMIT = 2', 'private const val DENSE_REFERENCE_SAMPLE_LIMIT = 3', 1)
s = s.replace('import kotlin.system.measureTimeMillis\n', 'import kotlin.math.abs\nimport kotlin.math.ln\nimport kotlin.system.measureTimeMillis\n', 1)

profile_start = s.index('data class NBioAdaptiveProfileResult(')
profile_end = s.index('\n\ndata class NBioAdaptiveInferenceAcceptanceReport(', profile_start)
profile_block = '''data class NBioAdaptiveProfileResult(
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
)'''
s = s[:profile_start] + profile_block + s[profile_end:]

replace_anchor = '''    val solverSubstrateBenchmark: InferenceSolverSubstrateBenchmarkResult,
    val backupRoundTrip: NBio7BBackupRoundTripResult,
    val javaHeapUsedBeforeBytes: Long,
'''
replace_value = '''    val solverSubstrateBenchmark: InferenceSolverSubstrateBenchmarkResult,
    val backupRoundTrip: NBio7BBackupRoundTripResult,
    val runtimeInfo: NBioAdaptiveRuntimeInfo,
    val thermalTimeline: List<NBioAdaptiveThermalSnapshot>,
    val initialPreparationElapsedMillis: Long,
    val solverSubstrateBenchmarkElapsedMillis: Long,
    val backupRoundTripElapsedMillis: Long,
    val postSafetyFingerprintElapsedMillis: Long,
    val javaHeapUsedBeforeBytes: Long,
'''
if replace_anchor not in s:
    raise SystemExit('report timing field anchor not found')
s = s.replace(replace_anchor, replace_value, 1)
s = s.replace('.put("formatVersion", 6)', '.put("formatVersion", 7)', 1)
s = s.replace('.put("strategy", "CURRENT_POSTERIOR_TOP_SUPPORT_PROFILE_SIDE_V1")', '.put("strategy", "REPRESENTATIVE_CURRENT_POSTERIOR_STABLE_PROGRESSING_NUMERICAL_STRESS_V2")', 1)
s = s.replace('.put("maximumSampledProfileSides", DENSE_REFERENCE_SAMPLE_LIMIT)', '.put("maximumSampledProfileSides", DENSE_REFERENCE_SAMPLE_LIMIT)\n                .put("selectionHeuristic", "diagnostic-only raw session log-resistance trajectory; prioritises stable, strongest progressing, and known/practical numerical-stress history; not used by model likelihood or priors")', 1)
s = s.replace('Dense fidelity is sampled on the richest current profile/side posteriors.', 'Dense fidelity is sampled on a small representative set spanning stable, strongly progressing and numerical-stress histories.', 1)

perf_anchor = '''    val scientificEvaluationNonVacuous get() = profiles.any { it.eligibleObservationCount > 0 }

    fun toJson(): String = JSONObject()
'''
perf_insert = '''    val scientificEvaluationNonVacuous get() = profiles.any { it.eligibleObservationCount > 0 }

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
'''
if perf_anchor not in s:
    raise SystemExit('performance function anchor not found')
s = s.replace(perf_anchor, perf_insert, 1)
s = s.replace(
    '''        .put("performance", JSONObject().put("totalElapsedMillis", totalElapsedMillis))\n''',
    '''        .put("runtime", runtimeInfo.toJson())\n        .put("thermalTimeline", JSONArray(thermalTimeline.map { it.toJson() }))\n        .put("performance", performanceJson())\n''',
    1,
)

run_start = s.index('    suspend fun run(\n')
run_end = s.index('    private suspend fun evaluateCurrentProfile(', run_start)
new_run = r'''    suspend fun run(
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

'''
s = s[:run_start] + new_run + s[run_end:]

current_start = s.index('    private suspend fun evaluateCurrentProfile(')
current_end = s.index('    private fun fitFrozenV1(', current_start)
new_current = r'''    private suspend fun evaluateCurrentProfile(
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

'''
s = s[:current_start] + new_current + s[current_end:]

s = s.replace(
    '''            DynamicTrendCapabilityParameterCodec.encode(fit) == DynamicTrendCapabilityParameterCodec.encode(loaded) &&\n                summariesEquivalent(before, after)\n''',
    '''            DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(fit, loaded) &&\n                summariesEquivalent(before, after)\n''',
    1,
)
s = s.replace(
    '''        return DynamicTrendCapabilityParameterCodec.encode(original) == DynamicTrendCapabilityParameterCodec.encode(replayed) &&\n            summariesEquivalent(solverPrediction(solver, original, repetitions), solverPrediction(solver, replayed, repetitions))\n''',
    '''        return DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(original, replayed) &&\n            summariesEquivalent(solverPrediction(solver, original, repetitions), solverPrediction(solver, replayed, repetitions))\n''',
    1,
)

# Insert representative dense-selection helper before fitFrozenV1.
helper_marker = '    private fun fitFrozenV1(\n'
helper_pos = s.index(helper_marker)
helper = r'''    private fun selectDenseReferenceReasons(
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

'''
s = s[:helper_pos] + helper + s[helper_pos:]

# Replace CurrentProfileEvaluation and add timing helpers.
current_data_start = s.index('    private data class CurrentProfileEvaluation(')
current_data_end = s.index('    companion object {', current_data_start)
current_data = r'''    private data class CurrentProfileEvaluation(
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

'''
s = s[:current_data_start] + current_data + s[current_data_end:]

# Profile JSON: run order, selection reason, thermal and timing decomposition.
s = s.replace(
    '''private fun NBioAdaptiveProfileResult.toJson(): JSONObject = JSONObject()\n    .put("executionProfileVersionId", executionProfileVersionId)\n''',
    '''private fun NBioAdaptiveProfileResult.toJson(): JSONObject = JSONObject()\n    .put("workloadOrder", workloadOrder)\n    .put("executionProfileVersionId", executionProfileVersionId)\n''',
    1,
)
s = s.replace(
    '''    .put("denseReferenceSampled", denseReferenceSampled)\n    .put("retrospectiveProtocolVersion", bakeoff.protocolVersion)\n''',
    '''    .put("denseReferenceSampled", denseReferenceSampled)\n    .put("denseReferenceSelectionReason", denseReferenceSelectionReason ?: JSONObject.NULL)\n    .put("retrospectiveProtocolVersion", bakeoff.protocolVersion)\n    .put("timing", timing.toJson())\n    .put("thermalBefore", thermalBefore.toJson())\n    .put("thermalAfter", thermalAfter.toJson())\n''',
    1,
)
# Serialize measured historical predictive-scoring timings.
s = s.replace(
    '''    .put("v1", bakeoff.v1PredictiveMetrics.toJson(bakeoff.v1ValidationSummary, bakeoff.v1Verdict.storageValue, bakeoff.v1FitElapsedMillis))\n''',
    '''    .put(\n        "v1",\n        bakeoff.v1PredictiveMetrics.toJson(\n            bakeoff.v1ValidationSummary,\n            bakeoff.v1Verdict.storageValue,\n            bakeoff.v1FitElapsedMillis,\n            bakeoff.v1PredictiveScoringElapsedMillis,\n        ),\n    )\n''',
    1,
)
s = s.replace(
    '''                        candidate.developmentComparisonAgainstV1.verdict.storageValue,\n                        candidate.extensionWallElapsedMillis,\n                    )\n''',
    '''                        candidate.developmentComparisonAgainstV1.verdict.storageValue,\n                        candidate.extensionWallElapsedMillis,\n                        candidate.predictiveScoringElapsedMillis,\n                    )\n''',
    1,
)
s = s.replace(
    '''    verdict: String,\n    fitRuntimeMillis: Long,\n): JSONObject = JSONObject()\n''',
    '''    verdict: String,\n    fitRuntimeMillis: Long,\n    predictiveScoringRuntimeMillis: Long,\n): JSONObject = JSONObject()\n''',
    1,
)
s = s.replace(
    '''    .put("fitRuntimeMillis", fitRuntimeMillis)\n\nprivate fun DynamicTrendPosteriorFidelityResult.toJson(): JSONObject = JSONObject()\n''',
    '''    .put("fitRuntimeMillis", fitRuntimeMillis)\n    .put("predictiveScoringRuntimeMillis", predictiveScoringRuntimeMillis)\n\nprivate fun DynamicTrendPosteriorFidelityResult.toJson(): JSONObject = JSONObject()\n''',
    1,
)

# Fidelity export now includes explicit tails/next-session quantiles and covariance details.
s = s.replace(
    '''    .put("trendPositiveProbabilityAbsoluteError", trendPositiveProbabilityAbsoluteError)\n    .put("nextFrontierMedianRelativeError", nextFrontierMedianRelativeError)\n''',
    '''    .put("referenceTrendPositiveProbability", referenceTrendPositiveProbability)\n    .put("candidateTrendPositiveProbability", candidateTrendPositiveProbability)\n    .put("trendPositiveProbabilityAbsoluteError", trendPositiveProbabilityAbsoluteError)\n    .put("referenceNextFrontierP05Kg", referenceNextFrontierP05Kg)\n    .put("referenceNextFrontierP50Kg", referenceNextFrontierP50Kg)\n    .put("referenceNextFrontierP95Kg", referenceNextFrontierP95Kg)\n    .put("candidateNextFrontierP05Kg", candidateNextFrontierP05Kg)\n    .put("candidateNextFrontierP50Kg", candidateNextFrontierP50Kg)\n    .put("candidateNextFrontierP95Kg", candidateNextFrontierP95Kg)\n    .put("nextFrontierMedianRelativeError", nextFrontierMedianRelativeError)\n''',
    1,
)
s = s.replace(
    '''    .put(\n        "marginals",\n        JSONArray(\n            marginals.map { marginal ->\n                JSONObject()\n                    .put("parameter", marginal.parameter)\n                    .put("referenceP05", marginal.referenceP05)\n                    .put("referenceP50", marginal.referenceP50)\n                    .put("referenceP95", marginal.referenceP95)\n                    .put("candidateP05", marginal.candidateP05)\n                    .put("candidateP50", marginal.candidateP50)\n                    .put("candidateP95", marginal.candidateP95)\n                    .put("referenceVariance", marginal.referenceVariance)\n                    .put("candidateVariance", marginal.candidateVariance)\n                    .put("quantileWasserstein1", marginal.quantileWasserstein1)\n                    .put("standardisedQuantileWasserstein1", marginal.standardisedQuantileWasserstein1)\n            },\n        ),\n    )\n\nprivate fun InferenceSolverSubstrateBenchmarkResult.toJson(): JSONObject = JSONObject()\n''',
    '''    .put(\n        "marginals",\n        JSONArray(\n            marginals.map { marginal ->\n                JSONObject()\n                    .put("parameter", marginal.parameter)\n                    .put("referenceP05", marginal.referenceP05)\n                    .put("referenceP50", marginal.referenceP50)\n                    .put("referenceP95", marginal.referenceP95)\n                    .put("candidateP05", marginal.candidateP05)\n                    .put("candidateP50", marginal.candidateP50)\n                    .put("candidateP95", marginal.candidateP95)\n                    .put("referenceVariance", marginal.referenceVariance)\n                    .put("candidateVariance", marginal.candidateVariance)\n                    .put("quantileWasserstein1", marginal.quantileWasserstein1)\n                    .put("standardisedQuantileWasserstein1", marginal.standardisedQuantileWasserstein1)\n            },\n        ),\n    )\n    .put(\n        "covariances",\n        JSONArray(\n            covariances.map { covariance ->\n                JSONObject()\n                    .put("leftParameter", covariance.leftParameter)\n                    .put("rightParameter", covariance.rightParameter)\n                    .put("referenceCovariance", covariance.referenceCovariance)\n                    .put("candidateCovariance", covariance.candidateCovariance)\n                    .put("absoluteError", covariance.absoluteError)\n                    .put("correlationScaleError", covariance.correlationScaleError ?: JSONObject.NULL)\n            },\n        ),\n    )\n\nprivate fun InferenceSolverSubstrateBenchmarkResult.toJson(): JSONObject = JSONObject()\n''',
    1,
)

p.write_text(s)

# ---------------------------------------------------------------------------
# 5) Regression tests: exact conditional-Laplace identity propagation and
# telemetry-insensitive deterministic scientific replay equality.
# ---------------------------------------------------------------------------
path = 'app/src/test/java/dev/kian/mymettle/engine/performance/DynamicTrendFrontierModelTest.kt'
replace_once(
    path,
    'import dev.kian.mymettle.domain.inference.InferenceSolverFamily\n',
    'import dev.kian.mymettle.domain.inference.InferencePosteriorRepresentation\nimport dev.kian.mymettle.domain.inference.InferenceSolverFamily\nimport dev.kian.mymettle.engine.inference.DynamicTrendConditionalLaplaceSolverAdapter\n',
)
anchor = '''    @Test\n    fun `math identity is shared while dense and conditional Laplace solver identities are distinct`() {\n'''
test = '''    @Test\n    fun `conditional Laplace fit carries exact configured mathematical and solver identities`() {\n        val evidence = generated(sessions = 5, trend = 0.025, repsBySession = { listOf(8) })\n        val projection = projection(evidence)\n        val horizon = evidence.maxOf { it.completedAt }\n        val base = DynamicStochasticFrontierModel(TEST_MATH_CONFIG.baseConfig).fit(\n            DynamicCapabilityFitRequest(\n                projection,\n                horizon,\n                TEST_MATH_CONFIG.baseConfig.toModelConfig(CONFIG_CREATED_AT),\n            ),\n        )\n        val model = DynamicTrendFrontierModel(TEST_MATH_CONFIG)\n        val solver = DynamicTrendConditionalLaplaceSolverAdapter(model)\n        val fit = solver.fitFromFrozenV1(\n            DynamicCapabilityFitRequest(projection, horizon, solver.modelConfig(CONFIG_CREATED_AT)),\n            base,\n        )\n        assertEquals(solver.mathematicalModelIdentity, fit.mathematicalModelIdentity)\n        assertEquals(solver.solverIdentity, fit.solverDiagnostics.solverIdentity)\n        assertEquals(InferencePosteriorRepresentation.WEIGHTED_DENSE_NODES, fit.solverDiagnostics.posteriorRepresentation)\n        assertTrue(fit.solverDiagnostics.updateRuntimeNanos != null)\n    }\n\n'''
replace_once(path, anchor, test + anchor)

path = 'app/src/test/java/dev/kian/mymettle/inference/DynamicTrendCapabilityParameterCodecTest.kt'
replace_once(
    path,
    'import kotlin.test.assertFailsWith\n',
    'import kotlin.test.assertFalse\nimport kotlin.test.assertFailsWith\nimport kotlin.test.assertNotEquals\nimport kotlin.test.assertTrue\n',
)
anchor = '''    @Test\n    fun `unknown Candidate v2 parameter schema fails closed`() {\n'''
tests = '''    @Test\n    fun `scientific replay equality ignores runtime and operational telemetry`() {\n        val fit = fixture()\n        val replay = fit.copy(\n            solverDiagnostics = fit.solverDiagnostics.copy(\n                evaluatedNodeCount = 999_999,\n                effectiveNodeCount = 77.0,\n                updateRuntimeNanos = 9_999_999,\n                peakWorkingBytes = 999_999_999,\n                notes = setOf("different_worker_count", "different_hardware_sample"),\n            ),\n        )\n        assertNotEquals(DynamicTrendCapabilityParameterCodec.encode(fit), DynamicTrendCapabilityParameterCodec.encode(replay))\n        assertTrue(DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(fit, replay))\n    }\n\n    @Test\n    fun `scientific replay equality rejects posterior config and solver mismatches`() {\n        val fit = fixture()\n        val posteriorMismatch = fit.copy(\n            posteriorNodes = fit.posteriorNodes.map { it.copy(slope = it.slope + 0.01) },\n        )\n        assertFalse(DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(fit, posteriorMismatch))\n\n        val otherConfig = ModelConfigId("candidate-v2-codec-test-other")\n        val configMismatch = fit.copy(\n            modelConfigId = otherConfig,\n            frontierAtLatestSession = fit.frontierAtLatestSession.copy(\n                provenance = fit.frontierAtLatestSession.provenance.copy(modelConfigId = otherConfig),\n            ),\n        )\n        assertFalse(DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(fit, configMismatch))\n\n        val solverMismatch = fit.copy(\n            solverDiagnostics = fit.solverDiagnostics.copy(\n                solverIdentity = fit.solverDiagnostics.solverIdentity.copy(semanticVersion = "codec-test-solver-v2"),\n            ),\n        )\n        assertFalse(DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(fit, solverMismatch))\n    }\n\n'''
replace_once(path, anchor, tests + anchor)

# ---------------------------------------------------------------------------
# 6) Preserve the physical acceptance checkpoint as requested. This is a
# checkpoint within N-BIO-7B.X, not a new roadmap phase.
# ---------------------------------------------------------------------------
Path('docs/n-bio-vnext/NBIO_7BX_ADAPTIVE_ACCEPTANCE_CHECKPOINT_2026-09-01.md').write_text(r'''# N-BIO-7B.X adaptive-inference physical checkpoint — 2026-09-01

This remains part of the existing **N-BIO-7B.X adaptive-inference architecture consolidation mission**. It is not 7C, Candidate v3, V8 or a new physiology phase.

## Latest completed physical Room14 run

The pre-observability physical acceptance run completed the full installed-device workflow after the fail-closed numerical projection patch.

Preserved interpretation:

- **Safety / integrity:** PASS.
- **Unstable-projection fail-closed behaviour:** VALIDATED.
- **Adaptive Sparse:** PROMISING development evidence only.
- **Conditional Laplace:** solver-specific numerical instability plus identity-propagation issue under investigation.
- **Replay result:** INVALIDATED BY COMPARATOR BUG because operational runtime telemetry participated in equality.
- **Runtime comparison:** NOT YET CLEANLY INTERPRETABLE because predictive-scoring timing was omitted and run order / thermal conditions were not captured.
- **N-BIO-7B.X closure:** PENDING corrected physical acceptance.

The run was performed on the real Room14 dataset under normal phone thermal constraints; screen recording was active during the run. Wall-clock timing must therefore not be treated as a clean solver benchmark.

## Bounded correction pass

This checkpoint authorises only acceptance-harness correctness and observability work:

- exact mathematical-model and solver-identity propagation;
- deterministic scientific replay equality that excludes runtime / hardware telemetry;
- predictive-scoring and per-stage timing export;
- run-order and best-effort Android thermal telemetry;
- representative bounded Dense sampling across stable, strongly progressing and numerical-stress histories;
- stronger exported Dense fidelity details.

Candidate-v2 mathematics, priors, evidence policy, Adaptive-Sparse algorithm and the fail-closed numerical-domain guard remain frozen in this pass. BENCHMARK/product authority, raw evidence, prescription state, Room14, Native backup semantics and normal workout behaviour remain unchanged.
''')

# Guardrail: preserve the validated numerical-domain fail-closed text exactly.
guard_path = Path('app/src/main/java/dev/kian/mymettle/engine/performance/DynamicTrendFrontierModel.kt')
guard_text = guard_path.read_text()
required_guard = 'Conditional-Laplace Candidate-v2 projection left the configured numerical resistance domain; approximation is unavailable for this horizon.'
if required_guard not in guard_text:
    raise SystemExit('validated numerical-domain guard was altered or removed')

print('N-BIO-7B.X acceptance correctness + observability patch applied')
