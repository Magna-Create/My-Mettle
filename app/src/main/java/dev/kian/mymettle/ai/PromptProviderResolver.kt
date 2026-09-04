package dev.kian.mymettle.ai

object PromptProviderResolver {
    fun resolve(
        requirements: PromptTaskRequirements,
        preference: PromptProviderPreference,
        system: PromptProviderSnapshot,
        localModel: LocalModelSnapshot,
    ): PromptProviderResolution = when (preference) {
        PromptProviderPreference.AUTO -> resolveAuto(requirements, system, localModel)
        PromptProviderPreference.SYSTEM -> resolveSystemOnly(requirements, system)
        PromptProviderPreference.LOCAL -> resolveLocalOnly(requirements, localModel)
    }

    private fun resolveAuto(
        requirements: PromptTaskRequirements,
        system: PromptProviderSnapshot,
        localModel: LocalModelSnapshot,
    ): PromptProviderResolution {
        val local = localModel.asProviderSnapshot()
        if (system.canSatisfy(requirements)) {
            val retirement = retirementState(localModel)
            return PromptProviderResolution(
                kind = PromptResolutionKind.SYSTEM_SELECTED,
                preference = PromptProviderPreference.AUTO,
                selectedProvider = PromptProviderId.SYSTEM,
                systemTransition = if (retirement == LocalRetirementState.NOT_REQUIRED) {
                    SystemTransitionState.NONE
                } else {
                    SystemTransitionState.SYSTEM_READY_LOCAL_PRESENT
                },
                localRetirement = retirement,
            )
        }

        if (system.availability == PromptProviderAvailability.SETUP_REQUIRED) {
            if (local.canSatisfy(requirements)) {
                return PromptProviderResolution(
                    kind = PromptResolutionKind.LOCAL_SELECTED,
                    preference = PromptProviderPreference.AUTO,
                    selectedProvider = PromptProviderId.LOCAL,
                    systemTransition = SystemTransitionState.SETUP_REQUIRED,
                )
            }
            return PromptProviderResolution(
                kind = PromptResolutionKind.SYSTEM_SETUP_REQUIRED,
                preference = PromptProviderPreference.AUTO,
                setupProvider = PromptProviderId.SYSTEM,
                reason = PromptResolutionReason.PROVIDER_NOT_READY,
                systemTransition = SystemTransitionState.SETUP_REQUIRED,
            )
        }

        if (system.availability == PromptProviderAvailability.SETUP_IN_PROGRESS) {
            if (local.canSatisfy(requirements)) {
                return PromptProviderResolution(
                    kind = PromptResolutionKind.LOCAL_SELECTED,
                    preference = PromptProviderPreference.AUTO,
                    selectedProvider = PromptProviderId.LOCAL,
                    systemTransition = SystemTransitionState.SETUP_IN_PROGRESS,
                )
            }
            return PromptProviderResolution(
                kind = PromptResolutionKind.SYSTEM_SETUP_IN_PROGRESS,
                preference = PromptProviderPreference.AUTO,
                setupProvider = PromptProviderId.SYSTEM,
                reason = PromptResolutionReason.PROVIDER_NOT_READY,
                systemTransition = SystemTransitionState.SETUP_IN_PROGRESS,
            )
        }

        if (local.canSatisfy(requirements)) {
            return PromptProviderResolution(
                kind = PromptResolutionKind.LOCAL_SELECTED,
                preference = PromptProviderPreference.AUTO,
                selectedProvider = PromptProviderId.LOCAL,
            )
        }

        return localNotReadyResolution(PromptProviderPreference.AUTO, requirements, local, localModel)
    }

    private fun resolveSystemOnly(
        requirements: PromptTaskRequirements,
        system: PromptProviderSnapshot,
    ): PromptProviderResolution {
        if (system.canSatisfy(requirements)) {
            return PromptProviderResolution(
                kind = PromptResolutionKind.SYSTEM_SELECTED,
                preference = PromptProviderPreference.SYSTEM,
                selectedProvider = PromptProviderId.SYSTEM,
                localRetirement = LocalRetirementState.NOT_REQUIRED,
            )
        }
        return when (system.availability) {
            PromptProviderAvailability.SETUP_REQUIRED -> PromptProviderResolution(
                kind = PromptResolutionKind.SYSTEM_SETUP_REQUIRED,
                preference = PromptProviderPreference.SYSTEM,
                setupProvider = PromptProviderId.SYSTEM,
                reason = PromptResolutionReason.PROVIDER_NOT_READY,
                systemTransition = SystemTransitionState.SETUP_REQUIRED,
            )
            PromptProviderAvailability.SETUP_IN_PROGRESS -> PromptProviderResolution(
                kind = PromptResolutionKind.SYSTEM_SETUP_IN_PROGRESS,
                preference = PromptProviderPreference.SYSTEM,
                setupProvider = PromptProviderId.SYSTEM,
                reason = PromptResolutionReason.PROVIDER_NOT_READY,
                systemTransition = SystemTransitionState.SETUP_IN_PROGRESS,
            )
            else -> PromptProviderResolution(
                kind = PromptResolutionKind.PROVIDER_UNAVAILABLE,
                preference = PromptProviderPreference.SYSTEM,
                reason = unavailableReason(system, requirements),
            )
        }
    }

