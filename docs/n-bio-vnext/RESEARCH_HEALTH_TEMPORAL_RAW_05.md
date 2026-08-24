Model A:
performance history

Model B:
performance history + set/rest timing

Model C:
performance history + timing + HR features

Evaluate:
held-out error predicting next-set reps/load/velocity/duration
```

If C consistently beats B within an individual, HR has practical modelling value even if the residual's biological meaning remains ambiguous.

A negative result is equally useful: it would tell N-BIO to keep HR for display/context without using it for prescription.

### Readiness and session-to-session state

Exercise HR has been extensively considered as a training-status marker in endurance/sport monitoring, but the direction of changes with fatigue/overreaching is not reliably one-way. Reviews explicitly describe submaximal HR responses to overreaching as controversial and distinguish resting HR, exercise HR and recovery HR as different monitoring measures. citeturn19search0turn19search1

Therefore neither rule is defensible:

```text
higher-than-usual HR = under-recovered
```

nor:

```text
lower-than-usual HR = fitter
```

At a matched external workload, an unusually high HR may reflect heat, dehydration, psychological arousal, recent work or reduced fitness; a lower response may reflect improved conditioning, but blunted autonomic responsiveness or fatigue can also alter exercise responses. citeturn19search1turn21search0

This is exactly where an **expected-response residual** is preferable:

```text
ExpectedHR(
    exerciseProfile,
    load,
    reps,
    setDuration,
    rest,
    setOrdinal,
    recentSessionWork,
    elapsedSessionTime
)

residual =
    ObservedHRFeature - ExpectedHRFeature
```

Classification:

**Scientifically defensible as a personal anomaly model:** yes.

**Established physiological biomarker of recovery:** no.

**Worth prospective N-BIO testing:** strongly yes.

**Ready to alter training prescription:** no.

### Longitudinal adaptation

Endurance conditioning changes cardiovascular responses and can allow greater work at a given physiological strain; exercise HR at matched submaximal work is therefore partly conditioned by cardiovascular fitness. citeturn19search13turn19search1

For a repeated resistance task such as:

```text
40 kg × 10, same execution and rest
```

a long-term reduction in HR could plausibly arise from:

```text
better general conditioning
better movement economy
technical familiarity / SkillState
different breathing behaviour
different autonomic state
environmental change
measurement change
```

There is insufficient evidence to isolate one cause from the HR change alone.

Accordingly, longitudinal stable-workload HR belongs primarily in **conditioning/systemic-context modelling**, with skill as a competing explanation. It should not directly increment a muscle-development latent.

### Heart rate → N-BIO state classification

| N-BIO question | Direct evidence? | Within-person predictive potential | Recommendation |
|---|---|---|---|
| **Local muscle stimulus** | **Weak/unsupported as direct HR proxy.** HR does not identify mechanical tension or local recruitment. | Unknown; likely little incremental value after detailed external performance for conventional sets | **Do not multiply hypertrophic dose by HR.** |
| **Local muscle fatigue** | Indirect only; HR reflects systemic cardiovascular response rather than local contractile state | Plausible for predicting later performance in the same exercise, but direct incremental evidence is sparse | Experimental only |
| **Systemic strain** | **Moderate descriptive evidence.** Exercise type, work rate, rest and set configuration modify HR/cardiorespiratory load | Good candidate for personal modelling | Contextual now; experimental latent |
| **Recovery/readiness** | Indirect; direction not universal | Moderate broader-sport rationale, resistance-specific evidence weak | Experimental anomaly signal |
| **Cardiovascular conditioning** | HR response at standardised external workload is relevant, but confounded | Strongest long-term role among proposed states | Conditioning-state input, uncertainty-aware |
| **Exercise-specific capability** | HR does not define capability | May contextualise why performance differs on a day | External performance remains canonical capability evidence |

The local-stimulus conclusion is especially important: a leg press can evoke a greater systemic cardiovascular response than a small-muscle exercise, but that does not mean its HR amplitude quantitatively measures quadriceps activation or hypertrophic stimulus. Studies of resistance protocol physiology show HR, VO₂, lactate and perceived exertion are related internal-response measures, not interchangeable representations of one underlying local-muscle variable. citeturn16search19turn17search5

### Systemic versus local state decomposition

The proposed decomposition

```text
poor systemic state
    ↓
greater strain / reduced available capability
    ↓
worse next-set performance
```

without retroactively requiring

```text
poor systemic state
    ↓
