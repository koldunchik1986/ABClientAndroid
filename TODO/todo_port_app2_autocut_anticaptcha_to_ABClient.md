# Портирование app2 Auto-Травника и Anti-Captcha в ABClient C#

## Цель

- [x] Проанализировать текущую реализацию `ABClient/` для травника и капчи.
- [x] Проанализировать рабочую реализацию `app2/` для `AutoCut` и `AntiCaptcha`.
- [x] Перенести первый runtime-слой в ПК-версию C# без лицензирования.
- [x] Сохранить текущий WinForms-дизайн и внедрять травник только в начатый модуль травника.
- [x] Проверить сборку `ABClient.csproj` через VS MSBuild.
- [x] Проверить финальную кодировку/дифф после второго прохода.

## Анализ ABClient

### Текущие точки травника

- `ABClient/ABForms/FormMainHerbs.cs` уже содержит `HerbsList`, `IsHerbAutoCut`, `HerbCut`, `DoHerbAutoCut`, `TraceCut`, `TraceCutID`.
- `HerbsList(list)` работает: сохраняет список трав текущей клетки в `AppVars.Profile.HerbCells[MapLocation]`.
- `TraceCut/TraceCutID` работают: пишут сообщение и ставят herb timer `Вырастет <трава> на <клетка>`.
- `IsHerbAutoCut`, `HerbCut`, `DoHerbAutoCut` были закомментированы и возвращали/делали ничего.
- `AppVars.DoHerbAutoCut` есть только как закомментированное поле.
- `FormMainTicks.cs` содержит закомментированный tick-блок авто-`Оглядеться`.
- `FormMain.cs` содержит закомментированные обработчики включения автоспила и настроек.
- `FormSettingsAutoCut` уже имеет текущий дизайн выбора трав и флаг `Выводить в чат результат`.
- `map.js` уже содержит hooks `window.external.HerbsList(...)`, `DoHerbAutoCut()`, `TraceCut(...)`, `Ogl(...)`, `ResoStart(...)`, но ищет только legacy `ogl`.
- `Filter.Process(...)` не маршрутизирует `gameplay/ajax/alchemy_ajax.php` в отдельный обработчик.

### Текущие точки капчи

- `AppVars.CodePng` заполняется в `ABProxy/Session.cs` при загрузке `modules/code/code.php?...`.
- `AppVars.FightLink` хранит pending protected action; placeholder капчи — `????`.
- `FormMainTicks.cs` при `FightLink` с `????` запускает `MyGuamod.Recognizer.Perform()`.
- `Recognizer` локально распознаёт captcha через `NeuroBase`; fallback при ошибке очищает `FightLink` и reload-ит main.php.
- `FormCode` остаётся ручным fallback для ввода captcha.

## Анализ app2

### Anti-Captcha

- `AntiCaptchaManager.java` реализует внешний сервис `https://api.anti-captcha.com/createTask` и `getTaskResult`.
- DTO `Config`: `clientKey`, `phrase`, `caseSensitive`, `numeric`, `math`, `minLength`, `maxLength`, `languagePool`.
- `createTask` отправляет `ImageToTextTask` с `body=<base64 image>`.
- `getTaskResult` polling: до 24 попыток, пауза 3 секунды, берёт `solution.text` при `status=ready`.
- Runtime не отправляет captcha напрямую: возвращает код в общий captcha submit-контур.
- В C# портируем без лицензирования и без Android proxy/runtime checks.

### Auto-Травник

- `AlchemyAjaxPhp.java` обрабатывает `alchemy_ajax.php?act=1/act=3`.
- `act=1` парсит `RESO@`, регистрирует травы, выбирает первую доступную выбранную траву (`availableCount > 0` и `cutVcode` не пустой).
- `act=3` использует `cutVcode` из записи ресурса, не глобальный vcode.
- Captcha token пустой/`00000` означает no-captcha `code=1`; иначе нужен popup/Anti-Captcha.
- Success marker: `Всё прошло успешно.` / `Все прошло успешно.`.
- Wrong-code marker: `невер` + `код`, после него ставится retry текущей клетки.
- `AutoCutManager` держит выбранные травы, клетки, timers, checked-cells, retry, mass-sync, cleanup.
- `AutoCutHandler` готовит main.php контекст: серп, inventory, mass-sync, cleanup; сам `act=3` не отправляет.
- `map.js` app2 принимает `look` и legacy `ogl`, ставит guard 3 секунды и delayed `Ogl` 250 ms.

## План реализации в ABClient C#

