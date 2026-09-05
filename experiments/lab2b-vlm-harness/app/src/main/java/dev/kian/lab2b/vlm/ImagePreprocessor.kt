// Centre-crop strategy adapted from Qualcomm AI Hub Apps at commit
// db3f9772d4e423dee2df517335009c703845dba8.
// Copyright 2025-2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
package dev.kian.lab2b.vlm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

object ImagePreprocessor {
    fun prepare(source: File, target: File, size: Int): File {
        require(size > 0) { "Projector image size must be positive" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image cannot be decoded" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = run {
                val shorter = minOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (shorter / (sample * 2) >= size) sample *= 2
                sample
            }
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var bitmap = BitmapFactory.decodeFile(source.absolutePath, options) ?: error("Image decode failed")
        val matrix = orientationMatrix(source)
        if (!matrix.isIdentity) {
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (transformed !== bitmap) bitmap.recycle()
            bitmap = transformed
        }

        val scale = size.toFloat() / minOf(bitmap.width, bitmap.height)
        val scaledWidth = ceil(bitmap.width * scale).toInt().coerceAtLeast(size)
        val scaledHeight = ceil(bitmap.height * scale).toInt().coerceAtLeast(size)
        val scaled = if (bitmap.width != scaledWidth || bitmap.height != scaledHeight) {
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } else {
            bitmap
        }
        if (scaled !== bitmap) bitmap.recycle()

        val cropped = Bitmap.createBitmap(
            scaled,
            (scaled.width - size) / 2,
            (scaled.height - size) / 2,
            size,
            size,
        )
        if (cropped !== scaled) scaled.recycle()

        target.parentFile?.mkdirs()
        FileOutputStream(target).use { output ->
            check(cropped.compress(Bitmap.CompressFormat.JPEG, 100, output)) { "JPEG write failed" }
        }
        cropped.recycle()
        return target
    }

    private fun orientationMatrix(source: File): Matrix {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        return Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
            }
        }
    }
}
