# FastActionManager.java: VCode в быстрых действиях

## Обзор

`FastActionManager.java` — это контроллер быстрых внеочередных действий (fast-actions). Сюда относятся:
- Использование эликсиров (здоровья, маны, выносливости)
- Быстрые заклинания (прорыв, оглушение и т.д.)
- Срочные предметы
- Атаки во время других операций

**Ключевая особенность:** Fast-actions имеют **жёсткий срок** выполнения, но нуждаются в свежем VCode. Это создаёт конфликт: нужна скорость, но нужна и валидность VCode.

---

## Жизненный цикл fast-action с VCode

### Фаза 1: Решение выполнить fast-action

**Триггер:**
```java
// В MainPhp.java или другом модуле:
if (personaHealth < CRITICAL_THRESHOLD) {
    FastActionManager.fastAttackBlazElixir(vcode);
}
```

**Что происходит:**
```java
public static void fastAttackBlazElixir(String vcode) {
    // ✅ ШАГ 1: Проверить VCode
    if (vcode == null) {
        FileLogger.error(TAG, "Cannot perform fast action without VCode");
        return;
    }
    
    // ✅ ШАГ 2: Установить flag блокировки других функций
    AppVars.FastNeed = true;
    FileLogger.trace(TAG, "FastNeed set for elixir action");
    
    // ✅ ШАГ 3: Отправить действие в очередь (асинхронно)
    executeHttpRequestWithVCode(vcode, "action=use&item=elixir");
}
```

### Фаза 2: Выполнение HTTP запроса

**Что происходит:**
```java
private static void executeHttpRequestWithVCode(
        String vcode, 
        String actionData) {
    
    // ✅ Построить URL с VCode
    String url = "http://neverlands.ru/pinfo.php?"
        + actionData + "&vcode=" + vcode;
    
    // ✅ Отправить запрос (асинхронно, на фоновом потоке)
    new AsyncTask<String, Void, HttpResponse>() {
        @Override
        protected HttpResponse doInBackground(String... urls) {
            try {
                HttpURLConnection conn = (HttpURLConnection) 
                    new URL(urls[0]).openConnection();
                
                // ✅ Обязательно установить User-Agent
                conn.setRequestProperty("User-Agent", 
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...");
                
                int statusCode = conn.getResponseCode();
                String responseBody = readResponse(conn);
                
                FileLogger.trace(TAG, 
                    "Fast action HTTP response: code=" + statusCode);
                
                return new HttpResponse(statusCode, responseBody);
            } catch (Exception e) {
                FileLogger.error(TAG, "Fast action request failed", e);
                return null;
            }
        }
        
        @Override
        protected void onPostExecute(HttpResponse response) {
            // ✅ Обработать результат (переход к Фазе 3)
            handleFastActionResponse(response);
        }
    }.execute(url);
}
```

### Фаза 3: Обработка результата

**Успешный результат (200 OK):**
```java
private static void handleFastActionResponse(HttpResponse response) {
    if (response.statusCode == 200) {
        // ✅ Действие успешно выполнено на сервере
        FileLogger.trace(TAG, "Fast action succeeded");
        
        // ⚠️ ВАЖНО: НЕ ОЧИЩАЕМ FastNeed ЕЩЁ!
        // Потому что есть cooldown...
        
        // ✅ Установить таймер на cooldown
        scheduleCooldownTimer(response.actionType);
        
        return;
    }
    
    // ❌ Ошибка
    if (response.statusCode == 403) {
        FileLogger.error(TAG, 
            "403 VCode invalid during fast action");
        
        // Очистить флаг, потому что действие провалено
        AppVars.FastNeed = false;
        
        return;
    }
    
    if (response.statusCode == 500) {
        FileLogger.error(TAG, 
            "500 Server error during fast action");
        
        AppVars.FastNeed = false;
        return;
    }
    
    // Другие ошибки
    FileLogger.error(TAG, 
        "Unknown error: " + response.statusCode);
    AppVars.FastNeed = false;
}
```

### Фаза 4: Управление cooldown'ом

**Проблема:** После использования эликсира есть серверный cooldown (обычно 60+ секунд). За это время:
- MainPhp может вызвать другие fast-action'ы
- AutoFish может попытаться запуститься (но будет заблокирована FastNeed)
- Система должна знать, когда cooldown закончится

**Решение:**

