package com.gendev.tiktokviewer.data.repository

import android.content.Context
import com.gendev.tiktokviewer.data.cache.EphemeralMetadataCache
import com.gendev.tiktokviewer.data.privacy.PrivacyPolicyEnforcer
import com.gendev.tiktokviewer.data.provider.ProxiTokProvider
import com.gendev.tiktokviewer.data.provider.TikTokOEmbedVideoProvider
import com.gendev.tiktokviewer.data.provider.TikTokPlaybackProviderImpl
import com.gendev.tiktokviewer.data.provider.TikTokPublicContentAdapter
import com.gendev.tiktokviewer.data.provider.TikWmPublicProvider
import com.gendev.tiktokviewer.data.remote.TikTokLinkParser
import com.gendev.tiktokviewer.data.remote.TikTokOEmbedService
import com.gendev.tiktokviewer.data.resilience.RetryBackoffPolicy
import com.gendev.tiktokviewer.data.resilience.TikTokFallbackManagerImpl
import com.gendev.tiktokviewer.domain.model.EndpointHealth
import com.gendev.tiktokviewer.domain.model.FeedPage
import com.gendev.tiktokviewer.domain.model.PlaybackTarget
import com.gendev.tiktokviewer.domain.model.PrivacyConfig
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.SearchMode
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import com.gendev.tiktokviewer.domain.provider.ProviderEndpointCall
import com.gendev.tiktokviewer.domain.provider.TikTokFallbackManager
import com.gendev.tiktokviewer.domain.provider.TikTokPlaybackProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class HybridTikTokRepository(
    private val cache: EphemeralMetadataCache,
    val okHttpClient: OkHttpClient,
    val fallbackManager: TikTokFallbackManager,
    private val tikWmProvider: TikWmPublicProvider? = null,
    private val proxiTokInstances: List<ProxiTokProvider>,
    private val oEmbedVideoProvider: TikTokOEmbedVideoProvider,
    private val publicContentAdapter: TikTokPublicContentAdapter,
    private val playbackProvider: TikTokPlaybackProvider,
    private val selfHostedFactory: (String) -> ProxiTokProvider
) {
    private val _privacyConfig = MutableStateFlow(PrivacyConfig())
    val privacyConfig: StateFlow<PrivacyConfig> = _privacyConfig.asStateFlow()

    private val dynamicProxiTokInstances = MutableStateFlow(proxiTokInstances)
    val endpointHealthFlow: StateFlow<List<EndpointHealth>> = fallbackManager.endpointHealthFlow

    init {
        tikWmProvider?.let {
            fallbackManager.registerOrUpdateEndpoint(it.providerId, "Public Stream · TikWM", it.sourceKind)
        }
        proxiTokInstances.forEach {
            fallbackManager.registerOrUpdateEndpoint(it.providerId, it.displayLabel, it.sourceKind)
        }
        fallbackManager.registerOrUpdateEndpoint(
            oEmbedVideoProvider.providerId,
            "TikTok Official oEmbed",
            oEmbedVideoProvider.sourceKind
        )
        fallbackManager.registerOrUpdateEndpoint(
            publicContentAdapter.providerId,
            "TikTok Official Embed v3",
            publicContentAdapter.sourceKind
        )
    }

    fun updateCustomProxiTokUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            _privacyConfig.value = _privacyConfig.value.copy(customProxiTokBaseUrl = "")
            return true
        }
        if (!PrivacyPolicyEnforcer.isSafeHttpsUrl(trimmed)) {
            return false
        }
        _privacyConfig.value = _privacyConfig.value.copy(customProxiTokBaseUrl = trimmed.trimEnd('/'))
        fallbackManager.registerOrUpdateEndpoint(
            endpointId = "proxitok_self_hosted",
            label = "Self-Hosted (${trimmed.removePrefix("https://")})",
            sourceKind = VideoSourceKind.PROXITOK_SELF_HOSTED
        )
        return true
    }

    /**
     * Fetches active community ProxiTok instances directly from pablouser1/ProxiTok GitHub directory.
     */
    suspend fun refreshCommunityProxiTokDirectory(): Int = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://raw.githubusercontent.com/pablouser1/ProxiTok/master/instances.json")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext 0
                val raw = response.body?.string().orEmpty()
                val arr = JSONArray(raw)
                val discovered = mutableListOf<ProxiTokProvider>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val url = obj.optString("url").trim().trimEnd('/')
                    val cloudflare = obj.optBoolean("cloudflare", false)
                    if (!cloudflare && PrivacyPolicyEnforcer.isSafeHttpsUrl(url)) {
                        val host = url.removePrefix("https://").substringBefore("/")
                        val provider = ProxiTokProvider(
                            providerId = "proxitok_dir_$i",
                            baseUrl = url,
                            displayLabel = "ProxiTok · $host",
                            sourceKind = VideoSourceKind.PROXITOK_PUBLIC,
                            okHttpClient = okHttpClient
                        )
                        discovered += provider
                        fallbackManager.registerOrUpdateEndpoint(provider.providerId, provider.displayLabel, provider.sourceKind)
                    }
                    if (discovered.size >= 4) break
                }
                if (discovered.isNotEmpty()) {
                    dynamicProxiTokInstances.value = discovered
                }
                discovered.size
            }
        }.getOrDefault(0)
    }

    suspend fun loadPublicFeed(forceRefresh: Boolean = false): ProviderResult<FeedPage> {
        val cacheKey = "trending_public"
        if (!forceRefresh) {
            cache.getFeed(cacheKey)?.let { cached ->
                return ProviderResult.Success(
                    data = cached,
                    sourceKind = cached.sourceKind,
                    endpoint = "${cached.sourceEndpoint} (cache)",
                    fromCache = true
                )
            }
        }

        val attempts = buildFeedAttempts()
        val result = fallbackManager.executeWithFallback("trending_feed", attempts)
        if (result is ProviderResult.Success) {
            cache.putFeed(cacheKey, result.data)
        }
        return result
    }

    suspend fun loadTopicFeed(topic: String): ProviderResult<FeedPage> {
        val clean = topic.trim()
        if (clean.equals("Trending", ignoreCase = true) || clean.isBlank()) {
            return loadPublicFeed(forceRefresh = true)
        }
        val mode = if (clean.startsWith("@")) SearchMode.CREATOR else SearchMode.HASHTAG
        return when (val searchRes = searchPublic(clean, mode)) {
            is ProviderResult.Success -> ProviderResult.Success(
                data = FeedPage(
                    videos = searchRes.data,
                    sourceKind = searchRes.sourceKind,
                    sourceEndpoint = searchRes.endpoint,
                    noticeMessage = "Topic: $clean"
                ),
                sourceKind = searchRes.sourceKind,
                endpoint = searchRes.endpoint,
                fromCache = searchRes.fromCache
            )
            is ProviderResult.RateLimited -> searchRes
            is ProviderResult.AccessRestricted -> searchRes
            is ProviderResult.NetworkError -> searchRes
            is ProviderResult.ContentUnavailable -> searchRes
        }
    }

    suspend fun searchPublic(rawQuery: String, mode: SearchMode): ProviderResult<List<PublicVideo>> {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            return ProviderResult.ContentUnavailable("Enter a public video link, #hashtag, or @creator.")
        }

        val effectiveMode = if (mode == SearchMode.AUTO) detectMode(query) else mode
        val cacheKey = "${effectiveMode.name}:$query"
        cache.getSearch(cacheKey)?.let { cached ->
            return ProviderResult.Success(
                data = cached,
                sourceKind = cached.firstOrNull()?.sourceKind ?: VideoSourceKind.TIKTOK_PUBLIC_ADAPTER,
                endpoint = "Ephemeral Cache",
                fromCache = true
            )
        }

        val result: ProviderResult<List<PublicVideo>> = when (effectiveMode) {
            SearchMode.LINK -> {
                when (val single = resolveVideoLink(query)) {
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
            SearchMode.HASHTAG -> {
                val attempts = buildSelfHostedOnly().map { provider ->
                    ProviderEndpointCall(provider.providerId, provider.displayLabel, provider.sourceKind) {
                        provider.searchHashtag(query)
                    }
                } + listOfNotNull(
                    tikWmProvider?.let {
                        ProviderEndpointCall(it.providerId, "Public Stream · TikWM", it.sourceKind) {
                            it.searchHashtag(query)
                        }
                    }
                ) + dynamicProxiTokInstances.value.map { provider ->
                    ProviderEndpointCall(provider.providerId, provider.displayLabel, provider.sourceKind) {
                        provider.searchHashtag(query)
                    }
                } + ProviderEndpointCall(
                    publicContentAdapter.providerId,
                    "TikTok Public Tag",
                    publicContentAdapter.sourceKind
                ) {
                    publicContentAdapter.searchHashtag(query)
                }
                fallbackManager.executeWithFallback("search_hashtag", attempts)
            }
            SearchMode.CREATOR -> {
                val attempts = buildSelfHostedOnly().map { provider ->
                    ProviderEndpointCall(provider.providerId, provider.displayLabel, provider.sourceKind) {
                        provider.searchCreator(query)
                    }
                } + listOfNotNull(
                    tikWmProvider?.let {
                        ProviderEndpointCall(it.providerId, "Public Stream · TikWM", it.sourceKind) {
                            it.searchCreator(query)
                        }
                    }
                ) + dynamicProxiTokInstances.value.map { provider ->
                    ProviderEndpointCall(provider.providerId, provider.displayLabel, provider.sourceKind) {
                        provider.searchCreator(query)
                    }
                } + ProviderEndpointCall(
                    publicContentAdapter.providerId,
                    "TikTok Public Creator",
                    publicContentAdapter.sourceKind
                ) {
                    publicContentAdapter.searchCreator(query)
                }
                fallbackManager.executeWithFallback("search_creator", attempts)
            }
            SearchMode.KEYWORD, SearchMode.AUTO -> {
                val attempts = buildSelfHostedOnly().map { provider ->
                    ProviderEndpointCall(provider.providerId, provider.displayLabel, provider.sourceKind) {
                        provider.searchVideos(query)
                    }
                } + listOfNotNull(
                    tikWmProvider?.let {
                        ProviderEndpointCall(it.providerId, "Public Stream · TikWM", it.sourceKind) {
                            it.searchVideos(query)
                        }
                    }
                ) + dynamicProxiTokInstances.value.map { provider ->
                    ProviderEndpointCall(provider.providerId, provider.displayLabel, provider.sourceKind) {
                        provider.searchVideos(query)
                    }
                } + ProviderEndpointCall(
                    publicContentAdapter.providerId,
                    "TikTok Public Search",
                    publicContentAdapter.sourceKind
                ) {
                    publicContentAdapter.searchVideos(query)
                }
                fallbackManager.executeWithFallback("search_keyword", attempts)
            }
        }

        if (result is ProviderResult.Success && result.data.isNotEmpty()) {
            cache.putSearch(cacheKey, result.data)
        }
        return result
    }

    suspend fun resolveVideoLink(urlOrId: String): ProviderResult<PublicVideo> {
        val parsed = TikTokLinkParser.parse(urlOrId)
        parsed?.let {
            cache.getVideo(it.videoId)?.let { cached ->
                return ProviderResult.Success(cached, cached.sourceKind, cached.sourceLabel, fromCache = true)
            }
        }

        val attempts = buildSelfHostedOnly().map { provider ->
            ProviderEndpointCall(provider.providerId, provider.displayLabel, provider.sourceKind) {
                provider.resolveVideoByUrlOrId(urlOrId)
            }
        } + listOfNotNull(
            tikWmProvider?.let {
                ProviderEndpointCall(it.providerId, "Public Stream · TikWM", it.sourceKind) {
                    it.resolveVideoByUrlOrId(urlOrId)
                }
            }
        ) + dynamicProxiTokInstances.value.map { provider ->
            ProviderEndpointCall(provider.providerId, provider.displayLabel, provider.sourceKind) {
                provider.resolveVideoByUrlOrId(urlOrId)
            }
        } + ProviderEndpointCall(
            oEmbedVideoProvider.providerId,
            "TikTok Official oEmbed",
            oEmbedVideoProvider.sourceKind
        ) {
            oEmbedVideoProvider.resolveVideoByUrlOrId(urlOrId)
        }

        val result = fallbackManager.executeWithFallback("resolve_video", attempts)
        if (result is ProviderResult.Success) {
            cache.putVideo(result.data)
        }
        return result
    }

    suspend fun resolvePlayback(video: PublicVideo): ProviderResult<PlaybackTarget> {
        return playbackProvider.resolvePlaybackTarget(video)
    }

    suspend fun runHealthChecks(): List<EndpointHealth> {
        refreshCommunityProxiTokDirectory()
        return fallbackManager.runHealthChecks()
    }

    fun rotateEndpoints() {
        fallbackManager.rotateEndpointOrder()
    }

    fun resetBreakers() {
        fallbackManager.resetCircuitBreakers()
    }

    fun wipeEphemeralCache(context: Context?) {
        cache.wipeAll(context)
    }

    fun cacheEntryCount(): Int = cache.entryCount()

    private fun buildSelfHostedOnly(): List<ProxiTokProvider> {
        val customUrl = _privacyConfig.value.customProxiTokBaseUrl
        return if (customUrl.isNotBlank() && PrivacyPolicyEnforcer.isSafeHttpsUrl(customUrl)) {
            listOf(selfHostedFactory(customUrl))
        } else {
            emptyList()
        }
    }

    private fun buildFeedAttempts(): List<ProviderEndpointCall<FeedPage>> {
        val selfHosted = buildSelfHostedOnly().map { provider ->
            ProviderEndpointCall(provider.providerId, provider.displayLabel, provider.sourceKind) {
                provider.fetchTrendingFeed()
            }
        }
        val tikWmCall = tikWmProvider?.let {
            listOf(
                ProviderEndpointCall(it.providerId, "Public Stream · TikWM", it.sourceKind) {
                    it.fetchTrendingFeed()
                }
            )
        }.orEmpty()
        val proxiAttempts = dynamicProxiTokInstances.value.map { provider ->
            ProviderEndpointCall(provider.providerId, provider.displayLabel, provider.sourceKind) {
                provider.fetchTrendingFeed()
            }
        }
        val officialFallback = ProviderEndpointCall(
            publicContentAdapter.providerId,
            "TikTok Official oEmbed + Embed v3",
            publicContentAdapter.sourceKind
        ) {
            publicContentAdapter.fetchTrendingFeed()
        }
        return selfHosted + tikWmCall + proxiAttempts + officialFallback
    }

    private fun detectMode(query: String): SearchMode {
        return when {
            query.contains("tiktok.com", ignoreCase = true) || TikTokLinkParser.parse(query) != null -> SearchMode.LINK
            query.startsWith("#") -> SearchMode.HASHTAG
            query.startsWith("@") -> SearchMode.CREATOR
            else -> SearchMode.KEYWORD
        }
    }

    companion object {
        fun createDefault(): HybridTikTokRepository {
            val okHttp = PrivacyPolicyEnforcer.createPrivacyOkHttpClient()
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            val oEmbedService = Retrofit.Builder()
                .baseUrl("https://www.tiktok.com/")
                .client(okHttp)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(TikTokOEmbedService::class.java)

            val defaultProxiTokEndpoints = listOf(
                Triple("proxitok_pablo", "https://proxitok.pabloferreiro.es", "ProxiTok · pabloferreiro.es"),
                Triple("proxitok_adminforge", "https://tok.adminforge.de", "ProxiTok · adminforge.de")
            )

            val proxiProviders = defaultProxiTokEndpoints.map { (id, url, label) ->
                ProxiTokProvider(
                    providerId = id,
                    baseUrl = url,
                    displayLabel = label,
                    sourceKind = VideoSourceKind.PROXITOK_PUBLIC,
                    okHttpClient = okHttp
                )
            }

            val tikWmProvider = TikWmPublicProvider(okHttp, moshi)
            val oEmbedProvider = TikTokOEmbedVideoProvider(oEmbedService, okHttp)
            val publicAdapter = TikTokPublicContentAdapter(oEmbedProvider, okHttp)
            val fallbackManager = TikTokFallbackManagerImpl(
                okHttpClient = okHttp,
                retryPolicy = RetryBackoffPolicy(maxAttempts = 1),
                healthCheckTargets = {
                    listOf(tikWmProvider.providerId to "https://www.tikwm.com/api/feed/list?region=ID&count=1") +
                        defaultProxiTokEndpoints.map { (id, url, _) -> id to "$url/trending" } +
                        listOf(
                            oEmbedProvider.providerId to "https://www.tiktok.com/oembed?url=https://www.tiktok.com/@tiktok/video/7106594312292453675",
                            publicAdapter.providerId to "https://www.tiktok.com/embed/v3/7106594312292453675"
                        )
                }
            )

            return HybridTikTokRepository(
                cache = EphemeralMetadataCache(),
                okHttpClient = okHttp,
                fallbackManager = fallbackManager,
                tikWmProvider = tikWmProvider,
                proxiTokInstances = proxiProviders,
                oEmbedVideoProvider = oEmbedProvider,
                publicContentAdapter = publicAdapter,
                playbackProvider = TikTokPlaybackProviderImpl(),
                selfHostedFactory = { customUrl ->
                    ProxiTokProvider(
                        providerId = "proxitok_self_hosted",
                        baseUrl = customUrl,
                        displayLabel = "Self-Hosted (${customUrl.removePrefix("https://")})",
                        sourceKind = VideoSourceKind.PROXITOK_SELF_HOSTED,
                        okHttpClient = okHttp
                    )
                }
            )
        }
    }
}
