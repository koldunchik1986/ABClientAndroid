# ПОЛНЫЙ АУДИТ ЦИКЛА АВТО-РЫБАЛКИ - РЕЗУЛЬТАТЫ

**Дата**: 01.04.2026  
**Статус**: 🔴 КРИТИЧЕСКАЯ АРХИТЕКТУРНАЯ ПРОБЛЕМА НАЙДЕНА  
**Приоритет**: 🚨 **ВЫСОКИЙ** — Основная причина "неверный код защиты"

---

## РЕЗЮМЕ 

### Главная Проблема в Одной Строке
**ПК-версия**: Каждый цикл загружает озеро (main.php?get_id=55) и парсит vcode из него  
**Android-версия**: Пытается загрузить vcode из fish_ajax.php?act=1 (неправильный источник)

---

## 1. ТОЧКА ВХОДА В ЦИКЛ АВТО-РЫБАЛКИ

### Где все начинается

#### 1.1 Маршрутизация (Filter.java, line 170)
```java
if (address.startsWith("http://neverlands.ru/gameplay/ajax/fish_ajax.php")) {
    return FishAjaxPhp.process(address, array);  // ← ВСЕ fish_ajax запросы идут сюда
}
```

#### 1.2 Обработка fish_ajax ответов (FishAjaxPhp.java)

**Точка входа**: `FishAjaxPhp.process(String address, byte[] array)` (line 100)

```
Входящий запрос:
  ├─ fish_ajax.php?act=1  → processFishAct1()
  └─ fish_ajax.php?act=2  → syncFishCooldownAndScheduleNextCycle()
```

#### 1.3 Инициирование цикла (FishAjaxPhp.java, line 426)
```java
lastFishAutoreloadAtMs = nowMs;
lastFishAutoreloadDueAtMs = effectiveDueAtMs;
lastFishCycleToken = effectiveDueAtMs;

// ... затем
activity.runOnUiThread(() -> webView.postDelayed(
    () -> kickFishCycleAttempt(cycleToken, 1),  // ← ИНИЦИИРОВАНИЕ ЦИКЛА
    delayMs));
```

### 🔴 ПРОБЛЕМА 1: Где первоначально запускается рыбалка?

При первом включении авто-рыбалки в UI нет явного вызова `kickFishCycleAttempt()`.  
**Неизвестно**: Как осуществляется ПЕРВЫЙ старт цикла?

**Поиск в코де**:
- ❓ AutoFunctionsManager — управляет включением/выключением
- ❓ MainActivity — нет явного вызова рыбалки при старте
- ❓ AutoModeForegroundService — может его быть там?

---

## 2. ТЕКУЩИЙ FLOW В MainPhp.java И FishAjaxPhp.java

### 2.1 Обработка ответа act=1 (FishAjaxPhp, line 180-260)

**Что происходит**:
```java
private static void processFishAct1(String address, String html) {
    if (!isAutoFishEnabled()) return;
    
    // Парсим payload из act=1 response
    FishAct1State state = parseFishAct1State(html);
    
    // Сохраняем vcode ИЗ ACT=1 (🔴 ЭТОТ vcode ИЗ НЕПРАВИЛЬНОГО ИСТОЧНИКА!)
    AppVars.FishCurrentVcode = state.vcode;  // ← 🔴 ПРОБЛЕМА В ЭТОЙ СТРОКЕ
    
    // Выбираем приманку
    FishBaitSelection selection = selectAllowedBait(state.baits);
    
    // Если есть капча
    if (captchaRequired) {
        showFishCaptchaDialogOnce(captchaUrl, submitUrl);
    } else {
        // Без капчи отправляем act=2
        scheduleNoCaptchaAct2Fallback(state.vcode, selection.id, lastFishAct1AtMs);
    }
}
```

### 🔴 ПРОБЛЕМА 2: Источник vcode НЕПРАВИЛЬНЫЙ

