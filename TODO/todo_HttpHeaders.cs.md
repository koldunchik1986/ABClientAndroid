# План портирования классов HttpHeaders

Этот документ описывает группу классов, отвечающих за ручную реализацию HTTP-заголовков: `HttpHeaderItem.cs`, `HttpHeaders.cs`, `HttpRequestHeaders.cs`, `HttpResponseHeaders.cs`.

## Функциональность в C#

Эта группа классов была создана для того, чтобы кастомный прокси-сервер мог работать с HTTP-протоколом на низком уровне:

*   **Парсинг**: Разбирать сырой текстовый HTTP-запрос/ответ на составные части (метод, путь, версия, заголовки).
*   **Объектная модель**: Представлять эти части в виде объектов для удобного доступа и модификации (например, `headers["Content-Type"] = "text/html"`).
*   **Сборка**: Собирать объектное представление обратно в сырую текстовую строку для отправки по сокету.

## Решение для портирования на Android

**Портировать эти классы не нужно.**

Современные HTTP-клиенты для Android, такие как `OkHttp` (рекомендуется) или встроенный `HttpURLConnection`, предоставляют высокоуровневые объекты `Request` и `Response`, которые полностью инкапсулируют всю работу с заголовками и другими частями HTTP-сообщений.

## План реализации (вместо портирования)

При реализации сетевого клиента внутри `shouldInterceptRequest` необходимо использовать нативные объекты HTTP-библиотеки.

**Пример с использованием `OkHttp`:**

```java
// 1. Создание запроса
Request.Builder requestBuilder = new Request.Builder().url(request.getUrl().toString());

// Копирование заголовков из запроса WebView в запрос OkHttp
for (Map.Entry<String, String> entry : request.getRequestHeaders().entrySet()) {
    requestBuilder.addHeader(entry.getKey(), entry.getValue());
}

// Установка метода (GET, POST и т.д.)
if ("POST".equalsIgnoreCase(request.getMethod())) {
    // ... добавить тело запроса ...
    RequestBody requestBody = ...;
    requestBuilder.post(requestBody);
} else {
    requestBuilder.get();
}

Request okHttpRequest = requestBuilder.build();

// 2. Выполнение запроса и получение ответа
Response okHttpResponse = httpClient.newCall(okHttpRequest).execute();

// 3. Работа с ответом
int statusCode = okHttpResponse.code(); // аналог HttpResponseCode
String statusMessage = okHttpResponse.message(); // аналог HttpResponseStatus
Headers responseHeaders = okHttpResponse.headers(); // аналог коллекции заголовков
String contentType = responseHeaders.get("Content-Type");
ResponseBody responseBody = okHttpResponse.body();
byte[] bodyBytes = responseBody.bytes();
```

Как видно из примера, вся низкоуровневая работа по парсингу и сборке строк полностью отсутствует. Мы работаем с удобными и типизированными объектами, что значительно упрощает код и снижает вероятность ошибок.
