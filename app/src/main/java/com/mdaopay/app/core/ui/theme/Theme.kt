package com.mdaopay.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppTheme { DARK, LIGHT, AMOLED, SYSTEM }

data class Shadow(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val blurRadius: Float = 0f,
    val spreadRadius: Float = 0f,
    val color: Color = Color.Black
)

data class MDAOElevation(
    val raise: List<Shadow>,
    val soft: List<Shadow>,
    val inset: List<Shadow>,
    val press: List<Shadow>,
    val glow: List<Shadow>
)

data class MDAOThemeColors(
    val bg: Color,
    val card: Color,
    val surface: Color,
    val tile: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val border: Color,
    val softBorder: Color,
    val overlayDim: Color,
    val overlayStrong: Color
)

data class MDAOExtendedColors(
    val accent: Color = Accent,
    val accentPress: Color = AccentPress,
    val accentSoft: Color = AccentSoft,
    val success: Color = Success,
    val successSoft: Color = SuccessSoft,
    val danger: Color = Danger,
    val dangerSoft: Color = DangerSoft,
    val warning: Color = Warning,
    val warningSoft: Color = WarningSoft,
    val pending: Color = PendingBlue,
    val elevation: MDAOElevation = darkElevation,
    val themeColors: MDAOThemeColors = darkThemeColors,
    val isAmoled: Boolean = false
) {
    val borderBright: Color get() = themeColors.border
    val surfaceVariant: Color get() = themeColors.card
}

val darkThemeColors = MDAOThemeColors(
    bg = DarkBg,
    card = DarkCard,
    surface = DarkSurface,
    tile = DarkTile,
    text = DarkText,
    text2 = DarkText2,
    text3 = DarkText3,
    border = DarkBorder,
    softBorder = DarkSoftBorder,
    overlayDim = DarkOverlayDim,
    overlayStrong = DarkOverlayStrong
)

val lightThemeColors = MDAOThemeColors(
    bg = LightBg,
    card = LightCard,
    surface = LightSurface,
    tile = LightTile,
    text = LightText,
    text2 = LightText2,
    text3 = LightText3,
    border = LightBorder,
    softBorder = LightSoftBorder,
    overlayDim = LightOverlayDim,
    overlayStrong = LightOverlayStrong
)

val amoledThemeColors = MDAOThemeColors(
    bg = AmoledBg,
    card = AmoledCard,
    surface = AmoledSurface,
    tile = AmoledTile,
    text = AmoledText,
    text2 = AmoledText2,
    text3 = AmoledText3,
    border = AmoledBorder,
    softBorder = AmoledSoftBorder,
    overlayDim = AmoledOverlayDim,
    overlayStrong = AmoledOverlayStrong
)

val darkElevation = MDAOElevation(
    raise = listOf(
        Shadow(offsetY = 18f, blurRadius = 40f, spreadRadius = -10f, color = Color.Black.copy(alpha = 0.7f)),
        Shadow(offsetY = 6f, blurRadius = 14f, spreadRadius = -4f, color = Color.Black.copy(alpha = 0.5f)),
        Shadow(offsetY = 1f, blurRadius = 0f, color = Color.White.copy(alpha = 0.06f))
    ),
    soft = listOf(
        Shadow(offsetY = 10f, blurRadius = 22f, spreadRadius = -6f, color = Color.Black.copy(alpha = 0.6f)),
        Shadow(offsetY = 3f, blurRadius = 8f, spreadRadius = -2f, color = Color.Black.copy(alpha = 0.4f)),
        Shadow(offsetY = 1f, blurRadius = 0f, color = Color.White.copy(alpha = 0.05f))
    ),
    inset = listOf(
        Shadow(offsetY = -4f, blurRadius = 10f, color = Color.Black.copy(alpha = 0.4f)),
        Shadow(offsetY = 1f, blurRadius = 1f, color = Color.White.copy(alpha = 0.06f))
    ),
    press = listOf(
        Shadow(offsetY = 5f, blurRadius = 12f, color = Color.Black.copy(alpha = 0.7f)),
        Shadow(offsetY = -1f, blurRadius = 1f, color = Color.White.copy(alpha = 0.04f))
    ),
    glow = listOf(
        Shadow(offsetY = 1f, blurRadius = 0f, color = Color.White.copy(alpha = 0.08f))
    )
)

val lightElevation = MDAOElevation(
    raise = listOf(
        Shadow(offsetY = 18f, blurRadius = 40f, spreadRadius = -10f, color = Color.Black.copy(alpha = 0.15f)),
        Shadow(offsetY = 6f, blurRadius = 14f, spreadRadius = -4f, color = Color.Black.copy(alpha = 0.08f)),
        Shadow(offsetY = 1f, blurRadius = 0f, color = Color.White.copy(alpha = 0.5f))
    ),
    soft = listOf(
        Shadow(offsetY = 10f, blurRadius = 22f, spreadRadius = -6f, color = Color.Black.copy(alpha = 0.10f)),
        Shadow(offsetY = 3f, blurRadius = 8f, spreadRadius = -2f, color = Color.Black.copy(alpha = 0.06f)),
        Shadow(offsetY = 1f, blurRadius = 0f, color = Color.White.copy(alpha = 0.4f))
    ),
    inset = listOf(
        Shadow(offsetY = -4f, blurRadius = 10f, color = Color.Black.copy(alpha = 0.08f)),
        Shadow(offsetY = 1f, blurRadius = 1f, color = Color.White.copy(alpha = 0.5f))
    ),
    press = listOf(
        Shadow(offsetY = 5f, blurRadius = 12f, color = Color.Black.copy(alpha = 0.15f)),
        Shadow(offsetY = -1f, blurRadius = 1f, color = Color.White.copy(alpha = 0.3f))
    ),
    glow = listOf(
        Shadow(offsetY = 1f, blurRadius = 0f, color = Color.White.copy(alpha = 0.6f))
    )
)

