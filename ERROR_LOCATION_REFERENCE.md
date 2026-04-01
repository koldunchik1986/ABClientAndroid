# COMPREHENSIVE ERROR LOCATION REFERENCE

## Error #1: FileLogger.clearAllLogs() Missing

### Location 1: SettingsActivity.java - Line 637

**File:** `app/src/main/java/ru/neverlands/abclient/SettingsActivity.java`  
**Line:** 637  
**Context (lines 632-642):**
```java
            // Очистка логов
            Preference clearLogsPref = findPreference("clear_logs");
            if (clearLogsPref != null) {
                clearLogsPref.setOnPreferenceClickListener(preference -> {
                    FileLogger.clearAllLogs();  // ❌ ERROR: Method doesn't exist
                    return true;
                });
            }
```

**Error Message:**
```
SettingsActivity.java:637: error: cannot find symbol
  symbol:   method clearAllLogs()
  location: class FileLogger
```

**Fix:** Add clearAllLogs() method to FileLogger.java

---

## Error #2: CustomDebugLogger Not Imported

### Location 1: ApiRepository.java - 16 Errors on Multiple Lines

**File:** `app/src/main/java/ru/neverlands/abclient/repository/ApiRepository.java`  
**Import Section (lines 1-29):**
```java
package ru.neverlands.abclient.repository;

import android.content.Context;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.network.NetworkClient;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.FileLogger;
// ❌ MISSING: import ru.neverlands.abclient.utils.CustomDebugLogger;
```

**Error Lines (16 errors total):**

| Line | Code | Error |
|------|------|-------|
| 193 | `CustomDebugLogger.log("REQUEST_URL: " + request.url());` | cannot find symbol variable CustomDebugLogger |
| 194 | `CustomDebugLogger.log("REQUEST_HEADERS: " + request.headers().toString());` | cannot find symbol variable CustomDebugLogger |
| 200 | `CustomDebugLogger.log("RESPONSE_ERROR: " + e.getMessage());` | cannot find symbol variable CustomDebugLogger |
| 207 | `CustomDebugLogger.log("RESPONSE_CODE: " + response.code());` | cannot find symbol variable CustomDebugLogger |
| 208 | `CustomDebugLogger.log("RESPONSE_BODY: " + responseBody);` | cannot find symbol variable CustomDebugLogger |
| 228 | `CustomDebugLogger.log("REQUEST_PREPARATION_ERROR: " + e.getMessage());` | cannot find symbol variable CustomDebugLogger |
| 248 | `CustomDebugLogger.log("REQUEST_URL: " + request.url());` | cannot find symbol variable CustomDebugLogger |
| 249 | `CustomDebugLogger.log("REQUEST_HEADERS: " + request.headers().toString());` | cannot find symbol variable CustomDebugLogger |
| 254 | `CustomDebugLogger.log("RESPONSE_ERROR: " + e.getMessage());` | cannot find symbol variable CustomDebugLogger |
| 261 | `CustomDebugLogger.log("RESPONSE_CODE: " + response.code());` | cannot find symbol variable CustomDebugLogger |
| 262 | `CustomDebugLogger.log("RESPONSE_BODY: " + responseBody);` | cannot find symbol variable CustomDebugLogger |
| 283 | `CustomDebugLogger.log("REQUEST_PREPARATION_ERROR: " + e.getMessage());` | cannot find symbol variable CustomDebugLogger |
| 380 | `CustomDebugLogger.log("DOWNLOAD_FILE_URL: " + request.url());` | cannot find symbol variable CustomDebugLogger |
| 381 | `CustomDebugLogger.log("DOWNLOAD_FILE_PROXY_REQUIRED: "...);` | cannot find symbol variable CustomDebugLogger |
| 388 | `CustomDebugLogger.log("DOWNLOAD_FILE_ERROR: " + e.getMessage());` | cannot find symbol variable CustomDebugLogger |
| 395 | `CustomDebugLogger.log("DOWNLOAD_FILE_HTTP_ERROR: code=" + response.code()...);` | cannot find symbol variable CustomDebugLogger |

**Fix:** Add import at line 30:
```java
import ru.neverlands.abclient.utils.CustomDebugLogger;
```

---

## Error #3: FileLogger Not Imported in ChatFilter.java

### Location 1: ChatFilter.java - Line 107

