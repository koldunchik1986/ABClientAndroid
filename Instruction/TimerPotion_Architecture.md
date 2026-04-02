# Архитектура таймеров пьющих зелья: ПК-версия vs Android

## 1. ПК-Версия (C#) - Как работает

### 1.1 Структура данных таймера (AppTimer.cs)

```csharp
internal sealed class AppTimer
{
    internal DateTime TriggerTime = DateTime.MinValue;    // Когда срабатить
    internal string Description = string.Empty;           // Описание ("Пить зелье разума")
    internal string Potion = string.Empty;                // Название зелья ("Эликсир Блаженства")
    internal int DrinkCount;                              // Количество раз (1, 2, 5 и т.д.)
    internal bool IsRecur;                                // Повторяется ли
    internal int EveryMinutes;                            // Каждые N минут
    internal string Destination = string.Empty;           // Куда идти (опционально)
    internal string Complect = string.Empty;              // Комплект надеть (опционально)
}
```

### 1.2 Управление таймерами (AppTimerManager.cs)

- Хранит **отсортированный список** таймеров
- **Thread-safe** через `ReaderWriterLock`
- Можно добавлять, удалять, получать таймеры
- Новые таймеры вставляются в правильную позицию по времени срабатывания

### 1.3 Проверка и срабатывание таймеров (FormMainTimers.cs, UpdateTimers)

```csharp
private void UpdateTimers()
{
    again:
    var arrayAppTimers = AppTimerManager.GetTimers();
    for (var i = 0; i < arrayAppTimers.Length; i++)
    {
        // ШАГ 1: Проверяем время срабатывания
        if (DateTime.Now <= arrayAppTimers[i].TriggerTime)
        {
            continue;  // Еще не пора
        }

        // ШАГ 2: КРИТИЧНО! Если FastNeed активна, ждём её завершения
        if (AppVars.FastNeed)
        {
            return;  // ← Выходим и не обрабатываем больше таймеров
                     // ← Будем проверены снова в следующий цикл UI
        }

        // ШАГ 3: Если это таймер с зельем - обрабатываем зелье
        if (!string.IsNullOrEmpty(arrayAppTimers[i].Potion))
        {
            // ВЫЗОВ: FastStartSafe с названием зелья, никнеймом и количеством
            FastStartSafe(
                arrayAppTimers[i].Potion,              // "Эликсир Блаженства"
                AppVars.Profile.UserNick,              // никнейм профиля
                arrayAppTimers[i].DrinkCount            // количество
            );

            // ШАГ 4: Если повторяющийся - создаёт новый таймер на следующее время
            if (arrayAppTimers[i].IsRecur)
            {
                var nextTimer = new AppTimer
                {
                    TriggerTime = arrayAppTimers[i].TriggerTime
                        .AddMinutes(arrayAppTimers[i].EveryMinutes),
                    Description = arrayAppTimers[i].Description,
                    Potion = arrayAppTimers[i].Potion,
                    DrinkCount = arrayAppTimers[i].DrinkCount,
                    IsRecur = true,
                    EveryMinutes = arrayAppTimers[i].EveryMinutes
                };
                AppTimerManager.RemoveTimerAt(i);
                AppTimerManager.AddAppTimer(nextTimer);
            }
            else
            {
                // Удаляет одноразовый таймер
                AppTimerManager.RemoveTimerAt(i);
            }

            EventSounds.PlayTimer();  // Звук
            ReloadMainFrame();
            return;
        }
        // ... обработка других типов таймеров (Destination, Complect)
    }
}
```

### 1.4 Установка Fast-Action (FormMainFast.cs)

```csharp
private void FastStartSafe(string id, string nick, int count = 1)
{
    if (InvokeRequired)
    {
        BeginInvoke((MethodInvoker)(() => FastStartSafe(id, nick, count)));
        return;
    }

    // КЛЮЧЕВОЙ МОМЕНТ: Устанавливаем ВСЕ параметры ОДНОВРЕМЕННО
    AppVars.FastNeed = true;          // ← Флаг активности
    AppVars.FastId = id;              // ← НАЗВАНИЕ зелья!
    AppVars.FastNick = nick;          // ← На кого использовать
    AppVars.FastCount = count;        // ← Сколько раз
}
```

### 1.5 Отмена Fast-Action (FormMainFast.cs)

