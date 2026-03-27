# Задача: портирование «Компас» + «Авто-компас» (Android)

## Контекст
- Требуется реализовать ручной «Компас» из PINFO (кнопка в tab-action + пункт menu).
- Требуется добавить отдельную авто-функцию `AUTO_COMPASS` (QuickButton + long-press настройки).
- Поиск цели выполняется по `pinfo`, затем по клеткам `map.xml`/`ExtMap.Cells` с движением через существующий `AutoMoving`.

## План
- [x] Добавить `AUTO_COMPASS` в типы быстрых функций и UI-списки.
- [x] Добавить обработку ручного «Компас» в PINFO-tab и `PinfoActivity` menu.
- [x] Реализовать снимок `pinfo` для компаса (`nick`, `location`, `tied`) в `NeverApi`.
- [x] Реализовать runtime-контур авто-компаса в `AutoFunctionsManager`:
  - [x] target/hunt/poll settings,
  - [x] tick-опрос pinfo,
  - [x] резолв клеток по локации,
  - [x] выбор ближайшей клетки,
  - [x] переходы через `startAutoMoving`,
  - [x] остановка с сообщением в чат при результате/ошибке.
- [x] Прокинуть room-list обновления в авто-компас (`RoomManager` -> `onRoomUsersUpdated`).
- [x] Включить `tickAutoCompass()` в background-контур (`AutoModeForegroundService`).
- [x] Добавить long-press настройки `AUTO_COMPASS` в `QuickButtonsPanel`.
- [x] Добавить автозаполнение поля клеток в настройках авто-компаса.
- [x] Устранить парсинг ника в `PinfoActivity` для `pinfo.cgi?<nick>` и `key=value`.
- [x] Финальная компиляция `:app:compileDebugJavaWithJavac` (успешно).

## Реализовано (файлы)
- [x] `app/src/main/java/ru/neverlands/abclient/model/QuickActionType.java`
- [x] `app/src/main/java/ru/neverlands/abclient/adapter/FunctionListAdapter.java`
- [x] `app/src/main/java/ru/neverlands/abclient/PinfoActivity.java`
- [x] `app/src/main/java/ru/neverlands/abclient/manager/TabManager.java`
- [x] `app/src/main/java/ru/neverlands/abclient/manager/NeverApi.java`
- [x] `app/src/main/java/ru/neverlands/abclient/manager/AutoFunctionsManager.java`
- [x] `app/src/main/java/ru/neverlands/abclient/manager/RoomManager.java`
- [x] `app/src/main/java/ru/neverlands/abclient/service/AutoModeForegroundService.java`
- [x] `app/src/main/java/ru/neverlands/abclient/ui/QuickButtonsPanel.java`
- [x] `app/src/main/res/layout/action_buttons_bar.xml`

## Проверка (чек-лист)
- [ ] В PINFO-tab отображается 4 кнопки, кнопка «Компас» запускает поиск по нику вкладки.
- [ ] В `PinfoActivity` пункт menu «Компас» запускает тот же ручной сценарий.
- [ ] `AUTO_COMPASS` включается/выключается QuickButton-ом, long-press открывает настройки.
- [ ] При нахождении цели пишется чат-сообщение: `Компас: Игрок найден на клетке №...`.
- [ ] При недоступной цели/локации/клетках авто-компас корректно останавливается с причиной.
