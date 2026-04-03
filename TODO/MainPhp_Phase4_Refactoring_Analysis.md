# MainPhp.java (6229 строк) - PHASE 4 REFACTORING ANALYSIS

**Дата:** 2026-04-03  
**Цель:** Подготовка к модульной рефакторизации согласно Rule 6  
**Охват:** ALL major methods + dependencies + refactoring recommendations  

---

# 1. ALL MAJOR METHODS WITH LINE RANGES

## 1.1 PUBLIC ENTRY POINTS

| Метод | Назначение | Строки | Возвр. |
|-------|-----------|--------|--------|
| `process(address, array)` | **ГЛАВНЫЙ ФИЛЬТР** - обработка ВСЕХ ответов main.php | 4012-4790 | `byte[]` |
| `onServerPopupMessage(text)` | Обработка server popup сообщений | 2676-2692 | void |
| `buildServerChatTimeHtmlExternal()` | Chat time для внешних источников | 5835-5846 | String |
| `notifyNewFightFromExternalSource(fight, html)` | Уведомление о новом бое (внешний) | 5846-5865 | void |
| `syncInventoryCacheFromHtml(html)` | Cache-only парсинг инвентаря | 6145-6170 | void |

## 1.2 FIGHT HANDLING METHODS

| Метод | Строки | Назначение | Сложность | VCode |
|-------|--------|-----------|-----------|-------|
| `isFightFrameHtml(html)` | 4797-4803 | Определение боевого фрейма | Low | No |
| `isAutoFightReloadProbeAddress(address)` | 4805-4822 | Определение reload-probe | Low | No |
| `isAutoFightBackgroundProbeAddress(address)` | 4823-4836 | Определение bg-probe | Low | No |
| `isAutoFightProbeAddress(address)` | 4837-4849 | ANY probe (reload OR bg)? | Low | No |
| `mainPhpFight(address, html)` | 5333-5352 | Обработка боя | Low | No |
| `mainPhpFightEnd(address, html)` | 5509-5584 | Обработка finish боя | **High** | Yes |
| `resolveFightCaptchaUrl(html)` | 694-731 | Парсинг URL капчи боя | **High (6+ regex)** | No |
| `extractCaptchaUrlFromFexp(html)` | 731-761 | Парсинг капчи из fexp | Medium | No |
| `extractFightFinishLinkFromHtml(html, placeholder)` | 761-819 | Парсинг finish-ссылки | **[REFACTOR] Very High (15+ условий)** | No |
| `extractFightCleanFinishLinkFromHtml(html)` | 819-872 | Clean finish-ссылка | **[REFACTOR] High** | No |
| `resolveFightFinishStateForAct5(html)` | 872-896 | Определение finish-state для act=5 | Medium | No |
| `showFightCaptchaDialogOnce(url, link, logBoi)` | 5404-5473 | UI капча диалог | **[REFACTOR]** | No |
| `publishFightResultFromLogsIfNeeded(html, addr, hint)` | 5653-5778 | Парсинг результатов боя | **[REFACTOR] Very High (20+ условий)** | No |
| `registerFightEnd(fight)` | 6091-6100 | Регистрация конца боя | Low | No |
| `registerFightEndByLogId(logId, source)` | 6100-6121 | По LogId | Low | No |
| `isAutoFightProbeFinishConfirmed(logBoi, link)` | 4885-4904 | Подтверждение finish | Low | No |
| `buildAutoFightProbeFinishCandidateKey(logBoi, link)` | 4850-4869 | Ключ finish-кандидата | Low | No |
| `clearAutoFightProbeFinishCandidate()` | 4869-4885 | Очистка finish-кандидата | Low | No |
| `notifyNewFight(fight)` | 5778-5820 | Broadcast новый бой | Medium | No |
| `notifyFightStopped(fight)` | 5865-5897 | Broadcast бой остановлен | Low | No |
| `notifyCaptchaRejectedOnce(code, vcode)` | 5897-5930 | Broadcast капча отклонена | Low | No |
| `primeLastBoiDamageFromFinishHtmlIfNeeded(html, logId)` | 6007-6033 | Парсинг последнего дамага | Medium | No |
| `extractBattleXpFromHtml(html)` | 6057-6091 | Парсинг XP боя | Low | No |
| `extractJsArrayTokens(html, prefix)` | 6033-6046 | Парсинг JS массива | Low | No |
| `parseIntFromJsToken(token, fallback)` | 6046-6057 | Парсинг числа из JS | Low | No |
| `logFightVariable(html, varName)` | 6121-6145 | Логирование JS переменной | Low | No |

**FIGHT HANDLING ИТОГО:** 23 метода, ~1400 строк (22% файла)

---

## 1.3 AUTO-CURE METHODS (Самоисцеление)

