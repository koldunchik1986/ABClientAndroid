



















# Анализ проекта ABClient — Сводный файл портирования

**Обновлено**: 2026-05-01
**Источник истины**: `ABClient\ABClient.csproj` + полное сканирование диска

## P0 — Авто-Лесоруб в ПК-версии ABClient

**Статус**: `[~]` Код реализован в ПК-версии; локальная сборка заблокирована отсутствующими .NET Framework 2.0 targeting assemblies.
**Цель**: добавить в C# клиент отдельную автофункцию `Авто-Лесоруб` по уже проверенной Android-логике `app2/ANClient`, но без создания второго HTTP/JS/scheduler-контура.
**Ограничение текущей задачи**: пользователь запросил реализацию именно ПК-версии; изменения внесены в `ABClient/` несмотря на общее правило read-only для эталонной папки.

### Фактический прогресс 2026-05-01

- [x] Расширен существующий `AutoCutRuntime` без второго `alchemy_ajax`/route/captcha контура.
- [x] Добавлен режим `AutoCutMode.Tree`, отдельные каталоги/выбор/кеш клеток деревьев и настройки `AutoLumberjack...` в профиле.
- [x] Добавлен whitelist топоров и mode-aware подготовка инструмента: травник `wca=4`, лесоруб `wca=2`.
- [x] `map.js` передаёт `r_type` в существующий bridge `HerbsList`, а `AlchemyAjaxPhp` фильтрует ресурсы по активному режиму.
- [x] Добавлен toolbar/menu toggle `Авто-Лесоруб`, взаимное исключение с `Авто-Травник`, настройки деревьев и синхронизация состояния кнопок.
- [x] Исправлены найденные review-риски: явный `r_type` имеет приоритет над именем, пустой скан очищает stale cache, меню лесоруба открывает вкладку деревьев.
- [-] Локальная сборка `dotnet build "ABClient10.sln" -c Debug` заблокирована окружением: нет reference assemblies для `.NETFramework,Version=v2.0`.
- [ ] Требуется smoke-run ПК-клиента в окружении с рабочим MSBuild/VS: травник, лесоруб на `Орешник`, взаимное исключение, ручные `Срезать`/`Срубить`.

### Что уже известно из Android и HAR

- `Оглядеться`: `gameplay/ajax/alchemy_ajax.php?act=1&vcode=...&r=...`.
- Ответ `act=1` может содержать травы и деревья одновременно.
- `Орешник` из `ogl_srez_oreshnik.har`: `[261,"Орешник",30,30,209397049,10,2,2,1,"522496d14d51781a8a2cc142812a245e",9,1]`.
- Признак дерева в HAR: `r_type=9`; дополнительный fallback — whitelist названий деревьев из `wiki/Таблица Лесоруба — Викиневер.html`.
- `Спилить`: `alchemy_ajax.php?act=3&res_id=261&r_x=993&r_y=999&r_time=30&r_type=9&uid=209397049&curs=10&mass=2&p=2&l_time=30&vcode=...&code=52022`.
- Успех: `RESO@["Всё прошло успешно."]@[]...@[0,[1,600]]@[]`.
- Подготовка инструмента лесоруба должна использовать `main.php?im=0&wca=2`, а не травяной `wca=4`.

### Инструменты лесоруба

- `Мачете`
- `Столярный топорик`
- `Плотницкий топор`
- `Топор подмастерья`
- `Топор дровосека`
- `Секира лесоруба`
- `Секира Мастера-лесоруба`

### Найденный существующий ПК-контур

