# Модуль AppTimer (Таймеры действий)

**Версия:** 1.1.4  
**Статус:** Полностью реализован с NeverTimer интеграцией и приоритизацией  
**Последнее обновление:** 2026-04-02

---

## 1. Назначение и область применения

Модуль AppTimer реализует систему пользовательских таймеров для отложенного выполнения автоматических действий:

| Тип действия | Описание | Пример |
|--------------|---------|---------|
| **Питье зелья** | Автоматическое питье выбранного зелья через N минут | "Пить Зелье Сильной Спины каждые 5 минут" |
| **Перемещение** | Автоматический переход на указанное место | "Идти в Город Форпост через 10 минут" |
| **Смена комплекта** | Автоматическое переодевание в комплект | "Надеть комплект 'На рыбалку' через 3 минуты" |
| **Напоминание** | Простой таймер (звуковой сигнал) | "Напомнить через 15 минут" |

## 2. Архитектура и компоненты

### 2.1 Основные классы

```
AppTimer (model)
    ├─ id: int                    // уникальный ID таймера
    ├─ triggerTime: long          // время срабатывания (System.currentTimeMillis)
    ├─ potion: String             // название зелья (если действие = питье)
    ├─ destination: String        // название места перемещения
    ├─ complect: String           // название комплекта для смены
    ├─ drinkCount: int            // количество повторений питья (по умолчанию 1)
    ├─ isRecur: boolean           // повторяется ли таймер
    ├─ everyMinutes: int          // интервал повторения в минутах
    └─ description: String        // описание для UI
```

```
AppTimerManager (singleton)
    ├─ processDueTimers()         // вызывается каждую секунду из MainActivity
    ├─ addAppTimer(timer)         // добавить новый таймер
    ├─ updateTimer(id, timer)     // изменить таймер
    ├─ removeTimer(id)            // удалить таймер
    ├─ getAllTimers()             // получить список всех таймеров
    └─ setAppTimers(list)         // установить весь список (для загрузки профиля)
```

### 2.2 Принцип работы

```
UI (Timers Fragment)
    ↓ (пользователь кликает "Добавить таймер")
AppTimerManager.addAppTimer(AppTimer)
    ↓ (сохранение в SharedPreferences)
processDueTimers() [вызывается каждую секунду]
    ├─ Получить текущее время: nowMs = System.currentTimeMillis()
    ├─ Для каждого таймера:
    │   ├─ if (nowMs <= triggerTime) → пропустить (еще не время)
    │   ├─ if (nowMs < AppVars.NeverTimer) → отложить (сервер занят)
    │   ├─ if (AppVars.FastNeed) → выход (быстрое действие в процессе)
    │   └─ Выполнить действие:
    │       ├─ executePotionTimer()
    │       ├─ executeDestinationTimer()
    │       └─ executeComplectTimer()
    └─ Сохранить состояние
```

## 3. Зависимости

### 3.1 Прямые зависимости

| Компонент | Использование | Причина |
|-----------|---------------|---------|
| **FastActionManager** | `fastStart(potion, nick, count)` | Запуск цепочки питья зелья |
| **AutoFunctionsManager** | `startAutoMoving(destination)` | Запуск навигации на место |
| **MainActivity** | `reloadMainPhpInf()` | Перезагрузка после смены комплекта |
| **AppVars.NeverTimer** | Проверка `nowMs < NeverTimer` | Ожидание рыбалки/перемещения |
| **AppVars.FastNeed** | Проверка перед срабатыванием | Избежание конфликта fast-action |
| **AppVars.ServerDateTime** | Для парсинга сроков годности | Определение просроченных зелий |
| **SharedPreferences** | Сохранение/загрузка таймеров | Восстановление после перезапуска |

### 3.2 Косвенные зависимости

```
AppTimerManager
    ├─ SessionManager (через FastActionManager)
    │   └─ VCode парсинг для fastStart
    ├─ InvEntry (через FastActionManager.selectBestPotionByExpiration)
    │   └─ Проверка сроков годности зелий
    ├─ FileLogger (трассировка действий)
    │   └─ Диагностические логи
    └─ MainPhp (через AutoFunctionsManager)
        └─ Парсинг целей навигации
```

