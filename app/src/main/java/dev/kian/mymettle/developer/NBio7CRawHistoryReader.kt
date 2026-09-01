package dev.kian.mymettle.developer

import android.database.Cursor
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.evidence.AcquisitionMethod
import dev.kian.mymettle.domain.evidence.EvidenceGranularity
import dev.kian.mymettle.domain.evidence.EvidenceQuality
import dev.kian.mymettle.domain.evidence.EvidenceSemanticRole
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.NonDynamicProfileSemantics
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.HistoricalCompletedSetEvidenceRevision
import dev.kian.mymettle.engine.inference.NonDynamicHistoricalAvailabilityV1
import java.time.Instant

data class NBio7CProfileDescriptor(
    val semantics: NonDynamicProfileSemantics,
    val exerciseName: String,
    val profileName: String,
) {
    val label: String get() = "$exerciseName — $profileName"
}

data class NBio7CRawHistory(
    val revisions: List<HistoricalCompletedSetEvidenceRevision>,
    val profiles: Map<String, NBio7CProfileDescriptor>,
)

/** Reads canonical Room14 evidence for the three N-BIO-7C families without mutating it. */
class NBio7CRawHistoryReader(private val database: MyMettleDatabase) {
    fun read(): NBio7CRawHistory {
        val sqlite = database.openHelper.readableDatabase
        val familySql = "'loaded_hold','duration_only','repeated_contraction'"
        val metricsByObservation = linkedMapOf<String, MutableList<PerformanceMetricValue>>()
        sqlite.query(
            """
            SELECT smv.observationId, smv.metric, smv.enteredValue, smv.enteredUnit,
                   smv.canonicalValue, smv.canonicalUnit, smv.acquisitionMethod,
                   smv.evidenceGranularity, smv.semanticRole
            FROM set_metric_value AS smv
            INNER JOIN set_observation AS so ON so.id = smv.observationId
            INNER JOIN set_record AS sr ON sr.id = so.setRecordId
            INNER JOIN session_exercise AS se ON se.id = sr.sessionExerciseId
            INNER JOIN session AS s ON s.id = se.sessionId
            INNER JOIN execution_profile_version AS epv ON epv.id = so.executionProfileVersionId
            WHERE s.status = 'completed' AND s.excludedFromInsights = 0
              AND epv.metricFamily IN ($familySql)
            ORDER BY smv.observationId, smv.metric
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val observationId = cursor.string7c("observationId")
                val metric = PerformanceMetric.fromStorage(cursor.string7c("metric"))
                metricsByObservation.getOrPut(observationId, ::mutableListOf) += PerformanceMetricValue(
                    metric = metric,
                    entered = Quantity(cursor.double7c("enteredValue"), UnitId.fromStorage(cursor.string7c("enteredUnit"))),
                    canonical = Quantity(cursor.double7c("canonicalValue"), UnitId.fromStorage(cursor.string7c("canonicalUnit"))),
                    evidenceQuality = EvidenceQuality(
                        EvidenceGranularity.fromStorage(cursor.string7c("evidenceGranularity")),
                        AcquisitionMethod.fromStorage(cursor.string7c("acquisitionMethod")),
                    ),
                    semanticRole = EvidenceSemanticRole.fromStorage(cursor.string7c("semanticRole")),
                )
            }
        }

        val revisions = mutableListOf<HistoricalCompletedSetEvidenceRevision>()
        sqlite.query(
            """
            SELECT sr.id AS setRecordId, so.id AS observationId, s.id AS sessionId,
                   s.completedAt AS sessionCompletedAt, s.editedAt AS sessionEditedAt,
                   sr.sessionExerciseId, so.executionProfileVersionId, epv.metricFamily,
                   so.side, so.source AS observationSource, so.completedAt, so.recordedAt,
                   so.bodyMassContextKg AS observationBodyMassContextKg,
                   s.bodyweightSnapshotKg AS sessionBodyMassSnapshotKg,
                   sr.warmUp, sr.kind, so.supersedesObservationId
            FROM set_record AS sr
            INNER JOIN session_exercise AS se ON se.id = sr.sessionExerciseId
            INNER JOIN session AS s ON s.id = se.sessionId
            INNER JOIN set_observation AS so ON so.setRecordId = sr.id
            INNER JOIN execution_profile_version AS epv ON epv.id = so.executionProfileVersionId
            WHERE s.status = 'completed' AND s.excludedFromInsights = 0
              AND epv.metricFamily IN ($familySql)
            ORDER BY so.completedAt, so.recordedAt, sr.id, so.ordinal, so.id
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val observationId = cursor.string7c("observationId")
                val completedAt = Instant.parse(cursor.string7c("completedAt"))
                val nativeRecordedAt = Instant.parse(cursor.string7c("recordedAt"))
                val sessionCompletedAt = cursor.nullableString7c("sessionCompletedAt")?.let(Instant::parse) ?: completedAt
                val sessionEditedAt = cursor.nullableString7c("sessionEditedAt")?.let(Instant::parse)
                val source = cursor.string7c("observationSource")
                revisions += HistoricalCompletedSetEvidenceRevision(
                    evidence = CompletedSetEvidence(
                        setRecordId = cursor.string7c("setRecordId"),
                        observationId = observationId,
                        sessionExerciseId = cursor.string7c("sessionExerciseId"),
                        executionProfileVersionId = ExecutionProfileVersionId(cursor.string7c("executionProfileVersionId")),
                        metricFamily = MetricFamily.fromStorage(cursor.string7c("metricFamily")),
                        laterality = Laterality.fromStorage(cursor.string7c("side")),
                        completedAt = completedAt,
                        metricValues = metricsByObservation[observationId].orEmpty(),
                        bodyMassContextKg = cursor.nullableDouble7c("observationBodyMassContextKg")
                            ?: cursor.nullableDouble7c("sessionBodyMassSnapshotKg"),
                        warmUp = cursor.int7c("warmUp") != 0,
                        kind = cursor.string7c("kind"),
                        observationSource = source,
                        sessionId = cursor.string7c("sessionId"),
                    ),
                    recordedAt = NonDynamicHistoricalAvailabilityV1.resolve(
                        source,
                        nativeRecordedAt,
                        sessionCompletedAt,
                        sessionEditedAt,
                    ),
                    sessionCompletedAt = sessionCompletedAt,
                    supersedesObservationId = cursor.nullableString7c("supersedesObservationId"),
                )
            }
        }

