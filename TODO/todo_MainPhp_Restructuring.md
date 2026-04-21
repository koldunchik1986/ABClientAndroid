# Анализ MainPhp.java — план реструктуризации (вынос модулей)

**Дата анализа:** 2026-04-20
**Файл:** `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java` (5956 строк, ~158 методов)

## Принцип

- **НЕ менять логику и код** — только перемещать методы в соответствующие модули
- В MainPhp остаются **только вызовы функций** из вынесенных модулей
- Используем существующие файлы где возможно, создаём Handler'ы по правилу п.7 AGENTS.md
- Bridge-паттерн (Host-интерфейсы) для двусторонних зависимостей

---

## Сводная таблица: что → куда

| Домен | Методов | Строк | Куда выносить | Тип файла |
|-------|---------|-------|---------------|-----------|
| Бой (Finish/Notify) | 15 | ~1200 | **FightAuto.java** (расширить) | Существующий |
| Авто-Лечение | 22 | ~750 | **AutoCureHandler.java** (новый) | Handler (п.7) |
| Авто-питьё | 4 | ~300 | **AutoDrinkHandler.java** (новый) | Handler |
| Авто-Охота/Разделка | 9 | ~400 | **AutoSkinHandler.java** (новый) | Handler |
| Авто-Ярость | 3 | ~120 | **AutoFuryHandler.java** (новый) | Handler |
| Капча | 5 | ~180 | **CaptchaHandler.java** (новый) | Handler |
| Инвентарь (Find/Parse) | 12 | ~500 | **InventoryParser.java** (расширить) | Существующий |
| URL-утилиты | 8 | ~250 | **UrlUtils.java** (новый) | Утилита |
| JS-парсинг | 4 | ~100 | **JsParseUtils.java** (новый) | Утилита |
| Чат/Уведомления | 8 | ~300 | **ServerNoticeParser.java** (новый) | Handler |
| Таймер/Комплект | 4 | ~200 | Оставить в MainPhp (тесная связь с process) | — |
| Vitals/HP-MA | 4 | ~100 | Оставить (инфраструктура process) | — |
| HTML-генерация | 5 | ~200 | **FightAuto.java** (fight-related) + общие в MainPhp | — |
| Паузы/Управление | 3 | ~40 | Оставить (координация) | — |
| process() оркестратор | 1 | ~780 | Сократится после выноса | — |

---

## Детальный план по модулям

### 1. FightAuto.java — расширить (болевые finish/notify методы)

**Уже делегировано:** `mainPhpFight()` → `FightAuto.processFight()`

**Перенести из MainPhp в FightAuto:**

| Метод | Строки MainPhp | Примечание |
|-------|----------------|------------|
| `mainPhpFightEnd` | 4921-4986 | Обработка завершения боя (get_id=61&act=7) |
| `extractFightFinishLinkFromHtml` | 724-771 | Ссылка завершения |
| `extractFightCleanFinishLinkFromHtml` | 782-825 | "Голая" ссылка (act=5) |
| `extractFightCleanVcodeFromFexp` | 827-841 | Vcode из fexp[3] |
| `extractFightCleanVcodeFromFightTy` | 843-857 | Vcode из fight_ty[5] |
| `resolveFightFinishStateForAct5` | 862-876 | st=6/7 из fight_ty[4] |
| `publishFightResultFromLogsIfNeeded` | 5059-5172 | Публикация итога |
| `publishFightSummaryFromFinishHtmlIfNeeded` | 5488-5549 | Fallback-сводка |
| `primeLastBoiDamageFromFinishHtmlIfNeeded` | 5566-5587 | Добор урона |
| `extractBattleXpFromHtml` | 5615-5639 | XP из finish-HTML |
| `registerFightEnd` | 5649-5652 | Обёртка регистрации |
| `registerFightEndByLogId` | 5658-5672 | Учёт статистики |
| `logFightVariable` | 5677-5691 | Диагностика |
| `notifyNewFight` | 5184-5212 | Анонс боя |
| `notifyNewFightFromExternalSource` | 5405-5413 | Внешняя точка |
| `notifyFightStopped` | 5424-5444 | Стоп автобоя |
| `buildWaitForTurnAutoRefreshHtml` | 468-478 | HTML ожидания хода |
| `buildInPlaceFightAutoRefreshHtml` | 488-506 | Инъекция auto-refresh |
| `buildDelayedRedirectHtml` | 626-640 | Отложенный redirect |
| `buildRestoringStatusHtml` | 571-614 | Статус лечения |
| `formatHms` | 554-560 | Вспомогательный |

