package dev.kian.mymettle.equipment

import dev.kian.mymettle.domain.equipment.EquipmentFactType
import dev.kian.mymettle.domain.equipment.EquipmentFactUnit
import dev.kian.mymettle.domain.equipment.EquipmentFactValue
import dev.kian.mymettle.domain.equipment.EquipmentFactVersion
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.equipment.ObservationLoadSemantics
import dev.kian.mymettle.domain.equipment.ResolvedEquipmentBinding
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceObservation
import dev.kian.mymettle.domain.performance.Quantity
import java.time.Instant

data class HistoricalEquipmentInterpretationSnapshot(
    val observationId: String,
    val asOf: Instant,
    val equipmentBinding: ResolvedEquipmentBinding?,
    val loadSemantics: ObservationLoadSemantics?,
    val timeValidFacts: List<EquipmentFactVersion>,
) {
    init {
        require(observationId.isNotBlank())
        if (equipmentBinding == null) require(timeValidFacts.isEmpty()) {
            "Equipment facts cannot be attached to an unresolved historical equipment binding."
        }
        require(
            equipmentBinding == null || timeValidFacts.all { it.equipmentId == equipmentBinding.equipmentId },
        ) { "All time-valid facts must belong to the resolved historical equipment instance." }
    }
}

enum class LocalEquipmentInterpretationUnavailableReason {
    EQUIPMENT_UNRESOLVED,
    LOAD_ACCOUNTING_UNKNOWN,
    EXTERNAL_LOAD_NOT_RECORDED,
    IMPLEMENT_MASS_UNKNOWN,
    AMBIGUOUS_IMPLEMENT_MASS,
    ADDED_LOAD_BASIS_NOT_EXACT,
}

sealed interface LocalEquipmentInterpretation {
    val observationId: String
    val interpretationVersion: String

    data class Resolved(
        override val observationId: String,
        override val interpretationVersion: String,
        val asOf: Instant,
        val equipmentBinding: ResolvedEquipmentBinding,
        val loadSemantics: ObservationLoadSemantics,
        val entryBasis: EntryBasis,
        val rawEnteredExternalLoad: Quantity,
        val canonicalEnteredExternalLoadKg: Double,
        /**
         * A local configured-load coordinate in the observation's existing EntryBasis.
         * This is not universal resistance and does not apply pulley, lever, rail or friction assumptions.
         */
        val localExternalLoadCoordinateKg: Double,
        val appliedFactVersions: List<EquipmentFactVersion>,
    ) : LocalEquipmentInterpretation

    data class Unavailable(
        override val observationId: String,
        override val interpretationVersion: String,
        val asOf: Instant,
        val reason: LocalEquipmentInterpretationUnavailableReason,
        val entryBasis: EntryBasis,
        val rawEnteredExternalLoad: Quantity?,
        val canonicalEnteredExternalLoadKg: Double?,
        val equipmentBinding: ResolvedEquipmentBinding?,
        val loadSemantics: ObservationLoadSemantics?,
    ) : LocalEquipmentInterpretation
}

/**
 * First deterministic 7F-B local interpretation kernel.
 *
 * It intentionally performs only exact external-mass bookkeeping. It does not infer or apply
 * pulley ratios, lever arms, Smith counterbalance, rail geometry, friction or cross-profile
 * equivalence. Those remain unknown unless a later version has an explicit device-local contract.
 */
object LocalEquipmentInterpreter {
    const val INTERPRETATION_VERSION = "n-bio-7f-local-external-mass-v1"

    fun interpret(
        observation: PerformanceObservation,
        entryBasis: EntryBasis,
        snapshot: HistoricalEquipmentInterpretationSnapshot,
    ): LocalEquipmentInterpretation {
        require(snapshot.observationId == observation.id) {
            "Historical equipment snapshot must belong to the interpreted observation."
        }

        val externalLoad = observation.values.singleOrNull { it.metric == PerformanceMetric.EXTERNAL_LOAD }

        fun unavailable(reason: LocalEquipmentInterpretationUnavailableReason) =
            LocalEquipmentInterpretation.Unavailable(
                observationId = observation.id,
                interpretationVersion = INTERPRETATION_VERSION,
                asOf = snapshot.asOf,
                reason = reason,
                entryBasis = entryBasis,
                rawEnteredExternalLoad = externalLoad?.entered,
                canonicalEnteredExternalLoadKg = externalLoad?.canonical?.value,
                equipmentBinding = snapshot.equipmentBinding,
                loadSemantics = snapshot.loadSemantics,
            )

        val binding = snapshot.equipmentBinding
            ?: return unavailable(LocalEquipmentInterpretationUnavailableReason.EQUIPMENT_UNRESOLVED)
        val semantics = snapshot.loadSemantics
            ?: return unavailable(LocalEquipmentInterpretationUnavailableReason.LOAD_ACCOUNTING_UNKNOWN)
        val load = externalLoad
            ?: return unavailable(LocalEquipmentInterpretationUnavailableReason.EXTERNAL_LOAD_NOT_RECORDED)

        return when (semantics.externalLoadAccounting) {
            ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD -> LocalEquipmentInterpretation.Resolved(
                observationId = observation.id,
                interpretationVersion = INTERPRETATION_VERSION,
                asOf = snapshot.asOf,
                equipmentBinding = binding,
                loadSemantics = semantics,
                entryBasis = entryBasis,
                rawEnteredExternalLoad = load.entered,
                canonicalEnteredExternalLoadKg = load.canonical.value,
                localExternalLoadCoordinateKg = load.canonical.value,
                appliedFactVersions = emptyList(),
            )

            ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY -> {
                if (entryBasis != EntryBasis.TOTAL) {
                    return unavailable(LocalEquipmentInterpretationUnavailableReason.ADDED_LOAD_BASIS_NOT_EXACT)
                }

                val implementMassFacts = snapshot.timeValidFacts.filter {
                    it.factType == EquipmentFactType.IMPLEMENT_MASS
                }
                if (implementMassFacts.isEmpty()) {
                    return unavailable(LocalEquipmentInterpretationUnavailableReason.IMPLEMENT_MASS_UNKNOWN)
                }
                if (implementMassFacts.size != 1) {
                    return unavailable(LocalEquipmentInterpretationUnavailableReason.AMBIGUOUS_IMPLEMENT_MASS)
                }

                val implementMassFact = implementMassFacts.single()
                val implementMass = implementMassFact.value as EquipmentFactValue.Scalar
                check(implementMass.unit == EquipmentFactUnit.KILOGRAM) {
                    "Validated implement-mass facts must be kilogram-dimensional."
                }

                LocalEquipmentInterpretation.Resolved(
                    observationId = observation.id,
                    interpretationVersion = INTERPRETATION_VERSION,
                    asOf = snapshot.asOf,
                    equipmentBinding = binding,
                    loadSemantics = semantics,
                    entryBasis = entryBasis,
                    rawEnteredExternalLoad = load.entered,
                    canonicalEnteredExternalLoadKg = load.canonical.value,
                    localExternalLoadCoordinateKg = load.canonical.value + implementMass.value,
                    appliedFactVersions = listOf(implementMassFact),
                )
            }
        }
    }
}
