# Анализ системы управления VCode в ABClient (C#)

## VCode Management в C#

### Класс: ParsedDressed (TInvUd.cs)
- **Хранилище:** Поле `Vcod` типа `string` в классе `ParsedDressed`
- **Назначение:** Хранит код защиты для конкретного предмета инвентаря

### Архитектура системы

#### 1. **Где хранится VCode**

VCode **НЕ хранится** в одном центральном месте. Система использует **распределенный подход**:

```
AppVars (Static) - НЕ содержит VCode
    ├─> FishAuto параметры (AutoFishHand1, AutoFishMassa и т.д.)
    ├─> FightLink
    └─> LastBoiLog (боевая информация, но не VCode)

Локальные переменные по контексту:
    ├─> PostFilter/MainPhpFish.cs - парсит vcode для рыбалки
    ├─> PostFilter/MainPhpFast.cs - парсит vcode для зелий/телепортов
    ├─> PostFilter/FightJs.cs - парсит vcode для боя
    ├─> TInvUd.cs (ParsedDressed) - хранит Vcod вещи
    └─> Lez/LezFight.cs - хранит vcode боя
```

#### 2. **Update механизм: Как обновляется VCode**

VCode обновляется на **каждый HTML ответ сервера** через Filter класс:

**Главный поток обработки:**

```csharp
// PostFilter/Filter.cs - Main Process Method
internal static byte[] Process(string address, byte[] array)
{
    // 1. Декодируем HTML
    var html = Russian.Codepage.GetString(array);
    
    // 2. Распределяем по типам
    if (address.Contains("/js/fight_v"))
        return FightJs(array);  // Парсит новый VCode из боя
    
    if (address.StartsWith("http://www.neverlands.ru/main.php"))
        return MainPhp(address, array);  // Парсит VCode из main.php
    
    if (address.StartsWith(".../gameplay/ajax/fish_ajax.php"))
        return FishAjaxPhp(array);  // Парсит VCode рыбалки
}

// MainPhp.cs - Парсинг VCode для рыбалки
private static string MainPhpFish(string html)
{
    // Парсим VCode из HTML ответа:
    var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
    if (string.IsNullOrEmpty(vcode))
        return string.Empty;  // Ошибка - VCode потерян!
    
    // Используем VCode в ссылке:
    var link = $"main.php?get_id=55&lakeid={lakeid}&act=4&primid={primid}&vcode={vcode}";
    
    return link;  // Возвращаем ссылку с новым VCode
}
```

#### 3. **Как система избегает потери VCode при контекстных переключениях**

**Метод 1: Парсинг VCode из каждого ответа**
```csharp
// TInvUd.cs - ParsedDressed
internal ParsedDressed(string html)
{
    var slvcod = pslots[4].Split('@');
    if (slvcod.Length < 3)
        return;  // Валидация - если VCode нет, парсинг не проходит
    
    Vcod = slvcod[2];  // Сохраняем новый VCode
}
```

**Метод 2: Использование VCode сразу после парсинга**
```csharp
// MainPhpWear.cs - VCode используется НЕМЕДЛЕННО после получения
var link = $"main.php?get_id=57&uid={ud.Wid}&s=0&vcode={ud.Vcod}";
// VCode не кэшируется долго - используется в текущем контексте
```

**Метод 3: Запрашивание инвентаря перед использованием**
```csharp
// MainPhp.cs - Перед использованием вещи система переходит на инвентарь
// Это гарантирует, что VCode будет свежим
var invHtml = MainPhpFindInv(html, "&im=0&wca=85");
if (!string.IsNullOrEmpty(invHtml))
{
    html = invHtml;  // Загружаем инвентарь со свежим VCode
    goto end;
}
```

#### 4. **Единый центр управления: Filter класс (PostFilter/Filter.cs)**

Хотя VCode не хранится централизованно, **обработка всех ответов** происходит в одной точке:

```csharp
internal static byte[] Process(string address, byte[] array)
{
    // ВСЕ HTML ответы проходят через эту функцию
    // Отсюда распределяются:
    // - main.php → MainPhp() → парсит VCode
    // - fight.js → FightJs() → парсит VCode боя  
    // - fish_ajax.php → FishAjaxPhp() → парсит VCode рыбалки
    // - быстрые действия → MainPhpFast() → парсит VCode зелий
}
```

