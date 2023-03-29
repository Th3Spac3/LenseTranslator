package com.example.lensetranslator

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkSingleton {
    private val retrofit: Retrofit by lazy {
    Retrofit.Builder()
        .baseUrl("https://microsoft-translator-text.p.rapidapi.com/")
        //.addConverterFactory(GsonConverterFactory.create())
        .build()
    }
    fun get():Retrofit = retrofit
}