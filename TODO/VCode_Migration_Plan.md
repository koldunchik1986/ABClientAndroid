# VCode Migration Plan для MainActivity.java

## Обзор

**Задача:** Мигрировать все 5 мест использования `AppVars.VCode` на `SessionManager`  
**Критичность:** CRITICAL  
**Время:** 2-3 часа + 1-2 часа тестирования  
**Риск:** MEDIUM (могут появиться "Неверный код защиты" ошибки)

---

## Точки миграции

### 1️⃣ **Миграция #1: adoptVCodeFromAutoSubmitPayload() [Строка 1526-1527]**

#### Текущий код:
```java
// Line 1521-1545
private void adoptVCodeFromAutoSubmitPayload(String payload) {
    String vcode = extractVCodeFromAutoSubmitPayload(payload);
    if (!vcode.isEmpty()) {
        if (!vcode.equals(AppVars.VCode)) {
            AppVars.VCode = vcode;  // ❌ ПРОБЛЕМА: напрямую в AppVars
            Log.d(TAG, "[BG_TRACE] VCode adopted from payload: " + vcode);
        }
    }
}
```

#### После миграции:
```java
private void adoptVCodeFromAutoSubmitPayload(String payload) {
    String vcode = extractVCodeFromAutoSubmitPayload(payload);
    if (!vcode.isEmpty()) {
        // ✅ Используем SessionManager для сохранения
        SessionManager sessionManager = SessionManager.getInstance();
        String cachedVCode = sessionManager.getCurrentVCode();
        
        if (!vcode.equals(cachedVCode)) {
            sessionManager.putVCode(vcode, "auto_submit_payload");
            Log.d(TAG, "[BG_TRACE] VCode adopted from payload: " + vcode);
            FileLogger.trace("[VCODE_ADOPT_SUBMIT] vcode=" + vcode);
        }
    }
}
```

#### Что нужно добавить в SessionManager (если нет):
```java
/**
 * Сохраняет новый VCode с указанным источником
 * @param vcode Новый код защиты
 * @param source Источник (например: "auto_submit_payload", "html_parse")
 */
public void putVCode(String vcode, String source) {
    writeLock.lock();
    try {
        this.currentVCode = vcode;
        this.vCodeSourceTag = source;
        this.vCodeTimestampMs = System.currentTimeMillis();
    } finally {
        writeLock.unlock();
    }
}

/**
 * Получает текущий кэшированный VCode (без проверки timeout)
 */
public String getCurrentVCode() {
    readLock.lock();
    try {
        return currentVCode != null ? currentVCode : "";
    } finally {
        readLock.unlock();
    }
}
```

**Результат:** Теперь VCode управляется SessionManager, что гарантирует версионирование при смене PHPSESSID.

---

### 2️⃣ **Миграция #2: checkServerTimerDrivenActions() - Reload URL [Строка 3849-3850]**

#### Текущий код:
```java
// Line 3849-3850
if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
    reloadUrl += "&vcode=" + AppVars.VCode;
}
```

#### Проблема:
- Использует `AppVars.VCode` напрямую без timeout-проверки
- Если VCode старая (>30с), отправится невалидная защита
- Нет fallback если VCode null

#### После миграции:
```java
// Line 3849-3850
String vcode = SessionManager.getInstance().getValidVCodeForAction("main_reload");
if (vcode != null && !vcode.isEmpty()) {
    reloadUrl += "&vcode=" + vcode;
    Log.d(TAG, "[BG_TRACE] mainReload with VCode");
    FileLogger.trace("[SERVER_TIMER_RELOAD] vcode_age=" + 
                     SessionManager.getInstance().getVCodeAgeMs() + "ms");
} else {
    // ⚠️ Fallback: потребуем перезагрузку основной страницы
    Log.w(TAG, "[BG_TRACE] mainReload VCode missing, deferring");
    FileLogger.trace("[SERVER_TIMER_RELOAD_FALLBACK] no_vcode");
    return;  // Skip reload, дождемся следующего цикла
}
```

