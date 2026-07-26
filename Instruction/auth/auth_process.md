# Процесс авторизации ANClient (app2) — полное описание

> Актуализировано 2026-07-26 по фактическому коду `app2/src/main/java/ru/neverlands/anclient/`.
> Документ описывает **текущую** реализацию, включая исправление выбора серверов DE/KZ.

---

## 1. Ввод данных и запуск

Процесс начинается в `LoginActivity`.

1. Пользователь выбирает **профиль**, **сервер** (DE / KZ / neverlands.ru) и вводит пароль.
2. Для зашифрованного профиля пароль расшифровывается через `CryptoUtils.decrypt(...)`
   (формат `ANC1`, AES-256-GCM; старый 3DES-формат читается для совместимости).
3. Перед сетевыми запросами выполняется **лицензионный gate**:
   `LicenseValidationHandler.validateBeforeLogin(...)`.
4. Вызывается `clearCookiesAndAuthorize(...)` → очистка cookie → `AuthManager.authorize(...)`.

### Очистка старой сессии

Выполняется в двух местах, обе очистки обязательны:

| Что чистится | Где | Метод |
| --- | --- | --- |
| Cookie-store OkHttp | `LoginActivity:752`, `AuthManager:79/132` | `NetworkClient.clearCookies()` |
| Cookie WebView | `LoginActivity:785` | `CookiesManager.clear(callback)` (асинхронно, с `flush()`) |

---

## 2. Выбор сервера (DE / KZ / neverlands.ru) — ключевой момент

Это самое частое место ошибок, поэтому описано отдельно.

### Как устроено

| Сущность | Значение |
| --- | --- |
| URL, заголовок `Host`, ключи cookie | **всегда** `neverlands.ru` (или `www.neverlands.ru`) |
| TCP-подключение | IP выбранного сервера: DE `136.243.18.79`, KZ `213.148.10.84` |
| Выбор игрового мира | поле формы логина `server=de` / `server=KZ` |

Реализация:

* `GameServerUrls.gameBaseUrl(...)` — возвращает **публичный домен**, а не хост сервера.
* `GameServerUrls.connectHostForServer(...)` / `currentConnectHost()` — IP для TCP-подключения,
  используется **только** прокси.
* `LocalHttpProxyServer.resolveRoute(...)` — подставляет IP в `connectHost`, **не трогая**
  `originHost` (то есть заголовок `Host` остаётся доменным).
* `GameServerUrls.loginFormServerCode(...)` — код мира для формы (`de`, `KZ`, пусто для основного),
  добавляется в `AuthManager.buildLoginFormBody(...)`.

### Почему нельзя подставлять IP в URL

Ранее выбор сервера выполнялся как `originHost = selectedHost`, и IP попадал одновременно
в URL, в `Host` и в ключи cookie. Сессия оказывалась разложена по трём хостам:

```
31 x neverlands.ru
18 x www.neverlands.ru
17 x 136.243.18.79
```

Сервер отвечал страницей **«Сеанс работы прерван»** с причиной
«Попытка доступа к ресурсам сайта с другого хоста». Ошибка воспроизводилась и на старых
сборках — это была давняя проблема выбора сервера, а не регрессия.

### Ограничение

При включённом **внешнем upstream-прокси** выбрать конкретный сервер нельзя: DNS резолвит
upstream, а подмена хоста вернула бы проблему с сессией. В этом режиме работа идёт через
публичный домен.

---

## 3. Сетевой auth-flow (`AuthManager`)

Выполняется в фоновом потоке через `NetworkClient.getInstance()` (OkHttp + `JavaNetCookieJar`).

### Основной сценарий — `authorizeInternal(...)`

| Шаг | Запрос | Назначение |
| --- | --- | --- |
| 1 | `GET {authBaseUrl}/` | первичные cookie, в т.ч. `watermark` |
| 2 | `POST {authBaseUrl}/game.php` | `player_nick`, `player_password` (+ `server`), кодировка **windows-1251** |
| 3 | `POST` flash-check *(если сервер запросил)* | поля `flcheck`, `nid` — как в ПК-версии |
| 4 | `GET {authBaseUrl}/main.php` | финализация сессии |

### Сценарий с капчей — `authorizeWithCaptchaInternal(...)`

Если сервер вернул капчу, второй POST содержит дополнительно `vcode` и `verify`.
Это совпадает с поведением браузера: в эталонной записи `Login.har` видно **два** POST на
`/game.php` — первый с логином/паролем, второй с `vcode + verify`.

### Тело формы — `buildLoginFormBody(...)`

