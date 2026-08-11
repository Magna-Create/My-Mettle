# N-BIO-5.1 — biological observability and lifecycle

## Purpose

N-BIO-3–5 made programme intent independent, inference recomputable and workout resolution
target-driven. N-BIO-5.1 makes those systems inspectable through the debug application before their
outputs are allowed to become opaque product behaviour.

Room is version 9. Native remains under the destructive-development migration policy while Lite is
the authoritative daily workout source.

## Developer surface

Debug builds expose **Settings → Developer → Biological developer tools**. The screen shows:

- Room and runtime-reference counts;
- programme targets and mode-level session constraints;
- every resolver candidate, its target coverage and its selected/rejected decision;
- generated prescriptions and the evidence behind each suggested load;
- the latest inference run, evidence count, stimulus estimates, muscle states and same-profile
  performance anchors;
- a diagnostic JSON export containing derived/resolver state, not the complete backup;
- a confirmed reset for the disposable Native database.

## Explicit inference lifecycle

Full-history replay remains user-started. **Recompute biological state** launches one process-level
task and exposes its status through the developer screen and an app-wide task lozenge. Navigation
does not cancel the replay and ordinary set entry/navigation never starts it silently.

The task remains deliberately replaceable. It still uses the N-BIO-4 v0 engines and does not add a
recovery, hypertrophy or cross-exercise transfer equation.

## RIR evidence

Working-set entry now accepts optional RIR from 0–10. It is persisted as raw evidence with
`effortSource = user_reported_rir`. RIR raises the confidence of the current stimulus estimator; it
does not yet drive an invented effort-response curve.

## Load provenance

Every Native-generated session exercise snapshots:

```text
prescribedLoad
prescribedLoadEvidenceSource
prescribedLoadEvidenceSetId
prescribedLoadInferenceRunId
prescribedLoadAnchor
generatedByModelVersion
```

The order is strict:

1. latest recomputed same-execution-profile anchor;
2. latest completed raw set for that same execution profile;
3. no load suggestion.

The final load may differ from the anchor only because it is conformed to the selected execution
profile's allowed values/increment/minimum/maximum.

## Exercise substitution

An active, unstarted session exercise can be replaced. Options are ranked by confidence-weighted
coverage of the original session targets and use the replacement exercise's default execution
profile.

The replacement receives its own same-profile load suggestion using the order above. The outgoing
exercise's load is never copied or used as evidence. If the replacement profile has no evidence,
the load remains blank and the UI states that no defensible suggestion exists.

Once a set is logged, substitution is blocked. This keeps raw performed evidence authoritative.
Mode changes preserve an existing user substitution while updating the session's dose budget.

## Diagnostic boundary

The diagnostic JSON includes reference metadata, programme targets/constraints, resolver
candidates, generated prescription provenance and derived inference outputs. It excludes profile
display name, notes, setup media and the full raw workout-history backup.