val amoledElevation = MDAOElevation(
    raise = listOf(
        Shadow(offsetY = 18f, blurRadius = 40f, spreadRadius = -10f, color = Color.Black.copy(alpha = 0.9f)),
        Shadow(offsetY = 6f, blurRadius = 14f, spreadRadius = -4f, color = Color.Black.copy(alpha = 0.7f)),
        Shadow(offsetY = 1f, blurRadius = 0f, color = Color.White.copy(alpha = 0.04f))
    ),
    soft = listOf(
        Shadow(offsetY = 10f, blurRadius = 22f, spreadRadius = -6f, color = Color.Black.copy(alpha = 0.8f)),
        Shadow(offsetY = 3f, blurRadius = 8f, spreadRadius = -2f, color = Color.Black.copy(alpha = 0.6f)),
        Shadow(offsetY = 1f, blurRadius = 0f, color = Color.White.copy(alpha = 0.03f))
    ),
    inset = listOf(
        Shadow(offsetY = -4f, blurRadius = 10f, color = Color.Black.copy(alpha = 0.6f)),
        Shadow(offsetY = 1f, blurRadius = 1f, color = Color.White.copy(alpha = 0.04f))
    ),
    press = listOf(
        Shadow(offsetY = 5f, blurRadius = 12f, color = Color.Black.copy(alpha = 0.9f)),
        Shadow(offsetY = -1f, blurRadius = 1f, color = Color.White.copy(alpha = 0.03f))
    ),
    glow = listOf(
        Shadow(offsetY = 1f, blurRadius = 0f, color = Color.White.copy(alpha = 0.05f))
    )
)

val LocalExtendedColors = staticCompositionLocalOf { MDAOExtendedColors() }

val MaterialTheme.extended: MDAOExtendedColors
    @Composable get() = LocalExtendedColors.current

@Composable
fun MDAOPayTheme(
    appTheme: AppTheme = AppTheme.DARK,
    accentColor: Color = Accent,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    val materialColorScheme = when (appTheme) {
        AppTheme.DARK -> darkColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = AccentSoft,
            onPrimaryContainer = accentColor,
            secondary = AccentBlue,
            tertiary = PendingBlue,
            background = DarkBg,
            surface = DarkSurface,
            surfaceVariant = DarkCard,
            onBackground = DarkText,
            onSurface = DarkText,
            onSurfaceVariant = DarkText2,
            error = Danger,
            outline = DarkBorder,
            outlineVariant = DarkSoftBorder
        )
        AppTheme.LIGHT -> lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = AccentSoft,
            onPrimaryContainer = accentColor,
            secondary = AccentBlue,
            tertiary = PendingBlue,
            background = LightBg,
            surface = LightSurface,
            surfaceVariant = LightCard,
            onBackground = LightText,
            onSurface = LightText,
            onSurfaceVariant = LightText2,
            error = Danger,
            outline = LightBorder,
            outlineVariant = LightSoftBorder
        )
        AppTheme.AMOLED -> darkColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = AccentSoft,
            onPrimaryContainer = accentColor,
            secondary = AccentBlue,
            tertiary = PendingBlue,
            background = AmoledBg,
            surface = AmoledSurface,
            surfaceVariant = AmoledCard,
            onBackground = AmoledText,
            onSurface = AmoledText,
            onSurfaceVariant = AmoledText2,
            error = Danger,
            outline = AmoledBorder,
            outlineVariant = AmoledSoftBorder
        )
        AppTheme.SYSTEM -> if (isDark) darkColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = AccentSoft,
            onPrimaryContainer = accentColor,
            secondary = AccentBlue,
            tertiary = PendingBlue,
            background = DarkBg,
            surface = DarkSurface,
            surfaceVariant = DarkCard,
            onBackground = DarkText,
            onSurface = DarkText,
            onSurfaceVariant = DarkText2,
            error = Danger,
            outline = DarkBorder,
            outlineVariant = DarkSoftBorder
        ) else lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = AccentSoft,
            onPrimaryContainer = accentColor,
            secondary = AccentBlue,
            tertiary = PendingBlue,
            background = LightBg,
            surface = LightSurface,
            surfaceVariant = LightCard,
            onBackground = LightText,
            onSurface = LightText,
            onSurfaceVariant = LightText2,
            error = Danger,
            outline = LightBorder,
            outlineVariant = LightSoftBorder
        )
    }

    val (themeColors, elevation, isAmoled) = when (appTheme) {
        AppTheme.DARK -> Triple(darkThemeColors, darkElevation, false)
        AppTheme.LIGHT -> Triple(lightThemeColors, lightElevation, false)
        AppTheme.AMOLED -> Triple(amoledThemeColors, amoledElevation, true)
        AppTheme.SYSTEM -> if (isDark) Triple(darkThemeColors, darkElevation, false)
        else Triple(lightThemeColors, lightElevation, false)
    }

    val extended = MDAOExtendedColors(
        accent = accentColor,
        elevation = elevation,
        themeColors = themeColors,
        isAmoled = isAmoled
    )

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = MDAOTypography,
            content = content
        )
    }
}
