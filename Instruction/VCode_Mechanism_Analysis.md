# VCode Механизм: Полный архитектурный анализ

## Введение

**VCode** (код защиты / validation code) — это временный криптографический токен от сервера Neverlands для защиты от несанкционированных запросов. Система управления VCode в приложении имеет единую архитектуру, основанную на классах:
- `SessionManager` — главный обработчик
- `SessionContext` — контейнер данных сеанса
- `WebViewRequestInterceptor` — источник свежих VCode из HTML

---

## 1. Архитектура VCode кеширования

### 1.1 SessionManager: Центральный компонент

**Файл:** `app/src/main/java/ru/neverlands/abclient/utils/SessionManager.java`

**Природа:** Singleton с thread-safe операциями через `ReentrantReadWriteLock`

```
┌─────────────────────────────────────────────────────────────┐
│ SessionManager (Singleton)                                       │
├─────────────────────────────────────────────────────────────┤
│                                                                 │
│ Поля:                                                           │
│  - currentContext: SessionContext (READ/WRITE LOCK)            │
│  - contextLock: ReentrantReadWriteLock                         │
│  - fightInProgress: boolean (VOLATILE)                         │
│  - fightStartVCode: String (кэш VCode боя)                   │
│  - FIGHT_CONTEXT_TIMEOUT = 120_000ms (2 минуты)             │
│  - VCODE_PATTERNS[6]: Pattern[] (6 regex паттернов)           │
│                                                                 │
│ Публичные методы:                                              │
│  1. parseVCodeFromHtml(html, source) → SessionContext         │
│  2. getValidVCodeForAction(actionName, maxAgeMs) → String    │
│  3. markFightInProgress() → void                              │
│  4. clearFightContext() → void                                │
│  5. onInvalidProtectionCodeError(vcode, action) → void       │
│  6. invalidateContext(reason) → void                          │
│                                                                 │
│ Приватные методы:                                              │
│  - extractVCode(html) → String (применяет 6 паттернов)        │
│                                                                 │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 SessionContext: Контейнер данных

**Файл:** `app/src/main/java/ru/neverlands/abclient/utils/SessionContext.java`

**Структура:**
```java
public class SessionContext {
    private final String htmlContent;           // Полный HTML ответа
    private final String parsedVCode;           // Распарсенный 32-символьный HEX код
    private final long timestamp;               // Время парсинга (System.currentTimeMillis())
    private final String source;                // Источник: "fight", "fish", "main", "pinfo"
    private final long contextVersion;          // Версия контекста (инкрементируется)
    private final String phpsessid;            // PHPSESSID из серверного ответа
    private final Map<String, String> attributes; // Дополнительные метаданные
    
