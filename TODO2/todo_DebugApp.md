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

## Debug 2026-04-30: Навигатор после боя с proxy-fail

- [x] Принят уточняющий контекст игрока: сетевой путь `WebView/OkHttp -> локальный proxy 127.0.0.1 -> удалённый upstream proxy -> игровой сервер`.
- [x] Найдены существующие decision point без нового контура: `MainPhp` для `AutoMoving`/возврата на карту и `WebViewRequestInterceptor` для маршрута через локальный proxy.
- [x] Гипотеза 1 подтверждена по коду: ветка `AutoMoving` в `MainPhp` могла пытаться выполнить non-combat возврат на карту прямо при `isFightFrame/isFightTopFrame`, если бой начался во время маршрута.
- [x] Гипотеза 2 подтверждена по proxy-коду: transient 502/503/504 от локального/upstream proxy попадал в WebView как обычное тело, поэтому `onReceivedError` retry не запускался и main-frame мог остаться на странице ошибки.
- [x] Исправление в `MainPhp`: навигатор теперь логирует `AUTO_MOVING_TRACE: pause navigator while fight frame is active` и не выполняет `mainPhpWtime`, city/teleport/map-return ветки на боевом кадре.
- [x] Исправление в `WebViewRequestInterceptor`: main-frame transient proxy failure возвращает HTML auto-retry на тот же URL через настроенный proxy-маршрут, с логом `PROXY_RETRY` и контекстом `autoMoving/destination/fight`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса — успешно.
- [ ] Проверить live на профиле с upstream proxy: во время боя нет `AUTO_MOVING_TRACE: redirect to map` до завершения боя; при proxy-сбое виден `PROXY_RETRY`; после `act=7` навигатор возвращается на карту и продолжает текущий `AutoMovingDestinaton`.

## Debug 2026-05-10: Авто-Лесоруб cleanup / бой с captcha

- [x] Проанализированы логи `logs/Critical/20260510_10_50_*` и `logs/Logcat/20260510_10_50_logcat.txt`.
- [x] Root cause cleanup: `AutoCutManager.routeNextAfterTimerReturnIfArrived(...)` обходил уже существующий guard `shouldDelayRouteForPreparation()`, поэтому timer-route мог продолжить CSV-маршрут во время `AppVars.AutoCutCleanupPending`.
- [x] Исправление cleanup: `routeNextAfterTimerReturnIfArrived(...)` теперь не очищает timer-route state и не вызывает `routeNextCellWithManager(...)`, пока идёт cleanup/mass-sync/pending cut preparation.
- [x] Дополнительное исправление cleanup: `AutoCutHandler.processCleanupOpenInventory(...)` больше не гоняет немедленный `go=inf -> go=inv`, если inventory-address вернул non-inventory HTML; вместо этого используется существующий delayed redirect на `main.php?im=0`.
- [x] Root cause captcha: popup не всегда открывался, когда JS-bridge/foreground-service видели `FightLink` с `code=????`, но `CodeAddress` не был готов, хотя `WebViewRequestInterceptor` уже захватил свежий `LastFightCaptchaImageUrl/Bytes`.
- [x] Исправление captcha: существующий контур `ACTION_SHOW_CAPTCHA`/`showFightCaptchaDialog(...)` теперь получает fallback URL из свежего `LastFightCaptchaImageUrl` в `AutoModeForegroundService` и `MainActivity.restorePendingFightCaptchaDialogIfNeeded()`.
- [x] Исправление JS-bridge captcha: `FightViewModel` при `IsBoi=false` и валидном `FightLink + CodeAddress` вызывает существующий дедуплицированный `FightAuto.showFightCaptchaDialogOnce(...)`, выключая `Autoboi` до ввода капчи.
- [x] `./gradlew2.bat --no-daemon :app2:assembleDebug` — успешно.
- [x] Mojibake-проверка `app2/src/main/java` — совпадений `РЎ`/`Рџ`/`Ð`/`Ñ` нет; `????` в Java относится к штатному placeholder `code=????`.
- [x] Проверка прямого `android.util.Log`/`Log.*` в изменённых Java-файлах — новых вхождений нет.
- [-] `git diff --check -- app2 TODO2` не показал whitespace errors в изменённых строках, но Git продолжает выводить существующее предупреждение `.gitattributes" is not a valid attribute name: .gitattributes:7` и CRLF warnings.
- [ ] Live-проверка: в новых логах cleanup не должен создавать серию `go=inf/go=inv` во время `AutoCutCleanupPending`, а бой с `FightLink code=????` должен открыть `showCaptchaDialog(...)` и запустить Anti-Captcha через общий popup.

## Debug 2026-05-10: задержка завершения боя 13:25 -> 13:31

- [x] Проанализированы свежие рекурсивные логи `logs/Critical/20260510_13_20_*` и `logs/Critical/20260510_13_30_*`.
- [x] Подтверждено: первый автоудар ушёл сразу (`13:25:48`), а уже на `13:25:49` парсинг inactive fight HTML сгенерировал нормальный `FightLink` `main.php?get_id=61&act=7...`.
- [x] Root cause 1: `MainActivity.isActiveFightContext(...)` использовался как validation-парсер и восстанавливал side effects `LezFight`, поэтому сгенерированный `act=7` терялся до `FightContextChoiceHandler.chooseAfterInactiveCurrentHtml(...)`; вместо `pendingFinish` выбирался `server probe`.
- [x] Root cause 2: `AutoModeForegroundService` удалял готовый non-captcha `FightLink act=7`, если истёк короткий `FIGHT_FINISH_PULSE_GRACE_MS`, хотя сама ссылка завершения уже самодостаточна и должна иметь приоритет над idle/probe/map automation.
- [x] Исправление в существующем контуре `MainActivity`/`FightContextChoiceHandler`: validation-парсер теперь сохраняет сгенерированный `get_id=61&act=7` finish-link, а oracle проверяет не только переданный snapshot, но и текущий `AppVars.FightLink`; captcha-placeholder `code=????` не считается готовой ссылкой для прямой навигации.
- [x] Исправление в существующем контуре `AutoModeForegroundService`: готовый non-captcha `act=7` больше не сбрасывается из-за отсутствия свежего fight pulse; сервис dispatch-ит ссылку и логирует `contextValid`, чтобы видеть fallback-сценарии без добавления параллельного HTTP-контура.
- [ ] Проверка после фикса: в новых логах после `isActiveFightContext: keep generated finish link` должен идти `pending finish link selected`/`navigating to pending finish link` или `uiTick: dispatch fight finish link`, без многоминутного ожидания `NeverTimer`.

## Debug 2026-05-10: проверка 13:38 captcha в фоне

- [x] Проверены `logs/Critical/20260510_13_30_*` и `logs/Logcat/20260510_13_30_logcat.txt` вокруг `13:38`.
- [x] Подтверждено: в `13:38:25` приложение было в background (`uiForegroundInteractive=false`, `uiForegroundLikely=false`) и бой был найден/обработан через direct HTTP.
- [x] В самом событии `13:38` `LezFight.BuildFightLink(normal)` сформировал обычный finish-link без captcha (`codeAddress=`), а `AutoModeForegroundService` отправил `main.php?get_id=61&act=7...`; ответ `act=7` не содержал `code=????`/`modules/code/code.php`.
- [x] Уточнение по наблюдению пользователя: реальная fight captcha появилась не в `13:38`, а только после разворачивания приложения в `14:00:25-14:00:26` (`onResume` -> загрузка fight JS/captcha image -> `BuildFightLink(captcha)` -> `showCaptchaDialog` -> `ANTI_CAPTCHA_TRACE`).
- [x] Между `13:40` и `13:59` в critical/logcat логах нет `code=????`, `BuildFightLink(captcha)`, `showCaptchaDialog` или `ANTI_CAPTCHA_TRACE`; сервис каждую секунду пишет `skip autoTurn/probe while map automation active` при `uiForegroundInteractive=false`.
- [x] Найден вероятный root cause: background-контур подавляет боевой `main.php` probe/sync из-за активной map automation, поэтому не подхватывает серверный переход к боевой/captcha-странице до `onResume`. Косвенный маркер: `ch.php` в фоне возвращал `top.frames['main_top'].location='main.php'` в `13:44:48` и `13:55:48`, но `processFightHtml`/captcha-flow не запускались до разворачивания.
- [x] Найден отдельный источник шума: `[SERVER_FLOW] state=FIGHT_CAPTCHA/FISH_CAPTCHA` срабатывает на статических JS-файлах (`/js/fight_v10.js`, `/js/map.js?v=6`), где в коде встречаются строки формы/картинки капчи. Это ложная классификация логирования, не HTML-страница капчи.
- [x] Исправление внесено в существующий background decision point без нового HTTP-контура: `WebViewRequestInterceptor` через уже существующий `onChatPollResponseMeta(...)` помечает `top.frames['main_top'].location='main.php'` как `LastFightAnnounceAtMs`, `AutoModeForegroundService` больше не блокирует autoTurn/probe при `AutoMoving=true` и свежем fight-сигнале, а `MainActivity` разрешает server-probe в том же окне.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; diff-проверки не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake в измененных строках. `git diff --check -- app2 TODO2` по-прежнему выводит существующие предупреждения `.gitattributes` и CRLF.
- [ ] Возможное отдельное улучшение: сузить `SERVER_FLOW` classifier, чтобы не помечать `application/javascript` ресурсы как captcha-состояния.

## Debug 2026-05-11: Авто-Лесоруб не выбросил `Бесполезный хлам` в фоне

- [x] Проверены `logs/Critical/20260511_00_10_*` и `logs/Critical/20260511_00_20_*` вокруг `00:19`.
- [x] В `00:19:17` найдено `garbage cleanup requested: thing=Бесполезный хлам`, значит detection и постановка cleanup-флага сработали.
- [x] После этого cleanup дошёл до `go=inv&im=0`, но HTML был без inventory rows: `cleanup waits real inventory html` / `inventory_without_rows`.
- [x] Root cause по логам: retry cleanup был привязан к глобальному `AppVars.NeverTimer`; после боя/замедленного перемещения глобальный timer сдвинулся на минуты (`dueInMs=544653`), поэтому `garbage bulk-drop completed` после `00:19` не наступил.
- [x] Исправление внесено в существующий общий AutoCut-контур без нового inventory HTTP-контура: `AutoCutManager` хранит собственный due-time cleanup/look retry, `deferCleanupInventoryUntilServerTimer(...)` ставит bounded retry вместо наследования дальнего `AppVars.NeverTimer`, а `MainActivity.checkServerTimerDrivenActions()` будит AutoCut по этому due-time независимо от чужого server timer.
- [x] Проверено покрытие `Авто-Травник`/`Авто-Лесоруб`: cleanup-флаги, `GARBAGE_ITEM_NAME`, `isAutoCutLikeEnabled()` и dispatcher общие для `AutoCutMode.HERB` и `AutoCutMode.TREE`; лесоруб-only retry веток для этой проблемы нет.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; grep по измененному контуру не выявил новых `AppVars.VCode`, прямого `android.util.Log` или mojibake. `git diff --check` по измененным файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: после фонового `garbage cleanup requested: thing=Бесполезный хлам` в `AUTO_CUT_TRACE` должны появиться `cleanup inventory wait deferred until bounded retry`, затем `SERVER_TIMER_TICK auto-cut retry ... retryDueInMs`, `garbage bulk-drop redirect` и `garbage bulk-drop completed` без многоминутного `globalDueInMs`-ожидания.

## Debug 2026-05-14: submit-storm Авто-Боя после `20:03:45`

- [x] Проанализированы `logs/Critical/20260514_20_00_fightviewmodel.log`, `mainactivity.log`, `vcode.log`, `fightannouncehandler.log` вокруг боя `Кабан[...]`.
- [x] Подтверждено: после первого event-driven удара `FightViewModel`/JS-bridge многократно отправляли одинаковый payload с тем же vcode (`15038072...`) десятки раз в секунду; `MainActivity.onActionSubmitted()` сразу сбрасывал LiveData-событие, поэтому существующий throttle не удерживал один submit на состояние боя.
- [x] Найден existing decision point без нового контура: `MainActivity.enqueueAutoBattleSubmit(...)` уже отвечает за pacing и pending submit авто-боя.
- [x] Исправление внесено в существующий submit-контур: повторный payload того же состояния подавляется коротким окном, а legacy-режим `HitDelaySec=0` теперь соблюдает 1-2 сек между ударами после первого мгновенного event-driven submit.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; targeted checks не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake в измененном Java-файле. `git diff --check` по измененным файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: в новых логах не должно быть серии `submit posted`/`ok_payload_submit` чаще одного раза на состояние боя; при повторной обработке того же payload ожидается `autoBattleDelay: duplicate payload suppressed`.

## Debug 2026-05-14: одноразовая cleanup-проверка после тяжелого рюкзака

- [x] Найден existing contour без нового HTTP/inventory-пути: `ServerNoticeParser` распознаёт popup `Рюкзак слишком тяжелый! Замедленное перемещение.`, `AutoCutManager` ставит `AppVars.AutoCutCleanupPending`, а `AutoCutHandler`/`InventoryParser` выполняют штатный inventory bulk-drop `Бесполезный хлам`.
- [x] `ServerNoticeParser` теперь запускает heavy-backpack cleanup только один раз за runtime и только если включён `AutoFunctionsManager.isAutoCutLikeEnabled()` (`Авто-Травник` или `Авто-Лесоруб`). Дубликаты popup-а по existing `HEAVY_BACKPACK_NOTICE_DEDUP_MS` не создают повторных действий.
- [x] `AutoCutManager.requestGarbageCleanupAfterHeavyBackpackNotice(...)` ставит cleanup-state без немедленного reload: проверка inventory откладывается через existing `deferCleanupInventoryUntilServerTimer(...)`/pending retry и `MainActivity.checkServerTimerDrivenActions()`, ожидая `NeverTimer` или короткий fallback.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; targeted checks не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake в изменённых Java-файлах. `git diff --check` по изменённым файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: после первого heavy-backpack popup в `AUTO_CUT_TRACE` ожидаются `heavy backpack garbage cleanup scheduled after NeverTimer`, затем `SERVER_TIMER_TICK auto-cut retry`, `garbage bulk-drop redirect`/`garbage bulk-drop completed`; последующие popup-ы не должны повторно ставить cleanup.

## Debug 2026-05-15: Авто-Лесоруб завис после Кабан[8]

- [x] Проанализированы `logs/Critical/20260515_16_50_*` вокруг `16:53:09 Нападение: Кабан[8]`.
- [x] Подтверждено: бой завершился быстро (`16:53:11 Победа за Юличка`), но stale fight HTML продолжал генерировать новый `main.php?get_id=61&act=7...` через `MainActivity.isActiveFightContext(...)`.
- [x] Root cause: non-fight ответ `act=7` не помечал fight-finish как подтвержденный для `LogBoi`, поэтому `FightContextChoiceHandler` снова выбирал `pending finish link selected after inactive current html`, а server-timer/Авто-Лесоруб не мог закрепиться на `go=inf`/карте.
- [x] Исправление внесено в существующий контур без нового HTTP-пути: `MainPhp` помечает non-fight `act=7` через `FightAuto.markFightFinishConfirmed(...)`, `FightAuto` очищает stale fight state, а `MainActivity.isActiveFightContext(...)` больше не сохраняет generated finish-link для уже подтвержденного `LogBoi`.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; Kotlin daemon упал, но Gradle выполнил fallback без daemon и завершил `BUILD SUCCESSFUL`. Targeted checks не нашли новых прямых `android.util.Log`/`Log.*` или mojibake; `AppVars.VCode` найден только в существующем комментарии `MainPhp`.
- [-] `git diff --check` по измененным файлам не показал новых whitespace errors, но Git продолжает выводить существующее предупреждение `.gitattributes:7` и CRLF warnings.
- [ ] Live-проверка: после первого non-fight `act=7` в логах ожидается `markFightFinishConfirmed: clear stale fight state`, затем отсутствие новых `keep generated finish link` для того же `LogBoi`; `SERVER_TIMER_TICK` должен довести `Авто-Лесоруб` до `go=inf`/карты и продолжить маршрут.

