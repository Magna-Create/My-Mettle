package dev.kian.lab2b.vlm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessStateTest {
    @Test
    fun generationIsSerialAndOnlyReadyCanRun() {
        assertTrue(HarnessStateMachine.canGenerate(HarnessPhase.READY))
        assertFalse(HarnessStateMachine.canGenerate(HarnessPhase.GENERATING))
        assertTrue(HarnessStateMachine.concurrentGenerationRejected(HarnessPhase.GENERATING))
        assertTrue(HarnessStateMachine.concurrentGenerationRejected(HarnessPhase.STOPPING))
    }

    @Test
    fun unloadCannotRaceActiveGeneration() {
        assertFalse(HarnessStateMachine.canUnload(HarnessPhase.GENERATING))
        assertFalse(HarnessStateMachine.canUnload(HarnessPhase.STOPPING))
        assertTrue(HarnessStateMachine.canUnload(HarnessPhase.READY))
    }

    @Test
    fun modelMustBeImportedBeforeLoad() {
        assertTrue(HarnessStateMachine.canLoad(HarnessPhase.IMPORTED))
        assertFalse(HarnessStateMachine.canLoad(HarnessPhase.IDLE))
        assertFalse(HarnessStateMachine.canLoad(HarnessPhase.IMPORTING))
    }
}
