# Задача: Авто-Шахта для app2/ANClient

Дата: 2026-05-19

## Цель

Портировать `Авто-Шахта` в `app2/ANClient` с сохранением логики ПК-версии: вход в шахту, работа с шахтной картой, выбор полезных клеток, перемещение с факелом, переодевание кирки, запуск добычи через кнопку `Начать добычу`, обработка `mine_ajax.php` и остановка при критичных ошибках.

## Источники анализа

- `IBClient/runtime/string_table.tsv` — восстановленные строки протокола, UI, инструментов и ошибок автошахты.
- `IBClient/runtime/target_key_map_named.tsv` — карта восстановленных методов `get_mine`, `mineMoveTo`, `getMineCellHTML`, `qppgIneATQ`.
- `IBClient/runtime/method_string_hints.tsv` — привязка `qppgIneATQ()` к шахтам `Рудник Провал` и `Рудник Пыльный`.
- `IBClient/decompiled_runtime_v2_named/ABClient/PostFilter/Filter.cs` — обработка шахтной страницы, `Resources.mine`, вызов `view_mine();`.
- `IBClient/decompiled_runtime_v2_named/ABClient/ScriptManager.cs` — bridge-методы `getCellImg`, `mineMoveTo`, `getMineCellHTML`.
- `IBClient/decompiled_runtime_v2_strings/iBClient/nryKFKxXsCDGHf5x3oK/y5EFtVxPk2kCIEAf0Nt.cs` — загрузка `map_mines.xml`, модель шахтных клеток и HTML клетки.
- `IBClient/decompiled_runtime_v2_strings/iBClient/tT3nlixN7icDQ0vaiOM/YZSyjDxCC6TA8Ed7M3n.cs` — модель клетки шахты: координаты, картинка, полезность.
- `app2/src/main/java/ru/neverlands/anclient/model/UserConfig.java` — уже есть профильный флаг `AutoMine = false`.
- `app2/src/main/java/ru/neverlands/anclient/model/QuickActionType.java` — сейчас нет `AUTO_MINE`.
- `app2/src/main/java/ru/neverlands/anclient/manager/AutoFunctionsManager.java` — единая точка persisted/runtime toggle автофункций.
- `app2/src/main/java/ru/neverlands/anclient/ui/QuickButtonsPanel.java` — UI quick buttons, long-press settings и иконки автофункций.
- `app2/src/main/java/ru/neverlands/anclient/postfilter/Filter.java` — центральный router; сейчас нет `/js/mine` и `mine_ajax.php`.
- `app2/src/main/java/ru/neverlands/anclient/postfilter/MainPhp.java` — bootstrap/redirect/nav контур non-combat автоматик.
- `app2/src/main/java/ru/neverlands/anclient/postfilter/TreasureDig.java` — близкий пример auto-click по кнопке `Digg(code)`, но для клада.
- `app2/src/main/java/ru/neverlands/anclient/postfilter/AlchemyAjaxPhp.java` и `AutoCutHandler.java` — актуальный образец ajax-профессии с инструментом, таймером, captcha и файловым trace.
- `app2/src/main/java/ru/neverlands/anclient/webview/WebViewRequestInterceptor.java` — `SessionManager.parseVCodeFromHtml(...)` и source/context для vcode.
- `app2/src/main/java/ru/neverlands/anclient/bridge/WebAppInterface.java` — bridge pattern для JS карты и `AutoCut`.

## Восстановленные строки ПК-версии

| Токен | Значение | Назначение |
| --- | --- | --- |
| `4696` | `var mine = [` | JS-массив состояния шахты. |
| `4724` | `var mine = ["` | Альтернативный формат массива шахты. |
| `4760` | `var pos = [` | Текущая позиция в шахте. |
| `4794` | `"digg","Начать добычу","` | Маркер кнопки добычи. |
| `70942` | `/js/mine` | Серверный шахтный JS, который ПК-клиент подменяет ресурсом. |
| `71474` | `http://www.neverlands.ru/gameplay/ajax/mine_ajax.php` | Ajax endpoint шахты. |
| `102116` | `Кирки не найдены в инвентаре. Отключаем автошахту.` | Hard-stop при отсутствии кирки. |
| `102220` | `&im=0&wca=2` | Устаревший восстановленный фильтр; live/IBClient-сверка 2026-05-21 показала, что кирки должны открываться через `wca=3`. |
| `102270` | `main.php?im=0&wca=2` | Устаревший восстановленный переход; в Android используется `main.php?im=0&wca=3`. |
| `116044` | `view_mine();` | Маркер шахтной страницы после загрузки. |
| `116072` | `Нет кнопки "Начать добычу". Автошахта отключена.` | Hard-stop, если добыча невозможна. |
| `116172` | `Digg('{0}');` | Прямой запуск добычи по коду кнопки. |
| `116200` | `Вам нужна кирка, чтобы начать добычу.` | Серверная ошибка отсутствующей кирки. |
| `116278` | `Вам нужен факел для перемещения по шахте.` | Серверная ошибка отсутствующего факела. |
| `116364` | `Обнаружены ресурсы` | Успешное обнаружение ресурсов. |
| `116404` | `Обнаружены ресурсы: ` | Chat/report prefix. |
| `116448` | `. Добываем` | Продолжение отчета перед добычей. |
| `116472` | `Добыты ресурсы (уровень <b>` | Успешный отчет о добыче. |
| `116624` | `Вы не нашли ни одного ресурса` | Пустая добыча. |
| `170574` | `map_mines.xml` | Карта шахт. |
| `170604`..`170682` | `mineid`, `x`, `y`, `img`, `usefull`, `right`, `up`, `down` | Атрибуты клетки шахты. |

## Инструменты и расходники

Кирки из ПК-версии:

- `Легкая кирка`
- `Тяжелая кирка`
- `Сбалансированная кирка`
- `Кирка Мастера-рудокопа`
- `Праздничная Кирка Рудокопа`

Факелы/фонари из ПК-версии:

- `Смоляной факел`
- `Масляный факел`
- `Дварфийский фонарь`

## Функциональность в C#

`Filter.cs` подменяет серверный `/js/mine` на встроенный `Resources.mine` и обрабатывает HTML, где есть `view_mine();`. Это аналог существующего Android postfilter-подхода для `MapJs`, `FightJs`, `AlchemyAjaxPhp`, но шахтный маршрут в `app2` пока не подключен.

`ScriptManager.cs` открывает JS bridge для шахты:

- `getCellImg(string x, string y)` возвращает картинку клетки из загруженной карты шахт.
- `mineMoveTo(string x, string y)` запускает перемещение внутри шахты через main form/runtime.
- `getMineCellHTML(string x, string y, string lvl)` формирует HTML клетки шахты с учетом уровня/координат.

Модель шахтных клеток загружается из `map_mines.xml` в словарь по шахте и координатам. По восстановленным атрибутам каждая клетка содержит `mineid`, `x`, `y`, `img`, `usefull`, `right`, `up`, `down`. `usefull` используется как пользовательская отметка полезности: плохая/нейтральная/хорошая/техническая информация в UI `Шахта`.

`qppgIneATQ()` сопоставляет название текущей шахты с внутренним id. Восстановлены значения:

