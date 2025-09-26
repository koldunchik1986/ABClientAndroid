# План портирования ServerChatter.cs

Файл `ServerChatter.cs` является низкоуровневым компонентом кастомного прокси-сервера. Он отвечает за взаимодействие с удаленным веб-сервером (например, `neverlands.ru`).

## Функциональность в C#

Этот класс является "зеркалом" `ClientChatter.cs`:

*   **Установка соединения (`ConnectToHost`)**: Получает IP-адрес хоста через `DNSResolver` и устанавливает с ним TCP-соединение.
*   **Отправка запроса (`ResendRequest`)**: Берет объект `HttpRequestHeaders`, собирает его обратно в текстовую HTTP-спецификацию и отправляет на удаленный сервер.
*   **Чтение ответа (`ReadResponse`)**: Асинхронно читает ответ от сервера в поток байт.
*   **Парсинг ответа (`ParseResponseForHeaders`)**: Парсит заголовки ответа и создает объект `HttpResponseHeaders`.
*   **Потоковая передача (`LeakResponseBytes`)**: Реализует механизм "протекания" (leaking), когда байты ответа сразу пересылаются клиенту, не дожидаясь полной загрузки. Это полезно для больших файлов.

## Решение для портирования на Android

**Портировать этот класс не нужно.**

Вся его функциональность по установке соединения, отправке запроса, чтению и парсингу ответа полностью покрывается любой современной HTTP-библиотекой для Android, например, `OkHttp`.

## План реализации (вместо портирования)

Вся логика `ServerChatter.cs` заменяется несколькими строчками кода с использованием `OkHttp` внутри `shouldInterceptRequest`.

```java
// 1. Создаем клиент (один раз для всего приложения)
OkHttpClient httpClient = new OkHttpClient.Builder().build();

// 2. Создаем запрос (как описано в todo_HttpHeaders.cs.md)
Request okHttpRequest = ...;

// 3. Выполняем запрос. Этот один вызов заменяет собой
// ConnectToHost, ResendRequest, ReadResponse, ParseResponseForHeaders.
Response okHttpResponse;
try {
    okHttpResponse = httpClient.newCall(okHttpRequest).execute();
} catch (IOException e) {
    // Обработка ошибок сети
    return createErrorResponse();
}

// 4. Получаем тело ответа
// Этот вызов заменяет TakeEntity и логику буферизации.
// OkHttp сам управляет потоками и памятью.
ResponseBody body = okHttpResponse.body();
byte[] responseBytes = (body != null) ? body.bytes() : new byte[0];
```

Таким образом, вместо сотен строк низкоуровневого кода по управлению сокетами и парсингу, мы используем несколько вызовов высокоуровневой, надежной и оптимизированной библиотеки.
