package dev.kian.mymettle.workout

/**
 * Native workout modes sit above imported Lite Legacy A/B/C preferences.
 * Metric-general planning belongs to the target resolver and prescription engine; this enum is
 * deliberately only product vocabulary, not a second rep-centric planning implementation.
 */
enum class TrainingMode(
    val code: String,
    val label: String,
    val description: String,
) {
    A("A", "All In", "The complete programmed session."),
    B("B", "Busy Day", "More than Nice & Chill, without committing to the full session."),
    C("C", "Nice & Chill", "Fewer sets, with the same movement coverage."),
    D("D", "Can’t be Arsed", "Minimum viable training; lower-priority movements can disappear entirely."),
}

enum class ExerciseImportance {
    PRINCIPAL,
    CORE,
    ACCESSORY,
}
