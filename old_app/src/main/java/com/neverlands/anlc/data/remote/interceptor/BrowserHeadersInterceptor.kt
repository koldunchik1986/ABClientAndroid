package com.neverlands.anlc.data.remote.interceptor

import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder

class BrowserHeadersInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var originalRequest = chain.request()

        // Re-encode body for login request to windows-1251
        if (originalRequest.body is FormBody) {
            val originalFormBody = originalRequest.body as FormBody
            val newFormBodyBuilder = FormBody.Builder(charset("windows-1251"))
            for (i in 0 until originalFormBody.size) {
                val decodedName = URLDecoder.decode(originalFormBody.encodedName(i), "UTF-8")
                val decodedValue = URLDecoder.decode(originalFormBody.encodedValue(i), "UTF-8")
                newFormBodyBuilder.add(decodedName, decodedValue)
            }
            val newBody = newFormBodyBuilder.build()
            originalRequest = originalRequest.newBuilder()
                .post(newBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
        }

        val requestBuilder = originalRequest.newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.7258.138 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Host", "neverlands.ru")
            .header("Connection", "keep-alive")
            .header("Cache-Control", "max-age=0")

        // Add Referer and Origin based on the request
        val url = originalRequest.url
        if (url.encodedPath == "/game.php") {
            requestBuilder.header("Referer", "http://neverlands.ru/")
            requestBuilder.header("Origin", "http://neverlands.ru")
        } else if (url.encodedPath == "/main.php") {
            requestBuilder.header("Referer", "http://neverlands.ru/game.php")
        }

        val newRequest = requestBuilder.build()
        return chain.proceed(newRequest)
    }
}