| Метод | Строки | Назначение | Сложность | VCode |
|-------|--------|-----------|-----------|-------|
| `mainPhpAutoCureStep(address, html)` | 2941-3079 | **ГЛАВНЫЙ цикл cure** | **[REFACTOR] Very High (>20 условий)** | Yes |
| `mainPhpExternalRequestedCureStep(address, html)` | 2795-2913 | Cure по внешнему запросу | **[REFACTOR] High (10 условий)** | Yes |
| `clearExternalCureRequest(reason)` | 2913-2942 | Очистка запроса | Low | No |
| `queueSelfHeavyInjuryCureIfNeeded(tag)` | 2743-2796 | Очередь тяжёлых травм | Medium | No |
| `handleHeavyInjurySignal(text, tag)` | 2692-2720 | Обработка сигнала травм | Low | No |
| `isHeavyInjurySignalText(text)` | 2720-2744 | Проверка текста травм | Low | No |
| `syncInjuriesFromMapHeavyPopup(html)` | 2666-2677 | Sync травм из popup | Low | No |
| `mainPhpBuildPoisonCureForm(html, nick)` | 3079-3143 | Форма лечения яда | Medium | Yes |
| `mainPhpTrySelfWoundCureByElixir(address, html, label)` | 3143-3183 | Cure раны эликсиром | **[REFACTOR] High (7 условий)** | Yes |
| `isSelfWoundElixirNavigationOnlyResult(html)` | 3183-3202 | Проверка only-nav результата | Medium | No |
| `mainPhpBuildSelfWoundCureElixirRedirect(html)` | 3202-3234 | Редирект на эликсир | Low | No |
| `mainPhpBuildWoundCureForm(html, cureTravm, nick)` | 3234-3308 | **Форма помощи раненому** | **[REFACTOR] High (10+ условий + switch)** | Yes |
| `decrementSelfWoundCounterIfNeeded(nick, travm, source)` | 3308-3319 | Уменьшение счётчика | Low | No |
| `isSelfNick(nick)` | 3319-3327 | Проверка что это я | Low | No |
| `woundIndexFromTravm(travm)` | 3327-3344 | Индекс раны от типа | Low | No |
| `disableAutoCureAndNotify(msg, poison, wounds)` | 3344-3378 | Отключение + уведомление | Low | No |
| `buildAutoFishDrinkCooldownHtml(remainingMs)` | 3378-3391 | Ожидание cooldown | Low | No |
| `isAutoCureEnabledByPreference()` | 1415-1431 | Проверка профиля | Low | No |
| `isAutoCureSelfElixirEnabledForWound(travm)` | 1456-1468 | Проверка эликсира | Low | No |
| `isAutoCureWoundTypeEnabledForTravm(travm)` | 1468-1482 | Проверка типа раны | Low | No |
| `isAutoCureWoundTypeEnabledForSelfByAnyMethod(travm)` | 1482-1489 | ANY способ? | Low | No |
| `parseCureTravmType(travm)` | 1489-1503 | Парсинг типа (1-4) | Low | No |

**AUTO-CURE ИТОГО:** 22 метода, ~900 строк (14%)

---

## 1.4 AUTO-FISHING METHODS

| Метод | Строки | Назначение | Сложность | VCode |
|-------|--------|-----------|-----------|-------|
| `mainPhpAutoFishFatigueStep(html)` | 2488-2606 | **ГЛАВНЫЙ шаг рыбалки** | **[REFACTOR] High (12 условий)** | Yes |
| `mainPhpFindFish(html)` | 2301-2358 | Поиск кнопки рыбалки | Low | No |
| `mainPhpFindDrink(html)` | 2455-2489 | Поиск питья (рыбалка) | Low | No |
| `mainPhpAutoDrinkBlazStep(address, html)` | 2606-2667 | Питьё эликсира блаженства | **[REFACTOR] High (7 условий + cooldown)** | Yes |
| `mainPhpAutoFishPrepare(html)` | 3648-3701 | Подготовка к рыбалке | Medium | Yes |
| `mainPhpWearUd(html)` | 3451-3506 | **Надевание UD в обе руки** | **[REFACTOR] High (8 условий + loop-guard)** | Yes |
| `mainPhpWearKnife(html)` | 3408-3436 | Надевание ножа | Medium | No |
| `mainPhpIsMustWearUd(html)` | 3436-3451 | Нужно ли надевать UD? | Low | No |
| `mainPhpArmedKnife(html)` | 3398-3408 | Надет ли нож? | Low | No |
| `mainPhpIsPerc(html)` | 3391-3398 | Есть ли перки? | Low | No |
| `buildAutoFishWearLoopKey()` | 3506-3527 | Ключ loop-guard | Low | No |
| `markAutoFishWearLoop(key)` | 3527-3548 | Маркирование цикла | Medium | No |
| `resetAutoFishWearLoopGuard()` | 3548-3553 | Сброс loop-guard | Low | No |
| `disableAutoFish(reason)` | 3553-3575 | Отключение рыбалки | Low | No |
| `mainPhpAutoFishWearLoopKey()` | 3505-3527 | [Дублирован как buildAutoFishWearLoopKey] | Low | No |
| `pickFishPrimId(html)` | 3588-3648 | Выбор наживки | Medium (switch + array) | No |
| `getWearInvList(html)` | 3850-3895 | Парсинг инвентаря для wear | Low | No |
| `parseWearInvEntry(htmlEntry)` | 3895-3926 | Парсинг одной записи | Low | No |
| `isAutoFishEnabledByPreference()` | 1392-1415 | Проверка профиля | Low | No |

**AUTO-FISHING ИТОГО:** 19 методов, ~1050 строк (17%)

---

## 1.5 AUTO-SKIN METHODS (Переключение скинов)

