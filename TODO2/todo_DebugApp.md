# Debug: license expiry / runtime downgrade

## Проблема

После выдачи `profile.reg` с `expiresAt=10m` full-функции должны отключаться после истечения grant, а public-функции должны остаться доступными. В логах/чате замечено, что без перезапуска full-функции могли оставаться активными (`Авто-Боссы` продолжал реагировать), а UI не всегда переходил в public-only состояние.

## Гипотезы

- `expiresAt` в `ANREG2` сохраняется как значение, но при повторной валидации не отсекается истекший grant.
- `LicenseRuntime` переиспользует `currentSession`, хотя при новом старте должен пересчитывать состояние по `profile.reg`.
- `LicenseManager` читает `profile.reg`, но где-то не проверяет `grant.isExpired(...)` для bundle-ветки.
- На устройстве остался старый профильный fallback `info/<profile>/profile.reg`, который перекрывает общий bundle.
- `LicenseRuntime.requireSession(...)` при истечении active full-сессии очищает `currentSession`, но не перевалидирует `ANREG2` в public-only сессию.
- `AutoFunctionsManager.onIncomingChatMessage(...)` пропускает чат-события в `BossAuto` без license-guard, поэтому persisted `auto_boss=true` может продолжать работать после downgrade.

## План проверки

- [x] Проверить runtime-логи `LICENSE_*` / `LICENSE_GRANT_*` / `LICENSE_RUNTIME_*` на первом и втором запуске.
- [x] Сопоставить `expiresAt` в `profile.reg` и реальное время второго запуска по доступным логам.
- [ ] Проверить приоритет чтения `info/<profile>/profile.reg` vs `info/profile.reg`.
- [x] Проверить, очищается ли `currentSession` и пересчитывается ли она при runtime-expiry.
- [x] Проверить, не сохраняется ли разрешение через stale `SharedPreferences`/cache.
- [x] Если нужно, поправить decision point в `LicenseManager` или `LicenseRuntime`, а не добавлять новый обход.

## Статус

- [x] Проблема зафиксирована.
- [x] Логи собраны и проанализированы.
- [x] Причина подтверждена.
- [x] Исправление внесено.
- [ ] Проверка после фикса.

## Вывод по логам

- В `Logs/Critical/20260425_12_30_automodefgservice.log` на `12:39:58` видно `autoBoss=true`, `autoFight=true`, `locationTracking=true` в фоне.
- В `Logs/Блудя/20260425_chat.html` есть последующие сообщения `[Авто-Боссы]`, значит persisted/runtime-флаг продолжал участвовать в обработке событий.
- В выгруженных `12:30` critical-логах нет `ANCLIENT_LICENSE` файла, поэтому точный момент `LICENSE_RUNTIME_EXPIRED` не был зафиксирован, но кодовая причина найдена: session-expiry очищала сессию без перехода на public-only, а чатовый вход Авто-Босса обходил guard.
