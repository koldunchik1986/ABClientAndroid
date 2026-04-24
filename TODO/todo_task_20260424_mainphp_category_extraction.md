# TODO: новый анализ выноса MainPhp.java по категориям

Дата анализа: 2026-04-24

Файл: `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`

Текущее состояние: 3635 строк.

Цель: уменьшить размер `MainPhp.java`, оставив в нём только фасадные вызовы и публичные точки входа. Логику кода не менять.

## Жёсткие правила выполнения

| Правило | Статус |
| --- | --- |
| Не менять порядок выполнения веток `process()` | `[ ]` Обязательно при реализации |
| Не менять условия `if`, значения флагов, URL, строки сообщений и тайминги | `[ ]` Обязательно при реализации |
| Не добавлять параллельные фиксы рядом с существующими модулями | `[ ]` Обязательно при реализации |
| Использовать существующие модули, если логика уже вынесена | `[ ]` Обязательно при реализации |
| Новые protected-запросы только через `SessionManager`, без новых `AppVars.VCode` | `[ ]` Обязательно при реализации |
| Логи в прикладных модулях только через `AppLog`, без `android.util.Log` | `[ ]` Обязательно при реализации |
| Все новые/изменённые файлы UTF-8 без BOM | `[ ]` Проверить перед сдачей |
| Проверить mojibake в диффе | `[ ]` Проверить перед сдачей |
| После каждого этапа запускать `:app:compileDebugJavaWithJavac` | `[ ]` Проверить перед сдачей |

## Почему нужен новый план

Существующий `TODO/todo_MainPhp_Restructuring.md` исторически полезен, но фактически устарел: он отмечает все этапы завершёнными, при этом текущий `MainPhp.java` всё ещё содержит большой `process()` и ряд реальных helper-блоков.

Новый план ниже построен по текущему коду, а не по старым строкам из предыдущего анализа.

## Уже вынесено, не дублировать

| Категория | Уже существующий файл | Использовать как текущий контур |
| --- | --- | --- |
| Бой, finish-flow, captcha боя, fight notify, fight URL utils | `FightAuto.java` | Да |
| Авто-лечение | `AutoCureHandler.java` | Да |
| Авто-питьё восстановления HP/MA | `AutoDrinkHandler.java` | Да |
| Авто-охота, разделка, нож, ресурсы | `AutoSkinHandler.java` | Да |
| Авто-ярость/снежок | `AutoFuryHandler.java` | Да |
| Инвентарь, поиск инвентаря, parsing, pack/sort/bulk | `InventoryParser.java` | Да |
| Системные server notice сообщения | `ServerNoticeParser.java` | Да |
| Авто-рыбалка, усталость, снасти, капча рыбалки, Blaz | `FishAjaxPhp.java` | Да |
| Быстрые действия | `FastActionManager.java` | Да |
| Авто-клад | `TreasureDig.java` | Да |
| Городская навигация | `MainPhpCityNavigation.java` | Да |

## Что должно остаться в MainPhp.java в финале

| Метод/поле | Финальное состояние |
| --- | --- |
| `process(String address, byte[] array)` | Один вызов `MainPhpProcessOrchestrator.process(address, array)` |
| `onServerPopupMessage(String popupText)` | Один вызов `AutoCureHandler.onServerPopupMessage(popupText)` |
| `extractServerNoticeForUi(...)` | Один вызов `ServerNoticeParser.extractServerNoticeForUi(...)` |
| `postServerNotificationToChat(...)` | Один вызов `ServerNoticeParser.postServerNotificationToChat(...)` |
| `notifyNewFightFromExternalSource(...)` | Один вызов `FightAuto.notifyNewFightFromExternalSource(...)` |
| `syncInventoryCacheFromHtml(...)` | Один вызов `InventoryParser.syncInventoryCacheFromHtml(...)` |
| `buildServerChatTimeHtmlExternal()` | Один вызов нового chat/time bridge или `FightAuto.buildServerChatTimeHtml()` |
| `buildRedirectHtml(...)` | Либо один фасадный вызов `MainPhpRedirects.buildRedirectHtml(...)`, либо все call-site переведены на новый класс |
| `sendInventoryChatMessage(...)` | Либо один фасадный вызов `MainPhpChatBridge.sendInventoryChatMessage(...)`, либо все call-site переведены на новый класс |
| Остальные private wrappers | Удалить из `MainPhp.java` после перевода call-site на профильные модули |

## Категории выноса: полный список

### 1. MainPhpProcessOrchestrator.java

