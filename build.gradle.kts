buildscript {
    dependencies {
        // Keep Kotlin 2.4.10 while AGP 9 provides the Kotlin integration.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
