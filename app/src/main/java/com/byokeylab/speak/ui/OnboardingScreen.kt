package com.byokeylab.speak.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.AppLinks
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.AppSound
import com.byokeylab.speak.LocalAppSoundEffects
import com.byokeylab.speak.Guides
import com.byokeylab.speak.R
import com.byokeylab.speak.grimoireBorder
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private data class OnboardingPage(
    val title: String,
    val lead: String,
    val sections: List<OnboardingSection>,
    @DrawableRes val backgroundImage: Int,
    val showApiGuides: Boolean = false,
    val showByokDiagram: Boolean = false
)

private data class OnboardingSection(
    val label: String,
    val body: String,
    val accent: Boolean = false,
    val linkLabel: String? = null,
    val linkUrl: String? = null
)

private val pages = listOf(
    OnboardingPage(
        title = "BYOKey Speakへようこそ",
        lead = "Geminiを使って、自分だけのコーチとの英会話を通じて英語を学習するAI英会話アプリです。",
        sections = listOf(
            OnboardingSection("自由に設定できるコーチ", "コーチの性格や話し方をあなたの言葉で設定できます。学習の方針やコーチに知っておいてもらいたいことなど、あなたの言葉で伝えてみてください。", accent = true),
            OnboardingSection("会話レベル", "CEFR A1からC2まで対応しています。自分に合った会話のレベルをいつでも選べます。"),
            OnboardingSection("学習の記録", "会話で使った表現はVocabulary Listに反映されていきます。また、会話の履歴からあなたの特徴を分析し、学習に役立てることができます。※本アプリでは音声の分析はできません。")
        ),
        backgroundImage = R.drawable.onboarding_bg_1
    ),
    OnboardingPage(
        title = "BYOKの仕組みとAPIキー",
        lead = "BYOKはBring Your Own Keyの略です。自分のGemini APIキーを使って会話します。",
        sections = listOf(
            OnboardingSection("APIキーとは", "Geminiを使うための大切な合鍵です。GoogleアカウントがあればGoogle AI Studioで作成できます。"),
            OnboardingSection("料金", "Geminiの利用量に応じてGoogleに対して費用が発生します。会話の回数だけでなく、文章の量や分析の頻度が影響するため、都度Google AI Studioのダッシュボードで料金の確認をしてください。また、事前に上限を設定してください。", accent = true),
            OnboardingSection("確認のしかた", "利用料はGoogle AI Studioで随時確認してください。APIキーの取得方法はアプリ内ヘルプで確認できます。")
        ),
        backgroundImage = R.drawable.onboarding_bg_2,
        showApiGuides = true,
        showByokDiagram = false
    ),
    OnboardingPage(
        title = "APIキーを安全に使うために",
        lead = "APIキーはGeminiの利用権限に結びつく大切な情報です。",
        sections = listOf(
            OnboardingSection("重要な注意事項", "Googleは、アプリへAPIキーを入力して使う構成を一般には推奨していません。本アプリは、このリスクを理解した利用者が自分のキーを自分の責任で設定するBYOK方式です。", accent = true),
            OnboardingSection("安全に使うための4つのポイント", "本アプリ専用のAPIキーを作る・利用上限を設定する・利用量を定期的に確認する・APIキーを定期的に更新することをおすすめします。"),
            OnboardingSection(
                "大切なお願い",
                "APIキーを他人と共有しないでください。スクリーンショット、画面共有のほか、問い合わせ本文にも含めないでください。"
            )
        ),
        backgroundImage = R.drawable.onboarding_bg_3
    ),
    OnboardingPage(
        title = "データの扱い",
        lead = "あなたのAPIキーや会話データを、BYOKey Labが受け取ることはありません。",
        sections = listOf(
            OnboardingSection("Geminiへ直接送信", "会話や設定したコーチ情報は、この端末からGemini APIへ直接送信されます。BYOKey Labのサーバーは経由しません。", accent = true),
            OnboardingSection("開発側では保存・把握しません", "BYOKey LabはAPIキーを取得・保存・問い合わせることはありません。アプリのソースコードはGitHubで公開し、通信とデータの扱いを確認できるようにしています。"),
            OnboardingSection("端末内のデータ", "会話履歴、Vocabulary List、設定は端末内に保存されます。バックアップと復元はアプリ内で行えます。アプリのアンインストールやアプリデータ／キャッシュの消去で、保存データが削除される場合があります。")
        ),
        backgroundImage = R.drawable.onboarding_bg_4
    ),
    OnboardingPage(
        title = "さあ、はじめましょう",
        lead = "設定画面からGeminiモデル、APIキー、会話レベル、コーチの性格を設定しましょう。",
        sections = listOf(
            OnboardingSection("設定はいつでも変更できます", "会話を始めたあとでも、レベルやコーチの設定は自由に変えられます。"),
            OnboardingSection("外部送信と利用料", "会話内容はGeminiへ送信されます。Gemini音声を選ぶ場合は、読み上げる返信文もGeminiへ送信され、API利用料が発生します。", accent = true),
            OnboardingSection("対象年齢", "Gemini APIの利用条件により、本アプリは18歳以上の方を対象とします。")
        ),
        backgroundImage = R.drawable.onboarding_bg_5
    )
)

