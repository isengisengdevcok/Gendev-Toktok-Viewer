package com.gendev.tiktokviewer

import com.gendev.tiktokviewer.ai.AiVibeState
import com.gendev.tiktokviewer.ai.AiVibeSuggestion
import com.gendev.tiktokviewer.ai.AiVideoInsightState
import com.gendev.tiktokviewer.ai.GeminiScrollAssistant
import com.gendev.tiktokviewer.data.cache.EphemeralMetadataCache
import com.gendev.tiktokviewer.data.privacy.PrivacyPolicyEnforcer
import com.gendev.tiktokviewer.data.provider.ProxiTokProvider
import com.gendev.tiktokviewer.data.provider.TikTokOEmbedVideoProvider
import com.gendev.tiktokviewer.data.provider.TikTokPlaybackProviderImpl
import com.gendev.tiktokviewer.data.provider.TikTokPublicContentAdapter
import com.gendev.tiktokviewer.data.remote.TikTokLinkParser
import com.gendev.tiktokviewer.data.remote.TikTokOEmbedService
import com.gendev.tiktokviewer.data.repository.HybridTikTokRepository
import com.gendev.tiktokviewer.data.resilience.RetryBackoffPolicy
import com.gendev.tiktokviewer.data.resilience.TikTokFallbackManagerImpl
import com.gendev.tiktokviewer.domain.model.PlaybackMode
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.SearchMode
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import com.gendev.tiktokviewer.ui.viewmodel.FeedUiState
import com.gendev.tiktokviewer.ui.viewmodel.ViewerEngineMode
import com.gendev.tiktokviewer.ui.viewmodel.ViewerViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class GendevViewModelAndOEmbedTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `link parser handles canonical embed and numeric tiktok ids`() {
        val canonical = TikTokLinkParser.parse("https://www.tiktok.com/@nasa/video/7343728212422282538")
        assertNotNull(canonical)
        assertEquals("7343728212422282538", canonical?.videoId)
        assertEquals("nasa", canonical?.authorHandle)
        assertEquals("https://www.tiktok.com/embed/v3/7343728212422282538", canonical?.embedUrl)

        val embed = TikTokLinkParser.parse("https://www.tiktok.com/embed/v3/7106594312292453675")
        assertNotNull(embed)
        assertEquals("7106594312292453675", embed?.videoId)
    }

    @Test
    fun `oembed provider resolves public video metadata and embed v2 target`() = runTest {
        val oEmbedJson = """
            {
              "version": "1.0",
              "type": "video",
              "title": "Exploring space #nasa #science",
              "author_name": "NASA",
              "author_unique_id": "nasa",
              "thumbnail_url": "https://p16-sign.tiktokcdn.com/thumb.jpg",
              "embed_product_id": "7343728212422282538"
            }
        """.trimIndent()

        val client = PrivacyPolicyEnforcer.createPrivacyOkHttpClient()
            .newBuilder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(oEmbedJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val service = Retrofit.Builder()
            .baseUrl("https://www.tiktok.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TikTokOEmbedService::class.java)

        val provider = TikTokOEmbedVideoProvider(service, client)
        val result = provider.resolveVideoByUrlOrId("https://www.tiktok.com/@nasa/video/7343728212422282538")
        assertTrue(result is ProviderResult.Success)
        val video = (result as ProviderResult.Success).data
        assertEquals("nasa", video.authorHandle)
        assertEquals(listOf("nasa", "science"), video.hashtags)
        assertEquals(PlaybackMode.ISOLATED_EMBED_WEBVIEW, video.playbackMode)
    }

    @Test
    fun `viewmodel loads feed switches source and wipes cache without storing history`() = runTest {
        val rssXml = """
            <rss version="2.0"><channel>
              <item>
                <title>Space launch #space</title>
                <link>https://proxitok.example/@nasa/video/7343728212422282538</link>
                <enclosure url="https://cdn.example/launch.mp4" type="video/mp4" />
              </item>
            </channel></rss>
        """.trimIndent()

        val client = PrivacyPolicyEnforcer.createPrivacyOkHttpClient()
            .newBuilder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(rssXml.toResponseBody("application/xml".toMediaType()))
                    .build()
            })
            .build()

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val oEmbedService = Retrofit.Builder()
            .baseUrl("https://www.tiktok.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TikTokOEmbedService::class.java)

        val proxiProvider = ProxiTokProvider(
            providerId = "p1",
            baseUrl = "https://proxitok.example",
            displayLabel = "ProxiTok P1",
            sourceKind = VideoSourceKind.PROXITOK_PUBLIC,
            okHttpClient = client,
            ioDispatcher = testDispatcher
        )
        val oEmbedProvider = TikTokOEmbedVideoProvider(oEmbedService, client)
        val publicAdapter = TikTokPublicContentAdapter(oEmbedProvider, client)
        val fallbackManager = TikTokFallbackManagerImpl(client, RetryBackoffPolicy(maxAttempts = 1, delayFn = {}))

        val repository = HybridTikTokRepository(
            cache = EphemeralMetadataCache(),
            okHttpClient = client,
            fallbackManager = fallbackManager,
            proxiTokInstances = listOf(proxiProvider),
            oEmbedVideoProvider = oEmbedProvider,
            publicContentAdapter = publicAdapter,
            playbackProvider = TikTokPlaybackProviderImpl(),
            selfHostedFactory = { url ->
                ProxiTokProvider("self", url, "Self", VideoSourceKind.PROXITOK_SELF_HOSTED, client, testDispatcher)
            }
        )

        val vm = ViewerViewModel(repository, startInUnifiedScroll = false)
        advanceUntilIdle()

        val state = vm.feedState.value
        assertTrue(state is FeedUiState.Ready)
        assertEquals(1, (state as FeedUiState.Ready).videos.size)
        assertTrue(vm.cacheCount.value > 0)

        // Reject non-HTTPS self-hosted URL
        assertFalse(vm.updateSelfHostedProxiTok("http://insecure.local"))
        assertTrue(vm.updateSelfHostedProxiTok("https://my-proxitok.example.org"))

        // Search hashtag works and cache wipe clears everything
        vm.performSearch("#space", SearchMode.HASHTAG)
        advanceUntilIdle()
        vm.wipeEphemeralCache(null)
        assertEquals(0, vm.cacheCount.value)
    }

    @Test
    fun `tikwm provider resolves trending feed and direct mp4 streams`() = runTest {
        val tikWmJson = """
            {
              "code": 0,
              "msg": "success",
              "data": [
                {
                  "video_id": "7343728212422282538",
                  "title": "Public ocean view #nature #fyp",
                  "play": "/video/media/play/7343728212422282538.mp4",
                  "cover": "https://www.tikwm.com/video/cover/7343728212422282538.jpg",
                  "duration": 18,
                  "author": {
                    "id": "101",
                    "unique_id": "oceanview",
                    "nickname": "Ocean View"
                  }
                }
              ]
            }
        """.trimIndent()

        val client = PrivacyPolicyEnforcer.createPrivacyOkHttpClient()
            .newBuilder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(tikWmJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val provider = com.gendev.tiktokviewer.data.provider.TikWmPublicProvider(
            okHttpClient = client,
            moshi = moshi,
            ioDispatcher = testDispatcher
        )

        val feedResult = provider.fetchTrendingFeed()
        assertTrue(feedResult is ProviderResult.Success)
        val first = (feedResult as ProviderResult.Success).data.videos.first()
        assertEquals("7343728212422282538", first.id)
        assertEquals("oceanview", first.authorHandle)
        assertEquals(PlaybackMode.EXO_DIRECT_STREAM, first.playbackMode)
        assertEquals("https://www.tikwm.com/video/media/play/7343728212422282538.mp4", first.directMediaUrl)
        assertEquals("https://www.tiktok.com/embed/v3/7343728212422282538", first.embedUrl)
    }

    @Test
    fun `viewmodel automatically switches to guest scroll mode when upstream endpoints fail and runs gemini ai`() = runTest {
        val failingClient = PrivacyPolicyEnforcer.createPrivacyOkHttpClient()
            .newBuilder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("Upstream unavailable".toResponseBody("text/plain".toMediaType()))
                    .build()
            })
            .build()

        val oEmbedService = object : TikTokOEmbedService {
            override suspend fun getOEmbed(videoUrl: String): retrofit2.Response<com.gendev.tiktokviewer.data.remote.dto.TikTokOEmbedDto> {
                return retrofit2.Response.error(
                    503,
                    "Service Unavailable".toResponseBody("text/plain".toMediaType())
                )
            }
        }

        val proxiProvider = ProxiTokProvider(
            providerId = "down_node",
            baseUrl = "https://down.example",
            displayLabel = "Down Node",
            sourceKind = VideoSourceKind.PROXITOK_PUBLIC,
            okHttpClient = failingClient,
            ioDispatcher = testDispatcher
        )
        val oEmbedProvider = TikTokOEmbedVideoProvider(oEmbedService, failingClient, testDispatcher)
        val publicAdapter = TikTokPublicContentAdapter(oEmbedProvider, failingClient, testDispatcher)
        val fallbackManager = TikTokFallbackManagerImpl(failingClient, RetryBackoffPolicy(maxAttempts = 1, delayFn = {}))

        val repository = HybridTikTokRepository(
            cache = EphemeralMetadataCache(),
            okHttpClient = failingClient,
            fallbackManager = fallbackManager,
            proxiTokInstances = listOf(proxiProvider),
            oEmbedVideoProvider = oEmbedProvider,
            publicContentAdapter = publicAdapter,
            playbackProvider = TikTokPlaybackProviderImpl(),
            selfHostedFactory = { url ->
                ProxiTokProvider("self", url, "Self", VideoSourceKind.PROXITOK_SELF_HOSTED, failingClient, testDispatcher)
            }
        )

        val fakeAi = object : GeminiScrollAssistant {
            override suspend fun buildVibeDiscovery(userMoodOrTopic: String): AiVibeSuggestion {
                return AiVibeSuggestion(
                    vibeLabel = "Kucing Santai",
                    searchQuery = "kucing lucu",
                    hashtags = listOf("kucinglucu", "cats", "fyp"),
                    guestTopicTag = "kucinglucu"
                )
            }

            override fun streamVideoInsight(video: PublicVideo): Flow<AiVideoInsightState> {
                return flowOf(AiVideoInsightState.Ready("Video santai tentang alam."))
            }
        }

        val vm = ViewerViewModel(repository, fakeAi, startInUnifiedScroll = false)
        advanceUntilIdle()

        // Must never strand user on dead error screen when stream APIs fail
        assertEquals(ViewerEngineMode.GUEST_SCROLL, vm.viewerEngineMode.value)
        assertEquals("https://www.tiktok.com/foryou", vm.guestUrl.value)

        // Switching topic in Guest Scroll mode updates URL cleanly
        vm.selectTopic("Trending")
        assertEquals("https://www.tiktok.com/explore", vm.guestUrl.value)

        // Gemini AI Vibe discovery populates Ready state and applies cleanly
        vm.askAiVibe("kucing santai")
        advanceUntilIdle()
        val aiState = vm.aiVibeState.value
        assertTrue(aiState is AiVibeState.Ready)
        vm.applyAiVibeToGuestScroll((aiState as AiVibeState.Ready).suggestion)
        assertEquals("https://www.tiktok.com/tag/kucinglucu", vm.guestUrl.value)
    }

    @Test
    fun `default viewmodel falls back to foryou guest scroll when unavailable and updates theme settings`() = runTest {
        val client = PrivacyPolicyEnforcer.createPrivacyOkHttpClient()
        val oEmbedService = object : TikTokOEmbedService {
            override suspend fun getOEmbed(videoUrl: String): retrofit2.Response<com.gendev.tiktokviewer.data.remote.dto.TikTokOEmbedDto> {
                return retrofit2.Response.error(404, "Not found".toResponseBody("text/plain".toMediaType()))
            }
        }
        val oEmbedProvider = TikTokOEmbedVideoProvider(oEmbedService, client, testDispatcher)
        val publicAdapter = TikTokPublicContentAdapter(oEmbedProvider, client, testDispatcher)
        val fallbackManager = TikTokFallbackManagerImpl(client, RetryBackoffPolicy(maxAttempts = 1, delayFn = {}))
        val repository = HybridTikTokRepository(
            cache = EphemeralMetadataCache(),
            okHttpClient = client,
            fallbackManager = fallbackManager,
            proxiTokInstances = emptyList(),
            oEmbedVideoProvider = oEmbedProvider,
            publicContentAdapter = publicAdapter,
            playbackProvider = TikTokPlaybackProviderImpl(),
            selfHostedFactory = { url ->
                ProxiTokProvider("self", url, "Self", VideoSourceKind.PROXITOK_SELF_HOSTED, client, testDispatcher)
            }
        )

        val vm = ViewerViewModel(repository)
        advanceUntilIdle()
        assertEquals(ViewerEngineMode.GUEST_SCROLL, vm.viewerEngineMode.value)
        assertEquals("https://www.tiktok.com/foryou", vm.guestUrl.value)

        vm.selectThemePreset(com.gendev.tiktokviewer.ui.theme.GendevThemePreset.MIDNIGHT_BLUE)
        assertEquals(com.gendev.tiktokviewer.ui.theme.GendevThemePreset.MIDNIGHT_BLUE, vm.themePreset.value)
        vm.setUltraCleanMode(true)
        assertTrue(vm.ultraCleanMode.value)
    }

    @Test
    fun `native stream loads more videos automatically when scrolling near end`() = runTest {
        val feedJson = """
            {
              "code": 0,
              "msg": "success",
              "data": [
                {"video_id": "101", "title": "Clip 1", "play": "https://cdn.example/101.mp4", "author": {"unique_id": "a1"}},
                {"video_id": "102", "title": "Clip 2", "play": "https://cdn.example/102.mp4", "author": {"unique_id": "a2"}},
                {"video_id": "103", "title": "Clip 3", "play": "https://cdn.example/103.mp4", "author": {"unique_id": "a3"}},
                {"video_id": "104", "title": "Clip 4", "play": "https://cdn.example/104.mp4", "author": {"unique_id": "a4"}},
                {"video_id": "105", "title": "Clip 5", "play": "https://cdn.example/105.mp4", "author": {"unique_id": "a5"}}
              ]
            }
        """.trimIndent()
        val searchMoreJson = """
            {
              "code": 0,
              "msg": "success",
              "data": {
                "videos": [
                  {"video_id": "201", "title": "More 1", "play": "https://cdn.example/201.mp4", "author": {"unique_id": "b1"}},
                  {"video_id": "202", "title": "More 2", "play": "https://cdn.example/202.mp4", "author": {"unique_id": "b2"}}
                ]
              }
            }
        """.trimIndent()

        val client = PrivacyPolicyEnforcer.createPrivacyOkHttpClient()
            .newBuilder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val body = if (url.contains("/api/feed/search")) searchMoreJson else feedJson
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val tikWmProvider = com.gendev.tiktokviewer.data.provider.TikWmPublicProvider(client, moshi, ioDispatcher = testDispatcher)
        val oEmbedService = object : TikTokOEmbedService {
            override suspend fun getOEmbed(videoUrl: String): retrofit2.Response<com.gendev.tiktokviewer.data.remote.dto.TikTokOEmbedDto> {
                return retrofit2.Response.error(404, "unused".toResponseBody("text/plain".toMediaType()))
            }
        }
        val oEmbedProvider = TikTokOEmbedVideoProvider(oEmbedService, client, testDispatcher)
        val publicAdapter = TikTokPublicContentAdapter(oEmbedProvider, client, testDispatcher)
        val fallbackManager = TikTokFallbackManagerImpl(client, RetryBackoffPolicy(maxAttempts = 1, delayFn = {}))
        val repository = HybridTikTokRepository(
            cache = EphemeralMetadataCache(),
            okHttpClient = client,
            fallbackManager = fallbackManager,
            tikWmProvider = tikWmProvider,
            proxiTokInstances = emptyList(),
            oEmbedVideoProvider = oEmbedProvider,
            publicContentAdapter = publicAdapter,
            playbackProvider = TikTokPlaybackProviderImpl(),
            selfHostedFactory = { url ->
                ProxiTokProvider("self", url, "Self", VideoSourceKind.PROXITOK_SELF_HOSTED, client, testDispatcher)
            }
        )

        val vm = ViewerViewModel(repository)
        advanceUntilIdle()
        val initial = vm.feedState.value as FeedUiState.Ready
        assertEquals(5, initial.videos.size)

        // Scrolling near end triggers infinite load-more seamlessly
        vm.onPageSelected(3)
        advanceUntilIdle()
        val expanded = vm.feedState.value as FeedUiState.Ready
        assertEquals(7, expanded.videos.size)
        assertTrue(expanded.videos.any { it.id == "201" })

        // Tapping refresh loads fresh cursor videos and resets index to 0
        vm.refreshFeed()
        advanceUntilIdle()
        val refreshed = vm.feedState.value as FeedUiState.Ready
        assertEquals(0, vm.currentVideoIndex.value)
        assertTrue(refreshed.videos.any { it.id == "201" })
    }

    @Test
    fun `refreshFeed toggles guest scroll between foryou and explore when stream unavailable`() = runTest {
        val failingClient = PrivacyPolicyEnforcer.createPrivacyOkHttpClient()
            .newBuilder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("Down".toResponseBody("text/plain".toMediaType()))
                    .build()
            })
            .build()

        val oEmbedService = object : TikTokOEmbedService {
            override suspend fun getOEmbed(videoUrl: String): retrofit2.Response<com.gendev.tiktokviewer.data.remote.dto.TikTokOEmbedDto> {
                return retrofit2.Response.error(503, "Down".toResponseBody("text/plain".toMediaType()))
            }
        }
        val oEmbedProvider = TikTokOEmbedVideoProvider(oEmbedService, failingClient, testDispatcher)
        val publicAdapter = TikTokPublicContentAdapter(oEmbedProvider, failingClient, testDispatcher)
        val fallbackManager = TikTokFallbackManagerImpl(failingClient, RetryBackoffPolicy(maxAttempts = 1, delayFn = {}))
        val repository = HybridTikTokRepository(
            cache = EphemeralMetadataCache(),
            okHttpClient = failingClient,
            fallbackManager = fallbackManager,
            proxiTokInstances = emptyList(),
            oEmbedVideoProvider = oEmbedProvider,
            publicContentAdapter = publicAdapter,
            playbackProvider = TikTokPlaybackProviderImpl(),
            selfHostedFactory = { url ->
                ProxiTokProvider("self", url, "Self", VideoSourceKind.PROXITOK_SELF_HOSTED, failingClient, testDispatcher)
            }
        )

        val vm = ViewerViewModel(repository, startInUnifiedScroll = true)
        assertEquals("https://www.tiktok.com/foryou", vm.guestUrl.value)
        val initialToken = vm.guestReloadToken.value

        vm.refreshFeed()
        advanceUntilIdle()
        assertEquals(ViewerEngineMode.GUEST_SCROLL, vm.viewerEngineMode.value)
        assertEquals("https://www.tiktok.com/explore", vm.guestUrl.value)
        assertEquals(initialToken + 1, vm.guestReloadToken.value)

        vm.refreshFeed()
        advanceUntilIdle()
        assertEquals("https://www.tiktok.com/foryou", vm.guestUrl.value)
        assertEquals(initialToken + 2, vm.guestReloadToken.value)
    }
}
