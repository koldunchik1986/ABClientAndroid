# Реализация механизма обработки капчи при авторизации

**Статус:** `[x]` Реализовано (требуется полевое тестирование при появлении капчи)

## 1. Назначение

Добавить в процесс авторизации (`AuthManager`) функционал для обработки капчи, которую сервер может запросить после нескольких неудачных попыток входа. Это предотвращает полную блокировку входа в приложение и позволяет пользователю ввести код с картинки для продолжения.

## 2. Анализ и логика работы

Анализ файла `LoginCaptcha.har` и логов показал следующий алгоритм со стороны сервера:

1.  **Первичная попытка входа:** Клиент отправляет `POST` запрос на `http://neverlands.ru/game.php` с параметрами `player_nick` и `player_password`.
2.  **Ответ с капчей:** Если сервер решает показать капчу, он отвечает кодом `200 OK`, но в теле ответа содержится HTML-страница с формой для ввода капчи. Ключевые элементы на этой странице:
    *   Изображение капчи: `<img src="http://neverlands.ru/modules/code/nl_reg_code.php?{ID_КАПЧИ}">`
    *   Скрытое поле с кодом сессии капчи: `<input type="hidden" name="vcode" value="{ЗНАЧЕНИЕ_VCODE}">`
    *   Поле для ввода: `<input type="text" name="verify">`
3.  **Повторная попытка с капчей:** Клиент должен отправить повторный `POST` запрос на тот же URL (`http://neverlands.ru/game.php`), но уже с дополнительными параметрами:
    *   `player_nick`
    *   `player_password`
    *   `vcode` (из скрытого поля)
    *   `verify` (код, введенный пользователем)
4.  **Результат:**
    *   При верном коде капчи происходит успешная авторизация.
    *   При неверном коде сервер снова возвращает страницу с капчей (вероятно, с новым `vcode` и новым изображением).

## 3. План реализации и выполненные шаги

### Шаг 1: Создание модели `AuthResult`

-   `[x]` **Действие:** Создан новый класс `ru.neverlands.abclient.model.AuthResult`.
-   **Назначение:** Инкапсулировать все возможные исходы асинхронной операции авторизации, чтобы избежать сложной логики с множественными колбэками.
-   **Структура класса:**
    *   `isSuccess` (boolean): `true`, если авторизация прошла успешно.
    *   `isCaptchaRequired` (boolean): `true`, если сервер запросил ввод капчи.
    *   `captchaUrl` (String): Полный URL изображения капчи.
    *   `vcode` (String): Значение скрытого поля `vcode`.
    *   `errorMessage` (String): Сообщение об ошибке.
    *   `cookies` (List<HttpCookie>): Список полученных cookies при успешном входе.

### Шаг 2: Модернизация `AuthManager`

-   `[x]` **Действие:** Класс `ru.neverlands.abclient.AuthManager` был полностью переписан.
-   **Изменения:**
    1.  **Метод `authorize(String username, String password)`:**
        *   Теперь возвращает объект `AuthResult` вместо использования `AuthCallback`.
        *   После `POST` запроса на `game.php` тело ответа (`loginResponseBody`) парсится с помощью библиотеки `Jsoup`.
        *   **Логика детекции капчи:**
            ```java
            Document doc = Jsoup.parse(loginResponseBody);
            Element captchaImg = doc.selectFirst("img[src*='nl_reg_code.php']");
            Element vcode_el = doc.selectFirst("input[name=vcode]");
            ```
        *   Если оба элемента (`captchaImg` и `vcode_el`) найдены, из них извлекаются `captchaUrl` и `vcode`, после чего создается и возвращается `new AuthResult(captchaUrl, vcode)`.
        *   Если капча не найдена, продолжается стандартная проверка на успех или ошибку.

    2.  **Метод `authorizeWithCaptcha(String username, String password, String vcode, String verify)`:**
        *   Создан новый метод для второго шага авторизации.
        *   Принимает все необходимые данные, включая `vcode` и `verify` (ввод пользователя).
        *   Формирует и отправляет `POST` запрос со всеми четырьмя параметрами (`player_nick`, `player_password`, `vcode`, `verify`).
        *   Также анализирует ответ и возвращает `AuthResult`, который может быть либо успешным, либо снова требовать капчу (в случае неверного ввода), либо содержать ошибку.

### Шаг 3: Создание UI для капчи

-   `[x]` **Действие:** Создан новый layout-файл `app/src/main/res/layout/dialog_captcha.xml`.
-   **Компоненты:**
    *   `ImageView` (`@+id/captchaImageView`): для отображения картинки с кодом.
    *   `ProgressBar` (`@+id/captchaProgressBar`): для индикации загрузки изображения.
    *   `EditText` (`@+id/captchaEditText`): для ввода кода пользователем.

### Шаг 4: Интеграция в `LoginActivity`

-   `[x]` **Действие:** Класс `ru.neverlands.abclient.LoginActivity` был значительно доработан.
-   **Изменения:**
    1.  **Метод `login()`:**
        *   Вызов `AuthManager.authorize` теперь выполняется в фоновом потоке с помощью `ExecutorService`.
        *   Результат `AuthResult` передается в новый метод `handleAuthResult` для обработки в UI-потоке.

    2.  **Метод `handleAuthResult(...)`:**
        *   Центральный метод для обработки ответа от `AuthManager`.
        *   Если `result.isSuccess()`, вызывается `onLoginSuccess()`.
        *   Если `result.isCaptchaRequired()`, вызывается `showCaptchaDialog()`.
        *   В случае ошибки, `result.getErrorMessage()` показывается в `Toast`.

    3.  **Метод `showCaptchaDialog(...)`:**
        *   Создает и показывает `AlertDialog` с кастомным layout (`dialog_captcha.xml`).
        *   Использует библиотеку `Glide` для асинхронной загрузки изображения капчи по `captchaUrl` в `captchaImageView`.
        *   При нажатии кнопки "OK":
            *   Получает введенный текст из `captchaEditText`.
            *   Запускает в фоновом потоке вызов `authManager.authorizeWithCaptcha(...)`.
            *   Результат этого вызова снова передается в `handleAuthResult`, замыкая цикл (на случай неверного ввода капчи).

## 4. Зависимости

*   **`org.jsoup:jsoup:1.17.2`**: Используется в `AuthManager` для парсинга HTML-ответа и поиска элементов формы капчи.
*   **`com.github.bumptech.glide:glide:4.15.1`**: Используется в `LoginActivity` для загрузки изображения капчи по URL.
