package dev.kian.mymettle.domain.performance

import kotlin.math.abs

enum class QuantityDimension {
    MASS,
    TIME,
    DISTANCE,
    SPEED,
    PACE,
    GRADE,
    POWER,
    RATE,
    COUNT,
    ORDINAL,
}

/** Stable persisted identifiers. Labels remain a presentation concern. */
enum class UnitId(
    val storageValue: String,
    val dimension: QuantityDimension,
    val canonicalUnit: Boolean = false,
) {
    KILOGRAM("kg", QuantityDimension.MASS, true),
    POUND("lb", QuantityDimension.MASS),
    SECOND("s", QuantityDimension.TIME, true),
    MINUTE("min", QuantityDimension.TIME),
    METRE("m", QuantityDimension.DISTANCE, true),
    KILOMETRE("km", QuantityDimension.DISTANCE),
    MILE("mi", QuantityDimension.DISTANCE),
    METRES_PER_SECOND("m/s", QuantityDimension.SPEED, true),
    KILOMETRES_PER_HOUR("km/h", QuantityDimension.SPEED),
    MILES_PER_HOUR("mph", QuantityDimension.SPEED),
    SECONDS_PER_METRE("s/m", QuantityDimension.PACE, true),
    MINUTES_PER_KILOMETRE("min/km", QuantityDimension.PACE),
    MINUTES_PER_MILE("min/mi", QuantityDimension.PACE),
    FRACTION("fraction", QuantityDimension.GRADE, true),
    PERCENT("percent", QuantityDimension.GRADE),
    WATT("W", QuantityDimension.POWER, true),
    EVENTS_PER_MINUTE("events/min", QuantityDimension.RATE, true),
    REPETITION("rep", QuantityDimension.COUNT, true),
    STEP("step", QuantityDimension.COUNT, true),
    FLOOR("floor", QuantityDimension.COUNT, true),
    MACHINE_LEVEL("machine_level", QuantityDimension.ORDINAL, true);

    companion object {
        fun fromStorage(value: String): UnitId = entries.firstOrNull {
            it.storageValue.equals(value.trim(), ignoreCase = true)
        } ?: when (value.trim().lowercase()) {
            "kgs" -> KILOGRAM
            "lbs", "pound", "pounds" -> POUND
            "seconds", "second" -> SECOND
            "minutes", "minute" -> MINUTE
            "metres", "meter", "meters" -> METRE
            "kilometres", "kilometer", "kilometers" -> KILOMETRE
            "miles" -> MILE
            "watts", "watt" -> WATT
            "reps", "repetition", "repetitions" -> REPETITION
            "steps" -> STEP
            "floors" -> FLOOR
            "level" -> MACHINE_LEVEL
            else -> throw IllegalArgumentException("Unsupported unit id: $value")
        }
    }
}

data class Quantity(
    val value: Double,
    val unit: UnitId,
) {
    init {
        require(value.isFinite()) { "Quantity must be finite." }
    }
}

object UnitConverter {
    private const val POUNDS_PER_KILOGRAM = 2.2046226218487757
    private const val METRES_PER_MILE = 1609.344
    private const val METRES_PER_KILOMETRE = 1000.0
    private const val SECONDS_PER_MINUTE = 60.0
    private const val METRES_PER_SECOND_PER_MPH = 0.44704
    private const val METRES_PER_SECOND_PER_KPH = 1.0 / 3.6

    fun convert(quantity: Quantity, to: UnitId): Quantity {
        require(quantity.unit.dimension == to.dimension) {
            "Cannot convert ${quantity.unit.storageValue} to ${to.storageValue}."
        }
        if (quantity.unit == to) return quantity
        if (quantity.unit.dimension == QuantityDimension.ORDINAL) {
            throw IllegalArgumentException("Ordinal machine levels are profile-local and cannot be converted.")
        }
        if (quantity.unit.dimension == QuantityDimension.COUNT) {
            throw IllegalArgumentException("Count units are metric-specific and cannot be converted.")
        }
        val canonical = toCanonical(quantity)
        return Quantity(fromCanonical(canonical.value, to), to)
    }

