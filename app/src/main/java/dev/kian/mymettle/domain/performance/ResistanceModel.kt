package dev.kian.mymettle.domain.performance

import dev.kian.mymettle.domain.exercise.EntryBasis

enum class ResistanceSemantics(val storageValue: String) {
    EXTERNAL("external"),
    ASSISTANCE("assistance"),
    BODYWEIGHT("bodyweight"),
    BODYWEIGHT_PLUS_EXTERNAL("bodyweight_plus_external"),
    NONE("none"),
    DEVICE_ORDINAL("device_ordinal")
}

/** Versioned modelling assumption. Raw body mass/load/assistance remain persistence truth. */
data class ResistanceModel(
    val modelVersion: String,
    val semantics: ResistanceSemantics,
    val bodyweightCoefficient: Double,
    val externalLoadCoefficient: Double,
    val assistanceCoefficient: Double,
) {
    init {
        require(modelVersion.isNotBlank())
        require(bodyweightCoefficient >= 0.0)
        require(externalLoadCoefficient >= 0.0)
        require(assistanceCoefficient >= 0.0)
    }
}

data class ResistanceInputs(
    val bodyMassKg: Double? = null,
    val externalLoadKg: Double? = null,
    val assistanceKg: Double? = null,
)

data class ResolvedResistance(
    val coordinate: Double,
    val unit: UnitId,
    val modelVersion: String,
)

object ResistanceResolver {
    fun resolve(model: ResistanceModel, inputs: ResistanceInputs): ResolvedResistance? {
        if (model.semantics == ResistanceSemantics.NONE) return null
        if (model.semantics == ResistanceSemantics.DEVICE_ORDINAL) {
            throw IllegalArgumentException("Device ordinal resistance must use its recorded machine-level coordinate.")
        }
        if (model.bodyweightCoefficient > 0.0 && inputs.bodyMassKg == null) return null
        if (model.externalLoadCoefficient > 0.0 && inputs.externalLoadKg == null) return null
        if (model.assistanceCoefficient > 0.0 && inputs.assistanceKg == null) return null
        val coordinate = model.bodyweightCoefficient * (inputs.bodyMassKg ?: 0.0) +
            model.externalLoadCoefficient * (inputs.externalLoadKg ?: 0.0) -
            model.assistanceCoefficient * (inputs.assistanceKg ?: 0.0)
        return ResolvedResistance(coordinate.coerceAtLeast(0.0), UnitId.KILOGRAM, model.modelVersion)
    }

    fun totalImplementMassForBookkeeping(
        enteredLoadKg: Double,
        entryBasis: EntryBasis,
        implementCount: Int?,
    ): Double? = when (entryBasis) {
        EntryBasis.TOTAL -> enteredLoadKg
        EntryBasis.PER_HAND -> implementCount?.let { enteredLoadKg * it }
        EntryBasis.PER_SIDE -> null // active-side meaning must not be destroyed by totalisation
    }
}
