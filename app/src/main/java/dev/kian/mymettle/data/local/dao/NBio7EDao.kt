package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.kian.mymettle.data.local.entity.NBio7EContextModuleStateEntity
import dev.kian.mymettle.data.local.entity.NBio7EContextModuleStatusEntity
import dev.kian.mymettle.data.local.entity.NBio7EContextSignalEntity
import dev.kian.mymettle.data.local.entity.NBio7ERunEntity
import dev.kian.mymettle.data.local.entity.NBio7ETemporalStateEntity

@Dao
interface NBio7EDao {
    @Query("SELECT * FROM n_bio_7e_run WHERE id = :runId LIMIT 1")
    suspend fun run(runId: String): NBio7ERunEntity?

    @Query("SELECT * FROM n_bio_7e_run WHERE userProfileId = :userProfileId ORDER BY calculatedAt DESC, id DESC LIMIT 1")
    suspend fun latestRun(userProfileId: String): NBio7ERunEntity?

    @Query("SELECT * FROM n_bio_7e_temporal_state WHERE runId = :runId ORDER BY candidateLayer, scopeKind, scopeId")
    suspend fun temporalStates(runId: String): List<NBio7ETemporalStateEntity>

    @Query("SELECT * FROM n_bio_7e_context_module_state WHERE runId = :runId ORDER BY moduleId")
    suspend fun moduleStates(runId: String): List<NBio7EContextModuleStateEntity>

    @Query("SELECT * FROM n_bio_7e_context_signal WHERE runId = :runId ORDER BY signalId")
    suspend fun signals(runId: String): List<NBio7EContextSignalEntity>

    @Query("SELECT * FROM n_bio_7e_context_module_status WHERE runId = :runId ORDER BY moduleId, phase")
    suspend fun moduleStatuses(runId: String): List<NBio7EContextModuleStatusEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(value: NBio7ERunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTemporalStates(values: List<NBio7ETemporalStateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertModuleStates(values: List<NBio7EContextModuleStateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSignals(values: List<NBio7EContextSignalEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertModuleStatuses(values: List<NBio7EContextModuleStatusEntity>)

    @Query("DELETE FROM n_bio_7e_context_module_state WHERE runId = :runId")
    suspend fun deleteModuleStates(runId: String)

    @Query("DELETE FROM n_bio_7e_context_signal WHERE runId = :runId")
    suspend fun deleteSignals(runId: String)

    @Query("DELETE FROM n_bio_7e_run WHERE id = :runId")
    suspend fun deleteRun(runId: String)

    @Query("DELETE FROM n_bio_7e_run WHERE userProfileId = :userProfileId")
    suspend fun deleteDerivedForUser(userProfileId: String)

    @Query("DELETE FROM n_bio_7e_run")
    suspend fun deleteAllDerived()

    @Query("DELETE FROM n_bio_7e_temporal_state WHERE candidateLayer = 'context_temporal'")
    suspend fun deleteAllContextTemporalStates()

    @Query("DELETE FROM n_bio_7e_context_signal WHERE sourceModuleId IN (:moduleIds)")
    suspend fun deleteSignalsForModules(moduleIds: List<String>)

    @Query("DELETE FROM n_bio_7e_context_module_status WHERE moduleId IN (:moduleIds)")
    suspend fun deleteStatusesForModules(moduleIds: List<String>)

    @Query("DELETE FROM n_bio_7e_context_module_state WHERE moduleId IN (:moduleIds)")
    suspend fun deleteStatesForModules(moduleIds: List<String>)

    @Query("DELETE FROM n_bio_7e_context_signal")
    suspend fun deleteAllSignals()

    @Query("DELETE FROM n_bio_7e_context_module_status")
    suspend fun deleteAllModuleStatuses()

    @Query("DELETE FROM n_bio_7e_context_module_state")
    suspend fun deleteAllModuleStates()

    /**
     * A changed feature invalidates its owning module(s) and the combined context candidate only.
     * Context-free/dose temporal states and unrelated module memories/signals remain reusable.
     */
    @Transaction
    suspend fun invalidateContextModules(moduleIds: List<String>) {
        if (moduleIds.isEmpty()) return
        deleteAllContextTemporalStates()
        deleteSignalsForModules(moduleIds)
        deleteStatusesForModules(moduleIds)
        deleteStatesForModules(moduleIds)
    }

    /** Deleting the complete annotation substrate preserves non-context 7E temporal candidates. */
    @Transaction
    suspend fun invalidateAllContextConditionedDerived() {
        deleteAllContextTemporalStates()
        deleteAllSignals()
        deleteAllModuleStatuses()
        deleteAllModuleStates()
    }

    @Transaction
    suspend fun insertCompleteRun(
        run: NBio7ERunEntity,
        temporalStates: List<NBio7ETemporalStateEntity>,
        moduleStates: List<NBio7EContextModuleStateEntity>,
        signals: List<NBio7EContextSignalEntity>,
        statuses: List<NBio7EContextModuleStatusEntity>,
    ) {
        insertRun(run)
        insertTemporalStates(temporalStates)
        insertModuleStates(moduleStates)
        insertSignals(signals)
        insertModuleStatuses(statuses)
    }
}