## Debug 2026-05-16: heavy-backpack cleanup не ждал поздний NeverTimer

- [x] Проанализированы свежие логи `logs/Critical/20260516_13_00_*` и `logs/Critical/20260516_13_10_*` вокруг первого popup `Рюкзак слишком тяжелый! Замедленное перемещение.`.
- [x] Подтверждено: `13:09:28` heavy-backpack cleanup поставил `cleanup_inventory:heavy_backpack_notice...` на fallback `1500ms`, затем `FishOverload`/`setAutoFishEnabled(false)` очистил `NeverTimer`, а реальный `WebAppInterface.SetNeverTimer` появился только в `13:09:30` с `dueInMs=31499`.
- [x] Root cause: pending cleanup retry хранил ранний `lookRetryDueAtMs=1500ms` и `MainActivity.checkServerTimerDrivenActions()` считал его due независимо от поздно пришедшего `AppVars.NeverTimer`; поэтому WebView открыл inventory до окончания server cooldown.
- [x] Исправление внесено в существующий `AutoCutManager` decision point без нового inventory/HTTP-контура: `cleanup_inventory:*` retry теперь динамически использует поздний `AppVars.NeverTimer`, если он дальше собственного fallback due-time.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; targeted checks не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake в измененном Java-файле. `TODO2/todo_DebugApp.md` содержит только старые штатные `code=????` записи. `git diff --check` по измененным файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: после popup в `AUTO_CUT_TRACE` ожидается ранний `heavy backpack garbage cleanup scheduled...`, но `SERVER_TIMER_TICK auto-cut retry` должен сработать только после актуального `NeverTimer`, без преждевременного `cleanup redirect to inventory` через 1.5 секунды.

## Debug 2026-05-16: cold-start auto-lumberjack cleanup loop

- [x] Проанализированы свежие логи `logs/Critical/20260516_13_50_*` и `logs/Logcat/20260516_13_50_logcat.txt` вокруг cold-start `auto_lumberjack`.
- [x] Подтверждено: после popup `Рюкзак слишком тяжелый! Замедленное перемещение.` cleanup выставил `AppVars.AutoCutCleanupPending=true` и `AppVars.BulkDropThing=Бесполезный хлам`, затем WebView начал цикл `go=ret/go=inv/go=inf`, но в логах нет `garbage bulk-drop redirect` и `garbage bulk-drop completed`.
- [x] Root cause: `AutoCutHandler.processMainPhpAutoCutStep(...)` при активном cleanup открывал inventory, но когда реальная inventory-страница приходила обратно, handler продолжал ветку проверки инструмента (`AutoCutCheckSickle`) и возвращал карту до того, как `MainPhp` доходил до штатного `InventoryParser.mainPhpInv(...)`. Поэтому `InvEntry.DropLink` не парсился, bulk-drop не запускался, а `AutoCutCleanupPending` оставался активным.
- [x] Исправление внесено в существующий cleanup/inventory decision point без нового HTTP-контура: если `processCleanupOpenInventory(...)` не вернул redirect при активном `AutoCutCleanupPending`, `AutoCutHandler` теперь уступает управление штатному inventory parser и не выполняет tool-check/wear до завершения cleanup.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; targeted checks не нашли `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake в измененном `AutoCutHandler.java`. `git diff --check` по измененным файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: после `cleanup pending yields to inventory parser` ожидаются `garbage bulk-drop redirect`, `garbage bulk-drop completed` и `cleanup completed`, без повторного `cleanup redirect to inventory` каждые 2 секунды.

## Debug 2026-05-16: свежие логи 15:40, cleanup и post-fight resume

- [x] Проанализированы `logs/Critical/20260516_15_40_*` и `logs/Logcat/20260516_15_40_logcat.txt` вокруг cleanup `15:40` и боя `Грабитель[7]` в `15:44`.
- [x] Root cause cleanup: после `cleanup pending yields to inventory parser` реальный inventory всё ещё не пришёл из-за server cooldown; когда `cleanup_inventory:*` retry истёк в `15:42:32`, `MainActivity.checkServerTimerDrivenActions()` построил общий `go=ret&an_auto_cut_tick=1`, поэтому pending cleanup ушёл в tool-check (`redirect to character page for tool check`) вместо повторного `go=inv&im=0` и `BulkDropThing=Бесполезный хлам` не дошёл до `InventoryParser`.
- [x] Исправление cleanup внесено в существующий dispatcher без нового inventory/HTTP-контура: для `cleanup_inventory:*` retry теперь строится `go=inv&im=0&an_auto_cut_tick=1`, а обычные look retry продолжают использовать `go=ret`/background direct look.
- [x] Root cause post-fight resume: после `act=7` бой корректно очистил stale state (`markFightFinishConfirmed`), но ветка `AUTO_DRINK_TRACE post-fight redirect to go=inf` оставила `auto_lumberjack` на странице персонажа; AutoCut был уже с готовым инструментом и не запускал ни tool-check, ни map-return, поэтому map.js/`Оглядеться` не продолжились.
- [x] Исправление post-fight внесено в существующий `AutoCutHandler` decision point: после короткого post-fight окна автопитья, если инструмент готов и текущий HTML не карта/инвентарь, AutoCut возвращает WebView на карту через существующий `buildReturnToMapHtml(...)`.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; targeted checks не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` в прикладных изменениях или mojibake в Java. `git diff --check` по изменённым файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: в новых логах ожидаются `SERVER_TIMER_TICK auto-cut cleanup inventory reload`, `garbage bulk-drop redirect`/`completed` и `post-fight return to map after auto-drink sync`.

## Debug 2026-05-16: live-логи 16:00-16:30 после сборки

- [x] Проверены свежие серии `logs/Critical/20260516_16_00_*`, `16_10_*`, `16_20_*`, `16_30_*` и соответствующие `logs/Logcat/20260516_16_*_logcat.txt`.
- [x] Cleanup частично подтвердился на `16:24:07`: есть `garbage bulk-drop completed`, `cleanup completed` и возврат на карту через `return to map using parsed link, action=auto_cut_cleanup_return`.
- [x] Но логи не подтверждают именно новый dispatcher-fix: на `16:24:02` `MainActivity` всё ещё строит `reloadUrl=...go=ret...&an_auto_cut_tick=1` для `autoCutRetrySource=cleanup_inventory:inventory_without_rows`, а ожидаемого маркера `SERVER_TIMER_TICK auto-cut cleanup inventory reload` нет.
- [x] Post-fight fix тоже не подтверждён свежими логами: после `AUTO_DRINK_TRACE post-fight redirect to go=inf` в `16:20`, `16:26`, `16:31` нет маркера `post-fight return to map after auto-drink sync`; `16:31` остаётся на `go=inf` и дальше идут только chat refresh / `DoHerbAutoCut=false`.
- [x] Уточнение после проверки: пользователь подтвердил, что APK был свежим; гипотезу stale-install не используем как объяснение.
- [x] Regression-fix внесён в существующие decision points: `cleanup_inventory:*` retry получил приоритет над `AutoMoving/AutoFish` на due tick, а source-check стал устойчив к wrapper-префиксам; post-fight return больше не зависит от `AutoCutArmedSickle` и логирует случай, когда после auto-drink sync мы уже на карте.
- [ ] Повторить live-проверку на следующей сборке; ожидаемые маркеры: `SERVER_TIMER_TICK auto-cut cleanup inventory reload` или `post-fight return already on map after auto-drink sync`, `garbage bulk-drop redirect`/`completed`, `post-fight return to map after auto-drink sync`.

## Debug 2026-05-16: `Бесполезный хлам` > 1 после cleanup

- [x] Проверены свежие логи `logs/Critical/20260516_18_30_*`: dispatcher-fix уже работает (`SERVER_TIMER_TICK auto-cut cleanup inventory reload` строит `go=inv&im=0`), но после первого `garbage bulk-drop redirect` сразу идут `garbage bulk-drop completed` и `cleanup completed`.
- [x] Найден existing contour без нового HTTP/inventory-пути: `AutoCutManager.startCleanupState(...)` выставляет `AppVars.BulkDropThing=Бесполезный хлам`, `InventoryParser.mainPhpInv(...)` выполняет drop через `InvEntry.DropLink`, а `AutoCutHandler.afterMainPhpInventoryStep(...)` закрывает cleanup после inventory-pass.
- [x] Root cause: generic `InventoryParser` очищал `BulkDropThing` и писал “пачка завершена” на первом post-drop inventory-pass, а `AutoCutHandler` сразу вызывал `onCleanupCompleted("inventory_pass")`; для `get_id=50` ответа не было отдельной fresh-проверки `go=inv&im=0`, поэтому cleanup мог завершиться после удаления одного экземпляра.
- [x] Исправление внесено в существующий contour: для AutoCut garbage cleanup `InventoryParser` больше не очищает `BulkDropThing`, а передаёт завершение в `AutoCutHandler`; после drop-result `get_id=50` handler делает fresh verification redirect на `main.php?im=0&an_auto_cut_cleanup_verify=1`, повторяет drop при найденном `DropLink` и завершает cleanup только после verification-pass без видимого `Бесполезный хлам`.
- [x] Добавлено файловое диагностическое логирование в `AUTO_CUT_TRACE`: `garbage cleanup inventory scan`, `garbage bulk-drop redirect` с `rawDeleteMarkers`, `garbage bulk-drop awaits AutoCut verification`, `garbage cleanup verification pass` с количеством `nameMatches/dropThingMatches/dropCandidates/totalCount`, `garbage bulk-drop completed: verified=true`.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; targeted checks не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*`, mojibake или новых `AB/ab_*` runtime-маркеров в изменённых Java-файлах. `git diff --check` по изменённым файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка с `Бесполезный хлам > 1`: ожидается повтор `garbage bulk-drop redirect`/fresh `an_auto_cut_cleanup_verify=1` до полного отсутствия предмета, а `cleanup completed` только после `verified=true`.

## Debug 2026-05-18: cleanup `Бесполезный хлам` открывал не ту вкладку

- [x] Проверены свежие логи `logs/Critical/20260518_09_00_*`: ручное открытие `main.php?im=0` показало только обычную вкладку, а ручной переход `main.php?wca=60` сразу дал 3 строки `Бесполезный хлам` (`INV_GROUP_TRACE sample[0..2]`).
- [x] Проверены логи `logs/Critical/20260517_14_00_*`: auto-cleanup заходил через `go=inv&im=0`, находил только `rawDeleteMarkers=1`, делал один `get_id=50`, затем verification уходил в `main.php?im=0&an_auto_cut_cleanup_verify=1` и ложно завершал cleanup с `remainingVisible=0`.
- [x] Root cause: existing contour работал в правильной WebView/inventory цепочке, но для garbage cleanup проверял вкладку `im=0`, тогда как полный список `Бесполезный хлам` находится в категории `wca=60`. Поэтому при количестве >1 удалялся максимум первый видимый экземпляр, а verification не видел остальные.
- [x] Исправление внесено в существующий `AutoCutHandler`: вход в инвентарь остаётся через реальную `go=inv&vcode&im=0` ссылку, но перед drop для `Бесполезный хлам` handler переключает уже открытый inventory на `main.php?wca=60`; fresh verification после `get_id=50` тоже идёт в `main.php?wca=60&an_auto_cut_cleanup_verify=1`.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; targeted checks не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*`, mojibake или новых `AB/ab_*` runtime-маркеров в изменённом `AutoCutHandler.java`. `git diff --check` по изменённым файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: после `garbage cleanup requested` ожидается `cleanup redirect to inventory ... filter=&im=0`, затем `garbage cleanup switch to quest inventory category`, затем повторяющиеся `garbage bulk-drop redirect`/`get_id=50`/`wca=60&an_auto_cut_cleanup_verify=1` до полного отсутствия `Бесполезный хлам`.

## Debug 2026-05-18: ручное открытие inventory не выбрасывало `Бесполезный хлам`

- [x] Проверены свежие логи `logs/Critical/20260518_11_30_*`: пользователь вручную открыл `main.php?wca=60`, `InventoryParser` распарсил строки `Бесполезный хлам`, но `garbage bulk-drop redirect`/`get_id=50` не появились.
- [x] Root cause: `InventoryParser.mainPhpInv(...)` запускал garbage drop только когда уже активен `AppVars.AutoCutCleanupPending` + `BulkDropThing=Бесполезный хлам`. При выключенном `Авто-Травник/Авто-Лесоруб` popup `Рюкзак слишком тяжелый` логировал `heavy backpack garbage cleanup ignored: AutoCut disabled`, а ручное открытие категории не выставляло `BulkDropThing`.
- [x] Исправление внесено в existing inventory bulk-drop contour без нового HTTP-пути: если в любом открытом inventory/category виден `DropLink` для `Бесполезный хлам` и нет чужого активного bulk-drop, `InventoryParser` автозапускает существующее `BulkDropThing=Бесполезный хлам` и сразу отдаёт штатный redirect на `DropLink`.
- [x] Защита ручного bulk-drop: если пользователь уже запустил выбрасывание другой пачки, автоматический garbage-drop не перебивает это действие и логирует skip до завершения чужого bulk-drop.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; targeted checks не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake в изменённом `InventoryParser.java`. `git diff --check` по изменённым файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: при ручном открытии `main.php?wca=60` ожидаются `garbage bulk-drop auto-start`, затем `garbage bulk-drop redirect` и повторные `get_id=50` до сообщения о завершении пачки.

## Debug 2026-05-18: автопитьё `Эликсир Восстановления` отправляло запросы циклом

- [x] Проверены свежие логи `logs/Critical/20260518_13_00_*`: после `AUTO_DRINK_TRACE trigger restore elixir` чат получал повторные `Запрос отправлен: <b>Эликсир Восстановления</b>` примерно каждые 5 секунд.
- [x] Найден existing contour без нового HTTP/inventory-пути: `AutoDrinkHandler.tryTriggerAutoDrinkRestoreElixir(...)` принимает решение по HP/MA, `FastActionManager.processMainPhpFast(...)` ищет эликсир на `im=6`, а `mainPhpFastElixir(...)` формирует GET `get_id=43&act=101`.
- [x] Root cause: после формирования fast-action для restore-elixir `fastCancel("fast-action-finished")` корректно очищал `FastNeed`, но общий auto-drink cooldown был всего `2500ms`; пока сервер постепенно восстанавливал HP/MA и показатели оставались ниже порога, handler повторно запускал тот же эликсир.
- [x] Исправление внесено в existing auto-drink/fast-action contour: `FastActionManager` сообщает `AutoDrinkHandler.markRestoreElixirRequestSent(...)` только после реально сформированного restore-elixir запроса, а `AutoDrinkHandler` подавляет повторный auto-trigger, пока активно retry-окно 30 секунд или наблюдается рост HP/MA.
- [x] FastNeed-инвариант сохранён: `fastCancel(...)` не заменён guard-ом и продолжает очищать fast-action state; guard работает только на стороне решения автопитья.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon :app2:assembleDebug` успешно; targeted checks не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake в изменённых Java-файлах. `git diff --check` по изменённым файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: после первого restore-elixir ожидаются `AUTO_DRINK_TRACE restore guard armed`, затем `restore guard skip`/`restore guard progress`; повторный `trigger restore elixir` допустим только после отсутствия прогресса дольше 30 секунд или истечения max guard 300 секунд.

## Debug 2026-05-19: Anti-Captcha submit с устаревшим fight `vcode`

- [x] Проанализированы `logs/Critical/20260519_08_20_*` и `BoiBot_captcha.har` для рабочего браузерного сценария.
- [x] Подтверждено: рабочий HAR отправляет код вместе с согласованными `captcha image token + finishUrl/vcode` (`modules/code/code.php?...` и `main.php?code=...&get_id=61&act=7...`).
- [x] Root cause: существующий popup-контур `MainActivity.updateCaptchaImageFromCaptured(...)` мог переключить картинку на свежий `LastFightCaptchaImageUrl`, но взять старый `activeFightFinishUrl`, если новый `AppVars.FightLink` ещё не был синхронизирован. В логах это видно как `captchaUrl=...200261...` вместе со старым `vcode=edbc...`, хотя `LezFight.BuildFightLink(captcha)` уже генерировал новый `vcode=ba158...`.
- [x] Исправление внесено в существующий `MainActivity.showCaptchaDialog(...)`/`updateCaptchaImageFromCaptured(...)` contour без нового popup/HTTP-контура: switch на новую картинку разрешён только при валидном pending `act=7` finish-link и совпадении его `vcode` с текущим `SessionManager fight_fallback`.
- [x] Дополнительная защита submit: ручной OK и Anti-Captcha перед отправкой повторно берут актуальный `activeFightFinishUrl`; Anti-Captcha key/solution/retry сравниваются с текущим finishUrl, а stale `vcode` блокирует auto-submit с логом `CAPTCHA_VCODE_TRACE`.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon` успешно; targeted checks не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake в изменённом `MainActivity.java`. Runtime `ab_*`-совпадения в `MainActivity.java` являются существующими строками вне текущего diff. `git diff --check` по изменённым файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: в новых логах не должно быть пары `updateCaptchaImageFromCaptured: switch to latest challenge` с новым `captchaUrl` и старым `vcode`; при гонке ожидается `CAPTCHA_VCODE_TRACE ... stale finish vcode` и последующий submit только с актуальным `vcode`.