- `Рудник Провал` -> `2`.
- `Рудник Пыльный` -> отдельный id, восстановлен через string hints как шахтный mine id.
- `Шахта в Деревне Подгорная` -> отдельный id.

Кнопка добычи определяется по маркеру `"digg","Начать добычу","..."`. После нахождения ресурсов ПК-версия строит вызов `Digg('{0}');` и запускает добычу. Это отличается от клада: клад использует `"dig","Копать",...`, а шахта использует `"digg","Начать добычу",...`.

При отсутствии кирки Android открывает инвентарь `main.php?im=0&wca=3`, ищет известные кирки и надевает их. Если кирка не найдена, автошахта выключается с сообщением `Кирки не найдены в инвентаре. Отключаем автошахту.`.

При перемещении внутри шахты сервер может вернуть ошибку `Вам нужен факел для перемещения по шахте.`. Это отдельный расходник/инструментальный guard, не надо смешивать с киркой.

## Текущее состояние app2

- В `UserConfig` уже есть `AutoMine = false`, но он нигде не подключен к runtime.
- В `QuickActionType` нет `AUTO_MINE`.
- В `AutoFunctionsManager` нет `isAutoMineEnabled/toggleAutoMine/setAutoMineEnabled`.
- В `QuickButtonsPanel` нет кнопки, иконки и настроек `Авто-Шахта`.
- В `Filter.process(...)` нет маршрутов `/js/mine` и `mine_ajax.php`.
- В `WebViewRequestInterceptor.determineSourceFromUrl(...)` нет source/context `mine`.
- В `app2/src/main/assets` нет `mine.js` или `map_mines.xml`.
- В `MainPhp` есть готовый non-combat bootstrap/nav контур, но нет ветки шахты.
- В `TreasureDig` есть похожий auto-click через `Digg(code)`, но его нельзя переиспользовать напрямую из-за другого маркера кнопки и другой логики инструмента.
- В `AutoCut` уже есть правильный pattern профессии: manager/handler/ajax postfilter, tool-check, cleanup, timers, captcha fallback и `AppLog` chain.

## Перепроверка карты шахт 2026-05-20

- `IBClient/restored_project/ABClient/ScriptManager.cs` подтверждает parity bridge: `getCellImg(x, y)` возвращает `MineCell.img`, `mineMoveTo(x, y)` делегирует переход в `FormMain`, `getMineCellHTML(x, y, lvl)` строит HTML клетки через модель шахты.
- `IBClient/decompiled_runtime_v2_strings/.../y5EFtVxPk2kCIEAf0Nt.cs` показывает структуру словаря `mineId -> x-y -> cell`; ключ клетки строится из `x + "-" + y`.
- `IBClient/runtime/string_table.tsv` подтверждает атрибуты клетки `mineid`, `x`, `y`, `img`, `usefull`, `right`, `up`, `down` и HTML-маркеры полезности/маршрута.
- `IBClient/decompiled_runtime_v2_strings/.../dnMjnNgAKAw4HFRl9Ur.cs` подтверждает id шахт: `Шахта в Деревне Подгорная -> 1`, `Рудник Провал -> 2`, `Рудник Пыльный -> 3`.
- После повторной проверки clean-decompile выяснено, что `map_mines.xml` является внешним файлом рядом с exe, а не embedded resource. Пользователь предоставил файл в корне workspace; он подключен в `app2/src/main/assets/map_mines.xml`.
- `AutoMineManager.getCellImg()` теперь читает asset-карту `map_mines.xml`, `mineMoveTo()` использует pending-target только при включенной автошахте, а `getMineCellHTML()` возвращает overlay/подпись клетки без вложенного inline `onclick`.

## Регрессия карты шахт 2026-05-20

- После базовой bridge-интеграции карта шахты всё ещё отображается серверным окном 3x3, хотя в IBClient используется масштабируемый рендер поверх `Resources.mine`.
- Через reflection из `IBClient/iBClient_BD.exe` извлечён embedded `ABClient.Properties.Resources.mine` типа `System.String`; начало ресурса: `var d = document; var div_tunnels = false; var disable_move = false; var cellSize = 130; var moved = false; var Timer = new Timer();`.
- Извлечённый ресурс подтверждает опорные маркеры масштабируемого UI: `cellSize = 130`, `view_mine()`, `getMineCellHTML`, `getMoveText`, `move_tunnels`, `Пункт назначения`, `Ещё переходов`.
- Исправление нужно внести в существующий `MineJs.process(...)`: не добавлять новый HTTP-контур, а после штатного `view_mine()` перестраивать DOM-карту из live `var mine`/`var pos` через уже добавленные bridge-методы `getMineCellHTML(...)`, `mineMoveTo(...)`, `getMoveText()`.
- Порядок координат `pos` в IBClient подтвержден через `scrollToElement()`: `pos[0]` — Y, `pos[1]` — X, `pos[2]` — уровень/режим туннелей. Android state должен хранить `lastX = pos[1]`, `lastY = pos[0]`, иначе подсветка и pending move будут расходиться с отображением.
- После live-проверки масштабируемая карта открывается, но клетки без картинок: причина в том, что `map_mines.xml` пока отсутствует, `AutoMineManager.getCellImg()` возвращает пусто для большинства клеток, а первый вариант patch полностью заменял серверный 3x3 DOM до чтения его фоновых `mine_new/*.jpg`.
- Исправление внесено в тот же `MineJs.process(...)`: wrapper `show_tunnels()` сначала вызывает оригинальный серверный рендер, считывает URL картинок из `#content`, затем перестраивает масштабируемую карту. До восстановления полной `map_mines.xml` эти URL используются как fallback для клеток без bridge-картинки.
- Повторная live-проверка показала, что default fallback размножает одну картинку на все неизвестные клетки и делает карту визуально неверной. Кроме того, клики уходили во вложенный bridge `mineMoveTo`, который при выключенной автошахте не выполняет ручной переход.
- Уточнение исправления: `MineJs` больше не заполняет неизвестные клетки default-картинкой; мапит только реально считанные серверные cell backgrounds. Inline `onclick` из bridge HTML удаляется, click handler ставится на саму `<td>`, а соседняя клетка вызывает штатный серверный `move_tunnels(dir)` без native pending-route.
- Перепроверка `IBClient/iBClient1.21.1 Full cleaned.exe` через ILSpy подтвердила: `Class79.method_0()` не извлекает карту из embedded resources, а читает внешний файл `Path.Combine(Application.StartupPath, "map_mines.xml")`. Формат файла: `<mines><mine mineid="..." level="..."><cell x="..." y="..." img="..." usefull="..." /></mine></mines>`.
- `Class77.smethod_33()` в IBClient сохраняет текущую карту в `map_mines_new.xml`, затем заменяет `map_mines.xml`. То есть `map_mines.xml` является дистрибутивным/пользовательским внешним файлом IBClient, а не ресурсом `Resources.mine`.
- Поиск по рабочему дереву, `IBClient/Cache`, `Downloads`, `Desktop`, `AppData/Roaming` и доступным частям `AppData/Local` не нашёл `map_mines.xml`/`map_mines_new.xml`. Внутри manifest resources `iBClient_BD.exe`/`iBClient_BD_deobf.exe`/`IBClient_BD_patched.exe`/clean exe также нет plain XML с `mineid/usefull`; `ABClient.Properties.Resources.resources` содержит `mine` JS, но не карту.
- Пользователь предоставил фактическую карту шахт `map_mines.xml`; файл скопирован в `app2/src/main/assets/map_mines.xml` как UTF-8 без BOM. Формат подтвержден: 12 mine-level секций, по 441 клетке на уровень, всего 5292 клетки.
- Вывод: app2 больше не должен использовать 3x3 server DOM как основной источник карты. Основной источник картинок и направлений теперь asset `map_mines.xml`; live `var mine`/server DOM остается только fallback/контекстом текущей позиции и кнопки добычи.

