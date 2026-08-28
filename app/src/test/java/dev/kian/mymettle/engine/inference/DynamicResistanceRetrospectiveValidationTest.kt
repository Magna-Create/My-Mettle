package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicCapabilityCandidateVerdict
import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationPolicy
import dev.kian.mymettle.domain.inference.DynamicHeldOutStatus
import dev.kian.mymettle.domain.inference.DynamicMetricEvidenceAudit
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV1Contract
import dev.kian.mymettle.domain.inference.HistoricalObservationRevision
import dev.kian.mymettle.domain.inference.HistoricalObservationRevisionSelector
import dev.kian.mymettle.domain.inference.ProfileLocalResistanceCoordinate
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.performance.DynamicReferenceRepSelector
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicResistanceRetrospectiveValidationTest {
    @Test
    fun `whole held-out session contributes zero fit evidence`() {
        val evidence = listOf(
            set("a", "s1", 8, 60.0, 1, 0),
            set("b", "s2", 6, 65.0, 2, 0),
            set("c", "s2", 10, 58.0, 2, 1),
            set("d", "s3", 8, 64.0, 3, 0),
        )
        val results = evaluator().evaluateObservations(projection(evidence))
        val second = results.filter { it.sessionId == "s2" }
        assertEquals(2, second.size)
        second.forEach {
            assertEquals(listOf("obs_a"), it.trainingObservationIds)
            assertFalse("obs_b" in it.trainingObservationIds)
            assertFalse("obs_c" in it.trainingObservationIds)
        }
        val third = results.single { it.sessionId == "s3" }
        assertTrue(setOf("obs_a", "obs_b", "obs_c").all { it in third.trainingObservationIds })
    }

    @Test
    fun `future sessions cannot alter an earlier held-out prediction`() {
        val prefix = listOf(
            set("a", "s1", 8, 60.0, 1),
            set("b", "s2", 8, 62.0, 2),
        )
        val future = prefix + listOf(
            set("c", "s3", 8, 90.0, 3),
            set("d", "s4", 8, 95.0, 4),
        )
        val early = evaluator().evaluateObservations(projection(prefix)).single { it.observationId == "obs_b" }
        val replay = evaluator().evaluateObservations(projection(future)).single { it.observationId == "obs_b" }
        assertEquals(early.candidatePredictive, replay.candidatePredictive)
        assertEquals(early.trainingObservationIds, replay.trainingObservationIds)
    }

    @Test
    fun `reference reps are selected from training evidence only`() {
        val evidence = listOf(
            set("a", "s1", 6, 65.0, 1),
            set("b", "s1", 8, 60.0, 1, 1),
            set("c", "s2", 30, 30.0, 2),
        )
        val result = evaluator().evaluateObservations(projection(evidence)).single { it.observationId == "obs_c" }
        assertEquals(6.0, result.referenceRepetitions)
    }

    @Test
    fun `future correction revision does not leak through historical cutoff`() {
        val original = HistoricalObservationRevision("a", day(1), null, "60kg")
        val correction = HistoricalObservationRevision("b", day(5), "a", "62kg")
        val atDay3 = HistoricalObservationRevisionSelector.currentAsKnownAt(listOf(original, correction), day(3))
        assertEquals(listOf("a"), atDay3.map { it.observationId })
        val atDay6 = HistoricalObservationRevisionSelector.currentAsKnownAt(listOf(original, correction), day(6))
        assertEquals(listOf("b"), atDay6.map { it.observationId })
    }

    @Test
    fun `successful set is scored with demonstration predictive not frontier interval`() {
        val results = evaluator().evaluateObservations(projection(generated(6)))
        val evaluable = results.filter { it.status == DynamicHeldOutStatus.EVALUABLE }
        assertTrue(evaluable.isNotEmpty())
        evaluable.forEach {
            val predictive = requireNotNull(it.candidatePredictive)
            assertTrue(predictive.observedCdf in 0.0..1.0)
            val frontierProbability = requireNotNull(it.frontierAtOrAboveObservedProbability)
            assertTrue(frontierProbability in 0.0..1.0)
        }
    }

    @Test
    fun `benchmark probabilistic metrics remain unsupported null`() {
        val summary = evaluator().evaluate(projection(generated(8)))
        assertNull(summary.benchmarkLogPredictiveDensity)
        assertNull(summary.benchmarkPredictiveCoverage)
        assertNull(summary.benchmarkPitCalibration)
        assertTrue(summary.benchmarkLatestAnchorMaeKg != null)
    }

    @Test
    fun `first session is insufficient rather than fabricated model failure`() {
        val results = evaluator().evaluateObservations(projection(generated(3)))
        val first = results.filter { it.sessionId == "s0" }
        assertTrue(first.isNotEmpty())
        assertTrue(first.all { it.status == DynamicHeldOutStatus.INSUFFICIENT_EVIDENCE })
        assertTrue(first.all { it.candidatePredictive == null })
        val summary = evaluator().evaluate(projection(generated(3)))
        assertTrue(summary.insufficientEvidenceCount > 0)
        assertEquals(0, summary.modelFailureCount)
    }

    @Test
    fun `predictive CDF and interval are deterministic and ordered`() {
        val evidence = generated(8)
        val training = evidence.filter { it.sessionId != "s7" }
        val model = DynamicStochasticFrontierModel()
        val fit = model.fit(
            dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest(
                projection = projection(training),
                inferenceHorizon = training.maxOf { it.completedAt },
                modelConfig = model.config.toModelConfig(Instant.parse("2026-08-27T00:00:00Z")),
            ),
        )
        val predictive = DynamicDemonstrationPredictiveEvaluator(model)
        val first = predictive.evaluate(fit, 8.0, 70.0)
        val second = predictive.evaluate(fit, 8.0, 70.0)
        assertEquals(first, second)
        assertTrue(first.p05ResistanceKg > 0.0)
        assertTrue(first.p05ResistanceKg <= first.p50ResistanceKg)
        assertTrue(first.p50ResistanceKg <= first.p95ResistanceKg)
        assertTrue(predictive.cdf(fit, 8.0, 50.0) < predictive.cdf(fit, 8.0, 90.0))
    }

    @Test
    fun `sparse calibration reports insufficient support`() {
        val summary = evaluator().evaluate(projection(generated(4)))
        assertTrue(summary.candidatePitCalibration.sampleCount < 15)
        assertNull(summary.candidatePitCalibration.meanAbsoluteBinError)
    }

    @Test
    fun `verdict does not accept tiny evidence`() {
        val summary = evaluator().evaluate(projection(generated(4)))
        assertEquals(DynamicCapabilityCandidateVerdict.INSUFFICIENT_EVIDENCE, evaluator().verdict(summary))
    }

    @Test
    fun `candidate validation remains context free by construction`() {
        val first = evaluator().evaluate(projection(generated(8)))
        val second = evaluator().evaluate(projection(generated(8)))
        assertEquals(first, second)
        assertTrue(DynamicResistanceV1Contract.contextPolicy.allowedTagIds.isEmpty())
    }

    private fun evaluator() = DynamicResistanceRetrospectiveEvaluator(
        policy = DynamicCapabilityValidationPolicy(minimumCalibrationObservations = 15),
    )

    private fun generated(sessions: Int): List<DynamicResistanceEvidence> = buildList {
        repeat(sessions) { session ->
            listOf(5, 8, 12).forEachIndexed { ordinal, reps ->
                val frontier = 78.0 + session * 0.7
                val slack = listOf(0.02, 0.06, 0.03)[ordinal]
                val noise = listOf(0.01, -0.01, 0.0)[(session + ordinal) % 3]
                val resistance = exp(ln(frontier) - 0.18 * ln(reps / 8.0) - slack + noise)
                add(set("${session}_$ordinal", "s$session", reps, resistance, session + 1, ordinal))
            }
        }
    }

    private fun projection(evidence: List<DynamicResistanceEvidence>): DynamicResistanceEvidenceProjection =
        DynamicResistanceEvidenceProjection(
            profile = PROFILE,
            side = Laterality.BILATERAL,
            evidence = evidence.sortedWith(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId }),
            exclusions = emptyList(),
            referenceRepetitions = DynamicReferenceRepSelector.select(evidence),
            policy = DynamicResistanceV1Contract.evidencePolicy,
        )

    private fun set(
        id: String,
        sessionId: String,
        reps: Int,
        kg: Double,
        day: Int,
        ordinal: Int = 0,
    ) = DynamicResistanceEvidence(
        observationId = "obs_$id",
        setRecordId = "set_$id",
        sessionId = sessionId,
        executionProfileVersionId = PROFILE.executionProfileVersionId,
        side = Laterality.BILATERAL,
        completedAt = day(day).plusSeconds(ordinal.toLong()),
        repetitions = reps,
        resistance = ProfileLocalResistanceCoordinate(
            value = kg,
            unit = UnitId.KILOGRAM,
            resistanceSemantics = ResistanceSemantics.EXTERNAL,
            entryBasis = EntryBasis.TOTAL,
            resistanceModelVersion = "test-resistance",
            resolverVersion = DynamicResistanceV1Contract.RESISTANCE_RESOLVER_VERSION,
        ),
        metricEvidence = listOf(
            DynamicMetricEvidenceAudit(
                PerformanceMetric.EXTERNAL_LOAD,
                Quantity(kg, UnitId.KILOGRAM),
                Quantity(kg, UnitId.KILOGRAM),
                "synthetic",
                "summary",
            ),
            DynamicMetricEvidenceAudit(
                PerformanceMetric.REPETITIONS,
                Quantity(reps.toDouble(), UnitId.REPETITION),
                Quantity(reps.toDouble(), UnitId.REPETITION),
                "synthetic",
                "summary",
            ),
        ),
        warmUp = false,
        setKind = "working",
        evidencePolicyIdentity = DynamicResistanceV1Contract.evidencePolicy.identity,
    )

    private fun day(day: Int): Instant = BASE.plusSeconds(day * 86_400L)

    companion object {
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val PROFILE = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
            executionProfileId = ExecutionProfileId("profile"),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            resistanceModel = ResistanceModel("test-resistance", ResistanceSemantics.EXTERNAL, 0.0, 1.0, 0.0),
            entryBasis = EntryBasis.TOTAL,
            lateralityMode = LateralityMode.BILATERAL_ONLY,
        )
    }
}
