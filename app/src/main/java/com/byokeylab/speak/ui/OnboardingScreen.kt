package com.byokeylab.speak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.byokeylab.speak.Grimoire
import com.byokeylab.speak.Guides
import com.byokeylab.speak.grimoireBorder

private data class OnboardingPage(
    val title: String,
    val body: String,
    val showGuides: Boolean = false
)

private val pages = listOf(
    OnboardingPage(
        title = "BYOKey Speakへようこそ",
        body = "AIと英会話を練習するアプリです。\n\nこのアプリは買い切りで、月額料金はありません。\nそのかわり、AI(LLM)のAPI利用料が「使った分だけ」(従量課金)かかります。\n\n【目安】推奨モデルのGemini 3.1 Flash-Liteなら、毎日50往復会話しても月間で数百円程度です(1日10往復なら月数十円程度)。",
        showGuides = false
    ),
    OnboardingPage(
        title = "BYOKの仕組みとAPIキー",
        body = "BYOK(Bring Your Own Key)は、あなた自身が取得したAPIキーをアプリに設定して使う方式です。\n\nGemini・OpenAI・Claudeのいずれかで、APIキーを取得して設定してください。手順は下のガイドで図解しています(あとからSettings > ヘルプでいつでも見られます)。",
        showGuides = true
    ),
    OnboardingPage(
        title = "API利用の注意点",
        body = "・API利用料はアプリ代金とは別に、各プロバイダーへ支払う従量課金です。\n・料金は使ったトークン量で決まります。使わない月はほぼ0円です。\n・各プロバイダーの管理画面で、利用上限(予算アラート)を設定しておくと安心です。\n・APIキーは他人と共有しないでください。",
        showGuides = false
    ),
    OnboardingPage(
        title = "データの扱い",
        body = "会話内容は、あなたが選んだLLMプロバイダーへ直接送信されます。\n\nBYOKey LabのサーバーにAPIキーや会話が保存されることはありません。会話・単語帳・APIキーはすべてこの端末の中に保存され、いつでも削除できます。",
        showGuides = false
    ),
    OnboardingPage(
        title = "始めましょう",
        body = "最初にAPIキーを設定すると、30秒で会話を始められます。\n\n設定は後回しにしてもOK。その場合は、最初の送信前に設定画面へご案内します。",
        showGuides = false
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
    val page = pages[pageIndex]
    val isLast = pageIndex == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "✦ BYOKey Speak ✦",
            style = MaterialTheme.typography.titleLarge,
            color = Grimoire.BurgundyDeep
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .grimoireBorder(12.dp),
            colors = CardDefaults.cardColors(containerColor = Grimoire.ParchmentLight),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Grimoire.Burgundy
                )
                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grimoire.Ink
                )
                if (page.showGuides) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Guides.all.forEach { guide ->
                        TextButton(
                            onClick = { guide.assetPath?.let { Guides.open(context, it) } },
                            enabled = guide.assetPath != null
                        ) {
                            Text(
                                text = "▶ ${guide.title}",
                                color = if (guide.assetPath != null) Grimoire.GoldDark else Grimoire.InkFaded
                            )
                        }
                    }
                }
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
            Button(
                onClick = { onFinish(true) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Text("APIキーを設定する")
            }
            TextButton(onClick = { onFinish(false) }) {
                Text("あとで設定する", color = Grimoire.InkFaded)
            }
        } else {
            Button(
                onClick = { pageIndex++ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Grimoire.Burgundy)
            ) {
                Text("次へ")
            }
            TextButton(onClick = { pageIndex = pages.lastIndex }) {
                Text("スキップ", color = Grimoire.InkFaded)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "続行すると、会話内容が選択したLLMプロバイダーへ送信されることに同意したものとみなされます。",
            style = MaterialTheme.typography.labelSmall,
            color = Grimoire.InkFaded,
            textAlign = TextAlign.Center
        )
    }
}
