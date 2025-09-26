# План портирования CookiesManager.cs

Файл `CookiesManager.cs` является центральным хранилищем cookie для кастомного прокси-сервера.

## Функциональность в C#

*   **Хранение**: Содержит `SortedDictionary`, который сопоставляет домен (хост) со списком cookie (`CookiePack`) для этого домена.
*   **`Assign`**: Метод для парсинга заголовка `Set-Cookie` и добавления новой cookie в хранилище.
*   **`Obtain`**: Метод для получения всех cookie для домена в виде готовой строки для HTTP-заголовка `Cookie`.
*   **`ClearGame`**: Метод для полной очистки всех сохраненных cookie.
*   **Специфичная логика**: Содержит важную проверку: при установке cookie `NeverNick` ее значение сравнивается с ником в профиле, и в случае несовпадения выбрасывается исключение.

## Решение для портирования на Android

**Портировать этот класс не нужно.**

Вся его основная функциональность полностью покрывается системным классом `android.webkit.CookieManager`. Единственная часть, требующая отдельной реализации — это специфичная проверка `NeverNick`.

## План реализации (вместо портирования)

1.  **Использовать `CookieManager`**: Вся работа с cookie должна выполняться через `CookieManager.getInstance()`, как описано в `TODO/todo_CookiePack.cs.md`.

2.  **Реализация проверки `NeverNick`**:
    Эту проверку необходимо встроить в кастомный `WebViewClient`.

    ```java
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // ... выполняем сетевой запрос с помощью OkHttp или другой библиотеки ...
        // Response response = httpClient.newCall(okHttpRequest).execute();

        // Получаем заголовки ответа от сервера
        Headers headers = response.headers();
        List<String> setCookieHeaders = headers.values("Set-Cookie");

        for (String cookieHeader : setCookieHeaders) {
            if (cookieHeader.trim().toLowerCase().startsWith("nevernick=")) {
                // Парсим значение cookie
                String nickValue = parseCookieValue(cookieHeader, "NeverNick");
                String decodedNick = "";
                try {
                    // В C# использовался HttpUtility.UrlDecode с windows-1251
                    decodedNick = URLDecoder.decode(nickValue, "windows-1251");
                } catch (UnsupportedEncodingException e) {
                    // ...
                }

                // Сравниваем с ником в профиле
                if (!decodedNick.equalsIgnoreCase(AppVars.Profile.getUserNick())) {
                    // Выбрасываем исключение или отправляем событие в UI
                    // для отображения ошибки "Неверное имя или пароль"
                    // Например, через Handler или EventBus
                    // return createErrorResponse();
                }
            }
        }

        // ... остальная логика: передача тела ответа в Filter.process и т.д. ...
    }
    ```

Таким образом, мы избавляемся от сложного ручного управления cookie, заменяя его системным механизмом, и точечно реализуем только ту бизнес-логику, которая уникальна для приложения.
