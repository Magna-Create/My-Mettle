package dev.kian.mymettle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.kian.mymettle.ui.MyMettleApp
import dev.kian.mymettle.ui.theme.MyMettleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // My Mettle is interaction-heavy during training. Ask Android for a 120 Hz window and let
        // the platform select the closest supported/seamless display mode. This is a preference,
        // not a forced mode, so system power/thermal/display policy remains authoritative.
        window.attributes = window.attributes.apply {
            preferredRefreshRate = 120f
        }

        setContent {
            MyMettleTheme {
                MyMettleApp()
            }
        }
    }
}
