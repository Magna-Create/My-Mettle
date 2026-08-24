package dev.kian.mymettle.data.migration

import dev.kian.mymettle.domain.evidence.AcquisitionMethod
import dev.kian.mymettle.domain.evidence.EvidenceGranularity
import dev.kian.mymettle.domain.evidence.TimingQuality
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject

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

    @Test
    fun `legacy conserved muscle proportions do not become N-BIO recruitment`() {
        val root = fixtureRoot()
        root.getJSONObject("database").getJSONArray("exercises").getJSONObject(0).put(
            "muscleLoadModel",
            JSONObject()
                .put("version", 1)
                .put("basis", "Legacy conserved share")
                .put("confidence", 0.8)
                .put(
                    "allocations",
                    JSONArray().put(
                        JSONObject()
                            .put("muscle", "upper chest")
                            .put("proportion", 1.0)
                            .put("role", "prime"),
                    ),
                ),
        )

        val snapshot = LegacyV6BackupReader.read(root.toString())

        assertTrue(snapshot.translatedRecruitment.isEmpty())
        assertFailsWith<LegacyImportException> {
            LegacyTranslationContract.requireActiveRecruitment(snapshot)
        }
    }

    @Test
    fun `reviewed Native supplement uses independent recruitment coefficients`() {
        val root = fixtureRoot().put(
            "nativeTranslation",
            JSONObject()
                .put("version", 1)
                .put("recruitmentSemantics", "independent-muscle-local-exposure-v1")
                .put(
                    "recruitmentProfiles",
                    JSONArray().put(
                        JSONObject()
                            .put("exerciseId", "exercise_press")
                            .put("modelVersion", "reviewed-press-v1")
                            .put("basis", "Reviewed against the exact execution profile")
                            .put("confidence", 0.7)
                            .put(
                                "allocations",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("muscleSegmentId", "pectoralis_major_clavicular_part")
                                            .put("weighting", 0.85)
                                            .put("role", "prime"),
                                    )
                                    .put(
                                        JSONObject()
                                            .put("muscleSegmentId", "triceps_brachii_lateral_head")
                                            .put("weighting", 0.55)
                                            .put("role", "synergist"),
                                    ),
                            ),
                    ),
                ),
        )

        val snapshot = LegacyV6BackupReader.read(root.toString())

        LegacyTranslationContract.requireActiveRecruitment(snapshot)
        assertEquals(listOf(0.85, 0.55), snapshot.translatedRecruitment.map { it.weighting })
        assertEquals("reviewed-press-v1", snapshot.translatedRecruitment.single { it.role == "prime" }.modelVersion)
    }

    private fun fixtureRoot(): JSONObject = JSONObject(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/lite-v6-session.json")).readText(),
    )
}
