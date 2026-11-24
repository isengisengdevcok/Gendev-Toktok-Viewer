package com.gendev.tiktokviewer.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gendev.tiktokviewer.data.repository.HybridTikTokRepository
import com.gendev.tiktokviewer.domain.model.EndpointHealth
import com.gendev.tiktokviewer.domain.model.PlaybackMode
import com.gendev.tiktokviewer.domain.model.PrivacyConfig
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.SearchMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FeedUiState {
    data class Loading(val sourceHint: String) : FeedUiState
    data class Ready(
        val videos: List<PublicVideo>,
        val activeSourceLabel: String,
        val noticeMessage: String? = null
    ) : FeedUiState

    data class Error(
        val title: String,
        val message: String,
        val isCaptcha: Boolean = false,
        val isRateLimited: Boolean = false,
        val isForbidden: Boolean = false,
        val retryAfterSeconds: Long? = null
    ) : FeedUiState
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Searching(val query: String) : SearchUiState
    data class Results(
        val query: String,
        val videos: List<PublicVideo>,
        val sourceLabel: String
    ) : SearchUiState

    data class Error(
        val title: String,
        val message: String,
        val isCaptcha: Boolean = false,
        val isRateLimited: Boolean = false
    ) : SearchUiState
}

class ViewerViewModel(
    private val repository: HybridTikTokRepository
) : ViewModel() {

    private val _feedState = MutableStateFlow<FeedUiState>(FeedUiState.Loading("ProxiTok · Public Nodes"))
    val feedState: StateFlow<FeedUiState> = _feedState.asStateFlow()

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _currentVideoIndex = MutableStateFlow(0)
    val currentVideoIndex: StateFlow<Int> = _currentVideoIndex.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _isResilienceOpen = MutableStateFlow(false)
    val isResilienceOpen: StateFlow<Boolean> = _isResilienceOpen.asStateFlow()

    private val _cacheCount = MutableStateFlow(0)
    val cacheCount: StateFlow<Int> = _cacheCount.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    val endpointHealth: StateFlow<List<EndpointHealth>> = repository.endpointHealthFlow
    val privacyConfig: StateFlow<PrivacyConfig> = repository.privacyConfig
    val okHttpClient = repository.okHttpClient

    init {
        loadFeed(forceRefresh = false)
    }

    fun loadFeed(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _feedState.value = FeedUiState.Loading("Connecting anonymously via HTTPS...")
            when (val res = repository.loadPublicFeed(forceRefresh)) {
                is ProviderResult.Success -> {
                    _currentVideoIndex.value = 0
                    _feedState.value = FeedUiState.Ready(
                        videos = res.data.videos,
                        activeSourceLabel = res.endpoint,
                        noticeMessage = res.data.noticeMessage
                    )
                    _cacheCount.value = repository.cacheEntryCount()
                }
                is ProviderResult.RateLimited -> {
                    _feedState.value = FeedUiState.Error(
                        title = "Rate Limit Cooldown (HTTP 429)",
                        message = res.message,
                        isRateLimited = true,
                        retryAfterSeconds = res.retryAfterSeconds
                    )
                }
                is ProviderResult.AccessRestricted -> {
                    _feedState.value = FeedUiState.Error(
                        title = if (res.isCaptcha) "Verification Required (CAPTCHA)" else "Access Restricted (HTTP ${res.statusCode})",
                        message = res.message,
                        isCaptcha = res.isCaptcha,
                        isForbidden = !res.isCaptcha
                    )
                }
                is ProviderResult.NetworkError -> {
                    _feedState.value = FeedUiState.Error(
                        title = "Upstream Unreachable",
                        message = "${res.message}. Check connection or switch to another public source."
                    )
                }
                is ProviderResult.ContentUnavailable -> {
                    _feedState.value = FeedUiState.Error(
                        title = "No Public Stream Available",
                        message = res.message
                    )
                }
            }
        }
    }

    fun onPageSelected(index: Int) {
        if (index >= 0) {
            _currentVideoIndex.value = index
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun openSearch() {
        _isSearchOpen.value = true
    }

    fun closeSearch() {
        _isSearchOpen.value = false
    }

    fun openResilienceSheet() {
        _isResilienceOpen.value = true
    }

    fun closeResilienceSheet() {
        _isResilienceOpen.value = false
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun performSearch(query: String, mode: SearchMode) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _searchState.value = SearchUiState.Searching(trimmed)
            when (val res = repository.searchPublic(trimmed, mode)) {
                is ProviderResult.Success -> {
                    _searchState.value = SearchUiState.Results(
                        query = trimmed,
                        videos = res.data,
                        sourceLabel = res.endpoint
                    )
                    _cacheCount.value = repository.cacheEntryCount()
                }
                is ProviderResult.RateLimited -> {
                    _searchState.value = SearchUiState.Error(
                        title = "Cooldown Active (HTTP 429)",
                        message = res.message,
                        isRateLimited = true
                    )
                }
                is ProviderResult.AccessRestricted -> {
                    _searchState.value = SearchUiState.Error(
                        title = if (res.isCaptcha) "CAPTCHA Challenge" else "Restricted (HTTP ${res.statusCode})",
                        message = res.message,
                        isCaptcha = res.isCaptcha
                    )
                }
                is ProviderResult.NetworkError -> {
                    _searchState.value = SearchUiState.Error(
                        title = "Search Connection Error",
                        message = res.message
                    )
                }
                is ProviderResult.ContentUnavailable -> {
                    _searchState.value = SearchUiState.Error(
                        title = "No Public Results",
                        message = res.message
                    )
                }
            }
        }
    }

    fun openHashtagFromFeed(hashtag: String) {
        val clean = "#${hashtag.removePrefix("#")}"
        _isSearchOpen.value = true
        performSearch(clean, SearchMode.HASHTAG)
    }

    fun selectSearchVideo(video: PublicVideo, contextList: List<PublicVideo>) {
        val ordered = listOf(video) + contextList.filter { it.id != video.id }
        _feedState.value = FeedUiState.Ready(
            videos = ordered,
            activeSourceLabel = video.sourceLabel,
            noticeMessage = "Public discovery stream · No history saved"
        )
        _currentVideoIndex.value = 0
        _isSearchOpen.value = false
    }

    fun openDeepLink(url: String) {
        viewModelScope.launch {
            _feedState.value = FeedUiState.Loading("Resolving public link anonymously...")
            when (val res = repository.resolveVideoLink(url)) {
                is ProviderResult.Success -> {
                    _feedState.value = FeedUiState.Ready(
                        videos = listOf(res.data),
                        activeSourceLabel = res.endpoint,
                        noticeMessage = "Opened direct public link anonymously"
                    )
                    _currentVideoIndex.value = 0
                }
                is ProviderResult.RateLimited -> {
                    _feedState.value = FeedUiState.Error(
                        title = "Cooldown Active (HTTP 429)",
                        message = res.message,
                        isRateLimited = true,
                        retryAfterSeconds = res.retryAfterSeconds
                    )
                }
                is ProviderResult.AccessRestricted -> {
                    _feedState.value = FeedUiState.Error(
                        title = if (res.isCaptcha) "CAPTCHA Challenge" else "Restricted (HTTP ${res.statusCode})",
                        message = res.message,
                        isCaptcha = res.isCaptcha,
                        isForbidden = !res.isCaptcha
                    )
                }
                is ProviderResult.NetworkError -> {
                    _feedState.value = FeedUiState.Error("Link Resolution Error", res.message)
                }
                is ProviderResult.ContentUnavailable -> {
                    _feedState.value = FeedUiState.Error("Unsupported or Unavailable Link", res.message)
                }
            }
        }
    }

    /**
     * Automatically downgrades a direct Media3 stream to Official TikTok Embed v2 if the direct CDN URL fails/blocks.
     */
    fun fallbackVideoToEmbed(videoId: String, reason: String) {
        val current = _feedState.value as? FeedUiState.Ready ?: return
        val updated = current.videos.map { video ->
            if (video.id == videoId && video.playbackMode == PlaybackMode.EXO_DIRECT_STREAM) {
                video.copy(
                    directMediaUrl = null,
                    playbackMode = PlaybackMode.ISOLATED_EMBED_WEBVIEW,
                    sourceLabel = "TikTok Official Embed v2 (Fallback)"
                )
            } else {
                video
            }
        }
        _feedState.value = current.copy(
            videos = updated,
            noticeMessage = reason
        )
    }

    fun switchSourceAndRetry() {
        repository.rotateEndpoints()
        _bannerMessage.value = "Rotated to next healthy public endpoint"
        loadFeed(forceRefresh = true)
    }

    fun runHealthChecks() {
        viewModelScope.launch {
            _bannerMessage.value = "Running HTTPS health probes..."
            repository.runHealthChecks()
            _bannerMessage.value = "Endpoint health checks updated"
        }
    }

    fun resetCircuitBreakers() {
        repository.resetBreakers()
        _bannerMessage.value = "All circuit breakers reset"
    }

    fun updateSelfHostedProxiTok(url: String): Boolean {
        val valid = repository.updateCustomProxiTokUrl(url)
        _bannerMessage.value = if (valid) {
            if (url.isBlank()) "Self-hosted proxy cleared" else "Self-hosted HTTPS proxy saved for session"
        } else {
            "Invalid URL: HTTPS is strictly required"
        }
        return valid
    }

    fun wipeEphemeralCache(context: Context? = null) {
        repository.wipeEphemeralCache(context)
        _cacheCount.value = 0
        _bannerMessage.value = "Ephemeral cache & WebView data wiped"
    }

    class Factory(private val repository: HybridTikTokRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ViewerViewModel(repository) as T
        }
    }
}
