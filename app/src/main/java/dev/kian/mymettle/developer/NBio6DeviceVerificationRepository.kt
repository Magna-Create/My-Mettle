package dev.kian.mymettle.developer

import android.content.Context
import androidx.room.Room
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.ProgrammeModeConstraintEntity
import dev.kian.mymettle.data.local.entity.RoutineMetricTargetEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SetObservationEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import dev.kian.mymettle.data.local.entity.UserProfileEntity
import dev.kian.mymettle.data.migration.LegacyProgrammeConstraintProjector
import dev.kian.mymettle.data.migration.LegacyRecruitmentResolver
import dev.kian.mymettle.data.migration.LegacySetupPhotoImporter
import dev.kian.mymettle.data.migration.LegacySnapshotPersister
import dev.kian.mymettle.data.migration.LegacyTargetProjector
import dev.kian.mymettle.data.migration.LegacyTranslationContract
import dev.kian.mymettle.data.migration.LegacyV6BackupReader
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
import dev.kian.mymettle.domain.performance.ResistanceInputs
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceResolver
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.SchemaMetric
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitConverter
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.history.HistoryRepository
import dev.kian.mymettle.inference.RoomInferenceRepository
import dev.kian.mymettle.library.ExecutionProfileAuthoringRepository
import dev.kian.mymettle.library.ExecutionProfileAuthoringRequest
import dev.kian.mymettle.workout.ObservationSupersedingPolicy
import dev.kian.mymettle.workout.RoomWorkoutRepository
import dev.kian.mymettle.workout.TrainingMode
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class NBio6VerificationCheck(
    val id: String,
    val title: String,
    val passed: Boolean,
    val detail: String,
    val durationMillis: Long,
)

data class NBio6DeviceVerificationReport(
    val startedAt: String,
    val completedAt: String,
    val checks: List<NBio6VerificationCheck>,
    val temporalChecks: List<NBio6VerificationCheck> = emptyList(),
) {
    val allChecks: List<NBio6VerificationCheck> get() = checks + temporalChecks
    val passed: Boolean get() = allChecks.isNotEmpty() && allChecks.all { it.passed }

    fun toJson(): JSONObject = JSONObject()
        .put("kind", "n-bio-6-device-verification")
        .put("contractVersion", CONTRACT_VERSION)
        .put("startedAt", startedAt)
        .put("completedAt", completedAt)
        .put("passed", passed)
        .put("schemaVersion", 12)
        .put("codecVersion", dev.kian.mymettle.domain.evidence.TemporalEvidenceCodec.ENCODING_VERSION)
        .put("checks", checks.toJson())
        .put("temporalChecks", temporalChecks.toJson())

    private fun List<NBio6VerificationCheck>.toJson(): JSONArray = JSONArray().apply {
            forEach { check ->
                put(
                    JSONObject()
                        .put("id", check.id)
                        .put("title", check.title)
                        .put("passed", check.passed)
                        .put("detail", check.detail)
                        .put("durationMillis", check.durationMillis),
                )
            }
        }

    companion object {
        const val CONTRACT_VERSION = "n-bio-6-device-verifier-v2"
    }
}

data class NBio6LiteBackupVerificationReport(
    val fileName: String,
    val completedAt: String,
    val passed: Boolean,
    val detail: String,
    val exercises: Int = 0,
    val sessions: Int = 0,
    val sets: Int = 0,
    val observations: Int = 0,
    val metricValues: Int = 0,
    val setupPhotosValidated: Int = 0,
    val sampleEvidence: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("kind", "n-bio-6-lite-backup-verification")
        .put("contractVersion", NBio6DeviceVerificationReport.CONTRACT_VERSION)
        .put("fileName", fileName)
        .put("completedAt", completedAt)
        .put("passed", passed)
        .put("detail", detail)
        .put("exercises", exercises)
        .put("sessions", sessions)
        .put("sets", sets)
        .put("observations", observations)
        .put("metricValues", metricValues)
        .put("setupPhotosValidated", setupPhotosValidated)
        .put("sampleEvidence", JSONArray(sampleEvidence))
}

/**
 * Device-executable N-BIO-6 acceptance harness. Every automated check owns a fresh in-memory
 * Room database, exercises production repositories, and closes the database after the result is
 * captured. It never reads from or writes to the user's Native workout database.
 */
class NBio6DeviceVerificationRepository(context: Context) {
    private val appContext = context.applicationContext

    suspend fun runAutomatedChecks(): NBio6DeviceVerificationReport = withContext(Dispatchers.IO) {
        val startedAt = Instant.now().toString()
        val checks = listOf(
            runCheck("room-12", "Room 12 schema and foreign keys", ::verifyRoomContract),
            runCheck("dead-hang", "Dead hang: rep-free end to end", ::verifyDeadHang),
            runCheck("grip-replay", "Grip hold: sides, correction and replay", ::verifyGripAndReplay),
            runCheck("assistance", "Assistance directionality", ::verifyAssistanceDirection),
            runCheck("treadmill", "Treadmill physical-unit round trip", ::verifyTreadmill),
            runCheck("stair-machine", "Stair-machine ordinal evidence", ::verifyStairMachine),
            runCheck("power-duration", "Power-duration separation", ::verifyPowerDuration),
        )
        val temporalChecks = TemporalEvidenceDeviceVerifier(appContext).runChecks()
        NBio6DeviceVerificationReport(startedAt, Instant.now().toString(), checks, temporalChecks)
    }

