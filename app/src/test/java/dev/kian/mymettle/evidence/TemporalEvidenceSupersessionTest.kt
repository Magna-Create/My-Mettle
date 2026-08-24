package dev.kian.mymettle.evidence

import dev.kian.mymettle.domain.evidence.EvidenceTimeBounds
import dev.kian.mymettle.domain.evidence.ExternalRecordProvenance
import dev.kian.mymettle.domain.evidence.SourceState
import dev.kian.mymettle.domain.evidence.TimingQuality
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class TemporalEvidenceSupersessionTest {
    @Test
    fun `source revisions append linearly while retaining deleted history`() {
        val revision1 = artifact("r1", 1, SourceState.AVAILABLE)
        ExternalArtifactSupersedingPolicy.validateAppend(revision1, emptyList())
        val revision2 = artifact("r2", 2, SourceState.UPDATED_AT_SOURCE, "r1")
        ExternalArtifactSupersedingPolicy.validateAppend(revision2, listOf(revision1))
        val deletion = artifact("r3", 3, SourceState.DELETED_AT_SOURCE, "r2")
        ExternalArtifactSupersedingPolicy.validateAppend(deletion, listOf(revision1, revision2))

        assertEquals(deletion, ExternalArtifactSupersedingPolicy.current(listOf(revision1, revision2, deletion)))
        assertEquals(SourceState.AVAILABLE, revision1.sourceState)
        assertNotEquals(SourceState.PERMISSION_UNAVAILABLE, deletion.sourceState)
    }

    @Test
    fun `duplicate revision forks and skipped revisions fail`() {
        val revision1 = artifact("r1", 1, SourceState.AVAILABLE)
        val revision2 = artifact("r2", 2, SourceState.UPDATED_AT_SOURCE, "r1")

        assertFailsWith<TemporalEvidenceException> {
            ExternalArtifactSupersedingPolicy.validateAppend(
                artifact("fork", 2, SourceState.UPDATED_AT_SOURCE, "r1"),
                listOf(revision1, revision2),
            )
        }
        assertFailsWith<TemporalEvidenceException> {
            ExternalArtifactSupersedingPolicy.validateAppend(
                artifact("skip", 4, SourceState.UPDATED_AT_SOURCE, "r2"),
                listOf(revision1, revision2),
            )
        }
    }

    private fun artifact(
        id: String,
        revision: Int,
        state: SourceState,
        supersedes: String? = null,
    ) = ExternalRecordProvenance(
        id = id,
        logicalSourceKey = "source-record",
        nativeRevision = revision,
        provider = "fixture",
        dataOrigin = "fixture.origin",
        sourceRecordType = "heart-rate-series",
        sourceRecordId = "source-record",
        sourceClientRecordId = null,
        sourceClientRecordVersion = revision.toLong(),
        sourceDeviceManufacturer = null,
        sourceDeviceModel = null,
        sourceDeviceType = null,
        recordingMethod = "sensor",
        sourceLastModifiedAt = NOW.plusSeconds(revision.toLong()),
        importedAt = NOW.plusSeconds(revision.toLong()),
        sourceBounds = EvidenceTimeBounds(NOW, NOW.plusSeconds(60), TimingQuality.SOURCE_REPORTED_BOUND),
        sourceState = state,
        supersedesArtifactId = supersedes,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-24T08:30:00Z")
    }
}
