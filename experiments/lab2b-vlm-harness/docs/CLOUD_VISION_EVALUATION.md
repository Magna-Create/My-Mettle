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

## Test sequence

- Inspect the selected image preview. Prefer a tight readable logo/wordmark crop without bystanders or identifying background features.
- Tap **Test Logo Detection**. Confirm the upload in the explicit dialog. The client sends **one image and one `LOGO_DETECTION` feature** over HTTPS with the key only in `x-goog-api-key` request header. The prepared JPEG has a maximum dimension of ~1500 pixels and maximum encoded payload 3 MB.
- Look for **named logos, API scores, elapsed time and KiB sent**. The result is **unverified evidence**. An empty response is explicitly reported as unknown.
- Tap **Test Web Detection** separately, confirm again. This sends one `WEB_DETECTION` feature (a separately billable operation), showing web entity names/scores, best-guess labels and up to five related page URLs. No automatic fallback or double-charge hidden behind one button.
- Repeat across brand/lighting/crop cases; compare both features against the same source. Try without network and with an unknown logo to check graceful failure.
- The cloud response is **not** inserted into OCR review, equipment identity, or production facts. The UI keeps results in RAM only. API keys and raw responses are never added to LAB-2B diagnostic JSON.

## Acceptance matrix

| Case | Evidence to capture |
| --- | --- |
| Technogym, Life Fitness, Matrix, Hammer Strength, Panatta | Logo recognised, web candidate, or unknown; report separately |
| Less-common / unknown brand | False-positive names; should remain unverified |
| Partial/cropped logo or stylised wordmark | Compare full photo vs prepared logo crop |
| Reflective machine, glare, low light, upside-down mark | Returned candidates and confidence score (if exposed) |
| Logo absent, only OCR brand text present | Cloud correctly returns unknown or ambiguous candidates |
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