```java
private static final Map<String, Long> cooldownTimers = new HashMap<>();

private static void scheduleCooldownTimer(String actionType) {
    // Определить длину cooldown'а
    int cooldownMs = getCooldownForAction(actionType);
    
    // Запомнить время начала
    long startTime = System.currentTimeMillis();
    cooldownTimers.put(actionType, startTime);
    
    FileLogger.trace(TAG, 
        "Cooldown scheduled: action=" + actionType 
        + ", duration=" + cooldownMs + "ms");
    
    // Установить таймер
    new Handler().postDelayed(() -> {
        // ✅ Cooldown истёк
        onCooldownExpired(actionType);
    }, cooldownMs);
}

private static void onCooldownExpired(String actionType) {
    // ✅ КРИТИЧНО: Очистить FastNeed флаг
    if (AppVars.FastNeed) {
        AppVars.FastNeed = false;
        FileLogger.trace(TAG, 
            "FastNeed cleared after cooldown: " + actionType);
        
        // ✅ Возобновить другие автофункции
        requestAutoTurnResume();
    }
    
    cooldownTimers.remove(actionType);
}

private static int getCooldownForAction(String actionType) {
    // Типичные cooldown'ы:
    switch (actionType) {
        case "elixir_health":    return 60000;  // 60 сек
        case "elixir_mana":      return 60000;  // 60 сек
        case "elixir_stamina":   return 60000;  // 60 сек
        case "spell_breakthrough": return 30000;  // 30 сек
        case "spell_stun":       return 45000;  // 45 сек
        default:                 return 90000;  // 90 сек (conservative)
    }
}
```

---

## Взаимодействие с ForcedActionGuard

### Проблема: Блокировка fast-action'ов

**Сценарий:**
```
T+0ms:   FastActionManager вызывает fastAttackBlazElixir(vcode)
         → AppVars.FastNeed = true

T+10ms:  ForcedActionGuard проверяет: shouldForceActionAdvanced()
         → Видит FastNeed=true
         → Может заблокировать другие действия

T+60s:   Cooldown истекает
         → onCooldownExpired() вызывается
         → AppVars.FastNeed = false
         → Другие функции разблокируются
```

**Правильная интеграция:**

```java
// В ForcedActionGuard.java:
public static boolean shouldForceActionAdvanced(
        String actionName,
        boolean uiForegroundLikely,
        boolean fightLikelyActive,
        boolean... otherBlockers) {
    
    // ✅ Если идёт fast-action, блокировать всё остальное
    if (AppVars.FastNeed) {
        return false;  // Заблокировать autofish, autobattle и т.д.
    }
    
    // ✅ Если идёт бой, разрешить autoTurn (не блокировать)
    if (fightLikelyActive) {
        return true;
    }
    
    // ... остальная логика ...
}
```

### Проблема: Застревание FastNeed

**Симптом:**
```
FastNeed=true долгое время спустя cooldown
→ AutoFish зависает на неопределённый срок
```

**Диагностика:**
```bash
# Проверить FastNeed состояние
grep "FastNeed" mainphp.log | tail -20

# Проверить вызовы cooldown expiry
grep "FastNeed cleared" fastactionmanager.log | tail -5

# Если вторая команда вернула мало результатов:
# ❌ ПРОБЛЕМА: cooldown обработчик не вызывается
```

**Решение: Guard timers**

```java
// Добавить в FastActionManager guard на случай зависания:
private static void checkAndClearStuckFastNeed() {
    if (!AppVars.FastNeed) return;
    
    long elapsed = System.currentTimeMillis() - lastFastActionTime;
    
    // Если FastNeed установлен больше чем 2x максимальный cooldown
    if (elapsed > 2 * 90 * 1000) {  // 180 сек
        FileLogger.warn(TAG, 
            "BUG: FastNeed stuck for " + elapsed + "ms, forcing clear");
        
        AppVars.FastNeed = false;
        requestAutoTurnResume();
    }
}

// Вызвать этот check периодически:
// В MainPhp.processMainPhpPage():
checkAndClearStuckFastNeed();  // Вызвать перед другой логикой
```

---

## VCode для fast-action'ов: где взять?

### Сценарий 1: Fast-action из MainPhp

**Код:**
```java
// В MainPhp.java:
public void consumeElixirIfNeeded() {
    // ✅ Получить свежий VCode
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("fast_action");
    
    if (vcode == null) {
        FileLogger.trace(TAG, "No VCode for fast action yet");
        return;  // Подождём следующего цикла
    }
    
    // ✅ Отправить fast-action
    FastActionManager.fastAttackBlazElixir(vcode);
}
```

### Сценарий 2: Fast-action во время боя

