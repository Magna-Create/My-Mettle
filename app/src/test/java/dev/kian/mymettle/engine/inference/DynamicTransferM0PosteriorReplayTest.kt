package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.equipment.EquipmentCanonicalDependencyId
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
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
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

class DynamicTransferM0PosteriorReplayTest {
    @Test
    fun `section 11 posterior replay is deterministic and beta zero stays exact n0`() {
        val fixture = fixture()
        val fit = DynamicTransferM0PosteriorReplay.fit(fixture.replayInput)
        val replay = DynamicTransferM0PosteriorReplay.replayPosterior(fit)

        assertEquals(fit, replay)
        assertEquals(NBio7FM0V1.EXPECTED_MODEL_CONFIG_ID, fit.modelConfigId)
        assertEquals(2, fit.pairedTraining.size)
        assertEquals(
            fixture.destination.n0.destinationFit.posteriorNodes.size * NBio7FM0V1.betaQuadrature.size,
            fit.posteriorNodes.size,
        )
        assertEquals(1.0, fit.posteriorNodes.sumOf { it.posteriorWeight }, 1e-12)
        fit.posteriorNodes.filter { it.betaNode.beta == 0.0 }.forEach { zeroNode ->
            assertTrue(abs(zeroNode.logLikelihoodRatio) <= 1e-10)
        }
        assertTrue(
            fixture.replayInput.destinationReplayDependencyScope.sourceDependencyIds.all {
                it in fit.replayDependencyScope.sourceDependencyIds
            },
        )
        fit.pairedTraining.forEach { pair ->
            assertTrue(pair.preparedEdge.sourceCoreset.nodes.isNotEmpty())
            assertEquals(NBio7FM0V1.SOURCE_CORESET_IDENTITY, pair.preparedEdge.sourceCoreset.algorithmIdentity)
        }
    }

    @Test
    fun `unpaired destination observations add zero likelihood-ratio information`() {
        val fixture = fixture()
        val original = DynamicTransferM0PosteriorReplay.fit(fixture.replayInput)
        val first = fixture.replayInput.trainingSessions.first()
        assertTrue(first.pairing is DynamicTransferM0HistoricalPairing.Unpaired)
        val alteredUnpaired = first.copy(
            observations = first.observations.map { it.copy(resistanceKg = it.resistanceKg * 4.0) },
        )
        val altered = DynamicTransferM0PosteriorReplay.fit(
            fixture.replayInput.copy(
                trainingSessions = listOf(alteredUnpaired) + fixture.replayInput.trainingSessions.drop(1),
            ),
        )

        assertEquals(original.sourceCentre, altered.sourceCentre)
        assertEquals(original.pairedTraining, altered.pairedTraining)
        assertEquals(original.posteriorNodes, altered.posteriorNodes)
    }

    @Test
    fun `section 12 predictive mixture replays exactly and beta zero matches next-session n0 frontier`() {
        val fixture = fixture()
        val fit = DynamicTransferM0PosteriorReplay.fit(fixture.replayInput)
        val predictionInput = DynamicTransferM0PredictionReplayInput(
            predictionCutoff = BASE.plusSeconds(8 * DAY_SECONDS),
            source = fixture.currentSource,
            sourceLoadAccounting = stableLoad(fixture.currentSource.selectedObservationIds.toSet()),
            relationship = fit.relationship,
            queryRepetitions = 8.0,
            sourceReplayDependencyScope = dependencies(fixture.currentSource.selectedObservationIds),
        )
        val prediction = DynamicTransferM0PosteriorReplay.predict(fit, predictionInput)
        val replay = DynamicTransferM0PosteriorReplay.replayPrediction(prediction)

        assertEquals(prediction, replay)
        assertEquals(1.0, prediction.components.sumOf { it.mixtureWeight }, 1e-12)
        assertEquals(
            fit.posteriorNodes.size * prediction.sourceCoreset.nodes.size,
            prediction.components.size,
        )
        prediction.components.filter { it.beta == 0.0 }.forEach { component ->
            val destinationNode = fit.posteriorNodes.first {
                it.destinationNodeIndex == component.destinationNodeIndex && it.betaNodeIndex == component.betaNodeIndex
            }.destinationNode
            val n0 = DynamicTransferM0Kernel.n0LogFrontier(
                destinationNode = destinationNode,
                destinationReferenceRepetitions = fixture.destination.n0.destinationFit.referenceRepetitions,
                queryRepetitions = predictionInput.queryRepetitions,
                destinationSessionOffset = 1.0,
            )
            assertEquals(n0, component.logFrontier, 1e-15)
        }
        assertTrue(
            predictionInput.sourceReplayDependencyScope.sourceDependencyIds.all {
                it in prediction.replayDependencyScope.sourceDependencyIds
            },
        )
    }