Назначение: вынести главный `process()` целиком, сохранив точный порядок веток.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| `process(String address, byte[] array)` тело метода | `MainPhp.java:2187-2967` | `MainPhpProcessOrchestrator.process(...)` | `[ ]` Не выполнено |
| Decode/initial state: `lastMainPhpResponse`, timers, `Russian.getString`, `Filter.removeDoctype` | `process()` | `MainPhpProcessOrchestrator` | `[ ]` Не выполнено |
| Раннее обновление HP/MA и усталости | `process()` | `MainPhpProcessOrchestrator` вызывает `MainPhpVitals` и `FishAjaxPhp` | `[ ]` Не выполнено |
| Ранний server notice и heavy injury popup | `process()` | `ServerNoticeParser` + `AutoCureHandler` вызовы внутри orchestrator | `[ ]` Не выполнено |
| Fight flags: `isFightFrame`, `isFightTopFrame`, finish address flags | `process()` | `MainPhpProcessOrchestrator` | `[ ]` Не выполнено |
| Post-fight auto-drink sync | `process()` | `AutoDrinkHandler` или отдельный метод в orchestrator | `[ ]` Не выполнено |
| Cure pipeline dispatch | `process()` | вызовы `AutoCureHandler` | `[ ]` Не выполнено |
| FastAction pipeline dispatch | `process()` | вызов `FastActionManager` через `MainPhpHosts` | `[ ]` Не выполнено |
| AutoFish pipeline dispatch | `process()` | `FishAjaxPhp.processMainPhpAutoFishPipeline(...)` | `[ ]` Не выполнено |
| AutoFury pipeline dispatch | `process()` | `AutoFuryHandler.processMainPhpAutoFuryStep(...)` | `[ ]` Не выполнено |
| AutoSkin pipeline dispatch | `process()` | `AutoSkinHandler.processMainPhpAutoSkinStep(...)` | `[ ]` Не выполнено |
| Fight frame dispatch | `process()` | `FightAuto.processFight(...)` через host | `[ ]` Не выполнено |
| Treasure/inventory dispatch | `process()` | `TreasureDig` + `InventoryParser` | `[ ]` Не выполнено |
| Movement/searchbox/map dispatch | `process()` | `MainPhpMovementPipeline` или `MainPhpNavigationHandler` | `[ ]` Не выполнено |
| Финальный `ContentMainPhp`, `Russian.getBytes`, return | `process()` | `MainPhpProcessOrchestrator` | `[ ]` Не выполнено |

Реализация этапа: сначала механически перенести тело `process()` в новый класс без разбиения условий. Только после успешной сборки дробить на приватные pipeline-методы внутри нового класса.

### 2. MainPhpHosts.java

Назначение: убрать из `MainPhp.java` большие bridge-объекты и DTO-маппинг.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| `FIGHT_AUTO_HOST` | `MainPhp.java:117-472` | `MainPhpHosts.fightAutoHost()` | `[ ]` Не выполнено |
| `FAST_ACTION_HOST` | `MainPhp.java:495-627` | `MainPhpHosts.fastActionHost()` | `[ ]` Не выполнено |
| `TREASURE_DIG_HOST` | `MainPhp.java:641-820` | `MainPhpHosts.treasureDigHost()` | `[ ]` Не выполнено |
| Маппинг `InventoryParser.WearInvEntry -> TreasureDig.WearInvEntry` | `TREASURE_DIG_HOST` | `MainPhpHosts` private mapper | `[ ]` Не выполнено |
| Маппинг `MainPhpVitals.InsHpSnapshot -> FightAuto.InsHpSnapshot` | `FIGHT_AUTO_HOST` | `MainPhpHosts` private mapper | `[ ]` Не выполнено |

Правило: host-объекты не должны добавлять новой логики. Только существующие вызовы в уже вынесенные классы.

### 3. MainPhpVitals.java

Назначение: вынести парсинг `ins_HP`/`inshp` и обновление `CharacterVitalsManager`.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| `InsHpSnapshot` | `MainPhp.java:842-849` | `MainPhpVitals.InsHpSnapshot` | `[ ]` Не выполнено |
| `mainPhpInsHp(String html)` | `MainPhp.java:1100-1119` | `MainPhpVitals.mainPhpInsHp(...)` | `[ ]` Не выполнено |
| `parseInsHpSnapshot(String html)` | `MainPhp.java:1124-1153` | `MainPhpVitals.parseInsHpSnapshot(...)` | `[ ]` Не выполнено |
| `parseInsHpSnapshotArgs(String args)` | `MainPhp.java:1155-1183` | `MainPhpVitals` private | `[ ]` Не выполнено |
| `tryParseDoubleInvariant(String raw)` | `MainPhp.java:1257-1275` | `MainPhpVitals` private/static | `[ ]` Не выполнено |
| `AutoDrinkHandler` зависимость от `MainPhp.InsHpSnapshot` | `AutoDrinkHandler.java` | заменить на `MainPhpVitals.InsHpSnapshot` | `[ ]` Не выполнено |
| `FightAuto.Host.parseInsHpSnapshot` mapping | `MainPhp.java` host | перенести mapping в `MainPhpHosts` | `[ ]` Не выполнено |

Проверка после этапа: бой и post-fight auto-drink должны получать те же HP/MA snapshots, без изменения логов и порогов.

