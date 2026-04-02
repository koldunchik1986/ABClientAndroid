# Примеры кода VCode Management в ABClient (C#)

## 1. ГЛАВНАЯ ХУБ ОБРАБОТКИ (Filter.cs)

```csharp
// PostFilter/Filter.cs - ГЛАВНЫЙ КЛАСС

internal static partial class Filter
{
    internal static byte[] PreProcess(string address, byte[] array)
    {
        // Предварительная обработка перед основным Process
        return array;
    }

    internal static byte[] Process(string address, byte[] array)
    {
        // ✅ ВСЕ интернет-ответы проходят через эту функцию!
        
        if (string.IsNullOrEmpty(address))
            return null;

        var html = Russian.Codepage.GetString(array);

        // Распределение по типам:
        if (address.Contains(".js"))
        {
            if (address.Contains("/js/fight_v"))
                return FightJs(array);  // Парсит VCode боя
            
            if (address.EndsWith("/js/svitok.js", StringComparison.OrdinalIgnoreCase))
                return SvitokJs(array);  // Парсит VCode зелий
        }

        if (address.StartsWith("http://www.neverlands.ru/main.php", StringComparison.OrdinalIgnoreCase))
        {
            AppVars.NextCheckNoConnection = DateTime.Now.AddMinutes(5);
            return MainPhp(address, array);  // Парсит VCode для всех действий
        }

        if (address.StartsWith("http://www.neverlands.ru/gameplay/ajax/fish_ajax.php", StringComparison.OrdinalIgnoreCase))
        {
            return FishAjaxPhp(array);  // Парсит VCode рыбалки
        }

        return array;  // Если не обработано - вернуть как есть
    }
}
```

---

## 2. ПАРСИНГ VCode ДЛЯ РЫБАЛКИ (MainPhpFish.cs)

```csharp
// PostFilter/MainPhpFish.cs

internal static partial class Filter
{
    private static string MainPhpFish(string html)
    {
        // Получаем параметры рыбалки
        var act = HelperStrings.SubString(html, "=act value=", ">");
        if (string.IsNullOrEmpty(act))
        {
            return string.Empty;
        }

        // ✅ ГЛАВНЫЙ МОМЕНТ: Парсим VCode из текущего HTML
        var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
        if (string.IsNullOrEmpty(vcode))
        {
            // ✅ ВАЛИДАЦИЯ: Если нет VCode - отмена действия
            return string.Empty;
        }

        var lakeid = HelperStrings.SubString(html, "=lakeid value=", ">");
        if (string.IsNullOrEmpty(lakeid))
        {
            return string.Empty;
        }

        AppVars.AutoFishMassa = HelperStrings.SubString(html, "<b>Масса Вашего инвентаря: ", "</b>");
        if (string.IsNullOrEmpty(AppVars.AutoFishMassa))
        {
            return string.Empty;
        }

        // ... выбираем приманку, определяем primid ...

        // ✅ ИСПОЛЬЗОВАНИЕ: VCode используется сразу же в ссылке
        var temp =
            "<input type=radio name=primid value=" +
            primid +
            "></td><td bgcolor=#FFFFFF><img src=http://image.neverlands.ru/tools/" +
            pr +
            ".gif width=60 height=60></td><td bgcolor=#FFFFFF align=center class=nickname><b>" +
            l2[0] +
            "</b></td><td bgcolor=#FFFFFF align=center class=nickname><b>";
        
        var pos = html.IndexOf(temp, StringComparison.OrdinalIgnoreCase);
        if (pos != -1)
        {
            AppVars.AutoFishLikeId = primid;
            // ... обработка ...
        }

        // ✅ ФИНАЛЬНЫЙ РЕЗУЛЬТАТ: Возвращаем ссылку с VCode
        return 
            "<form method=POST action=\"main.php\">" +
            "<input type=hidden name=post_id value=55>" +
            "<input type=hidden name=get_id value=55>" +
            "<input type=hidden name=vcode value=\"" + vcode + "\">" +  // ← VCode!
            "<input type=hidden name=lakeid value=\"" + lakeid + "\">" +
            "<input type=hidden name=act value=4>" +
            "<input type=hidden name=primid value=" + primid + ">" +
            "<input type=submit value=\"Ловить\">" +
            "</form>";
    }
}
```

