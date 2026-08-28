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
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.DynamicHistoricalAvailabilityV2
import dev.kian.mymettle.engine.inference.HistoricalCompletedSetEvidenceRevision
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

data class NBio7BProfileDescriptor(
    val semantics: DynamicResistanceProfileSemantics,
    val exerciseName: String,
    val profileName: String,
) {
    val label: String get() = "$exerciseName — $profileName"
}

data class NBio7BRawHistory(
    val revisions: List<HistoricalCompletedSetEvidenceRevision>,
    val profiles: Map<String, NBio7BProfileDescriptor>,
)

data class NBio7BRawEvidenceFingerprint(
    val sha256: String,
    val tableRowCounts: Map<String, Long>,
)

/** Reads only canonical Room14 workout/performance evidence needed by 7B acceptance. */
class NBio7BRawHistoryReader(private val database: MyMettleDatabase) {
    fun read(): NBio7BRawHistory {
        val sqlite = database.openHelper.readableDatabase
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
            WHERE s.status = 'completed'
              AND s.excludedFromInsights = 0
              AND epv.metricFamily IN ('dynamic_resistance', 'bodyweight_resistance')
            ORDER BY smv.observationId, smv.metric
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val observationId = cursor.string("observationId")
                val metric = PerformanceMetric.fromStorage(cursor.string("metric"))
                metricsByObservation.getOrPut(observationId, ::mutableListOf) += PerformanceMetricValue(
                    metric = metric,
                    entered = Quantity(cursor.double("enteredValue"), UnitId.fromStorage(cursor.string("enteredUnit"))),
                    canonical = Quantity(cursor.double("canonicalValue"), UnitId.fromStorage(cursor.string("canonicalUnit"))),
                    evidenceQuality = EvidenceQuality(
                        granularity = EvidenceGranularity.fromStorage(cursor.string("evidenceGranularity")),
                        acquisitionMethod = AcquisitionMethod.fromStorage(cursor.string("acquisitionMethod")),
                    ),
                    semanticRole = EvidenceSemanticRole.fromStorage(cursor.string("semanticRole")),
                )
            }
        }

        val revisions = mutableListOf<HistoricalCompletedSetEvidenceRevision>()
        sqlite.query(
            """
            SELECT sr.id AS setRecordId, so.id AS observationId, s.id AS sessionId,
                   s.completedAt AS sessionCompletedAt, s.editedAt AS sessionEditedAt,
                   sr.sessionExerciseId AS sessionExerciseId,
                   so.executionProfileVersionId AS executionProfileVersionId, epv.metricFamily AS metricFamily,
                   so.side AS side, so.source AS observationSource,
                   so.completedAt AS completedAt, so.recordedAt AS recordedAt,
                   so.bodyMassContextKg AS observationBodyMassContextKg,
                   s.bodyweightSnapshotKg AS sessionBodyMassSnapshotKg,
                   sr.warmUp AS warmUp, sr.kind AS kind, so.supersedesObservationId AS supersedesObservationId
            FROM set_record AS sr
            INNER JOIN session_exercise AS se ON se.id = sr.sessionExerciseId
            INNER JOIN session AS s ON s.id = se.sessionId
            INNER JOIN set_observation AS so ON so.setRecordId = sr.id
            INNER JOIN execution_profile_version AS epv ON epv.id = so.executionProfileVersionId
            WHERE s.status = 'completed'
              AND s.excludedFromInsights = 0
              AND epv.metricFamily IN ('dynamic_resistance', 'bodyweight_resistance')
            ORDER BY so.completedAt, so.recordedAt, sr.id, so.ordinal, so.id
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val observationId = cursor.string("observationId")
                val completedAt = Instant.parse(cursor.string("completedAt"))
                val nativeRecordedAt = Instant.parse(cursor.string("recordedAt"))
                val sessionCompletedAt = cursor.nullableString("sessionCompletedAt")?.let(Instant::parse) ?: completedAt
                val sessionEditedAt = cursor.nullableString("sessionEditedAt")?.let(Instant::parse)
                val observationSource = cursor.string("observationSource")
                revisions += HistoricalCompletedSetEvidenceRevision(
                    evidence = CompletedSetEvidence(
                        setRecordId = cursor.string("setRecordId"),
                        observationId = observationId,
                        sessionExerciseId = cursor.string("sessionExerciseId"),
                        executionProfileVersionId = ExecutionProfileVersionId(cursor.string("executionProfileVersionId")),
                        metricFamily = MetricFamily.fromStorage(cursor.string("metricFamily")),
                        laterality = Laterality.fromStorage(cursor.string("side")),
                        completedAt = completedAt,
                        metricValues = metricsByObservation[observationId].orEmpty(),
                        bodyMassContextKg = cursor.nullableDouble("observationBodyMassContextKg")
                            ?: cursor.nullableDouble("sessionBodyMassSnapshotKg"),
                        warmUp = cursor.int("warmUp") != 0,
                        kind = cursor.string("kind"),
                        observationSource = observationSource,
                        sessionId = cursor.string("sessionId"),
                    ),
                    recordedAt = DynamicHistoricalAvailabilityV2.resolve(
                        observationSource = observationSource,
                        nativeRecordedAt = nativeRecordedAt,
                        sessionCompletedAt = sessionCompletedAt,
                        sessionEditedAt = sessionEditedAt,
                    ),
                    sessionCompletedAt = sessionCompletedAt,
                    supersedesObservationId = cursor.nullableString("supersedesObservationId"),
                )
            }
        }

        val profiles = linkedMapOf<String, NBio7BProfileDescriptor>()
        sqlite.query(
            """
            SELECT epv.id AS executionProfileVersionId, epv.executionProfileId AS executionProfileId,
                   e.name AS exerciseName, eep.name AS profileName, epv.metricFamily AS metricFamily,
                   epv.resistanceSemantics AS resistanceSemantics,
                   epv.resistanceModelVersion AS resistanceModelVersion,
                   epv.bodyweightCoefficient AS bodyweightCoefficient,
                   epv.externalLoadCoefficient AS externalLoadCoefficient,
                   epv.assistanceCoefficient AS assistanceCoefficient,
                   epv.entryBasis AS entryBasis, epv.lateralityMode AS lateralityMode
            FROM execution_profile_version AS epv
            INNER JOIN exercise_execution_profile AS eep ON eep.id = epv.executionProfileId
            INNER JOIN exercise AS e ON e.id = eep.exerciseId
            WHERE epv.metricFamily IN ('dynamic_resistance', 'bodyweight_resistance')
            ORDER BY e.name, eep.name, epv.version, epv.id
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val versionId = cursor.string("executionProfileVersionId")
                profiles[versionId] = NBio7BProfileDescriptor(
                    semantics = DynamicResistanceProfileSemantics(
                        executionProfileVersionId = ExecutionProfileVersionId(versionId),
                        executionProfileId = ExecutionProfileId(cursor.string("executionProfileId")),
                        metricFamily = MetricFamily.fromStorage(cursor.string("metricFamily")),
                        resistanceModel = ResistanceModel(
                            modelVersion = cursor.string("resistanceModelVersion"),
                            semantics = ResistanceSemantics.entries.firstOrNull {
                                it.storageValue == cursor.string("resistanceSemantics")
                            } ?: error("Unsupported resistance semantics ${cursor.string("resistanceSemantics")}"),
                            bodyweightCoefficient = cursor.double("bodyweightCoefficient"),
                            externalLoadCoefficient = cursor.double("externalLoadCoefficient"),
                            assistanceCoefficient = cursor.double("assistanceCoefficient"),
                        ),
                        entryBasis = EntryBasis.fromStorage(cursor.string("entryBasis")),
                        lateralityMode = LateralityMode.entries.firstOrNull {
                            it.storageValue == cursor.string("lateralityMode")
                        } ?: error("Unsupported laterality mode ${cursor.string("lateralityMode")}"),
                    ),
                    exerciseName = cursor.string("exerciseName"),
                    profileName = cursor.string("profileName"),
                )
            }
        }
        return NBio7BRawHistory(revisions = revisions, profiles = profiles)
    }
}

