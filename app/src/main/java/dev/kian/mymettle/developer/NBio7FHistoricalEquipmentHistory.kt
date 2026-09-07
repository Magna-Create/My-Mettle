package dev.kian.mymettle.developer

import android.database.Cursor
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.equipment.EquipmentBindingResolutionSource
import dev.kian.mymettle.domain.equipment.EquipmentCanonicalDependencyId
import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.equipment.LocalEquipmentInterpreter
import java.time.Instant

/**
 * Immutable installed-history snapshot used only by the N-BIO-7F developer evaluator.
 *
 * This deliberately retains assertion/correction/fact knowledge times instead of calling the normal
 * current-state [dev.kian.mymettle.equipment.EquipmentContextRepository]. Historical evaluation must
 * reconstruct what was knowable at each pre-outcome freeze, not apply corrections authored later.
 */
internal data class NBio7FHistoricalEquipmentHistory(
    val sessionExerciseByObservation: Map<String, String>,
    val equipmentCreatedAt: Map<String, Instant>,
    val sessionBindings: Map<String, NBio7FEquipmentBindingAssertion>,
    val observationOverrides: Map<String, NBio7FEquipmentBindingAssertion>,
    val loadSemantics: Map<String, NBio7FLoadSemanticsAssertion>,
    val sessionBindingCorrections: Map<String, List<NBio7FEquipmentBindingCorrectionRecord>>,
    val observationOverrideCorrections: Map<String, List<NBio7FEquipmentBindingCorrectionRecord>>,
    val loadSemanticsCorrections: Map<String, List<NBio7FLoadSemanticsCorrectionRecord>>,
    val factsByEquipment: Map<String, List<NBio7FEquipmentFactRecord>>,
)

internal data class NBio7FEquipmentBindingAssertion(
    val equipmentId: String,
    val recordedAt: Instant,
)

internal data class NBio7FLoadSemanticsAssertion(
    val accounting: ExternalLoadAccounting,
    val recordedAt: Instant,
)

internal data class NBio7FEquipmentBindingCorrectionRecord(
    val version: Int,
    val previousEquipmentId: String?,
    val correctedEquipmentId: String?,
    val correctedAt: Instant,
)

internal data class NBio7FLoadSemanticsCorrectionRecord(
    val version: Int,
    val previousAccounting: ExternalLoadAccounting?,
    val correctedAccounting: ExternalLoadAccounting?,
    val correctedAt: Instant,
)

internal data class NBio7FEquipmentFactRecord(
    val id: String,
    val equipmentId: String,
    val factType: String,
    val version: Int,
    val createdAt: Instant,
    val effectiveAt: Instant,
)

enum class NBio7FHistoricalEquipmentUnavailabilityReason {
    MISSING_OBSERVATION_OCCURRENCE,
    EQUIPMENT_ASSERTION_NOT_KNOWN_AT_FREEZE,
    EQUIPMENT_CORRECTION_CHAIN_INVALID,
    EQUIPMENT_INSTANCE_NOT_KNOWN_AT_FREEZE,
    LOAD_SEMANTICS_NOT_KNOWN_AT_FREEZE,
    LOAD_SEMANTICS_CORRECTION_CHAIN_INVALID,
}

internal data class NBio7FCausalEquipmentObservation(
    val observationId: String,
    val sessionExerciseId: String?,
    val equipmentId: EquipmentId?,
    val resolutionSource: EquipmentBindingResolutionSource?,
    val loadAccounting: ExternalLoadAccounting?,
    val equipmentFactVersionIds: Set<String>,
    val dependencyIds: Set<String>,
    val futureCorrectionsExcluded: Int,
    val futureFactsExcluded: Int,
    val unavailabilityReasons: Set<NBio7FHistoricalEquipmentUnavailabilityReason>,
) {
    val equipmentResolved: Boolean get() = equipmentId != null
    val loadAccountingResolved: Boolean get() = loadAccounting != null
}

