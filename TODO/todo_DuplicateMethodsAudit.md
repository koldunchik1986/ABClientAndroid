# Аудит дубликатов и нарушений правил AGENTS.MD

**Дата:** 2026-04-13  
**Область:** `app/src/main/java/ru/neverlands/abclient/`  
**Правила-источники:** AGENTS.MD правила 6, 7, 8, 9

---

## 1. ДУБЛИКАТ InventoryParser — РАЗНЫЕ классы, одинаковое имя

| Файл | Назначение | Строк |
|---|---|---|
| `utils/InventoryParser.java` | Парсит **предметы инвентаря** (название, wearUrl, durability) | 248 |
| `postfilter/InventoryParser.java` | Парсит **комплекты (compl_view)** из HTML инвентаря и сохраняет в Profile | 80 |

**Не дубликаты по функциональности**, но одинаковое имя — нарушает правило 8 (anti fix-на-fix).

- [ ] Переименовать `postfilter/InventoryParser` → `ComplectParser.java` (отражает реальную задачу)
- [ ] Обновить все импорты (MainPhp ссылается на postfilter.InventoryParser)

---

## 2. ДУБЛИКАТ `hasFightMarkers()` — 3 копии!

| Файл | Строка | Сигнатура |
|---|---|---|
| `MainActivity.java` | 1406 | `private boolean hasFightMarkers(String html)` |
| `AutoModeForegroundService.java` | 581 | `private boolean hasFightMarkers(String html)` |
| `FightViewModel.java` | 288 | `private boolean containsFightMarkers(String html)` |

Все три реализуют **одну и ту же логику**: `html.contains("var fight_ty") || html.contains("magic_slots();")`

**Нарушение правила 8** — три параллельных копии одной проверки.

- [ ] Создать `utils/FightMarkerHelper.java` с единственным статическим методом `hasFightMarkers(String html)`
- [ ] Заменить вызов в `MainActivity.java:1406`
- [ ] Заменить вызов в `AutoModeForegroundService.java:581`
- [ ] Заменить вызов в `FightViewModel.java:288`
- [ ] Удалить приватные методы-дубликаты

---

## 3. НАРУШЕНИЕ правила 9 — markFightInProgress ПЕРЕД LezFight

**КРИТИЧНЫЙ ПОРЯДОК (AGENTS.MD правило 9):**
```
1️⃣ SessionManager.markFightInProgress()  ← ПЕРВОЙ!
2️⃣ LezFight fight = new LezFight(html)   ← ВТОРОЙ
3️⃣ fight.buildFrame()                     ← ТРЕТИЙ
```

### Текущее состояние:

| Файл | Где `new LezFight` | Где `markFightInProgress` | Вердикт |
|---|---|---|---|
| `LezFight.java:181` | Внутри `buildFrame()` | ✅ Встроен перед парсингом VCode | OK |
| `FightAuto.java:213` | Строка 213 | Строка 421 (ПОСЛЕ) | ❌ НАРУШЕНИЕ |
| `FightViewModel.java:113` | Строка 113 | **Нет вызова** | ❌ НАРУШЕНИЕ |
| `FightViewModel.java:124` | Строка 124 | **Нет вызова** | ❌ НАРУШЕНИЕ |
| `FightViewModel.java:191` | Строка 191 | **Нет вызова** | ❌ НАРУШЕНИЕ |
| `FightViewModel.java:255` | Строка 255 | **Нет вызова** | ❌ НАРУШЕНИЕ |
| `MainActivity.java:1451` | `isActiveFightContext()` | **Нет вызова** | ⚠️ Read-only проверка — допустимо |

- [ ] **FightAuto.java:213** — добавить `SessionManager.getInstance().markFightInProgress()` ПЕРЕД `new LezFight(html)`
- [ ] **FightViewModel.java** — добавить `markFightInProgress()` перед всеми 4 точками `new LezFight`
- [ ] Убедиться что `clearFightContext()` вызывается при завершении боя (FightAuto.java:294 уже вызывает — OK)

---

## 4. НАРУШЕНИЕ правила 6 — Прямой доступ к AppVars.VCode

| Файл | Строка | Контекст |
|---|---|---|
| `MainActivity.java` | 1380 | Комментарий — упоминает AppVars.VCode |
| `MainActivity.java` | 1697 | Извлечение vcode из payload + синхронизация `AppVars.VCode` |
| `MainActivity.java` | 1701 | Recovery-ветка использует `AppVars.VCode` |
| `MainActivity.java` | 1721 | Валидация чтобы не перетирать `AppVars.VCode` |
| `MainPhp.java` | 3479 | Только комментарий ("не кешируется") |

**MainActivity:1697-1721** — активно читает/пишет vcode в AppVars.VCode вместо SessionManager.

- [ ] Заменить `AppVars.VCode` на `SessionManager.getInstance().getValidVCodeForAction("auto_submit_payload")`
- [ ] Заменить запись в AppVars.VCode на `SessionManager.getInstance().parseVCodeFromHtml(...)`
- [ ] Добавить fallback при null-результате от SessionManager
- [ ] Убедиться что grep `AppVars\.VCode` не показывает новых вхождений вне SessionManager

