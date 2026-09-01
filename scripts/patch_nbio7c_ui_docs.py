from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"anchor not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# Consolidated developer action UI.
# ---------------------------------------------------------------------------
path = "app/src/main/java/dev/kian/mymettle/ui/BiologyDeveloperScreen.kt"
replace_once(
    path,
    '''    val liteBackupLauncher = rememberLauncherForActivityResult(\n''',
    '''    val nBio7CCapabilityExportLauncher = rememberLauncherForActivityResult(\n        ActivityResultContracts.CreateDocument("application/json"),\n    ) { uri ->\n        if (uri == null) return@rememberLauncherForActivityResult\n        scope.launch {\n            runCatching {\n                val json = viewModel.nBio7CCapabilityJson()\n                withContext(Dispatchers.IO) {\n                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }\n                        ?: error("Android could not open the selected N-BIO-7C report file.")\n                }\n            }.onSuccess { viewModel.markNBio7CCapabilityExported() }\n                .onFailure(viewModel::reportError)\n        }\n    }\n    val liteBackupLauncher = rememberLauncherForActivityResult(\n''',
)

# Existing long actions should visibly lock while 7C is running too.
p = Path(path)
text = p.read_text()
text = text.replace(
    '!state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning &&\n                                state.task.phase != BiologyTaskPhase.RUNNING,',
    '!state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning &&\n                                !state.nBio7CCapabilityRunning && state.task.phase != BiologyTaskPhase.RUNNING,',
)
text = text.replace(
    '!state.nBio7BAcceptanceRunning && !state.adaptiveInferenceRunning,',
    '!state.nBio7BAcceptanceRunning && !state.adaptiveInferenceRunning && !state.nBio7CCapabilityRunning,',
)
p.write_text(text)

