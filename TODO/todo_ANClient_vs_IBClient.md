# TODO ANClient vs IBClient

## Цель

Собрать полный diff между текущим `ANClient/`, восстановленным IBC из `D:\IBC2\iBClient_BD.exe` и Android-портом в `app/`, чтобы не потерять функциональность при переносе.

Этот файл является верхнеуровневым трекером. Технический runtime-unpack ведется отдельно в `IBClient/todo_runtime.md`.

## Источники

| Источник | Путь | Назначение |
| --- | --- | --- |
| ANClient | `ANClient/` | Текущая C# версия после rename `ABClient` -> `ANClient`. |
| IBC original | `D:\IBC2\iBClient_BD.exe` | Основной бинарник для runtime-восстановления логики. |
| IBC decompile | `IBClient/decompiled_original/` | Обычный decompile; часть методов остается runtime-stub. |
| IBC runtime | `IBClient/runtime/` | Дампы protected IL после запуска loader/protector. |
| IBC decrypted | `IBClient/decompiled_runtime_v2_strings/` | Runtime-restored decompile copy с подставленными decrypted строками. |
| IBC named | `IBClient/decompiled_runtime_v2_named/` | Generated named-copy с high-confidence type/control/`InitializeComponent` renames и связкой к token/key maps. |
| IBC restored source | `IBClient/restored_project/` | Единая user-facing структура восстановленных C# исходников; build/debug выполняется отдельно после стабилизации maps. |
| Android | `app/` | Целевая версия для портирования. |

## Статусы

| Статус | Значение |
| --- | --- |
| `[ ]` | Не начато. |
| `[~]` | В анализе / частично известно. |
| `[x]` | Подтверждено / выполнено. |
| `[-]` | Заблокировано до runtime-восстановления IBC. |

## Приоритетные зоны

| Зона | IBC | ANClient | Android | Статус | Следующий шаг |
| --- | --- | --- | --- | --- | --- |
| Соль и crypto-ключи | Runtime-строки подтвердили `SaltText = we1022@alA0`; бинарник содержит `Ivan Medvedev`; auth TripleDES key отличается: `p@ssw0rd649MiOfNiBCl1enT`. | `AppConsts.SaltBinary = Ivan Medvedev`, `SaltText = we1022@alA0`, `PasswordForTheKeyFile = Enot2OOpoloskun#`; старый auth TripleDES key `p@ssw0rdDR0wSS@P6660juht`. | `AppConsts.java` и `CryptoUtils.java` используют `Ivan Medvedev`; `SALT_TEXT` есть в `AppConsts.java`. | `[~]` | Восстановить точные crypto/key-file методы IBC и решить, какой auth/key контракт нужен Android. |
| Туротор | Есть `FormTurotor`, настройки `TurotorToGo`, `TurotorToGoSecondary`, `TurotorIntervalStart`, `TurotorIntevalEnd`; secondary route работает внутри часового интервала; teleport JS/scroll fallback восстановлены. | Есть быстрый `Телепорт (Остров Туротор)` через `MainPhpFastIsland` и route flag `MapPath.IsIslandRequired`; отдельной формы нет по inventory. | Есть quick-action `FastActionManager.fastAttackIslandPot()` и `MapPath.isIslandRequired`, но нет primary/secondary route settings и interval-handler. | `[~]` | Портировать настройки/handler поверх существующего quick-action и `MapPath`, не создавать новый teleport-контур. |
| Гиблая топь | Decrypted layer подтвердил teleport JS `main.php?get_id=16&act=3&sp=...&vcode=...` для entry `7,"Гиблая Топь"`. | Полноценного контура не подтверждено. | Не проверено. | `[~]` | Разобрать сценарий после перехода: бои/ресурсы/условия запуска. |
| Auto-digging кладов | Есть `FormTreasure`, `W4bBm7jSOr`, `ujqBpNAlnH`, `d9PBFLSwqa[0..2]` для выбора трех лопат. | `FormTreasure` отсутствует по inventory. | Не найден аналог auto-digging с выбором лопат. | `[~]` | Отдельно разобрать auto-digging endpoints/handler, не смешивать с казной. |
| Казна / вещи | Есть main-form `ClanKaznaViewButton`, `ClanKaznaViewAll/RAR/ART`, state `gbBBllYF94`; `k1HBZSBytl` - очередь id из `complects.xml` (`MNlBVcP8Me: name -> ids`), вход `main.php?wfo=1&useaction=clan-action&addid=3`, take action парсится из HTML как `main.php?get_id=29&uid=<itemId><pageSuffix>`. | Есть `ShopEntry`, но клан-казна отсутствует по inventory. | Есть `ShopEntry.java` и stub `startBulkOldSell(...)`, но нет `ClanKazna*` и item bridge `GetItem*`. | `[x]` | Портировать как отдельный WebView/postfilter flow: очередь id, вход в казну, поиск готовой ссылки по `uid`, подавление background probes при ручных действиях. |
| Фильтры `Арты` / `Рары` / `Все` | Decrypted layer подтвердил UI anchors `ClanKaznaViewAll`, `ClanKaznaViewRAR`, `ClanKaznaViewART`, `Рары`, `Арты`; выбранный режим хранится в `gbBBllYF94`: `0=Все`, `1=Рары`, `2=Арты`. | В текстовом поиске ANClient явная реализация пока не подтверждена. | Не найдены. | `[x]` | Mapping подтвержден; при портировании не использовать `d9PBFLSwqa` для казны. |
| Mine / Dwarf / Fort / Castle | В IBC найдены bridge-методы `getCellImg`, `mineMoveTo`, `getMineCellHTML`, `DwarfCraft*`, `GetFortBuffsState`, `LeaveFort`, `MoveToFort`, `CastleBuff*`; tokens/keys сведены в `target_key_map_runtime_v2.md`. | В ANClient эти методы не подтверждены. | Не проверено. | `[~]` | Составить отдельную матрицу endpoints/state по `ScriptManager` и postfilter. |
| Полный inventory IBC | Runtime-restored decompile v2 доступен; `IBClient/comparison/inventory_diff_runtime_v2.md` создан. | ANClient inventory создан. | Android структура доступна. | `[~]` | Расширить diff до Android и сделать функциональную матрицу по ключевым зонам. |

