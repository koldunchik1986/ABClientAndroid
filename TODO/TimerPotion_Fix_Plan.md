# План исправления: Таймер пить зелья - Быстрые действия

## 🔴 ДВА ОСНОВНЫХ ВОПРОСА

### Вопрос 1: Почему автоматический таймер во время рыбалки не срабатывает?

**Текущая проблема:**
```
20:10:09.502 AUTO_FISH_TRACE tied=24 > 20, trigger bliss elixir
20:10:09.704 AUTO_DRINK_TRACE skip: FastNeed active, fastId=
                                                         ↑ ПУСТО!
```

**Анализ:** Вызов `FastActionManager.fastAttackBlazElixir()` правильный, но может быть TIMING ISSUE:
1. AutoFish срабатывает на 09.502
2. Вызывает `fastStart("Эликсир Блаженства", "")`
3. fastStart() устанавливает `FastId = "Эликсир Блаженства"`
4. **НО** fastStart() вызывает `reloadMainFrame()` в конце
5. Перезагрузка может сбросить FastId если что-то перепишет его

**Гипотезы:**
- [ ] Race condition между установкой FastId и первым логированием
- [ ] Что-то в цепочке MainPhp→FastActionManager непоследовательное
- [ ] NeverTimer cooldown отменяет действие

---

### Вопрос 2: Где в Android реализовать полноценные пользовательские таймеры?

**Текущее состояние:**
- ✅ ПК-версия: `AppTimerManager` со списком таймеров
- ❌ Android: Таймеры не реализованы совсем
- ⚠️ Пользователь может только вручную нажимать кнопки быстрых действий

**Что нужно портировать:**
1. `AppTimer` - структура таймера (как в ПК)
2. `AppTimerManager` - управление списком таймеров
3. UI для управления таймерами (Таймеры → +Добавить → выбор зелья → время)
4. Проверка срабатывания в главном цикле ("UpdateTimers")

---

## 🟡 ПЕРВЫЙ ЭТАП: Исправить автоматический триггер при рыбалке

### 1. Усилить логирование в fastStart()

**Добавить в FastActionManager.fastStart() (строка 243-244):**

```java
public static void fastStart(String id, String nick, int count) {
    boolean prevFastNeed = AppVars.FastNeed;
    String prevFastId = AppVars.FastId;
    String prevFastNick = AppVars.FastNick;
    boolean prevPauseNonCombatAuto = AppVars.FastPauseNonCombatAutoFunctions;
    
    if (!prevFastNeed) {
        captureNonCombatAutoSnapshotBeforeFast("fastStart:" + id);
    }
    
    // КЛЮЧЕВОЙ МОМЕНТ: Установка параметров
    AppVars.FastNeed = true;
    AppVars.FastId = id;
    AppVars.FastNick = nick;
    AppVars.FastCount = count;
    AppVars.FastPauseNonCombatAutoFunctions = true;
    AppVars.FastReturnToMapPending = true;
    
    // ← ДОБАВИТЬ ЗДЕСЬ усиленное логирование
    FileLogger.trace(TAG, "[FAST_START_TRACE] УСТАНОВКА: "
            + "id=" + id
            + ", nick='" + nick + "'"
            + ", count=" + count
            + ", now FastNeed=" + AppVars.FastNeed
            + ", now FastId='" + AppVars.FastId + "'"
            + ", thread=" + Thread.currentThread().getId());
    
    Log.d(TAG, "[FAST_START_TRACE] УСТАНОВКА: "
            + "id=" + id
            + ", nick='" + nick + "'"
            + ", count=" + count
            + ", now FastNeed=" + AppVars.FastNeed
            + ", now FastId='" + AppVars.FastId + "'");
    
    Log.d(TAG, "fastStart: id=" + id + ", nick=" + nick + ", count=" + count);
    Log.d(TAG, "[AA_TRACE] fastStart state: prevFastNeed=" + prevFastNeed
            + ", prevFastId=" + prevFastId
            + ", prevFastNick=" + prevFastNick
            + ", prevPauseNonCombatAuto=" + prevPauseNonCombatAuto
            + ", newFastNeed=" + AppVars.FastNeed
            + ", newFastId=" + AppVars.FastId
            + ", newFastNick=" + AppVars.FastNick
            + ", newPauseNonCombatAuto=" + AppVars.FastPauseNonCombatAutoFunctions);
    
    reloadMainFrame();  // ← ПОСЛЕ логирования!
}
```

