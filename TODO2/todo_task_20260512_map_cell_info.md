# Задача 2026-05-12: modern-блок полной информации текущей клетки на карте

## Цель

Добавить под картой природы красивый modern-блок `Полная информация текущей клетки`, который показывает подробные данные о текущей клетке без дополнительных сетевых запросов.

## Найденный существующий контур

- [x] Карта рендерится через `app2/src/main/assets/js/map.js`, который вызывает `window.external.*` bridge-методы.
- [x] Bridge реализован в `WebAppInterface`: уже содержит `CellDivText(...)`, `CellAltText(...)`, `MapText()` и доступ к `ExtMap`.
- [x] Статические данные клеток загружаются в `ExtMap.Cells` из `map.xml`/runtime-map.
- [x] Время посещения клетки уже хранится в `AppVars.SearchBoxVisited` и персистится через `ExtMap.markCellVisited(...)` в `abcells.xml`.
- [x] Содержимое клетки после `Оглядеться`/спила уже сохраняется в `AutoCutManager` snapshot-cache (`cell_snapshots_json_*`, `lumberjack_cell_snapshots_json_*`).

## Реализация

- [x] Добавлен `Cell.BotInfo` для подробного хранения `<bots name="..." minLevel="..." maxLevel="..."/>` из `map.xml`.
- [x] `ExtMap.parseCellNode(...)` теперь сохраняет подробные bot-записи в `cell.Bots`, сохраняя старые агрегаты `MinBotLevel`/`MaxBotLevel`.
- [x] `AutoCutManager.getCellResourceSummaryForMap(...)` читает и травяной, и лесорубный snapshot клетки, чтобы блок показывал содержимое без нового `alchemy_ajax.php`.
- [x] В `WebAppInterface` добавлен `CurrentCellFullInfo()` с подробностями: регномер, название, регион, стоимость хода, боты, вода/рыба/группа трав, содержимое из snapshot, время посещения и осмотра.
- [x] В `MapJs` bridge-prelude добавлен alias `window.external.CurrentCellFullInfo()`.
- [x] В `assets/js/map.js` блок выводится в `view_build_bottom()` под картой и получает modern CSS с адаптивной сеткой.

## Проверки

- [x] `./gradlew.bat :app2:compileDebugJavaWithJavac --rerun-tasks` выполнен успешно; остался существующий deprecation warning в `DeviceKeyStore.java`.
- [x] Проверка типовых mojibake-паттернов в изменённых `.java`, `.js`, `.md` не нашла совпадений.

## Доработка после проверки `/logs/`

- [x] По `logs/Critical/20260512_06_20_mapjs.log` видно, что `/js/map.js?v=6` перехватывался и `MapJs` отдавал `source=assets/js/map.js`, но в `webappinterface/map runtime` логах не было вызова `CurrentCellFullInfo()`.
- [x] Добавлен runtime fallback `ANCLIENT_MAP_RUNTIME_PATCH_CELL_INFO`: он оборачивает `view_map()` и вставляет/обновляет `an_current_cell_info_host` после построения DOM карты, даже если конкретная версия `view_build_bottom()` не вывела блок.
- [x] Добавлена диагностика через существующий `TraceMapRuntime`: в новых логах искать `MAP_RUNTIME CELL_INFO rendered len=...` или `CELL_INFO skip/error ...`.
- [x] `./gradlew.bat :app2:compileDebugJavaWithJavac --rerun-tasks` после fallback-patch выполнен успешно; остался существующий deprecation warning в `DeviceKeyStore.java`.

## Доработка завершения перехода и мигания

