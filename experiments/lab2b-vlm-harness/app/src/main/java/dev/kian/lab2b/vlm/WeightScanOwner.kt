package dev.kian.lab2b.vlm

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

/** Process owner: one OCR operation, no model engine or production imports. */
object WeightScanOwner {
    data class Capture(val original: SelectedImageInfo? = null, val image: SelectedImageInfo? = null,
        val crop: CropRegion? = null, val profile: OcrEnhancement = OcrEnhancement.ORIGINAL,
        val input: SelectedImageInfo? = null, val evidence: OcrEvidence? = null, val parsed: WeightParse? = null,
        val reviewed: Boolean = false, val totalMs: Long? = null)
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captures = mutableMapOf<CapturePart, Capture>()
    private val cache = linkedMapOf<String,OcrEvidence>()
    var selected = CapturePart.MAIN_STACK; private set
    var busy = false; private set
    var status = "Select or photograph the main stack."; private set
    private var listener: (() -> Unit)? = null
    fun capture(part: CapturePart = selected) = captures[part] ?: Capture()
    fun attach(c: Context, l: () -> Unit) { context = c.applicationContext; listener = l
        if (captures.isEmpty()) restore(); l() }
    fun detach() { listener = null }
    private fun changed() { listener?.invoke() }
    private fun root() = File(context.filesDir,"lab2b/stack-captures").apply { mkdirs() }
    private fun draft() = AtomicFile(File(root(),"draft.json"))
    fun select(part: CapturePart) { if (!busy) { selected=part; changed() } }
    private fun operation(label: String, block: suspend () -> Unit) {
        if (busy) return
        busy=true; status=label; changed()
        scope.launch {
            try { block(); withContext(Dispatchers.IO) { save(); cleanUnusedImages() }; status="Ready. Review the evidence and selected kg values." }
            catch (e: Exception) { status="Failed: ${e.message ?: e.javaClass.simpleName}" }
            finally { busy=false; changed() }
        }
    }
    fun importImage(uri: Uri) { val part=selected
        operation("Preparing source image…") {
            val image=withContext(Dispatchers.IO) { StorageIo.copyImage(context,uri,"lab2b/stack-captures/images") }
            captures[part]=Capture(original=image,image=image)
        }
    }
    fun crop(region: CropRegion) { val part=selected; val old=capture(); val source=old.original ?: return
        operation("Preparing crop…") {
            val image=withContext(Dispatchers.IO) { CropImages.prepare(source,region) }
            captures[part]=Capture(original=source,image=image,crop=region,profile=old.profile)
        }
    }
    fun profile(value: OcrEnhancement) { if (busy || capture().profile==value) return
        val part=selected; operation("Changing OCR input…") { captures[part]=capture(part).copy(profile=value,input=null,evidence=null,parsed=null,reviewed=false,totalMs=null) }
    }
    fun runOcr() { val part=selected; val old=capture(); val image=old.image ?: return
        operation("Running OCR and deterministic kg extraction…") {
            val start=System.nanoTime()
            val input=withContext(Dispatchers.Default) { OcrImageEnhancer.prepare(image,old.profile) }
            val evidence=cache[input.normalisedSha256] ?: withContext(Dispatchers.Default) { OcrProcessor.recognise(input) }
            cache[input.normalisedSha256]=evidence
            while (cache.size>8) cache.remove(cache.keys.first())
            val parsed=withContext(Dispatchers.Default) { WeightOcrParser.parse(evidence,part) }
            captures[part]=old.copy(input=input,evidence=evidence,parsed=parsed,reviewed=false,totalMs=(System.nanoTime()-start)/1_000_000)
        }
    }
    fun change(id: Int, included: Boolean? = null, edit: String? = null) { val part=selected
        operation("Saving review…") {
            val old=capture(part); val parsed=old.parsed ?: return@operation
            val readings=parsed.readings.map { if (it.id != id) it else if (edit != null) WeightOcrParser.edit(it,edit) else it.copy(included=included ?: it.included) }
            captures[part]=old.copy(parsed=parsed.copy(readings=readings,issues=WeightOcrParser.issues(readings,part)),reviewed=false)
        }
    }
    fun review() { val part=selected
        operation("Saving confirmation…") { val old=capture(part)
            require(old.parsed?.sortedKg?.isNotEmpty()==true) { "Select at least one kg value before confirming" }
            captures[part]=old.copy(reviewed=true)
        }
    }
    fun clear() { val part=selected
        operation("Clearing this capture…") { captures[part]=Capture() }
    }
    fun export(uri: Uri) { operation("Saving JSON draft…") {
        require(capture(CapturePart.MAIN_STACK).reviewed) { "Confirm the main-stack values first" }
        require(capture(CapturePart.ADD_ON).image==null || capture(CapturePart.ADD_ON).reviewed) { "Confirm or clear the add-on capture" }
        val json=json().toString(2)
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt").use { requireNotNull(it).write(json.toByteArray(Charsets.UTF_8)) } }
    } }
    fun json(): JSONObject = JSONObject().put("schema","lab2b.ocr-stack.v1")
        .put("app_version",BuildConfig.VERSION_NAME).put("created_utc",java.time.Instant.now().toString())
        .put("production_import_compatible",false).put("engine","DETERMINISTIC_KOTLIN; ML Kit OCR")
        .put("image_supplied_to_language_model",false).put("inferred_weights_kg",JSONArray())
        .put("main_stack_kg",JSONArray(capture(CapturePart.MAIN_STACK).parsed?.sortedKg ?: emptyList<BigDecimal>()))
        .put("separate_add_on_kg",JSONArray(capture(CapturePart.ADD_ON).parsed?.sortedKg ?: emptyList<BigDecimal>()))
        .put("add_on_quantity",JSONObject.NULL).put("add_on_engaged",JSONObject.NULL)
        .put("captures",JSONObject().also { target -> CapturePart.entries.forEach { part ->
            val c=capture(part); target.put(part.name,JSONObject().put("reviewed",c.reviewed).put("profile",c.profile.name)
                .put("original",imageJson(c.original)).put("crop_image",imageJson(c.image)).put("ocr_input",imageJson(c.input))
                .put("crop",c.crop?.let { JSONArray(listOf(it.left,it.top,it.right,it.bottom)) } ?: JSONObject.NULL)
                .put("ocr",TestReport.ocr(c.evidence)).put("total_ms",c.totalMs ?: JSONObject.NULL)
                .put("ocr_order","top-to-bottom then left; numeric output sorted separately")
                .put("warnings",JSONArray(c.parsed?.issues ?: emptyList<String>()))
                .put("ignored_lines",JSONArray(c.parsed?.ignored ?: emptyList<String>()))
                .put("readings",JSONArray(c.parsed?.readings?.map { r -> JSONObject().put("id",r.id).put("raw",r.raw)
                    .put("kg",r.kg).put("origin",r.origin.name).put("included",r.included)
                    .put("changes",JSONArray(r.changes)).put("box",r.box?.let { JSONArray(listOf(it.left,it.top,it.right,it.bottom)) } ?: JSONObject.NULL) } ?: emptyList<JSONObject>())))
        } })
    private fun imageJson(i: SelectedImageInfo?): Any = if (i==null) JSONObject.NULL else (TestReport.image(i) as JSONObject).put("source_private_path",i.sourcePrivatePath)
    private fun save() { val file=draft(); val out=file.startWrite()
        try { out.write(json().toString().toByteArray(Charsets.UTF_8)); file.finishWrite(out) }
        catch(e: Exception) { file.failWrite(out); throw e }
    }
    private fun cleanUnusedImages() {
        val images=File(root(),"images")
        val kept=captures.values.mapNotNull { it.original?.sourcePrivatePath?.let { p -> File(p).parentFile?.canonicalPath } }.toSet()
        images.listFiles()?.filter { it.isDirectory && it.canonicalPath !in kept }?.forEach { it.deleteRecursively() }
        captures.values.forEach { c ->
            val base=c.original?.sourcePrivatePath?.let { File(it).parentFile } ?: return@forEach
            val cropPath=c.image?.sourcePrivatePath?.let { File(it).parentFile?.canonicalPath }
            base.listFiles()?.filter { it.isDirectory && it.name.startsWith("crop-") && it.canonicalPath != cropPath }?.forEach { it.deleteRecursively() }
        }
    }
    private fun restore() {
        CapturePart.entries.forEach { captures[it]=Capture() }
        try {
            if (!draft().baseFile.exists()) return
            val all=JSONObject(draft().openRead().bufferedReader().use { it.readText() }).getJSONObject("captures")
            CapturePart.entries.forEach { part ->
                val c=all.getJSONObject(part.name)
                val original=readImage(c.optJSONObject("original")); val image=readImage(c.optJSONObject("crop_image"))
                val input=readImage(c.optJSONObject("ocr_input")); val e=c.optJSONObject("ocr")?.let(::readOcr)
                val readings=c.getJSONArray("readings").let { a -> (0 until a.length()).map { n -> val r=a.getJSONObject(n)
                    WeightReading(r.getInt("id"),r.getString("raw"),r.getString("kg").toBigDecimal(),WeightOrigin.valueOf(r.getString("origin")),readBox(r.optJSONArray("box")),strings(r.getJSONArray("changes")),r.getBoolean("included")) } }
                val crop=c.optJSONArray("crop")?.let { CropRegion(part.name,it.getDouble(0),it.getDouble(1),it.getDouble(2),it.getDouble(3)) }
                require(image==null || File(image.normalisedPath).isFile) { "Saved image is missing" }
                require(e==null || input?.normalisedSha256==e.sourceImageSha256) { "Saved OCR source mismatch" }
                captures[part]=Capture(original,image,crop,OcrEnhancement.valueOf(c.getString("profile")),input,e,
                    if(e==null) null else WeightParse(readings,strings(c.getJSONArray("ignored_lines")),WeightOcrParser.issues(readings,part)),c.getBoolean("reviewed"),if(c.isNull("total_ms")) null else c.getLong("total_ms"))
            }
            status="Restored saved captures and review. No model is loaded by this workflow."
        } catch(e: Exception) { CapturePart.entries.forEach { captures[it]=Capture() }; status="Could not restore draft: ${e.message}. Saved files retained." }
    }
    private fun strings(a: JSONArray)=(0 until a.length()).map { a.getString(it) }
    private fun readBox(a: JSONArray?)=a?.let { OcrBox(it.getInt(0),it.getInt(1),it.getInt(2),it.getInt(3)) }
    private fun corners(a: JSONArray)=(0 until a.length()).map { a.getJSONArray(it).let { p -> OcrPoint(p.getInt(0),p.getInt(1)) } }
    private fun readOcr(e: JSONObject): OcrEvidence {
        val blocks=e.getJSONArray("blocks")
        return OcrEvidence(e.getString("full_text"),(0 until blocks.length()).map { n -> val b=blocks.getJSONObject(n); val lines=b.getJSONArray("lines")
            OcrBlock(b.getString("text"),readBox(b.optJSONArray("box")),corners(b.getJSONArray("corners")),if(b.isNull("language")) null else b.getString("language"),
                (0 until lines.length()).map { j -> val l=lines.getJSONObject(j); OcrLine(l.getString("text"),readBox(l.optJSONArray("box")),corners(l.getJSONArray("corners")),if(l.isNull("language")) null else l.getString("language")) })
        },e.getLong("processing_ms"),e.getString("source_sha256"),e.getInt("width"),e.getInt("height"),e.getString("recognizer"))
    }
    private fun readImage(i: JSONObject?): SelectedImageInfo? = i?.let { SelectedImageInfo(
        sourceName=it.getString("source_name"),sourcePrivatePath=it.getString("source_private_path"),sourceBytes=it.getLong("source_bytes"),
        sourceWidth=it.getInt("source_width"),sourceHeight=it.getInt("source_height"),sourceSha256=it.getString("source_sha256"),orientation=it.getInt("exif_orientation"),normalisation=it.getString("normalisation"),
        normalisedPath=it.getString("normalised_path"),normalisedSha256=it.getString("normalised_sha256"),normalisedWidth=it.getInt("normalised_width"),normalisedHeight=it.getInt("normalised_height"),
        preparedPath=it.getString("prepared_path"),preparedSha256=it.getString("prepared_sha256"),preparedWidth=it.getInt("prepared_width"),preparedHeight=it.getInt("prepared_height"),preparedBytes=it.getLong("prepared_bytes")) }
}
