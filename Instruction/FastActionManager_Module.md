# Модуль FastActionManager (Быстрые действия)

**Версия:** 1.1.4  
**Статус:** Полностью реализован с приоритизацией зелья и SessionManager интеграцией  
**Последнее обновление:** 2026-04-02

---

## 1. Назначение и область применения

Модуль FastActionManager реализует систему "быстрых действий" — немедленное выполнение команд пользователя или автоматизированных сценариев без полной перезагрузки страницы main.php:

| Тип действия | Описание | Команда |
|--------------|---------|---------|
| **Питье зелья** | Использование зелья (обычного или превосходного) | `fastStart("Зелье Сильной Спины", nickTarget, 1)` |
| **Быстрая атака** | Одноразовая атака на противника | `fastAttack(nick)` |
| **Атака с оружием** | Атака с заданным оружием | `fastAttackWithWeapon(nick, weapon)` |
| **Телепорт** | Телепортация на наговор | `fastTeleport(destinationId)` |
| **Эликсир Блаженства** | Питье эликсира при усталости | `fastDrinkBlissElixir()` |
| **Операции со скином** | Раздевание/одевание скина | `fastSkinOff()`, `fastSkinON()` |

## 2. Архитектура и компоненты

### 2.1 Основная диаграмма потока

```
Запуск fast-действия
    ↓
fastStart(action, nick, ...) или fastAttack*(nick)
    ├─ Установить AppVars.FastId = название действия
    ├─ Установить AppVars.FastNeed = true    ← БЛОКИРОВКА других авто-функций
    └─ Перезагрузить main.php через WebView
    
    ↓
MainActivity.onPageLoaded() вызывает Filter.process()
    ├─ Проверить AppVars.FastNeed == true ?
    ├─ ДА → перенаправить в FastActionManager.processMainPhp()
    └─ НЕТ → обычная обработка MAIN.PHP
    
    ↓
FastActionManager.processMainPhp(html)
    ├─ Определить тип действия по AppVars.FastId
    ├─ Вызвать соответствующий метод:
    │   ├─ mainPhpFastPotion() ← ПРИОРИТИЗАЦИЯ ЗЕЛЬЯ
    │   ├─ mainPhpFastAttack()
    │   ├─ mainPhpFastTeleport()
    │   ├─ mainPhpFastElixir()
    │   └─ mainPhpFastSkin*()
    ├─ Спарсить HTML и найти нужное действие
    ├─ Сгенерировать HTML с авто-submit формой
    └─ Вернуть форму для JS-injection
    
    ↓
WebView получает форму, инжектирует JS → автоматический submit
    ├─ POST запрос на нужное действие
    ├─ SessionManager парсит VCode из ответа
    └─ Сохранить ответ в кэш
    
    ↓ (на следующей итерации)
FastActionManager.processMainPhp(ответFromServer)
    ├─ Проверить результат действия
    ├─ AppVars.FastNeed = false  ← РАЗБЛОКИРОВАНИЕ
    └─ Очистить AppVars.FastId
```

### 2.2 Ключевые переменные состояния

```java
public class AppVars {
    public static volatile String FastId = "";           // Какое действие запущено?
    public static volatile boolean FastNeed = false;     // Блокирует ли fast-action другие?
    public static volatile String FastNick = "";         // На кого/на что действия?
    public static volatile int FastDrinkCount = 1;       // Сколько раз пить?
    public static volatile long NeverTimer = 0L;         // Серверный cooldown
    public static volatile boolean ForcedActionGuard = false; // Защита от spam
}
```

### 2.3 Основные методы

