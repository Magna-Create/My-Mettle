package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.performance.MetricFamily
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NBio7DSessionEvaluatorTest {
    @Test
    fun `model config identities change when delta or tau changes`() {
        val baseline = NBio7DModelConfigs.definitions(NBio7DConfig(), Instant.EPOCH).associateBy { it.component }
        val changedDelta = NBio7DModelConfigs.definitions(
            NBio7DConfig(dynamicResistanceDeltaLog = 0.10),
            Instant.EPOCH,
        ).associateBy { it.component }
        val changedTau = NBio7DModelConfigs.definitions(NBio7DConfig(tau = 8.0), Instant.EPOCH).associateBy { it.component }
        assertNotEquals(baseline.getValue(InferenceModelComponent.SET_DEMAND).id, changedDelta.getValue(InferenceModelComponent.SET_DEMAND).id)
        assertEquals(baseline.getValue(InferenceModelComponent.EXPOSURE).id, changedDelta.getValue(InferenceModelComponent.EXPOSURE).id)
        assertNotEquals(baseline.getValue(InferenceModelComponent.SESSION_DOSE).id, changedTau.getValue(InferenceModelComponent.SESSION_DOSE).id)
        assertEquals(baseline.getValue(InferenceModelComponent.EFFECTIVE_DOSE).id, changedTau.getValue(InferenceModelComponent.EFFECTIVE_DOSE).id)
    }

    @Test
    fun `session evaluator does not conserve recruitment across muscles`() {
        val result = evaluator().evaluate(
            listOf(
                input(
                    "set1",
                    gaps = listOf(0.01, 0.02),
                    weights = listOf(0.5, 0.5),
                    exposures = listOf(exposure("a", 1.0), exposure("b", 0.7), exposure("c", 0.4)),
                ),
            ),
        )
        assertEquals(3, result.exposureCount)
        assertEquals(listOf(1.0, 0.7, 0.4), result.setResults.single().muscleDoses.map { it.exposure.conservativeExposure })
        assertEquals(3, result.muscleResults.size)
    }

    @Test
    fun `capability changes demand and dose but never exposure`() {
        val exposure = exposure("m", 0.7)
        val near = evaluator().evaluate(listOf(input("near", listOf(0.01, 0.02), listOf(0.5, 0.5), listOf(exposure))))
        val far = evaluator().evaluate(listOf(input("far", listOf(0.20, 0.30), listOf(0.5, 0.5), listOf(exposure))))
        assertEquals(0.7, near.setResults.single().muscleDoses.single().exposure.conservativeExposure)
        assertEquals(0.7, far.setResults.single().muscleDoses.single().exposure.conservativeExposure)
        assertTrue(requireNotNull(near.setResults.single().demand.probabilityAtOrWithinDelta) > requireNotNull(far.setResults.single().demand.probabilityAtOrWithinDelta))
    }

    @Test
    fun `same stream sets share posterior and session result is order invariant`() {
        val a = input("a", listOf(0.01, 0.20), listOf(0.5, 0.5), listOf(exposure("m", 1.0)), stream = "profile-side")
        val b = input("b", listOf(0.02, 0.30), listOf(0.5, 0.5), listOf(exposure("m", 1.0)), stream = "profile-side")
        val forward = evaluator().evaluate(listOf(a, b)).muscleResults.single().dose
        val reverse = evaluator().evaluate(listOf(b, a)).muscleResults.single().dose
        assertEquals(forward.rawSummary, reverse.rawSummary)
        assertEquals(forward.concaveSummary, reverse.concaveSummary)
        assertEquals(1.0, requireNotNull(forward.rawSummary).posteriorVariance, 1e-12)
    }

    @Test
    fun `different profile streams use explicit cross stream independence approximation`() {
        val a = input("a", listOf(0.01, 0.20), listOf(0.5, 0.5), listOf(exposure("m", 1.0)), stream = "profile-a")
        val b = input("b", listOf(0.01, 0.20), listOf(0.5, 0.5), listOf(exposure("m", 1.0)), stream = "profile-b")
        val dose = evaluator().evaluate(listOf(a, b)).muscleResults.single().dose
        assertTrue(dose.crossStreamIndependenceApproximation)
        assertEquals(SessionDoseResolution.FULLY_RESOLVED, dose.resolution)
    }

    @Test
    fun `unresolved demand preserves exposure but makes session subtotal partial`() {
        val resolved = input("resolved", listOf(0.01), listOf(1.0), listOf(exposure("m", 0.7)))
        val unresolved = NBio7DSetInput(
            setObservationId = "unresolved",
            capabilityStreamKey = "profile-side",
            family = MetricFamily.DYNAMIC_RESISTANCE,
            logObservedPerformance = 4.0,
            logFrontierNodes = null,
            inheritedDemandSupport = SetDemandStructuralSupport.UNSUPPORTED,
            exposures = listOf(exposure("m", 0.7)),
        )
        val result = evaluator().evaluate(listOf(resolved, unresolved))
        assertEquals(2, result.exposureCount)
        assertEquals(1, result.effectiveDoseResolvedCount)
        assertEquals(1, result.effectiveDoseUnresolvedCount)
        val session = result.muscleResults.single().dose
        assertEquals(SessionDoseResolution.PARTIALLY_RESOLVED, session.resolution)
        assertEquals(1, session.unresolvedSetCount)
        assertEquals(0.7, requireNotNull(session.rawSummary).p50, 1e-12)
    }

    @Test
    fun `all unresolved demand does not create fake zero session dose`() {
        val result = evaluator().evaluate(
            listOf(
                NBio7DSetInput(
                    "u",
                    "profile-side",
                    MetricFamily.DYNAMIC_RESISTANCE,
                    4.0,
                    null,
                    SetDemandStructuralSupport.UNSUPPORTED,
                    listOf(exposure("m", 1.0)),
                ),
            ),
        )
        assertEquals(SessionDoseResolution.UNRESOLVED, result.muscleResults.single().dose.resolution)
        assertNull(result.muscleResults.single().dose.rawSummary)
    }

    @Test
    fun `7C demand retains both empirical quarantines through dose`() {
        val result = evaluator().evaluate(
            listOf(
                input(
                    "hold",
                    listOf(0.01, 0.02),
                    listOf(0.5, 0.5),
                    listOf(exposure("m", 1.0)),
                    family = MetricFamily.LOADED_HOLD,
                ),
            ),
        )
        val demand = result.setResults.single().demand
        val dose = result.setResults.single().muscleDoses.single()
        assertTrue(SetDemandEmpiricalStatus.EMPIRICAL_CALIBRATION_PENDING in demand.empiricalStatuses)
        assertTrue(SetDemandEmpiricalStatus.EMPIRICAL_ACCURACY_PENDING in demand.empiricalStatuses)
        assertEquals(demand.empiricalStatuses, dose.empiricalStatuses)
    }

    @Test
    fun `7D configs contain no 7E model component`() {
        val components = NBio7DModelConfigs.definitions().map { it.component }.toSet()
        assertEquals(
            setOf(
                InferenceModelComponent.SET_DEMAND,
                InferenceModelComponent.EXPOSURE,
                InferenceModelComponent.EFFECTIVE_DOSE,
                InferenceModelComponent.SESSION_DOSE,
            ),
            components,
        )
        assertFalse(InferenceModelComponent.FATIGUE in components)
        assertFalse(InferenceModelComponent.RECOVERY in components)
        assertFalse(InferenceModelComponent.DEVELOPMENT in components)
        assertFalse(InferenceModelComponent.SKILL in components)
    }

    private fun evaluator() = NBio7DSessionEvaluator(NBio7DConfig())

    private fun input(
        id: String,
        gaps: List<Double>,
        weights: List<Double>,
        exposures: List<MuscleExposure>,
        stream: String = "profile-side",
        family: MetricFamily = MetricFamily.DYNAMIC_RESISTANCE,
    ): NBio7DSetInput = NBio7DSetInput(
        setObservationId = id,
        capabilityStreamKey = stream,
        family = family,
        logObservedPerformance = 0.0,
        logFrontierNodes = gaps.mapIndexed { index, value -> WeightedScalarNode("n$index", value, weights[index]) },
        inheritedDemandSupport = SetDemandStructuralSupport.RESOLVED,
        exposures = exposures,
    )

    private fun exposure(muscle: String, weight: Double) = MuscleExposure(
        muscleSegmentId = muscle,
        side = "bilateral",
        recruitmentWeight = weight,
        historicalRecruitmentProfileVersionId = "recruitment-v1",
    )
}
