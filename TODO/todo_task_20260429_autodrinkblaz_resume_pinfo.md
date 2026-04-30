# Задача 2026-04-29: AutoDrinkBlaz, pinfo и возврат Auto-Травника

## Цель

- [x] Проверить свежие логи после прогона AutoDrinkBlaz.
- [x] Сверить `UseElik.har` с текущим способом использования `Эликсир Блаженства`.
- [x] Исправить автоматическое обновление усталости без ручного открытия `pinfo.cgi`.
- [x] После питья блажа возвращать поток в предыдущее действие Auto-Травника/навигации.
- [x] Не запускать новый круг Auto-Травника по клеткам, где в текущей смене нет выбранной травы.
- [x] Проверить сборку `ABClient.csproj` в `ABClient/bin/VerifyWork/`.
- [x] Проверить UTF-8/BOM и mojibake-паттерны.

## Наблюдения

- `NeverApi.TryGetTiedFromPInfo(...)` в логах `20260429_11_40_*` и `20260429_11_50_*` возвращал `EMPTY_RESPONSE`, тогда как ручное открытие `pinfo.cgi` через браузерный поток успешно запускало `Pinfo(...)` и обновляло `UpdateTied` до `82%`, `84%`, затем `0%`.
- `UseElik.har` подтверждает штатный GET для эликсира: `main.php?get_id=43&act=107&uid=...&curs=...&subid=0&ft=0&vcode=...`, ответ остаётся HTML инвентаря без редиректа обратно в природу.
- После успешного питья текущий C# поток устанавливал `AppVars.Tied = 0`, но оставался на странице инвентаря, поэтому Auto-Травник не получал следующий map/`Ogl` контекст до ручного возврата.
- `AutoCutRuntime.RouteNextCell(...)` при полностью проверенных CSV-клетках очищал `CheckedCells` и начинал `new_circle` в той же смене. Это повторно гоняло Авто-Травник по клеткам, где выбранная трава в текущей смене не растёт.
- По логам `20260429_12_10_auto_cut_trace.log` и `20260429_12_20_auto_cut_trace.log` клетка `12-224` была выбрана как `reason=unchecked`, хотя её `RESO@` содержал только невыбранные травы (`Петрушка`, `Чеснок`, перцы, `Сельдерей`) и ни одной выбранной (`Лён`, `Пшеница`, `Сахарный тростник`). Следом так же была выбрана `12-254` с `Чеснок`/`Сельдерей`.

## Решение

