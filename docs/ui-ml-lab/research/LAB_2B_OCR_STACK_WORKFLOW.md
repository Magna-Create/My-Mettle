# LAB-2B 0.4 OCR stack experiment

Branch: `agent/lab2b-ocr-stack`, forked from `agent/ui-ml-lab` at `de40aa37c4314713ae06b55ee0a0550906d02f72`.
Status: implementation validation in progress; physical acceptance pending. LAB-2C not started.

## Scope and evidence

Kian's four full/cropped stack OCR samples preserve almost every kg label. Main sequence in the supplied photographs: 4.5, 11, 18, 25, 32, 39, 45, 52, 59, 66, 73, 79, 86, 93, 100, 107, 113, 120, 127, 134 kg. Stack 1 has separately labelled 2.3 kg add-ons. This reference is a human reading, not embedded inference output.

OCR samples include `12Okg`, `1lkg`, `1 lkg`, `100 k`, `134ka`. Cropping is not uniformly better: the latter damaged units occur in cropped samples. All pasted blocks say recognizer block order. Fixed legacy Copy OCR to honour the selected ordering; the new workflow always copies top-to-bottom evidence while retaining the recognizer's original full text.

The new default launcher is a separate guided screen: choose main stack or add-on → take photo / choose image → manual crop → OCR filter → run OCR/extract → review kg rows → save combined JSON. Existing 0.3 model/thinking/crop/measurement features remain accessible through Open model comparison harness. No weights need downloading or loading for this workflow.

## Extraction decision

Use deterministic Kotlin first. Exact kg labels already carry sufficient evidence; sending the full image or a long OCR wrapper to a language model added errors and latency in the preceding physical trials. This screen never creates a model engine, dispatches an image or dispatches OCR to a language model. Existing OCR ONLY remains available in the model comparison screen as a separate experiment; model answers cannot overwrite reviewed extraction.

Whole-line parser rejects lb variants and unrelated serial numbers; does not convert lb to kg. Numeric O/o→0 and I/l/|→1 corrections are limited to numeric portions of kg candidates containing an actual digit. Damaged units k/ka are candidates, not established kg. Every correction starts unchecked. Human inclusion/edit is explicit; original text, geometry, origin, changes and inclusion remain in JSON. Unrelated/add-on rows can be unchecked. Values are positive decimals <=2000 kg.

Rows are ordered using OCR geometry before checking inversions, duplicates and unusually large gaps. Output is then separately sorted and deduplicated numerically. The stack has rounded alternating 6/7 kg steps; a constant-seven filler would be wrong. Warnings are heuristic, not proof of missing labels. No absent weights are inserted. Future production sequence proposals must remain separate from observed values and need confirmation. This branch makes no production pattern-recognition changes.

Add-on capture is independent and optional. Repeated labels do not establish quantity or engagement; these fields remain null. Main-stack labels are cumulative settings, not individual plate masses.

## Image / OCR pipeline

Bundled ML Kit Latin recognizer 16.0.1 unchanged. Camera uses the installed camera app via a full-resolution FileProvider destination; document picker also available. Images are privately copied (64 MB limit), EXIF normalised using the existing preparation pipeline, and cropped from the normalised source, not the small preview. Existing source decoder limits remain 16 MP / 4096 edge; OCR uses the normalised source, not the 1600-edge VLM image.

ORIGINAL is default. Optional grayscale contrast (1.35 around midpoint), grayscale four-neighbour sharpen, and fixed-threshold black/white (128) produce separate PNG OCR inputs. Filtered PNGs are activated atomically. They are experimental, not asserted accuracy improvements. Underexposure, shadows and worn labels may get worse. Always inspect Open exact OCR input and compare original against the chosen filter. Filtering cannot restore text absent from the pixels.

ML Kit primary guidance emphasises focus and sufficient character resolution: https://developers.google.com/ml-kit/vision/text-recognition/v2/android . General preprocessing possibilities are documented by Tesseract: https://tesseract-ocr.github.io/tessdoc/ImproveQuality.html ; these are not ML Kit-specific proof. No additional dependency or conversion runtime is introduced.

Cache is keyed by the exact filtered/normalised PNG SHA-256, bounded to eight entries in process. Capture/crop/filter changes invalidate that capture's OCR, selections and confirmation. OCR recognizer time and total preprocessing/extraction wall time are shown; cache hits are explicitly labelled and retain their original recognizer timing. Sources, crops, filter identity, exact OCR input, full text, blocks, lines, boxes, corner geometry and language metadata are exported. No confidence values fabricated.

## Persistence and export boundary

App-private `files/lab2b/stack-captures/` stores source/crop/filtered PNGs and atomic `draft.json`. Completed captures, OCR and review restore after process death. Operations are serialized off the UI thread where image/OCR work occurs; activity recreation attaches to the process owner. In-flight operation lost to force-close is retried from the last atomic draft. Superseded source/crop folders are cleaned only after a successful draft save. Clear capture affects its capture and review, not models or the other capture.

Save reviewed JSON requires a confirmed main capture and either no add-on capture or a confirmed add-on capture. Correction candidates may remain excluded. Copy diagnostic JSON also allows unfinished evidence. Export schema `lab2b.ocr-stack.v1` includes numeric main_stack_kg and separate_add_on_kg arrays, all raw/corrected records, warnings, image/OCR provenance, review status and empty inferred_weights_kg. `production_import_compatible=false`: this is a draft for evaluating a future contract, not an implemented My Mettle import. No credentials/network/model needed by extraction.

## Termux update from the existing installation

```sh
(
set -e
cd ~/My-Mettle
git fetch origin
git switch agent/lab2b-ocr-stack
git pull --ff-only origin agent/lab2b-ocr-stack
cd experiments/lab2b-vlm-harness
python tools/native_bundle.py reuse
./gradlew --no-daemon -Plab2bPrebuiltNative=true \
  -Pandroid.aapt2FromMavenOverride="$(command -v aapt2)" \
  testDebugUnitTest assembleDebug lintDebug
cp app/build/outputs/apk/debug/app-debug.apk \
  ~/storage/downloads/LAB-2B-OCR-Stack-debug.apk
)
```

Uses the already imported source-matched native bundle. Native code/JNI is unchanged. Open the copied APK in Samsung My Files → Downloads and update the installed harness using the same Termux signing key. No APK/native ZIP download required. Do not uninstall merely to switch branches: downloaded models persist in the existing app-specific model directory.

## Physical checklist

1. Launch shows OCR weight stack 0.4. Main stack selected; no model load.
2. Choose stack 1. Crop to main plates including 4.5 kg top plate, exclude the 2.3 kg add-ons. ORIGINAL → Run OCR + extract kg.
3. Inspect exact OCR image and rows. Confirm damaged-unit/numeric candidates from the image or edit; leave unsupported values unchecked. Confirm list only after checking.
4. Switch to Separate add-on, choose/crop its label, OCR and review. Main result must remain retained.
5. Save reviewed JSON, force-close/relaunch, verify captures/review restored. Check exported arrays, warnings and provenance.
6. Repeat with stack 2. Compare ORIGINAL with a single filter at a time, save each reviewed result. Filters are not correctness-approved by compilation.
7. Camera capture, cancel picker, rotation during OCR, clear one capture and branch update preserving downloaded models remain physical checks.
