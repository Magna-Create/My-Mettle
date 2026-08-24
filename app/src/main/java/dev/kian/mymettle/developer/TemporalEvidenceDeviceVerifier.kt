package dev.kian.mymettle.developer

import android.content.Context
import androidx.room.Room
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.UserProfileEntity
import dev.kian.mymettle.data.reference.ReferenceSeedCallback
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
import dev.kian.mymettle.domain.evidence.IntervalEvidenceSample
import dev.kian.mymettle.domain.evidence.IntervalEvidenceValue
import dev.kian.mymettle.domain.evidence.IntervalValueSemantics
import dev.kian.mymettle.domain.evidence.PointEvidenceSample
import dev.kian.mymettle.domain.evidence.SourceState
import dev.kian.mymettle.domain.evidence.TemporalEvidenceCodec
import dev.kian.mymettle.domain.evidence.TemporalRepresentation
import dev.kian.mymettle.domain.evidence.TimingQuality
import dev.kian.mymettle.domain.evidence.TraceScopeLinks
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.EquipmentProfile
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersion
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.exercise.RecruitmentProfile
import dev.kian.mymettle.domain.exercise.RecruitmentProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.PerformanceSchema
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.SchemaMetric
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.evidence.TemporalEvidenceRepository
import dev.kian.mymettle.inference.RoomInferenceRepository
import dev.kian.mymettle.library.ExecutionProfileAuthoringRepository
import dev.kian.mymettle.library.ExecutionProfileAuthoringRequest
import dev.kian.mymettle.workout.RoomWorkoutRepository
import java.time.Instant

/** Production-path temporal checks shared by the developer menu and instrumentation entry point. */
internal class TemporalEvidenceDeviceVerifier(context: Context) {
    private val appContext = context.applicationContext

    suspend fun runChecks(): List<NBio6VerificationCheck> = listOf(
        runCheck("temporal-point", "Point-series persistence and codec", ::verifyPointSeries),
        runCheck("temporal-interval", "Interval-series value semantics", ::verifyIntervalSeries),
        runCheck("temporal-session-hr", "One session HR trace contextualises multiple sets", ::verifySessionHeartRate),
        runCheck("temporal-mixed", "Manual summaries and imported trace coexist", ::verifyMixedResolutionBout),
        runCheck("temporal-revision", "Immutable external revision supersession", ::verifySourceRevision),
        runCheck("temporal-deletion", "Deleted-source snapshot retention", ::verifyDeletedSourceRetention),
        runCheck("temporal-raw-derived", "Raw trace survives derived-state deletion", ::verifyRawDerivedSeparation),
        runCheck("temporal-gap-spike", "Gaps and sensor spikes remain raw", ::verifyGapAndSpike),
        runCheck("temporal-shape", "Equal averages retain different traces", ::verifyEqualAverageDifferentTrace),
        runCheck("temporal-timing", "Exact bounds and timing quality", ::verifyTimingBounds),
    )

    private suspend fun runCheck(
        id: String,
        title: String,
        block: suspend (MyMettleDatabase) -> String,
    ): NBio6VerificationCheck {
        val started = System.nanoTime()
        return runCatching { withDatabase(block) }.fold(
            onSuccess = { NBio6VerificationCheck(id, title, true, it, elapsedMillis(started)) },
            onFailure = { NBio6VerificationCheck(id, title, false, it.conciseMessage(), elapsedMillis(started)) },
        )
    }

    private suspend fun <T> withDatabase(block: suspend (MyMettleDatabase) -> T): T {
        val database = Room.inMemoryDatabaseBuilder(appContext, MyMettleDatabase::class.java)
            .addCallback(ReferenceSeedCallback(appContext))
            .build()
        return try {
            database.openHelper.writableDatabase
            block(database)
        } finally {
            database.close()
        }
    }

