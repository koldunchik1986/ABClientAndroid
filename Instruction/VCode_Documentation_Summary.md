# VCode документация: Краткое резюме

## Созданные файлы

### 1. **VCode_Mechanism_Analysis.md** (1024 строк)
Подробный архитектурный анализ системы управления VCode с 10 разделами:

- **Разделы 1-2**: Архитектура SessionManager и SessionContext, структура механизма
- **Раздел 3**: 6 regex patterns для парсинга VCode из HTML
- **Раздел 4**: Жизненный цикл VCode (5 фаз: парсинг → кэширование → использование → инвалидация → fallback)
- **Раздел 5**: Версионирование контекста и обнаружение смены сеанса
- **Раздел 6**: FIGHT_FALLBACK_MODE (120 сек) и проблема временных окон
- **Раздел 7**: Cache lifetime и ageMs (старость VCode в миллисекундах)  
- **Раздел 8**: Thread-safety через ReentrantReadWriteLock (parallel readers + exclusive writer)
- **Раздел 9**: 4 критичные проблемы (PHPSESSID смена, 9ms gap, быстрые действия, race conditions)
- **Раздел 10**: Логирование и обозначения в логах (✅ ⚠️ ❌ 📍 🎯 📋)

**Для кого:** Архитекторы, senior разработчики, те кто исправляет сложные VCode баги

---

### 2. **VCode_Recovery_Guide.md** (800 строк)
Пошаговая инструкция по диагностике и восстановлению при всех типах VCode ошибок:

- **Раздел 1**: 5 признаков VCode бага (403 Invalid VCode, NO_SESSION, PARSE_VCODE_FAILED, STALE_SESSION, прерывание боя)
- **Раздел 2**: 5-шаговая процедура диагностики с grep командами и ожидаемыми результатами
- **Раздел 3**: **4 уровня recovery** (от простого к сложному):
  - **Уровень 1**: Парсинг VCode (проверка regex patterns)
  - **Уровень 2**: Кэширование (очистка контекста, PHPSESSID)
  - **Уровень 3**: Безопасность действий (null checks, fallback логика)
  - **Уровень 4**: Синхронизация потоков (markFightInProgress timing)
- **Раздел 4**: Универсальный чек-лист для каждого бага (7 пунктов)
- **Раздел 5**: 4 реальных сценария с примерами логов и решениями
- **Раздел 6**: Advanced Diagnostics (полный статус, логирование запросов, перехват ошибок)

**Для кого:** Junior разработчики, QA специалисты, те кто мониторит реальные баги

---

## Ключевые моменты для быстрого поиска

### Если бой прерывается после 5 сек:
→ `VCode_Mechanism_Analysis.md` раздел 6 (FIGHT_FALLBACK_MODE)
→ `VCode_Recovery_Guide.md` сценарий A

### Если видна ошибка "Неверный код защиты" (403):
→ `VCode_Recovery_Guide.md` раздел 1, признак 1
→ Чек-лист → Алгоритм recovery

### Если SessionManager не парсит VCode:
→ `VCode_Mechanism_Analysis.md` раздел 3 (6 patterns)
→ `VCode_Recovery_Guide.md` уровень 1

### Если бой теряет VCode перед ударом:
→ `VCode_Mechanism_Analysis.md` раздел 8 (критичные точки)
→ `VCode_Recovery_Guide.md` сценарий D (9ms gap)
→ Проверить FightAuto.java порядок вызовов

### Если нужен полный анализ кода:
→ `VCode_Mechanism_Analysis.md` раздел 1-8
→ Изучить класс SessionManager.java в коде

---

## Структура документов

```
VCode_Mechanism_Analysis.md (СПРАВОЧНИК)
├── 1. Архитектура кеширования
├── 2. Точки парсинга (6 regex)
├── 3. Жизненный цикл (5 фаз)
├── 4. SessionContext и версионирование
├── 5. FIGHT_FALLBACK_MODE (120s)
├── 6. Cache lifetime & ageMs
├── 7. Thread-safety (ReentrantReadWriteLock)
├── 8. Критичные точки сбоев (4 проблемы)
├── 9. Источники и примеры
└── 10. Логирование (✅ ⚠️ ❌)

VCode_Recovery_Guide.md (ИНСТРУКЦИЯ)
├── 1. Признаки бага (5 типов)
├── 2. Процедура диагностики (5 шагов)
├── 3. Recovery By Level (4 уровня)
│   ├── Уровень 1: Парсинг
│   ├── Уровень 2: Кэш
│   ├── Уровень 3: Безопасность
│   └── Уровень 4: Потоки
├── 4. Чек-лист (7 пунктов)
├── 5. Реальные сценарии (A-D)
└── 6. Advanced Diagnostics
```

