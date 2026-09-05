package dev.kian.lab2b.vlm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendEvidenceTest {
    @Test
    fun requestingNpuDoesNotProveNpu() {
        val evidence = BackendEvidence(
            requestedComputeUnit = "npu",
            resolvedRuntimeId = "llama_cpp",
        )

        assertFalse(evidence.requestedBackendIsProven)
        assertTrue(evidence.diagnosticSummary().contains("REQUESTED != PROVEN"))
    }

    @Test
    fun logConfirmedNpuCanProveExplicitNpuRequest() {
        val evidence = BackendEvidence(
            requestedComputeUnit = "npu",
            resolvedRuntimeId = "llama_cpp",
            proofState = BackendProofState.LOG_CONFIRMED_NPU,
            evidence = "physical runtime log retained separately",
        )

        assertTrue(evidence.requestedBackendIsProven)
    }

    @Test
    fun gpuProofDoesNotSatisfyNpuRequest() {
        val evidence = BackendEvidence(
            requestedComputeUnit = "npu",
            resolvedRuntimeId = "llama_cpp",
            proofState = BackendProofState.LOG_CONFIRMED_GPU,
        )

        assertFalse(evidence.requestedBackendIsProven)
    }
}
