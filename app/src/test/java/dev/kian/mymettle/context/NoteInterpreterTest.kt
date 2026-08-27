package dev.kian.mymettle.context

import dev.kian.mymettle.domain.context.AssertionSemantics
import dev.kian.mymettle.domain.context.ContextAnnotation
import dev.kian.mymettle.domain.context.ContextEvidenceProjector
import dev.kian.mymettle.domain.context.ContextInterpretationProvenance
import dev.kian.mymettle.domain.context.ContextTagId
import dev.kian.mymettle.domain.context.ContextTagRegistry
import dev.kian.mymettle.domain.context.ContextValue
import dev.kian.mymettle.domain.context.InferenceEligibility
import dev.kian.mymettle.domain.context.NoteScope
import dev.kian.mymettle.domain.context.TemporalApplicability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class NoteInterpreterTest {
    private val rules = RulesNoteInterpreter()

    @Test
    fun exactSleepDurationIsTypedInHours() = runBlocking {
        val result = rules.interpret(NoteInterpretationRequest("I slept 4 hours", NoteScope.SESSION_REVIEW))
        val sleep = result.annotations.single()
        assertEquals("SLEEP_DURATION_REPORTED", sleep.tagId.value)
        assertEquals(ContextValue.NumberValue(4.0, "h"), sleep.value)
        assertFalse(sleep.approximate)
    }

    @Test
    fun approximateSleepDurationRetainsApproximationAndExactSpan() = runBlocking {
        val source = "I slept about 4 hours"
        val sleep = rules.interpret(NoteInterpretationRequest(source, NoteScope.SESSION_REVIEW)).annotations.single()
        assertEquals(ContextValue.NumberValue(4.0, "h"), sleep.value)
        assertTrue(sleep.approximate)
        sleep.sourceSpan!!.validateAgainst(source)
        assertEquals("about 4 hours", sleep.sourceSpan.exactText)
    }

    @Test
    fun compactSleepWordingIsSupported() = runBlocking {
        val result = rules.interpret(NoteInterpretationRequest("Only got about 5.5h sleep", NoteScope.SESSION_REVIEW))
        val sleep = result.annotations.single()
        assertEquals(ContextValue.NumberValue(5.5, "h"), sleep.value)
        assertTrue(sleep.approximate)
    }

    @Test
    fun currentIllnessIsReportedWithoutDiagnosis() = runBlocking {
        val illness = rules.interpret(NoteInterpretationRequest("I was ill today", NoteScope.SESSION_REVIEW)).annotations.single()
        assertEquals("ILLNESS_REPORTED", illness.tagId.value)
        assertEquals(ContextValue.BooleanValue(true), illness.value)
        assertEquals(AssertionSemantics.ASSERTED, illness.assertion)
        assertEquals(TemporalApplicability.CURRENT, illness.temporalApplicability)
    }

    @Test
    fun negatedIllnessNeverBecomesPositiveIllness() = runBlocking {
        val result = rules.interpret(NoteInterpretationRequest("I wasn't ill", NoteScope.SESSION_REVIEW))
        assertTrue(result.annotations.none { it.tagId.value == "ILLNESS_REPORTED" && it.value == ContextValue.BooleanValue(true) })
        val negation = result.annotations.single()
        assertEquals(ContextValue.BooleanValue(false), negation.value)
        assertEquals(AssertionSemantics.NEGATED, negation.assertion)
    }

    @Test
    fun historicalIllnessWithCurrentRecoveryIsNotCurrentIllness() = runBlocking {
        val result = rules.interpret(
            NoteInterpretationRequest("I was ill last week but I'm fine today", NoteScope.SESSION_REVIEW),
        )
        assertTrue(result.annotations.none {
            it.tagId.value == "ILLNESS_REPORTED" &&
                it.value == ContextValue.BooleanValue(true) &&
                it.temporalApplicability == TemporalApplicability.CURRENT
        })
        assertEquals(TemporalApplicability.HISTORICAL, result.annotations.single().temporalApplicability)
    }

    @Test
    fun qualitativeFatigueIsExtracted() = runBlocking {
        val result = rules.interpret(NoteInterpretationRequest("felt really tired today", NoteScope.SESSION_REVIEW))
        assertEquals("GENERAL_FATIGUE_REPORTED", result.annotations.single().tagId.value)
    }

    @Test
    fun unknownWordingProducesNoFabricatedTag() = runBlocking {
        val result = rules.interpret(NoteInterpretationRequest("The playlist was a bit odd", NoteScope.SESSION_REVIEW))
        assertTrue(result.annotations.isEmpty())
    }

    @Test
    fun arbitraryTagIdIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ContextTagRegistry.V1.requireDefinition(ContextTagId("MODEL_MADE_THIS_UP"))
        }
    }

    @Test
    fun malformedModelResultWithUnregisteredTagFailsClosed() {
        val request = NoteInterpretationRequest("I slept 4 hours", NoteScope.SESSION_REVIEW)
        val malformed = NoteInterpretationResult(
            interpreterKind = InterpreterKind.ML_KIT_NANO,
            implementationVersion = "fixture-nano",
            annotations = listOf(
                ContextAnnotation(
                    tagId = ContextTagId("MODEL_MADE_THIS_UP"),
                    tagSchemaVersion = ContextTagRegistry.V1.schemaVersion,
                    value = ContextValue.BooleanValue(true),
                    scope = NoteScope.SESSION_REVIEW,
                    assertion = AssertionSemantics.ASSERTED,
                    temporalApplicability = TemporalApplicability.CURRENT,
                ),
            ),
            promptVersion = "fixture-prompt",
            structuredOutputSchemaVersion = 1,
            capabilities = NanoRuntimeCapabilities(
                promptApiStatus = PromptApiStatus.AVAILABLE,
                structuredOutputAvailable = true,
                systemInstructionAvailable = true,
                baseModelName = "fixture-model",
            ),
        )

        assertFailsWith<IllegalArgumentException> { malformed.validate(request) }
    }

    @Test
    fun scopesRemainExplicit() = runBlocking {
        val session = rules.interpret(NoteInterpretationRequest("I slept 4 hours", NoteScope.SESSION_REVIEW))
        assertEquals(NoteScope.SESSION_REVIEW, session.annotations.single().scope)
        val exercise = rules.interpret(
            NoteInterpretationRequest(
                "remember to move the seat one notch lower next time",
                NoteScope.EXERCISE_REVIEW,
                exerciseName = "Cable row",
            ),
        )
        assertEquals(NoteScope.EXERCISE_REVIEW, exercise.annotations.single().scope)
    }

    @Test
    fun nextSessionActionIsUxOnly() = runBlocking {
        val source = "remember to move the seat one notch lower next time"
        val result = rules.interpret(NoteInterpretationRequest(source, NoteScope.EXERCISE_REVIEW, "Cable row"))
        val action = result.annotations.single()
        assertEquals("NEXT_SESSION_ACTION", action.tagId.value)
        assertEquals(TemporalApplicability.NEXT_SESSION, action.temporalApplicability)
        assertEquals(InferenceEligibility.UX_ONLY, ContextTagRegistry.V1.requireDefinition(action.tagId).inferenceEligibility)
        val evidence = ContextEvidenceProjector.project(
            source,
            result.annotations,
            ContextInterpretationProvenance("run", "hash", "rules", "v1", 1, null, null, null),
        )
        assertTrue(evidence.items.isEmpty())
    }

    @Test
    fun oneNoteCanProduceMultipleAnnotations() = runBlocking {
        val source = "I slept about 4 hours and feel wrecked today"
        val result = rules.interpret(NoteInterpretationRequest(source, NoteScope.SESSION_REVIEW))
        assertEquals(setOf("SLEEP_DURATION_REPORTED", "GENERAL_FATIGUE_REPORTED"), result.annotations.map { it.tagId.value }.toSet())
        result.annotations.forEach { it.sourceSpan?.validateAgainst(source) }
    }

    @Test
    fun candidateCovariateCanEnterTypedViewWithoutAnyModifier() = runBlocking {
        val source = "I slept 4 hours"
        val result = rules.interpret(NoteInterpretationRequest(source, NoteScope.SESSION_REVIEW))
        val view = ContextEvidenceProjector.project(
            source,
            result.annotations,
            ContextInterpretationProvenance("run", "hash", "rules", RulesNoteInterpreter.VERSION, 1, null, null, null),
        )
        assertEquals(1, view.items.size)
        assertEquals(InferenceEligibility.CANDIDATE_COVARIATE, view.items.single().eligibility)
    }

    @Test
    fun rulesProvenanceCannotMasqueradeAsNano() = runBlocking {
        val result = rules.interpret(NoteInterpretationRequest("I slept 4 hours", NoteScope.SESSION_REVIEW))
        assertEquals(InterpreterKind.RULES, result.interpreterKind)
        assertEquals(RulesNoteInterpreter.VERSION, result.implementationVersion)
        assertNull(result.promptVersion)
        assertNull(result.structuredOutputSchemaVersion)
        assertEquals(PromptApiStatus.NOT_CHECKED, result.capabilities.promptApiStatus)
    }

    @Test
    fun noOpCannotMasqueradeAsRulesOrNano() = runBlocking {
        val result = NoOpNoteInterpreter().interpret(NoteInterpretationRequest("I slept 4 hours", NoteScope.SESSION_REVIEW))
        assertEquals(InterpreterKind.NO_OP, result.interpreterKind)
        assertTrue(result.annotations.isEmpty())
    }
}
