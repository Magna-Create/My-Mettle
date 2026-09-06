package dev.kian.mymettle.equipment

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.dao.EquipmentDao
import dev.kian.mymettle.data.local.entity.EquipmentFactVersionEntity
import dev.kian.mymettle.data.local.entity.EquipmentInstanceEntity
import dev.kian.mymettle.data.local.entity.PreferredEquipmentBindingEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEquipmentBindingEntity
import dev.kian.mymettle.data.local.entity.SetObservationEquipmentOverrideEntity
import dev.kian.mymettle.data.local.entity.SetObservationLoadSemanticsEntity
import dev.kian.mymettle.domain.equipment.EquipmentBindingResolutionSource
import dev.kian.mymettle.domain.equipment.EquipmentFactProvenance
import dev.kian.mymettle.domain.equipment.EquipmentFactType
import dev.kian.mymettle.domain.equipment.EquipmentFactUnit
import dev.kian.mymettle.domain.equipment.EquipmentFactValue
import dev.kian.mymettle.domain.equipment.EquipmentFactValueKind
import dev.kian.mymettle.domain.equipment.EquipmentFactVersion
import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.EquipmentInstance
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.equipment.ObservationEquipmentOverride
import dev.kian.mymettle.domain.equipment.ObservationLoadSemantics
import dev.kian.mymettle.domain.equipment.PreferredEquipmentBinding
import dev.kian.mymettle.domain.equipment.ResolvedEquipmentBinding
import dev.kian.mymettle.domain.equipment.SessionExerciseEquipmentBinding
import java.time.Instant

interface EquipmentTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

private class RoomEquipmentTransactionRunner(
    private val database: MyMettleDatabase,
) : EquipmentTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}

/**
 * Canonical write/read boundary for 7F equipment context.
 *
 * Preference and actual-use history deliberately use separate methods and storage. Historical
 * observation resolution never consults today's preferred equipment.
 */
