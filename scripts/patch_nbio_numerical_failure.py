from pathlib import Path

# 1) Fail closed when a conditional-Laplace forecast leaves Candidate-v2's
# already-versioned numerical resistance domain. Do not clamp or silently rescue it.
p = Path('app/src/main/java/dev/kian/mymettle/engine/performance/DynamicTrendFrontierModel.kt')
s = p.read_text()
old = '''    fun projectToSessionOffset(fit: DynamicTrendFrontierFit, sessionOffset: Double): DynamicStochasticFrontierFit {
        require(sessionOffset.isFinite())
        val projectedNodes = fit.posteriorNodes.map { node ->
            DynamicFrontierPosteriorNode(
                logFrontierAtReference = node.logFrontierAtLatestSession + node.frontierTrend * sessionOffset,
                slope = node.slope,
                slackScale = node.slackScale,
                noiseScale = node.noiseScale,
                posteriorWeight = node.posteriorWeight,
            )
        }
'''
new = '''    fun projectToSessionOffset(fit: DynamicTrendFrontierFit, sessionOffset: Double): DynamicStochasticFrontierFit {
        require(sessionOffset.isFinite())
        val minimumLogResistance = ln(config.baseConfig.numericalMinimumResistanceKg)
        val maximumLogResistance = ln(config.baseConfig.numericalMaximumResistanceKg)
        val projectedNodes = fit.posteriorNodes.map { node ->
            val projectedLogFrontier = node.logFrontierAtLatestSession + node.frontierTrend * sessionOffset
            if (!projectedLogFrontier.isFinite() || projectedLogFrontier !in minimumLogResistance..maximumLogResistance) {
                throw DynamicCapabilityFitException(
                    DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR,
                    "Conditional-Laplace Candidate-v2 projection left the configured numerical resistance domain; approximation is unavailable for this horizon.",
                )
            }
            DynamicFrontierPosteriorNode(
                logFrontierAtReference = projectedLogFrontier,
                slope = node.slope,
                slackScale = node.slackScale,
                noiseScale = node.noiseScale,
                posteriorWeight = node.posteriorWeight,
            )
        }
'''
if old not in s:
    raise SystemExit('conditional Laplace projection anchor not found')
s = s.replace(old, new, 1)
p.write_text(s)

# 2) Fidelity diagnostics must never serialize Infinity/NaN from a challenger.
p = Path('app/src/main/java/dev/kian/mymettle/engine/inference/DynamicTrendPosteriorFidelity.kt')
s = p.read_text()
old = '''    private fun weightedSummary(values: List<WeightedValue>): Summary {
        require(values.isNotEmpty())
        val total = values.sumOf { it.weight }
'''
new = '''    private fun weightedSummary(values: List<WeightedValue>): Summary {
        require(values.isNotEmpty())
        require(values.all { it.value.isFinite() && it.weight.isFinite() && it.weight >= 0.0 }) {
            "Posterior fidelity summary requires finite values and non-negative finite weights."
        }
        val total = values.sumOf { it.weight }
'''
if old not in s:
    raise SystemExit('fidelity finite-value anchor not found')
s = s.replace(old, new, 1)
p.write_text(s)

# 3) Preserve the reason when a sampled current-state fidelity comparison is unavailable.
p = Path('app/src/main/java/dev/kian/mymettle/developer/NBioAdaptiveInferenceAcceptance.kt')
s = p.read_text()
old = '''        return CurrentProfileEvaluation(
            sparseFidelity = if (dense != null && sparse != null) {
                runCatching { DynamicTrendPosteriorFidelity.compare(dense, sparse) }.getOrNull()
            } else null,
            laplaceFidelity = if (dense != null && laplace != null) {
                runCatching { DynamicTrendPosteriorFidelity.compare(dense, laplace) }.getOrNull()
            } else null,
'''
new = '''        val sparseFidelity = if (dense != null && sparse != null) {
            runCatching { DynamicTrendPosteriorFidelity.compare(dense, sparse) }
                .onFailure { limitations += "Adaptive-sparse posterior-fidelity comparison failed: ${it.message}" }
                .getOrNull()
        } else null
        val laplaceFidelity = if (dense != null && laplace != null) {
            runCatching { DynamicTrendPosteriorFidelity.compare(dense, laplace) }
                .onFailure { limitations += "Conditional-Laplace posterior-fidelity comparison failed: ${it.message}" }
                .getOrNull()
        } else null

        return CurrentProfileEvaluation(
            sparseFidelity = sparseFidelity,
            laplaceFidelity = laplaceFidelity,
'''
if old not in s:
    raise SystemExit('acceptance fidelity anchor not found')
s = s.replace(old, new, 1)
# Version the report because numerical approximation failures are now explicitly preserved through export.
s = s.replace('.put("formatVersion", 5)', '.put("formatVersion", 6)', 1)
p.write_text(s)

# 4) Regression: pathological projected trend nodes fail typed rather than overflowing PosteriorSummary.
p = Path('app/src/test/java/dev/kian/mymettle/engine/performance/DynamicTrendFrontierModelTest.kt')
s = p.read_text()
s = s.replace(
    'import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest\n',
    'import dev.kian.mymettle.domain.inference.DynamicCapabilityFitException\nimport dev.kian.mymettle.domain.inference.DynamicCapabilityFitFailureReason\nimport dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest\n',
    1,
)
s = s.replace(
    'import kotlin.test.assertEquals\nimport kotlin.test.assertTrue\n',
    'import kotlin.test.assertEquals\nimport kotlin.test.assertFailsWith\nimport kotlin.test.assertTrue\n',
    1,
)
anchor = '''    @Test
    fun `math identity is shared while dense and conditional Laplace solver identities are distinct`() {
'''
test = '''    @Test
    fun `conditional Laplace projection fails closed outside numerical resistance domain`() {
        val stable = fitDense(generated(sessions = 5, trend = 0.03, repsBySession = { listOf(8) }))
        val pathological = stable.copy(
            posteriorNodes = stable.posteriorNodes.map { it.copy(frontierTrend = 800.0) },
        )
        val model = DynamicTrendFrontierModel(TEST_MATH_CONFIG)
        val failure = assertFailsWith<DynamicCapabilityFitException> {
            model.projectToSessionOffset(pathological, 1.0)
        }
        assertEquals(DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR, failure.reason)
    }

'''
if anchor not in s:
    raise SystemExit('trend model test anchor not found')
s = s.replace(anchor, test + anchor, 1)
p.write_text(s)

# 5) Regression: fidelity comparison rejects non-finite next-frontier values instead of exporting them.
p = Path('app/src/test/java/dev/kian/mymettle/engine/inference/DynamicTrendPosteriorFidelityTest.kt')
s = p.read_text()
s = s.replace(
    'import kotlin.test.assertEquals\nimport kotlin.test.assertTrue\n',
    'import kotlin.test.assertEquals\nimport kotlin.test.assertFailsWith\nimport kotlin.test.assertTrue\n',
    1,
)
anchor = '''    @Test
    fun `trend distortion is visible in tails quantiles dependence and next frontier`() {
'''
test = '''    @Test
    fun `non finite challenger forecast fails fidelity comparison closed`() {
        val reference = fixture("reference", listOf(-0.02, 0.04))
        val pathological = fixture("pathological", listOf(800.0, 810.0))
        assertFailsWith<IllegalArgumentException> {
            DynamicTrendPosteriorFidelity.compare(reference, pathological)
        }
    }

'''
if anchor not in s:
    raise SystemExit('posterior fidelity test anchor not found')
s = s.replace(anchor, test + anchor, 1)
p.write_text(s)
