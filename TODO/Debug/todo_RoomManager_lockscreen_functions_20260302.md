# Детальный TODO: `RoomManager.java` (lockscreen/background)

## Контекст
- [ ] `RoomManager` обрабатывает `ch.php?lo=1` и запускает auto-attack pipeline.
- [ ] При пропадании room-ticks авто-нападение фактически «замирает».

## Функции и задачи

### `process(Context, String)`
- [ ] Проверить входные условия для старта auto-attack в фоне (`context`, `FastNeed`, `fightActive`, enemy selection).
- [ ] Проверить, что обработка вызывается при lockscreen (по логам `AA_TRACE room tick`).

### `isFightSessionActive()`
- [ ] Проверить, не дает ли ложноположительный `fightActive=true`, из-за чего авто-нападение вечно пропускается.
- [ ] Проверить устойчивость при неполном/устаревшем `AppVars.ContentMainPhp`.

### `FilterGetWalkers(...)`
- [ ] Проверить, обновляются ли walkers-состояния при фоне (`myCoordOld/myLocOld/myCharsOld`).
- [ ] Проверить, не вызывает ли ветка walkers лишние блокировки или ранние возвраты.

### `pickEnemyForAutoAttack(...)`
- [ ] Проверить blacklist-логику после длительного сна устройства (просрочка/вечный blacklist).
- [ ] Проверить выбор цели при частично устаревшем списке комнаты.

### `isAutoAttackEnabled(Context)`
- [ ] Проверить стабильность доступа к `AutoFunctionsManager` в фоне.
- [ ] Добавить диагностический лог причины `false` (если падает исключение).

### `charAddToBlackList(String)` / `isCharInBlackList(String)` / `getBlackListRemainingMs(String)`
- [ ] Проверить, нет ли накопления «мертвых» записей, блокирующих атаки после lockscreen.

## Зависимости
- [ ] `AutoFunctionsManager`, `ContactsManager`, `FastActionManager`, `AppVars`.
- [ ] Поток room-list из `MainActivity.requestRoomUsersRefreshSoon()`.

## Ожидаемый результат анализа
- [ ] Подтверждение: проблема в отсутствии room-ticks или во внутреннем guard/blacklist в `RoomManager`.

## Промежуточные выводы (статический анализ, 2026-03-02)
- [x] `RoomManager.process(...)` запускается только при поступлении свежего HTML `ch.php?lo=1`.
- [x] При отсутствии room-ticks авто-нападение физически не стартует (даже если флаги включены).
- [x] Внутри есть дополнительные guard-ветки (`FastNeed`, `fightActive`, `toolId`, blacklist), но они вторичны без входного room tick.
- [x] Критичность `MainActivity.requestRoomUsersRefreshSoon()` подтверждена архитектурно.
- [ ] Нужна runtime-проверка: есть ли room ticks в первые минуты после lockscreen и в момент полной остановки.
