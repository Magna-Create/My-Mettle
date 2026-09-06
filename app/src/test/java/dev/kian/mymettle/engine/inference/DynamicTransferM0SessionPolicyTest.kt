package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.equipment.EquipmentCanonicalDependencyId
import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.EquipmentInvalidationImpact
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DynamicTransferM0SessionPolicyTest {
    @Test
    fun `destination-session freeze rejects future source and same-session source evidence`() {
        val destination = destinationContext(sessionCount = 4)
        val heldOut = DynamicTransferM0DestinationSessionDescriptor(
            sessionId = "held-out-destination-session",
            firstObservationTime = BASE.plusSeconds(4 * DAY_SECONDS + 1),
            destination = destination,
        )

        val futureSource = sourceBoundary(sessionCount = 5)
        assertEquals(
            DynamicTransferM0FreezeFailure.SOURCE_NOT_STRICTLY_PRIOR,
            assertFailsWith<DynamicTransferM0FreezeException> {
                DynamicTransferM0SessionPolicy.freezePaired(
                    destinationSession = heldOut,
                    source = futureSource,
                    sourceLoadAccounting = stableLoad(futureSource.selectedObservationIds.toSet()),
                    relationship = relationship(futureSource, destination),
                )
            }.reason,
        )

        val priorSource = sourceBoundary(sessionCount = 4)
        val sameSessionSource = priorSource.copy(
            selectedSessionIds = priorSource.selectedSessionIds.dropLast(1) + heldOut.sessionId,
        )
        assertEquals(
            DynamicTransferM0FreezeFailure.SOURCE_CONTAINS_DESTINATION_SESSION,
            assertFailsWith<DynamicTransferM0FreezeException> {
                DynamicTransferM0SessionPolicy.freezePaired(
                    destinationSession = heldOut,
                    source = sameSessionSource,
                    sourceLoadAccounting = stableLoad(sameSessionSource.selectedObservationIds.toSet()),
                    relationship = relationship(sameSessionSource, destination),
                )
            }.reason,
        )

        val accepted = DynamicTransferM0SessionPolicy.freezePaired(
            destinationSession = heldOut,
            source = priorSource,
            sourceLoadAccounting = stableLoad(priorSource.selectedObservationIds.toSet()),
            relationship = relationship(priorSource, destination),
        )
        assertIs<DynamicTransferM0FrozenSourcePairing.Paired>(accepted.sourcePairing)
    }

    @Test
    fun `unpaired destination remains frozen n0 and contributes exactly zero m0 increment`() {
        val destination = destinationContext(sessionCount = 4)
        val descriptor = DynamicTransferM0DestinationSessionDescriptor(
            sessionId = "unpaired-destination-session",
            firstObservationTime = BASE.plusSeconds(4 * DAY_SECONDS + 10),
            destination = destination,
        )
        val frozen = DynamicTransferM0SessionPolicy.freezeUnpaired(
            destinationSession = descriptor,
            reason = "no admissible prior source snapshot",
        )

        assertEquals(descriptor, frozen.destinationSession)
        assertEquals(destination.n0, frozen.destinationSession.destination.n0)
        assertIs<DynamicTransferM0FrozenSourcePairing.Unpaired>(frozen.sourcePairing)
        assertEquals(0.0, DynamicTransferM0SessionPolicy.likelihoodRatioIncrement(frozen, 12.5))
    }

    @Test
    fun `one candidate accepts one direct edge and rejects multi-source or transitive edge paths`() {
        val source = sourceBoundary(sessionCount = 4)
        val destination = destinationContext(sessionCount = 4)
        val direct = relationship(source, destination)
        assertEquals(direct, DynamicTransferM0CandidateTopology.requireSingleDirectEdge(listOf(direct)))

        val alternativeSource = direct.copy(
            relationshipId = "alternative-source-to-destination",
            sourceExecutionProfileId = ExecutionProfileId("alternative-source-profile"),
            sourceExecutionProfileVersionId = ExecutionProfileVersionId("alternative-source-version"),
            sourceEquipmentId = EquipmentId("alternative-source-equipment"),
            sourceEquipmentInterpretationVersion = "alternative-source-local-v1",
            sourceEquipmentFactVersionIds = setOf("alternative-source-fact-v1"),
        )
        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0CandidateTopology.requireSingleDirectEdge(listOf(direct, alternativeSource))
        }

        val destinationToThird = direct.copy(
            relationshipId = "destination-to-third",
            sourceExecutionProfileId = direct.destinationExecutionProfileId,
            sourceExecutionProfileVersionId = direct.destinationExecutionProfileVersionId,
            destinationExecutionProfileId = ExecutionProfileId("third-profile"),
            destinationExecutionProfileVersionId = ExecutionProfileVersionId("third-version"),
            sourceEquipmentId = direct.destinationEquipmentId,
            sourceEquipmentInterpretationVersion = direct.destinationEquipmentInterpretationVersion,
            sourceEquipmentFactVersionIds = direct.destinationEquipmentFactVersionIds,
            destinationEquipmentId = EquipmentId("third-equipment"),
            destinationEquipmentInterpretationVersion = "third-local-v1",
            destinationEquipmentFactVersionIds = setOf("third-fact-v1"),
        )
        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0CandidateTopology.requireSingleDirectEdge(listOf(direct, destinationToThird))
        }
    }

    @Test
    fun `device ordinal and assistance resistance cannot enter m0`() {
        val source = sourceBoundary(sessionCount = 4)
        val destination = destinationContext(sessionCount = 4)
        listOf(ResistanceSemantics.DEVICE_ORDINAL, ResistanceSemantics.ASSISTANCE).forEach { semantics ->
            val incompatible = source.copy(profile = source.profile.copy(resistanceSemantics = semantics))
            assertEquals(
                DynamicTransferM0InadmissibilityReason.SOURCE_NOT_DYNAMIC_EXTERNAL_MASS,
                assertFailsWith<DynamicTransferM0InadmissibleException> {
                    DynamicTransferM0Kernel.prepareDirectedEdge(
                        source = incompatible,
                        sourceLoadAccounting = stableLoad(source.selectedObservationIds.toSet()),
                        destination = destination,
                        relationship = relationship(source, destination),
                    )
                }.reason,
            )
        }
    }

    @Test
    fun `m0 destination prediction refuses rep extrapolation while n0 coordinate remains available`() {
        val destination = destinationContext(sessionCount = 4)
        val fit = destination.n0.destinationFit
        val node = fit.posteriorNodes.first()
        val outside = fit.observedRepMax.toDouble() + 1.0

        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0Kernel.m0LogFrontier(
                destinationNode = node,
                destinationFit = destination.n0,
                queryRepetitions = outside,
                destinationSessionOffset = 1.0,
                beta = 0.0,
                sourceCovariate = 0.0,
            )
        }
        assertTrue(
            DynamicTransferM0Kernel.n0LogFrontier(
                destinationNode = node,
                destinationReferenceRepetitions = fit.referenceRepetitions,
                queryRepetitions = outside,
                destinationSessionOffset = 1.0,
            ).isFinite(),
        )
    }

    @Test
    fun `canonical correction impact invalidates only dependent derived m0 scope and leaves raw evidence unchanged`() {
        val rawEvidence = generated(DESTINATION_PROFILE, "raw", trend = 0.01, sessionCount = 4)
        val immutableSnapshot = rawEvidence.toList()
        val observationId = rawEvidence.first().observationId
        val dependencyId = EquipmentCanonicalDependencyId.observationLoadSemantics(observationId)
        val scope = DynamicTransferM0ReplayDependencyScope(setOf(dependencyId))

        assertTrue(scope.invalidatedBy(EquipmentInvalidationImpact(setOf(dependencyId))))
        assertFalse(
            scope.invalidatedBy(
                EquipmentInvalidationImpact(
                    setOf(EquipmentCanonicalDependencyId.observationLoadSemantics("unrelated-observation")),
                ),
            ),
        )
        assertEquals(immutableSnapshot, rawEvidence)
    }

    private fun sourceBoundary(sessionCount: Int): CapabilityTransferSource {
        val fit = champion.fit(
            destinationProjection = projection(
                SOURCE_PROFILE,
                generated(SOURCE_PROFILE, "source", trend = 0.02, sessionCount = sessionCount),
            ),
            inferenceHorizon = BASE.plusSeconds((sessionCount + 1L) * DAY_SECONDS),
            configCreatedAt = CONFIG_CREATED_AT,
        ).destinationFit
        return CapabilityTransferSourceFactory.fromDynamicTrendFit(
            profile = SOURCE_PROFILE,
            fit = fit,
            equipmentContext = CapabilityEquipmentContext.ResolvedSingleContext(
                equipmentId = SOURCE_EQUIPMENT,
                interpretationVersion = "source-local-v1",
                contributingObservationIds = fit.selectedObservationIds.toSet(),
                equipmentFactVersionIds = setOf("source-fact-v1"),
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
        source: CapabilityTransferSource,
        destination: DynamicTransferM0DestinationContext,
    ): DirectedDynamicTransferRelationshipDescriptor {
        val sourceEquipment = source.equipmentContext as CapabilityEquipmentContext.ResolvedSingleContext
        val destinationEquipment = destination.equipmentContext as CapabilityEquipmentContext.ResolvedSingleContext
        return DirectedDynamicTransferRelationshipDescriptor(
            relationshipId = "fixture-source-to-destination",
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
                add(
                    set(
                        profile = profile,
                        id = "$prefix-${session}_$ordinal",
                        sessionId = "$prefix-session-$session",
                        reps = reps,
                        resistanceKg = resistance,
                        day = session,
                        ordinal = ordinal,
                    ),
                )
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
        private val SOURCE_EQUIPMENT = EquipmentId("m0-policy-source-equipment")
        private val DESTINATION_EQUIPMENT = EquipmentId("m0-policy-destination-equipment")
        private val champion = DynamicTransferN0Champion()
        private val SOURCE_PROFILE = profile("m0-policy-source-profile", "m0-policy-source-version")
        private val DESTINATION_PROFILE = profile("m0-policy-destination-profile", "m0-policy-destination-version")

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