    // Методы:
    public long getAgeMs()              // Возраст контекста в миллисекундах
    public boolean isExpired(long maxAgeMs)  // Проверка истечения
    public boolean isSameSessionAs(SessionContext other)  // Проверка того же сеанса
}
```

**Дефолтный timeout:** 5 минут (300_000ms) для обычных действий

---

## 2. Точки парсинга VCode (6 regex patterns)

### 2.1 Все 6 паттернов в SessionManager

```java
private static final Pattern[] VCODE_PATTERNS = {
    // 1️⃣ fight_pm array (боевой контекст):
    // var fight_pm = [300,200,70,0,"3f7c5d...",...]
    Pattern.compile("var\\s+fight_pm\\s*=\\s*\\[[^\\[\\]]*?[,\\s]0[,\\s]+\"([a-f0-9]{32})\""),
    
    // 2️⃣ vcode в hidden input:
    // <input ... name=vcode value="...">
    Pattern.compile("(?i)name\\s*=\\s*['\"]?vcode['\"]?[^>]*?value\\s*=\\s*['\"]([a-f0-9]{32})['\"]"),
    
    // 3️⃣ vcode как параметр в URL или форме:
    // &vcode=... или vcode=...
    Pattern.compile("[&?]vcode\\s*=\\s*['\"]?([a-f0-9]{32})['\"]?"),
    
    // 4️⃣ Generic vcode = "..."
    Pattern.compile("vcode\\s*[=:]\\s*['\"]([a-f0-9]{32})['\"]"),
    
    // 5️⃣ var vcode = "..."
    Pattern.compile("var\\s+vcode\\s*=\\s*['\"]([a-f0-9]{32})['\"]"),
    
    // 6️⃣ "vcode": "..." (JSON)
    Pattern.compile("['\"]vcode['\"]\\s*:\\s*['\"]([a-f0-9]{32})['\"]")
};
```

### 2.2 Приоритет и порядок

Паттерны применяются **последовательно** в методе `extractVCode()`:
1. Проверяется каждый паттерн по очереди
2. При совпадении первого же паттерна возвращается найденный VCode
3. Если ни один не совпал → возвращается пустая строка ""

```java
private String extractVCode(String html) {
    if (html == null || html.isEmpty()) {
        return "";
    }

    for (Pattern pattern : VCODE_PATTERNS) {  // ← Последовательный перебор
        try {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String vcode = matcher.group(1);
                if (!vcode.isEmpty() && vcode.length() == 32) {
                    Log.d(TAG, "📍 VCODE_EXTRACTED: pattern matched, vcode=" + vcode.substring(0, 8) + "...");
                    FileLogger.trace("SessionManager", ...);
                    return vcode;  // ← Сразу возврат при совпадении
                }
            }
        } catch (Exception e) {
            // Продолжить со следующего паттерна
        }
    }

    return "";  // Ни один паттерн не совпал
}
```

---

## 3. Жизненный цикл VCode

### 3.1 Фаза 1: Парсинг из HTML (WebViewRequestInterceptor)

**Точка входа:** WebView перехватывает GET-запрос к `neverlands.ru`

```java
// WebViewRequestInterceptor.intercept() → line ~520-540
public static WebResourceResponse intercept(WebResourceRequest request) {
    // ... подготовка request ...
    
    byte[] processed = Filter.process(context, urlString, bytes);  // ← Фильтрация
    
    // ╔══════════════════════════════════════════════════════════╗
    // ║ КРИТИЧНАЯ ТОЧКА: Парсинг VCode из HTML ответа          ║
    // ╚══════════════════════════════════════════════════════════╝
    String source = determineSourceFromUrl(urlString);  // "fight", "fish", "main" ...
    try {
        String htmlContent = new String(processed, Charset.forName("windows-1251"));
        SessionManager.getInstance()
                .parseVCodeFromHtml(htmlContent, source);  // ← ВЫЗОВ ПАРСИНГА
    } catch (Exception e) {
        Log.e(TAG, "Failed to parse VCode from HTML: " + e.getMessage());
    }
    
    // Вернуть обработанный ответ в WebView
    return new WebResourceResponse(contentType, "utf-8", new ByteArrayInputStream(processed));
}
```

**Логирование при успехе:**
```
✅ VCODE_PARSED: source=fight, vcode=3f7c5d4e..., version=142, ageMs=0
```

**Логирование при неудаче:**
```
⚠️ PARSE_VCODE_FAILED: no vcode found in HTML, source=fish
```

### 3.2 Фаза 2: Кеширование в SessionContext

**Метод:** `SessionManager.parseVCodeFromHtml()`

```java
public SessionContext parseVCodeFromHtml(String html, String source) {
    // 1. Извлечь VCode из HTML
    String vcode = extractVCode(html);  // ← Применяет 6 паттернов
    if (vcode.isEmpty()) {
        Log.w(TAG, "⚠️ PARSE_VCODE_FAILED: no vcode found in HTML, source=" + source);
        return null;  // ← Ранний выход
    }

    // 2. Создать новый SessionContext с инкрементированной версией
    long newVersion = (currentContext != null ? currentContext.getContextVersion() : 0L) + 1;
    
    SessionContext newContext = new SessionContext(
            html,                                           // Полный HTML ответа
            vcode,                                          // Распарсенный VCode
            System.currentTimeMillis(),                     // Текущее время парсинга
            source,                                         // Источник ("fight", "fish", ...)
            newVersion                                      // Версия контекста
    );

    // 3. Обновить WRITE-LOCK (блокирует все читающие потоки)
    contextLock.writeLock().lock();
    try {
        currentContext = newContext;  // ← ATOMIC UPDATE
        Log.d(TAG, "✅ VCODE_PARSED: source=" + source + ", version=" + newVersion + ", ageMs=0");
        FileLogger.trace("SessionManager", ...);
    } finally {
        contextLock.writeLock().unlock();
    }

    return newContext;
}
```

**Инвариант:** После парсинга контекст всегда содержит:
- Непустой VCode (32 HEX символа)
- Свежий timestamp (ageMs = 0)
- Инкрементированную версию

### 3.3 Фаза 3: Использование перед запросом (getValidVCodeForAction)

**Методы вызова VCode:**
- `FishAjaxPhp.java`: `getValidVCodeForAction("fish_act")`
- `MainPhp.java`: `getValidVCodeForAction("nav_bootstrap")`
- `LezFight.java`: `getValidVCodeForAction("fight_fallback")`

```java
public String getValidVCodeForAction(String actionName, long maxAgeMs) {
    contextLock.readLock().lock();  // ← Все читающие потоки могут работать параллельно
    try {
        // 🎯 Специальная логика для fight_fallback: ВСЕГДА использовать extended timeout
        long actualTimeout = maxAgeMs;
        if ("fight_fallback".equals(actionName)) {
            actualTimeout = FIGHT_CONTEXT_TIMEOUT;  // 2 минуты вместо 5 минут
        }
        
        // Проверка 1: Существует ли контекст?
        if (currentContext == null) {
            // Fallback для боя: использовать кэшированный VCode с начала боя
            if (fightInProgress && fightStartVCode != null && !fightStartVCode.isEmpty()) {
                Log.d(TAG, "🎯 FIGHT_CACHE: using cached vcode from fight start");
                return fightStartVCode;  // ← Возврат кэшированного VCode
            }
            Log.w(TAG, "⚠️ NO_SESSION: actionName=" + actionName + " - контекст пуст");
            return null;  // ← Критичная ошибка
        }

        // Проверка 2: Не пуст ли VCode?
        if (currentContext.getParsedVCode().isEmpty()) {
            Log.w(TAG, "⚠️ EMPTY_VCODE: actionName=" + actionName);
            return null;
        }

        // Проверка 3: Не истёк ли контекст по времени?
        if (currentContext.isExpired(actualTimeout)) {
            long ageMs = currentContext.getAgeMs();
            // Fallback для боя
            if (fightInProgress && fightStartVCode != null && !fightStartVCode.isEmpty()) {
                Log.d(TAG, "🎯 FIGHT_CACHE: vcode expired but using cached from fight start, ageMs=" + ageMs);
                return fightStartVCode;
            }
            Log.w(TAG, "⚠️ STALE_SESSION: actionName=" + actionName + ", ageMs=" + ageMs + ", maxAgeMs=" + actualTimeout);
            return null;  // ← Токен слишком старый
        }

        // ✅ ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ
        String vcode = currentContext.getParsedVCode();
        Log.d(TAG, "✅ VALID_VCODE: actionName=" + actionName 
            + ", vcode=" + vcode.substring(0, 8) + "..."
            + ", ageMs=" + currentContext.getAgeMs()
            + ", source=" + currentContext.getSource());
        FileLogger.trace("SessionManager", ...);
        
        return vcode;
    } finally {
        contextLock.readLock().unlock();
    }
}
```

### 3.4 Фаза 4: Инвалидация и обновление

**При ошибке "Неверный код защиты" (403):**

```java
public void onInvalidProtectionCodeError(String failingVCode, String actionName) {
    Log.e(TAG, "❌ INVALID_CODE_ERROR: actionName=" + actionName 
        + ", failingVCode=" + failingVCode.substring(0, 8) + "...");
    
    contextLock.writeLock().lock();
    try {
        // Инвалидировать контекст, чтобы следующий вызов getValidVCodeForAction вернул null
        if (currentContext != null && failingVCode.equals(currentContext.getParsedVCode())) {
            currentContext = null;  // ← ОЧИСТКА
            Log.d(TAG, "📋 SESSION_INVALIDATED: контекст очищен для переперезагрузки");
        }
    } finally {
        contextLock.writeLock().unlock();
    }
}
```

### 3.5 Фаза 5: Fallback механизм при потере

**Для боевого контекста:**

```java
public void markFightInProgress() {
    fightInProgress = true;
    fightStartTimeMs = System.currentTimeMillis();
    
    // Сохранить текущий VCode для использования во время всего боя
    contextLock.readLock().lock();
    try {
        if (currentContext != null && !currentContext.getParsedVCode().isEmpty()) {
            fightStartVCode = currentContext.getParsedVCode();  // ← КЭШИРОВАНИЕ
            Log.d(TAG, "🎯 FIGHT_STARTED: cached vcode=" + fightStartVCode.substring(0, 8) + "..."
                + ", will keep for " + (FIGHT_CONTEXT_TIMEOUT / 1000) + " secs");
        }
    } finally {
        contextLock.readLock().unlock();
    }
}

