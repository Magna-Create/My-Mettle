package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceExclusionReason
import dev.kian.mymettle.domain.inference.DynamicResistanceV1Contract
import dev.kian.mymettle.domain.performance.UnitId

/**
 * 7B.1-only observability over a selected execution-profile-version/side projection.
 * It intentionally contains no inferred capability value because fitting begins in 7B.2.
 */
data class DynamicResistancePreparationDiagnostics(
    val executionProfileVersionId: String,
    val executionProfileId: String,
    val side: String,
    val eligibleObservationCount: Int,
    val independentSessionCount: Int,
    val repMin: Int?,
    val repMax: Int?,
    val resistanceMin: Double?,
    val resistanceMax: Double?,
    val canonicalCoordinateUnit: String?,
    val referenceRepetitions: Double?,
    val warmUpsExcludedCount: Int,
    val profileOrOtherFamilyExclusionCount: Int,
    val unresolvedResistanceExclusionCount: Int,
    val lateralityExclusionCount: Int,
    val supersededRowsExcludedCount: Int?,
    val contextPolicy: String,
    val candidateCapabilityPosterior: String,
    val evidencePolicyIdentity: String,
) {
    companion object {
        fun from(
            projection: DynamicResistanceEvidenceProjection,
            supersededRowsExcludedCount: Int? = null,
        ): DynamicResistancePreparationDiagnostics {
            val counts = projection.exclusions.groupingBy { it.reason }.eachCount()
            val unresolvedReasons = setOf(
                DynamicResistanceExclusionReason.METRIC_FAMILY_RESISTANCE_SEMANTICS_INCOMPATIBLE,
                DynamicResistanceExclusionReason.MISSING_EXTERNAL_LOAD,
                DynamicResistanceExclusionReason.MISSING_ASSISTANCE,
                DynamicResistanceExclusionReason.MISSING_BODY_MASS,
                DynamicResistanceExclusionReason.INCONSISTENT_RESISTANCE_MODEL,
                DynamicResistanceExclusionReason.UNSUPPORTED_RESISTANCE_SEMANTICS,
                DynamicResistanceExclusionReason.DEVICE_ORDINAL_NOT_PHYSICAL,
                DynamicResistanceExclusionReason.NON_POSITIVE_RESISTANCE_COORDINATE,
            )
            return DynamicResistancePreparationDiagnostics(
                executionProfileVersionId = projection.profile.executionProfileVersionId.value,
                executionProfileId = projection.profile.executionProfileId.value,
                side = projection.side.storageValue,
                eligibleObservationCount = projection.evidence.size,
                independentSessionCount = projection.independentSessionCount,
                repMin = projection.repDomain?.first,
                repMax = projection.repDomain?.last,
                resistanceMin = projection.resistanceRange?.start,
                resistanceMax = projection.resistanceRange?.endInclusive,
                canonicalCoordinateUnit = UnitId.KILOGRAM.storageValue.takeIf { projection.evidence.isNotEmpty() },
                referenceRepetitions = projection.referenceRepetitions,
                warmUpsExcludedCount = counts[DynamicResistanceExclusionReason.WARM_UP_EXCLUDED] ?: 0,
                profileOrOtherFamilyExclusionCount =
                    (counts[DynamicResistanceExclusionReason.PROFILE_VERSION_MISMATCH] ?: 0) +
                        (counts[DynamicResistanceExclusionReason.METRIC_FAMILY_INELIGIBLE] ?: 0),
                unresolvedResistanceExclusionCount = projection.exclusions.count { it.reason in unresolvedReasons },
                lateralityExclusionCount = counts[DynamicResistanceExclusionReason.LATERALITY_INCOMPATIBLE] ?: 0,
                supersededRowsExcludedCount = supersededRowsExcludedCount,
                contextPolicy = if (projection.policy.contextConsumptionPolicy.allowedTagIds.isEmpty()) "NONE" else "CONFIGURED",
                candidateCapabilityPosterior = DynamicResistanceV1Contract.CANDIDATE_POSTERIOR_STATUS,
                evidencePolicyIdentity = projection.policy.identity,
            )
        }
    }

    fun renderText(): String = buildString {
        appendLine("DYNAMIC RESISTANCE PREPARATION — 7B.1")
        appendLine("execution profile: $executionProfileId")
        appendLine("execution profile version: $executionProfileVersionId")
        appendLine("side: $side")
        appendLine("eligible observations: $eligibleObservationCount")
        appendLine("independent sessions: $independentSessionCount")
        appendLine("rep domain: ${repMin ?: "—"} .. ${repMax ?: "—"}")
        appendLine("resistance coordinate: ${resistanceMin ?: "—"} .. ${resistanceMax ?: "—"} ${canonicalCoordinateUnit ?: ""}".trimEnd())
        appendLine("reference reps: ${referenceRepetitions ?: "—"}")
        appendLine("warm-ups excluded: $warmUpsExcludedCount")
        appendLine("profile/other-family exclusions: $profileOrOtherFamilyExclusionCount")
        appendLine("laterality exclusions: $lateralityExclusionCount")
        appendLine("unresolved resistance exclusions: $unresolvedResistanceExclusionCount")
        appendLine("superseded rows excluded upstream: ${supersededRowsExcludedCount ?: "not separately counted"}")
        appendLine("context consumption: $contextPolicy")
        appendLine("candidate capability posterior: $candidateCapabilityPosterior")
        appendLine("evidence policy: $evidencePolicyIdentity")
    }
}