## Перепроверка маршрута шахт 2026-05-20

- В IBClient `ScriptManager.mineMoveTo(x, y)` делегирует `FormMain.method_125(x, y)`, где включается режим навигатора шахты и создается `Class80`.
- `Class80` строит маршрут по `map_mines.xml` от текущей клетки до выбранной через обход соседей. Доступность направления определяется только по буквам картинки текущей клетки: `t` вверх, `r` вправо, `b` вниз, `l` влево.
- `Class79.method_3()` показывает `Пункт назначения` и оставшееся число переходов, а `Class79.method_2()` подсвечивает текущую клетку, цель и следующий сегмент пути.
- В app2 уже есть правильный контур (`MineJs` click -> `AutoMineManager.mineMoveTo` -> `AutoMineHandler.buildPendingMoveInjection` -> server `move_tunnels(direction)`), но он не достигает IBClient parity: `mineMoveTo()` игнорирует non-adjacent клики при выключенной автошахте, `AutoMineHandler` инжектит pending move только при включенной автошахте, а выбор следующего шага сейчас greedy horizontal/vertical вместо path search.
- Исправление должно расширить существующий контур, не добавляя новый HTTP client: ручной маршрут должен работать независимо от toggle `Авто-Шахта`, а авто-добыча, кирка и факел остаются gated by `isAutoMineEnabled()`.
- Для touch WebView нужен отдельный tap fallback: `touchstart.preventDefault()` в drag-контейнере может подавить synthetic `click`, поэтому клетка должна обрабатывать `touchend`, если не было drag/pinch.
- Live-регрессия после первого BFS-варианта: WebView пытался открыть `http://main.php/?an_auto_mine_move=1...` и получал `net::ERR_EMPTY_RESPONSE`. Причина: относительный `loadUrl("main.php?...an_auto_mine_move...")` резолвился как host `main.php`, а proxy шёл на `origin=main.php:80`.
- `mine_nav.har` подтвердил штатный транспорт IB/server page: стек запроса идёт через `AjaxGet` из `js/ajax.js`, вызывающий `move_tunnels(dir)`. Поэтому исправление не должно создавать pseudo-URL/навигацию, а должно вызывать existing page JS `move_tunnels`.
- Финальный контур маршрута: `AutoMineManager.mineMoveTo(x,y)` фиксирует цель и возвращает первый direction, `MineJs` сразу вызывает `move_tunnels(next)` для ручного far-click, а продолжение маршрута идёт через bridge `getNextMineMoveDirection(currentX,currentY,lvl,source)` после `show_tunnels`/`view_mine`/`move` hooks. Throttle маршрута использует общий `window.__anMineRouteLastAt`, чтобы native injection и mine.js patch не дублировали шаги.

## Найденный существующий контур

Для реализации нужно расширять существующие точки, а не создавать параллельный контур:

- `QuickActionType` — добавить `AUTO_MINE("Авто-Шахта", "auto_mine")` рядом с профессиями.
- `AutoFunctionsManager` — добавить persisted/runtime toggle по аналогии с `AUTO_TREASURE` и конфликтами навигации как у `AUTO_CUT`.
- `QuickButtonsPanel` — добавить иконку, toast, long-press настройки, список кирок/факелов и параметры шахты.
- `Filter` — добавить маршрут `/js/mine` и `gameplay/ajax/mine_ajax.php`.
- `WebViewRequestInterceptor` — добавить source `mine` для vcode context.
- `MainPhp` — добавить bootstrap/возврат в шахту только через существующие redirect helpers, не через отдельный HTTP client.
- `WebAppInterface` — добавить bridge-методы для mine JS только если потребуется сохранить parity с `getCellImg/mineMoveTo/getMineCellHTML`.
- `AutoMineHandler` — выделить проверки кирки/факела/контекста, потому логика имеет больше трех каскадных условий.
- `MineAjaxPhp` — обрабатывать ответы `mine_ajax.php` и сообщения успеха/ошибок.
- `AutoMineManager` — хранить настройки, состояние текущей шахты, выбранные клетки/полезность, pending action и trace.

## Архитектурное решение для app2

Создать отдельный `AutoMineManager`, а не расширять `AutoCutManager`: шахта использует другой endpoint, другую карту, другие инструменты и внутреннее перемещение. При этом общие инфраструктурные подходы берутся из существующих контуров `AutoCut`/`TreasureDig`/`MainPhp`.

Предлагаемые классы и файлы:

- `app2/src/main/java/ru/neverlands/anclient/manager/AutoMineManager.java` — настройки, runtime state, выбор шахты/клеток, trace chain `AUTO_MINE_TRACE`.
- `app2/src/main/java/ru/neverlands/anclient/postfilter/AutoMineHandler.java` — main.php decision point: подготовка кирки/факела, инвентарь, возврат в шахту, hard-stop.
- `app2/src/main/java/ru/neverlands/anclient/postfilter/MineAjaxPhp.java` — postfilter для `mine_ajax.php`, анализ `RESO@`/ошибок/таймеров/добытых ресурсов.
- `app2/src/main/java/ru/neverlands/anclient/postfilter/MineJs.java` — подмена `/js/mine` на локальный asset или shim с AndroidBridge guards.
- `app2/src/main/assets/js/mine.js` или `app2/src/main/assets/mine.js` — локальная версия/patch шахтного JS, если серверный JS нужно заменить как в ПК-версии `Resources.mine`.
- `app2/src/main/assets/map_mines.xml` — карта шахт из ПК-версии, подключена как основной asset-источник клеток, картинок и `usefull`.

## Инварианты реализации

- Не изменять `ABClient/`, `IBClient/` или `ANClient/`; они только источники анализа.
- Все изменения делать в `app2/` и `TODO2/`.
- Не использовать `AppVars.VCode` в новом коде. Для защищенных запросов брать `SessionManager.getInstance().getValidVCodeForAction("mine_...")` и обрабатывать `null`.
- Логи только через `AppLog`, с файловым chain `AUTO_MINE_TRACE`.
- Не добавлять второй HTTP client/scheduler: использовать WebView, `Filter`, JS bridge и существующие redirect helpers.
- Ручные клики в шахте должны иметь приоритет: suppress auto-click/auto-probe после ручного действия, не отправлять конкурирующий `Digg`.
- Не смешивать `dig`/клад и `digg`/шахту. `TreasureDig` может быть примером, но marker должен быть `"digg","Начать добычу","`.
- Любой `fastStart()` в будущем quick-action шахты должен завершаться `FastActionManager.fastCancel("reason")`; базовая автошахта не должна ставить `FastNeed` без cleanup.

## Протокол и ожидаемый runtime flow

