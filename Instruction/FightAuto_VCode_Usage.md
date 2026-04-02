# FightAuto.java: Использование VCode в автобое

## Обзор

`FightAuto.java` — это главный контроллер автоматического боя. Здесь происходит:
- Парсинг HTML боевой страницы (`pinfo-battle.html`)
- Анализ боевого состояния (враг, его HP, наши stats)
- Отправка ходов через VCode
- Управление сложными боевыми стратегиями

**Критичная особенность:** Боевая страница требует валидного VCode для **каждого хода**. Если VCode невалидна или старая, ход не пройдёт (403 ошибка).

---

## Жизненный цикл VCode в боевом цикле

### 🔢 Шаг 1: Объявление боя (T+0ms)

**События:**
- Сервер отправил HTML боевой страницы
- WebView загружает боевую страницу
- `WebViewRequestInterceptor.onPageFinished()` вызывается

**VCode действие:**
```java
// В WebViewRequestInterceptor:
if (url.contains("pinfo-battle.html")) {
    // ✅ SESSION MANAGER ПАРСИТ VCODE
    SessionManager.getInstance().parseVCode(html);
    FileLogger.trace("FightAuto", "✅ VCODE_PARSED source=fight");
}
```

### 🔢 Шаг 2: Инициализация боевого состояния (T+5-10ms)

**События:**
- `LezFight` конструктор парсит боевые данные из HTML
- Создаётся объект врага, его статы, наше состояние

**КРИТИЧНАЯ ОЧЕРЕДЬ ВЫЗОВОВ:**

```java
// ❌ НЕПРАВИЛЬНЫЙ ПОРЯДОК (БЫЛ БАГ):
LezFight fight = new LezFight(html);          // T+5ms
// ... 150 строк логики ...
SessionManager.getInstance()
    .markFightInProgress();                   // T+700ms ❌ ОЧЕНЬ ПОЗДНО!

// ✅ ПРАВИЛЬНЫЙ ПОРЯДОК (ДОЛЖНО БЫТЬ):
SessionManager.getInstance()
    .markFightInProgress();                   // T+5ms ✅ ПЕРВОЙ!
LezFight fight = new LezFight(html);          // T+10ms
```

**Почему это критично:**

Когда `markFightInProgress()` вызывается поздно:
1. VCode уже парсилась к T+0ms (`parseVCode()`)
2. К T+5-700ms её возраст растёт с 5ms до 700ms
3. Если к T+700ms кто-то вызовет `getValidVCodeForAction("fight")`, он может получить старый эквивалент вместо свежего
4. Первый ход может отправиться с недостаточно свежим VCode

**Решение:** `markFightInProgress()` **ВСЕГДА** перед `LezFight` конструктором.

### 🔢 Шаг 3: Построение фрейма боя (T+10-50ms)

**События:**
- `LezFight.buildFrame()` анализирует HTML и готовит ход
- Определяются опции для автоударов (атака, магия, предметы)

**VCode действие:**
```java
private LezFight fight;

public void processFight(String html) {
    // ✅ ФИКСИРОВАННАЯ ОЧЕРЕДЬ:
    
    // 1️⃣ ПЕРВЫЙ: Отметить бой начался
    SessionManager.getInstance().markFightInProgress();
    FileLogger.trace("FightAuto", "markFightInProgress at T=" + tm());
    
    // 2️⃣ ВТОРОЙ: Парсить HTML боя
    fight = new LezFight(html);
    FileLogger.trace("FightAuto", "LezFight parsed at T=" + tm());
    
    // 3️⃣ ТРЕТИЙ: Построить фрейм
    fight.buildFrame();
    FileLogger.trace("FightAuto", "buildFrame at T=" + tm());
    
    // 4️⃣ ЧЕТВЁРТЫЙ: Отправить ход
    sendFightTurn(fight);
}
```

### 🔢 Шаг 4: Отправка хода (T+50-100ms)

