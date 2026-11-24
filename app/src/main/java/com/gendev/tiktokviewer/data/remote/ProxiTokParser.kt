package com.gendev.tiktokviewer.data.remote

import com.gendev.tiktokviewer.data.privacy.PrivacyPolicyEnforcer
import com.gendev.tiktokviewer.domain.model.PlaybackMode
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import java.net.URLDecoder

object TikTokLinkParser {
    private val videoUrlRegex = Regex(
        """https?://(?:www\.|m\.)?tiktok\.com/@([A-Za-z0-9_.]+)/video/(\d+)"""
    )
    private val embedUrlRegex = Regex(
        """https?://(?:www\.)?tiktok\.com/embed(?:/v[23])?/(\d+)"""
    )
    private val numericIdRegex = Regex("""^\d{15,22}$""")
    private val hashtagRegex = Regex("""#([A-Za-z0-9_\u00C0-\u024F]+)""")

    data class ParsedTikTokLink(
        val videoId: String,
        val authorHandle: String?,
        val canonicalUrl: String,
        val embedUrl: String
    )

    fun parse(input: String): ParsedTikTokLink? {
        val trimmed = input.trim()
        if (numericIdRegex.matches(trimmed)) {
            return ParsedTikTokLink(
                videoId = trimmed,
                authorHandle = null,
                canonicalUrl = "https://www.tiktok.com/video/$trimmed",
                embedUrl = "https://www.tiktok.com/embed/v3/$trimmed"
            )
        }

        videoUrlRegex.find(trimmed)?.let { match ->
            val author = match.groupValues[1]
            val id = match.groupValues[2]
            return ParsedTikTokLink(
                videoId = id,
                authorHandle = author,
                canonicalUrl = "https://www.tiktok.com/@$author/video/$id",
                embedUrl = "https://www.tiktok.com/embed/v3/$id"
            )
        }

        embedUrlRegex.find(trimmed)?.let { match ->
            val id = match.groupValues[1]
            return ParsedTikTokLink(
                videoId = id,
                authorHandle = null,
                canonicalUrl = "https://www.tiktok.com/video/$id",
                embedUrl = "https://www.tiktok.com/embed/v3/$id"
            )
        }

        return null
    }

    fun isShortTikTokUrl(input: String): Boolean {
        val lower = input.trim().lowercase()
        return lower.startsWith("https://vm.tiktok.com/") ||
            lower.startsWith("https://vt.tiktok.com/") ||
            lower.startsWith("https://www.tiktok.com/t/")
    }

    fun extractHashtags(text: String): List<String> {
        return hashtagRegex.findAll(text)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .take(5)
            .toList()
    }
}

