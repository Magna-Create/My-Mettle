package dev.kian.lab2b.vlm

import android.os.Build
import org.json.JSONObject
import org.json.JSONArray

object TestReport {
    fun image(i: SelectedImageInfo?): Any = i?.let { JSONObject()
        .put("source_name", it.sourceName).put("source_sha256", it.sourceSha256).put("source_bytes", it.sourceBytes)
        .put("source_width", it.sourceWidth).put("source_height", it.sourceHeight).put("exif_orientation", it.orientation)
        .put("normalisation", it.normalisation).put("normalised_path", it.normalisedPath)
        .put("normalised_sha256", it.normalisedSha256).put("normalised_width", it.normalisedWidth).put("normalised_height", it.normalisedHeight)
        .put("prepared_path", it.preparedPath).put("prepared_sha256", it.preparedSha256)
        .put("prepared_width", it.preparedWidth).put("prepared_height", it.preparedHeight).put("prepared_bytes", it.preparedBytes)
    } ?: JSONObject.NULL
    fun ocr(e: OcrEvidence?): Any = e?.let { JSONObject().put("full_text", it.fullText)
        .put("recognizer", it.recognizer).put("source_sha256", it.sourceImageSha256)
        .put("width", it.width).put("height", it.height).put("processing_ms", it.processingMs)
        .put("blocks", JSONArray(it.blocks.map { b -> JSONObject().put("text", b.text).put("box", box(b.box))
            .put("corners", corners(b.corners)).put("language", b.language ?: JSONObject.NULL)
            .put("lines", JSONArray(b.lines.map { l -> JSONObject().put("text", l.text).put("box", box(l.box))
                .put("corners", corners(l.corners)).put("language", l.language ?: JSONObject.NULL) })) }))
    } ?: JSONObject.NULL
    private fun box(b: OcrBox?): Any = b?.let { JSONArray(listOf(it.left, it.top, it.right, it.bottom)) } ?: JSONObject.NULL
    private fun corners(p: List<OcrPoint>) = JSONArray(p.map { JSONArray(listOf(it.x,it.y)) })
    fun create(request: HarnessSnapshot, result: HarnessSnapshot, turn: InferenceTurn?, image: SelectedImageInfo?, evidence: OcrEvidence?,
        terminal: String, stage: String, ocrMs: Long, totalMs: Long, measurements: JSONObject): String {
        val model = ModelRegistry.get(request.selectedModelId)
        return JSONObject().put("schema", 1).put("test_id", java.util.UUID.randomUUID().toString())
            .put("created_utc", java.time.Instant.now().toString()).put("app_version", BuildConfig.VERSION_NAME)
            .put("runtime", BuildConfig.RUNTIME_VERSION).put("device", Build.MODEL).put("sdk", Build.VERSION.SDK_INT)
            .put("stage", stage).put("terminal", terminal).put("error", result.lastError ?: JSONObject.NULL)
            .put("model_id", model.id).put("model_revision", model.revision).put("registry_fingerprint", model.fingerprint)
            .put("model_asset_bytes", model.sizeBytes).put("requested_backend", request.backend.name)
            .put("effective_text", request.backendEvidence.effectiveText).put("effective_vision", request.backendEvidence.effectiveVision)
            .put("thinking_requested", request.options.thinking)
            .put("thinking_transport", if (request.options.thinking) "Gemma marker at start of true system turn; simplified export template" else "OFF; baseline export template")
            .put("thought_channel_observed", result.rawOutput.contains("<|channel>thought"))
            .put("max_generated_tokens_including_thinking", request.options.maxTokens)
            .put("token_limit_reached_or_possible", (result.nativeMetrics.getOrNull(1) ?: 0) >= request.options.maxTokens)
            .put("pipeline", if (stage == "LOCALISE") "VISION_ONLY" else request.pipeline.name)
            .put("stateless", true).put("preset", request.preset.name).put("system_mode", turn?.systemMode?.name ?: "NONE")
            .put("system_text", turn?.system ?: JSONObject.NULL).put("user_content", turn?.user ?: JSONObject.NULL)
            .put("instruction", if (stage == "LOCALISE") ExperimentPrompts.locate else request.promptText)
            .put("original_image", image(request.originalImage ?: image)).put("input_image", image(image))
            .put("image_supplied_to_model", turn?.imagePath != null)
            .put("crop", request.crop?.let { JSONObject().put("label", it.label).put("ratios", JSONArray(listOf(it.left,it.top,it.right,it.bottom))) } ?: JSONObject.NULL)
            .put("localisation_report", if (stage == "LOCALISE" || request.localisationReport.isEmpty()) JSONObject.NULL else JSONObject(request.localisationReport))
            .put("ocr_reading_order", request.ocrReadingOrder).put("ocr", ocr(evidence)).put("ocr_evidence_supplied", evidence?.let { OcrFormatter.format(it, request.ocrReadingOrder) } ?: JSONObject.NULL)
            .put("ocr_cache_hit", evidence != null && result.ocrCacheHit).put("ocr_wall_ms", ocrMs)
            .put("cold_load_ms", request.timing.loadMs ?: JSONObject.NULL)
            .put("model_wall_ms", result.timing.totalGenerationMs ?: JSONObject.NULL)
            .put("first_final_output_ms", result.timing.firstOutputMs ?: JSONObject.NULL)
            .put("operation_wall_ms", totalMs).put("native_metrics_order", "prompt_tokens,generated_tokens,vision_us,prefill_us,decode_us,cancelled")
            .put("native_metrics", JSONArray(result.nativeMetrics)).put("loaded_pss_kb", request.memory.loadedPssKb ?: JSONObject.NULL)
            .put("measurements", measurements).put("raw_response", result.rawOutput).put("final_response", result.output)
            .put("physical_correctness", "UNSCORED; human review required").toString(2)
    }
}