```
FastActionManager
├─ PUBLIC API:
│   ├─ fastStart(potion, nickTarget, drinkCount)
│   ├─ fastCancel(reason)
│   ├─ fastAttack(nick)
│   ├─ fastAttackWithWeapon(nick, weapon)
│   ├─ fastTeleport(destinationId)
│   ├─ fastDrinkBlissElixir()
│   ├─ fastSkinOff(itemId)
│   ├─ fastSkinOn(complect)
│   └─ fastAttackBackWithExcellence(nick)
│
├─ INTERNAL PROCESSING:
│   ├─ processMainPhp(html)
│   ├─ processMainPhpFast(html)
│   └─ determineFastTypeFromServer(html)
│
└─ ITEM-SPECIFIC PARSERS:
    ├─ mainPhpFastPotion(html)         ← НОВОЕ: приоритизация
    ├─ mainPhpFastAttack(html)
    ├─ mainPhpFastTeleport(html)
    ├─ mainPhpFastElixir(html)
    ├─ mainPhpFastSkinOff(html)
    └─ mainPhpFastSkinOn(html)
```

## 3. Зависимости

### 3.1 Прямые зависимости

| Компонент | Использование | Причина |
|-----------|---------------|---------|
| **SessionManager** | `getValidVCodeForAction("fight_turn")` | Получение свежего VCode для POST |
| **InvEntry** | Парсинг сроков годности через `selectBestPotionByExpiration()` | Выбор лучших зелий |
| **MainPhp** | HTML парсинг, фильтры поиска | Получение данных из инвентаря |
| **HtmlUtils** | JS injection, форма-авто-submit | Инжектирование JS в WebView |
| **AppVars** | FastId, FastNeed, NeverTimer | Управление состоянием fast-action |
| **Filter** | `processMainPhp()` маршрутизация | Интеграция в основной pipeline |
| **FileLogger** | Трассировка всех действий | Диагностика проблем |
| **LocalBroadcastManager** | Broadcast результатов | Уведомление других компонентов |

### 3.2 Обратные зависимости

```
Кто вызывает FastActionManager:
├─ AppTimerManager.executePotionTimer()
│   └─ fastStart(potion, nick, count)
├─ QuickButtonsPanel при нажатии кнопки
│   └─ fastAttack(nick) или fastTeleport(id)
├─ AutoFunctionsManager для auto-рыбалки
│   └─ fastStart("Зелье Сильной Спины", nick, 1)
└─ BossAuto при атаке босса
    └─ fastAttackWithWeapon(nick, weapon)
```

### 3.3 Косвенные зависимости

```
FastActionManager
├─ MainActivity
│   ├─ WebView.loadUrl(mainPhp)
│   └─ WebViewRequestInterceptor
├─ LezFight (через SessionManager)
│   └─ VCode для боевых действий
├─ PostFilter
│   └─ HTML перехват и обработка
└─ Utils
    ├─ HelperStrings (парсинг HTML)
    ├─ HtmlUtils (JS injection)
    └─ Russian (преобразование номиналов)
```

## 4. Основной паттерн: fastStart()

### 4.1 Сигнатура и назначение

```java
public static void fastStart(String potion, String nickTarget, int drinkCount) {
    /**
     * Запускает цепочку быстрого питья зелья.
     * 
     * @param potion        - название зелья (например, "Зелье Сильной Спины")
     * @param nickTarget    - ник цели для питья (может быть пустой, если на себя)
     * @param drinkCount    - сколько раз пить (обычно 1, максимум определяется cooldown)
     */
}
```

### 4.2 Процесс выполнения

