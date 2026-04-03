# Анализ использования AppVars: VCode и миграция на SessionManager

## 1. Статистика AppVars.java

### Общий count переменных: ~85 переменных

**Категории переменных:**
- **Состояние боя:** 15+ переменных (LastBoiLog, LastBoiTimer, CurrentAutoBattleHitDelaySec и т.д.)
- **Авто-рыбалка:** 18+ переменных (FishCurrentVcode, ContentLakeHtml, AutoFishHand1, AutoFishLikeId и т.д.)
- **Авто-движение:** 10+ переменных (AutoMoving, DoSearchBox, AutoMovingDestinaton и т.д.)
- **Fast Actions:** 12+ переменных (FastNeed, FastId, WaitOpen, DoPerenap и т.д.)
- **VCode-related:** 8+ переменных (VCode, FishCurrentVcode, ContentLakeHtml и т.д.)
- **Состояние паузы:** 10+ переменных (FastPauseNonCombatAutoFunctions, TimerPauseNonCombatAutoFunctions и т.д.)
- **Прочее:** 15+ переменных (Profile, Autoboi, AutoAttackToolId и т.д.)

---

## 2. Критичные VCode-related переменные

### 🔴 ГЛАВНЫЕ переменные для миграции:

| Переменная | Тип | Статус | Где используется | Приоритет |
|------------|-----|--------|-----------------|-----------|
| **`VCode`** | `String` | ❌ DIRECT USAGE | TreasureDig, FastActionManager, AutoFunctionsManager, MainActivity | **CRITICAL** |
| **`FishCurrentVcode`** | `volatile String` | ⚠️ LEGACY + DIRECT | WebViewRequestInterceptor, FishAjaxPhp | **CRITICAL** |
| **`ContentLakeHtml`** | `String` | ⚠️ LAKE CACHE | MainPhp, FishAjaxPhp, MainActivity | **HIGH** |
| **`ContentLakeHtmlLastUpdateAtMs`** | `volatile long` | ⚠️ TIMESTAMP | FishAjaxPhp, MainActivity | **HIGH** |

### 🟡 ВСПОМОГАТЕЛЬНЫЕ переменные для боевой капчи:

| Переменная | Тип | Назначение | Файлы |
|------------|-----|-----------|-------|
| **`LastSubmittedFightCaptchaFinishKey`** | `volatile String` | TTL анти-дубль боевой капчи | MainActivity, AutoModeForegroundService, FightAuto |
| **`LastSubmittedFightCaptchaAtMs`** | `volatile long` | Timestamp последней отправки | MainActivity |
| **`ResumeAutoboiAfterCaptcha`** | `volatile boolean` | Флаг восстановления авто-боя | MainPhp, MainActivity, FightAuto, AutoModeForegroundService |
| **`ResumeSearchBoxAfterCaptcha`** | `volatile boolean` | Флаг восстановления авто-клада | MainPhp, MainActivity, FightAuto, AutoModeForegroundService |

### 🟢 ВСПОМОГАТЕЛЬНЫЕ переменные для капчи (уже в SessionManager):

| Переменная | Тип | Назначение |
|------------|-----|-----------|
| `IsFightCaptchaDialogVisible` | `volatile boolean` | Видим ли диалог капчи |
| `LastFightCaptchaImageUrl` | `volatile String` | URL последней капчи |
| `LastFightCaptchaImageAtMs` | `volatile long` | Когда увидели капчу |
| `LastFightCaptchaImageBytes` | `volatile byte[]` | PNG байты капчи |
| `FightLink` | `String` | URL боевой капчи |
| `CodeAddress` | `String` | URL-картинка капчи |

---

## 3. Нужна ли миграция для КАЖДОЙ переменной?

### ✅ МИГРИРОВАТЬ на SessionManager:

