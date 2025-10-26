package com.soneso.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable top app bar with Stellar branding and gradient background.
 *
 * This component provides a consistent navigation header across all demo screens with:
 * - Horizontal gradient background (StellarBlue to StellarBlueDark)
 * - Shadow effect with blue tint
 * - White text and icons
 * - Optional back navigation button
 * - Optional subtitle support
 *
 * Example usage:
 * ```
 * Scaffold(
 *     topBar = {
 *         StellarTopBar(
 *             title = "Key Generation",
 *             onNavigationClick = { navigator.pop() }
 *         )
 *     }
 * ) { padding ->
 *     // Screen content
 * }
 * ```
 *
 * Example with subtitle (for main screen):
 * ```
 * StellarTopBar(
 *     title = "KMP Stellar SDK Demo",
 *     subtitle = "Explore SDK Features on Testnet",
 *     showBackButton = false
 * )
 * ```
 *
 * @param title The screen title to display in the app bar
 * @param subtitle Optional subtitle to display below the title (for main screen)
 * @param showBackButton Whether to show the back navigation button (default true)
 * @param onNavigationClick Callback invoked when the back/navigation button is clicked (required if showBackButton is true)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StellarTopBar(
    title: String,
    subtitle: String? = null,
    showBackButton: Boolean = true,
    onNavigationClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0A4FD6), // StellarBlue
                        Color(0xFF0639A3)  // StellarBlueDark
                    )
                )
            )
            .shadow(
                elevation = 4.dp,
                spotColor = Color(0xFF0A4FD6).copy(alpha = 0.3f)
            )
    ) {
        if (subtitle != null) {
            // Use CenterAlignedTopAppBar with subtitle
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                letterSpacing = 0.3.sp
                            ),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onNavigationClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    {}
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        } else {
            // Use standard TopAppBar without subtitle
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White
                    )
                },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onNavigationClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    {}
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    }
}
