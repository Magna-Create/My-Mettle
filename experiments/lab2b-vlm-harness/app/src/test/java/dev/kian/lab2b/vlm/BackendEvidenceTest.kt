package dev.kian.lab2b.vlm

import org.junit.Assert.*
import org.junit.Test

class BackendEvidenceTest {
    @Test fun gpuRequestNeverInventsEffectiveGpuPlacement() {
        val result = BackendEvidence.loaded(ComputeBackend.GPU, "opencl", "cpu")
        assertTrue(result.effectiveText.startsWith("UNVERIFIED"))
        assertTrue(result.effectiveVision.startsWith("CPU"))
        assertTrue(result.gpuCorrectness.contains("UNTESTED"))
    }
    @Test fun registryIsExactAndPortableWithCpuDefault() {
        assertEquals(listOf("Gemma 4 E2B IT", "Qwen3.5-2B", "Qwen3-VL-2B-Instruct", "Gemma 4 E4B IT (experimental)"), ModelRegistry.models.map { it.displayName })
        assertEquals(4, ModelRegistry.models.map { it.id }.toSet().size)
        ModelRegistry.models.forEach { model ->
            assertTrue(model.supportsImage)
            assertEquals(setOf(ComputeBackend.CPU, ComputeBackend.GPU), model.supportedBackends)
            assertEquals(ComputeBackend.CPU, model.defaultBackend)
            assertTrue(Regex("[a-f0-9]{40}").matches(model.revision))
            assertEquals(model.files.size, model.files.map { it.name }.toSet().size)
            model.files.forEach {
                assertTrue(it.sizeBytes > 0); assertTrue(Regex("[a-f0-9]{64}").matches(it.sha256))
                assertTrue(model.url(it).contains("/resolve/${model.revision}/"))
                assertFalse(it.name.contains('/'))
            }
            assertTrue(model.files.any { it.name == "visual.mnn.weight" })
        }
    }
}
