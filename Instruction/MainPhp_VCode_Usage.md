# MainPhp.java: Использование VCode для основной страницы

## Обзор

`MainPhp.java` — это главный контроллер обработки основной страницы (`main.php`). Здесь обрабатываются:
- Загрузка основной страницы игры
- Обновление информации персонажа
- Управление фоновыми функциями (авто-рыбалка, авто-питьё, автоборшики)
- Синхронизация фазы игры с локальной моделью

**Ключевая особенность:** MainPhp часто работает в фоне и нуждается в свежем VCode для отправки действий (например, автоматическое использование эликсира).

---

## Критичные точки VCode в MainPhp

### 1. VCode для автоматизированных действий

**Где используется:** При выполнении автоматизированных действий из фона (авто-сокращение, авто-питьё).

**Как это работает:**

```java
// В методе processMainPhpPage():
if (AppVars.AutoFish && !LezFight.IsBoi()) {
    // Нужен свежий VCode для отправки рыбалки запроса
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("autofish");
    
    if (vcode == null) {
        // FALLBACK: перезагрузить основную страницу
        requestAutoTurn();
        return;
    }
    
    // Отправить рыбалку с VCode
    sendAutoFishRequest(vcode);
}
```

**Правило:** **НИКОГДА** не использовать `AppVars.VCode` напрямую. **ОБЯЗАТЕЛЬНО** получить через `SessionManager.getInstance().getValidVCodeForAction()`.

### 2. VCode для быстрых действий (Fast-action)

**Где используется:** При использовании эликсиров или других быстрых действий.

**Пример:**

```java
// Использование эликсира здоровья
if (shouldConsumeHealthElixir()) {
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("consume_elixir");
    
    if (vcode != null) {
        FastActionManager.fastAttackBlazElixir(vcode);
        lastElixirTime = System.currentTimeMillis();
        
        // ✅ ВАЖНО: ОБЯЗАТЕЛЬНО затереть FastNeed после cooldown
        scheduleElixirCooldownTimeout(vcode);
    }
}

// В методе scheduleElixirCooldownTimeout():
private void scheduleElixirCooldownTimeout(String vcode) {
    Handler handler = new Handler();
    handler.postDelayed(() -> {
        if (AppVars.FastNeed) {
            // Clear flag only if still set (avoid double-clear)
            FastActionManager.fastCancel("elixir_cooldown_finished");
            FileLogger.trace(TAG, 
                "ACTION_TRACE elixir_cooldown_finished, cancel FastNeed");
        }
        // Resume other auto functions
        requestAutoTurn();
    }, ELIXIR_COOLDOWN_MS);
}
```

**Правило:** После любого fast-action используется какой-либо cooldown или задержка, нужно **ОБЯЗАТЕЛЬНО вызвать `fastCancel()`** при завершении этого периода.

### 3. Синтаксис получения VCode с fallback

**ПРАВИЛЬНЫЙ ПАТТЕРН:**

```java
private void processAutoFunctionWithVCode(String actionName) {
    // ✅ ШАГ 1: Получить валидный VCode
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction(actionName);
    
    // ✅ ШАГ 2: Проверить наличие
    if (vcode == null) {
        FileLogger.trace(TAG, 
            "FALLBACK: no valid vcode for " + actionName);
        
        // ✅ ШАГ 3: Fallback - перезагрузить страницу
        // Это инициирует parseVCode() и может дать нам новый VCode
        requestAutoTurn();
        return;
    }
    
    // ✅ ШАГ 4: Отправить действие с VCode
    performActionWithVCode(actionName, vcode);
}

// ❌ НЕПРАВИЛЬНЫЙ ПАТТЕРН (ЗАПРЕЩЕНО):
private void wrongWay() {
    // ❌ НИКОГДА так:
    String vcode = AppVars.VCode;  // ❌ ЗАПРЕЩЕНО!
    
    // ❌ НИКОГДА так:
    performActionWithVCode("fish", vcode);  // vcode может быть null!
    
    // ❌ НИКОГДА так (забытие fastCancel):
    if (actionName.equals("elixir")) {
        useElixir(vcode);
        // ❌ ОШИБКА: FastNeed не очищается! Авто-рыбалка зависнет!
    }
}
```

