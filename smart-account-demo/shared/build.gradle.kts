plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
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
            export(project(":stellar-sdk"))
        }
    }

    listOf(
        macosX64(),
        macosArm64()
    ).forEach { macosTarget ->
        macosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
            export(project(":stellar-sdk"))
        }
    }

    sourceSets {
        val commonMain by getting {
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
                implementation("cafe.adriel.voyager:voyager-navigator:1.1.0-beta02")
                implementation("cafe.adriel.voyager:voyager-transitions:1.1.0-beta02")

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
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }

        // ============================================================
        // Android Platform
        // ============================================================
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.appcompat:appcompat:1.6.1")
            }
        }

        // ============================================================
        // iOS Platform
        // ============================================================
        val iosMain by creating {
            dependsOn(commonMain)
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
                implementation(compose.html.core)
            }
        }
    }
}

android {
    namespace = "com.soneso.smartdemo.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Android doesn't automatically include commonMain/resources in the APK.
    // This makes WASM files accessible via ClassLoader.getResourceAsStream()
    // at runtime (used by WasmResource.android.kt to load token contract WASM).
    sourceSets {
        getByName("main") {
            resources.srcDirs("src/commonMain/resources")
        }
    }
}
