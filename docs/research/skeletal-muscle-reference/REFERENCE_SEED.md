# Reference Evidence Seed

This file preserves numerical evidence explicitly printed in the surviving Deep Research reports so useful values do not depend on the vanished temporary XLSX/CSV pack.

It is now **historical seed documentation**, not the active machine-readable reference. Direct primary-source verification has superseded many of the numerical entries below.

Current structured sources live in:

- `data/reference_observations_v0_1.csv` — Riem adult-male morphology;
- `data/architecture_observations_v0_1.csv` — Charles lower-limb architecture;
- `data/source_registry_v0_1.csv` — provenance;
- `data/reference_profile_healthy_adult_male_v0_1.csv` — selected reference morphology.

## Riem et al. adult-male MRI volume seed

The rerun reported that Riem et al.'s whole-body MRI dataset contains 102 healthy active adults with a sex-specific male subset of **49 men**, mean age **32 ± 10 years** (range 19–50), mean height **1.80 ± 0.08 m**, mean mass **76.80 ± 11.26 kg**.

Selected directly resolved male means printed in the rerun included:

| Muscle / resolved head | Mean volume (cm³) | SD (cm³) |
|---|---:|---:|
| Vastus lateralis | 1070.58 | 202.07 |
| Gluteus maximus | 1017.08 | 212.65 |
| Adductor magnus | 663.34 | 161.95 |
| Vastus medialis | 549.26 | 117.07 |
| Latissimus dorsi | 496.93 | 115.26 |
| Triceps brachii | 496.46 | 111.70 |
| Deltoid | 493.77 | 96.83 |
| Soleus | 463.35 | 82.97 |
| Pectoralis major | 424.28 | 102.17 |
| Gluteus medius | 377.48 | 64.39 |
| Psoas major | 302.45 | 52.07 |
| Rectus femoris | 300.16 | 69.32 |
| Rectus abdominis | 278.53 | 68.74 |
| Gastrocnemius medial head | 266.93 | 56.44 |
| Semimembranosus | 266.87 | 55.72 |
| Biceps brachii | 203.63 | 48.26 |

These have since been checked against the primary Riem publication and expanded substantially in `reference_observations_v0_1.csv`.

### Directly resolved head-volume reconstructions

The rerun also printed male means for directly resolved heads:

| Parent | Segment | Mean volume (cm³) | Reconstructed parent fraction |
|---|---|---:|---:|
| Biceps femoris | Long head | 225.45 | ~66.5% |
| Biceps femoris | Short head | 113.77 | ~33.5% |
| Gastrocnemius | Medial head | 266.93 | ~62.9% |
| Gastrocnemius | Lateral head | 157.11 | ~37.1% |

The fractions are reconstructions from measured male component means, not source-published universal ratios.

Do not generate equivalent head/part fractions for biceps brachii, triceps, deltoid, pectoralis major, trapezius, gluteus medius or adductor magnus from whole-parent volumes alone.

## Charles et al. architecture seed

The reports highlighted Charles et al. 2019 because the study provides coherent in-vivo lower-limb architecture across 20 muscles in ten healthy young adults (five men, five women).

The report seed included examples such as:

| Muscle | Mean PCSA (mm²) | SD (mm²) |
|---|---:|---:|
| Vastus lateralis | 3206 | 1559 |
| Vastus intermedius | 2938 | 926 |
| Soleus | 3226 | 1042 |
| Sartorius | 333 | 84 |

and optimal-fibre-length examples including sartorius ~408 mm, adductor magnus ~231 mm, biceps-femoris long head ~204 mm, popliteus ~74 mm, adductor brevis ~76 mm and medial gastrocnemius ~97 mm.

The full primary-source Table 3 evidence has now been reconstructed into `data/architecture_observations_v0_1.csv`; this section remains only as research history.

## Specific-tension evidence examples

The reports deliberately recommend leaving canonical v1 specific tension unset.

Preserved examples:

- Erskine et al., young untrained men, quadriceps: **30 ± 5 N/cm²** under the study's in-vivo methodology.
- Arnold lower-limb model: **61 N/cm²** used as a modelling constant, not a directly measured universal biological value.
- The original synthesis additionally notes method-sensitive in-vivo values around **15–15.5 N/cm²** for soleus/tibialis anterior in one protocol and **55 ± 11 N/cm²** in another adult-male quadriceps protocol.

These values belong in source-level evidence, not in a universal `specificTension` field.

## Production handling

This file should not be imported by the application. It exists so the original research trail remains inspectable after primary-source reconstruction moves forward.
