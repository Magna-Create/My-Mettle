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
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierPosteriorNode
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DynamicTransferM0KernelTest {
    @Test
    fun `frozen m0 model config reproduces preregistered identity and beta quadrature`() {
        val config = NBio7FM0V1.modelConfig(CONFIG_CREATED_AT)
        assertEquals(NBio7FM0V1.EXPECTED_MODEL_CONFIG_ID, config.id.value)
        assertEquals("translation", config.component.storageValue)
        assertEquals(7, NBio7FM0V1.betaQuadrature.size)
        assertEquals(0.0, NBio7FM0V1.betaQuadrature.single { it.beta == 0.0 }.beta)
        assertTrue(abs(NBio7FM0V1.betaQuadrature.sumOf { it.priorWeight } - 1.0) <= 1e-15)
        assertEquals(
            "directed_dynamic_capability_transfer|m0-source-covariate-math-v1|" +
                "destinationBase=${NBio7FN0V1.mathematicalModelIdentity.identity};" +
                "equation=y=c+g*z-b*x+beta*q_source-u+epsilon;" +
                "sourceAnchor=S=logFrontierLatest-slope*ln(rD/rS);" +
                "sourceCenter=mean_paired_destination_sessions(E_source[S]);" +
                "sourceUncertainty=joint_source_node_mixture_inside_destination_likelihood;" +
                "betaPrior=normal(0,0.35);betaQuadrature=gauss_hermite_7_fixed;" +
                "n0Reuse=frozen_destination_n0_posterior_likelihood_ratio_reweight;" +
                "sessionWeight=equal_total_weight_per_session_v1;" +
                "chronology=destination_session_atomic_source_prior_sessions_only;" +
                "repDomain=no_extrapolation_source_anchor_or_m0_destination_prediction",
            NBio7FM0V1.mathematicalModelIdentity.identity,
        )
    }

    @Test
    fun `source coreset is deterministic and retains complete original joint tuples`() {
        val original = List(53) { index ->
            DynamicTrendFrontierPosteriorNode(
                logFrontierAtLatestSession = ln(60.0 + index),
                slope = 0.11 + index * 0.001,
                frontierTrend = -0.02 + index * 0.0007,
                slackScale = 0.06 + index * 0.0005,
                noiseScale = 0.025 + index * 0.0003,
                posteriorWeight = (index + 1).toDouble(),
            )
        }
        val first = DynamicTransferM0Kernel.buildSourceCoreset(original, 8.0, 10.0)
        val replay = DynamicTransferM0Kernel.buildSourceCoreset(original, 8.0, 10.0)

        assertEquals(first, replay)
        assertTrue(first.nodes.size <= 17)
        assertEquals(53, first.originalNodeCount)
        assertTrue(abs(first.nodes.sumOf { it.node.posteriorWeight } - 1.0) <= 1e-12)
        first.nodes.forEach { retained ->
            val upstream = original[retained.stableOriginalIndex]
            assertEquals(upstream.logFrontierAtLatestSession, retained.node.logFrontierAtLatestSession)
            assertEquals(upstream.slope, retained.node.slope)
            assertEquals(upstream.frontierTrend, retained.node.frontierTrend)
            assertEquals(upstream.slackScale, retained.node.slackScale)
            assertEquals(upstream.noiseScale, retained.node.noiseScale)
            assertEquals(upstream.posteriorWeight, retained.originalPosteriorWeight)
        }
    }

    @Test
    fun `beta zero reproduces n0 likelihood and prediction`() {
        val source = sourceBoundary()
        val destination = destinationContext()
        val prepared = DynamicTransferM0Kernel.prepareDirectedEdge(
            source = source,
            sourceLoadAccounting = stableLoad(source.selectedObservationIds.toSet()),
            destination = destination,
            relationship = relationship(source, destination),
        )
        val destinationNode = destination.n0.destinationFit.posteriorNodes.first()
        val centre = prepared.sourceCoreset.expectedAnchor
        val n0Density = DynamicTransferM0Kernel.n0ObservationLogDensity(
            destinationNode = destinationNode,
            yLogResistance = ln(72.0),
            destinationReferenceRepetitions = destination.n0.destinationFit.referenceRepetitions,
            repetitions = 8.0,
            destinationSessionOffset = 0.0,
        )
        val m0Density = DynamicTransferM0Kernel.m0ObservationLogDensity(
            destinationNode = destinationNode,
            yLogResistance = ln(72.0),
            destinationReferenceRepetitions = destination.n0.destinationFit.referenceRepetitions,
            repetitions = 8.0,
            destinationSessionOffset = 0.0,
            beta = 0.0,
            sourceCoreset = prepared.sourceCoreset,
            sourceCentre = centre,
        )
        assertEquals(n0Density, m0Density, 1e-12)

        val sourceCovariate = prepared.sourceCoreset.nodes.first().sourceAnchor - centre
        val n0Prediction = DynamicTransferM0Kernel.n0LogFrontier(
            destinationNode,
            destination.n0.destinationFit.referenceRepetitions,
            8.0,
            1.0,
        )
        val m0Prediction = DynamicTransferM0Kernel.m0LogFrontier(
            destinationNode = destinationNode,
            destinationFit = destination.n0,
            queryRepetitions = 8.0,
            destinationSessionOffset = 1.0,
            beta = 0.0,
            sourceCovariate = sourceCovariate,
        )
        assertEquals(n0Prediction, m0Prediction, 1e-15)
    }

    @Test
    fun `source centre freezes on one coordinate and rejects no variation`() {
        val source = sourceBoundary()
        val destination = destinationContext()
        val first = DynamicTransferM0Kernel.sourceCoreset(source, destination.n0.destinationFit.referenceRepetitions)
        val shifted = first.copy(
            nodes = first.nodes.map { retained ->
                retained.copy(sourceAnchor = retained.sourceAnchor + 0.02)
            },
        )
        val centre = DynamicTransferM0Kernel.freezeTrainingSourceCentre(listOf(first, shifted))
        assertTrue(centre.betweenSessionExpectedAnchorVariance > NBio7FM0V1.IDENTIFIABILITY_VARIANCE_FLOOR)
        assertEquals((first.expectedAnchor + shifted.expectedAnchor) / 2.0, centre.sourceCentre, 1e-15)

        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0Kernel.freezeTrainingSourceCentre(listOf(first, first))
        }
        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0Kernel.freezeTrainingSourceCentre(
                listOf(first, shifted.copy(destinationReferenceRepetitions = first.destinationReferenceRepetitions + 1.0)),
            )
        }
    }

    @Test
    fun `directed admissibility fails closed on mixed semantics unresolved equipment reverse edge and source extrapolation`() {
        val source = sourceBoundary()
        val destination = destinationContext()
        val relationship = relationship(source, destination)

        assertEquals(
            DynamicTransferM0InadmissibilityReason.SOURCE_LOAD_ACCOUNTING_UNKNOWN_OR_MIXED,
            assertFailsWith<DynamicTransferM0InadmissibleException> {
                DynamicTransferM0Kernel.prepareDirectedEdge(
                    source = source,
                    sourceLoadAccounting = DynamicTransferM0LoadAccountingContext.Mixed(
                        source.selectedObservationIds.toSet(),
                        "fixture intentionally mixes inclusive and added-only semantics",
                    ),
                    destination = destination,
                    relationship = relationship,
                )
            }.reason,
        )

        assertEquals(
            DynamicTransferM0InadmissibilityReason.DESTINATION_EQUIPMENT_UNRESOLVED,
            assertFailsWith<DynamicTransferM0InadmissibleException> {
                DynamicTransferM0Kernel.prepareDirectedEdge(
                    source = source,
                    sourceLoadAccounting = stableLoad(source.selectedObservationIds.toSet()),
                    destination = destination.copy(
                        equipmentContext = CapabilityEquipmentContext.Unresolved("fixture unresolved destination hardware"),
                    ),
                    relationship = relationship,
                )
            }.reason,
        )

        val reverse = relationship.copy(
            sourceExecutionProfileId = relationship.destinationExecutionProfileId,
            sourceExecutionProfileVersionId = relationship.destinationExecutionProfileVersionId,
            destinationExecutionProfileId = relationship.sourceExecutionProfileId,
            destinationExecutionProfileVersionId = relationship.sourceExecutionProfileVersionId,
        )
        assertEquals(
            DynamicTransferM0InadmissibilityReason.RELATIONSHIP_DIRECTION_MISMATCH,
            assertFailsWith<DynamicTransferM0InadmissibleException> {
                DynamicTransferM0Kernel.prepareDirectedEdge(
                    source = source,
                    sourceLoadAccounting = stableLoad(source.selectedObservationIds.toSet()),
                    destination = destination,
                    relationship = reverse,
                )
            }.reason,
        )

        assertEquals(
            DynamicTransferM0InadmissibilityReason.SOURCE_REFERENCE_REPETITIONS_OUTSIDE_DOMAIN,
            assertFailsWith<DynamicTransferM0InadmissibleException> {
                DynamicTransferM0Kernel.sourceCoreset(source, 20.0)
            }.reason,
        )
    }

    @Test
    fun `bodyweight dynamic source is not admissible for m0 external mass transfer`() {
        val source = sourceBoundary()
        val destination = destinationContext()
        val bodyweightSource = source.copy(
            profile = source.profile.copy(
                metricFamily = MetricFamily.BODYWEIGHT_RESISTANCE,
                resistanceSemantics = ResistanceSemantics.BODYWEIGHT,
            ),
        )
        assertEquals(
            DynamicTransferM0InadmissibilityReason.SOURCE_NOT_DYNAMIC_EXTERNAL_MASS,
            assertFailsWith<DynamicTransferM0InadmissibleException> {
                DynamicTransferM0Kernel.prepareDirectedEdge(
                    source = bodyweightSource,
                    sourceLoadAccounting = stableLoad(source.selectedObservationIds.toSet()),
                    destination = destination,
                    relationship = relationship(source, destination),
                )
            }.reason,
        )
    }

    private fun sourceBoundary(): CapabilityTransferSource {
        val fit = champion.fit(
            destinationProjection = projection(SOURCE_PROFILE, generated(SOURCE_PROFILE, "source", trend = 0.02)),
            inferenceHorizon = BASE.plusSeconds(5 * DAY_SECONDS),
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

    private fun destinationContext(): DynamicTransferM0DestinationContext {
        val n0 = champion.fit(
            destinationProjection = projection(DESTINATION_PROFILE, generated(DESTINATION_PROFILE, "destination", trend = 0.01)),
            inferenceHorizon = BASE.plusSeconds(5 * DAY_SECONDS),
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
    ): List<DynamicResistanceEvidence> = buildList {
        repeat(4) { session ->
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
        private val SOURCE_EQUIPMENT = EquipmentId("m0-source-equipment")
        private val DESTINATION_EQUIPMENT = EquipmentId("m0-destination-equipment")
        private val champion = DynamicTransferN0Champion()
        private val SOURCE_PROFILE = profile("m0-source-profile", "m0-source-version")
        private val DESTINATION_PROFILE = profile("m0-destination-profile", "m0-destination-version")

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
