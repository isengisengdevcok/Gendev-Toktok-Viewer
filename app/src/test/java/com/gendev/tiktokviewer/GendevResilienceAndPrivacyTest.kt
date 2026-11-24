package com.gendev.tiktokviewer

import com.gendev.tiktokviewer.data.cache.EphemeralMetadataCache
import com.gendev.tiktokviewer.data.privacy.PrivacyPolicyEnforcer
import com.gendev.tiktokviewer.data.provider.ProxiTokProvider
import com.gendev.tiktokviewer.data.provider.TikTokPlaybackProviderImpl
import com.gendev.tiktokviewer.data.resilience.RetryBackoffPolicy
import com.gendev.tiktokviewer.data.resilience.TikTokFallbackManagerImpl
import com.gendev.tiktokviewer.domain.model.CircuitState
import com.gendev.tiktokviewer.domain.model.PlaybackMode
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import com.gendev.tiktokviewer.domain.provider.ProviderEndpointCall
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GendevResilienceAndPrivacyTest {

    private fun buildTestClient(handler: (Request) -> Response): OkHttpClient {
        return PrivacyPolicyEnforcer.createPrivacyOkHttpClient()
            .newBuilder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()
    }

    private fun rssPayload(videoId: String = "7106594312292453675", author: String = "tiktok"): String = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <rss version="2.0">
          <channel>
            <item>
              <title>Public test video #tech #android</title>
              <link>https://proxitok.example/@$author/video/$videoId</link>
              <enclosure url="https://cdn.example/video/$videoId.mp4" type="video/mp4" />
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `1_public_feed_loads_without_login_or_cookies`() = runTest {
        var sawCookieHeader = false
        val client = buildTestClient { req ->
            sawCookieHeader = req.header("Cookie") != null
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(rssPayload().toResponseBody("application/xml".toMediaType()))
                .build()
        }

        val provider = ProxiTokProvider(
            providerId = "proxitok_test",
            baseUrl = "https://proxitok.example",
            displayLabel = "ProxiTok Test",
            sourceKind = VideoSourceKind.PROXITOK_PUBLIC,
            okHttpClient = client
        )

        val result = provider.fetchTrendingFeed()
        assertTrue(result is ProviderResult.Success)
        val feed = (result as ProviderResult.Success).data
        assertEquals(1, feed.videos.size)
        assertEquals("7106594312292453675", feed.videos.first().id)
        assertFalse(sawCookieHeader)
    }

    @Test
    fun `2_public_video_playback_resolves_media3_and_embed_fallback`() = runTest {
        val playbackProvider = TikTokPlaybackProviderImpl()
        val directVideo = PublicVideo(
            id = "123456789012345",
            title = "Direct Clip",
            authorHandle = "creator",
            authorDisplayName = "@creator",
            hashtags = listOf("tech"),
            thumbnailUrl = null,
            directMediaUrl = "https://cdn.example/video.mp4",
            embedUrl = "https://www.tiktok.com/embed/v2/123456789012345",
            canonicalUrl = "https://www.tiktok.com/@creator/video/123456789012345",
            sourceKind = VideoSourceKind.PROXITOK_PUBLIC,
            sourceLabel = "ProxiTok",
            playbackMode = PlaybackMode.EXO_DIRECT_STREAM
        )
        val directResult = playbackProvider.resolvePlaybackTarget(directVideo)
        assertTrue(directResult is ProviderResult.Success)
        assertEquals(PlaybackMode.EXO_DIRECT_STREAM, (directResult as ProviderResult.Success).data.mode)

        val embedOnlyVideo = directVideo.copy(directMediaUrl = null, playbackMode = PlaybackMode.ISOLATED_EMBED_WEBVIEW)
        val embedResult = playbackProvider.resolvePlaybackTarget(embedOnlyVideo)
        assertTrue(embedResult is ProviderResult.Success)
        assertEquals(PlaybackMode.ISOLATED_EMBED_WEBVIEW, (embedResult as ProviderResult.Success).data.mode)
    }

    @Test
    fun `4_and_5_hashtag_and_creator_search_return_public_videos`() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = buildTestClient { req ->
            requestedUrls += req.url.toString()
            val body = if (req.url.encodedPath.contains("/tag/")) {
                rssPayload("7343728212422282538", "nasa")
            } else {
                rssPayload("7324461580063771946", "natgeo")
            }
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/xml".toMediaType()))
                .build()
        }

        val provider = ProxiTokProvider(
            providerId = "proxitok_search",
            baseUrl = "https://proxitok.example",
            displayLabel = "ProxiTok Search",
            sourceKind = VideoSourceKind.PROXITOK_PUBLIC,
            okHttpClient = client
        )

        val tagResult = provider.searchHashtag("#space")
        assertTrue(tagResult is ProviderResult.Success)
        assertEquals("nasa", (tagResult as ProviderResult.Success).data.first().authorHandle)
        assertTrue(requestedUrls.any { it.contains("/tag/space") })

        val creatorResult = provider.searchCreator("@natgeo")
        assertTrue(creatorResult is ProviderResult.Success)
        assertEquals("natgeo", (creatorResult as ProviderResult.Success).data.first().authorHandle)
        assertTrue(requestedUrls.any { it.contains("/@natgeo") })
    }

    @Test
    fun `6_captcha_stops_retry_immediately_and_flags_verification_required`() = runTest {
        val hitCount = AtomicInteger(0)
        val client = buildTestClient { req ->
            hitCount.incrementAndGet()
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("<html><body>Please complete tt_captcha security check</body></html>".toResponseBody("text/html".toMediaType()))
                .build()
        }

        val provider = ProxiTokProvider(
            providerId = "captcha_node",
            baseUrl = "https://proxitok.example",
            displayLabel = "Captcha Node",
            sourceKind = VideoSourceKind.PROXITOK_PUBLIC,
            okHttpClient = client
        )
        val retryPolicy = RetryBackoffPolicy(maxAttempts = 3, delayFn = {})
        val result = retryPolicy.execute { provider.fetchTrendingFeed() }

        assertTrue(result is ProviderResult.AccessRestricted)
        assertTrue((result as ProviderResult.AccessRestricted).isCaptcha)
        assertEquals(1, hitCount.get()) // Must never retry or bypass CAPTCHA
    }

    @Test
    fun `7_http_403_opens_circuit_breaker_and_stops_retry`() = runTest {
        val hitCount = AtomicInteger(0)
        val client = buildTestClient { req ->
            hitCount.incrementAndGet()
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body("Access Denied".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val provider = ProxiTokProvider("node_403", "https://node403.example", "Node 403", VideoSourceKind.PROXITOK_PUBLIC, client)
        val fallbackManager = TikTokFallbackManagerImpl(client, RetryBackoffPolicy(maxAttempts = 3, delayFn = {}))

        val res = fallbackManager.executeWithFallback(
            "feed",
            listOf(ProviderEndpointCall("node_403", "Node 403", VideoSourceKind.PROXITOK_PUBLIC) { provider.fetchTrendingFeed() })
        )

        assertTrue(res is ProviderResult.AccessRestricted)
        assertEquals(403, (res as ProviderResult.AccessRestricted).statusCode)
        assertEquals(1, hitCount.get())
        val snap = fallbackManager.endpointHealthFlow.value.first()
        assertEquals(CircuitState.OPEN, snap.circuitState)
    }

    @Test
    fun `8_http_429_respects_cooldown_and_halts_request_loop`() = runTest {
        val hitCount = AtomicInteger(0)
        var currentNow = 10_000L
        val client = buildTestClient { req ->
            hitCount.incrementAndGet()
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .header("Retry-After", "45")
                .message("Too Many Requests")
                .body("Rate limit".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val provider = ProxiTokProvider("node_429", "https://node429.example", "Node 429", VideoSourceKind.PROXITOK_PUBLIC, client)
        val fallbackManager = TikTokFallbackManagerImpl(
            okHttpClient = client,
            retryPolicy = RetryBackoffPolicy(maxAttempts = 3, delayFn = {}),
            nowProvider = { currentNow }
        )

        val call = ProviderEndpointCall("node_429", "Node 429", VideoSourceKind.PROXITOK_PUBLIC) {
            provider.fetchTrendingFeed()
        }

        val first = fallbackManager.executeWithFallback("feed", listOf(call))
        assertTrue(first is ProviderResult.RateLimited)
        assertEquals(45L, (first as ProviderResult.RateLimited).retryAfterSeconds)
        assertEquals(1, hitCount.get())

        // Second call within cooldown window must NOT hit network at all
        currentNow += 5_000L
        val second = fallbackManager.executeWithFallback("feed", listOf(call))
        assertTrue(second is ProviderResult.RateLimited)
        assertEquals(1, hitCount.get())
    }

    @Test
    fun `9_fallback_manager_rotates_to_healthy_secondary_provider`() = runTest {
        val client = PrivacyPolicyEnforcer.createPrivacyOkHttpClient()
        val fallbackManager = TikTokFallbackManagerImpl(client, RetryBackoffPolicy(maxAttempts = 1, delayFn = {}))

        val failingCall = ProviderEndpointCall<String>("primary", "Primary Node", VideoSourceKind.PROXITOK_PUBLIC) {
            ProviderResult.AccessRestricted("Primary Node", 403, isCaptcha = false, message = "Blocked")
        }
        val healthyCall = ProviderEndpointCall("secondary", "Official Embed", VideoSourceKind.TIKTOK_OEMBED) {
            ProviderResult.Success("ok_stream", VideoSourceKind.TIKTOK_OEMBED, "Official Embed")
        }

        val result = fallbackManager.executeWithFallback("feed", listOf(failingCall, healthyCall))
        assertTrue(result is ProviderResult.Success)
        assertEquals("ok_stream", (result as ProviderResult.Success).data)
    }

    @Test
    fun `10_ephemeral_cache_wipe_and_ttl_expiration`() {
        var fakeTime = 1_000L
        val cache = EphemeralMetadataCache(maxEntries = 10, ttlMillis = 5_000L, nowProvider = { fakeTime })
        val sampleVideo = PublicVideo(
            id = "999",
            title = "Cached Clip",
            authorHandle = "creator",
            authorDisplayName = "@creator",
            hashtags = emptyList(),
            thumbnailUrl = null,
            directMediaUrl = null,
            embedUrl = "https://www.tiktok.com/embed/v2/999",
            canonicalUrl = "https://www.tiktok.com/@creator/video/999",
            sourceKind = VideoSourceKind.TIKTOK_OEMBED,
            sourceLabel = "oEmbed",
            playbackMode = PlaybackMode.ISOLATED_EMBED_WEBVIEW
        )

        cache.putVideo(sampleVideo)
        assertNotNull(cache.getVideo("999"))

        fakeTime += 10_000L
        assertNull(cache.getVideo("999"))

        cache.putVideo(sampleVideo)
        assertEquals(1, cache.entryCount())
        cache.wipeAll(null)
        assertEquals(0, cache.entryCount())
    }

    @Test
    fun `11_privacy_enforcer_strips_tracking_params_and_blocks_http`() {
        assertFalse(PrivacyPolicyEnforcer.isSafeHttpsUrl("http://insecure.example/video.mp4"))
        assertTrue(PrivacyPolicyEnforcer.isSafeHttpsUrl("https://www.tiktok.com/@tiktok/video/7106594312292453675"))

        val sanitized = PrivacyPolicyEnforcer.sanitizeHttpsUrl(
            "https://www.tiktok.com/@user/video/123?utm_source=copy&tt_from=more&_r=1&keep=yes"
        )
        assertNotNull(sanitized)
        val urlText = sanitized.toString()
        assertFalse(urlText.contains("utm_source"))
        assertFalse(urlText.contains("tt_from"))
        assertFalse(urlText.contains("_r="))
        assertTrue(urlText.contains("keep=yes"))
    }
}
