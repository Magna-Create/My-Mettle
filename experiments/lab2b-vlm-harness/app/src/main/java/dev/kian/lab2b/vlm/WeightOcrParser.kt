package dev.kian.lab2b.vlm

import java.math.BigDecimal

enum class CapturePart { MAIN_STACK, ADD_ON }
enum class OcrEnhancement { ORIGINAL, GREYSCALE_CONTRAST, GREYSCALE_SHARPEN, BLACK_WHITE }
enum class WeightOrigin { RECOGNISED_KG, CHARACTER_CORRECTION, UNIT_CANDIDATE, HUMAN_EDITED, COLUMN_CONFIRMED }
data class WeightReading(val id: Int, val raw: String, val kg: BigDecimal, val origin: WeightOrigin,
    val box: OcrBox?, val changes: List<String>, val included: Boolean)
data class WeightParse(val readings: List<WeightReading>, val ignored: List<String>, val issues: List<String>) {
    val sortedKg: List<BigDecimal> get() = readings.filter { it.included }.map { it.kg.stripTrailingZeros() }.distinct().sorted()
}
/** Strict whole-line parsing. No lb conversion, sequence completion, global text substitution or LLM. */
object WeightOcrParser {
    private val label = Regex("^([0-9OoIl|]+(?:[.,][0-9OoIl|]+)?)\\s*(kgs?|ka|ko|kog|k)\\s*['’.,]?$", RegexOption.IGNORE_CASE)
    fun parse(evidence: OcrEvidence, part: CapturePart): WeightParse {
        val readings = mutableListOf<WeightReading>(); val ignored = mutableListOf<String>()
        val geometryNumbers=WeightColumns.numbers(evidence)
        // Geometry order is preserved independently of the final numeric sorting.
        val lines = evidence.blocks.flatMap { it.lines }.sortedWith(compareBy({ it.box?.top ?: Int.MAX_VALUE }, { it.box?.left ?: Int.MAX_VALUE }))
        lines.forEachIndexed { index, line ->
            val nearbyKg=geometryNumbers.any { it.raw==line.text && it.box==line.box && it.unit==StackUnit.KG } &&
                !line.text.contains(Regex("kg",RegexOption.IGNORE_CASE))
            val compact = (line.text + if(nearbyKg) " kg" else "").trim().replace(Regex("\\s+"), " ")
            // Allow spaces within a numeric label (e.g. '1 lkg') only after a strict whole-line match.
            val match = label.matchEntire(compact.replace(" ", ""))
            if (match == null) { ignored += line.text; return@forEachIndexed }
            val numeric = match.groupValues[1]
            if (numeric.none { it.isDigit() }) { ignored += line.text; return@forEachIndexed }
            val fixed = numeric.map { when (it) { 'O','o' -> '0'; 'I','l','|' -> '1'; ',' -> '.'; else -> it } }.joinToString("")
            val value = fixed.toBigDecimalOrNull()
            if (value == null || value <= BigDecimal.ZERO || value > BigDecimal("2000")) { ignored += line.text; return@forEachIndexed }
            val unitExact = match.groupValues[2].equals("kg", true) || match.groupValues[2].equals("kgs", true)
            val changes = buildList {
                if(nearbyKg) add("kg label associated by nearby geometry")
                if (fixed != numeric) add("Numeric candidate: $numeric → $fixed")
                if (!unitExact) add("Unit candidate: ${match.groupValues[2]} → kg; confirm from label")
            }
            val origin = when { !unitExact -> WeightOrigin.UNIT_CANDIDATE; fixed != numeric -> WeightOrigin.CHARACTER_CORRECTION; else -> WeightOrigin.RECOGNISED_KG }
            readings += WeightReading(index, line.text, value, origin, line.box, changes, origin == WeightOrigin.RECOGNISED_KG)
        }
        return WeightParse(readings, ignored, issues(readings, part))
    }
    fun issues(readings: List<WeightReading>, part: CapturePart): List<String> {
        if (part == CapturePart.ADD_ON) return emptyList()
        val candidates = readings.map { it.kg }
        val deltas = candidates.zipWithNext { a,b -> b-a }
        val positive = deltas.filter { it > BigDecimal.ZERO }.sorted()
        val typical = positive.getOrNull(positive.size / 2)
        return buildList {
            if (readings.any { it.box == null }) add("Some labels have no geometry; physical order cannot be fully checked.")
            deltas.forEachIndexed { i,d ->
                if (d <= BigDecimal.ZERO) add("Order/duplicate issue before '${readings[i+1].raw}'. Numeric sorting does not resolve this.")
                if (typical != null && positive.size >= 4 && d > typical * BigDecimal("1.6"))
                    add("Possible gap between ${candidates[i]} and ${candidates[i+1]} kg; typical observed step ≈ $typical kg. Could be a missing label or intentional increment. No weight inserted.")
            }
        }
    }
    fun edit(reading: WeightReading, text: String): WeightReading {
        require(Regex("[0-9]+(?:[.,][0-9]+)?").matches(text.trim())) { "Enter a positive kg number" }
        val value = text.trim().replace(',', '.').toBigDecimal()
        require(value > BigDecimal.ZERO && value <= BigDecimal("2000"))
        return reading.copy(kg = value, origin = WeightOrigin.HUMAN_EDITED, included = true,
            changes = reading.changes + "Human edit: ${reading.kg} → $value kg")
    }
}
