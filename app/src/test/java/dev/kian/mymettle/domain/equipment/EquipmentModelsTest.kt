package dev.kian.mymettle.domain.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EquipmentModelsTest {
    @Test
    fun loadAccountingIsOrthogonalAndHasNoInventedUnknownEnum() {
        assertEquals(
            ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
            ExternalLoadAccounting.fromStorage("inclusive_external_load"),
        )
        assertEquals(
            ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY,
            ExternalLoadAccounting.fromStorage("added_external_load_only"),
        )
        assertFailsWith<IllegalArgumentException> { ExternalLoadAccounting.fromStorage("unknown") }
    }

    @Test
    fun scalarFactTypeEnforcesLocalUnitMeaning() {
        val fact = EquipmentFactVersion(
            id = "fact:bar:mass:v1",
            equipmentId = EquipmentId("equipment:bar"),
            factType = EquipmentFactType.IMPLEMENT_MASS,
            version = 1,
            value = EquipmentFactValue.Scalar(20.0, EquipmentFactUnit.KILOGRAM),
            scope = "whole_implement",
            provenance = EquipmentFactProvenance.USER_CONFIRMED_CONFIGURATION,
            provenanceReference = null,
            quality = null,
            createdAt = "2026-09-06T14:00:00Z",
            effectiveAt = "2026-09-06T14:00:00Z",
            supersededAt = null,
        )
        assertEquals(20.0, (fact.value as EquipmentFactValue.Scalar).value)

        assertFailsWith<IllegalArgumentException> {
            fact.copy(value = EquipmentFactValue.Scalar(20.0, EquipmentFactUnit.RATIO))
        }
    }

    @Test
    fun deviceKindDoesNotImplyMechanicalFacts() {
        assertEquals(EquipmentKind.CABLE_SYSTEM, EquipmentKind.fromStorage("cable_system"))
        assertFailsWith<IllegalArgumentException> { EquipmentKind.fromStorage("2_to_1_cable") }
    }
}
