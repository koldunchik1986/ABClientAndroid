# План портирования HelperConverters.cs

Файл `HelperConverters.cs` содержит набор статических утилит для форматирования строк, времени и кодирования URL.

## Функциональность в C#

Активные (не закомментированные) методы класса выполняют следующие задачи:

*   **Форматирование времени**: `TimeIntervalToNow`, `TimeSpanToString`, `MinsToStr` — преобразуют временные интервалы (в тиках, `TimeSpan` или минутах) в человекочитаемые строки формата `(Ч:ММ:СС)`.
*   **Кодирование/декодирование ников**: `NickEncode`, `NickDecode`, `AddressEncode` — выполняют специфичное для игры кодирование ников для использования в URL. Это включает замену некоторых символов (`+` <-> `|`) и вызов `HttpUtility.UrlEncode/UrlDecode` с кодировкой `windows-1251`.

## План портирования на Android

Функциональность этого класса полезна и **должна быть портирована** для корректного отображения времени и формирования URL.

1.  **Создать `ConverterUtils.java`** в пакете `ru.neverlands.abclient.utils`.

2.  **Реализовать методы для форматирования времени**:
    ```java
    import java.util.concurrent.TimeUnit;

    public static String timeSpanToString(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        if (hours > 0) {
            return String.format(Locale.US, "(%d:%02d:%02d)", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "(%d:%02d)", minutes, seconds);
        }
    }

    public static String minsToStr(int mins) {
        return "(" + (mins / 60) + ":" + String.format(Locale.US, "%02d", (mins % 60)) + ":00)";
    }
    ```

3.  **Реализовать методы для кодирования/декодирования**:
    ```java
    import java.net.URLDecoder;
    import java.net.URLEncoder;

    public static String nickEncode(String nick) {
        if (nick == null) return null;
        try {
            String s1 = nick.replace('+', '|');
            String s2 = URLEncoder.encode(s1, Russian.CODEPAGE.name());
            String s3 = s2.replace("+", "%20");
            return s3.replace("%7C", "%2B"); // %7C - это | 
        } catch (UnsupportedEncodingException e) {
            return nick; // fallback
        }
    }

    public static String nickDecode(String nick) {
        if (nick == null) return null;
        try {
            String s = nick.replace('+', ' ');
            String decoded = URLDecoder.decode(s, Russian.CODEPAGE.name());
            // ... и так далее, воспроизвести все замены
            return decoded.replace("|", " ").replace("%20", " ");
        } catch (UnsupportedEncodingException e) {
            return nick; // fallback
        }
    }
    ```
    **Важно:** Необходимо очень точно воспроизвести всю цепочку замен (`replace`) из оригинальных методов, чтобы кодирование и декодирование работало идентично C#-версии.
