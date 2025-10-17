plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "stellarDemoJs.js"
                devServer = devServer?.copy(
                    port = 8081,
                    open = false
                )
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":demo:shared"))
                implementation(compose.ui)
                implementation(compose.runtime)
                implementation(compose.foundation)
            }
        }
    }
}
