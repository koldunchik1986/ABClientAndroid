# План портирования ClientChatter.cs

Файл `ClientChatter.cs` является низкоуровневым компонентом кастомного прокси-сервера. Его единственная задача — читать данные из сокета, подключенного к клиенту (`WebBrowser`), и парсить их в полноценный HTTP-запрос.

## Функциональность в C#

*   **Чтение из сокета**: Асинхронно считывает байты из `ClientSocket`.
*   **Парсинг HTTP**: Находит конец заголовков (по `CRLFCRLF`), парсит стартовую строку (метод, путь, версия HTTP) и все заголовки (Host, Content-Length и т.д.).
*   **Определение конца запроса**: Проверяет, полностью ли получено тело запроса (на основе `Content-Length` или `chunked` кодирования).
*   **Обработка ошибок**: Если запрос имеет неверный формат, генерирует и отправляет клиенту страницу с ошибкой `400 Bad Request`.

## Решение для портирования на Android

**Портировать этот класс не нужно.**

Вся эта низкоуровневая работа по чтению из сокета и парсингу HTTP-запроса полностью абстрагирована в Android `WebView`.

При использовании `WebViewClient.shouldInterceptRequest(WebView view, WebResourceRequest request)`, мы получаем на вход уже готовый, полностью сформированный объект `WebResourceRequest`. Этот объект предоставляет всю ту же информацию, которую с таким трудом парсит `ClientChatter.cs`:

*   `request.getUrl()`: Полный URL запроса.
*   `request.getMethod()`: HTTP-метод (`GET`, `POST` и т.д.).
*   `request.getRequestHeaders()`: `Map<String, String>` со всеми заголовками запроса.
*   `request.isForMainFrame()`: Является ли запрос загрузкой основного фрейма.

Таким образом, вся функциональность `ClientChatter.cs` заменяется использованием готового API `WebResourceRequest`.

## План реализации (вместо портирования)

В классе `MyWebViewClient` (наследнике `WebViewClient`), в методе `shouldInterceptRequest` необходимо использовать данные из объекта `request` для формирования нового, исходящего запроса к игровому серверу.

```java
@Override
public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
    Uri url = request.getUrl();
    String method = request.getMethod();
    Map<String, String> headers = request.getRequestHeaders();

    // 1. Используя эти данные, формируем и выполняем собственный HTTP-запрос
    //    (например, с помощью OkHttp).
    // ...

    // 2. Получаем ответ от сервера.
    // ...

    // 3. Передаем ответ в наш Filter.process(...).
    // ...

    // 4. Возвращаем WebResourceResponse.
    // ...
}
```
