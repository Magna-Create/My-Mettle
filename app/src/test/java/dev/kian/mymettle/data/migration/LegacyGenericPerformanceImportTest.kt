package dev.kian.mymettle.data.migration

import dev.kian.mymettle.domain.evidence.AcquisitionMethod
import dev.kian.mymettle.domain.evidence.EvidenceGranularity
import dev.kian.mymettle.domain.evidence.TimingQuality
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegacyGenericPerformanceImportTest {
    @Test
    fun `representative Lite schema-6 session preserves factual set evidence`() {
        val json = requireNotNull(javaClass.classLoader?.getResource("fixtures/lite-v6-session.json")).readText()
        val snapshot = LegacyV6BackupReader.read(json)
        val observation = snapshot.setObservations.single()
        val values = snapshot.setMetricValues.associateBy { PerformanceMetric.fromStorage(it.metric) }

        assertEquals("lite_legacy_v6_import", observation.source)
        assertEquals("unknown", observation.side)
        assertNull(observation.bodyMassContextKg)
        assertNull(observation.startedAtEpochSecond)
        assertNull(observation.startedAtNano)
        assertEquals(TimingQuality.COMPLETION_ONLY.storageValue, observation.timingQuality)
        assertEquals(Instant.parse(observation.completedAt).epochSecond, observation.endedAtEpochSecond)
        assertEquals(Instant.parse(observation.completedAt).nano, observation.endedAtNano)
        assertEquals(40.0, values.getValue(PerformanceMetric.EXTERNAL_LOAD).enteredValue)
        assertEquals(UnitId.KILOGRAM.storageValue, values.getValue(PerformanceMetric.EXTERNAL_LOAD).enteredUnit)
        assertEquals(40.0, values.getValue(PerformanceMetric.EXTERNAL_LOAD).canonicalValue)
        assertEquals(9.0, values.getValue(PerformanceMetric.REPETITIONS).canonicalValue)
        assertEquals(AcquisitionMethod.UNKNOWN.storageValue, values.getValue(PerformanceMetric.REPETITIONS).acquisitionMethod)
        assertEquals(EvidenceGranularity.SUMMARY.storageValue, values.getValue(PerformanceMetric.REPETITIONS).evidenceGranularity)
        assertEquals(8.0, snapshot.routineMetricTargets.single().lowerCanonical)
        assertEquals(10.0, snapshot.routineMetricTargets.single().upperCanonical)
        assertEquals(3, snapshot.sessionSetPrescriptions.size)
        assertEquals(6, snapshot.sessionMetricTargets.size)
    }

    @Test
    fun `historical Lite tracking snapshot becomes a superseded semantic version`() {
        val base = requireNotNull(javaClass.classLoader?.getResource("fixtures/lite-v6-session.json")).readText()
        val historical = base.replace(
            "\"trackingSnapshot\": {\n              \"metric\": \"load_reps\"",
            "\"trackingSnapshot\": {\n              \"metric\": \"duration\"",
        ).replace(
            "\"sets\": [\n              {\n                \"id\": \"set_1\",",
            "\"sets\": [\n              {\n                \"id\": \"set_1\",\n                \"durationSeconds\": 45,",
        )
        val snapshot = LegacyV6BackupReader.read(historical)

        assertEquals(2, snapshot.executionProfileVersions.size)
        assertEquals(1, snapshot.executionProfileVersions.count { it.supersededAt == null })
        assertEquals("loaded_hold", snapshot.executionProfileVersions.single { it.version == 2 }.metricFamily)
        assertEquals(
            snapshot.executionProfileVersions.single { it.version == 2 }.id,
            snapshot.setObservations.single().executionProfileVersionId,
        )
    }
}
