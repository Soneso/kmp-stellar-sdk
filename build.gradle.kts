buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.12.3")
    }
}

plugins {
    kotlin("multiplatform") version "2.2.20" apply false
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
    kotlin("plugin.compose") version "2.2.20" apply false
    kotlin("android") version "2.2.20" apply false
    id("org.jetbrains.compose") version "1.9.1" apply false
    id("org.jetbrains.dokka") version "2.1.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.4" apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

allprojects {
    group = "com.soneso.stellar"
    version = "1.10.0"

    repositories {
        mavenLocal()  // For testing locally published artifacts
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                // Prevent Gradle from resolving Node.js/Yarn artifacts from this repo.
                // The Kotlin/JS plugin resolves org.nodejs and com.yarnpkg from its own
                // repository. When JetBrains Space returns 503, Gradle treats it as a
                // hard error instead of "not found", breaking JS builds.
                excludeGroup("org.nodejs")
                excludeGroup("com.yarnpkg")
            }
        }
    }
}

// Configure Nexus publishing for Maven Central via Central Portal
nexusPublishing {
    repositories {
        sonatype {
            // Use Central Portal OSSRH Staging API (OSSRH shut down June 30, 2025)
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME"))
            password.set(project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD"))
        }
    }
}
