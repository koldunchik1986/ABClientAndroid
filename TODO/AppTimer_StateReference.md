# 🔖 СПРАВОЧНИК: Переменные состояния AppTimer

**Статус:** ✅ Справочник готов  
**Дата:** 02.04.2026

---

## ТАБЛИЦА ПЕРЕМЕННЫХ

| Переменная | Тип | Где | Назначение | Установщик | Проверяющий |
|----------|-----|-----|-----------|-----------|------------|
| `trigger Time` | `long` | `AppTimer` | Абсолютное время срабатывания (мс) | Менеджер таймеров | processDueTimers() |
| `potion` | `String` | `AppTimer` | Имя зелья ("elixir_light" и т.д.) | UI | executePotion Timer() |
| `destination` | `String` | `AppTimer` | Точка назначения для навигации | UI | executeDest inationTimer() |
| `complect` | `String` | `AppTimer` | Имя комплекта одежды | UI | executeComplect Timer() |
| `isRecur` | `boolean` | `AppTimer` | Повторяется ли таймер? | UI | executePotionTimer() |
| `everyMinutes` | `int` | `AppTimer` | Интервал повтора в минутах | UI | executePotionTimer() |
| **`NeverTimer`** | `long` | `AppVars` | **Серверный cooldown** | SetNeverTimer() | processDueTimers() |
| **`FastNeed`** | `boolean` | `AppVars` | **Выполняется fast-action** | fastStart() | processDueTimers() |
| **`FastId`** | `String` | `AppVars` | ID текущего fast-action'а | fastStart() | processMain PhpFast() |
| `WearComplect` | `String` | `AppVars` | Комплект для надевания | executeComplect Timer() | MainPhp |
| **`IsAutoFishing Paused`** | `boolean` | `AppVars` | **[НОВОЕ]** Рыбалка паузирована | 5-сек буфер | FishAjax Php |
| **`IsAutoHealing Paused`** | `boolean` | `AppVars` | **[НОВОЕ]** Лечение паузировано | 5-сек буфер | AutoHealing Manager |
| **`IsAutoBuffPaused`** | `boolean` | `AppVars` | **[НОВОЕ]** Баффы паузированы | 5-сек буфер | AutoBuffManager |
| **`IsAutoBossPaused`** | `boolean` | `AppVars` | **[НОВОЕ]** Босс паузирован | 5-сек буфер | ❌ НИКТО! |
| **`WasAutoFunctions BeforePause`** | `boolean` | `AppVars` | **[НОВОЕ]** Включали ли паузу? | 5-сек буфер | fastCancel() |

---

## СОСТОЯНИЯ И ПЕРЕХОДЫ

### Таблица состояний таймера

```
┌─────────────────────────────────────────────────────────────────┐
│                     СОСТОЯНИЯ ТАЙМЕРА                           │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┐        processDueTimers()        ┌─────────────────┐
│  WAITING         │ ─────────────────────────────→   │ CHECK_CONDITIONS │
│  (ещё не пришло) │                                   │                 │
│                  │                                   │ [1] triggerTime? │
└──────────────────┘                                   │ [2] NeverTimer?  │
      ↑                                                │ [3] FastNeed?    │
      │                                                └────────┬────────┘
      │                                                         │
      │                                                         ├─no→ SKIP
      │                                                         │
      │                                    ┌────────────────────┴─→ EXECUTE
      │                                    │ (одно действие в сек)
      │                                    ↓
      │                        ┌──────────────────────────┐
      │                        │  EXECUTING               │
      │                        │  (зелье/уход/комплект)   │
      │                        │  FastNeed = true         │
      │                        │  Cooldown идёт...        │
      │                        └────────────┬─────────────┘
      │                                     │
      │                                     ↓
      │                        ┌──────────────────────────┐
      │                        │  COOLDOWN                │
      │                        │  (ждём сервера)          │
      │                        │  FastNeed = true         │
      │                        │  NeverTimer активен      │
      │                        └────────────┬─────────────┘
      │                                     │
      │                                     ↓
      │                        ┌──────────────────────────┐
      │                        │  DONE                    │
      │                        │  FastNeed = false        │
      │                        │  Таймер удален           │
      │                        └──────────────────────────┘
      │
      └─────────────── (если isRecur=true, создаём новый таймер)
```

