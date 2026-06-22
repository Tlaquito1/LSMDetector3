// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Lee app/google-services.json y genera la configuración Android de Firebase.
    alias(libs.plugins.google.services) apply false
}
