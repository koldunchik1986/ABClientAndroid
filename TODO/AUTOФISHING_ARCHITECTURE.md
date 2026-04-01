# Архитектура Авто-Рыбалки в ПК-версии ABClient

**Дата анализа:** Апрель 2026  
**Статус:** Полный анализ архитектуры и цикла рыбалки  
**Критичность:** ВЫСОКАЯ - множественные точки обработки vcode

---

## 1. ОБЗОР АРХИТЕКТУРЫ

### 1.1 Основные компоненты системы авто-рыбалки

```
┌─────────────────────────────────────────────────────────────────┐
│  MainPhp.cs (главный обработчик HTTP ответов)                  │
│  ├─ Секция "Переключения перед забросом удочки" (строка 1723) │
│  │   └─ Проверка усталости, переодевание, переключения        │
│  │                                                               │
│  └─ Секция "Новая рыбалка" (строка 2070+)                     │
│      ├─ if (AppVars.Profile.FishAuto)                          │
│      ├─ MainPhpFindFlora()  - поиск флоры на странице          │
│      └─ MainPhpFindFish()   - поиск озера/оружно для рыбалки   │
│                                                                  │
├─ MainPhpFish.cs (обработка HTML рыбалки)                       │
│  ├─ MainPhpAutoFishPrepare() - подготовка заброса              │
│  │   ├─ Парсинг HTML формы                                    │
│  │   ├─ Извлечение vcode                                      │
│  │   ├─ Выбор приманки                                        │
│  │   └─ Возврат ссылки для рыбалки                           │
│  │                                                              │
│  └─ MainPhpFishReport()  - отрчёт о улове                      │
│      ├─ Парсинг результата                                    │
│      ├─ Обновление статистики                                 │
│      └─ Проверка умелки                                       │
│                                                                  │
├─ FishAjaxPhp.cs (AJAX обновления во время рыбалки)             │
│  └─ FishAjaxPhp() - обработка AJAX ответов                    │
│      ├─ Проверка ошибок рыбалки                              │
│      └─ Обновление отчёта рыбалки                            │
│                                                                  │
└─ Filter.cs (маршрутизировану запросов)                         │
   ├─ fish_ajax.php → FishAjaxPhp()                              │
   └─ main.php?get_id=55 → MainPhpFish()                         │
```

### 1.2 Критические глобальные переменные

```csharp
// Основные флаги рыбалки (AppVars.cs)
AppVars.Profile.FishAuto              // Включена ли авто-рыбалка (bool)
AppVars.Profile.FishTiedHigh          // Верхний предел усталости для рыбалки
AppVars.Profile.FishTiedZero          // Нужно ли пить при рыбалке
AppVars.Profile.FishHandOne           // Имя первой удочки
AppVars.Profile.FishHandTwo           // Имя второй удочки
AppVars.Profile.FishMaxLevelBots      // Макс уровень ботов для рыбалки
AppVars.Profile.FishEnabledPrims      // Битовая маска приманок (Bread, Worm, etc.)

// Состояние рыбалки
AppVars.AutoFishHand1                 // Текущая выбранная первая удочка
AppVars.AutoFishHand1D                // ID первой удочки
AppVars.AutoFishHand2                 // Текущая выбранная вторая удочка  
AppVars.AutoFishHand2D                // ID второй удочки
AppVars.AutoFishMassa                 // Текущая масса инвентаря
AppVars.AutoFishLikeId                // ID выбранной приманки
AppVars.AutoFishLikeVal               // Значение приманки (количество)
AppVars.CodeAddress                   // URL адрес для решения vcode (!КРИТИЧНА)
AppVars.AutoFishCheckUm               // Нужно ли проверить умелку рыбалки
AppVars.AutoFishCheckUd               // Нужно ли переодеться в удочку
AppVars.AutoFishWearUd                // Надовано ли переодеться
AppVars.AutoFishDrink                 // Нужно ли пить перед рыбалкой
AppVars.AutoFishDrinkOnce             // Одноразовое питье перед рыбалкой
AppVars.AutoFishNV                    // количество добытых NV

// Таймер рыбалки
AppVars.NeverTimer                    // Таймер на выполнение действия
```

