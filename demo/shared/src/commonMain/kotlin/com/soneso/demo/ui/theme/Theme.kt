package com.soneso.demo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Celestial color system - Inspired by deep space, stellar phenomena, and cosmic beauty
// This design embodies the "Stellar" brand while maintaining developer professionalism

// === LIGHT THEME COLORS ===

// Primary Palette (Stellar Blues)
private val StellarBlue = Color(0xFF0A4FD6)
private val StellarBlueLight = Color(0xFF3D7EFF)
private val StellarBlueDark = Color(0xFF0639A3)
private val NebulaBlue = Color(0xFFE8F1FF)
private val NebulaBlueAccent = Color(0xFFD0E2FF)

// Secondary Palette (Cosmic Purples)
private val CosmicPurple = Color(0xFF5E3FBE)
private val CosmicPurpleLight = Color(0xFF8B6DD9)
private val CosmicPurpleDark = Color(0xFF3D2373)
private val StardustPurple = Color(0xFFF3EFFF)
private val StardustPurpleAccent = Color(0xFFE3D9FF)

// Accent Palette (Starlight Golds)
private val StarlightGold = Color(0xFFD97706)
private val StarlightGoldLight = Color(0xFFFFAB40)
private val StarlightGoldDark = Color(0xFFA85A00)

// Alert Palette
private val NovaRed = Color(0xFFDC2626)
private val NovaRedLight = Color(0xFFEF4444)
private val NovaRedDark = Color(0xFF991B1B)
private val NovaRedContainer = Color(0xFFFEF2F2)
private val NovaRedContainerAccent = Color(0xFFFEE2E2)

private val NebulaTeal = Color(0xFF0D9488)
private val NebulaTealLight = Color(0xFF14B8A6)
private val NebulaTealDark = Color(0xFF0F766E)
private val NebulaTealContainer = Color(0xFFF0FDFA)

// Neutrals (Deep Space Grays)
private val VoidBlack = Color(0xFF0A0E1A)
private val SpaceDark = Color(0xFF1A1F2E)
private val SpaceMedium = Color(0xFF2D3548)
private val SpaceLight = Color(0xFF4A5568)
private val StarWhite = Color(0xFFFAFAFA)
private val MoonGray = Color(0xFFE5E7EB)
private val MeteorGray = Color(0xFF9CA3AF)
private val AsteroidGray = Color(0xFF6B7280)
private val SurfaceLight = Color(0xFFFFFFFF)
private val SurfaceVariantLight = Color(0xFFF9FAFB)
private val OutlineLight = Color(0xFFE5E7EB)

// === DARK THEME COLORS ===

private val StellarBlueDarkTheme = Color(0xFF5B9FFF)
private val StellarBlueLightDarkTheme = Color(0xFF8ABAFF)
private val StellarBlueDarkDarkTheme = Color(0xFF2563EB)
private val NebulaBlueDark = Color(0xFF0F1729)
private val NebulaBlueDarkAccent = Color(0xFF1E293B)

private val CosmicPurpleDarkTheme = Color(0xFF9F7AEA)
private val CosmicPurpleLightDarkTheme = Color(0xFFB794F6)
private val CosmicPurpleDarkDarkTheme = Color(0xFF7C3AED)
private val StardustPurpleDark = Color(0xFF1A1625)
private val StardustPurpleDarkAccent = Color(0xFF271F3D)

private val StarlightGoldDarkTheme = Color(0xFFFBBF24)
private val NovaRedDarkTheme = Color(0xFFF87171)
private val NovaRedContainerDark = Color(0xFF1F1315)
private val NebulaTealDarkTheme = Color(0xFF2DD4BF)
private val NebulaTealContainerDark = Color(0xFF0F1C1A)

private val VoidBlackDark = Color(0xFF050711)
private val SurfaceDark = Color(0xFF0F1419)
private val SurfaceVariantDark = Color(0xFF1A1F2E)
private val OutlineDark = Color(0xFF2D3548)
private val StarWhiteDark = Color(0xFFFAFAFA)
private val MoonGrayDark = Color(0xFFD1D5DB)

// Success colors (custom extension colors for Material 3)
val SuccessContainer = NebulaTealContainer
val OnSuccessContainer = NebulaTealDark

private val LightColors = lightColorScheme(
    primary = StellarBlue,
    onPrimary = Color.White,
    primaryContainer = NebulaBlue,
    onPrimaryContainer = StellarBlueDark,

    secondary = CosmicPurple,
    onSecondary = Color.White,
    secondaryContainer = StardustPurple,
    onSecondaryContainer = CosmicPurpleDark,

    tertiary = StarlightGold,
    onTertiary = Color.White,
    tertiaryContainer = StardustPurpleAccent,
    onTertiaryContainer = StarlightGoldDark,

    error = NovaRed,
    onError = Color.White,
    errorContainer = NovaRedContainer,
    onErrorContainer = NovaRedDark,

    background = SurfaceVariantLight,
    onBackground = VoidBlack,

    surface = SurfaceLight,
    onSurface = VoidBlack,
    surfaceVariant = NebulaBlue,
    onSurfaceVariant = SpaceMedium,

    outline = OutlineLight,
    outlineVariant = OutlineLight.copy(alpha = 0.5f),
    scrim = Color.Black.copy(alpha = 0.32f),
    inverseSurface = VoidBlack,
    inverseOnSurface = StarWhite,
    inversePrimary = StellarBlueLight,
    surfaceTint = StellarBlue
)

// Dark mode colors
private val DarkColors = darkColorScheme(
    primary = StellarBlueDarkTheme,
    onPrimary = VoidBlackDark,
    primaryContainer = StellarBlueDarkDarkTheme,
    onPrimaryContainer = StellarBlueLightDarkTheme,

    secondary = CosmicPurpleDarkTheme,
    onSecondary = VoidBlackDark,
    secondaryContainer = CosmicPurpleDarkDarkTheme,
    onSecondaryContainer = CosmicPurpleLightDarkTheme,

    tertiary = StarlightGoldDarkTheme,
    onTertiary = VoidBlackDark,
    tertiaryContainer = StarlightGoldDark,
    onTertiaryContainer = StarlightGoldLight,

    error = NovaRedDarkTheme,
    onError = VoidBlackDark,
    errorContainer = NovaRedContainerDark,
    onErrorContainer = NovaRedLight,

    background = VoidBlackDark,
    onBackground = StarWhiteDark,

    surface = SurfaceDark,
    onSurface = StarWhiteDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = MoonGrayDark,

    outline = OutlineDark,
    outlineVariant = OutlineDark.copy(alpha = 0.5f),
    scrim = Color.Black.copy(alpha = 0.5f),
    inverseSurface = StarWhiteDark,
    inverseOnSurface = VoidBlackDark,
    inversePrimary = StellarBlueDark,
    surfaceTint = StellarBlueDarkTheme
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
