package com.gendev.tiktokviewer.domain.provider

import com.gendev.tiktokviewer.domain.model.EndpointHealth
import com.gendev.tiktokviewer.domain.model.FeedPage
import com.gendev.tiktokviewer.domain.model.PlaybackTarget
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import kotlinx.coroutines.flow.StateFlow

interface TikTokFeedProvider {
    val providerId: String
    val sourceKind: VideoSourceKind
    suspend fun fetchTrendingFeed(cursor: String? = null): ProviderResult<FeedPage>
}

interface TikTokSearchProvider {
    val providerId: String
    val sourceKind: VideoSourceKind
    suspend fun searchVideos(query: String): ProviderResult<List<PublicVideo>>
    suspend fun searchHashtag(hashtag: String): ProviderResult<List<PublicVideo>>
    suspend fun searchCreator(username: String): ProviderResult<List<PublicVideo>>
}

interface TikTokVideoProvider {
    val providerId: String
    val sourceKind: VideoSourceKind
    suspend fun resolveVideoByUrlOrId(urlOrId: String): ProviderResult<PublicVideo>
}

interface TikTokPlaybackProvider {
    suspend fun resolvePlaybackTarget(video: PublicVideo): ProviderResult<PlaybackTarget>
}

data class ProviderEndpointCall<T>(
    val endpointId: String,
    val label: String,
    val sourceKind: VideoSourceKind,
    val block: suspend () -> ProviderResult<T>
)

interface TikTokFallbackManager {
    val endpointHealthFlow: StateFlow<List<EndpointHealth>>
    suspend fun <T> executeWithFallback(
        operationName: String,
        attempts: List<ProviderEndpointCall<T>>
    ): ProviderResult<T>
    suspend fun runHealthChecks(): List<EndpointHealth>
    fun rotateEndpointOrder()
    fun resetCircuitBreakers()
    fun registerOrUpdateEndpoint(endpointId: String, label: String, sourceKind: VideoSourceKind)
    fun markEndpointResult(
        endpointId: String,
        label: String,
        sourceKind: VideoSourceKind,
        result: ProviderResult<*>,
        latencyMs: Long?
    )
}
