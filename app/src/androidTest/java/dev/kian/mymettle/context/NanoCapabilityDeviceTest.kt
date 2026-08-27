package dev.kian.mymettle.context

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device diagnostic check. Availability is not the pass criterion: truthful detection is.
 * CI compiles this APK; run it on the target device when instrumentation execution is available.
 */
@RunWith(AndroidJUnit4::class)
class NanoCapabilityDeviceTest {
    @Test
    fun reportsInstalledPromptCapabilitiesWithoutAssumingNanoAvailability() = runBlocking {
        val capabilities = NanoNoteInterpreter().capabilities()

        assertNotEquals(PromptApiStatus.NOT_CHECKED, capabilities.promptApiStatus)
        if (capabilities.strictExtractionAvailable) {
            assertEquals(PromptApiStatus.AVAILABLE, capabilities.promptApiStatus)
            assertTrue(capabilities.structuredOutputAvailable == true)
        }
    }
}
