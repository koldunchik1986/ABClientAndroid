# Анализ MainActivity.java (~6000 строк)

## 📊 Структура файла

**Общая статистика:**
- Размер: ~6000 строк  
- Методов: ~147  
- Полей: ~60+ приватных + все константы  
- Возраст: 2026-02-27 (активная разработка)

---

## 1️⃣ СТРУКТУРА ФАЙЛА И ОБЛАСТИ ОТВЕТСТВЕННОСТИ

| Раздел | Методы | Назначение |
|--------|--------|-----------|
| **Инициализация** | `onCreate`, `onResume`, `onPause`, `onDestroy` | Управление жизненным циклом |
| **WebView Setup** | `setupWebViews`, `setupWebView`, `injectClickInterceptor` | Конфигурация WebView + JS-инъекции |
| **Авто-бой (Main)** | `requestAutoTurn`, `requestAutoTurnInternal` | Оркестрация авто-хода (702+ строк) |
| **Авто-бой (Server)** | `requestAutoTurnFromServerProbe`, `loadFightProbeHtmlViaHttp` | Server-probe для background |
| **Server Probe HTTP** | `loadFightProbeHtmlViaHttp(String)` | Низкоуровневый HTTP-запрос |
| **Боевые валидаторы** | `hasFightMarkers`, `isActiveFightContext`, `hasPendingAct7FightLink` | Проверки бой-контекста |
| **Быстрые действия** | `submitAutoBattleActionToWebView`, `adoptVCodeFromAutoSubmitPayload` | Submit авто-боя + VCode |
| **Капча (UI)** | `showCaptchaDialog`, `loadCaptchaImageAsync`, `updateFightCaptchaSubmitButtonState` | Диалог + Картинка + Refresh |
| **Капча (Уведомления)** | `showCaptchaSystemNotification`, `cancelCaptchaSystemNotification` | Android notifications |
| **Чат & Полинг** | `startChatRefresh`, `requestChatRefresh`, `onChatPollResponseMeta` | Периодический опрос чата |
| **Navigation** | `isManualMainNavigationUrl`, `extractCompassCellRegNumFromUrl` | Анализ ссылок компаса |
| **UI State** | `isUiForegroundInteractive`, `isUiForegroundLikely`, `isDeviceLocked` | Проверка состояния экрана |
| **Таймеры & Handlers** | `startTimer`, `checkServerTimerDrivenActions`, `checkConnection` | Фоновые таймеры |
| **Broadcast & Receiver** | `registerAppBroadcastReceiverIfNeeded`, `broadcastReceiver`, `screenStateReceiver` | IPC + Экран on/off |

---

## 2️⃣ ОБНАРУЖЕННЫЕ "GOD METHODS" (>500 строк)

### 🔴 **CRITICAL: requestAutoTurnInternal() [Строки 702-857]**

**Размер:** ~200 строк логики (без возвращаемого значения)  
**Сложность:** EXTREME (вложенные условия > 10 уровней)

```plaintext
requestAutoTurnInternal(boolean allowServerProbeFallback)
  ├─ evaluateJavascript() callback
  │   ├─ Проверка HTML на null/size < 1000
  │   ├─ if (hasFightMarkers current HTML)
  │   │   ├─ if (allowServerProbeFallback && !isActiveFightContext)
  │   │   │   ├─ fallback to cached
  │   │   │   └─ server-probe
  │   │   └─ result: autoTurnHtml
  │   ├─ else (no markers current)
  │   │   ├─ if (hasFightMarkers cached)
  │   │   │   ├─ if (isActiveFightContext cached)
  │   │   │   │   └─ use cached
  │   │   │   └─ else (cached stale)
  │   │   │       └─ server-probe
  │   │   └─ else (no cached markers)
  │   │       └─ server-probe
  │   └─ fightViewModel.autoTurnOnce(...)
  └─ else (html is null) → recurse to cached logic
```

**Проблемы:**
- ❌ **>4 уровня вложенности if** (Rule 6 - требует Handler)
- ❌ **3 идентичных fallback-ветки** (дублирование)  
- ❌ **Race condition при multi-enemy fight** (встроенное в метод: check на html.length() < 1000)
- ❌ **Асинхронный callback не сегментирован**

---

### 🟠 **HIGH: onChatPollResponseMeta() [Строки 578-701]**