### 2. Логирование при первом срабатывании в AutoFish

**Добавить в MainPhp.mainPhpAutoFishStep() (строка 2559-2565):**

```java
if (tied >= tiedHigh) {
    lastAutoFishBlazTriggerAtMs = now;
    lastAutoFishDrinkTriggerAtMs = now;
    AppVars.AutoFishDrinkOnce = true;
    
    // ← ЛОГИРОВАНИЕ ДО вызова
    FileLogger.trace(TAG, "[AUTO_FISH_BEFORE_FAST] tied=" + tied 
            + ", tiedHigh=" + tiedHigh
            + ", before fastStart: FastNeed=" + AppVars.FastNeed
            + ", FastId='" + AppVars.FastId + "'");
    
    android.util.Log.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
            + ", trigger bliss elixir");
    FileLogger.trace(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
            + ", trigger bliss elixir");
    
    // ВЫЗОВ
    FastActionManager.fastAttackBlazElixir();
    
    // ← ЛОГИРОВАНИЕ ПОСЛЕ вызова
    FileLogger.trace(TAG, "[AUTO_FISH_AFTER_FAST] after fastStart: FastNeed=" 
            + AppVars.FastNeed
            + ", FastId='" + AppVars.FastId + "'");
    
    return buildRedirectHtml("Авторыбалка: Эликсир Блаженства", "main.php");
}
```

### 3. Логирование в MapAjax при пропуске

**Улучшить логирование в MapAjax.maybeTriggerAutoDrinkBlazOnThreshold() (строка 1248-1250):**

```java
int tiedBeforeSync = CharacterVitalsManager.snapshot().tied;
if (AppVars.FastNeed) {
    // ← УСИЛЕННОЕ ЛОГИРОВАНИЕ
    FileLogger.trace(TAG, "[MAPAJAX_SKIP_FASTNEED] "
            + "tied=" + tiedBeforeSync
            + ", threshold=" + threshold
            + ", FastNeed=" + AppVars.FastNeed
            + ", FastId='" + AppVars.FastId + "'"
            + ", FastNick='" + AppVars.FastNick + "'"
            + ", FastCount=" + AppVars.FastCount
            + ", thread=" + Thread.currentThread().getId());
    
    logAutoBlazDecision("decision", "skip_fast_need", tiedBeforeSync, threshold, 
            "reg=" + currentRegNum + ", fastId=" + AppVars.FastId);
    return null;
}
```

---

## 🟢 ВТОРОЙ ЭТАП: Реализовать полноценные пользовательские таймеры

### Архитектура портирования (из ПК-версии)

**Шаг 1: Создать AppTimer.java**