**Зависимости:** Расширить `FightAuto.Host` для доступа к `showFightCaptchaDialogOnce`, `sendInventoryChatMessage`, `buildServerChatTimeHtml`.

**В MainPhp останется:** Делегат `mainPhpFightEnd()` → `FightAuto.mainPhpFightEnd(...)` + Host-прокси.

---

### 2. AutoCureHandler.java — НОВЫЙ Handler (правило п.7: >3 каскадных условий)

**Перенести из MainPhp:**

| Метод | Строки MainPhp | Примечание |
|-------|----------------|------------|
| `mainPhpAutoCureStep` | 2547-2686 | Основная логика (каскад: яд→травма→эликсир→аптечка) |
| `mainPhpExternalRequestedCureStep` | 2400-2508 | Внешний запрос лечения |
| `mainPhpBuildPoisonCureForm` | 2688-2732 | Форма лечения яда |
| `mainPhpTrySelfWoundCureByElixir` | 2751-2781 | Попытка эликсира |
| `mainPhpBuildSelfWoundCureElixirRedirect` | 2810-2840 | Редирект эликсира |
| `mainPhpBuildWoundCureForm` | 2842-2914 | Форма аптечки |
| `isSelfWoundElixirNavigationOnlyResult` | 2791-2801 | Проверка навигации |
| `clearExternalCureRequest` | 2520-2527 | Очистка состояния |
| `isAutoCureEnabledByPreference` | 1438-1450 | Проверка включённости |
| `isAutoCureSelfElixirEnabledForWound` | 1475-1482 | Проверка типа |
| `isAutoCureWoundTypeEnabledForTravm` | 1487-1494 | Проверка типа |
| `isAutoCureWoundTypeEnabledForSelfByAnyMethod` | 1501-1506 | Комбинированная |
| `parseCureTravmType` | 1508-1518 | Парсинг |
| `disableAutoCureAndNotify` | 2952-2979 | Отключение + нотификация |
| `decrementSelfWoundCounterIfNeeded` | 2916-2925 | Декремент |
| `isSelfNick` | 2927-2933 | Проверка self |
| `woundIndexFromTravm` | 2935-2950 | Маппинг индексов |
| `handleHeavyInjurySignal` | 2300-2318 | Сигнал тяжёлой травмы |
| `isHeavyInjurySignalText` | 2327-2341 | Детектор |
| `queueSelfHeavyInjuryCureIfNeeded` | 2351-2377 | Приоритетное лечение |
| `syncInjuriesFromMapHeavyPopup` | 2274-2276 | Fallback-синхронизация |
| `onServerPopupMessage` | 2284-2286 | Обработка popup |

**Константы/поля для переноса:**
- `AUTO_CURE_POISON_POTION_NAME`
- `AUTO_CURE_SELF_ELIXIR_NAME`
- `POISON_INDEX`, `LIGHT_WOUND_INDEX`, `MEDIUM_WOUND_INDEX`, `HEAVY_WOUND_INDEX`
- `MAP_HEAVY_INJURY_POPUP_MARKER`
- `lastMapHeavyInjurySyncAtMs`

**Зависимости (Host-интерфейс):** `buildServerChatTimeHtml`, `sendInventoryChatMessage`, `postServerNotificationToChat`.

---

### 3. AutoDrinkHandler.java — НОВЫЙ Handler

**Перенести из MainPhp:**

