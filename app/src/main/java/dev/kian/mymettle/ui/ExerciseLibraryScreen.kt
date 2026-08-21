package dev.kian.mymettle.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import kotlin.math.roundToInt

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
                            onPlace = viewModel::placeRoutineSlot,
                            onAddExercise = viewModel::addExerciseToRoutine,
                            onDuplicate = viewModel::duplicateRoutineSlot,
                            onRemove = viewModel::removeRoutineSlot,
                            onOpenExercise = viewModel::selectExercise,
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
            MettleExerciseSearchField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
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
    onPlace: (String, String, Int) -> Unit,
    onAddExercise: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onRemove: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val board = state.routine
    val editing = state.routineDraft != null
    val days = state.routineDraft?.days ?: board?.days.orEmpty()
    val listState = rememberLazyListState()
    val view = LocalView.current
    val density = LocalDensity.current
    val laneBounds = remember { mutableStateMapOf<String, Rect>() }
    val slotBounds = remember { mutableStateMapOf<String, Rect>() }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<RoutineDragState?>(null) }
    var menuSlotId by remember { mutableStateOf<String?>(null) }
    var addingToDay by remember { mutableStateOf<String?>(null) }

    fun updateDrag(delta: Offset) {
        val current = drag ?: return
        val pointer = current.pointerInWindow + delta
        val target = resolveRoutineDropTarget(pointer, current.slot.id, days, laneBounds, slotBounds)
        if (target != current.dropTarget && target != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        drag = current.copy(pointerInWindow = pointer, dropTarget = target)
    }

    fun finishDrag(cancelled: Boolean = false) {
        val finished = drag ?: return
        drag = null
        val target = finished.dropTarget
        if (!cancelled && target != null) {
            onPlace(finished.slot.id, target.daySymbol, target.index)
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    LaunchedEffect(drag?.pointerInWindow) {
        val pointer = drag?.pointerInWindow ?: return@LaunchedEffect
        if (rootBounds == Rect.Zero) return@LaunchedEffect
        val edge = with(density) { 76.dp.toPx() }
        when {
            pointer.y < rootBounds.top + edge -> listState.scrollBy(-22f)
            pointer.y > rootBounds.bottom - edge -> listState.scrollBy(22f)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBounds = it.boundsInWindow() },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                if (editing) {
                    RoutineEditingToolbar(
                        version = board?.version,
                        saving = state.savingRoutine,
                        onCancel = onCancelEdit,
                        onSave = onSaveEdit,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Routine v${board?.version ?: "—"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Your training week",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        MettleGlassActionButton(onClick = onBeginEdit) { Text("Edit routine") }
                    }
                }
            }
            if (board == null) {
                item { Text("No active routine is available.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(days, key = { it.symbol }) { day ->
                    RoutineDayCard(
                        day = day,
                        editing = editing,
                        drag = drag,
                        onLaneBounds = { laneBounds[day.symbol] = it },
                        onSlotBounds = { slotId, bounds -> slotBounds[slotId] = bounds },
                        onDragStart = { slot, pointer, cardBounds ->
                            drag = RoutineDragState(
                                slot = slot,
                                pointerInWindow = pointer,
                                grabOffset = pointer - cardBounds.topLeft,
                                cardSize = IntSize(cardBounds.width.roundToInt(), cardBounds.height.roundToInt()),
                                dropTarget = RoutineDropTarget(day.symbol, slot.position),
                            )
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        },
                        onDrag = ::updateDrag,
                        onDragEnd = { finishDrag() },
                        onDragCancel = { finishDrag(cancelled = true) },
                        onOpenMenu = { menuSlotId = it },
                        onOpenExercise = onOpenExercise,
                        onAddExercise = { addingToDay = day.symbol },
                    )
                }
            }
        }

        drag?.let { active ->
            val topLeft = active.pointerInWindow - active.grabOffset - rootBounds.topLeft
            RoutineDragGhost(
                slot = active.slot,
                modifier = Modifier
                    .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                    .then(
                        with(density) {
                            Modifier
                                .width(active.cardSize.width.toDp())
                                .height(active.cardSize.height.toDp())
                        },
                    )
                    .zIndex(20f),
            )
        }
    }

    menuSlotId?.let { slotId ->
        val slotDay = days.firstOrNull { day -> day.slots.any { it.id == slotId } }
        val slot = slotDay?.slots?.firstOrNull { it.id == slotId }
        if (slotDay != null && slot != null) {
            RoutinePlacementSheet(
                slot = slot,
                day = slotDay,
                allDays = days,
                onDismiss = { menuSlotId = null },
                onMove = { targetDay, index ->
                    onPlace(slotId, targetDay, index)
                    menuSlotId = null
                },
                onStep = { delta ->
                    onMove(slotId, delta)
                    menuSlotId = null
                },
                onDuplicate = {
                    onDuplicate(slotId)
                    menuSlotId = null
                },
                onRemove = {
                    onRemove(slotId)
                    menuSlotId = null
                },
            )
        }
    }

    addingToDay?.let { daySymbol ->
        AddRoutineExerciseSheet(
            daySymbol = daySymbol,
            exercises = state.exercises,
            onDismiss = { addingToDay = null },
            onAdd = { exerciseId ->
                onAddExercise(exerciseId, daySymbol)
                addingToDay = null
            },
        )
    }
}

@Composable
private fun RoutineDayCard(
    day: RoutineBoardDay,
    editing: Boolean,
    drag: RoutineDragState?,
    onLaneBounds: (Rect) -> Unit,
    onSlotBounds: (String, Rect) -> Unit,
    onDragStart: (RoutineBoardSlot, Offset, Rect) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onOpenMenu: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onAddExercise: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val cardHaze = rememberHazeState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .dropShadow(
                shape,
                Shadow(
                    radius = 18.dp,
                    color = Color.Black.copy(alpha = .24f),
                    offset = DpOffset(0.dp, 7.dp),
                ),
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF173C35), Color(0xFF15302B))))
            .hazeSource(cardHaze)
            .onGloballyPositioned { onLaneBounds(it.boundsInWindow()) },
    ) {
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
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (editing) "Drop an exercise here" else "No exercises",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                day.slots.forEachIndexed { index, slot ->
                    val previewSlots = day.slots.filterNot { it.id == drag?.slot?.id }
                    val previewIndex = previewSlots.indexOfFirst { it.id == slot.id }
                    if (editing && drag?.dropTarget == RoutineDropTarget(day.symbol, previewIndex)) {
                        RoutineInsertionMarker()
                    }
                    RoutineSlotRow(
                        slot = slot,
                        editing = editing,
                        dragging = drag?.slot?.id == slot.id,
                        onBounds = { onSlotBounds(slot.id, it) },
                        onDragStart = { pointer, bounds -> onDragStart(slot, pointer, bounds) },
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        onOpenMenu = { onOpenMenu(slot.id) },
                        onOpenExercise = { onOpenExercise(slot.exerciseId) },
                    )
                }
                val previewCount = day.slots.count { it.id != drag?.slot?.id }
                if (editing && drag?.dropTarget == RoutineDropTarget(day.symbol, previewCount)) {
                    RoutineInsertionMarker()
                }
                if (editing) {
                    MettleGlassActionButton(
                        onClick = onAddExercise,
                        modifier = Modifier.fillMaxWidth(),
                        accent = false,
                    ) { Text("+  Add exercise") }
                }
            }
        }
    }
}

