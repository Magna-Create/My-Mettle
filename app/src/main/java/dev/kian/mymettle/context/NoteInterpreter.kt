package dev.kian.mymettle.context

import dev.kian.mymettle.domain.context.AssertionSemantics
import dev.kian.mymettle.domain.context.ContextAnnotation
import dev.kian.mymettle.domain.context.ContextTagId
import dev.kian.mymettle.domain.context.ContextTagRegistry
import dev.kian.mymettle.domain.context.ContextValue
import dev.kian.mymettle.domain.context.NoteScope
import dev.kian.mymettle.domain.context.SourceTextSpan
import dev.kian.mymettle.domain.context.TemporalApplicability

enum class InterpreterKind(val storageValue: String) {
    ML_KIT_NANO("ml_kit_nano"),
    RULES("rules"),
    NO_OP("no_op"),
}

enum class PromptApiStatus(val storageValue: String) {
    UNAVAILABLE("unavailable"),
    DOWNLOADABLE("downloadable"),
    DOWNLOADING("downloading"),
    AVAILABLE("available"),
    ERROR("error"),
    NOT_CHECKED("not_checked"),
}

data class NanoRuntimeCapabilities(
    val promptApiStatus: PromptApiStatus,
    val structuredOutputAvailable: Boolean? = null,
    val systemInstructionAvailable: Boolean? = null,
    val baseModelName: String? = null,
    val probeFailure: String? = null,
) {
    val strictExtractionAvailable: Boolean
        get() = promptApiStatus == PromptApiStatus.AVAILABLE && structuredOutputAvailable == true
}

data class NoteInterpretationRequest(
    val sourceText: String,
    val scope: NoteScope,
    val exerciseName: String? = null,
) {
    init {
        require(sourceText.isNotBlank())
        require(scope == NoteScope.EXERCISE_REVIEW || exerciseName == null) {
            "Exercise names may only accompany exercise-scoped note interpretation."
        }
    }
}

data class NoteInterpretationResult(
    val interpreterKind: InterpreterKind,
    val implementationVersion: String,
    val annotations: List<ContextAnnotation>,
    val promptVersion: String? = null,
    val structuredOutputSchemaVersion: Int? = null,
    val capabilities: NanoRuntimeCapabilities = NanoRuntimeCapabilities(PromptApiStatus.NOT_CHECKED),
) {
    fun validate(request: NoteInterpretationRequest, registry: ContextTagRegistry = ContextTagRegistry.V1) {
        annotations.forEach { it.validate(request.sourceText, registry) }
    }
}

fun interface NoteInterpreter {
    suspend fun interpret(request: NoteInterpretationRequest): NoteInterpretationResult
}

