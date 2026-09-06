package dev.kian.mymettle.equipment

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
import dev.kian.mymettle.domain.equipment.EquipmentFactVersion
import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.EquipmentInstance
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.equipment.ObservationEquipmentOverride
import dev.kian.mymettle.domain.equipment.ObservationEquipmentOverrideCorrection
import dev.kian.mymettle.domain.equipment.ObservationLoadSemantics
import dev.kian.mymettle.domain.equipment.ObservationLoadSemanticsCorrection
import dev.kian.mymettle.domain.equipment.PreferredEquipmentBinding
import dev.kian.mymettle.domain.equipment.SessionExerciseEquipmentBinding
import dev.kian.mymettle.domain.equipment.SessionExerciseEquipmentBindingCorrection
import dev.kian.mymettle.domain.inference.InferenceDependencyIndex
import dev.kian.mymettle.domain.inference.InferenceDependencyNode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class EquipmentContextRepositoryTest {
    private val dao = FakeEquipmentDao()
    private val correctionDao = FakeEquipmentCorrectionDao()
    private val repository = EquipmentContextRepository(dao, ImmediateTransactionRunner, correctionDao)

    @Test
    fun preferenceAndActualUseRemainSeparateAndOverrideWins() = runBlocking {
        createEquipment("equipment:a")
        createEquipment("equipment:b")
        createEquipment("equipment:c")
        dao.observationToSessionExercise["observation:1"] = "session-exercise:1"

        repository.setPreferredEquipment(preference("preference:1", "equipment:a", "2026-01-01T00:00:00Z"))
        repository.bindSessionActualEquipment(
            SessionExerciseEquipmentBinding(
                sessionExerciseId = "session-exercise:1",
                equipmentId = EquipmentId("equipment:b"),
                source = "session-choice",
                boundAt = "2026-01-02T00:00:00Z",
            ),
        )

        assertEquals(EquipmentId("equipment:a"), repository.currentPreferredEquipment("profile:1"))
        val historicalBeforePreferenceChange = repository.resolveHistoricalEquipment("observation:1")
        assertEquals(EquipmentId("equipment:b"), historicalBeforePreferenceChange?.equipmentId)
        assertEquals(EquipmentBindingResolutionSource.SESSION_EXERCISE, historicalBeforePreferenceChange?.resolutionSource)

        repository.setPreferredEquipment(preference("preference:2", "equipment:c", "2026-01-03T00:00:00Z"))
        val historicalAfterPreferenceChange = repository.resolveHistoricalEquipment("observation:1")
        assertEquals(EquipmentId("equipment:b"), historicalAfterPreferenceChange?.equipmentId)
        assertEquals(EquipmentBindingResolutionSource.SESSION_EXERCISE, historicalAfterPreferenceChange?.resolutionSource)

        repository.bindObservationEquipmentOverride(
            ObservationEquipmentOverride(
                observationId = "observation:1",
                equipmentId = EquipmentId("equipment:a"),
                source = "set-specific-choice",
                boundAt = "2026-01-02T00:05:00Z",
            ),
        )
        val overridden = repository.resolveHistoricalEquipment("observation:1")
        assertEquals(EquipmentId("equipment:a"), overridden?.equipmentId)
        assertEquals(EquipmentBindingResolutionSource.OBSERVATION_OVERRIDE, overridden?.resolutionSource)
        assertEquals("equipment:b", dao.sessionBindings.getValue("session-exercise:1").equipmentId)
        Unit
    }

    @Test
    fun historicalResolutionFailsUnknownInsteadOfUsingPreference() = runBlocking {
        createEquipment("equipment:a")
        repository.setPreferredEquipment(preference("preference:1", "equipment:a", "2026-01-01T00:00:00Z"))
        dao.observationToSessionExercise["observation:1"] = "session-exercise:1"

        assertNull(repository.resolveHistoricalEquipment("observation:1"))
        Unit
    }

    @Test
    fun factSuccessorSupersedesLifecycleMarkerWithoutRewritingOldValue() = runBlocking {
        createEquipment("equipment:a")
        repository.publishFact(implementMassFact("fact:1", 1, 20.0, "2026-01-01T00:00:00Z"))
        repository.publishFact(implementMassFact("fact:2", 2, 15.0, "2026-02-01T00:00:00Z"))

        val facts = dao.factVersions.filter { it.equipmentId == "equipment:a" && it.factType == "implement_mass" }
        assertEquals(2, facts.size)
        assertEquals(20.0, facts[0].numericValue)
        assertEquals("2026-02-01T00:00:00Z", facts[0].supersededAt)
        assertEquals(15.0, facts[1].numericValue)
        assertNull(facts[1].supersededAt)

        val failure = try {
            repository.publishFact(implementMassFact("fact:4", 4, 10.0, "2026-03-01T00:00:00Z"))
            null
        } catch (error: Throwable) {
            error
        }
        assertIs<IllegalArgumentException>(failure)
        Unit
    }

    @Test
    fun actualUseAndLoadSemanticsAreInsertOnlyExceptExactRetry() = runBlocking {
        createEquipment("equipment:a")
        createEquipment("equipment:b")
        val binding = SessionExerciseEquipmentBinding(
            sessionExerciseId = "session-exercise:1",
            equipmentId = EquipmentId("equipment:a"),
            source = "session-choice",
            boundAt = "2026-01-01T00:00:00Z",
        )
        repository.bindSessionActualEquipment(binding)
        repository.bindSessionActualEquipment(binding)

        val bindingFailure = try {
            repository.bindSessionActualEquipment(binding.copy(equipmentId = EquipmentId("equipment:b")))
            null
        } catch (error: Throwable) {
            error
        }
        assertIs<EquipmentContextException>(bindingFailure)

        val semantics = ObservationLoadSemantics(
            observationId = "observation:1",
            externalLoadAccounting = ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY,
            source = "explicit-entry-semantics",
            recordedAt = "2026-01-01T00:01:00Z",
        )
        repository.recordObservationLoadSemantics(semantics)
        repository.recordObservationLoadSemantics(semantics)

        val semanticsFailure = try {
            repository.recordObservationLoadSemantics(
                semantics.copy(externalLoadAccounting = ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD),
            )
            null
        } catch (error: Throwable) {
            error
        }
        assertIs<EquipmentContextException>(semanticsFailure)
        Unit
    }

    @Test
    fun correctionsAppendReplayRetractToUnknownAndInvalidateOnlyDependentDerivedNodes() = runBlocking {
        createEquipment("equipment:a")
        createEquipment("equipment:b")
        createEquipment("equipment:c")
        dao.observationToSessionExercise["observation:1"] = "session-exercise:1"

        repository.bindSessionActualEquipment(
            SessionExerciseEquipmentBinding(
                sessionExerciseId = "session-exercise:1",
                equipmentId = EquipmentId("equipment:a"),
                source = "recorded-session-choice",
                boundAt = "2026-01-02T10:00:00Z",
            ),
        )
        repository.bindObservationEquipmentOverride(
            ObservationEquipmentOverride(
                observationId = "observation:1",
                equipmentId = EquipmentId("equipment:c"),
                source = "recorded-set-choice",
                boundAt = "2026-01-02T10:01:00Z",
            ),
        )
        repository.recordObservationLoadSemantics(
            ObservationLoadSemantics(
                observationId = "observation:1",
                externalLoadAccounting = ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY,
                source = "recorded-load-meaning",
                recordedAt = "2026-01-02T10:02:00Z",
            ),
        )

        val sessionCorrection = SessionExerciseEquipmentBindingCorrection(
            id = "correction:session:1",
            sessionExerciseId = "session-exercise:1",
            version = 1,
            previousEquipmentId = EquipmentId("equipment:a"),
            correctedEquipmentId = EquipmentId("equipment:b"),
            source = "reviewed-history",
            reason = "wrong machine recorded",
            correctedAt = "2026-01-03T10:00:00Z",
        )
        val sessionImpact = repository.correctSessionActualEquipment(sessionCorrection)
        repository.correctSessionActualEquipment(sessionCorrection)
        val overrideImpact = repository.correctObservationEquipmentOverride(
            ObservationEquipmentOverrideCorrection(
                id = "correction:override:1",
                observationId = "observation:1",
                version = 1,
                previousEquipmentId = EquipmentId("equipment:c"),
                correctedEquipmentId = null,
                source = "reviewed-history",
                reason = "set override did not occur",
                correctedAt = "2026-01-03T10:01:00Z",
            ),
        )
        val loadImpact1 = repository.correctObservationLoadSemantics(
            ObservationLoadSemanticsCorrection(
                id = "correction:load:1",
                observationId = "observation:1",
                version = 1,
                previousExternalLoadAccounting = ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY,
                correctedExternalLoadAccounting = null,
                source = "reviewed-history",
                reason = "old convention could not be established",
                correctedAt = "2026-01-03T10:02:00Z",
            ),
        )
        val loadImpact2 = repository.correctObservationLoadSemantics(
            ObservationLoadSemanticsCorrection(
                id = "correction:load:2",
                observationId = "observation:1",
                version = 2,
                previousExternalLoadAccounting = null,
                correctedExternalLoadAccounting = ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
                source = "verified-entry-record",
                reason = "contemporaneous record confirmed inclusive load",
                correctedAt = "2026-01-04T10:02:00Z",
            ),
        )

        val resolved = repository.resolveHistoricalEquipment("observation:1")
        assertEquals(EquipmentId("equipment:b"), resolved?.equipmentId)
        assertEquals(EquipmentBindingResolutionSource.SESSION_EXERCISE, resolved?.resolutionSource)
        assertEquals("reviewed-history", resolved?.source)
        val snapshot = repository.resolveHistoricalInterpretationSnapshot(
            observationId = "observation:1",
            asOf = Instant.parse("2026-01-02T10:03:00Z"),
        )
        assertEquals(ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD, snapshot.loadSemantics?.externalLoadAccounting)
        assertEquals("verified-entry-record", snapshot.loadSemantics?.source)

        // Base canonical assertions are untouched; replay overlays the append-only correction chain.
        assertEquals("equipment:a", dao.sessionBindings.getValue("session-exercise:1").equipmentId)
        assertEquals("equipment:c", dao.observationOverrides.getValue("observation:1").equipmentId)
        assertEquals(
            ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY.storageValue,
            dao.loadSemantics.getValue("observation:1").externalLoadAccounting,
        )
        assertEquals(1, correctionDao.sessionCorrections.size)
        assertEquals(1, correctionDao.overrideCorrections.size)
        assertEquals(2, correctionDao.loadCorrections.size)
        assertEquals(loadImpact1.sourceDependencyIds, loadImpact2.sourceDependencyIds)
        assertEquals(
            setOf(EquipmentCanonicalDependencyId.sessionActualEquipment("session-exercise:1")),
            sessionImpact.sourceDependencyIds,
        )
        assertEquals(
            setOf(EquipmentCanonicalDependencyId.observationEquipmentOverride("observation:1")),
            overrideImpact.sourceDependencyIds,
        )

        val dependencyRoot = sessionImpact.sourceDependencyIds.single()
        val index = InferenceDependencyIndex(
            listOf(
                InferenceDependencyNode("local-interpretation", "equipment_interpretation", setOf(dependencyRoot), "7f-local", null),
                InferenceDependencyNode("translation", "translation_candidate", setOf("local-interpretation"), "7f-transfer", "solver"),
                InferenceDependencyNode(
                    "unrelated",
                    "equipment_interpretation",
                    setOf(EquipmentCanonicalDependencyId.sessionActualEquipment("session-exercise:other")),
                    "7f-local",
                    null,
                ),
            ),
        )
        assertEquals(setOf("local-interpretation", "translation"), index.invalidatedBy(dependencyRoot))

        val stale = runCatching {
            repository.correctSessionActualEquipment(
                sessionCorrection.copy(
                    id = "correction:session:2",
                    version = 2,
                    previousEquipmentId = EquipmentId("equipment:a"),
                    correctedEquipmentId = EquipmentId("equipment:c"),
                    correctedAt = "2026-01-05T10:00:00Z",
                ),
            )
        }.exceptionOrNull()
        assertIs<IllegalArgumentException>(stale)
        Unit
    }

    private suspend fun createEquipment(id: String) {
        repository.createEquipment(
            userProfileId = "user",
            equipment = EquipmentInstance(
                id = EquipmentId(id),
                localLabel = id,
                source = "test",
                createdAt = "2026-01-01T00:00:00Z",
                archivedAt = null,
            ),
        )
    }

    private fun preference(id: String, equipmentId: String, effectiveAt: String) = PreferredEquipmentBinding(
        id = id,
        executionProfileId = "profile:1",
        equipmentId = EquipmentId(equipmentId),
        effectiveAt = effectiveAt,
        supersededAt = null,
        source = "explicit-preference",
        createdAt = effectiveAt,
    )

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

private object ImmediateTransactionRunner : EquipmentTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

private class FakeEquipmentDao : EquipmentDao {
    val equipment = linkedMapOf<String, EquipmentInstanceEntity>()
    val factVersions = mutableListOf<EquipmentFactVersionEntity>()
    val preferences = mutableListOf<PreferredEquipmentBindingEntity>()
    val sessionBindings = linkedMapOf<String, SessionExerciseEquipmentBindingEntity>()
    val observationOverrides = linkedMapOf<String, SetObservationEquipmentOverrideEntity>()
    val loadSemantics = linkedMapOf<String, SetObservationLoadSemanticsEntity>()
    val observationToSessionExercise = mutableMapOf<String, String>()

    override suspend fun insertEquipmentInstances(rows: List<EquipmentInstanceEntity>) {
        rows.forEach { row ->
            check(row.id !in equipment)
            equipment[row.id] = row
        }
    }

    override suspend fun insertEquipmentFactVersions(rows: List<EquipmentFactVersionEntity>) {
        rows.forEach { row ->
            check(factVersions.none { it.id == row.id })
            check(factVersions.none {
                it.equipmentId == row.equipmentId && it.factType == row.factType && it.version == row.version
            })
            factVersions += row
        }
    }

    override suspend fun insertPreferredEquipmentBindings(rows: List<PreferredEquipmentBindingEntity>) {
        rows.forEach { row ->
            check(preferences.none { it.id == row.id })
            preferences += row
        }
    }

    override suspend fun insertSessionExerciseEquipmentBindings(rows: List<SessionExerciseEquipmentBindingEntity>) {
        rows.forEach { row ->
            check(row.sessionExerciseId !in sessionBindings)
            sessionBindings[row.sessionExerciseId] = row
        }
    }

    override suspend fun insertSetObservationEquipmentOverrides(rows: List<SetObservationEquipmentOverrideEntity>) {
        rows.forEach { row ->
            check(row.observationId !in observationOverrides)
            observationOverrides[row.observationId] = row
        }
    }

    override suspend fun insertSetObservationLoadSemantics(rows: List<SetObservationLoadSemanticsEntity>) {
        rows.forEach { row ->
            check(row.observationId !in loadSemantics)
            loadSemantics[row.observationId] = row
        }
    }

    override suspend fun equipmentInstances(ids: List<String>): List<EquipmentInstanceEntity> =
        ids.mapNotNull(equipment::get)

    override suspend fun equipmentFactVersions(equipmentId: String): List<EquipmentFactVersionEntity> =
        factVersions.filter { it.equipmentId == equipmentId }.sortedWith(compareBy({ it.factType }, { it.version }))

    override suspend fun supersedeEquipmentFactVersion(id: String, supersededAt: String): Int {
        val index = factVersions.indexOfFirst { it.id == id && it.supersededAt == null }
        if (index < 0) return 0
        factVersions[index] = factVersions[index].copy(supersededAt = supersededAt)
        return 1
    }

    override suspend fun currentPreferredEquipmentBindings(executionProfileId: String): List<PreferredEquipmentBindingEntity> =
        preferences.filter { it.executionProfileId == executionProfileId && it.supersededAt == null }
            .sortedBy { it.effectiveAt }

    override suspend fun supersedePreferredEquipmentBinding(id: String, supersededAt: String): Int {
        val index = preferences.indexOfFirst { it.id == id && it.supersededAt == null }
        if (index < 0) return 0
        preferences[index] = preferences[index].copy(supersededAt = supersededAt)
        return 1
    }

    override suspend fun sessionExerciseEquipmentBinding(sessionExerciseId: String): SessionExerciseEquipmentBindingEntity? =
        sessionBindings[sessionExerciseId]

    override suspend fun setObservationEquipmentOverride(observationId: String): SetObservationEquipmentOverrideEntity? =
        observationOverrides[observationId]

    override suspend fun setObservationLoadSemantics(observationId: String): SetObservationLoadSemanticsEntity? =
        loadSemantics[observationId]

    override suspend fun sessionExerciseIdForObservation(observationId: String): String? =
        observationToSessionExercise[observationId]
}

private class FakeEquipmentCorrectionDao : EquipmentCorrectionDao {
    val sessionCorrections = mutableListOf<SessionExerciseEquipmentBindingCorrectionEntity>()
    val overrideCorrections = mutableListOf<SetObservationEquipmentOverrideCorrectionEntity>()
    val loadCorrections = mutableListOf<SetObservationLoadSemanticsCorrectionEntity>()

    override suspend fun insertSessionExerciseEquipmentBindingCorrections(
        rows: List<SessionExerciseEquipmentBindingCorrectionEntity>,
    ) {
        rows.forEach { row ->
            check(sessionCorrections.none { it.id == row.id })
            check(sessionCorrections.none { it.sessionExerciseId == row.sessionExerciseId && it.version == row.version })
            sessionCorrections += row
        }
    }

    override suspend fun insertSetObservationEquipmentOverrideCorrections(
        rows: List<SetObservationEquipmentOverrideCorrectionEntity>,
    ) {
        rows.forEach { row ->
            check(overrideCorrections.none { it.id == row.id })
            check(overrideCorrections.none { it.observationId == row.observationId && it.version == row.version })
            overrideCorrections += row
        }
    }

    override suspend fun insertSetObservationLoadSemanticsCorrections(
        rows: List<SetObservationLoadSemanticsCorrectionEntity>,
    ) {
        rows.forEach { row ->
            check(loadCorrections.none { it.id == row.id })
            check(loadCorrections.none { it.observationId == row.observationId && it.version == row.version })
            loadCorrections += row
        }
    }

    override suspend fun sessionExerciseEquipmentBindingCorrections(
        sessionExerciseId: String,
    ): List<SessionExerciseEquipmentBindingCorrectionEntity> =
        sessionCorrections.filter { it.sessionExerciseId == sessionExerciseId }.sortedBy { it.version }

    override suspend fun setObservationEquipmentOverrideCorrections(
        observationId: String,
    ): List<SetObservationEquipmentOverrideCorrectionEntity> =
        overrideCorrections.filter { it.observationId == observationId }.sortedBy { it.version }

    override suspend fun setObservationLoadSemanticsCorrections(
        observationId: String,
    ): List<SetObservationLoadSemanticsCorrectionEntity> =
        loadCorrections.filter { it.observationId == observationId }.sortedBy { it.version }
}
