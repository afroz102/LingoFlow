import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Public endpoint only. The Gemini key stays in the backend environment.
val backendConfig = Properties().apply {
    val configFile = rootProject.file("backend.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}
val backendUrl = (System.getenv("LINGOFLOW_BACKEND_URL")
    ?: backendConfig.getProperty("BACKEND_URL", "")).trim().trimEnd('/')
val endpoint = backendUrl.takeIf { it.isNotEmpty() }?.let { URI(it) }
require(endpoint == null || (endpoint.scheme == "https" && endpoint.host != null &&
    endpoint.rawUserInfo == null && endpoint.rawQuery == null && endpoint.rawFragment == null &&
    endpoint.rawPath.isNullOrEmpty() && endpoint.port in -1..65535 && endpoint.port != 0)) {
    "BACKEND_URL must be an HTTPS origin without credentials, a path, query, or fragment"
}

// The latency harness reads the frozen corpus (benchmark/TIMING_HARNESS.md). Copying it in at
// build time instead of checking a second copy into app/src/androidTest/assets keeps
// benchmark/corpus/corpus_v1.jsonl the single source of truth — a duplicated corpus would drift
// after the first reviewer correction and silently benchmark different text than it scores.
val copyBenchmarkCorpus by tasks.registering(Copy::class) {
    from(rootProject.file("benchmark/corpus/corpus_v1.jsonl"))
    into(layout.buildDirectory.dir("generated/benchmarkAssets"))
}

android {
    namespace = "com.lingoflow.instanttranslate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lingoflow.instanttranslate"
        // Provisional minimum; physical-device compatibility review remains open.
        minSdk = 23
        targetSdk = 34
        versionCode = 7
        versionName = "1.1.0"
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // TIMING_ENABLED gates the Gate 1 latency instrumentation
        // (docs/VALIDATION_PLAN.md §3.4, benchmark/TIMING_HARNESS.md). It is a separate flag from
        // BuildConfig.DEBUG on purpose: benchmarking a release-shaped build later means adding a
        // build type that flips this one field, not re-instrumenting the flow.
        debug {
            buildConfigField("boolean", "TIMING_ENABLED", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "TIMING_ENABLED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            // TranslateCoordinator marks T_DIRECTION (docs/VALIDATION_PLAN.md §3.4), which reaches
            // SystemClock/Trace/Log. Those are stubs in the JVM unit-test android.jar and throw
            // "not mocked" by default, which failed three previously-passing coordinator tests.
            //
            // The trade-off is real and worth knowing: with this on, any Android API called from
            // code under unit test quietly returns 0/null instead of failing loudly. The
            // alternative was to keep the coordinator platform-free by moving the mark out of it,
            // but the direction is resolved *in* the coordinator, so the mark would then be
            // measuring the wrong instant — a wrong number is worse than a loosened test stub.
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir(layout.buildDirectory.dir("generated/benchmarkAssets"))
        }
    }

    buildFeatures {
        viewBinding = true
        // Public endpoint and content-free timing flags.
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.uiautomator)
}

tasks.named("preBuild") { dependsOn(copyBenchmarkCorpus) }

// Publish a predictable, versioned file for phone testing without changing the install identity.
val exportDebugApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    dependsOn("packageDebug")
    into(layout.buildDirectory.dir("outputs/apk/lingoboard"))
    rename { "LingoBoard-${android.defaultConfig.versionName}.apk" }
}
tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(exportDebugApk) }
