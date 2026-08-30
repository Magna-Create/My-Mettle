package dev.kian.mymettle.developer

import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.inference.DynamicCapabilityCandidateVerdict
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationSummary
import dev.kian.mymettle.domain.inference.DynamicFrontierParameterPosterior
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierEvidenceV2
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierV1
import dev.kian.mymettle.domain.inference.PosteriorSummary
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.inference.DynamicDemonstrationPredictiveEvaluator
import dev.kian.mymettle.engine.inference.DynamicResistanceHistoricalEvaluator
import dev.kian.mymettle.engine.inference.DynamicResistanceRetrospectiveEvaluator
import dev.kian.mymettle.engine.inference.DynamicStage1DiagnosticAnalyzer
import dev.kian.mymettle.engine.inference.DynamicStage1DiagnosticSummary
import dev.kian.mymettle.engine.inference.DynamicStage1ProfileDiagnostics
import dev.kian.mymettle.engine.inference.HistoricalObservationRevisionSelector
import dev.kian.mymettle.engine.performance.DynamicResistanceEvidenceProjector
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import dev.kian.mymettle.inference.DynamicCapabilityParameterCodec
import dev.kian.mymettle.inference.DynamicCapabilityShadowRepository
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.system.measureTimeMillis
import org.json.JSONArray
import org.json.JSONObject

enum class NBio7BAcceptanceStatus(val storageValue: String) {
    PASS("pass"),
    FAIL("fail"),
    INSUFFICIENT("insufficient"),
    NOT_EVALUATED("not_evaluated"),
}

enum class NBio7BIntegritySafetyVerdict(val storageValue: String) { PASS("pass"), FAIL("fail") }

enum class NBio7BEmpiricalEvaluationVerdict(val storageValue: String) {
    NOT_EVALUATED("not_evaluated"),
    INSUFFICIENT_EVIDENCE("insufficient_evidence"),
    ACCEPTABLE_FOR_SHADOW("acceptable_for_shadow"),
    ACCEPTABLE_WITH_LIMITATIONS("acceptable_with_limitations"),
    REQUIRES_NEW_CANDIDATE("requires_new_candidate"),
    REJECTED("rejected"),
}

enum class NBio7BOverallClosureVerdict(val storageValue: String) {
    PENDING_EMPIRICAL_EVALUATION("pending_empirical_evaluation"),
    READY_FOR_7B_CLOSURE("ready_for_7b_closure"),
    REQUIRES_NEW_CANDIDATE("requires_new_candidate"),
    INTEGRITY_FAILURE("integrity_failure"),
}

data class NBio7BAcceptanceProgress(
    val completedGroups: Int,
    val totalGroups: Int,
    val label: String,
)

data class NBio7BAcceptanceCheck(
    val id: String,
    val status: NBio7BAcceptanceStatus,
    val detail: String,
)

data class NBio7BRepresentativePrediction(
    val repetitions: Double,
    val frontier: PosteriorSummary,
    val demonstrationP05Kg: Double,
    val demonstrationP50Kg: Double,
    val demonstrationP95Kg: Double,
)

data class NBio7BProfileAcceptance(
    val executionProfileVersionId: String,
    val executionProfileId: String,
    val label: String,
    val side: String,
    val currentEligibleObservations: Int,
    val currentIndependentSessions: Int,
    val currentExclusions: Int,
    val currentExclusionReasonCounts: Map<String, Int>,
    val repMin: Int?,
    val repMax: Int?,
    val resistanceMinKg: Double?,
    val resistanceMaxKg: Double?,
    val referenceRepetitions: Double?,
    val frontierAtReference: PosteriorSummary?,
    val slope: DynamicFrontierParameterPosterior?,
    val slackScale: DynamicFrontierParameterPosterior?,
    val noiseScale: DynamicFrontierParameterPosterior?,
    val warnings: List<String>,
    val predictions: List<NBio7BRepresentativePrediction>,
    val validation: DynamicCapabilityValidationSummary,
    val stage1Diagnostics: DynamicStage1ProfileDiagnostics?,
    val candidateVerdict: DynamicCapabilityCandidateVerdict,
    val chronologicalFitCount: Int,
    val shadowRunId: String?,
    val parameterCodecVersion: Int?,
    val persistReloadEquivalent: Boolean?,
    val fullReplayEquivalent: Boolean?,
    val numericalValidity: Boolean?,
    val elapsedMillis: Long,
    val limitations: List<String>,
)

