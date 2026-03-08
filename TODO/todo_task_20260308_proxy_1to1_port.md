# TODO: Портирование proxy-flow 1:1 из ПК ABClient (2026-03-08)

## Контекст
- В C# версии (`ABClient`) прокси-слой обязателен в runtime: приложение стартует локальный прокси перед запуском главной формы.
- В Android версии сейчас есть только частичная подготовка:
  - поля профиля (`DoProxy`/`UseProxy`, `ProxyAddress`, `ProxyUserName`, `ProxyPassword`);
  - UI-настройки прокси в `SettingsActivity` и `ProfileActivity`;
  - helper для `WebView` (`WebViewProxyHelper`), но он не подключен к жизненному циклу;
  - нет локального прокси-сервера, нет upstream-туннеля и нет связки с HTTP-потоками.

## Цель
- Восстановить 1:1 архитектурный принцип ПК версии:
  - всегда работать через локальный прокси;
  - при заполненном `DoProxy + ProxyAddress` поднимать upstream-туннель через внешний прокси;
  - при пустом/выключенном прокси ходить наружу напрямую, но через локальный прокси-слой.

## Ограничения
- [x] Не изменять файлы в `ABClient` (эталон, read-only).
- [x] Все изменения только в `app/...`.
- [x] Кодировка всех новых/изменённых текстовых файлов: UTF-8 without BOM.

## Этап 1. Аудит C# (эталон)
- [x] Проверить запуск proxy в `Program.cs`.
- [x] Проверить инициализацию в `ABProxy/Proxy.cs`:
  - локальный listener с авто-подбором порта от `8052`;
  - `Gateway` + `BasicAuth` из `DoProxy/ProxyAddress/ProxyUserName/ProxyPassword`;
  - назначение системного прокси на `localhost:{listenPort}`.
- [x] Проверить путь туннелирования в `ABProxy/ServerChatter.cs` и `ABProxy/Session.cs`:
  - при `Gateway != null` подключение через upstream;
  - добавление `Proxy-Authorization` при BasicAuth.
- [x] Проверить формат хранения proxy-полей в профиле (`MyProfile/UserConfigLoad|Save|Constants`).

## Этап 2. Аудит Android (текущее состояние)
- [x] Проверить хранение proxy-полей в `model/UserConfig.java`.
- [x] Проверить UI-редактирование proxy в `SettingsActivity` и `ProfileActivity`.
- [x] Проверить runtime-использование proxy в сетевом стеке (`AuthManager`, `NetworkClient`, `WebViewRequestInterceptor`, `WebView`).
- [x] Проверить наличие локального proxy-сервера/сервисов (на текущий момент отсутствует).

## Этап 3. Diff (реализовано / отсутствует)
- [x] Сформировать матрицу соответствия C# ↔ Android.
- [x] Выделить первопричины текущих проблем входа и нестабильности (отсутствие единого proxy-runtime слоя).

## Этап 4. Проектирование 1:1 для Android
- [ ] Спроектировать `ProxyRuntimeManager`:
  - [ ] жизненный цикл локального прокси;
  - [ ] публикация активного порта и режима upstream/direct.
- [ ] Спроектировать `LocalProxyService` (foreground/bound) + accept loop.
- [ ] Спроектировать session pipeline:
  - [ ] разбор запроса;
  - [ ] выбор назначения (upstream или origin);
  - [ ] прокидка/нормализация заголовков;
  - [ ] поддержка `Proxy-Authorization` для upstream.
- [ ] Спроектировать интеграцию с WebView:
  - [ ] `WebViewProxyHelper.setWebViewProxy("127.0.0.1", activePort, ...)`;
  - [ ] fallback clear/restore при остановке.
- [ ] Спроектировать интеграцию с OkHttp/авторизацией:
  - [ ] единая фабрика клиента с `Proxy(Type.HTTP, 127.0.0.1:activePort)`;
  - [ ] единый cookie-контур.
- [ ] Спроектировать совместимость профиля:
  - [ ] убрать расхождение `UseProxy` vs `DoProxy` (один источник истины);
  - [ ] добавить чтение/запись `<proxy ...>` в `.profile`.

## Этап 5. Реализация
- [ ] Реализовать `ProxyRuntimeManager`.
- [ ] Реализовать `LocalProxyService` и базовый session-handler.
- [ ] Включить proxy-runtime на старте приложения/сессии.
- [ ] Подключить WebView к локальному proxy через `ProxyController`.
- [ ] Подключить Auth/NetworkClient к локальному proxy.
- [ ] Доработать `UserConfig` для полноценной сериализации `<proxy>`.

## Этап 6. Логирование и диагностика
- [ ] Ввести обязательные logcat-маркеры:
  - [ ] `PROXY_BOOT` (start/stop, port, mode);
  - [ ] `PROXY_UPSTREAM` (gateway/auth on/off);
  - [ ] `PROXY_SESSION` (client->target, status, latency);
  - [ ] `PROXY_AUTH` (без утечки секретов; только факты наличия/режима);
  - [ ] `PROXY_FAIL` (исключения и fallback).
- [ ] Добавить dedup-ограничение для шумных повторяющихся ошибок.

## Критерии готовности
- [ ] Вход в игру не деградирует на «двойной таймаут» из-за отсутствия proxy runtime.
- [ ] При `DoProxy=false` трафик стабильно идет через локальный proxy (direct egress).
- [ ] При `DoProxy=true` и `ProxyAddress` трафик идет через локальный proxy + upstream tunnel.
- [ ] Настройки прокси полностью читаются/пишутся в `.profile` совместимо с C# форматом.
- [ ] Логи позволяют точно восстановить путь каждого сбоя.

## Артефакты этой сессии
- [x] Подготовить детальную инструкцию в `Instruction/proxy.md`.
- [x] Подготовить сводный diff/roadmap в `TODO/Todo_proxy.md`.
