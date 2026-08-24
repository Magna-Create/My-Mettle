package dev.kian.mymettle.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kian.mymettle.domain.exercise.Exercise
import dev.kian.mymettle.domain.exercise.ExerciseSetupMedia
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen() {
    val context = LocalContext.current
    val viewModel: ExerciseLibraryViewModel = viewModel(
        factory = remember(context) { ExerciseLibraryViewModelFactory(context) },
    )
    val state = viewModel.uiState
    var showCamera by remember { mutableStateOf(false) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    val addPhotosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        state.selected?.id?.value?.let { exerciseId ->
            viewModel.addSetupPhotos(exerciseId, uris)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showCamera = granted
        cameraPermissionDenied = !granted
    }

    fun openCamera() {
        when (context.checkSelfPermission(Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> showCamera = true
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Exercises, setup and memory",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search exercises") },
                        singleLine = true,
                    )
                }
                if (state.visibleExercises.isEmpty()) {
                    item {
                        Text(
                            if (state.query.isBlank()) "No exercises have been imported yet." else "No matching exercises.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.visibleExercises, key = { it.id.value }) { item ->
                        LibraryExerciseCard(item = item, onClick = { viewModel.select(item) })
                    }
                }
            }
        }
    }

    state.selected?.let { selected ->
        ExerciseDetailSheet(
            item = selected,
            savingMedia = state.savingMedia,
            onDismiss = { viewModel.select(null) },
            onTakePhoto = ::openCamera,
            onAddPhotos = { addPhotosLauncher.launch(arrayOf("image/*")) },
            onDeletePhoto = viewModel::deleteSetupPhoto,
        )

        if (showCamera) {
            SetupCameraOverlay(
                exerciseName = selected.name,
                onCaptured = { captureFile ->
                    showCamera = false
                    viewModel.addCapturedSetupPhoto(selected.id.value, captureFile)
                },
                onDismiss = { showCamera = false },
            )
        }
    }

    if (cameraPermissionDenied) {
        AlertDialog(
            onDismissRequest = { cameraPermissionDenied = false },
            title = { Text("Camera permission needed") },
            text = { Text("My Mettle only uses the camera when you explicitly open setup-photo capture. You can still add existing photos without granting it.") },
            confirmButton = { TextButton(onClick = { cameraPermissionDenied = false }) { Text("OK") } },
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Library couldn't update") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun LibraryExerciseCard(item: Exercise, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val meta = listOfNotNull(
                item.memory?.category?.takeIf { it.isNotBlank() },
                item.memory?.equipment?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val recruitedSegments = item.recruitmentSegmentNames()
            if (recruitedSegments.isNotEmpty()) {
                Text(recruitedSegments.joinToString(" · "), style = MaterialTheme.typography.labelLarge)
            }
            if (item.setupMedia.isNotEmpty()) {
                Text(
                    "${item.setupMedia.size} setup photo${if (item.setupMedia.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailSheet(
    item: Exercise,
    savingMedia: Boolean,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onAddPhotos: () -> Unit,
    onDeletePhoto: (String) -> Unit,
) {
    var section by remember(item.id) { mutableStateOf("setup") }
    var pendingDelete by remember(item.id) { mutableStateOf<ExerciseSetupMedia?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(item.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                val meta = listOfNotNull(
                    item.memory?.category?.takeIf { it.isNotBlank() },
                    item.memory?.equipment?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = section == "setup", onClick = { section = "setup" }, label = { Text("Setup") })
                    FilterChip(selected = section == "info", onClick = { section = "info" }, label = { Text("Info") })
                }
            }

            if (section == "setup") {
                item {
                    Text("Setup photos", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (item.setupMedia.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "No setup photos yet.",
                                modifier = Modifier.padding(18.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        SetupPhotoGallery(
                            media = item.setupMedia,
                            savingMedia = savingMedia,
                            onDelete = { pendingDelete = it },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onTakePhoto,
                            enabled = !savingMedia && item.setupMedia.size < 12,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (savingMedia) "Processing…" else "Take photo")
                        }
                        OutlinedButton(
                            onClick = onAddPhotos,
                            enabled = !savingMedia && item.setupMedia.size < 12,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (item.setupMedia.size >= 12) "12 max" else "Add existing")
                        }
                    }
                }

                item {
                    DetailTextBlock("Setup notes", item.memory?.setupNotes.orEmpty())
                    Spacer(Modifier.height(10.dp))
                    DetailTextBlock("Machine settings", item.memory?.machineSettings.orEmpty())
                }
            } else {
                item {
                    item.essentialCue?.takeIf { it.isNotBlank() }?.let {
                        DetailTextBlock("Essential cue", it)
                        Spacer(Modifier.height(12.dp))
                    }
                    val increment = item.executionProfiles.firstOrNull { it.isDefault }?.loadResolution?.increment
                    DetailTextBlock("Tracking", buildString {
                        append(item.tracking.metricFamily.storageValue.replace('_', ' '))
                        append(" · ")
                        append(item.tracking.defaultUnit.storageValue)
                        increment?.let {
                            append(" · ")
                            append(formatLibraryNumber(it))
                            append(' ')
                            append(item.tracking.defaultUnit.storageValue)
                            append(" load increment")
                        }
                    })
                }

                val recruitment = item.executionProfiles.firstOrNull { it.isDefault }
                    ?.recruitment?.allocations.orEmpty()
                if (recruitment.isNotEmpty()) {
                    item {
                        TextListBlock(
                            "Execution recruitment",
                            recruitment.map {
                                "${it.segmentName} · ${formatLibraryNumber(it.weighting * 100)}% · ${it.role.storageValue}"
                            },
                        )
                    }
                }
                if (item.memory?.cues.orEmpty().isNotEmpty()) {
                    item { TextListBlock("Cues", item.memory?.cues.orEmpty()) }
                }
                if (item.memory?.commonMistakes.orEmpty().isNotEmpty()) {
                    item { TextListBlock("Common mistakes", item.memory?.commonMistakes.orEmpty()) }
                }
                if (item.memory?.substitutions.orEmpty().isNotEmpty()) {
                    item { TextListBlock("Substitutions", item.memory?.substitutions.orEmpty()) }
                }
                item {
                    item.memory?.videoReferenceUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        val context = LocalContext.current
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open video reference") }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    pendingDelete?.let { media ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove setup photo?") },
            text = { Text("This removes the app-private copy from this exercise.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        onDeletePhoto(media.id)
                    },
                    enabled = !savingMedia,
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SetupPhotoGallery(
    media: List<ExerciseSetupMedia>,
    savingMedia: Boolean,
    onDelete: (ExerciseSetupMedia) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(media, key = { it.id }) { photo ->
            ElevatedCard {
                Column {
                    SetupPhotoImage(photo)
                    TextButton(
                        onClick = { onDelete(photo) },
                        enabled = !savingMedia,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun SetupPhotoImage(media: ExerciseSetupMedia) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, media.relativePath) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(File(context.filesDir, media.relativePath).absolutePath)?.asImageBitmap()
        }
    }
    val ratio = if (media.height > 0) media.width.toFloat() / media.height else 1f
    val width = (180f * ratio).coerceIn(120f, 280f).dp

    Box(
        modifier = Modifier.width(width).height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = image,
                contentDescription = "Exercise setup photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun DetailTextBlock(title: String, value: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            value.ifBlank { "—" },
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TextListBlock(title: String, values: List<String>) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        values.forEach { value ->
            Text("• $value", modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

private fun formatLibraryNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')

private fun Exercise.recruitmentSegmentNames(): List<String> = executionProfiles
    .firstOrNull { it.isDefault }
    ?.recruitment
    ?.allocations
    .orEmpty()
    .map { it.segmentName }
    .distinct()