### 4. MainPhpRedirects.java

Назначение: вынести генерацию redirect HTML.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| `buildRedirectHtml(String description, String link)` | `MainPhp.java:3280-3287` | `MainPhpRedirects.buildRedirectHtml(...)` | `[ ]` Не выполнено |
| Вызовы из `MainPhp.java` | много мест | заменить на `MainPhpRedirects.buildRedirectHtml(...)` или фасад | `[ ]` Не выполнено |
| Вызовы из `InventoryParser`, `FishAjaxPhp`, `AutoSkinHandler`, `AutoFuryHandler`, `AutoCureHandler` | много мест | по отдельному этапу перевести с `MainPhp.buildRedirectHtml` на `MainPhpRedirects` | `[ ]` Не выполнено |

Правило: текст HTML и `window.location` оставить байт-в-байт эквивалентным текущей реализации.

### 5. MainPhpChatBridge.java

Назначение: вынести отправку локальных HTML-сообщений в чат и внешнее получение server timestamp.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| `sendInventoryChatMessage(String messageHtml)` | `MainPhp.java:3596-3607` | `MainPhpChatBridge.sendInventoryChatMessage(...)` | `[ ]` Не выполнено |
| `buildServerChatTimeHtml()` | `MainPhp.java:3406-3408` | `MainPhpChatBridge.buildServerChatTimeHtml()` делегирует в `FightAuto` | `[ ]` Не выполнено |
| `buildServerChatTimeHtmlExternal()` | `MainPhp.java:3416-3418` | фасад в `MainPhp` или прямой вызов `MainPhpChatBridge` | `[ ]` Не выполнено |
| Call-site из `RoomManager`, `BossAuto`, `MapAjax`, `MapActAjaxPhp`, `FishAjaxPhp`, `NeverApi`, `CompasAuto`, `LezFight` | внешние классы | оставить фасад сначала, переводить отдельным безопасным этапом | `[ ]` Не выполнено |

Правило: формат сообщений `'timestamp-server' + [Источник] + текст` не менять.

### 6. MainPhpNavigationHandler.java

Назначение: вынести общую non-combat навигацию main.php.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| `mainPhpFindPerc(String html)` | `MainPhp.java:1751-1791` | `MainPhpNavigationHandler.mainPhpFindPerc(...)` | `[ ]` Не выполнено |
| `mainPhpFindFlora(String html)` | `MainPhp.java:1805-1836` | `MainPhpNavigationHandler.mainPhpFindFlora(...)` | `[ ]` Не выполнено |
| `mainPhpFindMapReturnForAutoMoving(String html)` | `MainPhp.java:1840-1891` | `MainPhpNavigationHandler.mainPhpFindMapReturnForAutoMoving(...)` | `[ ]` Не выполнено |
| `mainPhpExtractMenuVcode(String html, String menuKey)` | `MainPhp.java:1893-1911` | `MainPhpNavigationHandler` private/static | `[ ]` Не выполнено |
| `startAutoSearchBoxMoving(String destination)` | `MainPhp.java:1492-1512` | `MainPhpNavigationHandler.startAutoSearchBoxMoving(...)` | `[ ]` Не выполнено |
| `getQueryParamValue(...)` wrappers if needed | `MainPhp.java:1737-1739` | оставить в `InventoryParser`, не дублировать | `[ ]` Не выполнено |

Правило: `go=inf`, `go=ret`, fallback через `vcode`, `Вернуться`, `Причал disabled` оставить без изменений.

### 7. MainPhpTimerHandler.java

Назначение: вынести server timer/wtime обработку и HTML-статус автоперехода.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| `WTIME_SYNC_LOG_GUARD_MS` | `MainPhp.java:54` | `MainPhpTimerHandler` | `[ ]` Не выполнено |
| `lastWtimeSyncLogAtMs` | `MainPhp.java:71` | `MainPhpTimerHandler` | `[ ]` Не выполнено |
| `mainPhpWtime(String html)` | `MainPhp.java:1469-1487` | `MainPhpTimerHandler.mainPhpWtime(...)` | `[ ]` Не выполнено |
| `parseUnsignedIntFrom(String text, int fromIndex)` | `MainPhp.java:1514-1548` | `MainPhpTimerHandler` private/static | `[ ]` Не выполнено |
| `extractWtimeTimeoutSeconds(String html)` | `MainPhp.java:1550-1595` | `MainPhpTimerHandler.extractWtimeTimeoutSeconds(...)` | `[ ]` Не выполнено |
| `syncNeverTimerFromWtime(String html, String address)` | `MainPhp.java:1597-1622` | `MainPhpTimerHandler.syncNeverTimerFromWtime(...)` | `[ ]` Не выполнено |

Правило: `SERVER_TIMER_TRACE` и `FileLogger.trace` оставить эквивалентными.

### 8. ComplectWearHandler.java

