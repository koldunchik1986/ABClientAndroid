# VCode Recovery Guide: Инструкция по восстановлению при багах

## Введение

Этот документ содержит **пошаговые процедуры диагностики и восстановления** для всех типов VCode-связанных ошибок в приложении.

---

## РАЗДЕЛ 1: Признаки VCode бага

### Признак 1: Ошибка "Неверный код защиты" на сервере (403)

```
Server Response HTTP 403:
"Неверный код защиты"

Когда появляется:
- При отправке AJAX запроса (рыбалка, бой, навигация)
- После корректной работы (не на первом же запросе)
- Часто после быстрых последовательных действий
```

**Логирование:**
```
❌ INVALID_CODE_ERROR: actionName=fish_act, failingVCode=3f7c5d4e...
📋 SESSION_INVALIDATED: контекст очищен для переперезагрузки
```

### Признак 2: Лог "NO_SESSION: контекст пуст"

```
⚠️ NO_SESSION: actionName=fight_fallback - контекст пуст
```

**Когда:** Боевой контекст потерян перед отправкой удара

**Последствие:** Бой прерывается, нет возможности выполнить действие

### Признак 3: Лог "PARSE_VCODE_FAILED: no vcode found"

```
⚠️ PARSE_VCODE_FAILED: no vcode found in HTML, source=fight
```

**Кто:** WebViewRequestInterceptor не смог извлечь VCode

**Почему:** HTML не содержит VCode в ожидаемом формате или паттерны не совпали

### Признак 4: Лог "STALE_SESSION: ageMs превышен"

```
⚠️ STALE_SESSION: actionName=nav_bootstrap, ageMs=5050ms, maxAgeMs=300000
```

**Когда:** VCode слишком старый (старше чем timeout)

**Причина:** Нет свежих HTML ответов на протяжении долгого времени

### Признак 5: Бой/действие прерывается после 1-2 попыток

```
Timeline:
10:00:00 - Бой начался, удар успешный
10:00:02 - Удар успешный
10:00:05 - ❌ "Invalid VCode" (бой прерывается)
```

**Причина:** Обычно это проблема 2 (gap между markFightInProgress и LezFight)

---

## РАЗДЕЛ 2: Процедура диагностики

### Шаг 1: Проверить SessionManager логи

**Вопросы:**
1. Парсится ли VCode из HTML?
2. Как часто парсится?
3. Какие паттерны совпадают?

**Команда для поиска в логах:**
```bash
grep "VCODE_PARSED" logcat.txt | head -20
grep "VCODE_EXTRACTED" logcat.txt | head -20
grep "PARSE_VCODE_FAILED" logcat.txt | head -20
```

**Ожидаемый результат:**
```
✅ VCODE_PARSED: source=fight, vcode=3f7c5d4e..., version=142, ageMs=0
✅ VCODE_PARSED: source=fight, vcode=a1b2c3d4..., version=143, ageMs=0
```

**Если видны только FAILED:**
```
⚠️ PARSE_VCODE_FAILED: no vcode found in HTML, source=fish
⚠️ PARSE_VCODE_FAILED: no vcode found in HTML, source=fish
```
→ **Перейти на Уровень 1 (Парсинг VCode)**

---

### Шаг 2: Проверить ageMs VCode

**Вопрос:** Какой возраст VCode при его использовании?

**Команда:**
```bash
grep "VALID_VCODE" logcat.txt | grep -E "ageMs=[0-9]+" -o | sort | uniq -c
```

**Ожидаемый результат:**
```
ageMs=120ms, ageMs=500ms, ageMs=1200ms, ...
```

**Нетипичный результат:**
```
ageMs=5050ms, ageMs=28000ms, ...  ← Очень старые VCode
```

→ **Перейти на Уровень 2 (Кэш VCode)**

---

### Шаг 3: Проверить PHPSESSID изменился ли

**Команда для поиска Set-Cookie:**
```bash
grep "Set-Cookie.*PHPSESSID" logcat.txt
```

**Ожидаемый результат:**
```
[один PHPSESSID на всю сессию]
```

**Нетипичный результат:**
```
Set-Cookie: PHPSESSID=session_1
...delay...
Set-Cookie: PHPSESSID=session_2  ← ИЗМЕНИЛСЯ!
```

→ **Перейти на Уровень 2 (Session invalidation)**

---

