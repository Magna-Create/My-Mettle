package dev.kian.lab2b.vlm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import org.json.JSONObject

object CropImages {
    fun prepare(original: SelectedImageInfo, region: CropRegion): SelectedImageInfo {
        val root = File(original.sourcePrivatePath).parentFile!!
        val directory = File(root, "crop-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(original.normalisedPath))
        try {
            val (l, t, r, b) = region.pixels(bitmap.width, bitmap.height)
            val crop = Bitmap.createBitmap(bitmap, l, t, r-l, b-t)
            val source = File(directory, "crop.png")
            try { source.outputStream().use { check(crop.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { if (crop !== bitmap) crop.recycle() }
            return ImagePreprocessor.prepare(source, directory, "${original.sourceName} / ${region.label}")
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
        finally { bitmap.recycle() }
    }
    fun parse(raw: String): List<CropRegion> {
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val regions = JSONObject(clean).getJSONArray("regions")
        require(regions.length() <= 4) { "At most four crop proposals" }
        return (0 until regions.length()).map { index ->
            val region = regions.getJSONObject(index)
            val box = region.getJSONArray("box")
            require(box.length() == 4)
            CropRegion(region.getString("label"), box.getDouble(0), box.getDouble(1), box.getDouble(2), box.getDouble(3))
        }
    }
}
