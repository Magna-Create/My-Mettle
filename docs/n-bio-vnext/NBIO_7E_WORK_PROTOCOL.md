# N-BIO-7E — Continuous-Agent Work & Review Protocol

> **Status:** required execution protocol for the N-BIO-7E implementation mission.
>
> **Purpose:** preserve the useful discipline of the former ~25-minute coding-agent compute window while allowing a continuous Work agent to run to completion without waiting for user input.
>
> Read with [`PLAN.md`](./PLAN.md), [`CONTEXT_MODULE_ARCHITECTURE.md`](./CONTEXT_MODULE_ARCHITECTURE.md), [`CORE_MODEL_DETAIL.md`](./CORE_MODEL_DETAIL.md), [`CONTEXT_INTERPRETATION_CONTRACT.md`](./CONTEXT_INTERPRETATION_CONTRACT.md), [`PRODUCT_ROADMAP_GATES.md`](./PRODUCT_ROADMAP_GATES.md), the accepted 7D contract/checkpoints, and [`NBIO_7E_CONTEXT_EXTENSION_JOURNAL.md`](./NBIO_7E_CONTEXT_EXTENSION_JOURNAL.md).

---

# 1. Why this protocol exists

Continuous execution is useful, but long uninterrupted implementation runs can accumulate hidden scope drift, forgotten failures, stale assumptions and undocumented interface decisions.

N-BIO-7E is especially sensitive because it introduces:

- new temporal state inference;
- context modules with independent memory/learning;
- a generic module↔N-BIO communication protocol;
- future human-authored/third-party extension seams;
- versioned interfaces that later code may rely on for years.

The implementation agent must therefore work in repeated **implementation → review → resume** cycles even when no platform timeout forces it to stop.

---

# 2. Required cadence

Use approximately **25 minutes of active implementation work per block**.

At the end of each block, enter a short review window before continuing.

Target review duration:

```text
~3–7 minutes
```

The review is an engineering reset, not a request for user input.

Do **not** stop and wait for the user after an ordinary review. Resume automatically.

If a build/test/replay job is already in flight at the 25-minute mark, do not kill useful work merely to satisfy a stopwatch. Finish or inspect the meaningful in-flight operation, then perform the review. As a general guardrail, avoid more than roughly 35 minutes of active implementation without a review unless a long-running deterministic test/build is the reason.

The cadence is approximate and outcome-driven, not ceremonial.

---

# 3. Mandatory review checklist

At every review window, explicitly inspect and record the following.

## A. Current state

Summarise in a few lines:

- what this block attempted;
- what actually changed;
- what remains incomplete;
- whether the current design still matches the 7E contracts.

## B. Repository state

Inspect at minimum:

```text
git status
git diff --stat
git diff --check
git rev-parse HEAD
```

Review the actual diff for the block rather than relying only on memory.

## C. Tests / builds / background work

Record:

- tests/builds already completed;
- tests/builds still running;
- failed/flaky tests;
- warnings or suspicious logs;
- background processes/tasks that should be allowed to finish, cancelled, or investigated.

Do not lose track of a background process merely because implementation moved on.

## D. Error and assumption audit

Ask:

- Did I work around an error instead of understanding it?
- Did I introduce a hidden coupling?
- Did I hard-code something that should be versioned/registered?
- Did I accidentally make a context module authoritative over N-BIO Core?
- Did I introduce future-data leakage or circular learning?
- Did I collapse unknown into false/zero/default?
- Did I accidentally cross into N-BIO-7F, N-BIO-8 or product behaviour?

## E. Documentation catch-up

Update rough documentation **during** 7E, not after memory has faded.

In particular update [`NBIO_7E_CONTEXT_EXTENSION_JOURNAL.md`](./NBIO_7E_CONTEXT_EXTENSION_JOURNAL.md) for any API/SPI-like decision, extension capability, read/write contract, lifecycle, versioning, failure/isolation or module registration choice made in the block.

Update normative docs when the implemented contract genuinely changes.

## F. Targeted internet review

Use the internet as an engineering input where it can improve the current or next block.

At each review, ask whether there is a concrete question worth checking online. Examples:

- plugin/SPI registration patterns;
- Kotlin/JVM service/provider patterns;
- Android component/module constraints;
- schema/API evolution;
- capability-based permissions;
- event/message envelope design;
- plugin lifecycle and failure isolation;
- deterministic replay and idempotency;
- coroutine/thread ownership;
- state-store design;
- version negotiation;
- extension contract testing/TCK patterns;
- lessons from mature extensible systems;
- known mistakes/postmortems from plugin architectures.

Prefer:

1. official platform/language documentation;
2. mature open-source projects with comparable extension requirements;
3. engineering write-ups/postmortems that explain failure modes;
4. standards/specifications where relevant.

