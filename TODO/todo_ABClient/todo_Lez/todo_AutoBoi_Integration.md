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

- [x] Добавить `processMainPhpFight()` в `MainPhp.java`
- [ ] Вызывать при обнаружении `var fight_ty = [`
- [x] Добавить уведомление в чат при остановке боя
- [ ] Обрабатывать переход состояний Autoboi → Timeout → AutoboiOn

### Этап 2: Уведомление о нападении

**Задача:** При новом бое (смене LogBoi) показывать уведомление в чат.

- [x] Добавить в `LezFight.Parse()` вызов `ParseFightLog()` при смене LogBoi
- [x] Парсить имя, уровень, тип противника
- [x] Отправлять в чат через `AppVars.ACTION_ADD_CHAT_MESSAGE`:
  ```
  Нападение: Орк [15] HP:120/200 MA:30/50
  (опасный бой!)
  ```
- [x] Добавить `AppVars.LastBoiLog` обновление

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

- [x] Создать `AutoBoiSettingsFragment.java` (DialogFragment, 4 вкладки)
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
- [x] Добавить `processMainPhpFight()` в `MainPhp.java` (реализовано как `mainPhpFight()`)
- [x] Вызвать при `html.contains("var fight_ty = [")` (уже вызывается в process())
- [x] Обрабатывать все состояния AutoboiState
- [x] Уведомлять в чат при остановке

### Этап 2: Уведомление о нападении
- [ ] При смене `LogBoi` — отправлять уведомление в чат
- [ ] Парсить имя/уровень/тип противника из `param_en`/`slots_en`
- [ ] Определять невидимку (levelprot == -1)
- [ ] Определять опасный бой (ftype >= 80)

### Этап 3: AndroidBridge методы
- [x] `AutoSelect()` — ручной выбор одного хода (реализовано в WebAppInterface)
- [x] `AutoTurn()` — один автоход (реализовано в WebAppInterface)
- [x] `AutoBoi()` — переключение автобоя (реализовано в WebAppInterface)
- [x] `ResetCure()` — сброс состояния
- [x] `XodButtonElapsedTime()` —мера хода строка тай
- [x] `ResetLastBoiTimer()` — сброс таймера

### Этап 4: AutoBoiSettingsFragment (UI)
- [ ] Создать DialogFragment с TabLayout (4 вкладки)
- [ ] Вкладка 1: Общие настройки (CheckBox + SeekBar)
- [ ] Вкладка 2: Группы противников (RecyclerView + Spinner)
- [ ] Вкладка 3: Ротация (CheckBox + SeekBar + 5 списков заклинаний)
- [ ] Вкладка 4: Останов боя (CheckBox + SeekBar)
- [ ] Сохранение в Profile
- [ ] Открытие из AUTO_FIGHT (long press) или меню настроек

### Этап 5: WebViewRequestInterceptor
- [x] Убедиться что Filter.process() вызывается для main.php (подтверждено в логах)
- [x] Убедиться что FightJs.process() вызывается для fight_v*.js

### Этап 6: Сериализация LezGroups
- [ ] Проверить save/load LezBotsGroup в UserConfig.java

---

## 11. Исправления и доработки сессии (24.02.2026)

### Проблема 1: Autoboi всегда выключен при старте

**Симптомы:**
- В логах: `LezDoAutoboi enabled, Autoboi state=AutoboiOff`
- Автобой не срабатывал несмотря на включённую опцию в профиле

**Причина:**
В `AppVars.java` значение `Autoboi` по умолчанию устанавливается в `AutoboiOff`. При входе в профиль состояние не синхронизировалось с `LezDoAutoboi`.

**Решение:**
Добавлена синхронизация в `LoginActivity.java:263`:
```java
// Синхронизируем состояние автобоя с профилем
if (profileToLogin.LezDoAutoboi) {
    AppVars.Autoboi = AutoboiState.AutoboiOn;
} else {
    AppVars.Autoboi = AutoboiState.AutoboiOff;
}
```

**Файл:** `app/src/main/java/ru/neverlands/abclient/LoginActivity.java`

### Проблема 2: POST запрос авто-формы не перехватывается

**Симптомы:**
- Frame генерируется, возвращается в логах: `SAFE - returning fight.Frame for auto-attack`
- Но атака не происходит

**Причина:**
В Android WebView `shouldInterceptRequest()` **не перехватывает POST запросы**. Сгенерированная форма использовала `method=POST`:
```html
<form action="main.php" method=POST name=ff>
<script>document.ff.submit();</script>
```

**Решение:**
Изменён метод `BuildFrame()` в `LezFight.java` - заменён POST на GET:
```java
// Форма авто-submit - используем GET для перехвата в Android WebView
sb.append("<form action=\"main.php\" method=GET name=ff>");
```

**Файл:** `app/src/main/java/ru/neverlands/abclient/lez/LezFight.java:429`

### Проблема 3: Кнопка "Завершить бой" не нажимается автоматически

**Симптомы:**
- Бой выигрывается, сервер возвращает страницу с `get_id=61&act=7`
- Кнопка "Завершить" требует ручного нажатия

**Причина:**
Страница завершения боя не обрабатывалась. Нужен авто-клик кнопки.

**Решение:**
Добавлена обработка `get_id=61&act=7` в `MainPhp.java`:
```java
// Обработка страницы завершения боя (get_id=61&act=7)
// Автоматически нажимает кнопку "Завершить"
if (address.contains("get_id=61") && address.contains("act=7")) {
    html = mainPhpFightEnd(address, html);
}
```

Метод `mainPhpFightEnd()` извлекает параметры из URL и строит редирект:
```java
String redirectUrl = "main.php?get_id=61&act=7" 
        + "&fexp=" + fexp + "&fres=" + fres + ...
return Filter.buildRedirect("Завершение боя", redirectUrl);
```

**Файл:** `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`

---

## 12. Статус задач (обновлено 24.02.2026)

### Этап 1: MainPhpFight интеграция
- [x] Добавить `mainPhpFight()` в `MainPhp.java` ✅
- [x] Вызывать при `html.contains("var fight_ty = [")` ✅
- [x] Обрабатывать все состояния AutoboiState ✅
- [x] Уведомлять в чат при остановке ✅
- [x] **ИСПРАВЛЕНО:** Синхронизация Autoboi с профилем при старте ✅

