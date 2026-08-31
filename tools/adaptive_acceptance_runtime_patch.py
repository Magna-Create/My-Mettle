from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
acceptance = ROOT / "app/src/main/java/dev/kian/mymettle/developer/NBioAdaptiveInferenceAcceptance.kt"
screen = ROOT / "app/src/main/java/dev/kian/mymettle/ui/BiologyDeveloperScreen.kt"
gradle = ROOT / "app/build.gradle.kts"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


text = acceptance.read_text()
text = replace_once(
    text,
    "import org.json.JSONObject\n",
    "import org.json.JSONObject\n\nprivate const val DENSE_REFERENCE_SAMPLE_LIMIT = 2\n",
    "dense sample constant",
)
text = replace_once(
    text,
    "    val chronologicalFitCount: Int,\n    val bakeoff: DynamicTrendSolverHistoricalBakeoffResult,\n",
    "    val chronologicalFitCount: Int,\n    val denseReferenceSampled: Boolean,\n    val bakeoff: DynamicTrendSolverHistoricalBakeoffResult,\n",
    "profile dense sampled field",
)
text = replace_once(text, '.put("formatVersion", 3)', '.put("formatVersion", 4)', "format v4")
text = replace_once(
    text,
    "        .put(\"candidateV2SequentialReuseAssessment\", sequentialReuseAssessment.toJson())\n",
    "        .put(\n            \"denseReferenceSampling\",\n            JSONObject()\n                .put(\"strategy\", \"CURRENT_POSTERIOR_TOP_SUPPORT_PROFILE_SIDE_V1\")\n                .put(\"maximumSampledProfileSides\", DENSE_REFERENCE_SAMPLE_LIMIT)\n                .put(\"historicalDenseScoring\", false)\n                .put(\"reason\", \"Dense Candidate-v2 remains the high-fidelity oracle, but full-history dense scoring is intentionally bounded after physical alpha25 exposed multi-minute single-profile runtime. Full chronology uses same-mathematics adaptive-sparse plus conditional-Laplace; dense fidelity is sampled on the richest current profile/side posteriors.\"),\n        )\n        .put(\"candidateV2SequentialReuseAssessment\", sequentialReuseAssessment.toJson())\n",
    "dense sampling report",
)
text = replace_once(
    text,
    '.put("denseCandidateV2Reference", "IMPLEMENTED_CURRENT_HISTORY_DEVICE_ACCEPTANCE")',
    '.put("denseCandidateV2Reference", "IMPLEMENTED_BOUNDED_CURRENT_POSTERIOR_ORACLE;FULL_HISTORICAL_DENSE_SCORING_DISABLED_AFTER_DEVICE_RUNTIME_EVIDENCE")',
    "architecture dense label",
)

old_runner = '''        val groups = raw.profiles.values.flatMap { descriptor ->
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
'''
new_runner = '''        val groups = raw.profiles.values.flatMap { descriptor ->
            currentAsKnown
                .filter { it.executionProfileVersionId == descriptor.semantics.executionProfileVersionId }
                .map { it.laterality }
                .distinct()
                .map { side -> descriptor to side }
        }
        val denseSolver = DynamicTrendDenseReferenceSolverAdapter()
        val sparseSolver = DynamicTrendAdaptiveSparseSolver()
        val laplaceSolver = DynamicTrendConditionalLaplaceSolverAdapter()
        val projectedGroups = groups.map { (descriptor, side) ->
            Triple(
                descriptor,
                side,
                DynamicResistanceEvidenceProjector.project(
                    descriptor.semantics,
                    side,
                    currentAsKnown,
                    DynamicResistanceV2Contract.evidencePolicy,
                ),
            )
        }
        val denseReferenceKeys = projectedGroups
            .filter { it.third.independentSessionCount >= DynamicTrendFrontierV2.config.trendMinimumIndependentSessionsToLearn }
            .sortedWith(
                compareByDescending<Triple<NBio7BProfileDescriptor, dev.kian.mymettle.domain.performance.Laterality, DynamicResistanceEvidenceProjection>> {
                    it.third.independentSessionCount
                }.thenByDescending { it.third.evidence.size },
            )
            .take(DENSE_REFERENCE_SAMPLE_LIMIT)
            .map { "${it.first.semantics.executionProfileVersionId.value}|${it.second.storageValue}" }
            .toSet()
        // Run inexpensive/full-history challengers first so physical progress is visible quickly;
        // the bounded dense oracle samples are deliberately pushed to the tail of the action.
        val orderedGroups = projectedGroups.sortedBy { group ->
            if ("${group.first.semantics.executionProfileVersionId.value}|${group.second.storageValue}" in denseReferenceKeys) 1 else 0
        }
        val results = mutableListOf<NBioAdaptiveProfileResult>()

        orderedGroups.forEachIndexed { index, (descriptor, side, projection) ->
            val key = "${descriptor.semantics.executionProfileVersionId.value}|${side.storageValue}"
            val includeDenseReference = key in denseReferenceKeys
            onProgress(
                NBio7BAcceptanceProgress(
                    index,
                    orderedGroups.size + 2,
                    "Adaptive inference · ${descriptor.label} · ${side.storageValue} · full-history sparse + Laplace",
                ),
            )
            val bakeoff = DynamicTrendSolverHistoricalBakeoffCorrected(
                listOf(sparseSolver, laplaceSolver),
            ).evaluate(
                descriptor.semantics,
                side,
                raw.revisions,
            )
            onProgress(
                NBio7BAcceptanceProgress(
                    index,
                    orderedGroups.size + 2,
                    if (includeDenseReference) {
                        "Adaptive inference · ${descriptor.label} · ${side.storageValue} · current posterior + bounded dense oracle"
                    } else {
                        "Adaptive inference · ${descriptor.label} · ${side.storageValue} · current posterior checks"
                    },
                ),
            )
            val current = evaluateCurrentProfile(
                userProfileId = userProfileId,
                projection = projection,
                denseSolver = denseSolver,
                sparseSolver = sparseSolver,
                laplaceSolver = laplaceSolver,
                includeDenseReference = includeDenseReference,
            )
            results += NBioAdaptiveProfileResult(
                executionProfileVersionId = descriptor.semantics.executionProfileVersionId.value,
                label = descriptor.label,
                side = side.storageValue,
                eligibleObservationCount = projection.evidence.size,
                independentSessionCount = projection.independentSessionCount,
                chronologicalFitCount = bakeoff.chronologicalFitCount,
                denseReferenceSampled = includeDenseReference,
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
'''
text = replace_once(text, old_runner, new_runner, "bounded runner block")
text = text.replace("groups.size, groups.size + 2", "orderedGroups.size, orderedGroups.size + 2")
text = text.replace("groups.size + 1, groups.size + 2", "orderedGroups.size + 1, orderedGroups.size + 2")

