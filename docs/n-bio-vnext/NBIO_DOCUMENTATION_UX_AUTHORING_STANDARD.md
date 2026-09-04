# N-BIO Developer Documentation UX & Authoring Standard

> **Status:** authoritative documentation-design standard for human/module-author-facing N-BIO extension documentation.
>
> **Scope:** how N-BIO developer documentation is organised, written, verified and maintained. This document does **not** redefine N-BIO runtime behaviour, scientific semantics or product authority.
>
> **Technical truth rule:** current source, tests and accepted normative contracts define what N-BIO actually does. This standard defines how that truth is taught.

---

# 1. Purpose

N-BIO extension documentation should make an unfamiliar developer think:

> I understand what this system is, I can make something useful quickly, and I know where to look when I need precision.

The documentation must optimise simultaneously for:

- low time to first success;
- low cognitive load;
- high technical precision;
- high trust;
- good discoverability;
- safe extension behaviour;
- useful advanced reference;
- long-term maintainability.

The target reader should never need to reverse-engineer N-BIO Core merely to add one supported feature or module.

---

# 2. Evidence basis

This standard synthesises the deep documentation-research pass completed after N-BIO-7E. That research drew from:

- empirical/HCI/API-documentation studies;
- official developer-writing guidance;
- mature extension ecosystems such as VS Code, JetBrains, Gradle and Terraform;
- reference ecosystems such as MDN;
- example-first systems such as Stripe;
- community feedback about documentation that developers praise or abandon.

The strongest recurring findings were:

1. developers scan before they read deeply;
2. they first need a small global mental model;
3. they then split between task/code-oriented and concept-oriented learning;
4. working examples create trust and accelerate learning;
5. task-oriented navigation beats navigation based on internal package/class structure;
6. reference and explanation serve different jobs;
7. unclear prerequisites, stale examples, hidden versioning and fragmented workflows destroy trust;
8. concise, active, direct prose is easier to scan;
9. examples should be executable/tested where practical;
10. extension systems need unusually explicit lifecycle, capability, ownership, compatibility and failure documentation.

Where research offered exact numerical targets such as sentence length or quickstart duration, this standard treats them as heuristics rather than universal laws.

---

# 3. Authority and reconciliation rules

When documentation sources disagree, use this precedence:

```text
CURRENT SOURCE + TESTS
        ↓
ACCEPTED N-BIO NORMATIVE CONTRACTS
        ↓
FINAL PHASE CLOSURE / ACCEPTANCE RECORDS
        ↓
NBIO_7E_CONTEXT_EXTENSION_JOURNAL.md
        ↓
THIS DOCUMENTATION STANDARD
        ↓
DEEP-RESEARCH DOCUMENTATION RECOMMENDATIONS
        ↓
OLDER OR SUPERSEDED NOTES
```

This ordering matters because the documentation research intentionally studied developer experience, not the final Kotlin implementation.

## 3.1 Research decides how to teach; source decides what exists

Never copy an API, lifecycle hook, manifest, packaging format, field or behaviour from the research report unless the current source proves it exists.

Examples of research-report implementation guesses that are **not** current N-BIO truth include:

- `contextmodule.json` or a module manifest;
- annotation-based module discovery;
- `onCreate`, `onDestroy` or `onSignalRejected` lifecycle hooks;
- `onAddEvidence(...)` as a ContextModule callback;
- a generic `publish(...)` mutation API;
- a universal `confidence=0.8` field;
- an `ActivityContextDefinition` permission type;
- a documented Randomness API;
- APK/AAR/JAR module distribution as an established authoring contract;
- runtime downloaded plug-ins;
- a security sandbox around module code.

If source inspection cannot verify a claim, the authoring docs must either omit it or label it explicitly as future/reserved design.

---

# 4. Audience model

The documentation must support at least these readers without forcing them through the same path.

## 4.1 Kotlin developer new to N-BIO

Needs:

- what a Context Module is;
- the shortest working example;
- registration and testing;
- what the module may read and publish.

Does not initially need:

