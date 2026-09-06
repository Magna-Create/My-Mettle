
```text
40 kg
```

is different. It is a mass-labelled **local device coordinate**, even if the endpoint force or exercise mechanics differ from another machine displaying 40 kg.

Therefore:

**REJECT:** selector-labelled kilograms and arbitrary ordinal levels belong to the same semantic type.

---

# Equipment Identity and Historical Binding

Equipment history should preserve the apparatus responsible for evidence.

The repository already requests conceptual equivalents of:

```text
EquipmentModel
EquipmentInstance
EquipmentCalibrationVersion
SessionEquipmentBinding
```

and explicitly requires later equipment replacement not to rewrite historical meaning.

Exact OEM identity is not a prerequisite for stable historical identity.

A useful record may begin as:

```text
EquipmentInstance E17
label = "Chest press by the window - Gym A"
manufacturer = unknown
model = unknown
```

Repeated personal history can already accumulate on E17.

If months later the user determines that E17 is a particular manufacturer/model, that should enrich E17 rather than replace the old observation identity.

Two physical machines of the same model at different gyms should remain distinguishable.

Model identity may justify stronger shared information.

It does not prove identical physical behaviour.

---

# Historical Binding Granularity

Four levels were considered.

**Execution-profile-only binding** is insufficient because one unchanged execution profile may be used on several pieces of equipment.

**Whole-session binding** is insufficient because one session contains multiple pieces of equipment.

**Per-observation binding** is semantically complete but can become unnecessarily repetitive.

The strongest minimum is therefore:

```text
ExecutionProfileVersion
    optional preferred/default equipment

session-exercise occurrence
    actual equipment binding

PerformanceObservation
    inherits actual binding
    unless explicit set-level override exists
```

The important invariant is not a particular database foreign-key placement.

It is:

> Every equipment-sensitive observation can resolve which equipment interpretation generated it, without reconstructing history from today's default.

---

# OEM Metadata Availability and Value

OEM information is useful, but its availability and epistemic strength are heterogeneous.

Useful facts found in ordinary commercial documentation include:

- nominal implement mass;
- starting resistance;
- pulley ratio;
- manufacturer-effective cable resistance;
- rail/glide angle;
- independent arms;
- loading mechanism;
- permitted loads and increments.

Less routinely available are:

- complete force-versus-position curves;
- full lever geometry;
- friction;
- bearing losses;
- actual endpoint tension;
- measured calibration of a specific gym instance.

The repository term `EquipmentCalibrationVersion` should therefore be interpreted carefully.

It may contain:

```text
OEM-declared specification
user-confirmed configuration
deterministic derivation
measured instance calibration
```

with appropriate provenance.

The word **calibration** must not itself imply that a value has been instrumentally measured.

---

# Standards and Calibration

ISO 20957-2:2024 covers stationary strength-training equipment and specifies additional safety requirements and test methods.

Its scope explicitly states that accuracy classes are not applicable to this equipment category because they do not affect safety.

The correct inference is narrow:

> **ISO 20957-2 compliance by itself does not establish that two machines displaying the same load are cross-device calibrated resistance measurements.**

It does **not** imply that commercial stack labels are generally inaccurate.

It does **not** imply that manufacturer specifications are meaningless.

It simply means this particular safety standard does not provide the cross-machine calibration evidence that a universal equipment coordinate would require.

---

# Starting/Base Resistance

Starting resistance is a legitimate and potentially large equipment fact.

The OEM survey found starting-resistance specifications ranging from only a few kilograms per workarm to more than 50 kg on large plate-loaded/sled systems.

Therefore a generic assumption such as:

```text
plate-loaded machine -> starting resistance = 0
```

is indefensible.

However, starting resistance should not automatically become:

```text
globalInterceptKg
```

or an unconditional additive term in every equation.

A defensible representation needs at least the conceptual meaning:

```text
value
unit
scope
manufacturer/device definition
configuration/version
provenance
quality/uncertainty
```

If its relation to the user-entered coordinate is genuinely exact, deterministic local processing is appropriate.

If it only partially constrains the exercise mechanics, it should remain a relationship feature or informative prior.

---

# Which Physics Is Worth Modelling?

| Physical/mechanical information | Recommended treatment |
|---|---|
| lb-to-kg conversion | deterministic |
| known implement mass | deterministic local arithmetic |
| inclusive vs added-load accounting | deterministic once semantics are known |
| exact, semantically understood OEM cable relationship | deterministic local relation |
| starting resistance | deterministic only where its meaning/algebra are exact; otherwise feature/prior |
| independent arms | structural relationship/execution feature |
| loading mechanism | relationship/gating feature |
| counterbalance | feature unless sufficiently quantified |
| sled/rail angle | partial physical constraint |
| detailed lever geometry | future, when genuinely available |
| full cam/resistance curve | future structured representation |
| friction | remain unknown unless measured/otherwise defensibly established |
| actual endpoint calibration | optional future pathway |

The evidence therefore favours a mixture of:

- relationship features;
- informative priors;
- deterministic **partial** transformations.

It does not favour universal normalisation.

---

# Friction

Friction is real but generally poorly observed in ordinary product data.

Brodt et al. experimentally measured friction on one selectorised knee-extension apparatus and found that the relative effect varied with load and movement speed. The study concerns one machine and should not be used to estimate a generic gym-machine friction coefficient.

Its useful implication is narrower:

> Actual instance behaviour can differ because of mechanics that are not completely specified by manufacturer/model identity.

This supports stable instance identity.

It does **not** support a v1 `frictionCoefficient` field populated with guesses.

**REJECT:** unobserved friction defaults to 1.0.

Unknown remains unknown.

