package dev.kian.mymettle.data.reference

import android.content.res.AssetManager
import dev.kian.mymettle.domain.anatomy.AnatomicalStatus
import dev.kian.mymettle.domain.anatomy.AnatomicalUnitKind
import dev.kian.mymettle.domain.anatomy.AnatomyVerificationStatus
import dev.kian.mymettle.domain.anatomy.BodyRegion
import dev.kian.mymettle.domain.anatomy.LateralityModel
import dev.kian.mymettle.domain.anatomy.Muscle
import dev.kian.mymettle.domain.anatomy.MuscleId
import dev.kian.mymettle.domain.anatomy.MuscleSegment
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.anatomy.SegmentStatePolicy
import dev.kian.mymettle.domain.anatomy.SegmentType
import dev.kian.mymettle.domain.physiology.AbsoluteSharePolicy
import dev.kian.mymettle.domain.physiology.Estimate
import dev.kian.mymettle.domain.physiology.EstimateSourceKind
import dev.kian.mymettle.domain.physiology.ReferencePhysiologyPrior
import dev.kian.mymettle.domain.physiology.ReferencePopulation
import dev.kian.mymettle.domain.physiology.ReferenceProfile
import dev.kian.mymettle.domain.physiology.ReferenceProfileId
import dev.kian.mymettle.domain.physiology.ReferenceSex
import dev.kian.mymettle.domain.physiology.UncertaintyClass
import org.json.JSONArray
import org.json.JSONObject

data class RuntimeReferenceDataset(
    val anatomyVersion: String,
    val muscles: List<Muscle>,
    val referenceProfile: ReferenceProfile,
)

object ReferenceAssetLoader {
    private const val ANATOMY_ASSET = "reference/anatomy_v1.json"
    private const val PROFILE_ASSET = "reference/reference_profile_healthy_adult_male_v1.json"

    fun load(assetManager: AssetManager): RuntimeReferenceDataset = parse(
        anatomyJson = assetManager.open(ANATOMY_ASSET).bufferedReader().use { it.readText() },
        profileJson = assetManager.open(PROFILE_ASSET).bufferedReader().use { it.readText() },
    )

    fun parse(anatomyJson: String, profileJson: String): RuntimeReferenceDataset {
        val anatomyRoot = JSONObject(anatomyJson)
        require(anatomyRoot.getInt("schemaVersion") == 1) { "Unsupported runtime anatomy schema." }
        val muscles = anatomyRoot.getJSONArray("muscles").objects().map { muscleJson ->
            val muscleId = MuscleId(muscleJson.getString("id"))
            Muscle(
                id = muscleId,
                name = muscleJson.getString("name"),
                region = BodyRegion.valueOf(muscleJson.getString("region")),
                unitKind = AnatomicalUnitKind.valueOf(muscleJson.getString("unitKind")),
                lateralityModel = LateralityModel.valueOf(muscleJson.getString("lateralityModel")),
                instancePattern = muscleJson.nullableString("instancePattern"),
                verificationStatus = AnatomyVerificationStatus.valueOf(muscleJson.getString("verificationStatus")),
                segments = muscleJson.getJSONArray("segments").objects().map { segmentJson ->
                    MuscleSegment(
                        id = MuscleSegmentId(segmentJson.getString("id")),
                        muscleId = muscleId,
                        name = segmentJson.getString("name"),
                        type = SegmentType.valueOf(segmentJson.getString("segmentType")),
                        anatomicalStatus = AnatomicalStatus.valueOf(segmentJson.getString("anatomicalStatus")),
                        statePolicy = SegmentStatePolicy.valueOf(segmentJson.getString("statePolicy")),
                        verificationStatus = AnatomyVerificationStatus.valueOf(segmentJson.getString("verificationStatus")),
                    )
                },
            )
        }

        val profileRoot = JSONObject(profileJson)
        require(profileRoot.getInt("schemaVersion") == 1) { "Unsupported runtime reference-profile schema." }
        val population = profileRoot.getJSONObject("population")
        val profile = ReferenceProfile(
            id = ReferenceProfileId(profileRoot.getString("id")),
            version = profileRoot.getInt("version"),
            population = ReferencePopulation(
                sex = ReferenceSex.valueOf(population.getString("sex")),
                ageSummary = population.getString("ageSummary"),
                description = population.getString("description"),
            ),
            datasetVersion = profileRoot.getString("datasetVersion"),
            modelVersion = profileRoot.getString("modelVersion"),
            priors = profileRoot.getJSONArray("priors").objects().map(::parsePrior),
        )

        return RuntimeReferenceDataset(
            anatomyVersion = anatomyRoot.getString("datasetVersion"),
            muscles = muscles,
            referenceProfile = profile,
        ).also(ReferenceDatasetValidator::validate)
    }

