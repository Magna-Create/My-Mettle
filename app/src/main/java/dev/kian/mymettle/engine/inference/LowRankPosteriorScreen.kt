package dev.kian.mymettle.engine.inference

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Targeted low-rank viability screen for posterior tensors after a chosen matricisation.
 * This is not a production tensor-train solver. It answers the cheaper prerequisite question:
 * does the posterior contain enough low-rank structure to justify a TT implementation at all?
 */
data class LowRankPosteriorScreenResult(
    val rows: Int,
    val columns: Int,
    val requestedRank: Int,
    val effectiveStoredScalars: Int,
    val denseStoredScalars: Int,
    val compressionRatio: Double,
    val l1ProbabilityError: Double,
    val maxRowMarginalError: Double,
    val maxColumnMarginalError: Double,
    val klDenseToApprox: Double,
    val useful: Boolean,
) {
    init {
        require(rows > 0 && columns > 0 && requestedRank > 0)
        require(effectiveStoredScalars > 0 && denseStoredScalars == rows * columns)
        require(compressionRatio.isFinite() && compressionRatio > 0.0)
        require(l1ProbabilityError.isFinite() && l1ProbabilityError >= 0.0)
        require(maxRowMarginalError.isFinite() && maxRowMarginalError >= 0.0)
        require(maxColumnMarginalError.isFinite() && maxColumnMarginalError >= 0.0)
        require(klDenseToApprox.isFinite() && klDenseToApprox >= 0.0)
    }
}

object LowRankPosteriorViabilityScreen {
    const val VERSION = "posterior-matricisation-greedy-low-rank-screen-v1"

    fun screen(
        denseProbability: List<List<Double>>,
        rank: Int,
        minimumUsefulCompressionRatio: Double = 2.0,
        maximumUsefulL1Error: Double = 0.02,
        maximumUsefulMarginalError: Double = 0.01,
    ): LowRankPosteriorScreenResult {
        require(denseProbability.isNotEmpty())
        val rows = denseProbability.size
        val columns = denseProbability.first().size
        require(columns > 0 && denseProbability.all { it.size == columns })
        require(rank in 1..minOf(rows, columns))
        require(denseProbability.flatten().all { it.isFinite() && it >= 0.0 })
        require(minimumUsefulCompressionRatio > 0.0)
        require(maximumUsefulL1Error >= 0.0 && maximumUsefulMarginalError >= 0.0)

        val total = denseProbability.sumOf { row -> row.sum() }
        require(total > 0.0 && total.isFinite())
        val dense = Array(rows) { row -> DoubleArray(columns) { col -> denseProbability[row][col] / total } }
        val residual = Array(rows) { row -> dense[row].clone() }
        val factors = mutableListOf<RankOneFactor>()

        repeat(rank) { component ->
            var v = DoubleArray(columns) { index -> 1.0 + ((index + component) % 7) * 0.01 }
            normaliseVector(v)
            var u = DoubleArray(rows)
            repeat(60) {
                u = multiply(residual, v)
                if (norm(u) <= 1e-15) return@repeat
                normaliseVector(u)
                v = multiplyTranspose(residual, u)
                if (norm(v) <= 1e-15) return@repeat
                normaliseVector(v)
            }
            val av = multiply(residual, v)
            val sigma = dot(u, av)
            if (!sigma.isFinite() || abs(sigma) <= 1e-15) return@repeat
            factors += RankOneFactor(sigma, u.clone(), v.clone())
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    residual[row][col] -= sigma * u[row] * v[col]
                }
            }
        }

        val approximation = Array(rows) { DoubleArray(columns) }
        factors.forEach { factor ->
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    approximation[row][col] += factor.sigma * factor.u[row] * factor.v[col]
                }
            }
        }
        for (row in 0 until rows) for (col in 0 until columns) {
            approximation[row][col] = maxOf(0.0, approximation[row][col])
        }
        val approxTotal = approximation.sumOf { it.sum() }
        val safeApproximation = if (approxTotal > 0.0 && approxTotal.isFinite()) {
            Array(rows) { row -> DoubleArray(columns) { col -> approximation[row][col] / approxTotal } }
        } else {
            Array(rows) { DoubleArray(columns) { 1.0 / (rows * columns).toDouble() } }
        }

        val l1 = sumCells(rows, columns) { row, col -> abs(dense[row][col] - safeApproximation[row][col]) }
        val rowError = (0 until rows).maxOf { row -> abs(dense[row].sum() - safeApproximation[row].sum()) }
        val columnError = (0 until columns).maxOf { col ->
            abs((0 until rows).sumOf { dense[it][col] } - (0 until rows).sumOf { safeApproximation[it][col] })
        }
        val epsilon = 1e-15
        val kl = sumCells(rows, columns) { row, col ->
            val p = dense[row][col]
            if (p == 0.0) 0.0 else p * ln(p / maxOf(safeApproximation[row][col], epsilon))
        }
        val stored = maxOf(1, factors.size * (rows + columns + 1))
        val denseScalars = rows * columns
        val compression = denseScalars.toDouble() / stored.toDouble()
        val useful = compression >= minimumUsefulCompressionRatio &&
            l1 <= maximumUsefulL1Error &&
            rowError <= maximumUsefulMarginalError &&
            columnError <= maximumUsefulMarginalError

        return LowRankPosteriorScreenResult(
            rows = rows,
            columns = columns,
            requestedRank = rank,
            effectiveStoredScalars = stored,
            denseStoredScalars = denseScalars,
            compressionRatio = compression,
            l1ProbabilityError = l1,
            maxRowMarginalError = rowError,
            maxColumnMarginalError = columnError,
            klDenseToApprox = maxOf(0.0, kl),
            useful = useful,
        )
    }

    private data class RankOneFactor(val sigma: Double, val u: DoubleArray, val v: DoubleArray)

    private fun multiply(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray =
        DoubleArray(matrix.size) { row -> matrix[row].indices.sumOf { col -> matrix[row][col] * vector[col] } }

    private fun multiplyTranspose(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray =
        DoubleArray(matrix.first().size) { col -> matrix.indices.sumOf { row -> matrix[row][col] * vector[row] } }

    private fun norm(vector: DoubleArray): Double = sqrt(vector.sumOf { it * it })

    private fun normaliseVector(vector: DoubleArray) {
        val length = norm(vector)
        if (length <= 1e-15) return
        vector.indices.forEach { vector[it] /= length }
    }

    private fun dot(left: DoubleArray, right: DoubleArray): Double = left.indices.sumOf { left[it] * right[it] }

    private inline fun sumCells(rows: Int, columns: Int, value: (Int, Int) -> Double): Double {
        var total = 0.0
        for (row in 0 until rows) for (col in 0 until columns) total += value(row, col)
        return total
    }
}
