# SessionManager - Единая система управления сессией (проектирование)

## Проблема текущей архитектуры
1. **VCode глобально** в `AppVars.VCode` - теряется при переключении контекстов
2. **No central validation** - каждый модуль проверяет VCode по-своему
3. **Нет парсинга из HTML** - VCode загружается один раз, потом кэшируется неправильно
4. **Параллельные запросы** конфликтуют - af_tick может перезаписать vcode во время рыбалки

## Архитектура SessionManager (по аналогии с C#)

```
┌─ SessionManager (singleton) ──────────────────────────────┐
│                                                             │
│  1. parseVCodeFromHtml(html) → VCode                       │
│  2. getValidVCodeForAction(actionName) → VCode or reload   │
│  3. validateSession() → boolean                            │
│  4. onServerError(errorType) → recovery strategy           │
│                                                             │
│  Internal:                                                  │
│  - currentContext: SessionContext (HTML, VCode, timestamp) │
│  - contextVersion: long (инкрементирующаяся версия)      │
│  - lastRefreshAtMs: long (когда последний раз освежили)   │
└─────────────────────────────────────────────────────────────┘

┌─ SessionContext ──────────────────────────────────────┐
│                                                        │
│  data class SessionContext {                          │
│    val htmlContent: String                            │
│    val parsedVCode: String                            │
│    val timestamp: Long                                │
│    val source: String ("fight", "fish", "main" и т.д)│
│    val contextVersion: Long                           │
│  }                                                    │
└────────────────────────────────────────────────────────┘

┌─ Workflow: Рыбалка ────────────────────────────────────┐
│                                                        │
│  1. FishAjaxPhp.executeFish()                         │
│  2. SessionManager.getValidVCodeForAction("fish")     │
│  3. ├─ Проверить текущий VCode + возраст            │
│  4. ├─ Если >5 мин: перезагрузить main.php          │
│  5. ├─ Вернуть свежий VCode                          │
│  6. FishAjaxPhp.sendAjax(vcode)                       │
│  7. SessionManager.onServerResponse(html) [парседь]  │
│                                                       │
└───────────────────────────────────────────────────────┘
```

## Ключевые Отличия от Текущей Системы

| Аспект | Текущая | SessionManager |
|--------|--------|-------------------|
| Хранилище VCode | `AppVars.VCode` (глобальное) | `SessionContext` (локальный контекст) |
| Парсинг VCode | Один раз при загрузке озера | На **каждый HTML ответ** |
| Валидация перед использованием | Нет | Да - getValidVCodeForAction() |
| Версионирование контекста | Нет | Да - contextVersion |
| Обработка ошибок | Ad-hoc в каждом модуле | Единый onServerError() |
| Refresh strategy | Manual в разных местах | Автоматический в getValidVCodeForAction() |
| Синхронизация между модулями | Конфликты (af_tick vs fish) | OrderedByVersion - no conflicts |

## Реализация в Android

### Файл 1: SessionContext.kt 
```kotlin
data class SessionContext(
    val htmlContent: String,          // Полный HTML текущей страницы
    val parsedVCode: String,          // Распарсенный VCode
    val timestamp: Long,              // Когда был загружен
    val source: String,               // "fight", "fish", "main", "pinfo"
    val contextVersion: Long,         // Возрастающий номер версии
    val phpsessid: String = "",       // PHPSESSID из cookies (если нужно)
    val attributes: Map<String, String> = emptyMap()  // Доп. данные
) {
    fun isExpired(maxAgeMs: Long = 300_000L): Boolean =
        (System.currentTimeMillis() - timestamp) > maxAgeMs
    
    fun isSameSessionAs(other: SessionContext): Boolean =
        phpsessid == other.phpsessid && contextVersion >= other.contextVersion - 1
}
```

