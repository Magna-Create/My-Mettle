# UI/ML Lab sync policy

> **Status:** branch-governance contract for the long-lived parallel development line.

## Branch roles

```text
agent/n-bio-vnext-inference
    = biological/backend upstream development

agent/ui-ml-lab
    = parallel product UI/ML experimentation
```

The Lab is downstream of coherent N-BIO checkpoints but is not a mirror that must contain every upstream commit immediately.

## Initial branch point

LAB-0 was created from the live remote `agent/n-bio-vnext-inference` commit:

`ec1406fcaa371241974031a4c2740d433a9e8f55`

This matched the prompt-preparation SHA and had a successful Android CI run before the Lab branch was created.

## Sync rule

Periodically sync coherent N-BIO checkpoints. Do **not** continuously pull every N-BIO commit merely because it exists.

Preferred sync points include:

- after LAB-0, when a later phase genuinely needs newer upstream behaviour;
- after meaningful ML infrastructure milestones where newer N-BIO contracts are required;
- before LAB-5 shared database/domain work;
- before V8 integration;
- whenever branch drift becomes materially risky to merge/integration safety.

Before each sync:

1. fetch/inspect the live N-BIO head;
2. confirm it represents a coherent checkpoint rather than an incomplete intermediate state;
3. record the exact upstream SHA being consumed;
4. review database/schema, build configuration and shared contracts likely to conflict;
5. run the normal and Lab acceptance gates after the sync.

## History policy

Once `agent/ui-ml-lab` is pushed and collaborative work exists, avoid history-rewriting/rebase workflows by default.

Prefer explicit, reviewable upstream merge/sync commits. A future task may deliberately choose another safe method, but it must state why history rewriting is appropriate and confirm collaboration/state safety first.

Never reset or rewrite `agent/n-bio-vnext-inference` to make a Lab sync easier.

Never merge Lab work back into N-BIO merely as part of a sync. Cross-line integration is a separate deliberate operation.

## Sync ledger

Record every consumed upstream checkpoint here.

| Sync | Upstream N-BIO SHA | Lab integration point | Reason | Result |
| --- | --- | --- | --- | --- |
| `SYNC-000` | `ec1406fcaa371241974031a4c2740d433a9e8f55` | LAB-0 branch creation | Establish parallel UI/ML Lab from latest coherent N-BIO Native state. | Initial branch point; no merge required. |

Add later sync rows in order (`SYNC-001`, `SYNC-002`, …). Do not replace the original SHA.

## LAB-5 cross-branch database gate

Canonical equipment persistence requires an explicit cross-branch stop:

1. stop Lab feature development;
2. refresh live N-BIO head;
3. refresh live Lab head;
4. determine the current Room version on both lines;
5. agree one shared equipment domain contract;
6. create the next legitimate Room migration from the live version;
7. isolate shared schema/domain changes from camera UI, Qwen/runtime code, Lab mocks and Figma UI work;
8. carry the same canonical database/domain contract across both lines;
9. verify schema identity/compatibility;
10. resume Lab feature work only after alignment.

Do not pre-name the future Room version. At LAB-0 the live schema is 15; the LAB-5 migration must use whatever next version is legitimate at that future checkpoint.

This policy exists to prevent parallel development from turning equipment persistence into Room merge hell.
