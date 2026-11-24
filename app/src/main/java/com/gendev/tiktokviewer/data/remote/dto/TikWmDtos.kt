package com.gendev.tiktokviewer.data.remote.dto

import com.squareup.moshi.Json

data class TikWmSingleResponse(
    @Json(name = "code") val code: Int? = null,
    @Json(name = "msg") val msg: String? = null,
    @Json(name = "data") val data: TikWmVideoDto? = null
)

data class TikWmFeedListResponse(
    @Json(name = "code") val code: Int? = null,
    @Json(name = "msg") val msg: String? = null,
    @Json(name = "data") val data: List<TikWmVideoDto>? = null
)

data class TikWmSearchPayload(
    @Json(name = "videos") val videos: List<TikWmVideoDto>? = null,
    @Json(name = "cursor") val cursor: Long? = null,
    @Json(name = "hasMore") val hasMore: Boolean? = null
)

data class TikWmSearchResponse(
    @Json(name = "code") val code: Int? = null,
    @Json(name = "msg") val msg: String? = null,
    @Json(name = "data") val data: TikWmSearchPayload? = null
)

data class TikWmUserPostsPayload(
    @Json(name = "videos") val videos: List<TikWmVideoDto>? = null,
    @Json(name = "cursor") val cursor: String? = null,
    @Json(name = "hasMore") val hasMore: Boolean? = null
)

data class TikWmUserPostsResponse(
    @Json(name = "code") val code: Int? = null,
    @Json(name = "msg") val msg: String? = null,
    @Json(name = "data") val data: TikWmUserPostsPayload? = null
)

data class TikWmVideoDto(
    @Json(name = "video_id") val videoId: String? = null,
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "origin_cover") val originCover: String? = null,
    @Json(name = "play") val play: String? = null,
    @Json(name = "wmplay") val wmPlay: String? = null,
    @Json(name = "hdplay") val hdPlay: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "author") val author: TikWmAuthorDto? = null
)

data class TikWmAuthorDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "unique_id") val uniqueId: String? = null,
    @Json(name = "nickname") val nickname: String? = null
)
