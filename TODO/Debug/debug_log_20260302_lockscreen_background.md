# Debug log: lockscreen background (2026-03-02)

## 1) Действие
- Проведён статический аудит цепочки `MainActivity` → `RoomManager` → `WebAppInterface` → `FightViewModel` → `AutoFunctionsManager`.
## Результат
- Подтверждено: `onPause()` останавливал `roomUsersPolling`, а ключевые действия завязаны на `WebView`/UI-thread.

## 2) Действие
- Добавлен foreground service: `AutoModeForegroundService`.
## Результат
- Реализован service tick (1 сек), foreground notification, `PARTIAL_WAKE_LOCK`, `WifiLock`, синхронизация start/stop по авто-флагам.

## 3) Действие
- Интегрирован сервис в `MainActivity` lifecycle.
## Результат
- Добавлены:
  - сохранение polling при `onPause`, если авто-режим активен,
  - screen-state receiver (`SCREEN_OFF/ON/USER_PRESENT`),
  - `BG_TRACE` логирование power/lifecycle состояния.

## 4) Действие
- Синхронизированы переключатели в `AutoFunctionsManager` + `WebAppInterface.AutoBoi()`.
## Результат
- Единый контур: изменение ключевых флагов теперь вызывает `AutoModeForegroundService.syncServiceState(...)`.

## 5) Действие
- Добавлены разрешения/манифестные изменения для фона.
## Результат
- `AndroidManifest.xml`: `WAKE_LOCK`, `ACCESS_WIFI_STATE`, `<service ... foregroundServiceType="dataSync" />`.

## 6) Действие
- Выполнена проверка сборки.
## Результат
- `./gradlew :app:compileDebugJavaWithJavac -x lint` — успешно.

## 7) Действие
- Продолжена реализация по TODO: закрыты пункты про троттлинг и recovery.
## Результат
- В `AutoModeForegroundService` добавлен throttle:
  - `room refresh` по `walkers_poll_interval_sec`,
  - `auto turn` минимум 1 сек.
- Добавлен recovery:
  - если fight-сессия активна, но `url_main_top` не на `fight.frame`, сервис форсирует возврат на `main.php?get_id=56&act=10&go=inf` (cooldown 5 сек).

## 8) Действие
- Проанализирован 15-минутный лог lockscreen: `Logs/logcat_runtime_20260302_11.txt`.
## Результат
- Фоновый контур не останавливался: `AutoModeFgService` выдавал `uiTick` непрерывно.
- Найдена логическая причина отсутствия авто-удара:
  - `FightViewModel.processFightHtml`: `autoBattleActive=false` при `AppVars.Autoboi=AutoboiOn`,
  - `AutoModeFgService`: `fightLikelyActive=false` в моменты загрузки `fight.frame`.

## 9) Действие
- Внесены фиксы по результатам анализа lockscreen-лога.
## Результат
- `FightViewModel.processFightHtml` переведён на runtime-проверку авто-боя:
  - `UI flag` **или** `AppVars.Autoboi` **или** `Profile.LezDoAutoboi`.
- В `AutoModeForegroundService` добавлен fallback-детектор боя по `MainWebView.getUrl()`.
