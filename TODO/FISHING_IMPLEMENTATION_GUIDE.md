# РЕШЕНИЕ: Правильная реализация цикла авто-рыбалки для Android

**Дата**: 01.04.2026  
**Статус**: Рекомендуемая реализация

---

## ПРОБЛЕМА В ОДНОЙ СТРОКЕ

**ПК-версия**: каждый цикл загружает `main.php?get_id=55` → парсит vcode ИЗ ЭТОГО HTML  
**Android-версия**: пытается извлечь vcode из `fish_ajax.php?act=1` (неправильный источник)

---

## РЕШЕНИЕ: Архитектурная переделка

### Текущая (неправильная) схема:

```
Инициирование откуда-то →  act=1 (ajax) → processFishAct1() 
                                          ↓
                                 act=2 fallback
                                          ↓
                                 scheduleNoCaptchaAct2Fallback()
```

**Проблема**: Нет загрузки озера! Нет mainPhpAutoFishPrepare()!

---

### Правильная схема (как в ПК):

```
ОСНОВНОЙ ЦИК Л:
1. kickFishCycleAttempt() (уже есть, но переделать)
              ↓
2. ★ loadFreshLakeHtml() ← ТЕКУЩЕЕ ОТСУТСТВИЕ!
   main.php?get_id=55
              ↓
3. ★ mainPhpAutoFishPrepare(html) ← ТЕКУЩЕЕ ОТСУТСТВИЕ!
   - Парсит vcode ИЗ озера
   - Выбирает приманку
   - Проверяет капчу
              ↓
4. Отправляет действие с СВЕЖИМ vcode
              ↓
5. Обрабатывает ответ (парсит cooldown)
              ↓
6. Переходит на шаг 1 через cooldown
```

---

## КОД: Правильная реализация

### Файл: FishAjaxPhp.java

**Добавить новые константы**:
```java
private static final String FISH_LAKE_HTML_FETCH_TIMEOUT_MS = 5000L;
private static final String FRESH_LAKE_HTML_CACHE_KEY = "fresh_lake_html";
```

