package dev.kian.mymettle.developer

import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.inference.DynamicHistoricalAvailabilityV3
import dev.kian.mymettle.engine.inference.DynamicTransferN0Champion
import dev.kian.mymettle.engine.inference.HistoricalObservationRevisionSelector
import dev.kian.mymettle.engine.inference.NBio7FN0V1
import dev.kian.mymettle.engine.performance.DynamicResistanceEvidenceProjector
import dev.kian.mymettle.inference.DynamicTrendCapabilityShadowRepository
import java.io.Writer
import java.time.Instant
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Developer-only read-only, historical-as-of export of N0's complete, weighted joint posterior.
 *
 * This is a visualisation artifact, not a new predictor, training recommendation or authority gate.
 * Fits are in memory; no candidate rows, canonical evidence or prescriptions are written.
 */
data class NBioPosteriorPortfolioOption(
    val executionProfileVersionId: String,
    val side: Laterality,
    val label: String,
    val independentSessions: Int,
    val eligibleObservations: Int,
    val minRepetitions: Int?,
    val maxRepetitions: Int?,
) {
    val key: String get() = "${executionProfileVersionId}|${side.storageValue}"
}

/** Export only explicit, time-bounded evidence states, never invented intermediate workouts. */
class NBioPosteriorPortfolioExporter(private val database: MyMettleDatabase) {
    private val champion = DynamicTransferN0Champion()

    fun options(): List<NBioPosteriorPortfolioOption> = optionsFromHistory(readHistory())

