# Ребилд парсинга персонажа: `getid.cgi` + `info.cgi` (Phase 1)

## 1) Текущий контур (до миграции)

- **Post-login sync персонажа**
  - `AutoFunctionsManager.requestCharacterSyncAfterLogin(...)`
  - `AutoFunctionsManager.requestCharacterSync(...)`
  - источник: `NeverApi.getPinfoVitalsFromPinfo(...)` (`pinfo.cgi`)
  - запись: `CharacterVitalsManager.updateFromPinfo(...)`
- **Авто-блаж (усталость)**
  - `MapAjax.maybeSyncVitalsFromPinfoAtSearchBoxStartup(...)`
  - `MapAjax.maybeSyncTiedFromPinfoIfNearThreshold(...)`
  - источник: `NeverApi.getPinfoVitalsFromPinfo(...)` (`pinfo.cgi`)
- **Region/CellName карты**
  - `RoomManager.maybeSyncCellMetaFromOwnPinfo(...)`
  - источник: `NeverApi.getPinfoCompassSnapshot(...)` (`pinfo.cgi`)
  - применение: `ExtMap.syncCellMetaFromPinfo(...)` с существующими guard-проверками подтверждённой клетки
- **Сетевой транспорт/таймаут**
  - `NeverApi.getInfo(...)`, `TIMEOUT_MS=10000`
  - retry для 536 (и для URL из `shouldRetryNeverApiRequest(...)`)

## 2) Новый контур (Phase 1)

- Единый pipeline:
  1. `getid.cgi?encodedNick` -> `playerId|nick`
  2. `info.cgi?playerid=...&slots=1&effects=1&info=1&hmu=1`
  3. парсинг 4 строк в единый DTO (`NeverApi.InfoApiSnapshot`)
- DTO-структуры:
  - `NeverApi.InfoApiSlot` (line1 slots, включая быстрый доступ к индексам через `slotsByIndex`)
  - `NeverApi.InfoApiEffect` (line2 effects)
  - `NeverApi.InfoApiInfoLine` (line3 info)
  - `NeverApi.InfoApiHmuLine` (line4 hmu + `curTire = 100 - maxTire`)
  - `NeverApi.InfoApiSnapshot` (объединённый снимок)
- Глобальный кэш nick->id:
  - in-memory: `NeverApi.nickIdCache`
  - disk: `files/info/nick_id.xml`
  - нормализация ключа: `trim + lower`
  - hit: сразу `info.cgi`
  - miss: `getid.cgi` -> запись в xml
- Dev-уведомление в чат о новой записи ID:
  - только при включённом dev-флаге профиля (`DoTexLog || DoHttpLog`)
  - формат: серверный timestamp + `[source_module]` + запись ID

## 3) Матрица замены (Phase 1)

- **Сценарий A: Post-login sync**
  - Было: `NeverApi.getPinfoVitalsFromPinfo(...)`
  - Стало: `NeverApi.getPinfoVitalsFromInfoApi(..., "login_sync")`
- **Сценарий B: Авто-блаж**
  - Было: `NeverApi.getPinfoVitalsFromPinfo(...)`
  - Стало:
    - startup: `NeverApi.getPinfoVitalsFromInfoApi(..., "auto_blaz_startup")`
    - near-threshold: `NeverApi.getPinfoVitalsFromInfoApi(..., "auto_blaz_near_threshold")`
- **Сценарий C: Region/CellName sync**
  - Было: `NeverApi.getPinfoCompassSnapshot(...)`
  - Стало: `NeverApi.getPinfoCompassSnapshotFromInfoApi(..., "map_region_sync")`

## 4) Совместимость и зависимости

- Существующие контуры `CompasAuto`, `BossAuto`, `MainPhp`, `ApiRepository` в этой фазе не переключаются принудительно.
- Сохранены старые методы `NeverApi.getPinfoVitalsFromPinfo(...)` и `NeverApi.getPinfoCompassSnapshot(...)` для обратной совместимости.
- Добавлены совместимые конвертеры:
  - `InfoApiSnapshot -> PinfoVitals`
  - `InfoApiSnapshot -> PinfoCompassSnapshot`

