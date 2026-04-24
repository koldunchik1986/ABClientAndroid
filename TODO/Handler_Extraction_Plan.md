# Извлечение Handler'ов из MainActivity.java

## Цель

Разбить монолитные методы на специализированные Handler-классы, следуя **Rule 6** из AGENTS.MD:
- Один Handler = одна область ответственности
- Цепочки проверок (>3 условий) → Handler с Callback pattern
- Логирование: `Log.i()` + `FileLogger.trace()`

---

## 📋 Таблица Handler'ов для переноса

| # | Handler | Текущая локация | Строк | Сложность | Приоритет |
|---|---------|-----------------|-------|-----------|-----------|
| 1 | FightContextChoiceHandler | requestAutoTurnInternal | ~150 | HARD | 🔴 HIGH |
| 2 | ChatPollRecoveryHandler | onChatPollResponseMeta | ~120 | MEDIUM | 🟠 MEDIUM |
| 3 | ManualNavGuardHandler | isManualMainNavigationUrl | ~125 | MEDIUM | 🟡 LOW |
| 4 | SubmitRetryHandler | submitAutoBattleActionToWebView | ~90 | MEDIUM | 🟡 LOW |
| 5 | CaptchaRefreshHandler | showCaptchaDialog refresh logic | ~80 | MEDIUM | 🟡 LOW |

---

## 🔴 Handler #1: FightContextChoiceHandler (CRITICAL)

### Текущая проблема:
**requestAutoTurnInternal()** [Линии 702-857] выглядит так:

```plaintext
requestAutoTurnInternal(boolean allowServerProbeFallback)
└─ evaluateJavascript() callback
   ├─ Check HTML null/size < 1000
   ├─ if hasFightMarkers(current HTML)
   │  ├─ if isActiveFightContext → use current
   │  └─ else → fallback
   ├─ if !hasFightMarkers(current)
   │  ├─ if hasFightMarkers(cached) → use cached
   │  └─ else → server-probe
   └─ fightViewModel.autoTurnOnce(result)
```

### Новая структура:

```java
// Файл: handlers/FightContextChoiceHandler.java

public class FightContextChoiceHandler {
    
    public interface FightContextCallback {
        void onFightContextSelected(String fightHtml);
        void onFightContextUnavailable(String reason);
    }
    
    /**
     * Выбирает оптимальный бой-контекст с fallback-цепочкой:
     * текущий HTML → кэшированный → server-probe
     */
    public static void chooseFightContext(
            String currentHtml,
            String cachedHtml,
            MainActivity.FightContextOracle oracle,
            boolean allowServerProbeFallback,
            FightContextCallback callback) {
        
        // 1️⃣ Первый выбор: текущий HTML
        if (isValidFightContext(currentHtml, oracle)) {
            Log.i(TAG, "[FIGHT_CONTEXT_CHOICE] using current HTML");
            FileLogger.trace("[CHOICE] current=" + hashHtml(currentHtml));
            callback.onFightContextSelected(currentHtml);
            return;
        }
        
        // 2️⃣ Второй выбор: кэшированный HTML
        if (isValidFightContext(cachedHtml, oracle)) {
            Log.i(TAG, "[FIGHT_CONTEXT_CHOICE] fallback to cached HTML");
            FileLogger.trace("[CHOICE] cached=" + hashHtml(cachedHtml));
            callback.onFightContextSelected(cachedHtml);
            return;
        }
        
        // 3️⃣ Третий выбор: server-probe (если разрешено)
        if (allowServerProbeFallback) {
            Log.i(TAG, "[FIGHT_CONTEXT_CHOICE] initiating server-probe");
            FileLogger.trace("[CHOICE] server_probe");
            // Callback будет вызван из requestAutoTurnFromServerProbe()
            return;
        }
        
        // ❌ Нет доступного контекста
        Log.w(TAG, "[FIGHT_CONTEXT_CHOICE] no valid context available");
        FileLogger.trace("[CHOICE] none_available");
        callback.onFightContextUnavailable("No valid fight context");
    }
    
    private static boolean isValidFightContext(String html, MainActivity.FightContextOracle oracle) {
        if (html == null || html.length() < 500) {
            return false;
        }
        
        boolean hasMarkers = oracle.hasFightMarkers(html);
        boolean isActive = oracle.isActiveFightContext(html);
        
        return hasMarkers && isActive;
    }
    
    private static String hashHtml(String html) {
        if (html == null) return "null";
        return Integer.toHexString(html.hashCode());
    }
}
```

### Использование в MainActivity:

