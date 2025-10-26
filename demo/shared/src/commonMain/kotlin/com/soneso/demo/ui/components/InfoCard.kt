package com.soneso.demo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * InfoCard - Reusable purple information card component
 *
 * A consistent card component used across all demo screens to display informational content
 * about SDK features, API usage, and functionality explanations.
 *
 * Design specifications:
 * - Background: Color(0xFFF3EFFF) - Stardust Purple
 * - Text color: Color(0xFF3D2373) - Cosmic Purple Dark
 * - Border: Optional, 1.dp with 0.2f alpha (some screens use it, some don't)
 * - Shape: RoundedCornerShape(16.dp) or MaterialTheme.shapes.medium
 * - Elevation: 2.dp
 * - Padding: 20.dp
 * - Content spacing: 12.dp vertical
 *
 * Usage patterns extracted from screens:
 * 1. Title + Description (most common)
 * 2. Custom content via lambda (flexible)
 *
 * @param modifier Modifier for the card (typically Modifier.fillMaxWidth())
 * @param useBorder Whether to show a subtle border (default: true for consistency with most screens)
 * @param useRoundedShape Whether to use RoundedCornerShape(16.dp) vs MaterialTheme.shapes.medium (default: true)
 * @param content Composable lambda for card content
 *
 * Example usage:
 * ```kotlin
 * InfoCard(modifier = Modifier.fillMaxWidth()) {
 *     Text(
 *         text = "Feature Title",
 *         style = MaterialTheme.typography.titleLarge.copy(
 *             fontWeight = FontWeight.SemiBold
 *         ),
 *         color = Color(0xFF3D2373)
 *     )
 *     Text(
 *         text = "Feature description...",
 *         style = MaterialTheme.typography.bodyMedium,
 *         color = Color(0xFF3D2373).copy(alpha = 0.85f),
 *         lineHeight = 22.sp
 *     )
 * }
 * ```
 *
 * Convenience overload with title and description:
 * ```kotlin
 * InfoCard(
 *     modifier = Modifier.fillMaxWidth(),
 *     title = "Feature Title",
 *     description = "Feature description..."
 * )
 * ```
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    useBorder: Boolean = true,
    useRoundedShape: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = if (useRoundedShape) RoundedCornerShape(16.dp) else MaterialTheme.shapes.medium,
        border = if (useBorder) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EFFF) // Stardust Purple
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/**
 * InfoCard convenience overload with title and description
 *
 * Provides a standardized layout for title + description information cards.
 * This is the most common pattern used across screens.
 *
 * @param modifier Modifier for the card (typically Modifier.fillMaxWidth())
 * @param title The main heading text (rendered with titleLarge and SemiBold)
 * @param description The descriptive body text (rendered with bodyMedium)
 * @param useBorder Whether to show a subtle border (default: true)
 * @param useRoundedShape Whether to use RoundedCornerShape(16.dp) vs MaterialTheme.shapes.medium (default: true)
 * @param titleStyle Typography style override for title (defaults to titleLarge + variations found in screens)
 * @param descriptionAlpha Alpha value for description text (default: 0.85f for consistency)
 *
 * Example:
 * ```kotlin
 * InfoCard(
 *     modifier = Modifier.fillMaxWidth(),
 *     title = "Stellar Keypair Generation",
 *     description = "Generate a cryptographically secure Ed25519 keypair for Stellar network operations..."
 * )
 * ```
 *
 * Note: Screens show slight variations:
 * - Some use titleLarge with FontWeight.SemiBold
 * - Some use titleMedium with FontWeight.Bold + lineHeight
 * This component defaults to titleLarge (most common pattern)
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    useBorder: Boolean = true,
    useRoundedShape: Boolean = true,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold
    ),
    descriptionAlpha: Float = 0.85f
) {
    InfoCard(
        modifier = modifier,
        useBorder = useBorder,
        useRoundedShape = useRoundedShape
    ) {
        Text(
            text = title,
            style = titleStyle,
            color = Color(0xFF3D2373) // Cosmic Purple Dark
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF3D2373).copy(alpha = descriptionAlpha),
            lineHeight = 22.sp
        )
    }
}

/**
 * InfoCard alternative for screens using titleMedium with Bold weight
 *
 * Some screens (like ContractDetailsScreen) use titleMedium + Bold + explicit lineHeight.
 * This convenience function provides that exact styling.
 *
 * Example:
 * ```kotlin
 * InfoCardMediumTitle(
 *     modifier = Modifier.fillMaxWidth(),
 *     title = "Soroban RPC: Fetch and Parse Smart Contract Details",
 *     description = "Enter a contract ID to fetch its WASM bytecode...",
 *     useBorder = false // ContractDetailsScreen doesn't use border
 * )
 * ```
 */
@Composable
fun InfoCardMediumTitle(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    useBorder: Boolean = false, // ContractDetailsScreen pattern
    useRoundedShape: Boolean = false, // Uses MaterialTheme.shapes.medium
    descriptionFontWeight: FontWeight = FontWeight.SemiBold
) {
    InfoCard(
        modifier = modifier,
        useBorder = useBorder,
        useRoundedShape = useRoundedShape
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            color = Color(0xFF3D2373) // Cosmic Purple Dark
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = descriptionFontWeight,
                lineHeight = 22.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            color = Color(0xFF3D2373).copy(alpha = 0.8f)
        )
    }
}