Назначение: вынести таймерное надевание комплектов, не смешивать с AutoSkin/AutoFury.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| `mainPhpWearComplect(String html, String complectName)` | `MainPhp.java:1382-1440` | `ComplectWearHandler.mainPhpWearComplect(...)` | `[ ]` Не выполнено |
| `COMPLECT_TIMER_PARSE_TRACE` логи | `mainPhpWearComplect` | оставить в новом handler через `AppLog` | `[ ]` Не выполнено |
| Ветка process `AppVars.WearComplect` | `MainPhp.java:2390-2405` | `ComplectWearHandler.processWearComplectStep(...)` | `[ ]` Не выполнено |

Правило: `s=2`, `compl_view(...)`, очистка `AppVars.WearComplect` после попытки должны остаться как сейчас.

### 9. MainPhpFastActionSupport.java

Назначение: вынести fast-action справочники и сообщения об отсутствии предмета.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| `isAttackFastId(String fastId)` | `MainPhp.java:3060-3074` | `MainPhpFastActionSupport.isAttackFastId(...)` | `[ ]` Не выполнено |
| `getInventoryFilter(String fastId)` | `MainPhp.java:3081-3167` | `MainPhpFastActionSupport.getInventoryFilter(...)` | `[ ]` Не выполнено |
| `normalizeFastId(String fastId)` | `MainPhp.java:3175-3183` | `MainPhpFastActionSupport` private/static | `[ ]` Не выполнено |
| `buildFastItemNotFoundMessage(String fastId)` | `MainPhp.java:3613-3634` | `MainPhpFastActionSupport.buildFastItemNotFoundMessage(...)` | `[ ]` Не выполнено |
| `processMainPhpFast(...)` wrapper | `MainPhp.java:2980-2982` | удалить после прямого вызова `FastActionManager` из orchestrator | `[ ]` Не выполнено |

Правило: список FastId и фильтры `wca=28`, `wca=27`, `im=6`, `TOTEM` не менять.

### 10. MainPhpPauseGuards.java

Назначение: вынести маленькие guard-методы, которыми пользуются разные pipeline-блоки.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| `isNonCombatAutoPausedByFastAction()` | `MainPhp.java:1701-1705` | `MainPhpPauseGuards.isNonCombatAutoPausedByFastAction()` | `[ ]` Не выполнено |
| `isNonCombatAutoPausedByCureAction()` | `MainPhp.java:1709-1711` | `MainPhpPauseGuards.isNonCombatAutoPausedByCureAction()` | `[ ]` Не выполнено |

Правило: fight/autoboi flow не должен попадать под эти guard-ы, как и сейчас.

### 11. AutoFish pipeline в FishAjaxPhp

Назначение: убрать из `process()` оставшийся большой блок оркестрации авто-рыбалки, не создавая новый параллельный модуль.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| AutoFish gate и `autoFightReloadProbeAddress` skip | `MainPhp.java:2419-2423` | `FishAjaxPhp.processMainPhpAutoFishPipeline(...)` | `[ ]` Не выполнено |
| Runtime recovery на карте/природе | `MainPhp.java:2425-2431` | `FishAjaxPhp` | `[ ]` Не выполнено |
| Диагностика blocked условий | `MainPhp.java:2433-2446` | `FishAjaxPhp` | `[ ]` Не выполнено |
| `mainPhpPrecheckFishingHandsByInfoApi` и fatigue step | `MainPhp.java:2448-2457` | `FishAjaxPhp` | `[ ]` Не выполнено |
| Проверка skill `AutoFishCheckUm` | `MainPhp.java:2460-2472` | `FishAjaxPhp` через navigation host | `[ ]` Не выполнено |
| Cooldown after drink | `MainPhp.java:2473-2482` | `FishAjaxPhp` | `[ ]` Не выполнено |
| Проверка/надевание удочек | `MainPhp.java:2483-2532` | `FishAjaxPhp` | `[ ]` Не выполнено |
| Возврат на природу и lake form detection | `MainPhp.java:2538-2597` | `FishAjaxPhp` | `[ ]` Не выполнено |
| Wait by `NeverTimer` logs | `MainPhp.java:2599-2603` | `FishAjaxPhp` | `[ ]` Не выполнено |

Правило: использовать существующий `FishAjaxPhp`, потому что там уже находятся `mainPhpFindFish`, `mainPhpAutoFishPrepare`, `mainPhpWearUd`, captcha hold и Blaz-ветки.

### 12. AutoFury pipeline в AutoFuryHandler

Назначение: убрать из `process()` оркестрацию ярости, оставив handler владельцем логики.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| Проверка `isAutoFuryEnabledByPreference()` | `MainPhp.java:2609` | `AutoFuryHandler.processMainPhpAutoFuryStep(...)` | `[ ]` Не выполнено |
| Проверка персонажа и свитка | `MainPhp.java:2612-2625` | `AutoFuryHandler` | `[ ]` Не выполнено |
| Переход в инвентарь `&im=0&wca=28` | `MainPhp.java:2627-2633` | `AutoFuryHandler` через inventory/navigation helpers | `[ ]` Не выполнено |
| Надевание свитка и переключение вкладки | `MainPhp.java:2634-2645` | `AutoFuryHandler` | `[ ]` Не выполнено |

