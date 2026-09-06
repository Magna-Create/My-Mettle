package dev.kian.lab2b.vlm

import java.util.Locale

enum class PlacardRegion { PLACARD, LOGO }
enum class PlacardField(val label: String) {
    BRAND("Brand"), MACHINE_NAME("Machine name"), MODEL_ID("Model identifier"),
    STARTING_RESISTANCE("Starting resistance"), PULLEY_RATIO("Labelled ratio")
}
data class PlacardCandidate(val field: PlacardField, val value: String, val raw: String,
    val region: PlacardRegion, val sourceSha: String, val box: OcrBox?, val method: String,
    val number: Double? = null, val unit: String? = null)
data class PlacardExtraction(val candidates: List<PlacardCandidate>) {
    fun choices(field: PlacardField) = candidates.filter { it.field==field }
    fun suggested(field: PlacardField): PlacardCandidate? {
        val all=choices(field)
        // A fuzzy match is never silently selected, even if it is the only result.
        return all.takeIf { it.map { c -> c.value.lowercase(Locale.ROOT) }.distinct().size==1 }
            ?.firstOrNull { !it.method.contains("CANDIDATE") }
    }
}
/** Label-specific text extraction, not equipment catalogue lookup or visual recognition. */
object PlacardParser {
    const val VERSION="placard-rules-2"
    val brands=listOf("Life Fitness","Technogym","Precor","Matrix","Hammer Strength","Cybex","Panatta",
        "Atlantis","Nautilus","Hoist","Prime","TuffStuff","Watson","Pulse","Spirit","Star Trac",
        "Body-Solid","Eleiko","Keiser","Freemotion","Gym80","Torque Fitness","Arsenal Strength")
    private val machineNames=listOf("chest press","incline chest press","decline chest press","shoulder press","leg press",
        "hack squat","pendulum squat","belt squat","leg extension","leg curl","seated leg curl","lying leg curl",
        "lat pulldown","lat pull down","low row","seated row","high row","pullover","pec fly","pec deck",
        "rear delt","rear deltoid","hip abductor","hip adductor","abductor / adductor","abdominal crunch",
        "back extension","biceps curl","bicep curl","triceps extension","tricep extension","assisted chin dip",
        "assisted pull up","calf raise","seated calf","standing calf","glute drive","hip thrust","smith machine",
        "functional trainer","cable crossover","dual adjustable pulley","dip","row")
    private fun normal(text:String)=text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"),"")
    private val starting=Regex("^(?:starting (?:resistance|weight|load)|start(?: resistance)?|initial resistance|unloaded resistance)\\b\\s*[:=–-]?\\s*(.*)$",RegexOption.IGNORE_CASE)
    private val ratio=Regex("^((?:pulley|cable|resistance|weight) ratio)\\b\\s*[:=–-]?\\s*(.*)$",RegexOption.IGNORE_CASE)
    private val machine=Regex("^(?:machine(?: name)?|equipment name|exercise name)\\s*[:=]\\s*(.*)$",RegexOption.IGNORE_CASE)
    private val model=Regex("^(?:model(?: (?:no\\.?|number|id))?|product code)\\b\\s*[:=#-]?\\s*(.*)$",RegexOption.IGNORE_CASE)
    private val brand=Regex("^(?:brand|manufacturer)\\s*[:=]\\s*(.*)$",RegexOption.IGNORE_CASE)
    fun extract(placard:OcrEvidence?,logo:OcrEvidence?):PlacardExtraction {
        val found=mutableListOf<PlacardCandidate>()
        fun scan(e:OcrEvidence,region:PlacardRegion) {
            val lines=e.blocks.flatMap { it.lines }.sortedWith(compareBy({it.box?.top ?: Int.MAX_VALUE},{it.box?.left ?: Int.MAX_VALUE}))
            fun candidate(field:PlacardField,value:String,line:OcrLine,method:String,number:Double?=null,unit:String?=null,raw:String=line.text) {
                if(value.isNotBlank() && value.length<=160) found+=PlacardCandidate(field,value,raw,region,e.sourceImageSha256,line.box,method,number,unit)
            }
            fun valueBelow(index:Int,value:String):Pair<String,String> {
                val line=lines[index]; if(value.isNotBlank()) return value to line.text
                val next=lines.getOrNull(index+1) ?: return "" to line.text
                val a=line.box ?: return "" to line.text; val b=next.box ?: return "" to line.text
                val overlap=minOf(a.right,b.right)-maxOf(a.left,b.left)
                return if(b.top>=a.bottom-3 && b.top-a.bottom<=maxOf(40,(a.bottom-a.top)*3) && overlap>0)
                    next.text.trim() to "${line.text}\n${next.text}" else "" to line.text
            }
            lines.forEachIndexed { index,line ->
                val text=line.text.trim().replace(Regex("\\s+")," "); val compact=normal(text)
                brands.filter { normal(it)==compact }.forEach { candidate(PlacardField.BRAND,it,line,"EXACT_BRAND_TEXT") }
                // Logo-only conservative typo candidates. No global correction of names or identifiers.
                if(region==PlacardRegion.LOGO && compact.length>=6 && text.length<=40 && brands.none { normal(it)==compact }) {
                    brands.filter { normal(it).length>=6 && editDistanceOne(compact,normal(it)) }
                        .forEach { candidate(PlacardField.BRAND,it,line,"BRAND_SPELLING_CANDIDATE") }
                }
                // Adjacent lines support stacked wordmarks, e.g. LIFE / FITNESS.
                lines.getOrNull(index+1)?.let { next ->
                    val a=line.box; val b=next.box
                    if(a!=null && b!=null && b.top>=a.top && b.top-a.bottom<=maxOf(40,3*(a.bottom-a.top)) && minOf(a.right,b.right)>maxOf(a.left,b.left)) {
                        brands.filter { normal(it)==normal(text+next.text) }.forEach {
                            candidate(PlacardField.BRAND,it,line,"EXACT_STACKED_BRAND_TEXT",raw="${line.text}\n${next.text}")
                        }
                    }
                }
                if(region==PlacardRegion.LOGO) return@forEachIndexed
                brand.matchEntire(text)?.let { m -> val (v,raw)=valueBelow(index,m.groupValues[1]); candidate(PlacardField.BRAND,v,line,"EXPLICIT_BRAND_LABEL",raw=raw) }
                machine.matchEntire(text)?.let { m -> val (v,raw)=valueBelow(index,m.groupValues[1]); candidate(PlacardField.MACHINE_NAME,v,line,"EXPLICIT_MACHINE_LABEL",raw=raw) }
                model.matchEntire(text)?.let { m -> val (v,raw)=valueBelow(index,m.groupValues[1]); if(v.length in 2..48 && v.any { it.isLetterOrDigit() } && (text.contains(Regex("[:=#]")) || (v.none { it.isWhitespace() } && v.any { it.isDigit() }))) candidate(PlacardField.MODEL_ID,v,line,"EXPLICIT_MODEL_LABEL",raw=raw) }
                if(machineNames.any { normal(it)==compact }) candidate(PlacardField.MACHINE_NAME,text,line,"EXACT_MACHINE_PHRASE")
                starting.matchEntire(text)?.let { m ->
                    val (v,raw)=valueBelow(index,m.groupValues[1])
                    StartingResistanceParser.parse(v)?.let { result ->
                        candidate(PlacardField.STARTING_RESISTANCE,result.text,line,result.method,result.number,result.unit,raw)
                    }
                }
                ratio.matchEntire(text)?.let { m -> val (v,raw)=valueBelow(index,m.groupValues[2])
                    val r=Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)$").matchEntire(v)
                    if(r!=null && r.groupValues.drop(1).all { it.toDoubleOrNull()?.let { n -> n>0 && n<=1000 }==true })
                        candidate(PlacardField.PULLEY_RATIO,"${r.groupValues[1]}:${r.groupValues[2]}",line,"EXPLICIT_RATIO_LABEL; direction retained, no load conversion",raw=raw)
                }
            }
        }
        placard?.let { scan(it,PlacardRegion.PLACARD) }; logo?.let { scan(it,PlacardRegion.LOGO) }
        return PlacardExtraction(found.distinctBy { listOf(it.field,it.value,it.raw,it.region,it.box,it.method) })
    }
    private fun editDistanceOne(a:String,b:String):Boolean {
        if(kotlin.math.abs(a.length-b.length)>1 || a==b) return false
        var i=0;var j=0;var edits=0
        while(i<a.length && j<b.length) {
            if(a[i]==b[j]) { i++;j++ } else {
                if(++edits>1) return false
                when { a.length>b.length->i++; b.length>a.length->j++; else->{i++;j++} }
            }
        }
        return edits+(a.length-i)+(b.length-j)==1
    }
}
