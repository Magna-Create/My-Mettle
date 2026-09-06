
A central hierarchy offers better sharing and cold start but can propagate a poor common assumption more widely.

The strongest long-term candidate is therefore hybrid:

```text
local capability nodes
        +
sparse pair-specific directed relationships
        +
central feature-conditioned relationship priors
        +
explicit no-transfer behaviour
```

No hardware-independent strength scalar is necessary.

---

# Partial Pooling and Negative Transfer

Hierarchical partial pooling does not automatically make transfer safe.

The key statistical issue is **exchangeability**.

If the model assumes two relationships belong to a common family when one is genuinely atypical, ordinary shrinkage can pull the atypical relationship towards an inappropriate shared mean.

Neuenschwander et al. developed exchangeable/non-exchangeable mixture structures precisely because standard exchangeability can produce excessive borrowing for atypical strata.

Kaizer et al. similarly developed source-specific exchangeability structures to avoid inappropriate multisource borrowing.

Bakker and Heskes introduced task clustering and gating because assuming every task can benefit equally from every other task is too strong.

These fields are not resistance training, so they do not provide an N-BIO biological model.

They establish the statistical principle:

> **Partial pooling is useful, but relatedness/exchangeability is itself a model assumption.**

Therefore 7F needs:

```text
semantic admissibility
        |
        v
robust relationship inference
        |
        v
explicit transfer/no-transfer branch
        |
        v
destination-only comparison
        |
        v
prequential promotion/rejection
```

**REJECT:** hierarchical pooling automatically prevents negative transfer.

---

# Equipment Context vs Execution Profile

Equipment identity and execution semantics should remain separate.

The Product Roadmap already states that changing machine/equipment does **not automatically** require a new `ExecutionProfileVersion`. A distinct profile/version may be required when the equipment materially changes mechanism, ROM geometry, unilateral/bilateral behaviour, resistance curve, or another capability-defining execution semantic.

This is the correct governing rule.

## Likely same execution profile, different equipment context

Examples include:

- 20 kg bar changed to a 15 kg bar;
- same cable movement on two otherwise comparable cable stations;
- same machine model at two gyms.

The numerical relationship may still need learning.

## Candidate for distinct profile/version

Examples include:

- an implementation whose fixed path materially changes existing profile semantics;
- dependent bilateral mechanism changed to genuinely independent arms where that changes laterality/support semantics;
- material ROM change;
- resistance-curve mode change.

A barbell RDL to Smith RDL is **not categorically declared** a new profile by this research because direct RDL-specific evidence was not found.

Rather:

> If the Smith implementation materially changes existing capability-defining execution semantics, a distinct profile/version is warranted under the repository's existing rule.

There is no universal "Smith = new profile" rule.

There is also no validated one-to-five stability/degree-of-freedom score that can decide this automatically.

---

# Architectural Ownership

Three ownership patterns were considered.

## ContextModule canonical ownership

**Reject.**

ContextModule memory is derived and replayable. Equipment identity used to interpret historical performance is canonical evidence.

The ContextModule contract itself states that equipment identity/calibration/translation remains governed by the dedicated late-7 equipment pathway and that text-extracted equipment-difference reports do not constitute canonical machine identity.

## Core/domain canonical ownership

Appropriate for:

- which equipment existed;
- which equipment was used;
- known/configured mechanical semantics;
- provenance;
- immutable versions.

## Hybrid canonical/derived architecture

Strongest design:

```text
CORE / DOMAIN
    canonical equipment identity
    equipment interpretation/calibration version
    historical binding
    provenance

TRANSLATION ENGINE
    learned user-specific relationship
    transfer/no-transfer gate
    residual uncertainty
    predictive mapping

CONTEXT MODULES
    may consume confirmed equipment semantics
    or learn auxiliary effects
    but do not own canonical equipment history
```

---

# Cold Start

Cold start should improve continuously as information increases rather than switching at arbitrary thresholds.

