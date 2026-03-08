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
| Локальный proxy listener (`localhost:8052+`) | Есть (`ABProxy/Proxy.cs`) | Нет | `[ ]` |
| Авто-подбор свободного порта | Есть | Нет | `[ ]` |
| Upstream gateway через `DoProxy+ProxyAddress` | Есть (`Proxy.Gateway`) | Нет | `[ ]` |
| Basic auth для upstream (`ProxyUserName/ProxyPassword`) | Есть (`Proxy.BasicAuth`) | Нет | `[ ]` |
| Глобальный путь трафика через локальный proxy | Есть (Windows InternetSetOption) | Частично (`WebViewProxyHelper` не активирован) | `[-]` |
| Сессионная обработка request/response | Есть (`Session`, `ServerChatter`, `Parser`) | Нет | `[ ]` |
| Проксирование запросов авторизации | Есть (через LocalProxy) | Нет (прямой OkHttp) | `[ ]` |
| Проксирование WebView-трафика | Есть (браузер через localhost proxy) | Нет (WebView interceptor ходит напрямую) | `[ ]` |
| Cookie-менеджмент в прокси-пайплайне | Есть (`ABProxy/CookiesManager`) | Частично (`proxy/CookiesManager`, но без proxy-runtime) | `[-]` |
| Хранение `<proxy ...>` в профиле | Есть (`UserConfigLoad/Save`) | Отсутствует в load/save `UserConfig.java` | `[ ]` |
| Поля `DoProxy`, `ProxyAddress`, `ProxyUserName`, `ProxyPassword` | Есть | Есть (но несогласованно с `UseProxy`) | `[-]` |
| Единый флаг прокси в UI и runtime | Есть (`DoProxy`) | Нет (`UseProxy` и `DoProxy` расходятся) | `[ ]` |
| Диагностическое логирование proxy-flow | Есть (через ABProxy+UI логи) | Нет целостного контура | `[ ]` |

## Конкретные расхождения в Android
- `UserConfig.java`:
  - поля прокси есть, но нет разбора/сохранения `<proxy ...>` тега в `.profile`.
  - дублируется флаг: `UseProxy` и `DoProxy`.
- `ProfileActivity.java`:
  - пишет `UseProxy`.
- `SettingsActivity.java`:
  - пишет `DoProxy`.
- `WebViewProxyHelper.java`:
  - есть реализация `setProxyOverride/clearProxyOverride`, но нигде не включается по профилю/жизненному циклу.
- `NetworkClient.java` и `AuthManager.java`:
  - нет использования proxy-параметров профиля.
- `WebViewRequestInterceptor.java`:
  - прямой `HttpURLConnection`, без routing через локальный/внешний proxy.

## План реализации (приоритеты)

### P0 — снять архитектурный разрыв
- [ ] Ввести `ProxyRuntimeManager` (единая точка старта/остановки локального proxy).
- [ ] Реализовать `LocalProxyService` с loopback listener.
- [ ] Включать `WebViewProxyHelper.setWebViewProxy(127.0.0.1, port)` после успешного старта.
- [ ] Перевести `NetworkClient` на локальный proxy endpoint.

### P1 — профиль и совместимость
- [ ] Убрать рассинхрон `UseProxy`/`DoProxy` (один источник истины).
- [ ] Добавить load/save `<proxy active address username password>` в `UserConfig`.
- [ ] Добавить миграцию старых Android-профилей без proxy-тега.

### P2 — upstream и безопасность маршрута
- [ ] Реализовать upstream режим (`DoProxy=true && ProxyAddress!=empty`).
- [ ] Реализовать `Proxy-Authorization: Basic ...` для upstream.
- [ ] Добавить валидацию `host:port` и fail-fast ошибки конфигурации.

### P3 — логирование и диагностика
- [ ] Ввести `PROXY_BOOT/PROXY_UPSTREAM/PROXY_SESSION/PROXY_FAIL/PROXY_BINDING`.
- [ ] Добавить dedup на повторяющиеся ошибки подключения.

## Критерии завершения
- [ ] Вход и игровой трафик идут через локальный proxy-контур.
- [ ] Upstream включается строго по профилю и работает с auth.
- [ ] В профиле корректно сохраняются и читаются proxy-настройки 1:1 с C#.
- [ ] Логов достаточно для диагностики без «патчей вслепую».

## Связанные документы
- [x] `Instruction/proxy.md` — целевая архитектура и правила 1:1 порта.
- [x] `TODO/todo_task_20260308_proxy_1to1_port.md` — пошаговый рабочий план.
