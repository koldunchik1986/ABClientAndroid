# План портирования FormCompas.cs

## Назначение C#-модуля
- `FormCompas.cs` в ПК-версии отвечает за поиск цели на карте и построение пути до неё.
- Логика опирается на `pinfo` (локация/состояние цели), карту (`MapPath`) и вывод статуса пользователю.

## Что нужно в Android (целевой контракт)
- [x] Ручной «Компас» из PINFO-tab (кнопка в action-bar).
- [x] Ручной «Компас» из `PinfoActivity` menu.
- [x] Отдельная авто-функция `AUTO_COMPASS` (QuickButton + long-press настройки).
- [x] Поиск клеток по локации цели из `pinfo` через `ExtMap.Cells`.
- [x] Движение по карте через существующий контур `AutoMoving`/`MapPath`.
- [x] Остановка с понятной причиной в чат и сообщение при успешном нахождении.

## Реализация в Android (статус на 2026-03-27)
- [x] `TabManager`:
  - [x] для PINFO сделана 4-я кнопка,
  - [x] кнопка «Компас» запускает `startManualCompassSearch(...)`.
- [x] `PinfoActivity`:
  - [x] пункт menu `action_pinfo_compas` запускает ручной компас,
  - [x] парсинг ника из URL стабилизирован для `pinfo.cgi?<nick>` и `key=value`.
- [x] `AutoFunctionsManager`:
  - [x] добавлен runtime-контур `AUTO_COMPASS` (target/hunt/poll/кандидаты/checked/stop reasons),
  - [x] добавлен API настроек авто-компаса (target, hunt mode, poll interval, manual cells),
  - [x] добавлен `startManualCompassSearch(String nick)` для single-run сценария.
- [x] `NeverApi`:
  - [x] добавлен снимок `PinfoCompassSnapshot` (nick, location, tied),
  - [x] добавлен парсинг локации из `var parameters`.
- [x] `QuickButtonsPanel`:
  - [x] добавлен `AUTO_COMPASS` в toggle/long-press,
  - [x] добавлен диалог настроек (цель, локация, клетки, hunt-mode, интервал 1/2/5),
  - [x] поле клеток автозаполняется из auto-резолва (без принудительной фиксации в manual-override).
- [x] `AutoModeForegroundService`:
  - [x] `AUTO_COMPASS` включён в фоновые тики и в `shouldRunInBackground(...)`.
- [x] `RoomManager`:
  - [x] room-list прокидывается в `AutoFunctionsManager.onRoomUsersUpdated(...)`.

## Чек-лист приёмки
- [ ] Проверить на устройстве запуск ручного «Компас» из PINFO-tab.
- [ ] Проверить запуск ручного «Компас» из `PinfoActivity` menu.
- [ ] Проверить `AUTO_COMPASS` в QuickButton (toggle + long-press).
- [ ] Проверить режимы «Ходим ловим по клеткам» ON/OFF.
- [ ] Проверить перестроение маршрута при изменении локации цели.
- [ ] Проверить чат-сообщение: `Компас: Игрок найден на клетке №...`.
