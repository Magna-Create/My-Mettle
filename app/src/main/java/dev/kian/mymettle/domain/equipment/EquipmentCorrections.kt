package dev.kian.mymettle.domain.equipment

/**
 * Stable canonical dependency ids used by derived 7F consumers.
 *
 * The id names the canonical concept, not one correction row. A later correction to the same
 * concept therefore invalidates the same dependency root without touching unrelated raw evidence.
 */
object EquipmentCanonicalDependencyId {
    fun sessionActualEquipment(sessionExerciseId: String): String =
        canonical("session_actual_equipment", sessionExerciseId)

    fun observationEquipmentOverride(observationId: String): String =
        canonical("observation_equipment_override", observationId)

    fun observationLoadSemantics(observationId: String): String =
        canonical("observation_load_semantics", observationId)

    private fun canonical(kind: String, id: String): String {
        require(id.isNotBlank()) { "Equipment canonical dependency id cannot be blank." }
        return "n-bio-7f:canonical:$kind:$id"
    }
}

data class EquipmentInvalidationImpact(
    val sourceDependencyIds: Set<String>,
) {
    init {
        require(sourceDependencyIds.isNotEmpty())
        require(sourceDependencyIds.all { it.isNotBlank() })
    }
}

/** Append-only correction to the canonical actual equipment for one session-exercise occurrence. */
data class SessionExerciseEquipmentBindingCorrection(
    val id: String,
    val sessionExerciseId: String,
    val version: Int,
    val previousEquipmentId: EquipmentId?,
    val correctedEquipmentId: EquipmentId?,
    val source: String,
    val reason: String,
    val correctedAt: String,
) {
    init {
        require(id.isNotBlank() && sessionExerciseId.isNotBlank())
        require(version > 0)
        require(previousEquipmentId != correctedEquipmentId) { "A correction must change the canonical value." }
        require(source.isNotBlank() && reason.isNotBlank() && correctedAt.isNotBlank())
    }
}

/**
 * Append-only correction to an observation-level equipment override.
 * A null corrected value retracts the override so resolution falls back to session actual use.
 */
data class ObservationEquipmentOverrideCorrection(
    val id: String,
    val observationId: String,
    val version: Int,
    val previousEquipmentId: EquipmentId?,
    val correctedEquipmentId: EquipmentId?,
    val source: String,
    val reason: String,
    val correctedAt: String,
) {
    init {
        require(id.isNotBlank() && observationId.isNotBlank())
        require(version > 0)
        require(previousEquipmentId != correctedEquipmentId) { "A correction must change the canonical value." }
        require(source.isNotBlank() && reason.isNotBlank() && correctedAt.isNotBlank())
    }
}

/**
 * Append-only correction to external-load accounting meaning.
 * Null is an explicit retraction to unknown; it never invents a replacement convention.
 */
data class ObservationLoadSemanticsCorrection(
    val id: String,
    val observationId: String,
    val version: Int,
    val previousExternalLoadAccounting: ExternalLoadAccounting?,
    val correctedExternalLoadAccounting: ExternalLoadAccounting?,
    val source: String,
    val reason: String,
    val correctedAt: String,
) {
    init {
        require(id.isNotBlank() && observationId.isNotBlank())
        require(version > 0)
        require(previousExternalLoadAccounting != correctedExternalLoadAccounting) {
            "A correction must change the canonical value."
        }
        require(source.isNotBlank() && reason.isNotBlank() && correctedAt.isNotBlank())
    }
}
