# План задачи: фон при заблокированном экране (Android)

## Контекст и ограничения
- [ ] **Проблема:** при блокировке экрана через ~5 минут останавливаются `Авто-Бой` и связанные авто-функции.
- [ ] **Ограничение 1:** меняем только Android-код (`app/*`), `ABClient/*` не трогаем.
- [ ] **Ограничение 2:** поведение должно остаться 1:1 с ПК-логикой там, где это возможно на Android.

## Цель диагностики
- [ ] Получить воспроизводимый сценарий с точной причиной остановки (жизненный цикл, Doze, таймеры, WebView, сеть).
- [ ] После фикса обеспечить стабильную работу 30+ минут с выключенным экраном без ручного пробуждения.

## Этап 0. Декомпозиция по функциям (выполнено)
- [x] Создан сводный файл функций: `TODO/todo_task_20260302_background_lockscreen_functions.md`.
- [x] Создан детальный TODO по `MainActivity`: `TODO/Debug/todo_MainActivity_lockscreen_functions_20260302.md`.
- [x] Создан детальный TODO по `AutoFunctionsManager`: `TODO/Debug/todo_AutoFunctionsManager_lockscreen_functions_20260302.md`.
- [x] Создан детальный TODO по `RoomManager`: `TODO/Debug/todo_RoomManager_lockscreen_functions_20260302.md`.
- [x] Создан детальный TODO по `WebAppInterface`: `TODO/Debug/todo_WebAppInterface_lockscreen_functions_20260302.md`.
- [x] Создан детальный TODO по `FightViewModel`: `TODO/Debug/todo_FightViewModel_lockscreen_functions_20260302.md`.
- [x] Создан детальный TODO по background infra: `TODO/Debug/todo_BackgroundInfra_lockscreen_20260302.md`.

## Этап 1. Инвентаризация текущего контура
- [x] Проверить точки старта/стопа авто-контуров в `MainActivity` (`onPause`, `onStop`, `onResume`, `onDestroy`) — статический анализ.
- [x] Проверить запуск/остановку периодических задач в `AutoFunctionsManager`, `LezFight`, `RoomManager`, `WebAppInterface` — статический анализ.
- [x] Зафиксировать, какие задачи завязаны на видимость `WebView` и UI-thread — статический анализ.
- [x] Проверить `AndroidManifest.xml`: сервисы, `foregroundServiceType`, разрешения питания/сети — статический анализ.

## Первичный вывод (статический)
- [x] Критичный контур авто-режима привязан к `Activity`/`WebView` и `runOnUiThread`.
- [x] `onPause()` останавливает `roomUsersPolling`, что объясняет остановку части авто-функций при блокировке экрана.
- [x] Для стабильного `screen off` режима потребуется отдельный фоновый контур (foreground service) и контролируемый wake strategy.

## Этап 2. Детальное логирование (до правок логики)
- [x] Добавить TRACE-метки переходов состояний авто-боя: `RUNNING/PAUSED/STOPPED/WAITING` (через `BG_TRACE` в `MainActivity`, `FightViewModel`, `WebAppInterface`, `RoomManager`).
- [x] Добавить TRACE по жизненному циклу + состоянию питания: `isInteractive`, `isDeviceIdleMode`, battery optimization.
- [x] Добавить TRACE по тикам авто-цикла: период, причина пропуска, причина остановки (в `AutoModeForegroundService`).
- [x] Добавить TRACE по сетевому контуру: URL, код ответа, исключение, таймаут (в `MainActivity` polling + сервисный tick).
- [x] Добавить TRACE при блокировке/разблокировке экрана (receiver/observer).

## Этап 3. Проверка гипотез
- [x] **H1:** цикл останавливается из-за `Activity` lifecycle — не подтвердилось по логу `logcat_runtime_20260302_11` (foreground service и тики живут 15+ минут).
- [x] **H2:** цикл throttled/заморожен Doze/App Standby без foreground-service + wakelock — не подтвердилось в данном тесте (`idleMode=false`, регулярные `BG_TRACE uiTick`).
- [x] **H3:** зависимость от JS/WebView-таймеров ломает фон — подтвердилось: `processFightHtml` вызывался, но не было `submit posted` из-за рассинхрона флага авто-боя.
- [x] **H4:** внутренний guard (idle/timeout/protection) ошибочно переводит в STOP — подтверждено частично: `fightLikelyActive=false` при фактической загрузке `fight.frame`.

