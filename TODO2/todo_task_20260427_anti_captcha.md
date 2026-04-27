# Задача: авто-функция Anti-Captcha

## Цель

- [x] Найти единый runtime-контур показа и submit капчи.
- [x] Найти систему быстрых авто-функций и long-press настроек.
- [x] Добавить авто-функцию `Анти-Captcha`.
- [x] Добавить настройки Anti-Captcha API.
- [x] Подключить ImageToTextTask к существующему popup капчи.
- [x] Проверить сборку `:app2:assembleDebug`.
- [x] Проверить UTF-8 без BOM и отсутствие mojibake.
- [x] Проверить по логам устройство с реальным API key: запрос Anti-Captcha стартует, но падает на HTTPS route через localhost proxy.
- [x] Исправить HTTPS route Anti-Captcha: внешний API не идёт через локальный HTTP proxy без CONNECT.
- [x] Задокументировать зависимости Anti-Captcha в коде: popup, license, proxy route, shared submit path.
- [x] Ограничить Anti-Captcha лицензией: только individual `full` или custom grant `anti_captcha`, не public/limited.
- [x] Обновить `app3` для выдачи/описания `anti_captcha` и удаления этой функции из `publicFeatures`.
- [-] Повторно проверить на устройстве боевую и рыбацкую капчу с реальным API key после выдачи/установки нужной лицензии.

## Контекст

Единый runtime popup находится в `MainActivity.showCaptchaDialog(...)`. Через него уже проходят боевые и рыбацкие captcha challenge, поэтому Anti-Captcha нужно подключать именно там, чтобы не плодить отдельные контуры в `FightAuto`, `FishAjaxPhp` и других модулях.

## План

- Добавить `QuickActionType.AUTO_CAPTCHA`.
- Добавить persisted-флаг и настройки в `AutoFunctionsManager`.
- Добавить long-press настройки в `QuickButtonsPanel`.
- Добавить HTTP-клиент `AntiCaptchaManager` для `createTask/getTaskResult`.
- В `MainActivity.showCaptchaDialog(...)` дождаться стабильных bytes картинки, отправить их в Anti-Captcha и при ответе вызвать тот же submit-контур, что и кнопка `ОК`.

## Реализация

- `QuickActionType.AUTO_CAPTCHA` добавлен как авто-функция `Анти-Captcha` с ключом `anti_captcha`.
- `AutoFunctionsManager` хранит флаг включения, API key и параметры `ImageToTextTask` (`phrase`, `case`, `numeric`, `math`, `minLength`, `maxLength`, `languagePool`).
- `QuickButtonsPanel` поддерживает обычный клик для включения/выключения, long-press настройки API, отображение состояния кнопки и выбор функции в таймерах.
- `AntiCaptchaManager` отправляет текущие bytes капчи в `createTask`, опрашивает `getTaskResult` и работает через активный runtime proxy при его наличии.
- `MainActivity.showCaptchaDialog(...)` использует существующий popup: ждёт bytes актуальной картинки, защищается challenge-key от stale-ответов, заполняет поле и отправляет код через тот же submit-контур, что кнопка `ОК`.

## Диагностика 2026-04-27

- В `Logs/Critical/20260427_16_50_mainactivity.log` видно, что `showCaptchaDialog(...)` вызывается, bytes картинки получены, Anti-Captcha стартует.
- В `Logs/Critical/20260427_16_50_anticaptchamanager.log` ошибка: `Unexpected response code for CONNECT: 501`.
- Причина: `AntiCaptchaManager.postJson(...)` отправлял HTTPS `https://api.anti-captcha.com/...` через `ProxyRuntimeManager.getActiveJavaProxyOrNull()`, то есть через локальный `127.0.0.1` proxy runtime. `LocalHttpProxyServer` поддерживает HTTP-трафик игры, но не реализует HTTPS `CONNECT`, поэтому возвращает `501`.
- Исправление: добавлены `ProxyRuntimeManager.getActiveUpstreamJavaProxyOrNull()` и `getActiveUpstreamBasicAuthHeaderOrEmpty()`. `AntiCaptchaManager` теперь для внешнего HTTPS API использует direct route при выключенном профильном proxy или upstream proxy напрямую при включенном профильном proxy. Локальный `127.0.0.1` proxy больше не используется для Anti-Captcha API.

## Лицензирование

- `QuickActionType.AUTO_CAPTCHA` использует feature key `anti_captcha`.
- `LicenseFeature.expandPublicFeatureSpec(...)` удаляет `anti_captcha` из public-набора, включая public `full`.
- Individual `full` grant включает Anti-Captcha, потому что `expandFeatureSpec("full")` разворачивается во все quick actions.
- Manual/custom grant может открыть только Anti-Captcha через `anti_captcha`.
- При истечении `expiresAt` текущего full/custom grant `LicenseRuntime.requireSession(...)` пересобирает public-only session и вызывает `AutoFunctionsManager.disableUnavailableFeatures(...)`; для Anti-Captcha это сбрасывает persisted ON-флаг `KEY_ANTI_CAPTCHA`, но сохраняет API key.
- `app3` теперь показывает `anti_captcha` в списке ручных feature tokens, описывает её в отчётах и вырезает из `publicFeatures` custom CSV.

## Проверки

- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` — успешно.
- [x] Повторная финальная сборка `./gradlew.bat --no-daemon :app2:assembleDebug` после лицензирования и комментариев — успешно.
- [x] `./gradlew.bat --no-daemon :app3:classes` — успешно для CLI-проверок license-tool.
- [x] `app3` individual `full` на временный `.reg` — включает Anti-Captcha.
- [x] `app3` custom grant `anti_captcha` на временный `.reg` — выдаёт только Anti-Captcha.
- [x] `app3` public `full` на временный `.reg` — Anti-Captcha удалена из public-набора.
- [x] `app3` public custom `anti_captcha,auto_fight` на временный `.reg` — в public остаётся только `auto_fight`.
- [x] BOM-проверка изменённых файлов — OK.
- [x] Поиск стандартных mojibake-паттернов в `app2/src/main/java/ru/neverlands/anclient` и `TODO2` — совпадений нет.
- [x] Проверка изменённых Java-файлов на прямой `android.util.Log`/`Log.*` — новых вхождений нет.
- [x] Установка свежего `app2/build/outputs/apk/debug/anclient_v1.1.5.apk` через `adb install -r` — успешно.
- [x] Проверка установленной версии: `versionCode=13`, `versionName=1.1.5`, `lastUpdateTime=2026-04-27 19:20:33`.
- [x] Запуск приложения через `adb shell monkey -p ru.neverlands.anclient 1` — успешно.
