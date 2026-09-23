package dev.kian.mymettle.developer

import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.inference.CapabilityEquipmentContext
import dev.kian.mymettle.domain.inference.CapabilitySourceProfileSemantics
import dev.kian.mymettle.domain.inference.CapabilityTransferSource
import dev.kian.mymettle.domain.inference.CapabilityTransferSourceFactory
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.inference.DirectedDynamicTransferRelationshipDescriptor
import dev.kian.mymettle.engine.inference.DynamicHistoricalAvailabilityV3
import dev.kian.mymettle.engine.inference.DynamicTransferContinuousAggregate
import dev.kian.mymettle.engine.inference.DynamicTransferM0DestinationContext
import dev.kian.mymettle.engine.inference.DynamicTransferM0DestinationSessionDescriptor
import dev.kian.mymettle.engine.inference.DynamicTransferM0DirectedEdgeKey
import dev.kian.mymettle.engine.inference.DynamicTransferM0HistoricalPairing
import dev.kian.mymettle.engine.inference.DynamicTransferM0Kernel
import dev.kian.mymettle.engine.inference.DynamicTransferM0LoadAccountingContext
import dev.kian.mymettle.engine.inference.DynamicTransferM0ObservedDestinationPoint
import dev.kian.mymettle.engine.inference.DynamicTransferM0PosteriorReplay
import dev.kian.mymettle.engine.inference.DynamicTransferM0PosteriorReplayInput
import dev.kian.mymettle.engine.inference.DynamicTransferM0PrequentialAggregator
import dev.kian.mymettle.engine.inference.DynamicTransferM0PrequentialScorer
import dev.kian.mymettle.engine.inference.DynamicTransferMetricDeltaDiagnostics
import dev.kian.mymettle.engine.inference.DynamicTransferPairedDeltaSummary
import dev.kian.mymettle.engine.inference.DynamicTransferPitReliabilitySummary
import dev.kian.mymettle.engine.inference.DynamicTransferM0PrequentialSessionScore
import dev.kian.mymettle.engine.inference.DynamicTransferPredictiveScoreResult
import dev.kian.mymettle.engine.inference.DynamicTransferPredictiveUnavailableReason
import dev.kian.mymettle.engine.inference.DynamicTransferM0ReplayDependencyScope
import dev.kian.mymettle.engine.inference.DynamicTransferM0SourceCandidateAssessment
import dev.kian.mymettle.engine.inference.DynamicTransferM0SourceCandidateInput
import dev.kian.mymettle.engine.inference.DynamicTransferM0SourceSelectionDecision
import dev.kian.mymettle.engine.inference.DynamicTransferM0SourceSelectionPolicies
import dev.kian.mymettle.engine.inference.DynamicTransferM0SourceSelector
import dev.kian.mymettle.engine.inference.DynamicTransferM0TrainingSession
import dev.kian.mymettle.engine.inference.DynamicTransferN0Champion
import dev.kian.mymettle.engine.inference.HistoricalCompletedSetEvidenceRevision
import dev.kian.mymettle.engine.inference.HistoricalObservationRevisionSelector
import dev.kian.mymettle.engine.inference.NBio7FM0V1
import dev.kian.mymettle.engine.inference.NBio7FN0V1
import dev.kian.mymettle.engine.inference.NBio7FPrequentialAggregationV1
import dev.kian.mymettle.engine.inference.NBio7FPrequentialScoringV1
import dev.kian.mymettle.engine.performance.DynamicResistanceEvidenceProjector
import dev.kian.mymettle.inference.DynamicTrendCapabilityShadowRepository
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Developer-only installed-history evaluation for the frozen N-BIO-7F N0/M0 family.
 *
 * Each destination event reconstructs performance revision heads, equipment assertions/corrections
 * and equipment facts strictly as-of the historical freeze. Explicit relationship descriptors are
 * input: this evaluator never manufactures transfer edges from names, muscles or equipment family.
 */
