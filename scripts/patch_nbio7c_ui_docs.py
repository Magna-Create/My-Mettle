from pathlib import Path


def read(path: str) -> str:
    return Path(path).read_text()


def write(path: str, text: str) -> None:
    Path(path).write_text(text)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise SystemExit(f"anchor not found in {path}: {old[:160]!r}")
    write(path, text.replace(old, new, 1))


def replace_optional(path: str, old: str, new: str) -> None:
    text = read(path)
    if old in text:
        write(path, text.replace(old, new, 1))


# Prediction-domain semantics: allow moderate extrapolation while retaining typed fail-closed safety.
path = "app/src/main/java/dev/kian/mymettle/engine/performance/NonDynamicCapabilityModel.kt"
replace_once(
    path,
    '''        val x = if (input == null) 0.0 else ln(input / requireNotNull(fit.referenceCoordinate))
        val logDomainMin = ln(config.outputPriorMinimum)
        val logDomainMax = ln(config.outputPriorMaximum)
        val logValues = fit.posteriorNodes.map { node ->
            val value = node.logFrontierAtReference + node.trajectory * offset - (node.slope ?: 0.0) * x
            if (!value.isFinite() || value !in logDomainMin..logDomainMax) {
                throw NonDynamicCapabilityFitException(
                    NonDynamicFitFailureReason.NON_FINITE_POSTERIOR,
                    "7C ${fit.family.storageValue} query left the configured numerical output domain; prediction is unavailable.",
                )
            }
            WeightedValue(value, node.posteriorWeight)
        }
        val direct = summary(logValues.map { WeightedValue(exp(it.value), it.weight) })
''',
    '''        val x = if (input == null) 0.0 else ln(input / requireNotNull(fit.referenceCoordinate))
        val safeExpLogMin = ln(Double.MIN_NORMAL)
        val safeExpLogMax = ln(Double.MAX_VALUE)
        val logValues = fit.posteriorNodes.map { node ->
            val value = node.logFrontierAtReference + node.trajectory * offset - (node.slope ?: 0.0) * x
            if (!value.isFinite() || value !in safeExpLogMin..safeExpLogMax) {
                throw NonDynamicCapabilityFitException(
                    NonDynamicFitFailureReason.NON_FINITE_POSTERIOR,
                    "7C ${fit.family.storageValue} query cannot be represented safely in finite positive output space.",
                )
            }
            WeightedValue(value, node.posteriorWeight)
        }
        val direct = summary(logValues.map { WeightedValue(exp(it.value), it.weight) })
        if (direct.p50 !in config.outputPriorMinimum..config.outputPriorMaximum) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.NON_FINITE_POSTERIOR,
                "7C ${fit.family.storageValue} query median left the configured numerical output domain; prediction is unavailable.",
            )
        }
''',
)
replace_once(
    path,
    '''        val approximateLower = exp(meanLog - z90 * totalLogSd)
        val approximateUpper = exp(meanLog + z90 * totalLogSd)
        val approximateVariance = (exp(totalLogVariance) - 1.0) * exp(2.0 * meanLog + totalLogVariance)
        val widened = PosteriorSummary(
''',
    '''        val approximateLower = exp(meanLog - z90 * totalLogSd)
        val approximateUpper = exp(meanLog + z90 * totalLogSd)
        val approximateVariance = (exp(totalLogVariance) - 1.0) * exp(2.0 * meanLog + totalLogVariance)
        if (!approximateLower.isFinite() || !approximateUpper.isFinite() || !approximateVariance.isFinite()) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.NON_FINITE_POSTERIOR,
                "7C ${fit.family.storageValue} extrapolation uncertainty became non-finite; prediction is unavailable.",
            )
        }
        val widened = PosteriorSummary(
''',
)

