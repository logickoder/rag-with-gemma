plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.logickoder.ragwithgemma"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.logickoder.ragwithgemma"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.core)
    implementation(libs.core.appcompat)
    implementation(libs.core.material)

    // Compose
    implementation(platform(libs.compose))
    implementation(libs.compose.activity)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlite.bundled)

    // Local AI Runtimes
    implementation(libs.ai.edge.litert)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.mediapipe.tasks.text)

    // Kotlin
    implementation(libs.kotlin.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
}