package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programme_target",
    foreignKeys = [
        ForeignKey(
            entity = RoutineVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MuscleSegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscleSegmentId"],
        ),
    ],
    indices = [
        Index("routineVersionId"),
        Index("muscleSegmentId"),
        Index(value = ["routineVersionId", "daySymbol", "muscleSegmentId"], unique = true),
    ],
)
data class ProgrammeTargetEntity(
    @PrimaryKey val id: String,
    val routineVersionId: String,
    val daySymbol: String,
    val muscleSegmentId: String,
    val priority: Double,
    val desiredStimulus: Double?,
    val source: String,
)

@Entity(
    tableName = "programme_mode_constraint",
    primaryKeys = ["routineVersionId", "daySymbol", "mode"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineVersionId")],
)
data class ProgrammeModeConstraintEntity(
    val routineVersionId: String,
    val daySymbol: String,
    val mode: String,
    val workingSetBudget: Int,
    val exerciseBudget: Int,
    val minimumSetsPerExercise: Int,
    val targetPriorityFloor: Double,
    val timeBudgetSeconds: Int?,
    val source: String,
    val resolverModelVersion: String,
)

@Entity(
    tableName = "session_target",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MuscleSegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscleSegmentId"],
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("muscleSegmentId"),
        Index(value = ["sessionId", "muscleSegmentId"], unique = true),
    ],
)
data class SessionTargetEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val programmeTargetId: String?,
    val muscleSegmentId: String,
    val priority: Double,
    val desiredStimulus: Double?,
    val source: String,
    val included: Boolean,
    val resolvedPriority: Double,
    val resolutionModelVersion: String,
)

@Entity(
    tableName = "session_constraint",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mode")],
)
data class SessionConstraintEntity(
    @PrimaryKey val sessionId: String,
    val mode: String,
    val workingSetBudget: Int,
    val exerciseBudget: Int,
    val minimumSetsPerExercise: Int,
    val targetPriorityFloor: Double,
    val timeBudgetSeconds: Int?,
    val source: String,
    val resolverModelVersion: String,
)

@Entity(
    tableName = "session_exercise_target",
    primaryKeys = ["sessionExerciseId", "sessionTargetId"],
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SessionTargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionTargetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId"), Index("sessionTargetId")],
)
data class SessionExerciseTargetEntity(
    val sessionExerciseId: String,
    val sessionTargetId: String,
)
