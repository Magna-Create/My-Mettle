package dev.kian.mymettle.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
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

/**
 * Figma 99:13817 and 99:13923. Coordinates are specified in the 453 × 983 design viewport,
 * but transformed to the actual Compose viewport. No blurred copy of the workout remains:
 * the modal area is an opaque surface, deliberately cheaper than multiple Haze captures.
 */
@Composable
internal fun WorkoutSwapGestureOverlay(
    gesture: WorkoutSwapGestureState,
    onChoose: (exerciseId: String, choice: WorkoutSwapChoice) -> Unit,
) {
    val activeExerciseId = gesture.exerciseId ?: return
    val view = LocalView.current
    val density = LocalDensity.current
    val cyan = Color(0xFFBBEBED)
    val paper = Color(0xFFE1E4DA)
    val targetFill = Color(0xFF4C6C6B)
    val targets = remember {
        listOf(
            SwapTarget(WorkoutSwapChoice.SWITCH_EQUIPMENT, 154f, 526f, 120f, FigmaWorkoutSwapIcons.SwitchEquipment),
            SwapTarget(WorkoutSwapChoice.ADD_EQUIPMENT, 298f, 526f, 120f, FigmaWorkoutSwapIcons.AddEquipment),
            SwapTarget(WorkoutSwapChoice.SUBSTITUTE_EXERCISE, 89f, 653f, 120f, FigmaWorkoutSwapIcons.SubstituteExercise),
            SwapTarget(WorkoutSwapChoice.CANCEL, 363f, 653f, 120f, FigmaWorkoutSwapIcons.Cancel),
        )
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reveal by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 190),
        label = "workout-swap-overlay-reveal",
    )
    var lastHapticChoice by remember { mutableStateOf<WorkoutSwapChoice?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = reveal }) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        fun x(ref: Float) = ref / 453f * widthPx
        fun y(ref: Float) = ref / 983f * heightPx
        fun dp(px: Float) = with(density) { px.toDp() }

        // A pressed footer control preserves its original pointer stream.
        // Display its handle at the designed anchor; only the finger's delta is transferred.
        val handle = Offset(
            x(227f) + if (gesture.dragging || gesture.release != null) gesture.handleRoot.x - gesture.dockRoot.x else 0f,
            y(759f) + if (gesture.dragging || gesture.release != null) gesture.handleRoot.y - gesture.dockRoot.y else 0f,
        )
        val hit = resolveWorkoutSwapChoice(handle, Offset.Zero, widthPx, heightPx)
        val title = when (hit) {
            WorkoutSwapChoice.SUBSTITUTE_EXERCISE -> "Exercise"
            WorkoutSwapChoice.CANCEL -> "Cancel"
            else -> "Switch"
        }
        val subtitle = when (hit) {
            WorkoutSwapChoice.SWITCH_EQUIPMENT -> "Switch equipment"
            WorkoutSwapChoice.ADD_EQUIPMENT -> "Add equipment"
            WorkoutSwapChoice.SUBSTITUTE_EXERCISE -> "Substitute exercise"
            WorkoutSwapChoice.CANCEL -> "Return to workout"
            null -> "Drag the handle"
        }
        val selectedGlow by animateFloatAsState(
            targetValue = if (hit != null) 1f else 0f,
            animationSpec = spring(stiffness = 360f),
            label = "workout-swap-target-focus",
        )
        LaunchedEffect(hit) {
            if (hit != null && hit != lastHapticChoice) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            lastHapticChoice = hit
        }
        LaunchedEffect(gesture.release?.generation) {
            val release = gesture.release ?: return@LaunchedEffect
            val location = Offset(
                x(227f) + release.positionRoot.x - gesture.dockRoot.x,
                y(759f) + release.positionRoot.y - gesture.dockRoot.y,
            )
            val choice = resolveWorkoutSwapChoice(location, release.velocity, widthPx, heightPx)
            if (choice != null) {
                gesture.dismiss()
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onChoose(release.exerciseId, choice)
            } else {
                // An inaccurate release must not accidentally apply a substitution.
                gesture.open(release.exerciseId, gesture.dockRoot)
            }
        }

        // Covers the entire exercise viewport below the fixed header and above the hotbar.
        // Unlike the first implementation, underlying set cards are fully obscured.
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = maxHeight * .122f, bottom = maxHeight * .091f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF101914), Color(0xFF1F3430), Color(0xFF344D4C)),
                    ),
                ),
        )
        // Independent design surface: x=17, y=129, w=418, h=678.
        Box(
            Modifier
                .offset { IntOffset(x(17f).roundToInt(), y(129f).roundToInt()) }
                .size(dp(x(418f)), dp(y(678f)))
                .clip(RoundedCornerShape(dp(x(24.7f))))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF14221D), Color(0xFF162925), Color(0xFF344F4E)),
                    ),
                )
                .border(
                    width = .55.dp,
                    color = cyan.copy(alpha = .06f),
                    shape = RoundedCornerShape(dp(x(24.7f))),
                ),
        )

        Column(
            Modifier
                .offset { IntOffset(0, y(222f).roundToInt()) }
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = cyan,
                fontSize = 43.sp,
                lineHeight = 49.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = paper,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
            )
        }

        targets.forEach { target ->
            val diameterPx = x(target.size)
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (x(target.x) - diameterPx / 2f).roundToInt(),
                            (y(target.y) - diameterPx / 2f).roundToInt(),
                        )
                    }
                    .size(dp(diameterPx))
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                targetFill.copy(alpha = if (hit == target.choice) .99f else .93f),
                                Color(0xFF456462),
                            ),
                        ),
                    )
                    .border(
                        width = if (hit == target.choice) 1.25.dp else .65.dp,
                        color = cyan.copy(alpha = if (hit == target.choice) .66f else .20f),
                        shape = CircleShape,
                    )
                    .clickable {
                        gesture.dismiss()
                        onChoose(activeExerciseId, target.choice)
                    },
                contentAlignment = Alignment.Center,
            ) {
                val glyphWidth = when (target.choice) {
                    WorkoutSwapChoice.SWITCH_EQUIPMENT, WorkoutSwapChoice.ADD_EQUIPMENT -> 65f
                    else -> 63f
                }
                Icon(
                    imageVector = target.icon,
                    contentDescription = when (target.choice) {
                        WorkoutSwapChoice.SWITCH_EQUIPMENT -> "Switch equipment"
                        WorkoutSwapChoice.ADD_EQUIPMENT -> "Add equipment"
                        WorkoutSwapChoice.SUBSTITUTE_EXERCISE -> "Substitute exercise"
                        WorkoutSwapChoice.CANCEL -> "Cancel"
                    },
                    tint = Color.Unspecified,
                    modifier = Modifier.size(dp(x(glyphWidth))),
                )
            }
        }

        // Figma's handle is a 78 × 56 capsule, never a circular avatar.
        val handleWidth = x(78f)
        val handleHeight = y(56f)
        val pill = RoundedCornerShape(percent = 50)
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (handle.x - handleWidth / 2f).roundToInt(),
                        (handle.y - handleHeight / 2f).roundToInt(),
                    )
                }
                .size(dp(handleWidth), dp(handleHeight))
                .clip(pill)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF456766).copy(alpha = .97f),
                            Color(0xFF719796).copy(alpha = .85f + selectedGlow * .12f),
                            Color(0xFF35514F),
                        ),
                    ),
                )
                .border(.85.dp, cyan.copy(alpha = .39f + selectedGlow * .28f), pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FigmaWorkoutSwapIcons.Cycle,
                contentDescription = "Switch gesture handle",
                tint = Color.Unspecified,
                modifier = Modifier.size(dp(x(24f))),
            )
        }
    }
}
