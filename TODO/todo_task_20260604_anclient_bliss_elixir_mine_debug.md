# Задача: отладка автопитья Эликсира Блаженства в шахте ANClient

## Симптом

- Во время движения шахтёрского навигатора не пьётся `Эликсир Блаженства`, когда усталость подходит к настроенному порогу.

## Контекст

- Проверять свежие логи из `ANClient/bin/Debug/Logs/Critical/`.
- Не создавать параллельный контур автопитья, если есть существующая точка принятия решения.
- Сохранить работу шахтёрского навигатора и предыдущие фиксы route/touch/floor-exit.

## План диагностики

- [x] Найти свежие Critical-логи по автопитью, усталости, fast-action и auto-mine route.
- [x] Сопоставить порядок событий: движение по маршруту, проверка усталости, решение пить/не пить, запрос использования эликсира.
- [x] Найти существующую реализацию автопитья Эликсира Блаженства и её guard-условия.
- [x] Определить, блокирует ли автопитьё `FastNeed`, `AutoMoving`, pending mine route, cooldown, недоступный item/link или порядок обработки `MainPhpAutoMine`.
- [x] Внести минимальную правку в существующий контур, если root cause подтверждён кодом и логами.
- [x] Собрать `ANClient.csproj` и проверить BOM/mojibake/diff.

## Наблюдения

- `20260604_14_00_mainphp.log`: автопитьё сработало до длительного маршрута: `auto-drink blaz triggered`, затем переходы `im=0&wca=27` -> `im=6`, затем `auto-drink blaz submitted`.
- `20260604_14_20_auto_mine_trace.log`: во время pending route есть повторяющиеся `snapshot ... pending=3/3`, `route accepted move ...`, `SetNeverTimer 40000`.
- `20260604_14_20_mainphp.log`: на шахтёрских `main.php` страницах есть только `processing complete`, без `ins_HP found`, `tied parsed`, `auto-drink blaz triggered`.
- В `MainPhp.cs` вызов `MainPhpAutoMine(address, html)` стоял до `MainPhpTied(html)` и до блока AutoDrinkBlaz. При pending route `MainPhpAutoMine` возвращал route-injection и делал `goto end`, поэтому усталость не парсилась и автопитьё не получало шанс выполниться.
- Дополнительный guard: исходный AutoDrinkBlaz требовал `DateTime.Now > AppVars.NeverTimer`; во время самого шага шахты `NeverTimer` выставляется JS-таймером на 30-40 секунд. Значит пить нужно между шагами, когда таймер уже истёк, до инъекции следующего route step.

## Реализация

- В `MainPhp.cs` существующая логика AutoDrinkBlaz вынесена в `MainPhpTryAutoDrinkBlaz(...)` без создания нового контура.
- При `AutoMineRuntime.HasPendingMineRoute()` перед `MainPhpAutoMine(...)` теперь выполняется `MainPhpTied(html)` и попытка `MainPhpTryAutoDrinkBlaz(...)`.
- Если `NeverTimer` ещё активен, логируется `auto-drink blaz waits NeverTimer during mine route` в `auto_mine_trace`.
- В `MainPhpTied.cs` `AppVars.Tied` обновляется синхронно до UI `BeginInvoke`, чтобы ранний decision point видел только что распарсенное значение.

## Проверки

- `ANClient.csproj`: сборка успешна, `0 warnings`, `0 errors`.
- BOM: `BOM_OK` для затронутых файлов.
- Mojibake в focused diff: `MOJIBAKE_DIFF_OK`.
- Focused `git diff --check`: exit `0`; остались только известные предупреждения `.gitattributes`/CRLF.
