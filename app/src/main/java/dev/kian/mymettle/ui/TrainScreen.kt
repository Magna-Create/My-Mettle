package dev.kian.mymettle.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.PerformanceSetRecord
import dev.kian.mymettle.workout.evaluateLoadExpression

/** Mutable UI buffer only. Canonical history is created as immutable observations on log. */
internal class TrainSetDraft(
    set: PerformanceSetRecord,
    exercise: ActiveWorkoutExercise,
    initialLaterality: Laterality? = null,
) {
    private val values = mutableStateMapOf<PerformanceMetric, String>().apply {
        exercise.schema.metrics.forEach { definition ->
            this[definition.metric] = set.enteredValue(definition.metric, initialLaterality)?.let(::formatDecimal).orEmpty()
        }
    }
    private val units = exercise.schema.metrics.associate { definition ->
        definition.metric to (set.enteredUnit(definition.metric, initialLaterality) ?: definition.defaultUnit)
    }

    var laterality by mutableStateOf(
        initialLaterality
            ?: set.latestObservation()?.laterality
            ?: exercise.prescription.setPrescriptions.firstOrNull { it.index == set.setIndex }?.laterality
            ?: when (exercise.lateralityMode) {
                LateralityMode.BILATERAL_ONLY -> Laterality.BILATERAL
                LateralityMode.ALTERNATING_ALLOWED -> Laterality.ALTERNATING
                LateralityMode.NOT_APPLICABLE -> Laterality.NOT_APPLICABLE
                LateralityMode.UNILATERAL, LateralityMode.UNKNOWN -> Laterality.UNKNOWN
            },
    )

    fun value(metric: PerformanceMetric): String = values[metric].orEmpty()

    fun unit(metric: PerformanceMetric): UnitId = units.getValue(metric)

    fun update(metric: PerformanceMetric, value: String) {
        values[metric] = value
    }
}

internal fun workoutDraftKey(setId: String, laterality: Laterality? = null): String =
    if (laterality == null) setId else "$setId:${laterality.storageValue}"

private data class LoadCalculatorTarget(
    val exercise: ActiveWorkoutExercise,
    val set: PerformanceSetRecord,
    val metric: PerformanceMetric,
    val laterality: Laterality?,
)