## Детальные analysis-файлы runtime v2

- [x] `TODO/IBClient/todo_FormTurotor.cs.md`: восстановлена логика UI, profile-полей и Android gap по primary/secondary route + interval.
- [x] `TODO/IBClient/todo_FormTreasure.cs.md`: восстановлена логика auto-digging кладов, `W4bBm7jSOr`, `d9PBFLSwqa[0..2]`, `ujqBpNAlnH`.
- [x] `TODO/IBClient/todo_ScriptManager.cs.md`: восстановлены item bridge методы `GetItemID`, `GetItemVcode`, `GetItemPrice`.
- [x] `IBClient/comparison/runtime_feature_matrix_v2.md`: матрица IBC vs ANClient vs Android по Туротору, казне и bridge.
- [x] `IBClient/comparison/target_key_map_runtime_v2.md`: focused map `token/key/name/string anchors` по `ScriptManager`, auth, teleport, ClanKazna.
- [x] `IBClient/runtime/token_key_name_string_map.tsv`: полная машинно-читаемая карта `metadata token -> runtime key -> real name -> string hints`.
- [x] `IBClient/decompiled_runtime_v2_named/`: named-copy восстановленного IBC; `1351` C# файлов, `661` control/field renames, `23` `InitializeComponent` method renames.
- [x] `IBClient/runtime/target_key_map_named.tsv`: token/key map с `realType`, `realMethod`, `outPath`; использовать для дальнейшего разбора вместо ручного сопоставления obfuscated paths.
- [x] `IBClient/restored_project/`: единый restored source project с `.Designer.cs`, generated `.csproj` и ссылкой на `Newtonsoft.Json.dll`; сборка не является целью текущего этапа.
- [x] `IBClient/scripts/restore_ibclient_project.ps1`: главный воспроизводимый pipeline source restoration без build-debug по умолчанию.

## Соль и ключи

