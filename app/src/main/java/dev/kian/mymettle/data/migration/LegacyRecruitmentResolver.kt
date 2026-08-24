package dev.kian.mymettle.data.migration

import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity

/**
 * Resolves the explicit Native translation supplement against canonical anatomy.
 *
 * Lite muscleLoadModel proportions are intentionally not accepted here: they are conserved shares,
 * while N-BIO weighting is an independent muscle-local exposure coefficient. Translation uses exact
 * stable segment IDs and preserves the reviewed model's conditions and provenance verbatim.
 */
class LegacyRecruitmentResolver(private val database: MyMettleDatabase) {
    suspend fun resolve(values: List<TranslatedRecruitmentAllocation>): List<RecruitmentAllocationEntity> {
        if (values.isEmpty()) return emptyList()

        val segmentIds = database.referenceDao().segments().mapTo(hashSetOf()) { it.id }
        val unresolved = values.map { it.muscleSegmentId }.distinct().filterNot(segmentIds::contains)
        if (unresolved.isNotEmpty()) {
            throw LegacyImportException(
                "Native translation references unknown stable muscle-segment IDs: ${unresolved.joinToString()}.",
            )
        }

        return values.map { value ->
            RecruitmentAllocationEntity(
                recruitmentProfileVersionId = value.recruitmentProfileVersionId,
                muscleSegmentId = value.muscleSegmentId,
                role = value.role,
                weighting = value.weighting,
                confidence = value.confidence,
                provenanceType = "ai_reviewed_lite_translation",
                provenanceReference = value.provenanceReference,
                applicableRom = value.applicableRom,
                applicableTechnique = value.applicableTechnique,
                resistanceCurveClass = value.resistanceCurveClass,
                modelVersion = value.modelVersion,
            )
        }
    }
}