**Это гарантирует:**
- ✅ Последовательность обновлений VCode
- ✅ Отсутствие гонки между модулями
- ✅ Единая точка валидации

#### 5. **Синхронизация VCode между модулями**

Каждый модуль парсит **свой VCode** из ответа сервера:

```
Модуль Рыбалка:
  MainPhpFish.cs:50
  var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
  → используется в ссылке: main.php?...&vcode={vcode}

Модуль Бой:
  FightJs.cs:78
  form_node.appendChild(AddElement('vcode', ss[0]));
  → ss[0] парсится из ответа боевого сервера
  → используется в form_main

Модуль Быстрые действия:
  MainPhpFast.cs:146
  var vcode = arg[0].Trim(new[] { '\'' });
  → Парсится из onclick="w28_form('vcode_value', ...)"
  → используется в POST форме

Модуль Вещи:
  TInvUd.cs:131
  Vcod = slvcod[2];  // Из slots_inv() функции
  → используется в MainPhpWear.cs:73
  → main.php?get_id=57&uid=...&vcode={ud.Vcod}
```

**Синхронизация достигается через:**
1. **Каждый модуль запрашивает main.php** перед действием
2. **main.php содержит актуальный VCode**
3. **VCode парсится "на лету"** и используется немедленно
4. **Нет кеширования VCode** между запросами к main.php

#### 6. **Механизм кэширования/восстановления VCode**

Система **минимально кэширует VCode**:

```csharp
// ParsedDressed хранит Vcod, но только в течение обработки
internal class ParsedDressed
{
    internal string Vcod;  // Живет только во время обработки HTML
    
    internal ParsedDressed(string html)
    {
        // Парсится один раз при создании объекта
        Vcod = slvcod[2];
        // Нет механизма освежения, объект одноразовый
    }
}

// Восстановление:
// - Если VCode испортился, система переходит на main.php
// - main.php всегда содержит свежий VCode в slots_inv()
// - Система перепарсирует VCode из новой страницы
```

#### 7. **Обработка ошибок "Неверный код защиты"**

Система НЕ обрабатывает явно ошибку "неверный код защиты", но предотвращает её:

```csharp
// MainPhpFish.cs - валидация перед использованием
var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
if (string.IsNullOrEmpty(vcode))
{
    return string.Empty;  // Отмена действия, если VCode не найден
}

// MainPhpFast.cs - аналогично
var vcode = arg[0].Trim(new[] { '\'' });
if (string.IsNullOrEmpty(vcode))
{
    continue;  // Пропуск, VCode не найден
}

// TInvUd.cs - ParsedDressed становится invalid
if (slvcod.Length < 3)
{
    Valid = false;  // Флаг ошибки
    return;
}
```

---

## Ключевые фрагменты кода

### 1. Парсинг VCode для рыбалки (MainPhpFish.cs:50-52)

```csharp
var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
if (string.IsNullOrEmpty(vcode))
    return string.Empty;

// Использование:
var link = $"main.php?get_id=55&lakeid={lakeid}&act=4&primid={primid}&vcode={vcode}";
```

### 2. Парсинг VCode для боя (FightJs.cs:78)

```csharp
sb.AppendLine(
    "function AutoSubmit(result)" +
    "{" +
    @" var ss = result.split(""|"");" +
    "  if (ss.length > 8)" +
    "  {" +
    "    var form_node = d.getElementById('form_main');" +
    "    form_node.appendChild(AddElement('post_id','7'));" +
    "    form_node.appendChild(AddElement('vcode',ss[0]));  // ← VCode из [0]" +
    "    form_node.appendChild(AddElement('enemy',ss[1]));" +
    "    // ... остальные параметры"
    "    fight_f.submit();" +
    "  }" +
    "}");
```

### 3. VCode для быстрых действий (MainPhpFast.cs:146, 166-167)

```csharp
var vcode = arg[0].Trim(new[] { '\'' });

// В форме:
sb.Append(@"<input name=vcode type=hidden value=""");
sb.Append(vcode);
sb.Append(@""">");
```

### 4. VCode для вещей (TInvUd.cs:125-131)

