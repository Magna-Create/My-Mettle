package dev.kian.lab2b.vlm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.max

/** Single-user experimental key vault. Never place keys in Gradle, APK assets or exported drafts. */
internal class CloudVisionKeyVault(private val context: Context) {
    private val prefs = context.getSharedPreferences("lab2b_cloud_vision_private", Context.MODE_PRIVATE)
    private val alias = "lab2b_cloud_vision_aes_gcm_v1"

    fun hasKey(): Boolean = prefs.contains("secret")
    fun clear() { prefs.edit().remove("secret").apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun store(raw: String) {
        val value = raw.trim()
        require(value.length in 20..256 && !value.any(Char::isWhitespace)) {
            "Enter a Google Cloud API key (not a service-account JSON file)."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        prefs.edit().putString("secret", Base64.encodeToString(payload, Base64.NO_WRAP)).commit().also {
            check(it) { "Could not save key" }
        }
    }

    /** Use just for the HTTPS request. Never expose the result in a UI, file or log. */
    fun read(): String? {
        val encoded = prefs.getString("secret", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(bytes.size > 12 + 16) { "Invalid key payload" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            // A restored or invalidated Keystore key must not silently use plaintext.
            clear()
            null
        }
    }
}

internal enum class CloudVisionFeature(val apiName: String) {
    LOGO("LOGO_DETECTION"),
    WEB("WEB_DETECTION"),
}

internal data class CloudBrandCandidate(val name: String, val score: Double?, val source: String)
internal data class CloudVisionEvidence(
    val feature: CloudVisionFeature,
    val candidates: List<CloudBrandCandidate>,
    val bestGuess: List<String>,
    val matchingPages: List<String>,
    val elapsedMs: Long,
    val uploadBytes: Int,
) {
    /** Candidates are evidence, never confirmed machine identity. */
    val hasCandidates get() = candidates.isNotEmpty() || bestGuess.isNotEmpty()
}

internal object CloudVisionResponseParser {
    fun parse(feature: CloudVisionFeature, response: JSONObject, elapsedMs: Long, bytes: Int): CloudVisionEvidence {
        val annotation = response.optJSONArray("responses")?.optJSONObject(0)
            ?: error("Cloud Vision returned no image annotations.")
        if (annotation.has("error")) {
            error("Cloud Vision rejected the image. Check project billing, quota and feature permissions.")
        }
        val candidates = mutableListOf<CloudBrandCandidate>()
        val guesses = mutableListOf<String>()
        val pages = mutableListOf<String>()
        if (feature == CloudVisionFeature.LOGO) {
            val items = annotation.optJSONArray("logoAnnotations") ?: JSONArray()
            for (i in 0 until minOf(items.length(), 12)) {
                val it = items.optJSONObject(i) ?: continue
                val name = it.optString("description").trim()
                if (name.isNotEmpty()) candidates += CloudBrandCandidate(name, it.optDouble("score").takeIf(Double::isFinite), "logo")
            }
        } else {
            val web = annotation.optJSONObject("webDetection") ?: JSONObject()
            val items = web.optJSONArray("webEntities") ?: JSONArray()
            for (i in 0 until minOf(items.length(), 12)) {
                val it = items.optJSONObject(i) ?: continue
                val name = it.optString("description").trim()
                if (name.isNotEmpty()) candidates += CloudBrandCandidate(name, it.optDouble("score").takeIf(Double::isFinite), "web entity")
            }
            val labels = web.optJSONArray("bestGuessLabels") ?: JSONArray()
            for (i in 0 until minOf(labels.length(), 5)) {
                val label = labels.optJSONObject(i)?.optString("label")?.trim().orEmpty()
                if (label.isNotEmpty()) guesses += label
            }
            val matches = web.optJSONArray("pagesWithMatchingImages") ?: JSONArray()
            for (i in 0 until minOf(matches.length(), 5)) {
                val url = matches.optJSONObject(i)?.optString("url").orEmpty()
                if (url.startsWith("https://") || url.startsWith("http://")) pages += url.take(300)
            }
        }
        return CloudVisionEvidence(feature, candidates, guesses, pages, elapsedMs, bytes)
    }
}

/** Network requests happen only after a user's explicit confirmation in CloudVisionActivity. */
internal object CloudVisionLabClient {
    private const val ENDPOINT = "https://vision.googleapis.com/v1/images:annotate"
    private const val MAX_UPLOAD = 3_000_000

    /** Expects a source already normalised/cropped into private LAB-2B storage. */
    fun call(context: Context, privateImagePath: String, apiKey: String, feature: CloudVisionFeature): CloudVisionEvidence {
        val source = File(privateImagePath).canonicalFile
        val privateRoot = context.filesDir.canonicalFile
        require(source.path.startsWith(privateRoot.path + File.separator) && source.isFile) {
            "Choose a locally prepared LAB-2B image."
        }
        val image = prepareSmallJpeg(source)
        val payload = JSONObject().put("requests", JSONArray().put(
            JSONObject()
                .put("image", JSONObject().put("content", Base64.encodeToString(image, Base64.NO_WRAP)))
                .put("features", JSONArray().put(
                    JSONObject().put("type", feature.apiName).put("maxResults", 12),
                )),
        )).toString().toByteArray(Charsets.UTF_8)
        val start = System.nanoTime()
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // No secret in URL, source, diagnostics, or exception strings.
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val reason = when (code) {
                    400 -> "Invalid image or request."
                    401, 403 -> "API key rejected, API disabled, billing unavailable, or key restriction blocked the request."
                    429 -> "Cloud Vision quota or rate limit reached."
                    in 500..599 -> "Cloud Vision is temporarily unavailable."
                    else -> "Request was not accepted."
                }
                throw IllegalStateException("Cloud Vision HTTP $code: $reason")
            }
            // Keep returned payload bounded; do not persist it or the image.
            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(400_000) }
            val duration = (System.nanoTime() - start) / 1_000_000
            return CloudVisionResponseParser.parse(feature, JSONObject(responseText), duration, image.size)
        } finally {
            connection.disconnect()
        }
    }

    private fun prepareSmallJpeg(source: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image could not be decoded" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1500) sample *= 2
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Cannot decode image")
        try {
            // Existing LAB-2B source/crop is already EXIF-normalised. No raw camera JPEG is sent.
            val output = ByteArrayOutputStream()
            require(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) { "Cannot encode image" }
            val bytes = output.toByteArray()
            require(bytes.size in 1..MAX_UPLOAD) { "Image is too large to send; crop the logo more tightly." }
            return bytes
        } finally {
            bitmap.recycle()
        }
    }
}