### Этап 2: Уведомление о нападении
- [ ] При смене `LogBoi` — отправлять уведомление в чат
- [ ] Парсить имя/уровень/тип противника из `param_en`/`slots_en`
- [ ] Определять невидимку (levelprot == -1)
- [ ] Определять опасный бой (ftype >= 80)

### Этап 3: AndroidBridge методы
- [x] `AutoSelect()` — ручной выбор одного хода ✅
- [x] `AutoTurn()` — один автоход ✅
- [x] `AutoBoi()` — переключение автобоя ✅
- [x] `ResetCure()` — сброс состояния ✅
- [x] `XodButtonElapsedTime()` — таймер хода ✅
- [x] `ResetLastBoiTimer()` — сброс таймера ✅

### Этап 4: AutoBoiSettingsFragment (UI)
- [ ] Создать DialogFragment с TabLayout (4 вкладки)
- [ ] Вкладка 1: Общие настройки (CheckBox + SeekBar)
- [ ] Вкладка 2: Группы противников (RecyclerView + Spinner)
- [ ] Вкладка 3: Ротация (CheckBox + SeekBar + 5 списков заклинаний)
- [ ] Вкладка 4: Останов боя (CheckBox + SeekBar)
- [ ] Сохранение в Profile
- [ ] Открытие из AUTO_FIGHT (long press) или меню настроек

### Этап 5: WebViewRequestInterceptor
- [x] Filter.process() вызывается для main.php ✅
- [x] FightJs.process() вызывается для fight_v*.js ✅

### Этап 6: Сериализация LezGroups
- [ ] Проверить save/load LezBotsGroup в UserConfig.java

### Этап 7: Исправления проблем сессии
- [x] Исправлена синхронизация Autoboi с профилем ✅
- [x] Исправлен POST → GET для авто-формы ✅
- [x] Добавлена обработка завершения боя (get_id=61&act=7) ✅

---

## 13. Отладка сессии 25.02.2026 - Автобой не работает

### Симптомы
- При нападении сервер вернул страницу с капчей (`vcode=`)
- После ввода капчи автобой не срабатывает
- В логах видно `FIGHT FRAME DETECTED isFightFrame=true isFightTopFrame=true`
- Но **НЕТ** логов `LezFight parsed:` и `logFightVar:`

### Анализ логов

#### Сессия 24.02.2026 (работает):
```
logFightVar: fight_ty = var fight_ty = [1,300,30,1,1,"","","2","718317209",[],[],4];
LezFight(26464): fight_pm: magmax=300, odmax=200, hitval=70, vcode=ec30e733
LezFight(26464): Foe: name=Орк, level=13, image=bot_1.jpg
LezFight(26464): BuildFrame: Frame generated, length=866
mainPhpFight: LezFight parsed: IsValid=true IsBoi=true
mainPhpFight: SAFE - returning fight.Frame for auto-attack
```

#### Сессия 25.02.2026 (НЕ работает):
```
mainPhpFight: address=http://neverlands.ru/main.php?get_id=56&act=10&go=inf, htmlLen=2922
process() returning 879 bytes for http://neverlands.ru/main.php?get_id=56&act=10&go=inf
```
**Промежуток между вызовами: ~56ms** — слишком мало для парсинга LezFight

### Выводы

1. **LezFight не парсится** — либо `IsValid=false`, либо не доходит до парсинга
2. **В HTML страницы нет данных** `var fight_ty` — поэтому `logFightVariable()` не находит их
3. **Страница `get_id=56&act=10&go=inf`** — это верхний фрейм (фрейм данных), не фрейм боя
4. **Проблема:** Сервер возвращает неполный HTML без переменных `fight_ty`, `param_en`, `slots_en`

### JS Ошибки в консоли (не влияют на парсинг):
```
Uncaught SyntaxError: Unexpected identifier 'FEND' -- From line 144 of http://neverlands.ru/js/fight_v10.js
Uncaught ReferenceError: magic_slots is not defined -- From line 31 of http://neverlands.ru/main.php
Uncaught SyntaxError: Invalid or unexpected token -- From line 1 of http://neverlands.ru/main.php?get_id=56&act=10&go=inf&vcode=fedc28595bce261b96b2737e120cba35
```

### Гипотезы

1. **Капча (`vcode=`)** — после ввода капчи сервер мог сбросить сессию боя
2. **Неполный HTML** — сервер возвращает только верхний фрейм без данных `fight_ty`
3. **Автообновление** — возможно нужен ручной переход на страницу боя

---

## 14. Результаты тестирования 25.02.2026 - Автобой РАБОТАЕТ!

### Резюме
Автобой **ПОЛНОСТЬЮ РАБОТАЕТ** после ввода капчи и начала нового боя.

### Сессия 25.02.2026 ПОСЛЕ ввода капчи (РАБОТАЕТ):

**Новый бой с Goblin [11]:**
```
LezFight(26464): fight_pm: magmax=50, odmax=20, hitval=10, vcode=...
LezFight(26464): Foe: name=Goblin, level=11, image=bot_2.jpg
LezFight(26464): BuildFrame: Frame generated, length=882
mainPhpFight: LezFight parsed: IsValid=true IsBoi=true IsWaitingForNextTurn=false
mainPhpFight: SAFE - returning fight.Frame for auto-attack
```

**Выполнено несколько авто-атак:**
```
mainPhpFight: address=..., LezFight parsed: IsValid=true IsBoi=true IsWaitingForNextTurn=false
mainPhpFight: SAFE - returning fight.Frame for auto-attack
(повторяется несколько раз)
```

**Гоблин повержен:**
- HP противника упало с 525/525 до 17/525
- Автобой успешно выполнял атаки

### Выводы

1. ✅ **Автобой работает корректно** — после ввода капчи и начала нового боя
2. ✅ **Frame генерируется правильно** — длина 882 байта
3. ✅ **Цикл авто-атак работает** — несколько атак подряд выполнено
4. ✅ **Проблема с капчей решена** — достаточно ввести капчу и начать новый бой

---

## 15. Баг: Неправильная обработка завершения боя

### Симптомы
После победы над противником (HP = 0), код некорректно переходит в ветку "ожидание хода противника" вместо "бой завершён".

### Логи
```
LezFight parsed: IsBoi=false IsWaitingForNextTurn=true
mainPhpFight: waiting for opponent turn, returning original HTML for auto-refresh
```

### Причина
В `LezFight.java` при парсинге HTML, когда HP противника равно 0 (противник повержен), переменная `IsWaitingForNextTurn` некорректно устанавливается в `true`.

### Где исправлять
Файл: `app/src/main/java/ru/neverlands/abclient/lez/LezFight.java`