---

## ЖИЗНЕННЫЙ ЦИКЛ: ПОЛНЫЙ ПРИМЕР

### Сценарий: Таймер зелья каждые 2 минуты

```
╔════════════════════════════════════════════════════════════════╗
║                  ПОЛНЫЙ ЖИЗНЕННЫЙ ЦИКЛ                         ║
╚════════════════════════════════════════════════════════════════╝

[14:23:40 - User создаёт таймер]
    AppTimer timer = new AppTimer();
    timer.triggerTime = System.currentTimeMillis() + 120_000; // +2 мин
    timer.potion = "elixir_light";
    timer.isRecur = true;
    timer.everyMinutes = 2;
    AppTimerManager.getInstance().addAppTimer(timer);

[14:23:41 - processDueTimers() запущена (каждую секунду)]
    ПРОВЕРКА 1: nowMs (14:23:41) <= triggerTime (14:25:40)?
    → ДА (ещё 119 сек до срабатывания) → continue

[14:25:35 - processDueTimers()]
    ПРОВЕРКА 1: nowMs (14:25:35) <= triggerTime (14:25:40)?
    → ДА (осталось 5 сек)
    
    [НОВОЕ: 5-сек буфер]
    remainMs = 14:25:40 - 14:25:35 = 5000 мс
    if (remainMs > 0 && remainMs <= 5000) {
        IsAutoFishingPaused = true;
        IsAutoHealingPaused = true;
        FileLogger: "[TIMER_5SEC_BUFFER] timerId=1, remainMs=5000"
    }

[14:25:36-14:25:39 - На протяжении 4 секунд]
    processDueTimers() вызывается каждую секунду
    ПРОВЕРКА 1: всё ещё до срабатывания
    Avto-рыбалка проверяет: if (AppVars.IsAutoFishingPaused) return;
    → Авто-рыбалка ПАУЗИРОВАНА

[14:25:41 - СРАБАТЫВАНИЕ ТАЙМЕРА]
    ПРОВЕРКА 1: nowMs (14:25:41) > triggerTime (14:25:40)?
    → ДА! Время пришло!
    
    ПРОВЕРКА 2: nowMs (14:25:41) >= NeverTimer?
    → ДА (cooldown истёк или не был)
    
    ПРОВЕРКА 3: AppVars.FastNeed == false?
    → ДА (никакой fast-action не выполняется)
    
    ВЫПОЛНЕНИЕ:
    if (timer.potion != null) {
        executePotionTimerLocked(0, timer)
        → FastActionManager.fastStart("elixir_light", "MyNick", 1)
            → AppVars.FastNeed = true
            → log: "[POTION_TIMER_FIRED] id=1"
    }
    
    [Если повторяющийся]
    AppTimer nextTimer = timer.copy();
    nextTimer.id = 0;
    nextTimer.triggerTime = 14:25:40 + 120_000 = 14:27:40;
    addAppTimerInternalLocked(nextTimer);  // НОВЫЙ таймер на 14:27:40

[14:25:42-14:25:50 - Зелье пьётся]
    AppVars.FastNeed = true (весь процесс)
    processDueTimers() вызывается каждую секунду
    if (AppVars.FastNeed) return;  ← БЛОКИРУЕТ
    
    Авто-функции НЕ срабатывают (уже паузированы)

[14:25:52 - SET NEVER TIMER ИЗ СЕРВЕРА]
    map.js вызовет: SetNeverTimer(45000)
    AppVars.NeverTimer = 14:25:52 + 45_000 = 14:26:37

[14:25:53-14:26:37 - Cooldown]
    processDueTimers() проверяет:
    if (nowMs < AppVars.NeverTimer) continue;  ← ПРОПУСКАЕТ

[14:26:37 - Cooldown закончился]
    SetNeverTimer(0) вызвана из JS
    AppVars.NeverTimer = 14:26:37  (уже прошёл!)
    
    Следующий таймер может срабатывать

[14:26:37+ - fastCancel() вызвана]
    (когда цепочка fast-action закончится)
    FastActionManager.fastCancel("elixir_consumed")
    
    AppVars.FastNeed = false;
    
    if (AppVars.WasAutoFunctionsBeforePause) {
        IsAutoFishingPaused = false;
        IsAutoHealingPaused = false;
        IsAutoBuffPaused = false;
        IsAutoBossPaused = false;
        WasAutoFunctionsBeforePause = false;
        
        FileLogger: "[AUTO_FUNCTIONS_RESTORED]"
    }

[14:26:38 - Авто-функции возобновляются]
    processDueTimers() может вызывать следующие таймеры
    Авто-рыбалка проверяет:
    if (AppVars.IsAutoFishingPaused) → FALSE
    → Авто-рыбалка ВОЗОБНОВЛЯЕТСЯ

[14:27:40 - ВТОРОЕ СРАБАТЫВАНИЕ (повторение)]
    processDueTimers() проверяет НОВЫЙ таймер (который мы создали)
    triggerTime = 14:27:40
    Repeat: выполнить таймер снова...

╚════════════════════════════════════════════════════════════════╝
```