| Файл | Текущая роль | Что нужно сделать для `Авто-Лесоруб` |
| ---- | ------------ | ------------------------------------ |
| `ABClient\HerbCell.cs` | `HerbCell`, `AutoCutCatalog`, `AutoCutRuntime`: каталог трав, кеш клеток, маршрут, таймеры роста, cleanup, retry | Ввести режим ресурса `HERB/TREE` или эквивалентную mode-aware модель; расширить каталог деревьями; сохранить старые поля трав для совместимости профилей |
| `ABClient\ABForms\FormMainHerbs.cs` | Bridge-методы `HerbsList`, `IsHerbAutoCut`, `HerbCut`, `DoHerbAutoCut`, `TraceCut` | Добавить mode-aware wrappers: выбор ресурса, сообщение в чат, таймеры и проверка выбранности без копирования алгоритма |
| `ABClient\PostFilter\AlchemyAjaxPhp.cs` | Парсит `RESO@`, выбирает ресурс, ставит `pendingAlchemyCut`, отправляет `act=3`, обрабатывает успех/капчу | Фильтровать `act=1` по активному режиму: травы отдельно, деревья отдельно; для деревьев использовать `r_type=9` и tree whitelist; `PendingAlchemyCut` должен хранить mode/source |
| `ABClient\PostFilter\MainPhp.cs` | Перед автоспилом проверяет/надевает серп, делает mass snapshot, cleanup | Убрать hard-code `sickle/wca=4` из общего AutoCut-префлоу: для деревьев проверять/надевать топор через `wca=2`; тексты и возврат на карту сделать mode-aware |
| `ABClient\PostFilter\MainPhpInv.cs` | Завершает bulk-drop `Бесполезный хлам`, вызывает `AutoCutRuntime.OnCleanupCompleted` | Cleanup оставить общим, но лог/source должен указывать `auto_cut` или `auto_lumberjack` |
| `ABClient\TInvUd.cs` | `ParsedDressed.IsWearSickle()` и whitelist серпов | Добавить whitelist топоров и общий метод `IsWearAutoCutTool(string[] toolNames)`; `IsWearSickle()` оставить wrapper-ом |
| `ABClient\ABForms\FormSettingsAutoCut.cs` + `.Designer.cs` + `.resx` | WinForms настройки травника: список трав, клетки, cleanup, timers, серпы, anti-captcha | Расширить существующее окно вкладками/группами `Авто-Травник` и `Авто-Лесоруб`; добавить список деревьев, клетки обхода, cleanup, timers, список топоров |
| `ABClient\MyProfile\UserConfig.cs` | Runtime/profile defaults: `AutoCutHerbs`, `HerbsAutoCut`, `HerbCells`, `AutoCutSicklesCsv` | Добавить tree-поля с дефолтами без миграции старых herb-полей: `AutoCutTrees`, `TreesAutoCut`, `TreeCells`, `AutoLumberjack...` |
| `ABClient\MyProfile\UserConfigVars.cs` | Свойства настроек | Добавить свойства `AutoLumberjackSearchCellsCsv`, `AutoLumberjackCleanupEnabled`, `AutoLumberjackByTimers`, `AutoLumberjackShiftSchedule`, `AutoLumberjackAxesCsv`, `DoAutoLumberjackWriteChat` или использовать общий chat flag с явным решением |
| `ABClient\MyProfile\UserConfigLoad.cs` | XML-load `<autocut>`, `<autocutherb>`, `<herbautocut>` | Добавить чтение `<autolumberjack>`, `<autocuttree>`/`<treeautocut>`; старые профили должны открываться без ошибок |
| `ABClient\MyProfile\UserConfigSave.cs` | XML-save autocut-настроек | Сохранять новые настройки деревьев отдельными XML-узлами; не менять семантику старых узлов травника |
| `ABClient\ScriptManager.cs` | COM bridge для `map.js`: `HerbsList`, `IsHerbAutoCut`, `DoHerbAutoCut`, `TraceCut` | Добавить bridge-методы для richer resource list (`ResourcesList`) или mode-aware wrappers; старые методы оставить для совместимости JS |
| `ABClient\map.js` | Активный JS карты из `.csproj`; вызывает `window.external.DoHerbAutoCut()`, `Ogl(...)`, `HerbsList(...)`, `TraceCut(...)`, `ResoStart(...)` | Не создавать новый JS-контур; при необходимости добавить передачу `r_type` в C# (`ResourcesList`) и сохранить старый `HerbsList` |
| `ABClient\ABForms\FormMain.cs` | Toolbar/menu: `buttonHerbAutoCut_Click`, `menuitemSettingsAutoCut_Click` | Добавить отдельный toggle `Авто-Лесоруб`; при включении выключать `Авто-Травник`, сбрасывать pending alchemy и runtime режима |
| `ABClient\ABForms\FormMainTicks.cs` | Tick-loop: consume retry, PressOgl/auto-look | Учитывать active AutoCut-like режим: retry и auto-look работают для травника или лесоруба, но не конкурируют |
| `ABClient\PostFilter\Filter.cs` | Роутинг `alchemy_ajax.php` в `AlchemyAjaxPhp` | Изменений минимум; убедиться, что оба режима проходят через тот же фильтр |