```
1. ✅ AppVars.VCode
   - Никогда не сохранять как буфер
   - Всегда использовать: SessionManager.getInstance().getValidVCodeForAction("action_name")
   - Время жизни: 30 сек (обычные), 120 сек (бой)
   - Текущих использований: TreasureDig, FastActionManager, AutoFunctionsManager, MainActivity (5 файлов)

2. ✅ AppVars.FishCurrentVcode
   - LEGACY переменная - уже запрещена (см. комментарий в коде)
   - Заменять на: SessionManager.getInstance().getValidVCodeForAction("fish_act1")
   - Текущих использований: 0 (уже не используется, но существует!)

3. ✅ AppVars.ContentLakeHtml
   - Это КЭШИРОВАНИЕ озера (не VCode)
   - Можно оставить в AppVars (это просто HTML-кэш)
   - ИЛИ мигрировать в отдельный FishingLakeCache в SessionManager
   - Текущих использований: MainPhp, FishAjaxPhp, MainActivity (3 файла)

4. ✅ AppVars.LastSubmittedFightCaptchaFinishKey
   - TTL анти-дубль боевой капчи
   - Можно оставить в AppVars (это состояние, не VCode)
   - Текущих использований: MainActivity, AutoModeForegroundService, FightAuto (3 файла)

5. ✅ AppVars.ResumeAutoboiAfterCaptcha
   - Флаг восстановления боя после капчи
   - Можно оставить в AppVars (это состояние, не VCode)
   - Текущих использований: MainPhp, MainActivity, FightAuto, AutoModeForegroundService (4 файла)

6. ✅ AppVars.ResumeSearchBoxAfterCaptcha
   - Флаг восстановления auto-клада после капчи
   - Можно оставить в AppVars (это состояние, не VCode)
   - Текущих использований: MainPhp, MainActivity, FightAuto, AutoModeForegroundService (4 файла)
```

### ❌ НЕ МИГРИРОВАТЬ:

Все остальные переменные в AppVars - это состояние и не требуют миграции на SessionManager. SessionManager работает только с VCode.

---

## 4. Файлы с ПРЯМЫМИ обращениями к AppVars.VCode*

### 🔴 КРИТИЧНЫЕ файлы (5 файлов):

```
1. [TreasureDig.java](TreasureDig.java#L404)
   Строки: 404-405
   Код: if (AppVars.VCode != null && !AppVars.VCode.trim().isEmpty()) { 
        link += "&vcode=" + AppVars.VCode.trim();
   Тип: Fast action - откапываемые клада
   Решение: Миграция на SessionManager.getValidVCodeForAction("treasure_dig")

2. [FastActionManager.java](FastActionManager.java#L2217)
   Строки: 2217-2218
   Код: if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
        url += "&vcode=" + AppVars.VCode;
   Тип: Fast actions (быстрые удары, умения)
   Решение: Миграция на SessionManager.getValidVCodeForAction("fast_action")

3. [AutoFunctionsManager.java](AutoFunctionsManager.java#L759)
   Строк: 759-760
   Код: if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
        reloadUrl += "&vcode=" + AppVars.VCode;
   Тип: Recovery after error - восстановление после ошибок
   Решение: Миграция на SessionManager.getValidVCodeForAction("main_php_reload")

4. [AutoFunctionsManager.java](AutoFunctionsManager.java#L1909)
   Строк: 1909
   Код: String vcode = AppVars.VCode != null ? AppVars.VCode.trim() : "";
   Тип: LogOff - выход из игры
   Решение: Миграция на SessionManager.getValidVCodeForAction("logoff")

5. [MainActivity.java](MainActivity.java#L1526)
   Строк: 1526-1527
   Код: if (!vcode.equals(AppVars.VCode)) { AppVars.VCode = vcode;
   Тип: Auto hit payload sync - синхронизация VCode из авто-удара
   Решение: ОСТАВИТЬ (это единственное легитимное место обновления - из payload'а)
```

### 🟡 ВСПОМОГАТЕЛЬНЫЕ файлы (использование вспомогательных переменных):

```
[MainActivity.java]
- LastSubmittedFightCaptchaFinishKey (5 строк)
- ResumeAutoboiAfterCaptcha (7 строк)
- ResumeSearchBoxAfterCaptcha (4 строки)
- ContentLakeHtml (4 строки, управление кэшем)

[FightAuto.java]
- LastSubmittedFightCaptchaFinishKey (2 строки)
- ResumeAutoboiAfterCaptcha (2 строки)
- ResumeSearchBoxAfterCaptcha (2 строки)

[AutoModeForegroundService.java]
- LastSubmittedFightCaptchaFinishKey (3 строки)
- ResumeAutoboiAfterCaptcha (1 строка)

[FishAjaxPhp.java]
- ContentLakeHtml (7 строк, управление кэшем)
- ContentLakeHtmlLastUpdateAtMs (4 строки, управление тайм-ауто)

[MainPhp.java]
- ResumeAutoboiAfterCaptcha (1 строка)
- ContentLakeHtml (4 строки, кэш озера)
- ContentLakeHtmlLastUpdateAtMs (2 строки)
```

