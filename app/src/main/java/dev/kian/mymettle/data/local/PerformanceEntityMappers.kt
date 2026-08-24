package dev.kian.mymettle.data.local

import dev.kian.mymettle.data.local.entity.PerformanceSchemaEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaMetricEntity
import dev.kian.mymettle.data.local.entity.RoutineMetricTargetEntity
import dev.kian.mymettle.data.local.entity.SessionMetricTargetEntity
import dev.kian.mymettle.data.local.entity.SetMetricValueEntity
import dev.kian.mymettle.data.local.entity.SetObservationEntity
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.evidence.AcquisitionMethod
import dev.kian.mymettle.domain.evidence.EvidenceGranularity
import dev.kian.mymettle.domain.evidence.EvidenceQuality
import dev.kian.mymettle.domain.evidence.EvidenceSemanticRole
import dev.kian.mymettle.domain.evidence.TimingQuality
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.MetricTarget
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.PerformanceObservation
import dev.kian.mymettle.domain.performance.PerformanceSchema
import dev.kian.mymettle.domain.performance.PrescriptionEvidence
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.SchemaMetric
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import org.json.JSONArray

fun PerformanceSchemaEntity.toDomain(metrics: List<PerformanceSchemaMetricEntity>): PerformanceSchema =
    PerformanceSchema(
        id = id,
        version = version,
        family = MetricFamily.fromStorage(metricFamily),
        metrics = metrics.filter { it.performanceSchemaId == id }.map { it.toDomain() },
        provenance = provenance,
    )

fun PerformanceSchemaMetricEntity.toDomain(): SchemaMetric = SchemaMetric(
    metric = PerformanceMetric.fromStorage(metric),
    required = required,
    targetable = targetable,
    defaultUnit = UnitId.fromStorage(defaultUnit),
    minimumCanonical = minimumCanonical,
    maximumCanonical = maximumCanonical,
    incrementCanonical = incrementCanonical,
    allowedCanonicalValues = allowedCanonicalValuesJson?.let { json ->
        JSONArray(json).let { array -> List(array.length()) { array.getDouble(it) } }
    }.orEmpty(),
)

fun RoutineMetricTargetEntity.toDomain(): MetricTarget = MetricTarget(
    metric = PerformanceMetric.fromStorage(metric),
    kind = TargetKind.entries.first { it.storageValue == targetKind },
    lowerCanonical = lowerCanonical,
    upperCanonical = upperCanonical,
    canonicalUnit = UnitId.fromStorage(canonicalUnit),
    displayUnit = UnitId.fromStorage(displayUnit),
)

fun SessionMetricTargetEntity.toDomain(): MetricTarget = MetricTarget(
    metric = PerformanceMetric.fromStorage(metric),
    kind = TargetKind.entries.first { it.storageValue == targetKind },
    lowerCanonical = lowerCanonical,
    upperCanonical = upperCanonical,
    canonicalUnit = UnitId.fromStorage(canonicalUnit),
    displayUnit = UnitId.fromStorage(displayUnit),
    evidence = evidenceSource?.let { source ->
        PrescriptionEvidence(
            source = source,
            sourceObservationId = sourceObservationId,
            sourceSetRecordId = sourceSetRecordId,
            inferenceRunId = inferenceRunId,
            anchorCanonical = evidenceAnchorCanonical,
            modelVersion = requireNotNull(evidenceModelVersion) { "Metric evidence requires a model version." },
        )
    },
)

fun MetricTarget.toEntity(sessionSetPrescriptionId: String): SessionMetricTargetEntity =
    SessionMetricTargetEntity(
        sessionSetPrescriptionId = sessionSetPrescriptionId,
        metric = metric.storageValue,
        targetKind = kind.storageValue,
        lowerCanonical = lowerCanonical,
        upperCanonical = upperCanonical,
        canonicalUnit = canonicalUnit.storageValue,
        displayUnit = displayUnit.storageValue,
        evidenceSource = evidence?.source,
        sourceObservationId = evidence?.sourceObservationId,
        sourceSetRecordId = evidence?.sourceSetRecordId,
        inferenceRunId = evidence?.inferenceRunId,
        evidenceAnchorCanonical = evidence?.anchorCanonical,
        evidenceModelVersion = evidence?.modelVersion,
    )

fun SetObservationEntity.toDomain(values: List<SetMetricValueEntity>): PerformanceObservation =
    PerformanceObservation(
        id = id,
        setRecordId = setRecordId,
        executionProfileVersionId = ExecutionProfileVersionId(executionProfileVersionId),
        ordinal = ordinal,
        laterality = Laterality.fromStorage(side),
        completedAt = Instant.parse(completedAt),
        source = source,
        bodyMassContextKg = bodyMassContextKg,
        values = values.filter { it.observationId == id }.map { it.toDomain() },
        supersedesObservationId = supersedesObservationId,
        startedAt = persistedInstant(startedAtEpochSecond, startedAtNano),
        endedAt = persistedInstant(endedAtEpochSecond, endedAtNano),
        timingQuality = TimingQuality.fromStorage(timingQuality),
        sourceZoneOffsetMinutes = sourceZoneOffsetMinutes,
    )

fun SetMetricValueEntity.toDomain(): PerformanceMetricValue = PerformanceMetricValue(
    metric = PerformanceMetric.fromStorage(metric),
    entered = Quantity(enteredValue, UnitId.fromStorage(enteredUnit)),
    canonical = Quantity(canonicalValue, UnitId.fromStorage(canonicalUnit)),
    evidenceQuality = EvidenceQuality(
        granularity = EvidenceGranularity.fromStorage(evidenceGranularity),
        acquisitionMethod = AcquisitionMethod.fromStorage(acquisitionMethod),
    ),
    semanticRole = EvidenceSemanticRole.fromStorage(semanticRole),
)

fun PerformanceMetricValue.toEntity(observationId: String): SetMetricValueEntity = SetMetricValueEntity(
    observationId = observationId,
    metric = metric.storageValue,
    enteredValue = entered.value,
    enteredUnit = entered.unit.storageValue,
    canonicalValue = canonical.value,
    canonicalUnit = canonical.unit.storageValue,
    acquisitionMethod = evidenceQuality.acquisitionMethod.storageValue,
    evidenceGranularity = evidenceQuality.granularity.storageValue,
    semanticRole = semanticRole.storageValue,
)

private fun persistedInstant(epochSecond: Long?, nano: Int?): Instant? = when {
    epochSecond == null && nano == null -> null
    epochSecond == null || nano == null -> error("Persisted timestamp must contain both epoch-second and nano columns.")
    else -> Instant.ofEpochSecond(epochSecond, nano.toLong())
}
