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
- [ ] Повторно проверить на устройстве боевую и рыбацкую капчу с реальным API key.

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

## Проверки

- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` — успешно.
- [x] BOM-проверка изменённых файлов — OK.
- [x] Поиск mojibake `РЎР|РџС|Ð|Ñ` в `app2/src/main/java/ru/neverlands/anclient` и `TODO2` — совпадений нет.