public void clearFightContext() {
    fightInProgress = false;
    fightStartTimeMs = 0L;
    fightStartVCode = null;  // ← ОЧИСТКА
    Log.d(TAG, "🎯 FIGHT_ENDED: боевой контекст и кэш vcode очищены");
}
```

---

## 4. Структура SessionContext и версионирование

### 4.1 Зачем нужна версия?

```
REQUEST 1:          REQUEST 2:          REQUEST 3:
HTML ответ 1        HTML ответ 2        HTML ответ 3
    ↓                   ↓                   ↓
SessionContext      SessionContext      SessionContext
version=1           version=2           version=3
timestamp=T1        timestamp=T2        timestamp=T3
vcode=AAA...        vcode=BBB...        vcode=CCC...
```

**Версионирование предотвращает:**
1. **Race conditions** между параллельными модулями (Fish, Chat, Fight)
2. **Смешивание контекстов** из разных ответов сервера
3. **Потерю синхронизации** при быстрых последовательных запросах

### 4.2 Проверка одной сессии

```java
public boolean isSameSessionAs(SessionContext other) {
    if (other == null) return false;
    
    // Если PHPSESSID присутствует - проверяем его
    if (!this.phpsessid.isEmpty() && !other.phpsessid.isEmpty()) {
        return this.phpsessid.equals(other.phpsessid);  // ← ТОЧНАЯ ПРОВЕРКА
    }
    
    // Иначе проверяем версии (должны быть близки - разница ≤ 1)
    return Math.abs(this.contextVersion - other.contextVersion) <= 1;
}
```

---

## 5. Механизм FIGHT_FALLBACK_MODE (120 секунд)

### 5.1 Проблема

**Сценарий:** Идет бой, обработчик запросит VCode для удара:
```
10:00:00.000 - Бой начался, SessionManager.markFightInProgress() кэширует VCode
10:00:05.000 - Нормальный VCode парсится из новой HTML (ageMs=5s)
10:00:10.000 - ЗАВИСАЕТ парсинг HTML (не приходит новый ответ)
10:05:10.000 - Нужно сделать удар, но VCode старый на 5+ минут
10:05:15.000 - SessionManager → STALE_SESSION (прерывание боя)
```

### 5.2 Решение

```java
public String getValidVCodeForAction(String actionName, long maxAgeMs) {
    contextLock.readLock().lock();
    try {
        // 🎯 СПЕЦИАЛЬНАЯ ЛОГИКА: fight_fallback всегда использует 2-минутный timeout
        long actualTimeout = maxAgeMs;
        if ("fight_fallback".equals(actionName)) {
            actualTimeout = FIGHT_CONTEXT_TIMEOUT;  // 120_000ms = 2 минуты
            Log.d(TAG, "🎯 FIGHT_FALLBACK_MODE: using extended timeout 120000ms");
        }
        
        // Если главный контекст истёк но идет бой → использовать кэшированный VCode
        if (currentContext.isExpired(actualTimeout)) {
            if (fightInProgress && fightStartVCode != null) {
                return fightStartVCode;  // ← FALLBACK
            }
        }
        // ...
    }
}
```

**Преимущества:**
- Бой может продолжаться даже если нет новых HTML ответов до 2 минут
- VCode гарантированно свежий (кэширован в начало боя)
- Падение с 5 минут на 2 минуты только для действий внутри боя

---

## 6. Cache lifetime и aging (ageMs)

### 6.1 Определение ageMs

```java
public long getAgeMs() {
    return System.currentTimeMillis() - this.timestamp;  // ← ДЕЛЬТА ВРЕМЕНИ
}
```

**Пример:**
- Контекст создан в 10:00:00.000 (timestamp = 1000000)
- Текущее время 10:00:05.500
- ageMs = 1000005500 - 1000000000 = 5500ms

### 6.2 Таймауты для разных действий

| Действие | Timeout | Источник |
|----------|---------|----------|
| `fish_act` | 300_000ms (5 мин) | FishAjaxPhp.java |
| `fight_fallback` | 120_000ms (2 мин) | LezFight.java + SessionManager |
| `nav_bootstrap` | 300_000ms (5 мин) | MainPhp.java |
| `searchbox_bootstrap` | 300_000ms (5 мин) | MainPhp.java |
| Дефолт | 300_000ms (5 мин) | SessionManager.getValidVCodeForAction() |

### 6.3 Кривая aging

```
Time: ───────────────────────────────────────────────────▶
Ctx:  FRESH   1s   2s   3s  30s  60s 180s  5min 6min
      (0ms)       ageMs →
      