| Метод | Строки | Назначение | Сложность | VCode |
|-------|--------|-----------|-----------|-------|
| `mainPhpProcessSkills(html, address)` | 1315-1373 | **ГЛАВНЫЙ цикл скиллов** | **[REFACTOR] High (switch + condition tree)** | No |
| `mainPhpProcessFishSkills(html, address)` | 1503-1553 | Скиллы для рыбалки | Medium | Yes |
| `mainPhpGetSkinRes(html)` | 3741-3850 | **Парсинг ресурсов скина** | **[REFACTOR] Very High (regex + tracking)** | No |
| `mainPhpWearComplect(html, name)` | 1553-1621 | Надевание комплекта | Medium | Yes |
| `mainPhpWearFuryScroll(html)` | 3713-3741 | Надевание свитка ярости | Medium | No |
| `mainPhpArmedFuryScroll(html)` | 3701-3713 | Вооружён ли fury scroll? | Low | No |
| `maybeMarkAutoSkinKnifeRecheck()` | 1641-1654 | Маркирование переповторной проверки | Low | No |
| `isAutoSkinEnabledByPreference()` | 1373-1392 | Проверка профиля | Low | No |
| `isAutoFuryEnabledByPreference()` | 1621-1641 | Проверка fury | Low | No |

**AUTO-SKIN ИТОГО:** 9 методов, ~520 строк (8%)

---

## 1.6 NAVIGATION & MOVEMENT METHODS

| Метод | Строки | Назначение | Сложность | VCode |
|-------|--------|-----------|-----------|-------|
| `mainPhpWtime(html)` | 1654-1678 | Обработка Wtime (таймер) | Medium | No |
| `startAutoSearchBoxMoving(destination)` | 1678-1700 | Запуск поиска ящика | Low | No |
| `extractWtimeTimeoutSeconds(html)` | 1735-1783 | Парсинг сек из wtime | Medium (regex) | No |
| `syncNeverTimerFromWtime(html, address)` | 1783-1810 | Sync таймера | **[REFACTOR] Medium (7 условий)** | No |
| `mainPhpRaz(html)` | 1810-1842 | Обработка разделки | Low | No |
| `buildRazLinkFromFightTyPayload(payload)` | 1842-1886 | Построение линка разделки | Medium (parsing) | No |
| `extractRazLinkFromHtml(html)` | 1886-1898 | Парсинг линка разделки | Low | No |
| `mainPhpFindMapReturnForAutoMoving(html)` | 2228-2282 | **Поиск возврата на карту** | **[REFACTOR] Medium (5+ ссылок)** | Yes |
| `mainPhpFindPerc(html)` | 2136-2194 | Поиск перси | Low | No |
| `mainPhpFindFlora(html)` | 2194-2229 | Поиск флоры | Low (template matching) | No |
| `parseUnsignedIntFrom(text, index)` | 1700-1736 | Парсинг числа | Low | No |

**NAVIGATION ИТОГО:** 11 методов, ~550 строк (9%)

---

## 1.7 INVENTORY & PARSING METHODS

| Метод | Строки | Назначение | Сложность | VCode |
|-------|--------|-----------|-----------|-------|
| `mainPhpInv(html)` | 6171-6172 | Wrapper (no param) | Low | No |
| `mainPhpInv(html, cacheOnly)` | 6172-6200+ | **ГЛАВНЫЙ парсер инвентаря** | **[REFACTOR] Very High (group/sort/bulk)** | Yes |
| `mainPhpFindInv(html, filter)` | 5135-5186 | Поиск инвентаря | Medium (HTML parsing) | No |
| `mainPhpFindInvWithFallback(html, filter, address)` | 5186-5230 | С fallback | Medium (retry logic) | No |
| `mainPhpFindInvArena(html, filter)` | 5230-5249 | Инвентарь в арене | Low | No |
| `mainPhpFindInvBuilding(html, filter)` | 5249-5270 | Инвентарь в здании | Low | No |
| `mainPhpFindInvOld(html, filter)` | 5270-5315 | Старый парсинг | Low | No |
| `hasInventoryRows(html)` | 5061-5093 | Есть ли записи? | Medium | No |
| `mainPhpIsInv(html)` | 5045-5061 | Это инвентарь? | Low | No |
| `syncInventoryCacheFromHtml(html)` | 6146-6171 | Cache-only sync | Low | No |
| `isInventoryAddress(address)` | 2041-2072 | Проверка адреса inv | Low | No |
| `inventoryAddressMatchesFilter(address, filter)` | 2072-2102 | Совпадение фильтра | Low | No |
| `isLikelyInventoryReloadSnapshot(address, html)` | 5093-5112 | Вероятно reload? | Low | No |
| `isGeneratedTransitionPage(address, html)` | 5112-5135 | Переходная страница? | Low | No |
| `buildRedirectHtml(desc, link)` | 5315-5334 | HTML редирект | Low | No |

**INVENTORY ИТОГО:** 14 методов, ~520 строк (8%)

---

## 1.8 URL/QUERY PARSING UTILITIES (SHARED)

| Метод | Строки | Назначение | Использ. |
|-------|--------|-----------|---------|
| `getUrlParam(url, paramName)` | 5585-5604 | Получить параметр | 5+ мест |
| `parseUrlParamInt(url, paramName, fallback)` | 5604-5622 | Получить int параметр | 3+ места |
| `appendOrReplaceUrlParam(url, key, value)` | 5622-5654 | Модифицировать параметр | 10+ мест |
| `getQueryParamValue(url, key)` | 2102-2137 | Получить из query | 5+ мест |
| `setOrAppendQueryParam(url, key, value)` | 2012-2041 | SET или APPEND | 8+ мест |
| `normalizeNeverlandsMainLink(link)` | 1898-1924 | Нормализация ссылки | 3+ места |
| `findMainPhpLinkByQueryParts(html, parts...)` | 1924-1961 | Поиск ссылки по частям | 2+ места |
| `applyInventoryFilterToLink(link, filter)` | 1961-1996 | Применить фильтр | 3+ места |

