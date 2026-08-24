package dev.kian.mymettle.data.migration

/** Guards the boundary between factual Lite parsing and reviewed Native enrichment. */
object LegacyTranslationContract {
    fun requireActiveRecruitment(snapshot: LegacyImportSnapshot) {
        val activeExerciseIds = snapshot.routineSlots
            .filter { it.routineVersionId == snapshot.appState.currentRoutineVersionId }
            .mapTo(linkedSetOf()) { it.exerciseId }
        val profilesByExercise = snapshot.executionProfiles
            .filter { it.isDefault && !it.archived }
            .associateBy { it.exerciseId }
        val versionsByProfile = snapshot.executionProfileVersions
            .filter { it.supersededAt == null }
            .associateBy { it.executionProfileId }
        val translatedVersionIds = snapshot.translatedRecruitment
            .mapTo(hashSetOf()) { it.recruitmentProfileVersionId }

        val missing = activeExerciseIds.filter { exerciseId ->
            val profile = profilesByExercise[exerciseId]
            val version = profile?.let { versionsByProfile[it.id] }
            version == null || version.recruitmentProfileVersionId !in translatedVersionIds
        }
        if (missing.isNotEmpty()) {
            val names = snapshot.exercises.associate { it.id to it.name }
            throw LegacyImportException(
                "Native translation is incomplete. Current-routine exercises need reviewed " +
                    "independent recruitment profiles: ${missing.joinToString { names[it] ?: it }}.",
            )
        }
    }
}