| Available information | Defensible contribution |
|---|---|
| Only "different equipment" known | broad relationship or no-transfer |
| Stable anonymous instance | direct local history can accumulate |
| Equipment family known | weak structural relationship information |
| Mechanism known | improved feature/prior |
| Manufacturer known | modest information alone |
| Exact model known | may unlock documented mechanics |
| Exact configuration known | stronger local constraints |
| Exact deterministic physics known | perform local deterministic processing |
| First destination history | update destination capability directly |
| Repeated destination history | destination evidence increasingly dominates |

A stable anonymous machine can eventually become highly predictable through direct history despite missing OEM identity.

Thus:

**REJECT:** generic population strength priors are required for cold start.

Population relationship priors may be useful later, but they are optional.

Willardson and Bressel's leg-press/squat data are instructive: population-level cross-exercise prediction contained useful information, but predictive strength differed substantially by training status. This supports population relationships as possible priors, not universal conversions.

---

# Missing Information

Missing information must not be replaced by plausible-looking constants.

Therefore:

```text
unknown pulley ratio != 2:1
unknown starting resistance != 0
unknown friction != 1
unknown manufacturer != generic calibrated machine
```

If a missing mechanical variable has a defensible prior, the model may marginalise over it.

If no defensible prior exists, the model should omit the feature and retain broader residual uncertainty.

No arbitrary rule such as:

```text
unknown OEM -> add 20% uncertainty
```

is required.

Uncertainty naturally increases because fewer constraints are available.

---

# Direct Destination Learning

Destination evidence is more directly relevant than transferred evidence.

But there is no universal rule saying:

```text
one destination observation -> transfer disappears
```

or:

```text
three destination observations -> transfer disabled
```

Evidence strength depends on:

- predictive precision;
- observation noise;
- temporal freshness;
- domain overlap;
- same-session dependence;
- semantic certainty.

Direct destination evidence should increasingly dominate according to **information content**.

Source evidence may continue to help even in a mature destination if, for example, the source is observed more recently and provides useful information about temporal capability change.

Whether that occurs is an empirical forecasting question.

---

# Temporal Confounding

N-BIO already has a temporal capability model.

7F should use it rather than inventing a separate equipment-time state unless predictive residuals demonstrate a need.

Same-session A/B observations are attractive because slow capability development is nearly held constant.

They are not clean calibration pairs.

Exercise-order studies show that earlier work can alter later repetitions and other performance measures through fatigue, while some preceding activity can also potentiate later output.

Thus:

> Same-session pairing reduces slow temporal confounding but can increase acute-state/order confounding.

A deliberate A/B experiment would preferably:

- counterbalance order across occasions;
- standardise rest;
- record preceding work;
- repeat across sessions;
- avoid treating all sets within one session as independent confirmations.

For prequential evaluation at time \(t\), use only state available before the event:

\[
p(C_t|D_{<t}).
\]

Do not use retrospective smoothed estimates containing later observations and call them historical predictions.

---

# Same Profile, Different Equipment: Open Architecture Experiment

This remains the most important unresolved 7F design question.

Consider one unchanged `ExecutionProfileVersion` performed on stable equipment A and B.

## N0 - equipment-local, no transfer

Maintain separate local predictions using only direct A or B history.

This is the safety champion.

## N1 - equipment-blind pooling

Ignore equipment identity and pool A+B history.

This diagnostic asks whether equipment separation improves prediction at all.

## M1 - common profile capability with equipment-conditioned observation mappings

Use one latent profile capability:

\[
C_e(t)
\]

with separate mappings:

\[
Y_A \sim h_A(C_e(t))
\]

\[
Y_B \sim h_B(C_e(t)).
\]

This is compact and can share temporal progression efficiently.

Its risk is that it presumes a sufficiently coherent common latent capability.

## M2 - equipment-local capability facets with directed translation

Maintain:

\[
C_{e,A}(t)
\]

and:

\[
C_{e,B}(t)
\]

and learn:

\[
R_{A\rightarrow B}
\]

and, separately if useful:

\[
R_{B\rightarrow A}.
\]

This preserves local coordinates most explicitly but creates more sparse states.

### Research verdict

**OPEN.**

M2 is epistemically conservative, while M1 may be statistically/computationally efficient.

Neither should become architecture by decree.

They should be compared on genuine repeated-equipment histories.

---

# Prequential Validation

The repository's existing evaluation requirement is correct.

