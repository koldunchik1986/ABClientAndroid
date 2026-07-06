# Задача: приоритет АвтоБоя над АвтоШахтёром

## Проблема

- В `ANClient\bin\Debug\Logs\Critical\20260604_11_50_auto_mine_trace.log` видно, что АвтоШахтёр уходит в ожидание факела и вызывает `main.php` по кругу.
- В `20260604_11_50_mainphp.log` после перехода на `main.php?im=0&wca=4&an_auto_mine_torch=1` обработка завершается без входа в `MainPhpFight`.
- Причина в порядке `MainPhp.cs`: `MainPhpAutoMine(address, html)` вызывается до проверки `magic_slots();`, поэтому страница боя может быть перехвачена шахтёрским/инвентарным контуром.

## Найденный существующий контур

- Боевой decision point уже есть в `ANClient\PostFilter\MainPhp.cs`: проверка `magic_slots();` и вызов `MainPhpFight(html)`.
- АвтоШахтёр уже централизован в `ANClient\PostFilter\MainPhpAutoMine.cs` и `ANClient\Mine\AutoMineRuntime.cs`.
- Исправление делается переносом существующего fight-check выше, а не добавлением нового параллельного обработчика.

## План

- [x] Проверить Critical logs по `auto_mine_trace`, `mainphp`, `lezfight`.
- [x] Найти порядок вызовов `MainPhpAutoMine` и `MainPhpFight`.
- [x] Перенести существующую проверку боя выше авто-проверок, чтобы АвтоБой имел приоритет.
- [x] Собрать `ANClient.csproj`.
- [x] Проверить BOM/mojibake/diff.

## Реализация

- В `ANClient\PostFilter\MainPhp.cs` проверка `magic_slots();` перенесена сразу после `UnderAttack.Parse(html)`.
- При обнаружении боя обработчик сразу вызывает существующий `MainPhpFight(html)` и уходит в `end`, поэтому AutoMine/Inventory/Rob/StopOnDig не перехватывают боевую страницу.
- Отдельный guard в `MainPhpAutoMine.cs` не добавлялся: существующий decision point боя теперь находится раньше конкурирующих авто-контуров.

## Проверки

- `MSBuild.exe ANClient.csproj /t:Build /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir=bin\Debug\` — успешно, 0 warnings, 0 errors.
- `git diff --check` по затронутому C# файлу — без новых whitespace-ошибок; остались существующие предупреждения `.gitattributes` и CRLF.
- BOM по `MainPhp.cs` и этому TODO-файлу — `False`.
- Mojibake-проверка затронутых файлов — clean.
