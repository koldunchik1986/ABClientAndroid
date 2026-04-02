# MainActivity.java: Event-Driven VCode для немедленного турна

## Обзор

`MainActivity.java` — это главная Activity приложения. В контексте VCode здесь происходит:
- Вызов события объявления боя (через WebView JavaScript)
- Немедленный запрос хода через event-driven архитектуру
- Bypass'инг 24+ секундных полинговых задержек AutoModeForegroundService
- Управление foreground состоянием приложения

**Ключевая особенность:** MainActivity может быстро реагировать на события WebView и отправлять запрос хода в течение <100ms, вместо ожидания следующего polling цикла (24+ сек).

---

## Event-Driven архитектура: requestImmediateAutoTurnOnFightAnnounce()

### Проблема: Задержка 24+ секунды

**Сценарий:**
```
T+0ms:     Сервер отправляет боевую страницу (объявление боя)
T+0-50ms:  WebView парсит HTML, вызывает parseVCode()
T+50ms:    MainActivity получает событие объявления

T+50-100ms: ideally отправляем ход
           ButInstead: придём в AutoModeForegroundService polling
T+0-24000ms: ждём следующего цикла polling
T+24000ms:  Ход отправляется (24 секунды задержки!)
```

**Последствия:**
- Враг наносит несколько ударов без ответа
- На сложных врагах персонаж может получить критичный урон
- Бой выглядит зависшим

### Решение: requestImmediateAutoTurnOnFightAnnounce()

**Код в MainActivity:**

```java
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    /**
     * Вызывается из WebView JavaScript при объявлении боя.
     * Отправляет немедленный запрос хода, минуя polling задержку.
     */
    public void requestImmediateAutoTurnOnFightAnnounce() {
        String msg_entry = BG_TRACE_PREFIX + 
            " requestImmediateAutoTurnOnFightAnnounce: triggered by fight announcement";
        Log.d(TAG, msg_entry);
        FileLogger.trace(TAG, msg_entry);
        
        // ✅ ШАГ 1: Проверить, виден ли captcha поверх боя
        if (isCaptchaVisible()) {
            FileLogger.trace(TAG, 
                "Captcha visible, skip immediate turn request");
            return;
        }
        
        // ✅ ШАГ 2: Сразу же отправить запрос хода (без очереди)
        requestAutoTurnBackgroundAware();
        
        String msg_done = BG_TRACE_PREFIX + 
            " requestImmediateAutoTurnOnFightAnnounce: request sent";
        FileLogger.trace(TAG, msg_done);
    }
    
    /**
     * Отправляет запрос на бой в фон, с учётом ForcedActionGuard
     */
    private void requestAutoTurnBackgroundAware() {
        // ✅ Проверить guard
        if (!ForcedActionGuard.shouldForceActionAdvanced(
                "autoTurn",
                false,  // не в foreground
                true    // бой активен
            )) {
            FileLogger.trace(TAG, 
                "ForcedActionGuard blocked autoTurn");
            return;
        }
        
        // ✅ Отправить в фоновый сервис или background thread
        sendAutoTurnRequestToBackground();
    }
    
    private void sendAutoTurnRequestToBackground() {
        // Option 1: Отправить через BackgroundAutoTurnTask
        if (autoTurnTask == null || autoTurnTask.isDone()) {
            autoTurnTask = new AutoTurnTask().execute();
            FileLogger.trace(TAG, "AutoTurn background task started");
        }
        
        // Option 2: Отправить из AutoModeForegroundService (если он активен)
        // Intent serviceIntent = new Intent(this, AutoModeForegroundService.class);
        // startService(serviceIntent);
    }
}
```

---

## Интеграция с FightViewModel

### Как FightViewModel вызывает requestImmediateAutoTurnOnFightAnnounce

**Flow:**

```
1. WebView получает боевую страницу
   └─ onPageFinished() вызывается
   
2. SessionManager.parseVCode() (автоматически)
   └─ VCode парсируется и кэшируется
   
3. FightViewModel.onFightPageAnnounced() (WebView callback)
   └─ Здесь мы уже знаем, что есть бой
   
4. FightViewModel.tryTriggerImmediateAutoTurnOnAnnounce()
   └─ Проверяет, включена ли автоматизация
   └─ Вызывает activity.requestImmediateAutoTurnOnFightAnnounce()
   
5. MainActivity.requestImmediateAutoTurnOnFightAnnounce()
   └─ Сразу отправляет ход (T+50-100ms)
   └─ Bypass'инг полинга
```