replace_once(
    path,
    '''                item {\n                    DebugCard("N‑BIO‑6 device acceptance") {\n''',
    '''                item {\n                    DebugCard("N-BIO-7C capability acceptance") {\n                        Text(\n                            "One consolidated structural/pre-validation action for loaded holds, duration-only and repeated-contraction capability. Runs synthetic latent-truth recovery, audits installed Room14 history, compares Adaptive Sparse with Dense, verifies SHADOW persistence/delete/replay and Native backup safety. PD-001 keeps empirical accuracy explicitly pending where longitudinal evidence is insufficient.",\n                            color = MaterialTheme.colorScheme.onSurfaceVariant,\n                        )\n                        Button(\n                            onClick = viewModel::runNBio7CCapabilityAcceptance,\n                            enabled = !state.nBio7CCapabilityRunning && !state.nBio7BAcceptanceRunning &&\n                                !state.adaptiveInferenceRunning && !state.nBio6VerificationRunning &&\n                                !state.nBio6LiteVerificationRunning && state.task.phase != BiologyTaskPhase.RUNNING,\n                            modifier = Modifier.fillMaxWidth(),\n                        ) {\n                            if (state.nBio7CCapabilityRunning) {\n                                CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))\n                                Text("Running N-BIO-7C capability acceptance…")\n                            } else {\n                                Text("Run N-BIO 7C Capability Acceptance")\n                            }\n                        }\n                        state.nBio7CCapabilityProgress?.let { progress ->\n                            DebugLine(\n                                "Progress",\n                                if (progress.totalGroups > 0) "${progress.completedGroups}/${progress.totalGroups}" else "Preparing",\n                            )\n                            Text(progress.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                        }\n                        state.nBio7CCapabilityReport?.let { report ->\n                            HorizontalDivider()\n                            DebugLine("Structural pre-validation", report.structuralVerdict.storageValue)\n                            DebugLine("Empirical accuracy", report.empiricalAccuracyStatus.storageValue)\n                            DebugLine("Overall", report.overallVerdict.storageValue)\n                            DebugLine("Room", "v${report.roomSchemaVersion}")\n                            DebugLine("Synthetic", if (report.synthetic.passed) "PASS" else "FAIL")\n                            DebugLine("Raw evidence", if (report.rawEvidenceUnchanged) "UNCHANGED" else "CHANGED")\n                            DebugLine("Prescriptions", if (report.prescriptionStateUnchanged) "UNCHANGED" else "CHANGED")\n                            DebugLine("BENCHMARK authority", if (report.benchmarkAuthorityUnchanged) "UNCHANGED" else "CHANGED")\n                            DebugLine("Native backup", if (report.backupRoundTrip.passed) "PASS" else "FAIL")\n                            DebugLine("Total runtime", "${report.totalElapsedMillis} ms")\n                            report.familyReports.forEach { family ->\n                                HorizontalDivider()\n                                Text(family.family.storageValue, fontWeight = FontWeight.SemiBold)\n                                DebugLine("Structural", family.structuralVerdict.storageValue)\n                                DebugLine("Empirical", family.empiricalAccuracyStatus.storageValue)\n                                DebugLine("Profiles", family.definedExecutionProfileVersions.toString())\n                                DebugLine("Real evidence", "${family.currentEligibleObservations} obs · ${family.independentSessions} sessions")\n                            }\n                            report.profileReports.forEach { profile ->\n                                HorizontalDivider()\n                                Text("${profile.label} · ${profile.side}", fontWeight = FontWeight.SemiBold)\n                                DebugLine("Family", profile.family.storageValue)\n                                DebugLine("Evidence", "${profile.eligibleObservations} obs · ${profile.independentSessions} sessions")\n                                DebugLine("Empirical", profile.empiricalAccuracyStatus.storageValue)\n                                DebugLine("Persist/reload", profile.persistReloadEquivalent?.let { if (it) "PASS" else "FAIL" } ?: "n/a")\n                                DebugLine("Delete-derived", profile.deleteDerivedConfirmed?.let { if (it) "PASS" else "FAIL" } ?: "n/a")\n                                DebugLine("Full replay", profile.fullReplayEquivalent?.let { if (it) "PASS" else "FAIL" } ?: "n/a")\n                                profile.numericalFailure?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }\n                                profile.limitations.forEach { limitation ->\n                                    Text("• $limitation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                                }\n                            }\n                            OutlinedButton(\n                                onClick = {\n                                    runCatching { viewModel.nBio7CCapabilityJson() }\n                                        .onSuccess {\n                                            nBio7CCapabilityExportLauncher.launch(\n                                                "my-mettle-n-bio-7c-capability-${Instant.now().epochSecond}.json",\n                                            )\n                                        }\n                                        .onFailure(viewModel::reportError)\n                                },\n                                modifier = Modifier.fillMaxWidth(),\n                            ) { Text("Export N-BIO-7C capability JSON") }\n                        }\n                    }\n                }\n\n                item {\n                    DebugCard("N‑BIO‑6 device acceptance") {\n''',
)

