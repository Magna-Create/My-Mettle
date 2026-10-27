package dev.kian.mymettle.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Only equipment/profile or exercise switches that already exist are actionable. */
internal enum class WorkoutSwapChoice {
    SWITCH_EQUIPMENT,
    ADD_EQUIPMENT,
    SUBSTITUTE_EXERCISE,
    CANCEL,
}

internal data class WorkoutSwapRelease(
    val exerciseId: String,
    val positionRoot: Offset,
    val velocity: Offset,
    val generation: Int,
)

/** Keeps one original pointer stream alive as the menu appears over a scrolling exercise card. */
@Stable
internal class WorkoutSwapGestureState {
    var exerciseId by mutableStateOf<String?>(null)
        private set
    var dockRoot by mutableStateOf(Offset.Zero)
        private set
    var handleRoot by mutableStateOf(Offset.Zero)
        private set
    var dragging by mutableStateOf(false)
        private set
    var release by mutableStateOf<WorkoutSwapRelease?>(null)
        private set
    private var generation by mutableIntStateOf(0)

    fun open(id: String, dock: Offset) {
        exerciseId = id
        dockRoot = dock
        handleRoot = dock
        dragging = false
        release = null
    }

    fun begin(id: String, dock: Offset) {
        open(id, dock)
        dragging = true
    }

    fun dragBy(delta: Offset) {
        if (dragging) handleRoot += delta
    }

    fun finish(velocity: Offset) {
        val id = exerciseId ?: return
        if (!dragging) return
        dragging = false
        generation++
        release = WorkoutSwapRelease(id, handleRoot, velocity, generation)
    }

    fun cancel() {
        dragging = false
        dismiss()
    }

    fun dismiss() {
        exerciseId = null
        dragging = false
        release = null
    }
}

private data class SwapTarget(
    val choice: WorkoutSwapChoice,
    val x: Float,
    val y: Float,
    val size: Float,
    val icon: ImageVector,
)

/** Uses the Figma 453 × 983 arrangement, scaled to the available screen viewport. */
internal fun resolveWorkoutSwapChoice(
    position: Offset,
    velocity: Offset,
    width: Float,
    height: Float,
): WorkoutSwapChoice? {
    if (width <= 0f || height <= 0f) return null
    val targets = listOf(
        Triple(WorkoutSwapChoice.SWITCH_EQUIPMENT, Offset(154f, 526f), 120f),
        Triple(WorkoutSwapChoice.ADD_EQUIPMENT, Offset(298f, 526f), 120f),
        Triple(WorkoutSwapChoice.SUBSTITUTE_EXERCISE, Offset(89f, 653f), 120f),
        Triple(WorkoutSwapChoice.CANCEL, Offset(363f, 653f), 120f),
    )
    fun hit(point: Offset, radius: Float): WorkoutSwapChoice? {
        val figmaPoint = Offset(point.x / width * 453f, point.y / height * 983f)
        return targets.minByOrNull { (_, centre, _) ->
            hypot(figmaPoint.x - centre.x, figmaPoint.y - centre.y)
        }?.takeIf { (_, centre, size) ->
            hypot(figmaPoint.x - centre.x, figmaPoint.y - centre.y) <= size * radius
        }?.first
    }
    // An accurately placed release wins. A flick can complete slightly short of a target.
    hit(position, .55f)?.let { return it }
    val velocityProjection = velocity * .10f
    val length = hypot(velocityProjection.x, velocityProjection.y)
    val capped = if (length > 160f && length > 0f) velocityProjection * (160f / length) else velocityProjection
    return hit(position + capped, .70f)
}