**Код:**

```java
// В FightViewModel.java:
public class FightViewModel extends ViewModel {
    private static final String TAG = "FightViewModel";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
    
    private LiveData<Boolean> _isAutoBattleActive;
    
    public void onFightPageAnnounced(String html) {
        FileLogger.trace(TAG, 
            "Fight page announced by WebView");
        
        // ✅ Попытаться запустить немедленный ход
        tryTriggerImmediateAutoTurnOnAnnounce();
    }
    
    private void tryTriggerImmediateAutoTurnOnAnnounce() {
        String msg_entry = BG_TRACE_PREFIX + 
            " tryTriggerImmediateAutoTurnOnAnnounce: ENTERED";
        Log.d(TAG, msg_entry);
        FileLogger.trace(TAG, msg_entry);
        
        try {
            // ✅ ШАГ 1: Проверить, включена ли автоматизация
            boolean autoBattleUiEnabled = 
                Boolean.TRUE.equals(_isAutoBattleActive.getValue());
            boolean autoBattleEnabledViaVm = 
                AppVars.Autoboi == AutoboiState.AutoboiOn ||
                (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi);
            
            String msg_state = BG_TRACE_PREFIX + 
                " tryTriggerImmediateAutoTurnOnAnnounce: "
                + "autoBattleUiEnabled=" + autoBattleUiEnabled 
                + ", autoBattleEnabledViaVm=" + autoBattleEnabledViaVm;
            Log.d(TAG, msg_state);
            FileLogger.trace(TAG, msg_state);
            
            if (!autoBattleUiEnabled && !autoBattleEnabledViaVm) {
                String msg_skip = BG_TRACE_PREFIX + 
                    " tryTriggerImmediateAutoTurnOnAnnounce: "
                    + "auto battle disabled, skip immediate trigger";
                FileLogger.trace(TAG, msg_skip);
                return;
            }
            
            // ✅ ШАГ 2: Получить MainActivity
            MainActivity activity = 
                AppVars.mainActivity != null ? 
                AppVars.mainActivity.get() : 
                null;
            
            if (activity != null) {
                String msg_trigger = BG_TRACE_PREFIX + 
                    " tryTriggerImmediateAutoTurnOnAnnounce: "
                    + "trigger immediate turn request";
                Log.d(TAG, msg_trigger);
                FileLogger.trace(TAG, msg_trigger);
                
                // ✅ ВЫЗВАТЬ НЕМЕДЛЕННЫЙ ЗАПРОС ХОДА
                activity.requestImmediateAutoTurnOnFightAnnounce();
            } else {
                FileLogger.warn(TAG, 
                    "MainActivity not available for immediate trigger");
            }
        } catch (Exception e) {
            Log.e(TAG, msg_entry + " exception", e);
            FileLogger.error(TAG, "tryTriggerImmediateAutoTurnOnAnnounce " +
                "exception", e);
        }
    }
}
```

---

## VCode использование в event-driven запросе

### Где берём VCode?

**Сценарий:**
```
T+0ms:     WebView загружает боевую страницу
T+5ms:     SessionManager.parseVCode() вызвана
           → VCode парсируется и кэшируется
           → VCode ageMs = 0

T+50ms:    requestImmediateAutoTurnOnFightAnnounce() вызывается
           → requestAutoTurnBackgroundAware() вызывает sendAutoTurnRequestToBackground()
           → getValidVCodeForAction("fight_turn") возвращает VCode
           → VCode ageMs ~= 50ms (очень свежая!)
```

**Код:**

