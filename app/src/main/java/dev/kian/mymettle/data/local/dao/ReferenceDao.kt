package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import dev.kian.mymettle.data.local.entity.MuscleEntity
import dev.kian.mymettle.data.local.entity.MuscleSegmentEntity
import dev.kian.mymettle.data.local.entity.ReferencePhysiologyPriorEntity
import dev.kian.mymettle.data.local.entity.ReferenceProfileEntity

@Dao
interface ReferenceDao {
    @Query("SELECT * FROM muscle ORDER BY id")
    suspend fun muscles(): List<MuscleEntity>

    @Query("SELECT * FROM muscle_segment ORDER BY muscleId, id")
    suspend fun segments(): List<MuscleSegmentEntity>

    @Query("SELECT * FROM muscle_segment WHERE id IN (:ids)")
    suspend fun segments(ids: List<String>): List<MuscleSegmentEntity>

    @Query("SELECT * FROM reference_profile WHERE id = :id LIMIT 1")
    suspend fun profile(id: String): ReferenceProfileEntity?

    @Query("SELECT * FROM reference_physiology_prior WHERE profileId = :profileId ORDER BY targetKind, targetId")
    suspend fun priors(profileId: String): List<ReferencePhysiologyPriorEntity>
}
