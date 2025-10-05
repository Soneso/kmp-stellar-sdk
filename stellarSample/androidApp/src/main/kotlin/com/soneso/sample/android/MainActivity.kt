package com.soneso.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soneso.sample.StellarDemo
import com.soneso.sample.KeyPairInfo
import com.soneso.sample.SorobanDemoResult
import com.soneso.sample.TestResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                StellarSampleApp()
            }
        }
    }
}

@Composable
fun StellarSampleApp(viewModel: StellarViewModel = viewModel()) {
    val keypair by viewModel.keypair.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val isRunningTests by viewModel.isRunningTests.collectAsState()
    val sorobanResult by viewModel.sorobanResult.collectAsState()
    val isRunningSoroban by viewModel.isRunningSoroban.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stellar KMP Sample") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SDK Info Card
            SDKInfoCard()

            // KeyPair Generation Card
            KeyPairCard(
                keypair = keypair,
                onGenerateRandom = { viewModel.generateRandom() },
                onGenerateFromSeed = { viewModel.generateFromSeed() }
            )

            // Soroban Smart Contracts Card
            SorobanCard(
                result = sorobanResult,
                isRunning = isRunningSoroban,
                onNetworkInfo = { viewModel.runSorobanNetworkInfo() },
                onSimulation = { viewModel.runSorobanSimulation() },
                onFullFlow = { viewModel.runSorobanFullFlow() },
                onEventQuery = { viewModel.runSorobanEventQuery() }
            )

            // Test Suite Card
            TestSuiteCard(
                testResults = testResults,
                isRunning = isRunningTests,
                onRunTests = { viewModel.runTests() }
            )
        }
    }
}

@Composable
fun SDKInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Stellar SDK for Kotlin Multiplatform",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This sample demonstrates shared business logic across Android, iOS, and Web platforms.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun KeyPairCard(
    keypair: KeyPairInfo?,
    onGenerateRandom: () -> Unit,
    onGenerateFromSeed: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "KeyPair Operations",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateRandom,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Random")
                }
                Button(
                    onClick = onGenerateFromSeed,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("From Seed")
                }
            }

            if (keypair != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                KeyPairInfoDisplay(keypair)
            }
        }
    }
}

@Composable
fun KeyPairInfoDisplay(keypair: KeyPairInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow("Account ID:", keypair.accountId)
        val seed = keypair.secretSeed
        if (seed != null) {
            InfoRow("Secret Seed:", seed, isSecret = true)
        }
        InfoRow("Can Sign:", if (keypair.canSign) "Yes" else "No")
        InfoRow("Crypto Library:", keypair.cryptoLibrary)
    }
}

@Composable
fun SorobanCard(
    result: SorobanDemoResult?,
    isRunning: Boolean,
    onNetworkInfo: () -> Unit,
    onSimulation: () -> Unit,
    onFullFlow: () -> Unit,
    onEventQuery: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Soroban Smart Contracts",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Interact with smart contracts on Stellar testnet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Demo buttons in a grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNetworkInfo,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Network Info", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onSimulation,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Simulate", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onFullFlow,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Full Flow", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onEventQuery,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Query Events", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Running...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }

            if (result != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                SorobanResultDisplay(result)
            }
        }
    }
}

@Composable
fun SorobanResultDisplay(result: SorobanDemoResult) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Status header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (result.success) "Success" else "Failed",
                style = MaterialTheme.typography.titleSmall,
                color = if (result.success) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
            Text(
                text = "${result.duration}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
            )
        }

        Text(
            text = result.message,
            style = MaterialTheme.typography.bodyMedium
        )

        // Steps
        if (result.steps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth()
                ) {
                    result.steps.take(15).forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    if (result.steps.size > 15) {
                        Text(
                            text = "... and ${result.steps.size - 15} more steps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Additional data
        if (result.data.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            result.data.forEach { (key, value) ->
                InfoRow(label = "$key:", value = value)
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, isSecret: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = if (isSecret) "S${"*".repeat(55)}" else value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (!isSecret) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
fun TestSuiteCard(
    testResults: List<TestResult>,
    isRunning: Boolean,
    onRunTests: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Test Suite",
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = onRunTests,
                    enabled = !isRunning
                ) {
                    Text(if (isRunning) "Running..." else "Run Tests")
                }
            }

            if (testResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                val passedCount = testResults.count { it.passed }
                Text(
                    text = "Results: $passedCount/${testResults.size} tests passed",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (passedCount == testResults.size)
                        Color(0xFF4CAF50) else Color(0xFFF44336)
                )

                Spacer(modifier = Modifier.height(16.dp))

                testResults.forEach { result ->
                    TestResultItem(result)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun TestResultItem(result: TestResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (result.passed) "✓" else "✗",
            color = if (result.passed) Color(0xFF4CAF50) else Color(0xFFF44336),
            style = MaterialTheme.typography.titleMedium
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Text(
            text = "${result.duration}ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