---

## 5. План миграции на SessionManager (Priority Stages)

### ⚡ STAGE 1 (CRITICAL - Немедленно):

**1. AppVars.VCode → SessionManager**
   - Файлы для фиксации: TreasureDig.java, FastActionManager.java (всего 2)
   - Действие: Заменить `AppVars.VCode` на `SessionManager.getInstance().getValidVCodeForAction("action_name")`
   - Fallback: Если null - reload main.php или skip действия
   - Проверка: grep -r "AppVars\.VCode" app/src/main/java/ должен вернуть только MainActivity (sync из payload)

**2. AutoFunctionsManager.java (два места)**
   - Строки 759-760: recovery-reload → SessionManager
   - Строка 1909: logoff → SessionManager

### 📌 STAGE 2 (HIGH - После Stage 1):

**1. Очистить AppVars от legacyи:**
   - ✅ `AppVars.FishCurrentVcode` - удалить (DEPRECATED)
   - Проверка использований: grep -r "FishCurrentVcode" app/ (должно быть только в AppVars.java и комментариях)

**2. Оптимизировать ContentLakeHtml:**
   - Опция 1: Оставить в AppVars (просто HTML-кэш, не VCode)
   - Опция 2: Мигрировать в SessionManager как временный контекст озера
   - Решение: Разработчик выберет (оставить проще, если нет race conditions)

### 🎯 STAGE 3 (MEDIUM - After verification):

**1. Обогатить SessionManager:**
   - Добавить методы:
     - `SessionManager.getValidVCodeForAction("treasure_dig")`
     - `SessionManager.getValidVCodeForAction("fast_action")`
     - `SessionManager.getValidVCodeForAction("main_php_reload")`
     - `SessionManager.getValidVCodeForAction("logoff")`
   - Это уже частично реализовано в SessionManager

**2. Логирование:**
   - Все `getValidVCodeForAction()` вызовы должны логировать:
     - `✅ VCODE_OBTAINED: action=X, ageMs=Y`
     - `⚠️ VCODE_EXPIRED: action=X, ageMs=Y (timeout=Z)`
     - `❌ VCODE_MISSING: action=X, using fallback`

---

## 6. Архитектура SessionManager (из правил CLAUDE.MD правило 5)

### Компоненты:

```
SessionManager
├── SessionContext (неизменяемая сущность с версионированием)
│   ├── vcode: String
│   ├── parsedAtMs: long
│   ├── ageMs(): long
│   ├── isValidFor(timeoutSec): boolean
│   └── source: String (где парсирован: "html_response", "payment_module" и т.д.)
├── parsingMechanics
│   ├── WebViewRequestInterceptor (перехват HTML ответов)
│   ├── parseVCodeFromHtml(html): String
│   └── parseVCodeFromJson(json): String
├── cacheManagement
│   ├── getValidVCodeForAction(action): String (с timeout'om)
│   ├── markFightInProgress() (120 сек timeout вместо 30)
│   ├── clearFightContext() (при завершении боя)
│   └── ageMs(): long (время с парсинга)
└── threadSafety
    └── ReentrantReadWriteLock (для параллельных потоков)
```

### Жизненный цикл VCode:

```
1. HTML-ответ сервера → WebViewRequestInterceptor
2. parseVCodeFromHtml() → новый SessionContext
3. кэширование: context.parsedAtMs = now()
4. При запросе: getValidVCodeForAction(action)
   - Проверка: context.ageMs() < timeout(action)
   - Если валидна: вернуть vcode
   - Если невалидна: reload main.php и вернуть null
5. При смене PHPSESSID: clearFightContext()
6. При завершении боя: clearFightContext()
```

---

## 7. Примеры кода миграции

