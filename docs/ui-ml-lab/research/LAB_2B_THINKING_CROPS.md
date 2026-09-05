# LAB-2B 0.3: thinking, crops, E4B and measured test exports

Status: implementation validation in progress; physical acceptance pending. LAB-2C not started.

## Physical evidence driving this build

Kian tested the prior MNN 3.6.1 harness on S25 Ultra. Gemma E2B CPU VISION ONLY returned the strongest equipment results in the supplied sample. On the dedicated kg prompt, stack 2 included 17 of 20 kg values with no extra main-list values, but omitted 100/113/120. Stack 1 misclassified add-ons and mixed a pounds value into kg. VISION + OCR introduced additional false numbers, units and add-on assignments. Kian reported under 10 seconds for vision only versus up to 30 seconds with OCR. These are user observations, not separately instrumented phase measurements. The earlier “OCR only” records are treated as recogniser evidence, not proven final-model OCR-only responses.

Qwen3.5 was fast but produced false numerical sequences and repetition. Qwen3-VL produced degenerate The/A outputs; its current route fails basic control correctness, without establishing whether the fault is model/export/runtime/decode integration. Gemma GPU was too slow in the observed trial; GPU vision correctness remains unestablished. Keep CPU and VISION ONLY default. No model is certified for equipment import.

## Thinking source truth

The pinned E2B export `ce18884f154ce405545f1acda5c5c8fdd9c1280c` and E4B export `fec885bae19e9363cebd36de22527b340bc6b450` both have a simplified Jinja template that inserts system content verbatim but does not reference enable_thinking. Consequently a flag-only switch is ineffective. Metadata was read directly by CI job 101344043734, run 33980235084. No weights were fetched.

Google's canonical template places `<|think|>` at the start of the first system turn: https://huggingface.co/google/gemma-4-E2B-it/blob/main/chat_template.jinja . This build implements that placement explicitly in ThinkingPrompt for the two Gemma entries. OFF retains baseline behaviour. The actual system text is exported, including the control marker; true-system mode is used even with the NONE preset when thinking is ON. No pretend user-preface mode is used.

Thinking OFF/ON is independent of ENGLISH GROUNDED/custom system text. Changing it or the token budget unloads the current engine; tap Load again. ON initially selects 2048 generated tokens if the old limit was 512. Available total generation limits are 512/1024/2048/4096, including thinking and final answer; prompt + image + generated budget is still capped at 8192. Raw stream and final answer are separate export fields; observed thought-channel markers are recorded. First raw output and first final output are separately timed. Runtime tokenisation/physical thinking behaviour requires device confirmation. A missing/unfinished final answer must not be scored as successful extraction.

## E4B

Exact source: https://huggingface.co/taobao-mnn/gemma-4-E4B-it-MNN/tree/fec885bae19e9363cebd36de22527b340bc6b450 . MNN 4-bit graph + external weights + tokenizer.mtok + PLE + vision graph/weights, 4,939,909,375 bytes. Full pins are in model-registry.json and the typed registry. Audio is excluded as for E2B. Same MNN runtime, same one-owner lifecycle, CPU default, OpenCL text/CPU vision experimental. Physical load/vision/RAM acceptance remains pending. Existing three model asset fingerprints are unchanged; no redownload is required.

## Crop workflow

Manual: Draw crop on original image, drag a rectangle, label the region, Apply crop, inspect exact prepared input, Send. Crop coordinates are ratios in the orientation-normalised full source (subject to the existing 16 MP/4096-edge decode cap). Android crops that source before the 1600-edge prepared-model resize. It never crops an AI-generated reconstruction or repeatedly crops the previous crop. Restore full image is explicit. Separate main-stack and add-on regions should be tested separately.