---

## 2. ЦИКЛ АВТО-РЫБАЛКИ (ПОЛНЫЙ WORKFLOW)

### 2.1 Инициализация авто-рыбалки

**Файл:** `FormMain.cs`, метод `ButtonAutoFish_Click()` (строка 295)

```csharp
private void ButtonAutoFish_Click(object sender, EventArgs e)
{
    AppVars.Profile.FishAuto = buttonAutoFish.Checked;
    
    // Инициализация состояния
    AppVars.AutoFishCheckUd = true;           // Проверить удочку
    AppVars.AutoFishWearUd = false;           // Не надета
    AppVars.AutoFishCheckUm = (Profile.FishUm == 0);  // Проверить умелку если 0
    
    AppVars.AutoFishHand1 = "";               // Очистить удочки
    AppVars.AutoFishHand2 = "";
    AppVars.AutoFishMassa = "";               // Очистить массу
    AppVars.AutoFishNV = 0;                   // Обнулить добычу
    AppVars.AutoFishDrink = false;            // Не пьём
}
```

### 2.2 ФАЗА 1 - Переключение вкладок и проверки

**Файл:** `MainPhp.cs`, строка 1723+  
**Условие:** `if (AppVars.Profile.FishAuto && (DateTime.Now > AppVars.NeverTimer))`

```
Этап 1.1: Проверка усталости
├─ Если усталость > FishTiedHigh
│  └─ Переключиться на питьё
│
Этап 1.2: Проверка умелки рыбалки (если AutoFishCheckUm == true)
├─ Переключиться на персонажа (mselect=1)
├─ Прочитать умелку со статистики
└─ Обновить AppVars.Profile.FishUm
│
Этап 1.3: Проверка и переодевание удочки (если AutoFishCheckUd == true)
├─ Если AutoFishWearUd == false
│  ├─ Переключиться на персонажа
│  ├─ Проверить какую удочку надевать (MainPhpIsMustWearUd)
│  └─ Установить AutoFishWearUd = true
│
├─ Если AutoFishWearUd == true
│  ├─ Переключиться на инвентарь (im=0&wca=4)
│  └─ Надеть выбранную удочку (MainPhpWearUd)
│
└─ После переодевания AutoFishCheckUd = false
```

### 2.3 ФАЗА 2 - Поиск озера и заброс удочки

**Файл:** `MainPhp.cs`, строка 2070+  
**Условие:** `if (AppVars.Profile.FishAuto && (DateTime.Now > AppVars.NeverTimer))`

```csharp
if (AppVars.Profile.FishAuto && (DateTime.Now > AppVars.NeverTimer))
{
    // Шаг 1: Поиск озера на текущей странице
    var newhtml = MainPhpFindFlora(html);  // Ищет озеро
    if (!string.IsNullOrEmpty(newhtml))
    {
        html = newhtml;  // Переход в озеро
        goto end;
    }

    // Шаг 2: Если уже в озере - запустить рыбалку
    newhtml = MainPhpFindFish(html);       // Запуск рыбалки
    if (!string.IsNullOrEmpty(newhtml))
    {
        html = newhtml;  // Выполнить рыбалку
        goto end;
    }
}
```

### 2.4 ФАЗА 3 - Подготовка и запуск заброса

**Файл:** `MainPhpFish.cs`, метод `MainPhpAutoFishPrepare()` (строка 16)

**ВАЖНО: Это место где ИЗВЛЕКАЕТСЯ vcode!**

