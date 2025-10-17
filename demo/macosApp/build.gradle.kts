// This module is built using Xcode via xcodegen
// The Gradle build only generates the framework dependency

tasks.register<Exec>("openXcode") {
    group = "build"
    description = "Generate Xcode project and open it"

    workingDir = projectDir

    commandLine("sh", "-c", """
        xcodegen generate
        open StellarDemo.xcodeproj
    """.trimIndent())
}

tasks.register<Exec>("buildFramework") {
    group = "build"
    description = "Build the shared framework for macOS"

    workingDir = rootProject.projectDir

    val arch = System.getProperty("os.arch")
    val task = if (arch == "aarch64" || arch == "arm64") {
        ":demo:shared:linkDebugFrameworkMacosArm64"
    } else {
        ":demo:shared:linkDebugFrameworkMacosX64"
    }

    commandLine("./gradlew", task)
}