Правило: не менять `AutoFuryCheckScroll`, `AutoFuryArmedScroll`, `AutoFuryHand`.

### 13. AutoSkin pipeline в AutoSkinHandler

Назначение: убрать из `process()` оркестрацию охоты/разделки/ножа, оставив handler владельцем логики.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| Auto-разделка `mainPhpRaz` gate | `MainPhp.java:2650-2657` | `AutoSkinHandler.processMainPhpAutoRazStep(...)` или внутри pipeline | `[ ]` Не выполнено |
| Suspend-флаги finish/inventory/generated | `MainPhp.java:2663-2671` | `AutoSkinHandler.processMainPhpAutoSkinStep(...)` | `[ ]` Не выполнено |
| Inventory reload fallback for skin resources | `MainPhp.java:2681-2694` | `AutoSkinHandler` | `[ ]` Не выполнено |
| Skill check `AutoSkinCheckUm` | `MainPhp.java:2695-2713` | `AutoSkinHandler` | `[ ]` Не выполнено |
| Resource inventory `&im=5` | `MainPhp.java:2714-2727` | `AutoSkinHandler` | `[ ]` Не выполнено |
| Knife check on character | `MainPhp.java:2728-2742` | `AutoSkinHandler` | `[ ]` Не выполнено |
| Knife inventory/wear/switch tab | `MainPhp.java:2743-2764` | `AutoSkinHandler` | `[ ]` Не выполнено |

Правило: использовать существующий `AutoSkinHandler`, не создавать второй hunting/skin handler.

### 14. Movement/SearchBox pipeline

Назначение: убрать из `process()` авто-клад/searchbox/map/moving orchestration.

| Элемент | Текущее место | Целевое место | Статус |
| --- | --- | --- | --- |
| SearchBox bootstrap to map/person | `MainPhp.java:2807-2851` | `MainPhpMovementPipeline.processSearchBoxBootstrap(...)` | `[ ]` Не выполнено |
| `MapAjax.findNextDestForBox` and `startAutoSearchBoxMoving` | `MainPhp.java:2852-2867` | `MainPhpMovementPipeline` | `[ ]` Не выполнено |
| AutoMoving wtime status | `MainPhp.java:2869-2877` | `MainPhpMovementPipeline` + `MainPhpTimerHandler` | `[ ]` Не выполнено |
| City navigation | `MainPhp.java:2878-2885` | `MainPhpMovementPipeline` calls `MainPhpCityNavigation` | `[ ]` Не выполнено |
| Teleport navigation | `MainPhp.java:2886-2891` | `MainPhpMovementPipeline` calls `TeleportAjax` | `[ ]` Не выполнено |
| Return-to-map and nav bootstrap | `MainPhp.java:2892-2914` | `MainPhpMovementPipeline` | `[ ]` Не выполнено |
| Map processing and searchbox retry | `MainPhp.java:2915-2957` | `MainPhpMovementPipeline` | `[ ]` Не выполнено |

Правило: все `SessionManager.getValidVCodeForAction(...)` вызовы для `searchbox_bootstrap`, `nav_bootstrap`, `searchbox_retry_bootstrap` сохранить с теми же action names и fallback.

### 15. Fight wrappers cleanup

Назначение: убрать из `MainPhp.java` private one-line wrappers на `FightAuto`, если после переноса они больше не нужны.