---

## 5. НАРУШЕНИЕ правила 7 — Scattered FastNeed checks (55 обращений)

`AppVars.FastNeed` читается напрямую в **9 файлах**, минуя FastActionManager:

| Файл | Кол-во обращений | Природа |
|---|---|---|
| `MainPhp.java` | 8 | Проверки `!AppVars.FastNeed` перед авто-действиями |
| `FishAjaxPhp.java` | 7 | Проверки FastNeed перед рыбалкой |
| `MapAjax.java` | 4 | Проверки FastNeed перед навигацией |
| `RoomManager.java` | 8 | Проверки FastNeed перед переходами |
| `AppTimerManager.java` | 1 | Проверка FastNeed |
| `FightAuto.java` | 1 | Проверка FastNeed |
| `AutoModeForegroundService.java` | 2 | Диагностика FastNeed |
| `FastActionManager.java` | ~15 | Установка/сброс (OK — владелец) |
| `MainActivity.java` | 1 | Проверка FastNeed |

- [ ] Добавить в `FastActionManager`:
  ```java
  public static boolean isBlocking() {
      return AppVars.FastNeed;
  }
  public static boolean shouldPauseAutoFunctions() {
      return AppVars.FastNeed && AppVars.FastPauseNonCombatAutoFunctions;
  }
  ```
- [ ] Заменить все `AppVars.FastNeed` чтения вне FastActionManager на вызовы новых методов
- [ ] Заменить `AppVars.FastNeed && AppVars.FastPauseNonCombatAutoFunctions` на `shouldPauseAutoFunctions()`
- [ ] Grep после миграции: `AppVars\.FastNeed` — должно остаться только внутри FastActionManager

---

## 6. КРУПНЫЕ ФАЙЛЫ — кандидаты на расщепление

| Файл | Строк | Методов | Приоритет |
|---|---|---|---|
| **MainPhp.java** | 5950 | 52 | 🔴 Высший — авто-питьё, навигация, cure, поиск — всё в одном |
| **MainActivity.java** | 5189 | 161 | 🔴 Высший — бой-логика, probe, навигация, UI |
| **FishAjaxPhp.java** | 3533 | — | 🟡 Большой — рыбалка + авто-питьё + fatigue |
| **FastActionManager.java** | 2307 | 2 | 🟡 Большой — координация + реализация processMainPhp |
| **NeverApi.java** | 2376 | 5 | 🟡 Большой — API-запросы + парсинг + трейсинг |
| **RoomManager.java** | 2898 | — | 🟡 Большой — рендеринг + инъекции + навигация |
| **BossAuto.java** | 1938 | 57 | 🟡 Большой — события + навигация + чат |
| **AutoFunctionsManager.java** | 2086 | 161 | 🟡 Большой — все авто-функции + SharedPreferences + UI |

---

## 7. HANDLER-КАНДИДАТЫ (методы с >3 каскадных условий — правило 7)

### 7.1. `processFight()` — FightAuto.java:200

**Каскады (10+):** captcha → guard → timeout → restoring → new-fight → finish → autoboi → safety → auto-refresh → exit  
**Рекомендация:** Создать `FightProcessHandler` с под-handler'ами:
- `FightFinishFlowHandler` — логика завершения боя (finish link, captcha, FEND)
- `FightSafetyCheckHandler` — проверки безопасности (lowHp, lowMa, dangerous)
- `FightAutoTurnHandler` — отправка авто-удара (vcode, frame, submit)

- [ ] Проанализировать FightAuto.processFight() детально
- [ ] Спроектировать FightProcessHandler
- [ ] Вынести finish-flow в FightFinishFlowHandler
- [ ] Вынести safety-check в FightSafetyCheckHandler

### 7.2. `autoDrinkProcessing` — MainPhp.java (~1000-1200)

**Каскады (5+):** hp/mp → thresholds → cooldown → elixir → vcode → restore  
**Рекомендация:** Создать `AutoDrinkHandler`

- [ ] Создать `utils/AutoDrinkHandler.java`
- [ ] Перенести логику авто-питья из MainPhp
- [ ] Использовать callback pattern (onDrinkTriggered, onDrinkSkipped)
- [ ] Логирование с `[AUTO_DRINK_HANDLER_...]`

### 7.3. `autoCureProcessing` — MainPhp.java (~2300)

**Каскады (5+):** injury → source → friends → neutrals → elixir → vcode  
**Рекомендация:** Создать `AutoCureHandler`

- [ ] Создать `utils/AutoCureHandler.java`
- [ ] Перенести логику авто-лечения из MainPhp
- [ ] Использовать callback pattern

### 7.4. `processMainPhpFast()` — FastActionManager.java:793

**Каскады (6+):** FastId → timer → fight → filter → redirect → inv → retry  
**Рекомендация:** Создать `FastActionProcessHandler`