---

## Жизненный цикл VCode в MainPhp

### Фаза 1: Парсинг (П)

**Триггер:** MainPhp загружается (WebView вызывает `onPageFinished()`).

**Действие:**
```java
// В WebViewRequestInterceptor:
@Override
public void onPageFinished(WebView view, String url) {
    if (url.contains("main.php")) {
        // SessionManager.parseVCode() вызывается автоматически
        String vcode = SessionManager.getInstance().getLastParsedVCode();
        FileLogger.trace("MainPhp", 
            "✅ VCODE_PARSED source=main, vcode=" + vcode);
    }
}
```

### Фаза 2: Кэширование (К)

**Триггер:** VCode успешно распарсилась.

**Действие:**
```java
// SessionManager кэширует VCode
SessionContext context = new SessionContext(
    vcode,              // parsed VCode
    "main",             // source
    System.currentTimeMillis()  // parsedAtMs
);
// ✅ Теперь vcode доступна через getValidVCodeForAction()
```

### Фаза 3: Использование (И)

**Триггер:** Автофункция (авто-рыбалка, авто-питьё) хочет отправить действие.

**Действие:**
```java
// В MainPhp.processAutoFunctionWithVCode():
String vcode = SessionManager.getInstance()
    .getValidVCodeForAction("autofish");

if (vcode != null) {
    // ✅ VCode достаточно свежий, используем
    sendAutoFishRequest(vcode);
}
```

### Фаза 4: Инвалидация (I)

**Триггер:** Либо истекло 120 секунд, либо сменился PHPSESSID.

**Действие:**
```java
// Автоматическое:
// - getValidVCodeForAction() возвращает null для VCode > 120s
// - SessionManager.clearFightContext() вызывается при смене сессии

// Ручное (для отладки):
if (sessionIdChanged()) {
    SessionManager.getInstance().clearFightContext();
    FileLogger.trace("MainPhp", "Session changed, vcode cache cleared");
}
```

### Фаза 5: Fallback (Ф)

**Триггер:** VCode null (невалидна или старая).

**Действие:**
```java
// Перезагрузить основную страницу
loadUrl("http://neverlands.ru/main.php");
// Выждать parseVCode()
// Повторить попытку действия с новым VCode
```

---

## Интеграция с FastActionManager

### Сценарий: Использование эликсира в боевой ситуации

**Проблема:** Эликсир используется во время боя, но нужен свежий VCode.

**Решение:**

```java
public void consumeHealthElixirDuringBattle(int healthAmount) {
    // ✅ ШАГ 1: Получить VCode для боя (может использовать FIGHT_FALLBACK_MODE)
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("consume_elixir");
    
    if (vcode == null) {
        FileLogger.trace(TAG, 
            "No VCode for elixir, game-over by damage");
        // Не можем использовать эликсир без VCode
        return;
    }
    
    // ✅ ШАГ 2: Установить FastNeed flag
    AppVars.FastNeed = true;
    
    // ✅ ШАГ 3: Отправить быстрое действие
    FastActionManager.fastAttackBlazElixir(vcode);
    lastElixirConsumptionTime = System.currentTimeMillis();
    
    // ✅ ШАГ 4: Запланировать очистку FastNeed после cooldown
    scheduleElixirCooldownHandler();
    
    FileLogger.trace(TAG, 
        "Elixir consumed, FastNeed set, cooldown scheduled");
}

private void scheduleElixirCooldownHandler() {
    // Эликсир имеет cooldown обычно 60+ секунд
    Handler handler = new Handler();
    handler.postDelayed(() -> {
        // ✅ КРИТИЧНО: Очистить FastNeed ПОСЛЕ того как cooldown минул
        long elapsed = System.currentTimeMillis() - lastElixirConsumptionTime;
        if (elapsed >= ELIXIR_COOLDOWN_MS && AppVars.FastNeed) {
            FastActionManager.fastCancel("elixir_cooldown_finished");
            
            FileLogger.trace(TAG, 
                "ACTION_TRACE elixir "
                + "cooldown finished ("
                + elapsed + "ms), FastNeed cleared");
            
            // ✅ Теперь автофункции (авто-рыбалка и т.д.) могут возобновиться
            requestAutoTurn();
        }
    }, ELIXIR_COOLDOWN_MS + 1000);  // +1s buffer
}
```