**События:**
- Получен ход для отправки
- `sendFightTurn()` вызывает FightViewModel

**VCode действие:**
```java
private void sendFightTurn(LezFight fight) {
    // ✅ ШАГ 1: Получить валидный VCode
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("fight_turn");
    
    if (vcode == null) {
        // ❌ ОШИБКА: VCode потеряна!
        FileLogger.error("FightAuto", 
            "CRITICAL: VCode null for fight turn! "
            + "Fight abandoned!");
        
        // Fallback: перезагрузить боевую страницу
        loadFightPage();
        return;
    }
    
    // ✅ ШАГ 2: Отправить ход через ViewModel
    FightViewModel vm = fightViewModel;
    if (vm != null) {
        vm.submitTurn(fight.getTurnAction(), vcode);
        FileLogger.trace("FightAuto", 
            "Turn submitted with vcode age=" + vcode.ageMs() + "ms");
    }
}
```

### 🔢 Шаг 5: Получение результата хода (T+100-300ms)

**События:**
- Сервер обработал ход
- Вернул новый HTML со статом боевой страницы
- WebView загружает обновленный HTML

**VCode действие:**
```java
// В WebViewRequestInterceptor (новый цикл):
if (url.contains("pinfo-battle.html")) {
    // ✅ АВТОМАТИЧЕСКИЙ ПАРС: SessionManager обновляет VCode
    SessionManager.getInstance().parseVCode(newHtml);
    FileLogger.trace("FightResult", "✅ VCODE_PARSED (new cycle)");
}
```

**Повторение:** Цикл возвращается к Шагу 1, но теперь с новым боевым состоянием.

---

## FIGHT_FALLBACK_MODE: Расширенный таймаут (120 сек)

### Когда включается:

```java
// В SessionManager:
public String getValidVCodeForAction(String actionName) {
    if ("fight_turn".equals(actionName) && 
        AppVars.fightLikelyActive) {
        
        // 🔴 РЕЖИМ 1: Обычный timeout (10-30 сек)
        // VCode должна быть помоложе 10-30 секунд
        
        // 🟠 РЕЖИМ 2: FIGHT_FALLBACK_MODE (120 сек)
        // Если идёт БОЙ, допускаем VCode до 120 сек!
        // (потому что смену боевой страницы может задержать UI)
        
        if (vcode.ageMs() > 120000) {
            return null;  // Даже fallback mode имеет лимит
        }
    }
    return vcode;
}
```

### Почему это нужно:

**Проблема:** Во время боя WebView может быть затёрт пользовательским взаимодействием. Из-за этого новый HTML не загружается, новый VCode не парсируется, и старый VCode становится "единственным оружием".

**Решение:** FIGHT_FALLBACK_MODE расширяет таймаут с 30 сек до 120 сек, чтобы дать достаточно времени WebView вернуться к жизни.

**Выход из режима:** При T > 120 сек система всё равно требует перезагрузку боевой страницы.

---

## Критичные проблемы с VCode в боях

### ⚠️ Проблема #1: 9ms VCode Cache Gap

**Что происходит:**

```
T+0ms:   parseVCode() вызывается → VCode stored in SessionContext
T+5ms:   markFightInProgress() вызывается
T+5ms:   LezFight constructor запускается
         Внутри LezFight может быть локальный запрос данных
         который нуждается в текущем VCode...
T+700ms: Но локальный запрос внутри LezFight 
         использует AppVars.VCode (старый способ!)
         который НЕ был обновлён вовремя
```

**Признак ошибки:**
```
grep "VCode fallback mode" fightauto.log
grep "FALLBACK_MODE used" sessionmanager.log
```

**Решение:** Убедиться, что `markFightInProgress()` вызывается **ДО** создания LezFight объекта. Всё остальное закон механики.

### ⚠️ Проблема #2: Потеря VCode между боями

**Что происходит:**

