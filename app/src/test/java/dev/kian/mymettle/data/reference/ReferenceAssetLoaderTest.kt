package dev.kian.mymettle.data.reference

import dev.kian.mymettle.domain.anatomy.MuscleId
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.anatomy.SegmentType
import dev.kian.mymettle.domain.physiology.AbsoluteSharePolicy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ReferenceAssetLoaderTest {
    private val dataset by lazy {
        ReferenceAssetLoader.parse(
            anatomyJson = asset("anatomy_v1.json").readText(),
            profileJson = asset("reference_profile_healthy_adult_male_v1.json").readText(),
        )
    }

    @Test
    fun `selected runtime dataset has stable complete anatomy`() {
        assertEquals(142, dataset.muscles.size)
        assertEquals(164, dataset.muscles.sumOf { it.segments.size })
        assertEquals(66, dataset.referenceProfile.priors.size)
        assertEquals(47, dataset.referenceProfile.priors.count { it.volumeCm3 != null })
    }

    @Test
    fun `segmented parent morphology does not invent child shares`() {
        val pectoralis = dataset.muscles.single { it.id == MuscleId("pectoralis_major") }
        assertFalse(pectoralis.segments.any { it.type == SegmentType.WHOLE_MUSCLE })

        val parent = dataset.referenceProfile.priors.single {
            it.muscleId == pectoralis.id && it.segmentId == null
        }
        assertEquals(424.28, assertNotNull(parent.volumeCm3).value)

        val clavicular = dataset.referenceProfile.priors.single {
            it.segmentId == MuscleSegmentId("pectoralis_major_clavicular_part")
        }
        assertIs<AbsoluteSharePolicy.Latent>(clavicular.absoluteSharePolicy)
    }

    @Test
    fun `direct and weak segment policies remain distinguishable`() {
        val gastrocnemius = dataset.referenceProfile.priors.single {
            it.segmentId == MuscleSegmentId("gastrocnemius_medial_head")
        }
        assertIs<AbsoluteSharePolicy.Known>(gastrocnemius.absoluteSharePolicy)
        assertNotNull(gastrocnemius.volumeCm3)

        val trapezius = dataset.referenceProfile.priors.single {
            it.segmentId == MuscleSegmentId("trapezius_transverse_part")
        }
        assertIs<AbsoluteSharePolicy.StructuralPrior>(trapezius.absoluteSharePolicy)
        assertEquals(null, trapezius.volumeCm3)
    }

    private fun asset(name: String): File = sequenceOf(
        File("src/main/assets/reference/$name"),
        File("app/src/main/assets/reference/$name"),
    ).firstOrNull(File::isFile) ?: error("Could not find runtime reference asset $name")
}
