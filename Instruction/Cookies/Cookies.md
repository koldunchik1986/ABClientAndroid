# Анализ и архитектура управления Cookies

## 1. Общая схема

Управление cookies в приложении представляет собой **гибридную модель с ручной передачей**. Cookies не синхронизируются автоматически между HTTP-клиентом (`OkHttp` в `AuthManager`) и `WebView`. Процесс состоит из трех основных этапов:

1.  **Получение:** `AuthManager` получает сессионные cookies от сервера.
2.  **Передача:** Cookies передаются из `AuthManager` в `MainActivity` через глобальную переменную.
3.  **Внедрение:** `MainActivity` вручную внедряет cookies в системный `android.webkit.CookieManager`, делая их доступными для `WebView`.

## 2. Пошаговый путь Cookie

### Шаг 1: Получение в `AuthManager`
-   **Файл:** `AuthManager.java`
-   **Механизм:**
    -   Для выполнения запросов создается временный `OkHttpClient`.
    -   Этому клиенту назначается `JavaNetCookieJar`, который использует временный экземпляр `java.net.CookieManager`.
    -   В ходе 3-шаговой авторизации все cookies от сервера (`Set-Cookie` заголовки) автоматически сохраняются в этом временном `cookieManager`.

### Шаг 2: Извлечение и передача в `LoginActivity`
-   **Файл:** `AuthManager.java`
-   **Механизм:**
    -   После успешной авторизации, `AuthManager` извлекает все накопленные cookies из хранилища:
        ```java
        List<java.net.HttpCookie> cookies = cookieManager.getCookieStore().get(HttpUrl.get("http://neverlands.ru/").uri());
        ```
    -   Этот `List<java.net.HttpCookie>` передается в `LoginActivity` через callback:
        ```java
        callback.onSuccess(cookies);
        ```

### Шаг 3: Сохранение в `AppVars`
-   **Файл:** `LoginActivity.java`
-   **Механизм:**
    -   В методе `onSuccess` `LoginActivity` немедленно сохраняет полученный список cookies в статическую глобальную переменную:
        ```java
        AppVars.lastCookies = cookies;
        ```
    -   Сразу после этого запускается `MainActivity`.

### Шаг 4: Внедрение в `CookieManager` в `MainActivity`
-   **Файл:** `MainActivity.java`
-   **Механизм:**
    -   В методе `setupWebViews()`, который вызывается из `onCreate()`, происходит финальный и самый важный шаг.
    -   Код проверяет, что `AppVars.lastCookies` не пуст.
    -   **Фильтрация дубликатов:** Код итерирует список cookies в обратном порядке, чтобы сохранить только последнее значение для каждого имени cookie (например, для `watermark`), избегая дублирования.
    -   **Внедрение:** Каждый отфильтрованный cookie форматируется в строку (`"имя=значение; domain=домен"`) и устанавливается в системный `android.webkit.CookieManager`:
        ```java
        CookieManager cookieManager = CookieManager.getInstance();
        String url = "http://neverlands.ru";
        for (java.net.HttpCookie cookie : filteredCookies) {
            String cookieString = cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain();
            cookieManager.setCookie(url, cookieString);
        }
        cookieManager.flush(); // Принудительное сохранение cookies на диск
        ```
    -   После внедрения глобальная переменная очищается: `AppVars.lastCookies = null;`.

### Шаг 5: Использование в `WebView`
-   **Файл:** `MainActivity.java` (`CustomWebViewClient`)
-   **Механизм:**
    -   Когда `shouldInterceptRequest` перехватывает запрос, он обращается к `CookiesManager.obtain(url)`.
    -   `CookiesManager` — это простая обертка, которая вызывает `android.webkit.CookieManager.getInstance().getCookie(url)`.
    -   Так как на Шаге 4 мы уже наполнили системный `CookieManager` нужными сессионными cookies, `shouldInterceptRequest` получает их и может добавить в заголовок `Cookie` своего `HttpURLConnection`.

## 3. Ключевые классы и переменные

-   `AuthManager`: Источник cookies.
-   `LoginActivity`: Посредник, который перекладывает cookies в `AppVars`.
-   `AppVars.lastCookies`: `List<java.net.HttpCookie>`, временное статическое хранилище для передачи cookies между `Activity`.
-   `MainActivity`: Конечный получатель, ответственный за инъекцию в системный `CookieManager`.
-   `android.webkit.CookieManager`: Системное хранилище cookies для всех `WebView` в приложении.
-   `CookiesManager`: Вспомогательный класс для удобного получения строки cookies из системного `CookieManager`.
