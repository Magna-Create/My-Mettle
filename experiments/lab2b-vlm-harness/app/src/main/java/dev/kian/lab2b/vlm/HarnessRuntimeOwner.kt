package dev.kian.lab2b.vlm

import android.content.Context
import android.net.Uri
import android.os.Debug
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.ModelManagerWrapper
import com.geniex.sdk.VlmWrapper
import com.geniex.sdk.bean.ComputeUnitValue
import com.geniex.sdk.bean.GenerationConfig
import com.geniex.sdk.bean.HubSource
import com.geniex.sdk.bean.LlmStreamResult
import com.geniex.sdk.bean.ModelConfig
import com.geniex.sdk.bean.ModelPullInput
import com.geniex.sdk.bean.VlmChatMessage
import com.geniex.sdk.bean.VlmContent
import com.geniex.sdk.bean.VlmCreateInput
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

object HarnessRuntimeOwner {
    const val LOCAL_MODEL_NAME = "local/qwen3-vl-2b-instruct-q4_0"
    const val EXPECTED_RUNTIME_ID = "llama_cpp"
    const val REQUESTED_COMPUTE_UNIT = "npu"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var appContext: Context? = null
    private var wrapper: VlmWrapper? = null
    private var generationJob: Job? = null
    private var listener: ((HarnessSnapshot) -> Unit)? = null

    @Volatile
    private var snapshot = HarnessSnapshot()

    fun currentSnapshot(): HarnessSnapshot = snapshot

