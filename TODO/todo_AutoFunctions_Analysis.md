# Анализ авто-функций ПК версии ABClient и статус реализации в Android

## 1. Список авто-функций в ПК версии (C#)

### Основные авто-функции

| № | Название (ПК) | Название в коде | Описание | Переменная в AppVars/Profile |
|---|---------------|-----------------|----------|------------------------------|
| 1 | Автобой (полноценный) | LezFight / Autoboi | Автоматический бой с настраиваемыми комбинациями ударов/блоков | `LezFight` (класс), `Autoboi` (enum) |
| 2 | Авто-Рыбалка | AutoFish | Автоматическая рыбалка | `AutoFishCheckUd`, `AutoFishWearUd`, `AutoFishDrink` |
| 3 | Авто-Приманка | FormAutoBait | Использует приманки для нападения ботов | `FormAutoBait` |
| 4 | Авто-Охота | AutoSkin | Автоматическое разделывание добычи и одевание инструментов | `AppVars.Profile.SkinAuto`, `AppVars.AutoSkinHand` |
| 5 | Авто-Нападение | AutoAttack | Автоматическое нападение | `AutoAttackToolId` |
| 6 | Авто-Невид | AutoNevid | Автоматическое поддержание невидимости | `AutoOpenNevid`, `DoSelfNevid` |
| 7 | Авто-Обнаружение | AutoDetect | Автоматическое обнаружение невидимых | (нужно найти) |
| 8 | Авто-Тотем | AutoTotem | Автоматическое использование тотема | (нужно найти) |
| 9 | Авто-Лечение | AutoCure | Автоматическое лечение | `DoAutoCure` (Profile) |
| 10 | Авто-Питье | AutoDrink | Автоматическое использование зелий/эликсиров | `AutoDrink` |
| 11 | Авто-Движение | AutoMoving | Автоматическое перемещение по локациям | `AutoMoving`, `AutoMovingDestinaton` |
| 12 | Авто-Травник | AutoCut / DoHerbAutoCut | Автоматическая резка травы (травник) | `DoHerbAutoCut` |
| 13 | Авто-Обновление | AutoRefresh | Автоматическое обновление страницы | `AutoRefresh` |
| 14 | Слежение за локацией | LocationTracking | Отслеживание текущей локации | (добавлено в Android) |

### Файлы ПК версии связанные с авто-функциями

| Файл | Описание |
|------|----------|
| `LezFight.cs` | Логика Автобоя (полноценный автобой с комбинациями) |
| `FormMainAutoBoi.cs` | Управление авто-боем |
| `FormAutoAttack.cs` | Форма Авто-Нападения |
| `FormAutoBait.cs` | Форма Авто-Приманки (использует приманки для нападения ботов) |
| `FormSettingsAutoCut.cs` | Настройки Авто-Травника (DoHerbAutoCut) |
| `FormSettingsGeneral.designer.cs` | Настройки Авто-Лечения (DoAutoCure), Авто-Рыбалки |
| `TInvUd.cs` | Инвентарь и Авто-Функции (AutoSkin - одевание инструментов) |
| `FormMainInit.cs` | Инициализация форм (buttonAutoSkin.Checked) |
| `AppVars.cs` | Глобальные переменные |
| `UserConfigVars.cs` | Профиль пользователя (DoAutoCure) |

---

## 2. Реальный статус реализации в Android

### Важное уточнение по терминологии
- **LezFight.cs** (ПК) / **LezFight.java** (Android) = **Автобой** - полноценный автобой с комбинациями ударов/блоков
- **AutoSkin** (ПК) / **LEZ_FIGHT** (Android) = **Авто-Охота** - разделывание добычи, одевание профессиональных инструментов

### Переключатели авто-функций (AutoFunctionsManager.java) ✅

