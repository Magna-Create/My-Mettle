package dev.kian.mymettle.developer

import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ReferenceProfileEntity
import dev.kian.mymettle.domain.inference.UserInferenceSnapshot
import dev.kian.mymettle.workout.NativeWorkoutPlan
import dev.kian.mymettle.workout.RoomWorkoutRepository
import dev.kian.mymettle.workout.TrainingMode
import org.json.JSONArray
import org.json.JSONObject

data class ReferenceDebugStatus(
    val schemaVersion: Int,
    val muscleCount: Int,
    val segmentCount: Int,
    val priorCount: Int,
    val referenceProfile: ReferenceProfileEntity?,
)

data class ProgrammeDayDebug(
    val day: String,
    val plans: Map<TrainingMode, NativeWorkoutPlan>,
)

data class BiologyDeveloperSnapshot(
    val reference: ReferenceDebugStatus,
    val routineVersionId: String?,
    val days: List<ProgrammeDayDebug>,
    val inference: UserInferenceSnapshot?,
    val executionProfileLabels: Map<String, String>,
)

/** Read-only N-BIO observability and diagnostic-export boundary. */
class BiologyDeveloperRepository(
    private val database: MyMettleDatabase,
    private val workoutRepository: RoomWorkoutRepository = RoomWorkoutRepository(database),
) {
    suspend fun snapshot(): BiologyDeveloperSnapshot {
        val referenceDao = database.referenceDao()
        val workoutDao = database.workoutDao()
        val inferenceDao = database.inferenceDao()
        val muscles = referenceDao.muscles()
        val segments = referenceDao.segments()
        val referenceProfile = inferenceDao.latestReferenceProfile()
        val state = workoutDao.appState()
        val days = state?.currentRoutineVersionId?.let { routineVersionId ->
            workoutDao.routineDays(routineVersionId).map { day ->
                ProgrammeDayDebug(
                    day = day,
                    plans = TrainingMode.entries.mapNotNull { mode ->
                        runCatching { workoutRepository.plan(day, mode) }.getOrNull()?.let { mode to it }
                    }.toMap(),
                )
            }
        }.orEmpty()
        val inference = runCatching {
            dev.kian.mymettle.inference.RoomInferenceRepository(database).latestSnapshot()
        }.getOrNull()
        val profileIds = inference?.exerciseTranslationStates.orEmpty()
            .map { it.executionProfileId.value }
            .distinct()
        val profileEntities = if (profileIds.isEmpty()) emptyList() else workoutDao.executionProfilesById(profileIds)
        val exerciseNames = if (profileEntities.isEmpty()) emptyMap() else {
            workoutDao.exercises(profileEntities.map { it.exerciseId }.distinct()).associate { it.id to it.name }
        }
        val profileLabels = profileEntities.associate { profile ->
            profile.id to "${exerciseNames[profile.exerciseId] ?: profile.exerciseId} — ${profile.name}"
        }

        return BiologyDeveloperSnapshot(
            reference = ReferenceDebugStatus(
                schemaVersion = SCHEMA_VERSION,
                muscleCount = muscles.size,
                segmentCount = segments.size,
                priorCount = referenceProfile?.let { referenceDao.priors(it.id).size } ?: 0,
                referenceProfile = referenceProfile,
            ),
            routineVersionId = state?.currentRoutineVersionId,
            days = days,
            inference = inference,
            executionProfileLabels = profileLabels,
        )
    }

    fun diagnosticJson(snapshot: BiologyDeveloperSnapshot): String = JSONObject()
        .put("format", "my-mettle-n-bio-diagnostic")
        .put("formatVersion", 1)
        .put("roomSchemaVersion", snapshot.reference.schemaVersion)
        .put("reference", snapshot.reference.toJson())
        .put("routineVersionId", snapshot.routineVersionId ?: JSONObject.NULL)
        .put("programme", JSONArray(snapshot.days.map(ProgrammeDayDebug::toJson)))
        .put("inference", snapshot.inference?.toJson(snapshot.executionProfileLabels) ?: JSONObject.NULL)
        .toString(2)

    private companion object {
        const val SCHEMA_VERSION = 9
    }
}

private fun ReferenceDebugStatus.toJson(): JSONObject = JSONObject()
    .put("muscleCount", muscleCount)
    .put("segmentCount", segmentCount)
    .put("priorCount", priorCount)
    .put("profileId", referenceProfile?.id ?: JSONObject.NULL)
    .put("profileVersion", referenceProfile?.version ?: JSONObject.NULL)
    .put("datasetVersion", referenceProfile?.datasetVersion ?: JSONObject.NULL)
    .put("modelVersion", referenceProfile?.modelVersion ?: JSONObject.NULL)

private fun ProgrammeDayDebug.toJson(): JSONObject = JSONObject()
    .put("day", day)
    .put("modes", JSONArray(plans.values.map(NativeWorkoutPlan::toJson)))

