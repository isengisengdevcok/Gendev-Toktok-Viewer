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

### Supported Playback & Discovery Engines
1. **TikTok Guest Scroll Mode (`Scroll`)** — Lightweight, login-free public scroll player (`https://www.tiktok.com/explore` / `foryou` / `#tag` / `@creator`) that blocks `/login`, `/signup`, and app-store hijacks while cleaning intrusive modal popups for uninterrupted vertical scrolling. Automatically engages if stream APIs are throttled.
2. **TikTok Public Content Adapter (TikWM JSON)** (`https://www.tikwm.com/api/`) with request pacing (`1.1s`) for public feeds, `#hashtag`, `@creator`, and direct HTTPS Media3 `ExoPlayer` streams.
3. **Open-Source & Self-Hosted ProxiTok Instances** (`pablouser1/ProxiTok` + custom HTTPS URL inside Privacy & Resilience Shield).
4. **TikTok Official oEmbed + Embed v3 Player** (`https://www.tiktok.com/oembed`, `https://www.tiktok.com/embed/v3/{videoId}`) rendered inside `IsolatedEmbedPlayer`.
5. **Google Gemini AI Smart Scroll (`Firebase.ai` · `gemini-3.8-flash`)** — Stateless natural-language topic/vibe discovery (`Schema.obj`) and streaming Indonesian caption summaries with App Check attestation (zero API key, zero saved history).

---

## Anti-Blocking Resilience (No Protection Bypass)

GENDEV TikTok Viewer implements **access resilience**, never DRM circumvention or CAPTCHA bypass:

- **Automatic Guest Scroll Fallback**: If upstream JSON/ProxiTok APIs return HTTP 429, 403, or network errors, the app seamlessly shifts into login-free Guest Scroll instead of stranding the user on an error screen.
- **Per-Endpoint Circuit Breaker** (`CLOSED`, `OPEN`, `HALF_OPEN`)
- **Exponential Backoff** for transient network errors only
- **Immediate Loop Prevention on HTTP 429**: Respects `Retry-After` cooldowns without hammering endpoints
- **Transparent HTTP 403 / CAPTCHA Handling**: Immediately halts retries, surfaces an informative status card, and offers manual **Retry**, **Switch**, or **Buka Mode Scroll**
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