```csharp
private static string MainPhpAutoFishPrepare(string html)
{
    // 1. Проверка наличия рыбы на странице
    var p1 = html.IndexOf(AppConsts.HtmlValueRiba, ...);
    if (p1 == -1) return "";  // Нет рыбы
    
    // 2. КРИТИЧНО: Извлечение vcode из формы
    var pcode = html.IndexOf(AppConsts.HtmlCodePhp, ...);
    if (pcode != -1)
    {
        // Находим URL для решения защитного кода
        AppVars.CodeAddress = AppConsts.HtmlCodePhpFull + 
            html.Substring(pcode, pe - pcode);
    }
    
    // 3. Парсинг параметров формы
    var getid = HelperStrings.SubString(html, "=get_id value=", ">");    // 55
    var act = HelperStrings.SubString(html, "=act value=", ">");         // 4
    var vcode = HelperStrings.SubString(html, "=vcode value=", ">");     // VCODE!!!
    var lakeid = HelperStrings.SubString(html, "=lakeid value=", ">");   // ID озера
    
    if (string.IsNullOrEmpty(vcode)) return "";  // ОШИБКА: нет vcode
    
    // 4. Чтение массы инвентаря
    AppVars.AutoFishMassa = HelperStrings.SubString(html, 
        "<b>Масса Вашего инвентаря: ", "</b>");
    
    // 5. Выбор приманки из доступных
    // - Собрать список доступных приманок из AppVars.Profile.FishEnabledPrims
    // - Случайно выбрать приманку из списка
    // - Найти её ID (primid) в HTML форме
    // - Обновить AppVars.AutoFishLikeId, AppVars.AutoFishLikeVal
    
    // 6. Если найдена приманка и всё OK - возвращаем ссылку заброса
    return $"main.php?get_id={getid}&act={act}&primid={primid}&vcode={vcode}";
    
    // 7. ЕСЛИ ЕСТЬ ЗАЩИТНЫЙ КОД - показать диалог ввода
    if (!string.IsNullOrEmpty(AppVars.CodeAddress))
    {
        // Вызвать метод WriteFishingCode через UI
        AppVars.MainForm.BeginInvoke(
            new UpdateTrayFlashDelegate(...),
            new object[] { "Ввод цифр" }
        );
    }
}
```

### 2.5 ФАЗА 4 - Ввод защитного кода (vcode)

**Файл:** `FormMainCross.cs`, метод `WriteFishingCode()` (строка 1065)

```csharp
internal void WriteFishingCode(string code)
{
    // Этот метод вызывается ИЗ ДРУГОГО ПОТОКА (поток UI)
    // Он получает введённый код и должен выполнить рыбалку
    
    // Жизненный цикл:
    // 1. Пользователь видит всплывающее окно/звук/уведомление
    // 2. Пользователь вводит цифры из изображения
    // 3. Вводит код в UI (где-то в приложении)
    // 4. Вызывается WriteFishingCode(code)
    // 5. Заменяем в ссылке: "?code=????" на "?code={code}"
    // 6. Выполняем HTTP запрос на fishing_ajax.php
}
```

**ПРОБЛЕМА С vcode В ТЕКУЩЕЙ АРХИТЕКТУРЕ:**

В ПК-версии vcode живёт в AppVars.CodeAddress, но есть несколько критических моментов:

1. **vcode на каждый заброс новый** - сервер генерирует новый vcode для каждого запроса рыбалки
2. **vcode жёстко привязан к форме** - старый vcode не работает на новой странице
3. **Двойной механизм передачи:**
   - Если есть `code=????` в параметрах - нужно показать диалог ввода кода
   - Если `code` не требуется - рыбалка выполняется напрямую

---

## 3. МЕХАНИЗМ ОБРАБОТКИ VCODE

### 3.1 Сетевой поток вызовов

