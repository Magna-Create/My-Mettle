package dev.kian.lab2b.vlm

import java.text.Normalizer
import java.util.Locale

/** Experimental, auditable manufacturer/brand alias catalogue.
 *
 * Entries are brand identities, NOT equipment models or claims of gym availability.
 * Brands from the existing PlacardParser are the baseline; additions and aliases below
 * remain deliberately conservative. A common generic word is only a review candidate,
 * never a reason to skip the Cloud fallback. No network or language model required.
 */
internal object ManufacturerCatalogue {
    internal data class Brand(val name: String, val aliases: List<String> = emptyList(), val generic: Boolean = false)

    // Initial curated coverage, not a comprehensive worldwide manufacturer database.
    // "Hammer Strength" is distinct from "Life Fitness" even though they share ownership.
    // Do not treat "Hammer Fitness" as an alias for "Hammer Strength".
    internal val brands: List<Brand> = listOf(
        Brand("Life Fitness", listOf("lifefitness")),
        Brand("Hammer Strength", listOf("hammerstrength")),
        Brand("Technogym", listOf("techno gym")),
        Brand("Matrix", listOf("matrix fitness"), generic = true),
        Brand("Precor"),
        Brand("Cybex", listOf("cybex international")),
        Brand("Nautilus"),
        Brand("Star Trac", listOf("startrac")),
        Brand("Panatta"),
        Brand("Gym80", listOf("gym 80", "gym-eighty", "gym eighty")),
        Brand("Hoist", listOf("hoist fitness"), generic = true),
        Brand("Freemotion", listOf("free motion", "freemotion fitness")),
        Brand("Atlantis", listOf("atlantis strength"), generic = true),
        Brand("Prime", listOf("prime fitness"), generic = true),
        Brand("Arsenal Strength"),
        Brand("Watson", listOf("watson gym equipment", "watson strength"), generic = true),
        Brand("TuffStuff", listOf("tuff stuff fitness")),
        Brand("Pulse", listOf("pulse fitness"), generic = true),
        Brand("Spirit", listOf("spirit fitness"), generic = true),
        Brand("Keiser"),
        Brand("Eleiko"),
        Brand("Torque Fitness"),
        Brand("Body-Solid", listOf("body solid", "bodysolid")),
        Brand("Assault Fitness", listOf("assaultfitness")),
        Brand("Concept2", listOf("concept 2")),
        Brand("Wattbike"),
        Brand("Woodway"),
        Brand("WaterRower", listOf("water rower")),
        Brand("StairMaster", listOf("stair master")),
        Brand("Schwinn"),
        Brand("Exigo"),
        Brand("Escape Fitness"),
        Brand("Jordan Fitness"),
        Brand("Attack Fitness"),
        Brand("Vision Fitness"),
        Brand("Hammer Sport", listOf("hammer sport fitness")), // Different name from Hammer Strength.
        Brand("Booty Builder", listOf("bootybuilder")),
        Brand("GluteBuilder", listOf("glute builder")),
        Brand("DRAX", generic = true),
        Brand("TECA"),
        Brand("BH Fitness"),
        Brand("Impulse Fitness"),
        Brand("SportsArt", listOf("sports art")),
        Brand("True Fitness", listOf("true fitness"), generic = true),
        Brand("Octane Fitness"),
        Brand("Power Plate"),
        Brand("VersaClimber", listOf("versa climber")),
        Brand("StairMaster"),
        Brand("NOHRD"),
        Brand("Sorinex"),
        Brand("Rogue Fitness"),
        Brand("REP Fitness"),
    ).distinctBy { it.name }

    internal enum class MatchKind { EXACT_PHRASE, STACKED_WORDMARK, REVIEW_ONLY }
    internal data class Match(
        val manufacturer: String,
        val raw: String,
        val kind: MatchKind,
        val reason: String,
    )

    /**
     * Replaces punctuation with boundaries before matching. Ensures that e.g.
     * 'Matrix' doesn't match 'matrixes' and 'Pulse' doesn't match 'impulse'.
     * No edit-distance/fuzzy auto-selection or ungrounded letter substitution.
     */
    private fun tokens(raw: String): List<String> {
        val latin = Normalizer.normalize(raw, Normalizer.Form.NFKD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase(Locale.ROOT)
        return Regex("[a-z0-9]+").findAll(latin).map { it.value }.toList()
    }

    private fun matched(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        return haystack.windowed(needle.size).any { it == needle }
    }

    private fun evaluate(raw: String, stacked: Boolean): List<Match> {
        val hay = tokens(raw)
        if (hay.isEmpty()) return emptyList()
        val result = mutableListOf<Match>()
        for (brand in brands) {
            val candidates = listOf(brand.name) + brand.aliases
            val alias = candidates.firstOrNull { candidate ->
                val expected = tokens(candidate)
                matched(hay, expected) ||
                    // A logo often OCRs 'TECHNOGYM' and 'LIFEFITNESS' without a space.
                    (expected.size > 1 && matched(hay, listOf(expected.joinToString(""))))
            } ?: continue
            val aliasTokens = tokens(alias)
            val isShortGeneric = brand.generic && aliasTokens.size == 1
            val kind = when {
                isShortGeneric -> MatchKind.REVIEW_ONLY
                stacked -> MatchKind.STACKED_WORDMARK
                else -> MatchKind.EXACT_PHRASE
            }
            result += Match(brand.name, raw, kind,
                if (isShortGeneric) "Generic single word; needs confirmation"
                else if (stacked) "Text on adjacent OCR lines"
                else "Case-insensitive whole-token phrase or known alias")
        }
        return result
    }

    /** Search actual OCR line text, then two adjacent lines as one stacked wordmark. */
    fun match(lines: List<String>): List<Match> {
        val matches = buildList {
            lines.forEachIndexed { index, line ->
                addAll(evaluate(line, stacked = false))
                // Across a line break only join one adjacent pair, not the entire placard.
                if (index + 1 < lines.size) {
                    val next = lines[index + 1]
                    val combined = "$line $next"
                    val joined = evaluate(combined, stacked = true).filter { m ->
                        // Must genuinely cross the boundary, not rediscover a name
                        // already present on either individual line.
                        evaluate(line, false).none { it.manufacturer == m.manufacturer } &&
                            evaluate(next, false).none { it.manufacturer == m.manufacturer }
                    }
                    addAll(joined)
                }
            }
        }
        return matches.distinctBy { it.manufacturer to it.kind }
            .sortedWith(compareBy<Match> { if (it.kind == MatchKind.REVIEW_ONLY) 1 else 0 }.thenBy { it.manufacturer })
    }

    fun match(evidence: OcrEvidence): List<Match> {
        // Use structured OCR lines rather than fullText; don't join unrelated columns.
        val lines = evidence.blocks.flatMap { block ->
            if (block.lines.isEmpty()) listOf(block.text) else block.lines.map { it.text }
        }
        return match(lines)
    }

    /** A single strong exact/stacked match is evidence to suggest; NEVER proof of a model. */
    fun uniqueStrongCandidate(matches: List<Match>): Match? =
        matches.filter { it.kind != MatchKind.REVIEW_ONLY }
            .map { it.manufacturer }.distinct().singleOrNull()
            ?.let { brand -> matches.firstOrNull { it.manufacturer == brand && it.kind != MatchKind.REVIEW_ONLY } }
}