/** Active-session renderer only. Session choice belongs exclusively to the intensity selector. */
@Composable
fun TrainScreen(
    viewModel: N2WorkoutViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
) {
    val state = viewModel.uiState
    if (state.workout == null) return

    val context = LocalContext.current
    val drafts = remember { mutableStateMapOf<String, TrainSetDraft>() }
    var calculatorTarget by remember { mutableStateOf<LoadCalculatorTarget?>(null) }
    var cameraExercise by remember { mutableStateOf<ActiveWorkoutExercise?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showCamera = granted && cameraExercise != null
        cameraPermissionDenied = !granted
    }

    fun openSetupCamera(exercise: ActiveWorkoutExercise) {
        cameraExercise = exercise
        when (context.checkSelfPermission(Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> showCamera = true
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun openExerciseLink(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return
        val normalised = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalised)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "No app can open that exercise link.", Toast.LENGTH_SHORT).show()
        }
    }

    FigmaWorkoutSessionV2(
        state = state,
        drafts = drafts,
        onOpenSettings = onOpenSettings,
        onOpenAccount = onOpenAccount,
        onOpenCalculator = { exercise, set, metric, laterality ->
            calculatorTarget = LoadCalculatorTarget(exercise, set, metric, laterality)
        },
        onSaveDraft = { exercise, set, draft ->
            persistDraft(viewModel, exercise, set, draft, logged = set.hasObservation(draft.laterality))
        },
        onLogSet = { exercise, set, draft ->
            persistDraft(viewModel, exercise, set, draft, logged = true)
        },
        onSwapExercise = viewModel::requestExerciseSwap,
        onSelectSwap = viewModel::swapExercise,
        onDismissSwap = viewModel::dismissExerciseSwap,
        onShowSets = viewModel::showWorkoutSets,
        onShowSetup = viewModel::showExerciseSetup,
        onAddSetupPhoto = ::openSetupCamera,
        onSaveSetupDetails = viewModel::saveWorkoutSetup,
        onOpenExerciseLink = ::openExerciseLink,
        onToggleExercise = viewModel::toggleExercise,
        onRateExercise = viewModel::rateExercise,
        onDismissSheet = viewModel::dismissWorkoutSheet,
        onShowDelete = viewModel::showDeleteConfirmation,
        onCompleteSession = { viewModel.completeSession() },
        onCompleteWithoutReview = { viewModel.completeSession(skipReview = true) },
        onDiscardSession = viewModel::discardActiveSession,
    )

    cameraExercise?.takeIf { showCamera }?.let { exercise ->
        SetupCameraOverlay(
            exerciseName = exercise.entity.exerciseNameSnapshot,
            onCaptured = { captureFile ->
                showCamera = false
                cameraExercise = null
                viewModel.addCapturedSetupPhoto(exercise, captureFile)
            },
            onDismiss = {
                showCamera = false
                cameraExercise = null
            },
        )
    }

    if (cameraPermissionDenied) {
        AlertDialog(
            onDismissRequest = { cameraPermissionDenied = false },
            title = { Text("Camera permission needed") },
            text = { Text("My Mettle only opens the camera when you tap the setup-photo button.") },
            confirmButton = {
                TextButton(onClick = { cameraPermissionDenied = false }) { Text("OK") }
            },
        )
    }

    calculatorTarget?.let { target ->
        val key = workoutDraftKey(target.set.id, target.laterality)
        val draft = drafts.getOrPut(key) { TrainSetDraft(target.set, target.exercise, target.laterality) }
        LoadCalculatorDialog(
            initialValue = draft.value(target.metric),
            onDismiss = { calculatorTarget = null },
            onUseValue = { value ->
                draft.update(target.metric, formatDecimal(value))
                persistDraft(
                    viewModel,
                    target.exercise,
                    target.set,
                    draft,
                    logged = target.set.hasObservation(draft.laterality),
                )
                calculatorTarget = null
            },
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("My Mettle couldn’t do that") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun LoadCalculatorDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onUseValue: (Double) -> Unit,
) {
    var expression by remember(initialValue) { mutableStateOf(initialValue) }
    val evaluated = remember(expression) { runCatching { evaluateLoadExpression(expression) }.getOrNull() }
    val formattedResult = evaluated?.let(::formatDecimal)
    val showEvaluation = formattedResult != null && expression.trim() != formattedResult
    val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf(".", "0", "⌫", "+"),
        listOf("C", "Enter"),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        containerColor = Color(0xFF1E281B),
        tonalElevation = 0.dp,
        title = { Text("Load calculator", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                MettleControlGlassSurface(
                    modifier = Modifier.fillMaxWidth().height(82.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = .05f),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = .34f),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (showEvaluation) {
                            Text(
                                expression,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                maxLines = 1,
                            )
                        }
                        Text(
                            if (showEvaluation) formattedResult.orEmpty() else expression.ifBlank { "0" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 29.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            MettleGlassActionButton(
                                onClick = {
                                    if (key == "Enter") {
                                        evaluated?.let(onUseValue)
                                    } else expression = when (key) {
                                        "C" -> ""
                                        "⌫" -> expression.dropLast(1)
                                        "×" -> expression + "*"
                                        "÷" -> expression + "/"
                                        "−" -> expression + "-"
                                        else -> expression + key
                                    }
                                },
                                modifier = Modifier.weight(1f).height(52.dp),
                                contentPadding = PaddingValues(0.dp),
                                accent = key == "Enter",
                                enabled = key != "Enter" || evaluated != null,
                            ) {
                                if (key == "⌫") {
                                    Icon(MettleIcons.Backspace, contentDescription = "Backspace", modifier = Modifier.size(20.dp))
                                } else {
                                    Text(
                                        key,
                                        fontSize = if (key == "Enter") 12.sp else 17.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private fun persistDraft(
    viewModel: N2WorkoutViewModel,
    exercise: ActiveWorkoutExercise,
    set: PerformanceSetRecord,
    draft: TrainSetDraft,
    logged: Boolean,
) {
    if (exercise.entity.status == "completed") return
    // N-BIO-6 stores immutable side-addressed observations correctly, but active Room drafts are
    // intentionally still set+metric keyed. Keep unfinished unilateral input in Compose memory so
    // LEFT and RIGHT can never overwrite one another before either side becomes real evidence.
    if (!logged && exercise.lateralityMode == LateralityMode.UNILATERAL) return

    val values = exercise.schema.metrics.mapNotNull { definition ->
        draft.value(definition.metric).toDoubleOrNull()?.let { entered ->
            PerformanceMetricValue(definition.metric, Quantity(entered, draft.unit(definition.metric)))
        }
    }
    viewModel.saveSet(
        exercise = exercise,
        setId = set.id,
        load = null,
        reps = null,
        durationSeconds = null,
        distanceMetres = null,
        additionalValues = values,
        laterality = draft.laterality,
        logged = logged,
    )
}

internal fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().trimEnd('0').trimEnd('.')
