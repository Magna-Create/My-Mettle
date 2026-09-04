# LAB-2B implementation notes

> **Status:** LAB-2B IN PROGRESS. Current gate: B0/B1 Qualcomm reference-app reproduction and physical native-initialisation proof.
>
> LAB-2B is an isolated runtime validation exercise. Nothing in this document authorises My Mettle `:app`, LAB-1 provider, N-BIO, Room or equipment integration.

## Starting source truth

- Working branch at entry: `agent/ui-ml-lab`.
- LAB-2B starting Lab HEAD: `13fcff8e608e18e3ac4faa232d17c98da25750df` (`Preserve LAB-2A governance detail at closure`).
- Live N-BIO observed at entry: `5727ea95cf692c8ea0145bdb4cc0ac5a4dc705de` (`Clarify Context Module scientific status`).
- N-BIO sync: **none**; LAB-2B has no shared N-BIO dependency requirement.
- LAB-2A: human accepted by explicit user instruction starting this mission.

## Frozen primary route

```text
Qwen3-VL-2B-Instruct
→ unsloth/Qwen3-VL-2B-Instruct-GGUF
→ Q4_0 + matching mmproj
→ Qualcomm GenieX Android AAR
→ resolved runtime_id expected llama_cpp
→ explicit compute_unit = npu
→ Samsung Galaxy S25 Ultra / Snapdragon 8 Elite
```

Fallback diagnosis, only if required, holds every other variable fixed and changes `npu → gpu → cpu` one step at a time.

## B0 reference baseline

### Exact source

- Repository: `qualcomm/ai-hub-apps`.
- Commit: `db3f9772d4e423dee2df517335009c703845dba8`.
- App directory: `geniex_chat_android`.
- Application ID / namespace: `com.geniex.demo`.

### Exact build/runtime matrix established from source

| Component | Pin |
| --- | --- |
| GenieX Android AAR | `com.qualcomm.qti:geniex-android:0.3.5` |
| AGP | `8.13.0` |
| Kotlin Android plugin | `2.2.0` |
| Kotlin serialization plugin | `2.2.0` |
| Gradle | `9.1.0` |
| Java | 17; source build utility pins `17.0.16-ms` |
| compileSdk | 34 |
| targetSdk | 34 |
| minSdk | 31 |
| NDK | `27.3.13750724` |
| JNI packaging | `jniLibs.useLegacyPackaging = true` |
| target ABI | `arm64-v8a` |

The LAB-2A matrix did not name the Gradle distribution explicitly. Current inspection of the accepted reference revision establishes `GRADLE_VERSION="9.1.0"` in `geniex_chat_android/scripts/versions.env`. This is an evidence completion, not a route deviation.

### Reference build mechanics

The accepted app contains no checked-in Gradle wrapper. Qualcomm's `build.sh` is deliberately Docker-only. Its Docker build calls `install_build.sh`, which sources `scripts/android_utils.sh`; that utility installs the exact Java/Gradle/Android SDK/NDK versions from `scripts/versions.env`. The build then executes:

```text
gradle assembleDebug assembleAndroidTest
```

Expected debug APK path from the reference README/source:

```text
geniex_chat_android/build/outputs/apk/debug/app-debug.apk
```

No source/toolchain version is to be modernised during B0.

### Agent execution-environment limitation

The repository-operation sandbox has no Android SDK and its shell cannot resolve outbound Git/network hosts. Therefore the unchanged reference APK cannot be truthfully compiled in that sandbox. The exact public reference source and pins were inspected through GitHub instead.

This does **not** count as B0 PASS or FAIL. B0 dependency resolution/build remains a physical/developer-environment action. The project will not move to B2 until Kian reports the unchanged reference build and B1 runtime gate evidence.

## B1 physical reference-app method

The least-expensive proof does not require model download.

Current reference source initialises GenieX from `MainActivity.onCreate()` via `GenieXSdk.getInstance().init(...)`. To force a subsequent ModelManager JNI call without fetching a model, use the reference UI's **Load** action before downloading a selected model. The app checks availability through `ModelManagerWrapper.getPaths(...)`; the expected ordinary outcome for an absent model is a “model not downloaded”/download-first message.

B1 therefore requires:

1. install unchanged `app-debug.apk`;
2. launch;
3. confirm no immediate linker/native crash;
4. select a catalog item if needed;
5. tap **Load** without starting Download;
6. confirm the reference UI reports the model is absent rather than crashing;
7. capture logcat around launch + Load;
8. record Android build and page size.

Do not download a multi-gigabyte model during B1.

## Page-size evidence

Current Android guidance uses:

```text
adb shell getconf PAGE_SIZE
```

A direct device shell such as Termux can run the inner command:

```text
getconf PAGE_SIZE
```

The physical value is not assumed. A result of `16384` identifies a 16 KB environment.

B3 later also requires the exact built harness APK/AAR native libraries to be inspected with current Android tools, including ELF LOAD alignment and:

```text
zipalign -c -P 16 -v 4 <apk>
```

No B3 result is pre-filled.

## Harness architecture — deferred until B1 PASS

Approved future path:

```text
experiments/lab2b-vlm-harness/
```

The harness will be its own Gradle root and will not be included by My Mettle's root settings. Planned Kotlin ownership remains one `HarnessRuntimeOwner` with serial state:

```text
IDLE
IMPORTING
IMPORTED
LOADING
READY
GENERATING
STOPPING
UNLOADING
FAILED
```

No harness files are created before B1 passes.

## Model import — deferred until B1 PASS

Planned route remains:

```text
developer-staged exact GGUF + matching mmproj
→ app-accessible staging if required
→ ModelManagerWrapper.pullFlow(... HubSource.LOCALFS ...)
→ ModelManagerWrapper.getPaths(...)
→ validate model_path + mmproj_path + runtime_id
```

No files will be written manually into GenieX internal cache directories.

## Threading / lifecycle — frozen from LAB-2A

- expensive runtime work off main thread;
- one owner and one active native inference lane;
- concurrent generation rejected;
- generation stop through `stopStream()`;
- unload through `stopStream()` then `destroy()`;
- no GC-based native cleanup assumption;
- no automatic hide-and-recreate on destroy failure.

## Prompt / media — frozen from LAB-2A

One-image proof only:

```text
VlmChatMessage(image path + text)
→ applyChatTemplate(...)
→ inject current-turn media paths
→ generateStreamFlow(...)
```

Start `nCtx = 4096` unless the exact pinned API/model establishes otherwise. Projector geometry must come from the actual mmproj metadata, never a copied magic image size.

## Backend evidence — frozen from LAB-2A

`compute_unit = npu` is a request, not proof. LAB-2B records separately:

- requested compute unit;
- resolved `runtime_id` from `ModelPaths`;
- actual native/backend evidence from the selected runtime/device logs;
- completed multimodal response.

No speed/temperature inference is accepted as backend identity.

## Deviations from LAB-2A playbook

None at this checkpoint.

The only newly established toolchain fact is Gradle `9.1.0`, recovered directly from the accepted Qualcomm source revision. The physical reference build itself remains pending rather than being replaced by a different matrix.

## Current gate

**LAB-2B B0/B1 — PHYSICAL REFERENCE APP GATE.**

B2 has **not** started. LAB-2C has **not** started.
