package dev.kian.mymettle.inference

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.NBio7FM0DerivedDependencyEntity
import dev.kian.mymettle.data.local.entity.NBio7FM0DerivedStateEntity
import dev.kian.mymettle.domain.equipment.EquipmentInvalidationImpact
import dev.kian.mymettle.engine.inference.DynamicTransferM0SourceSelectionMode
import java.time.Instant

/** Room18 derived-only persistence boundary for frozen N-BIO-7F M0 fit/prediction snapshots. */
class DynamicTransferM0DerivedRepository(
    private val database: MyMettleDatabase,
) {
    private val dao get() = database.nBio7FM0DerivedDao()

    suspend fun persist(snapshot: DynamicTransferM0DerivedSnapshot): String = database.withTransaction {
        DynamicTransferM0DerivedSnapshotCodec.validateStored(snapshot)
        val row = snapshot.toEntity()
        val existing = dao.state(snapshot.id)
        if (existing == null) {
            dao.insertState(row)
            dao.insertDependencies(
                snapshot.dependencyIds.sorted().map {
                    NBio7FM0DerivedDependencyEntity(stateId = snapshot.id, dependencyId = it)
                },
            )
        } else {
            require(existing.scientificallyEquivalent(row)) {
                "Immutable M0 derived state ${snapshot.id} differs from its content-addressed identity; delete and recompute."
            }
            require(dao.dependencies(snapshot.id) == snapshot.dependencyIds.sorted()) {
                "M0 derived dependency roots are incomplete/corrupt; delete and recompute."
            }
        }
        require(dao.dependencies(snapshot.id) == snapshot.dependencyIds.sorted()) {
            "M0 derived dependency persistence failed; transaction must roll back."
        }
        snapshot.id
    }

    suspend fun load(id: String): DynamicTransferM0DerivedSnapshot = database.withTransaction {
        require(id.isNotBlank())
        val row = dao.state(id)
            ?: throw IllegalArgumentException("Missing M0 derived state $id; recomputation is required.")
        row.toSnapshot(dao.dependencies(id).toSet()).also(DynamicTransferM0DerivedSnapshotCodec::validateStored)
    }

    suspend fun discard(id: String): Boolean = database.withTransaction { dao.deleteState(id) == 1 }

    suspend fun deleteAllDerived(): Int = database.withTransaction { dao.deleteAllStates() }

    suspend fun invalidate(impact: EquipmentInvalidationImpact): Set<String> = database.withTransaction {
        val ids = dao.stateIdsDependingOn(impact.sourceDependencyIds.sorted())
        if (ids.isNotEmpty()) dao.deleteStates(ids)
        ids.toSet()
    }

    suspend fun stateIds(): List<String> = database.withTransaction { dao.stateIds() }

    private fun DynamicTransferM0DerivedSnapshot.toEntity() = NBio7FM0DerivedStateEntity(
        id = id,
        stateKind = stateKind.name,
        destinationExecutionProfileVersionId = destinationExecutionProfileVersionId,
        sourceExecutionProfileVersionId = sourceExecutionProfileVersionId,
        side = side,
        relationshipId = relationshipId,
        relationshipVersion = relationshipVersion,
        relationshipPolicyIdentity = relationshipPolicyIdentity,
        relationshipFingerprint = relationshipFingerprint,
        modelConfigId = modelConfigId,
        mathematicalModelIdentity = mathematicalModelIdentity,
        solverIdentity = solverIdentity,
        sourceSelectionPolicyId = sourceSelectionPolicyId,
        sourceSelectionPolicyVersion = sourceSelectionPolicyVersion,
        sourceSelectionPolicyMode = sourceSelectionPolicyMode.name,
        sourceSelectionPolicyIdentity = sourceSelectionPolicyIdentity,
        sourceSelectionPolicyFrozenAt = sourceSelectionPolicyFrozenAt.toString(),
        evidenceThrough = evidenceThrough.toString(),
        predictionCutoff = predictionCutoff?.toString(),
        codecId = codecId,
        codecVersion = codecVersion,
        payloadSchemaVersion = payloadSchemaVersion,
        payload = payload,
        payloadSha256 = payloadSha256,
        createdAt = createdAt.toString(),
    )

    private fun NBio7FM0DerivedStateEntity.toSnapshot(dependencies: Set<String>) =
        DynamicTransferM0DerivedSnapshot(
            id = id,
            stateKind = parseEnum<DynamicTransferM0DerivedStateKind>(stateKind, "state kind"),
            destinationExecutionProfileVersionId = destinationExecutionProfileVersionId,
            sourceExecutionProfileVersionId = sourceExecutionProfileVersionId,
            side = side,
            relationshipId = relationshipId,
            relationshipVersion = relationshipVersion,
            relationshipPolicyIdentity = relationshipPolicyIdentity,
            relationshipFingerprint = relationshipFingerprint,
            modelConfigId = modelConfigId,
            mathematicalModelIdentity = mathematicalModelIdentity,
            solverIdentity = solverIdentity,
            sourceSelectionPolicyId = sourceSelectionPolicyId,
            sourceSelectionPolicyVersion = sourceSelectionPolicyVersion,
            sourceSelectionPolicyMode = parseEnum<DynamicTransferM0SourceSelectionMode>(
                sourceSelectionPolicyMode,
                "source-selection mode",
            ),
            sourceSelectionPolicyIdentity = sourceSelectionPolicyIdentity,
            sourceSelectionPolicyFrozenAt = parseInstant(sourceSelectionPolicyFrozenAt, "source-selection frozenAt"),
            evidenceThrough = parseInstant(evidenceThrough, "evidenceThrough"),
            predictionCutoff = predictionCutoff?.let { parseInstant(it, "predictionCutoff") },
            codecId = codecId,
            codecVersion = codecVersion,
            payloadSchemaVersion = payloadSchemaVersion,
            payload = payload,
            payloadSha256 = payloadSha256,
            dependencyIds = dependencies,
            createdAt = parseInstant(createdAt, "createdAt"),
        )

    private fun NBio7FM0DerivedStateEntity.scientificallyEquivalent(other: NBio7FM0DerivedStateEntity): Boolean =
        copy(createdAt = other.createdAt) == other

    private inline fun <reified T : Enum<T>> parseEnum(value: String, label: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: throw IllegalArgumentException("Unknown M0 derived $label '$value'; recomputation is required.")

    private fun parseInstant(value: String, label: String): Instant = try {
        Instant.parse(value)
    } catch (failure: Exception) {
        throw IllegalArgumentException("Invalid M0 derived $label; recomputation is required.", failure)
    }
}
