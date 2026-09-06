package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.kian.mymettle.data.local.entity.EquipmentFactVersionEntity
import dev.kian.mymettle.data.local.entity.EquipmentInstanceEntity
import dev.kian.mymettle.data.local.entity.PreferredEquipmentBindingEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEquipmentBindingEntity
import dev.kian.mymettle.data.local.entity.SetObservationEquipmentOverrideEntity
import dev.kian.mymettle.data.local.entity.SetObservationLoadSemanticsEntity

@Dao
interface EquipmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEquipmentInstances(rows: List<EquipmentInstanceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEquipmentFactVersions(rows: List<EquipmentFactVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPreferredEquipmentBindings(rows: List<PreferredEquipmentBindingEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionExerciseEquipmentBindings(rows: List<SessionExerciseEquipmentBindingEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSetObservationEquipmentOverrides(rows: List<SetObservationEquipmentOverrideEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSetObservationLoadSemantics(rows: List<SetObservationLoadSemanticsEntity>)

    @Query("SELECT * FROM equipment_instance WHERE id IN (:ids)")
    suspend fun equipmentInstances(ids: List<String>): List<EquipmentInstanceEntity>

    @Query("SELECT * FROM equipment_fact_version WHERE equipmentId = :equipmentId ORDER BY factType, version")
    suspend fun equipmentFactVersions(equipmentId: String): List<EquipmentFactVersionEntity>

    @Query(
        "SELECT * FROM preferred_equipment_binding " +
            "WHERE executionProfileId = :executionProfileId AND supersededAt IS NULL " +
            "ORDER BY effectiveAt DESC LIMIT 1",
    )
    suspend fun currentPreferredEquipmentBinding(executionProfileId: String): PreferredEquipmentBindingEntity?

    @Query("SELECT * FROM session_exercise_equipment_binding WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun sessionExerciseEquipmentBinding(sessionExerciseId: String): SessionExerciseEquipmentBindingEntity?

    @Query("SELECT * FROM set_observation_equipment_override WHERE observationId = :observationId")
    suspend fun setObservationEquipmentOverride(observationId: String): SetObservationEquipmentOverrideEntity?

    @Query("SELECT * FROM set_observation_load_semantics WHERE observationId = :observationId")
    suspend fun setObservationLoadSemantics(observationId: String): SetObservationLoadSemanticsEntity?
}
