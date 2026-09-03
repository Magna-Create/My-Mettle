package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One immutable shadow recomputation. Deleting a run deletes only 7E-derived rows; raw context,
 * workout evidence, 7C capability and 7D dose rows remain owned by their existing tables.
 */
@Entity(
    tableName = "n_bio_7e_run",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["userProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InferenceRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceInferenceRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userProfileId"), Index("sourceInferenceRunId"), Index("calculatedAt")],
)
data class NBio7ERunEntity(
    val id: String,
    val userProfileId: String,
    val sourceInferenceRunId: String,
    val temporalModelConfigId: String,
    val contextProtocolVersion: Int,
    val signalSchemaVersion: Int,
    val solverIdentity: String,
    val executionMode: String,
    val pd001Status: String,
    val pd002Status: String,
    val pd003Status: String,
    val calculatedAt: String,
)

@Entity(
    tableName = "n_bio_7e_temporal_state",
    primaryKeys = ["runId", "candidateLayer", "scopeKind", "scopeId"],
    foreignKeys = [
        ForeignKey(
            entity = NBio7ERunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index(value = ["scopeKind", "scopeId"])],
)
data class NBio7ETemporalStateEntity(
    val runId: String,
    val candidateLayer: String,
    val scopeKind: String,
    /** Empty string is the canonical database representation for a null/systemic scope id. */
    val scopeId: String,
    val stateSchemaVersion: Int,
    val persistentMean: Double,
    val transientMean: Double,
    val doseCoefficientMean: Double,
    val covariancePp: Double,
    val covariancePt: Double,
    val covariancePd: Double,
    val covarianceTt: Double,
    val covarianceTd: Double,
    val covarianceDd: Double,
    val horizon: String,
    val observationCount: Int,
    val independentSessionCount: Int,
)

@Entity(
    tableName = "n_bio_7e_context_module_state",
    primaryKeys = ["runId", "moduleId"],
    foreignKeys = [
        ForeignKey(
            entity = NBio7ERunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index("moduleId")],
)
data class NBio7EContextModuleStateEntity(
    val runId: String,
    val moduleId: String,
    val moduleModelVersion: String,
    val moduleConfigId: String,
    val stateSchemaVersion: Int,
    val encodedState: String,
    val evidenceThrough: String?,
    val updatedAt: String,
)

@Entity(
    tableName = "n_bio_7e_context_signal",
    primaryKeys = ["runId", "signalId"],
    foreignKeys = [
        ForeignKey(
            entity = NBio7ERunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index("sourceModuleId"), Index(value = ["target", "scopeKind", "scopeId"])],
)
data class NBio7EContextSignalEntity(
    val runId: String,
    val signalId: String,
    val signalSchemaVersion: Int,
    val sourceModuleId: String,
    val moduleModelVersion: String,
    val moduleConfigId: String,
    val sourceFeatureId: String,
    val sourceFeatureSchemaVersion: Int,
    val target: String,
    val scopeKind: String,
    val scopeId: String,
    val effectiveFrom: String,
    val effectiveUntil: String?,
    val effectRepresentation: String,
    val locationMean: Double?,
    val variance: Double?,
    val evidenceRowCount: Int,
    val independentSessionCount: Int,
    val independentEpisodeCount: Int,
    val evidenceMaturity: String,
    val correlationGroupId: String,
    val episodeId: String?,
    /** Sorted length-prefixed set encoding; this is derived provenance, never raw note content. */
    val encodedSourceEvidenceIds: String,
    val encodedUpstreamModelIdentities: String,
    val publishedAt: String,
    val status: String,
    val failureCode: String?,
)

@Entity(
    tableName = "n_bio_7e_context_module_status",
    primaryKeys = ["runId", "moduleId", "phase"],
    foreignKeys = [
        ForeignKey(
            entity = NBio7ERunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index("moduleId")],
)
data class NBio7EContextModuleStatusEntity(
    val runId: String,
    val moduleId: String,
    val phase: String,
    val status: String,
    val failureCode: String?,
    /** Bounded diagnostic, never a raw note or stack trace. */
    val failureSummary: String?,
    val recordedAt: String,
)
