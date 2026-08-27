package dev.kian.mymettle.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.kian.mymettle.data.backup.NativeFullBackupRepository
import dev.kian.mymettle.data.local.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NativeBackupUiState(
    val restoring: Boolean = false,
    val completedGeneration: Int = 0,
    val error: String? = null,
)

class NativeBackupViewModel(
    private val appContext: Context,
    private val backups: NativeFullBackupRepository,
) : ViewModel() {
    var uiState by androidx.compose.runtime.mutableStateOf(NativeBackupUiState())
        private set

    fun restoreBackup(uri: Uri) {
        if (uiState.restoring) return
        viewModelScope.launch {
            uiState = uiState.copy(restoring = true, error = null)
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: error("Could not open the selected My Mettle Native backup.")
                }
                backups.restoreJson(json)
            }.onSuccess {
                uiState = uiState.copy(
                    restoring = false,
                    completedGeneration = uiState.completedGeneration + 1,
                    error = null,
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    restoring = false,
                    error = error.message ?: error::class.java.simpleName,
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
