# OCR → My Mettle Native: implementation and lessons learned

> **Status (28 October 2026):** developer migration plan, **not** an implemented production integration. Source is the isolated `agent/lab2b-ocr-stack` experiment; target is the Kotlin/Compose Native app, currently developed on `agent/n-bio-vnext-workout-ui`. Do not merge the experimental branch wholesale. The experiments' documentation contains historical checkpoints; its latest weight-review record accepts the **workflow**, not complete OCR recognition accuracy. Keep this guide source-led and update its evidence column after every physical gate.

## 1. Goal and boundaries

Enable a gym user to photograph a machine's weight stack and placard, see which kg/lb columns and printed specifications were actually read, correct them, then explicitly save verified equipment facts. A later optional *live camera guide* may improve framing and confidence before capture. It is not required for the first production OCR feature.

Pipeline and ownership:

```text
camera / document picker
    → privately copied, EXIF-normalised source photograph
    → guided crop(s) and optional filter comparison
    → ML Kit OCR: text + original geometry + image identity
    → deterministic unit/column and placard candidates
    → explicit human review, correction, provenance
    → domain validation + canonical equipment fact/version (separate gate)
    → optional equipment binding/availability
    → N-BIO observes canonical evidence only; never raw OCR guesses
```

Do not confuse **observed print**, **interpretation**, **derived proposals** and **confirmed/corrected facts**. The lab schema `lab2b.ocr-stack.v1` expressly sets `production_import_compatible=false`. An export is not a Room migration. Do not change equipment history, existing workout entries, or N-BIO prescription authority as a side-effect of taking a photo.

## 2. Authoritative source map

| Evidence / behaviour | Current source of truth | Migration decision |
| --- | --- | --- |
| Guided main-stack + optional add-on capture, crop, save | [LAB_2B_OCR_STACK_WORKFLOW](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_OCR_STACK_WORKFLOW.md), `WeightScanActivity.kt`, `WeightScanOwner.kt` | Port *behaviour* to Compose/ViewModel; do not port the experiment's raw `Activity` layout. |
| Unit/column uncertainty and sloped manual selector | [LAB_2B_OCR_COLUMNS](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_OCR_COLUMNS.md), `WeightColumns.kt`, `ColumnEditor.kt` | Retain normalized coordinates, user-confirmed kg/lb, explicit multi-number geometry, and manual fallback. |
| Accepted review and implausible numeric labels | [LAB_2B_OCR_REVIEW_FREEZE](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_OCR_REVIEW_FREEZE.md), `WeightOcrParser.kt` | Preserve `>=1000 kg` attention warning and unchecked default; no silent decimal insertion or numeric upper cutoff. |
| Placard crop plus optional logo crop | [LAB_2B_PLACARD_EXTRACTION](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_PLACARD_EXTRACTION.md), `PlacardOwner.kt`, `PlacardParser.kt`, `StartingResistanceParser.kt` | Port labelled evidence and review, not catalog/spec guesses or equipment recognition. |
| Exact OCR evidence, source orientation and filters | `OcrProcessor.kt`, `ImagePreprocessor.kt`, `OcrImageEnhancer.kt`, `OcrJsonCodec.kt` | Retain raw text, blocks/lines/elements, boxes/corners, image SHA/dimensions, and actual processing times. |
| Design and domain authority | [EQUIPMENT_VISION_CONTRACT](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/EQUIPMENT_VISION_CONTRACT.md), [UX_DECISIONS](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/UX_DECISIONS.md); Native `domain/equipment/EquipmentModels.kt`, `equipment/LocalEquipmentInterpreter.kt` | Review-first candidate adapter; canonical instance/fact versions and historical semantics belong to Native equipment domain. |

The Lab documentation links deliberately point to the OCR branch because those files do not exist on the Native branch. All Kotlin source names above refer to `experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/` **on the OCR test branch**. The production destination already has CameraX 1.6.1 dependencies (per its `app/build.gradle.kts`), but does **not** thereby already have an OCR text-recognition integration; the separate Lab declares bundled `com.google.mlkit:text-recognition:16.0.1`. Check current pins/manifest/device support at implementation time rather than blindly duplicating dependencies.

## 3. What the experiments actually established

