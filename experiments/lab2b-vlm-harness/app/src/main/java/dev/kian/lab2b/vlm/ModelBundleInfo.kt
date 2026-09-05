package dev.kian.lab2b.vlm

enum class ModelFileRole {
    MAIN,
    MMPROJ,
}

data class ModelFileInfo(
    val name: String,
    val bytes: Long,
    val sha256: String,
    val role: ModelFileRole,
)

data class ModelBundleInfo(
    val stagingDir: String,
    val files: List<ModelFileInfo>,
) {
    val main: ModelFileInfo?
        get() = files.singleOrNull { it.role == ModelFileRole.MAIN }

    val mmproj: ModelFileInfo?
        get() = files.singleOrNull { it.role == ModelFileRole.MMPROJ }

    val totalBytes: Long
        get() = files.sumOf { it.bytes }

    fun validationErrors(): List<String> = buildList {
        val mainFiles = files.filter { it.role == ModelFileRole.MAIN }
        val projectors = files.filter { it.role == ModelFileRole.MMPROJ }
        if (mainFiles.size != 1) add("Expected exactly one main GGUF; found ${mainFiles.size}")
        if (projectors.size != 1) add("Expected exactly one mmproj GGUF; found ${projectors.size}")
        files.forEach { file ->
            if (file.bytes <= 0L) add("${file.name} has no data")
            if (!SHA256.matches(file.sha256)) add("${file.name} has invalid SHA-256")
            if (!file.name.endsWith(".gguf", ignoreCase = true)) add("${file.name} is not GGUF")
        }
    }

    companion object {
        private val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

data class SelectedImageInfo(
    val sourceName: String,
    val sourcePrivatePath: String,
    val sourceBytes: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val preparedPath: String? = null,
    val preparedWidth: Int? = null,
    val preparedHeight: Int? = null,
)
