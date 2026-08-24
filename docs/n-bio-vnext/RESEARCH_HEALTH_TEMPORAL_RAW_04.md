
```text
capability on device/profile X at local setting 8
```

rather than an SI-normalised stair capability.

#### Ellipticals and other machines

The same principle applies more strongly. A resistance number without a calibrated physical meaning is **ordinal/device-local**. Store it; do not invent Newtons, Watts or cross-machine equivalence.

### Why trace evidence materially changes conditioning inference

Consider:

```text
Workout A
20 min
average speed 9 km/h
```

versus:

```text
Workout B
20 min
10 × alternating hard/easy intervals
average speed 9 km/h
```

Scalar averages make these identical. Physiologically and in terms of speed-duration capability, they are not. Endurance interval training at higher intensities can produce different adaptations despite comparable total work, which demonstrates why intensity distribution is real information rather than graphical decoration. citeturn19search12

Thus traces are capability evidence, not merely presentation data.

### Cardio → skeletal-muscle evidence

The neutral conclusion is neither “cardio counts as resistance sets” nor “cardio has no local muscular effect”.

Endurance and resistance exercise produce mode-specific adaptations, but the boundary is not absolute. Reviews of concurrent training describe overlapping early skeletal-muscle responses and later increasing phenotype specificity. Meta-analyses of concurrent training generally find whole-muscle hypertrophy can remain similar to resistance training alone under many conditions, while some analyses find fibre-level interference when HIIT is combined with resistance training. Different meta-analyses have also disagreed over how much running versus cycling moderates interference. citeturn23search9turn23search11turn23search15turn23search1

That evidence supports the following architecture:

```text
cardio external evidence
    ↓
possible modality-specific local muscular exposure
    ↓
uncertain endurance/local-fatigue adaptation context

NOT

cardio minutes
    ↓
equivalent hypertrophy sets
```

Useful future variables include:

```text
modality
power / speed
grade
vertical displacement
body mass
cadence
duration
intensity distribution
recent muscle-specific work
```

but local-muscle attribution should initially be probabilistic and anatomy/modality-specific.

For example, steep uphill running plainly changes lower-limb mechanics and energetic demand, yet incline running research does not justify transforming a particular grade-duration pair directly into a resistance-style quadriceps/glute hypertrophy dose. citeturn15search10turn15search26

**N-BIO recommendation:** preserve cardio traces now; permit future uncertain `LocalEnduranceExposure` or `LocalFatigueContext`; **do not make cardio-derived hypertrophy dose behaviour-driving yet**.

### Cardio fatigue, systemic load and TRIMP

Heart-rate-derived TRIMP methods are established internal training-load metrics and can associate with longitudinal endurance adaptations. A 2025 meta-analysis in team-sport populations reported correlations between HR-based TRIMP and changes in VO₂max and lactate-threshold-related performance, while broader training-load literature distinguishes HR-based internal load from external workload. citeturn23search0turn23search16

TRIMP is therefore **not useless**, but it is also not a universal fatigue currency. HR changes with heat, hydration, fitness, medication, time of day and autonomic state; intermittent/mechanical work can impose substantial neuromuscular load without proportional HR response. Training-load consensus likewise recommends considering multiple internal and external measures rather than treating one metric as training load itself. citeturn19search7turn21search0turn19search10

For N-BIO:

```text
TRIMP-like HR load
    = possible contextual/systemic prior

external power/speed/grade/duration
    = modality-specific physical evidence

future fatigue model
    = personalised combination

calories
    ≠ universal training stress
```

### Trace-derived summary registry

For irregular traces, summaries should generally be **time-weighted**, not unweighted sample means.

