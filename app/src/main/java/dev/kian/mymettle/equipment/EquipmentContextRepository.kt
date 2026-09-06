package dev.kian.mymettle.equipment

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.dao.EquipmentCorrectionDao
import dev.kian.mymettle.data.local.dao.EquipmentDao
import dev.kian.mymettle.data.local.entity.EquipmentFactVersionEntity
import dev.kian.mymettle.data.local.entity.EquipmentInstanceEntity
import dev.kian.mymettle.data.local.entity.PreferredEquipmentBindingEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEquipmentBindingCorrectionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEquipmentBindingEntity
import dev.kian.mymettle.data.local.entity.SetObservationEquipmentOverrideCorrectionEntity
import dev.kian.mymettle.data.local.entity.SetObservationEquipmentOverrideEntity
import dev.kian.mymettle.data.local.entity.SetObservationLoadSemanticsCorrectionEntity
import dev.kian.mymettle.data.local.entity.SetObservationLoadSemanticsEntity
import dev.kian.mymettle.domain.equipment.EquipmentBindingResolutionSource
import dev.kian.mymettle.domain.equipment.EquipmentCanonicalDependencyId
import dev.kian.mymettle.domain.equipment.EquipmentFactProvenance
import dev.kian.mymettle.domain.equipment.EquipmentFactType
import dev.kian.mymettle.domain.equipment.EquipmentFactUnit
import dev.kian.mymettle.domain.equipment.EquipmentFactValue
import dev.kian.mymettle.domain.equipment.EquipmentFactValueKind
import dev.kian.mymettle.domain.equipment.EquipmentFactVersion
import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.EquipmentInstance
import dev.kian.mymettle.domain.equipment.EquipmentInvalidationImpact
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.equipment.ObservationEquipmentOverride
import dev.kian.mymettle.domain.equipment.ObservationEquipmentOverrideCorrection
import dev.kian.mymettle.domain.equipment.ObservationLoadSemantics
import dev.kian.mymettle.domain.equipment.ObservationLoadSemanticsCorrection
import dev.kian.mymettle.domain.equipment.PreferredEquipmentBinding
import dev.kian.mymettle.domain.equipment.ResolvedEquipmentBinding
import dev.kian.mymettle.domain.equipment.SessionExerciseEquipmentBinding
import dev.kian.mymettle.domain.equipment.SessionExerciseEquipmentBindingCorrection
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
 * observation resolution never consults today's preferred equipment. Room16 base assertions remain
 * immutable; Room17 corrections are append-only epistemic overlays that can also retract a wrong
 * assertion back to unknown.
 */
