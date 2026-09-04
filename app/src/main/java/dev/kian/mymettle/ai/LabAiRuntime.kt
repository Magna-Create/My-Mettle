package dev.kian.mymettle.ai

import dev.kian.mymettle.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

/** Single activation boundary for LAB-1 process-start probing. */
object LabAiRuntime {
    val isEnabled: Boolean
        get() = BuildConfig.UI_ML_LAB

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val coordinator by lazy {
        PromptRuntimeCoordinator(
            scope = scope,
            systemProbe = MlKitSystemPromptProviderProbe(),
            localLifecycle = NoOpLocalModelLifecycle(),
        )
    }

    val state: StateFlow<PromptRuntimeSnapshot>
        get() = coordinator.state

    fun onProcessStart() {
        if (!isEnabled) return
        coordinator.requestRefresh()
    }

    fun refresh() {
        if (!isEnabled) return
        coordinator.requestRefresh()
    }

    fun resolve(
        requirements: PromptTaskRequirements,
        preference: PromptProviderPreference = PromptProviderPreference.AUTO,
    ): PromptProviderResolution = coordinator.resolveCurrent(requirements, preference)
}
