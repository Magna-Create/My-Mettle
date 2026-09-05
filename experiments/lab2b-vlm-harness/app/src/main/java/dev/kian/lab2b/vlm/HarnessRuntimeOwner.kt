package dev.kian.lab2b.vlm

import android.content.Context
import android.net.Uri
import android.os.Debug
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/** Process owner. Activity instances observe immutable snapshots and never own native handles. */
object HarnessRuntimeOwner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val slot = EngineSlot()
    private val cache = OcrCache()
    private val cancelled = AtomicBoolean(false)
    private var context: Context? = null
    private lateinit var downloads: ModelDownloads
    private var listener: ((HarnessSnapshot) -> Unit)? = null
    @Volatile private var snapshot = HarnessSnapshot()
    fun currentSnapshot() = snapshot
    fun setListener(callback: ((HarnessSnapshot) -> Unit)?) {
        synchronized(lock) { listener = callback }
        callback?.invoke(snapshot)
    }
    fun initialize(context: Context) {
        synchronized(lock) {
            if (this.context != null) return
            this.context = context.applicationContext
            snapshot = snapshot.copy(phase = HarnessPhase.PREPARING)
        }
        scope.launch {
            try {
                // Images/transcript are session-only. Reclaim files left by force-close.
                File(context.filesDir, "lab2b/images").deleteRecursively()
                downloads = ModelDownloads(context)
                val failures = context.getSharedPreferences("lab2b-gpu-observations", Context.MODE_PRIVATE)
                    .getStringSet("failed-models", emptySet()).orEmpty().toSet()
                publish { it.copy(gpuFailedModels = failures) }
                publish { it.copy(modelStoragePath = downloads.root.absolutePath) }
                val folder = File(context.filesDir, "lab2b/prompts")
                val saved = File(folder, "custom.txt")
                val metadata = File(folder, "name.txt")
                if (saved.isFile && metadata.isFile) {
                    runCatching { saved.inputStream().use { PromptFiles.read(metadata.readText(), it) } }
                        .onSuccess { prompt -> publish { it.copy(customPrompt = prompt) } }
                }
                publish { it.copy(phase = HarnessPhase.IDLE) }
                while (isActive) {
                    if (synchronized(lock) { listener != null }) refreshDownloads()
                    delay(1500)
                }
            } catch (e: Exception) { fail("Initialisation failed: ${e.message}") }
        }
    }
    private fun refreshDownloads() {
        ModelRegistry.models.forEach { model ->
            val installation = downloads.refresh(model) {
                publish { it.copy(installations = it.installations + (model.id to Installation(InstallationPhase.VERIFYING, model.sizeBytes, model.sizeBytes))) }
            }
            publish { it.copy(installations = it.installations + (model.id to installation)) }
        }
        publish { it.copy(installedBytes = ModelRegistry.models.sumOf { m -> StorageIo.directoryBytes(downloads.installation.directory(m).path) },
            temporaryBytes = ModelRegistry.models.sumOf { m -> StorageIo.directoryBytes(downloads.installation.staging(m).path) } +
                StorageIo.directoryBytes(File(requireContext().cacheDir, "lab2b-runtime").path)) }
    }
    fun download() {
        if (!::downloads.isInitialized || !HarnessStateMachine.idle(snapshot.phase)) return
        val model = ModelRegistry.get(snapshot.selectedModelId)
        scope.launch { runCatching { downloads.start(model); refreshDownloads() }.onFailure { fail(it.message ?: "Download failed") } }
    }
    fun remove() {
        val model = ModelRegistry.get(snapshot.selectedModelId)
        if (!begin(HarnessPhase.UNLOADING)) return
        scope.launch {
            try {
                dispose()
                downloads.remove(model)
                File(requireContext().cacheDir, "lab2b-runtime/${model.id}").deleteRecursively()
                refreshDownloads()
                publish { it.copy(phase = HarnessPhase.IDLE, lastError = null) }
            } catch (e: Exception) { fail(e.message ?: "Remove failed", HarnessPhase.FAILED) }
        }
    }
    fun selectModel(id: String) {
        val model = ModelRegistry.get(id)
        if (id == snapshot.selectedModelId || !begin(HarnessPhase.UNLOADING)) return
        scope.launch {
            try {
                dispose()
                publish { it.copy(selectedModelId = id, backend = model.defaultBackend, options = it.options.copy(thinking = false), phase = HarnessPhase.IDLE,
                    backendEvidence = BackendEvidence(model.defaultBackend), lastError = null) }
            } catch (e: Exception) { fail("Model switch failed: ${e.message}", HarnessPhase.FAILED) }
        }
    }
    fun selectBackend(backend: ComputeBackend) {
        if (backend == snapshot.backend || !begin(HarnessPhase.UNLOADING)) return
        scope.launch {
            try {
                require(backend in ModelRegistry.get(snapshot.selectedModelId).supportedBackends)
                dispose()
                publish { it.copy(backend = backend, phase = HarnessPhase.IDLE, backendEvidence = BackendEvidence(backend), lastError = null) }
            } catch (e: Exception) { fail("Backend change failed: ${e.message}", HarnessPhase.FAILED) }
        }
    }
    fun selectOptions(options: GenerationOptions) {
        if (options == snapshot.options || !begin(HarnessPhase.UNLOADING)) return
        scope.launch {
            try {
                options.validate(snapshot.selectedModelId)
                dispose()
                publish { it.copy(options = options, phase = HarnessPhase.IDLE) }
            } catch (e: Exception) { fail("Settings change failed: ${e.message}", HarnessPhase.FAILED) }
        }
    }
    fun useWeightPrompt() { if (HarnessStateMachine.idle(snapshot.phase)) publish { it.copy(promptText = ExperimentPrompts.weights) } }
    fun load() {
        if (!::downloads.isInitialized || !begin(HarnessPhase.LOADING)) return
        val selected = snapshot
        scope.launch {
            try {
                check(slot.engine == null) { "Unload before loading again" }
                val model = ModelRegistry.get(selected.selectedModelId)
                require(downloads.installation.installed(model)) { "Download and verify this model first" }
                // Marker speeds discovery; actual weights are rehashed before a native load.
                downloads.installation.verifyAll(model)
                val before = pss()
                val start = System.nanoTime()
                val engine = MnnEngine(model, downloads.installation.directory(model), selected.backend,
                    File(requireContext().cacheDir, "lab2b-runtime/${model.id}/${selected.backend}"), selected.options)
                slot.install(model.id, engine)
                publish { it.copy(phase = HarnessPhase.READY, loadedModelId = model.id,
                    timing = TimingSnapshot(loadMs = elapsed(start)), memory = MemorySnapshot(before, pss()),
                    backendEvidence = engine.evidence, lastError = null) }
            } catch (e: Exception) {
                fail("Load failed: ${e.message}" + if (selected.backend == ComputeBackend.GPU) "\nSelect CPU and Load to retry explicitly." else "", HarnessPhase.FAILED)
            } catch (e: LinkageError) { fail("Native runtime could not load: ${e.message}", HarnessPhase.FAILED) }
        }
    }
    fun unload() {
        if (!begin(HarnessPhase.UNLOADING)) return
        scope.launch {
            try { dispose(); publish { it.copy(phase = HarnessPhase.IDLE, lastError = null) } }
            catch (e: Exception) { fail("Unload failed: ${e.message}", HarnessPhase.FAILED) }
        }
    }
    private fun dispose() {
        slot.dispose()
        publish { it.copy(loadedModelId = null, backendEvidence = BackendEvidence(it.backend), memory = it.memory.copy(afterDestroyPssKb = pss())) }
    }
    fun selectImage(uri: Uri) = idleWork {
        val image = StorageIo.copyImage(requireContext(), uri)
        val old = snapshot.originalImage ?: snapshot.selectedImage
        publish { it.copy(originalImage = image, crop = null, cropPreparationMs = 0, cropOrigin = "FULL_FRAME", proposedCrops = emptyList(), localisationReport = "", selectedImage = image, ocr = cache.get(image.normalisedSha256),
            ocrStatus = if (cache.get(image.normalisedSha256) != null) "CACHED" else "NOT RUN", ocrCacheHit = cache.get(image.normalisedSha256) != null) }
        old?.let { File(it.sourcePrivatePath).parentFile?.deleteRecursively() }
    }
    fun selectControl(textControl: Boolean) = idleWork {
        val folder = File(requireContext().filesDir, "lab2b/images/${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val bitmap = android.graphics.Bitmap.createBitmap(1024, 768, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            if (textControl) {
                paint.color = android.graphics.Color.BLACK; paint.textSize = 160f
                paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                canvas.drawText("HELLO", 180f, 330f, paint); canvas.drawText("1234", 180f, 540f, paint)
            } else { paint.color = android.graphics.Color.RED; canvas.drawRect(280f, 150f, 744f, 614f, paint) }
            val source = File(folder, "control.png")
            source.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            val image = ImagePreprocessor.prepare(source, folder, if (textControl) "HELLO-1234.png" else "red-square.png")
            val old = snapshot.originalImage ?: snapshot.selectedImage
            publish { it.copy(originalImage = image, crop = null, cropPreparationMs = 0, cropOrigin = "FULL_FRAME", proposedCrops = emptyList(), localisationReport = "", selectedImage = image, ocr = cache.get(image.normalisedSha256), ocrStatus = "NOT RUN") }
            old?.let { File(it.sourcePrivatePath).parentFile?.deleteRecursively() }
        } finally { bitmap.recycle() }
    }
    fun clearImage() {
        if (!HarnessStateMachine.idle(snapshot.phase)) return
        val old = snapshot.originalImage ?: snapshot.selectedImage
        publish { it.copy(originalImage = null, crop = null, cropPreparationMs = 0, cropOrigin = "FULL_FRAME", proposedCrops = emptyList(), localisationReport = "", selectedImage = null, ocr = null, ocrStatus = "NOT RUN") }
        scope.launch { old?.let { File(it.sourcePrivatePath).parentFile?.deleteRecursively() } }
    }
    fun applyCrop(region: CropRegion, fromProposal: Boolean = false) = idleWork {
        val start = System.nanoTime()
        val original = requireNotNull(snapshot.originalImage ?: snapshot.selectedImage) { "Select an image" }
        val image = CropImages.prepare(original, region)
        publish { it.copy(selectedImage = image, crop = region, cropPreparationMs = elapsed(start), cropOrigin = if (fromProposal) "MODEL_PROPOSED_HUMAN_REVIEWED" else "MANUAL", ocr = cache.get(image.normalisedSha256),
            ocrStatus = if (cache.get(image.normalisedSha256) == null) "NOT RUN" else "CACHED", ocrCacheHit = cache.get(image.normalisedSha256) != null) }
    }
    fun restoreFullImage() = idleWork {
        val image = requireNotNull(snapshot.originalImage) { "Select an image" }
        publish { it.copy(selectedImage = image, crop = null, cropPreparationMs = 0, cropOrigin = "FULL_FRAME", ocr = cache.get(image.normalisedSha256),
            ocrStatus = if (cache.get(image.normalisedSha256) == null) "NOT RUN" else "CACHED") }
    }
    fun selectPipeline(mode: PipelineMode) { if (mode != snapshot.pipeline && HarnessStateMachine.idle(snapshot.phase)) publish { it.copy(pipeline = mode) } }
    fun selectOcrOrder(enabled: Boolean) { if (HarnessStateMachine.idle(snapshot.phase)) publish { it.copy(ocrReadingOrder = enabled) } }
    fun selectPreset(preset: SystemPreset) { if (preset != snapshot.preset && HarnessStateMachine.idle(snapshot.phase)) publish { it.copy(preset = preset) } }
    fun promptChanged(value: String) { publish { it.copy(promptText = value) } }
    fun importPrompt(uri: Uri) = idleWork {
        val context = requireContext()
        val name = StorageIo.displayName(context, uri)
        val prompt = context.contentResolver.openInputStream(uri).use { PromptFiles.read(name, requireNotNull(it)) }
        val folder = File(context.filesDir, "lab2b/prompts").apply { mkdirs() }
        // Stored text is safe UTF-8; retain original selected byte identity for diagnostics.
        context.contentResolver.openInputStream(uri).use { input ->
            val bytes = requireNotNull(input).readBytesLimited(MAX_PROMPT_FILE_BYTES)
            require(Hashing.sha256(bytes) == prompt.sha256) { "Prompt changed while importing; select again" }
            val part = File(folder, "custom.partial"); part.writeBytes(bytes)
            check(part.renameTo(File(folder, "custom.txt")))
        }
        File(folder, "name.txt").writeText(name)
        publish { it.copy(customPrompt = prompt, preset = SystemPreset.CUSTOM) }
    }
    fun clearPrompt() = idleWork {
        File(requireContext().filesDir, "lab2b/prompts").deleteRecursively()
        publish { it.copy(customPrompt = null, preset = SystemPreset.NONE) }
    }
    fun clearOcr() {
        if (!HarnessStateMachine.idle(snapshot.phase)) return
        snapshot.selectedImage?.let { cache.clear(it.normalisedSha256) }
        publish { it.copy(ocr = null, ocrStatus = "CLEARED", ocrCacheHit = false) }
    }
    fun runOcr() = idleWork { recognise(requireNotNull(snapshot.selectedImage) { "Select an image" }) }
    private suspend fun recognise(image: SelectedImageInfo): OcrEvidence {
        cache.get(image.normalisedSha256)?.let { result ->
            publish { it.copy(ocr = result, ocrStatus = "CACHED", ocrCacheHit = true) }; return result
        }
        publish { it.copy(ocrStatus = "RUNNING", ocrCacheHit = false) }
        try {
            val result = OcrProcessor.recognise(image)
            cache.put(result)
            publish { if (it.selectedImage?.normalisedSha256 == result.sourceImageSha256) it.copy(ocr = result, ocrStatus = "READY") else it }
            return result
        } catch (e: Exception) { publish { it.copy(ocrStatus = "FAILED: ${e.message}") }; throw e }
    }
    fun generate() = runTurn(false)
    fun proposeCrops() = runTurn(true)
    private fun runTurn(localise: Boolean) {
        val request: HarnessSnapshot
        synchronized(lock) {
            if (!HarnessStateMachine.canGenerate(snapshot)) return
            request = snapshot
            cancelled.set(false)
            slot.engine!!.arm()
            snapshot = snapshot.copy(phase = HarnessPhase.PREPARING, output = "", rawOutput = "", nativeMetrics = emptyList(), lastError = null,
                stageLabel = if (localise) "LOCALISE" else "EXTRACT",
                timing = snapshot.timing.copy(firstOutputMs = null, firstRawOutputMs = null, totalGenerationMs = null))
        }
        notifySnapshot()
        scope.launch {
            var turn: InferenceTurn? = null
            var terminal = "FAILED"
            val stage = if (localise) "LOCALISE" else "EXTRACT"
            val image = if (localise) request.originalImage ?: request.selectedImage else request.selectedImage
            var evidence: OcrEvidence? = null
            var ocrMs = 0L
            val measurements = RunMeasurements(requireContext())
            val operationStart = System.nanoTime()
            try {
                measurements.start(this)
                val ocrStart = System.nanoTime()
                evidence = if (!localise && request.pipeline.ocr) recognise(requireNotNull(image)) else null
                ocrMs = if (evidence == null) 0 else elapsed(ocrStart)
                if (cancelled.get()) { terminal = "STOPPED"; return@launch }
                val system = if (localise) "" else if (request.preset == SystemPreset.CUSTOM) requireNotNull(request.customPrompt) { "Select a custom prompt file" }.text else request.preset.prompt
                turn = ThinkingPrompt.apply(PromptAssembler.assemble(if (localise) ExperimentPrompts.locate else request.promptText,
                    if (localise) "Return only the requested region JSON. Locate visible objects; do not invent regions." else system,
                    ModelRegistry.get(request.selectedModelId).systemMode, if (localise) PipelineMode.VISION_ONLY else request.pipeline,
                    image, evidence, request.ocrReadingOrder), request.options.thinking)
                publish { it.copy(systemMode = turn.systemMode, lastAssembledPrompt = "SYSTEM (${turn.systemMode}):\n${turn.system ?: "none / preface below"}\n\n${turn.user}") }
                synchronized(lock) { if (!cancelled.get()) snapshot = snapshot.copy(phase = HarnessPhase.GENERATING) }
                notifySnapshot()
                if (cancelled.get()) { terminal = "STOPPED"; return@launch }
                val inferenceStart = System.nanoTime()
                val metrics = requireNotNull(slot.engine).generate(turn) { output ->
                    val answer = GemmaOutput.finalAnswer(output)
                    publish { it.copy(rawOutput = output, output = answer, timing = it.timing.copy(firstRawOutputMs = it.timing.firstRawOutputMs ?: if (output.isNotEmpty()) elapsed(inferenceStart) else null, firstOutputMs = it.timing.firstOutputMs ?: if (answer.isNotEmpty()) elapsed(inferenceStart) else null)) }
                }
                terminal = if (cancelled.get() || metrics.lastOrNull() == 1L) "STOPPED" else "COMPLETED"
                publish { it.copy(nativeMetrics = metrics.toList(), timing = it.timing.copy(totalGenerationMs = elapsed(inferenceStart))) }
            } catch (e: Exception) { fail("Inference failed: ${e.message}") }
            finally {
                val measured = runCatching { measurements.finish() }.getOrElse { org.json.JSONObject().put("error", it.message) }
                if (localise && terminal == "COMPLETED") {
                    runCatching { CropImages.parse(snapshot.output) }.onSuccess { regions ->
                        publish { it.copy(proposedCrops = regions) }
                    }.onFailure { e ->
                        terminal = "INVALID_CROP_PROPOSAL"
                        publish { it.copy(proposedCrops = emptyList(), lastError = "Invalid crop proposal: ${e.message}. Use manual crop.") }
                    }
                }
                val report = TestReport.create(request, snapshot, turn, image, evidence, terminal, stage, ocrMs, elapsed(operationStart), measured)
                runCatching {
                    val folder = File(requireContext().filesDir, "lab2b/reports").apply { mkdirs() }
                    val file = File(folder, "${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.json")
                    val part = File(folder, file.name + ".partial")
                    part.writeText(report)
                    check(part.renameTo(file))
                }.onFailure { fail("Result export could not be persisted: ${it.message}") }
                publish { it.copy(measurementSummary = "Stage: $stage • $terminal\nOCR wall: $ocrMs ms • operation: ${elapsed(operationStart)} ms\nSampled peak PSS: ${measured.optInt("sampled_peak_pss_kb")} KB\nEnergy: ${measured.optString("energy_status", "UNAVAILABLE")}\n${measured.optJSONArray("power_monitors") ?: "No monitors"}\nSave results for complete timing, raw gauge and thermal data.", lastReport = report, testReports = (it.testReports + report).takeLast(50),
                    localisationReport = if (localise) report else it.localisationReport) }
                // MNN clears pending multimodal embeddings during prefill, not reset().
                // A cancelled/rejected turn can stop before prefill; never reuse that owner.
                if (HarnessStateMachine.mustDisposeAfterTurn(terminal)) {
                    publish { it.copy(phase = HarnessPhase.UNLOADING) }
                    dispose()
                }
                publish { it.copy(phase = if (slot.engine != null) HarnessPhase.READY else HarnessPhase.IDLE,
                    lastError = if (terminal == "STOPPED") "Stopped; runtime unloaded to clear pending image state. Load to run another turn." else if (terminal == "FAILED") (it.lastError ?: "Turn failed") + "\nRuntime unloaded. Load to retry." else it.lastError,
                    transcript = (it.transcript + TranscriptTurn(request.selectedModelId, request.backend, if (localise) PipelineMode.VISION_ONLY else request.pipeline,
                        turn?.systemMode ?: SystemPromptMode.NONE, if (localise) ExperimentPrompts.locate else request.promptText, turn?.imageSha256, it.output, terminal)).takeLast(20)) }
            }
        }
    }
    fun stopGeneration() {
        synchronized(lock) {
            if (!HarnessStateMachine.canStop(snapshot.phase)) return
            cancelled.set(true)
            slot.engine?.stop()
            snapshot = snapshot.copy(phase = HarnessPhase.STOPPING)
        }
        notifySnapshot()
    }
    fun exportReports(uri: Uri) = idleWork {
        val folder = File(requireContext().filesDir, "lab2b/reports")
        val reports = folder.listFiles().orEmpty().filter { it.extension == "json" }.sortedBy { it.name }
        require(reports.isNotEmpty()) { "No saved tests yet" }
        requireContext().contentResolver.openOutputStream(uri).use { stream ->
            requireNotNull(stream).bufferedWriter().use { writer ->
                writer.write("[\n")
                reports.forEachIndexed { index, file -> if (index > 0) writer.write(",\n"); writer.write(file.readText()) }
                writer.write("\n]")
            }
        }
    }
    fun clearTranscript() { if (HarnessStateMachine.idle(snapshot.phase)) publish { it.copy(transcript = emptyList(), output = "", lastAssembledPrompt = "") } }
    fun reportGpuFailure() {
        if (!HarnessStateMachine.idle(snapshot.phase)) return
        val failed = snapshot.gpuFailedModels + snapshot.selectedModelId
        requireContext().getSharedPreferences("lab2b-gpu-observations", Context.MODE_PRIVATE)
            .edit().putStringSet("failed-models", failed).apply()
        publish { it.copy(gpuFailedModels = failed) }
    }
    private fun idleWork(block: suspend () -> Unit) {
        if (!begin(HarnessPhase.PREPARING)) return
        scope.launch {
            try { block() } catch (e: Exception) { fail(e.message ?: "Operation failed") }
            finally { publish { it.copy(phase = if (slot.engine != null) HarnessPhase.READY else HarnessPhase.IDLE) } }
        }
    }
    private fun begin(phase: HarnessPhase): Boolean {
        synchronized(lock) {
            if (!HarnessStateMachine.idle(snapshot.phase)) return false
            snapshot = snapshot.copy(phase = phase, lastError = null)
        }
        notifySnapshot(); return true
    }
    private fun publish(transform: (HarnessSnapshot) -> HarnessSnapshot) {
        synchronized(lock) { snapshot = transform(snapshot) }; notifySnapshot()
    }
    private fun notifySnapshot() { synchronized(lock) { listener }?.invoke(snapshot) }
    private fun fail(message: String, phase: HarnessPhase? = null) { publish { it.copy(lastError = message, phase = phase ?: it.phase) } }
    private fun requireContext() = requireNotNull(context)
    private fun elapsed(start: Long) = (System.nanoTime() - start) / 1_000_000
    private fun pss(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss
}