class EquipmentContextRepository(
    private val dao: EquipmentDao,
    private val transactions: EquipmentTransactionRunner,
) {
    constructor(database: MyMettleDatabase) : this(
        dao = database.equipmentDao(),
        transactions = RoomEquipmentTransactionRunner(database),
    )

    suspend fun createEquipment(userProfileId: String, equipment: EquipmentInstance) = transactions.run {
        require(userProfileId.isNotBlank())
        if (dao.equipmentInstances(listOf(equipment.id.value)).isNotEmpty()) {
            throw EquipmentContextException("Equipment ${equipment.id.value} already exists.")
        }
        dao.insertEquipmentInstances(
            listOf(
                EquipmentInstanceEntity(
                    id = equipment.id.value,
                    userProfileId = userProfileId,
                    localLabel = equipment.localLabel,
                    source = equipment.source,
                    createdAt = equipment.createdAt,
                    archivedAt = equipment.archivedAt,
                ),
            ),
        )
    }

    suspend fun publishFact(version: EquipmentFactVersion) = transactions.run {
        requireEquipmentExists(version.equipmentId)
        require(version.supersededAt == null) { "A newly published equipment fact must be current." }

        val sameType = dao.equipmentFactVersions(version.equipmentId.value)
            .filter { it.factType == version.factType.storageValue }
        val current = sameType.filter { it.supersededAt == null }
        if (current.size > 1) {
            throw EquipmentContextException(
                "Equipment ${version.equipmentId.value} has multiple current ${version.factType.storageValue} facts.",
            )
        }

        val predecessor = current.singleOrNull()
        if (sameType.isEmpty()) {
            require(version.version == 1) { "The first ${version.factType.storageValue} fact must be version 1." }
        } else {
            if (predecessor == null || predecessor.version != sameType.maxOf { it.version }) {
                throw EquipmentContextException(
                    "Cannot extend ${version.factType.storageValue}; its current fact lifecycle is not well formed.",
                )
            }
            require(version.version == predecessor.version + 1) {
                "${version.factType.storageValue} fact versions must be contiguous."
            }
            requireNotBefore(version.effectiveAt, predecessor.effectiveAt, "Equipment fact successor")
        }

        dao.insertEquipmentFactVersions(listOf(version.toEntity()))
        if (predecessor != null && dao.supersedeEquipmentFactVersion(predecessor.id, version.effectiveAt) != 1) {
            throw EquipmentContextException("The preceding equipment fact changed while publishing its successor.")
        }
    }

    suspend fun setPreferredEquipment(binding: PreferredEquipmentBinding): PreferredEquipmentBinding = transactions.run {
        requireEquipmentExists(binding.equipmentId)
        require(binding.supersededAt == null) { "A newly selected preference must be current." }

        val currentRows = dao.currentPreferredEquipmentBindings(binding.executionProfileId)
        if (currentRows.size > 1) {
            throw EquipmentContextException(
                "Execution profile ${binding.executionProfileId} has multiple current equipment preferences.",
            )
        }
        val current = currentRows.singleOrNull()
        if (current != null) {
            requireNotBefore(binding.effectiveAt, current.effectiveAt, "Equipment preference change")
            if (current.equipmentId == binding.equipmentId.value) return@run current.toDomain()
        }

        dao.insertPreferredEquipmentBindings(listOf(binding.toEntity()))
        if (current != null && dao.supersedePreferredEquipmentBinding(current.id, binding.effectiveAt) != 1) {
            throw EquipmentContextException("The preceding equipment preference changed while publishing its successor.")
        }
        binding
    }

    suspend fun currentPreferredEquipment(executionProfileId: String): EquipmentId? = transactions.run {
        require(executionProfileId.isNotBlank())
        val current = dao.currentPreferredEquipmentBindings(executionProfileId)
        if (current.size > 1) {
            throw EquipmentContextException("Execution profile $executionProfileId has multiple current equipment preferences.")
        }
        current.singleOrNull()?.let { EquipmentId(it.equipmentId) }
    }

    suspend fun bindSessionActualEquipment(binding: SessionExerciseEquipmentBinding) = transactions.run {
        requireEquipmentExists(binding.equipmentId)
        val entity = binding.toEntity()
        val existing = dao.sessionExerciseEquipmentBinding(binding.sessionExerciseId)
        when {
            existing == null -> dao.insertSessionExerciseEquipmentBindings(listOf(entity))
            existing == entity -> Unit
            else -> throw EquipmentContextException(
                "Session exercise ${binding.sessionExerciseId} already has immutable actual-equipment history.",
            )
        }
    }

    suspend fun bindObservationEquipmentOverride(binding: ObservationEquipmentOverride) = transactions.run {
        requireEquipmentExists(binding.equipmentId)
        val entity = binding.toEntity()
        val existing = dao.setObservationEquipmentOverride(binding.observationId)
        when {
            existing == null -> dao.insertSetObservationEquipmentOverrides(listOf(entity))
            existing == entity -> Unit
            else -> throw EquipmentContextException(
                "Observation ${binding.observationId} already has immutable equipment-override history.",
            )
        }
    }

    suspend fun recordObservationLoadSemantics(semantics: ObservationLoadSemantics) = transactions.run {
        val entity = semantics.toEntity()
        val existing = dao.setObservationLoadSemantics(semantics.observationId)
        when {
            existing == null -> dao.insertSetObservationLoadSemantics(listOf(entity))
            existing == entity -> Unit
            else -> throw EquipmentContextException(
                "Observation ${semantics.observationId} already has immutable external-load semantics.",
            )
        }
    }

    suspend fun resolveHistoricalEquipment(observationId: String): ResolvedEquipmentBinding? = transactions.run {
        require(observationId.isNotBlank())
        resolveHistoricalEquipmentWithinTransaction(observationId)
    }

    suspend fun resolveHistoricalInterpretationSnapshot(
        observationId: String,
        asOf: Instant,
    ): HistoricalEquipmentInterpretationSnapshot = transactions.run {
        require(observationId.isNotBlank())
        val binding = resolveHistoricalEquipmentWithinTransaction(observationId)
        val semantics = dao.setObservationLoadSemantics(observationId)?.toDomain()
        val facts = binding?.let { resolved ->
            dao.equipmentFactVersions(resolved.equipmentId.value)
                .filter { it.activeAt(asOf) }
                .map { it.toDomain() }
        }.orEmpty()

        HistoricalEquipmentInterpretationSnapshot(
            observationId = observationId,
            asOf = asOf,
            equipmentBinding = binding,
            loadSemantics = semantics,
            timeValidFacts = facts,
        )
    }

    private suspend fun resolveHistoricalEquipmentWithinTransaction(observationId: String): ResolvedEquipmentBinding? {
        dao.setObservationEquipmentOverride(observationId)?.let { override ->
            return ResolvedEquipmentBinding(
                equipmentId = EquipmentId(override.equipmentId),
                resolutionSource = EquipmentBindingResolutionSource.OBSERVATION_OVERRIDE,
                source = override.source,
                boundAt = override.boundAt,
            )
        }

        val sessionExerciseId = dao.sessionExerciseIdForObservation(observationId) ?: return null
        return dao.sessionExerciseEquipmentBinding(sessionExerciseId)?.let { sessionBinding ->
            ResolvedEquipmentBinding(
                equipmentId = EquipmentId(sessionBinding.equipmentId),
                resolutionSource = EquipmentBindingResolutionSource.SESSION_EXERCISE,
                source = sessionBinding.source,
                boundAt = sessionBinding.boundAt,
            )
        }
    }

    private suspend fun requireEquipmentExists(equipmentId: EquipmentId) {
        if (dao.equipmentInstances(listOf(equipmentId.value)).singleOrNull() == null) {
            throw EquipmentContextException("Unknown equipment ${equipmentId.value}.")
        }
    }
}

