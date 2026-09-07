package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CapabilityEquipmentContext
import dev.kian.mymettle.domain.inference.CapabilitySourceProfileSemantics
import dev.kian.mymettle.domain.inference.CapabilityTransferSource
import dev.kian.mymettle.domain.inference.CapabilityTransferSourceFactory
import dev.kian.mymettle.domain.inference.DynamicMetricEvidenceAudit
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DynamicTransferM0SourceSelectionPolicyTest {
    @Test
    fun `independent policy returns every admissible direct edge separately in deterministic order`() {
        val destination = destinationContext(sessionCount = 4)
        val heldOut = heldOut(destination, secondsIntoDay = 10)
        val sourceA = sourceBoundary(SOURCE_A_PROFILE, SOURCE_A_EQUIPMENT, "source-a", sessionCount = 4)
        val sourceB = sourceBoundary(SOURCE_B_PROFILE, SOURCE_B_EQUIPMENT, "source-b", sessionCount = 4)
        val relationshipA = relationship("a-to-destination", sourceA, destination)
        val relationshipB = relationship("b-to-destination", sourceB, destination)
        val policy = DynamicTransferM0SourceSelectionPolicies.scoreIndependently(
            policyId = "independent-direct-edges",
            version = 1,
            frozenAt = heldOut.firstObservationTime.minusSeconds(1),
        )

        val forward = DynamicTransferM0SourceSelector.decide(
            destinationSession = heldOut,
            policy = policy,
            candidates = listOf(candidate(sourceB, relationshipB), candidate(sourceA, relationshipA)),
        )
        val replay = DynamicTransferM0SourceSelector.decide(
            destinationSession = heldOut,
            policy = policy,
            candidates = listOf(candidate(sourceA, relationshipA), candidate(sourceB, relationshipB)),
        )

        val independent = assertIs<DynamicTransferM0SourceSelectionDecision.IndependentCandidates>(forward)
        val replayIndependent = assertIs<DynamicTransferM0SourceSelectionDecision.IndependentCandidates>(replay)
        assertEquals(listOf("a-to-destination", "b-to-destination"), independent.candidates.map { it.edgeKey.relationshipId })
        assertEquals(independent.candidates.map { it.edgeKey }, replayIndependent.candidates.map { it.edgeKey })
        assertTrue(independent.rejected.isEmpty())
        independent.candidates.forEach { assessed ->
            assertIs<DynamicTransferM0FrozenSourcePairing.Paired>(assessed.frozenSession.sourcePairing)
        }
    }

    @Test
    fun `explicit priority selects first admissible edge and priority order changes policy identity`() {
        val destination = destinationContext(sessionCount = 4)
        val heldOut = heldOut(destination, secondsIntoDay = 10)
        val sourceA = sourceBoundary(SOURCE_A_PROFILE, SOURCE_A_EQUIPMENT, "source-a", sessionCount = 4)
        val sourceB = sourceBoundary(SOURCE_B_PROFILE, SOURCE_B_EQUIPMENT, "source-b", sessionCount = 4)
        val relationshipA = relationship("a-to-destination", sourceA, destination)
        val relationshipB = relationship("b-to-destination", sourceB, destination)
        val bFirst = DynamicTransferM0SourceSelectionPolicies.selectByExplicitPriority(
            policyId = "explicit-source-priority",
            version = 1,
            frozenAt = heldOut.firstObservationTime.minusSeconds(1),
            orderedRelationships = listOf(relationshipB, relationshipA),
        )
        val aFirst = DynamicTransferM0SourceSelectionPolicies.selectByExplicitPriority(
            policyId = "explicit-source-priority",
            version = 2,
            frozenAt = heldOut.firstObservationTime.minusSeconds(1),
            orderedRelationships = listOf(relationshipA, relationshipB),
        )

        val decision = assertIs<DynamicTransferM0SourceSelectionDecision.SelectedSingle>(
            DynamicTransferM0SourceSelector.decide(
                destinationSession = heldOut,
                policy = bFirst,
                candidates = listOf(candidate(sourceA, relationshipA), candidate(sourceB, relationshipB)),
            ),
        )
        assertEquals("b-to-destination", decision.selected.edgeKey.relationshipId)
        assertEquals(listOf("a-to-destination"), decision.admissibleButNotSelected.map { it.edgeKey.relationshipId })
        assertNotEquals(bFirst.policyIdentity, aFirst.policyIdentity)
    }

    @Test
    fun `explicit priority skips inadmissible first edge and excludes unlisted source without learning a ranking`() {
        val destination = destinationContext(sessionCount = 4)
        val heldOut = heldOut(destination, secondsIntoDay = 1)
        val sourceA = sourceBoundary(SOURCE_A_PROFILE, SOURCE_A_EQUIPMENT, "source-a", sessionCount = 4)
        val futureSourceB = sourceBoundary(SOURCE_B_PROFILE, SOURCE_B_EQUIPMENT, "source-b", sessionCount = 5)
        val sourceC = sourceBoundary(SOURCE_C_PROFILE, SOURCE_C_EQUIPMENT, "source-c", sessionCount = 4)
        val relationshipA = relationship("a-to-destination", sourceA, destination)
        val relationshipB = relationship("b-to-destination", futureSourceB, destination)
        val relationshipC = relationship("c-to-destination", sourceC, destination)
        val policy = DynamicTransferM0SourceSelectionPolicies.selectByExplicitPriority(
            policyId = "explicit-source-priority",
            version = 1,
            frozenAt = heldOut.firstObservationTime.minusSeconds(1),
            orderedRelationships = listOf(relationshipB, relationshipA),
        )

        val decision = assertIs<DynamicTransferM0SourceSelectionDecision.SelectedSingle>(
            DynamicTransferM0SourceSelector.decide(
                destinationSession = heldOut,
                policy = policy,
                candidates = listOf(
                    candidate(sourceC, relationshipC),
                    candidate(futureSourceB, relationshipB),
                    candidate(sourceA, relationshipA),
                ),
            ),
        )
        assertEquals("a-to-destination", decision.selected.edgeKey.relationshipId)
        val futureRefusal = decision.rejected.single { it.edgeKey.relationshipId == "b-to-destination" }
        assertEquals(
            DynamicTransferM0FreezeFailure.SOURCE_NOT_STRICTLY_PRIOR,
            assertIs<DynamicTransferM0SourceCandidateRefusal.FreezeFailure>(futureRefusal.refusal).reason,
        )
        assertIs<DynamicTransferM0SourceCandidateRefusal.OutsideExplicitPriority>(
            decision.rejected.single { it.edgeKey.relationshipId == "c-to-destination" }.refusal,
        )
    }

    @Test
    fun `unsupported transfer returns no source while destination n0 remains available`() {
        val destination = destinationContext(sessionCount = 4)
        val heldOut = heldOut(destination, secondsIntoDay = 1)
        val futureSource = sourceBoundary(SOURCE_A_PROFILE, SOURCE_A_EQUIPMENT, "future-source", sessionCount = 5)
        val relationship = relationship("future-to-destination", futureSource, destination)
        val policy = DynamicTransferM0SourceSelectionPolicies.scoreIndependently(
            policyId = "independent-direct-edges",
            version = 1,
            frozenAt = heldOut.firstObservationTime.minusSeconds(1),
        )

        val decision = assertIs<DynamicTransferM0SourceSelectionDecision.NoAdmissibleSource>(
            DynamicTransferM0SourceSelector.decide(
                destinationSession = heldOut,
                policy = policy,
                candidates = listOf(candidate(futureSource, relationship)),
            ),
        )
        assertEquals(destination.n0, decision.destinationSession.destination.n0)
        assertEquals(1, decision.rejected.size)
    }

    @Test
    fun `selection policy must be frozen before outcome and duplicate exact edges fail closed`() {
        val destination = destinationContext(sessionCount = 4)
        val heldOut = heldOut(destination, secondsIntoDay = 10)
        val source = sourceBoundary(SOURCE_A_PROFILE, SOURCE_A_EQUIPMENT, "source-a", sessionCount = 4)
        val relationship = relationship("a-to-destination", source, destination)
        val candidate = candidate(source, relationship)
        val latePolicy = DynamicTransferM0SourceSelectionPolicies.scoreIndependently(
            policyId = "independent-direct-edges",
            version = 1,
            frozenAt = heldOut.firstObservationTime,
        )
        assertEquals(
            DynamicTransferM0SourceSelectionPolicyFailure.POLICY_NOT_STRICTLY_PRIOR,
            assertFailsWith<DynamicTransferM0SourceSelectionPolicyException> {
                DynamicTransferM0SourceSelector.decide(heldOut, latePolicy, listOf(candidate))
            }.reason,
        )

        val priorPolicy = latePolicy.copy(frozenAt = heldOut.firstObservationTime.minusSeconds(1))
        assertEquals(
            DynamicTransferM0SourceSelectionPolicyFailure.DUPLICATE_CANDIDATE_EDGE,
            assertFailsWith<DynamicTransferM0SourceSelectionPolicyException> {
                DynamicTransferM0SourceSelector.decide(heldOut, priorPolicy, listOf(candidate, candidate))
            }.reason,
        )
    }

    private fun heldOut(
        destination: DynamicTransferM0DestinationContext,
        secondsIntoDay: Long,
    ) = DynamicTransferM0DestinationSessionDescriptor(
        sessionId = "held-out-destination-session",
        firstObservationTime = BASE.plusSeconds(4 * DAY_SECONDS + secondsIntoDay),
        destination = destination,
    )

    private fun candidate(
        source: CapabilityTransferSource,
        relationship: DirectedDynamicTransferRelationshipDescriptor,
    ) = DynamicTransferM0SourceCandidateInput(
        source = source,
        sourceLoadAccounting = stableLoad(source.selectedObservationIds.toSet()),
        relationship = relationship,
        replayDependencyScope = DynamicTransferM0ReplayDependencyScope(setOf("dep-${relationship.relationshipId}")),
    )

    private fun sourceBoundary(
        profile: DynamicResistanceProfileSemantics,
        equipmentId: EquipmentId,
        prefix: String,
        sessionCount: Int,
    ): CapabilityTransferSource {
        val fit = champion.fit(
            destinationProjection = projection(profile, generated(profile, prefix, trend = 0.02, sessionCount = sessionCount)),
            inferenceHorizon = BASE.plusSeconds((sessionCount + 1L) * DAY_SECONDS),
            configCreatedAt = CONFIG_CREATED_AT,
        ).destinationFit
        return CapabilityTransferSourceFactory.fromDynamicTrendFit(
            profile = profile,
            fit = fit,
            equipmentContext = CapabilityEquipmentContext.ResolvedSingleContext(
                equipmentId = equipmentId,
                interpretationVersion = "$prefix-local-v1",
                contributingObservationIds = fit.selectedObservationIds.toSet(),
                equipmentFactVersionIds = setOf("$prefix-fact-v1"),
            ),
        )
    }

    private fun destinationContext(sessionCount: Int): DynamicTransferM0DestinationContext {
        val n0 = champion.fit(
            destinationProjection = projection(
                DESTINATION_PROFILE,
                generated(DESTINATION_PROFILE, "destination", trend = 0.01, sessionCount = sessionCount),
            ),
            inferenceHorizon = BASE.plusSeconds((sessionCount + 1L) * DAY_SECONDS),
            configCreatedAt = CONFIG_CREATED_AT,
        )
        return DynamicTransferM0DestinationContext(
            n0 = n0,
            profile = CapabilitySourceProfileSemantics.from(DESTINATION_PROFILE),
            equipmentContext = CapabilityEquipmentContext.ResolvedSingleContext(
                equipmentId = DESTINATION_EQUIPMENT,
                interpretationVersion = "destination-local-v1",
                contributingObservationIds = n0.destinationFit.selectedObservationIds.toSet(),
                equipmentFactVersionIds = setOf("destination-fact-v1"),
            ),
            loadAccounting = stableLoad(n0.destinationFit.selectedObservationIds.toSet()),
        )
    }

    private fun relationship(
        relationshipId: String,
        source: CapabilityTransferSource,
        destination: DynamicTransferM0DestinationContext,
    ): DirectedDynamicTransferRelationshipDescriptor {
        val sourceEquipment = source.equipmentContext as CapabilityEquipmentContext.ResolvedSingleContext
        val destinationEquipment = destination.equipmentContext as CapabilityEquipmentContext.ResolvedSingleContext
        return DirectedDynamicTransferRelationshipDescriptor(
            relationshipId = relationshipId,
            version = 1,
            policyIdentity = "fixture-explicit-directed-v1",
            sourceExecutionProfileId = source.profile.executionProfileId,
            sourceExecutionProfileVersionId = source.profile.executionProfileVersionId,
            destinationExecutionProfileId = destination.profile.executionProfileId,
            destinationExecutionProfileVersionId = destination.profile.executionProfileVersionId,
            side = source.side,
            sourceEquipmentId = sourceEquipment.equipmentId,
            sourceEquipmentInterpretationVersion = sourceEquipment.interpretationVersion,
            sourceEquipmentFactVersionIds = sourceEquipment.equipmentFactVersionIds,
            destinationEquipmentId = destinationEquipment.equipmentId,
            destinationEquipmentInterpretationVersion = destinationEquipment.interpretationVersion,
            destinationEquipmentFactVersionIds = destinationEquipment.equipmentFactVersionIds,
            sourceLoadAccounting = ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
            destinationLoadAccounting = ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
        )
    }

    private fun stableLoad(observationIds: Set<String>) = DynamicTransferM0LoadAccountingContext.StableKnown(
        accounting = ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
        contributingObservationIds = observationIds,
    )

    private fun generated(
        profile: DynamicResistanceProfileSemantics,
        prefix: String,
        trend: Double,
        sessionCount: Int,
    ): List<DynamicResistanceEvidence> = buildList {
        repeat(sessionCount) { session ->
            listOf(6, 8, 12).forEachIndexed { ordinal, reps ->
                val logFrontierAtEight = ln(78.0) + trend * session
                val resistance = exp(logFrontierAtEight - 0.17 * ln(reps / 8.0) - 0.03)
                add(set(profile, "$prefix-${session}_$ordinal", "$prefix-session-$session", reps, resistance, session, ordinal))
            }
        }
    }

    private fun projection(
        profile: DynamicResistanceProfileSemantics,
        evidence: List<DynamicResistanceEvidence>,
    ) = DynamicResistanceEvidenceProjection(
        profile = profile,
        side = Laterality.BILATERAL,
        evidence = evidence,
        exclusions = emptyList(),
        referenceRepetitions = null,
        policy = DynamicResistanceV2Contract.evidencePolicy,
    )

    private fun set(
        profile: DynamicResistanceProfileSemantics,
        id: String,
        sessionId: String,
        reps: Int,
        resistanceKg: Double,
        day: Int,
        ordinal: Int,
    ): DynamicResistanceEvidence {
        val load = Quantity(resistanceKg, UnitId.KILOGRAM)
        return DynamicResistanceEvidence(
            observationId = "obs-$id",
            setRecordId = "set-$id",
            sessionId = sessionId,
            executionProfileVersionId = profile.executionProfileVersionId,
            side = Laterality.BILATERAL,
            completedAt = BASE.plusSeconds(day.toLong() * DAY_SECONDS + ordinal),
            repetitions = reps,
            resistance = dev.kian.mymettle.domain.inference.ProfileLocalResistanceCoordinate(
                value = resistanceKg,
                unit = UnitId.KILOGRAM,
                resistanceSemantics = ResistanceSemantics.EXTERNAL,
                entryBasis = EntryBasis.TOTAL,
                resistanceModelVersion = "m0-test-resistance-v1",
                resolverVersion = DynamicResistanceV2Contract.evidencePolicy.resistanceCoordinateResolverVersion,
            ),
            metricEvidence = listOf(
                DynamicMetricEvidenceAudit(
                    metric = PerformanceMetric.EXTERNAL_LOAD,
                    entered = load,
                    canonical = load,
                    acquisitionMethod = "synthetic",
                    evidenceGranularity = "set",
                ),
            ),
            warmUp = false,
            setKind = "working",
            evidencePolicyIdentity = DynamicResistanceV2Contract.evidencePolicy.identity,
        )
    }

    companion object {
        private const val DAY_SECONDS = 86_400L
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val CONFIG_CREATED_AT = Instant.parse("2026-09-06T00:00:00Z")
        private val champion = DynamicTransferN0Champion()
        private val DESTINATION_EQUIPMENT = EquipmentId("m0-selection-destination-equipment")
        private val SOURCE_A_EQUIPMENT = EquipmentId("m0-selection-source-a-equipment")
        private val SOURCE_B_EQUIPMENT = EquipmentId("m0-selection-source-b-equipment")
        private val SOURCE_C_EQUIPMENT = EquipmentId("m0-selection-source-c-equipment")
        private val DESTINATION_PROFILE = profile("m0-selection-destination-profile", "m0-selection-destination-version")
        private val SOURCE_A_PROFILE = profile("m0-selection-source-a-profile", "m0-selection-source-a-version")
        private val SOURCE_B_PROFILE = profile("m0-selection-source-b-profile", "m0-selection-source-b-version")
        private val SOURCE_C_PROFILE = profile("m0-selection-source-c-profile", "m0-selection-source-c-version")

        private fun profile(profileId: String, versionId: String) = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId(versionId),
            executionProfileId = ExecutionProfileId(profileId),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            resistanceModel = ResistanceModel(
                modelVersion = "m0-test-resistance-v1",
                semantics = ResistanceSemantics.EXTERNAL,
                bodyweightCoefficient = 0.0,
                externalLoadCoefficient = 1.0,
                assistanceCoefficient = 0.0,
            ),
            entryBasis = EntryBasis.TOTAL,
            lateralityMode = LateralityMode.BILATERAL_ONLY,
        )
    }
}
