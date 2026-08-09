package dev.kian.mymettle.data.migration

import android.content.Context
import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ModePrescriptionEntity
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

        // Legacy routine versions intentionally reuse stable logical slot ids. The backup reader
        // emits slots and their A/B/C prescriptions in the same nested order, so attach the
        // containing routine-version id before Room writes the immutable history.
        val versionedPrescriptions = versionModePrescriptions(snapshot)
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
                dao.upsertModePrescriptions(versionedPrescriptions)
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

    private fun versionModePrescriptions(snapshot: LegacyImportSnapshot): List<ModePrescriptionEntity> {
        val modesPerSlot = 3
        val expected = snapshot.routineSlots.size * modesPerSlot
        if (snapshot.modePrescriptions.size != expected) {
            throw LegacyImportException(
                "Lite Legacy routine history is malformed: expected $expected mode prescriptions for ${snapshot.routineSlots.size} slot occurrences, received ${snapshot.modePrescriptions.size}.",
            )
        }

        return snapshot.routineSlots.flatMapIndexed { slotIndex, slot ->
            val start = slotIndex * modesPerSlot
            snapshot.modePrescriptions.subList(start, start + modesPerSlot).map { prescription ->
                if (prescription.slotId != slot.id) {
                    throw LegacyImportException(
                        "Lite Legacy routine history lost slot/prescription ordering at slot ${slot.id}.",
                    )
                }
                prescription.copy(routineVersionId = slot.routineVersionId)
            }
        }
    }
}
