# Анализ и архитектура `AuthManager.java`

**Файл:** `app/src/main/java/ru/neverlands/abclient/AuthManager.java`

## 1. Назначение

`AuthManager` — это статический класс-утилита, отвечающий за выполнение полной последовательности сетевых запросов для авторизации пользователя на сервере `neverlands.ru`. Он инкапсулирует всю логику HTTP-взаимодействия, обработки cookies и обработки ошибок, связанных с процессом входа.

## 2. Алгоритм авторизации

Процесс авторизации, реализованный в методе `authorize`, является **трехэтапным** и выполняется в отдельном фоновом потоке (`ExecutorService`).

### Зависимости
-   `okhttp3.OkHttpClient`: Основной HTTP-клиент для выполнения запросов.
-   `okhttp3.JavaNetCookieJar`: Обработчик cookies, который хранит их в стандартном `java.net.CookieManager` в памяти на время выполнения запросов.
-   `ru.neverlands.abclient.utils.DebugLogger`: Класс для логирования процесса авторизации.

### Шаг 1: `GET` запрос на `http://neverlands.ru/`
-   **Назначение:** Получить первоначальные cookies от сервера, в частности `watermark`. Этот шаг необходим для инициализации сессии.
-   **Метод:** `GET`
-   **URL:** `http://neverlands.ru/`
-   **Результат:** Cookies, полученные в этом запросе, автоматически сохраняются в экземпляре `cookieManager` благодаря `JavaNetCookieJar`.

### Шаг 2: `POST` запрос на `http://neverlands.ru/game.php`
-   **Назначение:** Отправка учетных данных пользователя (логин и пароль) для фактической аутентификации.
-   **Метод:** `POST`
-   **URL:** `http://neverlands.ru/game.php`
-   **Тело запроса (`RequestBody`):**
    -   Тип: `okhttp3.FormBody`
    -   **Кодировка:** `windows-1251`
    -   **Параметры:**
        -   `player_nick`: Имя пользователя.
        -   `player_password`: Пароль.
-   **Проверка ответа:** Код анализирует тело ответа. Если оно содержит строку `"auth_form"`, это означает, что введен неверный логин или пароль, и выбрасывается исключение `IOException`.

### Шаг 3: `GET` запрос на `http://neverlands.ru/main.php`
-   **Назначение:** Финализация сессии. Этот запрос, используя уже полученные на предыдущих шагах сессионные cookies, окончательно подтверждает вход и загружает основную игровую страницу.
-   **Метод:** `GET`
-   **URL:** `http://neverlands.ru/main.php`

## 3. Управление Cookies

-   **Хранилище:** `AuthManager` создает временный, изолированный экземпляр `java.net.CookieManager` для каждой операции авторизации.
-   **Передача:** Cookies **не синхронизируются** автоматически с `WebView`. После успешного выполнения всех трех шагов, `AuthManager` вручную извлекает все cookies из своего `cookieManager`:
    ```java
    List<java.net.HttpCookie> cookies = cookieManager.getCookieStore().get(HttpUrl.get("http://neverlands.ru/").uri());
    ```
-   **Callback:** Этот список (`List<java.net.HttpCookie>`) передается в вызывающий код (в `LoginActivity`) через метод `callback.onSuccess(cookies)`.

## 4. Обработка результатов и ошибок

-   **`AuthCallback`:** Внутренний интерфейс для асинхронной обработки результата.
    -   `onSuccess(List<java.net.HttpCookie> cookies)`: Вызывается в UI-потоке (`Handler`) после успешного завершения всех трех шагов.
    -   `onFailure(String message)`: Вызывается в UI-потоке в случае любой сетевой ошибки или если сервер вернул страницу с ошибкой авторизации.

## 5. Общая схема взаимодействия

1.  `LoginActivity` вызывает `AuthManager.authorize(...)`.
2.  `AuthManager` в фоновом потоке выполняет 3-шаговую HTTP-аутентификацию.
3.  `AuthManager` собирает сессионные cookies.
4.  `AuthManager` вызывает `onSuccess`, передавая cookies обратно в `LoginActivity`.
5.  **(Предположение)** `LoginActivity` или `MainActivity` должны взять этот список cookies и вручную внедрить их в `android.webkit.CookieManager`, чтобы `WebView` мог их использовать.
