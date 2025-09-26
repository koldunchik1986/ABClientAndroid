# План портирования MyAutoAnswer.cs

Файл `MyAutoAnswer.cs` инкапсулирует всю логику и настройки для функции "Автоответчик".

## Функциональность в C#

*   **Хранение настроек**: Класс хранит два основных параметра: флаг `Active` (включен/выключен) и `StrAnswers` — большую строку со всеми вариантами ответов, разделенными переводом строки.
*   **Получение ответа (`GetNextAnswer`)**: Основной метод, который возвращает следующий ответ для отправки. Он не просто выбирает случайный ответ, а сначала перемешивает список ответов и затем выдает их по порядку, чтобы избежать частых повторений.
*   **Сериализация**: Методы `Write` и `Read` отвечают за сохранение и загрузку настроек автоответчика в/из XML-файла профиля.

## План портирования на Android

Логика и настройки этого класса должны быть интегрированы в общую систему профилей, основанную на `SharedPreferences`.

1.  **Интеграция настроек в `UserConfig.java`**:
    *   Вместо отдельного класса, его поля станут частью `UserConfig`.
    *   Добавить в `UserConfig.java` методы для доступа к настройкам:
        ```java
        public boolean isAutoAnswerActive() {
            return prefs.getBoolean("autoanswer_active", false);
        }

        public void setAutoAnswerActive(boolean active) {
            prefs.edit().putBoolean("autoanswer_active", active).apply();
        }

        public String getAutoAnswerMessages() {
            // Возвращаем стандартный набор ответов, если пользователь еще не задал свой
            return prefs.getString("autoanswer_messages", AppConsts.DEFAULT_AUTOANSWERS);
        }

        public void setAutoAnswerMessages(String messages) {
            prefs.edit().putString("autoanswer_messages", messages).apply();
        }
        ```

2.  **Интеграция в UI настроек**:
    *   В `profile_preferences.xml` добавить `SwitchPreferenceCompat` с ключом `autoanswer_active`.
    *   Добавить `EditTextPreference` с ключом `autoanswer_messages`. Для него нужно будет установить `android:singleLine="false"`, чтобы поле ввода было многострочным.

3.  **Реализация логики `GetNextAnswer`**:
    *   Эту логику можно разместить прямо в `UserConfig.java`.
    ```java
    private List<String> shuffledAnswers;
    private int lastAnswerIndex = -1;

    public String getNextAnswer() {
        if (shuffledAnswers == null) {
            String[] answers = getAutoAnswerMessages().split("\\r?\\n");
            shuffledAnswers = new ArrayList<>(Arrays.asList(answers));
            Collections.shuffle(shuffledAnswers);
        }

        if (shuffledAnswers.isEmpty()) {
            return "";
        }

        lastAnswerIndex++;
        if (lastAnswerIndex >= shuffledAnswers.size()) {
            lastAnswerIndex = 0;
        }

        return shuffledAnswers.get(lastAnswerIndex);
    }
    ```
    **Примечание**: Нужно будет также сбрасывать `shuffledAnswers` (`shuffledAnswers = null;`), если пользователь изменит текст сообщений в настройках.
