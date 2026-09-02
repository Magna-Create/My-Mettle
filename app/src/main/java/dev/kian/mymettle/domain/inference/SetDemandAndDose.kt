package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.performance.MetricFamily
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.pow

/**
 * N-BIO-7D structural model only. These values are derived SHADOW/CANDIDATE state and are not
 * RIR/RPE, failure probability, local muscle force, hypertrophy, fatigue, readiness, or product
 * authority.
 */
object NBio7DModelIdentity {
    const val DEMAND = "n-bio-7d-frontier-gap-demand-v1"
    const val EXPOSURE = "n-bio-7d-historical-recruitment-exposure-v1"
    const val EFFECTIVE_DOSE = "n-bio-7d-high-demand-band-dose-v1"
    const val SESSION_DOSE = "n-bio-7d-shared-posterior-session-dose-v1"
    const val EMPIRICAL_STATUS = "EMPIRICAL_CALIBRATION_PENDING"
}

enum class SetDemandStructuralSupport {
    RESOLVED,
    BROAD,
    PRIOR_DOMINATED,
    UNSUPPORTED,
    FRONTIER_CONTRADICTION,
}

enum class SetDemandEmpiricalStatus {
    EMPIRICAL_CALIBRATION_PENDING,
    EMPIRICAL_ACCURACY_PENDING,
}

enum class SessionDoseResolution {
    FULLY_RESOLVED,
    PARTIALLY_RESOLVED,
    UNRESOLVED,
}

data class NBio7DConfig(
    val dynamicResistanceDeltaLog: Double = 0.05,
    val loadedHoldDeltaLog: Double = 0.05,
    val repeatedContractionDeltaLog: Double = 0.05,
    val durationOnlyDeltaLog: Double = 0.05,
    val contradictionProbabilityThreshold: Double = 0.95,
    val tau: Double = 4.0,
    val maxIndependentConvolutionNodes: Int = 4096,
) {
    init {
        listOf(
            dynamicResistanceDeltaLog,
            loadedHoldDeltaLog,
            repeatedContractionDeltaLog,
            durationOnlyDeltaLog,
        ).forEach { require(it.isFinite() && it >= 0.0) }
        require(contradictionProbabilityThreshold in 0.5..1.0)
        require(tau.isFinite() && tau > 0.0)
        require(maxIndependentConvolutionNodes >= 32)
    }

    fun deltaFor(family: MetricFamily): Double = when (family) {
        MetricFamily.DYNAMIC_RESISTANCE,
        MetricFamily.BODYWEIGHT_RESISTANCE,
        -> dynamicResistanceDeltaLog
        MetricFamily.LOADED_HOLD -> loadedHoldDeltaLog
        MetricFamily.REPEATED_CONTRACTION -> repeatedContractionDeltaLog
        MetricFamily.DURATION_ONLY -> durationOnlyDeltaLog
        else -> error("N-BIO-7D SetDemand does not support ${family.storageValue}.")
    }

    fun empiricalStatusFor(family: MetricFamily): SetDemandEmpiricalStatus = when (family) {
        MetricFamily.LOADED_HOLD,
        MetricFamily.REPEATED_CONTRACTION,
        MetricFamily.DURATION_ONLY,
        -> SetDemandEmpiricalStatus.EMPIRICAL_ACCURACY_PENDING
        else -> SetDemandEmpiricalStatus.EMPIRICAL_CALIBRATION_PENDING
    }
}

/** One node from the shared contemporaneous capability posterior after projection to a scalar. */
data class WeightedScalarNode(
    val key: String,
    val value: Double,
    val weight: Double,
) {
    init {
        require(key.isNotBlank())
        require(value.isFinite())
        require(weight.isFinite() && weight >= 0.0)
    }
}

data class SetDemandPosterior(
    val family: MetricFamily,
    val frontierGapNodes: List<WeightedScalarNode>,
    val frontierGapSummary: PosteriorSummary?,
    val probabilityAtOrWithinDelta: Double?,
    val contradictionProbability: Double?,
    val structuralSupport: SetDemandStructuralSupport,
    val empiricalStatus: SetDemandEmpiricalStatus,
    val deltaLog: Double,
) {
    init {
        require(deltaLog.isFinite() && deltaLog >= 0.0)
        probabilityAtOrWithinDelta?.let { require(it in 0.0..1.0) }
        contradictionProbability?.let { require(it in 0.0..1.0) }
        if (structuralSupport == SetDemandStructuralSupport.UNSUPPORTED) {
            require(frontierGapNodes.isEmpty())
            require(frontierGapSummary == null)
            require(probabilityAtOrWithinDelta == null)
        } else {
            require(frontierGapNodes.isNotEmpty())
            require(frontierGapSummary != null)
            require(probabilityAtOrWithinDelta != null)
            require(contradictionProbability != null)
        }
    }
}

