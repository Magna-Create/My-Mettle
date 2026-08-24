package dev.kian.mymettle.evidence

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.DerivedEvidenceSummaryEntity
import dev.kian.mymettle.data.local.entity.DerivedEvidenceSummaryInputEntity
import dev.kian.mymettle.data.local.entity.EvidenceTraceChunkEntity
import dev.kian.mymettle.data.local.entity.EvidenceTraceEntity
import dev.kian.mymettle.data.local.entity.ExternalEvidenceArtifactEntity
import dev.kian.mymettle.data.local.entity.ObservationTraceLinkEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseTraceLinkEntity
import dev.kian.mymettle.data.local.entity.SessionTraceLinkEntity
import dev.kian.mymettle.data.local.entity.SetRecordTraceLinkEntity
import dev.kian.mymettle.domain.evidence.AcquisitionMethod
import dev.kian.mymettle.domain.evidence.DerivedEvidenceSummary
import dev.kian.mymettle.domain.evidence.EvidenceGranularity
import dev.kian.mymettle.domain.evidence.EvidenceMetricKey
import dev.kian.mymettle.domain.evidence.EvidencePayload
import dev.kian.mymettle.domain.evidence.EvidenceQuality
import dev.kian.mymettle.domain.evidence.EvidenceSemanticRole
import dev.kian.mymettle.domain.evidence.EvidenceTimeBounds
import dev.kian.mymettle.domain.evidence.EvidenceTrace
import dev.kian.mymettle.domain.evidence.EvidenceTraceChunk
import dev.kian.mymettle.domain.evidence.ExternalRecordProvenance
import dev.kian.mymettle.domain.evidence.IntervalEvidenceValue
import dev.kian.mymettle.domain.evidence.IntervalValueSemantics
import dev.kian.mymettle.domain.evidence.SourceState
import dev.kian.mymettle.domain.evidence.StoredEvidenceTrace
import dev.kian.mymettle.domain.evidence.TemporalEvidenceCodec
import dev.kian.mymettle.domain.evidence.TemporalRepresentation
import dev.kian.mymettle.domain.evidence.TimingQuality
import dev.kian.mymettle.domain.evidence.TraceScopeLinks
import dev.kian.mymettle.domain.performance.UnitId
import java.security.MessageDigest
import java.time.Instant

class TemporalEvidenceException(message: String) : IllegalStateException(message)

