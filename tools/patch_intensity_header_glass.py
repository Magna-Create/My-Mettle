from pathlib import Path

path = Path("app/src/main/java/dev/kian/mymettle/ui/FigmaIntensitySelectorV3.kt")
text = path.read_text(encoding="utf-8")

old_import = "import androidx.compose.foundation.Canvas\n"
new_import = "import androidx.compose.foundation.Canvas\nimport androidx.compose.foundation.clickable\n"
if text.count(old_import) != 1 or "import androidx.compose.foundation.clickable\n" in text:
    raise RuntimeError("unexpected clickable import state")
text = text.replace(old_import, new_import, 1)

old_capsule = '''        MettleGlassSurface(
            modifier = Modifier
                .offset(y = metrics.dp(-0.685))
                .width(metrics.dp(81))
                .height(metrics.dp(49.388)),
            shape = CircleShape,
            tint = IntensityV3OnTertiaryContainer.copy(alpha = 0.28f),
            blurRadius = metrics.dp(6),
            refractionDisplacement = metrics.dp(4),
            refractionStrength = 0.28f,
            shadowElevation = metrics.dp(3),
        ) {'''
new_capsule = '''        MettleControlGlassSurface(
            modifier = Modifier
                .offset(y = metrics.dp(-0.685))
                .width(metrics.dp(81))
                .height(metrics.dp(49.388)),
            shape = CircleShape,
            // Same hotbar-derived optic as the rest of the interactive glass family. Keep only
            // the page's cyan semantic bias and the capsule's own compact lift.
            tint = IntensityV3OnTertiaryContainer.copy(alpha = 0.07f),
            shadowElevation = metrics.dp(3),
        ) {'''
if text.count(old_capsule) != 1:
    raise RuntimeError(f"outer capsule match count: {text.count(old_capsule)}")
text = text.replace(old_capsule, new_capsule, 1)

old_icon = '''    MettleGlassSurface(
        modifier = Modifier.size(metrics.dp(32.78)),
        shape = CircleShape,
        tint = Color.Transparent,
        blurRadius = 0.dp,
        refractionDisplacement = 0.dp,
        refractionStrength = 0f,
        shadowElevation = 0.dp,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.size(metrics.dp(16.39)),
            )
        }
    }'''
new_icon = '''    Box(
        modifier = Modifier
            .size(metrics.dp(32.78))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.size(metrics.dp(16.39)),
        )
    }'''
if text.count(old_icon) != 1:
    raise RuntimeError(f"inner icon match count: {text.count(old_icon)}")
text = text.replace(old_icon, new_icon, 1)

path.write_text(text, encoding="utf-8")
print("updated", path)
