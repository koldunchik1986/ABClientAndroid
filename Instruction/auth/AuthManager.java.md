# Анализ и архитектура `AuthManager.java`

> Актуализировано: 2026-07-26.
> **Файл:** `app2/src/main/java/ru/neverlands/anclient/AuthManager.java` (604 строки).
>
> Прошлая редакция описывала `app/src/main/java/ru/neverlands/abclient/AuthManager.java` и
> устарела по существу: класс больше **не статическая утилита**, **не использует callback'и**,
> flow **не трёхшаговый**, и cookies собираются иначе.

## 1. Назначение

`AuthManager` инкапсулирует всю HTTP-логику входа на `neverlands.ru`: последовательность
запросов, кодировку формы, детекцию captcha и flash-пароля, host-fallback в proxy-режиме и
сбор итогового набора cookies.

**Ключевое отличие от старой версии:** методы **синхронные** и возвращают `AuthResult`.
Никаких `AuthCallback`/`onSuccess`/`onFailure` больше нет — за фоновый поток и UI отвечает
вызывающий код (`LoginActivity`).

## 2. Публичный API

```java
AuthResult authorize(String username, String password);
AuthResult authorize(String username, String password, String flashPassword);
AuthResult authorize(String username, String password, String flashPassword, String gameServerCode);

AuthResult authorizeWithCaptcha(String username, String password, String vcode, String verify);
AuthResult authorizeWithCaptcha(String username, String password, String flashPassword,
                                String vcode, String verify);
AuthResult authorizeWithCaptcha(String username, String password, String flashPassword,
                                String gameServerCode, String vcode, String verify);
```

Короткие перегрузки подставляют `AppVars.Profile.GameServerCode` (или
`GameServerUrls.DEFAULT_SERVER_CODE`, если профиля нет) и пустой flash-пароль.

## 3. Зависимости

| Зависимость | Назначение |
| --- | --- |
| `NetworkClient` | Общий `OkHttpClient` + `JavaNetCookieJar` (единый cookie-store) |
| `ProxyRuntimeManager` | Признак proxy-режима (влияет на разрешение host-fallback) |
| `GameServerUrls` | base URL, зеркальный host, код сервера для поля формы |
| `Jsoup` | Детекция captcha (`nl_reg_code.php` + `input[name=vcode]`) и `auth_form` |
| `FileLogger` | Пошаговая трассировка в `files/Logs/Critical/` |
| `AppVars.BROWSER_USER_AGENT` | Единый браузерный UA (анти-детект) |

`NetworkClient`: `DIRECT_TIMEOUT_SECONDS = 30`, `PROXY_TIMEOUT_SECONDS = 60`; клиент
пересоздаётся при изменении `ProxyRuntimeManager.getRuntimeSignature()`.

## 4. Алгоритм (`authorizeInternal`) — 4 шага

### Шаг 1: `GET {base}/`
Заголовки `User-Agent`, `Accept`, `Accept-Language`.
Получение первичных cookies (`watermark`).
Провал → `AuthResult("Ошибка получения начальной страницы: {code}")`.

### Шаг 2: `POST {base}/game.php`
Тело `buildLoginFormBody(...)`, заголовки `Referer` = `{base}/`, `Origin` = `{base}`.
Провал → `AuthResult("Ошибка авторизации: {code}")`.

Разбор тела (`Jsoup`), приоритет сверху вниз:
1. captcha (`img[src*='nl_reg_code.php']` + `input[name=vcode]`) → `AuthResult(captchaUrl, vcode)`;
2. `auth_form` в теле → `AuthResult("Ошибка авторизации: неверный логин или пароль.")`;
3. flash-страница → шаг 3.

### Шаг 3: flash-пароль (условный)
`submitFlashPasswordIfRequired(...)` — паритет с C# `PostFilter.GamePhp`.
Срабатывает **только** если flash-пароль непустой **и** `FLASH_PLID_PATTERN` нашёл `plid`
в `flashvars`. Отправляет `POST {base}/game.php` с `flcheck` + `nid` (windows-1251).
Провал → `AuthResult("Ошибка ввода Flash-пароля: {code}")`.
Возврат `null` означает «flash не требуется / успешно» — flow продолжается.

### Шаг 4: `GET {base}/main.php`
Перед запросом `Thread.sleep(500)`. Заголовок `Referer` = `{base}/game.php`.
Провал → `AuthResult("Ошибка финализации сессии: {code}")`.
Успех → `collectNeverlandsCookies(...)` → `AuthResult(cookies)`.

### Captcha-вариант (`authorizeWithCaptchaInternal`)
Тот же сценарий **без шага 1**: сразу `POST /game.php` с `vcode` + `verify`, затем те же
ветки разбора и финальный `GET /main.php`. Повторная captcha возвращает новый
`captchaUrl` + `vcode`.

## 5. Формирование формы — `buildLoginFormBody`

`FormBody.Builder(Charset.forName("windows-1251"))`, порядок полей значим:

| Поле | Условие |
| --- | --- |
| `vcode` | только если непустое (captcha-flow) |
| `player_nick` | всегда |
| `player_password` | всегда |
| `verify` | только если непустое (captcha-flow) |
| `server` | `GameServerUrls.loginFormServerCode(...)` → `de` / `KZ` |

Поле `server` — это и есть механизм выбора игрового сервера **на уровне формы**, а не через
подмену host (см. §6).

## 6. Выбор host — `resolveAuthBaseUrl`

```java
String baseUrl = GameServerUrls.authBaseUrl(proxyActive, gameServerCode);
```