# ---------------------------------------------------------------------------
# Normative contract: PD-001 amendment without editing the register itself.
# ---------------------------------------------------------------------------
path = "docs/n-bio-vnext/NON_DYNAMIC_CAPABILITY_CONTRACT.md"
replace_once(
    path,
    'Status: **N-BIO-7C ACTIVE — candidate models pre-registered before implementation.**',
    'Status: **N-BIO-7C ACTIVE — structural/pre-validation implementation; empirical calibration quarantined by PD-001.**',
)
replace_once(
    path,
    '''## 15. Product boundary\n\nN-BIO-7C remains SHADOW/developer inference foundation. `BENCHMARK_V0` remains normal product authority. No normal workout/prescription/order/prefill or normal-user capability display changes are authorised.\n\nN-BIO-7D SetDemand/dose work and every later biological/translation/conditioning/V8 feature remain strictly out of scope.\n''',
    '''## 15. Product boundary\n\nN-BIO-7C remains SHADOW/developer inference foundation. `BENCHMARK_V0` remains normal product authority. No normal workout/prescription/order/prefill or normal-user capability display changes are authorised.\n\nN-BIO-7D SetDemand/dose work and every later biological/translation/conditioning/V8 feature remain strictly out of scope while this 7C mission is active.\n\n## 16. PD-001 amended closure rule\n\n`POSTPONED_DEVELOPMENT.md` entry **PD-001** is authoritative for the empirical-status boundary. N-BIO-7C may close its initial architecture mission when the structural/pre-validation gate is complete even if current personal history cannot support meaningful calibration of these three family-specific numerical models.\n\nThe following remain hard, non-deferrable closure requirements: family/unit/semantic/laterality correctness; immutable identities; lower-bound and capability/action-policy separation; dynamic-state invariants; synthetic latent-truth recovery; adversarial and boundary fixtures; uncertainty-aware extrapolation; Adaptive-Sparse stability/fidelity against Dense; persistence/reload/delete-derived replay; Native backup/Room14 integrity; raw/prescription/product-authority invariance; typed fail-closed numerical behaviour; documentation and exact-head CI.\n\nWhere semantically valid longitudinal history is insufficient, the family/profile empirical result is **`EMPIRICAL_ACCURACY_PENDING`** or **`NOT_EVALUATED_REAL_HISTORY`**, never `PASS`. Synthetic latent-truth validation proves implementation against the declared candidate mathematics; it does not prove real-human accuracy of the equations, priors, process volatility, slack/noise assumptions or far-domain extrapolation.\n\nUntil PD-001 itself is later closed, its downstream quarantine applies unchanged: 7C numerical outputs cannot become normal-user authority/prefill/prescription truth; later phases may consume contracts/interfaces but may not infer empirical validation from a finite posterior; downstream 7C-family scientific claims remain synthetic/structural or explicitly empirical-pending; no later phase may tune itself around sparse apparent 7C accuracy; and raw evidence/model versions remain replayable for later immutable-candidate refinement.\n\nN-BIO-7D is not authorised until this amended 7C structural closure is complete.\n''',
)

