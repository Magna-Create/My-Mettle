package dev.kian.mymettle.library

import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.EquipmentProfile
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersion
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.exercise.RecruitmentAllocation
import dev.kian.mymettle.domain.exercise.RecruitmentProfile
import dev.kian.mymettle.domain.exercise.RecruitmentProfileVersionId
import dev.kian.mymettle.domain.exercise.RecruitmentRole
import dev.kian.mymettle.domain.exercise.RecruitmentSource
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceSchema
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.SchemaMetric
import dev.kian.mymettle.domain.performance.UnitId
import org.json.JSONArray
import org.json.JSONObject

data class ExerciseImportAssignedIdentity(
    val exerciseId: ExerciseId,
    val executionProfileId: ExecutionProfileId,
    val executionProfileVersionId: ExecutionProfileVersionId,
    val performanceSchemaId: String,
    val recruitmentProfileVersionId: RecruitmentProfileVersionId,
) {
    init { require(performanceSchemaId.isNotBlank()) }
}

data class RecruitmentAuthoringProvenance(
    val authorType: String,
    val evidenceStatus: String,
    val sourceReferences: List<String>,
    val biomechanicalBasis: String,
    val modelToolIdentity: String?,
) {
    init {
        require(authorType in setOf("human", "ai", "mixed", "unknown"))
        require(evidenceStatus in setOf("external_evidence", "reasoning_based_proposal", "source_unknown"))
        require(biomechanicalBasis.isNotBlank()) { "Recruitment provenance requires a biomechanical basis." }
        if (evidenceStatus == "external_evidence") {
            require(sourceReferences.isNotEmpty()) { "External-evidence provenance requires at least one genuine source reference." }
        }
    }

    fun compactReference(): String = buildString {
        append("author=").append(authorType).append("; evidence=").append(evidenceStatus)
        modelToolIdentity?.let { append("; tool=").append(it) }
        if (sourceReferences.isNotEmpty()) append("; refs=").append(sourceReferences.joinToString(" | "))
        append("; basis=").append(biomechanicalBasis)
    }
}

data class ProposedRecruitmentAllocation(
    val segmentId: String,
    val segmentName: String,
    val role: RecruitmentRole,
    val weighting: Double,
    val confidence: Double,
    val provenance: RecruitmentAuthoringProvenance,
    val applicableRom: String?,
    val applicableTechnique: String?,
    val resistanceCurveClass: String?,
)

