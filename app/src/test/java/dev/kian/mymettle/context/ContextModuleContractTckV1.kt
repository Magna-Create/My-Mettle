package dev.kian.mymettle.context

import dev.kian.mymettle.domain.context.CONTEXT_MODULE_PROTOCOL_VERSION
import dev.kian.mymettle.domain.context.ContextModuleProviderV7E

/**
 * Minimal reusable v1 contract kit. A future build-integrated provider calls [verify] from its unit
 * test, then adds learner-specific truth fixtures for missingness, scope and causal chronology.
 */
object ContextModuleContractTckV1 {
    data class Result(val checks: List<String>)

    fun verify(provider: ContextModuleProviderV7E): Result {
        val first = provider.create()
        val second = provider.create()
        check(first !== second) { "A provider must create independently owned module instances." }
        check(first.descriptor == second.descriptor) { "Provider descriptors must be deterministic." }
        check(first.descriptor.protocolVersion == CONTEXT_MODULE_PROTOCOL_VERSION)
        check(first.descriptor.deterministicReplay)
        check(first.descriptor.requiredReadCapabilities.isNotEmpty())
        check(first.descriptor.consumedFeatures.isNotEmpty())
        check(first.descriptor.allowedTargets.isNotEmpty())
        check(first.stateCodec.moduleId == first.descriptor.moduleId)
        check(first.stateCodec.schemaVersion == first.descriptor.stateSchemaVersion)
        val initial = first.initialState()
        check(initial.ownerModuleId == first.descriptor.moduleId)
        check(first.stateCodec.decode(first.stateCodec.encode(initial)) == initial) { "Initial state codec round-trip failed." }
        check(second.stateCodec.decode(second.stateCodec.encode(second.initialState())) == initial) {
            "Fresh provider instances must share deterministic initial state semantics."
        }
        return Result(
            listOf(
                "independent_instances",
                "deterministic_descriptor",
                "protocol_version",
                "declared_capabilities",
                "declared_features",
                "declared_targets",
                "state_owner",
                "state_codec_roundtrip",
                "deterministic_initial_state",
            ),
        )
    }
}
