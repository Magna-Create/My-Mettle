package dev.kian.mymettle.workout

/**
 * Native workout modes deliberately sit above the imported Lite Legacy A/B/C prescriptions.
 *
 * Legacy history is never rewritten. For a new workout we interpret the three stored anchors
 * through this one policy object, so the meaning of A/B/C/D can be tuned later without scattering
 * mode logic through UI, persistence and session code.
 */
enum class TrainingMode(
    val code: String,
    val label: String,
    val description: String,
) {
    A("A", "Full day", "The complete programmed session."),
    B("B", "Focused day", "More than Busy Day, without committing to the full session."),
    C("C", "Busy day", "The old Busy Day prescription: fewer sets, same movement coverage."),
    D("D", "Can't be arsed", "Minimum viable training; lower-priority movements can disappear entirely."),
}

enum class ExerciseImportance {
    PRINCIPAL,
    CORE,
    ACCESSORY,
}
