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

class PlacardActivity:AppCompatActivity() {
    private lateinit var root:LinearLayout
    private lateinit var scroll:ScrollView
    private var cameraPath:String?=null
    private var diagnostics=false
    private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(PlacardOwner::importImage) }
    private val camera=registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if(ok) cameraPath?.let { PlacardOwner.importImage(uri(File(it))) } }
    private val exporter=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(PlacardOwner::export) }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);cameraPath=state?.getString("cameraPath")
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(24)) }
        scroll=ScrollView(this).apply { addView(root) };setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view,insets -> val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets }
        PlacardOwner.attach(this,::render)
    }
    override fun onStart() { super.onStart();PlacardOwner.attach(this,::render) }
    override fun onStop() { PlacardOwner.detach();super.onStop() }
    override fun onSaveInstanceState(out:Bundle) { super.onSaveInstanceState(out);out.putString("cameraPath",cameraPath) }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float=15f) { root.addView(TextView(this).apply { text=value;textSize=size;setTextIsSelectable(true);setPadding(0,dp(7),0,dp(7)) }) }
    private fun button(label:String,enabled:Boolean=true,action:()->Unit) { root.addView(Button(this).apply { text=label;isAllCaps=false;isEnabled=enabled && !PlacardOwner.busy;setOnClickListener { action() } }) }
    private fun uri(file:File):Uri=FileProvider.getUriForFile(this,"$packageName.images",file)
    private fun copy(text:String) { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("LAB-2B placard",text));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show() }
    private fun open(path:String) {
        try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(File(path)),"image/png").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch(e:android.content.ActivityNotFoundException) { val view=ImageView(this).apply { setImageURI(uri(File(path)));adjustViewBounds=true };AlertDialog.Builder(this).setTitle("Exact OCR image").setView(view).setPositiveButton("Close",null).show() }
    }
    private fun photograph() { try {
        val folder=File(filesDir,"lab2b/placard-camera").apply { mkdirs() };folder.listFiles()?.forEach { it.delete() }
        val f=File(folder,"capture-${System.currentTimeMillis()}.jpg");cameraPath=f.absolutePath;camera.launch(uri(f))
    } catch(e:Exception) { Toast.makeText(this,"Camera unavailable: ${e.message}. Choose image instead.",Toast.LENGTH_LONG).show() } }
    private fun crop(kind:PlacardRegion) {
        val source=PlacardOwner.original ?: return
        try {
            val editor=CropEditor(this,source.preparedPath,PlacardOwner.capture(kind).region)
            val column=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL
                addView(TextView(this@PlacardActivity).apply { text=if(kind==PlacardRegion.PLACARD) "Draw around the entire placard. No individual field boxes needed." else "Draw around the logo lettering in this same photo." })
                addView(editor,LinearLayout.LayoutParams(-1,(resources.displayMetrics.heightPixels*0.55).toInt())) }
            AlertDialog.Builder(this).setTitle(if(kind==PlacardRegion.PLACARD) "1 · Placard box" else "2 · Logo box")
                .setView(column).setPositiveButton("Apply") { _,_ -> PlacardOwner.crop(kind,editor.region(kind.name)) }.setNegativeButton("Cancel",null).show()
        } catch(e:Exception) { Toast.makeText(this,e.message,Toast.LENGTH_LONG).show() }
    }
    private fun fieldOptions(field:PlacardField) {
        val choices=PlacardOwner.review.extraction.choices(field)
        val labels=choices.map { "${it.value}\n${it.region.name}: ${it.raw}\n${it.method}" } + listOf("Leave unknown / clear","Enter correction manually")
        AlertDialog.Builder(this).setTitle(field.label).setItems(labels.toTypedArray()) { _,n ->
            when { n<choices.size -> { val c=choices[n];PlacardOwner.choose(field,PlacardSelection(c.value,"HUMAN_CONFIRMED_CANDIDATE",c)) }
                n==choices.size -> PlacardOwner.choose(field,PlacardSelection("","HUMAN_CLEARED"))
                else -> { val input=EditText(this).apply { setText(PlacardOwner.review.selection(field)?.value ?: "");filters=arrayOf(android.text.InputFilter.LengthFilter(160)) }
                    AlertDialog.Builder(this).setTitle("Correct ${field.label.lowercase()}").setMessage("Copy what the placard states. Include units for resistance; preserve the printed ratio order.")
                        .setView(input).setPositiveButton("Save") { _,_ -> PlacardOwner.choose(field,PlacardSelection(input.text.toString().trim(),"HUMAN_EDITED")) }.setNegativeButton("Cancel",null).show() }
            }
        }.setNegativeButton("Cancel",null).show()
    }
    private fun render() {
        val y=scroll.scrollY;root.removeAllViews();val owner=PlacardOwner
        text("Placard extraction • ${BuildConfig.VERSION_NAME}",23f)
        button("Switch to weight extraction") { startActivity(Intent(this,WeightScanActivity::class.java));finish() }
        text(owner.status)
        text("One photo, two boxes",20f)
        text("Crop the whole placard, then its logo if useful. Fields are extracted from the text automatically. No model download or load.")
        button("Take photo") { photograph() };button("Choose image") { picker.launch(arrayOf("image/*")) }
        owner.original?.let { text(it.sourceName) }
        button("1 · Draw placard box",owner.original!=null) { crop(PlacardRegion.PLACARD) }
        button("2 · Draw logo box",owner.original!=null) { crop(PlacardRegion.LOGO) }
        button("Skip / clear logo box",owner.original!=null) { owner.clearLogo() }
        PlacardRegion.entries.forEach { kind -> val c=owner.capture(kind)
            if(c.image!=null) {
                text("${kind.name}: ${c.image.normalisedWidth} × ${c.image.normalisedHeight} • ${if(c.region==null) "full frame" else "cropped"}")
                button("${kind.name} filter: ${c.profile.name.replace('_',' ')}") {
                    AlertDialog.Builder(this).setTitle("OCR filter • ${kind.name}").setSingleChoiceItems(OcrEnhancement.entries.map { it.name.replace('_',' ') }.toTypedArray(),c.profile.ordinal) { d,n -> d.dismiss();owner.profile(kind,OcrEnhancement.entries[n]) }.setNegativeButton("Cancel",null).show()
                }
                button("Open ${kind.name.lowercase()} ${if(c.input==null) "crop" else "exact OCR input"}") { open((c.input ?: c.image).normalisedPath) }
                c.evidence?.let { text("${kind.name} OCR ${it.processingMs} ms ${if(c.cacheHit) "(cached; original timing)" else "(fresh)"}; total ${c.totalMs} ms") }
            }
        }
        button("Read placard + logo",owner.original!=null) { owner.run() }
        text("What we found",20f)
        text("Blank means not established. Missing specifications need no manual entry. Conflicting or spelling-only matches need a choice.")
        PlacardField.entries.forEach { field ->
            val s=owner.review.selection(field);val choices=owner.review.extraction.choices(field)
            text("${field.label}: ${s?.value?.takeIf { it.isNotBlank() } ?: "Not established"}",17f)
            s?.candidate?.let { text("From ${it.region.name.lowercase()}: ${it.raw}",12f) }
            if(s?.origin=="HUMAN_EDITED") text("Human-entered correction",12f)
            button("${if(choices.isEmpty()) "Review / correct" else "Review ${choices.map { it.value }.distinct().size} candidate(s)"} · ${field.label}",owner.capture(PlacardRegion.PLACARD).evidence!=null) { fieldOptions(field) }
        }
        text("Ratios stay exactly as labelled; no stack-load conversion is inferred. Starting resistance is separate from stack settings.",13f)
        button(if(owner.review.confirmed) "Fields confirmed ✓" else "Confirm fields, leaving unknowns blank",owner.capture(PlacardRegion.PLACARD).evidence!=null) { owner.confirm() }
        button("Save reviewed placard JSON",owner.review.confirmed) { exporter.launch("lab2b-placard-${System.currentTimeMillis()}.json") }
        button("Copy diagnostic JSON") { copy(owner.json().toString(2)) }
        button(if(diagnostics) "Hide OCR evidence" else "Show / copy OCR evidence") { diagnostics=!diagnostics;render() }
        if(diagnostics) PlacardRegion.entries.forEach { kind -> val c=owner.capture(kind);c.evidence?.let { e ->
            text("${kind.name}: source ${e.sourceImageSha256}",12f);button("Copy ${kind.name.lowercase()} OCR") { copy(OcrFormatter.format(e,true)) };text(OcrFormatter.format(e,true),12f)
        } }
        button("Clear placard test",owner.original!=null) { AlertDialog.Builder(this).setMessage("Clear this photo, both crops and all extracted fields?").setPositiveButton("Clear") { _,_ -> owner.clear() }.setNegativeButton("Cancel",null).show() }
        text("LAB-2B draft export. Production import and machine masking are not connected.",12f)
        scroll.post { scroll.scrollTo(0,y) }
    }
}
