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
        lead = "アプリを購入いただきどうもありがとうございます。会話を始めるにあたって、まずは本アプリの特徴と注意点を説明します。",
        sections = listOf(
            OnboardingSection("アプリ代金", "月額料金はありません。アプリ本体は買い切りです。", accent = true),
            OnboardingSection("API利用料", "本アプリはAPIという機能を使ってAIと会話する仕組みです。会話の量に応じて「API利用料」という形で費用が発生します。"),
            OnboardingSection("APIとは？", "APIは、本アプリとAIサービスをつなぐ窓口です。入力した内容をAIへ送り、その返答をアプリで受け取るために使います。"),
            OnboardingSection("目安", "推奨モデルのGemini 3.1 Flash-Liteでも、普通に使うと月数百円程度になることがあります。会話が多い月は1,000円前後以上になる可能性もあります。")
        ),
        backgroundImage = R.drawable.onboarding_bg_1
    ),
    OnboardingPage(
        title = "BYOKの仕組みとAPIキー",
        lead = "BYOK(Bring Your Own Key)は、あなた自身が取得したAPIキーをアプリに設定して使う方式です。",
        sections = listOf(
            OnboardingSection("設定するもの", "Gemini・OpenAI・Claudeのいずれかで、APIキーを取得して設定してください。"),
            OnboardingSection("APIキー設定ガイド", "利用するAIサービスに合わせて、下のガイドを開いてください。"),
            OnboardingSection("BYOKアプリの仕組み", "アプリ代金、API利用料、APIキーやデータの流れを図で確認できます。")
        ),
        backgroundImage = R.drawable.onboarding_bg_2,
        showApiGuides = true,
        showByokDiagram = true
    ),
    OnboardingPage(
        title = "API利用の注意点",
        lead = "APIキーは、クレジットカードやパスワードに近い注意が必要な情報です。",
        sections = listOf(
            OnboardingSection(
                label = "料金",
                body = "API利用料はアプリ代金とは別に、各プロバイダー（Google、OpenAIなど）へ支払います。料金は会話履歴、AIの思考トークン、利用回数に応じて変わります。",
                linkLabel = "料金シミュレーションを開く（BYOKey Labサイト）",
                linkUrl = AppLinks.PRICING_SIMULATOR
            ),
            OnboardingSection("予算管理", "各プロバイダーの管理画面で、利用上限や予算アラートを必ず設定してください。"),
            OnboardingSection(
                "APIキーの取り扱いについて",
                "LLMプロバイダ各社の安全ガイドライン上、モバイルアプリへのAPIキー入力は推奨されていません。本アプリを利用する場合は、このセキュリティリスクを十分に理解し、必ず利用上限を設定したうえで、APIキーを慎重に取り扱ってください。APIキーは他人と共有せず、スクリーンショット、画面共有、ログ、Git、Drive、問い合わせ本文などにも含めないでください。",
                accent = true
            )
        ),
        backgroundImage = R.drawable.onboarding_bg_3
    ),
    OnboardingPage(
        title = "データの扱い",
        lead = "APIキーと会話内容は、ユーザーとLLMプロバイダーだけが扱う情報です。",
        sections = listOf(
            OnboardingSection("APIキーを開発側が聞くことはありません。", "BYOKey Labのサーバーに、APIキー・会話・ユーザー情報を保存することはありません。"),
            OnboardingSection("本アプリが買い切りである理由", "APIキーを安全に扱い、開発側がユーザー情報を持たない設計にするため、本アプリは買い切り型にしています。", accent = true),
            OnboardingSection("端末内保存", "会話・Vocabulary List・APIキーはこの端末の中に保存され、いつでも削除できます。")
        ),
        backgroundImage = R.drawable.onboarding_bg_4
    ),
    OnboardingPage(
        title = "始めましょう",
        lead = "APIキーを設定すると、すぐに会話を始められます。",
        sections = listOf(
            OnboardingSection("はじめにAPIキーを設定", "設定画面でモデルを選び、APIキーを入力します。"),
            OnboardingSection("会話設定", "CEFRレベル、コーチの性格、解説の詳しさや要否は、設定画面からいつでも自由に変更できます。※なお、回答の癖はモデルに依存することもあります。設定を細かく調整してみると新たな発見があるかも…!"),
            OnboardingSection("会話のヒント", "相手はAIなので、言葉に迷ったときは途中で日本語を混ぜても大丈夫です。Quick Assistを使えば、言葉に詰まっても表現の候補を見ながら会話を続けられます。"),
            OnboardingSection("あとで設定", "設定は後回しにしてもOKです。最初の送信前に設定画面へご案内します。")
        ),
        backgroundImage = R.drawable.onboarding_bg_5
    )
)

/**
 * Onboarding (FR-001): BYOK方式・API料金・外部送信の説明。
 */
@Composable
fun OnboardingScreen(
    onFinish: (goToSettings: Boolean) -> Unit
) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var transitionProgress by remember { mutableFloatStateOf(0f) }
    var transitionDelta by remember { mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(1f) }
    var cardHeight by remember { mutableFloatStateOf(1f) }
    var isAnimating by remember { mutableStateOf(false) }
    var externalDataConsent by rememberSaveable { mutableStateOf(false) }
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
                    text = "会話内容・コーチ設定・分析対象の文章が、選択したLLMプロバイダーへ送信されることに同意します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grimoire.Ink,
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = { onFinish(true) },
                modifier = Modifier.fillMaxWidth(),
                enabled = externalDataConsent,
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Text("APIキーを設定する")
            }
            TextButton(
                onClick = { onFinish(false) },
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