### Архитектурное решение

- [ ] Не создавать новый `alchemy_ajax` handler, отдельный scheduler, отдельный route manager или отдельный captcha solver.
- [ ] Расширить существующий `AutoCutRuntime` mode-aware режимом `HERB/TREE`.
- [ ] Оставить старые публичные имена травника (`HerbsList`, `DoHerbAutoCut`, `TraceCut`, `AutoCutHerbs`) как wrappers/legacy storage, чтобы не ломать существующие профили и JS.
- [ ] Для деревьев добавить отдельное persistent-хранилище, чтобы не смешивать выбранные травы и деревья: `TreeCells`, `AutoCutTrees`, `TreesAutoCut` или эквивалент.
- [ ] Сделать активным только один AutoCut-like режим: если включили `Авто-Лесоруб`, выключить `Авто-Травник`; если включили `Авто-Травник`, выключить `Авто-Лесоруб`.
- [ ] `PendingAlchemyCut` должен знать режим, чтобы success/retry/cleanup/timer/chat не писали дерево как траву.
- [ ] `r_type=9` считается деревом; если сервер вернет нестандартный `r_type`, fallback по названию из tree catalog.
- [ ] `r_type!=9` для режима `HERB` не должен выбираться лесорубом; дерево не должно попадать в травяной auto-cut.

### Модель данных профиля

- [ ] В `UserConfig` добавить список деревьев по форме `AutoCutHerbInfo` или новый класс `AutoCutTreeInfo` с полями `Id`, `Name`, `Skill`, `GrowthMinutes`, `Group`, `LastLocation`, `Selected`.
- [ ] Добавить кеш клеток деревьев отдельно от `HerbCells`: `TreeCells` или generic map с mode key.
- [ ] Добавить выбранные деревья: `TreesAutoCut`.
- [ ] Добавить настройки обхода: `AutoLumberjackSearchCellsCsv`.
- [ ] Добавить настройки cleanup: `AutoLumberjackCleanupEnabled`.
- [ ] Добавить настройки таймеров: `AutoLumberjackByTimers`, `AutoLumberjackShiftSchedule`.
- [ ] Добавить настройки инструментов: `AutoLumberjackAxesCsv`.
- [ ] Решить и зафиксировать в коде, общий ли chat flag (`DoAutoCutWriteChat`) или отдельный `DoAutoLumberjackWriteChat`; предпочтение Android-паритету — отдельный флаг.
- [ ] XML backward compatibility: отсутствие новых узлов в старом профиле должно давать безопасные дефолты и не сбрасывать старый `Авто-Травник`.

### Каталог деревьев

- [ ] Извлечь seed-список деревьев из `wiki/Таблица Лесоруба — Викиневер.html`.
- [ ] Добавить в каталог `Орешник` и сверить его с HAR: `res_id=261`, `r_type=9`, `r_time=30`, `mass=2`.
- [ ] Для каждого дерева заполнить `Name`, `Skill`, `GrowthMinutes`, `Group`; если ID неизвестен — пустой ID, как сейчас сделано для квестовых трав.
- [ ] `RegisterObservedResource` должен обновлять ID/рост/последнюю клетку по фактическому `RESO@`, не ломая seed-данные.

### UI и управление

- [ ] В `FormSettingsAutoCut` добавить вкладку или отдельную группу `Авто-Лесоруб`.
- [ ] Для деревьев показать `ListView`: `Дерево`, `ID`, `Умение`, `Рост`, `Клетка`, `Группа`.
- [ ] Добавить кнопки `Выбрать все`/`Снять все` для деревьев или общий selector по активной вкладке.
- [ ] Добавить поле клеток обхода лесоруба отдельно от травника.
- [ ] Добавить checkbox cleanup для лесоруба.
- [ ] Добавить checkbox `Спиливать по таймерам роста` для лесоруба.
- [ ] Добавить список разрешённых топоров (`CheckedListBox`) по whitelist из `TInvUd.cs`.
- [ ] В `FormMain` добавить отдельный toolbar/menu toggle `Авто-Лесоруб`.
- [ ] При включении лесоруба: сбросить pending alchemy, runtime, tool-check flags, вызвать reload main.php, запустить route-next если текущая клетка не готова.
- [ ] При выключении лесоруба: отменить pending alchemy, очистить `FightLink` если это `alchemy_ajax.php`, не трогать ручные действия пользователя.

