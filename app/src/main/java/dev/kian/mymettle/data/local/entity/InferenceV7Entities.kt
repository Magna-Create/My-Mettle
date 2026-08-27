package dev.kian.mymettle.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Room-safe compact posterior. All numerical posterior fields are either present together or null together. */
data class PosteriorColumns(
    val p05: Double?,
    val p50: Double?,
    val p95: Double?,
    val variance: Double?,
    val observationCount: Int,
    val independentSessionCount: Int,
    val firstEvidenceAt: String?,
    val lastEvidenceAt: String?,
    val evidenceFamily: String,
) {
    init {
        val values = listOf(p05, p50, p95, variance)
        require(values.all { it == null } || values.all { it != null }) {
            "Posterior numerical fields must be all present or all absent."
        }
        if (values.all { it != null }) {
            val lower = requireNotNull(p05)
            val median = requireNotNull(p50)
            val upper = requireNotNull(p95)
            val posteriorVariance = requireNotNull(variance)
            require(lower.isFinite() && median.isFinite() && upper.isFinite() && posteriorVariance.isFinite())
            require(lower <= median && median <= upper)
            require(posteriorVariance >= 0.0)
        }
        require(observationCount >= 0)
        require(independentSessionCount in 0..observationCount)
        require(evidenceFamily.isNotBlank())
    }
}

@Entity(
    tableName = "model_config_definition",
    primaryKeys = ["id"],
    indices = [Index(value = ["component", "semanticVersion"])],
)
data class ModelConfigDefinitionEntity(
    val id: String,
    val component: String,
    val modelFamily: String,
    val modelName: String,
    val semanticVersion: String,
    val configSchemaVersion: Int,
    val canonicalConfigPayload: String,
    val createdAt: String,
    val effectiveAt: String?,
)

@Entity(tableName = "inference_model_manifest", primaryKeys = ["id"])
data class InferenceModelManifestEntity(
    val id: String,
    val createdAt: String,
)

