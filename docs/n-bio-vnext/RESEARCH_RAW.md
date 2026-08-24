# N-BIO vNext: evidence-based stimulus, capability, exercise translation, recovery and heterogeneous performance modelling

## Executive recommendation and mathematical architecture

My Mettle's existing boundaries are fundamentally sound and should be retained. The repository already separates canonical anatomy/reference physiology, execution profiles and their recruitment allocations, exercise-independent programme intent, immutable set evidence, versioned inference runs, muscle-state snapshots and execution-profile translation state. Raw workout evidence is intentionally free of subjective effort fields, while the present inference engine treats working sets as recruitment-weighted set exposure, keeps development neutral, leaves recovery/recent stimulus null, and limits translation to same-profile anchors. That is an unusually good starting point because later biological models can be replaced and recomputed without rewriting what the user actually performed. fileciteturn3file0L2-L2 fileciteturn4file0L2-L2 fileciteturn5file0L2-L2

The principal vNext change should therefore **not** be a new universal “muscle load” equation. N-BIO should become a **multi-latent state-space model**. Dynamic strength is strongly task-specific; high-load training improves high-load/dynamic strength more than low-load training even where hypertrophy is comparable, and dynamic training transfers only partially to non-trained isometric strength. Conversely, hypertrophy occurs over a broad range of resistance loads when training is sufficiently demanding. A scalar intended to mean kilograms, stimulus, fatigue, endurance and muscular development simultaneously would therefore discard biologically important distinctions rather than discover a latent common unit. citeturn20search2turn20search3turn20search11turn22search0

I recommend the following v1 latent decomposition:

| Layer | Recommended latent | Meaning | Unit / representation | Prescription role |
|---|---|---|---|---|
| Raw observation | `PerformanceObservation` | Immutable facts actually performed | Native physical units | Never inferred or overwritten |
| Profile performance | `CapabilityState_e` | What the user can demonstrably perform on a particular execution profile | Family-specific curve/posterior | Primary load/reps/time predictor |
| Profile adaptation | `SkillState_e` | Exercise-specific neural, technical and coordination contribution not attributable to general muscle development | log-scale dimensionless latent | Separates rapid strength learning from development |
| Set demand | `SetDemand_s` | Unobserved degree to which the set approached the user's current performance frontier | probability/distribution, `[0,1]` derived quality | Modulates confidence in stimulus evidence |
| Muscle exposure | `Exposure_sm` | Recruitment-weighted exposure from set `s` to muscle segment `m` | dimensionless set-equivalent exposure | Conservative continuation of current v0 |
| Muscle stimulus | `EffectiveDose_sm` | Uncertain stimulus estimate after demand is considered | posterior dimensionless dose | Dose accumulation, never kg-equivalent |
| Acute muscle state | `RecentStimulus_m`, `Fatigue_m` | Recent training exposure versus transient performance suppression | separate dimensionless states | Recovery/readiness and scheduling |
| Long-term muscle state | `Development_m` | Slow general muscular adaptation inferred across time/profiles | user-relative index; prior `1.0` | Weak modifier, not a muscle-size percentage |
| Structural state | `VolumeScale_m`, `StructuralCapacityScale_m` | User-specific morphology/structural capacity if actually inferable | nullable | **Remain null initially** |
| Conditioning | `ConditioningState_f` | Exercise-family endurance/cardiorespiratory capability | power-duration, speed-duration etc. | Cardio-specific prescription |

The repository's current `developmentIndex`, `volumeScale`, `structuralCapacityScale`, `recentStimulus` and `recovery` separation is therefore directionally correct; vNext should give some of those fields defensible semantics rather than collapse them. fileciteturn4file0L2-L2

A critical distinction is that **exercise capability is not muscle development**. Meta-analysis shows whole-muscle strength increases can considerably exceed fibre-level specific-force adaptation, and resistance-training strength gains contain substantial neural/skill/task-specific components. The recent task-specificity meta-analysis found an effect size of about 0.98 for trained dynamic strength versus only 0.42 for transfer to non-trained isometric strength. This justifies an explicit fast `SkillState_e` beside the slower muscle-general state. citeturn21search5turn22search0

The recommended data flow is:

```text
IMMUTABLE EVIDENCE
──────────────────────────────────────────────────────────────────
session / executionProfile / setRecord / body-mass context
 load, assistance, reps, duration, distance, side, equipment...
                              │
                              ▼
PERFORMANCE NORMALISATION
──────────────────────────────────────────────────────────────────
entry-basis conversion + equipment/profile semantics
bodyweight / assistance treatment
NO cross-profile kg equivalence
                              │
             ┌────────────────┼─────────────────────┐
             ▼                ▼                     ▼
DYNAMIC CAPABILITY      HOLD CAPABILITY      CONDITIONING CAPABILITY
load↔rep frontier       load↔duration         power/speed↔duration
profile-specific        profile-specific      modality-specific
             │                │                     │
             └────────────────┴───────┬─────────────┘
                                     ▼
                              SET-DEMAND POSTERIOR
                            unknown submaximality
                              ≠ reported RIR
                                     │
                                     ▼
EXECUTION PROFILE ─────────► MUSCLE EXPOSURE
recruitment allocations      weighting × set evidence
                                     │
                                     ▼
                           EFFECTIVE STIMULUS DOSE
                              posterior + interval
                                     │
                         ┌───────────┴───────────┐
                         ▼                       ▼
                  RECENT STIMULUS             FATIGUE
                    slower memory             fast decay
                         │                       │
                         └───────────┬───────────┘
                                     ▼
                       LONG-TERM MUSCLE DEVELOPMENT
                       + exercise-specific SkillState
                                     │
                                     ▼
                       CROSS-PROFILE TRANSLATION
                     hierarchical / similarity prior
                                     │
                                     ▼
                           SESSION PRESCRIPTION
                   load / reps / duration OR null
                       + predictive uncertainty

All derived nodes are attached to a versioned inference_run.
Recomputation starts from immutable evidence.
```

This preserves the repository's existing provenance architecture: an inference run can version recruitment, stimulus, muscle-state and translation algorithms while historical workout evidence and prior prescriptions remain immutable snapshots. fileciteturn4file0L2-L2

**The v1 design principle should be:** infer the narrowest quantity the evidence genuinely supports. A 70 kg × 8 bench-press set is strong evidence about bench-press performance; somewhat weaker evidence about pectoral/triceps loading; considerably weaker evidence about current hypertrophy; and essentially no direct evidence about the user's anatomical pectoral volume. The posterior should widen as N-BIO travels across those inferential boundaries.

For implementation, use two separate forms of uncertainty. **Scientific uncertainty** is attached to model/version/provenance: for example, “volume–hypertrophy direction: strong; exact diminishing-return equation: weak”. **Individual posterior uncertainty** is numerical: distributions around this user's capability, dose, recovery or translation. Conflating those two would allow a precise statistical estimate built on a physiologically weak assumption to masquerade as high confidence.

For positive continuous latents, log-normal or approximately Gaussian states on the log scale are convenient. For bounded quantities such as inferred demand or recovery, Beta/logit-normal posteriors are suitable. Store at least `p05`, `p50`, `p95`, effective independent session count, observation count, provenance and model version rather than a naked decimal. A robust Student-t observation distribution is preferable to Gaussian-only updates because one unusually poor workout should not catastrophically rewrite the person's state.

A practical v1 variable contract would be:

