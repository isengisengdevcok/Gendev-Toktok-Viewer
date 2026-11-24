package com.gendev.tiktokviewer.data.resilience

import com.gendev.tiktokviewer.domain.model.EndpointHealth
import com.gendev.tiktokviewer.domain.model.ProviderResult
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import com.gendev.tiktokviewer.domain.provider.ProviderEndpointCall
import com.gendev.tiktokviewer.domain.provider.TikTokFallbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

class TikTokFallbackManagerImpl(
    private val okHttpClient: OkHttpClient,
    private val retryPolicy: RetryBackoffPolicy = RetryBackoffPolicy(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val healthCheckTargets: () -> List<Pair<String, String>> = { emptyList() }
) : TikTokFallbackManager {

    private val breakers = ConcurrentHashMap<String, EndpointCircuitBreaker>()
    private val rotationOffset = MutableStateFlow(0)
    private val _endpointHealthFlow = MutableStateFlow<List<EndpointHealth>>(emptyList())
    override val endpointHealthFlow: StateFlow<List<EndpointHealth>> = _endpointHealthFlow.asStateFlow()

    override fun registerOrUpdateEndpoint(
        endpointId: String,
        label: String,
        sourceKind: VideoSourceKind
    ) {
        breakers.putIfAbsent(
            endpointId,
            EndpointCircuitBreaker(
                endpointId = endpointId,
                label = label,
                sourceKind = sourceKind,
                nowProvider = nowProvider
            )
        )
        publishHealth()
    }

    override fun markEndpointResult(
        endpointId: String,
        label: String,
        sourceKind: VideoSourceKind,
        result: ProviderResult<*>,
        latencyMs: Long?
    ) {
        val breaker = breakers.getOrPut(endpointId) {
            EndpointCircuitBreaker(endpointId, label, sourceKind, nowProvider = nowProvider)
        }
        if (result is ProviderResult.Success) {
            breaker.recordSuccess(latencyMs)
        } else {
            breaker.recordFailure(result, latencyMs)
        }
        publishHealth()
    }

    override suspend fun <T> executeWithFallback(
        operationName: String,
        attempts: List<ProviderEndpointCall<T>>
    ): ProviderResult<T> {
        if (attempts.isEmpty()) {
            return ProviderResult.ContentUnavailable("No endpoints configured for $operationName")
        }

        val offset = rotationOffset.value % attempts.size
        val orderedAttempts = attempts.drop(offset) + attempts.take(offset)

        var mostInformativeFailure: ProviderResult<T>? = null

        for (attempt in orderedAttempts) {
            val breaker = breakers.getOrPut(attempt.endpointId) {
                EndpointCircuitBreaker(
                    endpointId = attempt.endpointId,
                    label = attempt.label,
                    sourceKind = attempt.sourceKind,
                    nowProvider = nowProvider
                )
            }

            if (!breaker.canAttempt()) {
                val snap = breaker.snapshot()
                if (mostInformativeFailure == null && snap.rateLimitedUntilEpochMs != null) {
                    val remaining = ((snap.rateLimitedUntilEpochMs - nowProvider()) / 1000L).coerceAtLeast(1L)
                    mostInformativeFailure = ProviderResult.RateLimited(
                        endpoint = attempt.label,
                        retryAfterSeconds = remaining,
                        message = "Rate limit cooldown active on ${attempt.label} (${remaining}s left)."
                    )
                }
                continue
            }

            val start = nowProvider()
            val result = retryPolicy.execute { attempt.block() }
            val elapsed = (nowProvider() - start).coerceAtLeast(0L)

            if (result is ProviderResult.Success) {
                breaker.recordSuccess(elapsed)
                publishHealth()
                return result
            } else {
                breaker.recordFailure(result, elapsed)
                publishHealth()
                mostInformativeFailure = pickHigherPriorityFailure(mostInformativeFailure, result)
            }
        }

        return mostInformativeFailure
            ?: ProviderResult.ContentUnavailable("All public providers are currently unavailable. Tap Retry or Switch Source.")
    }

    override suspend fun runHealthChecks(): List<EndpointHealth> = withContext(Dispatchers.IO) {
        val targets = healthCheckTargets()
        targets.forEach { (endpointId, url) ->
            val breaker = breakers[endpointId] ?: return@forEach
            val start = nowProvider()
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    val elapsed = nowProvider() - start
                    val restriction = UpstreamBlockDetector.detectRestriction(
                        statusCode = response.code,
                        bodySnippet = null,
                        endpoint = breaker.label,
                        retryAfterHeader = response.header("Retry-After")
                    )
                    if (restriction != null) {
                        breaker.recordFailure(restriction, elapsed)
                    } else if (response.isSuccessful || response.code in 300..399 || response.code == 405) {
                        breaker.recordSuccess(elapsed)
                    } else {
                        breaker.recordFailure(
                            ProviderResult.NetworkError(breaker.label, "HTTP ${response.code}", "HTTP_${response.code}"),
                            elapsed
                        )
                    }
                }
            } catch (t: Throwable) {
                val elapsed = nowProvider() - start
                breaker.recordFailure(
                    ProviderResult.NetworkError(
                        breaker.label,
                        t.message ?: "Connection failed",
                        t.javaClass.simpleName
                    ),
                    elapsed
                )
            }
        }
        publishHealth()
        _endpointHealthFlow.value
    }

    override fun rotateEndpointOrder() {
        rotationOffset.value = rotationOffset.value + 1
    }

    override fun resetCircuitBreakers() {
        breakers.values.forEach { it.reset() }
        publishHealth()
    }

    private fun publishHealth() {
        _endpointHealthFlow.value = breakers.values
            .map { it.snapshot() }
            .sortedBy { it.sourceKind.ordinal }
    }

    private fun <T> pickHigherPriorityFailure(
        current: ProviderResult<T>?,
        candidate: ProviderResult<T>
    ): ProviderResult<T> {
        if (current == null) return candidate
        fun priority(r: ProviderResult<*>): Int = when (r) {
            is ProviderResult.AccessRestricted -> if (r.isCaptcha) 4 else 3
            is ProviderResult.RateLimited -> 3
            is ProviderResult.NetworkError -> 2
            is ProviderResult.ContentUnavailable -> 1
            is ProviderResult.Success -> 0
        }
        return if (priority(candidate) >= priority(current)) candidate else current
    }
}
