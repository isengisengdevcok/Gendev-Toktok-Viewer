package com.gendev.tiktokviewer.data.provider

import com.gendev.tiktokviewer.data.privacy.PrivacyPolicyEnforcer
import com.gendev.tiktokviewer.data.remote.TikTokLinkParser
import com.gendev.tiktokviewer.data.remote.TikTokOEmbedService
import com.gendev.tiktokviewer.data.resilience.UpstreamBlockDetector
import com.gendev.tiktokviewer.domain.model.FeedPage
import com.gendev.tiktokviewer.domain.model.PlaybackMode
import com.gendev.tiktokviewer.domain.model.PlaybackTarget
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import com.gendev.tiktokviewer.domain.provider.TikTokFeedProvider
import com.gendev.tiktokviewer.domain.provider.TikTokPlaybackProvider
import com.gendev.tiktokviewer.domain.provider.TikTokSearchProvider
import com.gendev.tiktokviewer.domain.provider.TikTokVideoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Official TikTok oEmbed + Official Embed v2 provider.
 * Resolves public video metadata using https://www.tiktok.com/oembed without cookies or login.
 */
class TikTokOEmbedVideoProvider(
    private val oEmbedService: TikTokOEmbedService,
    private val okHttpClient: OkHttpClient
) : TikTokVideoProvider {

    override val providerId: String = "tiktok_oembed"
    override val sourceKind: VideoSourceKind = VideoSourceKind.TIKTOK_OEMBED

    override suspend fun resolveVideoByUrlOrId(urlOrId: String): ProviderResult<PublicVideo> =
        withContext(Dispatchers.IO) {
            val resolvedInput = if (TikTokLinkParser.isShortTikTokUrl(urlOrId)) {
                resolveShortRedirect(urlOrId) ?: urlOrId
            } else {
                urlOrId
            }

            val parsed = TikTokLinkParser.parse(resolvedInput)
                ?: return@withContext ProviderResult.ContentUnavailable(
                    "Invalid TikTok link. Paste a public video link such as https://www.tiktok.com/@creator/video/1234567890"
                )

            try {
                val response = oEmbedService.getOEmbed(parsed.canonicalUrl)
                val restriction = UpstreamBlockDetector.detectRestriction(
                    statusCode = response.code(),
                    bodySnippet = response.errorBody()?.string()?.take(2048),
                    endpoint = "TikTok Official oEmbed",
                    retryAfterHeader = response.headers()["Retry-After"]
                )
                if (restriction != null) {
                    return@withContext restriction
                }

                val dto = response.body()
                if (response.isSuccessful && dto != null) {
                    val handle = dto.authorUniqueId
                        ?.takeIf { it.isNotBlank() }
                        ?: parsed.authorHandle
                        ?: "creator"
                    val videoId = dto.embedProductId
                        ?.takeIf { it.isNotBlank() }
                        ?: parsed.videoId
                    val title = dto.title?.takeIf { it.isNotBlank() } ?: "Public TikTok video"
                    return@withContext ProviderResult.Success(
                        data = PublicVideo(
                            id = videoId,
                            title = title,
                            authorHandle = handle,
                            authorDisplayName = dto.authorName?.takeIf { it.isNotBlank() } ?: "@$handle",
                            hashtags = TikTokLinkParser.extractHashtags(title),
                            thumbnailUrl = dto.thumbnailUrl?.takeIf { PrivacyPolicyEnforcer.isSafeHttpsUrl(it) },
                            directMediaUrl = null,
                            embedUrl = "https://www.tiktok.com/embed/v2/$videoId",
                            canonicalUrl = "https://www.tiktok.com/@$handle/video/$videoId",
                            sourceKind = VideoSourceKind.TIKTOK_OEMBED,
                            sourceLabel = "TikTok Official oEmbed + Embed v2",
                            playbackMode = PlaybackMode.ISOLATED_EMBED_WEBVIEW
                        ),
                        sourceKind = VideoSourceKind.TIKTOK_OEMBED,
                        endpoint = "TikTok Official oEmbed"
                    )
                }

                // If oEmbed returns 400/404 for an ID-only canonical URL, allow Official Embed v2 attempt
                ProviderResult.Success(
                    data = PublicVideo(
                        id = parsed.videoId,
                        title = "Public TikTok Video #${parsed.videoId.takeLast(6)}",
                        authorHandle = parsed.authorHandle ?: "public",
                        authorDisplayName = "@${parsed.authorHandle ?: "public"}",
                        hashtags = emptyList(),
                        thumbnailUrl = null,
                        directMediaUrl = null,
                        embedUrl = parsed.embedUrl,
                        canonicalUrl = parsed.canonicalUrl,
                        sourceKind = VideoSourceKind.TIKTOK_EMBED_PLAYER,
                        sourceLabel = "TikTok Official Embed v2",
                        playbackMode = PlaybackMode.ISOLATED_EMBED_WEBVIEW
                    ),
                    sourceKind = VideoSourceKind.TIKTOK_EMBED_PLAYER,
                    endpoint = "TikTok Official Embed v2"
                )
            } catch (t: Throwable) {
                ProviderResult.NetworkError(
                    endpoint = "TikTok Official oEmbed",
                    message = "Failed to query oEmbed (${t.javaClass.simpleName})",
                    causeType = t.javaClass.simpleName
                )
            }
        }

    private fun resolveShortRedirect(shortUrl: String): String? {
        if (!PrivacyPolicyEnforcer.isSafeHttpsUrl(shortUrl)) return null
        return runCatching {
            val request = Request.Builder().url(shortUrl).head().build()
            okHttpClient.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        }.getOrNull()
    }
}

