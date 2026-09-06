package dev.kian.lab2b.vlm

import org.junit.Assert.*
import org.junit.Test

class ExtractionReviewTest {
    @Test fun newEvidenceClearsConfirmationAndHumanOverrides() {
        val state=PlacardReview()
        state.choose(PlacardField.MACHINE_NAME,PlacardSelection("Leg press","HUMAN_EDITED"));state.confirm(true)
        state.replace(PlacardExtraction(emptyList()))
        assertFalse(state.confirmed);assertNull(state.selection(PlacardField.MACHINE_NAME))
    }
    @Test fun unknownFieldsCanBeConfirmedButMissingPlacardOcrCannot() {
        val state=PlacardReview()
        assertThrows(IllegalArgumentException::class.java) { state.confirm(false) }
        state.confirm(true);assertTrue(state.confirmed)
    }
    @Test fun editOrClearInvalidatesPreviousConfirmation() {
        val state=PlacardReview();state.confirm(true)
        state.choose(PlacardField.BRAND,PlacardSelection("","HUMAN_CLEARED"));assertFalse(state.confirmed)
        assertEquals("",state.selection(PlacardField.BRAND)?.value)
    }
    @Test fun exportWorksWithoutAddonAndWithExplicitNone() {
        assertTrue(WeightExportRules.canExport(true,AddOnStatus.NOT_CHECKED,false))
        assertTrue(WeightExportRules.canExport(true,AddOnStatus.NONE,false))
        assertFalse(WeightExportRules.canExport(true,AddOnStatus.CAPTURED,false))
        assertTrue(WeightExportRules.canExport(true,AddOnStatus.CAPTURED,true))
        assertFalse(WeightExportRules.canExport(false,AddOnStatus.NONE,false))
    }
    @Test fun weightSummaryUsesNumericLiteralsNotStringsOrScientificNotation() {
        val result=NumericJson.weights(listOf("4.5","100","120","134").map { it.toBigDecimal().stripTrailingZeros() })
        assertEquals("[4.5,100,120,134]",result);assertEquals("[]",NumericJson.weights(emptyList()))
        assertFalse(result.contains('"'))
    }
}
