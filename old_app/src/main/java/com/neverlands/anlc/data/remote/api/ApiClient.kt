package com.neverlands.anlc.data.remote.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit-интерфейс для взаимодействия с API Neverlands.
 */
interface ApiClient {

    /**
     * Получает главную страницу для инициализации сессии и получения кук.
     */
    @GET("/")
    suspend fun getInitialPage(): Response<ResponseBody>

    /**
     * Отправляет данные для входа.
     */
    @FormUrlEncoded
    @POST("/game.php")
    suspend fun login(@Field("player_nick") login: String, @Field("player_password") pass: String): Response<ResponseBody>

    /**
     * Загружает главную страницу игры для проверки авторизации или для "пульса" сессии.
     */
    @GET("/main.php")
    suspend fun getMainPage(): Response<ResponseBody>
}