**URL/QUERY UTILITIES ИТОГО:** 8 методов, ~400 строк - **ИСПОЛЬЗУЮТСЯ ПОВСЕМЕСТНО**

---

## 1.9 PARSER/SNAPSHOT UTILITIES

| Метод | Строки | Назначение | Используется |
|-------|--------|-----------|-------------|
| `parseInsHpSnapshot(html)` | 947-978 | Парсинг HP/MA | FightAuto, AutoCure |
| `parseInsHpSnapshotArgs(args)` | 978-1180 | Парсинг аргументов ins_HP | parseInsHpSnapshot |
| `tryBuildAutoDrinkSnapshotFromPinfo()` | 1180-1238 | Парсинг из profile | AutoDrink |
| `mainPhpUpdateTied(html)` | 2358-2382 | Парсинг усталости | process() |
| `parseMainPhpTiedValue(html)` | 2382-2418 | Парсинг значения tied | mainPhpUpdateTied |
| `parseMainPhpTiedFromHpmp(html)` | 2418-2456 | Парсинг из HPMP | parseMainPhpTiedValue |
| `mainPhpInsHp(html)` | 921-947 | Обновление HP/MA из ins_HP | process() |
| `mainPhpExtractMenuVcode(html, key)` | 2282-2302 | Парсинг VCode | MainPhpFast |
| `extractInputValue(html, name)` | 3575-3588 | Парсинг input-value | Fishing |
| `tryParseDoubleInvariant(raw)` | 1288-1316 | Парсинг Double | Snapshot parsing |
| `escapeHtmlAttr(value)` | 897-921 | Escape HTML атрибутов | SHARED (3+ мест) |
| `splitJsTopLevelCsv(raw)` | 3926-3988 | Парсинг JS CSV | FightAuto bridge |
| `trimJsToken(token)` | 3988-4013 | Trim JS токена | FightAuto bridge |
| `extractJsArrayTokens(html, prefix)` | 6034-6047 | Парсинг JS массива | Fight results |
| `parseIntFromJsToken(token, fallback)` | 6047-6058 | Парсинг числа из JS | Fight results |

**PARSER UTILITIES ИТОГО:** 15 методов, ~600 строк - **ИСПОЛЬЗУЮТСЯ ПОВСЕМЕСТНО, ОСТАВИТЬ КАК ЕСТЬ**

---

## 1.10 HELPER & STATE METHODS

| Метод | Строки | Назначение |
|-------|--------|-----------|
| `isNonCombatAutoPausedByFastAction()` | 1996-2004 | Проверка fast-паузы |
| `isNonCombatAutoPausedByCureAction()` | 2004-2012 | Проверка cure-паузы |
| `isPostFightAutoDrinkFollowupAddress(address)` | 1238-1257 | Post-fight адрес? |
| `isServerPlainMainAddress(address)` | 1257-1288 | "Чистый" main.php? |
| `isAttackFastId(fastId)` | 4904-4925 | Это атака? |
| `getInventoryFilter(fastId)` | 4925-5019 | Фильтр инвентаря |
| `normalizeFastId(fastId)` | 5019-5037 | Нормализация fastId |
| `containsIgnoreCase(value, token)` | 5037-5045 | Проверка содержания |
| `getAutoFunctionsManagerSafe()` | 1432-1456 | Safe getter AFM |
| `formatHms(seconds)` | 572-644 | Форматирование времени |

---

# 2. LOGICAL GROUPINGS BY FEATURE AREA

