package dev.kian.lab2b.vlm

import java.text.Normalizer
import java.util.Locale

/**
 * Normalised manufacturer keys for offline OCR matching. Spaces, case and punctuation
 * are not part of identity; display word breaks and exceptional brand casing are metadata.
 *
 * This is an initial curated list, not a verified exhaustive catalogue. A brand is
 * evidence for a manufacturer only: never equipment model, mechanics or gym location.
 */
internal object ManufacturerCatalogue {
    internal data class Brand(
        val key: String,
        val spacesAfter: List<Int> = emptyList(),
        val aliasKeys: List<String> = emptyList(),
        val generic: Boolean = false,
        val displayOverride: String? = null,
    ) {
        init {
            require(key.matches(Regex("[a-z0-9]+"))) { "Brand keys must be lower-case and compact" }
            require(aliasKeys.all { it.matches(Regex("[a-z0-9]+")) }) { "Aliases must be compact keys" }
            require(spacesAfter == spacesAfter.distinct().sorted() && spacesAfter.all { it in 1 until key.length }) {
                "Invalid word break for $key"
            }
        }

        val displayName: String
            get() {
                displayOverride?.let { return it }
                val boundaries = listOf(0) + spacesAfter + key.length
                return boundaries.zipWithNext { from, to ->
                    key.substring(from, to).replaceFirstChar { it.titlecase(Locale.ROOT) }
                }.joinToString(" ")
            }
    }

    // Indices are zero-based CHARACTER COUNTS where a new display word begins:
    // lifefitness breaks after character 4 -> Life Fitness
    // hammerstrength breaks after character 6 -> Hammer Strength
    // technogym has NO break -> Technogym (the manufacturer's actual spelling).
    // Special case/typographic forms keep an explicit displayOverride.
    internal val brands: List<Brand> = listOf(
        Brand("lifefitness", spacesAfter = listOf(4)),
        Brand("hammerstrength", spacesAfter = listOf(6)),
        Brand("technogym"),
        Brand("matrix", aliasKeys = listOf("matrixfitness"), generic = true),
        Brand("precor"),
        Brand("cybex", aliasKeys = listOf("cybexinternational")),
        Brand("nautilus"),
        Brand("startrac", spacesAfter = listOf(4)),
        Brand("panatta"),
        Brand("gym80", aliasKeys = listOf("gymeighty"), displayOverride = "Gym80"),
        Brand("hoist", aliasKeys = listOf("hoistfitness"), generic = true),
        Brand("freemotion", aliasKeys = listOf("freemotionfitness")),
        Brand("atlantis", aliasKeys = listOf("atlantisstrength"), generic = true),
        Brand("prime", aliasKeys = listOf("primefitness"), generic = true),
        Brand("arsenalstrength", spacesAfter = listOf(7)),
        Brand("watson", aliasKeys = listOf("watsongymequipment", "watsonstrength"), generic = true),
        Brand("tuffstuff", displayOverride = "TuffStuff", aliasKeys = listOf("tuffstufffitness")),
        Brand("pulse", aliasKeys = listOf("pulsefitness"), generic = true),
        Brand("spirit", aliasKeys = listOf("spiritfitness"), generic = true),
        Brand("keiser"),
        Brand("eleiko"),
        Brand("torquefitness", spacesAfter = listOf(6)),
        Brand("bodysolid", displayOverride = "Body-Solid"),
        Brand("assaultfitness", spacesAfter = listOf(7)),
        Brand("concept2", displayOverride = "Concept2"),
        Brand("wattbike"),
        Brand("woodway"),
        Brand("waterrower", displayOverride = "WaterRower"),
        Brand("stairmaster", displayOverride = "StairMaster"),
        Brand("schwinn"),
        Brand("exigo"),
        Brand("escapefitness", spacesAfter = listOf(6)),
        Brand("jordanfitness", spacesAfter = listOf(6)),
        Brand("attackfitness", spacesAfter = listOf(6)),
        Brand("visionfitness", spacesAfter = listOf(6)),
        Brand("hammersport", spacesAfter = listOf(6), aliasKeys = listOf("hammersportfitness")),
        Brand("bootybuilder", spacesAfter = listOf(5)),
        Brand("glutebuilder", displayOverride = "GluteBuilder"),
        Brand("drax", displayOverride = "DRAX", generic = true),
        Brand("teca", displayOverride = "TECA"),
        Brand("bhfitness", displayOverride = "BH Fitness", spacesAfter = listOf(2)),
        Brand("impulsefitness", spacesAfter = listOf(7)),
        Brand("sportsart", displayOverride = "SportsArt"),
        Brand("truefitness", spacesAfter = listOf(4), generic = true),
        Brand("octanefitness", spacesAfter = listOf(6)),
        Brand("powerplate", spacesAfter = listOf(5)),
        Brand("versaclimber", displayOverride = "VersaClimber"),
        Brand("nohrd", displayOverride = "NOHRD"),
        Brand("sorinex"),
        Brand("roguefitness", spacesAfter = listOf(5)),
        Brand("repfitness", displayOverride = "REP Fitness", spacesAfter = listOf(3)),
    )

