package dev.kian.mymettle.context

import dev.kian.mymettle.data.local.MyMettleDatabase

/**
 * Save-flow coordinator. Callers persist canonical raw text first, update their UI normally, then
 * invoke this optional foreground step. Every failure is contained here and can never roll back a note.
 */
class ContextInterpretationCoordinator(
    database: MyMettleDatabase,
    private val nano: NoteInterpreter = NanoNoteInterpreter(),
    private val nanoCapabilityProbe: suspend () -> NanoRuntimeCapabilities = {
        (nano as? NanoNoteInterpreter)?.capabilities()
            ?: NanoRuntimeCapabilities(PromptApiStatus.NOT_CHECKED)
    },
    private val rules: NoteInterpreter = RulesNoteInterpreter(),
    private val noOp: NoteInterpreter = NoOpNoteInterpreter(),
    private val persistence: ContextInterpretationRepository = ContextInterpretationRepository(database),
) {
    suspend fun interpretSaved(source: CanonicalNoteSource): Result<Unit> = runCatching {
        val request = NoteInterpretationRequest(source.text, source.scope, source.exerciseName)
        try {
            val nanoResult = nano.interpret(request)
            persistence.persist(
                source = source,
                result = nanoResult,
                outcome = if (nanoResult.annotations.isEmpty()) {
                    InterpretationExecutionOutcome.NO_ANNOTATIONS
                } else {
                    InterpretationExecutionOutcome.SUCCESS
                },
                promptApiLibraryVersion = NanoNoteInterpreter.PROMPT_API_LIBRARY_VERSION,
            )
        } catch (nanoUnavailable: NanoUnavailableException) {
            fallbackToRules(
                source = source,
                request = request,
                capabilities = nanoUnavailable.capabilities,
                reason = nanoUnavailable.message ?: "Nano unavailable",
            )
        } catch (nanoFailure: Throwable) {
            fallbackToRules(
                source = source,
                request = request,
                capabilities = NanoRuntimeCapabilities(
                    promptApiStatus = PromptApiStatus.ERROR,
                    probeFailure = nanoFailure::class.java.simpleName,
                ),
                reason = "Nano extraction rejected/failed: ${nanoFailure::class.java.simpleName}",
            )
        }
    }

    suspend fun capabilities(): NanoRuntimeCapabilities = nanoCapabilityProbe()

    private suspend fun fallbackToRules(
        source: CanonicalNoteSource,
        request: NoteInterpretationRequest,
        capabilities: NanoRuntimeCapabilities,
        reason: String,
    ) {
        try {
            val ruleResult = rules.interpret(request).copy(capabilities = capabilities)
            persistence.persist(
                source = source,
                result = ruleResult,
                outcome = InterpretationExecutionOutcome.FALLBACK_SUCCESS,
                fallbackReason = reason,
                promptApiLibraryVersion = NanoNoteInterpreter.PROMPT_API_LIBRARY_VERSION,
            )
        } catch (rulesFailure: Throwable) {
            val noOpResult = noOp.interpret(request).copy(capabilities = capabilities)
            persistence.persist(
                source = source,
                result = noOpResult,
                outcome = InterpretationExecutionOutcome.FALLBACK_SUCCESS,
                fallbackReason = "$reason; Rules failed: ${rulesFailure::class.java.simpleName}",
                promptApiLibraryVersion = NanoNoteInterpreter.PROMPT_API_LIBRARY_VERSION,
            )
        }
    }
}
