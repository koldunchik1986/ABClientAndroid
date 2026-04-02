# Анализ: Извлечение VCODE в C# версии ABClient

## 1. ФОРМАТ VCODE
- **Длина:** 32 символа (32 hex characters)
- **Формат:** Только строчные буквы и цифры (0-9, a-f)
- **Примеры из реального кода:**
  - `fbcf6e1e1f13168c974a674ad355a26e`
  - `602f954468e63529e8b3834980ec2734`
  - `787337e6dbe7e7c26bc662c2b8a7eaaa`
  - `228480248dc96614aa4b22205cd3f966`

---

## 2. ОСНОВНЫЕ МЕТОДЫ ИЗВЛЕЧЕНИЯ

### Метод 1: `HelperStrings.SubString()` - Простое извлечение между маркерами

```csharp
// Из HelperStrings.cs
internal static string SubString(string html, string s1, string s2)
{
    var p1 = html.IndexOf(s1, StringComparison.OrdinalIgnoreCase);
    if (p1 == -1)
    {
        return null;
    }

    var p2 = html.IndexOf(s2, p1 + s1.Length, StringComparison.OrdinalIgnoreCase);
    return p2 == -1 ? null : html.Substring(p1 + s1.Length, p2 - p1 - s1.Length);
}
```

**Использование в коде:**
```csharp
// Извлечение vcode из скрытого поля в форме (рыбалка)
var vcode = HelperStrings.SubString(html, "=vcode value=", ">");

// Пример HTML:
// <input type=hidden name=vcode value="602f954468e63529e8b3834980ec2734">
// Результат: "602f954468e63529e8b3834980ec2734"
```

---

### Метод 2: Извлечение из JavaScript переменных с Trim()

```csharp
// Из TeleportAjax.cs - извлечение vcode из массива телепортов
var build = MyHelpers.HelperStrings.SubString(html, "var vcode = [[", "]];");
if (!string.IsNullOrEmpty(build))
{
    var sbuild = build.Split(new[] { "],[" }, StringSplitOptions.None);
    if (sbuild.Length >= 3)
    {
        var pars = sbuild[2].Split(',');
        if (pars.Length >= 2)
        {
            var vcodex = pars[1].Trim('\"');  // Удаление кавычек
            var linkx = $"main.php?get_id=56&act=10&go=up&vcode={vcodex}";
        }
    }
}

// Пример JavaScript в HTML:
// var vcode = [["...", "..."], ["...", "..."], ["...", "787337e6dbe7e7c26bc662c2b8a7eaaa"]];
```

---

### Метод 3: Извлечение из JavaScript функцио-параметров 

```csharp
// Из MainPhpCure.cs - извлечение параметров функции
// HTML: <input ... onclick="doctorform('duid','vcode','dprice','dtype','dcurs')">

var p1 = html.IndexOf("doctorform(", StringComparison.OrdinalIgnoreCase) + "doctorform(".Length;
var p2 = html.IndexOf(")", p1);
var args = html.Substring(p1, p2 - p1);

var arg = args.Split(',');
var vcode = arg[1].Trim(new[] { '\'' });  // Извлечение и Trim кавычек
```

---

### Метод 4: Извлечение из JavaScript массивов (ParseJsString)

```csharp
// Из MainPhpRaz.cs - извлечение vcode из массива боя
var strfightty = HelperStrings.SubString(html, "var fight_ty = [", "];");
if (!string.IsNullOrEmpty(strfightty))
{
    var xfightty = HelperStrings.ParseJsString(strfightty);
    
    // fight_ty[9][5] содержит vcode разделки
    if ((xfightty.Count > 9) && (xfightty[9].Count > 1))
    {
        var vcode = xfightty[9][5];  // Получаем vcode для разделки
        var razLink = $"main.php?get_id=17&type=...&vcode={vcode}";
    }
}

// Пример JavaScript в HTML:
// var fight_ty = [
//   [...],
//   [...],
//   [...],
//   [...],          // fight_ty[3]
//   [...],
//   [...],
//   [...],
//   [...],
//   [...],
//   [...],          // fight_ty[9]
//      [type, p, uid, s, m, "787337e6dbe7e7c26bc662c2b8a7eaaa"]
// ];
```