Do not cargo-cult a framework or pattern merely because it is popular.

For each useful external finding, record briefly in the journal:

```text
question
source/project
lesson
whether it changes our implementation
```

If no external lookup is useful for that block, record that fact rather than inventing research work.

## G. Scope and next-block plan

Write the next concrete block objective before resuming.

Prefer one coherent goal over a grab-bag of unrelated tasks.

Then resume automatically.

---

# 4. Internet research is encouraged, but architecture remains evidence-driven

The agent is explicitly authorised to perform targeted online research during implementation and review windows.

The goal is to learn from existing systems before repeating known mistakes, especially because 7E is intended to expose human-authored extension seams later.

However:

- online examples are inputs, not authority over the N-BIO contracts;
- do not replace uncertainty-aware/replayable N-BIO requirements with a generic plugin framework's assumptions;
- do not add dependencies merely because an article recommends them;
- do not silently change scientific semantics based on software architecture examples;
- record material architecture changes and their rationale.

If current Android/Kotlin platform behaviour matters, verify current documentation rather than relying on old memory.

---

# 5. Do not accidentally implement arbitrary runtime code loading

Future third parties/humans may need to create new context modules, but that does **not** currently authorise downloading/executing arbitrary external code inside the Android app.

The 7E goal is a clean extension/SPI boundary that can support future human-authored modules through controlled build/library/integration mechanisms.

Runtime plugin loading, remote code loading, sandboxing and marketplace-style execution are separate security/product/platform decisions and must not be introduced merely to prove modularity.

Research relevant Android security/policy constraints if implementation choices approach that boundary.

---

# 6. Required rough API/SPI documentation discipline

7E is expected to create interfaces that future human-written modules may use.

Every material communication/interface decision must be documented approximately when it is made, including:

- interface or protocol name;
- who owns it;
- who may call/read/publish;
- data shape;
- versioning rule;
- missing/unknown semantics;
- chronology/time semantics;
- threading/lifecycle semantics where relevant;
- failure behaviour;
- replay behaviour;
- permissions/capabilities;
- downstream consumers;
- compatibility implications;
- example usage when useful.

This applies even to internal interfaces if they are plausible future extension seams.

Do not postpone all documentation until closure.

The required living record is:

[`NBIO_7E_CONTEXT_EXTENSION_JOURNAL.md`](./NBIO_7E_CONTEXT_EXTENSION_JOURNAL.md)

The journal is intentionally rough. A separate post-7E mission will clean and consolidate it into a polished extension/integration contract after the implementation is known to work.

---

# 7. Checkpoints and commits

The 25-minute cadence does **not** require a Git commit every 25 minutes.

Commit when a coherent, tested unit is complete.

At review windows:

- preserve work safely;
- avoid giant opaque diffs;
- commit/push when doing so creates a meaningful recovery/checkpoint boundary;
- do not create noisy commits solely to satisfy the timer.

Before a major architecture turn, a small coherent checkpoint is preferred.

---

# 8. When the agent may stop for the user

Ordinary review windows must resume without user input.

Stop and request collaboration only when one of these is genuinely reached:

1. `PRODUCT_ROADMAP_GATES.md` explicitly requires `COLLABORATION REQUIRED` for the decision at hand;
2. proceeding would make an unapproved behaviour-driving product-policy decision;
3. proceeding risks destructive/canonical data mutation without an accepted contract;
4. two scientifically/materially different architectures remain genuinely underdetermined after reasonable targeted research and the choice would materially constrain future behaviour;
5. a required external/tool capability is unavailable and no safe equivalent exists.

Do not use these exceptions as excuses to stop for ordinary coding decisions.

---

# 9. Review-entry template

Append a compact entry to the 7E journal or implementation checkpoint using a shape like:

```text
REVIEW <N> — <timestamp / HEAD>

Block objective:

Completed:

Diff / architecture notes:

Tests/builds/background tasks:

Errors/warnings:

Extension/API decisions recorded:

Internet check:
- question:
- source/project:
- lesson:
- implementation effect:

Scope check:

Next block:
```

Do not turn every entry into polished prose. The purpose is continuity and auditability.

---

# 10. Closure rule

N-BIO-7E is not complete merely because the agent ran continuously until tests became green.

Before closure, review all periodic entries and confirm:

- no unresolved background failure was forgotten;
- all material extension/API decisions are represented in the rough journal;
- the implemented module protocol matches the documented architecture;
- external lessons that materially changed code are traceable;
- important rejected approaches are recorded where their rejection prevents future repetition;
- normative docs reflect the final implemented semantics;
- a separate post-7E documentation-cleanup mission can reconstruct the extension surface from the journal without reverse-engineering the whole codebase.

The later cleanup mission may reorganise and polish the journal, but must not erase useful history about why the interface evolved as it did.
