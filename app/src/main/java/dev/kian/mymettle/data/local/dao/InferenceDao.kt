package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.kian.mymettle.data.local.entity.AdaptiveMuscleStateEntity
import dev.kian.mymettle.data.local.entity.CapabilityParameterStateEntity
import dev.kian.mymettle.data.local.entity.CapabilityStateEntity
import dev.kian.mymettle.data.local.entity.ExerciseTranslationMetricAnchorEntity
import dev.kian.mymettle.data.local.entity.ExerciseTranslationPredictionEntity
import dev.kian.mymettle.data.local.entity.ExerciseTranslationSourceEntity
import dev.kian.mymettle.data.local.entity.ExerciseTranslationStateEntity
import dev.kian.mymettle.data.local.entity.InferenceModelManifestEntity
import dev.kian.mymettle.data.local.entity.InferenceModelManifestEntryEntity
import dev.kian.mymettle.data.local.entity.InferenceRunEntity
import dev.kian.mymettle.data.local.entity.ModelConfigDefinitionEntity
import dev.kian.mymettle.data.local.entity.MuscleSegmentEntity
import dev.kian.mymettle.data.local.entity.MuscleSessionDoseEntity
import dev.kian.mymettle.data.local.entity.MuscleSetDoseEntity
import dev.kian.mymettle.data.local.entity.MuscleStateSnapshotEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.ReferenceProfileEntity
import dev.kian.mymettle.data.local.entity.SetDemandEstimateEntity
import dev.kian.mymettle.data.local.entity.SetMetricValueEntity
import dev.kian.mymettle.data.local.entity.SkillStateEntity
import dev.kian.mymettle.data.local.entity.StimulusEstimateEntity

data class CompletedSetEvidenceRow(
    val setRecordId: String,
    val observationId: String,
    val sessionId: String,
    val sessionExerciseId: String,
    val executionProfileVersionId: String,
    val metricFamily: String,
    val side: String,
    val completedAt: String,
    val observationBodyMassContextKg: Double?,
    val sessionBodyMassSnapshotKg: Double?,
    val warmUp: Boolean,
    val kind: String,
)

@Dao
interface InferenceDao {
    @Query("SELECT id FROM user_profile ORDER BY createdAt, id")
    suspend fun userProfileIds(): List<String>

    @Query(
        """
        SELECT
            sr.id AS setRecordId,
            so.id AS observationId,
            s.id AS sessionId,
            sr.sessionExerciseId AS sessionExerciseId,
            so.executionProfileVersionId AS executionProfileVersionId,
            epv.metricFamily AS metricFamily,
            so.side AS side,
            so.completedAt AS completedAt,
            so.bodyMassContextKg AS observationBodyMassContextKg,
            s.bodyweightSnapshotKg AS sessionBodyMassSnapshotKg,
            sr.warmUp AS warmUp,
            sr.kind AS kind
        FROM set_record AS sr
        INNER JOIN session_exercise AS se ON se.id = sr.sessionExerciseId
        INNER JOIN session AS s ON s.id = se.sessionId
        INNER JOIN set_observation AS so ON so.setRecordId = sr.id
        INNER JOIN execution_profile_version AS epv ON epv.id = so.executionProfileVersionId
        WHERE s.status = 'completed'
          AND s.excludedFromInsights = 0
          AND NOT EXISTS (SELECT 1 FROM set_observation newer WHERE newer.supersedesObservationId = so.id)
        ORDER BY so.completedAt, sr.id, so.ordinal
        """,
    )
    suspend fun completedSetEvidence(): List<CompletedSetEvidenceRow>

    @Query("SELECT * FROM set_metric_value WHERE observationId IN (:observationIds) ORDER BY observationId, metric")
    suspend fun completedMetricValues(observationIds: List<String>): List<SetMetricValueEntity>

    @Query(
        """
        SELECT epv.id AS executionProfileVersionId,
               ra.recruitmentProfileVersionId AS recruitmentProfileVersionId,
               ra.muscleSegmentId AS muscleSegmentId,
               ra.role AS role, ra.weighting AS weighting, ra.confidence AS confidence
        FROM execution_profile_version epv
        INNER JOIN recruitment_allocation ra
          ON ra.recruitmentProfileVersionId = epv.recruitmentProfileVersionId
        WHERE epv.id IN (:executionProfileVersionIds)
        """,
    )
    suspend fun recruitmentAllocations(executionProfileVersionIds: List<String>): List<RecruitmentEvidenceRow>

    @Query("SELECT * FROM muscle_segment WHERE statePolicy != 'SHARED_PARENT' ORDER BY id")
    suspend fun independentlyTrackedSegments(): List<MuscleSegmentEntity>

    @Query("SELECT * FROM reference_profile ORDER BY version DESC, id LIMIT 1")
    suspend fun latestReferenceProfile(): ReferenceProfileEntity?

