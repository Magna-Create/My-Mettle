package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.performance.MetricFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NBio7CSyntheticValidationTest {
    @Test
    fun `all 7C families pass preregistered synthetic latent truth prevalidation`() {
        val report = NBio7CSyntheticValidation.run()
        assertEquals(NBio7CSyntheticValidation.PROTOCOL_VERSION, report.protocolVersion)
        assertEquals(15, report.cases.size)
        assertEquals(NonDynamicExpectedFamilies, report.familyPassed.keys)
        assertTrue(
            report.passed,
            report.cases.filterNot { it.passed }.joinToString("\n") { failed ->
                "${failed.family.storageValue}/${failed.scenario}: " +
                    "failure=${failed.numericalFailure} " +
                    "truthSlope=${failed.truthSlope} " +
                    "sparseSlope=${failed.sparseSlope} " +
                    "denseSlope=${failed.denseSlope} " +
                    "checks=${failed.recoveryChecks.filterValues { !it }}"
            },
        )
    }

    private companion object {
        val NonDynamicExpectedFamilies = setOf(
            MetricFamily.LOADED_HOLD,
            MetricFamily.DURATION_ONLY,
            MetricFamily.REPEATED_CONTRACTION,
        )
    }
}
