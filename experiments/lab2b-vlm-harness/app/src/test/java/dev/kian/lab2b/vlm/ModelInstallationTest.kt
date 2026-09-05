package dev.kian.lab2b.vlm

import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ModelInstallationTest {
    private fun fixture(block: (ModelInstallation, HarnessModelSpec) -> Unit) {
        val root = Files.createTempDirectory("lab2b-install").toFile()
        try {
            val model = ModelRegistry.models.first().copy(files = listOf(ModelAsset("llm.mnn", 4, Hashing.sha256("test".toByteArray()))))
            block(ModelInstallation(root), model)
        } finally { root.deleteRecursively() }
    }
    @Test fun partialFilesNeverBecomeInstalledAndVerifiedInstallSurvivesNewOwner() = fixture { store, model ->
        val stage = store.staging(model).apply { mkdirs() }; File(stage, "llm.mnn").writeText("te")
        assertFalse(store.installed(model)); assertThrows(IllegalArgumentException::class.java) { store.activate(model) }
        File(stage, "llm.mnn").writeText("test"); store.activate(model)
        assertTrue(store.installed(model)); assertFalse(stage.exists())
        assertTrue(ModelInstallation(store.directory(model).parentFile!!).installed(model))
    }
    @Test fun sameSizeCorruptionFailsShaAndManifestChangesInvalidateInstallation() = fixture { store, model ->
        store.staging(model).mkdirs(); File(store.staging(model), "llm.mnn").writeText("test"); store.activate(model)
        assertFalse(store.installed(model.copy(revision = "b".repeat(40))))
        File(store.directory(model), "llm.mnn").writeText("evil")
        assertThrows(IllegalArgumentException::class.java) { store.verifyAll(model) }
    }
    @Test fun removeCleansOnlySelectedModelAndItsPartialFolder() = fixture { store, model ->
        val other = model.copy(id = "other")
        for (m in listOf(model,other)) { store.staging(m).mkdirs(); File(store.staging(m), "llm.mnn").writeText("test"); store.activate(m) }
        store.staging(model).mkdirs(); File(store.staging(model), "partial").writeText("unfinished")
        store.remove(model)
        assertFalse(store.directory(model).exists()); assertFalse(store.staging(model).exists()); assertTrue(store.installed(other))
    }
}
