# КРАТКОЕ РЕЗЮМЕ: VCode Management в ABClient (C#)

```
┌─────────────────────────────────────────────────────────┐
│             АРХИТЕКТУРА VCode (РАСПРЕДЕЛЁННАЯ)         │
└─────────────────────────────────────────────────────────┘

🔴 ГЛАВНЫЙ ПРИНЦИП:
   VCode НЕ хранится централизованно!
   
✅ ВМЕСТО ЭТОГО:
   1. VCode парсится из каждого HTML ответа
   2. Используется АЛАВНо же в том же коде
   3. Не кэшируется между запросами
```

---

## VCode Management в C# - ИТОГОВАЯ ТАБЛИЦА

| Параметр | Значение | Расположение |
|----------|----------|--------------|
| **Класс управления** | `ParsedDressed` | `TInvUd.cs` |
| **Поле хранения** | `internal string Vcod;` | `TInvUd.cs:13` |
| **Главная точка обработки** | `Filter.Process()` | `PostFilter/Filter.cs:82` |
| **Куда парсится VCode** | Локальные переменные в каждом методе | `MainPhp.cs:50`, `FightJs.cs:78` и т.д. |
| **Механизм обновления** | На каждый HTML ответ | После каждого `WebBrowser.GetString()` |
| **Валидация** | `if (string.IsNullOrEmpty(vcode))` | `MainPhpFish.cs:51`, `MainPhpFast.cs:166` |
| **Prevention потери** | Перезагрузка main.php перед действием | `MainPhpFindInv()` вызывается перед использованием вещи |
| **Синхронизация модулей** | Через главный Filter.Process() | Единая точка распределения |
| **Кэширование** | **МИНИМАЛЬНО** (только ParsedDressed в одной обработке) | Объект одноразовый |
| **Ошибка "неверный VCode"** | Не обрабатывается явно - предотвращается | Валидация перед использованием |

---

## КОД: Как система парсит VCode

### 1️⃣ РЫБАЛКА (MainPhpFish.cs:50-52)

```csharp
var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
if (string.IsNullOrEmpty(vcode))
    return string.Empty;  // ← FAIL SAFE

// Использование (line 225-226):
"&vcode=" + vcode;
```

**Паттерн парсинга:** `"=vcode value="` ... `">"`

### 2️⃣ БОЙ (FightJs.cs:78)

```javascript
form_node.appendChild(AddElement('vcode', ss[0]));
// ss[0] = результат боевого запроса, разрез по "|"
// Используется в form_main.submit()
```

**Паттерн**: Первый элемент результата боевого API

### 3️⃣ БЫСТРЫЕ ДЕЙСТВИЯ (MainPhpFast.cs:146, 166-167)

```csharp
var vcode = arg[0].Trim(new[] { '\'' });

sb.Append(@"<input name=vcode type=hidden value=""");
sb.Append(vcode);
sb.Append(@""">");
```

**Паттерн**: Парсинг из `onclick="w28_form('vcode_value', ...)"`

### 4️⃣ ВЕЩИ/ИНВЕНТАРЬ (TInvUd.cs:125-131)

```csharp
// Парсинг из slots_inv() функции:
// slots_inv(main_inv, wid_inv, [MAIN_DATA], [WID_DATA], [VCOD_DATA], ...)

var slvcod = pslots[4].Split('@');  // pslots[4] = VCOD_DATA
if (slvcod.Length < 3)
    return;

Vcod = slvcod[2];  // ← Вот VCode вещи!
```

**Паттерн**: `pslots[4].Split('@')[2]` из `slots_inv()`

### 5️⃣ ГЛАВНАЯ ХУБ (PostFilter/Filter.cs:195-220)

```csharp
internal static byte[] Process(string address, byte[] array)
{
    var html = Russian.Codepage.GetString(array);
    
    // ВСЕ ответы обрабатываются здесь:
    if (address.StartsWith("http://www.neverlands.ru/main.php"))
        return MainPhp(address, array);  // → MainPhpFish() → парсит VCode
    
    if (address.Contains("/js/fight_v"))
        return FightJs(array);  // → парсит VCode боя
    
    if (address.Contains("fish_ajax.php"))
        return FishAjaxPhp(array);  // → парсит VCode рыбалки
}
```

---

## ⚠️ КАК СИСТЕМА ИЗБЕГАЕТ ПОТЕРЬ VCode

### Проблема: Потеря VCode между запросами
**С#-версия решает это:**

```
1. VCode не кэшируется
   └─ Живет только в полложке, где используется

2. Перед каждым действием система получает свежий HTML
   └─ MainPhpFindInv("&im=0&wca=85") → новый main.php

3. Парсинг "на лету"
   └─ var vcode = HelperStrings.SubString(...)
   └─ Используется немедленно в той же функции

4. Валидация
   └─ if (string.IsNullOrEmpty(vcode)) { отмена }

5. Система переходит на main.php перед действием
   └─ main.php ВСЕ ПУТИ содержит slots_inv() с VCode
```

