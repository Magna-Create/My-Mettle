# OCR → My Mettle Native: implementation and lessons learned

> **Status (28 October 2026):** developer migration plan, **not** an implemented production integration. Source is the isolated `agent/lab2b-ocr-stack` experiment; target is the Kotlin/Compose Native app, currently developed on `agent/n-bio-vnext-workout-ui`. Do not merge the experimental branch wholesale. The experiments' documentation contains historical checkpoints; its latest weight-review record accepts the **workflow**, not complete OCR recognition accuracy. Keep this guide source-led and update its evidence column after every physical gate.

## 1. Goal and boundaries

Enable a gym user to photograph a machine's weight stack **once**, see exactly which printed kg/lb column was recognised, correct any suspect readings and save verified equipment facts. **Do not require a second/add-on weight photograph**: supplementary microplates are a manual, machine-configured counter. A placard remains an optional OCR evidence path, preferably cropped from the same camera image when visible. Pictorial logo identification stays in the separate experimental OCR app until accepted. A live overlay should make framing easier rather than add steps.

Pipeline and ownership:

```text
camera / document picker
    → privately copied, EXIF-normalised source photograph
    → optional live CameraX overlay for text/column location (never authority)
    → one high-quality still, reusable source and optional crop(s)
    → ML Kit OCR: text + original geometry + image identity
    → deterministic stack-column and optional placard text candidates
    → lightweight exception-only human review and explicit confirmation
    → separate manual fractional/add-on denomination counters (NO OCR)
    → domain validation + canonical equipment fact/version (separate gate)
    → gym-library context resolution at workout start; equipment remains independent
    → N-BIO observes canonical evidence only; never raw OCR guesses
```

Do not confuse **observed print**, **interpretation**, **derived proposals** and **confirmed/corrected facts**. The lab schema `lab2b.ocr-stack.v1` expressly sets `production_import_compatible=false`. An export is not a Room migration. Do not change equipment history, existing workout entries, or N-BIO prescription authority as a side-effect of taking a photo.

## 2. Authoritative source map

| Evidence / behaviour | Current source of truth | Migration decision |
| --- | --- | --- |
| Historical main-stack + optional add-on capture, crop, save | [LAB_2B_OCR_STACK_WORKFLOW](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_OCR_STACK_WORKFLOW.md), `WeightScanActivity.kt`, `WeightScanOwner.kt` | Port main-stack OCR and review *behaviour*, not the experimental add-on second-photo workflow or raw `Activity` layout. Replace add-on OCR with manual denomination counters. |
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
5. **Add-on selection and machine mechanics are separate.** Replace add-on image recognition with explicit denomination/count controls. A photographed add-on label, count, or presence does not establish whether a plate is engaged; unknown pulley ratios/starting resistance remain unknown.
6. **Track every invalidation.** Changing photo/crop/filter/column or unit invalidates stale OCR and/or review according to the recorded rules. A new image must never inherit old inclusion flags.
7. **Expose observability.** Keep image hash, orientation, pixel dimensions, crop/filter identity, geometry, OCR timing, cache hit and edit provenance. Never invent confidence percentages if the recognizer does not supply them.
8. **Use atomic, bounded storage.** Lab stores private source/crop/filter assets and atomic draft JSON. Preserve restore-after-force-close semantics, keep image decoding bounded (documented 16 MP / 4096 edge), off-main-thread work, and bounded cache; do not copy test absolute file paths into production entities.
9. **Keep model research separate.** The prior NPU route failed packaging portability gates; MNN/GPU results are not a proven camera-recognition contract. A deterministic OCR + review release should ship without multi-GB model dependencies.
10. **Avoid brand-to-physics leaps.** An OCR-supported manufacturer or model name does not establish pulley ratio, rail angle, starting resistance, effective resistance or muscle contribution.

## 3A. Product decision — one stack photo and manual add-on counters (28 Oct 2026)

The experimental 0.4 workflow split main stack and optional add-on photographs. **That is historical Lab behaviour, NOT the proposed Native production UX.** A single stack photo is enough to extract the machine's selectable printed settings. Any fractional/microplate options are deliberately entered rather than OCR-scanned.