#### Добавить в SessionManager:
```java
/**
 * Возвращает валидный VCode с таймаутом check
 * @param actionName Для логирования и статистики
 * @return VCode или null если истекла давность
 */
public String getValidVCodeForAction(String actionName) {
    readLock.lock();
    try {
        if (currentVCode == null || currentVCode.isEmpty()) {
            return null;
        }
        
        long ageMs = System.currentTimeMillis() - vCodeTimestampMs;
        long timeoutMs = isFightInProgress 
            ? FIGHT_FALLBACK_MODE_TIMEOUT_MS : NORMAL_TIMEOUT_MS;
        
        if (ageMs > timeoutMs) {
            Log.w(TAG, "[VCODE_EXPIRED] action=" + actionName + 
                       ", age=" + ageMs + "ms, timeout=" + timeoutMs + "ms");
            return null;
        }
        
        return currentVCode;
    } finally {
        readLock.unlock();
    }
}

public long getVCodeAgeMs() {
    readLock.lock();
    try {
        return System.currentTimeMillis() - vCodeTimestampMs;
    } finally {
        readLock.unlock();
    }
}
```

**Результат:** Каждая отправка проверяет timeout, предотвращая отправку старой защиты.

---

### 3️⃣ **Миграция #3: checkServerTimerDrivenActions() - Null Check [Строка 3943, 3948]**

#### Текущий код:
```java
// Line 3943, 3948
else if (AppVars.VCode == null || AppVars.VCode.isEmpty()) {
    reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&af_tick=1&r=" + now 
                + "&vcode=" + AppVars.VCode;  // ❌ ОПАСНО: может быть null!
}
```

#### Проблема:
- Первое условие проверяет NULL, но потом используется в URL
- Если null, отправится "&vcode=null"

#### После миграции:
```java
String vcode = SessionManager.getInstance().getValidVCodeForAction("auto_turn_timer");
if (vcode == null || vcode.isEmpty()) {
    // Fallback: перезагрузить main.php для обновления VCode
    loadUrl("main.php");
    Log.d(TAG, "[BG_TRACE] Auto-turn deferred (VCode missing)");
    FileLogger.trace("[SERVER_TIMER_NO_VCODE] reloading_main");
    return;
} else {
    reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&af_tick=1&r=" + now 
                + "&vcode=" + vcode;
}
```

**Результат:** Прямой fallback на перезагрузку, если VCode недоступна.

---

### 4️⃣ **Миграция #4: schedulePostResponseReload() [Строка 4604-4606]**

#### Текущий код:
```java
// Line 4604-4606
private void schedulePostResponseReload(boolean clearAutoTurnState) {
    if (ru.neverlands.abclient.utils.AppVars.VCode != null
        && !ru.neverlands.abclient.utils.AppVars.VCode.isEmpty()) {
        reloadUrl += "&vcode=" + ru.neverlands.abclient.utils.AppVars.VCode;
    }
}
```

#### Проблема:
- Полностью квалифицированный путь (ru.neverlands...) - признак legacy кода
- Проверка есть, но нет fallback

#### После миграции:
```java
private void schedulePostResponseReload(boolean clearAutoTurnState) {
    SessionManager sessionManager = SessionManager.getInstance();
    String vcode = sessionManager.getValidVCodeForAction("post_response_reload");
    
    if (vcode != null && !vcode.isEmpty()) {
        reloadUrl += "&vcode=" + vcode;
        Log.d(TAG, "[BG_TRACE] schedulePostResponseReload with VCode");
    } else {
        // Fallback: reload without VCode, server вернет новый VCode
        Log.w(TAG, "[BG_TRACE] schedulePostResponseReload VCode missing, fallback reload");
        FileLogger.trace("[POST_RESPONSE_RELOAD_FALLBACK] no_vcode");
    }
}
```

**Результат:** Graceful fallback при потере VCode в recovery-пути.

---

## 5️⃣ **Миграция #5: Удалить все прямые AppVars.VCode проверки**

