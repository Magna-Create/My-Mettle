package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kian.mymettle.ui.theme.MettleOnPrimaryContainer
import dev.kian.mymettle.ui.theme.MettleOnSurface
import dev.kian.mymettle.ui.theme.MettleOnSurfaceVariant

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onProfileSaved: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: AccountViewModel = viewModel(
        factory = remember(context) { AccountViewModelFactory(context) },
    )
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 21.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("My Mettle", color = MettleOnPrimaryContainer, fontSize = 24.sp, lineHeight = 30.sp)
                Text("Account", color = MettleOnSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            MettleControlGlassSurface(
                modifier = Modifier.size(52.dp),
                onClick = onBack,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(MettleIcons.Close, contentDescription = "Close account", modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(42.dp))
        Text("Your measurements", color = MettleOnSurface, fontSize = 34.sp, lineHeight = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Weight informs bodyweight snapshots and the Daily Brief. New entries preserve measurement history.",
            color = MettleOnSurfaceVariant,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(28.dp))

        if (state.loading) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MettleOnPrimaryContainer)
            }
        } else {
            ProfileNumberField(
                label = "Weight",
                suffix = "kg",
                value = state.weightKg,
                onValueChange = viewModel::setWeight,
            )
            Spacer(Modifier.height(14.dp))
            ProfileNumberField(
                label = "Height",
                suffix = "cm",
                value = state.heightCm,
                onValueChange = viewModel::setHeight,
                optional = true,
            )
            Spacer(Modifier.height(22.dp))
            MettleGlassActionButton(
                onClick = { viewModel.save(onProfileSaved) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                containerTint = MettleOnPrimaryContainer.copy(alpha = .09f),
                outlineColor = MettleOnPrimaryContainer.copy(alpha = .40f),
                foregroundColor = MettleOnSurface,
            ) {
                Text(
                    when {
                        state.saving -> "Saving…"
                        state.saved -> "Saved"
                        else -> "Save measurements"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Check your measurements") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun ProfileNumberField(
    label: String,
    suffix: String,
    value: String,
    onValueChange: (String) -> Unit,
    optional: Boolean = false,
) {
    MettleControlGlassSurface(
        modifier = Modifier.fillMaxWidth().height(82.dp),
        shape = RoundedCornerShape(24.dp),
        tint = Color.White.copy(alpha = .035f),
        borderColor = MettleOnPrimaryContainer.copy(alpha = .23f),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (optional) "$label · optional" else label,
                    color = MettleOnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = MettleOnSurface,
                        fontSize = 25.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (value.isBlank()) Text("—", color = MettleOnSurfaceVariant, fontSize = 25.sp)
                            inner()
                        }
                    },
                )
            }
            Text(suffix, color = MettleOnSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
    }
}