---

# Profile-Local kg and Why It Is Not `L_true`

N-BIO may legitimately retain a profile-local canonical kilogram coordinate where the current versioned profile model defines one.

A local coordinate can incorporate exact known semantics.

For example:

\[
60\text{ kg added plates}+20\text{ kg known bar}
=
80\text{ kg configured mass}.
\]

A documented cable mechanism may likewise have a manufacturer-defined effective local resistance.

These calculations remain local.

They do not establish:

\[
80\text{ kg barbell}
=
80\text{ kg Smith}
=
80\text{ kg cable}
=
80\text{ kg lever machine}.
\]

There is no requirement to manufacture an intermediate universal load before statistical transfer.

---

# What Quantity Should Transfer?

Several possibilities were considered.

## Raw displayed load

Advantages:

- canonical;
- directly observed;
- easy to interpret.

Limitations:

- does not incorporate repetitions or duration;
- does not represent temporal capability;
- does not carry uncertainty;
- does not distinguish easy working sets from stronger demonstrations;
- does not generalise across heterogeneous metric families.

**Verdict:** useful baseline, not preferred transfer object.

## Raw working-set history

A one-stage model consuming raw history could theoretically retain more information.

However, it would have to reimplement:

- lower-bound capability inference;
- temporal state;
- submaximality/action policy;
- robust performance noise;
- heterogeneous capability families.

This duplicates work already assigned to same-profile N-BIO.

**Verdict:** credible offline challenger, not preferred first production architecture.

## Profile-local resistance coordinate

Better semantically than raw input but still only one component of capability.

**Verdict:** useful upstream evidence, insufficient primary transfer object.

## Capability parameter summaries

Potentially efficient within compatible families.

However, point summaries can discard:

- parameter covariance;
- posterior skew/multimodality;
- supported domain;
- temporal uncertainty.

**Verdict:** possible implementation representation, but not the conceptual contract.

## Capability posterior/predictive information

This best matches the existing architecture.

Conceptually a source message should retain enough information to communicate:

```text
profile/version
equipment context
capability family
as-of timestamp
posterior/predictive state
informed load/rep/duration domain
important dependence/covariance
model versions
evidence cutoff/provenance
```

Then source uncertainty is propagated rather than discarded.

Conceptually:

\[
p(C_d|D_s)
=
\int
p(C_d|C_s,R_{sd},Z_{sd})
p(C_s|D_s)\,dC_s.
\]

**N-BIO DESIGN SYNTHESIS:** existing capability posterior/predictive information should be 7F's principal production input.

Modular Bayesian methodology provides a valid statistical precedent for joining separately specified probabilistic submodels through explicit link quantities rather than rebuilding the whole model monolithically.

---

# `L_Tensor` Analogies

`L_Tensor` should describe the broader structured learned representation, not dictate an implementation family.

The closest useful analogy is:

> **a set of heterogeneous local probabilistic states connected by typed, uncertain relationships.**

Possible implementations include:

### Hierarchical relationship models

Local capabilities remain local while selected relationship/noise parameters share priors.

### Sparse probabilistic graphs

Nodes represent local capability states.

Edges represent directed source-to-destination conditional relationships.

### Multi-output Gaussian processes

Potentially useful where nonlinear relationships and task covariance matter.

### Low-rank matrix/tensor factorisation

Potentially valuable if accumulated evidence demonstrates a genuinely low-rank relationship structure.

Low rank is an empirical assumption, not something established by the name `L_Tensor`.

Factor models also carry well-known rotation/identifiability issues.

### Multiple local spaces

Nothing requires all execution profiles to embed on one common numerical dimension.

Accordingly, `L_Tensor` should not imply:

- universal SI quantity;
- torque;
- Newtons;
- universal kilograms;
- one tensor object;
- one latent dimension;
- symmetric relationships;
- transitive relationships;
- global user strength.

---

# Candidate Relationship Models

## N0: destination-only/no-transfer champion

This is mandatory.

It contains everything legitimately available for destination prediction **without using source-profile performance evidence**:

- destination semantics;
- destination equipment facts;
- direct destination history;
- existing destination temporal capability;
- normal upstream priors.

This is the baseline that transfer must beat.

## M0: directed robust pairwise relationship

A minimal compatible-family candidate might resemble:

\[
C_d
=
\alpha_{sd}
+
B_{sd}C_s
+
\epsilon_{sd}.
\]

The equation is illustrative, not authoritative.

The important properties are:

- directionality;
- source-posterior uncertainty propagation;
- robust residual;
- strong shrinkage;
- destination evidence retained;
- explicit no-transfer possibility;
- chronology-safe learning.

Cotterman's population regression is evidence that a simple affine relationship can sometimes be highly predictive. It is **not** evidence that Cotterman's coefficients should become an N-BIO Smith conversion.

## Hierarchical relationship prior

Pair parameters can later borrow through semantic/equipment relationship features.

The hierarchy should pool **relationship behaviour**, not absolute user capability.

## Sparse graph + central hypermodel

If useful relationships become numerous enough, a central model can learn:

- what kinds of edges commonly transfer;
- typical residual variation;
- which semantic/equipment features help predict relationship strength;
- edge-prior parameters.

The centre remains a relationship learner.

It does not become global strength.

---

# Star vs Mesh

For \(P\) contexts, a complete directed mesh has:

\[
P(P-1)
\]

possible edges.

But real implementation need not instantiate them all.

A sparse graph can maintain:

\[
O(E), \qquad E \ll P^2
\]

learned relationships.

A pure mesh offers:

- transparency;
- local updates;
- explicit directionality;
- easy pair-level failure diagnosis.

Its weakness is sparse data.
