package dev.kian.lab2b.vlm

import java.math.BigDecimal
import kotlin.math.abs

enum class StackUnit { KG, LB }
/** Normalised to the current OCR crop. The centre can slope with perspective. */
data class ColumnSelection(val topX: Double, val bottomX: Double, val halfWidth: Double, val unit: StackUnit) {
    init { require(topX in 0.0..1.0 && bottomX in 0.0..1.0 && halfWidth in 0.005..0.5) }
    fun contains(box: OcrBox, width: Int, height: Int): Boolean {
        val y=(box.top+box.bottom)/2.0/height
        return abs((box.left+box.right)/2.0/width - (topX+(bottomX-topX)*y)) <= halfWidth
    }
}
data class ColumnNumber(val raw: String, val value: BigDecimal, val box: OcrBox, val unit: StackUnit?, val repaired: Boolean, val damagedUnit:Boolean=false)
data class ColumnProposal(val selection: ColumnSelection, val values: List<ColumnNumber>, val unitEvidence: String)

/** Geometry supplies grouping, never missing digits or unit certainty. */
object WeightColumns {
    private val numeric=Regex("^([0-9OoIl|]+(?:[.,][0-9OoIl|]+)?)\\s*(kgs?|lbs?|ka|ko|kog|k)?['’.,]?$",RegexOption.IGNORE_CASE)
    fun numbers(e: OcrEvidence): List<ColumnNumber> {
        val lines=e.blocks.flatMap { it.lines }
        return lines.mapNotNull { l ->
            val box=l.box ?: return@mapNotNull null
            val m=numeric.matchEntire(l.text.trim().replace(" ","")) ?: return@mapNotNull null
            val raw=m.groupValues[1]
            if(raw.none { it.isDigit() }) return@mapNotNull null
            val fixed=raw.map { when(it) { 'O','o' -> '0'; 'I','l','|' -> '1'; ',' -> '.'; else -> it } }.joinToString("")
            val value=fixed.toBigDecimalOrNull() ?: return@mapNotNull null
            if(value<=BigDecimal.ZERO || value>BigDecimal("5000")) return@mapNotNull null
            val inline=unit(m.groupValues[2])
            // A separate small label immediately above this number, with horizontal overlap.
            val nearby=lines.filter { label -> label.box?.let { b ->
                unit(label.text)!=null && ((b.bottom<=box.top && box.top-b.bottom<=maxOf(8,box.bottom-box.top) &&
                    (b.left+b.right)/2 in box.left..box.right) ||
                    (sameRow(box,b) && b.left>=box.right && b.left-box.right<=box.bottom-box.top))
            }==true }.mapNotNull { unit(it.text) }.distinct()
            ColumnNumber(l.text,value,box,inline ?: nearby.singleOrNull(),fixed!=raw,m.groupValues[2].isNotEmpty() && inline==null)
        }.sortedWith(compareBy({it.box.top},{it.box.left}))
    }
    private fun unit(s:String):StackUnit? = when(s.trim().lowercase()) { "kg","kgs" -> StackUnit.KG; "lb","lbs" -> StackUnit.LB; else -> null }
    private fun cy(n:ColumnNumber)=(n.box.top+n.box.bottom)/2.0
    private fun cx(n:ColumnNumber)=(n.box.left+n.box.right)/2.0
    fun sameRow(a:OcrBox,b:OcrBox):Boolean = abs((a.top+a.bottom-b.top-b.bottom)/2.0) <= maxOf(a.bottom-a.top,b.bottom-b.top)*0.65
    fun proposals(e:OcrEvidence):List<ColumnProposal> {
        val values=numbers(e)
        val rows=mutableListOf<MutableList<ColumnNumber>>()
        values.forEach { n ->
            val row=rows.lastOrNull()
            if(row!=null && sameRow(row.first().box,n.box)) row.add(n) else rows.add(mutableListOf(n))
        }
        val pairs=rows.filter { it.size==2 }.map { it.sortedBy(::cx) }
        val groups=if(pairs.size>=3) listOf(pairs.map { it[0] },pairs.map { it[1] }) else listOf(values)
        val leftLb=pairs.count { p -> abs(p[0].value.toDouble()/p[1].value.toDouble()-2.2046226218)<0.15 }
        val rightLb=pairs.count { p -> abs(p[1].value.toDouble()/p[0].value.toDouble()-2.2046226218)<0.15 }
        return groups.filter { it.size>=2 }.mapIndexed { index,group ->
            val ys=group.map { cy(it)/e.height };val xs=group.map { cx(it)/e.width }
            val ym=ys.average();val xm=xs.average()
            val denominator=ys.sumOf { (it-ym)*(it-ym) }
            val slope=if(denominator<1e-8) 0.0 else ys.indices.sumOf { (ys[it]-ym)*(xs[it]-xm) }/denominator
            val top=(xm-slope*ym).coerceIn(0.0,1.0);val bottom=(top+slope).coerceIn(0.0,1.0)
            val half=(group.maxOf { (it.box.right-it.box.left)/2.0/e.width }+0.015).coerceIn(0.02,0.25)
            val units=group.mapNotNull { it.unit }.distinct()
            val ratioUnit=when {
                pairs.size>=3 && leftLb>=3 && leftLb>=pairs.size*0.8 -> if(index==0) StackUnit.LB else StackUnit.KG
                pairs.size>=3 && rightLb>=3 && rightLb>=pairs.size*0.8 -> if(index==0) StackUnit.KG else StackUnit.LB
                else -> null
            }
            ColumnProposal(ColumnSelection(top,bottom,half,units.singleOrNull() ?: ratioUnit ?: StackUnit.KG),group,
                when { units.size>1 -> "Conflicting unit labels; choose the unit"; units.size==1 -> "OCR label: ${units.single()}";
                    ratioUnit!=null -> "Paired values suggest $ratioUnit; confirm on machine"; else -> "Unit unknown; confirm kg or lb" })
        }
    }
    fun parse(e:OcrEvidence,part:CapturePart,selection:ColumnSelection):WeightParse {
        val selected=numbers(e).filter { selection.contains(it.box,e.width,e.height) }
        val readings=selected.mapIndexed { i,n ->
            val conflict=n.unit!=null && n.unit!=selection.unit
            val kg=if(selection.unit==StackUnit.LB) n.value*BigDecimal("0.45359237") else n.value
            WeightReading(i,n.raw,kg,WeightOrigin.COLUMN_CONFIRMED,n.box,buildList {
                add("User assigned ${selection.unit} to selected column; printed value ${n.value}")
                if(selection.unit==StackUnit.LB) add("Converted lb to kg using 0.45359237; original value retained")
                if(n.repaired) add("Numeric character correction; check against image")
                if(n.damagedUnit) add("Damaged unit text; confirm against image")
                if(conflict) add("CONFLICT: OCR unit ${n.unit} differs from selected ${selection.unit}")
            },!n.repaired && !n.damagedUnit && !conflict)
        }
        return WeightParse(readings,e.blocks.flatMap { it.lines }.map { it.text }.filter { raw -> selected.none { it.raw==raw } },
            WeightOcrParser.issues(readings,part)+if(selected.any { it.unit!=null && it.unit!=selection.unit }) listOf("Unit conflict: affected rows are unchecked.") else emptyList())
    }
    /** Match physical rows, not numeric values. Disagreement is retained, never voted into truth. */
    fun compare(reference:OcrEvidence,other:OcrEvidence,selection:ColumnSelection):List<String> {
        require(reference.width==other.width && reference.height==other.height) { "Comparison requires identical crop geometry" }
        val a=numbers(reference).filter { selection.contains(it.box,reference.width,reference.height) }
        val b=numbers(other).filter { selection.contains(it.box,other.width,other.height) }
        return buildList {
            a.forEach { n ->
                val matches=b.filter { sameRow(n.box,it.box) }
                add("row y=${n.box.top}..${n.box.bottom}: ${n.raw} → ${if(matches.isEmpty()) "MISSING" else matches.joinToString(" / ") { it.raw }}"+
                    if(matches.size==1 && matches.single().value.compareTo(n.value)==0 && !n.repaired && !matches.single().repaired && !n.damagedUnit && !matches.single().damagedUnit) " [agrees]" else " [review]")
            }
            b.filter { n -> a.none { sameRow(it.box,n.box) } }.forEach { add("additional row y=${it.box.top}..${it.box.bottom}: ${it.raw} [review]") }
        }
    }
}
