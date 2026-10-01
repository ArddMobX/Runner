package com.runner.app.ui.theme

import androidx.compose.ui.graphics.Color

// Material 3 Dark Neutral Surface Palette
val SurfaceDark = Color(0xFF121214)
val SurfaceContainerLowest = Color(0xFF0C0C0E)
val SurfaceContainerLow = Color(0xFF18181B)
val SurfaceContainer = Color(0xFF1E1E22)
val SurfaceContainerHigh = Color(0xFF26262B)
val SurfaceContainerHighest = Color(0xFF303036)

// Subtle separator borders (8% and 14% white opacity)
val OutlineSubtle = Color(0xFFFFFFFF).copy(alpha = 0.08f)
val OutlineHover = Color(0xFFFFFFFF).copy(alpha = 0.14f)

// Accents - refined, muted, native feel
val AccentPrimary = Color(0xFF90CAF9)
val AccentPrimarySubtle = Color(0xFF90CAF9).copy(alpha = 0.14f)
val AccentSecondary = Color(0xFF80CBC4)

// Status colors - muted, non-screaming
val StatusSuccess = Color(0xFF81C784)
val StatusWarning = Color(0xFFFFB74D)
val StatusError = Color(0xFFE57373)

// Text hierarchy
val TextPrimary = Color(0xFFEDEDEF)
val TextSecondary = Color(0xFFA0A0A6)
val TextTertiary = Color(0xFF666670)

// Backward compatibility aliases
val DarkBackground = SurfaceDark
val DarkSurface = SurfaceContainerLow
val DarkSurfaceVariant = SurfaceContainer
val DarkBorder = OutlineSubtle
val PrimaryBlue = AccentPrimary
val PrimaryBlueVariant = Color(0xFF2A384A)
val SecondaryMint = AccentSecondary
val ToolAmber = StatusWarning
val ErrorRed = StatusError
