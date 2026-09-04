package dev.kian.mymettle.ai

enum class PromptProviderId {
    SYSTEM,
    LOCAL,
}

enum class PromptCapability {
    TEXT,
    IMAGE_INPUT,
    STRUCTURED_OUTPUT,
    SYSTEM_INSTRUCTIONS,
    MULTI_IMAGE,
}

enum class PromptCapabilitySupport {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

data class PromptCapabilitySnapshot(
    private val values: Map<PromptCapability, PromptCapabilitySupport> = emptyMap(),
) {
    fun supportFor(capability: PromptCapability): PromptCapabilitySupport =
        values[capability] ?: PromptCapabilitySupport.UNKNOWN

    fun satisfies(requirements: PromptTaskRequirements): Boolean =
        requirements.required.all { supportFor(it) == PromptCapabilitySupport.SUPPORTED }

    fun asMap(): Map<PromptCapability, PromptCapabilitySupport> =
        PromptCapability.entries.associateWith(::supportFor)

    companion object {
        fun of(vararg values: Pair<PromptCapability, PromptCapabilitySupport>): PromptCapabilitySnapshot =
            PromptCapabilitySnapshot(values.toMap())

        fun allUnknown(): PromptCapabilitySnapshot = PromptCapabilitySnapshot()
    }
}

data class PromptTaskRequirements(
    val required: Set<PromptCapability>,
) {
    init {
        require(required.isNotEmpty()) { "Prompt tasks must declare at least one required capability." }
    }

    companion object {
        val DIAGNOSTIC_TEXT_STRUCTURED = PromptTaskRequirements(
            setOf(
                PromptCapability.TEXT,
                PromptCapability.STRUCTURED_OUTPUT,
                PromptCapability.SYSTEM_INSTRUCTIONS,
            ),
        )

        fun of(vararg capabilities: PromptCapability): PromptTaskRequirements =
            PromptTaskRequirements(capabilities.toSet())
    }
}

enum class PromptProviderAvailability {
    NOT_CHECKED,
    READY,
    SETUP_REQUIRED,
    SETUP_IN_PROGRESS,
    UNAVAILABLE,
    NOT_INSTALLED,
    INCOMPATIBLE,
    CORRUPT,
    REMOVING,
    FAILED,
    UNKNOWN,
}

enum class PromptProviderFailureKind {
    UNSUPPORTED,
    CANCELLED,
    TEMPORARY_FAILURE,
    PERMANENT_FAILURE,
    UNKNOWN,
}

data class PromptProviderFailure(
    val kind: PromptProviderFailureKind,
    val errorClass: String? = null,
    val errorCode: String? = null,
)

data class PromptProviderDiagnostics(
    val providerRuntime: String? = null,
    val providerRuntimeVersion: String? = null,
    val modelIdentity: String? = null,
    val lastProbeEpochMillis: Long? = null,
    val capabilityProbeErrors: Map<PromptCapability, String> = emptyMap(),
)

data class PromptProviderSnapshot(
    val providerId: PromptProviderId,
    val availability: PromptProviderAvailability,
    val capabilities: PromptCapabilitySnapshot = PromptCapabilitySnapshot.allUnknown(),
    val diagnostics: PromptProviderDiagnostics = PromptProviderDiagnostics(),
    val failure: PromptProviderFailure? = null,
) {
    fun canSatisfy(requirements: PromptTaskRequirements): Boolean =
        availability == PromptProviderAvailability.READY && capabilities.satisfies(requirements)
}

enum class PromptProviderPreference {
    AUTO,
    SYSTEM,
    LOCAL,
}

enum class PromptResolutionKind {
    SYSTEM_SELECTED,
    LOCAL_SELECTED,
    SYSTEM_SETUP_REQUIRED,
    SYSTEM_SETUP_IN_PROGRESS,
    LOCAL_INSTALL_REQUIRED,
    LOCAL_SETUP_IN_PROGRESS,
    PROVIDER_UNAVAILABLE,
}

enum class PromptResolutionReason {
    NONE,
    PROVIDER_NOT_READY,
    REQUIRED_CAPABILITY_UNSUPPORTED_OR_UNKNOWN,
    LOCAL_MODEL_NOT_INSTALLED,
    LOCAL_MODEL_INCOMPATIBLE,
    PROVIDER_FAILED,
}

enum class SystemTransitionState {
    NONE,
    SETUP_REQUIRED,
    SETUP_IN_PROGRESS,
    SYSTEM_READY_LOCAL_PRESENT,
}

enum class LocalRetirementState {
    NOT_REQUIRED,
    REQUIRED,
    REMOVING,
    FAILED,
}

data class PromptProviderResolution(
    val kind: PromptResolutionKind,
    val preference: PromptProviderPreference,
    val selectedProvider: PromptProviderId? = null,
    val setupProvider: PromptProviderId? = null,
    val reason: PromptResolutionReason = PromptResolutionReason.NONE,
    val systemTransition: SystemTransitionState = SystemTransitionState.NONE,
    val localRetirement: LocalRetirementState = LocalRetirementState.NOT_REQUIRED,
)

fun interface PromptProviderProbe {
    suspend fun probe(): PromptProviderSnapshot
}