```
ШАГ 1: Инициализация
├─ AppVars.FastId = potion          // "Зелье Сильной Спины"
├─ AppVars.FastNick = nickTarget    // "БойцИЖивой"
├─ AppVars.FastDrinkCount = count   // 1
├─ AppVars.FastNeed = true          // Блокируем другие авто-функции!
└─ Логирование: FileLogger.trace("fast_action", "[FAST_START] ...")

ШАГ 2: Перезагрузка
├─ LocalBroadcastManager.sendBroadcast(ACTION_WEBVIEW_LOAD_URL)
├─ WebView загружает main.php
└─ Логирование: FileLogger.trace("fast_action", "[PAGE_LOAD_REQUESTED]")

ШАГ 3: Обработка ответа
├─ MainActivity.onPageLoaded() вызывает processMainPhp(html)
├─ Проверка: if (AppVars.FastNeed) → маршрут FastActionManager
├─ selectBestPotionByExpiration(html, potion)  ← НОВОЕ!
└─ Логирование всех найденных зелий

ШАГ 4: Генерация формы
├─ Парсинг: wuid, wmcode, vcode
├─ Создание HTML формы с hidden inputs
├─ SessionManager получает свежий VCode  ← CRITICAL!
└─ Логирование: FileLogger.trace("fast_action_potion", "[POTION_FORM_PREPARED]")

ШАГ 5: Отправка
├─ WebView инжектирует JS → document.forms[0].submit()
├─ HTTP POST отправляется на сервер
├─ SessionManager парсит VCode из ответа
└─ Логирование: FileLogger.trace("fast_action_potion", "[FORM_SUBMITTED]")

ШАГ 6: Завершение
├─ Проверка результата
├─ AppVars.FastNeed = false          // Разблокируем авто-функции
├─ AppVars.FastId = ""               // Очистка
└─ Логирование: FileLogger.trace("fast_action", "[FAST_COMPLETED]")
```

### 4.3 Пример вызова

```java
// Из UI при нажатии кнопки "Пить"
FastActionManager.fastStart(
    "Превосходное Зелье Сильной Спины",  // potion
    "",                                     // nickTarget (пусто = пить на себя)
    1                                       // drinkCount
);

// Из AppTimerManager при срабатывании таймера
FastActionManager.fastStart(
    "Зелье Здоровья",
    AppVars.SelfNick,
    2
);

// Из auto-рыбалки при низком здоровье
FastActionManager.fastStart(
    "Зелье Сильной Спины",
    AppVars.SelfNick,
    3
);
```

## 5. Приоритизация зелья — selectBestPotionByExpiration()

### 5.1 Проблема, которую решает

**Сценарий:** В инвентаре несколько одинаковых зелий разного качества и сроков годности.

| Зелье | Тип | Срок годности |
|-------|-----|---------------|
| #1 | Превосходное | 2026-04-05 (скоро) |
| #2 | Обычное | 2026-04-10 (далеко) |
| #3 | Превосходное | 2026-04-15 (далеко) |
| #4 | Обычное | 2026-04-20 (далеко) |

**Старая логика:** Выбрать первое найденное → #2 (обычное) ❌  
**Новая логика:** Выбрать лучшее → #1 (превосходное с ближайшим сроком) ✅

### 5.2 Алгоритм трёхуровневой приоритизации

```java
private static String[] selectBestPotionByExpiration(String html, String fastId) {
    
    // УРОВЕНЬ 1: Тип зелья
    // ════════════════════════════════════════════════════════════
    
    List<PotionMatch> excellent = findAllExcellentPotions(html, fastId);
    
    if (!excellent.isEmpty()) {
        // У нас есть "Превосходное" → не ищем обычное
        candidates = excellent;
    } else {
        // Нет "Превосходного" → ищем обычное
        candidates = findAllRegularPotions(html, fastId);
    }
    
    // УРОВЕНЬ 2: Сроки годности внутри одного типа
    // ════════════════════════════════════════════════════════════
    
    // Спарсить expireMs для каждого найденного зелья
    for each candidate {
        expireMs = parseExpirationFromHtml(htmlNearCandidate);
        if (expireMs == null) {
            expireMs = Long.MAX_VALUE;  // Нет срока = в конец списка
        }
    }
    
    // УРОВЕНЬ 3: Сортировка по близости срока
    // ════════════════════════════════════════════════════════════
    
    Collections.sort(candidates, 
        (a, b) -> Long.compare(a.expireMs, b.expireMs)
    );
    
    // expireMs с меньшим значением = ближайший срок = первый в списке
    // expireMs = Long.MAX_VALUE (нет срока) = в конце списка
    
    PotionMatch best = candidates.get(0);  // ← выбран!
    
    FileLogger.trace("fast_action_potion", 
        "[POTION_SELECTED] type=" + (best.isExcellent ? "EXCELLENT" : "REGULAR") 
        + ", expire=" + best.expireMs + "ms"
    );
    
    return new String[] { best.wuid, best.wmcode };
}
```

