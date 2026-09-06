package dev.kian.lab2b.vlm

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PlacardCapture(val region:CropRegion?=null,val image:SelectedImageInfo?=null,
    val profile:OcrEnhancement=OcrEnhancement.ORIGINAL,val input:SelectedImageInfo?=null,
    val evidence:OcrEvidence?=null,val totalMs:Long?=null,val cacheHit:Boolean=false)
object PlacardOwner {
    private lateinit var context:Context
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var initialised=false
    private var pendingImport:Uri?=null
    private var pendingExport:Uri?=null
    private var listener:(()->Unit)?=null
    private val captures=linkedMapOf<PlacardRegion,PlacardCapture>()
    private val cache=linkedMapOf<String,OcrEvidence>()
    val review=PlacardReview()
    var original:SelectedImageInfo?=null;private set
    var busy=false;private set
    var status="Choose one photo containing the placard and logo.";private set
    fun capture(region:PlacardRegion)=captures[region] ?: PlacardCapture()
    fun attach(c:Context,l:()->Unit) { context=c.applicationContext;listener=l
        if(!initialised) { initialised=true; operation("Restoring placard draft…",false) { restore() } } else l() }
    fun detach() { listener=null }
    private fun root()=File(context.filesDir,"lab2b/placards").apply { mkdirs() }
    private fun draft()=AtomicFile(File(root(),"draft.json"))
    private fun emit() { listener?.invoke() }
    private fun operation(label:String,persist:Boolean=true,block:suspend ()->Unit) {
        if(busy) return
        busy=true;status=label;emit()
        scope.launch {
            try { block(); if(persist) withContext(Dispatchers.IO) { save();cleanup() };status="Ready. Review what was found; unknown fields can stay blank." }
            catch(e:Exception) { status="Failed: ${e.message ?: e.javaClass.simpleName}" }
            finally { busy=false;emit()
                val next=pendingImport;pendingImport=null
                val exportTo=pendingExport;pendingExport=null
                if(next!=null) importImage(next) else if(exportTo!=null) export(exportTo)
            }
        }
    }
    private fun recompute() { review.replace(PlacardParser.extract(capture(PlacardRegion.PLACARD).evidence,capture(PlacardRegion.LOGO).evidence)) }
    fun importImage(uri:Uri) { if(busy) { pendingImport=uri;return };operation("Preparing source photo…") {
        val image=withContext(Dispatchers.IO) { StorageIo.copyImage(context,uri,"lab2b/placards/images") }
        original=image;captures.clear();captures[PlacardRegion.PLACARD]=PlacardCapture(image=image);recompute()
    } }
    fun crop(kind:PlacardRegion,region:CropRegion) { val source=original ?: return
        operation("Preparing ${kind.name.lowercase()} crop…") {
            val image=withContext(Dispatchers.Default) { CropImages.prepare(source,region) }
            captures[kind]=PlacardCapture(region,image,capture(kind).profile);recompute()
        }
    }
    fun clearLogo() { operation("Clearing logo crop…") { captures.remove(PlacardRegion.LOGO);recompute() } }
    fun clear() { operation("Clearing placard test…") { original=null;captures.clear();cache.clear();recompute() } }
    fun profile(kind:PlacardRegion,value:OcrEnhancement) { if(capture(kind).profile==value) return
        operation("Changing OCR input…") { val c=capture(kind);captures[kind]=PlacardCapture(c.region,c.image,value);recompute() }
    }
    fun run() { operation("Reading placard and logo…") {
        require(capture(PlacardRegion.PLACARD).image!=null) { "Choose a photo first" }
        // Clear previous reviewed fields before a retry, including a failed retry.
        review.replace(PlacardExtraction(emptyList()))
        PlacardRegion.entries.forEach { kind ->
            val old=capture(kind);val image=old.image ?: return@forEach
            val start=System.nanoTime()
            val input=withContext(Dispatchers.Default) { OcrImageEnhancer.prepare(image,old.profile) }
            val cached=cache[input.normalisedSha256]
            val evidence=cached ?: withContext(Dispatchers.Default) { OcrProcessor.recognise(input) }
            cache[input.normalisedSha256]=evidence
            while(cache.size>8) cache.remove(cache.keys.first())
            captures[kind]=old.copy(input=input,evidence=evidence,totalMs=(System.nanoTime()-start)/1_000_000,cacheHit=cached!=null)
        }
        recompute()
    } }
    fun choose(field:PlacardField,selection:PlacardSelection) { operation("Saving field review…") { review.choose(field,selection) } }
    fun confirm() { operation("Saving confirmation…") { review.confirm(capture(PlacardRegion.PLACARD).evidence!=null) } }
    fun export(uri:Uri) { if(busy) { pendingExport=uri;return };operation("Saving placard JSON…") {
        require(review.confirmed) { "Confirm the extracted fields first" }
        val content=json().toString(2)
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt").use { requireNotNull(it).write(content.toByteArray(Charsets.UTF_8)) } }
    } }
    private fun candidate(c:PlacardCandidate)=JSONObject().put("field",c.field.name).put("value",c.value).put("raw",c.raw)
        .put("region",c.region.name).put("source_sha256",c.sourceSha).put("method",c.method)
        .put("number",c.number ?: JSONObject.NULL).put("unit",c.unit ?: JSONObject.NULL)
        .put("box",c.box?.let { JSONArray(listOf(it.left,it.top,it.right,it.bottom)) } ?: JSONObject.NULL)
    fun json():JSONObject=JSONObject().put("schema","lab2b.placard.v1").put("app_version",BuildConfig.VERSION_NAME)
        .put("rules_version",PlacardParser.VERSION).put("created_utc",java.time.Instant.now().toString())
        .put("production_import_compatible",false).put("image_supplied_to_language_model",false)
        .put("engine","ML Kit OCR + deterministic field matching").put("reviewed",review.confirmed)
        .put("original",OcrJsonCodec.image(original))
        .put("fields",JSONObject().also { fields -> PlacardField.entries.forEach { field ->
            val s=review.selection(field)
            fields.put(field.name,JSONObject().put("value",s?.value?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("origin",s?.origin ?: "NOT_ESTABLISHED").put("evidence",s?.candidate?.let(::candidate) ?: JSONObject.NULL))
        } })
        .put("candidates",JSONArray(review.extraction.candidates.map(::candidate)))
        .put("captures",JSONObject().also { all -> PlacardRegion.entries.forEach { region ->
            val c=capture(region)
            all.put(region.name,JSONObject().put("crop",c.region?.let { JSONArray(listOf(it.left,it.top,it.right,it.bottom)) } ?: JSONObject.NULL)
                .put("image",OcrJsonCodec.image(c.image)).put("profile",c.profile.name).put("ocr_input",OcrJsonCodec.image(c.input))
                .put("ocr",TestReport.ocr(c.evidence)).put("total_ms",c.totalMs ?: JSONObject.NULL).put("cache_hit",c.cacheHit))
        } })
    private fun save() { val f=draft();val out=f.startWrite()
        try { out.write(json().toString().toByteArray(Charsets.UTF_8));f.finishWrite(out) } catch(e:Exception) { f.failWrite(out);throw e }
    }
    private suspend fun restore() {
        val data=withContext(Dispatchers.IO) { if(draft().baseFile.exists()) JSONObject(draft().openRead().bufferedReader().use { it.readText() }) else null } ?: return
        original=OcrJsonCodec.readImage(data.optJSONObject("original"))
        require(original==null || File(original!!.normalisedPath).isFile) { "Saved source photo is missing" }
        val all=data.getJSONObject("captures")
        PlacardRegion.entries.forEach { kind ->
            val c=all.getJSONObject(kind.name);val image=OcrJsonCodec.readImage(c.optJSONObject("image"));val input=OcrJsonCodec.readImage(c.optJSONObject("ocr_input"))
            val evidence=c.optJSONObject("ocr")?.let(OcrJsonCodec::readOcr)
            require(image==null || File(image.normalisedPath).isFile) { "Saved crop is missing" }
            require(evidence==null || input?.normalisedSha256==evidence.sourceImageSha256) { "Saved OCR source mismatch" }
            val region=c.optJSONArray("crop")?.let { CropRegion(kind.name,it.getDouble(0),it.getDouble(1),it.getDouble(2),it.getDouble(3)) }
            captures[kind]=PlacardCapture(region,image,OcrEnhancement.valueOf(c.getString("profile")),input,evidence,if(c.isNull("total_ms")) null else c.getLong("total_ms"),c.optBoolean("cache_hit"))
        }
        recompute()
        // Changed extraction rules require a new review, not an old confirmation applied to new candidates.
        if(data.optString("rules_version")==PlacardParser.VERSION) {
            val fields=data.getJSONObject("fields")
            PlacardField.entries.forEach { field -> val s=fields.getJSONObject(field.name)
                if(s.getString("origin")!="NOT_ESTABLISHED") {
                    val evidence=s.optJSONObject("evidence")
                    val chosen=evidence?.let { e -> review.extraction.choices(field).firstOrNull { it.value==e.optString("value") && it.raw==e.optString("raw") && it.sourceSha==e.optString("source_sha256") && it.region.name==e.optString("region") } }
                    if(evidence!=null) require(chosen!=null) { "Saved field evidence changed" }
                    review.choose(field,PlacardSelection(if(s.isNull("value")) "" else s.getString("value"),s.getString("origin"),chosen))
                }
            }
            if(data.optBoolean("reviewed")) review.confirm(capture(PlacardRegion.PLACARD).evidence!=null)
        }
    }
    private fun cleanup() {
        val sourceDir=original?.sourcePrivatePath?.let { File(it).parentFile?.canonicalPath }
        File(root(),"images").listFiles()?.filter { it.isDirectory && it.canonicalPath!=sourceDir }?.forEach { it.deleteRecursively() }
        if(sourceDir!=null) {
            val kept=captures.values.mapNotNull { it.image?.sourcePrivatePath?.let { p -> File(p).parentFile?.canonicalPath } }.toSet()
            File(sourceDir).listFiles()?.filter { it.isDirectory && it.name.startsWith("crop-") && it.canonicalPath !in kept }?.forEach { it.deleteRecursively() }
        }
    }
}
