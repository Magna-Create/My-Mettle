# LAB-2B B3 route decision

> **Status:** NPU route rejected for LAB-2B production readiness. Non-NPU continuation remains under review. B4 is blocked.

## Decision summary

LAB-2B will not continue pursuing the Qualcomm GenieX `llama_cpp` NPU/HTP path with the currently published standard Android AARs.

This decision is based on repeated static evidence across three published points:

| GenieX Android artefact | Static 16 KB result | Relevant failing payloads |
| --- | --- | --- |
| `0.3.5` reference AAR | FAIL | 13 Qualcomm Hexagon objects at `2**12` |
| `0.3.19` standard AAR | FAIL | 11 Qualcomm Hexagon objects at `2**12` |
| `0.6.1` current stable standard AAR | FAIL | same 11 Qualcomm Hexagon objects at `2**12` |

The `0.6.1` AAR was checked against Qualcomm's published SHA-256 before inspection. The target S25 Ultra reports a runtime page size of `4096` bytes, so this does not contradict the successful reference-app initialisation on that device. The rejection is about the required 16 KB deployment/native-compatibility gate, not about whether HTP can execute on the current 4 KB firmware.

## What is rejected

Rejected for this LAB-2B route:

```text
Qwen3-VL-2B-Instruct
→ GGUF Q4_0
→ standard Qualcomm GenieX Android AAR
→ runtime_id = llama_cpp
→ compute_unit = npu
→ HTP / Hexagon backend
```

No further standard-AAR NPU version cycling is justified without a materially new Qualcomm artefact or explicit upstream fix.

## What is not rejected

The following remain viable research directions:

- local VLM inference generally;
- Qwen3-VL-2B-Instruct GGUF as the candidate model;
- GenieX `llama_cpp` without the HTP/Hexagon payload set;
- CPU inference as a correctness-first compatibility route;
- GPU as a later optimisation if a deployable Android packaging route is established.

LAB-2C remains prohibited until LAB-2B has one physically reproducible, deployment-compatible route.

## Preferred next candidate

Qualcomm `v0.6.1` publishes a dedicated CPU-only Android AAR:

```text
geniex-android-aar-cpu-v0.6.1.aar
bytes: 7,330,109
SHA-256: a454d2442997cd9146a34d5717ff88eb486b441d0e241f0e1302e1fdcea9b39a
```

Its source build preset retains:

```text
GENIEX_PLUGIN_LLAMA_CPP = ON
```

and disables:

```text
GENIEX_PLUGIN_QAIRT = OFF
GGML_HEXAGON = OFF
GGML_OPENCL = OFF
GGML_OPENMP = OFF
GENIEX_CPU_ONLY = ON
```

This makes it a cleaner compatibility candidate than simply setting `compute_unit=cpu` or `gpu` against the standard AAR, because the standard AAR still physically packages the failing Hexagon payloads.

The CPU-only AAR is **not yet accepted**. Before any model download/import, the next route must pass:

1. published digest verification;
2. static native/16 KB audit of the CPU-only AAR;
3. harness API/build compatibility review for `v0.6.1`;
4. physical SDK initialisation on the target device.

Only after those checks may B4 resume with the exact model pair.

## GPU policy

GPU is deferred rather than selected as the immediate fallback. Qualcomm's standard Snapdragon AAR enables OpenCL but also packages the HTP/Hexagon objects that fail B3. Merely requesting GPU therefore does not make that artefact deployment-compatible.

A future GPU route requires either:

- a Qualcomm Android artefact whose packaged native set satisfies the 16 KB gate; or
- a separately justified packaging/build route that does not smuggle the rejected HTP payloads back into the APK.

Do not create that route during the current CPU compatibility check.

## Programme gate

```text
LAB-2B verdict = REVISE ROUTE
NPU/HTP = REJECTED FOR PRODUCTION READINESS
preferred candidate = GenieX v0.6.1 CPU-only / llama_cpp / CPU
candidate status = NOT YET PROVEN
B4 = BLOCKED
LAB-2C = NOT STARTED
```
