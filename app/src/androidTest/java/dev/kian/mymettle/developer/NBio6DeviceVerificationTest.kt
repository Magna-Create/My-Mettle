package dev.kian.mymettle.developer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NBio6DeviceVerificationTest {
    @Test
    fun developerAcceptanceHarnessPassesAgainstRealRoom() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = NBio6DeviceVerificationRepository(context).runAutomatedChecks()

        assertTrue(
            report.passed,
            report.checks.filterNot { it.passed }.joinToString("\n") { "${it.title}: ${it.detail}" },
        )
    }
}