| Метод | Строки MainPhp | Примечание |
|-------|----------------|------------|
| `tryTriggerAutoDrinkRestoreElixir` | 1008-1171 | Полная цепочка |
| `tryBuildAutoDrinkSnapshotFromPinfo` | 1179-1283 | Синхронизация info.cgi |
| `isPostFightAutoDrinkFollowupAddress` | 1285-1293 | Адресный детект |
| `isServerPlainMainAddress` | 1304-1330 | Адресный детект |

**Константы/поля для переноса:**
- `AUTO_DRINK_TRIGGER_COOLDOWN_MS`
- `lastAutoDrinkTriggerAtMs`
- `lastAutoDrinkBlazTriggerAtMs`
- `autoDrinkPostFightSyncPending`
- `autoDrinkPostFightSyncPendingSinceMs`

**Примечание:** FishAjaxPhp уже содержит `mainPhpResolveAutoDrinkBlazPending` и `mainPhpAutoDrinkBlazStep` — это Blaz-ветка, а AutoDrinkHandler будет для RestoreElixir-ветки.

---

### 4. AutoSkinHandler.java — НОВЫЙ Handler

**Перенести из MainPhp:**

| Метод | Строки MainPhp | Примечание |
|-------|----------------|------------|
| `mainPhpRaz` | 1783-1811 | Поиск разделки |
| `buildRazLinkFromFightTyPayload` | 1813-1849 | Сборка ссылки |
| `extractRazLinkFromHtml` | 1857-1862 | Fallback |
| `mainPhpProcessSkills` | 1363-1413 | Чтение умения "Охота" |
| `isAutoSkinEnabledByPreference` | 1417-1430 | Проверка |
| `maybeMarkAutoSkinKnifeRecheck` | 1615-1627 | Периодический флаг |
| `mainPhpArmedKnife` | 2988-2994 | Проверка ножа |
| `mainPhpWearKnife` | 2998-3022 | Надевание ножа |
| `mainPhpGetSkinRes` | 3066-3171 | Чтение ресурсов |

**Константы/поля для переноса:**
- `AUTO_SKIN_KNIFE_RECHECK_INTERVAL_MS`

**Зависимости (Host-интерфейс):** `sendInventoryChatMessage`, `buildServerChatTimeHtml`, `getWearInvList`.

---

### 5. AutoFuryHandler.java — НОВЫЙ Handler

**Перенести из MainPhp:**

| Метод | Строки MainPhp | Примечание |
|-------|----------------|------------|
| `isAutoFuryEnabledByPreference` | 1596-1610 | Проверка |
| `mainPhpArmedFuryScroll` | 3026-3032 | Проверка свитка |
| `mainPhpWearFuryScroll` | 3038-3062 | Надевание свитка |

**Зависимости (Host-интерфейс):** `getWearInvList`.

---

### 6. CaptchaHandler.java — НОВЫЙ Handler

**Перенести из MainPhp:**

| Метод | Строки MainPhp | Примечание |
|-------|----------------|------------|
| `extractCaptchaUrl` | 4812-4845 | Regex-извлечение |
| `resolveFightCaptchaUrl` | 660-687 | Каскадный выбор |
| `extractCaptchaUrlFromFexp` | 695-717 | Из fexp[4] |
| `showFightCaptchaDialogOnce` | 4861-4916 | Показ с дедуп |
| `notifyCaptchaRejectedOnce` | 5455-5470 | Отклонение с дедуп |

**Константы/поля для переноса:**
- `CAPTCHA_FALLBACK_TTL_MS`
- `lastFightCaptchaDialogKey`, `lastFightCaptchaDialogAtMs`
- `lastCaptchaRejectKey`, `lastCaptchaRejectAtMs`

**Зависимости:** `AppVars.ACTION_SHOW_CAPTCHA`, `LocalBroadcastManager`, `sendInventoryChatMessage`.

---

### 7. InventoryParser.java — расширить (postfilter/InventoryParser.java уже существует)

**Перенести из MainPhp:**