# Same-session replication is a numerical equivalence invariant, not bit identity.
path = "app/src/test/java/dev/kian/mymettle/engine/performance/NonDynamicCapabilityAdversarialTest.kt"
replace_once(
    path,
    '''        val one = fit(profile, single)
        val two = fit(profile, replicated)
        assertEquals(one.frontierAtReference.summary, two.frontierAtReference.summary)
        assertEquals(one.slope!!.summary, two.slope!!.summary)
        assertEquals(one.trajectory.summary, two.trajectory.summary)
''',
    '''        val one = fit(profile, single)
        val two = fit(profile, replicated)
        assertEquals(5, one.support.effectiveIndependentSessionCount)
        assertEquals(5, two.support.effectiveIndependentSessionCount)
        assertEquals(5, one.support.observationCount)
        assertEquals(10, two.support.observationCount)
        assertSummaryClose(requireNotNull(one.frontierAtReference.summary), requireNotNull(two.frontierAtReference.summary))
        assertSummaryClose(requireNotNull(one.slope).summary, requireNotNull(two.slope).summary)
        assertSummaryClose(one.trajectory.summary, two.trajectory.summary)
''',
)
replace_once(
    path,
    '''    private fun fit(profile: NonDynamicProfileSemantics, evidence: List<CompletedSetEvidence>) =
''',
    '''    private fun assertSummaryClose(
        left: dev.kian.mymettle.domain.inference.PosteriorSummary,
        right: dev.kian.mymettle.domain.inference.PosteriorSummary,
    ) {
        listOf(
            left.p05 to right.p05,
            left.p50 to right.p50,
            left.p95 to right.p95,
            left.posteriorVariance to right.posteriorVariance,
        ).forEach { (a, b) ->
            assertTrue(
                kotlin.math.abs(a - b) <= 1e-10 * kotlin.math.max(1.0, kotlin.math.max(kotlin.math.abs(a), kotlin.math.abs(b))),
                "same-session replication changed posterior: $a vs $b",
            )
        }
    }

    private fun fit(profile: NonDynamicProfileSemantics, evidence: List<CompletedSetEvidence>) =
''',
)

# Replace DataOutputStream modified UTF with length-prefixed UTF-8. This is a pre-closure codec format change.
path = "app/src/main/java/dev/kian/mymettle/inference/NonDynamicCapabilityParameterCodec.kt"
text = read(path)
text = text.replace("import java.io.DataOutputStream\n", "import java.io.DataOutputStream\nimport java.nio.charset.StandardCharsets\n", 1)
text = text.replace(
    '''    const val SCHEMA_VERSION = 1
    private const val MAGIC = "NBIO7C_FIT_V1"
    private const val PREFIX = "n7c1:"
''',
    '''    const val SCHEMA_VERSION = 2
    private const val MAGIC = "NBIO7C_FIT_V2"
    private const val PREFIX = "n7c2:"
    private const val MAX_STRING_BYTES = 4 * 1024 * 1024
''',
    1,
)
if 'SCHEMA_VERSION = 2' not in text:
    raise SystemExit("codec version anchor not found")
text = text.replace("readUTF()", "readString()")
text = text.replace("writeUTF(", "writeString(")
anchor = '''    private fun DataOutputStream.writeNullableDouble(value: Double?) { writeBoolean(value != null); if (value != null) writeDouble(value) }
'''
if anchor not in text:
    raise SystemExit("codec helper anchor not found")
helpers = '''    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "N-BIO-7C codec string exceeds $MAX_STRING_BYTES UTF-8 bytes." }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val length = readInt()
        require(length in 0..MAX_STRING_BYTES) { "N-BIO-7C codec string length $length is invalid." }
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

'''
text = text.replace(anchor, helpers + anchor, 1)
write(path, text)

