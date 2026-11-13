// SPDX-License-Identifier: GPL-3.0-only
plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.gplaider.keliboard.imesmoke"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.gplaider.keliboard.imesmoke"
        minSdk = 23 // UI Automator's test host; the keyboard still supports API 21
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