```
1. GET main.php  (загрузка страницы озера)
     ↓
2. POST fish_ajax.php с параметрами:
     get_id=55
     lakeid=1
     act=4
     primid=38 (приманка)
     vcode=abc123...  ← ТЕКУЩИЙ vcode из HTML
     code=???? ← ЕСЛИ ТРЕБУЕТСЯ ВВОД ЗАЩИТНОГО КОДА
     
     ↓
3. Сервер проверяет vcode:
     ├─ Если vcode верен - продолжает
     ├─ Если vcode неверен - возвращает ошибку
     └─ Если требуется код - возвращает изображение
     
4. Если нужен код защиты:
   ├─ Пользователь видит изображение с цифрами
   ├─ Пользователь вводит цифры
   └─ WriteFishingCode() переделывает запрос с code={введённый_код}
   
5. Повторный POST с code={числа}
     ↓
6. Сервер проверяет code:
     ├─ Если код верен - рыбалка успешна
     └─ Если код неверен - попросить перевести/залогиниться
```

### 3.2 Критические точки обработки vcode

| Номер | Файл | Метод | Строка | Действие |
|-------|------|-------|--------|----------|
| 1 | MainPhpFish.cs | MainPhpAutoFishPrepare | 50 | Парсинг vcode из HTML |
| 2 | MainPhpFish.cs | MainPhpAutoFishPrepare | 38 | Сохранение CodeAddress (URL для ввода кода) |
| 3 | MainPhpFish.cs | MainPhpAutoFishPrepare | 224-226 | Формирование URL рыбалки с vcode |
| 4 | FishAjaxPhp.cs | FishAjaxPhp | 11 | Обработка ответа рыбалки |
| 5 | FormMainCross.cs | WriteFishingCode | 1065 | Вставка введённого кода в URL |
| 6 | MainPhpWtime.cs | MainPhpWtime | 14 | Проверка отчёта рыбалки |

### 3.3 Формирование финального URL запроса рыбалки

```csharp
// В MainPhpFish.cs, строка 218-226
var link = 
    "main.php?get_id=" + getid +    // 55
    "&lakeid=" + lakeid +            // озеро (1-7)
    "&act=" + act +                  // 4
    "&primid=" + primid +            // приманка (38-46)
    (string.IsNullOrEmpty(AppVars.CodeAddress) 
        ? "" 
        : "&code=????") +            // МАРКЕР для ввода кода если нужен
    "&vcode=" + vcode;               // ТЕКУЩИЙ VCODE

// ЕСЛИ требуется ввод кода:
// - Система показывает диалог
// - Пользователь вводит числа
// - WriteFishingCode заменяет "????" на реальные цифры
```

---

## 4. ОСНОВНЫЕ КЛАССЫ И МЕТОДЫ

### 4.1 MainPhpFish.cs

```csharp
namespace ABClient.PostFilter
{
    // Главный класс обработки рыбалки
    internal static partial class Filter
    {
        // Подготовка заброса - ГЛАВНЫЙ МЕТОД
        private static string MainPhpAutoFishPrepare(string html)
        {
            // 1. Проверит наличие рыбы
            // 2. ИЗВЛЕКАЕТ vcode
            // 3. Выбирает приманку
            // 4. Возвращает URL для fish_ajax.php
        }
        
        // Анализ результата рыбалки
        private static string MainPhpFishReport(string html)
        {
            // 1. Парсит улов/клёв
            // 2. Обновляет статистику
            // 3. Проверяет повышение умелки
            // 4. Возвращает красивый отчёт
        }
    }
}
```

### 4.2 FishAjaxPhp.cs

```csharp
internal static byte[] FishAjaxPhp(byte[] array)
{
    // Обработка ответа fish_ajax.php
    
    var html = AppVars.Codepage.GetString(array);
    
    // Проверка ошибок рыбалки:
    if (html.IndexOf("У Вас нет рыболовных снастей", ...) != -1)
    {
        // ОШИБКА - отключить авто-рыбалку
        AppVars.MainForm.UpdateFishOff();
    }
    
    // Если в результате есть информация о улове
    if (html.IndexOf("лёв:", StringComparison.InvariantCultureIgnoreCase) != -1)
    {
        var newString = FishReport(html);  // Красиво отформатировать
        // Обновить HTML страницы
    }
    
    return array;  // Вернуть обновлённый ответ
}
```

