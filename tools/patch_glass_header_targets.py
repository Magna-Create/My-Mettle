from pathlib import Path
import re


def replace_once(text: str, pattern: str, replacement: str, label: str, flags: int = 0) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label} replacement count={count}")
    return updated


def patch_daily() -> None:
    path = Path("app/src/main/java/dev/kian/mymettle/ui/FigmaDailyUpdate.kt")
    text = path.read_text(encoding="utf-8")
    text = replace_once(
        text,
        r"\.width\(metrics\.dp\(81\)\)\s*\n\s*\.height\(metrics\.dp\(49\.388\)\),",
        ".width(metrics.dp(96))\n                .height(metrics.dp(52)),",
        "Daily capsule size",
    )
    text = replace_once(
        text,
        r"modifier = Modifier\s*\n\s*\.fillMaxSize\(\)\s*\n\s*\.padding\(horizontal = metrics\.dp\(6\.1735\)\),\s*\n\s*horizontalArrangement = Arrangement\.spacedBy\(metrics\.dp\(3\.08675\)\),",
        "modifier = Modifier.fillMaxSize(),\n                horizontalArrangement = Arrangement.SpaceBetween,",
        "Daily header row",
    )
    text = replace_once(
        text,
        r"@Composable\nprivate fun FigmaHeaderIconButton\(.*?\n}\n\n@Composable\nprivate fun FigmaHeroGreeting",
        """@Composable
private fun FigmaHeaderIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    metrics: FigmaMetrics,
) {
    MettleGlassIconTouchTarget(
        modifier = Modifier
            .width(metrics.dp(48))
            .fillMaxHeight(),
        imageVector = imageVector,
        contentDescription = contentDescription,
        onClick = onClick,
        iconSize = DpSize(metrics.dp(16.3916), metrics.dp(16.3916)),
        contentAlpha = 0.80f,
        pressedHaloSize = metrics.dp(36),
    )
}

@Composable
private fun FigmaHeroGreeting""",
        "Daily header target",
        flags=re.S,
    )
    path.write_text(text, encoding="utf-8")


def patch_intensity() -> None:
    path = Path("app/src/main/java/dev/kian/mymettle/ui/FigmaIntensitySelectorV3.kt")
    text = path.read_text(encoding="utf-8")
    if "import androidx.compose.ui.unit.DpSize\n" not in text:
        text = text.replace(
            "import androidx.compose.ui.unit.Dp\n",
            "import androidx.compose.ui.unit.Dp\nimport androidx.compose.ui.unit.DpSize\n",
            1,
        )
    text = replace_once(
        text,
        r"\.width\(metrics\.dp\(81\)\)\s*\n\s*\.height\(metrics\.dp\(49\.388\)\),",
        ".width(metrics.dp(96))\n                .height(metrics.dp(52)),",
        "Intensity capsule size",
    )
    text = replace_once(
        text,
        r"modifier = Modifier\s*\n\s*\.fillMaxSize\(\)\s*\n\s*\.padding\(horizontal = metrics\.dp\(6\.17\)\),\s*\n\s*horizontalArrangement = Arrangement\.spacedBy\(metrics\.dp\(3\.09\)\),",
        "modifier = Modifier.fillMaxSize(),\n                horizontalArrangement = Arrangement.SpaceBetween,",
        "Intensity header row",
    )
    text = replace_once(
        text,
        r"@Composable\nprivate fun IntensityV3HeaderIcon\(.*?\n}\n\n@Composable\nprivate fun IntensityV3ModeCopy",
        """@Composable
private fun IntensityV3HeaderIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    metrics: IntensityV3Metrics,
) {
    MettleGlassIconTouchTarget(
        modifier = Modifier
            .width(metrics.dp(48))
            .fillMaxHeight(),
        imageVector = imageVector,
        contentDescription = contentDescription,
        onClick = onClick,
        iconSize = DpSize(metrics.dp(16.39), metrics.dp(16.39)),
        contentAlpha = 0.82f,
        pressedHaloSize = metrics.dp(36),
    )
}

@Composable
private fun IntensityV3ModeCopy""",
        "Intensity header target",
        flags=re.S,
    )
    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_daily()
    patch_intensity()