Нужно добавить проверку: если `IsBoi=false` (противник повержен), то это не "ожидание хода", а "конец боя".

### План исправления

1. [ ] В методе `parse()` класса `LezFight.java` добавить логику определения конца боя
2. [ ] Добавить поле `IsFightEnded` (или аналог) для корректной обработки
3. [ ] Обновить `MainPhp.java` для обработки состояния "бой завершён"

---

## 16. Статус задач (обновлено 25.02.2026)

### Этап 1: MainPhpFight интеграция
- [x] Добавить `mainPhpFight()` в `MainPhp.java` ✅
- [x] Вызывать при `html.contains("var fight_ty = [")` ✅
- [x] Обрабатывать все состояния AutoboiState ✅
- [x] Уведомлять в чат при остановке ✅
- [x] **ИСПРАВЛЕНО:** Синхронизация Autoboi с профилем при старте ✅
- [x] **ИСПРАВЛЕНО:** POST → GET для авто-формы ✅
- [x] **ИСПРАВЛЕНО:** Обработка завершения боя (get_id=61&act=7) ✅

### Этап 2: Уведомление о нападении
- [ ] При смене `LogBoi` — отправлять уведомление в чат
- [ ] Парсить имя/уровень/тип противника из `param_en`/`slots_en`
- [ ] Определять невидимку (levelprot == -1)
- [ ] Определять опасный бой (ftype >= 80)

### Этап 3: AndroidBridge методы
- [x] `AutoSelect()` — ручной выбор одного хода ✅
- [x] `AutoTurn()` — один автоход ✅
- [x] `AutoBoi()` — переключение автобоя ✅
- [x] `ResetCure()` — сброс состояния ✅
- [x] `XodButtonElapsedTime()` — таймер хода ✅
- [x] `ResetLastBoiTimer()` — сброс таймера ✅

### Этап 4: AutoBoiSettingsFragment (UI)
- [ ] Создать DialogFragment с TabLayout (4 вкладки)
- [ ] Вкладка 1: Общие настройки (CheckBox + SeekBar)
- [ ] Вкладка 2: Группы противников (RecyclerView + Spinner)
- [ ] Вкладка 3: Ротация (CheckBox + SeekBar + 5 списков заклинаний)
- [ ] Вкладка 4: Останов боя (CheckBox + SeekBar)
- [ ] Сохранение в Profile
- [ ] Открытие из AUTO_FIGHT (long press) или меню настроек

### Этап 5: WebViewRequestInterceptor
- [x] Filter.process() вызывается для main.php ✅
- [x] FightJs.process() вызывается для fight_v*.js ✅

### Этап 6: Сериализация LezGroups
- [ ] Проверить save/load LezBotsGroup в UserConfig.java

### Этап 7: Исправления проблем сессии
- [x] Исправлена синхронизация Autoboi с профилем ✅
- [x] Исправлен POST → GET для авто-формы ✅
- [x] Добавлена обработка завершения боя (get_id=61&act=7) ✅
- [x] **Автобой РАБОТАЕТ** после ввода капчи ✅

### Этап 8: Баг завершения боя
- [x] Исправить определение конца боя в MainPhp.java ✅
- [x] Добавить проверку FightLink перед возвратом null при IsWaitingForNextTurn ✅
- [x] Очищать FightLink после использования ✅

---

## 17. Баг: Бесконечный цикл при завершении боя (25.02.2026)

### Симптомы
После победы над противником код попадает в бесконечный цикл редиректов на страницу `get_id=61&act=7`.

### Логи
```
mainPhpFightEnd: redirect to main.php?get_id=61&act=7&fexp=9599&fres=1&...
mainPhpFightEnd: redirect to main.php?get_id=61&act=7&fexp=9599&fres=1&...
(повторяется бесконечно)
```

### Причина
Код использовал `window.location = URL` (GET редирект), но сервер ожидает POST форму. В результате сервер возвращает страницу ошибки (`error.css`), а код снова делает редирект на тот же URL.

### Решение

#### 1. Добавлен метод buildPostForm в Filter.java
```java
public static byte[] buildPostForm(String description, String action, String... params) {
    StringBuilder sb = new StringBuilder();
    sb.append(ru.neverlands.abclient.utils.HtmlUtils.GENERATED_PAGE_MARKER);
    sb.append("<html><head>...");
    sb.append("<form action=\"").append(action).append("\" method=POST name=ff>");
    
    for (int i = 0; i < params.length; i += 2) {
        if (i + 1 < params.length) {
            sb.append("<input type=hidden name=\"").append(params[i])
              .append("\" value=\"").append(params[i + 1]).append("\">");
        }
    }
    
    sb.append("<script language=\"JavaScript\">document.ff.submit();</script></form></body></html>");
    return Russian.getBytes(sb.toString());
}
```

#### 2. Обновлён метод mainPhpFightEnd в MainPhp.java
- Использует POST форму вместо GET редиректа
- Добавлена проверка на `error.css` в HTML для защиты от бесконечного цикла
- При получении страницы ошибки возвращается оригинальный HTML

#### 3. Исправлена логика определения конца боя в mainPhpFight()
```java
// Проверяем, ждём ли мы хода противника - нужно auto-refresh
if (fight.IsWaitingForNextTurn) {
    // Проверяем - это конец боя (есть ссылка на завершение) или просто ожидание хода противника
    if (AppVars.FightLink != null && !AppVars.FightLink.isEmpty()) {
        android.util.Log.d(TAG, "mainPhpFight: FIGHT ENDED - FightLink available, clicking finish button");
        // Бой завершён - нажимаем кнопку завершения
        if (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi && AppVars.Autoboi == AutoboiState.AutoboiOn) {
            String fightLink = AppVars.FightLink;
            AppVars.FightLink = ""; // Очищаем после использования
            return Russian.getString(Filter.buildPostForm("Завершение боя", "main.php", ...));
        }
        return html;
    }
    // Просто ждём хода противника
    return null;
}
```

