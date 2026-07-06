# Задача 2026-05-25: кнопка `ТП` в ANClient для обычного телепорта

## Контекст

- Пользователь попросил перенести из `app2` в ПК `ANClient` быструю функцию обычного телепорта, не `Телепорт (Туратор)`.
- Кнопка должна называться `ТП`, стоять перед `Авторыбалка` и иметь выпадающий список локаций.
- При выборе локации должен использоваться `Свиток телепорта` с нужным `wtelid`.

## Анализ app2

- UI в `app2/src/main/java/ru/neverlands/anclient/ui/QuickButtonsPanel.java`: `showTeleportQuickActionDialog()` получает список через `FastActionManager.getTeleportDestinations()` и запускает `FastActionManager.fastAttackTeleportToDestination(destination.getId(), destination.getName())`.
- Логика в `FastActionManager.java`: `fastAttackTeleportToDestination(...)` сохраняет выбранный id/name и запускает существующий fast-action `fastStart("i_w28_22.gif", "")`.
- Парсер `mainPhpFastTeleport(...)` ищет `w28_form(..., wsubid=22, ...)` и формирует POST `main.php` с `post_id=25`, `wsubid=22`, `wsolid`, `wuid`, `vcode` и выбранным `wtelid`.
- Список обычных пунктов телепорта: 1-12 (`Город Форпост`, `Город Октал`, `Деревня Подгорная`, окрестности и т.д.). `wtelid=13` относится к островному/спецтелепорту и не входит в текущую задачу.

## Анализ ANClient

- В ПК уже есть existing contour: `FormMain.FastStartSafe("i_w28_22.gif", ...)` -> `MainPhp` -> `MainPhpFastTeleport(...)`.
- `MainPhpFastTeleport(...)` уже корректно парсит `w28_form` для `wsubid=22`, но сейчас выбирает `wtelid` случайно через `Dice.Make(12) + 1`.
- В toolbar текущие авто-кнопки находятся в `FormMain.Designer.cs`; `buttonAutoFish` уже стоит после `toolStripSeparator6`.
- Правильная точка расширения UI - `FormMain.cs`, как уже сделано для динамических кнопок `Авто-Лесоруб` и `Авто-Шахтёр`.

## План реализации

- [x] Добавить в `AppVars` runtime-поля выбранного обычного телепорта и каталог локаций 1-12.
- [x] Добавить в `FormMain` динамическую `ToolStripDropDownButton` `buttonFastTeleport` с текстом `ТП`, вставить перед `buttonAutoFish`.
- [x] При выборе пункта из списка записывать выбранный `wtelid`/name, запускать `FastStartSafe("i_w28_22.gif", destinationName)` и открывать категорию свитков вне `NeverTimer`.
- [x] Сохранить старую команду `Телепорт` как случайную: перед запуском сбрасывать выбранный `wtelid`.
- [x] Изменить `MainPhpFastTeleport(...)`: если выбран `wtelid` 1-12, использовать его, иначе оставлять старый random fallback.
- [x] Сбросить выбранный `wtelid` в `FastCancelSafe()`.
- [x] Проверить сборку `ANClient` через MSBuild и targeted checks на UTF-8/BOM/mojibake.

## Проверка

- [x] `MSBuild.exe ANClient.csproj /t:Build /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir=bin\Debug\` завершился успешно: 0 warnings, 0 errors.
- [x] Изменённые `.cs`/`.md` файлы проверены на BOM: BOM не найден.
- [x] Изменённые `.cs`/`.md` файлы проверены на стандартные mojibake-паттерны: новых совпадений нет.
- [-] Live smoke-test требует активной игровой сессии: проверить кнопку `ТП`, список локаций 1-12 и отправку выбранного `wtelid` через `Свиток телепорта`.

## Отладка 2026-05-26: `ТП` не сработал из инвентаря при активном NeverTimer

- [x] Лог `ANClient/bin/Debug/Logs/Critical/20260525_23_50_mainphp.log` показал `FastNeed=true, FastId=i_w28_22.gif` в `23:57:09`, но без дальнейшего `MainPhpFastTeleport(...)`.
- [x] Root cause: новая кнопка ставила fast-action и делала обычный `ReloadMainFrame()`, а existing fast-action ветка в `MainPhp` полностью блокировалась условием `DateTime.Now > AppVars.NeverTimer`.
- [x] Исправление внесено в existing contour без нового HTTP-контура: `ТП`/старое меню телепорта открывают `main.php?im=0&wca=28` напрямую, а `MainPhp` разрешает только `i_w28_22.gif` открыть/обработать инвентарь вне `NeverTimer`.
- [x] Добавлены файловые диагностические маркеры `FastTeleport: open scroll inventory outside NeverTimer`, `FastTeleport: processing outside NeverTimer`, `FastTeleport: inventory redirect prepared outside main reload`, `FastTeleport: submit prepared`.
- [x] Повторная сборка `ANClient` через MSBuild успешна: 0 warnings, 0 errors.
- [x] Повторная проверка изменённых `.cs` файлов: BOM не найден, mojibake не найден.
- [-] Live-проверка: при активном `NeverTimer` нажать `ТП`, выбрать локацию, ожидаемые логи `open scroll inventory outside NeverTimer` -> `processing outside NeverTimer` -> `submit prepared`.

## Инварианты

- Не создавать второй HTTP-контур.
- Не трогать `ABClient/` и `IBClient/`.
- Не менять спецтелепорты `Телепорт (Остров Туротор)` и `Телепорт (Гиблая Топь)`.
- Не менять app2, он используется только как источник логики.
