import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
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
                packageName = "StellarDemo"
                packageVersion = "1.0.0"

                macOS {
                    iconFile.set(project.file("icon.icns"))
                }
                windows {
                    iconFile.set(project.file("icon.ico"))
                }
                linux {
                    iconFile.set(project.file("icon.png"))
                }
            }
        }
    }
}