Status: ✅     ✅   ✅   ✅   ✅   ✅    ✅    ✅   ❌
VALID            Valid для всех timeout'ов               EXPIRED
```

---

## 7. Thread-safety через ReentrantReadWriteLock

### 7.1 Проблема параллелизма

```
Thread 1 (WebView):           Thread 2 (Fish):
parseVCodeFromHtml()          getValidVCodeForAction()
  ↓                             ↓
WriteLock.lock()              ReadLock.lock()
  ↓ (БЛОКИРОВКА)                ↓ (ОЖИДАНИЕ)
Update context            [waiting for all writers...]
  ↓                             ↓ (ПОЛУЧЕНО)
WriteLock.unlock()            Read context
  ↓                             ↓
Send to WebView           Return VCode
```

### 7.2 Решение: ReentrantReadWriteLock

```java
private final ReentrantReadWriteLock contextLock = new ReentrantReadWriteLock();

// WRITE операции (парсинг из HTML)
contextLock.writeLock().lock();
try {
    currentContext = newContext;  // Только один writer может быть в критической секции
} finally {
    contextLock.writeLock().unlock();
}

// READ операции (использование VCode)
contextLock.readLock().lock();  // Много readers могут быть одновременно
try {
    return currentContext.getParsedVCode();
} finally {
    contextLock.readLock().unlock();
}
```

**Гарантия:**
- Любые читающие потоки получат **консистентный** контекст (не half-updated)
- Множество читающих потоков работают параллельно (не блокируют друг друга)
- Писатель (парсинг) блокирует всех читающих (и других писателей)

---

## 8. Критичные точки для сбоев

### 8.1 Проблема 1: VCode инвалиден при смене PHPSESSID

```
REQUEST 1:
  Server: Set-Cookie: PHPSESSID=session_1; vcode=AAA...
  SessionManager: parse vcode=AAA, phpsessid=session_1
  ✅ VCode кешируется

