# План переименования ПК-версии ABClient в ANClient

## Назначение задачи

Переименовать ПК-версию C# клиента из `ABClient` в `ANClient`, сохранив функциональность и структуру проекта. Цель - убрать клиентские маркеры `ABClient`/`ABC`/`ab*` там, где они обозначают именно название клиента, и заменить их на `ANClient`/`ANC`/`an*` по аналогии с уже переименованным Android-модулем `app2`.

## Эталон из app2

- Java package: `ru.neverlands.anclient`.
- Application class: `ANClientApplication`.
- UI/application name: `ANClient`.
- Диагностические маркеры: `ANCLIENT_*`.
- Данные клиента: `ancache`, `ancells.xml`, `anthings.xml`, `anteleports.xml`.
- Схема карты/клеток: `ancell`.

## Область переименования ПК-версии

- Решение: `ABClient10.sln` -> `ANClient10.sln`.
- Папка проекта: `ABClient/` -> `ANClient/`.
- Project files: `ABClient.csproj`/`ABClient10.csproj` -> `ANClient.csproj`/`ANClient10.csproj`.
- Namespace/root assembly: `ABClient` -> `ANClient`.
- Подпапки клиентского имени: `ABForms` -> `ANForms`, `ABProxy` -> `ANProxy`.
- Классы/ресурсы с клиентским префиксом: `ABClientIcon` -> `ANClientIcon`, `ABFastRx.dll` -> `ANFastRx.dll` при наличии ссылки.
- Файлы данных клиента: `abcells.xml` -> `ancells.xml`, `abthings.xml` -> `anthings.xml`, `abteleports.xml` -> `anteleports.xml`, `abfavorites.xml` -> `anfavorites.xml`, `abcache` -> `ancache`.
- Классы карты клиента: `AbcCell`/`AbcCells` -> `AncCell`/`AncCells`.
- JS/COM bridge-маркеры клиента: `map_abc.js`, `map.js.abc`, `SetNeverTimer`-обвязки с `ABC` -> `ANC`, если это именно маркер клиента.
- Тексты UI/логов/HTML title: `ABClient` -> `ANClient`, `ABCLIENT_*` -> `ANCLIENT_*`.

## Что не менять автоматически

- Слова, где `ab` является частью предметной функции или английского слова, а не названием клиента: `AutoBoi`, `AutoBattle`, `ability`, `table`, `stabilize`, `disable`, `abstract`, `label`, `available`, `cabinet` и подобные.
- Внешние игровые URL, если сервер реально ожидает старый путь и нет подтверждения нового endpoint. Отдельно проверять `modules/abclient/auth.php`: это может быть внешний серверный контракт, а не локальное имя клиента.
- Сторонние имена/бренды/служебные GUID.
- Старые build outputs в `bin/` и `obj/` не использовать как источник истины; при необходимости чистить или переименовывать только проектные ссылки.

## Найденные текущие маркеры

- `ABClient10.sln` с project path `ABClient\ABClient.csproj`.
- `ABClient/ABClient.csproj` и `ABClient/ABClient10.csproj`: `RootNamespace`, `AssemblyName`, `StartupObject`, `ApplicationIcon`, include-пути `ABForms/*`, `ABProxy/*`, `ExtMap/AbcCell.cs`.
- C# namespaces/imports: `namespace ABClient...`, `using ABClient...`, short `using ABForms`, `using ABProxy`.
- UI/version strings: `VersionClass("ABClient", ...)`, `AssemblyTitle("ABClient")`, сообщения `Обновите версию ABClient!`.
- Серверные/диагностические маркеры: `UserKeyAbclientServerError = "!ABCLIENT"`, `ANCLIENT`-аналог нужен для локальной диагностики.
- Ресурсы: `Properties/Resources.resx` содержит `ABClientIcon` и `..\ABClientIcon.ico`.
- Данные/карта: `abcells.xml`, `abthings.xml`, `abteleports.xml`, `abfavorites.xml`, `abcache`; в корне уже существуют `ancells.xml`, `anthings.xml`, `anteleports.xml`, `anfavorites.xml`, `ancache`.
- Карта: `AbcCell`, `Map.AbcCells`.
- HTTP User-Agent: есть `httpWebRequest.UserAgent = "ABClient"`; после переименования нельзя оставлять клиентский маркер в User-Agent. Требуется браузерный UA или уже существующая браузерная константа.