**File:** `app/src/main/java/ru/neverlands/abclient/utils/ChatFilter.java`  
**Import Section (lines 1-11):**
```java
package ru.neverlands.abclient.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.manager.ChatUserList;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.model.ChatUser;
// ❌ MISSING: import ru.neverlands.abclient.utils.FileLogger;
```

**Error Location (line 107):**
```java
FileLogger.log("TexLog: Бой против " + AppVars.LastBoiSostav + " завершен (" + AppVars.LastBoiLog + ")");
```

**Error Message:**
```
ChatFilter.java:107: error: cannot find symbol
  symbol:   method log(String)
  location: class FileLogger
```

**Fix:** Add import after line 11:
```java
import ru.neverlands.abclient.utils.FileLogger;
```

---

## Related Files (Will Work After Fixes Applied)

### MainActivity.java - Lines 201, 208, 225, 232, 238, 245, 349, 4146

**File:** `app/src/main/java/ru/neverlands/abclient/MainActivity.java`  
**Status:** ✅ Will work once FileLogger.java gets log(String) method (ALREADY EXISTS but old build used old version)

**Affected Lines:**
```java
Line 201:  FileLogger.trace("chat_poll", "RECOVERED code=" + httpCode + "...");
Line 208:  FileLogger.warn("chat_poll", "DEGRADED code=" + httpCode + "...");
Line 225:  FileLogger.error("chat_poll", "requestChatRefresh loadUrl failed, url=" + url, t);
Line 3306: ru.neverlands.abclient.utils.FileLogger.log("MainActivity: onDestroy() called.");
Line 3552: FileLogger.trace("chat_poll", "defer show=1 by room-collision guard, waitMs=" + waitMs + "...");
Line 3565: FileLogger.error("chat_poll", "requestChatRefresh loadUrl failed, url=" + url, t);
Line 3571: FileLogger.warn("chat_poll", "requestChatRefresh retry after WebView rebind, url=" + url);
Line 3574: FileLogger.error("chat_poll", "requestChatRefresh retry failed, url=" + url, retryError);
Line 4146: FileLogger.log("Page loaded: " + url);  // ❌ May fail if FileLogger.log doesn't exist
```

**Lines with Problems:**
- Line 4146 uses `FileLogger.log(String)` which needs the method

### WebViewCookieJar.java - Lines 21, 24, 33, 35, 46

**File:** `app/src/main/java/ru/neverlands/abclient/WebViewCookieJar.java`  
**Status:** ✅ Will work once FileLogger.java has log(String) method

**Error-prone Lines:**
```java
Line 21: FileLogger.log("WebViewCookieJar: Saving " + cookies.size() + " cookies for " + urlString);
Line 24: FileLogger.log("  -> " + cookie.toString());
Line 33: FileLogger.log("WebViewCookieJar: Loading cookies for " + urlString);
Line 35: FileLogger.log("  -> Raw cookies: " + cookiesString);
Line 46: FileLogger.log("  -> No cookies found.");
```

### MainPhp.java - Line 5889

**File:** `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`  
**Status:** ✅ Will work once FileLogger.java has log(String) method

**Error-prone Line:**
```java
Line 5889: ru.neverlands.abclient.utils.FileLogger.log("Error during mainPhpInv processing: \n" + sw);
```

### SettingsActivity.java - Line 637

**File:** `app/src/main/java/ru/neverlands/abclient/SettingsActivity.java`  
**Status:** ⚠️ Will fail due to missing clearAllLogs() method (needs PATCH 1)

**Error Line:**
```java
Line 637: FileLogger.clearAllLogs();  // ❌ Method doesn't exist
```

---

## Logger Method Availability Status

### FileLogger.java Current Methods (app/src/main/java/ru/neverlands/abclient/utils/FileLogger.java)

**Line 39:**
```java
public static void trace(String chain, String message) {
    write("TRACE", chain, message, null);
}
```
✅ EXISTS - Used by MainActivity

**Line 43:**
```java
public static void log(String message) {
    trace("FileLogger", message);
}
```
✅ EXISTS - Used by multiple files (but apparently not in last compiled version)

**Line 47:**
```java
public static void warn(String chain, String message) {
    write("WARN", chain, message, null);
}
```
✅ EXISTS - Used by MainActivity

**Line 51:**
```java
public static void error(String chain, String message, Throwable error) {
    write("ERROR", chain, message, error);
}
```
✅ EXISTS - Used by MainActivity, ChatFilter

