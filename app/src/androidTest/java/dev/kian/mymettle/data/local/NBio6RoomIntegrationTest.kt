package dev.kian.mymettle.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.ProgrammeModeConstraintEntity
import dev.kian.mymettle.data.local.entity.RoutineMetricTargetEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import dev.kian.mymettle.data.local.entity.UserProfileEntity
import dev.kian.mymettle.data.reference.ReferenceSeedCallback
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.EquipmentProfile
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersion
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.exercise.RecruitmentAllocation
import dev.kian.mymettle.domain.exercise.RecruitmentProfile
import dev.kian.mymettle.domain.exercise.RecruitmentProfileVersionId
import dev.kian.mymettle.domain.exercise.RecruitmentRole
import dev.kian.mymettle.domain.exercise.RecruitmentSource
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.PerformanceSchema
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.SchemaMetric
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.history.HistoryRepository
import dev.kian.mymettle.inference.RoomInferenceRepository
import dev.kian.mymettle.library.ExecutionProfileAuthoringRepository
import dev.kian.mymettle.library.ExecutionProfileAuthoringRequest
import dev.kian.mymettle.workout.RoomWorkoutRepository
import dev.kian.mymettle.workout.TrainingMode
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NBio6RoomIntegrationTest {
    private lateinit var database: MyMettleDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java)
            .addCallback(ReferenceSeedCallback(context))
            .build()
        database.openHelper.writableDatabase
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun deadHangFlowsFromRepFreePreferenceToHistoryAndInference() = runBlocking {
        seedUser()
        val version = profileVersion(
            profile = "dead_hang",
            version = 1,
            family = MetricFamily.DURATION_ONLY,
            metrics = listOf(SchemaMetric(PerformanceMetric.DURATION, required = true)),
            lateralityMode = LateralityMode.BILATERAL_ONLY,
            entryBasis = EntryBasis.TOTAL,
            recruitment = emptyList(),
        )
        ExecutionProfileAuthoringRepository(database).createProfile(
            ExecutionProfileAuthoringRequest(
                exerciseId = ExerciseId("dead_hang"),
                exerciseName = "Dead hang",
                profileName = "Bar hang",
                isDefault = true,
                version = version,
            ),
        )

        val dao = database.workoutDao()
        dao.upsertRoutineVersions(listOf(RoutineVersionEntity("routine_1", 1, null, NOW, NOW, "test", "")))
        dao.upsertRoutineSlots(
            listOf(RoutineSlotEntity("slot_dead_hang", "routine_1", "ψ", "dead_hang", 0, "principal", true, 1, 60)),
        )
        dao.upsertRoutineMetricTargets(
            listOf(
                RoutineMetricTargetEntity(
                    routineVersionId = "routine_1",
                    slotId = "slot_dead_hang",
                    metric = PerformanceMetric.DURATION.storageValue,
                    targetKind = TargetKind.RANGE.storageValue,
                    lowerCanonical = 35.0,
                    upperCanonical = 45.0,
                    canonicalUnit = UnitId.SECOND.storageValue,
                    displayUnit = UnitId.SECOND.storageValue,
                    source = "test_duration_preference",
                    modelVersion = "test-v1",
                ),
            ),
        )
        dao.upsertProgrammeModeConstraints(
            listOf(ProgrammeModeConstraintEntity("routine_1", "ψ", "A", 1, 1, 1, 0.0, null, "test", "test-v1")),
        )
        dao.upsertTrainingCycles(listOf(TrainingCycleEntity("cycle_1", NOW, null, "active", false)))
        dao.upsertAppState(AppStateEntity(currentRoutineVersionId = "routine_1", currentCycleId = "cycle_1", activeSessionId = null, createdAt = NOW, updatedAt = NOW))

        val workouts = RoomWorkoutRepository(database)
        val plan = workouts.plan("ψ", TrainingMode.A)
        assertNull(plan.exercises.single().prescription.repRange)
        assertEquals(PerformanceMetric.DURATION, plan.exercises.single().prescription.setPrescriptions.single().metricTargets.single().metric)

        val active = workouts.startSession("ψ", TrainingMode.A)
        val exercise = active.exercises.single()
        val set = exercise.sets.single()
        workouts.saveObservation(
            sessionExerciseId = exercise.entity.id,
            setId = set.id,
            laterality = Laterality.BILATERAL,
            values = listOf(PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(42.0, UnitId.SECOND))),
        )
        workouts.setExerciseCompleted(active.session.id, exercise.entity.id, completed = true)
        workouts.completeSession(active.session.id)

        val historical = HistoryRepository(database).recent().single().exercises.single().sets.single()
        assertEquals(42, historical.durationSeconds)
        assertNull(historical.reps)
        assertTrue(historical.observations.single().values.none { it.metric == PerformanceMetric.REPETITIONS })

        val inference = RoomInferenceRepository(database).recomputeFromRawHistory()
        assertEquals(PerformanceMetric.DURATION, inference.exerciseTranslationStates.single().anchors.single().metric)
        assertEquals(Laterality.BILATERAL, inference.exerciseTranslationStates.single().laterality)
    }

    @Test
    fun correctionsInferenceDeletionAndVersionPublishingPreserveRawEvidence() = runBlocking {
        seedUser()
        val segment = database.referenceDao().segments().first()
        val v1 = profileVersion(
            profile = "grip_hold",
            version = 1,
            family = MetricFamily.LOADED_HOLD,
            metrics = listOf(
                SchemaMetric(PerformanceMetric.EXTERNAL_LOAD, required = true, defaultUnit = UnitId.POUND),
                SchemaMetric(PerformanceMetric.DURATION, required = true),
            ),
            lateralityMode = LateralityMode.UNILATERAL,
            entryBasis = EntryBasis.PER_SIDE,
            recruitment = listOf(allocation(segment.id, segment.name, 0.7, 1)),
        )
        val authoring = ExecutionProfileAuthoringRepository(database)
        authoring.createProfile(
            ExecutionProfileAuthoringRequest(
                exerciseId = ExerciseId("grip_hold"),
                exerciseName = "Loaded grip hold",
                profileName = "Dumbbell per side",
                isDefault = true,
                version = v1,
            ),
        )
        seedCompletedSet(v1)

        val workouts = RoomWorkoutRepository(database)
        val original = workouts.saveObservation(
            "session_exercise_1",
            "set_1",
            Laterality.LEFT,
            listOf(
                PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(44.09245243697552, UnitId.POUND)),
                PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(35.0, UnitId.SECOND)),
            ),
            bodyMassContextKg = 80.0,
        )
        val correction = workouts.saveObservation(
            "session_exercise_1",
            "set_1",
            Laterality.LEFT,
            listOf(
                PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(44.09245243697552, UnitId.POUND)),
                PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(40.0, UnitId.SECOND)),
            ),
            source = "native_history_correction",
            bodyMassContextKg = 80.0,
        )
        val right = workouts.saveObservation(
            "session_exercise_1",
            "set_1",
            Laterality.RIGHT,
            listOf(
                PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(17.5, UnitId.KILOGRAM)),
                PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(32.0, UnitId.SECOND)),
            ),
            bodyMassContextKg = 80.0,
        )

        val dao = database.workoutDao()
        val rawObservations = dao.observations(listOf("set_1"))
        val rawMetrics = dao.metricValues(rawObservations.map { it.id })
        val rawVersion = dao.executionProfileVersionsById(listOf(v1.id.value)).single()
        assertEquals(original.id, correction.supersedesObservationId)
        assertEquals(listOf(original.id, correction.id, right.id), rawObservations.map { it.id })
        assertEquals(listOf(correction.id, right.id), workouts.performanceSets("session_exercise_1").single().observations.map { it.id })
        val correctedLoad = rawMetrics.single {
            it.observationId == correction.id && it.metric == PerformanceMetric.EXTERNAL_LOAD.storageValue
        }
        assertEquals(UnitId.POUND.storageValue, correctedLoad.enteredUnit)
        assertEquals(UnitId.KILOGRAM.storageValue, correctedLoad.canonicalUnit)
        assertEquals(20.0, correctedLoad.canonicalValue, absoluteTolerance = 1e-9)

        val inferenceRepository = RoomInferenceRepository(database)
        val firstInference = inferenceRepository.recomputeFromRawHistory()
        assertEquals(setOf(correction.id, right.id), firstInference.stimulusEstimates.mapTo(hashSetOf()) { it.observationId })
        assertTrue(firstInference.stimulusEstimates.all { it.executionProfileVersionId == v1.id })
        assertTrue(firstInference.stimulusEstimates.all { it.recruitmentProfileVersionId == v1.recruitment.id })
        assertEquals(setOf(Laterality.LEFT, Laterality.RIGHT), firstInference.exerciseTranslationStates.mapTo(hashSetOf()) { it.laterality })

        inferenceRepository.discardDerivedStateForRebuild()
        assertEquals(rawObservations, dao.observations(listOf("set_1")))
        assertEquals(rawMetrics, dao.metricValues(rawObservations.map { it.id }))
        assertEquals(rawVersion, dao.executionProfileVersionsById(listOf(v1.id.value)).single())
        val replay = inferenceRepository.recomputeFromRawHistory()
        assertEquals(
            firstInference.stimulusEstimates.map { it.observationId to it.estimatedStimulus },
            replay.stimulusEstimates.map { it.observationId to it.estimatedStimulus },
        )

        val v2 = profileVersion(
            profile = "grip_hold",
            version = 2,
            family = MetricFamily.LOADED_HOLD,
            metrics = v1.schema.metrics,
            lateralityMode = LateralityMode.UNILATERAL,
            entryBasis = EntryBasis.PER_SIDE,
            recruitment = listOf(allocation(segment.id, segment.name, 0.8, 2)),
        )
        authoring.publishSuccessor(v2)
        val versions = dao.executionProfileVersions(listOf("profile_grip_hold"))
        val historicalV1 = versions.single { it.id == v1.id.value }
        assertNotNull(historicalV1.supersededAt)
        assertEquals(rawVersion, historicalV1.copy(supersededAt = rawVersion.supersededAt))
        assertEquals(v1.id.value, dao.observations(listOf("set_1")).first().executionProfileVersionId)
        assertEquals(
            setOf(v1.recruitment.id.value, v2.recruitment.id.value),
            dao.recruitmentProfileVersions(listOf(v1.recruitment.id.value, v2.recruitment.id.value)).mapTo(hashSetOf()) { it.id },
        )
    }

    @Test
    fun firstAuthoredProfileMustEstablishTheStableDefault() = runBlocking {
        val version = profileVersion(
            profile = "non_default_first",
            version = 1,
            family = MetricFamily.REPEATED_CONTRACTION,
            metrics = listOf(SchemaMetric(PerformanceMetric.REPETITIONS, required = true)),
            lateralityMode = LateralityMode.BILATERAL_ONLY,
            entryBasis = EntryBasis.TOTAL,
            recruitment = emptyList(),
        )

        assertFailsWith<dev.kian.mymettle.library.ExecutionProfileAuthoringException> {
            ExecutionProfileAuthoringRepository(database).createProfile(
                ExecutionProfileAuthoringRequest(
                    exerciseId = ExerciseId("non_default_first"),
                    exerciseName = "Invalid first profile",
                    profileName = "Not default",
                    isDefault = false,
                    version = version,
                ),
            )
        }
        assertTrue(database.workoutDao().exercises(listOf("non_default_first")).isEmpty())
    }

    private suspend fun seedUser() {
        database.workoutDao().upsertProfile(UserProfileEntity("user_1", "Tester", "kg", "none", 1, NOW, NOW))
    }

    private suspend fun seedCompletedSet(version: ExecutionProfileVersion) {
        val dao = database.workoutDao()
        dao.upsertSessions(
            listOf(
                SessionEntity(
                    id = "session_1",
                    cycleId = "cycle_1",
                    daySymbol = "ψ",
                    mode = "A",
                    routineVersionId = "routine_1",
                    status = "completed",
                    startedAt = NOW,
                    completedAt = "2026-08-24T10:30:00Z",
                    editedAt = null,
                    discardedAt = null,
                    excludedFromInsights = false,
                    bodyweightSnapshotKg = 79.0,
                    healthExportState = "not_requested",
                    healthClientRecordId = null,
                ),
            ),
        )
        dao.upsertSessionExercises(
            listOf(
                SessionExerciseEntity(
                    id = "session_exercise_1",
                    sessionId = "session_1",
                    position = 0,
                    exerciseId = "grip_hold",
                    slotId = "slot_grip",
                    exerciseNameSnapshot = "Loaded grip hold",
                    importanceSnapshot = "principal",
                    executionProfileId = version.executionProfileId.value,
                    executionProfileVersionId = version.id.value,
                    executionProfileNameSnapshot = "Dumbbell per side",
                    prescriptionMode = "A",
                    prescriptionIncluded = true,
                    restSeconds = 60,
                    generatedByModelVersion = "test-v1",
                    deferToAnd = false,
                    status = "completed",
                    note = null,
                    startedAt = NOW,
                    completedAt = "2026-08-24T10:30:00Z",
                    movementReason = "test",
                    substitutedFromExerciseId = null,
                ),
            ),
        )
        dao.upsertSets(listOf(SetRecordEntity("set_1", "session_exercise_1", 0, null, false, "prescribed", NOW)))
    }

    private fun profileVersion(
        profile: String,
        version: Int,
        family: MetricFamily,
        metrics: List<SchemaMetric>,
        lateralityMode: LateralityMode,
        entryBasis: EntryBasis,
        recruitment: List<RecruitmentAllocation>,
    ): ExecutionProfileVersion {
        val effectiveAt = if (version == 1) NOW else "2026-08-25T00:00:00Z"
        return ExecutionProfileVersion(
            id = ExecutionProfileVersionId("profile_${profile}:v$version"),
            executionProfileId = ExecutionProfileId("profile_$profile"),
            version = version,
            metricFamily = family,
            schema = PerformanceSchema("schema_${profile}:v$version", version, family, metrics, "instrumented-test"),
            equipment = EquipmentProfile(if (profile == "grip_hold") "dumbbell" else "pull_up_bar", "test"),
            resistanceModel = ResistanceModel(
                "resistance-$profile-v$version",
                if (family == MetricFamily.DURATION_ONLY) ResistanceSemantics.BODYWEIGHT else ResistanceSemantics.EXTERNAL,
                if (family == MetricFamily.DURATION_ONLY) 1.0 else 0.0,
                if (family == MetricFamily.DURATION_ONLY) 0.0 else 1.0,
                0.0,
            ),
            entryBasis = entryBasis,
            implementCount = if (entryBasis == EntryBasis.PER_SIDE) 1 else null,
            lateralityMode = lateralityMode,
            romClass = "isometric",
            techniqueClass = "test",
            resistanceCurveClass = null,
            movementPattern = profile,
            jointActions = emptyList(),
            kineticChain = null,
            contractionType = "isometric",
            gripSupportConstraints = emptyList(),
            recruitment = RecruitmentProfile(
                id = RecruitmentProfileVersionId("recruitment_${profile}:v$version"),
                version = version,
                allocations = recruitment,
                createdAt = effectiveAt,
                effectiveAt = effectiveAt,
                supersededAt = null,
                provenance = "instrumented-test",
                modelVersion = "recruitment-test-v$version",
            ),
            createdAt = effectiveAt,
            effectiveAt = effectiveAt,
            supersededAt = null,
            provenance = "instrumented-test",
            modelVersion = "execution-test-v$version",
        )
    }

    private fun allocation(segmentId: String, name: String, weighting: Double, version: Int) = RecruitmentAllocation(
        segmentId = MuscleSegmentId(segmentId),
        segmentName = name,
        role = RecruitmentRole.PRIME,
        weighting = weighting,
        confidence = 0.8,
        source = RecruitmentSource("instrumented-test", null),
        applicableRom = "isometric",
        applicableTechnique = "test",
        resistanceCurveClass = null,
        modelVersion = "recruitment-test-v$version",
    )

    private companion object {
        const val NOW = "2026-08-24T10:00:00Z"
    }
}
