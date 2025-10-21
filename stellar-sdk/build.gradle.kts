plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()

            // Configure system properties for tests
            systemProperty("javax.net.ssl.trustStore", System.getProperty("javax.net.ssl.trustStore", ""))
            systemProperty("javax.net.ssl.trustStorePassword", System.getProperty("javax.net.ssl.trustStorePassword", ""))

            // Enable TLS debugging if needed (uncomment for troubleshooting)
            // systemProperty("javax.net.debug", "ssl,handshake")
        }
    }

    // JS target (Browser and Node.js)
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    // Use our custom karma config
                    useConfigDirectory(project.projectDir)
                }
            }
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "600s"  // Increased for integration tests with multiple network calls and delays
                }
            }
        }
        compilations["test"].defaultSourceSet {
            resources.srcDir("src/jsTest/resources")
        }
        // Generate both library (for consumption) and executable (for tests)
        binaries.library()
        binaries.executable()
    }

    // Configure NODE_PATH for test environment
    tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest> {
        if (name == "jsNodeTest") {
            // Set NODE_PATH to include the build/js/node_modules directory
            val nodePath = project.rootProject.projectDir.resolve("build/js/node_modules").absolutePath
            environment("NODE_PATH", nodePath)
        }
    }

    // Configure JS test resource processing
    tasks.named("jsTestProcessResources") {
        (this as ProcessResources).duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    // Fix Gradle 9.0.0 task dependency validation for JS binary compilation
    // When both library() and executable() binaries are configured, we need explicit dependencies
    // to avoid implicit dependency warnings between the compile sync and distribution tasks
    tasks.named("jsBrowserProductionLibraryDistribution") {
        dependsOn("jsProductionExecutableCompileSync")
    }
    tasks.named("jsNodeProductionLibraryDistribution") {
        dependsOn("jsProductionExecutableCompileSync")
    }
    tasks.named("jsBrowserProductionWebpack") {
        dependsOn("jsProductionLibraryCompileSync")
    }


    // iOS targets
    val iosX64 = iosX64()
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()

    // Skip iosX64 tests - libsodium.a only has ARM64, not x86_64
    // x86_64 simulators are rare now with Apple Silicon
    tasks.matching { it.name.contains("iosX64Test") }.configureEach {
        enabled = false
    }

    // macOS targets (useful for development)
    macosX64()
    macosArm64()

    // iOS Framework configuration for Xcode
    listOf(
        iosX64,
        iosArm64,
        iosSimulatorArm64
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "stellar_sdk"
            isStatic = true
            // Force load libsodium
            linkerOpts += "-Wl,-all_load"
        }
    }

    // Configure C interop for libsodium
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops {
                val libsodium by creating {
                    defFile(project.file("src/nativeInterop/cinterop/libsodium.def"))
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("io.ktor:ktor-client-core:2.3.8")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.8")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")
                // BigInteger support for multiplatform
                implementation("com.ionspin.kotlin:bignum:0.3.9")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
                implementation("io.ktor:ktor-client-mock:2.3.8")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:2.3.8")
                implementation("org.bouncycastle:bcprov-jdk18on:1.78")
                implementation("commons-codec:commons-codec:1.16.1")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                // Add SLF4J implementation to fix logging warnings
                implementation("org.slf4j:slf4j-simple:2.0.9")
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:2.3.8")
                // Use libsodium-wrappers-sumo instead of standard build
                // The sumo build includes all functions including crypto_hash_sha256
                // which is needed for SHA-256 hashing (used in contract deployment)
                implementation(npm("libsodium-wrappers-sumo", "0.7.13"))
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)

            // Using libsodium for all native platforms
            kotlin.srcDir("src/nativeMain/kotlin")
        }

        val nativeTest by creating {
            dependsOn(commonTest)
            kotlin.srcDir("src/nativeTest/kotlin")
        }

        val iosMain by creating {
            dependsOn(nativeMain)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.8")
            }
        }

        val iosTest by creating {
            dependsOn(nativeTest)
        }

        // Configure iOS targets to use shared source sets
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

        val iosX64Test by getting { dependsOn(iosTest) }
        val iosArm64Test by getting { dependsOn(iosTest) }
        val iosSimulatorArm64Test by getting { dependsOn(iosTest) }

        // macOS source sets
        val macosMain by creating {
            dependsOn(nativeMain)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.8")
            }
        }

        val macosTest by creating {
            dependsOn(nativeTest)
        }

        val macosX64Main by getting { dependsOn(macosMain) }
        val macosArm64Main by getting { dependsOn(macosMain) }

        val macosX64Test by getting { dependsOn(macosTest) }
        val macosArm64Test by getting { dependsOn(macosTest) }
    }
}
