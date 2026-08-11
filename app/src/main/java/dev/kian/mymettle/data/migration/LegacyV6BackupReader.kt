package dev.kian.mymettle.data.migration

import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.BodyMeasurementEntity
import dev.kian.mymettle.data.local.entity.CycleCompletedDayEntity
import dev.kian.mymettle.data.local.entity.ExerciseCommonMistakeEntity
import dev.kian.mymettle.data.local.entity.ExerciseCueEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.ExerciseMemoryEntity
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.ExerciseSubstitutionEntity
import dev.kian.mymettle.data.local.entity.HealthIntegrationStateEntity
import dev.kian.mymettle.data.local.entity.HealthObservationEntity
import dev.kian.mymettle.data.local.entity.ModePrescriptionEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import dev.kian.mymettle.data.local.entity.UserProfileEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class LegacyImportException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

object LegacyV6BackupReader {
    private const val SCHEMA_VERSION = 6
    private const val BACKUP_FORMAT = "my-mettle-backup"
    private const val BACKUP_EXPORT_VERSION = 1
    private const val BACKUP_SOURCE = "my-mettle-lite-legacy"

    fun read(json: String): LegacyImportSnapshot {
        try {
            val root = JSONObject(json)
            val envelope = root.optString("format") == BACKUP_FORMAT
            val exportedAt: String?
            val database: JSONObject

            if (envelope) {
                requireImport(root.optInt("exportVersion", -1) == BACKUP_EXPORT_VERSION) {
                    "Unsupported Lite Legacy backup export version."
                }
                requireImport(root.optString("source") == BACKUP_SOURCE) {
                    "Backup source is not My Mettle Lite Legacy."
                }
                exportedAt = root.stringOrNull("exportedAt")
                database = root.objectRequired("database")
            } else {
                exportedAt = null
                database = root
            }

            requireImport(database.optInt("schemaVersion", -1) == SCHEMA_VERSION) {
                "This importer expects Lite Legacy schema $SCHEMA_VERSION. Open an older backup in Lite Legacy and export it again before importing."
            }

            val profileJson = database.objectRequired("profile")
            val profile = UserProfileEntity(
                id = profileJson.stringRequired("id"),
                displayName = profileJson.stringRequired("displayName"),
                units = profileJson.stringRequired("units"),
                dietaryPreference = profileJson.optString("dietaryPreference", "vegetarian"),
                cycleStartDay = profileJson.optInt("cycleStartDay", 1),
                createdAt = profileJson.stringRequired("createdAt"),
                updatedAt = profileJson.stringRequired("updatedAt"),
            )

            val restJson = database.objectRequired("settings").objectRequired("restTimer")
            val restTimerSettings = LegacyRestTimerSettings(
                autoStart = restJson.optBoolean("autoStart", true),
                vibrationEnabled = restJson.optBoolean("vibrationEnabled", true),
                vibrationStrength = restJson.optString("vibrationStrength", "strong"),
                chimeEnabled = restJson.optBoolean("chimeEnabled", false),
                backgroundNotificationEnabled = restJson.optBoolean("backgroundNotificationEnabled", true),
            )

            val bodyMeasurements = database.arrayOrEmpty("bodyMeasurements").objects().map { measurement ->
                BodyMeasurementEntity(
                    id = measurement.stringRequired("id"),
                    recordedAt = measurement.stringRequired("recordedAt"),
                    weightKg = measurement.doubleOrNull("weightKg"),
                    heightCm = measurement.doubleOrNull("heightCm"),
                    source = measurement.optString("source", "manual"),
                    sourceRecordId = measurement.stringOrNull("sourceRecordId"),
                )
            }

            val exercises = mutableListOf<ExerciseEntity>()
            val exerciseMemory = mutableListOf<ExerciseMemoryEntity>()
            val executionProfiles = mutableListOf<ExerciseExecutionProfileEntity>()
            val legacyRecruitment = mutableListOf<LegacyRecruitmentAllocation>()
            val cues = mutableListOf<ExerciseCueEntity>()
            val commonMistakes = mutableListOf<ExerciseCommonMistakeEntity>()
            val substitutions = mutableListOf<ExerciseSubstitutionEntity>()
            val setupPhotos = mutableListOf<LegacySetupPhotoPayload>()

            database.arrayRequired("exercises").objects().forEach { exercise ->
                val exerciseId = exercise.stringRequired("id")
                val executionProfileId = "execution_${exerciseId}_default"
                val tracking = exercise.objectRequired("tracking")
                val memory = exercise.objectOrNull("memory")
                val legacyIncrement = exercise.optDouble("progressionStep", 0.0).takeIf { it > 0.0 }
                exercises += ExerciseEntity(
                    id = exerciseId,
                    name = exercise.stringRequired("name"),
                    archived = exercise.optBoolean("archived", false),
                    defaultUnit = exercise.optString("defaultUnit", profile.units),
                    trackingMetric = tracking.stringRequired("metric"),
                    loadRelationship = tracking.stringRequired("loadRelationship"),
                    entryBasis = tracking.stringRequired("entryBasis"),
                    essentialCue = exercise.stringOrNull("essentialCue"),
                    createdAt = exercise.stringRequired("createdAt"),
                    updatedAt = exercise.stringRequired("updatedAt"),
                )
                executionProfiles += ExerciseExecutionProfileEntity(
                    id = executionProfileId,
                    exerciseId = exerciseId,
                    name = "Default",
                    equipment = memory?.optString("equipment", "").orEmpty(),
                    minimumLoad = legacyIncrement?.let { 0.0 },
                    maximumLoad = null,
                    loadIncrement = legacyIncrement,
                    allowedLoadsJson = null,
                    isDefault = true,
                )

                memory?.let {
                    exerciseMemory += ExerciseMemoryEntity(
                        exerciseId = exerciseId,
                        category = it.optString("category", ""),
                        equipment = it.optString("equipment", ""),
                        fatigueCost = it.optInt("fatigueCost", 3),
                        skillDifficulty = it.optInt("skillDifficulty", 3),
                        setupNotes = it.optString("setupNotes", ""),
                        videoReferenceUrl = it.optString("videoReferenceUrl", ""),
                        machineSettings = it.optString("machineSettings", ""),
                    )
                    it.stringArray("cues").forEachIndexed { index, cue ->
                        cues += ExerciseCueEntity(exerciseId, index, cue)
                    }
                    it.stringArray("commonMistakes").forEachIndexed { index, mistake ->
                        commonMistakes += ExerciseCommonMistakeEntity(exerciseId, index, mistake)
                    }
                    it.stringArray("substitutions").forEachIndexed { index, substitution ->
                        substitutions += ExerciseSubstitutionEntity(exerciseId, index, substitution)
                    }
                    it.arrayOrEmpty("setupPhotos").objects().forEachIndexed { index, photo ->
                        setupPhotos += LegacySetupPhotoPayload(
                            id = photo.stringRequired("id"),
                            exerciseId = exerciseId,
                            dataUrl = photo.stringRequired("dataUrl"),
                            createdAt = photo.stringRequired("createdAt"),
                            width = photo.intRequired("width"),
                            height = photo.intRequired("height"),
                            sortOrder = index,
                        )
                    }
                }

                exercise.objectOrNull("muscleLoadModel")?.let { model ->
                    requireImport(model.optInt("version", -1) == 1) {
                        "Exercise $exerciseId has an unsupported muscle-load model version."
                    }
                    val confidence = model.doubleRequired("confidence")
                    val basis = model.stringRequired("basis")
                    model.arrayRequired("allocations").objects().forEach { allocation ->
                        legacyRecruitment += LegacyRecruitmentAllocation(
                            executionProfileId = executionProfileId,
                            muscleLabel = allocation.stringRequired("muscle"),
                            weighting = allocation.doubleRequired("proportion"),
                            role = allocation.stringRequired("role"),
                            confidence = confidence,
                            source = basis,
                        )
                    }
                }
            }

            val routineVersions = mutableListOf<RoutineVersionEntity>()
            val routineSlots = mutableListOf<RoutineSlotEntity>()
            val modePrescriptions = mutableListOf<ModePrescriptionEntity>()

            database.arrayRequired("routineVersions").objects().forEach { version ->
                val versionId = version.stringRequired("id")
                routineVersions += RoutineVersionEntity(
                    id = versionId,
                    version = version.intRequired("version"),
                    parentId = version.stringOrNull("parentId"),
                    createdAt = version.stringRequired("createdAt"),
                    effectiveAt = version.stringRequired("effectiveAt"),
                    source = version.stringRequired("source"),
                    changeReason = version.optString("changeReason", ""),
                )
                version.arrayRequired("days").objects().forEach { day ->
                    val daySymbol = day.stringRequired("symbol")
                    day.arrayRequired("slots").objects().forEachIndexed { fallbackPosition, slot ->
                        val slotId = slot.stringRequired("id")
                        // Validate the Legacy value but do not persist it. Programme assignments no
                        // longer own load; historical session snapshots and performed sets do.
                        slot.doubleRequired("plannedLoad")
                        routineSlots += RoutineSlotEntity(
                            id = slotId,
                            routineVersionId = versionId,
                            daySymbol = daySymbol,
                            exerciseId = slot.stringRequired("exerciseId"),
                            position = if (slot.has("position")) slot.intRequired("position") else fallbackPosition,
                            importance = slot.stringRequired("importance"),
                            lockedToDay = slot.optBoolean("lockedToDay", false),
                        )
                        val prescriptions = slot.objectRequired("prescriptions")
                        listOf("A", "B", "C").forEach { mode ->
                            val prescription = prescriptions.objectRequired(mode)
                            modePrescriptions += ModePrescriptionEntity(
                                slotId = slotId,
                                mode = mode,
                                included = prescription.optBoolean("included", true),
                                sets = prescription.intRequired("sets"),
                                repMin = prescription.intRequired("repMin"),
                                repMax = prescription.intRequired("repMax"),
                                restSeconds = prescription.intRequired("restSeconds"),
                                deferToAnd = prescription.optBoolean("deferToAnd", false),
                            )
                        }
                    }
                }
            }

            val trainingCycles = mutableListOf<TrainingCycleEntity>()
            val completedDays = mutableListOf<CycleCompletedDayEntity>()
            database.arrayRequired("cycles").objects().forEach { cycle ->
                val cycleId = cycle.stringRequired("id")
                trainingCycles += TrainingCycleEntity(
                    id = cycleId,
                    startedAt = cycle.stringRequired("startedAt"),
                    endedAt = cycle.stringOrNull("endedAt"),
                    status = cycle.stringRequired("status"),
                    andCompleted = cycle.optBoolean("andCompleted", false),
                )
                cycle.stringArray("completedCoreDays").forEach { day ->
                    completedDays += CycleCompletedDayEntity(cycleId, day)
                }
            }

            val sessions = mutableListOf<SessionEntity>()
            val sessionExercises = mutableListOf<SessionExerciseEntity>()
            val sets = mutableListOf<SetRecordEntity>()
            val reflections = mutableListOf<ExerciseReflectionEntity>()

            database.arrayRequired("sessions").objects().forEach { session ->
                val sessionId = session.stringRequired("id")
                sessions += SessionEntity(
                    id = sessionId,
                    cycleId = session.stringRequired("cycleId"),
                    daySymbol = session.stringRequired("day"),
                    mode = session.stringRequired("mode"),
                    routineVersionId = session.stringRequired("routineVersionId"),
                    status = session.stringRequired("status"),
                    startedAt = session.stringRequired("startedAt"),
                    completedAt = session.stringOrNull("completedAt"),
                    editedAt = session.stringOrNull("editedAt"),
                    discardedAt = session.stringOrNull("discardedAt"),
                    excludedFromInsights = session.optBoolean("excludedFromInsights", false),
                    bodyweightSnapshotKg = session.doubleOrNull("bodyweightSnapshotKg"),
                    healthExportState = session.stringOrNull("healthExportState") ?: "not_requested",
                    healthClientRecordId = session.stringOrNull("healthClientRecordId"),
                )

                session.arrayRequired("exercises").objects().forEachIndexed { exercisePosition, sessionExercise ->
                    val sessionExerciseId = sessionExercise.stringRequired("id")
                    val exerciseId = sessionExercise.stringRequired("exerciseId")
                    val tracking = sessionExercise.objectRequired("trackingSnapshot")
                    val prescription = sessionExercise.objectRequired("prescription")
                    val prescribedSetCount = prescription.intRequired("sets")
                    sessionExercises += SessionExerciseEntity(
                        id = sessionExerciseId,
                        sessionId = sessionId,
                        position = exercisePosition,
                        exerciseId = exerciseId,
                        slotId = sessionExercise.stringRequired("slotId"),
                        exerciseNameSnapshot = sessionExercise.stringRequired("exerciseNameSnapshot"),
                        importanceSnapshot = sessionExercise.stringRequired("importanceSnapshot"),
                        trackingMetricSnapshot = tracking.stringRequired("metric"),
                        loadRelationshipSnapshot = tracking.stringRequired("loadRelationship"),
                        entryBasisSnapshot = tracking.stringRequired("entryBasis"),
                        bodyweightSnapshotKg = sessionExercise.doubleOrNull("bodyweightSnapshotKg"),
                        executionProfileId = "execution_${exerciseId}_default",
                        executionProfileNameSnapshot = "Default",
                        prescribedLoad = sessionExercise.doubleRequired("plannedLoad"),
                        prescriptionMode = prescription.stringRequired("mode"),
                        prescriptionIncluded = prescription.optBoolean("included", true),
                        prescribedSets = prescribedSetCount,
                        repMin = prescription.intRequired("repMin"),
                        repMax = prescription.intRequired("repMax"),
                        targetRir = null,
                        restSeconds = prescription.intRequired("restSeconds"),
                        generatedByModelVersion = "legacy-v6-session-snapshot-v1",
                        deferToAnd = prescription.optBoolean("deferToAnd", false),
                        status = sessionExercise.stringRequired("status"),
                        note = sessionExercise.stringOrNull("note"),
                        startedAt = sessionExercise.stringOrNull("startedAt"),
                        completedAt = sessionExercise.stringOrNull("completedAt"),
                        movementReason = sessionExercise.optString("movementReason", "base_routine"),
                    )

                    sessionExercise.arrayRequired("sets").objects().forEachIndexed { fallbackSetIndex, set ->
                        val setIndex = if (set.has("setIndex")) set.intRequired("setIndex") else fallbackSetIndex
                        val warmUp = set.optBoolean("warmUp", false)
                        val kind = set.stringOrNull("kind")
                            ?: if (warmUp) "warm_up" else if (setIndex < prescribedSetCount) "prescribed" else "additional"
                        sets += SetRecordEntity(
                            id = set.stringRequired("id"),
                            sessionExerciseId = sessionExerciseId,
                            setIndex = setIndex,
                            load = set.doubleOrNull("load"),
                            reps = set.intOrNull("reps"),
                            durationSeconds = set.intOrNull("durationSeconds"),
                            distanceMetres = set.doubleOrNull("distanceMetres"),
                            unit = set.stringRequired("unit"),
                            completedAt = set.stringOrNull("completedAt"),
                            note = set.stringOrNull("note"),
                            rir = set.doubleOrNull("rir"),
                            effortSource = set.stringOrNull("effortSource"),
                            warmUp = warmUp,
                            kind = kind,
                        )
                    }

                    sessionExercise.objectOrNull("reflection")?.let { reflection ->
                        reflections += ExerciseReflectionEntity(
                            sessionExerciseId = sessionExerciseId,
                            targetMuscleEngagement = reflection.scalarString("targetMuscleEngagement"),
                            execution = reflection.stringRequired("execution"),
                            enjoyment = reflection.scalarString("enjoyment"),
                            comfort = reflection.stringRequired("comfort"),
                            note = reflection.stringOrNull("note"),
                            recordedAt = reflection.stringRequired("recordedAt"),
                            updatedAt = reflection.stringRequired("updatedAt"),
                        )
                    }
                }
            }

            val healthObservations = database.arrayOrEmpty("healthObservations").objects().map { observation ->
                HealthObservationEntity(
                    id = observation.stringRequired("id"),
                    type = observation.stringRequired("type"),
                    startTime = observation.stringRequired("startTime"),
                    endTime = observation.stringOrNull("endTime"),
                    value = observation.doubleOrNull("value"),
                    unit = observation.stringOrNull("unit"),
                    provider = observation.stringRequired("provider"),
                    sourceRecordId = observation.stringRequired("sourceRecordId"),
                    sourceDevice = observation.stringOrNull("sourceDevice"),
                    payloadJson = observation.objectOrNull("payload")?.toString(),
                    importedAt = observation.stringRequired("importedAt"),
                )
            }

            val health = database.objectRequired("healthIntegration")
            val healthIntegration = HealthIntegrationStateEntity(
                provider = health.optString("provider", "none"),
                permissionState = health.optString("permissionState", "not_requested"),
                lastSyncedAt = health.stringOrNull("lastSyncedAt"),
                lastError = health.stringOrNull("lastError"),
            )

            val appState = AppStateEntity(
                currentRoutineVersionId = database.stringRequired("currentRoutineVersionId"),
                currentCycleId = database.stringRequired("currentCycleId"),
                activeSessionId = database.stringOrNull("activeSessionId"),
                createdAt = database.stringRequired("createdAt"),
                updatedAt = database.stringRequired("updatedAt"),
            )

            val snapshot = LegacyImportSnapshot(
                exportedAt = exportedAt,
                profile = profile,
                restTimerSettings = restTimerSettings,
                bodyMeasurements = bodyMeasurements,
                exercises = exercises,
                exerciseMemory = exerciseMemory,
                executionProfiles = executionProfiles,
                legacyRecruitment = legacyRecruitment,
                cues = cues,
                commonMistakes = commonMistakes,
                substitutions = substitutions,
                setupPhotos = setupPhotos,
                routineVersions = routineVersions,
                routineSlots = routineSlots,
                modePrescriptions = modePrescriptions,
                trainingCycles = trainingCycles,
                completedDays = completedDays,
                sessions = sessions,
                sessionExercises = sessionExercises,
                sets = sets,
                reflections = reflections,
                healthObservations = healthObservations,
                healthIntegration = healthIntegration,
                appState = appState,
            )
            validate(snapshot)
            return snapshot
        } catch (error: LegacyImportException) {
            throw error
        } catch (error: JSONException) {
            throw LegacyImportException("Lite Legacy backup JSON is malformed: ${error.message}", error)
        }
    }

