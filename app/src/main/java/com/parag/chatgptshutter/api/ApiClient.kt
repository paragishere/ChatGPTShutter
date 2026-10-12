package com.parag.chatgptshutter.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Configure this URL to your secure backend (e.g., https://your-app.fly.dev/)
    // Leave empty to trigger the setup error instruction screen.
    const val BACKEND_URL = ""

    private val okHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // SAFE LOGGING: Headers and Body are not fully logged to avoid leaking raw images
            // but you can change to BODY for deep debugging if necessary.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val apiService: BackendApi? by lazy {
        if (BACKEND_URL.isBlank()) {
            null
        } else {
            Retrofit.Builder()
                .baseUrl(if (BACKEND_URL.endsWith("/")) BACKEND_URL else "$BACKEND_URL/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BackendApi::class.java)
        }
    }
}
