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
        setContent {
            MyMettleTheme {
                MyMettleApp()
            }
        }
    }
}