**Где берется vcode сейчас**:
1. ✅ `fish_ajax.php?act=1` response (технический payload)
   - Формат: `RESO@...@[1,"captcha","VCODE_ЗДЕСЬ",...]`
   - **ПРОБЛЕМА**: Этот vcode может быть УСТАРЕВШИМ или от ОЗЕРА
   - **ПРОБЛЕМА**: act=1 это ЗОНД, не основной запрос

**Где должен браться vcode** (как в ПК-версии):
2. ❌ `main.php?get_id=55` (озеро с формой выбора приманки)
   - Форма содержит скрытое поле `<input name=vcode value="FRESH_VCODE">`
   - **ЭТО** - единственный правильный источник vcode
   - Гарантирует что vcode ВСЕГДА свежий перед действием

### 2.2 Обработка ответа act=2 (FishAjaxPhp, line 379-430)

```java
private static void syncFishCooldownAndScheduleNextCycle(String html) {
    // Извлекаем cooldown
    int cooldownSec = extractFishCooldownSec(html);
    
    // Планируем СЛЕДУЮЩИЙ цикл через cooldown
    // ← но БЕЗ ЗАГРУЗКИ озера перед следующим act=1!
    
    activity.runOnUiThread(() -> webView.postDelayed(
        () -> kickFishCycleAttempt(cycleToken, 1),
        delayMs));
}
```

### 🔴 ПРОБЛЕМА 3: ОТСУТСТВУЕТ loadFreshLakeHtml()

**Должно быть** (как в FISHING_IMPLEMENTATION_GUIDE.md):
```
Цикл вызов:
  1. kickFishCycleAttempt()
     ↓
  2. ★ loadFreshLakeHtml() ← 🔴 ЭТОГО НЕТ!
     (GET main.php?get_id=55)
     ↓
  3. mainPhpAutoFishPrepareAndroid()
     (парсим озеро, извлекаем СВЕЖИЙ vcode)
     ↓
  4. Отправляем действие с ПРАВИЛЬНЫМ vcode
```

**Что есть сейчас** (неправильно):
```
kickFishCycleAttempt()
  ↓
Отправляем JS-вызов FishStart() или fishbutton
  ↓ (неявно это должно загрузить озеро)
processFishAct1()  ← ЗОНД, не основной запрос
  ↓
Извлекаем СТАРЫЙ vcode из act=1
```

---

## 3. КРИТИЧЕСКИЕ ПРОПУСКИ В КОДЕ

### 3.1 Функция `loadFreshLakeHtmlForFishing()` - **ПОЛНОСТЬЮ ОТСУТСТВУЕТ**

Статус: 🔴 **НЕ РЕАЛИЗОВАНА**

Должна делать:
```java
private static String loadFreshLakeHtmlForFishing() {
    // Загрузить GET /main.php?get_id=55
    // Возвратить HTML озера с формой и скрытыми полями
}
```

Текущее решение (из FISHING_IMPLEMENTATION_GUIDE):
```
// ОПЦИЯ: кэширование озера при загрузке
AppVars.Profile.CachedLakeHtml = html;  // ← Не реализовано в MainPhp
AppVars.LastLakeHtmlCacheTimeMs = System.currentTimeMillis();
```

### 3.2 Функция `mainPhpAutoFishPrepareAndroid()` - ПЕРЕИМЕНОВАНА, НО ЛОГИКА ПОХОЖА

**Статус**: ⚠️ **ЧАСТИЧНО РЕАЛИЗОВАНА**

**Текущее имя**: `mainPhpAutoFishPrepare()` (line 3380 в MainPhp.java)

**Что делает**:
```java
private static String mainPhpAutoFishPrepare(String html) {
    String getid = extractInputValue(html, "getid");      // ← от озера
    String act = extractInputValue(html, "act");          // ← от озера
    String vcode = extractInputValue(html, "vcode");     // ← ОТ ОЗЕРА (правильно!)
    String lakeid = extractInputValue(html, "lakeid");   // ← от озера
    
    // ... выбор приманки, сборка URL действия
    
    String link = "main.php?get_id=55&act=4&primid=" 
                + primid + "&lakeid=" + lakeid
                + "&vcode=" + vcode;
    return link;
}
```

