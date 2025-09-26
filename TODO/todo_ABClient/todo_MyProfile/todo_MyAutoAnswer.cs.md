### 1. План портирования MyAutoAnswer.cs

Файл `MyAutoAnswer.cs` инкапсулирует логику и настройки для функции автоответчика. Он отвечает за хранение, выбор и выдачу фраз для ответа.

### 2. Функциональность в C#

- **Назначение:** Управлять списком фраз для автоответа и выдавать их в псевдослучайном порядке без немедленных повторений.
- **Хранение данных:**
    - `StrAnswers`: Строка, содержащая все фразы, разделенные `Environment.NewLine`.
    - `m_Answers`: Массив, получаемый из `StrAnswers` путем разделения по строкам.
    - `m_PrepAutoAnswers`: Массив *индексов*, который создается один раз и затем перемешивается (shuffle). Это гарантирует, что все ответы будут показаны по одному разу в случайном порядке, прежде чем цикл начнется заново.
    - `m_LastAutoAnswer`: Индекс, указывающий на текущую позицию в перемешанном массиве индексов `m_PrepAutoAnswers`.
- **Логика (`GetNextAnswer`):**
    1.  Если список ответов изменился, заново создает и перемешивает массив индексов `m_PrepAutoAnswers`.
    2.  Инкрементирует `m_LastAutoAnswer` для получения следующего *индекса* из `m_PrepAutoAnswers`.
    3.  Возвращает ответ из `m_Answers` по этому полученному индексу.
    4.  Когда доходит до конца списка, обнуляет счетчик, но **не перемешивает список заново** до тех пор, пока сам список фраз не изменится.
- **Сериализация (`Read`/`Write`):** Сохраняет в XML-профиль флаг `active` и строку `answers`, в которой все `Environment.NewLine` заменены на `[BR]` для корректного хранения в атрибуте XML.

### 3. Решение для портирования на Android

Логика должна быть выделена в отдельный сервис/провайдер, а настройки должны храниться в `SharedPreferences`. Необходимо в точности воспроизвести алгоритм "перемешать и пройти по циклу", чтобы поведение автоответчика не изменилось.

- **Архитектура:**
    - **`SettingsRepository`:** Будет хранить настройки: `isAutoAnswerEnabled: Boolean` и `autoAnswerText: String`.
    - **`AutoAnswerProvider`:** Синглтон-объект (`object` в Kotlin), который будет инкапсулировать состояние (список ответов, перемешанные индексы, текущий индекс) и логику `GetNextAnswer`.
- **UI:** Настройки будут вынесены на экран настроек приложения (`PreferenceFragmentCompat`).

### 4. План реализации

1.  **Добавить свойства в `SettingsRepository.kt`:**
    - [ ] `var isAutoAnswerEnabled: Boolean` (сохраняется в `SharedPreferences` по ключу `autoanswer_enabled`).
    - [ ] `var autoAnswerText: String` (сохраняется в `SharedPreferences` по ключу `autoanswer_text`).

2.  **Создать `AutoAnswerProvider.kt`:**
    - [ ] Создать `object AutoAnswerProvider`.
    - [ ] Объявить в нем приватные переменные для хранения состояния:
        ```kotlin
        private var sourceText: String = ""
        private var answers: List<String> = emptyList()
        private var shuffledIndices: List<Int> = emptyList()
        private var currentIndex: Int = -1
        ```
    - [ ] Реализовать метод `fun getNextAnswer(): String`:
        - Внутри метода, получить актуальный список фраз из `SettingsRepository.autoAnswerText`.
        - **Проверить, не изменился ли исходный текст:** `if (rawAnswers != sourceText)`.
        - **Если изменился (или при первом запуске):**
            - `sourceText = rawAnswers`
            - `answers = rawAnswers.split(Regex("\r?\n")).filter { it.isNotBlank() }`
            - `shuffledIndices = answers.indices.shuffled()`
            - `currentIndex = -1`
        - **Если `answers` пуст**, вернуть пустую строку.
        - **Инкрементировать `currentIndex`**. Если `currentIndex >= shuffledIndices.size`, то `currentIndex = 0`.
        - Получить индекс ответа: `val answerIndex = shuffledIndices[currentIndex]`.
        - Вернуть `answers[answerIndex]`.

3.  **Создать UI настроек:**
    - [ ] В `res/xml/` создать файл настроек чата.
    - [ ] Добавить `SwitchPreferenceCompat` с `app:key="autoanswer_enabled"` и `app:title="Включить автоответчик"`.
    - [ ] Добавить `EditTextPreference` с `app:key="autoanswer_text"`, `app:title="Фразы для автоответчика"` и диалогом, позволяющим вводить многострочный текст.

4.  **Интеграция:**
    - [ ] В коде, который обрабатывает получение приватных сообщений, добавить проверку `if (SettingsRepository.isAutoAnswerEnabled)`.
    - [ ] Если `true`, вызывать `AutoAnswerProvider.getNextAnswer()` и отправлять полученную фразу в ответ.