**Размер:** ~120 строк  
**Сложность:** HIGH (retry-логика + state management)

**Проблемы:**
- ❌ Управление `consecutiveChatPollFailures`, `lastChatPollFailureAtMs`, `roomUsersRefreshSuppressedUntilMs` в одном методе
- ⚠️ Двойное логирование (Log + FileLogger) вконце хорошо, но логика излишне развернута
- ✅ Обработка таймера хорошо организована через `chatPollRecoveryRunnable`

---

### 🟠 **HIGH: submitAutoBattleActionToWebView() [Строки 1427-1520]**

**Размер:** ~90 строк  
**Сложность:** HIGH (сложный JS-payload + retry)

**Проблемы:**
- ❌ Встроенный JS-скрипт (очень длинный) - нужна вынесение в HtmlUtils или аналог
- ❌ Retry-логика зависит от `retriesLeft` - предпочтительнее Handler с backoff
- ✅ VCode-adoption хорошо размещен (`adoptVCodeFromAutoSubmitPayload`)

---

### 🟡 **MEDIUM: handlePostMainPhpResponse() [Строки 4442-4524]**

**Размер:** ~80 строк  
**Сложность:** MEDIUM-HIGH (анализ POST-ответа)

**Проблемы:**
- ❌ Множество проверок состояния в одном методе
- ⚠️ Использует `AppVars.IsFightCaptchaDialogVisible` как флаг состояния - хорошо, но смешано с другой логикой

---

## 3️⃣ ДУБЛИРУЮЩИЙСЯ КОД

### Паттерн #1: Проверка binding/view nullability (3+ раз)
**Строки:** 297-310, 3644-3656, 4416, многие другие  
**Пример:**
```java
if (binding == null || binding.appBarMain == null || binding.appBarMain.contentMain == null) {
    return/skip;
}
```
**Рекомендация:** Создать утилитный метод `isMainBindingReady()` или `requireMainWebView()`

### Паттерн #2: Fallback-логика к cached HTML (3 раза в requestAutoTurnInternal)
**Строки:** 800-820, 823-835, 838-845
**Код повторяется:** проверка markers → проверка active → использование cached или probe
**Рекомендация:** Вынести в `FightContextValidator` Handler

### Паттерн #3: Проверка URLs на Manual Navigation (repeat)
**Строки:** 927-962  
**Проблема:** Множество `.contains()` для анализа URL  
**Рекомендация:** Расширение паттерна или создание URL-классификатора

### Паттерн #4: Retry-задержки (3+ раза)
**Строки:** 1483, chatPollRecoveryRunnable, другие  
**Рекомендация:** Общий RetryBackoffManager

---

## 4️⃣ VCode ИСПОЛЬЗОВАНИЕ (CRITICAL - Rule 5)

### ❌ **AppVars.VCode используется НАПРЯМУЮ в 5 МЕСТАХ:**

1. **Строка 1526-1527: adoptVCodeFromAutoSubmitPayload()**
   ```java
   if (!vcode.equals(AppVars.VCode)) {
       AppVars.VCode = vcode;
   }
   ```
   **Проблема:** Перезаписывает VCode напрямую - нарушает Rule 5

2. **Строка 3849-3850: checkServerTimerDrivenActions()**  
   ```java
   if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
       reloadUrl += "&vcode=" + AppVars.VCode;
   }
   ```
   **Проблема:** Используется для reload main.php - нужен fallback

3. **Строка 3943, 3948: checkServerTimerDrivenActions() (repeat)**
   ```java
   else if (AppVars.VCode == null || AppVars.VCode.isEmpty()) {
       reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&af_tick=1&r=" + now 
                   + "&vcode=" + AppVars.VCode;
   }
   ```
   **Проблема:** Дублирование + использование null VCode

4. **Строка 4604-4606: schedulePostResponseReload()**
   ```java
   if (ru.neverlands.abclient.utils.AppVars.VCode != null
       && !ru.neverlands.abclient.utils.AppVars.VCode.isEmpty()) {
       reloadUrl += "&vcode=" + ru.neverlands.abclient.utils.AppVars.VCode;
   }
   ```
   **Проблема:** Повторное использование после adoption

### ✅ **Хорошо:** Логирование в adoptVCodeFromAutoSubmitPayload() с prefix [BG_TRACE]

