package dev.kian.lab2b.vlm
import org.junit.Assert.*
import org.junit.Test
class StartingResistanceParserTest {
    private fun evidence(line:String)=OcrEvidence(line,listOf(OcrBlock(line,null,emptyList(),null,listOf(OcrLine(line,OcrBox(0,0,300,30),emptyList(),null)))),1,"sha",400,100)
    @Test fun suppliedPulloverOcrReturnsPrintedKg() {
        val c=PlacardParser.extract(evidence("Start 18 lb./8.2Kg."),null).suggested(PlacardField.STARTING_RESISTANCE)!!
        assertEquals(8.2,c.number!!,0.0);assertEquals("kg",c.unit);assertEquals("Start 18 lb./8.2Kg.",c.raw)
    }
    @Test fun suppliedHackSquatOcrReturnsPrintedKg() {
        val c=PlacardParser.extract(evidence("Start 90 lbs./40.8kg."),null).suggested(PlacardField.STARTING_RESISTANCE)!!
        assertEquals(40.8,c.number!!,0.0)
    }
    @Test fun eitherUnitOrderIsSupportedWithoutSynthesisingKg() {
        assertEquals(8.2,StartingResistanceParser.parse("8.2 kg / 18 lb")!!.number,0.0)
        assertEquals("lb",StartingResistanceParser.parse("18 lb.")!!.unit)
    }
    @Test fun contradictoryDualUnitsNeedReview() {
        val r=PlacardParser.extract(evidence("Start 10 lb / 40 kg"),null)
        assertNull(r.suggested(PlacardField.STARTING_RESISTANCE));assertTrue(r.choices(PlacardField.STARTING_RESISTANCE).single().method.contains("CONFLICT"))
    }
    @Test fun malformedPayloadsAndExerciseInstructionsAreRejected() {
        listOf("5-10 kg","10 kg / 20 kg","18 lb / 8.2 kg / 9 kg","3 sets of 10 kg").forEach { assertNull(StartingResistanceParser.parse(it)) }
        assertTrue(PlacardParser.extract(evidence("Start 3 sets of 10 kg"),null).choices(PlacardField.STARTING_RESISTANCE).isEmpty())
    }
    @Test fun numericRepairInEitherUnitNeedsReview() {
        val c=StartingResistanceParser.parse("9O lb / 40.8 kg")!!
        assertTrue(c.method.contains("CANDIDATE"))
    }
    @Test fun suppliedDamagedKgUnitsAreRetainedButNotAccepted() {
        for(text in listOf("127 ko","134 kog")) {
            val r=WeightOcrParser.parse(evidence(text),CapturePart.MAIN_STACK)
            assertEquals(WeightOrigin.UNIT_CANDIDATE,r.readings.single().origin)
            assertFalse(r.readings.single().included);assertTrue(r.sortedKg.isEmpty());assertTrue(r.ignored.isEmpty())
        }
    }
}
