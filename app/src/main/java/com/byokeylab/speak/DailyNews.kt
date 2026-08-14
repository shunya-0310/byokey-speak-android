package com.byokeylab.speak

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

enum class NewsCategory(val id: String, val label: String) {
    PoliticsEconomy("politics_economy", "Politics & Economy"),
    Technology("technology", "Technology"),
    Sports("sports", "Sports"),
    Entertainment("entertainment", "Entertainment");

    companion object {
        fun fromId(id: String): NewsCategory? = entries.firstOrNull { it.id == id }
    }
}

data class NewsSource(
    val title: String,
    val url: String
) {
    val host: String
        get() = runCatching { URI(url).host.orEmpty().removePrefix("www.") }
            .getOrDefault("")

    val iconUrl: String
        get() = if (host.isBlank()) {
            ""
        } else {
            "https://www.google.com/s2/favicons?domain=$host&sz=64"
        }
}

data class CoachLeads(
    val friendly: String,
    val energetic: String,
    val calm: String,
    val direct: String
)

enum class NewsFallbackKind {
    None,
    RssHeadline,
    SourceList;

    companion object {
        fun fromJson(value: String?, fallback: Boolean): NewsFallbackKind = when (value) {
            "rss_headline" -> RssHeadline
            "source_list" -> SourceList
            else -> if (fallback) SourceList else None
        }
    }
}

data class DailyNewsItem(
    val id: String,
    val category: NewsCategory,
    val headline: String,
    val summary: String,
    val question: String,
    val coachLeads: CoachLeads,
    val sources: List<NewsSource>,
    val fallbackKind: NewsFallbackKind = NewsFallbackKind.None
) {
    val canStartConversation: Boolean
        get() = fallbackKind != NewsFallbackKind.SourceList
}

data class DailyNewsFeed(
    val date: String,
    val generatedAt: String,
    val preview: Boolean,
    val items: List<DailyNewsItem>
)

data class DailyNewsUiState(
    val loading: Boolean = true,
    val feed: DailyNewsFeed? = null,
    val usingCachedData: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

object DailyNewsParser {
    fun parse(raw: String): DailyNewsFeed {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion") == 1) { "Unsupported news schema." }
        val date = root.getString("date")
        LocalDate.parse(date)
        val itemsJson = root.getJSONArray("items")
        val items = buildList {
            for (index in 0 until itemsJson.length()) {
                val item = itemsJson.getJSONObject(index)
                val category = NewsCategory.fromId(item.getString("category"))
                    ?: error("Unknown news category.")
                val leads = item.getJSONObject("coachLeads")
                val sourcesJson = item.getJSONArray("sources")
                val sources = buildList {
                    for (sourceIndex in 0 until sourcesJson.length()) {
                        val source = sourcesJson.getJSONObject(sourceIndex)
                        val url = source.getString("url")
                        require(url.startsWith("https://")) { "News source must use HTTPS." }
                        add(NewsSource(source.getString("title"), url))
                    }
                }
                require(sources.isNotEmpty()) { "Every news item needs a source." }
                add(
                    DailyNewsItem(
                        id = item.getString("id"),
                        category = category,
                        headline = requireNewsText(item.getString("headline"), "headline"),
                        summary = requireNewsText(item.getString("summary"), "summary"),
                        question = requireNewsText(item.getString("question"), "question"),
                        coachLeads = CoachLeads(
                            friendly = requireNewsText(leads.getString("friendly"), "coachLeads.friendly"),
                            energetic = requireNewsText(leads.getString("energetic"), "coachLeads.energetic"),
                            calm = requireNewsText(leads.getString("calm"), "coachLeads.calm"),
                            direct = requireNewsText(leads.getString("direct"), "coachLeads.direct")
                        ),
                        sources = sources,
                        fallbackKind = NewsFallbackKind.fromJson(
                            value = item.optString("fallbackKind", ""),
                            fallback = item.optBoolean("fallback", false)
                        )
                    )
                )
            }
        }
        require(NewsCategory.entries.all { category -> items.any { it.category == category } }) {
            "The daily feed must contain all four categories."
        }
        return DailyNewsFeed(
            date = date,
            generatedAt = root.getString("generatedAt"),
            preview = root.optString("status") != "live",
            items = items
        )
    }

    private fun requireNewsText(value: String, field: String): String {
        val text = value.trim()
        require(text.isNotEmpty()) { "$field is required." }
        require(!looksLikeJsonLeak(text)) { "$field contains leaked JSON." }
        return text
    }

    private fun looksLikeJsonLeak(text: String): Boolean {
        val jsonKeyPattern =
            Regex("""(?i)(^|[\s,{])"(headline|summary|question|coachLeads|sources|friendly|energetic|calm|direct)"\s*:""")
        val looseKeyPattern =
            Regex("""(?i)(headline|summary|question|coachLeads|sources|friendly|energetic|calm|direct)\s*:\s*[{"]""")
        return jsonKeyPattern.containsMatchIn(text) ||
            looseKeyPattern.containsMatchIn(text) ||
            text.contains("Treat these sources as the initial factual context", ignoreCase = true) ||
            text.contains("Search the web again when the learner", ignoreCase = true)
    }
}

class DailyNewsClient(context: Context) {
    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, "daily_news.json")
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun load(): DailyNewsUiState = withContext(Dispatchers.IO) {
        val remote = fetchRemoteFeed()
        if (remote != null) {
            runCatching { cacheFile.writeText(remote.first, Charsets.UTF_8) }
            val feedNotice = when {
                LocalDate.parse(remote.second.date).isBefore(LocalDate.now()) ->
                    DAILY_NEWS_SCHEDULE_NOTICE
                else -> null
            }
            return@withContext DailyNewsUiState(
                loading = false,
                feed = remote.second,
                usingCachedData = false,
                error = feedNotice
            )
        }

        val cached = runCatching {
            DailyNewsParser.parse(cacheFile.readText(Charsets.UTF_8))
        }.getOrNull()
        if (cached != null) {
            return@withContext DailyNewsUiState(
                loading = false,
                feed = cached,
                usingCachedData = true,
                error = "最新ニュースを取得できなかったため、保存済みの記事を表示しています。"
            )
        }

        val bundled = runCatching {
            appContext.assets.open("news/fallback_daily_news.json")
                .bufferedReader(Charsets.UTF_8)
                .use { DailyNewsParser.parse(it.readText()) }
        }.getOrNull()
        DailyNewsUiState(
            loading = false,
            feed = bundled,
            usingCachedData = true,
            error = if (bundled == null) {
                "ニュースを読み込めませんでした。通信状況を確認してください。"
            } else {
                null
            }
        )
    }

    private fun fetchRemoteFeed(): Pair<String, DailyNewsFeed>? {
        val today = LocalDate.now()
        val candidates = FEED_URLS.mapNotNull { url ->
            runCatching { fetchRemote(url) }.getOrNull()
        }
        return candidates.firstOrNull { (_, feed) ->
            runCatching { LocalDate.parse(feed.date) == today }.getOrDefault(false)
        } ?: candidates.maxByOrNull { (_, feed) ->
            runCatching { LocalDate.parse(feed.date) }.getOrDefault(LocalDate.MIN)
        }
    }

    private fun fetchRemote(url: String): Pair<String, DailyNewsFeed> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "News feed request failed." }
            val raw = response.body?.string().orEmpty()
            return raw to DailyNewsParser.parse(raw)
        }
    }

    companion object {
        // The public feed contains no user data or API credentials.
        const val DAILY_NEWS_SCHEDULE_NOTICE = "ニュースは毎朝6時半頃に配信されます。"
        const val FEED_URL =
            "https://byokey-lab.com/news/daily.json"
        const val GITHUB_FEED_URL =
            "https://raw.githubusercontent.com/shunya-0310/byokey-lab-site/main/public/news/daily.json"
        val FEED_URLS = listOf(FEED_URL, GITHUB_FEED_URL)
    }
}