@Entity(
    tableName = "inference_model_manifest_entry",
    primaryKeys = ["manifestId", "component"],
    foreignKeys = [
        ForeignKey(
            entity = InferenceModelManifestEntity::class,
            parentColumns = ["id"],
            childColumns = ["manifestId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ModelConfigDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelConfigId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("manifestId"), Index("modelConfigId")],
)
data class InferenceModelManifestEntryEntity(
    val manifestId: String,
    val component: String,
    val modelConfigId: String,
)

@Entity(
    tableName = "capability_state",
    primaryKeys = ["inferenceRunId", "executionProfileVersionId", "side", "capabilityFamily"],
    foreignKeys = [
        ForeignKey(
            entity = InferenceRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["inferenceRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExecutionProfileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionProfileVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ModelConfigDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelConfigId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("inferenceRunId"), Index("executionProfileVersionId"), Index("modelConfigId")],
)
data class CapabilityStateEntity(
    val inferenceRunId: String,
    val executionProfileVersionId: String,
    val side: String,
    val capabilityFamily: String,
    val canonicalUnit: String?,
    @Embedded(prefix = "posterior_") val posterior: PosteriorColumns,
    val modelConfigId: String,
    val updatedAt: String,
)

@Entity(
    tableName = "capability_parameter_state",
    primaryKeys = ["inferenceRunId", "executionProfileVersionId", "side", "capabilityFamily"],
    foreignKeys = [
        ForeignKey(
            entity = CapabilityStateEntity::class,
            parentColumns = ["inferenceRunId", "executionProfileVersionId", "side", "capabilityFamily"],
            childColumns = ["inferenceRunId", "executionProfileVersionId", "side", "capabilityFamily"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ModelConfigDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelConfigId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["inferenceRunId", "executionProfileVersionId", "side", "capabilityFamily"]),
        Index("modelConfigId"),
    ],
)
data class CapabilityParameterStateEntity(
    val inferenceRunId: String,
    val executionProfileVersionId: String,
    val side: String,
    val capabilityFamily: String,
    val parameterSchemaVersion: Int,
    val encodedParameters: String,
    val modelConfigId: String,
)

@Entity(
    tableName = "set_demand_estimate",
    primaryKeys = ["inferenceRunId", "setObservationId"],
    foreignKeys = [
        ForeignKey(
            entity = InferenceRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["inferenceRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SetObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["setObservationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExecutionProfileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionProfileVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ModelConfigDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelConfigId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("inferenceRunId"), Index("setObservationId"), Index("executionProfileVersionId"), Index("modelConfigId")],
)
data class SetDemandEstimateEntity(
    val inferenceRunId: String,
    val setObservationId: String,
    val executionProfileVersionId: String,
    val side: String,
    @Embedded(prefix = "posterior_") val posterior: PosteriorColumns,
    val modelConfigId: String,
)

@Entity(
    tableName = "muscle_set_dose",
    primaryKeys = ["inferenceRunId", "setObservationId", "muscleSegmentId", "side"],
    foreignKeys = [
        ForeignKey(entity = InferenceRunEntity::class, parentColumns = ["id"], childColumns = ["inferenceRunId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SetObservationEntity::class, parentColumns = ["id"], childColumns = ["setObservationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExecutionProfileVersionEntity::class, parentColumns = ["id"], childColumns = ["executionProfileVersionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = RecruitmentProfileVersionEntity::class, parentColumns = ["id"], childColumns = ["recruitmentProfileVersionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MuscleSegmentEntity::class, parentColumns = ["id"], childColumns = ["muscleSegmentId"]),
        ForeignKey(entity = ModelConfigDefinitionEntity::class, parentColumns = ["id"], childColumns = ["exposureModelConfigId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ModelConfigDefinitionEntity::class, parentColumns = ["id"], childColumns = ["effectiveDoseModelConfigId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index("inferenceRunId"), Index("setObservationId"), Index("executionProfileVersionId"),
        Index("recruitmentProfileVersionId"), Index("muscleSegmentId"), Index("exposureModelConfigId"),
        Index("effectiveDoseModelConfigId"),
    ],
)
data class MuscleSetDoseEntity(
    val inferenceRunId: String,
    val setObservationId: String,
    val executionProfileVersionId: String,
    val recruitmentProfileVersionId: String,
    val muscleSegmentId: String,
    val side: String,
    val recruitmentWeight: Double,
    val conservativeExposure: Double,
    @Embedded(prefix = "effectiveDose_") val effectiveDose: PosteriorColumns?,
    val exposureModelConfigId: String,
    val effectiveDoseModelConfigId: String?,
)

@Entity(
    tableName = "muscle_session_dose",
    primaryKeys = ["inferenceRunId", "sessionId", "muscleSegmentId", "side"],
    foreignKeys = [
        ForeignKey(entity = InferenceRunEntity::class, parentColumns = ["id"], childColumns = ["inferenceRunId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MuscleSegmentEntity::class, parentColumns = ["id"], childColumns = ["muscleSegmentId"]),
        ForeignKey(entity = ModelConfigDefinitionEntity::class, parentColumns = ["id"], childColumns = ["sessionDoseModelConfigId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("inferenceRunId"), Index("sessionId"), Index("muscleSegmentId"), Index("sessionDoseModelConfigId")],
)
data class MuscleSessionDoseEntity(
    val inferenceRunId: String,
    val sessionId: String,
    val muscleSegmentId: String,
    val side: String,
    @Embedded(prefix = "posterior_") val posterior: PosteriorColumns,
    val sessionDoseModelConfigId: String,
)

@Entity(
    tableName = "adaptive_muscle_state",
    primaryKeys = ["inferenceRunId", "muscleSegmentId", "side"],
    foreignKeys = [
        ForeignKey(entity = InferenceRunEntity::class, parentColumns = ["id"], childColumns = ["inferenceRunId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MuscleSegmentEntity::class, parentColumns = ["id"], childColumns = ["muscleSegmentId"]),
        ForeignKey(entity = ModelConfigDefinitionEntity::class, parentColumns = ["id"], childColumns = ["recentStimulusModelConfigId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ModelConfigDefinitionEntity::class, parentColumns = ["id"], childColumns = ["fatigueModelConfigId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ModelConfigDefinitionEntity::class, parentColumns = ["id"], childColumns = ["recoveryModelConfigId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ModelConfigDefinitionEntity::class, parentColumns = ["id"], childColumns = ["developmentModelConfigId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index("inferenceRunId"), Index("muscleSegmentId"), Index("recentStimulusModelConfigId"),
        Index("fatigueModelConfigId"), Index("recoveryModelConfigId"), Index("developmentModelConfigId"),
    ],
)
data class AdaptiveMuscleStateEntity(
    val inferenceRunId: String,
    val muscleSegmentId: String,
    val side: String,
    @Embedded(prefix = "recentStimulus_") val recentStimulus: PosteriorColumns?,
    @Embedded(prefix = "fatigue_") val fatigue: PosteriorColumns?,
    @Embedded(prefix = "recovery_") val recovery: PosteriorColumns?,
    @Embedded(prefix = "development_") val development: PosteriorColumns?,
    val recentStimulusModelConfigId: String?,
    val fatigueModelConfigId: String?,
    val recoveryModelConfigId: String?,
    val developmentModelConfigId: String?,
    val updatedAt: String,
)

@Entity(
    tableName = "skill_state",
    primaryKeys = ["inferenceRunId", "executionProfileVersionId", "side"],
    foreignKeys = [
        ForeignKey(entity = InferenceRunEntity::class, parentColumns = ["id"], childColumns = ["inferenceRunId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExecutionProfileVersionEntity::class, parentColumns = ["id"], childColumns = ["executionProfileVersionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ModelConfigDefinitionEntity::class, parentColumns = ["id"], childColumns = ["modelConfigId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("inferenceRunId"), Index("executionProfileVersionId"), Index("modelConfigId")],
)
data class SkillStateEntity(
    val inferenceRunId: String,
    val executionProfileVersionId: String,
    val side: String,
    @Embedded(prefix = "posterior_") val posterior: PosteriorColumns,
    val modelConfigId: String,
    val updatedAt: String,
)

@Entity(
    tableName = "exercise_translation_prediction",
    primaryKeys = ["inferenceRunId", "destinationExecutionProfileVersionId", "side", "metric"],
    foreignKeys = [
        ForeignKey(entity = InferenceRunEntity::class, parentColumns = ["id"], childColumns = ["inferenceRunId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExecutionProfileVersionEntity::class, parentColumns = ["id"], childColumns = ["destinationExecutionProfileVersionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ModelConfigDefinitionEntity::class, parentColumns = ["id"], childColumns = ["modelConfigId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("inferenceRunId"), Index("destinationExecutionProfileVersionId"), Index("modelConfigId")],
)
data class ExerciseTranslationPredictionEntity(
    val inferenceRunId: String,
    val destinationExecutionProfileVersionId: String,
    val side: String,
    val metric: String,
    val canonicalUnit: String,
    @Embedded(prefix = "posterior_") val posterior: PosteriorColumns?,
    val directDestinationObservationCount: Int,
    val modelConfigId: String,
)

@Entity(
    tableName = "exercise_translation_source",
    primaryKeys = ["inferenceRunId", "destinationExecutionProfileVersionId", "side", "metric", "sourceExecutionProfileVersionId"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseTranslationPredictionEntity::class,
            parentColumns = ["inferenceRunId", "destinationExecutionProfileVersionId", "side", "metric"],
            childColumns = ["inferenceRunId", "destinationExecutionProfileVersionId", "side", "metric"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = ExecutionProfileVersionEntity::class, parentColumns = ["id"], childColumns = ["sourceExecutionProfileVersionId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["inferenceRunId", "destinationExecutionProfileVersionId", "side", "metric"]),
        Index("sourceExecutionProfileVersionId"),
    ],
)
data class ExerciseTranslationSourceEntity(
    val inferenceRunId: String,
    val destinationExecutionProfileVersionId: String,
    val side: String,
    val metric: String,
    val sourceExecutionProfileVersionId: String,
    val similarityWeight: Double?,
    val directDestinationEvidence: Boolean,
)