    private fun resolveLocalOnly(
        requirements: PromptTaskRequirements,
        localModel: LocalModelSnapshot,
    ): PromptProviderResolution {
        val local = localModel.asProviderSnapshot()
        if (local.canSatisfy(requirements)) {
            return PromptProviderResolution(
                kind = PromptResolutionKind.LOCAL_SELECTED,
                preference = PromptProviderPreference.LOCAL,
                selectedProvider = PromptProviderId.LOCAL,
            )
        }
        return localNotReadyResolution(PromptProviderPreference.LOCAL, requirements, local, localModel)
    }

    private fun localNotReadyResolution(
        preference: PromptProviderPreference,
        requirements: PromptTaskRequirements,
        local: PromptProviderSnapshot,
        localModel: LocalModelSnapshot,
    ): PromptProviderResolution = when (localModel.lifecycleState) {
        LocalModelLifecycleState.NOT_INSTALLED,
        LocalModelLifecycleState.INSTALL_REQUIRED,
        -> PromptProviderResolution(
            kind = PromptResolutionKind.LOCAL_INSTALL_REQUIRED,
            preference = preference,
            setupProvider = PromptProviderId.LOCAL,
            reason = PromptResolutionReason.LOCAL_MODEL_NOT_INSTALLED,
        )
        LocalModelLifecycleState.INSTALLING -> PromptProviderResolution(
            kind = PromptResolutionKind.LOCAL_SETUP_IN_PROGRESS,
            preference = preference,
            setupProvider = PromptProviderId.LOCAL,
            reason = PromptResolutionReason.PROVIDER_NOT_READY,
        )
        LocalModelLifecycleState.INCOMPATIBLE,
        LocalModelLifecycleState.CORRUPT,
        -> PromptProviderResolution(
            kind = PromptResolutionKind.PROVIDER_UNAVAILABLE,
            preference = preference,
            reason = PromptResolutionReason.LOCAL_MODEL_INCOMPATIBLE,
        )
        LocalModelLifecycleState.REMOVING,
        LocalModelLifecycleState.FAILED,
        LocalModelLifecycleState.READY_VERIFIED,
        -> PromptProviderResolution(
            kind = PromptResolutionKind.PROVIDER_UNAVAILABLE,
            preference = preference,
            reason = if (local.availability == PromptProviderAvailability.READY && !local.capabilities.satisfies(requirements)) {
                PromptResolutionReason.REQUIRED_CAPABILITY_UNSUPPORTED_OR_UNKNOWN
            } else {
                PromptResolutionReason.PROVIDER_FAILED
            },
        )
    }

    private fun unavailableReason(
        provider: PromptProviderSnapshot,
        requirements: PromptTaskRequirements,
    ): PromptResolutionReason = when {
        provider.availability == PromptProviderAvailability.READY && !provider.capabilities.satisfies(requirements) ->
            PromptResolutionReason.REQUIRED_CAPABILITY_UNSUPPORTED_OR_UNKNOWN
        provider.availability == PromptProviderAvailability.FAILED || provider.failure != null ->
            PromptResolutionReason.PROVIDER_FAILED
        else -> PromptResolutionReason.PROVIDER_NOT_READY
    }

    private fun retirementState(localModel: LocalModelSnapshot): LocalRetirementState = when (localModel.removalState) {
        LocalRemovalState.FAILED -> LocalRetirementState.FAILED
        LocalRemovalState.REMOVING -> LocalRetirementState.REMOVING
        LocalRemovalState.REMOVED -> LocalRetirementState.NOT_REQUIRED
        LocalRemovalState.NOT_REQUESTED -> if (localModel.lifecycleState == LocalModelLifecycleState.READY_VERIFIED) {
            LocalRetirementState.REQUIRED
        } else {
            LocalRetirementState.NOT_REQUIRED
        }
    }
}
