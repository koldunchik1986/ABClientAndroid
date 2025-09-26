
### 1. План портирования FormAutoAttack.cs

Файл `FormAutoAttack.cs` на самом деле не является отдельной формой. Это `partial class` для `FormMain`, содержащая логику, связанную с выбором режима автоматической атаки.

### 2. Функциональность в C#

- **Назначение:** Обработать клик по пункту выпадающего меню кнопки "Авто-атака" на главной панели инструментов.
- **Логика:**
    1.  При выборе одного из режимов атаки из меню, код считывает `Tag` (ID атаки) и `Text`/`Image` этого пункта.
    2.  Обновляет главную кнопку (`buttonAutoAttack`) на панели, чтобы она отображала выбранный режим.
    3.  Сохраняет ID выбранной атаки в глобальную переменную `AppVars.AutoAttackToolId`.
    4.  **Важно:** Автоматически включает главный переключатель "Ходилки" (`buttonWalkers`), запуская основной режим бота.

### 3. Решение для портирования на Android

Эта логика является частью основного UI и должна быть портирована в рамках `MainActivity` и ее `ViewModel`.

- **UI:** Вместо `ToolStripButton` с выпадающим меню в Android можно использовать:
    - `Spinner`.
    - `Button`, который по клику показывает `PopupMenu`.
- **Управление состоянием:** Состояние (ID выбранной атаки, статус "Ходилок") должно храниться в `MainViewModel`.

### 4. План реализации

Эта функциональность будет реализована как часть `MainActivity`.

1.  **В `MainViewModel`:**
    - Добавить `val selectedAttackId = MutableLiveData<Int>()`.
    - Добавить `val isWalkersEnabled = MutableLiveData<Boolean>()`.
2.  **В макете `activity_main.xml`:**
    - Добавить `Spinner` или `Button` для выбора режима атаки.
    - Добавить `Switch` или `ToggleButton` для режима "Ходилки".
3.  **В `MainActivity.kt`:**
    - Настроить `Spinner` или `PopupMenu` со списком доступных атак.
    - Установить слушатель выбора (`OnItemSelectedListener` или `OnMenuItemClickListener`).
    - В слушателе:
        - Вызывать метод `viewModel.setAttackMode(selectedId)`.
        - Вызывать метод `viewModel.setWalkersEnabled(true)`.
    - Подписаться на `viewModel.isWalkersEnabled`, чтобы программно обновлять состояние `Switch` на UI.

- [ ] Добавить `Spinner` или `Button` с `PopupMenu` в макет `activity_main.xml`.
- [ ] Добавить `selectedAttackId` и `isWalkersEnabled` в `MainViewModel`.
- [ ] Реализовать логику обновления `ViewModel` при выборе атаки в `MainActivity`.