- full state-space mathematics;
- Room persistence internals;
- arbitration implementation details.

## 4.2 Android developer new to probabilistic inference

Needs:

- a straightforward model of evidence, signals and uncertainty;
- concrete examples;
- clear separation between association, uncertainty and authority.

## 4.3 Statistics/ML developer new to Android/N-BIO

Needs:

- host lifecycle and deterministic replay rules;
- module state ownership;
- versioning/persistence constraints;
- exact signal semantics.

## 4.4 Experienced N-BIO contributor

Needs:

- exact SPI/reference contracts;
- source links;
- compatibility/version details;
- design rationale;
- migration records.

## 4.5 Third-party or occasional module author

Needs:

- supported extension points only;
- a reliable example to copy;
- permissions/read capabilities;
- TCK/testing path;
- failure/troubleshooting help;
- no requirement to understand unrelated N-BIO internals.

---

# 5. Progressive-disclosure model

Use four reading depths.

## 5.1 First 30 seconds: orientation

The reader should be able to answer:

- What is a Context Module?
- What can it broadly do?
- What can it not do?
- Where is the quickstart?
- Where is the exact reference?

This is a design heuristic, not a timed acceptance test.

## 5.2 First 5–10 minutes: first success

The reader should be able to create or adapt the smallest supported module example, register it through the real production integration path, run the relevant tests/TCK and see an unambiguous success condition.

The exact duration is a target, not a hard requirement. Correctness and predictability outrank speed.

## 5.3 Next ~30 minutes: mental model

The reader should be able to understand:

- Feature Definition vs Evidence vs Module vs Module State vs ContextSignal vs Core state;
- read capabilities;
- module memory/replay;
- chronology;
- signal validation/arbitration;
- versioning;
- missingness;
- scientific uncertainty boundaries.

## 5.4 Reference/deep-dive mode

An experienced reader should be able to retrieve one exact contract without rereading tutorial prose.

Deep design rationale and mathematics should remain available without obstructing task-oriented paths.

---

# 6. Information architecture

Use a Diátaxis-inspired separation, adapted for a small GitHub-first engineering project.

Do **not** create dozens of tiny pages merely to satisfy a taxonomy. Separate content by purpose, not mechanically by topic.

The author-facing Context Module documentation should live under a dedicated directory, recommended as:

```text
docs/n-bio-vnext/context-modules/
```

The initial structure should be:

```text
context-modules/
├── README.md                    # landing / orientation
├── QUICKSTART.md                # first working module
├── concepts/
│   ├── ARCHITECTURE.md          # Feature → Evidence → Module → Signal → Core
│   ├── MEMORY_AND_REPLAY.md
│   ├── CAPABILITIES_AND_SCOPE.md
│   ├── UNCERTAINTY_AND_EVIDENCE.md
│   └── VERSIONING_AND_COMPATIBILITY.md
├── how-to/
│   ├── DEFINE_A_FEATURE.md
│   ├── REGISTER_A_MODULE.md
│   ├── READ_EVIDENCE.md
│   ├── PUBLISH_A_SIGNAL.md
│   ├── PERSIST_MODULE_STATE.md
│   ├── VERSION_A_MODULE.md
│   └── TEST_A_MODULE.md
├── reference/
│   ├── README.md
│   ├── CONTEXT_MODULE.md
│   ├── CONTEXT_MODULE_PROVIDER.md
│   ├── CONTEXT_FEATURE_DEFINITION.md
│   ├── CONTEXT_READ_VIEW.md
│   ├── CONTEXT_SIGNAL.md
│   ├── TARGETS_AND_CAPABILITIES.md
│   └── FAILURE_AND_COMPATIBILITY.md
├── examples/
│   ├── README.md
│   └── source-linked examples
├── TROUBLESHOOTING.md
├── MIGRATION.md
└── GLOSSARY.md
```

This is a target structure, not permission to fabricate pages for APIs that do not exist. Merge pages if the real extension surface is smaller and a split would create needless fragmentation.

## 6.1 GitHub-first, site-ready

Write clean Markdown first.

Do not block documentation quality on:

