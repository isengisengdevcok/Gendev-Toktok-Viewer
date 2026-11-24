package com.gendev.tiktokviewer.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gendev.tiktokviewer.ai.AiVibeState
import com.gendev.tiktokviewer.ai.AiVibeSuggestion
import com.gendev.tiktokviewer.ai.AiVideoInsightState
import com.gendev.tiktokviewer.ai.GeminiScrollAssistant
import com.gendev.tiktokviewer.data.repository.HybridTikTokRepository
import com.gendev.tiktokviewer.domain.model.EndpointHealth
import com.gendev.tiktokviewer.domain.model.FeedPage
import com.gendev.tiktokviewer.domain.model.PlaybackMode
import com.gendev.tiktokviewer.domain.model.PrivacyConfig
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.SearchMode
import com.gendev.tiktokviewer.ui.theme.GendevThemePreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class ViewerEngineMode {
    GUEST_SCROLL,
    NATIVE_STREAM
}

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

private const val UI_PREFS_NAME = "gendev_ui_prefs"
private const val PREF_THEME_ID = "theme_preset_id"
private const val PREF_ULTRA_CLEAN = "ultra_clean_mode"

class ViewerViewModel(
    private val repository: HybridTikTokRepository,
    private val aiAssistant: GeminiScrollAssistant? = null,
    private val startInUnifiedScroll: Boolean = false
) : ViewModel() {

    private val _viewerEngineMode = MutableStateFlow(
        if (startInUnifiedScroll) ViewerEngineMode.GUEST_SCROLL else ViewerEngineMode.NATIVE_STREAM
    )
    val viewerEngineMode: StateFlow<ViewerEngineMode> = _viewerEngineMode.asStateFlow()

    private val _guestUrl = MutableStateFlow("https://www.tiktok.com/foryou")
    val guestUrl: StateFlow<String> = _guestUrl.asStateFlow()

    private val _guestReloadToken = MutableStateFlow(0)
    val guestReloadToken: StateFlow<Int> = _guestReloadToken.asStateFlow()

    private val _feedState = MutableStateFlow<FeedUiState>(FeedUiState.Loading("Memuat video..."))
    val feedState: StateFlow<FeedUiState> = _feedState.asStateFlow()

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _currentVideoIndex = MutableStateFlow(0)
    val currentVideoIndex: StateFlow<Int> = _currentVideoIndex.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _selectedTopic = MutableStateFlow("#FYP")
    val selectedTopic: StateFlow<String> = _selectedTopic.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _isResilienceOpen = MutableStateFlow(false)
    val isResilienceOpen: StateFlow<Boolean> = _isResilienceOpen.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _isAiSheetOpen = MutableStateFlow(false)
    val isAiSheetOpen: StateFlow<Boolean> = _isAiSheetOpen.asStateFlow()

    private val _themePreset = MutableStateFlow(GendevThemePreset.AMOLED_DARK)
    val themePreset: StateFlow<GendevThemePreset> = _themePreset.asStateFlow()

    private val _ultraCleanMode = MutableStateFlow(false)
    val ultraCleanMode: StateFlow<Boolean> = _ultraCleanMode.asStateFlow()

    private val _aiVibeState = MutableStateFlow<AiVibeState>(AiVibeState.Idle)
    val aiVibeState: StateFlow<AiVibeState> = _aiVibeState.asStateFlow()

    private val _aiInsightState = MutableStateFlow<AiVideoInsightState>(AiVideoInsightState.Idle)
    val aiInsightState: StateFlow<AiVideoInsightState> = _aiInsightState.asStateFlow()

    private val _cacheCount = MutableStateFlow(0)
    val cacheCount: StateFlow<Int> = _cacheCount.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    val endpointHealth: StateFlow<List<EndpointHealth>> = repository.endpointHealthFlow
    val privacyConfig: StateFlow<PrivacyConfig> = repository.privacyConfig
    val okHttpClient = repository.okHttpClient
    val streamingOkHttpClient = repository.streamingOkHttpClient

    private var insightJob: Job? = null
    private var nextFeedCursor: String? = "1"
    private var isLoadingMoreFeed = false

    init {
        if (!startInUnifiedScroll) {
            loadFeed(forceRefresh = false)
        }
    }

    fun loadUiPreferences(context: Context) {
        val prefs = context.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        _themePreset.value = GendevThemePreset.fromId(prefs.getString(PREF_THEME_ID, null))
        _ultraCleanMode.value = prefs.getBoolean(PREF_ULTRA_CLEAN, false)
    }

    fun selectThemePreset(preset: GendevThemePreset, context: Context? = null) {
        _themePreset.value = preset
        context?.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(PREF_THEME_ID, preset.id)
            ?.apply()
    }

    fun setUltraCleanMode(enabled: Boolean, context: Context? = null) {
        _ultraCleanMode.value = enabled
        context?.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(PREF_ULTRA_CLEAN, enabled)
            ?.apply()
    }

    fun openSettings() {
        _isPaused.value = true
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
        _isPaused.value = false
    }

    fun switchEngineMode(mode: ViewerEngineMode) {
        _viewerEngineMode.value = mode
        _isPaused.value = false
        if (mode == ViewerEngineMode.GUEST_SCROLL) {
            _guestUrl.value = repository.buildGuestScrollUrl(_selectedTopic.value)
        } else if (_feedState.value !is FeedUiState.Ready) {
            loadFeed(forceRefresh = true)
        }
    }

    fun openGuestScroll(topicOrQuery: String = _selectedTopic.value) {
        _guestUrl.value = repository.buildGuestScrollUrl(topicOrQuery)
        _guestReloadToken.value += 1
        _viewerEngineMode.value = ViewerEngineMode.GUEST_SCROLL
        _isPaused.value = false
        _isSearchOpen.value = false
        _isAiSheetOpen.value = false
        _isSettingsOpen.value = false
    }

    fun reloadGuestScroll() {
        _isPaused.value = false
        _guestReloadToken.value += 1
        _isSettingsOpen.value = false
    }

    fun reloadUnifiedFeed() {
        refreshFeed()
    }

    fun refreshFeed() {
        _isSettingsOpen.value = false
        _isPaused.value = false
        if (_viewerEngineMode.value == ViewerEngineMode.GUEST_SCROLL) {
            val toggledGuestUrl = if (_guestUrl.value.contains("/foryou", ignoreCase = true)) {
                "https://www.tiktok.com/explore"
            } else {
                "https://www.tiktok.com/foryou"
            }
            viewModelScope.launch {
                val upgrade = withTimeoutOrNull(2_500L) {
                    repository.loadPublicFeed(forceRefresh = true)
                }
                if (upgrade is ProviderResult.Success && upgrade.data.videos.isNotEmpty()) {
                    applyFeedResult(upgrade, autoFallbackToGuestOnFailure = false)
                } else {
                    _guestUrl.value = toggledGuestUrl
                    _guestReloadToken.value += 1
                    _viewerEngineMode.value = ViewerEngineMode.GUEST_SCROLL
                }
            }
            return
        }

        val cursor = nextFeedCursor ?: "1"
        viewModelScope.launch {
            _feedState.value = FeedUiState.Loading("Memuat video baru...")
            val res = withTimeoutOrNull(4_500L) {
                repository.loadMorePublicFeed(cursor)
            }
            if (res is ProviderResult.Success && res.data.videos.isNotEmpty()) {
                applyFeedResult(res, autoFallbackToGuestOnFailure = true)
            } else {
                nextFeedCursor = ((cursor.toIntOrNull() ?: 1) + 1).toString()
                val fallbackRes = withTimeoutOrNull(4_200L) {
                    repository.loadPublicFeed(forceRefresh = true)
                }
                if (fallbackRes == null) {
                    activateSeamlessGuestFallback("Beralih cepat ke Mode Scroll Tanpa Login")
                } else {
                    applyFeedResult(fallbackRes, autoFallbackToGuestOnFailure = true)
                }
            }
        }
    }

    fun loadFeed(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isPaused.value = false
            _feedState.value = FeedUiState.Loading("Memuat video...")
            val res = withTimeoutOrNull(4_200L) {
                repository.loadPublicFeed(forceRefresh)
            }
            if (res == null) {
                activateSeamlessGuestFallback("Beralih cepat ke Mode Scroll Tanpa Login")
            } else {
                applyFeedResult(res, autoFallbackToGuestOnFailure = true)
            }
        }
    }

    fun selectTopic(topic: String) {
        _selectedTopic.value = topic
        _isPaused.value = false
        if (_viewerEngineMode.value == ViewerEngineMode.GUEST_SCROLL) {
            _guestUrl.value = repository.buildGuestScrollUrl(topic)
            _guestReloadToken.value += 1
            return
        }
        viewModelScope.launch {
            _feedState.value = FeedUiState.Loading("Memuat $topic secara anonim...")
            val res = repository.loadTopicFeed(topic)
            applyFeedResult(res, autoFallbackToGuestOnFailure = true)
        }
    }

    private fun applyFeedResult(
        res: ProviderResult<FeedPage>,
        autoFallbackToGuestOnFailure: Boolean
    ) {
        when (res) {
            is ProviderResult.Success -> {
                _currentVideoIndex.value = 0
                _isPaused.value = false
                nextFeedCursor = res.data.nextCursor ?: "1"
                isLoadingMoreFeed = false
                _viewerEngineMode.value = ViewerEngineMode.NATIVE_STREAM
                _feedState.value = FeedUiState.Ready(
                    videos = res.data.videos,
                    activeSourceLabel = res.endpoint,
                    noticeMessage = res.data.noticeMessage
                )
                _cacheCount.value = repository.cacheEntryCount()
            }
            is ProviderResult.RateLimited -> {
                if (autoFallbackToGuestOnFailure) {
                    activateSeamlessGuestFallback("Stream dibatasi sementara — otomatis beralih ke Mode Scroll")
                } else {
                    _feedState.value = FeedUiState.Error(
                        title = "Rate Limit Cooldown (HTTP 429)",
                        message = res.message,
                        isRateLimited = true,
                        retryAfterSeconds = res.retryAfterSeconds
                    )
                }
            }
            is ProviderResult.AccessRestricted -> {
                if (autoFallbackToGuestOnFailure && !res.isCaptcha) {
                    activateSeamlessGuestFallback("Otomatis beralih ke Mode Scroll Tanpa Login")
                } else {
                    _feedState.value = FeedUiState.Error(
                        title = if (res.isCaptcha) "Verification Required (CAPTCHA)" else "Access Restricted (HTTP ${res.statusCode})",
                        message = res.message,
                        isCaptcha = res.isCaptcha,
                        isForbidden = !res.isCaptcha
                    )
                }
            }
            is ProviderResult.NetworkError -> {
                if (autoFallbackToGuestOnFailure) {
                    activateSeamlessGuestFallback("Stream API sibuk — otomatis beralih ke Mode Scroll")
                } else {
                    _feedState.value = FeedUiState.Error(
                        title = "Koneksi Server Bermasalah",
                        message = "${res.message}. Ketuk Switch atau gunakan Mode Scroll."
                    )
                }
            }
            is ProviderResult.ContentUnavailable -> {
                if (autoFallbackToGuestOnFailure) {
                    activateSeamlessGuestFallback("Beralih ke Mode Scroll Tanpa Login")
                } else {
                    _feedState.value = FeedUiState.Error(
                        title = "Stream Tidak Tersedia",
                        message = res.message
                    )
                }
            }
        }
    }

    private fun activateSeamlessGuestFallback(notice: String) {
        _guestUrl.value = repository.buildGuestScrollUrl(_selectedTopic.value)
        _guestReloadToken.value += 1
        _viewerEngineMode.value = ViewerEngineMode.GUEST_SCROLL
        _bannerMessage.value = notice
        _feedState.value = FeedUiState.Error(
            title = "Stream API Sedang Sibuk",
            message = notice
        )
    }

    fun onPageSelected(index: Int) {
        if (index >= 0) {
            _currentVideoIndex.value = index
            _isPaused.value = false
            _aiInsightState.value = AiVideoInsightState.Idle

            val ready = _feedState.value as? FeedUiState.Ready
            if (_viewerEngineMode.value == ViewerEngineMode.NATIVE_STREAM &&
                ready != null &&
                index >= (ready.videos.size - 4).coerceAtLeast(0) &&
                !isLoadingMoreFeed
            ) {
                loadMoreFeed()
            }
        }
    }

    private fun loadMoreFeed() {
        val currentCursor = nextFeedCursor ?: "1"
        isLoadingMoreFeed = true
        viewModelScope.launch {
            try {
                when (val res = repository.loadMorePublicFeed(currentCursor)) {
                    is ProviderResult.Success -> {
                        val currentReady = _feedState.value as? FeedUiState.Ready ?: return@launch
                        val existingIds = currentReady.videos.mapTo(HashSet()) { it.id }
                        val fresh = res.data.videos.filter { it.id !in existingIds }
                        nextFeedCursor = res.data.nextCursor ?: ((currentCursor.toIntOrNull() ?: 1) + 1).toString()
                        if (fresh.isNotEmpty()) {
                            _feedState.value = currentReady.copy(
                                videos = currentReady.videos + fresh
                            )
                        }
                    }
                    else -> {
                        nextFeedCursor = ((currentCursor.toIntOrNull() ?: 1) + 1).toString()
                    }
                }
            } finally {
                isLoadingMoreFeed = false
            }
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun togglePlayPause() {
        _isPaused.value = !_isPaused.value
    }

    fun openSearch() {
        _isSettingsOpen.value = false
        _isPaused.value = true
        _isSearchOpen.value = true
    }

    fun closeSearch() {
        _isSearchOpen.value = false
        _isPaused.value = false
    }

    fun openResilienceSheet() {
        _isSettingsOpen.value = false
        _isPaused.value = true
        _isResilienceOpen.value = true
    }

    fun closeResilienceSheet() {
        _isResilienceOpen.value = false
        _isPaused.value = false
    }

    fun openAiSheet() {
        _isSettingsOpen.value = false
        _isPaused.value = true
        _isAiSheetOpen.value = true
    }

    fun closeAiSheet() {
        _isAiSheetOpen.value = false
        _isPaused.value = false
    }

    fun askAiVibe(prompt: String) {
        val assistant = aiAssistant ?: run {
            _aiVibeState.value = AiVibeState.Error("Gemini AI belum dikonfigurasi pada build ini.")
            return
        }
        val clean = prompt.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            _aiVibeState.value = AiVibeState.Loading(clean)
            try {
                val suggestion = assistant.buildVibeDiscovery(clean)
                _aiVibeState.value = AiVibeState.Ready(suggestion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiVibeState.value = AiVibeState.Error(
                    e.localizedMessage ?: "Gagal memproses permintaan Gemini AI."
                )
            }
        }
    }

    fun applyAiVibeToGuestScroll(suggestion: AiVibeSuggestion) {
        _selectedTopic.value = "#${suggestion.guestTopicTag}"
        openGuestScroll("#${suggestion.guestTopicTag}")
    }

    fun applyAiVibeToStream(suggestion: AiVibeSuggestion) {
        _selectedTopic.value = "#${suggestion.guestTopicTag}"
        _isAiSheetOpen.value = false
        _viewerEngineMode.value = ViewerEngineMode.NATIVE_STREAM
        viewModelScope.launch {
            _feedState.value = FeedUiState.Loading("Gemini AI: ${suggestion.vibeLabel}...")
            when (val res = repository.searchPublic(suggestion.searchQuery, SearchMode.KEYWORD)) {
                is ProviderResult.Success -> {
                    _currentVideoIndex.value = 0
                    _isPaused.value = false
                    _feedState.value = FeedUiState.Ready(
                        videos = res.data,
                        activeSourceLabel = "${res.endpoint} · AI",
                        noticeMessage = "Dikurasi AI: ${suggestion.vibeLabel}"
                    )
                }
                else -> {
                    openGuestScroll("#${suggestion.guestTopicTag}")
                }
            }
        }
    }

    fun summarizeCurrentVideo(video: PublicVideo) {
        val assistant = aiAssistant ?: run {
            _aiInsightState.value = AiVideoInsightState.Error("Gemini AI belum dikonfigurasi.")
            return
        }
        insightJob?.cancel()
        insightJob = viewModelScope.launch {
            assistant.streamVideoInsight(video).collect { state ->
                _aiInsightState.value = state
            }
        }
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
        _isPaused.value = true
        _isSearchOpen.value = true
        performSearch(clean, SearchMode.HASHTAG)
    }

    fun selectSearchVideo(video: PublicVideo, contextList: List<PublicVideo>) {
        val ordered = listOf(video) + contextList.filter { it.id != video.id }
        _viewerEngineMode.value = ViewerEngineMode.NATIVE_STREAM
        _feedState.value = FeedUiState.Ready(
            videos = ordered,
            activeSourceLabel = video.sourceLabel,
            noticeMessage = "Public discovery stream · No history saved"
        )
        _currentVideoIndex.value = 0
        _isPaused.value = false
        _isSearchOpen.value = false
    }

    fun openDeepLink(url: String) {
        viewModelScope.launch {
            _viewerEngineMode.value = ViewerEngineMode.NATIVE_STREAM
            _feedState.value = FeedUiState.Loading("Membuka tautan publik secara anonim...")
            when (val res = repository.resolveVideoLink(url)) {
                is ProviderResult.Success -> {
                    _feedState.value = FeedUiState.Ready(
                        videos = listOf(res.data),
                        activeSourceLabel = res.endpoint,
                        noticeMessage = "Opened direct public link anonymously"
                    )
                    _currentVideoIndex.value = 0
                    _isPaused.value = false
                }
                is ProviderResult.RateLimited,
                is ProviderResult.AccessRestricted,
                is ProviderResult.NetworkError,
                is ProviderResult.ContentUnavailable -> {
                    openGuestScroll(url)
                }
            }
        }
    }

    fun fallbackVideoToEmbed(videoId: String, reason: String) {
        val current = _feedState.value as? FeedUiState.Ready ?: return
        val updated = current.videos.map { video ->
            if (video.id == videoId && video.playbackMode == PlaybackMode.EXO_DIRECT_STREAM) {
                video.copy(
                    directMediaUrl = null,
                    playbackMode = PlaybackMode.ISOLATED_EMBED_WEBVIEW,
                    sourceLabel = "TikTok Official Embed v3 (Fallback)"
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
        selectTopic(_selectedTopic.value)
    }

    fun runHealthChecks() {
        viewModelScope.launch {
            _bannerMessage.value = "Syncing ProxiTok directory & running health checks..."
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

    class Factory(
        private val repository: HybridTikTokRepository,
        private val aiAssistant: GeminiScrollAssistant? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ViewerViewModel(repository, aiAssistant) as T
        }
    }
}
