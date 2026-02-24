# Инструкция по QuickButtons (Быстрые кнопки)

## Назначение файла

Файл реализует систему быстрых кнопок на основной вкладке Android-приложения. Является аналогом системы горячих клавиш/кнопок из ПК-версии ABClient. Позволяет пользователю назначать функции на 20 кнопок (10 сверху + 10 снизу основной вкладки).

## Назначение кнопок

| Кнопка | Описание | Назначение |
|--------|----------|------------|
| 0-9 (верхний ряд) | Быстрые кнопки | Выполнение действий при нажатии |
| 10-19 (нижний ряд) | Быстрые кнопки | Выполнение действий при нажатии |

## Структура класса

### Модель данных

#### QuickActionType (enum)
Определяет типы действий для кнопок.

**Поля:**
- `displayName` (String) - отображаемое название
- `actionKey` (String) - ключ действия
- `quickActionKey` (String) - ключ для быстрых действий

**Значения:**
```java
// Основные действия
AUTO_FIGHT("Авто-Бой", "auto_fight"),
QUICK_ACTIONS("Быстрые действия ▼", "quick_actions"),
AUTO_FISH("Авторыбалка", "auto_fish"),
AUTO_BAIT("Автоприманка", "auto_bait"),
LEZ_FIGHT("Автоохота", "lez_fight"),
AUTO_ATTACK("Авто-Нападение", "auto_attack"),
AUTO_INVISIBLE("Авто-Невид", "auto_invisible"),
LOCATION_TRACKING("Слежение за локацией", "location_tracking"),
AUTO_DETECT("Авто-Обнаружение", "auto_detect"),
AUTO_SUMMON("Авто-Тотем", "auto_summon"),
AUTO_CURE("Автолечение", "auto_cure"),
AUTO_DRINK("Авто-Питье", "auto_drink"),
AUTO_MOVING("Авто-Движение", "auto_moving"),
AUTO_CUT("Авто-Травник", "auto_cut"),
AUTO_REFRESH("Авто-Обновление", "auto_refresh"),

// Дополнительные действия
OPEN_CONTACTS("Открыть контакты", "open_contacts"),
OPEN_PINFO("Открыть PINFO", "open_pinfo"),
OPEN_LOGS("Открыть Логи", "open_logs"),
REFRESH_CONTACTS("Обновить контакты", "refresh_contacts"),

// Быстрые действия на себя
QUICK_SELF_RASS("Рассеять невид", "quick_self_rass", "selfRass"),
QUICK_OPEN_NEVID("Обнаружение", "quick_open_nevid", "openNevid"),
QUICK_TELEPORT("Телепорт", "quick_teleport", "teleport"),
QUICK_ISLAND("Остров (Туротор)", "quick_island", "island"),
QUICK_TOTEM("Тотем", "quick_totem", "totem"),
QUICK_ELIXIR_BLAZ("Эликсир Блаженства", "quick_elixir_blaz", "elixirBlaz"),
QUICK_ELIXIR_CURE("Эликсир Исцеления", "quick_elixir_cure", "elixirCure"),
QUICK_ELIXIR_RESTORE("Эликсир Восстановления", "quick_elixir_restore", "elixirRestore"),

// Пустая кнопка
NONE("Пустая", "none", null)
```

#### QuickButton (class)
Хранит информацию о назначенной функции на кнопке.

**Поля:**
- `position` (int) - позиция кнопки (0-19)
- `actionType` (QuickActionType) - тип действия
- `customName` (String) - кастомное название

**Методы:**
- `getPosition()` - получить позицию
- `setPosition(int)` - установить позицию
- `getActionType()` - получить тип действия
- `setActionType(QuickActionType)` - установить тип действия
- `getCustomName()` - получить кастомное название
- `setCustomName(String)` - установить кастомное название
- `getDisplayName()` - получить отображаемое название
- `isEmpty()` - проверить пустая ли кнопка

### QuickButtonsManager (class)
Менеджер управления кнопками. Использует SharedPreferences для сохранения.