**Добавить основной метод цикла**:
```java
/**
 * Главная точка цикла авто-рыбалки (архитектура как в ПК-версии).
 * 
 * Порядок действий:
 * 1. Загружаем свежий HTML озера (main.php?get_id=55)
 * 2. Парсим vcode, приманку, капчу ИЗ ЭТОГО HTML
 * 3. Отправляем действие рыбалки
 * 4. Обрабатываем ответ (cooldown)
 * 5. Планируем следующий цикл
 */
private static void executeFishingCycleCore() {
    if (!isAutoFishEnabled()) {
        return;
    }
    
    // Проверка cooldown (NeverTimer)
    long now = System.currentTimeMillis();
    long timeUntilDueMs = AppVars.NeverTimer - now;
    if (timeUntilDueMs > 250L) {
        Log.d(TAG, "AUTO_FISH_TRACE cycle defer by NeverTimer: waitMs=" + timeUntilDueMs);
        return;
    }
    
    // ★ ШАГ 1: ЗАГРУЖАЕМ СВЕЖИЙ HTML ОЗЕРА ★
    // Это критично! Должно быть main.php?get_id=55, а не озеро из кэша
    String lakeHtml = loadFreshLakeHtmlForFishing();
    if (lakeHtml == null || lakeHtml.isEmpty()) {
        Log.d(TAG, "AUTO_FISH_TRACE lake HTML fetch failed, will retry");
        scheduleRetryFishingCycleWithDelay(2000L);
        return;
    }
    
    // ★ ШАГ 2: ПАРСИМ mainPhpAutoFishPrepare эквивалент ★
    // Это основная функция из ПК-версии!
    FishCycleState cycleState = mainPhpAutoFishPrepareAndroid(lakeHtml);
    if (cycleState == null) {
        Log.d(TAG, "AUTO_FISH_TRACE prepare failed, lake state invalid");
        disableAutoFish("Озеро: неверное состояние");
        return;
    }
    
    // ★ СОХРАНЯЕМ СВЕЖИЙ VCODE ★ (из озера, не из act=1!)
    AppVars.FishCurrentVcode = cycleState.vcode;
    AppVars.AutoFishLikeId = cycleState.primid;
    AppVars.AutoFishMassa = cycleState.massa;
    AppVars.NamePri = cycleState.primName;
    AppVars.ValPri = cycleState.primCount;
    
    // Логирование для диагностики
    Log.d(TAG, "AUTO_FISH_TRACE cycle prepared: primid=" + cycleState.primid
            + ", vcode=" + cycleState.vcode.substring(0, Math.min(8, cycleState.vcode.length())) + "..."
            + ", hasCaptcha=" + (cycleState.codeAddress != null && !cycleState.codeAddress.isEmpty()));
    
    // ★ ШАГ 3: ЗАПУСКАЕМ ДЕЙСТВИЕ ★
    if (cycleState.codeAddress != null && !cycleState.codeAddress.isEmpty()) {
        // Есть капча - показываем диалог
        String submitUrl = buildFishSubmitUrl(cycleState);
        showFishCaptchaDialogOnce(cycleState.codeAddress, submitUrl);
        Log.d(TAG, "AUTO_FISH_TRACE cycle with captcha: codeUrl=" + cycleState.codeAddress);
    } else {
        // Нет капчи - отправляем сразу
        submitFishingActionDirect(cycleState);
        Log.d(TAG, "AUTO_FISH_TRACE cycle no captcha: act=4 submit");
    }
}

/**
 * Парсинг озера = mainPhpAutoFishPrepare из ПК-версии.
 * 
 * Что делает:
 * 1. Проверяет, что это именно озеро (есть маркер)
 * 2. Извлекает vcode, lakeid, get_id, act
 * 3. Извлекает CodeAddress (капча)
 * 4. Выбирает доступную приманку
 * 5. Возвращает состояние для цикла
 */
private static FishCycleState mainPhpAutoFishPrepareAndroid(String html) {
    if (html == null || html.isEmpty()) {
        Log.d(TAG, "AUTO_FISH_TRACE prepare: empty HTML");
        return null;
    }
    
    // Маркер озера (аналог C# HtmlValueRiba)
    String lakeMarker = "Вид ресурса: рыба";
    if (!html.contains(lakeMarker)) {
        Log.d(TAG, "AUTO_FISH_TRACE prepare: not a lake page");
        return null;
    }
    
    FishCycleState state = new FishCycleState();
    
    // Извлекаем базовые параметры озера
    state.massa = HelperStrings.subString(html, "<b>Масса Вашего инвентаря: ", "</b>");
    state.vcode = HelperStrings.subString(html, "=vcode value=", ">");
    String getid = HelperStrings.subString(html, "=get_id value=", ">");
    String lakeid = HelperStrings.subString(html, "=lakeid value=", ">");
    String act = HelperStrings.subString(html, "=act value=", ">");
    
    if (state.vcode == null || state.vcode.isEmpty()) {
        Log.d(TAG, "AUTO_FISH_TRACE prepare: vcode not found");
        return null;
    }
    
    state.getid = getid != null && !getid.isEmpty() ? getid : "55";
    state.lakeid = lakeid != null && !lakeid.isEmpty() ? lakeid : "1";
    state.act = act != null && !act.isEmpty() ? act : "4";
    
    // Проверяем капчу
    state.codeAddress = extractCodeAddressFromLake(html);
    
    // ★ ВЫБИРАЕМ ПРИМАНКУ С ПРОВЕРКОЙ ОСТАТКА ★
    if (!selectBaitFromLakeHtmlAndroid(html, state)) {
        Log.w(TAG, "AUTO_FISH_TRACE prepare: no bait available");
        disableAutoFish("Нет доступной приманки");
        return null;
    }
    
    return state;
}

/**
 * Выбор приманки из озера (плюс проверка остатка).
 * Аналог C# SelectBaitFromLake из MainPhpFish.cs.
 */
private static boolean selectBaitFromLakeHtmlAndroid(String html, FishCycleState state) {
    if (AppVars.Profile == null) {
        return false;
    }
    
    // Список включённых приманок в порядке из профиля
    List<BaitOption> baits = new ArrayList<>();
    
    // Проверяем каждую приманку из профиля
    if ((AppVars.Profile.FishEnabledPrims & PRIM_BREAD) != 0) 
        baits.add(new BaitOption("Хлеб", "38"));
    if ((AppVars.Profile.FishEnabledPrims & PRIM_WORM) != 0) 
        baits.add(new BaitOption("Червяк", "39"));
    if ((AppVars.Profile.FishEnabledPrims & PRIM_BIG_WORM) != 0) 
        baits.add(new BaitOption("Крупный червяк", "40"));
    if ((AppVars.Profile.FishEnabledPrims & PRIM_FLY) != 0) 
        baits.add(new BaitOption("Опарыш", "41"));
    if ((AppVars.Profile.FishEnabledPrims & PRIM_MOTTI) != 0) 
        baits.add(new BaitOption("Мотыль", "42"));
    if ((AppVars.Profile.FishEnabledPrims & PRIM_BLESA) != 0) 
        baits.add(new BaitOption("Блесна", "43"));
    if ((AppVars.Profile.FishEnabledPrims & PRIM_DONKA) != 0) 
        baits.add(new BaitOption("Донка", "44"));
    if ((AppVars.Profile.FishEnabledPrims & PRIM_MORMA) != 0) 
        baits.add(new BaitOption("Мормышка", "45"));
    if ((AppVars.Profile.FishEnabledPrims & PRIM_ZAGOVOR) != 0) 
        baits.add(new BaitOption("Заговоренная блесна", "46"));
    
    // Перетасовываем (Dice.Make эквивалент)
    Collections.shuffle(baits);
    
    // Ищем первую имеющуюся приманку в HTML
    for (BaitOption bait : baits) {
        String pattern = "<input type=radio name=primid value=" + bait.id + 
                        "></td><td bgcolor=#FFFFFF>";
        
        int pos = html.toLowerCase(Locale.ROOT).indexOf(pattern.toLowerCase(Locale.ROOT));
        if (pos == -1) {
            continue; // Приманка не в озере
        }
        
        // Проверяем остаток (количество после <b>)
        int countStart = pos + pattern.length();
        String afterPattern = html.substring(Math.min(countStart, html.length()), 
                                            Math.min(countStart + 500, html.length()));
        
        Integer count = extractBaitCount(afterPattern);
        if (count != null && count > 4) {
            // Приманка найдена и остаток достаточен
            state.primid = bait.id;
            state.primName = bait.name;
            state.primCount = count;
            Log.d(TAG, "AUTO_FISH_TRACE selected bait: " + bait.name + " (id=" + bait.id + ", count=" + count + ")");
            return true;
        } else if (count == null) {
            // Не смогли определить остаток, используем всё равно (будет ошибка если нет)
            state.primid = bait.id;
            state.primName = bait.name;
            state.primCount = count != null ? count : -1;
            Log.d(TAG, "AUTO_FISH_TRACE selected bait (unknown count): " + bait.name);
            return true;
        }
    }
    
    Log.w(TAG, "AUTO_FISH_TRACE no suitable baits found in lake page");
    return false;
}

/**
 * Загрузить свежий HTML озера.
 * ★ КРИТИЧНО: Это должен быть GET main.php?get_id=55 ★
 */
private static String loadFreshLakeHtmlForFishing() {
    MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
    if (activity == null) {
        return null;
    }
    
    // Формируем URL озера (get_id=55)
    String lakeUrl = "http://neverlands.ru/main.php?get_id=55";
    
    // ОПЦИЯ 1: Если есть доступ к синхронному HTTP (через interceptor)
    // String html = makeHttpGetSynchronous(lakeUrl);
    // return html;
    
    // ОПЦИЯ 2: Использовать асинхронный WebView.loadUrl + callback
    // (это потребует переделки в arquitectуру с callback'ами)
    
    // ТЕКУЩАЯ РЕАЛИЗАЦИЯ: использовать кэшированный HTML, если доступен
    // (ВРЕМЕННО, до полной переделки)
    String cachedHtml = AppVars.Profile != null ? AppVars.Profile.CachedLakeHtml : null;
    if (cachedHtml != null && !cachedHtml.isEmpty()) {
        // Проверяем, что это свежий кэш (не старше 1 сек)
        long ageMs = System.currentTimeMillis() - AppVars.LastLakeHtmlCacheTimeMs;
        if (ageMs < 1000L) {
            return cachedHtml;
        }
    }
    
    Log.d(TAG, "AUTO_FISH_TRACE lake HTML not available (need WebView refactor for sync load)");
    return null;
}

/**
 * Формирование URL submit-действия рыбалки.
 */
private static String buildFishSubmitUrl(FishCycleState state) {
    return "http://neverlands.ru/main.php?get_id=" + state.getid
            + "&lakeid=" + state.lakeid
            + "&act=" + state.act
            + "&primid=" + state.primid
            + "&code=????"  // Placeholder для капчи
            + "&vcode=" + state.vcode;
}

/**
 * Прямая отправка действия (без капчи).
 */
private static void submitFishingActionDirect(FishCycleState state) {
    MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
    if (activity == null) {
        return;
    }
    
    WebView webView = activity.getMainWebView();
    if (webView == null) {
        return;
    }
    
    String submitUrl = buildFishSubmitUrl(state).replace("&code=????", "");
    Log.d(TAG, "AUTO_FISH_TRACE submit fishing action: " + submitUrl);
    
    webView.loadUrl(submitUrl);
}

/**
 * Helper: Вспомогательный класс состояния цикла рыбалки.
 */
private static class FishCycleState {
    String vcode;        // Свежий vcode от озера
    String primid;       // ID приманки
    String primName;     // Название приманки
    Integer primCount;   // Остаток приманки
    String massa;        // Масса инвентаря
    String codeAddress;  // URL капчи (или null если её нет)
    String getid;
    String lakeid;
    String act;
}

/**
 * Helper: Информация о приманке.
 */
private static class BaitOption {
    String name;
    String id;
    BaitOption(String name, String id) {
        this.name = name;
        this.id = id;
    }
}

/**
 * Helper: Извлечение количества приманки из HTML.
 */
private static Integer extractBaitCount(String html) {
    if (html == null || html.isEmpty()) {
        return null;
    }
    try {
        // Ищем <b>NNN</b>
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<b>(\\d+)</b>");
        java.util.regex.Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
    } catch (Exception ignored) {
    }
    return null;
}

/**
 * Helper: Извлечение CodeAddress из озера (для капчи).
 */
private static String extractCodeAddressFromLake(String html) {
    if (html == null || html.isEmpty()) {
        return null;
    }
    String marker = "code.php?";
    int pos = html.indexOf(marker);
    if (pos == -1) {
        return null;
    }
    pos += marker.length();
    int end = Math.min(pos + 100, html.length());
    
    // Ищем конец токена (спецсимволы, пробел, кавычка)
    while (end > pos && html.charAt(end - 1) != '"' && html.charAt(end - 1) != '\''
            && html.charAt(end - 1) != ' ' && html.charAt(end - 1) != '>') {
        end--;
    }
    
    String token = html.substring(pos, end).trim();
    if (token.isEmpty()) {
        return null;
    }
    
    return "http://neverlands.ru/modules/code/code.php?" + token;
}

/**
 * Планирование повторной попытки цикла.
 */
private static void scheduleRetryFishingCycleWithDelay(long delayMs) {
    MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
    if (activity == null) {
        return;
    }
    
    WebView webView = activity.getMainWebView();
    if (webView == null) {
        return;
    }
    
    activity.runOnUiThread(() -> webView.postDelayed(
            FishAjaxPhp::executeFishingCycleCore,
            delayMs));
}
```