1. Пользователь включает `Авто-Шахта` через quick button.
2. `AutoFunctionsManager.setAutoMineEnabled(true)` сохраняет `KEY_AUTO_MINE`, синхронизирует `Profile.AutoMine`, выключает конфликтующие non-combat маршруты (`AutoTreasure`, `AutoCut`, `AutoLumberjack`, при необходимости `AutoFish/AutoSkin/AutoBait`) и запускает bootstrap.
3. Bootstrap открывает текущий шахтный/персонажный context через `main.php` existing redirect helpers.
4. `Filter` подменяет `/js/mine` и пропускает шахтную HTML-страницу через mine handler.
5. `AutoMineManager` парсит `var mine = [...]`, `var pos = [...]`, текущий mine id и доступную кнопку `digg`.
6. Если есть кнопка `Начать добычу`, `AutoMineHandler`/JS shim запускает `Digg(code)` один раз с guard `window.__anAutoMineDigClicked`.
7. `MineAjaxPhp` анализирует ответ `mine_ajax.php`: ресурсы найдены, добыча успешна, пусто, нужна кирка, нужен факел, неверный код защиты, таймер.
8. При найденных ресурсах и включенном chat-report пишет сообщение формата `'timestamp-server' + [auto_mine/source] + текст`.
9. При отсутствии кирки открывается `main.php?im=0&wca=3`, ищется выбранная/любая кирка и выполняется надевание через существующие inventory helpers.
10. При отсутствии факела открывается инвентарь с фильтром инструментов/расходников, ищется выбранный/любой факел/фонарь; если невозможно восстановиться, автошахта выключается.
11. После таймера/успеха выбирается следующая полезная клетка и выполняется `mineMoveTo(x, y)`/эквивалентный JS вызов.

## Настройки UI

Минимальный long-press dialog `Авто-Шахта`:

- `Настройки авто-шахты`.
- `Кирки` — multi-select из списка ПК-версии, default любая кирка.
- `Факелы/фонари` — multi-select из списка ПК-версии, default любой факел/фонарь.
- `Шахты/клетки` — выбор шахты и список полезных координат, если карта восстановлена.
- `Отчет в чат` — включить/выключить сообщения о найденных и добытых ресурсах.
- `Останавливать при пустой добыче` — optional fallback, default off.
- `Удалить кнопку`.

Иконка: подобрать внешнюю иконку с `image.neverlands.ru` для шахты/кирки. Если точная иконка неизвестна, временный fallback `R.drawable.ic_add` допустим до уточнения asset.

## План реализации

### Фаза 0: документация и pre-check

- [x] Перечитать `AGENTS.md` и `AGENTS2.MD`.
- [x] Проверить, что существующего TODO2 по auto mine нет.
- [x] Найти текущие app2 decision points по `AutoMine`, `mine_ajax.php`, `/js/mine`, `Digg`, `AutoFunctionsManager`, `QuickActionType`.
- [x] Зафиксировать анализ и план в `TODO2/todo_task_20260519_auto_mine.md` до правок Java.

### Фаза 1: базовая интеграция автофункции

- [x] Добавить `QuickActionType.AUTO_MINE("Авто-Шахта", "auto_mine")`.
- [x] Добавить license key `auto_mine` по тому же non-public/public правилу, которое будет согласовано для профессий.
- [x] Добавить `KEY_AUTO_MINE`, `isAutoMineEnabled`, `toggleAutoMine`, `setAutoMineEnabled` в `AutoFunctionsManager`.
- [x] Синхронизировать `Profile.AutoMine` с persisted prefs, аналогично `AutoTreasure`/`AutoFight`.
- [x] Добавить сброс в `disableAll()` и `disableUnavailableFeatures(...)`.
- [x] Добавить quick button UI, toast и long-press меню в `QuickButtonsPanel`.

### Фаза 2: manager/handler state

- [x] Создать `AutoMineManager` с `TRACE_CHAIN = "AUTO_MINE_TRACE"`.
- [x] Хранить настройки кирок, факелов, chat-report и читать шахтные клетки из `map_mines.xml`. Пользовательские отметки клеток остаются отдельной задачей хранения.
- [x] Хранить runtime: active mine id, current x/y/level, last dig code, pending tool check, pending torch check, last action time, dedup key.
- [x] Добавить hard-stop helpers: `disableAutoMine(reason)`, `stopBecauseNoPickaxe`, `stopBecauseNoDigButton`.
- [-] Добавить методы выбора следующей клетки по `usefull`/adjacency и fallback на текущую клетку: отложено до восстановления карты шахт или подтверждения live-формата `var mine`.

### Фаза 3: postfilter routing

- [x] Добавить `MineJs.process(...)` в `Filter.process(...)` для `/js/mine`.
- [x] Добавить `MineAjaxPhp.process(address, array)` для `http://neverlands.ru/gameplay/ajax/mine_ajax.php`.
- [x] Добавить source `mine` в `WebViewRequestInterceptor.determineSourceFromUrl(...)` для `/js/mine`, `mine_ajax.php` и шахтных `main.php` contexts.
- [x] Проверить, что `SessionManager.parseVCodeFromHtml(...)` получает шахтный context после postfilter.

### Фаза 4: JS/bridge parity

- [x] Восстановить минимальный локальный `mine.js`/shim: guard auto-click, `Digg(code)`, trace в `AndroidBridge.TraceAutoMineRuntime(...)`.
- [x] Добавить в `WebAppInterface` методы parity: `DoMineAutoDig`, `TraceAutoMineRuntime`, при необходимости `getCellImg`, `mineMoveTo`, `getMineCellHTML`.
- [x] Не добавлять второй auto-click рядом с серверным JS; патчить существующий path рендера кнопки `digg`.
- [x] Добавить one-shot guard `window.__anAutoMineDigClicked` и reset после server response/timer.

### Фаза 5: инструментальные проверки

- [x] Создать `AutoMineHandler.processMainPhpAutoMineStep(...)` и вызвать его из `MainPhp.process(...)` рядом с другими non-combat handlers.
- [x] Проверять кирку через inventory parser и фильтр `main.php?im=0&wca=3`.
- [x] Если выбранной кирки нет, пробовать любую разрешенную кирку; если нет ни одной, выключать автошахту.
- [x] Проверять факел/фонарь отдельно, с понятным логом и отдельным hard-stop.
- [x] Все redirect links строить через `MainPhp.buildRedirectHtml(...)`/существующие helpers, не через новый HTTP client.

### Фаза 6: `mine_ajax.php`

- [x] В `MineAjaxPhp` обработать ошибку `Вам нужна кирка, чтобы начать добычу.`.
- [x] Обработать ошибку `Вам нужен факел для перемещения по шахте.`.
- [x] Обработать отсутствие кнопки `Начать добычу` и выключить автошахту с логом.
- [x] Обработать `Обнаружены ресурсы` и сформировать следующий `Digg(code)` только при наличии валидного кода.
- [x] Обработать `Добыты ресурсы (уровень...)` и `Вы не нашли ни одного ресурса`.
- [x] Обработать `SessionManager.getValidVCodeForAction("mine_...") == null`: лог `NO_SESSION/EMPTY_VCODE`, skip/reload context.
- [x] Добавить dedup popup/action на 1000-1500 ms, чтобы не отправлять два `Digg` подряд.