```csharp
internal void FastCancelSafe()
{
    if (InvokeRequired)
    {
        BeginInvoke((MethodInvoker)(FastCancelSafe));
        return;
    }

    // КЛЮЧЕВОЙ МОМЕНТ: Очищаем ВСЕ параметры ОДНОВРЕМЕННО
    AppVars.FastNeed = false;
    AppVars.FastId = null;
    AppVars.FastNick = null;
    AppVars.FastCount = 0;
    AppVars.FastNeedAbilDarkTeleport = false;
    AppVars.FastNeedAbilDarkFog = false;
    
    if (AppVars.FastWaitEndOfBoiActive)
        AppVars.FastWaitEndOfBoiCancel = true;
}
```

### 1.6 Обработка Fast-Action в main.php (MainPhp.cs 1429+)

**Логика для ЗЕЛИЙ (im=0&wca=27):**

```csharp
if (AppVars.FastNeed)
{
    if (DateTime.Now > AppVars.NeverTimer)
    {
        switch (AppVars.FastId)
        {
            case "Эликсир Блаженства":        // ← Эликсир в инвентаре im=6
            case "Зелье Блаженства":           // ← Зелье в инвентаре im=0&wca=27
            case "Зелье Невидимости":
            case "Зелье Лечения":
            // ... и ещё 30+ зелий
            
                // ШАГ 1: Если не на странице "зелья" - переходим туда
                invHtml = MainPhpFindInv(html, "&im=0&wca=27");
                if (!string.IsNullOrEmpty(invHtml))
                {
                    html = invHtml;
                    goto end;  // ← Возвращает HTML перенаправления
                }

                // ШАГ 2: Если уже на странице "зелья" - ищем нужное зелье
                if (MainPhpIsInv(html))
                {
                    // MainPhpFast() ищет кнопку зелья и генерирует HTML с POST-запросом
                    fastHtml = MainPhpFast(html);
                    
                    if (string.IsNullOrEmpty(fastHtml))
                    {
                        // ОШИБКА: Зелье не найдено
                        if (!address.EndsWith("im=0&wca=27"))
                        {
                            // Еще не на странице - перенаправляем
                            html = BuildRedirect("Переключение на зелья", "main.php?im=0&wca=27");
                        }
                        else
                        {
                            // Уже на странице - зелье потеряно
                            AppVars.MainForm.FastCancelSafe();  // ← ОТМЕНА
                            AppVars.MainForm.WriteChatMsgSafe("Зелье не обнаружено, действие отменено.");
                        }
                    }
                    else
                    {
                        // УСПЕХ: Нашли зелье и генерируем POST-запрос
                        AppVars.MainForm.WriteChatMsgSafe(
                            $"Используем {AppVars.FastId} на {AppVars.FastNick}");
                        
                        AppVars.FastCount--;
                        if (AppVars.FastCount == 0)
                        {
                            AppVars.MainForm.FastCancelSafe();  // ← ОЧИСТКА
                        }
                        
                        html = fastHtml;  // ← POST-запрос с использованием зелья
                    }
                }
                break;
        }
    }
}
```

**Ключевые моменты:**
1. Для **ЗЕЛИЙ**: переходит на `main.php?im=0&wca=27`
2. Для **ЭЛИКСИРОВ**: переходит на `main.php?im=6`
3. Для **СВИТКОВ**: переходит на `main.php?im=0&wca=28`
4. Генерирует POST-запрос с выбранным зельем
5. После успешного использования уменьшает `FastCount`
6. Когда `FastCount == 0`, вызывает `FastCancelSafe()` для полной очистки

---

## 2. Текущая Android-Версия - Проблема

### 2.1 Где вызывается (MainPhp.java ~2559)

```java
if (tied >= tiedHigh) {  // tied=24 >= 20
    android.util.Log.d(TAG, "AUTO_FISH_TRACE tied=24 > 20, trigger bliss elixir");
    FastActionManager.fastAttackBlazElixir();  // ← ВЫЗОВ
    return buildRedirectHtml("Авторыбалка: Эликсир Блаженства", "main.php");
}
```

### 2.2 Что делает FastActionManager.fastAttackBlazElixir() (FastActionManager.java)

