package com.neverlands.anlc.data.remote

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebViewCookieJar : okhttp3.CookieJar {

    private val cookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookiesString = cookieManager.getCookie(urlString)
        if (cookiesString != null && cookiesString.isNotEmpty()) {
            val cookies = mutableListOf<Cookie>()
            val parts = cookiesString.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (part in parts) {
                val cookie = Cookie.parse(url, part)
                if (cookie != null) {
                    cookies.add(cookie)
                }
            }
            return cookies
        }
        return emptyList()
    }

    fun clearCookies(host: String) {
        val cookies = cookieManager.getCookie(host)
        if (cookies != null) {
            val cookieParts = cookies.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (cookiePart in cookieParts) {
                val parts = cookiePart.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (parts.isNotEmpty()) {
                    val cookieName = parts[0].trim()
                    cookieManager.setCookie(host, "$cookieName=; Max-Age=-1")
                }
            }
        }
    }
}
