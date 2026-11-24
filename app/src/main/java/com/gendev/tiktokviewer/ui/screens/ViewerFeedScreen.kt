package com.gendev.tiktokviewer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.gendev.tiktokviewer.domain.model.EndpointHealth
import com.gendev.tiktokviewer.domain.model.PlaybackMode
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.ui.components.GendevInitialHeroLoader
import com.gendev.tiktokviewer.ui.components.GendevLogoBadge
import com.gendev.tiktokviewer.ui.components.GendevPulseLoader
import com.gendev.tiktokviewer.ui.components.GlassIconButton
import com.gendev.tiktokviewer.ui.components.ResilientErrorCard
import com.gendev.tiktokviewer.ui.components.SourceIndicatorPill
import com.gendev.tiktokviewer.ui.components.ambientObsidianAura
import com.gendev.tiktokviewer.ui.player.ExoVideoPlayer
import com.gendev.tiktokviewer.ui.player.IsolatedEmbedPlayer
import com.gendev.tiktokviewer.ui.theme.AmoledBlack
import com.gendev.tiktokviewer.ui.theme.DarkGlassBg
import com.gendev.tiktokviewer.ui.theme.DarkGlassBorder
import com.gendev.tiktokviewer.ui.theme.DarkGlassSubtleBorder
import com.gendev.tiktokviewer.ui.theme.DeepPlum
import com.gendev.tiktokviewer.ui.theme.ElectricPurple
import com.gendev.tiktokviewer.ui.theme.MutedLavender
import com.gendev.tiktokviewer.ui.theme.NeonViolet
import com.gendev.tiktokviewer.ui.theme.PureWhite
import com.gendev.tiktokviewer.ui.viewmodel.FeedUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.OkHttpClient

private val quickFeedTopics = listOf("Trending", "#FYP", "#Tech", "#Gaming", "#Music", "#Comedy", "#Nature")

