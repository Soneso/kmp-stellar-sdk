plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
}

kotlin {
    android {
        namespace = "com.soneso.smartdemo.shared"
        compileSdk = 35
        minSdk = 24
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        // Runs commonTest on the Android target as host tests.
        withHostTest { }
    }

    // No desktop/JVM target - passkeys are not supported on Desktop JVM

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
            export(project.dependencies.project(":stellar-sdk"))
        }
    }

    listOf(
        macosX64(),
        macosArm64()
    ).forEach { macosTarget ->
        macosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
            export(project.dependencies.project(":stellar-sdk"))
        }
    }

    sourceSets {
        val commonMain = getByName("commonMain") {
            dependencies {
                // ============================================================
                // UI Framework - Compose Multiplatform
                // ============================================================
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)

                // ============================================================
                // Navigation
                // ============================================================
                implementation("cafe.adriel.voyager:voyager-navigator:1.1.0-beta03")
                implementation("cafe.adriel.voyager:voyager-transitions:1.1.0-beta03")

                // ============================================================
                // Stellar SDK
                // ============================================================
                api(project(":stellar-sdk"))

                // ============================================================
                // BigInteger / BigDecimal (same version as stellar-sdk)
                // ============================================================
                implementation("com.ionspin.kotlin:bignum:0.3.10")

                // ============================================================
                // Coroutines
                // ============================================================
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

                // ============================================================
                // Date/Time
                // ============================================================
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

                // ============================================================
                // Coordination server client (agent-signer flow)
                // Ktor + kotlinx.serialization. Versions track the stellar-sdk.
                // ============================================================
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("io.ktor:ktor-client-core:3.5.2")
            }
        }

        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
                implementation("io.ktor:ktor-client-mock:3.5.2")
            }
        }

        // ============================================================
        // Android Platform
        // ============================================================
        getByName("androidMain") {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.appcompat:appcompat:1.6.1")

                // Ktor engine for the coordination client on Android.
                implementation("io.ktor:ktor-client-cio:3.5.2")

                // Reown (WalletConnect v2) for external wallet connection via Freighter Mobile.
                // Required by ReownConnector which implements WalletConnector for Android.
                // Reown 1.6.14 and later carry Kotlin 2.4 metadata and require compileSdk 37;
                // this build uses Kotlin 2.2.21 and compileSdk 35, so both stay on 1.6.12.
                implementation("com.reown:android-core:1.6.12")
                implementation("com.reown:sign:1.6.12")
            }
        }

        // ============================================================
        // iOS Platform
        // ============================================================
        val iosMain = create("iosMain") {
            dependsOn(commonMain)
            dependencies {
                // Ktor engine for the coordination client on iOS.
                implementation("io.ktor:ktor-client-darwin:3.3.3")
            }
        }

        getByName("iosX64Main") {
            dependsOn(iosMain)
        }

        getByName("iosArm64Main") {
            dependsOn(iosMain)
        }

        getByName("iosSimulatorArm64Main") {
            dependsOn(iosMain)
        }

        // ============================================================
        // macOS Platform
        // ============================================================
        val macosMain = create("macosMain") {
            dependsOn(commonMain)
            dependencies {
                // Ktor engine for the coordination client on macOS.
                implementation("io.ktor:ktor-client-darwin:3.3.3")
            }
        }

        getByName("macosX64Main") {
            dependsOn(macosMain)
        }

        getByName("macosArm64Main") {
            dependsOn(macosMain)
        }

        // ============================================================
        // JavaScript Platform (Browser)
        // ============================================================
        getByName("jsMain") {
            dependencies {
                implementation(compose.html.core)
                // Freighter browser extension API for web wallet signing
                implementation(npm("@stellar/freighter-api", "6.0.1"))

                // Ktor engine for the coordination client on the web.
                implementation("io.ktor:ktor-client-js:3.3.3")
            }
        }
    }
}