- a static-site generator;
- Algolia or hosted search;
- a Stripe-style three-column layout;
- interactive code playgrounds;
- copy-button infrastructure.

A future site may render the same content. The information architecture and page semantics should survive presentation-layer changes.

---

# 7. Navigation rules

Navigation should follow developer intent, not package/class hierarchy.

Prefer:

- Build your first module
- Read evidence
- Publish a signal
- Keep module memory
- Version a module
- Fix a rejected signal

over:

- Domain context
- Extension V7E
- Module runtime internals
- Persistence entities

Every page should contain enough information scent that a scanning reader can decide quickly whether it is relevant.

## 7.1 Cross-linking without fragmentation

Good separation means one page has one job.

Bad fragmentation means one task requires reconstructing the workflow from unrelated pages.

Rules:

- a tutorial may link to reference for exact field semantics;
- a how-to may link to a concept page for deeper rationale;
- a reference page may link to a real implementation and TCK;
- troubleshooting should link directly to the corrective how-to/reference page;
- do not duplicate long explanations across page types;
- keep a short local explanation when a link-only answer would interrupt the task.

---

# 8. Landing-page standard

The landing page should answer the following before deeper detail:

1. What is a Context Module?
2. What problem does it solve?
3. What is the data flow?
4. What can a module read?
5. What can a module publish?
6. What can it not mutate?
7. How do I make my first one?
8. Where is the reference?

Recommended opening shape:

```text
# N-BIO Context Modules

A Context Module is a build-integrated N-BIO extension that owns its own
replayable learning state and publishes validated ContextSignals through a
least-privilege host interface.

[Build your first module] [Understand the architecture] [API/SPI reference]
```

Do not open with the history of N-BIO-7E or the mathematical model.

A small architecture diagram should appear near the top:

```text
Feature Definition
      ↓
Feature Evidence
      ↓
Context Module + module-owned memory
      ↓
ContextSignal
      ↓
N-BIO validation/arbitration
      ↓
combined inference state
```

---

# 9. Quickstart standard

The quickstart is a tutorial, not a reference dump.

It must:

- use a real source-verified module API;
- state prerequisites before step 1;
- use the actual registration mechanism;
- run the actual TCK/test path;
- produce a visible success condition;
- explain what the reader built after it works;
- link to the next concept/how-to pages.

The first example should be intentionally boring and structurally safe. Its job is to demonstrate the extension contract, not impressive biological inference.

Do not use a fake universal signal, hard-coded biological penalty or invented callback merely to shorten the example.

If the smallest real module still requires substantial boilerplate, improve the example presentation before considering a new convenience API. Documentation must not silently redesign runtime behaviour.

---

# 10. Tutorial vs how-to vs concept vs reference

## 10.1 Tutorial

Goal: learning through a successful end-to-end task.

Use:

- sequential steps;
- complete runnable code;
- expected output;
- minimal theory before success;
- explanation immediately after success.

## 10.2 How-to

Goal: solve one real author task.

Open with:

> This guide shows you how to ...

Assume the reader already understands the basic module model.

Do not retell the entire architecture.

## 10.3 Concept page

Goal: build a mental model.

Use prose, diagrams and small concrete examples.

Explain why the contract exists, but link to reference for exact signatures.

## 10.4 Reference page

Goal: exact retrieval.

Be predictable, structured and complete.

Reference pages should be boring in the best sense: readers should know where to find every fact.

---

# 11. Writing voice and tone

N-BIO documentation should sound like a technically competent colleague who respects the reader's intelligence.

Use:

- active voice;
- second person where the reader acts;
- present tense for behaviour;
- imperative mood for instructions;
- direct, literal language;
- basic English where a more elaborate phrase adds nothing;
- precise technical nouns when they carry necessary meaning;
- calm failure language.

Prefer:

> Add the provider to the production registry.

not:

> The provider should then be added to the production registry by the developer.

Prefer:

> N-BIO rejects signals with an unsupported target.

not:

> Signals with unsupported targets will be rejected.

## 11.1 Assume competence

