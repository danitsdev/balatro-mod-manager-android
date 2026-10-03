package com.balatromodmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import com.balatromodmanager.R

internal val PixelFont = FontFamily(Font(R.font.m6x11plus))
private val BalatroShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
)

internal object BmmColor {
    var darkMode: Boolean = false
    val FeltRed get() = if (darkMode) Color(0xFF0B1220) else Color(0xFFA53535)
    val FeltRedDark get() = if (darkMode) Color(0xFF111B2E) else Color(0xFFD66060)
    val Ink get() = if (darkMode) Color(0xFF0B1220) else Color(0xFF393646)
    val Panel get() = if (darkMode) Color(0xE60F2138) else Color(0xCC85231B)
    val PanelOpaque get() = if (darkMode) Color(0xFF0F2138) else Color(0xFF85231B)
    val PanelRaised get() = if (darkMode) Color(0xDB0C1420) else Color(0xCC282828)
    val Cream get() = if (darkMode) Color(0xFFE6EEFC) else Color(0xFFF4EEE0)
    val MutedCream get() = if (darkMode) Color(0xFFB3C1D9) else Color(0xFFC4C2C2)
    val Gold get() = if (darkMode) Color(0xFFF6C65F) else Color(0xFFFDCF51)
    val Accent get() = if (darkMode) Color(0xFF7FB5FF) else Color(0xFFFDCF51)
    val Amber get() = if (darkMode) Color(0xFFF0C45C) else Color(0xFFEA9600)
    val FilterControl get() = if (darkMode) Color(0xFF1B3B6B) else Color(0xFFEA9600)
    val Green get() = if (darkMode) Color(0xFF2F8F73) else Color(0xFF56A786)
    val GreenStrong get() = if (darkMode) Color(0xFF1F8A4E) else Color(0xFF27AE60)
    val Blue get() = if (darkMode) Color(0xFF2B6BB8) else Color(0xFF2878C8)
    val Danger get() = if (darkMode) Color(0xFF9A3831) else Color(0xFFC14139)
    val Neutral get() = if (darkMode) Color(0xFF5D6A7A) else Color(0xFF7F8C8D)
    val Input get() = if (darkMode) Color(0xFF101A2A) else Color(0xFF1F1F1F)
    val Glass get() = if (darkMode) Color(0x1AE6EEFC) else Color(0x1AF4EEE0)
    val GlassStrong get() = if (darkMode) Color(0x33E6EEFC) else Color(0x33F4EEE0)
    val GlassBorder get() = if (darkMode) Color(0x4DE6EEFC) else Color(0x4DF4EEE0)
    val Repository get() = if (darkMode) Color(0xFF101A2A) else Color(0xFF2B3137)
    val Purple get() = if (darkMode) Color(0xFF2F4A9E) else Color(0xFF6D28D9)
}

@Composable
internal fun BalatroManagerTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    BmmColor.darkMode = darkMode
    val typography = androidx.compose.material3.Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = PixelFont, letterSpacing = 0.sp),
            displayMedium = displayMedium.copy(fontFamily = PixelFont, letterSpacing = 0.sp),
            displaySmall = displaySmall.copy(fontFamily = PixelFont, fontSize = 38.sp, letterSpacing = 0.sp),
            headlineLarge = headlineLarge.copy(fontFamily = PixelFont, letterSpacing = 0.sp),
            headlineMedium = headlineMedium.copy(fontFamily = PixelFont, letterSpacing = 0.sp),
            headlineSmall = headlineSmall.copy(fontFamily = PixelFont, fontSize = 30.sp, letterSpacing = 0.sp),
            titleLarge = titleLarge.copy(fontFamily = PixelFont, fontSize = 24.sp, letterSpacing = 0.sp),
            titleMedium = titleMedium.copy(fontFamily = PixelFont, fontSize = 20.sp, letterSpacing = 0.sp),
            titleSmall = titleSmall.copy(fontFamily = PixelFont, letterSpacing = 0.sp),
            bodyLarge = bodyLarge.copy(fontFamily = PixelFont, fontSize = 18.sp, letterSpacing = 0.sp),
            bodyMedium = bodyMedium.copy(fontFamily = PixelFont, fontSize = 16.sp, letterSpacing = 0.sp),
            bodySmall = bodySmall.copy(fontFamily = PixelFont, letterSpacing = 0.sp),
            labelLarge = labelLarge.copy(fontFamily = PixelFont, fontSize = 16.sp, letterSpacing = 0.sp),
            labelMedium = labelMedium.copy(fontFamily = PixelFont, fontSize = 14.sp, letterSpacing = 0.sp),
            labelSmall = labelSmall.copy(fontFamily = PixelFont, fontSize = 12.sp, letterSpacing = 0.sp),
        )
    }

    val colors = if (darkMode) darkColorScheme(
            primary = BmmColor.Gold,
            onPrimary = BmmColor.Cream,
            secondary = BmmColor.Green,
            onSecondary = BmmColor.Cream,
            tertiary = BmmColor.Blue,
            background = BmmColor.FeltRed,
            onBackground = BmmColor.Cream,
            surface = BmmColor.Panel,
            onSurface = BmmColor.Cream,
            surfaceVariant = BmmColor.PanelRaised,
            onSurfaceVariant = BmmColor.MutedCream,
            error = BmmColor.Danger,
        ) else lightColorScheme(
            primary = BmmColor.Gold,
            onPrimary = BmmColor.Ink,
            secondary = BmmColor.Green,
            onSecondary = BmmColor.Cream,
            tertiary = BmmColor.Blue,
            background = BmmColor.FeltRed,
            onBackground = BmmColor.Cream,
            surface = BmmColor.Panel,
            onSurface = BmmColor.Cream,
            surfaceVariant = BmmColor.PanelRaised,
            onSurfaceVariant = BmmColor.MutedCream,
            error = BmmColor.Danger,
        )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = BalatroShapes,
        typography = typography,
        content = content,
    )
}