### Шаг 4: Проверить markFightInProgress() вызывается ли до LezFight

**Файл:** `app/src/main/java/ru/neverlands/abclient/postfilter/FightAuto.java`

**Ищите в коде (примерно line 390-410):**
```java
// ❌ НЕПРАВИЛЬНО:
new LezFight(html).buildFrame();
// ... 9ms позже
SessionManager.getInstance().markFightInProgress();

// ✅ ПРАВИЛЬНО:
SessionManager.getInstance().markFightInProgress();
// ... сразу
new LezFight(html).buildFrame();
```

**Логи при неправильном порядке:**
```
10:00:00.100 ✅ VCODE_PARSED: source=fight, vcode=3f7c5d4e...
10:00:00.109 ⚠️ NO_SESSION: actionName=fight_fallback - контекст пуст
           (в LezFight.buildFrame())
10:00:00.150 🎯 FIGHT_STARTED: cached vcode=3f7c5d4e...
           (в FightAuto, но уже слишком поздно!)
```

→ **Перейти на Уровень 4 (Синхронизация потоков)**

---

### Шаг 5: Проверить getValidVCodeForAction() результат

**Где искать:** В логах FishAjaxPhp, MainPhp, LezFight

**Команда:**
```bash
grep "VALID_VCODE\|NO_SESSION\|EMPTY_VCODE\|STALE_SESSION" logcat.txt | grep -B5 "INVALID_CODE_ERROR"
```

**Ожидаемый результат:**
```
✅ VALID_VCODE: actionName=fish_act, vcode=3f7c5d4e..., ageMs=245ms
[отправка на сервер]
[сервер принимает VCode]
```

**Нетипичный результат:**
```
⚠️ NO_SESSION: actionName=fish_act - контекст пуст
[отправка ??? - что отправлено?]
❌ INVALID_CODE_ERROR: ...
```

---

## РАЗДЕЛ 3: Recovery Steps

## Уровень 1: Парсинг VCode

### 1.1 Убедиться, что WebViewRequestInterceptor ловит все HTML

**Проверка:**
```bash
grep "Intercepting:" logcat.txt | grep -c "main.php\|ch.php\|pinfo"
```

**Ожидаемо:** Много записей (означает перехват работает)

###1.2 Проверить regex patterns охватывают HTML структуру

**Дейст вие:** Вручную применить паттерны к HTML ответу

```java
// Скопировать из SessionManager.java:
Pattern[] patterns = {
    Pattern.compile("var\\s+fight_pm\\s*=\\s*\\[[^\\[\\]]*?[,\\s]0[,\\s]+\"([a-f0-9]{32})\""),
    Pattern.compile("(?i)name\\s*=\\s*['\"]?vcode['\"]?[^>]*?value\\s*=\\s*['\"]([a-f0-9]{32})['\"]"),
    // ... остальные 4 паттерна
};

// Проверить каждый паттерн на реальном HTML:
String html = <скопировать из браузера>;
for (Pattern p : patterns) {
    Matcher m = p.matcher(html);
    if (m.find()) {
        System.out.println("Matched: " + m.group(1));
    }
}
```

### 1.3 Пересоздать SessionManager.getInstance() если нужно

**Когда это нужно:**
- После significant code changes
- Если SessionManager singleton испортился
- После крапа приложения

**Что делать:**
```java
// Добавить этот вызов в точку инициализации (например, MainActivity):
SessionManager.getInstance().invalidateContext("app_restart");

// Или полностью очистить:
try {
    java.lang.reflect.Field instanceField = 
            SessionManager.class.getDeclaredField("instance");
    instanceField.setAccessible(true);
    instanceField.set(null, new SessionManager());  // Пересоздать singleton
} catch (Exception e) {
    Log.e(TAG, "Failed to reset SessionManager singleton", e);
}
```

**Логирование после fix:**
```
🔄 SessionManager reinitialized
✅ VCODE_PARSED: source=fight, vcode=...
```

---

## Уровень 2: Кэш VCode

### 2.1 Очистить SessionContext через invalidateContext()

**Файл для редактирования:** Где бы вы ни вызывали SessionManager

**Добавить:**
```java
// Очистить кэш при подозрении на проблему
SessionManager.getInstance().invalidateContext("user_action_triggered");

// Затем переперезагрузить страницу или перейти на другой экран
// Это заставит WebViewRequestInterceptor пересоздать свежий контекст
```

