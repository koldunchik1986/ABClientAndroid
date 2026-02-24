# План портирования: Полноценный Авто-Бой (LezFight Integration)

## 1. Назначение и общая архитектура

Авто-Бой — система автоматического ведения боя в игре Neverlands. При включении:
1. Когда открыт фрейм боя (`main.php` с переменной `var fight_ty`) — `LezFight.parse()` анализирует HTML, генерирует оптимальную комбинацию ударов/блоков/магии
2. Результат (`Frame`) — HTML с авто-submit формой, которая загружается в WebView вместо оригинальной страницы
3. WebView отправляет форму → сервер обрабатывает ход → страница обновляется → цикл повторяется

### Ключевые файлы ПК версии

| Файл | Назначение | Статус Android |
|------|-----------|----------------|
| `ABClient/Lez/LezFight.cs` | Логика парсинга и генерации комбинаций | ✅ `LezFight.java` |
| `ABClient/Lez/LezBotsGroup.cs` | Настройки группы противников | ✅ `LezBotsGroup.java` |
| `ABClient/Lez/LezBotsClass.cs` | Класс противника | ✅ `LezBotsClass.java` |
| `ABClient/Lez/LezBotsClassCollection.cs` | Коллекция классов | ✅ `LezBotsClassCollection.java` |
| `ABClient/Lez/LezSpell.cs` | Заклинание | ✅ `LezSpell.java` |
| `ABClient/Lez/LezSpellCollection.cs` | Коллекция заклинаний | ✅ `LezSpellCollection.java` |
| `ABClient/Lez/LezNode.cs` | Узел комбинации | ✅ `LezNode.java` |
| `ABClient/Lez/FormSettingsAb.cs` | Форма настроек автобоя | ❌ **НЕ портирована** |
| `ABClient/PostFilter/FightJs.cs` | Модификация fight.js | ✅ `FightJs.java` (портирован) |
| `ABClient/PostFilter/MainPhpFight.cs` | Обработка страницы боя | ❌ **НЕ интегрирован** |
| `ABClient/MyProfile/UserConfigVars.cs` | Переменные профиля | ✅ Добавлены в `UserConfig.java` |

---

## 2. Текущее состояние Android

### Что уже реализовано (проверено)

- `LezFight.java` — полноценный порт логики парсинга и генерации комбинаций ✅
- `LezBotsGroup.java`, `LezNode.java` и др. — портированы ✅
- `FightJs.java` — добавляет кнопки "автовыбор"/"автоход"/"автобой" в HTML боя ✅
- `UserConfig.java` — поля `LezDoAutoboi`, `LezGroups` и все Lez* переменные присутствуют ✅
- `AutoFunctionsManager.java` — переключатель `AUTO_FIGHT` изменяет `AppVars.Autoboi` ✅

### Критические пробелы

1. **MainPhpFight не вызывается из MainPhp.process()** — нет точки интеграции боя
2. **LezFight.parse() не вызывается** — результат (`fight.Frame`) никогда не возвращается в WebView
3. **Нет UI настроек автобоя** — FormSettingsAb не портирована
4. **Верхний фрейм (уведомление о нападении)** — не парсится/не отображается
5. **FightJs.java** — кнопки добавлены, но `AndroidBridge.AutoBoi()`, `AutoTurn()`, `AutoSelect()` не реализованы в WebAppInterface

---

## 3. Анализ MainPhpFight.cs

