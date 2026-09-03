package dev.kian.mymettle.domain.inference

import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TemporalStateV7ETest {
    private val filter = NeutralTemporalStateFilterV1()
    private val start = Instant.parse("2026-01-01T12:00:00Z")

    @Test
    fun `transient expectation halves after configured half life`() {
        val initial = filter.initial(start).copy(transientMean = -0.12)
        val predicted = filter.predictState(initial, start.plusSeconds(3 * 86_400))
        assertEquals(-0.06, predicted.transientMean, 1e-12)
        assertEquals(initial.persistentMean, predicted.persistentMean)
    }

    @Test
    fun `future SessionDose is rejected from pre-session covariate`() {
        assertFailsWith<IllegalArgumentException> {
            RecentDoseCovariateV1.calculate(
                listOf(DatedSessionDose(start.plusSeconds(1), 2.0)),
                start,
            )
        }
    }

    @Test
    fun `stable state remains centred after repeated neutral observations`() {
        var state = filter.initial(start)
        repeat(12) { index ->
            state = filter.update(
                state,
                start.plusSeconds((index + 1L) * 86_400),
                observedLogResidual = 0.0,
                layer = TemporalCandidateLayer.TEMPORAL_BASE,
            ).posterior
        }
        assertTrue(abs(state.persistentMean) < 1e-12)
        assertTrue(abs(state.transientMean) < 1e-12)
        assertTrue(state.covariance.pp < filter.config.persistentPriorVariance)
    }

    @Test
    fun `slow corroborated improvement moves persistent component`() {
        var state = filter.initial(start)
        repeat(16) { index ->
            val residual = index * 0.006
            state = filter.update(
                state,
                start.plusSeconds((index + 1L) * 3 * 86_400),
                observedLogResidual = residual,
                layer = TemporalCandidateLayer.TEMPORAL_BASE,
            ).posterior
        }
        assertTrue(state.persistentMean > 0.025)
    }

    @Test
    fun `one severe anomaly is robustly downweighted after stable history`() {
        var state = filter.initial(start)
        repeat(10) { index ->
            state = filter.update(
                state,
                start.plusSeconds((index + 1L) * 86_400),
                0.0,
                TemporalCandidateLayer.TEMPORAL_BASE,
            ).posterior
        }
        val beforePersistent = state.persistentMean
        val update = filter.update(
            state,
            start.plusSeconds(11 * 86_400),
            -1.0,
            TemporalCandidateLayer.TEMPORAL_BASE,
        )
        assertTrue(update.robustWeight < 1.0)
        assertTrue(abs(update.posterior.persistentMean - beforePersistent) < 0.05)
        assertTrue(update.posterior.transientMean < 0.0)
    }

    @Test
    fun `multi-session suppression is transient and mean reverts without new observations`() {
        var state = filter.initial(start)
        repeat(8) { index ->
            state = filter.update(state, start.plusSeconds((index + 1L) * 86_400), 0.0, TemporalCandidateLayer.TEMPORAL_BASE).posterior
        }
        repeat(3) { index ->
            state = filter.update(state, start.plusSeconds((index + 9L) * 86_400), -0.12, TemporalCandidateLayer.TEMPORAL_BASE).posterior
        }
        val suppressed = state.transientMean
        val recovered = filter.predictState(state, state.horizon.plusSeconds(12 * 86_400))
        assertTrue(suppressed < 0.0)
        assertTrue(abs(recovered.transientMean) < abs(suppressed) / 8.0 + 1e-9)
        assertEquals(state.persistentMean, recovered.persistentMean)
    }

    @Test
    fun `dose coefficient starts neutral and learns only from prior-dose future residual pairs`() {
        var state = filter.initial(start)
        assertEquals(0.0, state.doseCoefficientMean)
        repeat(12) { index ->
            val dose = if (index % 2 == 0) 1.0 else 0.15
            val residual = -0.05 * dose
            state = filter.update(
                state,
                start.plusSeconds((index + 1L) * 2 * 86_400),
                residual,
                TemporalCandidateLayer.DOSE_TEMPORAL,
                standardisedRecentDose = dose,
            ).posterior
        }
        assertTrue(state.doseCoefficientMean < 0.0)
        assertTrue(state.covariance.dd < filter.config.doseCoefficientPriorVariance)
    }

    @Test
    fun `missing dose keeps typed availability false rather than pretending measured zero`() {
        val (_, prediction) = filter.predict(
            filter.initial(start),
            start.plusSeconds(86_400),
            TemporalCandidateLayer.DOSE_TEMPORAL,
            standardisedRecentDose = null,
        )
        assertTrue(!prediction.doseAvailable)
        assertEquals(0.0, prediction.doseContributionMean)
    }

    @Test
    fun `context layer is separate from same base state`() {
        val state = filter.initial(start)
        val at = start.plusSeconds(86_400)
        val (_, base) = filter.predict(state, at, TemporalCandidateLayer.TEMPORAL_BASE)
        val (_, context) = filter.predict(
            state,
            at,
            TemporalCandidateLayer.CONTEXT_TEMPORAL,
            standardisedRecentDose = 0.0,
            context = TemporalContextAdjustment(locationMean = -0.08, locationVariance = 0.02),
        )
        assertEquals(base.mean - 0.08, context.mean, 1e-12)
        assertTrue(context.variance > base.variance)
        assertEquals(state, filter.initial(start))
    }

    @Test
    fun `prediction chronology never names future evidence`() {
        val state = filter.initial(start)
        val at = start.plusSeconds(86_400)
        val (_, prediction) = filter.predict(state, at, TemporalCandidateLayer.TEMPORAL_BASE)
        assertTrue(requireNotNull(prediction.evidenceThrough).isBefore(prediction.predictedAt))
        assertFailsWith<IllegalArgumentException> { filter.predictState(state, start.minusSeconds(1)) }
    }

    @Test
    fun `slow corroborated decline moves persistent component without biological naming`() {
        var state = filter.initial(start)
        repeat(16) { index ->
            state = filter.update(
                state,
                start.plusSeconds((index + 1L) * 3 * 86_400),
                observedLogResidual = -index * 0.006,
                layer = TemporalCandidateLayer.TEMPORAL_BASE,
            ).posterior
        }
        assertTrue(state.persistentMean < -0.025)
    }

    @Test
    fun `symmetric observation noise does not become confident directional state`() {
        var state = filter.initial(start)
        repeat(24) { index ->
            val residual = if (index % 2 == 0) -0.10 else 0.10
            state = filter.update(state, start.plusSeconds((index + 1L) * 86_400), residual, TemporalCandidateLayer.TEMPORAL_BASE).posterior
        }
        assertTrue(abs(state.persistentMean) < 0.025)
        assertTrue(abs(state.transientMean) < 0.075)
    }

    @Test
    fun `sparse history retains broad uncertainty`() {
        val initial = filter.initial(start)
        val one = filter.update(initial, start.plusSeconds(86_400), 0.03, TemporalCandidateLayer.TEMPORAL_BASE).posterior
        assertEquals(1, one.observationCount)
        assertTrue(one.covariance.pp > 0.005)
        assertTrue(one.covariance.tt > 0.001)
    }

    @Test
    fun `persistent progression can coexist with acute transient suppression`() {
        var state = filter.initial(start)
        repeat(18) { index ->
            state = filter.update(
                state,
                start.plusSeconds((index + 1L) * 3 * 86_400),
                index * 0.005,
                TemporalCandidateLayer.TEMPORAL_BASE,
            ).posterior
        }
        val persistentBefore = state.persistentMean
        repeat(2) { index ->
            state = filter.update(state, state.horizon.plusSeconds(86_400), persistentBefore - 0.12, TemporalCandidateLayer.TEMPORAL_BASE).posterior
        }
        assertTrue(state.persistentMean > 0.0)
        assertTrue(state.transientMean < 0.0)
    }

    @Test
    fun `dose and context competing explanations retain predictive uncertainty`() {
        val state = filter.initial(start).copy(doseCoefficientMean = -0.04)
        val (_, base) = filter.predict(state, start.plusSeconds(86_400), TemporalCandidateLayer.TEMPORAL_BASE)
        val (_, competing) = filter.predict(
            state,
            start.plusSeconds(86_400),
            TemporalCandidateLayer.CONTEXT_TEMPORAL,
            standardisedRecentDose = 1.0,
            context = TemporalContextAdjustment(locationMean = 0.04, locationVariance = 0.03),
        )
        assertEquals(base.mean, competing.mean, 1e-12)
        assertTrue(competing.variance > base.variance)
    }

    @Test
    fun `semantic regime boundary uses independent state rather than leaking old regime`() {
        val oldRegime = filter.update(filter.initial(start), start.plusSeconds(86_400), 0.2, TemporalCandidateLayer.TEMPORAL_BASE).posterior
        val newRegime = filter.initial(start.plusSeconds(2 * 86_400))
        assertTrue(oldRegime.persistentMean > 0.0)
        assertEquals(0.0, newRegime.persistentMean)
        assertEquals(0, newRegime.observationCount)
    }

    @Test
    fun `no evidence produces prior-only candidate rather than fabricated state`() {
        val state = filter.initial(start)
        val (_, prediction) = filter.predict(state, start, TemporalCandidateLayer.CONTEXT_TEMPORAL)
        assertEquals(0, state.observationCount)
        assertTrue(!prediction.doseAvailable)
        assertEquals(0.0, prediction.mean)
        assertTrue(prediction.variance >= filter.config.observationVariance)
    }
}