Do not use patronising language.

Do not over-explain ordinary Kotlin concepts to experienced developers unless the page is explicitly introductory.

Explain N-BIO-specific semantics thoroughly, but use the simplest wording that remains correct.

## 11.2 Friendly means easy to read

Do not add conversational padding to make documentation sound friendly.

Friendly documentation is straightforward, well organised and easy to understand.

Prefer:

> Your module owns its derived state. N-BIO owns the combined inference state.

not:

> Think of your module as having its own little memory while N-BIO looks after the bigger picture.

The second version is less precise and takes longer to understand.

## 11.3 Do not translate simple explanations back into jargon

Do not write a plain explanation and then restate the same point in more technical language simply to sound rigorous.

If one straightforward sentence carries the full meaning, stop there.

Use a technical term when the reader needs that term to understand source, reference, logs, configuration or later documentation.

When a technical term is needed, introduce it inside the same clear explanation where possible.

For example:

> If your module has only been activated once, its learned relationship to its context target will remain uncertain.

This is preferable to splitting the same point into a friendly sentence followed by a separate "technically" sentence.

## 11.4 Contractions are allowed

Do not ban contractions mechanically.

`can't`, `don't` and `it's` are acceptable where they sound natural and remain unambiguous.

Reference contracts and warnings may use a slightly more formal tone when precision benefits.

## 11.5 Humour

Humour is optional and rare.

Do not use jokes in:

- API/reference semantics;
- safety/privacy warnings;
- compatibility requirements;
- errors/troubleshooting diagnoses;
- scientific uncertainty/causal language.

A small amount of personality in tutorials is acceptable if it never obscures the task.

---

# 12. Sentence and paragraph style

The research supports concise, scannable prose. Do not turn that into rigid word-count policing.

House rules:

- one main thought per sentence;
- one logical idea per paragraph;
- put the important sentence first;
- use the shortest natural wording that preserves the meaning;
- remove filler before adding explanation;
- split nested or clause-heavy sentences;
- use lists when the reader is comparing or executing multiple items;
- use prose when relationships or nuance matter.

Heuristics, not gates:

- many sentences will naturally fall around 15–20 words;
- many paragraphs will naturally contain 1–4 sentences;
- 5+ sentences is a prompt to ask whether the paragraph should be split;
- a long sentence is acceptable when splitting it would damage technical precision.

Do not add CI rules that fail documentation solely because a sentence exceeds a word count.

---

# 13. Heading standard

Headings are search anchors and scanning cues.

Use sentence case consistently.

Prefer headings that answer a likely developer question or name a concrete task.

Good:

- Publish a `ContextSignal`
- Give a module access to SessionDose
- Version module-owned state
- Fix a rejected signal
- Understand missing evidence

Weak:

- Signals
- Data
- Other considerations
- Details

Do not enforce an arbitrary maximum word count. Prefer concise headings, but preserve the key search term.

---

# 14. Lists and tables

Use numbered lists only when order matters.

Use bullets for sets, choices, prerequisites and non-sequential checks.

Use tables for structured comparisons such as:

- capability/read permissions;
- target support;
- version compatibility;
- stability/status;
- fields/properties;
- troubleshooting symptom → cause → fix.

Do not force nuanced explanation into a table just to shorten prose.

Avoid extremely wide tables in author-facing Markdown. Split them by concern when necessary.

---

# 15. Code-example standard

Examples are part of the product contract.

## 15.1 Source truth

Every example must come from or be verified against the current code at the documentation HEAD.

Never infer an API from:

- an old prompt;
- a design sketch;
- the deep-research report;
- a superseded journal entry.

## 15.2 Complete vs focused examples

Use two levels:

**Complete example**

- runnable/compilable;
- includes required imports/configuration;
- suitable for quickstarts and example modules;
- tested in CI where practical.

**Focused snippet**

- intentionally shows only the relevant lines;
- clearly labelled as an excerpt;
- links to the complete tested source.

Do not present a fragment as standalone copy/paste code.

## 15.3 Ellipses

Avoid `...` in code presented as runnable.