| Метод | Строки MainPhp | Примечание |
|-------|----------------|------------|
| `mainPhpInv` (оба варианта) | 5723-5908 | Полный парсинг |
| `mainPhpFindInv` | 4595-4641 | 5 стратегий |
| `mainPhpFindInvWithFallback` | 4646-4677 | С fallback |
| `mainPhpFindInvArena` | 4688-4702 | Арена |
| `mainPhpFindInvBuilding` | 4707-4724 | Здания |
| `mainPhpFindInvOld` | 4728-4768 | Старый шаблон |
| `getWearInvList` | 3175-3209 | Список wear-предметов |
| `parseWearInvEntry` | 3220-3240 | Парсинг записи |
| `mainPhpIsInv` | 4505-4513 | Детект инвентаря |
| `hasInventoryRows` | 4521-4551 | Структурный детект |
| `isLikelyInventoryReloadSnapshot` | 4553-4565 | Детект переходного кадра |
| `isGeneratedTransitionPage` | 4572-4580 | Детект промежуточной страницы |

**Внутренние классы для переноса:**
- `WearInvEntry` (строки 440-443)

---

### 8. UrlUtils.java — НОВАЯ утилита

**Перенести из MainPhp:**

| Метод | Строки MainPhp | Примечание |
|-------|----------------|------------|
| `normalizeNeverlandsMainLink` | 1869-1890 | Нормализация |
| `findMainPhpLinkByQueryParts` | 1895-1927 | Поиск ссылки |
| `getUrlParam` | 4990-5004 | Извлечение параметра |
| `getQueryParamValue` | 2073-2096 | Извлечение значения |
| `parseUrlParamInt` | 5009-5022 | parseInt |
| `setOrAppendQueryParam` | 1983-2007 | Установка параметра |
| `appendOrReplaceUrlParam` | 5027-5047 | Замена параметра |
| `applyInventoryFilterToLink` | 1932-1963 | Фильтр к ссылке |

**Примечание:** `setOrAppendQueryParam` и `appendOrReplaceUrlParam` — дубликаты (п.8 AGENTS.md). Объединить в один метод.

---

### 9. JsParseUtils.java — НОВАЯ утилита

**Перенести из MainPhp:**

| Метод | Строки MainPhp | Примечание |
|-------|----------------|------------|
| `splitJsTopLevelCsv` | 3251-3303 | Парсинг JS-массивов |
| `trimJsToken` | 3313-3323 | Нормализация |
| `parseIntFromJsToken` | 5604-5614 | parseInt |
| `extractJsArrayTokens` | 5591-5600 | Извлечение токенов |

---

### 10. ServerNoticeParser.java — НОВЫЙ Handler

**Перенести из MainPhp:**

| Метод | Строки MainPhp | Примечание |
|-------|----------------|------------|
| `extractServerNoticeFromMainHtml` | 3325-3367 | Парсинг HTML |
| `extractServerNoticeFromPlainText` | 3369-3424 | Парсинг plain |
| `extractServerNoticeForUi` (обе перегрузки) | 3432-3442 | Публичный доступ |
| `postServerNotificationToChat` | 5244-5294 | Публикация |
| `normalizeServerNotificationText` | 5329-5349 | Нормализация |
| `resolveServerNotificationType` | 5351-5372 | Классификация |
| `shouldSuppressAutoFishPopupChatNotice` | 5296-5304 | Подавление |
| `shouldAppendAutoCureTarget` | 5306-5315 | Доп. цель |
| `resolveAutoCureNoticeTargetNick` | 5317-5327 | Определение ника |

**Константы/поля для переноса:**
- `SERVER_NOTICE_CHAT_DEDUP_MS`
- `lastServerNoticeAtMs`, `lastServerNoticeKey`

---

### Остаётся в MainPhp (координация + инфраструктура)