class NBio7FInstalledHistoryEvaluator(
    private val database: MyMettleDatabase,
    private val relationships: List<DirectedDynamicTransferRelationshipDescriptor> = emptyList(),
) {
    private val n0Champion = DynamicTransferN0Champion()

    suspend fun run(
        onProgress: (NBio7BAcceptanceProgress) -> Unit = {},
    ): NBio7FInstalledHistoryEvaluationReport {
        val started = System.nanoTime()
        val heapBefore = usedHeapBytes()
        var peakHeap = heapBefore
        val inferenceDao = database.inferenceDao()
        val userProfileIds = inferenceDao.userProfileIds()
        require(userProfileIds.size == 1) {
            "N-BIO-7F installed-history evaluation requires exactly one Native user profile; found " +
                userProfileIds.size + "."
        }
        val userProfileId = userProfileIds.single()
        val rawFingerprintBefore = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionBefore = NBio7BPrescriptionStateFingerprinter.capture(database)
        val benchmarkRunIdBefore = inferenceDao.latestInferenceRun(userProfileId)?.id

        onProgress(NBio7BAcceptanceProgress(0, 3, "N-BIO-7F · reading causal installed history"))
        val dynamicHistory = NBio7BRawHistoryReader(
            database = database,
            historicalAvailabilityResolver = DynamicHistoricalAvailabilityV3::resolve,
        ).read()
        val nonDynamicHistory = NBio7CRawHistoryReader(database).read()
        val sessions = NBio7DHistoricalInputReader(database).read().sessions
        val equipmentHistory = NBio7FHistoricalEquipmentHistoryReader(database).read()
        val canonicalEquipmentCoverage = equipmentHistory.coverageAudit()
        peakHeap = maxOf(peakHeap, usedHeapBytes())

        val seeds = planDestinationSeeds(dynamicHistory.revisions, sessions)
        val edgeScores = linkedMapOf<DynamicTransferM0DirectedEdgeKey, MutableList<DynamicTransferM0PrequentialSessionScore>>()
        val events = mutableListOf<NBio7FInstalledDestinationEventAudit>()

        onProgress(NBio7BAcceptanceProgress(1, 3, "N-BIO-7F · reconstructing destination events"))
        seeds.forEachIndexed { index, seed ->
            events += evaluateDestinationEvent(seed, dynamicHistory, equipmentHistory, edgeScores)
            peakHeap = maxOf(peakHeap, usedHeapBytes())
            if (seeds.isNotEmpty() && (index + 1 == seeds.size || (index + 1) % 4 == 0)) {
                onProgress(
                    NBio7BAcceptanceProgress(
                        completedGroups = 1,
                        totalGroups = 3,
                        label = "N-BIO-7F · event " + (index + 1) + "/" + seeds.size,
                    ),
                )
            }
        }

        onProgress(NBio7BAcceptanceProgress(2, 3, "N-BIO-7F · aggregating exact directed edges"))
        val n0Diagnostics = NBio7FN0InstalledHistoryAggregator.fromEvents(events)
        val aggregates = edgeScores.entries
            .sortedBy { it.key.canonicalIdentity }
            .map { (edge, scores) ->
                NBio7FInstalledEdgeAggregate(
                    edgeIdentity = edge.canonicalIdentity,
                    aggregate = DynamicTransferM0PrequentialAggregator.aggregate(scores),
                )
            }

        val rawFingerprintAfter = NBio7BRawEvidenceFingerprinter.capture(database)
        val prescriptionAfter = NBio7BPrescriptionStateFingerprinter.capture(database)
        val benchmarkRunIdAfter = inferenceDao.latestInferenceRun(userProfileId)?.id
        val heapAfter = usedHeapBytes()
        peakHeap = maxOf(peakHeap, heapAfter)
        onProgress(NBio7BAcceptanceProgress(3, 3, "N-BIO-7F installed-history evaluation complete"))

        val roomVersion = database.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
        return NBio7FInstalledHistoryEvaluationReport(
            generatedAt = Instant.now(),
            roomSchemaVersion = roomVersion,
            historicalAvailabilityPolicy = DynamicHistoricalAvailabilityV3.POLICY_ID,
            n0MathematicalModelIdentity = NBio7FN0V1.mathematicalModelIdentity.identity,
            n0SolverIdentity = NBio7FN0V1.solverIdentity.identity,
            m0ModelConfigId = NBio7FM0V1.EXPECTED_MODEL_CONFIG_ID,
            m0MathematicalModelIdentity = NBio7FM0V1.mathematicalModelIdentity.identity,
            m0SolverIdentity = NBio7FM0V1.solverIdentity.identity,
            scoringProtocolId = NBio7FPrequentialScoringV1.PROTOCOL_ID,
            aggregationProtocolId = NBio7FPrequentialAggregationV1.PROTOCOL_ID,
            relationshipDescriptorCount = relationships.size,
            destinationEvents = events,
            edgeAggregates = aggregates,
            n0Diagnostics = n0Diagnostics,
            canonicalEquipmentCoverage = canonicalEquipmentCoverage,
            capabilityFamilyCoverage = (dynamicHistory.revisions + nonDynamicHistory.revisions)
                .groupingBy { it.evidence.metricFamily.storageValue }
                .eachCount()
                .toSortedMap(),
            n1RealHistoryStatus = "NOT_EVALUATED_REAL_HISTORY",
            m1RealHistoryStatus = "NOT_EVALUATED_REAL_HISTORY",
            m2RealHistoryStatus = "NOT_EVALUATED_REAL_HISTORY",
            rawFingerprintBefore = rawFingerprintBefore,
            rawFingerprintAfter = rawFingerprintAfter,
            prescriptionBefore = prescriptionBefore,
            prescriptionAfter = prescriptionAfter,
            benchmarkRunIdBefore = benchmarkRunIdBefore,
            benchmarkRunIdAfter = benchmarkRunIdAfter,
            runtimeMillis = elapsedMillis(started),
            heapUsedBeforeBytes = heapBefore,
            heapUsedAfterBytes = heapAfter,
            peakObservedHeapBytes = peakHeap,
        )
    }

    private fun evaluateDestinationEvent(
        seed: DestinationSeed,
        dynamicHistory: NBio7BRawHistory,
        equipmentHistory: NBio7FHistoricalEquipmentHistory,
        edgeScores: MutableMap<DynamicTransferM0DirectedEdgeKey, MutableList<DynamicTransferM0PrequentialSessionScore>>,
    ): NBio7FInstalledDestinationEventAudit {
        val started = System.nanoTime()
        val descriptor = dynamicHistory.profiles[seed.executionProfileVersionId]
            ?: return seed.unavailableEvent(
                status = NBio7FN0EventStatus.MISSING_PROFILE_SEMANTICS,
                detail = "No historical dynamic profile semantics exist for the destination version.",
                runtimeMillis = elapsedMillis(started),
            )

        val freezeAt = seed.firstObservationTime.minusNanos(1)
        val targetRaw = currentTargetEvidence(
            dynamicHistory = dynamicHistory,
            sessionId = seed.sessionId,
            outcomeKnowledgeAt = seed.outcomeKnowledgeAt,
        ).filter {
            it.executionProfileVersionId.value == seed.executionProfileVersionId && it.laterality == seed.side
        }
        val heldOutProjection = DynamicResistanceEvidenceProjector.project(
            profile = descriptor.semantics,
            side = seed.side,
            evidence = targetRaw,
            policy = NBio7FN0V1.evidencePolicy,
        )
        if (heldOutProjection.evidence.isEmpty()) {
            return seed.unavailableEvent(
                status = NBio7FN0EventStatus.NO_ELIGIBLE_HELD_OUT_EVIDENCE,
                detail = "No held-out evidence was knowable at the event outcome horizon after frozen projection.",
                heldOutRawCount = targetRaw.size,
                heldOutExclusions = heldOutProjection.exclusions.reasonCounts(),
                runtimeMillis = elapsedMillis(started),
            )
        }

        val priorRaw = currentPriorEvidence(
            dynamicHistory = dynamicHistory,
            cutoff = freezeAt,
            before = seed.firstObservationTime,
            excludedSessionId = seed.sessionId,
        ).filter {
            it.executionProfileVersionId.value == seed.executionProfileVersionId && it.laterality == seed.side
        }
        val destinationProjection = DynamicResistanceEvidenceProjector.project(
            profile = descriptor.semantics,
            side = seed.side,
            evidence = priorRaw,
            policy = NBio7FN0V1.evidencePolicy,
        )
        if (destinationProjection.evidence.isEmpty()) {
            return seed.unavailableEvent(
                status = NBio7FN0EventStatus.NO_PRIOR_DESTINATION_EVIDENCE,
                detail = "No eligible destination evidence was knowable before this held-out event.",
                heldOutRawCount = targetRaw.size,
                heldOutEligibleCount = heldOutProjection.evidence.size,
                heldOutExclusions = heldOutProjection.exclusions.reasonCounts(),
                destinationTrainingRawCount = priorRaw.size,
                destinationTrainingExclusions = destinationProjection.exclusions.reasonCounts(),
                runtimeMillis = elapsedMillis(started),
            )
        }

        val n0Started = System.nanoTime()
        val n0 = try {
            n0Champion.fit(
                destinationProjection = destinationProjection,
                inferenceHorizon = destinationProjection.evidence.maxOf { it.completedAt },
                configCreatedAt = DynamicTrendCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT,
            )
        } catch (failure: Exception) {
            return seed.unavailableEvent(
                status = NBio7FN0EventStatus.FIT_FAILURE,
                detail = fitFailureDetail("N0 fit failed", failure),
                heldOutRawCount = targetRaw.size,
                heldOutEligibleCount = heldOutProjection.evidence.size,
                heldOutExclusions = heldOutProjection.exclusions.reasonCounts(),
                destinationTrainingRawCount = priorRaw.size,
                destinationTrainingEligibleCount = destinationProjection.evidence.size,
                destinationTrainingIndependentSessions = destinationProjection.independentSessionCount,
                destinationTrainingExclusions = destinationProjection.exclusions.reasonCounts(),
                n0RuntimeMillis = elapsedMillis(n0Started),
                runtimeMillis = elapsedMillis(started),
            )
        }

        val destinationContext = causalCapabilityContext(
            selectedObservationIds = n0.destinationFit.selectedObservationIds,
            projectedEvidence = destinationProjection.evidence,
            knowledgeAt = freezeAt,
            equipmentHistory = equipmentHistory,
        )
        val destination = DynamicTransferM0DestinationContext(
            n0 = n0,
            profile = CapabilitySourceProfileSemantics.from(descriptor.semantics),
            equipmentContext = destinationContext.toCapabilityEquipmentContext(),
            loadAccounting = destinationContext.toM0LoadAccounting(),
        )
        val destinationSession = DynamicTransferM0DestinationSessionDescriptor(
            sessionId = seed.sessionId,
            firstObservationTime = seed.firstObservationTime,
            destination = destination,
        )
        val heldOutObservations = heldOutProjection.evidence.map {
            dev.kian.mymettle.engine.inference.DynamicTransferM0HeldOutObservation(
                observationId = it.observationId,
                sessionId = it.sessionId,
                completedAt = it.completedAt,
                repetitions = it.repetitions.toDouble(),
                resistanceKg = it.resistance.value,
            )
        }
        val n0Score = DynamicTransferM0PrequentialScorer.scoreN0Session(
            destinationSession = destinationSession,
            frozenAt = freezeAt,
            observations = heldOutObservations,
        )
        val n0RuntimeMillis = elapsedMillis(n0Started)
        val relevantRelationships = relationships
            .filter {
                it.destinationExecutionProfileId == descriptor.semantics.executionProfileId &&
                    it.destinationExecutionProfileVersionId == descriptor.semantics.executionProfileVersionId &&
                    it.side == seed.side
            }
            .sortedBy { DynamicTransferM0DirectedEdgeKey.from(it).canonicalIdentity }

        val relationshipAudits = mutableListOf<NBio7FM0RelationshipEventAudit>()
        if (relevantRelationships.isEmpty()) {
            relationshipAudits += NBio7FM0RelationshipEventAudit(
                edgeIdentity = null,
                status = NBio7FM0EventStatus.NO_EXPLICIT_RELATIONSHIP,
                detail = "No explicit versioned source-to-destination relationship was supplied for this event.",
            )
        } else {
            val candidateInputs = mutableListOf<DynamicTransferM0SourceCandidateInput>()
            val sourceAudits = mutableMapOf<DynamicTransferM0DirectedEdgeKey, NBio7FSourceSnapshotAudit>()
            relevantRelationships.forEach { relationship ->
                when (
                    val source = buildSourceSnapshot(
                        relationship = relationship,
                        dynamicHistory = dynamicHistory,
                        equipmentHistory = equipmentHistory,
                        before = seed.firstObservationTime,
                        cutoff = freezeAt,
                        excludedSessionId = seed.sessionId,
                    )
                ) {
                    is SourceSnapshotResult.Available -> {
                        val edgeKey = DynamicTransferM0DirectedEdgeKey.from(relationship)
                        sourceAudits[edgeKey] = source.audit
                        candidateInputs += DynamicTransferM0SourceCandidateInput(
                            source = source.source,
                            sourceLoadAccounting = source.loadAccounting,
                            relationship = relationship,
                            replayDependencyScope = source.dependencyScope,
                        )
                    }
                    is SourceSnapshotResult.Unavailable -> relationshipAudits += NBio7FM0RelationshipEventAudit(
                        edgeIdentity = DynamicTransferM0DirectedEdgeKey.from(relationship).canonicalIdentity,
                        relationship = relationship.toAuditDescriptor(),
                        sourceSnapshot = source.audit,
                        status = NBio7FM0EventStatus.SOURCE_SNAPSHOT_UNAVAILABLE,
                        detail = source.reason,
                        sourceFitRuntimeMillis = source.runtimeMillis,
                    )
                }
            }
            if (candidateInputs.isNotEmpty()) {
                val policy = DynamicTransferM0SourceSelectionPolicies.scoreIndependently(
                    policyId = SOURCE_SELECTION_POLICY_ID,
                    version = SOURCE_SELECTION_POLICY_VERSION,
                    frozenAt = freezeAt,
                )
                val decision = DynamicTransferM0SourceSelector.decide(destinationSession, policy, candidateInputs)
                decision.assessments.filterIsInstance<DynamicTransferM0SourceCandidateAssessment.Rejected>()
                    .forEach { rejected ->
                        relationshipAudits += NBio7FM0RelationshipEventAudit(
                            edgeIdentity = rejected.edgeKey.canonicalIdentity,
                            relationship = rejected.input.relationship.toAuditDescriptor(),
                            sourceSnapshot = sourceAudits[rejected.edgeKey],
                            status = NBio7FM0EventStatus.SOURCE_REJECTED,
                            detail = rejected.refusal.toString(),
                        )
                    }
                admissibleCandidates(decision).forEach { candidate ->
                    relationshipAudits += evaluateM0Edge(
                        seed = seed,
                        destination = destination,
                        destinationDependencyScope = destinationContext.replayDependencyScope(),
                        destinationSession = destinationSession,
                        destinationTrainingEvidence = destinationProjection.evidence,
                        heldOutObservations = heldOutObservations,
                        candidate = candidate,
                        sourceSnapshot = requireNotNull(sourceAudits[candidate.edgeKey]),
                        decision = decision,
                        dynamicHistory = dynamicHistory,
                        equipmentHistory = equipmentHistory,
                        edgeScores = edgeScores,
                    )
                }
            }
        }

        return NBio7FInstalledDestinationEventAudit(
            sessionId = seed.sessionId,
            destinationExecutionProfileVersionId = seed.executionProfileVersionId,
            destinationLabel = descriptor.label,
            side = seed.side.storageValue,
            firstObservationTime = seed.firstObservationTime,
            outcomeKnowledgeAt = seed.outcomeKnowledgeAt,
            frozenAt = freezeAt,
            heldOutRawCount = targetRaw.size,
            heldOutEligibleCount = heldOutProjection.evidence.size,
            heldOutExclusions = heldOutProjection.exclusions.reasonCounts(),
            destinationTrainingRawCount = priorRaw.size,
            destinationTrainingEligibleCount = destinationProjection.evidence.size,
            destinationTrainingIndependentSessions = destinationProjection.independentSessionCount,
            destinationTrainingExclusions = destinationProjection.exclusions.reasonCounts(),
            destinationTrainingRepMinimum = destinationProjection.repDomain?.first,
            destinationTrainingRepMaximum = destinationProjection.repDomain?.last,
            heldOutRepMinimum = heldOutProjection.repDomain?.first,
            heldOutRepMaximum = heldOutProjection.repDomain?.last,
            heldOutOutsideDestinationTrainingRepDomainCount = heldOutProjection.evidence.count {
                val domain = destinationProjection.repDomain
                domain != null && it.repetitions !in domain
            },
            n0Status = NBio7FN0EventStatus.AVAILABLE,
            n0Detail = null,
            n0SelectedObservationCount = n0.destinationFit.selectedObservationIds.size,
            n0SelectedIndependentSessionCount = n0.destinationFit.selectedSessionIds.size,
            n0ScoredObservationCount = n0Score.comparisons.count {
                it.n0 is DynamicTransferPredictiveScoreResult.Available
            },
            n0NumericalFailureCount = n0Score.comparisons.count {
                (it.n0 as? DynamicTransferPredictiveScoreResult.Unavailable)?.reason ==
                    DynamicTransferPredictiveUnavailableReason.NUMERICAL_FAILURE
            },
            n0Aggregate = n0Score.n0Aggregate,
            n0ScoredObservations = n0Score.comparisons.mapNotNull { comparison ->
                val score = (comparison.n0 as? DynamicTransferPredictiveScoreResult.Available)?.score
                    ?: return@mapNotNull null
                val domain = destinationProjection.repDomain
                NBio7FN0ScoredObservationAudit(
                    observationId = comparison.observation.observationId,
                    repetitions = comparison.observation.repetitions,
                    resistanceKg = comparison.observation.resistanceKg,
                    insideDestinationTrainingRepDomain = domain?.let {
                        comparison.observation.repetitions >= it.first.toDouble() &&
                            comparison.observation.repetitions <= it.last.toDouble()
                    },
                    score = score,
                )
            },
            destinationEquipmentStatus = destinationContext.equipment.statusString(),
            destinationLoadAccountingStatus = destinationContext.loadAccounting.statusString(),
            futureEquipmentCorrectionsExcluded = destinationContext.futureCorrectionsExcluded,
            futureEquipmentFactsExcluded = destinationContext.futureFactsExcluded,
            relationshipAudits = relationshipAudits.sortedBy { it.edgeIdentity ?: "" },
            n0RuntimeMillis = n0RuntimeMillis,
            runtimeMillis = elapsedMillis(started),
        )
    }

    private fun evaluateM0Edge(
        seed: DestinationSeed,
        destination: DynamicTransferM0DestinationContext,
        destinationDependencyScope: DynamicTransferM0ReplayDependencyScope,
        destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        destinationTrainingEvidence: List<DynamicResistanceEvidence>,
        heldOutObservations: List<dev.kian.mymettle.engine.inference.DynamicTransferM0HeldOutObservation>,
        candidate: DynamicTransferM0SourceCandidateAssessment.Admissible,
        sourceSnapshot: NBio7FSourceSnapshotAudit,
        decision: DynamicTransferM0SourceSelectionDecision,
        dynamicHistory: NBio7BRawHistory,
        equipmentHistory: NBio7FHistoricalEquipmentHistory,
        edgeScores: MutableMap<DynamicTransferM0DirectedEdgeKey, MutableList<DynamicTransferM0PrequentialSessionScore>>,
    ): NBio7FM0RelationshipEventAudit {
        val started = System.nanoTime()
        val trainingSessions = try {
            buildM0TrainingSessions(
                destination = destination,
                destinationTrainingEvidence = destinationTrainingEvidence,
                relationship = candidate.input.relationship,
                dynamicHistory = dynamicHistory,
                equipmentHistory = equipmentHistory,
            )
        } catch (failure: Exception) {
            return NBio7FM0RelationshipEventAudit(
                edgeIdentity = candidate.edgeKey.canonicalIdentity,
                relationship = candidate.input.relationship.toAuditDescriptor(),
                sourceSnapshot = sourceSnapshot,
                status = NBio7FM0EventStatus.TRAINING_RECONSTRUCTION_UNAVAILABLE,
                detail = failure.message ?: failure::class.simpleName ?: "M0 training reconstruction failed.",
                runtimeMillis = elapsedMillis(started),
            )
        }

        val fitStarted = System.nanoTime()
        val m0Fit = try {
            DynamicTransferM0PosteriorReplay.fit(
                DynamicTransferM0PosteriorReplayInput(
                    destination = destination,
                    trainingSessions = trainingSessions,
                    destinationReplayDependencyScope = destinationDependencyScope,
                ),
            )
        } catch (failure: Exception) {
            return NBio7FM0RelationshipEventAudit(
                edgeIdentity = candidate.edgeKey.canonicalIdentity,
                relationship = candidate.input.relationship.toAuditDescriptor(),
                sourceSnapshot = sourceSnapshot,
                status = NBio7FM0EventStatus.FIT_UNAVAILABLE,
                detail = failure.message ?: failure::class.simpleName ?: "M0 fit unavailable.",
                pairedTrainingSessionCount = trainingSessions.count {
                    it.pairing is DynamicTransferM0HistoricalPairing.Paired
                },
                unpairedTrainingSessionCount = trainingSessions.count {
                    it.pairing is DynamicTransferM0HistoricalPairing.Unpaired
                },
                fitRuntimeMillis = elapsedMillis(fitStarted),
                runtimeMillis = elapsedMillis(started),
            )
        }

        val scoringStarted = System.nanoTime()
        return try {
            val frozen = DynamicTransferM0PrequentialScorer.freeze(
                destinationSession = destinationSession,
                m0Fit = m0Fit,
                sourceSelectionDecision = decision,
                edgeKey = candidate.edgeKey,
                frozenAt = seed.firstObservationTime.minusNanos(1),
            )
            val score = DynamicTransferM0PrequentialScorer.scoreSession(
                frozen = frozen,
                observations = heldOutObservations,
            )
            edgeScores.getOrPut(candidate.edgeKey, ::mutableListOf) += score
            NBio7FM0RelationshipEventAudit(
                edgeIdentity = candidate.edgeKey.canonicalIdentity,
                relationship = candidate.input.relationship.toAuditDescriptor(),
                sourceSnapshot = sourceSnapshot,
                status = NBio7FM0EventStatus.SCORED,
                detail = null,
                sourceSelectedObservationCount = candidate.input.source.selectedObservationIds.size,
                sourceSelectedIndependentSessionCount = candidate.input.source.selectedSessionIds.size,
                pairedTrainingSessionCount = m0Fit.pairedTraining.size,
                unpairedTrainingSessionCount = trainingSessions.size - m0Fit.pairedTraining.size,
                comparableHeldOutObservationCount = score.comparisons.count { it.deltaM0MinusN0 != null },
                fitRuntimeMillis = elapsedMillis(fitStarted),
                scoringRuntimeMillis = elapsedMillis(scoringStarted),
                runtimeMillis = elapsedMillis(started),
            )
        } catch (failure: Exception) {
            NBio7FM0RelationshipEventAudit(
                edgeIdentity = candidate.edgeKey.canonicalIdentity,
                relationship = candidate.input.relationship.toAuditDescriptor(),
                sourceSnapshot = sourceSnapshot,
                status = NBio7FM0EventStatus.SCORING_FAILURE,
                detail = failure.message ?: failure::class.simpleName ?: "M0 scoring failed.",
                sourceSelectedObservationCount = candidate.input.source.selectedObservationIds.size,
                sourceSelectedIndependentSessionCount = candidate.input.source.selectedSessionIds.size,
                pairedTrainingSessionCount = m0Fit.pairedTraining.size,
                unpairedTrainingSessionCount = trainingSessions.size - m0Fit.pairedTraining.size,
                fitRuntimeMillis = elapsedMillis(fitStarted),
                scoringRuntimeMillis = elapsedMillis(scoringStarted),
                runtimeMillis = elapsedMillis(started),
            )
        }
    }

    private fun buildM0TrainingSessions(
        destination: DynamicTransferM0DestinationContext,
        destinationTrainingEvidence: List<DynamicResistanceEvidence>,
        relationship: DirectedDynamicTransferRelationshipDescriptor,
        dynamicHistory: NBio7BRawHistory,
        equipmentHistory: NBio7FHistoricalEquipmentHistory,
    ): List<DynamicTransferM0TrainingSession> {
        val destinationFit = destination.n0.destinationFit
        val selectedIds = destinationFit.selectedObservationIds.toSet()
        val selectedEvidence = destinationTrainingEvidence.filter { it.observationId in selectedIds }
        require(selectedEvidence.map { it.observationId }.toSet() == selectedIds) {
            "M0 training reconstruction lost selected destination observations."
        }
        val grouped = selectedEvidence.groupBy { it.sessionId }
        require(grouped.keys == destinationFit.selectedSessionIds.toSet()) {
            "M0 training reconstruction lost selected destination sessions."
        }
        val chronological = grouped.entries.sortedWith(
            compareBy<Map.Entry<String, List<DynamicResistanceEvidence>>> {
                it.value.minOf { point -> point.completedAt }
            }.thenBy { it.key },
        )
        return chronological.mapIndexed { index, entry ->
            val sessionId = entry.key
            val evidence = entry.value
            val firstObservationTime = evidence.minOf { it.completedAt }
            val source = buildSourceSnapshot(
                relationship = relationship,
                dynamicHistory = dynamicHistory,
                equipmentHistory = equipmentHistory,
                before = firstObservationTime,
                cutoff = firstObservationTime.minusNanos(1),
                excludedSessionId = sessionId,
            )
            val pairing = when (source) {
                is SourceSnapshotResult.Available -> {
                    try {
                        DynamicTransferM0Kernel.prepareDirectedEdge(
                            source = source.source,
                            sourceLoadAccounting = source.loadAccounting,
                            destination = destination,
                            relationship = relationship,
                        )
                        DynamicTransferM0HistoricalPairing.Paired(
                            source = source.source,
                            sourceLoadAccounting = source.loadAccounting,
                            relationship = relationship,
                            replayDependencyScope = source.dependencyScope,
                        )
                    } catch (failure: Exception) {
                        DynamicTransferM0HistoricalPairing.Unpaired(
                            "historical_source_inadmissible:" + (failure::class.simpleName ?: "Exception"),
                        )
                    }
                }
                is SourceSnapshotResult.Unavailable -> DynamicTransferM0HistoricalPairing.Unpaired(source.reason)
            }
            DynamicTransferM0TrainingSession(
                sessionId = sessionId,
                firstObservationTime = firstObservationTime,
                destinationSessionOffset = index - chronological.lastIndex,
                observations = evidence
                    .sortedWith(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId })
                    .map {
                        DynamicTransferM0ObservedDestinationPoint(
                            observationId = it.observationId,
                            repetitions = it.repetitions.toDouble(),
                            resistanceKg = it.resistance.value,
                        )
                    },
                pairing = pairing,
            )
        }
    }

    private fun buildSourceSnapshot(
        relationship: DirectedDynamicTransferRelationshipDescriptor,
        dynamicHistory: NBio7BRawHistory,
        equipmentHistory: NBio7FHistoricalEquipmentHistory,
        before: Instant,
        cutoff: Instant,
        excludedSessionId: String,
    ): SourceSnapshotResult {
        val started = System.nanoTime()
        val descriptor = dynamicHistory.profiles[relationship.sourceExecutionProfileVersionId.value]
            ?: return sourceUnavailable(
                startedNanos = started,
                status = "MISSING_PROFILE_SEMANTICS",
                detail = "missing_source_profile_semantics",
            )
        if (descriptor.semantics.executionProfileId != relationship.sourceExecutionProfileId) {
            return sourceUnavailable(
                startedNanos = started,
                status = "PROFILE_ID_VERSION_MISMATCH",
                detail = "source_profile_id_version_mismatch",
            )
        }

        val raw = currentPriorEvidence(dynamicHistory, cutoff, before, excludedSessionId).filter {
            it.executionProfileVersionId == relationship.sourceExecutionProfileVersionId &&
                it.laterality == relationship.side
        }
        val projection = DynamicResistanceEvidenceProjector.project(
            profile = descriptor.semantics,
            side = relationship.side,
            evidence = raw,
            policy = NBio7FN0V1.evidencePolicy,
        )
        if (projection.evidence.isEmpty()) {
            return sourceUnavailable(
                startedNanos = started,
                status = "NO_ELIGIBLE_SOURCE_EVIDENCE",
                detail = "no_eligible_source_evidence",
                rawEvidenceCount = raw.size,
                eligibleEvidenceCount = 0,
                independentSessionCount = 0,
                exclusions = projection.exclusions.reasonCounts(),
                observedRepDomain = projection.repDomain,
            )
        }

        val n0 = try {
            n0Champion.fit(
                destinationProjection = projection,
                inferenceHorizon = projection.evidence.maxOf { it.completedAt },
                configCreatedAt = DynamicTrendCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT,
            )
        } catch (failure: Exception) {
            return sourceUnavailable(
                startedNanos = started,
                status = "FIT_FAILURE",
                detail = fitFailureDetail("source_fit_failure", failure),
                rawEvidenceCount = raw.size,
                eligibleEvidenceCount = projection.evidence.size,
                independentSessionCount = projection.independentSessionCount,
                exclusions = projection.exclusions.reasonCounts(),
                observedRepDomain = projection.repDomain,
            )
        }

        val context = try {
            causalCapabilityContext(
                selectedObservationIds = n0.destinationFit.selectedObservationIds,
                projectedEvidence = projection.evidence,
                knowledgeAt = cutoff,
                equipmentHistory = equipmentHistory,
            )
        } catch (failure: Exception) {
            return sourceUnavailable(
                startedNanos = started,
                status = "CONTEXT_RECONSTRUCTION_FAILURE",
                detail = "source_context_failure:" + (failure::class.simpleName ?: "Exception"),
                rawEvidenceCount = raw.size,
                eligibleEvidenceCount = projection.evidence.size,
                independentSessionCount = projection.independentSessionCount,
                exclusions = projection.exclusions.reasonCounts(),
                observedRepDomain = projection.repDomain,
                selectedObservationCount = n0.destinationFit.selectedObservationIds.size,
                selectedIndependentSessionCount = n0.destinationFit.selectedSessionIds.size,
            )
        }

        val source = try {
            CapabilityTransferSourceFactory.fromDynamicTrendFit(
                profile = descriptor.semantics,
                fit = n0.destinationFit,
                equipmentContext = context.toCapabilityEquipmentContext(),
            )
        } catch (failure: Exception) {
            return sourceUnavailable(
                startedNanos = started,
                status = "TRANSFER_BOUNDARY_FAILURE",
                detail = "source_transfer_boundary_failure:" + (failure::class.simpleName ?: "Exception"),
                rawEvidenceCount = raw.size,
                eligibleEvidenceCount = projection.evidence.size,
                independentSessionCount = projection.independentSessionCount,
                exclusions = projection.exclusions.reasonCounts(),
                observedRepDomain = projection.repDomain,
                selectedObservationCount = n0.destinationFit.selectedObservationIds.size,
                selectedIndependentSessionCount = n0.destinationFit.selectedSessionIds.size,
                equipmentStatus = context.equipment.statusString(),
                loadAccountingStatus = context.loadAccounting.statusString(),
                futureCorrectionsExcluded = context.futureCorrectionsExcluded,
                futureFactsExcluded = context.futureFactsExcluded,
            )
        }

        val runtimeMillis = elapsedMillis(started)
        return SourceSnapshotResult.Available(
            source = source,
            loadAccounting = context.toM0LoadAccounting(),
            dependencyScope = context.replayDependencyScope(),
            runtimeMillis = runtimeMillis,
            audit = NBio7FSourceSnapshotAudit(
                status = "AVAILABLE",
                detail = null,
                rawEvidenceCount = raw.size,
                eligibleEvidenceCount = projection.evidence.size,
                independentSessionCount = projection.independentSessionCount,
                exclusions = projection.exclusions.reasonCounts(),
                observedRepMinimum = projection.repDomain?.first,
                observedRepMaximum = projection.repDomain?.last,
                selectedObservationCount = n0.destinationFit.selectedObservationIds.size,
                selectedIndependentSessionCount = n0.destinationFit.selectedSessionIds.size,
                equipmentStatus = context.equipment.statusString(),
                loadAccountingStatus = context.loadAccounting.statusString(),
                futureEquipmentCorrectionsExcluded = context.futureCorrectionsExcluded,
                futureEquipmentFactsExcluded = context.futureFactsExcluded,
                runtimeMillis = runtimeMillis,
            ),
        )
    }

    private fun sourceUnavailable(
        startedNanos: Long,
        status: String,
        detail: String,
        rawEvidenceCount: Int = 0,
        eligibleEvidenceCount: Int = 0,
        independentSessionCount: Int = 0,
        exclusions: Map<String, Int> = emptyMap(),
        observedRepDomain: IntRange? = null,
        selectedObservationCount: Int = 0,
        selectedIndependentSessionCount: Int = 0,
        equipmentStatus: String = "NOT_EVALUATED",
        loadAccountingStatus: String = "NOT_EVALUATED",
        futureCorrectionsExcluded: Int = 0,
        futureFactsExcluded: Int = 0,
    ): SourceSnapshotResult.Unavailable {
        val runtimeMillis = elapsedMillis(startedNanos)
        return SourceSnapshotResult.Unavailable(
            reason = detail,
            runtimeMillis = runtimeMillis,
            audit = NBio7FSourceSnapshotAudit(
                status = status,
                detail = detail,
                rawEvidenceCount = rawEvidenceCount,
                eligibleEvidenceCount = eligibleEvidenceCount,
                independentSessionCount = independentSessionCount,
                exclusions = exclusions,
                observedRepMinimum = observedRepDomain?.first,
                observedRepMaximum = observedRepDomain?.last,
                selectedObservationCount = selectedObservationCount,
                selectedIndependentSessionCount = selectedIndependentSessionCount,
                equipmentStatus = equipmentStatus,
                loadAccountingStatus = loadAccountingStatus,
                futureEquipmentCorrectionsExcluded = futureCorrectionsExcluded,
                futureEquipmentFactsExcluded = futureFactsExcluded,
                runtimeMillis = runtimeMillis,
            ),
        )
    }

    private fun causalCapabilityContext(
        selectedObservationIds: List<String>,
        projectedEvidence: List<DynamicResistanceEvidence>,
        knowledgeAt: Instant,
        equipmentHistory: NBio7FHistoricalEquipmentHistory,
    ): NBio7FHistoricalCapabilityContext {
        val byId = projectedEvidence.associateBy { it.observationId }
        val observations = selectedObservationIds.map { observationId ->
            val evidence = requireNotNull(byId[observationId]) {
                "Selected capability observation is absent from its causal projection: " + observationId
            }
            NBio7FCausalEquipmentHistory.resolveObservation(
                history = equipmentHistory,
                observationId = observationId,
                observationAt = evidence.completedAt,
                knowledgeAt = knowledgeAt,
            )
        }
        return NBio7FCausalEquipmentHistory.summariseCapability(observations)
    }

    private fun currentPriorEvidence(
        dynamicHistory: NBio7BRawHistory,
        cutoff: Instant,
        before: Instant,
        excludedSessionId: String,
    ): List<CompletedSetEvidence> {
        val priorSessionIds = dynamicHistory.revisions.asSequence()
            .filter {
                it.evidence.sessionId != excludedSessionId &&
                    it.sessionCompletedAt.isBefore(before)
            }
            .mapNotNull { it.evidence.sessionId }
            .toSet()
        return HistoricalObservationRevisionSelector.currentAsOf(
            revisions = dynamicHistory.revisions.filter { it.evidence.sessionId in priorSessionIds },
            cutoff = cutoff,
        )
    }

    private fun currentTargetEvidence(
        dynamicHistory: NBio7BRawHistory,
        sessionId: String,
        outcomeKnowledgeAt: Instant,
    ): List<CompletedSetEvidence> = HistoricalObservationRevisionSelector.currentAsOf(
        revisions = dynamicHistory.revisions.filter { it.evidence.sessionId == sessionId },
        cutoff = outcomeKnowledgeAt,
    )

    private fun NBio7FHistoricalCapabilityContext.toCapabilityEquipmentContext(): CapabilityEquipmentContext =
        when (val value = equipment) {
            is NBio7FHistoricalCapabilityEquipmentContext.Stable -> CapabilityEquipmentContext.ResolvedSingleContext(
                equipmentId = value.equipmentId,
                interpretationVersion = value.interpretationVersion,
                contributingObservationIds = value.contributingObservationIds,
                equipmentFactVersionIds = value.equipmentFactVersionIds,
            )
            is NBio7FHistoricalCapabilityEquipmentContext.Mixed -> CapabilityEquipmentContext.Unresolved(
                "mixed_historical_equipment:" + value.equipmentIds.map { it.value }.sorted().joinToString(","),
            )
            is NBio7FHistoricalCapabilityEquipmentContext.Unresolved -> CapabilityEquipmentContext.Unresolved(
                "unresolved_historical_equipment:" + value.reasons.map { it.name }.sorted().joinToString(","),
            )
        }

    private fun NBio7FHistoricalCapabilityContext.toM0LoadAccounting(): DynamicTransferM0LoadAccountingContext =
        when (val value = loadAccounting) {
            is NBio7FHistoricalCapabilityLoadAccounting.StableKnown -> DynamicTransferM0LoadAccountingContext.StableKnown(
                accounting = value.accounting,
                contributingObservationIds = value.contributingObservationIds,
            )
            is NBio7FHistoricalCapabilityLoadAccounting.Mixed -> DynamicTransferM0LoadAccountingContext.Mixed(
                contributingObservationIds = value.contributingObservationIds,
                reason = "mixed_historical_load_accounting:" +
                    value.accountingValues.map { it.storageValue }.sorted().joinToString(","),
            )
            is NBio7FHistoricalCapabilityLoadAccounting.Unknown -> DynamicTransferM0LoadAccountingContext.Unknown(
                "unknown_historical_load_accounting:" + value.reasons.map { it.name }.sorted().joinToString(","),
            )
        }

    private fun NBio7FHistoricalCapabilityContext.replayDependencyScope(): DynamicTransferM0ReplayDependencyScope {
        val ids = linkedSetOf<String>()
        when (val value = equipment) {
            is NBio7FHistoricalCapabilityEquipmentContext.Stable -> ids += value.dependencyIds
            is NBio7FHistoricalCapabilityEquipmentContext.Mixed -> ids += value.dependencyIds
            is NBio7FHistoricalCapabilityEquipmentContext.Unresolved -> ids += value.dependencyIds
        }
        when (val value = loadAccounting) {
            is NBio7FHistoricalCapabilityLoadAccounting.StableKnown -> ids += value.dependencyIds
            is NBio7FHistoricalCapabilityLoadAccounting.Mixed -> ids += value.dependencyIds
            is NBio7FHistoricalCapabilityLoadAccounting.Unknown -> ids += value.dependencyIds
        }
        return DynamicTransferM0ReplayDependencyScope(ids)
    }

    private fun admissibleCandidates(
        decision: DynamicTransferM0SourceSelectionDecision,
    ): List<DynamicTransferM0SourceCandidateAssessment.Admissible> = when (decision) {
        is DynamicTransferM0SourceSelectionDecision.IndependentCandidates -> decision.candidates
        is DynamicTransferM0SourceSelectionDecision.SelectedSingle -> listOf(decision.selected)
        is DynamicTransferM0SourceSelectionDecision.NoAdmissibleSource -> emptyList()
    }

    private sealed interface SourceSnapshotResult {
        data class Available(
            val source: CapabilityTransferSource,
            val loadAccounting: DynamicTransferM0LoadAccountingContext,
            val dependencyScope: DynamicTransferM0ReplayDependencyScope,
            val runtimeMillis: Long,
            val audit: NBio7FSourceSnapshotAudit,
        ) : SourceSnapshotResult

        data class Unavailable(
            val reason: String,
            val runtimeMillis: Long,
            val audit: NBio7FSourceSnapshotAudit,
        ) : SourceSnapshotResult
    }

    internal data class DestinationSeed(
        val sessionId: String,
        val executionProfileVersionId: String,
        val side: Laterality,
        val firstObservationTime: Instant,
        val outcomeKnowledgeAt: Instant,
    )

    private fun DestinationSeed.unavailableEvent(
        status: NBio7FN0EventStatus,
        detail: String,
        heldOutRawCount: Int = 0,
        heldOutEligibleCount: Int = 0,
        heldOutExclusions: Map<String, Int> = emptyMap(),
        destinationTrainingRawCount: Int = 0,
        destinationTrainingEligibleCount: Int = 0,
        destinationTrainingIndependentSessions: Int = 0,
        destinationTrainingExclusions: Map<String, Int> = emptyMap(),
        destinationTrainingRepMinimum: Int? = null,
        destinationTrainingRepMaximum: Int? = null,
        heldOutRepMinimum: Int? = null,
        heldOutRepMaximum: Int? = null,
        heldOutOutsideDestinationTrainingRepDomainCount: Int = 0,
        n0RuntimeMillis: Long = 0L,
        runtimeMillis: Long,
    ) = NBio7FInstalledDestinationEventAudit(
        sessionId = sessionId,
        destinationExecutionProfileVersionId = executionProfileVersionId,
        destinationLabel = executionProfileVersionId,
        side = side.storageValue,
        firstObservationTime = firstObservationTime,
        outcomeKnowledgeAt = outcomeKnowledgeAt,
        frozenAt = firstObservationTime.minusNanos(1),
        heldOutRawCount = heldOutRawCount,
        heldOutEligibleCount = heldOutEligibleCount,
        heldOutExclusions = heldOutExclusions,
        destinationTrainingRawCount = destinationTrainingRawCount,
        destinationTrainingEligibleCount = destinationTrainingEligibleCount,
        destinationTrainingIndependentSessions = destinationTrainingIndependentSessions,
        destinationTrainingExclusions = destinationTrainingExclusions,
        destinationTrainingRepMinimum = destinationTrainingRepMinimum,
        destinationTrainingRepMaximum = destinationTrainingRepMaximum,
        heldOutRepMinimum = heldOutRepMinimum,
        heldOutRepMaximum = heldOutRepMaximum,
        heldOutOutsideDestinationTrainingRepDomainCount = heldOutOutsideDestinationTrainingRepDomainCount,
        n0Status = status,
        n0Detail = detail,
        n0SelectedObservationCount = 0,
        n0SelectedIndependentSessionCount = 0,
        n0ScoredObservationCount = 0,
        n0NumericalFailureCount = 0,
        n0Aggregate = null,
        n0ScoredObservations = emptyList(),
        destinationEquipmentStatus = "NOT_EVALUATED",
        destinationLoadAccountingStatus = "NOT_EVALUATED",
        futureEquipmentCorrectionsExcluded = 0,
        futureEquipmentFactsExcluded = 0,
        relationshipAudits = emptyList(),
        n0RuntimeMillis = n0RuntimeMillis,
        runtimeMillis = runtimeMillis,
    )

    private fun DirectedDynamicTransferRelationshipDescriptor.toAuditDescriptor() =
        NBio7FRelationshipDescriptorAudit(
            relationshipId = relationshipId,
            version = version,
            policyIdentity = policyIdentity,
            sourceExecutionProfileVersionId = sourceExecutionProfileVersionId.value,
            destinationExecutionProfileVersionId = destinationExecutionProfileVersionId.value,
            side = side.storageValue,
            sourceEquipmentId = sourceEquipmentId.value,
            sourceEquipmentInterpretationVersion = sourceEquipmentInterpretationVersion,
            sourceEquipmentFactVersionIds = sourceEquipmentFactVersionIds.sorted(),
            destinationEquipmentId = destinationEquipmentId.value,
            destinationEquipmentInterpretationVersion = destinationEquipmentInterpretationVersion,
            destinationEquipmentFactVersionIds = destinationEquipmentFactVersionIds.sorted(),
            sourceLoadAccounting = sourceLoadAccounting.storageValue,
            destinationLoadAccounting = destinationLoadAccounting.storageValue,
        )

    private fun NBio7FHistoricalCapabilityEquipmentContext.statusString(): String = when (this) {
        is NBio7FHistoricalCapabilityEquipmentContext.Stable -> "STABLE:" + equipmentId.value
        is NBio7FHistoricalCapabilityEquipmentContext.Mixed -> "MIXED:" + equipmentIds.size
        is NBio7FHistoricalCapabilityEquipmentContext.Unresolved ->
            "UNRESOLVED:" + reasons.map { it.name }.sorted().joinToString(",")
    }

    private fun NBio7FHistoricalCapabilityLoadAccounting.statusString(): String = when (this) {
        is NBio7FHistoricalCapabilityLoadAccounting.StableKnown -> "STABLE:" + accounting.storageValue
        is NBio7FHistoricalCapabilityLoadAccounting.Mixed ->
            "MIXED:" + accountingValues.map { it.storageValue }.sorted().joinToString(",")
        is NBio7FHistoricalCapabilityLoadAccounting.Unknown ->
            "UNKNOWN:" + reasons.map { it.name }.sorted().joinToString(",")
    }

    private fun List<dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceExclusion>.reasonCounts(): Map<String, Int> =
        groupingBy { it.reason.storageValue }.eachCount().toSortedMap()

    private fun fitFailureDetail(prefix: String, failure: Exception): String {
        val typedReason = (failure as? DynamicCapabilityFitException)?.reason?.storageValue
        val parts = listOfNotNull(
            failure::class.simpleName,
            typedReason,
            failure.message?.takeIf { it.isNotBlank() },
        )
        return prefix + ": " + parts.joinToString(" · ")
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun elapsedMillis(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000L

    companion object {
        internal fun planDestinationSeeds(
            revisions: List<HistoricalCompletedSetEvidenceRevision>,
            sessions: Map<String, NBio7DHistoricalSession>,
        ): List<DestinationSeed> = revisions
            .groupBy { requireNotNull(it.evidence.sessionId) }
            .entries
            .flatMap { entry ->
                val session = sessions[entry.key] ?: return@flatMap emptyList()
                val outcomeHeads = HistoricalObservationRevisionSelector.currentAsOf(
                    revisions = entry.value,
                    cutoff = session.completedAt,
                )
                outcomeHeads.groupBy { it.executionProfileVersionId.value to it.laterality }
                    .map { grouped ->
                        DestinationSeed(
                            sessionId = entry.key,
                            executionProfileVersionId = grouped.key.first,
                            side = grouped.key.second,
                            firstObservationTime = grouped.value.minOf { it.completedAt },
                            outcomeKnowledgeAt = session.completedAt,
                        )
                    }
            }
            .distinct()
            .sortedWith(
                compareBy<DestinationSeed> { it.firstObservationTime }
                    .thenBy { it.sessionId }
                    .thenBy { it.executionProfileVersionId }
                    .thenBy { it.side.storageValue },
            )

        private const val SOURCE_SELECTION_POLICY_ID = "n-bio-7f-installed-history-independent-explicit-edges"
        private const val SOURCE_SELECTION_POLICY_VERSION = 1
    }
}

enum class NBio7FN0EventStatus {
    AVAILABLE,
    MISSING_PROFILE_SEMANTICS,
    NO_ELIGIBLE_HELD_OUT_EVIDENCE,
    NO_PRIOR_DESTINATION_EVIDENCE,
    FIT_FAILURE,
}

enum class NBio7FM0EventStatus {
    SCORED,
    NO_EXPLICIT_RELATIONSHIP,
    SOURCE_SNAPSHOT_UNAVAILABLE,
    SOURCE_REJECTED,
    TRAINING_RECONSTRUCTION_UNAVAILABLE,
    FIT_UNAVAILABLE,
    SCORING_FAILURE,
}

data class NBio7FRelationshipDescriptorAudit(
    val relationshipId: String,
    val version: Int,
    val policyIdentity: String,
    val sourceExecutionProfileVersionId: String,
    val destinationExecutionProfileVersionId: String,
    val side: String,
    val sourceEquipmentId: String,
    val sourceEquipmentInterpretationVersion: String,
    val sourceEquipmentFactVersionIds: List<String>,
    val destinationEquipmentId: String,
    val destinationEquipmentInterpretationVersion: String,
    val destinationEquipmentFactVersionIds: List<String>,
    val sourceLoadAccounting: String,
    val destinationLoadAccounting: String,
)

data class NBio7FSourceSnapshotAudit(
    val status: String,
    val detail: String?,
    val rawEvidenceCount: Int,
    val eligibleEvidenceCount: Int,
    val independentSessionCount: Int,
    val exclusions: Map<String, Int>,
    val observedRepMinimum: Int?,
    val observedRepMaximum: Int?,
    val selectedObservationCount: Int,
    val selectedIndependentSessionCount: Int,
    val equipmentStatus: String,
    val loadAccountingStatus: String,
    val futureEquipmentCorrectionsExcluded: Int,
    val futureEquipmentFactsExcluded: Int,
    val runtimeMillis: Long,
)

data class NBio7FM0RelationshipEventAudit(
    val edgeIdentity: String?,
    val relationship: NBio7FRelationshipDescriptorAudit? = null,
    val sourceSnapshot: NBio7FSourceSnapshotAudit? = null,
    val status: NBio7FM0EventStatus,
    val detail: String?,
    val sourceSelectedObservationCount: Int = 0,
    val sourceSelectedIndependentSessionCount: Int = 0,
    val pairedTrainingSessionCount: Int = 0,
    val unpairedTrainingSessionCount: Int = 0,
    val comparableHeldOutObservationCount: Int = 0,
    val sourceFitRuntimeMillis: Long = 0L,
    val fitRuntimeMillis: Long = 0L,
    val scoringRuntimeMillis: Long = 0L,
    val runtimeMillis: Long = 0L,
)

data class NBio7FInstalledDestinationEventAudit(
    val sessionId: String,
    val destinationExecutionProfileVersionId: String,
    val destinationLabel: String,
    val side: String,
    val firstObservationTime: Instant,
    val outcomeKnowledgeAt: Instant,
    val frozenAt: Instant,
    val heldOutRawCount: Int,
    val heldOutEligibleCount: Int,
    val heldOutExclusions: Map<String, Int>,
    val destinationTrainingRawCount: Int,
    val destinationTrainingEligibleCount: Int,
    val destinationTrainingIndependentSessions: Int,
    val destinationTrainingExclusions: Map<String, Int>,
    val destinationTrainingRepMinimum: Int?,
    val destinationTrainingRepMaximum: Int?,
    val heldOutRepMinimum: Int?,
    val heldOutRepMaximum: Int?,
    val heldOutOutsideDestinationTrainingRepDomainCount: Int,
    val n0Status: NBio7FN0EventStatus,
    val n0Detail: String?,
    val n0SelectedObservationCount: Int,
    val n0SelectedIndependentSessionCount: Int,
    val n0ScoredObservationCount: Int,
    val n0NumericalFailureCount: Int,
    val n0Aggregate: DynamicTransferContinuousAggregate?,
    val n0ScoredObservations: List<NBio7FN0ScoredObservationAudit>,
    val destinationEquipmentStatus: String,
    val destinationLoadAccountingStatus: String,
    val futureEquipmentCorrectionsExcluded: Int,
    val futureEquipmentFactsExcluded: Int,
    val relationshipAudits: List<NBio7FM0RelationshipEventAudit>,
    val n0RuntimeMillis: Long,
    val runtimeMillis: Long,
)

data class NBio7FInstalledEdgeAggregate(
    val edgeIdentity: String,
    val aggregate: dev.kian.mymettle.engine.inference.DynamicTransferM0EdgePrequentialAggregate,
)

data class NBio7FInstalledHistoryEvaluationReport(
    val generatedAt: Instant,
    val roomSchemaVersion: Int,
    val historicalAvailabilityPolicy: String,
    val n0MathematicalModelIdentity: String,
    val n0SolverIdentity: String,
    val m0ModelConfigId: String,
    val m0MathematicalModelIdentity: String,
    val m0SolverIdentity: String,
    val scoringProtocolId: String,
    val aggregationProtocolId: String,
    val relationshipDescriptorCount: Int,
    val destinationEvents: List<NBio7FInstalledDestinationEventAudit>,
    val edgeAggregates: List<NBio7FInstalledEdgeAggregate>,
    val n0Diagnostics: NBio7FN0InstalledHistoryDiagnostics,
    val canonicalEquipmentCoverage: NBio7FCanonicalEquipmentCoverageAudit,
    val capabilityFamilyCoverage: Map<String, Int>,
    val n1RealHistoryStatus: String,
    val m1RealHistoryStatus: String,
    val m2RealHistoryStatus: String,
    val rawFingerprintBefore: NBio7BRawEvidenceFingerprint,
    val rawFingerprintAfter: NBio7BRawEvidenceFingerprint,
    val prescriptionBefore: NBio7BPrescriptionStateFingerprint,
    val prescriptionAfter: NBio7BPrescriptionStateFingerprint,
    val benchmarkRunIdBefore: String?,
    val benchmarkRunIdAfter: String?,
    val runtimeMillis: Long,
    val heapUsedBeforeBytes: Long,
    val heapUsedAfterBytes: Long,
    val peakObservedHeapBytes: Long,
) {
    val n0AvailableEventCount: Int
        get() = destinationEvents.count { it.n0Status == NBio7FN0EventStatus.AVAILABLE }

    val m0ScoredEventEdgeCount: Int
        get() = destinationEvents.sumOf { event ->
            event.relationshipAudits.count { it.status == NBio7FM0EventStatus.SCORED }
        }

    val n0ScoredObservationCount: Int
        get() = destinationEvents.sumOf { it.n0ScoredObservationCount }

    val n0NumericalFailureCount: Int
        get() = destinationEvents.sumOf { it.n0NumericalFailureCount }

    val n0StatusCounts: Map<String, Int>
        get() = destinationEvents.groupingBy { it.n0Status.name }.eachCount().toSortedMap()

    val n0AvailabilityRate: Double
        get() = if (destinationEvents.isEmpty()) 0.0 else n0AvailableEventCount.toDouble() / destinationEvents.size

    val n0DestinationContextResolvedForM0Count: Int
        get() = destinationEvents.count { event ->
            event.n0Status == NBio7FN0EventStatus.AVAILABLE &&
                event.destinationEquipmentStatus.startsWith("STABLE:") &&
                event.destinationLoadAccountingStatus.startsWith("STABLE:")
        }

    val m0AvailableDestinationEventCount: Int
        get() = destinationEvents.count { event ->
            event.relationshipAudits.any { it.status == NBio7FM0EventStatus.SCORED }
        }

    val m0DestinationAvailabilityRate: Double
        get() = if (destinationEvents.isEmpty()) 0.0 else {
            m0AvailableDestinationEventCount.toDouble() / destinationEvents.size
        }

    val m0StatusCounts: Map<String, Int>
        get() = destinationEvents
            .flatMap { it.relationshipAudits }
            .groupingBy { it.status.name }
            .eachCount()
            .toSortedMap()

    val destinationFutureCorrectionExclusionCount: Int
        get() = destinationEvents.sumOf { it.futureEquipmentCorrectionsExcluded }

    val destinationFutureFactExclusionCount: Int
        get() = destinationEvents.sumOf { it.futureEquipmentFactsExcluded }

    val sourceFutureCorrectionExclusionCount: Int
        get() = destinationEvents.sumOf { event ->
            event.relationshipAudits.sumOf { it.sourceSnapshot?.futureEquipmentCorrectionsExcluded ?: 0 }
        }

    val sourceFutureFactExclusionCount: Int
        get() = destinationEvents.sumOf { event ->
            event.relationshipAudits.sumOf { it.sourceSnapshot?.futureEquipmentFactsExcluded ?: 0 }
        }

    val causallyExcludedFutureCorrectionCount: Int
        get() = destinationFutureCorrectionExclusionCount + sourceFutureCorrectionExclusionCount

    val causallyExcludedFutureFactCount: Int
        get() = destinationFutureFactExclusionCount + sourceFutureFactExclusionCount

    val rawEvidenceUnchanged: Boolean
        get() = rawFingerprintBefore == rawFingerprintAfter

    val prescriptionStateUnchanged: Boolean
        get() = prescriptionBefore == prescriptionAfter

    val benchmarkAuthorityUnchanged: Boolean
        get() = benchmarkRunIdBefore == benchmarkRunIdAfter

    val integrityPassed: Boolean
        get() = rawEvidenceUnchanged && prescriptionStateUnchanged && benchmarkAuthorityUnchanged

    fun toJson(): String = JSONObject()
        .put("format", "my-mettle-n-bio-7f-installed-history-development-evaluation")
        .put("formatVersion", 2)
        .put("generatedAt", generatedAt.toString())
        .put("roomSchemaVersion", roomSchemaVersion)
        .put("historicalAvailabilityPolicy", historicalAvailabilityPolicy)
        .put("authority", "SHADOW_DEVELOPER_ONLY")
        .put("productAuthority", "BENCHMARK_V0_UNCHANGED")
        .put(
            "integrity",
            JSONObject()
                .put("rawEvidenceBeforeSha256", rawFingerprintBefore.sha256)
                .put("rawEvidenceAfterSha256", rawFingerprintAfter.sha256)
                .put("rawEvidenceUnchanged", rawEvidenceUnchanged)
                .put("rawEvidenceTableRowCounts", JSONObject(rawFingerprintAfter.tableRowCounts))
                .put("prescriptionBeforeSha256", prescriptionBefore.sha256)
                .put("prescriptionAfterSha256", prescriptionAfter.sha256)
                .put("prescriptionStateUnchanged", prescriptionStateUnchanged)
                .put("prescriptionTableRowCounts", JSONObject(prescriptionAfter.tableRowCounts))
                .put("benchmarkRunIdBefore", benchmarkRunIdBefore ?: JSONObject.NULL)
                .put("benchmarkRunIdAfter", benchmarkRunIdAfter ?: JSONObject.NULL)
                .put("benchmarkAuthorityUnchanged", benchmarkAuthorityUnchanged)
                .put("passed", integrityPassed),
        )
        .put(
            "n0",
            JSONObject()
                .put("mathematicalModelIdentity", n0MathematicalModelIdentity)
                .put("solverIdentity", n0SolverIdentity)
                .put("destinationEventCount", destinationEvents.size)
                .put("availableEventCount", n0AvailableEventCount)
                .put("availabilityRate", n0AvailabilityRate)
                .put("statusCounts", JSONObject(n0StatusCounts))
                .put("scoredObservationCount", n0ScoredObservationCount)
                .put("numericalFailureCount", n0NumericalFailureCount)
                .put("diagnostics", n0Diagnostics.toJson7f()),
        )
        .put(
            "m0",
            JSONObject()
                .put("modelConfigId", m0ModelConfigId)
                .put("mathematicalModelIdentity", m0MathematicalModelIdentity)
                .put("solverIdentity", m0SolverIdentity)
                .put("relationshipDescriptorCount", relationshipDescriptorCount)
                .put("availableDestinationEventCount", m0AvailableDestinationEventCount)
                .put("destinationAvailabilityRate", m0DestinationAvailabilityRate)
                .put("scoredEventEdgeCount", m0ScoredEventEdgeCount)
                .put("statusCounts", JSONObject(m0StatusCounts)),
        )
        .put(
            "evaluation",
            JSONObject()
                .put("scoringProtocolId", scoringProtocolId)
                .put("aggregationProtocolId", aggregationProtocolId)
                .put("destinationEventCount", destinationEvents.size)
                .put("futureCorrectionsExcluded", causallyExcludedFutureCorrectionCount)
                .put("futureFactsExcluded", causallyExcludedFutureFactCount)
                .put("destinationFutureCorrectionsExcluded", destinationFutureCorrectionExclusionCount)
                .put("destinationFutureFactsExcluded", destinationFutureFactExclusionCount)
                .put("sourceFutureCorrectionsExcluded", sourceFutureCorrectionExclusionCount)
                .put("sourceFutureFactsExcluded", sourceFutureFactExclusionCount)
                .put("canonicalEquipmentCoverage", canonicalEquipmentCoverage.toJson7f())
                .put("n0DestinationContextResolvedForM0Count", n0DestinationContextResolvedForM0Count)
                .put("capabilityFamilyCoverage", JSONObject(capabilityFamilyCoverage)),
        )
        .put(
            "deferredRealHistoryCandidates",
            JSONObject()
                .put("N1", n1RealHistoryStatus)
                .put("M1", m1RealHistoryStatus)
                .put("M2", m2RealHistoryStatus),
        )
        .put(
            "runtime",
            JSONObject()
                .put("runtimeMillis", runtimeMillis)
                .put("heapUsedBeforeBytes", heapUsedBeforeBytes)
                .put("heapUsedAfterBytes", heapUsedAfterBytes)
                .put("peakObservedHeapBytes", peakObservedHeapBytes),
        )
        .put("destinationEvents", JSONArray(destinationEvents.map { it.toJson() }))
        .put("edgeAggregates", JSONArray(edgeAggregates.map { it.toJson() }))
        .toString(2)
}

private fun NBio7FInstalledDestinationEventAudit.toJson(): JSONObject = JSONObject()
    .put("sessionId", sessionId)
    .put("destinationExecutionProfileVersionId", destinationExecutionProfileVersionId)
    .put("destinationLabel", destinationLabel)
    .put("side", side)
    .put("firstObservationTime", firstObservationTime.toString())
    .put("outcomeKnowledgeAt", outcomeKnowledgeAt.toString())
    .put("frozenAt", frozenAt.toString())
    .put("heldOutRawCount", heldOutRawCount)
    .put("heldOutEligibleCount", heldOutEligibleCount)
    .put("heldOutExclusions", JSONObject(heldOutExclusions))
    .put("destinationTrainingRawCount", destinationTrainingRawCount)
    .put("destinationTrainingEligibleCount", destinationTrainingEligibleCount)
    .put("destinationTrainingIndependentSessions", destinationTrainingIndependentSessions)
    .put("destinationTrainingExclusions", JSONObject(destinationTrainingExclusions))
    .put("destinationTrainingRepMinimum", destinationTrainingRepMinimum ?: JSONObject.NULL)
    .put("destinationTrainingRepMaximum", destinationTrainingRepMaximum ?: JSONObject.NULL)
    .put("heldOutRepMinimum", heldOutRepMinimum ?: JSONObject.NULL)
    .put("heldOutRepMaximum", heldOutRepMaximum ?: JSONObject.NULL)
    .put("heldOutOutsideDestinationTrainingRepDomainCount", heldOutOutsideDestinationTrainingRepDomainCount)
    .put("n0Status", n0Status.name)
    .put("n0Detail", n0Detail ?: JSONObject.NULL)
    .put("n0SelectedObservationCount", n0SelectedObservationCount)
    .put("n0SelectedIndependentSessionCount", n0SelectedIndependentSessionCount)
    .put("n0ScoredObservationCount", n0ScoredObservationCount)
    .put("n0NumericalFailureCount", n0NumericalFailureCount)
    .put("n0Aggregate", n0Aggregate?.toJson7f() ?: JSONObject.NULL)
    .put("n0ScoredObservations", JSONArray(n0ScoredObservations.map { it.toJson7f() }))
    .put("destinationEquipmentStatus", destinationEquipmentStatus)
    .put("destinationLoadAccountingStatus", destinationLoadAccountingStatus)
    .put("futureEquipmentCorrectionsExcluded", futureEquipmentCorrectionsExcluded)
    .put("futureEquipmentFactsExcluded", futureEquipmentFactsExcluded)
    .put("n0RuntimeMillis", n0RuntimeMillis)
    .put("runtimeMillis", runtimeMillis)
    .put("relationships", JSONArray(relationshipAudits.map { it.toJson() }))

private fun NBio7FM0RelationshipEventAudit.toJson(): JSONObject = JSONObject()
    .put("edgeIdentity", edgeIdentity ?: JSONObject.NULL)
    .put("relationship", relationship?.toJson7f() ?: JSONObject.NULL)
    .put("sourceSnapshot", sourceSnapshot?.toJson7f() ?: JSONObject.NULL)
    .put("status", status.name)
    .put("detail", detail ?: JSONObject.NULL)
    .put("sourceSelectedObservationCount", sourceSelectedObservationCount)
    .put("sourceSelectedIndependentSessionCount", sourceSelectedIndependentSessionCount)
    .put("pairedTrainingSessionCount", pairedTrainingSessionCount)
    .put("unpairedTrainingSessionCount", unpairedTrainingSessionCount)
    .put("comparableHeldOutObservationCount", comparableHeldOutObservationCount)
    .put("sourceFitRuntimeMillis", sourceFitRuntimeMillis)
    .put("fitRuntimeMillis", fitRuntimeMillis)
    .put("scoringRuntimeMillis", scoringRuntimeMillis)
    .put("runtimeMillis", runtimeMillis)

private fun NBio7FRelationshipDescriptorAudit.toJson7f(): JSONObject = JSONObject()
    .put("relationshipId", relationshipId)
    .put("version", version)
    .put("policyIdentity", policyIdentity)
    .put("sourceExecutionProfileVersionId", sourceExecutionProfileVersionId)
    .put("destinationExecutionProfileVersionId", destinationExecutionProfileVersionId)
    .put("side", side)
    .put("sourceEquipmentId", sourceEquipmentId)
    .put("sourceEquipmentInterpretationVersion", sourceEquipmentInterpretationVersion)
    .put("sourceEquipmentFactVersionIds", JSONArray(sourceEquipmentFactVersionIds))
    .put("destinationEquipmentId", destinationEquipmentId)
    .put("destinationEquipmentInterpretationVersion", destinationEquipmentInterpretationVersion)
    .put("destinationEquipmentFactVersionIds", JSONArray(destinationEquipmentFactVersionIds))
    .put("sourceLoadAccounting", sourceLoadAccounting)
    .put("destinationLoadAccounting", destinationLoadAccounting)

private fun NBio7FSourceSnapshotAudit.toJson7f(): JSONObject = JSONObject()
    .put("status", status)
    .put("detail", detail ?: JSONObject.NULL)
    .put("rawEvidenceCount", rawEvidenceCount)
    .put("eligibleEvidenceCount", eligibleEvidenceCount)
    .put("independentSessionCount", independentSessionCount)
    .put("exclusions", JSONObject(exclusions))
    .put("observedRepMinimum", observedRepMinimum ?: JSONObject.NULL)
    .put("observedRepMaximum", observedRepMaximum ?: JSONObject.NULL)
    .put("selectedObservationCount", selectedObservationCount)
    .put("selectedIndependentSessionCount", selectedIndependentSessionCount)
    .put("equipmentStatus", equipmentStatus)
    .put("loadAccountingStatus", loadAccountingStatus)
    .put("futureEquipmentCorrectionsExcluded", futureEquipmentCorrectionsExcluded)
    .put("futureEquipmentFactsExcluded", futureEquipmentFactsExcluded)
    .put("runtimeMillis", runtimeMillis)

private fun NBio7FN0ScoredObservationAudit.toJson7f(): JSONObject = JSONObject()
    .put("observationId", observationId)
    .put("repetitions", repetitions)
    .put("resistanceKg", resistanceKg)
    .put(
        "insideDestinationTrainingRepDomain",
        insideDestinationTrainingRepDomain ?: JSONObject.NULL,
    )
    .put(
        "score",
        JSONObject()
            .put("p05ResistanceKg", score.p05ResistanceKg)
            .put("p50ResistanceKg", score.p50ResistanceKg)
            .put("p95ResistanceKg", score.p95ResistanceKg)
            .put("pit", score.pit)
            .put("logPredictiveDensity", score.logPredictiveDensity)
            .put("negativeLogScore", score.negativeLogScore)
            .put("crpsLogResistance", score.crpsLogResistance)
            .put("weightedIntervalScoreLogResistance", score.weightedIntervalScoreLogResistance)
            .put("coverage90", score.coverage90)
            .put("intervalLogWidth", score.intervalLogWidth)
            .put("medianAbsoluteErrorKg", score.medianAbsoluteErrorKg)
            .put("signedLogResidual", score.signedLogResidual),
    )

private fun NBio7FN0DiagnosticGroup.toJson7f(): JSONObject = JSONObject()
    .put("eventCount", eventCount)
    .put("observationCount", observationCount)
    .put("aggregate", aggregate.toJson7f())
    .put("pitReliability", pitReliability.toJson7f())

private fun NBio7FN0InstalledHistoryDiagnostics.toJson7f(): JSONObject = JSONObject()
    .put("overall", overall?.toJson7f() ?: JSONObject.NULL)
    .put(
        "repetitionDomain",
        JSONObject()
            .put(
                "insideTrainingDomain",
                repetitionDomain.insideTrainingDomain?.toJson7f() ?: JSONObject.NULL,
            )
            .put(
                "outsideTrainingDomain",
                repetitionDomain.outsideTrainingDomain?.toJson7f() ?: JSONObject.NULL,
            )
            .put(
                "unknownTrainingDomainObservationCount",
                repetitionDomain.unknownTrainingDomainObservationCount,
            ),
    )
    .put(
        "historyDepthBySelectedIndependentSessions",
        JSONObject().apply {
            historyDepth.forEach { (bucket, group) -> put(bucket, group.toJson7f()) }
        },
    )

private fun NBio7FCanonicalEquipmentCoverageAudit.toJson7f(): JSONObject = JSONObject()
    .put("equipmentInstanceCount", equipmentInstanceCount)
    .put("equipmentFactVersionCount", equipmentFactVersionCount)
    .put("sessionActualEquipmentBindingCount", sessionActualEquipmentBindingCount)
    .put("observationEquipmentOverrideCount", observationEquipmentOverrideCount)
    .put("observationLoadSemanticsCount", observationLoadSemanticsCount)

private fun DynamicTransferContinuousAggregate.toJson7f(): JSONObject = JSONObject()
    .put("count", count)
    .put("meanNegativeLogScore", meanNegativeLogScore)
    .put("meanCrpsLogResistance", meanCrpsLogResistance)
    .put("meanWeightedIntervalScoreLogResistance", meanWeightedIntervalScoreLogResistance)
    .put("coverage90", coverage90)
    .put("meanIntervalLogWidth", meanIntervalLogWidth)
    .put("meanMedianAbsoluteErrorKg", meanMedianAbsoluteErrorKg)
    .put("meanSignedLogResidual", meanSignedLogResidual)

private fun DynamicTransferPitReliabilitySummary.toJson7f(): JSONObject = JSONObject()
    .put("count", count)
    .put("lowThirdCount", lowThirdCount)
    .put("middleThirdCount", middleThirdCount)
    .put("highThirdCount", highThirdCount)
    .put("lowThirdRate", lowThirdRate)
    .put("middleThirdRate", middleThirdRate)
    .put("highThirdRate", highThirdRate)
    .put("meanPit", meanPit)
    .put("meanAbsoluteThreeBinDeviation", meanAbsoluteThreeBinDeviation)

private fun DynamicTransferPairedDeltaSummary.toJson7f(): JSONObject = JSONObject()
    .put("count", count)
    .put("cumulativeDelta", cumulativeDelta)
    .put("meanDelta", meanDelta)
    .put("medianDelta", medianDelta)
    .put("upperTailP95Delta", upperTailP95Delta)
    .put("maximumDelta", maximumDelta)
    .put("positiveCount", positiveCount)
    .put("positiveFraction", positiveFraction)

private fun DynamicTransferMetricDeltaDiagnostics.toJson7f(): JSONObject = JSONObject()
    .put("observationLevel", observationLevel.toJson7f())
    .put("sessionMeanLevel", sessionMeanLevel.toJson7f())

private fun NBio7FInstalledEdgeAggregate.toJson(): JSONObject = JSONObject()
    .put("edgeIdentity", edgeIdentity)
    .put("aggregateProtocolId", aggregate.aggregateProtocolId)
    .put("scoringProtocolId", aggregate.scoringProtocolId)
    .put("distributionProjectionId", aggregate.distributionProjectionId)
    .put("sourceSelectionPolicyIdentity", aggregate.sourceSelectionPolicyIdentity)
    .put("sourceSelectionMode", aggregate.sourceSelectionMode.name)
    .put("targetSessionIds", JSONArray(aggregate.targetSessionIds))
    .put("firstHeldOutSessionTime", aggregate.firstHeldOutSessionTime.toString())
    .put("lastHeldOutSessionTime", aggregate.lastHeldOutSessionTime.toString())
    .put("n0Aggregate", aggregate.n0Aggregate?.toJson7f() ?: JSONObject.NULL)
    .put("m0Aggregate", aggregate.m0Aggregate?.toJson7f() ?: JSONObject.NULL)
    .put("n0PitReliability", aggregate.n0PitReliability?.toJson7f() ?: JSONObject.NULL)
    .put("m0PitReliability", aggregate.m0PitReliability?.toJson7f() ?: JSONObject.NULL)
    .put(
        "availability",
        JSONObject()
            .put("sessionCount", aggregate.availability.sessionCount)
            .put("heldOutObservationCount", aggregate.availability.heldOutObservationCount)
            .put("n0AvailableObservationCount", aggregate.availability.n0AvailableObservationCount)
            .put("m0AvailableObservationCount", aggregate.availability.m0AvailableObservationCount)
            .put("comparableObservationCount", aggregate.availability.comparableObservationCount)
            .put("n0AvailabilityRate", aggregate.availability.n0AvailabilityRate)
            .put("m0AvailabilityRate", aggregate.availability.m0AvailabilityRate)
            .put("comparableObservationRate", aggregate.availability.comparableObservationRate)
            .put("fullyComparableSessionCount", aggregate.availability.fullyComparableSessionCount)
            .put("partiallyComparableSessionCount", aggregate.availability.partiallyComparableSessionCount)
            .put("noComparableSessionCount", aggregate.availability.noComparableSessionCount)
            .put("n0NumericalFailureCount", aggregate.availability.n0NumericalFailureCount)
            .put("m0NumericalFailureCount", aggregate.availability.m0NumericalFailureCount)
            .put(
                "m0OutsideDestinationRepetitionDomainCount",
                aggregate.availability.m0OutsideDestinationRepetitionDomainCount,
            ),
    )
    .put(
        "negativeTransfer",
        aggregate.negativeTransferDiagnostics?.let { negative ->
            JSONObject()
                .put("comparableObservationCount", negative.comparableObservationCount)
                .put("comparableSessionCount", negative.comparableSessionCount)
                .put("negativeLogScore", negative.negativeLogScore.toJson7f())
                .put("crpsLogResistance", negative.crpsLogResistance.toJson7f())
                .put(
                    "weightedIntervalScoreLogResistance",
                    negative.weightedIntervalScoreLogResistance.toJson7f(),
                )
                .put("medianAbsoluteErrorKg", negative.medianAbsoluteErrorKg.toJson7f())
                .put("intervalLogWidthSharpnessChange", negative.intervalLogWidth.toJson7f())
                .put(
                    "catastrophicOverconfidenceObservationCount",
                    negative.catastrophicOverconfidenceObservationCount,
                )
                .put(
                    "catastrophicOverconfidenceObservationFraction",
                    negative.catastrophicOverconfidenceObservationFraction,
                )
                .put(
                    "catastrophicOverconfidenceSessionCount",
                    negative.catastrophicOverconfidenceSessionCount,
                )
                .put(
                    "catastrophicOverconfidenceSessionFraction",
                    negative.catastrophicOverconfidenceSessionFraction,
                )
        } ?: JSONObject.NULL,
    )