### Runtime и маршрут

- [ ] Обобщить `AutoCutRuntime.CheckedCells`, `checkedShiftKey`, `lookRetryAt`, `timerRoute...`, `massSnapshotSyncPending` так, чтобы состояние не смешивалось между `HERB` и `TREE`.
- [ ] `ShouldAutoLookOnCurrentCell(mode)` должен брать клетки и cache текущего режима.
- [ ] `OnScanWithoutSelectedResource(mode, source)` должен маршрутизировать по клеткам текущего режима.
- [ ] `OnCutSuccess(mode, retrySameCell, source, resourceMass)` должен ставить таймер и cleanup для текущего режима.
- [ ] `GetUncheckedCellSkipReason(mode, cell)` должен анализировать cache именно трав или деревьев.
- [ ] `HasDueTimerForCell(mode, cell)` должен отличать timers лесоруба от timers травника.
- [ ] Логи оставить в `auto_cut_trace`, но source/tag должны явно содержать `auto_lumberjack` или `mode=tree`.

### Alchemy AJAX

- [ ] `ProcessAlchemyAct1` должен работать только если включен один из AutoCut-like режимов.
- [ ] Парсер `ResourceCandidate` уже читает `RType`; использовать его для mode-фильтра.
- [ ] Для режима `TREE` выбирать только `resource.RType == "9"` или resource name из tree catalog.
- [ ] Для режима `HERB` не выбирать `RType == "9"`, если это дерево.
- [ ] `RegisterObservedResources` должен писать и `HerbCells`, и `TreeCells` по соответствующим ресурсам.
- [ ] `HasMoreSelectedAvailableAfterCut` должен считать только ресурсы текущего режима.
- [ ] `BuildFinishUrl` уже передает `r_type`; для деревьев оставить `r_type=9` из ответа сервера без переопределения.
- [ ] Ошибка `Неверный код защиты`/captcha должна сбрасывать pending current mode и ставить retry только для current mode.

### Инструменты и inventory

- [ ] В `ParsedDressed` добавить `AutoCutAxeNames` с whitelist топоров.
- [ ] Добавить `IsWearAutoCutTool(string[] toolNames)` и `GetAutoCutAxeNames()`.
- [ ] `IsWearSickle()` оставить для старого кода и сделать wrapper на общий метод.
- [ ] В `MainPhp` заменить жесткие `MainPhpArmedSickle`/`MainPhpWearSickle` вызовы в AutoCut-preflow на mode-aware проверки.
- [ ] Для `HERB`: inventory filter `&im=0&wca=4`, tool list `GetAutoCutSickleNames()`.
- [ ] Для `TREE`: inventory filter `&im=0&wca=2`, tool list `GetAutoCutAxeNames()`.
- [ ] Если инструмент не найден, выключить только текущую автофункцию и написать понятное сообщение в чат.

### JS bridge и map.js

- [ ] Активный файл по `.csproj`: `ABClient\map.js`, не `ABClient\Js\map.js`.
- [ ] Не дублировать `Ogl(...)`, `ResoStart(...)`, `AjaxGet('alchemy_ajax.php?...')`.
- [ ] Сохранить ручные кнопки `Срезать`/`Срубить` и текущий `ResoStart(...)` без изменения server protocol.
- [ ] Если нужен richer cache для деревьев, добавить `window.external.ResourcesList(...)` с `name:available:r_type|` рядом с существующим `HerbsList(...)`.
- [ ] Старый `HerbsList(name:available|)` оставить, чтобы не ломать текущий травник.

### Чат, таймеры и сообщения

