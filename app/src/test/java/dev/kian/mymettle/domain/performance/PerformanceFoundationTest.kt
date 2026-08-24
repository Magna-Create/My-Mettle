package dev.kian.mymettle.domain.performance

import dev.kian.mymettle.data.local.toDomain
import dev.kian.mymettle.data.local.toEntity
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.EquipmentProfile
import dev.kian.mymettle.domain.exercise.ExecutionProfile
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersion
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.exercise.RecruitmentAllocation
import dev.kian.mymettle.domain.exercise.RecruitmentProfile
import dev.kian.mymettle.domain.exercise.RecruitmentProfileVersionId
import dev.kian.mymettle.domain.exercise.RecruitmentRole
import dev.kian.mymettle.domain.exercise.RecruitmentSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformanceFoundationTest {
    @Test
    fun `standard load and reps are separate metrics in one observation`() {
        val dynamic = schema(
            MetricFamily.DYNAMIC_RESISTANCE,
            SchemaMetric(PerformanceMetric.EXTERNAL_LOAD, true),
            SchemaMetric(PerformanceMetric.REPETITIONS, true),
        )
        val performed = values(
            PerformanceMetric.EXTERNAL_LOAD to 40.0,
            PerformanceMetric.REPETITIONS to 9.0,
        )

        dynamic.validate(performed)
        assertEquals(listOf(PerformanceMetric.EXTERNAL_LOAD, PerformanceMetric.REPETITIONS), performed.map { it.metric })
    }

    @Test
    fun `kg and lb round trip while entered and canonical values remain distinct`() {
        val entered = PerformanceMetricValue(
            PerformanceMetric.EXTERNAL_LOAD,
            Quantity(100.0, UnitId.POUND),
        )

        assertEquals(UnitId.POUND, entered.entered.unit)
        assertEquals(UnitId.KILOGRAM, entered.canonical.unit)
        assertTrue(UnitConverter.roundTripStable(Quantity(100.0, UnitId.POUND), UnitId.KILOGRAM))
        assertTrue(UnitConverter.roundTripStable(Quantity(45.0, UnitId.KILOGRAM), UnitId.POUND))
    }

    @Test
    fun `treadmill speed grade duration and distance retain physical dimensions`() {
        val treadmill = schema(
            MetricFamily.SPEED_DURATION,
            SchemaMetric(PerformanceMetric.SPEED, true, defaultUnit = UnitId.MILES_PER_HOUR),
            SchemaMetric(PerformanceMetric.INCLINE_GRADE, true, defaultUnit = UnitId.PERCENT),
            SchemaMetric(PerformanceMetric.DURATION, true, defaultUnit = UnitId.MINUTE),
            SchemaMetric(PerformanceMetric.DISTANCE, false, defaultUnit = UnitId.MILE),
        )
        val speed = PerformanceMetricValue(PerformanceMetric.SPEED, Quantity(10.0, UnitId.MILES_PER_HOUR))
        val values = listOf(
            speed,
            PerformanceMetricValue(PerformanceMetric.INCLINE_GRADE, Quantity(5.0, UnitId.PERCENT)),
            PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(20.0, UnitId.MINUTE)),
            PerformanceMetricValue(PerformanceMetric.DISTANCE, Quantity(3.0, UnitId.MILE)),
        )

        treadmill.validate(values)
        assertEquals(4.4704, speed.canonical.value, absoluteTolerance = 1e-9)
        assertEquals(UnitId.METRES_PER_SECOND, speed.canonical.unit)
        assertEquals(UnitId.MILES_PER_HOUR, speed.entered.unit)
    }

    @Test
    fun `uncalibrated stair level remains ordinal alongside duration steps and floors`() {
        val stairs = schema(
            MetricFamily.DEVICE_ORDINAL,
            SchemaMetric(PerformanceMetric.MACHINE_LEVEL, true, defaultUnit = UnitId.MACHINE_LEVEL),
            SchemaMetric(PerformanceMetric.DURATION, true),
            SchemaMetric(PerformanceMetric.STEPS, false, defaultUnit = UnitId.STEP),
            SchemaMetric(PerformanceMetric.FLOORS, false, defaultUnit = UnitId.FLOOR),
        )
        val level = PerformanceMetricValue(PerformanceMetric.MACHINE_LEVEL, Quantity(8.0, UnitId.MACHINE_LEVEL))
        stairs.validate(
            listOf(
                level,
                PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(600.0, UnitId.SECOND)),
                PerformanceMetricValue(PerformanceMetric.STEPS, Quantity(900.0, UnitId.STEP)),
                PerformanceMetricValue(PerformanceMetric.FLOORS, Quantity(45.0, UnitId.FLOOR)),
            ),
        )

        assertEquals(UnitId.MACHINE_LEVEL, level.canonical.unit)
        assertFailsWith<IllegalArgumentException> { UnitConverter.convert(level.entered, UnitId.KILOGRAM) }
        assertFailsWith<IllegalArgumentException> {
            UnitConverter.convert(Quantity(8.0, UnitId.REPETITION), UnitId.STEP)
        }
    }

    @Test
    fun `load-duration repeated-contraction and power-duration schemas need no composite enum`() {
        val loadedHold = schema(
            MetricFamily.LOADED_HOLD,
            SchemaMetric(PerformanceMetric.EXTERNAL_LOAD, true),
            SchemaMetric(PerformanceMetric.DURATION, true),
        )
        val repeated = schema(
            MetricFamily.REPEATED_CONTRACTION,
            SchemaMetric(PerformanceMetric.REPETITIONS, true),
            SchemaMetric(PerformanceMetric.DURATION, false),
            SchemaMetric(PerformanceMetric.CADENCE, false),
        )
        val conditioning = schema(
            MetricFamily.POWER_DURATION,
            SchemaMetric(PerformanceMetric.POWER, true),
            SchemaMetric(PerformanceMetric.DURATION, true),
        )

        loadedHold.validate(values(PerformanceMetric.EXTERNAL_LOAD to 20.0, PerformanceMetric.DURATION to 40.0))
        repeated.validate(values(PerformanceMetric.REPETITIONS to 30.0))
        conditioning.validate(values(PerformanceMetric.POWER to 250.0, PerformanceMetric.DURATION to 300.0))
    }

    @Test
    fun `left and right observations remain separate and are never averaged`() {
        val left = observation("left", Laterality.LEFT, 20.0)
        val right = observation("right", Laterality.RIGHT, 17.5)

        assertEquals(listOf(Laterality.LEFT, Laterality.RIGHT), listOf(left, right).map { it.laterality })
        assertEquals(listOf(20.0, 17.5), listOf(left, right).map { it.values.single().canonical.value })
    }

    @Test
    fun `entry basis preserves total per-hand and per-side semantics without unilateral doubling`() {
        assertEquals(20.0, ResistanceResolver.totalImplementMassForBookkeeping(20.0, EntryBasis.TOTAL, 2))
        assertEquals(40.0, ResistanceResolver.totalImplementMassForBookkeeping(20.0, EntryBasis.PER_HAND, 2))
        assertNull(ResistanceResolver.totalImplementMassForBookkeeping(20.0, EntryBasis.PER_SIDE, 2))
    }

    @Test
    fun `less assistance is harder and bodyweight plus external remains a versioned coordinate`() {
        val assistance = ResistanceModel("assistance-v1", ResistanceSemantics.ASSISTANCE, 1.0, 0.0, 1.0)
        val easier = ResistanceResolver.resolve(assistance, ResistanceInputs(bodyMassKg = 80.0, assistanceKg = 30.0))!!
        val harder = ResistanceResolver.resolve(assistance, ResistanceInputs(bodyMassKg = 80.0, assistanceKg = 20.0))!!
        assertTrue(harder.coordinate > easier.coordinate)

        val weighted = ResistanceModel("weighted-body-v1", ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL, 1.0, 1.0, 0.0)
        assertEquals(
            100.0,
            ResistanceResolver.resolve(weighted, ResistanceInputs(bodyMassKg = 80.0, externalLoadKg = 20.0))?.coordinate,
        )
    }

    @Test
    fun `execution and recruitment history stay recoverable after a new immutable version`() {
        val recruitmentV1 = recruitment(1, 0.7, supersededAt = "2026-08-20T00:00:00Z")
        val recruitmentV2 = recruitment(2, 0.8, supersededAt = null)
        val versionV1 = executionVersion(1, recruitmentV1, supersededAt = "2026-08-20T00:00:00Z")
        val versionV2 = executionVersion(2, recruitmentV2, supersededAt = null)
        val profile = ExecutionProfile(
            id = ExecutionProfileId("profile_grip"),
            exerciseId = ExerciseId("grip_hold"),
            name = "Default",
            isDefault = true,
            archived = false,
            versions = listOf(versionV1, versionV2),
        )

        assertEquals(2, profile.currentVersion.version)
        assertEquals(0.7, profile.versions.single { it.version == 1 }.recruitment.allocations.single().weighting)
        assertEquals(0.8, profile.currentVersion.recruitment.allocations.single().weighting)
    }

    @Test
    fun `per-metric prescription provenance survives persistence mapping`() {
        val target = MetricTarget(
            metric = PerformanceMetric.DURATION,
            kind = TargetKind.RANGE,
            lowerCanonical = 30.0,
            upperCanonical = 40.0,
            displayUnit = UnitId.SECOND,
            evidence = PrescriptionEvidence(
                source = "raw_same_profile_version_history",
                sourceObservationId = "observation_1",
                sourceSetRecordId = "set_1",
                inferenceRunId = null,
                anchorCanonical = 35.0,
                modelVersion = "test-v1",
            ),
        )

        val restored = target.toEntity("prescription_set_1").toDomain()
        assertEquals(target, restored)
    }

    private fun schema(family: MetricFamily, vararg metrics: SchemaMetric) = PerformanceSchema(
        id = "schema_${family.storageValue}",
        version = 1,
        family = family,
        metrics = metrics.toList(),
        provenance = "test",
    )

    private fun values(vararg values: Pair<PerformanceMetric, Double>) = values.map { (metric, value) ->
        PerformanceMetricValue(metric, Quantity(value, metric.canonicalUnit))
    }

    private fun observation(id: String, laterality: Laterality, load: Double) = PerformanceObservation(
        id = "observation_$id",
        setRecordId = "set_grip",
        executionProfileVersionId = ExecutionProfileVersionId("profile_grip:v1"),
        ordinal = if (laterality == Laterality.LEFT) 0 else 1,
        laterality = laterality,
        completedAt = Instant.parse("2026-08-20T10:00:00Z"),
        source = "test",
        bodyMassContextKg = null,
        values = listOf(PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(load, UnitId.KILOGRAM))),
    )

    private fun recruitment(version: Int, weighting: Double, supersededAt: String?) = RecruitmentProfile(
        id = RecruitmentProfileVersionId("recruitment_grip:v$version"),
        version = version,
        allocations = listOf(
            RecruitmentAllocation(
                segmentId = MuscleSegmentId("forearm_flexors"),
                segmentName = "Forearm flexors",
                role = RecruitmentRole.PRIME,
                weighting = weighting,
                confidence = 0.8,
                source = RecruitmentSource("curated", "test"),
                applicableRom = null,
                applicableTechnique = "neutral_grip",
                resistanceCurveClass = null,
                modelVersion = "recruitment-v$version",
            ),
        ),
        createdAt = "2026-08-10T00:00:00Z",
        effectiveAt = "2026-08-10T00:00:00Z",
        supersededAt = supersededAt,
        provenance = "test",
        modelVersion = "recruitment-v$version",
    )

    private fun executionVersion(
        version: Int,
        recruitment: RecruitmentProfile,
        supersededAt: String?,
    ) = ExecutionProfileVersion(
        id = ExecutionProfileVersionId("profile_grip:v$version"),
        executionProfileId = ExecutionProfileId("profile_grip"),
        version = version,
        metricFamily = MetricFamily.LOADED_HOLD,
        schema = schema(
            MetricFamily.LOADED_HOLD,
            SchemaMetric(PerformanceMetric.EXTERNAL_LOAD, true),
            SchemaMetric(PerformanceMetric.DURATION, true),
        ).copy(id = "schema_grip:v$version", version = version),
        equipment = EquipmentProfile("dumbbell", "free_weight"),
        resistanceModel = ResistanceModel("resistance-v$version", ResistanceSemantics.EXTERNAL, 0.0, 1.0, 0.0),
        entryBasis = EntryBasis.PER_SIDE,
        implementCount = 1,
        lateralityMode = LateralityMode.UNILATERAL,
        romClass = "isometric",
        techniqueClass = "neutral_grip",
        resistanceCurveClass = null,
        movementPattern = "grip_hold",
        jointActions = emptyList(),
        kineticChain = null,
        contractionType = "isometric",
        gripSupportConstraints = emptyList(),
        recruitment = recruitment,
        createdAt = "2026-08-10T00:00:00Z",
        effectiveAt = "2026-08-10T00:00:00Z",
        supersededAt = supersededAt,
        provenance = "test",
        modelVersion = "execution-v$version",
    )
}