Ellipses are acceptable only in an explicitly labelled excerpt where omitted context is irrelevant and a complete-source link is provided.

## 15.4 Examples should prove the architecture

The example set should eventually demonstrate at least:

- a minimal module/provider;
- module-owned memory;
- an allowed read capability;
- signal publication through the real return/result contract;
- missing vs explicit false evidence;
- versioned module state;
- failure of an unsupported target;
- TCK usage.

Do not make every example use the same learner architecture.

## 15.5 CI

Where practical, documentation examples should be real Kotlin sources or fixtures compiled/tested by CI.

Prefer extracting snippets from tested source over maintaining duplicate handwritten code.

A documentation change that makes an example disagree with tested source is a documentation bug.

---

# 16. Reference-page template

Each public/SPI reference page should use a predictable shape.

```text
# <Symbol>

<one-sentence purpose>

API stability: <...>
Capability status: <...>
Empirical status: <... if scientifically relevant>
Since: <version/phase if known>

## Signature

## What it represents

## Fields / methods

## Preconditions and invariants

## Lifecycle and chronology

## Read/write authority

## Failure behaviour

## Version and compatibility rules

## Minimal example

## Implementations / examples

## View source

## See also
```

Omit irrelevant sections rather than filling them with boilerplate.

Generated Dokka/KDoc may complement this reference. Generated symbol pages alone are not sufficient developer documentation.

---

# 17. Three separate status axes

Do not collapse software maturity, implementation support and scientific calibration into one badge.

## 17.1 API stability

Describes compatibility expectations for an author-facing software contract.

Recommended labels:

- **STABLE**: covered by a documented compatibility policy;
- **EXPERIMENTAL**: author-facing but may change materially;
- **DEPRECATED**: still present, replacement/removal path documented;
- **INTERNAL**: not an extension contract.

Do not define STABLE as "guaranteed never to break".

Compatibility must say what is protected, where relevant:

- source compatibility;
- binary compatibility;
- protocol/wire compatibility;
- persisted-state compatibility;
- behavioural compatibility.

## 17.2 Capability status

Describes whether a protocol surface actually affects current N-BIO behaviour.

Recommended labels:

- **IMPLEMENTED**;
- **PROTOCOL-ONLY**;
- **RESERVED**;
- **LATER PHASE**;
- **UNSUPPORTED**.

Examples from the accepted 7E architecture:

- `SYSTEMIC_TRANSIENT_STATE`: IMPLEMENTED in the v1 context temporal candidate;
- `OBSERVATION_VARIANCE`: IMPLEMENTED;
- `LOCAL_TRANSIENT_STATE`: PROTOCOL-ONLY in 7E, with envelope/validation/arbitration but no evolving local latent consumer;
- `EQUIPMENT_TRANSLATION`: LATER PHASE, N-BIO-7F;
- `CAPABILITY_CONDITIONING`: LATER PHASE, N-BIO-7G.

Always verify current source/status before publishing these examples in final author docs.

## 17.3 Empirical/scientific status

Use only where scientific interpretation is relevant.

Recommended labels:

- **STRUCTURALLY VALIDATED**;
- **CALIBRATION PENDING**;
- **EMPIRICALLY SUPPORTED**;
- **REJECTED / NO PREDICTIVE BENEFIT** where formally established.

For current 7E temporal/context claims, PD-003 remains the relevant calibration quarantine.

Software stability does not imply scientific truth.

---

# 18. Probabilistic and scientific language

Explain probabilistic and scientific concepts in straightforward language. Do not make the wording more elaborate merely because the underlying model is complex.

Use the simplest technically correct sentence.

Prefer:

> If your module has only been activated once, its learned relationship to its context target will remain uncertain.

Avoid:

> Your module has seen only one independent episode, so its learned relationship remains uncertain.

when the extra terminology does not add information the reader needs at that point.

Also avoid immediately restating a clear sentence as:

> Technically, the module remains `PARTIALLY_LEARNED` and publishes a broad posterior variance.

