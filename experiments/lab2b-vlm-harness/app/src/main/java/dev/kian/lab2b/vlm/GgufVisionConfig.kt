// Adapted from Qualcomm AI Hub Apps at commit
// db3f9772d4e423dee2df517335009c703845dba8.
// Copyright 2025-2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
package dev.kian.lab2b.vlm

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufVisionConfig(
    val imageSize: Int,
    val patchSize: Int,
    val spatialMergeSize: Int,
) {
    val tokenCount: Int
        get() {
            val grid = imageSize / patchSize / spatialMergeSize
            return grid * grid
        }
}

object GgufVisionReader {
    private const val TAG = "Lab2BGgufVision"
    private const val GGUF_MAGIC = 0x46554747
    private const val MAX_HEADER_BYTES = 8L * 1024 * 1024

    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    private const val KEY_IMAGE_SIZE = "clip.vision.image_size"
    private const val KEY_PATCH_SIZE = "clip.vision.patch_size"
    private const val KEY_MERGE_SIZE = "clip.vision.spatial_merge_size"

    fun read(mmprojFile: File): GgufVisionConfig? {
        if (!mmprojFile.isFile) return null
        return try {
            RandomAccessFile(mmprojFile, "r").use(::parse)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ${mmprojFile.name}", e)
            null
        }
    }

    private fun parse(raf: RandomAccessFile): GgufVisionConfig? {
        if (raf.length() < 24) return null
        if (readU32(raf).toInt() != GGUF_MAGIC) return null
        readU32(raf)
        readU64(raf)
        val kvCount = readU64(raf)

        var imageSize: Int? = null
        var patchSize: Int? = null
        var mergeSize: Int? = null
        var i = 0L
        while (i < kvCount) {
            if (raf.filePointer > MAX_HEADER_BYTES) break
            val key = readString(raf) ?: break
            val value = readValue(raf, readU32(raf).toInt()) ?: break
            when (key) {
                KEY_IMAGE_SIZE -> imageSize = (value as? Number)?.toInt()
                KEY_PATCH_SIZE -> patchSize = (value as? Number)?.toInt()
                KEY_MERGE_SIZE -> mergeSize = (value as? Number)?.toInt()
            }
            if (imageSize != null && patchSize != null && mergeSize != null) break
            i++
        }

        val image = imageSize ?: return null
        val patch = patchSize ?: return null
        val merge = (mergeSize ?: 1).coerceAtLeast(1)
        if (image <= 0 || patch <= 0 || image / patch / merge < 1) return null
        return GgufVisionConfig(image, patch, merge)
    }

    private fun readValue(raf: RandomAccessFile, type: Int): Any? = when (type) {
        TYPE_UINT8, TYPE_INT8 -> raf.readByte().toInt()
        TYPE_UINT16, TYPE_INT16 -> readLE(raf, 2).short.toInt()
        TYPE_UINT32, TYPE_INT32 -> readU32(raf)
        TYPE_FLOAT32 -> readLE(raf, 4).float
        TYPE_BOOL -> raf.readByte().toInt() != 0
        TYPE_UINT64, TYPE_INT64 -> readU64(raf)
        TYPE_FLOAT64 -> readLE(raf, 8).double
        TYPE_STRING -> readString(raf)
        TYPE_ARRAY -> skipArray(raf)
        else -> null
    }

    private fun skipArray(raf: RandomAccessFile): Any? {
        val elemType = readU32(raf).toInt()
        val count = readU64(raf)
        if (elemType == TYPE_STRING) {
            repeat(count.toInt()) { if (readString(raf) == null) return null }
            return Unit
        }
        val width = when (elemType) {
            TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> 1L
            TYPE_UINT16, TYPE_INT16 -> 2L
            TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> 4L
            TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> 8L
            else -> return null
        }
        raf.seek(raf.filePointer + width * count)
        return Unit
    }

    private fun readString(raf: RandomAccessFile): String? {
        val length = readU64(raf)
        if (length < 0 || length > 1L shl 20) return null
        val bytes = ByteArray(length.toInt())
        raf.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readU32(raf: RandomAccessFile): Long = readLE(raf, 4).int.toLong() and 0xFFFFFFFFL
    private fun readU64(raf: RandomAccessFile): Long = readLE(raf, 8).long
    private fun readLE(raf: RandomAccessFile, bytes: Int): ByteBuffer {
        val value = ByteArray(bytes)
        raf.readFully(value)
        return ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
    }
}