- [ ] Сообщение старого травника оставить: `Автоспил травы ...`.
- [ ] Для лесоруба добавить отдельное сообщение: `Авто-Лесоруб: спил дерева ...` или `Автоспил дерева ...`.
- [ ] `TraceCut` разделить по mode: `Трава ... спилена` и `Дерево ... спилено`.
- [ ] Таймеры роста должны иметь описания вида `Вырастет <дерево> на <клетка>` или отдельный маркер `IsLumberjack/IsTree`, чтобы routing по таймерам не смешивал дерево и траву.
- [ ] Если структура `AppTimer` не поддерживает mode, добавить поле/описание без ломки старых timers.

### Анти-регрессия травника

- [ ] С включенным только `Авто-Травник` должны сохраниться: выбор трав, route CSV, retry той же клетки, captcha, mass snapshot, cleanup `Бесполезный хлам`, таймеры роста.
- [ ] Деревья из смешанного `act=1` ответа не должны срезаться травником.
- [ ] Старые профили с `<herbsautocut>`, `<autocutherbs>`, `<autocut sickles=...>` должны загружаться без миграционных ошибок.
- [ ] Старый toolbar `buttonHerbAutoCut` должен работать как раньше.

### Проверки после реализации

- [ ] Собрать `ABClient.sln` или `ABClient.csproj` в той конфигурации, которая используется для ПК-клиента.
- [ ] Проверить `git diff --check`.
- [ ] Проверить UTF-8 без BOM и отсутствие mojibake в измененных `.cs`, `.js`, `.resx`, `.md`.
- [ ] Проверить, что не добавлены новые browser-identifying `User-Agent` строки.
- [ ] Проверить, что нет второго параллельного `alchemy_ajax.php`/route/captcha контура.
- [ ] Smoke-run ПК-клиента: включить `Авто-Травник`, убедиться что он ведет себя как раньше.
- [ ] Smoke-run ПК-клиента: включить `Авто-Лесоруб`, открыть клетку с `Орешник`, убедиться что выбран `r_type=9`, надевается топор через `wca=2`, отправляется `act=3`, успех ставит timer/cleanup.
- [ ] Проверить взаимное исключение: включение лесоруба выключает травник и наоборот.
- [ ] Проверить manual HTML buttons: ручное `Срубить`/`Срезать` продолжает работать.

### Риски

- Если смешать cache трав и деревьев в `HerbCells`, route может пропускать нужные клетки или считать клетку проверенной для неправильного режима.
- Если не mode-aware `PendingAlchemyCut`, success дерева может вызвать `TraceCut` травника и поставить неправильный timer.
- Если оставить hard-code `wca=4`, лесоруб будет искать серп вместо топора.
- Если включить обе автофункции одновременно, они начнут конкурировать за `Ogl(...)`, `FightLink` и `alchemy_ajax.php`.
- Если изменить `map.js` без совместимости `HerbsList`, можно сломать ручное окно ресурсов и текущий травник.

## Мёртвые файлы (есть на диске, но НЕ в .csproj — НЕ компилируются)

Следующие файлы **не включены** в .csproj и не должны портироваться:

| Файл | Папка | Заменён на |
| ---- | ----- | ---------- |
| `Converters.cs` | Helpers | `MyHelpers\HelperConverters.cs` |
| `HelperHttp.cs` | MyHelpers | Не используется |
| `HelperDice.cs` | MyHelpers | `Helpers\Dice.cs` |
| `AskPassword.cs` | Forms | `MyForms\FormAskPassword.cs` |
| `AutoLogon.cs` | Forms | `MyForms\FormAutoLogon.cs` |
| `FormProfile.cs` | Forms | `MyForms\FormProfile.cs` |
| `FormProfiles.cs` | Forms | `MyForms\FormProfiles.cs` |
| `NewPassword.cs` | Forms | `MyForms\FormNewPassword.cs` |
| `MapPath_0101.cs` | ExtMap | Старая версия |
| `MapPath_0103.cs` | ExtMap | Старая версия, не используется |

---

## Статус реализации по подпапкам

**Легенда:**
- `[+]` — Полностью реализована (все файлы портированы)
- `[~]` — Частично реализована
- `[-]` — Не требует портирования (Windows-специфика)
- `[ ]` — Не реализована






