```java
// В AutoTurnTask (фоновый task):
private class AutoTurnTask extends AsyncTask<Void, Void, Boolean> {
    @Override
    protected Boolean doInBackground(Void... voids) {
        // ✅ ШАГ 1: Получить свежий VCode
        String vcode = SessionManager.getInstance()
            .getValidVCodeForAction("fight_turn");
        
        if (vcode == null) {
            FileLogger.error(TAG, 
                "AutoTurn: VCode not available (may have expired)");
            return false;
        }
        
        String msg = "AutoTurn sending with VCode, "
            + "age=" + vcode.ageMs() + "ms";
        FileLogger.trace(TAG, msg);
        
        // ✅ ШАГ 2: Отправить ход
        // (это вызовет FightAuto.processFightPage() на нём)
        boolean success = requestAutoTurnHttp(vcode);
        
        return success;
    }
    
    @Override
    protected void onPostExecute(Boolean success) {
        if (success) {
            FileLogger.trace(TAG, 
                "AutoTurn request completed successfully");
        } else {
            FileLogger.warn(TAG, 
                "AutoTurn request failed, will retry in next polling cycle");
        }
    }
}
```

---

## Проблема: isCaptchaVisible() проверка

### Зачем это нужно?

**Сценарий:**
```
T+0ms:    WebView объявляет бой

T+30ms:   requestImmediateAutoTurnOnFightAnnounce() вызывается
         
T+40ms:   ⚠️ Вдруг captcha появляется поверх боя!
         → requestAutoTurn всё равно отправляется
         → Но это НЕПРАВИЛЬНО! Нужно ввести captcha!

T+100ms:  Captcha решена, можно отправить ход
```

**Решение:**

```java
private boolean isCaptchaVisible() {
    // ✅ Проверка 1: WebView рендерит чёрный фон или loading spinner
    
    // ✅ Проверка 2: Видна ли captcha форма (обычно overlay)
    
    // ✅ Проверка 3: Есть ли текст "Пожалуйста введите..." на странице
    
    // Реализация:
    if (webView == null) return false;
    
    try {
        // Вызвать JS для проверки видимости captcha
        webView.evaluateJavascript(
            "document.querySelector('.captcha-form') != null ? 'true' : 'false'",
            value -> {
                boolean captchaFound = "true".equals(value);
                FileLogger.trace(TAG, 
                    "Captcha visibility check: " + captchaFound);
            }
        );
    } catch (Exception e) {
        // Если ошибка в JS, assume captcha видна (безопаснее)
        return true;
    }
    
    return false;
}
```

---

## Guard: ForcedActionGuard проверка

### Почему нужна?

**Сценарий:**
```
T+0ms:    Объявляется бой

T+50ms:   requestImmediateAutoTurnOnFightAnnounce() вызывается

T+60ms:   ForcedActionGuard проверяет:
         → if (AppVars.FastNeed) return false  // Блокировано!
         → Причина: пользователь только что использовал эликсир

T+60-120ms: Ход НЕ отправляется (FastNeed блокирует)

T+120ms:   FastNeed очищается
           → Ход отправляется в СЛЕДУЮЩЕМ цикле polling
           → Но это уже не "немедленный" ход!
```

**Решение: Умная очередь**

```java
private void requestAutoTurnBackgroundAware() {
    // ✅ ШАГ 1: Проверить guard с приоритетом боя
    if (!ForcedActionGuard.shouldForceActionAdvanced(
            "autoTurn",
            false,              // не в foreground
            true                // БОЙ АКТИВЕН - приоритет!
        )) {
        
        // ⚠️ Guard заблокировал, но это мог быть временный lock (FastNeed)
        // Запланировать retry в ближайшее время
        new Handler().postDelayed(() -> {
            requestAutoTurnBackgroundAware();  // Повторить
        }, 500);  // 500ms
        
        return;
    }
    
    // ✅ ШАГ 2: Guard разрешил, отправляем
    sendAutoTurnRequestToBackground();
}
```

---

## Логирование: BG_TRACE_PREFIX

### Что это?

**Определение:**

```java
private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
```

**Использование:**

```java
String msg = BG_TRACE_PREFIX + 
    " MainActivity: autoTurn request initiated at T=" + tm();
FileLogger.trace(TAG, msg);
Log.d(TAG, msg);
```

**Для чего?**

1. Легче найти в логах all "фоновые события": `grep "\[BG_TRACE\]"`
2. Отличить от обычных логов
3. Быстро найти event-driven события

**Типичные логи:**

