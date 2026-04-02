# Анализ системы таймеров для одевания комплектов

## 1. ПК версия (C#): Как работает механизм

### Хранение комплекта в таймере
**Файл:** `AppTimer.cs` (C#)
```csharp
internal string Complect = string.Empty;  // Строка 16
```

Структура таймера содержит **название комплекта** (например "РЫБАК").

### Запуск одевания при срабатывании таймера
**Файл:** `FormMainTimers.cs` (C#, строки 65-70)
```csharp
var complect = arrayAppTimers[i].Complect;
if (!string.IsNullOrEmpty(complect))
{
    AppVars.WearComplect = complect;           // Установить название комплекта
    AppTimerManager.RemoveTimerAt(i);          // Удалить таймер из списка
    EventSounds.PlayTimer();                   // Проиграть звук
    ReloadMainFrame();                         // Перезагрузить основную страницу
    return;
}
```

**Ключевой момент:** Таймер устанавливает `AppVars.WearComplect = "РЫБАК"` и затем перезагружает страницу.

### Парсинг комплекта на HTML странице и отправка запроса
**Файл:** `MainPhpWearComplect.cs` (C#)

Код ищет вызов JavaScript функции `compl_view()`:
```javascript
compl_view("РЫБАК","8900462769ce20fe5606e","2af6a69baa0304ecc4b7840ea6379fbb");
```

Парсинг получает:
- **Название** (1-й параметр): "РЫБАК"
- **ID комплекта** (2-й параметр): "8900462769ce20fe5606e"
- **Хеш VCode** (3-й параметр): "2af6a69baa0304ecc4b7840ea6379fbb"

Затем формирует и отправляет запрос:
```url
main.php?get_id=57&uid=8900462769ce20fe5606e&s=2&vcode=2af6a69baa0304ecc4b7840ea6379fbb
```

**Параметры:**
- `get_id=57` - команда "надеть вещь"
- `uid=...` - ID комплекта
- `s=2` - **специальный код для комплекта** (s=1 для обычных вещей, s=2 для комплектов)
- `vcode=...` - код защиты от CSRF

### Хранение списка комплектов
**Файл:** `FormMainCross.cs` (C# строки 739-798)

Комплекты хранятся в виде:
```csharp
AppVars.Profile.Complects = "комплект1|комплект2|комплект3";
```

Разделены вертикальной чертой `|`.

---

## 2. Android реализация: КРИТИЧНЫЙ БАГ

### Структура таймера (РАБОТАЕТ)
**Файл:** `AppTimer.java`
```java
public String complect = "";  // строка 22
```

### Запуск таймера (РАБОТАЕТ)
**Файл:** `AppTimerManager.java` (строки 320-327)
```java
private void executeComplectTimerLocked(int index, AppTimer timer) {
    listAppTimers.remove(index);
    persistLocked();

    AppVars.WearComplect = timer.complect;  // Установка названия
    playTimerSignalIfEnabledLocked();
    reloadMainPhpInf();                     // Перезагрузка
    Log.d(TAG, "processDueTimers: complect timer fired, id=" + timer.id + ", complect=" + timer.complect);
}
```

### ❌ КРИТИЧНАЯ ПРОБЛЕМА: Отсутствует метод парсинга комплектов

**В Android НЕТ АНАЛОГА `MainPhpWearComplect.cs`!**

Поиск в коде Android:
- ❌ Нет метода `mainPhpWearComplect(...)`
- ❌ Нет парсинга `compl_view(...)`
- ❌ Нет формирования запроса `get_id=57&uid=...&s=2&vcode=...`

**Что происходит:**
1. Таймер срабатывает ✅
2. Устанавливается `AppVars.WearComplect = "РЫБАК"` ✅
3. Перезагружается main.php ✅
4. **НО:** Сервер отправляет HTML с `compl_view(...)` вызовом
5. Android просто **игнорирует этот вызов** (нет обработки)
6. Комплект НЕ одевается ❌

### Текущая обработка compl_view
**Файл:** `HtmlUtils.java` (строка 56)

В JS-фиксах есть заглушка:
```java
"if (typeof window.compl_view !== 'function') { window.compl_view = function() {}; }"
```

Это ЗАГЛУШКА, которая просто игнорирует вызов!

---

## 3. Реальный запрос на одевание комплекта (из DressCompl.har)

### JavaScript вызов на странице
```javascript
compl_view("РЫБАК","8900462769ce20fe5606e","2af6a69baa0304ecc4b7840ea6379fbb");
compl_view("КЛАД","209068011569c4c723cafcc","80c605f4eba7f0e50ca4df1261a27393");
compl_view("free","308876688660a7372cf8cf","f353786f8c2d2148f2e209d1c5ee2376");
```

### Формат данных
```
compl_view("название","uid","vcode");
```

Все три параметра это **строки**.

### HTTP запрос для одевания комплекта "РЫБАК"
```
GET /main.php?get_id=57&uid=8900462769ce20fe5606e&s=2&vcode=2af6a69baa0304ecc4b7840ea6379fbb HTTP/1.1
Host: neverlands.ru
```

**Параметры:**
| Параметр | Значение | Собирается из |
|----------|----------|---------------|
| `get_id` | `57` | Константа (надеть вещь) |
| `uid` | `8900462769ce20fe5606e` | 2-й параметр compl_view("РЫБАК", **это**, "vcode") |
| `s` | `2` | **`s=2` специально для комплектов!** (отличается от s=1 для вещей) |
| `vcode` | `2af6a69baa0304ecc4b7840ea6379fbb` | 3-й параметр compl_view("РЫБАК", "uid", **это**) |

---

## 4. Выводы и статус функционала

### ❌ Работает ли наш таймер для комплектов?
**НЕТ, НЕ РАБОТАЕТ.**

**Почему:**
1. Таймер корректно срабатывает
2. Устанавливается `AppVars.WearComplect`
3. Перезагружается main.php
4. **НО:** Отсутствует логика парсинга `compl_view()` и отправки запроса на одевание

**Результат:** Комплект НЕ одевается, хотя таймер считается выполненным.

### 🔴 Что нужно добавить/исправить

#### 1. Создать метод парсинга комплектов
Нужен новый метод в `MainPhp.java`:
```java
private static String mainPhpWearComplect(String html, String complect) {
    // Ищет compl_view("название", "uid", "vcode")
    // Парсит uid и vcode
    // Отправляет get_id=57&uid=...&s=2&vcode=...
}
```

#### 2. Интегрировать в оркестрацию main.php
В методе `parseMainPhp()` добавить вызов:
```java
if (!AppVars.WearComplect.isEmpty()) {
    String complectHtml = mainPhpWearComplect(html, AppVars.WearComplect);
    if (complectHtml != null && !complectHtml.isEmpty()) {
        AppVars.WearComplect = "";  // Очистить флаг
        return Russian.getBytes(complectHtml);
    }
}
```

#### 3. Удалить заглушку из HtmlUtils
Заглушка `window.compl_view = function() {}` позволяет JavaScript вызову "пройти мимо" без ошибок.

### 📋 Система паузы авто-функций: ЧТО РАБОТАЕТ

В коде уже реализовано:
- ✅ `AppVars.TimerPauseNonCombatAutoFunctions` - флаг паузы авто-функций при таймере
- ✅ Проверка в методе `isNonCombatAutoPausedByFastAction()`:
  ```java
  private static boolean isNonCombatAutoPausedByFastAction() {
      return (AppVars.FastNeed && AppVars.FastPauseNonCombatAutoFunctions)
              || AppVars.TreasureDigPauseNonCombatAutoFunctions
              || AppVars.TimerPauseNonCombatAutoFunctions;  // ← ТУТ!
  }
  ```

**Вывод:** Система паузы **готова** для таймеров, но сам механизм одевания комплектов не реализован.

---

## 5. Архитектурные замечания

### Инварианты, которые нужно сохранить:
1. **Заглушка `compl_view()`** должна оставаться в `HtmlUtils.getJsFix()`, чтобы не было JS ошибок
2. **Флаг `AppVars.WearComplect`** используется как триггер для `mainPhpWearComplect()`
3. **Параметр `s=2`** критичен - это отличает комплекты от обычных вещей (s=1)

### Путь реализации:
- [ ] Создать `MainPhp.mainPhpWearComplect(html, complectName)`
- [ ] Интегрировать в `parseMainPhp()` перед авто-рыбалкой/авто-бойом
- [ ] Добавить логирование для отладки
- [ ] Проверить на разных комплектах (РЫБАК, КЛАД, и т.д.)