---

## Обработка ошибок VCode

### Ошибка 1: "Нет VCode при загрузке main.php"

**Признак:**
```
grep "VCode null for main page" mainphp.log
```

**Диагностика:**
```java
// В processMainPhpPage():
String vcode = SessionManager.getInstance()
    .getValidVCodeForAction("general");

if (vcode == null) {
    FileLogger.error(TAG, 
        "CRITICAL: No VCode after main.php load! "
        + "Session may be lost.");
}
```

**Восстановление:**
1. Проверить, парсируется ли VCode вообще (SessionManager логи)
2. Убедиться, что WebViewRequestInterceptor.parseVCode() вызывается
3. Перезагрузить приложение полностью

### Ошибка 2: "403 Invalid VCode при быстром действии"

**Признак:**
```
grep "403.*vcode\|Invalid code" mainphp.log
```

**Диагностика:**
```java
// В FastActionManager callback:
if (httpCode == 403) {
    FileLogger.error(TAG, 
        "403 Invalid VCode during fast action. "
        + "Age: " + vcode.ageMs() + "ms");
    
    // Слишком старый VCode?
    if (vcode.ageMs() > 120000) {
        // Перезагрузить main.php
        requestAutoTurn();
    }
}
```

**Восстановление:**
1. Проверить возраст VCode (`ageMs()`)
2. Если > 120s, это нормально — нужна перезагрузка
3. Если < 10s и всё еще 403 — проблема в самом VCode парсинге

### Ошибка 3: "FastNeed зависнул (авто-рыбалка не возобновляется)"

**Признак:**
```
grep "FastNeed=true" mainphp.log
grep -A 60 "FastNeed=true" mainphp.log | grep -c "FastNeed=false"  # должно быть >0
```

**Диагностика:**
```java
// Проверить, очищается ли FastNeed:
if (AppVars.FastNeed && 
    System.currentTimeMillis() - lastElixirTime > ELIXIR_COOLDOWN_MS * 2) {
    // ❌ ПРОБЛЕМА: FastNeed не очищена спустя 2x cooldown
    FileLogger.error(TAG, 
        "BUG: FastNeed stuck=true, auto-fishing frozen!");
}
```

**Восстановление:**
1. Проверить, вызывается ли `fastCancel()` после cooldown
2. Убедиться, что `scheduleElixirCooldownHandler()` вызывается
3. Если проблема повторяется, добавить guard в MainPhp:
   ```java
   // Guard: регулярно проверять, не зависла ли FastNeed
   if (AppVars.FastNeed && timeSinceElixirUsage > 5 * ELIXIR_COOLDOWN_MS) {
       // Безопасно очистить
       FastActionManager.fastCancel("forced_clear_stale_fastneed");
   }
   ```

---

## Правила использования VCode в MainPhp

### ✅ ВСЕГДА ДЕЛАЙТЕ:

1. **Получайте VCode через SessionManager:**
   ```java
   String vcode = SessionManager.getInstance()
       .getValidVCodeForAction("action_name");
   ```

2. **Проверяйте null перед использованием:**
   ```java
   if (vcode == null) {
       // fallback
   }
   ```

3. **Логируйте VCode операции:**
   ```java
   FileLogger.trace(TAG, "Using VCode for " + actionName);
   ```

4. **Очищайте FastNeed после cooldown:**
   ```java
   FastActionManager.fastCancel("reason");
   ```

5. **Перезагружайте main.php при null VCode:**
   ```java
   loadUrl("http://neverlands.ru/main.php");
   ```

