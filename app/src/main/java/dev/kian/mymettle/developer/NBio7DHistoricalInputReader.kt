package dev.kian.mymettle.developer

import android.database.Cursor
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.inference.MuscleExposure
import java.time.Instant

/** Exact Room14 context needed to turn canonical 7B/7C evidence into a causal 7D replay. */
data class NBio7DHistoricalSession(
    val sessionId: String,
    val startedAt: Instant,
    val completedAt: Instant,
)

data class NBio7DObservationContext(
    val observationId: String,
    val sessionId: String,
    val executionProfileVersionId: String,
    val recruitmentProfileVersionId: String,
    val side: String,
    val completedAt: Instant,
    val exposures: List<MuscleExposure>,
)

data class NBio7DHistoricalInputs(
    val sessions: Map<String, NBio7DHistoricalSession>,
    val observations: Map<String, NBio7DObservationContext>,
)

/**
 * Reads no notes, reflections, health context or mutable drafts. Exposure is the exact historical
 * recruitment allocation attached to the observation's execution-profile version; no current-profile
 * reinterpretation, role multiplier, normalisation, or conservation step is permitted.
 */
class NBio7DHistoricalInputReader(private val database: MyMettleDatabase) {
    fun read(): NBio7DHistoricalInputs {
        val sqlite = database.openHelper.readableDatabase
        val familySql = "'dynamic_resistance','bodyweight_resistance','loaded_hold','duration_only','repeated_contraction'"
        val sessions = linkedMapOf<String, NBio7DHistoricalSession>()
        sqlite.query(
            """
            SELECT DISTINCT s.id AS sessionId, s.startedAt, s.completedAt
            FROM session AS s
            INNER JOIN session_exercise AS se ON se.sessionId = s.id
            INNER JOIN set_record AS sr ON sr.sessionExerciseId = se.id
            INNER JOIN set_observation AS so ON so.setRecordId = sr.id
            INNER JOIN execution_profile_version AS epv ON epv.id = so.executionProfileVersionId
            WHERE s.status = 'completed' AND s.excludedFromInsights = 0
              AND s.completedAt IS NOT NULL
              AND epv.metricFamily IN ($familySql)
            ORDER BY s.startedAt, s.id
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.string7d("sessionId")
                sessions[id] = NBio7DHistoricalSession(
                    sessionId = id,
                    startedAt = Instant.parse(cursor.string7d("startedAt")),
                    completedAt = Instant.parse(cursor.string7d("completedAt")),
                )
            }
        }

        data class ObservationBase(
            val observationId: String,
            val sessionId: String,
            val executionProfileVersionId: String,
            val recruitmentProfileVersionId: String,
            val side: String,
            val completedAt: Instant,
        )
        val bases = linkedMapOf<String, ObservationBase>()
        sqlite.query(
            """
            SELECT so.id AS observationId, s.id AS sessionId, so.executionProfileVersionId,
                   epv.recruitmentProfileVersionId, so.side, so.completedAt
            FROM set_observation AS so
            INNER JOIN set_record AS sr ON sr.id = so.setRecordId
            INNER JOIN session_exercise AS se ON se.id = sr.sessionExerciseId
            INNER JOIN session AS s ON s.id = se.sessionId
            INNER JOIN execution_profile_version AS epv ON epv.id = so.executionProfileVersionId
            WHERE s.status = 'completed' AND s.excludedFromInsights = 0
              AND epv.metricFamily IN ($familySql)
            ORDER BY so.completedAt, so.id
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.string7d("observationId")
                bases[id] = ObservationBase(
                    observationId = id,
                    sessionId = cursor.string7d("sessionId"),
                    executionProfileVersionId = cursor.string7d("executionProfileVersionId"),
                    recruitmentProfileVersionId = cursor.string7d("recruitmentProfileVersionId"),
                    side = cursor.string7d("side"),
                    completedAt = Instant.parse(cursor.string7d("completedAt")),
                )
            }
        }

        val allocations = linkedMapOf<String, MutableList<Pair<String, Double>>>()
        sqlite.query(
            """
            SELECT recruitmentProfileVersionId, muscleSegmentId, weighting
            FROM recruitment_allocation
            ORDER BY recruitmentProfileVersionId, muscleSegmentId
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                allocations.getOrPut(cursor.string7d("recruitmentProfileVersionId"), ::mutableListOf) +=
                    cursor.string7d("muscleSegmentId") to cursor.double7d("weighting")
            }
        }

        val observations = bases.mapValues { (_, base) ->
            val historicalAllocations = allocations[base.recruitmentProfileVersionId].orEmpty()
            NBio7DObservationContext(
                observationId = base.observationId,
                sessionId = base.sessionId,
                executionProfileVersionId = base.executionProfileVersionId,
                recruitmentProfileVersionId = base.recruitmentProfileVersionId,
                side = base.side,
                completedAt = base.completedAt,
                exposures = historicalAllocations.map { (segmentId, weighting) ->
                    MuscleExposure(
                        muscleSegmentId = segmentId,
                        side = base.side,
                        recruitmentWeight = weighting,
                        historicalRecruitmentProfileVersionId = base.recruitmentProfileVersionId,
                    )
                },
            )
        }
        return NBio7DHistoricalInputs(sessions = sessions, observations = observations)
    }
}

private fun Cursor.index7d(name: String): Int = getColumnIndexOrThrow(name)
private fun Cursor.string7d(name: String): String = getString(index7d(name))
private fun Cursor.double7d(name: String): Double = getDouble(index7d(name))
