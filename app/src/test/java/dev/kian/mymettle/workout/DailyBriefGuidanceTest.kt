package dev.kian.mymettle.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyBriefGuidanceTest {
    @Test
    fun `light session without bodyweight uses conservative defaults`() {
        val guidance = dailyBriefGuidance(
            DailyBriefSessionProfile(
                workingSets = 8,
                estimatedDurationSeconds = 42 * 60,
                bodyweightKg = null,
                targetSegments = listOf("gluteus_maximus_whole", "vastus_lateralis_whole"),
            ),
        )

        assertEquals("—", guidance.proteinDaily)
        assertEquals("20–40", guidance.proteinPerMeal)
        assertEquals("20", guidance.carbohydratesBefore)
        assertEquals("500", guidance.waterDuring)
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

        assertEquals("130", guidance.proteinDaily)
        assertEquals("20", guidance.proteinPerMeal)
        assertEquals("30", guidance.carbohydratesBefore)
        assertEquals("600", guidance.waterDuring)
        assertEquals("Shoulders + Triceps", guidance.emphasis)
        assertTrue(guidance.isWeightAware)
    }

    @Test
    fun `short moderate lifting day does not duplicate pre and post fuel`() {
        val guidance = dailyBriefGuidance(
            DailyBriefSessionProfile(
                workingSets = 15,
                estimatedDurationSeconds = 39 * 60,
                bodyweightKg = 65.0,
                targetSegments = emptyList(),
            ),
        )

        assertEquals("105", guidance.proteinDaily)
        assertEquals("20", guidance.proteinPerMeal)
        assertEquals("25", guidance.carbohydratesBefore)
        assertEquals("600", guidance.waterDuring)
    }

    @Test
    fun `high session separates daily protein from serving guidance`() {
        val guidance = dailyBriefGuidance(
            DailyBriefSessionProfile(
                workingSets = 20,
                estimatedDurationSeconds = 85 * 60,
                bodyweightKg = 180.0,
                targetSegments = emptyList(),
            ),
        )

        assertEquals("290", guidance.proteinDaily)
        assertEquals("40", guidance.proteinPerMeal)
        assertEquals("90", guidance.carbohydratesBefore)
        assertEquals("700", guidance.waterDuring)
        assertEquals("Full body", guidance.emphasis)
    }
}