    private suspend fun verifyPointSeries(database: MyMettleDatabase): String {
        val fixture = seedFixture(database, "point")
        val samples = canonicalPointSamples()
        val artifactA = artifact("point-artifact-a", "point-source-a", bounds = EvidencePayload.Points(samples.take(2)).bounds())
        val artifactB = artifact("point-artifact-b", "point-source-b", bounds = EvidencePayload.Points(samples.drop(2)).bounds())
        val repository = TemporalEvidenceRepository(database)
        repository.appendExternalArtifact(artifactA)
        repository.appendExternalArtifact(artifactB)
        val trace = pointTrace("point-trace", EvidenceMetricKey.HEART_RATE, samples, EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE)
        val chunkA = chunk("point-chunk-a", trace, EvidencePayload.Points(samples.take(2)), artifactA.id, ordinal = 0)
        val chunkB = chunk("point-chunk-b", trace, EvidencePayload.Points(samples.drop(2)), artifactB.id, ordinal = 1)
        repository.persistTrace(trace, listOf(chunkA, chunkB), TraceScopeLinks(workoutSessionIds = setOf(fixture.sessionId)))

        val stored = requireNotNull(repository.storedTrace(trace.id))
        val decoded = stored.chunks.flatMap { (it.payload as EvidencePayload.Points).samples }
        expect(decoded == samples) { "Point samples changed during Room round trip." }
        expect(stored.chunks.map { it.sourceArtifactId } == listOf(artifactA.id, artifactB.id)) {
            "Source-record provenance boundaries were not retained."
        }
        expect(stored.trace.metric == EvidenceMetricKey.HEART_RATE && stored.trace.semanticRole == EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE) {
            "Heart rate lost its physiological-response identity."
        }
        return "trace=${trace.id}; samples=${samples.size}; chunks=2; codec=${chunkA.encodingVersion}; sha256=${chunkA.payloadSha256.take(10)}…/${chunkB.payloadSha256.take(10)}…."
    }

    private suspend fun verifyIntervalSeries(database: MyMettleDatabase): String {
        val fixture = seedFixture(database, "interval")
        val repository = TemporalEvidenceRepository(database)
        val numeric = EvidencePayload.Intervals(
            listOf(IntervalEvidenceSample(START, START.plusSeconds(300), IntervalEvidenceValue.Numeric(1_020.0))),
        )
        val distance = intervalTrace(
            id = "distance-interval-trace",
            metric = EvidenceMetricKey.DISTANCE,
            payload = numeric,
            semantics = IntervalValueSemantics.TOTAL_OVER_INTERVAL,
        )
        repository.persistTrace(distance, listOf(chunk("distance-interval-chunk", distance, numeric)), TraceScopeLinks(workoutSessionIds = setOf(fixture.sessionId)))

        val statePayload = EvidencePayload.Intervals(
            listOf(IntervalEvidenceSample(START, START.plusSeconds(720), IntervalEvidenceValue.State("vigorous"))),
        )
        val state = intervalTrace(
            id = "activity-state-trace",
            metric = EvidenceMetricKey.ACTIVITY_STATE,
            payload = statePayload,
            semantics = IntervalValueSemantics.STATE_OVER_INTERVAL,
        )
        repository.persistTrace(state, listOf(chunk("activity-state-chunk", state, statePayload)), traceScope(fixture.sessionId))
        expect(repository.storedTrace(distance.id)?.chunks?.single()?.payload == numeric) { "Numeric interval total changed." }
        expect(repository.storedTrace(state.id)?.chunks?.single()?.payload == statePayload) { "Categorical interval state changed." }
        return "distance=1020 m TOTAL_OVER_INTERVAL; activity=vigorous STATE_OVER_INTERVAL; exact interval bounds preserved."
    }

