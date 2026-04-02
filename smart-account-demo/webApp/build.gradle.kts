plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                devServer = null
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":smart-account-demo:shared"))
                implementation(compose.ui)
                implementation(compose.runtime)
                implementation(compose.foundation)
            }
        }
    }
}

// Disable the webpack dev server tasks - Vite handles serving
tasks.named("jsBrowserDevelopmentRun") {
    enabled = false
}

tasks.named("jsBrowserProductionRun") {
    enabled = false
}

// Vite development task
tasks.register<Exec>("viteDev") {
    dependsOn("jsBrowserDevelopmentExecutableDistribution")
    group = "application"
    description = "Run Vite development server"

    workingDir = projectDir

    commandLine = if (System.getProperty("os.name").lowercase().contains("windows")) {
        listOf("cmd", "/c", "npx", "vite", "dev")
    } else {
        listOf("npx", "vite", "dev")
    }
}

// Production dist task
tasks.register<Copy>("productionDist") {
    dependsOn("jsBrowserProductionWebpack")
    group = "build"
    description = "Copy production webpack bundle to dist/ for deployment"

    from(file("$projectDir/build/kotlin-webpack/js/productionExecutable"))
    into(file("$projectDir/dist"))
    include("**/*.js", "**/*.wasm", "**/*.html")

    from(file("$projectDir/build/processedResources/js/main"))
    into(file("$projectDir/dist"))
    include("index.html")
}

// Vite preview task
tasks.register<Exec>("vitePreview") {
    dependsOn("jsBrowserProductionWebpack", "productionDist")
    group = "application"
    description = "Preview production webpack bundle with Vite server"

    workingDir = file("$projectDir/dist")

    commandLine = if (System.getProperty("os.name").lowercase().contains("windows")) {
        listOf("cmd", "/c", "npx", "vite", "preview", "--outDir", ".", "--port", "8083")
    } else {
        listOf("npx", "vite", "preview", "--outDir", ".", "--port", "8083")
    }
}