@Composable
internal fun WorkoutSwapGestureOverlay(
    gesture: WorkoutSwapGestureState,
    onChoose: (exerciseId: String, choice: WorkoutSwapChoice) -> Unit,
) {
    val activeExerciseId = gesture.exerciseId ?: return
    val view = LocalView.current
    var originRoot by remember { mutableStateOf(Offset.Zero) }
    var widthPx by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }
    val activeCyan = Color(0xFFBBEBED)
    val paper = Color(0xFFE1E4DA)
    val field = Color(0xFF405D5C)

    val targets = remember {
        listOf(
            SwapTarget(WorkoutSwapChoice.SWITCH_EQUIPMENT, 154f, 526f, 120f, MettleIcons.CardsStack),
            SwapTarget(WorkoutSwapChoice.ADD_EQUIPMENT, 298f, 526f, 120f, MettleIcons.AddChart),
            SwapTarget(WorkoutSwapChoice.SUBSTITUTE_EXERCISE, 89f, 653f, 120f, MettleIcons.SwapHoriz),
            SwapTarget(WorkoutSwapChoice.CANCEL, 363f, 653f, 120f, MettleIcons.Close),
        )
    }
    val handleLocal = gesture.handleRoot - originRoot
    val hit = resolveWorkoutSwapChoice(handleLocal, Offset.Zero, widthPx, heightPx)
    val (title, subtitle) = when (hit) {
        WorkoutSwapChoice.SWITCH_EQUIPMENT -> "Switch" to "Switch equipment"
        WorkoutSwapChoice.ADD_EQUIPMENT -> "Switch" to "Add equipment"
        WorkoutSwapChoice.SUBSTITUTE_EXERCISE -> "Exercise" to "Substitute exercise"
        WorkoutSwapChoice.CANCEL -> "Cancel" to "Return to workout"
        null -> "Switch" to "Drag the handle"
    }
    val glow by animateFloatAsState(
        targetValue = if (hit != null) 1f else 0f,
        animationSpec = spring(stiffness = 360f),
        label = "swap-gesture-glow",
    )
    var lastHapticChoice by remember { mutableStateOf<WorkoutSwapChoice?>(null) }
    LaunchedEffect(hit) {
        if (hit != null && hit != lastHapticChoice) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        lastHapticChoice = hit
    }
    LaunchedEffect(gesture.release?.generation) {
        val release = gesture.release ?: return@LaunchedEffect
        if (widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
        val selected = resolveWorkoutSwapChoice(
            release.positionRoot - originRoot, release.velocity, widthPx, heightPx,
        )
        gesture.dismiss()
        if (selected != null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onChoose(release.exerciseId, selected)
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned {
            originRoot = it.localToRoot(Offset.Zero)
            widthPx = it.size.width.toFloat()
            heightPx = it.size.height.toFloat()
        },
    ) {
        // The app header is already rendered beneath this overlay. Dim rather than replacing it.
        Box(
            Modifier.fillMaxSize().padding(top = maxHeight * .122f)
                .background(Color(0xFF101C19).copy(alpha = .97f)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(field.copy(alpha = .96f), field.copy(alpha = .46f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * .90f),
                        radius = size.height * .77f,
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = .56f), Color.Transparent),
                        endY = size.height * .34f,
                    ),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = maxHeight * .195f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = activeCyan, fontSize = 43.sp, lineHeight = 50.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = paper, fontSize = 20.sp, textAlign = TextAlign.Center)
        }

        // The four large targets are separately tappable for accessibility and precise selection.
        targets.forEach { target ->
            val xPx = target.x / 453f * widthPx
            val yPx = target.y / 983f * heightPx
            val targetPx = target.size / 453f * widthPx
            val targetDp = with(androidx.compose.ui.platform.LocalDensity.current) { targetPx.toDp() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (xPx - targetPx / 2f).roundToInt(),
                            (yPx - targetPx / 2f).roundToInt(),
                        )
                    }
                    .size(targetDp),
                contentAlignment = Alignment.Center,
            ) {
                MettleControlGlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    baseColor = field.copy(alpha = .58f),
                    tint = activeCyan.copy(alpha = if (hit == target.choice) .24f else .04f),
                    borderColor = activeCyan.copy(alpha = if (hit == target.choice) .68f else .16f),
                    onClick = {
                        gesture.dismiss()
                        onChoose(activeExerciseId, target.choice)
                    },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = target.icon,
                            contentDescription = when (target.choice) {
                                WorkoutSwapChoice.SWITCH_EQUIPMENT -> "Switch equipment"
                                WorkoutSwapChoice.ADD_EQUIPMENT -> "Add equipment"
                                WorkoutSwapChoice.SUBSTITUTE_EXERCISE -> "Substitute exercise"
                                WorkoutSwapChoice.CANCEL -> "Cancel"
                            },
                            tint = activeCyan,
                            modifier = Modifier.size(targetDp * .45f),
                        )
                    }
                }
            }
        }

        // Track the actual pressed control for one continuous hold–drag–release interaction.
        val handlePx = 57f / 453f * widthPx
        val handleDp = with(androidx.compose.ui.platform.LocalDensity.current) { handlePx.toDp() }
        Box(
            Modifier.offset {
                IntOffset((handleLocal.x - handlePx / 2).roundToInt(), (handleLocal.y - handlePx / 2).roundToInt())
            }.size(handleDp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(activeCyan.copy(alpha = .20f + glow * .35f), field.copy(alpha = .65f))),
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MettleIcons.Cycle, contentDescription = "Dragged switch handle", tint = paper, modifier = Modifier.size(handleDp * .55f))
            }
        }
    }
}
