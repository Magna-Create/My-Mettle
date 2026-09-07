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
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicTransferM0PrequentialScoringTest {
    @Test
    fun `frozen edge scores whole session and outcome changes cannot move predictive quantiles`() {
        val fixture = fixture()
        val fit = DynamicTransferM0PosteriorReplay.fit(fixture.replayInput)
        val frozen = freeze(fixture, fit)
        val observations = heldOutObservations(multiplier = 1.0)

        val scored = DynamicTransferM0PrequentialScorer.scoreSession(frozen, observations)

        assertEquals(NBio7FPrequentialScoringV1.PROTOCOL_ID, scored.protocolId)
        assertEquals(NBio7FPrequentialScoringV1.DISTRIBUTION_PROJECTION_ID, scored.distributionProjectionId)
        assertEquals(HELD_OUT_SESSION, scored.targetSessionId)
        assertEquals(3, scored.comparisons.size)
        assertEquals(3, scored.n0Aggregate?.count)
        assertEquals(3, scored.m0Aggregate?.count)
        val diagnostics = assertNotNull(scored.negativeTransferDiagnostics)
        assertEquals(3, diagnostics.comparableObservationCount)

        scored.comparisons.forEach { comparison ->
            val n0 = assertIs<DynamicTransferPredictiveScoreResult.Available>(comparison.n0).score
            val m0 = assertIs<DynamicTransferPredictiveScoreResult.Available>(comparison.m0).score
            val delta = assertNotNull(comparison.deltaM0MinusN0)
            assertEquals(m0.negativeLogScore - n0.negativeLogScore, delta.negativeLogScore, 1e-12)
            assertEquals(m0.crpsLogResistance - n0.crpsLogResistance, delta.crpsLogResistance, 1e-12)
            assertEquals(
                m0.weightedIntervalScoreLogResistance - n0.weightedIntervalScoreLogResistance,
                delta.weightedIntervalScoreLogResistance,
                1e-12,
            )
        }
        assertEquals(
            diagnostics.meanDeltaM0MinusN0.negativeLogScore > 0.0,
            diagnostics.harmfulByNegativeLogScore,
        )
        assertEquals(diagnostics.meanDeltaM0MinusN0.crpsLogResistance > 0.0, diagnostics.harmfulByCrps)
        assertEquals(
            diagnostics.meanDeltaM0MinusN0.weightedIntervalScoreLogResistance > 0.0,
            diagnostics.harmfulByWis,
        )

        val changedOutcome = DynamicTransferM0PrequentialScorer.scoreSession(
            frozen,
            heldOutObservations(multiplier = 1.20),
        )
        scored.comparisons.zip(changedOutcome.comparisons).forEach { (before, after) ->
            val beforeN0 = assertIs<DynamicTransferPredictiveScoreResult.Available>(before.n0).score
            val afterN0 = assertIs<DynamicTransferPredictiveScoreResult.Available>(after.n0).score
            val beforeM0 = assertIs<DynamicTransferPredictiveScoreResult.Available>(before.m0).score
            val afterM0 = assertIs<DynamicTransferPredictiveScoreResult.Available>(after.m0).score
            assertEquals(beforeN0.p05ResistanceKg, afterN0.p05ResistanceKg, 0.0)
            assertEquals(beforeN0.p50ResistanceKg, afterN0.p50ResistanceKg, 0.0)
            assertEquals(beforeN0.p95ResistanceKg, afterN0.p95ResistanceKg, 0.0)
            assertEquals(beforeM0.p05ResistanceKg, afterM0.p05ResistanceKg, 0.0)
            assertEquals(beforeM0.p50ResistanceKg, afterM0.p50ResistanceKg, 0.0)
            assertEquals(beforeM0.p95ResistanceKg, afterM0.p95ResistanceKg, 0.0)
            assertNotEquals(beforeN0.logPredictiveDensity, afterN0.logPredictiveDensity)
            assertNotEquals(beforeM0.logPredictiveDensity, afterM0.logPredictiveDensity)
        }
    }

    @Test
    fun `freeze is strictly pre outcome leakage fails and m0 domain refusal preserves n0`() {
        val fixture = fixture()
        val fit = DynamicTransferM0PosteriorReplay.fit(fixture.replayInput)
        val target = destinationSession(fixture)
        val policy = policy()
        val candidate = candidate(fixture, fit)
        val decision = DynamicTransferM0SourceSelector.decide(target, policy, listOf(candidate))

        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0PrequentialScorer.freeze(
                destinationSession = target,
                m0Fit = fit,
                sourceSelectionDecision = decision,
                edgeKey = candidate.edgeKey,
                frozenAt = target.firstObservationTime,
            )
        }

        val frozen = DynamicTransferM0PrequentialScorer.freeze(
            destinationSession = target,
            m0Fit = fit,
            sourceSelectionDecision = decision,
            edgeKey = candidate.edgeKey,
            frozenAt = FREEZE_AT,
        )
        val leakedId = fixture.destination.n0.destinationFit.selectedObservationIds.first()
        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0PrequentialScorer.scoreSession(
                frozen,
                listOf(
                    DynamicTransferM0HeldOutObservation(
                        observationId = leakedId,
                        sessionId = HELD_OUT_SESSION,
                        completedAt = HELD_OUT_AT,
                        repetitions = 8.0,
                        resistanceKg = 80.0,
                    ),
                ),
            )
        }

        val outside = DynamicTransferM0PrequentialScorer.scoreSession(
            frozen,
            listOf(
                DynamicTransferM0HeldOutObservation(
                    observationId = "held-out-outside-domain",
                    sessionId = HELD_OUT_SESSION,
                    completedAt = HELD_OUT_AT,
                    repetitions = 20.0,
                    resistanceKg = 70.0,
                ),
            ),
        )
        assertIs<DynamicTransferPredictiveScoreResult.Available>(outside.comparisons.single().n0)
        val unavailable = assertIs<DynamicTransferPredictiveScoreResult.Unavailable>(outside.comparisons.single().m0)
        assertEquals(
            DynamicTransferPredictiveUnavailableReason.OUTSIDE_M0_DESTINATION_REPETITION_DOMAIN,
            unavailable.reason,
        )
        assertNull(outside.comparisons.single().deltaM0MinusN0)
        assertNull(outside.m0Aggregate)
        assertNull(outside.negativeTransferDiagnostics)
    }

    private fun freeze(
        fixture: Fixture,
        fit: DynamicTransferM0PosteriorFit,
    ): DynamicTransferM0PrequentialFreeze {
        val target = destinationSession(fixture)
        val policy = policy()
        val candidate = candidate(fixture, fit)
        val decision = DynamicTransferM0SourceSelector.decide(target, policy, listOf(candidate))
        return DynamicTransferM0PrequentialScorer.freeze(
            destinationSession = target,
            m0Fit = fit,
            sourceSelectionDecision = decision,
            edgeKey = candidate.edgeKey,
            frozenAt = FREEZE_AT,
        )
    }

    private fun destinationSession(fixture: Fixture) = DynamicTransferM0DestinationSessionDescriptor(
        sessionId = HELD_OUT_SESSION,
        firstObservationTime = HELD_OUT_AT,
        destination = fixture.destination,
    )

    private fun policy() = DynamicTransferM0SourceSelectionPolicies.scoreIndependently(
        policyId = "prequential-test-independent",
        version = 1,
        frozenAt = POLICY_FROZEN_AT,
    )

    private fun candidate(
        fixture: Fixture,
        fit: DynamicTransferM0PosteriorFit,
    ) = DynamicTransferM0SourceCandidateInput(
        source = fixture.currentSource,
        sourceLoadAccounting = stableLoad(fixture.currentSource.selectedObservationIds.toSet()),
        relationship = fit.relationship,
        replayDependencyScope = dependencies(fixture.currentSource.selectedObservationIds),
    )

    private fun heldOutObservations(multiplier: Double): List<DynamicTransferM0HeldOutObservation> =
        listOf(6, 8, 12).mapIndexed { index, reps ->
            val logFrontierAtEight = ln(78.0) + 0.03 * 3.0
            val resistance = exp(logFrontierAtEight - 0.17 * ln(reps / 8.0) - 0.03) * multiplier
            DynamicTransferM0HeldOutObservation(
                observationId = "held-out-$index",
                sessionId = HELD_OUT_SESSION,
                completedAt = HELD_OUT_AT.plusSeconds(index.toLong()),
                repetitions = reps.toDouble(),
                resistanceKg = resistance,
            )
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
                destinationBySession.getValue("destination-session-0"),
                -2,
                DynamicTransferM0HistoricalPairing.Unpaired("no prior source"),
            ),
            trainingSession(destinationBySession.getValue("destination-session-1"), -1, paired(sourceOne, relationship)),
            trainingSession(destinationBySession.getValue("destination-session-2"), 0, paired(sourceTwo, relationship)),
        )
        return Fixture(
            destination = destination,
            replayInput = DynamicTransferM0PosteriorReplayInput(
                destination = destination,
                trainingSessions = sessions,
                destinationReplayDependencyScope = dependencies(destinationN0.destinationFit.selectedObservationIds),
            ),
            currentSource = sourceTwo,
        )
    }

    private fun paired(source: CapabilityTransferSource, relationship: DirectedDynamicTransferRelationshipDescriptor) =
        DynamicTransferM0HistoricalPairing.Paired(
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

    private fun sourceBoundary(evidence: List<DynamicResistanceEvidence>, horizon: Instant): CapabilityTransferSource {
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
            relationshipId = "prequential-source-to-destination",
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

    private fun projection(profile: DynamicResistanceProfileSemantics, evidence: List<DynamicResistanceEvidence>) =
        DynamicResistanceEvidenceProjection(
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
                resistanceModelVersion = "m0-prequential-test-resistance-v1",
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
        private const val HELD_OUT_SESSION = "destination-heldout-session"
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val CONFIG_CREATED_AT = Instant.parse("2026-09-06T00:00:00Z")
        private val POLICY_FROZEN_AT = BASE.plusSeconds(7 * DAY_SECONDS)
        private val FREEZE_AT = POLICY_FROZEN_AT.plusSeconds(600)
        private val HELD_OUT_AT = BASE.plusSeconds(8 * DAY_SECONDS)
        private val SOURCE_EQUIPMENT = EquipmentId("m0-prequential-source-equipment")
        private val DESTINATION_EQUIPMENT = EquipmentId("m0-prequential-destination-equipment")
        private val champion = DynamicTransferN0Champion()
        private val SOURCE_PROFILE = profile("m0-prequential-source-profile", "m0-prequential-source-version")
        private val DESTINATION_PROFILE = profile("m0-prequential-destination-profile", "m0-prequential-destination-version")

        private fun profile(profileId: String, versionId: String) = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId(versionId),
            executionProfileId = ExecutionProfileId(profileId),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            resistanceModel = ResistanceModel(
                modelVersion = "m0-prequential-test-resistance-v1",
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