- [ ] Создать `utils/FastActionProcessHandler.java`
- [ ] Перенести processMainPhpFast() и processMainPhp() из FastActionManager
- [ ] FastActionManager оставить только координацию (fastStart/fastCancel/isBlocking)

### 7.5. `requestAutoTurnInternal()` — MainActivity.java

**Каскады (5+):** fightMarkers → captcha → guard → vcode → probe → result  
**Рекомендация:** Создать `AutoTurnRequestHandler`

- [ ] Создать `utils/AutoTurnRequestHandler.java`
- [ ] Перенести probe-логику и autoTurn-запросы из MainActivity
- [ ] MainActivity оставить только вызов handler + обработку результата

### 7.6. `maybeForceFightFrameSync()` — AutoModeForegroundService.java:616

**Каскады (4+):** announceTime → throttle → existingHTML → finishLink → captcha  
**Рекомендация:** Создать `FightSyncHandler`

- [ ] Создать `utils/FightSyncHandler.java`
- [ ] Перенести логику синхронизации боевого фрейма

---

## 8. MANAGER/ ДИРЕКТОРИЯ — анализ на правило 7

| Файл | Строк | Нарушение rule 7? | Детали |
|---|---|---|---|
| `FastActionManager.java` | 2307 | ❌ Да | Содержит И координацию, И реализацию processMainPhp/processMainPhpFast |
| `AutoFunctionsManager.java` | 2086 | ❌ Частично | Координация + SharedPreferences + реализация рыбалки/навигации |
| `BossAuto.java` | 1938 | ⚠️ Частично | Смешаны события босса + навигация + чат-сообщения |
| `RoomManager.java` | 2898 | ⚠️ Частично | Смешаны навигация + рендеринг + HTML-инъекции |
| `NeverApi.java` | 2376 | ⚠️ Частично | HTTP-запросы + парсинг ответов в одном файле |
| `AppTimerManager.java` | 614 | ✅ OK | Только координация таймеров |
| `ContactsManager.java` | — | ✅ OK | Только работа с контактами |
| `QuickButtonsManager.java` | — | ✅ OK | Только управление кнопками |
| `CharacterVitalsManager.java` | — | ✅ OK | Только состояние персонажа |
| `ClanWarsManager.java` | — | ✅ OK | Только клановые войны |
| `UnderAttackManager.java` | — | ✅ OK | Только обработка нападений |
| `CompasAuto.java` | — | ✅ OK | Только автокомпас |
| `TabManager.java` | — | ✅ OK | Только управление вкладками |
| `ChatUserList.java` | — | ✅ OK | Только список пользователей чата |
| `TorgList.java` | — | ✅ OK | Только торговый список |
| `TorgPair.java` | — | ✅ OK | Только торговая пара |

---

## 9. ПРИОРИТЕТНЫЙ ПЛАН ИСПРАВЛЕНИЙ

### 🔴 Критичные (правила 6, 9 —直接影响 VCode/бой):

| # | Задача | Файлы | Статус |
|---|---|---|---|
| 1 | Устранить 3 копии `hasFightMarkers()` → `FightMarkerHelper` | MainActivity, AutoModeForegroundService, FightViewModel | [ ] |
| 2 | Исправить порядок markFightInProgress ПЕРЕД LezFight | FightAuto.java:213, FightViewModel (4 точки) | [ ] |
| 3 | Мигрировать AppVars.VCode → SessionManager | MainActivity:1697-1721 | [ ] |

### 🟡 Важные (правило 7, 8 — архитектура):

| # | Задача | Файлы | Статус |
|---|---|---|---|
| 4 | Инкапсулировать FastNeed в FastActionManager | 9 файлов, 55 обращений | [ ] |
| 5 | Переименовать postfilter/InventoryParser → ComplectParser | postfilter/InventoryParser, MainPhp | [ ] |
| 6 | Вынести processMainPhp из FastActionManager → FastActionProcessHandler | FastActionManager (2307 строк) | [ ] |
| 7 | Вынести auto-drink из MainPhp → AutoDrinkHandler | MainPhp (5950 строк) | [ ] |
| 8 | Вынести auto-cure из MainPhp → AutoCureHandler | MainPhp (5950 строк) | [ ] |
| 9 | Вынести requestAutoTurnInternal из MainActivity → AutoTurnRequestHandler | MainActivity (5189 строк) | [ ] |

### 🟢 Желательные (долгосрочный рефакторинг):

| # | Задача | Файлы | Статус |
|---|---|---|---|
| 10 | Расщепить FightAuto.processFight → FightProcessHandler + под-handler'ы | FightAuto (957 строк) | [ ] |
| 11 | Вынести maybeForceFightFrameSync → FightSyncHandler | AutoModeForegroundService (991 строк) | [ ] |
| 12 | Разделить BossAuto на BossEventHandler + BossNavigationHandler | BossAuto (1938 строк) | [ ] |
| 13 | Разделить RoomManager на RoomRenderer + RoomNavigationHandler | RoomManager (2898 строк) | [ ] |
