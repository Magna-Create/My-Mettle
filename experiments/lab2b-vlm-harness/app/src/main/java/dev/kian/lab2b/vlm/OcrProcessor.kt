package dev.kian.lab2b.vlm

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import java.io.File
import android.graphics.Point
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrProcessor {
    suspend fun recognise(image: SelectedImageInfo, separateColumns: Boolean = false): OcrEvidence {
        // Decode the normalised OCR PNG directly: no URI helper's hidden resize.
        val bitmap = requireNotNull(BitmapFactory.decodeFile(image.normalisedPath))
        return recogniseBitmap(bitmap, image.normalisedSha256, separateColumns)
    }

    /** Reuse the exact ML Kit OCR implementation for a prepared LAB-2B cloud-test crop. */
    suspend fun recogniseLocalPath(path: String): OcrEvidence {
        val image = File(path)
        require(image.isFile) { "OCR image is missing." }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(path)) { "OCR image could not be decoded." }
        return recogniseBitmap(bitmap, Hashing.sha256(image), separateColumns = false)
    }

    private suspend fun recogniseBitmap(
        bitmap: Bitmap,
        sourceSha256: String,
        separateColumns: Boolean,
    ): OcrEvidence {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val start = System.nanoTime()
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            return OcrEvidence(text.text, text.textBlocks.map { block ->
                OcrBlock(block.text, block.boundingBox.box(), block.cornerPoints.points(), block.recognizedLanguage,
                    block.lines.flatMap { line ->
                        if(separateColumns && line.elements.count { element -> element.text.any(Char::isDigit) }>=2)
                            line.elements.map { element -> OcrLine(element.text,element.boundingBox.box(),element.cornerPoints.points(),line.recognizedLanguage) }
                        else listOf(OcrLine(line.text, line.boundingBox.box(), line.cornerPoints.points(), line.recognizedLanguage))
                    })
            }, (System.nanoTime() - start) / 1_000_000, sourceSha256, bitmap.width, bitmap.height)
        } finally { recognizer.close(); bitmap.recycle() }
    }
    private fun Rect?.box() = this?.let { OcrBox(left, top, right, bottom) }
    private fun Array<Point>?.points() = this?.map { OcrPoint(it.x, it.y) } ?: emptyList()
}
