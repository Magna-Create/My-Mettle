package dev.kian.lab2b.vlm

enum class HarnessPhase {
    IDLE,
    IMPORTING,
    IMPORTED,
    LOADING,
    READY,
    GENERATING,
    STOPPING,
    UNLOADING,
    FAILED,
}

data class TimingSnapshot(
    val loadMs: Long? = null,
    val firstOutputMs: Long? = null,
    val totalGenerationMs: Long? = null,
)

data class MemorySnapshot(
    val beforeLoadPssKb: Int? = null,
    val loadedPssKb: Int? = null,
    val afterDestroyPssKb: Int? = null,
)

data class HarnessSnapshot(
    val phase: HarnessPhase = HarnessPhase.IDLE,
    val sdkReady: Boolean = false,
    val requestedComputeUnit: String = "npu",
    val resolvedRuntimeId: String? = null,
    val sourceFolderLabel: String? = null,
    val modelBundle: ModelBundleInfo? = null,
    val resolvedModelPath: String? = null,
    val resolvedMmprojPath: String? = null,
    val managedCacheBytes: Long? = null,
    val selectedImage: SelectedImageInfo? = null,
    val visionConfig: GgufVisionConfig? = null,
    val output: String = "",
    val timing: TimingSnapshot = TimingSnapshot(),
    val memory: MemorySnapshot = MemorySnapshot(),
    val backendEvidence: BackendEvidence = BackendEvidence(),
    val lastError: String? = null,
)

object HarnessStateMachine {
    fun canImport(phase: HarnessPhase): Boolean =
        phase == HarnessPhase.IDLE || phase == HarnessPhase.IMPORTED || phase == HarnessPhase.FAILED

    fun canLoad(phase: HarnessPhase): Boolean =
        phase == HarnessPhase.IMPORTED

    fun canGenerate(phase: HarnessPhase): Boolean =
        phase == HarnessPhase.READY

    fun canStop(phase: HarnessPhase): Boolean =
        phase == HarnessPhase.GENERATING

    fun canUnload(phase: HarnessPhase): Boolean =
        phase == HarnessPhase.READY || phase == HarnessPhase.IMPORTED || phase == HarnessPhase.FAILED

    fun concurrentGenerationRejected(phase: HarnessPhase): Boolean =
        phase == HarnessPhase.GENERATING || phase == HarnessPhase.STOPPING
}