class EquipmentContextException(message: String) : IllegalStateException(message)

private fun requireNotBefore(candidate: String, predecessor: String, label: String) {
    require(!Instant.parse(candidate).isBefore(Instant.parse(predecessor))) {
        "$label cannot become effective before its predecessor."
    }
}

private fun EquipmentFactVersion.toEntity(): EquipmentFactVersionEntity {
    val text = value as? EquipmentFactValue.Text
    val scalar = value as? EquipmentFactValue.Scalar
    return EquipmentFactVersionEntity(
        id = id,
        equipmentId = equipmentId.value,
        factType = factType.storageValue,
        version = version,
        valueKind = factType.valueKind.storageValue,
        textValue = text?.value,
        numericValue = scalar?.value,
        unit = scalar?.unit?.storageValue,
        scope = scope,
        provenanceType = provenance.storageValue,
        provenanceReference = provenanceReference,
        quality = quality,
        createdAt = createdAt,
        effectiveAt = effectiveAt,
        supersededAt = supersededAt,
    )
}

private fun EquipmentFactVersionEntity.toDomain(): EquipmentFactVersion {
    val type = EquipmentFactType.fromStorage(factType)
    if (valueKind != type.valueKind.storageValue) {
        throw EquipmentContextException(
            "Stored ${type.storageValue} fact $id has value kind $valueKind instead of ${type.valueKind.storageValue}.",
        )
    }
    val value = when (type.valueKind) {
        EquipmentFactValueKind.TEXT -> {
            if (textValue == null || numericValue != null || unit != null) {
                throw EquipmentContextException("Stored text equipment fact $id has inconsistent value columns.")
            }
            EquipmentFactValue.Text(textValue)
        }

        EquipmentFactValueKind.SCALAR -> {
            if (numericValue == null || unit == null || textValue != null) {
                throw EquipmentContextException("Stored scalar equipment fact $id has inconsistent value columns.")
            }
            val parsedUnit = EquipmentFactUnit.entries.firstOrNull { it.storageValue == unit }
                ?: throw EquipmentContextException("Stored equipment fact $id has unsupported unit $unit.")
            EquipmentFactValue.Scalar(numericValue, parsedUnit)
        }
    }
    return EquipmentFactVersion(
        id = id,
        equipmentId = EquipmentId(equipmentId),
        factType = type,
        version = version,
        value = value,
        scope = scope,
        provenance = EquipmentFactProvenance.fromStorage(provenanceType),
        provenanceReference = provenanceReference,
        quality = quality,
        createdAt = createdAt,
        effectiveAt = effectiveAt,
        supersededAt = supersededAt,
    )
}

private fun EquipmentFactVersionEntity.activeAt(asOf: Instant): Boolean {
    val effective = Instant.parse(effectiveAt)
    if (effective.isAfter(asOf)) return false
    val superseded = supersededAt?.let(Instant::parse)
    return superseded == null || asOf.isBefore(superseded)
}

private fun PreferredEquipmentBinding.toEntity() = PreferredEquipmentBindingEntity(
    id = id,
    executionProfileId = executionProfileId,
    equipmentId = equipmentId.value,
    effectiveAt = effectiveAt,
    supersededAt = supersededAt,
    source = source,
    createdAt = createdAt,
)

private fun PreferredEquipmentBindingEntity.toDomain() = PreferredEquipmentBinding(
    id = id,
    executionProfileId = executionProfileId,
    equipmentId = EquipmentId(equipmentId),
    effectiveAt = effectiveAt,
    supersededAt = supersededAt,
    source = source,
    createdAt = createdAt,
)

private fun SessionExerciseEquipmentBinding.toEntity() = SessionExerciseEquipmentBindingEntity(
    sessionExerciseId = sessionExerciseId,
    equipmentId = equipmentId.value,
    source = source,
    boundAt = boundAt,
)

private fun ObservationEquipmentOverride.toEntity() = SetObservationEquipmentOverrideEntity(
    observationId = observationId,
    equipmentId = equipmentId.value,
    source = source,
    boundAt = boundAt,
)

private fun ObservationLoadSemantics.toEntity() = SetObservationLoadSemanticsEntity(
    observationId = observationId,
    externalLoadAccounting = externalLoadAccounting.storageValue,
    source = source,
    recordedAt = recordedAt,
)

private fun SetObservationLoadSemanticsEntity.toDomain() = ObservationLoadSemantics(
    observationId = observationId,
    externalLoadAccounting = ExternalLoadAccounting.fromStorage(externalLoadAccounting),
    source = source,
    recordedAt = recordedAt,
)
