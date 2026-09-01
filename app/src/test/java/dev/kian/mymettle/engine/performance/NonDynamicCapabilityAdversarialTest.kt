package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.LoadedHoldCapabilityQuery
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import dev.kian.mymettle.domain.inference.NonDynamicExclusionReason
import dev.kian.mymettle.domain.inference.NonDynamicFitFailureReason
import dev.kian.mymettle.domain.inference.NonDynamicProfileSemantics
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NonDynamicCapabilityAdversarialTest {
    @Test
    fun `execution profile versions remain hard statistical boundaries`() {
        val a = loadedHoldProfile("a")
        val b = loadedHoldProfile("b")
        val evidenceFromA = observation(a, 0, load = 25.0, duration = 30.0)
        val projectedIntoB = NonDynamicCapabilityEvidenceProjector.project(b, Laterality.UNKNOWN, listOf(evidenceFromA))
        assertTrue(projectedIntoB.evidence.isEmpty())
        assertEquals(NonDynamicExclusionReason.PROFILE_VERSION_MISMATCH, projectedIntoB.exclusions.single().reason)
        val failure = assertFailsWith<NonDynamicCapabilityFitException> {
            NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.loadedHold)
                .fit(projectedIntoB, BASE.plusSeconds(1), CREATED_AT)
        }
        assertEquals(NonDynamicFitFailureReason.NO_ELIGIBLE_EVIDENCE, failure.reason)
    }

    @Test
    fun `same session replication cannot manufacture independent evidence weight`() {
        val profile = loadedHoldProfile("session_weight")
        val single = buildList {
            for (session in 0 until 5) add(observation(profile, session, load = 22.0 + session, duration = 30.0))
        }
        val replicated = buildList {
            for (session in 0 until 5) {
                add(observation(profile, session, load = 22.0 + session, duration = 30.0, ordinal = 0))
                add(observation(profile, session, load = 22.0 + session, duration = 30.0, ordinal = 1))
            }
        }
        val one = fit(profile, single)
        val two = fit(profile, replicated)
        assertEquals(5, one.support.effectiveIndependentSessionCount)
        assertEquals(5, two.support.effectiveIndependentSessionCount)
        assertEquals(5, one.support.observationCount)
        assertEquals(10, two.support.observationCount)
        assertSummaryClose(requireNotNull(one.frontierAtReference.summary), requireNotNull(two.frontierAtReference.summary))
        assertSummaryClose(requireNotNull(one.slope).summary, requireNotNull(two.slope).summary)
        assertSummaryClose(one.trajectory.summary, two.trajectory.summary)
    }

    @Test
    fun `one low successful demonstration does not catastrophically rewrite frontier`() {
        val profile = loadedHoldProfile("low_outlier")
        val baselineEvidence = buildList {
            for (session in 0 until 6) add(observation(profile, session, load = 25.0 + session, duration = 30.0))
        }
        val baseline = fit(profile, baselineEvidence)
        val withLowDemonstration = fit(
            profile,
            baselineEvidence + observation(profile, 5, load = 5.0, duration = 30.0, ordinal = 1),
        )
        val before = baseline.frontierAtReference.summary!!.p50
        val after = withLowDemonstration.frontierAtReference.summary!!.p50
        assertTrue(after >= before * 0.75, "lower-bound outlier moved frontier from $before to $after")
    }

    @Test
    fun `unsupported far query fails closed with typed numerical state`() {
        val profile = loadedHoldProfile("numerical_guard")
        val fit = fit(
            profile,
            listOf(
                observation(profile, 0, load = 30.0, duration = 20.0),
                observation(profile, 1, load = 25.0, duration = 30.0),
                observation(profile, 2, load = 20.0, duration = 45.0),
                observation(profile, 3, load = 18.0, duration = 60.0),
            ),
        )
        val failure = assertFailsWith<NonDynamicCapabilityFitException> {
            NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.loadedHold)
                .predict(fit, LoadedHoldCapabilityQuery(1e-12))
        }
        assertEquals(NonDynamicFitFailureReason.NON_FINITE_POSTERIOR, failure.reason)
    }

    @Test
    fun `warmup and wrong laterality exclusions remain diagnostic rather than silently pooled`() {
        val profile = NonDynamicProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("epv_adv_unilateral"),
            executionProfileId = ExecutionProfileId("ep_adv_unilateral"),
            metricFamily = MetricFamily.LOADED_HOLD,
            resistanceModel = ResistanceModel("external-v1", ResistanceSemantics.EXTERNAL, 0.0, 1.0, 0.0),
            entryBasis = EntryBasis.TOTAL,
            lateralityMode = LateralityMode.UNILATERAL,
        )
        val warmup = observation(profile, 0, load = 15.0, duration = 20.0, side = Laterality.LEFT, source = "native", warmUp = true)
        val right = observation(profile, 0, load = 20.0, duration = 30.0, side = Laterality.RIGHT, source = "native")
        val projection = NonDynamicCapabilityEvidenceProjector.project(profile, Laterality.LEFT, listOf(warmup, right))
        assertTrue(projection.evidence.isEmpty())
        assertEquals(
            setOf(NonDynamicExclusionReason.WARM_UP_EXCLUDED, NonDynamicExclusionReason.LATERALITY_INCOMPATIBLE),
            projection.exclusions.map { it.reason }.toSet(),
        )
    }

    private fun assertSummaryClose(
        left: dev.kian.mymettle.domain.inference.PosteriorSummary,
        right: dev.kian.mymettle.domain.inference.PosteriorSummary,
    ) {
        listOf(
            left.p05 to right.p05,
            left.p50 to right.p50,
            left.p95 to right.p95,
            left.posteriorVariance to right.posteriorVariance,
        ).forEach { (a, b) ->
            assertTrue(
                kotlin.math.abs(a - b) <= 1e-10 * kotlin.math.max(1.0, kotlin.math.max(kotlin.math.abs(a), kotlin.math.abs(b))),
                "same-session replication changed posterior: $a vs $b",
            )
        }
    }

    private fun fit(profile: NonDynamicProfileSemantics, evidence: List<CompletedSetEvidence>) =
        NonDynamicCapabilityEvidenceProjector.project(profile, Laterality.UNKNOWN, evidence).let { projection ->
            NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.loadedHold)
                .fit(projection, projection.evidence.maxOf { it.completedAt }, CREATED_AT)
        }

    private fun loadedHoldProfile(id: String) = NonDynamicProfileSemantics(
        executionProfileVersionId = ExecutionProfileVersionId("epv_adv_$id"),
        executionProfileId = ExecutionProfileId("ep_adv_$id"),
        metricFamily = MetricFamily.LOADED_HOLD,
        resistanceModel = ResistanceModel("external-v1", ResistanceSemantics.EXTERNAL, 0.0, 1.0, 0.0),
        entryBasis = EntryBasis.TOTAL,
        lateralityMode = LateralityMode.UNKNOWN,
    )

    private fun observation(
        profile: NonDynamicProfileSemantics,
        session: Int,
        load: Double,
        duration: Double,
        ordinal: Int = 0,
        side: Laterality = Laterality.UNKNOWN,
        source: String = "corrected_lite_import",
        warmUp: Boolean = false,
    ): CompletedSetEvidence {
        val suffix = "${profile.executionProfileVersionId.value}_${session}_$ordinal"
        return CompletedSetEvidence(
            setRecordId = "set_$suffix",
            observationId = "obs_$suffix",
            sessionExerciseId = "se_$suffix",
            executionProfileVersionId = profile.executionProfileVersionId,
            metricFamily = profile.metricFamily,
            laterality = side,
            completedAt = BASE.plusSeconds(session * 86_400L + ordinal),
            metricValues = listOf(
                PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(load, UnitId.KILOGRAM)),
                PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(duration, UnitId.SECOND)),
            ),
            bodyMassContextKg = null,
            warmUp = warmUp,
            kind = "work",
            observationSource = source,
            sessionId = "session_${profile.executionProfileVersionId.value}_$session",
        )
    }

    private companion object {
        val BASE: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val CREATED_AT: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
