# N-BIO-7F
## Equipment Context, Structured Cross-Profile Transfer, and Practical Equipment-Aware Inference
### Second-Pass Corrective Research — Final Proofread Report

---

# Executive Summary

N-BIO-7F should **not** convert heterogeneous resistance-training evidence into a universal physical load or a single hardware-independent strength quantity.

There is no defensible universal `L_true`.

`L_Tensor` should remain conceptual shorthand for N-BIO's broader heterogeneous, multidimensional, user-specific learned state. It should not be interpreted as a literal Kotlin tensor, an SI quantity, Newtons, torque, universal kilograms, "biological kilograms", a single random variable, or a global user-strength scalar.

The defensible architecture is instead:

```text
canonical local evidence
        |
        v
exact local physical interpretation where genuinely known
        |
        v
profile-local probabilistic capability
        |
        v
directed, uncertainty-aware relationship inference
        |
        v
destination predictive distribution
```

with:

```text
NO USEFUL TRANSFER
```

remaining a legitimate result.

This extends rather than replaces the current N-BIO architecture.

The repository already requires:

- immutable raw evidence;
- reproducible/versioned derived inference;
- historical semantics and current-model reinterpretation as distinct modes;
- dynamic profile-local capability;
- capability separated from action/observation policy;
- known equipment/execution changes treated as semantic boundaries;
- no raw kilogram transfer between unrelated profiles;
- equipment-specific mechanics remaining local unless an explicit translation model exists.

The Product Roadmap already requires late-N-BIO-7 support for conceptual equivalents of `EquipmentModel`, `EquipmentInstance`, `EquipmentCalibrationVersion`, and `SessionEquipmentBinding`. It explicitly states that changing equipment does **not automatically** require a new `ExecutionProfileVersion`, distinguishes user-entered load from equipment configuration and N-BIO's modelling coordinate, and requires 7F translation to condition on equipment information where available while propagating uncertainty where it is absent or weak.

The Core contract already places cross-profile translation downstream of same-profile capability and defines canonical capability as profile-specific demonstrated performance rather than generic estimated 1RM.

The empirical literature explains why this separation is necessary. Equipment contexts can produce substantial and repeatable performance differences while remaining strongly predictive of one another. Cotterman et al. found extremely strong Smith/free-weight bench relationships alongside a systematic mean difference. Simpson et al. found similarly strong free-weight/machine bench associations but markedly weaker lower-body relationships when comparing substantially different exercises.

Therefore:

> **Physical non-equivalence does not imply predictive uselessness, and predictive usefulness does not imply physical equivalence.**

Physics, statistics, and probability have different jobs.

**Physics** can resolve exact local facts such as unit conversion, known implement mass, a genuinely documented pulley relationship, or known geometry.

**Statistics** learns whether and how performance in one local context predicts another.

**Probability** represents sparse histories, missing mechanics, temporal change, observation noise, model discrepancy, and uncertainty about whether transfer is useful at all.

The smallest defensible 7F therefore preserves original evidence, adds only the missing historical semantics that cannot currently be represented, binds observations to equipment context where relevant, consumes existing local capability inference, and tests transfer against a destination-only baseline.

The strongest simple challenger is:

> **a directed, robust relationship model over existing capability distributions, with explicit source-uncertainty propagation and a genuine no-transfer branch.**

A stronger future architecture may use:

> **a sparse graph of heterogeneous local capability nodes with a central feature-conditioned relationship hypermodel.**

The centre would learn how relationships tend to behave. It would not contain a supposedly true equipment-independent strength value.

One important architectural question remains intentionally unresolved:

> When the same `ExecutionProfileVersion` is used on several stable but non-equivalent equipment contexts, should N-BIO infer one shared execution capability with equipment-conditioned observation mappings, or separate equipment-local capability facets joined by 7F?

That question should be resolved empirically through champion/challenger testing rather than architectural taste.

---

# Research Method and Source Quality

The research distinguished three forms of authority.

## Repository authority

The supplied My Mettle/N-BIO documents define the current architecture and the invariants that 7F must preserve.

The most relevant are:

- `PRODUCT_ROADMAP_GATES.md`;
- `ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md`;
- `CORE_MODEL_DETAIL.md`;
- `CONTEXT_MODULE_ARCHITECTURE.md`.

External literature was not used to silently replace these contracts.

The repository already establishes the equipment-history requirement, the distinction between local equipment mechanics and cross-profile translation, profile-local capability, immutable provenance, replay, and ContextModules as derived learners rather than canonical equipment stores.

## External evidence

Priority was given to:

1. systematic reviews and meta-analyses;
2. peer-reviewed controlled within-person studies;
3. longitudinal intervention studies;
4. direct biomechanics/mechanical studies;
5. canonical statistical-methodology papers;
6. metrology and equipment standards;
7. direct OEM technical documentation.

Conference abstracts, small exploratory studies, adjacent-device analogies, and weaker journals were retained only where their limitations were explicit.

## Evidence labels

Claims in this report use the following meanings:

- **ESTABLISHED PHYSICAL FACT**
- **SUPPORTED EMPIRICAL FINDING**
- **SUPPORTED STATISTICAL PRINCIPLE**
- **PLAUSIBLE ENGINEERING DESIGN**
- **N-BIO DESIGN SYNTHESIS**
- **WEAK / INSUFFICIENT EVIDENCE**
- **UNKNOWN**

A lack of evidence is not converted into a mechanical default.

---

# Existing N-BIO Constraints

N-BIO already imposes several hard constraints on 7F.

The Adaptive Architecture states that:

- raw evidence is immutable;
- derived inference is reproducible and versioned;
- historical semantics remain distinct from current-model reinterpretation;
- raw kilograms do not transfer between unrelated execution profiles;
- unknown remains unknown;
- weak evidence may produce broad or tentative output but not silent authority;
- semantic metadata overrides statistical guessing when a real-world boundary is known;
- absolute capability, local resistance coordinates, equipment mechanics, and side capability remain local unless explicit translation exists.

Its guiding rule is:

> **Pool statistical behaviour, not physical capability.**

The Core contract defines same-profile capability as demonstrated performance near the user's observed domain, not a generic e1RM. Ordinary successful working sets are lower-bound evidence rather than assumed maximal attempts. Cross-profile translation comes later in the model sequence.

The Product Roadmap separately distinguishes:

```text
USER-ENTERED LOAD
what the user recorded

EQUIPMENT CONFIGURATION
what the machine or implement means mechanically/operationally

N-BIO RESISTANCE/CHALLENGE COORDINATE
what a versioned model uses internally
```

and explicitly warns against converting known moving mass into universal resistance.

These constraints are preserved throughout this report.

---

# Physical Equivalence vs Predictive Transfer

The scientific objective of 7F is **prediction**, not proof of measurement interchangeability.

A source context can provide useful information about a destination even when:

- its mechanical coordinate differs;
- its selected-load scale differs;
- its execution constraints differ;
- the relationship contains a systematic offset;
- the relationship contains residual uncertainty.

Cotterman et al. provide the clearest example. Smith and free-weight bench performance were extremely strongly related across individuals, while the modal mean loads differed substantially.

Thus high association does not establish physical equivalence.

Conversely, physical non-equivalence does not imply that source evidence is useless.

The proper 7F question is:

> Given the user's currently available information, source and destination execution profiles, equipment contexts, time, and uncertainty, how much should source evidence alter the destination predictive distribution?

Legitimate answers include:

```text
strong transfer
moderate transfer
weak transfer
broad starting prior
no useful transfer
```

No-transfer is not an implementation failure. It is a valid learned conclusion.

---

# Practical Magnitude of Equipment Effects

Equipment effects can be large enough to dominate ordinary test-retest variation, but the magnitude is highly task-specific.

Grgic et al. reviewed 32 1RM reliability studies comprising 1,595 participants. Across studies, the median ICC was 0.97 and the median coefficient of variation was 4.2%. In participants with prior resistance-training experience, median ICC was 0.98 and median CV was 3.3%.

These values are useful only as a rough empirical scale reference. They are **not** an N-BIO working-set noise prior.

