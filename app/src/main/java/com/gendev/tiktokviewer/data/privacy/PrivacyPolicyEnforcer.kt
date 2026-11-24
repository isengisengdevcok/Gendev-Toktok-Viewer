package com.gendev.tiktokviewer.data.privacy

import okhttp3.ConnectionSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object PrivacyPolicyEnforcer {

    private val blockedTrackingQueryParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "_r", "_t", "tt_from", "sender_device", "is_from_webapp", "checksum",
        "sec_uid", "u_code", "share_app_id", "share_link_id"
    )

    private val blockedRequestHeaders = setOf(
        "cookie", "cookie2", "authorization", "x-forwarded-for",
        "x-real-ip", "x-device-id", "x-install-id", "x-openudid"
    )

    /**
     * Minimal, non-identifying standard header set.
     * Never claims anonymous user identity or transmits device/account identifiers.
     */
    private const val MINIMAL_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    val noCookieJar: CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // Strictly discard all cookies in Anonymous Mode.
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
    }

    fun sanitizeHttpsUrl(rawUrl: String): HttpUrl? {
        val parsed = rawUrl.trim().toHttpUrlOrNull() ?: return null
        if (!parsed.isHttps) return null
        val builder = parsed.newBuilder()
        parsed.queryParameterNames
            .filter { it.lowercase() in blockedTrackingQueryParams }
            .forEach { builder.removeAllQueryParameters(it) }
        return builder.build()
    }

    fun isSafeHttpsUrl(rawUrl: String): Boolean {
        val parsed = rawUrl.trim().toHttpUrlOrNull() ?: return false
        return parsed.isHttps
    }

    fun createPrivacyOkHttpClient(
        connectTimeoutMs: Long = 6_000L,
        readTimeoutMs: Long = 8_000L
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(noCookieJar)
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .followRedirects(true)
            .followSslRedirects(false) // Prevent HTTPS -> HTTP downgrade
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(6_000L, TimeUnit.MILLISECONDS)
            .addInterceptor(PrivacySanitizingInterceptor())
            .build()
    }

    private class PrivacySanitizingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            if (!original.url.isHttps) {
                throw IOException("Blocked non-HTTPS request to preserve privacy")
            }

            val sanitizedUrl = sanitizeHttpsUrl(original.url.toString()) ?: original.url
            val builder = original.newBuilder().url(sanitizedUrl)

            // Strip any sensitive/identifying headers
            original.headers.names().forEach { name ->
                if (name.lowercase() in blockedRequestHeaders) {
                    builder.removeHeader(name)
                }
            }

            builder.header("User-Agent", MINIMAL_USER_AGENT)
            builder.header("Accept-Language", "en-US,en;q=0.9")
            builder.header("DNT", "1")
            builder.header("Sec-GPC", "1")

            return chain.proceed(builder.build())
        }
    }
}