# ---------------------------------------------------------------------------
# README truthfulness.
# ---------------------------------------------------------------------------
path = "docs/n-bio-vnext/README.md"
replace_once(
    path,
    '''- [`DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md`](./DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md) — N-BIO-7B evidence/coordinate contract, frozen Candidate-v1 record, chronological validation, SHADOW persistence and Candidate-v2 development evidence. Read it together with the adaptive-inference supplement for current capability/policy and solver semantics.\n''',
    '''- [`DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md`](./DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md) — N-BIO-7B evidence/coordinate contract, frozen Candidate-v1 record, chronological validation, SHADOW persistence and Candidate-v2 development evidence. Read it together with the adaptive-inference supplement for current capability/policy and solver semantics.\n- [`NON_DYNAMIC_CAPABILITY_CONTRACT.md`](./NON_DYNAMIC_CAPABILITY_CONTRACT.md) — normative N-BIO-7C loaded-hold, duration-only and repeated-contraction capability contract, mathematical candidates, evidence semantics, extrapolation and solver/persistence rules.\n- [`POSTPONED_DEVELOPMENT.md`](./POSTPONED_DEVELOPMENT.md) — explicit deferred-validation register. PD-001 permits 7C structural closure with empirical accuracy pending while quarantining those numerical outputs from downstream/product authority.\n''',
)
replace_once(
    path,
    '''7B.X    ACTIVE — Adaptive Inference Architecture Consolidation; one consolidated mission, not a ladder of invented 7B.5/7B.6 phases\n7C      NOT STARTED\n''',
    '''7B.X    complete — Adaptive Sparse selected for Candidate-v2 forward inference; Dense retained oracle; Conditional Laplace rejected production\n7C      ACTIVE — loaded-hold, duration-only and repeated-contraction structural/pre-validation mission; PD-001 empirical accuracy pending where evidence is insufficient\n''',
)
replace_once(
    path,
    '''The valid same-mathematics Candidate-v2 solver comparison currently consists of:\n\n```text\nDENSE full-support trend-grid reference\nvs\nCONDITIONAL-LAPLACE approximation\n```\n\nBoth receive the same frozen-v1 proposal when directly compared. Generic dense-sequential-grid, adaptive-sparse-grid, low-rank-screen and Gaussian sigma-point implementations also exist as solver-architecture substrates, but they are not to be misreported as Candidate-v2-equivalent solvers unless a mathematical adapter makes their state/transition/likelihood problem genuinely identical.\n\nThe consolidated **N-BIO Adaptive Inference Acceptance** developer action is the current installed-Room14 evidence gate. It combines Candidate-v2 retrospective/current-state solver evidence with a separately labelled synthetic same-problem solver-substrate benchmark and exports one privacy-bounded JSON. Historical results remain development evidence; fresh future workouts are stronger confirmatory evidence.\n''',
    '''The corrected N-BIO-7B.X same-mathematics solver decision is frozen: **Adaptive Sparse** is selected for forward Candidate-v2 development inference, **Dense** remains the deterministic high-fidelity oracle/reference, and **Conditional Laplace** is rejected as the production Candidate-v2 solver after solver-specific numerical instability. This is an inference-algorithm decision, not product-authority promotion of Candidate-v2 mathematics.\n\nThe completed **N-BIO Adaptive Inference Acceptance** remains the physical Room14 evidence record for that 7B.X decision. N-BIO-7C consumes the architecture rather than reopening the tournament.\n''',
)
replace_once(
    path,
    '## Product and safety authority during 7B.X',
    '## Product and safety authority during N-BIO-7C',
)
replace_once(
    path,
    '- N-BIO-7C exercise-family capability work is not authorised by 7B.X.\n',
    '- N-BIO-7C outputs remain SHADOW/developer-only and are empirically quarantined by PD-001 where real history is insufficient.\n',
)
replace_once(
    path,
    '''For inference work during N-BIO-7B.X:\n\n```text\nPLAN.md\n→ ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md\n→ relevant CORE_MODEL_DETAIL.md section where not superseded\n→ DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md for current 7B evidence/model history\n→ PRODUCT_ROADMAP_GATES.md whenever crossing a product/research collaboration gate\n→ relevant research guide\n→ targeted raw research only when exact evidence/equations/platform wording are needed\n→ current source code and immutable model/config identities\n```\n''',
    '''For current N-BIO-7C inference work:\n\n```text\nPLAN.md\n→ ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md\n→ NON_DYNAMIC_CAPABILITY_CONTRACT.md\n→ POSTPONED_DEVELOPMENT.md / PD-001\n→ relevant CORE_MODEL_DETAIL.md section where not superseded\n→ DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md for the frozen 7B foundation/history\n→ PRODUCT_ROADMAP_GATES.md whenever crossing a product/research collaboration gate\n→ relevant research guide\n→ targeted raw research only when exact evidence/equations/platform wording are needed\n→ current source code and immutable model/config identities\n```\n''',
)
replace_once(path, '\nN-BIO-7C has not started.\n', '\nN-BIO-7C is the active consolidated capability-family mission; N-BIO-7D has not started.\n')

