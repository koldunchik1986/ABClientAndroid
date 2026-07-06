# Задача 2026-05-22: PC ANClient cleanup `Бесполезный хлам`

## Контекст

- Пользователь отказался от очереди ручной добычи в шахте: оставляем только безопасные кнопки `Ваш персонаж` и `Инвентарь` на шахтной странице.
- Нужно попробовать тем же безопасным способом открытия инвентаря довести выбрасывание `Бесполезный хлам` для `Авто-Травник`/`Авто-Лесоруб`.
- Изменения относятся к PC `ANClient/`; ранее пользователь явно подтвердил правки этой папки для текущего контура.

## Найденный существующий контур

- `AutoCutRuntime.RequestGarbageCleanupAfterCut(...)` выставляет `AppVars.AutoCutCleanupPending` и `AppVars.BulkDropThing = "Бесполезный хлам"`.
- `MainPhpAutoCutCleanupRedirect(...)` открывает инвентарь через `MainPhpFindInv(html, "&im=0")`, то есть через реальную ссылку `go=inv&vcode`, если она есть в HTML.
- `MainPhpInv(...)` парсит `InvEntry.DropLink` и выполняет штатный bulk-drop через `BuildRedirect(...)`.
- Проблемная зона: fallback `main.php?im=0` и коррекция inventory-адреса не учитывают категорию `wca=60`, где находится полный список `Бесполезный хлам`.

## План

- [x] Убрать очередь ручной `Начать добычу` и саму кнопку, оставив `Ваш персонаж`/`Инвентарь`.
- [x] Усилить `MainPhpFindInv(...)`, чтобы он надежнее вытаскивал `vcode` из menu-array формата `['inv','Инвентарь','...']` / `["inv","Инвентарь","..."]`.
- [x] Для активного garbage-cleanup после входа в инвентарь переключать категорию на `main.php?wca=60` перед поиском `DropLink`.
- [x] Разрешить `wca=60` как валидный cleanup-inventory адрес для `Бесполезный хлам`, чтобы корректор не возвращал страницу обратно на `im=0`.
- [x] Проверить сборку MSBuild и targeted checks: BOM=false, нет mojibake, нет новых старых runtime-маркеров.

## Проверка 2026-05-22

- [x] `MSBuild ANClient.csproj /t:Rebuild /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir=bin\Debug\` успешно: 0 warnings, 0 errors.
- [x] Targeted checks по `MineJs.cs`, `MainPhp.cs`, `MainPhpDrink.cs`, этому TODO: BOM=false, mojibake-паттернов нет.
- [x] В затронутых `.cs` нет новых `ABCLIENT`/`ABC`/`ab_`, `android.util.Log`, `AppVars.VCode`.
- [-] `git diff --check` по целевым файлам снова упирается в существующую ошибку `.gitattributes:7` и CRLF warnings; новых whitespace errors по содержимому файлов не показано.

## Live-проверка

- [ ] При `Авто-Травник`/`Авто-Лесоруб` после найденного `Бесполезный хлам` ожидаются логи `garbage cleanup requested`, затем `cleanup inventory redirect via link`, `garbage cleanup switch to quest inventory category`, `garbage bulk-drop redirect`, `garbage bulk-drop completed`.
- [ ] На шахтной странице должны остаться кнопки `Ваш персонаж`/`Инвентарь`; `Начать добычу` больше не добавляется клиентским патчем.
