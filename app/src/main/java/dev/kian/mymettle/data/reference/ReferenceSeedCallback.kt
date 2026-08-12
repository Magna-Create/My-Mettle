package dev.kian.mymettle.data.reference

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.kian.mymettle.domain.physiology.AbsoluteSharePolicy
import dev.kian.mymettle.domain.physiology.Estimate

class ReferenceSeedCallback(context: Context) : RoomDatabase.Callback() {
    private val appContext = context.applicationContext

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seed(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        seed(db)
    }

    private fun seed(db: SupportSQLiteDatabase) {
        val dataset = ReferenceAssetLoader.load(appContext.assets)
        if (db.contains(dataset)) return

        val ownsTransaction = !db.inTransaction()
        if (ownsTransaction) db.beginTransaction()
        try {
            upsert(db, dataset)
            if (ownsTransaction) db.setTransactionSuccessful()
        } finally {
            if (ownsTransaction) db.endTransaction()
        }
    }

    private fun upsert(db: SupportSQLiteDatabase, dataset: RuntimeReferenceDataset) {
        dataset.muscles.forEach { muscle ->
            db.upsertOrThrow("muscle", ContentValues().apply {
                put("id", muscle.id.value)
                put("name", muscle.name)
                put("region", muscle.region.name)
                put("unitKind", muscle.unitKind.name)
                put("lateralityModel", muscle.lateralityModel.name)
                put("instancePattern", muscle.instancePattern)
                put("verificationStatus", muscle.verificationStatus.name)
            })
            muscle.segments.forEach { segment ->
                db.upsertOrThrow("muscle_segment", ContentValues().apply {
                    put("id", segment.id.value)
                    put("muscleId", segment.muscleId.value)
                    put("name", segment.name)
                    put("segmentType", segment.type.name)
                    put("anatomicalStatus", segment.anatomicalStatus.name)
                    put("statePolicy", segment.statePolicy.name)
                    put("verificationStatus", segment.verificationStatus.name)
                })
            }
        }

        val profile = dataset.referenceProfile
        db.upsertOrThrow("reference_profile", ContentValues().apply {
            put("id", profile.id.value)
            put("version", profile.version)
            put("populationSex", profile.population.sex.name)
            put("populationAgeSummary", profile.population.ageSummary)
            put("populationDescription", profile.population.description)
            put("datasetVersion", profile.datasetVersion)
            put("modelVersion", profile.modelVersion)
        })

        profile.priors.forEach { prior ->
            val targetKind = if (prior.segmentId == null) "MUSCLE" else "SEGMENT"
            val targetId = prior.segmentId?.value ?: prior.muscleId.value
            db.upsertOrThrow("reference_physiology_prior", ContentValues().apply {
                put("id", "${profile.id.value}:${targetKind.lowercase()}:$targetId")
                put("profileId", profile.id.value)
                put("targetKind", targetKind)
                put("targetId", targetId)
                put("muscleId", prior.muscleId.value)
                put("segmentId", prior.segmentId?.value)
                putEstimate("volume", prior.volumeCm3)
                putEstimate("optimalFibreLength", prior.optimalFibreLengthMm)
                putEstimate("pennation", prior.pennationDeg)
                putEstimate("geometricPcsa", prior.geometricPcsaCm2)
                putEstimate("effectivePcsa", prior.effectivePcsaCm2)
                putEstimate("structuralCapacity", prior.structuralCapacityIndex)
                when (val share = prior.absoluteSharePolicy) {
                    is AbsoluteSharePolicy.Known -> {
                        put("absoluteShareKind", "KNOWN")
                        put("absoluteShareFraction", share.fraction)
                        putNull("absoluteShareUncertainty")
                    }
                    is AbsoluteSharePolicy.StructuralPrior -> {
                        put("absoluteShareKind", "STRUCTURAL_PRIOR")
                        put("absoluteShareFraction", share.fraction)
                        put("absoluteShareUncertainty", share.uncertainty.name)
                    }
                    AbsoluteSharePolicy.Latent -> {
                        put("absoluteShareKind", "LATENT")
                        putNull("absoluteShareFraction")
                        putNull("absoluteShareUncertainty")
                    }
                }
                put("availability", prior.availability)
                put("uncertaintyClass", prior.uncertaintyClass.name)
                put("selectionRule", prior.selectionRule)
            })
        }
    }
}

private fun SupportSQLiteDatabase.contains(dataset: RuntimeReferenceDataset): Boolean {
    val expectedSegments = dataset.muscles.sumOf { it.segments.size }
    val profile = dataset.referenceProfile
    val profileIsCurrent = query(
        "SELECT version, datasetVersion, modelVersion FROM reference_profile WHERE id = ? LIMIT 1",
        arrayOf(profile.id.value),
    ).use { cursor ->
        cursor.moveToFirst() &&
            cursor.getInt(0) == profile.version &&
            cursor.getString(1) == profile.datasetVersion &&
            cursor.getString(2) == profile.modelVersion
    }
    return profileIsCurrent &&
        rowCount("muscle") == dataset.muscles.size &&
        rowCount("muscle_segment") == expectedSegments &&
        rowCount("reference_physiology_prior") == profile.priors.size
}

private fun SupportSQLiteDatabase.rowCount(table: String): Int =
    query("SELECT COUNT(*) FROM $table").use { cursor ->
        check(cursor.moveToFirst()) { "Could not count runtime reference table $table." }
        cursor.getInt(0)
    }

private fun SupportSQLiteDatabase.upsertOrThrow(table: String, values: ContentValues) {
    val id = checkNotNull(values.getAsString("id")) { "Runtime reference row in $table has no id." }
    val updated = update(
        table,
        SQLiteDatabase.CONFLICT_ABORT,
        values,
        "id = ?",
        arrayOf(id),
    )
    if (updated == 0) {
        check(insert(table, SQLiteDatabase.CONFLICT_ABORT, values) != -1L) {
            "Failed to seed runtime reference table $table."
        }
    }
}

private fun ContentValues.putEstimate(prefix: String, estimate: Estimate<Double>?) {
    val valueSuffix = when {
        prefix == "volume" -> "Cm3"
        prefix == "optimalFibreLength" -> "Mm"
        prefix.endsWith("Pcsa") -> "Cm2"
        prefix == "pennation" -> "Deg"
        else -> "Index"
    }
    put("$prefix$valueSuffix", estimate?.value)
    put("${prefix}Uncertainty", estimate?.uncertainty)
    put("${prefix}SourceKind", estimate?.sourceKind?.name)
    put("${prefix}SourceId", estimate?.sourceId)
    put("${prefix}ModelVersion", estimate?.modelVersion)
}