@Composable
private fun RoutineSlotRow(
    slot: RoutineBoardSlot,
    editing: Boolean,
    dragging: Boolean,
    onBounds: (Rect) -> Unit,
    onDragStart: (Offset, Rect) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenExercise: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val localSurface = rememberHazeState()
    var cardBounds by remember(slot.id) { mutableStateOf(Rect.Zero) }
    var handleOrigin by remember(slot.id) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (dragging) .12f else 1f }
            .dropShadow(
                shape,
                Shadow(
                    radius = 11.dp,
                    color = Color.Black.copy(alpha = .22f),
                    offset = DpOffset(0.dp, 4.dp),
                ),
            )
            .clip(shape)
            .background(Color(0xE6162D2A))
            .hazeSource(localSurface)
            .onGloballyPositioned {
                cardBounds = it.boundsInWindow()
                onBounds(cardBounds)
            }
            .then(if (!editing) Modifier.clickable(onClick = onOpenExercise) else Modifier),
    ) {
        CompositionLocalProvider(LocalMettleHazeState provides localSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .padding(start = 14.dp, end = 8.dp),
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
                    RoutineSmallGlassControl("•••", "Edit placement", onOpenMenu)
                    MettleControlGlassSurface(
                        modifier = Modifier
                            .size(width = 48.dp, height = 56.dp)
                            .onGloballyPositioned { handleOrigin = it.boundsInWindow().topLeft }
                            .pointerInput(slot.id, cardBounds) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { local -> onDragStart(handleOrigin + local, cardBounds) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        onDrag(amount)
                                    },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                )
                            },
                        shape = RoundedCornerShape(16.dp),
                        tint = Color.White.copy(alpha = .035f),
                        borderColor = Color.White.copy(alpha = .20f),
                        shadowElevation = 3.dp,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("≡", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    RoutineSmallGlassControl("→", "Open exercise details", onOpenExercise)
                }
            }
        }
    }
}

