package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CapabilityTransferBoundaryTest {
    @Test
    fun dynamicBoundaryPreservesJointSparsePosteriorAndCausalIdentity() {
        val evidenceAt = Instant.parse("2026-01-10T00:00:00Z")
        val fit = dynamicFit(evidenceAt = evidenceAt)
        val source = CapabilityTransferSourceFactory.fromDynamicTrendFit(
            profile = dynamicProfile(),
            fit = fit,
            equipmentContext = resolvedEquipment(setOf("observation:1")),
        )

        assertEquals(CapabilityTransferFamily.DYNAMIC_RESISTANCE, source.capabilityFamily)
        assertEquals(ExecutionProfileId("profile:1"), source.profile.executionProfileId)
        assertEquals(evidenceAt, source.causalCutoff.evidenceThrough)
        assertEquals(fit.inferenceHorizon, source.causalCutoff.asOf)
        assertEquals(fit.solverDiagnostics.solverIdentity, source.upstream.solverIdentity)
        assertEquals(InferencePosteriorRepresentation.WEIGHTED_SPARSE_NODES, source.upstream.posteriorRepresentation)
        assertEquals(
            CrossSourceDependenceStatus.NOT_ESTABLISHED_DO_NOT_ASSUME_INDEPENDENT,
            source.dependence.crossSource,
        )
        assertEquals(CapabilitySourceContextStatus.COMPLETE_FOR_ADMISSIBILITY_REVIEW, source.contextStatus)

        val posterior = assertIs<CapabilityPosteriorPayload.DynamicTrendNodes>(source.posterior)
        assertEquals(fit.posteriorNodes, posterior.nodes)
        assertEquals(fit.frontierAtLatestSession, posterior.anchorPosterior)
        val domain = assertIs<CapabilityQueryDomain.DynamicResistance>(source.queryDomain)
        assertEquals(5, domain.observedRepetitions.minimum)
        assertEquals(12, domain.observedRepetitions.maximum)
        assertEquals(50.0, domain.observedResistanceKg.minimum)
        assertEquals(100.0, domain.observedResistanceKg.maximum)
    }

    @Test
    fun unresolvedEquipmentContextRemainsExplicit() {
        val source = CapabilityTransferSourceFactory.fromDynamicTrendFit(
            profile = dynamicProfile(),
            fit = dynamicFit(),
            equipmentContext = CapabilityEquipmentContext.Unresolved("legacy history has no canonical actual-use binding"),
        )

        assertIs<CapabilityEquipmentContext.Unresolved>(source.equipmentContext)
        assertEquals(CapabilitySourceContextStatus.EQUIPMENT_CONTEXT_UNRESOLVED, source.contextStatus)
    }

    @Test
    fun resolvedEquipmentMustCoverEverySelectedObservation() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityTransferSourceFactory.fromDynamicTrendFit(
                profile = dynamicProfile(),
                fit = dynamicFit(),
                equipmentContext = resolvedEquipment(setOf("another-observation")),
            )
        }
    }

    @Test
    fun futureEvidenceCannotCrossCausalBoundary() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityTransferSourceFactory.fromDynamicTrendFit(
                profile = dynamicProfile(),
                fit = dynamicFit(
                    inferenceHorizon = Instant.parse("2026-01-09T00:00:00Z"),
                    evidenceAt = Instant.parse("2026-01-10T00:00:00Z"),
                ),
                equipmentContext = resolvedEquipment(setOf("observation:1")),
            )
        }
    }

    @Test
    fun nonDynamicBoundaryPreservesFamilyDomainAndJointNodes() {
        val fit = loadedHoldFit()
        val source = CapabilityTransferSourceFactory.fromNonDynamicFit(
            profile = nonDynamicProfile(MetricFamily.LOADED_HOLD),
            fit = fit,
            equipmentContext = resolvedEquipment(setOf("observation:1")),
        )

        assertEquals(CapabilityTransferFamily.LOADED_HOLD, source.capabilityFamily)
        assertEquals(fit.mathematicalModelIdentity, source.upstream.mathematicalModelIdentity)
        assertEquals(fit.solverDiagnostics.solverIdentity, source.upstream.solverIdentity)
        val posterior = assertIs<CapabilityPosteriorPayload.NonDynamicNodes>(source.posterior)
        assertEquals(fit.posteriorNodes, posterior.nodes)
        assertEquals(CapabilityTransferFamily.LOADED_HOLD, posterior.capabilityFamily)
        val domain = assertIs<CapabilityQueryDomain.LoadedHold>(source.queryDomain)
        assertEquals(20.0, domain.observedDurationSeconds.minimum)
        assertEquals(40.0, domain.observedDurationSeconds.maximum)
        assertEquals(30.0, domain.referenceDurationSeconds)
        assertEquals(UnitId.KILOGRAM, domain.outputUnit)
    }

    @Test
    fun nonDynamicProfileFamilyMismatchFailsClosed() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityTransferSourceFactory.fromNonDynamicFit(
                profile = nonDynamicProfile(MetricFamily.DURATION_ONLY),
                fit = loadedHoldFit(),
                equipmentContext = resolvedEquipment(setOf("observation:1")),
            )
        }
    }

    private fun dynamicProfile() = DynamicResistanceProfileSemantics(
        executionProfileVersionId = ExecutionProfileVersionId("profile-version:1"),
        executionProfileId = ExecutionProfileId("profile:1"),
        metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
        resistanceModel = ResistanceModel(
            modelVersion = "resistance-v1",
            semantics = ResistanceSemantics.EXTERNAL,
            bodyweightCoefficient = 0.0,
            externalLoadCoefficient = 1.0,
            assistanceCoefficient = 0.0,
        ),
        entryBasis = EntryBasis.TOTAL,
        lateralityMode = LateralityMode.BILATERAL_ONLY,
    )

    private fun nonDynamicProfile(family: MetricFamily) = NonDynamicProfileSemantics(
        executionProfileVersionId = ExecutionProfileVersionId("profile-version:1"),
        executionProfileId = ExecutionProfileId("profile:1"),
        metricFamily = family,
        resistanceModel = ResistanceModel(
            modelVersion = "resistance-v1",
            semantics = ResistanceSemantics.EXTERNAL,
            bodyweightCoefficient = 0.0,
            externalLoadCoefficient = 1.0,
            assistanceCoefficient = 0.0,
        ),
        entryBasis = EntryBasis.TOTAL,
        lateralityMode = LateralityMode.BILATERAL_ONLY,
    )

    private fun resolvedEquipment(observations: Set<String>) = CapabilityEquipmentContext.ResolvedSingleContext(
        equipmentId = EquipmentId("equipment:a"),
        interpretationVersion = "n-bio-7f-local-external-mass-v1",
        contributingObservationIds = observations,
        equipmentFactVersionIds = setOf("fact:1"),
    )

    private fun dynamicFit(
        inferenceHorizon: Instant = Instant.parse("2026-01-11T00:00:00Z"),
        evidenceAt: Instant = Instant.parse("2026-01-10T00:00:00Z"),
    ): DynamicTrendFrontierFit {
        val support = support(MetricFamily.DYNAMIC_RESISTANCE, evidenceAt)
        val configId = ModelConfigId("model-config:dynamic")
        val posterior = PosteriorEstimate(
            summary = PosteriorSummary(70.0, 80.0, 90.0, 25.0),
            support = support,
            provenance = ModelOutputProvenance(
                modelConfigId = configId,
                modelManifestId = ModelManifestId("manifest:dynamic"),
                inferenceRunId = InferenceRunId("run:dynamic"),
                evidenceThrough = evidenceAt,
            ),
        )
        val math = InferenceMathematicalModelIdentity("dynamic_profile_local_frontier", "test-v1", "test joint trend model")
        val solver = InferenceSolverIdentity(
            solverFamily = InferenceSolverFamily.ADAPTIVE_SPARSE_TENSOR,
            semanticVersion = "test-sparse-v1",
            computeBackend = InferenceComputeBackend.KOTLIN_JVM,
            deterministicReplay = true,
            approximationDefinition = "test sparse nodes",
        )
        return DynamicTrendFrontierFit(
            executionProfileVersionId = ExecutionProfileVersionId("profile-version:1"),
            side = Laterality.BILATERAL,
            inferenceHorizon = inferenceHorizon,
            referenceRepetitions = 8.0,
            modelConfigId = configId,
            modelVersion = "dynamic-trend-test-v1",
            evidencePolicyIdentity = "dynamic-evidence-policy-test-v1",
            support = support,
            observedRepMin = 5,
            observedRepMax = 12,
            observedResistanceMinKg = 50.0,
            observedResistanceMaxKg = 100.0,
            frontierAtLatestSession = posterior,
            slope = dynamicParameter(0.10, 0.20, 0.30, "slope"),
            frontierTrend = dynamicParameter(-0.02, 0.0, 0.02, "trend"),
            slackScale = dynamicParameter(0.05, 0.10, 0.15, "slack"),
            noiseScale = dynamicParameter(0.02, 0.05, 0.08, "noise"),
            observationSlack = listOf(
                DynamicObservationSlackPosterior(
                    observationId = "observation:1",
                    summary = PosteriorSummary(0.0, 0.05, 0.10, 0.001),
                    identification = DynamicParameterIdentification.PRIOR_DOMINATED,
                    massPoints = listOf(DynamicSlackPosteriorMass(0.05, 1.0)),
                ),
            ),
            selectedObservationIds = listOf("observation:1"),
            selectedSessionIds = listOf("session:1"),
            approximationVersion = "test-approx-v1",
            laplaceValidBasePosteriorMass = null,
            laplaceFiniteDifferenceStep = null,
            posteriorEffectiveNodeCount = 1.0,
            warnings = setOf("test-source"),
            posteriorNodes = listOf(
                DynamicTrendFrontierPosteriorNode(
                    logFrontierAtLatestSession = ln(80.0),
                    slope = 0.20,
                    frontierTrend = 0.0,
                    slackScale = 0.10,
                    noiseScale = 0.05,
                    posteriorWeight = 1.0,
                ),
            ),
            mathematicalModelIdentity = math,
            solverDiagnostics = InferenceSolverDiagnostics(
                solverIdentity = solver,
                posteriorRepresentation = InferencePosteriorRepresentation.WEIGHTED_SPARSE_NODES,
                evaluatedNodeCount = 1,
                effectiveNodeCount = 1.0,
            ),
        )
    }

    private fun loadedHoldFit(): NonDynamicCapabilityFit {
        val evidenceAt = Instant.parse("2026-01-10T00:00:00Z")
        val support = support(MetricFamily.LOADED_HOLD, evidenceAt)
        val configId = ModelConfigId("model-config:hold")
        val posterior = PosteriorEstimate(
            summary = PosteriorSummary(60.0, 70.0, 80.0, 16.0),
            support = support,
            provenance = ModelOutputProvenance(
                modelConfigId = configId,
                modelManifestId = ModelManifestId("manifest:hold"),
                inferenceRunId = InferenceRunId("run:hold"),
                evidenceThrough = evidenceAt,
            ),
        )
        val math = InferenceMathematicalModelIdentity("loaded_hold_profile_local_dynamic_frontier", "test-v1", "test hold model")
        val solver = InferenceSolverIdentity(
            solverFamily = InferenceSolverFamily.ADAPTIVE_SPARSE_TENSOR,
            semanticVersion = "test-hold-sparse-v1",
            computeBackend = InferenceComputeBackend.KOTLIN_JVM,
            deterministicReplay = true,
            approximationDefinition = "test hold sparse nodes",
        )
        return NonDynamicCapabilityFit(
            executionProfileVersionId = ExecutionProfileVersionId("profile-version:1"),
            side = Laterality.BILATERAL,
            family = MetricFamily.LOADED_HOLD,
            inferenceHorizon = Instant.parse("2026-01-11T00:00:00Z"),
            referenceCoordinate = 30.0,
            canonicalUnit = UnitId.KILOGRAM,
            modelConfigId = configId,
            mathematicalModelIdentity = math,
            solverDiagnostics = InferenceSolverDiagnostics(
                solverIdentity = solver,
                posteriorRepresentation = InferencePosteriorRepresentation.WEIGHTED_SPARSE_NODES,
                evaluatedNodeCount = 1,
                effectiveNodeCount = 1.0,
            ),
            evidencePolicyIdentity = "hold-evidence-policy-test-v1",
            support = support,
            observedInputMin = 20.0,
            observedInputMax = 40.0,
            observedOutputMin = 50.0,
            observedOutputMax = 80.0,
            frontierAtReference = posterior,
            slope = NonDynamicParameterPosterior(
                summary = PosteriorSummary(0.4, 0.5, 0.6, 0.01),
                identification = DynamicParameterIdentification.DATA_INFORMED,
                semanticUnit = "slope",
            ),
            trajectory = NonDynamicParameterPosterior(
                summary = PosteriorSummary(-0.01, 0.0, 0.01, 0.0001),
                identification = DynamicParameterIdentification.PARTIALLY_LEARNED,
                semanticUnit = "trajectory",
            ),
            slackScale = NonDynamicParameterPosterior(
                summary = PosteriorSummary(0.05, 0.10, 0.15, 0.001),
                identification = DynamicParameterIdentification.PRIOR_DOMINATED,
                semanticUnit = "slack",
            ),
            noiseScale = NonDynamicParameterPosterior(
                summary = PosteriorSummary(0.02, 0.05, 0.08, 0.001),
                identification = DynamicParameterIdentification.PRIOR_DOMINATED,
                semanticUnit = "noise",
            ),
            posteriorNodes = listOf(
                NonDynamicPosteriorNode(
                    logFrontierAtReference = ln(70.0),
                    slope = 0.5,
                    trajectory = 0.0,
                    slackScale = 0.10,
                    noiseScale = 0.05,
                    posteriorWeight = 1.0,
                ),
            ),
            selectedObservationIds = listOf("observation:1"),
            selectedSessionIds = listOf("session:1"),
            originalBaseNodeCount = 1,
            retainedBaseNodeCount = 1,
            warnings = setOf("test-source"),
        )
    }

    private fun dynamicParameter(
        p05: Double,
        p50: Double,
        p95: Double,
        unit: String,
    ) = DynamicFrontierParameterPosterior(
        summary = PosteriorSummary(p05, p50, p95, 0.001),
        identification = DynamicParameterIdentification.PARTIALLY_LEARNED,
        semanticUnit = unit,
    )

    private fun support(family: MetricFamily, evidenceAt: Instant) = EvidenceSupport(
        observationCount = 1,
        effectiveIndependentSessionCount = 1,
        firstEvidenceAt = evidenceAt,
        lastEvidenceAt = evidenceAt,
        evidenceFamily = EvidenceFamily.fromMetricFamily(family),
    )
}
