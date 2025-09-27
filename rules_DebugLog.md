# Правила и описание процесса логирования

## 1. Введение

Этот документ описывает систему детального логирования, реализованную для отладки процесса авторизации и загрузки контента. Основной инструмент — класс `DebugLogger`, который записывает отладочную информацию в специальный лог-файл.

## 2. Расположение и формат лог-файла

-   **Расположение:** Логи сохраняются во внутренней директории приложения по пути: `[App Data]/files/Logs/`
-   **Именование файла:** `Log_{timestamp}.txt`, где `timestamp` формируется по шаблону `yyyyMMdd_HHmmss`. Пример: `Log_20250927_101545.txt`.
-   **Формат записи:** Каждая запись в логе имеет формат `{timestamp}: {message}`. `timestamp` имеет формат `yyyy-MM-dd HH:mm:ss.SSS` для максимальной точности.

## 3. Процесс логирования: пошаговое описание

Ниже описаны ключевые точки в коде, где происходит логирование, и какая информация записывается.

### Этап 1: Управление Cookies

-   **Класс:** `ru.neverlands.abclient.WebViewCookieJar`
-   **Цель:** Отследить, какие cookies сохраняются и загружаются для каждого сетевого запроса.

    -   **Метод: `saveFromResponse(HttpUrl url, List<Cookie> cookies)`**
        -   **Что логгируется:** URL, с которого пришли cookies, и полный список сохраняемых cookies.
        -   **Пример лога:**
            ```
            2025-09-27 10:15:44.500: WebViewCookieJar: Saving 2 cookies for http://neverlands.ru
            2025-09-27 10:15:44.501:   -> PHPSESSID=...; path=/
            2025-09-27 10:15:44.502:   -> other_cookie=...; path=/
            ```

    -   **Метод: `loadForRequest(HttpUrl url)`**
        -   **Что логгируется:** URL, для которого запрашиваются cookies, и сырая строка cookies, полученная из `CookieManager`.
        -   **Пример лога:**
            ```
            2025-09-27 10:15:44.600: WebViewCookieJar: Loading cookies for http://neverlands.ru/game.php
            2025-09-27 10:15:44.601:   -> Raw cookies: PHPSESSID=...; other_cookie=...
            ```

### Этап 2: Процесс авторизации

-   **Класс:** `ru.neverlands.abclient.AuthManager`
-   **Цель:** Задокументировать каждый шаг HTTP-запросов в процессе входа.

    -   **Метод: `authorize(...)`**
        1.  **Начало:** Логгируется начало процесса и факт очистки cookies.
        2.  **Запрос 1 (Initial GET):** Логгируется URL и заголовки первого GET-запроса к `http://neverlands.ru/`.
        3.  **Ответ 1:** Логгируются код ответа и заголовки.
        4.  **Запрос 2 (Login POST):** Логгируются URL и заголовки POST-запроса к `http://neverlands.ru/game.php`.
        5.  **Ответ 2:** Логгируются код ответа и заголовки.
        6.  **Запрос 3 (Main page GET):** Логгируются URL и заголовки GET-запроса к `http://neverlands.ru/main.php`.
        7.  **Ответ 3:** Логгируются код ответа и заголовки.
        8.  **Сырой HTML:** Полное тело ответа от `main.php` записывается в лог перед любой обработкой.
        9.  **Результат:** Логгируется сообщение об успехе или провале авторизации.
        10. **Закрытие лога:** `DebugLogger.close()` вызывается при любом завершении (успех или ошибка), чтобы гарантировать сохранение файла.

### Этап 3: Обработка ответа в MainActivity

-   **Класс:** `ru.neverlands.abclient.MainActivity`
-   **Цель:** Убедиться, что HTML-код от `AuthManager` дошел до `MainActivity`.

    -   **Метод: `onCreate(Bundle savedInstanceState)`**
        -   **Что логгируется:** Факт получения `mainPageBody` из `Intent` и решение о его загрузке через `loadDataWithBaseURL`.
        -   **Пример лога:**
            ```
            2025-09-27 10:15:45.900: MainActivity: Received mainPageBody from intent, loading with loadDataWithBaseURL.
            ```

### Этап 4: Фильтрация и модификация HTML

-   **Класс:** `ru.neverlands.abclient.postfilter.Filter`
-   **Цель:** Увидеть, как изменяется HTML-код страницы `main.php` после обработки.

    -   **Метод: `process(String address, byte[] array)`**
        -   **Что логгируется:** Если `address` указывает на `main.php`, в лог записывается HTML-контент **до** и **после** вызова `MainPhp.process()`.
        -   **Пример лога:**
            ```
            2025-09-27 10:15:46.100: Filter: Processing main.php. HTML content BEFORE processing:
<html>...</html>
            2025-09-27 10:15:46.150: Filter: Processing main.php. HTML content AFTER processing:
<html>...modified...</html>
            ```