**Ключевые моменты:**
1. Line 4: `var vcode = HelperStrings.SubString(...)`  - **парсим**
2. Line 6-9: Валидация `if (string.IsNullOrEmpty(vcode))`  - **проверяем**
3. Line xxx: `"<input type=hidden name=vcode value=\"" + vcode + "\">"` - **используем**
4. Нет: `AppVars.StoredVCode = vcode;` - **не кэшируем**

---

## 3. ПАРСИНГ VCode ДЛЯ БОЯ (FightJs.cs)

```csharp
// PostFilter/FightJs.cs

private static byte[] FightJs(byte[] array)
{
    var sb = new StringBuilder(Helpers.Russian.Codepage.GetString(array));

    // ... модификация HTML/JS ...

    // ✅ VCode для боя ПАРСИРУЕТСЯ ИЗ РЕЗУЛЬТАТА (ss[0])
    sb.AppendLine(
        "function AutoSubmit(result)" +
        "{" +
        @" var ss = result.split(""|"");" +  // Разбиваем результат боевого API
        "  if (ss.length > 8)" +
        "  {" +
        "    var form_node = d.getElementById('form_main');" +
        "    form_node.appendChild(AddElement('post_id','7'));" +
        
        // ✅ VCode из первого элемента результата
        "    form_node.appendChild(AddElement('vcode',ss[0]));  // ← ss[0] = VCode" +
        
        "    form_node.appendChild(AddElement('enemy',ss[1]));" +
        "    form_node.appendChild(AddElement('group',ss[2]));" +
        "    form_node.appendChild(AddElement('inf_bot',ss[3]));" +
        "    form_node.appendChild(AddElement('lev_bot',ss[4]));" +
        "    form_node.appendChild(AddElement('ftr',ss[5]));" +
        "    form_node.appendChild(AddElement('inu',ss[6]));" +
        "    form_node.appendChild(AddElement('inb',ss[7]));" +
        "    form_node.appendChild(AddElement('ina',ss[8]));" +
        "    fight_f.submit();  // ✅ Отправка формы с VCode" +
        "  }" +
        "}");

    return Helpers.Russian.Codepage.GetBytes(sb.ToString());
}
```

**Структура результата боевого API:**
```
Result format: "VCode|Enemy|Group|InfBot|LevBot|Ftr|Inu|Inb|Ina..."
                 [0]   [1]    [2]    [3]    [4]   [5] [6] [7] [8]...

ss[0] = VCode для следующего хода (парсится на лету)
```

---

## 4. ПАРСИНГ VCode ДЛЯ БЫСТРЫХ ДЕЙСТВИЙ (MainPhpFast.cs)

```csharp
// PostFilter/MainPhpFast.cs - Применение телепорта

private static string MainPhpFastTeleport(string html)
{
    // Ищем w28_form() вызовы в HTML
    const string patternW28Form = "w28_form(";
    int p1 = 0;
    
    while (p1 != -1)
    {
        p1 = html.IndexOf(patternW28Form, p1, StringComparison.OrdinalIgnoreCase);
        if (p1 == -1)
            break;

        p1 += patternW28Form.Length;
        var p2 = html.IndexOf(")", p1, StringComparison.OrdinalIgnoreCase);
        if (p2 == -1)
            continue;

        // Парсим параметры из: w28_form('vcode', 'wuid', 'wsubid', 'wsolid')
        var args = html.Substring(p1, p2 - p1);
        if (string.IsNullOrEmpty(args))
            continue;

        var arg = args.Split(',');
        if (arg.Length < 4)
            continue;

        // ✅ ПАРСИМ VCode
        var vcode = arg[0].Trim(new[] { '\'' });  // Удаляем кавычки: 'vcode' → vcode
        var wuid = arg[1].Trim(new[] { '\'' });
        var wsubid = arg[2].Trim(new[] { '\'' });
        var wsolid = arg[3].Trim(new[] { '\'' });

        if (!wsubid.Equals("22"))  // Проверяем тип действия (22 = телепорт)
            continue;

        // ✅ ИСПОЛЬЗУЕМ VCode В ФОРМЕ
        var sb = new StringBuilder();
        sb.Append(HelperErrors.Head());
        sb.Append("Используем телепорт");
        sb.Append(AppVars.FastNick);
        sb.Append("...");
        sb.Append("<form action=main.php method=POST name=ff>");

        sb.Append(@"<input name=post_id type=hidden value=""25"">");
        
        // ✅ VCode в hidden input
        sb.Append(@"<input name=vcode type=hidden value=""");
        sb.Append(vcode);  // ← Вот VCode!
        sb.Append(@""">");

        sb.Append(@"<input name=wuid type=hidden value=""");
        sb.Append(wuid);
        sb.Append(@""">");

        // ... остальные параметры ...

        // ✅ AUTO-SUBMIT ФОРМА
        sb.Append(
            @"</form>" +
            @"<script language=""JavaScript"">" +
            @"document.ff.submit();" +
            @"</script></body></html>");

        return sb.ToString();  // ← Возвращаем HTML с VCode
    }

    return null;
}
```