| Функция | QuickActionType | Методы | Статус |
|---------|-----------------|--------|--------|
| Авто-Бой | AUTO_FIGHT | `isAutoFightEnabled`, `toggleAutoFight`, `setAutoFightEnabled` | ✅ Реализован (устанавливает AppVars.Autoboi) |
| Авто-Рыбалка | AUTO_FISH | `isAutoFishEnabled`, `toggleAutoFish`, `setAutoFishEnabled` | ✅ Реализован (эксклюзивная) |
| Авто-Приманка | AUTO_BAIT | `isAutoBaitEnabled`, `toggleAutoBait`, `setAutoBaitEnabled` | ✅ Реализован (эксклюзивная) |
| Авто-Охота | AUTO_SKIN | `isAutoSkinEnabled`, `toggleAutoSkin`, `setAutoSkinEnabled` | ✅ Реализован (эксклюзивная, переименовано с LEZ_FIGHT) |
| Авто-Нападение | AUTO_ATTACK | `isAutoAttackEnabled`, `toggleAutoAttack`, `setAutoAttackEnabled` | ✅ Реализован |
| Авто-Невид | AUTO_INVISIBLE | `isAutoInvisibleEnabled`, `toggleAutoInvisible`, `setAutoInvisibleEnabled` | ✅ Реализован |
| Слежение за локацией | LOCATION_TRACKING | `isLocationTrackingEnabled`, `toggleLocationTracking`, `setLocationTrackingEnabled` | ✅ Реализован |
| Авто-Обнаружение | AUTO_DETECT | `isAutoDetectEnabled`, `toggleAutoDetect`, `setAutoDetectEnabled` | ✅ Реализован |
| Авто-Тотем | AUTO_SUMMON | `isAutoSummonEnabled`, `toggleAutoSummon`, `setAutoSummonEnabled` | ✅ Реализован |
| Авто-Лечение | AUTO_CURE | `isAutoCureEnabled`, `toggleAutoCure`, `setAutoCureEnabled` | ✅ Реализован |
| Авто-Питье | AUTO_DRINK | `isAutoDrinkEnabled`, `toggleAutoDrink`, `setAutoDrinkEnabled` | ✅ Реализован |
| Авто-Движение | AUTO_MOVING | `isAutoMovingEnabled`, `toggleAutoMoving`, `setAutoMovingEnabled` | ✅ Реализован |
| Авто-Травник | AUTO_CUT | `isAutoCutEnabled`, `toggleAutoCut`, `setAutoCutEnabled` | ✅ Реализован (эксклюзивная) |
| Авто-Обновление | AUTO_REFRESH | `isAutoRefreshEnabled`, `toggleAutoRefresh`, `setAutoRefreshEnabled` | ✅ Реализован |

**AutoFunctionsManager.java** содержит переключатели с логикой эксклюзивности и `isFunctionEnabled(QuickActionType)` / `toggleFunction(QuickActionType)`. **Реальная логика выполнения** авто-функций (фактические HTTP-запросы, парсинг HTML) — НЕ РЕАЛИЗОВАНА (см. раздел 3 Плана портирования).

### РЕАЛЬНО РЕАЛИЗОВАННЫЕ функции (отдельные классы)

| Функция | Файл | Описание | Статус |
|---------|------|----------|--------|
| **Автобой (полноценный)** | `LezFight.java` | Парсинг HTML боя, генерация комбинаций ударов/блоков/магии, выбор лучшей комбинации | ✅ Полностью портирован |
| **Быстрые действия** | `FastActionManager.java` | Все виды нападалок, свитков, зелий, эликсиров, тотема, телепортов | ✅ Полностью портирован |
| **FastAttackAsync** | `FastActionManager.java` + `NeverApi.java` | Фоновый поток ожидания конца боя цели перед атакой | ✅ Реализовано |
| **Автонападение** | Использует `FastActionManager` | Автоматическое нападение на цель | ✅ Реализовано |

### Логика работы эксклюзивных функций (реализована в AutoFunctionsManager)

**Эксклюзивные функции** - только одна может быть активна:
- Авто-Рыбалка (AUTO_FISH)
- Авто-Охота (AUTO_SKIN)
- Авто-Травник (AUTO_CUT)
- Авто-Приманка (AUTO_BAIT)