**Основные методы:**
- `getInstance(Context)` - получить экземпляр (singleton)
- `loadButtons()` - загрузить кнопки из SharedPreferences
- `saveButtons()` - сохранить кнопки в SharedPreferences
- `getButtons()` - получить список всех кнопок
- `getButton(int position)` - получить кнопку по позиции
- `assignFunction(int position, QuickActionType)` - назначить функцию
- `removeFunction(int position)` - удалить функцию с кнопки
- `clearAll()` - очистить все кнопки
- `getButtonCount()` - получить количество кнопок

**Константы:**
- `BUTTON_COUNT = 20` - общее количество кнопок
- `PREFS_NAME = "quick_buttons_prefs"` - имя SharedPreferences
- `KEY_BUTTONS = "quick_buttons"` - ключ для хранения кнопок

### QuickButtonsPanel (class)
UI компонент для отображения и управления кнопками.

**Основные методы:**
- `QuickButtonsPanel(Context, View, OnQuickActionListener)` - конструктор
- `initButtons(View)` - инициализировать кнопки
- `loadAndUpdateButtons()` - загрузить и обновить кнопки
- `updateButtonAppearance(int, QuickButton)` - обновить вид кнопки
- `loadIconForAction(ImageButton, QuickActionType)` - загрузить иконку
- `getIconUrlForAction(QuickActionType)` - получить URL иконки
- `getIconForAction(QuickActionType)` - получить ресурс иконки
- `executeAction(int position)` - выполнить действие
- `executeQuickAction(String, String)` - выполнить быстрое действие
- `showButtonOptions(int)` - показать опции кнопки
- `showFunctionSelector(int)` - показать выбор функции
- `showRemoveConfirmation(int)` - показать подтверждение удаления
- `openContacts()` - открыть контакты
- `openLogs()` - открыть логи
- `refreshContacts()` - обновить контакты
- `refresh()` - обновить состояние кнопок

**Интерфейсы:**
- `OnQuickActionListener` - слушатель действий
  - `onQuickAction(QuickActionType)` - при выполнении действия

### FunctionListAdapter (class)
Адаптер для списка функций в диалоге выбора.

**Основные методы:**
- `FunctionListAdapter(Context, OnFunctionSelectedListener)` - конструктор
- `setDialog(AlertDialog)` - установить диалог для закрытия
- `getCount()` - получить количество элементов
- `getView(int, View, ViewGroup)` - получить View элемента

**Интерфейсы:**
- `OnFunctionSelectedListener` - слушатель выбора функции
  - `onFunctionSelected(QuickActionType)` - при выборе функции

## Поведение кнопок

### Короткое нажатие
- Если кнопка пустая → показывает выбор функции
- Если кнопка с функцией → выполняет действие

### Длительное нажатие
- Если кнопка пустая → показывает выбор функции
- Если кнопка с функцией → показывает подтверждение удаления

## Взаимодействие с другими компонентами

| Компонент | Взаимодействие |
|-----------|----------------|
| `MainActivity` | Создает QuickButtonsPanel, передает callback для QUICK_ACTIONS |
| `FastActionManager` | Вызывает методы быстрых действий |
| `ContactsActivity` | Открывает при выборе OPEN_CONTACTS |
| `LogsActivity` | Открывает при выборе OPEN_LOGS |
| `ContactsManager` | Вызывает refreshAllContacts для REFRESH_CONTACTS |
| `QuickActionsBottomSheet` | Открывает при выборе QUICK_ACTIONS |
| `Glide` | Загружает иконки с URL |

## URL иконок

Иконки загружаются с сервера image.neverlands.ru:

| Действие | URL иконки |
|----------|------------|
| QUICK_SELF_RASS | http://image.neverlands.ru/weapon/i_w28_23.gif |
| QUICK_OPEN_NEVID | http://image.neverlands.ru/weapon/i_w28_28.gif |
| QUICK_TELEPORT | http://image.neverlands.ru/weapon/i_w28_22.gif |
| QUICK_ISLAND | http://image.neverlands.ru/weapon/i_w28_22.gif |
| QUICK_TOTEM | http://image.neverlands.ru/signs/totems/9.gif |
| QUICK_ELIXIR_BLAZ | http://image.neverlands.ru/weapon/i_w61_107.gif |
| QUICK_ELIXIR_CURE | http://image.neverlands.ru/weapon/i_w61_104.gif |
| QUICK_ELIXIR_RESTORE | http://image.neverlands.ru/weapon/i_w61_101.gif |