```
┌─────────────────────────────────────────────────────────────┐
│                      MAINPHP.JAVA (6229 строк)             │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ENTRY POINT (строки 4012-4790)                             │
│  ├─ Инициализация state + парсинг HTML                     │
│  ├─ ГЛАВНАЯ ЛОГИКА с множ. вложенными условиями (>5 уров)  │
│  └─ FastNeed dispatch → AutoCure → AutoFish → Navigate      │
│                                                               │
│  FIGHT BLOCK (строки 4800-6145, ~1350 строк, 22%)          │
│  ├─ Определение типа фрейма                                │
│  ├─ [REFACTOR] Finish link extraction (761-872)            │
│  ├─ [REFACTOR] Result publishing (5653-5778)               │
│  ├─ [REFACTOR] Captcha handling (694-731, 5404-5473)       │
│  └─ Notifications + logging                                 │
│                                                               │
│  AUTO-CURE BLOCK (строки 1415-3379, ~900 строк, 14%)       │
│  ├─ [REFACTOR] Main cure cycle (2941-3079)                 │
│  ├─ [REFACTOR] External cure request (2795-2913)           │
│  ├─ [REFACTOR] Wound cure forms (3079-3308)                │
│  ├─ [REFACTOR] Auto-drink-blaz (2606-2667)                 │
│  └─ Heavy injury signals + queue                            │
│                                                               │
│  AUTO-FISHING BLOCK (строки 2301-3700, ~1050 строк, 17%)   │
│  ├─ [REFACTOR] Main fatigue step (2488-2606)               │
│  ├─ [REFACTOR] Wear logic (3408-3550)                      │
│  ├─ Wear loop guards                                        │
│  └─ Fish prep + drink handling                              │
│                                                               │
│  AUTO-SKIN BLOCK (строки 1315-3850, ~520 строк, 8%)        │
│  ├─ [REFACTOR] Process skills (1315-1373)                  │
│  ├─ [REFACTOR] Get skin resources (3741-3850)              │
│  └─ Complect + fury + knife wear                            │
│                                                               │
│  NAVIGATION BLOCK (строки 1654-2282, ~550 строк, 9%)       │
│  ├─ [REFACTOR] Map return (2228-2282)                      │
│  ├─ [REFACTOR] Timer sync (1783-1810)                      │
│  ├─ Wtime parsing + search box                             │
│  └─ Movement + destination handling                         │
│                                                               │
│  INVENTORY BLOCK (строки 5045-6200, ~520 строк, 8%)        │
│  ├─ [REFACTOR] Main inv parser (6172-6200)                 │
│  ├─ Inventory finding (5135-5315)                          │
│  ├─ Cache sync                                              │
│  └─ Bulk actions (drop/sell)                                │
│                                                               │
│  PARSER UTILITIES (строки 900-1100+ scattered, 15%)         │
│  ├─ HP/MA snapshot parsing                                  │
│  ├─ URL/query parameter handling (SHARED)                   │
│  ├─ JS token parsing                                        │
│  └─ [KEEP AS-IS - используются повсеместно]               │
│                                                               │
│  HOST BRIDGES (строки 136-440, 10%)                         │
│  └─ [KEEP AS-IS - делегирование утилит]                    │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

# 3. REFACTORING CANDIDATES (Rule 6 - HANDLER PATTERN)

## 3.1 CANDIDATES FOR EXTRACTION - PRIORITY 1 (Critical)

### **Handler 1: FightParsingAndFinishHandler**
**Lines affected:** 694-872, 4797-4904, 5404-5778, 6091-6145  
**Total lines:** ~900  
**Complexity:** Very High (>25 условий in publish logic)  

**Methods to extract:**
```
extractFightFinishLinkFromHtml()           [761-872]
extractFightCleanFinishLinkFromHtml()      [819-872]
resolveFightCaptchaUrl()                   [694-731]
extractCaptchaUrlFromFexp()                [731-761]
showFightCaptchaDialogOnce()               [5404-5473]
publishFightResultFromLogsIfNeeded()       [5653-5778] ★★★
registerFightEnd()                         [6091-6100]
registerFightEndByLogId()                  [6100-6121]
buildAutoFightProbeFinishCandidateKey()    [4850-4869]
isAutoFightProbeFinishConfirmed()          [4885-4904]
clearAutoFightProbeFinishCandidate()       [4869-4885]
```

**Why:** 
- `publishFightResultFromLogsIfNeeded()` has >20 nested conditions
- `extractFightFinishLinkFromHtml()` has complex finish-state logic
- All methods are tightly coupled within fight lifecycle
- **VCode-free** (no SessionManager calls needed)

**Input:** HTML, address, logIdHint  
**Output:** finish link, broadcast intents  

---

### **Handler 2: AutoCureHandler**
**Lines affected:** 1415-1509, 2666-3379  
**Total lines:** ~900  
**Complexity:** Very High (>25 cascading conditions)  

**Methods to extract:**
```
mainPhpAutoCureStep()                      [2941-3079] ★★★
mainPhpExternalRequestedCureStep()         [2795-2913] ★★
queueSelfHeavyInjuryCureIfNeeded()         [2743-2795]
mainPhpBuildPoisonCureForm()               [3079-3143]
mainPhpTrySelfWoundCureByElixir()          [3143-3183] ★
mainPhpBuildWoundCureForm()                [3234-3308] ★★
[Support: all isXxx checks, woundIndex, decrements, etc.]
```

**Why:**
- `mainPhpAutoCureStep()` is main poison+wound decision tree (>20 conditions)
- Multiple wound handling branches (light/medium/heavy)
- Poison check → wound check → yelling method → form building (cascading)
- Several VCode calls for quest-item navigation
- Tightly coupled logic (no external dependencies except SessionManager)

**Input:** address, HTML, snapshot (optional)  
**Output:** redirect HTML or null  
**VCode usage:** YES (quest item navigation)

---

### **Handler 3: FishingAutomationHandler**
**Lines affected:** 2301-3700  
**Total lines:** ~1050  
**Complexity:** High (>12 conditions in fatigue step)  

**Methods to extract:**
```
mainPhpAutoFishFatigueStep()               [2488-2606] ★★★
mainPhpWearUd()                            [3451-3506] ★★
mainPhpAutoFishPrepare()                   [3648-3701] ★
mainPhpFindFish()                          [2301-2358]
mainPhpFindDrink()                         [2455-2489]
mainPhpWearKnife()                         [3408-3436]
pickFishPrimId()                           [3588-3648]
buildAutoFishWearLoopKey()                 [3506-3527]
markAutoFishWearLoop()                     [3527-3548]
resetAutoFishWearLoopGuard()               [3548-3553]
[Support: isPerc, isMustWearUd, armedKnife, isAutoFishEnabled]
```

**Why:**
- `mainPhpAutoFishFatigueStep()` has complex state machine (fatigue→drink→wait→retry)
- Wear-loop guard protects against infinite wear cycles (3+ conditions)
- Multiple decision branches (HP check → drink → wear → retry)
- VCode calls for main.php navigation
- Tightly coupled (fatigue→wear→drink→repeat)

**Input:** HTML, address  
**Output:** redirect or null  
**VCode usage:** YES (prep + drink)

---

## 3.2 CANDIDATES FOR EXTRACTION - PRIORITY 2 (High)

### **Handler 4: NavigationAndMovementHandler**
**Lines affected:** 1654-2282  
**Total lines:** ~550  
**Complexity:** Medium (6-8 conditions per method)  

**Methods to extract:**
```
mainPhpFindMapReturnForAutoMoving()        [2228-2282] ★★
syncNeverTimerFromWtime()                  [1783-1810] ★
mainPhpWtime()                             [1654-1678]
extractWtimeTimeoutSeconds()               [1735-1783]
startAutoSearchBoxMoving()                 [1678-1700]
mainPhpRaz()                               [1810-1842]
buildRazLinkFromFightTyPayload()           [1842-1886]
extractRazLinkFromHtml()                   [1886-1898]
mainPhpFindPerc()                          [2136-2194]
mainPhpFindFlora()                         [2194-2229]
parseUnsignedIntFrom()                     [1700-1736]
```

**Why:**
- Navigation concerns (map return, timer sync, search box) are mixed with process()
- VCode calls for map returns + bootstrap
- Multiple timer-related synchronizations
- Clearly separated from other concerns

**Input:** HTML, address  
**Output:** redirect or null  
**VCode usage:** YES (map navigation)

---

### **Handler 5: AutoSkinHandler**
**Lines affected:** 1315-3850  
**Total lines:** ~520  
**Complexity:** High (resource parsing + switch tree)  

**Methods to extract:**
```
mainPhpProcessSkills()                     [1315-1373] ★★
mainPhpGetSkinRes()                        [3741-3850] ★★★
mainPhpProcessFishSkills()                 [1503-1553] ★
mainPhpWearComplect()                      [1553-1621] ★
mainPhpWearFuryScroll()                    [3713-3741]
mainPhpArmedFuryScroll()                   [3701-3713]
[Support: isAutoSkin, isAutoFury, maybeMarkRecheck]
```

**Why:**
- Skill switching has complex condition tree (switch + multiple checks)
- Resource parsing is intensive (regex + state tracking)
- Can be disabled/enabled independently of other autos
- Multiple VCode calls for skill navigation

**Input:** HTML, address  
**Output:** HTML or null  
**VCode usage:** YES (complect switches)

---

### **Handler 6: InventoryParsingHandler**
**Lines affected:** 5045-6200  
**Total lines:** ~520  
**Complexity:** Very High (group, sort, bulk logic)  

**Methods to extract:**
```
mainPhpInv()                               [6172-6200+] ★★★
mainPhpFindInv()                           [5135-5186]
mainPhpFindInvWithFallback()               [5186-5230]
mainPhpFindInvArena()                      [5230-5249]
mainPhpFindInvBuilding()                   [5249-5270]
mainPhpFindInvOld()                        [5270-5315]
getWearInvList()                           [3850-3895]
parseWearInvEntry()                        [3895-3926]
buildRedirectHtml()                        [5315-5334]
hasInventoryRows()                         [5061-5093]
mainPhpIsInv()                             [5045-5061]
syncInventoryCacheFromHtml()               [6146-6171]
[Support: isInventoryAddress, addressMatchesFilter, isLikely*, isGenerated*]
```

**Why:**
- Grouping + sorting + bulk actions logic is complex
- Multiple inventory finding strategies (arena/building/old)
- Cache-only mode requires special handling
- Separate from main process() flow
- Multiple VCode calls for inventory actions

**Input:** HTML, filter, address, cache-only flag  
**Output:** modified HTML or null  
**VCode usage:** YES (form submissions)

---

## 3.3 CANDIDATES FOR EXTRACTION - PRIORITY 3 (Medium)

### **Handler 7: SystemMessageAndPopupHandler**
**Lines affected:** 2666-2744  
**Total lines:** ~80  
**Complexity:** Low-Medium (5-6 conditions)  

**Methods to extract:**
```
syncInjuriesFromMapHeavyPopup()            [2666-2677]
handleHeavyInjurySignal()                  [2692-2720]
isHeavyInjurySignalText()                  [2720-2744]
onServerPopupMessage()                     [2676-2692]
```

**Why:**
- Popup processing is orthogonal to main logic
- Can be enabled/disabled independently
- Simple state updates (no redirects)
- Broadcasts intents (decoupled)

**Input:** HTML, popup text  
**Output:** void (side effects only)  

---

# 4. COMPLEX MULTI-STEP PROCESSES (>3 nested conditions)

## 4.1 PROCESS() MAIN METHOD - **COMPLEXITY SCORE: 9/10**

**Lines 4012-4790 - Sequential cascade with 6 major decision points:**

```java
1. FastNeed dispatch
   ├── if (AppVars.FastNeed) 
   │   └── ProcessMainPhpFast()
   └── return if found
   