## 4. Интеграция с NeverTimer

### 4.1 Что такое NeverTimer?

`AppVars.NeverTimer` — это серверный cooldown время, в течение которого сервер "занят":
- **Рыбалка**: 15-300 сек в зависимости от удачи
- **Перемещение**: 1-3 сек (переход между ячейками)
- **Скин-операции**: 2-5 сек
- **Другие действия**: различные интервалы

### 4.2 Логика ожидания

```java
// В AppTimerManager.processDueTimers()

long nowMs = System.currentTimeMillis();

// Перед выполнением таймера проверяем NeverTimer
if (nowMs < AppVars.NeverTimer) {
    long deltaMs = AppVars.NeverTimer - nowMs;
    String msg = "[TIMER_DEFER_NEVERTIMER] Дожидаемся NeverTimer, deltaMs=" + deltaMs;
    FileLogger.trace("app_timer", msg);
    // НЕ удаляем таймер, просто пропускаем срабатывание
    continue;  // Попробуем на следующей итерации processDueTimers()
}
```

**Важный момент:** Таймер **не удаляется** из очереди, он просто откладывается. Это позволяет ему срабатить как только NeverTimer истечет.

### 4.3 Практический пример

Сценарий: Пользователь запустил таймер "Пить Зелье" через 10 минут, но через 5 минут начал рыбалку.

```
T=0:00   - Таймер установлен на T=10:00
T=5:00   - Начало рыбалки (AppVars.NeverTimer = T+120 сек = T=7:00)
T=7:00   - processDueTimers проверяет:
           ├─ nowMs (7:00) >= triggerTime (10:00)? НЕТ → пропускаем
           └─ Таймер остается в очереди
T=7:01   - Рыбалка продолжается (NeverTimer = T+180 сек = T=8:10)
T=10:00  - processDueTimers проверяет:
           ├─ nowMs (10:00) >= triggerTime (10:00)? ДА
           ├─ nowMs (10:00) < NeverTimer (8:10)? НЕТ (таймер истек)
           └─ ✅ executePotionTimer() сработает!
```

## 5. Приоритизация зелья

### 5.1 Приоритеты при выборе зелья

Когда таймер срабатывает и нужно выпить зелье, система автоматически выбирает **лучшее** доступное зелье по следующим приоритетам:

```
Приоритет 1: Тип зелья
├─ "Превосходное Зелье ..." имеет приоритет
└─ Обычное "Зелье ..." — fallback

Приоритет 2: Срок годности (внутри одного типа)
├─ Зелья с ближайшим сроком истечения выбираются первыми
└─ Зелья без срока (бесконечные) — в конце списка
```

### 5.2 Алгоритм selectBestPotionByExpiration

```java
// Вход: то самое зелье какое просил пользователь в таймере
// Выход: лучшее доступное зелье этого типа

List<PotionMatch> candidates = new ArrayList<>();

// ШАГ 1: Найти ВСЕ совпадения "Превосходного" зелья
for each "Превосходное Зелье ..." {
    candidates.add(PotionMatch(wuid, wmcode, expireMs, isExcellent=true));
}

// ШАГ 2: Если "Превосходного" не найдено, искать обычное
if (candidates.isEmpty()) {
    for each "Зелье ..." {
        candidates.add(PotionMatch(wuid, wmcode, expireMs, isExcellent=false));
    }
}

// ШАГ 3: Отсортировать по сроку годности (ascending)
// expireMs = 0 (нет срока) → Long.MAX_VALUE (в конец)
Collections.sort(candidates, (a, b) -> Long.compare(a.expireMs, b.expireMs));

// ШАГ 4: Выбрать первый (с минимальным expireMs)
return candidates.get(0);
```

### 5.3 Практический пример

Инвентарь игрока:
```
1. Превосходное Зелье Сильной Спины  (срок: 2026-04-05 12:00)
2. Зелье Сильной Спины                (срок: 2026-04-08 18:00)
3. Превосходное Зелье Сильной Спины  (срок: 2026-04-10 09:00)
4. Зелье Сильной Спины                (срок: без срока)
```

