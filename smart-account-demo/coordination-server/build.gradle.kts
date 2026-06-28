import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

// Match the :stellar-sdk JVM target (JVM 17 bytecode). The toolchain JDK that runs the build may
// be newer; only the emitted bytecode level is pinned, matching the SDK's jvm target.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Ktor 3.x (current stable line). Pure relay server: no :stellar-sdk dependency.
val ktorVersion = "3.5.0"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

application {
    mainClass.set("com.soneso.smartdemo.coordination.MainKt")
}

// Forward the invoking shell's COORDINATION_* environment to the launched JVM so the
// server's configuration (COORDINATION_TOKEN, COORDINATION_PORT, COORDINATION_STORE)
// reaches the application through `gradle run`. environmentVariablesPrefixedBy reads the
// actual build invocation's environment, so the values are not lost to a reused daemon's
// stale process environment.
tasks.named<JavaExec>("run") {
    environment(providers.environmentVariablesPrefixedBy("COORDINATION_").get())
}
