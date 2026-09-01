from pathlib import Path

codec = Path("app/src/main/java/dev/kian/mymettle/inference/NonDynamicCapabilityParameterCodec.kt")
text = codec.read_text()

# A stream value must be consumed exactly once before enum lookup. Reading inside
# entries.first { ... } advances the stream once per failed enum candidate and corrupts framing.
replacements = {
    "Laterality.entries.first { it.storageValue == input.readUtf8String() }":
        "input.readStoredEnum(Laterality.entries) { it.storageValue }",
    "MetricFamily.entries.first { it.storageValue == input.readUtf8String() }":
        "input.readStoredEnum(MetricFamily.entries) { it.storageValue }",
    "UnitId.entries.first { it.storageValue == input.readUtf8String() }":
        "input.readStoredEnum(UnitId.entries) { it.storageValue }",
    "InferenceSolverFamily.entries.first { it.storageValue == input.readUtf8String() }":
        "input.readStoredEnum(InferenceSolverFamily.entries) { it.storageValue }",
    "InferenceComputeBackend.entries.first { it.storageValue == input.readUtf8String() }":
        "input.readStoredEnum(InferenceComputeBackend.entries) { it.storageValue }",
    "InferencePosteriorRepresentation.entries.first { it.storageValue == input.readUtf8String() }":
        "input.readStoredEnum(InferencePosteriorRepresentation.entries) { it.storageValue }",
    "DynamicParameterIdentification.entries.first { it.storageValue == readUtf8String() }":
        "readStoredEnum(DynamicParameterIdentification.entries) { it.storageValue }",
}
for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f"codec enum anchor not found: {old}")
    text = text.replace(old, new)

helper_anchor = "    private fun DataInputStream.readParameterPosterior(): NonDynamicParameterPosterior = NonDynamicParameterPosterior(\n"
if helper_anchor not in text:
    raise SystemExit("codec readParameterPosterior anchor not found")
helper = """    private fun <T> DataInputStream.readStoredEnum(values: Iterable<T>, storageValue: (T) -> String): T {
        val encoded = readUtf8String()
        return values.firstOrNull { storageValue(it) == encoded }
            ?: throw IllegalArgumentException("Unsupported N-BIO-7C encoded enum value '$encoded'.")
    }

"""
if "private fun <T> DataInputStream.readStoredEnum" not in text:
    text = text.replace(helper_anchor, helper + helper_anchor, 1)
codec.write_text(text)

synthetic = Path("app/src/main/java/dev/kian/mymettle/developer/NBio7CSyntheticValidation.kt")
s = synthetic.read_text()
if "import kotlin.math.abs\n" not in s:
    s = s.replace("import kotlin.math.exp\n", "import kotlin.math.abs\nimport kotlin.math.exp\n", 1)
old_checks = """            checks[\"slope_truth_in_sparse_90pct_interval\"] = truthSlope in requireNotNull(sparse.slope).summary.p05..sparse.slope.summary.p95
            checks[\"slope_truth_in_dense_90pct_interval\"] = truthSlope in requireNotNull(dense.slope).summary.p05..dense.slope.summary.p95"""
new_checks = """            checks[\"slope_truth_in_sparse_90pct_interval\"] = containsWithFloatingPointTolerance(requireNotNull(sparse.slope).summary, truthSlope)
            checks[\"slope_truth_in_dense_90pct_interval\"] = containsWithFloatingPointTolerance(requireNotNull(dense.slope).summary, truthSlope)"""
if old_checks not in s:
    raise SystemExit("synthetic slope-check anchor not found")
s = s.replace(old_checks, new_checks, 1)
anchor = "    private fun relativeWidth(summary: PosteriorSummary): Double =\n"
if anchor not in s:
    raise SystemExit("relativeWidth anchor not found")
helper = """    private fun containsWithFloatingPointTolerance(summary: PosteriorSummary, truth: Double): Boolean {
        val scale = maxOf(1.0, abs(truth), abs(summary.p05), abs(summary.p95))
        val tolerance = 1e-12 * scale
        return truth >= summary.p05 - tolerance && truth <= summary.p95 + tolerance
    }

"""
if "private fun containsWithFloatingPointTolerance" not in s:
    s = s.replace(anchor, helper + anchor, 1)
synthetic.write_text(s)