    @Test
    fun `posterior requires complete destination replay history and prediction rejects future source snapshot`() {
        val fixture = fixture()
        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0PosteriorReplay.fit(
                fixture.replayInput.copy(trainingSessions = fixture.replayInput.trainingSessions.drop(1)),
            )
        }

        val fit = DynamicTransferM0PosteriorReplay.fit(fixture.replayInput)
        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0PosteriorReplay.predict(
                fit = fit,
                input = DynamicTransferM0PredictionReplayInput(
                    predictionCutoff = fixture.currentSource.causalCutoff.asOf.minusSeconds(1),
                    source = fixture.currentSource,
                    sourceLoadAccounting = stableLoad(fixture.currentSource.selectedObservationIds.toSet()),
                    relationship = fit.relationship,
                    queryRepetitions = 8.0,
                    sourceReplayDependencyScope = dependencies(fixture.currentSource.selectedObservationIds),
                ),
            )
        }
    }

    private fun fixture(): Fixture {
        val destinationEvidence = generated(
            profile = DESTINATION_PROFILE,
            prefix = "destination",
            sessionDays = listOf(2, 4, 6),
            baseFrontierKg = 78.0,
            trendPerSession = 0.03,
        )
        val rawDestinationN0 = champion.fit(
            destinationProjection = projection(DESTINATION_PROFILE, destinationEvidence),
            inferenceHorizon = BASE.plusSeconds(7 * DAY_SECONDS),
            configCreatedAt = CONFIG_CREATED_AT,
        )
        val destinationN0 = DynamicTransferN0Fit(trimPosterior(rawDestinationN0.destinationFit))
        val destination = DynamicTransferM0DestinationContext(
            n0 = destinationN0,
            profile = CapabilitySourceProfileSemantics.from(DESTINATION_PROFILE),
            equipmentContext = CapabilityEquipmentContext.ResolvedSingleContext(
                equipmentId = DESTINATION_EQUIPMENT,
                interpretationVersion = "destination-local-v1",
                contributingObservationIds = destinationN0.destinationFit.selectedObservationIds.toSet(),
                equipmentFactVersionIds = setOf("destination-fact-v1"),
            ),
            loadAccounting = stableLoad(destinationN0.destinationFit.selectedObservationIds.toSet()),
        )

        val sourceEvidence = generated(
            profile = SOURCE_PROFILE,
            prefix = "source",
            sessionDays = listOf(1, 3),
            baseFrontierKg = 66.0,
            trendPerSession = 0.15,
        )
        val sourceOne = sourceBoundary(
            evidence = sourceEvidence.filter { it.sessionId == "source-session-0" },
            horizon = BASE.plusSeconds(4 * DAY_SECONDS - 1),
        )
        val sourceTwo = sourceBoundary(
            evidence = sourceEvidence,
            horizon = BASE.plusSeconds(6 * DAY_SECONDS - 1),
        )
        val relationship = relationship(sourceOne, destination)
        assertEquals(relationship, relationship(sourceTwo, destination))

        val destinationBySession = destinationEvidence.groupBy { it.sessionId }
        val sessions = listOf(
            trainingSession(
                evidence = destinationBySession.getValue("destination-session-0"),
                offset = -2,
                pairing = DynamicTransferM0HistoricalPairing.Unpaired("no admissible prior source snapshot"),
            ),
            trainingSession(
                evidence = destinationBySession.getValue("destination-session-1"),
                offset = -1,
                pairing = paired(sourceOne, relationship),
            ),
            trainingSession(
                evidence = destinationBySession.getValue("destination-session-2"),
                offset = 0,
                pairing = paired(sourceTwo, relationship),
            ),
        )
        val replayInput = DynamicTransferM0PosteriorReplayInput(
            destination = destination,
            trainingSessions = sessions,
            destinationReplayDependencyScope = dependencies(destinationN0.destinationFit.selectedObservationIds),
        )
        return Fixture(
            destination = destination,
            replayInput = replayInput,
            currentSource = sourceTwo,
        )
    }

    private fun paired(
        source: CapabilityTransferSource,
        relationship: DirectedDynamicTransferRelationshipDescriptor,
    ) = DynamicTransferM0HistoricalPairing.Paired(
        source = source,
        sourceLoadAccounting = stableLoad(source.selectedObservationIds.toSet()),
        relationship = relationship,
        replayDependencyScope = dependencies(source.selectedObservationIds),
    )

    private fun trainingSession(
        evidence: List<DynamicResistanceEvidence>,
        offset: Int,
        pairing: DynamicTransferM0HistoricalPairing,
    ): DynamicTransferM0TrainingSession {
        val ordered = evidence.sortedBy { it.completedAt }
        return DynamicTransferM0TrainingSession(
            sessionId = ordered.first().sessionId,
            firstObservationTime = ordered.first().completedAt,
            destinationSessionOffset = offset,
            observations = ordered.map {
                DynamicTransferM0ObservedDestinationPoint(
                    observationId = it.observationId,
                    repetitions = it.repetitions.toDouble(),
                    resistanceKg = it.resistance.value,
                )
            },
            pairing = pairing,
        )
    }

    private fun sourceBoundary(
        evidence: List<DynamicResistanceEvidence>,
        horizon: Instant,
    ): CapabilityTransferSource {
        val fit = trimPosterior(
            champion.fit(
                destinationProjection = projection(SOURCE_PROFILE, evidence),
                inferenceHorizon = horizon,
                configCreatedAt = CONFIG_CREATED_AT,
            ).destinationFit,
        )
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

    private fun trimPosterior(fit: DynamicTrendFrontierFit): DynamicTrendFrontierFit {
        val selected = fit.posteriorNodes.sortedByDescending { it.posteriorWeight }.take(3)
        val total = selected.sumOf { it.posteriorWeight }
        return fit.copy(
            posteriorNodes = selected.map { it.copy(posteriorWeight = it.posteriorWeight / total) },
            posteriorEffectiveNodeCount = minOf(3.0, fit.posteriorEffectiveNodeCount),
        )
    }

    private fun relationship(
        source: CapabilityTransferSource,
        destination: DynamicTransferM0DestinationContext,
    ): DirectedDynamicTransferRelationshipDescriptor {
        val sourceEquipment = source.equipmentContext as CapabilityEquipmentContext.ResolvedSingleContext
        val destinationEquipment = destination.equipmentContext as CapabilityEquipmentContext.ResolvedSingleContext
        return DirectedDynamicTransferRelationshipDescriptor(
            relationshipId = "posterior-replay-source-to-destination",
            version = 1,
            policyIdentity = "fixture-explicit-directed-v1",
            sourceExecutionProfileId = source.profile.executionProfileId,
            sourceExecutionProfileVersionId = source.profile.executionProfileVersionId,
            destinationExecutionProfileId = destination.profile.executionProfileId,
            destinationExecutionProfileVersionId = destination.profile.executionProfileVersionId,
            side = Laterality.BILATERAL,
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

    private fun dependencies(observationIds: List<String>) = DynamicTransferM0ReplayDependencyScope(
        observationIds.map { EquipmentCanonicalDependencyId.observationLoadSemantics(it) }.toSet(),
    )

    private fun generated(
        profile: DynamicResistanceProfileSemantics,
        prefix: String,
        sessionDays: List<Int>,
        baseFrontierKg: Double,
        trendPerSession: Double,
    ): List<DynamicResistanceEvidence> = buildList {
        sessionDays.forEachIndexed { session, day ->
            listOf(6, 8, 12).forEachIndexed { ordinal, reps ->
                val logFrontierAtEight = ln(baseFrontierKg) + trendPerSession * session
                val resistance = exp(logFrontierAtEight - 0.17 * ln(reps / 8.0) - 0.03)
                add(set(profile, "$prefix-${session}_$ordinal", "$prefix-session-$session", reps, resistance, day, ordinal))
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
                resistanceModelVersion = "m0-replay-test-resistance-v1",
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

    private data class Fixture(
        val destination: DynamicTransferM0DestinationContext,
        val replayInput: DynamicTransferM0PosteriorReplayInput,
        val currentSource: CapabilityTransferSource,
    )

    companion object {
        private const val DAY_SECONDS = 86_400L
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val CONFIG_CREATED_AT = Instant.parse("2026-09-06T00:00:00Z")
        private val SOURCE_EQUIPMENT = EquipmentId("m0-replay-source-equipment")
        private val DESTINATION_EQUIPMENT = EquipmentId("m0-replay-destination-equipment")
        private val champion = DynamicTransferN0Champion()
        private val SOURCE_PROFILE = profile("m0-replay-source-profile", "m0-replay-source-version")
        private val DESTINATION_PROFILE = profile("m0-replay-destination-profile", "m0-replay-destination-version")

        private fun profile(profileId: String, versionId: String) = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId(versionId),
            executionProfileId = ExecutionProfileId(profileId),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            resistanceModel = ResistanceModel(
                modelVersion = "m0-replay-test-resistance-v1",
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
