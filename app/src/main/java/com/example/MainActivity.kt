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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gendev.tiktokviewer.data.repository.HybridTikTokRepository
import com.gendev.tiktokviewer.ui.screens.PublicSearchSheet
import com.gendev.tiktokviewer.ui.screens.ResilienceShieldSheet
import com.gendev.tiktokviewer.ui.screens.ViewerFeedScreen
import com.gendev.tiktokviewer.ui.theme.GendevTheme
import com.gendev.tiktokviewer.ui.viewmodel.ViewerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ViewerViewModel by viewModels {
        ViewerViewModel.Factory(HybridTikTokRepository.createDefault())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        handleIncomingDeepLink(intent)

        setContent {
            GendevTheme {
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
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentVideoIndex.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val selectedTopic by viewModel.selectedTopic.collectAsStateWithLifecycle()
    val isSearchOpen by viewModel.isSearchOpen.collectAsStateWithLifecycle()
    val isResilienceOpen by viewModel.isResilienceOpen.collectAsStateWithLifecycle()
    val endpointHealth by viewModel.endpointHealth.collectAsStateWithLifecycle()
    val privacyConfig by viewModel.privacyConfig.collectAsStateWithLifecycle()
    val cacheCount by viewModel.cacheCount.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        ViewerFeedScreen(
            feedState = feedState,
            currentIndex = currentIndex,
            isPaused = isPaused,
            isMuted = isMuted,
            selectedTopic = selectedTopic,
            endpointHealth = endpointHealth,
            okHttpClient = viewModel.okHttpClient,
            onPageChanged = viewModel::onPageSelected,
            onTogglePlayPause = viewModel::togglePlayPause,
            onToggleMute = viewModel::toggleMute,
            onSelectTopic = viewModel::selectTopic,
            onOpenSearch = viewModel::openSearch,
            onOpenResilience = viewModel::openResilienceSheet,
            onHashtagClick = viewModel::openHashtagFromFeed,
            onRetry = { viewModel.loadFeed(forceRefresh = true) },
            onSwitchSource = viewModel::switchSourceAndRetry,
            onFallbackToEmbed = viewModel::fallbackVideoToEmbed
        )

        AnimatedVisibility(
            visible = isSearchOpen,
            enter = fadeIn() + slideInVertically { it / 10 },
            exit = fadeOut() + slideOutVertically { it / 10 }
        ) {
            PublicSearchSheet(
                searchState = searchState,
                onClose = viewModel::closeSearch,
                onSearch = viewModel::performSearch,
                onSelectVideo = viewModel::selectSearchVideo
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
    }
}