LOGOUT/LOGIN:
  Server: Set-Cookie: PHPSESSID=session_2
  SessionManager: currentContext.phpsessid = session_1 (старый!)

REQUEST 2:
  Client: getValidVCodeForAction()
  SessionManager: проверяет ageMs, ВСЕГО OK (свежий!)
  Server: ❌ "Invalid VCode for this session"
  
  ПРИЧИНА: PHPSESSID изменился, но SessionManager этого не заметил!
```

**Решение:** Проверять PHPSESSID при парсинге нового контекста
```java
if (!newContext.isSameSessionAs(currentContext)) {
    Log.w(TAG, "⚠️ SESSION_CHANGED: clearing old context");
    currentContext = null;
}
```

### 8.2 Проблема 2: 9ms gap между markFightInProgress и LezFight constructor

```
FightAuto.java (line 395-400):

                    String fightStartHtml = htmlResponseFromServer;
                    // ❌ GAP START (это может быть 9ms или 187ms!)
                    // В это время может прийти новый HTML ответ от сервера,
                    // который перезапишет currentContext
                    
                    new LezFight(fightStartHtml).buildFrame();
                    // LezFight constructor вызывает:
                    // getValidVCodeForAction("fight_fallback")
                    // но markFightInProgress() ЕЩЕ не был вызван!
                    
                    SessionManager.getInstance().markFightInProgress();
                    // ❌ GAP END (кэширование VCode слишком поздно!)
