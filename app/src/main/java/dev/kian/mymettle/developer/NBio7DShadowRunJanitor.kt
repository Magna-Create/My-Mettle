package dev.kian.mymettle.developer

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.inference.NBio7DShadowRepository
import java.time.Instant

/**
 * Invalidates only derived N-BIO-7D SHADOW runs at/after the earliest session affected by a
 * correction or supersession.
 *
 * A corrected historical set can change that session's SetDemand (the performed observation
 * changed) and every later pre-session capability posterior that learns from it. Therefore replay
 * begins at the original affected session horizon, not at the time the edit happened. Canonical
 * workout/performance evidence is never deleted by this janitor.
 */
object NBio7DShadowRunJanitor {
    suspend fun invalidateFrom(
        database: MyMettleDatabase,
        earliestAffectedSessionCompletedAt: Instant,
    ): List<String> = database.withTransaction {
        val sqlite = database.openHelper.writableDatabase
        val ids = sqlite.query(
            """
            SELECT id
            FROM inference_run
            WHERE executionMode = 'shadow'
              AND modelVersion = ?
              AND evidenceThrough IS NOT NULL
              AND evidenceThrough >= ?
            ORDER BY evidenceThrough, id
            """.trimIndent(),
            arrayOf(
                NBio7DShadowRepository.SHADOW_RUN_MODEL_VERSION,
                earliestAffectedSessionCompletedAt.toString(),
            ),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        val dao = database.inferenceDao()
        ids.forEach { dao.deleteInferenceRun(it) }
        ids
    }

    suspend fun pruneAllDisposable7D(database: MyMettleDatabase): List<String> = database.withTransaction {
        val sqlite = database.openHelper.writableDatabase
        val ids = sqlite.query(
            """
            SELECT id
            FROM inference_run
            WHERE executionMode = 'shadow' AND modelVersion = ?
            ORDER BY calculatedAt, id
            """.trimIndent(),
            arrayOf(NBio7DShadowRepository.SHADOW_RUN_MODEL_VERSION),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        val dao = database.inferenceDao()
        ids.forEach { dao.deleteInferenceRun(it) }
        ids
    }
}