data class TemporalRawState(
    val artifacts: List<ExternalRecordProvenance>,
    val traces: List<EvidenceTrace>,
    val chunks: List<EvidenceTraceChunk>,
    val sessionLinks: List<Pair<String, String>>,
    val sessionExerciseLinks: List<Pair<String, String>>,
    val setRecordLinks: List<Pair<String, String>>,
    val observationLinks: List<Pair<String, String>>,
) {
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(value: Any?) {
            val bytes = (value?.toString() ?: "<null>").encodeToByteArray()
            digest.update(bytes.size.toString().encodeToByteArray())
            digest.update(0.toByte())
            digest.update(bytes)
            digest.update(0.toByte())
        }
        artifacts.forEach(::add)
        traces.forEach(::add)
        chunks.forEach { chunk ->
            add(chunk.copy(payload = EvidencePayload.Points(emptyList())))
            add(chunk.payloadSha256)
        }
        sessionLinks.forEach(::add)
        sessionExerciseLinks.forEach(::add)
        setRecordLinks.forEach(::add)
        observationLinks.forEach(::add)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Android-independent temporal semantics over Room's source-aligned BLOB storage. */
class TemporalEvidenceRepository(private val database: MyMettleDatabase) {
    private val dao get() = database.temporalEvidenceDao()

    suspend fun appendExternalArtifact(artifact: ExternalRecordProvenance) = database.withTransaction {
        val existing = dao.externalArtifactRevisions(artifact.logicalSourceKey).map { it.toDomain() }
        ExternalArtifactSupersedingPolicy.validateAppend(artifact, existing)
        dao.insertExternalArtifacts(listOf(artifact.toEntity()))
    }

    suspend fun persistTrace(
        trace: EvidenceTrace,
        chunks: List<EvidenceTraceChunk>,
        links: TraceScopeLinks,
    ) = database.withTransaction {
        if (chunks.isEmpty()) throw TemporalEvidenceException("A canonical trace needs at least one source-aligned chunk.")
        if (links.workoutSessionIds.isEmpty() && links.sessionExerciseIds.isEmpty() &&
            links.setRecordIds.isEmpty() && links.observationIds.isEmpty()
        ) {
            throw TemporalEvidenceException("A trace must have at least one referentially-safe scope link.")
        }
        TraceSupersedingPolicy.validateAppend(trace, dao.allTraces().map { it.toDomain() })
        trace.supersedesTraceId?.let { predecessorId ->
            val predecessor = storedTrace(predecessorId)
                ?: throw TemporalEvidenceException("A trace revision must supersede a stored predecessor.")
            if (predecessor.links != links) {
                throw TemporalEvidenceException("A trace revision cannot silently change its canonical scope links.")
            }
        }
        validateChunks(trace, chunks)
        dao.insertTraces(listOf(trace.toEntity()))
        dao.insertTraceChunks(chunks.sortedBy { it.ordinal }.map { it.toEntity() })
        links.workoutSessionIds.takeIf { it.isNotEmpty() }?.let { ids ->
            dao.insertSessionTraceLinks(ids.sorted().map { SessionTraceLinkEntity(it, trace.id) })
        }
        links.sessionExerciseIds.takeIf { it.isNotEmpty() }?.let { ids ->
            dao.insertSessionExerciseTraceLinks(ids.sorted().map { SessionExerciseTraceLinkEntity(it, trace.id) })
        }
        links.setRecordIds.takeIf { it.isNotEmpty() }?.let { ids ->
            dao.insertSetRecordTraceLinks(ids.sorted().map { SetRecordTraceLinkEntity(it, trace.id) })
        }
        links.observationIds.takeIf { it.isNotEmpty() }?.let { ids ->
            dao.insertObservationTraceLinks(ids.sorted().map { ObservationTraceLinkEntity(it, trace.id) })
        }
    }

    suspend fun storedTrace(traceId: String): StoredEvidenceTrace? {
        val trace = dao.trace(traceId)?.toDomain() ?: return null
        val chunks = dao.traceChunks(traceId).map { it.toDomain() }
        validateChunks(trace, chunks)
        return StoredEvidenceTrace(
            trace = trace,
            chunks = chunks,
            links = TraceScopeLinks(
                workoutSessionIds = dao.sessionIdsForTrace(traceId).toSet(),
                sessionExerciseIds = dao.sessionExerciseIdsForTrace(traceId).toSet(),
                setRecordIds = dao.setRecordIdsForTrace(traceId).toSet(),
                observationIds = dao.observationIdsForTrace(traceId).toSet(),
            ),
        )
    }

    suspend fun tracesForObservation(observationId: String): List<StoredEvidenceTrace> =
        currentStoredTraces(dao.traceIdsForObservation(observationId))

    suspend fun tracesForSession(sessionId: String): List<StoredEvidenceTrace> =
        currentStoredTraces(dao.traceIdsForSession(sessionId))

    /** Resolve the immutable successor chain without hiding the addressable historical revisions. */
    suspend fun currentTraceRevision(traceId: String): StoredEvidenceTrace? {
        val traces = dao.allTraces().map { it.toDomain() }
        validateChain(traces.map { ChainNode(it.id, it.supersedesTraceId) }, "trace")
        if (traces.none { it.id == traceId }) return null
        var currentId = traceId
        while (true) {
            val successor = traces.singleOrNull { it.supersedesTraceId == currentId } ?: break
            currentId = successor.id
        }
        return storedTrace(currentId)
    }

    suspend fun currentExternalArtifact(logicalSourceKey: String): ExternalRecordProvenance? {
        val current = dao.currentExternalArtifacts(logicalSourceKey)
        if (current.size > 1) throw TemporalEvidenceException("External source $logicalSourceKey has ambiguous active revisions.")
        return current.singleOrNull()?.toDomain()
    }

    suspend fun saveDerivedSummary(summary: DerivedEvidenceSummary) = database.withTransaction {
        if (summary.inputTraceIds.any { dao.trace(it) == null }) {
            throw TemporalEvidenceException("A derived summary references a missing canonical trace.")
        }
        dao.insertDerivedSummaries(listOf(summary.toEntity()))
        dao.insertDerivedSummaryInputs(summary.inputTraceIds.sorted().map { DerivedEvidenceSummaryInputEntity(summary.id, it) })
    }

    suspend fun discardDerivedEvidence() = database.withTransaction {
        dao.deleteAllDerivedSummaries()
        dao.deleteAllTraceUiCaches()
    }

    suspend fun rawState(): TemporalRawState {
        val artifacts = dao.allExternalArtifacts().map { it.toDomain() }
        val traces = dao.allTraces().map { it.toDomain() }
        val chunks = dao.allTraceChunks().map { it.toDomain() }
        return TemporalRawState(
            artifacts = artifacts,
            traces = traces,
            chunks = chunks,
            sessionLinks = dao.allSessionTraceLinks().map { it.sessionId to it.traceId },
            sessionExerciseLinks = dao.allSessionExerciseTraceLinks().map { it.sessionExerciseId to it.traceId },
            setRecordLinks = dao.allSetRecordTraceLinks().map { it.setRecordId to it.traceId },
            observationLinks = dao.allObservationTraceLinks().map { it.observationId to it.traceId },
        )
    }

    private suspend fun currentStoredTraces(traceIds: List<String>): List<StoredEvidenceTrace> = traceIds
        .mapNotNull { currentTraceRevision(it) }
        .distinctBy { it.trace.id }
        .sortedBy { it.trace.id }

    private suspend fun validateChunks(trace: EvidenceTrace, chunks: List<EvidenceTraceChunk>) {
        if (chunks.isEmpty()) throw TemporalEvidenceException("A canonical trace needs at least one source-aligned chunk.")
        if (chunks.any { it.traceId != trace.id }) throw TemporalEvidenceException("Every chunk must belong to its trace.")
        if (chunks.map { it.ordinal }.sorted() != chunks.indices.toList()) {
            throw TemporalEvidenceException("Chunk ordinals must be contiguous and zero-based.")
        }
        chunks.forEach { chunk ->
            if (chunk.payload.representation != trace.representation) {
                throw TemporalEvidenceException("Chunk ${chunk.id} representation does not match trace ${trace.id}.")
            }
            if (chunk.payload.sampleCount <= 0) throw TemporalEvidenceException("Canonical chunks cannot be empty.")
            if (chunk.encodingVersion != TemporalEvidenceCodec.ENCODING_VERSION) {
                throw TemporalEvidenceException("Unsupported chunk encoding version ${chunk.encodingVersion}.")
            }
            val encoded = TemporalEvidenceCodec.encode(chunk.payload)
            if (TemporalEvidenceCodec.sha256(encoded) != chunk.payloadSha256) {
                throw TemporalEvidenceException("Chunk ${chunk.id} checksum does not match its canonical payload.")
            }
            chunk.sourceArtifactId?.let { artifactId ->
                if (dao.externalArtifact(artifactId) == null) {
                    throw TemporalEvidenceException("Chunk ${chunk.id} references missing artifact $artifactId.")
                }
            }
            if (chunk.payload is EvidencePayload.Intervals) {
                val stateValues = chunk.payload.samples.map { it.value is IntervalEvidenceValue.State }.distinct()
                if (stateValues.size > 1) throw TemporalEvidenceException("One interval trace cannot mix numeric and state values.")
                if (stateValues.single() != (trace.intervalSemantics == IntervalValueSemantics.STATE_OVER_INTERVAL)) {
                    throw TemporalEvidenceException("Interval payload kind does not match trace interval semantics.")
                }
            }
        }
    }
}

internal object ExternalArtifactSupersedingPolicy {
    fun validateAppend(candidate: ExternalRecordProvenance, existing: List<ExternalRecordProvenance>) {
        validateExisting(existing)
        if (existing.any { it.id == candidate.id }) throw TemporalEvidenceException("External artifact ids are immutable and unique.")
        if (existing.any { it.nativeRevision == candidate.nativeRevision }) {
            throw TemporalEvidenceException("External source revisions cannot be imported twice.")
        }
        if (existing.isEmpty()) {
            if (candidate.nativeRevision != 1 || candidate.supersedesArtifactId != null) {
                throw TemporalEvidenceException("The first Native source snapshot must be revision 1 without a predecessor.")
            }
            return
        }
        val current = current(existing)
        if (candidate.supersedesArtifactId != current.id || candidate.nativeRevision != current.nativeRevision + 1) {
            throw TemporalEvidenceException("A source update must append the next revision to the single current snapshot.")
        }
    }

    fun current(existing: List<ExternalRecordProvenance>): ExternalRecordProvenance {
        validateExisting(existing)
        val superseded = existing.mapNotNullTo(hashSetOf()) { it.supersedesArtifactId }
        return existing.filterNot { it.id in superseded }.singleOrNull()
            ?: throw TemporalEvidenceException("External source revision chain has no single current snapshot.")
    }

    private fun validateExisting(existing: List<ExternalRecordProvenance>) {
        if (existing.isEmpty()) return
        if (existing.map { it.logicalSourceKey }.distinct().size != 1) {
            throw TemporalEvidenceException("External revision validation requires one logical source key.")
        }
        validateChain(existing.map { ChainNode(it.id, it.supersedesArtifactId) }, "external artifact")
    }
}

internal object TraceSupersedingPolicy {
    fun validateAppend(candidate: EvidenceTrace, existing: List<EvidenceTrace>) {
        validateChain(existing.map { ChainNode(it.id, it.supersedesTraceId) }, "trace")
        if (existing.any { it.id == candidate.id }) throw TemporalEvidenceException("Trace ids are immutable and unique.")
        val predecessorId = candidate.supersedesTraceId ?: return
        val predecessor = existing.singleOrNull { it.id == predecessorId }
            ?: throw TemporalEvidenceException("A trace revision must supersede an existing trace.")
        if (existing.any { it.supersedesTraceId == predecessorId }) {
            throw TemporalEvidenceException("A trace cannot have multiple direct successors.")
        }
        if (candidate.metric != predecessor.metric || candidate.representation != predecessor.representation ||
            candidate.canonicalUnit != predecessor.canonicalUnit
        ) {
            throw TemporalEvidenceException("A trace revision cannot change metric, representation, or canonical unit identity.")
        }
    }
}

private data class ChainNode(val id: String, val predecessorId: String?)

private fun validateChain(nodes: List<ChainNode>, label: String) {
    val byId = nodes.associateBy { it.id }
    if (byId.size != nodes.size) throw TemporalEvidenceException("$label ids must be unique.")
    val predecessors = nodes.mapNotNull { it.predecessorId }
    if (predecessors.size != predecessors.distinct().size) {
        throw TemporalEvidenceException("A $label cannot have multiple direct successors.")
    }
    nodes.forEach { start ->
        val visited = hashSetOf<String>()
        var cursor: ChainNode? = start
        while (cursor != null) {
            if (!visited.add(cursor.id)) throw TemporalEvidenceException("$label supersession cannot contain cycles.")
            cursor = cursor.predecessorId?.let { byId[it] ?: throw TemporalEvidenceException("$label supersedes a missing predecessor.") }
        }
    }
}

private fun ExternalRecordProvenance.toEntity() = ExternalEvidenceArtifactEntity(
    id, logicalSourceKey, nativeRevision, provider, dataOrigin, sourceRecordType, sourceRecordId,
    sourceClientRecordId, sourceClientRecordVersion, sourceDeviceManufacturer, sourceDeviceModel,
    sourceDeviceType, recordingMethod, sourceLastModifiedAt?.epochSecond, sourceLastModifiedAt?.nano,
    importedAt.epochSecond, importedAt.nano, sourceBounds.startedAt?.epochSecond, sourceBounds.startedAt?.nano,
    sourceBounds.endedAt?.epochSecond, sourceBounds.endedAt?.nano, sourceBounds.sourceZoneOffsetMinutes,
    sourceBounds.timingQuality.storageValue, sourceState.storageValue, supersedesArtifactId,
)

private fun ExternalEvidenceArtifactEntity.toDomain() = ExternalRecordProvenance(
    id, logicalSourceKey, nativeRevision, provider, dataOrigin, sourceRecordType, sourceRecordId,
    sourceClientRecordId, sourceClientRecordVersion, sourceDeviceManufacturer, sourceDeviceModel,
    sourceDeviceType, recordingMethod, instant(sourceLastModifiedEpochSecond, sourceLastModifiedNano),
    instantRequired(importedAtEpochSecond, importedAtNano),
    EvidenceTimeBounds(
        instant(sourceStartEpochSecond, sourceStartNano), instant(sourceEndEpochSecond, sourceEndNano),
        TimingQuality.fromStorage(timingQuality), sourceZoneOffsetMinutes,
    ),
    SourceState.fromStorage(sourceState), supersedesArtifactId,
)

private fun EvidenceTrace.toEntity() = EvidenceTraceEntity(
    id, metric.storageValue, representation.storageValue, intervalSemantics.storageValue,
    canonicalUnit?.storageValue, quality.acquisitionMethod.storageValue, quality.granularity.storageValue,
    semanticRole.storageValue, bounds.startedAt?.epochSecond, bounds.startedAt?.nano,
    bounds.endedAt?.epochSecond, bounds.endedAt?.nano, bounds.timingQuality.storageValue,
    bounds.sourceZoneOffsetMinutes, provenance, createdAt.epochSecond, createdAt.nano,
    recordedAt.epochSecond, recordedAt.nano, supersedesTraceId,
)

private fun EvidenceTraceEntity.toDomain() = EvidenceTrace(
    id = id,
    metric = EvidenceMetricKey.fromStorage(metricKey),
    representation = TemporalRepresentation.fromStorage(representation),
    intervalSemantics = IntervalValueSemantics.fromStorage(intervalSemantics),
    canonicalUnit = canonicalUnit?.let(UnitId::fromStorage),
    quality = EvidenceQuality(EvidenceGranularity.fromStorage(granularity), AcquisitionMethod.fromStorage(acquisitionMethod)),
    semanticRole = EvidenceSemanticRole.fromStorage(semanticRole),
    bounds = EvidenceTimeBounds(
        instant(startedAtEpochSecond, startedAtNano), instant(endedAtEpochSecond, endedAtNano),
        TimingQuality.fromStorage(timingQuality), sourceZoneOffsetMinutes,
    ),
    provenance = provenance,
    createdAt = instantRequired(createdAtEpochSecond, createdAtNano),
    recordedAt = instantRequired(recordedAtEpochSecond, recordedAtNano),
    supersedesTraceId = supersedesTraceId,
)

private fun EvidenceTraceChunk.toEntity(): EvidenceTraceChunkEntity {
    val bytes = TemporalEvidenceCodec.encode(payload)
    return EvidenceTraceChunkEntity(
        id, traceId, sourceArtifactId, ordinal, sourceBounds.startedAt?.epochSecond, sourceBounds.startedAt?.nano,
        sourceBounds.endedAt?.epochSecond, sourceBounds.endedAt?.nano, sourceBounds.sourceZoneOffsetMinutes,
        sourceBounds.timingQuality.storageValue, payload.sampleCount, encodingVersion, payload.representation.storageValue,
        bytes, payloadSha256, createdAt.epochSecond, createdAt.nano,
    )
}

private fun EvidenceTraceChunkEntity.toDomain(): EvidenceTraceChunk {
    if (sampleCount < 0) throw TemporalEvidenceException("Chunk $id has a negative sample count.")
    if (TemporalEvidenceCodec.sha256(payload) != payloadSha256) throw TemporalEvidenceException("Chunk $id checksum failed.")
    val decoded = TemporalEvidenceCodec.decode(payload, TemporalRepresentation.fromStorage(representation))
    if (decoded.sampleCount != sampleCount) throw TemporalEvidenceException("Chunk $id sample count does not match its payload.")
    return EvidenceTraceChunk(
        id, traceId, sourceArtifactId, ordinal,
        EvidenceTimeBounds(
            instant(sourceStartEpochSecond, sourceStartNano), instant(sourceEndEpochSecond, sourceEndNano),
            TimingQuality.fromStorage(timingQuality), sourceZoneOffsetMinutes,
        ),
        decoded, encodingVersion, payloadSha256, instantRequired(createdAtEpochSecond, createdAtNano),
    )
}

private fun DerivedEvidenceSummary.toEntity() = DerivedEvidenceSummaryEntity(
    id, summaryType, algorithmId, algorithmVersion, inputFingerprint, computedAt.epochSecond, computedAt.nano,
    numericValue, canonicalUnit?.storageValue, payload,
)

private fun instant(epochSecond: Long?, nano: Int?): Instant? = when {
    epochSecond == null && nano == null -> null
    epochSecond == null || nano == null -> throw TemporalEvidenceException("Timestamp epoch-second/nano columns must both be null or present.")
    nano !in 0..999_999_999 -> throw TemporalEvidenceException("Timestamp nano adjustment is invalid.")
    else -> Instant.ofEpochSecond(epochSecond, nano.toLong())
}

private fun instantRequired(epochSecond: Long, nano: Int): Instant =
    instant(epochSecond, nano) ?: throw TemporalEvidenceException("Required timestamp is missing.")
