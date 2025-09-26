# План портирования HelperErrors.cs

Файл `HelperErrors.cs` — это утилитарный класс для генерации стандартных HTML-заголовков для страниц, создаваемых самим клиентом.

## Функциональность в C#

*   **`Head()`**: Генерирует строку, содержащую `<html><head>...` со стандартными мета-тегами (для отключения кэширования) и встроенными CSS-стилями. Этот заголовок используется как основа для всех HTML-страниц, которые генерирует клиент (например, страницы с ошибками, страницы редиректов).
*   **`Marker()`**: Генерирует `<span>` с названием приложения, который вставляется в начало каждой сгенерированной страницы, помечая ее как созданную клиентом.

## План портирования на Android

Это простая, но полезная утилита, которую **следует портировать** для унификации генерируемых HTML-ответов.

1.  **Создать `HtmlUtils.java`** в пакете `ru.neverlands.abclient.utils` (если он еще не существует).
2.  **Реализовать статические методы**:
    ```java
    public final class HtmlUtils {

        private static final String HTML_HEAD;
        private static final String HTML_MARKER;

        static {
            HTML_MARKER = "<SPAN class=massm>&nbsp;" + AppConsts.APPLICATION_NAME + "&nbsp;</SPAN> ";
            
            StringBuilder sb = new StringBuilder();
            sb.append("<html><head>");
            sb.append("<META Http-Equiv=\"Cache-Control\" Content=\"No-Cache\">");
            sb.append("<META Http-Equiv=\"Pragma\" Content=\"No-Cache\">");
            sb.append("<META Http-Equiv=\"Expires\" Content=\"0\">");
            sb.append("<style type=\"text/css\">" +
                      "body {font-family:Tahoma, Verdana, Arial; font-size:11px; color:black; background-color:white;}" +
                      ".massm { color:white; background-color:#003893; }" +
                      ".gray { color:gray; }" +
                      "</style>");
            sb.append("</head><body>");
            sb.append(HTML_MARKER);
            HTML_HEAD = sb.toString();
        }

        public static String getHead() {
            return HTML_HEAD;
        }

        public static String getMarker() {
            return HTML_MARKER;
        }

        public static String buildPage(String bodyContent) {
            return getHead() + bodyContent + "</body></html>";
        }
    }
    ```
3.  **Использование**: Везде в коде, где нужно будет сгенерировать кастомный HTML для `WebResourceResponse`, можно будет использовать `HtmlUtils.buildPage("...")`.