```csharp
// MainPhpFight.cs — вызывается из MainPhp.process() при обнаружении фрейма боя
private static string MainPhpFight(string htmlFight)
{
    var fight = new LezFight(htmlFight);

    if (!fight.IsValid) return AppVars.ContentMainPhp; // Испорченный фрейм

    if (fight.IsWaitingForNextTurn && AppVars.AutoRefresh) return fight.Frame;

    if (AppVars.Profile.LezDoAutoboi) {
        if (fight.IsBoi) {
            // В бою
            if (!fight.DoStop && !fight.IsLowHp && !fight.IsLowMa && !fight.DoExit) {
                return fight.Frame; // БЫСТРЫЙ БОЙ — возвращаем Frame с авто-submit
            }
            // Опасная ситуация — останавливаем, записываем в Timeout
            AppVars.Autoboi = AutoboiState.Timeout;
        } else {
            // Бой завершился — переходим в Restoring или AutoboiOn
            var newReady = fight.CalcRestoreAfterBoi();
            if (newReady >= 60) {
                AppVars.Autoboi = AutoboiState.Restoring;
            } else {
                AppVars.Autoboi = AutoboiState.AutoboiOn;
                if (!string.IsNullOrEmpty(AppVars.FightLink))
                    return BuildRedirect("Завершение боя", AppVars.FightLink);
            }
        }
    }

    return AppVars.ContentMainPhp;
}
```

**Ключевые условия работы:**
- `fight.Frame` — HTML с `<form action="main.php" method=POST>` + `<script>document.ff.submit();</script>`
- Когда Frame возвращается — WebView загружает его, форма автоматически submit'ится, страница обновляется
- Цикл: `main.php` → `MainPhpFight` → `fight.Frame` → WebView submit → `main.php` → ...

---

## 4. Анализ FightJs.cs / FightJs.java

### Что делает FightJs
Модифицирует `fight_v*.js` (загружается в iframe боя):
- Добавляет кнопки: "автовыбор", "автоход", "автобой"
- Добавляет функции: `AutoSubmit(result)`, `AutoSelect()`, `AutoTurn()`, `AutoBoi()`
- Добавляет таймер отображения времени хода

### Функции требующие AndroidBridge

```javascript
function AutoSelect() { AndroidBridge.AutoSelect(); }
function AutoTurn()   { AndroidBridge.AutoTurn(); }
function AutoBoi()    { AndroidBridge.AutoBoi(); }
function ResetCure()  { AndroidBridge.ResetCure(); }
// Таймер:
document.all("btx0").value = AndroidBridge.XodButtonElapsedTime();
```

### Статус в FightJs.java
В Android версии `FightJs.java` уже использует `AndroidBridge.XodButtonElapsedTime()` и `AndroidBridge.ResetLastBoiTimer()`.
Функции `AutoSelect()`, `AutoTurn()`, `AutoBoi()` добавлены в JS, но в `WebAppInterface.java` они **не реализованы**.

---

## 5. Анализ FormSettingsAb.cs — настройки автобоя

### Вкладки формы

#### Вкладка 1: Общие настройки автобоя
Управляет полями `AppVars.Profile.Lez*`:

| Элемент C# | Тип | Поле Profile | Описание |
|-----------|-----|-------------|----------|
| `checkDoAutoboi` | CheckBox | `LezDoAutoboi` | Включить авто-бой |
| `checkDoWaitHp` | CheckBox | `LezDoWaitHp` | Ждать восстановления HP |
| `linkWaitHp` | LinkLabel (int) | `LezWaitHp` | До скольких % HP ждать (0-100) |
| `checkDoWaitMa` | CheckBox | `LezDoWaitMa` | Ждать восстановления MA |
| `linkWaitMa` | LinkLabel (int) | `LezWaitMa` | До скольких % MA ждать (0-100) |
| `checkDoDrinkHp` | CheckBox | `LezDoDrinkHp` | Пить зелья HP |
| `linkDrinkHp` | LinkLabel (int) | `LezDrinkHp` | Пить если HP < % (0-100) |
| `checkDoDrinkMa` | CheckBox | `LezDoDrinkMa` | Пить зелья MA |
| `linkDrinkMa` | LinkLabel (int) | `LezDrinkMa` | Пить если MA < % (0-100) |
| `checkDoWinTimeout` | CheckBox | `LezDoWinTimeout` | Выйти из боя по таймауту |
| `radioSayNo/Chat/Clan/Pair` | RadioGroup | `LezSay` | Сообщение после боя (LezSayType) |