### Файл 2: SessionManager.kt
```kotlin
class SessionManager private constructor() {
    companion object {
        private val instance = SessionManager()
        fun getInstance() = instance
    }

    private var currentContext: SessionContext? = null
    private var contextLock = ReentrantReadWriteLock()
    private val TAG = "SessionManager"

    /**
     * Парсить VCode из HTML (вызывается из WebViewInterceptor для каждого ответа)
     */
    fun parseVCodeFromHtml(html: String, source: String = "unknown"): SessionContext? {
        return try {
            val vcode = extractVCode(html)
            if (vcode.isNotEmpty()) {
                val context = SessionContext(
                    htmlContent = html,
                    parsedVCode = vcode,
                    timestamp = System.currentTimeMillis(),
                    source = source,
                    contextVersion = (currentContext?.contextVersion ?: 0L) + 1
                )
                contextLock.writeLock().withLock {
                    currentContext = context
                }
                Log.d(TAG, "✅ VCODE_PARSED: source=$source, vcode=${vcode.take(8)}..., version=${context.contextVersion}")
                context
            } else {
                Log.w(TAG, "⚠️ VCODE_PARSE_FAILED: no vcode in HTML from $source")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ VCODE_PARSE_ERROR: $e")
            null
        }
    }

    /**
     * Получить ВАЛИДНЫЙ VCode для использования в AJAX запросе.
     * Проверяет свежесть, при необходимости инициирует refresh.
     */
    fun getValidVCodeForAction(actionName: String, maxAgeMs: Long = 300_000L): String? {
        return contextLock.readLock().withLock {
            val context = currentContext ?: run {
                Log.w(TAG, "⚠️ NO_SESSION: actionName=$actionName - нет контекста")
                return@withLock null
            }

            if (context.parsedVCode.isEmpty()) {
                Log.w(TAG, "⚠️ EMPTY_VCODE: actionName=$actionName")
                scheduleSessionRefresh("empty_vcode_for_$actionName")
                return@withLock null
            }

            if (context.isExpired(maxAgeMs)) {
                Log.w(TAG, "⚠️ STALE_SESSION: actionName=$actionName, ageMs=${System.currentTimeMillis() - context.timestamp}")
                scheduleSessionRefresh("stale_session_for_$actionName")
                return@withLock null
            }

            Log.d(TAG, "✅ VALID_VCODE: actionName=$actionName, vcode=${context.parsedVCode.take(8)}..., ageMs=${System.currentTimeMillis() - context.timestamp}")
            context.parsedVCode
        }
    }

    /**
     * Обработка ошибки "Неверный код защиты" от сервера
     */
    fun onInvalidProtectionCodeError(failingVCode: String, actionName: String) {
        Log.e(TAG, "❌ INVALID_CODE_ERROR: actionName=$actionName, vcode=${failingVCode.take(8)}...")
        contextLock.writeLock().withLock {
            currentContext = null  // Инвалидировать текущий контекст
        }
        scheduleSessionRefresh("invalid_protection_code_$actionName")
    }

    /**
     * Запланировать refresh сессии (загрузка main.php)
     */
    private fun scheduleSessionRefresh(reason: String) {
        Log.d(TAG, "📋 REFRESH_SCHEDULED: reason=$reason")
        // Это будет trigged из MainActivity.loadUrl("main.php?go=ret&...")
        // SessionManager просто логирует причину
    }

    /**
     * Извлечь VCode из HTML
     * Ищет паттерны: vcode=..., var vcode = ..., и т.д.
     */
    private fun extractVCode(html: String): String {
        // Паттерны для разных源:
        val patterns = listOf(
            "vcode\\s*=\\s*['\"]([a-f0-9]{32})['\"]",  // vcode = "..."
            "vcode=([a-f0-9]{32})",                     // vcode=...
            "var\\s+vcode\\s*=\\s*['\"]([a-f0-9]{32})['\"]"  // var vcode = "..."
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern)
            val match = regex.find(html)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return ""
    }

    // Другие методы: getContext(), invalidateContext(), etc.
}
```

### Файл 3: Интеграция в WebViewInterceptor
```kotlin
// В WebViewInterceptor.onResponse():
val sessionContext = SessionManager.getInstance().parseVCodeFromHtml(
    html = processedHtml,
    source = determineSource(url)  // "fish", "fight", "main", etc.
)
```

### Файл 4: Интеграция в FishAjaxPhp
```kotlin
// В FishAjaxPhp.executeAjax():
fun sendFishAjax(primId: Int): Boolean {
    val vcode = SessionManager.getInstance().getValidVCodeForAction("fish_ajax", maxAgeMs = 120_000L)
    if (vcode == null) {
        Log.w(TAG, "Cannot send fish AJAX - no valid vcode, scheduling refresh")
        MainActivity.getInstance().reloadMainPhp()  // refresh
        return false
    }

    val url = "http://neverlands.ru/gameplay/ajax/fish_ajax.php?act=2&primid=$primId&vcode=$vcode&r=${System.currentTimeMillis()}"
    
    // На ошибку "неверный код защиты":
    onAjaxError { error ->
        if (error.contains("неверный код защиты")) {
            SessionManager.getInstance().onInvalidProtectionCodeError(vcode, "fish_ajax")
        }
    }
}
```

## Преимущества
1. ✅ **Одна точка **управления** VCode - SessionManager
2. ✅ **Автоматическая валидация** перед использованием
3. ✅ **Версионирование контекста** - no conflicts между модулями
4. ✅ **Одноразовая парсинг** - как в C#, забываем VCode сразу после использования
5. ✅ **Единая обработка ошибок** - onInvalidProtectionCodeError()
6. ✅ **Lock-free reads** для getValidVCodeForAction() (ReentrantReadWriteLock)
7. ✅ **Полная диагностика** - логи для каждого события

## Миграция (поэтапно)
1. Создать SessionManager.kt
2. **НЕ удалять** AppVars.VCode сразу - оставить для совместимости
3. Постепенно мигрировать модули:
   - WebViewInterceptor → parseVCodeFromHtml()
   - FishAjaxPhp → getValidVCodeForAction()
   - LezFight → getValidVCodeForAction()
   - MainPhp → getValidVCodeForAction()
4. После полной миграции → удалить AppVars.VCode

## Тестирование
- Unit: тестировать extractVCode() с разными HTML формами
- Integration: проверить parseVCodeFromHtml() из реальных WebView responses
- E2E: рыбалка + переключение контекста + проверить no "invalid code" errors