/**
 * Adapter over TikTok's official public web & oEmbed surfaces.
 * Serves as an anonymous public content fallback when third-party ProxiTok instances are unavailable or rate-limited.
 */
class TikTokPublicContentAdapter(
    private val oEmbedVideoProvider: TikTokOEmbedVideoProvider,
    private val okHttpClient: OkHttpClient
) : TikTokFeedProvider, TikTokSearchProvider {

    override val providerId: String = "tiktok_public_adapter"
    override val sourceKind: VideoSourceKind = VideoSourceKind.TIKTOK_PUBLIC_ADAPTER

    private val verifiedPublicChannels = listOf(
        "https://www.tiktok.com/@tiktok/video/7106594312292453675",
        "https://www.tiktok.com/@nasa/video/7343728212422282538",
        "https://www.tiktok.com/@natgeo/video/7324461580063771946",
        "https://www.tiktok.com/@espn/video/7353029442094468398",
        "https://www.tiktok.com/@redbull/video/7336966634199043360"
    )

    override suspend fun fetchTrendingFeed(cursor: String?): ProviderResult<FeedPage> = coroutineScope {
        val resolved = verifiedPublicChannels.map { url ->
            async {
                when (val res = oEmbedVideoProvider.resolveVideoByUrlOrId(url)) {
                    is ProviderResult.Success -> res.data
                    else -> null
                }
            }
        }.awaitAll().filterNotNull()

        if (resolved.isNotEmpty()) {
            ProviderResult.Success(
                data = FeedPage(
                    videos = resolved,
                    nextCursor = null,
                    sourceKind = VideoSourceKind.TIKTOK_OEMBED,
                    sourceEndpoint = "TikTok Official oEmbed + Embed v2",
                    noticeMessage = "Active via Official TikTok Embed v2 (ProxiTok instances rotated)"
                ),
                sourceKind = VideoSourceKind.TIKTOK_OEMBED,
                endpoint = "TikTok Official oEmbed"
            )
        } else {
            ProviderResult.NetworkError(
                endpoint = "TikTok Public Adapter",
                message = "Unable to reach TikTok public endpoints. Check internet connectivity.",
                causeType = "NETWORK_UNREACHABLE"
            )
        }
    }

    override suspend fun searchVideos(query: String): ProviderResult<List<PublicVideo>> {
        val trimmed = query.trim()
        TikTokLinkParser.parse(trimmed)?.let {
            return when (val single = oEmbedVideoProvider.resolveVideoByUrlOrId(trimmed)) {
                is ProviderResult.Success -> ProviderResult.Success(
                    listOf(single.data),
                    single.sourceKind,
                    single.endpoint
                )
                is ProviderResult.RateLimited -> single
                is ProviderResult.AccessRestricted -> single
                is ProviderResult.NetworkError -> single
                is ProviderResult.ContentUnavailable -> single
            }
        }
        if (TikTokLinkParser.isShortTikTokUrl(trimmed)) {
            return when (val single = oEmbedVideoProvider.resolveVideoByUrlOrId(trimmed)) {
                is ProviderResult.Success -> ProviderResult.Success(
                    listOf(single.data),
                    single.sourceKind,
                    single.endpoint
                )
                is ProviderResult.RateLimited -> single
                is ProviderResult.AccessRestricted -> single
                is ProviderResult.NetworkError -> single
                is ProviderResult.ContentUnavailable -> single
            }
        }
        return if (trimmed.startsWith("@")) {
            searchCreator(trimmed.removePrefix("@"))
        } else {
            searchHashtag(trimmed.removePrefix("#"))
        }
    }

    override suspend fun searchHashtag(hashtag: String): ProviderResult<List<PublicVideo>> =
        withContext(Dispatchers.IO) {
            val clean = hashtag.trim().removePrefix("#").replace(Regex("[^A-Za-z0-9_]"), "")
            if (clean.isBlank()) {
                return@withContext ProviderResult.ContentUnavailable("Enter a valid public #hashtag.")
            }
            val url = "https://www.tiktok.com/tag/${URLEncoder.encode(clean, "UTF-8")}"
            scrapeAndEnrichPublicPage(url, "TikTok Public Tag")
        }

    override suspend fun searchCreator(username: String): ProviderResult<List<PublicVideo>> =
        withContext(Dispatchers.IO) {
            val clean = username.trim().removePrefix("@").replace(Regex("[^A-Za-z0-9_.]"), "")
            if (clean.isBlank()) {
                return@withContext ProviderResult.ContentUnavailable("Enter a valid public @creator handle.")
            }
            val url = "https://www.tiktok.com/@${URLEncoder.encode(clean, "UTF-8")}"
            scrapeAndEnrichPublicPage(url, "TikTok Public Creator")
        }

    private suspend fun scrapeAndEnrichPublicPage(
        pageUrl: String,
        endpointLabel: String
    ): ProviderResult<List<PublicVideo>> = coroutineScope {
        try {
            val request = Request.Builder().url(pageUrl).get().build()
            val html = okHttpClient.newCall(request).execute().use { response ->
                val snippet = response.body?.string().orEmpty()
                val restriction = UpstreamBlockDetector.detectRestriction(
                    statusCode = response.code,
                    bodySnippet = snippet.take(4096),
                    endpoint = endpointLabel,
                    retryAfterHeader = response.header("Retry-After")
                )
                if (restriction != null) return@coroutineScope restriction
                if (!response.isSuccessful) {
                    return@coroutineScope ProviderResult.NetworkError(
                        endpointLabel,
                        "Upstream returned HTTP ${response.code}",
                        "HTTP_${response.code}"
                    )
                }
                snippet
            }

            val videoUrls = Regex("""https://www\.tiktok\.com/@[A-Za-z0-9_.]+/video/\d+""")
                .findAll(html)
                .map { it.value }
                .distinct()
                .take(8)
                .toList()

            if (videoUrls.isEmpty()) {
                return@coroutineScope ProviderResult.ContentUnavailable(
                    "No directly indexed public clips returned by $endpointLabel. Try pasting a direct TikTok video link."
                )
            }

            val enriched = videoUrls.map { url ->
                async {
                    when (val r = oEmbedVideoProvider.resolveVideoByUrlOrId(url)) {
                        is ProviderResult.Success -> r.data
                        else -> null
                    }
                }
            }.awaitAll().filterNotNull()

            if (enriched.isNotEmpty()) {
                ProviderResult.Success(enriched, VideoSourceKind.TIKTOK_PUBLIC_ADAPTER, endpointLabel)
            } else {
                ProviderResult.ContentUnavailable("Unable to resolve public metadata for $endpointLabel.")
            }
        } catch (t: Throwable) {
            ProviderResult.NetworkError(
                endpoint = endpointLabel,
                message = "Network error querying $endpointLabel (${t.javaClass.simpleName})",
                causeType = t.javaClass.simpleName
            )
        }
    }
}