- [x] Исправить `CookieAwareWebClient`: добавить перенос cookies из `CookiesManager.Obtain(...)` в `CookieContainer`, чтобы фоновые `NeverApi.GetInfo(...)` запросы отправляли игровые cookies как браузерный поток.
- [x] Исправить `NeverApi.GetInfo(...)`: передавать cookies через `SetCookies(...)`, добавить браузерный `Accept-Language`/`Cache-Control` и диагностировать пустое тело.
- [x] Исправить `MainPhp.cs`: после успешного submit AutoDrinkBlaz выставлять существующий `AppVars.SwitchToFlora = true`, чтобы следующий ответ инвентаря вернул `main_top` в природу и Auto-Травник/навигация продолжили работу.
- [x] Исправить `AutoCutRuntime`: `OnScanWithoutSelectedHerb(...)` помечает текущую клетку проверенной до смены, а `RouteNextCell(...)` больше не очищает `CheckedCells` при завершении круга. Если все клетки проверены и нет due herb timer, ставится `look retry` на начало следующей смены.
- [x] `ABClient.csproj` собран через VS MSBuild 2022 в `ABClient/bin/VerifyWork/`: `Build succeeded`, 0 warnings, 0 errors.
- [x] `git diff --check` по релевантным файлам не выявил новых whitespace-ошибок; остался только известный шум `.gitattributes:7` и LF/CRLF warnings.
- [x] BOM-проверка релевантных `.cs`/`.md` файлов: BOM не найден.
- [x] Mojibake-проверка diff по `РЎР|РџС|Ð|Ñ`: совпадений нет. Единственный `????` в diff относится к ожидаемому captcha placeholder в TODO.
- [x] Исправить выбор следующей клетки: `AutoCutRuntime` теперь проверяет `HerbCells` cache перед `MoveToSafe(...)` и пропускает кандидатов без выбранных трав (`no_selected_herbs_in_cell_cache`) или с выбранными травами `0` в текущей смене (`selected_herbs_empty_current_shift`). Неизвестные клетки и stale-cache клетки остаются допустимыми для первичной проверки.
- [x] Повторная сборка `ABClient.csproj` через VS MSBuild 2022 в `ABClient/bin/VerifyWork/`: `Build succeeded`, 0 warnings, 0 errors.
- [x] Повторные проверки после фикса `12-224`: `git diff --check` без новых whitespace-ошибок; `HerbCell.cs` без BOM; mojibake-паттерны в diff не найдены.
- [x] Исправить остановку после `act1: no resource state`: `AlchemyAjaxPhp.ProcessAlchemyAct1(...)` теперь для non-`ERR` пустого/неразобранного resource state вызывает существующий `AutoCutRuntime.OnScanWithoutSelectedHerb("alchemy_act1_no_resource_state")`, чтобы клетка помечалась checked и маршрут шёл дальше. `ERR` оставлен recoverable retry без отметки checked.
- [x] Финальная сборка после фикса `act1: no resource state`: `ABClient.csproj` собран через VS MSBuild 2022 в `ABClient/bin/VerifyWork/`, 0 warnings, 0 errors.
- [x] Финальные проверки после фикса `act1: no resource state`: `git diff --check` без новых whitespace-ошибок; релевантные `.cs`/`.md` без BOM; mojibake в `AlchemyAjaxPhp.cs` не найден. Совпадения в TODO относятся только к строкам, где записан сам шаблон проверки.
- [x] По свежим логам `20260429_13_40_auto_cut_trace.log` и `20260429_13_50_auto_cut_trace.log` подтверждено: маршрут пропускал `12-314`, `12-311`, `12-339`, `12-370` по старому `HerbCells` cache (`no_selected_herbs_in_cell_cache`) после новой смены, до первичного обновления этих клеток.
- [x] Исправление внесено в существующий decision point `AutoCutRuntime.GetUncheckedCellSkipReason(...)`: cache-skip применяется только если `HerbCell.UpdatedInTicks` относится к текущей смене. Stale-cache клетки снова считаются непроверенными и попадают в маршрут для обновления через обычный `alchemy_ajax.php?act=1`.
- [x] Проверочная сборка после stale-cache фикса: `ABClient.csproj` собран через VS MSBuild 2022 в `ABClient/bin/VerifyWork/`, 0 warnings, 0 errors.
- [x] Проверки после stale-cache фикса: `git diff --check` без новых whitespace-ошибок; `HerbCell.cs` без BOM; mojibake-паттерны в `HerbCell.cs` не найдены.
- [x] По свежему `ABClient/bin/VerifyWork/Logs/Critical/20260429_14_00_auto_cut_trace.log` найден стоп после нажатия `Автотравник`: после `sickle checked: return to flora before auto look` текущая клетка `12-341` получала `route skip cell: reason=selected_herbs_empty_current_shift, source=current_cell`, но bridge `DoHerbAutoCut()` возвращал `false` без запуска маршрута на следующую CSV-клетку.
- [x] Исправление внесено в существующий bridge/route decision point без нового HTTP-контура: `FormMainHerbs.DoHerbAutoCut()` теперь при cache-skip текущей unchecked CSV-клетки вызывает `AutoCutRuntime.RouteNextIfCurrentCellCachedNotReady(...)`, а тот делегирует в существующий `RouteNextCell(...)`/`MoveToSafe(...)`.
- [x] Проверочная сборка после bridge route-next фикса: `ABClient.csproj` собран через VS MSBuild 2022 в `ABClient/bin/VerifyWork/`, 0 warnings, 0 errors.
- [x] Проверки после bridge route-next фикса: релевантные файлы без BOM; mojibake-паттерны в изменённых C#/app2-файлах не найдены; `git diff --check` без новых whitespace-ошибок, только известное предупреждение `.gitattributes:7` и LF/CRLF warnings.
