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

data class InferenceRunDebugSummary(
    val id: String,
    val executionMode: String,
    val semanticsMode: String,
    val modelManifestId: String,
    val calculatedAt: String,
    val evidenceObservationCount: Int,
    val effectiveIndependentSessionCount: Int,
)

data class BiologyDeveloperSnapshot(
    val reference: ReferenceDebugStatus,
    val routineVersionId: String?,
    val days: List<ProgrammeDayDebug>,
    val inference: UserInferenceSnapshot?,
    val inferenceRuns: List<InferenceRunDebugSummary>,
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
        val inferenceRuns = inference?.run?.userProfileId?.let { userProfileId ->
            inferenceDao.inferenceRuns(userProfileId).map { run ->
                InferenceRunDebugSummary(
                    id = run.id,
                    executionMode = run.executionMode,
                    semanticsMode = run.semanticsMode,
                    modelManifestId = run.modelManifestId,
                    calculatedAt = run.calculatedAt,
                    evidenceObservationCount = run.evidenceObservationCount,
                    effectiveIndependentSessionCount = run.effectiveIndependentSessionCount,
                )
            }
        }.orEmpty()
        val versionIds = inference?.exerciseTranslationStates.orEmpty()
            .map { it.executionProfileVersionId.value }
            .distinct()
        val versions = if (versionIds.isEmpty()) emptyList() else workoutDao.executionProfileVersionsById(versionIds)
        val profileIds = versions.map { it.executionProfileId }.distinct()
        val profileEntities = if (profileIds.isEmpty()) emptyList() else workoutDao.executionProfilesById(profileIds)
        val exerciseNames = if (profileEntities.isEmpty()) emptyMap() else {
            workoutDao.exercises(profileEntities.map { it.exerciseId }.distinct()).associate { it.id to it.name }
        }
        val profilesById = profileEntities.associateBy { it.id }
        val profileLabels = versions.associate { version ->
            val profile = profilesById.getValue(version.executionProfileId)
            version.id to "${exerciseNames[profile.exerciseId] ?: profile.exerciseId} — ${profile.name} · v${version.version}"
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
            inferenceRuns = inferenceRuns,
            executionProfileLabels = profileLabels,
        )
    }

    fun diagnosticJson(snapshot: BiologyDeveloperSnapshot): String = JSONObject()
        .put("format", "my-mettle-n-bio-diagnostic")
        .put("formatVersion", 2)
        .put("roomSchemaVersion", snapshot.reference.schemaVersion)
        .put("reference", snapshot.reference.toJson())
        .put("routineVersionId", snapshot.routineVersionId ?: JSONObject.NULL)
        .put("programme", JSONArray(snapshot.days.map(ProgrammeDayDebug::toJson)))
        .put("inferenceRuns", JSONArray(snapshot.inferenceRuns.map(InferenceRunDebugSummary::toJson)))
        .put("inference", snapshot.inference?.toJson(snapshot.executionProfileLabels) ?: JSONObject.NULL)
        .toString(2)

    private companion object {
        const val SCHEMA_VERSION = 14
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

private fun InferenceRunDebugSummary.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("executionMode", executionMode)
    .put("semanticsMode", semanticsMode)
    .put("modelManifestId", modelManifestId)
    .put("calculatedAt", calculatedAt)
    .put("evidenceObservationCount", evidenceObservationCount)
    .put("effectiveIndependentSessionCount", effectiveIndependentSessionCount)

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
            .put("executionProfileVersionId", candidate.executionProfileVersionId)
            .put("executionProfileName", candidate.executionProfileName)
            .put("preferencePriority", candidate.preferencePriority)
            .put("selected", candidate.selected)
            .put("selectedSets", candidate.selectedSets ?: JSONObject.NULL)
            .put("decisionReason", candidate.decisionReason)
            .put("targetCoverage", JSONObject(candidate.targetCoverage))
    }))
    .put("prescriptions", JSONArray(exercises.map { planned ->
        JSONObject()
            .put("exerciseId", planned.prescription.exerciseId.value)
            .put("exerciseName", planned.name)
            .put("executionProfileId", planned.prescription.executionProfileId.value)
            .put("executionProfileVersionId", planned.prescription.executionProfileVersionId.value)
            .put("sets", planned.prescription.sets)
            .put("metricFamily", planned.schema.family.storageValue)
            .put("setPrescriptions", JSONArray(planned.prescription.setPrescriptions.map { set -> JSONObject()
                .put("index", set.index)
                .put("laterality", set.laterality.storageValue)
                .put("metricTargets", JSONArray(set.metricTargets.map { target -> JSONObject()
                    .put("metric", target.metric.storageValue)
                    .put("kind", target.kind.storageValue)
                    .put("lowerCanonical", target.lowerCanonical ?: JSONObject.NULL)
                    .put("upperCanonical", target.upperCanonical ?: JSONObject.NULL)
                    .put("canonicalUnit", target.canonicalUnit.storageValue)
                    .put("displayUnit", target.displayUnit.storageValue)
                    .put("evidence", target.evidence?.let { value -> JSONObject()
                        .put("source", value.source)
                        .put("anchorCanonical", value.anchorCanonical ?: JSONObject.NULL)
                        .put("sourceObservationId", value.sourceObservationId ?: JSONObject.NULL)
                        .put("sourceSetRecordId", value.sourceSetRecordId ?: JSONObject.NULL)
                        .put("inferenceRunId", value.inferenceRunId ?: JSONObject.NULL)
                        .put("modelVersion", value.modelVersion)
                    } ?: JSONObject.NULL)
                }))
            }))
            .put("movementReason", planned.movementReason)
            .put("modelVersion", planned.prescription.generatedByModelVersion)
    }))

