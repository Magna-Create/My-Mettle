package dev.kian.lab2b.vlm

enum class BackendProofState {
    UNPROVEN,
    LOG_CONFIRMED_NPU,
    LOG_CONFIRMED_GPU,
    LOG_CONFIRMED_CPU,
    CONTRADICTED,
}

data class BackendEvidence(
    val requestedComputeUnit: String = "npu",
    val resolvedRuntimeId: String? = null,
    val proofState: BackendProofState = BackendProofState.UNPROVEN,
    val evidence: String? = null,
) {
    val requestedBackendIsProven: Boolean
        get() =
            when (requestedComputeUnit.lowercase()) {
                "npu" -> proofState == BackendProofState.LOG_CONFIRMED_NPU
                "gpu" -> proofState == BackendProofState.LOG_CONFIRMED_GPU
                "cpu" -> proofState == BackendProofState.LOG_CONFIRMED_CPU
                else -> false
            }

    fun diagnosticSummary(): String = buildString {
        append("requested=").append(requestedComputeUnit)
        append("; runtime=").append(resolvedRuntimeId ?: "unresolved")
        append("; proof=").append(proofState)
        if (!evidence.isNullOrBlank()) append("; evidence=").append(evidence)
        if (!requestedBackendIsProven) append("; REQUESTED != PROVEN")
    }
}