    suspend fun verifyLiteBackup(fileName: String, json: String): NBio6LiteBackupVerificationReport =
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = LegacyV6BackupReader.read(json)
                LegacyTranslationContract.requireActiveRecruitment(snapshot)
                LegacySetupPhotoImporter(appContext).validate(snapshot.setupPhotos)
                withDatabase { database ->
                    val recruitment = LegacyRecruitmentResolver(database).resolve(snapshot.translatedRecruitment)
                    val targets = LegacyTargetProjector.project(snapshot, recruitment)
                    val constraints = LegacyProgrammeConstraintProjector.project(
                        routineSlots = snapshot.routineSlots,
                        prescriptions = snapshot.modePrescriptions,
                    )
                    LegacySnapshotPersister.persist(
                        database = database,
                        snapshot = snapshot,
                        recruitment = recruitment,
                        programmeTargets = targets.programmeTargets,
                        programmeConstraints = constraints,
                        sessionTargets = targets.sessionTargets,
                        sessionExerciseTargets = targets.sessionExerciseTargets,
                        setupMedia = emptyList(),
                    )

                    val dao = database.workoutDao()
                    expect(dao.profileCount() == 1) { "The translated profile was not persisted exactly once." }
                    expect(snapshot.sessions.all { dao.session(it.id) == it }) { "One or more sessions changed during Room persistence." }
                    snapshot.sessionExercises.forEach { exercise ->
                        expect(dao.sessionExercise(exercise.id) == exercise) {
                            "Session exercise ${exercise.id} changed during Room persistence."
                        }
                    }
                    val storedSets = snapshot.sessionExercises.flatMap { dao.sets(it.id) }
                    expect(storedSets.toSet() == snapshot.sets.toSet()) { "Set rows changed during Room persistence." }
                    val storedObservations = dao.observations(snapshot.sets.map { it.id })
                    expect(storedObservations.toSet() == snapshot.setObservations.toSet()) {
                        "Observation identity, version, time, source, body mass, or laterality changed during Room persistence."
                    }
                    val storedMetrics = dao.metricValues(storedObservations.map { it.id })
                    expect(storedMetrics.toSet() == snapshot.setMetricValues.toSet()) {
                        "Entered or canonical metric evidence changed during Room persistence."
                    }
                    expect(storedObservations.all { it.side == Laterality.UNKNOWN.storageValue }) {
                        "Lite evidence acquired invented laterality."
                    }
                    expect(storedObservations.all { it.source == "lite_legacy_v6_import" }) {
                        "Lite evidence lost its import provenance."
                    }
                    expect(storedObservations.all {
                        it.startedAtEpochSecond == null && it.startedAtNano == null &&
                            it.timingQuality == dev.kian.mymettle.domain.evidence.TimingQuality.COMPLETION_ONLY.storageValue
                    }) { "Lite evidence acquired an invented start bound or timing precision." }
                    expect(database.temporalEvidenceDao().allTraces().isEmpty()) {
                        "Lite import invented temporal traces that did not exist in schema 6."
                    }
                    expect(foreignKeyFailures(database).isEmpty()) {
                        "Imported database has foreign-key violations: ${foreignKeyFailures(database).joinToString()}"
                    }

                    val completedSessionCount = snapshot.sessions.count { it.status == "completed" }
                    val histories = HistoryRepository(database).recent(snapshot.sessions.size.coerceAtLeast(1))
                    expect(histories.size == completedSessionCount) {
                        "History returned ${histories.size} completed sessions; expected $completedSessionCount."
                    }
                    RoomInferenceRepository(database).recomputeFromRawHistory()

                    val valuesByObservation = storedMetrics.groupBy { it.observationId }
                    val samples = storedObservations.take(5).map { observation ->
                        val values = valuesByObservation[observation.id].orEmpty().joinToString(", ") { value ->
                            "${value.metric}=${format(value.enteredValue)} ${value.enteredUnit} " +
                                "(canonical ${format(value.canonicalValue)} ${value.canonicalUnit})"
                        }
                        "${observation.completedAt} · ${observation.side} · $values"
                    }
                    NBio6LiteBackupVerificationReport(
                        fileName = fileName,
                        completedAt = Instant.now().toString(),
                        passed = true,
                        detail = "AI-reviewed Native supplement and factual Lite rows persisted into isolated Room 12; independent recruitment completeness, completion-only timing, no invented traces, history, provenance, foreign keys and inference replay verified.",
                        exercises = snapshot.exercises.size,
                        sessions = snapshot.sessions.size,
                        sets = snapshot.sets.size,
                        observations = snapshot.setObservations.size,
                        metricValues = snapshot.setMetricValues.size,
                        setupPhotosValidated = snapshot.setupPhotos.size,
                        sampleEvidence = samples,
                    )
                }
            }.getOrElse { error ->
                NBio6LiteBackupVerificationReport(
                    fileName = fileName,
                    completedAt = Instant.now().toString(),
                    passed = false,
                    detail = error.conciseMessage(),
                )
            }
        }

    fun combinedJson(
        device: NBio6DeviceVerificationReport?,
        lite: NBio6LiteBackupVerificationReport?,
    ): String = JSONObject()
        .put("kind", "n-bio-6-closure-report")
        .put("contractVersion", NBio6DeviceVerificationReport.CONTRACT_VERSION)
        .put("exportedAt", Instant.now().toString())
        .put("deviceVerification", device?.toJson() ?: JSONObject.NULL)
        .put("liteBackupVerification", lite?.toJson() ?: JSONObject.NULL)
        .toString(2)

    private suspend fun runCheck(
        id: String,
        title: String,
        block: suspend (MyMettleDatabase) -> String,
    ): NBio6VerificationCheck {
        val started = System.nanoTime()
        return runCatching { withDatabase(block) }.fold(
            onSuccess = { detail ->
                NBio6VerificationCheck(id, title, true, detail, elapsedMillis(started))
            },
            onFailure = { error ->
                NBio6VerificationCheck(id, title, false, error.conciseMessage(), elapsedMillis(started))
            },
        )
    }

    private suspend fun <T> withDatabase(block: suspend (MyMettleDatabase) -> T): T {
        val database = Room.inMemoryDatabaseBuilder(appContext, MyMettleDatabase::class.java)
            .addCallback(ReferenceSeedCallback(appContext))
            .build()
        return try {
            database.openHelper.writableDatabase
            block(database)
        } finally {
            database.close()
        }
    }

    private suspend fun verifyRoomContract(database: MyMettleDatabase): String {
        val sqlite = database.openHelper.writableDatabase
        val userVersion = sqlite.query("PRAGMA user_version").use { cursor ->
            expect(cursor.moveToFirst()) { "PRAGMA user_version returned no row." }
            cursor.getInt(0)
        }
        expect(userVersion == 12) { "Room opened schema $userVersion instead of schema 12." }
        val tables = sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        val required = setOf(
            "execution_profile_version",
            "recruitment_profile_version",
            "performance_schema",
            "performance_schema_metric",
            "set_record",
            "set_observation",
            "set_metric_value",
            "inference_run",
            "muscle_state_snapshot",
            "stimulus_estimate",
            "exercise_translation_state",
            "exercise_translation_metric_anchor",
            "external_evidence_artifact",
            "evidence_trace",
            "evidence_trace_chunk",
            "session_trace_link",
            "session_exercise_trace_link",
            "set_record_trace_link",
            "observation_trace_link",
            "derived_evidence_summary",
            "derived_evidence_summary_input",
            "evidence_trace_ui_cache",
        )
        expect(required.all { it in tables }) { "Missing Room tables: ${(required - tables).sorted().joinToString()}." }
        expect(foreignKeyFailures(database).isEmpty()) { "Fresh Room schema failed PRAGMA foreign_key_check." }
        return "Room $userVersion opened on-device; all N-BIO-6 raw and derived tables exist; foreign_key_check is clean."
    }

    private suspend fun verifyDeadHang(database: MyMettleDatabase): String {
        seedUser(database)
        val version = profileVersion(
            key = "dead_hang",
            family = MetricFamily.DURATION_ONLY,
            metrics = listOf(SchemaMetric(PerformanceMetric.DURATION, required = true)),
            lateralityMode = LateralityMode.BILATERAL_ONLY,
            entryBasis = EntryBasis.TOTAL,
            resistanceSemantics = ResistanceSemantics.BODYWEIGHT,
        )
        authorProfile(database, "dead_hang", "Dead hang", "Bar hang", version)
        val dao = database.workoutDao()
        dao.upsertRoutineVersions(listOf(RoutineVersionEntity("routine_dead_hang", 1, null, NOW, NOW, SOURCE, "")))
        dao.upsertRoutineSlots(
            listOf(RoutineSlotEntity("slot_dead_hang", "routine_dead_hang", "ψ", "dead_hang", 0, "principal", true, 1, 60)),
        )
        dao.upsertRoutineMetricTargets(
            listOf(
                RoutineMetricTargetEntity(
                    routineVersionId = "routine_dead_hang",
                    slotId = "slot_dead_hang",
                    metric = PerformanceMetric.DURATION.storageValue,
                    targetKind = TargetKind.RANGE.storageValue,
                    lowerCanonical = 35.0,
                    upperCanonical = 45.0,
                    canonicalUnit = UnitId.SECOND.storageValue,
                    displayUnit = UnitId.SECOND.storageValue,
                    source = SOURCE,
                    modelVersion = CONTRACT,
                ),
            ),
        )
        dao.upsertProgrammeModeConstraints(
            listOf(ProgrammeModeConstraintEntity("routine_dead_hang", "ψ", "A", 1, 1, 1, 0.0, null, SOURCE, CONTRACT)),
        )
        dao.upsertTrainingCycles(listOf(TrainingCycleEntity("cycle_dead_hang", NOW, null, "active", false)))
        dao.upsertAppState(AppStateEntity("primary", "routine_dead_hang", "cycle_dead_hang", null, NOW, NOW))

        val workouts = RoomWorkoutRepository(database)
        val plan = workouts.plan("ψ", TrainingMode.A)
        val planned = plan.exercises.single()
        expect(planned.prescription.repRange == null) { "Dead-hang plan acquired a compatibility rep range." }
        expect(planned.prescription.setPrescriptions.single().metricTargets.single().metric == PerformanceMetric.DURATION) {
            "Dead-hang preference did not become a duration target."
        }
        val active = workouts.startSession("ψ", TrainingMode.A)
        val exercise = active.exercises.single()
        val set = exercise.sets.single()
        workouts.saveObservation(
            exercise.entity.id,
            set.id,
            Laterality.BILATERAL,
            listOf(value(PerformanceMetric.DURATION, 42.0, UnitId.SECOND)),
        )
        workouts.setExerciseCompleted(active.session.id, exercise.entity.id, true)
        workouts.completeSession(active.session.id)
        val historical = HistoryRepository(database).recent().single().exercises.single().sets.single()
        expect(historical.durationSeconds == 42) { "History did not expose the recorded 42-second duration." }
        expect(historical.reps == null && historical.observations.single().values.none { it.metric == PerformanceMetric.REPETITIONS }) {
            "Dead-hang history contains invented repetitions."
        }
        val inference = RoomInferenceRepository(database).recomputeFromRawHistory()
        val translation = inference.exerciseTranslationStates.single()
        expect(translation.anchors.single().metric == PerformanceMetric.DURATION) { "Inference did not anchor duration." }
        expect(inference.stimulusEstimates.isEmpty()) { "Duration-only evidence was converted into resistance stimulus." }
        return "Preference → duration target → session snapshot → 42 s observation → history → same-profile duration anchor; zero repetitions."
    }

    private suspend fun verifyGripAndReplay(database: MyMettleDatabase): String {
        seedUser(database)
        val segment = database.referenceDao().segments().first()
        val v1 = profileVersion(
            key = "grip_hold",
            family = MetricFamily.LOADED_HOLD,
            metrics = listOf(
                SchemaMetric(PerformanceMetric.EXTERNAL_LOAD, required = true, defaultUnit = UnitId.POUND),
                SchemaMetric(PerformanceMetric.DURATION, required = true),
            ),
            lateralityMode = LateralityMode.UNILATERAL,
            entryBasis = EntryBasis.PER_SIDE,
            resistanceSemantics = ResistanceSemantics.EXTERNAL,
            recruitment = listOf(allocation(segment.id, segment.name, 0.7, 1)),
        )
        val authoring = ExecutionProfileAuthoringRepository(database)
        authoring.createProfile(
            ExecutionProfileAuthoringRequest(ExerciseId("grip_hold"), "Loaded grip hold", "Dumbbell per side", true, v1),
        )
        seedCompletedSession(database, "grip", "grip_hold", "Loaded grip hold", v1)
        val workouts = RoomWorkoutRepository(database)
        val original = workouts.saveObservation(
            "session_exercise_grip", "set_grip_0", Laterality.LEFT,
            listOf(value(PerformanceMetric.EXTERNAL_LOAD, 44.09245243697552, UnitId.POUND), value(PerformanceMetric.DURATION, 35.0, UnitId.SECOND)),
            bodyMassContextKg = 80.0,
        )
        val correction = workouts.saveObservation(
            "session_exercise_grip", "set_grip_0", Laterality.LEFT,
            listOf(value(PerformanceMetric.EXTERNAL_LOAD, 44.09245243697552, UnitId.POUND), value(PerformanceMetric.DURATION, 40.0, UnitId.SECOND)),
            source = "native_history_correction",
            bodyMassContextKg = 80.0,
        )
        val right = workouts.saveObservation(
            "session_exercise_grip", "set_grip_0", Laterality.RIGHT,
            listOf(value(PerformanceMetric.EXTERNAL_LOAD, 17.5, UnitId.KILOGRAM), value(PerformanceMetric.DURATION, 32.0, UnitId.SECOND)),
            bodyMassContextKg = 80.0,
        )
        val dao = database.workoutDao()
        val rawBefore = dao.observations(listOf("set_grip_0"))
        val metricsBefore = dao.metricValues(rawBefore.map { it.id })
        val versionBefore = dao.executionProfileVersionsById(listOf(v1.id.value)).single()
        expect(correction.supersedesObservationId == original.id) { "Correction did not reference the original observation." }
        expect(rawBefore.map { it.id } == listOf(original.id, correction.id, right.id)) { "Append-only correction history was not retained." }
        val current = workouts.performanceSets("session_exercise_grip").single().observations
        expect(current.map { it.id }.toSet() == setOf(correction.id, right.id)) { "Canonical history did not select correction leaves." }
        expect(current.map { it.laterality }.toSet() == setOf(Laterality.LEFT, Laterality.RIGHT)) { "History collapsed left/right evidence." }
        val correctedLoad = metricsBefore.single { it.observationId == correction.id && it.metric == PerformanceMetric.EXTERNAL_LOAD.storageValue }
        expect(correctedLoad.enteredUnit == UnitId.POUND.storageValue && correctedLoad.canonicalUnit == UnitId.KILOGRAM.storageValue) {
            "Entered/canonical load units were not both preserved."
        }
        expect(close(correctedLoad.canonicalValue, 20.0)) { "44.092452 lb did not canonicalise to 20 kg." }

        val inferenceRepository = RoomInferenceRepository(database)
        val first = inferenceRepository.recomputeFromRawHistory()
        expect(first.stimulusEstimates.map { it.observationId }.toSet() == setOf(correction.id, right.id)) {
            "Inference used superseded or omitted current side evidence."
        }
        expect(first.stimulusEstimates.all { it.executionProfileVersionId == v1.id && it.recruitmentProfileVersionId == v1.recruitment.id }) {
            "Inference lost execution/recruitment version provenance."
        }
        inferenceRepository.discardDerivedStateForRebuild()
        expect(inferenceRepository.latestSnapshot() == null) { "Derived inference rows were not deleted." }
        expect(dao.observations(listOf("set_grip_0")) == rawBefore) { "Deleting derived state changed observations." }
        expect(dao.metricValues(rawBefore.map { it.id }) == metricsBefore) { "Deleting derived state changed metric values." }
        expect(dao.executionProfileVersionsById(listOf(v1.id.value)).single() == versionBefore) {
            "Deleting derived state changed execution semantics."
        }
        val replay = inferenceRepository.recomputeFromRawHistory()
        expect(
            replay.stimulusEstimates.map { it.observationId to it.estimatedStimulus } ==
                first.stimulusEstimates.map { it.observationId to it.estimatedStimulus },
        ) { "Inference replay was not deterministic over unchanged raw evidence." }

        val originalEntity = rawBefore.single { it.id == original.id }
        val correctionEntity = rawBefore.single { it.id == correction.id }
        val fork = correctionEntity.copy(id = "forked_correction", ordinal = 3, supersedesObservationId = original.id)
        expect(runCatching { dao.insertSetObservations(listOf(fork)) }.isFailure) {
            "Room accepted two observations superseding the same predecessor."
        }
        val cycle = listOf(originalEntity.copy(supersedesObservationId = correction.id), correctionEntity)
        expect(runCatching { ObservationSupersedingPolicy.current(cycle) }.isFailure) {
            "Correction-cycle validation did not reject a cycle."
        }

        val v2 = profileVersion(
            key = "grip_hold",
            version = 2,
            family = MetricFamily.LOADED_HOLD,
            metrics = v1.schema.metrics,
            lateralityMode = LateralityMode.UNILATERAL,
            entryBasis = EntryBasis.PER_SIDE,
            resistanceSemantics = ResistanceSemantics.EXTERNAL,
            recruitment = listOf(allocation(segment.id, segment.name, 0.8, 2)),
        )
        authoring.publishSuccessor(v2)
        expect(dao.observations(listOf("set_grip_0")).all { it.executionProfileVersionId == v1.id.value }) {
            "Publishing v2 rewrote historical v1 evidence."
        }
        expect(dao.recruitmentProfileVersions(listOf(v1.recruitment.id.value, v2.recruitment.id.value)).size == 2) {
            "Historical recruitment version was not recoverable."
        }
        expect(foreignKeyFailures(database).isEmpty()) { "Grip/replay flow left foreign-key violations." }
        return "LEFT 20 kg × 40 s and RIGHT 17.5 kg × 32 s remain asymmetric; A→B correction, raw deletion/replay, fork/cycle guards and v1 recovery passed."
    }

    private suspend fun verifyAssistanceDirection(database: MyMettleDatabase): String {
        seedUser(database)
        val model = ResistanceModel(CONTRACT, ResistanceSemantics.ASSISTANCE, 1.0, 0.0, 1.0)
        val version = profileVersion(
            key = "assisted_movement",
            family = MetricFamily.BODYWEIGHT_RESISTANCE,
            metrics = listOf(
                SchemaMetric(PerformanceMetric.ASSISTANCE, required = true),
                SchemaMetric(PerformanceMetric.REPETITIONS, required = true),
            ),
            lateralityMode = LateralityMode.BILATERAL_ONLY,
            entryBasis = EntryBasis.TOTAL,
            resistanceSemantics = ResistanceSemantics.ASSISTANCE,
        )
        authorProfile(database, "assisted_movement", "Assisted movement", "Machine assistance", version)
        seedCompletedSession(database, "assist", "assisted_movement", "Assisted movement", version, setCount = 2, bodyMassKg = 80.0)
        val workouts = RoomWorkoutRepository(database)
        listOf(30.0, 20.0).forEachIndexed { index, assistance ->
            workouts.saveObservation(
                "session_exercise_assist", "set_assist_$index", Laterality.BILATERAL,
                listOf(value(PerformanceMetric.ASSISTANCE, assistance, UnitId.KILOGRAM), value(PerformanceMetric.REPETITIONS, 8.0, UnitId.REPETITION)),
                bodyMassContextKg = 80.0,
            )
        }
        val stored = workouts.performanceSets("session_exercise_assist").flatMap { it.observations }
        expect(stored.map { observation ->
            observation.values.firstOrNull { it.metric == PerformanceMetric.ASSISTANCE }?.canonical?.value
        } == listOf(30.0, 20.0)) {
            "Assistance observations changed order or sign."
        }
        val easier = requireNotNull(ResistanceResolver.resolve(model, ResistanceInputs(bodyMassKg = 80.0, assistanceKg = 30.0)))
        val harder = requireNotNull(ResistanceResolver.resolve(model, ResistanceInputs(bodyMassKg = 80.0, assistanceKg = 20.0)))
        expect(harder.coordinate > easier.coordinate && close(easier.coordinate, 50.0) && close(harder.coordinate, 60.0)) {
            "Lower assistance did not produce greater resistance."
        }
        return "80 kg body mass × 8 reps: 30 kg assistance = 50 kg coordinate; 20 kg assistance = 60 kg, therefore lower assistance is harder."
    }

    private suspend fun verifyTreadmill(database: MyMettleDatabase): String {
        val values = listOf(
            value(PerformanceMetric.SPEED, 10.0, UnitId.MILES_PER_HOUR),
            value(PerformanceMetric.INCLINE_GRADE, 5.0, UnitId.PERCENT),
            value(PerformanceMetric.DURATION, 20.0, UnitId.MINUTE),
            value(PerformanceMetric.DISTANCE, 2.5, UnitId.MILE),
        )
        val current = persistConditioning(
            database = database,
            key = "treadmill",
            name = "Treadmill",
            family = MetricFamily.SPEED_DURATION,
            metrics = listOf(
                SchemaMetric(PerformanceMetric.SPEED, true, defaultUnit = UnitId.MILES_PER_HOUR),
                SchemaMetric(PerformanceMetric.INCLINE_GRADE, true, defaultUnit = UnitId.PERCENT),
                SchemaMetric(PerformanceMetric.DURATION, true, defaultUnit = UnitId.MINUTE),
                SchemaMetric(PerformanceMetric.DISTANCE, true, defaultUnit = UnitId.MILE),
            ),
            values = values,
        )
        val byMetric = current.values.associateBy { it.metric }
        expect(close(byMetric.getValue(PerformanceMetric.SPEED).canonical.value, 4.4704)) { "10 mph did not canonicalise to 4.4704 m/s." }
        expect(close(byMetric.getValue(PerformanceMetric.INCLINE_GRADE).canonical.value, 0.05)) { "5% grade did not canonicalise to 0.05 fraction." }
        expect(close(byMetric.getValue(PerformanceMetric.DURATION).canonical.value, 1200.0)) { "20 minutes did not canonicalise to 1200 seconds." }
        expect(close(byMetric.getValue(PerformanceMetric.DISTANCE).canonical.value, 4023.36)) { "2.5 miles did not canonicalise to 4023.36 metres." }
        values.forEach { entered ->
            val canonical = byMetric.getValue(entered.metric).canonical
            val roundTrip = UnitConverter.convert(canonical, entered.entered.unit)
            expect(close(roundTrip.value, entered.entered.value)) { "${entered.metric.storageValue} failed display-unit round trip." }
        }
        return "10 mph + 5% + 20 min + 2.5 mi persisted with deterministic m/s, fraction, seconds and metres, then round-tripped."
    }

    private suspend fun verifyStairMachine(database: MyMettleDatabase): String {
        val current = persistConditioning(
            database = database,
            key = "stair_machine",
            name = "Stair machine",
            family = MetricFamily.DEVICE_ORDINAL,
            metrics = listOf(
                SchemaMetric(PerformanceMetric.MACHINE_LEVEL, true, defaultUnit = UnitId.MACHINE_LEVEL, minimumCanonical = 1.0, maximumCanonical = 20.0, incrementCanonical = 1.0),
                SchemaMetric(PerformanceMetric.DURATION, true, defaultUnit = UnitId.MINUTE),
                SchemaMetric(PerformanceMetric.STEPS, true, defaultUnit = UnitId.STEP),
                SchemaMetric(PerformanceMetric.FLOORS, true, defaultUnit = UnitId.FLOOR),
            ),
            values = listOf(
                value(PerformanceMetric.MACHINE_LEVEL, 8.0, UnitId.MACHINE_LEVEL),
                value(PerformanceMetric.DURATION, 15.0, UnitId.MINUTE),
                value(PerformanceMetric.STEPS, 1200.0, UnitId.STEP),
                value(PerformanceMetric.FLOORS, 50.0, UnitId.FLOOR),
            ),
            resistanceSemantics = ResistanceSemantics.DEVICE_ORDINAL,
        )
        val level = requireNotNull(current.values.firstOrNull { it.metric == PerformanceMetric.MACHINE_LEVEL })
        expect(level.entered.unit == UnitId.MACHINE_LEVEL && level.canonical.unit == UnitId.MACHINE_LEVEL && level.canonical.value == 8.0) {
            "Machine level 8 did not remain a profile-local ordinal."
        }
        expect(current.values.none { it.metric == PerformanceMetric.EXTERNAL_LOAD || it.canonical.unit == UnitId.KILOGRAM }) {
            "Ordinal evidence was relabelled as physical load."
        }
        expect(runCatching { UnitConverter.convert(level.entered, UnitId.KILOGRAM) }.isFailure) {
            "Unit conversion accepted machine level → kg."
        }
        return "Level 8 + 15 min + 1200 steps + 50 floors persisted; level remains machine_level and cannot convert to kg."
    }

    private suspend fun verifyPowerDuration(database: MyMettleDatabase): String {
        val current = persistConditioning(
            database = database,
            key = "power_duration",
            name = "Power interval",
            family = MetricFamily.POWER_DURATION,
            metrics = listOf(
                SchemaMetric(PerformanceMetric.POWER, true, defaultUnit = UnitId.WATT),
                SchemaMetric(PerformanceMetric.DURATION, true, defaultUnit = UnitId.SECOND),
            ),
            values = listOf(
                value(PerformanceMetric.POWER, 250.0, UnitId.WATT),
                value(PerformanceMetric.DURATION, 300.0, UnitId.SECOND),
            ),
        )
        expect(current.values.map { it.metric }.toSet() == setOf(PerformanceMetric.POWER, PerformanceMetric.DURATION)) {
            "Power interval acquired an unrelated metric."
        }
        val inference = RoomInferenceRepository(database).latestSnapshot()
            ?: error("Power-duration inference snapshot was not persisted.")
        expect(inference.stimulusEstimates.isEmpty()) { "Power-duration evidence was converted to hypertrophy stimulus." }
        expect(inference.exerciseTranslationStates.single().anchors.map { it.metric }.toSet() == setOf(PerformanceMetric.POWER, PerformanceMetric.DURATION)) {
            "Power-duration same-profile anchors were incomplete."
        }
        return "250 W × 300 s persisted and anchored per metric; no cardio-to-resistance stimulus conversion was created."
    }

    private suspend fun persistConditioning(
        database: MyMettleDatabase,
        key: String,
        name: String,
        family: MetricFamily,
        metrics: List<SchemaMetric>,
        values: List<PerformanceMetricValue>,
        resistanceSemantics: ResistanceSemantics = ResistanceSemantics.NONE,
    ): dev.kian.mymettle.domain.performance.PerformanceObservation {
        seedUser(database)
        val version = profileVersion(
            key = key,
            family = family,
            metrics = metrics,
            lateralityMode = LateralityMode.NOT_APPLICABLE,
            entryBasis = EntryBasis.TOTAL,
            resistanceSemantics = resistanceSemantics,
        )
        authorProfile(database, key, name, "Developer acceptance", version)
        seedCompletedSession(database, key, key, name, version)
        val workouts = RoomWorkoutRepository(database)
        val saved = workouts.saveObservation("session_exercise_$key", "set_${key}_0", Laterality.NOT_APPLICABLE, values)
        val history = HistoryRepository(database).recent().single().exercises.single().sets.single().observations.single()
        expect(history == saved) { "History read model changed $name observation semantics." }
        val inference = RoomInferenceRepository(database).recomputeFromRawHistory()
        expect(inference.exerciseTranslationStates.single().laterality == Laterality.NOT_APPLICABLE) {
            "$name inference lost not-applicable laterality."
        }
        return history
    }

    private suspend fun authorProfile(
        database: MyMettleDatabase,
        exerciseId: String,
        exerciseName: String,
        profileName: String,
        version: ExecutionProfileVersion,
    ) {
        ExecutionProfileAuthoringRepository(database).createProfile(
            ExecutionProfileAuthoringRequest(ExerciseId(exerciseId), exerciseName, profileName, true, version),
        )
    }

    private suspend fun seedUser(database: MyMettleDatabase) {
        database.workoutDao().upsertProfile(UserProfileEntity("verification_user", "N-BIO-6 verifier", "kg", "none", 1, NOW, NOW))
    }

    private suspend fun seedCompletedSession(
        database: MyMettleDatabase,
        key: String,
        exerciseId: String,
        exerciseName: String,
        version: ExecutionProfileVersion,
        setCount: Int = 1,
        bodyMassKg: Double? = null,
    ) {
        val dao = database.workoutDao()
        dao.upsertSessions(
            listOf(
                SessionEntity(
                    id = "session_$key",
                    cycleId = "verification_cycle",
                    daySymbol = "dev",
                    mode = "A",
                    routineVersionId = "verification_routine",
                    status = "completed",
                    startedAt = NOW,
                    completedAt = COMPLETE,
                    editedAt = null,
                    discardedAt = null,
                    excludedFromInsights = false,
                    bodyweightSnapshotKg = bodyMassKg,
                    healthExportState = "not_requested",
                    healthClientRecordId = null,
                ),
            ),
        )
        dao.upsertSessionExercises(
            listOf(
                SessionExerciseEntity(
                    id = "session_exercise_$key",
                    sessionId = "session_$key",
                    position = 0,
                    exerciseId = exerciseId,
                    slotId = "slot_$key",
                    exerciseNameSnapshot = exerciseName,
                    importanceSnapshot = "principal",
                    executionProfileId = version.executionProfileId.value,
                    executionProfileVersionId = version.id.value,
                    executionProfileNameSnapshot = "Developer acceptance",
                    prescriptionMode = "A",
                    prescriptionIncluded = true,
                    restSeconds = 60,
                    generatedByModelVersion = CONTRACT,
                    deferToAnd = false,
                    status = "completed",
                    note = null,
                    startedAt = NOW,
                    completedAt = COMPLETE,
                    movementReason = SOURCE,
                    substitutedFromExerciseId = null,
                ),
            ),
        )
        dao.upsertSets(
            List(setCount) { index -> SetRecordEntity("set_${key}_$index", "session_exercise_$key", index, null, false, "prescribed", NOW) },
        )
    }

    private fun profileVersion(
        key: String,
        version: Int = 1,
        family: MetricFamily,
        metrics: List<SchemaMetric>,
        lateralityMode: LateralityMode,
        entryBasis: EntryBasis,
        resistanceSemantics: ResistanceSemantics,
        recruitment: List<RecruitmentAllocation> = emptyList(),
    ): ExecutionProfileVersion {
        val effectiveAt = if (version == 1) NOW else SUCCESSOR_TIME
        val bodyweightCoefficient = if (resistanceSemantics in setOf(ResistanceSemantics.BODYWEIGHT, ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL, ResistanceSemantics.ASSISTANCE)) 1.0 else 0.0
        val externalCoefficient = if (resistanceSemantics in setOf(ResistanceSemantics.EXTERNAL, ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL)) 1.0 else 0.0
        val assistanceCoefficient = if (resistanceSemantics == ResistanceSemantics.ASSISTANCE) 1.0 else 0.0
        return ExecutionProfileVersion(
            id = ExecutionProfileVersionId("profile_$key:v$version"),
            executionProfileId = ExecutionProfileId("profile_$key"),
            version = version,
            metricFamily = family,
            schema = PerformanceSchema("schema_$key:v$version", version, family, metrics, SOURCE),
            equipment = EquipmentProfile(key, "developer_acceptance"),
            resistanceModel = ResistanceModel("resistance-$key-v$version", resistanceSemantics, bodyweightCoefficient, externalCoefficient, assistanceCoefficient),
            entryBasis = entryBasis,
            implementCount = if (entryBasis == EntryBasis.PER_HAND) 2 else if (entryBasis == EntryBasis.PER_SIDE) 1 else null,
            lateralityMode = lateralityMode,
            romClass = if (family in setOf(MetricFamily.LOADED_HOLD, MetricFamily.DURATION_ONLY)) "isometric" else null,
            techniqueClass = SOURCE,
            resistanceCurveClass = null,
            movementPattern = key,
            jointActions = emptyList(),
            kineticChain = null,
            contractionType = if (family in setOf(MetricFamily.LOADED_HOLD, MetricFamily.DURATION_ONLY)) "isometric" else null,
            gripSupportConstraints = emptyList(),
            recruitment = RecruitmentProfile(
                id = RecruitmentProfileVersionId("recruitment_$key:v$version"),
                version = version,
                allocations = recruitment,
                createdAt = effectiveAt,
                effectiveAt = effectiveAt,
                supersededAt = null,
                provenance = SOURCE,
                modelVersion = "recruitment-$key-v$version",
            ),
            createdAt = effectiveAt,
            effectiveAt = effectiveAt,
            supersededAt = null,
            provenance = SOURCE,
            modelVersion = "execution-$key-v$version",
        )
    }

    private fun allocation(segmentId: String, name: String, weighting: Double, version: Int) = RecruitmentAllocation(
        segmentId = MuscleSegmentId(segmentId),
        segmentName = name,
        role = RecruitmentRole.PRIME,
        weighting = weighting,
        confidence = 0.8,
        source = RecruitmentSource(SOURCE, null),
        applicableRom = "isometric",
        applicableTechnique = SOURCE,
        resistanceCurveClass = null,
        modelVersion = "recruitment-grip-v$version",
    )

    private fun value(metric: PerformanceMetric, amount: Double, unit: UnitId) =
        PerformanceMetricValue(metric, Quantity(amount, unit))

    private fun foreignKeyFailures(database: MyMettleDatabase): List<String> =
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add("${cursor.getString(0)} row ${cursor.getLong(1)} → ${cursor.getString(2)}")
                }
            }
        }

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    private fun Throwable.conciseMessage(): String = buildString {
        append(this@conciseMessage::class.java.simpleName)
        this@conciseMessage.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
        cause?.takeIf { it !== this@conciseMessage }?.message?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.6f".format(value).trimEnd('0').trimEnd('.')

    private fun close(first: Double, second: Double): Boolean = kotlin.math.abs(first - second) <= 1e-8 * maxOf(1.0, kotlin.math.abs(second))

    private fun expect(condition: Boolean, message: () -> String) {
        if (!condition) throw NBio6VerificationException(message())
    }

    private companion object {
        const val CONTRACT = "n-bio-6-device-verifier-v1"
        const val SOURCE = "n-bio-6-device-verification"
        const val NOW = "2026-08-24T10:00:00Z"
        const val COMPLETE = "2026-08-24T10:30:00Z"
        const val SUCCESSOR_TIME = "2026-08-25T00:00:00Z"
    }
}

private class NBio6VerificationException(message: String) : IllegalStateException(message)
