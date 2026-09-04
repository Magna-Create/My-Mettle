package dev.kian.mymettle

import android.app.Application
import dev.kian.mymettle.ai.LabAiRuntime

class MyMettleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LabAiRuntime.onProcessStart()
    }
}