**Диаграмма:**
```
Action requested
    ↓
LoadMain.php (получить свежий контекст)
    ↓
Parse VCode из HTML
    ↓
Validate (не пусто?)
    ↓
Use immediately
    ↓
Don't cache for later
```

---

## 🔄 СИНХРОНИЗАЦИЯ МЕЖДУ МОДУЛЯМИ

**Рыбалка + Бой + Быстрые действия + Вещи = Один VCode?**

❌ **НЕТ!** Каждый модуль имеет **свой VCode** из **своего контекста**:

```
Рыбалка:
  VCode из: main.php?get_id=55&... (рыбалка)
  Парсит: MainPhpFish.cs:50
  
Бой:
  VCode из: fight.js ответ (боевой сервер)
  Парсит: FightJs.cs:78
  
Быстрые действия:
  VCode из: onclick в HTML
  Парсит: MainPhpFast.cs:146
  
Вещи:
  VCode из: slots_inv() для конкретной вещи
  Парсит: TInvUd.cs:131

📌 ОНИ СИНХРОНИЗИРУЮТСЯ ЧЕРЕЗ:
   Filter.Process() - единая точка обработки всех ответов
   
   ✓ Один поток обработки
   ✓ Последовательное распределение  
   ✓ Нет гонки между модулями
```

---

## 📊 ТАБЛИЦА ПАРСИНГА VCode

| Модуль | Источник | Функция парсинга | Где хранится | Когда используется |
|--------|----------|------------------|--------------|-------------------|
| **Рыбалка** | `main.php?get_id=55` | `MainPhpFish()` | Локальная переменная | В ссылке main.php |
| **Бой** | `fight.js` (боевой API) | `FightJs()` | `ss[0]` массива | В `form_main` |
| **Быстрые действия** | `onclick="w28_form(..."` | `MainPhpFast()` | `arg[0]` параметра | В hidden input |
| **Вещи** | `slots_inv()` в HTML | `ParsedDressed()` | `ParsedDressed.Vcod` | В ссылке main.php |
| **Навигация** | `main.php?get_id=16` | `TeleportAjax()` | `pars[4]` параметра | В ссылке main.php |

---

## ❌ ЧТО НЕ НУЖНО ДЕЛАТЬ (Антипаттерны)

### ❌ Антипаттерн 1: Единое хранилище VCode

```csharp
// ❌ НЕПРАВИЛЬНО:
public static string CurrentVCode { get; set; }

AppVars.CurrentVCode = vcode;  // Сохраняем
// ... потом, в другом месте
var link = "main.php?vcode=" + AppVars.CurrentVCode;  // ← СТАРЫЙ VCode!
```

**Результат:** Потеря VCode при контекстных переключениях!

### ❌ Антипаттерн 2: Долгосрочное кэширование

```csharp
// ❌ НЕПРАВИЛЬНО:
private static string _cachedVCode;

void ParseVCode(string html) {
    _cachedVCode = vcode;  // Кэшируем
}

// ... позже, после 10 других действий
void DoAction() {
    var link = "main.php?vcode=" + _cachedVCode;  // ← ПЕРЕСТАВШИЙ VCode!
}
```

**Результат:** VCode становится невалидным!

### ❌ Антипаттерн 3: Без валидации

```csharp
// ❌ НЕПРАВИЛЬНО:
var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
var link = "main.php?vcode=" + vcode;  // Не проверяем, пусто ли!
```

**Результат:** "Неверный код защиты" ошибка!

---

## ✅ ПРАВИЛЬНЫЙ ПОДХОД

```csharp
// ✅ ПРАВИЛЬНО:
1. Парсим VCode только при необходимости
   var vcode = HelperStrings.SubString(html, "=vcode value=", ">");

2. Валидируем немедленно
   if (string.IsNullOrEmpty(vcode))
       return string.Empty;  // Отмена, если нет VCode

3. Используем в той же функции
   var link = "main.php?...&vcode=" + vcode;
   return link;

4. Не сохраняем для потом
   // Нет: this.savedVCode = vcode;

5. При следующем действии - парсим снова
   var newVcode = HelperStrings.SubString(newHtml, "=vcode value=", ">");
```

---

## 🎯 КЛЮЧЕВОЙ ВЫВОД

### **В ABClient (C#) система управления VCode основана на:]**

1. **Распределённое хранилище** - нет единого "CurrentVCode"
2. **Парсинг на лету** - из каждого HTML ответа сервера
3. **Немедленное использование** - лока того же метода, где парсили
4. **Минимальное кэширование** - только в контексте одной обработки
5. **Валидация перед использованием** - проверка на пустоту
6. **Единая точка обработки** - Filter.Process() распределяет ответы
7. **Нет конфликтов** - потому что VCode парсится свежо каждый раз

### **Для Android это означает:**

```
❌ Не делать:
   static String currentVCode
   sessionVCode cache
   global VCode storage

✅ Вместо этого:
   Extract VCode from WebView DOM on demand
   Use immediately in the same operation
   Don't store between operations
   Validate before use
   If missing - reload to main.php
```
