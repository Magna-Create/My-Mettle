package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.LoadedHoldEvidence
import dev.kian.mymettle.domain.inference.NBio7DCapabilityProjection
import dev.kian.mymettle.domain.inference.NBio7DConfig
import dev.kian.mymettle.domain.inference.NBio7DSessionEvaluator
import dev.kian.mymettle.domain.inference.NBio7DSessionResult
import dev.kian.mymettle.domain.inference.NBio7DSetInput
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFit
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import dev.kian.mymettle.domain.inference.RepeatedContractionEvidence
import dev.kian.mymettle.domain.inference.DurationOnlyEvidence
import dev.kian.mymettle.domain.inference.SetDemandStructuralSupport
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.engine.performance.DynamicResistanceEvidenceProjector
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import dev.kian.mymettle.engine.performance.NonDynamicAdaptiveSparseSolver
import dev.kian.mymettle.engine.performance.NonDynamicCapabilityEvidenceProjector
import dev.kian.mymettle.inference.DynamicTrendCapabilityParameterCodec
import dev.kian.mymettle.inference.DynamicTrendCapabilityShadowRepository
import dev.kian.mymettle.inference.NBio7DShadowRepository
import dev.kian.mymettle.inference.NonDynamicCapabilityParameterCodec
import dev.kian.mymettle.inference.NonDynamicCapabilityShadowRepository
import java.time.Instant
import kotlin.math.ln

/** One auditable causal replay result. Nothing here changes product authority or writes 7E state. */
data class NBio7DExecutedSession(
    val sessionId: String,
    val startedAt: Instant,
    val result: NBio7DSessionResult,
    val persistenceInputs: List<NBio7DShadowRepository.SetPersistenceInput>,
    val capabilitySnapshots: List<NBio7DShadowRepository.CapabilitySnapshot>,
    val skippedTargetObservations: Map<String, String>,
    val streamFailures: Map<String, String>,
    val streamSolverIdentities: Map<String, String>,
    val streamFitElapsedMillis: Map<String, Long>,
)

data class NBio7DHistoricalReplayExecution(
    val sessions: List<NBio7DExecutedSession>,
    val plannerSkippedTargetObservations: Map<String, String>,
) {
    val evaluatedSetCount: Int get() = sessions.sumOf { it.result.setResults.size }
    val exposureCount: Int get() = sessions.sumOf { it.result.exposureCount }
    val resolvedEffectiveDoseCount: Int get() = sessions.sumOf { it.result.effectiveDoseResolvedCount }
    val unresolvedEffectiveDoseCount: Int get() = sessions.sumOf { it.result.effectiveDoseUnresolvedCount }
    val streamFailureCount: Int get() = sessions.sumOf { it.streamFailures.size }
}

/**
 * Executes a pre-built [NBio7DHistoricalReplayPlan] through the frozen 7B.X/7C capability engines.
 * Each capability stream is fit exactly once from strictly pre-session evidence, then every set in
 * the target session is projected from that same posterior. Invalid target semantics are skipped;
 * valid target semantics without a usable pre-session capability retain Exposure but receive typed
 * UNSUPPORTED demand/EffectiveDose.
 */
