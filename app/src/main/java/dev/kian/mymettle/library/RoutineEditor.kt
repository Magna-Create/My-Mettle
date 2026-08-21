package dev.kian.mymettle.library

data class RoutineBoardSlot(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val daySymbol: String,
    val position: Int,
    val importance: String,
    val preferredSets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val lockedToDay: Boolean,
)

data class RoutineBoardDay(
    val symbol: String,
    val slots: List<RoutineBoardSlot>,
)

data class RoutineBoard(
    val versionId: String,
    val version: Int,
    val days: List<RoutineBoardDay>,
)

data class RoutineEditDraft(
    val baseVersionId: String,
    val days: List<RoutineBoardDay>,
) {
    fun moveWithinDay(slotId: String, targetIndex: Int): RoutineEditDraft {
        val sourceDay = days.firstOrNull { day -> day.slots.any { it.id == slotId } }
            ?: error("Routine slot $slotId does not exist.")
        val sourceIndex = sourceDay.slots.indexOfFirst { it.id == slotId }
        val destination = targetIndex.coerceIn(0, sourceDay.slots.lastIndex)
        if (sourceIndex == destination) return this

        val reordered = sourceDay.slots.toMutableList()
        val moving = reordered.removeAt(sourceIndex)
        reordered.add(destination, moving)
        val normalised = reordered.mapIndexed { index, slot ->
            slot.copy(daySymbol = sourceDay.symbol, position = index)
        }
        return copy(days = days.map { if (it.symbol == sourceDay.symbol) it.copy(slots = normalised) else it })
    }
}

fun RoutineBoard.editDraft(): RoutineEditDraft = RoutineEditDraft(versionId, days)