| Variable | Range | Unit | Interpretation |
|---|---:|---|---|
| `RecruitmentAllocation.weighting` | `[0,1]` | dimensionless | Independent exposure coefficient for that muscle/profile |
| `CapabilityDynamic(r)` | `>0` | profile resistance coordinate, usually kg where meaningful | Predicted demonstrated resistance capability at `r` reps |
| `CapabilityHold(T)` | `>0` | kg at duration `T`, where loaded | Profile-specific load-duration frontier |
| `CapabilityDurationOnly` | `>0` | s | Duration capability at body/context state |
| `SubmaximalitySlack u` | `≥0` | log-performance | Distance below estimated performance frontier |
| `DemandProbability q` | `[0,1]` | probability | Probability set lay inside a model-defined high-demand band |
| `Exposure_sm` | `[0,1]` normally | muscle-specific set-equivalent | Current conservative set exposure |
| `EffectiveDose_sm` | posterior | muscle-specific dimensionless dose | Exposure adjusted by uncertain set demand |
| `RecentStimulus_m` | `≥0` | dimensionless | Decayed recent muscle dose |
| `Fatigue_m` | `≥0` | dimensionless | Transient performance-suppression state |
| `Recovery_m` | `[0,1]` | probability/index | Readiness posterior, not “% physically repaired” |
| `Development_m` | positive, prior `1` | relative index | Slow general adaptation latent |
| `Skill_e` | real, centred near `0` | log capability | Profile-specific neural/technical adaptation |
| `VolumeScale_m` | nullable | relative | Leave null without user-specific morphological evidence |
| `StructuralCapacityScale_m` | nullable initially | relative | Do not derive directly from reference anatomy |

The term *set-equivalent* must remain **muscle-local**. A quadriceps dose of 8 units is not claimed biologically equal to 8 forearm units, just as 8 cycling minutes is not eight hypertrophy sets. The purpose is longitudinal comparison within a modelled tissue and model version, not conservation of a universal physical quantity.

## Performance inference across dynamic resistance, holds and cardio

**Dynamic resistance should use an execution-profile-specific performance frontier rather than a generic e1RM formula.** Repetition-to-failure equations can estimate 1RM tolerably under constrained circumstances, but their error varies by exercise, repetitions performed, training experience and method. In a 2025 cross-validation, equation performance remained exercise-specific and the recommended use was restricted to roughly 4–10 repetitions to failure; other studies report systematic over/under-estimation, while individual load–velocity relationships outperform generic relationships in some exercises. citeturn13search0turn13search1turn13search7turn13search8

That matters particularly because N-BIO deliberately does **not** know whether a completed set was a repetition-to-failure set. Without RIR, velocity loss, an objective failure flag or equivalent signal, a single `80 kg × 8` observation establishes that the user could perform at least eight repetitions at 80 kg; it does not establish an 8RM. Repeated history can learn an empirical upper performance frontier, but true repetitions-in-reserve remain partially unidentifiable. Even when people are explicitly asked to estimate repetitions to failure, prediction error and heterogeneity are substantial, especially farther from failure. citeturn20search12turn13search6

A particularly suitable mathematical structure for N-BIO is therefore a **stochastic frontier model**. This is a modelling recommendation rather than an established exercise-physiology equation:

\[
y_s=\ln R_s
\]

\[
y_s = a_e(t)-b_e\ln(r_s)-u_s+\epsilon_s
\]

where:

- \(R_s\) is the execution-profile resistance coordinate;
- \(r_s\) is completed repetitions;
- \(a_e(t)\) is the time-varying profile capability intercept;
- \(b_e>0\) is that user's/profile's load–rep slope;
- \(u_s\ge0\) is latent **submaximality slack**;
- \(\epsilon_s\) is symmetric day-to-day/performance noise, preferably Student-t.

The attractive property is \(u_s\): N-BIO acknowledges that a set may have stopped below the performance frontier instead of silently declaring it an RM test. Repeated high-performing sets pull the estimated frontier upwards; repeated patterns across loads/repetition ranges personalise \(b_e\); one ordinary completed set does not suddenly reveal a precise 1RM.

The profile's curve becomes:

\[
\hat R_e(r,t)=\exp\left[a_e(t)-b_e\ln r\right].
\]

This is not asserted to be the true physiological load–repetition law over every possible repetition range. It is a compact monotonic approximation over the user's **observed domain**. Extrapolation outside that domain must widen uncertainty sharply. A calculated 1RM may be exposed as a secondary estimate when low-repetition evidence exists, but it should not be N-BIO's canonical capability. Evidence that individualised performance relations predict repetitions substantially better than generalised relations supports learning per-user/per-profile curves. citeturn13search2turn13search12

For programme resolution, a better quantity than e1RM is often `CapabilityDynamic(targetRep)` directly. If the next prescription calls for six to eight repetitions, model six to eight repetitions. There is little computational value in extrapolating to 1RM and immediately translating back to eight reps.

**Resistance normalisation must be profile-aware rather than “all things become total kilograms”.** Let:

\[
R_s = k_{bw,e}M_s+k_{ext,e}X_s-k_{assist,e}A_s ,
\]

where \(M\) is body mass, \(X\) external resistance, \(A\) assistance and the non-negative coefficients are execution-profile-specific. This equation is a resistance-coordinate model, **not a claim about individual muscle force**.

For a barbell exercise, \(k_{bw}=k_{assist}=0\) and the recorded external mass can usually serve directly as \(R\). For weighted pull-ups, a starting bookkeeping model may use body mass plus external mass minus machine assistance, while preserving uncertainty in how those terms transfer to joint/muscle demands. For push-ups, the proportion of body mass supported changes with body geometry and execution, so the body-mass coefficient should be a profile prior rather than silently equating body mass with lifted mass. For machines whose “level 8” has no documented force calibration, `R` remains an **ordinal device/profile coordinate** and must not be labelled kilograms.

Entry bases should be deterministic metadata, not physiological inference. A pair of 20 kg dumbbells entered as `PER_HAND` can be represented as 40 kg total external implement mass for bookkeeping while the execution profile still remembers that the performance basis was 20 kg per hand. A unilateral 20 kg dumbbell row is a 20 kg per-active-side observation; multiplying it to 40 kg merely to imitate bilateral exercise would destroy meaning. Side, implement count and input basis should therefore survive the normalisation layer.

Assisted movements require monotonicity tests: with body mass and reps unchanged, lower assistance must map to higher resistance. A 75 kg athlete moving from `30 kg assistance × 8` to `20 kg assistance × 8` has moved a simple system-load index from approximately 45 to 55 kg under the elementary \(M-A\) convention; that is valid *within the pull-up profile*, not an assertion that either figure is the latissimus force.

**Rep ranges should not receive a simplistic hypertrophy multiplier.** Meta-analyses consistently find greater maximal-strength gains from moderate/high loads, but broadly similar hypertrophy across load ranges when protocols are otherwise sufficiently demanding. Hence repetition range belongs strongly in the capability model and only indirectly in stimulus through inferred set demand; `5 reps` should not intrinsically be worth more or fewer muscle-dose units than `15 reps`. citeturn20search2turn20search3turn20search11

Failure should likewise not become a binary requirement. Meta-analyses find that training to momentary failure is not necessary for strength or hypertrophy, although sets closer to failure can carry different hypertrophic and especially fatigue implications. Failure protocols also produce greater acute fatigue and slower recovery. N-BIO should therefore infer *demand probabilistically*, not manufacture an RIR integer. citeturn20search1turn20search5turn23search0turn23search15

A useful derived statistic from the frontier is:

\[
q_s=P(u_s\le\delta_e\mid \text{history}),
\]

where \(\delta_e\) defines a “near demonstrated frontier” band. `q_s` is **not RIR**; it is a posterior probability that observed performance was unusually demanding relative to this user's learned profile. The physiological mapping from that probability to hypertrophic stimulus is weakly known, so \(\delta_e\) is a model-version hyperparameter and must be recorded as such.

This allows N-BIO to preserve the existing conservative estimate:

\[
Exposure_{s,m}=w_{e,m}
\]

for each working set, while optionally adding:

\[
EffectiveDose_{s,m}=w_{e,m}\,Q_s,\qquad Q_s\sim p(Q_s\mid\text{performance history}).
\]

When \(Q_s\) is uninformative, **do not replace the current set unit with a precise decimal**. Store a broad posterior or leave the effective component unresolved while retaining `Exposure`. That is more scientifically honest than pretending that load and repetitions alone reveal closeness to failure.