**Source/build evidence.** LAB-2B 0.4/0.5/0.6 documents record successful unit-test, lint, APK and native packaging checkpoints. LAB-2B uses bundled Latin ML Kit, while the experimental model comparison uses MNN CPU and experimental GPU. OCR-first extraction does **not** require the VLM runtime, model weights, native JNI adapter or DownloadManager.

**Human evidence.** The 0.6.1 review document records acceptance of the **weight-stack selection/review interaction** on 6 September 2026, not perfect recognition. Recorded physical failures include a printed `115.5` interpreted as `1155`, joined `59130` labels, and incomplete readings. The 0.4 and 0.6 build notes' “physical acceptance pending” apply to those individual checkpoints and should not erase the later workflow acceptance; neither claim certifies the camera or OCR accuracy generally. Placard recognition remains separately subject to physical evaluation.

**Lessons to preserve:**

1. **OCR text is evidence, not truth.** Preserve source glyphs and boxes. Restrict O/o→0, I/l/|→1 to plausible numeric candidates and mark corrections for review; never globally rewrite model identifiers.
2. **Never infer kg from decimal position, left/right position or an lb:kg ratio.** Paired columns can suggest candidates, including sloping strips, but unit ownership remains explicitly confirmed.
3. **Cropping and preprocessing are experiments, not universal enhancements.** In supplied stack photos, some unit damage appeared **after** crop. ORIGINAL is the baseline. Greyscale contrast, sharpen and threshold each retain their own exact-input hash. Never silently blend or vote values from filters.
4. **Do not fabricate a regular stack.** A real stack included alternating 6/7 kg increments; gap/outlier heuristics are warnings, not missing weights. Main stack labels represent cumulative **settings**, not individual plate masses.
5. **Add-on selection and machine mechanics are separate.** Repeat text does not imply add-on plate count or whether engaged; unknown pulley ratios/starting resistance remain unknown.
6. **Track every invalidation.** Changing photo/crop/filter/column or unit invalidates stale OCR and/or review according to the recorded rules. A new image must never inherit old inclusion flags.
7. **Expose observability.** Keep image hash, orientation, pixel dimensions, crop/filter identity, geometry, OCR timing, cache hit and edit provenance. Never invent confidence percentages if the recognizer does not supply them.
8. **Use atomic, bounded storage.** Lab stores private source/crop/filter assets and atomic draft JSON. Preserve restore-after-force-close semantics, keep image decoding bounded (documented 16 MP / 4096 edge), off-main-thread work, and bounded cache; do not copy test absolute file paths into production entities.
9. **Keep model research separate.** The prior NPU route failed packaging portability gates; MNN/GPU results are not a proven camera-recognition contract. A deterministic OCR + review release should ship without multi-GB model dependencies.
10. **Avoid brand-to-physics leaps.** An OCR-supported manufacturer or model name does not establish pulley ratio, rail angle, starting resistance, effective resistance or muscle contribution.

## 4. Safe Native integration shape

Create *interfaces* before copying implementation:

```kotlin
interface EquipmentOcrRecognizer {
    suspend fun recognise(input: PreparedOcrImage): OcrObservation
}
interface EquipmentEvidenceInterpreter {
    fun stackCandidates(observation: OcrObservation, selection: ColumnSelection?): StackReviewDraft
    fun placardCandidates(placard: OcrObservation, logo: OcrObservation?): PlacardReviewDraft
}
interface EquipmentFactReviewGateway {
    suspend fun validateAndSave(reviewed: ConfirmedEquipmentDraft): EquipmentSaveResult
}
```

These are **illustrative seams**, not existing project APIs. Align final names/types with current `EquipmentId`, `EquipmentInstance`, `EquipmentFactVersion`, `EquipmentFactProvenance`, existing equipment repository, execution-profile identity and session binding semantics. Use a schema adapter only **after** evidence-review gates pass. Never make image file paths or ephemeral OCR text the equipment primary key.

Suggested UI state: `EMPTY → CAPTURING/IMPORTING → CROPPING → ANALYSING → REVIEW_REQUIRED → VERIFIED_DRAFT → SAVING → SAVED`, plus `FAILED` and `CANCELLED`. A capture can be independently reset without silently overwriting another capture. A reviewed draft must be invalidated by an upstream evidence change. Distinguish **Use this session**, **Save as available equipment** and **Make default** if those binding features are actually implemented; otherwise omit/disable rather than faking the distinction.