path = "app/src/test/java/dev/kian/mymettle/inference/NonDynamicCapabilityParameterCodecTest.kt"
replace_once(
    path,
    '''    @Test
    fun `unknown future codec fails closed`() {
''',
    '''    @Test
    fun `codec supports operational strings beyond modified UTF ceiling`() {
        val fit = fitDurationHistory()
        val longNote = "x".repeat(70_000)
        val expanded = fit.copy(
            solverDiagnostics = fit.solverDiagnostics.copy(notes = fit.solverDiagnostics.notes + longNote),
        )
        val encoded = NonDynamicCapabilityParameterCodec.encode(expanded)
        val decoded = NonDynamicCapabilityParameterCodec.decode(
            parameterSchemaVersion = NonDynamicCapabilityParameterCodec.SCHEMA_VERSION,
            encodedParameters = encoded,
            frontierAtReference = expanded.frontierAtReference,
            executionProfileVersionId = expanded.executionProfileVersionId,
            side = expanded.side,
            modelConfigId = expanded.modelConfigId,
        )
        assertTrue(longNote in decoded.solverDiagnostics.notes)
        assertTrue(NonDynamicCapabilityParameterCodec.scientificallyEquivalent(expanded, decoded))
    }

    @Test
    fun `unknown future codec fails closed`() {
''',
)

# Developer UI: expose the consolidated 7C acceptance and export.
path = "app/src/main/java/dev/kian/mymettle/ui/BiologyDeveloperScreen.kt"
replace_once(
    path,
    '''    val liteBackupLauncher = rememberLauncherForActivityResult(
''',
    '''    val nBio7CCapabilityExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = viewModel.nBio7CCapabilityJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Android could not open the selected N-BIO-7C report file.")
                }
            }.onSuccess { viewModel.markNBio7CCapabilityExported() }
                .onFailure(viewModel::reportError)
        }
    }
    val liteBackupLauncher = rememberLauncherForActivityResult(
''',
)
text = read(path)
text = text.replace(
    '!state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning &&\n                                state.task.phase != BiologyTaskPhase.RUNNING,',
    '!state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning &&\n                                !state.nBio7CCapabilityRunning && state.task.phase != BiologyTaskPhase.RUNNING,',
)
text = text.replace(
    '!state.nBio7BAcceptanceRunning && !state.adaptiveInferenceRunning,',
    '!state.nBio7BAcceptanceRunning && !state.adaptiveInferenceRunning && !state.nBio7CCapabilityRunning,',
)
write(path, text)
replace_once(
    path,
    '''                item {
                    DebugCard("N‑BIO‑6 device acceptance") {
''',
    '''                item {
                    DebugCard("N-BIO-7C capability acceptance") {
                        Text(
                            "One consolidated structural/pre-validation action for loaded holds, duration-only and repeated-contraction capability. Runs synthetic latent-truth recovery, audits installed Room14 history, compares Adaptive Sparse with Dense, verifies SHADOW persistence/delete/replay and Native backup safety. PD-001 keeps empirical accuracy explicitly pending where longitudinal evidence is insufficient.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = viewModel::runNBio7CCapabilityAcceptance,
                            enabled = !state.nBio7CCapabilityRunning && !state.nBio7BAcceptanceRunning &&
                                !state.adaptiveInferenceRunning && !state.nBio6VerificationRunning &&
                                !state.nBio6LiteVerificationRunning && state.task.phase != BiologyTaskPhase.RUNNING,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.nBio7CCapabilityRunning) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                                Text("Running N-BIO-7C capability acceptance…")
                            } else {
                                Text("Run N-BIO 7C Capability Acceptance")
                            }
                        }
                        state.nBio7CCapabilityProgress?.let { progress ->
                            DebugLine("Progress", if (progress.totalGroups > 0) "${progress.completedGroups}/${progress.totalGroups}" else "Preparing")
                            Text(progress.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.nBio7CCapabilityReport?.let { report ->
                            HorizontalDivider()
                            DebugLine("Structural pre-validation", report.structuralVerdict.storageValue)
                            DebugLine("Empirical accuracy", report.empiricalAccuracyStatus.storageValue)
                            DebugLine("Overall", report.overallVerdict.storageValue)
                            DebugLine("Room", "v${report.roomSchemaVersion}")
                            DebugLine("Synthetic", if (report.synthetic.passed) "PASS" else "FAIL")
                            DebugLine("Raw evidence", if (report.rawEvidenceUnchanged) "UNCHANGED" else "CHANGED")
                            DebugLine("Prescriptions", if (report.prescriptionStateUnchanged) "UNCHANGED" else "CHANGED")
                            DebugLine("BENCHMARK authority", if (report.benchmarkAuthorityUnchanged) "UNCHANGED" else "CHANGED")
                            DebugLine("Native backup", if (report.backupRoundTrip.passed) "PASS" else "FAIL")
                            DebugLine("Total runtime", "${report.totalElapsedMillis} ms")
                            report.familyReports.forEach { family ->
                                HorizontalDivider()
                                Text(family.family.storageValue, fontWeight = FontWeight.SemiBold)
                                DebugLine("Structural", family.structuralVerdict.storageValue)
                                DebugLine("Empirical", family.empiricalAccuracyStatus.storageValue)
                                DebugLine("Profiles", family.definedExecutionProfileVersions.toString())
                                DebugLine("Real evidence", "${family.currentEligibleObservations} obs · ${family.independentSessions} sessions")
                            }
                            report.profileReports.forEach { profile ->
                                HorizontalDivider()
                                Text("${profile.label} · ${profile.side}", fontWeight = FontWeight.SemiBold)
                                DebugLine("Family", profile.family.storageValue)
                                DebugLine("Evidence", "${profile.eligibleObservations} obs · ${profile.independentSessions} sessions")
                                DebugLine("Empirical", profile.empiricalAccuracyStatus.storageValue)
                                DebugLine("Persist/reload", profile.persistReloadEquivalent?.let { if (it) "PASS" else "FAIL" } ?: "n/a")
                                DebugLine("Delete-derived", profile.deleteDerivedConfirmed?.let { if (it) "PASS" else "FAIL" } ?: "n/a")
                                DebugLine("Full replay", profile.fullReplayEquivalent?.let { if (it) "PASS" else "FAIL" } ?: "n/a")
                                profile.numericalFailure?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                                profile.limitations.forEach { limitation ->
                                    Text("• $limitation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching { viewModel.nBio7CCapabilityJson() }
                                        .onSuccess { nBio7CCapabilityExportLauncher.launch("my-mettle-n-bio-7c-capability-${Instant.now().epochSecond}.json") }
                                        .onFailure(viewModel::reportError)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Export N-BIO-7C capability JSON") }
                        }
                    }
                }

                item {
                    DebugCard("N‑BIO‑6 device acceptance") {
''',
)

