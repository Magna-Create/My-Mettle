import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.kian.mymettle"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.kian.mymettle"
        minSdk = 28
        targetSdk = 36
        versionCode = 11
        versionName = "0.1.0-alpha11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        resValue("string", "app_name", "My Mettle")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "My Mettle Dev")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val verifyHaze2Ui by tasks.registering {
    group = "verification"
    description = "Reject removed Haze v1 and pre-typed-Glass APIs from the Compose UI."

    val uiSourceRoot = file("src/main/java/dev/kian/mymettle/ui")
    inputs.dir(uiSourceRoot)

    doLast {
        val forbiddenPatterns = listOf(
            Regex("""\bHazeTint\b""") to "HazeTint was removed; do not reintroduce the v1 alias.",
            Regex("""\bHazeStyle\b""") to "HazeStyle is a removed v1 alias.",
            Regex("""\bLocalHazeStyle\b""") to "LocalHazeStyle is a removed v1 alias.",
            Regex("""\bHazeGlassStyle\b""") to "Use immutable GlassStyle with Modifier.hazeGlass.",
            Regex("""\bGlassVisualEffect\b""") to "Use GlassStyle plus the typed Modifier.hazeGlass API.",
            Regex("""\bGlassRenderer(?:Cache)?\b""") to "Renderer internals must not be selected from app UI code.",
            Regex("""\bGlassStyleConfiguration\b""") to "Use immutable GlassStyle directly.",
            Regex("""\bGlassLighting\b""") to "Write lighting properties inside GlassStyle instead of grouped legacy values.",
            Regex("""\bGlassColor\b""") to "Write colour properties inside GlassStyle instead of grouped legacy values.",
            Regex("""\bGlassRendering\b""") to "Write rendering properties inside GlassStyle instead of grouped legacy values.",
            Regex("""GlassStyle\s*\.\s*Unspecified""") to "Use GlassStyle itself as the empty/replayable style.",
            Regex("""\btints\s*=""") to "The old Haze tint list is not part of the Haze 2 Glass API.",
            Regex("""rememberHazeState\s*\([^)]*\bblurEnabled\s*=""") to "rememberHazeState no longer accepts blurEnabled.",
            Regex("""\bglassEffect\s*(?:\{|\()""") to "Use the typed Modifier.hazeGlass(input, style, …) API.",
            Regex("""\.hazeChild\s*\(""") to "Use hazeEffect for Haze 2 blur effects; hazeChild is removed.",
            Regex("""\.haze\s*\(""") to "Use hazeSource; the old haze source alias is removed.",
        )

        val violations = buildList {
            uiSourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { sourceFile ->
                    val source = sourceFile.readText()
                    forbiddenPatterns.forEach { (pattern, reason) ->
                        pattern.findAll(source).forEach { match ->
                            val lineNumber = source
                                .take(match.range.first)
                                .count { it == '\n' } + 1
                            add(
                                "${sourceFile.relativeTo(projectDir).path}:$lineNumber — $reason " +
                                    "[${match.value}]",
                            )
                        }
                    }
                }
        }

        check(violations.isEmpty()) {
            buildString {
                appendLine("Legacy Haze API usage found in Compose UI:")
                violations.forEach { appendLine(" - $it") }
                append("My Mettle Native targets Haze 2.0.0-alpha05 typed Glass APIs.")
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(verifyHaze2Ui)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    val cameraXVersion = "1.6.1"

    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Haze 2 keeps source capture and the experimental refractive glass renderer
    // behind a single modifier boundary, so the screen can change renderer later.
    implementation("dev.chrisbanes.haze:haze:2.0.0-alpha05")
    implementation("dev.chrisbanes.haze:haze-glass:2.0.0-alpha05")

    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    ksp(libs.androidx.room.compiler)

    testImplementation(kotlin("test-junit"))
    testImplementation("org.json:json:20250517")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
