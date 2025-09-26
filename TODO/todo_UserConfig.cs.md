# План портирования UserConfig

Эта группа файлов (`UserConfig.cs`, `UserConfigLoad.cs`, `UserConfigSave.cs`, `UserConfigConstants.cs`, `UserConfigVars.cs`) представляет собой ядро всей системы хранения настроек и профилей пользователя.

## Функциональность в C#

*   **`UserConfig.cs`**: Огромный `partial` класс, который является data-классом (моделью) для всех настроек приложения. Он содержит сотни полей — от логина/пароля до каждого флага-настройки.
*   **`UserConfigLoad.cs`**: Содержит метод `Load`, который вручную, тег за тегом, парсит XML-файл профиля (`*.abf`) с помощью `XmlReader` и заполняет поля объекта `UserConfig`.
*   **`UserConfigSave.cs`**: Содержит метод `Save`, который вручную, поле за полем, записывает содержимое объекта `UserConfig` в XML-файл с помощью `XmlWriter`.
*   **`UserConfigConstants.cs`**: Содержит все константы: имена XML-тегов и атрибутов, а также значения по умолчанию для всех настроек.

## Решение для портирования на Android

**Это критически важный модуль, который должен быть портирован в первую очередь, но с полной заменой механизма хранения.**

Вместо ручной сериализации в XML, в Android для хранения настроек используется системный механизм `SharedPreferences`.

## План портирования

1.  **Создать `AppConsts.java`**:
    *   Перенести в этот класс все константы из `UserConfigConstants.cs`. Имена XML-атрибутов (например, `ConstAttibuteUserNick`) станут ключами для `SharedPreferences` (например, `public static final String KEY_USER_NICK = "user_nick";`).
    *   Также перенести все значения по умолчанию (`...Default`).

2.  **Создать `ProfileManager.java`**:
    *   Создать класс-синглтон, который будет управлять доступом к профилям.
    *   Основной метод `loadProfile(Context context, String profileName)` будет возвращать готовый к работе объект `UserConfig`.
    *   Внутри `loadProfile` он будет получать экземпляр `SharedPreferences`: `context.getSharedPreferences("profile_" + profileName, Context.MODE_PRIVATE)`.

3.  **Создать `UserConfig.java`**:
    *   Этот класс будет **фасадом** для работы с `SharedPreferences`, а не простым data-классом.
    *   **Конструктор**: `public UserConfig(SharedPreferences preferences)`.
    *   **Геттеры/Сеттеры**: Для **каждого** поля из оригинального `UserConfig.cs` нужно создать геттер и сеттер. 
        *   Геттер будет читать значение из `SharedPreferences`.
        *   Сеттер будет записывать значение в `SharedPreferences`.

    **Пример реализации для одной настройки:**
    ```java
    public class UserConfig {
        private final SharedPreferences prefs;

        public UserConfig(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        // Аналог поля UserNick
        public String getUserNick() {
            return prefs.getString(AppConsts.KEY_USER_NICK, AppConsts.DEFAULT_USER_NICK);
        }

        public void setUserNick(String nick) {
            prefs.edit().putString(AppConsts.KEY_USER_NICK, nick).apply();
        }

        // Аналог поля DoAutoCure
        public boolean isAutoCureEnabled() {
            return prefs.getBoolean(AppConsts.KEY_AUTO_CURE, AppConsts.DEFAULT_AUTO_CURE);
        }

        public void setAutoCureEnabled(boolean enabled) {
            prefs.edit().putBoolean(AppConsts.KEY_AUTO_CURE, enabled).apply();
        }
        
        // ... и так далее для сотен других настроек.
    }
    ```

4.  **Отказаться от `UserConfigLoad` и `UserConfigSave`**: Эти классы не портируются. Их логика полностью заменяется набором геттеров и сеттеров в `UserConfig.java`, которые работают с `SharedPreferences`.

Этот подход позволит получить чистую, идиоматичную для Android систему настроек, с которой будет легко работать из любого места приложения, и которую легко расширять в будущем.
