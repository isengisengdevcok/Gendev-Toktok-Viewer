package com.gendev.tiktokviewer

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
import com.gendev.tiktokviewer.domain.model.SearchMode
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import com.gendev.tiktokviewer.ui.viewmodel.FeedUiState
import com.gendev.tiktokviewer.ui.viewmodel.ViewerViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
        assertEquals("https://www.tiktok.com/embed/v2/7343728212422282538", canonical?.embedUrl)

        val embed = TikTokLinkParser.parse("https://www.tiktok.com/embed/v2/7106594312292453675")
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

        val vm = ViewerViewModel(repository)
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
}
