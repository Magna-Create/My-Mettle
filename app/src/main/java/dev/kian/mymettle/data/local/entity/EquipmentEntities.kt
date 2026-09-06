package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "equipment_instance",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["userProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userProfileId"), Index("archivedAt")],
)
data class EquipmentInstanceEntity(
    @PrimaryKey val id: String,
    val userProfileId: String,
    val localLabel: String?,
    val source: String,
    val createdAt: String,
    val archivedAt: String?,
)

@Entity(
    tableName = "equipment_fact_version",
    foreignKeys = [
        ForeignKey(
            entity = EquipmentInstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("equipmentId"),
        Index(value = ["equipmentId", "factType", "version"], unique = true),
        Index(value = ["equipmentId", "factType", "supersededAt"]),
    ],
)
data class EquipmentFactVersionEntity(
    @PrimaryKey val id: String,
    val equipmentId: String,
    val factType: String,
    val version: Int,
    val valueKind: String,
    val textValue: String?,
    val numericValue: Double?,
    val unit: String?,
    val scope: String?,
    val provenanceType: String,
    val provenanceReference: String?,
    val quality: String?,
    val createdAt: String,
    val effectiveAt: String,
    val supersededAt: String?,
)

@Entity(
    tableName = "preferred_equipment_binding",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseExecutionProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EquipmentInstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("executionProfileId"),
        Index("equipmentId"),
        Index(value = ["executionProfileId", "supersededAt"]),
    ],
)
data class PreferredEquipmentBindingEntity(
    @PrimaryKey val id: String,
    val executionProfileId: String,
    val equipmentId: String,
    val effectiveAt: String,
    val supersededAt: String?,
    val source: String,
    val createdAt: String,
)

@Entity(
    tableName = "session_exercise_equipment_binding",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EquipmentInstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("equipmentId")],
)
data class SessionExerciseEquipmentBindingEntity(
    @PrimaryKey val sessionExerciseId: String,
    val equipmentId: String,
    val source: String,
    val boundAt: String,
)

@Entity(
    tableName = "set_observation_equipment_override",
    foreignKeys = [
        ForeignKey(
            entity = SetObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EquipmentInstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("equipmentId")],
)
data class SetObservationEquipmentOverrideEntity(
    @PrimaryKey val observationId: String,
    val equipmentId: String,
    val source: String,
    val boundAt: String,
)

@Entity(
    tableName = "set_observation_load_semantics",
    foreignKeys = [
        ForeignKey(
            entity = SetObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SetObservationLoadSemanticsEntity(
    @PrimaryKey val observationId: String,
    val externalLoadAccounting: String,
    val source: String,
    val recordedAt: String,
)
