package dev.kian.lab2b.vlm

import org.junit.Assert.*
import org.junit.Test

class WeightOcrParserTest {
    private fun evidence(vararg lines: String) = OcrEvidence(lines.joinToString("\n"), listOf(OcrBlock("",null,emptyList(),null,
        lines.mapIndexed { i,s -> OcrLine(s,OcrBox(10,i*20,100,i*20+10),emptyList(),null) })),1,"hash",500,1000)
    @Test fun rejectsPoundsAndSerialsWithoutGlobalCharacterReplacement() {
        val r=WeightOcrParser.parse(evidence("10 Ib","251b","70 1b","1901bs","G7MO428","olo","DDWDDI","4.5kg","32 kg'"),CapturePart.MAIN_STACK)
        assertEquals(listOf("4.5","32"),r.sortedKg.map { it.toPlainString() })
        assertEquals(7,r.ignored.size)
    }
    @Test fun numericCorrectionsAreAuditableCandidates() {
        val r=WeightOcrParser.parse(evidence("12Okg","1lkg","1 lkg","7o kg","7O kg","Okg"),CapturePart.MAIN_STACK)
        assertEquals(listOf("120","11","11","70","70"),r.readings.map { it.kg.toPlainString() })
        assertTrue(r.readings.all { !it.included && it.origin==WeightOrigin.CHARACTER_CORRECTION })
        assertEquals("12Okg",r.readings.first().raw)
    }
    @Test fun damagedUnitsNeverBecomeRecognisedKg() {
        val r=WeightOcrParser.parse(evidence("100 k","134ka","120kg"),CapturePart.MAIN_STACK)
        assertEquals(listOf("120"),r.sortedKg.map { it.toPlainString() })
        assertEquals(2,r.readings.count { it.origin==WeightOrigin.UNIT_CANDIDATE })
    }
    @Test fun ascendingSortDoesNotConcealPhysicalOrderErrors() {
        val r=WeightOcrParser.parse(evidence("4.5kg","18kg","11kg","25kg","25kg"),CapturePart.MAIN_STACK)
        assertEquals(listOf("4.5","11","18","25"),r.sortedKg.map { it.toPlainString() })
        assertEquals(2,r.issues.size)
    }
    @Test fun roundedKgStepsAreNotForcedToConstantIncrement() {
        val complete=WeightOcrParser.parse(evidence("4.5kg","11kg","18kg","25kg","32kg","39kg","45kg","52kg","59kg","66kg","73kg","79kg","86kg","93kg","100kg","107kg","113kg","120kg","127kg","134kg"),CapturePart.MAIN_STACK)
        assertEquals(20,complete.sortedKg.size); assertTrue(complete.issues.isEmpty())
        val gap=WeightOcrParser.parse(evidence("11kg","18kg","25kg","32kg","39kg","52kg"),CapturePart.MAIN_STACK)
        assertTrue(gap.issues.any { it.contains("Possible gap") }); assertFalse(gap.sortedKg.any { it.toInt()==45 })
    }
    @Test fun addonCaptureRemainsSeparateAndDoesNotInferQuantity() {
        val r=WeightOcrParser.parse(evidence("2.3kg","2.3kg"),CapturePart.ADD_ON)
        assertEquals(2,r.readings.size); assertEquals(1,r.sortedKg.size); assertTrue(r.issues.isEmpty())
        assertEquals("2.3",r.sortedKg.single().toPlainString())
    }
    @Test fun humanEditPreservesSourceAndRejectsInvalidLoads() {
        val reading=WeightOcrParser.parse(evidence("134ka"),CapturePart.MAIN_STACK).readings.single()
        val edited=WeightOcrParser.edit(reading,"134")
        assertEquals("134ka",edited.raw); assertTrue(edited.included); assertEquals(WeightOrigin.HUMAN_EDITED,edited.origin)
        assertThrows(IllegalArgumentException::class.java) { WeightOcrParser.edit(reading,"-1") }
    }
}