При срабатывании таймера "Пить Зелье Сильной Спины":

```
Шаг 1: Найти "Превосходные"
  ├─ Превосходное #1 (expireMs=1712323200)  ← ближайший срок
  └─ Превосходное #3 (expireMs=1712764800)

Шаг 2: "Превосходные" найдены → не ищем обычные

Шаг 3: Отсортировать
  ├─ Превосходное #1 (expireMs=1712323200)  ← выбран!!!
  └─ Превосходное #3 (expireMs=1712764800)

✅ РЕЗУЛЬТАТ: Превосходное Зелье #1 (быстро портится)
```

## 6. Логирование

### 6.1 Критичные события (FileLogger.trace)

Все эти события логируются с использованием `FileLogger.trace()` в `/files/Logs/`:

```
[TIMER_DEFER_NEVERTIMER]  - Таймер отложен ожиданием NeverTimer
[POTION_TIMER_FIRED]       - Таймер питья сработал
[POTION_FOUND_EXCELLENT]   - Найдено "Превосходное" зелье
[POTION_FOUND_REGULAR]     - Найдено обычное зелье
[POTION_SELECTED]          - Выбрано лучшее зелье
[POTION_NOT_FOUND]         - Зелье не найдено в инвентаре
[MAINPHP_FAST_POTION_START] - Начало обработки питья
[POTION_FORM_PREPARED]     - Форма питья готова к отправке
```

### 6.2 Вспомогательные события (Log.d/w)

В системный logcat также пишутся события для отладки:
```
Log.d("AppTimerManager", "[POTION_TIMER_FIRED] ...")
Log.d("FastActionManager", "[POTION_SELECTED] ...")
Log.w("AppTimerManager", "[TIMER_DEFER_NEVERTIMER] ...")
```

### 6.3 Пример логов при продуктивном срабатывании

```
T=10:00:15.243  [app_timer] [TIMER_DEFER_NEVERTIMER] Дожидаемся NeverTimer, deltaMs=45000, timerId=1
T=10:00:45.256  [app_timer] [POTION_TIMER_FIRED] id=1, potion='Зелье Сильной Спины', target=Боец, drinkCount=1, isRecur=false
T=10:00:45.312  [fast_action_potion] [MAINPHP_FAST_POTION_START] fastId='Зелье Сильной Спины'
T=10:00:45.418  [fast_action_potion] [POTION_FOUND_EXCELLENT] fastId='Зелье Сильной Спины', expire=2026-04-05, wuid=12345
T=10:00:45.512  [fast_action_potion] [POTION_SELECTED] fastId='Зелье Сильной Спины', type=EXCELLENT, expire=1712323200ms, candidates=3, wuid=12345
T=10:00:45.623  [fast_action_potion] [POTION_FORM_PREPARED] wuid='12345', wmcode='abc123'
```

## 7. API и примеры использования

### 7.1 Добавление нового таймера

```java
// В UI коде (например, Timers Fragment)

AppTimerManager mgr = AppTimerManager.getInstance(context);

// Пример 1: Таймер для питья зелья
AppTimer timer = new AppTimer();
timer.triggerTime = System.currentTimeMillis() + (5 * 60_000L); // через 5 минут
timer.potion = "Зелье Сильной Спины";
timer.drinkCount = 1;
timer.isRecur = true;
timer.everyMinutes = 5;  // повторять каждые 5 минут
timer.description = "Пить Зелье Сильной Спины каждые 5 минут";

mgr.addAppTimer(timer);
```

### 7.2 Обновление существующего таймера

```java
AppTimerManager mgr = AppTimerManager.getInstance(context);

AppTimer updated = new AppTimer();
updated.triggerTime = System.currentTimeMillis() + (10 * 60_000L); // перенести на 10 минут
updated.potion = "Превосходное Зелье Здоровья";
updated.drinkCount = 2;

boolean success = mgr.updateTimer(timerId, updated);
```

### 7.3 Удаление таймера

```java
mgr.removeTimer(timerId);
```

### 7.4 Вызов из MainActivity