### Фаза 7: карта шахт

- [x] Найти или восстановить `map_mines.xml`; файл предоставлен пользователем, подключен в `app2/src/main/assets/map_mines.xml`, fallback parser из server `var mine`/`var pos` сохранен как аварийный источник.
- [x] Создать модель `MineCell` с `mineId`, `x`, `y`, `img`, `usefull`, `right`, `up`, `down`.
- [-] Реализовать чтение/запись пользовательских отметок `usefull` в app2 storage.
- [-] Добавить простую стратегию движения: приоритет полезных соседей, затем непроверенные доступные, затем fallback без движения. Реализован безопасный pending-target шаг к выбранной клетке; авто-выбор полезных соседей оставлен до live-подтверждения payload.
- [x] Логировать каждое решение `next_cell`, `blocked_direction`, `no_torch`, `no_useful_cell`.

Фаза 7 продолжена 2026-05-20: минимальный безопасный fallback без `map_mines.xml` реализован в существующем `AutoMineManager`: live `var mine` парсится в `MineCell`, bridge отдаёт `img`/HTML клетки, `mineMoveTo` фиксирует pending target, а `AutoMineHandler` инжектит guarded `move_tunnels(direction)` только через существующий `main.php`/WebView-контур.

Фаза 7 отложена: `map_mines.xml` отсутствует в рабочем дереве, а live-формат `var mine`/`mine_ajax.php` без сессионного ответа не подтвержден. Текущий порт не угадывает движение по шахте, чтобы не создать второй ошибочный контур рядом с серверным `mine.js`.

Фаза 7 продолжена по регрессии 3x3: существующий `MineJs` должен augment/wrap server `view_mine()`, построить масштабируемый grid из live `var mine` и заменить только визуальный контейнер карты. Transport, `Digg`, `move_tunnels` и vcode остаются в текущем WebView/postfilter contour.

Фаза 7 продолжена после подключения `map_mines.xml`: `AutoMineManager` lazily читает asset, индексирует клетки по `mineid-level -> x-y`, определяет `mineid` по `var mine`/названию шахты и отдает `img/usefull` в bridge. `MineJs` строит 21x21 grid через `getCellImg/getMineCellHTML`; server 3x3 DOM больше не является основным источником картинок, но остается emergency fallback, если asset/контекст недоступен.

Перепроверка по логам 2026-05-20 16:00: `map_mines asset loaded: mines=12, cells=5292` есть, но `mine page snapshot` отсутствует. Причина: `AutoMineHandler.processMainPhpAutoMineStep(...)` выходил до `updateMinePageSnapshot(...)`, если `Авто-Шахта` выключена. При этом `MineJs` всё равно рендерит шахту для ручного просмотра и вызывает `getCellImg`, но `AutoMineManager` не знает `lastMineId/lastLevel`, поэтому asset-карта не выбирается и картинки остаются пустыми/только 3x3 fallback. Исправление внесено в существующий handler: snapshot обновляется всегда на странице шахты, а авто-действия продолжают выполняться только при включенной автошахте.

