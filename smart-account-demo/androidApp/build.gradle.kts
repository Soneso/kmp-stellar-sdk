plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

android {
    namespace = "com.soneso.stellar.smartdemo.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.soneso.stellar.smartdemo.android"
        minSdk = 28 // Passkey support requires API 28+
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":smart-account-demo:shared"))
    implementation("androidx.activity:activity-compose:1.8.2")

    // Reown (WalletConnect v2) for external wallet connection via Freighter Mobile.
    // com.reown:android-core provides CoreClient (relay, pairing, metadata).
    // com.reown:sign provides SignClient (session proposal, request/response, delegates).
    implementation("com.reown:android-core:1.6.12")
    implementation("com.reown:sign:1.6.12")
}