Guided: Stage 1 uses only the original image, a dedicated localisation prompt and the current thinking/budget setting. The loaded engine returns at most four labelled ratio boxes. Strict JSON/coordinate validation rejects malformed/out-of-range/inverted proposals. Review suggested crops opens the same editable visual crop editor. Applying a reviewed proposal selects it for Stage 2; Send uses the usual instruction/system/pipeline. Each stage is stateless; localisation text is not replayed into extraction. This is a human-reviewed two-stage workflow, not unattended multi-crop extraction or automatic weight aggregation. No guessed numerical merge is performed.

Every extraction records original and active image identities, crop ratios/origin/preparation time and, for reviewed proposals, the complete localisation report. Separate stage measurements permit comparison excluding human review delay. Crop files are session-only, while JSON reports persist. Selecting a new original or relaunching reclaims session images. Reports keep hashes/coordinates; image pixels are not embedded in JSON.

## OCR

Existing bundled ML Kit Latin 16.0.1. Full-frame and crop OCR have distinct normalised-image hashes and cache entries. Raw results preserve blocks/lines/boxes/corners/language. Optional Top to bottom sorts evidence lines by top coordinate then left; it does not infer units, repair digits, filter pounds, group plates, or invent missing labels. Original recogniser order remains available. Exact supplied evidence appears in each report. OCR stays explicitly supplementary. VISION ONLY never adds OCR; VISION + OCR and OCR ONLY automatically recognise the active image when cache is missing.

## Measurements and export

Save ALL test results as JSON uses Android CreateDocument and persists a JSON array chosen by the user. Reports are also atomically retained in internal app storage `files/lab2b/reports/` across force-close/relaunch. Copy complete last test replaces the need to manually assemble model/prompt/image/OCR/output/diagnostics. Raw/final output, statelessness, model/revision, selected/effective backends, system transport, crop and evidence provenance, output cap and possible truncation, cold load, OCR wall time, model wall time, first raw/final output and total operation time are included.

Measurements run only during a requested localisation/extraction. PSS and battery/thermal gauges are sampled every second; sampled peak may miss short spikes. CPU temperature is not inferred from battery temperature. API35 SystemHealthManager enumerates available power monitors and reads cumulative microjoules/timestamps before/after. Each monitor is recorded independently with its type; overlapping rails/consumers must not be summed. Missing counters, non-advancing snapshots, unchanged counters and resets are unavailable/unresolved, not zero energy. Device/component totals include other apps/screen and are not app-only; no invented idle subtraction. Query timeout is bounded. Raw battery current/voltage/remaining-energy/charge counters are supplementary, not converted into precise five-second app energy.

Sources: https://developer.android.com/reference/android/os/health/SystemHealthManager , https://developer.android.com/reference/android/os/PowerMonitorReadings , https://developer.android.com/reference/android/os/BatteryManager . S25 monitor availability and measurement resolution remain pending. Sample overhead is included. Compare fixed brightness, unplugged, similar temperature, same prompt/image/output limits, with loading separated.

## Termux

Use the existing checkout and imported `.deps/prebuilt` libraries. `python tools/native_bundle.py reuse` verifies every library hash and all native source/build hashes. A narrowly pinned compatibility migration accepts the previous adapter source hash only when its JVM native declarations remain byte-identical. It changes no native library. Unknown native revisions/build changes are rejected. New builds also accept their own matching manifest. This avoids downloading the APK or another native bundle for this Kotlin-only extension.

```bash
cd ~/My-Mettle
git switch agent/ui-ml-lab
git pull --ff-only origin agent/ui-ml-lab
cd experiments/lab2b-vlm-harness
python tools/native_bundle.py reuse
./gradlew --no-daemon -Plab2bPrebuiltNative=true \
  -Pandroid.aapt2FromMavenOverride="$(command -v aapt2)" \
  testDebugUnitTest assembleDebug lintDebug
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/LAB-2B-VLM-Harness-debug.apk
```

Open the copied APK in Samsung My Files. Build with the same Termux debug key to update in place and retain downloads. If there is no previously imported native bundle, the reuse command stops with a specific diagnostic; do not disable hash verification.
