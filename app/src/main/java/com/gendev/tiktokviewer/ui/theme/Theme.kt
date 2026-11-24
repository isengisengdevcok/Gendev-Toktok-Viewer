package com.gendev.tiktokviewer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class GendevThemePreset(
    val id: String,
    val displayName: String,
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val primary: Color,
    val accent: Color,
    val container: Color
) {
    AMOLED_DARK(
        id = "amoled",
        displayName = "AMOLED",
        background = AmoledBlack,
        surface = ObsidianSurface,
        elevated = ObsidianElevated,
        primary = ElectricPurple,
        accent = NeonViolet,
        container = DeepPlum
    ),
    MIDNIGHT_BLUE(
        id = "midnight",
        displayName = "Midnight",
        background = Color(0xFF040914),
        surface = Color(0xFF0A1324),
        elevated = Color(0xFF111E35),
        primary = Color(0xFF0EA5E9),
        accent = Color(0xFF7DD3FC),
        container = Color(0xFF0C2A45)
    ),
    EMERALD_DARK(
        id = "emerald",
        displayName = "Emerald",
        background = Color(0xFF030B08),
        surface = Color(0xFF081611),
        elevated = Color(0xFF0F231C),
        primary = Color(0xFF10B981),
        accent = Color(0xFF6EE7B7),
        container = Color(0xFF0B3023)
    ),
    CRIMSON_CINEMA(
        id = "crimson",
        displayName = "Sinema",
        background = Color(0xFF0A0406),
        surface = Color(0xFF14090D),
        elevated = Color(0xFF211017),
        primary = Color(0xFFF43F5E),
        accent = Color(0xFFFDA4AF),
        container = Color(0xFF3A0F1C)
    );

    companion object {
        fun fromId(id: String?): GendevThemePreset {
            return entries.firstOrNull { it.id == id } ?: AMOLED_DARK
        }
    }
}

val GendevShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun GendevTheme(
    preset: GendevThemePreset = GendevThemePreset.AMOLED_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = preset.primary,
        onPrimary = PureWhite,
        primaryContainer = preset.container,
        onPrimaryContainer = preset.accent,
        secondary = preset.accent,
        onSecondary = preset.background,
        secondaryContainer = preset.elevated,
        onSecondaryContainer = PureWhite,
        tertiary = preset.primary,
        onTertiary = PureWhite,
        background = preset.background,
        onBackground = PureWhite,
        surface = preset.surface,
        onSurface = PureWhite,
        surfaceVariant = preset.elevated,
        onSurfaceVariant = MutedLavender,
        outline = preset.primary.copy(alpha = 0.35f),
        outlineVariant = DarkGlassSubtleBorder,
        error = RoseRestricted,
        onError = preset.background
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GendevTypography,
        shapes = GendevShapes,
        content = content
    )
}