@Composable
private fun RoutineSmallGlassControl(label: String, description: String, onClick: () -> Unit) {
    MettleControlGlassSurface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        tint = Color.White.copy(alpha = .035f),
        borderColor = Color.White.copy(alpha = .18f),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, textAlign = TextAlign.Center, color = Color.White.copy(alpha = .88f))
        }
    }
}

private data class RoutineDropTarget(val daySymbol: String, val index: Int)

private data class RoutineDragState(
    val slot: RoutineBoardSlot,
    val pointerInWindow: Offset,
    val grabOffset: Offset,
    val cardSize: IntSize,
    val dropTarget: RoutineDropTarget?,
)

private fun resolveRoutineDropTarget(
    pointer: Offset,
    draggedSlotId: String,
    days: List<RoutineBoardDay>,
    laneBounds: Map<String, Rect>,
    slotBounds: Map<String, Rect>,
): RoutineDropTarget? {
    val day = days.minByOrNull { candidate ->
        val bounds = laneBounds[candidate.symbol] ?: return@minByOrNull Float.MAX_VALUE
        when {
            pointer.y < bounds.top -> bounds.top - pointer.y
            pointer.y > bounds.bottom -> pointer.y - bounds.bottom
            else -> 0f
        }
    } ?: return null
    val preview = day.slots.filterNot { it.id == draggedSlotId }
    val index = preview.count { slot ->
        val bounds = slotBounds[slot.id]
        bounds != null && pointer.y > bounds.center.y
    }
    return RoutineDropTarget(day.symbol, index)
}

@Composable
private fun RoutineEditingToolbar(
    version: Int?,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ROUTINE V${version ?: "—"} · DRAFT", style = MaterialTheme.typography.labelSmall)
            Text("Editing routine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        MettleGlassActionButton(onClick = onSave, enabled = !saving) {
            Text(if (saving) "Saving…" else "Done")
        }
    }
}

@Composable
private fun RoutineInsertionMarker() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.primary, Color.Transparent),
                ),
                CircleShape,
            ),
    )
}