| Summary | Definition / required input | Class | Persistence | N-BIO value |
|---|---|---|---|---|
| **Elapsed duration** | `end-start` | Deterministic | Existing scalar/cache | High |
| **Active duration** | elapsed minus explicitly known paused intervals | Deterministic only if pause data exists | Recompute/cache | High |
| **Recorded coverage** | duration represented by valid evidence / requested interval | Deterministic QC | Cache | High |
| **Distance** | preferably source interval totals; otherwise `∫speed dt` labelled derived | Deterministic with assumptions | Cache | High |
| **Time-weighted mean speed** | `Σ(vᵢ Δtᵢ)/ΣΔtᵢ` | Deterministic | Cache | Moderate/high |
| **Time-weighted median speed** | median by temporal occupancy | Deterministic | Recompute/cache | Moderate |
| **Raw peak speed** | `max(v)` | Deterministic but spike-sensitive | Recompute | Mostly UI/QC |
| **Maximum sustained speed** | max rolling time-weighted mean over versioned window, e.g. 30 s | Deterministic parameterised | Versioned cache | High |
| **Mean power** | time-weighted mean `P(t)` | Deterministic | Cache | High |
| **Work** | `∫P dt` in joules | Deterministic | Cache | High |
| **Best sustained power** | max rolling mean at specified duration | Deterministic parameterised | Versioned cache | High |
| **Weighted/normalised power** | definition-specific nonlinear weighting | Model/algorithm-specific | Versioned only | Possibly useful; not canonical |
| **Mean cadence** | time-weighted cadence | Deterministic | Cache | Moderate |
| **Cadence variability** | time-weighted SD/CV with gap rules | Deterministic | Cache | Contextual |
| **Elevation gain** | source intervals preferred; route-derived positive ascent requires filtering | Source deterministic or algorithm-derived | Label provenance | Moderate/high |
| **Floors/steps** | sum non-overlapping intervals from chosen coherent origin | Deterministic under source rule | Cache | Modality-specific |
| **Mean HR** | time-weighted HR over valid coverage | Deterministic | Cache | Contextual |
| **Raw peak HR** | maximum received sample | Deterministic, artefact-sensitive | Recompute | Weak alone |
| **Sustained peak HR** | maximum rolling median/mean over defined short window | Deterministic parameterised | Versioned cache | Better context |
| **Time in HR zones** | integrate time within *versioned personalised* thresholds | Model-derived | Versioned | Conditioning context |
| **HR rise** | peak/set HR − selected pre-set baseline | Derived | Versioned | Experimental resistance context |
| **HR recovery** | e.g. peak minus HR at +30/+60 s or fitted post-set slope | Derived | Versioned | Experimental |
| **Cardiovascular drift** | HR change between matched external-work segments | Model-derived | Versioned | Conditioning/context |
| **Interval detection** | change-point/segmentation algorithm on power/speed | Model-derived | Versioned | High UI/capability value |
| **Trace quality** | gaps, implausible derivatives, sensor spikes, coverage | Deterministic/model QC | Versioned cache | Essential |

HR zones should not become immutable raw evidence. Threshold methods and personal physiology can change; a zone assignment is an interpretation of HR, not the original observation.

## Resistance-training heart-rate evidence and N-BIO state implications

### Overall conclusion

Continuous resistance-training HR is worth **storing now as physiological context**, but it should initially have a much narrower behaviour-driving role than cardio power/speed traces.

The literature strongly supports that resistance exercise evokes structured cardiovascular responses affected by protocol and exercise identity. A half-squat produces different post-exercise HR kinetics from bench press/pulldown/triceps work; workload-matched dynamic and isometric leg press produce different HR/cardiovascular responses; rest interval and set configuration alter cardiovascular stress. citeturn17search5turn17search4turn17search0turn17search20

What is **not** established nearly as strongly is that wrist-derived set-level HR adds reliable out-of-sample prediction of the *next set's repetitions or local muscular fatigue after external work and rest are already known*. That is an excellent N-BIO research hypothesis, not a validated prescription rule.

### Acute exercise-specific HR signatures

Exercise identity matters. In one study, post-exercise HR kinetics were slower after half-squats than after bench press, pulldown or triceps pushdown, and the squat produced greater excess post-exercise oxygen consumption than some upper-body exercises. Dynamic large-muscle-mass leg exercise produced greater HR increases than workload-matched isometric leg exercise. citeturn17search5turn17search4

This makes the example

```text
hack squat
versus
cable curl
```

physiologically plausible as two exercises with different cardiovascular signatures even when both are difficult.

But several important qualifications follow:

* the difference does **not** identify local muscle activation;
* active muscle mass, posture, contraction type and breathing can explain substantial variance;
* the literature does not establish a universal hack-squat-to-curl HR ratio;
* direct evidence that an individual's exact exercise-specific signature is stable over months is limited.

There is, however, evidence from specialised resistance protocols that cardiovascular responses can exhibit repeatable interindividual variation. A 2026 replicate crossover study of low-load blood-flow-restricted resistance exercise found several acute cardiovascular responses were variable between people yet reproducible within people. It is inappropriate to generalise BFR physiology directly to normal lifting, but it supports the broader proposition that person-specific response modelling is experimentally plausible. citeturn17search10

### Resistance-training HR feature registry

Evidence strength below refers specifically to the proposed **N-BIO use**, not whether the physiological quantity itself exists.