- [x] Найден текущий контур проблемы: `StateReady('GO')` ставит `id="movingcell"` и запускает бесконечный `Flash1()/Flash2()`, а `finFunction()` после завершения шага вызывал только `MapReInit(...)`, не удаляя старый `movingcell`.
- [x] В `assets/js/map.js` добавлен единый stop-контур `AnStopMovingFlash(...)`: он гасит flash-флаг, отменяет pending timeout и снимает `id="movingcell"`, чтобы красная рамка переставала мигать после `NeverTimer`/завершения движения.
- [x] В `finFunction()` и `timerst(lp=1)` добавлен `AnRefreshCurrentCellMarker(...)`: текущая клетка пересобирается через существующий `CellDivText(..., isframe=true)`, затем повторно рендерится modern-блок полной информации.
- [x] Добавлен bridge `UpdateCurrentCellFromCoords(x, y, source)`: JS синхронизирует текущую клетку с `AppVars.Profile.MapLocation`, `RoomManager.onMapLocationConfirmed(...)` и `ExtMap.markCellVisited(...)` без нового HTTP-запроса.
- [x] В `MapJs` добавлен alias bridge-метода и runtime-wrapper для `finFunction()/timerst()`, чтобы fallback-patch также обновлял full-info и снимал старый `movingcell` на загруженных вариантах карты.

## Доработка AutoCut и дубля modern-блока

- [x] По свежим логам `06:50:33` найден ответ `alchemy_ajax.php?act=3`: `RESO@["Вас кто-то опередил."]...` после auto-submit captcha для `Тополь`.
- [x] Причина зависания: `AlchemyAjaxPhp.processAlchemyAct3(...)` не считал `опередил` ни успехом, ни wrong-captcha, поэтому оставлял `pendingCut`; затем `hasPendingCutForRouteGuard()` блокировал повторное `Оглядеться`, а повторный запуск AutoCut переиспользовал старую captcha.
- [x] В `AlchemyAjaxPhp` добавлена обработка `resource already taken`: stale `pendingCut` и captcha-dedup ключ очищаются, затем планируется штатный one-shot `Оглядеться` через существующий retry/NeverTimer-контур.
- [x] Убран второй вывод `CurrentCellFullInfo()` из `assets/js/map.js view_build_bottom()`. Теперь modern-блок должен создаваться одним стабильным runtime-host рядом с картой, до mail.ru баннера, чтобы не было дубля и голого текста без CSS после баннера.

## Доработка после логов 07:20

- [x] Найдена регрессия мигания: в `StateReady('GO')` `Flash1()` вызывался до `an_moving_flash_active=true`, поэтому новый guard сразу прекращал мигание. Флаг перенесён до `Flash1()`.
- [x] Найдена причина неверной current-cell синхронизации: runtime `timerst wrapper` синхронизировал target-клетку до финального `finFunction`, а затем `finFunction GO` мог перезаписать Java-локацию координатами из промежуточного payload. Теперь `timerst` только обновляет info-блок, а не меняет `Profile.MapLocation`.
- [x] `AnRefreshCurrentCellMarker(...)` теперь перерисовывает все видимые `divtext_*` overlay через существующий `CellDivText(...)`, чтобы старые красные route-квадраты исчезали после завершения шага.
- [x] Начальная загрузка карты синхронизирует координаты через `view_map wrapper` до рендера modern-блока, чтобы не показывать `Текущая клетка ещё не определена`, когда координаты уже есть в JS-карте.
- [x] Для captcha Авто-Боя добавлен быстрый повторный popup из `FightViewModel.autoTurnOnce(...)`: если после submit сервер вернул parsed captcha-state (`IsBoi=false`, `FightLink code=<captcha_code>`, `CodeAddress`), новое окно показывается сразу, не ожидая следующего боевого таймера.

## Доработка после логов 07:50

- [x] Сравнены переходы `go=inf`, `go=inv`, `go=ret`, `useaction=addon-action`: `MapJs.process(...)` стабильно отдаёт `cellInfoPatch=true`, а успешные рендеры фиксируются как `MAP_RUNTIME CELL_INFO rendered len=1309`.
- [x] Найден слабый участок существующего runtime-host: если `an_current_cell_info_host` был создан не рядом с DOM карты или anchor появился позже, повторный рендер только менял HTML и не переносил host под `world_host`.
- [x] В `MapJs.CURRENT_CELL_INFO_RUNTIME_PATCH` рендер теперь требует map-anchor и каждый раз прикрепляет host сразу после `world_host/world_cont2/world_cont`, чтобы возврат с `Ваш персонаж`/`Инвентарь` не оставлял блок в старом или невидимом месте.