    /** Normal workout UX deliberately reads only the conservative benchmark until v7 promotion. */
    @Query(
        """
        SELECT * FROM inference_run
        WHERE userProfileId = :userProfileId AND executionMode = 'benchmark_v0'
        ORDER BY calculatedAt DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun latestInferenceRun(userProfileId: String): InferenceRunEntity?

    @Query("SELECT * FROM inference_run WHERE id = :inferenceRunId LIMIT 1")
    suspend fun inferenceRun(inferenceRunId: String): InferenceRunEntity?

    @Query("SELECT * FROM inference_run WHERE userProfileId = :userProfileId ORDER BY calculatedAt DESC, id DESC")
    suspend fun inferenceRuns(userProfileId: String): List<InferenceRunEntity>

    @Query("SELECT * FROM model_config_definition WHERE id = :id LIMIT 1")
    suspend fun modelConfigDefinition(id: String): ModelConfigDefinitionEntity?

    @Query("SELECT * FROM model_config_definition WHERE id IN (:ids) ORDER BY component, id")
    suspend fun modelConfigDefinitions(ids: List<String>): List<ModelConfigDefinitionEntity>

    @Query("SELECT * FROM inference_model_manifest WHERE id = :id LIMIT 1")
    suspend fun inferenceModelManifest(id: String): InferenceModelManifestEntity?

    @Query("SELECT * FROM inference_model_manifest_entry WHERE manifestId = :manifestId ORDER BY component")
    suspend fun inferenceModelManifestEntries(manifestId: String): List<InferenceModelManifestEntryEntity>

    @Query("SELECT * FROM muscle_state_snapshot WHERE inferenceRunId = :inferenceRunId ORDER BY muscleSegmentId, side")
    suspend fun muscleStateSnapshots(inferenceRunId: String): List<MuscleStateSnapshotEntity>

    @Query("SELECT * FROM stimulus_estimate WHERE inferenceRunId = :inferenceRunId ORDER BY setRecordId, muscleSegmentId, side")
    suspend fun stimulusEstimates(inferenceRunId: String): List<StimulusEstimateEntity>

    @Query("SELECT * FROM exercise_translation_state WHERE inferenceRunId = :inferenceRunId ORDER BY executionProfileVersionId, side")
    suspend fun exerciseTranslationStates(inferenceRunId: String): List<ExerciseTranslationStateEntity>

    @Query("SELECT * FROM exercise_translation_metric_anchor WHERE inferenceRunId = :inferenceRunId ORDER BY executionProfileVersionId, side, metric")
    suspend fun exerciseTranslationMetricAnchors(inferenceRunId: String): List<ExerciseTranslationMetricAnchorEntity>

    @Query("SELECT * FROM capability_state WHERE inferenceRunId = :inferenceRunId ORDER BY executionProfileVersionId, side, capabilityFamily")
    suspend fun capabilityStates(inferenceRunId: String): List<CapabilityStateEntity>

    @Query("SELECT * FROM capability_parameter_state WHERE inferenceRunId = :inferenceRunId ORDER BY executionProfileVersionId, side, capabilityFamily")
    suspend fun capabilityParameterStates(inferenceRunId: String): List<CapabilityParameterStateEntity>

    @Query("SELECT * FROM set_demand_estimate WHERE inferenceRunId = :inferenceRunId ORDER BY setObservationId")
    suspend fun setDemandEstimates(inferenceRunId: String): List<SetDemandEstimateEntity>

    @Query("SELECT * FROM muscle_set_dose WHERE inferenceRunId = :inferenceRunId ORDER BY setObservationId, muscleSegmentId, side")
    suspend fun muscleSetDoses(inferenceRunId: String): List<MuscleSetDoseEntity>

    @Query("SELECT * FROM muscle_session_dose WHERE inferenceRunId = :inferenceRunId ORDER BY sessionId, muscleSegmentId, side")
    suspend fun muscleSessionDoses(inferenceRunId: String): List<MuscleSessionDoseEntity>

    @Query("SELECT * FROM adaptive_muscle_state WHERE inferenceRunId = :inferenceRunId ORDER BY muscleSegmentId, side")
    suspend fun adaptiveMuscleStates(inferenceRunId: String): List<AdaptiveMuscleStateEntity>

    @Query("SELECT * FROM skill_state WHERE inferenceRunId = :inferenceRunId ORDER BY executionProfileVersionId, side")
    suspend fun skillStates(inferenceRunId: String): List<SkillStateEntity>

    @Query("SELECT * FROM exercise_translation_prediction WHERE inferenceRunId = :inferenceRunId ORDER BY destinationExecutionProfileVersionId, side, metric")
    suspend fun exerciseTranslationPredictions(inferenceRunId: String): List<ExerciseTranslationPredictionEntity>

    @Query("SELECT * FROM exercise_translation_source WHERE inferenceRunId = :inferenceRunId ORDER BY destinationExecutionProfileVersionId, side, metric, sourceExecutionProfileVersionId")
    suspend fun exerciseTranslationSources(inferenceRunId: String): List<ExerciseTranslationSourceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertModelConfigDefinition(value: ModelConfigDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInferenceModelManifest(value: InferenceModelManifestEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInferenceModelManifestEntries(values: List<InferenceModelManifestEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInferenceRun(value: InferenceRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMuscleStateSnapshots(values: List<MuscleStateSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStimulusEstimates(values: List<StimulusEstimateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExerciseTranslationStates(values: List<ExerciseTranslationStateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExerciseTranslationMetricAnchors(values: List<ExerciseTranslationMetricAnchorEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCapabilityStates(values: List<CapabilityStateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCapabilityParameterStates(values: List<CapabilityParameterStateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSetDemandEstimates(values: List<SetDemandEstimateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMuscleSetDoses(values: List<MuscleSetDoseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMuscleSessionDoses(values: List<MuscleSessionDoseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAdaptiveMuscleStates(values: List<AdaptiveMuscleStateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSkillStates(values: List<SkillStateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExerciseTranslationPredictions(values: List<ExerciseTranslationPredictionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExerciseTranslationSources(values: List<ExerciseTranslationSourceEntity>)

    @Query("DELETE FROM inference_run WHERE id = :inferenceRunId")
    suspend fun deleteInferenceRun(inferenceRunId: String)

    @Query("DELETE FROM inference_run WHERE userProfileId = :userProfileId")
    suspend fun deleteDerivedState(userProfileId: String)
}

data class RecruitmentEvidenceRow(
    val executionProfileVersionId: String,
    val recruitmentProfileVersionId: String,
    val muscleSegmentId: String,
    val role: String,
    val weighting: Double,
    val confidence: Double,
)
