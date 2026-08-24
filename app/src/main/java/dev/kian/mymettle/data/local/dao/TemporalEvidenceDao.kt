package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.kian.mymettle.data.local.entity.DerivedEvidenceSummaryEntity
import dev.kian.mymettle.data.local.entity.DerivedEvidenceSummaryInputEntity
import dev.kian.mymettle.data.local.entity.EvidenceTraceChunkEntity
import dev.kian.mymettle.data.local.entity.EvidenceTraceEntity
import dev.kian.mymettle.data.local.entity.EvidenceTraceUiCacheEntity
import dev.kian.mymettle.data.local.entity.ExternalEvidenceArtifactEntity
import dev.kian.mymettle.data.local.entity.ObservationTraceLinkEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseTraceLinkEntity
import dev.kian.mymettle.data.local.entity.SessionTraceLinkEntity
import dev.kian.mymettle.data.local.entity.SetRecordTraceLinkEntity

@Dao
interface TemporalEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExternalArtifacts(values: List<ExternalEvidenceArtifactEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTraces(values: List<EvidenceTraceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTraceChunks(values: List<EvidenceTraceChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionTraceLinks(values: List<SessionTraceLinkEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionExerciseTraceLinks(values: List<SessionExerciseTraceLinkEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSetRecordTraceLinks(values: List<SetRecordTraceLinkEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObservationTraceLinks(values: List<ObservationTraceLinkEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDerivedSummaries(values: List<DerivedEvidenceSummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDerivedSummaryInputs(values: List<DerivedEvidenceSummaryInputEntity>)

    @Upsert
    suspend fun upsertTraceUiCaches(values: List<EvidenceTraceUiCacheEntity>)

    @Query("SELECT * FROM external_evidence_artifact WHERE id = :artifactId LIMIT 1")
    suspend fun externalArtifact(artifactId: String): ExternalEvidenceArtifactEntity?

    @Query("SELECT * FROM external_evidence_artifact WHERE logicalSourceKey = :logicalSourceKey ORDER BY nativeRevision, id")
    suspend fun externalArtifactRevisions(logicalSourceKey: String): List<ExternalEvidenceArtifactEntity>

    @Query(
        """
        SELECT * FROM external_evidence_artifact AS artifact
        WHERE logicalSourceKey = :logicalSourceKey
          AND NOT EXISTS (
              SELECT 1 FROM external_evidence_artifact newer
              WHERE newer.supersedesArtifactId = artifact.id
          )
        ORDER BY nativeRevision DESC, id DESC
        """,
    )
    suspend fun currentExternalArtifacts(logicalSourceKey: String): List<ExternalEvidenceArtifactEntity>

    @Query("SELECT * FROM evidence_trace WHERE id = :traceId LIMIT 1")
    suspend fun trace(traceId: String): EvidenceTraceEntity?

    @Query("SELECT * FROM evidence_trace WHERE id IN (:traceIds) ORDER BY id")
    suspend fun traces(traceIds: List<String>): List<EvidenceTraceEntity>

    @Query("SELECT * FROM evidence_trace_chunk WHERE traceId = :traceId ORDER BY ordinal")
    suspend fun traceChunks(traceId: String): List<EvidenceTraceChunkEntity>

    @Query("SELECT * FROM evidence_trace_chunk WHERE traceId IN (:traceIds) ORDER BY traceId, ordinal")
    suspend fun traceChunks(traceIds: List<String>): List<EvidenceTraceChunkEntity>

    @Query("SELECT traceId FROM session_trace_link WHERE sessionId = :sessionId ORDER BY traceId")
    suspend fun traceIdsForSession(sessionId: String): List<String>

    @Query("SELECT traceId FROM session_exercise_trace_link WHERE sessionExerciseId = :sessionExerciseId ORDER BY traceId")
    suspend fun traceIdsForSessionExercise(sessionExerciseId: String): List<String>

    @Query("SELECT traceId FROM set_record_trace_link WHERE setRecordId = :setRecordId ORDER BY traceId")
    suspend fun traceIdsForSetRecord(setRecordId: String): List<String>

    @Query("SELECT traceId FROM observation_trace_link WHERE observationId = :observationId ORDER BY traceId")
    suspend fun traceIdsForObservation(observationId: String): List<String>

    @Query("SELECT sessionId FROM session_trace_link WHERE traceId = :traceId ORDER BY sessionId")
    suspend fun sessionIdsForTrace(traceId: String): List<String>

    @Query("SELECT sessionExerciseId FROM session_exercise_trace_link WHERE traceId = :traceId ORDER BY sessionExerciseId")
    suspend fun sessionExerciseIdsForTrace(traceId: String): List<String>

    @Query("SELECT setRecordId FROM set_record_trace_link WHERE traceId = :traceId ORDER BY setRecordId")
    suspend fun setRecordIdsForTrace(traceId: String): List<String>

    @Query("SELECT observationId FROM observation_trace_link WHERE traceId = :traceId ORDER BY observationId")
    suspend fun observationIdsForTrace(traceId: String): List<String>

    @Query("SELECT * FROM external_evidence_artifact ORDER BY logicalSourceKey, nativeRevision, id")
    suspend fun allExternalArtifacts(): List<ExternalEvidenceArtifactEntity>

    @Query("SELECT * FROM evidence_trace ORDER BY id")
    suspend fun allTraces(): List<EvidenceTraceEntity>

    @Query("SELECT * FROM evidence_trace_chunk ORDER BY traceId, ordinal")
    suspend fun allTraceChunks(): List<EvidenceTraceChunkEntity>

    @Query("SELECT * FROM session_trace_link ORDER BY sessionId, traceId")
    suspend fun allSessionTraceLinks(): List<SessionTraceLinkEntity>

    @Query("SELECT * FROM session_exercise_trace_link ORDER BY sessionExerciseId, traceId")
    suspend fun allSessionExerciseTraceLinks(): List<SessionExerciseTraceLinkEntity>

    @Query("SELECT * FROM set_record_trace_link ORDER BY setRecordId, traceId")
    suspend fun allSetRecordTraceLinks(): List<SetRecordTraceLinkEntity>

    @Query("SELECT * FROM observation_trace_link ORDER BY observationId, traceId")
    suspend fun allObservationTraceLinks(): List<ObservationTraceLinkEntity>

    @Query("SELECT * FROM derived_evidence_summary ORDER BY id")
    suspend fun allDerivedSummaries(): List<DerivedEvidenceSummaryEntity>

    @Query("DELETE FROM derived_evidence_summary")
    suspend fun deleteAllDerivedSummaries()

    @Query("DELETE FROM evidence_trace_ui_cache")
    suspend fun deleteAllTraceUiCaches()
}
