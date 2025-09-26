# План портирования FormAutoAttack.cs

Файл `FormAutoAttack.cs` не является отдельным окном, а представляет собой `partial` часть класса `FormMain`. Он содержит логику, отвечающую за настройку режима автоматической атаки.

## Функциональность в C#

*   **Обработчик `MiAutoAttackClick`**: Этот метод срабатывает при выборе одного из пунктов в выпадающем меню кнопки авто-атаки на главной панели.
*   **Выбор действия**: Пользователь выбирает, каким именно инструментом (свитком) будет производиться автоматическая атака (например, "Свиток портала", "Кулачный бой" и т.д.).
*   **Сохранение настройки**: ID выбранного инструмента сохраняется в глобальную переменную `AppVars.AutoAttackToolId`.
*   **Обновление UI**: Иконка и текст основной кнопки авто-атаки на панели обновляются в соответствии с выбранным действием.
*   **Включение зависимости**: При выборе любого типа атаки, автоматически активируется опция отслеживания игроков в локации (`buttonWalkers`).

## План портирования на Android

Эта функциональность должна быть перенесена в экран настроек Android-приложения. Вместо выпадающего меню на панели инструментов, в Android более нативным будет использование диалогового окна для выбора.

1.  **UI Настроек (`PreferenceScreen`)**:
    *   В файле `res/xml/root_preferences.xml` (или аналогичном) создать `ListPreference` для настройки авто-атаки.
        ```xml
        <ListPreference
            app:key="auto_attack_action"
            app:title="Действие для авто-атаки"
            app:summary="%s"
            app:defaultValue="0"
            app:entries="@array/auto_attack_entries"
            app:entryValues="@array/auto_attack_values" />
        ```
    *   В `res/values/arrays.xml` определить массивы:
        ```xml
        <string-array name="auto_attack_entries">
            <item>Не атаковать</item>
            <item>Свиток Ультимативной Атаки</item>
            <item>Закрытый Свиток Ультимативной Атаки</item>
            <item>Свиток Кулачного Боя</item>
            <item>...</item>
        </string-array>
        <string-array name="auto_attack_values">
            <item>0</item>
            <item>1</item>
            <item>2</item>
            <item>3</item>
            <item>...</item>
        </string-array>
        ```

2.  **Логика (`SettingsFragment.java`)**:
    *   Создать `PreferenceFragmentCompat` для отображения экрана настроек.
    *   Добавить слушатель `OnPreferenceChangeListener` для `ListPreference` авто-атаки.
    *   В обработчике:
        1.  Сохранять выбранное значение в `SharedPreferences`.
        2.  Находить `SwitchPreferenceCompat`, отвечающий за отслеживание игроков (`walkers_enabled`).
        3.  Если выбранное значение для атаки не `"0"`, программно устанавливать `walkers_enabled.setChecked(true)`.

3.  **Глобальное состояние**:
    *   Логика, использующая эту настройку (например, в `RoomManager` или `MainPhp`), должна будет читать ее из `SharedPreferences` вместо `AppVars.AutoAttackToolId`.