internal sealed interface NBio7FHistoricalCapabilityEquipmentContext {
    data class Stable(
        val equipmentId: EquipmentId,
        val interpretationVersion: String,
        val contributingObservationIds: Set<String>,
        val equipmentFactVersionIds: Set<String>,
        val dependencyIds: Set<String>,
    ) : NBio7FHistoricalCapabilityEquipmentContext

    data class Mixed(
        val equipmentIds: Set<EquipmentId>,
        val contributingObservationIds: Set<String>,
        val dependencyIds: Set<String>,
    ) : NBio7FHistoricalCapabilityEquipmentContext

    data class Unresolved(
        val contributingObservationIds: Set<String>,
        val reasons: Set<NBio7FHistoricalEquipmentUnavailabilityReason>,
        val dependencyIds: Set<String>,
    ) : NBio7FHistoricalCapabilityEquipmentContext
}

internal sealed interface NBio7FHistoricalCapabilityLoadAccounting {
    data class StableKnown(
        val accounting: ExternalLoadAccounting,
        val contributingObservationIds: Set<String>,
        val dependencyIds: Set<String>,
    ) : NBio7FHistoricalCapabilityLoadAccounting

    data class Mixed(
        val accountingValues: Set<ExternalLoadAccounting>,
        val contributingObservationIds: Set<String>,
        val dependencyIds: Set<String>,
    ) : NBio7FHistoricalCapabilityLoadAccounting

    data class Unknown(
        val contributingObservationIds: Set<String>,
        val reasons: Set<NBio7FHistoricalEquipmentUnavailabilityReason>,
        val dependencyIds: Set<String>,
    ) : NBio7FHistoricalCapabilityLoadAccounting
}

internal data class NBio7FHistoricalCapabilityContext(
    val equipment: NBio7FHistoricalCapabilityEquipmentContext,
    val loadAccounting: NBio7FHistoricalCapabilityLoadAccounting,
    val futureCorrectionsExcluded: Int,
    val futureFactsExcluded: Int,
)