| Папка | Описание | Файлов .cs | Статус | Android-расположение | Детальный анализ |
| ----- | -------- | ---------- | ------ | -------------------- | --------------- |
| `PostFilter` | Фильтры ответов сервера | 59 | `[~]` **Частично** | `postfilter/` | `TODO/todo_PostFilter_detailed_comparison.md` |
| `ABProxy` | HTTP-прокси сервер | 18 | `[-]` Заменён архитектурно | WebView + SessionManager | — |
| `ABForms` | Главная форма (partial classes) | 36 | `[+]` Полностью | `MainActivity.java` | — |
| `MyForms` | Диалоговые формы | 22 | `[+]` Полностью | Activity / Dialog | — |
| `Forms` | Старые формы (только HerbNavigator) | 1 | `[+]` Полностью | `ui/Navigator.java` | — |
| `MyProfile` | Конфигурация профиля | 11 | `[+]` Полностью | `model/UserConfig.java` | — |
| `ExtMap` | Карта и навигация | 13 | `[+]` Полностью | `utils/ExtMap.java`, `model/Cell.java` | — |
| `Lez` | ИИ боя (автобой) | 9 | `[+]` Полностью | `lez/LezFight.java` | `TODO/todo_LezFight.md` |
| `AppControls` | WinForms контролы | 11 | `[-]` Win-специфика | — | — |
| `Helpers` | Утилиты (Crypts, Russian) | 8 | `[+]` Полностью | `utils/CryptoUtils.java` | — |
| `MyHelpers` | Утилиты (Strings, Conv) | 5 | `[+]` Полностью | `utils/HelperStrings.java` | — |
| `Neuro` | Нейросеть для капчи | 2 | `[~]` Частично | Captcha в Interceptor | — |
| `MyGuamod` | Распознавание капчи | 1 | `[~]` Частично | Captcha в Interceptor | — |
| `MyChat` | Очередь сообщений чата | 1 | `[+]` Полностью | `utils/Chat.java` | — |
| `MySounds` | Звуковые уведомления | 1 | `[+]` Полностью | `utils/EventSounds.java` | — |
| `Tabs` | Мульти-вкладки браузера | 3 | `[+]` Полностью | `manager/TabManager.java` | — |
| `Things` | База предметов | 2 | `[+]` Полностью | `repository/ThingsRepository.java` | — |
| `Profile` | Простой профиль (устар.) | 2 | `[-]` Заменён | `MyProfile/UserConfig.java` | — |
| `Properties` | Ресурсы/настройки | 3 | `[-]` Не требует | — | — |
| `Resources` | DLL, изображения | 2 | `[-]` Не требует | — | — |
| `Js` | JavaScript файлы | 6 | `[+]` В assets | assets/js/ | — |
| **QuickButtons** | Быстрые кнопки на UI | 5 | `[+]` Полностью | `ui/QuickButtonsPanel.java` | — |
| **Авто-Функции** | Автобой, авторыбалка, автоохота и т.д. | 10+ | `[+]` Полностью (AutoFunctionsManager.java, FastActionManager.java, BossAuto.java, CompasAuto.java) |

---

## Статус реализации корневых файлов (согласно .csproj)

