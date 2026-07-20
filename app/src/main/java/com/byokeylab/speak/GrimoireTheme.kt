package com.byokeylab.speak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
 * 魔導書（グリモワール）×羊皮紙テーマ。
 * 画像アセットなしで、色・書体・飾り罫だけで古書の雰囲気を出す。
 */
object Grimoire {
    // 羊皮紙
    val Parchment = Color(0xFFF0E4C6)
    val ParchmentLight = Color(0xFFF8EFDA)
    val ParchmentDeep = Color(0xFFE4D2A8)

    // インク
    val Ink = Color(0xFF3A2C18)
    val InkFaded = Color(0xFF6E5B3A)

    // 装飾色
    val Burgundy = Color(0xFF6E2B26)
    val BurgundyDeep = Color(0xFF4C1D1A)
    val Gold = Color(0xFFA37E2C)
    val GoldDark = Color(0xFF7C5E1E)
    val Moss = Color(0xFF55603A)

    /** 金箔がすり切れたような枠線 */
    val borderBrush = Brush.linearGradient(listOf(Gold, GoldDark, Gold))

    /** 経年で焼けた羊皮紙のグラデーション背景 */
    val pageBrush = Brush.verticalGradient(
        listOf(Color(0xFFEBDCB6), Color(0xFFF4EAD0), Color(0xFFE6D4AA))
    )
}

val GrimoireColorScheme = lightColorScheme(
    primary = Grimoire.Burgundy,
    onPrimary = Grimoire.ParchmentLight,
    primaryContainer = Grimoire.ParchmentDeep,
    onPrimaryContainer = Grimoire.Ink,
    secondary = Grimoire.Gold,
    onSecondary = Grimoire.Ink,
    secondaryContainer = Color(0xFFEADBB4),
    onSecondaryContainer = Grimoire.Ink,
    tertiary = Grimoire.Moss,
    tertiaryContainer = Color(0xFFE3CF9C),
    onTertiaryContainer = Color(0xFF44320F),
    background = Grimoire.Parchment,
    onBackground = Grimoire.Ink,
    surface = Grimoire.ParchmentLight,
    onSurface = Grimoire.Ink,
    surfaceVariant = Grimoire.ParchmentDeep,
    onSurfaceVariant = Grimoire.InkFaded,
    outline = Grimoire.GoldDark,
    error = Grimoire.BurgundyDeep,
    onError = Grimoire.ParchmentLight
)

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
fun GrimoireTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GrimoireColorScheme,
        typography = GrimoireTypography
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Grimoire.pageBrush)
        ) {
            content()
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
