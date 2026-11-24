package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gendev.tiktokviewer.ai.FirebaseGeminiScrollAssistant
import com.gendev.tiktokviewer.ai.configureAppCheck
import com.gendev.tiktokviewer.data.repository.HybridTikTokRepository
import com.gendev.tiktokviewer.ui.screens.AiAssistantSheet
import com.gendev.tiktokviewer.ui.screens.PublicSearchSheet
import com.gendev.tiktokviewer.ui.screens.ResilienceShieldSheet
import com.gendev.tiktokviewer.ui.screens.SimpleSettingsSheet
import com.gendev.tiktokviewer.ui.screens.ViewerFeedScreen
import com.gendev.tiktokviewer.ui.theme.GendevTheme
import com.gendev.tiktokviewer.ui.viewmodel.FeedUiState
import com.gendev.tiktokviewer.ui.viewmodel.ViewerEngineMode
import com.gendev.tiktokviewer.ui.viewmodel.ViewerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ViewerViewModel by viewModels {
        ViewerViewModel.Factory(
            repository = HybridTikTokRepository.createDefault(),
            aiAssistant = FirebaseGeminiScrollAssistant()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        handleIncomingDeepLink(intent)

        setContent {
            val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                viewModel.loadUiPreferences(this@MainActivity)
            }
            GendevTheme(preset = themePreset) {
                GendevViewerApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingDeepLink(intent)
    }

    private fun handleIncomingDeepLink(intent: Intent?) {
        val dataUrl = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
        if (!dataUrl.isNullOrBlank() && dataUrl.startsWith("https://")) {
            viewModel.openDeepLink(dataUrl)
        }
    }
}

@Composable
fun GendevViewerApp(viewModel: ViewerViewModel) {
    val context = LocalContext.current
    val viewerEngineMode by viewModel.viewerEngineMode.collectAsStateWithLifecycle()
    val guestUrl by viewModel.guestUrl.collectAsStateWithLifecycle()
    val guestReloadToken by viewModel.guestReloadToken.collectAsStateWithLifecycle()
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentVideoIndex.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
    val ultraCleanMode by viewModel.ultraCleanMode.collectAsStateWithLifecycle()
    val isSearchOpen by viewModel.isSearchOpen.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val isResilienceOpen by viewModel.isResilienceOpen.collectAsStateWithLifecycle()
    val isAiSheetOpen by viewModel.isAiSheetOpen.collectAsStateWithLifecycle()
    val aiVibeState by viewModel.aiVibeState.collectAsStateWithLifecycle()
    val aiInsightState by viewModel.aiInsightState.collectAsStateWithLifecycle()
    val endpointHealth by viewModel.endpointHealth.collectAsStateWithLifecycle()
    val privacyConfig by viewModel.privacyConfig.collectAsStateWithLifecycle()
    val cacheCount by viewModel.cacheCount.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val currentVideo = if (viewerEngineMode == ViewerEngineMode.NATIVE_STREAM) {
        (feedState as? FeedUiState.Ready)?.videos?.getOrNull(currentIndex)
    } else {
        null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ViewerFeedScreen(
            feedState = feedState,
            currentIndex = currentIndex,
            isPaused = isPaused,
            isMuted = isMuted,
            ultraCleanMode = ultraCleanMode,
            okHttpClient = viewModel.streamingOkHttpClient,
            onPageChanged = viewModel::onPageSelected,
            onTogglePlayPause = viewModel::togglePlayPause,
            onToggleMute = viewModel::toggleMute,
            onOpenSettings = viewModel::openSettings,
            onRefreshFeed = viewModel::refreshFeed,
            onReturnToUnifiedScroll = { viewModel.openGuestScroll("#FYP") },
            onRetry = { viewModel.loadFeed(forceRefresh = true) },
            onSwitchSource = viewModel::switchSourceAndRetry,
            onFallbackToEmbed = viewModel::fallbackVideoToEmbed,
            onGuestGateDetected = viewModel::handleGuestGateDetected,
            viewerEngineMode = viewerEngineMode,
            guestUrl = guestUrl,
            guestReloadToken = guestReloadToken
        )

        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = fadeIn() + slideInVertically { it / 10 },
            exit = fadeOut() + slideOutVertically { it / 10 }
        ) {
            SimpleSettingsSheet(
                currentTheme = themePreset,
                ultraCleanMode = ultraCleanMode,
                bannerMessage = bannerMessage,
                onSelectTheme = { preset -> viewModel.selectThemePreset(preset, context) },
                onToggleUltraCleanMode = { enabled -> viewModel.setUltraCleanMode(enabled, context) },
                onOpenSearch = viewModel::openSearch,
                onOpenAiAssistant = viewModel::openAiSheet,
                onReloadFeed = viewModel::reloadUnifiedFeed,
                onWipeCache = { viewModel.wipeEphemeralCache(context) },
                onOpenAdvancedStatus = viewModel::openResilienceSheet,
                onClose = {
                    viewModel.clearBanner()
                    viewModel.closeSettings()
                }
            )
        }

        AnimatedVisibility(
            visible = isSearchOpen,
            enter = fadeIn() + slideInVertically { it / 10 },
            exit = fadeOut() + slideOutVertically { it / 10 }
        ) {
            PublicSearchSheet(
                searchState = searchState,
                onClose = viewModel::closeSearch,
                onSearch = viewModel::performSearch,
                onSelectVideo = viewModel::selectSearchVideo,
                onOpenInGuestScroll = viewModel::openGuestScroll
            )
        }

        AnimatedVisibility(
            visible = isResilienceOpen,
            enter = fadeIn() + slideInVertically { it / 10 },
            exit = fadeOut() + slideOutVertically { it / 10 }
        ) {
            ResilienceShieldSheet(
                endpointHealth = endpointHealth,
                privacyConfig = privacyConfig,
                cacheCount = cacheCount,
                bannerMessage = bannerMessage,
                onClose = {
                    viewModel.clearBanner()
                    viewModel.closeResilienceSheet()
                },
                onRunHealthChecks = viewModel::runHealthChecks,
                onRotateSource = viewModel::switchSourceAndRetry,
                onResetBreakers = viewModel::resetCircuitBreakers,
                onWipeCache = { viewModel.wipeEphemeralCache(context) },
                onSaveSelfHostedUrl = viewModel::updateSelfHostedProxiTok
            )
        }

        AnimatedVisibility(
            visible = isAiSheetOpen,
            enter = fadeIn() + slideInVertically { it / 10 },
            exit = fadeOut() + slideOutVertically { it / 10 }
        ) {
            AiAssistantSheet(
                aiVibeState = aiVibeState,
                aiInsightState = aiInsightState,
                currentVideo = currentVideo,
                onClose = viewModel::closeAiSheet,
                onAskVibe = viewModel::askAiVibe,
                onApplyVibeToGuestScroll = viewModel::applyAiVibeToGuestScroll,
                onApplyVibeToStream = viewModel::applyAiVibeToStream,
                onSummarizeCurrentVideo = viewModel::summarizeCurrentVideo
            )
        }
    }
}