object CoachNewsTone {
    fun opener(item: DailyNewsItem, coachInstructions: String): String {
        val lower = coachInstructions.lowercase()
        return when {
            listOf("calm", "穏やか", "落ち着", "丁寧").any(lower::contains) ->
                item.coachLeads.calm
            listOf("direct", "率直", "厳し", "端的").any(lower::contains) ->
                item.coachLeads.direct
            listOf("energetic", "元気", "熱血", "情熱", "明る").any(lower::contains) ->
                item.coachLeads.energetic
            else -> item.coachLeads.friendly
        }
    }
}

object NewsConversation {
    const val HIDDEN_PREFIX = "__BYOKEY_NEWS_CONTEXT__"

    fun hiddenContext(item: DailyNewsItem): String {
        val sourceLines = item.sources.joinToString("\n") { "${it.title}: ${it.url}" }
        val summaryLabel = if (item.fallbackKind == NewsFallbackKind.RssHeadline) {
            "Source headline summary"
        } else {
            "Verified summary"
        }
        val caution = if (item.fallbackKind == NewsFallbackKind.RssHeadline) {
            "Only the headline and source were prepared automatically. Open the source before stating details beyond the summary."
        } else {
            "Treat these sources as the initial factual context."
        }
        return """
            $HIDDEN_PREFIX
            This chat started from a current-news story.
            Category: ${item.category.label}
            Headline: ${item.headline}
            $summaryLabel: ${item.summary}
            Suggested discussion question: ${item.question}
            Sources:
            $sourceLines
            $caution Search the web again when the learner
            asks for newer facts, results, prices, scores, or details not supported above.
        """.trimIndent()
    }

    fun visibleOpener(item: DailyNewsItem, coachInstructions: String): String {
        val sources = item.sources.joinToString("\n") { "${it.title}\n${it.url}" }
        return """
            ${CoachNewsTone.opener(item, coachInstructions)}

            ${item.summary}

            ${item.question}

            Sources:
            $sources
        """.trimIndent()
    }

    fun isHidden(message: ChatMessage): Boolean {
        val text = message.text.trimStart()
        return text.startsWith(HIDDEN_PREFIX) ||
            text.contains("Treat these sources as the initial factual context", ignoreCase = true) ||
            text.contains("Search the web again when the learner", ignoreCase = true)
    }

    fun hasNewsContext(messages: List<ChatMessage>): Boolean =
        messages.any(::isHidden)
}

object WebSearchPolicy {
    private val currentTopicTerms = listOf(
        "latest", "breaking", "news",
        "score", "result", "election", "price", "weather", "update",
        "最新情報", "ニュース", "速報", "試合結果", "スコア", "選挙結果",
        "価格", "株価", "天気", "どうなった", "何が起き"
    )

    fun shouldSearch(messages: List<ChatMessage>, latestUserMessage: String): Boolean {
        val lower = latestUserMessage.lowercase()
        if (currentTopicTerms.any(lower::contains)) return true
        if (!NewsConversation.hasNewsContext(messages)) return false
        val factualFollowUpTerms = listOf(
            "who", "what happened", "when", "where", "why", "how", "tell me more", "detail",
            "source", "after that", "then what", "誰", "何が", "いつ", "どこ", "なぜ", "どうして",
            "どうな", "詳しく", "出典", "その後", "他に"
        )
        return factualFollowUpTerms.any(lower::contains)
    }
}
