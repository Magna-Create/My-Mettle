package dev.kian.mymettle.domain.equipment

@JvmInline
value class EquipmentId(val value: String) {
    init {
        require(value.isNotBlank()) { "EquipmentId cannot be blank." }
    }
}

enum class EquipmentKind(val storageValue: String) {
    BARBELL("barbell"),
    DUMBBELL("dumbbell"),
    KETTLEBELL("kettlebell"),
    LOADED_IMPLEMENT("loaded_implement"),
    SMITH_MACHINE("smith_machine"),
    CABLE_SYSTEM("cable_system"),
    SELECTORISED_MACHINE("selectorised_machine"),
    PLATE_LOADED_LEVER("plate_loaded_lever"),
    SLED_RAIL("sled_rail"),
    ASSISTED_RESISTANCE("assisted_resistance"),
    OTHER("other");

    companion object {
        fun fromStorage(value: String): EquipmentKind = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported equipment kind: $value")
    }
}

enum class EquipmentFactType(val storageValue: String, val valueKind: EquipmentFactValueKind) {
    EQUIPMENT_KIND("equipment_kind", EquipmentFactValueKind.TEXT),
    MANUFACTURER("manufacturer", EquipmentFactValueKind.TEXT),
    MODEL("model", EquipmentFactValueKind.TEXT),
    FAMILY("family", EquipmentFactValueKind.TEXT),
    IMPLEMENT_MASS("implement_mass", EquipmentFactValueKind.SCALAR),
    STARTING_RESISTANCE("starting_resistance", EquipmentFactValueKind.SCALAR),
    MECHANICAL_RATIO("mechanical_ratio", EquipmentFactValueKind.SCALAR),
    RAIL_ANGLE("rail_angle", EquipmentFactValueKind.SCALAR),
    ARM_COUPLING("arm_coupling", EquipmentFactValueKind.TEXT),
    LOADING_MECHANISM("loading_mechanism", EquipmentFactValueKind.TEXT),
    LOCAL_DISPLAY_SEMANTICS("local_display_semantics", EquipmentFactValueKind.TEXT),
    ALLOWED_SELECTIONS("allowed_selections", EquipmentFactValueKind.TEXT);

    companion object {
        fun fromStorage(value: String): EquipmentFactType = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported equipment fact type: $value")
    }
}

enum class EquipmentFactValueKind(val storageValue: String) {
    TEXT("text"),
    SCALAR("scalar")
}

enum class EquipmentFactUnit(val storageValue: String) {
    KILOGRAM("kg"),
    RATIO("ratio"),
    DEGREE("deg")
}

enum class EquipmentFactProvenance(val storageValue: String) {
    OEM_DECLARED_SPECIFICATION("oem_declared_specification"),
    USER_CONFIRMED_CONFIGURATION("user_confirmed_configuration"),
    DETERMINISTIC_DERIVATION("deterministic_derivation"),
    MEASURED_INSTANCE_CALIBRATION("measured_instance_calibration"),
    UNKNOWN("unknown");

    companion object {
        fun fromStorage(value: String): EquipmentFactProvenance = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported equipment fact provenance: $value")
    }
}

enum class ExternalLoadAccounting(val storageValue: String) {
    INCLUSIVE_EXTERNAL_LOAD("inclusive_external_load"),
    ADDED_EXTERNAL_LOAD_ONLY("added_external_load_only");

    companion object {
        fun fromStorage(value: String): ExternalLoadAccounting = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported external-load accounting: $value")
    }
}

sealed interface EquipmentFactValue {
    data class Text(val value: String) : EquipmentFactValue {
        init {
            require(value.isNotBlank()) { "Equipment text fact cannot be blank." }
        }
    }

    data class Scalar(
        val value: Double,
        val unit: EquipmentFactUnit,
    ) : EquipmentFactValue {
        init {
            require(value.isFinite()) { "Equipment scalar fact must be finite." }
        }
    }
}

data class EquipmentInstance(
    val id: EquipmentId,
    val localLabel: String?,
    val source: String,
    val createdAt: String,
    val archivedAt: String?,
) {
    init {
        require(localLabel == null || localLabel.isNotBlank())
        require(source.isNotBlank())
        require(createdAt.isNotBlank())
    }
}

data class EquipmentFactVersion(
    val id: String,
    val equipmentId: EquipmentId,
    val factType: EquipmentFactType,
    val version: Int,
    val value: EquipmentFactValue,
    val scope: String?,
    val provenance: EquipmentFactProvenance,
    val provenanceReference: String?,
    val quality: String?,
    val createdAt: String,
    val effectiveAt: String,
    val supersededAt: String?,
) {
    init {
        require(id.isNotBlank())
        require(version > 0)
        require(scope == null || scope.isNotBlank())
        require(provenanceReference == null || provenanceReference.isNotBlank())
        require(quality == null || quality.isNotBlank())
        require(createdAt.isNotBlank() && effectiveAt.isNotBlank())
        require(
            when (factType.valueKind) {
                EquipmentFactValueKind.TEXT -> value is EquipmentFactValue.Text
                EquipmentFactValueKind.SCALAR -> value is EquipmentFactValue.Scalar
            },
        ) { "${factType.storageValue} requires ${factType.valueKind.storageValue} value semantics." }
        if (value is EquipmentFactValue.Scalar) {
            when (factType) {
                EquipmentFactType.IMPLEMENT_MASS,
                EquipmentFactType.STARTING_RESISTANCE -> {
                    require(value.unit == EquipmentFactUnit.KILOGRAM)
                    require(value.value >= 0.0)
                }
                EquipmentFactType.MECHANICAL_RATIO -> {
                    require(value.unit == EquipmentFactUnit.RATIO)
                    require(value.value > 0.0)
                }
                EquipmentFactType.RAIL_ANGLE -> require(value.unit == EquipmentFactUnit.DEGREE)
                else -> error("Text equipment fact ${factType.storageValue} cannot carry a scalar value.")
            }
        }
    }
}

data class ObservationLoadSemantics(
    val observationId: String,
    val externalLoadAccounting: ExternalLoadAccounting,
    val source: String,
    val recordedAt: String,
) {
    init {
        require(observationId.isNotBlank())
        require(source.isNotBlank())
        require(recordedAt.isNotBlank())
    }
}
