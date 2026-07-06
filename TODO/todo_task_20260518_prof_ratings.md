# Рейтинги Проф в справочнике Таблицы

## Найденный существующий контур

- [x] `TablesActivity` уже реализует современный справочник `Таблицы` без XML-разметки: верхние tabs, status card, search и карточки содержимого.
- [x] `RecipeDatabase` остаётся источником рецептов; новый рейтинг не должен ломать или дублировать базу рецептов.
- [x] Для сетевых справочников уже используется `NetworkClient.getInstance()` и браузерный `AppVars.BROWSER_USER_AGENT`.
- [x] Для локальных сообщений в чат уже есть `Chat.addMessageToChat(...)` и единый timestamp через `MainPhp.buildServerChatTimeHtmlExternal()`.
- [x] Ник текущего персонажа берётся из `AppVars.Profile.UserNick` с fallback на `AppVars.RuntimeAuthUserNick`.

## Требования

- [x] Добавить в `Таблицы` раздел `Рейтинги Проф`.
- [x] Внутри раздела сделать категории по id weekly-рейтингов: 1-14 и 51-54.
- [x] Загружать `http://service.neverlands.ru/rate/weekly_{id}.txt` как `windows-1251`.
- [x] Парсить строки формата `clanTotem|clanIco|Nick|level|rate`, где номер места равен порядку строки.
- [x] Отображать место, значок склонности, значок клана, ник, уровень, info-ссылку и очки рейтинга.
- [x] Открывать `pinfo.cgi?nick` из строки рейтинга.
- [x] Каждое воскресенье после 15:00 серверного времени проверять Top 10 всех категорий.
- [x] Если текущий ник попал в Top 10, раз в час выводить локальное сообщение в чат для каждой категории.

## План реализации

- [x] Создать `ProfessionRatingRepository` для списка категорий, загрузки и парсинга weekly-файлов.
- [x] Встроить раздел `Рейтинги Проф` в существующий `TablesActivity` без нового экрана.
- [x] Создать `ProfessionRatingMonitor` для hourly Sunday-check и локальных чат-уведомлений.
- [x] Подключить monitor к уже существующему UI timer в `MainActivity.startTimer()`.
- [x] Собрать `:app2:assembleDebug` и выполнить проверки UTF-8/BOM/mojibake.

## Проверки

- [x] `cmd.exe /c gradlew.bat :app2:assembleDebug --console=plain --no-daemon` — BUILD SUCCESSFUL.
- [x] UTF-8 BOM отсутствует в `ProfessionRatingRepository.java`, `ProfessionRatingMonitor.java`, `TablesActivity.java`, `MainActivity.java`, этом TODO-файле.
- [x] Targeted mojibake grep по новым/изменённым файлам рейтингов не нашёл признаков битой кириллицы.
- [x] `git diff --check` показывает только уже известные предупреждения `.gitattributes` и CRLF.

## ANClient Desktop

### Найденный существующий контур

- [x] `ANClient\Info\ForpostInfoController.cs` уже отвечает за вкладку `Справочник` и локальную навигацию `anclient://forpost` / `anclient://tables`.
- [x] Для справочников уже используется browser User-Agent и `DirectGameRequestGuard.Prepare(...)`, поэтому новый weekly-loader подключён к этому же fail-closed/proxy контуру.
- [x] Локальные сообщения в чат уже проходят через `FormMain.WriteChatMsgSafe(...)`, который добавляет серверный timestamp по `Profile.ServDiff`.
- [x] Runtime tick уже существует в `FormMainTicks.TimerCrap()`, поэтому монитор рейтингов подключён туда без отдельного таймера.

### План реализации

- [x] Создать `ANClient\Info\ProfessionRatingRepository.cs` для категорий, загрузки weekly-файлов CP1251, парсинга `clanTotem|clanIco|Nick|level|rate` и helper-ссылок.
- [x] Создать `ANClient\Info\ProfessionRatingMonitor.cs` для воскресной hourly Top10-проверки и локальных chat-уведомлений.
- [x] Встроить `Рейтинги Проф` первым разделом в `ForpostInfoController`.
- [x] Сделать категории 1-14 и 51-54, вывод строк с местом, иконками, ником `[level]`, info icon и очками.
- [x] Открывать `pinfo.cgi?nick` из строки рейтинга через существующую вкладочную навигацию ANClient.
- [x] Подключить monitor к `TimerCrap()`.
- [x] Добавить новые `.cs` в `ANClient.csproj` и `ANClient10.csproj`.
- [x] Собрать `ANClient.csproj` через VS BuildTools.
- [x] Проверить UTF-8 BOM/mojibake по новым desktop-файлам.

### Проверки Desktop

- [x] `Stop-Process -Name ANClient -Force -ErrorAction SilentlyContinue; MSBuild.exe ANClient.csproj /t:Build /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir=bin\Debug\` — BUILD SUCCESSFUL, 0 warnings, 0 errors.
- [x] UTF-8 BOM отсутствует в новых/изменённых desktop-файлах рейтингов и project files.
- [x] Targeted mojibake grep по `ANClient\Info` и этому TODO-файлу не нашёл совпадений.
- [x] `cmd.exe /c gradlew.bat :app2:assembleDebug --console=plain --no-daemon` — BUILD SUCCESSFUL.
- [x] `git diff --check` показывает только уже известные предупреждения `.gitattributes` и CRLF.
