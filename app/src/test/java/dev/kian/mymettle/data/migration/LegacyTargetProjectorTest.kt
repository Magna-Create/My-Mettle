package dev.kian.mymettle.data.migration

import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.ExecutionProfileVersionEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyTargetProjectorTest {
    @Test
    fun `projects prime intent once and snapshots its independent session resolution`() {
        val projection = LegacyTargetProjector.project(
            routineSlots = listOf(
                routineSlot(id = "slot_press", position = 0, importance = "principal"),
                routineSlot(id = "slot_press_repeat", position = 1, importance = "accessory"),
            ),
            executionProfiles = listOf(defaultProfile()),
            executionProfileVersions = listOf(executionVersion()),
            sessions = listOf(session()),
            sessionExercises = listOf(sessionExercise(included = true)),
            recruitment = listOf(
                allocation("pectoralis_major_clavicular_part", "prime"),
                allocation("triceps_brachii_lateral_head", "synergist"),
                allocation("serratus_anterior_whole", "stabiliser"),
            ),
        )

        assertEquals(1, projection.programmeTargets.size)
        assertEquals("pectoralis_major_clavicular_part", projection.programmeTargets.single().muscleSegmentId)
        assertEquals(1.0, projection.programmeTargets.single().priority)
        assertEquals(LegacyTargetProjector.SOURCE, projection.programmeTargets.single().source)
        assertEquals(1, projection.sessionTargets.size)
        assertEquals(1, projection.sessionExerciseTargets.size)
        assertEquals(
            projection.sessionTargets.single().id,
            projection.sessionExerciseTargets.single().sessionTargetId,
        )
    }

    @Test
    fun `does not bind excluded historical exercises to prescribed targets`() {
        val projection = LegacyTargetProjector.project(
            routineSlots = listOf(routineSlot("slot_press", 0, "principal")),
            executionProfiles = listOf(defaultProfile()),
            executionProfileVersions = listOf(executionVersion()),
            sessions = listOf(session()),
            sessionExercises = listOf(sessionExercise(included = false)),
            recruitment = listOf(allocation("pectoralis_major_clavicular_part", "prime")),
        )

        assertEquals(1, projection.sessionTargets.size)
        assertTrue(projection.sessionExerciseTargets.isEmpty())
    }

    private fun routineSlot(id: String, position: Int, importance: String) = RoutineSlotEntity(
        id = id,
        routineVersionId = "routine_v1",
        daySymbol = "psi",
        exerciseId = "exercise_press",
        position = position,
        importance = importance,
        lockedToDay = false,
        preferredSets = 3,
        restSeconds = 120,
    )

    private fun defaultProfile() = ExerciseExecutionProfileEntity(
        id = "execution_press_default",
        exerciseId = "exercise_press",
        name = "Default",
        isDefault = true,
        archived = false,
    )

    private fun executionVersion() = ExecutionProfileVersionEntity(
        id = "execution_press_default:v1",
        executionProfileId = "execution_press_default",
        version = 1,
        metricFamily = "dynamic_resistance",
        performanceSchemaId = "schema_press:v1",
        equipmentIdentity = "Cable",
        equipmentType = "cable",
        resistanceSemantics = "external",
        resistanceModelVersion = "legacy-v1",
        bodyweightCoefficient = 0.0,
        externalLoadCoefficient = 1.0,
        assistanceCoefficient = 0.0,
        entryBasis = "total",
        implementCount = null,
        lateralityMode = "unknown",
        romClass = null,
        techniqueClass = null,
        resistanceCurveClass = null,
        movementPattern = null,
        jointActionsJson = null,
        kineticChain = null,
        contractionType = null,
        gripSupportConstraintsJson = null,
        recruitmentProfileVersionId = "recruitment_press:v1",
        createdAt = "2026-08-10T00:00:00Z",
        effectiveAt = "2026-08-10T00:00:00Z",
        supersededAt = null,
        provenance = "test",
        modelVersion = "test-v1",
    )

    private fun allocation(segmentId: String, role: String) = RecruitmentAllocationEntity(
        recruitmentProfileVersionId = "recruitment_press:v1",
        muscleSegmentId = segmentId,
        role = role,
        weighting = 1.0,
        confidence = 0.8,
        provenanceType = "test",
        provenanceReference = null,
        applicableRom = null,
        applicableTechnique = null,
        resistanceCurveClass = null,
        modelVersion = "test-v1",
    )

    private fun session() = SessionEntity(
        id = "session_1",
        cycleId = "cycle_1",
        daySymbol = "psi",
        mode = "A",
        routineVersionId = "routine_v1",
        status = "completed",
        startedAt = "2026-08-10T10:00:00Z",
        completedAt = "2026-08-10T11:00:00Z",
        editedAt = null,
        discardedAt = null,
        excludedFromInsights = false,
        bodyweightSnapshotKg = null,
        healthExportState = "not_requested",
        healthClientRecordId = null,
    )

    private fun sessionExercise(included: Boolean) = SessionExerciseEntity(
        id = "session_exercise_1",
        sessionId = "session_1",
        position = 0,
        exerciseId = "exercise_press",
        slotId = "slot_press",
        exerciseNameSnapshot = "Press",
        importanceSnapshot = "principal",
        executionProfileId = "execution_press_default",
        executionProfileVersionId = "execution_press_default:v1",
        executionProfileNameSnapshot = "Default",
        prescriptionMode = "A",
        prescriptionIncluded = included,
        restSeconds = 120,
        generatedByModelVersion = "legacy-v6-session-snapshot-v1",
        deferToAnd = false,
        status = "completed",
        note = null,
        startedAt = "2026-08-10T10:00:00Z",
        completedAt = "2026-08-10T10:15:00Z",
        movementReason = "base_routine",
        substitutedFromExerciseId = null,
    )
}