## Доработка после логов 08:20

- [x] Свежие логи подтвердили, что после `Ваш персонаж -> Вернуться` и `Инвентарь -> Вернуться` вызовы `MAP_RUNTIME CELL_INFO rendered len=1197` есть, то есть HTML блока формируется, но DOM-host остаётся визуально не там/не виден.
- [x] Найдено отличие уровня DOM-вставки: прежний runtime-patch цеплял host рядом с ближайшим `world_host/world_cont2/world_cont`, что зависит от table/td-обёртки конкретного возврата.
- [x] `MapJs.CURRENT_CELL_INFO_RUNTIME_PATCH` теперь поднимается от `world_host` до внешней таблицы карты и вставляет `an_current_cell_info_host` после всей таблицы карты, с `display:block`, `clear:both`, `width:100%`.
- [x] Диагностика расширена: новый trace `CELL_INFO host attached after map table, anchor=..., after=...` покажет, что блок прикрепился вне внутренней map-обёртки.

## Доработка после логов 08:30

- [x] Логи подтвердили, что после `Ваш персонаж/Инвентарь -> Вернуться` host уже вставляется после таблицы карты (`CELL_INFO host attached after map table`) и HTML строится (`rendered len=1197`), но пользователь видит plain-text без modern CSS.
- [x] Причина локализована в применении CSS: runtime раньше доверял `<style id="an_cell_info_styles">`, но в проблемной ветке style мог существовать/теряться без фактического применения.
- [x] `__anEnsureCellInfoStyles()` теперь каждый раз валидирует и перепривязывает `<style>` в текущий документ, обновляет `cssText/textContent`, не выходит по одному только наличию id.
- [x] Добавлен inline fallback `__anApplyCellInfoInlineStyles(holder)` после `holder.innerHTML`, чтобы основные modern-стили применялись даже при сбое stylesheet в конкретном HTML-фрейме.

## Доработка кнопок после `Оглядеться`

- [x] Найден существующий RESO-контур отрисовки ресурсов в `app2/src/main/assets/js/map.js`: кнопки создавались, но `butalt` был пустым (`'' : ''`), поэтому `Срезать`/`Срубить` визуально не отображались.
- [x] Сверено с baseline `app2/src/main/assets/map_orig.js` и alternate asset `app2/src/main/assets/map.js`: корректная логика `ingr[i][10] == 4 ? 'Срезать' : 'Срубить'` уже существовала там.
- [x] В `app2/src/main/assets/js/map.js` восстановлены подписи кнопок без добавления нового параллельного контура.

## Доработка long-press выбранной клетки

- [x] Найден существующий единый контур modern-блока: `WebAppInterface.CurrentCellFullInfo()` строит HTML по текущему `AppVars.Profile.MapLocation`, а `MapJs.CURRENT_CELL_INFO_RUNTIME_PATCH` вставляет host рядом с DOM карты.
- [x] В `WebAppInterface` выделен общий builder полной информации клетки и добавлен `SelectedCellFullInfo(x, y)`, который строит блок по координатам без изменения текущей локации персонажа.
- [x] В `MapJs` runtime-patch добавлен long-press по существующим DOM id `img_x_y`/`divtext_x_y`: длительное нажатие показывает выбранную клетку, подавляет последующий click, чтобы не стартовал переход, и логирует событие через `TraceMapRuntime`.
- [x] При обычном клике/переходе и при синхронизации текущей клетки выбранная клетка сбрасывается, поэтому после перемещения блок снова показывает текущую клетку.

## Доработка боевой captcha по логам 09:03