class TikTokPlaybackProviderImpl : TikTokPlaybackProvider {
    override suspend fun resolvePlaybackTarget(video: PublicVideo): ProviderResult<PlaybackTarget> {
        val directUrl = video.directMediaUrl
        if (!directUrl.isNullOrBlank() && PrivacyPolicyEnforcer.isSafeHttpsUrl(directUrl)) {
            return ProviderResult.Success(
                data = PlaybackTarget(
                    videoId = video.id,
                    mode = PlaybackMode.EXO_DIRECT_STREAM,
                    streamUrl = directUrl,
                    sourceLabel = video.sourceLabel
                ),
                sourceKind = video.sourceKind,
                endpoint = video.sourceLabel
            )
        }

        if (PrivacyPolicyEnforcer.isSafeHttpsUrl(video.embedUrl)) {
            return ProviderResult.Success(
                data = PlaybackTarget(
                    videoId = video.id,
                    mode = PlaybackMode.ISOLATED_EMBED_WEBVIEW,
                    streamUrl = video.embedUrl,
                    sourceLabel = "TikTok Official Embed v2"
                ),
                sourceKind = VideoSourceKind.TIKTOK_EMBED_PLAYER,
                endpoint = "TikTok Official Embed v2"
            )
        }

        return ProviderResult.ContentUnavailable("No authorized public playback target available for video ${video.id}.")
    }
}
