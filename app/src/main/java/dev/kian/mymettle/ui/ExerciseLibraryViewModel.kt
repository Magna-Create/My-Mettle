package dev.kian.mymettle.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.kian.mymettle.data.local.DatabaseProvider
import dev.kian.mymettle.domain.exercise.Exercise
import dev.kian.mymettle.library.ExerciseLibraryRepository
import dev.kian.mymettle.library.RoutineBoard
import dev.kian.mymettle.library.RoutineBoardSlot
import dev.kian.mymettle.library.RoutineEditDraft
import dev.kian.mymettle.library.RoutineLibraryRepository
import dev.kian.mymettle.library.editDraft
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

data class ExerciseLibraryUiState(
    val loading: Boolean = true,
    val savingMedia: Boolean = false,
    val savingRoutine: Boolean = false,
    val exercises: List<Exercise> = emptyList(),
    val routine: RoutineBoard? = null,
    val routineDraft: RoutineEditDraft? = null,
    val section: LibrarySection = LibrarySection.ROUTINE,
    val query: String = "",
    val selected: Exercise? = null,
    val error: String? = null,
) {
    val visibleExercises: List<Exercise>
        get() {
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) return exercises
            return exercises.filter { item ->
                item.name.lowercase().contains(needle) ||
                    item.memory?.category?.lowercase()?.contains(needle) == true ||
                    item.memory?.equipment?.lowercase()?.contains(needle) == true ||
                    item.executionProfiles.any { profile ->
                        profile.recruitment.allocations.any { it.segmentName.lowercase().contains(needle) }
                    }
            }
        }
}

enum class LibrarySection { ROUTINE, EXERCISES }

class ExerciseLibraryViewModel(
    private val repository: ExerciseLibraryRepository,
    private val routineRepository: RoutineLibraryRepository,
) : ViewModel() {
    var uiState by mutableStateOf(ExerciseLibraryUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repository.all() to routineRepository.board() }
                .onSuccess { (items, routine) ->
                    uiState = uiState.copy(loading = false, exercises = items, routine = routine)
                }
                .onFailure(::showError)
        }
    }

    fun selectSection(section: LibrarySection) {
        uiState = uiState.copy(section = section)
    }

    fun beginRoutineEdit() {
        uiState = uiState.copy(routineDraft = uiState.routine?.editDraft())
    }

    fun cancelRoutineEdit() {
        uiState = uiState.copy(routineDraft = null)
    }

    fun moveRoutineSlot(slotId: String, delta: Int) {
        val draft = uiState.routineDraft ?: return
        val day = draft.days.firstOrNull { candidate -> candidate.slots.any { it.id == slotId } } ?: return
        val current = day.slots.indexOfFirst { it.id == slotId }
        uiState = uiState.copy(routineDraft = draft.moveWithinDay(slotId, current + delta))
    }

    fun placeRoutineSlot(slotId: String, daySymbol: String, index: Int) {
        val draft = uiState.routineDraft ?: return
        uiState = uiState.copy(routineDraft = draft.move(slotId, daySymbol, index))
    }

    fun addExerciseToRoutine(exerciseId: String, daySymbol: String) {
        val draft = uiState.routineDraft ?: return
        val exercise = uiState.exercises.firstOrNull { it.id.value == exerciseId } ?: return
        val exemplar = draft.days.asSequence().flatMap { it.slots.asSequence() }
            .firstOrNull { it.exerciseId == exerciseId }
        val slot = exemplar?.copy(
            id = "slot_${UUID.randomUUID()}",
            daySymbol = daySymbol,
        ) ?: RoutineBoardSlot(
            id = "slot_${UUID.randomUUID()}",
            exerciseId = exerciseId,
            exerciseName = exercise.name,
            daySymbol = daySymbol,
            position = Int.MAX_VALUE,
            importance = "accessory",
            preferredSets = 3,
            repMin = 8,
            repMax = 12,
            restSeconds = 90,
            lockedToDay = false,
        )
        uiState = uiState.copy(routineDraft = draft.insert(slot, daySymbol))
    }

    fun duplicateRoutineSlot(slotId: String) {
        val draft = uiState.routineDraft ?: return
        uiState = uiState.copy(
            routineDraft = draft.duplicate(slotId, "slot_${UUID.randomUUID()}"),
        )
    }

    fun removeRoutineSlot(slotId: String) {
        val draft = uiState.routineDraft ?: return
        uiState = uiState.copy(routineDraft = draft.remove(slotId))
    }

    fun selectExercise(exerciseId: String) {
        select(uiState.exercises.firstOrNull { it.id.value == exerciseId })
    }

    fun saveRoutineEdit() {
        val draft = uiState.routineDraft ?: return
        if (draft == uiState.routine?.editDraft()) {
            cancelRoutineEdit()
            return
        }
        if (uiState.savingRoutine) return
        viewModelScope.launch {
            uiState = uiState.copy(savingRoutine = true, error = null)
            runCatching { routineRepository.commitDraft(draft) }
                .onSuccess { routine ->
                    uiState = uiState.copy(
                        savingRoutine = false,
                        routine = routine,
                        routineDraft = null,
                    )
                }
                .onFailure(::showError)
        }
    }

    fun setQuery(value: String) {
        uiState = uiState.copy(query = value.take(80))
    }

    fun select(item: Exercise?) {
        uiState = uiState.copy(selected = item)
    }

    fun addSetupPhotos(exerciseId: String, uris: List<Uri>) {
        if (uris.isEmpty() || uiState.savingMedia) return
        viewModelScope.launch {
            uiState = uiState.copy(savingMedia = true, error = null)
            runCatching { repository.addSetupPhotos(exerciseId, uris) }
                .onSuccess(::replaceSelected)
                .onFailure(::showError)
        }
    }

    fun addCapturedSetupPhoto(exerciseId: String, captureFile: File) {
        if (uiState.savingMedia) {
            captureFile.delete()
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(savingMedia = true, error = null)
            runCatching { repository.addCapturedSetupPhoto(exerciseId, captureFile) }
                .onSuccess(::replaceSelected)
                .onFailure(::showError)
        }
    }

    fun deleteSetupPhoto(mediaId: String) {
        if (uiState.savingMedia) return
        viewModelScope.launch {
            uiState = uiState.copy(savingMedia = true, error = null)
            runCatching { repository.deleteSetupPhoto(mediaId) }
                .onSuccess(::replaceSelected)
                .onFailure(::showError)
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun replaceSelected(updated: Exercise) {
        uiState = uiState.copy(
            savingMedia = false,
            exercises = uiState.exercises.map { if (it.id == updated.id) updated else it },
            selected = updated,
        )
    }

    private fun showError(error: Throwable) {
        uiState = uiState.copy(
            loading = false,
            savingMedia = false,
            savingRoutine = false,
            error = error.message ?: error::class.java.simpleName,
        )
    }
}

class ExerciseLibraryViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = DatabaseProvider.get(appContext)
        return ExerciseLibraryViewModel(
            ExerciseLibraryRepository(appContext, database),
            RoutineLibraryRepository(database),
        ) as T
    }
}