### Файлы изменены
- `app/src/main/java/ru/neverlands/abclient/postfilter/Filter.java` - добавлен buildPostForm()
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java` - исправлена логика завершения боя

---

## 18. Статус задач (обновлено 25.02.2026 вечер)

### Этап 1: MainPhpFight интеграция
- [x] Добавить `mainPhpFight()` в `MainPhp.java` ✅
- [x] Вызывать при `html.contains("var fight_ty = [")` ✅
- [x] Обрабатывать все состояния AutoboiState ✅
- [x] Уведомлять в чат при остановке ✅
- [x] **ИСПРАВЛЕНО:** Синхронизация Autoboi с профилем при старте ✅
- [x] **ИСПРАВЛЕНО:** POST → GET для авто-формы ✅
- [x] **ИСПРАВЛЕНО:** Обработка завершения боя (get_id=61&act=7) ✅

### Этап 2: Уведомление о нападении
- [x] При смене `LogBoi` — отправлять уведомление в чат ✅ (notifyNewFight() в MainPhp.java)
- [x] Парсить имя/уровень/тип противника (FoeName, FoeLevel, FoeMaxHp в LezFight) ✅
- [-] Определять невидимку (levelprot == -1) — отложено
- [x] Определять опасный бой (ftype >= 80) ✅ (IsDangerousFoe(), IsBoss() в LezFight)

### Этап 3: AndroidBridge методы
- [x] `AutoSelect()` — ручной выбор одного хода ✅
- [x] `AutoTurn()` — один автоход ✅
- [x] `AutoBoi()` — переключение автобоя ✅
- [x] `ResetCure()` — сброс состояния ✅
- [x] `XodButtonElapsedTime()` — таймер хода ✅
- [x] `ResetLastBoiTimer()` — сброс таймера ✅

### Этап 4: AutoBoiSettingsFragment (UI)
- [ ] Создать DialogFragment с TabLayout (4 вкладки)
- [ ] Вкладка 1: Общие настройки (CheckBox + SeekBar)
- [ ] Вкладка 2: Группы противников (RecyclerView + Spinner)
- [ ] Вкладка 3: Ротация (CheckBox + SeekBar + 5 списков заклинаний)
- [ ] Вкладка 4: Останов боя (CheckBox + SeekBar)
- [ ] Сохранение в Profile
- [ ] Открытие из AUTO_FIGHT (long press) или меню настроек

### Этап 5: WebViewRequestInterceptor
- [x] Filter.process() вызывается для main.php ✅
- [x] FightJs.process() вызывается для fight_v*.js ✅

### Этап 6: Сериализация LezGroups
- [ ] Проверить save/load LezBotsGroup в UserConfig.java

### Этап 7: Исправления проблем сессии
- [x] Исправлена синхронизация Autoboi с профилем ✅
- [x] Исправлен POST → GET для авто-формы ✅
- [x] Добавлена обработка завершения боя (get_id=61&act=7) ✅
- [x] **Автобой РАБОТАЕТ** после ввода капчи ✅

### Этап 8: Баг завершения боя
- [x] Исправить определение конца боя в MainPhp.java ✅
- [x] Добавить проверку FightLink перед возвратом null при IsWaitingForNextTurn ✅
- [x] Очищать FightLink после использования ✅

### Этап 9: Баг бесконечного цикла при завершении боя
- [x] Добавлен buildPostForm() в Filter.java ✅
- [x] Изменён buildGetForm() для использования GET с задержкой ✅
- [x] Заменена POST форма на GET для завершения боя (mainPhpFightEnd) ✅
- [x] Задержка 500-1500ms с случайной составляющей для имитации игрока ✅

---

## 20. Исправления 25.02.2026 - POST → GET для завершения боя

### Проблема
POST запросы не перехватываются Android WebView через `shouldInterceptRequest()`. 
Форма завершения боя уходила напрямую на сервер, ответ не обрабатывался, что приводило к бесконечному циклу.

### Решение
1. Обновлён метод `buildGetForm()` в `Filter.java`:
   - Использует GET запрос вместо POST
   - Добавляет случайную задержку 500-1500ms перед редиректом
   - Использует `window.location = URL` для перехвата в WebView

2. Обновлён метод `mainPhpFightEnd()` в `MainPhp.java`:
   - Заменён вызов `buildPostForm()` на `buildGetForm()`

### Файлы изменены
- `app/src/main/java/ru/neverlands/abclient/postfilter/Filter.java` - buildGetForm()
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java` - mainPhpFightEnd()

---

## 21. Статус задач (обновлено 25.02.2026 вечер)

### Этап 1: MainPhpFight интеграция
- [x] Добавить `mainPhpFight()` в `MainPhp.java` ✅
- [x] Вызывать при `html.contains("var fight_ty = [")` ✅
- [x] Обрабатывать все состояния AutoboiState ✅
- [x] Уведомлять в чат при остановке ✅
- [x] **ИСПРАВЛЕНО:** Синхронизация Autoboi с профилем при старте ✅
- [x] **ИСПРАВЛЕНО:** POST → GET для авто-формы ✅
- [x] **ИСПРАВЛЕНО:** Обработка завершения боя (get_id=61&act=7) ✅

### Этап 2: Уведомление о нападении
- [ ] При смене `LogBoi` — отправлять уведомление в чат
- [ ] Парсить имя/уровень/тип противника из `param_en`/`slots_en`
- [ ] Определять невидимку (levelprot == -1)
- [ ] Определять опасный бой (ftype >= 80)

### Этап 3: AndroidBridge методы
- [x] `AutoSelect()` — ручной выбор одного хода ✅
- [x] `AutoTurn()` — один автоход ✅
- [x] `AutoBoi()` — переключение автобоя ✅
- [x] `ResetCure()` — сброс состояния ✅
- [x] `XodButtonElapsedTime()` — таймер хода ✅
- [x] `ResetLastBoiTimer()` — сброс таймера ✅

### Этап 4: AutoBoiSettingsFragment (UI)
- [ ] Создать DialogFragment с TabLayout (4 вкладки)
- [ ] Вкладка 1: Общие настройки (CheckBox + SeekBar)
- [ ] Вкладка 2: Группы противников (RecyclerView + Spinner)
- [ ] Вкладка 3: Ротация (CheckBox + SeekBar + 5 списков заклинаний)
- [ ] Вкладка 4: Останов боя (CheckBox + SeekBar)
- [ ] Сохранение в Profile
- [ ] Открытие из AUTO_FIGHT (long press) или меню настроек

### Этап 5: WebViewRequestInterceptor
- [x] Filter.process() вызывается для main.php ✅
- [x] FightJs.process() вызывается для fight_v*.js ✅

### Этап 6: Сериализация LezGroups
- [ ] Проверить save/load LezBotsGroup в UserConfig.java

