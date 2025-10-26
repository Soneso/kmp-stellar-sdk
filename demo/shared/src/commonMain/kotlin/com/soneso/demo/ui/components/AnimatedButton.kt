package com.soneso.demo.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A button with built-in hover and press animations for enhanced interactivity.
 *
 * This component provides consistent button animations across the demo app:
 * - Scale animation: Slightly grows on hover (1.02x), slightly shrinks on press (0.98x)
 * - Elevation animation: Increases shadow on hover (6dp), decreases on press (2dp)
 * - Loading state: Disables animations and shows progress indicator
 *
 * The animations use a fast 150ms tween for responsive feedback and align with
 * Material Design motion principles.
 *
 * Example usage:
 * ```
 * AnimatedButton(
 *     onClick = { submitForm() },
 *     enabled = !isLoading && inputIsValid,
 *     isLoading = isLoading,
 *     modifier = Modifier.fillMaxWidth().height(56.dp),
 *     colors = ButtonDefaults.buttonColors(
 *         containerColor = Color(0xFF0A4FD6)
 *     )
 * ) {
 *     Icon(imageVector = Icons.Default.Send, contentDescription = null)
 *     Spacer(modifier = Modifier.width(12.dp))
 *     Text("Submit")
 * }
 * ```
 *
 * @param onClick Callback invoked when the button is clicked
 * @param modifier Modifier to apply to the button
 * @param enabled Whether the button is enabled and can be clicked
 * @param isLoading Whether the button is in a loading state (disables animations, shows progress)
 * @param shape The shape of the button. Defaults to rounded corners (12.dp)
 * @param colors The colors for the button in different states
 * @param contentPadding The padding around the button content
 * @param shadowColor The color of the shadow when elevated. Defaults to StellarBlue with transparency
 * @param hoverElevation The elevation (shadow) when the button is hovered. Defaults to 6.dp
 * @param defaultElevation The elevation (shadow) in the default state. Defaults to 4.dp
 * @param pressElevation The elevation (shadow) when the button is pressed. Defaults to 2.dp
 * @param hoverScale The scale multiplier when the button is hovered. Defaults to 1.02 (2% larger)
 * @param pressScale The scale multiplier when the button is pressed. Defaults to 0.98 (2% smaller)
 * @param animationDuration The duration of animations in milliseconds. Defaults to 150ms
 * @param loadingIndicatorSize The size of the loading progress indicator. Defaults to 24.dp
 * @param content The button content (typically Icon + Text combination)
 */
@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF0A4FD6), // StellarBlue
        contentColor = Color.White
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    shadowColor: Color = Color(0xFF0A4FD6).copy(alpha = 0.3f),
    hoverElevation: Dp = 6.dp,
    defaultElevation: Dp = 4.dp,
    pressElevation: Dp = 2.dp,
    hoverScale: Float = 1.02f,
    pressScale: Float = 0.98f,
    animationDuration: Int = 150,
    loadingIndicatorSize: Dp = 24.dp,
    content: @Composable RowScope.() -> Unit
) {
    // Interaction source for tracking hover/press states
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    // Elevation animation: higher on hover, lower on press, none when loading
    val elevation by animateDpAsState(
        targetValue = when {
            isLoading -> 0.dp
            isPressed -> pressElevation
            isHovered -> hoverElevation
            else -> defaultElevation
        },
        animationSpec = tween(durationMillis = animationDuration),
        label = "button_elevation"
    )

    // Scale animation: larger on hover, smaller on press, normal when loading
    val scale by animateFloatAsState(
        targetValue = when {
            isLoading -> 1f
            isPressed -> pressScale
            isHovered -> hoverScale
            else -> 1f
        },
        animationSpec = tween(durationMillis = animationDuration),
        label = "button_scale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = shape,
                spotColor = shadowColor
            )
            .hoverable(interactionSource = interactionSource),
        enabled = enabled && !isLoading,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(loadingIndicatorSize),
                color = colors.contentColor,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        content()
    }
}

/**
 * Configuration object for customizing AnimatedButton animations.
 *
 * Use this to create consistent button animation profiles across your app:
 *
 * ```
 * val subtleAnimation = AnimatedButtonConfig(
 *     hoverScale = 1.01f,
 *     hoverElevation = 4.dp
 * )
 *
 * AnimatedButton(
 *     onClick = { },
 *     config = subtleAnimation
 * ) { Text("Subtle Button") }
 * ```
 *
 * @param hoverElevation The elevation when hovering
 * @param defaultElevation The elevation in default state
 * @param pressElevation The elevation when pressed
 * @param hoverScale The scale multiplier when hovering
 * @param pressScale The scale multiplier when pressed
 * @param animationDuration The animation duration in milliseconds
 */
data class AnimatedButtonConfig(
    val hoverElevation: Dp = 6.dp,
    val defaultElevation: Dp = 4.dp,
    val pressElevation: Dp = 2.dp,
    val hoverScale: Float = 1.02f,
    val pressScale: Float = 0.98f,
    val animationDuration: Int = 150
)

/**
 * AnimatedButton variant that accepts an [AnimatedButtonConfig] for easier reuse
 * of animation profiles.
 *
 * @param onClick Callback invoked when the button is clicked
 * @param modifier Modifier to apply to the button
 * @param enabled Whether the button is enabled
 * @param isLoading Whether the button is in loading state
 * @param shape The button shape
 * @param colors The button colors
 * @param contentPadding The content padding
 * @param shadowColor The shadow color
 * @param config Animation configuration profile
 * @param loadingIndicatorSize Size of loading indicator
 * @param content The button content
 */
@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF0A4FD6),
        contentColor = Color.White
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    shadowColor: Color = Color(0xFF0A4FD6).copy(alpha = 0.3f),
    config: AnimatedButtonConfig = AnimatedButtonConfig(),
    loadingIndicatorSize: Dp = 24.dp,
    content: @Composable RowScope.() -> Unit
) {
    AnimatedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isLoading = isLoading,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        shadowColor = shadowColor,
        hoverElevation = config.hoverElevation,
        defaultElevation = config.defaultElevation,
        pressElevation = config.pressElevation,
        hoverScale = config.hoverScale,
        pressScale = config.pressScale,
        animationDuration = config.animationDuration,
        loadingIndicatorSize = loadingIndicatorSize,
        content = content
    )
}