old_sig = '''    private suspend fun evaluateCurrentProfile(
        userProfileId: String,
        projection: DynamicResistanceEvidenceProjection,
        denseSolver: DynamicTrendCandidateV2Solver,
        sparseSolver: DynamicTrendCandidateV2Solver,
        laplaceSolver: DynamicTrendCandidateV2Solver,
    ): CurrentProfileEvaluation {'''
new_sig = '''    private suspend fun evaluateCurrentProfile(
        userProfileId: String,
        projection: DynamicResistanceEvidenceProjection,
        denseSolver: DynamicTrendCandidateV2Solver,
        sparseSolver: DynamicTrendCandidateV2Solver,
        laplaceSolver: DynamicTrendCandidateV2Solver,
        includeDenseReference: Boolean,
    ): CurrentProfileEvaluation {'''
text = replace_once(text, old_sig, new_sig, "current profile signature")

start = text.index("        val requestDense = DynamicCapabilityFitRequest", text.index(new_sig))
end_marker = "        return CurrentProfileEvaluation(\n            sparseFidelity = sparse?.let { runCatching { DynamicTrendPosteriorFidelity.compare(dense, it) }.getOrNull() },"
end_start = text.index(end_marker, start)
end = text.index("        )\n    }", end_start) + len("        )\n")
old_current = text[start:end]
new_current = '''        val requestSparse = DynamicCapabilityFitRequest(projection, horizon, sparseSolver.modelConfig(CONFIG_CREATED_AT))
        val requestLaplace = DynamicCapabilityFitRequest(projection, horizon, laplaceSolver.modelConfig(CONFIG_CREATED_AT))
        val requestDense = if (includeDenseReference) {
            DynamicCapabilityFitRequest(projection, horizon, denseSolver.modelConfig(CONFIG_CREATED_AT))
        } else null

        var denseElapsed: Long? = null
        var sparseElapsed: Long? = null
        var laplaceElapsed: Long? = null
        var denseFit: DynamicTrendFrontierFit? = null
        var sparseFit: DynamicTrendFrontierFit? = null
        var laplaceFit: DynamicTrendFrontierFit? = null
        val limitations = mutableListOf<String>()
        runCatching {
            sparseElapsed = measureTimeMillis { sparseFit = sparseSolver.fitFromFrozenV1(requestSparse, frozenV1) }
        }.onFailure { limitations += "Adaptive-sparse current fit failed: ${it.message}" }
        runCatching {
            laplaceElapsed = measureTimeMillis { laplaceFit = laplaceSolver.fitFromFrozenV1(requestLaplace, frozenV1) }
        }.onFailure { limitations += "Conditional-Laplace current fit failed: ${it.message}" }
        if (includeDenseReference) {
            runCatching {
                denseElapsed = measureTimeMillis { denseFit = denseSolver.fitFromFrozenV1(requireNotNull(requestDense), frozenV1) }
            }.onFailure { limitations += "Dense-reference sampled current fit failed: ${it.message}" }
        } else {
            limitations += "Dense reference intentionally not run for this profile/side; physical acceptance samples only the $DENSE_REFERENCE_SAMPLE_LIMIT richest trend-enabled current posteriors after alpha25 exposed multi-minute dense runtime."
        }

        val dense = denseFit
        val sparse = sparseFit
        val laplace = laplaceFit
        val reps = (dense ?: sparse ?: laplace)?.referenceRepetitions ?: frozenV1.referenceRepetitions

        val densePersist = dense?.let { fit ->
            runCatching { persistReloadEquivalent(userProfileId, denseSolver, fit, reps) }
                .onFailure { limitations += "Dense persist/reload check failed: ${it.message}" }
                .getOrNull()
        }
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

        val replayFrozenV1 = runCatching { fitFrozenV1(projection, horizon) }
            .onFailure { limitations += "Shared Candidate-v1 replay proposal failed: ${it.message}" }
            .getOrNull()
        val denseReplay = if (dense != null && replayFrozenV1 != null) {
            runCatching { replayEquivalent(denseSolver, projection, horizon, dense, reps, replayFrozenV1) }
                .onFailure { limitations += "Dense full replay check failed: ${it.message}" }
                .getOrNull()
        } else null
        val sparseReplay = if (sparse != null && replayFrozenV1 != null) {
            runCatching { replayEquivalent(sparseSolver, projection, horizon, sparse, reps, replayFrozenV1) }
                .onFailure { limitations += "Adaptive-sparse full replay check failed: ${it.message}" }
                .getOrNull()
        } else null
        val laplaceReplay = if (laplace != null && replayFrozenV1 != null) {
            runCatching { replayEquivalent(laplaceSolver, projection, horizon, laplace, reps, replayFrozenV1) }
                .onFailure { limitations += "Conditional-Laplace full replay check failed: ${it.message}" }
                .getOrNull()
        } else null

        if (includeDenseReference && dense == null) {
            limitations += "Selected dense oracle fit is unavailable; approximation fidelity is NOT_EVALUATED rather than vacuous PASS."
        }

        return CurrentProfileEvaluation(
            sparseFidelity = if (dense != null && sparse != null) {
                runCatching { DynamicTrendPosteriorFidelity.compare(dense, sparse) }.getOrNull()
            } else null,
            laplaceFidelity = if (dense != null && laplace != null) {
                runCatching { DynamicTrendPosteriorFidelity.compare(dense, laplace) }.getOrNull()
            } else null,
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
'''
text = text[:start] + new_current + text[end:]