| Feature | Definition | Evidence for N-BIO use | Main confounders | Candidate prediction targets | Practical temporal requirement | Boundaries? | Status |
|---|---|---|---|---|---|---|---|
| **Pre-set HR** | Time-weighted HR immediately before set | **Limited–moderate descriptive; weak direct predictive evidence** | prior exercise, rest, posture, stress, heat, caffeine | unusual state; next-set performance | ≲5–10 s sampling useful | **Yes** | Experimental |
| **Set-start HR** | nearest reliable HR around actual set start | Limited | same as above + watch lag | baseline for ΔHR | ≲5 s preferable | **Yes** | Contextual |
| **Set HR rise** | peak/sustained peak − pre-set level | Moderate descriptive; weak predictive | set duration, active muscle mass, breathing, sensor lag | systemic demand/residual strain | ≲5 s | **Yes** | Experimental |
| **Peak HR** | maximum/sustained maximum around set and early recovery | Moderate descriptive | optical artefact, muscle mass, posture | exercise-specific cardiovascular demand | ≲5–10 s | Helpful | Display/context |
| **Time to HR peak** | peak timestamp − set end/start | Limited resistance-specific evidence | sensor lag + real kinetics | morphology/QC | 1–5 s ideal | **Yes** | Experimental |
| **HR AUC above baseline** | `∫max(HR-baseline,0)dt` over versioned window | Modelling hypothesis | window choice, accumulated session load | systemic cost | ≲5 s | **Yes** | Experimental |
| **Early recovery slope** | robust regression of HR against time after set | Moderate physiological basis; weak performance prediction | posture, breathing, movement, next activity | recovery state/systemic strain | 1–5 s preferable | **Yes** | Experimental |
| **HRR30 / HRR60** | peak HR − HR 30/60 s later | HR recovery is established physiology; resistance-set prescription evidence limited | recovery posture, movement, active rest | autonomic/systemic recovery | ≤5–10 s | **Yes** | Contextual/experimental |
| **Pre-next-set HR** | HR just before subsequent set | Strong temporal plausibility; predictive increment unknown | elapsed rest, walking, setup, stress | rep/load/velocity drop | ≤5 s preferable | **Yes** | Experimental |
| **Fractional recovery** | `(peak − preNext)/(peak − preSet)` or versioned equivalent | Hypothesis | baseline instability | next-set performance | ≤5 s | **Yes** | Experimental |
| **Set-to-set HR drift** | change in matched-set HR response | Moderate descriptive plausibility | work changes, order, heat, hydration | cumulative strain | ≤5–10 s | **Yes** | Experimental |
| **Observed − expected HR residual** | actual feature − personal model prediction | **Strong modelling rationale; direct validation absent** | unmeasured state/context | abnormal strain, readiness, later degradation | depends feature | **Yes** | Highest-priority experiment |
| **Session HR burden** | integrated HR-derived features over whole session | Established as internal-load concept; weak specificity | all systemic confounders | session strain | 5–15 s may suffice | No | Contextual |
| **Exercise-specific recovery constant** | fitted personal post-set recovery kinetics | Plausible longitudinal phenotype | posture/rest protocol | personal systemic-cost model | 1–5 s ideal | **Yes** | Experimental |

Heart-rate recovery after conventional exercise reflects autonomic recovery, including vagal reactivation, but clinical/endurance HRR constructs cannot simply be transplanted into a 90-second gym rest interval as a validated set-readiness threshold. citeturn16search22turn16search6

### Sampling resolution and wrist-watch limitations

There is no scientifically established universal “minimum N-BIO resistance HR sample frequency”. The following should be treated as a **MODELLING CHOICE**:

```text
native samples:
    always preserve

median sample interval ≤ 5 s:
    useful for set morphology

5–10 s:
    adequate for coarse pre/peak/recovery features

> 15 s:
    increasingly weak for short-set recovery morphology
```

A one-second or similarly dense source would be preferable, but N-BIO must not demand it from Health Connect.

Set boundaries are far more important than trying to force a perfect sample rate. A 5-second HR stream with accurate set start/end is much more interpretable than a 1-second HR stream whose relationship to sets is unknown.

Optical wrist HR should also be treated probabilistically. Resistance-exercise validation has found device-specific errors and increasing underestimation at some higher intensities, while more recent watches can perform considerably better; PPG itself remains susceptible to motion and contact artefact. citeturn18search2turn18search0turn18search1

Hence:

```text
raw HR sample ≠ guaranteed true beat rate
```

N-BIO should preserve raw watch-derived HR while allowing derived quality flags and robust/sustained features.

### Set-to-set performance deterioration

The strongest established predictor structure currently remains the obvious one:

```text
previous external performance
+ load
+ repetitions
+ set count
+ elapsed rest
+ exercise/profile
```

Rest intervals materially affect repetitions and power maintained over multiple resistance sets, particularly at moderate-to-heavy loads. citeturn17search9

Resistance studies also show shorter rest/set configurations can modify cardiovascular stress, and internal-intensity measures including HR, oxygen uptake, lactate and RPE change systematically with protocol/work rate. A 2023 trial found such internal measures were more strongly related to session work rate than some conventional external “intensity” indices. citeturn16search19turn17search20

But this is **descriptive association**, not evidence that:

```text
high pre-set HR
    causes
rep loss
```

or that HR independently predicts rep loss after elapsed rest and work are accounted for.

Thus the correct N-BIO experiment is precisely the incremental one proposed by the user:

```text
