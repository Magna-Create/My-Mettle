package dev.kian.mymettle.equipment

import dev.kian.mymettle.domain.equipment.EquipmentBindingResolutionSource
import dev.kian.mymettle.domain.equipment.EquipmentFactProvenance
import dev.kian.mymettle.domain.equipment.EquipmentFactType
import dev.kian.mymettle.domain.equipment.EquipmentFactUnit
import dev.kian.mymettle.domain.equipment.EquipmentFactValue
import dev.kian.mymettle.domain.equipment.EquipmentFactVersion
import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.equipment.ObservationLoadSemantics
import dev.kian.mymettle.domain.equipment.ResolvedEquipmentBinding
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.PerformanceObservation
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LocalEquipmentInterpreterTest {
    private val asOf = Instant.parse("2026-01-15T12:00:00Z")
    private val equipmentId = EquipmentId("equipment:bar")
    private val binding = ResolvedEquipmentBinding(
        equipmentId = equipmentId,
        resolutionSource = EquipmentBindingResolutionSource.SESSION_EXERCISE,
        source = "recorded-session-choice",
        boundAt = "2026-01-15T11:00:00Z",
    )

    @Test
    fun addedLoadPlusKnownImplementMassResolvesLocallyWithoutRewritingRawEvidence() {
        val observation = observation(60.0)
        val before = observation
        val result = LocalEquipmentInterpreter.interpret(
            observation = observation,
            entryBasis = EntryBasis.TOTAL,
            snapshot = snapshot(
                semantics = semantics(ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY),
                facts = listOf(implementMassFact("fact:bar-20", 20.0)),
            ),
        )

        val resolved = assertIs<LocalEquipmentInterpretation.Resolved>(result)
        assertEquals(Quantity(60.0, UnitId.KILOGRAM), resolved.rawEnteredExternalLoad)
        assertEquals(60.0, resolved.canonicalEnteredExternalLoadKg)
        assertEquals(80.0, resolved.localExternalLoadCoordinateKg)
        assertEquals(listOf("fact:bar-20"), resolved.appliedFactVersions.map { it.id })
        assertEquals(before, observation)
    }

    @Test
    fun unknownLoadAccountingFailsClosedAndStillExposesRawEvidence() {
        val result = LocalEquipmentInterpreter.interpret(
            observation = observation(60.0),
            entryBasis = EntryBasis.TOTAL,
            snapshot = snapshot(semantics = null),
        )

        val unavailable = assertIs<LocalEquipmentInterpretation.Unavailable>(result)
        assertEquals(LocalEquipmentInterpretationUnavailableReason.LOAD_ACCOUNTING_UNKNOWN, unavailable.reason)
        assertEquals(Quantity(60.0, UnitId.KILOGRAM), unavailable.rawEnteredExternalLoad)
        assertEquals(60.0, unavailable.canonicalEnteredExternalLoadKg)
    }

    @Test
    fun addedLoadWithoutImplementMassFailsClosed() {
        val result = LocalEquipmentInterpreter.interpret(
            observation = observation(60.0),
            entryBasis = EntryBasis.TOTAL,
            snapshot = snapshot(semantics = semantics(ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY)),
        )

        val unavailable = assertIs<LocalEquipmentInterpretation.Unavailable>(result)
        assertEquals(LocalEquipmentInterpretationUnavailableReason.IMPLEMENT_MASS_UNKNOWN, unavailable.reason)
    }

    @Test
    fun simultaneousImplementMassFactsFailClosedAsAmbiguous() {
        val result = LocalEquipmentInterpreter.interpret(
            observation = observation(60.0),
            entryBasis = EntryBasis.TOTAL,
            snapshot = snapshot(
                semantics = semantics(ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY),
                facts = listOf(
                    implementMassFact("fact:bar-20", 20.0),
                    implementMassFact("fact:bar-15", 15.0),
                ),
            ),
        )

        val unavailable = assertIs<LocalEquipmentInterpretation.Unavailable>(result)
        assertEquals(LocalEquipmentInterpretationUnavailableReason.AMBIGUOUS_IMPLEMENT_MASS, unavailable.reason)
    }

    @Test
    fun inclusiveLoadPreservesPerHandAndPerSideCoordinatesWithoutTotalising() {
        for (basis in listOf(EntryBasis.PER_HAND, EntryBasis.PER_SIDE)) {
            val result = LocalEquipmentInterpreter.interpret(
                observation = observation(30.0),
                entryBasis = basis,
                snapshot = snapshot(semantics = semantics(ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD)),
            )

            val resolved = assertIs<LocalEquipmentInterpretation.Resolved>(result)
            assertEquals(basis, resolved.entryBasis)
            assertEquals(30.0, resolved.localExternalLoadCoordinateKg)
            assertEquals(emptyList(), resolved.appliedFactVersions)
        }
    }

    @Test
    fun addedOnlyNonTotalBasisFailsClosedInsteadOfInventingAggregation() {
        for (basis in listOf(EntryBasis.PER_HAND, EntryBasis.PER_SIDE)) {
            val result = LocalEquipmentInterpreter.interpret(
                observation = observation(30.0),
                entryBasis = basis,
                snapshot = snapshot(
                    semantics = semantics(ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY),
                    facts = listOf(implementMassFact("fact:bar-20", 20.0)),
                ),
            )

            val unavailable = assertIs<LocalEquipmentInterpretation.Unavailable>(result)
            assertEquals(LocalEquipmentInterpretationUnavailableReason.ADDED_LOAD_BASIS_NOT_EXACT, unavailable.reason)
            assertEquals(basis, unavailable.entryBasis)
        }
    }

    @Test
    fun unrelatedMechanicalFactsDoNotBecomeLoadArithmeticDefaults() {
        val ratioFact = EquipmentFactVersion(
            id = "fact:ratio",
            equipmentId = equipmentId,
            factType = EquipmentFactType.MECHANICAL_RATIO,
            version = 1,
            value = EquipmentFactValue.Scalar(2.0, EquipmentFactUnit.RATIO),
            scope = "documented local cable path",
            provenance = EquipmentFactProvenance.OEM_DECLARED_SPECIFICATION,
            provenanceReference = "fixture",
            quality = null,
            createdAt = "2026-01-01T00:00:00Z",
            effectiveAt = "2026-01-01T00:00:00Z",
            supersededAt = null,
        )
        val result = LocalEquipmentInterpreter.interpret(
            observation = observation(40.0),
            entryBasis = EntryBasis.TOTAL,
            snapshot = snapshot(
                semantics = semantics(ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY),
                facts = listOf(ratioFact),
            ),
        )

        val unavailable = assertIs<LocalEquipmentInterpretation.Unavailable>(result)
        assertEquals(LocalEquipmentInterpretationUnavailableReason.IMPLEMENT_MASS_UNKNOWN, unavailable.reason)
    }

    private fun observation(loadKg: Double) = PerformanceObservation(
        id = "observation:1",
        setRecordId = "set:1",
        executionProfileVersionId = ExecutionProfileVersionId("profile-version:1"),
        ordinal = 0,
        laterality = Laterality.BILATERAL,
        completedAt = asOf,
        source = "test",
        bodyMassContextKg = null,
        values = listOf(
            PerformanceMetricValue(
                metric = PerformanceMetric.EXTERNAL_LOAD,
                entered = Quantity(loadKg, UnitId.KILOGRAM),
            ),
        ),
    )

    private fun snapshot(
        semantics: ObservationLoadSemantics?,
        facts: List<EquipmentFactVersion> = emptyList(),
    ) = HistoricalEquipmentInterpretationSnapshot(
        observationId = "observation:1",
        asOf = asOf,
        equipmentBinding = binding,
        loadSemantics = semantics,
        timeValidFacts = facts,
    )

    private fun semantics(accounting: ExternalLoadAccounting) = ObservationLoadSemantics(
        observationId = "observation:1",
        externalLoadAccounting = accounting,
        source = "explicit-test-semantics",
        recordedAt = "2026-01-15T11:30:00Z",
    )

    private fun implementMassFact(id: String, kg: Double) = EquipmentFactVersion(
        id = id,
        equipmentId = equipmentId,
        factType = EquipmentFactType.IMPLEMENT_MASS,
        version = 1,
        value = EquipmentFactValue.Scalar(kg, EquipmentFactUnit.KILOGRAM),
        scope = "configured implement",
        provenance = EquipmentFactProvenance.USER_CONFIRMED_CONFIGURATION,
        provenanceReference = "test fixture",
        quality = null,
        createdAt = "2026-01-01T00:00:00Z",
        effectiveAt = "2026-01-01T00:00:00Z",
        supersededAt = null,
    )
}
