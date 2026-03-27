# Инструкция по `AutoFunctionsManager`

## Назначение файла
`AutoFunctionsManager` — единая точка управления авто-функциями Android-клиента:
- хранение и чтение флагов/настроек авто-режимов;
- переключение режимов из `QuickButtonsPanel`;
- синхронизация с background-контуром;
- запуск внутренних циклов (auto-attack, walkers, auto-compass и др.).

## Базовый статус портирования
- [x] Базовый менеджер авто-функций реализован.
- [x] Интеграция с `QuickActionType` и `QuickButtonsPanel` выполнена.
- [x] Интеграция с `AutoModeForegroundService` выполнена.

## Обновление 2026-03-02 (buttonWalkers parity)
- [x] `AUTO_ATTACK` переведён на C#-семантику: активность определяется только `AutoAttackToolId != 0`.
- [x] Добавлена миграция legacy `auto_attack=true` -> `toolId=1` (если `toolId` отсутствовал).
- [x] `setAutoAttackEnabled(boolean)` оставлен как compatibility-wrapper (`0` / `lastNonZeroToolId`).
- [x] `setAutoAttackToolId(int)` при `toolId != 0` автоматически включает `LOCATION_TRACKING`.
- [x] `LOCATION_TRACKING` синхронизируется с runtime `AppVars.DoShowWalkers`.
- [x] Добавлены настройки интервала walkers polling:
  - [x] ключ `auto_function_walkers_poll_interval_sec`,
  - [x] whitelist `1/2/5/10`,
  - [x] default `1`.

## Обновление 2026-03-27 (AUTO_COMPASS)
- [x] Добавлена авто-функция `AUTO_COMPASS`.
- [x] Добавлены публичные методы:
  - [x] `isAutoCompassEnabled() / setAutoCompassEnabled(...) / toggleAutoCompass()`;
  - [x] `setAutoCompassTargetNick(...) / getAutoCompassTargetNick()`;
  - [x] `setAutoCompassHuntMode(...) / isAutoCompassHuntMode()`;
  - [x] `setAutoCompassPollIntervalSec(...) / getAutoCompassPollIntervalSec()`;
  - [x] `setAutoCompassManualCellsCsv(...) / getAutoCompassManualCellsCsv()`;
  - [x] `startManualCompassSearch(...)`;
  - [x] `tickAutoCompass()` и `onRoomUsersUpdated(...)`.
- [x] Реализован runtime-контур:
  - [x] pinfo polling по цели;
  - [x] резолв клеток по `ExtMap.Cells` (`Name`, затем `Tooltip`);
  - [x] выбор ближайшей клетки через `MapPath`;
  - [x] переход через существующий `startAutoMoving(...)`;
  - [x] stop/reason сообщения в чат.
- [x] `AUTO_COMPASS` добавлен в `isFunctionEnabled(...)`, `toggleFunction(...)`, `disableAll()`.
- [x] Синхронизация с background-контуром включена.

## Связанные файлы
- `app/src/main/java/ru/neverlands/abclient/manager/AutoFunctionsManager.java`
- `app/src/main/java/ru/neverlands/abclient/manager/NeverApi.java`
- `app/src/main/java/ru/neverlands/abclient/manager/RoomManager.java`
- `app/src/main/java/ru/neverlands/abclient/service/AutoModeForegroundService.java`
- `app/src/main/java/ru/neverlands/abclient/ui/QuickButtonsPanel.java`
- `app/src/main/java/ru/neverlands/abclient/manager/TabManager.java`
- `app/src/main/java/ru/neverlands/abclient/PinfoActivity.java`

## Открытые пункты
- [ ] Прогон runtime-приёмки по логам устройства:
  - [ ] стабильность long-run цикла авто-компаса;
  - [ ] перестроение маршрута при смене локации цели;
  - [ ] отсутствие конфликтов с `AutoTreasure` и ручной навигацией.