**Ключевые моменты:**
1. `var vcode = arg[0].Trim(...)`  - **парсим** из параметров
2. `sb.Append(vcode);` - **используем** в hidden input
3. `document.ff.submit();` - **отправляем** форму с VCode
4. Время жизни vcode: **только в этой функции**

---

## 5. ПАРСИНГ VCode ДЛЯ ВЕЩЕЙ (TInvUd.cs - ParsedDressed)

```csharp
// TInvUd.cs

internal class ParsedDressed
{
    internal bool Valid;
    internal string Wid;
    internal string Vcod;  // ← Хранилище VCode вещи
    internal bool Empty1, Empty2;
    internal bool InRightSlot;
    internal string Hand1, Hand2;

    // Конструктор парсит HTML
    internal ParsedDressed(string html)
    {
        Valid = false;

        // ... инициализация ...

        // ПАРСИМ SLOTS_INV() ФУНКЦИЮ
        var slotsinv = HelperStrings.SubString(html, "slots_inv(", ");");
        if (string.IsNullOrEmpty(slotsinv))
        {
            // ... обработка альтернативного слота ...
            return;
        }

        // Разбиваем параметры slots_inv(): (inv, wid_inv, [MAIN], [WID], [VCOD], [DLG])
        var pslots = slotsinv.Split(',');
        if (pslots.Length < 6)
        {
            return;
        }

        // pslots[2] = главные данные вещей
        // pslots[3] = данные ID вещей
        // pslots[4] = ✅ ДАННЫЕ VCode ВЕЩЕЙ
        // pslots[5] = данные долговечности

        var slmain = pslots[2].Split('@');
        if (slmain.Length < 13)
        {
            return;
        }

        var slwid = pslots[3].Split('@');
        if (slwid.Length < 3)
        {
            return;
        }

        Wid = slwid[2];

        // ✅ ГЛАВНЫЙ МОМЕНТ: Парсим VCode
        var slvcod = pslots[4].Split('@');  // pslots[4] содержит VCode данные
        if (slvcod.Length < 3)
        {
            return;  // ✅ ВАЛИДАЦИЯ: если нет VCode - отмена
        }

        Vcod = slvcod[2];  // ← Сохраняем VCode вещи

        // ... остальной парсинг ...

        Valid = true;  // Валиден = у нас есть Wid, Vcod и другие данные
    }

    // Использование VCode:
}

// В MainPhpWear.cs - использование ParsedDressed.Vcod
internal static partial class Filter
{
    private static string MainPhpWear(string html)
    {
        var ud = new ParsedDressed(html);  // Парсим, включая Vcod

        if (!ud.Valid)
        {
            return string.Empty;  // Если не валидна - отмена
        }

        // ... проверяем, нужно ли что-то снимать ...

        // ✅ ИСПОЛЬЗУЕМ VCode ВЕЩИ
        return BuildRedirect(
            "Снимаем " + ud.Hand1,
            "main.php?get_id=57&uid=" + ud.Wid + "&s=0&vcode=" + ud.Vcod  // ← VCode здесь!
        );
    }
}
```

**Структура slots_inv():**
```javascript
slots_inv(
    main_inv,
    wid_inv,
    [main_item@prop1@prop2@... x 13 items],  // pslots[2] = MAIN
    [wid_item@wid2@wid3@... x 3 items],      // pslots[3] = WID
    [vcode@vcode2@VCODE_ITEM@...],           // pslots[4] = VCOD (pslots[4][2] = VCode вещи)
    [dlg@dlg2@dlg3@... x 13 items]          // pslots[5] = DLG
);

Структура pslots[4]:
  ["vcode", "vcode_item_1", "VCODE_ВЕЩИ_1", ...]
    [0]      [1]             [2]             ...
```

