package dev.kian.mymettle.profile

import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.BodyMeasurementEntity
import java.time.Instant
import java.util.UUID

data class BodyProfile(
    val weightKg: Double?,
    val heightCm: Double?,
)

class ProfileRepository(private val database: MyMettleDatabase) {
    suspend fun current(): BodyProfile {
        val measurement = database.workoutDao().latestBodyMeasurement()
        return BodyProfile(
            weightKg = measurement?.weightKg,
            heightCm = measurement?.heightCm,
        )
    }

    suspend fun save(weightKg: Double, heightCm: Double?): BodyProfile {
        require(weightKg in 35.0..250.0) { "Enter a weight between 35 and 250 kg." }
        require(heightCm == null || heightCm in 100.0..250.0) {
            "Enter a height between 100 and 250 cm."
        }
        val now = Instant.now().toString()
        database.workoutDao().upsertBodyMeasurements(
            listOf(
                BodyMeasurementEntity(
                    id = "manual-${UUID.randomUUID()}",
                    recordedAt = now,
                    weightKg = weightKg,
                    heightCm = heightCm,
                    source = "manual",
                    sourceRecordId = null,
                ),
            ),
        )
        return BodyProfile(weightKg, heightCm)
    }
}