        val profiles = linkedMapOf<String, NBio7CProfileDescriptor>()
        sqlite.query(
            """
            SELECT epv.id AS executionProfileVersionId, epv.executionProfileId,
                   e.name AS exerciseName, eep.name AS profileName, epv.metricFamily,
                   epv.resistanceSemantics, epv.resistanceModelVersion,
                   epv.bodyweightCoefficient, epv.externalLoadCoefficient, epv.assistanceCoefficient,
                   epv.entryBasis, epv.lateralityMode
            FROM execution_profile_version AS epv
            INNER JOIN exercise_execution_profile AS eep ON eep.id = epv.executionProfileId
            INNER JOIN exercise AS e ON e.id = eep.exerciseId
            WHERE epv.metricFamily IN ($familySql)
            ORDER BY e.name, eep.name, epv.version, epv.id
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.string7c("executionProfileVersionId")
                profiles[id] = NBio7CProfileDescriptor(
                    semantics = NonDynamicProfileSemantics(
                        executionProfileVersionId = ExecutionProfileVersionId(id),
                        executionProfileId = ExecutionProfileId(cursor.string7c("executionProfileId")),
                        metricFamily = MetricFamily.fromStorage(cursor.string7c("metricFamily")),
                        resistanceModel = ResistanceModel(
                            modelVersion = cursor.string7c("resistanceModelVersion"),
                            semantics = ResistanceSemantics.entries.first { it.storageValue == cursor.string7c("resistanceSemantics") },
                            bodyweightCoefficient = cursor.double7c("bodyweightCoefficient"),
                            externalLoadCoefficient = cursor.double7c("externalLoadCoefficient"),
                            assistanceCoefficient = cursor.double7c("assistanceCoefficient"),
                        ),
                        entryBasis = EntryBasis.fromStorage(cursor.string7c("entryBasis")),
                        lateralityMode = LateralityMode.entries.first { it.storageValue == cursor.string7c("lateralityMode") },
                    ),
                    exerciseName = cursor.string7c("exerciseName"),
                    profileName = cursor.string7c("profileName"),
                )
            }
        }
        return NBio7CRawHistory(revisions, profiles)
    }
}

private fun Cursor.index7c(name: String) = getColumnIndexOrThrow(name)
private fun Cursor.string7c(name: String): String = getString(index7c(name))
private fun Cursor.nullableString7c(name: String): String? = index7c(name).let { if (isNull(it)) null else getString(it) }
private fun Cursor.double7c(name: String): Double = getDouble(index7c(name))
private fun Cursor.nullableDouble7c(name: String): Double? = index7c(name).let { if (isNull(it)) null else getDouble(it) }
private fun Cursor.int7c(name: String): Int = getInt(index7c(name))