class NBio7DHistoricalReplayExecutor(
    private val config: NBio7DConfig = NBio7DConfig(),
) {
    fun execute(
        plan: NBio7DHistoricalReplayPlan,
        dynamicHistory: NBio7BRawHistory,
        nonDynamicHistory: NBio7CRawHistory,
    ): NBio7DHistoricalReplayExecution {
        val evaluator = NBio7DSessionEvaluator(config)
        val sessions = plan.sessions.mapNotNull { sessionPlan ->
            val streamOutcomes = linkedMapOf<String, StreamOutcome>()
            sessionPlan.sets.groupBy { it.capabilityStreamKey }.toSortedMap().forEach { (streamKey, sets) ->
                val evidenceIds = sets.map { set -> set.preSessionTrainingEvidence.map { it.observationId } }
                require(evidenceIds.distinct().size == 1) {
                    "Sets sharing 7D capability stream $streamKey must share one pre-session causal evidence slice."
                }
                streamOutcomes[streamKey] = fitStream(
                    set = sets.first(),
                    dynamicHistory = dynamicHistory,
                    nonDynamicHistory = nonDynamicHistory,
                )
            }

            val skipped = linkedMapOf<String, String>()
            val inputs = mutableListOf<NBio7DSetInput>()
            val persistenceInputs = mutableListOf<NBio7DShadowRepository.SetPersistenceInput>()
            sessionPlan.sets.forEach { set ->
                val outcome = requireNotNull(streamOutcomes[set.capabilityStreamKey])
                val target = projectTarget(set, dynamicHistory, nonDynamicHistory)
                if (target == null) {
                    skipped[set.target.observationId] = "invalid_or_unsupported_target_semantics"
                    return@forEach
                }
                val support = when (outcome) {
                    is StreamOutcome.Dynamic -> NBio7DReplaySupportClassifier.dynamic(outcome.fit, target.dynamicRepetitions)
                    is StreamOutcome.NonDynamic -> NBio7DReplaySupportClassifier.nonDynamic(outcome.fit, target.nonDynamicInputCoordinate)
                    is StreamOutcome.Unsupported -> SetDemandStructuralSupport.UNSUPPORTED
                }
                val nodes = when (outcome) {
                    is StreamOutcome.Dynamic -> NBio7DCapabilityProjection.dynamicResistanceLogFrontier(
                        outcome.fit,
                        requireNotNull(target.dynamicRepetitions),
                    )
                    is StreamOutcome.NonDynamic -> when (outcome.fit.family) {
                        MetricFamily.LOADED_HOLD -> NBio7DCapabilityProjection.loadedHoldLogFrontier(
                            outcome.fit,
                            requireNotNull(target.nonDynamicInputCoordinate),
                        )
                        MetricFamily.REPEATED_CONTRACTION -> NBio7DCapabilityProjection.repeatedContractionLogFrontier(
                            outcome.fit,
                            requireNotNull(target.nonDynamicInputCoordinate).toInt(),
                        )
                        MetricFamily.DURATION_ONLY -> NBio7DCapabilityProjection.durationOnlyLogFrontier(outcome.fit)
                        else -> error("Unsupported 7D non-dynamic family ${outcome.fit.family.storageValue}")
                    }
                    is StreamOutcome.Unsupported -> null
                }
                inputs += NBio7DSetInput(
                    setObservationId = set.target.observationId,
                    capabilityStreamKey = set.capabilityStreamKey,
                    family = set.target.metricFamily,
                    logObservedPerformance = target.logObservedPerformance,
                    logFrontierNodes = nodes,
                    inheritedDemandSupport = support,
                    exposures = set.context.exposures,
                )
                persistenceInputs += NBio7DShadowRepository.SetPersistenceInput(
                    sessionId = sessionPlan.session.sessionId,
                    setObservationId = set.target.observationId,
                    executionProfileVersionId = set.context.executionProfileVersionId,
                    side = set.context.side,
                    completedAt = set.context.completedAt,
                )
            }
            if (inputs.isEmpty()) return@mapNotNull null
            NBio7DExecutedSession(
                sessionId = sessionPlan.session.sessionId,
                startedAt = sessionPlan.session.startedAt,
                result = evaluator.evaluate(inputs),
                persistenceInputs = persistenceInputs,
                capabilitySnapshots = streamOutcomes.values.mapNotNull(StreamOutcome::snapshot),
                skippedTargetObservations = skipped.toSortedMap(),
                streamFailures = streamOutcomes.mapNotNull { (key, outcome) ->
                    (outcome as? StreamOutcome.Unsupported)?.let { key to it.reason }
                }.toMap(),
                streamSolverIdentities = streamOutcomes.mapNotNull { (key, outcome) ->
                    outcome.solverIdentity?.let { key to it }
                }.toMap(),
                streamFitElapsedMillis = streamOutcomes.mapValues { it.value.elapsedMillis },
            )
        }
        return NBio7DHistoricalReplayExecution(
            sessions = sessions,
            plannerSkippedTargetObservations = plan.skippedTargetObservations,
        )
    }

    private fun fitStream(
        set: NBio7DReplaySetPlan,
        dynamicHistory: NBio7BRawHistory,
        nonDynamicHistory: NBio7CRawHistory,
    ): StreamOutcome {
        if (set.preSessionTrainingEvidence.isEmpty()) return StreamOutcome.Unsupported("no_pre_session_training_evidence", 0L)
        val started = System.nanoTime()
        return try {
            when (set.target.metricFamily) {
                MetricFamily.DYNAMIC_RESISTANCE,
                MetricFamily.BODYWEIGHT_RESISTANCE,
                -> {
                    val descriptor = dynamicHistory.profiles[set.target.executionProfileVersionId.value]
                        ?: return StreamOutcome.Unsupported("missing_dynamic_profile_semantics", elapsedMillis(started))
                    val projection = DynamicResistanceEvidenceProjector.project(
                        profile = descriptor.semantics,
                        side = set.target.laterality,
                        evidence = set.preSessionTrainingEvidence,
                        policy = NBioCorrectedCandidateV2Bundle.evidencePolicy,
                    )
                    if (projection.evidence.isEmpty()) {
                        return StreamOutcome.Unsupported("no_eligible_dynamic_pre_session_evidence", elapsedMillis(started))
                    }
                    val horizon = projection.evidence.maxOf { it.completedAt }
                    val baseModel = DynamicStochasticFrontierModel(NBioCorrectedCandidateV2Bundle.baseConfig)
                    val baseFit = baseModel.fit(
                        DynamicCapabilityFitRequest(
                            projection = projection,
                            inferenceHorizon = horizon,
                            modelConfig = baseModel.config.toModelConfig(DynamicTrendCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT),
                        ),
                    )
                    val solver = NBioCorrectedCandidateV2Bundle.sparseSolver()
                    val modelConfig = solver.modelConfig(DynamicTrendCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
                    val fit = solver.fitFromFrozenV1(
                        DynamicCapabilityFitRequest(projection, horizon, modelConfig),
                        baseFit,
                    )
                    StreamOutcome.Dynamic(
                        fit = fit,
                        snapshot = NBio7DShadowRepository.CapabilitySnapshot(
                            streamKey = set.capabilityStreamKey,
                            executionProfileVersionId = fit.executionProfileVersionId.value,
                            side = fit.side.storageValue,
                            capabilityFamily = DynamicTrendCapabilityShadowRepository.CAPABILITY_FAMILY,
                            canonicalUnit = "kg",
                            posterior = fit.frontierAtLatestSession,
                            parameterSchemaVersion = DynamicTrendCapabilityParameterCodec.SCHEMA_VERSION,
                            encodedParameters = DynamicTrendCapabilityParameterCodec.encode(fit),
                            modelConfig = modelConfig,
                        ),
                        elapsedMillis = elapsedMillis(started),
                    )
                }
                MetricFamily.LOADED_HOLD,
                MetricFamily.DURATION_ONLY,
                MetricFamily.REPEATED_CONTRACTION,
                -> {
                    val descriptor = nonDynamicHistory.profiles[set.target.executionProfileVersionId.value]
                        ?: return StreamOutcome.Unsupported("missing_non_dynamic_profile_semantics", elapsedMillis(started))
                    val familyConfig = NonDynamicCapabilityV1.configFor(set.target.metricFamily)
                    val solver = NonDynamicAdaptiveSparseSolver(familyConfig)
                    val projection = NonDynamicCapabilityEvidenceProjector.project(
                        profile = descriptor.semantics,
                        side = set.target.laterality,
                        evidence = set.preSessionTrainingEvidence,
                    )
                    if (projection.evidence.isEmpty()) {
                        return StreamOutcome.Unsupported("no_eligible_non_dynamic_pre_session_evidence", elapsedMillis(started))
                    }
                    val modelConfig = familyConfig.toModelConfig(NonDynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
                    val fit = solver.fit(
                        projection = projection,
                        inferenceHorizon = projection.evidence.maxOf { it.completedAt },
                        configCreatedAt = NonDynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT,
                    )
                    require(fit.modelConfigId == modelConfig.id)
                    StreamOutcome.NonDynamic(
                        fit = fit,
                        snapshot = NBio7DShadowRepository.CapabilitySnapshot(
                            streamKey = set.capabilityStreamKey,
                            executionProfileVersionId = fit.executionProfileVersionId.value,
                            side = fit.side.storageValue,
                            capabilityFamily = fit.family.storageValue,
                            canonicalUnit = fit.canonicalUnit.storageValue,
                            posterior = fit.frontierAtReference,
                            parameterSchemaVersion = NonDynamicCapabilityParameterCodec.SCHEMA_VERSION,
                            encodedParameters = NonDynamicCapabilityParameterCodec.encode(fit),
                            modelConfig = modelConfig,
                        ),
                        elapsedMillis = elapsedMillis(started),
                    )
                }
                else -> StreamOutcome.Unsupported("unsupported_capability_family:${set.target.metricFamily.storageValue}", elapsedMillis(started))
            }
        } catch (failure: Exception) {
            StreamOutcome.Unsupported(
                reason = "capability_fit_failure:${failure::class.simpleName ?: "Exception"}",
                elapsedMillis = elapsedMillis(started),
            )
        }
    }

    private fun projectTarget(
        set: NBio7DReplaySetPlan,
        dynamicHistory: NBio7BRawHistory,
        nonDynamicHistory: NBio7CRawHistory,
    ): TargetProjection? = when (set.target.metricFamily) {
        MetricFamily.DYNAMIC_RESISTANCE,
        MetricFamily.BODYWEIGHT_RESISTANCE,
        -> {
            val descriptor = dynamicHistory.profiles[set.target.executionProfileVersionId.value] ?: return null
            val projected = DynamicResistanceEvidenceProjector.project(
                descriptor.semantics,
                set.target.laterality,
                listOf(set.target),
                NBioCorrectedCandidateV2Bundle.evidencePolicy,
            ).evidence.singleOrNull() ?: return null
            TargetProjection(
                logObservedPerformance = ln(projected.resistance.value),
                dynamicRepetitions = projected.repetitions,
            )
        }
        MetricFamily.LOADED_HOLD,
        MetricFamily.DURATION_ONLY,
        MetricFamily.REPEATED_CONTRACTION,
        -> {
            val descriptor = nonDynamicHistory.profiles[set.target.executionProfileVersionId.value] ?: return null
            val projected = NonDynamicCapabilityEvidenceProjector.project(
                descriptor.semantics,
                set.target.laterality,
                listOf(set.target),
            ).evidence.singleOrNull() ?: return null
            when (projected) {
                is LoadedHoldEvidence -> TargetProjection(
                    logObservedPerformance = ln(projected.resistance.valueKg),
                    nonDynamicInputCoordinate = projected.durationSeconds,
                )
                is RepeatedContractionEvidence -> TargetProjection(
                    logObservedPerformance = ln(projected.resistance.valueKg),
                    nonDynamicInputCoordinate = projected.cycles.toDouble(),
                )
                is DurationOnlyEvidence -> TargetProjection(
                    logObservedPerformance = ln(projected.durationSeconds),
                    nonDynamicInputCoordinate = null,
                )
            }
        }
        else -> null
    }

    private data class TargetProjection(
        val logObservedPerformance: Double,
        val dynamicRepetitions: Int? = null,
        val nonDynamicInputCoordinate: Double? = null,
    )

    private sealed interface StreamOutcome {
        val snapshot: NBio7DShadowRepository.CapabilitySnapshot?
        val elapsedMillis: Long
        val solverIdentity: String?

        data class Dynamic(
            val fit: DynamicTrendFrontierFit,
            override val snapshot: NBio7DShadowRepository.CapabilitySnapshot,
            override val elapsedMillis: Long,
        ) : StreamOutcome {
            override val solverIdentity: String get() = fit.solverDiagnostics.solverIdentity.identity
        }

        data class NonDynamic(
            val fit: NonDynamicCapabilityFit,
            override val snapshot: NBio7DShadowRepository.CapabilitySnapshot,
            override val elapsedMillis: Long,
        ) : StreamOutcome {
            override val solverIdentity: String get() = fit.solverDiagnostics.solverIdentity.identity
        }

        data class Unsupported(
            val reason: String,
            override val elapsedMillis: Long,
        ) : StreamOutcome {
            override val snapshot: NBio7DShadowRepository.CapabilitySnapshot? = null
            override val solverIdentity: String? = null
        }
    }

    private fun elapsedMillis(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000L
}

/** Structural support classifier only; it never inspects real-history distributions to tune cutoffs. */
object NBio7DReplaySupportClassifier {
    fun dynamic(fit: DynamicTrendFrontierFit, repetitions: Int?): SetDemandStructuralSupport {
        if (repetitions == null || repetitions <= 0) return SetDemandStructuralSupport.UNSUPPORTED
        if (fit.frontierTrend.identification == DynamicParameterIdentification.PRIOR_DOMINATED ||
            fit.slope.identification == DynamicParameterIdentification.PRIOR_DOMINATED
        ) return SetDemandStructuralSupport.PRIOR_DOMINATED
        if (repetitions !in fit.observedRepMin..fit.observedRepMax) return SetDemandStructuralSupport.BROAD
        return SetDemandStructuralSupport.RESOLVED
    }

    fun nonDynamic(fit: NonDynamicCapabilityFit, inputCoordinate: Double?): SetDemandStructuralSupport {
        if (fit.trajectory.identification == DynamicParameterIdentification.PRIOR_DOMINATED ||
            fit.slope?.identification == DynamicParameterIdentification.PRIOR_DOMINATED
        ) return SetDemandStructuralSupport.PRIOR_DOMINATED
        return when (fit.family) {
            MetricFamily.DURATION_ONLY -> SetDemandStructuralSupport.RESOLVED
            MetricFamily.LOADED_HOLD,
            MetricFamily.REPEATED_CONTRACTION,
            -> {
                val min = fit.observedInputMin ?: return SetDemandStructuralSupport.BROAD
                val max = fit.observedInputMax ?: return SetDemandStructuralSupport.BROAD
                if (inputCoordinate == null || inputCoordinate !in min..max) SetDemandStructuralSupport.BROAD
                else SetDemandStructuralSupport.RESOLVED
            }
            else -> SetDemandStructuralSupport.UNSUPPORTED
        }
    }
}
