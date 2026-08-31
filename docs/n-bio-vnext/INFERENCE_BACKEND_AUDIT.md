# N-BIO Inference Backend Audit — Android

> **Status:** N-BIO-7B.X engineering audit, 2026-08-31. This document concerns numerical execution only. It does not select or change the mathematical N-BIO model and does not authorize product behaviour changes.

## 1. Decision boundary

N-BIO deliberately separates:

```text
MATHEMATICAL MODEL
        ↓
INFERENCE REPRESENTATION / SOLVER
        ↓
COMPUTE BACKEND
```

A slow solver is not evidence that the mathematical model is wrong. A fast accelerator is not evidence that its posterior approximation is scientifically adequate.

Backend promotion therefore follows measured end-to-end benefit after posterior/predictive fidelity is established.

## 2. Kotlin/JVM remains the reference implementation layer

The current Kotlin implementation remains useful because it is:

- portable across supported Android devices;
- deterministic/replayable;
- easy to validate against unit fixtures;
- the reference oracle for later native/GPU kernels;
- sufficient for architectural experiments that remove unnecessary work before hardware-specific optimisation.

The current real-history Candidate-v1 acceptance also demonstrated that algorithmic workload matters more than nominal phone utilisation: repeated chronological fits took minutes despite moderate thermal load. N-BIO-7B.X therefore prioritises sequential reuse and solver approximation before hardware migration.

## 3. Native CPU / ARM SIMD

Android NDK documentation states that all ARMv8/arm64 Android devices support Advanced SIMD (Neon), and NDK toolchains enable Neon for Arm ABIs by default. Android explicitly recommends portable compiler-vectorisable C/C++ rather than immediately hand-writing Neon intrinsics; Clang can lower portable vector operations to Neon.

Official references:

- https://developer.android.com/ndk/guides/cpu-arm-neon
- https://developer.android.com/ndk/guides/abis

### N-BIO implication

The first native prototype, if profiling justifies one, should be:

```text
Kotlin/JVM reference
→ C++ scalar equivalent
→ compiler-auto-vectorised/vector-friendly C++
→ explicit intrinsics only if profiling proves a remaining gap
→ multicore/tiled execution only where independent work is large enough
```

Candidate kernels include repeated log-density/likelihood evaluation, slack quadrature, log-sum-exp/reduction and posterior marginalisation **only after device profiling proves they dominate model time**.

No native port is justified merely because C++ sounds faster.

## 4. Vulkan compute

Android exposes Vulkan through the NDK. Compute shaders are supplied as SPIR-V; Android documents Vulkan compute as a route for workloads whose arithmetic is genuinely GPU-suitable. It also gives the application direct responsibility for device/queue setup, buffers, synchronisation and dispatch.

Official references:

- https://developer.android.com/ndk/guides/graphics/
- https://developer.android.com/guide/topics/renderscript/migrate/migrate-vulkan
- https://developer.android.com/ndk/guides/graphics/shader-compilers

### N-BIO implication

A bounded Vulkan prototype is justified only if CPU profiling identifies a large, regular, data-parallel kernel such as batched independent likelihood evaluation or a reduction over a sufficiently large posterior state set.

Any prototype must compare:

- kernel-only runtime;
- dispatch/setup overhead;
- CPU↔GPU buffer movement;
- end-to-end inference runtime;
- precision/posterior error vs the reference;
- deterministic tolerance/reduction ordering;
- memory;
- repeated-run thermal throttling.

If end-to-end Vulkan loses to the best CPU/algorithmic solver, the result is `NOT_JUSTIFIED` and the GPU path should stop there.

## 5. LiteRT / NPU reality check

LiteRT hardware delegates accelerate **LiteRT model graphs** on supported accelerators. Current Google AI Edge documentation describes vendor/platform delegates for neural-network model execution; for Android NPU use, vendor support and supported model operations matter. This is not a general API for running arbitrary Bayesian quadrature, custom posterior pruning, log-sum-exp graphs or factor-message code on an NPU.

Official references:

- https://ai.google.dev/edge/litert/android/npu
- https://ai.google.dev/edge/litert/performance/delegates
- https://ai.google.dev/edge/api/litert/kotlin/com/google/ai/edge/litert/CompiledModel.Options

### N-BIO decision

Current status:

```text
LiteRT / NPU = NOT_CURRENTLY_JUSTIFIED for the existing custom Bayesian kernels
```

This is not a claim that the device NPU is incapable. It means N-BIO currently has no demonstrated, semantically equivalent LiteRT graph whose supported ops preserve the required posterior computation.

A future NPU experiment is reasonable only if a solver is deliberately expressible as a supported LiteRT model/graph and can be validated against the reference posterior. Device marketing names must never be treated as proof of delegate availability.

## 6. Current backend ladder

```text
1. Kotlin/JVM deterministic reference                         IMPLEMENTED
2. algorithmic reuse / solver competition                    ACTIVE
3. sequential dense-grid substrate                           IMPLEMENTED
4. adaptive posterior-pruned grid substrate                  IMPLEMENTED
5. conditional-Laplace Candidate-v2 challenger               IMPLEMENTED
6. Gaussian sigma-point generic challenger                   IMPLEMENTED / NOT YET CANDIDATE-V2 ADAPTER
7. low-rank posterior viability screen                       IMPLEMENTED / SCREEN ONLY
8. native C++ scalar/vectorised kernel                       GATED BY DEVICE PROFILE
9. multicore native                                          GATED BY PARALLEL HOTSPOT
10. Vulkan compute                                           GATED BY CPU PROFILE + END-TO-END BENCHMARK
11. LiteRT/NPU                                               NOT CURRENTLY JUSTIFIED
```

## 7. Device evidence required before native/GPU work

The single `N-BIO Adaptive Inference Acceptance` developer action now reports two different evidence classes:

1. **Candidate-v2 same-mathematical-model comparison** — dense reference versus conditional-Laplace over installed historical evidence.
2. **Solver-substrate synthetic microbenchmark** — dense sequential grid, adaptive sparse grid, Gaussian sigma-point and low-rank screening on one shared dynamic level+trajectory fixture.

The second is a backend/representation benchmark, not biological validation.

Native/GPU work should begin only after that report establishes where time is actually spent and whether algorithmic approximations already deliver sufficient speed with acceptable fidelity.

## 8. Production decision rule

No backend wins on runtime alone. Selection must consider:

- posterior quantiles, variance, covariance and tails;
- predictive CRPS/PIT/WIS where the solver is connected to a real model;
- deterministic replay/tolerance;
- median/p95 update runtime;
- full replay runtime;
- memory;
- battery/thermal behaviour;
- portability/device fallback;
- implementation and maintenance complexity.

A dual-solver product is acceptable if provenance/authority is explicit, for example a fast online filter plus richer non-authoritative reanalysis, but it must never create two ambiguous authoritative capability states.
