package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "n_bio_7f_m0_derived_state",
    indices = [
        Index("stateKind"),
        Index("destinationExecutionProfileVersionId"),
        Index("sourceExecutionProfileVersionId"),
        Index("relationshipId"),
        Index("sourceSelectionPolicyIdentity"),
        Index("evidenceThrough"),
    ],
)
data class NBio7FM0DerivedStateEntity(
    @PrimaryKey val id: String,
    val stateKind: String,
    val destinationExecutionProfileVersionId: String,
    val sourceExecutionProfileVersionId: String,
    val side: String,
    val relationshipId: String,
    val relationshipVersion: Int,
    val relationshipPolicyIdentity: String,
    val relationshipFingerprint: String,
    val modelConfigId: String,
    val mathematicalModelIdentity: String,
    val solverIdentity: String,
    val sourceSelectionPolicyId: String,
    val sourceSelectionPolicyVersion: Int,
    val sourceSelectionPolicyMode: String,
    val sourceSelectionPolicyIdentity: String,
    val sourceSelectionPolicyFrozenAt: String,
    val evidenceThrough: String,
    val predictionCutoff: String?,
    val codecId: String,
    val codecVersion: Int,
    val payloadSchemaVersion: Int,
    val payload: String,
    val payloadSha256: String,
    val createdAt: String,
)

@Entity(
    tableName = "n_bio_7f_m0_derived_dependency",
    primaryKeys = ["stateId", "dependencyId"],
    foreignKeys = [
        ForeignKey(
            entity = NBio7FM0DerivedStateEntity::class,
            parentColumns = ["id"],
            childColumns = ["stateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dependencyId")],
)
data class NBio7FM0DerivedDependencyEntity(
    val stateId: String,
    val dependencyId: String,
)
