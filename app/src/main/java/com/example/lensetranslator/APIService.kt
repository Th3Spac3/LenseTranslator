package com.example.lensetranslator

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface APIService {
    @POST("/translate")
    fun getTranslation(
        @Query(value = "to")to:String,
        @Query(value = "api-version")apiVersion:String,
        @Query(value = "profanityAction")action:String,
        @Query(value = "textType")textType:String,
        @Header(value = "X-RapidAPI-Key")key:String,
        @Header(value = "X-RapidAPI-Host")host:String,
        @Body requestBody: RequestBody): Call<ResponseBody>
}