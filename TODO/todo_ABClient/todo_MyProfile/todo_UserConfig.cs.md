### 1. План портирования UserConfig.cs (и его partial-частей)

Класс `UserConfig` — это центральная модель данных, хранящая абсолютно все настройки пользователя. Класс разделен на несколько `partial`-файлов:
- `UserConfig.cs`: Основная логика, конструктор со значениями по умолчанию, методы шифрования/дешифрования.
- `UserConfigVars.cs`: Декларация всех свойств (properties) для настроек.
- `UserConfigLoad.cs`: Логика загрузки (десериализации) профиля из XML-файла.
- `UserConfigSave.cs`: Логика сохранения (сериализации) профиля в XML-файл.
- `UserConfigConstants.cs`: Все константы, используемые при сохранении и загрузке (имена тегов и атрибутов XML, значения по умолчанию).

### 2. Функциональность в C#

- **Хранение данных:** Является DTO (Data Transfer Object) для всех настроек приложения, от логина/пароля до специфических настроек автобоя, чата, карты и т.д.
- **Сериализация:** Умеет сохранять и загружать себя в/из XML-файла. Использует `XmlReader` и `XmlWriter` для ручного разбора и построения XML.
- **Шифрование:** Содержит методы `Encrypt` и `Decrypt` для шифрования/дешифрования паролей пользователя с помощью `Helpers.Crypts`.
- **Вложенные объекты:** Содержит в себе другие классы-настройки, такие как `TWindow`, `TSplitter`, `TPers`, `List<LezBotsGroup>` и т.д.

### 3. Решение для портирования на Android

В Android для хранения настроек используется `SharedPreferences`. Вместо одного монолитного класса `UserConfig` и ручной XML-сериализации, следует использовать современный подход с `SharedPreferences` и паттерном "Репозиторий".

- **Архитектура:**
    - **`SettingsRepository`:** Создать синглтон-класс, который будет предоставлять типизированный доступ ко всем настройкам. Он будет инкапсулировать работу с `SharedPreferences`.
    - **`SharedPreferences`:** Использовать как основное хранилище. Для безопасности, пароли и другие чувствительные данные следует хранить в `EncryptedSharedPreferences`.
- **Миграция данных:** При первом запуске приложения необходимо будет реализовать логику, которая читает оригинальный `.xml` профиль и переносит все настройки в `SharedPreferences`.

### 4. План реализации

1.  **Создать `SettingsRepository.kt`:**
    - Создать `object SettingsRepository`.
    - Внедрить в него `Context` для доступа к `SharedPreferences`.
    - Для каждой группы настроек из `UserConfig` создать соответствующее свойство с `get`/`set`, которое будет читать/писать в `SharedPreferences`.
      ```kotlin
      object SettingsRepository {
          private lateinit var prefs: SharedPreferences
          private lateinit var encryptedPrefs: SharedPreferences

          fun init(context: Context) { 
              prefs = PreferenceManager.getDefaultSharedPreferences(context)
              // ... init EncryptedSharedPreferences
          }

          var userNick: String
              get() = prefs.getString("user_nick", "") ?: ""
              set(value) = prefs.edit().putString("user_nick", value).apply()

          var isAutoBattleEnabled: Boolean
              get() = prefs.getBoolean("lez_do_autoboi", true)
              set(value) = prefs.edit().putBoolean("lez_do_autoboi", value).apply()

          // ... и так далее для всех простых типов (String, Int, Boolean)
      }
      ```
2.  **Обработка сложных типов:**
    - **Списки (`List<string>`):** Хранить как `Set<String>` в `SharedPreferences`.
    - **Сложные объекты (`List<LezBotsGroup>`, `TWindow`):** Сериализовать в строку JSON с помощью `Gson` или `Moshi` и хранить как одну строку в `SharedPreferences`. Для них в `SettingsRepository` также создаются свойства, которые будут выполнять сериализацию/десериализацию при доступе.
      ```kotlin
      var lezGroups: List<LezBotsGroup>
          get() {
              val json = prefs.getString("lez_groups", null) ?: return listOf(LezBotsGroup(1, 0))
              // ... логика десериализации из JSON
          }
          set(value) {
              val json = // ... логика сериализации в JSON
              prefs.edit().putString("lez_groups", json).apply()
          }
      ```
3.  **Шифрование:**
    - Для паролей (`userPassword`, `userPasswordFlash`) использовать `EncryptedSharedPreferences` вместо самописного `Helpers.Crypts`.
4.  **Замена `AppVars.Profile`:**
    - Все обращения в коде к `AppVars.Profile.SomeSetting` должны быть заменены на `SettingsRepository.someSetting`.

- [ ] Создать `SettingsRepository` для инкапсуляции работы с `SharedPreferences`.
- [ ] Использовать `EncryptedSharedPreferences` для паролей.
- [ ] Для каждого поля из `UserConfigVars.cs` создать свойство в `SettingsRepository`.
- [ ] Реализовать сериализацию/десериализацию сложных объектов (списки, кастомные классы) в JSON.
- [ ] Спланировать и реализовать однократную миграцию данных из старого XML-профиля в `SharedPreferences` при первом запуске.