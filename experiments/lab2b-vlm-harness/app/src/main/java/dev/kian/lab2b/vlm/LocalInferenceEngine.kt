package dev.kian.lab2b.vlm

import org.json.JSONObject
import java.io.File

interface LocalInferenceEngine : AutoCloseable {
    val evidence: BackendEvidence
    fun arm()
    fun generate(turn: InferenceTurn, onOutput: (String) -> Unit): LongArray
    fun stop()
}
interface OutputCallback { fun onOutput(bytes: ByteArray) }
object MnnNative {
    init { System.loadLibrary("lab2b_mnn") }
    external fun load(path: ByteArray, config: ByteArray): Long
    external fun config(handle: Long): ByteArray
    external fun arm(handle: Long)
    external fun generate(handle: Long, system: ByteArray?, user: ByteArray, image: ByteArray?, maxTokens: Int, callback: OutputCallback): LongArray
    external fun stop(handle: Long)
    external fun unload(handle: Long)
}
class MnnEngine(model: HarnessModelSpec, directory: File, backend: ComputeBackend, cache: File) : LocalInferenceEngine {
    private var handle: Long = 0
    override val evidence: BackendEvidence
    init {
        cache.mkdirs()
        val config = JSONObject().put("backend_type", if (backend == ComputeBackend.CPU) "cpu" else "opencl")
            .put("thread_num", 4).put("precision", "normal").put("memory", "low")
            .put("max_new_tokens", 512).put("max_all_tokens", model.contextLength)
            .put("reuse_kv", false).put("prompt_cache", false)
            .put("use_mmap", true).put("tmp_path", cache.absolutePath + "/")
            .put("sampler_type", "greedy").put("is_audio", false)
            .put("jinja", JSONObject().put("context", JSONObject().put("enable_thinking", false)))
        // The pinned exports declare a separate CPU mllm configuration. set_config does
        // not reinitialise that cached configuration in 3.6.1: preserve CPU vision explicitly.
        val loaded = MnnNative.load(File(directory, "config.json").absolutePath.toByteArray(), config.toString().toByteArray())
        require(loaded != 0L) { "Native model owner was null" }
        handle = loaded
        try {
            val actualConfig = JSONObject(MnnNative.config(handle).toString(Charsets.UTF_8))
            val text = actualConfig.getString("backend_type")
            val vision = actualConfig.getJSONObject("mllm").getString("backend_type")
            require(text == if (backend == ComputeBackend.CPU) "cpu" else "opencl")
            require(vision == "cpu") { "Pinned model's vision configuration changed" }
            evidence = BackendEvidence.loaded(backend, text, vision)
        } catch (e: Exception) { close(); throw e }
    }
    override fun arm() { check(handle != 0L); MnnNative.arm(handle) }
    override fun generate(turn: InferenceTurn, onOutput: (String) -> Unit): LongArray {
        check(handle != 0L)
        return MnnNative.generate(handle, turn.system?.toByteArray(), turn.user.toByteArray(), turn.imagePath?.toByteArray(), 512,
            object : OutputCallback { override fun onOutput(bytes: ByteArray) { onOutput(bytes.toString(Charsets.UTF_8)) } })
    }
    override fun stop() { if (handle != 0L) MnnNative.stop(handle) }
    override fun close() { if (handle != 0L) { MnnNative.unload(handle); handle = 0 } }
}

/** Small ownership guard used by the real owner and lifecycle tests; never creates a second engine. */
class EngineSlot {
    var engine: LocalInferenceEngine? = null; private set
    var modelId: String? = null; private set
    fun install(id: String, loaded: LocalInferenceEngine) {
        check(engine == null) { "Unload the current engine before replacement" }
        engine = loaded; modelId = id
    }
    fun dispose() { engine?.close(); engine = null; modelId = null }
}
