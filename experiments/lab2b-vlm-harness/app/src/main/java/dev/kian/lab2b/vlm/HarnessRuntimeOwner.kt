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
        }
        scope.launch {
            try {
                downloads = ModelDownloads(context)
                publish { it.copy(modelStoragePath = downloads.root.absolutePath) }
                val folder = File(context.filesDir, "lab2b/prompts")
                val saved = File(folder, "custom.txt")
                val metadata = File(folder, "name.txt")
                if (saved.isFile && metadata.isFile) {
                    runCatching { saved.inputStream().use { PromptFiles.read(metadata.readText(), it) } }
                        .onSuccess { prompt -> publish { it.copy(customPrompt = prompt) } }
                }
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
                publish { it.copy(selectedModelId = id, backend = model.defaultBackend, phase = HarnessPhase.IDLE,
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
                    File(requireContext().cacheDir, "lab2b-runtime/${model.id}/${selected.backend}"))
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
        val old = snapshot.selectedImage
        publish { it.copy(selectedImage = image, ocr = cache.get(image.normalisedSha256),
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
            val old = snapshot.selectedImage
            publish { it.copy(selectedImage = image, ocr = cache.get(image.normalisedSha256), ocrStatus = "NOT RUN") }
            old?.let { File(it.sourcePrivatePath).parentFile?.deleteRecursively() }
        } finally { bitmap.recycle() }
    }
    fun clearImage() {
        if (!HarnessStateMachine.idle(snapshot.phase)) return
        val old = snapshot.selectedImage
        publish { it.copy(selectedImage = null, ocr = null, ocrStatus = "NOT RUN") }
        scope.launch { old?.let { File(it.sourcePrivatePath).parentFile?.deleteRecursively() } }
    }
    fun selectPipeline(mode: PipelineMode) { if (HarnessStateMachine.idle(snapshot.phase)) publish { it.copy(pipeline = mode) } }
    fun selectPreset(preset: SystemPreset) { if (HarnessStateMachine.idle(snapshot.phase)) publish { it.copy(preset = preset) } }
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
    fun generate() {
        val request: HarnessSnapshot
        synchronized(lock) {
            if (!HarnessStateMachine.canGenerate(snapshot)) return
            request = snapshot
            cancelled.set(false)
            slot.engine!!.arm()
            snapshot = snapshot.copy(phase = HarnessPhase.PREPARING, output = "", lastError = null,
                timing = snapshot.timing.copy(firstOutputMs = null, totalGenerationMs = null))
        }
        notifySnapshot()
        scope.launch {
            var turn: InferenceTurn? = null
            var terminal = "FAILED"
            val started = System.nanoTime()
            try {
                val image = requireNotNull(request.selectedImage)
                val ocr = if (request.pipeline.ocr) recognise(image) else null
                if (cancelled.get()) { terminal = "STOPPED"; return@launch }
                val system = if (request.preset == SystemPreset.CUSTOM) requireNotNull(request.customPrompt) { "Select a custom prompt file" }.text else request.preset.prompt
                turn = PromptAssembler.assemble(request.promptText, system, ModelRegistry.get(request.selectedModelId).systemMode, request.pipeline, image, ocr)
                publish { it.copy(systemMode = turn.systemMode, lastAssembledPrompt = "SYSTEM (${turn.systemMode}):\n${turn.system ?: "none / preface below"}\n\n${turn.user}") }
                synchronized(lock) { if (!cancelled.get()) snapshot = snapshot.copy(phase = HarnessPhase.GENERATING) }
                notifySnapshot()
                if (cancelled.get()) { terminal = "STOPPED"; return@launch }
                val inferenceStart = System.nanoTime()
                val metrics = requireNotNull(slot.engine).generate(turn) { output ->
                    publish { it.copy(output = output, timing = it.timing.copy(firstOutputMs = it.timing.firstOutputMs ?: if (output.isNotEmpty()) elapsed(inferenceStart) else null)) }
                }
                terminal = if (cancelled.get() || metrics.lastOrNull() == 1L) "STOPPED" else "COMPLETED"
                publish { it.copy(nativeMetrics = metrics.toList(), timing = it.timing.copy(totalGenerationMs = elapsed(inferenceStart))) }
            } catch (e: Exception) { fail("Inference failed: ${e.message}") }
            finally {
                publish { it.copy(phase = if (slot.engine != null) HarnessPhase.READY else HarnessPhase.IDLE,
                    transcript = (it.transcript + TranscriptTurn(request.selectedModelId, request.backend, request.pipeline,
                        turn?.systemMode ?: SystemPromptMode.NONE, request.promptText, turn?.imageSha256, it.output, terminal)).takeLast(20)) }
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
    fun clearTranscript() { if (HarnessStateMachine.idle(snapshot.phase)) publish { it.copy(transcript = emptyList(), output = "", lastAssembledPrompt = "") } }
    fun reportGpuFailure() {
        publish { it.copy(backendEvidence = it.backendEvidence.copy(gpuCorrectness = "FAILED CORRECTNESS (manual observation); use CPU")) }
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
