package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val units: String,
    val dietaryPreference: String,
    val cycleStartDay: Int,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "body_measurement",
    indices = [Index("recordedAt")],
)
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    val recordedAt: String,
    val weightKg: Double?,
    val heightCm: Double?,
    val source: String,
    val sourceRecordId: String?,
)

@Entity(
    tableName = "exercise",
    indices = [Index("name")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val archived: Boolean,
    val defaultUnit: String,
    val trackingMetric: String,
    val loadRelationship: String,
    val entryBasis: String,
    val progressionStep: Double,
    val essentialCue: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "exercise_memory",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExerciseMemoryEntity(
    @PrimaryKey val exerciseId: String,
    val category: String,
    val equipment: String,
    val fatigueCost: Int,
    val skillDifficulty: Int,
    val setupNotes: String,
    val videoReferenceUrl: String,
    val machineSettings: String,
)

@Entity(
    tableName = "exercise_target_muscle",
    primaryKeys = ["exerciseId", "ordinal"],
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
data class ExerciseTargetMuscleEntity(
    val exerciseId: String,
    val ordinal: Int,
    val muscle: String,
)

@Entity(
    tableName = "exercise_cue",
    primaryKeys = ["exerciseId", "ordinal"],
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
data class ExerciseCueEntity(
    val exerciseId: String,
    val ordinal: Int,
    val cue: String,
)

@Entity(
    tableName = "exercise_common_mistake",
    primaryKeys = ["exerciseId", "ordinal"],
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
data class ExerciseCommonMistakeEntity(
    val exerciseId: String,
    val ordinal: Int,
    val mistake: String,
)

@Entity(
    tableName = "exercise_substitution",
    primaryKeys = ["exerciseId", "ordinal"],
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
data class ExerciseSubstitutionEntity(
    val exerciseId: String,
    val ordinal: Int,
    val substitution: String,
)

@Entity(
    tableName = "exercise_setup_media",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId"), Index(value = ["exerciseId", "sortOrder"], unique = true)],
)
data class ExerciseSetupMediaEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val relativePath: String,
    val mimeType: String,
    val sortOrder: Int,
    val createdAt: String,
)

@Entity(tableName = "routine_version")
data class RoutineVersionEntity(
    @PrimaryKey val id: String,
    val version: Int,
    val parentId: String?,
    val createdAt: String,
    val effectiveAt: String,
    val source: String,
    val changeReason: String,
)

@Entity(
    tableName = "routine_slot",
    foreignKeys = [
        ForeignKey(
            entity = RoutineVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
        ),
    ],
    indices = [
        Index("routineVersionId"),
        Index("exerciseId"),
        Index(value = ["routineVersionId", "daySymbol", "position"], unique = true),
    ],
)
data class RoutineSlotEntity(
    @PrimaryKey val id: String,
    val routineVersionId: String,
    val daySymbol: String,
    val exerciseId: String,
    val position: Int,
    val importance: String,
    val plannedLoad: Double,
    val lockedToDay: Boolean,
)

@Entity(
    tableName = "mode_prescription",
    primaryKeys = ["slotId", "mode"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("slotId")],
)
data class ModePrescriptionEntity(
    val slotId: String,
    val mode: String,
    val included: Boolean,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val deferToAnd: Boolean,
)

@Entity(tableName = "training_cycle")
data class TrainingCycleEntity(
    @PrimaryKey val id: String,
    val startedAt: String,
    val endedAt: String?,
    val status: String,
    val andCompleted: Boolean,
)

@Entity(
    tableName = "cycle_completed_day",
    primaryKeys = ["cycleId", "daySymbol"],
    foreignKeys = [
        ForeignKey(
            entity = TrainingCycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cycleId")],
)
data class CycleCompletedDayEntity(
    val cycleId: String,
    val daySymbol: String,
)

@Entity(
    tableName = "session",
    indices = [
        Index("cycleId"),
        Index("routineVersionId"),
        Index("startedAt"),
        Index("status"),
    ],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val cycleId: String,
    val daySymbol: String,
    val mode: String,
    val routineVersionId: String,
    val status: String,
    val startedAt: String,
    val completedAt: String?,
    val editedAt: String?,
    val discardedAt: String?,
    val excludedFromInsights: Boolean,
    val bodyweightSnapshotKg: Double?,
    val healthExportState: String?,
    val healthClientRecordId: String?,
)

@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("exerciseId"),
        Index(value = ["sessionId", "position"], unique = true),
    ],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val position: Int,
    val exerciseId: String,
    val slotId: String,
    val exerciseNameSnapshot: String,
    val importanceSnapshot: String,
    val trackingMetricSnapshot: String,
    val loadRelationshipSnapshot: String,
    val entryBasisSnapshot: String,
    val bodyweightSnapshotKg: Double?,
    val plannedLoad: Double,
    val prescriptionMode: String,
    val prescriptionIncluded: Boolean,
    val prescribedSets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val deferToAnd: Boolean,
    val status: String,
    val note: String?,
    val startedAt: String?,
    val completedAt: String?,
    val movementReason: String,
)

@Entity(
    tableName = "set_record",
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
data class SetRecordEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val setIndex: Int,
    val load: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMetres: Double?,
    val unit: String,
    val completedAt: String?,
    val note: String?,
    val warmUp: Boolean,
    val kind: String,
)

@Entity(
    tableName = "exercise_reflection",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExerciseReflectionEntity(
    @PrimaryKey val sessionExerciseId: String,
    val targetMuscleEngagement: String,
    val execution: String,
    val enjoyment: String,
    val comfort: String,
    val note: String?,
    val recordedAt: String,
    val updatedAt: String,
)