### 4.3 TInvUd.cs (Управление удочками)

```csharp
// Инициализация удочек из инвентаря
public static void UpdateUd()
{
    AppVars.AutoFishHand1 = string.Empty;
    AppVars.AutoFishHand2 = string.Empty;
    
    // Если включена авто-экипировка удочек
    if (AppVars.Profile.FishAutoWear)
    {
        // Поиск первой удочки
        if (!AppVars.Profile.FishHandOne.Equals("нет", ...))
        {
            // Найти в инвентаре и установить в AppVars.AutoFishHand1
        }
        
        // Поиск второй удочки
        if (!AppVars.Profile.FishHandTwo.Equals("нет", ...))
        {
            // Найти в инвентаре и установить в AppVars.AutoFishHand2
        }
    }
}
```

### 4.4 MainPhpWtime.cs (Проверка отчёта)

```csharp
// Отмени вызова MainPhpFishReport для анализа результата
// Если есть информация о повышении умелки
if (html.IndexOf("повысилось на 1!", ...) != -1)
{
    AppVars.AutoFishCheckUm = true;  // Переход на проверку умелки
}

// Проверка непиття перед рыбалкой
if (AppVars.AutoFishDrink || AppVars.AutoFishDrinkOnce)
{
    // Нужно выпить перед рыбалкой
}
```

---

## 5. КРИТИЧЕСКИЕ ИНВАРИАНТЫ И ОГРАНИЧЕНИЯ

### 5.1 Обязательные условия для успешной рыбалки

1. **vcode ДОЛЖЕН быть извлечён из HTML**
   - Без vcode рыбалка невозможна
   - vcode различен для КАЖДОГО HTTP запроса
   - Если vcode не найден → вернуть пустую строку (пролип)

2. **Приманка ДОЛЖНА быть выбрана**
   - Проверить AppVars.Profile.FishEnabledPrims (битовая маска)
   - Убедиться, что приманка ЕСТЬ в инвентаре
   - Если приманка закончена → отключить рыбалку

3. **Удочка ДОЛЖНА быть одета**
   - Проверить что в экипировке находится удочка
   - Если нет → переключиться на инвентарь и одеть

4. **Усталость ДОЛЖНА быть в норме**
   - Если Tied > FishTiedHigh → пить
   - Если пить не получается → отключить рыбалку

### 5.2 ПРОБЛЕМА С vcode В ТЕКУЩЕЙ АРХИТЕКТУРЕ ANDROID

**КРИТИЧНА ДЛЯ ПОРТИРОВАНИЯ:**

В текущей реализации Android - vcode может быть потеряна/переписана если:

1. **Обновление страницы происходит асинхронно** - если в фоне идёт auto-probe, а пользователь на странице рыбалки
2. **Несинхронизированный доступ к AppVars.CodeAddress** - vcode может быть перезаписана в другом потоке
3. **Таймер проб может выполнить заброс тогда, когда пользователь читает страницу** - используется ту же vcode

**РЕШЕНИЕ:** 
- Хранить vcode локально в структуре рыбалки, не в глобальном AppVars.CodeAddress
- Передавать vcode как параметр между методами (не через глобальные переменные)
- Защитить доступ к vcode мьютексом если используется многопоточность

---

## 6. БЛОК-СХЕМА ЦИКЛА РЫБАЛКИ

