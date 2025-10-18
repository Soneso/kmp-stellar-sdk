package com.soneso.demo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Material 3 color palette matching macOS SwiftUI app
// These colors are extracted from demo/macosApp/StellarDemo/StellarDemoApp.swift

// Primary colors (blue-based)
private val Primary = Color(red = 0.13f, green = 0.35f, blue = 0.78f)
private val OnPrimary = Color.White
private val PrimaryContainer = Color(red = 0.85f, green = 0.90f, blue = 1.0f)
private val OnPrimaryContainer = Color(red = 0.0f, green = 0.11f, blue = 0.36f)

// Secondary colors (blue-gray)
private val Secondary = Color(red = 0.38f, green = 0.35f, blue = 0.45f)
private val OnSecondary = Color.White
private val SecondaryContainer = Color(red = 0.85f, green = 0.92f, blue = 0.96f)
private val OnSecondaryContainer = Color(red = 0.05f, green = 0.20f, blue = 0.30f)

// Tertiary colors (warm beige/tan for secret seeds)
private val Tertiary = Color(red = 0.49f, green = 0.31f, blue = 0.37f)
private val OnTertiary = Color.White
private val TertiaryContainer = Color(red = 0.98f, green = 0.92f, blue = 0.85f)
private val OnTertiaryContainer = Color(red = 0.35f, green = 0.18f, blue = 0.03f)

// Error colors
private val Error = Color(red = 0.73f, green = 0.21f, blue = 0.18f)
private val OnError = Color.White
private val ErrorContainer = Color(red = 1.0f, green = 0.85f, blue = 0.85f)
private val OnErrorContainer = Color(red = 0.4f, green = 0.0f, blue = 0.0f)

// Surface colors
private val Background = Color(red = 0.98f, green = 0.98f, blue = 0.98f)
private val OnBackground = Color(red = 0.1f, green = 0.1f, blue = 0.1f)
private val Surface = Color(red = 0.98f, green = 0.98f, blue = 0.98f)
private val OnSurface = Color(red = 0.1f, green = 0.1f, blue = 0.1f)
private val SurfaceVariant = Color(red = 0.9f, green = 0.89f, blue = 0.94f)
private val OnSurfaceVariant = Color(red = 0.4f, green = 0.4f, blue = 0.4f)
private val Outline = Color(red = 0.47f, green = 0.45f, blue = 0.5f)

// Success colors (custom extension colors for Material 3)
// These are not part of the standard ColorScheme, so we define them separately
val SuccessContainer = Color(red = 0.85f, green = 0.95f, blue = 0.87f)
val OnSuccessContainer = Color(red = 0.0f, green = 0.3f, blue = 0.1f)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,

    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,

    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,

    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,

    background = Background,
    onBackground = OnBackground,

    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,

    outline = Outline,

    // Additional Material 3 colors derived from the palette
    outlineVariant = Outline.copy(alpha = 0.5f),
    scrim = Color.Black.copy(alpha = 0.32f),
    inverseSurface = OnSurface,
    inverseOnSurface = Surface,
    inversePrimary = PrimaryContainer,
    surfaceTint = Primary
)

// Dark mode colors (can be customized later if needed)
private val DarkColors = darkColorScheme(
    primary = Color(red = 0.52f, green = 0.63f, blue = 0.91f),
    onPrimary = OnPrimaryContainer,
    primaryContainer = Color(red = 0.0f, green = 0.21f, blue = 0.58f),
    onPrimaryContainer = PrimaryContainer,

    secondary = Color(red = 0.62f, green = 0.75f, blue = 0.85f),
    onSecondary = OnSecondaryContainer,
    secondaryContainer = Color(red = 0.1f, green = 0.3f, blue = 0.45f),
    onSecondaryContainer = SecondaryContainer,

    tertiary = Color(red = 0.92f, green = 0.77f, blue = 0.65f),
    onTertiary = OnTertiaryContainer,
    tertiaryContainer = Color(red = 0.45f, green = 0.25f, blue = 0.1f),
    onTertiaryContainer = TertiaryContainer,

    error = Color(red = 0.93f, green = 0.47f, blue = 0.44f),
    onError = OnErrorContainer,
    errorContainer = Color(red = 0.6f, green = 0.0f, blue = 0.0f),
    onErrorContainer = ErrorContainer,

    background = Color(red = 0.1f, green = 0.1f, blue = 0.1f),
    onBackground = Color(red = 0.95f, green = 0.95f, blue = 0.95f),

    surface = Color(red = 0.15f, green = 0.15f, blue = 0.15f),
    onSurface = Color(red = 0.95f, green = 0.95f, blue = 0.95f),
    surfaceVariant = Color(red = 0.25f, green = 0.25f, blue = 0.25f),
    onSurfaceVariant = Color(red = 0.7f, green = 0.7f, blue = 0.7f),

    outline = Color(red = 0.6f, green = 0.58f, blue = 0.65f)
)

@Composable
fun StellarTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

/**
 * Extended colors for Material 3 theme.
 * These colors are not part of the standard ColorScheme but are used in the app.
 */
@Immutable
data class ExtendedColors(
    val successContainer: Color,
    val onSuccessContainer: Color
)

/**
 * Light theme extended colors matching macOS app.
 */
val LightExtendedColors = ExtendedColors(
    successContainer = SuccessContainer,
    onSuccessContainer = OnSuccessContainer
)

/**
 * Dark theme extended colors.
 */
val DarkExtendedColors = ExtendedColors(
    successContainer = Color(red = 0.0f, green = 0.4f, blue = 0.15f),
    onSuccessContainer = Color(red = 0.7f, green = 0.95f, blue = 0.8f)
)
