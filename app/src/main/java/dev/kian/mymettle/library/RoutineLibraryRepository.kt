package dev.kian.mymettle.library

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ProgrammeTargetEntity
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

    /**
     * Alpha16 deliberately commits within-day ordering only. Cross-day moves also change N-Bio
     * targets and programme constraints, so they stay disabled until the Native target projector
     * is part of the editor transaction.
     */
    suspend fun commitReorder(draft: RoutineEditDraft): RoutineBoard = database.withTransaction {
        val state = dao.appState() ?: error("App state is missing.")
        require(state.currentRoutineVersionId == draft.baseVersionId) {
            "The routine changed while this draft was open. Reopen the editor and try again."
        }
        val base = dao.routineVersion(draft.baseVersionId) ?: error("Routine version is missing.")
        val originalDays = dao.routineDays(base.id)
        val originalSlots = originalDays.flatMap { dao.routineSlots(base.id, it) }
        val draftSlots = draft.days.flatMap { it.slots }
        require(originalSlots.map { it.id }.toSet() == draftSlots.map { it.id }.toSet()) {
            "This editor pass may reorder slots, but may not add or remove them yet."
        }
        val originalDayBySlot = originalSlots.associate { it.id to it.daySymbol }
        require(draftSlots.all { originalDayBySlot[it.id] == it.daySymbol }) {
            "Moving exercises between days will arrive with Native N-Bio target projection."
        }

        val now = Instant.now().toString()
        val nextId = "routine_${UUID.randomUUID()}"
        dao.upsertRoutineVersions(
            listOf(
                RoutineVersionEntity(
                    id = nextId,
                    version = base.version + 1,
                    parentId = base.id,
                    createdAt = now,
                    effectiveAt = now,
                    source = "native-library",
                    changeReason = "Reordered routine exercises",
                ),
            ),
        )
        val originalById = originalSlots.associateBy { it.id }
        dao.upsertRoutineSlots(
            draft.days.flatMap { day ->
                day.slots.mapIndexed { index, slot ->
                    originalById.getValue(slot.id).copy(
                        routineVersionId = nextId,
                        daySymbol = day.symbol,
                        position = index,
                    )
                }
            },
        )
        dao.upsertProgrammeTargets(
            dao.programmeTargetsForRoutine(base.id).map { target ->
                target.copy(
                    id = "programme_target_${UUID.randomUUID()}",
                    routineVersionId = nextId,
                )
            },
        )
        dao.upsertProgrammeModeConstraints(
            dao.programmeModeConstraintsForRoutine(base.id).map { it.copy(routineVersionId = nextId) },
        )
        dao.upsertAppState(state.copy(currentRoutineVersionId = nextId, updatedAt = now))
        requireNotNull(board())
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