- [x] ANClient: `ANClient/AppConsts.cs` содержит `SaltBinary = 49-76-61-6E-20-4D-65-64-76-65-64-65-76` (`Ivan Medvedev`).
- [x] ANClient: `ANClient/AppConsts.cs` содержит `SaltText = we1022@alA0`.
- [x] ANClient: `ANClient/AppConsts.cs` содержит `PasswordForTheKeyFile = Enot2OOpoloskun#`.
- [x] ANClient: `ANClient/Helpers/Crypts.cs` использует `Rfc2898DeriveBytes(password, AppConsts.SaltBinary)` и `MD5(SaltText + password)`.
- [x] Android: `app/src/main/java/ru/neverlands/abclient/utils/AppConsts.java` содержит `SALT_BINARY` и `SALT_TEXT`.
- [x] Android: `app/src/main/java/ru/neverlands/abclient/utils/CryptoUtils.java` содержит локальный `SALT = Ivan Medvedev` для TripleDES/PBKDF2 совместимости.
- [x] IBC binary: `D:\IBC2\iBClient_BD.exe` содержит ASCII `Ivan Medvedev`.
- [x] IBC runtime strings: offset `@92` содержит `we1022@alA0`, то есть `SaltText` совпадает с ANClient/Android.
- [x] IBC runtime strings: offset `@188304` содержит auth IV/base string `p@ssw0rd`.
- [x] IBC runtime strings: offset `@188324` содержит auth TripleDES key `p@ssw0rd649MiOfNiBCl1enT`.
- [x] Подтверждено пользователем: правильный IBC auth TripleDES key точно `p@ssw0rd649MiOfNiBCl1enT`.
- [x] Уточнение: `p@ssw0rd649MiOfNiBCl1enT` - auth TripleDES key для runtime-контракта `ibclient/auth.php`, а не пароль protector/unpack; он не восстанавливает IL bodies и не заменяет runtime IL extraction.
- [x] IBC runtime strings: offset `@188388` содержит `http://www.neverlands.ru/modules/ibclient/auth.php`.
- [~] Сравнение: соль профилей/паролей совпадает (`Ivan Medvedev` + `we1022@alA0`), но auth key/URL отличаются от ANClient (`ibclient/auth.php` vs `anclient/auth.php`).
- [ ] IBC: восстановить пароль ключевого файла или подтвердить, что в IBC key-file контур заменен auth.php-контуром.
- [ ] Сравнить ANClient/Android/IBC по key-file password и TripleDES auth key/IV.
- [ ] Если IBC соль отличается, зафиксировать миграционный риск для профилей/ключей и не менять Android до отдельного решения.

## Туротор / Гиблая Топь

- [x] IBC: найдена форма `IBClient/decompiled_original/iBClient/ABClient/MyForms/FormTurotor.cs`.
- [x] IBC runtime v2: восстановлена форма `IBClient/decompiled_runtime_v2/ABClient.MyForms/FormTurotor.cs`.
- [x] IBC: найдены профильные настройки `TurotorToGo`, `TurotorToGoSecondary`, `TurotorIntervalStart`, `TurotorIntevalEnd`.
- [x] IBC runtime strings: `Телепорт (Остров Туротор)`, `Телепорт (Гиблая Топь)`, `6,"Остров Туротор"`, `7,"Гиблая Топь"`.
- [x] IBC runtime strings: teleport JS uses `main.php?get_id=16&act=3&sp=...&vcode=...` for both entries.
- [x] IBC decrypted references: offsets `@101620` и `@101826` привязаны к `xotL8...` teleport replacements в `decompiled_runtime_v2_strings/`.
- [x] IBC runtime strings: form title `Остров Туротор / Гиблая Топь` and interval labels `IntervalStart`, `IntervalEnd`, `Временной интервал с`, `до`.
- [x] ANClient: найден только быстрый телепорт `Телепорт (Остров Туротор)` в `ANClient/PostFilter/MainPhpFast.cs`.
- [x] IBC: восстановить реальные обработчики `FormTurotor` до decompiled/runtime IL уровня.
- [x] IBC: `FormTurotor.SDbkF6KP4A(...)` сохраняет основной и запасной маршрут в `TurotorToGo` / `TurotorToGoSecondary`.
- [x] IBC: `VulkAmRGuQQBxIOEF5d.BYePjRghxE()` сверяет текущий час с `TurotorIntervalStart` / `TurotorIntevalEnd`.
- [x] IBC: handler выбора `TurotorToGoSecondary` разобран: `Y2qPKQYd9ZDUjRRf7UA.LlfYfR769F()` проверяет текущий час в интервале `TurotorIntervalStart` / `TurotorIntevalEnd`; внутри интервала используется secondary route, вне интервала primary route.
- [x] IBC: маркеры `Гиблая Топь` подтверждены runtime-строками.
- [x] ANClient: inventory подтвердил отсутствие `Client.MyForms.FormTurotor`.
- [x] Android: найден существующий quick-action `FastActionManager.fastAttackIslandPot()` и `MapPath.isIslandRequired`; портировать нужно настройки/handler, а не новый quick-action.

