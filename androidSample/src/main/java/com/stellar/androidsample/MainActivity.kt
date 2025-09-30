package com.stellar.androidsample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stellar.androidsample.ui.theme.StellarSampleTheme
import com.stellar.sdk.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StellarSampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StellarSDKDemo()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StellarSDKDemo() {
    var accountId by remember { mutableStateOf("") }
    var testResults by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var isRunningTests by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stellar KMP SDK") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SDK Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Stellar SDK for Android",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    InfoRow("Platform", "Android (JVM)")
                    InfoRow("Crypto Library", KeyPair.getCryptoLibraryName())
                    InfoRow("Algorithm", "Ed25519 (RFC 8032)")
                }
            }

            // Generate Keypair Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Generate Keypair",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                accountId = withContext(Dispatchers.Default) {
                                    KeyPair.random().getAccountId()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate New Keypair")
                    }

                    if (accountId.isNotEmpty()) {
                        Text(
                            text = "Account ID:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                clipboardManager.setText(AnnotatedString(accountId))
                            }
                        ) {
                            Text(
                                text = accountId,
                                modifier = Modifier.padding(12.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "Tap to copy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Test Suite Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Test Suite",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isRunningTests = true
                                testResults = withContext(Dispatchers.Default) {
                                    runComprehensiveTests()
                                }
                                isRunningTests = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRunningTests
                    ) {
                        if (isRunningTests) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isRunningTests) "Running Tests..." else "Run Comprehensive Tests")
                    }

                    if (testResults.isNotEmpty()) {
                        val passedCount = testResults.count { it.passed }
                        val totalCount = testResults.size

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (passedCount == totalCount)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "Results: $passedCount/$totalCount tests passed",
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        testResults.forEach { result ->
                            TestResultCard(result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TestResultCard(result: TestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.passed)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (result.passed) "✓ PASS" else "✗ FAIL",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (result.passed)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
            if (result.message.isNotEmpty()) {
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Time: ${result.durationMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class TestResult(
    val name: String,
    val passed: Boolean,
    val message: String,
    val durationMs: Long
)

fun runComprehensiveTests(): List<TestResult> {
    val results = mutableListOf<TestResult>()

    // Test 1: Random KeyPair Generation
    results.add(runTest("Random KeyPair Generation") {
        val kp1 = KeyPair.random()
        val kp2 = KeyPair.random()
        require(kp1.getAccountId() != kp2.getAccountId()) { "Keypairs should be unique" }
        require(kp1.canSign()) { "Generated keypair should be able to sign" }
        "Generated unique keypairs successfully"
    })

    // Test 2: KeyPair from Secret Seed
    results.add(runTest("KeyPair from Secret Seed") {
        val seed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        val expected = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        val keypair = KeyPair.fromSecretSeed(seed)
        require(keypair.getAccountId() == expected) { "Account ID mismatch" }
        require(keypair.canSign()) { "Should be able to sign" }
        "Derived correct account ID from seed"
    })

    // Test 3: KeyPair from Account ID
    results.add(runTest("KeyPair from Account ID") {
        val accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        val keypair = KeyPair.fromAccountId(accountId)
        require(keypair.getAccountId() == accountId) { "Account ID mismatch" }
        require(!keypair.canSign()) { "Public-only keypair should not be able to sign" }
        "Created public-only keypair successfully"
    })

    // Test 4: Sign and Verify
    results.add(runTest("Sign and Verify") {
        val keypair = KeyPair.random()
        val message = "Hello Stellar".toByteArray()
        val signature = keypair.sign(message)
        require(signature.size == 64) { "Signature should be 64 bytes" }
        require(keypair.verify(message, signature)) { "Signature verification failed" }
        "Signature created and verified successfully"
    })

    // Test 5: Cross-KeyPair Verification
    results.add(runTest("Cross-KeyPair Verification") {
        val signingKeypair = KeyPair.random()
        val message = "Test message".toByteArray()
        val signature = signingKeypair.sign(message)

        val verifyingKeypair = KeyPair.fromAccountId(signingKeypair.getAccountId())
        require(verifyingKeypair.verify(message, signature)) { "Cross-keypair verification failed" }
        "Public-only keypair verified signature successfully"
    })

    // Test 6: Invalid Secret Seed
    results.add(runTest("Invalid Secret Seed") {
        try {
            KeyPair.fromSecretSeed("INVALID_SEED")
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            "Correctly rejected invalid seed"
        }
    })

    // Test 7: Invalid Account ID
    results.add(runTest("Invalid Account ID") {
        try {
            KeyPair.fromAccountId("GINVALID")
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            "Correctly rejected invalid account ID"
        }
    })

    // Test 8: Memory Safety Test
    results.add(runTest("Memory Safety (100 keypairs)") {
        val keypairs = (1..100).map { KeyPair.random() }
        require(keypairs.map { it.getAccountId() }.distinct().size == 100) {
            "All keypairs should be unique"
        }
        "Generated 100 unique keypairs successfully"
    })

    // Test 9: Crypto Library Info
    results.add(runTest("Crypto Library Info") {
        val libName = KeyPair.getCryptoLibraryName()
        require(libName.isNotEmpty()) { "Library name should not be empty" }
        "Using: $libName"
    })

    // Test 10: Sign Without Private Key
    results.add(runTest("Sign Without Private Key") {
        try {
            val keypair = KeyPair.fromAccountId("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D")
            keypair.sign("test".toByteArray())
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalStateException) {
            "Correctly prevented signing without private key"
        }
    })

    return results
}

fun runTest(name: String, test: () -> String): TestResult {
    val startTime = System.currentTimeMillis()
    return try {
        val message = test()
        val duration = System.currentTimeMillis() - startTime
        TestResult(name, true, message, duration)
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        TestResult(name, false, "Error: ${e.message}", duration)
    }
}
