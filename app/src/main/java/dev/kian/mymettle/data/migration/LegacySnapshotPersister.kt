package dev.kian.mymettle.data.migration

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ExerciseSetupMediaEntity
import dev.kian.mymettle.data.local.entity.ProgrammeModeConstraintEntity
import dev.kian.mymettle.data.local.entity.ProgrammeTargetEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseTargetEntity
import dev.kian.mymettle.data.local.entity.SessionTargetEntity

/**
 * Shared Room write path for the production Lite importer and the isolated developer verifier.
 * External artifacts (settings and setup-photo files) deliberately remain outside this boundary.
 */
internal object LegacySnapshotPersister {
    suspend fun persist(
        database: MyMettleDatabase,
        snapshot: LegacyImportSnapshot,
        recruitment: List<RecruitmentAllocationEntity>,
        programmeTargets: List<ProgrammeTargetEntity>,
        programmeConstraints: List<ProgrammeModeConstraintEntity>,
        sessionTargets: List<SessionTargetEntity>,
        sessionExerciseTargets: List<SessionExerciseTargetEntity>,
        setupMedia: List<ExerciseSetupMediaEntity>,
    ) = database.withTransaction {
        val dao = database.workoutDao()
        dao.upsertProfile(snapshot.profile)
        dao.upsertBodyMeasurements(snapshot.bodyMeasurements)
        dao.upsertExercises(snapshot.exercises)
        dao.upsertExerciseMemory(snapshot.exerciseMemory)
        dao.upsertExecutionProfiles(snapshot.executionProfiles)
        dao.insertPerformanceSchemas(snapshot.performanceSchemas)
        dao.insertPerformanceSchemaMetrics(snapshot.performanceSchemaMetrics)
        dao.insertRecruitmentProfileVersions(snapshot.recruitmentProfileVersions)
        dao.insertExecutionProfileVersions(snapshot.executionProfileVersions)
        dao.upsertCues(snapshot.cues)
        dao.upsertCommonMistakes(snapshot.commonMistakes)
        dao.upsertSubstitutions(snapshot.substitutions)
        dao.insertRecruitmentAllocations(recruitment)
        dao.upsertSetupMedia(setupMedia)
        dao.upsertRoutineVersions(snapshot.routineVersions)
        dao.upsertRoutineSlots(snapshot.routineSlots)
        dao.upsertRoutineMetricTargets(snapshot.routineMetricTargets)
        dao.upsertProgrammeTargets(programmeTargets)
        dao.upsertProgrammeModeConstraints(programmeConstraints)
        dao.upsertTrainingCycles(snapshot.trainingCycles)
        dao.upsertCompletedDays(snapshot.completedDays)
        dao.upsertSessions(snapshot.sessions)
        dao.upsertSessionTargets(sessionTargets)
        dao.upsertSessionExercises(snapshot.sessionExercises)
        dao.upsertSessionExerciseTargets(sessionExerciseTargets)
        dao.upsertSessionSetPrescriptions(snapshot.sessionSetPrescriptions)
        dao.upsertSessionMetricTargets(snapshot.sessionMetricTargets)
        dao.upsertSets(snapshot.sets)
        dao.insertSetObservations(snapshot.setObservations)
        dao.insertSetMetricValues(snapshot.setMetricValues)
        dao.upsertSetDraftMetricValues(snapshot.setDraftMetricValues)
        dao.upsertReflections(snapshot.reflections)
        dao.upsertHealthObservations(snapshot.healthObservations)
        dao.upsertHealthIntegrationState(snapshot.healthIntegration)
        dao.upsertAppState(snapshot.appState)
    }
}