    private suspend fun verifySessionHeartRate(database: MyMettleDatabase): String {
        val fixture = seedFixture(database, "session-hr", setCount = 2)
        val repository = TemporalEvidenceRepository(database)
        val samples = canonicalPointSamples()
        val trace = pointTrace("session-hr-trace", EvidenceMetricKey.HEART_RATE, samples, EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE)
        repository.persistTrace(
            trace,
            listOf(chunk("session-hr-chunk", trace, EvidencePayload.Points(samples))),
            TraceScopeLinks(
                workoutSessionIds = setOf(fixture.sessionId),
                observationIds = fixture.observationIds.toSet(),
            ),
        )
        val dao = database.temporalEvidenceDao()
        expect(dao.allTraceChunks().size == 1) { "The session HR payload was duplicated." }
        expect(dao.observationIdsForTrace(trace.id).toSet() == fixture.observationIds.toSet()) {
            "The same session trace did not resolve from both observations."
        }
        expect(fixture.observationIds.all { repository.tracesForObservation(it).single().trace.id == trace.id }) {
            "Observation-to-session trace resolution failed."
        }
        return "trace=${trace.id} stored once; chunkCount=1; observationLinks=${fixture.observationIds.size}."
    }

    private suspend fun verifyMixedResolutionBout(database: MyMettleDatabase): String {
        val fixture = seedFixture(
            database = database,
            key = "mixed-treadmill",
            family = MetricFamily.SPEED_DURATION,
            schemaMetrics = listOf(
                SchemaMetric(PerformanceMetric.SPEED, required = false, defaultUnit = UnitId.METRES_PER_SECOND),
                SchemaMetric(PerformanceMetric.DURATION, required = true, defaultUnit = UnitId.MINUTE),
                SchemaMetric(PerformanceMetric.INCLINE_GRADE, required = true, defaultUnit = UnitId.PERCENT),
            ),
            values = listOf(
                scalar(PerformanceMetric.DURATION, 20.0, UnitId.MINUTE, AcquisitionMethod.DEVICE_DERIVED),
                scalar(PerformanceMetric.INCLINE_GRADE, 4.0, UnitId.PERCENT, AcquisitionMethod.USER_REPORTED),
            ),
            lateralityMode = LateralityMode.NOT_APPLICABLE,
            laterality = Laterality.NOT_APPLICABLE,
        )
        val repository = TemporalEvidenceRepository(database)
        val artifact = artifact("mixed-speed-artifact", "mixed-speed-source")
        repository.appendExternalArtifact(artifact)
        val samples = listOf(PointEvidenceSample(START, 2.5), PointEvidenceSample(START.plusSeconds(30), 3.0))
        val trace = pointTrace("mixed-speed-trace", EvidenceMetricKey.SPEED, samples, EvidenceSemanticRole.PERFORMANCE_OUTPUT)
        repository.persistTrace(
            trace,
            listOf(chunk("mixed-speed-chunk", trace, EvidencePayload.Points(samples), artifact.id)),
            TraceScopeLinks(observationIds = setOf(fixture.observationIds.single())),
        )
        val values = database.workoutDao().metricValues(fixture.observationIds).associateBy { it.metric }
        expect(values.getValue(PerformanceMetric.DURATION.storageValue).acquisitionMethod == AcquisitionMethod.DEVICE_DERIVED.storageValue) {
            "Device-derived duration acquisition was lost."
        }
        expect(values.getValue(PerformanceMetric.INCLINE_GRADE.storageValue).acquisitionMethod == AcquisitionMethod.USER_REPORTED.storageValue) {
            "User-reported incline acquisition was lost."
        }
        expect(repository.tracesForObservation(fixture.observationIds.single()).single().trace.quality == EvidenceQuality(EvidenceGranularity.TRACE, AcquisitionMethod.SENSOR_RECORDED)) {
            "Imported speed trace quality was lost."
        }
        return "one bout: duration SUMMARY+DEVICE_DERIVED, grade SUMMARY+USER_REPORTED, speed TRACE+SENSOR_RECORDED."
    }

