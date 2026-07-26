# Процесс авторизации в ANClient (app2)

> Актуализировано: 2026-07-26. Модуль: `app2/`, package `ru.neverlands.anclient`.
> Предыдущая редакция описывала `app/` (ABClient) и была устаревшей по 4 пунктам:
> callback-архитектура, `WebViewCookieJar`, отсутствие captcha/flash/server-полей.

## Введение

Документ описывает полный путь входа пользователя в ANClient: от нажатия «Вход» до загрузки
игрового интерфейса в `WebView`. Цель процесса — получить от `neverlands.ru` валидный набор
сессионных cookies (`PHPSESSID`, `NeverPuid`, `NeverCode`, `NeverHash`, ...) и перенести их
в системный `CookieManager`, которым пользуется `WebView`.

## Основные компоненты

| Класс | Роль |
| --- | --- |
| `LoginActivity` | UI входа, выбор профиля, расшифровка пароля, показ captcha, миграция шифрования |
| `AuthManager` | Вся сетевая логика авторизации (604 строки). **Синхронный**, возвращает `AuthResult` |
| `AuthResult` | Результат-объединение трёх состояний: success (cookies) / captcha / error |
| `NetworkClient` | Единый `OkHttpClient` + `JavaNetCookieJar` (общий cookie-store) |
| `GameServerUrls` | Единый источник host'ов: URL-домен, connect-host (IP), код сервера для формы |
| `ProxyRuntimeManager` | Состояние proxy-режима, BasicAuth для upstream |
| `CookieSyncHelper` | Перенос cookies из `AppVars.lastCookies` в `android.webkit.CookieManager` |
| `MainActivity` | `WebView` с игровым интерфейсом, использует уже установленную сессию |

> **Внимание:** `WebViewCookieJar.java` физически присутствует в проекте, но это **мёртвый код** —
> 0 использований вне самого файла. Cookies идут через `JavaNetCookieJar` + ручной перенос.
> Старая документация ошибочно называла его «ключевым элементом синхронизации».

## Пошаговый процесс

### 0. Подготовка в `LoginActivity`

- Пользователь выбирает профиль и вводит пароль (или ключ шифрования).
- Профиль расшифровывается; при формате legacy-3DES выполняется **ленивая миграция**
  на `ANC1:` (AES-256-GCM) — `migrateProfileEncryptionIfLegacy(...)` (`LoginActivity:574, :665`).
- `AppVars.lastCookies = null` (`LoginActivity:751`) — сброс прошлой сессии.
- Вызывается `AuthManager.authorize(username, password, flashPassword, gameServerCode)`.

### 1. Выбор базового host (важно!)

`resolveAuthBaseUrl(serverCode)` → `GameServerUrls.authBaseUrl(proxyActive, serverCode)`.

**Текущее поведение: параметр `proxyActive` игнорируется, всегда возвращается `http://neverlands.ru`.**

Это результат фикса выбора сервера: раньше в URL/`Host`/cookies подставлялся IP выбранного
сервера, из-за чего сессия «разъезжалась» по трём хостам и обрывалась. Теперь:

- **URL, заголовок `Host`, домен cookies** — всегда `neverlands.ru`;
- **TCP-подключение** — на IP нужного сервера (`GameServerUrls.connectHostForServer`,
  `LocalHttpProxyServer.resolveRoute`);
- **выбор сервера передаётся полем формы** `server` = `de` / `KZ`
  (`GameServerUrls.loginFormServerCode`).

### 2. Шаг 1: `GET /`

Заголовки: `User-Agent` (`AppVars.BROWSER_USER_AGENT`, единый Chrome/140), `Accept`,
`Accept-Language: ru-RU,ru;q=0.9,...`.

**Цель:** получить первичные cookies (в т.ч. `watermark`), которые сервер требует для
последующего POST.

Ошибка → `AuthResult("Ошибка получения начальной страницы: {code}")`.

### 3. Шаг 2: `POST /game.php`

Тело — `FormBody` в кодировке **windows-1251** (`buildLoginFormBody`), порядок полей:

1. `vcode` — только если задан (captcha-flow);
2. `player_nick`;
3. `player_password`;
4. `verify` — только если задан (captcha-flow);
5. `server` — код сервера для формы (`de` / `KZ`).

Заголовки: `User-Agent`, `Referer` = `{base}/`, `Origin` = `{base}`.

Ответ разбирается через `Jsoup` по трём веткам:

| Условие | Результат |
| --- | --- |
| `img[src*='nl_reg_code.php']` **и** `input[name=vcode]` | `AuthResult(captchaUrl, vcode)` — нужна captcha |
| тело содержит `auth_form` | `AuthResult("Ошибка авторизации: неверный логин или пароль.")` |
| найден `flashvars="plid=..."` | переход к шагу 3 (flash-пароль) |

### 4. Шаг 3: flash-пароль (условный)

`submitFlashPasswordIfRequired(...)` — паритет с C# `PostFilter.GamePhp`.

Если задан flash-пароль **и** в HTML найден `plid` (`FLASH_PLID_PATTERN`), отправляется
`POST /game.php` с полями `flcheck` (пароль) и `nid` (plid), тоже в windows-1251.