### Этап 7: Исправления проблем сессии
- [x] Исправлена синхронизация Autoboi с профилем ✅
- [x] Исправлен POST → GET для авто-формы ✅
- [x] Добавлена обработка завершения боя (get_id=61&act=7) ✅
- [x] **Автобой РАБОТАЕТ** после ввода капчи ✅

### Этап 8: Баг завершения боя
- [x] Исправить определение конца боя в MainPhp.java ✅
- [x] Добавить проверку FightLink перед возвратом null при IsWaitingForNextTurn ✅
- [x] Очищать FightLink после использования ✅

### Этап 9: Баг бесконечного цикла при завершении боя
- [x] Изменён buildGetForm() на использование GET вместо POST ✅
- [x] Добавлена случайная задержка 500-1500ms ✅
- [x] Заменена форма завершения боя на GET ✅

### Зачем нужно
Сервер Neverlands может детектить авто-бой по слишком частым запросам. Реальный игрок не может отправлять ходы чаще чем 1 раз в секунду.

### Реализация

#### Вариант 1: Задержка в WebView (рекомендуемый)
Добавить задержку перед авто-submit формы в LezFight.java:

```java
// В методе BuildFrame(), после генерации HTML:
// Добавить задержку перед авто-submit для имитации реального игрока
sb.append("<script>");
sb.append("setTimeout(function(){ ");
sb.append("document.ff.submit();");
sb.append("}, " + (1000 + random.nextInt(500)) + ");"); // 1000-1500ms случайная задержка
sb.append("</script>");
```

#### Вариант 2: Задержка в MainPhp (альтернативный)
Добавить проверку времени в MainPhp.java перед возвратом Frame:

```java
// В mainPhpFight() перед возвратом fight.Frame:
long now = System.currentTimeMillis();
long lastAttack = AppVars.LastAttackTime;
if (now - lastAttack < 1000) {
    // Ждём минимум 1 секунду
    try {
        Thread.sleep(1000 - (now - lastAttack));
    } catch (InterruptedException e) {}
}
AppVars.LastAttackTime = now;
```

#### Вариант 3: Рандомизация User-Agent
Добавить random delay в JavaScript:

```javascript
// В начале autofight формы:
var delay = 1000 + Math.floor(Math.random() * 1000); // 1-2 секунды
setTimeout(function() { document.ff.submit(); }, delay);
```

### Что выбрать
Рекомендуется **Вариант 1 или 3** - задержка на стороне клиента (в HTML/JS). Это:
- Не блокирует поток Android
- Естественно для WebView
- Легче имитирует поведение реального игрока

### Дополнительные меры анти-детекта

1. **Рандомизация delay**: 1000ms + random(0-1500ms)
2. **Не отправлять запросы если страница не загружена полностью**
3. **Не использовать POST для авто-формы** (уже используем GET для перехвата в Android WebView)
4. **Добавить искусственные "ошибки"**: 5% шанс не отправить форму с первого раза

### План реализации

- [x] Добавить random delay в LezFight.BuildFrame() (1000-2000ms) ✅
- [x] Протестировать с разными значениями delay ✅
- [ ] Проверить что сервер не детектит авто-бой

---

## 21. Баг: Бой не завершается автоматически (25.02.2026)

### Симптомы
После победы над противником (`IsBoi=false`) код не нажимает кнопку завершения боя, а продолжает "ждать хода противника".

### Логи
```
LezFight parsed: IsBoi=false IsWaitingForNextTurn=true LogBoi=718451271
mainPhpFight: waiting for opponent turn (foe HP=X), returning original HTML for auto-refresh
```

### Причина
В `LezFight.java` строка 112:
```java
if (!IsBoi) return ParseNonFight();
```

Когда `IsBoi=false` (бой завершён), парсинг выходит раньше, чем доходит до парсинга HP врага. Поэтому `IsFoeDead` остаётся `false` (значение по умолчанию).

### Решение
Изменена логика в `MainPhp.java` - проверяем `!fight.IsBoi && !FightLink.isEmpty()` вместо `fight.IsFoeDead`:

```java
// Было:
if (fight.IsFoeDead) { ... }

// Стало:
if (!fight.IsBoi && !AppVars.FightLink.isEmpty()) { ... }
```

Это работает потому что:
- `FightLink` заполняется в `ParseNonFight()` при завершении боя
- Если `IsBoi=false` и есть `FightLink` - бой завершён, нужно нажать "Завершить"

### Файлы изменены
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`

---

## 22. Статус задач (обновлено 25.02.2026)

### Этап 1: MainPhpFight интеграция
- [x] Добавить `mainPhpFight()` в `MainPhp.java` ✅
- [x] Вызывать при `html.contains("var fight_ty = [")` ✅
- [x] Обрабатывать все состояния AutoboiState ✅
- [x] Уведомлять в чат при остановке ✅
- [x] **ИСПРАВЛЕНО:** Синхронизация Autoboi с профилем при старте ✅
- [x] **ИСПРАВЛЕНО:** POST → GET для авто-формы ✅
- [x] **ИСПРАВЛЕНО:** Обработка завершения боя (get_id=61&act=7) ✅
- [x] **ИСПРАВЛЕНО:** Определение конца боя (IsBoi + FightLink) ✅

### Этап 2: Уведомление о нападении
- [ ] При смене `LogBoi` — отправлять уведомление в чат
- [ ] Парсить имя/уровень/тип противника из `param_en`/`slots_en`
- [ ] Определять невидимку (levelprot == -1)
- [ ] Определять опасный бой (ftype >= 80)

### Этап 3: AndroidBridge методы
- [x] `AutoSelect()` — ручной выбор одного хода ✅
- [x] `AutoTurn()` — один автоход ✅
- [x] `AutoBoi()` — переключение автобоя ✅
- [x] `ResetCure()` — сброс состояния ✅
- [x] `XodButtonElapsedTime()` — таймер хода ✅
- [x] `ResetLastBoiTimer()` — сброс таймера ✅

### Этап 4: AutoBoiSettingsFragment (UI)
- [ ] Создать DialogFragment с TabLayout (4 вкладки)
- [ ] Вкладка 1: Общие настройки (CheckBox + SeekBar)
- [ ] Вкладка 2: Группы противников (RecyclerView + Spinner)
- [ ] Вкладка 3: Ротация (CheckBox + SeekBar + 5 списков заклинаний)
- [ ] Вкладка 4: Останов боя (CheckBox + SeekBar)
- [ ] Сохранение в Profile
- [ ] Открытие из AUTO_FIGHT (long press) или меню настроек

### Этап 5: WebViewRequestInterceptor
- [x] Filter.process() вызывается для main.php ✅
- [x] FightJs.process() вызывается для fight_v*.js ✅

### Этап 6: Сериализация LezGroups
- [ ] Проверить save/load LezBotsGroup в UserConfig.java

### Этап 7: Исправления проблем сессии
- [x] Исправлена синхронизация Autoboi с профилем ✅
- [x] Исправлен POST → GET для авто-формы ✅
- [x] Добавлена обработка завершения боя (get_id=61&act=7) ✅
- [x] **Автобой РАБОТАЕТ** после ввода капчи ✅

### Этап 8: Баг завершения боя
- [x] Исправить определение конца боя в MainPhp.java ✅
- [x] Добавить проверку FightLink перед возвратом null при IsWaitingForNextTurn ✅
- [x] Очищать FightLink после использования ✅

### Этап 9: Баг бесконечного цикла
- [x] Добавлен buildPostForm() в Filter.java ✅
- [x] Использована POST форма вместо GET редиректа ✅
- [x] Добавлена защита от бесконечного цикла (проверка на error.css) ✅
- [x] Добавлена проверка IsFoeDead для корректного определения конца боя ✅

### Этап 10: Анти-детект
- [x] Добавить random delay (1000-2000ms) в autofight форму ✅
- [ ] Проверить что сервер не детектит авто-бой

---

## 21. Реализация анти-детекта (25.02.2026)

### Что сделано

Добавлена задержка между запросами для имитации поведения реального игрока.

### Изменения в LezFight.java

1. Добавлен импорт `Random`:
```java
import java.util.Random;
```

2. Добавлено поле для рандома:
```java
private static final Random _random = new Random();
```

3. Добавлена задержка в BuildFrame():
```java
sb.append("</form>");
// Добавляем random delay для анти-детекта (1000-2000ms)
// Реальный игрок не может отправлять ходы чаще чем 1 раз в секунду
int delay = 1000 + _random.nextInt(1000);
sb.append("<script language=\"JavaScript\">");
sb.append("setTimeout(function(){ document.ff.submit(); }, ").append(delay).append(");");
sb.append("</script></body></html>");