## Этап 4. План исправления (после подтверждения причины)
- [x] Вынести критичный периодический контур в управляемый фоновый компонент (foreground service).
- [x] Для активного авто-режима включать `PARTIAL_WAKE_LOCK` на время работы; при стопе гарантированно освобождать.
- [x] Перевести ключевые тики на устойчивый scheduler (без зависимости от видимости WebView) — реализован сервисный тик + удержание polling при `onPause`.
- [x] Сохранить троттлинг запросов (не чаще заданного интервала), чтобы не перегружать сервер — добавлен сервисный throttling `room refresh`/`auto turn`.
- [x] Добавить recovery после краткого разрыва сети/renderer restart без ручного вмешательства — добавлен service recovery для возврата в `fight.frame`.

## Реализация (2026-03-02)
- [x] Добавлен `AutoModeForegroundService` (`app/src/main/java/ru/neverlands/abclient/service/AutoModeForegroundService.java`).
- [x] Добавлены разрешения `WAKE_LOCK`/`ACCESS_WIFI_STATE` и регистрация foreground service в `AndroidManifest.xml`.
- [x] `MainActivity` переведён на lockscreen-режим: room-polling не гасится в `onPause`, если авто-режим активен.
- [x] Добавлен screen receiver (`ACTION_SCREEN_OFF/ON/USER_PRESENT`) + синхронизация сервиса по событиям экрана.
- [x] `AutoFunctionsManager` синхронизирует background-service при изменении ключевых флагов.
- [x] `WebAppInterface.AutoBoi()` переключает состояние через `AutoFunctionsManager` (единый контур состояния/сервиса).
- [x] Добавлен throttling в `AutoModeForegroundService` по интервалу walkers и минимуму `AUTO_TURN_MIN_INTERVAL_MS=1000`.
- [x] Добавлен recovery в `AutoModeForegroundService`: при активной fight-сессии и потере `fight.frame` выполняется форс-возврат на боевой URL с cooldown.
- [x] Исправлен runtime-guard в `FightViewModel.processFightHtml`: авто-действия теперь учитывают `AppVars.Autoboi/Profile.LezDoAutoboi`, а не только UI LiveData.
- [x] Усилен детектор активного боя в `AutoModeForegroundService`: добавлен fallback по текущему URL `MainWebView`.

## Анализ лога `logcat_runtime_20260302_11`
- [x] В течение ~15 минут lockscreen `AutoModeFgService` работал стабильно (`uiTick` раз в ~1с).
- [x] Бой не продолжался из-за логической причины, а не из-за остановки фона:
  - `FightViewModel` логировал `autoBattleActive=false` при `AppVars.Autoboi=AutoboiOn`,
  - `AutoModeFgService` логировал `fightLikelyActive=false` даже когда загружался `main.php?get_id=56&act=10&go=inf`.

## Этап 5. Проверка и критерии готовности
- [ ] Сценарий: запустить авто-режим → заблокировать экран на 10/20/30 минут → проверить непрерывность.
- [ ] Убедиться, что нет регресса в ручном режиме и при выключенном авто-бое.
- [ ] Подтвердить по логам: причина каждого stop/start прозрачна и детерминирована.
- [ ] Подготовить финальный отчёт по изменённым классам и зависимостям.

## Что потребуется от теста на устройстве
- [ ] Новый лог: `Logs/logcat_runtime_*.txt` по сценарию 10/20/30 минут lockscreen.
- [ ] Модель устройства, Android-версия, оболочка (MIUI/OneUI/EMUI и т.д.).
- [ ] Подтверждение настроек: battery optimization OFF, unrestricted background ON, autostart ON.