---

## 3. HTML ПРИМЕРЫ - ГДЕ ВКРАИВАЕТСЯ VCODE

### Пример 1: Скрытый Input в форме (форма рыбалки)
```html
<!-- Рыбалка -->
<form method="POST">
    <input type="hidden" name="get_id" value="55">
    <input type="hidden" name="act" value="4">
    <input type="hidden" name="vcode" value="602f954468e63529e8b3834980ec2734">
    <input type="hidden" name="lakeid" value="1">
    <input type="hidden" name="primid" value="40">
    <input type="submit" value="Забросить удочку">
</form>
```

### Пример 2: URL параметры (навигация)
```html
<!-- Ссылка на посещение персонажа -->
<a href="main.php?get_id=56&act=10&go=inf&vcode=787337e6dbe7e7c26bc662c2b8a7eaaa">
    Перейти в профиль
</a>

<!-- Ссылка телепорта -->
<a href="main.php?get_id=56&act=10&go=up&vcode=f4ee18b9c9ea2f483836729311db8d4c">
    Выход из города
</a>

<!-- Ссылка лечения у врача -->
<a href="main.php?get_id=16&act=1&x=100&y=200&pr=1&vcode=5de3e0e983b05e33abf99d53e4ace2b6">
    Лечение
</a>
```

### Пример 3: JavaScript переменная (телепорты)
```javascript
var vcode = [[10, 20, "Тавакал", "99b7a854057a4200e28cc7e2c2334c9f"],
             [30, 40, "Грень", "33764ca99b7a854057a4200e28cc7e2c"],
             [50, 60, "Выход", "f4ee18b9c9ea2f483836729311db8d4c"]];
// vcode[0][3] = "99b7a854057a4200e28cc7e2c2334c9f"
// vcode[2][3] = "f4ee18b9c9ea2f483836729311db8d4c"
```

### Пример 4: JavaScript функция (чар действия)
```javascript
function doctorform(duid, vcode, dprice, dtype, dcurs) {
    // duid = 'doctor_123'
    // vcode = '787337e6dbe7e7c26bc662c2b8a7eaaa'
    // dprice = '100'
    // dtype = 'Лечение'
    // dcurs = 'Валюта'
}

// Вызов в HTML:
// onclick="doctorform('doctor_123','787337e6dbe7e7c26bc662c2b8a7eaaa','100','Лечение','Валюта')"
```

### Пример 5: JavaScript массив боя (fight_ty)
```javascript
var fight_ty = [
    ["gob", "Гоблин", 1, 2, ...],        // fight_ty[0]
    ["gob_warrior", "Гоблин-воин", ...], // fight_ty[1]
    ["gob_mage", "Гоблин-маг", ...],     // fight_ty[2]
    [...],                               // fight_ty[3]
    [...],                               // fight_ty[4]
    [...],                               // fight_ty[5]
    [...],                               // fight_ty[6]
    [...],                               // fight_ty[7]
    [...],                               // fight_ty[8]
    [0, "prob", 123456, 999, 100, "787337e6dbe7e7c26bc662c2b8a7eaaa"]  // fight_ty[9] - РАЗДЕЛКА
];
// fight_ty[9][5] содержит vcode для разделки
```

---

## 4. ПРИМЕРЫ ИСПОЛЬЗОВАНИЯ VCODE В РАЗНЫХ МОДУЛЯХ

### MainPhpFish.cs (Рыбалка)
```csharp
private static string MainPhpAutoFishPrepare(string html)
{
    // Извлечение параметров для рыбалки
    var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
    if (string.IsNullOrEmpty(vcode))
        return string.Empty;

    var lakeid = HelperStrings.SubString(html, "=lakeid value=", ">");
    var act = HelperStrings.SubString(html, "=act value=", ">");

    // Результат: /main.php?get_id=55&lakeid=1&act=4&primid=39&vcode=602f954468e63529e8b3834980ec2734
    var fishLink = $"/main.php?get_id=55&lakeid={lakeid}&act={act}&primid={primid}&vcode={vcode}";
    return BuildRedirect("Рыбалка", fishLink);
}
```

