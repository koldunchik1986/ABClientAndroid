# План портирования Russian.cs

Файл `Russian.cs` — это критически важный класс-хелпер, который обеспечивает правильную работу с русской кодировкой и культурой.

## Функциональность в C#

Класс содержит два статических поля:

*   **`Codepage`**: Хранит объект `System.Text.Encoding`, инициализированный для кодировки `windows-1251` (CP1251). Игровой сервер NeverLands использует именно эту кодировку для всего текста. Этот объект используется по всему приложению для корректного преобразования байтовых массивов, полученных от сервера, в читаемые строки, и наоборот.
*   **`Culture`**: Хранит объект `System.Globalization.CultureInfo` для `ru-RU`. Это используется для правильного форматирования дат и чисел в соответствии с русскими стандартами.

## План портирования на Android

**Портирование этого класса обязательно.** Без правильной работы с кодировкой `windows-1251` все тексты в игре будут нечитаемыми ("кракозябрами").

1.  **Создать `Russian.java`** в пакете `ru.neverlands.abclient.utils`.
2.  **Реализовать класс**:
    ```java
    import java.nio.charset.Charset;
    import java.util.Locale;

    public final class Russian {
        // Приватный конструктор, чтобы предотвратить создание экземпляров
        private Russian() { }

        /**
         * Кодировка Windows-1251, используемая игровым сервером.
         */
        public static final Charset CODEPAGE = Charset.forName("windows-1251");

        /**
         * Русская локаль для форматирования дат и чисел.
         */
        public static final Locale CULTURE = new Locale("ru", "RU");
    }
    ```

3.  **Использование в коде**:
    *   **C#:** `Russian.Codepage.GetString(bytes)`
    *   **Java:** `new String(bytes, Russian.CODEPAGE)`

    *   **C#:** `Russian.Codepage.GetBytes(str)`
    *   **Java:** `str.getBytes(Russian.CODEPAGE)`

    *   **C#:** `someDate.ToString(Russian.Culture)`
    *   **Java:** `SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Russian.CULTURE); sdf.format(someDate);`

Корректная реализация и повсеместное использование этого класса обеспечат правильное отображение всех текстов в приложении.
