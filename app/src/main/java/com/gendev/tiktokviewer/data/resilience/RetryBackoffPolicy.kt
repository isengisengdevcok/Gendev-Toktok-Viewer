package com.gendev.tiktokviewer.data.resilience

import com.gendev.tiktokviewer.domain.model.ProviderResult
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

class RetryBackoffPolicy(
    private val maxAttempts: Int = 2,
    private val baseDelayMs: Long = 350L,
    private val multiplier: Double = 2.0,
    private val maxDelayMs: Long = 2_400L,
    private val delayFn: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun <T> execute(block: suspend () -> ProviderResult<T>): ProviderResult<T> {
        var lastResult: ProviderResult<T>? = null
        for (attempt in 0 until maxAttempts) {
            val result = block()
            when (result) {
                is ProviderResult.Success,
                is ProviderResult.RateLimited,
                is ProviderResult.AccessRestricted,
                is ProviderResult.ContentUnavailable -> {
                    // Never retry HTTP 429, HTTP 403, CAPTCHA, or unavailable content.
                    return result
                }
                is ProviderResult.NetworkError -> {
                    lastResult = result
                    if (attempt < maxAttempts - 1) {
                        val backoff = min(
                            (baseDelayMs * multiplier.pow(attempt.toDouble())).toLong(),
                            maxDelayMs
                        )
                        delayFn(backoff)
                    }
                }
            }
        }
        return lastResult ?: ProviderResult.ContentUnavailable("Request failed without result")
    }
}
