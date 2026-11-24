package com.gendev.tiktokviewer.data.remote.dto

import com.squareup.moshi.Json

data class TikTokOEmbedDto(
    @Json(name = "version") val version: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "author_url") val authorUrl: String? = null,
    @Json(name = "author_name") val authorName: String? = null,
    @Json(name = "author_unique_id") val authorUniqueId: String? = null,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "html") val html: String? = null,
    @Json(name = "embed_product_id") val embedProductId: String? = null,
    @Json(name = "provider_name") val providerName: String? = null
)
