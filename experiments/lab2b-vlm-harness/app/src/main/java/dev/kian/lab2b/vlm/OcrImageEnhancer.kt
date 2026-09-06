package dev.kian.lab2b.vlm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Experimental OCR inputs. Always derived from the unchanged normalised crop. */
object OcrImageEnhancer {
    fun prepare(source: SelectedImageInfo, profile: OcrEnhancement): SelectedImageInfo {
        if (profile == OcrEnhancement.ORIGINAL) return source
        val bitmap = requireNotNull(BitmapFactory.decodeFile(source.normalisedPath))
        try {
            val w = bitmap.width; val h = bitmap.height
            val pixels = IntArray(w * h); bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val p = pixels[i]
                val gray = (77 * ((p shr 16) and 255) + 150 * ((p shr 8) and 255) + 29 * (p and 255)) shr 8
                pixels[i] = gray
            }
            val output = IntArray(pixels.size)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y*w+x; val v = pixels[i]
                val g = when (profile) {
                    OcrEnhancement.GREYSCALE_CONTRAST -> ((v-128)*1.35+128).toInt().coerceIn(0,255)
                    OcrEnhancement.BLACK_WHITE -> if (v >= 128) 255 else 0
                    OcrEnhancement.GREYSCALE_SHARPEN -> if (x > 0 && y > 0 && x < w-1 && y < h-1)
                        (5*v-pixels[i-1]-pixels[i+1]-pixels[i-w]-pixels[i+w]).coerceIn(0,255) else v
                    else -> v
                }
                output[i] = (255 shl 24) or (g shl 16) or (g shl 8) or g
            }
            val result = Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888)
            val file = File(File(source.normalisedPath).parentFile, "ocr-${profile.name}.png")
            try { file.outputStream().use { check(result.compress(Bitmap.CompressFormat.PNG,100,it)) } }
            finally { result.recycle() }
            return source.copy(normalisedPath=file.absolutePath, normalisedSha256=Hashing.sha256(file),
                normalisation=source.normalisation + "; OCR experimental " + profile.name)
        } finally { bitmap.recycle() }
    }
}
