package dev.kian.lab2b.vlm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBundleInfoTest {
    private val hash = "a".repeat(64)

    @Test
    fun validBundleRequiresOneMainAndOneProjector() {
        val bundle = ModelBundleInfo(
            stagingDir = "/tmp/model",
            files = listOf(
                ModelFileInfo("qwen-q4_0.gguf", 100, hash, ModelFileRole.MAIN),
                ModelFileInfo("mmproj-qwen.gguf", 20, hash, ModelFileRole.MMPROJ),
            ),
        )

        assertTrue(bundle.validationErrors().isEmpty())
        assertEquals(120L, bundle.totalBytes)
    }

    @Test
    fun duplicateMainOrMissingProjectorIsRejected() {
        val bundle = ModelBundleInfo(
            stagingDir = "/tmp/model",
            files = listOf(
                ModelFileInfo("one.gguf", 100, hash, ModelFileRole.MAIN),
                ModelFileInfo("two.gguf", 100, hash, ModelFileRole.MAIN),
            ),
        )

        assertTrue(bundle.validationErrors().any { it.contains("one main") })
        assertTrue(bundle.validationErrors().any { it.contains("one mmproj") })
    }

    @Test
    fun emptyOrMalformedFileIdentityIsRejected() {
        val bundle = ModelBundleInfo(
            stagingDir = "/tmp/model",
            files = listOf(
                ModelFileInfo("main.gguf", 0, "bad", ModelFileRole.MAIN),
                ModelFileInfo("mmproj.gguf", 1, hash, ModelFileRole.MMPROJ),
            ),
        )

        assertTrue(bundle.validationErrors().any { it.contains("no data") })
        assertTrue(bundle.validationErrors().any { it.contains("invalid SHA-256") })
    }
}
