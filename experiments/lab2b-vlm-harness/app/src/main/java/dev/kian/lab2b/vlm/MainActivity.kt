package dev.kian.lab2b.vlm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var tvRuntime: TextView
    private lateinit var tvModel: TextView
    private lateinit var tvImage: TextView
    private lateinit var tvOutput: TextView
    private lateinit var tvDiagnostics: TextView
    private lateinit var etPrompt: EditText
    private lateinit var btnSelectModel: Button
    private lateinit var btnLoad: Button
    private lateinit var btnUnload: Button
    private lateinit var btnReload: Button
    private lateinit var btnSelectImage: Button
    private lateinit var btnRun: Button
    private lateinit var btnStop: Button

    private val modelFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistReadAccess(uri)
            HarnessRuntimeOwner.importModelFolder(uri)
        }
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistReadAccess(uri)
            HarnessRuntimeOwner.selectImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        bindActions()
        HarnessRuntimeOwner.initialize(applicationContext)
    }

    override fun onStart() {
        super.onStart()
        HarnessRuntimeOwner.setListener { snapshot -> runOnUiThread { render(snapshot) } }
    }

    override fun onStop() {
        HarnessRuntimeOwner.setListener(null)
        super.onStop()
    }

    private fun bindViews() {
        tvRuntime = findViewById(R.id.tvRuntime)
        tvModel = findViewById(R.id.tvModel)
        tvImage = findViewById(R.id.tvImage)
        tvOutput = findViewById(R.id.tvOutput)
        tvDiagnostics = findViewById(R.id.tvDiagnostics)
        etPrompt = findViewById(R.id.etPrompt)
        btnSelectModel = findViewById(R.id.btnSelectModel)
        btnLoad = findViewById(R.id.btnLoad)
        btnUnload = findViewById(R.id.btnUnload)
        btnReload = findViewById(R.id.btnReload)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnRun = findViewById(R.id.btnRun)
        btnStop = findViewById(R.id.btnStop)
    }

    private fun bindActions() {
        btnSelectModel.setOnClickListener { modelFolderPicker.launch(null) }
        btnLoad.setOnClickListener { HarnessRuntimeOwner.loadNpu() }
        btnUnload.setOnClickListener { HarnessRuntimeOwner.unload() }
        btnReload.setOnClickListener { HarnessRuntimeOwner.reloadNpu() }
        btnSelectImage.setOnClickListener { imagePicker.launch(arrayOf("image/*")) }
        btnRun.setOnClickListener { HarnessRuntimeOwner.generate(etPrompt.text.toString()) }
        btnStop.setOnClickListener { HarnessRuntimeOwner.stopGeneration() }
    }

    private fun render(snapshot: HarnessSnapshot) {
        tvRuntime.text = buildString {
            appendLine("RUNTIME")
            appendLine("GenieX AAR: ${BuildConfig.GENIEX_AAR_VERSION}")
            appendLine("GenieX source: ${BuildConfig.GENIEX_SOURCE_COMMIT.take(12)}")
            appendLine("Reference app: ${BuildConfig.REFERENCE_COMMIT.take(12)}")
            appendLine("SDK init: ${if (snapshot.sdkReady) "READY" else "PENDING"}")
            appendLine("State: ${snapshot.phase}")
            appendLine("Requested compute: ${snapshot.requestedComputeUnit}")
            appendLine("Resolved runtime_id: ${snapshot.resolvedRuntimeId ?: "unresolved"}")
            appendLine("Backend proof: ${snapshot.backendEvidence.proofState}")
            if (!snapshot.backendEvidence.requestedBackendIsProven) appendLine("REQUESTED != PROVEN")
            snapshot.lastError?.let { appendLine("Last error: $it") }
        }

        tvModel.text = buildString {
            appendLine("MODEL")
            appendLine("Source: ${snapshot.sourceFolderLabel ?: "not selected"}")
            snapshot.modelBundle?.let { bundle ->
                appendLine("Staging: ${bundle.stagingDir}")
                bundle.files.forEach { file ->
                    appendLine("${file.role}: ${file.name}")
                    appendLine("  bytes=${file.bytes}")
                    appendLine("  sha256=${file.sha256}")
                }
                appendLine("Staging bytes: ${bundle.totalBytes}")
            }
            appendLine("Managed model: ${snapshot.resolvedModelPath ?: "unresolved"}")
            appendLine("Managed mmproj: ${snapshot.resolvedMmprojPath ?: "unresolved"}")
            appendLine("Managed cache bytes: ${snapshot.managedCacheBytes ?: 0}")
            snapshot.visionConfig?.let { vision ->
                appendLine("Vision: ${vision.imageSize}px / patch ${vision.patchSize} / merge ${vision.spatialMergeSize}")
                appendLine("Image token estimate: ${vision.tokenCount}")
            }
        }

        tvImage.text = buildString {
            appendLine("IMAGE")
            snapshot.selectedImage?.let { image ->
                appendLine("${image.sourceName}: ${image.sourceWidth}x${image.sourceHeight}, ${image.sourceBytes} bytes")
                appendLine("Private source: ${image.sourcePrivatePath}")
                appendLine("Prepared: ${image.preparedPath ?: "pending model geometry"}")
                if (image.preparedWidth != null) appendLine("Prepared geometry: ${image.preparedWidth}x${image.preparedHeight}")
            } ?: appendLine("not selected")
        }

        tvOutput.text = snapshot.output.ifBlank { "No generation yet." }
        tvDiagnostics.text = buildString {
            appendLine(snapshot.backendEvidence.diagnosticSummary())
            appendLine("load_ms=${snapshot.timing.loadMs ?: "pending"}")
            appendLine("ttft_ms=${snapshot.timing.firstOutputMs ?: "pending"}")
            appendLine("generation_ms=${snapshot.timing.totalGenerationMs ?: "pending"}")
            appendLine("pss_before_load_kb=${snapshot.memory.beforeLoadPssKb ?: "pending"}")
            appendLine("pss_loaded_kb=${snapshot.memory.loadedPssKb ?: "pending"}")
            appendLine("pss_after_destroy_kb=${snapshot.memory.afterDestroyPssKb ?: "pending"}")
        }

        btnSelectModel.isEnabled = HarnessStateMachine.canImport(snapshot.phase)
        btnLoad.isEnabled = HarnessStateMachine.canLoad(snapshot.phase)
        btnUnload.isEnabled = HarnessStateMachine.canUnload(snapshot.phase)
        btnReload.isEnabled = snapshot.phase == HarnessPhase.READY
        btnSelectImage.isEnabled = snapshot.phase != HarnessPhase.IMPORTING && snapshot.phase != HarnessPhase.LOADING
        btnRun.isEnabled = HarnessStateMachine.canGenerate(snapshot.phase) && snapshot.selectedImage != null
        btnStop.isEnabled = HarnessStateMachine.canStop(snapshot.phase)
    }

    private fun persistReadAccess(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
