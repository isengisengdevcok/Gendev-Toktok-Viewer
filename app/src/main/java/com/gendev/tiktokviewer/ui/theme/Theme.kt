package com.gendev.tiktokviewer.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val GendevAmoledDarkScheme = darkColorScheme(
    primary = ElectricPurple,
    onPrimary = PureWhite,
    primaryContainer = DeepPlum,
    onPrimaryContainer = NeonViolet,
    secondary = NeonViolet,
    onSecondary = AmoledBlack,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = PureWhite,
    tertiary = UltravioletGlow,
    onTertiary = PureWhite,
    background = AmoledBlack,
    onBackground = PureWhite,
    surface = ObsidianSurface,
    onSurface = PureWhite,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = MutedLavender,
    outline = DarkGlassBorder,
    outlineVariant = DarkGlassSubtleBorder,
    error = RoseRestricted,
    onError = AmoledBlack
)

val GendevShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun GendevTheme(
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(
            background = AmoledBlack,
            surface = ObsidianSurface,
            primary = ElectricPurple,
            secondary = NeonViolet
        )
    } else {
        GendevAmoledDarkScheme
    }

    MaterialTheme(
        colorScheme = baseScheme,
        typography = GendevTypography,
        shapes = GendevShapes,
        content = content
    )
}