---

## ИНТЕГРАЦИЯ: Вызов из правильного места

### В kickFishCycleAttempt():

**БЫЛО**:
```java
private static void kickFishCycleAttempt(long cycleToken, int attempt) {
    // ... много логики прямо здесь
}
```

**ДОЛЖНО БЫТЬ**:
```java
private static void kickFishCycleAttempt(long cycleToken, int attempt) {
    if (!isAutoFishEnabled()) {
        return;
    }
    
    MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
    if (activity == null) {
        return;
    }
    
    activity.runOnUiThread(() -> {
        // ★ ВЫЗОВ ОСНОВНОГО ЦИКЛА ★
        executeFishingCycleCore();
    });
}
```

---

## ОТЛИЧИЯ ОТ ТЕКУЩЕЙ РЕАЛИЗАЦИИ

| Функция | ДО (неправильно) | ПОСЛЕ (правильно) |
|---------|-----------------|------------------|
| **Загрузка данных** | ❌ act=1 зонд | ✅ main.php?get_id=55 (озеро) |
| **Парсинг vcode** | Из act=1 response | Из озера HTML |
| **mainPhpAutoFishPrepare** | ❌ Не вызывается | ✅ executeFishingCycleCore → mainPhpAutoFishPrepareAndroid() |
| **Выбор приманки** | selectAllowedBait() | selectBaitFromLakeHtmlAndroid() (с проверкой остатка) |
| **Точка инициирования** | kickFishCycleAttempt() (какие-то шаги) | ★ executeFishingCycleCore() (ОСНОВНОЙ) |