/** Conservative deterministic fallback. Unknown wording intentionally produces no annotations. */
class RulesNoteInterpreter(
    private val registry: ContextTagRegistry = ContextTagRegistry.V1,
) : NoteInterpreter {
    override suspend fun interpret(request: NoteInterpretationRequest): NoteInterpretationResult {
        val source = request.sourceText
        val annotations = buildList {
            if (request.scope == NoteScope.SESSION_REVIEW) {
                addSleepDuration(source)?.let(::add)
                addIllness(source)?.let(::add)
                addFatigue(source)?.let(::add)
            }
            if (request.scope == NoteScope.EXERCISE_REVIEW) {
                addNextSessionAction(source)?.let(::add)
            }
        }.distinctBy { annotation ->
            listOf(
                annotation.tagId.value,
                annotation.assertion.storageValue,
                annotation.temporalApplicability.storageValue,
                annotation.sourceSpan?.startInclusive,
                annotation.sourceSpan?.endExclusive,
            )
        }
        return NoteInterpretationResult(
            interpreterKind = InterpreterKind.RULES,
            implementationVersion = VERSION,
            annotations = annotations,
        ).also { it.validate(request, registry) }
    }

    private fun addSleepDuration(source: String): ContextAnnotation? {
        val match = SLEEP.find(source) ?: return null
        val hours = match.groups["value"]?.value?.toDoubleOrNull() ?: return null
        if (hours <= 0.0 || hours > 24.0) return null
        val approximate = match.groups["approx"] != null || match.groups["ish"] != null
        val valueGroup = match.groups["value"] ?: return null
        val end = match.range.last + 1
        val start = (match.groups["approx"]?.range?.first ?: valueGroup.range.first)
        val span = SourceTextSpan(start, end, source.substring(start, end))
        return ContextAnnotation(
            tagId = ContextTagId("SLEEP_DURATION_REPORTED"),
            tagSchemaVersion = registry.schemaVersion,
            value = ContextValue.NumberValue(hours, "h"),
            scope = NoteScope.SESSION_REVIEW,
            assertion = AssertionSemantics.ASSERTED,
            temporalApplicability = TemporalApplicability.CURRENT,
            approximate = approximate,
            sourceSpan = span,
        )
    }

    private fun addIllness(source: String): ContextAnnotation? {
        val negated = ILL_NEGATED.find(source)
        if (negated != null) {
            return ContextAnnotation(
                tagId = ContextTagId("ILLNESS_REPORTED"),
                tagSchemaVersion = registry.schemaVersion,
                value = ContextValue.BooleanValue(false),
                scope = NoteScope.SESSION_REVIEW,
                assertion = AssertionSemantics.NEGATED,
                temporalApplicability = TemporalApplicability.CURRENT,
                sourceSpan = negated.toSpan(source),
            )
        }
        val match = ILL.find(source) ?: return null
        val historical = HISTORICAL_MARKER.containsMatchIn(source) &&
            (match.range.first <= (HISTORICAL_MARKER.find(source)?.range?.last ?: -1) + 32)
        val recoveredNow = CURRENT_RECOVERY.containsMatchIn(source)
        val temporal = if (historical || recoveredNow) TemporalApplicability.HISTORICAL else TemporalApplicability.CURRENT
        return ContextAnnotation(
            tagId = ContextTagId("ILLNESS_REPORTED"),
            tagSchemaVersion = registry.schemaVersion,
            value = ContextValue.BooleanValue(true),
            scope = NoteScope.SESSION_REVIEW,
            assertion = AssertionSemantics.ASSERTED,
            temporalApplicability = temporal,
            sourceSpan = match.toSpan(source),
        )
    }

    private fun addFatigue(source: String): ContextAnnotation? {
        val match = FATIGUE.find(source) ?: return null
        return ContextAnnotation(
            tagId = ContextTagId("GENERAL_FATIGUE_REPORTED"),
            tagSchemaVersion = registry.schemaVersion,
            value = ContextValue.BooleanValue(true),
            scope = NoteScope.SESSION_REVIEW,
            assertion = AssertionSemantics.ASSERTED,
            temporalApplicability = if (HISTORICAL_MARKER.containsMatchIn(source) && !TODAY.containsMatchIn(source)) {
                TemporalApplicability.HISTORICAL
            } else {
                TemporalApplicability.CURRENT
            },
            sourceSpan = match.toSpan(source),
        )
    }

    private fun addNextSessionAction(source: String): ContextAnnotation? {
        val match = NEXT_ACTION.find(source) ?: return null
        val action = match.value.trim().trimEnd('.', '!', '?')
        if (action.length < 6) return null
        return ContextAnnotation(
            tagId = ContextTagId("NEXT_SESSION_ACTION"),
            tagSchemaVersion = registry.schemaVersion,
            value = ContextValue.TextActionValue(action),
            scope = NoteScope.EXERCISE_REVIEW,
            assertion = AssertionSemantics.ASSERTED,
            temporalApplicability = TemporalApplicability.NEXT_SESSION,
            sourceSpan = match.toSpan(source),
        )
    }

    private fun MatchResult.toSpan(source: String): SourceTextSpan {
        val start = range.first
        val end = range.last + 1
        return SourceTextSpan(start, end, source.substring(start, end))
    }

    companion object {
        const val VERSION = "rules-context-v1"

        private val SLEEP = Regex(
            """(?i)\b(?:i\s+)?(?:only\s+)?(?:slept|got)(?:\s+only)?\s+(?:(?<approx>about|around|roughly|approximately|maybe)\s+)?(?<value>\d+(?:\.\d+)?)\s*(?<ish>-?ish)?\s*(?:h|hr|hrs|hour|hours)\b(?:\s+(?:of\s+)?sleep)?""",
        )
        private val ILL_NEGATED = Regex("""(?i)\b(?:i\s+)?(?:wasn['’]t|was\s+not|am\s+not|not)\s+(?:actually\s+)?ill\b""")
        private val ILL = Regex("""(?i)\b(?:i\s+)?(?:was|am|felt|feel)\s+(?:really\s+|very\s+)?(?:ill|unwell|sick)\b""")
        private val HISTORICAL_MARKER = Regex("""(?i)\b(?:last\s+(?:week|night)|yesterday|earlier\s+this\s+week)\b""")
        private val CURRENT_RECOVERY = Regex("""(?i)\b(?:i(?:'m|\s+am)\s+fine|fine|better|recovered)\s+(?:now|today)\b""")
        private val TODAY = Regex("""(?i)\b(?:today|now|this\s+session)\b""")
        private val FATIGUE = Regex(
            """(?i)\b(?:i\s+)?(?:felt|feel|feeling|am|was)\s+(?:(?:really|very|so|pretty)\s+)?(?:tired|fatigued|wrecked|exhausted)\b""",
        )
        private val NEXT_ACTION = Regex(
            """(?i)(?:\bremember\s+to\b.{1,120}?(?:\bnext\s+time\b|\bnext\s+session\b)|\bnext\s+(?:time|session)\b.{1,120})""",
        )
    }
}

class NoOpNoteInterpreter : NoteInterpreter {
    override suspend fun interpret(request: NoteInterpretationRequest): NoteInterpretationResult = NoteInterpretationResult(
        interpreterKind = InterpreterKind.NO_OP,
        implementationVersion = VERSION,
        annotations = emptyList(),
    )

    companion object {
        const val VERSION = "no-op-context-v1"
    }
}