| Методы | Назначение |
|--------|-----------|
| `process()` | Оркестратор — после выноса сократится до диспетчеризации |
| `mainPhpInsHp` / `parseInsHpSnapshot` / `parseInsHpSnapshotArgs` | Vitals-парсинг (используется в process напрямую) |
| `tryParseDoubleInvariant` | Общий утилит для Vitals |
| `mainPhpWearComplect` | Таймер-комплекты (используется только из process) |
| `mainPhpWtime` | Таймер (используется только из process) |
| `extractWtimeTimeoutSeconds` / `syncNeverTimerFromWtime` / `parseUnsignedIntFrom` | Таймер-утилиты |
| `isNonCombatAutoPausedByFastAction` / `isNonCombatAutoPausedByCureAction` | Паузы (координация) |
| `getAutoFunctionsManagerSafe` | Безопасное получение менеджера |
| `isAutoFightEnabledByPreference` / `recoverAutoboiRuntimeStateIfNeeded` | ~~Боевая координация~~ → **Перенесены в FightAuto** |
| `isFightFrameHtml` / `isAutoFight*` | ~~Probe-детекторы~~ → **Перенесены в FightAuto** |
| `buildAutoFightProbeFinishCandidateKey` / `clearAutoFightProbeFinishCandidate` / `isAutoFightProbeFinishConfirmed` | ~~Probe-финиш~~ → **Перенесены в FightAuto** |
| `isInventoryAddress` / `inventoryAddressMatchesFilter` | Адресные детекторы |
| `mainPhpFindPerc` / `mainPhpIsPerc` / `mainPhpFindFlora` | Навигация (используются из process) |
| `startAutoSearchBoxMoving` / `mainPhpFindMapReturnForAutoMoving` / `mainPhpExtractMenuVcode` | Навигация |
| `buildRedirectHtml` | Общая HTML-утилита |
| `containsIgnoreCase` / `containsAny` / `escapeHtmlText` / `escapeHtmlAttr` | Общие утилиты |
| `sendInventoryChatMessage` / ~~`buildServerChatTimeHtml`~~ / `buildServerChatTimeHtmlExternal` | buildServerChatTimeHtml → **FightAuto**, остальное в MainPhp |
| `isAttackFastId` / `getInventoryFilter` / `normalizeFastId` / `buildFastItemNotFoundMessage` | Fast-Action утилиты |
| `syncInventoryCacheFromHtml` | Кэш (используется из process) |
| `InsHpSnapshot` (внутренний класс) | DTO для Vitals |

---

## Порядок реализации (рекомендуемый)

1. **~~UrlUtils.java + JsParseUtils.java~~** — отложено, включено в FightAuto (методы уже перенесены как часть FightAuto-расширения)
2. **~~FightAuto.java (расширение)~~** — ✅ ВЫПОЛНЕНО (этапы 1-4)
3. **CaptchaHandler.java** — замкнутый модуль, мало зависимостей
4. **ServerNoticeParser.java** — чат-уведомления, зависит от Chat
5. **AutoCureHandler.java** — самый объёмный Handler (22 метода)
6. **AutoDrinkHandler.java** — 4 метода, зависит от FastActionManager
7. **AutoSkinHandler.java** — 9 методов, зависит от getWearInvList
8. **AutoFuryHandler.java** — 3 метода, простейший Handler
9. **InventoryParser.java (расширение)** — инвентарные методы

### Прогресс FightAuto.java (этапы 1-4 завершены):

**Перенесённые методы (тела в FightAuto, делегаты в MainPhp):**

