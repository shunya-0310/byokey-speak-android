package com.byokeylab.speak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import com.byokeylab.speak.R
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 起動時スプラッシュ (1000ms)。
 * 鍵穴ロゴの縁(金色リム)に沿って、左下の角から 2 つの光が左右に分かれて走り、
 * 800ms で円の 45 度位置(右上)で合流、その後 200ms でフェードアウトする。
 * 速度はガウス分布状 (400ms 付近が最速、両端はゆっくり)。
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var tMs by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (tMs < TOTAL_MS) {
            val now = withFrameNanos { it }
            tMs = (now - start) / 1_000_000f
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.41f)
                .aspectRatio(LOGO_W / LOGO_H),
        ) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLights(tMs)
            }
        }
    }
}

// ---- アニメーション定数 -------------------------------------------------

private const val TOTAL_MS = 1000f
private const val TRAVEL_MS = 800f      // 光の移動時間 (この時点で合流)
private const val FADE_MS = 200f        // 合流後のフェードアウト
private const val CREEP_MS = 200f       // 溜め: じりじり動く時間
private const val CREEP_DIST = 0.06f    // 溜めで進む距離 (パス全長比)
private const val GAUSS_MU = 450f       // バーストの速度ピーク時刻
private const val GAUSS_SIGMA = 110f    // 速度カーブの広がり (右上近傍で減速)
private const val TRAIL_N = 42          // トレイルのサンプル数 (10ms 間隔 = 420ms)

private const val LOGO_W = 658f
private const val LOGO_H = 920f

// 鍵穴リム中心線の幾何 (元画像 658x920 のピクセル解析から算出、正規化済み)
private val CORNER_BL = Offset(0.26915f, 0.72391f)  // 左下角 (出発点)
private val CORNER_BR = Offset(0.70167f, 0.72391f)  // 右下角
private val JUNCTION_L = Offset(0.37933f, 0.33554f) // 左スラントと円の接続点
private val JUNCTION_R = Offset(0.59210f, 0.33609f) // 右スラントと円の接続点
private val CIRCLE_C = Offset(0.48632f, 0.21413f)   // 円の中心
private const val CIRCLE_R = 0.20061f               // 半径 (幅に対する比)
private const val ARC_START_L = 122.2f              // 左接続点の角度 (Android 座標系)
private const val ARC_SWEEP_L = 192.8f              // 左回り: 左辺→上→45度位置
private const val ARC_START_R = 58.2f               // 右接続点の角度
private const val ARC_SWEEP_R = -103.2f             // 右回り: 右辺→45度位置

private val SplashBg = Color(0xFFFEFEFE)            // ロゴ画像の背景色に一致
private val GlowWarm = Color(0xFFFFF7E0)            // 外側グローの暖色

// ---- 描画 ---------------------------------------------------------------

private class LightPath(path: Path) {
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    fun position(fraction: Float): Offset = measure.getPosition(fraction * length)
}

private fun buildPaths(w: Float, h: Float): Pair<LightPath, LightPath> {
    val r = CIRCLE_R * w
    val oval = Rect(
        CIRCLE_C.x * w - r, CIRCLE_C.y * h - r,
        CIRCLE_C.x * w + r, CIRCLE_C.y * h + r,
    )
    val a = Path().apply {
        moveTo(CORNER_BL.x * w, CORNER_BL.y * h)
        lineTo(JUNCTION_L.x * w, JUNCTION_L.y * h)
        arcTo(oval, ARC_START_L, ARC_SWEEP_L, false)
    }
    val b = Path().apply {
        moveTo(CORNER_BL.x * w, CORNER_BL.y * h)
        lineTo(CORNER_BR.x * w, CORNER_BR.y * h)
        lineTo(JUNCTION_R.x * w, JUNCTION_R.y * h)
        arcTo(oval, ARC_START_R, ARC_SWEEP_R, false)
    }
    return LightPath(a) to LightPath(b)
}

