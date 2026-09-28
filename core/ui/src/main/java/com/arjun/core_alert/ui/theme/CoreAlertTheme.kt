package com.arjun.core_alert.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.models.AppPalette
import com.arjun.core_alert.coreui.R

/** Manrope (same family the XML theme declares). */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
)

/**
 * Extra palette values that Material 3's [ColorScheme] has no slot for.
 * Resolved from the active palette, so the four palettes and day/night keep working.
 */
@Immutable
data class CoreAlertColors(
    val accent: Color,
    val statusOk: Color,
    val heroBg: Color,
    val heroChipBg: Color,
    val heroFaded: Color,
    val ink: Color,
    val inkHeading: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val bg: Color,
    val surfaceAlt: Color,
    val outline: Color,
    val outlineStrong: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val statusMissing: Color,
    val trackInactive: Color,
    val heroOn: Color,
    val heroChipOn: Color,
    val heroOffBg: Color,
    val heroOffOn: Color,
    val heroOffMuted: Color,
    val heroOffBadgeBg: Color,
    val heroOffBadgeIcon: Color
)

val LocalCoreAlertColors = compositionLocalOf<CoreAlertColors> {
    error("CoreAlertTheme not provided")
}

object CoreAlertShape {
    val Small = RoundedCornerShape(14.dp)
    val Medium = RoundedCornerShape(20.dp)
    val Large = RoundedCornerShape(26.dp)
    val Pill = RoundedCornerShape(50)
}

private val CoreAlertTypography = Typography(
    // Hero state ("Active" / "Paused")
    displaySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 32.sp
    ),
    // Toolbar title
    headlineSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 28.sp
    ),
    // Card titles, dialog titles
    titleMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // Contact names, list row titles
    titleSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 15.5.sp,
        lineHeight = 20.sp
    ),
    // Body
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    ),
    // Overline section labels
    labelSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

/** Resource ids of the colours each palette contributes to the theme. */
private data class PaletteRes(
    val primary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val secondaryContainer: Int,
    val accent: Int,
    val faded: Int,
    val statusOk: Int,
    val heroBg: Int,
    val heroChipBg: Int
)

private fun paletteRes(palette: AppPalette): PaletteRes = when (palette) {
    AppPalette.INDACO -> PaletteRes(
        R.color.indaco_primary, R.color.indaco_container, R.color.indaco_on_container,
        R.color.indaco_secondary, R.color.indaco_secondary_bg, R.color.indaco_accent,
        R.color.indaco_faded, R.color.indaco_status_ok, R.color.indaco_hero_bg,
        R.color.indaco_hero_chip_bg
    )

    AppPalette.TEAL -> PaletteRes(
        R.color.teal_primary, R.color.teal_container, R.color.teal_on_container,
        R.color.teal_secondary, R.color.teal_secondary_bg, R.color.teal_accent,
        R.color.teal_faded, R.color.teal_status_ok, R.color.teal_hero_bg,
        R.color.teal_hero_chip_bg
    )

    AppPalette.ARGILLA -> PaletteRes(
        R.color.argilla_primary, R.color.argilla_container, R.color.argilla_on_container,
        R.color.argilla_secondary, R.color.argilla_secondary_bg, R.color.argilla_accent,
        R.color.argilla_faded, R.color.argilla_status_ok, R.color.argilla_hero_bg,
        R.color.argilla_hero_chip_bg
    )

    AppPalette.ARDESIA -> PaletteRes(
        R.color.ardesia_primary, R.color.ardesia_container, R.color.ardesia_on_container,
        R.color.ardesia_secondary, R.color.ardesia_secondary_bg, R.color.ardesia_accent,
        R.color.ardesia_faded, R.color.ardesia_status_ok, R.color.ardesia_hero_bg,
        R.color.ardesia_hero_chip_bg
    )
}

@Composable
fun CoreAlertTheme(palette: AppPalette, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val p = remember(palette) { paletteRes(palette) }
    val dark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    val primary = colorResource(p.primary)
    val onPrimary = colorResource(R.color.on_primary)
    val primaryContainer = colorResource(p.primaryContainer)
    val onPrimaryContainer = colorResource(p.onPrimaryContainer)
    val secondary = colorResource(p.secondary)
    val secondaryContainer = colorResource(p.secondaryContainer)
    val tertiary = colorResource(p.accent)
    val bg = colorResource(R.color.bg)
    val ink = colorResource(R.color.ink)
    val surface = colorResource(R.color.surface)
    val surfaceAlt = colorResource(R.color.surface_alt)
    val inkSecondary = colorResource(R.color.ink_secondary)
    val outline = colorResource(R.color.outline)
    val outlineStrong = colorResource(R.color.outline_strong)
    val statusMissing = colorResource(R.color.status_missing)

    val colorScheme: ColorScheme = if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = tertiary,
            background = bg,
            onBackground = ink,
            surface = surface,
            onSurface = ink,
            surfaceVariant = surfaceAlt,
            onSurfaceVariant = inkSecondary,
            outline = outline,
            outlineVariant = outlineStrong,
            error = statusMissing
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = tertiary,
            background = bg,
            onBackground = ink,
            surface = surface,
            onSurface = ink,
            surfaceVariant = surfaceAlt,
            onSurfaceVariant = inkSecondary,
            outline = outline,
            outlineVariant = outlineStrong,
            error = statusMissing
        )
    }

    val sosColors = CoreAlertColors(
        accent = colorResource(p.accent),
        statusOk = colorResource(p.statusOk),
        heroBg = colorResource(p.heroBg),
        heroChipBg = colorResource(p.heroChipBg),
        heroFaded = colorResource(p.faded),
        ink = ink,
        inkHeading = colorResource(R.color.ink_heading),
        inkSecondary = inkSecondary,
        inkMuted = colorResource(R.color.ink_muted),
        bg = bg,
        surfaceAlt = surfaceAlt,
        outline = outline,
        outlineStrong = outlineStrong,
        warning = colorResource(R.color.warning),
        warningBg = colorResource(R.color.warning_bg),
        danger = colorResource(R.color.danger),
        statusMissing = statusMissing,
        trackInactive = colorResource(R.color.track_inactive),
        heroOn = colorResource(R.color.hero_on),
        heroChipOn = colorResource(R.color.hero_chip_on),
        heroOffBg = colorResource(R.color.hero_off_bg),
        heroOffOn = colorResource(R.color.hero_off_on),
        heroOffMuted = colorResource(R.color.hero_off_muted),
        heroOffBadgeBg = colorResource(R.color.hero_off_badge_bg),
        heroOffBadgeIcon = colorResource(R.color.hero_off_badge_icon)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CoreAlertTypography,
        shapes = Shapes(
            small = CoreAlertShape.Small,
            medium = CoreAlertShape.Medium,
            large = CoreAlertShape.Large
        )
    ) {
        CompositionLocalProvider(LocalCoreAlertColors provides sosColors) {
            content()
        }
    }
}

/** Convenience: the active extra colors. */
@Composable
fun coreAlertColors(): CoreAlertColors = LocalCoreAlertColors.current