```
T+0s:   Бой #1 заканчивается
        → SessionManager.clearFightContext() вызывается
        → VCode очищается из файт-контекста
        
T+500ms: Пользователь снова объявляет бой (новый враг)
        → parseVCode() должна вызваться
        → НО... если страница не перезагрузилась, 
          то новый HTML может не содержать VCode!
```

**Признак ошибки:**
```
grep "clearFightContext" fightauto.log
grep -A3 "clearFightContext" fightauto.log | grep "parseVCode"  # should exist
```

**Решение:** После `clearFightContext()` убедиться, что следующий цикл включает загрузку боевой страницы.

### ⚠️ Проблема #3: Race condition между авто-бойм и быстрыми действиями

**Что происходит:**

```
Сценарий: Персонаж пьёт эликсир во время боя

T+0ms:   FastActionManager запускает быстрое действие
        → AppVars.FastNeed = true
        
T+10ms:  ForcedActionGuard проверяет:
        → if (FastNeed && fightLikelyActive) → БЛОКИРОВАТЬ ход!
        
T+500ms: Эликсир готов (cooldown готов)
        → fastCancel() вызывается
        → AppVars.FastNeed = false
        
T+510ms: autoTurn может возобновиться
```

**Правильный флоу:**

```java
// В FastActionManager:
public static void fastAttackBlazElixir(String vcode) {
    AppVars.FastNeed = true;
    FileLogger.trace("FastAction", "FastNeed set");
    
    // Отправить в очередь быстрое действие
    // ...
    
    // В callback при получении результата:
    if (httpCode == 200) {
        // Успешно
        FileLogger.trace("FastAction", "Elixir used successfully");
        
        // NOT ВСЁ! Ещё cooldown...
    }
}

// В MainPhp.java (обработка cooldown):
private void handleElixirCooldown() {
    // Ждём, пока elixir cooldown истечёт
    new Handler().postDelayed(() -> {
        FastActionManager.fastCancel("elixir_cooldown_finished");
        FileLogger.trace("MainPhp", "FastNeed cleared after cooldown");
        requestAutoTurn();  // Возобновить боевые ходы
    }, ELIXIR_COOLDOWN_MS);
}
```

---

## Интеграция с SessionManager

### Правильное использование:

```java
// ✅ ВСЕГДА ДЕЛАЙТЕ:

public void processFightPage(String html) {
    // 1. Отметить начало боя
    SessionManager.getInstance().markFightInProgress();
    
    // 2. Создать LezFight объект
    LezFight fight = new LezFight(html);
    
    // 3. Получить VCode для хода
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("fight_turn");
    
    // 4. Проверить наличие
    if (vcode == null) {
        FileLogger.error("FightAuto", "VCode lost during fight!");
        reloadFightPage();
        return;
    }
    
    // 5. Отправить ход
    submitTurn(fight, vcode);
}

// ❌ НИКОГДА НЕ ДЕЛАЙТЕ:

public void wrongApproach(String html) {
    // ❌ Не используйте AppVars.VCode напрямую:
    String vcode = AppVars.VCode;  // ❌ ЗАПРЕЩЕНО!
    
    // ❌ Не создавайте LezFight перед markFightInProgress:
    LezFight fight = new LezFight(html);  // ❌ НЕПРАВИЛЬНЫЙ ПОРЯДОК!
    SessionManager.getInstance().markFightInProgress();
    
    // ❌ Не игнорируйте null VCode:
    submitTurn(fight, vcode);  // vcode может быть null!
}
```

---

## Боевые сражения: примеры кода

### Пример 1: Простой автобой (один ход)