    private suspend fun verifySourceRevision(database: MyMettleDatabase): String {
        val fixture = seedFixture(database, "revision")
        val repository = TemporalEvidenceRepository(database)
        val revision1 = artifact("artifact-r1", "watch-record", revision = 1)
        repository.appendExternalArtifact(revision1)
        val samples1 = canonicalPointSamples()
        val trace1 = pointTrace("trace-r1", EvidenceMetricKey.HEART_RATE, samples1, EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE)
        repository.persistTrace(trace1, listOf(chunk("chunk-r1", trace1, EvidencePayload.Points(samples1), revision1.id)), traceScope(fixture.sessionId))

        val revision2 = artifact(
            id = "artifact-r2",
            logicalKey = "watch-record",
            revision = 2,
            state = SourceState.UPDATED_AT_SOURCE,
            supersedes = revision1.id,
        )
        repository.appendExternalArtifact(revision2)
        val samples2 = samples1 + PointEvidenceSample(START.plusSeconds(20), 152.0)
        val trace2 = pointTrace(
            "trace-r2",
            EvidenceMetricKey.HEART_RATE,
            samples2,
            EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE,
            supersedes = trace1.id,
        )
        repository.persistTrace(trace2, listOf(chunk("chunk-r2", trace2, EvidencePayload.Points(samples2), revision2.id)), traceScope(fixture.sessionId))
        expect(repository.currentExternalArtifact("watch-record") == revision2) { "Current source selection did not resolve revision 2." }
        expect(repository.currentTraceRevision(trace1.id)?.trace?.id == trace2.id) {
            "Current trace selection did not resolve revision 2."
        }
        expect(repository.storedTrace(trace1.id)?.chunks?.single()?.payload == EvidencePayload.Points(samples1)) {
            "Revision 1 trace was mutated."
        }
        expect(database.temporalEvidenceDao().externalArtifactRevisions("watch-record").size == 2) { "Revision audit chain was not retained." }
        return "artifact-r1 and artifact-r2 retained; active revision=2; trace-r2 supersedes trace-r1 without mutation."
    }

    private suspend fun verifyDeletedSourceRetention(database: MyMettleDatabase): String {
        val fixture = seedFixture(database, "deleted")
        val repository = TemporalEvidenceRepository(database)
        val available = artifact("deleted-r1", "deleted-source")
        repository.appendExternalArtifact(available)
        val samples = canonicalPointSamples()
        val trace = pointTrace("deleted-trace", EvidenceMetricKey.HEART_RATE, samples, EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE)
        repository.persistTrace(trace, listOf(chunk("deleted-chunk", trace, EvidencePayload.Points(samples), available.id)), traceScope(fixture.sessionId))
        repository.appendExternalArtifact(
            artifact("deleted-r2", "deleted-source", 2, SourceState.DELETED_AT_SOURCE, available.id),
        )
        expect(repository.currentExternalArtifact("deleted-source")?.sourceState == SourceState.DELETED_AT_SOURCE) {
            "Source deletion state did not become current."
        }
        expect(repository.storedTrace(trace.id)?.chunks?.single()?.payload == EvidencePayload.Points(samples)) {
            "Source deletion destroyed the Native trace snapshot."
        }
        expect(SourceState.PERMISSION_UNAVAILABLE != SourceState.DELETED_AT_SOURCE) { "Permission loss and deletion collapsed into one state." }
        return "current source state=DELETED_AT_SOURCE; prior canonical trace and ${samples.size} samples remain reproducible."
    }

