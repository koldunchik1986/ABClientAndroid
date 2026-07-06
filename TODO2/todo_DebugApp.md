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
