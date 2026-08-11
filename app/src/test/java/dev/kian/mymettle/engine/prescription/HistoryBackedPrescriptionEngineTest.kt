package dev.kian.mymettle.engine.prescription

import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.exercise.LoadResolution
import dev.kian.mymettle.domain.training.TrainingTargetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryBackedPrescriptionEngineTest {
    private val engine = HistoryBackedPrescriptionEngine()

    @Test
    fun `generates a target-linked prescription and conforms prior load to equipment values`() {
        val result = engine.generate(
            request(
                previousLoad = 8.6,
                loadResolution = LoadResolution(
                    minimumLoad = 5.0,
                    maximumLoad = 20.0,
                    increment = null,
                    allowedValues = listOf(5.0, 7.5, 10.0, 12.5),
                ),
            ),
        )

        assertEquals(7.5, result.prescribedLoad)
        assertEquals(listOf(TrainingTargetId("target_chest")), result.targetIds)
        assertEquals(3, result.sets)
        assertEquals(8..12, result.repRange)
        assertEquals(HistoryBackedPrescriptionEngine.MODEL_VERSION, result.generatedByModelVersion)
    }

    @Test
    fun `leaves load null when the selected execution has no usable load evidence`() {
        assertNull(engine.generate(request(previousLoad = null)).prescribedLoad)
        assertNull(engine.generate(request(previousLoad = 42.0, permitsExternalLoad = false)).prescribedLoad)
    }

    @Test
    fun `snaps carried evidence from the execution profile minimum`() {
        val result = engine.generate(
            request(
                previousLoad = 9.0,
                loadResolution = LoadResolution(
                    minimumLoad = 5.0,
                    maximumLoad = 15.0,
                    increment = 2.5,
                    allowedValues = emptyList(),
                ),
            ),
        )

        assertEquals(10.0, result.prescribedLoad)
    }

    private fun request(
        previousLoad: Double?,
        permitsExternalLoad: Boolean = true,
        loadResolution: LoadResolution? = null,
    ) = PrescriptionRequest(
        exerciseId = ExerciseId("exercise_press"),
        executionProfileId = ExecutionProfileId("execution_press_default"),
        targetIds = listOf(TrainingTargetId("target_chest")),
        sets = 3,
        repRange = 8..12,
        targetRir = null,
        previousPerformedLoad = previousLoad,
        permitsExternalLoad = permitsExternalLoad,
        loadResolution = loadResolution,
        restSeconds = 120,
    )
}