### 🔧 **Требуется миграция на SessionManager:**
- Все 5 мест должны использовать `SessionManager.getInstance().getValidVCodeForAction("action_name")`
- Добавить fallback: если VCode == null, не отправлять, а закэшировать для retry

---

## 5️⃣ БЛОКИРУЮЩИЕ ОПЕРАЦИИ И СИНХРОНИЗАЦИЯ

### 🟢 **Хорошие примеры:**
- ✅ **autoTurnServerProbeLock** (line 218) - synchronized, предотвращает параллельные probe
- ✅ **autoTurnServerProbeInFlight** volatile flag
- ✅ **ManualNavigation suppress window** - не блокирует, a отлагает

### ❌ **Проблемы синхронизации:**

1. **Нет гвардов для параллельных submitAutoBattleActionToWebView() вызовов**
   - Если два autoTurn-тика придут одновременно, оба отправят submit
   - **Рекомендация:** Добавить флаг `autoBattleSubmitInFlight` + synchronized блок

2. **Race condition: hasFightMarkers + isActiveFightContext = 2 отдельных парсинга**
   - Между проверкой `hasFightMarkers()` и `isActiveFightContext()` может придти новый HTML
   - **Рекомендация:** Парсить один раз, кэшировать результаты обеих проверок

3. **requestAutoTurnFromServerProbe() в отдельном потоке без синхронизации с WebView-потоком**
   - `autoTurnServerProbeInFlight` хорошо, но результат обновляет `AppVars.ContentMainPhp` без guard
   - **Рекомендация:** Добавить версионирование данных (epoch/generation count)

---

## 6️⃣ ЦЕПОЧКИ ПРОВЕРОК (>3 условий для Handler)

### Цепочка #1: **Валидация бой-контекста перед submit** [Строки 702-857]
```
┌─ Проверка Captcha видимости
├─ Проверка FirstFrameRender defer
├─ evaluateJavascript() 
│  ├─ null/сорт check
│  ├─ if hasFightMarkers(current)
│  │  ├─ if isActiveFightContext(current)
│  │  └─ else
│  │     ├─ check cached
│  │     └─ server-probe
│  └─ if !hasFightMarkers(current)
│     ├─ check cached
│     └─ server-probe
└─ autoTurnOnce(html)
```
**Сложность:** 5+ уровней вложенности  
**Кандидат для Handler:** `FightContextChoiceHandler`

### Цепочка #2: **Chat Poll Recovery** [Строки 578-701]  
```
┌─ Проверка Main Thread
├─ Определение pollFailed (code >= 535 || bytes <= 0)
├─ if !pollFailed
│  ├─ Reset counters
│  └─ Cancel recovery runnable
└─ else
   ├─ if not dedup-blocked
   ├─ Update counters
   ├─ Check AutoBoss flag
   ├─ Schedule recovery runnable
   └─ Log warnings
```
**Сложность:** 4+ уровней  
**Кандидат для Handler:** `ChatPollRecoveryHandler`

### Цепочка #3: **Manual Navigation Suppression** [Строки 927-1051]
```
┌─ Parse URL
├─ if (isManualMainNavigationUrl)
├─ suppressAutoTurnServerProbeForManualNavigation()
│  ├─ Calculate suppress window
│  └─ Max with existing suppress
└─ Request deferred auto-turn
```
**Сложность:** 4+ уровней  
**Кандидат для Handler:** `ManualNavigationGuardHandler`

---

## 7️⃣ АНАЛИЗ ЛОГИРОВАНИЯ

### ✅ **Хорошее логирование:**
- Двойное: `Log.d()` + `FileLogger.trace()` в критичных функциях
- Префиксы: `[BG_TRACE]`, `[AA_TRACE]`, `[HANDLER_NAME]`
- Примеры: adoptVCodeFromAutoSubmitPayload, requestAutoTurn, chat-poll

### ❌ **Паттерны логирования, которые можно улучшить:**
- Слишком много Log.d() для debug-информации (можно было бы отключать в production)
- Нет структурированного логирования (не используются теги по типам: TIMING, STATE, ERROR*)
- Отсутствует логирование начала асинхронного блока (requestAutoTurnFromServerProbe)

---

## 8️⃣ ОБЩИЕ МЕТРИКИ

