# Детальный анализ: QuickButtons и AutoFunctions - сравнение ПК и Android

## 0. Важное уточнение по терминологии (27.02.2026)

| Android | ПК Версия | Описание |
|---------|-----------|----------|
| AUTO_FIGHT | Autoboi | Автобой (полноценный с комбинациями) |
| AUTO_SKIN | AutoSkin | Авто-Охота (разделывание, одевание ножей) |
| LezFight.java | LezFight.cs | Класс логики автобоя |

**Логика работы эксклюзивных функций (реализовано):**

Эксклюзивные функции - только одна может быть активна:
- Авто-Рыбалка (AUTO_FISH)
- Авто-Охота (AUTO_SKIN)
- Авто-Травник (AUTO_CUT)
- Авто-Приманка (AUTO_BAIT)

При включении **любой** эксклюзивной функции:
1. Авто-Бой включается автоматически (если был выключен)
2. Остальные эксклюзивные функции выключаются

---

## 1. Текущее состояние в Android

### 1.1 Реализовано

| Компонент | Статус | Описание |
|-----------|--------|----------|
| QuickButtonsPanel | ✅ Готов | UI панель из 20 кнопок |
| QuickButtonsManager | ✅ Готов | Управление кнопками, SharedPreferences |
| QuickActionType enum | ✅ Готов | 18 типов действий |
| AutoFunctionsManager | ⚠️ Частично | Только ON/OFF переключатели |
| FastActionManager | ✅ Готов | Быстрые действия с HTML парсингом |

### 1.2 Проблема

**AutoFunctionsManager** только хранит состояние в SharedPreferences, но **не выполняет никакую логику**.

```java
// Текущая реализация - только переключатель:
public void setAutoDrinkEnabled(boolean enabled) {
    prefs.edit().putBoolean(KEY_PREFIX + "auto_drink", enabled).apply();
    Log.d(TAG, "setAutoDrinkEnabled: " + enabled);
    // ЛОГИКА НЕ РЕАЛИЗОВАНА!
}
```

---

## 2. Как работают авто-функции в ПК версии

### 2.1 Общая архитектура (C#)

1. Пользователь включает авто-функцию через UI (checkbox)
2. При каждой загрузке `main.php` срабатывает `MainPhp.process()`
3. Внутри process() проверяются флаги (AppVars.AutoDrink, AppVars.AutoMoving и т.д.)
4. Если флаг установлен и выполняются условия → генерируется HTML с авто-submit формой
5. WebView автоматически отправляет форму → сервер выполняет действие

### 2.2 Пример: AutoDrink (авто-питье)

**Файл:** `ABClient/PostFilter/MainPhp.cs` (строки ~1697-1814)

```csharp
// Проверка: если AutoDrink включен и прошло достаточно времени
if (AppVars.AutoDrink && (DateTime.Now > AppVars.NeverTimer))
{
    // Ищем зелье в инвентаре
    var invHtml = MainPhpFindInv(html, "&im=0&wca=27");
    // Генерируем HTML форму для использования
    return BuildRedirect invHtml...;
}
```

**Логика:**
- Проверяет таймер (чтобы не использовать слишком часто)
- Ищет доступное зелье в инвентаре
- Формирует POST-форму с vcode
- Автоматически отправляет

### 2.3 Пример: AutoMoving (авто-движение)

**Файл:** `ABClient/PostFilter/MapAjax.cs`

```csharp
// Проверяем текущую локацию
if (AppVars.AutoMoving)
{
    // Вычисляем следующий переход
    AppVars.AutoMovingNextJump = AppVars.AutoMovingMapPath.NextJump;
    AppVars.AutoMovingJumps = AppVars.AutoMovingMapPath.Jumps;
    
    // Генерируем форму перехода
    return BuildAjaxMove(...);
}
```

**Логика:**
- Использует MapPath для построения маршрута
- Выполняет AJAX переходы между локациями
- Отслеживает количество оставшихся переходов

### 2.4 Пример: AutoCure (авто-лечение)

**Файл:** `ABClient/PostFilter/MainPhp.cs` (строки ~1313-1394)