```java
public static void fastAttackBlazElixir() {
    // ❌ ПРОБЛЕМА: Не устанавливает FastId!
    AppVars.FastNeed = true;      // ✅ Правильно
    AppVars.FastNick = userName;  // ✅ Правильно
    AppVars.FastCount = 1;        // ✅ Правильно
    // ❌ ОТСУТСТВУЕТ: AppVars.FastId = "Эликсир Блаженства"
}
```

### 2.3 Результат в логах

```
20:10:09.502 AUTO_FISH_TRACE tied=24 > 20, trigger bliss elixir
20:10:09.704 AUTO_DRINK_TRACE skip: FastNeed active, fastId=
                                                         ↑
                                                    ПУСТО!
20:10:10.368 AUTO_DRINK_TRACE skip: FastNeed active, fastId=
                                                         ↑
                                                    ПУСТО!
```

**Почему это проблема:**
- MapAjax видит `FastNeed=true` → пропускает авто-питьё
- Но `FastId=""` (пусто) → система не знает **как очистить** FastNeed
- FastNeed остаётся `true` **навсегда**
- Авто-питьё блокируется на всё время

---

## 3. Правильное решение для Android

### 3.1 Исправить FastActionManager.fastAttackBlazElixir()

```java
public static void fastAttackBlazElixir() {
    AppVars.FastNeed = true;
    AppVars.FastId = "Эликсир Блаженства";  // ← ДОБАВИТЬ
    AppVars.FastNick = userName;
    AppVars.FastCount = 1;
    FileLogger.trace(TAG, "fastAttackBlazElixir: FastNeed=true, FastId=" 
        + AppVars.FastId + ", nick=" + AppVars.FastNick);
}
```

### 3.2 Обновить ВСЕ методы быстрых действий в FastActionManager

**Текущие методы которые нужно исправить:**
- `fastAttackBlazElixir()` - установить FastId
- Все другие fast-методы - проверить что они установляют FastId

**Шаблон:**
```java
public static void fastActionSomething() {
    AppVars.FastNeed = true;
    AppVars.FastId = "Название действия";      // ← ДОЛЖНО БЫТЬ
    AppVars.FastNick = getUserNick();           // если нужно
    AppVars.FastCount = getCount();             // если нужно
    
    FileLogger.trace(TAG, "fastActionSomething: "
        + "FastNeed=true, FastId=" + AppVars.FastId
        + ", nick=" + AppVars.FastNick);
}
```

### 3.3 Синхронизировать с именами зелий в MainPhp.java

**Для эликсиров (нужно использовать одни из этих имён):**
- "Эликсир Блаженства"
- "Эликсир Мгновенного Исцеления"
- "Эликсир Восстановления"

**Для зелий (если понадобится):**
- "Зелье Блаженства"
- "Зелье Невидимости"
- "Зелье Лечения"
- и ещё 30+ зелий

### 3.4 Расширить MapAjax/MainPhp для обработки FastId

В MainPhp нужна логика как в ПК-версии:

```java
if (AppVars.FastNeed) {
    // Переходим на страницу с нужным типом инвентаря бэзде на FastId
    switch(AppVars.FastId) {
        case "Эликсир Блаженства":
        case "Эликсир Мгновенного Исцеления":
            // Переходим на главный инвентарь эликсиров (im=6)
            return buildRedirectHtml("Таймер: Переход на эликсиры", "main.php?im=6");
        case "Зелье Блаженства":
        case "Зелье Невидимости":
            // Переходим на зелья (im=0&wca=27)
            return buildRedirectHtml("Таймер: Переход на зелья", "main.php?im=0&wca=27");
        // ... и т.д.
    }
}
```

---

## 4. Рекомендуемая архитектура для Android (как в ПК)

### 4.1 Константы для названий (AppConsts.kt или AppVars.java)

```java
public static final String FAST_ID_BLISS_ELIXIR = "Эликсир Блаженства";
public static final String FAST_ID_QUICK_HEAL = "Эликсир Мгновенного Исцеления";
public static final String FAST_ID_POTION_BLISS = "Зелье Блаженства";
public static final String FAST_ID_POTION_INVISIBILITY = "Зелье Невидимости";
// ... и т.д.
```

### 4.2 Класс AppTimer (как в ПК)

