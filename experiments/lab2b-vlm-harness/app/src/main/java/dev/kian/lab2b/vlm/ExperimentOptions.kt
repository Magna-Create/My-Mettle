package dev.kian.lab2b.vlm

import kotlin.math.roundToInt

data class GenerationOptions(val thinking: Boolean = false, val maxTokens: Int = 512) {
    fun validate(modelId: String) {
        require(maxTokens in listOf(512, 1024, 2048, 4096)) { "Unsupported output budget" }
        require(!thinking || modelId.startsWith("gemma4-")) { "Thinking control is verified for Gemma templates only" }
    }
}
/** Ratios in the orientation-normalised ORIGINAL image, never relative to a previous crop. */
data class CropRegion(val label: String, val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(label.isNotBlank() && label.length <= 80)
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0.0..1.0 })
        require(right > left && bottom > top) { "Crop must have positive area" }
    }
    fun pixels(width: Int, height: Int): List<Int> {
        require(width > 0 && height > 0)
        val l = (left * width).roundToInt().coerceIn(0, width - 1)
        val t = (top * height).roundToInt().coerceIn(0, height - 1)
        val r = (right * width).roundToInt().coerceIn(l + 1, width)
        val b = (bottom * height).roundToInt().coerceIn(t + 1, height)
        return listOf(l, t, r, b)
    }
}
object GemmaOutput {
    // Keep the raw stream in the export; render only the final channel in the answer card.
    fun finalAnswer(raw: String): String {
        if (!raw.contains("<|channel>thought")) return raw
        val end = raw.indexOf("<channel|>", raw.indexOf("<|channel>thought"))
        return if (end < 0) "" else raw.substring(end + "<channel|>".length)
            .removePrefix("<|channel>final").trimStart()
    }
}
object ExperimentPrompts {
    val weights = """Extract the weight labels from this image.
Return only these sections:
MAIN STACK KG: unique readable kilogram values, smallest to largest.
SEPARATE ADD-ON WEIGHTS: kilogram value and visible quantity of separate small add-on weights; otherwise 'not established'.
UNCERTAIN: obscured, unreadable or uncertain-unit labels.
Read printed kg values only. Ignore pounds; never convert. Never fill missing values by guessing a sequence. Decimal values are not automatically add-ons. Do not infer engagement or calculate combined/selected loads. OCR may be wrong: verify against the image. Report uncertainty instead of guessing. Do not add an equipment description."""
    val locate = """Locate regions for reading equipment weight labels. Do NOT extract weights.
Return ONLY JSON: {"regions":[{"label":"main stack","box":[0.0,0.0,1.0,1.0]}]}.
Each box is [left,top,right,bottom], ratios from 0 to 1 in this full image. Propose one region covering ALL main-stack labels and separate regions for visible add-on weights. Keep enough context to identify the object and its units. Maximum 4 regions. If uncertain return {"regions":[]}. Never invent a region."""
}

object ThinkingPrompt {
    fun apply(turn: InferenceTurn, enabled: Boolean): InferenceTurn {
        if (!enabled) return turn
        require(turn.systemMode != SystemPromptMode.USER_PREFACE_FALLBACK)
        return turn.copy(system = "<|think|>\n" + (turn.system ?: ""), systemMode = SystemPromptMode.TRUE_SYSTEM_ROLE)
    }
}

object EnergyMath {
    fun microJouleDelta(start: Long, end: Long, startMs: Long, endMs: Long): Double? =
        if (start < 0 || end <= start || endMs <= startMs) null else (end - start) / 1_000_000.0
}
