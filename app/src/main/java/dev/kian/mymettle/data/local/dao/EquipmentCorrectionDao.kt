package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.kian.mymettle.data.local.entity.SessionExerciseEquipmentBindingCorrectionEntity
import dev.kian.mymettle.data.local.entity.SetObservationEquipmentOverrideCorrectionEntity
import dev.kian.mymettle.data.local.entity.SetObservationLoadSemanticsCorrectionEntity

@Dao
interface EquipmentCorrectionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionExerciseEquipmentBindingCorrections(
        rows: List<SessionExerciseEquipmentBindingCorrectionEntity>,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSetObservationEquipmentOverrideCorrections(
        rows: List<SetObservationEquipmentOverrideCorrectionEntity>,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSetObservationLoadSemanticsCorrections(
        rows: List<SetObservationLoadSemanticsCorrectionEntity>,
    )

    @Query(
        "SELECT * FROM session_exercise_equipment_binding_correction " +
            "WHERE sessionExerciseId = :sessionExerciseId ORDER BY version",
    )
    suspend fun sessionExerciseEquipmentBindingCorrections(
        sessionExerciseId: String,
    ): List<SessionExerciseEquipmentBindingCorrectionEntity>

    @Query(
        "SELECT * FROM set_observation_equipment_override_correction " +
            "WHERE observationId = :observationId ORDER BY version",
    )
    suspend fun setObservationEquipmentOverrideCorrections(
        observationId: String,
    ): List<SetObservationEquipmentOverrideCorrectionEntity>

    @Query(
        "SELECT * FROM set_observation_load_semantics_correction " +
            "WHERE observationId = :observationId ORDER BY version",
    )
    suspend fun setObservationLoadSemanticsCorrections(
        observationId: String,
    ): List<SetObservationLoadSemanticsCorrectionEntity>
}
