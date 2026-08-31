package dev.kian.mymettle.engine.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicTrendSequentialReuseAssessmentTest {
    @Test
    fun `latest-session reanchor preserves the same latent linear trajectory`() {
        val oldLatest = 4.20
        val trend = 0.035
        val newLatest = DynamicTrendSequentialReuseAssessment.reanchorLatestLogFrontier(oldLatest, trend)

        // A session that was z=0 becomes z=-1 after the origin advances.
        assertEquals(oldLatest, newLatest + trend * -1.0, 1e-15)
        // The next-session forecast under the old origin becomes the new z=0 level exactly.
        assertEquals(oldLatest + trend, newLatest, 1e-15)
    }

    @Test
    fun `current Candidate v2 numerical reference is not falsely labelled exact incremental replay`() {
        val result = DynamicTrendSequentialReuseAssessment.assess()
        assertEquals(
            CandidateV2SequentialReuseVerdict.FULL_INCREMENTAL_POSTERIOR_NOT_EQUIVALENT_TO_CURRENT_BATCH_REFERENCE,
            result.verdict,
        )
        assertTrue(result.currentReferenceUsesMovingRecentSessionWindow)
        assertTrue(result.oldestLikelihoodMayNeedRemoval)
        assertTrue(result.referenceRepCoordinateMayChange)
        assertTrue(result.nuisanceLearningRegimeMayChange)
        assertTrue(result.frozenV1NumericalGridMayChange)
        assertFalse(result.exactFactorRemovalStatePersisted)
    }
}
