package com.byokeylab.speak

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * アプリ同梱のPDFガイド(assets/guides)。
 * Settingsのヘルプ、Onboardingの両方から開ける。
 */
object Guides {
    data class Guide(
        val title: String,
        /** nullなら準備中 */
        val assetPath: String?,
        val platformLabel: String? = null,
        val platformUrl: String? = null,
        val platformHint: String? = null
    )

    val all = listOf(
        Guide("BYOKアプリの仕組み(図解)", "guides/byok_overview.pdf"),
        Guide(
            "APIキー設定ガイド(Gemini)",
            "guides/api_guide_gemini.pdf",
            "Google AI Studio",
            "https://aistudio.google.com/app/apikey",
            "利用料の確認もこちらから"
        )
    )

    val apiKeyGuides: List<Guide> = all.drop(1)

    /** assetsのPDFをキャッシュへコピーし、端末のPDFビューアで開く。 */
    fun open(context: Context, assetPath: String) {
        runCatching {
            val fileName = assetPath.substringAfterLast('/')
            val outDir = File(context.cacheDir, "guides").apply { mkdirs() }
            val outFile = File(outDir, fileName)
            context.assets.open(assetPath).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
                )
            }
            context.startActivity(intent)
        }
    }
}