    private suspend fun verifyRawDerivedSeparation(database: MyMettleDatabase): String {
        val fixture = seedFixture(database, "raw-derived")
        val repository = TemporalEvidenceRepository(database)
        val samples = canonicalPointSamples()
        val trace = pointTrace("raw-trace", EvidenceMetricKey.HEART_RATE, samples, EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE)
        repository.persistTrace(trace, listOf(chunk("raw-chunk", trace, EvidencePayload.Points(samples))), traceScope(fixture.sessionId))
        repository.saveDerivedSummary(
            DerivedEvidenceSummary(
                id = "sample-count-summary",
                summaryType = "sample_count",
                algorithmId = "device-verifier-count",
                algorithmVersion = "1",
                inputTraceIds = setOf(trace.id),
                inputFingerprint = repository.rawState().fingerprint(),
                computedAt = RECORDED,
                numericValue = samples.size.toDouble(),
                canonicalUnit = null,
                payload = null,
            ),
        )
        RoomInferenceRepository(database).recomputeFromRawHistory()
        val before = repository.rawState()
        RoomInferenceRepository(database).discardDerivedStateForRebuild()
        expect(repository.rawState() == before) { "Deleting inference mutated raw temporal evidence." }
        repository.discardDerivedEvidence()
        expect(repository.rawState() == before) { "Deleting temporal summaries mutated raw temporal evidence." }
        expect(database.temporalEvidenceDao().allDerivedSummaries().isEmpty()) { "Derived summaries were not deleted." }
        return "rawFingerprint=${before.fingerprint().take(20)}… unchanged after inference and temporal-summary deletion."
    }

    private suspend fun verifyGapAndSpike(database: MyMettleDatabase): String {
        val fixture = seedFixture(database, "gap-spike")
        val repository = TemporalEvidenceRepository(database)
        val samples = canonicalPointSamples()
        val trace = pointTrace("gap-spike-trace", EvidenceMetricKey.HEART_RATE, samples, EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE)
        repository.persistTrace(trace, listOf(chunk("gap-spike-chunk", trace, EvidencePayload.Points(samples))), traceScope(fixture.sessionId))
        val decoded = (repository.storedTrace(trace.id)?.chunks?.single()?.payload as EvidencePayload.Points).samples
        expect(decoded == samples) { "Raw gap/spike samples changed." }
        expect(decoded.zipWithNext().maxOf { (a, b) -> b.timestamp.epochSecond - a.timestamp.epochSecond } == 590L) {
            "The large sampling gap was filled or changed."
        }
        expect(decoded.any { it.canonicalValue == 238.0 }) { "The sensor spike was removed." }
        return "irregular samples=${decoded.size}; largestGap=590 s; raw spike=238 bpm preserved."
    }

    private suspend fun verifyEqualAverageDifferentTrace(database: MyMettleDatabase): String {
        val fixture = seedFixture(database, "shape")
        val repository = TemporalEvidenceRepository(database)
        val steady = listOf(PointEvidenceSample(START, 3.0), PointEvidenceSample(START.plusSeconds(60), 3.0))
        val interval = listOf(PointEvidenceSample(START, 2.0), PointEvidenceSample(START.plusSeconds(60), 4.0))
        val steadyTrace = pointTrace("steady-speed", EvidenceMetricKey.SPEED, steady, EvidenceSemanticRole.PERFORMANCE_OUTPUT)
        val intervalTrace = pointTrace("interval-speed", EvidenceMetricKey.SPEED, interval, EvidenceSemanticRole.PERFORMANCE_OUTPUT)
        val steadyChunk = chunk("steady-speed-chunk", steadyTrace, EvidencePayload.Points(steady))
        val intervalChunk = chunk("interval-speed-chunk", intervalTrace, EvidencePayload.Points(interval))
        repository.persistTrace(steadyTrace, listOf(steadyChunk), traceScope(fixture.sessionId))
        repository.persistTrace(intervalTrace, listOf(intervalChunk), traceScope(fixture.sessionId))
        expect(steady.map { it.canonicalValue }.average() == interval.map { it.canonicalValue }.average()) { "Fixture averages are not equal." }
        expect(steadyChunk.payloadSha256 != intervalChunk.payloadSha256) { "Different trace shapes collapsed to one canonical payload." }
        return "both arithmetic averages=3.0 m/s; steadySha=${steadyChunk.payloadSha256.take(12)}…; intervalSha=${intervalChunk.payloadSha256.take(12)}…."
    }