**Line 59:**
```java
public static void proxyPool(String message) {
    writeToProxySegment("TRACE", message, null);
}
```
✅ EXISTS

**Line 63:**
```java
public static void proxyPoolError(String message, Throwable error) {
    writeToProxySegment("ERROR", message, error);
}
```
✅ EXISTS

**Missing (needs to be added after line 63):**
```java
public static void clearAllLogs() {
    // ... implementation ...
}
```
❌ MISSING - Used by SettingsActivity line 637

**Missing (needs to be added as helper):**
```java
private static void deleteRecursive(File fileOrDirectory) {
    // ... implementation ...
}
```
❌ MISSING - Helper for clearAllLogs()

---

## DebugLogger Status (app/src/main/java/ru/neverlands/abclient/utils/DebugLogger.java)

### AuthManager.java Status

**File:** `app/src/main/java/ru/neverlands/abclient/AuthManager.java`  
**Import (line 27):**
```java
import ru.neverlands.abclient.utils.DebugLogger;
```
✅ CORRECTLY IMPORTED

**Usage Lines (14 total):**
```java
Line 185: DebugLogger.log("AuthManager: 3. Final GET request\n" + mainRequest);
Line 187: DebugLogger.log("AuthManager: 3. Final GET response\n" + mainResponse);
Line 193: DebugLogger.log("AuthManager: Full Authorization SUCCESS.");
Line 197: DebugLogger.log("AuthManager: Authorization FAILED: " + e.getMessage());
Line 240: DebugLogger.log("AuthManager: 2. Captcha Login POST request\n" + loginRequest);
Line 242: DebugLogger.log("AuthManager: 2. Captcha Login POST response\n" + loginResponse);
Line 255: DebugLogger.log("AuthManager: Captcha detected again. URL: " + captchaUrl + ", vcode: " + newVcode);
Line 263: DebugLogger.log("AuthManager: 2. Captcha Login POST SUCCESS.");
Line 277: DebugLogger.log("AuthManager: 3. Final GET request\n" + mainRequest);
Line 279: DebugLogger.log("AuthManager: 3. Final GET response\n" + mainResponse);
Line 285: DebugLogger.log("AuthManager: Full Authorization SUCCESS.");
Line 289: DebugLogger.log("AuthManager: Authorization FAILED: " + e.getMessage());
Line 358: DebugLogger.log("AuthManager: authBaseUrl=" + baseUrl + ", proxyActive=" + proxyActive);
Line 430: DebugLogger.log("AuthManager: collected cookies count=" + result.size() + " names=[" + names + "]");
```
✅ ALL SHOULD WORK - DebugLogger.log(String) exists and is properly imported

---

## Call Count By File

| File | Logger | Method | Count | Status |
|------|--------|--------|-------|--------|
| AuthManager.java | DebugLogger | log() | 14 | ✅ OK |
| MainActivity.java | FileLogger | trace/warn/error/log | 8 | ⚠️ Depends on method availability |
| ApiRepository.java | CustomDebugLogger | log() | 16 | ❌ NOT IMPORTED |
| WebViewCookieJar.java | FileLogger | log() | 5 | ⚠️ Depends on method availability |
| ChatFilter.java | FileLogger | log() | 1 | ❌ NOT IMPORTED |
| MainPhp.java | FileLogger | log() | 1 | ⚠️ Depends on method availability |
| SettingsActivity.java | FileLogger | clearAllLogs() | 1 | ❌ METHOD MISSING |

---

## Summary by Severity

### 🔴 CRITICAL (Build Blocking)
1. **FileLogger.clearAllLogs() missing** - SettingsActivity.java line 637 (1 error)
2. **CustomDebugLogger not imported** - ApiRepository.java lines 193, 194, 200, 207, 208, 228, 248, 249, 254, 261, 262, 283, 380, 381, 388, 395 (16 errors)

### 🟡 HIGH (Will fail once code compiles past critical errors)
3. **FileLogger not imported** - ChatFilter.java line 107 (1 error)
4. **FileLogger.log() not available** - MainActivity.java, WebViewCookieJar.java, MainPhp.java, SettingsActivity.java (15 errors)

### ✅ GREEN (Already Working)
- DebugLogger in AuthManager.java - properly imported and used (0 errors)

---

**Reference Document Version:** 1.0  
**Generated:** 2026-04-01  
**Total Unique Error Locations:** 18
