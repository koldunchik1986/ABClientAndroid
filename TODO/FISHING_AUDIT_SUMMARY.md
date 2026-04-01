# КРАТКОЕ РЕЗЮМЕ АУДИТА АВТО-РЫБАЛКИ

**Дата**: 01.04.2026  
**Статус**: 🔴 АРХИТЕКТУРНАЯ ПРОБЛЕМА  
**Приоритет**: 🚨 КРИТИЧЕСКИЙ

---

## ТРИ ГЛАВНЫХ ВЫВОДА

### 1️⃣ УЖЕ НАЙДЕНА И ЗАДОКУМЕНТИРОВАНА 

**Проблема**: vcode сохраняется из неправильного источника  
- ❌ Сейчас: из `fish_ajax.php?act=1` response (line 225 в FishAjaxPhp.java)
- ✅ Должно быть: из `main.php?get_id=55` озера (озеро с формой выбора приманки)

**Почему это проблема**:
- act=1 это ТЕХНИЧЕСКИЙ ЗОНД, не основной запрос
- vcode из act=1 может быть устаревшим на сервере
- Через 5 минут vcode истекает и следующий цикл использует СТАРЫЙ код
- Результат: "Неверный код защиты" на каждом запросе

---

## ТЕКУЩИЙ FLOW (НЕПРАВИЛЬНЫЙ)

```
kickFishCycleAttempt()
  ↓
JS открывает озеро & act=1
  ↓
processFishAct1()
  ├─ Парсим state.vcode из act=1 response
  ├─ AppVars.FishCurrentVcode = state.vcode  🔴 НЕПРАВИЛЬНЫЙ ИСТОЧНИК
  ├─ Отправляем act=2
  └─ syncFishCooldownAndScheduleNextCycle()
     └─ Планируем следующий kickFishCycleAttempt()
        (БЕЗ загрузки озера, БЕЗ свежего vcode!)
```

---

## ПРАВИЛЬНЫЙ FLOW (КАК В ПК-ВЕРСИИ)

```
kickFishCycleAttempt()
  ↓
★ executeFishingCycleCore() 🟢 ГЛАВНЫЙ ЦИКЛ
  ├─ ★ loadFreshLakeHtml() → GET main.php?get_id=55
  ├─ ★ parseAndPrepareFromLakeHtml()
  │  └─ Извлекаем СВЕЖИЙ vcode из озера 🟢
  ├─ Выбираем приманку
  └─ Отправляем действие с ПРАВИЛЬНЫМ vcode
     └─ Следующий цикл → новое озеро → новый свежий vcode
```

---

## ЧТО НУЖНО ИЗМЕНИТЬ

### Добавить в MainPhp.java (при загрузке озера)

```java
if (address.contains("get_id=55") && !address.contains("&act=")) {
    AppVars.ContentLakeHtml = html;  // ← Кэшируем озеро
    AppVars.LastLakeHtmlCacheTimeMs = System.currentTimeMillis();
}
```

### Добавить в FishAjaxPhp.java

1. **executeFishingCycleCore()** - главный цикл
2. **parseAndPrepareFromLakeHtml()** - парс озера
3. **selectBaitFromLakeHtml()** - выбор приманки из озера

### Изменить в FishAjaxPhp.java

```java
// БЫЛО (строка 225)
AppVars.FishCurrentVcode = state.vcode;  // ❌ из act=1

// ДОЛЖНО БЫТЬ
// (удалить, vcode будет из озера)
```

### Обновить kickFishCycleAttempt()

Вызвать `executeFishingCycleCore()` вместо текущей логики

---

## ФАЙЛЫ ДЛЯ АНАЛИЗА

### 📖 Полный аудит
→ [FISHING_CYCLE_COMPLETE_AUDIT.md](FISHING_CYCLE_COMPLETE_AUDIT.md)

Содержит:
- Точное расположение всех функций с номерами строк
- Кэш кода для каждого компонента
- Пошаговый план реализации
- Тестовые сценарии

### 🔍 Файлы кода

- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java` (line ~3380)
  - mainPhpAutoFishPrepare() — вспомогательная функция парса озера
  
- `app/src/main/java/ru/neverlands/abclient/postfilter/FishAjaxPhp.java`
  - processFishAct1() (line 180-260) — НЕПРАВИЛЬНОЕ сохранение vcode
  - kickFishCycleAttempt() (line 464+) — вызов цикла
  - syncFishCooldownAndScheduleNextCycle() (line 379+) — планирование

- `app/src/main/java/ru/neverlands/abclient/postfilter/Filter.java` (line 170)
  - Маршрутизация fish_ajax запросов

---

## БЫСТРЫЙ ЧЕКЛИСТ ИСПРАВЛЕНИЙ

- [ ] Кэшировать озеро в MainPhp
- [ ] Создать executeFishingCycleCore() в FichAjaxPhp
- [ ] Создать parseAndPrepareFromLakeHtml()
- [ ] Создать selectBaitFromLakeHtml() с проверкой остатка
- [ ] Удалить неправильное сохранение vcode из act=1
- [ ] Обновить kickFishCycleAttempt()
- [ ] Протестировать рыбалку 5+ минут без ошибок

---

## ПОЧЕМУ ДО СЕШНЯ ИСПРАВЛЕНИЯ НЕ СРАБОТАЛИ

### Что было раньше:
1. В `submitCaptchaSolution()` добавлена функция `refreshFishVcodeInUrl()`
2. Она пытается обновить vcode в URL перед отправкой

### Почему не сработало:
- **Проблема в архитектуре**, а не в деталях
- vcode ВСЕГДА из act=1 response (неправильный источник)
- Даже если обновить его перед отправкой, через 5 минут снова истечет
- Нужно изменить ИСТОЧНИК vcode, а не способ передачи

### Почему новое решение поможет:
- Каждый цикл → НОВОЕ озеро → НОВЫЙ свежий vcode
- vcode гарантирова действительный ПЕРЕД отправкой
- Сервер не будет отклонять запросы

---

## ОЖИДАЕМЫЙ РЕЗУЛЬТАТ

### До исправления:
```
00:44:42 - ✅ Судак [3/3]
00:50:14 - ❌ Неверный код защиты
00:51:16 - ❌ Неверный код защиты
00:52:18 - ❌ Неверный код защиты
00:53:20 - ❌ Неверный код защиты
01:01:16 - ✅ Судак [3/3]  (работает после тайм-аута)
```

### После исправления:
```
00:44:42 - ✅ Судак [3/3]
00:50:14 - ✅ Судак [2/3]  (свежий vcode!)
00:55:45 - ✅ Судак [3/4]  (свежий vcode!)
01:01:17 - ✅ Судак [3/3]  (свежий vcode!)
... бесконечно работает без ошибок
```

---

## ДОКУМЕНТАЦИЯ

✅ Полный аудит: [FISHING_CYCLE_COMPLETE_AUDIT.md](FISHING_CYCLE_COMPLETE_AUDIT.md)  
✅ Рекомендуемая реализация: [FISHING_IMPLEMENTATION_GUIDE.md](FISHING_IMPLEMENTATION_GUIDE.md)  
✅ Справочник: [FISHING_QUICK_REFERENCE.md](FISHING_QUICK_REFERENCE.md)