| Метрика | Значение | Оценка |
|---------|----------|--------|
| Средний размер метода | ~40 строк | 🟡 HIGH (вкл. 200+ строк callback'ов) |
| Нестинг условий | 5-10 уровней | 🔴 CRITICAL |
| Количество полей | 60+ | 🟡 HIGH (нужне структурирование) |
| Использование try-catch | ~30% методов | 🟢 OK |
| Параллельные операции | 3+ | 🟡 MEDIUM (нужны гварды) |
| Миграция на SessionManager | 0% | 🔴 CRITICAL |

---

## 9️⃣ КРИТИЧНОСТЬ И РЕКОМЕНДАЦИИ

### 🔴 **CRITICAL (немедленно):**

1. **VCode Migration (Rule 5)**
   - ✅ Все 5 мест `AppVars.VCode` → `SessionManager.getInstance().getValidVCodeForAction()`
   - 📌 Скорость: ~2-3 часа на полный рефакторинг
   - 📌 Сложность: MEDIUM (требует тестирования)

2. **requestAutoTurnInternal() разрезание**
   - ✅ Вынести fallback-логику в `FightContextChoiceHandler`
   - ✅ Вынести race-condition guard в отдельный метод
   - 📌 Скорость: ~4-5 часов
   - 📌 Сложность: HARD (требует понимания всей цепочки авто-боя)

### 🟠 **HIGH (в текущем спринте):**

3. **Создать утилитный метод для binding-проверок**
   - ✅ `isMainBindingReady()`, `getMainWebViewOrNull()`
   - 📌 Скорость: ~1 час
   - 📌 Сложность: EASY

4. **Дублирующие fallback-ветки в requestAutoTurnInternal**
   - ✅ Вынести в `FightContextFallbackHandler`
   - 📌 Скорость: ~3 часа
   - 📌 Сложность: MEDIUM

### 🟡 **MEDIUM (планировать на следующий спринт):**

5. **submitAutoBattleActionToWebView() оптимизация**
   - ✅ Вынести JS-скрипт в HtmlUtils или ResourceManager
   - ✅ Добавить backoff-retry вместо простого incrementing
   - 📌 Скорость: ~2-3 часа
   - 📌 Сложность: MEDIUM

6. **Структурировать поля Activity в блоки**
   - ✅ Создать внутренние классы для поля-группы (ChatStateHolder, FightStateHolder и т.д.)
   - 📌 Скорость: ~3-4 часа
   - 📌 Сложность: MEDIUM

---

## 🔟 ИТОГОВАЯ ОЦЕНКА

| Параметр | Оценка |
|----------|--------|
| **Критичность** | 🔴 CRITICAL |
| **Сложность рефакторинга** | 🔴 HARD |
| **Приоритет** | 1️⃣ Первый модуль для рефакторинга |
| **Общее время на реабилитацию** | ~10-15 часов (при параллельной работе) |
| **Риск регрессии** | 🟡 HIGH (много асинхроно, нужны тесты) |

---

## 📝 РЕКОМЕНДУЕМЫЙ ПОРЯДОК РАБОТ

### Этап 1: VCode Migration (CRITICAL)
1. ✅ Создать `SessionManagerVCodeAdapter` для обертывания `SessionManager.getInstance()` 
2. ✅ Миграция: adoptVCodeFromAutoSubmitPayload → SessionManager
3. ✅ Миграция: checkServerTimerDrivenActions → SessionManager
4. ✅ Миграция: schedulePostResponseReload → SessionManager
5. ✅ Тестирование: все AJAX-запросы должны идти с валидным VCode

### Этап 2: Разбиение God Methods
1. ✅ Создать `FightContextChoiceHandler` для requestAutoTurnInternal fallback-логики
2. ✅ Создать `ChatPollRecoveryHandler` для retry-логики чата
3. ✅ Рефакторинг requestAutoTurnInternal с использованием Handler'ов

### Этап 3: Структурирование полей
1. ✅ Crear FightStateHolder (поля fight-related)
2. ✅ Создать ChatStateHolder (поля chat-related)
3. ✅ Создать CaptchaStateHolder (поля captcha-related)

---

**Дата анализа:** 2026-04-03  
**Версия:** 1.0  
**Статус:** Ready for Implementation
