# Отладка: смещение названия клетки карты (2026-03-24 00:42:42)

## Проблема
- При синхронизации названия клетки (`normalizeCellLabel`, `syncCellLabelFromServer`, `syncCellNameFromRoomHtml`) подпись иногда применялась не к текущей клетке, а к соседней/предыдущей.
- На рендере карты текст берётся из `cell.Tooltip` с приоритетом над `cell.Name` (`WebAppInterface.shortLabel`), поэтому ошибка привязки клетки сразу визуально заметна.

## План
- [x] Проанализировать логи `Logs/logcat_runtime_20260324_02.txt`, `Logs/logcat_runtime_20260324_03.txt` по `MAP_NAME_SYNC_TRACE`/`persistLabel`.
- [x] Проверить связку `RoomManager.syncCellNameFromRoomHtml` -> `RoomManager.onMapLocationConfirmed` -> `ExtMap.syncCellLabelFromServer`.
- [x] Внести фикс привязки deferred-обновления к конкретному `regNum`.
- [x] Убрать риск fallback на `currentReg` во время `AutoMoving` при несовпадении названий.
- [x] Проверить компиляцию `:app:compileDebugJavaWithJavac`.

## Изменения
- [x] `RoomManager`: добавлено хранение `pendingRoomLocationTargetRegNum` для deferred-синхронизации.
- [x] `syncCellNameFromRoomHtml(...)`: deferred-кэш теперь сохраняется вместе с target `regNum` (`nextJump` при авто-движении).
- [x] `onMapLocationConfirmed(...)`: отложенное имя применяется только если подтверждённый `regNum` совпадает с target `regNum`.
- [x] `resolveCellRegNumForRoomName(...)`: при `AppVars.AutoMoving=true` и отсутствии уверенного совпадения больше нет fallback на `currentReg`.
- [x] Добавлены диагностические логи `MAP_NAME_SYNC_TRACE` для случаев удержания deferred-обновления.

## Результат
- Компиляция успешна.
- Защищён сценарий, где deferred-имя из `ch.php` могло быть применено к неверной клетке при рассинхроне между `RoomManager` и `MapAjax`.