## 5) Логирование (decision points)

- `INFO_API_TRACE ... stage=id_cache_hit`
- `INFO_API_TRACE ... stage=id_cache_miss`
- `INFO_API_TRACE ... stage=id_cache_write`
- `INFO_API_TRACE ... stage=info_parse_ok_line1/2/3/4`
- `INFO_API_TRACE ... stage=info_parse_fail_line1/2/3/4`
- `INFO_API_TRACE ... source_module=<...>`

Логи пишутся через `AppLog` (дублирование в logcat + `FileLogger`).

## 6) Риски / анти-регресс

- HTTP 535/536: не ломаем существующий transport, сохраняем текущую retry-модель.
- Proxy strict mode: используется существующий контроль в `NeverApi.getInfo(...)`.
- Offline/invisible:
  - для snapshot используется `onlineStatus` + `locationName`.
- Невалидный `info.cgi` (неполные 4 строки): мягкий fail без краша.

## 7) Чеклист выполнения

- [x] Добавить единый DTO для 4 строк `info.cgi`
- [x] Добавить pipeline `nick -> playerId -> info.cgi`
- [x] Добавить `nick_id.xml` в `files/info`
- [x] Добавить dev-уведомление о новой записи ID
- [x] Переключить post-login sync на новый контур
- [x] Переключить авто-блаж на новый контур
- [x] Переключить map region/cell sync на новый контур
- [x] Прогнать компиляцию `:app:compileDebugJavaWithJavac`
- [ ] Проверить сценарии на устройстве по логам `INFO_API_TRACE`

## 8) Phase 2 runtime migration (full switch)

- Scope: только runtime-автоконтуры/sync в `app/` (без `ContactsActivity/ApiRepository`).
- Legacy-методы `NeverApi.getPinfoVitalsFromPinfo(...)` и `NeverApi.getPinfoCompassSnapshot(...)`
  сохранены как адаптеры, но теперь делегируют в info API pipeline без `pinfo.cgi`.
- Сохранена семантика `lastCompassPinfoHttpStatus`/`wasLastCompassPinfoRateLimited()` через статус `info.cgi`.

### Матрица «было/стало» (Phase 2)

- `CompasAuto`:
  - было: `NeverApi.getPinfoCompassSnapshot(...)`
  - стало: `NeverApi.getPinfoCompassSnapshotFromInfoApi(..., "auto_compass_manual|auto_compass_tick")`
- `BossAuto`:
  - было: `NeverApi.getPinfoCompassSnapshot(...)` в safe snapshot/poll
  - стало: `NeverApi.getPinfoCompassSnapshotFromInfoApi(..., "auto_boss_snapshot|auto_boss_poll")`
- `RoomManager` (Auto-Cure room):
  - было: `NeverApi.getPinfoVitalsFromPinfo(...)`
  - стало: `NeverApi.getPinfoVitalsFromInfoApi(..., "auto_cure_room")`
- `MainPhp` (post-fight auto-drink):
  - было: `NeverApi.getPinfoVitalsFromPinfo(...)`
  - стало: `NeverApi.getPinfoVitalsFromInfoApi(..., "post_fight_auto_drink")`
- `AutoFunctionsManager` (character sync):
  - было: ветка `login -> info`, остальное `-> pinfo`
  - стало: всегда `NeverApi.getPinfoVitalsFromInfoApi(...)` с `source_module=login_sync|character_sync_auto_enable`

### Чеклист Phase 2

- [x] Убрать runtime-вызовы `NeverApi.getPinfoVitalsFromPinfo(...)` в `app/src/main/java`
- [x] Убрать runtime-вызовы `NeverApi.getPinfoCompassSnapshot(...)` в `app/src/main/java`
- [x] Добавить decision-point trace `INFO_API_TRACE stage=info_api_runtime_call` в migrated call-site
- [x] Обновить legacy-адаптеры `NeverApi` на info API pipeline (без fallback в `pinfo.cgi`)
- [ ] Проверить device/runtime сценарии по логам и proxy-трафику (что `pinfo.cgi` не используется в runtime-автоконтуре)
