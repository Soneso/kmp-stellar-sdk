plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    js(IR) {
        browser {
            // Use webpack only for bundling Kotlin modules, Vite handles serving/optimization
            commonWebpackConfig {
                // Don't use webpack dev server - Vite will handle that
                devServer = null

                // Minimal output configuration - just bundle the modules
                outputFileName = "app.js"
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

// Disable the webpack dev server tasks - we'll use Vite
tasks.named("jsBrowserDevelopmentRun") {
    enabled = false
}

tasks.named("jsBrowserProductionRun") {
    enabled = false
}

// Add Vite development task
tasks.register<Exec>("viteDev") {
    dependsOn("jsBrowserDevelopmentExecutableDistribution")
    group = "application"
    description = "Run Vite development server"

    workingDir = projectDir

    // Use npx to run vite, ensuring it's available
    commandLine = if (System.getProperty("os.name").lowercase().contains("windows")) {
        listOf("cmd", "/c", "npx", "vite", "dev")
    } else {
        listOf("npx", "vite", "dev")
    }
}

// Add production dist task (copies webpack output to dist/ for deployment)
tasks.register<Copy>("productionDist") {
    dependsOn("jsBrowserProductionWebpack")
    group = "build"
    description = "Copy production webpack bundle to dist/ for deployment"

    from(file("$projectDir/build/kotlin-webpack/js/productionExecutable"))
    into(file("$projectDir/dist"))
    include("**/*.js", "**/*.wasm", "**/*.html")

    // Also copy the index.html
    from(file("$projectDir/build/processedResources/js/main"))
    into(file("$projectDir/dist"))
    include("index.html")

    doLast {
        // Update index.html to reference all chunks
        val indexFile = file("$projectDir/dist/index.html")
        if (indexFile.exists()) {
            var content = indexFile.readText()

            // Replace single app.js with all chunks
            val chunks = listOf("app-kotlin-stdlib.js", "app-vendors.js", "app.js")
            val scriptTags = chunks.joinToString("\n    ") { "<script src=\"$it\"></script>" }

            content = content.replace(
                """<script src="app.js"></script>""",
                scriptTags
            )

            indexFile.writeText(content)
        }
    }
}

// Add Vite preview task for testing production build
tasks.register<Exec>("vitePreview") {
    dependsOn("jsBrowserProductionWebpack", "productionDist")
    group = "application"
    description = "Preview production webpack bundle with Vite server"

    workingDir = file("$projectDir/dist")

    // Use vite preview to serve the dist directory
    commandLine = if (System.getProperty("os.name").lowercase().contains("windows")) {
        listOf("cmd", "/c", "npx", "vite", "preview", "--outDir", ".", "--port", "8082")
    } else {
        listOf("npx", "vite", "preview", "--outDir", ".", "--port", "8082")
    }
}