Ошибка → `AuthResult("Ошибка ввода Flash-пароля: {code}")`.

### 5. Шаг 4: `GET /main.php`

Перед запросом — `Thread.sleep(500)` (пауза, унаследованная от рабочего сценария ПК-версии).

Заголовки: `User-Agent`, `Referer` = `{base}/game.php`.

**Цель:** финализировать сессию. Ошибка → `AuthResult("Ошибка финализации сессии: {code}")`.

### 6. Сбор cookies

`collectNeverlandsCookies(cookieManager, responseCookies)`:

1. Проходит **весь** `CookieStore`, оставляя host-only (пустой domain) и домены neverlands;
2. дедуплицирует по ключу `name|domain|path`;
3. если результат пуст — fallback на явные URI `neverlands.ru`, затем `www.neverlands.ru`;
4. добавляет cookies, перехваченные напрямую из заголовков `Set-Cookie` на каждом шаге
   (`collectResponseCookies`, стадии: `initial_get`, `login_post`, `flash_post`, `main_get`,
   `captcha_login_post`, `captcha_main_get`).

Двойной сбор (store + сырые заголовки) нужен потому, что `CookieStore` отбрасывает cookies
с нестандартными атрибутами, а игровой сервер такие присылает.

### 7. Перенос в `WebView` и запуск `MainActivity`

- `LoginActivity` получает `AuthResult`, проверяет `isSuccess()` (`:864`) / `isCaptchaRequired()` (`:868`);
- при успехе: `AppVars.lastCookies = cookies` (`:961`);
- `CookieSyncHelper` переносит их в `android.webkit.CookieManager` и вызывает **`flush()`**;
- `startActivity(intent)` (`:1010`) → `MainActivity` грузит `main.php`.

> **`CookieManager.flush()` остаётся критичным.** Без него cookies живут только в памяти и
> `WebView` может не увидеть сессию (историческая проблема экрана «Cookie...»).

## Ветка captcha

Если пришёл `AuthResult` с `isCaptchaRequired()`, `LoginActivity` показывает картинку и
вызывает `authorizeWithCaptcha(username, password, flashPassword, serverCode, vcode, verify)`.

`authorizeWithCaptchaInternal` **не повторяет** `GET /` — сразу `POST /game.php` с `vcode` и
`verify`, далее те же ветки (повторная captcha / `auth_form` / flash) и финальный `GET /main.php`.

## Fallback на альтернативный host

`shouldRetryWithAlternateHost(result)` разрешает **одну** повторную попытку, только если
одновременно:

- `ProxyRuntimeManager.isRunning()` — proxy активен;
- результат не success и не captcha;
- последний HTTP-код из текста ошибки ∈ {400, 403, 405, 407, 429, 500, 502, 503, 504}.

Перед повтором вызывается `NetworkClient.clearCookies()`, host меняется `www` ↔ `non-www`
(`GameServerUrls.alternateAuthBaseUrl`). Подробности — в `auth_proxy.md`.

## Анти-детект (обязательный инвариант)

- Единый браузерный `User-Agent` (Chrome/140) в `AppVars.BROWSER_USER_AGENT`,
  `WebViewConfigurator`, `PinfoActivity` — сверен с реальным трафиком (`Login.har`).
- Прокси вырезает `X-Requested-With`, `X-Android-*`, `Sec-CH-UA*`
  (реальный Chrome по HTTP клиент-хинты **не шлёт** — проверено по `Login.har`).
- `stripClientMarkersFromTarget` убирает служебные параметры `ab_*` / `an_*` из исходящих URL.
- Cache-buster в чате — `Math.random()`, как у браузера, а не timestamp.

## Зависимости

- `com.squareup.okhttp3:okhttp` + `okhttp3.JavaNetCookieJar`;
- `org.jsoup:jsoup` — детекция captcha / `auth_form`;
- `android.webkit.CookieManager` — cookies для `WebView`;
- `FileLogger` — трассировка каждого шага (`files/Logs/Critical/`).

## Типовые проблемы

1. **Экран «Cookie...» после входа** — сессия не доехала до `WebView`.
   → Проверить `AppVars.lastCookies`, работу `CookieSyncHelper` и вызов `CookieManager.flush()`.

2. **Сырой HTML вместо интерфейса** — неверный MIME.
   → В `shouldInterceptRequest` для `main.php` задавать
   `new WebResourceResponse("text/html", "windows-1251", ...)`.

3. **«Неверный логин или пароль»** — сервер вернул страницу с `auth_form`.
   → Обрабатывается штатно. Если пароль верный, проверить кодировку формы (должна быть
   windows-1251) и поле `server`.

4. **Сессия обрывается при смене сервера** — регрессия host-mixing.
   → Убедиться, что в URL/`Host`/cookies стоит домен `neverlands.ru`, а IP используется
   **только** для TCP-подключения. См. раздел 1.

5. **Ошибка входа только через proxy (400/403/405/407)** — см. `auth_proxy.md`,
   срабатывает одноразовый host-fallback.
