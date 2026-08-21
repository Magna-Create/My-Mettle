package dev.kian.mymettle.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyBriefGuidanceTest {
    @Test
    fun `light session without bodyweight uses honest ranges`() {
        val guidance = dailyBriefGuidance(
            DailyBriefSessionProfile(
                workingSets = 8,
                estimatedDurationSeconds = 42 * 60,
                bodyweightKg = null,
                targetSegments = listOf("gluteus_maximus_whole", "vastus_lateralis_whole"),
            ),
        )

        assertEquals("20–40", guidance.proteinBefore)
        assertEquals("25–40", guidance.carbohydratesBefore)
        assertEquals("400–600", guidance.waterDuring)
        assertEquals("Glutes + Quads", guidance.emphasis)
        assertFalse(guidance.isWeightAware)
    }

    @Test
    fun `moderate session uses recorded bodyweight`() {
        val guidance = dailyBriefGuidance(
            DailyBriefSessionProfile(
                workingSets = 12,
                estimatedDurationSeconds = 58 * 60,
                bodyweightKg = 80.0,
                targetSegments = listOf("deltoid_acromial_part", "triceps_brachii_long_head"),
            ),
        )

        assertEquals("25", guidance.proteinBefore)
        assertEquals("60", guidance.carbohydratesBefore)
        assertEquals("500–700", guidance.waterDuring)
        assertEquals("Shoulders + Triceps", guidance.emphasis)
        assertTrue(guidance.isWeightAware)
    }

    @Test
    fun `high session clamps protein to evidence supported serving range`() {
        val guidance = dailyBriefGuidance(
            DailyBriefSessionProfile(
                workingSets = 20,
                estimatedDurationSeconds = 85 * 60,
                bodyweightKg = 180.0,
                targetSegments = emptyList(),
            ),
        )

        assertEquals("40", guidance.proteinAfter)
        assertEquals("100", guidance.carbohydratesAfter)
        assertEquals("600–800", guidance.waterDuring)
        assertEquals("Full body", guidance.emphasis)
    }
}
