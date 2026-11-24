package com.gendev.tiktokviewer.ui.player

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gendev.tiktokviewer.ui.theme.DarkGlassSubtleBorder
import kotlinx.coroutines.delay

private const val MOBILE_WEB_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * Lightweight JS/CSS cosmetic cleanup for TikTok Guest Mobile Web:
 * - Preserves TikTok's native touch-swipe & scroll-snap feed container (never overrides touch-action)
 * - Strips TikTok headers, right-side social buttons, bottom black bars, captions, and login/app modals
 *   so ONLY the clean video remains visible without black blocks
 * - Strictly preserves CAPTCHA/security challenges and any container holding a <video>
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
            '#login-modal, [data-e2e="login-modal"], [data-e2e="login-guide"],',
            '[class*="DivLoginModal"], [class*="LoginModal"], [class*="DivLoginGuide"],',
            '[class*="AppBanner"], [class*="DownloadApp"], [data-e2e="open-app-button"],',
            '[data-e2e="top-login-button"], [class*="DivSwipeGuide"],',
            '[data-e2e="like-icon"], [data-e2e="comment-icon"], [data-e2e="share-icon"],',
            '[data-e2e="browse-like-icon"], [data-e2e="browse-comment-icon"],',
            '[data-e2e="video-desc"], [data-e2e="browse-video-desc"], [data-e2e="browse-music"],',
            '[data-e2e="video-author-uniqueid"], [data-e2e="user-avatar"] {',
            '  display: none !important; visibility: hidden !important; pointer-events: none !important;',
            '}'
          ].join(' ');
          (document.head || document.documentElement).appendChild(s);
        }

        function isCaptchaNode(el) {
          if (!el) return false;
          var id = (el.id || '').toLowerCase();
          var cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
          var txt = (el.textContent || '').toLowerCase();
          if (id.indexOf('captcha') !== -1 || cls.indexOf('captcha') !== -1) return true;
          if (el.querySelector && el.querySelector('[id*="captcha"], [class*="captcha"], iframe[src*="captcha"]')) return true;
          return txt.indexOf('verify to continue') !== -1 || txt.indexOf('drag the puzzle') !== -1;
        }

        function hasVisibleCaptcha() {
          var nodes = document.querySelectorAll('[id*="captcha"], [class*="captcha"], iframe[src*="captcha"], [role="dialog"], [aria-modal="true"]');
          for (var i = 0; i < nodes.length; i++) {
            if (isCaptchaNode(nodes[i]) && nodes[i].offsetParent !== null) return true;
          }
          return false;
        }

        function isProtectedRoot(el) {
          if (!el) return true;
          if (el === document.documentElement || el === document.body) return true;
          if (el.id === 'app' || el.id === 'main') return true;
          if (el.tagName === 'VIDEO') return true;
          if (el.querySelector && el.querySelector('video')) return true;
          return isCaptchaNode(el);
        }

        function hideNode(node) {
          if (isProtectedRoot(node)) return false;
          node.style.setProperty('display', 'none', 'important');
          node.style.setProperty('visibility', 'hidden', 'important');
          node.style.setProperty('pointer-events', 'none', 'important');
          return true;
        }

        function unlockGuestScroll() {
          try {
            var captchaActive = hasVisibleCaptcha();
            var removedModal = false;

            var closeSelectors = '[data-e2e="modal-close-inner-button"], [data-e2e="login-modal"] [aria-label="Close"], [aria-label="Close"], [aria-label="Tutup"]';
            var closeBtns = document.querySelectorAll(closeSelectors);
            for (var i = 0; i < closeBtns.length; i++) {
              var btn = closeBtns[i];
              var parentDialog = btn.closest ? btn.closest('[role="dialog"], [aria-modal="true"], [class*="Modal"]') : null;
              if (!isCaptchaNode(parentDialog) && !(parentDialog && parentDialog.querySelector && parentDialog.querySelector('video'))) {
                btn.click();
                removedModal = true;
              }
            }

            var actionButtons = document.querySelectorAll('button, a, [role="button"]');
            for (var k = 0; k < actionButtons.length; k++) {
              var ab = actionButtons[k];
              if (isCaptchaNode(ab)) continue;
              var label = (ab.textContent || '').trim().toLowerCase();
              if (label === 'continue as guest' ||
                  label === 'lanjutkan sebagai tamu' ||
                  label === 'not now' ||
                  label === 'lain kali' ||
                  label === 'nanti' ||
                  label === 'skip' ||
                  label === 'lewati') {
                var dlg = ab.closest ? ab.closest('[role="dialog"], [aria-modal="true"], [class*="Modal"]') : null;
                if (!isCaptchaNode(dlg) && !(dlg && dlg.querySelector && dlg.querySelector('video'))) {
                  ab.click();
                  removedModal = true;
                }
              }
            }

            // Strip TikTok chrome (headers, bottom black bars, side social action bars, captions)
            var chromeNodes = document.querySelectorAll(
              'header, footer, nav, ' +
              '[class*="DivHeader"], [class*="HeaderContainer"], [class*="TopBar"], ' +
              '[class*="BottomBar"], [class*="TabBar"], [class*="DivFooter"], [class*="DivBottomContainer"], ' +
              '[class*="ActionBar"], [class*="DivActionItem"], [class*="DivSideBar"], [class*="SideBar"], ' +
              '[class*="DivVideoInfo"], [class*="VideoInfo"], [class*="DivMusic"], [class*="DivDescription"], ' +
              '[class*="OpenApp"], [class*="AppBanner"], [class*="DownloadApp"]'
            );
            for (var c = 0; c < chromeNodes.length; c++) {
              hideNode(chromeNodes[c]);
            }

            // Remove non-CAPTCHA login/app/interest modals and dark/fixed backdrop blocks
            var overlays = document.querySelectorAll(
              '#login-modal, [data-e2e="login-modal"], [data-e2e="modal-mask"], ' +
              '[role="dialog"], [aria-modal="true"], [class*="TUXModal"], ' +
              '[class*="DivModalMask"], [class*="ModalMask"], [class*="DivModalContainer"]'
            );
            for (var j = 0; j < overlays.length; j++) {
              var node = overlays[j];
              if (isProtectedRoot(node)) continue;

              var content = (node.textContent || '').trim().toLowerCase();
              var style = window.getComputedStyle ? window.getComputedStyle(node) : null;
              var isFixedMask = style && style.position === 'fixed' && content.length === 0;
              var isLoginOrAppPrompt =
                node.id === 'login-modal' ||
                node.getAttribute('data-e2e') === 'login-modal' ||
                node.getAttribute('data-e2e') === 'modal-mask' ||
                content.indexOf('log in') !== -1 ||
                content.indexOf('sign up') !== -1 ||
                content.indexOf('masuk ke tiktok') !== -1 ||
                content.indexOf('daftar') !== -1 ||
                content.indexOf('open tiktok') !== -1 ||
                content.indexOf('download') !== -1 ||
                content.indexOf('choose your interests') !== -1 ||
                content.indexOf('pilih minat') !== -1 ||
                isFixedMask;

              if (isLoginOrAppPrompt) {
                if (hideNode(node)) removedModal = true;
                if (node.parentElement) {
                  var pStyle = window.getComputedStyle ? window.getComputedStyle(node.parentElement) : null;
                  if (pStyle && pStyle.position === 'fixed') {
                    hideNode(node.parentElement);
                  }
                }
              }
            }

            // Hide any opaque dark/black fixed bottom/edge bars without <video> (e.g. "Open app" / nav dock)
            var fixedEls = document.querySelectorAll('div, section, aside');
            for (var f = 0; f < fixedEls.length; f++) {
              var fel = fixedEls[f];
              if (isProtectedRoot(fel)) continue;
              var fs = window.getComputedStyle ? window.getComputedStyle(fel) : null;
              if (!fs || (fs.position !== 'fixed' && fs.position !== 'sticky')) continue;
              if (fs.display === 'none' || fs.visibility === 'hidden') continue;
              var bg = fs.backgroundColor || '';
              var isOpaqueBg = bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent';
              var rect = fel.getBoundingClientRect ? fel.getBoundingClientRect() : null;
              if (isOpaqueBg && rect && rect.height > 0 && rect.height <= window.innerHeight * 0.45) {
                hideNode(fel);
                removedModal = true;
              }
            }

            if (!captchaActive && (removedModal || (document.body && document.body.style.overflow === 'hidden'))) {
              var targets = [document.documentElement, document.body, document.getElementById('app'), document.getElementById('main')];
              for (var t = 0; t < targets.length; t++) {
                var el = targets[t];
                if (!el) continue;
                if (el.style.overflow === 'hidden') el.style.overflow = '';
                if (el.style.overflowY === 'hidden') el.style.overflowY = '';
                if (el.style.position === 'fixed') {
                  el.style.position = '';
                  el.style.top = '';
                  el.style.width = '';
                }
                if (el.style.pointerEvents === 'none') el.style.pointerEvents = '';
                if (el.style.touchAction === 'none') el.style.touchAction = '';
              }
            }
          } catch (e) {}
        }

        unlockGuestScroll();

        if (!window.__gendevGuestObserverAttached) {
          window.__gendevGuestObserverAttached = true;
          var scheduled = false;
          var observer = new MutationObserver(function() {
            if (scheduled) return;
            scheduled = true;
            setTimeout(function() {
              scheduled = false;
              unlockGuestScroll();
            }, 140);
          });
          if (document.body || document.documentElement) {
            observer.observe(document.body || document.documentElement, {
              childList: true,
              subtree: true,
              attributes: true,
              attributeFilter: ['style', 'class']
            });
          }
          window.addEventListener('touchend', function() {
            setTimeout(unlockGuestScroll, 180);
          }, { passive: true });
        }
      } catch (e) {}
    })();
