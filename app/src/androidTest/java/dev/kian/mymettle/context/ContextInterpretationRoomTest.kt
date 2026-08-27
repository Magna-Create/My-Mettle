package dev.kian.mymettle.context

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.domain.context.ContextValue
import dev.kian.mymettle.domain.context.InferenceEligibility
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextInterpretationRoomTest {
    private lateinit var database: MyMettleDatabase
    private lateinit var repository: ContextInterpretationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        database.openHelper.writableDatabase
        repository = ContextInterpretationRepository(database)
        insertRawFixture()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rawReviewsAndNBio6EvidenceSurviveAnnotationDeletion() = runBlocking {
        val sessionSource = sessionSource(SESSION_NOTE_A, T0)
        val exerciseSource = exerciseSource(EXERCISE_NOTE_A, T0)
        val rules = RulesNoteInterpreter()

        val sessionRun = repository.persist(
            sessionSource,
            rules.interpret(NoteInterpretationRequest(sessionSource.text, sessionSource.scope)),
            InterpretationExecutionOutcome.SUCCESS,
            createdAt = Instant.parse("2026-08-27T10:01:00Z"),
        )
        val exerciseRun = repository.persist(
            exerciseSource,
            rules.interpret(NoteInterpretationRequest(exerciseSource.text, exerciseSource.scope, exerciseSource.exerciseName)),
            InterpretationExecutionOutcome.SUCCESS,
            createdAt = Instant.parse("2026-08-27T10:02:00Z"),
        )

        assertTrue(database.contextDao().annotations(sessionRun.id).isNotEmpty())
        assertTrue(database.contextDao().annotations(exerciseRun.id).isNotEmpty())
        assertEquals(1, count("set_observation"))
        assertEquals(1, count("set_metric_value"))

        assertEquals(2, repository.deleteAllDerivedInterpretations())

        assertEquals(SESSION_NOTE_A, scalarText("SELECT note FROM session_review WHERE sessionId = 'session-1'"))
        assertEquals(EXERCISE_NOTE_A, scalarText("SELECT note FROM exercise_reflection WHERE sessionExerciseId = 'session-exercise-1'"))
        assertEquals(1, count("set_observation"))
        assertEquals(1, count("set_metric_value"))
        assertEquals(0, count("note_interpretation_run"))
        assertEquals(0, count("context_annotation"))
        assertForeignKeysClean()
    }

    @Test
    fun reinterpretationIsImmutableAndEditedTextInvalidatesCurrentSelection() = runBlocking {
        val sourceA = sessionSource(SESSION_NOTE_A, T0)
        val rules = RulesNoteInterpreter()
        val resultV1 = rules.interpret(NoteInterpretationRequest(sourceA.text, sourceA.scope))
        val runV1 = repository.persist(
            sourceA,
            resultV1,
            InterpretationExecutionOutcome.SUCCESS,
            createdAt = Instant.parse("2026-08-27T10:01:00Z"),
        )
        val runV2 = repository.persist(
            sourceA,
            resultV1.copy(implementationVersion = "rules-context-v2-test"),
            InterpretationExecutionOutcome.SUCCESS,
            createdAt = Instant.parse("2026-08-27T10:02:00Z"),
        )

        assertNotEquals(runV1.id, runV2.id)
        assertEquals(2, database.contextDao().sessionReviewRuns("session-1").size)
        assertEquals(runV2.id, repository.current(sourceA)?.run?.id)

        val newUpdatedAt = "2026-08-27T10:03:00Z"
        database.openHelper.writableDatabase.execSQL(
            "UPDATE session_review SET note = ?, updatedAt = ? WHERE sessionId = 'session-1'",
            arrayOf(SESSION_NOTE_B, newUpdatedAt),
        )
        val sourceB = sessionSource(SESSION_NOTE_B, newUpdatedAt)
        assertNull(repository.current(sourceB))
        assertNotEquals(RawNoteHash.sha256(sourceA.text), RawNoteHash.sha256(sourceB.text))

        val resultB = rules.interpret(NoteInterpretationRequest(sourceB.text, sourceB.scope))
        val runB = repository.persist(
            sourceB,
            resultB,
            InterpretationExecutionOutcome.SUCCESS,
            createdAt = Instant.parse("2026-08-27T10:04:00Z"),
        )
        val current = assertNotNull(repository.current(sourceB))
        assertEquals(runB.id, current.run.id)
        assertEquals(RawNoteHash.sha256(SESSION_NOTE_B), current.run.sourceTextHash)
        assertEquals(3, database.contextDao().sessionReviewRuns("session-1").size)
        assertEquals(ContextValue.NumberValue(7.0, "h"), current.annotations.single().value)
        assertForeignKeysClean()
    }

    @Test
    fun uxOnlyAndCandidateCovariateRemainStructurallySeparated() = runBlocking {
        val rules = RulesNoteInterpreter()
        val sessionSource = sessionSource(SESSION_NOTE_A, T0)
        repository.persist(
            sessionSource,
            rules.interpret(NoteInterpretationRequest(sessionSource.text, sessionSource.scope)),
            InterpretationExecutionOutcome.SUCCESS,
            createdAt = Instant.parse("2026-08-27T10:01:00Z"),
        )
        val sessionView = repository.contextEvidenceView(sessionSource)
        assertEquals(2, sessionView.items.size)
        assertTrue(sessionView.items.all { it.eligibility == InferenceEligibility.CANDIDATE_COVARIATE })

        val exerciseSource = exerciseSource(EXERCISE_NOTE_A, T0)
        repository.persist(
            exerciseSource,
            rules.interpret(NoteInterpretationRequest(exerciseSource.text, exerciseSource.scope, exerciseSource.exerciseName)),
            InterpretationExecutionOutcome.SUCCESS,
            createdAt = Instant.parse("2026-08-27T10:02:00Z"),
        )
        assertTrue(repository.contextEvidenceView(exerciseSource).items.isEmpty())
        assertEquals("ux_only", dev.kian.mymettle.domain.context.ContextTagRegistry.V1
            .requireDefinition(dev.kian.mymettle.domain.context.ContextTagId("NEXT_SESSION_ACTION"))
            .inferenceEligibility.storageValue)
    }

    @Test
    fun promptUnavailableFallsBackToRulesWithoutTouchingRawNote() = runBlocking {
        val caps = NanoRuntimeCapabilities(
            promptApiStatus = PromptApiStatus.DOWNLOADABLE,
            structuredOutputAvailable = false,
            systemInstructionAvailable = false,
        )
        val fakeNano = NoteInterpreter {
            throw NanoUnavailableException(caps, "Prompt API is downloadable; Save must not download a model.")
        }
        val coordinator = ContextInterpretationCoordinator(
            database = database,
            nano = fakeNano,
            nanoCapabilityProbe = { caps },
        )
        val source = sessionSource(SESSION_NOTE_A, T0)

        assertTrue(coordinator.interpretSaved(source).isSuccess)

        val current = assertNotNull(repository.current(source))
        assertEquals(InterpreterKind.RULES.storageValue, current.run.interpreterKind)
        assertEquals(PromptApiStatus.DOWNLOADABLE.storageValue, current.run.promptApiStatus)
        assertTrue(current.run.fallbackReason!!.contains("must not download"))
        assertEquals(SESSION_NOTE_A, scalarText("SELECT note FROM session_review WHERE sessionId = 'session-1'"))
        assertEquals(caps, coordinator.capabilities())
    }

    @Test
    fun structuredOutputUnavailableUsesRulesInsteadOfLooseModelJson() = runBlocking {
        val caps = NanoRuntimeCapabilities(
            promptApiStatus = PromptApiStatus.AVAILABLE,
            structuredOutputAvailable = false,
            systemInstructionAvailable = true,
            baseModelName = "fixture-model",
        )
        val fakeNano = NoteInterpreter {
            throw NanoUnavailableException(
                caps,
                "Prompt API is available but Structured Output is unavailable; free-form model JSON is intentionally not accepted.",
            )
        }
        val coordinator = ContextInterpretationCoordinator(database, nano = fakeNano, nanoCapabilityProbe = { caps })
        val source = sessionSource(SESSION_NOTE_A, T0)

        assertTrue(coordinator.interpretSaved(source).isSuccess)
        val current = assertNotNull(repository.current(source))
        assertEquals(InterpreterKind.RULES.storageValue, current.run.interpreterKind)
        assertTrue(current.run.fallbackReason!!.contains("free-form model JSON is intentionally not accepted"))
        assertEquals("fixture-model", current.run.actualBaseModelName)
    }

    @Test
    fun interpreterExceptionsFallThroughToNoOpAndNeverRollBackRawText() = runBlocking {
        val fakeNano = NoteInterpreter { error("nano fixture failure") }
        val brokenRules = NoteInterpreter { error("rules fixture failure") }
        val coordinator = ContextInterpretationCoordinator(
            database = database,
            nano = fakeNano,
            nanoCapabilityProbe = { NanoRuntimeCapabilities(PromptApiStatus.ERROR) },
            rules = brokenRules,
        )
        val source = sessionSource(SESSION_NOTE_A, T0)

        assertTrue(coordinator.interpretSaved(source).isSuccess)
        val current = assertNotNull(repository.current(source))
        assertEquals(InterpreterKind.NO_OP.storageValue, current.run.interpreterKind)
        assertTrue(current.annotations.isEmpty())
        assertTrue(current.run.fallbackReason!!.contains("Rules failed"))
        assertEquals(SESSION_NOTE_A, scalarText("SELECT note FROM session_review WHERE sessionId = 'session-1'"))
    }

    private fun insertRawFixture() {
        val db = database.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO session " +
                "(id, cycleId, daySymbol, mode, routineVersionId, status, startedAt, completedAt, editedAt, discardedAt, excludedFromInsights, bodyweightSnapshotKg, healthExportState, healthClientRecordId) " +
                "VALUES ('session-1', 'cycle-fixture', 'ψ', 'B', 'routine-fixture', 'completed', ?, ?, NULL, NULL, 0, 70.0, NULL, NULL)",
            arrayOf(T0, T0),
        )
        db.execSQL(
            "INSERT INTO session_review (sessionId, exerciseOrder, organisation, pacing, delayImpact, note, recordedAt, updatedAt) " +
                "VALUES ('session-1', NULL, NULL, NULL, NULL, ?, ?, ?)",
            arrayOf(SESSION_NOTE_A, T0, T0),
        )
        db.execSQL(
            "INSERT INTO exercise (id, name, archived, essentialCue, createdAt, updatedAt) " +
                "VALUES ('exercise-1', 'Cable row', 0, NULL, ?, ?)",
            arrayOf(T0, T0),
        )
        db.execSQL(
            "INSERT INTO exercise_execution_profile (id, exerciseId, name, isDefault, archived) " +
                "VALUES ('profile-1', 'exercise-1', 'Default', 1, 0)",
        )
        db.execSQL(
            "INSERT INTO performance_schema (id, version, metricFamily, createdAt, provenance) " +
                "VALUES ('schema-1', 1, 'dynamic_resistance', ?, 'fixture')",
            arrayOf(T0),
        )
        db.execSQL(
            "INSERT INTO recruitment_profile_version " +
                "(id, executionProfileId, version, createdAt, effectiveAt, supersededAt, provenance, modelVersion) " +
                "VALUES ('recruitment-1', 'profile-1', 1, ?, ?, NULL, 'fixture', 'fixture-v1')",
            arrayOf(T0, T0),
        )
        db.execSQL(
            "INSERT INTO execution_profile_version " +
                "(id, executionProfileId, version, metricFamily, performanceSchemaId, equipmentIdentity, equipmentType, resistanceSemantics, resistanceModelVersion, bodyweightCoefficient, externalLoadCoefficient, assistanceCoefficient, entryBasis, implementCount, lateralityMode, romClass, techniqueClass, resistanceCurveClass, movementPattern, jointActionsJson, kineticChain, contractionType, gripSupportConstraintsJson, recruitmentProfileVersionId, createdAt, effectiveAt, supersededAt, provenance, modelVersion) " +
                "VALUES ('profile-version-1', 'profile-1', 1, 'dynamic_resistance', 'schema-1', NULL, 'cable', 'external', 'fixture-v1', 0.0, 1.0, 0.0, 'total', 1, 'bilateral_only', NULL, NULL, NULL, 'row', NULL, NULL, 'dynamic', NULL, 'recruitment-1', ?, ?, NULL, 'fixture', 'fixture-v1')",
            arrayOf(T0, T0),
        )
        db.execSQL(
            "INSERT INTO session_exercise " +
                "(id, sessionId, position, exerciseId, slotId, exerciseNameSnapshot, importanceSnapshot, executionProfileId, executionProfileVersionId, executionProfileNameSnapshot, prescriptionMode, prescriptionIncluded, restSeconds, generatedByModelVersion, deferToAnd, status, note, startedAt, completedAt, movementReason, substitutedFromExerciseId) " +
                "VALUES ('session-exercise-1', 'session-1', 0, 'exercise-1', 'slot-fixture', 'Cable row', 'normal', 'profile-1', 'profile-version-1', 'Default', 'B', 1, 120, 'fixture', 0, 'completed', NULL, ?, ?, 'fixture', NULL)",
            arrayOf(T0, T0),
        )
        db.execSQL(
            "INSERT INTO exercise_reflection " +
                "(sessionExerciseId, targetMuscleEngagement, execution, enjoyment, comfort, note, recordedAt, updatedAt) " +
                "VALUES ('session-exercise-1', '', '', '', '', ?, ?, ?)",
            arrayOf(EXERCISE_NOTE_A, T0, T0),
        )
        db.execSQL(
            "INSERT INTO set_record (id, sessionExerciseId, setIndex, note, warmUp, kind, createdAt) " +
                "VALUES ('set-1', 'session-exercise-1', 0, NULL, 0, 'working', ?)",
            arrayOf(T0),
        )
        db.execSQL(
            "INSERT INTO set_observation " +
                "(id, setRecordId, executionProfileVersionId, ordinal, side, completedAt, recordedAt, source, bodyMassContextKg, bodyMassContextSource, supersedesObservationId, startedAtEpochSecond, startedAtNano, endedAtEpochSecond, endedAtNano, timingQuality, sourceZoneOffsetMinutes) " +
                "VALUES ('observation-1', 'set-1', 'profile-version-1', 0, 'bilateral', ?, ?, 'fixture', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'completion_only', NULL)",
            arrayOf(T0, T0),
        )
        db.execSQL(
            "INSERT INTO set_metric_value " +
                "(observationId, metric, enteredValue, enteredUnit, canonicalValue, canonicalUnit, acquisitionMethod, evidenceGranularity, semanticRole) " +
                "VALUES ('observation-1', 'repetitions', 8.0, 'rep', 8.0, 'rep', 'manual', 'summary', 'performance_output')",
        )
        assertForeignKeysClean()
    }

    private fun sessionSource(note: String, updatedAt: String): CanonicalNoteSource = requireNotNull(
        CanonicalNoteSource.from(
            SessionReviewEntity(
                sessionId = "session-1",
                exerciseOrder = null,
                organisation = null,
                pacing = null,
                delayImpact = null,
                note = note,
                recordedAt = T0,
                updatedAt = updatedAt,
            ),
        ),
    )

    private fun exerciseSource(note: String, updatedAt: String): CanonicalNoteSource = requireNotNull(
        CanonicalNoteSource.from(
            ExerciseReflectionEntity(
                sessionExerciseId = "session-exercise-1",
                targetMuscleEngagement = "",
                execution = "",
                enjoyment = "",
                comfort = "",
                note = note,
                recordedAt = T0,
                updatedAt = updatedAt,
            ),
            exerciseName = "Cable row",
        ),
    )

    private fun count(table: String): Int = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM \"$table\"")
        .use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun scalarText(sql: String): String? = database.openHelper.writableDatabase.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        if (cursor.isNull(0)) null else cursor.getString(0)
    }

    private fun assertForeignKeysClean() {
        assertTrue(database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
    }

    companion object {
        private const val T0 = "2026-08-27T10:00:00Z"
        private const val SESSION_NOTE_A = "I slept about 4 hours and feel wrecked today"
        private const val SESSION_NOTE_B = "I slept 7 hours"
        private const val EXERCISE_NOTE_A = "remember to move the seat one notch lower next time"
    }
}
