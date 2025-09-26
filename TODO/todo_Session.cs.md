# План портирования Session.cs

Файл `Session.cs` является центральным классом, "сердцем" всего кастомного прокси-сервера. Экземпляр этого класса создается для каждой HTTP-транзакции и управляет всем ее жизненным циклом.

## Функциональность в C#

Метод `Execute()` этого класса представляет собой конвейер обработки HTTP-запроса:

1.  **Чтение запроса**: Использует `ClientChatter` для чтения и парсинга запроса от браузера.
2.  **Пре-фильтрация**: Вызывает `Filter.PreProcess`.
3.  **Проверка кэша**: Обращается к `Cache` для поиска готового ответа.
4.  **Сетевой запрос**: Если в кэше ничего нет, использует `ServerChatter` для отправки запроса на удаленный сервер и получения ответа.
5.  **Декодирование**: Распаковывает `gzip/deflate` контент.
6.  **Обработка Cookie**: Сохраняет `Set-Cookie` заголовки через `CookiesManager`.
7.  **Основная фильтрация**: Передает тело ответа в `Filter.Process` для модификации.
8.  **Сохранение в кэш**: Сохраняет полученный ответ в `Cache`.
9.  **Ответ клиенту**: Отправляет финальный, обработанный ответ обратно в браузер.
10. **Управление соединением**: Управляет `Keep-Alive` соединениями для повторного использования.

## Решение для портирования на Android

**Портировать этот класс не нужно.**

Вся его сложная оркестровая логика будет реализована внутри одного метода — `shouldInterceptRequest` нашего кастомного `WebViewClient`.

## План реализации (вместо портирования)

Метод `shouldInterceptRequest` станет аналогом метода `Session.Execute`. Он будет выполнять ту же последовательность действий, но с использованием современных Android-библиотек.

```java
@Nullable
@Override
public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
    // 1. Получаем URL, метод, заголовки из 'request'
    Uri url = request.getUrl();
    String method = request.getMethod();
    Map<String, String> headers = request.getRequestHeaders();

    // 2. Пре-фильтрация (если нужна)
    // byte[] modifiedRequestBody = Filter.preProcess(url.toString(), ...);

    // 3. Проверка кэша (выполняется WebView автоматически до вызова этого метода,
    //    если установлен Cache-Control. Ручная проверка не нужна).

    // 4. Сетевой запрос с помощью OkHttp
    Request okHttpRequest = buildOkHttpRequest(request, modifiedRequestBody);
    Response okHttpResponse;
    try {
        okHttpResponse = httpClient.newCall(okHttpRequest).execute();
    } catch (IOException e) {
        return createErrorResponse(); // Возвращаем страницу с ошибкой
    }

    // 5. Декодирование (выполняется OkHttp автоматически)

    // 6. Обработка Cookie (выполняется CookieManager автоматически, 
    //    но мы можем перехватить заголовки для проверки NeverNick)
    checkNeverNickCookie(okHttpResponse.headers());

    // 7. Основная фильтрация
    byte[] originalBody = okHttpResponse.body().bytes();
    byte[] modifiedBody = Filter.process(url.toString(), originalBody);

    // 8. Сохранение в кэш (выполняется WebView автоматически после получения ответа,
    //    если настроены заголовки Cache-Control).

    // 9. Ответ клиенту (WebView)
    return new WebResourceResponse(
        getMimeType(okHttpResponse), // e.g., "text/html"
        getEncoding(okHttpResponse), // e.g., "windows-1251"
        new ByteArrayInputStream(modifiedBody)
    );
}
```

Таким образом, сложная, запутанная и низкоуровневая логика `Session.cs` заменяется на чистый, линейный и высокоуровневый код внутри одного метода, стандартного для платформы Android.