---

## МАТРИЦА БЛОКИРОВОК

### Кто блокирует кого?

```
                        ┌─────────────────────────┐
                        │  processDueTimers()     │
                        │  (один таймер в сек)    │
                        └────────────┬────────────┘
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        │                            │                            │
        ↓                            ↓                            ↓
    [Таймер 1:               [Таймер 2:               [Таймер 3:
     Зелье]                  Перемещение]             Комплект]
        │                        │                        │
        ├─→ FastNeed = true      ├─→ Запуск навигации    ├─→ Reload
        │   (блокирует            │   (текущее)           │   (текущее)
        │    другие таймеры)       │                       │
        │                         │                       │
        ↓                         ↓                       ↓
    [Авто-функции           [Авто-функции           [Авто-функции
     блокированы             могут работать          могут работать
     FastNeed=true]          (нет явной блокировки)] (нет явной блокировки)]
        │
        ├─→ Авто-рыбалка:         if (AppVars.FastNeed) return;
        │   НЕ срабатывает
        │
        ├─→ Авто-лечение:         if (AppVars.FastNeed) return;
        │   НЕ срабатывает
        │
        ├─→ Авто-баффы:            if (AppVars.FastNeed) return;
        │   НЕ срабатывают
        │
        ├─→ Авто-бой:              [Игнорирует FastNeed!]
        │   ✅ ПРОДОЛЖАЕТ РАБОТАТЬ!
        │
        └─→ Другие таймеры:        if (AppVars.FastNeed) return;
            ВСЕ блокированы


    5-СЕКУНДНЫЙ БУФЕР (НОВОЕ):
    ┌──────────────────────────────────────────────────────┐
    │ Если осталось < 5 сек до таймера зелья:             │
    │                                                      │
    │ IsAutoFishingPaused = true  ← Авто-рыбалка        │
    │ IsAutoHealingPaused = true  ← Авто-лечение         │
    │ IsAutoBuffPaused = true     ← Авто-баффы           │
    │ IsAutoBossPaused = true     ← Но это ИГНОРИРУЕТСЯ!  │
    │                                                      │
    │ После выполнения таймера → fastCancel() →           │
    │ Все флаги = false → Авто-функции возобновляются    │
    └──────────────────────────────────────────────────────┘
```

---

## ПРОВЕРОЧНЫЕ ВОПРОСЫ И ОТВЕТЫ

### Q1: Почему fastNeed не достаточно для 5-сек буфера?
**A:** FastNeed срабатывает ПОСЛЕ старта таймера. Буфер срабатывает ПЕРЕД, предотвращая конфликт заранее.

### Q2: Что если при срабатывании таймера сервер вернёт ошибку?
**A:** 
- fast-action цепочка обработает ошибку в processFast()
- fastCancel() будет вызвана в любом случае
- Флаги паузы восстановятся

### Q3: Как узнать, что буфер сработал?
**A:** Проверить логи:
```
[TIMER_5SEC_BUFFER] timerId=1, remainMs=4200
[POTION_TIMER_FIRED] id=1, potion='elixir_light'
[AUTO_FUNCTIONS_RESTORED]
```