If the reader later needs `PARTIALLY_LEARNED` or posterior variance to understand an API field, status value, log or mathematical contract, explain that term there in equally direct language.

Do not replace real concepts with a generic word such as `confidence` when it changes the meaning.

Keep these distinctions accurate where they matter:

- extraction confidence;
- evidence support;
- posterior uncertainty;
- empirical calibration;
- model/prediction authority.

They are not interchangeable, but they do not all need to be introduced every time uncertainty is mentioned.

## 18.1 Prefer direct explanations over terminology displays

Do not write documentation to demonstrate that N-BIO uses sophisticated statistics.

Write it so the developer knows what happens and what they need to do.

For example:

> A module with little independent evidence should make only a weak contribution to the combined state.

is usually better introductory documentation than:

> A prior-dominated module with broad posterior variance is precision-weighted during arbitration.

The second sentence belongs only where the exact arbitration or posterior contract is being explained.

## 18.2 Association is not causation

Prefer:

> For this user, illness reports have tended to occur alongside lower predicted performance.

Do not write:

> Illness caused an 8% performance loss.

unless a separately justified causal model establishes that claim.

## 18.3 Plain language before equations

When mathematics materially helps:

1. explain the idea in words;
2. show the equation;
3. define every symbol;
4. give a small numerical or behavioural example where useful.

Do not hide essential contract mathematics merely because it is mathematical.

Do not put dense equations in the quickstart.

## 18.4 No fake universal confidence or action threshold

Do not document patterns like:

```text
confidence > 0.8 → reduce training
```

unless a real accepted contract explicitly defines them.

7E inference and V8 policy remain separate.

---

# 19. Extension-specific terminology rules

Use current implementation language precisely.

## 19.1 Do not call modules sandboxed

Current 7E modules are build-integrated code running under host-controlled contracts. They are **capability-restricted / least-privilege**, not a security sandbox.

Say:

> Modules receive a host-owned typed read view and cannot obtain arbitrary DAOs through the SPI.

Do not say:

> Modules run in a sandbox.

## 19.2 Do not imply arbitrary runtime plug-ins

Current registration is build-time and explicit.

There is no:

- downloaded executable module marketplace;
- reflection-based discovery contract;
- `ServiceLoader` contract;
- arbitrary DEX loading.

Future distribution mechanisms require a separate authorised design.

## 19.3 Explain the real lifecycle

Current module work is an I/O-free deterministic transform under a host-owned lifecycle.

The host owns:

- ordering;
- coroutine/dispatcher lifetime;
- cancellation;
- transactions/persistence;
- capability grants;
- validation;
- replay chronology.

The module owns:

- its learner logic;
- its derived state format/codec;
- its declared feature consumption;
- its validated signal output.

Never invent Android-style lifecycle callbacks if the SPI does not contain them.

---

# 20. Troubleshooting standard

Troubleshooting should be organised primarily by **symptom/error**, because that matches how developers search under pressure.

Recommended entry template:

```text
## My ContextSignal was rejected

What this means

Likely causes

1. ...
2. ...

How to verify

How to fix

Related reference
```

Prioritise real failure states found in validators/tests/logs.

Do not invent error codes merely to make the page look complete.

Useful current categories likely include:

- provider/registry incompatibility;
- duplicate identity;
- stale protocol/model/config/state codec;
- denied read capability;
- wrong feature ownership;
- wrong target/scope;
- invalid or non-finite signal values;
- no eligible evidence;
- missingness misunderstood as false;
- replay/state mismatch.

Verify exact names/messages against source before publishing.

---

# 21. Developer-journey acceptance tests

The documentation set is not complete merely because every symbol has a page.

Test these journeys manually during the authoring mission.

## Journey A: create my first module

The reader can:

- understand the extension model;
- create/adapt a real module;
- register it;
- run the TCK/tests;
- recognise success.

## Journey B: look up `ContextSignalV1`

The reader can directly find:

- fields;
- semantics;
- supported targets;
- validation;
- versioning;
- source/example links.

## Journey C: fix a rejected signal

The reader can start from the symptom/error and reach a concrete fix without reading the whole architecture guide.

