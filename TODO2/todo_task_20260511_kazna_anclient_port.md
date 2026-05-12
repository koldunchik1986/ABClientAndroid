# Задача 2026-05-11: порт модуля Казна в ANClient C#

## Цель

Перенести поведение Android-модуля `Казна` из `app2` в C# проект `ANClient`, используя существующий postfilter-контур `MainPhpClanKazna`, чтобы серверный HTML-ответ казны модифицировался локально под выбранный режим отображения.

## Найденный существующий контур

- [x] `ANClient/PostFilter/MainPhp.cs` уже содержит `MainPhpClanKazna(...)`, `ClanKaznaUrl`, переход в `main.php?wfo=1&useaction=clan-action&addid=3` и обработку очереди взятия комплекта.
- [x] `ANClient/ANForms/FormMain.cs` уже содержит меню `Клановая казна`, режимы отображения и сохранение комплектов в `AppVars.Profile.ClanKaznaComplects`.
- [x] Поэтому новый параллельный сетевой или UI-контур не создаётся: дорабатывается существующая модификация HTML-ответа сервера.

## Что перенесено из app2

- [x] Режимы `Все`, `Арты`, `Рары`, `Обычные`.
- [x] Классификация `Арты`: коэффициент вида `1.xx`/`2.xx` в строке предмета.
- [x] Классификация `Рары`: нет коэффициента арта и максимальная долговечность `>= 300`.
- [x] Классификация `Обычные`: нет коэффициента арта и максимальная долговечность `< 300`.
- [x] В HTML-ответ казны добавлена верхняя панель со счётчиками и ссылками переключения режима.
- [x] В меню `Клановая казна` добавлен пункт `Обычные`.

## Доработка после уточнения: полноценная структура модуля

- [x] Создана папка `ANClient/Kazna/` по аналогии с Android model/parser/renderer слоем.
- [x] Добавлены модели `KaznaItem`, `KaznaCategory`, `KaznaSnapshot` с полями Android-модуля: `uid`, `displayName`, `baseName`, `owner`, `durabilityText`, `currentDurability`, `maxDurability`, `status`, `free`, `artifactCoefficient`, `takeUrl`, `donateUrl`, `sourceUrl`, `categoryWca`, `categoryTitle`, `rowHtml`.
- [x] Добавлен `KaznaParser`: разбирает категории `wca`, строки казны, `uid`, action-ссылки `Взять из казны`/`Пожертвовать`, долговечность, статус и коэффициент арта.
- [x] Добавлен `KaznaHtmlRenderer`: строит панель, счётчики, категории и применяет фильтрацию HTML-ответа сервера через parsed snapshot.
- [x] `MainPhpClanKaznaApplyView(...)` теперь делегирует модификацию HTML в `ANClient.Kazna.KaznaHtmlRenderer.Render(...)`, а `MainPhp` остаётся только точкой подключения существующего postfilter-контура.
- [x] Новые файлы подключены в `ANClient/ANClient10.csproj` и `ANClient/ANClient.csproj`.

## Проверки

- [x] `msbuild` BuildTools найден по явному пути и сборка `ANClient.csproj` выполнена успешно.
- [-] `dotnet build ANClient/ANClient10.csproj -c Debug` не дошёл до компиляции кода из-за отсутствующего targeting pack `.NETFramework,Version=v2.0`.
- [x] Проверить сборку в окружении с установленным .NET Framework 2.0/совместимым MSBuild.

## Доработка 2026-05-11 после ошибки загрузки казны

- [x] Найден существующий контур обрыва: `ANProxy/Session.cs` вызывал `Filter.Process(...)` без защиты, поэтому исключение postfilter могло оставить фрейм без ответа.
- [x] Добавлен `ProcessCustomFilterSafe(...)`: при исключении пишет stacktrace в `ProxySession` и возвращает исходный ответ сервера вместо пустой/оборванной загрузки.
- [x] `MainPhpClanKaznaApplyView(...)` обёрнут в `try/catch` с логами `Kazna`: `render start`, `render complete`, `render failed`.
- [x] `KaznaParser.NormalizeMainUrl(...)` теперь нормализует относительные ссылки в `http://www.neverlands.ru/...`, чтобы category/action-ссылки оставались в домене, который обрабатывает postfilter.

## Доработка 2026-05-11 после проверки логов `ANClient/bin/Debug/Logs`