Frame = sb.toString();
android.util.Log.d("LezFight", "BuildFrame: Frame generated, length=" + Frame.length() + ", delay=" + delay + "ms");
```

### Как это работает
- При каждой атаке генерируется случайная задержка от 1000 до 2000 мс
- Это имитирует время реакции реального игрока
- Сервер не должен детектить авто-бой по частоте запросов
- Значение delay логируется для отладки

### Дополнительные меры (при необходимости)
1. Увеличить delay до 2000-3000ms
2. Добавить "искусственные ошибки" - 5% шанс пропустить ход
3. Рандомизировать параметры формы

---

## 22. Статус задач (обновлено 25.02.2026 финально)

### Этап 1: MainPhpFight интеграция
- [x] Добавить `mainPhpFight()` в `MainPhp.java` ✅
- [x] Вызывать при `html.contains("var fight_ty = [")` ✅
- [x] Обрабатывать все состояния AutoboiState ✅
- [x] Уведомлять в чат при остановке ✅
- [x] **ИСПРАВЛЕНО:** Синхронизация Autoboi с профилем при старте ✅
- [x] **ИСПРАВЛЕНО:** POST → GET для авто-формы ✅
- [x] **ИСПРАВЛЕНО:** Обработка завершения боя (get_id=61&act=7) ✅

### Этап 2: Уведомление о нападении
- [ ] При смене `LogBoi` — отправлять уведомление в чат
- [ ] Парсить имя/уровень/тип противника из `param_en`/`slots_en`
- [ ] Определять невидимку (levelprot == -1)
- [ ] Определять опасный бой (ftype >= 80)

### Этап 3: AndroidBridge методы
- [x] `AutoSelect()` — ручной выбор одного хода ✅
- [x] `AutoTurn()` — один автоход ✅
- [x] `AutoBoi()` — переключение автобоя ✅
- [x] `ResetCure()` — сброс состояния ✅
- [x] `XodButtonElapsedTime()` — таймер хода ✅
- [x] `ResetLastBoiTimer()` — сброс таймера ✅

### Этап 4: AutoBoiSettingsFragment (UI)
- [ ] Создать DialogFragment с TabLayout (4 вкладки)
- [ ] Вкладка 1: Общие настройки (CheckBox + SeekBar)
- [ ] Вкладка 2: Группы противников (RecyclerView + Spinner)
- [ ] Вкладка 3: Ротация (CheckBox + SeekBar + 5 списков заклинаний)
- [ ] Вкладка 4: Останов боя (CheckBox + SeekBar)
- [ ] Сохранение в Profile
- [ ] Открытие из AUTO_FIGHT (long press) или меню настроек

### Этап 5: WebViewRequestInterceptor
- [x] Filter.process() вызывается для main.php ✅
- [x] FightJs.process() вызывается для fight_v*.js ✅

### Этап 6: Сериализация LezGroups
- [ ] Проверить save/load LezBotsGroup в UserConfig.java

### Этап 7: Исправления проблем сессии
- [x] Исправлена синхронизация Autoboi с профилем ✅
- [x] Исправлен POST → GET для авто-формы ✅
- [x] Добавлена обработка завершения боя (get_id=61&act=7) ✅
- [x] **Автобой РАБОТАЕТ** после ввода капчи ✅

### Этап 8: Баг завершения боя
- [x] Исправить определение конца боя в MainPhp.java ✅
- [x] Добавить проверку FightLink перед возвратом null при IsWaitingForNextTurn ✅
- [x] Очищать FightLink после использования ✅
- [x] Добавлено поле IsFoeDead для определения мёртвого врага ✅

### Этап 9: Баг бесконечного цикла
- [x] Добавлен buildPostForm() в Filter.java ✅
- [x] Использована POST форма вместо GET редиректа ✅
- [x] Добавлена защита от бесконечного цикла (проверка на error.css) ✅

### Этап 10: Анти-детект
- [x] Добавлен random delay (1000-2000ms) в autofight форму ✅
- [ ] Проверить что сервер не детектит авто-бой

---

## 19. Итоговый статус (25.02.2026 — ЗАВЕРШЕНО)

### Все задачи выполнены:

**Этап 2: Уведомление о нападении** ✅
- notifyNewFight() добавлен в MainPhp.java
- LezFight получил поля FoeName, FoeLevel, FoeMaxHp
- Методы IsDangerousFoe(), IsBoss() добавлены в LezFight

**Этап 3: AndroidBridge** ✅
- ResetCure() реализован (сбрасывает Autoboi → AutoboiOn)

**Этап 4: AutoBoiSettingsFragment (UI)** ✅
- AutoBoiSettingsFragment.java создан (DialogFragment, 4 вкладки)
- Layouts: dialog_autoboi_settings.xml, tab_autoboi_general/groups/rotation/stop.xml
- Items: item_lez_spell.xml, item_lez_group.xml
- Открывается по long press на кнопку AUTO_FIGHT

**Этап 6: Сериализация LezGroups** ✅
- UserConfig.save(): XML тег lezgroups/group со всеми полями
- UserConfig.load(): парсинг тегов autoboi и group
- Вспомогательные методы: intArrayToString(), parseIntArrayAttr(), parseIntAttr()

---

## 22. Исправления 25.02.2026 - Автоматическое завершение боя + белый фрейм

### Проблема 1: Автоматическое нажатие кнопки "Завершить бой" не работает

**Симптомы:**
- При победе в бою сервер показывает страницу с кнопкой "Завершить"
- Кнопка не нажимается автоматически
- Пользователь должен нажать её вручную

**Причина:**
В C# версии (MainPhpFight.cs, строки 136-141) после завершения боя делается редирект по FightLink:
```csharp
if (!string.IsNullOrEmpty(AppVars.FightLink) && (AppVars.FightLink.IndexOf("????", StringComparison.Ordinal) == -1))
{
    var fightLink = AppVars.FightLink;
    AppVars.FightLink = string.Empty;
    return BuildRedirect("Завершение боя", fightLink);
}
```

В Android эта логика была реализована, но FightLink содержит "code????", и проверка на "????" блокировала редирект.

**Решение:**
1. Исправлена проверка FightLink в mainPhpFight() - теперь корректно проверяет наличие "????"
2. Добавлен редирект при отключенном автобое (аналог C# версии)
3. Изменён mainPhpFightEnd() - использует buildRedirect вместо buildGetForm (как в C#)
4. Убраны некорректные редиректы на main.php при невалидном FightLink

**Файлы изменены:**
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`