**Код:**
```java
// В FightAuto.java:
public void autoAttackWithElixirSupport(String html) {
    SessionManager.getInstance().markFightInProgress();
    LezFight fight = new LezFight(html);
    
    // Проверить, нужен ли эликсир
    if (fight.getPersonaHealth() < CRITICAL_HP) {
        // ✅ Получить VCode для fast-action (может быть FIGHT_FALLBACK_MODE)
        String vcode = SessionManager.getInstance()
            .getValidVCodeForAction("fast_action");
        
        if (vcode != null) {
            // Использовать эликсир
            FastActionManager.fastAttackBlazElixir(vcode);
            
            // ⚠️ ВАЖНО: После этого AppVars.FastNeed = true
            // Поэтому autoTurn будет заблокирована временно
            
            return;  // Выйти из боя, дождаться cooldown'а
        }
    }
    
    // Обычный ход
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("fight_turn");
    
    if (vcode != null) {
        submitTurn(fight, vcode);
    }
}
```

### Сценарий 3: Cascading fast-actions (несколько подряд)

**Проблема:**
```
T+0s:   Использовать эликсир здоровья
        → AppVars.FastNeed = true
        → cooldown = 60 сек

T+5s:   Нужно срочно использовать эликсир маны?
        → AppVars.FastNeed ещё true! Не можем!
```

**Решение: Очередь fast-actions**

```java
public class FastActionQueue {
    private final Queue<FastActionRequest> queue = new LinkedList<>();
    private volatile boolean processing = false;
    
    public void enqueue(String actionType, String vcode) {
        queue.add(new FastActionRequest(actionType, vcode));
        processQueue();
    }
    
    private synchronized void processQueue() {
        if (processing || queue.isEmpty()) return;
        if (AppVars.FastNeed) return;  // Ещё один fast-action выполняется
        
        processing = true;
        
        FastActionRequest request = queue.poll();
        
        // Отправить действие
        FastActionManager.fastAction(
            request.actionType, 
            request.vcode);
        
        // Guard на case зависания:
        new Handler().postDelayed(() -> {
            processing = false;
            processQueue();  // Попытаться обработать следующий элемент
        }, 2 * getMaxCooldown());
    }
}
```

---

## Правила fast-action'ов с VCode

### ✅ ВСЕГДА ДЕЛАЙТЕ:

1. **Проверяйте null VCode:**
   ```java
   String vcode = SessionManager.getInstance()
       .getValidVCodeForAction("fast_action");
   if (vcode == null) return;  // Не отправлять без VCode
   ```

2. **Устанавливайте FastNeed ПЕРЕД отправкой:**
   ```java
   AppVars.FastNeed = true;
   FastActionManager.fastAction(vcode);
   ```

3. **Планируйте cooldown обработчик:**
   ```java
   scheduleCooldownTimer(actionType);
   // Это автоматически очистит FastNeed
   ```

4. **Логируйте все этапы:**
   ```java
   FileLogger.trace(TAG, "FastNeed set");
   FileLogger.trace(TAG, "Fast action sent");
   FileLogger.trace(TAG, "Cooldown scheduled");
   FileLogger.trace(TAG, "FastNeed cleared");
   ```

5. **Обрабатывайте ошибки 403:**
   ```java
   if (statusCode == 403) {
       FileLogger.error(TAG, "VCode invalid for fast action");
       AppVars.FastNeed = false;
   }
   ```

### ❌ НИКОГДА НЕ ДЕЛАЙТЕ:

1. **Не отправляйте fast-action без VCode:**
   ```java
   // ❌ ЗАПРЕЩЕНО:
   FastActionManager.fastAction(null);
   ```

2. **Не забывайте устанавливать FastNeed:**
   ```java
   // ❌ ЗАПРЕЩЕНО:
   FastActionManager.fastAction(vcode);
   // FastNeed не установлен!
   ```

3. **Не забывайте очищать FastNeed:**
   ```java
   // ❌ ЗАПРЕЩЕНО:
   AppVars.FastNeed = true;
   FastActionManager.fastAction(vcode);
   // FastNeed никогда не очищается!
   // → AutoFish зависает навстречу eternity
   ```

4. **Не используйте старый AppVars.VCode:**
   ```java
   // ❌ ЗАПРЕЩЕНО:
   FastActionManager.fastAction(AppVars.VCode);
   ```

5. **Не игнорируйте параллельные fast-actions:**
   ```java
   // ❌ ЗАПРЕЩЕНО:
   // Если AppVars.FastNeed уже true, не отправляйте второй fast-action!
   if (!AppVars.FastNeed) {
       FastActionManager.fastAction(vcode);
   }
   ```

