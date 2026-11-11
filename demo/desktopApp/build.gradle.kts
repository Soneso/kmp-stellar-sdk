import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvm()

    sourceSets {
        getByName("jvmMain") {
            dependencies {
                implementation(project(":demo:shared"))
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose {
    desktop {
        application {
            mainClass = "com.soneso.demo.desktop.MainKt"

            nativeDistributions {
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                packageName = "Stellar SDK Demo"  // Display name in Finder
                // Desktop native distributions require semantic versioning (MAJOR.MINOR.PATCH)
                // Strip any suffix like "-alpha" for packaging
                packageVersion = (project.property("demo.version") as String).substringBefore("-")
                description = "Stellar SDK Demo Application (Desktop Edition)"
                vendor = "Stellar Development Foundation"

                // Unique bundle identifier for JVM desktop app
                macOS {
                    bundleID = "com.soneso.stellar.demo.desktop"
                    // Icons are optional - will use default if not provided
                    // iconFile.set(project.file("icon.icns"))
                }

                windows {
                    // Windows doesn't use bundle IDs
                    // iconFile.set(project.file("icon.ico"))
                }

                linux {
                    packageName = "stellar-demo-desktop"
                    // iconFile.set(project.file("icon.png"))
                }
            }
        }
    }
}
