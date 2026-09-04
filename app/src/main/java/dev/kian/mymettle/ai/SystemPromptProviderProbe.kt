package dev.kian.mymettle.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import java.util.concurrent.CancellationException

/** Read-only Prompt API capability probe. It never calls GenerativeModel.download(). */
class MlKitSystemPromptProviderProbe(
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : PromptProviderProbe {
    override suspend fun probe(): PromptProviderSnapshot {
        val model = Generation.getClient()
        return try {
            val status = model.checkStatus()
            val availability = when (status) {
                FeatureStatus.AVAILABLE -> PromptProviderAvailability.READY
                FeatureStatus.DOWNLOADABLE -> PromptProviderAvailability.SETUP_REQUIRED
                FeatureStatus.DOWNLOADING -> PromptProviderAvailability.SETUP_IN_PROGRESS
                FeatureStatus.UNAVAILABLE -> PromptProviderAvailability.UNAVAILABLE
                else -> PromptProviderAvailability.UNKNOWN
            }

            if (availability != PromptProviderAvailability.READY) {
                return PromptProviderSnapshot(
                    providerId = PromptProviderId.SYSTEM,
                    availability = availability,
                    capabilities = PromptCapabilitySnapshot.of(
                        PromptCapability.TEXT to if (availability == PromptProviderAvailability.UNAVAILABLE) {
                            PromptCapabilitySupport.UNSUPPORTED
                        } else {
                            PromptCapabilitySupport.UNKNOWN
                        },
                    ),
                    diagnostics = PromptProviderDiagnostics(
                        providerRuntime = RUNTIME_ID,
                        providerRuntimeVersion = LIBRARY_VERSION,
                        lastProbeEpochMillis = nowEpochMillis(),
                    ),
                )
            }

            val structured = probeBooleanCapability(
                PromptCapability.STRUCTURED_OUTPUT,
                model::isStructuredOutputFeatureAvailable,
            )
            val systemInstructions = probeBooleanCapability(
                PromptCapability.SYSTEM_INSTRUCTIONS,
                model::isSystemPromptAvailable,
            )
            val errors = buildMap {
                structured.errorClass?.let { put(PromptCapability.STRUCTURED_OUTPUT, it) }
                systemInstructions.errorClass?.let { put(PromptCapability.SYSTEM_INSTRUCTIONS, it) }
            }
            val baseModel = try {
                model.getBaseModelName()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }

            PromptProviderSnapshot(
                providerId = PromptProviderId.SYSTEM,
                availability = PromptProviderAvailability.READY,
                capabilities = PromptCapabilitySnapshot.of(
                    PromptCapability.TEXT to PromptCapabilitySupport.SUPPORTED,
                    PromptCapability.STRUCTURED_OUTPUT to structured.support,
                    PromptCapability.SYSTEM_INSTRUCTIONS to systemInstructions.support,
                    // The pinned API accepts image/multi-image request parts, but exposes no
                    // per-device feature probe equivalent to the two checks above. Keep them
                    // UNKNOWN until a later task can verify them honestly.
                    PromptCapability.IMAGE_INPUT to PromptCapabilitySupport.UNKNOWN,
                    PromptCapability.MULTI_IMAGE to PromptCapabilitySupport.UNKNOWN,
                ),
                diagnostics = PromptProviderDiagnostics(
                    providerRuntime = RUNTIME_ID,
                    providerRuntimeVersion = LIBRARY_VERSION,
                    modelIdentity = baseModel,
                    lastProbeEpochMillis = nowEpochMillis(),
                    capabilityProbeErrors = errors,
                ),
                failure = errors.values.firstOrNull()?.let { errorClass ->
                    PromptProviderFailure(
                        kind = PromptProviderFailureKind.TEMPORARY_FAILURE,
                        errorClass = errorClass,
                    )
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            PromptProviderSnapshot(
                providerId = PromptProviderId.SYSTEM,
                availability = PromptProviderAvailability.UNKNOWN,
                diagnostics = PromptProviderDiagnostics(
                    providerRuntime = RUNTIME_ID,
                    providerRuntimeVersion = LIBRARY_VERSION,
                    lastProbeEpochMillis = nowEpochMillis(),
                ),
                failure = PromptProviderFailure(
                    kind = PromptProviderFailureKind.TEMPORARY_FAILURE,
                    errorClass = error::class.java.simpleName,
                ),
            )
        } finally {
            model.close()
        }
    }

    private suspend fun probeBooleanCapability(
        capability: PromptCapability,
        block: suspend () -> Boolean,
    ): BooleanCapabilityProbe = try {
        BooleanCapabilityProbe(
            support = if (block()) PromptCapabilitySupport.SUPPORTED else PromptCapabilitySupport.UNSUPPORTED,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        BooleanCapabilityProbe(
            support = PromptCapabilitySupport.UNKNOWN,
            errorClass = "${capability.name}:${error::class.java.simpleName}",
        )
    }

    private data class BooleanCapabilityProbe(
        val support: PromptCapabilitySupport,
        val errorClass: String? = null,
    )

    companion object {
        const val RUNTIME_ID = "ML Kit GenAI Prompt API"
        const val LIBRARY_VERSION = "1.0.0-beta4"
    }
}