    private suspend fun verifyTimingBounds(database: MyMettleDatabase): String {
        val fixture = seedFixture(database, "timing")
        val observation = RoomWorkoutRepository(database).performanceSets(fixture.sessionExerciseId)
            .first().observations.single()
        expect(observation.startedAt == START && observation.endedAt == END && observation.timingQuality == TimingQuality.USER_ACTION_BOUND) {
            "Observation exact bounds or timing quality did not round-trip."
        }
        val entity = database.workoutDao().observations(listOf(fixture.setIds.first())).single()
        expect(entity.startedAtEpochSecond == START.epochSecond && entity.startedAtNano == START.nano) { "Observation start lost nanosecond precision." }
        expect(entity.endedAtEpochSecond == END.epochSecond && entity.endedAtNano == END.nano) { "Observation end lost nanosecond precision." }
        return "observation=${observation.id}; start=${observation.startedAt}; end=${observation.endedAt}; quality=${observation.timingQuality.storageValue}."
    }

    private suspend fun seedFixture(
        database: MyMettleDatabase,
        key: String,
        setCount: Int = 1,
        family: MetricFamily = MetricFamily.DYNAMIC_RESISTANCE,
        schemaMetrics: List<SchemaMetric> = listOf(
            SchemaMetric(PerformanceMetric.EXTERNAL_LOAD, required = true),
            SchemaMetric(PerformanceMetric.REPETITIONS, required = true),
        ),
        values: List<PerformanceMetricValue> = listOf(
            scalar(PerformanceMetric.EXTERNAL_LOAD, 20.0, UnitId.KILOGRAM, AcquisitionMethod.USER_REPORTED),
            scalar(PerformanceMetric.REPETITIONS, 8.0, UnitId.REPETITION, AcquisitionMethod.USER_REPORTED),
        ),
        lateralityMode: LateralityMode = LateralityMode.BILATERAL_ONLY,
        laterality: Laterality = Laterality.BILATERAL,
    ): Fixture {
        val version = profileVersion(key, family, schemaMetrics, lateralityMode)
        ExecutionProfileAuthoringRepository(database).createProfile(
            ExecutionProfileAuthoringRequest(ExerciseId("exercise-$key"), "Temporal $key", "Temporal profile", true, version),
        )
        val dao = database.workoutDao()
        dao.upsertProfile(UserProfileEntity("temporal-user", "Temporal verifier", "kg", "none", 1, NOW, NOW))
        val sessionId = "session-$key"
        val sessionExerciseId = "session-exercise-$key"
        dao.upsertSessions(
            listOf(SessionEntity(sessionId, "cycle", "dev", "A", "routine", "completed", NOW, COMPLETE, null, null, false, null, "not_requested", null)),
        )
        dao.upsertSessionExercises(
            listOf(
                SessionExerciseEntity(
                    sessionExerciseId, sessionId, 0, "exercise-$key", "slot-$key", "Temporal $key", "principal",
                    version.executionProfileId.value, version.id.value, "Temporal profile", "A", true, 60,
                    CONTRACT, false, "completed", null, NOW, COMPLETE, SOURCE, null,
                ),
            ),
        )
        val setIds = List(setCount) { "set-$key-$it" }
        dao.upsertSets(setIds.mapIndexed { index, id -> SetRecordEntity(id, sessionExerciseId, index, null, false, "prescribed", NOW) })
        val workouts = RoomWorkoutRepository(database)
        val observationIds = setIds.mapIndexed { index, setId ->
            val start = START.plusSeconds(index * 30L)
            val end = END.plusSeconds(index * 30L)
            workouts.saveObservation(
                sessionExerciseId = sessionExerciseId,
                setId = setId,
                laterality = laterality,
                values = values,
                source = SOURCE,
                startedAt = start,
                endedAt = end,
                timingQuality = TimingQuality.USER_ACTION_BOUND,
                completedAt = end,
            ).id
        }
        return Fixture(sessionId, sessionExerciseId, setIds, observationIds)
    }

