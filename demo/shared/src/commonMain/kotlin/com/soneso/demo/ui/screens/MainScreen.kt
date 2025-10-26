package com.soneso.demo.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.ui.components.StellarTopBar

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
                title = "Send Payment",
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
                title = "Fetch Contract Details",
                description = "Parse contract WASM to view metadata and specification",
                icon = Icons.Default.Code,
                screen = ContractDetailsScreen()
            ),
            DemoTopic(
                title = "Deploy Smart Contract",
                description = "Upload and deploy Soroban contracts with constructor support",
                icon = Icons.Default.CloudUpload,
                screen = DeployContractScreen()
            ),
            DemoTopic(
                title = "Invoke Hello World",
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
                StellarTopBar(
                    title = "KMP Stellar SDK Demo",
                    subtitle = "Explore SDK Features on Testnet",
                    showBackButton = false
                )
            }
        ) { paddingValues ->
            // Centered content container with max width
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
}

@Composable
fun DemoTopicCard(
    topic: DemoTopic,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val elevation by animateDpAsState(
        targetValue = when {
            isPressed -> 1.dp
            isHovered -> 8.dp
            else -> 2.dp
        },
        animationSpec = tween(durationMillis = 150),
        label = "card_elevation"
    )

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.98f
            isHovered -> 1.02f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 150),
        label = "card_scale"
    )

    val iconBackgroundColor = when (topic.icon) {
        Icons.Default.Key -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        Icons.Default.AccountBalance, Icons.Default.Person -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        Icons.Default.AttachMoney, Icons.AutoMirrored.Filled.Send -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        Icons.Default.Code, Icons.Default.CloudUpload, Icons.Default.PlayArrow, Icons.Default.VerifiedUser -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }

    val iconTint = when (topic.icon) {
        Icons.Default.Key -> MaterialTheme.colorScheme.primary
        Icons.Default.AccountBalance, Icons.Default.Person -> MaterialTheme.colorScheme.secondary
        Icons.Default.AttachMoney, Icons.AutoMirrored.Filled.Send -> MaterialTheme.colorScheme.tertiary
        Icons.Default.Code, Icons.Default.CloudUpload, Icons.Default.PlayArrow, Icons.Default.VerifiedUser -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(99.dp)
            .scale(scale)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with background container
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = iconBackgroundColor,
                        shape = RoundedCornerShape(9.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = topic.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = topic.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