| Файл | Описание | Статус реализации |
| ---- | -------- | ----------------- |
| `Program.cs` | Точка входа | `[+]` ABClientApplication.java |
| `AppConsts.cs` | Константы | `[+]` AppConsts.java |
| `AppVars.cs` | Глобальное состояние | `[+]` AppVars.java |
| `AppTimer.cs` | Кастомный таймер | `[+]` AppTimer.java |
| `AppTimerManager.cs` | Менеджер таймеров | `[+]` AppTimerManager.java |
| `AutoAnswerMachine.cs` | Автоответчик | `[+]` AutoAnswerMachine.java |
| `AutoboiState.cs` | Enum состояний автобоя | `[+]` AutoboiState.java |
| **Авто-Функции (FastAction)** | LezFight, FastActionManager, AutoFunctionsManager | `[+]` Полностью реализованы |
| `Bookmark.cs` | Закладки | `[-]` Не требуется (TabManager) |
| `BossContact.cs` | Контакты боссов | `[+]` BossAuto.java |
| `BossMap.cs` | Карта боссов | `[+]` BossAuto.java |
| `ChatUser.cs` | Пользователь чата | `[+]` ChatUser.java |
| `ChatUsersManager.cs` | Менеджер пользователей чата | `[+]` ChatUserList.java |
| `Contact.cs` | Модель контакта | `[+]` Contact.java |
| `ContactsManager.cs` | Менеджер контактов | `[+]` ContactsManager.java |
| `CookieAwareWebClient.cs` | WebClient с cookies | `[-]` Не требует (OkHttp) |
| `DataManager.cs` | Менеджер файлов/путей | `[+]` DataManager.java |
| `ExplorerHelper.cs` | Очистка кеша IE | `[-]` Не требует (Windows-специфика) |
| `Favorites.cs` | Избранное | `[+]` Favorites.java (или TabManager) |
| `FeatureBrowserEmulation.cs` | Эмуляция IE | `[-]` Не требует (Windows-специфика) |
| `FishTip.cs` | Подсказка рыбалки | `[+]` FishAjaxPhp.java |
| `Foe.cs` | Враг | `[+]` Foe.java |
| `HerbCell.cs` | Ячейка с травой | `[+]` AbcCell.java |
| `IdleManager.cs` | Менеджер простоя | `[+]` MainActivity.java / ForegroundService |
| `InvEntry.cs` | Запись инвентаря | `[+]` InvEntry.java / InventoryParser.java |
| `KeyList.cs` | Список ключей | `[ ]` Не проанализирован |
| `ListItemBotLevelEx.cs` | Элемент списка бота | `[-]` Не требуется |
| `LoadingUrlList.cs` | Список загружаемых URL | `[+]` WebViewRequestInterceptor.java |
| `Log.cs` | Логирование | `[+]` FileLogger.java / DebugLogger.java |
| `NativeMethods.cs` | P/Invoke для WinINet | `[-]` Не требует (Windows-специфика) |
| `NeverApi.cs` | API Neverlands | `[+]` NeverApi.java |
| `Prims.cs` | Примитивы | `[+]` Prims.java |
| `RoomManager.cs` | Менеджер комнат/чата | `[+]` RoomManager.java |
| `ScriptManager.cs` | Менеджер JS-инъекций | `[+]` WebAppInterface.java |
| `TInvUd.cs` | Обновление инвентаря | `[+]` InventoryParser.java / ParsedDressed.java |
| `Tips.cs` | Подсказки | `[ ]` Не реализован |
| `TorgList.cs` | Список торговли | `[+]` TorgList.java |
| `TorgPair.cs` | Пара торговли | `[+]` TorgPair.java |
| `UnderAttack.cs` | Состояние "под атакой" | `[+]` UnderAttackManager.java |
| `UnhandledExceptionManager.cs` | Обработчик исключений | `[-]` Не требует (Android crashlytics) |
| `UserForBo.cs` | Пользователь для бота | `[+]` LezBotsGroup.java |
| `UserInfo.cs` | Информация о пользователе | `[+]` NeverApi.java / PinfoActivity |
| `VersionClass.cs` | Версия | `[+]` VersionClass.java |


---

## Контентные файлы (Content/None в .csproj)

| Файл | Тип | Описание | Статус |
| ---- | --- | -------- | ------ |
| `abcells.xml` | Content | Данные карты | `[+]` В assets |
| `abthings.xml` | Content | База предметов | `[+]` В assets |
| `abfavorites.xml` | Content | Избранное | `[+]` В assets |
| `abteleports.xml` | Content | Телепорты | `[+]` В assets |
| `bossusers.xml` | Content | Боссы | `[+]` В assets |
| `chatusers.xml` | Content | Пользователи чата | `[+]` В assets |
| `map.xml` | Content | Основная карта | `[+]` В assets |
| `mapnav.js` | Content | JS навигации | `[+]` В assets |
| `PostFilter\json2.js` | Content | JSON2 библиотека | `[+]` В assets/js |
| `arena_v04.js` | None | JS арены | `[+]` В assets |
| `ch_list.js` | None | JS списка чата | `[+]` В assets |
| `map.js` | None | JS карты | `[+]` В assets |
| `Resources\map2.xml` | None | Вторичная карта | `[ ]` Не скопирован |
| `abneuro.dat` | None | Данные нейросети | `[ ]` Не скопирован |
| `spells.txt` | None | Заклинания для Lez | `[ ]` Не скопирован |
| `MySounds\*.wav` | None | Звуки (7 файлов) | `[ ]` Не скопированы |

