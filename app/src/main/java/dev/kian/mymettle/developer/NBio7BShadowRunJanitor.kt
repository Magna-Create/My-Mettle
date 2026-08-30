package dev.kian.mymettle.developer

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.inference.DynamicCapabilityShadowRepository

/**
 * Keeps explicit 7B acceptance runs from accumulating large disposable parameter blobs forever.
 *
 * Only N-BIO-7B SHADOW inference runs are deleted. The FK graph cascades from inference_run into
 * capability_state / capability_parameter_state; canonical workout evidence and BENCHMARK_V0 runs
 * are not referenced by this predicate and therefore cannot be removed here.
 */
object NBio7BShadowRunJanitor {
    suspend fun prunePreviousAcceptanceRuns(database: MyMettleDatabase): Int = database.withTransaction {
        val sqlite = database.openHelper.writableDatabase
        val ids = sqlite.query(
            "SELECT id FROM inference_run WHERE executionMode = 'shadow' AND modelVersion = ? ORDER BY calculatedAt, id",
            arrayOf(DynamicCapabilityShadowRepository.SHADOW_RUN_MODEL_VERSION),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        val dao = database.inferenceDao()
        ids.forEach { dao.deleteInferenceRun(it) }
        ids.size
    }
}