# ---------------------------------------------------------------------------
# PLAN current phase and PD-001 closure semantics.
# ---------------------------------------------------------------------------
path = "docs/n-bio-vnext/PLAN.md"
replace_once(path, 'Room remains **14** during 7B.X unless a genuine semantic impossibility is demonstrated.', 'Room remains **14** through the current N-BIO-7C mission unless a genuine semantic impossibility is demonstrated.')
replace_once(
    path,
    '''N-BIO-7B.X ACTIVE — adaptive inference architecture consolidation\nN-BIO-7C   NOT STARTED\nlater 7C–7G retain their intended product/domain destinations but must consume this corrected inference architecture\n''',
    '''N-BIO-7B.X complete — corrected adaptive-inference consolidation; Sparse selected, Dense oracle retained, Laplace rejected production\nN-BIO-7C   ACTIVE — loaded-hold, duration-only and repeated-contraction structural/pre-validation under NON_DYNAMIC_CAPABILITY_CONTRACT.md\nPD-001     OPEN — 7C empirical human calibration postponed where longitudinal evidence is insufficient; downstream quarantine remains binding\nlater 7D–7G retain their intended product/domain destinations but must consume the corrected inference/7C contracts without treating PD-001-pending numbers as validated truth\n''',
)
replace_once(
    path,
    'Later loaded-hold, duration-only, repeated-contraction, SetDemand, Exposure/Dose, Fatigue, Recovery, SkillState, Development, cross-profile translation, equipment intelligence, Health Connect and conditioning implementations remain outside 7B.X.',
    'Loaded-hold, duration-only and repeated-contraction capability are the current 7C scope. SetDemand, Exposure/Dose, Fatigue, Recovery, SkillState, Development, cross-profile translation, equipment intelligence, Health Connect and conditioning remain outside 7C and must not begin before amended 7C closure.',
)
replace_once(
    path,
    '''When empirical evidence is insufficient, the correct result is `INCONCLUSIVE`/deferred—not invented certainty.\n''',
    '''When empirical evidence is insufficient, the correct result is `INCONCLUSIVE`/deferred—not invented certainty. For N-BIO-7C specifically, PD-001 refines that rule: insufficient family-specific real history does **not** block structural/pre-validation closure once every non-deferrable mathematical/semantic/numerical/persistence/replay/backup/product-authority gate passes. Such families must remain `EMPIRICAL_ACCURACY_PENDING` or `NOT_EVALUATED_REAL_HISTORY`, and PD-001's downstream quarantine remains binding until a later evidence-backed validation pass closes it.\n''',
)

# ---------------------------------------------------------------------------
# CORE detail routes exact 7C mathematics to the new normative contract.
# ---------------------------------------------------------------------------
path = "docs/n-bio-vnext/CORE_MODEL_DETAIL.md"
replace_once(
    path,
    '''# 4. Loaded-hold capability\n\n**[RESEARCH-BACKED]** Load-duration/isometric work requires a separate capability family.\n''',
    '''# 4. Loaded-hold capability\n\n> **Current N-BIO-7C authority:** exact equations, priors, reference duration, evidence policy, dynamic-state rules, extrapolation and solver semantics are specified in [`NON_DYNAMIC_CAPABILITY_CONTRACT.md`](./NON_DYNAMIC_CAPABILITY_CONTRACT.md). The older text below remains the research/design shape and is superseded where the normative 7C contract is more exact. Empirical calibration status is governed by PD-001 in [`POSTPONED_DEVELOPMENT.md`](./POSTPONED_DEVELOPMENT.md).\n\n**[RESEARCH-BACKED]** Load-duration/isometric work requires a separate capability family.\n''',
)
replace_once(
    path,
    '''# 5. Duration-only and repeated-contraction capability\n\n## Duration-only\n''',
    '''# 5. Duration-only and repeated-contraction capability\n\n> **Current N-BIO-7C authority:** [`NON_DYNAMIC_CAPABILITY_CONTRACT.md`](./NON_DYNAMIC_CAPABILITY_CONTRACT.md) defines the versioned duration-only log-frontier and repeated-contraction load-cycle frontier, cadence/duration treatment, lower-bound semantics, exact isolation rules and Sparse/Dense validation. PD-001 permits structural closure without pretending sparse real history establishes empirical calibration.\n\n## Duration-only\n''',
)

print("N-BIO-7C UI/docs patch applied")