- [x] Создать TODO-анализ портирования.
- [x] Включить `AppVars.DoHerbAutoCut` как runtime-флаг.
- [x] Реанимировать существующие `FormMainHerbs.IsHerbAutoCut/HerbCut/DoHerbAutoCut` без платежных/license checks.
- [x] Добавить минимальный `AlchemyAjaxPhp` C# postfilter: parse `act=1/act=3`, pending cut, no-captcha/captcha action через текущий `FightLink` placeholder.
- [x] Подключить `AlchemyAjaxPhp` в `Filter.Process(...)`.
- [x] Обновить `map.js` в существующем контуре: принимать `look` и `ogl`, не запускать параллельный native HTTP-контур.
- [x] Добавить `AntiCaptchaManager` C# как внешний HTTP-клиент anti-captcha.com.
- [x] Добавить настройки Anti-Captcha в `UserConfig` без изменения текущего дизайна: defaults + XML save/load.
- [x] Встроить Anti-Captcha как независимый режим: при включении используется `anti-captcha.com`, при выключении работает ручной ввод или локальный `Recognizer` только если включен `Автоввод цифр`.
- [x] Проверить сборку `ABClient.csproj` / доступный build tool.
- [-] Проверить `ABClient10.csproj`: сборка блокируется до компиляции текущим старым отсутствующим файлом `MyForms/FormContact.cs`, не связанным с этой правкой.
- [x] Проверить mojibake для изменённых `.cs` и TODO — совпадений по контрольным паттернам нет.
- [x] Добавить серп-guard: `act=1` не отправляет `act=3`, пока `MainPhp` не подтвердил или не надел серп.
- [x] Добавить resume pending-среза после подготовки серпа без нового HTTP-контура.
- [x] Добавить видимые настройки Auto-Травника в `FormSettingsAutoCut`: клетки обхода, cleanup, режим таймеров, смены трав, список серпов.
- [x] Добавить видимые настройки Anti-Captcha в `FormSettingsAutoCut`: enabled/API key/phrase/case/numeric/math/min/max/languagePool.
- [x] Добавить seed-каталог трав по группам и live-пополнение каталога из `HerbsList(...)`/`RESO@` с привязкой к текущей клетке.
- [x] Добавить XML save/load для `<autocut>` и `<autocutherbs>`.
- [x] Подключить runtime-обход по CSV-клеткам к существующему `MoveToSafe(...)`/`MapAjax` контуру без отдельного HTTP-клиента.
- [x] Подключить checked-cells текущей смены и retry текущей клетки после неверной captcha/partial multi-cut.

## Первый этап реализации

- Реализуется минимальный безопасный слой:
  - включение runtime-флага травника;
  - выбор травы через существующий список `HerbsAutoCut`;
  - postfilter `alchemy_ajax.php` для `act=1/act=3`;
  - pending `FightLink` для captcha/no-captcha через существующий tick-контур;
  - Anti-Captcha сервис с fallback на старый `Recognizer`.
- Mass-sync, cleanup и route по timer-ам будут дорабатываться следующим проходом, чтобы не ломать текущий WinForms flow большим изменением за раз.

## Выполнено 2026-04-28