For every destination event:

```text
1. use evidence available strictly before the event
2. freeze candidate predictive distributions
3. observe the destination outcome
4. score each frozen prediction
5. only then update model state
```

This is consistent with Dawid's prequential principle.

The no-transfer baseline must contain all legitimate destination information except source-profile performance evidence.

Otherwise the comparison does not answer whether transfer helped.

---

# Continuous Predictive Scores

For appropriate continuous destination outcomes, evaluate:

- CRPS;
- log predictive score;
- WIS where quantile forecasts are used;
- interval coverage;
- PIT/reliability;
- signed bias;
- MAE as a secondary diagnostic;
- prediction availability;
- numerical stability.

Proper scoring rules reward probabilistic sharpness while maintaining calibration rather than rewarding artificially broad or overconfident forecasts.

CRPS remains expressed on the target's scale.

Therefore raw CRPS from kilograms should not simply be averaged with raw CRPS from seconds and interpreted as one meaningful universal error.

Bolin and Wallin's scaled CRPS is worth testing as a secondary scale-invariant diagnostic.

Scale invariance still does **not** imply that unlike variables represent one semantic quantity.

---

# Ordinal Validation

`DEVICE_ORDINAL` requires ordinal-aware validation.

Appropriate tools include:

- Ranked Probability Score;
- categorical log score;
- ordinal reliability/calibration;
- appropriate discrete/randomised PIT diagnostics.

Do not score `Level 7` by pretending its numeric distance from `Level 6` is the same physical quantity as another adjacent level.

---

# Negative-Transfer Evaluation

For a lower-is-better proper score \(S\), define:

\[
\Delta S_k
=
S_{\text{transfer},k}
-
S_{\text{no-transfer},k}.
\]

Then:

```text
Delta S < 0   transfer helped
Delta S > 0   transfer hurt
```

But one outcome is noisy.

Report at least:

- cumulative/mean paired difference;
- median difference;
- proportion of events made worse;
- profile/equipment-family strata;
- severe tail losses;
- catastrophic overconfidence;
- prediction availability;
- runtime/memory/numerical failure.

A globally favourable mean must not conceal repeated harmful transfer for one relationship family.

---

# Previous Claims: Final Classification

| # | Previous claim | Final verdict |
|---|---|---|
| 1 | Displayed load has zero predictive value across modalities | **REJECT** |
| 2 | Every observation should first become universal physical load | **REJECT** |
| 3 | Known 2:1 pulley is sufficient to halve personal capability | **REJECT** |
| 4 | Free weights are simply machines with ratio 1 | **REJECT** |
| 5 | Fixed 1-5 stability/DOF index is defensible | **REJECT AS PROPOSED** |
| 6 | Unknown friction should default to 1.0 | **REJECT** |
| 7 | OEM cam curves should collapse to one scalar | **REJECT** |
| 8 | Central/star architecture requires global latent strength | **REJECT** |
| 9 | Hierarchical pooling automatically prevents negative transfer | **REJECT** |
| 10 | Population strength priors are required for cold start | **REJECT** |
| 11 | One destination observation should override transfer | **REJECT** |
| 12 | Three observations is an appropriate general transfer gate | **REJECT** |
| 13 | Formal equipment calibration is necessary for useful translation | **REJECT** |
| 14 | OEM information should always act as a prior | **REJECT** |
| 15 | Equipment instance requires known manufacturer/model | **REJECT** |
| 16 | Selector-labelled kg and arbitrary ordinal share one semantic type | **REJECT** |

The fact that all sixteen categorical versions are rejected does not mean every underlying intuition was useless.

For example:

- stability matters;
- equipment mechanics matter;
- hierarchical sharing can help;
- population information may later help;
- OEM information can be valuable.

The overgeneralised categorical formulation is what fails.

---

# Minimum Useful N-BIO-7F

## 1. Does every relevant resistance observation need stable equipment context?

It must be able to resolve an appropriate equipment context wherever equipment can change evidence meaning.

This does not require serialising every commodity dumbbell, bar, or plate.

## 2. Historical granularity?

Session-exercise actual binding with observation inheritance and optional set-level override.

Effective observation meaning must remain unambiguous.

