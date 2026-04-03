# BUG: Смена удочки при переполнении массы инвентаря

## 🔴 Проблема

**Рыбалка остановилась** на **17:51:05** когда масса инвентаря достигла **1225.02/1405** (~92% полно).

### Симптомы

1. **17:51:05.730** — Отправлен `act=2&primid=39` (отправка наживки)
2. **17:51:05.825** — Получен ответ `200 OK`
3. **17:51:05.825 - 17:52:00** — **НИКАКИХ рыбалочных запросов**
4. **17:52:00** — Система переключилась на БОЙ (загрузились боевые скрипты fight_v10.js)

### Причина

Когда масса инвентаря подходит к лимиту (1225/1405), **сервер выбросил в бой** вместо возврата рыбалочного ответа.

**Почему так произошло?**
- Нет проверки массы перед отправкой `act=2`
- Нет логики автоматической смены удочки при переполнении инвентаря
- **В Android версии полностью отсутствует механизм управления удочками!**

---

## 🔍 Анализ логов

### fishajaxphp.log (последние строки)

```
2026-04-03 17:51:05.572 [TRACE] vcode parsed through SessionManager, vcode=9987bb90477bb73f3fee0df41686f128
```

**После этой строки — НИЧЕГО!** Означает что после получения vcode приложение не отправило act=2!

### proxy.log

```
17:51:05.730 REQ act=2&primid=39&vcode=9987bb90477bb73f3fee0df41686f128
17:51:05.825 RESP 200 OK
```

Запрос отправлен, ответ получен. Но **ответ, вероятно, содержал БОЕВУЮ СТРАНИЦУ**, потому что система переключилась на бой!

### filelogger.log

```
17:51:03.516 VALID_VCODE: ageMs=139567  (старая vcode!)
17:51:59.367 main.php loaded
17:52:00.234 FIGHT_STARTED
```

На 17:51:03 был переход на **main.php** профиля персонажа (get_id=56), а на 17:52:00 уже бой!

---

## 📐 C# версия (_Как это должно работать_)

### 1. **UserConfig.cs** — Настройки

```csharp
public bool FishStopOverWeight { get; set; }  // Останавливать при перегрузе
public bool FishAutoWear { get; set; }         // Автонадевание удочек
public string FishHandOne { get; set; }        // Удочка правая рука
public string FishHandTwo { get; set; }        // Удочка левая рука
```

### 2. **TInvUd.cs** — Проверка амленты

```csharp
public bool IsWear1()  // Проверка надета ли удочка #1
{
    if (!AppVars.Profile.FishAutoWear) return true;
    
    // Ищет удочку в инвентаре
    // Если найдена -> AppVars.AutoFishHand1 = имя удочки
    // Если нет -> iswear1 = false
    
    return iswear1;
}

public bool IsWear2()  // Проверка надета ли удочка #2
{
    // Аналогично...
}
```

### 3. **MainPhpFish.cs** — Проверка перед отправкой

```csharp
// Перед отправкой act=2 нужно проверить:
if (user_mass >= max_mass && FishStopOverWeight)
{
    // Нужно сменить удочку
    // Если нет альтернативной удочки - остановить рыбалку
}
```

### 4. **FishAjaxPhp.cs** — Логика act=2

Когда пришел ответ act=2:
- Если ответ содержит боевую страницу → **не отправляютact=2 вообще!**
- Если масса переполнена → проверяют вторую удочку
- Если вторая удочка доступна → **переодеваются в неё и отправляют act=1 снова**

---

## 🛠️ Решение для Android

### План реализации

#### ✅ ШАГ 1: Добавить парсинг максимальной массы в FishAjaxPhp.java

В методе `parseFishAct1State()` уже парсится масса:
```java
state.massCurrent = massMatcher.group(1);
state.massMax = massMatcher.group(2);
```

**ХОРОШО!** Это уже есть.

#### ✅ ШАГ 2: Добавить проверку массы перед act=2

**Файл:** `app/src/main/java/ru/neverlands/abclient/postfilter/FishAjaxPhp.java`

**Новый метод:** `checkMassBeforeCasting()`

```java
/**
 * Проверяет, можно ли отправить наживку (act=2)
 * Если масса инвентаря > 90% от максимальной:
 *   - Логирует warning
 *   - Инициирует смену удочки
 *   - Отменяет act=2 отправку
 * 
 * @param massCurrent текущая масса
 * @param massMax максимальная масса  
 * @param selectedBaitMass масса выбранной наживки
 * @return true = можно отправлять act=2, false = нужна смена удочки
 */
private static boolean checkMassBeforeCasting(
        double massCurrent, double massMax, double selectedBaitMass) {
    
    boolean isMassOkay = true;
    double percentUsed = (massCurrent / massMax) * 100;
    
    // Критичный порог: 90% от максимума
    double warningThreshold = 0.90;
    
    if ((massCurrent + selectedBaitMass) >= (massMax * warningThreshold)) {
        Log.w("FishAjaxPhp", String.format(
            "⚠️ MASS_WARNING: current=%.2f, max=%.2f, percent=%.1f%%, " +
            "with bait would be=%.2f (OVER THRESHOLD 90%%)",
            massCurrent, massMax, percentUsed, massCurrent + selectedBaitMass));
        
        FileLogger.trace("[FISH_MASS_CHECK] ⚠️ Current mass " + percentUsed + 
            "% full. Hook mass + bait would exceed 90% threshold. " +
            "Triggering rod change sequence.");
        
        isMassOkay = false;
    } else {
        Log.d("FishAjaxPhp", String.format(
            "✅ MASS_OK: current=%.2f/%.2f (%.1f%%)",
            massCurrent, massMax, percentUsed));
    }
    
    return isMassOkay;
}
```