2. Fight frame detection
   ├── if (isFightFrame || isFightTopFrame)
   │   └── FightAuto.processFight()
   └── return if found
   
3. Post-fight recovery
   ├── if (isFightFinishAddress && !isFightFrame)
   │   ├── syncPostFightStats()
   │   └── buildSyncRedirect()
   └── return if needed
   
4. Skills processing
   ├── if (go=inf)
   │   ├── mainPhpProcessSkills()
   │   └── mainPhpProcessFishSkills()
   
5. Navigation/Movement
   ├── if (AutoMoving && !FastNeed)
   │   ├── findMapReturn()
   │   └── redirect to map
   └── return if found
   
6. Timer sync
   └── if (wtime detected)
       └── syncNeverTimer()
```

**Refactoring recommendation:** Create `ProcessMainPhpDispatcher` that delegates based on address/state **OR** keep as coordinator but split inner blocks into Handlers.

---

## 4.2 mainPhpAutoCureStep() - **COMPLEXITY SCORE: 8/10**
**Lines 2941-3079**

```
Chain:
1. Check if cure disabled → SKIP
2. Check snapshot (external poison? wounds?)
   ├── Get snapshot from pinfo OR arg
   └── Parse counts (poison, light, medium, heavy)
3. Chain: poison → light → medium → heavy
   For EACH wound type:
   ├── Check if cure enabled (user pref)
   ├── Check if item available
   ├── Build cure form (quest-item, elixir, or fast-action)
   └── Return redirect or continue to next type
