package com.gendev.tiktokviewer.ui.player

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gendev.tiktokviewer.ui.components.GendevPulseLoader
import com.gendev.tiktokviewer.ui.theme.AmoledBlack
import com.gendev.tiktokviewer.ui.theme.DarkGlassSubtleBorder
import com.gendev.tiktokviewer.ui.theme.NeonViolet

private const val MOBILE_WEB_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * Lightweight JS/CSS cosmetic cleanup for TikTok Guest Mobile Web:
 * - Suppresses intrusive "Log in to continue" / "Open TikTok App" banners & modals
 * - Does NOT tamper with security/CAPTCHA challenges
 * - Keeps layout AMOLED dark and scroll-first
 */
private val GUEST_CLEANUP_JS = """
    (function() {
      try {
        var styleId = 'gendev-guest-lite-css';
        if (!document.getElementById(styleId)) {
          var s = document.createElement('style');
          s.id = styleId;
          s.textContent = [
            'html, body { background-color: #000000 !important; overscroll-behavior-y: contain; }',
            '#login-modal, [data-e2e="login-modal"], [class*="DivLoginModal"], [class*="LoginModal"],',
            '[class*="DivBottomBanner"], [class*="OpenApp"], [class*="DownloadApp"],',
            '[data-e2e="open-app-button"], [data-e2e="top-login-button"],',
            '[class*="DivFixedBottomContainer"], [class*="AppBanner"] { display: none !important; visibility: hidden !important; pointer-events: none !important; }'
          ].join(' ');
          (document.head || document.documentElement).appendChild(s);
        }
        var closeBtn = document.querySelector('[data-e2e="modal-close-inner-button"], [aria-label="Close"], [aria-label="Tutup"]');
        if (closeBtn) { closeBtn.click(); }
      } catch (e) {}
    })();
""".trimIndent()

/**
 * Lightweight, login-free TikTok Guest Scroll player.
 * Lets users scroll public TikTok feeds directly without accounts, login screens, or app-store redirects.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokGuestScrollPlayer(
    targetUrl: String,
    isPaused: Boolean,
    isMuted: Boolean,
    reloadToken: Int,
    onBackHandled: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var loadProgress by remember { mutableIntStateOf(10) }
    var isInitialLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
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
                userAgentString = MOBILE_WEB_UA
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadProgress = newProgress
                    if (newProgress >= 75) {
                        isInitialLoading = false
                        view?.evaluateJavascript(GUEST_CLEANUP_JS, null)
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return true
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    if (scheme == "about" || scheme == "blob") return false
                    // Block deep-link app-store / native TikTok hijacks
                    if (scheme != "https") return true

                    val host = uri.host?.lowercase().orEmpty()
                    val path = uri.path?.lowercase().orEmpty()
                    // Never navigate to login/signup walls
                    if (path.startsWith("/login") ||
                        path.startsWith("/signup") ||
                        path.startsWith("/register") ||
                        path.contains("/passport")
                    ) {
                        view?.evaluateJavascript(GUEST_CLEANUP_JS, null)
                        return true
                    }

                    val allowed = host == "www.tiktok.com" ||
                        host == "m.tiktok.com" ||
                        host == "tiktok.com" ||
                        host == "vm.tiktok.com" ||
                        host == "vt.tiktok.com" ||
                        host.endsWith(".tiktok.com") ||
                        host.endsWith(".tiktokcdn.com") ||
                        host.endsWith(".tiktokcdn-us.com") ||
                        host.endsWith(".tiktokv.com") ||
                        host.endsWith(".ttwstatic.com") ||
                        host.endsWith(".ibytedtos.com")
                    return !allowed
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    isInitialLoading = false
                    canGoBack = view?.canGoBack() == true
                    view?.evaluateJavascript(GUEST_CLEANUP_JS, null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isInitialLoading = false
                    canGoBack = view?.canGoBack() == true
                    view?.evaluateJavascript(GUEST_CLEANUP_JS, null)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        isInitialLoading = false
                    }
                }
            }
        }
    }

    BackHandler(enabled = canGoBack || onBackHandled != null) {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onBackHandled?.invoke()
        }
    }

    LaunchedEffect(targetUrl, reloadToken) {
        isInitialLoading = true
        loadProgress = 15
        webView.onResume()
        webView.loadUrl(targetUrl)
    }

    LaunchedEffect(isPaused, isMuted) {
        val playJs = if (isPaused) "v.pause();" else "v.play();"
        val muteJs = "v.muted=${if (isMuted) "true" else "false"};"
        webView.evaluateJavascript(
            "try{document.querySelectorAll('video').forEach(function(v){$muteJs$playJs});}catch(e){}",
            null
        )
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
                    webView.onResume()
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
            .testTag("guest_scroll_player")
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        if (loadProgress in 1..95) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                color = NeonViolet,
                trackColor = DarkGlassSubtleBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(2.5.dp)
                    .align(Alignment.TopCenter)
            )
        }

        AnimatedVisibility(
            visible = isInitialLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GendevPulseLoader(
                label = "Membuka TikTok Scroll tanpa login...",
                modifier = Modifier.testTag("guest_loading_indicator")
            )
        }
    }
}
