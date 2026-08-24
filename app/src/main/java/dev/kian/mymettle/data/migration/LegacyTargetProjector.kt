package dev.kian.mymettle.data.migration

import dev.kian.mymettle.data.local.entity.ProgrammeTargetEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.ExecutionProfileVersionEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseTargetEntity
import dev.kian.mymettle.data.local.entity.SessionTargetEntity

data class LegacyTargetProjection(
    val programmeTargets: List<ProgrammeTargetEntity>,
    val sessionTargets: List<SessionTargetEntity>,
    val sessionExerciseTargets: List<SessionExerciseTargetEntity>,
)

/**
 * One-time compatibility projection from the Legacy exercise-list programme.
 *
 * Legacy had no independent target intent. We therefore project only PRIME recruitment from its
 * pinned routine assignments, label the provenance explicitly, and leave desired stimulus null.
 * The persisted result is independent: later target editing/versioning does not read recruitment
 * as programme truth.
 */
object LegacyTargetProjector {
    const val SOURCE = "lite-reviewed-prime-target-compatibility-v2"

    fun project(
        snapshot: LegacyImportSnapshot,
        recruitment: List<RecruitmentAllocationEntity>,
    ): LegacyTargetProjection = project(
        routineSlots = snapshot.routineSlots,
        executionProfiles = snapshot.executionProfiles,
        executionProfileVersions = snapshot.executionProfileVersions,
        sessions = snapshot.sessions,
        sessionExercises = snapshot.sessionExercises,
        recruitment = recruitment,
    )

    fun project(
        routineSlots: List<RoutineSlotEntity>,
        executionProfiles: List<ExerciseExecutionProfileEntity>,
        executionProfileVersions: List<ExecutionProfileVersionEntity>,
        sessions: List<SessionEntity>,
        sessionExercises: List<SessionExerciseEntity>,
        recruitment: List<RecruitmentAllocationEntity>,
    ): LegacyTargetProjection {
        val defaultProfileByExercise = executionProfiles
            .filter { it.isDefault }
            .associateBy { it.exerciseId }
        val currentVersionByProfile = executionProfileVersions.groupBy { it.executionProfileId }.mapValues { (_, values) ->
            values.filter { it.supersededAt == null }.singleOrNull() ?: values.maxBy { it.version }
        }
        val recruitmentByVersion = recruitment.groupBy { it.recruitmentProfileVersionId }

        data class TargetKey(
            val routineVersionId: String,
            val daySymbol: String,
            val segmentId: String,
        )

        val targetPriority = linkedMapOf<TargetKey, Double>()
        routineSlots.forEach { slot ->
            val profile = defaultProfileByExercise[slot.exerciseId]
                ?: throw LegacyImportException("Exercise ${slot.exerciseId} has no default execution profile.")
            val version = currentVersionByProfile[profile.id]
                ?: throw LegacyImportException("Execution profile ${profile.id} has no semantic version.")
            recruitmentByVersion[version.recruitmentProfileVersionId]
                .orEmpty()
                .filter { it.role.equals("prime", ignoreCase = true) }
                .forEach { allocation ->
                    val key = TargetKey(slot.routineVersionId, slot.daySymbol, allocation.muscleSegmentId)
                    val priority = slot.importance.toLegacyTargetPriority()
                    targetPriority[key] = maxOf(targetPriority[key] ?: 0.0, priority)
                }
        }

        val programmeTargets = targetPriority.map { (key, priority) ->
            ProgrammeTargetEntity(
                id = programmeTargetId(key.routineVersionId, key.daySymbol, key.segmentId),
                routineVersionId = key.routineVersionId,
                daySymbol = key.daySymbol,
                muscleSegmentId = key.segmentId,
                priority = priority,
                desiredStimulus = null,
                source = SOURCE,
            )
        }
        val programmeTargetsBySessionKey = programmeTargets.groupBy { it.routineVersionId to it.daySymbol }

        val sessionTargets = sessions.flatMap { session ->
            programmeTargetsBySessionKey[session.routineVersionId to session.daySymbol].orEmpty().map { target ->
                SessionTargetEntity(
                    id = sessionTargetId(session.id, target.id),
                    sessionId = session.id,
                    programmeTargetId = target.id,
                    muscleSegmentId = target.muscleSegmentId,
                    priority = target.priority,
                    desiredStimulus = target.desiredStimulus,
                    source = target.source,
                    included = true,
                    resolvedPriority = target.priority,
                    resolutionModelVersion = LEGACY_SESSION_RESOLUTION_MODEL,
                )
            }
        }
        val sessionById = sessions.associateBy { it.id }
        val targetsBySession = sessionTargets.groupBy { it.sessionId }

        val sessionExerciseTargets = sessionExercises.flatMap { sessionExercise ->
            if (!sessionExercise.prescriptionIncluded) return@flatMap emptyList()

            val session = sessionById[sessionExercise.sessionId]
                ?: throw LegacyImportException("Session exercise ${sessionExercise.id} references a missing session.")
            val version = executionProfileVersions.firstOrNull { it.id == sessionExercise.executionProfileVersionId }
                ?: throw LegacyImportException("Session exercise ${sessionExercise.id} references a missing execution version.")
            val recruitedSegments = recruitmentByVersion[version.recruitmentProfileVersionId]
                .orEmpty()
                .filterNot { it.role.equals("stabiliser", ignoreCase = true) }
                .mapTo(mutableSetOf()) { it.muscleSegmentId }
            targetsBySession[session.id]
                .orEmpty()
                .filter { it.muscleSegmentId in recruitedSegments }
                .map { target -> SessionExerciseTargetEntity(sessionExercise.id, target.id) }
        }

        return LegacyTargetProjection(
            programmeTargets = programmeTargets,
            sessionTargets = sessionTargets,
            sessionExerciseTargets = sessionExerciseTargets,
        )
    }

    private fun String.toLegacyTargetPriority(): Double = when (lowercase()) {
        "principal" -> 1.0
        "core" -> 0.7
        "accessory" -> 0.4
        else -> 0.7
    }

    private fun programmeTargetId(routineVersionId: String, day: String, segmentId: String): String =
        "programme_target:$routineVersionId:$day:$segmentId"

    private fun sessionTargetId(sessionId: String, programmeTargetId: String): String =
        "session_target:$sessionId:$programmeTargetId"

    private const val LEGACY_SESSION_RESOLUTION_MODEL = "legacy-session-target-projection-v1"
}
