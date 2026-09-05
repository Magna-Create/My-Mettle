package dev.kian.lab2b.vlm

import org.junit.Assert.*
import org.junit.Test

class HarnessStateTest {
    @Test fun interruptedOrRejectedPrefillCannotReusePendingImageState() {
        assertTrue(HarnessStateMachine.mustDisposeAfterTurn("STOPPED"))
        assertTrue(HarnessStateMachine.mustDisposeAfterTurn("FAILED"))
        assertTrue(HarnessStateMachine.mustDisposeAfterTurn("TOKEN_LIMIT"))
        assertFalse(HarnessStateMachine.mustDisposeAfterTurn("COMPLETED"))
    }
    @Test fun generationRequiresMatchingLoadedModelAndImage() {
        val s = HarnessSnapshot(phase = HarnessPhase.READY, loadedModelId = "gemma4-e2b", selectedImage = testImage())
        assertTrue(HarnessStateMachine.canGenerate(s))
        assertFalse(HarnessStateMachine.canGenerate(s.copy(selectedModelId = "qwen35-2b")))
        assertFalse(HarnessStateMachine.canGenerate(s.copy(selectedImage = null)))
        assertFalse(HarnessStateMachine.canGenerate(s.copy(loadedModelId = null)))
    }
    @Test fun allBusyPhasesRejectSwitchUnloadAndSecondGeneration() {
        for (phase in listOf(HarnessPhase.LOADING, HarnessPhase.PREPARING, HarnessPhase.GENERATING, HarnessPhase.STOPPING, HarnessPhase.UNLOADING)) {
            assertFalse(phase.name, HarnessStateMachine.idle(phase))
            assertFalse(phase.name, HarnessStateMachine.canUnload(phase))
            assertFalse(HarnessStateMachine.canGenerate(HarnessSnapshot(phase = phase, loadedModelId = "gemma4-e2b", selectedImage = testImage())))
        }
    }
    @Test fun disposalMustPrecedeReplacementAndIsIdempotent() {
        val events = mutableListOf<String>()
        fun engine(name: String) = object : LocalInferenceEngine {
            override val evidence = BackendEvidence()
            override fun arm() = Unit
            override fun stop() = Unit
            override fun generate(turn: InferenceTurn, onOutput: (String) -> Unit): LongArray = error("Not an inference test")
            override fun close() { events += "close:$name" }
        }
        val slot = EngineSlot(); slot.install("a", engine("a"))
        assertThrows(IllegalStateException::class.java) { slot.install("b", engine("b")) }
        assertEquals("a", slot.modelId)
        slot.dispose(); slot.dispose()
        assertNull(slot.engine); assertNull(slot.modelId)
        slot.install("b", engine("b")); slot.dispose()
        assertEquals(listOf("close:a", "close:b"), events)
    }
}
