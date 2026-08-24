package dev.kian.mymettle.data.migration

import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.BodyMeasurementEntity
import dev.kian.mymettle.data.local.entity.CycleCompletedDayEntity
import dev.kian.mymettle.data.local.entity.ExerciseCommonMistakeEntity
import dev.kian.mymettle.data.local.entity.ExerciseCueEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.ExecutionProfileVersionEntity
import dev.kian.mymettle.data.local.entity.ExerciseMemoryEntity
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.ExerciseSubstitutionEntity
import dev.kian.mymettle.data.local.entity.HealthIntegrationStateEntity
import dev.kian.mymettle.data.local.entity.HealthObservationEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaMetricEntity
import dev.kian.mymettle.data.local.entity.RecruitmentProfileVersionEntity
import dev.kian.mymettle.data.local.entity.RoutineMetricTargetEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionMetricTargetEntity
import dev.kian.mymettle.data.local.entity.SessionSetPrescriptionEntity
import dev.kian.mymettle.data.local.entity.SetDraftMetricValueEntity
import dev.kian.mymettle.data.local.entity.SetMetricValueEntity
import dev.kian.mymettle.data.local.entity.SetObservationEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import dev.kian.mymettle.data.local.entity.UserProfileEntity
import dev.kian.mymettle.domain.evidence.AcquisitionMethod
import dev.kian.mymettle.domain.evidence.EvidenceGranularity
import dev.kian.mymettle.domain.evidence.TimingQuality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitConverter
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.domain.performance.defaultSemanticRole
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

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
            val performanceSchemas = mutableListOf<PerformanceSchemaEntity>()
            val performanceSchemaMetrics = mutableListOf<PerformanceSchemaMetricEntity>()
            val recruitmentProfileVersions = mutableListOf<RecruitmentProfileVersionEntity>()
            val executionProfileVersions = mutableListOf<ExecutionProfileVersionEntity>()
            val semanticsByExerciseId = linkedMapOf<String, LegacyExecutionSemantics>()
            val historicalSemantics = linkedMapOf<List<String>, LegacyExecutionSemantics>()
            val translatedRecruitment = mutableListOf<TranslatedRecruitmentAllocation>()
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
                val createdAt = exercise.stringRequired("createdAt")
                val semantics = legacyExecutionSemantics(
                    exerciseId = exerciseId,
                    trackingMetric = tracking.stringRequired("metric"),
                    loadRelationship = tracking.stringRequired("loadRelationship"),
                    entryBasis = tracking.stringRequired("entryBasis"),
                    defaultUnit = exercise.optString("defaultUnit", profile.units),
                    legacyIncrement = legacyIncrement,
                    equipment = memory?.optString("equipment", "")?.takeIf { it.isNotBlank() },
                    createdAt = createdAt,
                )
                semanticsByExerciseId[exerciseId] = semantics
                exercises += ExerciseEntity(
                    id = exerciseId,
                    name = exercise.stringRequired("name"),
                    archived = exercise.optBoolean("archived", false),
                    essentialCue = exercise.stringOrNull("essentialCue"),
                    createdAt = createdAt,
                    updatedAt = exercise.stringRequired("updatedAt"),
                )
                executionProfiles += ExerciseExecutionProfileEntity(
                    id = executionProfileId,
                    exerciseId = exerciseId,
                    name = "Default",
                    isDefault = true,
                    archived = false,
                )
                performanceSchemas += semantics.schema
                performanceSchemaMetrics += semantics.schemaMetrics
                recruitmentProfileVersions += semantics.recruitmentVersion
                executionProfileVersions += semantics.executionVersion

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

            }

            // Legacy muscleLoadModel.proportion was a conserved load-share model. N-BIO-6
            // weighting is an independent muscle-local exposure coefficient and therefore cannot
            // be populated by relabelling that field. Only an explicit, AI-reviewed Native
            // supplement is eligible to create behaviour-driving recruitment allocations.
            root.objectOrNull("nativeTranslation")?.let { translation ->
                requireImport(translation.optInt("version", -1) == 1) {
                    "Unsupported Native translation supplement version."
                }
                requireImport(
                    translation.optString("recruitmentSemantics") == "independent-muscle-local-exposure-v1",
                ) { "Native translation uses unsupported recruitment semantics." }
                val seenExercises = mutableSetOf<String>()
                translation.arrayRequired("recruitmentProfiles").objects().forEach { profile ->
                    val exerciseId = profile.stringRequired("exerciseId")
                    requireImport(seenExercises.add(exerciseId)) {
                        "Native translation contains more than one recruitment profile for exercise $exerciseId."
                    }
                    val semantics = semanticsByExerciseId[exerciseId]
                        ?: throw LegacyImportException("Native translation references unknown exercise $exerciseId.")
                    val modelVersion = profile.stringRequired("modelVersion")
                    val basis = profile.stringRequired("basis")
                    val profileConfidence = profile.doubleRequired("confidence")
                    val allocations = profile.arrayRequired("allocations").objects()
                    requireImport(allocations.isNotEmpty()) {
                        "Native translation recruitment profile for $exerciseId has no allocations."
                    }
                    allocations.forEach { allocation ->
                        translatedRecruitment += TranslatedRecruitmentAllocation(
                            recruitmentProfileVersionId = semantics.recruitmentVersion.id,
                            muscleSegmentId = allocation.stringRequired("muscleSegmentId"),
                            weighting = allocation.doubleRequired("weighting"),
                            role = allocation.stringRequired("role"),
                            confidence = allocation.doubleOrNull("confidence") ?: profileConfidence,
                            provenanceReference = basis,
                            applicableRom = allocation.stringOrNull("applicableRom"),
                            applicableTechnique = allocation.stringOrNull("applicableTechnique"),
                            resistanceCurveClass = allocation.stringOrNull("resistanceCurveClass"),
                            modelVersion = modelVersion,
                        )
                    }
                }
            }

            val routineVersions = mutableListOf<RoutineVersionEntity>()
            val routineSlots = mutableListOf<RoutineSlotEntity>()
            val routineMetricTargets = mutableListOf<RoutineMetricTargetEntity>()
            val modePrescriptions = mutableListOf<LegacyModePrescription>()

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
                        val prescriptions = slot.objectRequired("prescriptions")
                        val slotPrescriptions = listOf("A", "B", "C").map { mode ->
                            val prescription = prescriptions.objectRequired(mode)
                            LegacyModePrescription(
                                routineVersionId = versionId,
                                daySymbol = daySymbol,
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
                        val full = slotPrescriptions.single { it.mode == "A" }
                        // Validate the Legacy value but do not persist it. Programme assignments no
                        // longer own load; historical session snapshots and performed sets do.
                        slot.doubleRequired("plannedLoad")
                        val preferredSets = full.sets.takeIf { full.included && it > 0 }
                            ?: slotPrescriptions.filter { it.included }.maxOfOrNull { it.sets }
                                ?.takeIf { it > 0 }
                            ?: 1
                        routineSlots += RoutineSlotEntity(
                            id = slotId,
                            routineVersionId = versionId,
                            daySymbol = daySymbol,
                            exerciseId = slot.stringRequired("exerciseId"),
                            position = if (slot.has("position")) slot.intRequired("position") else fallbackPosition,
                            importance = slot.stringRequired("importance"),
                            lockedToDay = slot.optBoolean("lockedToDay", false),
                            preferredSets = preferredSets,
                            restSeconds = full.restSeconds,
                        )
                        val exerciseId = slot.stringRequired("exerciseId")
                        val semantics = semanticsByExerciseId[exerciseId]
                            ?: throw LegacyImportException("Routine slot $slotId references a missing exercise.")
                        if (semantics.metrics.contains(PerformanceMetric.REPETITIONS)) {
                            routineMetricTargets += RoutineMetricTargetEntity(
                                routineVersionId = versionId,
                                slotId = slotId,
                                metric = PerformanceMetric.REPETITIONS.storageValue,
                                targetKind = TargetKind.RANGE.storageValue,
                                lowerCanonical = full.repMin.toDouble(),
                                upperCanonical = full.repMax.toDouble(),
                                canonicalUnit = UnitId.REPETITION.storageValue,
                                displayUnit = UnitId.REPETITION.storageValue,
                                source = "legacy-v6-repetition-preference",
                                modelVersion = "legacy-preference-translation-v1",
                            )
                        }
                        modePrescriptions += slotPrescriptions
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
            val sessionSetPrescriptions = mutableListOf<SessionSetPrescriptionEntity>()
            val sessionMetricTargets = mutableListOf<SessionMetricTargetEntity>()
            val sets = mutableListOf<SetRecordEntity>()
            val setObservations = mutableListOf<SetObservationEntity>()
            val setMetricValues = mutableListOf<SetMetricValueEntity>()
            val setDraftMetricValues = mutableListOf<SetDraftMetricValueEntity>()
            val reflections = mutableListOf<ExerciseReflectionEntity>()

            database.arrayRequired("sessions").objects().forEach { session ->
                val sessionId = session.stringRequired("id")
                val sessionStartedAt = session.stringRequired("startedAt")
                val sessionBodyweight = session.doubleOrNull("bodyweightSnapshotKg")
                sessions += SessionEntity(
                    id = sessionId,
                    cycleId = session.stringRequired("cycleId"),
                    daySymbol = session.stringRequired("day"),
                    mode = session.stringRequired("mode"),
                    routineVersionId = session.stringRequired("routineVersionId"),
                    status = session.stringRequired("status"),
                    startedAt = sessionStartedAt,
                    completedAt = session.stringOrNull("completedAt"),
                    editedAt = session.stringOrNull("editedAt"),
                    discardedAt = session.stringOrNull("discardedAt"),
                    excludedFromInsights = session.optBoolean("excludedFromInsights", false),
                    bodyweightSnapshotKg = sessionBodyweight,
                    healthExportState = session.stringOrNull("healthExportState") ?: "not_requested",
                    healthClientRecordId = session.stringOrNull("healthClientRecordId"),
                )

                session.arrayRequired("exercises").objects().forEachIndexed { exercisePosition, sessionExercise ->
                    val sessionExerciseId = sessionExercise.stringRequired("id")
                    val exerciseId = sessionExercise.stringRequired("exerciseId")
                    val tracking = sessionExercise.objectRequired("trackingSnapshot")
                    val prescription = sessionExercise.objectRequired("prescription")
                    val prescribedSetCount = prescription.intRequired("sets")
                    val currentSemantics = semanticsByExerciseId[exerciseId]
                        ?: throw LegacyImportException("Session exercise $sessionExerciseId references missing semantics.")
                    val trackingMetric = tracking.stringRequired("metric")
                    val loadRelationship = tracking.stringRequired("loadRelationship")
                    val entryBasis = tracking.stringRequired("entryBasis")
                    val semantics = if (
                        trackingMetric == currentSemantics.legacyTrackingMetric &&
                        loadRelationship == currentSemantics.legacyLoadRelationship &&
                        entryBasis == currentSemantics.legacyEntryBasis
                    ) {
                        currentSemantics
                    } else {
                        val key = listOf(exerciseId, trackingMetric, loadRelationship, entryBasis)
                        historicalSemantics.getOrPut(key) {
                            val version = executionProfileVersions.count { it.executionProfileId == currentSemantics.executionVersion.executionProfileId } + 1
                            legacyExecutionSemantics(
                                exerciseId = exerciseId,
                                trackingMetric = trackingMetric,
                                loadRelationship = loadRelationship,
                                entryBasis = entryBasis,
                                defaultUnit = currentSemantics.defaultUnit.storageValue,
                                legacyIncrement = null,
                                equipment = currentSemantics.executionVersion.equipmentIdentity,
                                createdAt = sessionStartedAt,
                                version = version,
                                recruitmentProfileVersionId = currentSemantics.recruitmentVersion.id,
                                supersededAt = exportedAt ?: sessionStartedAt,
                                provenance = "lite-legacy-v6-historical-tracking-snapshot",
                            ).also { historical ->
                                performanceSchemas += historical.schema
                                performanceSchemaMetrics += historical.schemaMetrics
                                executionProfileVersions += historical.executionVersion
                            }
                        }
                    }
                    val legacyExerciseBodyweight = sessionExercise.doubleOrNull("bodyweightSnapshotKg")
                    sessionExercises += SessionExerciseEntity(
                        id = sessionExerciseId,
                        sessionId = sessionId,
                        position = exercisePosition,
                        exerciseId = exerciseId,
                        slotId = sessionExercise.stringRequired("slotId"),
                        exerciseNameSnapshot = sessionExercise.stringRequired("exerciseNameSnapshot"),
                        importanceSnapshot = sessionExercise.stringRequired("importanceSnapshot"),
                        executionProfileId = "execution_${exerciseId}_default",
                        executionProfileVersionId = semantics.executionVersion.id,
                        executionProfileNameSnapshot = "Default",
                        prescriptionMode = prescription.stringRequired("mode"),
                        prescriptionIncluded = prescription.optBoolean("included", true),
                        restSeconds = prescription.intRequired("restSeconds"),
                        generatedByModelVersion = "legacy-v6-session-snapshot-v1",
                        deferToAnd = prescription.optBoolean("deferToAnd", false),
                        status = sessionExercise.stringRequired("status"),
                        note = sessionExercise.stringOrNull("note"),
                        startedAt = sessionExercise.stringOrNull("startedAt"),
                        completedAt = sessionExercise.stringOrNull("completedAt"),
                        movementReason = sessionExercise.optString("movementReason", "base_routine"),
                        substitutedFromExerciseId = null,
                    )

                    val prescribedTargets = mutableListOf<LegacyTargetValue>()
                    val plannedLoad = sessionExercise.doubleRequired("plannedLoad")
                    semantics.loadMetric?.let { metric ->
                        val value = semantics.metricValue(metric, plannedLoad)
                        prescribedTargets += LegacyTargetValue(
                            metric = metric,
                            kind = TargetKind.EXACT,
                            lowerCanonical = value.canonical.value,
                            upperCanonical = null,
                            displayUnit = value.entered.unit,
                            anchorCanonical = value.canonical.value,
                        )
                    }
                    if (PerformanceMetric.REPETITIONS in semantics.metrics) {
                        prescribedTargets += LegacyTargetValue(
                            metric = PerformanceMetric.REPETITIONS,
                            kind = TargetKind.RANGE,
                            lowerCanonical = prescription.intRequired("repMin").toDouble(),
                            upperCanonical = prescription.intRequired("repMax").toDouble(),
                            displayUnit = UnitId.REPETITION,
                            anchorCanonical = null,
                        )
                    }
                    repeat(prescribedSetCount) { setIndex ->
                        val setPrescriptionId = "$sessionExerciseId:prescription:set:$setIndex"
                        sessionSetPrescriptions += SessionSetPrescriptionEntity(
                            id = setPrescriptionId,
                            sessionExerciseId = sessionExerciseId,
                            setIndex = setIndex,
                            kind = "prescribed",
                            laterality = "unknown",
                        )
                        sessionMetricTargets += prescribedTargets.map { target ->
                            SessionMetricTargetEntity(
                                sessionSetPrescriptionId = setPrescriptionId,
                                metric = target.metric.storageValue,
                                targetKind = target.kind.storageValue,
                                lowerCanonical = target.lowerCanonical,
                                upperCanonical = target.upperCanonical,
                                canonicalUnit = target.metric.canonicalUnit.storageValue,
                                displayUnit = target.displayUnit.storageValue,
                                evidenceSource = "legacy_session_snapshot",
                                sourceObservationId = null,
                                sourceSetRecordId = null,
                                inferenceRunId = null,
                                evidenceAnchorCanonical = target.anchorCanonical,
                                evidenceModelVersion = "legacy-v6-session-snapshot-v2",
                            )
                        }
                    }

                    sessionExercise.arrayRequired("sets").objects().forEachIndexed { fallbackSetIndex, set ->
                        val setIndex = if (set.has("setIndex")) set.intRequired("setIndex") else fallbackSetIndex
                        val warmUp = set.optBoolean("warmUp", false)
                        val kind = set.stringOrNull("kind")
                            ?: if (warmUp) "warm_up" else if (setIndex < prescribedSetCount) "prescribed" else "additional"
                        val setId = set.stringRequired("id")
                        val completedAt = set.stringOrNull("completedAt")
                        sets += SetRecordEntity(
                            id = setId,
                            sessionExerciseId = sessionExerciseId,
                            setIndex = setIndex,
                            note = set.stringOrNull("note"),
                            warmUp = warmUp,
                            kind = kind,
                            createdAt = completedAt ?: sessionStartedAt,
                        )
                        val values = legacySetValues(semantics, set)
                        if (completedAt != null && values.isNotEmpty()) {
                            val completionInstant = Instant.parse(completedAt)
                            val observationId = "legacy_observation:$setId"
                            setObservations += SetObservationEntity(
                                id = observationId,
                                setRecordId = setId,
                                executionProfileVersionId = semantics.executionVersion.id,
                                ordinal = 0,
                                side = "unknown",
                                completedAt = completedAt,
                                recordedAt = exportedAt ?: completedAt,
                                source = "lite_legacy_v6_import",
                                bodyMassContextKg = legacyExerciseBodyweight.takeIf { it != sessionBodyweight },
                                bodyMassContextSource = legacyExerciseBodyweight.takeIf { it != sessionBodyweight }?.let {
                                    "legacy_session_exercise_snapshot"
                                },
                                supersedesObservationId = null,
                                startedAtEpochSecond = null,
                                startedAtNano = null,
                                endedAtEpochSecond = completionInstant.epochSecond,
                                endedAtNano = completionInstant.nano,
                                timingQuality = TimingQuality.COMPLETION_ONLY.storageValue,
                                sourceZoneOffsetMinutes = null,
                            )
                            setMetricValues += values.map { it.toLegacyEntity(observationId) }
                        } else if (completedAt == null && values.isNotEmpty()) {
                            setDraftMetricValues += values.map { value ->
                                SetDraftMetricValueEntity(
                                    setRecordId = setId,
                                    metric = value.metric.storageValue,
                                    enteredValue = value.entered.value,
                                    enteredUnit = value.entered.unit.storageValue,
                                    updatedAt = exportedAt ?: sessionStartedAt,
                                )
                            }
                        }
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
                performanceSchemas = performanceSchemas,
                performanceSchemaMetrics = performanceSchemaMetrics,
                recruitmentProfileVersions = recruitmentProfileVersions,
                executionProfileVersions = executionProfileVersions,
                translatedRecruitment = translatedRecruitment,
                cues = cues,
                commonMistakes = commonMistakes,
                substitutions = substitutions,
                setupPhotos = setupPhotos,
                routineVersions = routineVersions,
                routineSlots = routineSlots,
                routineMetricTargets = routineMetricTargets,
                modePrescriptions = modePrescriptions,
                trainingCycles = trainingCycles,
                completedDays = completedDays,
                sessions = sessions,
                sessionExercises = sessionExercises,
                sessionSetPrescriptions = sessionSetPrescriptions,
                sessionMetricTargets = sessionMetricTargets,
                sets = sets,
                setObservations = setObservations,
                setMetricValues = setMetricValues,
                setDraftMetricValues = setDraftMetricValues,
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
        requireImport(snapshot.translatedRecruitment.all { it.weighting >= 0.0 && it.weighting <= 1.0 }) {
            "A recruitment allocation has an invalid weighting."
        }
        requireImport(snapshot.translatedRecruitment.all { it.confidence >= 0.0 && it.confidence <= 1.0 }) {
            "A recruitment model has an invalid confidence value."
        }
        requireImport(snapshot.translatedRecruitment.all { it.role in setOf("prime", "synergist", "stabiliser") }) {
            "A recruitment allocation has an invalid role."
        }
        requireImport(snapshot.translatedRecruitment.groupBy { it.recruitmentProfileVersionId to it.muscleSegmentId }.values.all { it.size == 1 }) {
            "A Native translation contains duplicate muscle-segment allocations in one recruitment profile."
        }
    }
}

private data class LegacyExecutionSemantics(
    val legacyTrackingMetric: String,
    val legacyLoadRelationship: String,
    val legacyEntryBasis: String,
    val defaultUnit: UnitId,
    val metrics: Set<PerformanceMetric>,
    val loadMetric: PerformanceMetric?,
    val schema: PerformanceSchemaEntity,
    val schemaMetrics: List<PerformanceSchemaMetricEntity>,
    val recruitmentVersion: RecruitmentProfileVersionEntity,
    val executionVersion: ExecutionProfileVersionEntity,
) {
    fun metricValue(metric: PerformanceMetric, enteredValue: Double, enteredUnit: UnitId? = null): PerformanceMetricValue {
        val unit = enteredUnit ?: schemaMetrics.first { it.metric == metric.storageValue }.defaultUnit.let(UnitId::fromStorage)
        return PerformanceMetricValue(metric, Quantity(enteredValue, unit))
    }
}

private data class LegacyTargetValue(
    val metric: PerformanceMetric,
    val kind: TargetKind,
    val lowerCanonical: Double?,
    val upperCanonical: Double?,
    val displayUnit: UnitId,
    val anchorCanonical: Double?,
)

private fun legacyExecutionSemantics(
    exerciseId: String,
    trackingMetric: String,
    loadRelationship: String,
    entryBasis: String,
    defaultUnit: String,
    legacyIncrement: Double?,
    equipment: String?,
    createdAt: String,
    version: Int = 1,
    recruitmentProfileVersionId: String? = null,
    supersededAt: String? = null,
    provenance: String = "lite-legacy-v6-execution-translation-v1",
): LegacyExecutionSemantics {
    val relationship = when (loadRelationship) {
        "external" -> ResistanceSemantics.EXTERNAL
        "assistance" -> ResistanceSemantics.ASSISTANCE
        "bodyweight" -> ResistanceSemantics.BODYWEIGHT
        "bodyweight_plus_external" -> ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL
        "none" -> ResistanceSemantics.NONE
        else -> throw LegacyImportException("Exercise $exerciseId has unsupported load relationship '$loadRelationship'.")
    }
    val loadMetric = when (relationship) {
        ResistanceSemantics.ASSISTANCE -> PerformanceMetric.ASSISTANCE
        ResistanceSemantics.EXTERNAL, ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL -> PerformanceMetric.EXTERNAL_LOAD
        else -> null
    }
    val metrics = linkedSetOf<PerformanceMetric>()
    when (trackingMetric) {
        "load_reps" -> {
            loadMetric?.let(metrics::add)
            metrics += PerformanceMetric.REPETITIONS
        }
        "reps" -> metrics += PerformanceMetric.REPETITIONS
        "duration" -> {
            loadMetric?.let(metrics::add)
            metrics += PerformanceMetric.DURATION
        }
        "distance" -> metrics += PerformanceMetric.DISTANCE
        else -> throw LegacyImportException("Exercise $exerciseId has unsupported tracking metric '$trackingMetric'.")
    }
    val family = when {
        trackingMetric == "duration" && loadMetric != null -> MetricFamily.LOADED_HOLD
        trackingMetric == "duration" -> MetricFamily.DURATION_ONLY
        trackingMetric == "distance" -> MetricFamily.SPEED_DURATION
        relationship in setOf(ResistanceSemantics.BODYWEIGHT, ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL, ResistanceSemantics.ASSISTANCE) ->
            MetricFamily.BODYWEIGHT_RESISTANCE
        trackingMetric == "reps" -> MetricFamily.REPEATED_CONTRACTION
        else -> MetricFamily.DYNAMIC_RESISTANCE
    }
    val parsedDefault = runCatching { UnitId.fromStorage(defaultUnit) }.getOrNull()
    val loadUnit = parsedDefault?.takeIf { it.dimension == dev.kian.mymettle.domain.performance.QuantityDimension.MASS }
        ?: UnitId.KILOGRAM
    val schemaId = "performance_schema_${exerciseId}_v$version"
    val schemaRows = metrics.map { metric ->
        val unit = when (metric) {
            PerformanceMetric.EXTERNAL_LOAD, PerformanceMetric.ASSISTANCE -> loadUnit
            else -> metric.canonicalUnit
        }
        val incrementCanonical = if (metric == loadMetric && legacyIncrement != null) {
            UnitConverter.canonical(Quantity(legacyIncrement, unit)).value
        } else null
        PerformanceSchemaMetricEntity(
            performanceSchemaId = schemaId,
            metric = metric.storageValue,
            required = true,
            targetable = true,
            defaultUnit = unit.storageValue,
            minimumCanonical = if (metric == loadMetric && legacyIncrement != null) 0.0 else null,
            maximumCanonical = null,
            incrementCanonical = incrementCanonical,
            allowedCanonicalValuesJson = null,
        )
    }
    val profileId = "execution_${exerciseId}_default"
    val recruitmentId = recruitmentProfileVersionId ?: "recruitment_${profileId}_v1"
    val versionId = "${profileId}_v$version"
    val coefficients = when (relationship) {
        ResistanceSemantics.EXTERNAL -> Triple(0.0, 1.0, 0.0)
        ResistanceSemantics.ASSISTANCE -> Triple(1.0, 0.0, 1.0)
        ResistanceSemantics.BODYWEIGHT -> Triple(1.0, 0.0, 0.0)
        ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL -> Triple(1.0, 1.0, 0.0)
        ResistanceSemantics.NONE, ResistanceSemantics.DEVICE_ORDINAL -> Triple(0.0, 0.0, 0.0)
    }
    return LegacyExecutionSemantics(
        legacyTrackingMetric = trackingMetric,
        legacyLoadRelationship = loadRelationship,
        legacyEntryBasis = entryBasis,
        defaultUnit = parsedDefault ?: schemaRows.first().defaultUnit.let(UnitId::fromStorage),
        metrics = metrics,
        loadMetric = loadMetric,
        schema = PerformanceSchemaEntity(
            id = schemaId,
            version = version,
            metricFamily = family.storageValue,
            createdAt = createdAt,
            provenance = "lite-legacy-v6-tracking-translation-v1",
        ),
        schemaMetrics = schemaRows,
        recruitmentVersion = RecruitmentProfileVersionEntity(
            id = recruitmentId,
            executionProfileId = profileId,
            version = 1,
            createdAt = createdAt,
            effectiveAt = createdAt,
            supersededAt = null,
            provenance = "lite-legacy-v6-muscle-load-model",
            modelVersion = "legacy-recruitment-projection-v2",
        ),
        executionVersion = ExecutionProfileVersionEntity(
            id = versionId,
            executionProfileId = profileId,
            version = version,
            metricFamily = family.storageValue,
            performanceSchemaId = schemaId,
            equipmentIdentity = equipment,
            equipmentType = equipment,
            resistanceSemantics = relationship.storageValue,
            resistanceModelVersion = "legacy-resistance-coordinate-assumption-v1",
            bodyweightCoefficient = coefficients.first,
            externalLoadCoefficient = coefficients.second,
            assistanceCoefficient = coefficients.third,
            entryBasis = entryBasis,
            implementCount = null,
            lateralityMode = "unknown",
            romClass = null,
            techniqueClass = null,
            resistanceCurveClass = null,
            movementPattern = null,
            jointActionsJson = null,
            kineticChain = null,
            contractionType = null,
            gripSupportConstraintsJson = null,
            recruitmentProfileVersionId = recruitmentId,
            createdAt = createdAt,
            effectiveAt = createdAt,
            supersededAt = supersededAt,
            provenance = provenance,
            modelVersion = "n-bio-6-execution-semantics-v1",
        ),
    )
}

private fun legacySetValues(
    semantics: LegacyExecutionSemantics,
    set: JSONObject,
): List<PerformanceMetricValue> = buildList {
    semantics.loadMetric?.let { metric ->
        set.doubleOrNull("load")?.let { load ->
            val enteredUnit = runCatching { UnitId.fromStorage(set.stringRequired("unit")) }.getOrNull()
                ?.takeIf { it.dimension == dev.kian.mymettle.domain.performance.QuantityDimension.MASS }
                ?: semantics.defaultUnit
            add(semantics.metricValue(metric, load, enteredUnit))
        }
    }
    set.intOrNull("reps")?.let { if (PerformanceMetric.REPETITIONS in semantics.metrics) add(semantics.metricValue(PerformanceMetric.REPETITIONS, it.toDouble())) }
    set.intOrNull("durationSeconds")?.let {
        if (PerformanceMetric.DURATION in semantics.metrics) add(PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(it.toDouble(), UnitId.SECOND)))
    }
    set.doubleOrNull("distanceMetres")?.let {
        if (PerformanceMetric.DISTANCE in semantics.metrics) add(PerformanceMetricValue(PerformanceMetric.DISTANCE, Quantity(it, UnitId.METRE)))
    }
}

private fun PerformanceMetricValue.toLegacyEntity(observationId: String): SetMetricValueEntity = SetMetricValueEntity(
    observationId = observationId,
    metric = metric.storageValue,
    enteredValue = entered.value,
    enteredUnit = entered.unit.storageValue,
    canonicalValue = canonical.value,
    canonicalUnit = canonical.unit.storageValue,
    acquisitionMethod = AcquisitionMethod.UNKNOWN.storageValue,
    evidenceGranularity = EvidenceGranularity.SUMMARY.storageValue,
    semanticRole = metric.defaultSemanticRole().storageValue,
)

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
