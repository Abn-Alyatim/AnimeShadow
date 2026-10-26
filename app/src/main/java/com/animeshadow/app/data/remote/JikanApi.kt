package com.animeshadow.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Jikan v4 (unofficial MyAnimeList API): MAL score, scorers, members, content rating and the MAL main picture.
 * Optional enrichment: when Jikan is down the app falls back to the MAL page itself, then to AniList.
 */
interface JikanApi {
    @GET("v4/anime/{id}")
    suspend fun anime(@Path("id") malId: Int): JikanAnimeResponse
}

@Serializable
data class JikanAnimeResponse(val data: JikanAnime? = null)

@Serializable
data class JikanAnime(
    val mal_id: Int,
    val score: Double? = null,
    val scored_by: Int? = null,
    val members: Int? = null,
    val rank: Int? = null,
    val rating: String? = null,
    val images: JikanImages? = null,
)

@Serializable
data class JikanImages(val jpg: JikanImage? = null, val webp: JikanImage? = null)

@Serializable
data class JikanImage(val image_url: String? = null, val small_image_url: String? = null, val large_image_url: String? = null)