Data contract needs: source image identity/EXIF, crop-relative geometry, observed text/elements, candidate field and unit, corrected value, `observed/interpreted/derived/human-corrected` provenance, versioned confirmation, model recognizer/filter versions, and unknown/ambiguous states. Persist only what is necessary for audit/user correction under a clear retention policy; capture/session caches must be bounded and deletable. Do not use `WeightScanOwner`/process singletons as a production lifecycle owner.

## 5. Staged delivery and acceptance gates

| Stage | Actual implementation | Acceptance / do-not-do |
| --- | --- | --- |
| **A — Extraction foundation** | Create Native OCR module and deterministic parser/unit tests; use existing picked photos; adapt Lab OCR models and bounded source preparation. | No camera UI required; golden cases include `115.5→1155`, `59130`, `1lkg`, `134ka`, reversed units, duplicate/missing values and malformed OCR boxes. |
| **B — Semi-auto stack capture** | Compose guided manual crop, sloped column strip, unit choice, optional add-on, explicit row review, original-vs-filter comparison on demand, atomic draft/restore. | Tests cover crops/rotation, invalidation, user edits, lb→kg only after confirmation, cache stale rejection, process death, battery/storage cost. Never fill missing values. |
| **C — Placard facts** | Whole placard and optional logo crop, conservative labelled fields, manual corrections and "unknown", user confirmation. | Reject model ID typos corrected without evidence, invented starting load, unlabelled ratio, made-up manufacturer specifications. |
| **D — Canonical save adapter** | Map confirmed candidates to Native equipment instance/fact versions using current schema and repository APIs; explicit local session binding if agreed. | Database migration and backup round-trip tests; historical equipment semantics unchanged; no inference model promotion. |
| **E — Live viewfinder guidance** | CameraX Preview + ImageAnalysis + Compose overlay for detected text/columns; full-resolution still capture then standard B/C review. | Must improve capture quality on held-out gym photos **without** false unit certainty, stale boxes or preview/render jank. Feature-flag until physical acceptance. |
| **F — Optional visual recognizers** | Separate research for logo retrieval, illustration segmentation, machine/masking and calibrated AR if needed. | Not required for OCR; measure unknown-brand false acceptance, accuracy, latency and storage. No VLM/YOLO requirement by default. |

Every stage gets a separate small PR, tests, a device acceptance sheet and a rollback route. LAB-2B itself stays isolated. Porting a Lab implementation is not permission to merge or cherry-pick its app/Gradle/JNI source wholesale.

## 6. Live camera overlay: viable, but **2D first**

**Product concept:** let the camera show *what is currently visible to the recognizer* before shutter press. Draw a soft, stable outline/strip over a visible kg or lb number column; show detected unit text only when the text genuinely provides evidence; lightly prompt "Move closer", "Reduce glare", or "Hold steady" based on measurable input, not a fabricated recognition-confidence score. The user should know the **preview is provisional**; a high-resolution still photo and review decide what is saved.

### Recommended pipeline

```text
CameraX PreviewView (camera preview, device orientation)
    ├── ImageAnalysis KEEP_ONLY_LATEST → sampled live ML Kit text recognition
    │      → text lines/elements/boxes/rotated corners
    │      → candidate columns (fit sloping centre line; reject weak/ambiguous)
    │      → coordinate transform from analysis frame to visible preview
    │      → smoothed overlay state (short expiration; no stale markers)
    └── ImageCapture (full-quality still, explicit shutter)
           → full-res crop & OCR → human-confirmed results → optional canonical save
```

The lab currently launches the **installed camera app** via `ActivityResultContracts.TakePicture` and only crops/reads *after* capture. Live guides require a **new** in-app CameraX preview path; merely drawing Compose rectangles over the existing external camera will not work. The Native app already declares CameraX modules, so inspect existing camera owners before building another CameraX stack. No ARCore permission/session needed initially.

Use CameraX's `ImageProxy` rotation and matched `ViewPort` transforms (`ImageProxyTransformFactory`, `CoordinateTransform`, or the documented analysis-to-`PreviewView` mapping) for every OCR bounding box/corner. **Never** assume scaled photo pixels = view pixels; deal with centre-crop, rotation, zoom, portrait aspect ratio and lens mirroring. Recompute on layout/rotation changes. Original photo coordinates and preview coordinates are separate products.

