# MASTER PLAN: Phase 2-5 Refactoring

**Дата:** 3 апреля 2026  
**Статус:** 📋 PLANNING  
**Объем:** ~2000 строк кода, 50+ методов рефакторинга  

---

## PHASE 2: PostFilter Cleanup + Filter.java Refactoring

### Задачи
- [ ] Удалить 19 пустых файлов из postfilter
- [ ] Обновить вызовы в Filter.java (26 вызовов удалить)
- [ ] Рефакторинг Filter.java на JsFilterRouter + HtmlFilterRouter
- [ ] Создать документацию

### Файлы на удаление (19 шт)
```
ArenaJs, BuildingJs, ForumTopicJs, LogsJs, NlPinfoJs, OutpostJs, 
Pinfo, PinfoJs, PinfonewJs, PvJs, RouletteAjaxPhp, ShopAjaxPhp, 
ShopJs, SlotsJs, SvitokJs, TarenaJs, TopJs, TowerJs, TradePhp
```

### Оценка
- **Объем:** ~280 строк кода + 19 файлов
- **Время:** 1-2 часа
- **Риск:** LOW (удаление пустых файлов безопасно)
- **Приоритет:** MEDIUM

---

## PHASE 3: AppVars VCode Migration (CRITICAL)

### Задачи
- [ ] Мигрировать 4 файла на SessionManager.getValidVCodeForAction()
- [ ] Добавить fallback'ы (reload main.php при null)
- [ ] Удалить deprecated AppVars.FishCurrentVcode
- [ ] Полное логирование (Log.w + FileLogger.trace)

### Файлы для миграции (5 шт)
1. **TreasureDig.java** (строки 404-405) - fast action клад
2. **FastActionManager.java** (строки 2217-2218) - быстрые удары
3. **AutoFunctionsManager.java** (строка 759-760) - recovery reload
4. **AutoFunctionsManager.java** (строка 1909) - LogOff
5. **MainActivity.java** (строки 1526-1527) - ⚠️ ОСТАВИТЬ БЕЗ ИЗМЕНЕНИЙ

### Оценка
- **Объем:** ~160 строк изменений
- **Время:** 1-1.5 часа
- **Риск:** MEDIUM (критичные места, нужна тестирование)
- **Приоритет:** 🔴 CRITICAL (RULE 5)

---

## PHASE 4: MainPhp.java Refactoring (LARGEST)

### Текущее состояние
- **6229 строк** (монолитный файл)
- **~100-110 методов**
- **Нарушение Rule 6** (один файл делает всё)

### Задачи
- [ ] Рефакторинг структуры (выделение блоков)
- [ ] Извлечение Handler'ов для >3 условий
- [ ] Оптимизация организации
- [ ] Добавление FileLogger в критичные цепочки
- [ ] Документация

### Сложные блоки (места для Handler'ов)
- **Боевая логика** (~800 строк) - TODO: check if extracted to FightAuto
- **Инвентарь и вещи** (~600 строк) - использует InventoryParser
- **Автонавигация** (~300 строк) - MainPhpCityNavigation
- **Быстрые действия** (~200 строк) - интеграция с fast-action
- **VCode обработка** (~150 строк) - RULE 5 - check SessionManager usage

### Оценка
- **Объем:** ~6000+ строк (LARGEST)
- **Время:** 3-4 часа
- **Риск:** HIGH (монолитный файл, много зависимостей)
- **Приоритет:** MEDIUM (важное, но не критичное)

---

## PHASE 5: ChatStats Enhancement

### Текущее состояние
- **636 строк** (OK размер)
- **~25-30 методов**
- **Статус:** ✅ Здоров, но нужно добавить FileLogger

### Задачи
- [ ] Добавить FileLogger.trace() во все критичные методы
- [ ] Документация по логированию статистики
- [ ] Проверка корректности логики парсинга

### Оценка
- **Объем:** ~80 строк добавлений (FileLogger вызовы)
- **Время:** 0.5 часа
- **Риск:** LOW (добавление логирования)
- **Приоритет:** LOW (уже здоров)

---

## EXECUTION ORDER

### 🔴 КРИТИЧНЫЕ (IMMEDIATE)
1. **Phase 3: AppVars VCode Migration** (RULE 5, 4 файла)
   - Мигрировать AppVars.VCode на SessionManager
   - Добавить fallback'ы
   - Полное логирование

### 🟠 ВЫСОКИЕ (THIS WEEK)
2. **Phase 2: PostFilter Cleanup** (19 файлов, Filter.java рефакторинг)
   - Удалить пустые файлы
   - Рефакторинг Filter.java на роутеры

3. **Phase 4: MainPhp.java Refactoring** (6000+ строк, LARGEST)
   - Декомпозиция на логические блоки
   - Извлечение Handler'ов
   - FileLogger в критичных местах

### 🟡 СРЕДНИЕ (NEXT WEEK)
4. **Phase 5: ChatStats Enhancement** (добавить FileLogger)

---

## SUMMARY

| Phase | Название | Объем | Время | Риск | Статус |
|-------|----------|-------|-------|------|--------|
| **1** | ✅ ParseUtils Consolidation | 23+ вызовов | 2ч | LOW | **DONE** |
| **2** | PostFilter Cleanup | 19 файлов + Filter | 1-2ч | LOW | 📋 TODO |
| **3** | VCode Migration (RULE 5) | 4 файла | 1-1.5ч | MEDIUM | 🔴 CRITICAL |
| **4** | MainPhp Refactoring | 6200 строк | 3-4ч | HIGH | 📋 TODO |
| **5** | ChatStats Enhancement | 80 строк | 0.5ч | LOW | 📋 TODO |

**Всего времени:** ~7-9 часов (1-2 дня)

---

## NEXT STEPS

1. ✅ Phase 1: DONE
2. ⏳ Phase 3: START IMMEDIATELY (CRITICAL)
3. ⏳ Phase 2: START AFTER Phase 3
4. ⏳ Phase 4: START AFTER Phase 2  
5. ⏳ Phase 5: START AFTER Phase 4