---

## Ключевые архитектурные отличия Android от C#

### 1. Прокси-подход
- **C#**: Локальный HTTP-прокси (`ABProxy/`) перехватывает все запросы, модифицирует HTML/JS
- **Android**: `WebViewRequestInterceptor` + `WebViewClient.shouldInterceptRequest()` + JS-инъекции через `HtmlUtils.getJsFix()`
- **Результат**: Функционально эквивалентно, но архитектурно иначе

### 2. Фреймовая модель
- **C#**: Мульти-фреймовый IE-браузер (`main_top`, `ch_buttons`, `ch_list`, `chmain`)
- **Android**: Одиночный WebView + JS-эмуляция `top.frames[...]` через `AndroidBridge`
- **Результат**: Полная эмуляция через `HtmlUtils.getJsFix()` + DOM-stubs (`transfer`, `complect`, `hbar`)

### 3. Система VCode
- **C#**: Парсинг из каждого ответа в глобальный `AppVars.VCode`
- **Android**: `SessionManager` — централизованный синглтон с TTL, fight-fallback, версионированием
- **Результат**: Android-версия более надёжна (TTL, thread-safe, fight-context)

### 4. Авто-функции
- **C#**: Таймеры WinForms + `IdleManager`
- **Android**: `AutoModeForegroundService` + `ForcedActionGuard` + `FightViewModel`
- **Результат**: Полный паритет, event-driven бой < 100ms

---

## Сводная статистика

| Категория | Количество |
| --------- | ---------- |
| `[+]` Полностью реализовано | ~120 файлов/модулей |
| `[~]` Частично реализовано | ~20 файлов (PostFilter + Neuro) |
| `[s]` Заглушки (PostFilter) | ~28 файлов |
| `[-]` Не требует портирования | ~15 файлов (Windows-специфика) |
| `[ ]` Не реализовано | ~18 файлов |
| **Мёртвые файлы (не портировать!)** | **15** |

---

## Приоритеты доработки (по важности)

### P0 — Критичные пробелы (влияют на gameplay)
1. **PostFilter: 16 отсутствующих MainPhp-модулей** — `MainPhpCure`, `MainPhpDrink`, `MainPhpFish`, `MainPhpWearComplect`, `MainPhpTied`, `MainPhpDarkFog`, `MainPhpDarkTeleport`, `MainPhpRob`, `MainPhpRobinHood`, `MainPhpWtime`, `MainPhpAutoCure`
2. **PostFilter: 28 заглушек** — `HpJs`, `HpmpJs`, `MapAjax`, `MapJs`, `ButPhp`, `ShopAjaxPhp`, `SvitokJs`, `IndexCgi` и др.
3. **Neuro/капча** — `abneuro.dat` + нейросеть для распознавания капчи

### P1 — Важные (ухудшают UX)
4. **Ресурсы**: `spells.txt`, `MySounds/*.wav`, `map2.xml` — не скопированы в assets
5. **KeyList.cs** — не проанализирован, назначение неизвестно
6. **Tips.cs** — подсказки игроку

### P2 — Желательные (косметика/оптимизация)
7. **Neuro полная интеграция** — замена hardcoded-распознавания на нейросеть
8. **Refactor PostFilter заглушек** — заменить пустые методы на реальные фильтры
9. **ForumTopicJs, TradePhp, RouletteAjaxPhp** — второстепенные фильтры

---

## Карта зависимостей ключевых систем

```
MainActivity
├── WebView → WebViewRequestInterceptor → SessionManager (VCode)
├── WebView → HtmlUtils.getJsFix() → AndroidBridge (WebAppInterface)
├── PostFilter.Filter → MainPhp → [LezFight, FishAjaxPhp, FightJs, ...]
├── AutoModeForegroundService → ForcedActionGuard → AppVars
├── FightViewModel → FightAnnounceHandler → SessionManager
├── FastActionManager → AppVars.FastNeed → MainPhp
├── AutoFunctionsManager → UserConfig → SharedPreferences
└── ProfileManager → UserConfig → DataManager
| **Мёртвые файлы (не портировать!)** | **15** |