## Journey D: keep memory across sessions

The reader can understand module-owned derived state, codec/version requirements, persistence ownership and replay.

## Journey E: determine whether SessionDose may be read

The reader can find the real read capability and chronology/permission semantics without guessing from availability.

## Journey F: evolve a module safely

The reader can determine when to advance:

- config identity/payload;
- model version;
- state schema/codec;
- feature schema;
- signal/protocol compatibility.

The reader should understand when replay is required instead of silent reinterpretation.

---

# 22. Source discoverability

Author-facing reference pages should link to the actual source symbol where practical.

Also link to:

- a representative implementation;
- the relevant TCK/test fixture;
- the concept/how-to page;
- migration notes where applicable.

Do not force a developer to search the entire repository for a concrete implementation of an interface documented as extensible.

Deep rationale may link back to:

- `NBIO_7E_CONTEXT_EXTENSION_JOURNAL.md`;
- `CONTEXT_MODULE_ARCHITECTURE.md`;
- `NBIO_7E_STATE_CONTEXT_CONTRACT.md`;
- accepted closure records.

The rough journal remains historical evidence, not the final author-facing guide.

---

# 23. Stable extension surface vs internals

Do not document every 7E class as if it were public SPI.

For every symbol considered for the author-facing docs, classify it as one of:

```text
AUTHOR SPI
AUTHOR SUPPORT / TESTING
PUBLIC SEMANTIC TYPE
INTERNAL HOST IMPLEMENTATION
INTERNAL PERSISTENCE
DEVELOPER ACCEPTANCE / DIAGNOSTIC
```

Only the first three categories belong in the normal extension reference.

Internal host algorithms such as arbitration implementation details may have concept/design notes, but authors should not be encouraged to depend on internal classes.

Room entities/DAOs, physical acceptance runners and developer-screen plumbing are not extension APIs merely because they are visible Kotlin classes.

---

# 24. Documentation QA

Every author-facing documentation change must satisfy the following.

## 24.1 Accuracy

- Symbol names match current source.
- Signatures/fields match current source.
- Lifecycle rules match tests/contracts.
- Supported targets/capabilities match current target policy.
- Scientific status matches PD/closure records.
- No future phase is described as implemented.

## 24.2 Examples

- Full examples compile/run where practical.
- Expected output is tested or directly source-backed.
- Focused snippets link to complete examples.
- No example smuggles in an invented convenience API.

## 24.3 Structure

- The page has one primary job.
- Important information appears early.
- Headings are descriptive/searchable.
- Long tasks are not fragmented across unnecessary pages.
- Related pages are cross-linked.

## 24.4 Language

- Active/direct voice predominates.
- The simplest technically correct wording is used.
- N-BIO-specific jargon appears only where it helps the reader understand the real interface or model.
- Plain explanations are not immediately duplicated in more technical wording.
- Terminology is consistent.
- Association/causation language is correct.
- Uncertainty types are not collapsed into generic confidence.

## 24.5 Version/status

Where relevant, the page states separately:

- API stability;
- capability status;
- empirical/scientific status.

## 24.6 Accessibility/rendering

- tables have clear headers;
- diagrams have text equivalents or explanatory prose;
- code fences specify language;
- headings form a sensible hierarchy;
- author docs remain readable as plain GitHub Markdown.

---

# 25. Documentation CI policy

Treat author documentation as product code where practical.

Strict CI candidates:

- internal Markdown links;
- referenced repository paths/symbol fixtures where tooling exists;
- compiled example modules;
- TCK example execution;
- Markdown syntax/format checks;
- duplicate/broken local anchors;
- generated/reference drift checks if added later.

External links should not necessarily fail every ordinary Android build because temporary network failures and rate limits are outside repository control. Prefer scheduled or retry-aware external-link validation.

Do not add a prose linter that mechanically rejects:

- contractions;
- sentences above 20 words;
- paragraphs above an arbitrary sentence count.

Review those as clarity heuristics, not syntax errors.

---

# 26. Page templates

## 26.1 Quickstart