**Логирование:**
```
📋 CONTEXT_INVALIDATED: reason=user_action_triggered
✅ VCODE_PARSED: source=fight, vcode=NEW_CODE..., version=1
```

### 2.2 Переинициализировать SessionManager

**Полная очистка:**
```java
// 1. Инвалидировать текущий контекст
SessionManager.getInstance().invalidateContext("reinitialization");

// 2. Очистить боевой контекст если был бой
SessionManager.getInstance().clearFightContext();

// 3. Переперезагрузить текущую страницу
MainActivity mainActivity = (MainActivity) context;
mainActivity.reloadCurrentPage();  // или WebView.reload()
```

**Ожидаемый результат:**
```
📋 CONTEXT_INVALIDATED
🎯 FIGHT_ENDED
[новый запрос к серверу]
✅ VCODE_PARSED: source=main, vcode=..., version=1
```

### 2.3 Пересоздать session с новым PHPSESSID

**Когда это нужно:** После logout/login, если PHPSESSID изменился

**Процедура:**
```java
// 1. Разлогиниться полностью
clearSharedPreferences();  // Очистить сохраненные данные
AppVars.Profile = null;

// 2. Очистить контекст SessionManager
SessionManager.getInstance().invalidateContext("logout");

// 3. Очистить cookies WebView
CookieManager.getInstance().removeAllCookies(null);
CookieManager.getInstance().flush();

// 4. Переперезагрузить приложение
Intent intent = getIntent();
finish();
startActivity(intent);
```

**Логирование:**
```
📋 CONTEXT_INVALIDATED: reason=logout
[CookieManager.removeAllCookies]
[App restart]
✅ VCODE_PARSED: source=login, vcode=NEW_CODE..., version=1
```

---

## Уровень 3: Безопасность действий

### 3.1 Вызвать getValidVCodeForAction() перед каждым запросом

**Текущее состояние (может быть неправильным):**
```java
String vcode = AppVars.VCode;  // ❌ Неправильно: глобальный буфер
if (vcode == null || vcode.isEmpty()) {
    Log.w(TAG, "No VCode in AppVars");
}
sendRequest(vcode);
```

**Правильная реализация:**
```java
String vcode = SessionManager.getInstance().getValidVCodeForAction("action_name");
if (vcode == null) {
    Log.w(TAG, "VCode is null, falling back or reopening page");
    // Fallback: переперезагрузить страницу или skip действие
    reloadPage();
    return;
}
sendRequest(vcode);
```

### 3.2 Добавить null check и fallback

**Шаблон:**
```java
String vcode = SessionManager.getInstance().getValidVCodeForAction("fish_act"); 

// 🎯 ОБЯЗАТЕЛЬНЫЙ NULL CHECK
if (vcode == null) {
    String msg = "FALLBACK_VCODE_NULL: actionName=fish_act, reloading page";
    Log.w(TAG, "⚠️ " + msg);
    FileLogger.warn("FishAjaxPhp", msg);
    
    // Fallback логика (выбрать одно):
    
    // Вариант 1: Переperезагрузить страницу
    mainActivity.reloadCurrentPage();
    return;
    
    // Вариант 2: Skip действие на один цикл
    return;
    
    // Вариант 3: Использовать сохраненный VCode (РИСКОВАННО!)
    if (lastKnownVCode != null && !lastKnownVCode.isEmpty()) {
        vcode = lastKnownVCode;
        Log.w(TAG, "Using lastKnownVCode (might fail)");
    } else {
        return;  // Нет резервного варианта
    }
}

// ✅ Дальше работаем с гарантированно непустым vcode
sendAjaxRequest(vcode, ...);
```

### 3.3 Логировать результат VCode запроса

**Перед отправкой:**
```java
String vcode = SessionManager.getInstance().getValidVCodeForAction("action_name");
if (vcode != null) {
    String msg = "VCode obtained for action_name, vcode=" + vcode.substring(0, 8) + "..., sending request";
    Log.d(TAG, "✅ " + msg);
    FileLogger.trace("ComponentName", msg);
}
```

**При ошибке:**
```java
if (vcode == null) {
    String msg = "VCode is null for action_name, statusStr=" + SessionManager.getInstance().getStatusForLogging();
    Log.w(TAG, "⚠️ " + msg);
    FileLogger.warn("ComponentName", msg);
}
```

