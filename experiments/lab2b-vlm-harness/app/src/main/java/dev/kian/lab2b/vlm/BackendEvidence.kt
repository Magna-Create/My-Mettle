package dev.kian.lab2b.vlm

data class BackendEvidence(
    val requested: ComputeBackend = ComputeBackend.CPU,
    val configuredText: String? = null,
    val configuredVision: String? = null,
    val effectiveText: String = "NOT LOADED",
    val effectiveVision: String = "NOT LOADED",
    val gpuCorrectness: String = "EXPERIMENTAL / PHYSICAL CORRECTNESS UNTESTED",
) {
    companion object {
        fun loaded(requested: ComputeBackend, text: String, vision: String) = BackendEvidence(
            requested, text, vision,
            if (text == "cpu") "CPU (explicit CPU runtime)" else "UNVERIFIED: OpenCL configured; per-op placement/fallback not reported",
            if (vision == "cpu") "CPU (explicit CPU vision runtime)" else "UNVERIFIED: $vision configured",
        )
    }
}