private fun NativeWorkoutPlan.toJson(): JSONObject = JSONObject()
    .put("mode", mode.code)
    .put("constraints", JSONObject()
        .put("workingSetBudget", constraints.workingSetBudget)
        .put("exerciseBudget", constraints.exerciseBudget)
        .put("minimumSetsPerExercise", constraints.minimumSetsPerExercise)
        .put("targetPriorityFloor", constraints.targetPriorityFloor)
        .put("timeBudgetSeconds", constraints.timeBudgetSeconds ?: JSONObject.NULL)
        .put("source", constraints.source)
        .put("resolverModelVersion", constraints.resolverModelVersion))
    .put("targets", JSONArray(targetResolutions.map { resolution ->
        JSONObject()
            .put("id", resolution.target.id.value)
            .put("segmentId", resolution.target.segmentId.value)
            .put("programmePriority", resolution.target.priority)
            .put("resolvedPriority", resolution.resolvedPriority)
            .put("included", resolution.included)
            .put("source", resolution.target.source.description)
            .put("resolutionModelVersion", resolution.resolutionModelVersion)
    }))
    .put("candidates", JSONArray(candidateDecisions.map { candidate ->
        JSONObject()
            .put("slotId", candidate.slotId)
            .put("exerciseId", candidate.exerciseId)
            .put("exerciseName", candidate.exerciseName)
            .put("executionProfileId", candidate.executionProfileId)
            .put("executionProfileName", candidate.executionProfileName)
            .put("preferencePriority", candidate.preferencePriority)
            .put("selected", candidate.selected)
            .put("selectedSets", candidate.selectedSets ?: JSONObject.NULL)
            .put("decisionReason", candidate.decisionReason)
            .put("targetCoverage", JSONObject(candidate.targetCoverage))
    }))
    .put("prescriptions", JSONArray(exercises.map { planned ->
        val evidence = planned.prescription.loadEvidence
        JSONObject()
            .put("exerciseId", planned.prescription.exerciseId.value)
            .put("exerciseName", planned.name)
            .put("executionProfileId", planned.prescription.executionProfileId.value)
            .put("sets", planned.prescription.sets)
            .put("repMin", planned.prescription.repRange.first)
            .put("repMax", planned.prescription.repRange.last)
            .put("prescribedLoad", planned.prescription.prescribedLoad ?: JSONObject.NULL)
            .put("loadEvidence", evidence?.let { value -> JSONObject()
                .put("source", value.source)
                .put("anchorLoad", value.anchorLoad)
                .put("sourceSetRecordId", value.sourceSetRecordId ?: JSONObject.NULL)
                .put("inferenceRunId", value.inferenceRunId ?: JSONObject.NULL)
            } ?: JSONObject.NULL)
            .put("movementReason", planned.movementReason)
            .put("modelVersion", planned.prescription.generatedByModelVersion)
    }))

private fun UserInferenceSnapshot.toJson(profileLabels: Map<String, String>): JSONObject = JSONObject()
    .put("run", JSONObject()
        .put("id", run.id.value)
        .put("modelVersion", run.modelVersion)
        .put("calculatedAt", run.calculatedAt.toString())
        .put("evidenceThrough", run.evidenceThrough?.toString() ?: JSONObject.NULL)
        .put("evidenceSetCount", run.evidenceSetCount)
        .put("referenceProfileId", run.referenceProfileId.value)
        .put("referenceProfileVersion", run.referenceProfileVersion)
        .put("referenceModelVersion", run.referenceModelVersion)
        .put("recruitmentModelVersion", run.recruitmentModelVersion)
        .put("stimulusModelVersion", run.stimulusModelVersion)
        .put("muscleStateModelVersion", run.muscleStateModelVersion)
        .put("exerciseTranslationModelVersion", run.exerciseTranslationModelVersion))
    .put("muscleStates", JSONArray(muscleStates.map { state -> JSONObject()
        .put("segmentId", state.segmentId.value)
        .put("side", state.side.storageValue)
        .put("developmentIndex", state.developmentIndex.value)
        .put("developmentUncertainty", state.developmentIndex.uncertainty ?: JSONObject.NULL)
        .put("recentStimulus", state.recentStimulus?.value ?: JSONObject.NULL)
        .put("recovery", state.recovery?.value ?: JSONObject.NULL)
        .put("evidenceCount", state.evidenceCount)
        .put("modelVersion", state.inferenceModelVersion)
    }))
    .put("stimulusEstimates", JSONArray(stimulusEstimates.map { estimate -> JSONObject()
        .put("setRecordId", estimate.setRecordId)
        .put("sessionExerciseId", estimate.sessionExerciseId)
        .put("segmentId", estimate.segmentId.value)
        .put("side", estimate.side.storageValue)
        .put("role", estimate.role.storageValue)
        .put("recruitmentWeighting", estimate.recruitmentWeighting)
        .put("estimatedStimulus", estimate.estimatedStimulus)
        .put("confidence", estimate.confidence)
        .put("modelVersion", estimate.modelVersion)
    }))
    .put("exerciseTranslationStates", JSONArray(exerciseTranslationStates.map { state -> JSONObject()
        .put("executionProfileId", state.executionProfileId.value)
        .put("label", profileLabels[state.executionProfileId.value] ?: JSONObject.NULL)
        .put("observedLoadAnchor", state.observedLoadAnchor?.value ?: JSONObject.NULL)
        .put("observedLoadUnit", state.observedLoadUnit ?: JSONObject.NULL)
        .put("observedLoadSourceSetId", state.observedLoadAnchor?.sourceId ?: JSONObject.NULL)
        .put("observedRepAnchor", state.observedRepAnchor?.value ?: JSONObject.NULL)
        .put("observedRirAnchor", state.observedRirAnchor ?: JSONObject.NULL)
        .put("sampleCount", state.sampleCount)
        .put("modelVersion", state.modelVersion)
    }))
