package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: String = "primary",
    val currentRoutineVersionId: String,
    val currentCycleId: String,
    val activeSessionId: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "session_review",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SessionReviewEntity(
    @PrimaryKey val sessionId: String,
    val exerciseOrder: Int?,
    val organisation: Int?,
    val pacing: Int?,
    val delayImpact: Int?,
    val note: String?,
    val recordedAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "health_observation",
    indices = [Index("type"), Index("startTime"), Index(value = ["provider", "sourceRecordId"], unique = true)],
)
/**
 * Pre-temporal scalar Health compatibility row retained for Lite import and future N-BIO-9
 * reconciliation. It is not the canonical store for traces, chunks, routes, or source revisions;
 * those belong to the generic temporal-evidence tables and must not be dual-written here.
 */
data class HealthObservationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val startTime: String,
    val endTime: String?,
    val value: Double?,
    val unit: String?,
    val provider: String,
    val sourceRecordId: String,
    val sourceDevice: String?,
    val payloadJson: String?,
    val importedAt: String,
)

@Entity(tableName = "health_integration_state")
data class HealthIntegrationStateEntity(
    @PrimaryKey val id: String = "primary",
    val provider: String,
    val permissionState: String,
    val lastSyncedAt: String?,
    val lastError: String?,
)