```text
# Build your first Context Module

<one-sentence outcome>

## Before you start
<real prerequisites>

## 1. <first action>
<instruction + tested code>

## 2. <next action>
...

## Verify it
<real TCK/test command + expected result>

## What you just built
<short architecture explanation>

## Next steps
<2–4 relevant links>
```

## 26.2 How-to

```text
# <Do a concrete task>

This guide shows you how to <goal>.

## Before you start

## <Action-oriented steps>

## Verify the result

## Common failure

## See also
```

## 26.3 Concept page

```text
# <Concept name>

<plain-English thesis>

## Why this exists

## How it fits the data flow

## Concrete example

## Important boundaries

## Technical detail

## See also
```

## 26.4 Reference

Use the template in section 16.

## 26.5 Troubleshooting

Use the symptom template in section 20.

## 26.6 Migration

```text
# Migrate <old> to <new>

## What changed

## Who is affected

## Why replay/migration is required

## Change your code
<before/after diff>

## Handle existing module state

## Verify compatibility

## See also
```

---

# 27. Terminology and glossary policy

Use one preferred term for one concept.

The glossary should include N-BIO-specific meanings for at least:

- Context Feature Definition;
- Context Feature Evidence;
- Context Module;
- module-owned state / memory;
- ContextSignal;
- read capability;
- target;
- scope;
- evidence support;
- independent episode;
- prior-dominated;
- posterior uncertainty;
- replay;
- invalidation;
- protocol-only;
- SHADOW / candidate authority;
- empirical calibration.

Do not define common programming terms unnecessarily unless N-BIO uses them unusually.

---

# 28. Documentation anti-patterns to reject

Do not ship author docs that exhibit these patterns:

1. Reference dump with no first-success path.
2. Huge architecture essay before runnable code.
3. Runnable-looking snippets that do not compile.
4. Hidden prerequisites.
5. API names copied from a design/research report rather than source.
6. Stable/internal boundaries left implicit.
7. Software stability confused with scientific validation.
8. Protocol-only targets described as effectful.
9. Missing evidence described as false.
10. Capability availability described as permission.
11. Build-integrated modules described as sandboxed runtime plug-ins.
12. Direct Core mutation implied where only ContextSignal publication is allowed.
13. Tutorial, concept and reference content duplicated until they disagree.
14. Deep navigation with weak cross-links.
15. Vague failure wording such as "it should work".
16. Unversioned examples or migration assumptions.
17. Documentation that requires reading implementation internals for routine author tasks.
18. Simple ideas rewritten in ornate, conversational or unnecessarily statistical language.
19. A clear explanation immediately repeated as a "technical" translation that adds no new information.

---

# 29. Definition of documentation completion

The post-7E authoring mission is complete only when:

1. the rough 7E journal has been reconciled against final source;
2. superseded decisions are not presented as current API;
3. the stable/author-facing extension surface is explicitly classified;
4. the landing page gives a fast global mental model;
5. the quickstart uses real tested code;
6. task-oriented how-tos cover the core author workflows;
7. concept pages explain the architecture without becoming reference dumps;
8. the public/SPI reference is complete for supported author surfaces;
9. target/read capability support is explicit;
10. troubleshooting is symptom-oriented and based on real failures;
11. examples are source-linked and CI-backed where practical;
12. versioning/migration semantics are documented;
13. the three status axes are kept separate;
14. Journeys A–F can be completed from the docs;
15. no normal N-BIO runtime/product behaviour changes are introduced merely to make documentation easier;
16. docs are correct at the exact final documentation HEAD.

---

# 30. Final writing test

Before publishing a page, ask:

**Can a scanning reader tell within seconds why this page matters?**

**Can a task-oriented reader act without reading unrelated theory?**

**Can a concept-oriented reader understand why the contract exists?**

**Can an expert retrieve the exact semantics without reverse-engineering source?**

**Is every sentence as simple as it can be without losing technical meaning?**

**Does every technical claim match the actual implementation?**

If the final answer to either of the last two questions is not demonstrably yes, the page is not ready.