### 5.3 Структура PotionMatch

```java
class PotionMatch {
    String wuid;              // ID предмета в инвентаре
    String wmcode;            // Код магии (vcode)
    long expireMs;            // Дата истечения в ms (Long.MAX_VALUE если нет)
    boolean isExcellent;      // true = "Превосходное", false = обычное
    
    // Логирование
    String expireTime;        // Человекочитаемый формат: "2026-04-05 12:30"
    String type;              // "EXCELLENT" или "REGULAR"
}
```

### 5.4 Практический пример с логами

```
HTML инвентаря содержит:
─────────────────────────────────────────────────────────
magicreform('12345','target','Превосходное Зелье','abc123')
<font color=#cc0000>Срок годности: 05.04.2026 12:30</font>

magicreform('12346','target','Зелье','def456')
<font color=#cc0000>Срок годности: 10.04.2026 18:45</font>

magicreform('12347','target','Превосходное Зелье','ghi789')
[Нет срока]

processMainPhpFastPotion() вызывает selectBestPotionByExpiration():
─────────────────────────────────────────────────────────

Шаг 1: Поиск "Превосходного"
  └─ Найдено 2 экземпляра:
     ├─ #12345 (expireMs=1712323800, срок 05.04 12:30)
     └─ #12347 (expireMs=Long.MAX_VALUE, нет срока)

Шаг 2: Сортировка по expireMs (ascending)
  ├─ #12345 (1712323800)      ← ближайший срок
  └─ #12347 (9223372036854775807 = MAX)

Шаг 3: Выбор первого
  └─ ✅ #12345 "Превосходное" (скоро портится)

Логирование:
  [POTION_FOUND_EXCELLENT] fastId='Зелье', expire=05.04 12:30, wuid=12345
  [POTION_SELECTED] fastId='Зелье', type=EXCELLENT, expire=1712323800ms, candidates=2, wuid=12345
```

## 6. Логирование по AGENTS.MD Rule 6

### 6.1 Критичные события (FileLogger.trace)

Все события в критичной цепочке питья зелья логируются:

```
[FAST_START]                    - Инициирован fast-action
[PAGE_LOAD_REQUESTED]           - Запрос на перезагрузку main.php
[MAINPHP_FAST_POTION_START]     - Начало обработки питья
[POTION_FOUND_EXCELLENT]        - Найдено "Превосходное" зелье
[POTION_FOUND_REGULAR]          - Найдено обычное зелье
[POTION_SELECTED]               - Выбрано лучшее зелье
[POTION_NOT_FOUND]              - Зелье не найдено
[POTION_FORM_PREPARED]          - Форма питья подготовлена
[FAST_COMPLETED]                - Fast-action завершен
[FAST_CANCELLED]                - Fast-action отменен
```

### 6.2 Каналы логирования

```
FileLogger.trace("fast_action", msg)          // Основной fast-action
FileLogger.trace("fast_action_potion", msg)   // Питье зелья
FileLogger.trace("fast_action_attack", msg)   // Атака
FileLogger.trace("fast_action_teleport", msg) // Телепорт
FileLogger.trace("fast_action_skin", msg)     // Операции со скином
```

Также логируются в системный logcat:
```
Log.d("FastActionManager", msg)   // Debug информация
Log.w("FastActionManager", msg)   // Предупреждения
Log.e("FastActionManager", msg)   // Ошибки
```

