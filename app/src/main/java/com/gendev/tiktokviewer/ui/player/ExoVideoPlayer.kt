package com.gendev.tiktokviewer.ui.player

import android.view.TextureView
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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
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

    LaunchedEffect(isActivePage) {
        if (!isActivePage) {
            onBufferingChanged(false)
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
        if (isActivePage) {
            val exoPlayer = remember(streamUrl) {
                // Low-latency RAM-optimized buffer configuration for fast video start & minimal buffering
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        1_500, // minBufferMs
                        6_000, // maxBufferMs
                        400,   // bufferForPlaybackMs (fast instant start)
                        900    // bufferForPlaybackAfterRebufferMs
                    )
                    .setTargetBufferBytes(6 * 1024 * 1024)
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

            var aspectLayoutRef by remember(exoPlayer) { mutableStateOf<AspectRatioFrameLayout?>(null) }
            var textureViewRef by remember(exoPlayer) { mutableStateOf<TextureView?>(null) }
            var attachedTexture by remember(exoPlayer) { mutableStateOf<TextureView?>(null) }

            LaunchedEffect(isPaused, lifecycleResumed, exoPlayer) {
                exoPlayer.playWhenReady = !isPaused && lifecycleResumed
            }

            LaunchedEffect(isMuted, exoPlayer) {
                exoPlayer.volume = if (isMuted) 0f else 1f
            }

            LaunchedEffect(isPaused, exoPlayer) {
                while (!isPaused) {
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

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            val ratio = (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) /
                                videoSize.height.toFloat()
                            if (ratio > 0f) {
                                aspectLayoutRef?.setAspectRatio(ratio)
                            }
                        }
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
                    textureViewRef?.let { exoPlayer.clearVideoTextureView(it) }
                    attachedTexture = null
                    exoPlayer.release()
                }
            }

            AndroidView(
                factory = { ctx ->
                    val textureView = TextureView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    val aspectLayout = AspectRatioFrameLayout(ctx).apply {
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        addView(textureView)
                    }
                    textureViewRef = textureView
                    aspectLayoutRef = aspectLayout
                    attachedTexture = textureView
                    exoPlayer.setVideoTextureView(textureView)
                    aspectLayout
                },
                update = { aspectLayout ->
                    aspectLayoutRef = aspectLayout
                    val textureView = aspectLayout.getChildAt(0) as? TextureView
                    if (textureView != null && attachedTexture !== textureView) {
                        textureViewRef = textureView
                        attachedTexture = textureView
                        exoPlayer.setVideoTextureView(textureView)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

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