#### Вкладка 2: Группы противников
- `listGroups` — список `LezBotsGroup` из `AppVars.Profile.LezGroups`
- `comboNewGroup` — ComboBox из `LezBotsClassCollection.ListForComboBox()`
- `linkNewGroupLevel` — минимальный уровень (0-33)
- `buttonCreateGroup` / `buttonDeleteGroup`
- Нельзя удалить группу `Id=001, MinimalLevel=0` (группа "Все")

#### Вкладка 3: Настройки выбранной группы (Ротация)

| Элемент C# | Тип | Поле LezBotsGroup | Описание |
|-----------|-----|------------------|----------|
| `checkDoRestoreHp` | CheckBox | `DoRestoreHp` | Восстанавливать HP |
| `linkRestoreHp` | LinkLabel (int) | `RestoreHp` | Если HP ≤ % (0-100) |
| `checkDoRestoreMa` | CheckBox | `DoRestoreMa` | Восстанавливать MA |
| `linkRestoreMa` | LinkLabel (int) | `RestoreMa` | Если MA ≤ % (0-100) |
| `checkDoAbilBlocks` | CheckBox | `DoAbilBlocks` | Абилки-блоки |
| `checkDoAbilHits` | CheckBox | `DoAbilHits` | Абилки-удары |
| `checkDoMagHits` | CheckBox | `DoMagHits` | Магические удары |
| `linkMagHits` | LinkLabel (int) | `MagHits` | Ставить мag ударов по N (5-1000) |
| `checkDoMagBlocks` | CheckBox | `DoMagBlocks` | Магические блоки |
| `checkDoHits` | CheckBox | `DoHits` | Обычные удары |
| `checkDoBlocks` | CheckBox | `DoBlocks` | Обычные блоки |
| `checkDoMiscAbils` | CheckBox | `DoMiscAbils` | Прочие абилки |
| `listSpellsHits` | ListView (checkbox) | `SpellsHits` | Заклинания ударов |
| `listSpellsBlocks` | ListView (checkbox) | `SpellsBlocks` | Заклинания блоков |
| `listSpellsRestoreHp` | ListView (checkbox) | `SpellsRestoreHp` | Заклинания HP |
| `listSpellsRestoreMa` | ListView (checkbox) | `SpellsRestoreMa` | Заклинания MA |
| `listSpellsMisc` | ListView (checkbox) | `SpellsMisc` | Прочие заклинания |
| `buttonFullHp` | Button | — | Установить RestoreHp=100 |
| `buttonFullMa` | Button | — | Установить RestoreMa=100 |

#### Вкладка 4: Останов боя

| Элемент C# | Тип | Поле LezBotsGroup | Описание |
|-----------|-----|------------------|----------|
| `checkDoStopNow` | CheckBox | `DoStopNow` | Остановить немедленно |
| `checkDoStopLowHp` | CheckBox | `DoStopLowHp` | Остановить при низком HP |
| `linkStopLowHp` | LinkLabel (int) | `StopLowHp` | Остановить если HP ≤ % (0-100) |
| `checkDoStopLowMa` | CheckBox | `DoStopLowMa` | Остановить при низкой MA |
| `linkStopLowMa` | LinkLabel (int) | `StopLowMa` | Остановить если MA ≤ % (0-100) |
| `checkDoExit` | CheckBox | `DoExit` | Выйти из боя |
| `checkDoExitRisky` | CheckBox | `DoExitRisky` | Выйти при опасном противнике (ftype≥80 + человек) |

---

## 6. Отслеживание верхнего фрейма (уведомление о нападении)

### Что приходит от сервера при нападении

В HTML верхнего фрейма при нападении передаётся `var fight_ty = [...]` с данными:
- `fight_ty[0]` — тип боя (строка)
- `fight_ty[2]` — `ftype`: 0=обычный, 80+=опасный (от человека)
- `fight_ty[3]` — `'1'` = мы в активном бою, `'0'` = ждём хода
- `fight_ty[8]` — `LogBoi` — ID лога боя