Порядок полей (важен для совместимости):

```
[vcode] · player_nick · player_password · [verify] · [server]
```

Кодировка формы — `windows-1251`.

### Fallback по хосту

`shouldRetryWithAlternateHost(...)` + `resolveAlternateAuthBaseUrl(...)` — при определённых
HTTP-ошибках выполняется повторная попытка на зеркальном хосте (`www` ↔ без `www`).

---

## 4. Передача cookie в WebView

`AuthManager` работает через собственный `java.net.CookieManager` (OkHttp) и **не**
синхронизируется с WebView автоматически.

```
AuthManager.collectNeverlandsCookies(...)      собирает host-only + *.neverlands.ru
        ↓  callback onSuccess(List<HttpCookie>)
LoginActivity.onLoginSuccess(...)              AppVars.lastCookies = cookies
        ↓  запуск MainActivity
MainActivity.setupWebViews()                   CookieSyncHelper.applyAuthCookiesToWebView(
                                                   AppVars.lastCookies, "lastCookies_apply")
        ↓
android.webkit.CookieManager                   + flush()
```

`CookieSyncHelper` (пакет `webview`) отвечает за:

* фильтрацию дубликатов по имени cookie;
* запись cookie на **все** игровые хосты из `GameServerUrls.cookieUrls()`;
* зеркалирование между хостами (`syncSessionCookiesAcrossHosts`);
* обязательный `flush()` — без него WebView может не увидеть новую сессию.

> **Важно:** класс `WebViewCookieJar` в проекте присутствует, но **нигде не используется**
> (мёртвый код). Не опирайтесь на него при отладке — реальная синхронизация идёт через
> `AppVars.lastCookies` + `CookieSyncHelper`.

Тот же путь используется при авто-перелогине: `SessionReloginHandler` → `MainActivity` →
`CookieSyncHelper.applyAuthCookiesToWebView(cookies, "session_relogin")`.

---

## 5. Работа сессии после входа

1. `MainActivity` настраивает WebView через `WebViewConfigurator.applyGameSettings(...)`.
2. Загружается `main.php`; запросы перехватываются `WebViewRequestInterceptor`.
3. Весь игровой трафик идёт через `LocalHttpProxyServer` (127.0.0.1), который:
   * подставляет cookie из `CookiesManager`;
   * подключается к IP выбранного сервера;
   * нормализует заголовки (анти-детект, см. `autentification.md`).
4. Фоновое обновление контактов стартует **не сразу**, а через
   `LOGIN_CONTACT_REFRESH_START_DELAY_MS = 8000` мс с шагом 1200 мс —
   иначе пачка `info.cgi` накладывается на загрузку фреймов и сервер отвечает `535`.

---

## 6. Ключевые классы

| Класс | Роль |
| --- | --- |
| `LoginActivity` | UI входа, выбор профиля/сервера, очистка cookie, лицензионный gate |
| `AuthManager` | сетевой auth-flow (OkHttp), сбор cookie |
| `NetworkClient` | единый `OkHttpClient` + `JavaNetCookieJar`, пересборка при смене прокси |
| `GameServerUrls` | адреса, выбор сервера, cookie-хосты, `loginFormServerCode` |
| `CookieSyncHelper` | перенос cookie из OkHttp в WebView + зеркалирование |
| `CookiesManager` | cookie для прокси-пути (обёртка над WebView CookieManager) |
| `LocalHttpProxyServer` | локальный прокси: маршрутизация, cookie, анти-детект |
| `SessionReloginHandler` | автоматический перелогин при обрыве сессии |
| `MainActivity` | игровые WebView, фреймы, чат |

---

## 7. Диагностика по логам

| Маркер | Значение |
| --- | --- |
| `AuthManager: authBaseUrl=..., server=DE, formServer=de` | какой сервер и код мира выбраны |
| `SERVER_ROUTE: connect neverlands.ru:80 -> 136.243.18.79:80 (Host остаётся ...)` | корректная маршрутизация |
| `AUTH_COOKIE_SYNC: applied lastCookies_apply names=[...]` | cookie перенесены в WebView |
| `AUTH_COOKIE_SYNC[after_...]: <host>=count=N` | состояние по каждому хосту |
| `assign: mirrored host-safe cookie name=...` | cookie от сервера сохранена прокси-путём |
| `SESSION_RELOGIN_DETECTED` | сервер оборвал сессию |

**Признак неправильной маршрутизации:** появление IP-адреса в `SESSION_RELOGIN_DETECTED`
или в ключах cookie — значит IP снова попал в URL/`Host`.