data class ValidatedExerciseImportProposal(
    val stableConceptKey: String,
    val exerciseName: String,
    val conceptDefinition: String,
    val profileName: String,
    val semanticVersionIntent: String,
    val isDefault: Boolean,
    val metricFamily: MetricFamily,
    val schemaMetrics: List<SchemaMetric>,
    val resistanceModel: ResistanceModel,
    val entryBasis: EntryBasis,
    val implementCount: Int?,
    val lateralityMode: LateralityMode,
    val equipment: EquipmentProfile,
    val movementPattern: String?,
    val jointActions: List<String>,
    val kineticChain: String?,
    val contractionType: String?,
    val romClass: String?,
    val techniqueClass: String?,
    val resistanceCurveClass: String?,
    val gripSupportConstraints: List<String>,
    val recruitmentModelVersion: String,
    val recruitmentProfileProvenance: String,
    val recruitment: List<ProposedRecruitmentAllocation>,
) {
    /**
     * External JSON never supplies persistence ids/timestamps. A future importer assigns them only
     * after structural + semantic validation and human approval, then reuses the existing canonical
     * ExecutionProfileAuthoringRepository request type.
     */
    fun toAuthoringRequest(
        identity: ExerciseImportAssignedIdentity,
        createdAt: String,
        effectiveAt: String = createdAt,
    ): ExecutionProfileAuthoringRequest {
        val schema = PerformanceSchema(
            id = identity.performanceSchemaId,
            version = 1,
            family = metricFamily,
            metrics = schemaMetrics,
            provenance = "external-authoring-contract-v1:$stableConceptKey",
        )
        val recruitmentProfile = RecruitmentProfile(
            id = identity.recruitmentProfileVersionId,
            version = 1,
            allocations = recruitment.map { proposal ->
                RecruitmentAllocation(
                    segmentId = MuscleSegmentId(proposal.segmentId),
                    segmentName = proposal.segmentName,
                    role = proposal.role,
                    weighting = proposal.weighting,
                    confidence = proposal.confidence,
                    source = RecruitmentSource(
                        provenanceType = proposal.provenance.evidenceStatus,
                        reference = proposal.provenance.compactReference(),
                    ),
                    applicableRom = proposal.applicableRom,
                    applicableTechnique = proposal.applicableTechnique,
                    resistanceCurveClass = proposal.resistanceCurveClass,
                    modelVersion = recruitmentModelVersion,
                )
            },
            createdAt = createdAt,
            effectiveAt = effectiveAt,
            supersededAt = null,
            provenance = recruitmentProfileProvenance,
            modelVersion = recruitmentModelVersion,
        )
        val version = ExecutionProfileVersion(
            id = identity.executionProfileVersionId,
            executionProfileId = identity.executionProfileId,
            version = 1,
            metricFamily = metricFamily,
            schema = schema,
            equipment = equipment,
            resistanceModel = resistanceModel,
            entryBasis = entryBasis,
            implementCount = implementCount,
            lateralityMode = lateralityMode,
            romClass = romClass,
            techniqueClass = techniqueClass,
            resistanceCurveClass = resistanceCurveClass,
            movementPattern = movementPattern,
            jointActions = jointActions,
            kineticChain = kineticChain,
            contractionType = contractionType,
            gripSupportConstraints = gripSupportConstraints,
            recruitment = recruitmentProfile,
            createdAt = createdAt,
            effectiveAt = effectiveAt,
            supersededAt = null,
            provenance = "external-authoring-contract-v1:$stableConceptKey",
            modelVersion = "external-authoring-contract-v1",
        )
        return ExecutionProfileAuthoringRequest(
            exerciseId = identity.exerciseId,
            exerciseName = exerciseName,
            profileName = profileName,
            isDefault = isDefault,
            version = version,
        )
    }
}

class ExerciseImportSemanticException(message: String) : IllegalArgumentException(message)

