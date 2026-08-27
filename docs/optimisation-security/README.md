# Optimisation & Security

This directory is the maintenance ledger for My Mettle Native optimisation, security hardening, pruning and later UI cleanup passes.

The goal is to keep maintenance work human-readable and version-specific so a future regression can be traced to a small, named change set rather than reconstructed from branch history.

## File convention

Each maintenance pass gets its own document tied to the app version it started from.

- `OPTIMISATION_CHANGELOG_<version>.md` — code pruning, architecture cleanup, performance maintenance and security/privacy hardening.
- `UI_PASS_<version>.md` — visual/interaction cleanup carried out after the optimisation pass.

Do not append unrelated future work to an old version file. Create a new versioned file instead.

## Every changelog should record

1. The app version and version code.
2. The exact baseline commit/ref.
3. The maintenance branch.
4. The intended scope and explicit exclusions.
5. Each applied change and its commit SHA.
6. Validation performed after the changes.
7. Known deferred items.
8. Rollback guidance and any behavioural/data implications.

## Maintenance rule

Optimisation/security work should remain mechanically separable from UI work wherever practical. A visual or interaction change should not be hidden inside a pruning/security commit merely because both happened during the same maintenance window.

For alpha24, the optimisation/security pass precedes a separate UI pass. The UI pass should therefore receive its own `UI_PASS_0.1.0-alpha24.md` file rather than modifying the optimisation changelog.
