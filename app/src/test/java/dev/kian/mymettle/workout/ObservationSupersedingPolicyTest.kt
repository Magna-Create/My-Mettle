package dev.kian.mymettle.workout

import dev.kian.mymettle.data.local.entity.SetObservationEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ObservationSupersedingPolicyTest {
    @Test
    fun `append-only correction leaves history stored and selects only the leaf`() {
        val original = observation("A")
        val correction = observation("B", supersedes = "A", ordinal = 1)

        ObservationSupersedingPolicy.validateAppend(
            newObservationId = "B",
            predecessor = original,
            existing = listOf(original),
            setRecordId = original.setRecordId,
            executionProfileVersionId = original.executionProfileVersionId,
            side = original.side,
        )

        assertEquals(listOf("B"), ObservationSupersedingPolicy.current(listOf(original, correction)).map { it.id })
        assertEquals(listOf("A", "B"), listOf(original, correction).map { it.id })
    }

    @Test
    fun `forks cycles and semantic changes are rejected`() {
        val original = observation("A")
        val correction = observation("B", supersedes = "A", ordinal = 1)
        val cycle = listOf(
            observation("A", supersedes = "B"),
            observation("B", supersedes = "A", ordinal = 1),
        )

        assertFailsWith<NativeWorkoutException> {
            ObservationSupersedingPolicy.validateAppend(
                newObservationId = "C",
                predecessor = original,
                existing = listOf(original, correction),
                setRecordId = original.setRecordId,
                executionProfileVersionId = original.executionProfileVersionId,
                side = original.side,
            )
        }
        assertFailsWith<NativeWorkoutException> { ObservationSupersedingPolicy.current(cycle) }
        assertFailsWith<NativeWorkoutException> {
            ObservationSupersedingPolicy.validateAppend(
                newObservationId = "B",
                predecessor = original,
                existing = listOf(original),
                setRecordId = original.setRecordId,
                executionProfileVersionId = original.executionProfileVersionId,
                side = "right",
            )
        }
    }

    private fun observation(
        id: String,
        supersedes: String? = null,
        ordinal: Int = 0,
    ) = SetObservationEntity(
        id = id,
        setRecordId = "set_1",
        executionProfileVersionId = "profile_1:v1",
        ordinal = ordinal,
        side = "left",
        completedAt = "2026-08-24T10:00:00Z",
        recordedAt = "2026-08-24T10:00:00Z",
        source = "test",
        bodyMassContextKg = null,
        bodyMassContextSource = null,
        supersedesObservationId = supersedes,
    )
}
