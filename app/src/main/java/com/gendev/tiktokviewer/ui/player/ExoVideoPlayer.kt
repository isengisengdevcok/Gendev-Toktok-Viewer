package com.gendev.tiktokviewer.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gendev.tiktokviewer.ui.theme.AmoledBlack
import com.gendev.tiktokviewer.ui.theme.DarkGlassBorder
import com.gendev.tiktokviewer.ui.theme.DarkGlassStrong
import com.gendev.tiktokviewer.ui.theme.NeonViolet
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
@Composable
fun ExoVideoPlayer(
    streamUrl: String,
    okHttpClient: OkHttpClient,
    isActivePage: Boolean,
    isPaused: Boolean,
    isMuted: Boolean,
    onTogglePlayPause: () -> Unit,
    onProgressChanged: (Float) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onStreamError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleResumed by remember { mutableStateOf(true) }

    val exoPlayer = remember(streamUrl) {
        // RAM-optimized buffer configuration tailored for 2GB RAM Android devices
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2_500, // minBufferMs
                8_000, // maxBufferMs
                800,   // bufferForPlaybackMs
                1_500  // bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(8 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
            }
    }

    LaunchedEffect(isActivePage, isPaused, lifecycleResumed) {
        exoPlayer.playWhenReady = isActivePage && !isPaused && lifecycleResumed
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isActivePage, isPaused, exoPlayer) {
        while (isActivePage && !isPaused) {
            val dur = exoPlayer.duration
            if (dur > 0L) {
                onProgressChanged((exoPlayer.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f))
            }
            delay(250L)
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    lifecycleResumed = false
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    lifecycleResumed = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                onBufferingChanged(
                    playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                onBufferingChanged(false)
                onStreamError("Direct stream unavailable (${error.errorCodeName})")
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTogglePlayPause
            ),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = isPaused,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DarkGlassStrong)
                    .border(1.5.dp, DarkGlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Paused - tap to play",
                    tint = NeonViolet,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}
