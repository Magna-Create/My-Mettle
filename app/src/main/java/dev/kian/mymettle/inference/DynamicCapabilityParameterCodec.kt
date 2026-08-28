package dev.kian.mymettle.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitWarning
import dev.kian.mymettle.domain.inference.DynamicFrontierParameterPosterior
import dev.kian.mymettle.domain.inference.DynamicFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.ModelConfigId
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import dev.kian.mymettle.domain.performance.Laterality
import java.time.Instant
import java.util.Base64

/**
 * Deterministic versioned codec for the behaviourally meaningful 7B.2 joint posterior.
 *
 * It stores derived parameter/posterior state and evidence identifiers only. Raw load/repetition
 * observations are deliberately not duplicated. Per-observation slack can be reconstructed by full
 * replay from canonical raw evidence when later SetDemand work needs it.
 */
object DynamicCapabilityParameterCodec {
    const val SCHEMA_VERSION: Int = 1
    const val CODEC_ID: String = "n-bio-7b34-dynamic-capability-parameters-v1"

    fun encode(fit: DynamicStochasticFrontierFit): String = buildString {
        line("codec", CODEC_ID)
        line("inferenceHorizon", fit.inferenceHorizon.toString())
        line("referenceRepetitions", fit.referenceRepetitions.toString())
        line("modelVersion", text(fit.modelVersion))
        line("evidencePolicyIdentity", text(fit.evidencePolicyIdentity))
        line("observedRepMin", fit.observedRepMin.toString())
        line("observedRepMax", fit.observedRepMax.toString())
        line("observedResistanceMinKg", fit.observedResistanceMinKg.toString())
        line("observedResistanceMaxKg", fit.observedResistanceMaxKg.toString())
        line("approximationVersion", text(fit.approximationVersion))
        line("slope", parameter(fit.slope))
        line("slackScale", parameter(fit.slackScale))
        line("noiseScale", parameter(fit.noiseScale))
        line("warnings", fit.warnings.map { it.storageValue }.sorted().joinToString(",") { text(it) })
        line("selectedObservationIds", fit.selectedObservationIds.joinToString(",") { text(it) })
        line("selectedSessionIds", fit.selectedSessionIds.joinToString(",") { text(it) })
        line(
            "posteriorNodes",
            fit.posteriorNodes.joinToString(";") { node ->
                listOf(
                    node.logFrontierAtReference,
                    node.slope,
                    node.slackScale,
                    node.noiseScale,
                    node.posteriorWeight,
                ).joinToString(",")
            },
        )
        line("observationSlackPersistence", text("recompute_from_raw_history"))
    }.trimEnd('\n')

    fun decode(
        parameterSchemaVersion: Int,
        encodedParameters: String,
        frontierAtReference: PosteriorEstimate,
        executionProfileVersionId: ExecutionProfileVersionId,
        side: Laterality,
        modelConfigId: ModelConfigId,
    ): DynamicStochasticFrontierFit {
        require(parameterSchemaVersion == SCHEMA_VERSION) {
            "Unsupported dynamic capability parameter schema $parameterSchemaVersion; recomputation is required."
        }
        val values = encodedParameters.lineSequence()
            .filter { it.isNotBlank() }
            .associate { line ->
                val split = line.indexOf('=')
                require(split > 0) { "Malformed dynamic capability parameter line." }
                line.substring(0, split) to line.substring(split + 1)
            }
        require(values.required("codec") == CODEC_ID) { "Unsupported dynamic capability parameter codec; recomputation is required." }
        require(frontierAtReference.summary != null) { "Persisted dynamic capability requires a known frontier posterior." }
        require(frontierAtReference.provenance.modelConfigId == modelConfigId)

        val observationIds = decodeList(values.required("selectedObservationIds"))
        val sessionIds = decodeList(values.required("selectedSessionIds"))
        require(observationIds.size == frontierAtReference.support.observationCount)
        require(sessionIds.distinct().size == frontierAtReference.support.effectiveIndependentSessionCount)

        return DynamicStochasticFrontierFit(
            executionProfileVersionId = executionProfileVersionId,
            side = side,
            inferenceHorizon = Instant.parse(values.required("inferenceHorizon")),
            referenceRepetitions = values.required("referenceRepetitions").finiteDouble("referenceRepetitions"),
            modelConfigId = modelConfigId,
            modelVersion = untext(values.required("modelVersion")),
            evidencePolicyIdentity = untext(values.required("evidencePolicyIdentity")),
            support = frontierAtReference.support,
            observedRepMin = values.required("observedRepMin").toIntStrict("observedRepMin"),
            observedRepMax = values.required("observedRepMax").toIntStrict("observedRepMax"),
            observedResistanceMinKg = values.required("observedResistanceMinKg").finiteDouble("observedResistanceMinKg"),
            observedResistanceMaxKg = values.required("observedResistanceMaxKg").finiteDouble("observedResistanceMaxKg"),
            frontierAtReference = frontierAtReference,
            slope = decodeParameter(values.required("slope")),
            slackScale = decodeParameter(values.required("slackScale")),
            noiseScale = decodeParameter(values.required("noiseScale")),
            observationSlack = emptyList(),
            selectedObservationIds = observationIds,
            selectedSessionIds = sessionIds,
            approximationVersion = untext(values.required("approximationVersion")),
            warnings = decodeList(values.required("warnings")).map { warning ->
                DynamicCapabilityFitWarning.entries.firstOrNull { it.storageValue == warning }
                    ?: throw IllegalArgumentException("Unsupported dynamic capability warning $warning")
            }.toSet(),
            posteriorNodes = decodeNodes(values.required("posteriorNodes")),
        )
    }