    private fun parsePrior(json: JSONObject): ReferencePhysiologyPrior {
        val share = json.getJSONObject("absoluteSharePolicy")
        val sharePolicy = when (share.getString("kind")) {
            "KNOWN" -> AbsoluteSharePolicy.Known(share.getDouble("fraction"))
            "STRUCTURAL_PRIOR" -> AbsoluteSharePolicy.StructuralPrior(
                fraction = share.getDouble("fraction"),
                uncertainty = UncertaintyClass.valueOf(share.getString("uncertainty")),
            )
            "LATENT" -> AbsoluteSharePolicy.Latent
            else -> error("Unsupported absolute-share policy ${share.getString("kind")}")
        }
        return ReferencePhysiologyPrior(
            muscleId = MuscleId(json.getString("muscleId")),
            segmentId = json.nullableString("segmentId")?.let(::MuscleSegmentId),
            volumeCm3 = json.estimate("volumeCm3"),
            optimalFibreLengthMm = json.estimate("optimalFibreLengthMm"),
            pennationDeg = json.estimate("pennationDeg"),
            geometricPcsaCm2 = json.estimate("geometricPcsaCm2"),
            effectivePcsaCm2 = json.estimate("effectivePcsaCm2"),
            structuralCapacityIndex = json.estimate("structuralCapacityIndex"),
            absoluteSharePolicy = sharePolicy,
            availability = json.getString("availability"),
            uncertaintyClass = UncertaintyClass.valueOf(json.getString("uncertaintyClass")),
            selectionRule = json.getString("selectionRule"),
        )
    }
}

object ReferenceDatasetValidator {
    fun validate(dataset: RuntimeReferenceDataset) {
        require(dataset.muscles.size == 142) { "Runtime anatomy must contain 142 canonical muscles." }
        require(dataset.muscles.map { it.id }.distinct().size == dataset.muscles.size) { "Muscle ids are not unique." }

        val segments = dataset.muscles.flatMap { it.segments }
        require(segments.size == 164) { "Runtime anatomy must contain 164 generated segments." }
        require(segments.map { it.id }.distinct().size == segments.size) { "Muscle-segment ids are not unique." }
        require(dataset.muscles.all { it.segments.isNotEmpty() }) { "Every muscle needs an addressable segment representation." }
        require(segments.all { segment -> dataset.muscles.any { it.id == segment.muscleId } }) {
            "A muscle segment references an unknown parent."
        }

        val muscleIds = dataset.muscles.mapTo(hashSetOf()) { it.id }
        val segmentById = segments.associateBy { it.id }
        val priors = dataset.referenceProfile.priors
        require(priors.size == 66) { "Runtime reference profile must contain 66 selected/policy priors." }
        require(priors.all { it.muscleId in muscleIds }) { "A reference prior targets an unknown muscle." }
        require(priors.all { prior ->
            prior.segmentId == null || segmentById[prior.segmentId]?.muscleId == prior.muscleId
        }) { "A reference prior targets a segment outside its parent muscle." }
        require(priors.map { it.segmentId?.value ?: "muscle:${it.muscleId.value}" }.distinct().size == priors.size) {
            "Reference-prior targets are not unique."
        }

        priors.forEach { prior ->
            prior.estimates().forEach { estimate ->
                require(estimate.value > 0.0) { "Physiology estimates must be positive." }
                require(estimate.uncertainty == null || estimate.uncertainty >= 0.0) {
                    "Physiology uncertainty cannot be negative."
                }
            }
            when (val share = prior.absoluteSharePolicy) {
                is AbsoluteSharePolicy.Known -> require(share.fraction > 0.0 && share.fraction <= 1.0)
                is AbsoluteSharePolicy.StructuralPrior -> require(share.fraction > 0.0 && share.fraction <= 1.0)
                AbsoluteSharePolicy.Latent -> Unit
            }
        }

        val independentSegments = segments.filter {
            it.statePolicy == SegmentStatePolicy.TRACK || it.statePolicy == SegmentStatePolicy.PROVISIONAL_TRACK
        }
        val segmentedParents = dataset.muscles.filter { muscle ->
            muscle.segments.none { it.type == SegmentType.WHOLE_MUSCLE }
        }.mapTo(hashSetOf()) { it.id }
        val independentlyTrackedChildren = independentSegments.filter { it.muscleId in segmentedParents }
        val childPriorIds = priors.mapNotNullTo(hashSetOf()) { it.segmentId }
        require(independentlyTrackedChildren.all { it.id in childPriorIds }) {
            "Every independently tracked child segment needs an explicit structural/latent policy."
        }
    }

    private fun ReferencePhysiologyPrior.estimates(): List<Estimate<Double>> = listOfNotNull(
        volumeCm3,
        optimalFibreLengthMm,
        pennationDeg,
        geometricPcsaCm2,
        effectivePcsaCm2,
        structuralCapacityIndex,
    )
}

private fun JSONObject.estimate(name: String): Estimate<Double>? {
    if (!has(name) || isNull(name)) return null
    val json = getJSONObject(name)
    return Estimate(
        value = json.getDouble("value"),
        uncertainty = json.nullableDouble("uncertainty"),
        sourceKind = EstimateSourceKind.valueOf(json.getString("sourceKind")),
        sourceId = json.nullableString("sourceId"),
        modelVersion = json.nullableString("modelVersion"),
    )
}

private fun JSONObject.nullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else getString(name)

private fun JSONObject.nullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else getDouble(name)

private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