**Candidate design:** a compact **Supplementary weights** section on the *equipment authoring screen*, with denomination as the central label and the `− [available quantity] +` counter alongside, analogous to the user's supplied stepped-control reference. Offer `5 kg`, `2.5 kg`, `1.5 kg`, `1.25 kg` and `0.625 kg`. Each counter describes how many optional supplementary/add-on plates of that denomination the specific machine **has available**, persisted as part of its reviewed configuration, and initially zero until the user specifies them. Only configured nonzero denominations need appear in normal workout UI. **Do not equate inventory quantity with currently engaged resistance**. Never demand a second photograph to fill these values.

Conceptual rows (not approved final Figma proportions):

```text
5 kg      [ − ]  0  [ + ]
2.5 kg    [ − ]  1  [ + ]
1.5 kg    [ − ]  0  [ + ]
1.25 kg   [ − ]  0  [ + ]
0.625 kg  [ − ]  0  [ + ]
```

To avoid repeated data entry, the *equipment setup* counter is the **available quantity** for each denomination, remembered across gym visits. If the machine has one built-in flip-in plate, quantity is `1`; an independent set of supplementary plates might have more. A separate **engaged today** value may later be exposed by the workout equipment controls, bounded by available quantity, but is **not** a required field in this authoring step. Do not multiply availability by a set's selected load or infer currently engaged plates. If engagement is not independently recorded, keep the actual added load unknown/zero only where the user's explicit entry semantics warrant it; never assume all available plates are engaged.

Store exact integer grams or decimal values (e.g. `625 g`, avoiding floating-point drift), **available count**, and later optional **engaged count** as separate facts. Keep total indicated stack setting distinct from auxiliary applied mass; do **not** infer its effect on equivalent external load without known load accounting, mechanical application, and `TOTAL/PER_SIDE` semantics. Some independent left/right additions need per-side tracking. When an engaged-load feature exists, user selection must distinguish once, per-side, or paired total; don't automatically double it. This must remain compatible with the existing N-BIO equipment/entry-basis contract.

**Minimal steps is a front-end UX constraint, not permission to remove validation.** Engineering can release OCR, equipment authoring and live recognition in separate PRs, yet the target user journey is *open machine → frame one stack → capture → correct only uncertain fields → save*. Existing reviewed equipment should open directly; scanning is an add-new/correct-facts path, not a compulsory workout interruption.

**Two internal modes, one coherent entry point:** `Auto` first offers previously reviewed **equipment choices** that fit the resolved current-gym context, then presents a compact confirmation; `Semi-auto` provides the same camera/review pipeline for unknown or ambiguous equipment. The gym context does not establish machine identity: a remembered equipment choice is a suggestion until confirmed for the current session. A user need not choose an AI mode before scanning. **Experimental logo recognition is excluded from the Native Auto path** until separately tested and accepted in the OCR Lab. Reuse known settings before asking for another scan.

### Gym library and automatic session context — equipment stays independent

**Product decision (28 October 2026):** keep the equipment library **independent of gym locations**. Add a separate **Gym Library** of saved venues/branches. The machine record has its own identity, reviewed configuration and versioned facts, whether it is used at one gym or several. Do **not** put a mandatory `gymId` or coordinates on `EquipmentInstance`, clone machines merely because a user switches branch, or make a Gym Library entry required for equipment authoring.

The inspected Native `EquipmentInstance` domain has equipment identity/facts and preferred/session bindings but no explicit gym entity or session-gym resolution contract. The library and resolver therefore need their **own design/schema gate**. The actual equipment used for a set remains a session/historical fact; guessing a gym from a coordinate must not rewrite equipment identity, historical observations, or exercise execution profile.

**Intended minimal journey:**