private fun UserInferenceSnapshot.toJson(profileLabels: Map<String, String>): JSONObject = JSONObject()
    .put("run", JSONObject()
        .put("id", run.id.value)
        .put("modelVersion", run.modelVersion)
        .put("executionMode", run.executionMode.storageValue)
        .put("semanticsMode", run.semanticsMode.storageValue)
        .put("modelManifestId", run.modelManifestId.value)
        .put("calculatedAt", run.calculatedAt.toString())
        .put("evidenceThrough", run.evidenceThrough?.toString() ?: JSONObject.NULL)
        .put("evidenceSetCount", run.evidenceSetCount)
        .put("evidenceObservationCount", run.evidenceObservationCount)
        .put("effectiveIndependentSessionCount", run.effectiveIndependentSessionCount)
        .put("referenceProfileId", run.referenceProfileId.value)
        .put("referenceProfileVersion", run.referenceProfileVersion)
        .put("referenceModelVersion", run.referenceModelVersion)
        .put("recruitmentModelVersion", run.recruitmentModelVersion)
        .put("stimulusModelVersion", run.stimulusModelVersion)
        .put("muscleStateModelVersion", run.muscleStateModelVersion)
        .put("exerciseTranslationModelVersion", run.exerciseTranslationModelVersion))
    .put("modelManifest", modelManifest?.let { manifest -> JSONObject()
        .put("id", manifest.id.value)
        .put("entries", JSONArray(manifest.entries.entries.sortedBy { it.key.storageValue }.map { (component, configId) ->
            JSONObject().put("component", component.storageValue).put("modelConfigId", configId.value)
        }))
    } ?: JSONObject.NULL)
    .put("modelConfigs", JSONArray(modelConfigs.sortedBy { it.component.storageValue }.map { config -> JSONObject()
        .put("id", config.id.value)
        .put("component", config.component.storageValue)
        .put("modelFamily", config.modelFamily)
        .put("modelName", config.modelName)
        .put("semanticVersion", config.semanticVersion)
        .put("configSchemaVersion", config.configSchemaVersion)
        .put("canonicalConfigPayload", config.canonicalConfigPayload)
        .put("createdAt", config.createdAt.toString())
        .put("effectiveAt", config.effectiveAt?.toString() ?: JSONObject.NULL)
    }))
    .put("candidatePosteriorCount", 0)
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
        .put("observationId", estimate.observationId)
        .put("sessionExerciseId", estimate.sessionExerciseId)
        .put("executionProfileVersionId", estimate.executionProfileVersionId.value)
        .put("recruitmentProfileVersionId", estimate.recruitmentProfileVersionId.value)
        .put("segmentId", estimate.segmentId.value)
        .put("side", estimate.side.storageValue)
        .put("role", estimate.role.storageValue)
        .put("recruitmentWeighting", estimate.recruitmentWeighting)
        .put("estimatedStimulus", estimate.estimatedStimulus)
        .put("confidence", estimate.confidence)
        .put("modelVersion", estimate.modelVersion)
    }))
    .put("exerciseTranslationStates", JSONArray(exerciseTranslationStates.map { state -> JSONObject()
        .put("executionProfileVersionId", state.executionProfileVersionId.value)
        .put("side", state.laterality.storageValue)
        .put("label", profileLabels[state.executionProfileVersionId.value] ?: JSONObject.NULL)
        .put("anchors", JSONArray(state.anchors.map { anchor -> JSONObject()
            .put("metric", anchor.metric.storageValue)
            .put("value", anchor.estimate.value)
            .put("uncertainty", anchor.estimate.uncertainty ?: JSONObject.NULL)
            .put("canonicalUnit", anchor.canonicalUnit)
            .put("sourceObservationId", anchor.sourceObservationId)
            .put("sourceSetRecordId", anchor.sourceSetRecordId)
        }))
        .put("sampleCount", state.sampleCount)
        .put("modelVersion", state.modelVersion)
    }))