**Правила (реализованы):**
1. При включении **любой** эксклюзивной функции → Авто-Бой включается автоматически (если был выключен)
2. При включении эксклюзивной функции → остальные эксклюзивные функции выключаются

---

## 3. План портирования

### Переключатели авто-функций

- [x] Все 14 переключателей добавлены в AutoFunctionsManager с логикой эксклюзивности
- [x] isFunctionEnabled(QuickActionType) и toggleFunction(QuickActionType) — универсальные методы

### Реализация логики авто-функций (в процессе)

| Функция | Статус | Файлы для изучения | Приоритет |
|---------|--------|-------------------|-----------|
| Автобой (LezFight) | ✅ Реализован | `LezFight.java` | - |
| Быстрые действия | ✅ Реализованы | `FastActionManager.java` + `NeverApi.java` | - |
| Авто-Рыбалка | ❌ Логика не реализована | `ABClient/PostFilter/AutoFish.cs` | Средний |
| Авто-Охота | ❌ Логика не реализована | `ABClient/Lez/LezFight.cs` (разделывание) | Средний |
| Авто-Приманка | ❌ Логика не реализована | `ABClient/ABForms/FormAutoBait.cs` | Средний |
| Авто-Травник | ❌ Логика не реализована | `ABClient/ABForms/FormMainHerbs.cs` | Средний |
| Авто-Невид | ❌ Логика не реализована | Нужно найти C# код | Средний |
| Авто-Обнаружение | ❌ Логика не реализована | Нужно найти C# код | Низкий |
| Авто-Тотем | ❌ Логика не реализована | Нужно найти C# код | Низкий |
| Авто-Лечение | ❌ Логика не реализована | `ABClient/MyForms/FormSettingsGeneral.cs` | Средний |
| Авто-Питье | ❌ Логика не реализована | Нужно найти C# код | Высокий |
| Авто-Движение | ❌ Логика не реализована | Нужно найти C# код | Высокий |
| Слежение за локацией | ❌ Логика не реализована | (Android специфичная) | Низкий |
| Авто-Обновление | ❌ Логика не реализована | Нужно найти C# код | Низкий |

---

## 4. Источники для анализа

### Файлы ПК версии для детального изучения

1. `ABClient/ABForms/FormMainAutoBoi.cs` - основная логика автобоя
2. `ABClient/ABForms/FormAutoAttack.cs` - автонападение
3. `ABClient/ABForms/FormAutoBait.cs` - автоприманка (использует приманки для нападения ботов)
4. `ABClient/Lez/LezFight.cs` - автоохота
5. `ABClient/ABForms/FormMainHerbs.cs` - авто-травник (DoHerbAutoCut)
6. `ABClient/ABForms/FormSettingsAutoCut.cs` - настройки авто-травника
7. `ABClient/MyForms/FormSettingsGeneral.cs` - настройки автолечения (DoAutoCure)
8. `ABClient/TInvUd.cs` - инвентарь и автофункции
9. `ABClient/AppVars.cs` - переменные автофункций
10. `ABClient/MyProfile/UserConfigVars.cs` - профиль пользователя
11. `ABClient/PostFilter/AutoFish.cs` - авто-рыбалка (нужно найти)

### Файлы Android уже портированы

- `app/.../lez/LezFight.java` ✅
- `app/.../manager/FastActionManager.java` ✅
- `app/.../manager/AutoFunctionsManager.java` ✅ (переключатели с логикой эксклюзивности)
- `app/.../manager/NeverApi.java` ✅ (HTTP API клиент игры)

### Уже проанализированные файлы

- `TODO/todo_AutoboiState.cs.md`
- `TODO/todo_ABClient/todo_ABForms/todo_FormAutoAttack.cs.md`
- `TODO/todo_ABClient/todo_ABForms/todo_FormAutoBait.cs.md`
- `TODO/todo_ABClient/todo_ABForms/todo_FormSettingsAutoCut.cs.md`