```java
public class AppTimer {
    public Date triggerTime;          // Когда срабатить
    public String description;        // Описание
    public String potion;             // Название зелья
    public int drinkCount = 1;        // Сколько раз
    public boolean isRecurrent;       // Повторяется ли
    public int everyMinutes;          // Каждые N минут
    public String destination;        // Куда идти
    public String complect;           // Комплект надеть
}
```

### 4.3 Manager для таймеров (как в ПК)

```java
public class AppTimerManager {
    private static final List<AppTimer> timers = new CopyOnWriteArrayList<>();
    
    public static void addTimer(AppTimer timer) {
        // Добавить в отсортированный список
        // Thread-safe
    }
    
    public static void removeTimer(int index) {
        // Удалить таймер
    }
    
    public static AppTimer[] getTimers() {
        // Получить массив активных таймеров
        return timers.toArray(new AppTimer[0]);
    }
}
```

### 4.4 Обработчик проверки таймеров (как в ПК)

```kotlin
fun checkAndProcessTimers() {
    val timers = AppTimerManager.getTimers()
    
    for (timer in timers) {
        // Не срабатил ещё
        if (System.currentTimeMillis() <= timer.triggerTime.time) {
            continue
        }
        
        // ВАЖНО: Если FastNeed активна - ждём её
        if (AppVars.FastNeed) {
            return  // Выходим, проверим в следующий раз
        }
        
        // Обработка по типу таймера
        when {
            timer.potion != null && timer.potion.isNotEmpty() -> {
                // FastStartSafe(timer.potion, AppVars.profile.userNick, timer.drinkCount)
                FastActionManager.fastStartPotion(timer.potion, timer.drinkCount)
                
                // Если повторяющийся - создать новый
                if (timer.isRecurrent) {
                    val nextTimer = timer.copy(
                        triggerTime = Date(timer.triggerTime.time + timer.everyMinutes * 60000)
                    )
                    AppTimerManager.removeTimer(timers.indexOf(timer))
                    AppTimerManager.addTimer(nextTimer)
                }
                // Звук, обновление UI и т.д.
                return
            }
        }
    }
}
```

---

## 5. Краткое сравнение

| Аспект | ПК (C#) | Android (текущее) | Android (нужно) |
|--------|---------|-------------------|------------------|
| **FastId установка** | ✅ `FastId = id` | ❌ Пусто | ✅ `FastId = "Эликсир Блаженства"` |
| **FastNeed установка** | ✅ `FastNeed = true` | ✅ Есть | ✅ Есть |
| **FastCount** | ✅ Уменьшается | ❌ Нет обработки | ✅ Нужна обработка |
| **FastCount == 0** | ✅ Вызывает `FastCancelSafe()` | ❌ Не вызывается | ✅ Нужно добавить |
| **Таймеры в приложении** | ✅ Есть (AppTimerManager) | ❌ Только NeverTimer | ✅ Нужны пользовательские таймеры |
| **Thread-safe** | ✅ ReaderWriterLock | ⚠️ Зависит | ✅ Нужно использовать CopyOnWriteArrayList |
| **Повторяющиеся таймеры** | ✅ IsRecur + EveryMinutes | ❌ Нет | ✅ Нужны |

---

## 6. Ефект после исправления

### Было (текущее):
```
20:10:09.502 AUTO_FISH_TRACE tied=24 > 20, trigger bliss elixir
20:10:09.704 AUTO_DRINK_TRACE skip: FastNeed active, fastId=
                                                         ↑ ПУСТО
```

### Будет (после исправления):
```
20:10:09.502 AUTO_FISH_TRACE tied=24 > 20, trigger bliss elixir
20:10:09.503 FAST_ACTION_TRACE fastAttackBlazElixir: FastNeed=true, FastId=Эликсир Блаженства, nick=ProfileName
20:10:09.504 AUTO_DRINK_TRACE skip: FastNeed active, fastId=Эликсир Блаженства
                                                         ↑ ЕСТЬ (может обработаться)
20:10:15.200 MAIN_PHP_TRACE FastNeed=true, переход на main.php?im=6 (эликсиры)
20:10:20.100 FAST_ACTION_TRACE fastCancelSafe: FastNeed=false (после использования)
20:10:20.500 AUTO_DRINK_TRACE AUTO_DRINK работает снова
                         ↑ FastNeed очищена, система свободна
```