### TeleportAjax.cs (Телепорт)
```csharp
private static string TeleportAjax(string html)
{
    // Способ 1: Прямое извлечение из массива
    var telep = MyHelpers.HelperStrings.SubString(html, "var telep = [[", "]];");
    var stelep = telep.Split(new[] { "],[" }, StringSplitOptions.None);
    foreach (var etelep in stelep)
    {
        var pars = etelep.Split(',');
        var x = pars[0];
        var y = pars[1];
        var pr = pars[3];
        var vcode = pars[4].Trim('"');  // Trim кавычек
        
        var link = $"main.php?get_id=16&act=1&x={x}&y={y}&pr={pr}&vcode={vcode}";
        return BuildRedirect($"Телепорт", link);
    }

    // Способ 2: Из vcode массива (fallback)
    var build = MyHelpers.HelperStrings.SubString(html, "var vcode = [[", "]];");
    // ... аналогично
}
```

### MainPhpRaz.cs (Разделка)
```csharp
private static string MainPhpRaz(string html)
{
    // Извлечение из fight_ty массива боя
    var strfightty = HelperStrings.SubString(html, "var fight_ty = [", "];");
    var xfightty = HelperStrings.ParseJsString(strfightty);

    // fight_ty[9] = [type, p, uid, s, m, vcode]
    if ((xfightty.Count > 9) && (xfightty[9].Count > 5))
    {
        var razLink =
            "http://www.neverlands.ru/main.php?get_id=17&type=" + xfightty[9][0] +
            "&p=" + xfightty[9][1] +
            "&uid=" + xfightty[9][2] +
            "&s=" + xfightty[9][3] +
            "&m=" + xfightty[9][4] +
            "&vcode=" + xfightty[9][5];
        
        return BuildRedirect("Разделка", razLink);
    }
}
```

### MainPhpCure.cs (Лечение)
```csharp
// Извлечение параметров функции doctorform
var duid = arg[0].Trim(new[] { '\'' });
var vcode = arg[1].Trim(new[] { '\'' });
var dprice = arg[2].Trim(new[] { '\'' });

// Формирование скрытого поля
sb.Append(@"<input name=vcode type=hidden value=""");
sb.Append(vcode);
sb.Append(@""">");
```

---

## 5. ОТПРАВКА VCODE В ЗАПРОСАХ

### Способ 1: URL параметр (GET)
```
GET /main.php?get_id=55&act=4&vcode=602f954468e63529e8b3834980ec2734
GET /main.php?get_id=56&act=10&go=inf&vcode=787337e6dbe7e7c26bc662c2b8a7eaaa
GET /main.php?get_id=16&act=1&x=100&y=200&pr=1&vcode=5de3e0e983b05e33abf99d53e4ace2b6
```

### Способ 2: POST форма (скрытый input)
```html
<form action="main.php" method="POST" name="ff">
    <input type="hidden" name="post_id" value="8">
    <input type="hidden" name="vcode" value="787337e6dbe7e7c26bc662c2b8a7eaaa">
    <input type="hidden" name="uid" value="123456">
    <input type="submit" value="Выполнить">
</form>
```

**POST request:**
```
POST /main.php
Content-Type: application/x-www-form-urlencoded

post_id=8&vcode=787337e6dbe7e7c26bc662c2b8a7eaaa&uid=123456
```

---

## 6. КЛЮЧЕВЫЕ МАРКЕРЫ ДЛЯ ПОИСКА VCODE