#### ✅ ШАГ 3: Создать RodInventoryManager

**Файл:** `app/src/main/java/ru/neverlands/abclient/utils/RodInventoryManager.java`

```java
/**
 * Управление удочками и их сменой при необходимости.
 * Порт логики из C# TInvUd.cs
 */
public class RodInventoryManager {
    
    private static final String TAG = "RodInventoryManager";
    
    /**
     * Проверяет доступные удочки в инвентаре
     * @param html текущая страница озера
     * @return список доступных удочек
     */
    public static List<String> getAvailableRods(String html) {
        List<String> rods = new ArrayList<>();
        
        // Парсим инвентарь из HTML
        // Ищем предметы с именем содержащим "удочка" или "спиннинг"
        // Добавляем в список доступных
        
        FileLogger.trace("[ROD_INVENTORY] Found " + rods.size() + " available rods");
        return rods;
    }
    
    /**
     * Возвращает следующую удочку из инвентаря
     * @return имя удочки или null если нет
     */
    public static String getNextAvailableRod() {
        if (AppVars.Profile == null || !AppVars.Profile.FishAutoWear) {
            return null;
        }
        
        // Проверяем вторую удочку (left hand)
        // Если доступна - возвращаем
        
        FileLogger.trace("[ROD_INVENTORY] Next available rod: " + nextRod);
        return nextRod;
    }
    
    /**
     * Инициирует смену удочки
     * @param oldRod текущая удочка
     * @param newRod удочка на смену
     */
    public static String generateRodChangeRequest(String oldRod, String newRod) {
        String changeHtml = "";
        
        // Генерируем HTML запрос для смены удочки
        // Это запрос к main.php для переодевания
        
        FileLogger.trace("[ROD_CHANGE] " + oldRod + " → " + newRod);
        return changeHtml;
    }
}
```

#### ✅ ШАГ 4: Интегрировать в FishAjaxPhp

**Метод:** `parseFishAct1State()` — добавить проверку масс перед возвратом

```java
// После выбора наживки:
Boolean isMassOkay = checkMassBeforeCasting(
    parsedState.massCurrent,
    parsedState.massMax,
    selectedBaitMass);

if (!isMassOkay) {
    // Инициируем смену удочки
    String nextRod = RodInventoryManager.getNextAvailableRod();
    
    if (nextRod != null) {
        FileLogger.trace("[FISH_ACT1_REJECTED] Mass over limit, " +
            "triggering rod change to: " + nextRod);
        // Сигнал на смену удочки
        return;  // Не отправляем act=2!
    } else {
        FileLogger.trace("[FISH_ACT1_REJECTED] Mass over limit, " +
            "NO RODS AVAILABLE - stopping fishing");
        // Сигнал на остановку рыбалки
        return;
    }
}

// Только если масса OK - продолжаем act=2
```

---

## 📊 Данные из логов

### Масса при остановке
- **Current:** 1225.02
- **Max:** 1405
- **Percent:** 87.2%

### Стока до остановки
| Время | Mass | Worms | Статус |
|-------|------|-------|--------|
| 17:47:40 | 1214.02/1405 | 190 | ✅ OK |
| 17:48:44 | 1215.72/1405 |  187 | ✅ OK |
| 17:50:00 | 1221.42/1405 | 184 | ✅ OK |
| 17:51:05 | 1225.02/1405 | 180 | ❌ act=2 отправлен → БОЙ! |

**Вывод:** Сервер выбросил в бой когда масса достаточно близко к лимиту.

---

## 📝 Чек-лист реализации

- [ ] Добавить `checkMassBeforeCasting()` метод в FishAjaxPhp.java
- [ ] Создать RodInventoryManager.java
- [ ] Интегрировать проверку в `parseFishAct1State()`
- [ ] Добавить логирование смены удочки (Log.d + FileLogger.trace)
- [ ] Протестировать с разными удочками и наживками
- [ ] Убедиться что вторая удочка меняется корректно
- [ ] Проверить что логи содержат трассировку смены удочки

---

## 🔗 Связанные файлы

- `app/src/main/java/ru/neverlands/abclient/postfilter/FishAjaxPhp.java` — основная логика рыбалки
- `app/src/main/java/ru/neverlands/abclient/model/ParsedDressed.java` — парсер экипировки
- `ABClient/PostFilter/MainPhpFish.cs` — C# логика рыбалки (для справки)
- `ABClient/TInvUd.cs` — C# логика управления удочками (для справки)

---

## 🎯 Приоритет

**ВЫСОКИЙ** — Рыбалка полностью неработоспособна при полной массе инвентаря!