/** 標準正規分布の累積分布関数 (erf の Abramowitz–Stegun 近似)。 */
private fun normCdf(x: Float): Float {
    val z = abs(x) / sqrt(2f)
    val t = 1f / (1f + 0.3275911f * z)
    val poly = t * (0.254829592f + t * (-0.284496736f + t * (1.421413741f + t * (-1.453152027f + t * 1.061405429f))))
    val erf = 1f - poly * exp(-z * z)
    return 0.5f * (1f + if (x >= 0f) erf else -erf)
}

/**
 * 経過時間 -> パス上の進行率 (0..1)。
 * 0..200ms は溜め (じりじり前進)、その後ガウス CDF で一気に加速し、
 * 右上近傍 (800ms 手前) で再び減速して到着する。
 */
private fun progress(tMs: Float): Float {
    if (tMs <= 0f) return 0f
    if (tMs <= CREEP_MS) {
        // 溜め: わずかに前進 (少し加速気味の易しいカーブ)
        val x = tMs / CREEP_MS
        return CREEP_DIST * x * x * (2f - x) // 滑らかに 0 -> CREEP_DIST
    }
    val lo = normCdf((CREEP_MS - GAUSS_MU) / GAUSS_SIGMA)
    val hi = normCdf((TRAVEL_MS - GAUSS_MU) / GAUSS_SIGMA)
    val g = (normCdf((tMs - GAUSS_MU) / GAUSS_SIGMA) - lo) / (hi - lo)
    return (CREEP_DIST + (1f - CREEP_DIST) * g).coerceIn(0f, 1f)
}

private fun DrawScope.glowPoint(center: Offset, coreR: Float, glowR: Float, intensity: Float) {
    if (intensity <= 0.01f) return
    val i = intensity.coerceAtMost(1f)
    // 外側の大きくぼけたグロー
    drawCircle(
        brush = Brush.radialGradient(
            0f to GlowWarm.copy(alpha = 0.55f * i),
            0.35f to GlowWarm.copy(alpha = 0.22f * i),
            1f to Color.Transparent,
            center = center, radius = glowR,
        ),
        radius = glowR, center = center, blendMode = BlendMode.Plus,
    )
    // 飽和する白いコア
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = i),
            0.6f to Color.White.copy(alpha = 0.5f * i),
            1f to Color.Transparent,
            center = center, radius = coreR,
        ),
        radius = coreR, center = center, blendMode = BlendMode.Plus,
    )
}

private fun DrawScope.drawLights(tMs: Float) {
    val fade = if (tMs <= TRAVEL_MS) 1f else (1f - (tMs - TRAVEL_MS) / FADE_MS).coerceIn(0f, 1f)
    if (fade <= 0f) return

    val (pathA, pathB) = buildPaths(size.width, size.height)
    val u = size.width / LOGO_W  // 元画像ピクセル -> 描画ピクセル
    val t = tMs.coerceAtMost(TRAVEL_MS)

    for (path in listOf(pathA, pathB)) {
        // 流れ星のような軌跡: 直近 ~420ms の位置に減衰するグローを置く
        for (k in 1 until TRAIL_N) {
            val tt = t - k * 10f
            if (tt < 0f) break
            val w = 1f - k.toFloat() / TRAIL_N
            glowPoint(
                center = path.position(progress(tt)),
                coreR = (4.5f * w + 1f) * u * 2.2f,
                glowR = (13f * w + 2f) * u * 3f,
                intensity = fade * 0.4f * w * w,
            )
        }
        // 先頭の光
        glowPoint(
            center = path.position(progress(t)),
            coreR = 6f * u * 2.2f,
            glowR = 26f * u * 3f,
            intensity = fade,
        )
    }

    // 合流の一瞬のきらめき (780ms から立ち上がり)
    if (tMs >= TRAVEL_MS - 20f) {
        val flash = ((tMs - (TRAVEL_MS - 20f)) / 60f).coerceAtMost(1f)
        glowPoint(
            center = pathA.position(1f),
            coreR = 8f * u * 2.2f,
            glowR = 34f * u * 3f,
            intensity = flash * fade * 1.2f,
        )
    }
}