- [x] Реализовать scalable DOM render в `MineJs.java` после оригинального `view_mine()`.
- [x] Проверить, что `window.external.getMineCellHTML(x, y, lvl)` вызывается для всех доступных live-клеток, а не только 3x3 вокруг `pos`.
- [x] Сохранить one-shot auto-dig guard `window.__anAutoMineDigClicked` и runtime trace `ANCLIENT_MINE_RUNTIME_PATCH`.
- [x] Исправить пустые картинки клеток: не глушить оригинальный server `show_tunnels()`, а использовать его `mine_new/*.jpg` как fallback для scalable grid.
- [x] Убрать размножение одной fallback-картинки на все клетки и вернуть кликабельность соседних клеток через `move_tunnels(dir)`.
- [x] Подключить `map_mines.xml` в assets и использовать его как основной источник `img/usefull` для всех 21x21 клеток.
- [x] Разрешить ручной native pending-route `mineMoveTo` при выключенной автошахте, но оставить авто-добычу, кирку и факел под toggle `Авто-Шахта`.
- [x] Исправить отсутствие картинок при выключенной автошахте: `updateMinePageSnapshot(...)` теперь вызывается до проверки toggle, чтобы bridge `getCellImg` знал активный `mineid/level` для ручного рендера карты.
- [x] Заменить greedy-шаг до цели на BFS-маршрут по `map_mines.xml` с порядком направлений и проверкой `t/r/b/l`, как в `IBClient/Class80`.
- [x] Инжектить pending mine route до проверки toggle автошахты в `AutoMineHandler`, чтобы ручной маршрут продолжался без включения авто-добычи.
- [x] Добавить `touchend` fallback в `MineJs`, чтобы tap по клетке на Android WebView не терялся из-за drag/pinch `preventDefault`.
- [x] Убрать pseudo-навигацию `main.php?an_auto_mine_move=...`: `mineMoveTo(...)` теперь возвращает первый шаг маршрута, а движение выполняется только через server page JS `move_tunnels(direction)`.
- [x] Нормализовать оставшиеся reload-ссылки `main.php?...` в `AutoMineManager.reloadMainFrame(...)` до `http://neverlands.ru/...`, чтобы WebView не создавал host `main.php`.
- [x] Синхронизировать текущие координаты из JS перед ручным far-click маршрутом и перед каждым route-step через `getNextMineMoveDirection(...)`.
- [x] Использовать общий `window.__anMineRouteLastAt` для suppression между HTML-injection route step и `MineJs` route hooks.
- [x] Исправить DOM-инвариант server `mine.js`: scalable-render не удаляет оригинальный `#main_tunnels/#tunnels_div`, сохраняет его скрытым и больше не перезаписывает глобальный `div_tunnels`; `loadMap(dir)` снова получает `#tunnels_div.lastChild.lastChild` во время `display_move(...)`.
- [x] Подключить `SetNeverTimer` к шахтному `Timer.start(sec)`, чтобы движение/добыча выставляли общий NeverTimer; route-step теперь ждёт `time_left/disable_move/moving_status` и не отправляется до окончания серверного таймера.
- [x] Подготовка факела работает и для manual route при выключенной `Авто-Шахте`: `mineMoveTo`/pending route запрашивают inventory-категорию вещей `wca=4`, а авто-добыча/кирка остаются gated by toggle.
- [x] `MineAjaxPhp` обрабатывает active route/torch responses без раннего выхода по `isAutoMineEnabled()`, но chat-report оставляет только для включенной автошахты.
- [x] Проверка после фикса: `./gradlew2.bat --no-daemon` успешно; изменённые файлы без BOM; targeted grep по изменённым файлам не нашёл новых `AppVars.VCode`, прямого `android.util.Log` или mojibake-паттернов.
- [x] По свежим логам `18_10` найдено зависание `Авто-Шахтёр: Факел`: прямой переход `main.php?im=0&wca=28` внутри шахты возвращает шахтную страницу, а обработчик считал URL уже подходящим inventory-фильтром и повторял `torch_check`.
- [x] Уточнение пользователя: для факелов/фонарей нужна кнопка `Надеть`, не `Использовать`. Исправление внесено в существующий torch-flow: вход в инвентарь строится через штатный `go=inv&vcode`, предмет берётся из `InventoryParser.getWearInvList(...)`, после клика состояние подтверждается через `ParsedDressed.IsWearAutoMineTorch(...)`, затем маршрут возвращается в шахту.
- [x] По свежим логам `18_30` найдено зависание маршрута после успешного движения: `AUTO_MINE_JS route waits timer 1000 source=...:timer` повторялся бесконечно. Root cause в штатном `mine.js`: `show_move()` выставляет `moving_status = 1`, а `finFunction()` после завершения анимации вызывает `show_tunnels()`/`EnableButtons()`, но не возвращает `moving_status` в `0`. Исправление должно быть в существующем `MineJs` route contour: при завершении `finFunction()` сбрасывать stale `moving_status/time_left`, а scheduler держать одним pending timeout, чтобы не плодить параллельные `:timer` цепочки.
- [x] По свежим логам `19_40` найден новый сбой маршрута: после перехода `8/10 -> 8/11` HTML-injection `main_php_mine_page_route` отправил следующий `move_tunnels('down')` сразу после `Timer.start(8000)`, пока штатный `mine.js` держал `disable_move=true`. Далее маршрут повторял один и тот же шаг `8/11 -> 8/12`, позиция оставалась `8/11`, а сервер показал `Невозможно пройти`.
- [x] Исправить route-contour по аналогии с природным Навигатором: direct HTML-injection только планирует штатный `MineJs` route-step, route-step ждёт `time_left/disable_move/moving_status`, перед рендером синхронизирует текущую позицию из JS в `AutoMineManager`, пройденные клетки не подсвечиваются, оставшийся маршрут подсвечивается красным миганием, а ответ `Невозможно пройти` временно блокирует проблемное направление и перестраивает маршрут.
- [x] Добавить bridge-export `WebAppInterface.MarkMineRouteMoveDispatched(...)`, чтобы JS route-step фиксировал последнее отправленное направление в `AutoMineManager` перед `move_tunnels(...)`.
- [x] По свежим логам `20_10` найден torch-flow сбой: после ручного `mineMoveTo(3,3)` pending route сохраняется, открывается `go=inv&wca=28`, но при отсутствии найденной кнопки `Надеть` вызывается `stopBecauseNoTorch()`, который чистит manual pending route (`mine route cleared: reason=no_torch`) и выключает автошахту даже при `autoMineEnabled=false`. Исправление должно быть в текущем `AutoMineHandler.processTorchCheck(...)`/`AutoMineManager.stopBecauseNoTorch(...)`, без нового inventory-контура.
- [x] По свежим логам `20_20` уточнён повторный `Невозможно пройти`: сервер принимал несколько `mine_ajax.php?action=move&dir=right`/`left` и запускал `Timer.start(40000)`, но JS `pos` оставался старым (`5/15`, затем `8/15`). `AutoMineManager` снова строил тот же шаг и повторял тот же `move_tunnels(dir)`, пока сервер фактически уже ушёл дальше и вернул `Невозможно пройти`. Root cause: после успешного `display_move(...)` штатный `mine.js` анимирует ход, но не обновляет `pos`; текущий route-sync доверяет stale `pos`.
- [x] Исправить существующий `MineJs` route-hook: `display_move(tunn,time,dir)` запоминает принятый server move, а wrapper `finFunction()` перед `show_tunnels` сдвигает `pos[1]/pos[0]` по принятому направлению, вызывает bridge-sync и только затем планирует следующий route-step. Это сохраняет штатный transport `move_tunnels(dir)` и убирает повтор одного и того же шага.
- [x] Исправить manual torch hard-stop: если `Авто-Шахта` выключена, но есть pending route, `AutoMineHandler.processTorchCheck(...)` больше не вызывает `stopBecauseNoTorch()` и не чистит маршрут; он возвращает в шахту, помечает torch-ready optimistic fallback, а фактическую валидацию оставляет серверному `mine_ajax.php` (`вам нужен факел` снова запустит проверку).
- [x] По свежим логам `21_05` найден loop после серверного `Вам нужен факел`: `MineAjaxPhp` корректно вызывал `requestTorchCheckBeforeMove("mine_ajax_torch")`, но `AutoMineHandler` открывал `go=inv&wca=28` (свитки), не находил кнопку `Надеть`, затем optimistic fallback выставлял `AutoMineTorchReady=true` и маршрут снова отправлял тот же ход. По IBClient `Class30.smethod_93` факелы/фонари ищутся в `&im=0&wca=4`; исправление внесено в существующий torch-flow без нового HTTP-контура.
- [x] Убрать optimistic fallback после неуспешного поиска факела в отфильтрованном inventory: если `wca=4` не содержит выбранный факел/фонарь с кнопкой `Надеть`, маршрут останавливается через текущий `stopBecauseNoTorch()` вместо повторной отправки заведомо отклоняемого `move_tunnels`.

### Дополнение 2026-05-20: усталость и `Эликсир Блаженства` в шахтном маршруте

- [x] Pre-check существующих контуров: усталость хранится в `CharacterVitalsManager.snapshot().tied`, map-навигация распознает server `too tired` через `MapAjax.containsTooTiredMessage(...)`, а автопитье блажа выполняется единым fast-action `FastActionManager.fastAttackBlazElixir(sourceLabel)`.
- [x] Добавить шахтный fatigue guard в существующий decision point `AutoMineManager.getPendingMoveDirection(...)`: если pending route активен и `tied >= Profile.AutoDrinkBlazTied`, не отдавать direction в JS, а запускать уже существующий fast-action блажа с source `Авто-Шахта`.
- [x] При активном `NeverTimer` не стартовать fast-action сразу: сохранить pending route, запланировать повторную проверку после таймера и только затем дать текущему `FastActionManager`/`MainPhp` контуру применить блаж.
- [x] После успешного шахтного перехода увеличивать runtime-усталость через `CharacterVitalsManager.increaseTied(...)` по аналогии с природной навигацией, чтобы следующий шаг заранее видел приближение к порогу.
- [x] Переиспользовать существующий детектор server-усталости из `MapAjax` в `MineAjaxPhp`: ответ `слишком устал`/`отдохните`/`вы устали` должен ставить усталость в 100, сохранять pending target и запускать тот же fast-action блажа, не блокируя направление как `Невозможно пройти`.
- [x] Проверить, что маршрут не создает новый HTTP-контур: движение остается только через page JS `move_tunnels(dir)`, а блаж идет через текущий `FastActionManager`/`MainPhp` pipeline.

### Дополнение 2026-05-21: раздел инвентаря кирок и замена сломанной кирки