    private fun validate(snapshot: LegacyImportSnapshot) {
        requireImport(snapshot.routineVersions.any { it.id == snapshot.appState.currentRoutineVersionId }) {
            "Backup current routine does not exist."
        }
        requireImport(snapshot.trainingCycles.any { it.id == snapshot.appState.currentCycleId }) {
            "Backup current cycle does not exist."
        }
        snapshot.appState.activeSessionId?.let { activeId ->
            requireImport(snapshot.sessions.any { it.id == activeId }) { "Backup active session does not exist." }
        }

        val exerciseIds = snapshot.exercises.mapTo(hashSetOf()) { it.id }
        requireImport(snapshot.routineSlots.all { it.exerciseId in exerciseIds }) {
            "A routine slot references an exercise that is not present in the backup."
        }
        requireImport(snapshot.sessionExercises.all { it.exerciseId in exerciseIds }) {
            "A session references an exercise that is not present in the backup."
        }
        requireImport(snapshot.setupPhotos.groupBy { it.exerciseId }.values.all { it.size <= 12 }) {
            "A Legacy exercise contains more than 12 setup photos."
        }
        requireImport(snapshot.setupPhotos.all { it.width > 0 && it.height > 0 }) {
            "A setup photo has invalid dimensions."
        }
        requireImport(snapshot.legacyRecruitment.all { it.weighting >= 0.0 && it.weighting <= 1.0 }) {
            "A recruitment allocation has an invalid weighting."
        }
        requireImport(snapshot.legacyRecruitment.all { it.confidence >= 0.0 && it.confidence <= 1.0 }) {
            "A recruitment model has an invalid confidence value."
        }
        requireImport(snapshot.legacyRecruitment.all { it.role in setOf("prime", "synergist", "stabiliser") }) {
            "A recruitment allocation has an invalid role."
        }
    }
}

