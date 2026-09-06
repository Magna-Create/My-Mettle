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
    private val reference=listOf("4.5","11","18","25","32","39","45","52","59","66","73","79","86","93","100","107","113","120","127","134")
    private fun fixture(labels: List<Pair<String,Int>>): OcrEvidence = OcrEvidence("recognizer order",labels.map { (text,y) ->
        OcrBlock(text,null,emptyList(),"und-Latn",listOf(OcrLine(text,OcrBox(100,y,180,y+10),emptyList(),"und-Latn"))) },1,"fixture-sha",600,1200)
    @Test fun suppliedCroppedStackTwoRestoresGeometryAndKeepsRepairsUnconfirmed() {
        val labels=listOf("52 kg" to 584,"59 kg" to 641,"66kg" to 694,"73 kg" to 743,"45kg" to 527,
            "79 kg" to 796,"86kg" to 837,"93 kg" to 881,"39 kg" to 465,"100kg" to 920,
            "107kg" to 963,"127kg" to 1067,"134ka" to 1101,"25kg" to 333,"32 kg" to 401,
            "113kg" to 1001,"120kg" to 1033,"18kg" to 255,"1 lkg" to 177,"4.5kg" to 96,
            "DDWDDI" to 95,"175ibs" to 796,"1901bs" to 839,"295ibs" to 1108,"10ibs" to 90)
        val parsed=WeightOcrParser.parse(fixture(labels),CapturePart.MAIN_STACK)
        assertTrue(parsed.issues.isEmpty())
        assertEquals(reference,parsed.readings.map { it.kg.toPlainString() })
        assertEquals(listOf("1 lkg","134ka"),parsed.readings.filter { !it.included }.map { it.raw })
        assertEquals(reference,parsed.copy(readings=parsed.readings.map { it.copy(included=true) }).sortedKg.map { it.toPlainString() })
        assertEquals(18,parsed.sortedKg.size)
    }
    @Test fun suppliedCroppedStackOneDoesNotSilentlyAcceptMissingG() {
        val labels=reference.drop(1).mapIndexed { i,v -> (if(v=="100") "100 k" else "$v kg") to (114+i*35) } + listOf("4.5 kg" to 47,"10 Ib" to 51,"G7MO428" to 840)
        val parsed=WeightOcrParser.parse(fixture(labels),CapturePart.MAIN_STACK)
        assertEquals(20,parsed.readings.size); assertEquals(19,parsed.sortedKg.size)
        assertEquals("100 k",parsed.readings.single { !it.included }.raw)
        assertEquals(reference,parsed.copy(readings=parsed.readings.map { it.copy(included=true) }).sortedKg.map { it.toPlainString() })
    }
    @Test fun fullFrameAddonLabelsRemainVisibleForHumanExclusion() {
        val labels=listOf("2.3kg" to 80,"2.3kg" to 90) + reference.mapIndexed { i,v -> "$v kg" to (377+i*35) }
        val parsed=WeightOcrParser.parse(fixture(labels),CapturePart.MAIN_STACK)
        assertEquals(22,parsed.readings.size)
        assertTrue(parsed.issues.any { it.contains("duplicate") })
        val reviewed=parsed.copy(readings=parsed.readings.map { it.copy(included=it.kg.toPlainString()!="2.3") })
        assertEquals(reference,reviewed.sortedKg.map { it.toPlainString() })
    }

    @Test fun highWeightsStayVisibleUncheckedIncludingAddonAndJoinedLabels() {
        for (part in CapturePart.entries) {
            val r=WeightOcrParser.parse(evidence("999.9kg","1000kg","1155kg","59130kg"),part)
            assertEquals(4,r.readings.size)
            assertEquals(listOf("999.9"),r.sortedKg.map { it.toPlainString() })
            assertEquals(3,r.issues.count { it.startsWith("CHECK WEIGHT") })
            assertEquals("1155kg",r.readings[2].raw)
            assertEquals("115.5",WeightOcrParser.edit(r.readings[2],"115.5").kg.toPlainString())
            assertFalse(WeightOcrParser.edit(r.readings[0],"1000").included)
        }
    }
    @Test fun columnThresholdUsesConvertedKgAndPreservesExplicitOverride() {
        val selection=ColumnSelection(0.11,0.11,0.1,StackUnit.KG)
        val r=WeightColumns.parse(evidence("999.9","1000","1155","59130"),CapturePart.MAIN_STACK,selection)
        assertEquals(4,r.readings.size)
        assertEquals(listOf("999.9"),r.sortedKg.map { it.toPlainString() })
        assertTrue(r.readings[2].copy(included=true).included)
        val lb=WeightColumns.parse(evidence("2000","2205"),CapturePart.ADD_ON,selection.copy(unit=StackUnit.LB))
        assertTrue(lb.readings[0].included)
        assertFalse(lb.readings[1].included)
        assertTrue(lb.issues.any { it.startsWith("CHECK WEIGHT") })
    }

}
