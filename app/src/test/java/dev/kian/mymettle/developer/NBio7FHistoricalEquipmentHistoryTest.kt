package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.equipment.EquipmentBindingResolutionSource
import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NBio7FHistoricalEquipmentHistoryTest {
    @Test
    fun `future correction and fact never leak into earlier freeze`() {
        val history = history(
            sessionBinding = NBio7FEquipmentBindingAssertion("equipment-a", time(1)),
            load = NBio7FLoadSemanticsAssertion(ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY, time(1)),
            sessionCorrections = listOf(
                NBio7FEquipmentBindingCorrectionRecord(1, "equipment-a", "equipment-b", time(8)),
            ),
            loadCorrections = listOf(
                NBio7FLoadSemanticsCorrectionRecord(
                    1,
                    ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY,
                    ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
                    time(8),
                ),
            ),
            facts = listOf(
                NBio7FEquipmentFactRecord("fact-a-v1", "equipment-a", "implement_mass", 1, time(1), time(1)),
                NBio7FEquipmentFactRecord("fact-a-v2", "equipment-a", "implement_mass", 2, time(9), time(1)),
            ),
        )

        val resolved = NBio7FCausalEquipmentHistory.resolveObservation(
            history = history,
            observationId = OBSERVATION,
            observationAt = time(5),
            knowledgeAt = time(5),
        )

        assertEquals(EquipmentId("equipment-a"), resolved.equipmentId)
        assertEquals(EquipmentBindingResolutionSource.SESSION_EXERCISE, resolved.resolutionSource)
        assertEquals(ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY, resolved.loadAccounting)
        assertEquals(setOf("fact-a-v1"), resolved.equipmentFactVersionIds)
        assertEquals(2, resolved.futureCorrectionsExcluded)
        assertEquals(1, resolved.futureFactsExcluded)
        assertTrue(resolved.unavailabilityReasons.isEmpty())
    }

    @Test
    fun `correction known before freeze applies and retracted override falls back to session actual`() {
        val history = history(
            sessionBinding = NBio7FEquipmentBindingAssertion("equipment-a", time(1)),
            override = NBio7FEquipmentBindingAssertion("equipment-c", time(2)),
            load = NBio7FLoadSemanticsAssertion(ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY, time(1)),
            overrideCorrections = listOf(
                NBio7FEquipmentBindingCorrectionRecord(1, "equipment-c", null, time(3)),
            ),
            sessionCorrections = listOf(
                NBio7FEquipmentBindingCorrectionRecord(1, "equipment-a", "equipment-b", time(4)),
            ),
        )

        val resolved = NBio7FCausalEquipmentHistory.resolveObservation(
            history = history,
            observationId = OBSERVATION,
            observationAt = time(5),
            knowledgeAt = time(5),
        )

        assertEquals(EquipmentId("equipment-b"), resolved.equipmentId)
        assertEquals(EquipmentBindingResolutionSource.SESSION_EXERCISE, resolved.resolutionSource)
        assertEquals(ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY, resolved.loadAccounting)
        assertEquals(0, resolved.futureCorrectionsExcluded)
    }

    @Test
    fun `mixed equipment and unknown load remain explicit at capability boundary`() {
        val first = NBio7FCausalEquipmentObservation(
            observationId = "observation-a",
            sessionExerciseId = "session-exercise-a",
            equipmentId = EquipmentId("equipment-a"),
            resolutionSource = EquipmentBindingResolutionSource.SESSION_EXERCISE,
            loadAccounting = ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
            equipmentFactVersionIds = setOf("fact-a"),
            dependencyIds = setOf("dependency-a"),
            futureCorrectionsExcluded = 0,
            futureFactsExcluded = 0,
            unavailabilityReasons = emptySet(),
        )
        val second = NBio7FCausalEquipmentObservation(
            observationId = "observation-b",
            sessionExerciseId = "session-exercise-b",
            equipmentId = EquipmentId("equipment-b"),
            resolutionSource = EquipmentBindingResolutionSource.SESSION_EXERCISE,
            loadAccounting = null,
            equipmentFactVersionIds = setOf("fact-b"),
            dependencyIds = setOf("dependency-b"),
            futureCorrectionsExcluded = 1,
            futureFactsExcluded = 2,
            unavailabilityReasons = setOf(
                NBio7FHistoricalEquipmentUnavailabilityReason.LOAD_SEMANTICS_NOT_KNOWN_AT_FREEZE,
            ),
        )

        val summary = NBio7FCausalEquipmentHistory.summariseCapability(listOf(first, second))

        val equipment = assertIs<NBio7FHistoricalCapabilityEquipmentContext.Mixed>(summary.equipment)
        assertEquals(setOf(EquipmentId("equipment-a"), EquipmentId("equipment-b")), equipment.equipmentIds)
        val load = assertIs<NBio7FHistoricalCapabilityLoadAccounting.Unknown>(summary.loadAccounting)
        assertTrue(
            NBio7FHistoricalEquipmentUnavailabilityReason.LOAD_SEMANTICS_NOT_KNOWN_AT_FREEZE in load.reasons,
        )
        assertEquals(1, summary.futureCorrectionsExcluded)
        assertEquals(2, summary.futureFactsExcluded)
    }

    @Test
    fun `invalid correction chain fails closed instead of rewriting history`() {
        val history = history(
            sessionBinding = NBio7FEquipmentBindingAssertion("equipment-a", time(1)),
            load = NBio7FLoadSemanticsAssertion(ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD, time(1)),
            sessionCorrections = listOf(
                NBio7FEquipmentBindingCorrectionRecord(1, "wrong-previous", "equipment-b", time(3)),
            ),
        )

        val resolved = NBio7FCausalEquipmentHistory.resolveObservation(
            history = history,
            observationId = OBSERVATION,
            observationAt = time(4),
            knowledgeAt = time(5),
        )

        assertEquals(null, resolved.equipmentId)
        assertTrue(
            NBio7FHistoricalEquipmentUnavailabilityReason.EQUIPMENT_CORRECTION_CHAIN_INVALID in
                resolved.unavailabilityReasons,
        )
    }

    private fun history(
        sessionBinding: NBio7FEquipmentBindingAssertion,
        override: NBio7FEquipmentBindingAssertion? = null,
        load: NBio7FLoadSemanticsAssertion,
        sessionCorrections: List<NBio7FEquipmentBindingCorrectionRecord> = emptyList(),
        overrideCorrections: List<NBio7FEquipmentBindingCorrectionRecord> = emptyList(),
        loadCorrections: List<NBio7FLoadSemanticsCorrectionRecord> = emptyList(),
        facts: List<NBio7FEquipmentFactRecord> = emptyList(),
    ) = NBio7FHistoricalEquipmentHistory(
        sessionExerciseByObservation = mapOf(OBSERVATION to SESSION_EXERCISE),
        equipmentCreatedAt = mapOf(
            "equipment-a" to time(0),
            "equipment-b" to time(0),
            "equipment-c" to time(0),
        ),
        sessionBindings = mapOf(SESSION_EXERCISE to sessionBinding),
        observationOverrides = override?.let { mapOf(OBSERVATION to it) }.orEmpty(),
        loadSemantics = mapOf(OBSERVATION to load),
        sessionBindingCorrections = mapOf(SESSION_EXERCISE to sessionCorrections),
        observationOverrideCorrections = mapOf(OBSERVATION to overrideCorrections),
        loadSemanticsCorrections = mapOf(OBSERVATION to loadCorrections),
        factsByEquipment = facts.groupBy { it.equipmentId },
    )

    private fun time(day: Int): Instant = BASE.plusSeconds(day * DAY_SECONDS)

    private companion object {
        const val OBSERVATION = "observation-1"
        const val SESSION_EXERCISE = "session-exercise-1"
        const val DAY_SECONDS = 86_400L
        val BASE: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
