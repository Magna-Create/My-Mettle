package dev.kian.lab2b.vlm

/** Parse only the payload of an explicit starting-resistance label. Never infer from bare weights. */
object StartingResistanceParser {
    data class Value(val text:String,val number:Double,val unit:String,val method:String)
    private val measurement=Regex("([0-9OoIl|]+(?:[.,][0-9OoIl|]+)?)\\s*(kgs?|lbs?)\\.?",RegexOption.IGNORE_CASE)
    fun parse(payload:String):Value? {
        val parts=payload.trim().split(Regex("\\s*/\\s*"))
        if(parts.size !in 1..2) return null
        val values=parts.map { part ->
            val m=measurement.matchEntire(part.trim()) ?: return null
            val raw=m.groupValues[1]
            if(raw.none { it.isDigit() }) return null
            val fixed=raw.map { when(it) { 'O','o'->'0'; 'I','l','|'->'1'; ','->'.'; else->it } }.joinToString("")
            val n=fixed.toDoubleOrNull() ?: return null
            if(!n.isFinite() || n !in 0.0..2000.0) return null
            val unit=if(m.groupValues[2].startsWith("k",true)) "kg" else "lb"
            Value("$fixed $unit",n,unit,if(raw==fixed) "EXPLICIT_RESISTANCE_LABEL" else "NUMERIC_CORRECTION_CANDIDATE")
        }
        if(values.size==1) return values.single()
        if(values.map { it.unit }.distinct().size!=2) return null
        val kg=values.first { it.unit=="kg" };val lb=values.first { it.unit=="lb" }
        // Consistency check only; the returned kg is read from the placard, never generated from pounds.
        val consistent=kotlin.math.abs(kg.number-lb.number*0.45359237)<=maxOf(0.2,kg.number*0.03)
        val method=when {
            !consistent->"DUAL_UNIT_CONFLICT_CANDIDATE"
            values.any { it.method.contains("CANDIDATE") }->"NUMERIC_CORRECTION_CANDIDATE"
            else->"EXPLICIT_DUAL_UNIT_RESISTANCE; kg read directly; lb retained in raw evidence"
        }
        return kg.copy(method=method)
    }
}