```csharp
var slvcod = pslots[4].Split('@');
if (slvcod.Length < 3)
{
    return;
}

Vcod = slvcod[2];  // Сохраняем код защиты вещи

// Использование в MainPhpWear.cs:73
return BuildRedirect(
    "Снимаем " + ud.Hand1, 
    "main.php?get_id=57&uid=" + ud.Wid + "&s=0&vcode=" + ud.Vcod
);
```

### 5. Парсинг VCode из slots_inv() функции (TInvUd.cs:28)

```csharp
internal ParsedDressed(string html)
{
    Valid = false;
    
    // Получаем: slots_inv(main_inv, wid_inv, [MAIN_DATA], [WID_DATA], [VCOD_DATA], ...)
    var slotsinv = HelperStrings.SubString(html, "slots_inv(", ");");
    if (string.IsNullOrEmpty(slotsinv))
        return;

    var pslots = slotsinv.Split(',');
    if (pslots.Length < 6)
        return;

    // pslots[4] содержит данные VCode: [@, @, VCode, ...]
    var slvcod = pslots[4].Split('@');
    if (slvcod.Length < 3)
        return;

    Vcod = slvcod[2];  // ← Вот он!
    Valid = true;
}
```

### 6. SubString утилита (HelperStrings.cs:24-33)

```csharp
internal static string SubString(string html, string s1, string s2)
{
    int p1 = html.IndexOf(s1);
    if (p1 == -1)
        return null;

    int p2 = html.IndexOf(s2, p1 + s1.Length);
    return p2 == -1 ? null : html.Substring(p1 + s1.Length, p2 - p1 - s1.Length);
}

// Пример использования для VCode:
// SubString("<input type=hidden name=vcode value=abc123>", "=vcode value=", ">")
// → "abc123"
```

---

## Диаграмма потока VCode

```
┌─────────────────┐
│  WebBrowser     │
│  (main.php)     │
└────────┬────────┘
         │ HTML с VCode в:
         │ - slots_inv()
         │ - form inputs
         │ - onclick handlers
         │
         ▼
┌─────────────────────────┐
│ PostFilter/Filter.cs    │  ← ГЛАВНАЯ ТОЧКА
│ Process() method        │     обработки ВСЕх ответов
└────┬────────────────────┘
     │ Распределение по типам
     │
  ┌──┴──┬──────┬───────┬──────┐
  │     │      │       │      │
  ▼     ▼      ▼       ▼      ▼
MainPhp FishAjax FightJs MainPhpFast MainPhpInv
  │      │       │         │        │
  ├─►MainPhpFish ├─►FaceVarsVCode ├─►MainPhpFastTeleport
  │    (рыбалка)  │ (боевой VCode)  │ (зелья, телепорты)
  │              │                 │
  └──────────────┴─────────────────┘
         │
         ▼ Каждый модуль:
     1. Парсит VCode
     2. Использует немедленно
     3. Не кэширует между запросами
```

---

## Сравнение подходов

| Аспект | CBClient (C#) | Требуется для Android |
|--------|--------------|----------------------|
| **Хранилище VCode** | Распределено (парсится на лету) | Аналогично - на лету из WebView |
| **Обновление** | После каждого HTML ответа | При каждом обновлении DOM в WebView |
| **Синхронизация** | Через Filter.Process() | Через JS injection + обработка ответов |
| **Prevention потери** | Переход на main.php перед действием | Извлечение из current HTML context |
| **Кэширование** | Минимально (только ParsedDressed) | Не рекомендуется кэшировать |
| **Ошибка "неверный VCode"** | Валидация перед использованием | Проверка наличия VCode в HTML |

---

## Выводы для Android портирования

1. **НЕ создавать статическое хранилище VCode** (типа `static String currentVCode`)
   - Это будет **источником конфликтов и потерь**
   - Каждый запрос должен парсить VCode из текущего контекста HTML

2. **Парсить VCode "на лету" перед каждым действием**
   - Извлекать из WebView DOM
   - Использовать немедленно
   - Не сохранять для "потом"

3. **Использовать единую точку обработки ответов**
   - Как Filter.Process() в C#
   - Все ответы → обработка → парсинг VCode → распределение по модулям

4. **Валидировать наличие VCode**
   - Если VCode нет → отмена действия
   - Если VCode не найден → переход на main.php для обновления контекста

5. **НЕ использовать кэширование VCode** между разными contexts
   - Только парсить из текущего HTML
   - Текущий HTML всегда содержит актуальный VCode