## Layout файлы

### quick_buttons_bar.xml
Layout верхней панели кнопок (10 кнопок).

### quick_buttons_bar_bottom.xml
Layout нижней панели кнопок (10 кнопок).

### content_main.xml
Добавлены include для quick_buttons_bar и quick_buttons_bar_bottom.

### dialog_select_function.xml
Layout диалога выбора функции.

### item_function.xml
Layout элемента списка функций.

## Зависимости

### Android SDK
- `android.content.Context`
- `android.content.Intent`
- `android.content.SharedPreferences`
- `android.util.Log`
- `android.view.View`
- `android.view.LayoutInflater`
- `android.widget.ImageButton`
- `android.widget.Toast`
- `android.widget.ListView`
- `android.widget.BaseAdapter`
- `android.widget.AlertDialog`
- `com.bumptech.glide.Glide`

### Библиотеки проекта
- `com.google.gson.Gson` - сериализация/десериализация JSON
- `com.bumptech.glide.Glide` - загрузка изображений

### Внутренние классы проекта
- `ru.neverlands.abclient.R` - ресурсы приложения
- `ru.neverlands.abclient.manager.QuickButtonsManager` - менеджер кнопок
- `ru.neverlands.abclient.manager.FastActionManager` - быстрые действия
- `ru.neverlands.abclient.manager.ContactsManager` - менеджер контактов
- `ru.neverlands.abclient.model.QuickActionType` - типы действий
- `ru.neverlands.abclient.model.QuickButton` - модель кнопки
- `ru.neverlands.abclient.adapter.FunctionListAdapter` - адаптер списка
- `ru.neverlands.abclient.ContactsActivity` - активность контактов
- `ru.neverlands.abclient.LogsActivity` - активность логов
- `ru.neverlands.abclient.ui.QuickActionsBottomSheet` - панель быстрых действий

## Особенности реализации

### Сохранение состояния
Кнопки сохраняются в SharedPreferences в формате JSON:
```json
[
  {"position": 0, "actionType": "QUICK_TELEPORT"},
  {"position": 1, "actionType": "NONE"},
  ...
]
```

### Загрузка иконок
Иконки загружаются асинхронно через Glide с URL сервера image.neverlands.ru.

### Выполнение действий
При нажатии на кнопку вызывается соответствующий метод FastActionManager:
- `fastAttackSelfRass()` - рассеять невид
- `fastAttackOpenNevid()` - обнаружение
- `fastAttackTeleport("")` - телепорт
- `fastAttackIslandPot()` - остров
- `fastAttackTotem("")` - тотем
- `fastAttackBlazElixir()` - эликсир блаженства
- `fastAttackMomentCureElixir()` - эликсир исцеления
- `fastAttackMomentRestoreElixir()` - эликсир восстановления

## Интеграция в MainActivity

```java
// Инициализация панели быстрых кнопок
quickButtonsPanel = new QuickButtonsPanel(this, binding.getRoot(), actionType -> {
    if (actionType == QuickActionType.QUICK_ACTIONS) {
        QuickActionsBottomSheet.newInstance(null)
            .show(getSupportFragmentManager(), "QuickActions");
    }
});
```

## План реализации

- [x] Создать layout quick_buttons_bar.xml
- [x] Создать layout quick_buttons_bar_bottom.xml
- [x] Создать иконку ic_add.xml
- [x] Добавить панели в content_main.xml
- [x] Создать enum QuickActionType
- [x] Создать класс QuickButton
- [x] Создать класс QuickButtonsManager
- [x] Создать адаптер FunctionListAdapter
- [x] Создать layout для диалогов
- [x] Создать класс QuickButtonsPanel
- [x] Интегрировать в MainActivity
- [x] Подключить FastActionManager
- [x] Добавить загрузку иконок через Glide