data class MuscleExposure(
    val muscleSegmentId: String,
    val side: String,
    val recruitmentWeight: Double,
    val historicalRecruitmentProfileVersionId: String,
) {
    init {
        require(muscleSegmentId.isNotBlank())
        require(side.isNotBlank())
        require(historicalRecruitmentProfileVersionId.isNotBlank())
        require(recruitmentWeight.isFinite() && recruitmentWeight >= 0.0)
    }

    /** Conservative Exposure is exactly the immutable historical recruitment weight. */
    val conservativeExposure: Double get() = recruitmentWeight
}

data class EffectiveDosePosterior(
    val exposure: MuscleExposure,
    val nodes: List<WeightedScalarNode>,
    val summary: PosteriorSummary?,
    val structuralSupport: SetDemandStructuralSupport,
    val empiricalStatus: SetDemandEmpiricalStatus,
) {
    val isResolvedEnoughToAggregate: Boolean
        get() = summary != null && structuralSupport !in setOf(
            SetDemandStructuralSupport.UNSUPPORTED,
            SetDemandStructuralSupport.FRONTIER_CONTRADICTION,
        )
}

data class SessionDosePosterior(
    val rawNodes: List<WeightedScalarNode>,
    val rawSummary: PosteriorSummary?,
    val concaveNodes: List<WeightedScalarNode>,
    val concaveSummary: PosteriorSummary?,
    val resolution: SessionDoseResolution,
    val contributingSetCount: Int,
    val unresolvedSetCount: Int,
    val crossStreamIndependenceApproximation: Boolean,
    val tau: Double,
) {
    init {
        require(contributingSetCount >= 0)
        require(unresolvedSetCount >= 0)
        require(unresolvedSetCount <= contributingSetCount)
        require(tau.isFinite() && tau > 0.0)
        if (resolution == SessionDoseResolution.UNRESOLVED) {
            require(rawSummary == null && concaveSummary == null)
        }
    }
}

/**
 * Pure N-BIO-7D posterior transforms. The caller is responsible for supplying a pre-session,
 * contemporaneous capability posterior; this object never fits capability and therefore cannot
 * accidentally consume the performed session as its own demand baseline.
 */
object NBio7DPosteriorMath {
    fun setDemandFromLogFrontier(
        family: MetricFamily,
        logFrontierNodes: List<WeightedScalarNode>,
        logObservedPerformance: Double,
        inheritedSupport: SetDemandStructuralSupport,
        config: NBio7DConfig = NBio7DConfig(),
    ): SetDemandPosterior {
        require(logObservedPerformance.isFinite())
        require(family in supportedDemandFamilies)
        if (logFrontierNodes.isEmpty() || inheritedSupport == SetDemandStructuralSupport.UNSUPPORTED) {
            return unsupportedDemand(family, config)
        }

        val gaps = normalise(logFrontierNodes.map { it.copy(value = it.value - logObservedPerformance) })
        val summary = summary(gaps)
        val delta = config.deltaFor(family)
        val q = gaps.filter { it.value <= delta }.sumOf { it.weight }.coerceIn(0.0, 1.0)
        val contradiction = gaps.filter { it.value < 0.0 }.sumOf { it.weight }.coerceIn(0.0, 1.0)
        val support = if (contradiction >= config.contradictionProbabilityThreshold) {
            SetDemandStructuralSupport.FRONTIER_CONTRADICTION
        } else {
            inheritedSupport
        }
        return SetDemandPosterior(
            family = family,
            frontierGapNodes = gaps,
            frontierGapSummary = summary,
            probabilityAtOrWithinDelta = q,
            contradictionProbability = contradiction,
            structuralSupport = support,
            empiricalStatus = config.empiricalStatusFor(family),
            deltaLog = delta,
        )
    }

    fun unsupportedDemand(
        family: MetricFamily,
        config: NBio7DConfig = NBio7DConfig(),
    ): SetDemandPosterior {
        require(family in supportedDemandFamilies)
        return SetDemandPosterior(
            family = family,
            frontierGapNodes = emptyList(),
            frontierGapSummary = null,
            probabilityAtOrWithinDelta = null,
            contradictionProbability = null,
            structuralSupport = SetDemandStructuralSupport.UNSUPPORTED,
            empiricalStatus = config.empiricalStatusFor(family),
            deltaLog = config.deltaFor(family),
        )
    }