⚠️ **问題**: Эта функция вызывается только для озера (`get_id=55`), не в цикле рыбалки

### 3.3 Функция `stopScheduleNextFishingCycle()` - **ОТКУДА-ТО ВЫЗЫВАЕТСЯ**

Поиск: `stopScheduleNextFishingCycle` → **НЕ НАЙДЕНА**

**Вероятно**, имеется в виду:
- Очистка `lastFishCycleToken` 
- Отмена отложенных вызовов `kickFishCycleAttempt()`

**Реализация**: ❌ **НЕ НАЙДЕНА**, нельзя явно остановить цикл

### 3.4 Fallback логика при act=1 без озера - **ЭКСПЕРИМЕНТАЛЬНАЯ**

```java
// FishAjaxPhp.java, line 268
private static void scheduleNoCaptchaAct2Fallback(String vcode, String primid, long act1AtMs) {
    // Если act=1 не требует капчи, отправляем act=2 через JS
    webView.evaluateJavascript(js, value -> {
        if (jsOk) return;  // JS сработал
        
        // Fallback: POST запрос фоном (БЕЗ озера, БЕЗ свежего vcode!)
        String url = "http://neverlands.ru/gameplay/ajax/fish_ajax.php?act=2"
                + "&primid=" + safePrimid
                + "&vcode=" + safeVcode;  // ← СТАРЫЙ vcode!
        webView.loadUrl(url);
    });
}
```

🔴 **ПРОБЛЕМА**: Если JS не отработал, vcode может быть ИНВАЛИДНЫМ

---

## 4. СРАВНЕНИЕ С FISHING_IMPLEMENTATION_GUIDE.md

### Таблица реализации

| Функция | В Guide | В коде | Статус |
|---------|--------|--------|--------|
| **executeFishingCycleCore()** | ✅ Есть | ❌ НЕТ | 🔴 ОТСУТСТВУЕТ |
| **loadFreshLakeHtmlForFishing()** | ✅ Есть | ❌ НЕТ | 🔴 ОТСУТСТВУЕТ |
| **mainPhpAutoFishPrepareAndroid()** | ✅ Есть | ⚠️ Частично (mainPhpAutoFishPrepare) | ⚠️ НЕПРАВИЛЬНОЕ ПРИМЕНЕНИЕ |
| **selectBaitFromLakeHtmlAndroid()** | ✅ Есть с остатком | ⚠️ selectAllowedBait() но из act=1 | ⚠️ НЕПРАВИЛЬНЫЙ ИСТОЧНИК |
| **kickFishCycleAttempt()** | ✅ Есть | ✅ Есть | ✅ OK |
| **syncFishCooldownAndScheduleNextCycle()** | ✅ Есть | ✅ Есть | ✅ OK |

---

## 5. ГДЕ vcode ПОЛУЧАЕТ НЕПРАВИЛЬНОЕ ЗНАЧЕНИЕ

### 5.1 Отследить цепочку vcode

```
1. Первый старт авто-рыбалки
   ↓
2. kickFishCycleAttempt() вызывает JS для открытия озера
   (FishStart() или fishbutton click)
   ↓
3. Озеро открывается (main.php?get_id=55)
   ❓ ЗДЕСЬ ДОЛЖНА быть загрузка озера и кэширование HTML
   ✅ Есть mainPhpAutoFishPrepare() который парсит озеро
   ❓ Но это код mainPhp.java, а не FishAjaxPhp!
   ↓
4. ⚠️ Затем отправляется fish_ajax.php?act=1 (JS зонд)
   ↓
5. processFishAct1() парсит act=1 payload
   🔴 AppVars.FishCurrentVcode = state.vcode  ← ОТ ACT=1!
   ↓
6. Через несколько минут vcode на сервере истекает
   ↓
7. Следующий цикл использует СТАРЫЙ vcode
   🔴 "Неверный код защиты"
```

### 5.2 Почему vcode из act=1 неправильный?

