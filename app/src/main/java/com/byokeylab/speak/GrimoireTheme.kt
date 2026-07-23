package com.byokeylab.speak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 魔導書(グリモワール)テーマのパレット。
 * Dark(夜の書斎)をデフォルトに、Light(羊皮紙)も選べる。
 */
class GrimoirePalette(
    val Parchment: Color,
    val ParchmentLight: Color,
    val ParchmentDeep: Color,
    val Ink: Color,
    val InkFaded: Color,
    val Burgundy: Color,
    val BurgundyDeep: Color,
    val Gold: Color,
    val GoldDark: Color,
    val Moss: Color,
    val BubbleUser: Color,
    val BubbleSystem: Color,
    val isDark: Boolean
) {
    /** 金箔がすり切れたような枠線 */
    val borderBrush = Brush.linearGradient(listOf(Gold, GoldDark, Gold))

    /** ページ背景のグラデーション */
    val pageBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF1B140C), Color(0xFF221A10), Color(0xFF161009)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFEBDCB6), Color(0xFFF4EAD0), Color(0xFFE6D4AA)))
    }
}

val LightGrimoire = GrimoirePalette(
    Parchment = Color(0xFFF0E4C6),
    ParchmentLight = Color(0xFFF8EFDA),
    ParchmentDeep = Color(0xFFE4D2A8),
    Ink = Color(0xFF3A2C18),
    InkFaded = Color(0xFF6E5B3A),
    Burgundy = Color(0xFF6E2B26),
    BurgundyDeep = Color(0xFF4C1D1A),
    Gold = Color(0xFFA37E2C),
    GoldDark = Color(0xFF7C5E1E),
    Moss = Color(0xFF55603A),
    BubbleUser = Color(0xFFEADBB4),
    BubbleSystem = Color(0xFFE3CF9C),
    isDark = false
)

val DarkGrimoire = GrimoirePalette(
    Parchment = Color(0xFF1D160D),
    ParchmentLight = Color(0xFF2A2113),
    ParchmentDeep = Color(0xFF352A18),
    Ink = Color(0xFFEADCBB),
    InkFaded = Color(0xFFB29B70),
    Burgundy = Color(0xFFD08A76),
    BurgundyDeep = Color(0xFFE2A691),
    Gold = Color(0xFFCFA43B),
    GoldDark = Color(0xFFA8842E),
    Moss = Color(0xFFA3B47E),
    BubbleUser = Color(0xFF3B2F1A),
    BubbleSystem = Color(0xFF41341D),
    isDark = true
)

val LocalGrimoire = staticCompositionLocalOf { DarkGrimoire }

/** 既存コードの `Grimoire.Ink` 等をそのまま使えるようにするアクセサ。 */
val Grimoire: GrimoirePalette
    @Composable
    @ReadOnlyComposable
    get() = LocalGrimoire.current

private fun schemeFor(p: GrimoirePalette): ColorScheme {
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.Burgundy,
        onPrimary = p.ParchmentLight,
        primaryContainer = p.ParchmentDeep,
        onPrimaryContainer = p.Ink,
        secondary = p.Gold,
        onSecondary = p.Ink,
        secondaryContainer = p.BubbleUser,
        onSecondaryContainer = p.Ink,
        tertiary = p.Moss,
        tertiaryContainer = p.BubbleSystem,
        onTertiaryContainer = p.Ink,
        background = p.Parchment,
        onBackground = p.Ink,
        surface = p.ParchmentLight,
        onSurface = p.Ink,
        surfaceVariant = p.ParchmentDeep,
        onSurfaceVariant = p.InkFaded,
        outline = p.GoldDark,
        error = p.BurgundyDeep,
        onError = p.ParchmentLight
    )
}

private val GrimoireTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 25.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 11.sp,
        letterSpacing = 1.sp
    )
)

/** 金の飾り枠 */
@Composable
fun Modifier.grimoireBorder(cornerRadius: Dp = 10.dp): Modifier =
    border(width = 1.dp, brush = Grimoire.borderBrush, shape = RoundedCornerShape(cornerRadius))

/** 「✦ ──── ✦ ──── ✦」風の飾り罫 */
@Composable
fun OrnamentDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OrnamentLine(Modifier.weight(1f), toRight = false)
        Text(
            text = "✦",
            color = Grimoire.Gold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        OrnamentLine(Modifier.weight(1f), toRight = true)
    }
}

@Composable
private fun OrnamentLine(modifier: Modifier, toRight: Boolean) {
    val colors = listOf(Color.Transparent, Grimoire.Gold)
    Box(
        modifier = modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(if (toRight) colors.reversed() else colors)
            )
    )
}

@Composable
fun GrimoireTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkGrimoire else LightGrimoire
    CompositionLocalProvider(LocalGrimoire provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = GrimoireTypography
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.pageBrush)
            ) {
                content()
            }
        }
    }
}

/** 古書らしいイタリックの脚注スタイル */
val FootnoteStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
    fontSize = 12.sp,
    letterSpacing = 0.5.sp
)