object ProxiTokParser {
    private val rssItemRegex = Regex("""<item>(.*?)</item>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val titleRegex = Regex("""<title>(?:<!\[CDATA\[)?(.*?)(?:]]>)?</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val linkRegex = Regex("""<link>(.*?)</link>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val enclosureRegex = Regex("""<enclosure[^>]+url=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val mediaContentRegex = Regex("""<media:content[^>]+url=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val videoPathRegex = Regex("""/@([A-Za-z0-9_.]+)/video/(\d+)""")
    private val htmlVideoSrcRegex = Regex("""<(?:video|source)[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ogVideoRegex = Regex("""<meta[^>]+property=["']og:video(?::url)?["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ogImageRegex = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ogTitleRegex = Regex("""<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    fun parseRssFeed(
        xml: String,
        baseUrl: String,
        sourceKind: VideoSourceKind,
        sourceLabel: String
    ): List<PublicVideo> {
        val items = rssItemRegex.findAll(xml).toList()
        return items.mapNotNull { itemMatch ->
            val block = itemMatch.groupValues[1]
            val rawTitle = decodeHtml(titleRegex.find(block)?.groupValues?.get(1).orEmpty().trim())
            val rawLink = linkRegex.find(block)?.groupValues?.get(1).orEmpty().trim()
            val pathMatch = videoPathRegex.find(rawLink) ?: return@mapNotNull null

            val author = pathMatch.groupValues[1]
            val videoId = pathMatch.groupValues[2]
            val rawStream = enclosureRegex.find(block)?.groupValues?.get(1)
                ?: mediaContentRegex.find(block)?.groupValues?.get(1)

            val resolvedStream = resolveAndVerifyMediaUrl(rawStream, baseUrl)
            val playbackMode = if (resolvedStream != null) {
                PlaybackMode.EXO_DIRECT_STREAM
            } else {
                PlaybackMode.ISOLATED_EMBED_WEBVIEW
            }

            PublicVideo(
                id = videoId,
                title = rawTitle.ifBlank { "Public clip by @$author" },
                authorHandle = author,
                authorDisplayName = "@$author",
                hashtags = TikTokLinkParser.extractHashtags(rawTitle),
                thumbnailUrl = null,
                directMediaUrl = resolvedStream,
                embedUrl = "https://www.tiktok.com/embed/v3/$videoId",
                canonicalUrl = "https://www.tiktok.com/@$author/video/$videoId",
                sourceKind = sourceKind,
                sourceLabel = sourceLabel,
                playbackMode = playbackMode
            )
        }.distinctBy { it.id }
    }

    fun parseHtmlFeedOrVideo(
        html: String,
        baseUrl: String,
        sourceKind: VideoSourceKind,
        sourceLabel: String,
        fallbackAuthor: String? = null
    ): List<PublicVideo> {
        val pathMatches = videoPathRegex.findAll(html)
            .map { it.groupValues[1] to it.groupValues[2] }
            .distinctBy { it.second }
            .toList()

        val rawVideoSrc = ogVideoRegex.find(html)?.groupValues?.get(1)
            ?: htmlVideoSrcRegex.find(html)?.groupValues?.get(1)
        val singleResolvedStream = resolveAndVerifyMediaUrl(rawVideoSrc, baseUrl)
        val ogImage = resolveAndVerifyMediaUrl(ogImageRegex.find(html)?.groupValues?.get(1), baseUrl)
        val ogTitle = decodeHtml(ogTitleRegex.find(html)?.groupValues?.get(1).orEmpty().trim())

        return pathMatches.take(20).mapIndexed { index, (author, videoId) ->
            val directStream = if (index == 0 || pathMatches.size == 1) singleResolvedStream else null
            val resolvedAuthor = author.ifBlank { fallbackAuthor ?: "creator" }
            PublicVideo(
                id = videoId,
                title = if (index == 0 && ogTitle.isNotBlank()) ogTitle else "Public video by @$resolvedAuthor",
                authorHandle = resolvedAuthor,
                authorDisplayName = "@$resolvedAuthor",
                hashtags = TikTokLinkParser.extractHashtags(ogTitle),
                thumbnailUrl = if (index == 0) ogImage else null,
                directMediaUrl = directStream,
                embedUrl = "https://www.tiktok.com/embed/v2/$videoId",
                canonicalUrl = "https://www.tiktok.com/@$resolvedAuthor/video/$videoId",
                sourceKind = sourceKind,
                sourceLabel = sourceLabel,
                playbackMode = if (directStream != null) PlaybackMode.EXO_DIRECT_STREAM else PlaybackMode.ISOLATED_EMBED_WEBVIEW
            )
        }
    }

    private fun resolveAndVerifyMediaUrl(raw: String?, baseUrl: String): String? {
        if (raw.isNullOrBlank()) return null
        val decoded = decodeHtml(raw.trim())
        val absolute = when {
            decoded.startsWith("https://") -> decoded
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> "${baseUrl.trimEnd('/')}$decoded"
            else -> return null
        }
        return if (PrivacyPolicyEnforcer.isSafeHttpsUrl(absolute)) absolute else null
    }

    private fun decodeHtml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .let { str ->
                runCatching {
                    if (str.contains("%3A%2F%2F")) URLDecoder.decode(str, "UTF-8") else str
                }.getOrDefault(str)
            }
    }
}