## План реализации

- [x] Провести первичную инвентаризацию `ABClient`/`ABC`/`ab*` в ПК-коде и сверить подход с `app2`.
- [x] Переименовать solution/project/folder paths: `ABClient` -> `ANClient`.
- [x] Переименовать namespaces/usings/root namespace/assembly/startup object: `ABClient` -> `ANClient`.
- [x] Переименовать `ABForms` -> `ANForms`, `ABProxy` -> `ANProxy` в папках, namespace и project includes.
- [x] Переименовать клиентские файлы данных и ссылки: `abcells`/`abthings`/`abteleports`/`abfavorites`/`abcache` -> `ancells`/`anthings`/`anteleports`/`anfavorites`/`ancache`.
- [x] Переименовать `AbcCell`/`AbcCells` -> `AncCell`/`AncCells`.
- [x] Переименовать ресурсы/icon/library references: `ABClientIcon` -> `ANClientIcon`, проверить `ABFastRx.dll`.
- [x] Убрать клиентский `ABClient` из User-Agent и заменить на браузерный UA.
- [x] Проверить остаточные `ABClient`/`ABC`/`ABForms`/`ABProxy`/`abcells`/`abthings`/`abteleports`/`abcache` в ПК-версии.
- [x] Проверить mojibake-паттерны в изменённых файлах.
- [x] Запустить доступную сборочную проверку и зафиксировать известные ограничения окружения.

## Риски и контроль

- Массовый replace `ab` запрещён: высокий риск сломать обычные слова и функциональные имена.
- Внешний endpoint `modules/abclient/auth.php` требует отдельной проверки: если сервер не имеет `modules/anclient`, менять путь нельзя без подтверждения.
- `bin/` и `obj/` могут содержать старые артефакты `ABClient`; они не должны блокировать исходный refactor, но не должны случайно попадать как ручные изменения.
- После переименования нужно проверить `.sln`, `.csproj`, `.resx`, generated resource references и WinForms designer dependent paths.

## Текущее состояние проверки