/** Pure causal resolver. No current preference or current correction head participates. */
internal object NBio7FCausalEquipmentHistory {
    fun resolveObservation(
        history: NBio7FHistoricalEquipmentHistory,
        observationId: String,
        observationAt: Instant,
        knowledgeAt: Instant,
    ): NBio7FCausalEquipmentObservation {
        require(observationId.isNotBlank())
        require(!observationAt.isAfter(knowledgeAt)) {
            "Historical equipment knowledge cutoff cannot precede the observation being interpreted."
        }
        val sessionExerciseId = history.sessionExerciseByObservation[observationId]
        if (sessionExerciseId == null) {
            return NBio7FCausalEquipmentObservation(
                observationId = observationId,
                sessionExerciseId = null,
                equipmentId = null,
                resolutionSource = null,
                loadAccounting = null,
                equipmentFactVersionIds = emptySet(),
                dependencyIds = setOf(
                    EquipmentCanonicalDependencyId.observationEquipmentOverride(observationId),
                    EquipmentCanonicalDependencyId.observationLoadSemantics(observationId),
                ),
                futureCorrectionsExcluded = 0,
                futureFactsExcluded = 0,
                unavailabilityReasons = setOf(NBio7FHistoricalEquipmentUnavailabilityReason.MISSING_OBSERVATION_OCCURRENCE),
            )
        }

        val dependencies = linkedSetOf(
            EquipmentCanonicalDependencyId.sessionActualEquipment(sessionExerciseId),
            EquipmentCanonicalDependencyId.observationEquipmentOverride(observationId),
            EquipmentCanonicalDependencyId.observationLoadSemantics(observationId),
        )
        val reasons = linkedSetOf<NBio7FHistoricalEquipmentUnavailabilityReason>()

        val sessionResolution = resolveEquipmentValue(
            assertion = history.sessionBindings[sessionExerciseId],
            corrections = history.sessionBindingCorrections[sessionExerciseId].orEmpty(),
            knowledgeAt = knowledgeAt,
        )
        val overrideResolution = resolveEquipmentValue(
            assertion = history.observationOverrides[observationId],
            corrections = history.observationOverrideCorrections[observationId].orEmpty(),
            knowledgeAt = knowledgeAt,
        )
        if (!sessionResolution.valid || !overrideResolution.valid) {
            reasons += NBio7FHistoricalEquipmentUnavailabilityReason.EQUIPMENT_CORRECTION_CHAIN_INVALID
        }

        val resolvedEquipmentValue = if (overrideResolution.valid && overrideResolution.assertionKnown) {
            overrideResolution.value?.let { it to EquipmentBindingResolutionSource.OBSERVATION_OVERRIDE }
                ?: if (sessionResolution.valid) {
                    sessionResolution.value?.let { it to EquipmentBindingResolutionSource.SESSION_EXERCISE }
                } else null
        } else if (sessionResolution.valid) {
            sessionResolution.value?.let { it to EquipmentBindingResolutionSource.SESSION_EXERCISE }
        } else null

        if (resolvedEquipmentValue == null && reasons.isEmpty()) {
            reasons += NBio7FHistoricalEquipmentUnavailabilityReason.EQUIPMENT_ASSERTION_NOT_KNOWN_AT_FREEZE
        }
        val equipmentId = resolvedEquipmentValue?.first?.let(::EquipmentId)?.takeIf { id ->
            val createdAt = history.equipmentCreatedAt[id.value]
            if (createdAt == null || createdAt.isAfter(knowledgeAt)) {
                reasons += NBio7FHistoricalEquipmentUnavailabilityReason.EQUIPMENT_INSTANCE_NOT_KNOWN_AT_FREEZE
                false
            } else true
        }
        val resolutionSource = if (equipmentId == null) null else resolvedEquipmentValue?.second

        val loadResolution = resolveLoadValue(
            assertion = history.loadSemantics[observationId],
            corrections = history.loadSemanticsCorrections[observationId].orEmpty(),
            knowledgeAt = knowledgeAt,
        )
        if (!loadResolution.valid) {
            reasons += NBio7FHistoricalEquipmentUnavailabilityReason.LOAD_SEMANTICS_CORRECTION_CHAIN_INVALID
        } else if (!loadResolution.assertionKnown || loadResolution.value == null) {
            reasons += NBio7FHistoricalEquipmentUnavailabilityReason.LOAD_SEMANTICS_NOT_KNOWN_AT_FREEZE
        }
        val loadAccounting = loadResolution.value.takeIf { loadResolution.valid }

        val facts = equipmentId?.let { id ->
            knownFactsAt(
                history.factsByEquipment[id.value].orEmpty(),
                observationAt = observationAt,
                knowledgeAt = knowledgeAt,
            )
        } ?: KnownFacts(emptySet(), 0)

        return NBio7FCausalEquipmentObservation(
            observationId = observationId,
            sessionExerciseId = sessionExerciseId,
            equipmentId = equipmentId,
            resolutionSource = resolutionSource,
            loadAccounting = loadAccounting,
            equipmentFactVersionIds = facts.factVersionIds,
            dependencyIds = dependencies,
            futureCorrectionsExcluded = sessionResolution.futureExcluded +
                overrideResolution.futureExcluded + loadResolution.futureExcluded,
            futureFactsExcluded = facts.futureExcluded,
            unavailabilityReasons = reasons,
        )
    }