**Формат act=1 payload**:
```
RESO@...@[1,"captcha_token","vcode_here",massCur,massMax,[primid,name,count]...]
```

**Проблема**:
1. Этот vcode может быть кэширован на сервере (не свежий)
2. act=1 это ТЕХНИЧЕСКИЙ зонд, не основной запрос
3. В ПК-версии vcode ВСЕГДА извлекается из озера (form hidden field)

**Правильный источник vcode**:
```html
<form name=fish method=post action="main.php">
    <input name=vcode value="REAL_FRESH_VCODE_HERE">
    <input name=get_id value="55">
    <input name=act value="4">
    <input name=primid value="">
    ...
</form>
```

---

## 6. АРХИТЕКТУРНАЯ ПЕРЕДЕЛКА

### Текущая архитектура (неправильная)

```
kickFishCycleAttempt()
  │
  ├─ JS: FishStart(vcode) / fishbutton.click()
  │
  ├─ (загружается озеро main.php?get_id=55)
  │  ├─ mainPhpAutoFishPrepare() парсит озеро ✅
  │  └─ Показывает форму озера
  │
  └─ Затем выполняется fish_ajax.php?act=1
     │
     └─ processFishAct1()
        └─ AppVars.FishCurrentVcode = act1_vcode 🔴 НЕПРАВИЛЬНО
```

### Правильная архитектура (как в ПК-версии)

```
kickFishCycleAttempt()
  │
  ├─ ★ executeFishingCycleCore() 🟢 НОВЫЙ ОСНОВНОЙ ЦИКЛ
  │  │
  │  ├─ ★ loadFreshLakeHtmlForFishing()  🟢 ЗАГРУЖАЕМ озеро
  │  │  └─ GET main.php?get_id=55
  │  │     └─ Cache: AppVars.ContentLakeHtml = html
  │  │
  │  ├─ ★ mainPhpAutoFishPrepareAndroid()  🟢 ПАРСИМ озеро
  │  │  ├─ Извлекаем СВЕЖИЙ vcode из формы озера 🟢
  │  │  ├─ Выбираем приманку с остатком
  │  │  └─ AppVars.FishCurrentVcode = freshVcode ✅
  │  │
  │  └─ Отправляем действие (act=4) с ПРАВИЛЬНЫМ vcode
  │     ├─ С капчой → showFishCaptchaDialog()
  │     └─ Без капчи → loadUrl(submitUrl)
  │
  └─ (act=1 становится вспомогательным)
     └─ processFishAct1() только для синхронизации
```

---

## 7. КОНКРЕТНЫЕ ФАЙЛЫ И СТРОКИ ДЛЯ ИЗМЕНЕНИЯ

### 7.1 MainPhp.java

#### Кэширование озера при загрузке

**Искать**: Метод обработки `main.php?get_id=55`

**Добавить**:
```java
if (address != null && address.contains("get_id=55") && !address.contains("&act=")) {
    // Это озеро (без action запроса)
    // Кэшируем для последующего использования в FishAjaxPhp
    AppVars.ContentLakeHtml = html;
    AppVars.LastLakeHtmlCacheTimeMs = System.currentTimeMillis();
    Log.d(TAG, "FISH_OPTIMIZE lake cached for cycling");
}
```

### 7.2 FishAjaxPhp.java

#### Добавить executeFishingCycleCore() метод