---

## Уровень 4: Синхронизация потоков

### 4.1 Убедиться что markFightInProgress() вызвана ДО LezFight

**Файл:** `app/src/main/java/ru/neverlands/abclient/postfilter/FightAuto.java`

**Проверка текущего кода (примерно line 390-410):**

```java
// ❌ НЕПРАВИЛЬНЫЙ КОД (будет найден gap):
if (NEW_FIGHT_detected) {
    String html = htmlResponseFromServer;
    
    // ... какой-то код ...
    
    new LezFight(html).buildFrame();  // Это вызвает getValidVCodeForAction()
    
    // ... 9ms позже ...
    
    SessionManager.getInstance().markFightInProgress();  // ← Слишком поздно!
}
```

**✅ ПРАВИЛЬНЫЙ КОД:**
```java
if (NEW_FIGHT_detected) {
    String html = htmlResponseFromServer;
    
    // 🎯 ВЫЗВАТЬ СРАЗУ, ДО LezFight
    SessionManager.getInstance().markFightInProgress();
    
    // Теперь создавать LezFight
    new LezFight(html).buildFrame();
    
    // markFightInProgress() может быть вызван снова (избыточная защита если хотите)
    // SessionManager.getInstance().markFightInProgress();  // ← Безопасное дублирование
}
```

**Логирование при правильном порядке:**
```
10:00:00.100 ✅ VCODE_PARSED: source=fight, vcode=3f7c5d4e..., version=142
10:00:00.110 🎯 FIGHT_STARTED: cached vcode=3f7c5d4e..., will keep for 120 secs
10:00:00.120 [LezFight.buildFrame() вызывается]
10:00:00.125 ✅ VALID_VCODE: actionName=fight_fallback, vcode=3f7c5d4e..., ageMs=25ms
```

### 4.2 Использовать synchronized блоки где нужно

**Если видны логи о race condition:**
```
⚠️ STALE_SESSION или ❌ INVALID_CODE_ERROR в нечетких местах
```

**Добавить синхронизацию:**
```java
private static final Object fightContextLock = new Object();

// В месте парсинга:
synchronized (fightContextLock) {
    SessionManager.getInstance().parseVCodeFromHtml(html, "fight");
}

// В месте использования:
synchronized (fightContextLock) {
    String vcode = SessionManager.getInstance().getValidVCodeForAction("fight_fallback");
    if (vcode != null) {
        // использовать vcode
    }
}
```

### 4.3 Тестировать параллельные действия

**Сценарий для тестирования:**
```
1. Открыть рыбалку (автоматическое)
2. ОДНОВРЕМЕННО открыть чат
3. Нажать на бой
4. Проверить логи на ошибки VCode
```

**Ожидаемый результат:**
```
✅ VALID_VCODE: actionName=fish_act, source=fish
✅ VALID_VCODE: actionName=chat_poll, source=chat
✅ VALID_VCODE: actionName=fight_fallback, source=fight
```

**Нетипичный результат:**
```
❌ INVALID_CODE_ERROR: actionName=fish_act
⚠️ NO_SESSION: actionName=fight_fallback
```

→ Вернуться на Уровень 4.1 и 4.2

---

## РАЗДЕЛ 4: Чек-лист для каждого бага VCode

### Когда обнаружена новая VCode ошибка:

Используйте этот чек-лист для систематической диагностики

**Шаг 1: Парсинг**
- [ ] Парсится ли VCode из HTML? (Check Step 1 in Diagnostics)
- [ ] Какие паттерны совпадают? (6 patterns check)
- [ ] Парсится ли из всех источников (fight, fish, main, chat)?

**Шаг 2: Кеширование**
- [ ] VCode кешируется в SessionManager? (VCODE_PARSED logs)
- [ ] PHPSESSID не изменился? (Check Step 3 in Diagnostics)
- [ ] Контекст имеет свежую версию?

**Шаг 3: Использование**
- [ ] SessionManager.getValidVCodeForAction() возвращает непустое значение?
- [ ] Null check добавлен в обработчик?
- [ ] Fallback логика на месте?

**Шаг 4: Синхронизация**
- [ ] markFightInProgress() вызвана ДО LezFight? (Line order check)
- [ ] Нет race condition между WebView и Fish потоками?
- [ ] ReentrantReadWriteLock работает корректно?

