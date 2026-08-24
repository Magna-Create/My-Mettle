package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "performance_schema",
    indices = [Index(value = ["id", "version"], unique = true)],
)
data class PerformanceSchemaEntity(
    @PrimaryKey val id: String,
    val version: Int,
    val metricFamily: String,
    val createdAt: String,
    val provenance: String,
)

@Entity(
    tableName = "performance_schema_metric",
    primaryKeys = ["performanceSchemaId", "metric"],
    foreignKeys = [
        ForeignKey(
            entity = PerformanceSchemaEntity::class,
            parentColumns = ["id"],
            childColumns = ["performanceSchemaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("performanceSchemaId")],
)
data class PerformanceSchemaMetricEntity(
    val performanceSchemaId: String,
    val metric: String,
    val required: Boolean,
    val targetable: Boolean,
    val defaultUnit: String,
    val minimumCanonical: Double?,
    val maximumCanonical: Double?,
    val incrementCanonical: Double?,
    val allowedCanonicalValuesJson: String?,
)

/** Optional routine preference; unlike the former repMin/repMax it is metric-general. */
@Entity(
    tableName = "routine_metric_target",
    primaryKeys = ["routineVersionId", "slotId", "metric"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineSlotEntity::class,
            parentColumns = ["routineVersionId", "id"],
            childColumns = ["routineVersionId", "slotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["routineVersionId", "slotId"])],
)
data class RoutineMetricTargetEntity(
    val routineVersionId: String,
    val slotId: String,
    val metric: String,
    val targetKind: String,
    val lowerCanonical: Double?,
    val upperCanonical: Double?,
    val canonicalUnit: String,
    val displayUnit: String,
    val source: String,
    val modelVersion: String,
)

@Entity(
    tableName = "session_set_prescription",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionExerciseId"),
        Index(value = ["sessionExerciseId", "setIndex"], unique = true),
    ],
)
data class SessionSetPrescriptionEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val setIndex: Int,
    val kind: String,
    val laterality: String,
)

@Entity(
    tableName = "session_metric_target",
    primaryKeys = ["sessionSetPrescriptionId", "metric"],
    foreignKeys = [
        ForeignKey(
            entity = SessionSetPrescriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionSetPrescriptionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionSetPrescriptionId"), Index("sourceObservationId"), Index("sourceSetRecordId")],
)
data class SessionMetricTargetEntity(
    val sessionSetPrescriptionId: String,
    val metric: String,
    val targetKind: String,
    val lowerCanonical: Double?,
    val upperCanonical: Double?,
    val canonicalUnit: String,
    val displayUnit: String,
    val evidenceSource: String?,
    val sourceObservationId: String?,
    val sourceSetRecordId: String?,
    val inferenceRunId: String?,
    val evidenceAnchorCanonical: Double?,
    val evidenceModelVersion: String?,
)

@Entity(
    tableName = "set_observation",
    foreignKeys = [
        ForeignKey(
            entity = SetRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["setRecordId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExecutionProfileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionProfileVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("setRecordId"),
        Index("executionProfileVersionId"),
        Index(value = ["setRecordId", "ordinal"], unique = true),
        Index(value = ["supersedesObservationId"], unique = true),
        Index("completedAt"),
    ],
)
data class SetObservationEntity(
    @PrimaryKey val id: String,
    val setRecordId: String,
    val executionProfileVersionId: String,
    val ordinal: Int,
    val side: String,
    val completedAt: String,
    val recordedAt: String,
    val source: String,
    /** Non-null overrides the sole session-level snapshot; null inherits it. */
    val bodyMassContextKg: Double?,
    val bodyMassContextSource: String?,
    val supersedesObservationId: String?,
)

@Entity(
    tableName = "set_metric_value",
    primaryKeys = ["observationId", "metric"],
    foreignKeys = [
        ForeignKey(
            entity = SetObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("observationId"), Index("metric")],
)
data class SetMetricValueEntity(
    val observationId: String,
    val metric: String,
    val enteredValue: Double,
    val enteredUnit: String,
    val canonicalValue: Double,
    val canonicalUnit: String,
)

/** Mutable active-workout input buffer. It is not historical performance evidence. */
@Entity(
    tableName = "set_draft_metric_value",
    primaryKeys = ["setRecordId", "metric"],
    foreignKeys = [
        ForeignKey(
            entity = SetRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["setRecordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("setRecordId")],
)
data class SetDraftMetricValueEntity(
    val setRecordId: String,
    val metric: String,
    val enteredValue: Double,
    val enteredUnit: String,
    val updatedAt: String,
)
