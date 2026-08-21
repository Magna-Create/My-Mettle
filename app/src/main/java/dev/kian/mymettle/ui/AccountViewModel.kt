package dev.kian.mymettle.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.kian.mymettle.data.local.DatabaseProvider
import dev.kian.mymettle.profile.ProfileRepository
import kotlinx.coroutines.launch

data class AccountUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val weightKg: String = "",
    val heightCm: String = "",
    val saved: Boolean = false,
    val error: String? = null,
)

class AccountViewModel(private val repository: ProfileRepository) : ViewModel() {
    var uiState by mutableStateOf(AccountUiState())
        private set

    init {
        viewModelScope.launch {
            runCatching { repository.current() }
                .onSuccess { profile ->
                    uiState = uiState.copy(
                        loading = false,
                        weightKg = profile.weightKg?.let(::formatProfileDecimal).orEmpty(),
                        heightCm = profile.heightCm?.let(::formatProfileDecimal).orEmpty(),
                    )
                }
                .onFailure(::showError)
        }
    }

    fun setWeight(value: String) {
        uiState = uiState.copy(weightKg = numericInput(value), saved = false, error = null)
    }

    fun setHeight(value: String) {
        uiState = uiState.copy(heightCm = numericInput(value), saved = false, error = null)
    }

    fun save(onSaved: () -> Unit) {
        if (uiState.saving) return
        val weight = uiState.weightKg.toDoubleOrNull()
        if (weight == null) {
            uiState = uiState.copy(error = "Enter your current weight in kilograms.")
            return
        }
        val height = uiState.heightCm.takeIf(String::isNotBlank)?.toDoubleOrNull()
        if (uiState.heightCm.isNotBlank() && height == null) {
            uiState = uiState.copy(error = "Enter your height in centimetres, or leave it blank.")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(saving = true, saved = false, error = null)
            runCatching { repository.save(weight, height) }
                .onSuccess { profile ->
                    uiState = uiState.copy(
                        loading = false,
                        saving = false,
                        saved = true,
                        weightKg = profile.weightKg?.let(::formatProfileDecimal).orEmpty(),
                        heightCm = profile.heightCm?.let(::formatProfileDecimal).orEmpty(),
                    )
                    onSaved()
                }
                .onFailure(::showError)
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun showError(error: Throwable) {
        uiState = uiState.copy(
            loading = false,
            saving = false,
            error = error.message ?: "Profile could not be updated.",
        )
    }

    private fun numericInput(value: String): String = value
        .filter { it.isDigit() || it == '.' }
        .take(6)

    private fun formatProfileDecimal(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')
}

class AccountViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(
        ProfileRepository(DatabaseProvider.get(appContext)),
    ) as T
}
