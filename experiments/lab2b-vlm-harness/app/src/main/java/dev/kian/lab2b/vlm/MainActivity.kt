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
    private lateinit var ocrOrder: Spinner
    private lateinit var thinking: Spinner
    private lateinit var budget: Spinner
    private lateinit var cropText: TextView
    private lateinit var propose: Button
    private val budgets = listOf(512, 1024, 2048, 4096)
    private val reportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(HarnessRuntimeOwner::exportReports) }
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
    private val sections = linkedMapOf<String, LinearLayout>()
    private var rendering = false
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(HarnessRuntimeOwner::selectImage) }
    private val promptPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(HarnessRuntimeOwner::importPrompt) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        var body = root
        scroll.addView(root)
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
        fun section(title: String, expanded: Boolean) {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(4), dp(8), dp(12))
                visibility = if (savedInstanceState?.getBoolean(title, expanded) ?: expanded) View.VISIBLE else View.GONE
            }
            val header = Button(this).apply {
                text = "$title • tap to expand/collapse"
                isAllCaps = false
                setOnClickListener { panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            }
            root.addView(header); root.addView(panel); sections[title] = panel; body = panel
        }
        text("LAB-2B VLM Harness • 0.4", 22f)
        button("Open OCR weight-stack workflow") { startActivity(Intent(this, WeightScanActivity::class.java)); finish() }
        text("1. Configure and Load → 2. Select image / crop → 3. Send → 4. Export results")
        text("MNN 3.6.1 • Local image + text • Stateless turns\nCPU is the correctness baseline. GPU is experimental.")
        section("1. Model & runtime", true)
        modelsText = text()
        model = spinner("MODEL", ModelRegistry.models.map { it.displayName }) { HarnessRuntimeOwner.selectModel(ModelRegistry.models[it].id) }
        backend = spinner("BACKEND", ComputeBackend.entries.map { it.name }) { HarnessRuntimeOwner.selectBackend(ComputeBackend.entries[it]) }
        thinking = spinner("THINKING (Gemma only; changing unloads model)", listOf("OFF", "ON • experimental")) {
            val old = HarnessRuntimeOwner.currentSnapshot().options
            HarnessRuntimeOwner.selectOptions(old.copy(thinking = it == 1, maxTokens = if (it == 1 && old.maxTokens == 512) 2048 else old.maxTokens))
        }
        budget = spinner("TOTAL GENERATED TOKEN LIMIT (thinking + answer)", budgets.map { it.toString() }) {
            HarnessRuntimeOwner.selectOptions(HarnessRuntimeOwner.currentSnapshot().options.copy(maxTokens = budgets[it]))
        }
        download = button("Download / retry (Wi-Fi or mobile data)", action = HarnessRuntimeOwner::download)
        load = button("Load", action = HarnessRuntimeOwner::load)
        unload = button("Unload", action = HarnessRuntimeOwner::unload)
        remove = button("Remove selected model", action = HarnessRuntimeOwner::remove)
        runtimeText = text()
        button("GPU result incorrect → mark failed", action = HarnessRuntimeOwner::reportGpuFailure)
        section("2. Image, region & prompts", true)
        pipeline = spinner("PIPELINE", listOf("VISION ONLY", "VISION + OCR", "OCR ONLY")) { HarnessRuntimeOwner.selectPipeline(PipelineMode.entries[it]) }
        preset = spinner("SYSTEM PROMPT", SystemPreset.entries.map { it.name.replace('_', ' ') }) { HarnessRuntimeOwner.selectPreset(SystemPreset.entries[it]) }
        customText = text()
        button("Choose custom .txt / .md (≤ 64 KB)", true) { promptPicker.launch(arrayOf("text/*", "application/octet-stream")) }
        button("Clear custom prompt / None", true, HarnessRuntimeOwner::clearPrompt)
        button("Select image", true) { imagePicker.launch(arrayOf("image/*")) }
        button("Control: red square", true) { HarnessRuntimeOwner.selectControl(false) }
        button("Control: HELLO / 1234", true) { HarnessRuntimeOwner.selectControl(true) }
        button("Clear image", true, HarnessRuntimeOwner::clearImage)
        text("IMAGE REGION • full frame by default", 18f)
        button("Draw crop on original image", true) { editCrop(null) }
        button("Restore full image", true, HarnessRuntimeOwner::restoreFullImage)
        propose = button("Stage 1: suggest crops with loaded model", action = HarnessRuntimeOwner::proposeCrops)
        button("Review suggested crops", true) {
            val regions = HarnessRuntimeOwner.currentSnapshot().proposedCrops
            if (regions.isEmpty()) { Toast.makeText(this, "No valid proposals. Run Stage 1 or draw a crop.", Toast.LENGTH_LONG).show() }
            else androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Choose a region to inspect")
                .setItems(regions.map { it.label }.toTypedArray()) { _, index -> editCrop(regions[index]) }.show()
        }
        cropText = text()
        imageText = text()
        button("Open exact prepared image") { openImage(HarnessRuntimeOwner.currentSnapshot().selectedImage?.preparedPath) }
        button("Open normalised OCR image") { openImage(HarnessRuntimeOwner.currentSnapshot().selectedImage?.normalisedPath) }
        section("3. OCR evidence", false)
        text("OCR", 18f)
        ocrOrder = spinner("OCR EVIDENCE ORDER", listOf("Original recognizer order", "Top to bottom (experimental)")) { HarnessRuntimeOwner.selectOcrOrder(it == 1) }
        ocrText = text()
        button("Run OCR", true, HarnessRuntimeOwner::runOcr)
        button("Copy OCR") { val s = HarnessRuntimeOwner.currentSnapshot(); copy(s.ocr?.let { OcrFormatter.format(it, s.ocrReadingOrder) } ?: "No OCR") }
        button("Clear OCR", true, HarnessRuntimeOwner::clearOcr)
        section("4. Run & save results", true)
        text("USER INSTRUCTION", 16f)
        button("Use kg extraction prompt", true, HarnessRuntimeOwner::useWeightPrompt)
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
        send = button("Send / Stage 2: extract selected image region", action = HarnessRuntimeOwner::generate)
        stop = button("Stop (waits for current native operation)", action = HarnessRuntimeOwner::stopGeneration)
        text("LOCAL RESPONSE", 18f)
        output = text()
        button("Save ALL test results as JSON", true) { reportPicker.launch("LAB-2B-tests-${System.currentTimeMillis()}.json") }
        button("Copy complete last test") { copy(HarnessRuntimeOwner.currentSnapshot().lastReport.ifEmpty { "No completed test yet" }) }
        button("Copy response") { copy(HarnessRuntimeOwner.currentSnapshot().output) }
        button("Clear transcript", true, HarnessRuntimeOwner::clearTranscript)
        transcript = text()
        button("Copy exact assembled prompt") { copy(HarnessRuntimeOwner.currentSnapshot().lastAssembledPrompt) }
        section("5. Diagnostics", false)
        text("DIAGNOSTICS", 18f)
        diagnostics = text()
        button("Copy diagnostics") { copy(diagnostics.text.toString() + "\n" + runtimeText.text + "\n" + imageText.text) }
        HarnessRuntimeOwner.initialize(applicationContext)
    }
    override fun onSaveInstanceState(outState: Bundle) { sections.forEach { (key, value) -> outState.putBoolean(key, value.visibility == View.VISIBLE) }; super.onSaveInstanceState(outState) }
    override fun onStart() { super.onStart(); HarnessRuntimeOwner.setListener { runOnUiThread { render(it) } } }
    override fun onStop() { HarnessRuntimeOwner.setListener(null); super.onStop() }
    private fun render(s: HarnessSnapshot) {
        rendering = true
        try {
            ocrOrder.setSelection(if (s.ocrReadingOrder) 1 else 0)
            thinking.setSelection(if (s.options.thinking) 1 else 0)
            budget.setSelection(budgets.indexOf(s.options.maxTokens))
            if (prompt.text.toString() != s.promptText) prompt.setText(s.promptText)
            model.setSelection(ModelRegistry.models.indexOfFirst { it.id == s.selectedModelId })
            backend.setSelection(s.backend.ordinal); pipeline.setSelection(s.pipeline.ordinal); preset.setSelection(s.preset.ordinal)
            val idle = HarnessStateMachine.idle(s.phase)
            ocrOrder.isEnabled = idle
            thinking.isEnabled = idle && s.selectedModelId.startsWith("gemma4-")
            budget.isEnabled = idle
            propose.isEnabled = HarnessStateMachine.canGenerate(s)
            model.isEnabled = idle; backend.isEnabled = idle; pipeline.isEnabled = idle; preset.isEnabled = idle
            idleButtons.forEach { it.isEnabled = idle }
            prompt.isEnabled = idle
            val installation = s.installations[s.selectedModelId]
            download.isEnabled = idle && installation?.phase in setOf(InstallationPhase.NOT_INSTALLED, InstallationPhase.FAILED)
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
                "GPU: ${if (s.selectedModelId in s.gpuFailedModels) "FAILED CORRECTNESS (manual observation); use CPU" else s.backendEvidence.gpuCorrectness}\n" + (s.lastError?.let { "ERROR: $it" } ?: "")
            cropText.text = "Active: ${s.crop ?: "FULL FRAME"}\nProposals: ${s.proposedCrops.size} • inspect before applying.\nStage 2 uses only the active region. Restore full image or choose another region to compare."
            customText.text = "Active preset: ${s.preset}\n" + (s.customPrompt?.let { "${it.name} • ${it.bytes} bytes\nSHA-256: ${it.sha256}\n${if (s.preset == SystemPreset.CUSTOM) "ACTIVE" else "INACTIVE"}" } ?: "No custom prompt file")
            imageText.text = s.selectedImage?.let { i ->
                "Original: ${i.sourceName}\n${i.sourceWidth} × ${i.sourceHeight} • ${i.sourceBytes} bytes\nSHA-256: ${i.sourceSha256}\n" +
                    "EXIF orientation: ${i.orientation}\n${i.normalisation}\n" +
                    "OCR image: ${i.normalisedWidth} × ${i.normalisedHeight}\n${i.normalisedPath}\nSHA-256: ${i.normalisedSha256}\n" +
                    "Prepared input: ${i.preparedWidth} × ${i.preparedHeight} ${i.preparedFormat} • ${i.preparedBytes} bytes\n${i.preparedPath}\nSHA-256: ${i.preparedSha256}\n" +
                    "MNN performs its own model-specific resampling/patching after this exact input."
            } ?: "No image selected"
            ocrText.text = "Status: ${s.ocrStatus}\n" + (s.ocr?.let { "${it.processingMs} ms • ${it.blocks.size} blocks / ${it.lineCount} lines\n${it.width} × ${it.height}\nSource SHA-256: ${it.sourceImageSha256}\nCache hit: ${s.ocrCacheHit}\n\n${it.fullText}" } ?: "")
            output.text = s.output.ifEmpty { if (s.rawOutput.contains("<|channel>thought")) if (HarnessStateMachine.canStop(s.phase)) "Thinking channel received; waiting for final answer." else "Generation ended without a final answer. Check token limit and diagnostics." else "No response yet" }
            transcript.text = s.transcript.joinToString("\n\n") { "${it.model} / ${it.backend} / ${it.mode} / ${it.terminal}\nUSER: ${it.instruction}\nMODEL: ${it.response}" }
            diagnostics.text = "Runtime: ${BuildConfig.RUNTIME_VERSION}\nLast system mode: ${s.systemMode}\nContext: stateless; current image only; ${s.options.maxTokens} generated token limit (includes thinking)\nThinking requested: ${s.options.thinking} • stage: ${s.stageLabel}\n" +
                "${s.measurementSummary}\nFirst raw output: ${s.timing.firstRawOutputMs} ms\nCold load: ${s.timing.loadMs} ms\nFirst final output (includes vision/prefill/thinking): ${s.timing.firstOutputMs} ms\nGeneration: ${s.timing.totalGenerationMs} ms\n" +
                "PSS KB before / loaded / after unload: ${s.memory.beforeLoadPssKb} / ${s.memory.loadedPssKb} / ${s.memory.afterDestroyPssKb}\n" +
                "Native [prompt tokens, generated tokens, vision μs, prefill μs, decode μs, cancelled]: ${s.nativeMetrics}\n" +
                "Persistent storage: ${s.modelStoragePath}\nInstalled bytes: ${s.installedBytes}\nDownload staging + runtime cache bytes: ${s.temporaryBytes}\n" +
                "Physical acceptance pending. No GPU correctness is inferred from speed."
        } finally { rendering = false }
    }
    private fun editCrop(initial: CropRegion?) {
        val original = HarnessRuntimeOwner.currentSnapshot().originalImage ?: return
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)) }
        val label = EditText(this).apply { setText(initial?.label ?: "main stack"); hint = "Region label" }
        val editor = CropEditor(this, original.preparedPath, initial)
        column.addView(TextView(this).apply { text = "Drag a rectangle. Include all labels and their units. Add-on weights may need a separate crop." })
        column.addView(label)
        column.addView(editor, LinearLayout.LayoutParams(-1, dp(400)))
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Inspect crop on full image").setView(column)
            .setNegativeButton("Cancel", null).setPositiveButton("Apply crop") { _, _ ->
                HarnessRuntimeOwner.applyCrop(editor.region(label.text.toString().take(80)), initial != null)
            }.show()
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