    fun setListener(listener: ((HarnessSnapshot) -> Unit)?) {
        synchronized(lock) { this.listener = listener }
        listener?.invoke(snapshot)
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            GenieXSdk.getInstance().init(
                context.applicationContext,
                object : GenieXSdk.InitCallback {
                    override fun onSuccess() {
                        publish { it.copy(sdkReady = true, lastError = null) }
                        recoverManagedModel()
                    }

                    override fun onFailure(reason: String) {
                        fail("GenieX init failed: $reason")
                    }
                },
            )
        }
    }

    fun importModelFolder(treeUri: Uri) {
        val context = requireContext()
        synchronized(lock) {
            if (!HarnessStateMachine.canImport(snapshot.phase)) return
            snapshot = snapshot.copy(phase = HarnessPhase.IMPORTING, lastError = null)
        }
        notifySnapshot()

        scope.launch {
            runCatching {
                val bundle = StorageIo.copyModelFolder(context, treeUri)
                publish {
                    it.copy(
                        sourceFolderLabel = treeUri.lastPathSegment,
                        modelBundle = bundle,
                    )
                }

                val input = ModelPullInput(
                    model_name = LOCAL_MODEL_NAME,
                    precision = "Q4_0",
                    hub = HubSource.LOCALFS,
                    local_path = bundle.stagingDir,
                )

                var completed = false
                var pullFailure: String? = null
                ModelManagerWrapper.pullFlow(input).collect { event ->
                    when (event) {
                        is ModelManagerWrapper.PullEvent.Completed -> completed = true
                        is ModelManagerWrapper.PullEvent.Error -> pullFailure = "${event.message} [${event.code}]"
                        is ModelManagerWrapper.PullEvent.Progress -> Unit
                    }
                }
                check(completed && pullFailure == null) { pullFailure ?: "LOCALFS import did not complete" }

                val paths = requireNotNull(ModelManagerWrapper.getPaths(LOCAL_MODEL_NAME)) {
                    "ModelManager returned no ModelPaths after import"
                }
                val mmproj = requireNotNull(paths.mmproj_path?.takeIf { it.isNotBlank() }) {
                    "ModelManager returned no mmproj_path"
                }
                require(paths.model_path.isNotBlank()) { "ModelManager returned blank model_path" }
                require(paths.runtime_id.isNotBlank()) { "ModelManager returned blank runtime_id" }
                require(paths.runtime_id == EXPECTED_RUNTIME_ID) {
                    "Expected runtime_id=$EXPECTED_RUNTIME_ID; got ${paths.runtime_id}"
                }

                publish {
                    it.copy(
                        phase = HarnessPhase.IMPORTED,
                        resolvedRuntimeId = paths.runtime_id,
                        resolvedModelPath = paths.model_path,
                        resolvedMmprojPath = mmproj,
                        managedCacheBytes = StorageIo.directoryBytes(paths.model_dir),
                        backendEvidence = BackendEvidence(
                            requestedComputeUnit = REQUESTED_COMPUTE_UNIT,
                            resolvedRuntimeId = paths.runtime_id,
                        ),
                        lastError = null,
                    )
                }
            }.onFailure { fail("Model import failed: ${it.message ?: it}") }
        }
    }

    fun selectImage(uri: Uri) {
        val context = requireContext()
        scope.launch {
            runCatching { StorageIo.copyImage(context, uri) }
                .onSuccess { image -> publish { it.copy(selectedImage = image, lastError = null) } }
                .onFailure { fail("Image import failed: ${it.message ?: it}", keepPhase = true) }
        }
    }

    fun loadNpu() {
        scope.launch {
            if (!begin(HarnessPhase.IMPORTED, HarnessPhase.LOADING)) return@launch
            loadNpuInternal()
        }
    }

    fun reloadNpu() {
        scope.launch {
            if (!begin(HarnessPhase.READY, HarnessPhase.UNLOADING)) return@launch
            if (!destroyInternal()) return@launch
            publish { it.copy(phase = HarnessPhase.IMPORTED) }
            if (!begin(HarnessPhase.IMPORTED, HarnessPhase.LOADING)) return@launch
            loadNpuInternal()
        }
    }

    fun generate(prompt: String) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty()) {
            fail("Prompt is empty", keepPhase = true)
            return
        }

        synchronized(lock) {
            if (HarnessStateMachine.concurrentGenerationRejected(snapshot.phase)) return
            if (!HarnessStateMachine.canGenerate(snapshot.phase)) return
            snapshot = snapshot.copy(
                phase = HarnessPhase.GENERATING,
                output = "",
                timing = snapshot.timing.copy(firstOutputMs = null, totalGenerationMs = null),
                lastError = null,
            )
        }
        notifySnapshot()

        generationJob = scope.launch {
            val activeWrapper = wrapper ?: run {
                fail("No VlmWrapper is loaded")
                return@launch
            }
            val image = snapshot.selectedImage ?: run {
                fail("Select one image before generation")
                return@launch
            }
            val vision = snapshot.visionConfig ?: run {
                fail("Projector geometry is unavailable")
                return@launch
            }

            runCatching {
                val prepared = File(requireContext().filesDir, "lab2b/images/prepared.jpg")
                ImagePreprocessor.prepare(File(image.sourcePrivatePath), prepared, vision.imageSize)
                val preparedImage = image.copy(
                    preparedPath = prepared.absolutePath,
                    preparedWidth = vision.imageSize,
                    preparedHeight = vision.imageSize,
                )
                publish { it.copy(selectedImage = preparedImage) }

                val message = VlmChatMessage(
                    role = "user",
                    contents = listOf(
                        VlmContent("image", prepared.absolutePath),
                        VlmContent("text", trimmedPrompt),
                    ),
                )
                val template = activeWrapper.applyChatTemplate(arrayOf(message), null, false).getOrThrow()
                val baseConfig = GenerationConfig(
                    maxTokens = 2048,
                    stopWords = null,
                    stopCount = 0,
                    nPast = 0,
                    imagePaths = null,
                    imageCount = 0,
                    audioPaths = null,
                    audioCount = 0,
                )
                val config = activeWrapper.injectMediaPathsToConfig(arrayOf(message), baseConfig)
                val startedNs = System.nanoTime()
                var firstOutputMs: Long? = null
                val output = StringBuilder()

                activeWrapper.generateStreamFlow(template.formattedText, config).collect { result ->
                    when (result) {
                        is LlmStreamResult.Token -> {
                            if (firstOutputMs == null) {
                                firstOutputMs = elapsedMs(startedNs)
                            }
                            output.append(result.text)
                            publish {
                                it.copy(
                                    output = output.toString(),
                                    timing = it.timing.copy(firstOutputMs = firstOutputMs),
                                )
                            }
                        }

                        is LlmStreamResult.Completed -> {
                            publish {
                                it.copy(
                                    phase = HarnessPhase.READY,
                                    output = output.toString(),
                                    timing = it.timing.copy(
                                        firstOutputMs = firstOutputMs,
                                        totalGenerationMs = elapsedMs(startedNs),
                                    ),
                                    lastError = null,
                                )
                            }
                        }

                        is LlmStreamResult.Error -> throw result.throwable
                    }
                }
            }.onFailure { fail("Generation failed: ${it.message ?: it}") }
        }
    }

    fun stopGeneration() {
        synchronized(lock) {
            if (!HarnessStateMachine.canStop(snapshot.phase)) return
            snapshot = snapshot.copy(phase = HarnessPhase.STOPPING)
        }
        notifySnapshot()

        scope.launch {
            val activeWrapper = wrapper ?: run {
                fail("Stop requested with no VlmWrapper")
                return@launch
            }
            val result = activeWrapper.stopStream()
            if (result.isFailure) {
                fail("stopStream failed: ${result.exceptionOrNull()?.message}")
            } else {
                publish { it.copy(phase = HarnessPhase.READY, lastError = null) }
            }
        }
    }

    fun unload() {
        scope.launch {
            val allowed = synchronized(lock) { HarnessStateMachine.canUnload(snapshot.phase) }
            if (!allowed) return@launch
            publish { it.copy(phase = HarnessPhase.UNLOADING) }
            destroyInternal()
        }
    }

    private suspend fun loadNpuInternal() {
        val paths = ModelManagerWrapper.getPaths(LOCAL_MODEL_NAME) ?: run {
            fail("ModelManager no longer resolves $LOCAL_MODEL_NAME")
            return
        }
        val mmprojPath = paths.mmproj_path?.takeIf { it.isNotBlank() } ?: run {
            fail("Resolved model has no mmproj_path")
            return
        }
        if (paths.runtime_id != EXPECTED_RUNTIME_ID) {
            fail("Expected runtime_id=$EXPECTED_RUNTIME_ID; got ${paths.runtime_id}")
            return
        }

        val vision = GgufVisionReader.read(File(mmprojPath)) ?: run {
            fail("Could not read projector geometry from ${File(mmprojPath).name}; refusing magic fallback")
            return
        }
        val beforePss = totalPssKb()
        val startedNs = System.nanoTime()
        val result = VlmWrapper
            .builder()
            .vlmCreateInput(
                VlmCreateInput(
                    model_name = paths.model_name,
                    model_path = paths.model_path,
                    mmproj_path = mmprojPath,
                    config = ModelConfig(
                        nCtx = 4096,
                        nThreads = 4,
                        nBatch = 1,
                        nUBatch = 1,
                        nGpuLayers = 999,
                        enable_thinking = false,
                    ),
                    runtime_id = paths.runtime_id,
                    compute_unit = ComputeUnitValue.NPU.value,
                ),
            )
            .build()

        result.onSuccess { loaded ->
            wrapper = loaded
            publish {
                it.copy(
                    phase = HarnessPhase.READY,
                    requestedComputeUnit = REQUESTED_COMPUTE_UNIT,
                    resolvedRuntimeId = paths.runtime_id,
                    resolvedModelPath = paths.model_path,
                    resolvedMmprojPath = mmprojPath,
                    managedCacheBytes = StorageIo.directoryBytes(paths.model_dir),
                    visionConfig = vision,
                    timing = it.timing.copy(loadMs = elapsedMs(startedNs)),
                    memory = it.memory.copy(
                        beforeLoadPssKb = beforePss,
                        loadedPssKb = totalPssKb(),
                    ),
                    backendEvidence = BackendEvidence(
                        requestedComputeUnit = REQUESTED_COMPUTE_UNIT,
                        resolvedRuntimeId = paths.runtime_id,
                    ),
                    lastError = null,
                )
            }
        }.onFailure { fail("VLM load failed: ${it.message ?: it}") }
    }

    private suspend fun destroyInternal(): Boolean {
        val activeWrapper = wrapper
        if (activeWrapper != null) {
            val stop = activeWrapper.stopStream()
            if (stop.isFailure) {
                fail("stopStream before destroy failed: ${stop.exceptionOrNull()?.message}")
                return false
            }
            val rc = activeWrapper.destroy()
            if (rc != 0) {
                fail("VlmWrapper.destroy failed rc=$rc")
                return false
            }
        }
        wrapper = null
        generationJob = null
        publish {
            it.copy(
                phase = if (it.resolvedModelPath != null) HarnessPhase.IMPORTED else HarnessPhase.IDLE,
                visionConfig = null,
                memory = it.memory.copy(afterDestroyPssKb = totalPssKb()),
                lastError = null,
            )
        }
        return true
    }

    private fun recoverManagedModel() {
        scope.launch {
            runCatching { ModelManagerWrapper.getPaths(LOCAL_MODEL_NAME) }
                .onSuccess { paths ->
                    if (paths != null && paths.model_path.isNotBlank() && !paths.mmproj_path.isNullOrBlank()) {
                        publish {
                            it.copy(
                                phase = HarnessPhase.IMPORTED,
                                resolvedRuntimeId = paths.runtime_id,
                                resolvedModelPath = paths.model_path,
                                resolvedMmprojPath = paths.mmproj_path,
                                managedCacheBytes = StorageIo.directoryBytes(paths.model_dir),
                                backendEvidence = BackendEvidence(
                                    requestedComputeUnit = REQUESTED_COMPUTE_UNIT,
                                    resolvedRuntimeId = paths.runtime_id,
                                ),
                            )
                        }
                    }
                }
                .onFailure { fail("Model recovery failed: ${it.message ?: it}", keepPhase = true) }
        }
    }

    private fun begin(expected: HarnessPhase, next: HarnessPhase): Boolean {
        synchronized(lock) {
            if (snapshot.phase != expected) return false
            snapshot = snapshot.copy(phase = next, lastError = null)
        }
        notifySnapshot()
        return true
    }

    private fun fail(message: String, keepPhase: Boolean = false) {
        publish {
            it.copy(
                phase = if (keepPhase) it.phase else HarnessPhase.FAILED,
                lastError = message,
            )
        }
    }

    private fun publish(transform: (HarnessSnapshot) -> HarnessSnapshot) {
        synchronized(lock) { snapshot = transform(snapshot) }
        notifySnapshot()
    }

    private fun notifySnapshot() {
        val callback = synchronized(lock) { listener }
        callback?.invoke(snapshot)
    }

    private fun requireContext(): Context = requireNotNull(appContext) { "HarnessRuntimeOwner is not initialised" }

    private fun totalPssKb(): Int {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss
    }

    private fun elapsedMs(startedNs: Long): Long = (System.nanoTime() - startedNs) / 1_000_000L
}
