package dev.kian.mymettle.data.migration

import android.content.Context
import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.settings.SettingsStore

data class LegacyImportReport(
    val exercises: Int,
    val routineVersions: Int,
    val sessions: Int,
    val sets: Int,
    val setupPhotos: Int,
    val muscleAllocations: Int,
    val programmeTargets: Int,
)

class LegacyV6Importer(
    context: Context,
    private val database: MyMettleDatabase,
    private val settingsStore: SettingsStore = SettingsStore(context),
    private val photoImporter: LegacySetupPhotoImporter = LegacySetupPhotoImporter(context.applicationContext),
) {
    suspend fun importJson(json: String): LegacyImportReport {
        val snapshot = LegacyV6BackupReader.read(json)
        val dao = database.workoutDao()
        if (dao.profileCount() > 0) {
            throw LegacyImportException(
                "Native My Mettle already contains user data. Legacy import is intentionally allowed only into a fresh native database.",
            )
        }

        val recruitment = LegacyRecruitmentResolver(database).resolve(snapshot.legacyRecruitment)
        val targets = LegacyTargetProjector.project(snapshot, recruitment)
        val constraints = LegacyProgrammeConstraintProjector.project(
            routineSlots = snapshot.routineSlots,
            prescriptions = snapshot.modePrescriptions,
        )
        val decodedPhotos = photoImporter.import(snapshot.setupPhotos)
        try {
            settingsStore.importLegacyRestTimer(snapshot.restTimerSettings)
            database.withTransaction {
                dao.upsertProfile(snapshot.profile)
                dao.upsertBodyMeasurements(snapshot.bodyMeasurements)
                dao.upsertExercises(snapshot.exercises)
                dao.upsertExerciseMemory(snapshot.exerciseMemory)
                dao.upsertExecutionProfiles(snapshot.executionProfiles)
                dao.upsertPerformanceSchemas(snapshot.performanceSchemas)
                dao.upsertPerformanceSchemaMetrics(snapshot.performanceSchemaMetrics)
                dao.upsertRecruitmentProfileVersions(snapshot.recruitmentProfileVersions)
                dao.upsertExecutionProfileVersions(snapshot.executionProfileVersions)
                dao.upsertCues(snapshot.cues)
                dao.upsertCommonMistakes(snapshot.commonMistakes)
                dao.upsertSubstitutions(snapshot.substitutions)
                dao.upsertRecruitmentAllocations(recruitment)
                dao.upsertSetupMedia(decodedPhotos.media)
                dao.upsertRoutineVersions(snapshot.routineVersions)
                dao.upsertRoutineSlots(snapshot.routineSlots)
                dao.upsertRoutineMetricTargets(snapshot.routineMetricTargets)
                dao.upsertProgrammeTargets(targets.programmeTargets)
                dao.upsertProgrammeModeConstraints(constraints)
                dao.upsertTrainingCycles(snapshot.trainingCycles)
                dao.upsertCompletedDays(snapshot.completedDays)
                dao.upsertSessions(snapshot.sessions)
                dao.upsertSessionTargets(targets.sessionTargets)
                dao.upsertSessionExercises(snapshot.sessionExercises)
                dao.upsertSessionExerciseTargets(targets.sessionExerciseTargets)
                dao.upsertSessionSetPrescriptions(snapshot.sessionSetPrescriptions)
                dao.upsertSessionMetricTargets(snapshot.sessionMetricTargets)
                dao.upsertSets(snapshot.sets)
                dao.upsertSetObservations(snapshot.setObservations)
                dao.upsertSetMetricValues(snapshot.setMetricValues)
                dao.upsertSetDraftMetricValues(snapshot.setDraftMetricValues)
                dao.upsertReflections(snapshot.reflections)
                dao.upsertHealthObservations(snapshot.healthObservations)
                dao.upsertHealthIntegrationState(snapshot.healthIntegration)
                dao.upsertAppState(snapshot.appState)
            }
        } catch (error: Throwable) {
            photoImporter.cleanup(decodedPhotos.createdFiles)
            throw error
        }

        return LegacyImportReport(
            exercises = snapshot.exercises.size,
            routineVersions = snapshot.routineVersions.size,
            sessions = snapshot.sessions.size,
            sets = snapshot.sets.size,
            setupPhotos = decodedPhotos.media.size,
            muscleAllocations = recruitment.size,
            programmeTargets = targets.programmeTargets.size,
        )
    }

}
