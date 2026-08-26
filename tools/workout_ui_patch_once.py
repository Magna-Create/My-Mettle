from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"{label}: expected source block not found")
    return text.replace(old, new, 1)


workout = ROOT / "app/src/main/java/dev/kian/mymettle/ui/FigmaWorkoutSessionV2.kt"
text = workout.read_text()
if "import androidx.compose.foundation.layout.requiredSize\n" not in text:
    text = text.replace(
        "import androidx.compose.foundation.layout.padding\n",
        "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.requiredSize\n",
        1,
    )
if "import androidx.compose.ui.zIndex\n" not in text:
    text = text.replace(
        "import androidx.compose.ui.unit.sp\n",
        "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.zIndex\n",
        1,
    )

old_light = '''        if (isCurrent) {
            Box(
                Modifier
                .size(metrics.dp(104))
                .align(Alignment.CenterStart)
                .blur(
                    radius = metrics.dp(40),
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .background(WorkoutV2Cyan.copy(alpha = .20f), CircleShape),
            )
        }
'''
new_light = '''        if (isCurrent) {
            // Oversized active-set illumination: the old blurred child sat behind the metric
            // glass and was visually clipped to the set-number cell on device. This radial field
            // is deliberately larger than the row and drawn above it, so fall-off continues
            // naturally across load/reps and slightly beyond the row boundaries.
            Canvas(
                Modifier
                    .requiredSize(metrics.dp(280))
                    .align(Alignment.CenterStart)
                    .zIndex(1f),
            ) {
                val centre = Offset(metrics.dp(47).toPx(), size.height / 2f)
                val radius = metrics.dp(140).toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to WorkoutV2Cyan.copy(alpha = .12f),
                            .28f to WorkoutV2Cyan.copy(alpha = .075f),
                            .58f to WorkoutV2Cyan.copy(alpha = .032f),
                            1f to Color.Transparent,
                        ),
                        center = centre,
                        radius = radius,
                    ),
                    center = centre,
                    radius = radius,
                )
            }
        }
'''
text = replace_once(text, old_light, new_light, "workout lighting")
workout.write_text(text)

intensity = ROOT / "app/src/main/java/dev/kian/mymettle/ui/FigmaIntensitySelectorV3.kt"
text = intensity.read_text()
old_dock = '''    // Same home marker as the workout-exit surface: quiet fill, outer definition ring,
    // then one smaller inner ring. Deliberately no third concentric treatment.
    Canvas(modifier = Modifier.size(metrics.dp(48))) {
        drawCircle(Color.White.copy(alpha = .045f))
        drawCircle(
            MettleOnSurface.copy(alpha = .68f),
            style = Stroke(metrics.dp(.8).toPx()),
        )
        drawCircle(
            MettleOnSurface.copy(alpha = .34f),
            radius = size.minDimension * .22f,
            style = Stroke(metrics.dp(.65).toPx()),
        )
    }
'''
new_dock = '''    // Match the workout-exit dock as it actually reads on-device: one outer definition
    // ring and one smaller home ring. The selector's brighter field made the previous translucent
    // disc read as an unwanted third concentric circle.
    Canvas(modifier = Modifier.size(metrics.dp(48))) {
        drawCircle(
            Color(0xFFE1E4DA).copy(alpha = .68f),
            style = Stroke(metrics.dp(.8).toPx()),
        )
        drawCircle(
            Color(0xFFE1E4DA).copy(alpha = .34f),
            radius = size.minDimension * .22f,
            style = Stroke(metrics.dp(.65).toPx()),
        )
    }
'''
text = replace_once(text, old_dock, new_dock, "intensity dock")
intensity.write_text(text)

baseline_android = '''name: Android CI

on:
  push:
    branches:
      - main
      - "native/**"
      - "agent/**"
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle 9.3.1
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "9.3.1"

      - name: Install Android SDK 37
        shell: bash
        run: |
          SDKMANAGER="${ANDROID_SDK_ROOT:-$ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
          yes | "$SDKMANAGER" --licenses >/dev/null || true
          "$SDKMANAGER" "platforms;android-37.0" "build-tools;36.0.0"

      - name: Verify generated biological reference assets
        run: python3 tools/reference_data/generate_reference_assets.py --check

      - name: Test and build debug APK
        run: ./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace
'''
(ROOT / ".github/workflows/android.yml").write_text(baseline_android)
Path(__file__).unlink()

subprocess.run(["git", "config", "user.name", "Magna-Create"], cwd=ROOT, check=True)
subprocess.run(["git", "config", "user.email", "53941598+Magna-Create@users.noreply.github.com"], cwd=ROOT, check=True)
subprocess.run(["git", "add", "-A"], cwd=ROOT, check=True)
subprocess.run(["git", "commit", "-m", "Fix workout set lighting and simplify intensity dock"], cwd=ROOT, check=True)
subprocess.run(["git", "push", "origin", "HEAD:agent/workout-ui-nbio6-upgrade"], cwd=ROOT, check=True)
