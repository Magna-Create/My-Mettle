package dev.kian.mymettle.domain.evidence

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant

class EvidenceCodecException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Deterministic canonical BLOB codec. Version 1 keeps exact epoch-second+nano timestamps and raw
 * IEEE-754 values. Missing samples are represented by absence; no interpolation or cleaning occurs.
 */
object TemporalEvidenceCodec {
    const val ENCODING_VERSION = 1
    private const val MAGIC = 0x4E424536 // NBE6
    private const val MAX_SAMPLES = 1_000_000

    fun encode(payload: EvidencePayload): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(ENCODING_VERSION)
            output.writeByte(payload.representation.code)
            output.writeInt(payload.sampleCount)
            when (payload) {
                is EvidencePayload.Points -> payload.samples.forEach { sample ->
                    output.writeInstant(sample.timestamp)
                    output.writeDouble(sample.canonicalValue)
                }
                is EvidencePayload.Intervals -> payload.samples.forEach { sample ->
                    output.writeInstant(sample.startedAt)
                    output.writeInstant(sample.endedAt)
                    when (val value = sample.value) {
                        is IntervalEvidenceValue.Numeric -> {
                            output.writeByte(1)
                            output.writeDouble(value.canonicalValue)
                        }
                        is IntervalEvidenceValue.State -> {
                            output.writeByte(2)
                            val encoded = value.value.encodeToByteArray()
                            output.writeInt(encoded.size)
                            output.write(encoded)
                        }
                    }
                }
                is EvidencePayload.Route -> payload.samples.forEach { sample ->
                    output.writeInstant(sample.timestamp)
                    output.writeDouble(sample.latitudeDegrees)
                    output.writeDouble(sample.longitudeDegrees)
                    output.writeNullableDouble(sample.altitudeMetres)
                    output.writeNullableDouble(sample.horizontalAccuracyMetres)
                    output.writeNullableDouble(sample.verticalAccuracyMetres)
                }
            }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray, expected: TemporalRepresentation? = null): EvidencePayload {
        try {
            val inputBytes = ByteArrayInputStream(bytes)
            val payload = DataInputStream(inputBytes).use { input ->
                if (input.readInt() != MAGIC) throw EvidenceCodecException("Temporal payload magic is invalid.")
                val version = input.readInt()
                if (version != ENCODING_VERSION) throw EvidenceCodecException("Unsupported temporal encoding version: $version")
                val representation = TemporalRepresentation.fromCode(input.readUnsignedByte())
                if (expected != null && representation != expected) {
                    throw EvidenceCodecException("Expected ${expected.storageValue}, found ${representation.storageValue}.")
                }
                val count = input.readInt()
                if (count !in 0..MAX_SAMPLES) throw EvidenceCodecException("Temporal sample count is invalid: $count")
                when (representation) {
                    TemporalRepresentation.POINT_SERIES -> EvidencePayload.Points(
                        List(count) { PointEvidenceSample(input.readInstant(), input.readDouble()) },
                    )
                    TemporalRepresentation.INTERVAL_SERIES -> EvidencePayload.Intervals(
                        List(count) {
                            val start = input.readInstant()
                            val end = input.readInstant()
                            val value = when (val kind = input.readUnsignedByte()) {
                                1 -> IntervalEvidenceValue.Numeric(input.readDouble())
                                2 -> {
                                    val size = input.readInt()
                                    if (size !in 1..1_048_576) throw EvidenceCodecException("Interval state length is invalid: $size")
                                    val state = ByteArray(size)
                                    input.readFully(state)
                                    IntervalEvidenceValue.State(state.decodeToString())
                                }
                                else -> throw EvidenceCodecException("Unsupported interval value kind: $kind")
                            }
                            IntervalEvidenceSample(start, end, value)
                        },
                    )
                    TemporalRepresentation.SPATIAL_ROUTE -> EvidencePayload.Route(
                        List(count) {
                            SpatialRouteSample(
                                timestamp = input.readInstant(),
                                latitudeDegrees = input.readDouble(),
                                longitudeDegrees = input.readDouble(),
                                altitudeMetres = input.readNullableDouble(),
                                horizontalAccuracyMetres = input.readNullableDouble(),
                                verticalAccuracyMetres = input.readNullableDouble(),
                            )
                        },
                    )
                }.also {
                    if (inputBytes.available() != 0) throw EvidenceCodecException("Temporal payload contains trailing bytes.")
                }
            }
            return payload
        } catch (error: EvidenceCodecException) {
            throw error
        } catch (error: EOFException) {
            throw EvidenceCodecException("Temporal payload is truncated.", error)
        } catch (error: DateTimeException) {
            throw EvidenceCodecException("Temporal payload contains an invalid timestamp.", error)
        } catch (error: IllegalArgumentException) {
            throw EvidenceCodecException(error.message ?: "Temporal payload is invalid.", error)
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private val TemporalRepresentation.code: Int get() = when (this) {
        TemporalRepresentation.POINT_SERIES -> 1
        TemporalRepresentation.INTERVAL_SERIES -> 2
        TemporalRepresentation.SPATIAL_ROUTE -> 3
    }

    private fun TemporalRepresentation.Companion.fromCode(code: Int): TemporalRepresentation = when (code) {
        1 -> TemporalRepresentation.POINT_SERIES
        2 -> TemporalRepresentation.INTERVAL_SERIES
        3 -> TemporalRepresentation.SPATIAL_ROUTE
        else -> throw EvidenceCodecException("Unsupported temporal representation code: $code")
    }

    private fun DataOutputStream.writeInstant(value: Instant) {
        writeLong(value.epochSecond)
        writeInt(value.nano)
    }

    private fun DataInputStream.readInstant(): Instant = Instant.ofEpochSecond(readLong(), readInt().toLong())

    private fun DataOutputStream.writeNullableDouble(value: Double?) {
        writeBoolean(value != null)
        if (value != null) writeDouble(value)
    }

    private fun DataInputStream.readNullableDouble(): Double? = if (readBoolean()) readDouble() else null
}
