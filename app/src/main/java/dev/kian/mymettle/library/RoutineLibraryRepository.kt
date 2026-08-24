package dev.kian.mymettle.library

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.RoutineMetricTargetEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.domain.performance.MetricTarget
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitId
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
        val slotIds = slotsByDay.values.flatten().map { it.id }
        val targetsBySlot = if (slotIds.isEmpty()) emptyMap() else {
            dao.routineMetricTargets(version.id, slotIds).groupBy { it.slotId }
        }
        return RoutineBoard(
            versionId = version.id,
            version = version.version,
            days = days.map { day ->
                RoutineBoardDay(
                    symbol = day,
                    slots = slotsByDay.getValue(day).map { slot ->
                        slot.toBoardSlot(
                            exerciseName = exerciseNames[slot.exerciseId] ?: "Unknown exercise",
                            targets = targetsBySlot[slot.id].orEmpty().map(RoutineMetricTargetEntity::toDomain),
                        )
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
                        restSeconds = slot.restSeconds,
                    )
            }
        }
        dao.upsertRoutineSlots(nextSlots)
        val nextMetricTargets = draft.days.flatMap { it.slots }.flatMap { slot ->
            slot.metricTargets.map { target ->
                RoutineMetricTargetEntity(
                    routineVersionId = nextId,
                    slotId = slot.id,
                    metric = target.metric.storageValue,
                    targetKind = target.kind.storageValue,
                    lowerCanonical = target.lowerCanonical,
                    upperCanonical = target.upperCanonical,
                    canonicalUnit = target.canonicalUnit.storageValue,
                    displayUnit = target.displayUnit.storageValue,
                    source = "native-library-preference-v1",
                    modelVersion = "n-bio-6-routine-metric-target-v1",
                )
            }
        }
        if (nextMetricTargets.isNotEmpty()) dao.upsertRoutineMetricTargets(nextMetricTargets)

        // Routine membership is a scheduling preference, not evidence that programme intent
        // changed. Preserve the independently authored targets when creating the next immutable
        // routine version. A future target editor may change these deliberately, with provenance.
        val nextTargets = dao.programmeTargetsForRoutine(base.id).map { target ->
            target.copy(
                id = "programme_target_${UUID.randomUUID()}",
                routineVersionId = nextId,
            )
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

    private fun RoutineSlotEntity.toBoardSlot(
        exerciseName: String,
        targets: List<MetricTarget>,
    ) = RoutineBoardSlot(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        daySymbol = daySymbol,
        position = position,
        importance = importance,
        preferredSets = preferredSets,
        metricTargets = targets,
        restSeconds = restSeconds,
        lockedToDay = lockedToDay,
    )

    private companion object {
        val DEFAULT_DAYS = listOf("ψ", "φ", "π", "&")
    }
}

private fun RoutineMetricTargetEntity.toDomain(): MetricTarget = MetricTarget(
    metric = PerformanceMetric.fromStorage(metric),
    kind = TargetKind.entries.first { it.storageValue == targetKind },
    lowerCanonical = lowerCanonical,
    upperCanonical = upperCanonical,
    canonicalUnit = UnitId.fromStorage(canonicalUnit),
    displayUnit = UnitId.fromStorage(displayUnit),
    evidence = null,
)