    private fun StringBuilder.line(key: String, value: String) {
        require(key.isNotBlank() && !key.contains('='))
        append(key).append('=').append(value).append('\n')
    }

    private fun parameter(value: DynamicFrontierParameterPosterior): String = listOf(
        value.summary.p05,
        value.summary.p50,
        value.summary.p95,
        value.summary.posteriorVariance,
        text(value.identification.storageValue),
        text(value.semanticUnit),
    ).joinToString(",")

    private fun decodeParameter(value: String): DynamicFrontierParameterPosterior {
        val parts = value.split(',')
        require(parts.size == 6) { "Malformed dynamic capability parameter posterior." }
        val identification = untext(parts[4]).let { stored ->
            DynamicParameterIdentification.entries.firstOrNull { it.storageValue == stored }
                ?: throw IllegalArgumentException("Unsupported parameter identification $stored")
        }
        return DynamicFrontierParameterPosterior(
            summary = PosteriorSummary(
                credibleLower05 = parts[0].finiteDouble("p05"),
                estimateMedian = parts[1].finiteDouble("p50"),
                credibleUpper95 = parts[2].finiteDouble("p95"),
                posteriorVariance = parts[3].finiteDouble("variance"),
            ),
            identification = identification,
            semanticUnit = untext(parts[5]),
        )
    }

    private fun decodeNodes(value: String): List<DynamicFrontierPosteriorNode> {
        require(value.isNotBlank()) { "Persisted dynamic capability posterior nodes cannot be empty." }
        val nodes = value.split(';').map { encoded ->
            val parts = encoded.split(',')
            require(parts.size == 5) { "Malformed dynamic capability posterior node." }
            DynamicFrontierPosteriorNode(
                logFrontierAtReference = parts[0].finiteDouble("logFrontierAtReference"),
                slope = parts[1].finiteDouble("slope"),
                slackScale = parts[2].finiteDouble("slackScale"),
                noiseScale = parts[3].finiteDouble("noiseScale"),
                posteriorWeight = parts[4].finiteDouble("posteriorWeight"),
            )
        }
        val weight = nodes.sumOf { it.posteriorWeight }
        require(kotlin.math.abs(weight - 1.0) <= 1e-8) { "Persisted dynamic capability posterior weights must sum to one." }
        return nodes
    }

    private fun decodeList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(',').map(::untext)

    private fun Map<String, String>.required(key: String): String =
        get(key) ?: throw IllegalArgumentException("Missing dynamic capability parameter field $key")

    private fun String.finiteDouble(name: String): Double = toDoubleOrNull()?.takeIf { it.isFinite() }
        ?: throw IllegalArgumentException("Dynamic capability parameter $name must be finite.")

    private fun String.toIntStrict(name: String): Int = toIntOrNull()
        ?: throw IllegalArgumentException("Dynamic capability parameter $name must be an integer.")

    private fun text(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun untext(value: String): String = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrElse { throw IllegalArgumentException("Malformed encoded dynamic capability text.", it) }
}