/**
 * Fingerprints canonical raw workout/performance tables before and after disposable candidate work.
 * Text values (including any legacy note columns) are hashed only; they are never returned/exported.
 */
object NBio7BRawEvidenceFingerprinter {
    private val tableQueries = linkedMapOf(
        "session" to "SELECT * FROM session ORDER BY id",
        "session_exercise" to "SELECT * FROM session_exercise ORDER BY id",
        "set_record" to "SELECT * FROM set_record ORDER BY id",
        "set_observation" to "SELECT * FROM set_observation ORDER BY id",
        "set_metric_value" to "SELECT * FROM set_metric_value ORDER BY observationId, metric",
    )

    fun capture(database: MyMettleDatabase): NBio7BRawEvidenceFingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        val counts = linkedMapOf<String, Long>()
        val sqlite = database.openHelper.readableDatabase
        tableQueries.forEach { (table, query) ->
            digest.text("table:$table\n")
            var count = 0L
            sqlite.query(query).use { cursor ->
                cursor.columnNames.forEach { digest.text("column:$it\n") }
                while (cursor.moveToNext()) {
                    count += 1
                    digest.text("row\n")
                    for (column in 0 until cursor.columnCount) {
                        digest.update(byteArrayOf(cursor.getType(column).toByte()))
                        when (cursor.getType(column)) {
                            Cursor.FIELD_TYPE_NULL -> Unit
                            Cursor.FIELD_TYPE_INTEGER -> digest.update(ByteBuffer.allocate(8).putLong(cursor.getLong(column)).array())
                            Cursor.FIELD_TYPE_FLOAT -> digest.update(ByteBuffer.allocate(8).putDouble(cursor.getDouble(column)).array())
                            Cursor.FIELD_TYPE_STRING -> digest.text(cursor.getString(column))
                            Cursor.FIELD_TYPE_BLOB -> digest.update(cursor.getBlob(column))
                            else -> error("Unsupported SQLite cursor type ${cursor.getType(column)}")
                        }
                        digest.update(byteArrayOf(0))
                    }
                }
            }
            counts[table] = count
            digest.text("count:$count\n")
        }
        return NBio7BRawEvidenceFingerprint(
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            tableRowCounts = counts,
        )
    }

    private fun MessageDigest.text(value: String) {
        update(value.toByteArray(StandardCharsets.UTF_8))
    }
}

private fun Cursor.index(name: String): Int = getColumnIndexOrThrow(name)
private fun Cursor.string(name: String): String = getString(index(name))
private fun Cursor.nullableString(name: String): String? = index(name).let { if (isNull(it)) null else getString(it) }
private fun Cursor.double(name: String): Double = getDouble(index(name))
private fun Cursor.nullableDouble(name: String): Double? = index(name).let { if (isNull(it)) null else getDouble(it) }
private fun Cursor.int(name: String): Int = getInt(index(name))
