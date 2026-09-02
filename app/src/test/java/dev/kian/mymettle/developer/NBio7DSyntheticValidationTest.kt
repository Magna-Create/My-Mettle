package dev.kian.mymettle.developer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NBio7DSyntheticValidationTest {
    @Test
    fun `consolidated 7D structural acceptance covers all pre-registered cases`() {
        val report = NBio7DSyntheticValidation.run()
        assertEquals(
            listOf(
                "high_demand",
                "sub_frontier",
                "broad_capability",
                "sparse_prior_dominated",
                "positive_trajectory_projection",
                "declining_trajectory_projection",
                "rep_extrapolation",
                "hold_duration_extrapolation",
                "repeated_contraction_extrapolation",
                "duration_only",
                "semantic_boundary_fail_closed",
                "side_isolation",
                "numerical_stress",
                "frontier_contradiction_fail_closed",
            ),
            report.cases.map { it.id },
        )
        assertEquals(14, report.passedCount)
        assertEquals(0, report.failedCount)
        assertTrue(report.allPassed, report.cases.filterNot { it.passed }.joinToString { "${it.id}:${it.detail}" })
    }
}
