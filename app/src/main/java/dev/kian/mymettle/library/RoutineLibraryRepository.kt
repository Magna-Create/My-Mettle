package dev.kian.mymettle.library

import androidx.room.withTransaction
import dev.kian.mymettle.data.migration.LegacyTargetProjector
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import java.time.Instant
import java.util.UUID

class RoutineLibraryRepository(private val database: MyMettleDatabase) {
    private val dao get() = database.workoutDao()

    suspend fun board(): RoutineBoard? {
        val state = dao.appState() ?: return null
        val version = dao.routineVersion(state.currentRoutineVersionId) ?: return null
        val storedDays = dao.routineDays(version.id)
        val days = (DEFAULT_DAYS + storedDays).distinct()
        val slotsByDay = days.associateWith { day -> dao.routineSlots(version.id, day) }
        val exerciseIds = slotsByDay.values.flatten().map { it.exerciseId }.distinct()
        val exerciseNames = if (exerciseIds.isEmpty()) emptyMap() else {
            dao.exercises(exerciseIds).associate { it.id to it.name }
        }
        return RoutineBoard(
            versionId = version.id,
            version = version.version,
            days = days.map { day ->
                RoutineBoardDay(
                    symbol = day,
                    slots = slotsByDay.getValue(day).map { slot ->
                        slot.toBoardSlot(exerciseNames[slot.exerciseId] ?: "Unknown exercise")
                    },
                )
            },
        )
    }

    suspend fun commitDraft(draft: RoutineEditDraft): RoutineBoard = database.withTransaction {
        val state = dao.appState() ?: error("App state is missing.")
        require(state.currentRoutineVersionId == draft.baseVersionId) {
            "The routine changed while this draft was open. Reopen the editor and try again."
        }
        val base = dao.routineVersion(draft.baseVersionId) ?: error("Routine version is missing.")
        val originalDays = dao.routineDays(base.id)
        val originalSlots = originalDays.flatMap { dao.routineSlots(base.id, it) }
        val draftSlots = draft.days.flatMap { it.slots }
        require(draftSlots.map { it.id }.distinct().size == draftSlots.size) {
            "A routine draft cannot contain the same slot identity twice."
        }

        val now = Instant.now().toString()
        val nextId = "routine_${UUID.randomUUID()}"
        val changeReason = describeChange(originalSlots, draftSlots)
        dao.upsertRoutineVersions(
            listOf(
                RoutineVersionEntity(
                    id = nextId,
                    version = base.version + 1,
                    parentId = base.id,
                    createdAt = now,
                    effectiveAt = now,
                    source = "native-library",
                    changeReason = changeReason,
                ),
            ),
        )
        val originalById = originalSlots.associateBy { it.id }
        val nextSlots = draft.days.flatMap { day ->
            day.slots.mapIndexed { index, slot ->
                originalById[slot.id]
                    ?.copy(
                        routineVersionId = nextId,
                        daySymbol = day.symbol,
                        position = index,
                    )
                    ?: RoutineSlotEntity(
                        id = slot.id,
                        routineVersionId = nextId,
                        daySymbol = day.symbol,
                        exerciseId = slot.exerciseId,
                        position = index,
                        importance = slot.importance,
                        lockedToDay = slot.lockedToDay,
                        preferredSets = slot.preferredSets,
                        repMin = slot.repMin,
                        repMax = slot.repMax,
                        restSeconds = slot.restSeconds,
                    )
            }
        }
        dao.upsertRoutineSlots(nextSlots)

        val membershipChanged = originalSlots.associate { it.id to it.daySymbol } !=
            nextSlots.associate { it.id to it.daySymbol }
        val nextTargets = if (membershipChanged) {
            // Programme intent stays independent from exercise identity, but moving/adding/removing
            // slots needs a defensible new baseline. Re-project PRIME recruitment with explicit
            // provenance; historical versions retain their original targets untouched.
            val exerciseIds = nextSlots.map { it.exerciseId }.distinct()
            val profiles = if (exerciseIds.isEmpty()) emptyList() else dao.executionProfiles(exerciseIds)
            val profileIds = profiles.map { it.id }
            val recruitment = if (profileIds.isEmpty()) emptyList() else dao.recruitmentAllocations(profileIds)
            LegacyTargetProjector.project(
                routineSlots = nextSlots,
                executionProfiles = profiles,
                sessions = emptyList(),
                sessionExercises = emptyList(),
                recruitment = recruitment,
            ).programmeTargets.map { target ->
                target.copy(
                    id = "programme_target_${UUID.randomUUID()}",
                    routineVersionId = nextId,
                    source = "native-library-structural-projection-v1",
                )
            }
        } else {
            dao.programmeTargetsForRoutine(base.id).map { target ->
                target.copy(
                    id = "programme_target_${UUID.randomUUID()}",
                    routineVersionId = nextId,
                )
            }
        }
        dao.upsertProgrammeTargets(nextTargets)
        dao.upsertProgrammeModeConstraints(
            dao.programmeModeConstraintsForRoutine(base.id).map {
                it.copy(
                    routineVersionId = nextId,
                )
            },
        )
        dao.upsertAppState(state.copy(currentRoutineVersionId = nextId, updatedAt = now))
        requireNotNull(board())
    }

    private fun describeChange(
        original: List<RoutineSlotEntity>,
        edited: List<RoutineBoardSlot>,
    ): String {
        val originalIds = original.mapTo(mutableSetOf()) { it.id }
        val editedIds = edited.mapTo(mutableSetOf()) { it.id }
        val additions = (editedIds - originalIds).size
        val removals = (originalIds - editedIds).size
        val originalDay = original.associate { it.id to it.daySymbol }
        val movedDays = edited.count { slot -> originalDay[slot.id]?.let { it != slot.daySymbol } == true }
        return buildList {
            if (additions > 0) add("added $additions")
            if (removals > 0) add("removed $removals")
            if (movedDays > 0) add("moved $movedDays between days")
            if (isEmpty()) add("reordered exercises")
        }.joinToString(prefix = "Library edit: ", separator = ", ")
    }

    private fun RoutineSlotEntity.toBoardSlot(exerciseName: String) = RoutineBoardSlot(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        daySymbol = daySymbol,
        position = position,
        importance = importance,
        preferredSets = preferredSets,
        repMin = repMin,
        repMax = repMax,
        restSeconds = restSeconds,
        lockedToDay = lockedToDay,
    )

    private companion object {
        val DEFAULT_DAYS = listOf("ψ", "φ", "π", "&")
    }
}