/** Semantic layer two. JSON Schema is layer one; human preview remains layer three. */
class ExerciseImportSemanticValidator(
    private val canonicalSegmentIds: Set<String>,
) {
    init { require(canonicalSegmentIds.isNotEmpty()) }

    fun validate(json: String): ValidatedExerciseImportProposal = try {
        validate(JSONObject(json))
    } catch (error: ExerciseImportSemanticException) {
        throw error
    } catch (error: Throwable) {
        throw ExerciseImportSemanticException(error.message ?: "Malformed exercise authoring proposal.")
    }

    fun validate(root: JSONObject): ValidatedExerciseImportProposal {
        requireSemantic(root.getString("format") == "my-mettle-exercise-authoring", "Unsupported exercise-authoring format.")
        requireSemantic(root.getInt("formatVersion") == 1, "Unsupported exercise-authoring format version.")
        val exercise = root.getJSONObject("exercise")
        val profile = root.getJSONObject("profile")
        val stableConceptKey = exercise.getString("stableConceptKey").trim()
        val exerciseName = exercise.getString("name").trim()
        val conceptDefinition = exercise.getString("conceptDefinition").trim()
        requireSemantic(stableConceptKey.matches(Regex("[a-z0-9][a-z0-9_-]{2,79}")), "Invalid stable conceptual exercise key.")
        requireSemantic(exerciseName.isNotBlank() && conceptDefinition.isNotBlank(), "Exercise identity requires name and definition.")

        val metricFamily = MetricFamily.fromStorage(profile.getString("metricFamily"))
        val metrics = profile.getJSONObject("performanceSchema").getJSONArray("metrics").mapObjects { metricJson ->
            val metric = PerformanceMetric.fromStorage(metricJson.getString("metric"))
            val unit = UnitId.fromStorage(metricJson.getString("defaultUnit"))
            requireSemantic(unit.dimension == metric.dimension, "${metric.storageValue} cannot use unit ${unit.storageValue}.")
            SchemaMetric(
                metric = metric,
                required = metricJson.getBoolean("required"),
                targetable = metricJson.getBoolean("targetable"),
                defaultUnit = unit,
                minimumCanonical = metricJson.nullableDouble("minimumCanonical"),
                maximumCanonical = metricJson.nullableDouble("maximumCanonical"),
                incrementCanonical = metricJson.nullableDouble("incrementCanonical"),
                allowedCanonicalValues = metricJson.optJSONArray("allowedCanonicalValues")?.mapDoubles().orEmpty(),
            )
        }
        requireSemantic(metrics.isNotEmpty(), "Performance schema requires at least one metric.")
        requireSemantic(metrics.map { it.metric }.distinct().size == metrics.size, "Performance schema contains duplicate metrics.")
        validateMetricFamily(metricFamily, metrics.map { it.metric }.toSet())

        val resistanceJson = profile.getJSONObject("resistance")
        val semantics = ResistanceSemantics.entries.firstOrNull { it.storageValue == resistanceJson.getString("semantics") }
            ?: throw ExerciseImportSemanticException("Unsupported resistance semantics.")
        val resistance = ResistanceModel(
            modelVersion = resistanceJson.getString("modelVersion"),
            semantics = semantics,
            bodyweightCoefficient = resistanceJson.getDouble("bodyweightCoefficient"),
            externalLoadCoefficient = resistanceJson.getDouble("externalLoadCoefficient"),
            assistanceCoefficient = resistanceJson.getDouble("assistanceCoefficient"),
        )
        validateResistance(resistance, metrics.map { it.metric }.toSet())

        val equipmentJson = profile.getJSONObject("equipment")
        val calibration = equipmentJson.getJSONObject("calibration")
        val calibrationStatus = calibration.getString("status")
        val physicalUnit = calibration.nullableString("physicalUnit")
        validateCalibration(calibrationStatus, physicalUnit, metrics.map { it.metric }.toSet(), semantics)

        val entryBasis = EntryBasis.fromStorage(profile.getString("entryBasis"))
        val implementCount = profile.nullableInt("implementCount")
        val laterality = LateralityMode.entries.firstOrNull { it.storageValue == profile.getString("lateralityMode") }
            ?: throw ExerciseImportSemanticException("Unsupported laterality mode.")
        validateEntryBasis(entryBasis, implementCount, laterality)

        val mechanics = profile.getJSONObject("mechanics")
        val romClass = mechanics.nullableString("romClass")
        val techniqueClass = mechanics.nullableString("techniqueClass")
        val resistanceCurveClass = mechanics.nullableString("resistanceCurveClass")
        val recruitmentJson = profile.getJSONObject("recruitment")
        val recruitmentModelVersion = recruitmentJson.getString("modelVersion").trim()
        val profileProvenance = recruitmentJson.getString("profileProvenance").trim()
        requireSemantic(recruitmentModelVersion.isNotBlank() && profileProvenance.isNotBlank(), "Recruitment model/provenance is mandatory.")
        val allocations = recruitmentJson.getJSONArray("allocations").mapObjects { allocation ->
            val segmentId = allocation.getString("segmentId")
            requireSemantic(segmentId in canonicalSegmentIds, "Unknown canonical muscle segment id: $segmentId")
            val weighting = allocation.getDouble("weighting")
            val confidence = allocation.getDouble("confidence")
            requireSemantic(weighting in 0.0..1.0, "Recruitment weighting must be in [0,1].")
            requireSemantic(confidence in 0.0..1.0, "Recruitment confidence must be in [0,1].")
            val provenanceJson = allocation.optJSONObject("provenance")
                ?: throw ExerciseImportSemanticException("Recruitment allocation $segmentId is missing mandatory provenance.")
            val provenance = RecruitmentAuthoringProvenance(
                authorType = provenanceJson.getString("authorType"),
                evidenceStatus = provenanceJson.getString("evidenceStatus"),
                sourceReferences = provenanceJson.getJSONArray("sourceReferences").mapStrings(),
                biomechanicalBasis = provenanceJson.getString("biomechanicalBasis"),
                modelToolIdentity = provenanceJson.nullableString("modelToolIdentity"),
            )
            ProposedRecruitmentAllocation(
                segmentId = segmentId,
                segmentName = allocation.getString("segmentName").trim(),
                role = RecruitmentRole.fromStorage(allocation.getString("role")),
                weighting = weighting,
                confidence = confidence,
                provenance = provenance,
                applicableRom = allocation.nullableString("applicableRom"),
                applicableTechnique = allocation.nullableString("applicableTechnique"),
                resistanceCurveClass = allocation.nullableString("resistanceCurveClass"),
            )
        }
        requireSemantic(allocations.isNotEmpty(), "Recruitment proposal requires at least one allocation.")
        requireSemantic(allocations.map { it.segmentId }.distinct().size == allocations.size, "Recruitment cannot repeat a canonical muscle segment.")

        return ValidatedExerciseImportProposal(
            stableConceptKey = stableConceptKey,
            exerciseName = exerciseName,
            conceptDefinition = conceptDefinition,
            profileName = profile.getString("name").trim().also { requireSemantic(it.isNotBlank(), "Profile name cannot be blank.") },
            semanticVersionIntent = profile.getString("semanticVersionIntent").also {
                requireSemantic(it in setOf("create_new_immutable_profile", "propose_successor_immutable_version"), "Unsupported semantic version intent.")
            },
            isDefault = profile.getBoolean("isDefault"),
            metricFamily = metricFamily,
            schemaMetrics = metrics,
            resistanceModel = resistance,
            entryBasis = entryBasis,
            implementCount = implementCount,
            lateralityMode = laterality,
            equipment = EquipmentProfile(
                identity = equipmentJson.nullableString("identity"),
                type = equipmentJson.nullableString("type"),
            ),
            movementPattern = mechanics.nullableString("movementPattern"),
            jointActions = mechanics.getJSONArray("jointActions").mapStrings(),
            kineticChain = mechanics.nullableString("kineticChain"),
            contractionType = mechanics.nullableString("contractionType"),
            romClass = romClass,
            techniqueClass = techniqueClass,
            resistanceCurveClass = resistanceCurveClass,
            gripSupportConstraints = mechanics.getJSONArray("gripSupportConstraints").mapStrings(),
            recruitmentModelVersion = recruitmentModelVersion,
            recruitmentProfileProvenance = profileProvenance,
            recruitment = allocations,
        )
    }

    private fun validateMetricFamily(family: MetricFamily, metrics: Set<PerformanceMetric>) {
        fun requires(vararg required: PerformanceMetric) {
            requireSemantic(required.all { it in metrics }, "${family.storageValue} is missing required metric(s): ${required.filterNot { it in metrics }.joinToString { it.storageValue }}")
        }
        when (family) {
            MetricFamily.DYNAMIC_RESISTANCE -> requires(PerformanceMetric.REPETITIONS)
            MetricFamily.BODYWEIGHT_RESISTANCE -> requires(PerformanceMetric.REPETITIONS)
            MetricFamily.LOADED_HOLD -> requires(PerformanceMetric.DURATION)
            MetricFamily.DURATION_ONLY -> requires(PerformanceMetric.DURATION)
            MetricFamily.REPEATED_CONTRACTION -> requires(PerformanceMetric.REPETITIONS)
            MetricFamily.POWER_DURATION -> requires(PerformanceMetric.POWER, PerformanceMetric.DURATION)
            MetricFamily.SPEED_DURATION -> requires(PerformanceMetric.SPEED, PerformanceMetric.DURATION)
            MetricFamily.DEVICE_ORDINAL -> requires(PerformanceMetric.MACHINE_LEVEL)
        }
    }

    private fun validateResistance(model: ResistanceModel, metrics: Set<PerformanceMetric>) {
        when (model.semantics) {
            ResistanceSemantics.EXTERNAL -> requireSemantic(
                model.externalLoadCoefficient > 0.0 && model.bodyweightCoefficient == 0.0 && model.assistanceCoefficient == 0.0 && PerformanceMetric.EXTERNAL_LOAD in metrics,
                "External resistance requires an external-load metric/coefficient and no bodyweight/assistance coefficient.",
            )
            ResistanceSemantics.ASSISTANCE -> requireSemantic(
                model.assistanceCoefficient > 0.0 && PerformanceMetric.ASSISTANCE in metrics,
                "Assistance resistance requires an assistance metric and positive assistance coefficient.",
            )
            ResistanceSemantics.BODYWEIGHT -> requireSemantic(
                model.bodyweightCoefficient > 0.0 && model.externalLoadCoefficient == 0.0 && model.assistanceCoefficient == 0.0 &&
                    PerformanceMetric.EXTERNAL_LOAD !in metrics && PerformanceMetric.ASSISTANCE !in metrics,
                "Bodyweight resistance cannot masquerade as external load or assistance.",
            )
            ResistanceSemantics.BODYWEIGHT_PLUS_EXTERNAL -> requireSemantic(
                model.bodyweightCoefficient > 0.0 && model.externalLoadCoefficient > 0.0 && model.assistanceCoefficient == 0.0 && PerformanceMetric.EXTERNAL_LOAD in metrics,
                "Bodyweight-plus-external semantics require both positive coefficients and an external-load metric.",
            )
            ResistanceSemantics.NONE -> requireSemantic(
                model.bodyweightCoefficient == 0.0 && model.externalLoadCoefficient == 0.0 && model.assistanceCoefficient == 0.0,
                "No-resistance semantics require zero resistance coefficients.",
            )
            ResistanceSemantics.DEVICE_ORDINAL -> requireSemantic(
                model.bodyweightCoefficient == 0.0 && model.externalLoadCoefficient == 0.0 && model.assistanceCoefficient == 0.0 && PerformanceMetric.MACHINE_LEVEL in metrics,
                "Device-ordinal resistance requires machine_level and no physical resistance coefficients.",
            )
        }
    }

    private fun validateCalibration(
        status: String,
        physicalUnit: String?,
        metrics: Set<PerformanceMetric>,
        semantics: ResistanceSemantics,
    ) {
        when (status) {
            "calibrated_physical" -> {
                requireSemantic(physicalUnit != null, "Calibrated equipment requires a physical unit.")
                val unit = UnitId.fromStorage(requireNotNull(physicalUnit))
                requireSemantic(unit.dimension != dev.kian.mymettle.domain.performance.QuantityDimension.ORDINAL, "Calibrated physical equipment cannot use an ordinal unit.")
            }
            "uncalibrated_ordinal" -> {
                requireSemantic(physicalUnit == null, "Uncalibrated ordinal equipment must not claim a physical unit.")
                requireSemantic(PerformanceMetric.MACHINE_LEVEL in metrics && PerformanceMetric.EXTERNAL_LOAD !in metrics && PerformanceMetric.ASSISTANCE !in metrics, "Uncalibrated ordinal equipment must use machine_level, not kg/physical load.")
                requireSemantic(semantics == ResistanceSemantics.DEVICE_ORDINAL, "Uncalibrated ordinal equipment requires device_ordinal resistance semantics.")
            }
            "not_applicable" -> requireSemantic(physicalUnit == null, "Not-applicable calibration must not claim a physical unit.")
            else -> throw ExerciseImportSemanticException("Unsupported equipment calibration status: $status")
        }
    }

    private fun validateEntryBasis(entryBasis: EntryBasis, implementCount: Int?, laterality: LateralityMode) {
        requireSemantic(implementCount == null || implementCount > 0, "Implement count must be positive when present.")
        if (entryBasis != EntryBasis.TOTAL) {
            requireSemantic(implementCount != null, "Per-hand/per-side entry requires implementCount.")
        }
        if (entryBasis == EntryBasis.PER_SIDE) {
            requireSemantic(laterality in setOf(LateralityMode.UNILATERAL, LateralityMode.ALTERNATING_ALLOWED, LateralityMode.UNKNOWN), "Per-side entry is incompatible with the selected laterality mode.")
        }
    }

    private fun requireSemantic(condition: Boolean, message: String) {
        if (!condition) throw ExerciseImportSemanticException(message)
    }
}

private fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else getString(key).trim().ifBlank { null }
private fun JSONObject.nullableDouble(key: String): Double? = if (!has(key) || isNull(key)) null else getDouble(key)
private fun JSONObject.nullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)

private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> = List(length()) { index -> block(getJSONObject(index)) }
private fun JSONArray.mapStrings(): List<String> = List(length()) { index -> getString(index).trim() }
private fun JSONArray.mapDoubles(): List<Double> = List(length()) { index -> getDouble(index) }
