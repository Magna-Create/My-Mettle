package dev.kian.lab2b.vlm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

/** Guided OCR-only experiment. No image or OCR is dispatched to a language model here. */
class WeightScanActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private var cameraPath: String? = null
    private var cameraPart = CapturePart.MAIN_STACK
    private var pickerPart = CapturePart.MAIN_STACK
    private var rawExpanded = false
    private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) { WeightScanOwner.select(pickerPart); WeightScanOwner.importImage(uri) }
    }
    private val camera=registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        cameraPath?.let { path ->
            if(success) { WeightScanOwner.select(cameraPart); WeightScanOwner.importImage(uri(File(path))) }
            // Keep pending camera source until the bounded private import has completed.
        }
    }
    private val exporter=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(WeightScanOwner::export) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraPath=savedInstanceState?.getString("cameraPath")
        cameraPart=CapturePart.valueOf(savedInstanceState?.getString("cameraPart") ?: "MAIN_STACK")
        pickerPart=CapturePart.valueOf(savedInstanceState?.getString("pickerPart") ?: "MAIN_STACK")
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(24)) }
        scroll=ScrollView(this).apply { addView(root) }; setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
        }
        WeightScanOwner.attach(this,::render)
    }
    override fun onStart() { super.onStart(); WeightScanOwner.attach(this,::render) }
    override fun onStop() { WeightScanOwner.detach(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState)
        outState.putString("cameraPath",cameraPath); outState.putString("cameraPart",cameraPart.name); outState.putString("pickerPart",pickerPart.name) }
    private fun dp(v:Int)=(resources.displayMetrics.density*v).toInt()
    private fun text(value:String,size:Float=15f) = TextView(this).apply { text=value; textSize=size; setTextIsSelectable(true); setPadding(0,dp(7),0,dp(7)); root.addView(this) }
    private fun button(label:String, enabled:Boolean=true, action:()->Unit) = Button(this).apply {
        text=label; isAllCaps=false; isEnabled=enabled && !WeightScanOwner.busy; setOnClickListener { action() }; root.addView(this)
    }
    private fun copy(value:String) { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("LAB-2B OCR stack",value)); Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show() }
    private fun uri(file:File):Uri = FileProvider.getUriForFile(this,"$packageName.images",file)
    private fun open(path:String) { try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(File(path)),"image/png").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch(e:Exception) { Toast.makeText(this,e.message,Toast.LENGTH_LONG).show() } }
    private fun photograph() {
        try {
            val folder=File(filesDir,"lab2b/stack-camera").apply { mkdirs() }
            folder.listFiles()?.forEach { it.delete() }
            val file=File(folder,"capture-${System.currentTimeMillis()}.jpg")
            cameraPath=file.absolutePath; cameraPart=WeightScanOwner.selected; camera.launch(uri(file))
        } catch(e:Exception) { Toast.makeText(this,"Camera unavailable: ${e.message}. Use Choose image.",Toast.LENGTH_LONG).show() }
    }
    private fun crop() {
        val c=WeightScanOwner.capture(); val source=c.original ?: return
        try {
            val editor=CropEditor(this,source.preparedPath,c.crop)
            val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; addView(editor,LinearLayout.LayoutParams(-1,(resources.displayMetrics.heightPixels*0.55).toInt())) }
            val label=if(WeightScanOwner.selected==CapturePart.MAIN_STACK) "main stack" else "separate add-on"
            AlertDialog.Builder(this).setTitle("Drag a rectangle around $label")
                .setView(panel).setPositiveButton("Apply crop") { _,_ -> WeightScanOwner.crop(editor.region(label)) }
                .setNegativeButton("Cancel",null).show()
        } catch(e:Exception) { Toast.makeText(this,e.message,Toast.LENGTH_LONG).show() }
    }
    private fun edit(r:WeightReading) {
        val field=EditText(this).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(r.kg.toPlainString()) }
        AlertDialog.Builder(this).setTitle("Correct kg: ${r.raw}").setMessage("Check the actual label. This edit will be recorded as human supplied.")
            .setView(field).setPositiveButton("Save edit") { _,_ -> WeightScanOwner.change(r.id,edit=field.text.toString()) }.setNegativeButton("Cancel",null).show()
    }
    private fun render() {
        val y=scroll.scrollY; root.removeAllViews()
        val owner=WeightScanOwner; val c=owner.capture(); val main=owner.capture(CapturePart.MAIN_STACK); val addon=owner.capture(CapturePart.ADD_ON)
        text("OCR weight stack • 0.4",24f)
        text("No model load needed. Image → bundled OCR → kg extraction → human review → JSON draft.")
        text(owner.status,16f)
        text("1 · Choose the capture",20f)
        button("Main stack ${if(owner.selected==CapturePart.MAIN_STACK) "• selected" else ""}") { owner.select(CapturePart.MAIN_STACK) }
        button("Separate add-on ${if(owner.selected==CapturePart.ADD_ON) "• selected" else ""}") { owner.select(CapturePart.ADD_ON) }
        text(if(owner.selected==CapturePart.MAIN_STACK)
            "Keep the entire main stack and its kg labels in frame, including the small top plate. Exclude the separate add-on weights. Pounds may remain visible."
            else "Photograph the separate add-on label. Keep it out of the main-stack capture. We do not infer quantity or whether it is engaged.")
        button("Take photo") { photograph() }
        button("Choose image") { pickerPart=owner.selected; picker.launch(arrayOf("image/*")) }
        if(c.image!=null) {
            text("${c.original?.sourceName}\nCrop: ${c.image.normalisedWidth} × ${c.image.normalisedHeight}\n${if(c.crop==null) "Full frame; crop before OCR if needed." else "Manual crop applied."}")
            button("2 · Crop / adjust frame") { crop() }
            button("Open current crop") { open(c.image.normalisedPath) }
        }
        text("3 · OCR input",20f)
        button("Filter: ${c.profile.name.replace('_',' ')}",c.image!=null) {
            AlertDialog.Builder(this).setTitle("Experimental OCR filters")
                .setSingleChoiceItems(OcrEnhancement.entries.map { it.name.replace('_',' ') }.toTypedArray(),c.profile.ordinal) { dialog,n -> dialog.dismiss(); owner.profile(OcrEnhancement.entries[n]) }.setNegativeButton("Cancel",null).show()
        }
        text("Start with ORIGINAL. Filters can damage faint digits. Changing the crop or filter clears its previous OCR and review.")
        button("Run OCR + extract kg",c.image!=null) { owner.runOcr() }
        c.input?.let { input -> button("Open exact OCR input") { open(input.normalisedPath) }
            text("OCR input SHA-256: ${input.normalisedSha256}\n${input.normalisedWidth} × ${input.normalisedHeight}; ${input.normalisedPath}",12f) }
        c.evidence?.let { e ->
            text("OCR: ${e.processingMs} ms ${if(c.cacheHit) "(cached; original timing)" else "(fresh)"} · total workflow: ${c.totalMs} ms\n${e.blocks.size} blocks / ${e.blocks.sumOf { it.lines.size }} lines")
            button("Copy OCR • top to bottom") { copy(OcrFormatter.format(e,true)) }
            button(if(rawExpanded) "Hide raw OCR" else "Show raw OCR / ignored text") { rawExpanded=!rawExpanded; render() }
            if(rawExpanded) text(OcrFormatter.format(e,true),12f)
        }
        c.parsed?.let { parsed ->
            text("4 · Review kg labels",20f)
            text("Rows stay in image order. Checked values enter the sorted list. Corrections start unchecked: inspect the label, then check or edit. Uncheck any add-on or unrelated label.")
            parsed.readings.forEach { r ->
                val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; root.addView(this) }
                val check=CheckBox(this).apply { text="${r.raw} → ${r.kg.toPlainString()} kg\n${r.origin.name.replace('_',' ')}"; isChecked=r.included; isEnabled=!owner.busy }
                row.addView(check,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
                check.setOnCheckedChangeListener { _,checked -> owner.change(r.id,included=checked) }
                row.addView(Button(this).apply { text="Edit"; isAllCaps=false; isEnabled=!owner.busy; setOnClickListener { edit(r) } })
                if(r.changes.isNotEmpty()) text(r.changes.joinToString("\n"),12f)
            }
            text("Selected kg, smallest first:\n${parsed.sortedKg.joinToString { it.toPlainString() }}",17f)
            text(if(parsed.issues.isEmpty()) "No order/gap warning detected. This does not prove completeness." else parsed.issues.joinToString("\n\n"))
            text("Ignored ${parsed.ignored.size} other lines. No pounds converted. No missing weights invented.")
            if(rawExpanded) text(parsed.ignored.joinToString("\n"),12f)
            button(if(c.reviewed) "Values confirmed ✓" else "Confirm I checked these values",parsed.sortedKg.isNotEmpty()) { owner.review() }
        }
        button("Clear this capture",c.image!=null) { AlertDialog.Builder(this).setMessage("Clear this capture and its review? The other capture is retained.")
            .setPositiveButton("Clear") { _,_ -> owner.clear() }.setNegativeButton("Cancel",null).show() }
        text("5 · Save combined result",20f)
        text("Main stack: ${if(main.reviewed) "confirmed" else "needs review"}\nAdd-on: ${if(addon.image==null) "not captured (optional)" else if(addon.reviewed) "confirmed" else "needs review"}")
        text("This is a LAB-2B JSON draft with source evidence, corrections and warnings. My Mettle production import is not connected.")
        button("Save reviewed JSON",main.reviewed && (addon.image==null || addon.reviewed)) { exporter.launch("lab2b-ocr-stack-${System.currentTimeMillis()}.json") }
        button("Copy diagnostic JSON") { copy(owner.json().toString(2)) }
        button("Open model comparison harness") { startActivity(Intent(this,MainActivity::class.java)); finish() }
        scroll.post { scroll.scrollTo(0,y) }
    }
}
