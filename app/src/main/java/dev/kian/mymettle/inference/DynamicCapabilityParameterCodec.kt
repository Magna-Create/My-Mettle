package dev.kian.mymettle.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitWarning
import dev.kian.mymettle.domain.inference.DynamicFrontierParameterPosterior
import dev.kian.mymettle.domain.inference.DynamicFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.DynamicObservationSlackPosterior
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.DynamicSlackPosteriorMass
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.ModelConfigId
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import dev.kian.mymettle.domain.performance.Laterality
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Deterministic versioned codec for the behaviourally meaningful 7B.2 joint posterior.
 *
 * It stores derived parameter/posterior state and evidence identifiers only. Raw load/repetition
 * observations are deliberately not duplicated. Per-observation slack remains encoded explicitly
 * so later SetDemand/replay semantics are retained.
 *
 * Schema v2 losslessly DEFLATE-compresses the same text payload before Room persistence. The
 * compression is a storage optimisation only: model/config identity and decoded values are
 * unchanged. Schema v1 remains readable so existing disposable SHADOW rows fail neither replay nor
 * explicit cleanup.
 */
object DynamicCapabilityParameterCodec {
    const val SCHEMA_VERSION: Int = 2
    const val CODEC_ID: String = "n-bio-7b34-dynamic-capability-parameters-deflate-v2"
    private const val LEGACY_SCHEMA_VERSION: Int = 1
    private const val LEGACY_CODEC_ID: String = "n-bio-7b34-dynamic-capability-parameters-v1"
    private const val COMPRESSED_PREFIX: String = "deflate64:"
    private const val MAX_DECOMPRESSED_BYTES: Int = 64 * 1024 * 1024