- [x] `AppVars.DoHerbAutoCut` восстановлен как runtime-флаг.
- [x] На toolbar добавлена кнопка `Автотравник` в текущем стиле текстовых авто-кнопок; при пустом списке трав открываются существующие настройки автоспила.
- [x] `FormSettingsAutoCut` после сохранения выбранных трав включает runtime-флаг, если список не пустой.
- [x] `FormMainHerbs.IsHerbAutoCut/HerbCut/DoHerbAutoCut` снова выполняют реальную работу без старой pay/license проверки.
- [x] `PostFilter/AlchemyAjaxPhp.cs` добавлен как C# аналог app2 `AlchemyAjaxPhp`: парсит `RESO@`, выбирает первую доступную выбранную траву, ставит pending `FightLink` для no-captcha/captcha, обрабатывает success/wrong-code.
- [x] `Filter.Process(...)` маршрутизирует `gameplay/ajax/alchemy_ajax.php` в новый postfilter.
- [x] `FormMainTicks` отправляет `alchemy_ajax.php?act=3` через `AjaxGet` текущего `main_top` frame, если `FightLink` уже без `????`.
- [x] `FormMainTicks` перед локальным `Recognizer.Perform()` пробует `AntiCaptchaManager.TrySolveCurrentCaptchaWithFallback()`.
- [x] `MyGuamod/AntiCaptchaManager.cs` добавлен: `createTask`, polling `getTaskResult`, browser User-Agent, stale-check challenge, fallback на локальный recognizer.
- [x] `UserConfig` получил XML-настройки Anti-Captcha: `enabled`, `apikey`, `phrase`, `case`, `numeric`, `math`, `minlength`, `maxlength`, `languagepool`.
- [x] `map.js` обновлён в существующем контуре: поддерживает `look` и `ogl`; no-captcha `ResoStart` ставит `code=1`, если `CAPCODE` отсутствует.
- [x] Добавлены runtime-флаги `AutoCutCheckSickle`, `AutoCutArmedSickle`, `AutoCutSickleHand`, `AutoCutSickleHandD`.
- [x] `ParsedDressed` получил `IsWearSickle()` и whitelist серпов по app2.
- [x] `MainPhp` получил существующий-style pre-processing для Авто-Травника: перейти на персонажа, проверить серп, перейти в инвентарь, надеть серп, затем возобновить pending срез.
- [x] `AlchemyAjaxPhp` теперь при `act=1` ставит pending cut и вызывает reload main.php, если серп ещё не подтверждён.
- [x] `ABClient.csproj` собран через Visual Studio MSBuild 2022: `Build succeeded`, 0 warnings, 0 errors.
- [x] `ABClient.csproj` повторно собран после серп-guard прохода через Visual Studio MSBuild 2022: `Build succeeded`, 0 warnings, 0 errors.
- [x] Финальная проверочная сборка выполнена в отдельный `OutDir=bin\Verify\`: `Build succeeded`, 0 warnings, 0 errors.
- [-] Повторная обычная сборка в `bin\Debug\` заблокирована запущенным процессом `ABClient (13608)`, который держит `bin\Debug\ABClient.exe`; компиляция перед copy-step прошла.
- [-] `dotnet build ABClient.csproj` невозможен в этой среде без reference assemblies `.NETFramework v2.0`; рабочий build tool — VS MSBuild.
- [-] `ABClient10.csproj` не собран из-за pre-existing ошибки `MyForms/FormContact.cs` not found.
- [-] `git diff --check -- ABClient TODO` показывает только известное `.gitattributes:7` и LF/CRLF warnings.
- [x] Финальная BOM-проверка изменённых файлов: `NO_BOM_CHANGED_FILES`.
- [x] Финальная mojibake-проверка `ABClient/*.cs` и `TODO/*.md`: совпадений нет.

## Выполнено 2026-04-28, UI и каталог

- [x] `FormSettingsAutoCut` расширен без отдельной новой формы: слева список трав с колонками `Трава/ID/Умение/Рост/Клетка`, справа блоки `Авто-Травник` и `Auto-Captcha / anti-captcha.com`.
- [x] Список трав теперь строится из `AutoCutCatalog`: seed-справочник переносит группы/ID/умение/рост, а live-ответы сервера обновляют `LastLocation`.
- [x] Настройки Auto-Травника сохраняются в профиль: `AutoCutSearchCellsCsv`, `AutoCutCleanupEnabled`, `AutoCutByTimers`, `AutoCutShiftSchedule`, `AutoCutSicklesCsv`.
- [x] Настройки Anti-Captcha сохраняются из UI в уже добавленные поля профиля и затем в XML `<anticaptcha>`.
- [x] `ABClient.csproj` проверочно собран через VS MSBuild 2022 в `bin\Verify\`: `Build succeeded`, 0 warnings, 0 errors.
- [x] `AutoCutRuntime` добавлен в существующий C# контур: использует `AutoCutSearchCellsCsv`, checked-cells текущей смены, due herb timers и `MoveToSafe(...)` для перехода к следующей клетке.
- [x] `AlchemyAjaxPhp` теперь после пустого скана помечает клетку checked и ведёт route дальше; после wrong captcha или multi-cut ставит retry текущей клетки.

## Выполнено 2026-04-28, меню Anti-Captcha и AutoBoss

- [x] В меню `Инструменты` программно добавлены пункты рядом с `Советник рыбака...`: `Авто-Травник...` и `Anti-Captcha...`.
- [x] `Авто-Травник...` открывает `FormSettingsAutoCut` без необходимости первого нажатия toolbar-кнопки `Автотравник`.
- [x] Добавлена отдельная форма `FormSettingsAntiCaptcha` для включения `anti-captcha.com` и настройки API key/phrase/case/numeric/math/min/max/languagePool.
- [x] Anti-Captcha развязана от `Автоввод цифр`: ошибка внешнего сервиса запускает локальный `Recognizer` только при включённом `DoGuamod`; иначе открывается ручной ввод captcha.
- [x] Для `anti-captcha.com` добавлена попытка включить TLS 1.1/1.2 перед HTTPS-запросом и отключён `Expect100Continue` на запросе.
- [x] По свежим логам исправлен следующий слой Anti-Captcha: TLS прошёл, но `createTask` возвращал `ERROR_TASK_ABSENT`; запрос переведён на официальный C#-контракт `JObject` (`clientKey` + `softId` + корневой `task`) и `ContentType=application/json`.
- [x] `WaitForResult` приведён ближе к официальному C# клиенту: первая проверка результата через 3 секунды, далее polling по 1 секунде до 120 секунд.
- [x] Добавлена диагностика Anti-Captcha без утечки секрета: логируется sanitized `createTask request` с `clientKey=***` и `body=base64_len`, а ошибки API пишут `errorCode`, `errorDescription` и raw response до 600 символов.
- [x] Добавлен parity с `/app2` для чата Anti-Captcha: после валидного ответа сервиса C# пишет локальное сообщение `[Анти-Captcha]: ответ сервиса '<code>' - код отправлен.` через существующий `WriteChatMsgSafe(...)`.
- [x] Настройки AutoBoss вынесены из общей вкладки в отдельную вкладку `АвтоБосс`: enabled/attack/trace/report, фильтр боссов и таймаут поиска.
- [x] `BossSearchWords` и `BossSearchInterval` сохраняются/загружаются в XML `<bossauto searchwords="" searchinterval=""/>` и используются в `BossAutoScenario`.
- [x] Исправлен `CookieAwareWebClient.GetWebResponse`: при `WebException` больше не возвращает `null`, что убирает `NullReferenceException` из `NeverApi.GetInfo` и оставляет нормальный WebException/fallback.
- [x] Проверены логи `ABClient/bin/Verify/Logs/Critical`: главные проблемы — TLS-сбой Anti-Captcha, локальный `Recognizer` возвращал `?????`, `NeverApi` ловил NRE из-за `CookieAwareWebClient`, AutoCut доходил до captcha pending и серп подтверждался.

## Осталось для полного parity app2

- [x] Подключить mass-sync/cleanup inventory после прироста массы и `Бесполезный хлам`.
- [x] Довести `AutoCutByTimers` до полного app2 parity: route к due timer-клетке с возвратом на исходную клетку после detour.

## Выполнено 2026-04-28, cleanup и timer-route parity

- [x] `AutoCutRuntime` получил runtime-флаги cleanup/mass-sync: `AutoCutCleanupPending`, `AutoCutCleanupReason`, `AutoCutHarvestedMassSinceCleanup`, `AutoCutKnownMassMax`.
- [x] `AlchemyAjaxPhp` теперь перед срезом при необходимости запрашивает main.php/inventory mass snapshot, а после success учитывает массу ресурса и marker `Бесполезный хлам`.
- [x] Cleanup `Бесполезный хлам` идёт через существующий C# bulk-drop контур `AppVars.BulkDropThing` -> `MainPhpInv` -> `InvEntry.DropLink`, без нового HTTP-контура.
- [x] `MainPhpInv` допускает bulk-drop без фиксированной цены для AutoCut garbage cleanup, логирует redirect/completed и завершает cleanup через `AutoCutRuntime.OnCleanupCompleted(...)`.
- [x] `AutoCutRuntime` добавил due herb timer route-priority, запоминание detour source cell, возврат на исходную клетку после timer-route и продолжение CSV-обхода.
- [x] `AutoCutRuntime` чистит due herb timers текущей клетки после `act=1` и удаляет stale herb timers вне текущей смены.
- [x] Проверочная сборка `ABClient.csproj` через VS MSBuild 2022 выполнена в `bin\VerifyCleanup\`: `Build succeeded`, 0 warnings, 0 errors.

## Выполнено 2026-04-28, fix runtime route/pending cut

- [x] По логам `20260428_20_10_auto_cut_trace.log` найден существующий контур регрессии: `buttonHerbAutoCut_Click` вызывал `RouteNextCellIfCurrentIsNotReady("toolbar_enabled")` сразу после `AutoCutCheckSickle=true`, из-за чего текущая клетка считалась not-ready и маршрут уходил дальше до завершения pending-среза `Лён`.
- [x] Исправление внесено в существующий decision point `AutoCutRuntime.RouteNextCellIfCurrentIsNotReady(...)`: route подавляется, если идёт подготовка AutoCut на текущей CSV-клетке, cleanup, mass-sync или pending `alchemy_ajax.php` в `FightLink`.
- [x] Устранён inventory-loop mass snapshot: если ответ уже пришёл с `main.php?...go=inv...&im=0`, pending mass-sync очищается по текущему address даже если `MainPhpIsInv(html)` не распознал AJAX-инвентарь.
- [x] По логам `20260428_22_50_auto_cut_trace.log` исправлен второй слой: после успешного `sickle armed` без pending-среза планируется `look retry`, чтобы tick-контур reload-нул карту и существующий `map.js` вызвал `DoHerbAutoCut()` -> `Ogl(...)`.
- [x] По повторным логам `look retry consumed` сработал, но reload возвращал не карту, а `inf`/персонажа; добавлен возврат через существующий `MainPhpFindFlora(html)` сразу после `sickle_checked`, чтобы перейти в map/nature контекст до авто-`Ogl`.
- [x] Проверочная сборка `ABClient.csproj` через VS MSBuild 2022 выполнена в `bin\VerifyCleanup\`: `Build succeeded`, 0 warnings, 0 errors.

## Выполнено 2026-04-28, fix city navigation loop

- [x] По логам `ABClient/bin/VerifyCleanup/Logs/Critical/20260428_23_40_*` найден цикл навигации Октала: AutoCut запустил маршрут `12-494 -> 12-307`, затем клиент многократно отправлял `go=dep`/`go=up`, а `UpdateLocationSafe` оставался на `12-494`.
- [x] Причина локализована в существующем decision point `ExtMap/MapPath.cs`: для перехода `12-494 -> 12-428` выставлялся `CityGateType.OktalLeftToRightGate`, из-за чего `MainPhpCityNavigation` выбирал выход через Восточные Ворота и возвращался на ту же клетку.
- [x] Исправлено направление gate на `CityGateType.OktalRightToLeftGate` для `12-494 -> 12-428`; архивные `MapPath_0101/0103.cs` не менялись, так как не входят в `ABClient.csproj`.

## Выполнено 2026-04-29, fix Anti-Captcha -> cut latency

- [x] По логам `ABClient/bin/VerifyCleanup/Logs/Critical/20260429_00_00_*` найден таймаут между Anti-Captcha и срезом: первый код был распознан в `00:09:37.335`, но `act=3` ушёл только в `00:09:57.135` после повторного `Оглядеться`/`act=1`.
- [x] Причина локализована в существующем decision point `FormMainTicks.TimerCrap()`: после замены `????` на код срез выполнялся только на следующем `timerCrap` tick, а `timerCrap` обычно имеет интервал 10 секунд.
- [x] Добавлен общий UI-метод `TrySubmitReadyAlchemyFightLink(source)`, который использует существующий `EnterAlchemyCode(...)`/`AjaxGet` и очищает `FightLink`; timer-контур теперь вызывает этот метод вместо дублирующей ветки.
- [x] `AntiCaptchaManager` после успешного ответа API сразу ставит вызов `TrySubmitReadyAlchemyFightLink("anti_captcha_solved")` в UI thread через `BeginInvoke`, чтобы не ждать следующего 10-секундного tick.

## Выполнено 2026-04-29, fix Anti-Captcha -> AutoBoi finish latency

- [x] Для боевой captcha найден тот же симптом в C# decision point `FormMainTicks.TimerCrap()`: после ответа Anti-Captcha `AppVars.FightLink` получал готовый `code=...`, но `SetMainTopInvoke(...)` выполнялся только на следующем 10-секундном tick.
- [x] Старый timer-контур завершения боя вынесен в общий UI-метод `TrySubmitReadyAutoboiFightLink(source)`, который использует существующий `SetMainTopInvoke(...)`, очищает `FightLink`, сбрасывает `Pers.Ready`, сохраняет tray/autoboi side effects и пишет в `LezFight` log chain.
- [x] `AntiCaptchaManager` после успешного ответа API сразу ставит вызов `TrySubmitReadyAutoboiFightLink("anti_captcha_solved")` через `BeginInvoke`, чтобы готовая `main.php?get_id=61&act=7&code=...` отправлялась без ожидания `timerCrap`.

## Выполнено 2026-04-29, fix Ogl/captcha context для Auto-Травника

- [x] Сверен браузерный эталон `Ogl_srez_kartofel.har`: `act=1` возвращает `RESO@`, `code.php` грузится сразу, `act=3` уходит с `vcode` конкретного ресурса без промежуточного `main.php`/fight flow.
- [x] По логам `ABClient/bin/VerifyWork/Logs/Critical/20260429_01_40_*` найдено отличие C# flow: captcha-срез ждал mass snapshot/main.php, captcha PNG мог затираться после `code.php`, а отправка `act=3` уходила поздно и получала `wrong protection/captcha code`.
- [x] Исправление внесено в существующий decision point `PostFilter/AlchemyAjaxPhp.cs`, без нового HTTP-submit контура: captcha-подготовка теперь создаёт `CodeAddress` сразу из `RESO@`, не ждёт mass snapshot перед captcha-срезом и сразу просит `AntiCaptchaManager.TrySolveCurrentCaptcha()` через `ThreadPool`, чтобы не блокировать proxy/postfilter thread.
- [x] `DispatchPendingAlchemyCut(...)` больше не затирает уже пойманный `CodePng`, если captcha context (`CodeAddress`) тот же; при смене context PNG очищается.
- [-] Проверочная сборка `ABClient.csproj` через VS MSBuild 2022 скомпилировала код, но copy в `bin\VerifyWork\ABClient.exe` заблокирован запущенным процессом `ABClient (12892)`.
- [x] BOM-проверка изменённых текстовых файлов: BOM не найден.
- [x] Mojibake-проверка изменённых файлов: реальных совпадений нет; self-hit в `TODO2/todo_task_20260427_auto_herbalist.md` содержит только перечисление контрольных паттернов.
- [-] `git diff --check` по-прежнему блокируется/шумит известным `.gitattributes:7` и LF/CRLF warnings.

## Выполнено 2026-04-29, fix AutoMoving -> auto Ogl race

- [x] По свежим логам `ABClient/bin/VerifyWork/Logs/Critical/20260429_09_00_*` найдено, что после `sickle checked: return to flora before auto look` не было `alchemy_ajax.php?act=1`: map page доходила до `MapAjax`, но `AppVars.AutoMoving` снимался только асинхронным `NavigatorOffInvoke`, поэтому `map.js` успевал получить `DoHerbAutoCut() == false` и не запускал `Ogl(...)`.
- [x] Исправление внесено в существующий decision point `PostFilter/MapAjax.cs`: при подтверждённом достижении `AutoMovingDestinaton` и выключенном `DoSearchBox` `AppVars.AutoMoving` очищается синхронно до выполнения map scripts; новый submit/HTTP-контур не добавлялся.
- [x] Для диагностики верхнего фрейма `ABProxy/Session.cs` теперь логирует `response read failed` с URL, количеством прочитанных байт и exception из `ServerChatter.ReadResponse()`; fallback-HTML не добавлялся, чтобы не подменять реальные сетевые/серверные ошибки.
- [-] Повторная сборка `ABClient.csproj` через VS MSBuild 2022 дошла до `CoreCompile`, но финальный copy в `bin\VerifyWork\ABClient.exe` заблокирован запущенным `ABClient (11800)`.
- [x] Проверки после правки: `BOM_OK`, `MOJIBAKE_DIFF_OK`; `git diff --check` не показал новых whitespace-ошибок, остались только известный `.gitattributes:7` и LF/CRLF warnings.

## Выполнено 2026-04-29, fix усталости и AutoDrinkBlaz

- [x] По логам `ABClient/bin/VerifyWork/Logs/Critical/20260429_10_20_*` и `20260429_10_30_*` найдено, что `NeverApi.GetAll(...)` постоянно возвращал `GetUserId: EMPTY_RESPONSE nick=Юличка`, а `UpdateTied` больше не появлялся после старого inventory-маркера `Усталость`.
- [x] Исправление внесено в существующий decision point `PostFilter/MainPhpTied.cs`: добавлен гибкий парсинг усталости по legacy-маркеру, label `Усталость:` и fallback `var hpmp = [...]`, с нормализацией `0..100` и логом источника.
- [x] `MainPhp.cs` теперь вызывает единый `MainPhpTied(html)` без дублирования точного HTML-маркера в основном фильтре.
- [x] `FormMainCheckTied.cs` больше не скрывает неуспешный API-опрос на полный 200-секундный интервал: при `null` выставляется retry примерно через 30 секунд и пишется `AppLog.w`.
- [x] Новый raw HTTP-submit для `AutoDrinkBlaz` не добавлялся: срабатывание остаётся в существующем `MainPhp` условии `DoAutoDrinkBlaz && AppVars.Tied >= AutoDrinkBlazTied`.

## Выполнено 2026-04-29, fix усталости во время навигации

- [x] По свежим логам `ABClient/bin/VerifyWork/Logs/Critical/20260429_10_50_*` подтверждено: после `tied=81%` природные страницы содержали только `map data found in HTML`, без `Усталость`/`hpmp`, а `CheckTiedAsync` каждые 30 секунд падал на `NeverApi.GetAll -> GetUserId: EMPTY_RESPONSE`.
- [x] Исправление внесено в существующий контур `FormMainCheckTied.cs`: если `NeverApi.GetAll(...)` вернул `null`, запускается fallback `NeverApi.TryGetTiedFromPInfo(...)`, который читает `pinfo.cgi` и парсит `var hpmp = [...]`.
- [x] `NeverApi.GetInfo(...)` теперь использует браузерный User-Agent для API/pinfo-запросов, чтобы не отправлять нестандартный клиентский идентификатор и повысить шанс ответа сервера.
- [x] `NeverApi.GetPInfo(...)` переведён на `http://www.neverlands.ru/pinfo.cgi?...`, без редиректа с non-www.
- [x] Ужесточён fallback label-парсер в `MainPhpTied.cs`: он больше не берёт первый случайный номер после слова `Усталость`; значение должно идти сразу после `:`. Это закрывает ложное `tied=1%` на странице эликсиров из свежих логов.

## Выполнено 2026-04-29, fix AutoDrinkBlaz и pinfo-refresh после 11:10 логов

- [x] По логам `ABClient/bin/VerifyWork/Logs/Critical/20260429_11_10_*` подтверждено: `main.php?im=6` давал ложный `UpdateTied: tied=1%`, `NeverApi.TryGetTiedFromPInfo(...)` возвращал `EMPTY_RESPONSE`, а AutoDrinkBlaz мог отключиться на нецелевой странице инвентаря, не доказав отсутствие обеих категорий.
- [x] Исправление AutoDrinkBlaz внесено в существующий decision point `PostFilter/MainPhp.cs`: проверка теперь различает точные страницы `im=0&wca=27` и `im=6`, сбрасывает stale `DrinkBlazPotOrElixirFirst` при старте с non-inventory и не отключает автопитьё на общей/нецелевой странице инвентаря.
- [x] Поиск действия эликсира усилен в существующем `MainPhpDrinkBlazPotOrElixir(...)`: ссылка извлекается из `location='...'`, `location="..."`, `href='...'`, `href="..."` и только затем из старого общего `='...'` fallback.
- [x] `MainPhpTied.cs` дополнительно ограничивает label-парсер строкой/ячейкой, где текст до двоеточия равен именно `Усталость`, чтобы описания предметов вроде снятия усталости не обновляли UI ложным `1%`.
- [x] `Filter.cs` теперь маршрутизирует `pinfo.cgi` в существующий `Pinfo(...)`, а `Pinfo.cs` парсит `var hpmp = [...]` и обновляет `UpdateTied` из браузерного pinfo-ответа.
- [x] `FormMainCheckTied.cs` теперь сначала пробует pinfo/hpmp для быстрого обновления усталости, затем выполняет старый `NeverApi.GetAll(...)` для травм/локации; добавлен guard от параллельных `CheckTied`.
- [x] `MapAjax.cs` после каждого обновления map location вызывает tied-only `UpdateCheckTiedFromPInfoSafe()`, чтобы Навигатор обновлял усталость через pinfo/hpmp без ожидания полного `NeverApi.GetAll(...)`.
- [x] Проверочная сборка `ABClient.csproj` через VS MSBuild 2022 выполнена в `bin\VerifyWork\`: `Build succeeded`, 0 warnings, 0 errors.

## Выполнено 2026-04-29, fix AutoDrinkBlaz resume и cookies для pinfo

- [x] По свежим логам `ABClient/bin/VerifyWork/Logs/Critical/20260429_11_40_*` и `20260429_11_50_*` подтверждено: автоматический `NeverApi.TryGetTiedFromPInfo(...)` получал `EMPTY_RESPONSE`, но ручной браузерный `pinfo.cgi` проходил через `Pinfo(...)` и обновлял усталость.
- [x] Исправление внесено в существующий `NeverApi.GetInfo(...)`: cookies из `CookiesManager.Obtain(...)` теперь попадают в `CookieContainer` `CookieAwareWebClient`, а не только в заголовок, который не гарантировал браузерный session context.
- [x] `UseElik.har` подтвердил, что `Эликсир Блаженства` используется GET-запросом `main.php?get_id=43&act=107...`, а сервер возвращает страницу инвентаря без автоматического возврата.
- [x] Исправление возврата внесено в существующий decision point `PostFilter/MainPhp.cs`: после успешного AutoDrinkBlaz выставляется уже существующий `AppVars.SwitchToFlora = true`, чтобы следующий inventory-ответ вернул поток в природу и Auto-Травник/навигация продолжили прежнее действие.
- [x] Исправлен route-loop Auto-Травника по смене: `AutoCutRuntime.RouteNextCell(...)` больше не очищает `CheckedCells` и не запускает `new_circle` в той же смене, если все CSV-клетки уже проверены и due herb timer отсутствует. Следующая проверка планируется на начало новой смены, аналогично смыслу сообщения `Таймер не установлен, смена трав близка.`
- [x] Проверочная сборка `ABClient.csproj` через VS MSBuild 2022 выполнена в `bin\VerifyWork\`: `Build succeeded`, 0 warnings, 0 errors.
- [x] Финальные проверки: `git diff --check` без новых whitespace-ошибок; BOM не найден; mojibake-паттерны `РЎР|РџС|Ð|Ñ` в diff не найдены, `????` присутствует только как ожидаемый captcha placeholder в TODO.

## Выполнено 2026-04-29, fix route skip для клеток без выбранных трав

- [x] По логам `20260429_12_10_auto_cut_trace.log` и `20260429_12_20_auto_cut_trace.log` найдено, что после `12-221` маршрут выбрал `12-224` как `reason=unchecked`, хотя `RESO@` на этой клетке содержал только невыбранные травы; затем аналогично была выбрана `12-254` с `Чеснок`/`Сельдерей`.
- [x] Причина локализована в существующем decision point `AutoCutRuntime.FindNextUncheckedCell(...)`: фильтр смотрел только CSV и `CheckedCells`, но не учитывал `HerbCells` cache и список выбранных трав.
- [x] Исправление внесено в существующий контур `AutoCutRuntime`: перед авто-`Ogl` и перед `MoveToSafe(...)` клетка проверяется по кешу. Если в кеше нет выбранных трав, логируется `route skip cell: reason=no_selected_herbs_in_cell_cache`; если выбранные травы есть, но в текущей смене все `0`, логируется `selected_herbs_empty_current_shift`.
- [x] Неизвестные клетки и клетки со stale-cache оставлены допустимыми, чтобы не потерять первичное сканирование после смены трав.
- [x] Проверочная сборка `ABClient.csproj` через VS MSBuild 2022 выполнена в `bin\VerifyWork\`: `Build succeeded`, 0 warnings, 0 errors.
- [x] Проверки после фикса: `git diff --check` без новых whitespace-ошибок; `HerbCell.cs` без BOM; mojibake-паттерны в diff не найдены.

## Выполнено 2026-04-29, fix no resource state route

- [x] По логам `20260429_12_30_auto_cut_trace.log` найден новый стоп после предыдущего route-skip фикса: `AlchemyAjaxPhp` писал `act1: no resource state`, но не помечал клетку checked и не запускал переход к следующей CSV-клетке.
- [x] Исправление внесено в существующий decision point `PostFilter/AlchemyAjaxPhp.cs`: `ERR` по-прежнему планирует retry текущей клетки, а non-`ERR` пустой/неразобранный resource state вызывает `AutoCutRuntime.OnScanWithoutSelectedHerb("alchemy_act1_no_resource_state")`.
- [x] Новый raw HTTP-submit или параллельный route-контур не добавлялся; используется прежний `AutoCutRuntime`/`MoveToSafe(...)`.
- [x] Проверочная сборка `ABClient.csproj` через VS MSBuild 2022 выполнена в `bin\VerifyWork\`: `Build succeeded`, 0 warnings, 0 errors.
- [x] Финальные проверки: `git diff --check` без новых whitespace-ошибок; релевантные файлы без BOM; mojibake в изменённом C# файле не найден.

## Выполнено 2026-04-29, fix stale-cache после смены трав

- [x] По логам `20260429_13_40_auto_cut_trace.log` и `20260429_13_50_auto_cut_trace.log` найдено, что route-skip начал правильно пропускать клетки без выбранных трав, но применял старый `HerbCells` cache после новой смены. Из-за этого клетки `12-314`, `12-311`, `12-339`, `12-370` не попадали на первичное обновление текущей смены.
- [x] Причина локализована в существующем decision point `AutoCutRuntime.GetUncheckedCellSkipReason(...)`: ветка `no_selected_herbs_in_cell_cache` не проверяла `UpdatedInTicks`, в отличие от ветки `selected_herbs_empty_current_shift`.
- [x] Исправление внесено в тот же контур: если `HerbCell` не обновлён в текущей смене, cache не используется для skip, и клетка остаётся допустимой для маршрута. После обновления через `HerbsList`/`RESO@` cache-skip снова работает до следующей смены.
- [x] Новый route/HTTP-контур не добавлялся; используется прежний `AutoCutRuntime`/`MoveToSafe(...)`/`alchemy_ajax.php?act=1` flow.
- [x] Проверочная сборка `ABClient.csproj` через VS MSBuild 2022 выполнена в `bin\VerifyWork\`: `Build succeeded`, 0 warnings, 0 errors.
- [x] Финальные проверки: `git diff --check` без новых whitespace-ошибок; `HerbCell.cs` без BOM; mojibake в `HerbCell.cs` не найден.

## Выполнено 2026-04-29, fix cache-skip current cell after toolbar enable

- [x] По логам `20260429_14_00_auto_cut_trace.log` найдено, что после нажатия `Автотравник` серп проверялся и выполнялся возврат в природу, но `DoHerbAutoCut()` останавливался на `route skip cell: cell=12-341, reason=selected_herbs_empty_current_shift, source=current_cell` без `route next`.
- [x] Причина локализована в существующем bridge decision point `FormMainHerbs.DoHerbAutoCut()`: если `AutoCutRuntime.ShouldAutoLookOnCurrentCell()` возвращал `false` из-за cache-skip текущей клетки, bridge не запускал route-next. В `app2` аналог уже решён через `routeNextIfCurrentCellCachedNotReady(...)`.
- [x] Исправление внесено в тот же C# контур: добавлен `AutoCutRuntime.RouteNextIfCurrentCellCachedNotReady(...)`, который проверяет текущую unchecked CSV-клетку по cache и запускает существующий `RouteNextCell(...)`/`MoveToSafe(...)`; новый raw HTTP-submit/route-контур не добавлялся.
- [x] Проверочная сборка `ABClient.csproj` через VS MSBuild 2022 выполнена в `bin\VerifyWork\`: `Build succeeded`, 0 warnings, 0 errors.
- [x] Финальные проверки: релевантные файлы без BOM; mojibake в изменённых C#/app2-файлах не найден; `git diff --check` показал только известный шум `.gitattributes:7` и LF/CRLF warnings.

## Выполнено 2026-04-29, fix continuous route round after cleanup

- [x] По логам `ABClient/bin/VerifyWork/Logs/Critical/20260429_14_40_auto_cut_trace.log` найдено, что после `garbage bulk-drop completed` автотравник дошёл до `12-370`, сделал `act=1`, затем `RouteNextCell(...)` решил `all cells checked for shift=20260429:3` и поставил ожидание до следующей смены `18:50`.
- [x] Причина локализована в существующем decision point `AutoCutRuntime.RouteNextCell(...)`: при отсутствии следующей unchecked-клетки контур завершал весь обход до следующей смены, хотя автотравник должен продолжать круговой маршрут по CSV-клеткам.
- [x] Исправление внесено в тот же контур: при полном круге `CheckedCells` очищается, для клеток с выбранными травами в состоянии `selected_herbs_empty_current_shift` сбрасывается runtime-снимок `UpdatedInTicks`, и через существующий `lookRetry` планируется новый круг. Клетки без выбранных трав (`no_selected_herbs_in_cell_cache`) по-прежнему не гоняются зря.
- [x] Проверочная сборка `ABClient.csproj` через VS BuildTools MSBuild 2022 выполнена в `bin\VerifyWork\`: `Build succeeded`, 0 warnings, 0 errors.
- [x] Финальные проверки: релевантные файлы без BOM; mojibake в `HerbCell.cs` не найден; `git diff --check` показал только известный шум `.gitattributes:7` и LF/CRLF warnings.

## Инварианты

- Дизайн WinForms не менять, существующие формы и кнопки сохранять.
- Травник внедрять в `FormMainHerbs`, `FormSettingsAutoCut`, `map.js`, `Filter`, а не создавать отдельный параллельный UI.
- Не добавлять лицензирование.
- Для логов использовать `AppLog`/`FileLogger`.
- Не добавлять User-Agent с маркерами клиента для внешнего Anti-Captcha API; использовать браузерный UA.
- При сбое Anti-Captcha сохранять fallback на локальный `Recognizer` или ручной ввод.
