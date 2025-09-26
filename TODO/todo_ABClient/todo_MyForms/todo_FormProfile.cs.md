
### 1. План портирования FormProfile.cs

Файл `FormProfile.cs` реализует ключевое окно приложения - форму для создания и редактирования профиля пользователя (`UserConfig`).

### 2. Функциональность в C#

- **Режимы работы:** Может работать в режиме создания нового профиля или редактирования существующего.
- **UI:** Содержит поля для всех основных настроек:
    - Имя пользователя, ключ, пароль, флеш-пароль.
    - Галочка "Автовход".
    - Настройки прокси: галочка "Использовать прокси", адрес, логин и пароль.
- **Логика:**
    - **`CheckAvailability()`**: Центральный метод, который управляет активностью (`Enabled`) элементов UI в зависимости от введенных данных (например, кнопка "OK" активна, только если введен логин и пароль).
    - **`LinkDetectProxy_LinkClicked`**: Пытается автоматически определить системные настройки прокси из Internet Explorer.
    - **`LinkPasswordProtected_LinkClicked`**: Управляет шифрованием профиля. Вызывает диалог `FormNewPassword` для установки мастер-пароля и затем вызывает метод `UserConfig.Encrypt()` для шифрования паролей. Также позволяет расшифровать профиль.
    - **`ButtonOk_Click`**: Собирает все данные из полей ввода и сохраняет их в объект `SelectedUserConfig`.

### 3. Решение для портирования на Android

Это главный экран настроек. Его следует реализовать как `Activity`, используя `PreferenceFragmentCompat` для стандартного вида экрана настроек Android.

- **Хранение данных:** Вместо прямого сохранения в XML, следует использовать `SharedPreferences` как стандартное хранилище для настроек. Класс `UserConfig` будет служить моделью данных, а `SettingsRepository` будет отвечать за его загрузку/сохранение в `SharedPreferences`.
- **Шифрование:** Для безопасного хранения паролей следует использовать **`EncryptedSharedPreferences`** из библиотеки Android Jetpack Security. Для защиты мастер-паролем можно использовать **Android Keystore System** для хранения ключа шифрования.

### 4. План реализации

### 4. План реализации

Поскольку в Android-коде уже существует `SettingsActivity`, эта форма может быть реализована как `PreferenceFragmentCompat`, который будет загружаться в этой же `Activity`.

1.  **Создать `res/xml/profile_preferences.xml`:**
    - Описать экран настроек профиля с помощью `PreferenceScreen`.
    - Использовать `EditTextPreference` для текстовых полей (UserNick, UserPassword и т.д.). Установить `app:isPassword="true"` для полей с паролями.
    - Использовать `SwitchPreferenceCompat` для `UserAutoLogon` и `DoProxy`.
    - Создать кастомные `Preference` для действий "Определить прокси" и "Зашифровать профиль".

2.  **Создать `ProfileSettingsFragment`:**
    - Унаследовать от `PreferenceFragmentCompat`.
    - Загрузить `profile_preferences.xml`.
    - Этот фрагмент будет запускаться из `ProfileChooserActivity` (анализ `FormProfiles.cs`) в режиме создания или редактирования.

3.  **Создать `ProfileRepository`:**
    - Реализовать методы `fun loadProfile(profileName: String): UserConfig` и `fun saveProfile(config: UserConfig)`.
    - Использовать `EncryptedSharedPreferences` для безопасного хранения. Имя файла `SharedPreferences` должно быть основано на имени профиля, чтобы поддерживать несколько профилей.

4.  **Реализовать шифрование:**
    - При нажатии на `Preference` "Зашифровать", показать `NewPasswordDialogFragment`.
    - Получив пароль, использовать его для генерации ключа, который сохраняется в Android Keystore.
    - Перешифровать все чувствительные данные в `SharedPreferences`.

5.  **Реализовать логику UI:**
    - В `ProfileSettingsFragment` добавить `OnPreferenceChangeListener` для реализации логики, аналогичной `CheckAvailability()` (например, делать неактивными настройки прокси, если главный переключатель выключен).

- [ ] Создать `profile_preferences.xml` для UI настроек профиля.
- [ ] Создать `ProfileSettingsFragment`.
- [ ] Реализовать `ProfileRepository` для работы с несколькими файлами `EncryptedSharedPreferences` (по одному на профиль).
- [ ] Реализовать логику шифрования/дешифрования профиля.
- [ ] Настроить `OnPreferenceChangeListener` для динамического управления UI.
