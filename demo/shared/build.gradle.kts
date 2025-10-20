plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    js(IR) {
        browser()
        binaries.executable()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    listOf(
        macosX64(),
        macosArm64()
    ).forEach { macosTarget ->
        macosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
            // Export stellar-sdk so it's accessible from Swift
            export(project(":stellar-sdk"))
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // ============================================================
                // UI Framework - Compose Multiplatform
                // ============================================================
                // Compose runtime, foundation, and Material 3 components for building
                // cross-platform UI demonstrating SDK functionality
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)

                // ============================================================
                // Navigation
                // ============================================================
                // Voyager for multi-screen navigation across all platforms
                // Using 1.1.0-beta02 for better JS/WASM support
                implementation("cafe.adriel.voyager:voyager-navigator:1.1.0-beta02")
                implementation("cafe.adriel.voyager:voyager-transitions:1.1.0-beta02")

                // ============================================================
                // Stellar SDK
                // ============================================================
                // The main SDK dependency - provides all Stellar functionality:
                // - KeyPair generation and signing (cryptography)
                // - FriendBot for testnet funding (HTTP client internally)
                // - Transaction building and submission
                // - All transitive dependencies (ktor, serialization, coroutines, etc.)
                api(project(":stellar-sdk"))

                // ============================================================
                // Coroutines
                // ============================================================
                // Required for calling suspend functions from UI coroutineScopes
                // (e.g., KeyPair.random(), FriendBot.fundTestnetAccount())
                // Note: Also provided transitively by stellar-sdk, but explicitly
                // declared here since it's used directly in UI layer
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
            }
        }

        // ============================================================
        // Android Platform
        // ============================================================
        val androidMain by getting {
            dependencies {
                // Android-specific Compose integration
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.appcompat:appcompat:1.6.1")
            }
        }

        // ============================================================
        // Desktop Platform (JVM)
        // ============================================================
        val desktopMain by getting {
            dependencies {
                // Desktop-specific Compose support
                implementation(compose.desktop.common)
            }
        }

        // ============================================================
        // iOS Platform
        // ============================================================
        val iosMain by creating {
            dependsOn(commonMain)
            // No additional dependencies needed - stellar-sdk provides everything
        }

        val iosX64Main by getting {
            dependsOn(iosMain)
        }

        val iosArm64Main by getting {
            dependsOn(iosMain)
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }

        // ============================================================
        // macOS Platform
        // ============================================================
        val macosMain by creating {
            dependsOn(commonMain)
            // No additional dependencies needed - stellar-sdk provides everything
        }

        val macosX64Main by getting {
            dependsOn(macosMain)
        }

        val macosArm64Main by getting {
            dependsOn(macosMain)
        }

        // ============================================================
        // JavaScript Platform (Browser)
        // ============================================================
        val jsMain by getting {
            dependencies {
                // HTML-based Compose for web
                implementation(compose.html.core)
            }
        }
    }
}

android {
    namespace = "com.soneso.demo.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    // ============================================================
    // WASM Resources Configuration for Android
    // ============================================================
    // Android doesn't automatically include commonMain/resources in the APK.
    // We need to explicitly configure the source sets to include WASM files
    // from commonMain/resources/wasm/ so they can be loaded via ClassLoader
    // at runtime.
    //
    // This configuration:
    // 1. Adds commonMain/resources to Android's source sets
    // 2. Ensures WASM files are packaged into the APK as Java resources
    // 3. Makes them accessible via ClassLoader.getResourceAsStream()
    sourceSets {
        getByName("main") {
            // Include commonMain resources in Android builds
            resources.srcDirs("src/commonMain/resources")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
