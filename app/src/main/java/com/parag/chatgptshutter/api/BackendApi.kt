package com.parag.chatgptshutter.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class AiResponse(
    val response: String?,
    val error: String?
)

interface BackendApi {
    @Multipart
    @POST("api/analyze-photo")
    suspend fun analyzePhoto(
        @Part image: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody
    ): Response<AiResponse>
}