1. **Gym Library**: user saves a gym name and optionally its branch/venue location. Default is the most recent **confirmed** gym, not necessarily the geographically nearest venue. A user may rename/remove venues and specify a different regular location.
2. **Workout starts**: with explicit location permission, make one bounded current-location lookup and match against user-saved venues/branches with a conservative ambiguity/accuracy rule. When one match is sufficiently distinctive, propose/select the current gym for **this workout**, showing an unobtrusive editable indicator. If no valid match, permission is denied, location services are off, or multiple branches overlap, use the **saved gym selector**; the workout itself must never be blocked. Do not require continuous foreground GPS, background location permission or auto-created unverified gyms.
3. **Equipment assignment inside a workout**: use the current gym context to **prioritise previously chosen equipment for that exercise/profile at this gym**; user can pick any independent equipment record, search the library, or create/correct a record via the one-photo OCR path. If the gym is unknown, offer the gym selector alongside the equipment choice, not a forced separate location step.
4. **Context update**: when the current gym changes, recompute suggestions and any appropriate **uncommitted** exercise/equipment defaults. Do **not** silently replace equipment already confirmed/logged in the current session; a current-gym match is not proof of which physical machine is being used.
5. **Learning and reuse**: remember an explicit user choice in a **gym + exercise/profile → preferred equipment** contextual selection record, or recent-use index, independently of the equipment record. This is a *usage preference* (and may be uncertain or absent), **not an intrinsic equipment-to-location ownership or availability mapping**. A physical machine or model is not silently duplicated, and corrections to its specification remain versioned.
6. **History**: store the *resolved/confirmed workout gym*, where useful, as contextual session metadata; preserve actual chosen equipment bindings independently. Later default-gym changes, location corrections or geofence updates never retrospectively relabel equipment/load interpretation.

**Location and privacy:** device location access is optional and granted through the app at use time; an approximate location may not distinguish branches reliably. No location permission or precise coordinate is presumed available. If the user never wants auto-detection, the gym selector and last confirmed gym remain sufficient. Avoid uploading workout location to external services merely to guess venue identity. The Figma Gym/Exercise/Equipment Library redesign will own the eventual navigation; do not pre-empt it with new permanent screens.

### Logo identification: test official cloud lookup versus local matching IN THE OCR LAB

**Decision (28 October 2026):** keep all logo-identification experiments inside the separate `agent/lab2b-ocr-stack` Android app. Do **not** choose XFeat by default, do not add a new mandatory logo-capture step to Native, and do not merge API/cloud/model code into production before real-image acceptance.

**What was and wasn't learned:** standalone XFeat (without LightGlue) was previously a proposed lightweight local matcher, but there is **no measured S25 Ultra XFeat end-to-end speed benchmark** in the retrieved Lab decisions. Slow Qwen/Gemma VLM tests do not prove that XFeat is slow. Benchmark rather than invent a performance conclusion. A simple ORB/AKAZE local matching baseline may be tried as a lower-complexity alternative, but it is also not yet proven on gym logos.