```

**Решение:** Вызвать markFightInProgress() ПЕРЕД LezFight()
```java
SessionManager.getInstance().markFightInProgress();  // ← СНАЧАЛА
new LezFight(fightStartHtml).buildFrame();           // ← ПОТОМ
```

### 8.3 Проблема 3: Потеря VCode при быстрых последовательных действиях

```
Action 1 (fish):
  getValidVCodeForAction("fish_act") → VCode=AAA...
  Send to server
  Server delays response for 50ms

Action 2 (navigation):
  New HTML ответ приходит
  parseVCodeFromHtml() → VCode=BBB... (новый VCode!)
  currentContext.timestamp = now

Action 3 (fish callback):
  getValidVCodeForAction("fish_act")
  ageMs уже 45ms (новый контекст же!) но это OK
  Используется VCode=BBB... вместо AAA...
  Server: ❌ "VCode mismatch" (VCode для другой сессии!)
```

**Решение:** Сохранять VCode в локальной переменной перед отправкой
```java
String vcode = SessionManager.getInstance().getValidVCodeForAction("fish_act");
if (vcode == null) {
    Log.w(TAG, "No VCode available");
    return;  // Fallback
}

// ✅ Сейчас vcode в локальной переменной, не зависит от изменений контекста
sendFishAjaxRequest(vcode, ...);
```

### 8.4 Проблема 4: Race condition в многопоточности

```
Thread 1 (WebView):           Thread 2 (Fish):
parseVCodeFromHtml()          getValidVCodeForAction()
  ↓
WriteLock.lock()              
  ↓ (БЛОКИРОВКА)   
  ↓                           (waiting...)
Вычисляет newVersion = 2
  ↓
  ↓ (ВСЕ READERS ЖДУТ!)
Пишет currentContext          
  ↓
WriteLock.unlock()
  ↓                           ReadLock ПОЛУЧЕНА!
  ↓                           ↓ (fish читает версию 2)
