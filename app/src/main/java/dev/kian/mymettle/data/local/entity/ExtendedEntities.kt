package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: String = "primary",
    val currentRoutineVersionId: String,
    val currentCycleId: String,
    val activeSessionId: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "exercise_muscle_load",
    primaryKeys = ["exerciseId", "muscle"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class ExerciseMuscleLoadEntity(
    val exerciseId: String,
    val muscle: String,
    val proportion: Double,
    val role: String,
    val confidence: Double,
    val source: String?,
)

@Entity(
    tableName = "experiment",
    indices = [Index("exerciseId"), Index("routineSlotId"), Index("status")],
)
data class ExperimentEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val routineSlotId: String,
    val exerciseName: String,
    val hypothesis: String,
    val baselineLoad: Double,
    val proposedLoad: Double,
    val targetRepMin: Int,
    val status: String,
    val createdAt: String,
    val activatedAt: String?,
    val testedSessionId: String?,
    val evidenceSummary: String?,
    val adoptedRoutineVersionId: String?,
)

@Entity(
    tableName = "health_observation",
    indices = [Index("type"), Index("startTime"), Index(value = ["provider", "sourceRecordId"], unique = true)],
)
data class HealthObservationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val startTime: String,
    val endTime: String?,
    val value: Double?,
    val unit: String?,
    val provider: String,
    val sourceRecordId: String,
    val sourceDevice: String?,
    val payloadJson: String?,
    val importedAt: String,
)

@Entity(tableName = "health_integration_state")
data class HealthIntegrationStateEntity(
    @PrimaryKey val id: String = "primary",
    val provider: String,
    val permissionState: String,
    val lastSyncedAt: String?,
    val lastError: String?,
)
