# План портирования MyCure.cs

Файл `MyCure.cs` — это data-класс, инкапсулирующий все настройки для функции автоматического лечения других игроков.

## Функциональность в C#

Класс хранит набор параметров, которые пользователь может настроить:

*   **Цены**: Массив цен в NV за лечение 4-х типов трав (легкая, средняя, тяжелая, боевая).
*   **Шаблоны сообщений**: Набор шаблонов для разных ситуаций: рекламное сообщение в чат, запрос на лечение, сообщение после успеха, сообщение об ошибке (если цель в бою).
*   **Фильтры**: Набор булевых флагов для включения/выключения лечения каждого типа травмы, а также опция для отключения лечения низкоуровневых персонажей.
*   **Сериализация**: Методы `Write` и `Read` отвечают за сохранение и загрузку этих настроек в/из XML-файла профиля.

## План портирования на Android

Все эти настройки должны быть перенесены в `SharedPreferences` и управляться через `UserConfig.java` и экран настроек `PreferenceScreen`.

1.  **Интеграция настроек в `UserConfig.java`**:
    *   Для каждой настройки из `MyCure.cs` создать соответствующий геттер/сеттер в `UserConfig.java`, который будет работать с `SharedPreferences`.
    *   Для массивов (цены, сообщения, флаги `Enabled`) рекомендуется использовать сериализацию в JSON. Создать простой POJO-класс `CureSettingItem` (с полями `price`, `message`, `isEnabled`) и хранить в `SharedPreferences` массив `List<CureSettingItem>` в виде одной JSON-строки. Это упростит управление.
        ```java
        // Пример для цен
        public List<Integer> getCurePrices() {
            String json = prefs.getString("cure_prices_json", null);
            if (json == null) return Arrays.asList(10, 15, 25, 600); // Default
            Type type = new TypeToken<ArrayList<Integer>>() {}.getType();
            return new Gson().fromJson(json, type);
        }

        public void setCurePrices(List<Integer> prices) {
            String json = new Gson().toJson(prices);
            prefs.edit().putString("cure_prices_json", json).apply();
        }
        ```

2.  **Интеграция в UI настроек**:
    *   В `root_preferences.xml` создать `PreferenceCategory` с заголовком "Авто-лечение".
    *   Внутри нее создать 4 под-экрана (`PreferenceScreen`) для каждого типа травмы.
    *   В каждом под-экране разместить:
        *   `SwitchPreferenceCompat` для включения/выключения лечения этого типа травмы.
        *   `EditTextPreference` с `inputType="number"` для указания цены.
        *   `EditTextPreference` для редактирования сообщения-запроса.
    *   В основной категории "Авто-лечение" также разместить `EditTextPreference` для общего рекламного сообщения и `SwitchPreferenceCompat` для опции "Не лечить 0-4 уровни".

3.  **Логика**: Основная логика авто-лечения (которая, вероятно, находится в `MainPhp.cs`) будет использовать эти геттеры из `UserConfig.java` для принятия решений.