    fun canonical(quantity: Quantity): Quantity {
        if (quantity.unit.dimension == QuantityDimension.ORDINAL) return quantity
        return toCanonical(quantity)
    }

    fun roundTripStable(quantity: Quantity, intermediate: UnitId, tolerance: Double = 1e-9): Boolean {
        val result = convert(convert(quantity, intermediate), quantity.unit)
        return abs(result.value - quantity.value) <= tolerance * maxOf(1.0, abs(quantity.value))
    }

    private fun toCanonical(quantity: Quantity): Quantity = Quantity(
        value = when (quantity.unit) {
            UnitId.KILOGRAM,
            UnitId.SECOND,
            UnitId.METRE,
            UnitId.METRES_PER_SECOND,
            UnitId.SECONDS_PER_METRE,
            UnitId.FRACTION,
            UnitId.WATT,
            UnitId.EVENTS_PER_MINUTE,
            UnitId.REPETITION,
            UnitId.STEP,
            UnitId.FLOOR,
            UnitId.MACHINE_LEVEL -> quantity.value
            UnitId.POUND -> quantity.value / POUNDS_PER_KILOGRAM
            UnitId.MINUTE -> quantity.value * SECONDS_PER_MINUTE
            UnitId.KILOMETRE -> quantity.value * METRES_PER_KILOMETRE
            UnitId.MILE -> quantity.value * METRES_PER_MILE
            UnitId.KILOMETRES_PER_HOUR -> quantity.value * METRES_PER_SECOND_PER_KPH
            UnitId.MILES_PER_HOUR -> quantity.value * METRES_PER_SECOND_PER_MPH
            UnitId.MINUTES_PER_KILOMETRE -> quantity.value * SECONDS_PER_MINUTE / METRES_PER_KILOMETRE
            UnitId.MINUTES_PER_MILE -> quantity.value * SECONDS_PER_MINUTE / METRES_PER_MILE
            UnitId.PERCENT -> quantity.value / 100.0
        },
        unit = canonicalUnitFor(quantity.unit.dimension, quantity.unit),
    )

    private fun fromCanonical(value: Double, to: UnitId): Double = when (to) {
        UnitId.KILOGRAM,
        UnitId.SECOND,
        UnitId.METRE,
        UnitId.METRES_PER_SECOND,
        UnitId.SECONDS_PER_METRE,
        UnitId.FRACTION,
        UnitId.WATT,
        UnitId.EVENTS_PER_MINUTE,
        UnitId.REPETITION,
        UnitId.STEP,
        UnitId.FLOOR,
        UnitId.MACHINE_LEVEL -> value
        UnitId.POUND -> value * POUNDS_PER_KILOGRAM
        UnitId.MINUTE -> value / SECONDS_PER_MINUTE
        UnitId.KILOMETRE -> value / METRES_PER_KILOMETRE
        UnitId.MILE -> value / METRES_PER_MILE
        UnitId.KILOMETRES_PER_HOUR -> value / METRES_PER_SECOND_PER_KPH
        UnitId.MILES_PER_HOUR -> value / METRES_PER_SECOND_PER_MPH
        UnitId.MINUTES_PER_KILOMETRE -> value * METRES_PER_KILOMETRE / SECONDS_PER_MINUTE
        UnitId.MINUTES_PER_MILE -> value * METRES_PER_MILE / SECONDS_PER_MINUTE
        UnitId.PERCENT -> value * 100.0
    }

    private fun canonicalUnitFor(dimension: QuantityDimension, countUnit: UnitId): UnitId = when (dimension) {
        QuantityDimension.MASS -> UnitId.KILOGRAM
        QuantityDimension.TIME -> UnitId.SECOND
        QuantityDimension.DISTANCE -> UnitId.METRE
        QuantityDimension.SPEED -> UnitId.METRES_PER_SECOND
        QuantityDimension.PACE -> UnitId.SECONDS_PER_METRE
        QuantityDimension.GRADE -> UnitId.FRACTION
        QuantityDimension.POWER -> UnitId.WATT
        QuantityDimension.RATE -> UnitId.EVENTS_PER_MINUTE
        QuantityDimension.COUNT -> countUnit
        QuantityDimension.ORDINAL -> UnitId.MACHINE_LEVEL
    }
}
