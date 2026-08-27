package dev.kian.mymettle.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.kian.mymettle.data.local.DatabaseProvider
import dev.kian.mymettle.data.migration.LegacyV6Importer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LegacyImportUiState(
    val importing: Boolean = false,
    val completedGeneration: Int = 0,
    val error: String? = null,
)

/**
 * One-shot bootstrap importer for bringing the canonical My Mettle Lite backup into a fresh
 * Native development database. File reading and translation stay outside Compose; the normal
 * workout view model only needs to refresh after a successful import.
 */
class LegacyImportViewModel(
    private val appContext: Context,
    private val importer: LegacyV6Importer,
) : ViewModel() {
    var uiState by androidx.compose.runtime.mutableStateOf(LegacyImportUiState())
        private set

    fun importBackup(uri: Uri) {
        if (uiState.importing) return
        viewModelScope.launch {
            uiState = uiState.copy(importing = true, error = null)
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        reader.readText()
                    } ?: error("Could not open the selected My Mettle Lite backup.")
                }
                withContext(Dispatchers.IO) {
                    importer.importJson(json)
                }
            }.onSuccess {
                uiState = uiState.copy(
                    importing = false,
                    completedGeneration = uiState.completedGeneration + 1,
                    error = null,
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    importing = false,
                    error = error.message ?: error::class.java.simpleName,
                )
            }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }
}

class LegacyImportViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = DatabaseProvider.get(appContext)
        return LegacyImportViewModel(
            appContext = appContext,
            importer = LegacyV6Importer(appContext, database),
        ) as T
    }
}
