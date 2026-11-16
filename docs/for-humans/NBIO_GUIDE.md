# N-BIO, explained without the engineering shorthand

> **Status:** explanation of the current roadmap and limitations as of 16 November 2026. This is not a mathematical specification or an implementation status claim beyond the cited technical sources.
>
> Technical authority: [N-BIO PLAN](../n-bio-vnext/PLAN.md), [postponed investigation register](../n-bio-vnext/POSTPONED_DEVELOPMENT.md), [product decision gates](../n-bio-vnext/PRODUCT_ROADMAP_GATES.md).

## What is N-BIO for?

N-BIO is the intelligence and evidence system behind My Mettle. Instead of treating a training session as a few weights and numbers, it preserves what was performed, under which conditions, and what can responsibly be inferred about future training.

The short version: **remember → estimate → compare → eventually recommend**. None of those should require the user to understand the model's internal mathematical terminology.

| Phase | What it contributes | Current practical interpretation |
| --- | --- | --- |
| N-BIO-5 | Represents programme targets and resolves exercises using stated priorities and constraints | Existing normal workout resolver is a deterministic benchmark, not the future full adaptive coach |
| N-BIO-6 | Records training evidence with correct units, meaning, timing and history | Foundation for future inference and replay |
| N-BIO-7A/B | Models uncertainty about current/possible performance, using versioned numerical methods | Research architecture and competing statistical predictors |
| N-BIO-7C | Extends capability estimation to holds, timed tasks and repeated contractions | Structurally built; empirical accuracy still needs data (PD-001) |
| N-BIO-7D | Estimates the *relative demand* of a set and aggregates training exposure | Structural candidate, not a proven physiological stimulus measurement (PD-002) |
| N-BIO-7E | Estimates slowly changing versus temporary performance effects; can consume learned context signals | Structural candidate, not a measured fatigue/recovery meter (PD-003) |
| N-BIO-7F | Describes actual equipment and investigates prediction transfer across machines/execution profiles | Structural equipment foundation; cross-machine accuracy needs evidence (PD-004) |
| N-BIO-7G | More advanced conditioning/cardio capability | Deferred until after first strength-focused v1 |
| N-BIO-8 | Chooses and adapts training using programme intent, constraints and supported model evidence | Not yet implemented as the full adaptive coach; research/product design gate applies |
| N-BIO-9 | Connects health/context sources, manages reanalysis and model updates, and provides richer exports | Some earlier foundations exist; broader integrations remain later scope |

A model being **structurally complete** means its code, mathematical invariants, replay and defined tests passed a stated gate. It does **not** certify that its biological interpretation or predictions are accurate for every person.

## The four postponed investigations

### PD-001: Can N-BIO estimate your capabilities accurately?

The algorithm can generate estimates for tasks like loaded holds, duration-only work or repeated contractions. We don't yet have enough comparable real, chronological observations for good calibration.

**Useful next step:** collect normal performance observations plus occasional safe, optional effort/probe information. Freeze a prediction before the attempt, compare with the result, and check whether it beats a simple baseline. Completing a submaximal hold demonstrates ability to do *at least that much*, not maximum possible capability.

**Meaning:** primarily an evidence/validation gap, not a reason to stop building the diary.

### PD-002: Does N-BIO know how demanding a set really was?

N-BIO estimates set demand relative to inferred capability and combines it into a candidate training-dose quantity. But identical sets can feel very different depending on how much effort remained. Nor can ordinary workout history directly measure hypertrophy or actual tissue fatigue.

**Useful next step:** optionally record **estimated reps in reserve (RIR)** and distinguish voluntary stopping, technical limit and an attempted failed rep. Evaluate whether demand/dose improves later performance prediction, without equating that result to proof of muscle growth.

**Meaning:** shares the need for better data with PD-001, but tests a *different* claim.

### PD-003: Does N-BIO understand breaks, illness, travel and time?

The current temporal model includes a slowly changing component and a temporary component that returns towards normal. Its first version has a **fixed three-day transient half-life**. It is not a general detraining/recovery theory.

ContextModules can maintain their own learned state and mathematical learners. They can supply approved signals, but currently cannot freely rewrite the Core temporal transition. New context modules **and/or versioned alternative time models** are possible experiments.

**Useful next step:** timestamped, overlapping context episodes with actual start/end, user-confirmed resolution and a separate date for retrospective entry; compare models before/after training interruptions. A break is not automatically equivalent to illness or physiological regression.

**Meaning:** good place for learning from real user history and improving UX, not for piling up hard-coded context penalties.

### PD-004: Does knowing the machine improve predictions?

Displayed kilograms on different machines may not represent equivalent mechanical resistance. Brand, model, actual equipment instance, loading convention, settings and sometimes physical geometry can improve a transfer prediction.

**Useful next step:** record the actual machine used, maintain a user-editable equipment library and optional calibrated geometry (e.g. roughly measured rail inclination). Test transfer on comparable recorded outcomes. Equipment angles are *metadata*, not a universal force equation.

**Meaning:** build reliable equipment recording **before** demanding proof that equipment-transfer intelligence is accurate.

## How do we stop getting stuck in endless research?

For a reversible candidate:

- Implement a sensible first version with a simple fallback.
- Make real observations and save original facts permanently.
- Preserve the **prediction made before the result**.
- Compare against a baseline and record when the candidate loses.
- Update model versions and recompute *derived* state, not raw history.
- Introduce advanced algorithms only if they offer credible value.

For irreversible history, unsafe advice or claims of measured physiology: require stronger checks. A useful "you could try" suggestion is not the same as an automatically enforced prescription.

The current strategic release order is **Native Stable daily use first**, N-BIO-8/9 afterwards. Lab will use a **read-only local evidence connection** to evaluate future models without touching Stable history.

## Glossary: ordinary English first

| Term in code/docs | Human meaning |
| --- | --- |
| Canonical evidence | What was actually recorded, with unambiguous meaning and history |
| Capability | What the model thinks you may be able to achieve, including uncertainty |
| Prior | A starting assumption before enough personal evidence exists |
| Posterior | The updated range of possibilities after examining evidence |
| Calibration | Whether the model's predicted ranges match how often real outcomes occur |
| Shadow / candidate | Experimental calculation not in charge of the normal workout |
| Benchmark / baseline | The simpler existing method used for fair comparison |
| Prequential / chronological | Predict first, then score against a later observation |
| SetDemand | A candidate estimate of how hard a particular set was relative to ability |
| EffectiveDose / SessionDose | Candidate estimates of muscle-specific exposure and session exposure, not measured muscle growth |
| Persistent / transient | Slower-changing and shorter-lived *statistical* performance effects, not medical diagnoses |
| ContextModule | A separately authored/learned feature processor (e.g. illness or time pressure) with bounded influence |
| Episode | A period of a condition or circumstance; repeated notes within it are not independent occurrences |
| Provenance | Where a value came from, when, and under which model/interpretation |
| Equipment translation | A tentative performance relationship between different machines or execution conditions |
| Model snapshot | Versioned parameters, beliefs and diagnostic state, distinct from a backup of raw workout history |
| RIR | Repetitions in reserve: the estimated number of additional good repetitions remaining |

## Conversation preference for future work

Explain first: **What does it do? Why is it useful? What works already? What's uncertain? What would we build or test next?** Bring in equations, solver terms and specific model identities only if needed. The accompanying technical documents preserve precision so ordinary planning conversations don't need to.