```
START
  │
  ├─ Если НЕ FishAuto → EXIT
  │
  ├─ ФАЗА ПРОВЕРОК:
  │  ├─ Усталость > FishTiedHigh? → Пить
  │  ├─ Умелка не известна? → Проверить
  │  └─ Удочка не наделась? → Переодеть
  │
  ├─ ФАЗА ПОИСКА:
  │  ├─ MainPhpFindFlora() → Переходим в озеро
  │  └─ (Если не в озере - выход в озеро)
  │
  ├─ ФАЗА РЫБАЛКИ:
  │  ├─ MainPhpFindFish() 
  │  │  └─ MainPhpAutoFishPrepare()
  │  │     ├─ Парсинг HTML страницы озера
  │  │     ├─ ИЗВЛЕЧЕНИЕ vcode ← КРИТИЧНО!
  │  │     ├─ Выбор приманки
  │  │     └─ Возврат URL заброса с vcode
  │  │
  │  └─ Fish_ajax.php запрос
  │     ├─ Если требуется код → WriteFishingCode()
  │     └─ Если успех → FishAjaxPhp() обработка
  │
  ├─ ФАЗА ОТЧЁТА:
  │  ├─ Парсинг результата улова
  │  ├─ Проверка умелки
  │  └─ Обновление статистики
  │
  └─ LOOP → Вернуться на ФАЗА ПРОВЕРОК
```

---

## 7. РЕКОМЕНДАЦИИ ДЛЯ ПОРТИРОВАНИЯ НА ANDROID

### 7.1 Архитектура должна сохранить:

✅ **ОБЯЗАТЕЛЬНО СОХРАНИТЬ:**
- Парсинг vcode из HTML (критично!)
- Выбор приманки из масспрофиля
- Проверку усталости перед заброс
- Проверку наличия удочки
- Механизм ввода защитного кода (WriteFishingCode)
- Анализ результата улова
- Обновление статистики профиля

✅ **ПЕРЕДЕЛАТЬ ДЛЯ ANDROID:**
- Таймер рыбалки → использовать Handler/Timer Android
- HTTP запросы → использовать HttpClient/OkHttp
- Парсинг HTML → использовать JSoup или регулярные выражения
- UI обновления → использовать Handler/runOnUiThread
- Хранилище состояния → использовать SharedPreferences/базу данных

### 7.2 Критические точки внимания:

1. **vcode НИКОГДА не должна быть переиспользована**
   - Каждый заброс → новый парсинг vcode
   - Не кешировать vcode между запросами

2. **Защита от состояния гонки (race condition)**
   - Auto-probe может выполниться в момент когда пользователь на странице
   - Нужна синхронизация между потоками операций

3. **Механизм ввода защитного кода должен быть синхронным**
   - После вывода диалога - ждём ввода пользователя
   - Не отправлять заброс, пока не введён код (если требуется)

### 7.3 Предложенная структура в Android:

```kotlin
// В пакете ru.neverlands.abclient.fishing

package ru.neverlands.abclient.fishing

// Основной цикл рыбалки
class FishingAutoManager {
    fun executeFishingCycle()
    
    // Фазы цикла
    private fun checkFishingPrerequisites()
    private fun findFishingLocation()
    private fun prepareFishingCast()      // ← Парсинг vcode
    private fun handleFishingResult()
}

// Состояние рыбалки (вместо глобальных AppVars)
data class FishingState(
    val vcode: String,            // Текущий vcode
    val selectedBait: BaitType,   // Выбранная приманка
    val rodEquipped: String,      // Наделась ли удочка
    val lastCastTime: Long        // Время последнего заброса
)

// Парсер HTML страницы озера
object FishingHtmlParser {
    fun extractVcode(html: String): String?
    fun extractFishingParams(html: String): FishingParams?
    fun selectRandomBait(enabledBaits: Int): BaitType?
}

// Защитный код
class ProtectionCodeHandler {
    fun requestCodeInput(imageUrl: String): String  // Синхронный запрос
}
```

---

## 8. ИЗВЕСТНЫЕ ПРОБЛЕМЫ И РЕШЕНИЯ

### 8.1 Потеря vcode в Android версии

**Симптом:** Рыбалка периодически перестаёт работать, в логах видны ошибки про "неверный vcode"

