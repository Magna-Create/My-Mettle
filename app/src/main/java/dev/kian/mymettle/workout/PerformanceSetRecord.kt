package dev.kian.mymettle.workout

import dev.kian.mymettle.data.local.entity.SetDraftMetricValueEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
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

    fun observedValue(metric: PerformanceMetric): PerformanceMetricValue? = observations
        .sortedWith(compareByDescending<PerformanceObservation> { it.completedAt }.thenByDescending { it.ordinal })
        .firstNotNullOfOrNull { observation -> observation.values.firstOrNull { it.metric == metric } }

    fun enteredValue(metric: PerformanceMetric): Double? = observedValue(metric)?.entered?.value
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

    fun enteredUnit(metric: PerformanceMetric): UnitId? = observedValue(metric)?.entered?.unit
        ?: draftValues.firstOrNull { it.metric == metric.storageValue }?.enteredUnit?.let(UnitId::fromStorage)
        ?: metricTargets.firstOrNull { it.metric == metric }?.displayUnit

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