    fun summariseCapability(
        observations: Collection<NBio7FCausalEquipmentObservation>,
    ): NBio7FHistoricalCapabilityContext {
        require(observations.isNotEmpty())
        require(observations.map { it.observationId }.distinct().size == observations.size)
        val observationIds = observations.map { it.observationId }.toSet()
        val dependencies = observations.flatMapTo(linkedSetOf()) { it.dependencyIds }
        val unresolvedEquipment = observations.filterNot { it.equipmentResolved }
        val equipmentIds = observations.mapNotNull { it.equipmentId }.toSet()
        val equipmentContext = when {
            unresolvedEquipment.isNotEmpty() -> NBio7FHistoricalCapabilityEquipmentContext.Unresolved(
                contributingObservationIds = observationIds,
                reasons = unresolvedEquipment.flatMapTo(linkedSetOf()) { it.unavailabilityReasons },
                dependencyIds = dependencies,
            )
            equipmentIds.size == 1 -> NBio7FHistoricalCapabilityEquipmentContext.Stable(
                equipmentId = equipmentIds.single(),
                interpretationVersion = LocalEquipmentInterpreter.INTERPRETATION_VERSION,
                contributingObservationIds = observationIds,
                equipmentFactVersionIds = observations.flatMapTo(linkedSetOf()) { it.equipmentFactVersionIds },
                dependencyIds = dependencies,
            )
            else -> NBio7FHistoricalCapabilityEquipmentContext.Mixed(
                equipmentIds = equipmentIds,
                contributingObservationIds = observationIds,
                dependencyIds = dependencies,
            )
        }

        val unresolvedLoad = observations.filterNot { it.loadAccountingResolved }
        val loadValues = observations.mapNotNull { it.loadAccounting }.toSet()
        val loadContext = when {
            unresolvedLoad.isNotEmpty() -> NBio7FHistoricalCapabilityLoadAccounting.Unknown(
                contributingObservationIds = observationIds,
                reasons = unresolvedLoad.flatMapTo(linkedSetOf()) { it.unavailabilityReasons },
                dependencyIds = dependencies,
            )
            loadValues.size == 1 -> NBio7FHistoricalCapabilityLoadAccounting.StableKnown(
                accounting = loadValues.single(),
                contributingObservationIds = observationIds,
                dependencyIds = dependencies,
            )
            else -> NBio7FHistoricalCapabilityLoadAccounting.Mixed(
                accountingValues = loadValues,
                contributingObservationIds = observationIds,
                dependencyIds = dependencies,
            )
        }
        return NBio7FHistoricalCapabilityContext(
            equipment = equipmentContext,
            loadAccounting = loadContext,
            futureCorrectionsExcluded = observations.sumOf { it.futureCorrectionsExcluded },
            futureFactsExcluded = observations.sumOf { it.futureFactsExcluded },
        )
    }

    private data class EquipmentResolution(
        val value: String?,
        val assertionKnown: Boolean,
        val valid: Boolean,
        val futureExcluded: Int,
    )

    private fun resolveEquipmentValue(
        assertion: NBio7FEquipmentBindingAssertion?,
        corrections: List<NBio7FEquipmentBindingCorrectionRecord>,
        knowledgeAt: Instant,
    ): EquipmentResolution {
        val knownAssertion = assertion?.takeIf { !it.recordedAt.isAfter(knowledgeAt) }
        var value = knownAssertion?.equipmentId
        var valid = true
        corrections.sortedBy { it.version }.filter { !it.correctedAt.isAfter(knowledgeAt) }.forEach { correction ->
            if (correction.previousEquipmentId != value) valid = false
            if (valid) value = correction.correctedEquipmentId
        }
        return EquipmentResolution(
            value = value,
            assertionKnown = knownAssertion != null || corrections.any { !it.correctedAt.isAfter(knowledgeAt) },
            valid = valid,
            futureExcluded = corrections.count { it.correctedAt.isAfter(knowledgeAt) },
        )
    }

    private data class LoadResolution(
        val value: ExternalLoadAccounting?,
        val assertionKnown: Boolean,
        val valid: Boolean,
        val futureExcluded: Int,
    )

    private fun resolveLoadValue(
        assertion: NBio7FLoadSemanticsAssertion?,
        corrections: List<NBio7FLoadSemanticsCorrectionRecord>,
        knowledgeAt: Instant,
    ): LoadResolution {
        val knownAssertion = assertion?.takeIf { !it.recordedAt.isAfter(knowledgeAt) }
        var value = knownAssertion?.accounting
        var valid = true
        corrections.sortedBy { it.version }.filter { !it.correctedAt.isAfter(knowledgeAt) }.forEach { correction ->
            if (correction.previousAccounting != value) valid = false
            if (valid) value = correction.correctedAccounting
        }
        return LoadResolution(
            value = value,
            assertionKnown = knownAssertion != null || corrections.any { !it.correctedAt.isAfter(knowledgeAt) },
            valid = valid,
            futureExcluded = corrections.count { it.correctedAt.isAfter(knowledgeAt) },
        )
    }