## Debug 2026-05-19: `fexp[4]` после submit и AutoCut restore cleanup

- [x] Повторно проверен existing contour `FightAuto.extractCaptchaUrlFromFexp(...)`: причина повторного popup в `app2` была в том, что `fexp[6]` трактовался как countdown `0..30`, хотя активной боевой captcha считается только `fexp[6] == "0"`.
- [x] Исправление внесено без нового captcha/HTTP-контура: `FightAuto.extractCaptchaUrlFromFexp(...)` теперь разбирает `fexp` через `splitJsTopLevelCsv(...)`, строит URL картинки только при `fexp[6] == "0"`, а ненулевой flag логирует как переходное состояние.
- [x] Обновлён комментарий `AppVars.FightCaptchaSubmitNotBeforeMs`: ненулевой `fexp[6]` не должен запускать popup/auto-submit, чтобы не отправить stale `vcode`.
- [x] По `logs/Critical/20260519_23_10_auto_cut_trace.log` найден root cause зависания AutoCut после `NeverTimer`: `restorePersistentAutoModesAfterLogin()` вызывал `setAutoLumberjackEnabled(true)`, а `AutoCutManager.onAutoCutEnabled(...)` сбрасывал `AutoCutCleanupPending` перед due `cleanup_inventory:*` retry. В результате `SERVER_TIMER_TICK auto-cut cleanup inventory reload` попадал в tool-check loop вместо cleanup.
- [x] Исправление внесено в существующий bootstrap-contour `AutoCutManager.onAutoCutEnabled(...)`: если активен `AutoCutCleanupPending` или pending retry `cleanup_inventory:*`, bootstrap сохраняет cleanup-state/tool-state и делает reload с source `enabled_cleanup_resume:*` вместо сброса в `enabled_tool_check`.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon` успешно. Targeted checks не нашли новых `AppVars.VCode` или прямого `android.util.Log`/`Log.*` в изменённом контуре; `git diff --check` по изменённым файлам по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями.
- [ ] Live-проверка: после `restore after login: auto-lumberjack owns cold start bootstrap` при pending cleanup ожидается `enabled bootstrap preserves pending cleanup`, затем `SERVER_TIMER_TICK auto-cut cleanup inventory reload`, `cleanup redirect/switch to quest inventory category`, `garbage bulk-drop redirect/completed`; не должно быть серии `tool not armed ... inventory address has no inventory html` до завершения cleanup.

## Debug 2026-05-20: повторная captcha после принятого submit

- [x] Проанализирован свежий `logs/Logcat/20260520_03_30_logcat.txt` вокруг `03:31-03:32`.
- [x] Подтверждено: первый submit Anti-Captcha (`code=77848`, `vcode=9263e1d1...`) сервер принял, в ответе есть `Победа за Юличка.`, но текущий finish-flow снова открыл captcha по `fexp[4]` с `fexp[6]=30` и новым `vcode=082a94bc...`.
- [x] Root cause: после частичного фикса `extractCaptchaUrlFromFexp(...)` оставались два helper-а finish-flow, где `isRealFexpCaptchaFlag(...)` считал `0..30` активной captcha. Поэтому flag `30` блокировал normal/clean finish-link и мог создавать phantom captcha после уже принятого кода.
- [x] Исправление внесено в existing `FightAuto` fexp decision point без нового popup/HTTP-контура: активной captcha теперь считается только `fexp[6] == "0"`; ненулевой flag больше не блокирует finish-link fallback.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon` успешно; targeted checks по `FightAuto.java` не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake. Grep `TODO2/todo_DebugApp.md` по mojibake-паттернам находит только документированную строку с самими паттернами.
- [-] Повторная проверка логов: свежего post-fix live-сегмента в `logs/` нет; последний доступный `logs/Logcat/20260520_03_30_logcat.txt` всё ещё содержит старый pre-fix паттерн `Победа за Юличка.` -> `CAPTCHA_REQUIRED` -> длительный `captchaDialogVisible=true`.
- [ ] Live-проверка: в новых логах после принятого submit ожидается завершение/redirect без `CAPTCHA_REQUIRED` для `fexp[6]=30` и без зависания `captchaDialogVisible=true`.

## Debug 2026-05-20: 04:09 captcha-submit вернул transition `fexp[6]=30`

- [x] Проанализированы свежие `logs/Logcat/20260520_04_00_logcat.txt`, `logs/Logcat/20260520_04_10_logcat.txt` и связанные `logs/Critical/20260520_04_00_*` / `20260520_04_10_*`.
- [x] Подтверждено: Anti-Captcha submit `code=73757` отправлен с согласованными `captchaUrl`, `finishUrl` и `vcode=e00ecf82...`; это не повтор старой проблемы stale image-code/vcode mismatch.
- [x] Root cause: после принятого submit сервер вернул transition fight-frame с captcha token в `fexp[4]` и ненулевым `fexp[6]=30`; клиент не считал это accepted transition и затем синтезировал новый `act=7&fexp=48&vcode=3399...` без `code=73757`, что запускало finish-loop и новый Anti-Captcha answer для того же боя.
- [x] Исправление внесено в existing `FightAuto` captcha/finish-flow contour без нового retry/HTTP-контура: captcha-submit response с captcha token и non-zero `fexp[6]` считается accepted transition, помечает `markFightFinishConfirmed(..., "fight_captcha_submit_transition", address)` и redirect-ит на `main.php?get_id=56&act=10&go=inf`.
- [x] Дополнительная защита в том же contour: `extractFightFinishLinkFromHtml(...)` и `extractFightCleanVcodeFromFexp(...)` больше не синтезируют normal finish-link/clean fallback из transition `fexp`, если `fexp[4]` содержит captcha token.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon` успешно; targeted checks по `FightAuto.java` не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake. `git diff --check -- app2/src/main/java/ru/neverlands/anclient/postfilter/FightAuto.java` не показал whitespace errors в изменённых строках, но Git продолжает шуметь существующей `.gitattributes:7` и CRLF warning.
- [ ] Live-проверка: после captcha-submit ожидается `processFight: accepted captcha submit returned transition fexp, finish confirmed`; не должно быть повторного `act=7&fexp=...&vcode=...` без `code=...` и второго Anti-Captcha answer для того же боя.

## Debug 2026-05-20: 04:42 Anti-Captcha решил код, но submit заблокирован как stale

- [x] Проанализированы свежие `logs/Logcat/20260520_04_40_logcat.txt` и critical-файлы `20260520_04_40_mainactivity.log`, `sessionmanager.log`, `lezfight.log`, `webviewinterceptor.log`, `anticaptchamanager.log`.
- [x] Подтверждено: первая captcha challenge была согласованной: `showCaptchaDialog` открыл `modules/code/code.php?3372267026a0d117cb7e92` с finishUrl `vcode=6286066e...`, картинка была перехвачена как PNG `5900 bytes`, Anti-Captcha стартовал по тому же key и решил код через 10 секунд.
- [x] Root cause: уже после открытия popup в `04:42:24.112` сработал ранее поставленный delayed auto-battle submit (`04:42:24.569`), который отправил авто-удар с боевым `vcode=f87d0de9...`; затем сервер/WebView построили новый captcha finish-link `vcode=7d7a5a8c...` и новый image URL `modules/code/code.php?4730726176a0d117fae270`.
- [x] Почему пользователь видел старую картинку: `WebViewRequestInterceptor` намеренно заблокировал foreign captcha request `...?473072...` и отдал старые bytes ожидаемой картинки `...?337226...`, чтобы не подменить активный popup. Поэтому UI остался на старой картинке, но `SessionManager` уже видел новый `vcode=7d7a5a8c...`.
- [x] Почему submit не ушёл: `MainActivity.isFightCaptchaFinishVCodeCurrent(...)` сравнил active finish `vcode=6286066e...` с current `SessionManager fight_fallback=7d7a5a8c...` и правильно остановил отправку с сообщением `Капча обновилась, дождитесь новой картинки`.
- [x] Исправление внесено в existing `MainActivity` auto-turn/auto-submit contour без нового captcha/HTTP-контура: при pending fight captcha (`code=????`) или открытом fight captcha popup очищается delayed auto-battle submit, новые auto-battle submit/server-probe пропускаются, а поздний server-probe result не передаётся в `FightViewModel.autoTurnOnce(...)`.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon` успешно; targeted checks по `MainActivity.java` не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или mojibake. `git diff --check` по изменённым файлам по-прежнему шумит существующей `.gitattributes:7` и CRLF warning, без новых whitespace errors в diff.
- [ ] Live-проверка: после `showCaptchaDialog` не должно быть `autoBattleDelay: submit now`/`submitAutoBattleAction` до submit captcha; ожидаемые маркеры `showCaptchaDialog: cleared pending auto battle submit for fight captcha`, `autoBattleDelay: drop delayed submit, fight captcha pending` или `server probe skipped, fight captcha pending`; current `fight_fallback` должен оставаться vcode активного finishUrl до отправки кода.

## Debug 2026-05-20: 10:42 stale captcha после cached auto-turn

