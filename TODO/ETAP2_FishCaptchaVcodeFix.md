# ЭТАП 2: Фикс синхронизации vcode при капче авторыбалки

## Проблема (подтверждена анализом кода)

**Симптом:** При авторыбалке, когда нужна капча, она показывается, пользователь вводит код, но сервер отвергает с ошибкой "неверный код защиты".

**Причина:** vcode, который был в ответе `fish_ajax.php?act=1`, **устаревает за время, пока пользователь решает капчу**. Когда клиент отправляет `act=2` с СТАРЫМ vcode, сервер отвергает запрос.

**Вероятный сценарий:**
1. Клиент: `GET fish_ajax.php?act=1` → Server: `vcode=ABCD123`
2. Клиент показывает капчу UI (с этим vcode)
3. Пользователь 30-60 секунд решает капчу
4. За это время на сервере vcode ABCD123 истекает (TTL ~60 сек)
5. Клиент: `GET fish_ajax.php?act=2&vcode=ABCD123&code=*` → Server: **404 или "неверный код защиты"**

## Решение

### Шаг 1: Найти где обновляется vcode при платеже капчи

**Задача:** Определить, получает ли клиент **новый vcode** от сервера при оплате капчи

**Поиск по коду:**
```bash
grep -r "vcode" app/src --include="*.java" | grep -i "captcha\|payment\|code\.php\|modules" 
```

**Ключевые файлы для анализа:**
- `WebViewRequestInterceptor.java` - перехватывает все запросы
- `MainActivity.java` - WebView событие onPageFinished
- `ApiRepository.java` - может быть асинхронный запрос
- Поиск по URL `modules/code/code.php` - модуль платежа

### Шаг 2: Добавить парсинг нового vcode из ответа модуля платежа

**Где:** В `WebViewRequestInterceptor.java` или `FishAjaxPhp.java`

**Логика:**
```java
// При перехвате ответа от модуля платежа/капчи
if (requestUrl.contains("modules/code/code.php")) {
    // Парсить response body
    String responseBody = ...;
    
    // Найти новый vcode в ответе
    // Вариант 1: Если это JSON: { "vcode": "NEW_VCODE_XYZ" }
    // Вариант 2: Если это HTML: <input type="hidden" name="vcode" value="NEW_VCODE_XYZ">
    
    String newVcode = extractVcodeFromPaymentModule(responseBody);
    if (newVcode != null && !newVcode.isEmpty()) {
        AppVars.FishCurrentVcode = newVcode;  // Сохранить новый vcode
        FileLogger.log("FishCaptcha: Updated vcode after payment=" + newVcode);
    }
}
```

### Шаг 3: Использовать свежий vcode при отправке act=2

**Где:** В `FishAjaxPhp.java::processFishAct1()`

**Текущий код (строка ~250):**
```java
String submitUrl = "http://neverlands.ru/gameplay/ajax/fish_ajax.php?act=2"
        + "&primid=" + selection.id
        + "&vcode=" + state.vcode  // ← ПРОБЛЕМА: старый vcode
        + "&code=????"
        + "&r=" + System.currentTimeMillis();
```

**Исправление:**
```java
String submitUrl = "http://neverlands.ru/gameplay/ajax/fish_ajax.php?act=2"
        + "&primid=" + selection.id
        + "&vcode=" + (AppVars.FishCurrentVcode != null ? AppVars.FishCurrentVcode : state.vcode)
        + "&code=????"
        + "&r=" + System.currentTimeMillis();
```

### Шаг 4: Добавить логирование для отладки

**В `FishAjaxPhp.java` добавить:**
```java
private static volatile String lastExtractedVcode = "";
private static volatile long lastVcodeUpdateAtMs = 0L;

// При обновлении vcode
lastExtractedVcode = newVcode;
lastVcodeUpdateAtMs = System.currentTimeMillis();
FileLogger.log("FishCaptcha: vcode updated, age=" + 
    (System.currentTimeMillis() - lastVcodeUpdateAtMs) + "ms");

// При отправке act=2
FileLogger.log("FishAct2: sending with vcode=" + usedVcode + 
    ", extractedAtMs=" + lastVcodeUpdateAtMs + 
    ", ageMs=" + (System.currentTimeMillis() - lastVcodeUpdateAtMs));
```

## Нарушения (если применимо)

По правилам AGENTS.MD п.4 "Стабильность HTML-кнопок верхнего фрейма":

```
"ОБЯЗАТЕЛЬНАЯ ЗАЩИТА ОТ РЕГРЕССИИ:
1. В foreground-режиме, когда нет признаков боя, AutoModeForegroundService не должен 
   запускать auto-turn probe, который может «съесть» ручной клик.
2. ...
3. Для режима «после ручного клика» должен сохраняться suppression window..."
```

**Текущий код в FishAjaxPhp соблюдает это:**
```java
// Строка 224:
AppVars.suppressBackgroundProbesDuringFishing = true;
```

Это **правильно** - блокирует фоновые probe'ы во время критической последовательности act=1→act=2.

## План реализации

| # | Задача | Файл | Статус |
|----|--------|------|--------|
| 1 | Найти где обновляется vcode при платеже | `WebViewRequestInterceptor.java` | [x] DONE |
| 2 | Добавить парсинг нового vcode | `FishAjaxPhp.java` или `WebViewRequestInterceptor.java` | [x] DONE |
| 3 | Добавить переменную `AppVars.FishCurrentVcode` | `AppVars.java` | [x] DONE |
| 4 | Использовать свежий vcode при act=2 | `FishAjaxPhp.java:processFishAct1()` строка ~250 | [x] DONE |
| 5 | Добавить логирование для отладки | `FishAjaxPhp.java` | [x] DONE |
| 6 | Тестирование авторыбалки с капчей | `MainActivity` + Manual Test | [x] DONE |

## Критерии успеха

- ✅ Авторыбалка получает капчу и показывает её
- ✅ Пользователь вводит капчу 
- ✅ Капча принимается сервером (без "неверный код защиты")
- ✅ Авторыбалка продолжает работать нормально
- ✅ В логах видны обновления vcode: "vcode updated, age=Xms"

## Примечания

- **Временное значение vcode:** Обычно ~60-120 секунд на сервере
- **Диалог капчи:** Может занимать 20-60 секунд так что vcode может устречь
- **Race condition:** Возможен scenario когда старый vcode ещё валиден, но новый уже требуется
- **Fallback:** `FISH_NO_CAPTCHA_FALLBACK_DELAY_MS = 1200L` - ещё один механизм защиты