    private data class KnownFacts(
        val factVersionIds: Set<String>,
        val futureExcluded: Int,
    )

    private fun knownFactsAt(
        facts: List<NBio7FEquipmentFactRecord>,
        observationAt: Instant,
        knowledgeAt: Instant,
    ): KnownFacts {
        val applicableKnown = facts.filter { fact ->
            !fact.createdAt.isAfter(knowledgeAt) && !fact.effectiveAt.isAfter(observationAt)
        }
        val active = applicableKnown.groupBy { it.factType }.values.map { versions ->
            versions.maxWith(compareBy<NBio7FEquipmentFactRecord> { it.version }.thenBy { it.id })
        }
        val futureExcluded = facts.count { fact ->
            fact.createdAt.isAfter(knowledgeAt) && !fact.effectiveAt.isAfter(observationAt)
        }
        return KnownFacts(
            factVersionIds = active.mapTo(sortedSetOf()) { it.id },
            futureExcluded = futureExcluded,
        )
    }
}

/** Reads the complete canonical equipment audit substrate once; causal slicing remains pure above. */
internal class NBio7FHistoricalEquipmentHistoryReader(private val database: MyMettleDatabase) {
    fun read(): NBio7FHistoricalEquipmentHistory {
        val sqlite = database.openHelper.readableDatabase
        val sessionExerciseByObservation = linkedMapOf<String, String>()
        sqlite.query(
            "SELECT so.id AS observationId, sr.sessionExerciseId " +
                "FROM set_observation AS so INNER JOIN set_record AS sr ON sr.id = so.setRecordId " +
                "ORDER BY so.id",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                sessionExerciseByObservation[cursor.string7f("observationId")] = cursor.string7f("sessionExerciseId")
            }
        }

