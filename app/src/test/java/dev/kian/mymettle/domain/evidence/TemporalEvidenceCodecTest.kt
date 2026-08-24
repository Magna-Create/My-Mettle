package dev.kian.mymettle.domain.evidence

import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TemporalEvidenceCodecTest {
    @Test
    fun pointSeriesRoundTripPreservesNanosIrregularGapsSpikeAndNegativeValues() {
        val samples = listOf(
            point("2026-08-24T08:31:02.100000001Z", 122.0),
            point("2026-08-24T08:31:05.700000009Z", -2.5),
            point("2026-08-24T08:47:10.000000003Z", 248.0),
        )
        val payload = EvidencePayload.Points(samples)

        val encoded = TemporalEvidenceCodec.encode(payload)
        val decoded = TemporalEvidenceCodec.decode(encoded, TemporalRepresentation.POINT_SERIES)

        assertEquals(payload, decoded)
        assertContentEquals(encoded, TemporalEvidenceCodec.encode(payload))
        assertEquals(3, (decoded as EvidencePayload.Points).samples.size)
        assertEquals(Instant.parse("2026-08-24T08:47:10.000000003Z"), decoded.samples.last().timestamp)
        assertEquals(248.0, decoded.samples.last().canonicalValue)
    }

    @Test
    fun intervalSeriesPreservesNumericTotalsAndCategoricalStates() {
        val numeric = EvidencePayload.Intervals(
            listOf(
                IntervalEvidenceSample(
                    Instant.parse("2026-08-24T08:30:00Z"),
                    Instant.parse("2026-08-24T08:35:00Z"),
                    IntervalEvidenceValue.Numeric(1020.0),
                ),
            ),
        )
        val state = EvidencePayload.Intervals(
            listOf(
                IntervalEvidenceSample(
                    Instant.parse("2026-08-24T08:30:00.123456789Z"),
                    Instant.parse("2026-08-24T08:42:00.987654321Z"),
                    IntervalEvidenceValue.State("vigorous"),
                ),
            ),
        )

        assertEquals(numeric, TemporalEvidenceCodec.decode(TemporalEvidenceCodec.encode(numeric)))
        assertEquals(state, TemporalEvidenceCodec.decode(TemporalEvidenceCodec.encode(state)))
        assertEquals(IntervalValueSemantics.TOTAL_OVER_INTERVAL.storageValue, "total_over_interval")
        assertEquals(IntervalValueSemantics.STATE_OVER_INTERVAL.storageValue, "state_over_interval")
    }

    @Test
    fun representationMismatchMalformedAndUnknownVersionsFailSafely() {
        val bytes = TemporalEvidenceCodec.encode(EvidencePayload.Points(listOf(point("2026-08-24T08:30:00Z", 3.0))))
        assertFailsWith<EvidenceCodecException> {
            TemporalEvidenceCodec.decode(bytes, TemporalRepresentation.INTERVAL_SERIES)
        }
        assertFailsWith<EvidenceCodecException> { TemporalEvidenceCodec.decode(bytes.copyOf(bytes.size - 2)) }
        val unknownVersion = bytes.copyOf().also {
            it[4] = 0
            it[5] = 0
            it[6] = 0
            it[7] = 2
        }
        assertFailsWith<EvidenceCodecException> { TemporalEvidenceCodec.decode(unknownVersion) }
    }

    @Test
    fun spatialRouteCodecIsForwardCompatibleWithoutClaimingElevationGain() {
        val route = EvidencePayload.Route(
            listOf(
                SpatialRouteSample(
                    timestamp = Instant.parse("2026-08-24T08:30:00.000000001Z"),
                    latitudeDegrees = 51.5074,
                    longitudeDegrees = -0.1278,
                    altitudeMetres = 34.5,
                    horizontalAccuracyMetres = 3.2,
                    verticalAccuracyMetres = null,
                ),
            ),
        )
        assertEquals(route, TemporalEvidenceCodec.decode(TemporalEvidenceCodec.encode(route), TemporalRepresentation.SPATIAL_ROUTE))
        assertEquals(EvidenceMetricKey.ROUTE.canonicalUnit, null)
        assertNotEquals(EvidenceMetricKey.ELEVATION_GAIN, EvidenceMetricKey.ROUTE)
        val trace = EvidenceTrace(
            id = "route-trace",
            metric = EvidenceMetricKey.ROUTE,
            representation = TemporalRepresentation.SPATIAL_ROUTE,
            intervalSemantics = IntervalValueSemantics.UNSPECIFIED,
            canonicalUnit = null,
            quality = EvidenceQuality(EvidenceGranularity.TRACE, AcquisitionMethod.SENSOR_RECORDED),
            semanticRole = EvidenceSemanticRole.MOVEMENT_CONTEXT,
            bounds = EvidenceTimeBounds(route.samples.first().timestamp, route.samples.last().timestamp, TimingQuality.SOURCE_REPORTED_BOUND),
            provenance = "test",
            createdAt = route.samples.first().timestamp,
            recordedAt = route.samples.first().timestamp,
        )
        assertEquals(TemporalRepresentation.SPATIAL_ROUTE, trace.representation)
    }

    @Test
    fun granularityAcquisitionAndSemanticRoleRemainIndependent() {
        val reportedSummary = EvidenceQuality(EvidenceGranularity.SUMMARY, AcquisitionMethod.USER_REPORTED)
        val estimatedSummary = EvidenceQuality(EvidenceGranularity.SUMMARY, AcquisitionMethod.USER_ESTIMATE)
        val sensorTrace = EvidenceQuality(EvidenceGranularity.TRACE, AcquisitionMethod.SENSOR_RECORDED)

        assertNotEquals(reportedSummary, estimatedSummary)
        assertNotEquals(reportedSummary.granularity, sensorTrace.granularity)
        assertEquals(EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE, EvidenceMetricKey.HEART_RATE.defaultRole)
        assertEquals(EvidenceSemanticRole.PERFORMANCE_OUTPUT, EvidenceMetricKey.SPEED.defaultRole)

        val manualScalar = PerformanceMetricValue(
            PerformanceMetric.INCLINE_GRADE,
            Quantity(4.0, UnitId.PERCENT),
            evidenceQuality = reportedSummary,
            semanticRole = EvidenceSemanticRole.MOVEMENT_CONTEXT,
        )
        assertEquals(EvidenceGranularity.SUMMARY, manualScalar.evidenceQuality.granularity)
        assertEquals(AcquisitionMethod.USER_REPORTED, manualScalar.evidenceQuality.acquisitionMethod)
    }

    @Test
    fun sourceChunksRetainBoundariesAndEqualAveragesDoNotCollapseTraceShape() {
        val steady = EvidencePayload.Points(
            listOf(point("2026-08-24T08:30:00Z", 3.0), point("2026-08-24T08:31:00Z", 3.0)),
        )
        val interval = EvidencePayload.Points(
            listOf(point("2026-08-24T08:30:00Z", 2.0), point("2026-08-24T08:31:00Z", 4.0)),
        )
        assertEquals(steady.samples.map { it.canonicalValue }.average(), interval.samples.map { it.canonicalValue }.average())
        assertNotEquals(
            TemporalEvidenceCodec.sha256(TemporalEvidenceCodec.encode(steady)),
            TemporalEvidenceCodec.sha256(TemporalEvidenceCodec.encode(interval)),
        )

        val first = artifact("artifact_a", "source-record-a", 1)
        val second = artifact("artifact_b", "source-record-b", 1)
        assertNotEquals(first.logicalSourceKey, second.logicalSourceKey)
        assertTrue(first.sourceState != SourceState.DELETED_AT_SOURCE)
        assertNotEquals(SourceState.PERMISSION_UNAVAILABLE, SourceState.DELETED_AT_SOURCE)
    }

    @Test
    fun timingQualityAndUnknownLegacyBoundsRemainRepresentable() {
        val exact = EvidenceTimeBounds(
            Instant.parse("2026-08-24T08:30:00.000000001Z"),
            Instant.parse("2026-08-24T08:30:10.000000009Z"),
            TimingQuality.SOURCE_REPORTED_BOUND,
            sourceZoneOffsetMinutes = 60,
        )
        val legacy = EvidenceTimeBounds(null, Instant.parse("2026-08-24T08:30:10Z"), TimingQuality.LEGACY_UNKNOWN)

        assertEquals(1, exact.startedAt?.nano)
        assertEquals(9, exact.endedAt?.nano)
        assertEquals(null, legacy.startedAt)
        assertEquals(TimingQuality.LEGACY_UNKNOWN, legacy.timingQuality)
    }

    @Test
    fun semanticBoundariesRejectPhysiologyAsPerformanceAndTraceMetadataOnScalars() {
        assertFailsWith<IllegalArgumentException> {
            PerformanceMetricValue(
                PerformanceMetric.SPEED,
                Quantity(3.0, UnitId.METRES_PER_SECOND),
                evidenceQuality = EvidenceQuality(EvidenceGranularity.TRACE, AcquisitionMethod.SENSOR_RECORDED),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EvidenceTrace(
                id = "mislabelled-heart-rate",
                metric = EvidenceMetricKey.HEART_RATE,
                representation = TemporalRepresentation.POINT_SERIES,
                intervalSemantics = IntervalValueSemantics.UNSPECIFIED,
                canonicalUnit = UnitId.BEATS_PER_MINUTE,
                quality = EvidenceQuality(EvidenceGranularity.TRACE, AcquisitionMethod.SENSOR_RECORDED),
                semanticRole = EvidenceSemanticRole.PERFORMANCE_OUTPUT,
                bounds = EvidenceTimeBounds(NOW, NOW.plusSeconds(10), TimingQuality.SOURCE_REPORTED_BOUND),
                provenance = "test",
                createdAt = NOW,
                recordedAt = NOW,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EvidenceTimeBounds(NOW, NOW.plusSeconds(10), TimingQuality.COMPLETION_ONLY)
        }
    }

    private fun point(timestamp: String, value: Double) = PointEvidenceSample(Instant.parse(timestamp), value)

    private fun artifact(id: String, logicalKey: String, revision: Int) = ExternalRecordProvenance(
        id = id,
        logicalSourceKey = logicalKey,
        nativeRevision = revision,
        provider = "fixture",
        dataOrigin = "fixture.origin",
        sourceRecordType = "fixture_record",
        sourceRecordId = logicalKey,
        sourceClientRecordId = null,
        sourceClientRecordVersion = revision.toLong(),
        sourceDeviceManufacturer = null,
        sourceDeviceModel = null,
        sourceDeviceType = null,
        recordingMethod = "sensor",
        sourceLastModifiedAt = Instant.parse("2026-08-24T08:35:00Z"),
        importedAt = Instant.parse("2026-08-24T09:00:00Z"),
        sourceBounds = EvidenceTimeBounds(
            Instant.parse("2026-08-24T08:30:00Z"),
            Instant.parse("2026-08-24T08:35:00Z"),
            TimingQuality.SOURCE_REPORTED_BOUND,
        ),
        sourceState = SourceState.AVAILABLE,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-24T08:30:00Z")
    }
}
