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
        val sourceDay = dayContaining(slotId)
        return move(slotId, sourceDay.symbol, targetIndex)
    }

    /**
     * Release-only placement shared by drag/drop and the accessibility fallback menu.
     * The target index is evaluated after removing the source slot, so a card never oscillates
     * beneath the pointer while it is being dragged.
     */
    fun move(slotId: String, targetDaySymbol: String, targetIndex: Int): RoutineEditDraft {
        val sourceDay = dayContaining(slotId)
        val destinationDay = days.firstOrNull { it.symbol == targetDaySymbol }
            ?: error("Routine day $targetDaySymbol does not exist.")
        val sourceIndex = sourceDay.slots.indexOfFirst { it.id == slotId }
        val targetSlotsWithoutSource = destinationDay.slots.filterNot { it.id == slotId }
        val destination = targetIndex.coerceIn(0, targetSlotsWithoutSource.size)
        if (sourceDay.symbol == destinationDay.symbol && sourceIndex == destination) return this

        val moving = sourceDay.slots[sourceIndex]
        return replaceDays(
            days.map { day ->
                val withoutSource = day.slots.filterNot { it.id == slotId }
                if (day.symbol == destinationDay.symbol) {
                    val placed = withoutSource.toMutableList().apply {
                        add(destination, moving.copy(daySymbol = targetDaySymbol))
                    }
                    day.copy(slots = placed)
                } else {
                    day.copy(slots = withoutSource)
                }
            },
        )
    }

    fun insert(slot: RoutineBoardSlot, targetDaySymbol: String, targetIndex: Int = Int.MAX_VALUE): RoutineEditDraft {
        require(days.none { day -> day.slots.any { it.id == slot.id } }) {
            "Routine slot ${slot.id} already exists."
        }
        val destinationDay = days.firstOrNull { it.symbol == targetDaySymbol }
            ?: error("Routine day $targetDaySymbol does not exist.")
        val destination = targetIndex.coerceIn(0, destinationDay.slots.size)
        return replaceDays(
            days.map { day ->
                if (day.symbol != targetDaySymbol) return@map day
                val placed = day.slots.toMutableList().apply {
                    add(destination, slot.copy(daySymbol = targetDaySymbol))
                }
                day.copy(slots = placed)
            },
        )
    }

    fun duplicate(slotId: String, duplicateId: String): RoutineEditDraft {
        val sourceDay = dayContaining(slotId)
        val sourceIndex = sourceDay.slots.indexOfFirst { it.id == slotId }
        return insert(
            slot = sourceDay.slots[sourceIndex].copy(id = duplicateId),
            targetDaySymbol = sourceDay.symbol,
            targetIndex = sourceIndex + 1,
        )
    }

    fun remove(slotId: String): RoutineEditDraft {
        dayContaining(slotId)
        return replaceDays(days.map { day -> day.copy(slots = day.slots.filterNot { it.id == slotId }) })
    }

    private fun dayContaining(slotId: String): RoutineBoardDay =
        days.firstOrNull { day -> day.slots.any { it.id == slotId } }
            ?: error("Routine slot $slotId does not exist.")

    private fun replaceDays(nextDays: List<RoutineBoardDay>): RoutineEditDraft = copy(
        days = nextDays.map { day ->
            day.copy(
                slots = day.slots.mapIndexed { index, slot ->
                    slot.copy(daySymbol = day.symbol, position = index)
                },
            )
        },
    )
}

fun RoutineBoard.editDraft(): RoutineEditDraft = RoutineEditDraft(versionId, days)
