# NeverTimer: открытие `Ваш персонаж` / `Инвентарь` во время таймера

## Назначение

Документ фиксирует подтвержденный феномен в ПК-версии `ANClient`: при активном серверном `NeverTimer` шахтная страница может не давать открыть штатные кнопки `Ваш персонаж` и `Инвентарь`, хотя безопасные переходы `go=inf` и `go=inv` с корректным `vcode` должны работать как обычная навигация.

Цель инструкции: быстро отличать допустимую навигацию во время `NeverTimer` от запрещенных серверных действий, не создавая параллельные HTTP-контуры и не подменяя `vcode` из другого контекста.

## Подтвержденные факты

- По логам ПК `ANClient/bin/Debug/Logs/Critical/20260522_11_50_*` подтверждено: при активном таймере `SetNeverTimer 156000` штатный сетевой контур не был причиной блокировки переходов `main.php?get_id=56&act=10&go=inv&wca=4` и `get_id=57...` для надевания `Дварфийский фонарь`.
- Блокировка была на уровне UI/серверного `mine.js`: `disable_move` и `time_left` держали кнопки недоступными, поэтому пользователь не мог добраться до обычных переходов.
- По логам `ANClient/bin/Debug/Logs/Critical/20260522_12_20_*` подтверждено: независимые кнопки `Ваш персонаж` и `Инвентарь` срабатывают во время активного таймера, логируя `manual menu open ... during timer=true`.
- По логам `ANClient/bin/Debug/Logs/Critical/20260522_12_40_*` подтверждено: ручная `Начать добычу` при активном таймере отправляла `Digg(code)` слишком рано, сервер отвечал `Вы не можете сейчас начать добычу.`.
- Ошибка ранней добычи не лечится подменой `vcode` из инвентаря: это именно server cooldown, а не проблема защитного кода.

## Текущие code decision points

- `ANClient/PostFilter/MineJs.cs` инжектит шахтный JS patch и является основной точкой исправления UI/тайминга шахты.
- `MineJs.cs` оборачивает `Timer.start(sec)` и вызывает `window.external.SetNeverTimer(ms)`. Значение `sec` из серверного JS переводится в миллисекунды через `timerMs(...)`.
- `ANClient/ScriptManager.cs` метод `SetNeverTimer(int msec)` сохраняет общий `AppVars.NeverTimer = DateTime.Now.AddMilliseconds(msec)`.
- `ANClient/Mine/AutoMineRuntime.cs` метод `GetAutoDigWaitMs()` возвращает максимум из собственного `nextAutoDigAllowedAt` и общего `AppVars.NeverTimer`.
- `MineJs.cs` метод `__ancTryMineDig(...)` обязан ждать `getMineAutoDigWaitMs()`: если wait больше нуля, логируется `Digg waits timer ...`, ставится one-shot `scheduleAutoDig(...)`, ранний `Digg(code)` не отправляется.
- `MineJs.cs` метод `routeWait()` смотрит на серверные JS-поля `time_left`, `disable_move`, `moving_status`; это ожидание относится к шахтному движению, а не к безопасному переходу в персонажа или инвентарь.
- `MineJs.cs` метод `addMineMenuButton(...)` добавляет независимые кнопки в существующий `ButtonPlace`, берет `vcode` из server menu arrays через `findMenuCode(...)` и выполняет обычный `window.location` на `go=inf` или `go=inv`.

## Правильная модель поведения

- `NeverTimer` означает серверный cooldown для действий: добыча, перемещение, `Оглядеться`, удаление/проверка после server action и похожие операции должны ждать актуальный таймер.
- `NeverTimer` не должен считаться универсальным запретом на ручную навигацию, если сервер уже отдал валидную ссылку `go=inf` или `go=inv` с `vcode`.
- `Ваш персонаж` и `Инвентарь` в шахте являются safe-navigation кнопками: они не добывают ресурс, не двигают персонажа и не должны зависеть от `disable_move/time_left` серверного UI.
- `Начать добычу` не является safe-navigation: при активном таймере ранний `Digg(code)` должен откладываться, иначе сервер возвращает `Вы не можете сейчас начать добычу.`.
- Авто-ход и авто-добыча должны продолжать ждать `NeverTimer` через текущие `routeWait()` и `getMineAutoDigWaitMs()` контуры.

## Что проверять при похожем баге

1. Проверить, где выставлен `NeverTimer`.

Искать маркеры `SetNeverTimer`, `Timer.start`, `DIGG@`, `time_left`, `disable_move`, `moving_status`.

2. Проверить тип действия.