Отправляет в WebView         ✅ Корректный VCode
```

**Гарантия:** ReentrantReadWriteLock **устраняет** эту проблему

---

## 9. Источники и примеры использования

### 9.1 Точки парсинга (WRITE)

| Компонент | Метод | Опция |
|-----------|-------|-------|
| WebViewRequestInterceptor | parseVCodeFromHtml() | Основной источник |
| WebViewRequestInterceptor | parseVCodeFromHtml() | Для payment module |

### 9.2 Точки использования (READ)

| Компонент | Метод | ActionName | MaxAgeMs |
|-----------|-------|-----------|----------|
| FishAjaxPhp | getValidVCodeForAction() | "fish_act" | 300_000 |
| FishAjaxPhp | getValidVCodeForAction() | "fish_recovery" | 300_000 |
| MainPhp | getValidVCodeForAction() | "nav_bootstrap" | 300_000 |
| MainPhp | getValidVCodeForAction() | "searchbox_bootstrap" | 300_000 |
| LezFight | getValidVCodeForAction() | "fight_fallback" | 120_000* |
| FightAuto | markFightInProgress() | — | — |
| FightAuto | clearFightContext() | — | — |

*`fight_fallback` переопределяет на 120_000ms внутри SessionManager

### 9.3 Обработка ошибок (ERROR)

| Код ошибки | Компонент | Метод | Действие |
|-----------|-----------|-------|----------|
| 403 (Invalid VCode) | Любой | onInvalidProtectionCodeError() | Очистить контекст |
| null == vcode | Любой | getValidVCodeForAction() | Fallback/Redisplay |

---

## 10. Логирование VCode операций

### 10.1 Обозначения в логах

```
✅ - Успех (VCode найден, валиден, использован)
⚠️  - Предупреждение (VCode не найден, истёк, но не критично)
❌ - Ошибка (критичное отсутствие VCode, невалиден)
📍 - Отслеживание (VCode извлечен, совпадение паттерна)
🎯 - Специальная логика (fight_fallback, fallback к кэшированному)
📋 - Управление (контекст очищен, инвалидирован)
```

### 10.2 Пример логов при нормальной работе

```
10:00:00.100 📍 VCODE_EXTRACTED: pattern matched, vcode=3f7c5d4e...
10:00:00.110 ✅ VCODE_PARSED: source=fight, vcode=3f7c5d4e..., version=142, ageMs=0
10:00:00.120 🎯 FIGHT_STARTED: cached vcode=3f7c5d4e..., will keep for 120 secs
10:00:01.050 ✅ VALID_VCODE: actionName=fight_fallback, vcode=3f7c5d4e..., ageMs=950ms, source=fight
10:00:02.100 ✅ VCODE_PARSED: source=fight, vcode=a1b2c3d4..., version=143, ageMs=0
10:00:02.150 ✅ VALID_VCODE: actionName=fight_fallback, vcode=a1b2c3d4..., ageMs=50ms, source=fight
10:00:10.200 🎯 FIGHT_ENDED: боевой контекст и кэш vcode очищены
```

### 10.3 Пример логов при багах

```
10:00:00.100 ⚠️ PARSE_VCODE_FAILED: no vcode found in HTML, source=fish
10:00:00.200 ⚠️ NO_SESSION: actionName=fish_act - контекст пуст
10:00:01.000 ⚠️ STALE_SESSION: actionName=nav_bootstrap, ageMs=5050ms, maxAgeMs=300000
10:00:02.100 ❌ INVALID_CODE_ERROR: actionName=fight, failingVCode=3f7c5d4e...
10:00:02.110 📋 SESSION_INVALIDATED: контекст очищен для переперезагрузки
```

---

## Заключение

VCode механизм в приложении — это **распределённая, масштабируемая система управления сеансом**, которая:

1. ✅ **Автоматически парсит** VCode из HTML (6 паттернов)
2. ✅ **Кэширует** контекст с версионированием
3. ✅ **Валидирует** перед использованием (age, timeout, nullability)
4. ✅ **Обрабатывает ошибки** (инвалидация, fallback к боевому кэшу)
5. ✅ **Thread-safe** через ReentrantReadWriteLock
6. ✅ **Логирует** все операции для отладки

Эта архитектура предотвращает большинство VCode-связанных ошибок, но требует точного соблюдения порядка вызовов (особенно markFightInProgress → LezFight) и регулярной проверки логов при новых багах.
