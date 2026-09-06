# LAB-2B 0.5: two-crop placard extraction

Branch `agent/lab2b-ocr-stack`. Parent `f858f8d803efc31cd6a1e1673e2d95e8411ea857`. LAB-2B IMPLEMENTATION READY / PHYSICAL ACCEPTANCE PENDING.

Validated implementation: `a2fb835201f902d1584daaa5953706338ae97b6b`. [Actions run 34038988169](https://github.com/Magna-Create/My-Mettle/actions/runs/34038988169) SUCCESS: 55 unit tests, zero failures/errors/skips, lint, APK assembly, source/binary isolation, all ten native ELF LOAD alignment checks, APK ZIP alignment and Termux prebuilt-path rebuild. Current/known-legacy native reuse passes and altered C++ reuse is rejected. APK 36,621,333 bytes; SHA-256 `0e2108bd5925c08e62676fa8a3cebab443a2d5ab9e3bf0dc55788dcfb5e3c8f7`. Artifact `lab2b-vlm-harness-debug`, ID `9991132288`. Native payload remains 20,327,800 bytes. See LAB_2B_05_NATIVE_AUDIT.txt.

No physical placard OCR accuracy claim follows from these unit/build results. Camera/crop/export UI and process-relaunch acceptance remain S25 checks. No production runtime, N-BIO, Room/equipment code or model binaries changed. A Termux build signed locally may have a different APK SHA-256. LAB-2C and machine masking NOT STARTED.

## Interaction and scope

Weight extraction and Placard extraction are separate screens with a direct switch near the top. Select or photograph one machine placard. Draw one rectangle around the entire placard, then one around the logo lettering from the same original photo. Logo crop can be skipped if unnecessary. No field-specific rectangles, AI model, catalogue/network lookup or model download required. Tap Read placard + logo. Review the automatically populated fields, leave unsupported specifications blank, confirm and save JSON.

Brand and machine name are the primary targets. Model identifier, starting resistance and explicitly labelled ratios are optional. Missing specifications do not block export, require more crops or require manual entry. Instructions/diagrams are not interpreted as machine specifications. Starting resistance is not the first weight-stack setting. A printed ratio is retained verbatim in its printed order, without inferring mechanical meaning or converting stack loads.

## Baseline extraction rules

`PlacardParser` is a bounded English text baseline, version placard-rules-1. It operates on geometry-ordered lines and retains source SHA, crop identity, text, box, rule and unit/number where applicable for every candidate. OCR full text, block/line geometry, corners and language metadata remain in the export.

- Brand: exact wordmark matching against a small seed list (23 names in source), explicitly labelled manufacturer/brand text, and neighbouring stacked wordmark lines. Not a comprehensive gym brand catalogue. One-character fuzzy suggestions are restricted to logo words >=6 characters and are never silently selected. Purely graphic logos and unknown unlabelled wordmarks can remain unresolved.
- Machine name: explicitly labelled machine/equipment/exercise name, or exact matching of a curated machine-name phrase. This first baseline does not guess arbitrary headings or extract machine names from usage sentences. Composite names, unusual headings and non-English labels are known coverage gaps; raw OCR is available for evaluation and optional correction.
- Model identifier: only an explicit model/product-code label, with the OCR identifier preserved. No general grammar correction or O-to-0 replacement in IDs.
- Starting resistance: explicit starting/initial/unloaded-resistance labels with a single number and kg/lb unit. Narrow numeric character repairs become unselected candidates. No lb conversion; bare weights, max-user weights, ranges and weight-stack capacity are not starting resistance. Dual-unit lines and unit-in-heading layouts are currently unresolved, not silently truncated.
- Ratio: an explicit pulley/cable/resistance/weight-ratio label and positive a:b value. Bare ratios or exercise tempo ratios are ignored.
- A label's next line can supply its value only when nearby and horizontally overlapping. This avoids taking a number from a neighbouring column. Conflicting candidates stay unselected. Every field can be reviewed against its original text, chosen, cleared or manually corrected.

No confidence percentages invented. Exact rule matches prefill the form but the combined export requires human confirmation. Corrections/unknowns remain visible. Human edits are explicitly distinguished from source-supported values. A brand match does not authorise looking up or inferring model-specific specifications.

## Image, persistence and lifecycle

Reuse the existing bundled ML Kit Latin recognizer 16.0.1, source orientation normalisation, crop editor and ORIGINAL / grayscale contrast / grayscale sharpen / black-white filters. Full source resolution is used within existing 16 MP / 4096-edge decode bounds; OCR does not use the 1600-edge crop-editor preview. No runtime/JNI/native dependency changes.

Each crop has its own filter; changing it invalidates that crop's OCR plus the combined review. Replacing the photo clears both crops/results. Exact-input SHA cache is bounded to eight in-process entries. Both crops are processed sequentially, off the UI thread. Cache hits and fresh recognizer/total times are visible. All filters remain physically experimental; no automatic multi-filter consensus in this build.

App-private `files/lab2b/placards/` holds original/crop/filtered PNGs plus atomic draft.json. The process owner survives activity recreation; photo/crop/OCR/field selections and confirmation restore from completed drafts after process death. Pending picker/camera/export callbacks wait for asynchronous restoration. Replacing a photo or clearing a test cleans only superseded placard sources after a successful draft save. Weight capture/model storage are separate.

`lab2b.placard.v1` is a diagnostic/review export, `production_import_compatible=false`. Contains nullable fields, raw candidates, chosen evidence, human-edited origins, crop bounds, exact OCR input paths/hashes and timings. No production schema or My Mettle import added.

## Weight fixes from physical feedback

Kian reported the 0.4 UI effectively blocked export without both captures and intentionally used the main top plate as the second capture. This is UX evidence, not a reading error by Kian. Add explicit No separate add-on weights action: clears the previous add-on, persists NONE, returns to main capture and enables export once the main capture is reviewed. NOT_CHECKED also allows export without asserting absence; CAPTURED requires add-on review. Replacing the main image clears previous add-on data to avoid mixing machines.

Weight export schema v2 uses genuine numeric array literals, fixing Android JSONArray(Collection)'s BigDecimal-to-string wrapping (`"1E+2"` etc.). Old v1 saved capture drafts still restore. New unit tests cover both optional-add-on paths and non-string, non-exponent numeric output.

## Termux

The previous handoff omitted the fresh-session Gradle PATH setup. `tools/build_termux.sh` now locates existing Gradle 9.1.0, or downloads and verifies its official binary distribution hash if absent. It reuses the previously imported native bundle, builds/tests/lints, and copies the APK to Downloads. It does not download model weights.

```sh
cd ~/My-Mettle
git fetch origin
git switch agent/lab2b-ocr-stack
git pull --ff-only origin agent/lab2b-ocr-stack
bash experiments/lab2b-vlm-harness/tools/build_termux.sh
```

Open Samsung My Files → Downloads → LAB-2B-OCR-debug.apk and update using the same Termux signing key. Existing model installations are preserved. The Gradle archive pin is from https://gradle.org/release-checksums/ . No APK download or new native bundle needed.

## Physical acceptance

- Start from weight extraction, switch to placard extraction. Photo → placard rectangle → logo rectangle → Read.
- Try brand + machine name + diagrams with no specifications. No false ratio/starting resistance; blank optional fields export successfully.
- Try a plate-loaded placard with stated starting resistance. Check raw label, units and source association.
- Try a text logo and a graphic logo; unknown is acceptable for the latter. Record fuzzy-match errors and missed machine headings rather than hiding them with manual edits.
- Save original diagnostics before editing; then review and export the chosen fields. Check camera, picker, cancellation and rotation; force-close/relaunch and check restoration.
- Return to weights. No add-on → confirm main → export. Export main_stack_kg should contain JSON numbers, not strings. Confirm stale add-on data cleared on a new main photo.

Background removal/machine masking and small specialised language models remain later experiments. Physical greenlighting of both OCR extraction tasks precedes that work.

## 0.5.1 physical-feedback fixes

The two Hammer Strength exports contain correctly recognised `Start 18 lb./8.2Kg.` and `Start 90 lbs./40.8kg.`. Version 0.5.0 rejected both in the parser. Version 0.5.1 accepts Start and dual lb/kg labels, prefers the kg value actually printed, preserves both in raw evidence, and leaves conflicting units or character repairs as review candidates. Ratios/ranges/usage instructions are not inferred. Rules version advances to placard-rules-2; old field confirmations are invalidated when reparsed.

Stack export ending 8262745 stops at 120 because OCR read `127 ko` and `134 kog`, rejected by the strict parser. These are now retained as unchecked UNIT_CANDIDATE rows. Export ending 8195042 contains all 20 main values. Export ending 8140686 is already severely corrupted at raw OCR, including `O59 kg`, `6 k`, `3 kg` and missing lines; parser changes cannot restore absent evidence.

Checked version diff: OcrProcessor, OcrImageEnhancer, ImagePreprocessor, CropImages and WeightOcrParser were unchanged from the final 0.4 build to 0.5.0. Older successful stack-1 capture and the failed capture share original SHA f7ee5a2868d373d51c959472256fc567486bc8eee3e813f791df872dfb65abe1 but use different crops: 478×795 versus 607×817 (both contrast). The later original-filter crop is 439×783. These are not controlled identical-input comparisons; root cause of raw OCR degradation remains open, not dismissed as user error or declared fixed.

Added Compare four filters on the identical selected crop. It retains each exact input, raw OCR, recognised values, candidate count, warnings, timings and cache status in diagnostic JSON without overwriting reviewed values or automatically merging. Prominent sequence warning added before weight review. This allows a controlled comparison on the failing input; filter consensus remains deferred.

No Life Fitness OCR JSON is in this attachment batch. Graphic/script-logo recognition is not fixed by these parser patches. A crop classifier is a plausible later approach because the user already locates the logo; no YOLO/runtime or logo dataset added here. No new OCR accuracy claims.