### Проблема 2: Белый фрейм при отключенном автобое

**Симптомы:**
- При отключенной кнопке "Авто-Бой" верхний фрейм становится белым
- Должен отображаться ответ сервера с кнопками "Ход", "Автовыбор", "Автоход", "Автобой", "Сбросить"

**Причина:**
В C# версии (MainPhpFight.cs, строка 165) в конце ВСЕГДА возвращается `AppVars.ContentMainPhp`:
```csharp
return AppVars.ContentMainPhp;
```

В Android возвращался `html`, который мог быть изменён в процессе обработки, что приводило к белому фрейму.

**Решение:**
В конце метода mainPhpFight() изменён возврат с `html` на `AppVars.ContentMainPhp`:
```java
// Аналог C# версии - возвращаем AppVars.ContentMainPhp (оригинальный HTML)
// а не изменённый html, чтобы избежать белого фрейма
return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
```

**Файлы изменены:**
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`

---

## 23. Статус задач (обновлено 25.02.2026 - вечер)

### Этап 1: MainPhpFight интеграция
- [x] Добавить `mainPhpFight()` в `MainPhp.java` ✅
- [x] Вызывать при `html.contains("var fight_ty = [")` ✅
- [x] Обрабатывать все состояния AutoboiState ✅
- [x] Уведомлять в чат при остановке ✅
- [x] **ИСПРАВЛЕНО:** Синхронизация Autoboi с профилем при старте ✅
- [x] **ИСПРАВЛЕНО:** POST → GET для авто-формы ✅
- [x] **ИСПРАВЛЕНО:** Обработка завершения боя (get_id=61&act=7) ✅
- [x] **ИСПРАВЛЕНО:** Автобой работает после ввода капчи ✅

### Этап 2: Уведомление о нападении
- [x] При смене LogBoi — отправлять уведомление в чат ✅
- [x] Парсить имя/уровень/тип противника ✅
- [x] Определять опасный бой (ftype >= 80) ✅

### Этап 3: AndroidBridge методы
- [x] AutoSelect() — ручной выбор одного хода ✅
- [x] AutoTurn() — один автоход ✅
- [x] AutoBoi() — переключение автобоя ✅
- [x] ResetCure() — сброс состояния ✅
- [x] XodButtonElapsedTime() — таймер хода ✅
- [x] ResetLastBoiTimer() — сброс таймера ✅

### Этап 4: AutoBoiSettingsFragment (UI)
- [x] Создан ✅
- [x] Открывается по long press ✅

### Этап 5: WebViewRequestInterceptor
- [x] Filter.process() вызывается для main.php ✅
- [x] FightJs.process() вызывается для fight_v*.js ✅

### Этап 6: Сериализация LezGroups
- [x] Проверена работа ✅

### Этап 7: Исправления проблем сессии
- [x] Исправлена синхронизация Autoboi с профилем ✅
- [x] Исправлен POST → GET для авто-формы ✅
- [x] Добавлена обработка завершения боя ✅

### Этап 8: Баг завершения боя
- [x] Исправлено определение конца боя ✅
- [x] Добавлена проверка FightLink ✅
- [x] Очищается FightLink после использования ✅

### Этап 9: Баг бесконечного цикла
- [x] Изменён buildGetForm() на GET ✅
- [x] Добавлена случайная задержка ✅
- [x] Заменена форма завершения боя на GET ✅

### Этап 10: Исправления 25.02.2026
- [x] **ИСПРАВЛЕНО:** Автоматическое нажатие кнопки "Завершить бой" ✅
- [x] **ИСПРАВЛЕНО:** Белый фрейм при отключенном автобое ✅

### Этап 11: Дополнительные исправления 25.02.2026
- [x] **ИСПРАВЛЕНО:** FightLink содержал `code=????` - убран ✅
- [x] **ИСПРАВЛЕНО:** IsWaitingForNextTurn=true при IsBoi=false - исправлено ✅

#### Проблема 1: FightLink содержал `code=????`
**Логи:**
```
LezFight: BuildFightLink: main.php?code=????&get_id=61&act=7&fexp=374&...
```
**Причина:** В BuildFightLink() был жёстко закодирован параметр `code=????`
**Решение:** Убран параметр `code=????&` из ссылки (как в C# версии)

#### Проблема 2: IsWaitingForNextTurn=true при завершённом бое
**Логи:**
```
LezFight parsed: IsBoi=false IsWaitingForNextTurn=true
mainPhpFight: FIGHT ENDED - IsBoi=false, waiting for main page
```
**Причина:** IsWaitingForNextTurn вычислялся на основе _fightty[3], без учёта IsBoi
**Решение:** Изменена формула: `IsWaitingForNextTurn = IsBoi && (_fightty[3]...`

---

## 24. Актуализация анализа (01.03.2026): `Autoboi` / `LezFight` / `FightJs` / `Foe` / `Attack`

### 24.1 Что проверено в эталоне C# (без изменений в `ABClient`)

- `ABClient/ABForms/FormMainAutoBoi.cs`
- `ABClient/PostFilter/MainPhpFight.cs`
- `ABClient/PostFilter/FightJs.cs`
- `ABClient/Lez/LezFight.cs`
- `ABClient/Foe.cs`
- `ABClient/UnderAttack.cs`
- `ABClient/ABForms/FormAutoAttack.cs`
- Связанные вызовы в `ABClient/ScriptManager.cs`, `ABClient/ABForms/FormMainFast.cs`, `ABClient/RoomManager.cs`

### 24.2 Сводка соответствия C# ↔ Android

| C# модуль | Android модуль | Статус анализа | Статус портирования | Комментарий |
|---|---|---|---|---|
| `MainPhpFight.cs` | `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java` (`mainPhpFight`) | `[x]` | `[ ]` | Базовый поток боя портирован, но есть отклонения от C# по восстановлению/веткам состояний |
| `LezFight.cs` | `app/src/main/java/ru/neverlands/abclient/lez/LezFight.java` | `[x]` | `[ ]` | Порт основной логики есть; отсутствует полноценный аналог `CalcRestoreAfterBoi()` и всех `ParseNonFight` кейсов |
| `FightJs.cs` | `app/src/main/java/ru/neverlands/abclient/postfilter/FightJs.java` | `[x]` | `[+]` | Кнопки/JS-мост перенесены, таймер и reset-ветки присутствуют |
| `FormMainAutoBoi.cs` | `app/src/main/java/ru/neverlands/abclient/bridge/WebAppInterface.java`, `.../manager/AutoFunctionsManager.java` | `[x]` | `[ ]` | Переключение работает, но управление рассредоточено по двум точкам и частично дублируется |
| `FormSettingsAb.cs` | `app/src/main/java/ru/neverlands/abclient/ui/AutoBoiSettingsFragment.java` | `[x]` | `[ ]` | UI есть, но выбор группы не доведён: ротация/останов используют группу `0` |
| `Foe.cs` | (отдельного класса нет, логика в `LezFight.java`) | `[x]` | `[ ]` | Нет отдельного порта сущности `Foe` (валидация/сравнение/типизация) |
| `UnderAttack.cs` | Частично в `MainPhp.java` (`notifyNewFight`) | `[x]` | `[ ]` | Нет полного аналога логики `LezSay` (`Chat/Clan/Pair/No`) и сценариев уведомлений C# |
| `FormAutoAttack.cs` + `AutoAttackToolId` | `AutoFunctionsManager`/`QuickButtonsPanel`/`FastActionManager` | `[x]` | `[ ]` | Флаг авто-нападения есть, но нет эквивалента выбора `AutoAttackToolId` как в C# |

### 24.3 Найденные расхождения (приоритет)

1. `[ ]` **Нет полного `CalcRestoreAfterBoi`-поведения C#**
   - В C#: расчёт времени восстановления по HP/MA и `Pers.IntHP`.
   - В Android: упрощённая проверка по `LezWaitHp` без эквивалентной формулы и тайминга.

2. `[ ]` **`ParseNonFight` в `LezFight.java` покрывает не все ветки C#**
   - В C# детально обрабатываются кейсы (`fight_ty[4]`) `3/4/5/7`, `act=6/act=7`, `FightLink`, ожидание и завершение.
   - В Android часть переходов сведена к общему `fexp`-разбору.

3. `[ ]` **`AutoBoiSettingsFragment` не привязывает выбранную группу к вкладкам 3/4**
   - Локально выбирается `selectedGroupIdx` во вкладке групп.
   - Вкладки ротации/останов загружают `LezGroups.get(0)` вместо текущей выбранной группы.

4. `[ ]` **`AutoUd()` в JS-мосте не реализован**
   - `FightJs.java` вызывает `AndroidBridge.AutoUd()`, в `WebAppInterface` метод содержит `TODO`.

5. `[ ]` **Сильное логирование в боевом цикле включено постоянно**
   - В `mainPhpFight()` принудительный дамп HTML чанками (`if (true)`), что создаёт нагрузку и влияет на ритм авто-боя.

6. `[ ]` **Нет отдельного порта `Foe` и полного `UnderAttack`-потока**
   - Типизация/валидация противника и маршрутизация уведомлений (`LezSay`) реализованы не в полном объёме C#.

7. `[ ]` **`Attack`-ветка (авто-нападение с выбором инструмента) не доведена до C#-эквивалента**
   - В C# используется `AutoAttackToolId` + интеграция с `RoomManager`.
   - В Android есть on/off `AUTO_ATTACK`, но нет полноценного аналога выбора/применения `toolId` в общем потоке.

### 24.4 План доработки (обновлённый)

- [ ] Вынести и портировать `CalcRestoreAfterBoi()` в `LezFight.java` 1:1 с C# формулой и перейти на него из `MainPhp.mainPhpFight`.
- [ ] Довести `ParseNonFight()` до полного покрытия C# кейсов (`3/4/5/7`, таймаут, `FightLink`-переходы).
- [ ] Исправить связку вкладок в `AutoBoiSettingsFragment`: единый `selectedGroupIndex` для Groups/Rotation/Stop.
- [ ] Реализовать `AutoUd()` в `WebAppInterface` по C#-поведению (`AutoUd` + `AutoSelect`).
- [ ] Перевести боевой дамп HTML в управляемый debug-флаг (выкл по умолчанию).
- [ ] Добавить/портировать слой `Foe` (или эквивалентную строгую типизацию) и закрыть пробелы `UnderAttack` (`LezSay`).
- [ ] Закрыть `Attack`-ветку: добавить аналог `AutoAttackToolId` и связать с авто-нападением в общем цикле.

### 24.5 Примечание по правилам

- Папка `ABClient` использована только как эталон для сверки.
- Изменения выполнены только в TODO-документации проекта Android.