### 6.3 Пример логов при продуктивном питье

```
T=10:15:23.421  [fast_action] [FAST_START] potion='Превосходное Зелье Сильной Спины', target='', count=1
T=10:15:23.512  [fast_action] [PAGE_LOAD_REQUESTED] url=main.php
T=10:15:24.103  [fast_action_potion] [MAINPHP_FAST_POTION_START] fastId='Превосходное Зелье Сильной Спины'
T=10:15:24.211  [fast_action_potion] [POTION_FOUND_EXCELLENT] fastId='...', expire=2026-04-05, wuid=45892
T=10:15:24.312  [fast_action_potion] [POTION_FOUND_EXCELLENT] fastId='...', expire=2026-04-12, wuid=45893
T=10:15:24.401  [fast_action_potion] [POTION_SELECTED] fastId='...', type=EXCELLENT, expire=1712323800ms, candidates=2, wuid=45892
T=10:15:24.521  [fast_action_potion] [POTION_FORM_PREPARED] wuid='45892', wmcode='c7d8e9f0'
T=10:15:24.734  [fast_action] [FORM_SUBMITTED] POST /main.php
T=10:15:25.342  [fast_action] [FAST_COMPLETED] Питье завершено, AppVars.FastNeed=false
```

## 7. SessionManager интеграция (VCode)

### 7.1 Критичные моменты использования VCode

В процессе `mainPhpFastPotion()` VCode используется дважды:

```
Момент 1: Парсинг первого HTML (main.php с инвентарем)
├─ SessionManager.getInstance().cacheFightVCode(...)  ← SAVE
└─ Сохраняем в кэш на случай потери

Момент 2: Отправка формы питья
├─ SessionManager.getInstance().getValidVCodeForAction("potion_drink")
├─ Проверка: !isExpired(maxAgeMs=30000)
└─ Если истек → перезагрузить main.php
```

### 7.2 Обработка null VCode

```java
String[] result = selectBestPotionByExpiration(html, fastId);

if (result == null || result.length < 2) {
    String msg = "[POTION_NOT_FOUND] fastId='" + fastId + "'";
    Log.w(TAG, msg);
    FileLogger.trace("fast_action_potion", msg);
    
    // Fallback: перезагрузить main.php, чтобы заново парсить
    AppVars.FastNeed = false;
    reloadMainPhp();
    return null;
}
```

## 8. Управление FastNeed (Rule 7 - FastNeed Management)

### 8.1 Жизненный цикл FastNeed

```
НАЧАЛО:
├─ fastStart() → AppVars.FastNeed = true

ПРОЦЕСС:
├─ processDueTimers() в AppTimerManager:
│   ├─ if (AppVars.FastNeed) return;  ← Ждём завершения
│   └─ Не запускать другие таймеры
├─ AutoFunctionsManager конкурирует:
│   ├─ if (AppVars.FastNeed) return;  ← Ждём завершения
│   └─ Не запускать авто-функции
└─ MainActivity проверяет:
    ├─ if (AppVars.FastNeed) → FastActionManager
    └─ Маршрутизирует в правильный обработчик

ЗАВЕРШЕНИЕ:
├─ processMainPhp() завершает действие
├─ AppVars.FastNeed = false  ← РАЗБЛОКИРОВАНИЕ
└─ На следующей итерации другие авто-функции могут запуститься
```

### 8.2 Инварианты

```java
// ИНВАРИАНТ 1: FastNeed блокирует другие fast-actions
if (AppVars.FastNeed) {
    // Нельзя запустить новый fastStart() пока не завершится старый
    return;
}

// ИНВАРИАНТ 2: После завершения ВСЕГДА затереть FastNeed
try {
    executeFastAction();
} finally {
    AppVars.FastNeed = false;
    FileLogger.trace("fast_action", "[FAST_NEED_CLEARED]");
}

// ИНВАРИАНТ 3: Ожидание NeverTimer НЕ разблокирует FastNeed
// FastNeed остается true пока действие не завершится!
if (nowMs < AppVars.NeverTimer) {
    // Продолжаем ждать сервера, но FastNeed остается true
}
```

