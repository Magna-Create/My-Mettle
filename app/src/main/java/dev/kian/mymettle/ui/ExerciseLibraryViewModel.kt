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
import dev.kian.mymettle.library.ExerciseLibraryRepository
import dev.kian.mymettle.library.LibraryExercise
import java.io.File
import kotlinx.coroutines.launch

data class ExerciseLibraryUiState(
    val loading: Boolean = true,
    val savingMedia: Boolean = false,
    val exercises: List<LibraryExercise> = emptyList(),
    val query: String = "",
    val selected: LibraryExercise? = null,
    val error: String? = null,
) {
    val visibleExercises: List<LibraryExercise>
        get() {
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) return exercises
            return exercises.filter { item ->
                item.exercise.name.lowercase().contains(needle) ||
                    item.memory?.category?.lowercase()?.contains(needle) == true ||
                    item.memory?.equipment?.lowercase()?.contains(needle) == true ||
                    item.targetMuscles.any { it.lowercase().contains(needle) }
            }
        }
}

class ExerciseLibraryViewModel(
    private val repository: ExerciseLibraryRepository,
) : ViewModel() {
    var uiState by mutableStateOf(ExerciseLibraryUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repository.all() }
                .onSuccess { items -> uiState = uiState.copy(loading = false, exercises = items) }
                .onFailure(::showError)
        }
    }

    fun setQuery(value: String) {
        uiState = uiState.copy(query = value.take(80))
    }

    fun select(item: LibraryExercise?) {
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

    private fun replaceSelected(updated: LibraryExercise) {
        uiState = uiState.copy(
            savingMedia = false,
            exercises = uiState.exercises.map { if (it.exercise.id == updated.exercise.id) updated else it },
            selected = updated,
        )
    }

    private fun showError(error: Throwable) {
        uiState = uiState.copy(
            loading = false,
            savingMedia = false,
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
        ) as T
    }
}