```java
/**
 * ★ ГЛАВНЫЙ ЦИКЛ АВТО-РЫБАЛКИ (как в ПК-версии)
 * 
 * Архитектура:
 * 1. Загружаем свежий HTML озера (main.php?get_id=55)
 * 2. Парсим vcode ИЗ ОЗЕРА (не из act=1!)
 * 3. Выбираем приманку
 * 4. Отправляем действие с ПРАВИЛЬНЫМ vcode
 */
private static void executeFishingCycleCore() {
    if (!isAutoFishEnabled()) {
        return;
    }
    
    // ★ ШАГ 1: ЗАГРУЖАЕМ озеро ★
    String lakeHtml = AppVars.ContentLakeHtml;
    if (lakeHtml == null || lakeHtml.isEmpty()) {
        Log.d(TAG, "FISH_OPTIMIZE cycle: lake HTML not cached, requesting...");
        // Нужна загрузка озера - но это асинхронно!
        // Временное решение: отправляем JS для открытия озера
        WebView webView = getMainWebView();
        if (webView != null) {
            webView.loadUrl("http://neverlands.ru/main.php?get_id=55");
        }
        return;
    }
    
    // ★ ШАГ 2: ПАРСИМ озеро ★
    FishCycleState state = parseAndPrepareFromLakeHtml(lakeHtml);
    if (state == null) {
        Log.w(TAG, "FISH_OPTIMIZE cycle: failed to parse lake");
        return;
    }
    
    // ★ СОХРАНЯЕМ СВЕЖИЙ vcode ОТ ОЗЕРА ★
    AppVars.FishCurrentVcode = state.vcode;
    
    // ★ ШАГ 3: ОТПРАВЛЯЕМ действие ★
    if (state.captchaRequired) {
        showFishCaptchaDialog(state.captchaUrl, state.submitUrl);
    } else {
        WebView webView = getMainWebView();
        if (webView != null) {
            webView.loadUrl(state.submitUrl);
        }
    }
}

/**
 * Парсинг озера (аналог C# mainPhpAutoFishPrepare).
 */
private static FishCycleState parseAndPrepareFromLakeHtml(String html) {
    if (html == null || !html.contains("Вид ресурса: рыба")) {
        return null;  // Не озеро
    }
    
    FishCycleState state = new FishCycleState();
    
    // Извлекаем параметры  озера
    state.vcode = HelperStrings.subString(html, "name=vcode value=", ">");
    state.getid = HelperStrings.subString(html, "name=get_id value=", ">");
    state.lakeid = HelperStrings.subString(html, "name=lakeid value=", ">");
    state.act = HelperStrings.subString(html, "name=act value=", ">");
    
    if (state.vcode == null || state.vcode.isEmpty()) {
        Log.w(TAG, "FISH_OPTIMIZE parse lake: vcode not found");
        return null;
    }
    
    // Проверяем капчу
    state.captchaUrl = extractCaptchaUrl(html);
    state.captchaRequired = (state.captchaUrl != null && !state.captchaUrl.isEmpty());
    
    // Выбираем приманку  
    if (!selectBaitFromLakeHtml(html, state)) {
        Log.w(TAG, "FISH_OPTIMIZE: no suitable bait");
        return null;
    }
    
    // Собираем URL действия
    state.submitUrl = "http://neverlands.ru/main.php?get_id=55&act=4"
            + "&primid=" + state.primid
            + "&lakeid=" + state.lakeid
            + "&vcode=" + state.vcode;
    
    if (state.captchaRequired) {
        state.submitUrl += "&code=????";
    }
    
    Log.d(TAG, "FISH_OPTIMIZE cycle prepared: primid=" + state.primid
            + ", vcode=" + state.vcode.substring(0, Math.min(8, state.vcode.length()))
            + "..., captcha=" + state.captchaRequired);
    
    return state;
}
```

#### Обновить processFishAct1() - удалить неправильное присваивание vcode

**БЫЛО**:
```java
AppVars.FishCurrentVcode = state.vcode;  // 🔴 ИЗ ACT=1!
```

**ДОЛЖНО БЫТЬ**:
```java
// Только для инфо, НЕ для сохранения vcode!
Log.d(TAG, "FISH_OPTIMIZE act1 received, but using vcode from lake, not from act1");
```

### 7.3 Проверка в AppVars.java

Убедиться что есть:
```java
public static String ContentLakeHtml = "";
public static long LastLakeHtmlCacheTimeMs = 0L;
```

---

## 8. ПОШАГОВЫЙ ТЕСТОВЫЙ ПЛАН

### Шаг 1: Проверить кэширование озера

**Логи**: Ищем `FISH_OPTIMIZE lake cached`

```
[OK] Озеро загружается при открытии get_id=55
[OK] HTML сохраняется в AppVars.ContentLakeHtml
```