**Official Google services, not an official Lens API:** Google does **not** expose a public official Google Lens visual-search API. It exposes [Cloud Vision Logo Detection](https://docs.cloud.google.com/vision/docs/detecting-logos), which returns known logo names/scores/bounding polygons, and [Cloud Vision Web Detection](https://docs.cloud.google.com/vision/docs/detecting-web), which returns web entities, similar/matching images and pages with matching images. Third-party commercial [SerpApi Google Lens](https://serpapi.com/google-lens-api) performs Lens scraping; it must not be treated as Google's own stable service contract. Do not confuse Google ML Kit on-device OCR with Cloud Vision remotely processed images.

**Preferred Lab comparison sequence:**

1. **Zero-cloud baseline:** OCR existing placard/wordmark text and match against a small reviewed manufacturer alias list. A supported wordmark is often enough, including without an image classifier.
2. **Cloud logo:** after the shutter press, on explicit user consent, send only a tightly cropped logo region to a small test backend using `LOGO_DETECTION`; retain original string, returned score/box and failure/unknown state.
3. **Cloud web fallback:** on logo-detection ambiguity/unknown only, try `WEB_DETECTION` for entities/related pages; use evidence from multiple results rather than accepting the first search-result title as a fact. Restrict to candidate manufacturers if appropriate, but always allow `unknown`. Evaluate both whole-image and tight logo-crop source if necessary.
4. **Local alternative:** benchmark ORB/AKAZE and only then XFeat on the same accepted test set if cloud dependence, privacy or reliability warrants local recognition. Use a prebuilt gallery and geometric verification with explicit rejection.
5. **Optional later multimodal adjudication:** only if needed, evaluate a remote vision-language model to *organise* contradictory candidate evidence into a proposed brand, not invent physics or claim a machine model as verified.

**Example research output (not an implemented API schema):**
```json
{
  "manufacturerCandidate": "example_brand",
  "evidenceSources": ["ocr_wordmark", "cloud_logo", "web_entity"],
  "status": "candidate_requires_review",
  "verifiedByUser": false
}
```

**Costs/security/privacy:** Cloud Vision is billed per image **and per feature**. As currently published, Logo Detection costs $1.50 per 1,000 paid feature units and Web Detection $3.50 per 1,000 paid feature units, with the first 1,000 monthly units per feature free; confirm current rates at [official pricing](https://cloud.google.com/vision/pricing) before implementation. Cloud calls require network, billing and safe authentication: use a backend, keep Cloud credentials out of the APK, crop away identifiable bystanders/backgrounds, provide a local-only/no-upload route, and disclose/consent to remote image processing. No background or continuous camera-frame uploads. A cloud call may be quicker *overall* than heavy local matching on some inputs, but network round-trip and service variability make absolute speed claims inappropriate until tested.

**Personal Google Cloud account and future Intelligence settings:** for the current single-user product, a user-owned Google Cloud project can fund occasional Cloud Vision queries. Create/enable Vision API and billing in that Google Cloud project; **Google account login alone is not the API credential**. A standard project-scoped API key can be supplied at runtime by the user; never commit the key, service account JSON or OAuth refresh tokens to GitHub, `BuildConfig`, packaged assets, CI logs or exported workout backups. When a real Native connector exists, add **Settings → Intelligence → Cloud recognition** with a user-entered key stored using Android Keystore-backed encryption in non-backed-up app-private storage, plus connection test, disconnect/delete, opt-in for remote image analysis, and unambiguous status. Never imply that storing a key proves authorization until an authenticated test passes.

Restrict the key to the Cloud Vision API, set service quotas and billing alerts; Android package + signing SHA-1 restrictions may provide an additional layer, but Google explicitly warns that Android restriction headers can be spoofed. A client-held key is **not** equivalent to keeping a key on a private server. The personal build may accept that documented residual risk; if the app becomes multi-user, move cloud access behind a small authenticated backend. Do not let the future product UI enable paid cloud requests by default. The new **Intelligence** section is a shared home for local OCR, opt-in cloud logo identification, and later **N-BIO user preferences**. N-BIO's present experimental inference authority stays unchanged; do not add misleading user switches for unpromoted models. The separate settings UI foundation is tracked in [PR #43](https://github.com/Magna-Create/My-Mettle/pull/43); it is not a working Cloud Vision connection yet.

**Experimental acceptance:** include Technogym, Life Fitness, Matrix, Hammer Strength, Panatta and less-familiar brands; negative samples with no logo, unknown brands, mirrored/reflected/logos under glare, stylised wordmarks and machine parts that resemble marks. Measure top-1 supported-brand correctness, false-positive **unknown** acceptance, coverage, per-stage latency, bandwidth/cost and failure-on-no-network. Compare on identical crops and whole-machine shots where appropriate. Report original responses alongside decisions; no fabricated confidence number or silently corrected manufacturer. A brand match can suggest an identity; it cannot itself assert machine model, resistance, pulley ratio or gym branch.

**Gate:** once the experimental app demonstrates adequate accuracy and reliability, decide explicitly whether Native uses `on-device wordmark only`, `wordmark + optional cloud fallback`, or a tested local matcher. OCR stack/placard integration does not wait for this decision.

### CameraX implementation notes from current source

The isolated Lab `OcrProcessor.kt` uses ML Kit text recognition on a **saved normalized PNG**, and already copies `TextBlock` / `Line` `boundingBox` and `cornerPoints` into `OcrEvidence`. The `CropEditor` and `ColumnEditor` operate on a still/source image. **No pre-shutter ML Kit live camera overlay exists in that test app.** Reuse the recogniser, box shapes, fitted column logic and UI concept; switch the input source to CameraX `ImageAnalysis` for preview. Draw those recognised polygons over `PreviewView` after correct orientation/scale/crop transforms. No need for a separate bitmap crop for every live OCR box. Save/crop the still at high resolution **after** capture; that is the source of reviewed authority.

As of 28 October 2026, the official AndroidX Camera release notes list **1.6.2 stable** (existing Native pin: 1.6.1), with 1.7.0-rc01 available but not stable. Prefer the narrowly scoped **1.6.2** upgrade when implementing CameraX overlay, using a dedicated dependency-change PR and compiling/regression-testing existing camera features. No need to adopt RC or 3D AR for OCR.

---

## 4. Safe Native integration shape

Create *interfaces* before copying implementation:

```kotlin
interface EquipmentOcrRecognizer {
    suspend fun recognise(input: PreparedOcrImage): OcrObservation
}
interface EquipmentEvidenceInterpreter {
    fun stackCandidates(observation: OcrObservation, selection: ColumnSelection?): StackReviewDraft
    fun placardCandidates(placard: OcrObservation): PlacardReviewDraft
}
interface EquipmentFactReviewGateway {
    suspend fun validateAndSave(reviewed: ConfirmedEquipmentDraft): EquipmentSaveResult
}
```

These are **illustrative seams**, not existing project APIs. Align final names/types with current `EquipmentId`, `EquipmentInstance`, `EquipmentFactVersion`, `EquipmentFactProvenance`, existing equipment repository, execution-profile identity and session binding semantics. Use a schema adapter only **after** evidence-review gates pass. Never make image file paths or ephemeral OCR text the equipment primary key.

Suggested internal UI state: `EMPTY → CAPTURING/IMPORTING → optional CROPPING → ANALYSING → REVIEW_IF_NEEDED → VERIFIED_DRAFT → SAVING → SAVED`, plus `FAILED` and `CANCELLED`. These are implementation states, **not required separate screens or taps**. The main-stack photo can be reset without discarding independently confirmed equipment metadata. A reviewed draft must be invalidated by an upstream evidence change. Distinguish **Use this session**, **Save as available equipment** and **Make default** if those binding features are actually implemented; otherwise omit/disable rather than faking the distinction.

Data contract needs: source image identity/EXIF, crop-relative geometry, observed text/elements, candidate field and unit, corrected value, `observed/interpreted/derived/human-corrected` provenance, versioned confirmation, model recognizer/filter versions, and unknown/ambiguous states. Persist only what is necessary for audit/user correction under a clear retention policy; capture/session caches must be bounded and deletable. Do not use `WeightScanOwner`/process singletons as a production lifecycle owner.

## 5. Staged delivery and acceptance gates

| Stage | Actual implementation | Acceptance / do-not-do |
| --- | --- | --- |
| **A — Extraction foundation** | Create Native OCR module and deterministic parser/unit tests; use existing picked photos; adapt Lab OCR models and bounded source preparation. | No camera UI required; golden cases include `115.5→1155`, `59130`, `1lkg`, `134ka`, reversed units, duplicate/missing values and malformed OCR boxes. |
| **B — Semi-auto stack capture** | One stack photo, Compose guided crop/column strip when needed, unit choice, exception-first row review, original-vs-filter comparison on demand and atomic draft/restore. Add-on weight OCR and its second photograph are **excluded**; offer manual denomination counters. | Tests cover crops/rotation, invalidation, user edits, lb→kg only after confirmation, counters/engagement semantics, stale cache, process death, battery/storage cost. Never fill missing stack values. |
| **C — Placard fields** | Whole placard from the stack photo where possible, conservative labelled fields, manual corrections and "unknown". Logo recognition is **not** a Native dependency. | Reject model-ID typo repairs without evidence, invented starting load, unlabelled ratios and made-up manufacturer specifications. |
| **D — Independent equipment save + Gym Library context** | Map confirmed candidates to standalone Native equipment instance/fact versions; separately create a gym/branch library, per-workout gym context and optional gym-scoped equipment-selection preferences after schema/UX gate. | Equipment is never owned by gym, geolocation never rewrites recorded equipment, auto-selection requires conservative match/manual fallback, backups and history preserve both contexts; no inference model promotion. |
| **E — Live viewfinder guidance** | CameraX Preview + ImageAnalysis + Compose overlay for detected text/columns; reuse the SAME OCR observation geometry and full-resolution still/review adapters as B/C. Can be engineered in a separate gate but appears in the same user capture flow. | Must improve capture quality on held-out gym photos **without** false unit certainty, stale boxes or preview/render jank. Feature-flag until physical acceptance. |
| **F — Lab-only logo and web-recognition comparison** | In **`agent/lab2b-ocr-stack` only**, benchmark OCR wordmark matching, optional official Google Cloud Vision Logo/Web Detection, and ORB/AKAZE/XFeat local baselines if warranted. No product integration or credentials in the APK until a new gate. | Evaluate obscure/unknown brands, offline fallback, false acceptances, time/cost/consent/credential safety and runtime; no untested Native dependency. |

Every stage gets a separate small PR, tests, a device acceptance sheet and a rollback route. LAB-2B itself stays isolated. Porting a Lab implementation is not permission to merge or cherry-pick its app/Gradle/JNI source wholesale.

## 6. Live camera overlay: viable, but **2D first**

**Product concept:** let the camera show *what is currently visible to the recognizer* before shutter press, with **no add-on weight scanning**. Draw a soft, stable outline/strip over a visible kg or lb number column; show detected unit text only when the text genuinely provides evidence; lightly prompt "Move closer", "Reduce glare", or "Hold steady" based on measurable input, not a fabricated recognition-confidence score. The user should know the **preview is provisional**; a high-resolution still photo and review decide what is saved.

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
- [ ] Single stack capture succeeds without any add-on photograph; **available denomination quantities** are saved/recalled per equipment instance, and no unselected availability becomes engaged load.
- [ ] Gym Library, optional location match, fallback selector and **gym-contextual equipment suggestions** are designed/approved with Figma; equipment identity stays independent and historical actual-use bindings are unchanged.
- [ ] **OCR Lab only**: test wordmark OCR, official Cloud Vision Logo/Web Detection and local ORB/AKAZE/XFeat only if justified; require unknown-brand rejection and privacy/latency/cost measurements before a separate Native decision.
- [ ] Workout start never blocks on location; denied/ambiguous lookup has a manual gym selection fallback, and auto gym changes never mutate confirmed exercise equipment.
- [ ] Live-overlay feature is optional and can be disabled without losing capture/review.
- [ ] LAB-2B AI model assets/runtime are **not** shipped as a dependency of deterministic OCR.

## 8. Reference documentation

**Lab:** [0.4 OCR workflow](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_OCR_STACK_WORKFLOW.md) · [0.6 columns](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_OCR_COLUMNS.md) · [0.6.1 review freeze](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_OCR_REVIEW_FREEZE.md) · [0.5 placard](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/research/LAB_2B_PLACARD_EXTRACTION.md) · [source policy](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/EQUIPMENT_VISION_CONTRACT.md) · [UX](https://github.com/Magna-Create/My-Mettle/blob/agent/lab2b-ocr-stack/docs/ui-ml-lab/UX_DECISIONS.md).

**Android official:** [ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) · [CameraX transform output](https://developer.android.com/media/camera/camerax/transform-output) · [CoordinateTransform API](https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform) · [ARCore Augmented Images](https://developers.google.com/ar/develop/augmented-images).

**N-BIO:** [7F equipment translation](../n-bio-vnext/NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md). Current canonical model code: `app/src/main/java/dev/kian/mymettle/domain/equipment/EquipmentModels.kt`.

**Evidence notation:** existing Lab/source claims above come from the named files and source. The live-CameraX overlay, staged migration plan and proposed UI states are **new design recommendations**, not implemented or physically validated features.
