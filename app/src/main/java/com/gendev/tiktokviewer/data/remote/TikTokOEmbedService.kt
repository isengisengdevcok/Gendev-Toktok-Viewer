package com.gendev.tiktokviewer.data.remote

import com.gendev.tiktokviewer.data.remote.dto.TikTokOEmbedDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface TikTokOEmbedService {
    @GET("oembed")
    suspend fun getOEmbed(
        @Query("url") videoUrl: String
    ): Response<TikTokOEmbedDto>
}
