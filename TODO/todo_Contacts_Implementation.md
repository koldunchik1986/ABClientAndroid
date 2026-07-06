# Полная миграция контактов app2 -> ANClient

Документ фиксирует фактический план переноса контактной системы из `app2` в WinForms-проект `ANClient`. Цель - не минимальная интеграция, а полноценное расширение существующей цепочки `Contact` -> `ContactsManager` -> `NeverApi` -> profile XML -> TreeView/UI.

## 1. Найденный существующий контур

- `ANClient/Contact.cs` уже является живой моделью контакта: хранит локальные настройки, сравнивает состояния, формирует chat-уведомления, обновляет TreeView.
- `ANClient/ContactsManager.cs` уже управляет `AppVars.Profile.Contacts`, TreeView-группировкой, `Pulse()`, boss contacts, class/tool lookup.
- `ANClient/NeverApi.cs` уже выполняет `getid.cgi` -> `info.cgi?playerid=...&info=1&hmu=1&effects=1&slots=1` через `DirectGameRequestGuard` и браузерный `User-Agent`.
- `ANClient/MyProfile/UserConfigLoad.cs` и `UserConfigSave.cs` уже читают/пишут `contactentry` в profile XML.
- Поэтому новый параллельный менеджер не создаётся: расширяем текущую точку принятия решений.

## 2. Поля app2, которые должны быть представлены в ANClient

- `[x]` `playerID` -> `Contact.PlayerId`, `UserInfo.PlayerId`.
- `[x]` `nick` -> `Contact.Name`, `UserInfo.Nick` с сохранением нормализованного ника сервера.
- `[x]` `playerLevel` -> `Contact.PlayerLevel`, `Contact.Level`, `UserInfo.PlayerLevel`.
- `[x]` `inclination` -> `Contact.Inclination`, `Contact.Align`, `UserInfo.Inclination`.
- `[x]` `inclinationName` -> `Contact.InclinationName`, `UserInfo.InclinationName`.
- `[x]` `clanNumber` -> `Contact.ClanNumber`, `UserInfo.ClanCode`.
- `[x]` `clanIco` -> `Contact.ClanIco`, `Contact.Sign`, `UserInfo.ClanSign`.
- `[x]` `clanName` -> `Contact.ClanName`, `Contact.Clan`, `UserInfo.ClanName`.
- `[x]` `clanStatus` -> `Contact.ClanStatus`, `UserInfo.ClanStatus`.
- `[x]` `gender` -> `Contact.Gender`, `UserInfo.Gender`.
- `[x]` `blockStatus` -> `Contact.BlockStatus`, `UserInfo.BlockStatus`.
- `[x]` `jailStatus` -> `Contact.JailStatus`, `UserInfo.JailStatus`.
- `[x]` `muteSeconds` -> `Contact.MuteSeconds`, `UserInfo.MuteSeconds`.
- `[x]` `muteForumSeconds` -> `Contact.MuteForumSeconds`, `UserInfo.MuteForumSeconds`.
- `[x]` `onlineStatus` -> `Contact.OnlineStatus`, `Contact.IsOnline`, `UserInfo.OnlineStatus`.
- `[x]` `geoLocation` -> `Contact.GeoLocation`, `Contact.Location`, `UserInfo.Location`.
- `[x]` `warLogNumber` -> `Contact.WarLogNumber`, `Contact.FightLog`, `UserInfo.FightLog`.
- `[x]` `classId`, `comment`, `toolId` -> сохранить локальными и не затирать при refresh.
- `[x]` `effectIds`, `effectStates` -> перенос parser/helper из `ContactRenderHelper.java`.

## 3. Сетевой слой и parser

- `[x]` Сделать публичный lookup `NeverApi.GetPlayerId(nick)` с in-memory cache `NameToId`.
- `[x]` Добавить `NeverApi.GetContactByNick(nick)` для добавления/полного обновления.
- `[x]` Добавить `NeverApi.GetContactByPlayerId(playerId)` для refresh без повторного `getid.cgi`.
- `[x]` В `NeverApi.GetAll(nick)` сохранить текущий `UserInfo` контракт, но заполнить все новые app2-поля.
- `[x]` Parser должен поддерживать multiline `info.cgi` (`1|slots`, `2|effects`, `3|info`, `4|hmu`) и singleline fallback.
- `[x]` Для effect parser перенести дедупликацию, суммирование count и CSV-форматы `effectIds`/`effectStates`.
- `[x]` Сетевые запросы оставить через `DirectGameRequestGuard`, `AppVars.LocalProxy`, browser UA; cookie values не логировать.

## 4. Persistence и совместимость profile XML