/**
 * Onboarding (FR-001): BYOK方式・API料金・外部送信の説明。
 */
@Composable
fun OnboardingScreen(
    initialPage: Int = 0,
    requireExternalDataConsent: Boolean = true,
    onFinish: (goToSettings: Boolean, externalDataConsented: Boolean) -> Unit
) {
    var pageIndex by rememberSaveable(initialPage) {
        mutableIntStateOf(initialPage.coerceIn(0, pages.lastIndex))
    }
    var transitionProgress by remember { mutableFloatStateOf(0f) }
    var transitionDelta by remember { mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(1f) }
    var cardHeight by remember { mutableFloatStateOf(1f) }
    var isAnimating by remember { mutableStateOf(false) }
    var externalDataConsent by rememberSaveable(requireExternalDataConsent) {
        mutableStateOf(!requireExternalDataConsent)
    }
    val scope = rememberCoroutineScope()
    val soundEffects = LocalAppSoundEffects.current
    val uriHandler = LocalUriHandler.current
    val isLast = pageIndex == pages.lastIndex

    fun animateToPage(delta: Int, playSound: Boolean = true) {
        if (isAnimating || pageIndex + delta !in pages.indices) return
        if (playSound) soundEffects?.play(AppSound.Pages)
        transitionDelta = delta
        isAnimating = true
        scope.launch {
            animate(
                initialValue = transitionProgress,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
            ) { value, _ ->
                transitionProgress = value
            }
            pageIndex += delta
            transitionProgress = 0f
            transitionDelta = 0
            dragDistance = 0f
            isAnimating = false
        }
    }

    fun settleBack() {
        if (isAnimating) return
        isAnimating = true
        scope.launch {
            animate(
                initialValue = transitionProgress,
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            ) { value, _ ->
                transitionProgress = value
            }
            transitionDelta = 0
            dragDistance = 0f
            isAnimating = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "✦ BYOKey Speak ✦",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            ),
            color = Grimoire.BurgundyDeep
        )
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged {
                    cardWidth = it.width.toFloat().coerceAtLeast(1f)
                    cardHeight = it.height.toFloat().coerceAtLeast(1f)
                }
                .pointerInput(pageIndex, isAnimating, cardWidth) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (!isAnimating) {
                                dragDistance = 0f
                                soundEffects?.play(AppSound.Pages)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isAnimating) return@detectHorizontalDragGestures
                            dragDistance += dragAmount
                            val delta = if (dragDistance < 0f) 1 else -1
                            if (pageIndex + delta in pages.indices) {
                                transitionDelta = delta
                                transitionProgress = (abs(dragDistance) / cardWidth).coerceIn(0f, 1f)
                                change.consume()
                            } else {
                                transitionDelta = 0
                                transitionProgress = 0f
                            }
                        },
                        onDragEnd = {
                            if (transitionProgress >= 0.18f && transitionDelta != 0) {
                                animateToPage(transitionDelta, playSound = false)
                            } else {
                                settleBack()
                            }
                        },
                        onDragCancel = { settleBack() }
                    )
                }
        ) {
            val targetIndex = pageIndex + transitionDelta
            if (transitionDelta != 0 && targetIndex in pages.indices) {
                val targetScale = 0.78f + transitionProgress * 0.22f
                key(targetIndex) {
                    OnboardingPageCard(
                        page = pages[targetIndex],
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = transitionDelta * cardWidth * (1f - transitionProgress)
                                translationY = cardHeight * 0.38f * (1f - transitionProgress)
                                rotationY = transitionDelta * 90f * (1f - transitionProgress)
                                scaleX = targetScale
                                scaleY = targetScale
                                alpha = transitionProgress.coerceAtLeast(0.08f)
                            }
                    )
                }
            }

            val arc = sin(PI * transitionProgress).toFloat()
            key(pageIndex) {
                OnboardingPageCard(
                    page = pages[pageIndex],
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -transitionDelta * cardWidth * transitionProgress
                            translationY = cardHeight * (0.12f * arc + 0.38f * transitionProgress)
                            rotationY = -transitionDelta * 90f * transitionProgress
                            val scale = 1f - 0.22f * transitionProgress
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - 0.24f * transitionProgress
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pages.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (index == pageIndex) Grimoire.Gold else Grimoire.ParchmentDeep,
                            shape = CircleShape
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (isLast) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = externalDataConsent,
                    onCheckedChange = { externalDataConsent = it }
                )
                Text(
                    text = "私は18歳以上であり、会話内容・コーチ設定・分析対象の文章がGeminiへ送信されること、ならびに上記のAPIキーに関するリスクを理解しました。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.Ink,
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = { onFinish(true, externalDataConsent) },
                modifier = Modifier.fillMaxWidth(),
                enabled = externalDataConsent,
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Text("APIキーを設定する")
            }
            TextButton(
                onClick = { onFinish(false, externalDataConsent) },
                enabled = externalDataConsent
            ) {
                Text("あとで設定する", color = Grimoire.InkFaded)
            }
        } else {
            Button(
                onClick = { animateToPage(1) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAnimating,
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Text("次へ")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { animateToPage(-1) },
                    enabled = pageIndex > 0 && !isAnimating
                ) {
                    Text(
                        "戻る",
                        color = if (pageIndex > 0) Grimoire.InkFaded else Grimoire.InkFaded.copy(alpha = 0.35f)
                    )
                }
                TextButton(
                    onClick = {
                        if (!isAnimating) {
                            soundEffects?.play(AppSound.Pages)
                            pageIndex = pages.lastIndex
                        }
                    }
                ) {
                    Text("スキップ", color = Grimoire.InkFaded)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { uriHandler.openUri(AppLinks.PRIVACY_POLICY) }) {
            Text(
                text = "プライバシーポリシーを確認",
                style = MaterialTheme.typography.labelSmall,
                color = Grimoire.GoldDark
            )
        }
    }
}

