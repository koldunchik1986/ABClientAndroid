### 1. План портирования FormSettingsAutoCut.cs

Файл `FormSettingsAutoCut.cs` реализует UI и логику для окна настроек функции "Авто-срезание" трав.

### 2. Функциональность в C#

- **Назначение:** Предоставить пользователю интерфейс для выбора трав, которые клиент будет пытаться срезать автоматически при их обнаружении.
- **UI Компоненты:**
    - `listViewHerbs`: Основной элемент, `ListView` с включенными чекбоксами (`CheckBoxes = true`). В дизайнере он статически заполнен списком всех 75 трав, разделенных на 9 групп (например, "Группа 1", "Квестовые").
    - `buttonSelectAll`: Кнопка для выбора всех элементов в `listViewHerbs`.
    - `buttonUnselectAll`: Кнопка для снятия выбора со всех элементов.
    - `checkDoAutoCutWriteChat`: `CheckBox` для включения/отключения сообщений о срезании в чат клиента.
    - `buttonAccept`: Кнопка "Сохранить", которая применяет изменения и закрывает форму.
    - `buttonCancel`: Стандартная кнопка отмены.
- **Логика:**
    - **Загрузка (`FormSettingsAutoCut()` конструктор):** При открытии формы код итерирует по списку уже сохраненных в профиле трав (`AppVars.Profile.HerbsAutoCut`). Для каждой сохраненной травы он находит соответствующий `ListViewItem` по тексту и устанавливает его свойство `Checked` в `true`.
    - **Сохранение (`buttonAccept_Click`):**
        1.  Список `AppVars.Profile.HerbsAutoCut` полностью очищается.
        2.  Код итерирует по всем `Items` в `listViewHerbs`.
        3.  Если `Item` отмечен (`Checked == true`), его текст (название травы) добавляется в `AppVars.Profile.HerbsAutoCut`.
        4.  Сохраняется состояние `checkDoAutoCutWriteChat` в `AppVars.Profile.DoAutoCutWriteChat`.
        5.  Вызывается `AppVars.Profile.Save()` для сохранения изменений в XML-файле профиля.

### 3. Решение для портирования на Android

Данный экран является классическим примером настроек с множественным выбором и должен быть реализован с помощью стандартных компонентов Android `Preference`.

- **Архитектура:** Создать `PreferenceFragmentCompat`, который будет содержать все элементы настроек. Это стандартный подход для экранов настроек в Android.
- **Хранение данных:** Настройки будут храниться в `SharedPreferences`. `MultiSelectListPreference` идеально подходит для хранения набора выбранных строк.

### 4. План реализации

1.  **Создать `res/values/arrays.xml` (или обновить существующий):**
    - [ ] Создать строковый массив `<string-array name="herb_names">`, который будет содержать все 75 названий трав, взятых из `FormSettingsAutoCut.Designer.cs`.
    - [ ] Создать второй, идентичный массив `<string-array name="herb_values">`, который будет использоваться для `entryValues`.
2.  **Создать `res/xml/preferences_autocut.xml`:**
    - [ ] Создать корневой `PreferenceScreen`.
    - [ ] Добавить `MultiSelectListPreference`:
        - `app:key="autocut_herb_list"`
        - `app:title="Травы для авто-срезания"`
        - `app:summary="Выберите, какие травы срезать автоматически"`
        - `app:entries="@array/herb_names"`
        - `app:entryValues="@array/herb_names"`
        - `app:dialogTitle="Выберите травы"`
    - [ ] Добавить `SwitchPreferenceCompat`:
        - `app:key="autocut_report_to_chat"`
        - `app:title="Сообщать о срезании в чат"`
        - `app:defaultValue="true"`
    - [ ] Добавить два `Preference` для кнопок "Выбрать все" / "Снять выбор":
        - `<Preference app:key="autocut_select_all" app:title="Выбрать все" />`
        - `<Preference app:key="autocut_unselect_all" app:title="Снять выбор" />`
3.  **Создать `SettingsAutoCutFragment.kt`:**
    - [ ] Создать класс, наследующий `PreferenceFragmentCompat`.
    - [ ] В `onCreatePreferences`, загрузить иерархию из `R.xml.preferences_autocut`.
    - [ ] Найти `Preference` "Выбрать все" и "Снять выбор" по их ключам.
    - [ ] Установить на них `onPreferenceClickListener`.
    - [ ] В обработчике клика:
        - Найти `MultiSelectListPreference` по ключу `autocut_herb_list`.
        - Получить полный список всех возможных значений из `R.array.herb_names`.
        - Создать новый `Set<String>`, содержащий все или ни одного значения.
        - Установить этот новый сет как значение для `MultiSelectListPreference`: `multiSelectListPreference.values = newValues`.

- [ ] Создать `arrays.xml` со списком трав.
- [ ] Создать `prefs_autocut.xml` с `MultiSelectListPreference` и `SwitchPreferenceCompat`.
- [ ] Создать `SettingsAutoCutFragment.kt` и реализовать в нем логику для кнопок "Выбрать все" / "Снять выбор".