Если это `go=inf` или `go=inv` по готовой ссылке из HTML, это навигация. Если это `Digg(code)`, `move_tunnels(dir)`, `alchemy_ajax.php?act=1`, `get_id=50` или другой action, он должен ждать server cooldown.

3. Проверить источник `vcode`.

Для шахтных кнопок `Ваш персонаж` и `Инвентарь` использовать только `vcode` из загруженных server menu arrays текущей страницы. Не брать `vcode` из инвентаря, профиля или старого HTML.

4. Проверить, не создали ли второй контур.

Фикс должен оставаться в существующих точках `MineJs.cs`, `ScriptManager.cs`, `AutoMineRuntime.cs`, `MainPhp.cs`, `MainPhpInv.cs` или общем AutoCut runtime. Не добавлять отдельный raw HTTP-клиент для обхода UI.

5. Проверить HTML результата.

Для AutoCut cleanup переход `go=inv` во время cooldown может вернуть HTML без inventory rows. Это не считается успешным инвентарем: нужно ждать текущий retry/cleanup-контур и подтверждать наличие реального inventory HTML перед `BulkDropThing` completion.

## Диагностические маркеры

- `SetNeverTimer <ms> source=Timer.start` - шахтный серверный таймер попал в `AppVars.NeverTimer`.
- `manual menu open inf during timer=true` - пользователь открыл `Ваш персонаж` во время активного `routeWait()`; это допустимое поведение.
- `manual menu open inv during timer=true` - пользователь открыл `Инвентарь` во время активного `routeWait()`; это допустимое поведение.
- `Digg waits timer <ms> source=...` - авто-добыча корректно отложена до окончания cooldown.
- `auto dig approved: source=bridge` - bridge разрешил отправку `Digg(code)` после проверок.
- `server rejected dig during cooldown` - `Digg(code)` ушел слишком рано; нужно искать обход `getMineAutoDigWaitMs()` или устаревший manual path.
- `cleanup inventory redirect via link` - AutoCut cleanup использовал реальную ссылку инвентаря из HTML.
- `garbage cleanup switch to quest inventory category` - cleanup переключился на категорию `wca=60` для `Бесполезный хлам`.
- `garbage bulk-drop redirect` - `InventoryParser` нашел `InvEntry.DropLink` и начал штатное удаление.
- `garbage bulk-drop completed` - cleanup завершился после реального inventory pass.

## Анти-регрессия

- Не удалять независимые кнопки `Ваш персонаж` и `Инвентарь` из `ButtonPlace` без равноценной замены.
- Не завязывать safe-navigation `go=inf/go=inv` на `routeWait()`, `autoWait()` или `AppVars.NeverTimer`.
- Не отправлять `Digg(code)` при `getMineAutoDigWaitMs() > 0`.
- Не использовать `go=inv` как доказательство, что открыт реальный инвентарь: проверять HTML/rows и штатный `InvEntry.DropLink`.
- Не подменять `vcode` между контекстами.
- Не создавать новый HTTP/drop/inventory контур, если текущий `MainPhp`/`MainPhpInv`/`AutoCutRuntime` уже покрывает задачу.
- Не очищать и не игнорировать общий `AppVars.NeverTimer` ради одного модуля: если нужен более короткий retry, хранить модульный due-time и будить модуль через существующий dispatcher.

## Быстрый чеклист проверки

- HTML-кнопки `Ваш персонаж` и `Инвентарь` видны в шахте и оформлены независимо от server disabled state.
- При активном `NeverTimer` клик по `Ваш персонаж` строит `main.php?get_id=56&act=10&go=inf&vcode=...` и логирует `manual menu open inf during timer=true`.
- При активном `NeverTimer` клик по `Инвентарь` строит `main.php?get_id=56&act=10&go=inv&vcode=...` и логирует `manual menu open inv during timer=true`.
- Авто-добыча при активном cooldown логирует `Digg waits timer ...`, а не `server rejected dig during cooldown`.
- После `DIGG@...@480` следующий auto-dig планируется не раньше `480 + 2` секунд.
- AutoCut cleanup не завершает `Бесполезный хлам` по пустому inventory-like HTML без rows.
- В логах нет циклов `go=inf/go=inv` каждые 250-500 ms во время cleanup.

## Вывод

Открытие `Ваш персонаж` и `Инвентарь` во время активного `NeverTimer` в шахте является ожидаемым safe-navigation поведением, если ссылка взята из текущего server HTML и содержит актуальный `vcode`. Ошибкой является не сам переход, а ранняя отправка серверного действия во время cooldown или ложное завершение автоматического cleanup по неполному HTML.
