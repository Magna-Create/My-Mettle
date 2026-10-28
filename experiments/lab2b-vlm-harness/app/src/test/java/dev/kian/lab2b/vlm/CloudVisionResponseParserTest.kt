package dev.kian.lab2b.vlm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline, deterministic contract tests: no key, no network, no Android device. */
class CloudVisionResponseParserTest {
    @Test fun logoOnlyReadsNamesAndScores() {
        val response = JSONObject(
            """{"responses":[{"logoAnnotations":[{"description":"Technogym","score":0.92},
            {"description":"Hammer Strength","score":0.73}]}]}"""
        )
        val result = CloudVisionResponseParser.parse(CloudVisionFeature.LOGO, response, 183L, 32000)
        assertEquals(2, result.candidates.size)
        assertEquals("Technogym", result.candidates[0].name)
        assertEquals("logo", result.candidates[0].source)
        assertEquals(0.92, result.candidates[0].score ?: 0.0, 0.0001)
        assertTrue(result.hasCandidates)
        assertEquals(183L, result.elapsedMs)
        assertTrue(result.bestGuess.isEmpty())
    }

    @Test fun unknownIsValidNonMatchWithoutInventedManufacturer() {
        val response = JSONObject("""{"responses":[{}]}""")
        val logo = CloudVisionResponseParser.parse(CloudVisionFeature.LOGO, response, 15L, 2048)
        val web = CloudVisionResponseParser.parse(CloudVisionFeature.WEB, response, 22L, 2048)
        assertFalse(logo.hasCandidates)
        assertFalse(web.hasCandidates)
        assertTrue(logo.candidates.isEmpty())
        assertTrue(web.bestGuess.isEmpty())
    }

    @Test fun webEntitiesBestGuessAndPagesRemainSeparateEvidence() {
        val response = JSONObject(
            """{"responses":[{"webDetection":{
                "webEntities":[{"description":"Matrix Fitness","score":1.22},
                               {"description":"","score":0.19}],
                "bestGuessLabels":[{"label":"gym equipment"}],
                "pagesWithMatchingImages":[
                  {"url":"https://manufacturer.example/machine"},
                  {"url":"javascript:alert(1)"}]
            }}]}"""
        )
        val result = CloudVisionResponseParser.parse(CloudVisionFeature.WEB, response, 450L, 40000)
        assertEquals(1, result.candidates.size)
        assertEquals("Matrix Fitness", result.candidates[0].name)
        assertEquals("web entity", result.candidates[0].source)
        assertEquals(1.22, result.candidates[0].score ?: 0.0, 0.0001)
        assertEquals(listOf("gym equipment"), result.bestGuess)
        assertEquals(listOf("https://manufacturer.example/machine"), result.matchingPages)
    }

    @Test(expected = IllegalStateException::class)
    fun perImageErrorCannotBeMistakenForUnknownLogo() {
        CloudVisionResponseParser.parse(
            CloudVisionFeature.LOGO,
            JSONObject("""{"responses":[{"error":{"code":7,"message":"Permission denied"}}]}"""),
            20L,
            2000,
        )
    }
}
