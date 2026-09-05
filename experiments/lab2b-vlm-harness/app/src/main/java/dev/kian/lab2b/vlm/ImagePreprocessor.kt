package dev.kian.lab2b.vlm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.roundToInt

/** Preserve the complete frame. Runtime-specific patching remains inside MNN's vision processor. */
object ImagePreprocessor {
    fun prepare(source: File, directory: File, name: String): SelectedImageInfo {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image cannot be decoded" }
        val orientation = runCatching { ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 1
            while (bounds.outWidth.toLong() / inSampleSize * (bounds.outHeight / inSampleSize) > 16_000_000 ||
                maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 4096) inSampleSize *= 2
        }
        var bitmap = requireNotNull(BitmapFactory.decodeFile(source.absolutePath, options)) { "Image decode failed" }
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> postScale(-1f, 1f)
                3 -> postRotate(180f)
                4 -> postScale(1f, -1f)
                5 -> { postRotate(90f); postScale(-1f, 1f) }
                6 -> postRotate(90f)
                7 -> { postRotate(270f); postScale(-1f, 1f) }
                8 -> postRotate(270f)
            }
        }
        try {
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }
            // PNG transparency must not become black in one decoder and white in another.
            val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(opaque).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
            bitmap.recycle(); bitmap = opaque
            val normalised = File(directory, "normalised-ocr.png")
            normalised.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val normalisedHash = Hashing.sha256(normalised)
            val scale = minOf(1.0, 1600.0 / maxOf(bitmap.width, bitmap.height))
            val model = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
            val prepared = File(directory, "prepared-model.png")
            try { prepared.outputStream().use { check(model.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { if (model !== bitmap) model.recycle() }
            return SelectedImageInfo(name, source.absolutePath, source.length(), bounds.outWidth, bounds.outHeight,
                Hashing.sha256(source), orientation, "EXIF orientation; white alpha background; decode sample=${options.inSampleSize}; no crop",
                normalised.absolutePath, normalisedHash, bitmap.width, bitmap.height,
                prepared.absolutePath, Hashing.sha256(prepared), (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1), prepared.length())
        } finally { bitmap.recycle() }
    }
}
