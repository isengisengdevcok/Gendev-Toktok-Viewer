package com.gendev.tiktokviewer.domain.model

enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

data class EndpointHealth(
    val endpointId: String,
    val label: String,
    val sourceKind: VideoSourceKind,
    val circuitState: CircuitState,
    val failureCount: Int,
    val lastLatencyMs: Long?,
    val rateLimitedUntilEpochMs: Long?,
    val isHealthy: Boolean,
    val lastStatusMessage: String
)

sealed class ProviderResult<out T> {
    data class Success<T>(
        val data: T,
        val sourceKind: VideoSourceKind,
        val endpoint: String,
        val fromCache: Boolean = false
    ) : ProviderResult<T>()

    data class RateLimited(
        val endpoint: String,
        val retryAfterSeconds: Long,
        val message: String
    ) : ProviderResult<Nothing>()

    data class AccessRestricted(
        val endpoint: String,
        val statusCode: Int,
        val isCaptcha: Boolean,
        val message: String
    ) : ProviderResult<Nothing>()

    data class NetworkError(
        val endpoint: String,
        val message: String,
        val causeType: String
    ) : ProviderResult<Nothing>()

    data class ContentUnavailable(
        val message: String
    ) : ProviderResult<Nothing>()
}

data class PrivacyConfig(
    val anonymousModeDefault: Boolean = true,
    val customProxiTokBaseUrl: String = "",
    val autoClearCacheOnBackground: Boolean = true,
    val preferOfficialEmbedWhenBlocked: Boolean = true
)
