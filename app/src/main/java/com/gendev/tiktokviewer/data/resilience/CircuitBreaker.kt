package com.gendev.tiktokviewer.data.resilience

import com.gendev.tiktokviewer.domain.model.CircuitState
import com.gendev.tiktokviewer.domain.model.EndpointHealth
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.VideoSourceKind

object UpstreamBlockDetector {
    private val captchaSignatures = listOf(
        "captcha",
        "tt_captcha",
        "verify to continue",
        "drag the puzzle",
        "g-recaptcha",
        "hcaptcha",
        "cf-challenge",
        "attention required! | cloudflare",
        "security check"
    )

    private val forbiddenSignatures = listOf(
        "access denied",
        "forbidden",
        "video is private",
        "account is private",
        "content unavailable in your region"
    )

    fun detectRestriction(
        statusCode: Int,
        bodySnippet: String?,
        endpoint: String,
        retryAfterHeader: String? = null
    ): ProviderResult<Nothing>? {
        val normalizedBody = bodySnippet?.lowercase().orEmpty()

        if (statusCode == 429) {
            val retrySeconds = retryAfterHeader?.toLongOrNull()?.coerceIn(10L, 600L) ?: 60L
            return ProviderResult.RateLimited(
                endpoint = endpoint,
                retryAfterSeconds = retrySeconds,
                message = "Upstream rate limit (HTTP 429). Requests paused for ${retrySeconds}s."
            )
        }

        val hasCaptcha = captchaSignatures.any { normalizedBody.contains(it) }
        if (hasCaptcha) {
            return ProviderResult.AccessRestricted(
                endpoint = endpoint,
                statusCode = if (statusCode in 200..599) statusCode else 403,
                isCaptcha = true,
                message = "Upstream presented a verification challenge (CAPTCHA)."
            )
        }

        if (statusCode == 403 || statusCode == 401 || forbiddenSignatures.any { normalizedBody.contains(it) }) {
            return ProviderResult.AccessRestricted(
                endpoint = endpoint,
                statusCode = if (statusCode == 401 || statusCode == 403) statusCode else 403,
                isCaptcha = false,
                message = "Access restricted by upstream endpoint (HTTP $statusCode)."
            )
        }

        return null
    }
}

class EndpointCircuitBreaker(
    val endpointId: String,
    val label: String,
    val sourceKind: VideoSourceKind,
    private val failureThreshold: Int = 2,
    private val openCooldownMs: Long = 30_000L,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var state: CircuitState = CircuitState.CLOSED
    private var failureCount: Int = 0
    private var openedAtMs: Long = 0L
    private var rateLimitedUntilMs: Long = 0L
    private var lastLatencyMs: Long? = null
    private var lastStatusMessage: String = "Standby"

    @Synchronized
    fun canAttempt(): Boolean {
        val now = nowProvider()
        if (now < rateLimitedUntilMs) {
            return false
        }
        return when (state) {
            CircuitState.CLOSED -> true
            CircuitState.OPEN -> {
                if (now - openedAtMs >= openCooldownMs) {
                    state = CircuitState.HALF_OPEN
                    lastStatusMessage = "Probing recovery"
                    true
                } else {
                    false
                }
            }
            CircuitState.HALF_OPEN -> true
        }
    }

    @Synchronized
    fun recordSuccess(latencyMs: Long?) {
        state = CircuitState.CLOSED
        failureCount = 0
        openedAtMs = 0L
        rateLimitedUntilMs = 0L
        lastLatencyMs = latencyMs
        lastStatusMessage = if (latencyMs != null) "Healthy (${latencyMs}ms)" else "Healthy"
    }

    @Synchronized
    fun recordFailure(result: ProviderResult<*>, latencyMs: Long?) {
        val now = nowProvider()
        lastLatencyMs = latencyMs
        when (result) {
            is ProviderResult.RateLimited -> {
                failureCount += 1
                state = CircuitState.OPEN
                openedAtMs = now
                rateLimitedUntilMs = now + (result.retryAfterSeconds * 1000L)
                lastStatusMessage = "HTTP 429 cooldown (${result.retryAfterSeconds}s)"
            }
            is ProviderResult.AccessRestricted -> {
                failureCount += 1
                state = CircuitState.OPEN
                openedAtMs = now
                lastStatusMessage = if (result.isCaptcha) "CAPTCHA challenge" else "HTTP ${result.statusCode} blocked"
            }
            is ProviderResult.NetworkError -> {
                failureCount += 1
                lastStatusMessage = result.causeType
                if (state == CircuitState.HALF_OPEN || failureCount >= failureThreshold) {
                    state = CircuitState.OPEN
                    openedAtMs = now
                }
            }
            is ProviderResult.ContentUnavailable -> {
                lastStatusMessage = "No content"
            }
            is ProviderResult.Success -> Unit
        }
    }

    @Synchronized
    fun reset() {
        state = CircuitState.CLOSED
        failureCount = 0
        openedAtMs = 0L
        rateLimitedUntilMs = 0L
        lastStatusMessage = "Reset ready"
    }

    @Synchronized
    fun snapshot(): EndpointHealth {
        val now = nowProvider()
        val effectiveState = if (state == CircuitState.OPEN && now >= openedAtMs + openCooldownMs && now >= rateLimitedUntilMs) {
            CircuitState.HALF_OPEN
        } else {
            state
        }
        return EndpointHealth(
            endpointId = endpointId,
            label = label,
            sourceKind = sourceKind,
            circuitState = effectiveState,
            failureCount = failureCount,
            lastLatencyMs = lastLatencyMs,
            rateLimitedUntilEpochMs = if (rateLimitedUntilMs > now) rateLimitedUntilMs else null,
            isHealthy = effectiveState == CircuitState.CLOSED && now >= rateLimitedUntilMs,
            lastStatusMessage = lastStatusMessage
        )
    }
}