class EquipmentContextRepository(
    private val dao: EquipmentDao,
    private val transactions: EquipmentTransactionRunner,
    private val correctionDao: EquipmentCorrectionDao = EmptyEquipmentCorrectionDao,
) {
    constructor(database: MyMettleDatabase) : this(
        dao = database.equipmentDao(),
        transactions = RoomEquipmentTransactionRunner(database),
        correctionDao = database.equipmentCorrectionDao(),
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

    suspend fun correctSessionActualEquipment(
        correction: SessionExerciseEquipmentBindingCorrection,
    ): EquipmentInvalidationImpact = transactions.run {
        correction.correctedEquipmentId?.let { requireEquipmentExists(it) }
        val base = dao.sessionExerciseEquipmentBinding(correction.sessionExerciseId)
            ?: throw EquipmentContextException(
                "Session exercise ${correction.sessionExerciseId} has no base actual-equipment assertion to correct.",
            )
        val rows = correctionDao.sessionExerciseEquipmentBindingCorrections(correction.sessionExerciseId)
        val entity = correction.toEntity()
        rows.firstOrNull { it.id == correction.id }?.let { existing ->
            if (existing != entity) throw EquipmentContextException("Correction ${correction.id} already exists with different data.")
            return@run sessionBindingImpact(correction.sessionExerciseId)
        }
        val current = effectiveEquipmentValue(
            baseEquipmentId = base.equipmentId,
            baseSource = base.source,
            baseChangedAt = base.boundAt,
            rows = rows.map { it.toStep() },
            label = "Session exercise ${correction.sessionExerciseId} actual equipment",
        )
        require(correction.version == rows.size + 1) { "Session actual-equipment corrections must be contiguous." }
        require(correction.previousEquipmentId?.value == current.equipmentId) {
            "Session actual-equipment correction previous value is stale."
        }
        require(correction.correctedEquipmentId?.value != current.equipmentId) { "Correction must change the canonical value." }
        requireNotBefore(correction.correctedAt, current.changedAt, "Session actual-equipment correction")
        correctionDao.insertSessionExerciseEquipmentBindingCorrections(listOf(entity))
        sessionBindingImpact(correction.sessionExerciseId)
    }

    suspend fun correctObservationEquipmentOverride(
        correction: ObservationEquipmentOverrideCorrection,
    ): EquipmentInvalidationImpact = transactions.run {
        correction.correctedEquipmentId?.let { requireEquipmentExists(it) }
        val base = dao.setObservationEquipmentOverride(correction.observationId)
            ?: throw EquipmentContextException(
                "Observation ${correction.observationId} has no base equipment override to correct; record the initial override instead.",
            )
        val rows = correctionDao.setObservationEquipmentOverrideCorrections(correction.observationId)
        val entity = correction.toEntity()
        rows.firstOrNull { it.id == correction.id }?.let { existing ->
            if (existing != entity) throw EquipmentContextException("Correction ${correction.id} already exists with different data.")
            return@run observationOverrideImpact(correction.observationId)
        }
        val current = effectiveEquipmentValue(
            baseEquipmentId = base.equipmentId,
            baseSource = base.source,
            baseChangedAt = base.boundAt,
            rows = rows.map { it.toStep() },
            label = "Observation ${correction.observationId} equipment override",
        )
        require(correction.version == rows.size + 1) { "Observation equipment-override corrections must be contiguous." }
        require(correction.previousEquipmentId?.value == current.equipmentId) {
            "Observation equipment-override correction previous value is stale."
        }
        require(correction.correctedEquipmentId?.value != current.equipmentId) { "Correction must change the canonical value." }
        requireNotBefore(correction.correctedAt, current.changedAt, "Observation equipment-override correction")
        correctionDao.insertSetObservationEquipmentOverrideCorrections(listOf(entity))
        observationOverrideImpact(correction.observationId)
    }

    suspend fun correctObservationLoadSemantics(
        correction: ObservationLoadSemanticsCorrection,
    ): EquipmentInvalidationImpact = transactions.run {
        val base = dao.setObservationLoadSemantics(correction.observationId)
            ?: throw EquipmentContextException(
                "Observation ${correction.observationId} has no base load semantics to correct; record initial semantics instead.",
            )
        val rows = correctionDao.setObservationLoadSemanticsCorrections(correction.observationId)
        val entity = correction.toEntity()
        rows.firstOrNull { it.id == correction.id }?.let { existing ->
            if (existing != entity) throw EquipmentContextException("Correction ${correction.id} already exists with different data.")
            return@run loadSemanticsImpact(correction.observationId)
        }
        val current = effectiveLoadSemanticsValue(base, rows)
        require(correction.version == rows.size + 1) { "Observation load-semantics corrections must be contiguous." }
        require(correction.previousExternalLoadAccounting == current.accounting) {
            "Observation load-semantics correction previous value is stale."
        }
        require(correction.correctedExternalLoadAccounting != current.accounting) { "Correction must change the canonical value." }
        requireNotBefore(correction.correctedAt, current.changedAt, "Observation load-semantics correction")
        correctionDao.insertSetObservationLoadSemanticsCorrections(listOf(entity))
        loadSemanticsImpact(correction.observationId)
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
        val semantics = resolveLoadSemanticsWithinTransaction(observationId)
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
        val override = dao.setObservationEquipmentOverride(observationId)
        val overrideCorrections = correctionDao.setObservationEquipmentOverrideCorrections(observationId)
        if (override == null && overrideCorrections.isNotEmpty()) {
            throw EquipmentContextException("Observation $observationId has correction rows without a base equipment override.")
        }
        if (override != null) {
            val effective = effectiveEquipmentValue(
                baseEquipmentId = override.equipmentId,
                baseSource = override.source,
                baseChangedAt = override.boundAt,
                rows = overrideCorrections.map { it.toStep() },
                label = "Observation $observationId equipment override",
            )
            effective.equipmentId?.let { equipmentId ->
                return ResolvedEquipmentBinding(
                    equipmentId = EquipmentId(equipmentId),
                    resolutionSource = EquipmentBindingResolutionSource.OBSERVATION_OVERRIDE,
                    source = effective.source,
                    boundAt = override.boundAt,
                )
            }
        }

        val sessionExerciseId = dao.sessionExerciseIdForObservation(observationId) ?: return null
        val sessionBinding = dao.sessionExerciseEquipmentBinding(sessionExerciseId)
        val sessionCorrections = correctionDao.sessionExerciseEquipmentBindingCorrections(sessionExerciseId)
        if (sessionBinding == null) {
            if (sessionCorrections.isNotEmpty()) {
                throw EquipmentContextException(
                    "Session exercise $sessionExerciseId has correction rows without a base actual-equipment assertion.",
                )
            }
            return null
        }
        val effective = effectiveEquipmentValue(
            baseEquipmentId = sessionBinding.equipmentId,
            baseSource = sessionBinding.source,
            baseChangedAt = sessionBinding.boundAt,
            rows = sessionCorrections.map { it.toStep() },
            label = "Session exercise $sessionExerciseId actual equipment",
        )
        return effective.equipmentId?.let { equipmentId ->
            ResolvedEquipmentBinding(
                equipmentId = EquipmentId(equipmentId),
                resolutionSource = EquipmentBindingResolutionSource.SESSION_EXERCISE,
                source = effective.source,
                boundAt = sessionBinding.boundAt,
            )
        }
    }

    private suspend fun resolveLoadSemanticsWithinTransaction(observationId: String): ObservationLoadSemantics? {
        val base = dao.setObservationLoadSemantics(observationId)
        val rows = correctionDao.setObservationLoadSemanticsCorrections(observationId)
        if (base == null) {
            if (rows.isNotEmpty()) {
                throw EquipmentContextException("Observation $observationId has load-semantics corrections without a base assertion.")
            }
            return null
        }
        val effective = effectiveLoadSemanticsValue(base, rows)
        return effective.accounting?.let { accounting ->
            ObservationLoadSemantics(
                observationId = observationId,
                externalLoadAccounting = accounting,
                source = effective.source,
                recordedAt = effective.changedAt,
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

private data class EquipmentCorrectionStep(
    val version: Int,
    val previousEquipmentId: String?,
    val correctedEquipmentId: String?,
    val source: String,
    val correctedAt: String,
)

private data class EffectiveEquipmentValue(
    val equipmentId: String?,
    val source: String,
    val changedAt: String,
)

private data class EffectiveLoadSemanticsValue(
    val accounting: ExternalLoadAccounting?,
    val source: String,
    val changedAt: String,
)

private fun effectiveEquipmentValue(
    baseEquipmentId: String,
    baseSource: String,
    baseChangedAt: String,
    rows: List<EquipmentCorrectionStep>,
    label: String,
): EffectiveEquipmentValue {
    var equipmentId: String? = baseEquipmentId
    var source = baseSource
    var changedAt = baseChangedAt
    rows.forEachIndexed { index, row ->
        val expectedVersion = index + 1
        if (row.version != expectedVersion) {
            throw EquipmentContextException("$label correction chain is not contiguous at version $expectedVersion.")
        }
        if (row.previousEquipmentId != equipmentId) {
            throw EquipmentContextException("$label correction version ${row.version} does not match its previous canonical value.")
        }
        if (row.correctedEquipmentId == equipmentId) {
            throw EquipmentContextException("$label correction version ${row.version} does not change the canonical value.")
        }
        requireNotBefore(row.correctedAt, changedAt, "$label correction chain")
        equipmentId = row.correctedEquipmentId
        source = row.source
        changedAt = row.correctedAt
    }
    return EffectiveEquipmentValue(equipmentId, source, changedAt)
}

private fun effectiveLoadSemanticsValue(
    base: SetObservationLoadSemanticsEntity,
    rows: List<SetObservationLoadSemanticsCorrectionEntity>,
): EffectiveLoadSemanticsValue {
    var accounting: ExternalLoadAccounting? = ExternalLoadAccounting.fromStorage(base.externalLoadAccounting)
    var source = base.source
    var changedAt = base.recordedAt
    rows.forEachIndexed { index, row ->
        val expectedVersion = index + 1
        if (row.version != expectedVersion) {
            throw EquipmentContextException(
                "Observation ${base.observationId} load-semantics correction chain is not contiguous at version $expectedVersion.",
            )
        }
        val previous = row.previousExternalLoadAccounting?.let(ExternalLoadAccounting::fromStorage)
        val corrected = row.correctedExternalLoadAccounting?.let(ExternalLoadAccounting::fromStorage)
        if (previous != accounting) {
            throw EquipmentContextException(
                "Observation ${base.observationId} load-semantics correction version ${row.version} has stale previous meaning.",
            )
        }
        if (corrected == accounting) {
            throw EquipmentContextException(
                "Observation ${base.observationId} load-semantics correction version ${row.version} changes no meaning.",
            )
        }
        requireNotBefore(row.correctedAt, changedAt, "Observation ${base.observationId} load-semantics correction chain")
        accounting = corrected
        source = row.source
        changedAt = row.correctedAt
    }
    return EffectiveLoadSemanticsValue(accounting, source, changedAt)
}

private fun requireNotBefore(candidate: String, predecessor: String, label: String) {
    require(!Instant.parse(candidate).isBefore(Instant.parse(predecessor))) {
        "$label cannot become effective before its predecessor."
    }
}

private fun sessionBindingImpact(sessionExerciseId: String) = EquipmentInvalidationImpact(
    setOf(EquipmentCanonicalDependencyId.sessionActualEquipment(sessionExerciseId)),
)

private fun observationOverrideImpact(observationId: String) = EquipmentInvalidationImpact(
    setOf(EquipmentCanonicalDependencyId.observationEquipmentOverride(observationId)),
)

private fun loadSemanticsImpact(observationId: String) = EquipmentInvalidationImpact(
    setOf(EquipmentCanonicalDependencyId.observationLoadSemantics(observationId)),
)

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

private fun SessionExerciseEquipmentBindingCorrection.toEntity() = SessionExerciseEquipmentBindingCorrectionEntity(
    id = id,
    sessionExerciseId = sessionExerciseId,
    version = version,
    previousEquipmentId = previousEquipmentId?.value,
    correctedEquipmentId = correctedEquipmentId?.value,
    source = source,
    reason = reason,
    correctedAt = correctedAt,
)

private fun ObservationEquipmentOverrideCorrection.toEntity() = SetObservationEquipmentOverrideCorrectionEntity(
    id = id,
    observationId = observationId,
    version = version,
    previousEquipmentId = previousEquipmentId?.value,
    correctedEquipmentId = correctedEquipmentId?.value,
    source = source,
    reason = reason,
    correctedAt = correctedAt,
)

private fun ObservationLoadSemanticsCorrection.toEntity() = SetObservationLoadSemanticsCorrectionEntity(
    id = id,
    observationId = observationId,
    version = version,
    previousExternalLoadAccounting = previousExternalLoadAccounting?.storageValue,
    correctedExternalLoadAccounting = correctedExternalLoadAccounting?.storageValue,
    source = source,
    reason = reason,
    correctedAt = correctedAt,
)

private fun SessionExerciseEquipmentBindingCorrectionEntity.toStep() = EquipmentCorrectionStep(
    version = version,
    previousEquipmentId = previousEquipmentId,
    correctedEquipmentId = correctedEquipmentId,
    source = source,
    correctedAt = correctedAt,
)

private fun SetObservationEquipmentOverrideCorrectionEntity.toStep() = EquipmentCorrectionStep(
    version = version,
    previousEquipmentId = previousEquipmentId,
    correctedEquipmentId = correctedEquipmentId,
    source = source,
    correctedAt = correctedAt,
)

private fun SetObservationLoadSemanticsEntity.toDomain() = ObservationLoadSemantics(
    observationId = observationId,
    externalLoadAccounting = ExternalLoadAccounting.fromStorage(externalLoadAccounting),
    source = source,
    recordedAt = recordedAt,
)

private object EmptyEquipmentCorrectionDao : EquipmentCorrectionDao {
    override suspend fun insertSessionExerciseEquipmentBindingCorrections(
        rows: List<SessionExerciseEquipmentBindingCorrectionEntity>,
    ) = error("Correction writes require a correction-capable repository instance.")

    override suspend fun insertSetObservationEquipmentOverrideCorrections(
        rows: List<SetObservationEquipmentOverrideCorrectionEntity>,
    ) = error("Correction writes require a correction-capable repository instance.")

    override suspend fun insertSetObservationLoadSemanticsCorrections(
        rows: List<SetObservationLoadSemanticsCorrectionEntity>,
    ) = error("Correction writes require a correction-capable repository instance.")

    override suspend fun sessionExerciseEquipmentBindingCorrections(
        sessionExerciseId: String,
    ): List<SessionExerciseEquipmentBindingCorrectionEntity> = emptyList()

    override suspend fun setObservationEquipmentOverrideCorrections(
        observationId: String,
    ): List<SetObservationEquipmentOverrideCorrectionEntity> = emptyList()

    override suspend fun setObservationLoadSemanticsCorrections(
        observationId: String,
    ): List<SetObservationLoadSemanticsCorrectionEntity> = emptyList()
}