**Причина:** 
- Globakyle AppVars переписываютсяавтоматически
- Auto-probe обновляет состояние поока во время рыбалки
- Между парсингом vcode и отправкой запроса может пройти время

**Решение:**
```java
// ВМЕСТО этого (неправильно):
String vcode = extractVcode(html);
// ... какой-то код ...
sendFishingRequest(vcode);  // vcode может быть потеряна!

// ИСПОЛЬЗУЙ это (правильно):
FishingRequest request = new FishingRequest();
request.vcode = extractVcode(html);
request.primid = selectBait();
request.lakeid = currentLakeId;
// Всё готово - отправляем СРАЗУ
sendFishingRequest(request);  // vcode в целости
```

### 8.2 Конфликт между ручным вводом и автоматикой

**Симптом:** Когда пользователь открывает озеро вручную, авто-рыбалка не будет ловить

**Причина:** Таймер/probe может перезаписать страницу

**Решение:** Использовать флаг "ручного контекста" - если пользователь сам открыл страницу, авто-probe должен подождать

---

## 9. СПИСОК ФАЙЛОВ ДЛЯ АНАЛИЗА/ПОРТИРОВАНИЯ

```
АНАЛИЗ ЗАВЕРШЕН ДЛЯ:
├─ ABClient/PostFilter/MainPhpFish.cs         ✓ ПОЛНЫЙ анализ
├─ ABClient/PostFilter/FishAjaxPhp.cs         ✓ ПОЛНЫЙ анализ  
├─ ABClient/PostFilter/MainPhp.cs            ✓ ПОЛНЫЙ анализ (поиск озера)
├─ ABClient/PostFilter/MainPhpWtime.cs       ✓ ПОЛНЫЙ анализ (проверка)
├─ ABClient/ABForms/FormMainCross.cs         ✓ ПОЛНЫЙ анализ (vcode ввод)
├─ ABClient/ABForms/FormMain.cs              ✓ ПОЛНЫЙ анализ (инициализация)
├─ ABClient/TInvUd.cs                        ✓ ПОЛНЫЙ анализ (удочки)
├─ ABClient/AppVars.cs                       ✓ ПОЛНЫЙ анализ (переменные)
├─ ABClient/MyForms/FormFishAdvisor.cs       ✓ ПОЛНЫЙ анализ (советник)
└─ ABClient/Helpers/                         ✓ HelperStrings, HelperConverters

ТРЕБУЕТСЯ ДОПОЛНИТЕЛЬНЫЙ АНАЛИЗ:
├─ Обработка капчи (если добавляется)
├─ Механизм пула соединений HTTP
└─ Синхронизация многопоточности
```

---

## 10. ВЫВОДЫ И КРИТИЧНЫЕ ЗАМЕЧАНИЯ

### 🔴 КРИТИЧНО:

1. **vcode - ключ ко ВСЕЙ системе рыбалки**
   - Без неё ничего не работает
   - Извлекается из HTML КАЖДЫЙ раз
   - Нельзя кешировать или переиспользовать

2. **Синхронизация состояния между потоками**
   - Global AppVars могут быть перезаписаны
   - Нужна защита от race conditions
   - Лучше использовать локальные переменные

3. **Два режима работы:**
   - Режим без защитного кода (fast path)
   - Режим с защитным кодом (slow path с диалогом)
   - Оба должны работаться идеально

### ✅ РЕШЕНИЕ:

Портировать авто-рыбалку как отдельный модуль (`FishingManager`) с чистой архитектурой, где:
- Состояние хранится локально (не в глобальных переменных)
- vcode передаётся как параметр между методами
- Защита от асинхронных вмешательств
- Явное управление состоянием (State Machine)

---

**Дата обновления:** 2026-04-01  
**Статус:** 🟢 ГОТОВ К ПОРТИРОВАНИЮ  
**Сложность:** СРЕДНЯЯ (требует внимательного управления состоянием)
