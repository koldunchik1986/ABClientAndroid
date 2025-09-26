# Общий анализ папки MyProfile

Папка `MyProfile` содержит классы, которые формируют модель данных для профиля пользователя и управляют его сохранением и загрузкой. Это ядро всей системы настроек приложения.

## Содержимое папки

*   **`UserConfig.cs`**: **Центральный класс-модель**. Содержит десятки полей, представляющих все возможные настройки пользователя: от логина и пароля до каждой галочки в UI (например, `DoAutoCure`, `FishAuto`, `TorgSliv`).
*   **`UserConfigLoad.cs`, `UserConfigSave.cs`**: **Ключевые классы**, отвечающие за сериализацию объекта `UserConfig` в файл на диске и его десериализацию при загрузке. Это механизм сохранения и загрузки профилей.
*   **`ConfigSelector.cs`**: Класс-менеджер, который управляет жизненным циклом профилей: создание нового, выбор существующего, запуск формы для редактирования.
*   **Остальные классы (`MyAutoAnswer.cs`, `TNavigator.cs` и т.д.)**: Вспомогательные классы, которые, вероятно, инкапсулируют отдельные группы настроек, используемые в `UserConfig`.

## Решение для портирования на Android

**Портирование этой системы является задачей высокого приоритета.** Без нее приложение не сможет хранить никакие данные.

Подход к портированию должен быть полностью переработан с учетом особенностей платформы Android.

*   **Хранение данных**: Вместо сериализации одного большого объекта в файл, в Android для хранения настроек используется `SharedPreferences`. Это системный механизм, который хранит данные в виде пар "ключ-значение" в XML-файле. Каждая настройка из `UserConfig` (например, `UserNick`, `DoAutoCure`) станет отдельной записью в `SharedPreferences`.
*   **Класс-модель**: `UserConfig.cs` будет портирован в `UserConfig.java`, но вместо прямых полей он будет содержать геттеры и сеттеры, которые будут читать и записывать значения в `SharedPreferences`.
*   **Сохранение/Загрузка**: Классы `UserConfigLoad` и `UserConfigSave` не портируются. Их логика заменяется на вызовы `SharedPreferences.Editor.putString()`, `SharedPreferences.getBoolean()`, и т.д.

## План портирования

1.  **Создать `ProfileManager.java`**: Создать класс-синглтон, который будет управлять всеми операциями с профилями. Он будет отвечать за переключение между профилями (каждый профиль — это отдельный файл `SharedPreferences`).
2.  **Создать `UserConfig.java`**: Создать класс, который будет являться "фасадом" для доступа к настройкам текущего профиля. Его методы будут обертками над `SharedPreferences`.
    ```java
    public class UserConfig {
        private SharedPreferences prefs;

        public UserConfig(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        public String getUserNick() {
            return prefs.getString("user_nick", "");
        }

        public void setUserNick(String nick) {
            prefs.edit().putString("user_nick", nick).apply();
        }

        public boolean isAutoCureEnabled() {
            return prefs.getBoolean("auto_cure_enabled", false);
        }
        // ... и так далее для всех настроек
    }
    ```
3.  **Портировать `ConfigSelector.java`**: Перенести логику управления профилями, но с использованием `SharedPreferences`.

## Список файлов в папке

*   ConfigSelector.cs
*   MyAutoAnswer.cs
*   MyCure.cs
*   TAutoAdv.cs
*   TNavigator.cs
*   TPers.cs
*   TSound.cs
*   TSplitter.cs
*   TWindow.cs
*   TypeItemDrop.cs
*   TypeStat.cs
*   UserConfig.cs
*   UserConfigConstants.cs
*   UserConfigLoad.cs
*   UserConfigSave.cs
*   UserConfigVars.cs