        val equipmentCreatedAt = linkedMapOf<String, Instant>()
        sqlite.query("SELECT id, createdAt FROM equipment_instance ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                equipmentCreatedAt[cursor.string7f("id")] = Instant.parse(cursor.string7f("createdAt"))
            }
        }

        val sessionBindings = linkedMapOf<String, NBio7FEquipmentBindingAssertion>()
        sqlite.query(
            "SELECT sessionExerciseId, equipmentId, boundAt FROM session_exercise_equipment_binding ORDER BY sessionExerciseId",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                sessionBindings[cursor.string7f("sessionExerciseId")] = NBio7FEquipmentBindingAssertion(
                    equipmentId = cursor.string7f("equipmentId"),
                    recordedAt = Instant.parse(cursor.string7f("boundAt")),
                )
            }
        }

        val observationOverrides = linkedMapOf<String, NBio7FEquipmentBindingAssertion>()
        sqlite.query(
            "SELECT observationId, equipmentId, boundAt FROM set_observation_equipment_override ORDER BY observationId",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                observationOverrides[cursor.string7f("observationId")] = NBio7FEquipmentBindingAssertion(
                    equipmentId = cursor.string7f("equipmentId"),
                    recordedAt = Instant.parse(cursor.string7f("boundAt")),
                )
            }
        }

        val loadSemantics = linkedMapOf<String, NBio7FLoadSemanticsAssertion>()
        sqlite.query(
            "SELECT observationId, externalLoadAccounting, recordedAt FROM set_observation_load_semantics ORDER BY observationId",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                loadSemantics[cursor.string7f("observationId")] = NBio7FLoadSemanticsAssertion(
                    accounting = ExternalLoadAccounting.fromStorage(cursor.string7f("externalLoadAccounting")),
                    recordedAt = Instant.parse(cursor.string7f("recordedAt")),
                )
            }
        }

        val sessionCorrections = linkedMapOf<String, MutableList<NBio7FEquipmentBindingCorrectionRecord>>()
        sqlite.query(
            "SELECT sessionExerciseId, version, previousEquipmentId, correctedEquipmentId, correctedAt " +
                "FROM session_exercise_equipment_binding_correction ORDER BY sessionExerciseId, version",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.string7f("sessionExerciseId")
                sessionCorrections.getOrPut(key, ::mutableListOf) += cursor.equipmentCorrection7f()
            }
        }

        val overrideCorrections = linkedMapOf<String, MutableList<NBio7FEquipmentBindingCorrectionRecord>>()
        sqlite.query(
            "SELECT observationId, version, previousEquipmentId, correctedEquipmentId, correctedAt " +
                "FROM set_observation_equipment_override_correction ORDER BY observationId, version",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.string7f("observationId")
                overrideCorrections.getOrPut(key, ::mutableListOf) += cursor.equipmentCorrection7f()
            }
        }

        val loadCorrections = linkedMapOf<String, MutableList<NBio7FLoadSemanticsCorrectionRecord>>()
        sqlite.query(
            "SELECT observationId, version, previousExternalLoadAccounting, correctedExternalLoadAccounting, correctedAt " +
                "FROM set_observation_load_semantics_correction ORDER BY observationId, version",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.string7f("observationId")
                loadCorrections.getOrPut(key, ::mutableListOf) += NBio7FLoadSemanticsCorrectionRecord(
                    version = cursor.int7f("version"),
                    previousAccounting = cursor.nullableString7f("previousExternalLoadAccounting")
                        ?.let(ExternalLoadAccounting::fromStorage),
                    correctedAccounting = cursor.nullableString7f("correctedExternalLoadAccounting")
                        ?.let(ExternalLoadAccounting::fromStorage),
                    correctedAt = Instant.parse(cursor.string7f("correctedAt")),
                )
            }
        }

        val factsByEquipment = linkedMapOf<String, MutableList<NBio7FEquipmentFactRecord>>()
        sqlite.query(
            "SELECT id, equipmentId, factType, version, createdAt, effectiveAt " +
                "FROM equipment_fact_version ORDER BY equipmentId, factType, version",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val equipmentId = cursor.string7f("equipmentId")
                factsByEquipment.getOrPut(equipmentId, ::mutableListOf) += NBio7FEquipmentFactRecord(
                    id = cursor.string7f("id"),
                    equipmentId = equipmentId,
                    factType = cursor.string7f("factType"),
                    version = cursor.int7f("version"),
                    createdAt = Instant.parse(cursor.string7f("createdAt")),
                    effectiveAt = Instant.parse(cursor.string7f("effectiveAt")),
                )
            }
        }

        return NBio7FHistoricalEquipmentHistory(
            sessionExerciseByObservation = sessionExerciseByObservation.toMap(),
            equipmentCreatedAt = equipmentCreatedAt.toMap(),
            sessionBindings = sessionBindings.toMap(),
            observationOverrides = observationOverrides.toMap(),
            loadSemantics = loadSemantics.toMap(),
            sessionBindingCorrections = sessionCorrections.mapValues { it.value.toList() },
            observationOverrideCorrections = overrideCorrections.mapValues { it.value.toList() },
            loadSemanticsCorrections = loadCorrections.mapValues { it.value.toList() },
            factsByEquipment = factsByEquipment.mapValues { it.value.toList() },
        )
    }
}

private fun Cursor.equipmentCorrection7f() = NBio7FEquipmentBindingCorrectionRecord(
    version = int7f("version"),
    previousEquipmentId = nullableString7f("previousEquipmentId"),
    correctedEquipmentId = nullableString7f("correctedEquipmentId"),
    correctedAt = Instant.parse(string7f("correctedAt")),
)

private fun Cursor.index7f(name: String): Int = getColumnIndexOrThrow(name)
private fun Cursor.string7f(name: String): String = getString(index7f(name))
private fun Cursor.nullableString7f(name: String): String? = if (isNull(index7f(name))) null else getString(index7f(name))
private fun Cursor.int7f(name: String): Int = getInt(index7f(name))