```java
package ru.neverlands.abclient.utils;

public class AppTimer {
    public long triggerTime;           // Когда срабатить (System.currentTimeMillis)
    public String description;        // "Пить Эликсир для рыбалки" etc.
    public String potion;             // "Эликсир Блаженства" или null
    public int drinkCount = 1;        // Сколько раз применить
    public boolean isRecurrent;       // Повторяется ли?
    public int everyMinutes;          // Каждые N минут
    public String destination;        // Куда идти (для навигации)
    public String complect;           // Какой комплект надеть
    public int id;                    // Уникальный ID
    public boolean isHerb;            // Травку ли использовать
    
    @Override
    public String toString() {
        // Аналог C# ToString() - показывает время и описание
        long dueInMs = triggerTime - System.currentTimeMillis();
        if (dueInMs < 0) dueInMs = 0;
        
        long hours = dueInMs / 3_600_000;
        long minutes = (dueInMs % 3_600_000) / 60_000;
        long seconds = (dueInMs % 60_000) / 1000;
        
        String timeLeft;
        if (hours > 0) {
            timeLeft = String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else if (minutes > 0) {
            timeLeft = String.format("%d:%02d", minutes, seconds);
        } else {
            timeLeft = String.format("0:%02d", seconds);
        }
        
        String result = (isRecurrent ? "*" : "") + ") Еще " + timeLeft + " - " + description;
        if (drinkCount > 1) {
            result += " [" + drinkCount + "]";
        }
        return result;
    }
}
```