**Шаг 5: Логирование**
- [ ] Логирование покрывает все пути (success, null, stale)?
- [ ] FileLogger.trace() вызывается для критичных событий?
- [ ] Какого уровня логирование помогает найти проблему?

**Шаг 6: Тестирование**
- [ ] Тест на параллельные потоки (Fish + Chat)?
- [ ] Тест на смену PHPSESSID (logout/login)?
- [ ] Тест на быстрые последовательные действия?

**Шаг 7: Документирование**
- [ ] Описано в какой компонент добавлена исправление?
- [ ] Commit message содержит "VCode" тег?
- [ ] Ссылка на SessionManager документацию?

---

## РАЗДЕЛ 5: Распространённые сценарии и их решения

### Сценарий A: Бой прерывается на 5-й секунде

**Логи:**
```
10:00:00.100 🎯 FIGHT_STARTED: cached vcode=3f7c5d4e...
10:00:00.105 ✅ VALID_VCODE: actionName=fight_fallback, vcode=3f7c5d4e..., ageMs=5ms
10:00:02.150 ✅ VALID_VCODE: actionName=fight_fallback, vcode=3f7c5d4e..., ageMs=2050ms
10:00:05.200 ✅ VALID_VCODE: actionName=fight_fallback, vcode=3f7c5d4e..., ageMs=5100ms
10:00:07.250 ⚠️ STALE_SESSION: actionName=fight_fallback, ageMs=7150ms, maxAgeMs=120000
```

**Диагноз:** Нет новых HTML ответов для обновления VCode

**Решение:** Уровень 1 (Парсинг) - проверить будут ли новые выходы с сервера

---

### Сценарий B: Ошибка "Invalid VCode" после login

**Логи:**
```
[logout]
📋 CONTEXT_INVALIDATED: reason=logout
[login]
✅ VCODE_PARSED: source=auth, vcode=aaa111..., version=1
✅ VALID_VCODE: actionName=nav_bootstrap, vcode=aaa111..., ageMs=15ms
❌ INVALID_CODE_ERROR: actionName=nav_bootstrap, failingVCode=aaa111...
```

**Диагноз:** PHPSESSID изменился между logout и login, но SessionManager не знает

**Решение:** Уровень 2 (Кэш VCode) - добавить проверку PHPSESSID

```java
public SessionContext parseVCodeFromHtml(String html, String source) {
    SessionContext newContext = new SessionContext(...);
    
    // 🎯 ДОБАВИТЬ ПРОВЕРКУ PHPSESSID
    if (currentContext != null && !newContext.isSameSessionAs(currentContext)) {
        Log.w(TAG, "⚠️ SESSION_CHANGED: PHPSESSID mismatch, clearing old context");
        currentContext = null;  // Очистить старый контекст
    }
    
    contextLock.writeLock().lock();
    try {
        currentContext = newContext;
        Log.d(TAG, "✅ VCODE_PARSED: ...");
    } finally {
        contextLock.writeLock().unlock();
    }
    return newContext;
}
```

---

### Сценарий C: Рыбалка зависает после 10 запросов

**Логи:**
```
✅ VALID_VCODE: actionName=fish_act, vcode=aaa..., ageMs=120ms
✅ VALID_VCODE: actionName=fish_act, vcode=aaa..., ageMs=520ms
✅ VALID_VCODE: actionName=fish_act, vcode=aaa..., ageMs=1200ms
✅ VALID_VCODE: actionName=fish_act, vcode=aaa..., ageMs=3500ms
⚠️ NO_SESSION: actionName=fish_act - контекст пуст
[рыбалка останавливается]
```

**Диагноз:** SessionManager.currentContext = null (был инвалидирован где-то)

**Решение:** Уровень 2 (Кэш VCode) - найти где currentContext очищается

```bash
# Поиск в коде:
grep -r "currentContext = null" app/src/main/java/
grep -r "invalidateContext" app/src/main/java/
```

Найти вызывающего и проверить должен ли он очищать контекст.

---

### Сценарий D: 9ms gap между markFightInProgress и LezFight

**Логи:**
```
10:00:00.100 ✅ VCODE_PARSED: source=fight, vcode=3f7c5d4e..., version=142
10:00:00.109 ⚠️ NO_SESSION: actionName=fight_fallback - контекст пуст
           [NEW HTML RESPONSE CAME HERE (in 9ms gap)]
10:00:00.110 ✅ VCODE_PARSED: source=fight, vcode=bbb222..., version=143
10:00:00.115 🎯 FIGHT_STARTED: cached vcode=bbb222...
           [но это слишком поздно!]
```

