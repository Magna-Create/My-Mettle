package dev.kian.mymettle.workout

import kotlin.math.ceil
import kotlin.math.roundToInt

private const val DailyProteinGramsPerKg = 1.60
private const val ProteinServingGramsPerKg = 0.25

/**
 * A compact, explainable summary for the Daily Brief.
 *
 * This is compact training-day guidance rather than a meal plan. The primary protein number is a
 * daily target of 1.6 g/kg; the smaller distribution hint uses 0.25 g/kg per meal. Carbohydrate is
 * an optional pre-session amount, not a mandatory mirrored pre/post dose. It scales conservatively
 * with session demand because fasting state, previous meals and total daily energy are not yet
 * represented in the profile.
 */
data class DailyBriefGuidance(
    val workingSets: Int,
    val estimatedMinutes: Int,
    val emphasis: String,
    val proteinDaily: String,
    val proteinPerMeal: String,
    val carbohydratesBefore: String,
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
    val proteinDaily = validBodyweight
        ?.let { roundToFive(it * DailyProteinGramsPerKg).toString() }
        ?: "—"
    val proteinPerMeal = validBodyweight
        ?.let { roundToFive((it * ProteinServingGramsPerKg).coerceIn(20.0, 40.0)).toString() }
        ?: "20–40"
    val carbohydrates = if (validBodyweight == null) {
        demand.fallbackCarbohydrates.toString()
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
        proteinDaily = proteinDaily,
        proteinPerMeal = proteinPerMeal,
        carbohydratesBefore = carbohydrates,
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
    val fallbackCarbohydrates: Int,
    val waterDuring: String,
) {
    // Acute carbohydrate benefit is context-dependent in resistance training. These deliberately
    // small optional amounts are a usability heuristic, not a claim that a fed session requires a
    // pre-workout supplement or a matching post-workout bolus.
    LIGHT(0.30, 15, 20, "500"),
    MODERATE(0.40, 20, 30, "600"),
    HIGH(0.50, 25, 40, "700"),
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