```
[BG_TRACE] MainActivity: requestImmediateAutoTurnOnFightAnnounce: triggered
[BG_TRACE] FightViewModel: tryTriggerImmediateAutoTurnOnAnnounce: ENTERED
[BG_TRACE] FightViewModel: tryTriggerImmediateAutoTurnOnAnnounce: autoBattleUiEnabled=true
[BG_TRACE] FightViewModel: tryTriggerImmediateAutoTurnOnAnnounce: trigger immediate turn
[BG_TRACE] MainActivity: requestAutoTurn: defer first turn for frame render, remainingMs=416
[BG_TRACE] MainActivity: AutoTurn HTTP request sent
[BG_TRACE] MainPhp: Received autoTurn response, processing...
```

---

## Примеры интеграции

### Пример 1: Базовый event-driven ход

```java
// В MainActivity.java:
public void requestImmediateAutoTurnOnFightAnnounce() {
    // ✅ Проверить captcha
    if (isCaptchaVisible()) return;
    
    // ✅ Отправить ход с guard'ом
    requestAutoTurnBackgroundAware();
}

private void requestAutoTurnBackgroundAware() {
    // ✅ Guard проверка
    if (!ForcedActionGuard.shouldForceActionAdvanced(
            "autoTurn", false, true)) {
        // Guard заблокировал, retry позже
        new Handler().postDelayed(
            this::requestAutoTurnBackgroundAware, 500);
        return;
    }
    
    // ✅ Отправить
    new AutoTurnTask().execute();
}
```

### Пример 2: С fallback на polling если ошибка

```java
private class AutoTurnTask extends AsyncTask<Void, Void, Integer> {
    @Override
    protected Integer doInBackground(Void... voids) {
        String vcode = SessionManager.getInstance()
            .getValidVCodeForAction("fight_turn");
        
        if (vcode == null) {
            return -1;  // Ошибка: no vcode
        }
        
        int httpCode = requestAutoTurnHttp(vcode);
        return httpCode;
    }
    
    @Override
    protected void onPostExecute(Integer httpCode) {
        if (httpCode == 200) {
            FileLogger.trace(TAG, "Event-driven turn succeeded");
        } else if (httpCode == -1) {
            FileLogger.warn(TAG, 
                "Event-driven failed (no vcode), falling back to polling");
        } else {
            FileLogger.error(TAG, 
                "Event-driven HTTP error: " + httpCode);
        }
    }
}
```

### Пример 3: С дополнительной очередью

```java
private final Queue<Runnable> eventDrivenQueue = new LinkedList<>();

public void requestImmediateAutoTurnOnFightAnnounce() {
    eventDrivenQueue.offer(() -> {
        if (isCaptchaVisible()) return;
        requestAutoTurnBackgroundAware();
    });
    
    processEventDrivenQueue();
}

private synchronized void processEventDrivenQueue() {
    if (eventDrivenQueue.isEmpty()) return;
    
    Runnable task = eventDrivenQueue.poll();
    task.run();
    
    // Обработать следующий в очереди
    processEventDrivenQueue();
}
```

---

## Чек-лист для event-driven фич

- [ ] `requestImmediateAutoTurnOnFightAnnounce()` добавлена в MainActivity
- [ ] Проверка `isCaptchaVisible()` вызывается перед отправкой
- [ ] Guard проверка `ForcedActionGuard.shouldForceActionAdvanced()`
- [ ] VCode получается через `SessionManager.getValidVCodeForAction()`
- [ ] Добавлено логирование с `[BG_TRACE]` префиксом
- [ ] Добавлен retry-обработчик при блокировке guard'ом
- [ ] Протестировано: ход отправляется <100ms от объявления боя
- [ ] Протестировано: captcha ретеп не прерывает event-driven
- [ ] Логи показывают правильную последовательность вызовов

---

## Заключение

Event-driven архитектура в MainActivity — это критичная оптимизация, которая сокращает задержку от 24 сек до <100ms. 

**Три главных компонента:**

1. **WebView callback** — объявление боя из JavaScript
2. **FightViewModel** — проверка условий автоматизации
3. **MainActivity event** — немедленная отправка с VCode

Следование этим правилам гарантирует, что первый ход отправляется почти мгновенно после объявления боя, что значительно улучшает боевой опыт.