---

## 6. УТИЛИТА ПАРСИНГА (HelperStrings.cs)

```csharp
// MyHelpers/HelperStrings.cs

public static class HelperStrings
{
    // ✅ ОСНОВНАЯ ФУНКЦИЯ ДЛЯ ПАРСИНГА VCode
    internal static string SubString(string html, string s1, string s2)
    {
        if (string.IsNullOrEmpty(html) || string.IsNullOrEmpty(s1) || string.IsNullOrEmpty(s2))
            return null;

        // Находим начало маркера s1
        int p1 = html.IndexOf(s1);
        if (p1 == -1)
            return null;  // ← Маркер не найден = VCode не найден

        // Находим конец маркера s2 после начала
        int p2 = html.IndexOf(s2, p1 + s1.Length);
        if (p2 == -1)
            return null;  // ← Конец не найден

        // Извлекаем текст между маркерами
        return html.Substring(p1 + s1.Length, p2 - p1 - s1.Length);
    }

    // примеры использования:
    /*
    SubString(html, "=vcode value=", ">")
    → Ищет: значение между "=vcode value=" и ">"
    → Return: "abc123def456"
    
    SubString(html, "slots_inv(", ");")
    → Ищет: содержимое между "slots_inv(" и ");"
    → Return: "inv,wid,[...],[...]"
    
    SubString(html, "var vcode = [[", "]];")
    → Ищет: содержимое между "var vcode = [[" и "]];"
    → Return: "код1", "код2", ...
    */
}

// Пример для VCode рыбалки:
var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
if (string.IsNullOrEmpty(vcode))
    return "ERROR: VCode not found";  // Валидация
else
    return "OK: vcode = " + vcode;
```

---

## 7. ПОЛНЫЙ ЦИКЛ: ОТ ЗАПРОСА К ИСПОЛЬЗОВАНИЮ

```csharp
// ПРИМЕР 1: Рыбалка

// 1️⃣ ЗАПРОС
browser.Navigate("main.php?get_id=55");  // Переходим на рыбалку

// 2️⃣ ОТВЕТ ОБРАБАТЫВАЕТСЯ
// ↓ Filter.Process() вызывается
// ↓ Определяет: это main.php
// ↓ Вызывает: MainPhp()
// ↓ Определяет: есть рыбалка
// ↓ Вызывает: MainPhpFish()

// 3️⃣ ПАРСИНГ VCode (MainPhpFish.cs:50)
var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
// ← vcode = "26ed9c9afae6128894a60e1b8275ebf4"

// 4️⃣ ВАЛИДАЦИЯ
if (string.IsNullOrEmpty(vcode))
    return string.Empty;  // Отмена, если нет

// 5️⃣ ВЫБОР ПРИМАНКИ И ОЗЕРА
var lakeid = HelperStrings.SubString(html, "=lakeid value=", ">");
var primid = "38";  // Выбрали хлеб

// 6️⃣ ПОСТРОЕНИЕ ССЫЛКИ С VCode
string link = $"main.php?get_id=55&lakeid={lakeid}&act=4&primid={primid}&vcode={vcode}";
// ← link = "main.php?get_id=55&lakeid=1&act=4&primid=38&vcode=26ed9c9afae6128894a60e1b8275ebf4"

// 7️⃣ НЕ СОХРАНЯЕМ VCode
// ✅ Правильно: нет `AppVars.CurrentVCode = vcode;`
// ✅ VCode живет только в локальной переменной

// 8️⃣ ИСПОЛЬЗУЕМ НЕМЕДЛЕННО
return link;  // Возвращаем ссылку с VCode

// 9️⃣ СЛЕДУЮЩИЙ ЗАПРОС → НОВЫЙ VCode
// Когда пользователь кликает ссылку → новый main.php ответ → новый VCode парсится
```

---

## ВЫВОД

**VCode в C# версии:**
1. ✅ Парсится из каждого HTML ответа
2. ✅ Хранится в локальной переменной (не в static)
3. ✅ Используется немедленно в том же методе
4. ✅ Валидируется перед использованием
5. ✅ Обрабатывается в единной точке (Filter.Process())
6. ✅ НЕ кэшируется между запросами

**Главная идея:** VCode - это **одноразовая** сессионная переменная, а не постоянное хранилище!
