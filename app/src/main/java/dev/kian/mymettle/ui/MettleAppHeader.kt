package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

/** Shared destination header for Native screens outside the fixed Figma workout viewport. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MettleAppHeader(
    destination: String,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    "My Mettle",
                    color = MettleOnPrimaryContainer,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                )
                Text(
                    destination,
                    color = MettleOnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        actions = {
            MettleControlGlassSurface(
                modifier = Modifier.width(96.dp).height(52.dp),
                tint = MettleOnPrimaryContainer.copy(alpha = .075f),
                borderColor = Color.White.copy(alpha = .20f),
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MettleGlassIconTouchTarget(
                        modifier = Modifier.size(width = 48.dp, height = 52.dp),
                        imageVector = MettleIcons.Settings,
                        contentDescription = "Settings",
                        onClick = onOpenSettings,
                        iconSize = DpSize(17.dp, 17.dp),
                    )
                    MettleGlassIconTouchTarget(
                        modifier = Modifier.size(width = 48.dp, height = 52.dp),
                        imageVector = MettleIcons.AccountCircle,
                        contentDescription = "Account",
                        onClick = onOpenAccount,
                        iconSize = DpSize(17.dp, 17.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}
