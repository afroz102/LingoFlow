// Root Gradle project. No modules besides :app and :testhost — see
// IMPLEMENTATION_PLAN.md §1 (no shared library module until a second product surface exists).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