- [x] Проанализированы свежие `logs/Logcat/20260520_10_40_logcat.txt` и critical-файлы `20260520_10_40_mainactivity.log`, `sessionmanager.log`, `lezfight.log`, `fightviewmodel.log`, `webviewinterceptor.log`, `anticaptchamanager.log`.
- [x] Подтверждено: первая captcha challenge была согласованной: `modules/code/code.php?13486849986a0d65f9833b2`, bytes `4737`, finishUrl `vcode=16717036...`; Anti-Captcha стартовал по этому же key и решил код.
- [x] Предыдущий 04:42 guard частично сработал: `requestAutoTurn: server probe skipped, fight captcha pending`, а после popup был `showCaptchaDialog: cleared pending auto battle submit for fight captcha`.
- [x] Root cause: в том же `requestAutoTurn` после распознавания inactive captcha HTML (`FightLink code=????`) `FightContextChoiceHandler` fallback-нул на cached active fight HTML и вызвал локальный `FightViewModel.autoTurnOnce(...)`; это отправило auto-hit `10:42:52.978` с `vcode=8718df8a...` уже после появления pending captcha, но до открытия popup.
- [x] Итог гонки: сервер построил новый captcha state `vcode=aeed228d...` и image URL `modules/code/code.php?851539186a0d65fb7ee98`; `WebViewRequestInterceptor` корректно заблокировал foreign image и оставил старую картинку, но submit Anti-Captcha был остановлен как stale (`finish=16717036...`, `session=aeed228d...`).
- [x] Исправление внесено в existing `MainActivity.requestAutoTurn` contour без нового captcha/HTTP-контура: при pending fight captcha теперь пропускается не только server-probe/delayed submit, но и локальный cached-html auto-turn из `applyFightContextDecision(...)`; pending submit очищается до выхода.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon` успешно; Java-diff targeted checks не нашли новых `AppVars.VCode`, прямого `android.util.Log`/`Log.*` или legacy `AB/ab_*` runtime-префиксов. Mojibake-проверка Java-файлов не нашла `РЎ`/`Рџ`/`Ð`/`Ñ`; `git diff --check` по изменённым файлам по-прежнему шумит существующей `.gitattributes:7` и CRLF warning без новых whitespace errors.
- [ ] Live-проверка после 10:42 fix: в новых логах после `FightLink code=????` ожидается `requestAutoTurn: skip autoTurn, fight captcha pending`, без последующего `autoBattleDelay: submit now`/`autoTurnOnce: submit posted` до captcha submit.

---

# Аудит качества кода app2/ANClient (2026-07-26): план оптимизации и рефакторинга

> **Источник:** аудит всего модуля `app2/` (192 Java-файла) по запросу пользователя.
> **Область изменений (AGENTS2 п.1):** только `app2/` и связанные корневые файлы сборки. `app/`, `ANClient/`, `ABClient/` не трогаем.
> **Явно ВНЕ области (решение пользователя):** хранение учётных данных — пункты «Критично — Безопасность (учётные данные) №1-3» (открытый пароль в XML/SharedPreferences, `EncryptedSharedPreferences`/Keystore) **НЕ реализуем в этой итерации**. Модернизируем **только** сам крипто-модуль `CryptoUtils` (направление D1).

## Общий статус

- [x] Аудит проведён, находки классифицированы.
- [x] Декомпозиция на направления D1-D6 выполнена.
- [x] D1. CryptoUtils — модернизация шифрования. — **выполнено 2026-07-26**
- [x] D2. WebView — сокращение поверхности атаки. — **выполнено 2026-07-26**
- [x] D3. Утечки памяти / threading. — **выполнено 2026-07-26**
- [x] D4. Обработка ошибок (молчаливые catch / printStackTrace). — **выполнено 2026-07-26**
- [x] D5. Устранение дублирования (InventoryParser, containsIgnoreCase). — **выполнено 2026-07-26**
- [~] D6. Архитектурный рефакторинг god-классов — **начат 2026-07-26** (шаг 1 из N выполнен, остальное требует согласования).
- [x] Финальная сборка `./gradlew2.bat --no-daemon` + анти-регресс grep'ы — **пройдены**.

## Порядок выполнения (этапность)

1. **Этап 1 (низкий риск, высокий эффект):** D4 (логирование catch) → D5 (дедупликация) — не меняют поведение, чистят базу.
2. **Этап 2 (средний риск):** D3 (threading/утечки) → D2 (WebView-настройки).
3. **Этап 3 (изолированный, требует ручной проверки логина):** D1 (CryptoUtils).
4. **Этап 4 (крупный, поэтапно, за отдельными подтверждениями):** D6 (god-классы).

Каждое направление сдаётся отдельно: правка → сборка `./gradlew2.bat --no-daemon :app2:assembleDebug` → grep-проверки → отметка в этом файле.

---

## D1. CryptoUtils — модернизация шифрования (Критично)

**Файл:** `app2/src/main/java/ru/neverlands/anclient/utils/CryptoUtils.java:18,20,23`
**Потребители (важно для совместимости):**
- `LoginActivity.java:551,624` — `decrypt` (вход/relogin snapshot).
- `handlers/SessionReloginHandler.java:134,137` — `decrypt` (авто-relogin, критичная цепочка).
- `ProfileActivity.java:493,515` — `decrypt`; `ProfileActivity.java:546,547` — **единственный `encrypt`** (сохранение профиля).

**Проблема:** `DESede/CBC` (3DES) + статичная захардкоженная соль `"Ivan Medvedev"` + `ITERATION_COUNT=1000` + `windows-1251`. Устаревший шифр, одинаковая соль для всех, мало итераций.

**Совместимость с C# ПК-версией — БОЛЬШЕ НЕ ТРЕБУЕТСЯ (уточнение пользователя 2026-07-26).** Ранее 3DES-схема была нужна для импорта `.profile` из ПК-клиента; сейчас это требование снято. Значит можно перейти на чистый современный формат без обязательного legacy-3DES ради ПК.

**Остаётся отдельный вопрос — локальная миграция:** уже сохранённые в предыдущих сборках Android **локальные** зашифрованные профили созданы старым 3DES-`CryptoUtils`. Полный отказ от legacy-чтения сделает их нечитаемыми → пользователю придётся заново ввести пароль в профиле. Это UX-решение, не ПК-совместимость (см. открытый вопрос).

**Решение — чистый новый формат (AES-GCM), legacy-чтение опционально:**
- `encrypt(...)` → **новый формат**: префикс-версия `ANC1:` + `Base64(salt||iv||ciphertext||tag)`, алгоритм **AES-256/GCM/NoPadding**, случайная соль (16 байт) на запись, IV 12 байт, полезная нагрузка UTF-8.
- Деривация ключа: **PBKDF2WithHmacSHA1, ITERATION_COUNT = 120_000** (единый baseline, доступен на всех API от 21 без веток по SDK).
- `decrypt(...)` → по префиксу: `ANC1:` → новый AES-GCM путь; иначе → best-effort `decryptLegacy(...)` (3DES) **для плавной миграции локальных профилей** (legacy-чтение остаётся).
- Ленивая миграция: при успешном `decryptLegacy` в `ProfileActivity` перешифровать в `ANC1:` и пересохранить (чтобы старый формат постепенно вытеснялся).

**Принятые решения (2026-07-26):**
- ПК-совместимость 3DES — снята (не требуется).
- Локальная миграция — **вариант A**: оставить legacy-3DES-чтение + ленивая перешифровка в `ANC1:` (не терять сохранённые пароли).
- PBKDF2 — **SHA1 @ 120_000 итераций**, единый путь.

**План:**
- [x] Реализовать `encrypt(...)` в формате `ANC1:` (AES-256-GCM + случайная соль 16B + IV 12B + PBKDF2-SHA1 @120k, UTF-8).
- [x] Извлечь текущую 3DES-логику в приватный `decryptLegacy(...)` (поведение 1:1).
- [x] Реализовать `decrypt(...)`: `ANC1:` → AES-GCM; иначе → `decryptLegacy(...)`.
- [x] Ленивая перешифровка: реализована в `LoginActivity` (точка, где профиль расшифровывается и **остаётся зашифрованным**).
- [x] Round-trip тест `encrypt→decrypt`; корректная ошибка при неверном пароле; legacy-строка читается.
- [ ] Ручная проверка на устройстве: логин по новому и по старому (legacy) профилю; авто-relogin (`SessionReloginHandler.java:134,137`); сохранение профиля (`ProfileActivity.java:546,547`).
- [x] Логирование: без утечки пароля/ключа; при ошибке миграции — `AppLog.w` с причиной (не содержимым).
- [x] Обновить Javadoc `CryptoUtils`: новый формат `ANC1:`; отмечено, что legacy-3DES оставлен только для чтения старых локальных профилей.

### Результат D1 (2026-07-26)

**Новый формат `ANC1`:**
```
"ANC1:" + Base64( salt[16] || iv[12] || ciphertext+tag )
```
- Шифр: **AES-256/GCM/NoPadding** (аутентифицированное шифрование — подмена шифротекста обнаруживается).
- Деривация: **PBKDF2WithHmacSHA1, 120 000 итераций** (вместо 1000).
- **Случайная соль 16 байт на каждую операцию** (вместо статичной `"Ivan Medvedev"` для всех пользователей).
- IV 12 байт (рекомендованный nonce для GCM), тег 128 бит, полезная нагрузка UTF-8, `Base64.NO_WRAP` (безопасно для XML-атрибута).

**Почему PBKDF2-SHA1, а не SHA256:** `PBKDF2WithHmacSHA256` доступен только с API 26, а `minSdkVersion` проекта — **21**. Выбран единый путь без ветвлений по версии SDK; 120k итераций компенсируют выбор PRF.

**Совместимость:**
- Совместимость с C# ПК-версией снята решением пользователя — новый формат намеренно несовместим.
- `decrypt(...)` определяет формат по префиксу и читает старый 3DES-формат через `decryptLegacy(...)` (поведение 1:1, включая `windows-1251`). Legacy используется **только для чтения**; любая новая запись идёт в `ANC1`.
- Добавлен публичный `isLegacyFormat(...)` — нужен для ленивой миграции.

**Ленивая миграция (`LoginActivity.migrateProfileEncryptionIfLegacy(...)`):**
- Точка выбрана осознанно: `ProfileActivity.showEnterEncryptionPasswordToDecryptDialog(...)` расшифровывает профиль, чтобы **снять** шифрование (там миграция бессмысленна). А `LoginActivity` расшифровывает профиль, который **остаётся зашифрованным** — именно там перешифровка уместна.
- Срабатывает только после успешной расшифровки (ключ гарантированно верный), перешифровывает главный и flash-пароль тем же ключом и сохраняет профиль.
- Ошибка миграции **не блокирует вход**: профиль остаётся в старом формате, пишется `AppLog.w`. Успех логируется как `CRYPTO_MIGRATION: profile re-encrypted to ANC1`.

**Фактическая проверка криптографии (автономный прогон на JDK 17, вне репозитория):**

| Проверка | Результат |
|---|---|
| ANC1 round-trip (ASCII) | OK |
| ANC1 round-trip (кириллица UTF-8) | OK |
| ANC1 round-trip (пустая строка) | OK |
| Префикс `ANC1:` присутствует | OK |
| Случайная соль/IV → шифротексты различаются | OK |
| Неверный пароль отвергается | OK (`AEADBadTagException`) |
| **Изменённый шифротекст отвергается** | OK (`AEADBadTagException`) — контроль целостности, которого у 3DES/CBC не было |
| Legacy-значение опознаётся как legacy | OK |
| Legacy 3DES читается через `decrypt()` | OK |
| Legacy кириллица (windows-1251) | OK |
| Ленивая миграция legacy → ANC1 | OK |
| Производительность PBKDF2@120k | 274 мс на desktop JVM |

**Замечание по производительности (важно для UX):** одна операция PBKDF2@120k ≈ **274 мс** на desktop JVM; на мобильном устройстве ориентировочно в 3-5 раз медленнее (~1-1.4 с). При входе в зашифрованный профиль выполняется 2 операции (основной + flash пароль) ≈ **2-3 с**, разово при миграции — ещё 2. Это происходит под уже показываемым прогресс-баром. Если на реальном устройстве задержка окажется некомфортной, число итераций — единственная константа `ANC1_ITERATION_COUNT`, снижается одной правкой.

**Проверки после фикса:**
- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL**.
- [x] 12/12 крипто-проверок пройдено (см. таблицу выше).
- [x] `android.util.Log` — **0**; `printStackTrace` — **0**; mojibake/BOM — **0**.
- [ ] Live-проверка: вход по **старому** профилю → в логах ожидается `decrypt: legacy 3DES payload detected` и затем `CRYPTO_MIGRATION: profile re-encrypted to ANC1`; повторный вход по тому же профилю — уже без legacy-сообщения; авто-relogin (`SessionReloginHandler`) работает; неверный ключ по-прежнему даёт «Неверный пароль шифрования».

---

## D2. WebView — сокращение поверхности атаки (Критично/Высоко)

**Файлы:** `MainActivity.java:4581-4595`; дубли настроек `manager/TabManager.java:578-590`, `ui/Navigator.java:445-463`; мост `bridge/WebAppInterface.java` (`loadFrame:2300`, `redirectToUrl:2523`, `openInNewTab:2509`, `chatSubmit:1608`); interceptor `webview/WebViewRequestInterceptor.java:316-543`.

**Проблема:**
- `setAllowUniversalAccessFromFileURLs(true)` (4584) + `setAllowFileAccessFromFileURLs(true)` (4583): `file://` получает доступ к любому origin.
- `setMixedContentMode(MIXED_CONTENT_ALWAYS_ALLOW)` (4592).
- `addJavascriptInterface` (4595) в связке с cleartext HTTP → JS с подменённой по MITM страницы получает нативный мост.
- Мост принимает **произвольные URL/POST** под сессионными cookie (SSRF/CSRF-примитив изнутри WebView).

**Инвариант (AGENTS2 п.5, п.8):** нельзя ломать ручные HTML-клики и JS-фиксы (`transfer/complect/hbar`, `document.FBT`-shim). Не плодить параллельные контуры — свести настройки в один модуль.

**План:**
- [x] Создать единый `WebViewConfigurator` — один decision point настроек WebView; заменить разрозненные места (`MainActivity`, `TabManager`).
- [x] Отключить `setAllowUniversalAccessFromFileURLs`/`setAllowFileAccessFromFileURLs`.
- [-] Пересмотреть `MixedContentMode` — **осознанно оставлен `ALWAYS_ALLOW`** (обоснование ниже).
- [x] В `WebAppInterface`: добавлен единый валидатор URL для `loadFrame`/`redirectToUrl`/`openInNewTab`/`chatSubmit`; чужие хосты отклоняются с `AppLog.w`.
- [ ] Проверить работу ручных кликов и JS-shim на устройстве (чек-лист AGENTS2 п.5) — требуется live-прогон.

### Результат D2 (2026-07-26)

**1. Единый `WebViewConfigurator` (новый класс `webview/WebViewConfigurator.java`).**
- Настройки WebView дублировались в трёх местах и **разошлись**: опасные `setAllowFileAccessFromFileURLs(true)` и `setAllowUniversalAccessFromFileURLs(true)` стояли только в `MainActivity`, в `TabManager` их не было.
- Введены профили `Profile.MAIN_GAME` (нужны `setDatabaseEnabled` + `setSupportMultipleWindows` для `window.open`) и `Profile.SECONDARY_TAB`.
- `MainActivity.setupWebView(...)` и `TabManager.setupSecondaryWebView(...)` переведены на конфигуратор; cookie-настройки (`setAcceptCookie`, `setAcceptThirdPartyCookies`) тоже переехали внутрь, чтобы профили не разъезжались.

**2. Сняты опасные file-URL флаги.**
- Проверено по коду, что **игровые WebView грузят только `http://`**; единственный потребитель `file:///android_asset/` — мини-карта навигатора (`Navigator.loadDataWithBaseURL(..., "file:///android_asset/", ...)` + `<script src='file:///android_asset/mapnav.js'>`).
- Мини-карта настраивается отдельно и **намеренно не переведена** в конфигуратор: у неё принципиально другой профиль доступа к файлам. Её настройки не менялись (риск сломать отрисовку карты).
- Итог: для игровых фреймов флаги давали нулевую пользу, но открывали `file://`-документу доступ к любому origin — в связке с `addJavascriptInterface` и cleartext-трафиком это была самая опасная комбинация. Удалены.

**3. `MixedContentMode` — осознанно НЕ понижен.**
- Игра работает по `http://` (`usesCleartextTraffic="true"`), при этом форум/pinfo могут отдаваться по `https` и подтягивать `http`-ресурсы. Переход на `COMPATIBILITY_MODE` заблокировал бы смешанные скрипты и мог сломать эти страницы.
- Режим сохранён, но теперь задаётся **в одном месте** (конфигуратор) с комментарием-обоснованием — при желании меняется одной строкой.

**4. Allowlist URL для JS-моста (главная мера против SSRF/CSRF).**
- Добавлен `WebAppInterface.isAllowedBridgeUrl(url, bridgeMethod)`.
- **Переиспользован существующий** `GameServerUrls.isNeverlandsHost(...)` (AGENTS2 п.8) — он уже покрывает `neverlands.ru`, все поддомены (включая `forum.neverlands.ru`) и настроенные хосты/IP игровых серверов DE/KZ, поэтому легальные переходы не ломаются. Дополнительно разрешены `127.0.0.1`/`localhost` (локальный прокси).
- Проверка схемы: только `http`/`https` (отсекает `javascript:`, `file:`, `content:` и пр.).
- Применено в 4 методах: `chatSubmit` (L1664), `loadFrame` (L2364), `openInNewTab` (L2569), `redirectToUrl` (L2594). Отказ логируется как `BRIDGE_URL_REJECTED` с методом и причиной (пустой URL / чужая схема / чужой хост / ошибка парсинга).

**Проверки после фикса:**
- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL**.
- [x] `setAllowUniversalAccessFromFileURLs` / `setAllowFileAccessFromFileURLs` в коде — **0**.
- [x] `setJavaScriptEnabled(true)` остался только в: `WebViewConfigurator` (игровые WebView), `Navigator` (мини-карта, отдельный профиль), `PinfoActivity`, `ForpostInfoActivity` (отдельные информационные экраны).
- [x] Валидация подключена во всех 4 bridge-методах.
- [x] `android.util.Log` — **0**; `printStackTrace` — **0**; mojibake/BOM в 41 изменённом файле — **0**; новых legacy `ab_*`/`ABCLIENT` — **0**.
- [ ] **Live-проверка обязательна (AGENTS2 п.5):** ручные HTML-клики (`Взять из казны`, `main.php?get_id=60`) срабатывают с первого раза; чат отправляет сообщения (`chatSubmit`); открытие форума/pinfo в новой вкладке работает; переключение сервера DE/KZ не даёт `BRIDGE_URL_REJECTED`; в логах нет `null.innerHTML`.

**Открытый вопрос (на решение пользователя):**
- [ ] Понижать ли `MixedContentMode` до `COMPATIBILITY_MODE`? Плюс — меньше рисков MITM на https-страницах; минус — возможна поломка форума/pinfo, где https-страница тянет http-ресурсы. Сейчас оставлено `ALWAYS_ALLOW` (как было). Рекомендация: менять только после live-проверки форума.

---

## D3. Утечки памяти / threading (Средне)

**Находки:**
- `manager/ContactsManager.java:479-493` — рекурсивная `handler.postDelayed`-цепочка на **static Handler без отмены**; `ContactsActivity.java:726,728` передаёт `this` (Activity) + onComplete-лямбду → удержание Activity. **(приоритет)**
- Анонимные `new Handler(...).postDelayed(...)` без хранения Runnable → нельзя отменить: `BossAuto.java:1318,1348,1901`, `AutoCutManager.java:981,1624,1673`, `FastActionManager.java:339`.
- `ui/viewmodel/FightViewModel.java:125,194,260` — `new Thread` без `onCleared()`/отмены (боевая цепочка).
- `ContactsActivity.java:657` — `InterruptedException` проглочен без `Thread.currentThread().interrupt()`.
- `manager/AntiCaptchaManager.java:215-258` — новый `OkHttpClient` на каждый polling-запрос (лишние сокеты).
- `manager/ContactsManager.java:64,72` — static `ExecutorService`+`Handler` без shutdown (архитектурно).

**Инвариант:** FastNeed-цепочки (AGENTS2 fast-action) и порядок инициализации боя не ломать; отмена задач не должна убивать легитимный автоход.

