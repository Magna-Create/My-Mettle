package dev.kian.lab2b.vlm

import org.junit.Assert.*
import org.junit.Test

class ExperimentOptionsTest {
    @Test fun thinkingIsExplicitAndGemmaOnly() {
        GenerationOptions(true, 2048).validate("gemma4-e2b")
        GenerationOptions(true, 4096).validate("gemma4-e4b")
        assertThrows(IllegalArgumentException::class.java) { GenerationOptions(true).validate("qwen35-2b") }
        assertThrows(IllegalArgumentException::class.java) { GenerationOptions(false, 999999).validate("gemma4-e2b") }
        val turn = InferenceTurn("English", "instruction", "image.png", SystemPromptMode.TRUE_SYSTEM_ROLE, "hash")
        assertEquals("<|think|>\nEnglish", ThinkingPrompt.apply(turn, true).system)
        assertEquals(turn, ThinkingPrompt.apply(turn, false))
        assertEquals("image.png", ThinkingPrompt.apply(turn, true).imagePath)
        assertEquals(SystemPromptMode.TRUE_SYSTEM_ROLE, ThinkingPrompt.apply(turn.copy(system=null, systemMode=SystemPromptMode.NONE), true).systemMode)
    }
    @Test fun cropCoordinatesValidateAndScaleToOriginal() {
        val crop = CropRegion("kg labels", .5, .1, .8, .9)
        assertEquals(listOf(500,200,800,1800), crop.pixels(1000,2000))
        assertEquals(listOf(1000,400,1600,3600), crop.pixels(2000,4000))
        assertThrows(IllegalArgumentException::class.java) { CropRegion("bad", -.1, 0.0, 1.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { CropRegion("bad", .8, .1, .2, .9) }
        assertThrows(IllegalArgumentException::class.java) { CropRegion("bad", Double.NaN, .1, .2, .9) }
    }
    @Test fun finalAnswerDoesNotLeakIncompleteThinking() {
        assertEquals("", GemmaOutput.finalAnswer("<|channel>thought\nchecking"))
        assertEquals("answer", GemmaOutput.finalAnswer("<|channel>thought\nchecking<channel|>answer"))
        assertEquals("answer", GemmaOutput.finalAnswer("answer"))
        assertEquals("", GemmaOutput.finalAnswer("<|channel>"))
    }
    @Test fun unavailableEnergyIsNotZeroAndUnitsAreCorrect() {
        assertNull(EnergyMath.microJouleDelta(-1, 10, 1, 2))
        assertNull(EnergyMath.microJouleDelta(10, 10, 1, 2))
        assertNull(EnergyMath.microJouleDelta(10, 20, 2, 2))
        assertNull(EnergyMath.microJouleDelta(20, 10, 1, 2))
        assertEquals(2.5, EnergyMath.microJouleDelta(1000000, 3500000, 1, 2)!!, 0.00001)
    }
    @Test fun e4bHasCompleteIndependentPinnedAssets() {
        val e4b = ModelRegistry.get("gemma4-e4b")
        assertEquals(4939909375L, e4b.sizeBytes)
        assertEquals(8, e4b.files.size)
        assertTrue(e4b.files.any { it.name == "visual.mnn.weight" })
        assertTrue(e4b.files.none { it.name.startsWith("audio") })
        assertNotEquals(ModelRegistry.get("gemma4-e2b").fingerprint, e4b.fingerprint)
    }
    @Test fun orderedOcrKeepsValuesAndGeometryWithoutGuessingUnits() {
        val low = OcrLine("79k6", OcrBox(20,80,60,90), emptyList(), null)
        val high = OcrLine("4.5kg", OcrBox(20,10,60,20), emptyList(), null)
        val evidence = OcrEvidence("79k6\n4.5kg", listOf(OcrBlock("", null, emptyList(), null, listOf(low, high))), 5, "hash", 100, 100)
        val ordered = OcrFormatter.format(evidence, true).substringAfter("ordering:")
        assertTrue(ordered.indexOf("4.5kg") < ordered.indexOf("79k6"))
        assertTrue(ordered.contains("[20,80,60,90]"))
        assertFalse(ordered.contains("79kg"))
        assertEquals("79k6\n4.5kg", evidence.fullText)
    }
}
