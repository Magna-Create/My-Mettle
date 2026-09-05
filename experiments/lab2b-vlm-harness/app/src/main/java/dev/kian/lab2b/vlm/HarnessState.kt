package dev.kian.lab2b.vlm

enum class HarnessPhase { IDLE, LOADING, READY, PREPARING, GENERATING, STOPPING, UNLOADING, FAILED }
data class TimingSnapshot(val loadMs: Long? = null, val firstOutputMs: Long? = null, val totalGenerationMs: Long? = null)
data class MemorySnapshot(val beforeLoadPssKb: Int? = null, val loadedPssKb: Int? = null, val afterDestroyPssKb: Int? = null)
data class TranscriptTurn(val model: String, val backend: ComputeBackend, val mode: PipelineMode,
    val systemMode: SystemPromptMode, val instruction: String, val imageHash: String?, val response: String, val terminal: String)
data class HarnessSnapshot(
    val gpuFailedModels: Set<String> = emptySet(),
    val phase: HarnessPhase = HarnessPhase.IDLE,
    val selectedModelId: String = ModelRegistry.models.first().id,
    val loadedModelId: String? = null,
    val backend: ComputeBackend = ComputeBackend.CPU,
    val installations: Map<String, Installation> = emptyMap(),
    val selectedImage: SelectedImageInfo? = null,
    val ocr: OcrEvidence? = null, val ocrStatus: String = "NOT RUN", val ocrCacheHit: Boolean = false,
    val pipeline: PipelineMode = PipelineMode.VISION_PLUS_OCR,
    val preset: SystemPreset = SystemPreset.ENGLISH_GROUNDED, val customPrompt: PromptFile? = null,
    val promptText: String = "Respond in English only. Describe exactly what is visible in this image. Do not infer or invent anything.",
    val systemMode: SystemPromptMode = SystemPromptMode.NONE,
    val output: String = "", val transcript: List<TranscriptTurn> = emptyList(),
    val lastAssembledPrompt: String = "", val nativeMetrics: List<Long> = emptyList(),
    val timing: TimingSnapshot = TimingSnapshot(), val memory: MemorySnapshot = MemorySnapshot(),
    val backendEvidence: BackendEvidence = BackendEvidence(), val lastError: String? = null,
    val modelStoragePath: String = "", val installedBytes: Long = 0, val temporaryBytes: Long = 0,
)
object HarnessStateMachine {
    fun idle(phase: HarnessPhase) = phase in setOf(HarnessPhase.IDLE, HarnessPhase.READY, HarnessPhase.FAILED)
    fun canGenerate(s: HarnessSnapshot) = s.phase == HarnessPhase.READY && s.loadedModelId == s.selectedModelId && s.selectedImage != null
    fun canStop(phase: HarnessPhase) = phase in setOf(HarnessPhase.PREPARING, HarnessPhase.GENERATING)
    fun canUnload(phase: HarnessPhase) = idle(phase)
}
