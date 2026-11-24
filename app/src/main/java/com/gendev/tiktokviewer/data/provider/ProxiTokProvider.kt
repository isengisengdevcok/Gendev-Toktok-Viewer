package com.gendev.tiktokviewer.data.provider

import com.gendev.tiktokviewer.data.privacy.PrivacyPolicyEnforcer
import com.gendev.tiktokviewer.data.remote.ProxiTokParser
import com.gendev.tiktokviewer.data.remote.TikTokLinkParser
import com.gendev.tiktokviewer.data.resilience.UpstreamBlockDetector
import com.gendev.tiktokviewer.domain.model.FeedPage
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import com.gendev.tiktokviewer.domain.provider.TikTokFeedProvider
import com.gendev.tiktokviewer.domain.provider.TikTokSearchProvider
import com.gendev.tiktokviewer.domain.provider.TikTokVideoProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class ProxiTokProvider(
    override val providerId: String,
    val baseUrl: String,
    val displayLabel: String,
    override val sourceKind: VideoSourceKind,
    private val okHttpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TikTokFeedProvider, TikTokSearchProvider, TikTokVideoProvider {

    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    override suspend fun fetchTrendingFeed(cursor: String?): ProviderResult<FeedPage> {
        val rssUrl = if (cursor.isNullOrBlank()) {
            "$normalizedBaseUrl/trending?format=rss"
        } else {
            "$normalizedBaseUrl/trending?format=rss&cursor=${encode(cursor)}"
        }
        return when (val result = fetchVideosFromRoute(rssUrl, "$normalizedBaseUrl/trending")) {
            is ProviderResult.Success -> ProviderResult.Success(
                data = FeedPage(
                    videos = result.data,
                    nextCursor = null,
                    sourceKind = sourceKind,
                    sourceEndpoint = displayLabel
                ),
                sourceKind = sourceKind,
                endpoint = displayLabel
            )
            is ProviderResult.RateLimited -> result
            is ProviderResult.AccessRestricted -> result
            is ProviderResult.NetworkError -> result
            is ProviderResult.ContentUnavailable -> result
        }
    }

    override suspend fun searchVideos(query: String): ProviderResult<List<PublicVideo>> {
        val trimmed = query.trim()
        TikTokLinkParser.parse(trimmed)?.let {
            return when (val single = resolveVideoByUrlOrId(trimmed)) {
                is ProviderResult.Success -> ProviderResult.Success(
                    data = listOf(single.data),
                    sourceKind = single.sourceKind,
                    endpoint = single.endpoint
                )
                is ProviderResult.RateLimited -> single
                is ProviderResult.AccessRestricted -> single
                is ProviderResult.NetworkError -> single
                is ProviderResult.ContentUnavailable -> single
            }
        }
        if (trimmed.startsWith("@")) {
            return searchCreator(trimmed.removePrefix("@"))
        }
        val sanitizedTag = trimmed.removePrefix("#").replace(Regex("[^A-Za-z0-9_]"), "")
        if (sanitizedTag.isBlank()) {
            return ProviderResult.ContentUnavailable("Enter a public keyword, #hashtag, @creator, or TikTok link.")
        }
        return searchHashtag(sanitizedTag)
    }

    override suspend fun searchHashtag(hashtag: String): ProviderResult<List<PublicVideo>> {
        val cleanTag = hashtag.trim().removePrefix("#").replace(Regex("[^A-Za-z0-9_\\u00C0-\\u024F]"), "")
        if (cleanTag.isBlank()) {
            return ProviderResult.ContentUnavailable("Hashtag cannot be empty.")
        }
        val encoded = encode(cleanTag)
        return fetchVideosFromRoute(
            rssUrl = "$normalizedBaseUrl/tag/$encoded?format=rss",
            htmlFallbackUrl = "$normalizedBaseUrl/tag/$encoded"
        )
    }

    override suspend fun searchCreator(username: String): ProviderResult<List<PublicVideo>> {
        val cleanHandle = username.trim().removePrefix("@").replace(Regex("[^A-Za-z0-9_.]"), "")
        if (cleanHandle.isBlank()) {
            return ProviderResult.ContentUnavailable("Creator handle cannot be empty.")
        }
        val encoded = encode(cleanHandle)
        return fetchVideosFromRoute(
            rssUrl = "$normalizedBaseUrl/@$encoded?format=rss",
            htmlFallbackUrl = "$normalizedBaseUrl/@$encoded",
            fallbackAuthor = cleanHandle
        )
    }

    override suspend fun resolveVideoByUrlOrId(urlOrId: String): ProviderResult<PublicVideo> {
        val parsed = TikTokLinkParser.parse(urlOrId)
            ?: return ProviderResult.ContentUnavailable("Unsupported TikTok URL or video ID.")
        val authorSegment = parsed.authorHandle ?: "tiktok"
        val htmlUrl = "$normalizedBaseUrl/@$authorSegment/video/${parsed.videoId}"
        return when (val result = fetchHtmlOnly(htmlUrl, fallbackAuthor = parsed.authorHandle)) {
            is ProviderResult.Success -> {
                val matched = result.data.firstOrNull { it.id == parsed.videoId } ?: result.data.firstOrNull()
                if (matched != null) {
                    ProviderResult.Success(matched, sourceKind, displayLabel)
                } else {
                    ProviderResult.ContentUnavailable("Video metadata not returned by $displayLabel.")
                }
            }
            is ProviderResult.RateLimited -> result
            is ProviderResult.AccessRestricted -> result
            is ProviderResult.NetworkError -> result
            is ProviderResult.ContentUnavailable -> result
        }
    }

    private suspend fun fetchVideosFromRoute(
        rssUrl: String,
        htmlFallbackUrl: String,
        fallbackAuthor: String? = null
    ): ProviderResult<List<PublicVideo>> = withContext(ioDispatcher) {
        if (!PrivacyPolicyEnforcer.isSafeHttpsUrl(rssUrl)) {
            return@withContext ProviderResult.NetworkError(displayLabel, "HTTPS required for endpoint", "HTTPS_REQUIRED")
        }

        val rssResult = executeGet(rssUrl)
        when (rssResult) {
            is HttpFetchResult.Body -> {
                val parsedRss = ProxiTokParser.parseRssFeed(
                    xml = rssResult.text,
                    baseUrl = normalizedBaseUrl,
                    sourceKind = sourceKind,
                    sourceLabel = displayLabel
                )
                if (parsedRss.isNotEmpty()) {
                    return@withContext ProviderResult.Success(parsedRss, sourceKind, displayLabel)
                }
            }
            is HttpFetchResult.Failure -> {
                // Immediately propagate 429 / 403 / CAPTCHA without issuing a second request
                if (rssResult.result is ProviderResult.RateLimited || rssResult.result is ProviderResult.AccessRestricted) {
                    return@withContext rssResult.result
                }
            }
        }

        fetchHtmlOnly(htmlFallbackUrl, fallbackAuthor)
    }

    private suspend fun fetchHtmlOnly(
        htmlUrl: String,
        fallbackAuthor: String? = null
    ): ProviderResult<List<PublicVideo>> = withContext(ioDispatcher) {
        if (!PrivacyPolicyEnforcer.isSafeHttpsUrl(htmlUrl)) {
            return@withContext ProviderResult.NetworkError(displayLabel, "HTTPS required for endpoint", "HTTPS_REQUIRED")
        }
        when (val htmlResult = executeGet(htmlUrl)) {
            is HttpFetchResult.Body -> {
                val parsedHtml = ProxiTokParser.parseHtmlFeedOrVideo(
                    html = htmlResult.text,
                    baseUrl = normalizedBaseUrl,
                    sourceKind = sourceKind,
                    sourceLabel = displayLabel,
                    fallbackAuthor = fallbackAuthor
                )
                if (parsedHtml.isNotEmpty()) {
                    ProviderResult.Success(parsedHtml, sourceKind, displayLabel)
                } else {
                    ProviderResult.ContentUnavailable("No public playable streams found on $displayLabel.")
                }
            }
            is HttpFetchResult.Failure -> htmlResult.result
        }
    }

    private sealed class HttpFetchResult {
        data class Body(val text: String) : HttpFetchResult()
        data class Failure(val result: ProviderResult<Nothing>) : HttpFetchResult()
    }

    private fun executeGet(url: String): HttpFetchResult {
        return try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val restriction = UpstreamBlockDetector.detectRestriction(
                    statusCode = response.code,
                    bodySnippet = bodyString.take(4096),
                    endpoint = displayLabel,
                    retryAfterHeader = response.header("Retry-After")
                )
                if (restriction != null) {
                    return HttpFetchResult.Failure(restriction)
                }
                if (!response.isSuccessful) {
                    return HttpFetchResult.Failure(
                        ProviderResult.NetworkError(
                            endpoint = displayLabel,
                            message = "Endpoint returned HTTP ${response.code}",
                            causeType = "HTTP_${response.code}"
                        )
                    )
                }
                HttpFetchResult.Body(bodyString)
            }
        } catch (t: Throwable) {
            HttpFetchResult.Failure(
                ProviderResult.NetworkError(
                    endpoint = displayLabel,
                    message = "Connection error on $displayLabel (${t.javaClass.simpleName})",
                    causeType = t.javaClass.simpleName
                )
            )
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