В `param_en`:
- `param_en[0]` — имя противника (в кавычках)
- `param_en[1]`/`[2]` — текущий/макс HP противника
- `param_en[3]`/`[4]` — текущий/макс MA противника
- `param_en[5]` — уровень противника

В `slots_en[0]` — изображение противника (определяет тип):
- Начинается с `bot` → бот
- Начинается с `_xneto` → нетопырь
- Начинается с `_xsilf` → сильф
- Иначе → **Человек**

### Определение босса
```java
private boolean IsBossName(String name) {
    return name.equals("Королева Змей") ||
           name.equals("Хранитель Леса") ||
           name.equals("Громлех Синезубый") ||
           name.equals("Выползень");
}
```

### Что надо показывать пользователю (аналог TrayBalloon в C#)

При начале нового боя (`LogBoi != AppVars.LastBoiLog`):
- Имя противника + тип (бот/человек/невидимка/босс)
- Уровень противника
- HP/MA противника
- Тип боя (опасный если ftype >= 80)
- Уведомление в чат Android (LocalBroadcast ACTION_ADD_CHAT_MESSAGE)

### ParseFightLog (аналог C#)
При новом бое вызывается `ParseFightLog` — парсит историю боя и обновляет `AppVars.LastBoiLog`.

---

## 7. Полный план реализации

### Этап 1: Интеграция MainPhpFight в Filter/MainPhp ✅ → ❌

**Задача:** В `MainPhp.process()` добавить вызов логики боя.

**Файл:** `app/.../postfilter/MainPhp.java`

```java
// В методе process() — когда HTML содержит "var fight_ty ="
if (html.contains("var fight_ty = [")) {
    String fightFrame = processMainPhpFight(html);
    if (fightFrame != null) return fightFrame;
}
```

```java
private static String processMainPhpFight(String html) {
    LezFight fight = new LezFight(html);

    if (!fight.IsValid) return AppVars.ContentMainPhp;

    // Ждём хода противника + AutoRefresh
    if (fight.IsWaitingForNextTurn && AppVars.autoRefreshEnabled) return fight.Frame;

    if (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi &&
        AppVars.Autoboi == AutoboiState.AutoboiOn) {

        if (fight.IsBoi) {
            if (!fight.DoStop && !fight.IsLowHp && !fight.IsLowMa && !fight.DoExit) {
                return fight.Frame; // Быстрый бой
            }
            // Опасная ситуация
            if (AppVars.Autoboi != AutoboiState.Timeout) {
                notifyFightStopped(fight);
                AppVars.Autoboi = AutoboiState.Timeout;
            }
        } else {
            // Бой завершился
            if (AppVars.Autoboi != AutoboiState.Restoring) {
                AppVars.Autoboi = AutoboiState.AutoboiOn;
            }
        }
    }

    return null; // Показываем обычную страницу
}
```

- [ ] Добавить `processMainPhpFight()` в `MainPhp.java`
- [ ] Вызывать при обнаружении `var fight_ty = [`
- [ ] Добавить уведомление в чат при остановке боя
- [ ] Обрабатывать переход состояний Autoboi → Timeout → AutoboiOn

### Этап 2: Уведомление о нападении

**Задача:** При новом бое (смене LogBoi) показывать уведомление в чат.

- [ ] Добавить в `LezFight.Parse()` вызов `ParseFightLog()` при смене LogBoi
- [ ] Парсить имя, уровень, тип противника
- [ ] Отправлять в чат через `AppVars.ACTION_ADD_CHAT_MESSAGE`:
  ```
  Нападение: Орк [15] HP:120/200 MA:30/50
  (опасный бой!)
  ```
- [ ] Добавить `AppVars.LastBoiLog` обновление

**Примечание:** `AppVars.LastBoiLog` уже есть в Android AppVars.java ✅

### Этап 3: AndroidBridge — методы управления боем

**Файл:** `app/.../bridge/WebAppInterface.java`

Добавить методы для вызова из JavaScript кнопок в fight.js:

```java
@JavascriptInterface
public void AutoSelect() {
    // Один автовыбор хода (вызывает LezFight один раз)
    // Аналог window.external.AutoSelect() в C#
}

@JavascriptInterface
public void AutoTurn() {
    // Один автоход
}

@JavascriptInterface
public void AutoBoi() {
    // Включает/выключает полный автобой
    AutoFunctionsManager.getInstance(context).toggleAutoFight();
}

@JavascriptInterface
public void ResetCure() {
    // Сброс состояния лечения после боя
    AppVars.Autoboi = AutoboiState.AutoboiOn;
}

@JavascriptInterface
public String XodButtonElapsedTime() {
    // Возвращает строку типа " ход (1:23)"
    long elapsed = System.currentTimeMillis() - AppVars.LastBoiTimer;
    long mins = elapsed / 60000;
    long secs = (elapsed % 60000) / 1000;
    return String.format(Locale.getDefault(), " ход (%d:%02d)", mins, secs);
}

@JavascriptInterface
public void ResetLastBoiTimer() {
    AppVars.LastBoiTimer = System.currentTimeMillis();
}
```

- [ ] Добавить `AutoSelect()` в WebAppInterface
- [ ] Добавить `AutoTurn()` в WebAppInterface
- [ ] Добавить `AutoBoi()` в WebAppInterface
- [ ] Добавить `ResetCure()` в WebAppInterface
- [ ] Проверить/добавить `XodButtonElapsedTime()` в WebAppInterface
- [ ] Проверить/добавить `ResetLastBoiTimer()` в WebAppInterface
- [ ] `AppVars.LastBoiTimer` — хранить как `long` (миллисекунды)

### Этап 4: UI Настроек AutoBoi (AutoBoiSettingsActivity)

**Задача:** Создать экран настроек автобоя (аналог FormSettingsAb).

**Архитектурное решение:** `DialogFragment` с `TabLayout` (4 вкладки).

#### Файлы для создания:

```
app/.../ui/AutoBoiSettingsFragment.java    — главный DialogFragment
res/layout/dialog_autoboi_settings.xml    — основной layout с TabLayout
res/layout/tab_autoboi_general.xml        — вкладка 1: Общие
res/layout/tab_autoboi_groups.xml         — вкладка 2: Группы
res/layout/tab_autoboi_rotation.xml       — вкладка 3: Ротация
res/layout/tab_autoboi_stop.xml           — вкладка 4: Останов
res/layout/item_lez_spell.xml             — элемент списка заклинаний
res/layout/item_lez_group.xml             — элемент списка группы
```

#### Вкладка 1 (Общие): CheckBox + SeekBar/NumberPicker

```xml
<!-- Включить автобой -->
<CheckBox android:id="@+id/checkDoAutoboi" ... />

<!-- Ждать восстановления HP -->
<CheckBox android:id="@+id/checkDoWaitHp" ... />
<SeekBar android:id="@+id/seekWaitHp" ... />  <!-- 0-100% -->
<TextView android:id="@+id/tvWaitHp" ... />   <!-- "Пока HP не станет X%" -->

<!-- Ждать восстановления MA -->
<CheckBox android:id="@+id/checkDoWaitMa" ... />
<SeekBar android:id="@+id/seekWaitMa" ... />
<TextView android:id="@+id/tvWaitMa" ... />

<!-- Пить зелья HP -->
<CheckBox android:id="@+id/checkDoDrinkHp" ... />
<SeekBar android:id="@+id/seekDrinkHp" ... />
<TextView android:id="@+id/tvDrinkHp" ... />  <!-- "Если HP < X%" -->

<!-- Пить зелья MA -->
<CheckBox android:id="@+id/checkDoDrinkMa" ... />
<SeekBar android:id="@+id/seekDrinkMa" ... />
<TextView android:id="@+id/tvDrinkMa" ... />

<!-- Выйти по таймауту -->
<CheckBox android:id="@+id/checkDoWinTimeout" ... />

<!-- Сообщение после боя (RadioGroup) -->
<RadioGroup android:id="@+id/radioGroupSay" ... >
    <RadioButton android:id="@+id/radioSayNo" android:text="Не отправлять" />
    <RadioButton android:id="@+id/radioSayChat" android:text="В чат" />
    <RadioButton android:id="@+id/radioSayClan" android:text="В клан" />
    <RadioButton android:id="@+id/radioSayPair" android:text="Приватом" />
</RadioGroup>
```

