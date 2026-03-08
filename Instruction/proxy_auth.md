# Proxy Auth: Полная инструкция (Android ABClient)

## 1. Назначение
Документ фиксирует рабочую схему авторизации через прокси для `neverlands.ru` и правила диагностики/восстановления, если вход снова начнет падать.

Ключевая задача: не допустить утечки direct-трафика при включенном прокси и стабильно проходить 3-шаговый auth-flow.

---

## 2. Подтвержденный рабочий baseline (2026-03-08)
Рабочая комбинация:
- auth-host в proxy-режиме: `http://www.neverlands.ru`
- `Proxy-Authorization`: `Basic ...` с кодировкой `windows-1251`
- login form (`player_nick`, `player_password`) — `windows-1251`
- runtime-прокси поднят до старта auth (`127.0.0.1:8052+`)

A/B проверка:
- `non-www + cp1251` -> падение (`POST /game.php` => `405`, retry => `400`)
- `www + cp1251` -> успешный вход (`GET /`, `POST /game.php`, `GET /main.php` все `200`)

---

## 3. Зависимости (модули и роли)
## 3.1 `LoginActivity`
Файл: `app/src/main/java/ru/neverlands/abclient/LoginActivity.java`
- Точка входа `login()`.
- До авторизации вызывает `ProxyRuntimeManager.ensureStarted(...)`.
- После старта proxy делает `NetworkClient.invalidateInstance()`.
- Чистит cookies и запускает `AuthManager.authorize(...)`.

## 3.2 `AuthManager`
Файл: `app/src/main/java/ru/neverlands/abclient/AuthManager.java`
- Реализует 3 шага auth:
  1. `GET /`
  2. `POST /game.php`
  3. `GET /main.php`
- В proxy-режиме выбирает `www`-host через `resolveAuthBaseUrl()`.
- Формирует `FormBody` в `windows-1251`.

## 3.3 `ProxyRuntimeManager`
Файл: `app/src/main/java/ru/neverlands/abclient/proxy/ProxyRuntimeManager.java`
- Поднимает локальный proxy-server.
- Парсит `ProxyAddress`, применяет `ProxyUserName/ProxyPassword`.
- Формирует `basicAuthHeader` в `windows-1251`.
- Применяет proxy override для WebView.

## 3.4 `LocalHttpProxyServer`
Файл: `app/src/main/java/ru/neverlands/abclient/proxy/LocalHttpProxyServer.java`
- Forward HTTP-запросов в upstream proxy.
- Retry-ветки для `POST /game.php` при `405`:
  - absolute-form c explicit `:80`
  - origin-form `/game.php`
  - fallback CONNECT-туннель

## 3.5 `NetworkClient`
Файл: `app/src/main/java/ru/neverlands/abclient/network/NetworkClient.java`
- Строит `OkHttpClient` с прокси `127.0.0.1:port`.
- Timeout policy:
  - proxy mode: `60s`
  - direct mode: `30s`
- Блокирует direct fallback при strict-proxy.

## 3.6 `WebViewRequestInterceptor`
Файл: `app/src/main/java/ru/neverlands/abclient/webview/WebViewRequestInterceptor.java`
- Перехватывает main/chat/frame-запросы.
- Использует локальный proxy endpoint из runtime.
- Возвращает `proxy runtime unavailable` только если strict-proxy включен, а runtime недоступен.

---

## 4. Обязательные правила для proxy-login
1. При включенном proxy (`DoProxy/UseProxy`) auth должен стартовать только после успешного `ensureStarted`.
2. Нельзя отправлять direct-запросы к игровому серверу, если strict-proxy включен.
3. Для auth в proxy-режиме использовать `www.neverlands.ru`.
4. `Proxy-Authorization` кодировать через `windows-1251`.
5. Форму логина отправлять как `application/x-www-form-urlencoded` в `windows-1251`.
6. Не менять порядок auth-шагов:
   - GET `/`
   - POST `/game.php`
   - GET `/main.php`

---

## 5. Диагностика по logcat (что смотреть первым)
Поиск по маркерам:
- `PROXY_BOOT`
- `PROXY_BINDING`
- `AuthManager: authBaseUrl=`
- `PROXY_RESP: ... method=POST ... game.php`
- `Authorization attempt result`

Ожидаемый успех:
- `authBaseUrl=http://www.neverlands.ru` (в proxy-режиме)
- `POST http://www.neverlands.ru/game.php` -> `200`
- `Authorization attempt result ... success=true`

Паттерн поломки:
- `POST http://neverlands.ru/game.php` -> `405`
- retry -> `400`/`403`

---

## 6. Матрица ошибок и действия
1. `POST /game.php` = `405` (nginx), затем `400` (squid):
- Проверить host (`www` vs non-www) в `AuthManager.resolveAuthBaseUrl()`.
- Проверить `Proxy-Authorization` кодировку (`windows-1251`).

2. `proxy runtime unavailable` в WebView:
- Проверить, что runtime реально поднят (`PROXY_BOOT: started`).
- Проверить `ProxyRuntimeManager.getActiveJavaProxyOrNull()` в момент запроса.
- Проверить `DoProxy/UseProxy` и strict-proxy путь блокировок.

3. Долгий вход без падения:
- Нормально для медленного upstream.
- Контролировать `NET_TIMEOUT: mode=proxy`.

---

## 7. Точки переключения (rollback/restore)
## 7.1 Auth host
Файл: `AuthManager.resolveAuthBaseUrl()`
- Рабочий вариант:
```java
final boolean proxyActive = ProxyRuntimeManager.isRunning();
final String baseUrl = proxyActive ? "http://www.neverlands.ru" : "http://neverlands.ru";
```

## 7.2 Proxy basic auth encoding
Файл: `ProxyRuntimeManager.buildUpstreamSettings(...)`
- Рабочий вариант:
```java
pair.getBytes(Charset.forName("windows-1251"))
```

---

## 8. Мини-чеклист перед релизом
- [ ] `resolveAuthBaseUrl()` использует `www` в proxy-режиме.
- [ ] `Proxy-Authorization` кодируется в `windows-1251`.
- [ ] `POST /game.php` через proxy возвращает `200` в тестовом логине.
- [ ] Нет `PROXY_FAIL` с блокировкой direct в успешной сессии.
- [ ] `WebView`-фреймы (`main/chat/buttons`) ходят через `127.0.0.1:port`.

---

## 9. Референс-логи
- Успех proxy-login: `Logs/logcat_runtime_20260308_31.txt`
- Падение `non-www + cp1251`: `Logs/logcat_runtime_20260308_32.txt`