```java
// Line 702-857: requestAutoTurnInternal
private void requestAutoTurnInternal(boolean allowServerProbeFallback) {
    if (binding == null) return;
    
    final WebView mainWebView = binding.appBarMain.contentMain;
    if (mainWebView == null) return;
    
    // Guard'ы
    if (AppVars.IsFightCaptchaDialogVisible) return;
    if (shouldDeferAutoTurnForFirstFrameRender()) return;
    
    mainWebView.evaluateJavascript(
        "document.documentElement.outerHTML",
        value -> {
            final String currentHtml = HtmlUtils.parseJsResultString(value);
            
            // ✅ Используем Handler для выбора контекста
            FightContextChoiceHandler.chooseFightContext(
                currentHtml,
                AppVars.ContentMainPhp,  // cached
                new MainActivity.FightContextOracle() {
                    @Override
                    public boolean hasFightMarkers(String html) {
                        return MainActivity.this.hasFightMarkers(html);
                    }
                    
                    @Override
                    public boolean isActiveFightContext(String html) {
                        return MainActivity.this.isActiveFightContext(html);
                    }
                },
                allowServerProbeFallback,
                new FightContextChoiceHandler.FightContextCallback() {
                    @Override
                    public void onFightContextSelected(String fightHtml) {
                        Log.i(TAG, "[AUTO_TURN] executing with selected context");
                        FightViewModel viewModel = getFightViewModel();
                        if (viewModel != null) {
                            viewModel.autoTurnOnce(fightHtml);
                        }
                    }
                    
                    @Override
                    public void onFightContextUnavailable(String reason) {
                        Log.w(TAG, "[AUTO_TURN] context unavailable: " + reason);
                        if (allowServerProbeFallback) {
                            requestAutoTurnFromServerProbe();
                        }
                    }
                }
            );
        }
    );
}
```

### Добавить interface для Oracle pattern:

```java
// Interface в MainActivity.java
public interface FightContextOracle {
    boolean hasFightMarkers(String html);
    boolean isActiveFightContext(String html);
}
```

**Результат:** 
- ✅ requestAutoTurnInternal сократилась с ~150 до ~30 строк
- ✅ Вся логика выбора контекста в FightContextChoiceHandler
- ✅ Легче тестировать и менять fallback-логику

---

## 🟠 Handler #2: ChatPollRecoveryHandler

### Текущая проблема:
**onChatPollResponseMeta()** [Линии 578-701] имеет сложную retry-логику с состоянием.

### Новая структура:

```java
// Файл: handlers/ChatPollRecoveryHandler.java

public class ChatPollRecoveryHandler {
    
    public interface ChatPollRecoveryCallback {
        void onRecoveryScheduled(long retryAfterMs);
        void onRecoveryCancel();
    }
    
    /**
     * Обрабатывает ошибку в чат-полле с exponential backoff
     */
    public static void handleChatPollError(
            int httpCode,
            int responseBytes,
            int consecutiveFailures,
            long lastFailureTime,
            boolean isAutoBossActive,
            MainActivity mainActivity,
            ChatPollRecoveryCallback callback) {
        
        boolean pollFailed = (httpCode >= 535) || (responseBytes <= 0);
        
        if (!pollFailed) {
            Log.i(TAG, "[CHAT_POLL_RECOVERY] poll success, clearing state");
            FileLogger.trace("[POLL_RECOVERY] success");
            callback.onRecoveryCancel();
            return;
        }
        
        // poll failed - implement backo ff
        long baseDelayMs = isAutoBossActive ? 350L : 350L;
        long delayMs = Math.min(baseDelayMs * (long)Math.pow(2, consecutiveFailures - 1), 4000L);
        
        if (isTooSoon(lastFailureTime, delayMs)) {
            Log.d(TAG, "[CHAT_POLL_RECOVERY] dedup: too soon");
            return;
        }
        
        Log.w(TAG, "[CHAT_POLL_RECOVERY] scheduling retry after " + delayMs + "ms, " +
              "consecutive=" + consecutiveFailures);
        FileLogger.trace("[POLL_RECOVERY] retry_scheduled, delay=" + delayMs + 
                        "ms, count=" + consecutiveFailures);
        
        callback.onRecoveryScheduled(delayMs);
    }
    
    private static boolean isTooSoon(long lastFailureTime, long minGapMs) {
        return (System.currentTimeMillis() - lastFailureTime) < minGapMs;
    }
}
```

### Использование:

```java
// Line 578-701: onChatPollResponseMeta (упрощенная версия)
private void onChatPollResponseMeta(int httpCode, int bytes, boolean isClan, boolean isChat, String responseTag) {
    if (binding == null) return;
    
    ChatPollRecoveryHandler.handleChatPollError(
        httpCode, bytes,
        consecutiveChatPollFailures,
        lastChatPollFailureAtMs,
        AutoFunctionsManager.getInstance().isAutoBossEnabled(),
        this,
        new ChatPollRecoveryHandler.ChatPollRecoveryCallback() {
            @Override
            public void onRecoveryScheduled(long retryAfterMs) {
                consecutiveChatPollFailures++;
                lastChatPollFailureAtMs = System.currentTimeMillis();
                
                // Cancel previous recovery
                if (chatPollRecoveryRunnable != null) {
                    chatRefreshHandler.removeCallbacks(chatPollRecoveryRunnable);
                }
                
                // Schedule new recovery
                chatPollRecoveryRunnable = () -> {
                    Log.d(TAG, "[CHAT_POLL] recovery attempt");
                    requestChatRefresh();
                };
                
                chatRefreshHandler.postDelayed(chatPollRecoveryRunnable, retryAfterMs);
            }
            
            @Override
            public void onRecoveryCancel() {
                consecutiveChatPollFailures = 0;
                lastChatPollFailureAtMs = 0;
                
                if (chatPollRecoveryRunnable != null) {
                    chatRefreshHandler.removeCallbacks(chatPollRecoveryRunnable);
                    chatPollRecoveryRunnable = null;
                }
            }
        }
    );
}
```