---

## ТРЕБОВАНИЯ К WebView ИНТЕГРАЦИИ

Для полной работы нужно:

1. **Синхронный GET запрос озера** (или кэш)
   ```java
   // Либо есть метод загрузки озера которого нет
   // Либо нужно кэшировать озеро при первой загрузке
   ```

2. **Обновить MainPhp.java** для кэширования озера
   ```java
   // При загрузке main.php?get_id=55 сохранять в:
   AppVars.Profile.CachedLakeHtml = html;
   AppVars.LastLakeHtmlCacheTimeMs = System.currentTimeMillis();
   ```

3. **Синхронизировать processFishAct1/Act2** с новым циклом
   - act=1 становится ВСПОМОГАТЕЛЬНЫМ (только для синхронизации)
   - Основной цикл - executeFishingCycleCore()

---

## ЧЕКЛИСТ РЕАЛИЗАЦИИ

- [ ] Создать executeFishingCycleCore() метод
- [ ] Создать mainPhpAutoFishPrepareAndroid() метод  
- [ ] Реализовать selectBaitFromLakeHtmlAndroid() с проверкой остатка
- [ ] Добавить кэширование озера в MainPhp.java
- [ ] Обновить kickFishCycleAttempt() для вызова executeFishingCycleCore()
- [ ] Протестировать с логированием каждого шага цикла
- [ ] Убедиться, что vcode ВСЕГДА из озера (main.php?get_id=55)
- [ ] Убедиться, что приманка выбирается с проверкой остатка
- [ ] Обновить документацию/комментарии в коде

---

## ЗАКЛЮЧЕНИЕ

Эта реализация делает Android версию **соответствующей ПК архитектуре**:
- ✅ Загрузка озера перед действием
- ✅ Парсинг vcode из озера (свежий)
- ✅ Выбор приманки с проверкой остатка
- ✅ Единый точка входа в цикл (executeFishingCycleCore)
- ✅ Логирование для отладки