| Wrapper | Текущее место | Действие | Статус |
| --- | --- | --- | --- |
| `buildWaitForTurnAutoRefreshHtml` | `MainPhp.java:864-866` | заменить call-site на `FightAuto` или host | `[ ]` Не выполнено |
| `buildInPlaceFightAutoRefreshHtml` | `MainPhp.java:880-882` | заменить call-site на `FightAuto` или host | `[ ]` Не выполнено |
| `isAutoFightEnabledByPreference` | `MainPhp.java:892-894` | заменить на `FightAuto.isAutoFightEnabledByPreference()` | `[ ]` Не выполнено |
| `recoverAutoboiRuntimeStateIfNeeded` | `MainPhp.java:906-908` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `formatHms` | `MainPhp.java:918-920` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `buildRestoringStatusHtml` | `MainPhp.java:931-943` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `buildDelayedRedirectHtml` | `MainPhp.java:960-962` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `resolveFightCaptchaUrl` | `MainPhp.java:986-988` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `extractCaptchaUrlFromFexp` | `MainPhp.java:1001-1003` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `extractFightFinishLinkFromHtml` | `MainPhp.java:1015-1017` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `extractFightCleanFinishLinkFromHtml` | `MainPhp.java:1033-1035` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `extractFightCleanVcodeFromFexp` | `MainPhp.java:1044-1046` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `extractFightCleanVcodeFromFightTy` | `MainPhp.java:1055-1057` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `resolveFightFinishStateForAct5` | `MainPhp.java:1066-1068` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `escapeHtmlAttr` | `MainPhp.java:1083-1085` | заменить на `FightAuto.escapeHtmlAttr()` | `[ ]` Не выполнено |
| `isFightFrameHtml` | `MainPhp.java:2986-2988` | заменить на `FightAuto.isFightFrameHtml()` | `[ ]` Не выполнено |
| `isAutoFightReloadProbeAddress` | `MainPhp.java:2994-2996` | заменить на `FightAuto.isAutoFightReloadProbeAddress()` | `[ ]` Не выполнено |
| `isAutoFightBackgroundProbeAddress` | `MainPhp.java:3004-3006` | заменить на `FightAuto.isAutoFightBackgroundProbeAddress()` | `[ ]` Не выполнено |
| `isAutoFightProbeAddress` | `MainPhp.java:3014-3016` | заменить на `FightAuto.isAutoFightProbeAddress()` | `[ ]` Не выполнено |
| `buildAutoFightProbeFinishCandidateKey` | `MainPhp.java:3027-3029` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `clearAutoFightProbeFinishCandidate` | `MainPhp.java:3038-3040` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `isAutoFightProbeFinishConfirmed` | `MainPhp.java:3053-3055` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `mainPhpFight` | `MainPhp.java:3299-3301` | заменить в orchestrator на `FightAuto.processFight(...)` | `[ ]` Не выполнено |
| `extractCaptchaUrl` | `MainPhp.java:3319-3321` | оставить public/package facade только если есть внешние call-site | `[ ]` Не выполнено |
| `showFightCaptchaDialogOnce` | `MainPhp.java:3337-3339` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `mainPhpFightEnd` | `MainPhp.java:3344-3346` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `getUrlParam` | `MainPhp.java:3350-3352` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `parseUrlParamInt` | `MainPhp.java:3357-3359` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `appendOrReplaceUrlParam` | `MainPhp.java:3364-3366` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `publishFightResultFromLogsIfNeeded` | `MainPhp.java:3378-3380` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `notifyNewFight` | `MainPhp.java:3392-3394` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `notifyFightStopped` | `MainPhp.java:3472-3474` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `notifyCaptchaRejectedOnce` | `MainPhp.java:3485-3487` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `publishFightSummaryFromFinishHtmlIfNeeded` | `MainPhp.java:3505-3507` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `extractJsArrayTokens` | `MainPhp.java:3512-3514` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `parseIntFromJsToken` | `MainPhp.java:3518-3520` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `extractBattleXpFromHtml` | `MainPhp.java:3521-3523` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `registerFightEnd` | `MainPhp.java:3533-3535` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `registerFightEndByLogId` | `MainPhp.java:3541-3543` | заменить на `FightAuto` | `[ ]` Не выполнено |
| `logFightVariable` | `MainPhp.java:3548-3550` | заменить на `FightAuto` | `[ ]` Не выполнено |

Правило: если wrapper используется только внутри host, переносить host раньше cleanup.

### 16. Inventory wrappers cleanup

Назначение: убрать из `MainPhp.java` private one-line wrappers на `InventoryParser`, если после переноса они больше не нужны.

