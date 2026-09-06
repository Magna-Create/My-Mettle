package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_exercise_equipment_binding_correction",
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
            childColumns = ["previousEquipmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = EquipmentInstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["correctedEquipmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("sessionExerciseId"),
        Index("previousEquipmentId"),
        Index("correctedEquipmentId"),
        Index(value = ["sessionExerciseId", "version"], unique = true),
    ],
)
data class SessionExerciseEquipmentBindingCorrectionEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val version: Int,
    val previousEquipmentId: String?,
    val correctedEquipmentId: String?,
    val source: String,
    val reason: String,
    val correctedAt: String,
)

@Entity(
    tableName = "set_observation_equipment_override_correction",
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
            childColumns = ["previousEquipmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = EquipmentInstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["correctedEquipmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("observationId"),
        Index("previousEquipmentId"),
        Index("correctedEquipmentId"),
        Index(value = ["observationId", "version"], unique = true),
    ],
)
data class SetObservationEquipmentOverrideCorrectionEntity(
    @PrimaryKey val id: String,
    val observationId: String,
    val version: Int,
    val previousEquipmentId: String?,
    val correctedEquipmentId: String?,
    val source: String,
    val reason: String,
    val correctedAt: String,
)

@Entity(
    tableName = "set_observation_load_semantics_correction",
    foreignKeys = [
        ForeignKey(
            entity = SetObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("observationId"),
        Index(value = ["observationId", "version"], unique = true),
    ],
)
data class SetObservationLoadSemanticsCorrectionEntity(
    @PrimaryKey val id: String,
    val observationId: String,
    val version: Int,
    val previousExternalLoadAccounting: String?,
    val correctedExternalLoadAccounting: String?,
    val source: String,
    val reason: String,
    val correctedAt: String,
)
