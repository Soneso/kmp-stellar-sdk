package com.soneso.web

import com.soneso.sample.StellarDemo
import com.soneso.sample.KeyPairInfo
import com.soneso.sample.TestResult
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

private val demo = StellarDemo()
private var currentKeypair: KeyPairInfo? = null

fun main() {
    console.log("Initializing app...")

    // Initialize app with coroutine support
    MainScope().launch {
        initializeApp()
    }
}

suspend fun initializeApp() {
    val root = document.getElementById("root") as? HTMLDivElement
    if (root == null) {
        console.error("Root element not found")
        return
    }

    root.innerHTML = ""

    root.append {
        div("container") {
            h1 { +"Stellar KMP Sample" }

            // SDK Info Card
            div("card info-card") {
                h2 { +"Stellar SDK for Kotlin Multiplatform" }
                p {
                    +"This sample demonstrates shared business logic across Android, iOS, and Web platforms. "
                    +"All three platforms use the same Kotlin code for cryptographic operations."
                }
            }

            // KeyPair Card
            div("card") {
                h2 { +"KeyPair Operations" }

                div("button-group") {
                    button(classes = "btn btn-primary") {
                        +"Generate Random"
                        onClickFunction = {
                            MainScope().launch {
                                generateRandom()
                            }
                        }
                    }
                    button(classes = "btn btn-primary") {
                        +"From Seed"
                        onClickFunction = {
                            MainScope().launch {
                                generateFromSeed()
                            }
                        }
                    }
                }

                div {
                    id = "keypair-info"
                }
            }

            // Test Suite Card
            div("card") {
                h2 { +"Test Suite" }

                button(classes = "btn btn-primary") {
                    id = "run-tests-btn"
                    +"Run Tests"
                    onClickFunction = {
                        MainScope().launch {
                            runTests()
                        }
                    }
                }

                div {
                    id = "test-results"
                }
            }
        }
    }
}

suspend fun generateRandom() {
    try {
        currentKeypair = demo.generateRandomKeyPair()
        displayKeypair(currentKeypair!!)
    } catch (e: Exception) {
        console.error("Error generating keypair:", e)
        showError("keypair-info", "Error generating keypair: ${e.message}")
    }
}

suspend fun generateFromSeed() {
    try {
        val testSeed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        val result = demo.createFromSeed(testSeed)

        if (result.isSuccess) {
            currentKeypair = result.getOrNull()
            displayKeypair(currentKeypair!!)
        } else {
            showError("keypair-info", "Error: ${result.exceptionOrNull()?.message}")
        }
    } catch (e: Exception) {
        console.error("Error creating from seed:", e)
        showError("keypair-info", "Error: ${e.message}")
    }
}

fun displayKeypair(keypair: KeyPairInfo) {
    val container = document.getElementById("keypair-info") as? HTMLDivElement ?: return

    container.innerHTML = ""
    container.append {
        div("keypair-details") {
            div("info-row") {
                div("info-label") { +"Account ID:" }
                div("info-value monospace") { +keypair.accountId }
            }

            keypair.secretSeed?.let {
                div("info-row") {
                    div("info-label") { +"Secret Seed:" }
                    div("info-value") { +"S${"*".repeat(55)}" }
                }
            }

            div("info-row") {
                div("info-label") { +"Can Sign:" }
                div("info-value") { +if (keypair.canSign) "Yes" else "No" }
            }

            div("info-row") {
                div("info-label") { +"Crypto Library:" }
                div("info-value") { +keypair.cryptoLibrary }
            }
        }
    }
}

suspend fun runTests() {
    val btn = document.getElementById("run-tests-btn") as? HTMLButtonElement
    val resultsContainer = document.getElementById("test-results") as? HTMLDivElement ?: return

    btn?.disabled = true
    btn?.textContent = "Running..."

    resultsContainer.innerHTML = ""
    resultsContainer.append {
        div("loading") { +"Running tests..." }
    }

    // Run tests asynchronously
    try {
        val results = demo.runTestSuite()
        displayTestResults(results)
    } catch (e: Exception) {
        console.error("Error running tests:", e)
        showError("test-results", "Error running tests: ${e.message}")
    } finally {
        btn?.disabled = false
        btn?.textContent = "Run Tests"
    }
}

fun displayTestResults(results: List<TestResult>) {
    val container = document.getElementById("test-results") as? HTMLDivElement ?: return

    val passedCount = results.count { it.passed }
    val totalCount = results.size

    container.innerHTML = ""
    container.append {
        div("test-summary ${if (passedCount == totalCount) "success" else "error"}") {
            +"Results: $passedCount/$totalCount tests passed"
        }

        div("test-list") {
            results.forEach { result ->
                div("test-item ${if (result.passed) "test-pass" else "test-fail"}") {
                    div("test-status") {
                        +if (result.passed) "✓" else "✗"
                    }
                    div("test-details") {
                        div("test-name") { +result.name }
                        div("test-message") { +result.message }
                    }
                    div("test-duration") {
                        +"${result.duration}ms"
                    }
                }
            }
        }
    }
}

fun showError(containerId: String, message: String) {
    val container = document.getElementById(containerId) as? HTMLDivElement ?: return

    container.innerHTML = ""
    container.append {
        div("error") { +message }
    }
}