**ROM and muscle length should principally define execution profiles, not be arbitrary set multipliers.** A 2021 meta-analysis favoured full ROM over partial ROM for strength and lower-limb hypertrophy, but newer analyses of mean muscle length and lengthened partials suggest the picture is more nuanced and that long-length partial training can sometimes approach full-ROM outcomes. Therefore ROM class, joint-angle region and long-muscle-length exposure should be profile features feeding recruitment and cross-profile similarity; v1 should not encode “full ROM = ×1.23 stimulus”. citeturn21search17turn21search4

Tempo has an even weaker claim to a multiplier. The systematic review of repetition duration found broadly similar hypertrophy across approximately 0.5–8 seconds per repetition and only tentative evidence that deliberately very slow repetitions above roughly 10 seconds may be inferior. N-BIO should record tempo when objectively available but **not use time-under-tension as a linear hypertrophy dose term**. citeturn21search0

Rest intervals should be context for performance/fatigue rather than a direct muscle-dose multiplier. Both short and long rests can produce hypertrophy; the literature offers some evidence favouring longer rest in trained participants but remains sparse. Rest strongly affects the capability to maintain repetitions/load between sets, which N-BIO already observes directly through performance. citeturn21search3

Training frequency should similarly be treated primarily as temporal distribution of dose. When weekly volume is equated, later meta-analysis finds little meaningful hypertrophy advantage from higher frequency, while strength-frequency effects are substantially mediated by extra volume. Thus a muscle trained on Monday and Thursday should not receive a special frequency multiplier merely because it was trained twice; its dose and recovery trajectories already encode the relevant timing. citeturn21search6turn21search11

**Isometrics require a different capability family.** The literature supports isometric training as a genuine strength/hypertrophy stimulus, with adaptation influenced by contraction angle/muscle length and training intent, while transfer between dynamic and isometric tasks remains incomplete. This rules out treating “45 seconds” as though it were “45 repetitions”. citeturn22search2turn22search0turn22search1

For loaded holds, use a profile-specific monotonic load-duration frontier:

\[
\ln L_s=a_e(t)-b_e\ln(T_s/T_0)-u_s+\epsilon_s
\]

where \(L\) is load, \(T\) duration and \(T_0\) a reference duration such as 30 seconds chosen for numerical conditioning. Again, \(u_s\) represents unknown submaximality.

This family is biologically plausible because endurance duration changes very substantially with relative isometric intensity. In classic sustained handgrip data, mean endurance times in men were approximately 364, 139 and 66 seconds at 30%, 50% and 75% MVC respectively; women also showed systematically different durations. That is evidence for a nonlinear force–duration relationship and against a universal “kg-seconds” workload scalar. citeturn23search3

A dead hang where only duration is recorded should instead maintain:

\[
\ln T_s=c_e(t)-b_{bw,e}\ln(M_s/M_0)-u_s+\epsilon_s,
\]

with the body-mass term omitted or left weakly identified when body mass barely varies. Its state is `duration capability under this execution profile`; no hidden kilograms need be invented.

A loaded unilateral grip hold can use the load-duration curve directly and should be side-resolved. A suitcase hold that also creates anti-lateral-flexion demand can contribute simultaneously to grip/forearm exposure and trunk stabiliser exposure through recruitment allocations while maintaining **one performance profile**. The measured 20 kg is evidence about the whole task; it is not separately 20 kg of forearm force and 20 kg of quadratus-lumborum force.

Repeated grip rolls or contractions should form another dynamic/repeated-contraction profile, with load, cycles/repetitions and possibly cadence/duration. A continuous static hold and fifty wrist-roll cycles should remain related through cross-profile similarity but should not share the same load-duration equation.

**Cardiorespiratory/conditioning exercises require a parallel adaptation model.** Where cycling or rowing power is available, the critical-power family provides a well-supported performance representation:

\[
P(t)=CP+\frac{W'}{t},
\]

where `CP` represents the asymptotic sustainable power-domain parameter and \(W'\) a finite amount of work that can be expended above it. Training can change these components somewhat independently. Comparable critical-speed/distance formulations can be used for running where appropriate. citeturn22search8turn22search12

N-BIO should therefore share a **generic performance-observation substrate**, not a generic adaptation scalar:

```text
PerformanceObservation
    metricFamily
    executionProfileId
    time
    side?
    quantities {
        loadKg?
        assistanceKg?
        repetitions?
        durationSeconds?
        distanceMetres?
        speedMps?
        grade?
        powerWatts?
        cadencePerMinute?
        elevationMetres?
        steps?
        floors?
        machineLevel?
    }
    equipmentIdentity
    bodyMassContext?
```

Then different inference families consume only semantically valid fields.

| Exercise | Primary capability model | Skeletal-muscle N-BIO contribution | Conditioning contribution |
|---|---|---|---|
| Barbell/dumbbell/machine resistance | Load–rep | Yes | Normally negligible |
| Bodyweight resistance | Body/context–rep | Yes | Sometimes secondary |
| Static loaded grip/suitcase hold | Load–duration | Yes | Usually secondary |
| Dead hang/plank | Duration/context | Yes, uncertain | Usually secondary |
| Cycling with power | Power–duration | Local muscle exposure only if modelled; **no hypertrophy-set conversion** | Yes |
| Rowing with power | Power–duration | Possible local exposure, uncertain | Yes |
| Running/treadmill | Speed–duration plus grade | Contextual local fatigue only initially | Yes |
| Stair machine | Device-specific pace/steps/level | Possible fatigue context | Yes |
| Walking/steps | Distance/speed/grade | Normally no set-equivalent stimulus | Yes/health context |
| Uncalibrated machine “level” | Device-specific ordinal curve | Profile-local only | Profile-local only |

Running, cycling or rowing can affect lower-body fatigue and concurrent-training outcomes, but evidence does not justify converting thirty minutes of cycling into, say, “four quadriceps sets”. Concurrent resistance/endurance evidence shows modality- and outcome-dependent interference rather than a clean conversion factor. citeturn21search9turn21search16

Treadmill speed, duration and grade are useful raw dimensions. If body mass is available, vertical mechanical power can be approximated from vertical velocity for contextual analysis, but it is not total metabolic or muscular work. StairMaster resistance levels, “floors” and similar proprietary quantities should remain machine/profile-specific unless manufacturer calibration is known. Power in watts is far more transportable than an arbitrary machine level.

## Muscle recruitment, cross-exercise translation and morphology

The current execution-profile → stable muscle-segment mapping is the correct level at which to represent recruitment. Exercise names alone are insufficient because joint angle, ROM, equipment geometry, cable direction, grip, stance and technique alter the external joint moments and muscle operating lengths. External joint mechanics also do not uniquely determine each muscle's force because human joints have redundant agonists, antagonistic co-contraction and stabilisation requirements. citeturn22search6turn22search7

Surface EMG should be treated as **supporting evidence for activation**, not as a proportional muscle-force, hypertrophy or recruitment-weight measurement. EMG–force relations can change with joint angle because active/passive force and moment arms vary, and muscle excitation does not map one-to-one onto total joint torque. Consequently an EMG study showing muscle A at “130% of muscle B” does not justify storing `weightingA = .57`, `weightingB = .43`. citeturn22search4turn22search6turn22search7

This leads to a strong recommendation for the meaning of `RecruitmentAllocation.weighting`:

> **`weighting` is an independent, dimensionless estimate of the relative local loading/exposure of one stable muscle segment during the specified execution profile, normalised so that `1.0` represents a high, direct loading exposure for that segment under a reference execution. It is not a fraction of exercise load, not an EMG percentage, and not a share of a conserved total.**

Therefore **weights should not sum to one**.

A sum-to-one convention causes a biomechanical absurdity: adding evidence that an exercise also demands a stabiliser would automatically reduce the stored exposure of its unchanged prime movers. Muscles act concurrently; their demands are not pieces of a fixed 100% pie. The sum may legitimately exceed 1.0, and values across different muscles should not be interpreted as equal newtons or equal anabolic effect.

Normalisation should primarily be **within a muscle segment across execution profiles**. A weighting near 1 means “this is a high-exposure task for this segment”; it does not mean a quadriceps `1.0` and a forearm `1.0` have equal force capacity or equal dose requirement.

`role = PRIME | SYNERGIST | STABILISER` should remain separately recorded. It is descriptive and provenance-bearing, not a fixed multiplier. A stabiliser can have substantial isometric demand, and muscles can simultaneously contribute joint moment and joint stability. For v1, let the weighting already express expected exposure and use `role` for explanation/search/confidence. **Do not additionally apply an arbitrary `STABILISER × 0.25` factor.**

A defensible authoring pipeline is:

```text
execution description
      ↓
joint actions + ROM + technique constraints
      ↓
external resistance/moment curve
      ↓
known segment moment arms and operating lengths
      ↓
prime/synergist/stabiliser candidate set
      ↓
EMG / imaging / biomechanical evidence as supporting evidence
      ↓
independent weighting [0,1]
      + role
      + uncertainty
      + evidence provenance
      + evidence-specific execution conditions
```

Moment-arm and architecture evidence can improve priors, but N-BIO should not attempt v1 inverse dynamics down to muscle force unless execution kinematics and external-force geometry are actually measured. EMG–force evidence itself shows how strongly joint angle and length-tension mechanics complicate such inference. citeturn22search6turn14search6

A useful recruitment record extension would therefore be conceptually:

```text
RecruitmentAllocation {
    muscleSegmentId
    weighting              // 0..1 independent exposure coefficient
    role
    confidenceDistribution
    provenanceType         // biomechanics | EMG | anatomy | expert synthesis
    provenanceReference
    applicableRom
    applicableTechnique
    resistanceCurveClass
    modelVersion
}
```

The **cross-exercise translation problem is then a hierarchical prediction problem**, not a table of kg ratios.

Represent every execution profile with a feature vector \(x_e\) containing at least:

| Feature family | Examples |
|---|---|
| Muscle exposure | full `RecruitmentAllocation` vector; cosine/overlap similarity |
| Joint mechanics | primary joint actions, multi/single-joint, movement pattern |
| Contraction | dynamic concentric/eccentric, isometric, repeated hold |
| ROM | full/partial; long-length region; profile-specific angles |
| Kinetic chain | open/closed |
| Bodyweight | none, partial, dominant; assistance mechanism |
| Laterality | unilateral, bilateral, alternating |
| Equipment | barbell, dumbbell, cable, plate machine, selectorised machine, bodyweight |
| Resistance curve | approximately constant external mass, cable, cam, elastic, unknown |
| Grip/support | grip-limited, straps, supported torso, free stabilisation |
| Entry basis | total, per-hand, per-side, assistance, machine level |
| Measurement family | load–rep, load–duration, duration-only, power–duration etc. |

A recruitment-vector cosine similarity can be derived from the independent weights:

\[
sim_{muscle}(e,i)=
\frac{\mathbf w_e\cdot\mathbf w_i}
{\|\mathbf w_e\|\|\mathbf w_i\|},
\]

without requiring either vector to sum to one.

The recommended hierarchical capability decomposition is:

\[
a_e(t)=\alpha_e + Skill_e(t)
      +\gamma_e\sum_m w_{e,m}G_m(t)
      -\lambda_eF_e(t)+\epsilon,
\]

where:

- \(\alpha_e\) is the mechanical/profile intercept;
- `Skill_e` is fast task-specific learning;
- \(G_m=\log Development_m\) is the slow muscle-general latent;
- \(F_e\) is a recruitment-weighted transient fatigue state;
- \(\gamma_e\) absorbs profile-specific scale.

This equation is a **reasonable model architecture, not a discovered physiological law**. Its value is identifiability discipline: improvement confined to one unfamiliar exercise can mostly update `Skill_e`; persistent improvement across multiple profiles sharing the same muscle segments increasingly supports \(G_m\).

For a new profile:

\[
\alpha_{new}\sim
N(x_{new}^{T}\beta_{user},\,\sigma^2_{new}).
\]

The user's existing profiles train \(\beta_{user}\). Similar existing profiles can supply kernel-weighted residual information:

\[
\mu_{new}=x_{new}^{T}\beta+
\frac{\sum_i k(x_{new},x_i)(\alpha_i-x_i^{T}\beta)}
{\lambda+\sum_i k(x_{new},x_i)} .
\]

Predictive variance should increase when profile similarity is low, recruitment provenance is weak, equipment is uncalibrated, metric families differ, or user observations are sparse. The strong task specificity of strength adaptation is precisely why this residual uncertainty must remain large for superficially similar exercises. citeturn22search0

This architecture allows **partial pooling** without copying kilograms. A dumbbell incline press and machine chest press can share evidence through pectoral/triceps recruitment and pressing mechanics while retaining different \(\alpha_e\), load–rep slopes, resistance curves and skill states. A barbell bench 80 kg capability therefore does not imply an 80 kg chest-press prescription.

For cold start, use the following hierarchy:

| Evidence available | Behaviour |
|---|---|
| No history on profile, no close neighbour | Leave numeric load blank |
| No profile history, one biologically similar neighbour but uncalibrated equipment | Very broad prior; usually blank |
| Several related profiles, similar metric/mechanics | Produce posterior candidate only if uncertainty threshold passes |
| One new-profile session | Strong update to profile intercept, slope still broad |
| Several sessions across one rep band | Intercept useful; avoid extrapolating far in reps |
| Several sessions across two or more load/rep zones | Personalised slope becomes useful |
| Repeated multi-profile history | User translation coefficients and muscle-general state become increasingly identifiable |

The exact blank threshold is a **product-risk rule, not physiology**. A defensible initial rule is to emit a numeric cross-profile prescription only when the 90% posterior predictive interval is narrower than both a practical relative bound and a small number of equipment increments—for example, no wider than roughly 35% of the median and no more than two increments. That threshold should be versioned and validated, not presented as scientific fact. Otherwise leave `prescribedLoad = null`, exactly as the present conservative model already does for absent evidence. fileciteturn5file0L2-L2

Bayesian updating naturally solves the requested “how fast should translation coefficients update?” question more cleanly than an arbitrary fixed moving average. Give a new profile only a small prior pseudo-sample, let the first few **independent sessions** move its posterior substantially, and count multiple near-identical sets within one session as correlated rather than five fully independent observations. Thereafter, posterior variance and robust observation noise reduce the influence of each additional session. A modest process-noise term permits genuine drift.

The 45-second dead hang → 20 kg unilateral grip-hold example illustrates why cross-family translation should transfer **biological intent rather than units**. A dead hang demonstrates grip/endurance capability within a bilateral hanging chain containing shoulder/scapular demands; it does not reveal the force borne by each hand with enough precision to solve an exact unilateral kg-duration equation. Handgrip endurance itself varies markedly with relative force level. citeturn23search3

Therefore:

```text
45 s dead hang
   ↓
high-confidence evidence: this dead-hang profile's duration capability
   ↓
moderate evidence: forearm/grip exposure exists
   ↓
weak related evidence: new unilateral loaded grip-hold profile
   ↓
preserve target muscle + intended demanding-hold stimulus
   ↓
load-duration coefficient initially broad
   ↓
numeric kg prescription = NULL if uncertainty threshold fails
   ↓
first performed loaded hold calibrates new profile rapidly
```

That is not a failure of the model; it is exactly what an honest model should output.

**Reference morphology should remain a prior.** The repository already explicitly treats adult-male morphology as reference data rather than observed user anatomy. fileciteturn3file0L2-L2

Anatomical studies support the relevance of physiological cross-sectional area, fibre length and architecture to muscle force/excursion capacity, but turning those data into subject-specific force is highly uncertain. Ward and colleagues characterised fibre length and PCSA as important functional architecture properties, while a recent systematic review found reported in-vivo human muscle specific-tension estimates spanning roughly 2–73 N/cm² because activation, moment arms, tendon mechanics and muscle-force attribution are difficult to estimate. New mechanical modelling also indicates that force capacity does not scale perfectly with PCSA alone. citeturn14search1turn14search6turn13search3

Accordingly, reference morphology may defensibly influence:

- broad cold-start **priors** about relative structural potential;
- expected movement/excursion roles from fibre length;
- uncertainty on biomechanical recruitment allocations;
- perhaps the prior covariance between profiles sharing a segment.

It should **not** determine a user's predicted kilograms, development percentage, recovery speed, hypertrophy dose requirement or learning rate. There is no defensible basis for saying that a reference muscle with twice the PCSA requires twice the training dose, nor that the user's PCSA equals the database specimen value. Those relationships should remain unimplemented.

## Dose, fatigue, recovery and long-term development

Resistance-training volume has one of the stronger evidence bases in this problem: meta-analysis supports a graded relationship between weekly set volume and hypertrophy, and multiple-set training generally outperforms very low volume. What the literature does **not** provide is a validated equation converting every set, load and repetition into an exact percentage of new muscle. citeturn20search0turn21search14

N-BIO should consequently distinguish three objects:

\[
Exposure_{s,m}
\rightarrow
EffectiveDose_{s,m}
\rightarrow
SessionDose_{j,m}.
\]

`Exposure` is essentially the existing recruitment-weighted set unit and remains directly recomputable. `EffectiveDose` incorporates uncertain set demand. `SessionDose` is where any diminishing marginal return should be applied.

A simple concave aggregation candidate is:

\[
X_{j,m}=\sum_{s\in j}EffectiveDose_{s,m}
\]

\[
SessionDose_{j,m}
=
\tau_m\ln\left(1+\frac{X_{j,m}}{\tau_m}\right).
\]

For low \(X\), this is approximately linear; marginal gain then decreases progressively without declaring a hard maximum number of productive sets. **The logarithmic form and \(\tau_m\) are modelling assumptions, not experimentally established physiology.** Evidence supports the direction “more sets generally help, with plausible diminishing marginal benefit”; it does not identify this curve. For v1, preserve both `rawDose = X` and `concaveDose` so future inference versions can reinterpret the same history. citeturn20search0turn21search2

A useful precaution is to make \(\tau\) initially global or broadly pooled rather than pretending that N-BIO knows a precise “maximum recoverable volume” for each of 164 segments. User-specific muscle-by-muscle saturation parameters would be hopelessly underidentified for most users.

**Recent stimulus and fatigue should be distinct temporal states.** Failure-oriented resistance sessions can depress mechanical performance for approximately 24–48 hours more than comparable non-failure work, and proximity to failure strongly affects immediate velocity loss. Those observations support a transient fatigue state, but they do not demonstrate one universal 48-hour recovery clock for every muscle and exercise. citeturn23search0turn23search1turn23search12turn23search15

A computationally convenient state model is:

\[
RecentStimulus_m(t)
=
\sum_j SessionDose_{j,m}
\exp\left[
-\ln2\frac{t-t_j}{h_{S,m}}
\right]
\]

and separately:

\[
Fatigue_m(t)
=
\sum_j FatigueImpulse_{j,m}
\exp\left[
-\ln2\frac{t-t_j}{h_{F,m}}
\right].
\]

These exponential kernels should be described in N-BIO as **state-estimation kernels**, not literal molecular kinetics. Their half-lives \(h_S\) and \(h_F\) are learnable/model-version parameters. Acute recovery research gives useful scale information for \(h_F\), but not precise muscle-specific constants. citeturn23search0turn23search1

An initial fatigue impulse could be:

\[
FatigueImpulse_{j,m}
=
X_{j,m}
\left[
1+\lambda_q\bar q_j+
\lambda_d Density_j
\right],
\]

where `Density` represents short rest/high set density and \(\lambda\) coefficients have conservative priors. If demand evidence is weak, the resulting fatigue posterior must widen rather than assuming the set was near failure.

Then:

\[
Recovery_m(t)=
\operatorname{logistic}
\left(
\eta_0-\eta_F Fatigue_m(t)
\right).
\]

Again, `Recovery = 0.82` means the model's bounded readiness index/posterior, **not “82% of muscle tissue is biologically repaired”**.

The best way to personalise fatigue decay is through **future performance residuals**. Suppose the capability model expected 80 kg × 6 but the user performs materially below that across several profiles recruiting the same segment one day after a high-dose session and returns to baseline two days later. That pattern supplies evidence for the user's fatigue response. Because performance is noisy and task-specific, repeated patterns are required.

Sleep, resting HR and other Health Connect context may eventually explain part of those residuals, but they should be covariates with uncertainty rather than deterministic recovery multipliers. A poor night's sleep should not mathematically set “pectoralis recovery = 63%”. Health-data associations are population-level and noisy, while N-BIO's task is individual state estimation.

**Development should evolve much more slowly than capability.** The strength literature makes this essential: task-specific neural/technical improvements can be rapid, while strength and muscle size do not move one-for-one. Detraining data likewise show that resistance-training strength is not normally lost on a day-scale; in one 31-week study, 1RM losses were about 8% in younger and 14% in older adults, occurring primarily later in detraining. citeturn21search5turn23search7turn23search11

The long-term model should therefore decompose:

\[
\Delta Capability_e
=
\Delta Skill_e
+
\sum_m w_{e,m}\Delta Development_m
-
\Delta Fatigue_e
+
noise.
\]

A possible slow state transition is:

\[
g_m(t+\Delta t)
=
\rho_D(\Delta t)g_m(t)
+
\eta_m\,RecentStimulus_m(t)\Delta t
+
\omega_m,
\]

with:

\[
Development_m=\exp(g_m).
\]

This is an implementable state-space prior, **not a hypertrophy equation**. \(\eta_m\) should start close to zero with substantial uncertainty and be updated only where longitudinal data support persistent general improvement. A new lifter whose squat rises rapidly should initially update `Skill_squat` much more readily than `Development_quadriceps`.

Identifiability improves when multiple exercises constrain the same latent. If squat, leg press and knee extension all show persistent capability increases over weeks while acute fatigue is controlled, the posterior evidence for a quadriceps-general component becomes stronger. If only squat improves after the user has just learnt to squat, the model should mainly update squat skill.

This implies an important v1 product rule: **development may exist computationally before it becomes prescription-driving**. During early/sparse history its posterior should remain centred near 1 with a wide credible interval. That is a principled continuation of the current neutral prior rather than an abrupt switch to fake muscle-growth percentages.

Do not infer segmental `volumeScale` from strength. Do not infer it from Health Connect whole-body lean mass either. Segmental morphology requires segmental observation; whole-body body-composition records cannot tell N-BIO that the right lateral triceps head increased 4%.

Likewise, acute muscle-protein-synthesis responses should not be encoded as a growth equation. Early post-exercise MPS responses in untrained states do not straightforwardly predict later hypertrophy, and the relationship changes with training status. citeturn23search9

The resulting prescription logic should operate in this order:

```text
programme target
    ↓
candidate execution profiles
    ↓
target coverage from recruitment model
    ↓
current profile capability posterior
    ↓
current muscle fatigue/recovery posterior
    ↓
long-term development only when sufficiently identified
    ↓
predicted outcome distribution for load × reps / load × time
    ↓
equipment quantisation
    ↓
uncertainty gate
    ↓
prescription OR null
```

The key behavioural rule is asymmetric: **capability may change quickly; development should resist quick changes.** That single design choice prevents the common error of turning four weeks of beginner skill acquisition into four weeks of invented muscle growth.

## Uncertainty, cold start and validation behaviour

Every inference output should contain more than `value + confidence`. At minimum:

```text
estimateMedian
credibleLower05
credibleUpper95
posteriorVariance
observationCount
effectiveIndependentSessionCount
firstEvidenceAt
lastEvidenceAt
evidenceFamily
provenance
modelVersion
referenceProfileVersion
```

`effectiveIndependentSessionCount` matters because five sets performed in twelve minutes are not five independent measurements of the user's long-term physiological state. Their errors share sleep, motivation, equipment, warm-up, fatigue and technique conditions.

For high-dimensional muscle state, covariance also matters. If a bench-press observation updates pectoralis major, anterior deltoid and triceps together, N-BIO cannot honestly know from that exercise alone which component changed. The posterior should retain correlated uncertainty or approximate it by hierarchical factor uncertainty rather than independently awarding all three muscles the same “development gain”.

**Cold-start rules should be conservative.**

A first same-profile set can provide a strong lower-bound performance anchor but little information about the user's load–rep slope or submaximality. After repeated sessions over distinct loads/repetition zones, the profile curve can become personalised. Cross-profile translation should begin broader still. The first observations on a new profile should update the profile-specific intercept/skill rapidly, while muscle-general development remains pooled and slow.

No fixed evidence count is physiological truth, but an implementable product policy is:

| Stage | Suggested model behaviour |
|---|---|
| First observation | Store demonstrated performance; broad slope and effort uncertainty |
| Two or three independent sessions | Useful local prediction around observed rep/time range |
| Several sessions spanning different loads/reps | Personalised curve starts replacing population prior |
| New related profile | Hierarchical prior + wide predictive interval |
| Several weeks + multiple overlapping profiles | Begin identifying general muscle-development factors |
| Long absence | Capability uncertainty grows; development decays only slowly |
| Conflicting single session | Increase noise/fatigue probability before structural regression |

Bayesian posterior updating rather than hard thresholds should do most of the work; the stages above are UX/exposure rules.

The following test vectors should become automated inference tests. Exact posterior numbers will depend on model version; the invariants are more important.

| Scenario | Evidence | Expected N-BIO behaviour | Behaviour that should fail the test |
|---|---|---|---|
| Load × reps progression | Bench: `60×8`, then `62.5×8`, then `65×8` over three sessions | Same-profile capability intercept rises; uncertainty falls. If all sets sit similarly near the learned frontier, muscle exposure/dose stays broadly comparable rather than rising 8% merely because kg rose. Development moves only slightly unless corroborated elsewhere. | `development +8.3%` because load rose 8.3%; stimulus proportional to kg |
| Assistance × reps | 75 kg BW: assisted pull-up `30 kg assistance×8` → `20 kg×8` | Profile resistance coordinate rises; capability updates positively. Assistance has negative sign. | Treating 20 kg as “less load” and marking regression |
| Bodyweight × reps | 70 kg, push-up 15 → 20 reps | Rep capability rises. Bodyweight coefficient remains profile-specific. | Converting 70 kg directly to 70 kg bench load |
| Bodyweight change | 70 kg×15 push-ups → 80 kg×15 | Evidence favours increased capability if bodyweight meaningfully loads profile; magnitude remains uncertain through \(k_{bw}\). | Ignoring body mass or applying a universal BW fraction |
| Duration-only hold | Dead hang 45 s → 55 s | Duration capability rises; grip/scapular exposure remains profile-specific. | Turning +10 s into “+10 reps” or kg-equivalent |
| Load × duration | Left grip hold `20 kg×30 s` → `22.5 kg×30 s` | Load-duration intercept rises; slope remains broad unless multiple durations are observed. | e1RM formula applied to hold |
| Unilateral asymmetry | Left `18 kg×30 s`; right `22 kg×30 s` | Left/right capability states diverge with partial pooling. Recruitment remains anatomically same profile, side-addressed. | Averaging immediately to bilateral 20 kg and deleting asymmetry |
| Related resistance swap | Incline dumbbell press history → unfamiliar chest-press machine | Recruitment/mechanical similarity creates prior; machine-specific intercept and resistance curve create large translation variance; no kg copy. | Prescribing identical kilograms |
| Measurement-family swap | 45 s dead hang → unilateral loaded grip hold | Carry target muscle/biological intent. Numerical kg remains null until posterior narrows. | `45 s ≡ 20 kg` conversion |
| Temporary regression | Normal `80×5`; one poor day `75×5` | Student-t residual prevents large capability drop; fatigue probability/uncertainty rises. Repeated later poor sessions eventually move capability. | Immediate structural development regression |
| Beginner skill gain | `40×8 → 55×8` over four weeks | Large exercise capability/skill increase; general muscle development only modest/broad unless corroborated across profiles. | 37.5% load gain interpreted as 37.5% muscle development |
| High set count | 10 similar working sets same muscle | Raw exposure approximately additive; concave session-dose estimate shows smaller marginal contribution late in session, with uncertainty. | Every set awarded identical certain hypertrophic benefit indefinitely |
| Long detraining | No relevant training for three weeks, then months | Fatigue disappears rapidly; development changes little over the first short absence and gradually becomes uncertain/decays over much longer horizons. | Muscle development falling sharply after 48–72 h |

The poor-session test is especially important. Failure-oriented sessions and close proximity to failure produce measurable short-term performance suppression, while detraining operates on a far slower time scale. Those two phenomena should never share one “muscle state” variable. citeturn23search0turn23search1turn23search7

The beginner test is equally important because high-load/dynamic strength is markedly specific to the trained task. The correct interpretation of rapid progression is “the user is becoming much more capable at this execution profile”; attribution between technique/neural adaptation and muscle-general development should remain uncertain. citeturn20search3turn21search5turn22search0

A useful validation methodology is **retrospective prediction**, not merely checking whether internal state looks plausible. For every historical session \(k\), recompute the model using evidence only through \(k-1\), predict session \(k\), and record calibration:

\[
Calibration(p)=
P(
Y_{observed}
\le Q_p(Y_{predicted})
).
\]

A nominal 90% predictive interval should contain approximately 90% of compatible future observations over sufficient data. If it contains only 55%, the model is overconfident even if its point predictions look attractive. Compare vNext against the current simple same-profile anchor; sophistication is justified only if it improves held-out predictive accuracy or uncertainty calibration.

Useful validation metrics are MAE in kg/reps/seconds within each measurement family, log predictive density, interval coverage, calibration error and “blank appropriately” rate for cold-start translations. Do **not** aggregate kg error and seconds error into one global score.

## Android Health Connect and Gemini Nano findings for August 2026

As of **24 August 2026**, the AndroidX versions index lists `androidx.health.connect:connect-client` **1.1.0 as stable** and **1.2.0-alpha05 as the current alpha**, dated 12 August 2026. Google's English Health Connect release-notes page was lagging at `1.2.0-alpha04` when indexed, while the current AndroidX versions page and API reference expose the alpha05 surface. Production code should therefore distinguish stable 1.1 functionality from 1.2-alpha functionality instead of assuming every currently documented property is stable. citeturn16search4turn17view0turn15search0

The Health Connect record surface relevant to My Mettle includes `ExerciseSessionRecord`, `PlannedExerciseSessionRecord`, `WeightRecord`, `HeightRecord`, `BodyFatRecord`, `LeanBodyMassRecord`, `HeartRateRecord`, `RestingHeartRateRecord`, `HeartRateVariabilityRmssdRecord`, `SleepSessionRecord`, `StepsRecord`, `StepsCadenceRecord`, `DistanceRecord`, `SpeedRecord`, `PowerRecord`, `CyclingPedalingCadenceRecord`, `ElevationGainedRecord`, `FloorsClimbedRecord`, `ActivityIntensityRecord`, calorie records and `Vo2MaxRecord`. citeturn12search12

A sensible N-BIO mapping is:

| Health Connect record | My Mettle use |
|---|---|
| `ExerciseSessionRecord` | External session context / Native workout export |
| `PlannedExerciseSessionRecord` | Optional programme/prescription export |
| `WeightRecord` | Bodyweight context for bodyweight/assisted profiles |
| `HeightRecord` | Static user context; little direct N-BIO inference value |
| `BodyFatRecord`, `LeanBodyMassRecord` | Whole-body context only; **not segment morphology** |
| `HeartRateRecord` | Conditioning/session context |
| `RestingHeartRateRecord` | Recovery/context covariate, not deterministic readiness |
| `HeartRateVariabilityRmssdRecord` | Optional recovery/context covariate |
| `SleepSessionRecord` | Sleep duration/stages context |
| `StepsRecord`, `DistanceRecord` | Ambient/activity dose and conditioning context |
| `SpeedRecord` | Running/cycling performance |
| `PowerRecord` | Particularly useful for cycle/row conditioning capability |
| `CyclingPedalingCadenceRecord`, `StepsCadenceRecord` | Modality context |
| `ElevationGainedRecord`, `FloorsClimbedRecord` | Vertical activity context |
| `Vo2MaxRecord` | Conditioning reference; source/method dependent |

Health Connect supports foreground reads normally. Background reads require the additional `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` permission and runtime feature availability. Cumulative data such as steps should generally use Health Connect aggregation rather than naïvely summing overlapping records from multiple origins. citeturn18search0

Historical access is separately controlled. Without `PERMISSION_READ_HEALTH_DATA_HISTORY`, normal reads are restricted to the default historical window—data older than roughly 30 days before the app's first permission grant is unavailable for external history—while own-app data has different behaviour on Android 14+. The history permission extends access and also applies to changes/aggregation APIs. citeturn18search0turn18search7

For safe mirroring, Health Connect's own sync guidance fits My Mettle's architecture well. Records expose a Health Connect-assigned `Metadata.id`, `lastModifiedTime`, `dataOrigin`, optional app-defined `clientRecordId`, and `clientRecordVersion`. For a given client and record type, repeated inserts using the same client ID resolve according to record version, with the highest version taking precedence. citeturn18search1turn18search2turn18search3

For **My Mettle-originated records**, use:

```text
clientRecordId      = stable Native UUID
clientRecordVersion = monotonic Native export revision
HealthConnect id    = persisted after insertion
dataOrigin          = Health Connect populated package origin
```

This makes repeat exports idempotent rather than duplicate-producing. Health Connect explicitly recommends this client-ID/version upsert pattern. citeturn18search1turn18search2

For **external records imported into My Mettle**, mirror:

```text
recordType
healthConnectId
dataOrigin.packageName
lastModifiedTime
payload / canonical value
importedAt
```

Do not synthesise a new immutable Native workout set from a generic external record unless its semantics are sufficient and provenance is retained.

Use separate Changes tokens for independently consumed record types. Health Connect recommends this because permission revocation on one data type should not break synchronisation of unrelated types. `UpsertionChange` supplies the changed record; `DeletionChange` supplies only its ID, so the mirror needs enough stored identity to process deletions. Unused Changes tokens expire after 30 days; on expiry, re-read and deduplicate rather than blindly appending. citeturn18search1

The deletion policy needs one My Mettle-specific refinement. The Native immutable workout store remains My Mettle's source of truth, while Health Connect is an integration surface. Therefore:

```text
External HC record deleted
    → remove/expire its local imported mirror
    → recompute derived contextual state if necessary

Native workout exported to HC, later deleted from HC
    → DO NOT erase Native immutable workout evidence
    → mark HC export as deleted/suppressed
    → do not silently recreate it against the user's HC deletion

User deletes workout inside My Mettle
    → apply My Mettle's own deletion semantics
    → delete corresponding HC export where appropriate
```

That respects both N-BIO recomputability and Health Connect user intent.

Health Connect's current workout representation has become substantially richer. `ExerciseSessionRecord` contains segments, laps, title/notes and optional linkage to a planned session. Stable training-plan APIs allow planned exercise steps with completion goals and performance targets including weight, power, speed, cadence and heart-rate targets; `ExercisePerformanceTarget.WeightTarget` explicitly carries a `Mass`. citeturn12search0turn12search1turn12search9turn15search2

As of the current **1.2.0-alpha05** API, `ExerciseSegment` also exposes optional actual `weight`, `setIndex` and RPE in addition to repetition count and segment timing. These richer completed-set fields are explicitly marked as alpha05 additions in the API reference. My Mettle can therefore map much more of a strength session back to Health Connect when using that alpha surface, but production should feature/version-gate it; stable 1.1 should not be treated as though these alpha fields were guaranteed. citeturn15search0turn15search1

N-BIO's decision not to collect subjective RIR remains unaffected. Health Connect's new optional RPE field need not be populated, and an absent field must never be fabricated.

For duplicate resistance-workout export, preserve the Native workout ID as `clientRecordId`; use the same client identity for subsequent corrections; avoid exporting multiple overlapping copies of the same session. Health Connect's workout guidance supports writing session records and associated workout metrics while its general synchronisation contract supplies the idempotent update mechanism. citeturn12search13turn18search1

**Gemini Nano / ML Kit is suitable for the post-exercise note interpreter, but it should remain completely outside N-BIO biological inference.** Google's current ML Kit GenAI APIs run through AICore and Gemini Nano on-device; input, inference and output remain local. The flexible Prompt API accepts custom text prompts and can produce structured output. As of July 2026 the Prompt API is explicitly **beta** and has no SLA/backwards-compatibility guarantee. citeturn19search0turn19search1

Structured Output allows the target schema to be represented through Kotlin classes/annotations and the complete structured request can be token-counted before inference, making this significantly safer than parsing unconstrained prose with regex. It is a good fit for “extract tags and a next-session reminder from a short note”. citeturn19search2turn19search10

Device support remains conditional. Google's current list includes Pixel 9/10 families and a growing set of recent Samsung, OnePlus, Oppo, Vivo, Xiaomi and other devices, with different devices running Nano v2 or v3. The runtime API can report model/feature availability, and different Nano versions can produce different outputs for the same prompt. AICore also enforces per-app quota. citeturn19search0turn19search6

Most importantly for application design, GenAI inference is currently allowed only while the app is the **top foreground application**; attempting inference from the background, even through a foreground service, can produce `BACKGROUND_USE_BLOCKED`. Therefore note interpretation should occur interactively after the user submits the note, not in a deferred WorkManager inference job. citeturn19search0turn19search9

The required privacy boundary should be structural rather than prompt-based:

```text
HealthConnectRepository ──────X──────► NoteInterpreter

WorkoutEvidenceRepository ────X──────► NoteInterpreter
          unless an individual field is explicitly allow-listed

PostExerciseNote UI
        │
        ▼
NoteInterpreterInput {
    noteText
    exerciseDisplayName?          // static app-owned label only
    executionProfileDisplayName?  // static app-owned label only
}
        │
        ▼
ML Kit Prompt API / Gemini Nano
        │
        ▼
Structured NoteInterpretation {
    techniqueTags[]
    equipmentTags[]
    discomfortTags[]
    reminderTags[]
    reminderText?
}
        │
        ▼
schema validation
        │
        ▼
user-visible suggestion / persisted note metadata
```

**No Health Connect record, aggregate, sleep value, bodyweight, heart rate, HRV, body composition or source metadata should even be representable by `NoteInterpreterInput`.** A separate Gradle/domain module with no dependency on the Health Connect DTO package would make accidental leakage much harder than a runtime instruction saying “do not include health data”.

Likewise, Gemini output should never mutate immutable workout evidence, recruitment coefficients, muscle state or prescriptions directly. It can classify a user's own note into an allow-listed vocabulary and create a reminder candidate; deterministic application code validates the schema and decides what is stored. LLM self-reported “confidence” should not be used as N-BIO statistical confidence.

## Evidence ledger and relationships not yet fit to implement

The table below separates evidence for the *direction of a relationship* from confidence in an exact computational form.

| Major assumption / relationship | Evidence quality | v1 decision | Basis |
|---|---|---|---|
| Exercise performance and muscle development require separate latents | **Strong** | Implement | Strength is highly task-specific and has neural/technical components. citeturn21search5turn22search0 |
| One universal skeletal-muscle load scalar | **Evidence against** | Reject | Dynamic, isometric and endurance performance have different response surfaces and units. citeturn22search0turn23search3turn22search8 |
| Higher loads produce greater 1RM-specific strength adaptation | **Strong** | Encode through capability specificity, not stimulus multiplier | Multiple meta-analyses. citeturn20search2turn20search3turn20search11 |
| Hypertrophy can occur across broad load ranges | **Strong/moderate** | No fixed rep-range hypertrophy multiplier | Reviews find broadly similar hypertrophy across loads under demanding protocols. citeturn20search2turn20search3 |
| Failure is required for hypertrophy | **Evidence against** | Do not encode | Meta-analyses show failure is not required. citeturn20search1turn20search5 |
| Proximity to failure affects stimulus/fatigue | **Moderate** | Latent demand variable; wide uncertainty without objective effort evidence | Failure/proximity changes fatigue; hypertrophy relation exists but exact dose curve is uncertain. citeturn20search7turn23search1 |
| Completed load+reps uniquely identifies RIR | **False / unidentifiable** | Reject | Even explicit RIR/failure prediction is noisy; completed set alone is only a lower-bound capability observation. citeturn20search12turn13search6 |
| User-specific load–rep relationship is preferable to a universal equation | **Moderate/strong** | Implement personalised profile curves | Individual relationships outperform generalised relations in several studies. citeturn13search2turn13search12 |
| Generic e1RM formula is universally accurate | **Evidence against** | Secondary estimate only | Accuracy varies by exercise, repetitions and method. citeturn13search0turn13search7turn13search8 |
| More weekly sets generally support greater hypertrophy | **Strong** | Accumulate muscle dose | Dose-response meta-analysis. citeturn20search0 |
| Exact marginal-stimulus curve versus set number | **Weak/unknown** | Use only a soft, versioned concave model with uncertainty | Direction better supported than mathematical form. citeturn20search0turn21search2 |
| Frequency independently determines hypertrophy | **Weak once volume matched** | Let temporal dose/recovery handle frequency | Volume-equated meta-analysis finds little meaningful effect. citeturn21search6 |
| Full ROM/long-length work modifies adaptation | **Moderate, evolving** | Execution-profile feature; no precise multiplier | Full ROM often favoured; newer long-length evidence is more nuanced. citeturn21search17turn21search4 |
| Time-under-tension proportional to hypertrophy | **Unsupported** | Do not implement | Similar hypertrophy across broad normal repetition durations. citeturn21search0 |
| Rest interval has a universal hypertrophy multiplier | **Weak** | Context/fatigue variable only | Both short and long rests work; evidence sparse. citeturn21search3 |
| EMG amplitude directly supplies recruitment percentage | **False** | Reject | EMG–force relation varies with joint mechanics and activation context. citeturn22search4turn22search6turn22search7 |
| Recruitment coefficients must sum to 1 | **No physiological basis** | Reject; independent `[0,1]` coefficients | Concurrent muscle action is not a conserved allocation pool; this is a modelling convention informed by biomechanics. |
| Stabiliser means negligible stimulus | **Unsupported** | Allow non-zero/high weighting where mechanically justified | Stabilising tasks can create substantial sustained activation/loading; role and weighting remain separate. |
| Isometric load–duration capability can be modelled | **Moderate** | Implement separate monotonic curve | Isometric endurance strongly depends on relative force and adaptation is task-specific. citeturn23search3turn22search2 |
| Seconds can be converted directly to repetitions | **Evidence against** | Reject | Dynamic and isometric domains show limited transfer and different performance laws. citeturn22search0turn23search3 |
| Critical power/speed are useful endurance capability models | **Strong within suitable modalities** | Implement in conditioning layer where power/speed evidence supports it | Established power-duration framework and training literature. citeturn22search8turn22search12 |
| Cardio can be converted to hypertrophy set units | **Unknown/unsupported** | Reject | Concurrent effects are modality/outcome dependent, not a fixed conversion. citeturn21search9turn21search16 |
| Acute fatigue is distinct from long-term detraining | **Strong** | Separate states/time constants | Failure fatigue commonly resolves around day scale; detraining losses emerge over weeks/months. citeturn23search0turn23search7 |
| Exponential fatigue/recent-dose decay is physiological truth | **Weak** | Use as transparent state-estimation kernel only | Convenient model form; exact kinetics unvalidated |
| Strength improvement equals hypertrophy | **False** | Reject | Neural/task-specific and muscle-size contributions coexist. citeturn21search5turn22search0 |
| Multi-profile shared factor can help isolate general muscle development | **Reasonable modelling assumption** | Implement cautiously with strong shrinkage | Supported conceptually by task specificity; exact factor model requires N-BIO validation. citeturn22search0 |
| Reference PCSA predicts this user's kilograms | **Weak/unsupported** | Reject | Human specific tension estimates vary widely and architecture is not user anatomy. citeturn14search6turn13search3 |
| Reference morphology can inform broad priors | **Reasonable** | Retain as prior/provenance only | Architecture relates to force/excursion capacity. citeturn14search1 |
| One bad session means permanent regression | **Unsupported** | Robust observation + fatigue first | Acute fatigue/noise can suppress performance without detraining. citeturn23search0turn23search1 |
| Bayesian cross-exercise feature translation | **Reasonable modelling assumption** | Implement and validate prospectively | Not an established physiological equation; designed to expose task-specific uncertainty |
| A numeric cold-start load must always be supplied | **No** | Leave blank above uncertainty threshold | Product safety/epistemic rule; aligns existing conservative architecture. fileciteturn5file0L2-L2 |

**Do not implement yet** the following relationships:

1. **A universal hypertrophy formula such as `load × reps × sets × recruitment = muscle growth`.** External volume-load is not a direct measure of fibre tension or hypertrophy, and broad load ranges can produce comparable hypertrophy while producing different strength adaptation. citeturn20search2turn20search3

2. **A deterministic RIR estimator from load and reps alone.** The model may infer a broad latent submaximality distribution from longitudinal behaviour, but an integer such as “2.3 RIR” would manufacture information N-BIO did not observe. citeturn20search12turn13search6

3. **A fixed “effective reps” rule.** Evidence that proximity to failure matters does not establish a universal last-five-repetitions law applicable across muscles, loads, execution profiles and users. citeturn20search7turn20search5

4. **Time-under-tension as a linear stimulus term.** Normal repetition-duration differences do not support it. citeturn21search0

5. **Metabolic stress, pump, lactate or muscle damage as separate additive hypertrophy currencies.** These are not required for a useful v1 and their inclusion would add far more apparent mechanistic precision than predictive information.

6. **Fixed per-role stimulus attenuation such as `stabiliser = 0.25 prime mover`.** Encode expected local exposure in the recruitment weight and retain role separately.

7. **EMG-derived recruitment percentages.** EMG is useful provenance but cannot uniquely establish force or hypertrophic contribution. citeturn22search4turn22search6

8. **Reference-PCSA → user-force conversion or morphology-driven dose requirements.** The existing reference morphology should remain exactly what the repository intends: a reference prior, not measured user anatomy. fileciteturn3file0L2-L2 citeturn14search6

9. **A fixed 24/48/72-hour muscle-recovery schedule.** Acute recovery research supplies useful priors but not a universal muscle clock. citeturn23search0turn23search1

10. **Cardio minutes, watts, kilometres, steps or calories converted into resistance-training set units.** Share the evidence substrate and fatigue context, not the adaptation unit. citeturn22search8turn21search9

11. **Exact conversion between dead hangs and unilateral loaded grip holds, or between unrelated equipment implementations.** Similarity should influence a prior and its variance, never substitute for calibration.

12. **Development inferred from a single exercise's strength progression.** Until multiple overlapping profiles or other longitudinal evidence can separate general adaptation from profile skill, keep development strongly shrunk towards its neutral prior. citeturn22search0turn21search5

13. **Segment-specific muscle volume inferred from Health Connect lean mass/body fat.** Those records describe whole-body composition, not N-BIO's anatomical segments. citeturn12search12

14. **Gemini Nano as part of biological inference.** It should parse an allow-listed user note into constrained metadata only. Health Connect data must never enter that input path. Prompt API availability is device- and foreground-dependent and the API remains beta. citeturn19search0turn19search1turn19search9

The resulting v1 is intentionally less dramatic than a single grand “biological load” formula, but considerably more useful. It converts N-BIO from a recruitment-weighted bookkeeping scaffold into a **recomputable probabilistic inference system**: profile-specific demonstrated capability learns quickly; unknown effort remains explicitly latent; muscle exposure remains traceable to recruitment evidence; dose and fatigue become separate temporal states; exercise skill absorbs fast neural/technical progress; muscle development moves slowly and only when shared evidence supports it; isometric and conditioning profiles retain their natural measurement families; cross-exercise translation uses biological/mechanical similarity without copying units; and prescriptions remain blank whenever the posterior knows too little. That architecture is consistent both with the strongest available training evidence and with the conservative provenance boundaries already present in My Mettle.
