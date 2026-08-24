package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "external_evidence_artifact",
    foreignKeys = [
        ForeignKey(
            entity = ExternalEvidenceArtifactEntity::class,
            parentColumns = ["id"],
            childColumns = ["supersedesArtifactId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["logicalSourceKey", "nativeRevision"], unique = true),
        Index(value = ["supersedesArtifactId"], unique = true),
        Index(value = ["provider", "dataOrigin", "sourceRecordType", "sourceRecordId"]),
        Index("sourceState"),
    ],
)
data class ExternalEvidenceArtifactEntity(
    @PrimaryKey val id: String,
    val logicalSourceKey: String,
    val nativeRevision: Int,
    val provider: String?,
    val dataOrigin: String?,
    val sourceRecordType: String?,
    val sourceRecordId: String?,
    val sourceClientRecordId: String?,
    val sourceClientRecordVersion: Long?,
    val sourceDeviceManufacturer: String?,
    val sourceDeviceModel: String?,
    val sourceDeviceType: String?,
    val recordingMethod: String?,
    val sourceLastModifiedEpochSecond: Long?,
    val sourceLastModifiedNano: Int?,
    val importedAtEpochSecond: Long,
    val importedAtNano: Int,
    val sourceStartEpochSecond: Long?,
    val sourceStartNano: Int?,
    val sourceEndEpochSecond: Long?,
    val sourceEndNano: Int?,
    val sourceZoneOffsetMinutes: Int?,
    val timingQuality: String,
    val sourceState: String,
    val supersedesArtifactId: String?,
)

@Entity(
    tableName = "evidence_trace",
    foreignKeys = [
        ForeignKey(
            entity = EvidenceTraceEntity::class,
            parentColumns = ["id"],
            childColumns = ["supersedesTraceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("metricKey"),
        Index("representation"),
        Index("semanticRole"),
        Index(value = ["supersedesTraceId"], unique = true),
        Index(value = ["startedAtEpochSecond", "endedAtEpochSecond"]),
    ],
)
data class EvidenceTraceEntity(
    @PrimaryKey val id: String,
    val metricKey: String,
    val representation: String,
    val intervalSemantics: String,
    val canonicalUnit: String?,
    val acquisitionMethod: String,
    val granularity: String,
    val semanticRole: String,
    val startedAtEpochSecond: Long?,
    val startedAtNano: Int?,
    val endedAtEpochSecond: Long?,
    val endedAtNano: Int?,
    val timingQuality: String,
    val sourceZoneOffsetMinutes: Int?,
    val provenance: String,
    val createdAtEpochSecond: Long,
    val createdAtNano: Int,
    val recordedAtEpochSecond: Long,
    val recordedAtNano: Int,
    val supersedesTraceId: String?,
)

@Entity(
    tableName = "evidence_trace_chunk",
    foreignKeys = [
        ForeignKey(
            entity = EvidenceTraceEntity::class,
            parentColumns = ["id"],
            childColumns = ["traceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExternalEvidenceArtifactEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceArtifactId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("traceId"),
        Index("sourceArtifactId"),
        Index(value = ["traceId", "ordinal"], unique = true),
        Index(value = ["traceId", "payloadSha256"]),
    ],
)
data class EvidenceTraceChunkEntity(
    @PrimaryKey val id: String,
    val traceId: String,
    val sourceArtifactId: String?,
    val ordinal: Int,
    val sourceStartEpochSecond: Long?,
    val sourceStartNano: Int?,
    val sourceEndEpochSecond: Long?,
    val sourceEndNano: Int?,
    val sourceZoneOffsetMinutes: Int?,
    val timingQuality: String,
    val sampleCount: Int,
    val encodingVersion: Int,
    val representation: String,
    val payload: ByteArray,
    val payloadSha256: String,
    val createdAtEpochSecond: Long,
    val createdAtNano: Int,
)

@Entity(
    tableName = "session_trace_link",
    primaryKeys = ["sessionId", "traceId"],
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EvidenceTraceEntity::class, parentColumns = ["id"], childColumns = ["traceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("traceId")],
)
data class SessionTraceLinkEntity(val sessionId: String, val traceId: String)

@Entity(
    tableName = "session_exercise_trace_link",
    primaryKeys = ["sessionExerciseId", "traceId"],
    foreignKeys = [
        ForeignKey(entity = SessionExerciseEntity::class, parentColumns = ["id"], childColumns = ["sessionExerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EvidenceTraceEntity::class, parentColumns = ["id"], childColumns = ["traceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionExerciseId"), Index("traceId")],
)
data class SessionExerciseTraceLinkEntity(val sessionExerciseId: String, val traceId: String)

@Entity(
    tableName = "set_record_trace_link",
    primaryKeys = ["setRecordId", "traceId"],
    foreignKeys = [
        ForeignKey(entity = SetRecordEntity::class, parentColumns = ["id"], childColumns = ["setRecordId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EvidenceTraceEntity::class, parentColumns = ["id"], childColumns = ["traceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("setRecordId"), Index("traceId")],
)
data class SetRecordTraceLinkEntity(val setRecordId: String, val traceId: String)

@Entity(
    tableName = "observation_trace_link",
    primaryKeys = ["observationId", "traceId"],
    foreignKeys = [
        ForeignKey(entity = SetObservationEntity::class, parentColumns = ["id"], childColumns = ["observationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EvidenceTraceEntity::class, parentColumns = ["id"], childColumns = ["traceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("observationId"), Index("traceId")],
)
data class ObservationTraceLinkEntity(val observationId: String, val traceId: String)

@Entity(tableName = "derived_evidence_summary", indices = [Index("algorithmId"), Index("inputFingerprint")])
data class DerivedEvidenceSummaryEntity(
    @PrimaryKey val id: String,
    val summaryType: String,
    val algorithmId: String,
    val algorithmVersion: String,
    val inputFingerprint: String,
    val computedAtEpochSecond: Long,
    val computedAtNano: Int,
    val numericValue: Double?,
    val canonicalUnit: String?,
    val payload: ByteArray?,
)

@Entity(
    tableName = "derived_evidence_summary_input",
    primaryKeys = ["summaryId", "traceId"],
    foreignKeys = [
        ForeignKey(entity = DerivedEvidenceSummaryEntity::class, parentColumns = ["id"], childColumns = ["summaryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EvidenceTraceEntity::class, parentColumns = ["id"], childColumns = ["traceId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("summaryId"), Index("traceId")],
)
data class DerivedEvidenceSummaryInputEntity(val summaryId: String, val traceId: String)

/** Disposable graph/downsample payload. It is never a canonical evidence source. */
@Entity(
    tableName = "evidence_trace_ui_cache",
    foreignKeys = [
        ForeignKey(entity = EvidenceTraceEntity::class, parentColumns = ["id"], childColumns = ["traceId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class EvidenceTraceUiCacheEntity(
    @PrimaryKey val traceId: String,
    val cacheVersion: String,
    val payload: ByteArray,
    val updatedAtEpochSecond: Long,
    val updatedAtNano: Int,
)
