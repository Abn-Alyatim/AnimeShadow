package com.animeshadow.app.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AniListApi {
    @POST("/")
    suspend fun page(@Body request: GqlRequest): PageResponse

    @POST("/")
    suspend fun media(@Body request: GqlRequest): MediaResponse

    @POST("/")
    suspend fun character(@Body request: GqlRequest): CharacterResponse

    @POST("/")
    suspend fun staff(@Body request: GqlRequest): StaffResponse

    @POST("/")
    suspend fun studio(@Body request: GqlRequest): StudioResponse

    /** v1.2: the signed-in AniList user (needs the OAuth token). */
    @POST("/")
    suspend fun viewer(@Header("Authorization") bearer: String, @Body request: GqlRequest): ViewerResponse
}