## Auto-digging / Казна / Вещи / Фильтры

- [x] IBC: найдена форма auto-digging `IBClient/decompiled_original/iBClient/ABClient/MyForms/FormTreasure.cs`.
- [x] IBC runtime v2: восстановлена форма auto-digging `IBClient/decompiled_runtime_v2/ABClient.MyForms/FormTreasure.cs`.
- [x] IBC: найдены bridge-методы `ScriptManager.GetItemID`, `GetItemVcode`, `GetItemPrice`.
- [x] IBC: найден `IBClient/decompiled_original/iBClient/ABClient/PostFilter/ShopEntry.cs`.
- [x] IBC runtime strings: найдены `Переходим в казну`, `Вещь занята или отсутствует в казне. ID: <b>`.
- [x] IBC runtime strings: найдены UI-настройки `ClanKaznaViewButton`, `Режим отображения вещей в клан-казне`.
- [x] IBC runtime strings: найдены фильтры `ClanKaznaViewAll` / `Отображать все вещи`, `ClanKaznaViewRAR` / `Рары`, `ClanKaznaViewART` / `Арты`.
- [x] ANClient: найден `ANClient/PostFilter/ShopEntry.cs` и `ShopAjaxPhp.cs`.
- [x] IBC: восстановить реальные обработчики `FormTreasure` до decompiled/runtime IL уровня.
- [x] IBC: найти UI-строки фильтров `Арты`, `Рары`, `Все`.
- [x] IBC: `FormTreasure.Oebk3KKUCK(...)` сохраняет три shovel-флага в `d9PBFLSwqa[0..2]`: `[0] Лопата кладоискателя`, `[1] Лопата археолога`, `[2] Походная лопатка`.
- [x] IBC: `d9PBFLSwqa[0..2]` не относится к фильтрам `Все`/`Рары`/`Арты`; это была ложная гипотеза.
- [x] IBC: фильтры клан-казны хранятся в `TKPDnOaJQc5chbOU6u.gbBBllYF94`: `0=Все`, `1=Рары`, `2=Арты`; UI handler `Wd0FHDaMP3(...)` читает `ToolStripMenuItem.Tag` и обновляет текст `ClanKaznaViewButton`.
- [x] IBC: для входа в казну используется redirect `main.php?wfo=1&useaction=clan-action&addid=3`; для взятия вещи из казны `D28HZRpBoY(...)` строит `main.php?get_id=29&uid=<itemId><pageSuffix>` из HTML `СПИСОК ВЕЩЕЙ КЛАНА / СЕМЬИ`.
- [x] IBC: `pageSuffix` не хардкодится. `D28HZRpBoY(...)` ищет в HTML готовую ссылку с префиксом `main.php?get_id=29&uid=<itemId>` и вырезает хвост до ближайшей одинарной кавычки; отдельная ветка response parsing ожидает `&wmas`, но это не константа для сборки take URL.
- [x] IBC: `k1HBZSBytl` - colon-separated очередь item-id выбранного комплекта. Источник очереди - `TKPDnOaJQc5chbOU6u.MNlBVcP8Me`, загружаемый/сохраняемый из `complects.xml` как `name -> ids`; после попытки первый id удаляется, остаток очереди пересобирается, пустая очередь переводится в `FINISHED` и затем очищается внешним handler'ом.
- [x] IBC: `GetItemID` парсит первый CSV-токен из `qZIBY9ZYgG`; `GetItemVcode` берет последний CSV-токен; `GetItemPrice` возвращает `KMIBQYxb5T`.
- [x] IBC decrypted layer: найден HTML/JS injection с `window.external.GetItemID()`, `GetItemPrice()`, `GetItemVcode()` в `xotL8...` для `market_ajax.php?action=place_item_put` и `shop_ajax.php`.
- [x] IBC: shop/market JS injection отделен от клан-казны; action взятия из казны идет через `D28HZRpBoY(...)` и `main.php?get_id=29&uid=...`.
- [x] ANClient: inventory подтвердил наличие `ShopEntry` и отсутствие `Client.MyForms.FormTreasure`.
- [x] Android: найден `ShopEntry.java` и stub `WebAppInterface.startBulkOldSell(...)`; клан-казна/`GetItem*` bridge отсутствуют, портировать надо в существующий `WebAppInterface`/postfilter контур.

