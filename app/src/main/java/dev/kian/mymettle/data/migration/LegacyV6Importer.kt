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

        val decodedPhotos = photoImporter.import(snapshot.setupPhotos)
        try {
            settingsStore.importLegacyRestTimer(snapshot.restTimerSettings)
            database.withTransaction {
                dao.upsertProfile(snapshot.profile)
                dao.upsertBodyMeasurements(snapshot.bodyMeasurements)
                dao.upsertExercises(snapshot.exercises)
                dao.upsertExerciseMemory(snapshot.exerciseMemory)
                dao.upsertTargetMuscles(snapshot.targetMuscles)
                dao.upsertCues(snapshot.cues)
                dao.upsertCommonMistakes(snapshot.commonMistakes)
                dao.upsertSubstitutions(snapshot.substitutions)
                dao.upsertMuscleLoads(snapshot.muscleLoads)
                dao.upsertSetupMedia(decodedPhotos.media)
                dao.upsertRoutineVersions(snapshot.routineVersions)
                dao.upsertRoutineSlots(snapshot.routineSlots)
                dao.upsertModePrescriptions(snapshot.modePrescriptions)
                dao.upsertTrainingCycles(snapshot.trainingCycles)
                dao.upsertCompletedDays(snapshot.completedDays)
                dao.upsertSessions(snapshot.sessions)
                dao.upsertSessionExercises(snapshot.sessionExercises)
                dao.upsertSets(snapshot.sets)
                dao.upsertReflections(snapshot.reflections)
                dao.upsertExperiments(snapshot.experiments)
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
            muscleAllocations = snapshot.muscleLoads.size,
        )
    }
}