**Диагноз:** Новый HTML пришёл между LezFight и markFightInProgress, overwrite VCode

**Решение:** Уровень 4 (Синхронизация) - switch order в FightAuto.java

```java
// ❌ СТАРЫЙ КОД:
new LezFight(html).buildFrame();  // Вызывает getValidVCodeForAction() (FAIL!)
SessionManager.getInstance().markFightInProgress();  // Кэширует (слишком поздно)

// ✅ НОВЫЙ КОД:
SessionManager.getInstance().markFightInProgress();  // Кэширует СНАЧАЛА
new LezFight(html).buildFrame();  // Теперь getValidVCodeForAction() вернет кэш
```

---

## РАЗДЕЛ 6: Advanced Diagnostics

### 6.1 Вывести полный статус SessionManager

**Добавить метод для отладки:**
```java
public String getFullDiagnostics() {
    contextLock.readLock().lock();
    try {
        if (currentContext == null) {
            return "SessionManager { currentContext=null, fightInProgress=" + fightInProgress + 
                   ", fightStartVCode=" + (fightStartVCode != null ? fightStartVCode.substring(0,8) + "..." : "null") + " }";
        }
        return "SessionManager { " + currentContext.toString() + 
               ", fightInProgress=" + fightInProgress + 
               ", fightStartVCode=" + (fightStartVCode != null ? fightStartVCode.substring(0,8) + "..." : "null") + " }";
    } finally {
        contextLock.readLock().unlock();
    }
}
```

**Использование:**
```java
Log.d("Diagnostic", SessionManager.getInstance().getFullDiagnostics());
// Output:
// SessionManager { SessionContext{source='fight', vcode='3f7c5d4e...', ageMs=1250, version=142, htmlLen=52480}, 
//                   fightInProgress=true, fightStartVCode='3f7c5d4e...' }
```

###6.2 Логировать все AJAX запросы с использованной VCode

**В FishAjaxPhp.java и других местах:**
```java
String vcode = SessionManager.getInstance().getValidVCodeForAction("fish_act");
if (vcode != null) {
    String msg = "AJAX_REQUEST: action=fish_act, vcode=" + vcode.substring(0, 8) + "..., " +
                 "status=" + SessionManager.getInstance().getStatusForLogging();
    Log.i(TAG, "📤 " + msg);
    FileLogger.trace("FishAjaxPhp", msg);
    
    // Отправить AJAX
    sendFishAjaxViaWebView(vcode);
}
```

### 6.3 Перехватить ошибку "Invalid VCode" от сервера

**В обработчике ответа:**
```java
if (responseStatus == 403 && responseBody.contains("Неверный код защиты")) {
    String currentVCodeStatus = SessionManager.getInstance().getStatusForLogging();
    String msg = "SERVER_ERROR_403_INVALID_VCODE: attempting with action=" + actionName +
                 ", current_session=" + currentVCodeStatus;
    Log.e(TAG, "❌ " + msg);
    FileLogger.error("AJAX_Handler", msg);
    
    // Инвалидировать контекст для переперезагрузки на следующий раз
    SessionManager.getInstance().onInvalidProtectionCodeError(lastUsedVCode, actionName);
}
```

---

## Заключение

**Инструкция по восстановлению VCode багов состоит из 4 уровней:**

| Уровень | Область | Когда | Действие |
|---------|---------|-------|---------|
| 1 | Парсинг | VCode не парсится | Проверить regex, WebView |
| 2 | Кэш | VCode теряется | Проверить контекст, PHPSESSID |
| 3 | Безопасность | Null или stale | Добавить checks, fallback |
| 4 | Потоки | Race conditions | Синхронизировать вызовы |

**Для каждого нового бага:**
1. ✅ Использовать чек-лист из Раздела 4
2. ✅ Выполнить процедуру диагностики из Раздела 2
3. ✅ Применить соответствующий уровень recovery из Раздела 3
4. ✅ Использовать Advanced Diagnostics из Раздела 6 если нужна более подробная информация

**Помните:** VCode система очень надежна благодаря SessionManager, но требует точного соблюдения порядка вызовов и регулярного логирования для диагностики проблем.