@Composable
private fun OnboardingPageCard(
    page: OnboardingPage,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = modifier.grimoireBorder(12.dp),
        colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentLight),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(page.backgroundImage),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .graphicsLayer(alpha = 0.30f)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Grimoire.Burgundy
                )
                Text(
                    text = page.lead,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Grimoire.Ink
                )
                page.sections.forEach { section ->
                    OnboardingInfoBlock(section)
                    if (section.label == "APIキー設定ガイド" && page.showApiGuides) {
                        Guides.apiKeyGuides.forEach { guide ->
                            TextButton(
                                onClick = { guide.assetPath?.let { Guides.open(context, it) } },
                                enabled = guide.assetPath != null
                            ) {
                                Text("▶ ${guide.title}", color = Grimoire.GoldDark)
                            }
                            if (guide.platformLabel != null && guide.platformUrl != null) {
                                TextButton(
                                    onClick = { openOnboardingUrl(context, guide.platformUrl) },
                                    modifier = Modifier.padding(start = 24.dp)
                                ) {
                                    Text("↳ 公式: ${guide.platformLabel}を開く", color = Grimoire.GoldDark)
                                }
                            }
                        }
                    }
                    if (section.label == "BYOKアプリの仕組み" && page.showByokDiagram) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .grimoireBorder(8.dp)
                                .clickable {
                                    Guides.all.firstOrNull()?.assetPath?.let { Guides.open(context, it) }
                                },
                            color = Grimoire.ParchmentLight.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.byok_app_diagram),
                                contentDescription = "BYOKアプリの仕組みを図で表示",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingInfoBlock(section: OnboardingSection) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (section.accent) {
            Grimoire.ParchmentDeep.copy(alpha = 0.9f)
        } else {
            Grimoire.ParchmentLight.copy(alpha = 0.86f)
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = section.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (section.accent) Grimoire.BurgundyDeep else Grimoire.GoldDark
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = section.body,
                style = MaterialTheme.typography.bodyMedium,
                color = Grimoire.Ink
            )
            if (section.linkLabel != null && section.linkUrl != null) {
                TextButton(onClick = { openOnboardingUrl(context, section.linkUrl) }) {
                    Text(section.linkLabel, color = Grimoire.GoldDark)
                }
            }
        }
    }
}

private fun openOnboardingUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            )
        )
    }
}
