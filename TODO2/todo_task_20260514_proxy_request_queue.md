# Задача: очередь proxy-запросов для app2

## Цель

Сделать в `app2` Android-аналог `ANClient/ANProxy/ProxyRequestQueue.cs`: динамические игровые запросы Neverlands, проходящие через локальный/внешний proxy runtime, должны стартовать с интервалом `1000 ms`, если попадают в один короткий временной слот. Решение должно быть универсальным и не менять бизнес-логику автофункций.

## Анализ

- [x] C# `ProxyRequestQueue.WaitTurn(...)` вызывается перед реальным отправлением запроса в `ANProxy.Session` и резервирует один общий слот на все worker-потоки.
- [x] Android equivalent point найден в `LocalHttpProxyServer.forwardRequest(...)`: сюда приходят WebView, OkHttp и `HttpURLConnection`, если они используют `ProxyRuntimeManager.getActiveJavaProxyOrNull()`.
- [x] `SessionManager` сейчас управляет VCode/session context и уже является singleton с thread-safe lock; queue-state можно держать там, не создавая отдельный runtime-контур.
- [x] `TODO2/ANProxy/todo_ProxyRequestQueue.cs.md` создан как подробный анализ C# источника и план порта.

## План реализации

- [x] Добавить `ProxyRequestQueue.java` в `app2/src/main/java/ru/neverlands/anclient/proxy/`.
- [x] Добавить в `SessionManager` session-wide reservation методы для proxy queue.
- [x] Подключить `ProxyRequestQueue.waitTurn(...)` в `LocalHttpProxyServer.forwardRequest(...)` перед remote socket connect.
- [x] Подключить тот же guard к proxy retry-веткам, если они открывают новый remote socket.
- [x] Логировать очередь через `AppLog.d("proxy", ...)` с `PROXY_QUEUE` маркерами.
- [x] Запустить `./gradlew2.bat --no-daemon :app2:assembleDebug`.
- [x] Выполнить targeted checks: `AppVars.VCode`, прямой `Log.*`, mojibake, runtime `AB/ABC`, `git diff --check`.

## Результат реализации

- [x] `ProxyRequestQueue.waitTurn(...)` классифицирует игровые динамические запросы и пропускает static/cache/counter/safe lookup/read-only chat frame без задержки.
- [x] `LocalHttpProxyServer` вызывает очередь для primary-forward, retry-forward и connect-tunnel веток перед открытием удалённого сокета.
- [x] `SessionManager` хранит общий timestamp-слот очереди и throttle для skip-логов, чтобы несколько proxy worker-потоков делили один pacing state.
- [x] URL в логах маскирует `vcode=<redacted>`.
- [x] Сборка `.\gradlew2.bat --no-daemon :app2:assembleDebug` успешна 2026-05-14.

## Проверки

- [x] `AppVars.VCode` в изменённых proxy/SessionManager участках не найден.
- [x] Прямой `Log.*`/`import android.util.Log` в изменённых proxy/SessionManager участках не найден.
- [x] Mojibake/control characters в `ProxyRequestQueue` task-файле и proxy/SessionManager участках не найдены.
- [x] Runtime `AB/ABC/ABCLIENT` в новых proxy строках не найден.
- [x] `git diff --check -- app2 TODO2` выполнен: новых whitespace-ошибок не показал, но репозиторий всё ещё выводит существующую ошибку `.gitattributes:7` и CRLF warnings.

## Ожидаемые логи

- `PROXY_QUEUE: queued game action, reason=main_php|gameplay_ajax|room_or_chat_dynamic|dynamic_game_endpoint|dynamic_game_page, waitMs=...`
- `PROXY_QUEUE: skipped game request queue, reason=static_path|safe_lookup|read_only_chat_frame|cache_or_static|counter`

## Риски

- Если очередь поставить выше локального proxy, придётся править много автофункций и появится риск двойного throttle.
- Если задерживать static/cache/captcha-картинки, WebView может получить лишние таймауты.
- Если логировать полный URL, можно раскрыть `vcode`; нужен redaction.
