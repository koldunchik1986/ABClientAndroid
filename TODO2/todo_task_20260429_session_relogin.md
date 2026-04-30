# Задача: auto-relogin при HTML session error

## Контекст

HAR `session_error.har` показывает, что обрыв игровой сессии приходит как HTTP 200 HTML-страница с `./css/error.css` и текстом `Внимание! Сеанс работы прерван.`. Поэтому сетевые error-callback не срабатывают, а клиент должен распознать HTML и выполнить перезаход.

## Найденный существующий контур

- `WebViewRequestInterceptor` уже получает HTML-ответы `main.php`/`ch.php` и запускает `Filter.process`.
- `AuthManager` уже реализует штатный login-flow через `game.php`/`main.php`, включая proxy fallback и cookie-store.
- `MainActivity.setupWebViews()` уже переносит cookies из `AppVars.lastCookies` в WebView и синхронизирует `neverlands.ru`/`www.neverlands.ru`.
- В ПК-версии `ABClient/PostFilter/MainPhp.cs` при `Сеанс работы прерван` вызывает `UpdateGame(...)`, что приводит к `LogOn()` на следующем timer tick.

## План реализации

- [x] Добавить детектор HTML session-error в общий WebView response pipeline.
- [x] Добавить debounce/orchestration в `MainActivity`, чтобы несколько фреймов не запускали параллельный перезаход.
- [x] Вынести auth/relogin цепочку в `SessionReloginHandler` и переиспользовать `AuthManager`.
- [x] Переиспользовать существующий перенос cookies в WebView после успешного relogin.
- [x] Добавить fallback на `LoginActivity`, если нет сохраненного пароля, профиль зашифрован, нужна captcha или auth не удался.
- [x] Выполнить сборку `:app2:assembleDebug` и проверки UTF-8/mojibake/Log/AppVars.VCode.

## Проверки

- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` — успешно.
- [x] `git diff --check` по измененным файлам — новых whitespace-ошибок нет; остался существующий warning `.gitattributes" is not a valid attribute name: .gitattributes:7` и LF/CRLF warnings.
- [x] BOM-проверка измененных файлов — BOM не найден.
- [x] Mojibake-проверка по измененным файлам — совпадений нет.
- [x] Новых прямых `android.util.Log` / `Log.*` в измененных файлах нет.
- [x] Новых `AppVars.VCode` в измененных файлах нет.
