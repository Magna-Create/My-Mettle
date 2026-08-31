package dev.kian.mymettle.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.kian.mymettle.data.backup.NativeFullBackupRepository
import dev.kian.mymettle.data.local.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NativeBackupUiState(
    val exporting: Boolean = false,
    val restoring: Boolean = false,
    val exportedGeneration: Int = 0,
    val completedGeneration: Int = 0,
    val restoredTableCount: Int? = null,
    val restoredRowCount: Int? = null,
    val error: String? = null,
)

class NativeBackupViewModel(
    private val appContext: Context,
    private val backups: NativeFullBackupRepository,
) : ViewModel() {
    var uiState by androidx.compose.runtime.mutableStateOf(NativeBackupUiState())
        private set

    fun exportBackup(uri: Uri) {
        if (uiState.exporting || uiState.restoring) return
        viewModelScope.launch {
            uiState = uiState.copy(exporting = true, error = null)
            runCatching {
                val json = backups.exportJson()
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri, "wt")
                        ?.bufferedWriter(Charsets.UTF_8)
                        ?.use { it.write(json) }
                        ?: error("Could not open the selected destination for the Native backup.")
                }
            }.onSuccess {
                uiState = uiState.copy(
                    exporting = false,
                    exportedGeneration = uiState.exportedGeneration + 1,
                    error = null,
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    exporting = false,
                    error = error.message ?: error::class.java.simpleName,
                )
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        if (uiState.exporting || uiState.restoring) return
        viewModelScope.launch {
            uiState = uiState.copy(
                restoring = true,
                restoredTableCount = null,
                restoredRowCount = null,
                error = null,
            )
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: error("Could not open the selected My Mettle Native backup.")
                }
                backups.restoreJson(json)
            }.onSuccess { result ->
                // Restore uses raw SupportSQLiteDatabase statements. Explicitly refresh Room's
                // invalidation tracker so repositories cannot keep serving the pre-restore view.
                DatabaseProvider.get(appContext).invalidationTracker.refreshAsync()
                uiState = uiState.copy(
                    restoring = false,
                    completedGeneration = uiState.completedGeneration + 1,
                    restoredTableCount = result.tableCount,
                    restoredRowCount = result.rowCount,
                    error = null,
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    restoring = false,
                    error = buildString {
                        append(error.message ?: error::class.java.simpleName)
                        error.cause?.message?.takeIf { it.isNotBlank() }?.let { cause ->
                            append("\nCause: ")
                            append(cause)
                        }
                    },
                )
            }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }
}

class NativeBackupViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = DatabaseProvider.get(appContext)
        return NativeBackupViewModel(
            appContext = appContext,
            backups = NativeFullBackupRepository(database),
        ) as T
    }
}