### Q4: Может ли произойти двойное запуск таймера?
**A:** Нет, потому что:
1. processDueTimers() синхронизирован
2. После выполнения таймер УДАЛЯЕТСЯ из списка
3. listAppTimers отсортирован по времени

### Q5: Что если таймер срабатит ВО ВРЕМЯ боя?
**A:**
- Бой продолжает работать (IsAutoBossPaused не проверяется)
- FastNeed блокирует авто-функции (естественно)
- После боя может срабатить следующий таймер

### Q6: Как долго держать флаги паузы?
**A:** До fastCancel():
```
fastStart() → AppVars.FastNeed = true
             → IsAutoFishing Paused = true (из буфера)
             
             ... цепочка fast-action ...
             
fastCancel() → AppVars.FastNeed = false
             → IsAutoFishing Paused = false
```

---

## БЫСТРАЯ ДИАГНОСТИКА

### Если авто-рыбалка паузируется без причины

**Проверить логи:**
```
grep -r "TIMER_5SEC_BUFFER\|AUTO_FUNCTIONS_RESTORED" logs/
```

**Проверить код:**
```java
if (AppVars.IsAutoFishingPaused) {
    Log.d(TAG, "[DIAGNOSIS] Fishing paused, reasons: " +
               "WasAutoFunctions=" + AppVars.WasAutoFunctionsBeforePause +
               ", FastNeed=" + AppVars.FastNeed);
}
```

### Если буфер н сработал

**Проверить условия в processDueTimers():**
```java
long remainMs = timer.triggerTime - nowMs;
Log.d(TAG, "[DIAGNOSIS_BUFFER] remainMs=" + remainMs + 
           ", isPotion=" + !TextUtils.isEmpty(timer.potion) +
           ", WasAutoFunctions=" + AppVars.WasAutoFunctionsBeforePause);
```

### Если авто-функции не восстановились

**Проверить в fastCancel():**
```java
Log.d(TAG, "[DIAGNOSIS_CANCEL] FastNeed was=" + AppVars.FastNeed +
           ", WasAutoFunctions=" + AppVars.WasAutoFunctionsBeforePause);
```

---

## ПРОИЗВОДИТЕЛЬНОСТЬ

### Влияние 5-сек буфера на производительность

| Метрика | Значение | Влияние |
|---------|----------|--------|
| Дополнительные проверки в сек | 1 | НИЧТОЖНО |
| Дополнительные вычисления | `remainMs = triggerTime - nowMs` | НИЧТОЖНО |
| Память (новые поля) | 5 * бул + 1 * бул = 6 байт | НИЧТОЖНО |
| IO при логировании | ~100 байт при срабатывании | НОРМАЛЬНО |

**Итог: НОЛЬ негативного влияния на производительность**

---

## РЕГРЕССИОННЫЕ ТЕСТЫ

### Что проверить, чтобы не сломать

```
[✓] Непрерывное срабатывание таймеров (не более 1 в сек)
[✓] Повторяющиеся таймеры (isRecur=true)
[✓] Таймеры за разных персонажей (мультиаккаунт)
[✓] Таймеры с разными cooldown'ами (NeverTimer)
[✓] Авто-бой во время таймера проходит
[✓] Не потеря таймеров при перезагрузке приложения
[✓] Звуковые сигналы таймеров работают
[✓] Комплекты одежды надеваются
[✓] Перемещение задействуется
```

---

## РЕЗЮМЕ

**AppTimer и AppTimerManager** — надёжная система таймеров с защитой от конфликтов.

**Ключевые переменные:**
- `triggerTime` — когда срабатывать
- `NeverTimer` — серверный cooldown
- `FastNeed` — другой fast-action выполняется

**Предложение:** добавить 5-сек буфер для гладкого перехода авто-функций

**Риск:** ✅ МИНИМАЛЬНЫЙ  
**Выигрыш:** ✅ ЗНАЧИТЕЛЬНЫЙ

---

*Справочник завершён. Держивайте документы при внедрении!*
