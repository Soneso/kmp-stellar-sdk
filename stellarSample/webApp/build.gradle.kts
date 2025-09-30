plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            webpackTask {
                outputFileName = "stellar-sample.js"
            }
            runTask {
                outputFileName = "stellar-sample.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":stellarSample:shared"))
                implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.11.0")
                implementation(npm("libsodium-wrappers", "0.7.15"))
            }
        }
    }
}