private inline fun requireImport(condition: Boolean, message: () -> String) {
    if (!condition) throw LegacyImportException(message())
}

private fun JSONObject.objectRequired(name: String): JSONObject =
    optJSONObject(name) ?: throw LegacyImportException("Backup field '$name' is missing or is not an object.")

private fun JSONObject.objectOrNull(name: String): JSONObject? =
    if (!has(name) || isNull(name)) null else optJSONObject(name)

private fun JSONObject.arrayRequired(name: String): JSONArray =
    optJSONArray(name) ?: throw LegacyImportException("Backup field '$name' is missing or is not an array.")

private fun JSONObject.arrayOrEmpty(name: String): JSONArray = optJSONArray(name) ?: JSONArray()

private fun JSONObject.stringRequired(name: String): String {
    if (!has(name) || isNull(name)) throw LegacyImportException("Backup field '$name' is missing.")
    return getString(name)
}

private fun JSONObject.intRequired(name: String): Int {
    if (!has(name) || isNull(name)) throw LegacyImportException("Backup field '$name' is missing.")
    return getInt(name)
}

private fun JSONObject.doubleRequired(name: String): Double {
    if (!has(name) || isNull(name)) throw LegacyImportException("Backup field '$name' is missing.")
    return getDouble(name)
}

private fun JSONObject.stringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else getString(name)

private fun JSONObject.intOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else getInt(name)

private fun JSONObject.doubleOrNull(name: String): Double? =
    if (!has(name) || isNull(name)) null else getDouble(name)

private fun JSONObject.stringArray(name: String): List<String> =
    arrayOrEmpty(name).let { array -> List(array.length()) { index -> array.getString(index) } }

private fun JSONObject.scalarString(name: String): String {
    if (!has(name) || isNull(name)) throw LegacyImportException("Backup field '$name' is missing.")
    return when (val value = get(name)) {
        is Number -> value.toString()
        is String -> value
        else -> throw LegacyImportException("Backup field '$name' is not a supported scalar value.")
    }
}

private fun JSONArray.objects(): List<JSONObject> =
    List(length()) { index ->
        optJSONObject(index) ?: throw LegacyImportException("Backup array item $index is not an object.")
    }
