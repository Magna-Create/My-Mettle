package dev.kian.mymettle.data.reference

import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ReferencePhysiologyPriorEntity
import dev.kian.mymettle.domain.anatomy.AnatomicalStatus
import dev.kian.mymettle.domain.anatomy.AnatomicalUnitKind
import dev.kian.mymettle.domain.anatomy.AnatomyVerificationStatus
import dev.kian.mymettle.domain.anatomy.BodyRegion
import dev.kian.mymettle.domain.anatomy.LateralityModel
import dev.kian.mymettle.domain.anatomy.Muscle
import dev.kian.mymettle.domain.anatomy.MuscleId
import dev.kian.mymettle.domain.anatomy.MuscleSegment
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.anatomy.SegmentStatePolicy
import dev.kian.mymettle.domain.anatomy.SegmentType
import dev.kian.mymettle.domain.physiology.AbsoluteSharePolicy
import dev.kian.mymettle.domain.physiology.Estimate
import dev.kian.mymettle.domain.physiology.EstimateSourceKind
import dev.kian.mymettle.domain.physiology.ReferencePhysiologyPrior
import dev.kian.mymettle.domain.physiology.ReferencePopulation
import dev.kian.mymettle.domain.physiology.ReferenceProfile
import dev.kian.mymettle.domain.physiology.ReferenceProfileId
import dev.kian.mymettle.domain.physiology.ReferenceSex
import dev.kian.mymettle.domain.physiology.UncertaintyClass

class ReferenceRepository(private val database: MyMettleDatabase) {
    private val dao get() = database.referenceDao()

    suspend fun anatomy(): List<Muscle> {
        val segmentsByMuscle = dao.segments().groupBy { it.muscleId }
        return dao.muscles().map { muscle ->
            Muscle(
                id = MuscleId(muscle.id),
                name = muscle.name,
                region = BodyRegion.valueOf(muscle.region),
                unitKind = AnatomicalUnitKind.valueOf(muscle.unitKind),
                lateralityModel = LateralityModel.valueOf(muscle.lateralityModel),
                instancePattern = muscle.instancePattern,
                verificationStatus = AnatomyVerificationStatus.valueOf(muscle.verificationStatus),
                segments = segmentsByMuscle[muscle.id].orEmpty().map { segment ->
                    MuscleSegment(
                        id = MuscleSegmentId(segment.id),
                        muscleId = MuscleId(segment.muscleId),
                        name = segment.name,
                        type = SegmentType.valueOf(segment.segmentType),
                        anatomicalStatus = AnatomicalStatus.valueOf(segment.anatomicalStatus),
                        statePolicy = SegmentStatePolicy.valueOf(segment.statePolicy),
                        verificationStatus = AnatomyVerificationStatus.valueOf(segment.verificationStatus),
                    )
                },
            )
        }
    }

    suspend fun profile(id: ReferenceProfileId): ReferenceProfile? {
        val entity = dao.profile(id.value) ?: return null
        return ReferenceProfile(
            id = ReferenceProfileId(entity.id),
            version = entity.version,
            population = ReferencePopulation(
                sex = ReferenceSex.valueOf(entity.populationSex),
                ageSummary = entity.populationAgeSummary,
                description = entity.populationDescription,
            ),
            datasetVersion = entity.datasetVersion,
            modelVersion = entity.modelVersion,
            priors = dao.priors(entity.id).map(::toDomain),
        )
    }

    private fun toDomain(entity: ReferencePhysiologyPriorEntity): ReferencePhysiologyPrior =
        ReferencePhysiologyPrior(
            muscleId = MuscleId(entity.muscleId),
            segmentId = entity.segmentId?.let(::MuscleSegmentId),
            volumeCm3 = estimate(
                entity.volumeCm3,
                entity.volumeUncertainty,
                entity.volumeSourceKind,
                entity.volumeSourceId,
                entity.volumeModelVersion,
            ),
            optimalFibreLengthMm = estimate(
                entity.optimalFibreLengthMm,
                entity.optimalFibreLengthUncertainty,
                entity.optimalFibreLengthSourceKind,
                entity.optimalFibreLengthSourceId,
                entity.optimalFibreLengthModelVersion,
            ),
            pennationDeg = estimate(
                entity.pennationDeg,
                entity.pennationUncertainty,
                entity.pennationSourceKind,
                entity.pennationSourceId,
                entity.pennationModelVersion,
            ),
            geometricPcsaCm2 = estimate(
                entity.geometricPcsaCm2,
                entity.geometricPcsaUncertainty,
                entity.geometricPcsaSourceKind,
                entity.geometricPcsaSourceId,
                entity.geometricPcsaModelVersion,
            ),
            effectivePcsaCm2 = estimate(
                entity.effectivePcsaCm2,
                entity.effectivePcsaUncertainty,
                entity.effectivePcsaSourceKind,
                entity.effectivePcsaSourceId,
                entity.effectivePcsaModelVersion,
            ),
            structuralCapacityIndex = estimate(
                entity.structuralCapacityIndex,
                entity.structuralCapacityUncertainty,
                entity.structuralCapacitySourceKind,
                entity.structuralCapacitySourceId,
                entity.structuralCapacityModelVersion,
            ),
            absoluteSharePolicy = when (entity.absoluteShareKind) {
                "KNOWN" -> AbsoluteSharePolicy.Known(requireNotNull(entity.absoluteShareFraction))
                "STRUCTURAL_PRIOR" -> AbsoluteSharePolicy.StructuralPrior(
                    fraction = requireNotNull(entity.absoluteShareFraction),
                    uncertainty = UncertaintyClass.valueOf(requireNotNull(entity.absoluteShareUncertainty)),
                )
                "LATENT" -> AbsoluteSharePolicy.Latent
                else -> error("Unknown absolute-share policy ${entity.absoluteShareKind}")
            },
            availability = entity.availability,
            uncertaintyClass = UncertaintyClass.valueOf(entity.uncertaintyClass),
            selectionRule = entity.selectionRule,
        )
}

private fun estimate(
    value: Double?,
    uncertainty: Double?,
    sourceKind: String?,
    sourceId: String?,
    modelVersion: String?,
): Estimate<Double>? = value?.let {
    Estimate(
        value = it,
        uncertainty = uncertainty,
        sourceKind = EstimateSourceKind.valueOf(requireNotNull(sourceKind)),
        sourceId = sourceId,
        modelVersion = modelVersion,
    )
}