    /**
     * Streams nodes directly to Android's document output, avoiding a potentially massive in-memory
     * JSON report. The original, normalised posterior weights are retained without resampling.
     */
    fun export(
        selectedKey: String,
        writer: Writer,
        onProgress: (completed: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ) {
        val history = readHistory()
        val option = optionsFromHistory(history).firstOrNull { it.key == selectedKey }
            ?: error("The selected exercise no longer has eligible evidence. Refresh the selector.")
        val descriptor = requireNotNull(history.profiles[option.executionProfileVersionId])
        val cutoffs = history.revisions.asSequence()
            .filter {
                it.evidence.executionProfileVersionId.value == option.executionProfileVersionId &&
                    it.evidence.laterality == option.side
            }
            .map { it.sessionCompletedAt }
            .distinct()
            .sorted()
            .toList()
        require(cutoffs.isNotEmpty()) { "No historical sessions remain for this profile." }
        val selectedCutoffs = selectHistoricalCutoffs(cutoffs)
        writer.write("{\"format\":\"my-mettle-n-bio-posterior-portfolio\",\"formatVersion\":1")
        writer.write(",\"status\":\"SHADOW_DEVELOPER_ONLY\",\"productAuthority\":\"BENCHMARK_V0_UNCHANGED\"")
        writer.write(",\"generatedAt\":${quote(Instant.now().toString())}")
        writer.write(",\"historicalAvailabilityPolicy\":${quote(DynamicHistoricalAvailabilityV3.POLICY_ID)}")
        writer.write(",\"modelIdentity\":${quote(NBio7FN0V1.mathematicalModelIdentity.identity)}")
        writer.write(",\"solverIdentity\":${quote(NBio7FN0V1.solverIdentity.identity)}")
        writer.write(",\"executionProfileVersionId\":${quote(option.executionProfileVersionId)}")
        writer.write(",\"exerciseLabel\":${quote(option.label)},\"side\":${quote(option.side.storageValue)}")
        writer.write(",\"selectionBasis\":\"independent_sessions_then_rep_range_then_eligible_observations\"")
        writer.write(",\"axes\":[\"logFrontierAtLatestSession\",\"slope\",\"frontierTrend\"]")
        writer.write(",\"extraChannels\":[\"slackScale\",\"noiseScale\",\"posteriorWeight\"]")
        writer.write(",\"snapshotPolicy\":\"evenly_spaced_historical_session_finalisation_cutoffs_max_6_as_of_v1\"")
        writer.write(",\"notes\":\"Parameter nodes are weighted hypotheses, not neural-network synaptic weights. Snapshot cutoffs are historical; predictions are experimental capability estimates, not prescribed working loads.\"")
        writer.write(",\"snapshots\":[")
        selectedCutoffs.forEachIndexed { index, cutoff ->
            onProgress(index, selectedCutoffs.size, "Fitting historical snapshot ${index + 1}/${selectedCutoffs.size}")
            if (index > 0) writer.write(",")
            val current = currentEvidence(history, cutoff, option.executionProfileVersionId, option.side)
            val projection = DynamicResistanceEvidenceProjector.project(
                profile = descriptor.semantics,
                side = option.side,
                evidence = current,
                policy = NBio7FN0V1.evidencePolicy,
            )
            writer.write("{\"cutoff\":${quote(cutoff.toString())}")
            writer.write(",\"historicalSessionIndex\":${cutoffs.indexOf(cutoff) + 1}")
            writer.write(",\"eligibleObservations\":${projection.evidence.size}")
            writer.write(",\"independentSessions\":${projection.independentSessionCount}")
            writer.write(",\"excludedObservations\":${projection.exclusions.size}")
            if (projection.evidence.isEmpty()) {
                writer.write(",\"status\":\"NO_ELIGIBLE_EVIDENCE\",\"nodes\":[]}")
            } else {
                val fit = runCatching {
                    champion.fit(
                        destinationProjection = projection,
                        inferenceHorizon = projection.evidence.maxOf { it.completedAt },
                        configCreatedAt = DynamicTrendCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT,
                    ).destinationFit
                }
                fit.fold(
                    onSuccess = { writeFit(writer, it) },
                    onFailure = { failure ->
                        writer.write(",\"status\":\"FIT_UNAVAILABLE\",\"reason\":${quote(failure.message ?: failure.javaClass.simpleName)},\"nodes\":[]}")
                    },
                )
            }
            writer.flush()
            onProgress(index + 1, selectedCutoffs.size, "Snapshot ${index + 1}/${selectedCutoffs.size} complete")
        }
        writer.write("]}")
        writer.flush()
    }

    private fun optionsFromHistory(history: NBio7BRawHistory): List<NBioPosteriorPortfolioOption> {
        // Use exactly the same read-only candidate ranking as the selector, without a second DB read.
        val cutoff = history.revisions.maxOfOrNull { maxOf(it.recordedAt, it.sessionCompletedAt) }
            ?: return emptyList()
        return history.profiles.flatMap { (id, descriptor) ->
            history.revisions.asSequence()
                .filter { it.evidence.executionProfileVersionId.value == id }
                .map { it.evidence.laterality }
                .distinct()
                .mapNotNull { side ->
                    val projection = DynamicResistanceEvidenceProjector.project(
                        profile = descriptor.semantics,
                        side = side,
                        evidence = currentEvidence(history, cutoff, id, side),
                        policy = NBio7FN0V1.evidencePolicy,
                    )
                    if (projection.evidence.isEmpty()) return@mapNotNull null
                    NBioPosteriorPortfolioOption(
                        executionProfileVersionId = id,
                        side = side,
                        label = descriptor.label,
                        independentSessions = projection.independentSessionCount,
                        eligibleObservations = projection.evidence.size,
                        minRepetitions = projection.repDomain?.first,
                        maxRepetitions = projection.repDomain?.last,
                    )
                }.toList()
        }.sortedWith(compareByDescending<NBioPosteriorPortfolioOption> { it.independentSessions }
            .thenByDescending { (it.maxRepetitions ?: 0) - (it.minRepetitions ?: 0) }
            .thenByDescending { it.eligibleObservations }
            .thenBy { it.label }.thenBy { it.side.storageValue })
    }

    private fun readHistory(): NBio7BRawHistory = NBio7BRawHistoryReader(
        database = database,
        historicalAvailabilityResolver = DynamicHistoricalAvailabilityV3::resolve,
    ).read()

    private fun currentEvidence(
        history: NBio7BRawHistory,
        cutoff: Instant,
        versionId: String,
        side: Laterality,
    ): List<CompletedSetEvidence> = HistoricalObservationRevisionSelector.currentAsOf(
        revisions = history.revisions.filter { !it.sessionCompletedAt.isAfter(cutoff) },
        cutoff = cutoff,
    ).filter {
        it.executionProfileVersionId.value == versionId && it.laterality == side &&
            !it.completedAt.isAfter(cutoff)
    }

    private fun writeFit(writer: Writer, fit: DynamicTrendFrontierFit) {
        writer.write(",\"status\":\"RESOLVED\"")
        writer.write(",\"modelConfigId\":${quote(fit.modelConfigId.value)}")
        writer.write(",\"inferenceHorizon\":${quote(fit.inferenceHorizon.toString())}")
        writer.write(",\"referenceRepetitions\":${fit.referenceRepetitions}")
        writer.write(",\"observedRepMinimum\":${fit.observedRepMin},\"observedRepMaximum\":${fit.observedRepMax}")
        writer.write(",\"observedResistanceMinimumKg\":${fit.observedResistanceMinKg}")
        writer.write(",\"observedResistanceMaximumKg\":${fit.observedResistanceMaxKg}")
        writer.write(",\"selectedIndependentSessions\":${fit.selectedSessionIds.size}")
        writer.write(",\"selectedObservations\":${fit.selectedObservationIds.size}")
        writer.write(",\"posteriorEffectiveNodeCount\":${fit.posteriorEffectiveNodeCount}")
        writer.write(",\"posteriorNodeCount\":${fit.posteriorNodes.size}")
        writer.write(",\"frontierMedianAtReferenceKg\":${requireNotNull(fit.frontierAtLatestSession.summary).p50}")
        writer.write(",\"nodes\":[")
        fit.posteriorNodes.forEachIndexed { i, node ->
            if (i > 0) writer.write(",")
            writer.write("[${node.logFrontierAtLatestSession},${node.slope},${node.frontierTrend},${node.slackScale},${node.noiseScale},${node.posteriorWeight}]")
        }
        writer.write("]}")
    }

    companion object {
        /** Historical finals only; no artificial interpolation or future observations. */
        internal fun selectHistoricalCutoffs(cutoffs: List<Instant>, maxSnapshots: Int = 6): List<Instant> {
            require(maxSnapshots >= 2)
            val ordered = cutoffs.distinct().sorted()
            if (ordered.size <= maxSnapshots) return ordered
            return (0 until maxSnapshots).map { index ->
                ordered[((ordered.lastIndex * index.toDouble()) / (maxSnapshots - 1)).roundToInt()]
            }.distinct()
        }

        private fun quote(value: String): String = JSONObject.quote(value)
    }
}
