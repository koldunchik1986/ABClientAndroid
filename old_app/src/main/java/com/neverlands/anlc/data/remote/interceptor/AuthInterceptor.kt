package com.neverlands.anlc.data.remote.interceptor

import com.neverlands.anlc.data.local.ProfileManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * Перехватчик OkHttp, который имитирует логику авторизации из C# прокси.
 * Внедряет "флеш-пароль" в запросы к game.php, если они не содержат данных для входа.
 */
class AuthInterceptor : Interceptor {

    @Volatile
    private var passwordFlash: String? = null

    /**
     * Устанавливает пароль для последующих запросов.
     * @param password Пароль для флеш-запросов.
     */
    fun setPassword(password: String?) {
        this.passwordFlash = password
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val password = this.passwordFlash
        val url = originalRequest.url

        // Проверяем, что это запрос к game.php и у нас есть пароль
        if (password != null && url.host.contains("neverlands.ru") && url.encodedPath == "/game.php") {
            var hasLoginPass = false
            if (originalRequest.method == "POST" && originalRequest.body != null) {
                val bodyStr = requestBodyToString(originalRequest.body)
                if (bodyStr.contains("login=") && bodyStr.contains("pass=")) {
                    hasLoginPass = true
                }
            }

            // Если в запросе нет логина/пароля, внедряем флеш-пароль
            if (!hasLoginPass) {
                val newRequestBody = "password=$password"
                    .toRequestBody("application/x-www-form-urlencoded; charset=windows-1251".toMediaTypeOrNull())
                
                val newRequest = originalRequest.newBuilder()
                    .post(newRequestBody)
                    .build()
                
                return chain.proceed(newRequest)
            }
        }

        return chain.proceed(originalRequest)
    }

    private fun requestBodyToString(requestBody: okhttp3.RequestBody?): String {
        if (requestBody == null) return ""
        try {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            return buffer.readUtf8()
        } catch (e: Exception) {
            return ""
        }
    }
}