    private fun profileVersion(
        key: String,
        family: MetricFamily,
        metrics: List<SchemaMetric>,
        lateralityMode: LateralityMode,
    ) = ExecutionProfileVersion(
        id = ExecutionProfileVersionId("temporal-profile-$key:v1"),
        executionProfileId = ExecutionProfileId("temporal-profile-$key"),
        version = 1,
        metricFamily = family,
        schema = PerformanceSchema("temporal-schema-$key:v1", 1, family, metrics, SOURCE),
        equipment = EquipmentProfile("temporal-$key", "developer_verification"),
        resistanceModel = ResistanceModel(
            "temporal-resistance-$key-v1",
            if (family == MetricFamily.DYNAMIC_RESISTANCE) ResistanceSemantics.EXTERNAL else ResistanceSemantics.NONE,
            0.0,
            if (family == MetricFamily.DYNAMIC_RESISTANCE) 1.0 else 0.0,
            0.0,
        ),
        entryBasis = EntryBasis.TOTAL,
        implementCount = null,
        lateralityMode = lateralityMode,
        romClass = null,
        techniqueClass = SOURCE,
        resistanceCurveClass = null,
        movementPattern = key,
        jointActions = emptyList(),
        kineticChain = null,
        contractionType = null,
        gripSupportConstraints = emptyList(),
        recruitment = RecruitmentProfile(
            RecruitmentProfileVersionId("temporal-recruitment-$key:v1"), 1, emptyList(), NOW, NOW, null, SOURCE, CONTRACT,
        ),
        createdAt = NOW,
        effectiveAt = NOW,
        supersededAt = null,
        provenance = SOURCE,
        modelVersion = CONTRACT,
    )

    private fun artifact(
        id: String,
        logicalKey: String,
        revision: Int = 1,
        state: SourceState = SourceState.AVAILABLE,
        supersedes: String? = null,
        bounds: EvidenceTimeBounds = EvidenceTimeBounds(START, TRACE_END, TimingQuality.SOURCE_REPORTED_BOUND),
    ) = ExternalRecordProvenance(
        id = id,
        logicalSourceKey = logicalKey,
        nativeRevision = revision,
        provider = "synthetic-device-verifier",
        dataOrigin = "dev.kian.mymettle.synthetic",
        sourceRecordType = "heart-rate-series",
        sourceRecordId = logicalKey,
        sourceClientRecordId = "$logicalKey-client",
        sourceClientRecordVersion = revision.toLong(),
        sourceDeviceManufacturer = "Synthetic",
        sourceDeviceModel = "N-BIO-6",
        sourceDeviceType = "watch",
        recordingMethod = "synthetic_fixture",
        sourceLastModifiedAt = RECORDED.plusSeconds(revision.toLong()),
        importedAt = RECORDED.plusSeconds(revision.toLong()),
        sourceBounds = bounds,
        sourceState = state,
        supersedesArtifactId = supersedes,
    )

    private fun pointTrace(
        id: String,
        metric: EvidenceMetricKey,
        samples: List<PointEvidenceSample>,
        role: EvidenceSemanticRole,
        supersedes: String? = null,
    ) = EvidenceTrace(
        id = id,
        metric = metric,
        representation = TemporalRepresentation.POINT_SERIES,
        intervalSemantics = IntervalValueSemantics.UNSPECIFIED,
        canonicalUnit = metric.canonicalUnit,
        quality = EvidenceQuality(EvidenceGranularity.TRACE, AcquisitionMethod.SENSOR_RECORDED),
        semanticRole = role,
        bounds = EvidenceTimeBounds(samples.first().timestamp, samples.last().timestamp, TimingQuality.SOURCE_REPORTED_BOUND),
        provenance = SOURCE,
        createdAt = RECORDED,
        recordedAt = RECORDED,
        supersedesTraceId = supersedes,
    )

