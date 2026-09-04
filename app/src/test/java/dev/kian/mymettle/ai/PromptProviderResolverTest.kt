package dev.kian.mymettle.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PromptProviderResolverTest {
    private val text = PromptTaskRequirements.of(PromptCapability.TEXT)

    @Test
    fun autoPrefersSufficientReadySystem() {
        val result = resolve(systemReady(PromptCapability.TEXT), localAbsent())
        assertEquals(PromptResolutionKind.SYSTEM_SELECTED, result.kind)
        assertEquals(PromptProviderId.SYSTEM, result.selectedProvider)
    }

    @Test
    fun autoPreservesSystemSetupRequiredInsteadOfInventingLocalSuccess() {
        val result = resolve(system(PromptProviderAvailability.SETUP_REQUIRED), localAbsent())
        assertEquals(PromptResolutionKind.SYSTEM_SETUP_REQUIRED, result.kind)
        assertEquals(PromptProviderId.SYSTEM, result.setupProvider)
    }

    @Test
    fun autoUsesReadyLocalWhileSystemSetupIsInProgress() {
        val result = resolve(system(PromptProviderAvailability.SETUP_IN_PROGRESS), localReady(PromptCapability.TEXT))
        assertEquals(PromptResolutionKind.LOCAL_SELECTED, result.kind)
        assertEquals(PromptProviderId.LOCAL, result.selectedProvider)
        assertEquals(SystemTransitionState.SETUP_IN_PROGRESS, result.systemTransition)
    }

    @Test
    fun autoUsesReadyLocalWhileSystemDownloadIsRequired() {
        val result = resolve(system(PromptProviderAvailability.SETUP_REQUIRED), localReady(PromptCapability.TEXT))
        assertEquals(PromptResolutionKind.LOCAL_SELECTED, result.kind)
        assertEquals(SystemTransitionState.SETUP_REQUIRED, result.systemTransition)
    }

    @Test
    fun autoUsesLocalWhenSystemUnavailable() {
        val result = resolve(system(PromptProviderAvailability.UNAVAILABLE), localReady(PromptCapability.TEXT))
        assertEquals(PromptResolutionKind.LOCAL_SELECTED, result.kind)
    }

    @Test
    fun autoRequestsLocalInstallWhenSystemUnavailableAndLocalAbsent() {
        val result = resolve(system(PromptProviderAvailability.UNAVAILABLE), localAbsent())
        assertEquals(PromptResolutionKind.LOCAL_INSTALL_REQUIRED, result.kind)
        assertEquals(PromptProviderId.LOCAL, result.setupProvider)
    }

    @Test
    fun autoUsesLocalWhenReadySystemLacksRequiredCapability() {
        val requirements = PromptTaskRequirements.of(PromptCapability.TEXT, PromptCapability.STRUCTURED_OUTPUT)
        val system = systemReady(PromptCapability.TEXT)
        val local = localReady(PromptCapability.TEXT, PromptCapability.STRUCTURED_OUTPUT)
        val result = PromptProviderResolver.resolve(requirements, PromptProviderPreference.AUTO, system, local)
        assertEquals(PromptResolutionKind.LOCAL_SELECTED, result.kind)
        assertEquals(PromptProviderId.LOCAL, result.selectedProvider)
    }

    @Test
    fun unknownCapabilityNeverCountsAsSupported() {
        val requirements = PromptTaskRequirements.of(PromptCapability.IMAGE_INPUT)
        val system = PromptProviderSnapshot(
            providerId = PromptProviderId.SYSTEM,
            availability = PromptProviderAvailability.READY,
            capabilities = PromptCapabilitySnapshot.of(
                PromptCapability.IMAGE_INPUT to PromptCapabilitySupport.UNKNOWN,
            ),
        )
        val local = localReady(PromptCapability.IMAGE_INPUT)
        val result = PromptProviderResolver.resolve(requirements, PromptProviderPreference.AUTO, system, local)
        assertEquals(PromptResolutionKind.LOCAL_SELECTED, result.kind)
    }

    @Test
    fun systemOverrideNeverFallsBackToReadyLocal() {
        val result = PromptProviderResolver.resolve(
            requirements = text,
            preference = PromptProviderPreference.SYSTEM,
            system = system(PromptProviderAvailability.UNAVAILABLE),
            localModel = localReady(PromptCapability.TEXT),
        )
        assertEquals(PromptResolutionKind.PROVIDER_UNAVAILABLE, result.kind)
        assertNull(result.selectedProvider)
    }

    @Test
    fun localOverrideNeverSubstitutesReadySystem() {
        val result = PromptProviderResolver.resolve(
            requirements = text,
            preference = PromptProviderPreference.LOCAL,
            system = systemReady(PromptCapability.TEXT),
            localModel = localAbsent(),
        )
        assertEquals(PromptResolutionKind.LOCAL_INSTALL_REQUIRED, result.kind)
        assertNull(result.selectedProvider)
    }

    @Test
    fun autoMovesFromLocalToSystemWhenSystemBecomesSufficient() {
        val local = localReady(PromptCapability.TEXT)
        val before = resolve(system(PromptProviderAvailability.UNAVAILABLE), local)
        val after = resolve(systemReady(PromptCapability.TEXT), local)
        assertEquals(PromptProviderId.LOCAL, before.selectedProvider)
        assertEquals(PromptProviderId.SYSTEM, after.selectedProvider)
    }

    @Test
    fun systemSelectionMarksReadyLocalFallbackForRetirement() {
        val result = resolve(systemReady(PromptCapability.TEXT), localReady(PromptCapability.TEXT))
        assertEquals(PromptProviderId.SYSTEM, result.selectedProvider)
        assertEquals(LocalRetirementState.REQUIRED, result.localRetirement)
        assertEquals(SystemTransitionState.SYSTEM_READY_LOCAL_PRESENT, result.systemTransition)
    }

    @Test
    fun localRetirementFailureDoesNotLieAboutSystemSelection() {
        val local = localReady(
            PromptCapability.TEXT,
            removalState = LocalRemovalState.FAILED,
        )
        val result = resolve(systemReady(PromptCapability.TEXT), local)
        assertEquals(PromptProviderId.SYSTEM, result.selectedProvider)
        assertEquals(LocalRetirementState.FAILED, result.localRetirement)
    }

    @Test
    fun successfulLocalRemovalClearsRetirementRequirement() {
        val local = LocalModelSnapshot(
            lifecycleState = LocalModelLifecycleState.NOT_INSTALLED,
            removalState = LocalRemovalState.REMOVED,
        )
        val result = resolve(systemReady(PromptCapability.TEXT), local)
        assertEquals(PromptProviderId.SYSTEM, result.selectedProvider)
        assertEquals(LocalRetirementState.NOT_REQUIRED, result.localRetirement)
    }

    @Test
    fun systemSetupInProgressWithoutLocalStaysSystemSetupInProgress() {
        val result = resolve(system(PromptProviderAvailability.SETUP_IN_PROGRESS), localAbsent())
        assertEquals(PromptResolutionKind.SYSTEM_SETUP_IN_PROGRESS, result.kind)
        assertEquals(PromptProviderId.SYSTEM, result.setupProvider)
    }

    private fun resolve(
        system: PromptProviderSnapshot,
        local: LocalModelSnapshot,
    ): PromptProviderResolution = PromptProviderResolver.resolve(
        requirements = text,
        preference = PromptProviderPreference.AUTO,
        system = system,
        localModel = local,
    )

    private fun system(availability: PromptProviderAvailability): PromptProviderSnapshot = PromptProviderSnapshot(
        providerId = PromptProviderId.SYSTEM,
        availability = availability,
    )

    private fun systemReady(vararg capabilities: PromptCapability): PromptProviderSnapshot = PromptProviderSnapshot(
        providerId = PromptProviderId.SYSTEM,
        availability = PromptProviderAvailability.READY,
        capabilities = supported(*capabilities),
    )

    private fun localAbsent(): LocalModelSnapshot = LocalModelSnapshot(LocalModelLifecycleState.NOT_INSTALLED)

    private fun localReady(
        vararg capabilities: PromptCapability,
        removalState: LocalRemovalState = LocalRemovalState.NOT_REQUESTED,
    ): LocalModelSnapshot = LocalModelSnapshot(
        lifecycleState = LocalModelLifecycleState.READY_VERIFIED,
        metadata = LocalModelMetadata(
            modelId = "fixture-model",
            modelVersion = "1",
            runtimeId = "fixture-runtime",
            runtimeVersion = "1",
            assetSizeBytes = 1L,
            integritySha256 = "fixture-sha256",
            declaredCapabilities = supported(*capabilities),
        ),
        removalState = removalState,
    )

    private fun supported(vararg capabilities: PromptCapability): PromptCapabilitySnapshot =
        PromptCapabilitySnapshot.of(
            *capabilities.map { it to PromptCapabilitySupport.SUPPORTED }.toTypedArray(),
        )
}
