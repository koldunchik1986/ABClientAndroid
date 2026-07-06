# Анализ ProxyRequestQueue.cs для портирования в app2

## 1. План портирования ProxyRequestQueue.cs

`ANClient/ANProxy/ProxyRequestQueue.cs` реализует session-wide очередь динамических игровых HTTP-запросов. Цель порта в `app2` - поставить такой же универсальный throttle перед отправкой запросов через существующий localhost/upstream proxy, чтобы несколько динамических запросов Neverlands не стартовали в один серверный слот и не провоцировали `535/536`/rate-limit поведение.

## 2. Функциональность в C#

- `ProxyRequestQueue.WaitTurn(url, host, isGameHost, isCache)` вызывается из `ANProxy.Session` сразу перед `Response.ResendRequest(...)`, то есть после cache-hit проверки и до реального сетевого запроса.
- `DirectGameRequestGuard` вызывает тот же `WaitTurn(...)`, если запрос к Neverlands идёт без локального proxy; это закрывает прямые `HttpWebRequest`/`WebClient` пути.
- `GameRequestSpacingMs = 1000` задаёт минимальный интервал между динамическими игровыми запросами.
- `SkipLogThrottleMs = 10000` ограничивает шум логов для запросов, которые не ставятся в очередь.
- `ShouldQueue(...)` пропускает неигровые host, cache/static, счётчики `top.list.ru`/`counter.yadro.ru`, статические расширения, безопасные lookup endpoint (`getid.cgi`, `info.cgi`, `getcity.cgi`, `pinfo.cgi`, `pbots.cgi`, `logs.fcg`) и read-only chat frame.
- В очередь попадают `main.php`, `/gameplay/ajax/*`, `ch.php`, прочие `.php/.cgi/.fcg` и динамические игровые страницы.
- `ReserveSlot()` под lock резервирует следующий timestamp, возвращая `waitMs`; поток спит через `Thread.Sleep(waitMs)`.
- `SafeUrl(...)` маскирует `vcode=` и режет URL до 220 символов для безопасного лога.

## 3. Решение для портирования на Android

- Создать `app2/src/main/java/ru/neverlands/anclient/proxy/ProxyRequestQueue.java` как Android-аналог C# класса.
- Интегрировать queue-state в существующий `SessionManager`, чтобы pacing был привязан к текущей игровой сессии и был единым для всех proxy worker-потоков.
- Встроить вызов в `LocalHttpProxyServer.forwardRequest(...)` перед открытием remote socket. Это покрывает WebView, OkHttp и `HttpURLConnection`, когда они используют `ProxyRuntimeManager.getActiveJavaProxyOrNull()`.
- Не менять бизнес-логику автофункций, VCode, кликов, навигации и parser-веток.
- Логировать через `AppLog` chain `proxy`, чтобы события сохранялись в файловых critical-логах.

## 4. План реализации

- [x] Проанализировать C# `ProxyRequestQueue.cs`, `Session.cs`, `DirectGameRequestGuard.cs`.
- [x] Найти Android proxy decision point: `LocalHttpProxyServer.forwardRequest(...)`.
- [ ] Добавить `ProxyRequestQueue.java` с фильтрами `ShouldQueue`, `PathAndQuery`, `PathOnly`, `IsStaticPath`, `IsSafeLookup`, `IsReadOnlyChatFrame`, `SafeUrl`.
- [ ] Добавить в `SessionManager` методы резервирования queue-slot и throttled skip-log для proxy request queue.
- [ ] Вызвать очередь из `LocalHttpProxyServer` перед `remote.connect(...)` и перед retry-сокетами, чтобы повторные варианты того же запроса тоже не стартовали одновременно с другими динамическими запросами.
- [ ] Проверить сборку `:app2:assembleDebug`.
- [ ] Проверить отсутствие новых `AppVars.VCode`, прямого `Log.*`, mojibake и runtime `AB/ABC` в новых строках.

## 5. Инварианты

- `ANClient/` только читается, не изменяется.
- Не добавлять новый HTTP/proxy-контур рядом с `LocalHttpProxyServer`.
- Не задерживать static/cache/counter/safe lookup запросы.
- Не маскировать и не менять payload, cookie, VCode и HTML-ответы.
- Не логировать полный `vcode`; в URL-логах использовать `<redacted>`.
