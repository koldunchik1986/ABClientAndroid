# Анализ авто-функций ПК версии ABClient и статус реализации в Android

## 1. Список авто-функций в ПК версии (C#)

### Основные авто-функции

| № | Название (ПК) | Описание | Переменная в AppVars |
|---|---------------|----------|---------------------|
| 1 | Автобой (Autoboi) | Автоматический бой с настраиваемыми комбинациями ударов/блоков | `Autoboi` (enum) |
| 2 | Авторыбалка (AutoFish) | Автоматическая рыбалка | `AutoFishCheckUd`, `AutoFishWearUd`, `AutoFishDrink` |
| 3 | Автоохота (AutoHunt) | Автоматическая охота на монстров | (нужно найти) |
| 4 | Автонападение (AutoAttack) | Автоматическое нападение | `AutoAttackToolId` |
| 5 | АвтоНевид (AutoNevid) | Автоматическое поддержание невидимости | `AutoOpenNevid`, `DoSelfNevid` |
| 6 | АвтоОбнаружение (AutoDetect) | Автоматическое обнаружение невидимых | (нужно найти) |
| 7 | АвтоТотем (AutoTotem) | Автоматическое использование тотема | (нужно найти) |
| 8 | АвтоЛечение (AutoHeal) | Автоматическое лечение | (нужно найти) |
| 9 | АвтоПитье (AutoDrink) | Автоматическое использование зелий/эликсиров | `AutoDrink` |
| 10 | АвтоДвижение (AutoMoving) | Автоматическое перемещение по локациям | `AutoMoving`, `AutoMovingDestinaton` |
| 11 | АвтоСкин (AutoSkin) | Автоматическая нарезка (скин) | `AutoSkinCheckUm`, `AutoSkinHand` |
| 12 | АвтоОбновление (AutoRefresh) | Автоматическое обновление страницы | `AutoRefresh` |
| 13 | Слежение за локацией (LocationTracking) | Отслеживание текущей локации | (добавлено в Android) |

### Файлы ПК версии связанные с авто-функциями

| Файл | Описание |
|------|----------|
| `FormMainAutoBoi.cs` | Управление автобоем |
| `FormAutoAttack.cs` | Форма автонападения |
| `FormAutoBait.cs` | Форма автонаживки |
| `FormSettingsAutoCut.cs` | Настройки автоскина |
| `AutoboiState.cs` | Состояния автобоя |
| `TInvUd.cs` | Инвентарь и авто-функции |
| `AppVars.cs` | Глобальные переменные |

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
| Авторыбалка (AUTO_RECALL) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| Автоохота (AUTO_HUNT) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| АвтоНевид (AUTO_INVISIBLE) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| АвтоОбнаружение (AUTO_DETECT) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| АвтоТотем (AUTO_SUMMON) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| АвтоЛечение (AUTO_HEAL) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| Слежение за локацией (LOCATION_TRACKING) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |
| АвтоОбновление (AUTO_REFRESH) | ⚠️ Заглушка | Переключатель работает, но логика не реализована |

### Не реализованные в QuickButtons

| Функция | Статус |
|---------|--------|
| АвтоПитье (AutoDrink) | ❌ Не добавлен в QuickButtons |
| АвтоДвижение (AutoMoving) | ❌ Не добавлен в QuickButtons |
| АвтоСкин (AutoSkin) | ❌ Не добавлен в QuickButtons |

---

## 3. План портирования

### Приоритет 1 (Основные авто-функции)

- [ ] Реализовать логику Автобоя (уже есть база)
- [ ] Добавить АвтоПитье (AutoDrink) в QuickButtons
- [ ] Добавить АвтоДвижение (AutoMoving) в QuickButtons

### Приоритет 2 (Дополнительные)

- [ ] Реализовать логику АвтоРыбалки
- [ ] Реализовать логику АвтоОхоты
- [ ] Реализовать логику АвтоНевида
- [ ] Реализовать логику АвтоОбнаружения
- [ ] Реализовать логику АвтоТотема
- [ ] Реализовать логику АвтоЛечения
- [ ] Реализовать логику Слежения за локацией

### Приоритет 3 (Продвинутые)

- [ ] Добавить АвтоСкин
- [ ] Добавить АвтоОбновление

---

## 4. Источники для анализа

### Файлы для детального изучения

1. `ABClient/ABForms/FormMainAutoBoi.cs` - основная логика автобоя
2. `ABClient/ABForms/FormAutoAttack.cs` - автонападение
3. `ABClient/ABForms/FormAutoBait.cs` - авторыбалка
4. `ABClient/ABForms/FormSettingsAutoCut.cs` - настройки автоскина
5. `ABClient/TInvUd.cs` - инвентарь и автофункции
6. `ABClient/AppVars.cs` - переменные автофункций

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
AUTO_RECALL("Авто-Рыбалка", "auto_recall"),
AUTO_HUNT("Авто-Охота", "auto_hunt"),
AUTO_ATTACK("Авто-Нападение", "auto_attack"),
AUTO_INVISIBLE("Авто-Невид", "auto_invisible"),
LOCATION_TRACKING("Слежение за локацией", "location_tracking"),
AUTO_DETECT("Авто-Обнаружение", "auto_detect"),
AUTO_SUMMON("Авто-Тотем", "auto_summon"),
AUTO_HEAL("Авто-Лечение", "auto_heal"),

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

---

## 6. Рекомендации

1. **Не удалять существующую функциональность** - все переключатели работают, нужно только добавить логику
2. **Использовать FastActionManager как базу** - многие авто-функции основаны на быстрых действиях
3. **Портировать по очереди** - начать с наиболее востребованных (АвтоПитье, АвтоДвижение)
4. **Документировать каждую функцию** - создавать отдельные TODO файлы для каждой авто-функции