```java
public void autoAttackSingleTurn(String html) {
    // ✅ ПОРЯДОК ВЫЗОВОВ КРИТИЧЕН!
    SessionManager.getInstance().markFightInProgress();
    
    LezFight fight = new LezFight(html);
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("fight_turn");
    
    if (vcode == null) {
        FileLogger.error(TAG, "VCode unavailable");
        return;
    }
    
    // Отправить простую атаку
    String turnAction = fight.selectAutoAttack();
    submitTurnToServer(turnAction, vcode);
    
    FileLogger.trace(TAG, 
        "Single turn sent: action=" + turnAction 
        + ", vcode_age=" + vcode.ageMs() + "ms");
}
```

### Пример 2: Многоходовой ауто-бой с магией

```java
public void autoAttackWithMagic(String html) {
    SessionManager.getInstance().markFightInProgress();
    LezFight fight = new LezFight(html);
    
    // ✅ Получить свежий VCode
    String vcode = SessionManager.getInstance()
        .getValidVCodeForAction("fight_turn");
    if (vcode == null) return;
    
    // Выбрать стратегию
    String turnAction;
    if (fight.shouldCastSpell()) {
        turnAction = fight.selectMagicSpell();
    } else {
        turnAction = fight.selectAutoAttack();
    }
    
    submitTurnToServer(turnAction, vcode);
    
    FileLogger.trace(TAG, 
        "Magical turn sent: spell=" + turnAction);
}
```

### Пример 3: Обработка 403 VCode ошибки

```java
public void submitTurnWithErrorHandling(
        String turnAction, 
        String vcode) {
    
    // Отправить запрос
    int httpCode = submitTurnToServer(turnAction, vcode);
    
    if (httpCode == 403) {
        // ❌ ОШИБКА: Invalid VCode from server
        FileLogger.error(TAG, 
            "403 VCode error. Age=" + 
            (vcode != null ? vcode.ageMs() : "null"));
        
        if (vcode != null && vcode.ageMs() > 60000) {
            // Стандартный случай: VCode старая
            reloadFightPage();
        } else if (vcode == null) {
            // Критичный случай: VCode потеряна
            SessionManager.getInstance().clearFightContext();
            reloadFightPage();
        } else {
            // Странный случай: VCode свежая, но сервер отвергает
            FileLogger.error(TAG, 
                "ERROR: Fresh VCode rejected (possible server issue)");
        }
    }
}
```

---

## Профилактика VCode ошибок в боях

### Чек-лист при добавлении новой боевой логики:

- [ ] `markFightInProgress()` вызвана ПЕРЕД `new LezFight()`
- [ ] VCode получается через `SessionManager.getValidVCodeForAction("fight_turn")`
- [ ] Проверен null VCode с fallback логикой
- [ ] Использован финальный в боевом VCode, а не старый AppVars.VCode
- [ ] Добавлено логирование `FileLogger.trace()` для отладки
- [ ] Обработана 403 VCode ошибка (см. пример 3)
- [ ] Протестировано на множественных боях подряд
- [ ] Логи проверены на "VCode related" ошибки

### Диагностические команды:

```bash
# Проверить порядок вызовов:
grep -E "markFightInProgress|LezFight|buildFrame" fightauto.log | head -10

# Проверить VCode возраст:
grep "vcode_age=" fightauto.log | head -5

# Проверить ошибки:
grep "403\|VCode.*null\|CRITICAL" fightauto.log | head -10

# Проверить обновления VCode:
grep "VCODE_PARSED.*fight" sessionmanager.log | tail -5
```

---

## Заключение

Боевая система — это самая критичная часть VCode использования, потому что:

1. Требуется **валидный VCode на каждый ход**
2. **Ходы отправляются быстро** (нет времени на fallback)
3. **Прерывание боя** — это серьёзное нарушение игрового процесса

Три главных принципа:

1. **`markFightInProgress()` ДО `new LezFight()`** — это критичный порядок
2. **Всегда проверяйте null VCode** — даже в FIGHT_FALLBACK_MODE
3. **Логируйте всё** — возраст VCode, обновления, ошибки

Следование этим правилам гарантирует бесперебойный боевой процесс даже при сложных условиях синхронизации.