| Wrapper | Текущее место | Действие | Статус |
| --- | --- | --- | --- |
| `applyInventoryFilterToLink` | `MainPhp.java:1695-1697` | заменить на `InventoryParser.applyInventoryFilterToLink(...)` | `[ ]` Не выполнено |
| `isInventoryAddress` | `MainPhp.java:1724-1726` | заменить на `InventoryParser.isInventoryAddress(...)` | `[ ]` Не выполнено |
| `inventoryAddressMatchesFilter` | `MainPhp.java:1731-1733` | заменить на `InventoryParser.inventoryAddressMatchesFilter(...)` | `[ ]` Не выполнено |
| `getQueryParamValue` | `MainPhp.java:1737-1739` | заменить на `InventoryParser.getQueryParamValue(...)` | `[ ]` Не выполнено |
| `getWearInvList` | `MainPhp.java:2108-2110` | заменить call-site на `InventoryParser.getWearInvList(...)` или mapper в нужном module | `[ ]` Не выполнено |
| `parseWearInvEntry` | `MainPhp.java:2121-2125` | заменить на `InventoryParser.parseWearInvEntryPublic(...)` | `[ ]` Не выполнено |
| `containsIgnoreCase` | `MainPhp.java:3193-3195` | заменить на `InventoryParser.containsIgnoreCase(...)` | `[ ]` Не выполнено |
| `mainPhpIsInv` | `MainPhp.java:3200-3202` | заменить на `InventoryParser.mainPhpIsInv(...)` | `[ ]` Не выполнено |
| `hasInventoryRows` | `MainPhp.java:3210-3212` | заменить на `InventoryParser.hasInventoryRows(...)` | `[ ]` Не выполнено |
| `isLikelyInventoryReloadSnapshot` | `MainPhp.java:3214-3216` | заменить на `InventoryParser.isLikelyInventoryReloadSnapshot(...)` | `[ ]` Не выполнено |
| `isGeneratedTransitionPage` | `MainPhp.java:3223-3225` | заменить на `InventoryParser.isGeneratedTransitionPage(...)` | `[ ]` Не выполнено |
| `mainPhpFindInv` | `MainPhp.java:3240-3242` | заменить на `InventoryParser.mainPhpFindInv(...)` | `[ ]` Не выполнено |
| `mainPhpFindInvWithFallback` | `MainPhp.java:3247-3249` | заменить на `InventoryParser.mainPhpFindInvWithFallback(...)` | `[ ]` Не выполнено |
| `mainPhpFindInvArena` | `MainPhp.java:3260-3262` | заменить на `InventoryParser.mainPhpFindInvArena(...)` | `[ ]` Не выполнено |
| `mainPhpFindInvBuilding` | `MainPhp.java:3267-3269` | заменить на `InventoryParser.mainPhpFindInvBuilding(...)` | `[ ]` Не выполнено |
| `mainPhpFindInvOld` | `MainPhp.java:3273-3275` | заменить на `InventoryParser.mainPhpFindInvOld(...)` | `[ ]` Не выполнено |
| `mainPhpInv` overloads | `MainPhp.java:3574-3586` | заменить на `InventoryParser.mainPhpInv(...)` | `[ ]` Не выполнено |

Правило: сначала перевести host/pipeline call-site, потом удалять wrappers.

### 17. Server notice wrappers cleanup

Назначение: оставить публичный API или перевести внешние call-site на `ServerNoticeParser`.

| Wrapper | Текущее место | Действие | Статус |
| --- | --- | --- | --- |
| `extractServerNoticeFromMainHtml` | `MainPhp.java:2152-2154` | удалить после переноса process в orchestrator | `[ ]` Не выполнено |
| `extractServerNoticeFromPlainText` | `MainPhp.java:2156-2158` | удалить после проверки call-site | `[ ]` Не выполнено |
| `extractServerNoticeForUi` overloads | `MainPhp.java:2166-2172` | временно оставить фасад для `MainActivity`/`LocalHttpProxyServer` | `[ ]` Не выполнено |
| `postServerNotificationToChat` | `MainPhp.java:3420-3422` | временно оставить фасад для `LocalHttpProxyServer` | `[ ]` Не выполнено |
| `shouldSuppressAutoFishPopupChatNotice` | `MainPhp.java:3424-3426` | удалить после переноса call-site | `[ ]` Не выполнено |
| `shouldAppendAutoCureTarget` | `MainPhp.java:3428-3430` | удалить после переноса call-site | `[ ]` Не выполнено |
| `resolveAutoCureNoticeTargetNick` | `MainPhp.java:3432-3434` | удалить после переноса call-site | `[ ]` Не выполнено |
| `normalizeServerNotificationText` | `MainPhp.java:3436-3438` | удалить после переноса call-site | `[ ]` Не выполнено |
| `resolveServerNotificationType` | `MainPhp.java:3440-3442` | удалить после переноса call-site | `[ ]` Не выполнено |
| `containsAny` | `MainPhp.java:3444-3446` | заменить на `ServerNoticeParser.containsAny(...)` | `[ ]` Не выполнено |
| `escapeHtmlText` | `MainPhp.java:3448-3450` | заменить на `ServerNoticeParser.escapeHtmlText(...)` | `[ ]` Не выполнено |

Правило: не менять дедуп `SERVER_NOTICE_CHAT_DEDUP_MS` и source labels.

## Предлагаемый порядок реализации

| Этап | Действие | Ожидаемый эффект | Статус |
| --- | --- | --- | --- |
| 0 | Зафиксировать baseline сборкой до правок | Понимание исходного состояния | `[ ]` Не выполнено |
| 1 | Вынести `MainPhpRedirects`, `MainPhpChatBridge`, `MainPhpVitals`, `MainPhpPauseGuards` | Убрать независимые helper-блоки | `[ ]` Не выполнено |
| 2 | Вынести `MainPhpHosts` | Убрать ~700 строк bridge-кода из `MainPhp` | `[ ]` Не выполнено |
| 3 | Механически перенести тело `process()` в `MainPhpProcessOrchestrator` без изменения тела | Главный файл станет фасадом | `[ ]` Не выполнено |
| 4 | Вынести navigation/timer/complect/fast-support | Убрать оставшуюся реальную логику из `MainPhp` | `[ ]` Не выполнено |
| 5 | Перенести AutoFish/AutoFury/AutoSkin pipeline-блоки из orchestrator в существующие модули | Декомпозиция по доменам без дублей | `[ ]` Не выполнено |
| 6 | Перенести Movement/SearchBox pipeline в отдельный файл | Изолировать навигационный state machine | `[ ]` Не выполнено |
| 7 | Удалить private wrappers на `FightAuto`, `InventoryParser`, `ServerNoticeParser` | Сократить фасадный шум | `[ ]` Не выполнено |
| 8 | Почистить imports/comments, проверить compile, BOM, mojibake, `AppVars.VCode`, `android.util.Log` | Финальная стабилизация | `[ ]` Не выполнено |