`GameServerUrls.authBaseUrl(...)` делегирует в `gameBaseUrl(...)`, который **игнорирует и
`proxyActive`, и `serverCode`** и всегда возвращает `http://neverlands.ru`.

Это следствие фикса обрыва сессии: домен в URL/`Host`/cookies обязан быть одинаковым, иначе
сессия разъезжается по хостам. Физическое подключение к нужному серверу выполняется
на уровне транспорта (`GameServerUrls.connectHostForServer`, `LocalHttpProxyServer.resolveRoute`).

Логируется: `authBaseUrl=..., proxyActive=..., server=..., formServer=...`.

## 7. Host-fallback — `shouldRetryWithAlternateHost`

Разрешает **ровно одну** повторную попытку при совпадении всех условий:

- `ProxyRuntimeManager.isRunning()`;
- результат не `isSuccess()` и не `isCaptchaRequired()`;
- код из `extractLastHttpCode(errorMessage)` ∈ {400, 403, 405, 407, 429, 500, 502, 503, 504}.

`extractLastHttpCode` использует `LAST_HTTP_CODE_PATTERN = (\d{3})(?!.*\d)` — берёт
последнее трёхзначное число из текста ошибки (например, из «Ошибка финализации сессии: 405»).

Перед повтором: `NetworkClient.clearCookies()`.
Новый host: `GameServerUrls.alternateAuthBaseUrl(...)` — зеркало `www` ↔ `non-www`.
Если зеркало совпало с исходным — повтор не выполняется.

> Ограничение fallback'а captcha-случаем и success'ом важно: повтор POST'а с теми же
> credentials при уже выданной captcha приводил бы к лишней нагрузке и риску блокировки.

## 8. Управление cookies

### `collectNeverlandsCookies(cookieManager, responseCookies)`
1. Обходит **весь** `CookieStore` (не один URI, как в старой версии);
2. оставляет host-only cookies (пустой domain) и домены, прошедшие
   `GameServerUrls.isNeverlandsCookieDomain`;
3. дедуплицирует по `name|domain|path`;
4. если пусто — fallback на явные URI `neverlands.ru`, затем `www.neverlands.ru`;
5. добавляет cookies из `responseCookies` (сырые `Set-Cookie`);
6. логирует итог: `collected cookies count=N names=[...]`.

### `collectResponseCookies(response, out, stage)`
Параллельный сбор напрямую из заголовков `Set-Cookie` на каждом шаге. Стадии:
`initial_get`, `login_post`, `flash_post`, `main_get`, `captcha_login_post`, `captcha_main_get`.
Некорректные заголовки не роняют flow — логируются как `ignored malformed Set-Cookie`.

**Зачем дублирование:** `java.net.CookieStore` отбрасывает cookies с нестандартными
атрибутами, которые игровой сервер присылает. Без сырого перехвата часть сессии терялась.

### Куда уходят cookies дальше
`AuthResult` → `LoginActivity` → `AppVars.lastCookies` (`LoginActivity:961`) →
`CookieSyncHelper` → `android.webkit.CookieManager` + `flush()` → `WebView`.

> `WebViewCookieJar.java` в проекте **не используется** (0 ссылок вне файла) — мёртвый код,
> кандидат на удаление. Синхронизацию делает `CookieSyncHelper`.

## 9. `AuthResult` — три взаимоисключающих состояния

| Конструктор | Состояние | Поля |
| --- | --- | --- |
| `AuthResult(List<HttpCookie> cookies)` | успех | `isSuccess = true`, `cookies` |
| `AuthResult(String captchaUrl, String vcode)` | нужна captcha | `isCaptchaRequired = true` |
| `AuthResult(String errorMessage)` | ошибка | `errorMessage` |

Проверять строго в порядке: `isSuccess()` → `isCaptchaRequired()` → иначе ошибка
(как в `LoginActivity:864` / `:868`).

## 10. Схема взаимодействия

```
LoginActivity
  ├─ расшифровка профиля (+ ленивая миграция 3DES → ANC1)
  ├─ AppVars.lastCookies = null
  └─ AuthManager.authorize(user, pass, flash, serverCode)   [фоновый поток]
        ├─ resolveAuthBaseUrl        → http://neverlands.ru
        ├─ GET /                     → watermark
        ├─ POST /game.php            → captcha? auth_form? flash?
        ├─ POST /game.php (flcheck)  → если flash-страница
        ├─ GET /main.php             → финализация
        └─ collectNeverlandsCookies  → AuthResult(cookies)
                 │
                 ├─ success  → AppVars.lastCookies → CookieSyncHelper → flush() → MainActivity
                 ├─ captcha  → показ картинки → authorizeWithCaptcha(...)
                 └─ error    → сообщение; при proxy + {400,403,405,...} → один host-fallback
```

## 11. На что смотреть в логах

- `AuthManager: authBaseUrl=..., proxyActive=..., server=..., formServer=...` — выбор host'а;
- `AuthManager: 1./2./3. ...` — прохождение шагов;
- `AuthManager: Captcha detected. URL: ...` — ветка captcha;
- `AuthManager: Flash password POST request, nid=...` — ветка flash;
- `AuthManager: captured response cookies stage=..., names=[...]` — сырые `Set-Cookie`;
- `AuthManager: collected cookies count=N names=[...]` — итоговый набор;
- `AuthManager: proxy fallback for authorize, primary=..., alternate=..., reason=...` — host-fallback.

Пустой или подозрительно короткий `names=[...]` на успешном входе — прямой признак того,
что сессия до `WebView` не доедет.
