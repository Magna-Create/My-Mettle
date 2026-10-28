package dev.kian.lab2b.vlm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One placard OCR observation must provide both manufacturer and other labelled fields. */
class PlacardBrandIntegrationTest {
    private fun evidence(vararg lineTexts:String):OcrEvidence {
        val lines=lineTexts.mapIndexed { index,text ->
            OcrLine(text,OcrBox(10,index*28,260,index*28+20),emptyList(), "en")
        }
        return OcrEvidence(lineTexts.joinToString("\n"),
            listOf(OcrBlock(lineTexts.joinToString("\n"),OcrBox(10,0,260,lineTexts.size*28),emptyList(),"en",lines)),
            107L,"sha_one_source",900,1300)
    }

    @Test fun stackedHammerStrengthRecognisedFromSinglePlacardPass() {
        val scan=evidence("HAMMER", "STRENGTH", "LINEAR HACK SQUAT")
        val parsed=PlacardParser.extract(scan,null)
        val brands=parsed.choices(PlacardField.BRAND)
        assertTrue(brands.any { it.value=="Hammer Strength" && it.method.startsWith("LOCAL_BRAND_CANONICAL") })
        assertEquals("Hammer Strength",parsed.suggested(PlacardField.BRAND)?.value)
        assertTrue(brands.all { it.sourceSha=="sha_one_source" })
        assertTrue(parsed.choices(PlacardField.MACHINE_NAME).any { it.value.equals("LINEAR HACK SQUAT",true) }.not())
        // The purpose is brand recognition from the same evidence, not an inferred model.
        assertTrue(parsed.choices(PlacardField.MODEL_ID).isEmpty())
    }

    @Test fun compactWordmarkIsRecognisedWithoutSeparatelyCroppedLogo() {
        val scan=evidence("LIFEFITNESS","CHEST PRESS")
        val parsed=PlacardParser.extract(scan,null)
        assertEquals("Life Fitness",parsed.suggested(PlacardField.BRAND)?.value)
        assertTrue(parsed.choices(PlacardField.MACHINE_NAME).any { it.value.equals("CHEST PRESS",ignoreCase=true) })
    }

    @Test fun cloudCandidateRequiresExplicitReviewAndDoesNotClaimModel() {
        val scan=evidence("LINEAR HACK SQUAT")
        val candidate=PlacardCandidate(
            PlacardField.BRAND,"Hammer Strength","Cloud Vision LOGO_DETECTION; API score 0.95",
            PlacardRegion.PLACARD,"sha_one_source",null,"CLOUD_LOGO_REVIEW_CANDIDATE",
        )
        val extracted=PlacardParser.extract(scan,null,listOf(candidate))
        assertTrue(extracted.choices(PlacardField.BRAND).any { it.value=="Hammer Strength" })
        assertNull(extracted.suggested(PlacardField.BRAND))
        assertTrue(extracted.choices(PlacardField.MODEL_ID).isEmpty())
        val review=PlacardReview()
        review.replace(extracted)
        assertNull(review.selection(PlacardField.BRAND))
        review.choose(PlacardField.BRAND,PlacardSelection("Hammer Strength","HUMAN_CONFIRMED_CANDIDATE",candidate))
        assertEquals("Hammer Strength",review.selection(PlacardField.BRAND)?.value)
        assertFalse(review.confirmed)
    }

    @Test fun localAndConflictingCloudEvidenceMustNotAutoSelect() {
        val scan=evidence("TECHNOGYM","CHEST PRESS")
        val foreign=PlacardCandidate(
            PlacardField.BRAND,"Life Fitness","Cloud Vision LOGO_DETECTION; API score 0.4",
            PlacardRegion.PLACARD,"sha_one_source",null,"CLOUD_LOGO_REVIEW_CANDIDATE",
        )
        val found=PlacardParser.extract(scan,null,listOf(foreign))
        assertEquals(2,found.choices(PlacardField.BRAND).map { it.value }.distinct().size)
        assertNull(found.suggested(PlacardField.BRAND))
    }

    @Test fun discardedNonBrandCloudCandidatesCannotPolluteExtraction() {
        val scan=evidence("CHEST PRESS")
        val invalid=PlacardCandidate(
            PlacardField.MACHINE_NAME,"Made-up machine name","untrusted",
            PlacardRegion.PLACARD,"sha_one_source",null,"CLOUD_LOGO_REVIEW_CANDIDATE",
        )
        val out=PlacardParser.extract(scan,null,listOf(invalid))
        assertTrue(out.choices(PlacardField.MACHINE_NAME).none { it.value=="Made-up machine name" })
    }
}
