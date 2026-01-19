package id.xms.xcai.ui.theme

import androidx.compose.ui.graphics.Color

// Web3 Clean Theme Colors
val Web3Black = Color(0xFF050505) // Matte Black
val Web3MidnightBlue = Color(0xFF0A0E17) // Deep Midnight Blue
val Web3Cyan = Color(0xFF80DEEA) // Soft Cyan (was Neon #00F0FF)
val Web3CyanDark = Color(0xFF4DD0E1) // Muted Cyan (was #008F99)
val Web3Purple = Color(0xFFB39DDB) // Soft Lavender (was #6200EA)
val Web3Slate = Color(0xFF1E2230) // Slate Blue/Grey for surfaces
val Web3TextPrimary = Color(0xFFFFFFFF)
val Web3TextSecondary = Color(0xFFB0B3C6)

// Light Theme Colors (Kept for compatibility, but intended to be unused or re-mapped)
val md_theme_light_primary = Web3Cyan
val md_theme_light_onPrimary = Color.Black
val md_theme_light_primaryContainer = Web3CyanDark
val md_theme_light_onPrimaryContainer = Color.White
val md_theme_light_secondary = Web3Slate
val md_theme_light_onSecondary = Color.White
val md_theme_light_secondaryContainer = Web3Slate
val md_theme_light_onSecondaryContainer = Color.White
val md_theme_light_tertiary = Web3Purple
val md_theme_light_onTertiary = Color.White
val md_theme_light_tertiaryContainer = Web3Purple
val md_theme_light_onTertiaryContainer = Color.White
val md_theme_light_error = Color(0xFFEA4335)
val md_theme_light_onError = Color.White
val md_theme_light_errorContainer = Color(0xFFFCD8D6)
val md_theme_light_onErrorContainer = Color(0xFF3A0A08)
val md_theme_light_background = Web3MidnightBlue
val md_theme_light_onBackground = Web3TextPrimary
val md_theme_light_surface = Web3MidnightBlue
val md_theme_light_onSurface = Web3TextPrimary
val md_theme_light_surfaceVariant = Web3Slate
val md_theme_light_onSurfaceVariant = Web3TextPrimary
val md_theme_light_outline = Web3CyanDark

// Dark Theme Colors (Primary Web3 Theme)
val md_theme_dark_primary = Web3Cyan
val md_theme_dark_onPrimary = Color(0xFF000000)
val md_theme_dark_primaryContainer = Color(0xFF004A50)
val md_theme_dark_onPrimaryContainer = Color(0xFF9CF1F9)
val md_theme_dark_secondary = Web3Slate
val md_theme_dark_onSecondary = Web3TextPrimary
val md_theme_dark_secondaryContainer = Color(0xFF2A2E40)
val md_theme_dark_onSecondaryContainer = Web3TextSecondary
val md_theme_dark_tertiary = Web3Purple
val md_theme_dark_onTertiary = Color.White
val md_theme_dark_tertiaryContainer = Color(0xFF3A008F)
val md_theme_dark_onTertiaryContainer = Color(0xFFEADDFF)
val md_theme_dark_error = Color(0xFFF2B8B5)
val md_theme_dark_onError = Color(0xFF601410)
val md_theme_dark_errorContainer = Color(0xFF8C1D18)
val md_theme_dark_onErrorContainer = Color(0xFFFCD8D6)
val md_theme_dark_background = Web3Black
val md_theme_dark_onBackground = Web3TextPrimary
val md_theme_dark_surface = Web3MidnightBlue
val md_theme_dark_onSurface = Web3TextPrimary
val md_theme_dark_surfaceVariant = Web3Slate
val md_theme_dark_onSurfaceVariant = Web3TextSecondary
val md_theme_dark_outline = Web3CyanDark

// Glassmorphism & Web3 Accents
val GlassSurface = Web3Slate.copy(alpha = 0.7f)
val GlassSurfaceDark = Web3Black.copy(alpha = 0.8f)

val NeonCyan = Web3Cyan
val NeonPurple = Web3Purple

val UserBubbleColor = Web3Cyan.copy(alpha = 0.15f)
val UserBubbleBorder = Web3Cyan.copy(alpha = 0.5f)

val AIBubbleColor = Web3Slate
val AIBubbleBorder = Web3Slate.copy(alpha = 0.5f)

val GlassOverlay = Color.Black.copy(alpha = 0.4f)

