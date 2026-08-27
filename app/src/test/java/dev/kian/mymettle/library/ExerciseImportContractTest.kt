package dev.kian.mymettle.library

import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.exercise.RecruitmentProfileVersionId
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class ExerciseImportContractTest {
    private val canonicalSegments = setOf(
        "deltoid_acromial_part",
        "supraspinatus_whole",
        "trapezius_descending_part",
        "deltoid_clavicular_part",
    )
    private val validator = ExerciseImportSemanticValidator(canonicalSegments)

    @Test
    fun canonicalExampleIsSemanticallyValidAndWeightsNeedNotSumToOne() {
        val proposal = validator.validate(exampleText())

        assertEquals("cable_lateral_raise", proposal.stableConceptKey)
        assertEquals(4, proposal.recruitment.size)
        assertEquals(2.15, proposal.recruitment.sumOf { it.weighting }, 1e-9)
        assertTrue(proposal.recruitment.sumOf { it.weighting } > 1.0)
        assertTrue(proposal.recruitment.all { it.confidence in 0.0..1.0 })
    }

    @Test
    fun weightingAboveOneIsRejected() {
        val json = exampleJson()
        json.firstAllocation().put("weighting", 1.01)
        assertSemanticFailure(json, "weighting")
    }

    @Test
    fun weightingBelowZeroIsRejected() {
        val json = exampleJson()
        json.firstAllocation().put("weighting", -0.01)
        assertSemanticFailure(json, "weighting")
    }

    @Test
    fun confidenceAboveOneIsRejected() {
        val json = exampleJson()
        json.firstAllocation().put("confidence", 1.01)
        assertSemanticFailure(json, "confidence")
    }

    @Test
    fun confidenceBelowZeroIsRejected() {
        val json = exampleJson()
        json.firstAllocation().put("confidence", -0.01)
        assertSemanticFailure(json, "confidence")
    }

    @Test
    fun unknownCanonicalMuscleSegmentIsRejected() {
        val json = exampleJson()
        json.firstAllocation().put("segmentId", "model_invented_muscle")
        assertSemanticFailure(json, "Unknown canonical muscle segment")
    }

    @Test
    fun invalidUnitMetricPairingIsRejected() {
        val json = exampleJson()
        json.getJSONObject("profile")
            .getJSONObject("performanceSchema")
            .getJSONArray("metrics")
            .getJSONObject(0)
            .put("defaultUnit", "s")
        assertSemanticFailure(json, "cannot use unit")
    }

    @Test
    fun incoherentAssistanceSemanticsAreRejected() {
        val json = exampleJson()
        json.getJSONObject("profile").getJSONObject("resistance").apply {
            put("semantics", "assistance")
            put("externalLoadCoefficient", 0.0)
            put("assistanceCoefficient", 1.0)
        }
        assertSemanticFailure(json, "Assistance resistance")
    }

    @Test
    fun incoherentBodyweightSemanticsAreRejected() {
        val json = exampleJson()
        json.getJSONObject("profile").getJSONObject("resistance").apply {
            put("semantics", "bodyweight")
            put("bodyweightCoefficient", 1.0)
            put("externalLoadCoefficient", 0.0)
            put("assistanceCoefficient", 0.0)
        }
        assertSemanticFailure(json, "Bodyweight resistance")
    }

    @Test
    fun uncalibratedOrdinalCannotMasqueradeAsPhysicalKg() {
        val json = exampleJson()
        json.getJSONObject("profile").getJSONObject("equipment").getJSONObject("calibration").apply {
            put("status", "uncalibrated_ordinal")
            put("physicalUnit", "kg")
        }
        assertSemanticFailure(json, "must not claim a physical unit")
    }

    @Test
    fun malformedProfileIsRejected() {
        val json = exampleJson()
        json.getJSONObject("profile").remove("mechanics")
        assertFailsWith<ExerciseImportSemanticException> { validator.validate(json.toString()) }
    }

    @Test
    fun missingRecruitmentProvenanceIsRejectedRatherThanInvented() {
        val json = exampleJson()
        json.firstAllocation().remove("provenance")
        assertSemanticFailure(json, "missing mandatory provenance")
    }

    @Test
    fun exampleMapsIntoExistingAuthoringRequestOnlyAfterAppAssignsPersistenceIdentity() {
        val proposal = validator.validate(exampleText())
        val identity = ExerciseImportAssignedIdentity(
            exerciseId = ExerciseId("app-exercise-id"),
            executionProfileId = ExecutionProfileId("app-profile-id"),
            executionProfileVersionId = ExecutionProfileVersionId("app-profile-version-id"),
            performanceSchemaId = "app-performance-schema-id",
            recruitmentProfileVersionId = RecruitmentProfileVersionId("app-recruitment-version-id"),
        )

        val request = proposal.toAuthoringRequest(identity, createdAt = "2026-08-27T12:00:00Z")

        assertEquals("app-exercise-id", request.exerciseId.value)
        assertEquals("app-profile-id", request.version.executionProfileId.value)
        assertEquals("app-profile-version-id", request.version.id.value)
        assertEquals("app-performance-schema-id", request.version.schema.id)
        assertEquals("app-recruitment-version-id", request.version.recruitment.id.value)
        assertEquals(2.15, request.version.recruitment.allocations.sumOf { it.weighting }, 1e-9)
        val external = exampleText()
        listOf(
            "roomId",
            "databaseSchemaVersion",
            "inferenceRunId",
            "createdAt",
            "effectiveAt",
            "supersededAt",
            "executionProfileVersionId",
            "recruitmentProfileVersionId",
        ).forEach { internalName -> assertFalse(external.contains("\"$internalName\"")) }
    }

    @Test
    fun contractAssetsUseCurrentNativeNBioSemanticsNotLiteDumpFields() {
        val schema = contractFile("exercise-import.schema.json").readText()
        val example = exampleText()
        val guide = contractFile("EXERCISE_AUTHORING_CONTRACT.md").readText()

        assertTrue(schema.contains("dynamic_resistance"))
        assertTrue(schema.contains("bodyweight_plus_external"))
        assertTrue(schema.contains("machine_level"))
        assertTrue(guide.contains("ExecutionProfileAuthoringRepository"))
        assertTrue(guide.contains("muscle-local"))
        assertTrue(guide.contains("must never be normalised to 100%", ignoreCase = true))
        assertFalse(schema.contains("Room PK"))
        assertFalse(example.contains("databaseSchemaVersion"))
        assertFalse(example.contains("Lite"))
    }

    private fun assertSemanticFailure(json: JSONObject, messageFragment: String) {
        val failure = assertFailsWith<ExerciseImportSemanticException> { validator.validate(json) }
        assertTrue(
            failure.message.orEmpty().contains(messageFragment, ignoreCase = true),
            "Expected semantic failure containing '$messageFragment', got '${failure.message}'.",
        )
    }

    private fun exampleJson(): JSONObject = JSONObject(exampleText())

    private fun exampleText(): String = contractFile("exercise-import-example.json").readText()

    private fun JSONObject.firstAllocation(): JSONObject = getJSONObject("profile")
        .getJSONObject("recruitment")
        .getJSONArray("allocations")
        .getJSONObject(0)

    private fun contractFile(name: String): File {
        val candidates = listOf(
            File("docs/n-bio-vnext/$name"),
            File("../docs/n-bio-vnext/$name"),
            File("../../docs/n-bio-vnext/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Cannot locate docs/n-bio-vnext/$name from ${File(".").absolutePath}")
    }
}