| Контекст | Маркер начала | Маркер конца | Метод | Пример файла |
|----------|---|---|---|---|
| Скрытое поле формы | `=vcode value=` | `>` | SubString | MainPhpFish.cs |
| URL параметр | `&vcode=` | `&` или `\` | SubString | MainPhpRaz.cs |
| JavaScript прямо | `var vcode = ` | `;` | SubString | TeleportAjax.cs |
| JavaScript массив | `fight_ty[9][5]` | - | ParseJsString | MainPhpRaz.cs |
| Function параметр | `'vcode'` | `','` | Split + Trim | MainPhpCure.cs |
| JSON объект | `"vcode":"` | `"` | Regex или SubString | - |

---

## 7. РЕГУЛЯРНОЕ ВЫРАЖЕНИЕ (если verwendеется)

Хотя в C# коде используется простое строковое извлечение, эквивалентный regex для валидации:

```regex
[a-f0-9]{32}
```

**Примеры совпадения:**
- ✓ `602f954468e63529e8b3834980ec2734`
- ✓ `787337e6dbe7e7c26bc662c2b8a7eaaa`
- ✓ `5de3e0e983b05e33abf99d53e4ace2b6`
- ✗ `602f954468e63529e8b3834980ec273G` (содержит G - не hex)
- ✗ `602f954468e63529e8b3834980ec27` (31 символ - слишком коротко)

---

## 8. ВАЖНЫЕ ЗАМЕЧАНИЯ ДЛЯ ПОРТИРОВАНИЯ НА ANDROID

1. **Не сохранять vcode глобально:** В C# он извлекается, используется и удаляется. В Android нужно использовать `SessionManager`.

2. **Порядок приоритета поиска vcode:**
   - Первый: Поиск в скрытых input полях (`=vcode value=`)
   - Второй: Поиск в URL параметрах (`&vcode=`)
   - Третий: Поиск в JavaScript переменных (`var vcode = `, `var fight_ty = `)
   - Четвёртый: Поиск в function параметрах

3. **Обработка Trim операций:**
   - Удаление одинарных кавычек: `'...'` → `...`
   - Удаление двойных кавычек: `"..."` → `...`
   - Удаление пробелов

4. **Валидация:**
   - Проверить, что vcode имеет ровно 32 символа
   - Проверить, что все символы - hex (0-9, a-f)
   - Проверить, что NullOrEmpty — reject

---

## 9. ПРИМЕРЫ РЕАЛЬНЫХ HTML ОТВЕТОВ

### HTML#1: Страница рыбалки
```html
<form method="POST" name="fish_form">
    <input type=hidden name="get_id" value="55">
    <input type=hidden name="act" value="4">
    <input type=hidden name="vcode" value="602f954468e63529e8b3834980ec2734">
    <input type=hidden name="lakeid" value="1">
    <input type=hidden name="primid" value="40">
    ...
</form>
```

### HTML#2: Навигация по карте с ссылками
```html
<div style="position:absolute; left: 154; top: 167;">
    <a href="main.php?get_id=56&act=10&go=build&pl=bar0&vcode=420ebb3d63e219797d03db2e9242b27a">
        <img src="...">
    </a>
</div>
<div style="position:absolute; left: 374; top: 0;">
    <a href="main.php?get_id=56&act=10&go=arena&vcode=98c621c0ec59516304a1de99aa0f743f">
        <img src="...">
    </a>
</div>
```

### HTML#3: Скрипт с массивом боя
```html
<script>
var fight_ty = [
    ["gob", "Гоблин", 1, 2, 3, 4, 5],
    [...других полей...],
    [...],
    [...],
    [...],
    [...],
    [...],
    [...],
    [...],
    [0, "prob", 123456, 100, 50, "787337e6dbe7e7c26bc662c2b8a7eaaa"]
];
</script>
```

---

## SUMMARY (Краткий итог)

| Параметр | Значение |
|---|---|
| **Формат vcode** | 32 hex символа `[a-f0-9]{32}` |
| **Основной метод извлечения** | `HelperStrings.SubString(html, startMarker, endMarker)` |
| **Trim операции** | Удаление `'` и `"`, а также пробелов |
| **Способы передачи** | GET параметры, POST скрытые input, JavaScript переменные |
| **Первый появляется** | На каждой странице (get_id, act, vcode) |
| **Жизненный цикл** | Извлечение → Использование → Удаление (не сохраняется) |