#### Вкладка 2 (Группы): RecyclerView + Spinner + Button

```
[Список групп] <-- RecyclerView, каждый элемент показывает group.toString()
[Spinne: Тип противника] [Уровень: SeekBar 0-33]
[Кнопка: Создать группу] [Кнопка: Удалить группу]
```

#### Вкладка 3 (Ротация): CheckBox + SeekBar + RecyclerView списков заклинаний

Структура: те же поля что в `LoadGroup()` / `SaveGroup()`:
- Заголовок "Восстановление"
- DoRestoreHp + SeekBar RestoreHp
- DoRestoreMa + SeekBar RestoreMa
- Заголовок "Абилки"
- CheckBox x5 (DoAbilBlocks, DoAbilHits, DoMagHits, DoMagBlocks, DoHits, DoBlocks, DoMiscAbils)
- SeekBar MagHits (если DoMagHits)
- 5 списков заклинаний (RecyclerView с checkboxes):
  - SpellsHits, SpellsBlocks, SpellsRestoreHp, SpellsRestoreMa, SpellsMisc

**Важно:** Элементы списка заклинаний показывают: ID, Название, ОД, Мана, Тип.
Данные берутся из `LezSpellCollection.Spells`, `LezSpellCollection.Od`, `LezSpellCollection.PosMana`, `LezSpellCollection.PosType`.

#### Вкладка 4 (Останов): CheckBox + SeekBar

```
[DoStopNow CheckBox]
[DoStopLowHp CheckBox] + [SeekBar StopLowHp 0-100]
[DoStopLowMa CheckBox] + [SeekBar StopLowMa 0-100]
[DoExit CheckBox]
[DoExitRisky CheckBox]  <-- Выход при опасном (ftype>=80, человек)
```

#### Сохранение

```java
// В onSaveClicked():
UserConfig profile = AppVars.Profile;
profile.LezDoAutoboi = checkDoAutoboi.isChecked();
profile.LezDoWaitHp = checkDoWaitHp.isChecked();
// ... все поля
profile.LezGroups = new ArrayList<>(editedGroups); // deep copy
ProfileManager.save(profile, context);
```

- [ ] Создать `AutoBoiSettingsFragment.java` (DialogFragment, 4 вкладки)
- [ ] Создать XML layout файлы для каждой вкладки
- [ ] Реализовать `loadSettings()` / `saveSettings()`
- [ ] Реализовать `loadGroup()` / `saveGroup()`
- [ ] Реализовать создание/удаление групп
- [ ] Реализовать списки заклинаний с чекбоксами (RecyclerView)
- [ ] Добавить кнопки "Полное HP" / "Полное MA"
- [ ] Открывать из `AUTO_FIGHT` long press или из боковой шторки

### Этап 5: Интеграция WebViewRequestInterceptor

**Текущая проблема:** `WebViewRequestInterceptor.intercept()` закомментирован.
Без него `MainPhp.process()` не вызывается → `processMainPhpFight()` не сработает.

Это **блокирующая зависимость** — без раскомментирования interceptor'а автобой работать не будет.

См. план в `TODO/todo_DebugApp.md` — Этап 1: раскомментировать WebViewRequestInterceptor.

- [ ] Убедиться что `Filter.process()` вызывается для main.php ответов
- [ ] Проверить что `FightJs.process()` вызывается для fight_v*.js

### Этап 6: Сохранение/загрузка LezGroups в профиле

**Файл:** `UserConfig.java` — методы `load()` / `save()`