### ❌ НИКОГДА НЕ ДЕЛАЙТЕ:

1. **Не используйте `AppVars.VCode` напрямую:**
   ```java
   // ❌ ЗАПРЕЩЕНО:
   String vcode = AppVars.VCode;
   ```

2. **Не игнорируйте null VCode:**
   ```java
   // ❌ ЗАПРЕЩЕНО:
   String vcode = SessionManager.getInstance()
       .getValidVCodeForAction("fish");
   sendFishRequest(vcode);  // vcode может быть null!
   ```

3. **Не забывайте очищать FastNeed:**
   ```java
   // ❌ ЗАПРЕЩЕНО:
   FastActionManager.fastAttackBlazElixir(vcode);
   // ... забыли fastCancel!
   ```

4. **Не сохраняйте VCode в переменную на длительное время:**
   ```java
   // ❌ ЗАПРЕЩЕНО:
   String cachedVCode = getSomeVCode();
   // 30 минут спустя...
   sendRequest(cachedVCode);  // VCode 30 минут старый!
   ```

5. **Не вызывайте fast-action если VCode отсутствует:**
   ```java
   // ❌ ЗАПРЕЩЕНО:
   FastActionManager.fastAttackBlazElixir(null);
   ```

---

## Интеграционный контрольный список

Перед добавлением новой автофункции в MainPhp:

- [ ] Функция получает VCode через `SessionManager.getInstance().getValidVCodeForAction()`
- [ ] Обработана ситуация null VCode (fallback на loadUrl)
- [ ] Добавлено логирование через `FileLogger.trace()`
- [ ] Если используется FastNeed, добавлена очистка через `fastCancel()`
- [ ] Cooldown обработан правильно (не забыта очистка флага)
- [ ] Протестировано с множественными запусками avto-функции
- [ ] Проверены логи на предмет "VCode related" ошибок

---

## Примеры кода: готовые паттерны

### Паттерн 1: Простое автодействие (без cooldown)

```java
private void performSimpleAutoAction(String actionName) {
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction(actionName);
    
    if (vcode == null) {
        loadUrl("http://neverlands.ru/main.php");
        return;
    }
    
    sendActionRequest(actionName, vcode);
    FileLogger.trace(TAG, "Action " + actionName + " sent");
}
```

### Паттерн 2: Быстрое действие с cooldown

```java
private void performFastActionWithCooldown(String actionName, long cooldownMs) {
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction(actionName);
    
    if (vcode == null) {
        loadUrl("http://neverlands.ru/main.php");
        return;
    }
    
    AppVars.FastNeed = true;
    FastActionManager.fastAction(actionName, vcode);
    lastActionTime = System.currentTimeMillis();
    
    new Handler().postDelayed(() -> {
        if (AppVars.FastNeed && 
            System.currentTimeMillis() - lastActionTime >= cooldownMs) {
            FastActionManager.fastCancel(actionName + "_cooldown_finished");
            FileLogger.trace(TAG, "Cooldown finished, FastNeed cleared");
        }
    }, cooldownMs + 1000);
}
```

### Паттерн 3: Условное автодействие в фоне

```java
private void checkAndPerformAutoAction() {
    // Проверить сложное условие
    if (!shouldPerformAction()) return;
    
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("conditional_action");
    
    if (vcode == null) {
        // Не перезагружать сразу, ждём следующего цикла
        // (чтобы не забомбить loadUrl вызовами)
        return;
    }
    
    sendActionRequest("conditional_action", vcode);
    FileLogger.trace(TAG, "Conditional action performed");
}
```

---

## Заключение

MainPhp.java — это критичный модуль, который часто работает в фоне и нуждается в постоянном доступе к свежему VCode. 

**Три главных правила:**
1. **ВСЕГДА** получайте VCode через `SessionManager`
2. **ВСЕГДА** проверяйте null
3. **ВСЕГДА** очищайте `FastNeed` после fast-action cooldown

Последование этим правилам гарантирует стабильное автоматизированных действий даже при изменении сессии или потере синхронизации.