data class NBio7BAcceptanceReport(
    val generatedAt: Instant,
    val roomSchemaVersion: Int,
    val candidateModelVersion: String,
    val candidateModelConfigId: String,
    val validationProtocolId: String,
    val contextConsumption: String,
    val rawFingerprintBefore: NBio7BRawEvidenceFingerprint,
    val rawFingerprintAfter: NBio7BRawEvidenceFingerprint,
    val benchmarkRunIdBefore: String?,
    val benchmarkRunIdAfter: String?,
    val groupsDiscovered: Int,
    val globalExclusionReasonCounts: Map<String, Int>,
    val profiles: List<NBio7BProfileAcceptance>,
    val stage1Diagnostics: DynamicStage1DiagnosticSummary,
    val checks: List<NBio7BAcceptanceCheck>,
    val finalModelVerdict: DynamicCapabilityCandidateVerdict,
    val productAuthorityStatus: String,
    val totalChronologicalFits: Int,
    val totalElapsedMillis: Long,
    val worstProfileElapsedMillis: Long?,
) {
    val integritySafetyVerdict: NBio7BIntegritySafetyVerdict get() =
        if (checks.filter { it.id in INTEGRITY_CHECK_IDS }.any { it.status == NBio7BAcceptanceStatus.FAIL })
            NBio7BIntegritySafetyVerdict.FAIL else NBio7BIntegritySafetyVerdict.PASS

    val empiricalModelEvaluationVerdict: NBio7BEmpiricalEvaluationVerdict get() = when {
        totalChronologicalFits == 0 && profiles.none { it.frontierAtReference != null } -> NBio7BEmpiricalEvaluationVerdict.NOT_EVALUATED
        finalModelVerdict == DynamicCapabilityCandidateVerdict.REJECTED -> NBio7BEmpiricalEvaluationVerdict.REJECTED
        finalModelVerdict == DynamicCapabilityCandidateVerdict.REQUIRES_NEW_CANDIDATE -> NBio7BEmpiricalEvaluationVerdict.REQUIRES_NEW_CANDIDATE
        finalModelVerdict == DynamicCapabilityCandidateVerdict.ACCEPTABLE_WITH_LIMITATIONS -> NBio7BEmpiricalEvaluationVerdict.ACCEPTABLE_WITH_LIMITATIONS
        finalModelVerdict == DynamicCapabilityCandidateVerdict.ACCEPTABLE_FOR_SHADOW -> NBio7BEmpiricalEvaluationVerdict.ACCEPTABLE_FOR_SHADOW
        else -> NBio7BEmpiricalEvaluationVerdict.INSUFFICIENT_EVIDENCE
    }

    val overall7BClosureVerdict: NBio7BOverallClosureVerdict get() = when {
        integritySafetyVerdict == NBio7BIntegritySafetyVerdict.FAIL -> NBio7BOverallClosureVerdict.INTEGRITY_FAILURE
        empiricalModelEvaluationVerdict in setOf(
            NBio7BEmpiricalEvaluationVerdict.REJECTED,
            NBio7BEmpiricalEvaluationVerdict.REQUIRES_NEW_CANDIDATE,
        ) -> NBio7BOverallClosureVerdict.REQUIRES_NEW_CANDIDATE
        empiricalModelEvaluationVerdict in setOf(
            NBio7BEmpiricalEvaluationVerdict.ACCEPTABLE_FOR_SHADOW,
            NBio7BEmpiricalEvaluationVerdict.ACCEPTABLE_WITH_LIMITATIONS,
        ) -> NBio7BOverallClosureVerdict.READY_FOR_7B_CLOSURE
        else -> NBio7BOverallClosureVerdict.PENDING_EMPIRICAL_EVALUATION
    }

    val passed: Boolean get() = overall7BClosureVerdict == NBio7BOverallClosureVerdict.READY_FOR_7B_CLOSURE

    fun toJson(): String = JSONObject()
        .put("format", "my-mettle-n-bio-7b-acceptance")
        .put("formatVersion", 4)
        .put("generatedAt", generatedAt.toString())
        .put("roomSchemaVersion", roomSchemaVersion)
        .put("executionEnvironment", "installed_native_room14_explicit_developer_action")
        .put("candidateModelVersion", candidateModelVersion)
        .put("candidateModelConfigId", candidateModelConfigId)
        .put("candidateV1Status", "REJECTED_EMPIRICAL_CALIBRATION_V1")
        .put("validationProtocolId", validationProtocolId)
        .put("contextConsumption", contextConsumption)
        .put("rawEvidence", JSONObject()
            .put("beforeSha256", rawFingerprintBefore.sha256)
            .put("afterSha256", rawFingerprintAfter.sha256)
            .put("unchanged", rawFingerprintBefore == rawFingerprintAfter)
            .put("tableRowCounts", JSONObject(rawFingerprintAfter.tableRowCounts)))
        .put("benchmarkAuthority", JSONObject()
            .put("beforeRunId", benchmarkRunIdBefore ?: JSONObject.NULL)
            .put("afterRunId", benchmarkRunIdAfter ?: JSONObject.NULL)
            .put("unchanged", benchmarkRunIdBefore == benchmarkRunIdAfter)
            .put("productAuthorityStatus", productAuthorityStatus))
        .put("performance", JSONObject()
            .put("groupsDiscovered", groupsDiscovered)
            .put("chronologicalFits", totalChronologicalFits)
            .put("exclusionReasonCounts", JSONObject(globalExclusionReasonCounts))
            .put("totalElapsedMillis", totalElapsedMillis)
            .put("worstProfileElapsedMillis", worstProfileElapsedMillis ?: JSONObject.NULL))
        .put("checks", JSONArray(checks.map { it.toJson() }))
        .put("stage1Diagnostics", stage1Diagnostics.toStage1Json())
        .put("profiles", JSONArray(profiles.map { it.toJson() }))
        .put("finalModelVerdict", finalModelVerdict.storageValue)
        .put("verdicts", JSONObject()
            .put("integritySafety", integritySafetyVerdict.storageValue)
            .put("empiricalModelEvaluation", empiricalModelEvaluationVerdict.storageValue)
            .put("overall7BClosure", overall7BClosureVerdict.storageValue))
        .put("passed", passed)
        .toString(2)
}

