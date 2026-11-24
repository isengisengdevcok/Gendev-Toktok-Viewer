package com.gendev.tiktokviewer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.gendev.tiktokviewer.domain.model.PlaybackMode
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.ui.components.GlassIconButton
import com.gendev.tiktokviewer.ui.components.ResilientErrorCard
import com.gendev.tiktokviewer.ui.components.ambientObsidianAura
import com.gendev.tiktokviewer.ui.player.ExoVideoPlayer
import com.gendev.tiktokviewer.ui.player.IsolatedEmbedPlayer
import com.gendev.tiktokviewer.ui.player.TikTokGuestScrollPlayer
import com.gendev.tiktokviewer.ui.viewmodel.FeedUiState
import com.gendev.tiktokviewer.ui.viewmodel.ViewerEngineMode
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.OkHttpClient

@Composable
fun ViewerFeedScreen(
    feedState: FeedUiState,
    currentIndex: Int,
    isPaused: Boolean,
    isMuted: Boolean,
    ultraCleanMode: Boolean,
    okHttpClient: OkHttpClient,
    onPageChanged: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshFeed: () -> Unit,
    onReturnToUnifiedScroll: () -> Unit,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
    onFallbackToEmbed: (videoId: String, reason: String) -> Unit,
    viewerEngineMode: ViewerEngineMode = ViewerEngineMode.GUEST_SCROLL,
    guestUrl: String = "https://www.tiktok.com/foryou",
    guestReloadToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val bg = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
        containerColor = bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .ambientObsidianAura(
                    backgroundColor = bg,
                    primaryGlow = primary,
                    secondaryGlow = MaterialTheme.colorScheme.secondary
                )
        ) {
            if (viewerEngineMode == ViewerEngineMode.GUEST_SCROLL) {
                TikTokGuestScrollPlayer(
                    targetUrl = guestUrl,
                    isPaused = isPaused,
                    isMuted = isMuted,
                    reloadToken = guestReloadToken,
                    modifier = Modifier.fillMaxSize()
                )

                MinimalCornerControls(
                    ultraCleanMode = ultraCleanMode,
                    isPaused = isPaused,
                    isMuted = isMuted,
                    showBackToFeed = false,
                    onTogglePlayPause = onTogglePlayPause,
                    onToggleMute = onToggleMute,
                    onBackToFeed = onReturnToUnifiedScroll,
                    onRefreshFeed = onRefreshFeed,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 14.dp, bottom = 18.dp)
                )
            } else {
                when (feedState) {
                    is FeedUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("feed_loading_indicator"),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = Color.White.copy(alpha = 0.12f),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    is FeedUiState.Error -> {
                        ResilientErrorCard(
                            title = feedState.title,
                            message = feedState.message,
                            isCaptcha = feedState.isCaptcha,
                            isRateLimited = feedState.isRateLimited,
                            isForbidden = feedState.isForbidden,
                            onRetry = onRetry,
                            onSwitchSource = onSwitchSource,
                            onOpenGuestScroll = onReturnToUnifiedScroll,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    is FeedUiState.Ready -> {
                        if (feedState.videos.isEmpty()) {
                            LaunchedEffect(Unit) {
                                onReturnToUnifiedScroll()
                            }
                        } else {
                            val pagerState = rememberPagerState(
                                initialPage = currentIndex.coerceIn(0, feedState.videos.lastIndex),
                                pageCount = { feedState.videos.size }
                            )

                            LaunchedEffect(pagerState) {
                                snapshotFlow { pagerState.currentPage }
                                    .distinctUntilChanged()
                                    .collect { page -> onPageChanged(page) }
                            }

                            LaunchedEffect(currentIndex, feedState.videos) {
                                val target = currentIndex.coerceIn(0, feedState.videos.lastIndex)
                                if (pagerState.currentPage != target) {
                                    pagerState.scrollToPage(target)
                                }
                            }

                            VerticalPager(
                                state = pagerState,
                                beyondViewportPageCount = 0,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("vertical_video_pager")
                            ) { pageIndex ->
                                val video = feedState.videos[pageIndex]
                                val isActive = pageIndex == pagerState.currentPage
                                CleanStreamVideoPage(
                                    video = video,
                                    isActivePage = isActive,
                                    isPaused = isPaused,
                                    isMuted = isMuted,
                                    ultraCleanMode = ultraCleanMode,
                                    okHttpClient = okHttpClient,
                                    onTogglePlayPause = onTogglePlayPause,
                                    onFallbackToEmbed = onFallbackToEmbed
                                )
                            }
                        }
                    }
                }

                MinimalCornerControls(
                    ultraCleanMode = ultraCleanMode,
                    isPaused = isPaused,
                    isMuted = isMuted,
                    showBackToFeed = false,
                    onTogglePlayPause = onTogglePlayPause,
                    onToggleMute = onToggleMute,
                    onBackToFeed = onReturnToUnifiedScroll,
                    onRefreshFeed = onRefreshFeed,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 14.dp, bottom = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun MinimalCornerControls(
    ultraCleanMode: Boolean,
    isPaused: Boolean,
    isMuted: Boolean,
    showBackToFeed: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onBackToFeed: () -> Unit,
    onRefreshFeed: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!ultraCleanMode) {
            if (showBackToFeed) {
                GlassIconButton(
                    icon = Icons.Rounded.DynamicFeed,
                    contentDescription = "Kembali ke Feed Scroll",
                    onClick = onBackToFeed,
                    sizeDp = 40,
                    alpha = 0.24f,
                    testTagName = "back_to_scroll_button"
                )
            }
            GlassIconButton(
                icon = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = if (isPaused) "Play video" else "Pause video",
                onClick = onTogglePlayPause,
                isHighlighted = isPaused,
                sizeDp = 40,
                alpha = 0.24f,
                testTagName = "play_pause_button"
            )
            GlassIconButton(
                icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(R.string.mute_cd),
                onClick = onToggleMute,
                isHighlighted = isMuted,
                sizeDp = 40,
                alpha = 0.24f,
                testTagName = "mute_toggle_button"
            )
        }
        GlassIconButton(
            icon = Icons.Rounded.Refresh,
            contentDescription = stringResource(R.string.refresh_cd),
            onClick = onRefreshFeed,
            sizeDp = if (ultraCleanMode) 36 else 40,
            alpha = if (ultraCleanMode) 0.20f else 0.24f,
            testTagName = "refresh_feed_button"
        )
        GlassIconButton(
            icon = Icons.Rounded.Tune,
            contentDescription = stringResource(R.string.settings_cd),
            onClick = onOpenSettings,
            sizeDp = if (ultraCleanMode) 36 else 40,
            alpha = if (ultraCleanMode) 0.18f else 0.26f,
            testTagName = "open_settings_button"
        )
    }
}

@Composable
private fun CleanStreamVideoPage(
    video: PublicVideo,
    isActivePage: Boolean,
    isPaused: Boolean,
    isMuted: Boolean,
    ultraCleanMode: Boolean,
    okHttpClient: OkHttpClient,
    onTogglePlayPause: () -> Unit,
    onFallbackToEmbed: (videoId: String, reason: String) -> Unit
) {
    var isBuffering by remember(video.id, video.playbackMode) { mutableStateOf(true) }
    var playbackProgress by remember(video.id, video.playbackMode) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("video_page_${video.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
        ) {
            when (video.playbackMode) {
                PlaybackMode.EXO_DIRECT_STREAM -> {
                    val directUrl = video.directMediaUrl
                    if (directUrl != null) {
                        ExoVideoPlayer(
                            streamUrl = directUrl,
                            okHttpClient = okHttpClient,
                            isActivePage = isActivePage,
                            isPaused = isPaused,
                            isMuted = isMuted,
                            onTogglePlayPause = onTogglePlayPause,
                            onProgressChanged = { playbackProgress = it },
                            onBufferingChanged = { buffering -> isBuffering = buffering },
                            onStreamError = { reason ->
                                onFallbackToEmbed(video.id, reason)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        IsolatedEmbedPlayer(
                            embedUrl = video.embedUrl,
                            isActivePage = isActivePage,
                            isPaused = isPaused,
                            isMuted = isMuted,
                            onLoadingChanged = { loading -> isBuffering = loading },
                            onEmbedBlocked = { _, _, msg ->
                                onFallbackToEmbed(video.id, msg)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                PlaybackMode.ISOLATED_EMBED_WEBVIEW -> {
                    IsolatedEmbedPlayer(
                        embedUrl = video.embedUrl,
                        isActivePage = isActivePage,
                        isPaused = isPaused,
                        isMuted = isMuted,
                        onLoadingChanged = { loading -> isBuffering = loading },
                        onEmbedBlocked = { _, _, msg ->
                            onFallbackToEmbed(video.id, msg)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isActivePage && isBuffering && !isPaused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.secondary,
                trackColor = Color.White.copy(alpha = 0.12f),
                strokeWidth = 2.5.dp,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("video_buffering_indicator")
            )
        }

        if (!ultraCleanMode && video.playbackMode == PlaybackMode.EXO_DIRECT_STREAM) {
            LinearProgressIndicator(
                progress = { playbackProgress },
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                trackColor = Color.White.copy(alpha = 0.10f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .height(2.dp)
                    .clip(RoundedCornerShape(50))
                    .testTag("video_progress_bar")
            )
        }
    }
}