**Шаг 2: Создать AppTimerManager.java** (thread-safe, как в C#)

```java
package ru.neverlands.abclient.utils;

import java.util.concurrent.CopyOnWriteArrayList;

public class AppTimerManager {
    private static final CopyOnWriteArrayList<AppTimer> timers = new CopyOnWriteArrayList<>();
    private static int nextId = 1;
    
    public static synchronized void addAppTimer(AppTimer timer) {
        if (timer.triggerTime <= System.currentTimeMillis()) {
            return; // Не добавляем прошлые таймеры
        }
        
        if (timers.isEmpty()) {
            timer.id = nextId++;
            timers.add(timer);
            return;
        }
        
        // Вставляем в отсортированном порядке по triggerTime
        boolean inserted = false;
        for (int i = 0; i < timers.size(); i++) {
            if (timer.triggerTime < timers.get(i).triggerTime) {
                timers.add(i, timer);
                inserted = true;
                break;
            }
        }
        if (!inserted) {
            timers.add(timer);
        }
        
        timer.id = nextId++;
    }
    
    public static AppTimer[] getTimers() {
        return timers.toArray(new AppTimer[0]);
    }
    
    public static void removeTimer(int index) {
        if (index >= 0 && index < timers.size()) {
            timers.remove(index);
        }
    }
    
    public static void clear() {
        timers.clear();
        nextId = 1;
    }
}
```

**Шаг 3: Добавить метод в MainPhp (или отдельный класс)**

```java
/**
 * Проверяет и обрабатывает срабатывание пользовательских таймеров.
 * Вызывается из ProcessMapAjax или ProcessMainPhp как часть main-loop.
 * 
 * Аналог: ПК-версия FormMain.UpdateTimers()
 */
public static void checkAndProcessUserTimers() {
    AppTimer[] timers = AppTimerManager.getTimers();
    
    for (int i = 0; i < timers.length; i++) {
        // Ещё не срабатил
        if (System.currentTimeMillis() <= timers[i].triggerTime) {
            continue;
        }
        
        // КРИТИЧНО: Если FastNeed активна, ждём её завершения
        if (AppVars.FastNeed) {
            Log.d(TAG, "[TIMER_TRACE] FastNeed active, дожидаемся её завершения");
            return; // Выходим - проверим в следующий цикл
        }
        
        // Обработка по типу таймера
        if (timers[i].potion != null && !timers[i].potion.isEmpty()) {
            // ТАЙМЕР С ЗЕЛЬЕМ
            FileLogger.trace(TAG, "[TIMER_TRACE] Таймер " + timers[i].id 
                    + " срабатил: " + timers[i].description
                    + ", выполняем: " + timers[i].potion
                    + ", count=" + timers[i].drinkCount);
            
            // Вызов быстрого действия
            FastActionManager.fastStart(
                    timers[i].potion,
                    AppVars.Profile != null ? AppVars.Profile.UserNick : "",
                    timers[i].drinkCount);
            
            // Если повторяющийся - создаём новый таймер на следующее время
            if (timers[i].isRecurrent) {
                AppTimer nextTimer = new AppTimer();
                nextTimer.triggerTime = timers[i].triggerTime + timers[i].everyMinutes * 60_000L;
                nextTimer.description = timers[i].description;
                nextTimer.potion = timers[i].potion;
                nextTimer.drinkCount = timers[i].drinkCount;
                nextTimer.isRecurrent = true;
                nextTimer.everyMinutes = timers[i].everyMinutes;
                
                AppTimerManager.removeTimer(i);
                AppTimerManager.addAppTimer(nextTimer);
                
                FileLogger.trace(TAG, "[TIMER_TRACE] Повторяющийся таймер: "
                        + "следующее срабатывание через " + timers[i].everyMinutes + " минут");
            } else {
                // Удаляем одноразовый таймер
                AppTimerManager.removeTimer(i);
            }
            
            // Звук + обновление UI
            // EventSounds.playTimer();  // Если есть
            return; // Больше таймеров не обрабатываем в этот цикл
        }
    }
}
```

**Шаг 4: Вызывать checkAndProcessUserTimers() из main loop**

В MapAjax.processMapAjax() добавить:

```java
// ← ДОБАВИТЬ перед/после других проверок
String timerResult = checkAndProcessUserTimers();
if (timerResult != null) {
    return timerResult;  // Таймер обработан, возвращаем результат
}
```

**Шаг 5: UI для управления таймерами**

```kotlin
// Создать новый фрагмент TimersFragment
// Интеграция в MainNavigationBottomSheet

// UI должен позволять:
// - Добавить новый таймер (выбрать зелье → выбрать время → повторяется ли)
// - Просмотреть список активных таймеров
// - Удалить таймер
```

---

## 📊 Итоговая архитектура

```
AutoFish/AutoDrink (любой триггер)
    ↓
if tied >= threshold or user_timer_fired:
    ├─→ FastActionManager.fastStart("Эликсир Блаженства", "")
    │   ├─ AppVars.FastNeed = true
    │   ├─ AppVars.FastId = "Эликсир Блаженства"  ← КЛЮЧЕВОЕ!
    │   ├─ AppVars.FastNick = ""
    │   ├─ AppVars.FastCount = 1
    │   └─ reloadMainFrame()
    │
    └─→ MainPhp.process(html)
        ├─→ processMainPhpFast(html)
        │   ├─ Видит FastNeed=true
        │   ├─ Видит FastId="Эликсир Блаженства"  ← НАХОДИТ ИНВЕНТАРЬ!
        │   ├─ Переходит на main.php?im=6 (эликсиры)
        │   ├─ Нашёл эликсир → генерирует форму
        │   ├─ POST → сервер применяет
        │   ├─ FastCount-- (если>1)
        │   └─ if FastCount==0 → FastCancel()
        │
        └─→ MapAjax.processMapAjax(html)  (параллельный вызов)
            └─→ checkAndProcessUserTimers()
                ├─ Проверяет AppVars.FastNeed
                ├─ Если FastNeed=true → пропускает таймеры
                └─ Если FastNeed=false → обрабатывает следующий таймер
```

---

## ✅ Чек-лист исправлений

- [ ] Добавить усиленное логирование в FastActionManager.fastStart()
- [ ] Добавить логирование в MainPhp.mainPhpAutoFishStep() (до/после fastStart)
- [ ] Добавить логирование в MapAjax.maybeTriggerAutoDrinkBlazOnThreshold()
- [ ] Запустить на устройстве и получить новые логи с подробными трассировками
- [ ] Проанализировать временные метки - найти где теряется FastId
- [ ] Создать AppTimer.java структуру
- [ ] Создать AppTimerManager.java с thread-safe операциями
- [ ] Создать checkAndProcessUserTimers() метод
- [ ] Интегрировать вызов в main-loop (MapAjax или MainPhp)
- [ ] Создать UI для управления таймерами
- [ ] Тестирование пользовательских таймеров на устройстве

