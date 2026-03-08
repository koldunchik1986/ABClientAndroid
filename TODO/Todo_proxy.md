# Todo_proxy.md — итоговый diff по прокси (C# ABClient ↔ Android)

## Область анализа
- Эталон (read-only): `ABClient/ABProxy/*`, `ABClient/Program.cs`, `ABClient/MyProfile/UserConfig*`.
- Android: `app/src/main/java/...` и `app/src/main/res/...`.

## Ключевые наблюдения
- В ПК версии прокси — системообразующий слой, запускается до основного UI.
- В Android версии сейчас прокси — только частично в профиле/настройках; сетевой runtime через прокси фактически не работает.

## Матрица соответствия (Diff)

| Функциональный блок | C# ABClient | Android | Статус |
| --- | --- | --- | --- |
| Локальный proxy listener (`localhost:8052+`) | Есть (`ABProxy/Proxy.cs`) | Есть (`ProxyRuntimeManager` + `LocalHttpProxyServer`) | `[x]` |
| Авто-подбор свободного порта | Есть | Есть (`LocalHttpProxyServer` bind fallback) | `[x]` |
| Upstream gateway через `DoProxy+ProxyAddress` | Есть (`Proxy.Gateway`) | Есть (`ProxyRuntimeManager` + upstream route) | `[x]` |
| Basic auth для upstream (`ProxyUserName/ProxyPassword`) | Есть (`Proxy.BasicAuth`) | Есть (`Proxy-Authorization` в runtime) | `[x]` |
| Глобальный путь трафика через локальный proxy | Есть (Windows InternetSetOption) | Есть (WebView override + Java proxy endpoint) | `[x]` |
| Сессионная обработка request/response | Есть (`Session`, `ServerChatter`, `Parser`) | Есть (base HTTP session-handler) | `[x]` |
| Проксирование запросов авторизации | Есть (через LocalProxy) | Есть (`NetworkClient` через runtime proxy) | `[x]` |
| Проксирование WebView-трафика | Есть (браузер через localhost proxy) | Есть (`WebViewProxyHelper` + runtime) | `[x]` |
| Cookie-менеджмент в прокси-пайплайне | Есть (`ABProxy/CookiesManager`) | Частично (`proxy/CookiesManager`, но без proxy-runtime) | `[-]` |
| Хранение `<proxy ...>` в профиле | Есть (`UserConfigLoad/Save`) | Есть (load/save в `UserConfig.java`) | `[x]` |
| Поля `DoProxy`, `ProxyAddress`, `ProxyUserName`, `ProxyPassword` | Есть | Есть (с runtime-синхронизацией) | `[x]` |
| Единый флаг прокси в UI и runtime | Есть (`DoProxy`) | Частично (`DoProxy`/`UseProxy` нормализуются в профиле) | `[-]` |
| Диагностическое логирование proxy-flow | Есть (через ABProxy+UI логи) | Есть (`PROXY_*` logcat-маркеры) | `[x]` |

## Конкретные расхождения в Android
- ProfileActivity.java:
  - пишет UseProxy (legacy-ветка UI).
- SettingsActivity.java:
  - пишет DoProxy (основная ветка UI).
- UserConfig.java:
  - добавлены load/save для <proxy active address username password>;
  - добавлена нормализация UseProxy/DoProxy в `normalizeProxyFlags()`.
- WebViewProxyHelper.java:
  - интегрирован в lifecycle через ProxyRuntimeManager.
- NetworkClient/LoginActivity:
  - используют единый runtime proxy endpoint до auth-flow.
- WebViewRequestInterceptor/NeverApi/WebAppInterface:
  - прямые HttpURLConnection переведены на ProxyRuntimeManager.getActiveJavaProxyOrNull().
## План реализации (приоритеты)

### P0 — снять архитектурный разрыв
- [x] Ввести `ProxyRuntimeManager` (единая точка старта/остановки локального proxy).
- [-] Реализовать `LocalProxyService` с loopback listener.
- [x] Включать `WebViewProxyHelper.setWebViewProxy(127.0.0.1, port)` после успешного старта.
- [x] Перевести `NetworkClient` на локальный proxy endpoint.

### P1 — профиль и совместимость
- [x] Убрать рассинхрон `UseProxy`/`DoProxy` (один источник истины).
- [x] Добавить load/save `<proxy active address username password>` в `UserConfig`.
- [x] Добавить миграцию старых Android-профилей без proxy-тега.

### P2 — upstream и безопасность маршрута
- [x] Реализовать upstream режим (`DoProxy=true && ProxyAddress!=empty`).
- [x] Реализовать `Proxy-Authorization: Basic ...` для upstream.
- [x] Добавить валидацию `host:port` и fail-fast ошибки конфигурации.

### P3 — логирование и диагностика
- [x] Ввести `PROXY_BOOT/PROXY_UPSTREAM/PROXY_SESSION/PROXY_FAIL/PROXY_BINDING`.
- [x] Добавить dedup на повторяющиеся ошибки подключения.

## Критерии завершения
- [ ] Вход и игровой трафик идут через локальный proxy-контур.
- [ ] Upstream включается строго по профилю и работает с auth.
- [ ] В профиле корректно сохраняются и читаются proxy-настройки 1:1 с C#.
- [ ] Логов достаточно для диагностики без «патчей вслепую».

## Связанные документы
- [x] `Instruction/proxy.md` — целевая архитектура и правила 1:1 порта.
- [x] `TODO/todo_task_20260308_proxy_1to1_port.md` — пошаговый рабочий план.

