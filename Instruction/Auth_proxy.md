# Auth + Proxy (Android): рабочая инструкция

## 1. Цель документа
- Зафиксировать рабочий auth-flow через proxy в Android-клиенте.
- Описать первопричину сбоя `Go out!!!`/белых room/chat-фреймов.
- Оставить проверяемые правила, чтобы быстро диагностировать регрессии.

## 2. Ключевой вывод по логам 2026-03-09

По логу `Logs/logcat_runtime_20260309_05.txt` авторизация прошла корректно, потому что выполнились **оба** условия:

1. `AuthManager` завершил 3-шаговый вход на правильном host для proxy:
   - `GET http://www.neverlands.ru/`
   - `POST http://www.neverlands.ru/game.php`
   - `GET http://www.neverlands.ru/main.php`
   - маркер: `Authorization attempt result ... success=true`.
2. После входа cookie были синхронизированы в оба host (`neverlands.ru` и `www.neverlands.ru`) и chat/room начали уходить с полным набором `Never* + PHPSESSID + watermark`.

### Первопричина прошлых сбоев
- Сессия создавалась на одном host (часто `www`), а фреймы `ch.php/ch/msg.php/ch/but.php` шли на `neverlands.ru`.
- Для `neverlands.ru` cookie-набор был неполным, сервер давал разлогин (`Go out!!!`) и белые/пустые фреймы.

## 3. Текущая рабочая цепочка (step-by-step)

### 3.1 LoginActivity
- Вызывает `AuthManager.authorize(...)`.
- До запуска auth поднимает proxy-runtime (если включено в профиле).

### 3.2 AuthManager (без captcha)
1. `GET /` (получить первичные cookie + watermark).
2. `POST /game.php` (логин/пароль в `windows-1251`).
3. `GET /main.php` (финализация сессии).

Если proxy-режим активен:
- базовый host: `http://www.neverlands.ru`;
- fallback на альтернативный host (`www <-> non-www`) разрешен только на явных HTTP-ошибках (400/403/405/407/429/500/502/503/504), с очисткой cookie перед ретраем.

### 3.3 Cookie перенос в MainActivity
- В `setupWebViews()` применяется `AppVars.lastCookies`.
- Каждая cookie пишется в **оба** host:
  - `http://neverlands.ru/`
  - `http://www.neverlands.ru/`
- Далее `syncSessionCookiesAcrossHosts(...)` добивает зеркалирование, если на одном host есть сессия, а на втором нет.

### 3.4 Chat/Room/WebViewInterceptor
- Запросы room/chat должны идти с `cookieSummary=count=11` (или другим полным набором, но обязательно с `PHPSESSID`, `watermark`, `Never*`).
- В proxy-режиме все запросы должны иметь маркер `PROXY_BINDING: ... via local proxy`.

## 4. Зависимости по классам

## Auth и cookie
- `app/src/main/java/ru/neverlands/abclient/AuthManager.java`
- `app/src/main/java/ru/neverlands/abclient/LoginActivity.java`
- `app/src/main/java/ru/neverlands/abclient/MainActivity.java`
- `app/src/main/java/ru/neverlands/abclient/network/NetworkClient.java`

## Proxy runtime
- `app/src/main/java/ru/neverlands/abclient/proxy/ProxyRuntimeManager.java`
- `app/src/main/java/ru/neverlands/abclient/proxy/LocalHttpProxyServer.java`
- `app/src/main/java/ru/neverlands/abclient/webview/WebViewRequestInterceptor.java`
- `app/src/main/java/ru/neverlands/abclient/bridge/WebAppInterface.java`

## Профиль и настройки
- `app/src/main/java/ru/neverlands/abclient/model/UserConfig.java`

## 5. Какие фиксы обязательны (оставляем)

1. В proxy-режиме auth на `www.neverlands.ru`.
2. `POST /game.php` в `windows-1251`.
3. Basic Proxy-Authorization в `windows-1251` (как в ПК-версии C#).
4. Синхронизация cookie между `neverlands.ru` и `www.neverlands.ru` после применения `lastCookies`.
5. Строгий запрет direct egress при включенном proxy (`DoProxy/UseProxy`), чтобы не утекал реальный IP.

## 6. Какие фиксы убраны как лишние

1. Удален неиспользуемый метод `resolveFramesBaseUrl()` в `MainActivity`.
2. Убран предварительный дубль-синк `AUTH_COOKIE_SYNC(before_lastCookies_apply)` в `setupWebViews()`:
   - практической роли не играл,
   - итоговый рабочий sync после `lastCookies` оставлен.

## 7. Правила логирования (обязательные маркеры)

При любой проблеме входа/фреймов проверять:

1. `Authorization attempt result ... success=true/false`
2. `AUTH_COOKIE_SYNC: applied lastCookies names=[...]`
3. `AUTH_COOKIE_SYNC[after_lastCookies_apply]: never=..., www=...`
4. `CHAT_REQ_HEADERS ... cookieSummary=...`
5. `PROXY_BINDING: interceptor openConnection via local proxy`
6. `PROXY_FAIL`/`PROXY_UPSTREAM_RETRY` (если есть)

Если `chat/room` пустые:
- сначала проверить, есть ли `PHPSESSID`, `watermark`, `NeverCode`, `NeverHash`, `NeverPuid` в `cookieSummary`.

## 8. Чеклист регрессии

1. Вход без proxy: успех с 1 попытки.
2. Вход с proxy: успех с 1 попытки (допускается медленнее по времени).
3. После входа работает:
   - верхний фрейм,
   - `ch/msg.php`,
   - `ch.php?lo=1`,
   - `ch/but.php`.
4. Нет `Go out!!!` при активной сессии.
5. В proxy-режиме нет прямых запросов в обход локального прокси.

## 9. Операционный порядок при новых сбоях

1. Снять runtime-log и найти маркеры из раздела 7.
2. Проверить, на каком host прошел шаг 1/2/3 auth.
3. Проверить фактический cookie-набор на `neverlands.ru` и `www.neverlands.ru`.
4. Проверить, через какой маршрут ушли chat/room (local proxy или direct).
5. Только после этого вносить правки (без “слепых” retry-патчей).

## 10. Важные ограничения проекта

1. Все файлы проекта сохранять в UTF-8 without BOM.
2. Папку `ABClient` не изменять (это эталон ПК-версии).
3. В сетевых заголовках использовать только браузерный User-Agent (без `ABClient`/`Android; ABClient`).
