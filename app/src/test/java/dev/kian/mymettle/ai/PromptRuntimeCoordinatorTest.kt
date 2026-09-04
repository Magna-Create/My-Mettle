package dev.kian.mymettle.ai

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

class PromptRuntimeCoordinatorTest {
    @Test
    fun providerProbeExceptionBecomesExplicitUnknownFailureWithoutCrashingRefresh() = runBlocking {
        val coordinator = PromptRuntimeCoordinator(
            scope = this,
            systemProbe = PromptProviderProbe { throw IllegalStateException("fixture failure") },
            localLifecycle = NoOpLocalModelLifecycle(),
            defaultRequirements = PromptTaskRequirements.of(PromptCapability.TEXT),
            nowEpochMillis = { 1234L },
        )

        coordinator.requestRefresh().join()

        val state = coordinator.state.value
        assertEquals(PromptProviderAvailability.UNKNOWN, state.systemProvider.availability)
        assertEquals(PromptProviderFailureKind.TEMPORARY_FAILURE, state.systemProvider.failure?.kind)
        assertEquals("IllegalStateException", state.systemProvider.failure?.errorClass)
        assertFalse(state.refreshInProgress)
        assertEquals(1234L, state.lastRefreshEpochMillis)
    }

    @Test
    fun repeatedRefreshCoalescesOntoTheActiveProbe() = runBlocking {
        val calls = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = PromptRuntimeCoordinator(
            scope = this,
            systemProbe = PromptProviderProbe {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                readySystem()
            },
            localLifecycle = NoOpLocalModelLifecycle(),
            defaultRequirements = PromptTaskRequirements.of(PromptCapability.TEXT),
        )

        val first = coordinator.requestRefresh()
        started.await()
        val second = coordinator.requestRefresh()
        assertSame(first, second)

        release.complete(Unit)
        first.join()
        assertEquals(1, calls.get())
        assertEquals(PromptProviderId.SYSTEM, coordinator.state.value.resolution?.selectedProvider)
    }

    @Test
    fun debugBuildDoesNotActivateLabProcessStartup() {
        assertFalse(LabAiRuntime.isEnabled)
        LabAiRuntime.onProcessStart()
        assertEquals(PromptProviderAvailability.NOT_CHECKED, LabAiRuntime.state.value.systemProvider.availability)
        assertFalse(LabAiRuntime.state.value.refreshInProgress)
    }

    @Test
    fun localRemovalFailureRemainsDiagnosticWhileReadySystemWins() = runBlocking {
        val local = FakeLocalLifecycle(readyLocal())
        local.failRemoval = true
        val removal = local.remove()
        assertTrue(removal is LocalModelOperationResult.Rejected)

        val coordinator = PromptRuntimeCoordinator(
            scope = this,
            systemProbe = PromptProviderProbe { readySystem() },
            localLifecycle = local,
            defaultRequirements = PromptTaskRequirements.of(PromptCapability.TEXT),
        )
        coordinator.requestRefresh().join()

        val resolution = coordinator.state.value.resolution
        assertEquals(PromptProviderId.SYSTEM, resolution?.selectedProvider)
        assertEquals(LocalRetirementState.FAILED, resolution?.localRetirement)
        assertEquals(PromptProviderFailureKind.TEMPORARY_FAILURE, coordinator.state.value.localModel.lastFailure?.kind)
    }

    @Test
    fun successfulLocalRemovalLeavesSystemSelectedAndNoRetirementWork() = runBlocking {
        val local = FakeLocalLifecycle(readyLocal())
        val removal = local.remove()
        assertTrue(removal is LocalModelOperationResult.Completed)

        val coordinator = PromptRuntimeCoordinator(
            scope = this,
            systemProbe = PromptProviderProbe { readySystem() },
            localLifecycle = local,
            defaultRequirements = PromptTaskRequirements.of(PromptCapability.TEXT),
        )
        coordinator.requestRefresh().join()

        val resolution = coordinator.state.value.resolution
        assertEquals(PromptProviderId.SYSTEM, resolution?.selectedProvider)
        assertEquals(LocalRetirementState.NOT_REQUIRED, resolution?.localRetirement)
    }

    private fun readySystem(): PromptProviderSnapshot = PromptProviderSnapshot(
        providerId = PromptProviderId.SYSTEM,
        availability = PromptProviderAvailability.READY,
        capabilities = PromptCapabilitySnapshot.of(
            PromptCapability.TEXT to PromptCapabilitySupport.SUPPORTED,
        ),
    )

    private fun readyLocal(): LocalModelSnapshot = LocalModelSnapshot(
        lifecycleState = LocalModelLifecycleState.READY_VERIFIED,
        metadata = LocalModelMetadata(
            modelId = "fixture-model",
            modelVersion = "1",
            runtimeId = "fixture-runtime",
            runtimeVersion = "1",
            assetSizeBytes = 1L,
            integritySha256 = "fixture-sha256",
            declaredCapabilities = PromptCapabilitySnapshot.of(
                PromptCapability.TEXT to PromptCapabilitySupport.SUPPORTED,
            ),
        ),
    )

    private class FakeLocalLifecycle(
        private var current: LocalModelSnapshot,
    ) : LocalModelLifecycle {
        var failRemoval: Boolean = false

        override suspend fun probe(): LocalModelSnapshot = current

        override suspend fun install(): LocalModelOperationResult = LocalModelOperationResult.Completed(current)

        override suspend fun verify(): LocalModelOperationResult = LocalModelOperationResult.Completed(current)

        override suspend fun remove(): LocalModelOperationResult {
            return if (failRemoval) {
                val failure = PromptProviderFailure(
                    kind = PromptProviderFailureKind.TEMPORARY_FAILURE,
                    errorClass = "FixtureRemovalFailure",
                )
                current = current.copy(
                    removalState = LocalRemovalState.FAILED,
                    lastFailure = failure,
                )
                LocalModelOperationResult.Rejected(failure)
            } else {
                current = LocalModelSnapshot(
                    lifecycleState = LocalModelLifecycleState.NOT_INSTALLED,
                    removalState = LocalRemovalState.REMOVED,
                )
                LocalModelOperationResult.Completed(current)
            }
        }
    }
}
