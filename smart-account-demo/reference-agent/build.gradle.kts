import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

// Match the :stellar-sdk JVM target (JVM 17 bytecode). The toolchain JDK that runs the build may
// be newer; only the emitted bytecode level is pinned, matching the SDK's jvm target so the SDK's
// JVM variant is consumable from this kotlin("jvm") module.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Ktor 3.x (current stable line). Client side: the agent talks to the coordination server.
val ktorVersion = "3.5.0"

dependencies {
    // KMP SDK JVM variant. Headless connect and the multi-signer pipeline come from here.
    implementation(project(":stellar-sdk"))

    // Exposed on the SDK's amount/i128 helpers (OZTransactionOperations.amountToBaseUnits returns
    // a BigInteger, Scv.toInt128 consumes one), so the agent needs the type on its own classpath.
    implementation("com.ionspin.kotlin:bignum:0.3.10")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    // Gated end-to-end test only: drives the REAL coordination server in-process
    // (RUN_COORDINATION_E2E=true). Pulls in the actual server module plus the Ktor
    // server engine to host it; skipped, and never loaded, on a default test run.
    testImplementation(project(":smart-account-demo:coordination-server"))
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-cio:$ktorVersion")
}

application {
    mainClass.set("com.soneso.smartdemo.agent.MainKt")
}

// Forward the invoking shell's AGENT_* environment to the launched JVM so the agent's
// gates and configuration (AGENT_PRINT_KEY, AGENT_RUN_LIVE, AGENT_SMART_ACCOUNT, ...)
// reach the application through `gradle run`. environmentVariablesPrefixedBy reads the
// actual build invocation's environment, so the values are not lost to a reused daemon's
// stale process environment.
tasks.named<JavaExec>("run") {
    environment(providers.environmentVariablesPrefixedBy("AGENT_").get())
}
