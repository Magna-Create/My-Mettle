package dev.kian.lab2b.vlm

data class SelectedImageInfo(
    val sourceName: String, val sourcePrivatePath: String, val sourceBytes: Long,
    val sourceWidth: Int, val sourceHeight: Int, val sourceSha256: String,
    val orientation: Int, val normalisation: String,
    val normalisedPath: String, val normalisedSha256: String, val normalisedWidth: Int, val normalisedHeight: Int,
    val preparedPath: String, val preparedSha256: String, val preparedWidth: Int, val preparedHeight: Int,
    val preparedBytes: Long, val preparedFormat: String = "PNG",
)