@Composable
private fun RoutineDragGhost(slot: RoutineBoardSlot, modifier: Modifier) {
    Row(
        modifier
            .graphicsLayer {
                rotationZ = -1.1f
                scaleX = 1.025f
                scaleY = 1.025f
            }
            .dropShadow(
                RoundedCornerShape(19.dp),
                Shadow(radius = 28.dp, color = Color.Black.copy(alpha = .38f), offset = DpOffset(0.dp, 12.dp)),
            )
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0xF222413D))
            .border(1.dp, Color.White.copy(alpha = .28f), RoundedCornerShape(19.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(slot.exerciseName, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(slot.importance.replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("≡", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutinePlacementSheet(
    slot: RoutineBoardSlot,
    day: RoutineBoardDay,
    allDays: List<RoutineBoardDay>,
    onDismiss: () -> Unit,
    onMove: (String, Int) -> Unit,
    onStep: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    val localSurface = rememberHazeState()
    val index = day.slots.indexOfFirst { it.id == slot.id }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF17211B),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF17211B))
                .hazeSource(localSurface)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompositionLocalProvider(LocalMettleHazeState provides localSurface) {
                Text("EDIT PLACEMENT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(slot.exerciseName, style = MaterialTheme.typography.headlineSmall)
                MettleGlassActionButton(
                    onClick = { onStep(-1) },
                    enabled = index > 0,
                    modifier = Modifier.fillMaxWidth(),
                    accent = false,
                ) { Text("Move up") }
                MettleGlassActionButton(
                    onClick = { onStep(1) },
                    enabled = index < day.slots.lastIndex,
                    modifier = Modifier.fillMaxWidth(),
                    accent = false,
                ) { Text("Move down") }
                allDays.filter { it.symbol != day.symbol }.forEach { target ->
                    MettleGlassActionButton(
                        onClick = { onMove(target.symbol, target.slots.size) },
                        modifier = Modifier.fillMaxWidth(),
                        accent = false,
                    ) { Text("Move to ${target.symbol}") }
                }
                MettleGlassActionButton(
                    onClick = onDuplicate,
                    modifier = Modifier.fillMaxWidth(),
                    accent = false,
                ) { Text("Duplicate") }
                MettleGlassActionButton(
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                    accent = false,
                    foregroundColor = Color(0xFFFFB4AB),
                ) { Text("Remove from routine") }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRoutineExerciseSheet(
    daySymbol: String,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val localSurface = rememberHazeState()
    var query by remember(daySymbol) { mutableStateOf("") }
    val visible = remember(exercises, query) {
        val needle = query.trim()
        if (needle.isEmpty()) exercises else exercises.filter { it.name.contains(needle, ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10150F),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(620.dp)
                .background(Color(0xFF10150F))
                .hazeSource(localSurface)
                .padding(horizontal = 16.dp),
        ) {
            CompositionLocalProvider(LocalMettleHazeState provides localSurface) {
                Text("ADD TO $daySymbol", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Choose an exercise", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(14.dp))
                MettleExerciseSearchField(value = query, onValueChange = { query = it.take(80) })
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id.value }) { exercise ->
                        LibraryExerciseCard(item = exercise, onClick = { onAdd(exercise.id.value) })
                    }
                }
            }
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
    val shape = RoundedCornerShape(25.dp)
    val localSurface = rememberHazeState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(114.dp)
            .dropShadow(
                shape,
                Shadow(
                    radius = 13.dp,
                    color = Color.Black.copy(alpha = .25f),
                    offset = DpOffset(0.dp, 5.dp),
                ),
            )
            .clip(shape)
            .background(Color(0xE61A3A37))
            .border(.7.dp, Color.White.copy(alpha = .10f), shape)
            .hazeSource(localSurface)
            .clickable(onClick = onClick),
    ) {
        CompositionLocalProvider(LocalMettleHazeState provides localSurface) {
            Row(
                Modifier.fillMaxSize().padding(start = 16.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    val profile = item.executionProfiles.firstOrNull { it.isDefault }
                    val quickMeta = profile?.name?.takeIf { it.isNotBlank() && !it.equals("default", ignoreCase = true) }
                        ?: item.memory?.equipment?.takeIf { it.isNotBlank() }
                        ?: "Default"
                    Text(quickMeta, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (item.setupMedia.isNotEmpty()) {
                        Text(
                            "${item.setupMedia.size} setup photo${if (item.setupMedia.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                MettleControlGlassSurface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = .055f),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = .38f),
                    shadowElevation = 4.dp,
                    onClick = onClick,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("→", color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp)
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