**План:**
- [x] `ContactsManager`: добавлена отмена цепочки; `ContactsActivity` вызывает её в `onDestroy`; передаётся `getApplicationContext()` вместо `this`, onComplete защищён guard'ом.
- [x] `BossAuto`/`AutoCutManager`/`FastActionManager`: отложенные задачи планируются с токеном и отменяются в существующих точках выключения.
- [x] `FightViewModel`: добавлен `onCleared()` с остановкой фоновых задач (переведено на управляемый executor).
- [x] `ContactsActivity.java:657`: восстановлен interrupt-флаг; `printStackTrace()` → `AppLog.w` (сделано в D4).
- [x] `AntiCaptchaManager`: OkHttpClient кэшируется и пересобирается только при смене proxy-сигнатуры.
- [-] Graceful shutdown static-executors при logout — отложено (низкий приоритет, отдельная задача).

### Результат D3 (2026-07-26)

**1. `ContactsManager` — отменяемая цепочка обновления контактов (главная находка).**
- Проблема: рекурсивная цепочка `handler.postDelayed(...)` на **статическом** Handler не имела механизма отмены. `ContactsActivity.updateGroup(...)` передавал `this` (Activity) и onComplete-лямбду, захватывающую Activity, — цепочка удерживала экран до полного обхода списка (шаг 1200 мс на контакт, т.е. минуты).
- Добавлены `REFRESH_TOKEN` + `volatile refreshCancelled` + публичный `cancelContactsRefresh(reason)` и приватный `beginContactsRefresh(source)` (сброс отмены на старте).
- Шаги планируются через `handler.postAtTime(task, REFRESH_TOKEN, uptimeMillis()+1200)`. **Важно:** перегрузка `postDelayed(Runnable, Object token, long)` доступна только с API 28, а `minSdkVersion` проекта — 21, поэтому используется `postAtTime`, существующий с API 1.
- Флаг `refreshCancelled` проверяется не только в отложенном шаге, но и в обоих колбэках `ApiRepository` (`onSuccess`/`onFailure`) — цепочка продолжается и оттуда, а такие продолжения из очереди Handler удалить нельзя.
- Из приватного `updateContactsRecursive(...)` **удалён неиспользуемый параметр `Context`**: внутри он не применялся, но протаскивался по всей рекурсии и удерживал Activity. Публичные сигнатуры `refreshAllContacts/refreshGroupContacts/refreshNeutralContacts(Context, ...)` не менялись (без churn у вызывающих).
- `ContactsActivity.onDestroy()` → `cancelContactsRefresh("contacts_activity_destroyed")`.
- `updateGroup(...)` приведён к эталону уже существующего корректного `refreshContacts()`: передаётся `getApplicationContext()`, а onComplete проверяет `isFinishing()/isDestroyed()` до обращения к UI.

**2. `BossAuto` — отменяемые отложенные чат-сообщения.**
- 3 анонимных `new Handler(...).postDelayed(...)` (задержки 500 мс / 1 с для анти-DDoS) заменены на общий `CHAT_DELAY_HANDLER` + `CHAT_DELAY_TOKEN` через новый `postDelayedChatTask(...)`.
- Отмена добавлена в **существующую** точку остановки сценария `stopAndRestore(...)` (не создавался новый контур). Раньше сообщение уходило в чат даже после выключения Авто-Босса.

**3. `AutoCutManager` — отменяемые маршрутные задачи.**
- 3 анонимных handler'а (tired-route retry, route-next, timer-route return) переведены на `ROUTE_HANDLER` + `ROUTE_TOKEN` через `postDelayedRouteTask(...)`.
- Отмена добавлена в существующий `onAutoCutDisabled()` — он уже сбрасывал `tiredRouteRetryPending`, но не мог остановить уже запланированный `startAutoMoving(...)`, из-за чего персонаж мог уйти по маршруту после выключения функции.

**4. `FastActionManager` — отменяемое отложенное восстановление.**
- Отложенный restore non-combat авто-функций (`FAST_FINALIZE_RESTORE_DELAY_MS`) переведён на `RESTORE_HANDLER` + `RESTORE_TOKEN`.
- `cancelPendingNonCombatRestore(...)` вызывается в `fastStart(...)` (новое быстрое действие делает старый restore неактуальным) и перед немедленным restore.
- **FastNeed-инвариант сохранён:** логика `fastCancel(...)` и порядок очистки флагов не менялись — добавлена только отменяемость отложенной задачи.

**5. `FightViewModel` — управляемый пул вместо «сырых» потоков.**
- 3 × `new Thread(...)` (`processFightHtml`, `autoTurnOnce`, `autoSelect`) заменены на единый `submitFightTask(...)` поверх `fightExecutor`.
- Выбран `Executors.newCachedThreadPool()`, **а не однопоточный executor**, чтобы сохранить прежнюю параллельность и не изменить тайминги боевой цепочки (AGENTS: не деградировать автобой).
- Добавлен `onCleared()`: `cleared = true` + `shutdownNow()`. `submitFightTask` проверяет `cleared` и перехватывает `RejectedExecutionException`.
- **Порядок AGENTS п.9 сохранён:** `markFightInProgress()` по-прежнему вызывается ПЕРЕД `new LezFight(html)` во всех трёх задачах (добавлены поясняющие комментарии).

**6. `AntiCaptchaManager` — переиспользование OkHttpClient.**
- Раньше клиент создавался заново на каждый `postJson(...)`, а метод вызывается в цикле polling'а решения капчи → лишние пулы соединений и сокеты.
- Добавлен кэш `cachedApiClient` + `cachedApiClientSignature`; пересборка только при смене proxy-маршрута, со `connectionPool().evictAll()` для старого клиента.
- Собственный кэш (а не `NetworkClient.getInstance()`) — намеренно: у Anti-Captcha принципиально другой маршрут (внешний HTTPS напрямую через upstream proxy либо `NO_PROXY`), локальный `127.0.0.1` proxy здесь запрещён, иначе OkHttp шлёт `CONNECT` в `LocalHttpProxyServer` и получает `501 Not Implemented`. Логика выбора маршрута не менялась.

**Проверки после фикса:**
- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL**.
- [x] Анонимных `new Handler(Looper.getMainLooper()).postDelayed` в `BossAuto`/`AutoCutManager`/`FastActionManager`/`ContactsManager` — **0** (остались только упоминания в Javadoc).
- [x] `new Thread(` в `FightViewModel` — **0** (остались только упоминания в Javadoc); `onCleared()` присутствует.
- [x] `new OkHttpClient.Builder` в `AntiCaptchaManager` — **1** (внутри кэширующего `obtainApiClient`).
- [x] `android.util.Log` вне инфраструктуры — **0**; `printStackTrace` — **0**; mojibake/BOM — **0**.
- [ ] Live-проверка: закрыть экран контактов во время обновления группы → в логах ожидается `cancelContactsRefresh: chain cancelled` и отсутствие дальнейших `updateContactsRecursive`; выключить Авто-Босса сразу после запроса цели → ожидается `pending chat messages cancelled` без ухода сообщения в чат; выключить Авто-Лесоруб при запланированном переходе → `pending route tasks cancelled` без `startAutoMoving`.

---

## D4. Обработка ошибок — молчаливые catch / printStackTrace (Средне)

**Находки (62 пустых catch, 11 `printStackTrace`):**
- **Приоритет (критичная сетевая/прокси-цепочка, AGENTS2 доп. п.6 требует FileLogger):** `webview/WebViewRequestInterceptor.java` — 11 пустых catch (812, 878, 1229, 1241, 1374, 1410, 1422, 1487, 1512, 1584).
- **Боевая цепочка:** `postfilter/FightAuto.java` (1989, 2076, 2163), `ui/viewmodel/FightViewModel.java` (83, 353, 430).
- `MainActivity.java` (9), `proxy/LocalHttpProxyServer.java` (7, в осн. close-cleanup), `ui/QuickButtonsPanel.java` (6), `manager/AutoMineManager.java` (6), `postfilter/HpmpJs.java` (6), `postfilter/MapJs.java` (18 — проверить, не генератор ли).
- `printStackTrace()` → заменить на `AppLog`: `ContactsActivity` (2), `LoginActivity` (1), `MainActivity` (1), `model/UserConfig.java` (2), `postfilter/ChListJs.java` (2), `utils/ConverterUtils.java` (2), `lez/LezSpellCollection.java` (1).

**Инвариант (AGENTS2 п.12):** только `AppLog.*` (не `android.util.Log`), для критичных цепочек — `chain`-параметр (напр. `PROXY_TRACE`, `FIGHT_TRACE`).

**План:**
- [x] `WebViewRequestInterceptor`: во все молчаливые catch добавить `AppLog.w/d` с `chain` (файловое логирование). Не менять управляющую логику — только диагностика.
- [x] `FightAuto`/`FightViewModel`: логировать проглоченные исключения (chain=`FightAuto`/`FightViewModel`, префикс `BG_TRACE`).
- [x] Заменить все `printStackTrace()` (11) на `AppLog.w/e(tag, msg, e)`.
- [x] Остальные пустые catch: пройтись, добавить хотя бы `AppLog.d` (где это не «ожидаемая» ветка close/cleanup — там короткий комментарий-обоснование).
- [x] Grep-проверка: `printStackTrace` → 0 в изменённых; `android.util.Log` не добавлен.

### Результат D4 (2026-07-26)

**Важное уточнение по подсчёту:** исходная оценка «62 пустых catch» включала ложные срабатывания. Реальная картина после ручной верификации:
- **Ложные (JS-строки, а не Java-контроль):** `MapJs` (18), `MineJs` (10), `HpmpJs` (6) — это `catch` внутри генерируемого JS-текста (`+ "} catch (_an_e0) {}\n"`). Не трогали.
- **Ложные (catch с телом):** `ParseUtils` (4), `UserConfig` (1172/1178/1246), `FishAjaxPhp:3661`, `Navigator:1661`, `LezFight:216` — там есть `return defaultValue` / присваивание fallback.
- **Инфраструктура (исключение AGENTS п.12):** `LogcatFileRecorder` (5) — мета-логирование самого логгера, не трогали.

