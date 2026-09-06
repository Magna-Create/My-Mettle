package dev.kian.mymettle.equipment

import dev.kian.mymettle.data.local.dao.EquipmentDao
import dev.kian.mymettle.data.local.entity.EquipmentFactVersionEntity
import dev.kian.mymettle.data.local.entity.EquipmentInstanceEntity
import dev.kian.mymettle.data.local.entity.PreferredEquipmentBindingEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEquipmentBindingEntity
import dev.kian.mymettle.data.local.entity.SetObservationEquipmentOverrideEntity
import dev.kian.mymettle.data.local.entity.SetObservationLoadSemanticsEntity
import dev.kian.mymettle.domain.equipment.EquipmentFactProvenance
import dev.kian.mymettle.domain.equipment.EquipmentFactType
import dev.kian.mymettle.domain.equipment.EquipmentFactUnit
import dev.kian.mymettle.domain.equipment.EquipmentFactValue
import dev.kian.mymettle.domain.equipment.EquipmentFactVersion
import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.EquipmentInstance
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.equipment.ObservationLoadSemantics
import dev.kian.mymettle.domain.equipment.SessionExerciseEquipmentBinding
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class EquipmentHistoricalSnapshotTest {
    private val dao = SnapshotEquipmentDao()
    private val repository = EquipmentContextRepository(dao, SnapshotTransactionRunner)

    @Test
    fun snapshotUsesFactVersionValidAtObservationTimeRatherThanNewestFact() = runBlocking {
        repository.createEquipment(
            userProfileId = "user",
            equipment = EquipmentInstance(
                id = EquipmentId("equipment:a"),
                localLabel = "bar",
                source = "test",
                createdAt = "2026-01-01T00:00:00Z",
                archivedAt = null,
            ),
        )
        repository.publishFact(implementMassFact("fact:20", version = 1, kg = 20.0, "2026-01-01T00:00:00Z"))
        repository.publishFact(implementMassFact("fact:15", version = 2, kg = 15.0, "2026-02-01T00:00:00Z"))
        dao.observationToSessionExercise["observation:1"] = "session-exercise:1"
        repository.bindSessionActualEquipment(
            SessionExerciseEquipmentBinding(
                sessionExerciseId = "session-exercise:1",
                equipmentId = EquipmentId("equipment:a"),
                source = "recorded-session-choice",
                boundAt = "2026-01-10T00:00:00Z",
            ),
        )
        repository.recordObservationLoadSemantics(
            ObservationLoadSemantics(
                observationId = "observation:1",
                externalLoadAccounting = ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY,
                source = "explicit-entry-semantics",
                recordedAt = "2026-01-10T00:01:00Z",
            ),
        )

        val january = repository.resolveHistoricalInterpretationSnapshot(
            observationId = "observation:1",
            asOf = Instant.parse("2026-01-15T00:00:00Z"),
        )
        val atSuccessorBoundary = repository.resolveHistoricalInterpretationSnapshot(
            observationId = "observation:1",
            asOf = Instant.parse("2026-02-01T00:00:00Z"),
        )
        val march = repository.resolveHistoricalInterpretationSnapshot(
            observationId = "observation:1",
            asOf = Instant.parse("2026-03-01T00:00:00Z"),
        )

        assertEquals(20.0, scalarValue(january.timeValidFacts.single()))
        assertEquals("fact:20", january.timeValidFacts.single().id)
        assertEquals(15.0, scalarValue(atSuccessorBoundary.timeValidFacts.single()))
        assertEquals("fact:15", atSuccessorBoundary.timeValidFacts.single().id)
        assertEquals(15.0, scalarValue(march.timeValidFacts.single()))
        assertEquals(ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY, january.loadSemantics?.externalLoadAccounting)
        Unit
    }

    private fun scalarValue(fact: EquipmentFactVersion): Double =
        assertIs<EquipmentFactValue.Scalar>(fact.value).value

    private fun implementMassFact(id: String, version: Int, kg: Double, effectiveAt: String) = EquipmentFactVersion(
        id = id,
        equipmentId = EquipmentId("equipment:a"),
        factType = EquipmentFactType.IMPLEMENT_MASS,
        version = version,
        value = EquipmentFactValue.Scalar(kg, EquipmentFactUnit.KILOGRAM),
        scope = "configured implement",
        provenance = EquipmentFactProvenance.USER_CONFIRMED_CONFIGURATION,
        provenanceReference = "test fixture",
        quality = null,
        createdAt = effectiveAt,
        effectiveAt = effectiveAt,
        supersededAt = null,
    )
}

private object SnapshotTransactionRunner : EquipmentTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

private class SnapshotEquipmentDao : EquipmentDao {
    private val equipment = linkedMapOf<String, EquipmentInstanceEntity>()
    private val facts = mutableListOf<EquipmentFactVersionEntity>()
    private val sessionBindings = linkedMapOf<String, SessionExerciseEquipmentBindingEntity>()
    private val observationOverrides = linkedMapOf<String, SetObservationEquipmentOverrideEntity>()
    private val loadSemantics = linkedMapOf<String, SetObservationLoadSemanticsEntity>()
    val observationToSessionExercise = mutableMapOf<String, String>()

    override suspend fun insertEquipmentInstances(rows: List<EquipmentInstanceEntity>) {
        rows.forEach { equipment[it.id] = it }
    }

    override suspend fun insertEquipmentFactVersions(rows: List<EquipmentFactVersionEntity>) {
        facts += rows
    }

    override suspend fun insertPreferredEquipmentBindings(rows: List<PreferredEquipmentBindingEntity>) = Unit

    override suspend fun insertSessionExerciseEquipmentBindings(rows: List<SessionExerciseEquipmentBindingEntity>) {
        rows.forEach { sessionBindings[it.sessionExerciseId] = it }
    }

    override suspend fun insertSetObservationEquipmentOverrides(rows: List<SetObservationEquipmentOverrideEntity>) {
        rows.forEach { observationOverrides[it.observationId] = it }
    }

    override suspend fun insertSetObservationLoadSemantics(rows: List<SetObservationLoadSemanticsEntity>) {
        rows.forEach { loadSemantics[it.observationId] = it }
    }

    override suspend fun equipmentInstances(ids: List<String>): List<EquipmentInstanceEntity> = ids.mapNotNull(equipment::get)

    override suspend fun equipmentFactVersions(equipmentId: String): List<EquipmentFactVersionEntity> =
        facts.filter { it.equipmentId == equipmentId }.sortedWith(compareBy({ it.factType }, { it.version }))

    override suspend fun supersedeEquipmentFactVersion(id: String, supersededAt: String): Int {
        val index = facts.indexOfFirst { it.id == id && it.supersededAt == null }
        if (index < 0) return 0
        facts[index] = facts[index].copy(supersededAt = supersededAt)
        return 1
    }

    override suspend fun currentPreferredEquipmentBindings(executionProfileId: String): List<PreferredEquipmentBindingEntity> =
        emptyList()

    override suspend fun supersedePreferredEquipmentBinding(id: String, supersededAt: String): Int = 0

    override suspend fun sessionExerciseEquipmentBinding(sessionExerciseId: String): SessionExerciseEquipmentBindingEntity? =
        sessionBindings[sessionExerciseId]

    override suspend fun setObservationEquipmentOverride(observationId: String): SetObservationEquipmentOverrideEntity? =
        observationOverrides[observationId]

    override suspend fun setObservationLoadSemantics(observationId: String): SetObservationLoadSemanticsEntity? =
        loadSemantics[observationId]

    override suspend fun sessionExerciseIdForObservation(observationId: String): String? =
        observationToSessionExercise[observationId]
}
