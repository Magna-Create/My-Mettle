package dev.kian.mymettle.workout

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A compact, explainable summary for the Daily Brief.
 *
 * This is session guidance rather than a meal plan. Protein uses the commonly recommended
 * 0.25-0.40 g/kg serving range (0.30 g/kg as the displayed target), while carbohydrate and water
 * are deliberately conservative workload bands. The latter should become sweat-rate and dietary-
 * context aware once those inputs exist.
 */
data class DailyBriefGuidance(
    val workingSets: Int,
    val estimatedMinutes: Int,
    val emphasis: String,
    val proteinBefore: String,
    val proteinAfter: String,
    val carbohydratesBefore: String,
    val carbohydratesAfter: String,
    val waterDuring: String,
    val isWeightAware: Boolean,
)

data class DailyBriefSessionProfile(
    val workingSets: Int,
    val estimatedDurationSeconds: Int,
    val bodyweightKg: Double?,
    val targetSegments: List<String>,
)

fun dailyBriefGuidance(profile: DailyBriefSessionProfile): DailyBriefGuidance {
    val minutes = ceil(profile.estimatedDurationSeconds.coerceAtLeast(60) / 60.0)
        .toInt()
        .coerceAtLeast(1)
    val demand = when {
        profile.workingSets >= 16 || minutes >= 75 -> SessionDemand.HIGH
        profile.workingSets >= 11 || minutes >= 50 -> SessionDemand.MODERATE
        else -> SessionDemand.LIGHT
    }
    val validBodyweight = profile.bodyweightKg?.takeIf { it in 35.0..250.0 }
    val protein = validBodyweight
        ?.let { roundToFive((it * 0.30).coerceIn(20.0, 40.0)).toString() }
        ?: "20–40"
    val carbohydrates = if (validBodyweight == null) {
        demand.fallbackCarbohydrates
    } else {
        roundToFive(
            (validBodyweight * demand.carbohydrateGramsPerKg)
                .coerceIn(demand.minimumCarbohydrates.toDouble(), 100.0),
        ).toString()
    }

    return DailyBriefGuidance(
        workingSets = profile.workingSets,
        estimatedMinutes = minutes,
        emphasis = profile.targetSegments
            .asSequence()
            .map(::muscleGroupLabel)
            .distinct()
            .take(2)
            .toList()
            .joinToString(" + ")
            .ifBlank { "Full body" },
        proteinBefore = protein,
        proteinAfter = protein,
        carbohydratesBefore = carbohydrates,
        carbohydratesAfter = carbohydrates,
        waterDuring = demand.waterDuring,
        isWeightAware = validBodyweight != null,
    )
}

fun NativeWorkoutPlan.dailyBriefGuidance(bodyweightKg: Double?): DailyBriefGuidance =
    dailyBriefGuidance(
        DailyBriefSessionProfile(
            workingSets = workingSetCount,
            estimatedDurationSeconds = estimatedDurationSeconds,
            bodyweightKg = bodyweightKg,
            targetSegments = targetResolutions
                .asSequence()
                .filter { it.included }
                .sortedByDescending { it.resolvedPriority }
                .map { it.target.segmentId.value }
                .toList(),
        ),
    )

fun ActiveWorkout.dailyBriefGuidance(): DailyBriefGuidance {
    val included = exercises.filter { it.entity.prescriptionIncluded }
    val estimatedSeconds = included.sumOf { exercise ->
        exercise.entity.prescribedSets * (35 + exercise.entity.restSeconds)
    }
    val targetPriorityById = targets.associate { it.id to it.priority }
    val targetSegmentsById = targets.associate { it.id to it.segmentId.value }
    val orderedSegments = included
        .asSequence()
        .flatMap { exercise -> exercise.targetIds.asSequence() }
        .distinct()
        .sortedByDescending { targetPriorityById[it] ?: 0.0 }
        .mapNotNull { targetSegmentsById[it] }
        .toList()

    return dailyBriefGuidance(
        DailyBriefSessionProfile(
            workingSets = included.sumOf { it.entity.prescribedSets },
            estimatedDurationSeconds = estimatedSeconds,
            bodyweightKg = session.bodyweightSnapshotKg,
            targetSegments = orderedSegments,
        ),
    )
}

private enum class SessionDemand(
    val carbohydrateGramsPerKg: Double,
    val minimumCarbohydrates: Int,
    val fallbackCarbohydrates: String,
    val waterDuring: String,
) {
    LIGHT(0.50, 25, "25–40", "400–600"),
    MODERATE(0.75, 35, "40–60", "500–700"),
    HIGH(1.00, 45, "60–80", "600–800"),
}

private fun roundToFive(value: Double): Int = (value / 5.0).roundToInt() * 5

private fun muscleGroupLabel(segmentId: String): String {
    val id = segmentId.lowercase()
    return when {
        id.contains("rectus_femoris") || id.contains("vastus_") -> "Quads"
        id.contains("gluteus") -> "Glutes"
        id.contains("biceps_femoris") || id.contains("semitendinosus") ||
            id.contains("semimembranosus") -> "Hamstrings"
        id.contains("gastrocnemius") || id.contains("soleus") -> "Calves"
        id.contains("adductor") || id.contains("gracilis") -> "Adductors"
        id.contains("pectoralis") -> "Chest"
        id.contains("latissimus") || id.contains("teres_major") -> "Lats"
        id.contains("deltoid") -> "Shoulders"
        id.contains("triceps") || id.contains("anconeus") -> "Triceps"
        id.contains("biceps") || id.contains("brachialis") || id.contains("brachioradialis") -> "Biceps"
        id.contains("trapezius") || id.contains("rhomboid") || id.contains("levator_scapulae") -> "Upper back"
        id.contains("abdominis") || id.contains("oblique") -> "Core"
        id.contains("erector_spinae") || id.contains("multifidus") -> "Back"
        else -> segmentId
            .removeSuffix("_whole")
            .substringBefore("_part")
            .split('_')
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }
}