    fun encode(fit: DynamicStochasticFrontierFit): String =
        COMPRESSED_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
            deflate(encodePlain(fit, CODEC_ID).toByteArray(Charsets.UTF_8)),
        )

    fun decode(
        parameterSchemaVersion: Int,
        encodedParameters: String,
        frontierAtReference: PosteriorEstimate,
        executionProfileVersionId: ExecutionProfileVersionId,
        side: Laterality,
        modelConfigId: ModelConfigId,
    ): DynamicStochasticFrontierFit {
        val expectedCodec: String
        val plain = when (parameterSchemaVersion) {
            LEGACY_SCHEMA_VERSION -> {
                expectedCodec = LEGACY_CODEC_ID
                encodedParameters
            }
            SCHEMA_VERSION -> {
                expectedCodec = CODEC_ID
                inflate(encodedParameters)
            }
            else -> throw IllegalArgumentException(
                "Unsupported dynamic capability parameter schema $parameterSchemaVersion; recomputation is required.",
            )
        }
        return decodePlain(
            plain = plain,
            expectedCodec = expectedCodec,
            frontierAtReference = frontierAtReference,
            executionProfileVersionId = executionProfileVersionId,
            side = side,
            modelConfigId = modelConfigId,
        )
    }

    private fun encodePlain(fit: DynamicStochasticFrontierFit, codecId: String): String = buildString {
        line("codec", codecId)
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
        line("observationSlack", fit.observationSlack.joinToString(";") { encodeSlack(it) })
    }.trimEnd('\n')

    private fun decodePlain(
        plain: String,
        expectedCodec: String,
        frontierAtReference: PosteriorEstimate,
        executionProfileVersionId: ExecutionProfileVersionId,
        side: Laterality,
        modelConfigId: ModelConfigId,
    ): DynamicStochasticFrontierFit {
        val values = plain.lineSequence()
            .filter { it.isNotBlank() }
            .associate { line ->
                val split = line.indexOf('=')
                require(split > 0) { "Malformed dynamic capability parameter line." }
                line.substring(0, split) to line.substring(split + 1)
            }
        require(values.required("codec") == expectedCodec) {
            "Unsupported dynamic capability parameter codec; recomputation is required."
        }
        require(frontierAtReference.summary != null) { "Persisted dynamic capability requires a known frontier posterior." }
        require(frontierAtReference.provenance.modelConfigId == modelConfigId)

        val observationIds = decodeList(values.required("selectedObservationIds"))
        val sessionIds = decodeList(values.required("selectedSessionIds"))
        val observationSlack = decodeSlack(values.required("observationSlack"))
        require(observationIds.size == frontierAtReference.support.observationCount)
        require(sessionIds.distinct().size == frontierAtReference.support.effectiveIndependentSessionCount)
        require(observationSlack.map { it.observationId }.toSet() == observationIds.toSet())

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
            observationSlack = observationSlack,
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

    private fun deflate(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_SPEED, true)
        try {
            DeflaterOutputStream(output, deflater).use { stream -> stream.write(bytes) }
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(encoded: String): String {
        require(encoded.startsWith(COMPRESSED_PREFIX)) {
            "Malformed compressed dynamic capability parameter payload."
        }
        val compressed = runCatching {
            Base64.getUrlDecoder().decode(encoded.removePrefix(COMPRESSED_PREFIX))
        }.getOrElse { throw IllegalArgumentException("Malformed compressed dynamic capability base64.", it) }
        val inflater = Inflater(true)
        try {
            InflaterInputStream(ByteArrayInputStream(compressed), inflater).use { input ->
                val output = ByteArrayOutputStream(minOf(compressed.size * 4, 1 shl 20))
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_DECOMPRESSED_BYTES) {
                        "Dynamic capability parameter payload exceeds the supported decompressed size; recomputation is required."
                    }
                    output.write(buffer, 0, read)
                }
                return output.toString(Charsets.UTF_8.name())
            }
        } catch (failure: IllegalArgumentException) {
            throw failure
        } catch (failure: Exception) {
            throw IllegalArgumentException("Malformed compressed dynamic capability parameter payload.", failure)
        } finally {
            inflater.end()
        }
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
        val identification = identification(untext(parts[4]))
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

    private fun encodeSlack(value: DynamicObservationSlackPosterior): String = listOf(
        text(value.observationId),
        text(value.identification.storageValue),
        value.summary.p05,
        value.summary.p50,
        value.summary.p95,
        value.summary.posteriorVariance,
        text(value.semanticDefinition),
        value.massPoints.joinToString("|") { "${it.slack}:${it.probability}" },
    ).joinToString(",")

    private fun decodeSlack(value: String): List<DynamicObservationSlackPosterior> {
        require(value.isNotBlank()) { "Persisted per-observation slack cannot be empty for a complete fit." }
        return value.split(';').map { encoded ->
            val parts = encoded.split(',', limit = 8)
            require(parts.size == 8) { "Malformed dynamic observation slack posterior." }
            val mass = parts[7].split('|').map { massEncoded ->
                val massParts = massEncoded.split(':')
                require(massParts.size == 2) { "Malformed dynamic observation slack mass." }
                DynamicSlackPosteriorMass(
                    slack = massParts[0].finiteDouble("slack"),
                    probability = massParts[1].finiteDouble("slackProbability"),
                )
            }
            DynamicObservationSlackPosterior(
                observationId = untext(parts[0]),
                summary = PosteriorSummary(
                    credibleLower05 = parts[2].finiteDouble("slackP05"),
                    estimateMedian = parts[3].finiteDouble("slackP50"),
                    credibleUpper95 = parts[4].finiteDouble("slackP95"),
                    posteriorVariance = parts[5].finiteDouble("slackVariance"),
                ),
                identification = identification(untext(parts[1])),
                massPoints = mass,
                semanticDefinition = untext(parts[6]),
            )
        }
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

    private fun identification(stored: String): DynamicParameterIdentification =
        DynamicParameterIdentification.entries.firstOrNull { it.storageValue == stored }
            ?: throw IllegalArgumentException("Unsupported parameter identification $stored")

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
