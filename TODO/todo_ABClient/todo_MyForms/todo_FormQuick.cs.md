# План портирования FormQuick.cs

Файл `FormQuick.cs` реализует панель быстрых атак — набор кнопок для применения различных типов атак на указанного персонажа.

## 1. Функциональность в C#

### Назначение
Панель быстрых действий позволяет игроку выбрать тип атаки и цель (ник) и одним нажатием начать атаку. Открывается из контекстного меню игрока в чате или из других мест.

### UI (из FormQuick.Designer.cs)
- `textBoxNick` — поле ввода ника цели (с AutoComplete)
- `checkBoxClose` — галочка "Как нажали - закрыть" (checked по умолчанию)
- 11 кнопок с иконками из ресурсов:

| Кнопка | Иконка | Тултип | Метод FastAttack |
| ------ | ------ | ------ | ---------------- |
| `buttonHitSimple` | `i_svi_001` | "Обычная нападалка" | `FormMain.FastAttack(nick)` |
| `buttonHitBlood` | `i_svi_002` | "Кровавая нападалка" | `FormMain.FastAttackBlood(nick)` |
| `buttonHitUltimate` | `i_w28_26` | "Боевая нападалка" | `FormMain.FastAttackUltimate(nick)` |
| `buttonHitClosedUltimate` | `i_w28_26x` | "Закрытая боевая нападалка" | `FormMain.FastAttackClosedUltimate(nick)` |
| `buttonClosed` | — | "Закрытая нападалка" | `FormMain.FastAttackClosed(nick)` |
| `buttonFistSimple` | `i_w28_24` | "Обычная кулачка" | `FormMain.FastAttackFist(nick)` |
| `buttonFistClosed` | `i_w28_25` | "Закрытая кулачка" | `FormMain.FastAttackClosedFist(nick)` |
| `buttonFog` | `i_svi_213` | "Туман" | `FormMain.FastAttackFog(nick)` |
| `buttonPoison` | `i_w27_41` | "Яд" | `FormMain.FastAttackPoison(nick)` |
| `buttonStrong` | `i_w27_52` | "Сильная спина" | `FormMain.FastAttackStrong(nick)` |
| `buttonInvisible` | `i_w27_53` | "Невид" | `FormMain.FastAttackNevidPot(nick)` |

### Логика
1. Конструктор принимает `nick` — предзаполняет `textBoxNick`
2. Каждая кнопка при клике:
   - Вызывает `FormMain.FastAttackXxx(textBoxNick.Text.Trim())`
   - Вызывает `CheckClose()` — если `checkBoxClose.Checked`, закрывает окно
3. Изменение текста ника обновляет заголовок окна

### Связь с FastAttack-системой (FormMainFast.cs)
`FormMain.FastAttack(nick)` вызывает `FastStartSafe(id, nick)` которая:
- Устанавливает `AppVars.FastNeed = true`, `AppVars.FastId`, `AppVars.FastNick`
- Запускает `FastAttackAsync` в `ThreadPool` — он:
  1. Получает инфу о цели через `NeverApi.GetAll(nick)`
  2. Проверяет, не в бою ли цель (опрашивает лог боя)
  3. Ожидает окончания боя цели (polling)
  4. Выполняет атаку

### Зависимости
- `NeverApi.GetAll(nick)` — нужен `ApiRepository` (частично портирован)
- `AppVars.FastNeed/FastId/FastNick/FastCount` — **не существуют** в Android AppVars
- Иконки — все есть в `assets/Icons/`

## 2. Проверка существующей реализации в Android

### Что уже есть:
- `WebAppInterface.java` — методы `CheckFastAttack*()` возвращают HTML-кнопки на основе `doShowFast*` настроек профиля
- `UserConfig.java` — все 16 полей `doShowFastAttack*` с сохранением/загрузкой из XML
- Иконки в `assets/Icons/`: i_svi_001.gif, i_svi_002.gif, i_w28_24.gif, i_w28_25.gif, i_w28_26.gif, i_w28_26x.png, i_svi_213.gif, i_w27_41.gif, i_w27_52.gif, i_w27_53.gif

### Чего нет:
- Нет класса `QuickActionsBottomSheet` или аналога
- Нет layout `bottom_sheet_quick_actions.xml`
- Нет `AppVars.FastNeed/FastId/FastNick/FastCount`
- Нет `FastActionManager` (логика из `FormMainFast.cs`)
- Нет реального вызова быстрых атак (WebAppInterface только проверяет видимость кнопок)

## 3. Решение для портирования на Android

Реализуем как `BottomSheetDialogFragment` — современный Material-компонент, выезжающий снизу.

**Важно:** На данном этапе реализуем только UI-панель. Логика самих быстрых атак (`FastActionManager` / `FormMainFast.cs`) — это отдельная задача, которая зависит от системы автоматизации (`MainPhpFast.cs`). Пока что кнопки будут показывать Toast с информацией о действии.

## 4. План реализации

### Шаг 1: Создать layout
- [x] Создать `res/layout/bottom_sheet_quick_actions.xml`:
  - `EditText` для ника (hint: "Ник цели")
  - `SwitchCompat` — "Закрыть после действия" (checked по умолчанию)
  - `GridLayout` (3 колонки) с `ImageButton` для каждой атаки
  - Кнопки загружают иконки из assets через Glide

### Шаг 2: Создать QuickActionsBottomSheet.java
- [x] Создать `app/src/main/java/ru/neverlands/abclient/ui/QuickActionsBottomSheet.java`
- [x] Наследник `BottomSheetDialogFragment`
- [x] Конструктор/аргумент — nick (через `Bundle` / `newInstance`)
- [x] В `onCreateView` — inflate layout
- [x] В `onViewCreated` — настроить кнопки, тултипы (long press показывает Toast с названием)

### Шаг 3: Реализовать обработчики кнопок
- [x] Каждая кнопка: получает ник из EditText, вызывает метод атаки
- [x] Пока: показывать Toast "Быстрая атака: {тип} на {ник}" (заглушка)
- [ ] Позже: заменить на реальный вызов `FastActionManager`
- [x] Если Switch включён — вызвать `dismiss()`

### Шаг 4: Добавить Fast-переменные в AppVars
- [x] Добавить в `AppVars.java`: `FastNeed`, `FastId`, `FastNick`, `FastCount`, `FastWaitEndOfBoiActive`, `FastWaitEndOfBoiCancel`, `FastNeedAbilDarkTeleport`, `FastNeedAbilDarkFog`

### Шаг 5: Интеграция
- [x] Добавить вызов `QuickActionsBottomSheet.newInstance(nick).show(...)` из Navigation Drawer или из контекстного меню
- [x] Добавить пункт меню в `activity_main_drawer` (Navigation Drawer)

### Шаг 6: Проверка
- [x] Собрать приложение — BUILD SUCCESSFUL
- [ ] Проверить на устройстве, что BottomSheet открывается
- [ ] Проверить на устройстве, что иконки отображаются
- [ ] Проверить на устройстве, что кнопки реагируют на нажатие
- [ ] Проверить на устройстве, что auto-close работает