- [x] Pre-check существующих контуров: кирки обрабатываются в `AutoMineHandler.processPickaxeCheck(...)`/`processPickaxeWear(...)`, server `Вам нужна кирка` уже сбрасывает `AutoMineArmedPickaxe` через `AutoMineManager.requestPickaxeCheckBeforeDig(...)`, отдельный HTTP-контур не нужен.
- [x] По запросу пользователя исправить фильтр кирок с `main.php?im=0&wca=2` на `main.php?im=0&wca=3`; `wca=2` относится к другому разделу и не показывает кирки в актуальном инвентаре.
- [x] Сверка с `IBClient/`: в строках/коде есть актуальный переход `main.php?im=0&wca=3` для раздела кирок, а также старые восстановленные строки `wca=2`, поэтому Android должен использовать подтвержденный live-раздел `wca=3`.
- [x] Проверить замену сломанной кирки: `ParsedDressed` хранит долговечность слота в формате `current/max`; кирка считается неготовой только при `current <= 0`, а `1000/1000` и `38/150` являются пригодными значениями и не должны запускать повторный wear-flow.
- [x] Старое состояние логов: до повторного запуска папка `logs/` была пустой, поэтому live-подтверждение было отложено.
- [x] Логи `logs/Critical/20260521_14_20_auto_mine_trace.log` проанализированы: цикл был вызван неправильным guard'ом `dolg > 0`, из-за чего после каждого `wear pickaxe` надетая кирка `1000/1000` или `38/150` считалась сломанной и открывался следующий `wca=3` wear-flow. Исправлено в `ParsedDressed.IsWearAutoMinePickaxe(...)` без нового HTTP-контура.

### Дополнение 2026-05-21: `DIGG@...@NeverTimer`, чат и статистика ресурсов

- [x] По свежим логам `logs/Critical/20260521_14_50_auto_mine_trace.log` подтверждён формат ответа шахты: `DIGG@Обнаружены ресурсы: ... Добываем...@480`, где последний сегмент `480` является серверным `NeverTimer` в секундах.
- [x] Найден существующий контур исправления без нового HTTP-пути: `MineAjaxPhp` уже обрабатывает `mine_ajax.php` и публикует chat-report, `AutoMineManager.shouldDispatchAutoDigFromBridge(...)` уже решает, можно ли нажимать `Digg`, `MineJs` уже планирует JS-side retry, а `ChatStats.addResourceDeltaKg(...)` уже аккумулирует ресурсы в кг для окна статистики.
- [x] Исправить chat-report `mine_ajax.php`: вместо сырого payload с hash-сегментами и `DIGG@...@480` выводить только `Ресурсы: ... кг` и `Задержка: ... сек.` с источником `[auto_mine/mine_ajax]`.
- [x] Добавить учёт ресурсов из `DIGG@...` в существующую статистику `ChatStats`: значения в скобках (`0.3`, `1.2`) считаются килограммами и добавляются через `addResourceDeltaKg(...)`; повтор того же ответа дедуплицируется в `AutoMineManager`.
- [x] Планировать следующий auto-dig не раньше `serverDelay + 2 сек.`: `AutoMineManager` хранит отдельный `nextAutoDigAllowedAtMs`, не меняя общий `AppVars.NeverTimer`, а `MineJs`/HTML-injection спрашивают bridge `getMineAutoDigWaitMs()` и ставят one-shot retry на оставшуюся задержку.
- [x] Убрать лишний route-polling без маршрута: `MineJs.runMineRouteStep(...)` теперь сначала спрашивает bridge `hasPendingMineRoute()`, поэтому после DIGG без выбранной цели не должен каждую секунду писать `route waits timer`.
- [x] Установить иконку `Авто-Шахтёр` для назначенной quick-button и списка добавления авто-функций: `http://image.neverlands.ru/achievement/60/a_60_4.gif` в существующих `getIconUrlForAction(...)` контурах `QuickButtonsPanel` и `FunctionListAdapter`.

### Фаза 8: проверки