## Финальная структура файлов

| Файл | Роль |
| --- | --- |
| `MainPhp.java` | Тонкий фасад: public API и однострочные вызовы |
| `MainPhpProcessOrchestrator.java` | Порядок обработки `main.php`, без доменной реализации |
| `MainPhpHosts.java` | Host-адаптеры для `FightAuto`, `FastActionManager`, `TreasureDig` |
| `MainPhpVitals.java` | `ins_HP`/`inshp` parsing и `CharacterVitalsManager` sync |
| `MainPhpRedirects.java` | Генерация redirect HTML |
| `MainPhpChatBridge.java` | Локальные chat notifications и server timestamp facade |
| `MainPhpNavigationHandler.java` | `go=inf`, `go=ret`, return-to-map, menu vcode, searchbox start |
| `MainPhpTimerHandler.java` | `wtime`, `tdsec`, `NeverTimer`, moving status HTML |
| `ComplectWearHandler.java` | Таймерное надевание комплектов |
| `MainPhpFastActionSupport.java` | FastId filters и сообщения об отсутствии предмета |
| `MainPhpPauseGuards.java` | Non-combat pause guards |
| `MainPhpMovementPipeline.java` | AutoMoving/SearchBox/Map orchestration |
| `FishAjaxPhp.java` | AutoFish pipeline и существующая рыбалка |
| `AutoFuryHandler.java` | AutoFury pipeline и существующий scroll wear |
| `AutoSkinHandler.java` | AutoSkin pipeline и существующая охота |

## Контрольные проверки после реализации каждого этапа

| Проверка | Команда/действие | Статус |
| --- | --- | --- |
| Java compile | `./gradlew --no-daemon :app:compileDebugJavaWithJavac` | `[ ]` Не выполнено |
| Новые `AppVars.VCode` | поиск `AppVars\.VCode` в `app/src/main/java` | `[ ]` Не выполнено |
| Прямой `android.util.Log` | поиск `import android.util.Log;` и `Log.d/i/w/e` | `[ ]` Не выполнено |
| BOM | проверка изменённых `.java` и `.md` | `[ ]` Не выполнено |
| Mojibake | поиск типовых mojibake-маркеров из `AGENTS.MD` в диффе | `[ ]` Не выполнено |
| Ручные HTML клики | убедиться, что новые pipeline не запускают дополнительные probe/redirect контуры | `[ ]` Не выполнено |
| AutoFight | порядок `markFightInProgress -> new LezFight -> buildFrame -> submitFightTurn` не изменён | `[ ]` Не выполнено |
| AutoFish/FastAction | каждый `fastStart` сохраняет существующий `fastCancel` path | `[ ]` Не выполнено |

## Риски и как их снижать

| Риск | Снижение риска |
| --- | --- |
| Случайная смена порядка веток в `process()` | Сначала переносить тело целиком в `MainPhpProcessOrchestrator`, без разбиения |
| Циклические зависимости после выноса hosts | Сначала вынести общие helpers (`Redirects`, `ChatBridge`, `Vitals`), затем hosts |
| Дублирование URL/redirect utilities | Не создавать новый URL-utils на первом этапе, использовать `FightAuto`/`InventoryParser` как текущие контуры |
| Поломка external call-site на `MainPhp.buildServerChatTimeHtmlExternal()` | Сначала оставить фасад, внешний перевод делать отдельным этапом |
| Потеря VCode fallback в навигации | Action names `searchbox_bootstrap`, `nav_bootstrap`, `searchbox_retry_bootstrap` оставить без изменений |
| Потеря файлового логирования | При переносе строк с `FileLogger.trace` переносить их вместе с логикой |

## Критерий завершения

| Критерий | Статус |
| --- | --- |
| `MainPhp.java` содержит только фасадные вызовы и минимум DTO/API, без длинного `process()` | `[ ]` Не выполнено |
| Все доменные блоки находятся в профильных файлах | `[ ]` Не выполнено |
| Сборка проходит | `[ ]` Не выполнено |
| Нет новых прямых `AppVars.VCode` | `[ ]` Не выполнено |
| Нет новых `android.util.Log` в прикладном коде | `[ ]` Не выполнено |
| UTF-8 без BOM и без mojibake | `[ ]` Не выполнено |