## 9. API и примеры использования

### 9.1 Питье зелья с приоритизацией

```java
// Пример 1: Питье лучшего доступного зелья
FastActionManager.fastStart(
    "Зелье Сильной Спины",
    "",                    // на себя
    1
);
// Система автоматически выберет:
// 1. "Превосходное" если есть
// 2. С ближайшим сроком годности
```

### 9.2 Быстрая атака

```java
// Пример 2: Одноразовая атака из UI
FastActionManager.fastAttack("БойцИЖивой");

// Или с оружием:
FastActionManager.fastAttackWithWeapon("БойцИЖивой", "Молот");
```

### 9.3 Телепорт

```java
// Пример 3: Быстрый телепорт
FastActionManager.fastTeleport(3);  // ID места телепорта

// Доступные ID:
// 1 = Город Форпост
// 2 = Город Октал
// 3 = Деревня Подгорная
// ...
```

### 9.4 Питье эликсира блаженства

```java
// Пример 4: Автопитье ближа при усталости
if (AppVars.Tired >= AppVars.Profile.AutoDrinkBlazTired) {
    FastActionManager.fastDrinkBlissElixir();
}
```

### 9.5 Отмена fast-action

```java
// Пример 5: Отмена с логированием причины
FastActionManager.fastCancel("user_cancelled");
// или
FastActionManager.fastCancel("manual_action_priority");
```

## 10. Обработка ошибок

### 10.1 HTML слишком малый (race condition)

```
Ошибка: [POTION_NOT_FOUND] в результате incomplete HTML load

Попытка 1:
├─ Загружаем main.php → WebView еще loading
├─ HTML размер = 370 bytes (loading indicator)
└─ parseExpiration() не находит зелья

Попытка 2 (407ms спустя):
├─ WebView завершил загрузку
├─ HTML размер = 14500 bytes (полная страница)
└─ ✅ Зелье найдено!

Решение: FastActionManager.processMainPhp() повторно вызывает себя
```

### 10.2 VCode истек просроченная

```
Ошибка: SessionManager.getValidVCodeForAction() вернула null

Причина: Между запросом HTML и отправкой формы прошло >30 сек

Решение:
├─ Определить что VCode истек
├─ Перезагрузить main.php
├─ [На следующей итерации] Запарсить свежий VCode
└─ Попробовать снова
```

### 10.3 Зелье исчезло из инвентаря

```
Ошибка: HTML распарсен, зелье найдено, но wuid больше не в инвентаре

Сценарий:
├─ Было: Превосходное Зелье (wuid=12345)
├─ Между HTML и submit: Пользователь выпил зелье вручную
└─ Результат: "Предмет не найден" от сервера

Решение:
├─ FastActionManager получит ошибку от сервера
├─ Логирует в FileLogger.trace()
└─ На следующей итерации попробует снова
```

## 11. Связанные модули

| Модуль | Файл | Роль |
|--------|------|------|
| **SessionManager** | SessionManager.java | Управление VCode и кэшированием |
| **InvEntry** | InvEntry.java | Парсинг сроков годности (expireMs) |
| **MainPhp** | MainPhp.java | HTML парсинг инвентаря |
| **HtmlUtils** | HtmlUtils.java | JS injection и авто-submit |
| **AppVars** | AppVars.java | Глобальное состояние (FastId, FastNeed) |
| **AppTimerManager** | AppTimerManager.java | Таймеры вызывают fastStart() |
| **AutoFunctionsManager** | AutoFunctionsManager.java | Конкурирует за ресурсы |
| **BossAuto** | BossAuto.java | Вызывает fastAttackWithWeapon() |
| **FileLogger** | FileLogger.java | Диагностическое логирование |
| **Filter** | Filter.java | Маршрутизирует в processMainPhp() |
| **MainActivity** | MainActivity.java | WebView интеграция |

