package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.developer.DynamicResistancePreparationDiagnostics
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceCapabilityStateContract
import dev.kian.mymettle.domain.inference.DynamicResistanceExclusionReason
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV1Contract
import dev.kian.mymettle.domain.inference.InferenceModelComponent
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.PerformanceObservation
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicResistanceCapabilityContractTest {
    @Test
    fun `valid calibrated external load and reps is eligible`() {
        val projection = project(profile(), evidence(load = 60.0, reps = 8))
        assertEquals(1, projection.evidence.size)
        assertTrue(projection.exclusions.isEmpty())
        assertEquals(60.0, projection.evidence.single().resistance.value)
    }

    @Test
    fun `duration hold power cardio and ordinal families are not dynamic frontier evidence`() {
        val cases = listOf(
            MetricFamily.DURATION_ONLY to listOf(metric(PerformanceMetric.DURATION, 30.0)),
            MetricFamily.LOADED_HOLD to listOf(metric(PerformanceMetric.EXTERNAL_LOAD, 40.0), metric(PerformanceMetric.DURATION, 30.0)),
            MetricFamily.POWER_DURATION to listOf(metric(PerformanceMetric.POWER, 250.0), metric(PerformanceMetric.DURATION, 60.0)),
            MetricFamily.SPEED_DURATION to listOf(metric(PerformanceMetric.SPEED, 3.0), metric(PerformanceMetric.DURATION, 60.0)),
            MetricFamily.DEVICE_ORDINAL to listOf(metric(PerformanceMetric.MACHINE_LEVEL, 8.0), metric(PerformanceMetric.REPETITIONS, 10.0)),
        )
        cases.forEachIndexed { index, (family, values) ->
            val target = profile(family = family, semantics = if (family == MetricFamily.DEVICE_ORDINAL) ResistanceSemantics.DEVICE_ORDINAL else ResistanceSemantics.EXTERNAL)
            val candidate = evidence(id = "case_$index", family = family, valuesOverride = values)
            val projection = project(target, candidate)
            assertTrue(projection.evidence.isEmpty(), family.storageValue)
            assertEquals(DynamicResistanceExclusionReason.METRIC_FAMILY_INELIGIBLE, projection.exclusions.single().reason)
        }
    }

    @Test
    fun `zero reps and non-positive resolved resistance are rejected without epsilon clamp`() {
        val zeroReps = project(profile(), evidence(load = 60.0, reps = 0))
        assertEquals(DynamicResistanceExclusionReason.NON_POSITIVE_REPETITIONS, zeroReps.exclusions.single().reason)

        val zeroLoad = project(profile(), evidence(load = 0.0, reps = 8))
        assertEquals(DynamicResistanceExclusionReason.NON_POSITIVE_RESISTANCE_COORDINATE, zeroLoad.exclusions.single().reason)
        assertTrue(zeroLoad.evidence.isEmpty())
    }

    @Test
    fun `warm-up is explicitly excluded under v1 policy`() {
        val projection = project(profile(), evidence(load = 40.0, reps = 10, warmUp = true))
        assertEquals(DynamicResistanceExclusionReason.WARM_UP_EXCLUDED, projection.exclusions.single().reason)
        assertEquals("exclude", projection.policy.warmUpPolicy.storageValue)
    }

    @Test
    fun `superseded observation selector retains only latest factual correction`() {
        val a = observation("A", load = 50.0)
        val b = observation("B", load = 55.0, supersedes = a.id)
        val current = CurrentPerformanceObservationSelector.current(listOf(a, b))
        assertEquals(listOf("obs_B"), current.map { it.id })
        assertEquals(55.0, current.single().values.first { it.metric == PerformanceMetric.EXTERNAL_LOAD }.canonical.value)
    }

    @Test
    fun `profile version A cannot enter B and left cannot enter right`() {
        val evidenceA = evidence(profileVersionId = "profile:v1", laterality = Laterality.LEFT)
        val profileB = profile(versionId = "profile:v2", mode = LateralityMode.UNILATERAL)
        val wrongVersion = DynamicResistanceEvidenceProjector.project(profileB, Laterality.LEFT, listOf(evidenceA))
        assertEquals(DynamicResistanceExclusionReason.PROFILE_VERSION_MISMATCH, wrongVersion.exclusions.single().reason)

        val profileA = profile(versionId = "profile:v1", mode = LateralityMode.UNILATERAL)
        val wrongSide = DynamicResistanceEvidenceProjector.project(profileA, Laterality.RIGHT, listOf(evidenceA))
        assertEquals(DynamicResistanceExclusionReason.LATERALITY_INCOMPATIBLE, wrongSide.exclusions.single().reason)
    }

    @Test
    fun `profile-local evidence remains keyed to exact version and side`() {
        val p = profile(versionId = "row:v4", profileId = "row")
        val projection = project(p, evidence(profileVersionId = "row:v4"))
        val item = projection.evidence.single()
        assertEquals(ExecutionProfileVersionId("row:v4"), item.executionProfileVersionId)
        assertEquals(Laterality.BILATERAL, item.side)
    }

    @Test
    fun `equivalent kg and lb resolve to equal coordinates while raw units remain untouched`() {
        val poundsFor60Kg = 60.0 * 2.2046226218487757
        val kgEvidence = evidence(id = "kg", load = 60.0, loadUnit = UnitId.KILOGRAM)
        val lbEvidence = evidence(id = "lb", load = poundsFor60Kg, loadUnit = UnitId.POUND)
        val p = profile()
        val kg = project(p, kgEvidence).evidence.single()
        val lb = project(p, lbEvidence).evidence.single()

        assertEquals(kg.resistance.value, lb.resistance.value, 1e-9)
        assertEquals(UnitId.KILOGRAM, kgEvidence.metric(PerformanceMetric.EXTERNAL_LOAD)!!.entered.unit)
        assertEquals(UnitId.POUND, lbEvidence.metric(PerformanceMetric.EXTERNAL_LOAD)!!.entered.unit)
        assertEquals(poundsFor60Kg, lbEvidence.metric(PerformanceMetric.EXTERNAL_LOAD)!!.entered.value)
    }

    @Test
    fun `per hand semantics are preserved rather than silently totalised`() {
        val p = profile(entryBasis = EntryBasis.PER_HAND)
        val projected = project(p, evidence(load = 20.0)).evidence.single()
        assertEquals(20.0, projected.resistance.value)
        assertEquals(EntryBasis.PER_HAND, projected.resistance.entryBasis)
    }

    @Test
    fun `assistance direction is monotonic when explicit profile mapping is available`() {
        val p = profile(
            family = MetricFamily.BODYWEIGHT_RESISTANCE,
            semantics = ResistanceSemantics.ASSISTANCE,
            bodyweightCoefficient = 1.0,
            externalLoadCoefficient = 0.0,
            assistanceCoefficient = 1.0,
        )
        val easier = project(p, evidence(family = MetricFamily.BODYWEIGHT_RESISTANCE, assistance = 30.0, bodyMass = 80.0)).evidence.single()
        val harder = project(p, evidence(family = MetricFamily.BODYWEIGHT_RESISTANCE, assistance = 20.0, bodyMass = 80.0)).evidence.single()
        assertTrue(harder.resistance.value > easier.resistance.value)
    }

    @Test
    fun `unsupported assistance mapping is unresolved instead of fake resistance`() {
        val p = profile(
            family = MetricFamily.BODYWEIGHT_RESISTANCE,
            semantics = ResistanceSemantics.ASSISTANCE,
            bodyweightCoefficient = 0.0,
            externalLoadCoefficient = 0.0,
            assistanceCoefficient = 1.0,
        )
        val projection = project(p, evidence(family = MetricFamily.BODYWEIGHT_RESISTANCE, assistance = 20.0, bodyMass = 80.0))
        assertTrue(projection.evidence.isEmpty())
        assertEquals(DynamicResistanceExclusionReason.INCONSISTENT_RESISTANCE_MODEL, projection.exclusions.single().reason)
    }

    @Test
    fun `bodyweight coordinate requires explicit coefficient and never assumes full body mass`() {
        val unsupported = profile(
            family = MetricFamily.BODYWEIGHT_RESISTANCE,
            semantics = ResistanceSemantics.BODYWEIGHT,
            bodyweightCoefficient = 0.0,
            externalLoadCoefficient = 0.0,
        )
        val unresolved = project(unsupported, evidence(family = MetricFamily.BODYWEIGHT_RESISTANCE, bodyMass = 80.0, noLoad = true))
        assertEquals(DynamicResistanceExclusionReason.INCONSISTENT_RESISTANCE_MODEL, unresolved.exclusions.single().reason)

        val explicit = unsupported.copy(
            resistanceModel = ResistanceModel("bodyweight-65pct-v1", ResistanceSemantics.BODYWEIGHT, 0.65, 0.0, 0.0),
        )
        val resolved = project(explicit, evidence(family = MetricFamily.BODYWEIGHT_RESISTANCE, bodyMass = 80.0, noLoad = true)).evidence.single()
        assertEquals(52.0, resolved.resistance.value, 1e-9)
    }

    @Test
    fun `bodyweight plus external requires explicit profile coefficients`() {
        val p = profile(
            family = MetricFamily.BODYWEIGHT_RESISTANCE,
            semantics = ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL,
            bodyweightCoefficient = 0.7,
            externalLoadCoefficient = 1.0,
        )
        val resolved = project(p, evidence(family = MetricFamily.BODYWEIGHT_RESISTANCE, bodyMass = 80.0, load = 20.0)).evidence.single()
        assertEquals(76.0, resolved.resistance.value, 1e-9)
    }

    @Test
    fun `device ordinal can never become physical kilograms`() {
        val p = profile(family = MetricFamily.DEVICE_ORDINAL, semantics = ResistanceSemantics.DEVICE_ORDINAL)
        val candidate = evidence(
            family = MetricFamily.DEVICE_ORDINAL,
            valuesOverride = listOf(metric(PerformanceMetric.MACHINE_LEVEL, 8.0), metric(PerformanceMetric.REPETITIONS, 10.0)),
        )
        val projection = project(p, candidate)
        assertTrue(projection.evidence.isEmpty())
        assertFalse(candidate.metric(PerformanceMetric.MACHINE_LEVEL)!!.canonical.unit == UnitId.KILOGRAM)
    }

    @Test
    fun `log resistance inverse round trip is stable and strictly positive`() {
        val coordinate = project(profile(), evidence(load = 62.5)).evidence.single().resistance
        val logged = DynamicResistanceLogCoordinates.logResistance(coordinate)
        val restored = DynamicResistanceLogCoordinates.resistanceFromLog(logged, coordinate)
        assertTrue(restored.value > 0.0)
        assertEquals(coordinate.value, restored.value, 1e-12)
    }

    @Test
    fun `reference rep selection is deterministic and inside observed domain`() {
        val p = profile()
        val observations = listOf(
            evidence(id = "one", reps = 5),
            evidence(id = "two", reps = 8),
            evidence(id = "three", reps = 12),
            evidence(id = "four", reps = 20),
        )
        val first = DynamicResistanceEvidenceProjector.project(p, Laterality.BILATERAL, observations)
        val second = DynamicResistanceEvidenceProjector.project(p, Laterality.BILATERAL, observations.reversed())
        assertEquals(8.0, first.referenceRepetitions)
        assertEquals(first.referenceRepetitions, second.referenceRepetitions)
        assertTrue(first.referenceRepetitions!! in 5.0..20.0)
    }

    @Test
    fun `centred rep transform is zero at reference reps`() {
        assertEquals(0.0, DynamicResistanceLogCoordinates.centredLogRep(8.0, 8.0), 1e-12)
    }

    @Test
    fun `one eligible set projects successfully without pretending slope is identified`() {
        val projection = project(profile(), evidence(reps = 9))
        assertEquals(1, projection.evidence.size)
        assertEquals(9.0, projection.referenceRepetitions)
        assertEquals(9..9, projection.repDomain)
    }

    @Test
    fun `same rep sets keep local domain while multiple zones preserve wider domain`() {
        val p = profile()
        val same = DynamicResistanceEvidenceProjector.project(
            p,
            Laterality.BILATERAL,
            listOf(evidence(id = "s1", reps = 8), evidence(id = "s2", reps = 8), evidence(id = "s3", reps = 8)),
        )
        assertEquals(8..8, same.repDomain)

        val zones = DynamicResistanceEvidenceProjector.project(
            p,
            Laterality.BILATERAL,
            listOf(evidence(id = "z1", reps = 4), evidence(id = "z2", reps = 10), evidence(id = "z3", reps = 20)),
        )
        assertEquals(4..20, zones.repDomain)
    }

    @Test
    fun `canonical capability state is reference-rep frontier and never e1RM`() {
        val contract = DynamicResistanceCapabilityStateContract(
            executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
            side = Laterality.BILATERAL,
            referenceRepetitions = 8.0,
        )
        assertTrue(contract.semanticDefinition.contains("reference rep", ignoreCase = true))
        assertFalse(contract.semanticDefinition.contains("e1rm", ignoreCase = true))
        assertEquals(UnitId.KILOGRAM, contract.canonicalUnit)
    }

    @Test
    fun `context consumption is explicitly none and not an evidence input`() {
        assertTrue(DynamicResistanceV1Contract.contextPolicy.allowedTagIds.isEmpty())
        val p = profile()
        val candidate = evidence()
        val before = project(p, candidate)
        // 7A.5 annotations can be added/deleted independently; the 7B.1 projector has no context argument.
        val after = project(p, candidate)
        assertEquals(before.evidence, after.evidence)
        assertEquals("NONE", DynamicResistancePreparationDiagnostics.from(before).contextPolicy)
    }

    @Test
    fun `7B1 model configs encode real policy choices without fitting hyperparameters`() {
        val configs = DynamicResistanceV1Contract.modelConfigs(Instant.parse("2026-08-27T00:00:00Z"))
        assertEquals(
            setOf(
                InferenceModelComponent.PERFORMANCE_NORMALISATION,
                InferenceModelComponent.RESISTANCE,
                InferenceModelComponent.DYNAMIC_CAPABILITY,
            ),
            configs.map { it.component }.toSet(),
        )
        val capability = configs.single { it.component == InferenceModelComponent.DYNAMIC_CAPABILITY }
        assertTrue(capability.canonicalConfigPayload.contains("contextConsumption=NONE"))
        assertTrue(capability.canonicalConfigPayload.contains("not_implemented_until_7b2"))
        assertFalse(capability.canonicalConfigPayload.contains("slopePrior"))
        assertFalse(capability.canonicalConfigPayload.contains("e1RM", ignoreCase = true))
    }

    @Test
    fun `successful-set validation contract is lower-bound rather than chosen-load MAE`() {
        val validation = dev.kian.mymettle.domain.inference.DynamicCapabilityValidationContract()
        assertFalse(validation.naiveChosenLoadMaeIsCapabilityMetric)
        assertEquals("lower_bound_demonstration", validation.successfulSetSemantics.storageValue)
        assertTrue(validation.questions.isNotEmpty())
    }

    @Test
    fun `projection does not mutate raw entered or canonical evidence`() {
        val candidate = evidence(load = 132.27735731092655, loadUnit = UnitId.POUND, reps = 8)
        val original = candidate.metricValues.map { it.copy() }
        project(profile(), candidate)
        assertEquals(original, candidate.metricValues)
        assertEquals(UnitId.POUND, candidate.metric(PerformanceMetric.EXTERNAL_LOAD)!!.entered.unit)
    }

    @Test
    fun `ordinary external coordinate is independent of unrelated body mass changes`() {
        val p = profile()
        val lightBody = project(p, evidence(id = "light", bodyMass = 60.0)).evidence.single()
        val heavyBody = project(p, evidence(id = "heavy", bodyMass = 100.0)).evidence.single()
        assertEquals(lightBody.resistance.value, heavyBody.resistance.value)
    }

    @Test
    fun `developer preparation diagnostics report evidence not a fake posterior`() {
        val p = profile()
        val projection = DynamicResistanceEvidenceProjector.project(
            p,
            Laterality.BILATERAL,
            listOf(
                evidence(id = "working", sessionId = "session_1", reps = 8, load = 60.0),
                evidence(id = "warm", sessionId = "session_2", reps = 10, load = 40.0, warmUp = true),
            ),
        )
        val diagnostics = DynamicResistancePreparationDiagnostics.from(projection)
        assertEquals(1, diagnostics.eligibleObservationCount)
        assertEquals(1, diagnostics.independentSessionCount)
        assertEquals(1, diagnostics.warmUpsExcludedCount)
        assertEquals("NOT_YET_FIT_7B1", diagnostics.candidateCapabilityPosterior)
        assertTrue(diagnostics.renderText().contains("candidate capability posterior: NOT_YET_FIT_7B1"))
    }

    private fun project(
        profile: DynamicResistanceProfileSemantics,
        candidate: CompletedSetEvidence,
    ) = DynamicResistanceEvidenceProjector.project(profile, candidate.laterality, listOf(candidate))

    private fun profile(
        versionId: String = "profile:v1",
        profileId: String = "profile",
        family: MetricFamily = MetricFamily.DYNAMIC_RESISTANCE,
        semantics: ResistanceSemantics = ResistanceSemantics.EXTERNAL,
        bodyweightCoefficient: Double = 0.0,
        externalLoadCoefficient: Double = 1.0,
        assistanceCoefficient: Double = 0.0,
        entryBasis: EntryBasis = EntryBasis.TOTAL,
        mode: LateralityMode = LateralityMode.BILATERAL_ONLY,
    ) = DynamicResistanceProfileSemantics(
        executionProfileVersionId = ExecutionProfileVersionId(versionId),
        executionProfileId = ExecutionProfileId(profileId),
        metricFamily = family,
        resistanceModel = ResistanceModel(
            modelVersion = "resistance-test-v1",
            semantics = semantics,
            bodyweightCoefficient = bodyweightCoefficient,
            externalLoadCoefficient = externalLoadCoefficient,
            assistanceCoefficient = assistanceCoefficient,
        ),
        entryBasis = entryBasis,
        lateralityMode = mode,
    )

    private fun evidence(
        id: String = "evidence",
        profileVersionId: String = "profile:v1",
        family: MetricFamily = MetricFamily.DYNAMIC_RESISTANCE,
        laterality: Laterality = Laterality.BILATERAL,
        load: Double = 60.0,
        loadUnit: UnitId = UnitId.KILOGRAM,
        assistance: Double? = null,
        bodyMass: Double? = null,
        reps: Int = 8,
        warmUp: Boolean = false,
        sessionId: String = "session_1",
        noLoad: Boolean = false,
        valuesOverride: List<PerformanceMetricValue>? = null,
    ): CompletedSetEvidence {
        val values = valuesOverride ?: buildList {
            if (!noLoad && assistance == null) add(PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(load, loadUnit)))
            if (assistance != null) add(PerformanceMetricValue(PerformanceMetric.ASSISTANCE, Quantity(assistance, UnitId.KILOGRAM)))
            add(PerformanceMetricValue(PerformanceMetric.REPETITIONS, Quantity(reps.toDouble(), UnitId.REPETITION)))
        }
        return CompletedSetEvidence(
            setRecordId = "set_$id",
            observationId = "obs_$id",
            sessionExerciseId = "session_exercise_$id",
            executionProfileVersionId = ExecutionProfileVersionId(profileVersionId),
            metricFamily = family,
            laterality = laterality,
            completedAt = Instant.parse("2026-08-27T10:00:00Z").plusSeconds(id.length.toLong()),
            metricValues = values,
            bodyMassContextKg = bodyMass,
            warmUp = warmUp,
            kind = if (warmUp) "warmup" else "working",
            sessionId = sessionId,
        )
    }

    private fun metric(metric: PerformanceMetric, value: Double) =
        PerformanceMetricValue(metric, Quantity(value, metric.canonicalUnit))

    private fun observation(id: String, load: Double, supersedes: String? = null) = PerformanceObservation(
        id = "obs_$id",
        setRecordId = "set_correction",
        executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
        ordinal = if (supersedes == null) 0 else 1,
        laterality = Laterality.BILATERAL,
        completedAt = Instant.parse("2026-08-27T10:00:00Z").plusSeconds(if (supersedes == null) 0 else 1),
        source = "test",
        bodyMassContextKg = null,
        values = listOf(
            PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(load, UnitId.KILOGRAM)),
            PerformanceMetricValue(PerformanceMetric.REPETITIONS, Quantity(8.0, UnitId.REPETITION)),
        ),
        supersedesObservationId = supersedes,
    )
}
