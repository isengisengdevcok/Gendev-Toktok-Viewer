package com.gendev.tiktokviewer.domain.model

enum class VideoSourceKind(val displayName: String) {
    PROXITOK_SELF_HOSTED("Self-Hosted Proxy"),
    PROXITOK_PUBLIC("ProxiTok Public"),
    TIKTOK_PUBLIC_ADAPTER("Public Content Adapter"),
    TIKTOK_OEMBED("TikTok oEmbed"),
    TIKTOK_EMBED_PLAYER("TikTok Official Embed")
}

enum class PlaybackMode(val badgeText: String) {
    EXO_DIRECT_STREAM("MEDIA3"),
    ISOLATED_EMBED_WEBVIEW("EMBED v3")
}

data class PublicVideo(
    val id: String,
    val title: String,
    val authorHandle: String,
    val authorDisplayName: String,
    val hashtags: List<String>,
    val thumbnailUrl: String?,
    val directMediaUrl: String?,
    val embedUrl: String,
    val canonicalUrl: String,
    val sourceKind: VideoSourceKind,
    val sourceLabel: String,
    val playbackMode: PlaybackMode,
    val durationSeconds: Int? = null
)

data class FeedPage(
    val videos: List<PublicVideo>,
    val nextCursor: String? = null,
    val sourceKind: VideoSourceKind,
    val sourceEndpoint: String,
    val noticeMessage: String? = null
)

data class PlaybackTarget(
    val videoId: String,
    val mode: PlaybackMode,
    val streamUrl: String,
    val sourceLabel: String
)

enum class SearchMode(val label: String) {
    AUTO("Auto"),
    LINK("Link"),
    HASHTAG("#Hashtag"),
    CREATOR("@Creator"),
    KEYWORD("Keyword")
}
