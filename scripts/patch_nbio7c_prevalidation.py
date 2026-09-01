from pathlib import Path

codec = Path("app/src/main/java/dev/kian/mymettle/inference/NonDynamicCapabilityParameterCodec.kt")
text = codec.read_text()

old_reader_start = "DataInputStream(ByteArrayInputStream(bytes)).use { input ->\n            require(input.readUtf8String() == MAGIC)"
new_reader_start = "DataInputStream(ByteArrayInputStream(bytes)).use { input ->\n            val reader = CodecReader(input)\n            require(reader.string() == MAGIC)"
if old_reader_start not in text:
    raise SystemExit("codec reader start anchor not found")
text = text.replace(old_reader_start, new_reader_start, 1)
for old, new in [
    ("input.readUtf8String()", "reader.string()"),
    ("input.readNullableDouble()", "reader.nullableDouble()"),
    ("input.readNullableLong()", "reader.nullableLong()"),
    ("input.readNullableString()", "reader.nullableString()"),
    ("input.readUtf8StringList()", "reader.stringList()"),
    ("input.readNullableParameterPosterior()", "reader.nullableParameterPosterior()"),
    ("input.readParameterPosterior()", "reader.parameterPosterior()"),
    ("input.readBoolean()", "reader.boolean()"),
    ("input.readInt()", "reader.int()"),
    ("input.readDouble()", "reader.double()"),
    ("input.available()", "reader.available()"),
]:
    text = text.replace(old, new)

old_writer_start = "DataOutputStream(bytes).use { output ->\n            output.writeUtf8String(MAGIC)"
new_writer_start = "DataOutputStream(bytes).use { output ->\n            val writer = CodecWriter(output)\n            writer.string(MAGIC)"
if old_writer_start not in text:
    raise SystemExit("codec writer start anchor not found")
text = text.replace(old_writer_start, new_writer_start, 1)
for old, new in [
    ("output.writeUtf8String(", "writer.string("),
    ("output.writeNullableDouble(", "writer.nullableDouble("),
    ("output.writeNullableLong(", "writer.nullableLong("),
    ("output.writeNullableString(", "writer.nullableString("),
    ("output.writeUtf8StringList(", "writer.stringList("),
    ("output.writeNullableParameterPosterior(", "writer.nullableParameterPosterior("),
    ("output.writeParameterPosterior(", "writer.parameterPosterior("),
    ("output.writeBoolean(", "writer.boolean("),
    ("output.writeInt(", "writer.int("),
    ("output.writeDouble(", "writer.double("),
]:
    text = text.replace(old, new)

start = text.index("    private fun DataOutputStream.writeParameterPosterior")
end = text.index("    private fun deflate", start)
helpers = """    private class CodecWriter(private val output: DataOutputStream) {
        fun parameterPosterior(value: NonDynamicParameterPosterior) {
            double(value.summary.p05)
            double(value.summary.p50)
            double(value.summary.p95)
            double(value.summary.posteriorVariance)
            string(value.identification.storageValue)
            string(value.semanticUnit)
        }

        fun nullableParameterPosterior(value: NonDynamicParameterPosterior?) {
            boolean(value != null)
            if (value != null) parameterPosterior(value)
        }

        fun string(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            require(bytes.size <= MAX_STRING_BYTES) { "N-BIO-7C codec string exceeds $MAX_STRING_BYTES UTF-8 bytes." }
            output.writeInt(bytes.size)
            output.write(bytes)
        }

        fun nullableDouble(value: Double?) { boolean(value != null); if (value != null) double(value) }
        fun nullableLong(value: Long?) { boolean(value != null); if (value != null) output.writeLong(value) }
        fun nullableString(value: String?) { boolean(value != null); if (value != null) string(value) }
        fun stringList(values: List<String>) { int(values.size); values.forEach(::string) }
        fun boolean(value: Boolean) = output.writeBoolean(value)
        fun int(value: Int) = output.writeInt(value)
        fun double(value: Double) = output.writeDouble(value)
    }

    private class CodecReader(private val input: DataInputStream) {
        fun parameterPosterior(): NonDynamicParameterPosterior = NonDynamicParameterPosterior(
            summary = PosteriorSummary(double(), double(), double(), double()),
            identification = DynamicParameterIdentification.entries.first { it.storageValue == string() },
            semanticUnit = string(),
        )

        fun nullableParameterPosterior(): NonDynamicParameterPosterior? =
            if (boolean()) parameterPosterior() else null

        fun string(): String {
            val length = input.readInt()
            require(length in 0..MAX_STRING_BYTES) { "N-BIO-7C codec string length $length is invalid." }
            val bytes = ByteArray(length)
            input.readFully(bytes)
            return String(bytes, StandardCharsets.UTF_8)
        }

        fun nullableDouble(): Double? = if (boolean()) double() else null
        fun nullableLong(): Long? = if (boolean()) input.readLong() else null
        fun nullableString(): String? = if (boolean()) string() else null
        fun stringList(): List<String> {
            val size = int()
            require(size >= 0) { "N-BIO-7C codec list length $size is invalid." }
            return List(size) { string() }
        }
        fun boolean(): Boolean = input.readBoolean()
        fun int(): Int = input.readInt()
        fun double(): Double = input.readDouble()
        fun available(): Int = input.available()
    }

"""
text = text[:start] + helpers + text[end:]
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
