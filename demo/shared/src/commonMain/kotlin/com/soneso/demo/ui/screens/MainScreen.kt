package com.soneso.demo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

data class DemoTopic(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val screen: Screen
)

class MainScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val demoTopics = listOf(
            DemoTopic(
                title = "Key Generation",
                description = "Generate and manage Stellar keypairs",
                icon = Icons.Default.Key,
                screen = KeyGenerationScreen()
            ),
            DemoTopic(
                title = "Fund Testnet Account",
                description = "Get test XLM from Friendbot for testnet development",
                icon = Icons.Default.AccountBalance,
                screen = FundAccountScreen()
            ),
            DemoTopic(
                title = "Fetch Account Details",
                description = "Retrieve comprehensive account information from Horizon",
                icon = Icons.Default.Person,
                screen = AccountDetailsScreen()
            ),
            DemoTopic(
                title = "Trust Asset",
                description = "Establish a trustline to hold non-native assets",
                icon = Icons.Default.AttachMoney,
                screen = TrustAssetScreen()
            ),
            DemoTopic(
                title = "Send a Payment",
                description = "Transfer XLM or issued assets to another account",
                icon = Icons.AutoMirrored.Filled.Send,
                screen = SendPaymentScreen()
            ),
            DemoTopic(
                title = "Fetch Transaction Details",
                description = "Retrieve transaction information from Horizon or Soroban RPC",
                icon = Icons.Default.Receipt,
                screen = FetchTransactionScreen()
            ),
            DemoTopic(
                title = "Fetch Smart Contract Details",
                description = "Parse contract WASM to view metadata and specification",
                icon = Icons.Default.Code,
                screen = ContractDetailsScreen()
            ),
            DemoTopic(
                title = "Deploy a Smart Contract",
                description = "Upload and deploy Soroban contracts with constructor support",
                icon = Icons.Default.CloudUpload,
                screen = DeployContractScreen()
            ),
            DemoTopic(
                title = "Invoke Hello World Contract",
                description = "Invoke a deployed contract using the beginner-friendly API",
                icon = Icons.Default.PlayArrow,
                screen = InvokeHelloWorldContractScreen()
            ),
            DemoTopic(
                title = "Invoke Auth Contract",
                description = "Dynamic authorization handling: same-invoker vs different-invoker scenarios",
                icon = Icons.Default.VerifiedUser,
                screen = InvokeAuthContractScreen()
            )
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Stellar SDK Demo") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(demoTopics) { topic ->
                    DemoTopicCard(
                        topic = topic,
                        onClick = { navigator.push(topic.screen) }
                    )
                }
            }
        }
    }
}

@Composable
fun DemoTopicCard(
    topic: DemoTopic,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = topic.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = topic.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
