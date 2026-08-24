package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "muscle",
    indices = [Index("name"), Index("region")],
)
data class MuscleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val region: String,
    val unitKind: String,
    val lateralityModel: String,
    val instancePattern: String?,
    val verificationStatus: String,
)

@Entity(
    tableName = "muscle_segment",
    foreignKeys = [
        ForeignKey(
            entity = MuscleEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("muscleId"), Index("statePolicy")],
)
data class MuscleSegmentEntity(
    @PrimaryKey val id: String,
    val muscleId: String,
    val name: String,
    val segmentType: String,
    val anatomicalStatus: String,
    val statePolicy: String,
    val verificationStatus: String,
)

@Entity(tableName = "reference_profile")
data class ReferenceProfileEntity(
    @PrimaryKey val id: String,
    val version: Int,
    val populationSex: String,
    val populationAgeSummary: String,
    val populationDescription: String,
    val datasetVersion: String,
    val modelVersion: String,
)

@Entity(
    tableName = "reference_physiology_prior",
    foreignKeys = [
        ForeignKey(
            entity = ReferenceProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MuscleEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MuscleSegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profileId"),
        Index("muscleId"),
        Index("segmentId"),
        Index(value = ["profileId", "targetKind", "targetId"], unique = true),
    ],
)
data class ReferencePhysiologyPriorEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val targetKind: String,
    val targetId: String,
    val muscleId: String,
    val segmentId: String?,
    val volumeCm3: Double?,
    val volumeUncertainty: Double?,
    val volumeSourceKind: String?,
    val volumeSourceId: String?,
    val volumeModelVersion: String?,
    val optimalFibreLengthMm: Double?,
    val optimalFibreLengthUncertainty: Double?,
    val optimalFibreLengthSourceKind: String?,
    val optimalFibreLengthSourceId: String?,
    val optimalFibreLengthModelVersion: String?,
    val pennationDeg: Double?,
    val pennationUncertainty: Double?,
    val pennationSourceKind: String?,
    val pennationSourceId: String?,
    val pennationModelVersion: String?,
    val geometricPcsaCm2: Double?,
    val geometricPcsaUncertainty: Double?,
    val geometricPcsaSourceKind: String?,
    val geometricPcsaSourceId: String?,
    val geometricPcsaModelVersion: String?,
    val effectivePcsaCm2: Double?,
    val effectivePcsaUncertainty: Double?,
    val effectivePcsaSourceKind: String?,
    val effectivePcsaSourceId: String?,
    val effectivePcsaModelVersion: String?,
    val structuralCapacityIndex: Double?,
    val structuralCapacityUncertainty: Double?,
    val structuralCapacitySourceKind: String?,
    val structuralCapacitySourceId: String?,
    val structuralCapacityModelVersion: String?,
    val absoluteShareKind: String,
    val absoluteShareFraction: Double?,
    val absoluteShareUncertainty: String?,
    val availability: String,
    val uncertaintyClass: String,
    val selectionRule: String,
)

@Entity(
    tableName = "exercise_execution_profile",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class ExerciseExecutionProfileEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val name: String,
    val isDefault: Boolean,
    val archived: Boolean,
)

@Entity(
    tableName = "recruitment_profile_version",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseExecutionProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionProfileId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("executionProfileId"),
        Index(value = ["executionProfileId", "version"], unique = true),
    ],
)
data class RecruitmentProfileVersionEntity(
    @PrimaryKey val id: String,
    val executionProfileId: String,
    val version: Int,
    val createdAt: String,
    val effectiveAt: String,
    val supersededAt: String?,
    val provenance: String,
    val modelVersion: String,
)

@Entity(
    tableName = "execution_profile_version",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseExecutionProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionProfileId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PerformanceSchemaEntity::class,
            parentColumns = ["id"],
            childColumns = ["performanceSchemaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecruitmentProfileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recruitmentProfileVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("executionProfileId"),
        Index("performanceSchemaId"),
        Index("recruitmentProfileVersionId"),
        Index(value = ["executionProfileId", "version"], unique = true),
    ],
)
data class ExecutionProfileVersionEntity(
    @PrimaryKey val id: String,
    val executionProfileId: String,
    val version: Int,
    val metricFamily: String,
    val performanceSchemaId: String,
    val equipmentIdentity: String?,
    val equipmentType: String?,
    val resistanceSemantics: String,
    val resistanceModelVersion: String,
    val bodyweightCoefficient: Double,
    val externalLoadCoefficient: Double,
    val assistanceCoefficient: Double,
    val entryBasis: String,
    val implementCount: Int?,
    val lateralityMode: String,
    val romClass: String?,
    val techniqueClass: String?,
    val resistanceCurveClass: String?,
    val movementPattern: String?,
    val jointActionsJson: String?,
    val kineticChain: String?,
    val contractionType: String?,
    val gripSupportConstraintsJson: String?,
    val recruitmentProfileVersionId: String,
    val createdAt: String,
    val effectiveAt: String,
    val supersededAt: String?,
    val provenance: String,
    val modelVersion: String,
)

@Entity(
    tableName = "recruitment_allocation",
    primaryKeys = ["recruitmentProfileVersionId", "muscleSegmentId"],
    foreignKeys = [
        ForeignKey(
            entity = RecruitmentProfileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recruitmentProfileVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = MuscleSegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscleSegmentId"],
        ),
    ],
    indices = [Index("recruitmentProfileVersionId"), Index("muscleSegmentId")],
)
data class RecruitmentAllocationEntity(
    val recruitmentProfileVersionId: String,
    val muscleSegmentId: String,
    val role: String,
    val weighting: Double,
    val confidence: Double,
    val provenanceType: String,
    val provenanceReference: String?,
    val applicableRom: String?,
    val applicableTechnique: String?,
    val resistanceCurveClass: String?,
    val modelVersion: String,
)