- [x] Рекурсивно проверены свежие `/logs/` с поддиректориями `Critical`, `Logcat`, `pool`; ключевой сценарий найден в `logs/Critical/20260512_09_00_fightauto.log`, `fightviewmodel.log`, `mainactivity.log`, `anticaptchamanager.log`.
- [x] Причина задержки: после submit сервер сразу возвращал новый `fexp[4]` captcha-token, но `fexp[6]` был countdown (`30`, `29`, ...), и `extractCaptchaUrlFromFexp(...)` игнорировал token до `fexp[6]=0`. Из-за этого новая captcha показывалась только через ~30 секунд, затем ещё тратила время на распознавание.
- [x] `FightAuto.extractCaptchaUrlFromFexp(...)` теперь принимает token сразу, даже если `fexp[6] > 0`, чтобы popup и Anti-Captcha стартовали немедленно.
- [x] Добавлен `AppVars.FightCaptchaSubmitNotBeforeMs`: captcha отображается и распознаётся сразу, но `MainActivity.submitCaptchaCodeFromDialog(...)` удерживает submit до истечения countdown, чтобы не отправлять правильный код раньше серверного окна.

## Доработка настройки блока и captcha по логам 09:36

- [x] В существующий раздел настроек `Карта` добавлен переключатель `Полная информация текущей клетки` с ключом `show_current_cell_full_info` и значением по умолчанию `true`.
- [x] В `WebAppInterface` добавлен bridge `IsCurrentCellFullInfoEnabled()`, который читает настройку из default shared preferences.
- [x] В `MapJs.CURRENT_CELL_INFO_RUNTIME_PATCH` добавлена проверка этой настройки: при выключении host `an_current_cell_info_host` очищается и скрывается без удаления остального runtime-контура карты.
- [x] По логам `09:36` подтверждено, что первый код Anti-Captcha `12108` был принят: бой завершился победой сразу после submit.
- [x] Найдена причина ощущения "со второго раза": после победы finish HTML содержал `fexp[4]` с flag `90`; предыдущая правка принимала любой `fexp[6] > 0` как новую captcha, из-за чего открывался лишний post-fight dialog с невалидной картинкой 258 bytes и timeout.
- [x] `FightAuto` теперь считает реальным fexp-captcha только flag `0..30`; flag `90` и другие non-countdown значения игнорируются как post-fight markers и не запускают ложную captcha.

## Доработка боевой captcha по логам 10:30

- [x] В свежих логах `10:30` найден новый сбой: Anti-Captcha решила код `19460`, но `MainActivity.submitCaptchaCodeFromDialog(...)` удерживал отправку `18313 ms` по `FightCaptchaSubmitNotBeforeMs`, после чего сервер уже выдал новый challenge и чат показал `Капча не принята сервером. Введите код заново.`
- [x] Сравнение с утренней `app2/src_old` показало, что рабочий submit-контур отправлял боевую captcha сразу, без ожидания countdown.
- [x] Задержка отправки убрана из существующего `submitCaptchaCodeFromDialog(...)`; `FightAuto` больше не выставляет future-time для `FightCaptchaSubmitNotBeforeMs`, сохраняя немедленный popup и немедленную отправку ручного/Anti-Captcha ответа.

## Доработка карты и Auto-Лесоруба по логам 11:20-11:30

- [x] По свежим логам найден момент поломки: `11:25:56` после post-fight возврата на `main.php?get_id=56&act=10&go=inf` JS падает с `Cannot read properties of null (reading 'innerHTML')`.
- [x] После JS-ошибки Auto-Лесоруб видит `DoHerbAutoCut=false, current cell not ready`, а карта остаётся частично построенной: без стабильных подписей/форматирования и без нормального modern-блока.
- [x] Исправлен существующий guard в `HtmlUtils.getJsFix()`: override `document.getElementById(...)` больше не возвращает `null`, если body ещё не создан, а отдаёт безопасный dummy-элемент для ранних `.innerHTML` вызовов.
- [x] Дополнительно укреплена существующая ветка `StateReady('GO')` в `assets/js/map.js`: обновление `divtext_*` и `maptext` теперь проверяет наличие DOM-узла, чтобы серверный GO/таймер не обрывал весь runtime карты.
- [x] Повторная проверка mojibake по изменённым `HtmlUtils.java`, `map.js`, `todo_task_20260512_map_cell_info.md` не нашла совпадений.
- [x] `./gradlew.bat :app2:compileDebugJavaWithJavac --rerun-tasks` выполнен успешно; остался существующий deprecation warning в `DeviceKeyStore.java`.
- [x] `./gradlew.bat :app2:assembleDebug` выполнен успешно, assets/runtime JS вошли в APK.
- [x] `./gradlew.bat :app2:installDebug` выполнен успешно; APK `anclient_v1.1.5.apk` установлен на `Mi Note 3 - 9`.

