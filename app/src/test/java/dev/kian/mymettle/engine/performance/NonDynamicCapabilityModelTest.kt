package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DurationOnlyCapabilityQuery
import dev.kian.mymettle.domain.inference.DurationOnlyEvidence
import dev.kian.mymettle.domain.inference.LoadedHoldCapabilityQuery
import dev.kian.mymettle.domain.inference.LoadedHoldEvidence
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import dev.kian.mymettle.domain.inference.NonDynamicExclusionReason
import dev.kian.mymettle.domain.inference.NonDynamicFitFailureReason
import dev.kian.mymettle.domain.inference.NonDynamicProfileSemantics
import dev.kian.mymettle.domain.inference.RepeatedContractionCapabilityQuery
import dev.kian.mymettle.domain.inference.RepeatedContractionEvidence
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.NonDynamicPosteriorFidelity
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NonDynamicCapabilityModelTest {
    @Test
    fun `duration only keeps seconds as duration and never invents kilograms`() {
        val profile = profile(MetricFamily.DURATION_ONLY)
        val evidence = listOf(observation(profile, 0, duration = 40.0))
        val projection = NonDynamicCapabilityEvidenceProjector.project(profile, Laterality.UNKNOWN, evidence)
        assertEquals(1, projection.evidence.size)
        assertIs<DurationOnlyEvidence>(projection.evidence.single())
        assertEquals(null, projection.referenceCoordinate)
        assertEquals(null, projection.resistanceDomainKg)

        val contaminated = NonDynamicCapabilityEvidenceProjector.project(
            profile,
            Laterality.UNKNOWN,
            listOf(observation(profile, 0, duration = 40.0, load = 20.0)),
        )
        assertTrue(contaminated.evidence.isEmpty())
        assertEquals(NonDynamicExclusionReason.UNSUPPORTED_METRIC_COMBINATION, contaminated.exclusions.single().reason)
    }

    @Test
    fun `family projector never reclassifies loaded hold or repeated contraction as dynamic resistance`() {
        val hold = profile(MetricFamily.LOADED_HOLD)
        val holdProjection = NonDynamicCapabilityEvidenceProjector.project(
            hold,
            Laterality.UNKNOWN,
            listOf(observation(hold, 0, duration = 30.0, load = 25.0)),
        )
        assertIs<LoadedHoldEvidence>(holdProjection.evidence.single())
        assertEquals(30.0, holdProjection.referenceCoordinate)

        val repeated = profile(MetricFamily.REPEATED_CONTRACTION)
        val repeatedProjection = NonDynamicCapabilityEvidenceProjector.project(
            repeated,
            Laterality.UNKNOWN,
            listOf(observation(repeated, 0, load = 15.0, cycles = 12)),
        )
        assertIs<RepeatedContractionEvidence>(repeatedProjection.evidence.single())
        assertEquals(12.0, repeatedProjection.referenceCoordinate)
    }

    @Test
    fun `laterality streams are exact and unknown remains unknown`() {
        val unilateral = profile(MetricFamily.LOADED_HOLD, LateralityMode.UNILATERAL)
        val left = observation(unilateral, 0, duration = 30.0, load = 20.0, side = Laterality.LEFT, source = "native")
        val right = observation(unilateral, 1, duration = 30.0, load = 22.0, side = Laterality.RIGHT, source = "native")
        assertEquals(listOf(left.observationId), NonDynamicCapabilityEvidenceProjector.project(unilateral, Laterality.LEFT, listOf(left, right)).evidence.map { it.observationId })
        assertEquals(listOf(right.observationId), NonDynamicCapabilityEvidenceProjector.project(unilateral, Laterality.RIGHT, listOf(left, right)).evidence.map { it.observationId })

        val unknown = profile(MetricFamily.DURATION_ONLY, LateralityMode.UNKNOWN)
        val imported = observation(unknown, 0, duration = 40.0, source = "corrected_lite_import")
        val unknownProjection = NonDynamicCapabilityEvidenceProjector.project(unknown, Laterality.UNKNOWN, listOf(imported))
        assertEquals(1, unknownProjection.evidence.size)
        val ineligibleSource = NonDynamicCapabilityEvidenceProjector.project(
            unknown,
            Laterality.UNKNOWN,
            listOf(observation(unknown, 0, duration = 40.0, source = "native")),
        )
        assertTrue(ineligibleSource.evidence.isEmpty())
        assertEquals(NonDynamicExclusionReason.UNKNOWN_LATERALITY_PROVENANCE_INELIGIBLE, ineligibleSource.exclusions.single().reason)
    }

    @Test
    fun `loaded hold longer duration and higher load imply higher demonstrated capability`() {
        val profile = profile(MetricFamily.LOADED_HOLD)
        val short = fitSparse(profile, listOf(observation(profile, 0, duration = 30.0, load = 20.0)))
        val longer = fitSparse(profile, listOf(observation(profile, 0, duration = 60.0, load = 20.0)))
        val heavier = fitSparse(profile, listOf(observation(profile, 0, duration = 30.0, load = 25.0)))
        val solver = NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.loadedHold)
        val query = LoadedHoldCapabilityQuery(30.0)
        assertTrue(solver.predict(longer, query).summary!!.p50 > solver.predict(short, query).summary!!.p50)
        assertTrue(solver.predict(heavier, query).summary!!.p50 > solver.predict(short, query).summary!!.p50)
    }

    @Test
    fun `loaded hold frontier is monotonic in duration and extrapolation widens uncertainty`() {
        val profile = profile(MetricFamily.LOADED_HOLD)
        val data = buildList {
            for (session in 0 until 5) {
                add(observation(profile, session, duration = 20.0, load = 32.0 + session))
                add(observation(profile, session, duration = 45.0, load = 22.0 + session))
            }
        }
        val fit = fitSparse(profile, data)
        val solver = NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.loadedHold)
        val at20 = solver.predict(fit, LoadedHoldCapabilityQuery(20.0)).summary!!
        val at45 = solver.predict(fit, LoadedHoldCapabilityQuery(45.0)).summary!!
        val at120 = solver.predict(fit, LoadedHoldCapabilityQuery(120.0)).summary!!
        assertTrue(at20.p50 > at45.p50)
        assertTrue((at120.p95 - at120.p05) > (at45.p95 - at45.p05))
    }

    @Test
    fun `loaded hold trajectory recovers positive negative and static directions`() {
        val profile = profile(MetricFamily.LOADED_HOLD)
        val positive = fitSparse(profile, trendData(profile, start = 20.0, logStep = 0.10, duration = 30.0))
        val negative = fitSparse(profile, trendData(profile, start = 35.0, logStep = -0.10, duration = 30.0))
        val flat = fitSparse(profile, trendData(profile, start = 25.0, logStep = 0.0, duration = 30.0))
        assertTrue(positive.trajectory.summary.p50 > 0.0)
        assertTrue(negative.trajectory.summary.p50 < 0.0)
        assertTrue(flat.trajectory.summary.p05 <= 0.0 && flat.trajectory.summary.p95 >= 0.0)
    }

    @Test
    fun `duration only longer demonstration raises duration capability without load coordinate`() {
        val profile = profile(MetricFamily.DURATION_ONLY)
        val shorter = fitSparse(profile, listOf(observation(profile, 0, duration = 30.0)))
        val longer = fitSparse(profile, listOf(observation(profile, 0, duration = 60.0)))
        val solver = NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.durationOnly)
        val shortPrediction = solver.predict(shorter, DurationOnlyCapabilityQuery()).summary!!
        val longPrediction = solver.predict(longer, DurationOnlyCapabilityQuery()).summary!!
        assertTrue(longPrediction.p50 > shortPrediction.p50)
        assertEquals(UnitId.SECOND, longer.canonicalUnit)
        assertEquals(null, longer.slope)
        assertEquals(null, longer.referenceCoordinate)
    }

    @Test
    fun `duration only trajectory and future horizon remain statistical not biological`() {
        val profile = profile(MetricFamily.DURATION_ONLY)
        val positive = fitSparse(profile, durationTrendData(profile, 25.0, 0.12))
        val negative = fitSparse(profile, durationTrendData(profile, 70.0, -0.12))
        val solver = NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.durationOnly)
        assertTrue(positive.trajectory.summary.p50 > 0.0)
        assertTrue(negative.trajectory.summary.p50 < 0.0)
        val current = solver.predict(positive, DurationOnlyCapabilityQuery()).summary!!
        val future = solver.predict(positive, DurationOnlyCapabilityQuery(independentSessionOffset = 5)).summary!!
        assertTrue((future.p95 - future.p05) > 0.0)
        assertTrue(future.posteriorVariance >= current.posteriorVariance)
    }

    @Test
    fun `repeated contraction has own load cycle frontier and fixed cadence contract`() {
        val profile = profile(MetricFamily.REPEATED_CONTRACTION)
        val data = buildList {
            for (session in 0 until 5) {
                add(observation(profile, session, load = 24.0 + session, cycles = 8, cadence = 30.0))
                add(observation(profile, session, load = 16.0 + session, cycles = 16, cadence = 30.0, duration = 32.0))
            }
        }
        val fit = fitSparse(profile, data)
        val solver = NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.repeatedContraction)
        assertTrue(solver.predict(fit, RepeatedContractionCapabilityQuery(8)).summary!!.p50 >
            solver.predict(fit, RepeatedContractionCapabilityQuery(16)).summary!!.p50)
        assertEquals(UnitId.KILOGRAM, fit.canonicalUnit)

        val varyingCadence = data + observation(profile, 6, load = 20.0, cycles = 12, cadence = 40.0)
        val projection = NonDynamicCapabilityEvidenceProjector.project(profile, Laterality.UNKNOWN, varyingCadence)
        val failure = assertFailsWith<NonDynamicCapabilityFitException> {
            solver.fit(projection, projection.evidence.maxOf { it.completedAt }, CREATED_AT)
        }
        assertEquals(NonDynamicFitFailureReason.UNSUPPORTED_CONTEXT, failure.reason)
    }

    @Test
    fun `input ordering is deterministic`() {
        val profile = profile(MetricFamily.LOADED_HOLD)
        val data = trendData(profile, 20.0, 0.05, 30.0) + trendData(profile, 18.0, 0.04, 45.0, ordinalOffset = 10)
        val a = fitSparse(profile, data)
        val b = fitSparse(profile, data.reversed())
        assertEquals(a.frontierAtReference.summary, b.frontierAtReference.summary)
        assertEquals(a.trajectory.summary, b.trajectory.summary)
        assertEquals(a.posteriorNodes, b.posteriorNodes)
    }

    @Test
    fun `adaptive sparse remains close to dense on representative 7C cases`() {
        val cases = listOf(
            Triple(profile(MetricFamily.LOADED_HOLD, id = "hold"), NonDynamicCapabilityV1.loadedHold, "hold"),
            Triple(profile(MetricFamily.DURATION_ONLY, id = "duration"), NonDynamicCapabilityV1.durationOnly, "duration"),
            Triple(profile(MetricFamily.REPEATED_CONTRACTION, id = "repeat"), NonDynamicCapabilityV1.repeatedContraction, "repeat"),
        )
        cases.forEach { (profile, config, label) ->
            val data = when (profile.metricFamily) {
                MetricFamily.LOADED_HOLD -> trendData(profile, 22.0, 0.06, 30.0) + trendData(profile, 15.0, 0.06, 60.0, ordinalOffset = 10)
                MetricFamily.DURATION_ONLY -> durationTrendData(profile, 30.0, 0.08)
                MetricFamily.REPEATED_CONTRACTION -> buildList {
                    for (session in 0 until 6) {
                        add(observation(profile, session, load = 25.0 * exp(0.04 * session), cycles = 8, cadence = 30.0))
                        add(observation(profile, session, load = 17.0 * exp(0.04 * session), cycles = 16, cadence = 30.0, ordinal = 1))
                    }
                }
                else -> error("unexpected")
            }
            val projection = NonDynamicCapabilityEvidenceProjector.project(profile, Laterality.UNKNOWN, data)
            val horizon = projection.evidence.maxOf { it.completedAt }
            val denseSolver = NonDynamicDenseReferenceSolver(config)
            val sparseSolver = NonDynamicAdaptiveSparseSolver(config)
            val dense = denseSolver.fit(projection, horizon, CREATED_AT)
            val sparse = sparseSolver.fit(projection, horizon, CREATED_AT)
            val queries = when (profile.metricFamily) {
                MetricFamily.LOADED_HOLD -> listOf("in" to LoadedHoldCapabilityQuery(30.0), "tail" to LoadedHoldCapabilityQuery(90.0))
                MetricFamily.DURATION_ONLY -> listOf("current" to DurationOnlyCapabilityQuery(), "future" to DurationOnlyCapabilityQuery(3))
                MetricFamily.REPEATED_CONTRACTION -> listOf("in" to RepeatedContractionCapabilityQuery(12), "tail" to RepeatedContractionCapabilityQuery(30))
                else -> error("unexpected")
            }
            val fidelity = NonDynamicPosteriorFidelity.compare(denseSolver, dense, sparseSolver, sparse, queries)
            assertTrue(fidelity.queries.all { it.p50RelativeError < 0.10 }, "$label sparse median fidelity $fidelity")
            assertTrue(fidelity.maximumQueryTailRelativeError < 0.30, "$label sparse tail fidelity $fidelity")
            assertTrue(fidelity.positiveTrajectoryProbabilityAbsoluteError < 0.15, "$label sparse trajectory fidelity $fidelity")
            assertTrue(sparse.retainedBaseNodeCount <= dense.retainedBaseNodeCount)
        }
    }

    @Test
    fun `sparse history remains explicitly broad and successful observation is lower bound evidence`() {
        val profile = profile(MetricFamily.LOADED_HOLD)
        val fit = fitSparse(profile, listOf(observation(profile, 0, duration = 30.0, load = 20.0)))
        val summary = fit.frontierAtReference.summary!!
        assertTrue(summary.p95 > summary.p05)
        assertTrue(summary.p50 >= 20.0)
        assertTrue("lower_bound_capability_not_action_policy" in fit.warnings)
    }

    private fun fitSparse(profile: NonDynamicProfileSemantics, evidence: List<CompletedSetEvidence>) =
        NonDynamicCapabilityEvidenceProjector.project(profile, Laterality.UNKNOWN, evidence).let { projection ->
            assertTrue(projection.evidence.isNotEmpty())
            NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.configFor(profile.metricFamily))
                .fit(projection, projection.evidence.maxOf { it.completedAt }, CREATED_AT)
        }

    private fun trendData(
        profile: NonDynamicProfileSemantics,
        start: Double,
        logStep: Double,
        duration: Double,
        ordinalOffset: Int = 0,
    ): List<CompletedSetEvidence> = List(6) { session ->
        observation(
            profile,
            session,
            duration = duration,
            load = start * exp(logStep * session),
            ordinal = ordinalOffset,
        )
    }

    private fun durationTrendData(profile: NonDynamicProfileSemantics, start: Double, logStep: Double): List<CompletedSetEvidence> =
        List(6) { session -> observation(profile, session, duration = start * exp(logStep * session)) }

    private fun profile(
        family: MetricFamily,
        lateralityMode: LateralityMode = LateralityMode.UNKNOWN,
        id: String = family.storageValue,
    ): NonDynamicProfileSemantics = NonDynamicProfileSemantics(
        executionProfileVersionId = ExecutionProfileVersionId("epv_$id"),
        executionProfileId = ExecutionProfileId("ep_$id"),
        metricFamily = family,
        resistanceModel = when (family) {
            MetricFamily.DURATION_ONLY -> ResistanceModel("none-v1", ResistanceSemantics.NONE, 0.0, 0.0, 0.0)
            else -> ResistanceModel("external-v1", ResistanceSemantics.EXTERNAL, 0.0, 1.0, 0.0)
        },
        entryBasis = EntryBasis.TOTAL,
        lateralityMode = lateralityMode,
    )

    private fun observation(
        profile: NonDynamicProfileSemantics,
        session: Int,
        duration: Double? = null,
        load: Double? = null,
        cycles: Int? = null,
        cadence: Double? = null,
        side: Laterality = Laterality.UNKNOWN,
        source: String = "corrected_lite_import",
        ordinal: Int = 0,
    ): CompletedSetEvidence {
        val values = buildList {
            if (load != null) add(metric(PerformanceMetric.EXTERNAL_LOAD, load, UnitId.KILOGRAM))
            if (cycles != null) add(metric(PerformanceMetric.REPETITIONS, cycles.toDouble(), UnitId.REPETITION))
            if (duration != null) add(metric(PerformanceMetric.DURATION, duration, UnitId.SECOND))
            if (cadence != null) add(metric(PerformanceMetric.CADENCE, cadence, UnitId.EVENTS_PER_MINUTE))
        }
        val suffix = "${profile.executionProfileVersionId.value}_${session}_$ordinal_${values.joinToString("_") { it.metric.storageValue }}"
        return CompletedSetEvidence(
            setRecordId = "set_$suffix",
            observationId = "obs_$suffix",
            sessionExerciseId = "se_$suffix",
            executionProfileVersionId = profile.executionProfileVersionId,
            metricFamily = profile.metricFamily,
            laterality = side,
            completedAt = BASE.plusSeconds(session * 86_400L + ordinal),
            metricValues = values,
            bodyMassContextKg = null,
            warmUp = false,
            kind = "work",
            observationSource = source,
            sessionId = "session_${profile.executionProfileVersionId.value}_$session",
        )
    }

    private fun metric(metric: PerformanceMetric, value: Double, unit: UnitId): PerformanceMetricValue =
        PerformanceMetricValue(metric = metric, entered = Quantity(value, unit))

    companion object {
        private val BASE = Instant.parse("2026-01-01T12:00:00Z")
        private val CREATED_AT = Instant.parse("2026-09-01T00:00:00Z")
    }
}