    internal enum class MatchKind { EXACT_PHRASE, STACKED_WORDMARK, REVIEW_ONLY }
    internal data class Match(
        val manufacturer: String,
        val raw: String,
        val kind: MatchKind,
        val reason: String,
    )

    /** OCR can vary spacing/case/punctuation; normalise input tokens without
     * accepting a brand name in the *middle* of an unrelated token.
     * E.g. "Hammer Strength" and "HAMMERSTRENGTH" both match "hammerstrength";
     * "Pulse" does not match "Impulse Fitness" and "Matrix" does not match "matrixes".
     */
    private fun tokens(raw: String): List<String> {
        val latin = Normalizer.normalize(raw, Normalizer.Form.NFKD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase(Locale.ROOT)
        return Regex("[a-z0-9]+").findAll(latin).map { it.value }.toList()
    }

    /** Bound the OCR "in string" search to at most four adjacent tokens.
     * This supports arbitrary spaces within a wordmark while preserving token
     * boundaries. It is not an unsafe String.contains on the entire OCR document.
     */
    private fun containsCanonicalPhrase(words: List<String>, key: String): Boolean {
        for (i in words.indices) {
            var combined = ""
            for (j in i until minOf(words.size, i + 4)) {
                combined += words[j]
                if (combined == key) return true
                if (!key.startsWith(combined)) break
            }
        }
        return false
    }

    private fun evaluate(raw: String, stacked: Boolean): List<Match> {
        val words = tokens(raw)
        if (words.isEmpty()) return emptyList()
        return brands.mapNotNull { brand ->
            // Longer alias first so "Matrix Fitness" outranks the ambiguous
            // "Matrix" even if both compact forms are found in the same line.
            val matchedKey = (brand.aliasKeys + brand.key)
                .sortedByDescending { it.length }
                .firstOrNull { containsCanonicalPhrase(words, it) } ?: return@mapNotNull null
            val reviewOnly = brand.generic && matchedKey == brand.key
            val kind = when {
                reviewOnly -> MatchKind.REVIEW_ONLY
                stacked -> MatchKind.STACKED_WORDMARK
                else -> MatchKind.EXACT_PHRASE
            }
            Match(
                manufacturer = brand.displayName,
                raw = raw,
                kind = kind,
                reason = when {
                    reviewOnly -> "Generic brand text; confirmation or stronger evidence needed"
                    stacked -> "Canonical brand found across adjacent OCR lines"
                    else -> "Case/spacing-insensitive exact canonical phrase or reviewed alias"
                },
            )
        }
    }

    /** Match a list of lines from one contiguous OCR region. */
    fun match(lines: List<String>): List<Match> {
        val matches = buildList {
            lines.forEachIndexed { index, line ->
                addAll(evaluate(line, stacked = false))
                if (index + 1 < lines.size) {
                    val next = lines[index + 1]
                    val combined = "$line $next"
                    val joined = evaluate(combined, stacked = true).filter { m ->
                        evaluate(line, false).none { it.manufacturer == m.manufacturer } &&
                            evaluate(next, false).none { it.manufacturer == m.manufacturer }
                    }
                    addAll(joined)
                }
            }
        }
        return matches.distinctBy { it.manufacturer to it.kind }
            .sortedWith(compareBy<Match> { if (it.kind == MatchKind.REVIEW_ONLY) 1 else 0 }
                .thenBy { it.manufacturer })
    }

    fun match(evidence: OcrEvidence): List<Match> {
        // Never join the last line of one unrelated text block with the first
        // line of the next. Keep multi-line matches within the same OCR block.
        return evidence.blocks.flatMap { block ->
            match(if (block.lines.isEmpty()) listOf(block.text) else block.lines.map { it.text })
        }.distinctBy { it.manufacturer to it.kind }
            .sortedWith(compareBy<Match> { if (it.kind == MatchKind.REVIEW_ONLY) 1 else 0 }
                .thenBy { it.manufacturer })
    }

    /** A single distinctive OCR brand is a suggestion, NOT a verified fact. */
    fun uniqueStrongCandidate(matches: List<Match>): Match? =
        matches.filter { it.kind != MatchKind.REVIEW_ONLY }
            .map { it.manufacturer }.distinct().singleOrNull()
            ?.let { selected ->
                matches.firstOrNull { it.manufacturer == selected && it.kind != MatchKind.REVIEW_ONLY }
            }
}