## Доработка long-press по логам 13:20

- [x] В свежих логах modern-блок строится (`MAP_RUNTIME CELL_INFO rendered len=1304`), но нет событий `selected by long press`, значит отказ происходит до вызова `SelectedCellFullInfo(...)`.
- [x] Найдена слабая точка существующего runtime-patch: long-press таймер отменялся на любом `touchmove`; Android WebView часто генерирует микродвижение пальца во время удержания.
- [x] В `MapJs.CURRENT_CELL_INFO_RUNTIME_PATCH` long-press больше не отменяется на `touchmove`, добавлен `contextmenu` fallback для native long-click WebView и trace `CELL_INFO long press handlers installed`.
- [x] Проверка mojibake по `MapJs.java` и этому TODO не нашла совпадений.
- [x] `./gradlew.bat :app2:compileDebugJavaWithJavac --rerun-tasks` выполнен успешно; остался существующий deprecation warning в `DeviceKeyStore.java`.
- [x] `./gradlew.bat :app2:assembleDebug` выполнен успешно.
- [x] `./gradlew.bat :app2:installDebug` выполнен успешно; APK `anclient_v1.1.6.apk` установлен на `Mi Note 3 - 9`.

## Доработка динамической карты жестом

- [x] Найден существующий контур подгрузки видимых клеток: `map.js` уже содержит `loadMap(dir)` для дорисовки строк/колонок и `freeMap(dir)` для серверного движения.
- [x] Добавлен touch/mouse-pan в `assets/js/map.js`: при перетаскивании карты меняются `cur_margin_left/top`, а края догружаются через существующий `loadMap('left/right/top/bottom')` без `map_ajax.php` и без изменения текущей клетки персонажа.
- [x] После drag подавляется синтетический click WebView, чтобы отпускание пальца не запускало переход по клетке.
- [x] `MapJs` учитывает флаг `window.__an_map_pan_active`, чтобы long-press full-info не срабатывал во время реального перетаскивания карты.
- [x] Проверка mojibake по `map.js` и `MapJs.java` не нашла совпадений; в TODO осталась только старая документированная строка про `????` в `AlchemyAjaxPhp.java`.
- [x] `./gradlew.bat :app2:compileDebugJavaWithJavac --rerun-tasks` выполнен успешно; остался существующий deprecation warning в `DeviceKeyStore.java`.
- [x] `./gradlew.bat :app2:assembleDebug` выполнен успешно.
- [x] `./gradlew.bat :app2:installDebug` выполнен успешно; APK `anclient_v1.1.6.apk` установлен на `Mi Note 3 - 9`.

## Доработка количества ресурсов в full-info

- [x] Найден существующий контур содержимого клетки: `AlchemyAjaxPhp.buildCellSnapshotList(...)` сохраняет `RESO@` snapshot, `AutoCutManager.getCellResourceSummaryForMap(...)` отдаёт его в `WebAppInterface.buildCellContentsInfo(...)`.
- [x] Snapshot теперь сохраняет видимое серверное количество `available/total` для трав и деревьев, например `1/1`, `0/1`, `1/2`.
- [x] Старые snapshot-ы с одним числом остаются совместимыми: `name:1` отображается как `1/1`.
- [x] Проверка mojibake по `AutoCutManager.java`, `AlchemyAjaxPhp.java` и этому TODO не нашла новых повреждений; `????` в `AlchemyAjaxPhp.java` относится к старому captcha-placeholder.
- [x] `./gradlew.bat :app2:compileDebugJavaWithJavac --rerun-tasks` выполнен успешно; остался существующий deprecation warning в `DeviceKeyStore.java`.
- [x] `./gradlew.bat :app2:assembleDebug` выполнен успешно.
- [x] `./gradlew.bat :app2:installDebug` выполнен успешно; APK `anclient_v1.1.6.apk` установлен на `Mi Note 3 - 9`.
