package dev.kian.lab2b.vlm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

/** Intentionally compact developer controls. The transcript is display-only. */
class MainActivity : AppCompatActivity() {
    private lateinit var model: Spinner
    private lateinit var backend: Spinner
    private lateinit var pipeline: Spinner
    private lateinit var preset: Spinner
    private lateinit var modelsText: TextView
    private lateinit var runtimeText: TextView
    private lateinit var imageText: TextView
    private lateinit var ocrText: TextView
    private lateinit var customText: TextView
    private lateinit var output: TextView
    private lateinit var diagnostics: TextView
    private lateinit var transcript: TextView
    private lateinit var prompt: EditText
    private lateinit var download: Button
    private lateinit var load: Button
    private lateinit var unload: Button
    private lateinit var remove: Button
    private lateinit var send: Button
    private lateinit var stop: Button
    private val idleButtons = mutableListOf<Button>()
    private var rendering = false
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(HarnessRuntimeOwner::selectImage) }
    private val promptPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(HarnessRuntimeOwner::importPrompt) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        scroll.addView(body)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        fun text(value: String = "", size: Float = 14f): TextView = TextView(this).apply {
            text = value; textSize = size; setTextIsSelectable(true); setPadding(0, dp(5), 0, dp(5)); body.addView(this)
        }
        fun button(label: String, idle: Boolean = false, action: () -> Unit): Button = Button(this).apply {
            text = label; setOnClickListener { action() }; body.addView(this); if (idle) idleButtons += this
        }
        fun spinner(label: String, choices: List<String>, action: (Int) -> Unit): Spinner {
            text(label, 16f)
            return Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, choices)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { if (!rendering) action(position) }
                }
                body.addView(this)
            }
        }
        text("LAB-2B VLM Harness", 22f)
        text("MNN 3.6.1 • Local image + text • Stateless turns\nCPU is the correctness baseline. GPU is experimental.")
        modelsText = text()
        model = spinner("MODEL", ModelRegistry.models.map { it.displayName }) { HarnessRuntimeOwner.selectModel(ModelRegistry.models[it].id) }
        backend = spinner("BACKEND", ComputeBackend.entries.map { it.name }) { HarnessRuntimeOwner.selectBackend(ComputeBackend.entries[it]) }
        download = button("Download / retry (Wi-Fi or mobile data)", action = HarnessRuntimeOwner::download)
        load = button("Load", action = HarnessRuntimeOwner::load)
        unload = button("Unload", action = HarnessRuntimeOwner::unload)
        remove = button("Remove selected model", action = HarnessRuntimeOwner::remove)
        runtimeText = text()
        button("GPU result incorrect → mark failed", action = HarnessRuntimeOwner::reportGpuFailure)
        pipeline = spinner("PIPELINE", listOf("VISION ONLY", "VISION + OCR", "OCR ONLY")) { HarnessRuntimeOwner.selectPipeline(PipelineMode.entries[it]) }
        preset = spinner("SYSTEM PROMPT", SystemPreset.entries.map { it.name.replace('_', ' ') }) { HarnessRuntimeOwner.selectPreset(SystemPreset.entries[it]) }
        customText = text()
        button("Choose custom .txt / .md (≤ 64 KB)", true) { promptPicker.launch(arrayOf("text/*", "application/octet-stream")) }
        button("Clear custom prompt / None", true, HarnessRuntimeOwner::clearPrompt)
        button("Select image", true) { imagePicker.launch(arrayOf("image/*")) }
        button("Control: red square", true) { HarnessRuntimeOwner.selectControl(false) }
        button("Control: HELLO / 1234", true) { HarnessRuntimeOwner.selectControl(true) }
        button("Clear image", true, HarnessRuntimeOwner::clearImage)
        imageText = text()
        button("Open exact prepared image") { openImage(HarnessRuntimeOwner.currentSnapshot().selectedImage?.preparedPath) }
        button("Open normalised OCR image") { openImage(HarnessRuntimeOwner.currentSnapshot().selectedImage?.normalisedPath) }
        text("OCR", 18f)
        ocrText = text()
        button("Run OCR", true, HarnessRuntimeOwner::runOcr)
        button("Copy OCR") { copy(HarnessRuntimeOwner.currentSnapshot().ocr?.let(OcrFormatter::format) ?: "No OCR") }
        button("Clear OCR", true, HarnessRuntimeOwner::clearOcr)
        text("USER INSTRUCTION", 16f)
        prompt = EditText(this).apply {
            hint = "Enter your instruction"; minLines = 3; maxLines = 6
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(HarnessRuntimeOwner.currentSnapshot().promptText)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (!rendering) HarnessRuntimeOwner.promptChanged(s.toString()) }
                override fun afterTextChanged(s: Editable?) = Unit
            }); body.addView(this)
        }
        send = button("Send", action = HarnessRuntimeOwner::generate)
        stop = button("Stop (waits for current native operation)", action = HarnessRuntimeOwner::stopGeneration)
        text("LOCAL RESPONSE", 18f)
        output = text()
        button("Copy response") { copy(HarnessRuntimeOwner.currentSnapshot().output) }
        button("Clear transcript", true, HarnessRuntimeOwner::clearTranscript)
        transcript = text()
        button("Copy exact assembled prompt") { copy(HarnessRuntimeOwner.currentSnapshot().lastAssembledPrompt) }
        text("DIAGNOSTICS", 18f)
        diagnostics = text()
        button("Copy diagnostics") { copy(diagnostics.text.toString() + "\n" + runtimeText.text + "\n" + imageText.text) }
        HarnessRuntimeOwner.initialize(applicationContext)
    }
    override fun onStart() { super.onStart(); HarnessRuntimeOwner.setListener { runOnUiThread { render(it) } } }
    override fun onStop() { HarnessRuntimeOwner.setListener(null); super.onStop() }
    private fun render(s: HarnessSnapshot) {
        rendering = true
        try {
            model.setSelection(ModelRegistry.models.indexOfFirst { it.id == s.selectedModelId })
            backend.setSelection(s.backend.ordinal); pipeline.setSelection(s.pipeline.ordinal); preset.setSelection(s.preset.ordinal)
            val idle = HarnessStateMachine.idle(s.phase)
            model.isEnabled = idle; backend.isEnabled = idle; pipeline.isEnabled = idle; preset.isEnabled = idle
            idleButtons.forEach { it.isEnabled = idle }
            prompt.isEnabled = idle
            val installation = s.installations[s.selectedModelId]
            download.isEnabled = installation?.phase in setOf(InstallationPhase.NOT_INSTALLED, InstallationPhase.FAILED)
            load.isEnabled = idle && s.loadedModelId == null && installation?.phase == InstallationPhase.INSTALLED
            unload.isEnabled = idle && s.loadedModelId != null
            remove.isEnabled = idle && installation?.phase !in setOf(null, InstallationPhase.NOT_INSTALLED, InstallationPhase.VERIFYING)
            send.isEnabled = HarnessStateMachine.canGenerate(s)
            stop.isEnabled = HarnessStateMachine.canStop(s.phase)
            modelsText.text = ModelRegistry.models.joinToString("\n") { m ->
                val state = s.installations[m.id]
                val label = when {
                    s.loadedModelId == m.id -> "LOADED"
                    s.selectedModelId == m.id && s.phase == HarnessPhase.LOADING -> "LOADING"
                    s.selectedModelId == m.id && s.phase == HarnessPhase.FAILED -> "FAILED"
                    else -> state?.phase?.name ?: "DISCOVERING"
                }
                "${m.displayName}: $label • ${state?.bytes ?: 0}/${m.sizeBytes} bytes" + (state?.message?.let { "\n$it" } ?: "")
            }
            runtimeText.text = "State: ${s.phase}\nSelected: ${s.selectedModelId}\nLoaded: ${s.loadedModelId ?: "none"}\n" +
                "REQUESTED BACKEND: ${s.backend}\nEFFECTIVE TEXT: ${s.backendEvidence.effectiveText}\nEFFECTIVE VISION: ${s.backendEvidence.effectiveVision}\n" +
                "GPU: ${s.backendEvidence.gpuCorrectness}\n" + (s.lastError?.let { "ERROR: $it" } ?: "")
            customText.text = "Active preset: ${s.preset}\n" + (s.customPrompt?.let { "${it.name} • ${it.bytes} bytes\nSHA-256: ${it.sha256}\n${if (s.preset == SystemPreset.CUSTOM) "ACTIVE" else "INACTIVE"}" } ?: "No custom prompt file")
            imageText.text = s.selectedImage?.let { i ->
                "Original: ${i.sourceName}\n${i.sourceWidth} × ${i.sourceHeight} • ${i.sourceBytes} bytes\nSHA-256: ${i.sourceSha256}\n" +
                    "EXIF orientation: ${i.orientation}\n${i.normalisation}\n" +
                    "OCR image: ${i.normalisedWidth} × ${i.normalisedHeight}\n${i.normalisedPath}\nSHA-256: ${i.normalisedSha256}\n" +
                    "Prepared input: ${i.preparedWidth} × ${i.preparedHeight} ${i.preparedFormat} • ${i.preparedBytes} bytes\n${i.preparedPath}\nSHA-256: ${i.preparedSha256}\n" +
                    "MNN performs its own model-specific resampling/patching after this exact input."
            } ?: "No image selected"
            ocrText.text = "Status: ${s.ocrStatus}\n" + (s.ocr?.let { "${it.processingMs} ms • ${it.blocks.size} blocks / ${it.lineCount} lines\n${it.width} × ${it.height}\nSource SHA-256: ${it.sourceImageSha256}\nCache hit: ${s.ocrCacheHit}\n\n${it.fullText}" } ?: "")
            output.text = s.output.ifEmpty { "No response yet" }
            transcript.text = s.transcript.joinToString("\n\n") { "${it.model} / ${it.backend} / ${it.mode} / ${it.terminal}\nUSER: ${it.instruction}\nMODEL: ${it.response}" }
            diagnostics.text = "Runtime: ${BuildConfig.RUNTIME_VERSION}\nLast system mode: ${s.systemMode}\nContext: stateless; current image only; 512 output token limit\n" +
                "Cold load: ${s.timing.loadMs} ms\nTTFT (inference start, includes vision/prefill): ${s.timing.firstOutputMs} ms\nGeneration: ${s.timing.totalGenerationMs} ms\n" +
                "PSS KB before / loaded / after unload: ${s.memory.beforeLoadPssKb} / ${s.memory.loadedPssKb} / ${s.memory.afterDestroyPssKb}\n" +
                "Native [prompt tokens, generated tokens, vision μs, prefill μs, decode μs, cancelled]: ${s.nativeMetrics}\n" +
                "Persistent storage: ${s.modelStoragePath}\nInstalled bytes: ${s.installedBytes}\nDownload staging + runtime cache bytes: ${s.temporaryBytes}\n" +
                "Physical acceptance pending. No GPU correctness is inferred from speed."
        } finally { rendering = false }
    }
    private fun openImage(path: String?) {
        if (path == null) return
        val uri = FileProvider.getUriForFile(this, "$packageName.images", File(path))
        try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/png").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (_: android.content.ActivityNotFoundException) {
            val view = ImageView(this).apply { setImageURI(uri); adjustViewBounds = true }
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Exact prepared image").setView(view).setPositiveButton("Close", null).show()
        }
    }
    private fun copy(value: String) { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("LAB-2B", value)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