**Результат:**
- ✅ onChatPollResponseMeta становится ~20 строк
- ✅ Вся retry-логика в ChatPollRecoveryHandler
- ✅ Легче тестировать backoff-алгоритм

---

## 🟡 Handler #3: ManualNavGuardHandler

### Текущая проблема:
**isManualMainNavigationUrl()** + **suppressAutoTurnServerProbeForManualNavigation()** [Линии 927-1051] 
занимают ~125 строк.

### Новая структура:

```java
// Файл: handlers/ManualNavGuardHandler.java

public class ManualNavGuardHandler {
    
    public interface ManualNavGuardCallback {
        void suppressAutoTurn(long suppressForMs);
        void allowAutoTurn();
    }
    
    /**
     * Проверяет если это manual навигация и устанавливает guard
     */
    public static void evaluateNavigationAndSuppress(
            String url,
            long currentSuppressUntilMs,
            ManualNavGuardCallback callback) {
        
        if (!isManualNavigation(url)) {
            Log.d(TAG, "[MANUAL_NAV_GUARD] auto-turn allowed");
            callback.allowAutoTurn();
            return;
        }
        
        // Manual navigation detected
        long suppressForMs = calculateSuppressWindow(url);
        long newSuppressUntilMs = Math.max(currentSuppressUntilMs, 
                                           System.currentTimeMillis() + suppressForMs);
        
        Log.i(TAG, "[MANUAL_NAV_GUARD] suppressing auto-turn for " + suppressForMs + "ms");
        FileLogger.trace("[NAV_GUARD] suppress_window=" + suppressForMs + "ms");
        
        callback.suppressAutoTurn(newSuppressUntilMs);
    }
    
    private static boolean isManualNavigation(String url) {
        if (url == null) return false;
        
        // Compass, combat, location, etc URLs
        return url.contains("get_id=60") ||      // Взять из казны
               url.contains("get_id=56") ||      // Автолог
               url.contains("act=10") ||         // Атаковать
               url.contains("gid=") ||           // ID врага
               url.contains("rout=") ||          // Маршрут
               url.contains("cell_") ||          // Клетка компаса
               url.contains("map.php");          // Карта
    }
    
    private static long calculateSuppressWindow(String url) {
        if (url.contains("map.php")) {
            return 5000L;  // 5 сек для навигации карты
        }
        return 2000L;  // 2 сек по умолчанию
    }
}
```

**Результат:**
- ✅ Упрощает логику навигации
- ✅ Легче добавлять новые типы навигации

---

## 📊 План внедрения

### Фаза 1: FightContextChoiceHandler (HIGH PRIORITY)
1. ✅ Создать файл `handlers/FightContextChoiceHandler.java`
2. ✅ Добавить interface `FightContextOracle` в MainActivity
3. ✅ Рефакторинг `requestAutoTurnInternal()` для использования Handler
4. ✅ Тестирование: auto-turn должен работать как раньше

**Время:** ~4-5 часов

### Фаза 2: ChatPollRecoveryHandler (MEDIUM PRIORITY)
1. ✅ Создать файл `handlers/ChatPollRecoveryHandler.java`
2. ✅ Рефакторинг `onChatPollResponseMeta()`
3. ✅ Тестирование: чат-полл должен восстанавливаться

**Время:** ~2-3 часа

### Фаза 3: Оставшиеся Handler'ы (LOW PRIORITY)
1. ✅ ManualNavGuardHandler
2. ✅ SubmitRetryHandler
3. ✅ CaptchaRefreshHandler

**Время:** ~3-4 часа

---

## ✅ Чек-лист

### До начала:
- [ ] VCode Migration завершена (зависимость)
- [ ] Создана папка `handlers/`
- [ ] Все импорты подготовлены

### После реализации каждого Handler'а:
- [ ] Код компилируется
- [ ] Использование в MainActivity скомпилировано
- [ ] Логирование добавлено (Log.i + FileLogger.trace)
- [ ] Тестирование на реальном устройстве

---

## Актуализация 2026-04-24

- [x] VCode Migration завершена фактически: прямых runtime-обращений к `AppVars.VCode` в Java-коде не найдено.
- [x] `FightContextChoiceHandler` создан и подключён к `requestAutoTurnInternal(...)`.
- [ ] `ChatPollRecoveryHandler` отсутствует.
- [ ] `ManualNavGuardHandler` отсутствует.
- [ ] `SubmitRetryHandler` отсутствует; часть submit-guard уже реализована в `enqueueAutoBattleSubmit(...)`.
- [ ] При внедрении использовать `AppLog`, не прямой `android.util.Log`.

**Дата плана:** 2026-04-03  
**Статус:** In Progress (после фактической VCode Migration)