- В tracked текстовых файлах `ANClient/` вне `bin/` и `obj/` не найдено остаточных клиентских маркеров по whitelist-паттернам `ABClient`/`ABForms`/`ABProxy`/`AbcCell`/`abcells`/`abthings`/`abteleports`/`abcache`/`abmap`/`abcmapwidth`.
- В `bin/` и `obj/` остаются старые build artifacts с `ABClient`; они не являются источником истины и должны пересоздаваться сборкой.
- `ANClient/Resources/ANFastRx.dll` переименован как файл, но бинарно всё ещё содержит старую строку `ABFast`; без исходников DLL это фиксируется как ограничение.
- `ANClient/index.html` содержит внешний URL `https://github.com/wmlabtx/abclient/wiki`; не менялось без подтверждения существования нового внешнего wiki URL.
- Дополнительно переименован внутренний контур карты `AbcMap` -> `AncMap` в методах, константах и JS-переменных; XML-значения атрибутов (`regnum`, `cost`, `label`) оставлены совместимыми.
- `git diff --check` не проходит из-за уже существующих trailing spaces в сгенерированных/данных файлах, которые попали в diff из-за rename, и из-за warning `.gitattributes" is not a valid attribute name: .gitattributes:7`.
- Mojibake-проверка diff показывает старые data/html участки с нечитаемой кириллицей (`anthings.xml`, `index.html`); это отдельная encoding-cleanup задача, не смешивалась с rename-логикой.
- `MSBuild.exe ANClient\ANClient.csproj /t:CoreCompile /p:Configuration=Debug` запущен; сборка не проходит на локальном .NET4/MSBuild из-за C# 6 `$` interpolation/newer syntax в существующем коде, не из-за текущего rename.
- VS2022 BuildTools `MSBuild.exe ANClient.csproj /t:Build /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir=bin\VerifyWork\` после восстановления повреждённых `.resx` base64-потоков проходит успешно: 0 warnings, 0 errors.
- Причина ошибки `FormMain.resx : MSB3103 Invalid Resx file / Не удалось загрузить ImageList`: blind-replace `AB` -> `AN` попал внутрь base64 `ImageListStreamer` (`AQAB...` -> `AQAN...`), что повредило сериализованный `ImageList`.

## Анализ `D:\IBC2` на отсутствующие функции

- [x] Проверены прямые совпадения в `D:\IBC2` по `Turotor`, `Treasure`, `CastleBuff`, `DwarfCraft`, `mineMoveTo`, `getMineCellHTML`, `Казн`, `Арты`, `Рары`, `Гибл`, `топь`.
- [x] Найдены формы-заготовки `D:\IBC2\ABClient.MyForms\FormTurotor.cs` и `D:\IBC2\ABClient.MyForms\FormTreasure.cs`, но тела обработчиков пустые из-за protected/runtime-stub decompile.
- [x] Найдены новые настройки профиля в `D:\IBC2\Fv2WJm81JhH8CRlcvjB\sl7XIJ89X3GijIiML1i.cs`: `TurotorToGo`, `TurotorToGoSecondary`, `TurotorIntervalStart`, `TurotorIntevalEnd`, `CastleBuffTeleportId`, `CastleBuffCastleCell`, `CastleBuffBackToCell`, `CastleBuffEffects`, `DNVNVLimit`, `DNVFrequency`.
- [x] Найдены новые COM/JS bridge-методы в `D:\IBC2\ABClient\ScriptManager.cs`: `GetItemID`, `GetItemVcode`, `GetItemPrice`, `getCellImg`, `mineMoveTo`, `getMineCellHTML`, `getMoveText`, `DwarfCraftSetCategory`, `DwarfCraftGetCategory`, `DwarfCraftSetId`, `DwarfCraftGetId`, `GetFortBuffsState`, `LeaveFort`, `MoveToFort`, `TellBuffTaken`, `SetTirednessToMax`, `ThrowAwayUselessRubbish`, `SaveCastleBuffEffects`, `GetCastleBuffState`, `GetCastleBuffEffects`, `SetCastleBuffState`.
- [x] Проверен `ANClient`: есть только быстрый телепорт `Телепорт (Остров Туротор)` через `MainPhpFastIsland`, `FormMainFast.FastAttackIslandPot`, `FormMainNavigator` и флаг маршрута `MapPath.IsIslandRequired`.
- [x] Проверен `ANClient`: нет `FormTurotor`, `FormTreasure`, профильных настроек `Turotor*`/`CastleBuff*`, bridge-методов `DwarfCraft*`/mine/fort/castle/tiredness и ресурсов `castle_v05`, `dwarfshop_v01`, `lottery_v01`, `mine`, `outpost_v02`.
- [x] Установлен `ilspycmd` во временную папку `C:\Users\User\AppData\Local\Temp\opencode\tools` и выполнен decompile `D:\IBC2\iBClient_BD_deobf.exe` в `C:\Users\User\AppData\Local\Temp\opencode\IBC2_ilspy` без изменения репозитория.
- [x] `ilspycmd` подтвердил блокер: `FormTurotor`, `FormTreasure` и критичные методы `ScriptManager` остались runtime-stub; реальная логика находится в encrypted resources/protector, обычный decompile её не раскрывает.
- [ ] Для восстановления реальной логики нужен отдельный разрешённый шаг: runtime-unpack/запуск protected loader или другой unpacker. Без этого можно портировать только UI-настройки и stubs, но нельзя достоверно восстановить endpoints и state-machine `Туротор`/`Гиблая топь`/казна.
