# LAB-2B 0.6.2 — Google Cloud Vision logo/web evaluation

**Scope:** standalone experimental Android harness only; unrelated to My Mettle Native or N-BIO. No secret in GitHub or APK source. This is a **working request-path implementation for personal device testing**, not proof that Google recognises gym manufacturers or that the experiment is ready for production.

## Setup once

1. In [Google Cloud Console](https://console.cloud.google.com/), select/create **your personal project**, enable **Cloud Vision API** (`vision.googleapis.com`) and billing. Check the project's current free tier/paid rates and set a conservative quota and billing alert before testing.
2. In **APIs & Services → Credentials**, create a standard **API key**, not a service-account JSON file. Restrict **API access** to **Cloud Vision API**. For app restrictions, you can add this test app's package and installed APK signing SHA-1, shown on the test screen:
   - Package: `dev.kian.lab2b.vlm`.
   - Fingerprint: see **Google Cloud Vision → Package / SHA-1** (depends on installed debug signature: Termux / CI builds may differ).
   - Google requires `X-Android-Package` and `X-Android-Cert` headers for Android-restricted API keys; the LAB-2B client supplies them.
   - Android application restrictions can be spoofed; always set API restrictions and usage limits as well.
3. Open **LAB-2B VLM Harness → Cloud Vision logo/web test** from either the weight screen or **Placard extraction → Test Cloud Vision with this logo/photo**.
4. Tap **Enter API key**, paste your key, save. The app uses Android Keystore AES-GCM + app-private preferences, with `android:allowBackup=false`. It will not display the stored value, add it to diagnostic JSON, ship it in the APK, or send it to GitHub. Delete/replace from the same screen. A local key in a compromised app/device is **not equivalent to a backend-held secret**.
5. If you already photographed a placard, use **Draw logo box** and open Cloud Vision: the existing, EXIF-normalised **logo crop** is selected. Otherwise use **Choose image** on the Cloud Vision screen to pick a standalone photo.

## Integrated placard workflow (28 October 2026)

The **Placard Extraction** activity is now the **primary one-photo entry point**. Taking or importing a photo immediately runs ML Kit OCR and applies the shared canonical-key manufacturer catalogue to the **same** placard evidence, along with labelled model/exercise/resistance fields. There is no separate step to identify the brand. A placard crop and optional logo crop are refinement tools only; changing either crop or its OCR filter automatically refreshes the extracted candidates and invalidates stale review.

For unrecognised, conflicting or generic manufacturer text, the **same placard review screen** offers **Find manufacturer online (optional)**. The user confirms an explicitly described **single** Cloud Vision `LOGO_DETECTION` upload using the previously entered private API key. When available, a logo crop is sent; otherwise the prepared placard image is used. The returned name(s) appear as **review-only Brand candidates** in the same existing Review/Correct control, never as silently accepted identities. Confirmation and export remain under the existing placard review process.

The stand-alone Cloud Vision test screen remains available as an **advanced research tool**, not required during normal placard capture. The account key is shared via the same encrypted app-private key vault, never stored in equipment/placard export. The local draft stores only source-bound unverified cloud name candidates for correct review restoration; crop/source changes invalidate them. This remains Lab-only and is **not** Native integration.

### Acceptance checks for this combined workflow

- One selected photo → OCR result and candidate brand/fields without tapping `Read` or visiting the Cloud Vision activity.
- Hammer Strength two-line wordmark → canonical Brand suggestion with the original OCR text/source identity.
- A local distinct brand → no automatically initiated paid request. Unknown/generic/conflicting brands → optional Cloud Vision button and consent.
- Cloud result → selectable **Brand** candidate with explicit human review, preserving independent model/starting-resistance fields.
- New photo, crop or profile → stale reviewed brand/cloud observations are cleared. Draft and export contain no key or raw service response.
- Re-run/restart, no-network, missing key, missing logo crop, known brand and unknown brand remain workable.

## Test sequence — offline brand lookup before paid cloud fallback

- Inspect the selected image preview. Prefer a readable logo/wordmark crop without bystanders or identifying background features.
- **The app performs one local ML Kit text pass** on the selected photo and compares the text against a curated `ManufacturerCatalogue`, which is also used by `PlacardParser`. The catalogue stores compact keys (`lifefitness`, `hammerstrength`) and **independent display metadata** (word breaks 4 and 6 respectively). `technogym` has no display word break, preserving its real-world spelling. Matching ignores casing, spacing and punctuation, including wordmarks split into two adjacent OCR lines inside the same text block, without accepting substrings inside unrelated words.
- Tap **Identify brand • OCR → cloud if unresolved**. A single distinct non-generic OCR match is displayed as a *suggestion* without an upload; ambiguous, generic or missing matches can offer an explicit **Logo Detection** fallback. **No hidden cloud call:** the user must confirm each upload.
- For comparison, tap **Test Logo Detection** manually even when OCR succeeds. Confirm the upload in the explicit dialog. The client sends **one image and one `LOGO_DETECTION` feature** over HTTPS with the key only in `x-goog-api-key` request header. The prepared JPEG has a maximum dimension of ~1500 pixels and maximum encoded payload 3 MB.
- Look for **named logos, API scores, elapsed time and KiB sent**. The result is **unverified evidence**. An empty response is explicitly reported as unknown.
- Tap **Test Web Detection** separately, confirm again. This sends one `WEB_DETECTION` feature (a separately billable operation), showing web entity names/scores, best-guess labels and up to five related page URLs. No automatic fallback or double-charge hidden behind one button.
- Repeat across brand/lighting/crop cases; compare both features against the same source. Try without network and with an unknown logo to check graceful failure.
- The cloud response is **not** inserted into OCR review, equipment identity, or production facts. The UI keeps results in RAM only. API keys and raw responses are never added to LAB-2B diagnostic JSON.

## Observed physical sample — 28 October 2026

User-supplied LAB-2B device screenshots (one call per still photo) show:

| Subject in photo | Cloud LOGO_DETECTION | Response | Upload | Interpretation |
| --- | --- | --- | --- | --- |
| Life Fitness chest-press placard | Life Fitness (0.970 API score) | 1,384 ms | 121 KiB | Name recognised |
| Technogym placard | Technogym (0.992 API score) | 521 ms | 62 KiB | Name recognised |
| Hammer Strength label | No named result | 809 ms | 147 KiB | False negative for prominent printed text |

These are **three individual trials**, not a measured accuracy benchmark; they justify testing local wordmark OCR before paid cloud identification. The third image reads **Hammer Strength** (not Hammer Fitness). `hammerstrength` and `hammersport` remain separate keys; don't infer either from a generic "hammer". Web Detection did not return a useful result for the failed case according to user testing, but its raw response is unavailable for analysis.

**Next physical checks:** local ML Kit OCR on all three images, especially the Hammer Strength label; brand alias false-positive cases; ambiguous multiple manufacturers; latency for offline-only success and cloud fallback; number of avoided cloud requests. Compare to the existing recorded Cloud responses. A correct brand name is still an unverified candidate until accepted.

## Acceptance matrix

| Case | Evidence to capture |
| --- | --- |
| Technogym, Life Fitness, Matrix, Hammer Strength, Panatta | Logo recognised, web candidate, or unknown; report separately |
| Less-common / unknown brand | False-positive names; should remain unverified |
| Partial/cropped logo or stylised wordmark | Compare full photo vs prepared logo crop |
| Reflective machine, glare, low light, upside-down mark | Returned candidates and confidence score (if exposed) |
| Logo absent, only OCR brand text present | Offline OCR can propose a manufacturer, and optional Cloud Vision remains manual |
| Offline or key missing / API disabled / quota reached | Clear failure, no app crash |
| Key deletion and restart | Secret no longer usable |
| Existing weight/placard OCR flows | No changes to their results or export |

## Contract and caveats

- Request endpoint: `POST https://vision.googleapis.com/v1/images:annotate`. Authentication uses the standard Google `x-goog-api-key` header, never URL query parameters. Server responses are parsed from `logoAnnotations` or `webDetection`. Cloud Vision detects **popular** logos, so false negatives for specialist gym brands are expected.
- CameraX/live ML Kit remains **separate**: this experiment uploads a still image only when explicitly requested.
- Never compare `webEntities.score` directly against a logo's `logoAnnotations.score` as equivalent calibrated probabilities. The scores have different meanings.
- A recognised brand alone never establishes machine model, starting resistance, mechanics, gym branch or N-BIO load translation.
- This client-only key approach is a constrained *personal prototype*. Production or multi-user integration needs a server-backed authenticated gateway, rate limiting and privacy review.
- Secret vault cannot be recovered after uninstall, device migration, Keystore invalidation, or clear-app-data; re-enter the key.
- Do not copy keys into tickets, screenshots, terminal commands or chat. Rotate the key immediately if leaked.

**Source:** [Cloud Vision REST API](https://docs.cloud.google.com/vision/docs/request), [Logo Detection](https://docs.cloud.google.com/vision/docs/detecting-logos), [Web Detection](https://docs.cloud.google.com/vision/docs/detecting-web), [using API keys](https://docs.cloud.google.com/docs/authentication/api-keys-use), [Android restrictions](https://docs.cloud.google.com/docs/authentication/api-keys).
