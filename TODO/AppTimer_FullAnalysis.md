# 📋 ПОЛНЫЙ АНАЛИЗ: AppTimer, NeverTimer и логика срабатывания

**Дата:** 02.04.2026  
**Статус:** ✅ Анализ завершен  
**Автор:** Code Assistant

---

## НАВИГАЦИЯ ПО ДОКУМЕНТУ

1. [Краткая сводка](#краткая-сводка)
2. [Архитектура компонентов](#архитектура-компонентов)
3. [Класс AppTimer](#класс-apptimer)
4. [Класс AppTimerManager](#класс-apptimermanager)
5. [Переменные AppVars](#переменные-appvars)
6. [Логика срабатывания таймера](#логика-срабатывания-таймера)
7. [Возможность добавления 5-секундного буфера](#возможность-добавления-5-секундного-буфера)
8. [Примеры кода](#примеры-кода-реализации)

---

## КРАТКАЯ СВОДКА

| Аспект | Ответ |
|--------|-------|
| **Где класс AppTimer?** | `app/src/main/java/ru/neverlands/abclient/model/AppTimer.java` |
| **Где менеджер?** | `app/src/main/java/ru/neverlands/abclient/manager/AppTimerManager.java` |
| **Как запускается?** | `MainActivity.startTimer()` → `processDueTimers()` каждую секунду |
| **Где проверка условий?** | `processDueTimers()` линия 174-220 |
| **Какие переменные?** | `AppVars.NeverTimer`, `AppVars.FastNeed` |
| **Что вызывает действие?** | `FastActionManager.fastStart()` для зелья |
| **Можно ли добавить буфер?** | ✅ **ДА, очень возможно** |
| **Уровень сложности:** | Средний (модификация логики + координация) |

---

## АРХИТЕКТУРА КОМПОНЕНТОВ

```
┌────────────────────────────────────────────────────────────────┐
│                         ЭКРАН (UI)                              │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ QuickButtonsPanel                                        │   │
│  │ - Отображает список таймеров                             │   │
│  │ - Показывает "Еще mm:ss - описание [count]"             │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
            ↓
┌────────────────────────────────────────────────────────────────┐
│                       ЛОГИКА (Backend)                          │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ MainActivity.startTimer() → processDueTimers() [каждую 1s]   │
│  └──────────────────────────────────────────────────────────┘   │
│            ↓                                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ AppTimerManager.processDueTimers()                      │   │
│  │   - Проверка triggerTime                               │   │
│  │   - Проверка AppVars.NeverTimer (серверный cooldown)   │   │
│  │   - Проверка AppVars.FastNeed (другой fast-action)     │   │
│  │   - Выбор типа действия (зелье/переместить/комплект)   │   │
│  └──────────────────────────────────────────────────────────┘   │
│            ↓                                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Выполнение действия                                     │   │
│  │   - Зелье → FastActionManager.fastStart()               │   │
│  │   - Перемещение → AutoFunctionsManager.startAutoMoving()   │
│  │   - Комплект → AppVars.WearComplect + reload            │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
            ↓
┌────────────────────────────────────────────────────────────────┐
│                      ХРАНИЛИЩЕ (Persistence)                    │
│                                                                  │
│  SharedPreferences: "timers_json_[NICK]"                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ [                                                        │   │
│  │   {"id": 1, "triggerTime": 1712058240000, ...},         │   │
│  │   {"id": 2, "triggerTime": 1712058300000, ...},         │   │
│  │   ...                                                     │   │
│  │ ]                                                         │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
```

---

## КЛАСС AppTimer

**Файл:** `app/src/main/java/ru/neverlands/abclient/model/AppTimer.java` (99 строк)

### Определение

```java
public class AppTimer {
    public long triggerTime = 0L;          // ⏰ Абсолютное время срабатывания
    public String description = "";        // 📝 Описание для UI
    public String potion = "";             // 🧪 Имя зелья (e.g., "elixir_light")
    public int drinkCount = 0;             // #️⃣ Кол-во приложений
    public boolean isRecur = false;        // 🔄 Повторяется ли?
    public int everyMinutes = 0;           // ⏲️ Интервал повтора (мин)
    public String destination = "";        // 🗺️ Точка назначения
    public String complect = "";           // 👕 Комплект одежды
    public int id = 0;                     // 🆔 Уникальный ID
    public boolean isHerb = false;         // 🌿 Это трава исцеления?
}
```

### Ключевой метод: toDisplayString()

```java
public String toDisplayString(long nowMs) {
    // Формирует строку для UI: "1) Еще 00:45 - Зелье здоровья [3]"
    
    long triggerForDisplay = triggerTime;
    if (isHerb) {
        // ВАЖНО: для трав смещение -30 минут (как в ПК клиенте)
        triggerForDisplay -= TimeUnit.MINUTES.toMillis(30);
    }
    
    if (triggerForDisplay < nowMs) {
        // Время пришло или прошло
        return "0:00";
    } else {
        // Время ещё не пришло
        long remainMs = triggerForDisplay - nowMs;
        return formatRemain(remainMs);  // "mm:ss" формат
    }
}
```

### Вывод

- **AppTimer — это просто КОНТЕЙНЕР данных!**
- Логика выполнения полностью в `AppTimerManager`
- Поле `triggerTime` хранит абсолютное время в миллисекундах

---

## КЛАСС AppTimerManager

**Файл:** `app/src/main/java/ru/neverlands/abclient/manager/AppTimerManager.java` (412 строк)

### Структура

```java
public class AppTimerManager {
    private static AppTimerManager instance;
    private final Context appContext;
    private final SharedPreferences prefs;
    private final List<AppTimer> listAppTimers = new ArrayList<>();  // ⭐ Основной список
    private String loadedStorageKey = "";
    
    // Singleton
    public static synchronized AppTimerManager getInstance(Context context) { ... }
    
    // Основной метод
    public synchronized void processDueTimers() { ... }
    
    // Вспомогательные методы
    private void executePotionTimerLocked(int index, AppTimer timer) { ... }
    private void executeDestinationTimerLocked(int index, AppTimer timer) { ... }
    private void executeComplectTimerLocked(int index, AppTimer timer) { ... }
}
```

---

## ЛОГИКА СРАБАТЫВАНИЯ ТАЙМЕРА

### 🎯 ГЛАВНЫЙ МЕТОД: processDueTimers()

Вызывается **каждую секунду** из `MainActivity.startTimer()`:

```java
public synchronized void processDueTimers() {
    ensureLoadedForCurrentProfileLocked();  // Загружаем таймеры для текущего персонажа
    if (listAppTimers.isEmpty()) {
        return;  // Нечего обрабатывать
    }

    long nowMs = System.currentTimeMillis();

    // Проходим по всем таймерам (отсортированы по triggerTime)
    for (int index = 0; index < listAppTimers.size(); index++) {
        AppTimer timer = listAppTimers.get(index);
        
        // ════════════════════════════════════════════════════════════
        // ПРОВЕРКА 1️⃣ : Время срабатывания пришло?
        // ════════════════════════════════════════════════════════════
        if (nowMs <= timer.triggerTime) {
            continue;  // ❌ Ещё не пришло, переходим к следующему
        }
        
        // ════════════════════════════════════════════════════════════
        // ПРОВЕРКА 2️⃣ : Серверный cooldown (NeverTimer) истек?
        // ════════════════════════════════════════════════════════════
        // AppVars.NeverTimer = абсолютное время, когда сервер позволит
        // следующее действие (например, следующую рыбалку, ход в бою)
        if (nowMs < AppVars.NeverTimer) {
            long deltaMs = AppVars.NeverTimer - nowMs;
            String msg = "[TIMER_DEFER_NEVERTIMER] Дожидаемся NeverTimer, " +
                         "deltaMs=" + deltaMs + ", timerId=" + timer.id;
            Log.w(TAG, msg);
            FileLogger.trace("app_timer", msg);
            continue;  // ❌ Серверный cooldown ещё активен, пропускаем
        }
        
        // ════════════════════════════════════════════════════════════
        // ПРОВЕРКА 3️⃣ : Другой fast-action (зелье) не выполняется?
        // ════════════════════════════════════════════════════════════
        // AppVars.FastNeed = true означает, что сейчас выполняется
        // другое быстрое действие (питье зелья, например)
        if (AppVars.FastNeed) {
            return;  // ❌ Блокируем ВСЕ таймеры, пока FastNeed = true
        }
        
        // ════════════════════════════════════════════════════════════
        // ✅ ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ! ВЫПОЛНЯЕМ ДЕЙСТВИЕ
        // ════════════════════════════════════════════════════════════
        
        if (!TextUtils.isEmpty(timer.potion)) {
            // Зелье
            executePotionTimerLocked(index, timer);
            return;  // ⚠️ Один таймер за итерацию!
        }

        if (!TextUtils.isEmpty(timer.destination)) {
            // Перемещение
            executeDestinationTimerLocked(index, timer);
            return;  // ⚠️ Один таймер за итерацию!
        }

        if (!TextUtils.isEmpty(timer.complect)) {
            // Комплект одежды
            executeComplectTimerLocked(index, timer);
            return;  // ⚠️ Один таймер за итерацию!
        }

        // Простой таймер (без действия) — просто удалить
        listAppTimers.remove(index);
        index--;
        playTimerSignalIfEnabledLocked();
    }
}
```

### ⚠️ КРИТИЧНАЯ ДЕТАЛЬ: Один таймер за проход!

```
    ┌─────────────────────────────────────────┐
    │ ВАЖНО для понимания!                    │
    │                                         │
    │ processDueTimers() обрабатывает         │
    │ **ТОЛЬКО ОДИН таймер-действие**         │
    │ за одну итерацию (за одну секунду)!     │
    │                                         │
    │ После `return` из executePotion...()    │
    │ цикл foreach ПРЕРЫВАЕТСЯ.               │
    │                                         │
    │ Остальные таймеры ждут следующей       │
    │ секунды!                                │
    │                                         │
    │ Это предотвращает конфликты между       │
    │ одновременным выполнением действий.    │
    └─────────────────────────────────────────┘
```

---

## ПЕРЕМЕННЫЕ AppVars

### AppVars.NeverTimer (КРИТИЧНАЯ ✨)

```java
public static volatile long NeverTimer = 0L;  // в AppVars.java
```

**Назначение:** Серверный cooldown после действия  
**Когда устанавливается:** JavaScript вызывает `SetNeverTimer(msLeft)` из `map.js`  
**Логика:**
```
NeverTimer = currentTime + msLeft
NeverTimer содержит абсолютное время (в будущем) когда cooldown закончится

Если nowMs < NeverTimer → cooldown ещё активен → пропускаем таймер
Если nowMs >= NeverTimer → cooldown закончился → можно срабатывать
```

**Пример потока:**
```
14:23:41.000 - Map.js выполнил рыбалку
             - SetNeverTimer(45000) вызвана
             - AppVars.NeverTimer = 14:23:86.000

14:23:42.000 - processDueTimers(): check NeverTimer
             - nowMs = 14:23:42.000
             - NeverTimer = 14:23:86.000
             - nowMs < NeverTimer → пропускаем таймер ❌

14:23:43.000 - processDueTimers(): Всё ещё ждём
             - Ещё 43 секунды...

14:24:26.000 - processDueTimers(): check NeverTimer
             - nowMs = 14:24:26.000
             - NeverTimer = 14:23:86.000
             - nowMs >= NeverTimer → МОЖНО СРАБАТЫВАТЬ ✅
             - executePotionTimer...()
```

### AppVars.FastNeed

```java
public static volatile boolean FastNeed = false;  // в AppVars.java
```

**Назначение:** Флаг, что сейчас выполняется быстрое действие  
**Когда устанавливается:**
- `= true` в `FastActionManager.fastStart()` 
- `= false` в `FastActionManager.fastCancel()`

**Логика в processDueTimers():**
```java
if (AppVars.FastNeed) {
    return;  // Блокируем ВСЕ таймеры, пока FastNeed = true
}
```

**Пример потока:**
```
14:24:30.000 - Таймер зелья срабатывает
             - FastActionManager.fastStart("elixir_light", targetNick, 1)
             - AppVars.FastNeed = true

14:24:31.000 - processDueTimers(): 
             - if (AppVars.FastNeed) return;  ← выходим, другие таймеры ждут

14:24:32.000 - Зелье всё ещё пьется...
             - processDueTimers(): return (FastNeed всё ещё true)

14:24:35.000 - Cooldown закончился
             - FastActionManager.fastCancel("elixir_consumed")
             - AppVars.FastNeed = false

14:24:36.000 - processDueTimers(): 
             - if (AppVars.FastNeed) больше не true
             - Можно обрабатывать следующий таймер ✅
```

---

## ЗАПУСК ДЕЙСТВИЙ

### 1️⃣ Таймер зелья: executePotionTimerLocked()

```java
private void executePotionTimerLocked(int index, AppTimer timer) {
    // Целевой персонаж (для мультиаккаунта)
    String targetNick = resolveCurrentNick();
    int drinkCount = timer.drinkCount > 0 ? timer.drinkCount : 1;

    // Удаляем таймер из списка
    listAppTimers.remove(index);

    // Если повторяющийся — добавляем следующее срабатывание
    if (timer.isRecur && timer.everyMinutes > 0) {
        AppTimer nextTimer = timer.copy();
        nextTimer.id = 0;
        nextTimer.triggerTime = timer.triggerTime + (timer.everyMinutes * 60_000L);
        addAppTimerInternalLocked(nextTimer);
    }

    // Сохраняем изменения в SharedPreferences
    persistLocked();

    // 🔔 Звуковой сигнал
    playTimerSignalIfEnabledLocked();

    // ⭐⭐⭐ ГЛАВНОЕ: ЗАПУСК ДЕЙСТВИЯ ⭐⭐⭐
    FastActionManager.fastStart(timer.potion, targetNick, drinkCount);
    // После этого вызова:
    // - AppVars.FastNeed = true
    // - Начинается цепочка: построение запроса → отправка → обработка ответа

    Log.d(TAG, "[POTION_TIMER_FIRED] id=" + timer.id + 
           ", potion='" + timer.potion + "', drinkCount=" + drinkCount);
    FileLogger.trace("app_timer", msg);
}
```

**Цепочка вызовов:**
```
executePotionTimerLocked()
    ↓
FastActionManager.fastStart(potion, nick, count)
    ↓
AppVars.FastNeed = true
AppVars.FastId = potion
    ↓
[Готовимся отправить запрос]
    ↓
[Когда приходит main.php]
FastActionManager.processMainPhpFast() обрабатывает ответ
    ↓
[После завершения cooldown'а]
FastActionManager.fastCancel("reason")
    ↓
AppVars.FastNeed = false
```

### 2️⃣ Таймер перемещения: executeDestinationTimerLocked()

```java
private void executeDestinationTimerLocked(int index, AppTimer timer) {
    listAppTimers.remove(index);
    persistLocked();

    playTimerSignalIfEnabledLocked();

    // ⭐ Запуск навигации
    AutoFunctionsManager.getInstance(appContext)
        .startAutoMoving(timer.destination);

    Log.d(TAG, "destination timer fired, id=" + timer.id +
           ", destination=" + timer.destination);
}
```

### 3️⃣ Таймер комплекта: executeComplectTimerLocked()

```java
private void executeComplectTimerLocked(int index, AppTimer timer) {
    listAppTimers.remove(index);
    persistLocked();

    // Установить комплект одежды
    AppVars.WearComplect = timer.complect;

    playTimerSignalIfEnabledLocked();

    // Перезагрузить main.php с параметром надевания
    reloadMainPhpInf();

    Log.d(TAG, "complect timer fired, id=" + timer.id +
           ", complect=" + timer.complect);
}
```

---

## ВОЗМОЖНОСТЬ ДОБАВЛЕНИЯ 5-СЕКУНДНОГО БУФЕРА

### 🎯 ОТВЕТ: ДА, ПОЛНОСТЬЮ ВОЗМОЖНО

**Уровень сложности:** Средний  
**Файлы для изменения:**
1. `AppVars.java` — добавить флаги паузы
2. `AppTimerManager.java` — добавить логику буфера в processDueTimers()
3. `FastActionManager.java` — восстановить состояние в fastCancel()

### Основная идея

```
За 5 сек до срабатывания таймера зелья:
    ↓
Установить флаги паузы для авто-функций
(но НЕ для авто-боя)
    ↓
После выполнения зелья:
    ↓
Восстановить состояние авто-функций
```

### Почему это безопасно?

1. **processDueTimers() синхронизирован** → нет race conditions
2. **Флаги паузы легко добавить в AppVars** → просто новые поля
3. **fastCancel() вызывается всегда** → гарантированное восстановление
4. **Авто-бой проверяет свои флаги** → не будет заблокирован

---

## ПРИМЕРЫ КОДА РЕАЛИЗАЦИИ

### Шаг 1: Добавить флаги паузы в AppVars

```java
// В app/src/main/java/ru/neverlands/abclient/utils/AppVars.java

public class AppVars {
    // ... существующие поля ...
    
    // ════════════════════════════════════════════════════════════
    // 5-СЕКУНДНЫЙ БУФЕР ТАЙМЕРА
    // ════════════════════════════════════════════════════════════
    public static volatile boolean IsAutoFishingPaused = false;
    public static volatile boolean IsAutoHealingPaused = false;
    public static volatile boolean IsAutoBuffPaused = false;
    public static volatile boolean IsAutoBossPaused = false;
    public static volatile boolean WasAutoFunctionsBeforePause = false;
}
```

### Шаг 2: Добавить логику буфера в AppTimerManager

```java
// В AppTimerManager.java, внутри processDueTimers()

public synchronized void processDueTimers() {
    ensureLoadedForCurrentProfileLocked();
    if (listAppTimers.isEmpty()) {
        return;
    }

    long nowMs = System.currentTimeMillis();

    for (int index = 0; index < listAppTimers.size(); index++) {
        AppTimer timer = listAppTimers.get(index);
        
        // ════════════════════════════════════════════════════════════
        // ⭐ НОВОЕ: 5-СЕКУНДНЫЙ БУФЕР ПЕРЕД СРАБАТЫВАНИЕМ
        // ════════════════════════════════════════════════════════════
        if (nowMs <= timer.triggerTime) {
            long remainMs = timer.triggerTime - nowMs;
            
            // Если осталось 0-5000ms до срабатывания таймера-зелья
            // И таймер ещё не был занесен в буфер
            if (remainMs > 0 && remainMs <= 5000L && 
                !TextUtils.isEmpty(timer.potion) &&
                !AppVars.WasAutoFunctionsBeforePause) {
                
                // Включаем паузу авто-функций
                AppVars.IsAutoFishingPaused = true;
                AppVars.IsAutoHealingPaused = true;
                AppVars.IsAutoBuffPaused = true;
                AppVars.IsAutoBossPaused = true;
                
                AppVars.WasAutoFunctionsBeforePause = true;
                
                String msg = "[TIMER_5SEC_BUFFER] Паузим авто-функции, " +
                             "timerId=" + timer.id + ", remainMs=" + remainMs +
                             ", potion='" + timer.potion + "'";
                Log.d(TAG, msg);
                FileLogger.trace("app_timer", msg);
            }
            
            continue;  // Ещё не пришло время для srабатывания
        }

        // Проверка NeverTimer
        if (nowMs < AppVars.NeverTimer) {
            long deltaMs = AppVars.NeverTimer - nowMs;
            String msg = "[TIMER_DEFER_NEVERTIMER] Дожидаемся NeverTimer, " +
                         "deltaMs=" + deltaMs + ", timerId=" + timer.id;
            Log.w(TAG, msg);
            FileLogger.trace("app_timer", msg);
            continue;
        }

        // Проверка FastNeed
        if (AppVars.FastNeed) {
            return;
        }

        // Выполнение действия
        if (!TextUtils.isEmpty(timer.potion)) {
            executePotionTimerLocked(index, timer);
            // ⭐ После срабатывания таймера зелья сбрасываем флаг
            AppVars.WasAutoFunctionsBeforePause = false;
            return;
        }

        if (!TextUtils.isEmpty(timer.destination)) {
            executeDestinationTimerLocked(index, timer);
            return;
        }

        if (!TextUtils.isEmpty(timer.complect)) {
            executeComplectTimerLocked(index, timer);
            return;
        }

        listAppTimers.remove(index);
        index--;
        playTimerSignalIfEnabledLocked();
    }
}
```

### Шаг 3: Восстановить состояние в FastActionManager

```java
// В FastActionManager.java, метод fastCancel()

public static void fastCancel(String reason) {
    // ... существующий код ...
    
    // Очистить флаг FastNeed
    AppVars.FastNeed = false;
    AppVars.FastId = null;
    
    // ════════════════════════════════════════════════════════════
    // ⭐ НОВОЕ: ВОССТАНОВИТЬ АВТО-ФУНКЦИИ
    // ════════════════════════════════════════════════════════════
    if (AppVars.WasAutoFunctionsBeforePause) {
        AppVars.IsAutoFishingPaused = false;
        AppVars.IsAutoHealingPaused = false;
        AppVars.IsAutoBuffPaused = false;
        AppVars.IsAutoBossPaused = false;
        
        AppVars.WasAutoFunctionsBeforePause = false;
        
        String msg = "fastCancel: restored auto-functions, reason=" + reason;
        Log.d(TAG, msg);
        FileLogger.trace(TAG, msg);
    }
    
    // ... остальной код ...
}
```

### Шаг 4: Использовать флаги паузы в авто-функциях

Каждая авто-функция должна проверять свой флаг паузы:

```java
// В FishAjaxPhp.java (авто-рыбалка):

public void processDueTimers() {
    // ... существующий код ...
    
    // Проверка: не паузирована ли авто-рыбалка?
    if (AppVars.IsAutoFishingPaused) {
        Log.d(TAG, "[AUTOFISH_PAUSE] Fishing paused, waiting for timer to complete");
        return;  // Пропускаем цикл рыбалки
    }
    
    // ... остальная логика рыбалки ...
}

// В AutoHealingManager.java (авто-лечение):

public synchronized void processDueTimers() {
    if (AppVars.IsAutoHealingPaused) {
        Log.d(TAG, "[AUTOHEALING_PAUSE] Healing paused");
        return;
    }
    
    // ... остальная логика лечения ...
}

// В AutoBuffManager.java (авто-баффы):

public void processDueTimers() {
    if (AppVars.IsAutoBuffPaused) {
        Log.d(TAG, "[AUTOBUFF_PAUSE] Buffs paused");
        return;
    }
    
    // ... остальная логика баффов ...
}

// ⭐ НЕ БЛОКИРУЕМ АВТО-БОЙ!
// В FightAuto.java:

public synchronized void processDueTimers() {
    // Авто-бой НЕ проверяет IsAutoBossPaused
    // Бой продолжает работать независимо
    
    // ... логика боя ...
}
```

---

## ТАБЛИЦА СРАВНЕНИЯ: ТЕКУЩЕЕ vs ПРЕДЛОЖЕННОЕ

| Сценарий | Текущее поведение | Предложенное поведение | Результат |
|----------|---|---|---|
| Таймер зелья за 10 сек | Авто-рыбалка работает | Авто-рыбалка работает | ✓ Без изменений |
| Таймер зелья за 4 сек | Авто-рыбалка работает | Авто-рыбалка паузирована | ✓ Конфликт предотвращен |
| Таймер неожиданно срабатывает | FastNeed блокирует все | FastNeed + 5-сек буфер | ✓ Двойная защита |
| После срабатывания зелья | FastNeed отключен | FastNeed отключен + восстановлена пауза | ✓ Чистое состояние |
| Авто-бой во время таймера | Работает | Продолжает работать | ✓ Не трогаем боевик |

---

## ПРОВЕРКА ПЕРЕД СДАЧЕЙ (ЧЕК-ЛИСТ)

### Функциональность

- [ ] 5-секундный буфер активируется за 5 сек до таймера зелья
- [ ] В логах видны сообщения `[TIMER_5SEC_BUFFER]` и `[AUTO_FUNCTION_RESUMED]`
- [ ] Авто-рыбалка паузируется за 5 сек до таймера зелья
- [ ] Авто-рыбалка возобновляется после выполнения зелья
- [ ] Авто-бой **НЕ** паузируется ни перед, ни после таймера
- [ ] Таймеры с количеством > 1 работают корректно

### Интеграция

- [ ] Все новые поля в AppVars используют `volatile`
- [ ] Fast-action цепочки не нарушены
- [ ] Нет deadlock'ов с synchronized методами
- [ ] Логирование добавлено через FileLogger

### Тестирование

- [ ] На одиночном персонаже
- [ ] На мультиаккаунте (разные персонажи)
- [ ] С разными типами зелий
- [ ] С повторяющимися таймерами
- [ ] Во время активного боя

---

## ВЫВОДЫ

### ✅ Что работает хорошо

1. **NeverTimer** — надежная защита от конфликтов с сервером
2. **FastNeed** — блокирует конкурирующие fast-action'ы
3. **Синхронизация** — processDueTimers синхронизирован
4. **Персистентность** — таймеры сохраняются между сессиями

### ⚠️ Потенциальные проблемы (текущие)

1. Авто-функции могут срабатывать в момент срабатывания таймера
2. Нет "мягкого" снижения конфликтов за 5 сек до события
3. Авто-функции могут "завибрировать" при быстрых таймерах

### 🎯 Решение (предложенное)

1. Добавить 5-секундный буфер перед срабатыванием таймера
2. Установить флаги паузы для авто-функций (но не для боя)
3. Восстановить состояние после выполнения действия
4. Улучшить логирование для отладки конфликтов

### 📊 Уровень риска реализации

| Аспект | Риск | Обоснование |
|--------|------|-------------|
| Логика буфера | **НИЗКИЙ** | Просто арифметика времени |
| Синхронизация | **НИЗКИЙ** | Используем существующие synchronized блоки |
| Интеграция | **СРЕДНИЙ** | Нужно убедиться в правильности восстановления |
| Авто-бой | **ОЧЕНЬ НИЗКИЙ** | Не трогаем, просто не проверяем флаг |
| Производительность | **ОЧЕНЬ НИЗКИЙ** | Только одна дополнительная проверка в секунду |

---

## СПРАВОЧНАЯ ИНФОРМАЦИЯ

### Файлы для редактирования

```
app/src/main/java/ru/neverlands/abclient/
├── utils/AppVars.java                         [+5 новых полей]
├── manager/AppTimerManager.java               [+15 строк логики]
├── manager/FastActionManager.java             [+10 строк восстановления]
├── postfilter/FishAjaxPhp.java                [+1 проверка]
├── manager/AutoHealingManager.java            [+1 проверка]
├── manager/AutoBuffManager.java               [+1 проверка]
└── manager/FightAuto.java                     [БЕЗ ИЗМЕНЕНИЙ!]
```

### Константы, которые можно скорректировать

```java
private static final long TIMER_5SEC_BUFFER_MS = 5000L;  // 5 секунд
// Можно изменить на 3000L (3 сек) или 7000L (7 сек) при необходимости
```

### Как откатить, если что-то сломалось

```java
// Быстрый откат: удалить из processDueTimers():
if (remainMs > 0 && remainMs <= 5000L && !TextUtils.isEmpty(timer.potion)) {
    // Закомментировать эту секцию целиком
}

// И в fastCancel():
if (AppVars.WasAutoFunctionsBeforePause) {
    // Закомментировать эту секцию целиком
}
```

---

## ЧАСТО ЗАДАВАЕМЫЕ ВОПРОСЫ (FAQ)

### Q: Почему FastNeed не достаточно для защиты?
**A:** FastNeed срабатывает ПОСЛЕ старта таймера. Буфер — это ПЕРЕД началом, предотвращая конфликт заранее.

### Q: Почему не паузируем авто-бой?
**A:** Бой — критичная функция. Таймеры зелья часто срабатывают ВО ВРЕМЯ боя. Если паузировать бой, можно пропустить ход противника.

### Q: Что если таймер зелья срабатит, а потом срабатит таймер перемещения?
**A:** Вторая проверка: FastNeed будет true, второй таймер пропустится, пока не закончится первый.

### Q: Как это влияет на производительность?
**A:** Минимально. Только одна дополнительная проверка в processDueTimers() каждую секунду.

### Q: Можно ли сделать буфер 10 секунд вместо 5?
**A:** Да, нужно изменить только одно число в коде: `remainMs <= 5000L` → `remainMs <= 10000L`

### Q: Что если повторяющийся таймер сработает каждую минуту?
**A:** Работает нормально. Каждые 60 сек будет 5-сек буфер, потом 55 сек обычной работы.

---

**Конец документа.** Анализ выполнен 02.04.2026.
