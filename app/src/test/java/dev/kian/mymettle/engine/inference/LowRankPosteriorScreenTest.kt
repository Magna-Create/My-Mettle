package dev.kian.mymettle.engine.inference

import kotlin.math.exp
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LowRankPosteriorScreenTest {
    @Test
    fun `separable posterior is identified as useful low rank structure`() {
        val row = List(25) { index -> exp(-0.5 * ((index - 12.0) / 4.0).pow(2)) }
        val col = List(31) { index -> exp(-0.5 * ((index - 15.0) / 5.0).pow(2)) }
        val matrix = row.map { r -> col.map { c -> r * c } }
        val result = LowRankPosteriorViabilityScreen.screen(matrix, rank = 1)
        assertTrue(result.compressionRatio > 5.0)
        assertTrue(result.l1ProbabilityError < 1e-6)
        assertTrue(result.useful)
    }

    @Test
    fun `strong diagonal dependence is not falsely declared rank one`() {
        val matrix = List(25) { row ->
            List(25) { col -> exp(-0.5 * ((row - col) / 1.2).pow(2)) }
        }
        val result = LowRankPosteriorViabilityScreen.screen(matrix, rank = 1)
        assertTrue(result.compressionRatio > 5.0)
        assertTrue(result.l1ProbabilityError > 0.10)
        assertFalse(result.useful)
    }
}
