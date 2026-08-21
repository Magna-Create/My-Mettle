package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kian.mymettle.ui.theme.MettleOnPrimaryContainer
import dev.kian.mymettle.ui.theme.MettleOnSurfaceVariant

/**
 * Fixed destination header for Native screens outside the Workout viewport.
 *
 * The geometry intentionally mirrors WorkoutHeader: status-bar safe, 70 dp tall, left-aligned
 * two-line identity and one locally sampled action capsule. It does not inherit Material's
 * TopAppBar content insets, which were making these destinations sit differently from Workout.
 */
@Composable
internal fun MettleAppHeader(
    destination: String,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(70.dp)
            .padding(start = 21.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "My Mettle",
                color = MettleOnPrimaryContainer,
                fontSize = 24.2.sp,
                lineHeight = 31.sp,
            )
            Text(
                destination,
                color = MettleOnSurfaceVariant,
                fontSize = 13.2.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        MettleControlGlassSurface(
            modifier = Modifier.width(96.dp).height(49.dp),
            tint = MettleOnPrimaryContainer.copy(alpha = .055f),
            borderColor = Color.White.copy(alpha = .10f),
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MettleGlassIconTouchTarget(
                    modifier = Modifier.size(width = 48.dp, height = 49.dp),
                    imageVector = MettleIcons.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                    iconSize = DpSize(17.dp, 17.dp),
                )
                MettleGlassIconTouchTarget(
                    modifier = Modifier.size(width = 48.dp, height = 49.dp),
                    imageVector = MettleIcons.AccountCircle,
                    contentDescription = "Account",
                    onClick = onOpenAccount,
                    iconSize = DpSize(17.dp, 17.dp),
                )
            }
        }
    }
}