## 12. Руководство по отладке

### 12.1 Проверка приоритизации зелья

**Тест:** Система выбирает "Превосходное" перед обычным

```
1. Создать инвентарь:
   ├─ Превосходное Зелье Спины (срок 2 дня)
   ├─ Зелье Спины (срок 5 дней)
   └─ Превосходное Зелье Здоровья

2. Нажать "Пить Зелье Спины"

3. Ожидаемый результат:
   [POTION_SELECTED] fastId='Зелье Спины', type=EXCELLENT
   └─ Выбрано Превосходное!

4. Проверить логи:
   tail -f /files/Logs/filelogger.log | grep POTION_SELECTED
```

### 12.2 Проверка сроков годности

**Тест:** Система выбирает зелье с ближайшим сроком дорогости

```
1. Инвентарь:
   ├─ Превосходное #1 (EXPIRE: 2026-04-05 12:00)
   ├─ Превосходное #2 (EXPIRE: 2026-04-10 09:00)
   └─ Превосходное #3 (НЕТ СРОКА)

2. Нажать "Пить Превосходное Зелье"

3. Ожидаемый результат:
   [POTION_SELECTED] ..., expire=1712323800ms, candidates=3
   └─ Выбрано #1 с ближайшим сроком

4. Проверить:
   grep "POTION_FOUND\|POTION_SELECTED" /files/Logs/filelogger.log
```

### 12.3 Проверка FastNeed блокирования

**Тест:** AppTimerManager ждет завершения fast-action

```
1. Установить таймер #1 "Пить зелье" на T+10 сек
2. Установить таймер #2 "Пить зелье" на T+15 сек

3. T+10: Таймер #1 срабатывает → AppVars.FastNeed = true

4. T+11: Проверить что Таймер #2 НЕ сработал
   └─ processDueTimers() проверит FastNeed → return

5. T+12-15: Таймер #1 завершается → AppVars.FastNeed = false

6. T+16: Таймер #2 теперь может сработать

7. Проверить логи:
   grep "POTION_TIMER_FIRED" /files/Logs/filelogger.log
```

### 12.4 Проверка VCode свежести

**Тест:** SessionManager обновляет VCode между запросами

```
1. Профилировать fastStart("Зелье")

2. Проверить SessionManager логи:
   grep "VCODE_PARSED\|VCODE_CACHED" /files/Logs/filelogger.log
   
3. Ожидаемый результат:
   T=10:15:24.211  SessionManager: ✅ VCODE_PARSED
   T=10:15:24.401  SessionManager: ✅ VCODE_CACHED
```

## 13. История версий

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0.0 | 2026-01-10 | Базовая реализация fast-actions |
| 1.0.5 | 2026-02-15 | Поддержка питья зелья |
| 1.1.0 | 2026-03-15 | SessionManager интеграция для VCode |
| 1.1.2 | 2026-03-28 | Приоритизация "Превосходного" зелья |
| 1.1.4 | 2026-04-02 | Приоритизация по сроку годности, FileLogger.trace() |

## 14. Дополнительные ресурсы

- [SessionManager_VCode.md](SessionManager_VCode.md) — управление VCode
- [AppTimer_Module.md](AppTimer_Module.md) — таймеры и их интеграция
- [MainPhp_VCode_Usage.md](MainPhp_VCode_Usage.md) — использование VCode в основных действиях
- [FightAuto_VCode_Usage.md](FightAuto_VCode_Usage.md) — VCode в боевой логике
- [AGENTS.MD](../AGENTS.MD) — общие правила проекта
- [CLAUDE.MD](../CLAUDE.MD) — новые правила VCode
