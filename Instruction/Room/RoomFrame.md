# Система отображения фрейма комнаты

## Обзор

Эта инструкция описывает новую систему отображения списка персонажей в комнате. Новая система была разработана для решения проблемы, из-за которой список персонажей не отображался в `WebView`.

## Компоненты

Новая система состоит из следующих компонентов:

*   **`Filter.java`**: Этот класс перехватывает HTTP-запросы и направляет их на соответствующую обработку.
*   **`ChRoomPhp.java`**: Этот класс обрабатывает ответ от `ch.php?lo=1`. Он генерирует HTML-список персонажей и вставляет его в исходный HTML.
*   **`RoomManager.java`**: Этот класс содержит логику для парсинга `ChatListU` и генерации HTML-кода для списка персонажей.
*   **`ChListJs.java`**: Этот класс возвращает `ch_list.js` из `assets`.
*   **`MainActivity.java`**: Основная активность приложения.

## Процесс

1.  `WebView` запрашивает `http://neverlands.ru/ch.php?lo=1`.
2.  `Filter.java` перехватывает запрос и вызывает `ChRoomPhp.process()`.
3.  `ChRoomPhp.process()` получает HTML-ответ от `ch.php?lo=1`.
4.  `ChRoomPhp.process()` вызывает `RoomManager.process()` для генерации HTML-списка персонажей.
5.  `RoomManager.process()` парсит `ChatListU` из HTML и генерирует HTML-код для каждого персонажа.
6.  `ChRoomPhp.process()` вставляет сгенерированный HTML-список в исходный HTML.
7.  `ChRoomPhp.process()` возвращает измененный HTML в `Filter.java`.
8.  `Filter.java` возвращает измененный HTML в `WebView`.
9.  `WebView` отображает измененный HTML, который теперь содержит список персонажей.

## Код

### `Filter.java`

```java
// Filter.java
if (address.contains("ch.php?lo=1")) {
    return ChRoomPhp.process(array);
}
```

### `ChRoomPhp.java`

```java
// ChRoomPhp.java
public class ChRoomPhp {
    public static byte[] process(byte[] array) {
        Log.d("ChRoomPhp", "Input length: " + array.length);
        String originalHtml = Russian.getString(array);

        // 1. Генерируем наш список игроков
        String generatedPlayerList = RoomManager.process(null, originalHtml);

        // 2. Находим контейнер в оригинальном HTML и заменяем его
        String startTag = "<font class=\"placename\">";
        String endTag = "</font>";

        int startIndex = originalHtml.indexOf(startTag);
        int endIndex = originalHtml.indexOf(endTag, startIndex);

        if (startIndex != -1 && endIndex != -1) {
            String before = originalHtml.substring(0, startIndex + startTag.length());
            String after = originalHtml.substring(endIndex);
            originalHtml = before + generatedPlayerList + after;
        } else {
            // Fallback: добавляем в конец body
            originalHtml = originalHtml.replace("</body>", generatedPlayerList + "</body>");
        }

        byte[] result = Russian.getBytes(originalHtml);
        Log.d("ChRoomPhp", "Output length: " + result.length);
        return result;
    }
}
```

### `RoomManager.java`

```java
// RoomManager.java
public class RoomManager {
    // Вместо этого, метод process должен просто вернуть сгенерированный список
    public static String process(Context context, String html) {
        FilterProcRoomResult filterResult = FilterProcRoom(html);
        // Здесь можно добавить логику для генерации выпадающего списка навигации и кнопки свитка
        // и вставить их в filterResult.html
        return filterResult.html; // Возвращаем ТОЛЬКО сгенерированный список игроков
    }

    public static String HtmlChar(String schar) {
        // ... (логика генерации HTML для одного персонажа)
    }

    public static FilterProcRoomResult FilterProcRoom(String html) {
        // ... (логика парсинга ChatListU)
    }
}
```
