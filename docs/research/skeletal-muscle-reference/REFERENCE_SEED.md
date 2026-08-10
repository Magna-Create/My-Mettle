# Reference Evidence Seed

This file preserves numerical evidence explicitly printed in the surviving Deep Research reports so useful values do not depend on the vanished temporary XLSX/CSV pack.

It is **not** the final production reference table. Values remain source/population evidence until a production reference-selection process is defined.

## Riem et al. adult-male MRI volume seed

The rerun reports that Riem et al.'s whole-body MRI dataset contains 102 healthy active adults with a sex-specific male subset of **49 men**, mean age **32 ± 10 years** (range 19–50), mean height **1.80 ± 0.08 m**, mean mass **76.80 ± 11.26 kg**.

Selected directly resolved male means printed in the rerun:

| Muscle / resolved head | Mean volume (cm³) | SD (cm³) | Status |
|---|---:|---:|---|
| Vastus lateralis | 1070.58 | 202.07 | source male mean |
| Gluteus maximus | 1017.08 | 212.65 | source male mean |
| Adductor magnus | 663.34 | 161.95 | source male mean |
| Vastus medialis | 549.26 | 117.07 | source male mean |
| Latissimus dorsi | 496.93 | 115.26 | source male mean |
| Triceps brachii | 496.46 | 111.70 | source male mean |
| Deltoid | 493.77 | 96.83 | source male mean |
| Soleus | 463.35 | 82.97 | source male mean |
| Pectoralis major | 424.28 | 102.17 | source male mean |
| Gluteus medius | 377.48 | 64.39 | source male mean |
| Psoas major | 302.45 | 52.07 | source male mean |
| Rectus femoris | 300.16 | 69.32 | source male mean |
| Rectus abdominis | 278.53 | 68.74 | source male mean |
| Gastrocnemius medial head | 266.93 | 56.44 | source male mean |
| Semimembranosus | 266.87 | 55.72 | source male mean |
| Biceps brachii | 203.63 | 48.26 | source male mean |

### Directly resolved head-volume reconstructions

The rerun also prints male means for directly resolved heads and transparently reconstructs parent fractions:

| Parent | Segment | Mean volume (cm³) | Reconstructed parent fraction |
|---|---|---:|---:|
| Biceps femoris | Long head | 225.45 | ~66.5% |
| Biceps femoris | Short head | 113.77 | ~33.5% |
| Gastrocnemius | Medial head | 266.93 | ~62.9% |
| Gastrocnemius | Lateral head | 157.11 | ~37.1% |

The fractions are **reconstructions from measured male component means**, not source-published universal ratios. They should retain `valueSource = RECONSTRUCTED` when used.

Do not generate equivalent head/part fractions for biceps brachii, triceps, deltoid, pectoralis major, trapezius, gluteus medius or adductor magnus from whole-parent volumes alone; the reports explicitly reject arbitrary splitting.

## Architecture/PCSA examples preserved from the original synthesis

The original synthesis prints examples from a human lower-limb MRI/DTI architecture dataset (Charles et al.) to demonstrate cross-muscle architectural differences. The report describes the cohort as ten healthy adults, five men and five women, so these values are **mixed-sex context evidence**, not adult-male canonical values.

### PCSA examples

| Muscle | Mean PCSA (mm²) | SD (mm²) | Eligibility |
|---|---:|---:|---|
| Vastus lateralis | 3206 | 1559 | context-only mixed sex |
| Vastus intermedius | 2938 | 926 | context-only mixed sex |
| Soleus | 3226 | 1042 | context-only mixed sex |
| Sartorius | 333 | 84 | context-only mixed sex |

### Fibre/fascicle-length examples

| Muscle | Reported mean length (mm) | Notes |
|---|---:|---|
| Sartorius | ~408 | original synthesis also prints ±30 mm |
| Adductor magnus | ~231 | context architecture evidence |
| Biceps femoris long head | ~204 | context architecture evidence |
| Popliteus | ~74 | context architecture evidence |
| Adductor brevis | ~76 | context architecture evidence |
| Medial gastrocnemius | ~97 | context architecture evidence |

These examples are useful because they demonstrate why equal volume does not imply equal force capacity: muscles differ substantially in fibre length and parallel contractile area.

## Specific-tension evidence examples

The reports deliberately recommend leaving canonical v1 specific tension unset.

Preserved examples:

- Erskine et al., young untrained men, quadriceps: **30 ± 5 N/cm²** under the study's in-vivo methodology.
- Arnold lower-limb model: **61 N/cm²** used as a **modelling constant**, not a directly measured universal biological value.
- The original synthesis additionally notes method-sensitive in-vivo values around **15–15.5 N/cm²** for soleus/tibialis anterior in one protocol and **55 ± 11 N/cm²** in another adult-male quadriceps protocol.

These values belong in source-level evidence, not in a universal `specificTension` field.

## Production handling

When these values are migrated into machine-readable data, each record should receive at least:

- `sourceId`
- `entityId`
- `variable`
- `value`
- `unit`
- uncertainty/dispersion where printed
- `measurementTier`
- `populationCompatibility`
- `entityCompatibility`
- `methodCompatibility`
- `valueSource`
- `availabilityStatus`

No number in this file should silently become the application's biological source of truth merely because it is currently the best available seed.