4. If external request pending
   ├── Check if action completed
   └── Clear or retry
5. Return null if nothing to cure OR redirect HTML
```

**Decision points:** >20 (if checks, for loops, switch)  
**VCode calls:** 3-4 (quest navigation, form submission)  

---

## 4.3 mainPhpAutoFishFatigueStep() - **COMPLEXITY SCORE: 7/10**
**Lines 2488-2606**

```
State machine:
1. Check if fishing disabled → SKIP
2. Check tied (fatigue) stat
   ├── if (tied > threshold) → SKIP (too tired)
   └── if (tied < threshold) → CONTINUE
3. Find drink or fatigue recovery item
   ├── if (found drink)
   │   ├── Build redirect to drink
   │   └── Set cooldown timer
   │   └── Return redirect
   └── else if (not on correct inventory tab)
       ├── Build redirect to correct tab
       └── Return redirect
4. Check for loop prevention
   ├── if (wear loop detected)
   │   └── Disable fishing, notify
   │   └── Return null
   └── else mark loop + continue
5. Check if needs wear
   ├── mainPhpWearUd()
   ├── mainPhpWearKnife()
   └── Return wear redirect if needed
6. Otherwise find main fish action
   └── Return fish-click redirect
```

**Decision points:** 12+ (nested if/for/while)  
**Cooldown management:** 2 timers tracked  

---

## 4.4 publishFightResultFromLogsIfNeeded() - **COMPLEXITY SCORE: 8/10**
**Lines 5653-5778**

```
1. Check if logs file existed
2. Parse logs for fight results (>15 regex/parsing ops)
   ├── Extract boi log ID
   ├── Extract fight type
   ├── Extract winner/loser
   ├── Extract damage/loot
   └── Extract XP
3. For EACH result type:
   ├── if (not already broadcast)
   │   ├── Build broadcast intent
   │   ├── Send intent
   │   └── Mark as published
   └── else (skip duplicate)
4. Clean up log file
```

**Decision points:** 20+ (if checks, duplicate prevention)  
**Broadcast deduplication:** 3 keys tracked  

---

## 4.5 mainPhpWearUd() - **COMPLEXITY SCORE: 6/10**
**Lines 3451-3506**

```
1. Check if wear enabled
   └── if (not enabled) → return null
2. Loop guard check
   ├── if (loop detected) → reset & return null
   └── else mark current status
3. Parse inventory (getWearInvList)
4. Check current wear state (ParsedDressed)
5. For EACH inv item:
   ├── Check if suitable (name match)
   ├── Check if not already worn
   ├── Build wear form
   └── Return wear redirect
6. If not found:
   └── Build inventory redirect
```

**Decision points:** 8+ (wear state checks)  
**Loop prevention:** complex (key + timestamp guard)  

---

# 5. PERCENTAGE BREAKDOWN OF FILE

| Area | Lines | % | Refactor Priority |
|------|-------|---|------------------|
| **Fight Handling** | ~1350 | 22% | ★★★ P1 |
| **Auto-Cure** | ~900 | 14% | ★★★ P1 |
| **Auto-Fishing** | ~1050 | 17% | ★★★ P1 |
| **Navigation/Movement** | ~550 | 9% | ★★ P2 |
| **Auto-Skin** | ~520 | 8% | ★★ P2 |
| **Inventory** | ~520 | 8% | ★★ P2 |
| **Parser/Utilities** | ~600 | 10% | (Keep) |
| **Bridges/Constants** | ~140 | 2% | (Keep) |

---

# 6. METHODS CALLED FROM MULTIPLE PLACES (DEPENDENCY TRACKING)

## CRITICAL SHARED APIS (4+ callers)

| Метод | Калlers | Используется | Примечание |
|-------|--------|-------------|-----------|
| `getUrlParam()` | 5+ | Fast, Cure, Fish, Nav, Inv | **CRITICAL - многоуровневая** |
| `appendOrReplaceUrlParam()` | 8+ | EVERYWHERE | **CRITICAL - построение редиректов** |
| `normalizeNeverlandsMainLink()` | 3 | Navigation, Treasure | Нормализация ссылок |
| `parseUrlParamInt()` | 3 | Fast, Navigation | Парсинг числовых параметров |
| `mainPhpIsInv()` | 4 | Fast, Cure, Treasure | Проверка инвентаря |
| `isInventoryAddress()` | 6 | Fast, Cure, Treasure, Nav | **CRITICAL - адрес инвентаря** |
| `mainPhpFindInvWithFallback()` | 4+ | Fast, Cure, Treasure | **CRITICAL - получение инвентаря** |
| `getWearInvList()` | 3 | Fish, Skin manage, Treasure | Надеватель помощник |
| `mainPhpExtractMenuVcode()` | 2 | FastAction | VCode управление |
| `escapeHtmlAttr()` | 3+ | Utilities для HTML | HTML building |
| `getAutoFunctionsManagerSafe()` | 2 | Skin functions | Context access |

## IMPORTANT: These methods MUST remain stable and well-tested during refactoring

---

# 7. REFACTORING IMPACT ANALYSIS

## Files that will IMPORT newly extracted Handlers

1. **MainPhp.java (modified)**
   - Remove extracted methods
   - Add Handler imports and calls
   - Reduce from 6229 → ~3500-4000 lines (43-36% reduction)

2. **FightAuto.java (existing)**
   - Already uses FIGHT_AUTO_HOST bridge ✓

3. **FastActionManager.java (existing)**
   - Already uses FAST_ACTION_HOST bridge ✓

4. **TreasureDig.java (existing)**
   - Already uses TREASURE_DIG_HOST bridge ✓

## New files to create (Phase 4)

```
Handler classes   
├── FightParsingAndFinishHandler.java       (~350 lines)
├── AutoCureHandler.java                    (~450 lines)
├── FishingAutomationHandler.java           (~500 lines)
├── NavigationAndMovementHandler.java       (~300 lines)
├── AutoSkinHandler.java                    (~300 lines)
├── InventoryParsingHandler.java            (~350 lines)
└── SystemMessageAndPopupHandler.java       (~100 lines)
                                    TOTAL: ~2250 lines NEW CODE
