package dev.kian.mymettle.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.EvidenceFamily
import dev.kian.mymettle.domain.inference.EvidenceSupport
import dev.kian.mymettle.domain.inference.InferenceComputeBackend
import dev.kian.mymettle.domain.inference.InferenceMathematicalModelIdentity
import dev.kian.mymettle.domain.inference.InferencePosteriorRepresentation
import dev.kian.mymettle.domain.inference.InferenceSolverDiagnostics
import dev.kian.mymettle.domain.inference.InferenceSolverFamily
import dev.kian.mymettle.domain.inference.InferenceSolverIdentity
import dev.kian.mymettle.domain.inference.ModelConfigId
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFit
import dev.kian.mymettle.domain.inference.NonDynamicParameterPosterior
import dev.kian.mymettle.domain.inference.NonDynamicPosteriorNode
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.UnitId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Room14 parameter codec for all three 7C families. Joint posterior nodes are required for arbitrary queries. */
object NonDynamicCapabilityParameterCodec {
    const val SCHEMA_VERSION = 2
    private const val MAGIC = "NBIO7C_FIT_V2"
    private const val PREFIX = "n7c2:"
    private const val MAX_STRING_BYTES = 4 * 1024 * 1024

    fun encode(fit: NonDynamicCapabilityFit): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
        deflate(encodeBinary(fit, includeOperationalTelemetry = true)),
    )

    fun scientificallyEquivalent(left: NonDynamicCapabilityFit, right: NonDynamicCapabilityFit): Boolean =
        encodeBinary(left, includeOperationalTelemetry = false).contentEquals(
            encodeBinary(right, includeOperationalTelemetry = false),
        )

    fun decode(
        parameterSchemaVersion: Int,
        encodedParameters: String,
        frontierAtReference: PosteriorEstimate,
        executionProfileVersionId: ExecutionProfileVersionId,
        side: Laterality,
        modelConfigId: ModelConfigId,
    ): NonDynamicCapabilityFit {
        require(parameterSchemaVersion == SCHEMA_VERSION) {
            "Unsupported N-BIO-7C parameter schema $parameterSchemaVersion; refusing to guess future state."
        }
        require(encodedParameters.startsWith(PREFIX)) { "Unsupported N-BIO-7C parameter payload prefix." }
        val bytes = inflate(Base64.getUrlDecoder().decode(encodedParameters.removePrefix(PREFIX)))
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readString() == MAGIC)
            val storedProfile = ExecutionProfileVersionId(input.readString())
            val storedSide = Laterality.entries.first { it.storageValue == input.readString() }
            val family = MetricFamily.entries.first { it.storageValue == input.readString() }
            val horizon = Instant.parse(input.readString())
            val reference = input.readNullableDouble()
            val canonicalUnit = UnitId.entries.first { it.storageValue == input.readString() }
            val storedConfigId = ModelConfigId(input.readString())
            require(storedProfile == executionProfileVersionId && storedSide == side && storedConfigId == modelConfigId) {
                "N-BIO-7C codec envelope does not match persisted capability-state identity."
            }
            val math = InferenceMathematicalModelIdentity(input.readString(), input.readString(), input.readString())
            val solverIdentity = InferenceSolverIdentity(
                solverFamily = InferenceSolverFamily.entries.first { it.storageValue == input.readString() },
                semanticVersion = input.readString(),
                computeBackend = InferenceComputeBackend.entries.first { it.storageValue == input.readString() },
                deterministicReplay = input.readBoolean(),
                approximationDefinition = input.readString(),
            )
            val diagnostics = InferenceSolverDiagnostics(
                solverIdentity = solverIdentity,
                posteriorRepresentation = InferencePosteriorRepresentation.entries.first { it.storageValue == input.readString() },
                evaluatedNodeCount = input.readNullableLong(),
                effectiveNodeCount = input.readNullableDouble(),
                updateRuntimeNanos = input.readNullableLong(),
                peakWorkingBytes = input.readNullableLong(),
                approximationFailure = input.readNullableString(),
                notes = input.readStringList().toSet(),
            )
            val evidencePolicy = input.readString()
            val support = EvidenceSupport(
                observationCount = input.readInt(),
                effectiveIndependentSessionCount = input.readInt(),
                firstEvidenceAt = input.readNullableString()?.let(Instant::parse),
                lastEvidenceAt = input.readNullableString()?.let(Instant::parse),
                evidenceFamily = EvidenceFamily(input.readString()),
            )
            val observedInputMin = input.readNullableDouble()
            val observedInputMax = input.readNullableDouble()
            val observedOutputMin = input.readDouble()
            val observedOutputMax = input.readDouble()
            val slope = input.readNullableParameterPosterior()
            val trajectory = input.readParameterPosterior()
            val slack = input.readParameterPosterior()
            val noise = input.readParameterPosterior()
            val nodeCount = input.readInt()
            require(nodeCount > 0)
            val nodes = List(nodeCount) {
                NonDynamicPosteriorNode(
                    logFrontierAtReference = input.readDouble(),
                    slope = input.readNullableDouble(),
                    trajectory = input.readDouble(),
                    slackScale = input.readDouble(),
                    noiseScale = input.readDouble(),
                    posteriorWeight = input.readDouble(),
                )
            }
            val selectedObservationIds = input.readStringList()
            val selectedSessionIds = input.readStringList()
            val originalBaseNodeCount = input.readInt()
            val retainedBaseNodeCount = input.readInt()
            val warnings = input.readStringList().toSet()
            require(input.available() == 0) { "N-BIO-7C parameter payload has trailing unsupported fields." }
            require(frontierAtReference.support == support) { "Persisted scalar capability support differs from joint 7C state." }
            return NonDynamicCapabilityFit(
                executionProfileVersionId = storedProfile,
                side = storedSide,
                family = family,
                inferenceHorizon = horizon,
                referenceCoordinate = reference,
                canonicalUnit = canonicalUnit,
                modelConfigId = storedConfigId,
                mathematicalModelIdentity = math,
                solverDiagnostics = diagnostics,
                evidencePolicyIdentity = evidencePolicy,
                support = support,
                observedInputMin = observedInputMin,
                observedInputMax = observedInputMax,
                observedOutputMin = observedOutputMin,
                observedOutputMax = observedOutputMax,
                frontierAtReference = frontierAtReference,
                slope = slope,
                trajectory = trajectory,
                slackScale = slack,
                noiseScale = noise,
                posteriorNodes = nodes,
                selectedObservationIds = selectedObservationIds,
                selectedSessionIds = selectedSessionIds,
                originalBaseNodeCount = originalBaseNodeCount,
                retainedBaseNodeCount = retainedBaseNodeCount,
                warnings = warnings,
            )
        }
    }

    private fun encodeBinary(fit: NonDynamicCapabilityFit, includeOperationalTelemetry: Boolean): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeString(MAGIC)
            output.writeString(fit.executionProfileVersionId.value)
            output.writeString(fit.side.storageValue)
            output.writeString(fit.family.storageValue)
            output.writeString(fit.inferenceHorizon.toString())
            output.writeNullableDouble(fit.referenceCoordinate)
            output.writeString(fit.canonicalUnit.storageValue)
            output.writeString(fit.modelConfigId.value)
            output.writeString(fit.mathematicalModelIdentity.family)
            output.writeString(fit.mathematicalModelIdentity.semanticVersion)
            output.writeString(fit.mathematicalModelIdentity.definition)
            val solver = fit.solverDiagnostics.solverIdentity
            output.writeString(solver.solverFamily.storageValue)
            output.writeString(solver.semanticVersion)
            output.writeString(solver.computeBackend.storageValue)
            output.writeBoolean(solver.deterministicReplay)
            output.writeString(solver.approximationDefinition)
            output.writeString(fit.solverDiagnostics.posteriorRepresentation.storageValue)
            output.writeNullableLong(if (includeOperationalTelemetry) fit.solverDiagnostics.evaluatedNodeCount else null)
            output.writeNullableDouble(if (includeOperationalTelemetry) fit.solverDiagnostics.effectiveNodeCount else null)
            output.writeNullableLong(if (includeOperationalTelemetry) fit.solverDiagnostics.updateRuntimeNanos else null)
            output.writeNullableLong(if (includeOperationalTelemetry) fit.solverDiagnostics.peakWorkingBytes else null)
            output.writeNullableString(fit.solverDiagnostics.approximationFailure)
            output.writeStringList(if (includeOperationalTelemetry) fit.solverDiagnostics.notes.sorted() else emptyList())
            output.writeString(fit.evidencePolicyIdentity)
            output.writeInt(fit.support.observationCount)
            output.writeInt(fit.support.effectiveIndependentSessionCount)
            output.writeNullableString(fit.support.firstEvidenceAt?.toString())
            output.writeNullableString(fit.support.lastEvidenceAt?.toString())
            output.writeString(fit.support.evidenceFamily.value)
            output.writeNullableDouble(fit.observedInputMin)
            output.writeNullableDouble(fit.observedInputMax)
            output.writeDouble(fit.observedOutputMin)
            output.writeDouble(fit.observedOutputMax)
            output.writeNullableParameterPosterior(fit.slope)
            output.writeParameterPosterior(fit.trajectory)
            output.writeParameterPosterior(fit.slackScale)
            output.writeParameterPosterior(fit.noiseScale)
            output.writeInt(fit.posteriorNodes.size)
            fit.posteriorNodes.forEach { node ->
                output.writeDouble(node.logFrontierAtReference)
                output.writeNullableDouble(node.slope)
                output.writeDouble(node.trajectory)
                output.writeDouble(node.slackScale)
                output.writeDouble(node.noiseScale)
                output.writeDouble(node.posteriorWeight)
            }
            output.writeStringList(fit.selectedObservationIds)
            output.writeStringList(fit.selectedSessionIds)
            output.writeInt(fit.originalBaseNodeCount)
            output.writeInt(fit.retainedBaseNodeCount)
            output.writeStringList(fit.warnings.sorted())
        }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.writeParameterPosterior(value: NonDynamicParameterPosterior) {
        writeDouble(value.summary.p05)
        writeDouble(value.summary.p50)
        writeDouble(value.summary.p95)
        writeDouble(value.summary.posteriorVariance)
        writeString(value.identification.storageValue)
        writeString(value.semanticUnit)
    }

    private fun DataOutputStream.writeNullableParameterPosterior(value: NonDynamicParameterPosterior?) {
        writeBoolean(value != null)
        if (value != null) writeParameterPosterior(value)
    }

    private fun DataInputStream.readParameterPosterior(): NonDynamicParameterPosterior = NonDynamicParameterPosterior(
        summary = PosteriorSummary(readDouble(), readDouble(), readDouble(), readDouble()),
        identification = DynamicParameterIdentification.entries.first { it.storageValue == readString() },
        semanticUnit = readString(),
    )

    private fun DataInputStream.readNullableParameterPosterior(): NonDynamicParameterPosterior? =
        if (readBoolean()) readParameterPosterior() else null

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "N-BIO-7C codec string exceeds $MAX_STRING_BYTES UTF-8 bytes." }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val length = readInt()
        require(length in 0..MAX_STRING_BYTES) { "N-BIO-7C codec string length $length is invalid." }
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableDouble(value: Double?) { writeBoolean(value != null); if (value != null) writeDouble(value) }
    private fun DataInputStream.readNullableDouble(): Double? = if (readBoolean()) readDouble() else null
    private fun DataOutputStream.writeNullableLong(value: Long?) { writeBoolean(value != null); if (value != null) writeLong(value) }
    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null
    private fun DataOutputStream.writeNullableString(value: String?) { writeBoolean(value != null); if (value != null) writeString(value) }
    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readString() else null
    private fun DataOutputStream.writeStringList(values: List<String>) { writeInt(values.size); values.forEach(::writeString) }
    private fun DataInputStream.readStringList(): List<String> = List(readInt()) { readString() }

    private fun deflate(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private fun inflate(bytes: ByteArray): ByteArray = InflaterInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
}