# Documentation: make the current phase and PD-001 boundary explicit without editing the register itself.
path = "docs/n-bio-vnext/NON_DYNAMIC_CAPABILITY_CONTRACT.md"
replace_once(
    path,
    'Status: **N-BIO-7C ACTIVE — candidate models pre-registered before implementation.**',
    'Status: **N-BIO-7C ACTIVE — structural/pre-validation implementation; empirical calibration quarantined by PD-001.**',
)
replace_once(
    path,
    'N-BIO-7D SetDemand/dose work and every later biological/translation/conditioning/V8 feature remain strictly out of scope.',
    '''N-BIO-7D SetDemand/dose work and every later biological/translation/conditioning/V8 feature remain strictly out of scope while this 7C mission is active.

## 16. PD-001 amended closure rule

`POSTPONED_DEVELOPMENT.md` entry **PD-001** is authoritative for the empirical-status boundary. N-BIO-7C may close its initial architecture mission when the structural/pre-validation gate is complete even if current longitudinal history cannot support meaningful calibration of these three family-specific numerical models.

The hard, non-deferrable closure requirements remain semantic/family/laterality correctness; immutable identities; lower-bound and capability/action-policy separation; synthetic latent-truth recovery; adversarial boundaries; widening extrapolation uncertainty; Adaptive-Sparse fidelity/stability against Dense; persistence/reload/delete/full replay; Native backup and Room14 integrity; raw/prescription/product-authority invariance; typed fail-closed numerical behaviour; documentation; and exact-head CI.

Where semantically valid longitudinal history is insufficient, family/profile empirical status is **`EMPIRICAL_ACCURACY_PENDING`** or **`NOT_EVALUATED_REAL_HISTORY`**, never `PASS`. Synthetic latent-truth validation proves implementation against the declared candidate mathematics; it does not prove real-human accuracy of equations, priors, process volatility, slack/noise assumptions or far-domain extrapolation.

Until PD-001 is later closed, 7C numerical outputs remain quarantined from normal-user authority and downstream phases may consume only the contracts/interfaces without inferring empirical validation from a finite posterior. Raw evidence and immutable model versions remain replayable for later candidate refinement.

N-BIO-7D is not authorised until this amended 7C structural closure is complete.''',
)

