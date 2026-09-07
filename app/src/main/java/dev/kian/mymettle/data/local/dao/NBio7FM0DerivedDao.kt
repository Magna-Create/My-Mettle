package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.kian.mymettle.data.local.entity.NBio7FM0DerivedDependencyEntity
import dev.kian.mymettle.data.local.entity.NBio7FM0DerivedStateEntity

@Dao
interface NBio7FM0DerivedDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertState(row: NBio7FM0DerivedStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDependencies(rows: List<NBio7FM0DerivedDependencyEntity>)

    @Query("SELECT * FROM n_bio_7f_m0_derived_state WHERE id = :id")
    suspend fun state(id: String): NBio7FM0DerivedStateEntity?

    @Query("SELECT dependencyId FROM n_bio_7f_m0_derived_dependency WHERE stateId = :stateId ORDER BY dependencyId")
    suspend fun dependencies(stateId: String): List<String>

    @Query(
        "SELECT DISTINCT stateId FROM n_bio_7f_m0_derived_dependency " +
            "WHERE dependencyId IN (:dependencyIds) ORDER BY stateId",
    )
    suspend fun stateIdsDependingOn(dependencyIds: List<String>): List<String>

    @Query("DELETE FROM n_bio_7f_m0_derived_state WHERE id = :id")
    suspend fun deleteState(id: String): Int

    @Query("DELETE FROM n_bio_7f_m0_derived_state WHERE id IN (:ids)")
    suspend fun deleteStates(ids: List<String>): Int

    @Query("DELETE FROM n_bio_7f_m0_derived_state")
    suspend fun deleteAllStates(): Int

    @Query("SELECT id FROM n_bio_7f_m0_derived_state ORDER BY id")
    suspend fun stateIds(): List<String>
}