- `[x]` Сохранить старый формат `contactentry` с атрибутами `name`, `classid`, `toolid`, `sign`, `clan`, `align`, `comments`, `tracing`, `level`.
- `[x]` Добавить новые совместимые атрибуты: `playerid`, `playerlevel`, `inclination`, `inclinationname`, `clannumber`, `clanico`, `clanname`, `clanstatus`, `gender`, `blockstatus`, `jailstatus`, `muteseconds`, `muteforumseconds`, `onlinestatus`, `geolocation`, `warlognumber`, `effectids`, `effectstates`.
- `[x]` При чтении старых профилей использовать fallback из старых полей (`name`, `level`, `align`, `sign`, `clan`).
- `[x]` При refresh сохранять локальные поля `ClassId`, `ToolId`, `Comments`, `Tracing`, `Parent`, `TreeNode`.

## 5. ContactsManager и UI

- `[x]` `ContactsManager.Add(tree, nick)` должен делать полноценное добавление через `getid.cgi` -> `info.cgi`, а не создавать пустой контакт без данных.
- `[x]` Если сеть недоступна, оставить fallback-контакт по нику, чтобы не ломать ручное добавление.
- `[x]` Добавить `RefreshContact`, `RefreshGroupContacts`, `RefreshNeutralContacts`, `RefreshAllContacts` с задержкой между `info.cgi` запросами.
- `[x]` Добавить helper lookup: `GetLevelOfContact`, `GetEffectIdsOfContact`.
- `[x]` TreeView должен использовать клан/админ/ПВ группировку, цвет, иконки склонностей/кланов/травм/молчанки.
- `[x]` Боковой TreeView контактов должен показывать после `nick [level]` иконки `eff_ID.gif` с `[xN](HH:MM)` на базе существующих `effectStates/effectIds`.
- `[x]` Изменения обычных эффектов должны попадать в чат слежения: получение/потеря, изменение количества и явное обновление таймера без спама от обычного обратного отсчёта.
- `[x]` Details textbox должен показывать комментарий и server snapshot: playerID, level, склонность, клан, статус, online/location/fight, эффекты.
- `[x]` Список персонажей комнаты должен получать из contacts-cache `effectStates/effectIds` и показывать после ника HTML-иконки эффектов с количеством и временем истечения, как `app2`.
- `[x]` Context menu/toolbar должны давать ручной refresh контакта, группы и всех контактов.
- `[x]` `Contact.Process()` и `ApplySnapshot()` должны одинаково определять online: приоритет `onlineStatus > 0`, затем legacy fallback по непустой локации; иначе polling мог красить online-персонажей как offline при пустой/скрытой локации.
- `[x]` ANClient: refresh существующих контактов с сохранённым `PlayerId` идёт только через `info.cgi?playerid=...`; `getid.cgi` остаётся только для добавления нового или legacy-контакта без `playerid`.
- `[x]` ANClient: ускорить info-only refresh контактов с `1200ms` до `500ms`, но оставить `1200ms` для lookup-маршрута `getid.cgi` + `info.cgi`; добавить route-диагностику `info.cgi`/`getid+info.cgi` в `ContactsManager`.
- `[x]` ANClient: закрепить обход общей очереди игровых действий для contact API в существующем `ProxyRequestQueue`: `info.cgi`/`getid.cgi` логируются как `contact_api_lookup`, при этом `DirectGameRequestGuard` продолжает вести запросы через `AppVars.LocalProxy` или fail-closed при внешнем proxy.
- `[x]` ANClient: уменьшить мерцание правой панели контактов без нового refresh-контура: обновлять TreeView-ноды только при реальном изменении текста/цвета/иконки/tooltip, переносить ноду между группами под `BeginUpdate/EndUpdate`, синхронизировать `Parent` и инвалидировать только строки с догруженной effect-иконкой.
- `[x]` ANClient: устранить верхний визуальный артефакт owner-draw при частом refresh: контактные строки рисуются единым clipped-путём даже без эффектов, а `ContactsManager.Update()` явно инвалидирует всю строку контакта, включая область effect-иконок/счётчиков за пределами штатного `TreeNode.Bounds`.
- `[x]` ANClient: исключить блокировку фреймовых ссылок во время contact refresh: `ContactsManager` больше не создаёт пачку параллельных `ProcessAsync`/`RefreshContactAsync` задач, которые спят в `WaitContactApiTurn`; все contact refresh ставятся в одну дедуплицированную очередь и выполняются одним worker-потоком.
- `[x]` Android/app2: UI контактов должен считать online как `onlineStatus > 0`, а не только `== 1`; иначе нестандартные положительные статусы сервера отображались offline.
- `[x]` Android/app2: фоновое обновление контактов после логина должно сначала инициализировать `ContactsManager`, иначе по логам refresh стартовал до загрузки `contacts.xml` и мог брать пустой cache.
- `[x]` Android/app2: добавить файловую диагностику `CONTACT_INFO_PARSED`, `updateContact`, queue size и skip missing playerID без cookie values.

## 6. Проверки

