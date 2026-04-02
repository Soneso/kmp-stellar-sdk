package com.soneso.smartdemo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Smart Account theme colors - professional palette for account management
private val SmartBlue = Color(0xFF0A4FD6)
private val SmartBlueDark = Color(0xFF0639A3)
private val SmartBlueContainer = Color(0xFFE8F1FF)
private val SmartPurple = Color(0xFF5E3FBE)
private val SmartPurpleDark = Color(0xFF3D2373)
private val SmartPurpleContainer = Color(0xFFF3EFFF)
private val SmartRed = Color(0xFFDC2626)
private val SmartRedDark = Color(0xFF991B1B)
private val SmartRedContainer = Color(0xFFFEF2F2)
private val VoidBlack = Color(0xFF0A0E1A)
private val StarWhite = Color(0xFFFAFAFA)
private val SurfaceLight = Color(0xFFFFFFFF)
private val SurfaceVariantLight = Color(0xFFF9FAFB)
private val OutlineLight = Color(0xFFE5E7EB)
private val SpaceMedium = Color(0xFF2D3548)

private val LightColors = lightColorScheme(
    primary = SmartBlue,
    onPrimary = Color.White,
    primaryContainer = SmartBlueContainer,
    onPrimaryContainer = SmartBlueDark,
    secondary = SmartPurple,
    onSecondary = Color.White,
    secondaryContainer = SmartPurpleContainer,
    onSecondaryContainer = SmartPurpleDark,
    error = SmartRed,
    onError = Color.White,
    errorContainer = SmartRedContainer,
    onErrorContainer = SmartRedDark,
    background = SurfaceVariantLight,
    onBackground = VoidBlack,
    surface = SurfaceLight,
    onSurface = VoidBlack,
    surfaceVariant = SmartBlueContainer,
    onSurfaceVariant = SpaceMedium,
    outline = OutlineLight,
    outlineVariant = OutlineLight.copy(alpha = 0.5f),
    inverseSurface = VoidBlack,
    inverseOnSurface = StarWhite,
    surfaceTint = SmartBlue
)

@Composable
fun SmartAccountTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
