package dev.kian.lab2b.vlm

import org.junit.Assert.*
import org.junit.Test

class WeightColumnsTest {
    private fun line(s:String,x:Int,y:Int)=OcrLine(s,OcrBox(x,y,x+40,y+20),emptyList(),null)
    private fun evidence(vararg lines:OcrLine)=OcrEvidence("raw",listOf(OcrBlock("",null,emptyList(),null,lines.toList())),1,"hash",500,1000)
    private val right=ColumnSelection(0.64,0.64,0.08,StackUnit.KG)
    @Test fun unlabelledValuesRemainUnresolvedUntilColumnUnitConfirmed() {
        val e=evidence(line("10",100,10),line("4.5",300,10),line("20",100,80),line("9.0",300,80))
        assertTrue(WeightOcrParser.parse(e,CapturePart.MAIN_STACK).readings.isEmpty())
        assertEquals(4,WeightColumns.numbers(e).size)
        assertTrue(WeightColumns.numbers(e).all { it.unit==null })
        assertEquals(listOf("4.5","9"),WeightColumns.parse(e,CapturePart.MAIN_STACK,right).sortedKg.map { it.toPlainString() })
    }
    @Test fun reversedColumnsAndTinyUnitsAboveAreRecognised() {
        val e=evidence(line("KGS",100,0),line("5",100,22),line("LBS",300,0),line("10",300,22))
        assertEquals(listOf(StackUnit.KG,StackUnit.LB),WeightColumns.numbers(e).map { it.unit })
        assertTrue(WeightColumns.parse(e,CapturePart.MAIN_STACK,right).readings.single().included.not())
    }
    @Test fun explicitLbChoiceConvertsAndPreservesPrintedEvidence() {
        val r=WeightColumns.parse(evidence(line("10",300,20)),CapturePart.MAIN_STACK,right.copy(unit=StackUnit.LB)).readings.single()
        assertEquals("4.53592370",r.kg.toPlainString());assertEquals("10",r.raw)
        assertTrue(r.changes.any { it.contains("Converted lb") })
    }
    @Test fun repairsAreUncheckedAndNeverInferredFromSequence() {
        val p=WeightColumns.parse(evidence(line("7O",300,20),line("90",300,60)),CapturePart.MAIN_STACK,right)
        assertFalse(p.readings.first().included);assertEquals(listOf("90"),p.sortedKg.map { it.toPlainString() })
    }
    @Test fun slopingColumnUsesRelativePositionAtEachRow() {
        val s=ColumnSelection(0.3,0.7,0.07,StackUnit.KG)
        val e=evidence(line("5",130,0),line("10",310,900),line("20",100,900))
        assertEquals(listOf("5","10"),WeightColumns.parse(e,CapturePart.MAIN_STACK,s).sortedKg.map { it.toPlainString() })
    }
    @Test fun proposalsFollowBothSlopingColumnsWithoutAssumingDecimalsAreKg() {
        val e=evidence(*((0..4).flatMap { i -> listOf(line("${i+1}.0",50+i*10,i*100),line("${i+10}",250+i*10,i*100)) }).toTypedArray())
        val p=WeightColumns.proposals(e)
        assertEquals(2,p.size);assertTrue(p.all { it.unitEvidence.contains("unknown") })
        assertTrue(p.all { it.selection.bottomX>it.selection.topX })
    }
    @Test fun rowComparisonPreservesDisagreementMissingAndAdditionalRows() {
        val a=evidence(line("4",300,20),line("6",300,80),line("16",300,140))
        val b=evidence(line("4",300,20),line("8",300,80),line("32",300,200))
        val rows=WeightColumns.compare(a,b,right)
        assertTrue(rows.any { it.contains("6 → 8") && it.contains("review") })
        assertTrue(rows.any { it.contains("MISSING") });assertTrue(rows.any { it.contains("additional") })
    }
    @Test fun legitimateStepChangesDoNotCauseSequenceCompletion() {
        val numbers=listOf("4.5","9.0","13.5","18.0","22.5","27.0","34.0","41.0","47.5","54.5")
        val p=WeightColumns.parse(evidence(*numbers.mapIndexed { i,s -> line(s,300,i*70) }.toTypedArray()),CapturePart.MAIN_STACK,right)
        assertEquals(10,p.readings.size);assertTrue(p.issues.isEmpty())
    }
    @Test fun comparisonRejectsDifferentCropGeometry() {
        val a=evidence(line("4",300,20))
        assertThrows(IllegalArgumentException::class.java) { WeightColumns.compare(a,a.copy(width=600),right) }
    }
    @Test fun labelAboveSuppliesKgToDefaultParser() {
        val p=WeightOcrParser.parse(evidence(line("KGS",300,0),line("5",300,22)),CapturePart.MAIN_STACK)
        assertEquals("5",p.sortedKg.single().toPlainString())
        assertTrue(p.readings.single().changes.any { it.contains("geometry") })
    }
    @Test fun pairedRatioIsOnlyAUnitSuggestion() {
        val e=evidence(line("10",100,10),line("4.5",300,10),line("20",100,80),line("9",300,80),line("30",100,150),line("13.5",300,150))
        val p=WeightColumns.proposals(e)
        assertEquals(StackUnit.LB,p.first().selection.unit)
        assertTrue(p.all { it.unitEvidence.contains("suggest") })
        assertTrue(WeightOcrParser.parse(e,CapturePart.MAIN_STACK).readings.isEmpty())
    }
    @Test fun damagedUnitCandidatesSurviveColumnSelection() {
        val p=WeightColumns.parse(evidence(line("127 ko",300,10),line("134 kog",300,80)),CapturePart.MAIN_STACK,right)
        assertEquals(2,p.readings.size);assertTrue(p.sortedKg.isEmpty())
        assertTrue(p.readings.all { it.changes.any { change -> change.contains("Damaged unit") } })
    }
    @Test fun inlinePluralKgIsAccepted() {
        assertEquals("5",WeightOcrParser.parse(evidence(line("5 KGS",300,10)),CapturePart.MAIN_STACK).sortedKg.single().toPlainString())
    }
}
