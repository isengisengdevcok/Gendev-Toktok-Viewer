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

/**
 * Isolated WebView player restricted strictly to TikTok Official Embed v2 URLs.
 * Disables cookies, persistent DOM storage, and blocks redirects to login/tracking pages.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IsolatedEmbedPlayer(
    embedUrl: String,
    isActivePage: Boolean,
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
        runCatching {
            CookieManager.getInstance().apply {
                setAcceptCookie(false)
                removeAllCookies(null)
            }
        }

        WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                databaseEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_NO_CACHE
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
                    if (uri.scheme != "https") return true
                    val host = uri.host?.lowercase().orEmpty()
                    val path = uri.path?.lowercase().orEmpty()
                    // Block navigation to login, signup, or external redirect pages
                    if (path.contains("login") || path.contains("signup")) {
                        return true
                    }
                    val permitted = host in allowedHosts ||
                        host.endsWith(".tiktokcdn.com") ||
                        host.endsWith(".tiktokv.com") ||
                        host.endsWith(".ttwstatic.com")
                    return !permitted
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
        } else {
            webView.onPause()
            webView.loadUrl("about:blank")
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
