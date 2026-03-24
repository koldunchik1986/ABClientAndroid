# Задача: дописка номера клетки в сообщения трекинга локации (2026-03-24 11:03:47)

## Контекст
- В авто-функции `Слежение за локацией` (`RoomManager.FilterGetWalkers`) сообщения о событиях
  `приходит/покидает локацию` публиковались без номера текущей клетки.
- Требование: в конце такого сообщения показывать текущий `regNum` клетки.

## План
- [x] Найти место формирования сообщений `приходит/покидает локацию`.
- [x] Добавить вычисление актуального `regNum` на текущем room-ответе.
- [x] Дописать `regNum` в конец сообщения.
- [x] Сохранить совместимость текущей логики сравнения составов комнаты.
- [x] Проверить компиляцию `:app:compileDebugJavaWithJavac`.

## Изменения
- [x] `RoomManager.FilterGetWalkers(...)`:
  - добавлен `currentCellRegNum = resolveCurrentCellRegNumForWalkers(locationNow)`;
  - добавлен лог `regNum` в `FilterGetWalkers` trace.
- [x] `buildWalkersMessage(...)`:
  - сигнатура расширена параметром `currentCellRegNum`;
  - в конец сообщения добавляется суффикс ` (клетка <regNum>)`, если `regNum` определён.
- [x] Добавлен helper `resolveCurrentCellRegNumForWalkers(...)`:
  - приоритет 1: `resolveCellRegNumForRoomName(locationNow)`;
  - приоритет 2: fallback на `AppVars.Profile.MapLocation`.

## Результат
- Сообщения вида `приходит/покидает локацию` теперь включают номер клетки в конце.
- Сборка успешна: `BUILD SUCCESSFUL`.
