package com.neverlands.anlc.webview

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.neverlands.anlc.data.remote.ApiClientFactory
import com.neverlands.anlc.data.remote.FileLogger // Import FileLogger
import java.io.ByteArrayInputStream
import okhttp3.Headers // Import Headers
import okhttp3.Headers.Companion.toHeaders // Added this import
import android.content.Context // Ensure Context is imported

class WebViewRequestInterceptor(private val context: Context) { // Modified constructor

    fun interceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val method = request.method
        val requestHeaders = request.requestHeaders

        // Log request details
        FileLogger.log(context, "WebViewRequest - Request URL: $url, Method: $method, Headers: $requestHeaders") // Passed context

        // Intercept .js files as before
        if (url.endsWith(".js")) {
            try {
                val client = ApiClientFactory.okHttpClient
                val okHttpRequest = okhttp3.Request.Builder()
                    .url(url)
                    .headers(requestHeaders.toHeaders()) // Fixed Headers.of
                    .build()
                val response = client.newCall(okHttpRequest).execute()

                // Log response details
                FileLogger.log(context, "WebViewRequest - Response for $url - Code: ${response.code}, Headers: ${response.headers}") // Passed context

                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: return null
                    val stream = ByteArrayInputStream(bytes)
                    return WebResourceResponse("text/javascript", "windows-1251", stream)
                }
            } catch (e: Exception) {
                FileLogger.log(context, "WebViewRequest - Error intercepting JS: ${e.message}") // Passed context
                return null
            }
        }
        return null // Let WebView handle other requests normally
    }
}