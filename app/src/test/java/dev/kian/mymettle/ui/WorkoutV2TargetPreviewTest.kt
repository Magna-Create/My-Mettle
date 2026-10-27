package dev.kian.mymettle.ui

import dev.kian.mymettle.domain.performance.MetricTarget
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitId
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutV2TargetPreviewTest {
    @Test fun missingTargetNeverInventsForecast() {
        assertEquals("—", workoutV2TargetPreview(null, PerformanceMetric.EXTERNAL_LOAD).value)
        assertEquals("", workoutV2TargetPreview(null, PerformanceMetric.EXTERNAL_LOAD).provenance)
    }

    @Test fun exactLoadShowsPlanReferenceSeparateFromEntry() {
        val target = MetricTarget(
            metric = PerformanceMetric.EXTERNAL_LOAD,
            kind = TargetKind.EXACT,
            lowerCanonical = 22.5,
            displayUnit = UnitId.KILOGRAM,
        )
        val preview = workoutV2TargetPreview(target, PerformanceMetric.EXTERNAL_LOAD)
        assertEquals("Reference load", preview.label)
        assertEquals("22.5 kg", preview.value)
        assertEquals("Plan", preview.provenance)
    }

    @Test fun repetitionRangeStaysAReadableRange() {
        val target = MetricTarget(
            metric = PerformanceMetric.REPETITIONS,
            kind = TargetKind.RANGE,
            lowerCanonical = 8.0,
            upperCanonical = 12.0,
        )
        val preview = workoutV2TargetPreview(target, PerformanceMetric.REPETITIONS)
        assertEquals("Target reps", preview.label)
        assertEquals("8–12", preview.value)
    }

    @Test fun unrelatedMetricIsNotPresentedAsARecommendation() {
        val target = MetricTarget(
            metric = PerformanceMetric.REPETITIONS,
            kind = TargetKind.EXACT,
            lowerCanonical = 10.0,
        )
        assertEquals("—", workoutV2TargetPreview(target, PerformanceMetric.EXTERNAL_LOAD).value)
    }
}
