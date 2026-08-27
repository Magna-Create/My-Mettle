package dev.kian.mymettle.data.migration

import android.content.Context
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
        if (json.length > MAX_BACKUP_CHARACTERS) {
            throw LegacyImportException("Lite backup is too large to import safely.")
        }
        val snapshot = LegacyV6BackupReader.read(json)
        LegacyTranslationContract.requireActiveRecruitment(snapshot)
        val dao = database.workoutDao()
        if (dao.profileCount() > 0) {
            throw LegacyImportException(
                "Native My Mettle already contains user data. Legacy import is intentionally allowed only into a fresh native database.",
            )
        }

        val recruitment = LegacyRecruitmentResolver(database).resolve(snapshot.translatedRecruitment)
        val targets = LegacyTargetProjector.project(snapshot, recruitment)
        val constraints = LegacyProgrammeConstraintProjector.project(
            routineSlots = snapshot.routineSlots,
            prescriptions = snapshot.modePrescriptions,
        )
        val decodedPhotos = photoImporter.import(snapshot.setupPhotos)
        try {
            settingsStore.importLegacyRestTimer(snapshot.restTimerSettings)
            LegacySnapshotPersister.persist(
                database = database,
                snapshot = snapshot,
                recruitment = recruitment,
                programmeTargets = targets.programmeTargets,
                programmeConstraints = constraints,
                sessionTargets = targets.sessionTargets,
                sessionExerciseTargets = targets.sessionExerciseTargets,
                setupMedia = decodedPhotos.media,
            )
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

    private companion object {
        const val MAX_BACKUP_CHARACTERS = 64 * 1024 * 1024
    }
}
