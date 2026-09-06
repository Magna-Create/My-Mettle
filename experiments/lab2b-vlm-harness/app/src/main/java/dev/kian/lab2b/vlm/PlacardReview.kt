package dev.kian.lab2b.vlm

data class PlacardSelection(val value:String, val origin:String, val candidate:PlacardCandidate?=null)
/** Pure review state: any evidence change invalidates human edits and confirmation. */
class PlacardReview {
    var extraction=PlacardExtraction(emptyList()); private set
    var confirmed=false; private set
    val overrides=linkedMapOf<PlacardField,PlacardSelection>()
    fun replace(e:PlacardExtraction) { extraction=e; overrides.clear(); confirmed=false }
    fun selection(field:PlacardField):PlacardSelection? = overrides[field] ?: extraction.suggested(field)?.let { PlacardSelection(it.value,it.method,it) }
    fun choose(field:PlacardField,value:PlacardSelection) { require(value.value.length<=160); overrides[field]=value; confirmed=false }
    fun confirm(hasPlacardOcr:Boolean) { require(hasPlacardOcr) { "Run placard OCR first" }; confirmed=true }
}
enum class AddOnStatus { NOT_CHECKED, NONE, CAPTURED }
object WeightExportRules {
    fun canExport(mainReviewed:Boolean,addonStatus:AddOnStatus,addonReviewed:Boolean)=mainReviewed && (addonStatus!=AddOnStatus.CAPTURED || addonReviewed)
}
object NumericJson {
    // Android JSONArray(Collection) wraps BigDecimal as strings. Parse a numeric literal array instead.
    fun weights(values:List<java.math.BigDecimal>) = values.joinToString(prefix="[",postfix="]",separator=",") { it.stripTrailingZeros().toPlainString() }
}
