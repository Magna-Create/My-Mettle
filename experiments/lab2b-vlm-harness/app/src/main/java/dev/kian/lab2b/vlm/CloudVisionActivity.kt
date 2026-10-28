package dev.kian.lab2b.vlm

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.UnknownHostException
import java.net.SocketTimeoutException

/**
 * Separate, explicitly opt-in Cloud Vision evaluator. Never changes OCR/equipment
 * review state or sends camera frames automatically.
 */
class CloudVisionActivity : AppCompatActivity() {
    companion object { const val IMAGE_PATH_EXTRA = "lab2b.cloud_vision.source_image" }

    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var vault: CloudVisionKeyVault
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var imagePath: String? = null
    private var busy = false
    private var status = "Choose a photo (or reuse your existing placard/logo crop)."
    private var lastResult: CloudVisionEvidence? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            status = "Preparing the selected image locally…"
            lastResult = null
            render()
            scope.launch {
                try {
                    val img = withContext(Dispatchers.IO) {
                        StorageIo.copyImage(this@CloudVisionActivity, uri, "lab2b/cloud-vision-images")
                    }
                    imagePath = img.normalisedPath
                    status = "Image prepared locally. Nothing uploaded yet."
                } catch (_: Exception) {
                    status = "Could not prepare image. Choose a supported image under 64 MB."
                } finally {
                    busy = false
                    render()
                }
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        vault = CloudVisionKeyVault(applicationContext)
        imagePath = state?.getString("source") ?: intent.getStringExtra(IMAGE_PATH_EXTRA)
        imagePath = imagePath?.takeIf(::allowedImage)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("source", imagePath)
        // No API keys, request JSON or cloud response in Activity state.
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun allowedImage(path: String): Boolean = runCatching {
        val image = File(path).canonicalFile
        image.isFile && image.path.startsWith(filesDir.canonicalPath + File.separator)
    }.getOrDefault(false)

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 15f) {
        root.addView(TextView(this).apply {
            text = value
            textSize = size
            setTextIsSelectable(true)
            setPadding(0, dp(6), 0, dp(6))
        })
    }
    private fun button(label: String, enabled: Boolean = true, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            isEnabled = enabled && !busy
            setOnClickListener { action() }
        })
    }

    private fun enterKey() {
        // Do not rehydrate the stored secret into an EditText or an exportable diagnostic.
        val field = EditText(this).apply {
            hint = "Google Cloud API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(256))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Your Google Cloud Vision API key")
            .setMessage("Stored encrypted in this harness's app-private storage, excluded from Android backup. Never commit a key to GitHub. A client-held key can still be extracted from a compromised device; restrict its API and quota.")
            .setView(field)
            .setPositiveButton("Save key", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    vault.store(field.text.toString())
                    field.text?.clear()
                    status = "Key saved locally. It has not been tested or sent yet."
                    lastResult = null
                    dialog.dismiss()
                    render()
                } catch (_: Exception) {
                    field.error = "Enter a valid API key, not a JSON service account credential."
                }
            }
        }
        dialog.show()
    }

    private fun request(feature: CloudVisionFeature) {
        val path = imagePath?.takeIf(::allowedImage) ?: return
        if (!vault.hasKey() || busy) return
        AlertDialog.Builder(this)
            .setTitle("Send image to Google Cloud?")
            .setMessage(
                "One prepared image (preferably the logo crop) will be uploaded to Google Cloud Vision for ${feature.apiName}. " +
                    "Google may charge this feature to your project. No image is sent until you choose Send. " +
                    "Bystanders or identifying background details should be cropped out first.",
            )
            .setPositiveButton("Send once") { _, _ -> perform(feature, path) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun perform(feature: CloudVisionFeature, path: String) {
        busy = true
        lastResult = null
        status = "Sending one ${feature.apiName} request…"
        render()
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val key = vault.read() ?: error("The saved key is unavailable. Re-enter it.")
                    CloudVisionLabClient.call(applicationContext, path, key, feature)
                }
                lastResult = result
                status = if (result.hasCandidates)
                    "Cloud Vision returned candidates. Nothing is verified or saved to equipment."
                else "No matching names found. Unknown is an acceptable result."
            } catch (e: Exception) {
                // Never echo exceptions, server bodies, request URLs or key material.
                status = when (e) {
                    is UnknownHostException -> "No internet or DNS connection; try again on Wi-Fi/mobile data."
                    is SocketTimeoutException -> "Cloud request timed out; no response received."
                    is IllegalStateException -> e.message?.takeIf { it.startsWith("Cloud Vision HTTP ") }
                        ?: "Cloud request failed. Check image, key, permissions and billing."
                    else -> "Cloud request failed. Check image, key, permissions and billing."
                }
            } finally {
                busy = false
                render()
            }
        }
    }

    private fun render() {
        if (!::root.isInitialized) return
        val y = scroll.scrollY
        root.removeAllViews()
        text("Google Cloud Vision • experimental", 23f)
        text("Runs in the separate LAB-2B app only. No language model, no automatic image upload, no changes to workout equipment.")
        text("1 · Personal Cloud key", 19f)
        text(if (vault.hasKey()) "Key stored on this device (encrypted; not yet verified)." else "No API key saved.")
        button(if (vault.hasKey()) "Replace API key" else "Enter API key") { enterKey() }
        if (vault.hasKey()) button("Delete saved API key") {
            AlertDialog.Builder(this)
                .setTitle("Remove API key?")
                .setMessage("Delete the encrypted credential from this harness. No Cloud project settings will be changed.")
                .setPositiveButton("Delete") { _, _ ->
                    vault.clear()
                    lastResult = null
                    status = "Key removed."
                    render()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        text("Enable Cloud Vision API and billing in your Google Cloud project; restrict the key to Cloud Vision and set quota/billing alerts. No service-account JSON is needed.", 13f)
        text(
            "Android key restriction: package ${packageName} • installed signing SHA-1 " +
                (CloudVisionLabClient.signingSha1(this) ?: "unavailable"),
            12f,
        )

        text("2 · Source image", 19f)
        val path = imagePath?.takeIf(::allowedImage)
        text(if (path == null) "No image chosen." else "Selected: ${File(path).name} (private prepared image)")
        button("Choose image") { picker.launch(arrayOf("image/*")) }
        if (path != null) {
            val bitmap = runCatching {
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                })
                val options = BitmapFactory.Options().apply { inSampleSize = 8 }
                BitmapFactory.decodeFile(path, options)
            }.getOrNull()
            if (bitmap != null) root.addView(ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                contentDescription = "Selected photo for Cloud Vision"
                layoutParams = LinearLayout.LayoutParams(-1, dp(165))
                scaleType = ImageView.ScaleType.FIT_CENTER
            })
        }
        text("Only the selected crop is sent. Prefer an existing logo crop to reduce background details.", 13f)

        text("3 · Explicit online tests", 19f)
        button("Test Logo Detection • one request", path != null && vault.hasKey()) {
            request(CloudVisionFeature.LOGO)
        }
        button("Test Web Detection • one request", path != null && vault.hasKey()) {
            request(CloudVisionFeature.WEB)
        }
        text(status)
        lastResult?.let { result ->
            text("Feature: ${result.feature.apiName} • ${result.elapsedMs} ms • ${result.uploadBytes / 1024} KiB sent", 16f)
            if (result.candidates.isEmpty()) text("No named logo/web entity candidates.")
            result.candidates.forEach { item ->
                text("${item.source}: ${item.name}" + (item.score?.let { " (API score: %.3f)".format(java.util.Locale.US, it) } ?: ""))
            }
            if (result.bestGuess.isNotEmpty()) text("Google best guesses (not verified): ${result.bestGuess.joinToString()}")
            if (result.matchingPages.isNotEmpty()) text("Related pages (unverified):\n${result.matchingPages.joinToString("\n")}")
        }
        text("Responses are session-only. We never write the API key, result, or photo to JSON exports. A candidate does not prove the manufacturer, machine model or mechanics.", 13f)
        button("Back to OCR harness") { finish() }
        scroll.post { scroll.scrollTo(0, y) }
    }
}
