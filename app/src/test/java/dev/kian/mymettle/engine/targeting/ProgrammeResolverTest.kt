package dev.kian.mymettle.engine.targeting

import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.performance.PerformanceTargetTemplate
import dev.kian.mymettle.domain.training.SessionConstraints
import dev.kian.mymettle.domain.training.TargetSource
import dev.kian.mymettle.domain.training.TrainingTarget
import dev.kian.mymettle.domain.training.TrainingTargetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgrammeResolverTest {
    private val targetResolver = ConstraintTargetResolver()
    private val exerciseSelector = BudgetedTargetExerciseSelector()

    @Test
    fun `busy budget selects fewer complete movements rather than one set everywhere`() {
        val targets = listOf(
            target("chest", 1.0),
            target("triceps", 0.7),
            target("side_delts", 0.4),
        )
        val constraints = constraints(
            workingSetBudget = 8,
            exerciseBudget = 4,
            minimumSetsPerExercise = 2,
            targetPriorityFloor = 0.7,
        )
        val resolved = targetResolver.resolve(targets, constraints)
        val candidates = listOf(
            candidate("press", 0, 1.0, "chest", "triceps"),
            candidate("fly", 1, 0.7, "chest"),
            candidate("dip", 2, 0.7, "triceps", "chest"),
            candidate("pushdown", 3, 0.7, "triceps"),
            candidate("lateral_raise", 4, 0.4, "side_delts"),
            candidate("machine_lateral", 5, 0.4, "side_delts"),
        )

        val selected = exerciseSelector.select(resolved, candidates, constraints)

        assertEquals(4, selected.size)
        assertEquals(8, selected.sumOf { it.sets })
        assertTrue(selected.all { it.sets >= 2 })
        assertTrue(selected.none { it.candidate.preferenceId.contains("lateral") })
    }

    @Test
    fun `time budget stops allocation before the set budget is exhausted`() {
        val targets = listOf(target("chest", 1.0))
        val constraints = constraints(
            workingSetBudget = 6,
            exerciseBudget = 3,
            minimumSetsPerExercise = 2,
            targetPriorityFloor = 0.0,
            timeBudgetSeconds = 300,
        )
        val selected = exerciseSelector.select(
            targets = targetResolver.resolve(targets, constraints),
            candidates = listOf(candidate("press", 0, 1.0, "chest")),
            constraints = constraints,
        )

        assertEquals(2, selected.single().sets)
        assertTrue(selected.single().estimatedDurationSeconds <= 300)
    }

    @Test
    fun `unresolved legacy preferences remain an explicit fallback`() {
        val constraints = constraints(4, 2, 2, 0.0)
        val selected = exerciseSelector.select(
            targets = emptyList(),
            candidates = listOf(
                candidate("first", 0, 1.0),
                candidate("second", 1, 0.7),
                candidate("third", 2, 0.4),
            ),
            constraints = constraints,
        )

        assertEquals(listOf("first", "second"), selected.map { it.candidate.preferenceId })
        assertTrue(selected.all { it.reason == BudgetedTargetExerciseSelector.UNRESOLVED_PREFERENCE_FALLBACK })
    }

    @Test
    fun `partially unresolved programme preserves eligible pinned work without restoring accessories`() {
        val constraints = constraints(4, 2, 2, 0.7)
        val selected = exerciseSelector.select(
            targets = targetResolver.resolve(listOf(target("chest", 1.0)), constraints),
            candidates = listOf(
                candidate("press", 0, 1.0, "chest"),
                candidate("unresolved_core", 1, 0.7),
                candidate("unresolved_accessory", 2, 0.4),
            ),
            constraints = constraints,
        )

        assertEquals(listOf("press", "unresolved_core"), selected.map { it.candidate.preferenceId })
        assertEquals(BudgetedTargetExerciseSelector.TARGET_COVERAGE, selected.first().reason)
        assertEquals(BudgetedTargetExerciseSelector.UNRESOLVED_PREFERENCE_FALLBACK, selected.last().reason)
    }

    private fun target(name: String, priority: Double) = TrainingTarget(
        id = TrainingTargetId("target_$name"),
        segmentId = MuscleSegmentId("segment_$name"),
        priority = priority,
        desiredStimulus = null,
        source = TargetSource("test"),
    )

    private fun candidate(
        name: String,
        ordinal: Int,
        priority: Double,
        vararg targetNames: String,
    ) = ExerciseSelectionCandidate(
        preferenceId = name,
        exerciseId = ExerciseId("exercise_$name"),
        executionProfileId = ExecutionProfileId("execution_$name"),
        executionProfileVersionId = ExecutionProfileVersionId("execution_$name:v1"),
        ordinal = ordinal,
        preferencePriority = priority,
        preferredSetCap = 3,
        preferredTemplate = PerformanceTargetTemplate(emptyList()),
        restSeconds = 120,
        targetCoverage = targetNames.associate { TrainingTargetId("target_$it") to 1.0 },
    )

    private fun constraints(
        workingSetBudget: Int,
        exerciseBudget: Int,
        minimumSetsPerExercise: Int,
        targetPriorityFloor: Double,
        timeBudgetSeconds: Int? = null,
    ) = SessionConstraints(
        mode = "test",
        workingSetBudget = workingSetBudget,
        exerciseBudget = exerciseBudget,
        minimumSetsPerExercise = minimumSetsPerExercise,
        targetPriorityFloor = targetPriorityFloor,
        timeBudgetSeconds = timeBudgetSeconds,
        source = "test",
        resolverModelVersion = BudgetedTargetExerciseSelector.MODEL_VERSION,
    )
}