**Фактически изменено (26 Java-файлов):**
- `WebViewRequestInterceptor` — 12 точек: 2 API-guard (`isMainFrameRequest`, `isNeverlandsUrlString`), 2 диагностических callback (`notifyChatPollMetaToActivity` chain=`chat_poll`, `notifySessionErrorToActivity` chain=`session_relogin`), 8 веток синхронизации серверного времени (`updateServerTimeFromChat/But/JsDate/Parts`).
- `FightAuto` — 3 (skin skill parse, `uiForegroundInteractive` guard, `extractBattleXpFromHtml`), chain=`FightAuto`.
- `FightViewModel` — 3 (`uiForeground` flags, `updateLastBoiFromLogs`, `isActiveAlchemyCaptchaDialog`), chain=`FightViewModel` + `BG_TRACE`.
- `LocalHttpProxyServer` — 7: `AppLog.d` chain=`LOG_CHAIN` для недоставленного 502-fallback и полного провала декодирования; комментарии-обоснования для 5 close/cleanup-веток.
- `LezFight` — 3 (2 комментария на parse-элементы, `AppLog.d` на `updateLastBoiDamageIfNeeded`).
- `MainPhp`, `MainPhpNavigationHandler` — 2 (vcode-extraction, важно для VCode-цепочки).
- `AutoCutManager` — 2 (chain=`TRACE_CHAIN`), `Navigator` — 1, `CookiesManager` — 1, `TabManager` — 3, `ButPhp` — 1, `ApiRepository` — 1, `LicenseRequestDialog` — 1, `ExtMap` — 3.
- `MainActivity` — 10 (2 диагностических лога: compass regnum, host parse; 8 комментариев: close/cleanup потоков, parse-fallback'и).
- `QuickButtonsPanel` — 6 комментариев (парсинг пользовательского ввода в диалогах настроек).
- **printStackTrace → AppLog (11):** `ContactsActivity` (2, включая восстановление interrupt-флага), `LoginActivity`, `MainActivity`, `LezSpellCollection`, `UserConfig` (2), `ChListJs` (2 — один был дублем существующего `AppLog.e`, удалён), `ConverterUtils` (2).
- Добавлен импорт `AppLog` в `model/LezSpellCollection.java`.

**Побочно исправлено (из D3):** `ContactsActivity:657` — `InterruptedException` теперь восстанавливает флаг через `Thread.currentThread().interrupt()`.

**Инфраструктурная проблема сборки (не код):** первая сборка падала на этапе конфигурации с `Could not read workspace metadata ... transforms-4\*\metadata.bin`. Причина: 38 из 146 папок кэша трансформ Gradle остались без `metadata.bin` после прерванной сборки. Исправлено хирургически — удалены только эти 38 папок, 108 валидных сохранены.

**Проверки после фикса:**
- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL**.
- [x] `android.util.Log` вне `AppLog`/`FileLogger`/`LogcatFileRecorder` — **0**.
- [x] `printStackTrace()` по всему `app2` — **0** (было 11).
- [x] `AppVars.VCode` вне `SessionManager` — **2**, оба существующие комментарии-напоминания (`KaznaHtmlInjectionRenderer:115`, `MainPhp:1817`), новых нет.
- [x] Mojibake в 26 изменённых файлах (паттерны `РЎР`/`РџС`/`СЂР`/`Ð`/`Ñ` + U+FFFD) — **0**; BOM — **0**.
- [x] Новые legacy-префиксы (`ABCLIENT`/`abclient`/`ab_*`/`AbcCell`) в добавленных строках — **0**.
- [x] Метки клиента в `User-Agent` в добавленных строках — **0**.
- [-] `git diff --check` по-прежнему шумит существующей ошибкой `.gitattributes:7` и CRLF-предупреждениями (не связано с правками).
- [ ] Live-проверка: в новых `files/Logs/Critical/` должны появляться диагностические записи вместо «тишины» при сбоях парсинга времени, host-parse и captcha/fight-guard'ов; поведение автобоя/прокси не должно измениться.

---

## D5. Устранение дублирования (Средне, AGENTS2 п.8)

**Находки:**
- **`InventoryParser` в двух пакетах**: `utils/InventoryParser.java` (248 стр.) и `postfilter/InventoryParser.java` (771 стр.) — парсят один HTML. `postfilter/FishAjaxPhp.java` использует **оба сразу** (import utils — стр. 34; вызовы utils — 2274, 2284; вызовы postfilter — 3197-3232, 3501-3505). Риск расхождения логики.
- **`containsIgnoreCase` — 6 копий:** `postfilter/InventoryParser.java:149`, `postfilter/MainPhp.java:2455`, `postfilter/TreasureDig.java:600`, `model/ParsedDressed.java:590`, `parser/KaznaParser.java:316`, `proxy/ProxyRequestQueue.java:274`.
- `getMainActivityOrNull` — 2 версии с разной строгостью (`WebAppInterface.java:86`, `FishAjaxPhp.java:1966`).

**План:**
- [x] Проанализировать оба `InventoryParser`: определить, какой канонический; составить карту различий API (`InventoryItem`/`findSpecificRod` vs `WearInvEntry`/`getWearInvList`).
- [x] Слить в один класс (расширен `postfilter/InventoryParser` как более полный), перевести `FishAjaxPhp` на единый источник; удалить дубликат после проверки сборки.
- [x] `containsIgnoreCase` → единый `HelperStrings.containsIgnoreCase(...)`; локальные копии превращены в делегаты.
- [x] Унифицировать `getMainActivityOrNull` (строгая версия с `isFinishing`+`isDestroyed`); обе копии → делегаты.
- [x] Grep: `class InventoryParser` → 1; реализация `containsIgnoreCase` → 1.

### Результат D5 (2026-07-26)

**1. Дубль `InventoryParser` устранён.**
- Сравнение показало, что `postfilter/InventoryParser.getWearInvList(...)` **строго превосходит** `utils/InventoryParser.parseInventory(...)`:
  * устойчивое извлечение имени через `<font class=nickname><b>` вместо хрупкой эвристики «текст перед кнопкой + strip тегов + fallback на `title=`»;
  * **fallback-извлечение wear-ссылки**, если точный паттерн кнопки «Надеть» не совпал (в utils-версии в этом случае возвращался `null`);
  * case-insensitive `regionMatches(true, ...)` вместо `startsWith`;
  * выделенный `findInventoryEntryEnd(...)` вместо двух захардкоженных концевых паттернов;
  * дополнительно извлекается `uid`.
- Установлено, что у `utils/InventoryParser` был **единственный потребитель** — `FishAjaxPhp` (3 строки: импорт, `parseInventory`, `findSpecificRod`), при этом **в том же файле** (строки ~3197-3232) уже работал второй путь выбора удочки через `postfilter`-версию. То есть дублировался не только парсер, но и логика подбора удочки.
- `FishAjaxPhp.checkAndWearRodIfNeeded(...)` переведён на `InventoryParser.getWearInvList(...)`/`WearInvEntry`. Поле `durability` (отсутствует в `WearInvEntry`) использовалось только в тексте лога — заменено на `uid`.
- Вместо переноса `findSpecificRod/findAnyRod/findItemByName` добавлен один приватный `findRodInInventory(...)`, который **переиспользует существующий decision point** `matchesFishingHandSetting(...)` (уже обрабатывает «Любая удочка» и точное имя). Сохранена прежняя семантика: пустая/`null` настройка = «Любая удочка»; предметы без wear-ссылки пропускаются.
- Файл `app2/src/main/java/ru/neverlands/anclient/utils/InventoryParser.java` удалён (`git rm`).

**2. `containsIgnoreCase` — единый источник.**
- Проверено, что все 6 копий были семантически идентичны (null-safe, `Locale.ROOT`) — расхождения ещё не произошло, но риск сохранялся.
- Каноническая реализация добавлена в `HelperStrings.containsIgnoreCase(String, String)`.
- Все 6 прежних определений превращены в **тонкие делегаты**: `InventoryParser:150` (public, ~40 внешних вызовов), `MainPhp:2457`, `TreasureDig:601`, `ParsedDressed:591`, `KaznaParser:317`, `ProxyRequestQueue:275`.
- Выбран вариант «делегаты» вместо массовой замены ~60 мест вызова: логика становится единственной, а поведение call-site'ов гарантированно не меняется.

**3. `getMainActivityOrNull` — единый безопасный аксессор.**
- Канонический метод добавлен в `AppVars` (класс-владелец `WeakReference<MainActivity> mainActivity`) со **строгой** семантикой: `isFinishing()` + `isDestroyed()` + защита от исключений с `AppLog.d`.
- `WebAppInterface:87` и `FishAjaxPhp:1965` → делегаты.
- Побочный эффект (намеренный, безопасный): путь `FishAjaxPhp` теперь также отсекает уничтоженную Activity — раньше проверялся только `isFinishing()`.

**Проверки после фикса:**
- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL** (дважды: после слияния парсера и после унификации хелперов).
- [x] Классов `InventoryParser` в `app2` — **1** (был 2).
- [x] Именованных реализаций `containsIgnoreCase` — **1** (`HelperStrings`), остальные 6 — делегаты (подтверждено скриптом).
- [x] Определений `getMainActivityOrNull` — 3: 1 канон (`AppVars`) + 2 делегата.
- [x] `android.util.Log` вне инфраструктуры — **0**; `printStackTrace` — **0**.
- [x] Mojibake — **0**; BOM — **0** (36 изменённых файлов).
- [ ] Live-проверка авто-рыбалки: при пустой руке 1 ожидается `FISH_GEAR_TRY_WEAR_HAND1` → `FISH_WEAR_ROD: одеваем '<имя>' (uid=...)`; при отсутствии удочки — `FISH_NO_ROD_FOUND` и остановка. Ранее возможный сбой парсинга имени (utils-эвристика) должен исчезнуть.
- [-] **Опциональный follow-up (не входит в D5):** остаются ~19 inline-выражений `toLowerCase(Locale.ROOT).contains(...)` (в `ChatFilter`, `FishAjaxPhp`, `MainActivity`, `InvEntry` и др.). Это не дублирующиеся методы, а разовые выражения; миграция на `HelperStrings` возможна, но даёт мало пользы при заметном churn.

---

## D6. Архитектурный рефакторинг god-классов (Низко-средне, поэтапно)

**Находки:**
- `MainActivity.java` — ~6220 строк. Кандидаты на вынос (в контроллеры/Handler'ы, AGENTS2 п.7):
  - Captcha-подсистема ~2254-3767 (**~1500 строк**) → `FightCaptchaController`.
  - Auto-turn / боевой probe (600-2192, 4009-4138) → `AutoTurnProbeController`.
  - Чат-цикл + room users polling (554-599, 4949-5180) → `ChatRefreshController`.
  - Cookie-синхронизация (4167-4343) → `CookieSyncHelper`.
  - WebView setup/destroy + JS-инъекции (4139-4667, 5814-6211) → `WebViewConfigurator` (совместить с D2).
  - Logout flow (5655-5771) → `LogoutFlowHandler`.
- `QuickButtonsPanel.java` — ~3451 строк, ~25 диалогов настроек → вынести в отдельные `DialogFragment`/фабрики по функциям.
- Связность: `AppVars.mainActivity` — 100 обращений в 21 файле; `AppVars` — 195 мутабельных `public static` (god-state, `lastMainPhpResponse`/`lastChatMsgResponse` держат полные HTML).

**Инвариант:** рефакторинг чисто структурный — поведение и тайминги боёв/автоходов/капчи не менять; каждый вынос отдельным шагом с проверкой сборки и логов.

**План (каждый пункт — отдельная согласованная итерация):**
- [x] Начать с наименее рискованного: `CookieSyncHelper` (изолирован) — **выполнено**.
- [x] `WebViewConfigurator` — объединён с D2 — **выполнено**.
- [ ] `LogoutFlowHandler` — следующий кандидат низкого риска.
- [ ] `FightCaptchaController` — крупнейший выигрыш, но высокий риск (боевая captcha) → только после live-проверки D1-D5.
- [ ] `QuickButtonsPanel`: вынести диалоги настроек группами (AutoFish, AutoCut, Boss, Timers…).
- [ ] Оценка `AppVars`: пометить кандидатов на инкапсуляцию (крупные буферы HTML — освобождать по завершению использования).
- [ ] Каждый вынос: сборка + сверка отсутствия регресса по критичным логам.

### Результат D6, шаг 1 (2026-07-26)

**Выполнено два выноса из god-класса `MainActivity`:**

1. **`WebViewConfigurator`** (сделан в рамках D2) — настройки WebView + cookie-политика, ~20 строк из `MainActivity` и ~17 из `TabManager`, плюс устранено расхождение профилей.

2. **`CookieSyncHelper`** (`webview/CookieSyncHelper.java`) — синхронизация игровых cookie между хостами:
   - перенесены 5 методов: `applyAuthCookiesToWebView`, `syncSessionCookiesAcrossHosts`, `mirrorCookieHeaderToHost`, `summarizeCookieHeaderNames`, `isCookieAttributeName`;
   - блок выбран первым намеренно: он **полностью изолирован** — зависел только от `CookieManager` и `GameServerUrls`, не обращался к состоянию Activity, не участвует в боевых/captcha-таймингах;
   - логика перенесена **1:1**, формат логов `AUTH_COOKIE_SYNC` сохранён (это структурный рефакторинг, не изменение поведения);
   - оба места вызова (`lastCookies_apply` при инициализации WebView и `session_relogin` при авто-перелогине) переведены на helper.

**Попутно удалён мёртвый код:** приватный `MainActivity.hasSessionCookieTokens(...)` нигде не вызывался (упоминался только в Javadoc). Обнаружено также, что его копия в `WebViewRequestInterceptor:920` тоже не используется, причём **списки токенов у копий разошлись** (в версии `MainActivity` был лишний `watermark=`). Копия в interceptor намеренно не тронута — устранение второго мёртвого метода вынесено в отдельную задачу, чтобы не смешивать с текущим рефакторингом.

**Эффект:** `MainActivity` уменьшился с **6630 до 6053 строк** (−577, около −9%).

**Проверки:**
- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL**.
- [x] Оба call-site переведены, старые приватные методы удалены полностью.
- [x] `android.util.Log` — 0; `printStackTrace` — 0; mojibake/BOM (42 файла) — 0.
- [ ] Live-проверка: после входа и после авто-перелогина в логах ожидаются прежние записи `AUTH_COOKIE_SYNC: applied ...` и `AUTH_COOKIE_SYNC[after_...]`, сессия в чат/room-фреймах не теряется.

### Результат D6, шаг 2 (2026-07-26)

**Выполнено два выноса:**

1. **`LogoutFlowHandler`** (`handlers/LogoutFlowHandler.java`, 167 строк) — весь сценарий выхода:
   * `startLogout(activity, onBeforeNavigate)` — серверный `GET /exit.php` в фоне + финализация в UI-потоке;
   * `forceLogoutToLogin(...)` — локальный выход без сетевого запроса (для `SESSION_RELOGIN_FALLBACK`, где сессия уже мертва);
   * `clearLocalSession(context)` — очистка runtime-креденшелов, обоих cookie-хранилищ и лицензии; вынесена публично для переиспользования.
   * В `MainActivity` осталась только координация (проверка `isExiting` + колбэк). Константы `LOGOUT_*` переехали вместе с логикой.

2. **`FightCaptchaUtils`** (`utils/FightCaptchaUtils.java`, 265 строк) — **только чистые** функции боевой капчи:
   * `downloadCaptchaImageBytes(...)`, `decodeUsableCaptchaBitmap(...)`;
   * `normalizeUrlForCompare(...)`, `isSameUrl(...)`, `appendOrReplaceCaptchaCode(...)`, `isAlchemyCaptchaFinishUrl(...)`;
   * `shouldRetryAntiCaptchaFailure(...)`, `isAntiCaptchaSolutionValid(...)`;
   * константа `CAPTCHA_IMAGE_MIN_USABLE_BYTES` стала единым источником.
   * В `MainActivity` методы оставлены как однострочные делегаты — публичный контракт класса не менялся, риск нулевой.

**Попутно устранён дубль:** приватные `normalizeCaptchaUrlForCompare` и `normalizeFightFinishUrlForCompare` в `MainActivity` имели **идентичные тела** — объединены в `FightCaptchaUtils.normalizeUrlForCompare(...)`.

**Осознанно НЕ тронут stateful-контур капчи:** диалог, авто-обновление картинки, retry Anti-Captcha, системные уведомления и ~25 полей состояния (`activeFightCaptcha*`, `antiCaptcha*`). Это критичная боевая цепочка (AGENTS п.8/п.9), её вынос требует отдельной итерации с live-проверкой на реальных боях.

**Оценка чат-контура:** `ChatRefreshController` из плана оказался существенно более связанным, чем предполагалось — 13 полей состояния, ссылки на `chatRefrWebView`, публичные методы вызываются из `WebAppInterface`, `AutoModeForegroundService` и `RoomManager`. Вынос без подготовки дал бы высокий риск; перенесён в отложенные.

**Динамика `MainActivity`:** 6630 → 6053 (шаг 1) → **5836** строк (суммарно −794, около −12%).

**Проверки:**
- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL** (после каждого выноса отдельно).
- [x] `android.util.Log` вне инфраструктуры — 0; `printStackTrace` — 0; mojibake — 0 (236 файлов).
- [ ] Live-проверка: выход из аккаунта через меню; принудительный выход при неудачном авто-перелогине; показ и отправка боевой капчи.

**Что осталось в D6 (требует отдельного согласования из-за риска):**
| Блок | Объём | Риск |
|---|---|---|
| `LogoutFlowHandler` | ~120 строк | низкий |
| `ChatRefreshController` (чат-цикл + room polling) | ~270 строк | средний |
| `AutoTurnProbeController` (auto-turn/probe) | ~700 строк | высокий (боевая цепочка) |
| `FightCaptchaController` | ~1500 строк | **высокий** (боевая captcha, AGENTS п.8/п.9) |
| `QuickButtonsPanel` → DialogFragment'ы | ~25 диалогов | средний |
| Инкапсуляция `AppVars` (195 static-полей) | архитектурный | высокий |

---

## Проверка перед сдачей каждого направления (AGENTS2 п.5, п.12)

- [ ] Сборка: `./gradlew2.bat --no-daemon :app2:assembleDebug` — успешно.
- [ ] `grep android.util.Log` в изменённых `.java` (вне AppLog/FileLogger/LogcatFileRecorder) — 0.
- [ ] `grep AppVars.VCode` — новых вхождений нет (только существующие комментарии).
- [ ] Mojibake-проверка изменённых файлов (`РЎ`/`Рџ`/`Ð`/`Ñ`) — 0; UTF-8 без BOM.
- [ ] Нет новых legacy `ab_*`/`ABCLIENT` runtime-префиксов (для app2 — только `an_*`/`ANC`).
- [ ] User-Agent не содержит меток клиента (браузерный).
- [ ] Обновить статус соответствующего направления D1-D6 в этом файле.

---

# Регрессия: сессия не держится / куки слетают (2026-07-26)

## Симптом

После рефакторинга D1-D6 пользователь сообщил: сломалась авторизация — сессия не держится, куки слетают.

## Что проверено в моих изменениях (аудит диффа)

Прогнан срез всех изменений кода без комментариев и логов (592 строки диффа). Проверено построчно:

| Компонент | Вердикт |
| --- | --- |
| `CookieSyncHelper` (вынос из `MainActivity`) | **1:1 перенос**. Автоматическое сравнение statement-ов с версией из HEAD: единственное расхождение — `java.util.List` → `List` (импорт) и удалённый мёртвый `hasSessionCookieTokens`. Логика `setCookie`/`flush`/зеркалирования идентична |
| `WebViewConfigurator` | Все `webSettings.*` сохранены; `setAcceptCookie(true)` и `setAcceptThirdPartyCookies(webView, true)` присутствуют (L94-95) |
| `CookiesManager` | В моём диффе — только лог в catch, логика не тронута |
| `WebViewRequestInterceptor` | Только логи в catch (12 точек) |
| `AppVars` | Только добавленный `getMainActivityOrNull()` |
| `UserConfig.save()` / `onLoginSuccess` | Не изменялись; `AppVars.lastCookies` ставится как раньше |

Вывод: в перенесённом cookie-контуре регрессии нет. Реальных изменений поведения, способных влиять на сессию, оказалось **два** (см. ниже), и оба возвращены к исходному состоянию.

## Найденная корневая причина: расхождение с эталоном C#

Изучены `ANClient/ANProxy/CookiesManager.cs`, `ANClient/ANProxy/CookiePack.cs`, `ANClient/CookieAwareWebClient.cs` и `Instruction/auth/*`.

**Как держит сессию ПК-версия:**
- Локальный прокси — единственный источник истины по cookie.
- `CookiesManager.Assign(host, data)` берёт из `Set-Cookie` строго часть **до первой `;`**:
  ```csharp
  var posemi = data.IndexOf(';', poseq);
  var svalue = (posemi == -1) ? data.Substring(poseq + 1)
                              : data.Substring(poseq + 1, posemi - poseq - 1);
  ```
- `CookiePack` хранит **только `Name` и `Value`**, отдаёт `name=value; name2=value2`.
- Итог: ПК-клиент **физически не переносит** атрибуты cookie. Сервер не может ни протухнуть куку, ни пометить её `Secure`. Она живёт в памяти всю сессию.
- Один и тот же pack зеркалится на все хосты из `CookieHosts()`; `Obtain()` дополнительно мапит `forum.neverlands.ru` → `www.neverlands.ru`.

**Что делал Java-порт:** `CookiesManager.toHostOnlyCookieHeader()` отбрасывал только `Domain`, а `Expires`, `Max-Age`, `Secure`, `SameSite` **передавал в Android CookieManager**, который честно их исполняет:
- `Expires`/`Max-Age` в прошлом или короткий срок → cookie удаляется, сессия слетает во время игры;
- `Secure` → cookie перестаёт уходить по `http://`, а игра работает по cleartext HTTP;
- `SameSite` → cookie может не уйти при переходах между фреймами/хостами.

**Важно:** это **не регрессия текущего рефакторинга** — код `toHostOnlyCookieHeader` существовал до него (в моём диффе этого файла был только лог в catch). Но именно он расходится с эталоном и точно объясняет симптом.

## Внесённые исправления

1. **`CookiesManager.toHostOnlyCookieHeader()` приведён к поведению C#**: сохраняется только `name=value` + `Path=/`, все остальные атрибуты отбрасываются. Добавлен Javadoc со ссылкой на исходник ПК-версии и объяснением каждого риска.

2. **`WebViewConfigurator`: возвращены `setAllowFileAccessFromFileURLs(true)` и `setAllowUniversalAccessFromFileURLs(true)`** — откат hardening из D2 до подтверждения логами. Приоритет рабочего входа над гипотетической защитой.

3. **`WebAppInterface.isAllowedBridgeUrl(...)` переведён в неблокирующий режим**: allowlist больше не отменяет вызовы `loadFrame`/`redirectToUrl`/`openInNewTab`/`chatSubmit`, а только пишет `BRIDGE_URL_FOREIGN` в лог. Блокировка навигации внешне неотличима от «слетевшей сессии», поэтому она снята до диагностики.

После этих правок поведение WebView и моста **идентично состоянию до рефакторинга**, а cookie-контур стал ближе к ПК-эталону, чем был.

## Проверки

- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL**.
- [x] Флаги file-URL присутствуют (L77-78 конфигуратора).
- [x] В `isAllowedBridgeUrl` нет ни одного `return false` (5 × `return true`).
- [x] `toHostOnlyCookieHeader` обрезает по первой `;`.
- [ ] **Live-проверка (нужны логи с устройства).**

## Что смотреть в логах после установки

| Маркер | Значение |
| --- | --- |
| `assign: mirrored host-safe cookie name=...` | какие куки реально сохраняются (должны быть `PHPSESSID`, `NeverPuid`, `NeverHash`, `NeverCode`, `watermark`) |
| `AUTH_COOKIE_SYNC: applied lastCookies_apply names=[...]` | перенос куки из OkHttp в WebView после логина |
| `AUTH_COOKIE_SYNC[after_...]` | состояние по каждому хосту; `empty` для игрового хоста = проблема |
| `BRIDGE_URL_FOREIGN` | если появится — allowlist действительно резал легитимные переходы (тогда его нельзя включать обратно) |
| `SESSION_RELOGIN_DETECTED` | сервер сбросил сессию |

## Открытый вопрос

- [ ] Если после фикса сессия **всё ещё** слетает — следующий шаг: сверить `GameServerUrls.cookieUrls()` с `GameServerSelector.CookieHosts()` из C# и проверить, что зеркалирование покрывает тот же набор хостов (включая `www`).

---

# Анализ логов 14:05 и устранение залпа запросов при входе (2026-07-26)

## Что показали логи `logs/Critical/20260726_14_20_*`

Сессия рвётся через **~6 секунд после успешного логина**:

| Время | Событие |
| --- | --- |
| 14:05:02.060 | `AuthManager: Full Authorization SUCCESS`, собрано 22 cookie |
| 14:05:02.447 | `AUTH_COOKIE_SYNC[after_lastCookies_apply]: http://136.243.18.79/=count=11` — перенос в WebView отработал корректно |
| 14:05:03-08 | Залп параллельных запросов, очередь прокси растёт |
| 14:05:08.058 | Ответ на `main.php?...ab_reload_probe=1` = `<LINK href="./css/error.css">` → страница «Сеанс работы прерван» |
| 14:05:09.672 | `Failed to refresh contact by ID 2260271: Server error: 535` (anti-rate-limit) |

**Cookie-контур исправен** — 11 cookie (`PHPSESSID`, `NeverPuid`, `NeverCode`, `NeverHash`, `watermark`, …) корректно перенесены и зеркалированы. `SESSION_COOKIE_APPLIED ... bytes=0` появляется только в 14:05:08.864, то есть **после** смерти сессии — это следствие, а не причина.

## Корневая причина: залп запросов при входе

За ~1 секунду после логина в игру уходило порядка 13 запросов одновременно:

```
main.php                       (главный фрейм)
main.php?...ab_reload_probe=1  (probe)
main.php?ab_bg_probe=1         (фоновый probe)
ch.php?show=1  x2              <- дубликат, интервал 19 мс
ch.php?lo=1    x2
info.cgi       x7              (фоновое обновление контактов)
wars.cgi
```

Очередь локального прокси захлёбывалась:
```
PROXY_QUEUE: queued game action, waitMs=6874
PROXY_QUEUE: queued game action, waitMs=7702
PROXY_QUEUE: queued game action, waitMs=7877
```
Ответ на probe шёл 5.4 секунды (`X-Android-Sent-Millis` 14:05:02.599 → ответ 14:05:08.042). Сервер интерпретирует пачку одновременных обращений как **«попытка войти в другом окне»** и обрывает сессию.

## Найденные конкретные источники

1. **`LoginActivity.updateContactsRecursive(...)` — отдельная локальная копия цепочки обновления контактов** (не та, что в `ContactsManager`). Стартовала **сразу** после логина с интервалом **500 мс** — вдвое агрессивнее, чем 1200 мс в `ContactsManager`. Именно она давала `Server error: 535`.

2. **Дубликат chat-poll.** Опрос чата инициируется из двух независимых источников: периодический `chatRefreshRunnable` в `MainActivity` и bootstrap `AutoModeForegroundService.uiTick -> requestChatRefreshNow()`. Поле `lastChatRefreshAtMs` существовало, но **как guard не использовалось**, поэтому оба源 стреляли с разницей 19 мс.

## Внесённые исправления

1. **Отложенный старт обновления контактов** (`LoginActivity`): добавлена константа `LOGIN_CONTACT_REFRESH_START_DELAY_MS = 8_000L`. Цепочка запускается через main-looper Handler (переживает `finish()` активности), лог: `Background contact refresh scheduled: queueSize=..., startDelayMs=..., stepDelayMs=...` → затем `Starting background contact refresh after login settle window.`

2. **Интервал между `info.cgi` приведён к 1200 мс** (`LOGIN_CONTACT_REFRESH_STEP_DELAY_MS`), как в `ContactsManager` — устраняет причину `535/536`.

3. **Дедупликация chat-poll** в существующей точке `MainActivity.requestChatRefresh(...)`: добавлена `CHAT_REFRESH_MIN_INTERVAL_MS = 900L`, дубликат отбрасывается с логом `requestChatRefresh: skip duplicate poll, deltaMs=..., minIntervalMs=...`. Новый контур не создавался — задействовано уже существовавшее поле `lastChatRefreshAtMs`.

4. **Исправлен вводящий в заблуждение лог** `WebViewConfigurator`: строка `universalFileAccess=disabled` была **захардкожена** и продолжала показывать старое значение после возврата флагов, из-за чего актуальная сборка выглядела как устаревшая. Теперь значения читаются из `WebSettings` фактически.

## Проверки

- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL**.
- [x] Хардкод `}, 500);` в цепочке контактов удалён.
- [x] Guard подключён (`L4835`), константы на месте.
- [ ] Live-проверка на устройстве.

## Что ожидать в новых логах

| Маркер | Ожидание |
| --- | --- |
| `Background contact refresh scheduled: ... startDelayMs=8000` | контакты больше не стартуют в момент входа |
| `Starting background contact refresh after login settle window.` | появляется через ~8 с после логина |
| `requestChatRefresh: skip duplicate poll` | дубликат chat-poll отсекается |
| `PROXY_QUEUE ... waitMs=` | значения должны упасть с 6000-7900 до сотен мс |
| `Server error: 535` | должен исчезнуть |
| `applyGameSettings: ... universalFileAccess=true` | подтверждает, что установлена свежая сборка |

## Замечание на будущее (не входит в текущий фикс)

В запросах на сервер уходят legacy-префиксы `ab_reload_probe=1` и `ab_bg_probe=1`. Это нарушает AGENTS2 п.2 (для `app2` должно быть `an_*`) и является опознаваемым признаком неофициального клиента в трафике. Требует отдельной задачи (переименование + проверка всех мест чтения параметра).

---

# КОРНЕВАЯ ПРИЧИНА НАЙДЕНА: рассинхрон User-Agent внутри одной сессии (2026-07-26)

## Как нашли

Пользователь сообщил, что фикс залпа не помог. Проверка свежих логов `logs/Critical/20260726_14_30_*` / `14_40_*` показала, что **на устройстве стояла предыдущая сборка** (присутствовали старые строки `Starting background contact refresh after successful login` / `Background contact refresh queue size=7`, новых маркеров — 0). Поэтому фикс залпа ещё не проверялся.

Параллельно, по просьбе пользователя, изучена авторизация в C#-эталоне `ANClient` — и это дало ответ.

## Ключевое отличие от ПК-версии

**C# (`ANClient/PostFilter/IndexCgi.cs`):** логин выполняет **сам браузер**. Пост-фильтр перехватывает HTML страницы входа и подменяет его автосабмит-формой:
```csharp
<form action="./game.php" method=POST name=ff>
  <input name=player_nick type=hidden value="...">
  <input name=player_password type=hidden value="...">
</form>
<script>document.ff.submit();</script>
```
Вся авторизация происходит одной цепочкой навигации внутри одного HTTP-клиента, поэтому User-Agent на всех запросах сессии **один и тот же**.

**Android:** `AuthManager` логинится через **OkHttp вне WebView**, затем cookie переносятся в WebView, и WebView начинает работать сам. Получаются **два разных HTTP-клиента на одной сессии**.

## Подтверждение по логам

| Кто | User-Agent |
| --- | --- |
| `AuthManager` (OkHttp), получает `PHPSESSID` | `Mozilla/5.0 (Windows NT 10.0; Win64; x64) ... Chrome/124.0.0.0 Safari/537.36` |
| WebView, все последующие запросы | `Mozilla/5.0 (Linux; Android 14; 22101316G Build/...; wv) ... Mobile Safari/537.36` |

Сервер выдаёт сессию клиенту «Windows Chrome», а через секунду получает запросы с тем же `PHPSESSID` от «Android WebView» → отвечает страницей «Сеанс работы прерван», причина **«Попытка войти в другом окне браузера (возможно взлом)»** — дословно то, что видит пользователь.

Проверка кода подтвердила системность:
- `AppVars.BROWSER_USER_AGENT` (Windows Chrome) используют **все** нативные пути: `AuthManager`, `NeverApi`, `ApiRepository`, `ProfessionRatingRepository`, `AntiCaptchaManager`, прямые запросы `MainActivity`, `WebViewRequestInterceptor`.
- `setUserAgentString` для игровых WebView **не вызывался нигде** (единственное вхождение было в `ForpostInfoActivity`, и там уже стоял Windows-UA).
- В `WebViewRequestInterceptor` подстановка UA срабатывала только `if (reqUserAgent == null || reqUserAgent.isEmpty())` — то есть никогда, потому что WebView всегда присылает свой.

Дополнительно родной UA WebView содержит маркер `wv` и модель устройства `22101316G` — это прямое нарушение AGENTS п.4 (анти-детект), сервер видит неофициальный клиент.

## Исправление

1. **`WebViewConfigurator.applyGameSettings(...)`** — добавлен `webSettings.setUserAgentString(AppVars.BROWSER_USER_AGENT)`. Единая точка: покрывает все игровые WebView `MainActivity` (main/chat/chatUsers/chatButtons/ch_refr) и вторичные вкладки `TabManager`.

2. **`PinfoActivity.setupWebView()`** — тот же UA. Этот экран грузит игровой `pinfo.cgi` под теми же cookie и был вторым источником рассинхрона.

3. Лог конфигуратора расширен полем `userAgent=`, чтобы установленную сборку можно было проверить одной строкой.

Не тронуты намеренно: `Navigator` (мини-карта работает с `file:///android_asset/`, на сервер не ходит) и `ForpostInfoActivity` (уже использовал Windows-UA).

## Проверки

- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL**, APK `anclient_v1.1.6.apk` (14:47).
- [x] `setUserAgentString` теперь во всех WebView, ходящих на игровой сервер.
- [ ] Live-проверка.

## Как убедиться, что установлена нужная сборка

В логе `webviewconfigurator` должна появиться строка вида:
```
applyGameSettings: profile=MAIN_GAME, allowFileAccess=true, universalFileAccess=true, mixedContent=0, userAgent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...
```
Если там по-прежнему `universalFileAccess=disabled, mixedContent=ALWAYS_ALLOW` — установлена старая сборка.

В `CHAT_REQ_HEADERS` поле `ua=` должно стать `Mozilla/5.0 (Windows NT 10.0; ...)` вместо `Mozilla/5.0 (Linux; Android ...)`.

---

# Анти-детект: полный аудит следов клиента в трафике (2026-07-26)

**Требование пользователя:** сервер должен видеть браузер, а не клиент — администрация проекта блокирует за неофициальные клиенты. Это ужесточает AGENTS п.4: недостаточно «браузерного User-Agent», нужно отсутствие **любых** признаков приложения.

## Найденные следы и статус

| # | След | Как выглядит для сервера | Статус |
| --- | --- | --- | --- |
| 1 | **Рассинхрон User-Agent** | Логин с `Windows Chrome`, дальнейшие запросы с `Linux; Android 14; <модель>; wv` на той же сессии | **Исправлено** |
| 2 | **Маркер `wv` и модель устройства** в UA WebView | Прямое указание на Android WebView, а не браузер | **Исправлено** |
| 3 | **Кастомные query-параметры** `ab_*` / `an_*` | `main.php?...&ab_reload_probe=1` — такого не шлёт ни один браузер, ищется обычным grep по логам сервера | **Исправлено** |
| 4 | **`X-Requested-With`** | Android WebView может подставить **имя пакета** `ru.neverlands.anclient` | **Исправлено** (вырезается) |
| 5 | **`Sec-CH-UA*` client hints** | Сообщали бы «Android WebView, Chrome 151» вопреки desktop-UA — рассинхрон детектируется тривиально | **Исправлено** (переписываются согласованно) |
| 6 | **`X-Android-*`** служебные заголовки | Признак Android-стека | **Исправлено** (вырезаются) |

## Что именно сделано

**Единая точка — локальный прокси** (`LocalHttpProxyServer.writeRequest`). Через него идёт весь игровой трафик: и запросы WebView, и нативные. Это повторяет архитектуру ПК-версии, где прокси — единственная граница с сервером.

1. **`stripClientMarkersFromTarget(...)`** — вырезает из строки запроса все параметры с префиксом `ab_`/`an_`.
   *Почему безопасно:* маркеры нужны только внутренней логике, и она читает их из собственного URL (`Filter.process(context, urlString, ...)`) **до** отправки. Вырезание касается лишь того, что уходит на сервер. Игровые параметры (`get_id`, `act`, `go`, `im`, `wca`, `vcode`, `code`, `fexp`) префиксов не имеют.
   *Почему в прокси, а не по месту:* маркеров ~25 в разных модулях (авто-срез, казна, шахта, навигация, таймеры). Правка в одной точке не трогает авто-функции и не создаёт риск регрессий.

2. **`isClientIdentityHeader(...)` + `appendBrowserIdentityHeaders(...)`** — идентифицирующие заголовки не пробрасываются, вместо них подставляется согласованный набор десктопного Chrome:
   ```
   User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) ... Chrome/124.0.0.0 Safari/537.36
   sec-ch-ua: "Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"
   sec-ch-ua-mobile: ?0
   sec-ch-ua-platform: "Windows"
   ```
   Версия в client hints намеренно совпадает с версией в `AppVars.BROWSER_USER_AGENT` — несогласованность этих полей сама по себе является признаком подделки.

3. **`WebViewConfigurator` + `PinfoActivity`** — `setUserAgentString(AppVars.BROWSER_USER_AGENT)` (см. предыдущий раздел).

## Проверка логики вырезания параметров

Логика проверена автономным прогоном на JDK (13/13 кейсов), включая критичные:

| Кейс | Результат |
| --- | --- |
| `main.php?get_id=56&act=10&go=inf&ab_reload_probe=1&ts=...` → маркер убран, остальное цело | OK |
| **`ch.php?1785063903592&show=1&fyo=0`** — «голый» timestamp без `=`, который использует сама игра | **сохранён** |
| `ch.php?lo=1&1785063902504` | сохранён |
| `main.php?code=77848&get_id=61&act=7&vcode=abc123&ab_timer=1` — боевые `code`/`vcode` | сохранены |
| `main.php?answer=1&action=go&abc=2` — похожие по началу игровые параметры | не затронуты |
| `main.php?ab_nav_bootstrap=1` → query исчезает целиком | OK |
| absolute-form URL, верхний регистр маркера, отсутствие query | OK |

## Проверки

- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL**.
- [x] 13/13 кейсов вырезания параметров.
- [ ] Live-проверка.

## Что смотреть в логах

| Маркер | Ожидание |
| --- | --- |
| `PROXY_STEALTH: stripped client markers=[ab_reload_probe], target=...` | маркеры реально вырезаются перед отправкой |
| `applyGameSettings: ... userAgent=Mozilla/5.0 (Windows NT 10.0...)` | WebView представляется десктопным Chrome |
| `CHAT_REQ_HEADERS: ... ua=Mozilla/5.0 (Windows NT 10.0...)` | UA единый на всех запросах |

## Осталось (вне текущей правки)

- **`Referer`** при переходах формируется клиентом — стоит сверить с тем, что слал бы браузер.
- **Порядок заголовков** в запросе отличается от Chrome; это детектируется только продвинутым фингерпринтингом (TLS/HTTP2 здесь неприменимы — трафик cleartext HTTP/1.1).
- **Тайминги**: идеально равномерные интервалы авто-функций отличимы от человека. Разброс задержек стоит рандомизировать (в ПК-версии есть `IdleManager`).
- Переименование `ab_*` → `an_*` в самом коде по AGENTS2 п.2 остаётся полезным для чистоты, но на трафик больше не влияет.

---

# Сверка с эталоном реального браузера: `Login.har` (2026-07-26)

Пользователь указал на запись реального браузерного трафика авторизации — `Login.har` в корне репозитория (14 записей, Chrome 140, Windows). Это ground truth для анти-детекта.

## Что показал эталон

**Последовательность входа в браузере:**
```
1. GET  /                → 200
2. POST /game.php        → 200   (player_nick + player_password)
3. POST /game.php        → 200   (vcode + player_nick + player_password + verify=33175)  ← шаг капчи
4. GET  /main.php        → 200
5-14. фреймы: ch/resize.html, ch/temp.html, ch/msg.php, ch.php?lo=1, ch/tempw.html,
      ch/but.php, ch/refr.html, ch.php?lo=1, ch.php?<random>&show=1&fyo=0 (x2)
```

**Полный набор заголовков реального браузера (по всем 14 записям):**
```
accept, accept-encoding, accept-language, cache-control, connection,
content-length, content-type, dnt, host, origin, referer,
upgrade-insecure-requests, user-agent
```

## КРИТИЧНО: моя предыдущая правка была ошибочной

В предыдущем шаге я добавил в прокси заголовки `Sec-CH-UA`, `Sec-CH-UA-Mobile`, `Sec-CH-UA-Platform`, считая, что они сделают клиент похожим на Chrome.

**Эталон опроверг это:** Chrome 140 по обычному `http://` **не отправляет ни одного** `Sec-CH-UA*` — client hints передаются только в secure context (HTTPS). Игра работает по cleartext HTTP.

Итог: добавление этих заголовков **усиливало** детект, а не ослабляло — клиент отправлял то, чего настоящий браузер в этом протоколе не шлёт никогда.

**Исправлено:** `appendBrowserIdentityHeaders(...)` теперь подставляет только `User-Agent`. Вырезание `Sec-CH-UA*` из входящих запросов оставлено (на случай, если их пришлёт WebView).

## Прочие расхождения, найденные по HAR, и что сделано

| # | Параметр | Браузер (`Login.har`) | Было в клиенте | Статус |
| --- | --- | --- | --- | --- |
| 1 | `Sec-CH-UA*` | не отправляются | добавлялись мной | **исправлено** (убраны) |
| 2 | Версия User-Agent | `Chrome/140.0.0.0` | `Chrome/124.0.0.0` | **исправлено** (приведено к 140) |
| 3 | Cache-buster чата | `ch.php?0.03977238288876905&show=1&fyo=0` (`Math.random()`) | `ch.php?1785063903592&...` (epoch ms) | **исправлено** |
| 4 | Список комнаты | `ch.php?lo=1` (без cache-buster) | `ch.php?lo=1&1785063902504` | **исправлено** |
| 5 | `Accept-Encoding` | `gzip, deflate` (10/10 запросов) | `identity` в интерцепторе | **не трогал** (см. ниже) |
| 6 | `Connection` | `keep-alive` (10/10) | прокси форсирует `close` | **не трогал** (см. ниже) |
| 7 | `DNT: 1`, `Upgrade-Insecure-Requests: 1` | присутствуют | отсутствуют в нативных запросах | **не трогал** (см. ниже) |

## Почему пункты 5-7 отложены

- **`Accept-Encoding: identity`** — интерцептор ставит его намеренно, чтобы упростить обработку тела; gzip там поддержан, но переключение затрагивает разбор всех ответов (пост-фильтры, парсеры боя/инвентаря). Менять без отдельной проверки рискованно.
- **`Connection: close`** — прокси закрывает соединение осознанно (упрощает управление сокетами и retry). Переход на keep-alive — это переработка жизненного цикла соединений в `LocalHttpProxyServer`.
- **`DNT` / `Upgrade-Insecure-Requests`** — добавить просто, но они имеют смысл только вместе с полным набором браузерных заголовков в нужном порядке; иначе получится ещё одна «почти-браузерная» комбинация. Логично делать одним заходом вместе с пунктами 5-6.

Все три — кандидаты на следующий этап анти-детекта, где набор и порядок заголовков приводится к HAR целиком.

## Дополнительное наблюдение по авторизации

В браузере логин — это **два** POST на `/game.php`: первый с ником/паролем, второй с `vcode` + `verify` (капча). Наш `AuthManager` капчу поддерживает (в логах `captcha=false`, т.е. в тот раз она не потребовалась), но стоит отдельно сверить, что второй POST формируется идентично браузерному, включая порядок полей формы.

## Проверки

- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL**.
- [ ] Live-проверка.

## Что смотреть в логах

| Маркер | Ожидание |
| --- | --- |
| `requestChatRefresh: http://.../ch.php?0.xxxxxxx&show=1&fyo=0` | cache-buster в формате `Math.random()`, а не epoch |
| `Intercepting: http://.../ch.php?lo=1` | без хвостового timestamp |
| `applyGameSettings: ... userAgent=...Chrome/140.0.0.0...` | UA обновлён |
| `PROXY_STEALTH: stripped client markers=[...]` | внутренние маркеры не уходят на сервер |

---

# Авто-бой: красный `proxy-fail` и «не бьёт противников» (2026-07-26)

## Жалоба пользователя

> «После включения авто-боя в поединке он часто высвечивает снизу в нашей строке proxy-fail (красным),
> потом через некоторое время (он раздупляется) и начинает бить противников.»

## Две мои гипотезы, которые оказались НЕВЕРНЫМИ

Фиксирую специально — чтобы к ним не возвращаться и чтобы был виден способ проверки.

### Гипотеза 1 (отвергнута): боевая капча блокировала ходы

В `webviewinterceptor.log` виден `[SERVER_FLOW][raw] state=FIGHT_CAPTCHA` в самом начале боя,
а диалог капчи появился только через 62 секунды. Вывод «капча не распознана и блокирует бой» —
**ошибочный**.

**Что опровергло:** пользователь указал, что капча в игре приходит только в конце поединка.
Проверка URL это подтвердила: диалог всплыл на `main.php?code=...&act=7&fexp=458&fres=1` —
это страница результата боя. Плюс `state=FIGHT_CAPTCHA` из `WebViewRequestInterceptor:1200` —
всего лишь диагностический ярлык для `[SERVER_FLOW]`-лога, он ни на что не влияет.

### Гипотеза 2 (отвергнута): автобой крутится на одной протухшей странице

`autoTurnOnce: htmlLen=3670` не менялся весь бой — я счёл это признаком застрявшего кэша.
**Ошибочно.**

**Что опровергло:** подсчёт vcode в `lezfight.log` — за бой было **25 различных** `vcode`
и 25 различных `enemy`. Состояние боя обновлялось. Постоянная длина обманчива: `vcode`
всегда 32 hex-символа, id врага — 7 цифр, поэтому размер страницы совпадает при разном
содержимом. **Урок: не делать выводов о «застывшем состоянии» по длине HTML.**

## Подтверждённая корневая причина

`fight_context_choice.log` — одна строка, повторённая каждую секунду весь бой:

```
requestAutoTurn: fallback to cached active fight html, len=3670
```

Автоход шёл по кэшу `AppVars.ContentMainPhp` (`MainActivity:1063-1069`) и запускался
**по секундному таймеру**, а не по факту смены раунда. Guard `IsWaitingForNextTurn`
почти не включался: `false` 145 раз против `true` 3.

Замер: **25 раундов, ~65 отправок = 2,6 хода на раунд вместо одного.**

Следствия:
1. Лишняя отправка обрывала предыдущий незавершённый ответ → `Broken pipe` в
   `copyStreamWithCapture` **на успешном `status=200`** → `PROXY_FAIL` → красная строка в UI.
2. Очередь игровых действий забивалась: `waitMs` рос с 930 до 3627 мс (среднее 2257).
   Ходы стояли в очереди по 2-3,6 с — визуально «не бьёт».
3. Через ~50 с всплеск рассасывался — «раздупляется».

Ни одного реального сетевого сбоя не было: сервер отвечал `200` на все запросы.

## Исправление 1: анти-дубль отправки хода

- `LezFight`: добавлен публичный `getVCode()` (поле `_vcode` = `fight_pm[4]`).
- `FightViewModel`: новый `shouldSkipDuplicateTurn(fight, source)`, вызывается в обеих
  точках отправки — `autoTurnOnce` и `processFightHtml`.

Ключевые решения:

| Решение | Почему |
| --- | --- |
| Дедуп по `vcode`, не по `Result` | `inu`/`inb`/`ina` пересчитываются при каждой генерации комбинации — `Result` отличается даже внутри одного раунда |
| Окно `SAME_ROUND_RESUBMIT_GUARD_MS = 3000`, а не жёсткий запрет | Отправки реально терялись из-за оборванных сокетов; глухая блокировка подвесила бы бой до таймаута раунда |
| `autoSelect` не тронут | Это ручное действие пользователя, дедуп там был бы регрессией |

## Исправление 2: ложный `PROXY_FAIL` при уходе клиента

После первого фикса осталось 2 сбоя — **до боя**, на старте приложения.

Механика: `main.php` отдаёт фреймсет (1515 байт), его подфреймы сами грузят `main.php`.
Наша очередь ставит каждый в слот (`reason=main_php`), пейсинг 1 запрос/сек, поэтому
на старте они растягиваются: `waitMs` 980 → 4839 → 5482 → 6209. WebView не дожидается
и отменяет лишний подзапрос → `Broken pipe` на уже полученном `200`.

**Очередь трогать нельзя** — это точный 1:1 порт C# `ProxyRequestQueue.cs`
(там `/main.php` тоже ставится в очередь, L85-88), пейсинг совпадает с `ReserveSlot()`.

Поэтому исправлена **классификация ошибки**, а не пейсинг:

- `LocalHttpProxyServer.copyStreamWithCapture(...)`: запись в сокет клиента теперь
  оборачивается в `ClientAbortException` (все 5 вызовов пишут в `clientOut`,
  поэтому сбой записи = уход клиента; сбой `source.read(...)` остаётся реальной ошибкой).
- Новый `isClientAbort(Throwable)` проверяет тип и текст (`broken pipe`, `connection reset`,
  `connection abort`, `socket closed`, `stream closed`).
- В catch сессии: при уходе клиента пишется `PROXY_CLIENT_ABORT` на уровне debug,
  `RuntimeNetTrace.push("PROXY_FAIL", ...)` **не вызывается** (нет красной тревоги),
  502 не отправляется (сокет уже закрыт). Настоящие сбои идут прежним путём.

## Исправление 3: автозаполнение Google в WebView

В трафике замечен `CONNECT content-autofill.googleapis.com:443` — WebView ходит в сервис
автозаполнения через наш же прокси-канал. Игре автозаполнение не нужно, а посторонний
внешний коннект — лишний признак нестандартного клиента.

`WebViewConfigurator.applyGameSettings(...)`: `setSaveFormData(false)` +
`setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS)` под guard API 26.

## Результат замера (до / после фикса 1)

| Метрика | До | После |
| --- | --- | --- |
| Отправок на раунд | 2,6 | **1,0** |
| `PROXY_FAIL` за сессию | ~27 | **2** (оба до боя, вне поединка) |
| Отсечено дублей | — | 8 |
| Повторов после окна 3 с | — | **0** (ни одна отправка не потеряна) |
| `lezfight.log` | 130 КБ | 21 КБ |
| `htmlLen` в бою | застрял на 3670 | растёт 31679 → 38810 (накопление лога боя) |

Бой завершился штатно: 4 раунда, 4 отправки, `IsBoi=false` + `act=7&fexp=12`.

> Отдельно: первая оценка «0,8 отправки на раунд» была артефактом подсчёта — пятый `vcode`
> имел единственный разбор и не был раундом с ходом. Реальное соотношение ровно 1,0.

## Проверки

- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL** (фикс 1).
- [x] Live-проверка фикса 1 — метрики выше, подтверждено пользователем.
- [x] `./gradlew2.bat --no-daemon` — **BUILD SUCCESSFUL** (фиксы 2 и 3).
- [ ] Live-проверка фиксов 2 и 3.

## Что смотреть в логах

| Маркер | Ожидание |
| --- | --- |
| `autoTurnOnce: submit posted` | ровно один раз на каждый `vcode` |
| `skip duplicate turn, same round vcode=..., ageMs=...` | дубли отсекаются (норма — десятки за бой) |
| `resubmit same round after guard window` | в норме **отсутствует**; если появляется часто — отправки теряются, искать отдельную причину |
| `PROXY_CLIENT_ABORT: client closed connection` | вместо прежнего `PROXY_FAIL` на старте |
| `PROXY_FAIL` | только при настоящих сбоях; на старте не должно быть |
| `CONNECT content-autofill.googleapis.com` | должен исчезнуть из `proxy.log` |

## Осталось (вне текущей правки)

- Стартовая задержка ~7 с: фреймсет + пейсинг очереди 1 req/s держат WebView пустым
  (`html length=279` шесть секунд). Соответствует поведению ПК-версии, менять — значит
  отходить от C#-паритета анти-флуда. Требует отдельного решения.
- `QuickButtonsPanel.java` (3457 строк) — кандидат на разбор по D6.