### БЫЛО (TreasureDig.java):
```java
if (AppVars.VCode != null && !AppVars.VCode.trim().isEmpty()) {
    link += "&vcode=" + AppVars.VCode.trim();
}
```

### СТАЛО (SessionManager):
```java
import ru.neverlands.abclient.utils.SessionManager;

String vcode = SessionManager.getInstance().getValidVCodeForAction("treasure_dig");
if (vcode != null && !vcode.isEmpty()) {
    link += "&vcode=" + vcode;
} else {
    // Fallback: reload main.php
    FileLogger.trace("TreasureDig: VCode expired, reloading main.php");
    MainActivity.getInstance().loadUrl("main.php");
    return; // skip операцию
}
```

### БЫЛО (FastActionManager.java):
```java
if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
    url += "&vcode=" + AppVars.VCode;
}
```

### СТАЛО (SessionManager):
```java
import ru.neverlands.abclient.utils.SessionManager;

String vcode = SessionManager.getInstance().getValidVCodeForAction("fast_action");
if (vcode != null && !vcode.isEmpty()) {
    url += "&vcode=" + vcode;
    FileLogger.trace("FastAction: VCode obtained, ageMs=" + 
        SessionManager.getInstance().getVCodeAgeMs());
} else {
    Log.w(TAG, "❌ VCODE_MISSING: fast_action, skipping or using fallback");
    FileLogger.trace("FastAction: VCode expired, retry after delay");
    // Опция: retry после 500ms, или skip действия
}
```

---

## 8. Чек-лист перед сдачей

### ✅ Проверить:

- [ ] Все 5 файлов с ПРЯМЫМИ обращениями к `AppVars.VCode` мигрированы на SessionManager
  - [ ] TreasureDig.java (2 строки)
  - [ ] FastActionManager.java (2 строки)
  - [ ] AutoFunctionsManager.java (2 места: 759, 1909)
  - [ ] MainActivity.java (ОСТАВИТЬ: 1526-1527, это sync из payload)
  
- [ ] Каждый `SessionManager.getValidVCodeForAction()` имеет fallback
  - [ ] Проверка на null
  - [ ] Лог при отказе: FileLogger.trace() + Log.w()
  
- [ ] UTF-8 кодировка всех файлов (AGENTS.MD Rule 0)
  
- [ ] Логирование дуальное - Log.i()/e() + FileLogger.trace() (Rule 6)
  
- [ ] Grep проверка:
  ```powershell
  grep -r "AppVars\.VCode" app/src/main/java/ 
  # Должны остаться только:
  # 1. AppVars.java (определение)
  # 2. MainActivity.java:1526-1527 (sync из payload)
  # 3. Комментарии в SessionManager (объяснение архитектуры)
  ```

- [ ] Сборка: `. \gradlew clean assembleDebug 2>&1` - должна успеть

- [ ] Логи при выполнении:
  ```
  ✅ VCODE_OBTAINED: action=fast_attack, ageMs=324
  ⚠️ VCODE_EXPIRED: action=treasure_dig, ageMs=35000 (timeout=30000)
  ❌ VCODE_MISSING: action=logoff, using fallback (reload main.php)
  ```

---

## Summary

| Метрика | Значение |
|---------|----------|
| **Всего переменных в AppVars** | ~85 |
| **VCode-related переменных** | 8 |
| **Переменных для миграции на SessionManager** | 1 (VCode) + опция (FishCurrentVcode - deprecated) |
| **Файлов с КРИТИЧНЫМИ обращениями к AppVars.VCode** | 5 (TreasureDig, FastActionManager x2, AutoFunctionsManager x2, MainActivity - sync) |
| **Файлов со ВСПОМОГАТЕЛЬНЫМИ обращениями** | 4 (MainActivity, FightAuto, AutoModeForegroundService, FishAjaxPhp) |
| **Приоритет 1 (немедленно)** | TreasureDig.java, FastActionManager.java (2 места), AutoFunctionsManager.java (2 места) |
| **Приоритет 2 (после Stage 1)** | Удаление FishCurrentVcode (deprecated), оптимизация ContentLakeHtml |
| **Приоритет 3 (документация)** | Добавить методы в SessionManager для всех типов действий |