ML Kit can expose blocks, lines, elements and their geometry. Analyze downsampled preview frames with `STRATEGY_KEEP_ONLY_LATEST`, one in-flight recognizer task, and **always close the `ImageProxy`** after completion. Prefer throttling, lightweight geometry/column matching and a rate-limited UI overlay; do not run all four OCR enhancement filters or model inference per preview frame. Capture the still at sufficient character resolution and perform authoritative OCR once, off the UI thread.

Suggested visual states: **unlocated** (subtle framing rails), **possible labels** (thin translucent column outline, units unresolved), **unit-supported** (separate kg/lb annotated strips), **stable ready** (soft positive glow/shutter affordance, still review required), **stale/blurred** (recede after a short time, never leave "locked" ghosts). No universal green "correct" status before review. Let users reposition strips manually or disable live recognition altogether; accessibility and no-preview fallback are required.

### Why not full ARCore as the default?

An AR-like **visual overlay** is enough for screen-space labels. ARCore's `Augmented Images` is principally for matching **known reference images** with unique feature texture, not unknown numbers, repeated identical plates or arbitrary text. Its initial image detection expects substantial frame occupancy and can add CPU load. Anchoring a crop rectangle in 3D does not make the OCR reading more accurate. If later testing finds that 2D boxes jitter under camera movement, compare short-lived visual feature tracking/homography on the actual photographed plane, **then** ARCore only where true pose/3D anchoring demonstrably adds value. Do not run a VLM solely to keep boxes near a camera preview.

### Physical experiment before Stage E promotion

Record, on S25 Ultra: recognition coverage of expected visible labels; false kg/lb claims; time from camera open to first usable box; overlay lag versus current preview; shutter-to-reviewed-result latency; jank/frame time; CPU/memory, thermal/battery cost; glare/blur, diagonal stack, narrow plates, reverse kg/lb, non-latin/low-contrast labels, missing units; rotation/zoom; and a complete manual capture with live OCR off. Compare **manual-only** versus **live guidance** on the same held-out photo set. Thresholds/acceptance budgets must be set from measured baseline—not invented by this document. Record raw failures, not just successful demonstration videos.

## 7. Promotion checklist (not yet satisfied)

- [ ] OCR-specific pure parsing/geometry tests migrated into a Native module without changing Lab.
- [ ] Device capture and preview-to-photo coordinate mapping verified on S25 Ultra.
- [ ] Manual crop, unit confirmation and per-row correction preserve raw evidence.
- [ ] Unknown or suspect weights never silently enter the saved list.
- [ ] Canonical equipment facts require explicit human confirmation and correct provenance.
- [ ] Room migration/backup/replay and historical-equipment interpretation unchanged.
- [ ] Process death, file cleanup, bounded memory and cancel/retry demonstrated.
- [ ] Measurements from held-out stacks, placards and awkward gym lighting recorded.
- [ ] Live-overlay feature is optional and can be disabled without losing capture/review.
- [ ] LAB-2B AI model assets/runtime are **not** shipped as a dependency of deterministic OCR.

## 8. Reference documentation

**Lab:** [0.4 OCR workflow](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_OCR_STACK_WORKFLOW.md) · [0.6 columns](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_OCR_COLUMNS.md) · [0.6.1 review freeze](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_OCR_REVIEW_FREEZE.md) · [0.5 placard](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_PLACARD_EXTRACTION.md) · [source policy](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/EQUIPMENT_VISION_CONTRACT.md) · [UX](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/UX_DECISIONS.md).

**Android official:** [ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) · [CameraX transform output](https://developer.android.com/media/camera/camerax/transform-output) · [CoordinateTransform API](https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform) · [ARCore Augmented Images](https://developers.google.com/ar/develop/augmented-images).

**N-BIO:** [7F equipment translation](../n-bio-vnext/NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md). Current canonical model code: `app/src/main/java/dev/kian/mymettle/domain/equipment/EquipmentModels.kt`.

**Evidence notation:** existing Lab/source claims above come from the named files and source. The live-CameraX overlay, staged migration plan and proposed UI states are **new design recommendations**, not implemented or physically validated features.