less hypertrophic stimulus from a set already completed
```

is physiologically and computationally sensible.

External work actually completed remains strong evidence of what occurred. Systemic state can influence future performance and the cost of producing that performance without being identical to local muscular adaptation.

This supports separate N-BIO states approximately like:

```text
LocalMuscleAcuteState
SystemicAcuteState
ConditioningState
SkillState
CapabilityState
```

Heart-rate-derived state should **not** live in `SkillState`. It can contextualise SkillState-related efficiency changes, but cardiovascular response is conceptually distinct.

### Exercise-specific systemic cost

There is enough evidence to justify collecting the inputs to learn this, but not enough to initialise it as a confident biological constant.

A future latent could estimate:

```text
SystemicCost(
    exerciseProfile,
    externalWork,
    duration,
    restStructure,
    HRResponse,
    downstreamPerformanceEffects
)
```

not:

```text
SystemicCost = averageHR
```

The distinction matters because shorter-rest protocols can increase cardiovascular/metabolic strain, while exercise identity changes HR recovery and EPOC. citeturn17search20turn17search5

The most compelling validation target is downstream behaviour:

```text
two exercises
similar target-muscle performance evidence
but different:
    HR residuals
    recovery times
    later-session performance losses
```

Repeated person-specific data could then justify a learned fatigue/systemic-cost parameter.

### Rest recommendations

Elapsed rest has direct evidence for preserving performance across repeated sets. HR recovery may contain additional information, but current resistance literature does not establish a personal HR threshold at which another set becomes “ready”. citeturn17search9turn16search6

Thus N-BIO should learn:

```text
P(next-set performance |
  elapsed rest,
  previous performance,
  HR recovery features,
  profile,
  session state)
```

and test whether HR terms improve held-out performance.

Do **not** implement:

```text
wait until HR < 120
```

or

```text
start when HR has recovered 80%
```

without personal predictive validation.

### Major confounders

| Confounder | Consequence for N-BIO HR interpretation |
|---|---|
| **Heat** | Raises HR at matched prolonged external workload through cardiovascular/thermoregulatory strain; cardiovascular drift is well established. citeturn21search0turn21search5 |
| **Hydration / plasma volume** | Dehydration can exacerbate HR drift and cardiovascular strain. citeturn21search3 |
| **Caffeine** | Effects on exercise physiology vary by protocol; meta-analytic evidence shows caffeine can alter submaximal responses and perceived effort, while some HR/HRV outcomes are null. Treat as a confounder, not a fixed-direction correction. citeturn20search1turn20search10turn20search23 |
| **Stimulant medication** | Sympathomimetic stimulant exposure can alter cardiovascular and fatigue/performance responses; exact effects depend on compound/dose/person. Do not “correct” HR without known medication context. citeturn20search2turn20search8 |
| **Sleep loss** | Can alter performance/cardiovascular state, but individual exercise-HR effects are inconsistent; one controlled sleep-deprivation study found no HR difference despite other physiological effects. citeturn20search6 |
| **Time of day** | Meta-analysis reports systematic diurnal differences in submaximal/maximal HR and endurance performance. citeturn19search10 |
| **Breathing / Valsalva** | Substantially modifies haemodynamics during resistance exercise. citeturn17search1turn17search11 |
| **Body posture** | Changes cardiovascular recovery/loading; comparisons should preferably use the same execution profile. |
| **Exercise order / accumulated work** | Changes the pre-set state and makes identical nominal sets physiologically non-identical. |
| **Recent cardio or resistance work** | Residual fatigue can affect subsequent endurance/performance behaviour for hours or longer depending on workload/recovery. citeturn23search13 |
| **Wrist contact/motion/skin perfusion** | Optical HR may contain spikes, lag or missing samples. citeturn18search1turn18search15 |
| **Cardiorespiratory fitness / HRmax** | Changes the relationship between external work and HR. |
| **Stress, anxiety, illness** | Can change autonomic/cardiovascular response independently of muscle capability; unexplained residuals should therefore remain probabilistic rather than diagnostic. |

When these states are unknown, **increase uncertainty; do not infer an unobserved confounder from the HR residual itself**.

## Product flow, predictive validation and concrete test vectors

### Cardio exercise UX

The proposed flow is sound:

```text
TREADMILL

[ Import from Health ]
[ Enter manually ]
```

After selection:

```text
┌──────────────────────────────────┐
│ 20:04                            │
│ 3.06 km                          │
│                                  │
│   ╭─╮     ╭──╮       speed      │
│ ──╯ ╰─────╯  ╰────              │
│                                  │