```

## Migration strategy

1. **Phase 4A:** Extract P1 Handlers (FightParsing, AutoCure, Fishing)
2. **Phase 4B:** Extract P2 Handlers (Navigation, Skin, Inventory)
3. **Phase 4C:** Extract P3 Handlers (SystemMessage)
4. **Phase 4D:** Refactor MainPhp.process() for coordination
5. **Phase 4E:** Testing + integration + VCode verification

---

# 8. VCODE USAGE MAP (Rule 5 Compliance)

| Handler | Methods with VCode | SessionManager calls | Notes |
|---------|------------------|------------------|-------|
| FightParsing | None | ✓ 0 | No VCode needed for parsing |
| AutoCure | YES (4 methods) | ✓ quest navigation | mainPhpBuildPoisonCureForm, wound cure |
| Fishing | YES (3 methods) | ✓ prep + drink | mainPhpAutoFishPrepare, find drink |
| Navigation | YES (2 methods) | ✓ map returns | mainPhpFindMapReturnForAutoMoving |
| AutoSkin | YES (2 methods) | ✓ complect switch | mainPhpWearComplect, fish skills |
| Inventory | YES (1 method) | ✓ inv actions | mainPhpInv bulk submit |
| SystemMessage | None | ✓ 0 | No VCode needed |

**Critical:** All VCode calls must use `SessionManager.getInstance().getValidVCodeForAction()`  
**Check point:** Grep all new Handlers for `AppVars.VCode` (should be ZERO)

---

# 9. QUICK REFACTORING CHECKLIST

## Pre-Refactoring
- [ ] Run full test suite on current codebase
- [ ] Build APK with no errors
- [ ] Document all VCode usage (THIS ANALYSIS)
- [ ] Create Handler skeleton test files

## Phase 4A (P1 - Critical)
- [ ] FightParsingAndFinishHandler - Create + migrate methods
  - [ ] Verify `publishFightResultFromLogsIfNeeded()` porting
  - [ ] Test finish link extraction (all variants)
  - [ ] Test captcha dialog dedup logic
  - [ ] Test broadcast dedup keys
- [ ] AutoCureHandler - Create + migrate
  - [ ] Test mainPhpAutoCureStep() decision tree (>20 branches)
  - [ ] Test poison→wound cascade
  - [ ] Test SessionManager.getValidVCodeForAction() calls
- [ ] FishingAutomationHandler - Create + migrate
  - [ ] Test fatigue state machine
  - [ ] Test wear loop guard
  - [ ] Test cooldown management

## Phase 4B (P2 - High)
- [ ] NavigationAndMovementHandler
- [ ] AutoSkinHandler
- [ ] InventoryParsingHandler

## Phase 4C (P3 - Medium)
- [ ] SystemMessageAndPopupHandler

## Post-Refactoring
- [ ] Run full test suite again
- [ ] Build APK with new Handlers
- [ ] Test fight logic E2E
- [ ] Test fishing cycle E2E
- [ ] Test cure logic E2E
- [ ] Verify no regressions in navigation/skin/inventory
- [ ] Verify all VCode usage through SessionManager (grep all files)
- [ ] Check FileLogger entries (all critical paths logged)
- [ ] Performance profiling (should be same or faster)

---

# 10. REFERENCE: ORIGINAL C# STRUCTURE

For cross-reference, these C# files were originally split:

- **MainPhp.cs** (main entry + dispatch)
- **MainPhpFight.cs** (fight handling)
- **MainPhpInv.cs** (inventory)
- **MainPhpRaz.cs** (разделка)
- **MainPhpDrink.cs** (drinks)
- **MainPhpWear.cs** (сkins + wear)
- **MainPhpFish.cs** (fishing)
- **MainPhpInsHp.cs** (HP/MA parsing)

**Android consolidation:** All above were merged into single MainPhp.java. Phase 4 reverses this by extracting Handlers while keeping shared utilities.

---