path = "docs/n-bio-vnext/README.md"
replace_optional(
    path,
    '- [`DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md`](./DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md) — N-BIO-7B evidence/coordinate contract, frozen Candidate-v1 record, chronological validation, SHADOW persistence and Candidate-v2 development evidence. Read it together with the adaptive-inference supplement for current capability/policy and solver semantics.\n',
    '- [`DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md`](./DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md) — N-BIO-7B evidence/coordinate contract, frozen Candidate-v1 record, chronological validation, SHADOW persistence and Candidate-v2 development evidence. Read it together with the adaptive-inference supplement for current capability/policy and solver semantics.\n- [`NON_DYNAMIC_CAPABILITY_CONTRACT.md`](./NON_DYNAMIC_CAPABILITY_CONTRACT.md) — normative N-BIO-7C loaded-hold, duration-only and repeated-contraction capability contract.\n- [`POSTPONED_DEVELOPMENT.md`](./POSTPONED_DEVELOPMENT.md) — deferred-validation register; PD-001 permits 7C structural closure while explicitly quarantining unearned empirical claims.\n',
)
replace_optional(
    path,
    '7B.X    ACTIVE — Adaptive Inference Architecture Consolidation; one consolidated mission, not a ladder of invented 7B.5/7B.6 phases\n7C      NOT STARTED',
    '7B.X    complete — Adaptive Sparse selected for forward Candidate-v2 inference; Dense retained oracle; Conditional Laplace rejected production\n7C      ACTIVE — loaded-hold, duration-only and repeated-contraction structural/pre-validation; PD-001 empirical accuracy pending where evidence is insufficient',
)
replace_optional(path, 'N-BIO-7C has not started.', 'N-BIO-7C is the active consolidated capability-family mission; N-BIO-7D has not started.')

path = "docs/n-bio-vnext/PLAN.md"
replace_optional(path, 'Room remains **14** during 7B.X unless a genuine semantic impossibility is demonstrated.', 'Room remains **14** through the current N-BIO-7C mission unless a genuine semantic impossibility is demonstrated.')
replace_optional(
    path,
    'N-BIO-7B.X ACTIVE — adaptive inference architecture consolidation\nN-BIO-7C   NOT STARTED\nlater 7C–7G retain their intended product/domain destinations but must consume this corrected inference architecture',
    'N-BIO-7B.X complete — corrected adaptive-inference consolidation; Sparse selected, Dense oracle retained, Laplace rejected production\nN-BIO-7C   ACTIVE — loaded-hold, duration-only and repeated-contraction structural/pre-validation under NON_DYNAMIC_CAPABILITY_CONTRACT.md\nPD-001     OPEN — empirical human calibration postponed where longitudinal evidence is insufficient; downstream quarantine remains binding\nlater 7D–7G retain their intended product/domain destinations and must consume the corrected inference/7C contracts without treating PD-001 as a pass',
)

path = "docs/n-bio-vnext/CORE_MODEL_DETAIL.md"
replace_optional(path, 'Recommended model implementation order after N-BIO-6 is fully frozen:', 'Forward model implementation order (with same-profile capability now active in N-BIO-7C):')

print("N-BIO-7C code, UI and documentation patch applied.")
