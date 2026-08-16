plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lingoflow.instanttranslate.testhost"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lingoflow.instanttranslate.testhost"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // Dev-only controlled host app (docs/VALIDATION_PLAN.md §2.2), never published. Two build
    // variants isolate one Android 11+ package-visibility variable at a time (§2.2 /
    // docs/IMPLEMENTATION_PLAN.md §2 item 9): whether *this host* declares the official
    // <queries> element for discovering PROCESS_TEXT-capable packages.
    flavorDimensions += "queries"
    productFlavors {
        create("withQueries") {
            dimension = "queries"
            applicationIdSuffix = ".withqueries"
            versionNameSuffix = "-with-queries"
        }
        create("withoutQueries") {
            dimension = "queries"
            applicationIdSuffix = ".withoutqueries"
            versionNameSuffix = "-without-queries"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Only :testhost pulls in Compose — the shipping :app module (Stage 0A) uses classic
    // Views, so this dependency never reaches the product surface it's testing against.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.uiautomator)
}
