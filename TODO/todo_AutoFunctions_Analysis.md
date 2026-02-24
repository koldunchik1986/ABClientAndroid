# Анализ авто-функций ПК версии ABClient и статус реализации в Android

## 1. Список авто-функций в ПК версии (C#)

### Основные авто-функции

| № | Название (ПК) | Название в коде | Описание | Переменная в AppVars/Profile |
|---|---------------|-----------------|----------|------------------------------|
| 1 | Авто-Бой | Autoboi | Автоматический бой с настраиваемыми комбинациями ударов/блоков | `Autoboi` (enum) |
| 2 | Авто-Рыбалка | AutoFish | Автоматическая рыбалка | `AutoFishCheckUd`, `AutoFishWearUd`, `AutoFishDrink` |
| 3 | Авто-Приманка | FormAutoBait | Использует приманки для нападения ботов | `FormAutoBait` |
| 4 | Авто-Охота | AutoSkin | Автоматическое разделывание добычи и одевание инструментов | `AppVars.Profile.SkinAuto`, `AppVars.AutoSkinHand` |
| 5 | Авто-Бой | LezFight | Полноценный автобой (основной) | `LezFight` (класс) |
| 6 | Авто-Нападение | AutoAttack | Автоматическое нападение | `AutoAttackToolId` |
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
| `FormMainAutoBoi.cs` | Управление авто-боем |
| `FormAutoAttack.cs` | Форма Авто-Нападения |
| `FormAutoBait.cs` | Форма Авто-Приманки (использует приманки для нападения ботов) |
| `FormSettingsAutoCut.cs` | Настройки Авто-Травника (DoHerbAutoCut) |
| `FormSettingsGeneral.designer.cs` | Настройки Авто-Лечения (DoAutoCure), Авто-Рыбалки |
| `LezFight.cs` | Логика Авто-Охоты |
| `FormMainHerbs.cs` | Логика Авто-Травника (DoHerbAutoCut) |
| `AutoboiState.cs` | Состояния Авто-Боя |
| `TInvUd.cs` | Инвентарь и Авто-Функции |
| `AppVars.cs` | Глобальные переменные |
| `UserConfigVars.cs` | Профиль пользователя (DoAutoCure) |

---

## 2. Статус реализации в Android

### Реализованные (полностью или частично)

| Функция | Статус | Файлы |
|---------|--------|-------|
| Автобой (AUTO_FIGHT) | ✅ Реализован | `FastActionManager.java`, `AutoboiState.java`, `AutoFunctionsManager.java` |
| Автонападение (AUTO_ATTACK) | ✅ Реализован (FastActionManager) | `FastActionManager.java` (множественные виды атак) |
| Быстрые действия на себя | ✅ Реализованы | `FastActionManager.java`, `QuickButtonsPanel.java` |
| Переключатели авто-функций | ✅ Реализованы | `AutoFunctionsManager.java`, `QuickButtonsPanel.java` |

### Реализованные как заглушки (переключатель есть, логика нет)

| Функция | Статус | Комментарий |
|---------|--------|-------------|
| Авторыбалка (AUTO_FISH) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| Автобой (LezFight) | ✅ Реализован | `FastActionManager.java`, `LezFight.java`, `AutoboiState.java`, `AutoFunctionsManager.java` |
| Автоохота (AUTO_SKIN) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| АвтоНевид (AUTO_INVISIBLE) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| АвтоОбнаружение (AUTO_DETECT) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| АвтоТотем (AUTO_SUMMON) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| Автолечение (AUTO_CURE) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| Слежение за локацией (LOCATION_TRACKING) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| АвтоОбновление (AUTO_REFRESH) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| АвтоПитье (AUTO_DRINK) | ⚠️ Заглушка | **Добавлен** - переключатель работает, логика не реализована |
| АвтоДвижение (AUTO_MOVING) | ⚠️ Заглушка | **Добавлен** - переключатель работает, логика не реализована |
| Авто-травник (AUTO_CUT) | ⚠️ Заглушка | **Добавлен** - переключатель работает, логика не реализована |

### Не реализованные в QuickButtons

| Функция | Статус |
|---------|--------|
| - | Все добавлены |

---

## 3. План портирования

### Приоритет 1 (Основные авто-функции)

- [x] Добавить АвтоПитье (AutoDrink) в QuickButtons
- [x] Добавить АвтоДвижение (AutoMoving) в QuickButtons
- [ ] Реализовать логику АвтоПитья
- [ ] Реализовать логику АвтоДвижения

### Приоритет 2 (Дополнительные)

- [x] Добавить Авто-травник (AutoCut/DoHerbAutoCut) в QuickButtons
- [x] Добавить АвтоОбновление в QuickButtons
- [ ] Реализовать логику АвтоРыбалки (AutoFish)
- [ ] Реализовать логику АвтоОхоты (LezFight)
- [ ] Реализовать логику АвтоНевида
- [ ] Реализовать логику АвтоОбнаружения
- [ ] Реализовать логику АвтоТотема
- [ ] Реализовать логику Автолечения (AutoCure)
- [ ] Реализовать логику Слежения за локацией

### Приоритет 3 (Продвинутые)

- [ ] Реализовать логику Авто-травника (DoHerbAutoCut)
- [ ] Реализовать логику АвтоОбновления

---

## 4. Источники для анализа

### Файлы для детального изучения

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

1. **Не удалять существующую функциональность** - все переключатели работают, нужно только добавить логику
2. **Использовать FastActionManager как базу** - многие авто-функции основаны на быстрых действиях
3. **Портировать по очереди** - начать с наиболее востребованных (АвтоПитье, АвтоДвижение)
4. **Документировать каждую функцию** - создавать отдельные TODO файлы для каждой авто-функции