Необходимо убедиться что `LezGroups` (список `LezBotsGroup`) корректно сериализуется/десериализуется в XML профиля.

- [ ] Проверить сериализацию `LezBotsGroup` в `UserConfig.save()`
- [ ] Проверить десериализацию `LezBotsGroup` в `UserConfig.load()`
- [ ] Убедиться что `SpellsHits[]`, `SpellsBlocks[]` и другие int[] массивы сохраняются

---

## 8. Зависимости (граф)

```
MainPhp.process()
    └── processMainPhpFight(html)
            └── LezFight(html)
                    ├── LezBotsGroup (из AppVars.Profile.LezGroups)
                    ├── LezBotsClassCollection (определение типа противника)
                    ├── LezSpellCollection (коллекция спеллов)
                    └── LezNode (узлы комбинаций)

WebAppInterface (AndroidBridge)
    ├── AutoBoi() → AutoFunctionsManager.toggleAutoFight()
    ├── XodButtonElapsedTime() → AppVars.LastBoiTimer
    └── ResetLastBoiTimer() → AppVars.LastBoiTimer

AutoBoiSettingsFragment (новый)
    ├── LezBotsClassCollection (для Spinner)
    ├── LezSpellCollection (для списков заклинаний)
    └── AppVars.Profile (чтение/запись)
```

---

## 9. Переменные AppVars для проверки/добавления

Все уже есть в `AppVars.java`:
- `AppVars.Autoboi` ✅ (тип AutoboiState)
- `AppVars.LastBoiLog` ✅
- `AppVars.LastBoiTimer` ✅ (тип Date — нужно изменить на long для миллисекунд)
- `AppVars.FightLink` ✅
- `AppVars.Profile.LezDoAutoboi` ✅
- `AppVars.Profile.LezGroups` ✅

**Требует проверки:** `AppVars.LastBoiTimer` в Java — тип `Date`, в C# это `DateTime`. Для вычисления elapsed time нужен `long` (System.currentTimeMillis()).

---

## 10. Статус задач

### Этап 1: MainPhpFight интеграция
- [ ] Добавить `processMainPhpFight()` в `MainPhp.java`
- [ ] Вызвать при `html.contains("var fight_ty = [")`
- [ ] Обрабатывать все состояния AutoboiState
- [ ] Уведомлять в чат при остановке

### Этап 2: Уведомление о нападении
- [ ] При смене `LogBoi` — отправлять уведомление в чат
- [ ] Парсить имя/уровень/тип противника из `param_en`/`slots_en`
- [ ] Определять невидимку (levelprot == -1)
- [ ] Определять опасный бой (ftype >= 80)

### Этап 3: AndroidBridge методы
- [ ] `AutoSelect()` — ручной выбор одного хода
- [ ] `AutoTurn()` — один автоход
- [ ] `AutoBoi()` — переключение автобоя
- [ ] `ResetCure()` — сброс состояния
- [ ] `XodButtonElapsedTime()` — строка таймера хода
- [ ] `ResetLastBoiTimer()` — сброс таймера

### Этап 4: AutoBoiSettingsFragment (UI)
- [ ] Создать DialogFragment с TabLayout (4 вкладки)
- [ ] Вкладка 1: Общие настройки (CheckBox + SeekBar)
- [ ] Вкладка 2: Группы противников (RecyclerView + Spinner)
- [ ] Вкладка 3: Ротация (CheckBox + SeekBar + 5 списков заклинаний)
- [ ] Вкладка 4: Останов боя (CheckBox + SeekBar)
- [ ] Сохранение в Profile
- [ ] Открытие из AUTO_FIGHT (long press) или меню настроек

### Этап 5: WebViewRequestInterceptor
- [ ] Убедиться что Filter.process() вызывается для main.php
- [ ] Убедиться что FightJs.process() вызывается для fight_v*.js

### Этап 6: Сериализация LezGroups
- [ ] Проверить save/load LezBotsGroup в UserConfig.java
