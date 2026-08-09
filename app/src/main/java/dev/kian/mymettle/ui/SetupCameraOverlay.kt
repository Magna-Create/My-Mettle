package dev.kian.mymettle.ui

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.UUID

@Composable
fun SetupCameraOverlay(
    exerciseName: String,
    onCaptured: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(controller, lifecycleOwner) {
        runCatching { controller.bindToLifecycle(lifecycleOwner) }
            .onFailure { error = it.message ?: "Camera could not start." }
        onDispose { controller.unbind() }
    }

    Dialog(
        onDismissRequest = { if (!capturing) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { previewContext ->
                        PreviewView(previewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                            this.controller = controller
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.46f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Setup photo", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(exerciseName, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f))
                    }
                    TextButton(onClick = onDismiss, enabled = !capturing) {
                        Text("Cancel", color = Color.White)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.52f))
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.errorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            capturing = true
                            error = null
                            captureSetupPhoto(
                                context = context,
                                controller = controller,
                                onSaved = { file ->
                                    capturing = false
                                    onCaptured(file)
                                },
                                onError = { message ->
                                    capturing = false
                                    error = message
                                },
                            )
                        },
                        enabled = !capturing && error == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (capturing) "Capturing…" else "Capture setup photo")
                    }
                }
            }
        }
    }
}

private fun captureSetupPhoto(
    context: Context,
    controller: LifecycleCameraController,
    onSaved: (File) -> Unit,
    onError: (String) -> Unit,
) {
    val directory = File(context.cacheDir, "setup-camera").apply { mkdirs() }
    val output = File(directory, "capture-${UUID.randomUUID()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(output).build()

    runCatching {
        controller.takePicture(
            options,
            context.mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSaved(output)
                }

                override fun onError(exception: ImageCaptureException) {
                    output.delete()
                    onError(exception.message ?: "Camera could not save the photo.")
                }
            },
        )
    }.onFailure { throwable ->
        output.delete()
        onError(throwable.message ?: "Camera could not take the photo.")
    }
}
