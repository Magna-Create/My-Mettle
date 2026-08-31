package dev.kian.mymettle.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicFrontierParameterPosterior
import dev.kian.mymettle.domain.inference.DynamicObservationSlackPosterior
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.DynamicSlackPosteriorMass
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.InferenceComputeBackend
import dev.kian.mymettle.domain.inference.InferenceMathematicalModelIdentity
import dev.kian.mymettle.domain.inference.InferencePosteriorRepresentation
import dev.kian.mymettle.domain.inference.InferenceSolverDiagnostics
import dev.kian.mymettle.domain.inference.InferenceSolverFamily
import dev.kian.mymettle.domain.inference.InferenceSolverIdentity
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

/** Separate Room14 parameter-state codec for Candidate-v2 dynamic/trend solver state. */
object DynamicTrendCapabilityParameterCodec {
    const val SCHEMA_VERSION = 1
    const val CODEC_ID = "n-bio-7bx-dynamic-trend-capability-parameters-deflate-v1"
    private const val PREFIX = "deflate64:"
    private const val MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024

    fun encode(fit: DynamicTrendFrontierFit): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
        deflate(encodePlain(fit).toByteArray(Charsets.UTF_8)),
    )

    fun decode(
        parameterSchemaVersion: Int,
        encodedParameters: String,
        frontierAtLatestSession: PosteriorEstimate,
        executionProfileVersionId: ExecutionProfileVersionId,
        side: Laterality,
        modelConfigId: ModelConfigId,
    ): DynamicTrendFrontierFit {
        require(parameterSchemaVersion == SCHEMA_VERSION) {
            "Unsupported Candidate-v2 capability parameter schema $parameterSchemaVersion; recomputation is required."
        }
        val values = inflate(encodedParameters).lineSequence()
            .filter { it.isNotBlank() }
            .associate { line ->
                val split = line.indexOf('=')
                require(split > 0) { "Malformed Candidate-v2 capability parameter line." }
                line.substring(0, split) to line.substring(split + 1)
            }
        require(values.required("codec") == CODEC_ID) { "Unsupported Candidate-v2 capability codec." }
        require(frontierAtLatestSession.summary != null)
        require(frontierAtLatestSession.provenance.modelConfigId == modelConfigId)

        val observationIds = decodeList(values.required("selectedObservationIds"))
        val sessionIds = decodeList(values.required("selectedSessionIds"))
        val observationSlack = decodeSlack(values.required("observationSlack"))
        require(observationIds.size == frontierAtLatestSession.support.observationCount)
        require(sessionIds.distinct().size == frontierAtLatestSession.support.effectiveIndependentSessionCount)
        require(observationSlack.map { it.observationId }.toSet() == observationIds.toSet())

        val math = InferenceMathematicalModelIdentity(
            family = untext(values.required("mathFamily")),
            semanticVersion = untext(values.required("mathVersion")),
            definition = untext(values.required("mathDefinition")),
        )
        val solver = InferenceSolverIdentity(
            solverFamily = enumByStorage(values.required("solverFamily"), InferenceSolverFamily.entries) { it.storageValue },
            semanticVersion = untext(values.required("solverVersion")),
            computeBackend = enumByStorage(values.required("computeBackend"), InferenceComputeBackend.entries) { it.storageValue },
            deterministicReplay = values.required("deterministicReplay").toBooleanStrict(),
            approximationDefinition = untext(values.required("solverApproximation")),
        )
        val representation = enumByStorage(
            values.required("posteriorRepresentation"),
            InferencePosteriorRepresentation.entries,
        ) { it.storageValue }
        val diagnostics = InferenceSolverDiagnostics(
            solverIdentity = solver,
            posteriorRepresentation = representation,
            evaluatedNodeCount = values.optionalLong("evaluatedNodeCount"),
            effectiveNodeCount = values.optionalDouble("diagnosticEffectiveNodeCount"),
            updateRuntimeNanos = values.optionalLong("updateRuntimeNanos"),
            peakWorkingBytes = values.optionalLong("peakWorkingBytes"),
            approximationFailure = values.optionalText("approximationFailure"),
            notes = decodeList(values.required("solverNotes")).toSet(),
        )

        return DynamicTrendFrontierFit(
            executionProfileVersionId = executionProfileVersionId,
            side = side,
            inferenceHorizon = Instant.parse(values.required("inferenceHorizon")),
            referenceRepetitions = values.required("referenceRepetitions").finiteDouble("referenceRepetitions"),
            modelConfigId = modelConfigId,
            modelVersion = untext(values.required("modelVersion")),
            evidencePolicyIdentity = untext(values.required("evidencePolicyIdentity")),
            support = frontierAtLatestSession.support,
            observedRepMin = values.required("observedRepMin").toIntStrict("observedRepMin"),
            observedRepMax = values.required("observedRepMax").toIntStrict("observedRepMax"),
            observedResistanceMinKg = values.required("observedResistanceMinKg").finiteDouble("observedResistanceMinKg"),
            observedResistanceMaxKg = values.required("observedResistanceMaxKg").finiteDouble("observedResistanceMaxKg"),
            frontierAtLatestSession = frontierAtLatestSession,
            slope = decodeParameter(values.required("slope")),
            frontierTrend = decodeParameter(values.required("frontierTrend")),
            slackScale = decodeParameter(values.required("slackScale")),
            noiseScale = decodeParameter(values.required("noiseScale")),
            observationSlack = observationSlack,
            selectedObservationIds = observationIds,
            selectedSessionIds = sessionIds,
            approximationVersion = untext(values.required("approximationVersion")),
            laplaceValidBasePosteriorMass = values.optionalDouble("laplaceValidBasePosteriorMass"),
            laplaceFiniteDifferenceStep = values.optionalDouble("laplaceFiniteDifferenceStep"),
            posteriorEffectiveNodeCount = values.required("posteriorEffectiveNodeCount").finiteDouble("posteriorEffectiveNodeCount"),
            warnings = decodeList(values.required("warnings")).toSet(),
            posteriorNodes = decodeNodes(values.required("posteriorNodes")),
            mathematicalModelIdentity = math,
            solverDiagnostics = diagnostics,
        )
    }

    private fun encodePlain(fit: DynamicTrendFrontierFit): String = buildString {
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
        line("frontierTrend", parameter(fit.frontierTrend))
        line("slackScale", parameter(fit.slackScale))
        line("noiseScale", parameter(fit.noiseScale))
        line("laplaceValidBasePosteriorMass", optionalDouble(fit.laplaceValidBasePosteriorMass))
        line("laplaceFiniteDifferenceStep", optionalDouble(fit.laplaceFiniteDifferenceStep))
        line("posteriorEffectiveNodeCount", fit.posteriorEffectiveNodeCount.toString())
        line("warnings", fit.warnings.sorted().joinToString(",") { text(it) })
        line("selectedObservationIds", fit.selectedObservationIds.joinToString(",") { text(it) })
        line("selectedSessionIds", fit.selectedSessionIds.joinToString(",") { text(it) })
        line("mathFamily", text(fit.mathematicalModelIdentity.family))
        line("mathVersion", text(fit.mathematicalModelIdentity.semanticVersion))
        line("mathDefinition", text(fit.mathematicalModelIdentity.definition))
        val diagnostics = fit.solverDiagnostics
        line("solverFamily", diagnostics.solverIdentity.solverFamily.storageValue)
        line("solverVersion", text(diagnostics.solverIdentity.semanticVersion))
        line("computeBackend", diagnostics.solverIdentity.computeBackend.storageValue)
        line("deterministicReplay", diagnostics.solverIdentity.deterministicReplay.toString())
        line("solverApproximation", text(diagnostics.solverIdentity.approximationDefinition))
        line("posteriorRepresentation", diagnostics.posteriorRepresentation.storageValue)
        line("evaluatedNodeCount", optionalLong(diagnostics.evaluatedNodeCount))
        line("diagnosticEffectiveNodeCount", optionalDouble(diagnostics.effectiveNodeCount))
        line("updateRuntimeNanos", optionalLong(diagnostics.updateRuntimeNanos))
        line("peakWorkingBytes", optionalLong(diagnostics.peakWorkingBytes))
        line("approximationFailure", diagnostics.approximationFailure?.let(::text) ?: "-")
        line("solverNotes", diagnostics.notes.sorted().joinToString(",") { text(it) })
        line("posteriorNodes", fit.posteriorNodes.joinToString(";") { node ->
            listOf(
                node.logFrontierAtLatestSession,
                node.slope,
                node.frontierTrend,
                node.slackScale,
                node.noiseScale,
                node.posteriorWeight,
            ).joinToString(",")
        })
        line("observationSlack", fit.observationSlack.joinToString(";") { encodeSlack(it) })
    }.trimEnd('\n')

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
        require(parts.size == 6)
        return DynamicFrontierParameterPosterior(
            summary = PosteriorSummary(
                credibleLower05 = parts[0].finiteDouble("p05"),
                estimateMedian = parts[1].finiteDouble("p50"),
                credibleUpper95 = parts[2].finiteDouble("p95"),
                posteriorVariance = parts[3].finiteDouble("variance"),
            ),
            identification = enumByStorage(untext(parts[4]), DynamicParameterIdentification.entries) { it.storageValue },
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
        require(value.isNotBlank())
        return value.split(';').map { encoded ->
            val parts = encoded.split(',', limit = 8)
            require(parts.size == 8)
            DynamicObservationSlackPosterior(
                observationId = untext(parts[0]),
                summary = PosteriorSummary(
                    credibleLower05 = parts[2].finiteDouble("slackP05"),
                    estimateMedian = parts[3].finiteDouble("slackP50"),
                    credibleUpper95 = parts[4].finiteDouble("slackP95"),
                    posteriorVariance = parts[5].finiteDouble("slackVariance"),
                ),
                identification = enumByStorage(untext(parts[1]), DynamicParameterIdentification.entries) { it.storageValue },
                massPoints = parts[7].split('|').map { mass ->
                    val pair = mass.split(':')
                    require(pair.size == 2)
                    DynamicSlackPosteriorMass(pair[0].finiteDouble("slack"), pair[1].finiteDouble("slackProbability"))
                },
                semanticDefinition = untext(parts[6]),
            )
        }
    }

    private fun decodeNodes(value: String): List<DynamicTrendFrontierPosteriorNode> {
        require(value.isNotBlank())
        val nodes = value.split(';').map { encoded ->
            val parts = encoded.split(',')
            require(parts.size == 6)
            DynamicTrendFrontierPosteriorNode(
                logFrontierAtLatestSession = parts[0].finiteDouble("logFrontier"),
                slope = parts[1].finiteDouble("slope"),
                frontierTrend = parts[2].finiteDouble("trend"),
                slackScale = parts[3].finiteDouble("slackScale"),
                noiseScale = parts[4].finiteDouble("noiseScale"),
                posteriorWeight = parts[5].finiteDouble("posteriorWeight"),
            )
        }
        require(kotlin.math.abs(nodes.sumOf { it.posteriorWeight } - 1.0) <= 1e-8)
        return nodes
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_SPEED, true)
        try {
            DeflaterOutputStream(output, deflater).use { it.write(bytes) }
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(encoded: String): String {
        require(encoded.startsWith(PREFIX)) { "Malformed Candidate-v2 compressed parameter payload." }
        val compressed = runCatching { Base64.getUrlDecoder().decode(encoded.removePrefix(PREFIX)) }
            .getOrElse { throw IllegalArgumentException("Malformed Candidate-v2 parameter base64.", it) }
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
                    require(total <= MAX_DECOMPRESSED_BYTES) { "Candidate-v2 parameter payload exceeds supported size." }
                    output.write(buffer, 0, read)
                }
                return output.toString(Charsets.UTF_8.name())
            }
        } finally {
            inflater.end()
        }
    }

    private fun StringBuilder.line(key: String, value: String) {
        require(key.isNotBlank() && !key.contains('='))
        append(key).append('=').append(value).append('\n')
    }

    private fun Map<String, String>.required(key: String): String = get(key)
        ?: throw IllegalArgumentException("Missing Candidate-v2 parameter field $key")

    private fun Map<String, String>.optionalDouble(key: String): Double? = required(key).let { if (it == "-") null else it.finiteDouble(key) }
    private fun Map<String, String>.optionalLong(key: String): Long? = required(key).let { if (it == "-") null else it.toLongOrNull() ?: error("Invalid $key") }
    private fun Map<String, String>.optionalText(key: String): String? = required(key).let { if (it == "-") null else untext(it) }
    private fun optionalDouble(value: Double?): String = value?.toString() ?: "-"
    private fun optionalLong(value: Long?): String = value?.toString() ?: "-"

    private fun String.finiteDouble(name: String): Double = toDoubleOrNull()?.takeIf { it.isFinite() }
        ?: throw IllegalArgumentException("Candidate-v2 parameter $name must be finite.")
    private fun String.toIntStrict(name: String): Int = toIntOrNull()
        ?: throw IllegalArgumentException("Candidate-v2 parameter $name must be an integer.")

    private fun decodeList(value: String): List<String> = if (value.isBlank()) emptyList() else value.split(',').map(::untext)
    private fun text(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun untext(value: String): String = runCatching { String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8) }
        .getOrElse { throw IllegalArgumentException("Malformed Candidate-v2 encoded text.", it) }

    private fun <T> enumByStorage(value: String, entries: List<T>, storage: (T) -> String): T =
        entries.firstOrNull { storage(it) == value }
            ?: throw IllegalArgumentException("Unsupported Candidate-v2 enum value $value")
}