/**
 * Explicit, foreground developer acceptance over the installed Native Room14 database.
 * It reads canonical evidence, writes only disposable/final SHADOW derived rows, and never changes
 * benchmark authority or ordinary workout prescription state.
 */
class NBio7BAcceptanceRepository(
    private val database: MyMettleDatabase,
    private val model: DynamicStochasticFrontierModel = DynamicStochasticFrontierModel(DynamicStochasticFrontierEvidenceV2.config),
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun run(
        onProgress: (NBio7BAcceptanceProgress) -> Unit = {},
    ): NBio7BAcceptanceReport {
        val started = System.nanoTime()
        val inferenceDao = database.inferenceDao()
        val userProfileIds = inferenceDao.userProfileIds()
        require(userProfileIds.size == 1) {
            "N-BIO-7B acceptance requires exactly one Native user profile; found ${userProfileIds.size}."
        }
        val userProfileId = userProfileIds.single()
        val benchmarkBefore = inferenceDao.latestInferenceRun(userProfileId)?.id
        val rawBefore = NBio7BRawEvidenceFingerprinter.capture(database)
        val history = NBio7BRawHistoryReader(database).read()
        val allKnownCutoff = history.revisions.maxOfOrNull { it.recordedAt } ?: Instant.EPOCH
        val currentHeads = HistoricalObservationRevisionSelector.currentAsOf(history.revisions, allKnownCutoff)
        val groupKeys = history.revisions
            .map { it.evidence.executionProfileVersionId.value to it.evidence.laterality }
            .distinct()
            .sortedWith(compareBy<Pair<String, Laterality>> { it.first }.thenBy { it.second.storageValue })
        val candidateConfig = model.config.toModelConfig(DynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
        val shadowRepository = DynamicCapabilityShadowRepository(database, model = model)
        val historicalEvaluator = DynamicResistanceHistoricalEvaluator(
            evaluator = DynamicResistanceRetrospectiveEvaluator(model = model),
            evidencePolicy = DynamicResistanceV2Contract.evidencePolicy,
        )
        val profileReports = mutableListOf<NBio7BProfileAcceptance>()

        groupKeys.forEachIndexed { index, (versionId, side) ->
            val descriptor = history.profiles[versionId]
                ?: error("History references missing execution profile version $versionId")
            onProgress(NBio7BAcceptanceProgress(index, groupKeys.size, "${descriptor.label} · ${side.storageValue}"))
            val groupStart = System.nanoTime()
            val historical = historicalEvaluator.evaluate(
                profile = descriptor.semantics,
                side = side,
                revisions = history.revisions,
            )
            val stage1Diagnostics = DynamicStage1DiagnosticAnalyzer.profile(
                profile = descriptor.semantics,
                side = side,
                observations = historical.observations,
                revisions = history.revisions,
                evidencePolicy = DynamicResistanceV2Contract.evidencePolicy,
            )
            val currentProjection = DynamicResistanceEvidenceProjector.project(
                profile = descriptor.semantics,
                side = side,
                evidence = currentHeads.filter {
                    it.executionProfileVersionId.value == versionId && it.laterality == side
                },
                policy = DynamicResistanceV2Contract.evidencePolicy,
            )
            var fit: DynamicStochasticFrontierFit? = null
            var shadowRunId: String? = null
            var persistReloadEquivalent: Boolean? = null
            var replayEquivalent: Boolean? = null
            var numericalValidity: Boolean? = null
            val limitations = mutableListOf<String>()
            val predictions = mutableListOf<NBio7BRepresentativePrediction>()

            if (currentProjection.evidence.isEmpty()) {
                limitations += "No current eligible evidence remains for this profile-version/side."
            } else {
                val horizon = currentProjection.evidence.maxOf { it.completedAt }
                fit = try {
                    model.fit(DynamicCapabilityFitRequest(currentProjection, horizon, candidateConfig))
                } catch (failure: DynamicCapabilityFitException) {
                    limitations += "Final-horizon fit failed: ${failure.reason.storageValue}."
                    null
                }
                if (fit != null) {
                    val reps = representativeRepetitions(fit)
                    val predictive = DynamicDemonstrationPredictiveEvaluator(model)
                    reps.forEach { repetitions ->
                        val frontier = requireNotNull(model.predictFrontier(fit, repetitions).summary)
                        val demonstration = predictive.evaluate(
                            fit = fit,
                            repetitions = repetitions,
                            observedResistanceKg = frontier.p50,
                        )
                        val prediction = NBio7BRepresentativePrediction(
                            repetitions = repetitions,
                            frontier = frontier,
                            demonstrationP05Kg = demonstration.p05ResistanceKg,
                            demonstrationP50Kg = demonstration.p50ResistanceKg,
                            demonstrationP95Kg = demonstration.p95ResistanceKg,
                        )
                        predictions += prediction
                    }
                    numericalValidity = predictions.all { prediction ->
                        prediction.repetitions > 0.0 &&
                            prediction.frontier.p05 > 0.0 && prediction.frontier.p50 > 0.0 && prediction.frontier.p95 > 0.0 &&
                            prediction.frontier.p05.isFinite() && prediction.frontier.p50.isFinite() && prediction.frontier.p95.isFinite() &&
                            prediction.demonstrationP05Kg > 0.0 && prediction.demonstrationP50Kg > 0.0 && prediction.demonstrationP95Kg > 0.0 &&
                            prediction.demonstrationP05Kg.isFinite() && prediction.demonstrationP50Kg.isFinite() &&
                            prediction.demonstrationP95Kg.isFinite()
                    }

                    val temporaryRunId = shadowRepository.persist(userProfileId, fit)
                    val reloaded = shadowRepository.load(temporaryRunId)
                    persistReloadEquivalent = equivalentFitAndPredictions(fit, reloaded, reps)
                    shadowRepository.discard(temporaryRunId)

                    val replay = model.fit(DynamicCapabilityFitRequest(currentProjection, horizon, candidateConfig))
                    replayEquivalent = equivalentFitAndPredictions(fit, replay, reps)
                    val finalRun = shadowRepository.persist(userProfileId, replay)
                    shadowRunId = finalRun.value
                }
            }

            fit?.let { fitted ->
                if (fitted.slope.identification.storageValue == "prior_dominated") {
                    limitations += "Slope remains prior-dominated."
                }
                if (fitted.warnings.isNotEmpty()) {
                    limitations += "Fit warnings: ${fitted.warnings.map { it.storageValue }.sorted().joinToString()}"
                }
            }
            if (historical.summary.candidatePitCalibration.meanAbsoluteBinError == null) {
                limitations += "Held-out sample is too small for an informative coarse PIT calibration error."
            }
            if (historical.verdict == DynamicCapabilityCandidateVerdict.INSUFFICIENT_EVIDENCE) {
                limitations += "Historical support is insufficient for an acceptance verdict on this group."
            }

            val elapsedMillis = (System.nanoTime() - groupStart) / 1_000_000L
            profileReports += NBio7BProfileAcceptance(
                executionProfileVersionId = versionId,
                executionProfileId = descriptor.semantics.executionProfileId.value,
                label = descriptor.label,
                side = side.storageValue,
                currentEligibleObservations = currentProjection.evidence.size,
                currentIndependentSessions = currentProjection.independentSessionCount,
                currentExclusions = currentProjection.exclusions.size,
                currentExclusionReasonCounts = currentProjection.exclusions
                    .groupingBy { it.reason.storageValue }.eachCount().toSortedMap(),
                repMin = currentProjection.repDomain?.first,
                repMax = currentProjection.repDomain?.last,
                resistanceMinKg = currentProjection.resistanceRange?.start,
                resistanceMaxKg = currentProjection.resistanceRange?.endInclusive,
                referenceRepetitions = fit?.referenceRepetitions ?: currentProjection.referenceRepetitions,
                frontierAtReference = fit?.frontierAtReference?.summary,
                slope = fit?.slope,
                slackScale = fit?.slackScale,
                noiseScale = fit?.noiseScale,
                warnings = fit?.warnings.orEmpty().map { it.storageValue }.sorted(),
                predictions = predictions,
                validation = historical.summary,
                stage1Diagnostics = stage1Diagnostics,
                candidateVerdict = historical.verdict,
                chronologicalFitCount = historical.chronologicalFitCount,
                shadowRunId = shadowRunId,
                parameterCodecVersion = fit?.let { DynamicCapabilityParameterCodec.SCHEMA_VERSION },
                persistReloadEquivalent = persistReloadEquivalent,
                fullReplayEquivalent = replayEquivalent,
                numericalValidity = numericalValidity,
                elapsedMillis = elapsedMillis,
                limitations = limitations.distinct(),
            )
            onProgress(NBio7BAcceptanceProgress(index + 1, groupKeys.size, "Completed ${descriptor.label} · ${side.storageValue}"))
        }

        val rawAfter = NBio7BRawEvidenceFingerprinter.capture(database)
        val benchmarkAfter = inferenceDao.latestInferenceRun(userProfileId)?.id
        val foreignKeysClean = database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { !it.moveToFirst() }
        val checks = listOf(
            NBio7BAcceptanceCheck(
                "raw_evidence_unchanged",
                if (rawBefore == rawAfter) NBio7BAcceptanceStatus.PASS else NBio7BAcceptanceStatus.FAIL,
                if (rawBefore == rawAfter) "Canonical workout/performance fingerprint and table counts are unchanged."
                else "Canonical workout/performance evidence changed during derived acceptance work.",
            ),
            NBio7BAcceptanceCheck(
                "benchmark_authority_unchanged",
                if (benchmarkBefore == benchmarkAfter) NBio7BAcceptanceStatus.PASS else NBio7BAcceptanceStatus.FAIL,
                "BENCHMARK_V0 latest run before=${benchmarkBefore ?: "none"}; after=${benchmarkAfter ?: "none"}.",
            ),
            NBio7BAcceptanceCheck(
                "context_consumption_none",
                if (model.config.contextConsumption.startsWith("NONE:")) NBio7BAcceptanceStatus.PASS else NBio7BAcceptanceStatus.FAIL,
                model.config.contextConsumption,
            ),
            NBio7BAcceptanceCheck(
                "persist_reload_equivalence",
                aggregateBoolean(profileReports.mapNotNull { it.persistReloadEquivalent }),
                "Every fitted profile must reproduce equivalent state and representative predictions after Room reload.",
            ),
            NBio7BAcceptanceCheck(
                "full_replay_equivalence",
                aggregateBoolean(profileReports.mapNotNull { it.fullReplayEquivalent }),
                "Every fitted profile must reproduce equivalent state and representative predictions after deleting its temporary shadow run and replaying raw evidence.",
            ),
            NBio7BAcceptanceCheck(
                "numeric_validity",
                aggregateBoolean(profileReports.mapNotNull { it.numericalValidity }),
                "No fitted representative frontier/demonstration prediction may be NaN, infinite, zero or negative; no fit means NOT_EVALUATED.",
            ),
            NBio7BAcceptanceCheck(
                "foreign_keys_clean",
                if (foreignKeysClean) NBio7BAcceptanceStatus.PASS else NBio7BAcceptanceStatus.FAIL,
                if (foreignKeysClean) "PRAGMA foreign_key_check returned no violations." else "Foreign-key violations exist after acceptance.",
            ),
        )
        val globalExclusionReasonCounts = profileReports
            .flatMap { report -> report.currentExclusionReasonCounts.entries }
            .groupingBy { it.key }.fold(0) { total, entry -> total + entry.value }
            .toSortedMap()
        val stage1Diagnostics = DynamicStage1DiagnosticAnalyzer.aggregate(
            profileReports.mapNotNull { it.stage1Diagnostics },
        )
        val totalElapsed = (System.nanoTime() - started) / 1_000_000L
        return NBio7BAcceptanceReport(
            generatedAt = clock(),
            roomSchemaVersion = 14,
            candidateModelVersion = DynamicStochasticFrontierV1.MODEL_VERSION,
            candidateModelConfigId = candidateConfig.id.value,
            validationProtocolId = dev.kian.mymettle.domain.inference.DynamicCapabilityValidationPolicy().protocolVersion,
            contextConsumption = model.config.contextConsumption,
            rawFingerprintBefore = rawBefore,
            rawFingerprintAfter = rawAfter,
            benchmarkRunIdBefore = benchmarkBefore,
            benchmarkRunIdAfter = benchmarkAfter,
            groupsDiscovered = groupKeys.size,
            globalExclusionReasonCounts = globalExclusionReasonCounts,
            profiles = profileReports,
            stage1Diagnostics = stage1Diagnostics,
            checks = checks,
            finalModelVerdict = aggregateVerdict(profileReports.map { it.candidateVerdict }),
            productAuthorityStatus = "BENCHMARK_V0_REMAINS_AUTHORITATIVE; candidate rows are SHADOW only",
            totalChronologicalFits = profileReports.sumOf { it.chronologicalFitCount },
            totalElapsedMillis = totalElapsed,
            worstProfileElapsedMillis = profileReports.maxOfOrNull { it.elapsedMillis },
        )
    }

    private fun aggregateBoolean(values: List<Boolean>): NBio7BAcceptanceStatus = when {
        values.isEmpty() -> NBio7BAcceptanceStatus.NOT_EVALUATED
        values.all { it } -> NBio7BAcceptanceStatus.PASS
        else -> NBio7BAcceptanceStatus.FAIL
    }

    private fun representativeRepetitions(fit: DynamicStochasticFrontierFit): List<Double> = listOf(
        fit.observedRepMin.toDouble(),
        fit.referenceRepetitions,
        fit.observedRepMax.toDouble(),
        max(fit.observedRepMax * 1.5, fit.observedRepMax + 4.0),
    ).distinct().sorted()

    private fun equivalentFitAndPredictions(
        expected: DynamicStochasticFrontierFit,
        actual: DynamicStochasticFrontierFit,
        repetitions: List<Double>,
    ): Boolean {
        if (expected.executionProfileVersionId != actual.executionProfileVersionId || expected.side != actual.side) return false
        if (expected.modelConfigId != actual.modelConfigId || expected.referenceRepetitions != actual.referenceRepetitions) return false
        if (expected.support != actual.support || expected.frontierAtReference.summary != actual.frontierAtReference.summary) return false
        if (DynamicCapabilityParameterCodec.encode(expected) != DynamicCapabilityParameterCodec.encode(actual)) return false
        return repetitions.all { reps ->
            val left = model.predictFrontier(expected, reps).summary ?: return@all false
            val right = model.predictFrontier(actual, reps).summary ?: return@all false
            summariesEquivalent(left, right)
        }
    }

    private fun summariesEquivalent(left: PosteriorSummary, right: PosteriorSummary): Boolean =
        close(left.p05, right.p05) && close(left.p50, right.p50) && close(left.p95, right.p95) &&
            close(left.posteriorVariance, right.posteriorVariance)

    private fun close(left: Double, right: Double): Boolean =
        abs(left - right) <= 1e-9 * max(1.0, max(abs(left), abs(right)))

    private fun aggregateVerdict(values: List<DynamicCapabilityCandidateVerdict>): DynamicCapabilityCandidateVerdict = when {
        values.any { it == DynamicCapabilityCandidateVerdict.REJECTED } -> DynamicCapabilityCandidateVerdict.REJECTED
        values.any { it == DynamicCapabilityCandidateVerdict.REQUIRES_NEW_CANDIDATE } -> DynamicCapabilityCandidateVerdict.REQUIRES_NEW_CANDIDATE
        values.any { it == DynamicCapabilityCandidateVerdict.ACCEPTABLE_WITH_LIMITATIONS } -> DynamicCapabilityCandidateVerdict.ACCEPTABLE_WITH_LIMITATIONS
        values.any { it == DynamicCapabilityCandidateVerdict.ACCEPTABLE_FOR_SHADOW } -> DynamicCapabilityCandidateVerdict.ACCEPTABLE_FOR_SHADOW
        else -> DynamicCapabilityCandidateVerdict.INSUFFICIENT_EVIDENCE
    }
}

private val INTEGRITY_CHECK_IDS = setOf(
    "raw_evidence_unchanged",
    "benchmark_authority_unchanged",
    "context_consumption_none",
    "foreign_keys_clean",
)

private fun NBio7BAcceptanceCheck.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("status", status.storageValue)
    .put("detail", detail)

private fun NBio7BProfileAcceptance.toJson(): JSONObject = JSONObject()
    .put("executionProfileVersionId", executionProfileVersionId)
    .put("executionProfileId", executionProfileId)
    .put("label", label)
    .put("side", side)
    .put("evidence", JSONObject()
        .put("eligibleObservations", currentEligibleObservations)
        .put("independentSessions", currentIndependentSessions)
        .put("exclusions", currentExclusions)
        .put("exclusionReasonCounts", JSONObject(currentExclusionReasonCounts))
        .put("repMin", repMin ?: JSONObject.NULL)
        .put("repMax", repMax ?: JSONObject.NULL)
        .put("resistanceMinKg", resistanceMinKg ?: JSONObject.NULL)
        .put("resistanceMaxKg", resistanceMaxKg ?: JSONObject.NULL)
        .put("referenceRepetitions", referenceRepetitions ?: JSONObject.NULL))
    .put("fit", JSONObject()
        .put("frontierAtReference", frontierAtReference?.toJson() ?: JSONObject.NULL)
        .put("slope", slope?.toJson() ?: JSONObject.NULL)
        .put("slackScale", slackScale?.toJson() ?: JSONObject.NULL)
        .put("noiseScale", noiseScale?.toJson() ?: JSONObject.NULL)
        .put("warnings", JSONArray(warnings)))
    .put("representativePredictions", JSONArray(predictions.map { it.toJson() }))
    .put("validation", validation.toJson())
    .put("stage1Diagnostics", stage1Diagnostics?.toStage1Json() ?: JSONObject.NULL)
    .put("candidateVerdict", candidateVerdict.storageValue)
    .put("chronologicalFitCount", chronologicalFitCount)
    .put("persistence", JSONObject()
        .put("shadowRunId", shadowRunId ?: JSONObject.NULL)
        .put("parameterCodecVersion", parameterCodecVersion ?: JSONObject.NULL)
        .put("persistReloadEquivalent", persistReloadEquivalent ?: JSONObject.NULL)
        .put("fullReplayEquivalent", fullReplayEquivalent ?: JSONObject.NULL))
    .put("numericalValidity", numericalValidity ?: JSONObject.NULL)
    .put("elapsedMillis", elapsedMillis)
    .put("limitations", JSONArray(limitations))

private fun NBio7BRepresentativePrediction.toJson(): JSONObject = JSONObject()
    .put("repetitions", repetitions)
    .put("frontierPosterior", frontier.toJson())
    .put("demonstrationPredictive", JSONObject()
        .put("p05Kg", demonstrationP05Kg)
        .put("p50Kg", demonstrationP50Kg)
        .put("p95Kg", demonstrationP95Kg))

private fun DynamicFrontierParameterPosterior.toJson(): JSONObject = JSONObject()
    .put("posterior", summary.toJson())
    .put("identification", identification.storageValue)
    .put("semanticUnit", semanticUnit)

private fun PosteriorSummary.toJson(): JSONObject = JSONObject()
    .put("p05", p05)
    .put("p50", p50)
    .put("p95", p95)
    .put("variance", posteriorVariance)

private fun DynamicCapabilityValidationSummary.toJson(): JSONObject = JSONObject()
    .put("protocolVersion", protocolVersion)
    .put("semanticsMode", semanticsMode.storageValue)
    .put("heldOutObservationCount", heldOutObservationCount)
    .put("heldOutSessionCount", heldOutSessionCount)
    .put("evaluableCount", evaluableCount)
    .put("insufficientEvidenceCount", insufficientEvidenceCount)
    .put("modelFailureCount", modelFailureCount)
    .put("availabilityRate", availabilityRate)
    .put("modelFailureRate", modelFailureRate)
    .put("candidateMeanLogPredictiveDensity", meanCandidateLogPredictiveDensity ?: JSONObject.NULL)
    .put("candidatePredictiveCoverage", candidatePredictiveCoverage ?: JSONObject.NULL)
    .put("candidateMeanPredictiveLogWidth", meanCandidatePredictiveLogWidth ?: JSONObject.NULL)
    .put("candidateDemonstrationMedianMaeKg", candidateDemonstrationMedianMaeKg ?: JSONObject.NULL)
    .put("benchmarkLatestAnchorMaeKg", benchmarkLatestAnchorMaeKg ?: JSONObject.NULL)
    .put("benchmarkLogPredictiveDensity", JSONObject.NULL)
    .put("benchmarkPredictiveCoverage", JSONObject.NULL)
    .put("benchmarkPitCalibration", JSONObject.NULL)
    .put("candidatePitCalibration", JSONObject()
        .put("sampleCount", candidatePitCalibration.sampleCount)
        .put("lowCount", candidatePitCalibration.lowCount)
        .put("middleCount", candidatePitCalibration.middleCount)
        .put("highCount", candidatePitCalibration.highCount)
        .put("meanAbsoluteBinError", candidatePitCalibration.meanAbsoluteBinError ?: JSONObject.NULL))
    .put("catastrophicFrontierContradictionCount", catastrophicFrontierContradictionCount)
    .put("catastrophicFrontierContradictionRate", catastrophicFrontierContradictionRate ?: JSONObject.NULL)