### Шаг 2: Проверить извлечение vcode из озера

**Логи**: Ищем `FISH_OPTIMIZE cycle prepared: primid=X, vcode=...`

```
[OK] vcode извлекается ИЗ озера, а не из act=1
[OK] vcode не пустой
[OK] Остаток приманки > 4
```

### Шаг 3: Проверить цикл рыбалки

**Логи**:
```
AUTO_FISH_TRACE act2 cooldown=300s
AUTO_FISH_TRACE cycle kick via JS / loadUrl
FISH_OPTIMIZE cycle prepared при следующем цикле
```

### Шаг 4: Регрессионный тест - "неверный код защиты"

**Проверка**:
```
[✅] Первая ловля успешна
[✅] Вторая ловля (через 5+ минут) УСПЕШНА (не ошибка!)
[✅] Третья ловля УСПЕШНА
```

---

## 9. ИТОГОВЫЙ ЧЕКЛИСТ ИСПРАВЛЕНИЙ

### Обязательно реализовать

- [ ] **Добавить кэширование озера в MainPhp.java**
  - При загрузке get_id=55, сохранять HTML в AppVars
  - Маркер: `if (address.contains("get_id=55"))`

- [ ] **Создать executeFishingCycleCore() в FishAjaxPhp.java**
  - Основной цикл рыбалки (как в ПК-версии)
  - Загружает озеро → парсит → отправляет действие

- [ ] **Создать parseAndPrepareFromLakeHtml() в FishAjaxPhp.java**
  - Парсит озеро (вместо act=1 зонда)
  - Извлекает СВЕЖИЙ vcode из озера

- [ ] **Обновить processFishAct1()**
  - УДАЛИТЬ неправильное: `AppVars.FishCurrentVcode = state.vcode`
  - Оставить только для синхронизации

- [ ] **Обновить kickFishCycleAttempt()**
  - Вызвать `executeFishingCycleCore()` вместо JS-ветки

### Опционально (будущая оптимизация)

- [ ] Убрать зависимость от JS FishStart() / fishbutton
- [ ] Реализовать синхронный GET озера через interceptor
- [ ] Добавить тайминг-защиту от дублей vcode

---

## 10. КРИТИЧЕСКИЕ МОМЕНТЫ

### 🔴 НИКОГДА не использовать vcode из act=1

```java
// ❌ НЕПРАВИЛЬНО - может привести к "неверный код защиты"
AppVars.FishCurrentVcode = act1Response.vcode;

// ✅ ПРАВИЛЬНО - только из озера
AppVars.FishCurrentVcode = lakeHtml.vcode;
```

### 🔴 ВСЕГДА проверять остаток приманки > 4

```java
// ❌ НЕПРАВИЛЬНО - выберет приманку даже если остаток 0
selectAllowedBait(state.baits);

// ✅ ПРАВИЛЬНО - проверяет остаток в озере
selectBaitFromLakeHtml(html, state);  // count > 4
```

### 🔴 НИКОГДА не планировать цикл без свежего озера

```java
// ❌ НЕПРАВИЛЬНО - следующий цикл без озера
kickFishCycleAttempt(token, attempt + 1);

// ✅ ПРАВИЛЬНО - перезагрузить озеро перед циклом
loadFreshLakeHtmlForFishing();
executeFishingCycleCore();
```

---

## ВЫВОД

**Основная причина "неверный код защиты"**:
1. ✅ Загружается озеро (get_id=55) 
2. ⚠️ Парсится mainPhpAutoFishPrepare() но ТОЛЬКО передается finishLink
3. 🔴 Но vcode сохраняется ИЗ act=1 (неправильный источник)
4. 🔴 Через 5 минут vcode истекает
5. 🔴 Следующий цикл использует СТАРЫЙ vcode
6. 🔴 "Неверный код защиты" - ошибка сервера (мы же отправили старый код!)

**Решение**: Всегда сохранять vcode ИЗ озера (main.php?get_id=55), а не из act=1.