    fun effectiveDose(
        exposure: MuscleExposure,
        demand: SetDemandPosterior,
    ): EffectiveDosePosterior {
        if (demand.structuralSupport in setOf(
                SetDemandStructuralSupport.UNSUPPORTED,
                SetDemandStructuralSupport.FRONTIER_CONTRADICTION,
            )
        ) {
            return EffectiveDosePosterior(
                exposure = exposure,
                nodes = emptyList(),
                summary = null,
                structuralSupport = demand.structuralSupport,
                empiricalStatus = demand.empiricalStatus,
            )
        }
        val nodes = normalise(
            demand.frontierGapNodes.map {
                it.copy(value = if (it.value <= demand.deltaLog) exposure.conservativeExposure else 0.0)
            },
        )
        return EffectiveDosePosterior(
            exposure = exposure,
            nodes = nodes,
            summary = summary(nodes),
            structuralSupport = demand.structuralSupport,
            empiricalStatus = demand.empiricalStatus,
        )
    }

    /**
     * Aggregates sets that were all transformed from the same shared capability posterior.
     * Node keys and weights must match, preserving same-session covariance exactly.
     */
    fun aggregateSharedStream(setDoses: List<EffectiveDosePosterior>): List<WeightedScalarNode> {
        require(setDoses.isNotEmpty())
        require(setDoses.all { it.isResolvedEnoughToAggregate })
        val reference = setDoses.first().nodes
        require(reference.isNotEmpty())
        val referenceWeights = reference.associate { it.key to it.weight }
        setDoses.drop(1).forEach { dose ->
            require(dose.nodes.size == reference.size) { "Shared posterior node count changed within a session stream." }
            val weights = dose.nodes.associate { it.key to it.weight }
            require(weights.keys == referenceWeights.keys) { "Shared posterior node keys changed within a session stream." }
            referenceWeights.forEach { (key, weight) ->
                require(kotlin.math.abs(requireNotNull(weights[key]) - weight) <= 1e-10) {
                    "Shared posterior node weights changed within a session stream."
                }
            }
        }
        val byDose = setDoses.map { it.nodes.associateBy(WeightedScalarNode::key) }
        return normalise(
            reference.map { base ->
                base.copy(value = byDose.sumOf { requireNotNull(it[base.key]).value })
            },
        )
    }

    /**
     * Combines already-joint profile-local stream distributions. Cross-stream independence is an
     * explicit N-BIO-7D v1 approximation because no joint cross-profile capability posterior exists.
     */
    fun convolveIndependentStreams(
        streamNodes: List<List<WeightedScalarNode>>,
        config: NBio7DConfig = NBio7DConfig(),
    ): List<WeightedScalarNode> {
        require(streamNodes.isNotEmpty())
        var combined = listOf(WeightedScalarNode("root", 0.0, 1.0))
        streamNodes.forEachIndexed { streamIndex, rawStream ->
            val stream = compressEqualValues(normalise(rawStream))
            val next = ArrayList<WeightedScalarNode>(combined.size * stream.size)
            combined.forEachIndexed { leftIndex, left ->
                stream.forEachIndexed { rightIndex, right ->
                    next += WeightedScalarNode(
                        key = "s${streamIndex}_${leftIndex}_$rightIndex",
                        value = left.value + right.value,
                        weight = left.weight * right.weight,
                    )
                }
            }
            combined = compressEqualValues(normalise(next))
            if (combined.size > config.maxIndependentConvolutionNodes) {
                combined = quantileCompress(combined, config.maxIndependentConvolutionNodes)
            }
        }
        return normalise(combined)
    }

