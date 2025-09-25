package com.neverlands.anlc.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * A more robust cookie jar that stores cookies per domain and filters by path.
 */
class SimpleCookieJar : CookieJar {

    private val cookieStore: ConcurrentHashMap<String, MutableList<Cookie>> = ConcurrentHashMap()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val newCookies = cookieStore.getOrPut(host) { mutableListOf() }

        cookies.forEach { cookie ->
            // Remove any old cookie with the same name
            newCookies.removeAll { it.name == cookie.name }
            // Add the new cookie
            newCookies.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookiesForHost = cookieStore[host] ?: return emptyList()

        val now = System.currentTimeMillis()
        // Return only non-expired cookies that match the path
        return cookiesForHost.filter { cookie ->
            cookie.expiresAt > now && url.encodedPath.startsWith(cookie.path)
        }
    }

    /**
     * Clears all cookies for a specific domain.
     */
    fun clearCookies(host: String) {
        cookieStore.remove(host)
    }

    /**
     * Gets all cookies for a specific domain.
     */
    fun getCookies(host: String): List<Cookie> {
        return cookieStore[host] ?: emptyList()
    }
}
