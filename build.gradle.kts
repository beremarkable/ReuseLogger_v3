// build.gradle.kts (Project-level)

plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
    id("org.jetbrains.kotlin.kapt") version "1.8.10" apply false

    // Add the Google services Gradle plugin
    id("com.google.gms.google-services") version "4.4.2" apply false
}