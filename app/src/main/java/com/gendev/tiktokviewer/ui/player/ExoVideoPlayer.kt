package com.gendev.tiktokviewer.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.gendev.tiktokviewer.ui.theme.PureWhite
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
@Composable
fun ExoVideoPlayer(
    streamUrl: String,
    okHttpClient: OkHttpClient,
    isActivePage: Boolean,
    isMuted: Boolean,
    onBufferingChanged: (Boolean) -> Unit,
    onStreamError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var userPaused by remember(streamUrl) { mutableStateOf(false) }
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

    LaunchedEffect(isActivePage, userPaused, lifecycleResumed) {
        exoPlayer.playWhenReady = isActivePage && !userPaused && lifecycleResumed
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
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
                indication = null
            ) {
                userPaused = !userPaused
            },
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

        if (userPaused) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Resume playback",
                tint = PureWhite.copy(alpha = 0.85f),
                modifier = Modifier.background(Color.Black.copy(alpha = 0.35f))
            )
        }
    }
}