```csharp
// Проверяем HP
if ((AppVars.Profile.DoAutoCure) && ...)
{
    // Проверяем текущее HP
    int currentHp = ...;
    int maxHp = ...;
    int percentHp = (currentHp * 100) / maxHp;
    
    // Если HP ниже порога - используем зелье
    if (percentHp <= AppVars.Profile.AutoCurePercent)
    {
        // Генерируем форму использования зелья
    }
}
```

---

## 3. Что нужно портировать

### 3.1 Приоритет 1: Базовая инфраструктура

| Задача | Файл ПК | Описание |
|--------|---------|----------|
| Добавить переменные в AppVars (Android) | `AppVars.cs` | Хранение состояния авто-функций |
| Добавить обработку в MainPhp | `MainPhp.cs` | Триггеры авто-функций при загрузке страниц |
| Добавить HTML генерацию | `MainPhp.cs` | Генерирование форм |

### 3.2 Приоритет 2: Реализация авто-функций

| Функция | Статус в Android | Что нужно сделать |
|---------|------------------|-------------------|
| AUTO_DRINK | Заглушка | Портировать логику проверки HP/MP и использования зелий |
| AUTO_MOVING | Заглушка | Портировать логику перемещения по локациям |
| AUTO_CURE | Заглушка | Портировать логику авто-лечения |
| AUTO_FISH | Заглушка | Портировать логику авторыбалки |
| LEZ_FIGHT | Заглушка | Портировать логику автоохоты (сложно!) |
| AUTO_INVISIBLE | Заглушка | Портировать логику авто-невидимости |
| AUTO_DETECT | Заглушка | Портировать логику обнаружения |
| AUTO_SUMMON | Заглушка | Портировать логику авто-тотема |
| LOCATION_TRACKING | Заглушка | Отслеживание текущей локации |
| AUTO_REFRESH | Заглушка | Авто-обновление страницы |
| AUTO_CUT | Заглушка | Портировать логику травника |

### 3.3 Приоритет 3: UI настройки

| Задача | Описание |
|--------|----------|
| Создать экран настроек авто-функций | Диалог/активность для конфигурации |
| Сохранение профиля | UserConfigVars аналог |

---

## 4. Архитектура для портирования

### 4.1 Новый класс: AutoFunctionsExecutor

```java
/**
 * Исполнитель авто-функций.
 * Аналог логики из MainPhp.cs в ПК версии.
 * Запускается при каждой загрузке main.php.
 */
public class AutoFunctionsExecutor {
    
    /**
     * Обрабатывает HTML страницы и выполняет авто-функции если нужно.
     * Вызывается из WebViewClient.afterPageLoaded()
     * 
     * @param html HTML содержимое страницы
     * @return HTML для отображения (может быть изменен)
     */
    public String processMainPhp(String html) {
        if (AppVars.AutoDrink) {
            html = processAutoDrink(html);
        }
        if (AppVars.AutoMoving) {
            html = processAutoMoving(html);
        }
        if (AppVars.Profile.DoAutoCure) {
            html = processAutoCure(html);
        }
        // ... остальные функции
        return html;
    }
}
```

### 4.2 Интеграция в WebView

```java
// В MainActivity или WebViewClient
@Override
public void onPageFinished(WebView view, String url) {
    if (url.contains("main.php")) {
        // Получаем HTML (через shouldInterceptRequest или evaluateJavascript)
        String html = getPageHtml();
        
        // Обрабатываем авто-функции
        AutoFunctionsExecutor executor = AutoFunctionsExecutor.getInstance(this);
        String newHtml = executor.processMainPhp(html);
        
        if (!newHtml.equals(html)) {
            // Загружаем модифицированный HTML
            view.loadDataWithBaseURL(...);
        }
    }
}
```

---

## 5. Детальная логика авто-функций

### 5.1 AUTO_DRINK (Авто-Питье)

**Логика:**
1. Проверить таймер (AppVars.NeverTimer + интервал)
2. Проверить HP% и MP%
3. Если ниже порога → искать зелье в инвентаре
4. Сформировать POST форму
5. Вернуть HTML с авто-submit