| Этап | Методы | Количество |
|------|--------|-----------|
| 1. Утилиты | formatHms, getUrlParam, parseUrlParamInt, appendOrReplaceUrlParam, extractJsArrayTokens, parseIntFromJsToken, splitJsTopLevelCsv, trimJsToken, escapeHtmlAttr | 9 |
| 2. HTML/URL | buildRestoringStatusHtml, buildDelayedRedirectHtml, buildWaitForTurnAutoRefreshHtml, buildInPlaceFightAutoRefreshHtml, normalizeNeverlandsMainLink, findMainPhpLinkByQueryParts, setOrAppendQueryParam | 7 |
| 3. Капча + автобой | resolveFightCaptchaUrl, extractCaptchaUrlFromFexp, extractCaptchaUrl, extractFightFinishLinkFromHtml, extractFightCleanFinishLinkFromHtml, extractFightCleanVcodeFromFexp, extractFightCleanVcodeFromFightTy, resolveFightFinishStateForAct5, showFightCaptchaDialogOnce, mainPhpFightEnd, mainPhpFight, publishFightResultFromLogsIfNeeded, publishFightSummaryFromFinishHtmlIfNeeded, primeLastBoiDamageFromFinishHtmlIfNeeded, extractBattleXpFromHtml, registerFightEnd, registerFightEndByLogId, logFightVariable, isAutoFightEnabledByPreference, recoverAutoboiRuntimeStateIfNeeded, notifyNewFight, notifyNewFightFromExternalSource, notifyFightStopped, notifyCaptchaRejectedOnce, buildServerChatTimeHtml | 25 |
| 4. Probe | isFightFrameHtml, isAutoFightReloadProbeAddress, isAutoFightBackgroundProbeAddress, isAutoFightProbeAddress, buildAutoFightProbeFinishCandidateKey, clearAutoFightProbeFinishCandidate, isAutoFightProbeFinishConfirmed | 7 |
| **Итого** | | **48 методов** |

**Перенесённые поля/константы:**
- CAPTCHA_FALLBACK_TTL_MS, lastFightCaptchaDialogKey, lastFightCaptchaDialogAtMs, lastCaptchaRejectKey, lastCaptchaRejectAtMs
- lastFightResultWinnerBroadcastKey, lastFightResultLootBroadcastKey, lastFightSummaryBroadcastKey
- AUTO_FIGHT_PROBE_FINISH_CONFIRM_WINDOW_MS, lastAutoFightProbeFinishCandidateKey, lastAutoFightProbeFinishCandidateAtMs

**Результат:**
- MainPhp: 5956 → 4996 строк (-960)
- FightAuto: 963 → 2065 строк (+1102)

**Host-делегаты обновлены:** FIGHT_AUTO_HOST теперь вызывает FightAuto напрямую (не через MainPhp) для 12 методов.

**Остаются в MainPhp (по плану):** process(), mainPhpInsHp, InsHpSnapshot, mainPhpWearComplect, mainPhpWtime, isNonCombatAutoPaused*, mainPhpRaz, mainPhpFindPerc/Flora, startAutoSearchBoxMoving, sendInventoryChatMessage, isInventoryAddress, buildRedirectHtml, containsIgnoreCase/Any, escapeHtmlText, isAttackFastId, getInventoryFilter, normalizeFastId, syncInventoryCacheFromHtml, mainPhpInv, mainPhpFindInv*, mainPhpIsInv, mainPhpProcessSkills

---

## Ожидаемый результат

| Метрика | До | После (фактически) |
|---------|-----|-------|
| Строк в MainPhp.java | ~5956 | ~3063 |
| Методов с телами в MainPhp | ~158 | ~70 (координация + делегаты) |
| Новых Handler'ов | 0 | 5 (AutoCureHandler, AutoDrinkHandler, AutoSkinHandler, AutoFuryHandler, ServerNoticeParser) |
| Расширено существующих | 0 | 2 (FightAuto, InventoryParser) |

### Все этапы завершены ✅

| Модуль | Методов | Строк | Статус |
|--------|---------|-------|--------|
| FightAuto.java (расширение) | 48 | 2065 | ✅ |
| AutoCureHandler.java (новый) | 22+1 | 679 | ✅ |
| AutoDrinkHandler.java (новый) | 4 | 324 | ✅ |
| AutoSkinHandler.java (новый) | 9 | 330 | ✅ |
| AutoFuryHandler.java (новый) | 3 | 58 | ✅ |
| ServerNoticeParser.java (новый) | 12 | 284 | ✅ |
| InventoryParser.java (расширение) | 17 | 640 | ✅ |
| **Итого вынесено** | **~115** | **~4380** | |

**Сборка:** BUILD SUCCESSFUL
