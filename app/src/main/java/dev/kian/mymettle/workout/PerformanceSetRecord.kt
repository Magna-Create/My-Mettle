package dev.kian.mymettle.workout

import dev.kian.mymettle.data.local.entity.SetDraftMetricValueEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricTarget
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.PerformanceObservation
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitConverter
import dev.kian.mymettle.domain.performance.UnitId

/** UI/history read model over immutable observations plus non-historical active drafts. */
data class PerformanceSetRecord(
    val record: SetRecordEntity,
    val observations: List<PerformanceObservation>,
    val draftValues: List<SetDraftMetricValueEntity> = emptyList(),
    val metricTargets: List<MetricTarget> = emptyList(),
) {
    val id: String get() = record.id
    val sessionExerciseId: String get() = record.sessionExerciseId
    val setIndex: Int get() = record.setIndex
    val note: String? get() = record.note
    val warmUp: Boolean get() = record.warmUp
    val kind: String get() = record.kind
    val completedAt: String? get() = observations.maxByOrNull { it.completedAt }?.completedAt?.toString()

    fun latestObservation(laterality: Laterality? = null): PerformanceObservation? = observations
        .asSequence()
        .filter { laterality == null || it.laterality == laterality }
        .sortedWith(compareByDescending<PerformanceObservation> { it.completedAt }.thenByDescending { it.ordinal })
        .firstOrNull()

    fun hasObservation(laterality: Laterality): Boolean = observations.any { it.laterality == laterality }

    fun observedValue(metric: PerformanceMetric): PerformanceMetricValue? = observedValue(metric, laterality = null)

    fun observedValue(metric: PerformanceMetric, laterality: Laterality?): PerformanceMetricValue? = observations
        .asSequence()
        .filter { laterality == null || it.laterality == laterality }
        .sortedWith(compareByDescending<PerformanceObservation> { it.completedAt }.thenByDescending { it.ordinal })
        .firstNotNullOfOrNull { observation -> observation.values.firstOrNull { it.metric == metric } }

    fun enteredValue(metric: PerformanceMetric): Double? = enteredValue(metric, laterality = null)

    fun enteredValue(metric: PerformanceMetric, laterality: Laterality?): Double? =
        observedValue(metric, laterality)?.entered?.value
            ?: draftValues.firstOrNull { it.metric == metric.storageValue }?.enteredValue
            ?: metricTargets.firstOrNull { it.metric == metric && it.kind == TargetKind.EXACT }
                ?.lowerCanonical
                ?.let { canonical ->
                    val target = metricTargets.first { it.metric == metric }
                    UnitConverter.convert(
                        dev.kian.mymettle.domain.performance.Quantity(canonical, target.canonicalUnit),
                        target.displayUnit,
                    ).value
                }

    fun enteredUnit(metric: PerformanceMetric): UnitId? = enteredUnit(metric, laterality = null)

    fun enteredUnit(metric: PerformanceMetric, laterality: Laterality?): UnitId? =
        observedValue(metric, laterality)?.entered?.unit
            ?: draftValues.firstOrNull { it.metric == metric.storageValue }?.enteredUnit?.let(UnitId::fromStorage)
            ?: metricTargets.firstOrNull { it.metric == metric }?.displayUnit

    /** A prescribed set stays one logical set even when it contains two side-specific observations. */
    fun isCompleteFor(mode: LateralityMode): Boolean {
        if (observations.isEmpty()) return false
        val sides = observations.mapTo(mutableSetOf()) { it.laterality }
        return when (mode) {
            LateralityMode.UNILATERAL -> Laterality.RIGHT in sides && Laterality.LEFT in sides
            LateralityMode.BILATERAL_ONLY -> Laterality.BILATERAL in sides || Laterality.UNKNOWN in sides
            LateralityMode.ALTERNATING_ALLOWED -> sides.any {
                it in setOf(Laterality.ALTERNATING, Laterality.BILATERAL, Laterality.LEFT, Laterality.RIGHT, Laterality.UNKNOWN)
            }
            LateralityMode.NOT_APPLICABLE -> Laterality.NOT_APPLICABLE in sides || Laterality.UNKNOWN in sides
            LateralityMode.UNKNOWN -> true
        }
    }

    val load: Double? get() = enteredValue(PerformanceMetric.EXTERNAL_LOAD)
        ?: enteredValue(PerformanceMetric.ASSISTANCE)
    val reps: Int? get() = enteredValue(PerformanceMetric.REPETITIONS)?.toInt()
    val durationSeconds: Int? get() = observedValue(PerformanceMetric.DURATION)?.canonical?.value?.toInt()
        ?: draftValues.firstOrNull { it.metric == PerformanceMetric.DURATION.storageValue }
            ?.let { UnitConverter.canonical(dev.kian.mymettle.domain.performance.Quantity(it.enteredValue, UnitId.fromStorage(it.enteredUnit))).value.toInt() }
        ?: metricTargets.firstOrNull { it.metric == PerformanceMetric.DURATION && it.kind == TargetKind.EXACT }
            ?.lowerCanonical?.toInt()
    val distanceMetres: Double? get() = observedValue(PerformanceMetric.DISTANCE)?.canonical?.value
        ?: draftValues.firstOrNull { it.metric == PerformanceMetric.DISTANCE.storageValue }
            ?.let { UnitConverter.canonical(dev.kian.mymettle.domain.performance.Quantity(it.enteredValue, UnitId.fromStorage(it.enteredUnit))).value }
        ?: metricTargets.firstOrNull { it.metric == PerformanceMetric.DISTANCE && it.kind == TargetKind.EXACT }
            ?.lowerCanonical
    val unit: String get() = enteredUnit(PerformanceMetric.EXTERNAL_LOAD)?.storageValue
        ?: enteredUnit(PerformanceMetric.ASSISTANCE)?.storageValue
        ?: metricTargets.firstOrNull()?.displayUnit?.storageValue
        ?: "kg"
}
