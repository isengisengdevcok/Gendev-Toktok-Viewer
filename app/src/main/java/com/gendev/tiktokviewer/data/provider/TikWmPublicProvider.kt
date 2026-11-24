package com.gendev.tiktokviewer.data.provider

import com.gendev.tiktokviewer.data.privacy.PrivacyPolicyEnforcer
import com.gendev.tiktokviewer.data.remote.TikTokLinkParser
import com.gendev.tiktokviewer.data.remote.dto.TikWmFeedListResponse
import com.gendev.tiktokviewer.data.remote.dto.TikWmSearchResponse
import com.gendev.tiktokviewer.data.remote.dto.TikWmSingleResponse
import com.gendev.tiktokviewer.data.remote.dto.TikWmUserPostsResponse
import com.gendev.tiktokviewer.data.remote.dto.TikWmVideoDto
import com.gendev.tiktokviewer.data.resilience.UpstreamBlockDetector
import com.gendev.tiktokviewer.domain.model.FeedPage
import com.gendev.tiktokviewer.domain.model.PlaybackMode
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import com.gendev.tiktokviewer.domain.provider.TikTokFeedProvider
import com.gendev.tiktokviewer.domain.provider.TikTokSearchProvider
import com.gendev.tiktokviewer.domain.provider.TikTokVideoProvider
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Public Content Adapter powered by keyless TikWM JSON endpoints (https://www.tikwm.com/api/).
 * Provides direct HTTPS streams for Media3 ExoPlayer alongside Official Embed v3 fallback URLs.
 */
class TikWmPublicProvider(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val baseUrl: String = "https://www.tikwm.com",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TikTokFeedProvider, TikTokSearchProvider, TikTokVideoProvider {

    override val providerId: String = "tikwm_public_api"
    override val sourceKind: VideoSourceKind = VideoSourceKind.TIKTOK_PUBLIC_ADAPTER

    private val normalizedBase = baseUrl.trimEnd('/')
    private val displayLabel = "Public Stream · TikWM"

    private val feedAdapter = moshi.adapter(TikWmFeedListResponse::class.java)
    private val searchAdapter = moshi.adapter(TikWmSearchResponse::class.java)
    private val userPostsAdapter = moshi.adapter(TikWmUserPostsResponse::class.java)
    private val singleAdapter = moshi.adapter(TikWmSingleResponse::class.java)
    private val requestMutex = Mutex()
    private var lastRequestAtMs: Long = 0L

    private val infiniteFeedTopics = listOf(
        "fyp",
        "viral",
        "trending",
        "lucu",
        "musik",
        "alam",
        "kuliner",
        "olahraga"
    )

    override suspend fun fetchTrendingFeed(cursor: String?): ProviderResult<FeedPage> = withContext(ioDispatcher) {
        val pageNum = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        if (pageNum > 0) {
            val topic = infiniteFeedTopics[(pageNum - 1) % infiniteFeedTopics.size]
            val searchCursor = ((pageNum - 1) / infiniteFeedTopics.size) * 15
            val pagedUrl = "$normalizedBase/api/feed/search?keywords=${encode(topic)}&count=15&cursor=$searchCursor&hd=1"
            return@withContext when (val raw = executeJsonGet(pagedUrl)) {
                is JsonFetchResult.Success -> {
                    val parsed = runCatching { searchAdapter.fromJson(raw.body) }.getOrNull()
                    val videos = parsed?.data?.videos.orEmpty().mapNotNull { it.toDomain() }
                    if (videos.isNotEmpty()) {
                        ProviderResult.Success(
                            data = FeedPage(
                                videos = videos,
                                nextCursor = (pageNum + 1).toString(),
                                sourceKind = sourceKind,
                                sourceEndpoint = displayLabel
                            ),
                            sourceKind = sourceKind,
                            endpoint = displayLabel
                        )
                    } else {
                        ProviderResult.ContentUnavailable("No more videos for page $pageNum")
                    }
                }
                is JsonFetchResult.Failure -> raw.result
            }
        }

        val region = "ID"
        val url = "$normalizedBase/api/feed/list?region=$region&count=15"
        when (val raw = executeJsonGet(url)) {
            is JsonFetchResult.Success -> {
                val parsed = runCatching { feedAdapter.fromJson(raw.body) }.getOrNull()
                if (parsed?.code == 0 && !parsed.data.isNullOrEmpty()) {
                    val videos = parsed.data.mapNotNull { it.toDomain() }
                    if (videos.isNotEmpty()) {
                        return@withContext ProviderResult.Success(
                            data = FeedPage(
                                videos = videos,
                                nextCursor = "1",
                                sourceKind = sourceKind,
                                sourceEndpoint = displayLabel
                            ),
                            sourceKind = sourceKind,
                            endpoint = displayLabel
                        )
                    }
                }
                // Fallback to search "fyp" if regional feed list is empty or throttled
                when (val fypFallback = searchVideos("fyp")) {
                    is ProviderResult.Success -> ProviderResult.Success(
                        data = FeedPage(
                            videos = fypFallback.data,
                            nextCursor = "1",
                            sourceKind = sourceKind,
                            sourceEndpoint = displayLabel
                        ),
                        sourceKind = sourceKind,
                        endpoint = displayLabel
                    )
                    is ProviderResult.RateLimited -> fypFallback
                    is ProviderResult.AccessRestricted -> fypFallback
                    is ProviderResult.NetworkError -> fypFallback
                    is ProviderResult.ContentUnavailable -> fypFallback
                }
            }
            is JsonFetchResult.Failure -> raw.result
        }
    }

    override suspend fun searchVideos(query: String): ProviderResult<List<PublicVideo>> = withContext(ioDispatcher) {
        val trimmed = query.trim()
        TikTokLinkParser.parse(trimmed)?.let {
            return@withContext when (val single = resolveVideoByUrlOrId(trimmed)) {
                is ProviderResult.Success -> ProviderResult.Success(listOf(single.data), single.sourceKind, single.endpoint)
                is ProviderResult.RateLimited -> single
                is ProviderResult.AccessRestricted -> single
                is ProviderResult.NetworkError -> single
                is ProviderResult.ContentUnavailable -> single
            }
        }
        if (trimmed.startsWith("@")) {
            return@withContext searchCreator(trimmed.removePrefix("@"))
        }

        val cleanQuery = trimmed.removePrefix("#").trim()
        if (cleanQuery.isBlank()) {
            return@withContext ProviderResult.ContentUnavailable("Enter a keyword, #hashtag, @creator, or TikTok link.")
        }

        val url = "$normalizedBase/api/feed/search?keywords=${encode(cleanQuery)}&count=15&cursor=0&hd=1"
        when (val raw = executeJsonGet(url)) {
            is JsonFetchResult.Success -> {
                val parsed = runCatching { searchAdapter.fromJson(raw.body) }.getOrNull()
                if (parsed?.code == -1 && parsed.msg?.contains("limit", ignoreCase = true) == true) {
                    return@withContext ProviderResult.RateLimited(displayLabel, 15L, parsed.msg)
                }
                val videos = parsed?.data?.videos.orEmpty().mapNotNull { it.toDomain() }
                if (videos.isNotEmpty()) {
                    ProviderResult.Success(videos, sourceKind, displayLabel)
                } else {
                    ProviderResult.ContentUnavailable("No public TikTok videos found for \"$trimmed\".")
                }
            }
            is JsonFetchResult.Failure -> raw.result
        }
    }

    override suspend fun searchHashtag(hashtag: String): ProviderResult<List<PublicVideo>> {
        val clean = hashtag.trim().removePrefix("#")
        return searchVideos(clean)
    }

    override suspend fun searchCreator(username: String): ProviderResult<List<PublicVideo>> = withContext(ioDispatcher) {
        val cleanHandle = username.trim().removePrefix("@").replace(Regex("[^A-Za-z0-9_.]"), "")
        if (cleanHandle.isBlank()) {
            return@withContext ProviderResult.ContentUnavailable("Creator handle cannot be empty.")
        }
        val url = "$normalizedBase/api/user/posts?unique_id=${encode("@$cleanHandle")}&count=15&cursor=0"
        when (val raw = executeJsonGet(url)) {
            is JsonFetchResult.Success -> {
                val parsed = runCatching { userPostsAdapter.fromJson(raw.body) }.getOrNull()
                val videos = parsed?.data?.videos.orEmpty().mapNotNull { it.toDomain(fallbackAuthor = cleanHandle) }
                if (videos.isNotEmpty()) {
                    ProviderResult.Success(videos, sourceKind, displayLabel)
                } else {
                    // Fallback to keyword search for the creator handle
                    searchVideos(cleanHandle)
                }
            }
            is JsonFetchResult.Failure -> raw.result
        }
    }

    override suspend fun resolveVideoByUrlOrId(urlOrId: String): ProviderResult<PublicVideo> = withContext(ioDispatcher) {
        val targetUrl = TikTokLinkParser.parse(urlOrId)?.canonicalUrl ?: urlOrId.trim()
        val url = "$normalizedBase/api/?url=${encode(targetUrl)}&hd=1"
        when (val raw = executeJsonGet(url)) {
            is JsonFetchResult.Success -> {
                val parsed = runCatching { singleAdapter.fromJson(raw.body) }.getOrNull()
                val video = parsed?.data?.toDomain()
                if (parsed?.code == 0 && video != null) {
                    ProviderResult.Success(video, sourceKind, displayLabel)
                } else {
                    ProviderResult.ContentUnavailable(parsed?.msg ?: "Video unavailable on $displayLabel.")
                }
            }
            is JsonFetchResult.Failure -> raw.result
        }
    }

    private fun TikWmVideoDto.toDomain(fallbackAuthor: String? = null): PublicVideo? {
        val resolvedId = (videoId ?: id)?.takeIf { it.isNotBlank() } ?: return null
        val handle = author?.uniqueId?.takeIf { it.isNotBlank() } ?: fallbackAuthor ?: "creator"
        val displayName = author?.nickname?.takeIf { it.isNotBlank() } ?: "@$handle"
        val caption = title?.trim().orEmpty().ifBlank { "Public video by @$handle" }

        val rawPlayUrl = hdPlay?.takeIf { it.isNotBlank() }
            ?: play?.takeIf { it.isNotBlank() }
            ?: wmPlay?.takeIf { it.isNotBlank() }
        val resolvedMedia = resolveHttpsUrl(rawPlayUrl)
        val resolvedCover = resolveHttpsUrl(originCover ?: cover)

        return PublicVideo(
            id = resolvedId,
            title = caption,
            authorHandle = handle,
            authorDisplayName = displayName,
            hashtags = TikTokLinkParser.extractHashtags(caption),
            thumbnailUrl = resolvedCover,
            directMediaUrl = resolvedMedia,
            embedUrl = "https://www.tiktok.com/embed/v3/$resolvedId",
            canonicalUrl = "https://www.tiktok.com/@$handle/video/$resolvedId",
            sourceKind = sourceKind,
            sourceLabel = displayLabel,
            playbackMode = if (resolvedMedia != null) PlaybackMode.EXO_DIRECT_STREAM else PlaybackMode.ISOLATED_EMBED_WEBVIEW,
            durationSeconds = duration
        )
    }

    private fun resolveHttpsUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val full = when {
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "$normalizedBase$trimmed"
            else -> return null
        }
        return if (PrivacyPolicyEnforcer.isSafeHttpsUrl(full)) full else null
    }

    private sealed class JsonFetchResult {
        data class Success(val body: String) : JsonFetchResult()
        data class Failure(val result: ProviderResult<Nothing>) : JsonFetchResult()
    }

    private suspend fun executeJsonGet(url: String): JsonFetchResult {
        if (!PrivacyPolicyEnforcer.isSafeHttpsUrl(url)) {
            return JsonFetchResult.Failure(
                ProviderResult.NetworkError(displayLabel, "HTTPS required", "HTTPS_REQUIRED")
            )
        }
        return requestMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastRequestAtMs
            if (lastRequestAtMs > 0L && elapsed in 0..1_099L) {
                delay(1_100L - elapsed)
            }
            lastRequestAtMs = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val restriction = UpstreamBlockDetector.detectRestriction(
                    statusCode = response.code,
                    bodySnippet = body.take(2048),
                    endpoint = displayLabel,
                    retryAfterHeader = response.header("Retry-After")
                )
                if (restriction != null) {
                    return JsonFetchResult.Failure(restriction)
                }
                if (!response.isSuccessful) {
                    return JsonFetchResult.Failure(
                        ProviderResult.NetworkError(
                            endpoint = displayLabel,
                            message = "Endpoint returned HTTP ${response.code}",
                            causeType = "HTTP_${response.code}"
                        )
                    )
                }
                JsonFetchResult.Success(body)
            }
            } catch (t: Throwable) {
                JsonFetchResult.Failure(
                    ProviderResult.NetworkError(
                        endpoint = displayLabel,
                        message = "Connection error on $displayLabel (${t.javaClass.simpleName})",
                        causeType = t.javaClass.simpleName
                    )
                )
            }
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