@Composable
fun ViewerFeedScreen(
    feedState: FeedUiState,
    currentIndex: Int,
    isPaused: Boolean,
    isMuted: Boolean,
    selectedTopic: String,
    endpointHealth: List<EndpointHealth>,
    okHttpClient: OkHttpClient,
    onPageChanged: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectTopic: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenResilience: () -> Unit,
    onHashtagClick: (String) -> Unit,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
    onFallbackToEmbed: (videoId: String, reason: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSourceSummary = when (feedState) {
        is FeedUiState.Ready -> feedState.videos.getOrNull(currentIndex)?.sourceLabel ?: feedState.activeSourceLabel
        is FeedUiState.Loading -> "Resolving..."
        is FeedUiState.Error -> "Source Alert"
    }
    val hasHealthyNode = endpointHealth.isEmpty() || endpointHealth.any { it.isHealthy }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack),
        containerColor = AmoledBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .ambientObsidianAura()
        ) {
            when (feedState) {
                is FeedUiState.Loading -> {
                    GendevInitialHeroLoader(
                        sourceHint = feedState.sourceHint,
                        onOpenSearch = onOpenSearch,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("feed_loading_indicator")
                    )
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
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is FeedUiState.Ready -> {
                    if (feedState.videos.isEmpty()) {
                        ResilientErrorCard(
                            title = "No Public Videos",
                            message = "The selected public source returned zero videos.",
                            isCaptcha = false,
                            isRateLimited = false,
                            isForbidden = false,
                            onRetry = onRetry,
                            onSwitchSource = onSwitchSource,
                            modifier = Modifier.align(Alignment.Center)
                        )
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
                            VideoFeedPage(
                                video = video,
                                isActivePage = isActive,
                                isPaused = isPaused,
                                isMuted = isMuted,
                                isFirstPage = pageIndex == 0 && feedState.videos.size > 1,
                                okHttpClient = okHttpClient,
                                onTogglePlayPause = onTogglePlayPause,
                                onToggleMute = onToggleMute,
                                onSwitchSource = onSwitchSource,
                                onHashtagClick = onHashtagClick,
                                onFallbackToEmbed = onFallbackToEmbed
                            )
                        }
                    }
                }
            }

            // Floating Top Glass Header + Quick Topic Selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.80f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GendevLogoBadge()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceIndicatorPill(
                            label = activeSourceSummary,
                            isHealthy = hasHealthyNode,
                            onClick = onOpenResilience
                        )
                        GlassIconButton(
                            icon = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.search_cd),
                            onClick = onOpenSearch,
                            testTagName = "open_search_button"
                        )
                        GlassIconButton(
                            icon = Icons.Rounded.Shield,
                            contentDescription = stringResource(R.string.resilience_cd),
                            onClick = onOpenResilience,
                            testTagName = "open_resilience_button"
                        )
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickFeedTopics) { topic ->
                        val isSelected = selectedTopic.equals(topic, ignoreCase = true)
                        Surface(
                            onClick = { onSelectTopic(topic) },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) DeepPlum.copy(alpha = 0.92f) else DarkGlassBg,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) ElectricPurple else DarkGlassSubtleBorder
                            ),
                            modifier = Modifier.testTag("topic_pill_$topic")
                        ) {
                            Text(
                                text = topic,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) NeonViolet else PureWhite.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoFeedPage(
    video: PublicVideo,
    isActivePage: Boolean,
    isPaused: Boolean,
    isMuted: Boolean,
    isFirstPage: Boolean,
    okHttpClient: OkHttpClient,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onSwitchSource: () -> Unit,
    onHashtagClick: (String) -> Unit,
    onFallbackToEmbed: (videoId: String, reason: String) -> Unit
) {
    var isBuffering by remember(video.id, video.playbackMode) { mutableStateOf(true) }
    var playbackProgress by remember(video.id, video.playbackMode) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
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
                                onFallbackToEmbed(video.id, "$reason · Switched to Official Embed v3")
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
            GendevPulseLoader(
                label = stringResource(R.string.loading_stream),
                modifier = Modifier.testTag("video_buffering_indicator")
            )
        }

        // Bottom Glass Metadata + Dedicated Play/Pause/Mute Control Dock (ZERO social buttons)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "@${video.authorHandle}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PureWhite
                        )
                        Text(
                            text = video.playbackMode.badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonViolet,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DeepPlum.copy(alpha = 0.88f))
                                .border(1.dp, DarkGlassBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    if (video.title.isNotBlank()) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = PureWhite.copy(alpha = 0.94f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (video.hashtags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            video.hashtags.take(4).forEach { tag ->
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonViolet,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(DarkGlassBg)
                                        .border(1.dp, DarkGlassSubtleBorder, RoundedCornerShape(50))
                                        .clickable { onHashtagClick(tag) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    if (isFirstPage) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MutedLavender,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Swipe up for next public video",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedLavender
                            )
                        }
                    }
                }

                // Right-side Minimalist Player Control Capsule (Play/Pause, Mute, Switch Source)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassIconButton(
                        icon = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (isPaused) "Play video" else "Pause video",
                        onClick = onTogglePlayPause,
                        isHighlighted = isPaused,
                        sizeDp = 52,
                        testTagName = "play_pause_button"
                    )
                    GlassIconButton(
                        icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.mute_cd),
                        onClick = onToggleMute,
                        isHighlighted = isMuted,
                        sizeDp = 48,
                        testTagName = "mute_toggle_button"
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.SwapHoriz,
                        contentDescription = stringResource(R.string.switch_source_action),
                        onClick = onSwitchSource,
                        sizeDp = 48,
                        testTagName = "quick_switch_source_button"
                    )
                }
            }

            if (video.playbackMode == PlaybackMode.EXO_DIRECT_STREAM) {
                LinearProgressIndicator(
                    progress = { playbackProgress },
                    color = NeonViolet,
                    trackColor = DarkGlassSubtleBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .testTag("video_progress_bar")
                )
            }
        }
    }
}