---

## 5. Текущие QuickActionType в Android

Текущий список в `QuickActionType.java`:

```java
// Основные действия
AUTO_FIGHT("Авто-Бой", "auto_fight"),
QUICK_ACTIONS("Быстрые действия ▼", "quick_actions"),
AUTO_FISH("Авторыбалка", "auto_fish"),
AUTO_BAIT("Автоприманка", "auto_bait"),
LEZ_FIGHT("Автоохота", "lez_fight"),
AUTO_ATTACK("Авто-Нападение", "auto_attack"),
AUTO_INVISIBLE("Авто-Невид", "auto_invisible"),
LOCATION_TRACKING("Слежение за локацией", "location_tracking"),
AUTO_DETECT("Авто-Обнаружение", "auto_detect"),
AUTO_SUMMON("Авто-Тотем", "auto_summon"),
AUTO_CURE("Автолечение", "auto_cure"),
AUTO_DRINK("Авто-Питье", "auto_drink"),
AUTO_MOVING("Авто-Движение", "auto_moving"),
AUTO_CUT("Авто-Травник", "auto_cut"),
AUTO_REFRESH("Авто-Обновление", "auto_refresh"),

// Дополнительные действия
OPEN_CONTACTS("Открыть контакты", "open_contacts"),
OPEN_PINFO("Открыть PINFO", "open_pinfo"),
OPEN_LOGS("Открыть Логи", "open_logs"),
REFRESH_CONTACTS("Обновить контакты", "refresh_contacts"),

// Быстрые действия на себя
QUICK_SELF_RASS("Рассеять невид", "quick_self_rass", "selfRass"),
QUICK_OPEN_NEVID("Обнаружение", "quick_open_nevid", "openNevid"),
QUICK_TELEPORT("Телепорт", "quick_teleport", "teleport"),
QUICK_ISLAND("Остров (Туротор)", "quick_island", "island"),
QUICK_TOTEM("Тотем", "quick_totem", "totem"),
QUICK_ELIXIR_BLAZ("Эликсир Блаженства", "quick_elixir_blaz", "elixirBlaz"),
QUICK_ELIXIR_CURE("Эликсир Исцеления", "quick_elixir_cure", "elixirCure"),
QUICK_ELIXIR_RESTORE("Эликсир Восстановления", "quick_elixir_restore", "elixirRestore"),
```

Всего: **18 функций** (12 основных авто + 4 доп + 8 быстрых действий на себя)

---

## 6. Рекомендации

1. **Различать заглушки и реальную логику** - AutoFunctionsManager содержит только переключатели, реальная логика в отдельных классах
2. **Автобой и Быстрые действия уже работают** - LezFight.java и FastActionManager.java полностью портированы
3. **Сосредоточиться на логике** - нужно портировать реальную логику авто-функций из C#
4. **Начать с автолечения и автопитья** - это наиболее востребованные функции после автобоя

---

## 7. Итоговая таблица статусов

| Компонент | Статус | Комментарий |
|-----------|--------|-------------|
| LezFight.java | ✅ | Полноценный порт логики автобоя |
| FastActionManager.java | ✅ | Полноценный порт быстрых действий + FastAttackAsync |
| NeverApi.java | ✅ | HTTP API клиент игры (getAll, getFlog, getUserId) |
| AutoFunctionsManager.java | ✅ | Переключатели с логикой эксклюзивности и универсальными методами |
| QuickButtonsPanel.java | ✅ | Полная обработка всех 14 авто-функций в executeAction |
| Авто-Рыбалка (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
| Авто-Охота (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
| Авто-Приманка (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
| Авто-Травник (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
| Авто-Невид (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
| Авто-Обнаружение (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
| Авто-Тотем (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
| Авто-Лечение (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
| Авто-Питье (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
| Авто-Движение (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
| Слежение за локацией | ❌ | Переключатель работает, логика не реализована |
| Авто-Обновление (логика HTTP) | ❌ | Переключатель работает, реальный HTTP не реализован |
