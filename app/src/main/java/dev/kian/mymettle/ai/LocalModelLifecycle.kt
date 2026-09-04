package dev.kian.mymettle.ai

enum class LocalModelLifecycleState {
    NOT_INSTALLED,
    INSTALL_REQUIRED,
    INSTALLING,
    READY_VERIFIED,
    INCOMPATIBLE,
    CORRUPT,
    REMOVING,
    FAILED,
}

enum class LocalRemovalState {
    NOT_REQUESTED,
    REMOVING,
    FAILED,
    REMOVED,
}

data class LocalModelMetadata(
    val modelId: String,
    val modelVersion: String,
    val runtimeId: String,
    val runtimeVersion: String,
    val assetSizeBytes: Long,
    val integritySha256: String,
    val declaredCapabilities: PromptCapabilitySnapshot,
    val compatibilityIdentity: String? = null,
) {
    init {
        require(modelId.isNotBlank())
        require(modelVersion.isNotBlank())
        require(runtimeId.isNotBlank())
        require(runtimeVersion.isNotBlank())
        require(assetSizeBytes >= 0L)
        require(integritySha256.isNotBlank())
    }
}

data class LocalModelSnapshot(
    val lifecycleState: LocalModelLifecycleState,
    val metadata: LocalModelMetadata? = null,
    val removalState: LocalRemovalState = LocalRemovalState.NOT_REQUESTED,
    val lastFailure: PromptProviderFailure? = null,
) {
    fun asProviderSnapshot(nowEpochMillis: Long? = null): PromptProviderSnapshot = PromptProviderSnapshot(
        providerId = PromptProviderId.LOCAL,
        availability = when (lifecycleState) {
            LocalModelLifecycleState.NOT_INSTALLED -> PromptProviderAvailability.NOT_INSTALLED
            LocalModelLifecycleState.INSTALL_REQUIRED -> PromptProviderAvailability.SETUP_REQUIRED
            LocalModelLifecycleState.INSTALLING -> PromptProviderAvailability.SETUP_IN_PROGRESS
            LocalModelLifecycleState.READY_VERIFIED -> PromptProviderAvailability.READY
            LocalModelLifecycleState.INCOMPATIBLE -> PromptProviderAvailability.INCOMPATIBLE
            LocalModelLifecycleState.CORRUPT -> PromptProviderAvailability.CORRUPT
            LocalModelLifecycleState.REMOVING -> PromptProviderAvailability.REMOVING
            LocalModelLifecycleState.FAILED -> PromptProviderAvailability.FAILED
        },
        capabilities = metadata?.declaredCapabilities ?: PromptCapabilitySnapshot.allUnknown(),
        diagnostics = PromptProviderDiagnostics(
            providerRuntime = metadata?.runtimeId,
            providerRuntimeVersion = metadata?.runtimeVersion,
            modelIdentity = metadata?.let { "${it.modelId}@${it.modelVersion}" },
            lastProbeEpochMillis = nowEpochMillis,
        ),
        failure = lastFailure,
    )
}

sealed interface LocalModelOperationResult {
    data class Completed(val snapshot: LocalModelSnapshot) : LocalModelOperationResult
    data class Rejected(val failure: PromptProviderFailure) : LocalModelOperationResult
}

interface LocalModelLifecycle {
    suspend fun probe(): LocalModelSnapshot
    suspend fun install(): LocalModelOperationResult
    suspend fun verify(): LocalModelOperationResult
    suspend fun remove(): LocalModelOperationResult
}

/** LAB-1 intentionally has no local runtime or downloader. */
class NoOpLocalModelLifecycle : LocalModelLifecycle {
    private val absent = LocalModelSnapshot(LocalModelLifecycleState.NOT_INSTALLED)
    private val unsupported = LocalModelOperationResult.Rejected(
        PromptProviderFailure(
            kind = PromptProviderFailureKind.UNSUPPORTED,
            errorClass = "LocalRuntimeNotIntegrated",
        ),
    )

    override suspend fun probe(): LocalModelSnapshot = absent

    override suspend fun install(): LocalModelOperationResult = unsupported

    override suspend fun verify(): LocalModelOperationResult = unsupported

    override suspend fun remove(): LocalModelOperationResult = unsupported
}
