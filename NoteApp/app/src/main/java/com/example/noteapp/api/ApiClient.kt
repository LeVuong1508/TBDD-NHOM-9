package com.example.noteapp.api

import android.os.Build
import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private val BASE_URL: String by lazy {
        // Nếu đang chạy trên Android Emulator
        if (Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator")) {
            Log.d("ApiClient", "Using Emulator URL")
            "http://10.0.2.2:5000/"
        } else {
            // Thiết bị thật trong cùng mạng LAN
            Log.d("ApiClient", "Using LAN URL")
            "http://10.44.70.16:5000/"
        }
    }

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