    fun sessionDose(
        resolvedStreamNodes: List<List<WeightedScalarNode>>,
        contributingSetCount: Int,
        unresolvedSetCount: Int,
        config: NBio7DConfig = NBio7DConfig(),
    ): SessionDosePosterior {
        require(contributingSetCount >= 0)
        require(unresolvedSetCount in 0..contributingSetCount)
        if (resolvedStreamNodes.isEmpty()) {
            return SessionDosePosterior(
                rawNodes = emptyList(),
                rawSummary = null,
                concaveNodes = emptyList(),
                concaveSummary = null,
                resolution = SessionDoseResolution.UNRESOLVED,
                contributingSetCount = contributingSetCount,
                unresolvedSetCount = unresolvedSetCount,
                crossStreamIndependenceApproximation = false,
                tau = config.tau,
            )
        }
        val raw = if (resolvedStreamNodes.size == 1) {
            normalise(resolvedStreamNodes.single())
        } else {
            convolveIndependentStreams(resolvedStreamNodes, config)
        }
        require(raw.all { it.value >= -1e-12 }) { "Raw SessionDose cannot be negative." }
        val concave = normalise(
            raw.map { node -> node.copy(value = config.tau * ln1p(max(0.0, node.value) / config.tau)) },
        )
        return SessionDosePosterior(
            rawNodes = raw,
            rawSummary = summary(raw),
            concaveNodes = concave,
            concaveSummary = summary(concave),
            resolution = if (unresolvedSetCount == 0) {
                SessionDoseResolution.FULLY_RESOLVED
            } else {
                SessionDoseResolution.PARTIALLY_RESOLVED
            },
            contributingSetCount = contributingSetCount,
            unresolvedSetCount = unresolvedSetCount,
            crossStreamIndependenceApproximation = resolvedStreamNodes.size > 1,
            tau = config.tau,
        )
    }

    fun summary(nodes: List<WeightedScalarNode>): PosteriorSummary {
        val normalised = normalise(nodes)
        val mean = normalised.sumOf { it.value * it.weight }
        val variance = normalised.sumOf { (it.value - mean).pow(2) * it.weight }.coerceAtLeast(0.0)
        return PosteriorSummary(
            credibleLower05 = weightedQuantile(normalised, 0.05),
            estimateMedian = weightedQuantile(normalised, 0.50),
            credibleUpper95 = weightedQuantile(normalised, 0.95),
            posteriorVariance = variance,
        )
    }

    private fun weightedQuantile(nodes: List<WeightedScalarNode>, probability: Double): Double {
        require(probability in 0.0..1.0)
        val ordered = normalise(nodes).sortedBy { it.value }
        var cumulative = 0.0
        ordered.forEach {
            cumulative += it.weight
            if (cumulative + 1e-15 >= probability) return it.value
        }
        return ordered.last().value
    }

    private fun normalise(nodes: List<WeightedScalarNode>): List<WeightedScalarNode> {
        require(nodes.isNotEmpty())
        require(nodes.map { it.key }.distinct().size == nodes.size) { "Posterior node keys must be unique." }
        val total = nodes.sumOf { it.weight }
        require(total.isFinite() && total > 0.0) { "Posterior mass must be positive." }
        return nodes.map { it.copy(weight = it.weight / total) }
    }

    private fun compressEqualValues(nodes: List<WeightedScalarNode>): List<WeightedScalarNode> {
        val grouped = nodes.groupBy { roundedValueKey(it.value) }
        return grouped.entries.sortedBy { it.key }.mapIndexed { index, (_, values) ->
            val mass = values.sumOf { it.weight }
            val value = values.sumOf { it.value * it.weight } / mass
            WeightedScalarNode("c$index", value, mass)
        }
    }

    /** Deterministic equal-mass support compression for pathological multi-stream Cartesian growth. */
    private fun quantileCompress(nodes: List<WeightedScalarNode>, cap: Int): List<WeightedScalarNode> {
        val ordered = normalise(nodes).sortedBy { it.value }
        val buckets = Array(cap) { mutableListOf<WeightedScalarNode>() }
        var cumulative = 0.0
        ordered.forEach { node ->
            val midpoint = (cumulative + node.weight / 2.0).coerceIn(0.0, 0.999999999999)
            val bucket = (midpoint * cap).toInt().coerceIn(0, cap - 1)
            buckets[bucket] += node
            cumulative += node.weight
        }
        return normalise(
            buckets.mapIndexedNotNull { index, values ->
                if (values.isEmpty()) return@mapIndexedNotNull null
                val mass = values.sumOf { it.weight }
                WeightedScalarNode(
                    key = "q$index",
                    value = values.sumOf { it.value * it.weight } / mass,
                    weight = mass,
                )
            },
        )
    }

    private fun roundedValueKey(value: Double): Long = kotlin.math.round(value * 1e12).toLong()

    private val supportedDemandFamilies = setOf(
        MetricFamily.DYNAMIC_RESISTANCE,
        MetricFamily.BODYWEIGHT_RESISTANCE,
        MetricFamily.LOADED_HOLD,
        MetricFamily.DURATION_ONLY,
        MetricFamily.REPEATED_CONTRACTION,
    )
}
