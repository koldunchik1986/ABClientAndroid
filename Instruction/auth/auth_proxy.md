# Auth + Proxy: host-стратегия и BasicAuth

> Актуализировано: 2026-07-26. Модуль: `app2/` (ANClient).
> Прежняя редакция (2026-03-08) фиксировала baseline «`www` + cp1251» и оставляла открытым
> A/B-тест. **Вопрос закрыт** — см. §3. Ссылки на `app/.../abclient/AuthManager.java` устарели.

## 1. Текущее состояние кода (факт)

### Primary host — всегда `non-www`

```java
// GameServerUrls.java:307
public static String authBaseUrl(boolean proxyActive, String serverCode) {
    return gameBaseUrl(serverCode);
}

// GameServerUrls.java:255
public static String gameBaseUrl(String serverCode) {
    return "http://" + HOST_NEVERLANDS;   // всегда http://neverlands.ru
}
```

Параметры `proxyActive` и `serverCode` **игнорируются**. Ветка «в proxy-режиме используем
`www`» из кода убрана.

### `www` остался только как одноразовый fallback

`AuthManager.shouldRetryWithAlternateHost(result)` разрешает повтор, только если:

- `ProxyRuntimeManager.isRunning()`;
- результат не success и не captcha;
- HTTP-код ∈ {400, 403, 405, 407, 429, 500, 502, 503, 504}.

Тогда `GameServerUrls.alternateAuthBaseUrl(...)` меняет `non-www` → `www`, вызывается
`NetworkClient.clearCookies()` и auth-flow повторяется **один раз**.

### BasicAuth для upstream-прокси — cp1251 (сохранено)

```java
// ProxyRuntimeManager.java:331-332
// 1:1 с ПК-версией C#: BasicAuth кодируется через cp1251 (AppVars.Codepage).
String encoded = android.util.Base64.encodeToString(...);
```

### Таймауты

`NetworkClient`: `DIRECT_TIMEOUT_SECONDS = 30`, `PROXY_TIMEOUT_SECONDS = 60`.
Если proxy runtime поднят, применяется proxy-таймаут. Клиент пересоздаётся при изменении
`ProxyRuntimeManager.getRuntimeSignature()`.

## 2. Исходные симптомы (исторический контекст)

Через прокси авторизация падала на `POST /game.php`:

```
GET  /            -> 200
POST /game.php    -> 405
fallback/retry    -> 403 или 400 (squid/nginx)
```

Тогда были внесены 4 правки: `www` в proxy-режиме, cp1251 для `Proxy-Authorization`,
retry origin-form в `LocalHttpProxyServer`, исправление выбора таймаута.
Вход заработал на связке «`www` + cp1251», но вклад каждого фактора остался неразделённым.

## 3. Разрешение A/B-вопроса

Отдельный лабораторный тест не понадобился — **ответ дал продакшн**.

При устранении регрессии обрыва сессии выяснилось, что корневой причиной было смешение
хостов: выбор игрового сервера подставлял **IP** в URL, заголовок `Host` и домен cookies
(`originHost = selectedHost`), из-за чего сессия расползалась по трём хостам.

После фикса:

- URL / `Host` / cookies — всегда домен `neverlands.ru` (**non-www**);
- IP используется **только** для TCP-подключения (`connectHostForServer`, `resolveRoute`);
- выбор сервера передаётся полем формы `server` = `de` / `KZ`.

**Результат:** текущий продакшн работает в конфигурации «`non-www` + cp1251» — то есть
ровно «Вариант B» из прежнего плана. Пользователь подтвердил стабильную сессию на DE, KZ и
`neverlands.ru`.

### Вывод

| Гипотеза | Статус |
| --- | --- |
| `www` обязателен для входа через proxy | **Опровергнута.** `non-www` работает как primary |
| cp1251 в `Proxy-Authorization` нужен | Сохранена. 1:1 с ПК-версией, снимать без нужды не стоит |
| Реальная причина 405/400 — смешение хостов | **Вероятная** (см. оговорку ниже) |

> **Оговорка о доказательности.** Симптом `405` наблюдался на более старом состоянии кода,
> где host-mixing присутствовал. Прямого контрольного эксперимента «старый код + non-www»
> не проводилось, поэтому связка «405 ⇐ host-mixing» — обоснованная, но не доказанная
> гипотеза. Практически это уже не важно: текущая конфигурация стабильна, а `www` остаётся
> защитным fallback'ом для нестабильных прокси.

## 4. Точки переключения (если понадобится диагностика)

| Что | Где |
| --- | --- |
| Primary host | `GameServerUrls.authBaseUrl(...)` / `gameBaseUrl(...)` |
| Зеркальный host | `GameServerUrls.alternateAuthBaseUrl(...)` |
| Условия fallback'а | `AuthManager.shouldRetryWithAlternateHost(...)` |
| Кодировка BasicAuth | `ProxyRuntimeManager` (~L331) |
| Таймауты | `NetworkClient` (`DIRECT_TIMEOUT_SECONDS` / `PROXY_TIMEOUT_SECONDS`) |
| Connect-host (IP) | `GameServerUrls.connectHostForServer(...)`, `LocalHttpProxyServer.resolveRoute(...)` |

> **Не менять `gameBaseUrl` на IP или на `www` «для теста» без отката.** Это ровно та правка,
> которая вызвала обрыв сессии. Домен в URL/`Host`/cookies должен оставаться единым.

## 5. Анти-детект в proxy-слое

`LocalHttpProxyServer` приводит исходящий трафик к виду обычного браузера:

- `isClientIdentityHeader` вырезает `X-Requested-With`, `X-Android-*`, `Sec-CH-UA*`;
- `stripClientMarkersFromTarget` убирает служебные параметры `ab_*` / `an_*` из URL (13/13 тестов);
- `appendBrowserIdentityHeaders` добавляет браузерный набор заголовков;
- единый `User-Agent` Chrome/140 — сверен с реальным трафиком (`Login.har`).

> **Важный урок:** ранее добавленные `Sec-CH-UA` пришлось убрать — реальный Chrome по HTTP
> клиент-хинты **не отправляет** (проверено по `Login.har`), их наличие само по себе было
> маркером неофициального клиента.

`CookiesManager.toHostOnlyCookieHeader` приведён к C#-эталону: только `name=value; Path=/`,
лишние атрибуты вырезаются.

## 6. Диагностика в логах

- `AuthManager: authBaseUrl=..., proxyActive=..., server=..., formServer=...` — выбранный host;
- `AuthManager: proxy fallback for authorize, primary=..., alternate=..., reason=...` — сработал fallback;
- `AuthManager: collected cookies count=N names=[...]` — итоговая сессия.

Появление строки `proxy fallback` в норме означает проблему у upstream-прокси, а не в
логике входа: primary-попытка на `non-www` должна проходить.

## 7. Чеклист

- [x] Зафиксировано текущее состояние: primary `non-www`, `www` — одноразовый fallback.
- [x] Сохранена cp1251-кодировка BasicAuth (паритет с ПК).
- [x] A/B-вопрос закрыт: `non-www` работает в продакшне (DE / KZ / neverlands.ru).
- [x] Устранена корневая причина обрыва сессии (host-mixing IP ↔ домен).
- [x] Документация приведена в соответствие с `app2/`.