- [x] В `20260511_22_30_kazna.log` найдено падение renderer-а: `System.ArgumentException: oldValue` в `KaznaParser.ParseName(...)` при пустом `artifactCoefficient`.
- [x] Исправлен `KaznaParser.ParseName(...)`: удаление коэффициента из `<b>` выполняется только если коэффициент реально найден.
- [x] `KaznaHtmlRenderer` теперь добавляет видимый app2-like блок `Распарсованный список ANClient` над серверной таблицей: имя, uid, статус, долговечность, коэффициент и рабочие ссылки `Взять из казны`/`Пожертвовать` из HTML-ответа.
- [x] Сборка `ANClient.csproj` после правки прошла успешно: 0 errors, 0 warnings.

## Доработка UI и кэша деталей по app2

- [x] Renderer больше не показывает старый серверный список предметов: `KaznaHtmlRenderer.Render(...)` возвращает отдельный HTML-документ ANClient.
- [x] Добавлена верхняя кнопка `Вернуться к основному окну` со ссылкой на `http://www.neverlands.ru/main.php`.
- [x] Action-ссылки `Взять из казны` и `Пожертвовать` сделаны визуально яркими кнопками (`take` зелёная, `donate` оранжевая), но остаются прямыми `href` из HTML-ответа сервера.
- [x] Добавлено чередование строк: чётные карточки светлые, нечётные темнее.
- [x] Renderer переведён на более современный card-style HTML: панели, pill-фильтры, категории, карточки с изображением, meta-блоком и свойствами.
- [x] Доанализирован app2-контур `KaznaItemDetailsCache` / `KaznaItemDetailsParser` / `InventoryParser.syncKaznaItemDetailsCacheFromHtml(...)`.
- [x] В ANClient добавлены `KaznaItemDetails`, `KaznaItemDetailsCache`, `KaznaItemDetailsParser`.
- [x] `MainPhpInv(...)` теперь пополняет `info/<nick>/kazna/uids.txt` из уже полученного HTML инвентаря без дополнительных запросов.
- [x] `KaznaHtmlRenderer` читает `uids.txt` и показывает картинки/свойства при точном совпадении UID или безопасном совпадении по видимой сигнатуре, как в app2.

## Доработка комплектов как в app2

- [x] Доанализирован app2-контур `KaznaSet`, `KaznaSetAdapter`, `KaznaActivity` и `KaznaManager.collectSet(...)`.
- [x] Добавлены `KaznaSet` и `KaznaSetStore` для работы с существующим профильным `AppVars.Profile.ClanKaznaComplects` без второго хранилища.
- [x] В HTML-инъекцию добавлена вкладка/кнопка `Комплекты` и `+ Комплект`.
- [x] В карточках предметов добавлены кнопки добавления UID в существующий комплект и создания нового комплекта через prompt.
- [x] Во вкладке `Комплекты` показываются локальные комплекты, предметы комплекта, свойства/картинки из `uids.txt`, кнопки `Собрать`, `Удалить комплект`, `Убрать из комплекта`.
- [x] `MainPhpClanKazna(...)` обрабатывает URL actions `create`, `add`, `remove`, `delete`, `collect` и переиспользует существующий `ClanKaznaComplectQueue` для последовательного взятия вещей из казны.
- [x] Сборка `ANClient.csproj` после добавления комплектов прошла успешно: 0 errors, 0 warnings.

## Доработка app2: HTML-инъекция казны в WebView

- [x] Найден существующий Android-контур: `MainPhp.process(...)` как единая точка postfilter для `main.php`, `KaznaParser` для разбора HTML, `KaznaManager` для snapshot/cache/sets, `KaznaItemDetailsCache` для картинок и свойств.
- [x] Добавлен `KaznaManager.acceptPostfilterHtml(...)`, чтобы прямой HTML-ответ казны сохранялся в уже существующий cache-контур без второго сетевого слоя.
- [x] Добавлен `postfilter/KaznaHtmlInjectionRenderer.java`: строит modern HTML-документ с режимами `Все/Арты/Рары/Обычные/Комплекты`, категориями, карточками предметов, action-кнопками и локальными комплектами.
- [x] `MainPhp.process(...)` подключает renderer для `main.php?useaction=clan-action&addid=3` до инвентарного postfilter, чтобы серверная казна не обрабатывалась как обычный инвентарь.
- [x] `./gradlew.bat :app2:compileDebugJavaWithJavac --rerun-tasks` выполнен успешно; остался один существующий deprecation warning в `DeviceKeyStore.java`.
- [x] Mojibake-проверка по `РЎ|Рџ|Ð|Ñ` для изменённых `postfilter`, `manager` и этого TODO-файла не нашла совпадений.
