# План портирования CookiePack.cs и CookiePackItem.cs

Файлы `CookiePack.cs` и `CookiePackItem.cs` являются вспомогательными классами для ручного управления HTTP-cookie внутри кастомного прокси-сервера.

## Функциональность в C#

*   **`CookiePackItem`**: Простой класс-контейнер для хранения пары "имя-значение" одной cookie.
*   **`CookiePack`**: Коллекция объектов `CookiePackItem`. Основная задача — собрать все cookie в единую строку формата `name1=value1; name2=value2`, пригодную для использования в HTTP-заголовке `Cookie`.

**Назначение:**

Прокси-сервер вручную парсил `Set-Cookie` заголовки из ответов сервера, сохранял их в `CookiePack` и прикреплял к последующим запросам. Это была ручная реализация механизма управления cookie.

## Решение для портирования на Android

**Портировать эти классы не нужно.**

В Android есть встроенный системный менеджер cookie, который автоматически работает с `WebView`.

*   **`android.webkit.CookieManager`**: Это синглтон, который обеспечивает персистентное хранилище cookie для всех `WebView` в приложении. Он автоматически парсит `Set-Cookie` заголовки и добавляет `Cookie` к исходящим запросам. Он делает всю работу, которую в C#-версии выполняли `CookiePack`, `CookiesManager` и другие связанные классы.

## План реализации (вместо портирования)

Необходимо убедиться, что `CookieManager` правильно настроен для работы с `WebView`.

1.  **Включение поддержки cookie**:
    При настройке `WebView` нужно убедиться, что cookie включены (обычно это так по умолчанию).
    ```java
    CookieManager cookieManager = CookieManager.getInstance();
    cookieManager.setAcceptCookie(true);
    // Для поддержки сторонних cookie (если необходимо)
    cookieManager.setAcceptThirdPartyCookies(myWebView, true);
    ```

2.  **Ручные операции (если нужны)**:
    Если в какой-то части приложения потребуется вручную прочитать или установить cookie, нужно использовать методы `CookieManager`:
    *   `cookieManager.getCookie(url)` — получить все cookie для URL.
    *   `cookieManager.setCookie(url, value)` — установить cookie.
    *   `cookieManager.removeAllCookies(callback)` — очистить все cookie.
    *   `cookieManager.flush()` — синхронизировать cookie из памяти на диск.

Использование системного `CookieManager` — это правильный, эффективный и единственно верный подход в Android, который полностью заменяет всю кастомную логику управления cookie из папки `ABProxy`.