    private fun intervalTrace(
        id: String,
        metric: EvidenceMetricKey,
        payload: EvidencePayload.Intervals,
        semantics: IntervalValueSemantics,
    ) = EvidenceTrace(
        id = id,
        metric = metric,
        representation = TemporalRepresentation.INTERVAL_SERIES,
        intervalSemantics = semantics,
        canonicalUnit = metric.canonicalUnit,
        quality = EvidenceQuality(EvidenceGranularity.INTERVAL, AcquisitionMethod.DEVICE_DERIVED),
        semanticRole = metric.defaultRole,
        bounds = EvidenceTimeBounds(payload.samples.first().startedAt, payload.samples.last().endedAt, TimingQuality.SOURCE_REPORTED_BOUND),
        provenance = SOURCE,
        createdAt = RECORDED,
        recordedAt = RECORDED,
    )

    private fun chunk(
        id: String,
        trace: EvidenceTrace,
        payload: EvidencePayload,
        artifactId: String? = null,
        ordinal: Int = 0,
    ): EvidenceTraceChunk {
        val bytes = TemporalEvidenceCodec.encode(payload)
        return EvidenceTraceChunk(
            id = id,
            traceId = trace.id,
            sourceArtifactId = artifactId,
            ordinal = ordinal,
            sourceBounds = payload.bounds(),
            payload = payload,
            encodingVersion = TemporalEvidenceCodec.ENCODING_VERSION,
            payloadSha256 = TemporalEvidenceCodec.sha256(bytes),
            createdAt = RECORDED,
        )
    }

    private fun EvidencePayload.bounds(): EvidenceTimeBounds = when (this) {
        is EvidencePayload.Points -> EvidenceTimeBounds(
            samples.first().timestamp,
            samples.last().timestamp,
            TimingQuality.SOURCE_REPORTED_BOUND,
        )
        is EvidencePayload.Intervals -> EvidenceTimeBounds(
            samples.first().startedAt,
            samples.last().endedAt,
            TimingQuality.SOURCE_REPORTED_BOUND,
        )
        is EvidencePayload.Route -> EvidenceTimeBounds(
            samples.first().timestamp,
            samples.last().timestamp,
            TimingQuality.SOURCE_REPORTED_BOUND,
        )
    }

    private fun canonicalPointSamples() = listOf(
        PointEvidenceSample(Instant.parse("2026-08-24T08:31:02.100000001Z"), 122.0),
        PointEvidenceSample(Instant.parse("2026-08-24T08:31:05.700000002Z"), 134.0),
        PointEvidenceSample(Instant.parse("2026-08-24T08:31:10.000000003Z"), 238.0),
        PointEvidenceSample(Instant.parse("2026-08-24T08:41:00.000000004Z"), 148.0),
    )

    private fun scalar(metric: PerformanceMetric, value: Double, unit: UnitId, acquisition: AcquisitionMethod) =
        PerformanceMetricValue(
            metric = metric,
            entered = Quantity(value, unit),
            evidenceQuality = EvidenceQuality(EvidenceGranularity.SUMMARY, acquisition),
        )

    private fun traceScope(workoutSessionId: String) = TraceScopeLinks(workoutSessionIds = setOf(workoutSessionId))

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    private fun Throwable.conciseMessage(): String = buildString {
        append(this@conciseMessage::class.java.simpleName)
        this@conciseMessage.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
        cause?.takeIf { it !== this@conciseMessage }?.message?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }

    private fun expect(condition: Boolean, message: () -> String) {
        if (!condition) throw IllegalStateException(message())
    }

    private data class Fixture(
        val sessionId: String,
        val sessionExerciseId: String,
        val setIds: List<String>,
        val observationIds: List<String>,
    )

    private companion object {
        const val CONTRACT = "n-bio-6-temporal-verifier-v1"
        const val SOURCE = "n-bio-6-temporal-device-verification"
        const val NOW = "2026-08-24T08:30:00Z"
        const val COMPLETE = "2026-08-24T09:00:00Z"
        val START: Instant = Instant.parse("2026-08-24T08:31:00.123456789Z")
        val END: Instant = Instant.parse("2026-08-24T08:31:20.987654321Z")
        val TRACE_END: Instant = Instant.parse("2026-08-24T08:42:00.000000004Z")
        val RECORDED: Instant = Instant.parse("2026-08-24T09:05:00.000000007Z")
    }
}
