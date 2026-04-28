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

## Инварианты

- Дизайн WinForms не менять, существующие формы и кнопки сохранять.
- Травник внедрять в `FormMainHerbs`, `FormSettingsAutoCut`, `map.js`, `Filter`, а не создавать отдельный параллельный UI.
- Не добавлять лицензирование.
- Для логов использовать `AppLog`/`FileLogger`.
- Не добавлять User-Agent с маркерами клиента для внешнего Anti-Captcha API; использовать браузерный UA.
- При сбое Anti-Captcha сохранять fallback на локальный `Recognizer` или ручной ввод.
