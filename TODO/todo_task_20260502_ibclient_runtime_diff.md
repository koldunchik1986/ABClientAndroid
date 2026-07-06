# Задача 2026-05-02: runtime diff IBC vs ANClient vs Android

## Цель

Зафиксировать восстановленную IBC-логику `FormTurotor`, `FormTreasure` и item bridge `ScriptManager`, затем определить ближайшие точки портирования в Android без дублирования существующих quick-action/navigation/shop контуров.

## Статус

- [x] Прочитаны локальные правила: `AGENTS.MD`; `.cursor/rules/AI.txt`, `.cursor/rules/general rule.txt`, `.cursor/rules/UTF8.ps1` отсутствуют.
- [x] Прочитаны recovered файлы `FormTurotor.cs`, `FormTreasure.cs`, `ScriptManager.cs`.
- [x] Проверены существующие Android-контуры: island quick-action, `MapPath.isIslandRequired`, `ShopEntry`, `WebAppInterface`.
- [x] Созданы детальные analysis-файлы в `TODO/IBClient/`.
- [x] Создана runtime feature matrix в `IBClient/comparison/`.
- [x] Восстановлен string-decrypt слой в отдельную копию `IBClient/decompiled_runtime_v2_strings/`.
- [x] Созданы отчёты `string_table.tsv`, `string_references.tsv`, `method_string_hints.tsv`, `token_key_name_string_map.tsv`, `target_key_map_runtime_v2.md`.
- [x] Разобран exact HTML/JS contract клан-казны на уровне anchors: `main.php?wfo=1&useaction=clan-action&addid=3`, `СПИСОК ВЕЩЕЙ КЛАНА / СЕМЬИ`, `main.php?get_id=29&uid=...`, `item_bid(...)`, `k1HBZSBytl`.
- [x] Уточнен контракт `D28HZRpBoY(...)`: suffix после `uid=<itemId>` берется из текущего HTML до одинарной кавычки, а не задается константой; `k1HBZSBytl` хранит colon-separated очередь id из `complects.xml` (`MNlBVcP8Me`).
- [x] Уточнено, что `d9PBFLSwqa[0..2]` относится к лопатам auto-digging, а не к `Все`/`Рары`/`Арты`.
- [x] Уточнен mapping клан-казны: `gbBBllYF94`: `0=Все`, `1=Рары`, `2=Арты`.
- [x] Handler выбора `TurotorToGoSecondary` разобран: `Y2qPKQYd9ZDUjRRf7UA.LlfYfR769F()` проверяет текущий час внутри `TurotorIntervalStart` / `TurotorIntevalEnd`; secondary route используется внутри интервала, primary route вне интервала.
- [x] Зафиксировано, что `p@ssw0rd649MiOfNiBCl1enT` - auth TripleDES key, а не protector/unpack password; поэтому он не мог заменить runtime IL patching и string extraction.
- [x] Создан named-layer `IBClient/decompiled_runtime_v2_named/`: `1351` C# файлов, `22` form-name anchors, `851` control/field/property/container renames, `23` `InitializeComponent` method renames, `0` skipped conflicts.
- [x] `target_key_map_named.tsv` теперь прокидывает доказанные `realType`, `realMethod` и `outPath`; `23` method tokens получили `realMethod=InitializeComponent`.
- [x] Создан `IBClient/restored_project/` как единая директория восстановленных C# исходников с `.Designer.cs`; сборка не считается целью этого этапа.
- [x] Создан главный workflow `IBClient/scripts/restore_ibclient_project.ps1`; по умолчанию выполняет source restoration без build-debug, `-BuildRestored` только для отдельной будущей фазы.
- [x] `publish_restored_project.ps1` переключен на сохранение fidelity по умолчанию: compile-driven fixes доступны только через `-CompileFixes`.
- [x] `restore_named_project.ps1` уточнен: control-name renames берутся только из классифицированных WinForms `Name` setter delegates (`3` delegates), а designer `IContainer` fields восстанавливаются как `components`.
- [x] В общий restore workflow добавлен полный `delegate_proxy_map.tsv`: `1141` proxy fields, `1135` target methods resolved через protector `CeStL955nFL` map, `6` unresolved.
- [x] `restore_named_project.ps1` теперь восстанавливает `1141` delegate proxy class bodies из original decompile, чтобы collapsed ILSpy `delegate` файлы снова содержали `ApatIwcx50e` и static proxy fields.

## Найденные контуры

- Android island quick-action уже есть: `FastActionManager.fastAttackIslandPot()` и `mainPhpFastIsland(...)`.
- Android route-to-island уже есть: `MapPath.isIslandRequired` и `ISLAND_CELLS`.
- Android казна IBC-уровня не найдена: нет `ClanKazna*`, `gbBBllYF94`-аналога и direct take action `main.php?get_id=29&uid=...`.
- Android item bridge IBC-уровня не найден: нет `GetItemID`, `GetItemVcode`, `GetItemPrice` для recovered shop/market JS injections.
- Android shop sell flow есть, но это отдельный контур `ShopEntry`/`startBulkOldSell` и не заменяет IBC клан-казну.
- IBC `FormTreasure` оказался auto-digging кладов, а не клан-казной; портировать отдельно от казны/shop.

## Риски

- Базовый `decompiled_runtime_v2/` ещё содержит encrypted string calls, но отдельная копия `decompiled_runtime_v2_strings/` уже раскрывает runtime-строки и anchors.
- Named-layer не является deobfuscator control-flow: он безопасно переименовывает symbols по decrypted anchors, metadata maps, classified WinForms `Name` setters и designer/container anchors, без guess-renames; delegate proxy map уже выгружен отдельно для следующего deproxy/inlining шага.
- Build/debug `restored_project` выполнять отдельной фазой после завершения восстановления структуры, названий и token/key metadata; текущий workflow не должен превращаться в compile-fix loop.
- Клан-казну нельзя портировать через хардкод suffix: Android должен искать готовую ссылку `main.php?get_id=29&uid=<itemId>` в HTML и брать suffix страницы до `'`, сохраняя приоритет ручных HTML-действий над background probes.
- При будущей реализации защищенных запросов использовать только `SessionManager`, не `AppVars.VCode`.