A cross-equipment shift of 15-20% is therefore plainly large relative to typical 1RM test-retest variability. A 2-4% difference may still be genuine, but repeated personal evidence is correspondingly more important.

Haugen et al.'s meta-analysis included 13 studies and 1,016 participants. Free-weight-test strength improved more following free-weight training, while machine-test strength tended to improve more after machine training. In direct comparisons between the modalities, no significant differences were found for dynamic strength, isometric strength, countermovement jump, or hypertrophy.

The appropriate conclusion is therefore:

> **Strength adaptation shows substantial training/testing specificity, while the available evidence does not establish a general free-weight or machine superiority for the directly compared strength, jump, or hypertrophy outcomes.**

It does not establish that free-weight and machine adaptations are biologically identical.

---

# Free Weights

Free weights should remain first-class equipment.

They should not be represented merely as machines whose mechanical ratio happens to equal one.

Their physical semantics can often be unusually transparent.

If N-BIO knows that:

```text
bar mass = 20 kg
added plates = 60 kg
```

then:

\[
60+20=80\text{ kg configured mass}
\]

is exact local arithmetic.

That is useful.

It does not establish:

\[
80\text{ kg barbell performance}
=
80\text{ kg machine performance}.
\]

Dumbbells provide the same distinction. Two 30 kg dumbbells have straightforward mass semantics, but independent implements impose different control and stability demands from one 60 kg bar.

Saeterbakken et al. studied 12 trained men and found dumbbell chest-press 1RM load 14% below Smith and 17% below barbell, while barbell load was about 3% above Smith.

**ESTABLISHED PHYSICAL FACT:** known implement mass can resolve local load accounting.

**REJECT:** free weights are simply machines with ratio 1.

---

# Smith Machines

Smith machines provide some of the strongest evidence for useful but non-universal transfer.

Cotterman et al. found:

- free-weight bench exceeded Smith bench on average;
- Smith squat modestly exceeded free-weight squat on average;
- free-weight and Smith bench performance nevertheless had an extremely strong predictive relationship.

Saeterbakken et al. found barbell bench only around 3% higher than Smith in their sample.

Ruiz-Alias et al., in 26 recreationally trained participants, reported modestly **higher** Smith rather than free-weight bench 1RM means in both women and men.

These results should not be forced into one preferred direction.

Their combined implication is stronger:

> **The direction and magnitude of a Smith/free-weight offset are not demonstrably invariant across equipment, procedures, populations, and familiarity.**

There is therefore no defensible:

```text
SMITH_MULTIPLIER
```

for general use.

Schwanbeck et al. also found materially different muscle-activation patterns during Smith and free-weight squatting. The study was very small, with six participants, so its reported 43% higher average EMG in the free-weight condition should not be generalised as a universal effect size. It is evidence that the constrained path can change the execution problem.

**SUPPORTED EMPIRICAL FINDING:** Smith constraints can alter both expressed performance and exercise mechanics.

**REJECT:** one universal Smith correction.

---

# Cable and Pulley Systems

Cable systems show where deterministic physics can genuinely help without creating universal personal-equivalence rules.

Life Fitness documents one Adjustable Cable Crossover with:

```text
stack mass = 92.5 kg
manufacturer-specified user-effective resistance = 46.25 kg
cable ratio = 2:1
```

for that exact product/configuration.

This is a legitimate device-local mechanical relationship.

It does **not** imply:

\[
C_{\text{2:1 cable}}
=
\frac{1}{2}C_{\text{1:1 cable}}
\]

for personal capability.

A pulley ratio describes device mechanics. It does not by itself describe personal exercise equivalence.

Cable implementation can also alter line of pull, support, body position, ROM, and stability. Santana et al.'s standing cable-press comparison is useful mainly because it demonstrates how a superficially similar pushing action can become a different whole-body execution task. It should not be used as a generic cable-to-bench conversion.

**ESTABLISHED PHYSICAL FACT:** an exact, semantically understood mechanical ratio may support deterministic local processing.

**REJECT:** generic cable systems have a standard pulley ratio.

**REJECT:** 2:1 mechanics imply half personal capability.

---

# Selectorised and Plate-Loaded Machines

Selectorised and plate-loaded equipment are mechanically heterogeneous.

