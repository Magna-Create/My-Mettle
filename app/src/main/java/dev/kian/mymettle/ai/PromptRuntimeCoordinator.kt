package dev.kian.mymettle.ai

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PromptRuntimeSnapshot(
    val systemProvider: PromptProviderSnapshot = PromptProviderSnapshot(
        providerId = PromptProviderId.SYSTEM,
        availability = PromptProviderAvailability.NOT_CHECKED,
    ),
    val localModel: LocalModelSnapshot = LocalModelSnapshot(LocalModelLifecycleState.NOT_INSTALLED),
    val resolution: PromptProviderResolution? = null,
    val refreshInProgress: Boolean = false,
    val lastRefreshEpochMillis: Long? = null,
    val lastRefreshFailure: PromptProviderFailure? = null,
)

class PromptRuntimeCoordinator(
    private val scope: CoroutineScope,
    private val systemProbe: PromptProviderProbe,
    private val localLifecycle: LocalModelLifecycle,
    private val defaultRequirements: PromptTaskRequirements = PromptTaskRequirements.DIAGNOSTIC_TEXT_STRUCTURED,
    private val preferenceProvider: () -> PromptProviderPreference = { PromptProviderPreference.AUTO },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(PromptRuntimeSnapshot())
    val state: StateFlow<PromptRuntimeSnapshot> = mutableState.asStateFlow()

    private val refreshLock = Any()
    private var refreshJob: Job? = null

    /**
     * Coalescing policy: while one refresh is active, every caller receives that same Job. A newer
     * probe cannot start until the active one finishes, so an older result cannot overwrite it.
     */
    fun requestRefresh(): Job = synchronized(refreshLock) {
        refreshJob?.takeIf { it.isActive } ?: scope.launch {
            refreshOnce()
        }.also { refreshJob = it }
    }

    fun resolveCurrent(
        requirements: PromptTaskRequirements,
        preference: PromptProviderPreference,
    ): PromptProviderResolution {
        val current = state.value
        return PromptProviderResolver.resolve(
            requirements = requirements,
            preference = preference,
            system = current.systemProvider,
            localModel = current.localModel,
        )
    }

    private suspend fun refreshOnce() {
        mutableState.update { it.copy(refreshInProgress = true) }
        try {
            val (system, local) = coroutineScope {
                val systemDeferred = async { safeSystemProbe() }
                val localDeferred = async { safeLocalProbe() }
                systemDeferred.await() to localDeferred.await()
            }
            val resolution = PromptProviderResolver.resolve(
                requirements = defaultRequirements,
                preference = preferenceProvider(),
                system = system,
                localModel = local,
            )
            mutableState.value = PromptRuntimeSnapshot(
                systemProvider = system,
                localModel = local,
                resolution = resolution,
                refreshInProgress = false,
                lastRefreshEpochMillis = nowEpochMillis(),
                lastRefreshFailure = system.failure ?: local.lastFailure,
            )
        } catch (cancelled: CancellationException) {
            mutableState.update {
                it.copy(
                    refreshInProgress = false,
                    lastRefreshFailure = PromptProviderFailure(
                        kind = PromptProviderFailureKind.CANCELLED,
                        errorClass = cancelled::class.java.simpleName,
                    ),
                )
            }
            throw cancelled
        }
    }

    private suspend fun safeSystemProbe(): PromptProviderSnapshot = try {
        systemProbe.probe()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        PromptProviderSnapshot(
            providerId = PromptProviderId.SYSTEM,
            availability = PromptProviderAvailability.UNKNOWN,
            failure = PromptProviderFailure(
                kind = PromptProviderFailureKind.TEMPORARY_FAILURE,
                errorClass = error::class.java.simpleName,
            ),
        )
    }

    private suspend fun safeLocalProbe(): LocalModelSnapshot = try {
        localLifecycle.probe()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        LocalModelSnapshot(
            lifecycleState = LocalModelLifecycleState.FAILED,
            lastFailure = PromptProviderFailure(
                kind = PromptProviderFailureKind.TEMPORARY_FAILURE,
                errorClass = error::class.java.simpleName,
            ),
        )
    }
}