old_replay_sig = '''    private fun replayEquivalent(
        solver: DynamicTrendCandidateV2Solver,
        projection: DynamicResistanceEvidenceProjection,
        horizon: Instant,
        original: DynamicTrendFrontierFit,
        repetitions: Double,
    ): Boolean {
        val frozenV1 = fitFrozenV1(projection, horizon)
        val replayed = solver.fitFromFrozenV1('''
new_replay_sig = '''    private fun replayEquivalent(
        solver: DynamicTrendCandidateV2Solver,
        projection: DynamicResistanceEvidenceProjection,
        horizon: Instant,
        original: DynamicTrendFrontierFit,
        repetitions: Double,
        frozenV1: DynamicStochasticFrontierFit,
    ): Boolean {
        val replayed = solver.fitFromFrozenV1('''
text = replace_once(text, old_replay_sig, new_replay_sig, "shared replay base")
text = replace_once(
    text,
    '    .put("chronologicalFitCount", chronologicalFitCount)\n    .put("retrospectiveProtocolVersion", bakeoff.protocolVersion)\n',
    '    .put("chronologicalFitCount", chronologicalFitCount)\n    .put("denseReferenceSampled", denseReferenceSampled)\n    .put("retrospectiveProtocolVersion", bakeoff.protocolVersion)\n',
    "profile json dense sampled",
)
acceptance.write_text(text)

screen_text = screen.read_text()
screen_text = replace_once(
    screen_text,
    "Single N‑BIO‑7B.X physical acceptance over installed Room14 history. Compares dense Candidate-v2, same-mathematics adaptive sparse and conditional-Laplace solvers against the same frozen Candidate-v1 proposal, with corrected median-MAE evaluation, persistence/replay checks, solver-substrate benchmarks and safety fingerprints. No product authority is changed.",
    "Single N‑BIO‑7B.X physical acceptance over installed Room14 history. Full chronology compares same-mathematics adaptive sparse and conditional-Laplace Candidate-v2 solvers; the expensive dense tensor remains a bounded high-fidelity oracle on the richest current profile/side posteriors. Includes corrected median-MAE evaluation, shared replay, solver-substrate benchmarks and safety fingerprints. No product authority is changed.",
    "screen acceptance description",
)
screen.write_text(screen_text)

gradle_text = gradle.read_text()
gradle_text = replace_once(gradle_text, "versionCode = 24", "versionCode = 25", "version code alpha26")
gradle_text = replace_once(gradle_text, 'versionName = "0.1.0-alpha25"', 'versionName = "0.1.0-alpha26"', "version name alpha26")
gradle.write_text(gradle_text)

for path in (acceptance, screen, gradle):
    print(path.relative_to(ROOT))
