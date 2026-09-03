package dev.kian.mymettle.developer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NBio7ESyntheticValidationTest {
    @Test
    fun `all preregistered temporal and module cases pass`() {
        val report = NBio7ESyntheticValidation.run()
        assertEquals(17, report.temporalCases.size)
        assertEquals(25, report.contextModuleCases.size)
        assertTrue(report.futureDataLeakageGuardPassed)
        assertTrue(report.allPassed, (report.temporalCases + report.contextModuleCases).filterNot { it.passed }.toString())
    }
}