### Глобальный поиск:
```bash
grep -n "AppVars\.VCode" app/src/main/java/ru/neverlands/abclient/MainActivity.java
```

**Ожидаемые результаты после миграции:**
- ✅ 0 совпадений `AppVars.VCode` (кроме комментариев о deprecation)

### Проверочный скрипт после миграции:
```bash
# Убедиться что миграция завершена
grep "AppVars\.VCode" app/src/main/java/ru/neverlands/abclient/MainActivity.java | wc -l
# Должно быть: 0
```

---

## 📋 Чек-лист миграции

### Фаза 1: Подготовка (30 мин)
- [ ] Убедиться что SessionManager скомпилирован и тестирован
- [ ] Убедиться что SessionManager.getInstance() всегда возвращает singleton
- [ ] Добавить методы putVCode(), getCurrentVCode(), getValidVCodeForAction(), getVCodeAgeMs() в SessionManager

### Фаза 2: Миграция (90 мин)
- [ ] Миграция #1: adoptVCodeFromAutoSubmitPayload → SessionManager.putVCode()
- [ ] Миграция #2: checkServerTimerDrivenActions reload → getValidVCodeForAction + fallback
- [ ] Миграция #3: checkServerTimerDrivenActions auto-turn → getValidVCodeForAction + main.php reload
- [ ] Миграция #4: schedulePostResponseReload → getValidVCodeForAction + fallback
- [ ] Полный поиск AppVars.VCode - убедиться 0 остатков

### Фаза 3: Тестирование (60+ мин)
- [ ] Запустить auto-turn, убедиться что VCode отправляется с каждым запросом
- [ ] Запустить кэп-диалог, убедиться что защита валидна
- [ ] Проверить логи: `[VCODE_ADOPT_SUBMIT]`, `[SERVER_TIMER_RELOAD]`, `[POST_RESPONSE_RELOAD_FALLBACK]`
- [ ] Убедиться что нет `AppVars.VCode` в logcat-выводе

---

## 🔍 Отладочные логи для мониторинга

Добавить в SessionManager для отладки:

```java
public void debugPrintVCodeState() {
    readLock.lock();
    try {
        long ageMs = System.currentTimeMillis() - vCodeTimestampMs;
        Log.d("SessionManager", "[VCODE_DEBUG] " +
              "code=" + (currentVCode != null ? currentVCode.substring(0, 4) + "..." : "null") + 
              ", age=" + ageMs + "ms, " +
              "timeout=" + (isFightInProgress ? FIGHT_TIMEOUT : NORMAL_TIMEOUT) + "ms, " +
              "source=" + vCodeSourceTag + ", " +
              "inFight=" + isFightInProgress);
    } finally {
        readLock.unlock();
    }
}
```

Вызвать в MainActivity.requestAutoTurnInternal():
```java
SessionManager.getInstance().debugPrintVCodeState();
```

---

## ⚠️ Возможные проблемы и решения

| Проблема | Симптом | Решение |
|----------|---------|---------|
| VCode expired timeout | "Неверный код защиты" при отправке | Уменьшить timeout в SessionManager или перезагружать main.php чаще |
| SessionManager.getInstance() == null | NullPointerException | Убедиться что SessionManager инициализируется в Application.onCreate() |
| Race condition при смене PHPSESSID | Отправляется старый VCode после logout | Вызвать SessionManager.clearFightContext() в logout |
| Логи не показывают VCode | Нельзя отследить что происходит | Добавить debugPrintVCodeState() вызовы |

---

## 📊 Метрики успеха

После миграции ожидаются:

1. ✅ 0 совпадений `AppVars.VCode` в검토
2. ✅ 100% успешность AJAX-запросов с валидным VCode
3. ✅ Нет "Неверный код защиты" ошибок в логах
4. ✅ Время на VCode-проверку <1ms (fast path)
5. ✅ Fallback на main.php reload работает корректно

---

**Дата плана:** 2026-04-03  
**Статус:** Ready for Implementation