---

## Примеры кода

### Пример 1: Базовое использование эликсира

```java
public void consumeHealthElixirIfCritical() {
    // Проверить здоровье
    if (AppVars.PersonaHealth > CRITICAL_HP) return;
    
    // Получить VCode
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("fast_action");
    
    if (vcode == null) {
        FileLogger.trace(TAG, "No VCode available yet");
        return;
    }
    
    // Использовать эликсир
    AppVars.FastNeed = true;
    FastActionManager.fastAttackBlazElixir(vcode);
    
    lastElixirTime = System.currentTimeMillis();
    
    // Запланировать очистку
    scheduleElixirCooldown();
    
    FileLogger.trace(TAG, "Health elixir action initiated");
}

private void scheduleElixirCooldown() {
    new Handler().postDelayed(() -> {
        if (AppVars.FastNeed) {
            AppVars.FastNeed = false;
            FileLogger.trace(TAG, "Elixir cooldown expired, FastNeed cleared");
        }
    }, ELIXIR_COOLDOWN_MS + 1000);
}
```

### Пример 2: Fast-action во время боя с fallback

```java
public void emergencyElixirDuringBattle() {
    LezFight fight = currentFight;
    if (fight == null) return;
    
    // Критичное здоровье во время боя
    if (fight.getPersonaHealth() > EMERGENCY_HP) return;
    
    // Получить VCode (может быть FIGHT_FALLBACK_MODE)
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("fast_action");
    
    if (vcode == null) {
        FileLogger.warn(TAG, "No VCode for emergency elixir");
        // Бой без эликсира... печально
        return;
    }
    
    // Использовать эликсир
    AppVars.FastNeed = true;
    FastActionManager.fastAttackBlazElixir(vcode);
    
    FileLogger.trace(TAG, "Emergency elixir during battle: vcode_age=" 
        + vcode.ageMs() + "ms");
    
    scheduleElixirCooldown();
}
```

### Пример 3: Безопасная обработка ошибок с guard'ом

```java
public void safeElixirUsage(String vcode) {
    if (vcode == null) {
        FileLogger.error(TAG, "Null VCode provided");
        return;
    }
    
    AppVars.FastNeed = true;
    lastFastActionTime = System.currentTimeMillis();
    
    FastActionManager.fastAttackBlazElixir(vcode);
    
    FileLogger.trace(TAG, "Elixir action started with guard");
    
    // Guard: выходить если зависла или просто долго
    scheduleGuardedCooldown();
}

private void scheduleGuardedCooldown() {
    final long startTime = System.currentTimeMillis();
    final long maxWaitTime = ELIXIR_COOLDOWN_MS + 30000;  // +30s buffer
    
    new Handler().postDelayed(() -> {
        long elapsed = System.currentTimeMillis() - startTime;
        
        if (AppVars.FastNeed && elapsed > maxWaitTime) {
            FileLogger.error(TAG, 
                "GUARD: FastNeed stuck for " + elapsed 
                + "ms, forcing clear");
            
            AppVars.FastNeed = false;
        } else if (AppVars.FastNeed) {
            FileLogger.trace(TAG, "Elixir cooldown normal");
            AppVars.FastNeed = false;
        }
    }, maxWaitTime);
}
```

---

## Чек-лист для добавления нового fast-action

- [ ] Функция получает VCode через `SessionManager.getValidVCodeForAction()`
- [ ] Проверена ситуация null VCode с fallback логикой
- [ ] `AppVars.FastNeed = true` установлена ПЕРЕД отправкой
- [ ] Добавлено логирование через `FileLogger.trace()`
- [ ] Запланирован cooldown обработчик с явной очисткой FastNeed
- [ ] Добавлена обработка HTTP кодов ошибок (403, 500, etc.)
- [ ] Добавлен guard на случай зависания (если cooldown > 30s)
- [ ] Протестировано с множественными fast-actions подряд
- [ ] Проверены логи на "FastNeed stuck" или аналогичные ошибки

---

## Заключение

Fast-actions — это мощный инструмент, но требуют аккуратности с VCode:

1. **VCode всегда обязателена** (не может быть null)
2. **FastNeed всегда нужна очистка** (иначе авто-функции зависают)
3. **Cooldown'ы должны быть спланированы** (с guard'ами на зависание)

Следование этим правилам гарантирует, что fast-actions сработают без побочных эффектов на остальную автоматизацию системы.