**Android аналог:**
```java
private String processAutoDrink(String html) {
    // 1. Проверяем таймер
    long now = System.currentTimeMillis();
    if (now - lastDrinkTime < MIN_DRINK_INTERVAL) {
        return html;
    }
    
    // 2. Проверяем HP
    int hpPercent = getCurrentHpPercent();
    if (hpPercent <= AppVars.Profile.AutoDrinkHpPercent) {
        // 3. Ищем зелье
        String potionHtml = findPotionInInventory(html, "Зелье Лечения");
        if (potionHtml != null) {
            lastDrinkTime = now;
            return generatePotionForm(potionHtml);
        }
    }
    
    return html;
}
```

### 5.2 AUTO_MOVING (Авто-Движение)

**Логика:**
1. Проверить текущую локацию (AppVars.Profile.MapLocation)
2. Сравнить с пунктом назначения
3. Если не достигли → вычислить следующий переход
4. Сформировать AJAX форму перехода

**Android аналог:**
```java
private String processAutoMoving(String html) {
    String currentLoc = AppVars.Profile.MapLocation;
    String dest = AppVars.AutoMovingDestination;
    
    if (currentLoc.equals(dest)) {
        // Достигли цели - выключаем
        AppVars.AutoMoving = false;
        return html;
    }
    
    // Вычисляем маршрут
    MapPath path = new MapPath(currentLoc, dest);
    if (path.hasNextJump()) {
        String nextJump = path.getNextJump();
        return generateMoveForm(nextJump);
    }
    
    return html;
}
```

### 5.3 AUTO_CURE (Авто-Лечение)

**Логика:**
1. Получить текущие HP из HTML (парсинг param_ow)
2. Вычислить процент HP
3. Если ниже порога DoAutoCure → использовать зелье

---

## 6. План реализации

### Этап 1: Подготовка инфраструктуры

- [ ] Создать класс `AutoFunctionsExecutor`
- [ ] Добавить интеграцию с WebViewClient
- [ ] Добавить необходимые поля в AppVars (Android)

### Этап 2: AUTO_DRINK

- [ ] Реализовать `processAutoDrink()`
- [ ] Добавить настройки (порог HP%, интервал)
- [ ] Протестировать

### Этап 3: AUTO_MOVING

- [ ] Реализовать `MapPath` класс
- [ ] Реализовать `processAutoMoving()`
- [ ] Протестировать

### Этап 4: AUTO_CURE

- [ ] Реализовать `processAutoCure()`
- [ ] Добавить настройки профиля
- [ ] Протестировать

### Этап 5: Остальные функции

- [ ] AUTO_FISH
- [ ] AUTO_INVISIBLE
- [ ] AUTO_DETECT
- [ ] AUTO_SUMMON
- [ ] LOCATION_TRACKING
- [ ] AUTO_REFRESH
- [ ] AUTO_CUT

---

## 7. Источники для изучения

### Ключевые файлы ПК версии

| Файл | Описание |
|------|----------|
| `ABClient/PostFilter/MainPhp.cs` | Основная обработка main.php, триггеры авто-функций |
| `ABClient/AppVars.cs` | Глобальные переменные |
| `ABClient/PostFilter/TeleportAjax.cs` | Логика перемещения |
| `ABClient/PostFilter/MapAjax.cs` | AJAX переходы между локациями |
| `ABClient/ABForms/FormMainMap.cs` | UI карты |
| `ABClient/Lez/LezFight.cs` | Логика автобоя (очень сложная) |

### Файлы для создания в Android

| Файл | Назначение |
|------|------------|
| `manager/AutoFunctionsExecutor.java` | Основной исполнитель |
| `utils/MapPath.java` | Класс для построения маршрута |
| `utils/InventoryHelper.java` | Поиск предметов в инвентаре |
| `filter/AutoFunctionsFilter.java` | Фильтр для WebView |

---

## 8. Сложности и ограничения

1. **LezFight (автоохота)** - Очень сложная логика, требует полного парсинга боя, выбора оптимальной комбинации ударов/блоков. Рекомендуется реализовать в последнюю очередь.

2. **WebView ограничения** - В Android WebView сложнее перехватывать и модифицировать HTML после загрузки. Нужно использовать `shouldInterceptRequest` или `evaluateJavascript`.

3. **Асинхронность** - Некоторые операции требуют ожидания от сервера. Нужна правильная обработка таймеров.

4. **Состояние профиля** - Многие настройки хранятся в профиле пользователя. Нужно синхронизировать с Android SharedPreferences.
