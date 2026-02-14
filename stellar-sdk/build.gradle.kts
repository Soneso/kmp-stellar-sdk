plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover")
    id("maven-publish")
    id("signing")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()

            // Exclude integration tests in CI (they require network access to Stellar Testnet)
            // CI passes -PexcludeIntegrationTests; local/IDE runs include them by default
            if (project.hasProperty("excludeIntegrationTests")) {
                exclude("**/integrationTests/**")
            }

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

    // Exclude integration tests from JS and Native test tasks in CI
    // CI passes -PexcludeIntegrationTests; local/IDE runs include them by default
    if (project.hasProperty("excludeIntegrationTests")) {
        tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest> {
            filter.excludeTestsMatching("com.soneso.stellar.sdk.integrationTests.*")
        }
        tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest> {
            filter.excludeTestsMatching("com.soneso.stellar.sdk.integrationTests.*")
        }
    }

    // Pass project directory to native tests so they can find test resources.
    // iOS simulator runs from a different CWD (CoreSimulator sandbox), so relative paths fail.
    // SIMCTL_CHILD_ prefix makes simctl forward the env var to the spawned process.
    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest> {
        environment("PROJECT_DIR", project.projectDir.absolutePath)
        environment("SIMCTL_CHILD_PROJECT_DIR", project.projectDir.absolutePath)
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
    val libsodiumIosDir = project.projectDir.resolve("native-libs/libsodium-ios")
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops {
                val libsodium by creating {
                    defFile(project.file("src/nativeInterop/cinterop/libsodium.def"))
                    // iOS targets: pass include path for header resolution
                    if (konanTarget.family == org.jetbrains.kotlin.konan.target.Family.IOS) {
                        compilerOpts("-I${libsodiumIosDir.resolve("include")}")
                    }
                }
            }
        }
        // iOS targets: set linker opts on ALL binaries (framework, test, executable)
        // Note: cinterop's linkerOpts() is silently ignored — linker opts must go here
        if (konanTarget.family == org.jetbrains.kotlin.konan.target.Family.IOS) {
            binaries.all {
                if (konanTarget == org.jetbrains.kotlin.konan.target.KonanTarget.IOS_SIMULATOR_ARM64) {
                    linkerOpts("-Wl,-force_load,${libsodiumIosDir.resolve("lib/libsodium.a")}")
                } else {
                    linkerOpts("${libsodiumIosDir.resolve("lib/libsodium.a")}")
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("io.ktor:ktor-client-core:3.3.2")
                implementation("io.ktor:ktor-client-content-negotiation:3.3.2")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.2")
                // BigInteger support for multiplatform
                implementation("com.ionspin.kotlin:bignum:0.3.10")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
                implementation("io.ktor:ktor-client-mock:3.3.2")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.3.2")
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
                implementation("io.ktor:ktor-client-js:3.3.2")
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
                implementation("io.ktor:ktor-client-darwin:3.3.2")
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
                implementation("io.ktor:ktor-client-darwin:3.3.2")
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

// Kover Code Coverage Configuration
kover {
    reports {
        // Filter out integration test classes from coverage reports
        filters {
            excludes {
                packages("com.soneso.stellar.sdk.integrationTests")
            }
        }
    }
}

// Dokka 2.x generates docs from shared source sets without requiring native compilation
// No special configuration needed for cross-compilation on CI

// Dokka Configuration for API Documentation (V2 mode)
tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
    from(tasks.named("dokkaGeneratePublicationHtml").get().outputs)
}

// Maven Publishing Configuration
publishing {
    publications.withType<MavenPublication> {
        // Add javadoc JAR to all publications
        artifact(tasks.named("javadocJar"))

        // Configure POM for all publications (KMP creates multiple publications automatically)
        pom {
            name.set("Stellar SDK for Kotlin Multiplatform")
            description.set("Kotlin Multiplatform Stellar SDK")
            url.set("https://github.com/Soneso/kmp-stellar-sdk")

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://github.com/Soneso/kmp-stellar-sdk/blob/main/LICENSE")
                    distribution.set("https://github.com/Soneso/kmp-stellar-sdk/blob/main/LICENSE")
                }
            }

            developers {
                developer {
                    id.set("soneso")
                    name.set("Soneso")
                    email.set("info@soneso.com")
                    organization.set("Soneso")
                    organizationUrl.set("https://github.com/Soneso")
                }
            }

            scm {
                url.set("https://github.com/Soneso/kmp-stellar-sdk")
                connection.set("scm:git:https://github.com/Soneso/kmp-stellar-sdk.git")
                developerConnection.set("scm:git:ssh://git@github.com/Soneso/kmp-stellar-sdk.git")
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            // Updated to use Central Portal OSSRH Staging API (OSSRH shut down June 30, 2025)
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

// Signing Configuration
signing {
    // Only require signing when publishing to Maven Central (not for local builds/tests)
    val publishTasks = listOf("publishAllPublicationsToOSSRHRepository", "publishToOSSRH", "publishToSonatype", "Sonatype")
    isRequired = gradle.startParameter.taskNames.any { taskName ->
        publishTasks.any { taskName.contains(it) }
    }

    // Use GPG command-line tool for signing
    // This works with both Ed25519 and RSA keys
    // Signing will only be attempted if isRequired is true
    if (isRequired) {
        useGpgCmd()
        sign(publishing.publications)
    }
}

// Fix Gradle 9.0.0 task dependency validation
// All signing tasks share the javadocJar, so they must run sequentially
afterEvaluate {
    val signTasks = tasks.matching { it.name.startsWith("sign") && it.name.endsWith("Publication") }
    val publishTasks = tasks.matching {
        it.name.startsWith("publish") &&
        (it.name.endsWith("PublicationToOSSRHRepository") || it.name.endsWith("PublicationToSonatypeRepository"))
    }

    // Make all publish tasks depend on all sign tasks
    publishTasks.configureEach {
        signTasks.forEach { signTask ->
            mustRunAfter(signTask)
        }
    }
}
