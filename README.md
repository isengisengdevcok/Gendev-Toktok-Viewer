# GENDEV TikTok Viewer

**Anonymous TikTok Public Video Streaming Application for Android**

GENDEV TikTok Viewer is a standalone, zero-account Android client built with **Kotlin**, **Jetpack Compose**, **Media3 ExoPlayer**, and **Privacy-Isolated Official Embed v2** for watching public TikTok videos without login, cookies, analytics, or watch history.

---

## Core Principles & Privacy Guarantees

- **Anonymous Mode by Default**: No login, registration, email, phone number, Google Sign-In, or `sessionid` cookies.
- **Zero Persistent History**: No watch history, no saved search history, and `android:allowBackup="false"`.
- **HTTPS-Only Transport**: Non-HTTPS URLs and SSL downgrades are blocked by `PrivacyPolicyEnforcer`; tracking query parameters (`utm_*`, `_r`, `_t`, `tt_from`, `sender_device`, etc.) and identifying headers are stripped automatically.
- **Zero Analytics / Tracking SDKs**: No Firebase Analytics, no Crashlytics, no ad SDKs, and ProGuard strips verbose logs in release builds.
- **Ephemeral RAM Cache**: Bounded TTL metadata cache (`EphemeralMetadataCache`) with one-tap full memory + WebView cache wipe.

---

## Hybrid Multi-Source Architecture

All providers sit behind clean, swappable interfaces (`com.gendev.tiktokviewer.domain.provider`):

- `TikTokFeedProvider`
- `TikTokSearchProvider`
- `TikTokVideoProvider`
- `TikTokPlaybackProvider`
- `TikTokFallbackManager`

### Supported Sources
1. **Self-Hosted ProxiTok Instance** (configurable HTTPS URL inside Privacy & Resilience Shield)
2. **Open-Source ProxiTok Public Instances** (`proxitok.pabloferreiro.es`, `proxitok.pussthecat.org`, `tok.adminforge.de`, `proxitok.privacydev.net`)
3. **TikTok Public Content Adapter** (public `#hashtag`, `@creator`, and link resolution)
4. **TikTok Official oEmbed API** (`https://www.tiktok.com/oembed`)
5. **TikTok Official Embed v2 Player** (`https://www.tiktok.com/embed/v2/{videoId}`) rendered inside a cookie-disabled, storage-disabled `IsolatedEmbedPlayer` WebView.

---

## Anti-Blocking Resilience (No Protection Bypass)

GENDEV TikTok Viewer implements **access resilience**, never DRM circumvention or CAPTCHA bypass:

- **Per-Endpoint Circuit Breaker** (`CLOSED`, `OPEN`, `HALF_OPEN`)
- **Exponential Backoff** for transient network errors only
- **Immediate Loop Prevention on HTTP 429**: Respects `Retry-After` cooldowns without hammering endpoints
- **Transparent HTTP 403 / CAPTCHA Handling**: Immediately halts retries, surfaces an informative status card, and offers manual **Retry** or **Switch Source**
- **Live HTTPS Health Checks** & one-tap endpoint rotation

---

## Building Locally & via GitHub Actions

### Local Gradle Build
```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

### GitHub Actions CI/CD
Push to `main` or trigger **Build GENDEV TikTok Viewer APK** under the **Actions** tab to automatically run all tests and download:
- `gendev-tiktok-viewer-debug-apk`
- `gendev-tiktok-viewer-release-apk`

For production Release signing on GitHub Actions, configure repository secrets:
- `KEYSTORE_BASE64`
- `STORE_PASSWORD`
- `KEY_PASSWORD`
