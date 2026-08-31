package dev.kian.mymettle.domain.inference

/**
 * Evidence-policy v3 for the authoritative semantically corrected Lite -> Room14 baseline.
 *
 * The corrected import is still factual Lite history: it preserves the legacy source's lack of
 * laterality while applying explicit semantic corrections such as exercise splitting and resistance
 * coordinate correction before Room14 ingestion. UNKNOWN therefore remains an isolated capability
 * stream and is admitted only for the two explicit Lite-derived provenance classes below. Native or
 * manual UNKNOWN observations remain ineligible, and no UNKNOWN observation can enter a known-side
 * stream.
 *
 * v1/v2 stay immutable so earlier physical acceptance evidence remains reproducible.
 */
object DynamicResistanceV3Contract {
    const val EVIDENCE_POLICY_VERSION = "n-bio-7b1-dynamic-resistance-evidence-v3-corrected-lite"
    const val CORRECTED_LEGACY_UNSIDED_SOURCE = "corrected_lite_import"
    const val UNKNOWN_LATERALITY_POLICY =
        "allow_exact_unknown_stream_only_for_explicit_lite_derived_import_source_and_unknown_profile_mode"

    val contextPolicy = DynamicResistanceV2Contract.contextPolicy

    val evidencePolicy: DynamicResistanceEvidencePolicy = DynamicResistanceV2Contract.evidencePolicy.copy(
        semanticVersion = EVIDENCE_POLICY_VERSION,
        unknownLateralityPolicy = UNKNOWN_LATERALITY_POLICY,
        eligibleHistoricalUnknownSources = DynamicResistanceV2Contract.evidencePolicy.eligibleHistoricalUnknownSources +
            CORRECTED_LEGACY_UNSIDED_SOURCE,
    )
}

/** Same frozen Candidate-v1 mathematics, explicitly rebound to corrected evidence-policy v3. */
object DynamicStochasticFrontierEvidenceV3 {
    val config: DynamicStochasticFrontierConfig = DynamicStochasticFrontierV1.config.copy(
        evidencePolicyIdentity = DynamicResistanceV3Contract.evidencePolicy.identity,
    )
}

/**
 * Candidate-v2 mathematical family with the corrected evidence identity. The trend equation, priors,
 * likelihood and numerical parameters are unchanged; only the admissible historical provenance is
 * versioned. Because evidence identity participates in the mathematical-model fingerprint, this gets
 * a distinct reproducible identity without rewriting the historical Candidate-v2 definition.
 */
object DynamicTrendFrontierEvidenceV3 {
    val config: DynamicTrendFrontierConfig = DynamicTrendFrontierV2.config.copy(
        baseConfig = DynamicStochasticFrontierEvidenceV3.config,
    )

    val mathematicalModelIdentity: InferenceMathematicalModelIdentity =
        DynamicTrendFrontierV2.mathematicalIdentity(config)
}
