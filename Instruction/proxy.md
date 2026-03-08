# Proxy 1:1: эталон C# → Android

## 1. Эталонная логика в C# (ABClient)

## 1.1 Запуск прокси как обязательный этап приложения
- `ABClient/Program.cs`:
  - создаёт `new Proxy()`;
  - вызывает `proxy.Start()` до запуска `FormMain`;
  - при ошибке инициализации прокси не запускает основной клиент.

Следствие: proxy-слой в ПК версии не опционален в runtime, это базовый сетевой контур.

## 1.2 Локальный listener + upstream gateway
- `ABClient/ABProxy/Proxy.cs`:
  - стартовый порт `8052`, поиск свободного порта инкрементом;
  - listener socket + async accept;
  - режим upstream:
    - если `AppVars.Profile.DoProxy == true`, то:
      - `Gateway = IPEndPointFromHostPortString(Profile.ProxyAddress)`;
      - формируется `BasicAuth` (`ProxyUserName:ProxyPassword` → base64);
      - создаётся `AppVars.LocalProxy = new WebProxy("http://{ProxyAddress}")`.
  - выставляет системный proxy (`InternetSetOption`) на `localhost:{listenPort}`.

## 1.3 Сессия и туннелирование
- `ABClient/ABProxy/Session.cs`:
  - принимает клиентский HTTP-запрос;
  - если есть `Proxy.BasicAuth`, добавляет `Proxy-Authorization`;
  - делает pre/post filter и работу с cookies/cache.
- `ABClient/ABProxy/ServerChatter.cs`:
  - `ConnectToHost()`:
    - если `Proxy.Gateway != null`, соединяется с upstream proxy;
    - иначе соединяется с origin host напрямую.

Итог: всегда есть локальный прокси-пайплайн, а upstream включается по профилю.

## 1.4 Формат профиля (источник истины)
- `ABClient/MyProfile/UserConfigConstants.cs`:
  - `<proxy active="..." address="..." username="..." password="..."/>`
- `UserConfigLoad.cs`:
  - читает `DoProxy`, `ProxyAddress`, `ProxyUserName`, `ProxyPassword`.
- `UserConfigSave.cs`:
  - сохраняет те же поля в тег `proxy`.

---

## 2. Текущее состояние Android

## 2.1 Что уже есть
- Профильные поля:
  - `UserConfig.DoProxy`
  - `UserConfig.UseProxy` (дублирующий legacy-флаг)
  - `ProxyAddress`, `ProxyUserName`, `ProxyPassword`
- UI:
  - `SettingsActivity` работает с `DoProxy` + proxy-полями;
  - `ProfileActivity` работает с `UseProxy` + proxy-полями.
- Технический helper:
  - `WebViewProxyHelper` (wrapper над `ProxyController.setProxyOverride`).
- Константы:
  - `AppVars.LocalProxyAddress=127.0.0.1`, `AppVars.LocalProxyPort=8052`.

## 2.2 Что отсутствует (критично)
- Нет локального proxy-сервера (`ServerSocket` + session pipeline).
- Нет upstream gateway режима с auth.
- `WebViewProxyHelper` не подключён к runtime lifecycle.
- `AuthManager/NetworkClient` не маршрутизируют трафик через локальный proxy.
- `WebViewRequestInterceptor` ходит в сеть напрямую (`HttpURLConnection`) без привязки к proxy-профилю.
- `UserConfig` не сериализует тег `<proxy ...>` в `.profile` (нет load/save ветки proxy-тега).
- Расхождение флагов `UseProxy` vs `DoProxy` создаёт неоднозначность поведения.

---

## 3. Целевая 1:1 архитектура Android

## 3.1 Единый runtime-компонент
- `ProxyRuntimeManager` (новый):
  - хранит состояние: `stopped/starting/running/failed`;
  - активный локальный порт;
  - режим egress: `DIRECT` или `UPSTREAM`.

Зависимости:
- `UserConfig.DoProxy + ProxyAddress + ProxyUserName + ProxyPassword`;
- `AppVars.LocalProxyAddress/Port`;
- lifecycle приложения (`ABClientApplication` / login-session bootstrap).

## 3.2 Локальный proxy сервис
- `LocalProxyService` (новый, foreground или bound):
  - поднимает listener на loopback (`127.0.0.1`, старт 8052 + fallback);
  - принимает HTTP-сессии;
  - для каждой сессии:
    - парсит request line + headers;
    - при upstream-режиме отправляет запрос через внешний proxy;
    - при direct-режиме отправляет запрос на origin;
    - прокидывает response обратно клиенту.

Минимум для parity:
- GET/POST для основных игровых URL;
- `Proxy-Authorization: Basic ...` при upstream auth;
- безопасная нормализация hop-by-hop заголовков.

## 3.3 Интеграция WebView
- После успешного старта локального proxy:
  - `WebViewProxyHelper.setWebViewProxy("127.0.0.1", activePort, ...)`.
- При остановке:
  - `WebViewProxyHelper.clearWebViewProxy()`.

Примечание:
- Это эквивалент C# поведения «браузер идёт через localhost:{port}».

## 3.4 Интеграция OkHttp/Auth
- `NetworkClient` должен использовать локальный proxy всегда, пока runtime active:
  - `proxy(new Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", activePort)))`.
- Это касается:
  - `AuthManager.authorize(...)`
  - `AuthManager.authorizeWithCaptcha(...)`
  - всех внутренних okhttp-клиентов.

## 3.5 Профиль и совместимость
- Убрать двусмысленность:
  - единый флаг `DoProxy` как источник истины.
- Обязательно добавить в `UserConfig`:
  - чтение тега `<proxy ...>`;
  - сохранение тега `<proxy ...>`;
  - обратная совместимость со старыми Android-профилями без этого тега.

---

## 4. Контракт логирования (обязательный)

- `PROXY_BOOT`:
  - start/stop, выбранный порт, mode(DIRECT/UPSTREAM), причина stop/fail.
- `PROXY_UPSTREAM`:
  - gateway host:port, auth enabled/disabled (без вывода логина/пароля).
- `PROXY_SESSION`:
  - request id, target host, status code, bytes in/out, latency.
- `PROXY_FAIL`:
  - исключения сокета/парсинга/туннеля с dedup.
- `PROXY_BINDING`:
  - факт применения/снятия WebView proxy override.

---

## 5. Минимальные критерии parity

- Приложение стартует proxy-runtime до начала активного игрового трафика.
- При `DoProxy=false` трафик всё равно идёт через локальный proxy (direct egress).
- При `DoProxy=true` + `ProxyAddress` локальный proxy маршрутизирует через upstream.
- `ProxyUserName/ProxyPassword` применяются в upstream запросах как Basic auth.
- Профиль `.profile` содержит/восстанавливает `<proxy ...>` совместимо с C#.
- По logcat можно восстановить любой сбой без догадок.
