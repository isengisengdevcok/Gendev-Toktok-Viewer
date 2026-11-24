package com.gendev.tiktokviewer.ui.player

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gendev.tiktokviewer.ui.theme.AmoledBlack
import kotlinx.coroutines.delay

/**
 * Isolated WebView player restricted strictly to TikTok Official Embed URLs.
 * Keeps ephemeral DOM storage for JS player compatibility and wipes cookies/storage on disposal.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IsolatedEmbedPlayer(
    embedUrl: String,
    isActivePage: Boolean,
    isPaused: Boolean,
    isMuted: Boolean,
    onLoadingChanged: (Boolean) -> Unit,
    onEmbedBlocked: (statusCode: Int, isCaptcha: Boolean, message: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val allowedHosts = remember {
        setOf(
            "www.tiktok.com",
            "tiktok.com",
            "m.tiktok.com",
            "lf16-tiktok-web.ttwstatic.com",
            "sf16-website-login.neutral.ttwstatic.com"
        )
    }

    val effectiveUrl = remember(embedUrl, isActivePage, isMuted) {
        val separator = if (embedUrl.contains("?")) "&" else "?"
        val autoplay = if (isActivePage) "1" else "0"
        val mute = if (isMuted) "1" else "0"
        "$embedUrl${separator}autoplay=$autoplay&loop=1&muted=$mute&controls=1&rel=0"
    }

    val webView = remember(embedUrl) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportMultipleWindows(false)
            }

            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return true
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    if (scheme == "about" || scheme == "blob") return false
                    if (scheme != "https") return true
                    val host = uri.host?.lowercase().orEmpty()
                    val path = uri.path?.lowercase().orEmpty()
                    if (path.contains("login") || path.contains("signup") || path.contains("passport")) {
                        return true
                    }
                    val permitted = host in allowedHosts ||
                        host.endsWith(".tiktokcdn.com") ||
                        host.endsWith(".tiktokcdn-us.com") ||
                        host.endsWith(".tiktokv.com") ||
                        host.endsWith(".ttwstatic.com") ||
                        host.endsWith(".ibytedtos.com")
                    return !permitted
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    onLoadingChanged(false)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    onLoadingChanged(false)
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    if (request?.isForMainFrame == true) {
                        val code = errorResponse?.statusCode ?: 403
                        onLoadingChanged(false)
                        if (code == 403 || code == 429) {
                            onEmbedBlocked(
                                code,
                                false,
                                if (code == 429) "Official Embed rate limited (HTTP 429)" else "Embed restricted (HTTP 403)"
                            )
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        onLoadingChanged(false)
                    }
                }
            }
        }
    }

    LaunchedEffect(effectiveUrl, isActivePage) {
        if (isActivePage) {
            onLoadingChanged(true)
            webView.onResume()
            webView.loadUrl(effectiveUrl)
            delay(2_000L)
            onLoadingChanged(false)
        } else {
            webView.onPause()
            webView.loadUrl("about:blank")
        }
    }

    LaunchedEffect(isPaused, isActivePage) {
        if (isActivePage) {
            val action = if (isPaused) "pause()" else "play()"
            webView.evaluateJavascript(
                "try{document.querySelectorAll('video').forEach(function(v){v.$action;});}catch(e){}",
                null
            )
        }
    }

    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    webView.onPause()
                    webView.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME -> {
                    webView.resumeTimers()
                    if (isActivePage) webView.onResume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
            }
            webView.removeAllViews()
            webView.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