""".trimIndent()

/**
 * Lightweight, login-free TikTok Guest Scroll player.
 * Lets users scroll public TikTok feeds directly without accounts, login screens, or app-store redirects.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
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
            setLayerType(View.LAYER_TYPE_NONE, null)
            isFocusable = true
            isFocusableInTouchMode = true
            isNestedScrollingEnabled = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }

            runCatching {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                setSupportMultipleWindows(false)
                userAgentString = MOBILE_WEB_UA
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadProgress = newProgress
                    if (newProgress >= 60) {
                        view?.evaluateJavascript(GUEST_CLEANUP_JS, null)
                    }
                    if (newProgress >= 45) {
                        isInitialLoading = false
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

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    val lower = url?.lowercase().orEmpty()
                    if (lower.contains("/login") || lower.contains("/signup") || lower.contains("/passport")) {
                        if (view?.canGoBack() == true) {
                            view.goBack()
                        }
                        view?.evaluateJavascript(GUEST_CLEANUP_JS, null)
                    }
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
        loadProgress = 20
        webView.onResume()
        webView.loadUrl(targetUrl)
        // Fast failsafe so spinner never blocks video rendering
        delay(1_600L)
        isInitialLoading = false
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
            .background(MaterialTheme.colorScheme.background)
            .testTag("guest_scroll_player")
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        if (loadProgress in 1..92) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                color = MaterialTheme.colorScheme.secondary,
                trackColor = DarkGlassSubtleBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
            )
        }

        AnimatedVisibility(
            visible = isInitialLoading,
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
                    .testTag("guest_loading_indicator")
            )
        }
    }
}
