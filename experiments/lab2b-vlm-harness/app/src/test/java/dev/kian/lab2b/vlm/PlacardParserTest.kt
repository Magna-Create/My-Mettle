package dev.kian.lab2b.vlm

import org.junit.Assert.*
import org.junit.Test

class PlacardParserTest {
    private fun evidence(vararg strings:String)=OcrEvidence(strings.joinToString("\n"),listOf(OcrBlock("",null,emptyList(),"en",
        strings.mapIndexed { i,s -> OcrLine(s,OcrBox(20,20+i*30,300,40+i*30),emptyList(),"en") })),10,"image-sha",500,900)
    @Test fun commonPlacardNeedsOnlyBrandAndName() {
        val result=PlacardParser.extract(evidence("TECHNOGYM","LEG PRESS","Adjust the seat before use"),null)
        assertEquals("Technogym",result.suggested(PlacardField.BRAND)?.value)
        assertEquals("LEG PRESS",result.suggested(PlacardField.MACHINE_NAME)?.value)
        assertTrue(result.choices(PlacardField.STARTING_RESISTANCE).isEmpty())
        assertTrue(result.choices(PlacardField.PULLEY_RATIO).isEmpty())
    }
    @Test fun logoOnlyContributesBrandNotMachineOrSpecs() {
        val result=PlacardParser.extract(evidence("Chest Press"),evidence("PRECOR","Machine: Leg Press","Starting weight: 100 kg"))
        assertEquals("Precor",result.suggested(PlacardField.BRAND)?.value)
        assertEquals("Chest Press",result.suggested(PlacardField.MACHINE_NAME)?.value)
        assertTrue(result.choices(PlacardField.STARTING_RESISTANCE).isEmpty())
    }
    @Test fun stackedLogoKeepsBothRawLines() {
        val r=PlacardParser.extract(null,evidence("LIFE","FITNESS"))
        assertEquals("Life Fitness",r.suggested(PlacardField.BRAND)?.value)
        assertEquals("LIFE\nFITNESS",r.suggested(PlacardField.BRAND)?.raw)
    }
    @Test fun fuzzyBrandIsVisibleButNotAutomaticallySelected() {
        val r=PlacardParser.extract(null,evidence("TECHN0GYM"))
        assertEquals("Technogym",r.choices(PlacardField.BRAND).single().value)
        assertNull(r.suggested(PlacardField.BRAND))
    }
    @Test fun unknownLogoDoesNotBecomeKnownBrand() {
        assertTrue(PlacardParser.extract(null,evidence("MY UNKNOWN BRAND")).candidates.isEmpty())
    }
    @Test fun conflictingBrandsRequireSelection() {
        val r=PlacardParser.extract(evidence("Life Fitness"),evidence("Hammer Strength"))
        assertEquals(2,r.choices(PlacardField.BRAND).size);assertNull(r.suggested(PlacardField.BRAND))
    }
    @Test fun modelIdentifierIsPreservedWithoutGrammarCorrection() {
        val r=PlacardParser.extract(evidence("Model: IO-7O","Machine: Super Squat"),null)
        assertEquals("IO-7O",r.suggested(PlacardField.MODEL_ID)?.value)
        assertEquals("Super Squat",r.suggested(PlacardField.MACHINE_NAME)?.value)
    }
    @Test fun labelledValuesCanBeOnTheNextNearbyLine() {
        val r=PlacardParser.extract(evidence("Starting resistance:","4.5 kg","Model:","ABC-100"),null)
        assertEquals(4.5,r.suggested(PlacardField.STARTING_RESISTANCE)!!.number!!,0.0)
        assertEquals("Starting resistance:\n4.5 kg",r.suggested(PlacardField.STARTING_RESISTANCE)?.raw)
        assertEquals("ABC-100",r.suggested(PlacardField.MODEL_ID)?.value)
    }
    @Test fun neighbouringColumnDoesNotSupplyALabelValue() {
        val e=evidence("Starting resistance:","100 kg")
        val lines=e.blocks.single().lines.toMutableList();lines[1]=lines[1].copy(box=OcrBox(350,50,480,70))
        val r=PlacardParser.extract(e.copy(blocks=listOf(e.blocks.single().copy(lines=lines))),null)
        assertTrue(r.choices(PlacardField.STARTING_RESISTANCE).isEmpty())
    }
    @Test fun bareNumbersAndTrainingRatiosAreNotSpecifications() {
        val r=PlacardParser.extract(evidence("100 kg","2:1","Tempo: 2:1","Maximum user weight: 150 kg","Weight stack: 100 kg"),null)
        assertTrue(r.choices(PlacardField.STARTING_RESISTANCE).isEmpty());assertTrue(r.choices(PlacardField.PULLEY_RATIO).isEmpty())
    }
    @Test fun explicitRatioPreservesDirectionAndDoesNotComputeLoads() {
        val r=PlacardParser.extract(evidence("Pulley ratio: 1:2"),null).suggested(PlacardField.PULLEY_RATIO)!!
        assertEquals("1:2",r.value);assertNull(r.number);assertEquals("Pulley ratio: 1:2",r.raw)
    }
    @Test fun poundsRemainPoundsAndZeroStartingResistanceIsValid() {
        assertEquals("lb",PlacardParser.extract(evidence("Starting weight: 10 lbs"),null).suggested(PlacardField.STARTING_RESISTANCE)?.unit)
        assertEquals(0.0,PlacardParser.extract(evidence("Starting resistance: 0 kg"),null).suggested(PlacardField.STARTING_RESISTANCE)!!.number!!,0.0)
    }
    @Test fun numericalCorrectionsNeedReviewAndRangesAreNotCollapsed() {
        val r=PlacardParser.extract(evidence("Starting weight: 7O kg"),null)
        assertEquals(70.0,r.choices(PlacardField.STARTING_RESISTANCE).single().number!!,0.0);assertNull(r.suggested(PlacardField.STARTING_RESISTANCE))
        assertTrue(PlacardParser.extract(evidence("Starting weight: 5-10 kg"),null).choices(PlacardField.STARTING_RESISTANCE).isEmpty())
    }
    @Test fun instructionsAreNotMachineNamesOrModelIdentifiers() {
        val r=PlacardParser.extract(evidence("Sit on the leg press","Model your posture on the diagram","Modelled posture"),null)
        assertTrue(r.choices(PlacardField.MACHINE_NAME).isEmpty());assertTrue(r.choices(PlacardField.MODEL_ID).isEmpty())
    }
    @Test fun absentGeometryPreventsGuessingNextLineAssociation() {
        val e=evidence("Model:","1234");val b=e.blocks.single()
        assertTrue(PlacardParser.extract(e.copy(blocks=listOf(b.copy(lines=b.lines.map { it.copy(box=null) }))),null).choices(PlacardField.MODEL_ID).isEmpty())
    }
}