- [x] Собрать `./gradlew2.bat --no-daemon`.
- [x] Проверить отсутствие новых `AppVars.VCode` вне `SessionManager`.
- [x] Проверить отсутствие прямого `android.util.Log`/`Log.*` вне разрешенной инфраструктуры.
- [x] Проверить runtime naming: в новых app2 runtime-строках использовать `AN/ANC`, не `AB/ABC/ABCLIENT`.
- [x] Проверить diff на стандартные mojibake-паттерны из `AGENTS.md`.
- [x] Повторная сборка после route-bridge исправления: `./gradlew2.bat --no-daemon` успешна 2026-05-20.
- [x] Повторная сборка после `MarkMineRouteMoveDispatched`: `./gradlew2.bat --no-daemon` успешна; embedded `MineJs` syntax check успешен (`JS_SYNTAX_OK lines=208`).
- [x] Targeted checks по изменённым файлам: нет BOM, нет новых `AppVars.VCode`, прямого `android.util.Log` и стандартных mojibake-паттернов.
- [x] Повторная сборка после фикса stale `pos`/manual torch fallback: `./gradlew2.bat --no-daemon` успешна 2026-05-20; embedded `MineJs` syntax check успешен (`JS_SYNTAX_OK lines=213`); изменённые файлы проверены на BOM (`NO_BOM`).
- [x] Проверка 2026-05-21: `./gradlew2.bat --no-daemon` успешна (`BUILD SUCCESSFUL`); forced `:app2:compileDebugJavaWithJavac --rerun-tasks` тоже успешен, с fallback после недоступного Kotlin daemon и одним существующим deprecation warning в `DeviceKeyStore.java`.
- [x] Targeted checks 2026-05-21: файлы AutoMine/MapAjax/TODO2 без BOM и mojibake; прямого `android.util.Log` в затронутом Java-контуре нет; `AppVars.VCode` найден только в документации `TODO2`; legacy `AB_LOCAL_CHAT`/`ab_nav_*` в `MapAjax.java` существуют вне текущего AutoMine-диффа и требуют отдельного rename-контроля, чтобы не смешивать фиксы.
- [x] Проверка 2026-05-21 после фикса кирок: `./gradlew2.bat --no-daemon` успешна (`BUILD SUCCESSFUL`); targeted checks по `AutoMineHandler.java`, `ParsedDressed.java`, `todo_task_20260519_auto_mine.md`: нет BOM, mojibake, `AppVars.VCode`, прямого `android.util.Log`, старого AutoMine-фильтра `wca=2` для кирок.
- [x] Дополнительная проверка 2026-05-21: `./gradlew2.bat --no-daemon` успешна (`BUILD SUCCESSFUL`); `logs/` и `Logs/` пустые, live-подтверждение кирок/факела/усталости остается внешним шагом.
- [x] Проверка 2026-05-21 после исправления цикла кирок: `./gradlew2.bat --no-daemon` успешна (`BUILD SUCCESSFUL`); targeted checks по `ParsedDressed.java` и `TODO2` без BOM/mojibake, прямого `android.util.Log`, `AppVars.VCode` и старого `isBrokenByCurrentDolg`.
- [x] Проверка 2026-05-21 после `DIGG@...@NeverTimer`/статистики/иконки: `./gradlew2.bat --no-daemon` успешна (`BUILD SUCCESSFUL`); targeted checks по изменённым Java/TODO2 файлам без BOM/mojibake, новых `AppVars.VCode`, прямого `android.util.Log`; `AUTO_MINE` icon URL найден в `QuickButtonsPanel` и `FunctionListAdapter`.
- [x] Проверка ПК-переноса 2026-05-21: `Stop-Process -Name ANClient -Force -ErrorAction SilentlyContinue; & "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\MSBuild\Current\Bin\MSBuild.exe" ANClient.csproj /t:Build /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir=bin\Debug\` успешна (`Сборка успешно завершена`, 0 warnings, 0 errors); `map_mines.xml` скопирован в `ANClient/bin/Debug/map_mines.xml`.
- [x] Targeted checks ПК-переноса 2026-05-21: `Mine/*.cs` и `PostFilter/*Mine*.cs` без стандартных mojibake-паттернов и старых runtime `AB/ABC/ab_*` маркеров; `git diff --check` по целевым tracked файлам не показал новых whitespace-ошибок, но снова сообщил существующую проблему `.gitattributes:7` и CRLF warnings.
- [x] Дополнение 2026-05-21: по явному подтверждению пользователя перенести рабочий контур `Авто-Шахтёр` из `app2` в ПК-версию `ANClient/`, не меняя `AGENTS*.MD`. Использованы существующие точки ПК-клиента: `Filter.Process` для `/js/mine` и `mine_ajax.php`, `ScriptManager` как `window.external` bridge, `MainPhp` как decision point, `FormMain` для кнопки панели и `FormSettingsGeneral` для отдельной вкладки настроек.
- [x] Для ПК-версии не создавать второй сетевой контур: шахтное движение и добыча идут через штатные server JS функции `move_tunnels(dir)` и `Digg(code)`; C# только подготавливает инструменты, хранит состояние, строит карту/маршрут и инжектит bridge-guard.
- [x] Подключить `map_mines.xml` как основной источник 21x21 карты шахт для ПК `ANClient`, с fallback на серверный DOM/JS контекст, если карта не найдена рядом с exe.
- [x] Добавить кнопку `АвтоШахтёр` на `toolbarGame`, синхронизировать с `AppVars.DoAutoMine`, запускать bootstrap через `ReloadMainPhpInvoke()` и выключать конфликтующие non-combat режимы.
- [x] Добавить отдельную вкладку `АвтоШахтёр` в общие настройки ПК-клиента: включение, chat-report, остановка при пустой добыче, список разрешенных кирок (`wca=3`) и факелов/фонарей (`wca=4`).
- [x] Перенести обработку `DIGG@...@480`: парсить ресурсы, публиковать в чат только компактный отчет `Ресурсы`/`Задержка`, планировать следующий `Digg` не раньше `serverDelay + 2 сек.`.
- [x] По логам ПК-версии после `inject mine.js patch` не было bridge/runtime событий (`TraceMineRuntime`, `scalable mine rendered`, `Digg auto click`). Причина найдена в существующем `MineJs.cs`: JS проверял COM bridge-методы как свойства (`if (window.external.getCellImg)`), тогда как WinForms WebBrowser-скрипты проекта вызывают `window.external.Method(...)` напрямую. Из-за этого guard мог отбрасывать все bridge-вызовы без ошибок.
- [x] Исправить существующий `MineJs.cs`, не создавая второй контур: перенесен рабочий app2 scalable-map contour с direct COM calls, сохранением скрытого server `#main_tunnels/#tunnels_div`, fallback-сбором картинок, route-sync, one-shot `Digg`, touch fallback и масштабом карты по колесу мыши.
- [x] Проверка ПК после исправления `MineJs.cs`: `MSBuild ANClient.csproj /t:Build /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir=bin\Debug\` успешна 2026-05-21 (`Сборка успешно завершена`, 0 warnings, 0 errors).
- [x] По свежим ПК-логам `19_10` найдено, почему добыча не стартовала сразу после включения `АвтоШахтёр`: до включения JS видел `digg`, но bridge отклонял его из-за `enabled=False/armed=False`; после проверки кирки `MainPhpAutoMine.cs` возвращал WebBrowser на plain `main.php`, поэтому шахтный HTML с кнопкой `Начать добычу` не переисполнялся до ручного `Вернуться`.
- [x] Исправить существующий ПК-контур `MainPhpAutoMine.cs`, не добавляя новый HTTP path: после успешной проверки/готовности кирки или факела возвращать на шахтный `go=ret` через parsed link/menu `vcode`/fallback, добавляя диагностический маркер `an_auto_mine=1`. Это повторяет рабочую логику app2 `buildReturnToMineHtml(...)` и должно заново загрузить шахтную страницу, где `MineJs.cs` сразу запускает guarded `Digg(code)`.
- [x] Проверка ПК после фикса возврата в шахту: `MSBuild ANClient.csproj /t:Build /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir=bin\Debug\` успешна 2026-05-21 (`Сборка успешно завершена`, 0 warnings, 0 errors); targeted checks по `MainPhpAutoMine.cs` и `TODO2` без BOM/mojibake, новых `AB/ABC/ab_*` runtime-маркеров в `MainPhpAutoMine.cs`; `git diff --check` снова сообщает только существующую проблему `.gitattributes:7` и CRLF warning для `TODO2`.
- [x] По вопросу пользователя найден пропуск ПК-переноса статистики: `MineAjaxPhp.cs` уже парсил `DIGG@Обнаружены ресурсы...@сек` для chat-report, но не передавал `deltaByResourceKg` в статистику, а старый `TypeStat` вообще не имел ресурсного раздела. Исправление внесено в существующий `mine_ajax.php` parser и текущую ПК-статистику: добавлены `ResourceKg`/`ResourceDrop`, сохранение/загрузка профиля, пункт `Добыто ресурсов`, раздел `Добыты ресурсы`, и обновление через `UpdateMineResourceStatsSafe(...)` только для нового dedup-события.
- [x] Проверка ПК после подключения ресурсов к статистике: `MSBuild ANClient.csproj /t:Build /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir=bin\Debug\` успешна 2026-05-22 (`Сборка успешно завершена`, 0 warnings, 0 errors); targeted checks по изменённым `ANClient`/`TODO2` файлам без BOM/mojibake и без новых whitespace-ошибок, кроме существующей проблемы `.gitattributes:7` и CRLF warnings.
- [-] Проверить ручные HTML-клики: автошахта не должна перехватывать ручной клик по кнопке шахты или другой non-fight странице. Нужна live/device-проверка с сессионной шахтой.

## Риски и вопросы для live-проверки

- Точный формат `mine_ajax.php` нужно подтвердить live HAR/log, потому восстановленные строки дают маркеры, но не полную структуру payload.
- `map_mines.xml` подключен, но live/device-проверка всё еще нужна для подтверждения, что `var mine`/название шахты всегда дают корректный `mineid` и уровень.
- Неизвестна точная иконка `Авто-Шахта`; можно использовать временный fallback до нахождения asset.
- Лицензирование `auto_mine` закреплено как non-public: individual `full` или custom grant `auto_mine`.
- Перемещение в шахте требует факел; если сервер списывает факел как расходник, нужен отдельный cooldown/stock guard.

## Итоговые критерии

- `Авто-Шахта` доступна как отдельная quick-button автофункция.
- Состояние хранится в `Profile.AutoMine` и prefs, восстанавливается после логина.
- `/js/mine` и `mine_ajax.php` проходят через app2 postfilter.
- Добыча запускается по `digg`/`Начать добычу`, а не по кладовому `dig`/`Копать`.
- Кирка и факел проверяются и надеваются/используются через существующий inventory/main.php контур.
- Нет новых прямых `AppVars.VCode`, `android.util.Log`, runtime `AB/ABC/ABCLIENT` в app2.
- Файловые логи `AUTO_MINE_TRACE` показывают все decision points.