```java
// В MainActivity.onPageLoaded() или в главном UI-таймере (каждую секунду)

AppTimerManager.getInstance(this).processDueTimers();
```

## 8. Инварианты и проверки (Rule 7 - FastNeed Management)

### 8.1 FastNeed блокирует таймеры

```java
if (AppVars.FastNeed) {
    // Таймер не сработает если идет быстрое действие
    return;
}
```

**Почему:** Если пользователь запустил быстрое действие (например, fast-attack), таймер должен подождать завершения.

### 8.2 После питья зелья таймер ожидает завершения

```java
// Таймер сработает → FastActionManager.fastStart() → AppVars.FastNeed = true
// ↓
// processDueTimers проверит AppVars.FastNeed → вернёт, не будет тестировать другие таймеры
// ↓
// [после завершения fast-action] AppVars.FastNeed = false
// ↓
// На следующей итерации processDueTimers может обработать следующий таймер
```

## 9. Обработка ошибок

### 9.1 Зелье не найдено в инвентаре

```
[POTION_NOT_FOUND] fastId='Зелье Несуществующее'
→ Таймер удаляется из очереди без выполнения
→ Если isRecur=true, следующий повтор НЕ создается
```

### 9.2 NeverTimer превышает время таймера

```
Сценарий: Таймер через 1 минуту, но рыбалка на 5 минут

processDueTimers проверяет:
├─ Таймер готов (nowMs >= triggerTime)?  ДА
├─ NeverTimer истек (nowMs >= NeverTimer)? НЕТ (рыбалка еще идет)
└─ Таймер ОТКЛАДЫВАЕТСЯ на следующую итерацию

→ Таймер сработает ПОСЛЕ завершения NeverTimer
```

### 9.3 Профиль сохранен - таймеры восстанавливаются

```java
// При загрузке профиля в AppVars
AppTimerManager.getInstance(context).setAppTimers(loadedTimersFromProfile);
```

## 10. Тестирование

### 10.1 Проверка приоритизации зелья

1. Создать инвентарь с смешанными зельями:
   - Превосходное Зелье (срок 2 дня)
   - Обычное Зелье (срок 5 дней)
   - Превосходное Зелье (срок 7 дней)

2. Установить таймер на питье "Зелье"

3. **Ожидаемый результат:** Выбрано "Превосходное Зелье" со сроком 2 дня

### 10.2 Проверка NeverTimer

1. Начать рыбалку (NeverTimer = текущее время + 60 сек)

2. Установить таймер на питье через 10 сек

3. **Ожидаемый результат:**
   - T+10 сек: Таймер "ждет" (рыбалка еще идет)
   - T+60 сек: Таймер срабатывает (рыбалка закончилась)

### 10.3 Проверка повторения (isRecur)

1. Установить таймер "Пить зелье" через 1 минуту с isRecur=true, everyMinutes=5

2. Подождать до срабатывания

3. **Ожидаемый результат:**
   - T=1:00 - первое питье
   - T=6:00 - второе питье (автоматически создано как новый таймер)
   - T=11:00 - третье питье
   - И т.д.

## 11. Связанные модули и файлы

| Файл | Роль |
|------|------|
| `AppTimerManager.java` | Основной менеджер таймеров |
| `AppTimer.java` | Модель таймера |
| `FastActionManager.java` | Выполнение питья зелья + selectBestPotionByExpiration |
| `InvEntry.java` | Парсинг сроков годности (expireMs) |
| `AutoFunctionsManager.java` | Выполнение перемещения |
| `MainActivity.java` | Вызов processDueTimers каждую секунду |
| `AppVars.java` | NeverTimer, FastNeed флаги |
| `SessionManager.java` | VCode для fastStart |
| `FileLogger.java` | Логирование действий |

## 12. Версионирование и история

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0.0 | 2026-03-15 | Базовая реализация таймеров (питье, перемещение, комплект) |
| 1.1.0 | 2026-03-28 | Добавлена приоритизация зелья по сроку годности |
| 1.1.4 | 2026-04-02 | Добавлена интеграция с NeverTimer, правильное логирование FileLogger |
