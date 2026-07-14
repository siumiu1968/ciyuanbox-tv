package com.jing.sakura.repo

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.extend.TraditionalChinese
import com.jing.sakura.extend.getDocument
import com.jing.sakura.extend.getHtml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Calendar
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class CycaniSource(private val okHttpClient: OkHttpClient) : AnimationSource {

    private var navCache: List<NavItem>? = null

    override val sourceId: String
        get() = SOURCE_ID

    override val name: String
        get() = trad("次元城动画")

    override val pageSize: Int
        get() = 20

    override fun supportSearch(): Boolean = true

    override fun supportSearchByCategory(): Boolean = true

    override fun supportTimeline(): Boolean = true

    override suspend fun fetchHomePageData(): HomePageData {
        val timelineGroups = fetchUpdateTimeline().timeline.mapNotNull { (name, animeList) ->
            animeList.takeIf { it.isNotEmpty() }?.let { NamedValue(name, it) }
        }

        val groups = runCatching { fetchOfficialHomeGroups() }.getOrDefault(emptyList()).ifEmpty {
            val navItems = fetchNavItems()
            val preferredItems = navItems.filter { it.typeId in setOf("20", "21") }
                .ifEmpty { navItems.take(2) }
            preferredItems.mapNotNull { navItem ->
                val page = fetchQueryPage(
                    page = 1,
                    params = linkedMapOf("type_id" to navItem.typeId)
                )
                if (page.animeList.isEmpty()) {
                    null
                } else {
                    NamedValue(trad(navItem.typeName), page.animeList)
                }
            }
        }

        val homeGroups = timelineGroups + groups

        if (homeGroups.isEmpty()) {
            throw RuntimeException(trad("次元城首页未解析到内容"))
        }

        return HomePageData(sourceId = sourceId, seriesList = homeGroups)
    }

    override suspend fun fetchDetailPage(animeId: String): AnimeDetailPageData = coroutineScope {
        val cachedSynopsis = async {
            try {
                withTimeoutOrNull(CACHED_SYNOPSIS_TIMEOUT_MS) {
                    fetchCachedSynopsis(animeId)
                }.orEmpty()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ""
            }
        }
        val detail = apiGetDataObject("$API_BASE_URL/video/info/$animeId")
        val title = localizeText(detail.string("vod_name")).ifBlank {
            throw RuntimeException(trad("未找到番剧标题"))
        }
        val originalDescription = detail.string("vod_content")
            .ifBlank { detail.string("vod_blurb") }
            .let { Jsoup.parse(it).text() }
        val imageUrl = detail.string("vod_pic").ifBlank {
            throw RuntimeException(trad("未找到番剧封面"))
        }

        val playFromArray = detail.array("vod_play_from")
        val playLists = mutableListOf<AnimePlayList>()
        for (item in playFromArray.asJsonObjects()) {
            val code = item.string("code")
            val lineName = localizeText(item.string("name"))
                .ifBlank { code }
                .normalizePlayLineName()
            if (code.isBlank()) continue

            val episodesJson = apiGetDataArray(
                "$API_BASE_URL/video/play_url",
                linkedMapOf(
                    "id" to animeId,
                    "from" to code
                )
            )

            val episodes = episodesJson.asJsonObjects().mapIndexedNotNull { index, episode ->
                val episodeName = localizeText(episode.string("name")).ifBlank { "第${index + 1}集" }
                val url = episode.string("url")
                if (url.isBlank()) {
                    null
                } else {
                    AnimePlayListEpisode(
                        episode = episodeName,
                        episodeId = encodeEpisodePayload(
                            EpisodePayload(
                                url = url,
                                needParse = episode.boolean("needParse")
                            )
                        )
                    )
                }
            }

            if (episodes.isNotEmpty()) {
                playLists += AnimePlayList(
                    name = lineName,
                    episodeList = episodes,
                    defaultPlayList = playLists.isEmpty()
                )
            }
        }

        if (playLists.isEmpty()) {
            throw RuntimeException(trad("未找到播放列表"))
        }

        val related = apiGetDataArray(
            "$API_BASE_URL/video/prefer",
            linkedMapOf("vod_id" to animeId)
        ).asJsonObjects().map { parseAnimeItem(it) }

        val infoList = buildList {
            addInfo("地區", trad(detail.string("vod_area")))
            addInfo("年份", trad(detail.string("vod_year")))
            addInfo("類型", trad(detail.string("vod_class")))
            addInfo("聲優／演員", localizeText(detail.string("vod_actor")))
            addInfo("導演", localizeText(detail.string("vod_director")))
            addInfo("編劇", localizeText(detail.string("vod_writer")))
            detail.string("vod_score").takeIf { it.isNotBlank() }?.let { add("評分：${trad(it)}") }
        }
        val description = cachedSynopsis.await().ifBlank { localizeText(originalDescription) }

        AnimeDetailPageData(
            animeId = animeId,
            animeName = title,
            description = description,
            imageUrl = imageUrl,
            playLists = playLists,
            otherAnimeList = related.filter { it.id != animeId },
            infoList = infoList
        )
    }

    override suspend fun searchAnimation(keyword: String, page: Int): AnimePageData {
        val root = apiGetRootObject(
            "$API_BASE_URL/video/search",
            linkedMapOf(
                "text" to keyword,
                "pg" to page.toString(),
                "limit" to pageSize.toString()
            )
        )
        ensureSuccess(root)
        val total = root.int("total")
        val items = root.array("data").asJsonObjects().map { parseAnimeItem(it) }
        return AnimePageData(
            page = page,
            hasNextPage = total > page * pageSize,
            animeList = items
        )
    }

    override suspend fun fetchUpdateTimeline(): UpdateTimeLine = coroutineScope {
        val labels = listOf(
            "週一",
            "週二",
            "週三",
            "週四",
            "週五",
            "週六",
            "週日"
        )
        val timeline = (1..7).map { day ->
            async {
                labels[day - 1] to apiGetDataArray(
                    "$API_BASE_URL/video/weekday_list",
                    linkedMapOf("day" to day.toString())
                ).asJsonObjects().map { parseAnimeItem(it) }
            }
        }.awaitAll()
        UpdateTimeLine(
            current = Calendar.getInstance().run {
                val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                (dayOfWeek + 5) % 7
            },
            timeline = timeline
        )
    }

    override suspend fun fetchVideoUrl(
        animeId: String,
        episodeId: String
    ): Resource<AnimationSource.VideoUrlResult> {
        return try {
            val payload = decodeEpisodePayload(episodeId)
            val finalUrl = if (payload.needParse) {
                val parsed = apiGetRootObject(payload.url)
                ensureSuccess(parsed)
                parsed.string("url").ifBlank { throw RuntimeException(trad("获取到的视频链接为空")) }
            } else {
                payload.url
            }

            Resource.Success(
                AnimationSource.VideoUrlResult(
                    url = finalUrl,
                    headers = linkedMapOf(
                        "User-Agent" to DESKTOP_USER_AGENT,
                        "Accept" to "*/*"
                    )
                )
            )
        } catch (e: Exception) {
            Resource.Error(e.message ?: trad("加载视频失败"))
        }
    }

    override suspend fun getVideoCategories(): List<VideoCategoryGroup> {
        val navItems = fetchNavItems()
        if (navItems.isEmpty()) return emptyList()

        val typeCategories = navItems.map {
            VideoCategory(label = it.typeName, value = it.typeId)
        }

        return listOf(
            VideoCategoryGroup.NormalCategoryGroup(
                name = "番劇類型",
                key = "type_id",
                defaultValue = typeCategories.first().value,
                categories = typeCategories
            ),
            VideoCategoryGroup.DynamicCategoryGroup(
                name = "題材",
                key = "class",
                dependsOnKey = listOf("type_id")
            ) { selected ->
                val navItem = navItems.firstOrNull { it.typeId == selected.valueOf("type_id") } ?: navItems.first()
                VideoCategoryGroup.NormalCategoryGroup(
                    name = "題材",
                    key = "class",
                    defaultValue = "",
                    categories = listOf(VideoCategory("全部", "")) +
                        navItem.classes.map { VideoCategory(trad(it), it) }
                )
            },
            VideoCategoryGroup.DynamicCategoryGroup(
                name = "年份",
                key = "year",
                dependsOnKey = listOf("type_id")
            ) { selected ->
                val navItem = navItems.firstOrNull { it.typeId == selected.valueOf("type_id") } ?: navItems.first()
                VideoCategoryGroup.NormalCategoryGroup(
                    name = "年份",
                    key = "year",
                    defaultValue = "",
                    categories = listOf(VideoCategory("全部", "")) +
                        navItem.years.map { VideoCategory(trad(it), it) }
                )
            }
        )
    }

    override suspend fun queryByCategory(
        categories: List<NamedValue<String>>,
        page: Int
    ): AnimePageData {
        val params = linkedMapOf<String, String>()
        categories.forEach { selected ->
            if (selected.value.isNotBlank()) {
                params[selected.name] = selected.value
            }
        }
        if (!params.containsKey("type_id")) {
            fetchNavItems().firstOrNull()?.let { params["type_id"] = it.typeId }
        }
        return fetchQueryPage(page = page, params = params)
    }

    private suspend fun fetchOfficialHomeGroups(): List<NamedValue<List<AnimeData>>> {
        val doc = okHttpClient.getDocument(WEB_BASE_URL) {
            header("User-Agent", DESKTOP_USER_AGENT)
            header("Accept-Language", "zh-CN,zh;q=0.9")
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
        val preferredSections = linkedMapOf(
            "TV番组" to trad("TV 番組"),
            "剧场番组" to trad("劇場番組")
        )
        return doc.select("h4.title-h").mapNotNull { titleElement ->
            val normalizedTitle = normalizeSectionName(titleElement.text())
            val displayTitle = preferredSections[normalizedTitle] ?: return@mapNotNull null
            val titleRow = titleElement.parents().firstOrNull { "title" in it.classNames() }
                ?: return@mapNotNull null
            var contentRow = titleRow.nextElementSibling()
            while (contentRow != null && "public-r" !in contentRow.classNames()) {
                contentRow = contentRow.nextElementSibling()
            }
            val animeList = contentRow
                ?.select("div.public-list-box.public-pic-b")
                ?.mapNotNull { parseOfficialHomeItem(it, displayTitle) }
                .orEmpty()
            animeList.takeIf { it.isNotEmpty() }?.let {
                NamedValue(displayTitle, it)
            }
        }
    }

    private suspend fun fetchQueryPage(
        page: Int,
        params: LinkedHashMap<String, String>
    ): AnimePageData {
        val root = apiGetRootObject(
            "$API_BASE_URL/video/query",
            LinkedHashMap<String, String>().apply {
                putAll(params)
                put("page", page.toString())
                put("limit", pageSize.toString())
            }
        )
        ensureSuccess(root)
        val total = root.int("total")
        val items = root.array("data").asJsonObjects().map { parseAnimeItem(it) }
        return AnimePageData(
            page = page,
            hasNextPage = total > page * pageSize,
            animeList = items
        )
    }

    private suspend fun fetchNavItems(): List<NavItem> {
        navCache?.let { return it }

        val data = apiGetDataArray("$API_BASE_URL/index/nav")
        val items = data.asJsonObjects().mapNotNull { item ->
            val typeId = item.string("type_id")
            val typeName = item.string("type_name")
            if (typeId.isBlank() || typeName.isBlank()) {
                null
            } else {
                val typeExtend = item.objectOrNull("type_extend")
                NavItem(
                    typeId = typeId,
                    typeName = trad(typeName),
                    classes = splitCsv(typeExtend?.string("class").orEmpty()).map(::trad),
                    years = splitCsv(typeExtend?.string("year").orEmpty()).map(::trad)
                )
            }
        }

        navCache = items
        return items
    }

    private fun parseAnimeItem(item: JsonObject): AnimeData {
        val animeId = item.string("vod_id")
        val title = trad(item.string("name").ifBlank { item.string("vod_name") })
        return AnimeData(
            id = animeId,
            url = "$API_BASE_URL/video/info/$animeId",
            title = title,
            currentEpisode = trad(item.string("remarks").ifBlank { item.string("vod_remarks") }),
            imageUrl = item.string("pic").ifBlank { item.string("vod_pic") },
            description = trad(item.string("blurb").ifBlank { item.string("vod_content") }),
            tags = trad(item.string("class").ifBlank { item.string("vod_class") }),
            sourceId = sourceId,
            year = trad(item.string("year").ifBlank { item.string("vod_year") })
        )
    }

    private fun parseOfficialHomeItem(card: Element, sectionTitle: String): AnimeData? {
        val link = card.selectFirst("a.public-list-exp") ?: return null
        val href = link.attr("href")
        val animeId = extractAnimeId(href)
        val image = card.selectFirst("img")
        val title = trad(
            link.attr("title")
                .ifBlank { card.selectFirst("a.time-title")?.text().orEmpty() }
        )
        if (animeId.isBlank() || title.isBlank()) return null
        return AnimeData(
            id = animeId,
            url = "$API_BASE_URL/video/info/$animeId",
            title = title,
            currentEpisode = trad(
                card.selectFirst(".public-list-subtitle")?.text().orEmpty()
            ),
            imageUrl = image?.attr("data-src").orEmpty().ifBlank { image?.attr("src").orEmpty() },
            description = "",
            tags = sectionTitle,
            sourceId = sourceId
        )
    }

    private suspend fun apiGetRootObject(
        url: String,
        params: Map<String, String> = emptyMap()
    ): JsonObject {
        val targetUrl = buildUrl(url, params)
        val body = okHttpClient.getHtml(targetUrl) {
            defaultHeaders()
        }
        return JsonParser.parseString(body).asJsonObject
    }

    private suspend fun apiGetDataObject(
        url: String,
        params: Map<String, String> = emptyMap()
    ): JsonObject {
        val root = apiGetRootObject(url, params)
        ensureSuccess(root)
        return root.objectOrNull("data") ?: throw RuntimeException(trad("响应中缺少 data"))
    }

    private suspend fun apiGetDataArray(
        url: String,
        params: Map<String, String> = emptyMap()
    ): JsonArray {
        val root = apiGetRootObject(url, params)
        ensureSuccess(root)
        return root.array("data")
    }

    private fun ensureSuccess(root: JsonObject) {
        if (root.int("code") != 200) {
            throw RuntimeException(trad(root.string("msg").ifBlank { "請求失敗" }))
        }
    }

    private fun buildUrl(url: String, params: Map<String, String>): String {
        if (params.isEmpty()) return url
        val builder = url.toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun JsonArray.asJsonObjects(): List<JsonObject> =
        mapNotNull { element ->
            if (element.isJsonObject) element.asJsonObject else null
        }

    private fun JsonObject.string(name: String): String {
        val element = get(name) ?: return ""
        if (element.isJsonNull) return ""
        return element.asString
    }

    private fun JsonObject.int(name: String): Int {
        val element = get(name) ?: return 0
        if (element.isJsonNull) return 0
        return runCatching { element.asInt }
            .getOrElse { element.asString.toIntOrNull() ?: 0 }
    }

    private fun JsonObject.boolean(name: String): Boolean {
        val element = get(name) ?: return false
        if (element.isJsonNull) return false
        return when {
            element.isJsonPrimitive && element.asJsonPrimitive.isBoolean -> element.asBoolean
            else -> element.asString.equals("true", ignoreCase = true)
        }
    }

    private fun JsonObject.array(name: String): JsonArray {
        val element = get(name) ?: return JsonArray()
        if (!element.isJsonArray) return JsonArray()
        return element.asJsonArray
    }

    private fun JsonObject.objectOrNull(name: String): JsonObject? {
        val element = get(name) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun splitCsv(raw: String): List<String> =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "分类资源不代表全部资源" }

    private fun MutableList<String>.addInfo(label: String, value: String) {
        if (value.isNotBlank()) {
            add("$label：$value")
        }
    }

    private fun List<NamedValue<String>>.valueOf(key: String): String? =
        firstOrNull { it.name == key }?.value

    private fun normalizeSectionName(name: String): String =
        name.replace("\\s+".toRegex(), "")

    private fun extractAnimeId(href: String): String =
        "/bangumi/(\\d+)\\.html".toRegex().find(href)?.groupValues?.getOrNull(1).orEmpty()

    private fun encodeEpisodePayload(payload: EpisodePayload): String {
        val raw = JsonObject().apply {
            addProperty("u", payload.url)
            addProperty("p", payload.needParse)
        }.toString()
        return PAYLOAD_PREFIX + Base64.encodeToString(
            raw.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
    }

    private fun decodeEpisodePayload(episodeId: String): EpisodePayload {
        if (!episodeId.startsWith(PAYLOAD_PREFIX)) {
            return EpisodePayload(url = episodeId, needParse = false)
        }
        val raw = String(
            Base64.decode(episodeId.removePrefix(PAYLOAD_PREFIX), Base64.DEFAULT),
            Charsets.UTF_8
        )
        val json = JsonParser.parseString(raw).asJsonObject
        return EpisodePayload(
            url = json.string("u"),
            needParse = json.boolean("p")
        )
    }

    private suspend fun fetchCachedSynopsis(animeId: String): String {
        val url = AULAMA_API_BASE_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment("synopsis")
            .addPathSegment(animeId)
            .build()
            .toString()
        val root = JsonParser.parseString(
            okHttpClient.getHtml(url) {
                header("User-Agent", DESKTOP_USER_AGENT)
                header("Accept-Language", "zh-TW,zh;q=0.9")
                header("Accept", "application/json")
            }
        ).asJsonObject
        return if (root.boolean("ok")) root.string("summaryZhHant") else ""
    }

    private fun localizeText(text: String): String = trad(text.trim())

    private fun String.normalizePlayLineName(): String {
        val value = trim()
        return if (
            value.equals("CYC", ignoreCase = true) ||
            value.startsWith("CYC_", ignoreCase = true) ||
            value.startsWith("CYC-", ignoreCase = true)
        ) {
            "主線路"
        } else {
            value
        }
    }

    private data class NavItem(
        val typeId: String,
        val typeName: String,
        val classes: List<String>,
        val years: List<String>
    )

    private data class EpisodePayload(
        val url: String,
        val needParse: Boolean
    )

    private val defaultHeaders: okhttp3.Request.Builder.() -> Unit = {
        header("User-Agent", DESKTOP_USER_AGENT)
        header("Accept-Language", "zh-CN,zh;q=0.9")
        header("Accept", "application/json,text/plain,*/*")
    }

    companion object {
        const val SOURCE_ID = "cycani"
        private const val API_BASE_URL = "https://pc.cycback.org"
        private const val AULAMA_API_BASE_URL = "https://aulama.org/anime/api"
        private const val WEB_BASE_URL = "https://www.cycani.org/"
        private const val CACHED_SYNOPSIS_TIMEOUT_MS = 800L
        private const val PAYLOAD_PREFIX = "cycapi:"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) cyc-desktop/1.0.8 Chrome/128.0.6613.36 Electron/32.0.1 Safari/537.36"
    }

    private fun trad(text: String): String = TraditionalChinese.convert(text)
}