## Runtime-восстановление IBC

- [x] Подтверждено: `iBClient_BD.exe` полезнее `iBClient_BD_deobf.exe`, потому что `*_deobf.exe` содержит stubs.
- [x] Подтверждено: после `RMxTB6tLfvU9nlCYFJp2.bttt5gH5NC2()` таблица `eRVtTLZ5h0A` содержит 5761 IL-блок.
- [x] Созданы дампы целевых методов в `IBClient/runtime/`.
- [x] Сопоставить keys `eRVtTLZ5h0A` с metadata tokens: `IBClient/runtime/method_il_map.tsv`.
- [x] Очистить runtime IL от protector-пролога `br.s + invalid call`.
- [x] Подготовить patcher, вставляющий runtime IL в копию assembly: `IBClient/tools/IbcRuntimePatcher`.
- [x] Декомпилировать patched assembly и обновить этот TODO фактами, а не предположениями: `IBClient/decompiled_runtime_v2/`.
- [x] Восстановить string-decrypt слой без изменения базового decompile: `IBClient/decompiled_runtime_v2_strings/`.
- [x] Создать `IBClient/runtime/string_table.tsv`, `string_references.tsv`, `method_string_hints.tsv`, `token_key_name_string_map.tsv`.
- [x] Создать target report `IBClient/comparison/target_key_map_runtime_v2.md`.
- [x] Создать generated named-layer: `IBClient/scripts/restore_named_project.ps1`, `IBClient/decompiled_runtime_v2_named/`, `named_symbol_map.tsv`, `named_member_map.tsv`, `target_key_map_named.tsv`.

## Inventory diff runtime v2

- [x] Создан report `IBClient/comparison/inventory_diff_runtime_v2.md`.
- [x] Сформирован `IBClient/comparison/inventory_ibclient_runtime_v2.tsv`.
- [x] Сформирован `IBClient/comparison/inventory_anclient.tsv`.
- [x] Итог normalized client types: `52` есть в обоих, `17` только в IBC runtime v2, `161` только в ANClient.
- [x] Только IBC runtime v2 среди важных: `Client.MyForms.FormTurotor`, `Client.MyForms.FormTreasure`, `Client.APIForms.CityHall`, `Client.APIForms.ForpostBuildings`, `Client.ExtMap.AbcCell`.
- [x] В обоих: `Client.ScriptManager`, `Client.PostFilter.ShopEntry`, `Client.ExtMap.NavScriptManager`, `Client.ExtMap.MapPath`, `Client.Lez.LezFight`.
- [x] Создана функциональная матрица `IBClient/comparison/runtime_feature_matrix_v2.md`.

## Правила дальнейшей работы

- Не портировать Туротор/Гиблую топь/казну по догадкам: сначала восстановить IBC runtime IL или явно зафиксировать fallback.
- Не добавлять параллельный фикс, если в Android уже есть существующий контур quick-action/inventory/shop/navigation.
- Для новых защищенных запросов Android использовать `SessionManager.getValidVCodeForAction(...)`, не `AppVars.VCode`.
- Для ручных HTML-действий казны сохранять приоритет пользователя над background probes.
- Все промежуточные результаты сравнения фиксировать в этом файле, а детальные технические дампы держать в `IBClient/`.
