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

## Повторный regression-check 2026-04-26 11:00-11:30

- [x] Проверены `Logs/Critical/20260426_11_00_*`, `20260426_11_10_*`, `20260426_11_20_*`, `20260426_11_30_*` с учетом поддиректорий `Logs`.
- [x] В `20260426_11_00_auto_boss.log`, `20260426_11_10_auto_boss.log`, `20260426_11_20_auto_boss.log` найден повтор `Сценарий завершен (manual_disable)` после downgrade full -> public.
- [x] В `20260426_11_00_chatfilter.log` и соседних `chat.log` подтверждено, что это уходит в локальный чат как `[Авто-Боссы]`.
- [x] В `20260426_11_00_anclient_license.log` видно постоянное `LICENSE_FEATURE_HIDDEN: source=isAutoBossEnabled/onIncomingChatMessage, action=auto_boss`, то есть feature уже недоступна в license-session.
- [x] Причина: guard в `AutoFunctionsManager.onIncomingChatMessage(...)` при denied `AUTO_BOSS` вызывал side-effectful `bossAuto.setAutoBossEnabled(false)`, а `BossAuto.setAutoBossEnabled(false)` всегда выполнял `stopAndRestore("manual_disable", true)` и писал chat-сообщение даже при повторном `false`.
- [x] Исправление: `BossAuto` получил idempotent setter и тихий путь `disableForLicenseSync(...)`; `AutoFunctionsManager` использует его для `license_denied:*` и `license_downgrade:*`, чтобы снять persisted/runtime флаг без chat-шума и без восстановления snapshot full-функций.

## Debug 2026-04-26: Авто-Бой в background не отправляет удары

- [x] Проверены `Logs/` с учетом поддиректорий (`Critical`, `Logcat`, профильные папки).
- [x] В `Logs/Critical/20260426_12_00_mainactivity.log` и `20260426_12_10_mainactivity.log` найден повтор: `directHttpSubmit: payload parts=8, need 9, skip`.
- [x] В `fightviewmodel.log` видно, что `autoTurnOnce` формирует `submit posted`, то есть парсер боя и `LezFight.BuildResult()` работают.
- [x] Причина: `MainActivity.submitAutoBattleActionViaDirectHttp()` парсил payload через `split("\\|")`; Java отбрасывает trailing empty token, поэтому корректный payload с пустым `ina` (`...|inb|`) превращался из 9 частей в 8 и фоновая отправка отбрасывалась.
- [x] Исправление: direct HTTP submit использует `split("\\|", -1)`, чтобы сохранить пустое последнее поле `ina` и отправлять ход в background.

## Debug 2026-04-27: JS-кнопки инвентаря не открывают формы действий

- [x] Проверены `Logs/` рекурсивно (`Critical`, `pool`).
- [x] В `Logs/Critical/20260427_12_40_js_console.log` найден повтор: `Cannot read properties of undefined (reading 'FBT')` из `transfer_v01.js`, `w28.js`, `svitok_v2.js`.
- [x] По логам `webviewinterceptor` подтверждено, что инвентарные JS-файлы (`dealer.js`, `selling.js`, `compl.js`, `transfer_v01.js`, `svitok_v2.js`, `w28.js`) загружаются, но выполнение action-функций обрывается на `top.frames['ch_buttons'].document.FBT...` до вставки формы в `transfer`.
- [x] Сверено с ПК-эталоном `ABClient/PostFilter/SvitokJs.cs`: оригинальный JS перед показом формы вызывает `top.frames['ch_buttons'].document.FBT.text.focus()`.
- [x] Причина: Android-shim в `HtmlUtils.getJsFix()` создавал `top.frames['ch_buttons']`, но без совместимого `document.FBT`, поэтому старый JS падал и кнопки визуально ничего не делали.
- [x] Исправление: существующий frames-shim расширен `__anEnsureChatButtonsFrame(...)`, который добавляет `document.FBT.text/fyo/lmid/schat/spchat/lrchat/submit` и сохраняет bridge-фокус через `AndroidBridge.chatFocus()`.
