package dev.kian.mymettle.data.migration

import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.MuscleSegmentEntity

/**
 * One explicit compatibility seam for the pre-Native free-text muscle model.
 *
 * Broad Legacy labels are expanded through a versioned alias table and their old proportional
 * weighting is divided across the resolved stable segments. This is migration provenance, not the
 * final exercise-recruitment dataset; later recruitment research can replace it without touching
 * canonical anatomy.
 */
class LegacyRecruitmentResolver(private val database: MyMettleDatabase) {
    suspend fun resolve(values: List<LegacyRecruitmentAllocation>): List<RecruitmentAllocationEntity> {
        if (values.isEmpty()) return emptyList()

        val muscles = database.referenceDao().muscles()
        val segments = database.referenceDao().segments()
        val segmentById = segments.associateBy { it.id }
        val segmentsByMuscle = segments.groupBy { it.muscleId }
        val labels = mutableMapOf<String, List<String>>()

        segments.forEach { segment ->
            labels[normalise(segment.id)] = listOf(segment.id)
            val muscle = muscles.first { it.id == segment.muscleId }
            labels[normalise("${muscle.name} ${segment.name}")] = listOf(segment.id)
        }
        muscles.forEach { muscle ->
            val children = segmentsByMuscle.getValue(muscle.id).stateTargets()
            labels[normalise(muscle.id)] = children.map { it.id }
            labels[normalise(muscle.name)] = children.map { it.id }
        }

        LEGACY_ALIASES.forEach { (label, ids) ->
            val unknown = ids.filterNot(segmentById::containsKey)
            require(unknown.isEmpty()) { "Legacy recruitment alias '$label' references unknown segments: $unknown" }
            labels[normalise(label)] = ids
        }

        val unresolved = values.map { it.muscleLabel }.distinct().filter { normalise(it) !in labels }
        if (unresolved.isNotEmpty()) {
            throw LegacyImportException(
                "Legacy recruitment contains muscle labels that cannot be mapped safely to stable anatomy: ${unresolved.joinToString()}. " +
                    "Update the explicit Legacy alias map rather than persisting new free text.",
            )
        }

        data class Accumulator(
            var weighting: Double,
            var role: String,
            var confidence: Double,
            val sources: MutableSet<String>,
        )

        val accumulated = linkedMapOf<Pair<String, String>, Accumulator>()
        values.forEach { value ->
            val targetIds = labels.getValue(normalise(value.muscleLabel))
            val dividedWeighting = value.weighting / targetIds.size
            targetIds.forEach { segmentId ->
                val key = value.executionProfileId to segmentId
                val provenance = buildString {
                    value.source?.takeIf { it.isNotBlank() }?.let(::append)
                    if (targetIds.size > 1) {
                        if (isNotEmpty()) append("; ")
                        append("LEGACY_ALIAS_EXPANSION_V1[")
                        append(value.muscleLabel)
                        append(']')
                    }
                }
                val current = accumulated[key]
                if (current == null) {
                    accumulated[key] = Accumulator(
                        weighting = dividedWeighting,
                        role = value.role,
                        confidence = value.confidence,
                        sources = mutableSetOf<String>().apply { if (provenance.isNotBlank()) add(provenance) },
                    )
                } else {
                    current.weighting += dividedWeighting
                    current.role = strongerRole(current.role, value.role)
                    current.confidence = maxOf(current.confidence, value.confidence)
                    if (provenance.isNotBlank()) current.sources += provenance
                }
            }
        }

        return accumulated.map { (key, value) ->
            RecruitmentAllocationEntity(
                executionProfileId = key.first,
                muscleSegmentId = key.second,
                role = value.role,
                weighting = value.weighting,
                confidence = value.confidence,
                source = value.sources.takeIf { it.isNotEmpty() }?.joinToString(" | "),
            )
        }
    }
}

private fun List<MuscleSegmentEntity>.stateTargets(): List<MuscleSegmentEntity> {
    val independent = filter { it.statePolicy == "TRACK" || it.statePolicy == "PROVISIONAL_TRACK" }
    return independent.ifEmpty { this }
}

private fun normalise(value: String): String = value
    .lowercase()
    .replace("&", " and ")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun strongerRole(first: String, second: String): String {
    val rank = mapOf("stabiliser" to 0, "synergist" to 1, "prime" to 2)
    return if ((rank[second] ?: -1) > (rank[first] ?: -1)) second else first
}

private val LEGACY_ALIASES = mapOf(
    "upper chest" to listOf("pectoralis_major_clavicular_part"),
    "front delt" to listOf("deltoid_clavicular_part"),
    "front delts" to listOf("deltoid_clavicular_part"),
    "side delt" to listOf("deltoid_acromial_part"),
    "side delts" to listOf("deltoid_acromial_part"),
    "lateral delt" to listOf("deltoid_acromial_part"),
    "lateral delts" to listOf("deltoid_acromial_part"),
    "rear delt" to listOf("deltoid_spinal_part"),
    "rear delts" to listOf("deltoid_spinal_part"),
    "posterior delt" to listOf("deltoid_spinal_part"),
    "posterior delts" to listOf("deltoid_spinal_part"),
    "delts" to listOf("deltoid_clavicular_part", "deltoid_acromial_part", "deltoid_spinal_part"),
    "biceps" to listOf("biceps_brachii_long_head", "biceps_brachii_short_head"),
    "triceps" to listOf("triceps_brachii_long_head", "triceps_brachii_lateral_head", "triceps_brachii_medial_head"),
    "lats" to listOf("latissimus_dorsi_whole"),
    "upper back" to listOf(
        "trapezius_descending_part",
        "trapezius_transverse_part",
        "trapezius_ascending_part",
        "rhomboid_major_whole",
        "rhomboid_minor_whole",
    ),
    "quads" to listOf(
        "rectus_femoris_whole",
        "vastus_lateralis_whole",
        "vastus_medialis_whole",
        "vastus_intermedius_whole",
    ),
    "glutes" to listOf(
        "gluteus_maximus_whole",
        "gluteus_medius_anterior_region",
        "gluteus_medius_middle_region",
        "gluteus_medius_posterior_region",
        "gluteus_minimus_whole",
    ),
)