- `[x]` Собрать `ANClient` через VS BuildTools MSBuild.
- `[x]` Проверить UTF-8 без BOM в изменённых `.cs`/`.md`.
- `[x]` Проверить diff на mojibake-паттерны `РЎР`, `РџС`, `Ð`, `Ñ`.
- `[x]` Убедиться, что нет новых custom User-Agent идентификаторов и cookie values в логах.
- `[x]` Runtime app2 2026-05-16 15:34: свежий `anclient_v1.1.6.apk` установлен на `192.168.1.100:5555`; `ContactsManager` загрузил `contacts.xml` (`count=54`, `online=32`), `LoginActivity` поставил в очередь `54` refresh-запроса.
- `[x]` Runtime app2 2026-05-16 15:34-15:35: `CONTACT_INFO_PARSED` обработал `54` контакта (`isOnline=true` для `29`, `isOnline=false` для `25`), refresh завершился без пустого cache; подтверждено в файловых логах `20260516_15_30_loginactivity.log`, `20260516_15_30_contactsmanager.log`, `20260516_15_30_apirepository.log`.
- `[x]` ANClient logs 2026-05-16 16:10: desktop-логи найдены в `ANClient/bin/Debug/Logs/Critical`; `getid.cgi` в этом сегменте не найден, refresh шёл по `info.cgi?playerid=...`, а задержка была общим `WaitContactApiTurn sleepMs=1200`.
- `[x]` ANClient build после ускорения refresh: MSBuild `ANClient.csproj` Debug/AnyCPU; сначала проверка в temp `ANClientVerify`, затем штатный `ANClient/bin/Debug`, `0 warnings`, `0 errors`.
- `[x]` ANClient build после queue/flicker правок: MSBuild `ANClient.csproj` Debug/AnyCPU в `ANClient/bin/Debug`, `0 warnings`, `0 errors`.
- `[x]` ANClient logs 2026-05-16 18:30: `proxyrequestqueue.log` подтверждает `reason=contact_api_lookup`; `contactsmanager.log` показывает частые `ProcessAsync` refresh каждые ~500ms, поэтому исправление внесено в owner-draw/row invalidation, а не в сетевой контур.
- `[x]` ANClient build после clipped owner-draw правки: MSBuild `ANClient.csproj` Debug/AnyCPU в `ANClient/bin/Debug`, `0 warnings`, `0 errors`.
- `[x]` ANClient logs 2026-05-16 20:40: при открытии фреймовых ссылок `proxysession.log` почти не получает новых запросов, а `contactsmanager.log` показывает накопление множества `ProcessAsync: key` вокруг одного `WaitContactApiTurn`; причина — ThreadPool/local-proxy starvation от contact refresh backlog.
- `[x]` ANClient build после сериализации contact refresh очереди: MSBuild `ANClient.csproj` Debug/AnyCPU в `ANClient/bin/Debug`, `0 warnings`, `0 errors`.
- `[x]` ANClient повторная проверка 2026-05-16 21:57: текущий `ContactsManager` направляет `Pulse`/manual refresh в один `ContactRefreshWorker`; MSBuild `ANClient.csproj` Debug/AnyCPU снова прошёл с `0 warnings`, `0 errors`.
- `[x]` Static checks 2026-05-16 21:57: UTF-8 BOM отсутствует в изменённых contact-файлах; targeted mojibake grep по `ANClient/*.cs` не нашёл `РЎ`/`Рџ`/`Ð`/`Ñ`; `git diff --check` показывает только уже известные `.gitattributes`/CRLF предупреждения.
- `[ ]` Runtime ANClient после сериализации очереди: вручную запустить `ANClient/bin/Debug/ANClient.exe`, залогиниться, дождаться contact refresh и проверить, что фреймовые/обычные ссылки доходят до `proxysession.log` во время работы `ContactRefreshWorker`.

## 7. Импорт всего клана в контакты

- `[x]` Найти существующий клановый контур: `FormAddClan` и `FormCompas` уже парсили `allnl.ru/clan-players`, а `ContactsManager` уже управлял добавлением контактов.
- `[x]` Вынести общий fetch/parsing состава клана в `NeverApi.GetClanRosterByNick`, чтобы не плодить параллельные ветки.
- `[x]` Заменить устаревший `allnl.ru` на источник из `app2`: `http://service.neverlands.ru/info/clans.txt`.
- `[x]` Перенести формат parser из `app2`: `clanId|clanName|...|players`, где `players` разбираются как `playerId,nick,level,status#...`.
- `[x]` Добавить `ContactsManager.ImportClan(tree, nick, source)`: получение состава клана, быстрое применение snapshot из `clans.txt` к существующим/новым контактам, сохранение profile XML.
- `[x]` После первичного добавления запускать отдельную фоновую проверку через существующую очередь `ImportClan.PostRefresh`, не блокируя появление полного списка контактов.
- `[x]` Для импорта из меню контакта/группы использовать уже известные `Contact.ClanIco`/`Contact.ClanName`, без лишнего `getid/info` для определения клана.
- `[x]` Подключить контекстное действие контактов/групп `Добавить всех из 'Название Клана'`.
- `[x]` Кнопка `Весь Клан` во вкладках `PInfo`/`PName` запускает импорт в контакты напрямую через `ContactsManager`.
