from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/dev/kian/mymettle/ui"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def write_if_changed(path: Path, original: str, updated: str) -> None:
    if updated == original:
        raise RuntimeError(f"{path.name}: migration made no changes")
    path.write_text(updated, encoding="utf-8")
    print(f"updated {path.relative_to(ROOT)}")


def migrate_figma_daily() -> None:
    path = UI / "FigmaDailyUpdate.kt"
    original = path.read_text(encoding="utf-8")
    text = original

    text = replace_once(
        text,
        """            glassBlurRadius = metrics.dp(3.087),
            glassRefractionDisplacement = metrics.dp(2.4),
            glassRefractionStrength = 0.20f,
        ) {""",
        """            glassBlurRadius = metrics.dp(3.087),
            glassRefractionDisplacement = metrics.dp(2.4),
            glassRefractionStrength = 0.20f,
            useSharedControlGlass = true,
        ) {""",
        "daily header capsule",
    )

    text = replace_once(
        text,
        """        glassBlurRadius = metrics.dp(4),
        glassRefractionDisplacement = metrics.dp(3),
        glassRefractionStrength = 0.24f,
        enabled = enabled,""",
        """        glassBlurRadius = metrics.dp(4),
        glassRefractionDisplacement = metrics.dp(3),
        glassRefractionStrength = 0.24f,
        useSharedControlGlass = true,
        enabled = enabled,""",
        "daily begin action",
    )

    text = replace_once(
        text,
        """        glassRefractionStrength = if (selected) 0.22f else 0.14f,
        borderWidth = metrics.dp(0.116),
        borderColor = MettleOutlineVariant,
        enabled = enabled,""",
        """        glassRefractionStrength = if (selected) 0.22f else 0.14f,
        borderWidth = metrics.dp(0.116),
        borderColor = MettleOutlineVariant,
        useSharedControlGlass = true,
        enabled = enabled,""",
        "daily programme day controls",
    )

    text = replace_once(
        text,
        """    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    enabled: Boolean = true,""",
        """    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    useSharedControlGlass: Boolean = false,
    enabled: Boolean = true,""",
        "FigmaTintedSurface shared material flag",
    )

    old_surface = """        MettleGlassSurface(
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            tint = fill,
            blurRadius = glassBlurRadius,
            refractionDisplacement = glassRefractionDisplacement,
            refractionStrength = glassRefractionStrength,
            // For Figma shadows with \"show behind translucent areas\" enabled, the
            // precise drop shadow is drawn on the outer layer above. Where Figma disables
            // that option (Begin Session), retain a small platform shadow instead.
            shadowElevation = if (shadowBehindTranslucent) 0.dp else shadowElevation,
            borderWidth = borderWidth,
            borderColor = borderColor,
            enabled = enabled,
            onClick = onClick,
        ) {
            content()
        }
"""
    new_surface = """        if (useSharedControlGlass) {
            // Interactive surfaces use the same optical material as the global hotbar. Keep the
            // component's existing outer/inset shadow treatment above, and only translate its old
            // paint-heavy tint into a much lighter semantic hue.
            MettleControlGlassSurface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                tint = fill.asMettleControlGlassTint(),
                enabled = enabled,
                shadowElevation = if (shadowBehindTranslucent) 0.dp else shadowElevation,
                onClick = onClick,
            ) {
                content()
            }
        } else {
            MettleGlassSurface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                tint = fill,
                blurRadius = glassBlurRadius,
                refractionDisplacement = glassRefractionDisplacement,
                refractionStrength = glassRefractionStrength,
                // For Figma shadows with \"show behind translucent areas\" enabled, the
                // precise drop shadow is drawn on the outer layer above. Where Figma disables
                // that option (Begin Session), retain a small platform shadow instead.
                shadowElevation = if (shadowBehindTranslucent) 0.dp else shadowElevation,
                borderWidth = borderWidth,
                borderColor = borderColor,
                enabled = enabled,
                onClick = onClick,
            ) {
                content()
            }
        }
"""
    text = replace_once(text, old_surface, new_surface, "FigmaTintedSurface material branch")
    write_if_changed(path, original, text)


def migrate_home_bootstrap() -> None:
    path = UI / "HomeScreen.kt"
    original = path.read_text(encoding="utf-8")
    text = replace_once(
        original,
        """            MettleGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                tint = MettleOnPrimaryContainer.copy(alpha = 0.18f),
                blurRadius = 16.dp,
                refractionDisplacement = 5.dp,
                refractionStrength = 0.18f,
                shadowElevation = 4.dp,
                onClick = onOpenWorkout,
            ) {""",
        """            MettleControlGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                tint = MettleOnPrimaryContainer.copy(alpha = 0.055f),
                shadowElevation = 4.dp,
                onClick = onOpenWorkout,
            ) {""",
        "bootstrap import action",
    )
    write_if_changed(path, original, text)


def migrate_material_controls(relative: str) -> None:
    path = UI / relative
    original = path.read_text(encoding="utf-8")
    text = original

    # Deliberately leave TextButton alone: dismissive/tertiary actions should remain visually
    # lighter than raised glass controls.
    text = re.sub(r"(?<![A-Za-z0-9_])Button\(", "MettleGlassActionButton(", text)
    text = text.replace("OutlinedButton(", "MettleGlassActionButton(accent = false, ")
    text = text.replace("FilledTonalButton(", "MettleGlassActionButton(")
    text = text.replace("FilterChip(", "MettleGlassChoiceChip(")
    text = text.replace("AssistChip(", "MettleGlassAssistChip(")

    write_if_changed(path, original, text)


def main() -> None:
    migrate_figma_daily()
    migrate_home_bootstrap()

    for relative in (
        "TrainScreen.kt",
        "SettingsScreen.kt",
        "ExerciseLibraryScreen.kt",
        "HistoryScreen.kt",
        "NativeRestTimerOverlay.kt",
        "ExerciseReflectionOverlay.kt",
        "SessionOutcomeOverlay.kt",
        "SetupCameraOverlay.kt",
    ):
        migrate_material_controls(relative)


if __name__ == "__main__":
    main()
