package dev.kian.mymettle.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.kian.mymettle.domain.exercise.Exercise
import dev.kian.mymettle.domain.exercise.ExerciseSetupMedia
import dev.kian.mymettle.library.RoutineBoardDay
import dev.kian.mymettle.library.RoutineBoardSlot
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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Routine, exercises and setup",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF10150F), Color(0xFF132018), Color(0xFF10140F)),
                    ),
                ),
        ) {
            if (state.loading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                Column(Modifier.fillMaxSize().padding(innerPadding)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MettleGlassChoiceChip(
                            selected = state.section == LibrarySection.ROUTINE,
                            onClick = { viewModel.selectSection(LibrarySection.ROUTINE) },
                            label = { Text("Routine") },
                        )
                        MettleGlassChoiceChip(
                            selected = state.section == LibrarySection.EXERCISES,
                            onClick = { viewModel.selectSection(LibrarySection.EXERCISES) },
                            label = { Text("Exercises") },
                        )
                    }
                    if (state.section == LibrarySection.ROUTINE) {
                        RoutineBoardContent(
                            state = state,
                            onBeginEdit = viewModel::beginRoutineEdit,
                            onCancelEdit = viewModel::cancelRoutineEdit,
                            onSaveEdit = viewModel::saveRoutineEdit,
                            onMove = viewModel::moveRoutineSlot,
                        )
                    } else {
                        ExerciseCatalogueContent(
                            state = state,
                            onQueryChange = viewModel::setQuery,
                            onSelect = viewModel::select,
                        )
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
private fun ExerciseCatalogueContent(
    state: ExerciseLibraryUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (Exercise?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
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
                LibraryExerciseCard(item = item, onClick = { onSelect(item) })
            }
        }
    }
}

@Composable
private fun RoutineBoardContent(
    state: ExerciseLibraryUiState,
    onBeginEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onMove: (String, Int) -> Unit,
) {
    val board = state.routine
    val editing = state.routineDraft != null
    val days = state.routineDraft?.days ?: board?.days.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (editing) "Editing routine" else "Current routine",
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Version ${board?.version ?: "—"}${if (editing) " · draft" else ""}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!editing) {
                    MettleGlassActionButton(onClick = onBeginEdit) { Text("Reorder") }
                }
            }
        }
        if (editing) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MettleGlassActionButton(
                        onClick = onCancelEdit,
                        accent = false,
                        modifier = Modifier.weight(1f),
                    ) { Text("Cancel") }
                    MettleGlassActionButton(
                        onClick = onSaveEdit,
                        enabled = !state.savingRoutine,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.savingRoutine) "Saving…" else "Save version") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "This first Native editor pass reorders within each day. Cross-day movement follows with N-Bio target projection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (board == null) {
            item { Text("No active routine is available.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(days, key = { it.symbol }) { day ->
                RoutineDayCard(day = day, editing = editing, onMove = onMove)
            }
        }
    }
}

@Composable
private fun RoutineDayCard(
    day: RoutineBoardDay,
    editing: Boolean,
    onMove: (String, Int) -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val cardHaze = rememberHazeState()
    Box(modifier = Modifier.fillMaxWidth().clip(shape)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .hazeSource(cardHaze)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF173C35), Color(0xFF15302B))),
                ),
        )
        CompositionLocalProvider(LocalMettleHazeState provides cardHaze) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(day.symbol, color = Color(0xFFC3EFAD), fontSize = 30.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(dayLabel(day.symbol), fontWeight = FontWeight.Medium)
                        Text(
                            "${day.slots.size} exercise${if (day.slots.size == 1) "" else "s"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (day.slots.isEmpty()) {
                    Text("No permanent slots.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                day.slots.forEachIndexed { index, slot ->
                    RoutineSlotRow(
                        slot = slot,
                        canMoveUp = index > 0,
                        canMoveDown = index < day.slots.lastIndex,
                        editing = editing,
                        onMove = onMove,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineSlotRow(
    slot: RoutineBoardSlot,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    editing: Boolean,
    onMove: (String, Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0E211F).copy(alpha = .72f))
            .padding(start = 14.dp, top = 11.dp, bottom = 11.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(slot.exerciseName, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(
                "${slot.importance.replaceFirstChar { it.uppercase() }} · ${slot.preferredSets}×${slot.repMin}–${slot.repMax} · ${slot.restSeconds}s",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (editing) {
            RoutineMoveButton("↑", canMoveUp) { onMove(slot.id, -1) }
            RoutineMoveButton("↓", canMoveDown) { onMove(slot.id, 1) }
        }
    }
}

@Composable
private fun RoutineMoveButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    MettleControlGlassSurface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        enabled = enabled,
        tint = Color.White.copy(alpha = if (enabled) .035f else .012f),
        borderColor = Color.White.copy(alpha = if (enabled) .24f else .08f),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, textAlign = TextAlign.Center, color = Color.White.copy(alpha = if (enabled) .9f else .28f))
        }
    }
}

private fun dayLabel(symbol: String): String = when (symbol) {
    "ψ" -> "Foundation"
    "φ" -> "Development"
    "π" -> "Performance"
    "&" -> "Conditional catch-up"
    else -> "Programme day"
}

@Composable
private fun LibraryExerciseCard(item: Exercise, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val localSurface = rememberHazeState()
    Box(Modifier.fillMaxWidth().clip(shape)) {
        Box(
            Modifier
                .matchParentSize()
                .hazeSource(localSurface)
                .background(Brush.verticalGradient(listOf(Color(0xFF183832), Color(0xFF132824)))),
        )
        CompositionLocalProvider(LocalMettleHazeState provides localSurface) {
            MettleControlGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                tint = Color.White.copy(alpha = .026f),
                borderColor = Color.White.copy(alpha = .16f),
                onClick = onClick,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val meta = listOfNotNull(
                        item.memory?.category?.takeIf { it.isNotBlank() },
                        item.memory?.equipment?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    MettleGlassChoiceChip(selected = section == "setup", onClick = { section = "setup" }, label = { Text("Setup") })
                    MettleGlassChoiceChip(selected = section == "info", onClick = { section = "info" }, label = { Text("Info") })
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
                        MettleGlassActionButton(
                            onClick = onTakePhoto,
                            enabled = !savingMedia && item.setupMedia.size < 12,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (savingMedia) "Processing…" else "Take photo")
                        }
                        MettleGlassActionButton(accent = false, 
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
                        append(item.tracking.metric.storageValue.replace('_', ' '))
                        append(" · ")
                        append(item.tracking.defaultUnit)
                        increment?.let {
                            append(" · ")
                            append(formatLibraryNumber(it))
                            append(' ')
                            append(item.tracking.defaultUnit)
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
                        MettleGlassActionButton(
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
                MettleGlassActionButton(
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
