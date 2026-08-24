package dev.kian.mymettle.domain.exercise

import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceSchema
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId

@JvmInline
value class ExerciseId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExerciseId cannot be blank." }
    }
}

@JvmInline
value class ExecutionProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExecutionProfileId cannot be blank." }
    }
}

@JvmInline
value class ExecutionProfileVersionId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExecutionProfileVersionId cannot be blank." }
    }
}

@JvmInline
value class RecruitmentProfileVersionId(val value: String) {
    init {
        require(value.isNotBlank()) { "RecruitmentProfileVersionId cannot be blank." }
    }
}

enum class EntryBasis(val storageValue: String) {
    TOTAL("total"),
    PER_HAND("per_hand"),
    PER_SIDE("per_side");

    companion object {
        fun fromStorage(value: String): EntryBasis = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported entry basis: $value")
    }
}

data class LoadResolution(
    val minimumLoad: Double?,
    val maximumLoad: Double?,
    val increment: Double?,
    val allowedValues: List<Double>,
)

data class EquipmentProfile(
    val identity: String?,
    val type: String?,
)

enum class RecruitmentRole(val storageValue: String) {
    PRIME("prime"),
    SYNERGIST("synergist"),
    STABILISER("stabiliser");

    companion object {
        fun fromStorage(value: String): RecruitmentRole = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported recruitment role: $value")
    }
}

data class RecruitmentSource(
    val provenanceType: String,
    val reference: String?,
)

data class RecruitmentAllocation(
    val segmentId: MuscleSegmentId,
    val segmentName: String,
    val role: RecruitmentRole,
    val weighting: Double,
    val confidence: Double,
    val source: RecruitmentSource?,
    val applicableRom: String?,
    val applicableTechnique: String?,
    val resistanceCurveClass: String?,
    val modelVersion: String,
) {
    init {
        require(weighting in 0.0..1.0) { "Recruitment weighting is a muscle-local coefficient in [0,1]." }
        require(confidence in 0.0..1.0)
        require(modelVersion.isNotBlank())
    }
}

data class RecruitmentProfile(
    val id: RecruitmentProfileVersionId,
    val version: Int,
    val allocations: List<RecruitmentAllocation>,
    val createdAt: String,
    val effectiveAt: String,
    val supersededAt: String?,
    val provenance: String,
    val modelVersion: String,
) {
    init {
        require(version > 0)
        require(allocations.map { it.segmentId }.distinct().size == allocations.size)
        require(provenance.isNotBlank() && modelVersion.isNotBlank())
    }
}

/** Immutable semantics used by historical observations and session prescriptions. */
data class ExecutionProfileVersion(
    val id: ExecutionProfileVersionId,
    val executionProfileId: ExecutionProfileId,
    val version: Int,
    val metricFamily: MetricFamily,
    val schema: PerformanceSchema,
    val equipment: EquipmentProfile,
    val resistanceModel: ResistanceModel,
    val entryBasis: EntryBasis,
    val implementCount: Int?,
    val lateralityMode: LateralityMode,
    val romClass: String?,
    val techniqueClass: String?,
    val resistanceCurveClass: String?,
    val movementPattern: String?,
    val jointActions: List<String>,
    val kineticChain: String?,
    val contractionType: String?,
    val gripSupportConstraints: List<String>,
    val recruitment: RecruitmentProfile,
    val createdAt: String,
    val effectiveAt: String,
    val supersededAt: String?,
    val provenance: String,
    val modelVersion: String,
) {
    init {
        require(version > 0)
        require(schema.family == metricFamily)
        require(implementCount == null || implementCount > 0)
        require(provenance.isNotBlank() && modelVersion.isNotBlank())
    }

    val loadResolution: LoadResolution?
        get() = schema.metrics.firstOrNull { it.metric == PerformanceMetric.EXTERNAL_LOAD }?.let { metric ->
            if (
                metric.minimumCanonical != null || metric.maximumCanonical != null ||
                metric.incrementCanonical != null || metric.allowedCanonicalValues.isNotEmpty()
            ) {
                LoadResolution(
                    minimumLoad = metric.minimumCanonical,
                    maximumLoad = metric.maximumCanonical,
                    increment = metric.incrementCanonical,
                    allowedValues = metric.allowedCanonicalValues,
                )
            } else null
        }
}

data class ExecutionProfile(
    val id: ExecutionProfileId,
    val exerciseId: ExerciseId,
    val name: String,
    val isDefault: Boolean,
    val archived: Boolean,
    val versions: List<ExecutionProfileVersion>,
) {
    init {
        require(versions.isNotEmpty()) { "Execution profile requires at least one semantic version." }
        require(versions.all { it.executionProfileId == id })
        require(versions.map { it.version }.distinct().size == versions.size)
        require(versions.count { it.supersededAt == null } == 1) {
            "Execution profile must have exactly one current immutable version."
        }
    }

    val currentVersion: ExecutionProfileVersion
        get() = versions.single { it.supersededAt == null }

    val equipment: EquipmentProfile get() = currentVersion.equipment
    val loadResolution: LoadResolution? get() = currentVersion.loadResolution
    val recruitment: RecruitmentProfile get() = currentVersion.recruitment
}

/** Read-only UI adapter. Measurement semantics remain owned by the selected profile version. */
data class ExerciseTracking(
    val defaultUnit: UnitId,
    val metricFamily: MetricFamily,
    val resistanceSemantics: ResistanceSemantics,
    val entryBasis: EntryBasis,
)

data class ExerciseMemory(
    val category: String,
    val equipment: String,
    val fatigueCost: Int,
    val skillDifficulty: Int,
    val setupNotes: String,
    val videoReferenceUrl: String,
    val machineSettings: String,
    val cues: List<String>,
    val commonMistakes: List<String>,
    val substitutions: List<String>,
)

data class ExerciseSetupMedia(
    val id: String,
    val exerciseId: ExerciseId,
    val relativePath: String,
    val mimeType: String,
    val sortOrder: Int,
    val createdAt: String,
    val width: Int,
    val height: Int,
)

data class Exercise(
    val id: ExerciseId,
    val name: String,
    val archived: Boolean,
    val essentialCue: String?,
    val createdAt: String,
    val updatedAt: String,
    val memory: ExerciseMemory?,
    val setupMedia: List<ExerciseSetupMedia>,
    val executionProfiles: List<ExecutionProfile>,
) {
    val defaultExecutionProfile: ExecutionProfile
        get() = executionProfiles.singleOrNull { it.isDefault }
            ?: error("Exercise ${id.value} must have exactly one default execution profile.")

    val tracking: ExerciseTracking
        get() {
            val version = defaultExecutionProfile.currentVersion
            val defaultUnit = version.schema.metrics.firstOrNull()?.defaultUnit
                ?: error("Execution schema is empty.")
            return ExerciseTracking(
                defaultUnit = defaultUnit,
                metricFamily = version.metricFamily,
                resistanceSemantics = version.resistanceModel.semantics,
                entryBasis = version.entryBasis,
            )
        }
}