---

## Примеры использования

### Сценарий 1: Обнаружена новая VCode ошибка
```
1. Открыть VCode_Recovery_Guide.md → Раздел 1 (Признаки)
2. Найти совпадение с одним из 5 признаков
3. Перейти → Раздел 2 (Диагностика, шаг 1-5)
4. Выполнить соответствующий уровень (Раздел 3)
5. Использовать чек-лист (Раздел 4) для проверки
6. Если не помогает → Advanced Diagnostics (Раздел 6)
```

### Сценарий 2: Нужен deep dive в архитектуру
```
1. Открыть VCode_Mechanism_Analysis.md
2. Прочитать Раздел 1-3 (основы)
3. Прочитать Раздел 8 (критичные точки, которые интересуют)
4. Прочитать в коде SessionManager.java
5. Коррелировать с логированием (Раздел 10)
```

### Сценарий 3: Нужно добавить новую функцию с VCode
```
1. Прочитать VCode_Mechanism_Analysis.md Раздел 3-4 (жизненный цикл)
2. Найти похожую функцию в коде (например, FishAjaxPhp.java)
3. Скопировать паттерн использования:
   - String vcode = SessionManager.getInstance().getValidVCodeForAction("action_name");
   - if (vcode == null) { fallback... }
   - sendRequest(vcode);
4. Добавить логирование из Раздела 10
5. Использовать чек-лист из Recovery_Guide.md для проверки
```

---

## Критичные файлы в проекте (для reference)

| Файл | Класс | Назначение |
|------|-------|-----------|
| `app/src/main/.../utils/SessionManager.java` | SessionManager | Центральный обработчик VCode |
| `app/src/main/.../utils/SessionContext.java` | SessionContext | Контейнер данных сеанса |
| `app/src/main/.../webview/WebViewRequestInterceptor.java` | — | Вызывает parseVCodeFromHtml() |
| `app/src/main/.../postfilter/FishAjaxPhp.java` | — | Пример использования fish_act |
| `app/src/main/.../postfilter/MainPhp.java` | — | Пример использования nav-action |
| `app/src/main/.../postfilter/FightAuto.java` | — | Критичный markFightInProgress() |
| `app/src/main/.../lez/LezFight.java` | LezFight | Использует fight_fallback |

---

## Быстрая справка: Команды grep для диагностики

```bash
# 1. Проверить парсится ли VCode
grep "VCODE_PARSED" logcat.txt | head -5

# 2. Проверить нет ли ошибок парсинга
grep "PARSE_VCODE_FAILED" logcat.txt

# 3. Проверить какой возраст VCode при использовании
grep "VALID_VCODE" logcat.txt | grep -oE "ageMs=[0-9]+" | sort | uniq -c

# 4. Проверить есть ли ошибки "Invalid VCode"
grep "INVALID_CODE_ERROR" logcat.txt

# 5. Проверить есть ли NO_SESSION
grep "NO_SESSION" logcat.txt

# 6. Проверить боевой контекст
grep "FIGHT_STARTED\|FIGHT_ENDED\|FIGHT_CACHE" logcat.txt

# 7. Полный timeline для одного боя
grep -E "FIGHT_STARTED|VALID_VCODE.*fight_fallback|FIGHT_ENDED" logcat.txt | head -20
```

---

## Закрепление: Три правила VCode

### ✅ Правило 1: ВСЕГДА вызывать getValidVCodeForAction() перед использованием
```java
String vcode = SessionManager.getInstance().getValidVCodeForAction("action_name");
if (vcode == null) {
    // fallback
    return;
}
// использовать vcode
```

### ✅ Правило 2: Вызвать markFightInProgress() ДО LezFight конструктора
```java
SessionManager.getInstance().markFightInProgress();  // ПЕРЕД
new LezFight(html).buildFrame();                     // ПОСЛЕ
```

### ✅ Правило 3: Логировать все VCode операции
```java
Log.d(TAG, "✅ VCODE_USED: " + vcode.substring(0, 8) + "...");
FileLogger.trace("Component", "VCode obtained for action_name");
```

---

## Документы готовы к использованию!

**Оба файла находятся в:**
- `/Instruction/VCode_Mechanism_Analysis.md` — Справочник по архитектуре
- `/Instruction/VCode_Recovery_Guide.md` — Инструкция по восстановлению

**Рекомендуемый порядок чтения:**
1. Сначала прочитать `VCode_Recovery_Guide.md` раздел 1-2 для понимания проблемы
2. Затем `VCode_Mechanism_Analysis.md` раздел 1-3 для понимания системы
3. По мере необходимости углубляться в специфические разделы
