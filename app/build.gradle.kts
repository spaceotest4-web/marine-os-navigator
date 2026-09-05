import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.ferrostartnew"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        // Public app identity (Play Store rejects com.example.*). The internal
        // Kotlin namespace stays as-is; only the shipped ID matters.
        applicationId = "com.marineos.navigator"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        // Real phones are arm64; shipping only that ABI roughly halves the
        // APK (Ferrostar + MapLibre native libs otherwise come in 4 flavors).
        // Note: x86 emulators can't run this build - use a real device.
        ndk { abiFilters.add("arm64-v8a") }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "stadiaApiKey",
            "\"${localProperties.getProperty("stadiaApiKey") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "graphhopperApiKey",
            "\"${localProperties.getProperty("graphhopperApiKey") ?: ""}\"",
        )
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    val ferrostarVersion = "0.53.0"
    implementation("com.stadiamaps.ferrostar:core:$ferrostarVersion")
    implementation("com.stadiamaps.ferrostar:ui-compose:$ferrostarVersion")
    implementation("com.stadiamaps.ferrostar:ui-maplibre:$ferrostarVersion")
    implementation("com.stadiamaps.ferrostar:google-play-services:$ferrostarVersion")
    implementation("org.maplibre.compose:maplibre-compose-android:0.13.0")

    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.3.2"))
    implementation("com.squareup.okhttp3:okhttp")

    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