Folland and Morris assessed eight variable-cam knee-extension machines from six manufacturers. For a constant selected load, machine resistance-torque profiles varied substantially across joint angle and differed from human knee-extensor torque capability.

Dalleau et al. compared a knee-extension machine using a variable cam with a constant-radius pulley. The mechanism significantly changed torque and average velocity and altered the torque-velocity and power-velocity relationships. Average and peak power were generally not significantly different except at 50 and 55 kg.

This corrected wording is important: the study shows meaningful mechanical/performance consequences of the moment-arm design, but not a general reduction or increase in power.

Biscarini's selectorised-equipment analysis further shows that exact joint-power calculations can depend on machine geometry, cam-pulley mechanics, the stack, and user-limb mechanics rather than stack mass alone.

Biscarini and Bonafoni show that two plate-loaded configurations can preserve static resistance torque while differing in inertial properties.

Cacchio et al. provide direct evidence that even two chest-press machines designed around the same broad exercise can differ in motor-learning and transfer properties. Twenty sedentary women were randomised to constrained-path or unconstrained-path training; the authors specifically examined transfer between the two machines.

Therefore:

**SUPPORTED PHYSICAL/EMPIRICAL FINDING:** loading architecture and machine geometry can materially alter local performance mechanics.

**WEAK / INSUFFICIENT EVIDENCE:** a generic selectorised-to-plate-loaded personal-performance conversion.

The loading mechanism is a reasonable relationship feature.

Its personal numerical effect should be learned.

---

# Sleds and Assisted Systems

A sled/rail angle provides real physical information.

For an ideal incline:

\[
F_{\parallel}=mg\sin\theta.
\]

But actual guided-equipment resistance may additionally depend on bearings, friction, linkages, geometry, and the location through which the user applies force.

Therefore:

**ESTABLISHED PHYSICAL FACT:** known angle constrains a gravitational component.

**REJECT:** angle plus nominal moving mass automatically reconstructs a universal personal resistance.

Assistance also requires separate semantics.

If the user records:

```text
20 kg assistance
```

then that assistance value should remain canonical assistance evidence.

It should not silently be rewritten as positive external load.

Direct literature establishing a generic numerical assisted-to-unassisted personal-capability conversion remains weak.

**WEAK / INSUFFICIENT EVIDENCE:** universal assisted-system translation.

---

# Load-Entry Semantics

Current `EntryBasis` should remain an aggregation convention:

```text
TOTAL
PER_HAND
PER_SIDE
```

It answers:

> How is this scalar aggregated across hands/sides?

It does not answer:

> Does this scalar include the base implement or machine contribution?

These are independent questions.

UCUM gives unusually direct support for keeping them independent. UCUM annotations such as `kg{total}` do not change the semantics of the unit itself: the unit remains kilograms. Therefore total/per-side/added semantics should not be encoded in the unit string.

The clearest unresolved historical case is:

```text
20 kg bar + 60 kg plates
```

entered as either:

```text
80 kg
```

meaning the complete local load, or:

```text
60 kg
```

meaning added plates only.

Both could currently be:

```text
EntryBasis = TOTAL
ResistanceSemantics = EXTERNAL
```

while meaning different things.

Therefore 7F requires an additional orthogonal historical semantic conceptually equivalent to:

```text
INCLUSIVE
ADDED
```

The exact enum name and storage location should be determined through source/schema audit.

Historical observations whose convention cannot be reconstructed must **not** be guessed into either category.

**N-BIO DESIGN SYNTHESIS - REQUIRED NOW AS A REPRESENTATIONAL CAPABILITY.**

"Required now" here means N-BIO must be able to represent the distinction where it matters. It does not mean every legacy row must suddenly contain a known value.

---

# Ordinal Device Semantics

`MACHINE_LEVEL` should remain genuinely ordinal.

The International Vocabulary of Metrology defines ordinal quantities as ordered quantities for which differences and ratios do not have physical meaning; they have no measurement unit or quantity dimension.

Thus:

```text
Level 7
```

cannot legitimately become kilograms merely because another machine's selector is mass-labelled.

A selector labelled:
