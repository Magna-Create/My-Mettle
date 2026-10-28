package dev.kian.lab2b.vlm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManufacturerCatalogueTest {
    @Test fun storedNamesAreCompactAndDisplayBreaksAreSeparate() {
        val all = ManufacturerCatalogue.brands
        assertTrue(all.size >= 45)
        assertEquals(all.size, all.map { it.key }.distinct().size)
        assertTrue(all.all { it.key.matches(Regex("[a-z0-9]+")) })
        assertTrue(all.all { brand -> brand.aliasKeys.all { it.matches(Regex("[a-z0-9]+")) } })

        assertEquals("Life Fitness", all.single { it.key == "lifefitness" }.displayName)
        assertEquals(listOf(4), all.single { it.key == "lifefitness" }.spacesAfter)
        assertEquals("Hammer Strength", all.single { it.key == "hammerstrength" }.displayName)
        assertEquals(listOf(6), all.single { it.key == "hammerstrength" }.spacesAfter)
        assertEquals("Technogym", all.single { it.key == "technogym" }.displayName)
        assertTrue(all.single { it.key == "technogym" }.spacesAfter.isEmpty())
        assertEquals("BH Fitness", all.single { it.key == "bhfitness" }.displayName)
        assertEquals("TuffStuff", all.single { it.key == "tuffstuff" }.displayName)
        assertEquals("WaterRower", all.single { it.key == "waterrower" }.displayName)
    }

    @Test fun insensitiveToCaseSpacePunctuationAndLineBreaks() {
        fun strong(vararg lines: String) =
            ManufacturerCatalogue.uniqueStrongCandidate(ManufacturerCatalogue.match(lines.toList()))?.manufacturer
        assertEquals("Life Fitness", strong("LIFEFITNESS"))
        assertEquals("Life Fitness", strong("life fitness"))
        assertEquals("Life Fitness", strong("LiFe-FiTnEsS"))
        assertEquals("Hammer Strength", strong("HAMMERSTRENGTH"))
        assertEquals("Hammer Strength", strong("HAMMER", "STRENGTH"))
        assertEquals("Hammer Strength", strong("Hammer  Strength"))
        assertEquals("Technogym", strong("TECHNO GYM"))
        assertEquals("Technogym", strong("technogym"))
        assertEquals("Arsenal Strength", strong("ArsenalStrength Machines"))
    }

    @Test fun doNotMatchInsideUnrelatedTokensOrInventOtherBrands() {
        fun matched(vararg lines: String) = ManufacturerCatalogue.match(lines.toList())
        assertTrue(matched("I felt an impulse to try this machine").isEmpty())
        assertTrue(matched("matrices and matrixes").isEmpty())
        assertTrue(matched("LIFEFITNESSX").isEmpty())
        assertTrue(matched("hammer fitness").isEmpty())
        assertTrue(matched("Hammer sport").any { it.manufacturer == "Hammer Sport" })
        assertFalse(matched("Hammer sport").any { it.manufacturer == "Hammer Strength" })
    }

    @Test fun genericBrandIsReviewOnlyUnlessRecognisedByFullAlias() {
        val generic = ManufacturerCatalogue.match(listOf("Matrix"))
        assertEquals(ManufacturerCatalogue.MatchKind.REVIEW_ONLY, generic.single().kind)
        assertNull(ManufacturerCatalogue.uniqueStrongCandidate(generic))
        val stronger = ManufacturerCatalogue.match(listOf("Matrix Fitness"))
        assertEquals("Matrix", ManufacturerCatalogue.uniqueStrongCandidate(stronger)?.manufacturer)
        val pulse = ManufacturerCatalogue.match(listOf("Pulse"))
        assertNull(ManufacturerCatalogue.uniqueStrongCandidate(pulse))
        assertEquals("Pulse", ManufacturerCatalogue.uniqueStrongCandidate(
            ManufacturerCatalogue.match(listOf("Pulse Fitness"))
        )?.manufacturer)
    }

    @Test fun conflictingStrongBrandsDoNotTriggerSilentCloudSkip() {
        val candidates = ManufacturerCatalogue.match(listOf("Technogym", "Life Fitness"))
        assertNull(ManufacturerCatalogue.uniqueStrongCandidate(candidates))
    }

    @Test fun placardAndCloudUseOneCatalogue() {
        assertEquals(
            ManufacturerCatalogue.brands.map { it.displayName },
            PlacardParser.brands,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidKeyIsRejectedAtConstruction() {
        ManufacturerCatalogue.Brand("Life Fitness", spacesAfter = listOf(4))
    }
}
