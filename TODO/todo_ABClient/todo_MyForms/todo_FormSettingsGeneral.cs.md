
### 1. План портирования FormSettingsGeneral.cs

Файл `FormSettingsGeneral.cs` представляет собой огромную форму-"бога", содержащую все основные настройки приложения, сгруппированные по вкладкам.

### 2. Функциональность в C#

- **UI:** Окно с `TabControl`, содержащее десятки элементов управления (`CheckBox`, `TextBox`, `NumericUpDown` и т.д.) для каждой настройки.
- **Логика:**
    - **Загрузка:** При открытии формы, она считывает практически все свойства из глобального объекта `AppVars.Profile` и выставляет соответствующие значения в элементах UI.
    - **Сохранение:** При нажатии "OK", форма считывает состояние каждого элемента UI и записывает его обратно в `AppVars.Profile`, после чего вызывает `AppVars.Profile.Save()`.
    - **Валидация:** Некоторые поля имеют логику валидации, которая не позволяет пользователю ввести некорректные данные.

### 3. Решение для портирования на Android

Прямое портирование такой монолитной формы приведет к ужасному пользовательскому опыту на мобильном устройстве. Необходимо полностью перепроектировать этот экран, следуя гайдлайнам Android, с использованием `PreferenceFragmentCompat`.

- **Архитектура:** Создать иерархическую систему настроек. Главный экран настроек будет содержать категории, каждая из которых будет вести на отдельный экран с настройками для этой категории.
- **Хранение:** Все настройки должны храниться в `SharedPreferences`, что является стандартом для Android. `PreferenceFragmentCompat` работает с `SharedPreferences` автоматически.

### 4. План реализации

### 4. План реализации

Анализ показал, что в Android-коде уже существует `SettingsActivity` и базовый `SettingsFragment`, что является отличной основой. План состоит в наполнении этой структуры реальными настройками.

1.  **Создать иерархию XML-файлов настроек:**
    - `res/xml/root_preferences.xml`: Модифицировать существующий файл (если он есть) или создать новый. Он будет главным экраном настроек и будет содержать `Preference` для перехода на другие экраны.
      ```xml
      <PreferenceScreen xmlns:app="http://schemas.android.com/apk/res-auto">
          <Preference
              app:fragment="ru.neverlands.abclient.SettingsActivity$ChatSettingsFragment"
              app:title="Настройки чата" />
          <Preference
              app:fragment="ru.neverlands.abclient.SettingsActivity$MapSettingsFragment"
              app:title="Настройки карты" />
          <Preference
              app:fragment="ru.neverlands.abclient.SettingsActivity$FishSettingsFragment"
              app:title="Настройки рыбалки" />
          <!-- и так далее для торга, звуков, автобоя... -->
      </PreferenceScreen>
      ```
    - `res/xml/chat_preferences.xml`, `res/xml/map_preferences.xml` и т.д.: Создать отдельные файлы для каждой группы настроек, используя `SwitchPreferenceCompat`, `EditTextPreference`, `ListPreference`.

2.  **Модифицировать `SettingsActivity.java`:**
    - Убедиться, что `SettingsActivity` корректно обрабатывает навигацию между различными фрагментами настроек.

3.  **Создать вложенные классы `...SettingsFragment`:**
    - Внутри `SettingsActivity.java` создать публичные статические вложенные классы для каждого экрана настроек.
      ```java
      public static class ChatSettingsFragment extends PreferenceFragmentCompat {
          @Override
          public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
              setPreferencesFromResource(R.xml.chat_preferences, rootKey);
          }
      }

      public static class MapSettingsFragment extends PreferenceFragmentCompat {
          // ... и так далее
      }
      ```

4.  **Перенести логику:**
    - **Валидация:** Для `EditTextPreference` использовать `setOnPreferenceChangeListener` для проверки вводимых данных перед сохранением.
    - **Кастомные диалоги:** Для настроек, которые в C# открывали `FormEnterInt`, создать кастомные классы, унаследованные от `DialogPreference`.

- [ ] Спроектировать полную иерархию экранов настроек.
- [ ] Создать XML-файлы с `Preference`-элементами для каждой категории.
- [ ] Реализовать все необходимые `...SettingsFragment` как вложенные классы в `SettingsActivity`.
- [ ] Перенести логику валидации и кастомных диалогов в новые фрагменты.
