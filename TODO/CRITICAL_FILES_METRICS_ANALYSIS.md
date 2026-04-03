# Анализ размера и критичности файлов проекта

**Дата анализа:** 3 апреля 2026  
**Фокус:** ChatStats.java и MainPhp.java — два ключевых файла обработки событий

---

## 📊 Метрики файлов

| Параметр | ChatStats.java | MainPhp.java | Примечание |
|----------|---|---|---|
| **Размер (строк)** | 636 | 6229 | MainPhp в **9.8x раз больше** |
| **Примерные методы** | ~25-30 | ~100-110 | Диспаритет в ответственности |
| **Средний размер метода** | ~20-25 строк | ~60 строк | MainPhp методы более вязкие |
| **Публичные методы** | ~15 | ~40+ | Излишняя poверхность API |
| **Вспомогательные методы** | ~10 | ~60+ | MainPhp содержит чужую логику |
| **Кодировка** | UTF-8 ✅ | UTF-8 ✅ | Оба файла в правильной кодировке |

---

## 📍 ChatStats.java (636 строк)

### Назначение
**Централизованное управление статистикой игрока:** XP, бои, денежный дроп (NV), ресурсы, рыбалка, лут.

### Основные обязанности

1. **Счетчики собственные:**
   - `totalXp` — накопленный опыт
   - `totalFights` — количество завершенных боев
   - `totalNv` — денежный дроп (NV)
   - `totalFishNv` — доход от рыбалки
   - `totalResourceKg` — общий вес ресурсов

2. **Коллекции:**
   - `resourceKgByType` — разбивка ресурсов по типам (Map)
   - `itemCountByName` — разбивка предметов по названиям (Map)
   - `fishCountByType` — разбивка рыбы по типам (Map)
   - `lootLog` — журнал лута (legacy, для совместимости)

3. **Персистентность:**
   - Сохранение в файл `Logs/<profile>_stat.txt`
   - Загрузка при старте
   - Поддержка legacy-форматов (`LOOT=`)
   - Миграция старых данных

4. **Автоматизация:**
   - Автосброс при переходе на новый день (если включен в профиле)
   - Синхронизация по дате и профилю

### Публичный API

```java
// Счетчики
public static synchronized void addXp(long xp);
public static synchronized long getTotalXp();
public static synchronized void addFight();
public static synchronized long getTotalFights();
public static synchronized long getTotalNv();
public static synchronized double getTotalResourceKg();

// Коллекции
public static synchronized Map<String, Double> getResourceKgByType();
public static synchronized Map<String, Long> getItemCountByName();
public static synchronized Map<String, Long> getFishCountByType();

// Добавление данных
public static synchronized void addFishCatch(String fishName, int count, double incomeNv);
public static synchronized void addLoot(String time, List<String> items);
public static synchronized void addResourceDeltaKg(Map<String, Double> deltaByResource);

// Управление
public static synchronized void reset();
public static synchronized long getStatsElapsedMs();
public static synchronized List<String> getLootLog();
```

### Структура кода
- **Синхронизированные методы:** ~100% покрытие (все методы synchronized для thread-safety)
- **Статические поля:** ~12 основных
- **Приватные помощники:** ~10 методов для парсинга, сохранения, загрузки
- **Regex-паттерны:** 2 (денежный дроп, ресурсы в кг)

### Критичные правила
- ✅ **Правило 0:** UTF-8 без BOM
- ✅ **Правило 7:** Управление FastNeed — не касается ChatStats напрямую, но используется в context'е fast-action'ов

---

## 🚨 MainPhp.java (6229 строк) — ГИГАНТСКИЙ ФАЙЛ

### Назначение
**Главный пост-фильтр для main.php:** обработка боя, инвентаря, быстрых действий, системных сообщений, парсинг HTML.

### Основные обязанности (ВСЕ В ОДНОМ ФАЙЛЕ)

#### 1️⃣ **Обработка боя** (~1500 строк)
- Главный entry-point в `mainPhpFight(String, String)` — здесь обрабатывается вся боевая логика
- Парсинг боевого фрейма, шагов, капчи, завершения
- Построение finish-ссылки, redirect'ов, HTML-статусов
- Управление состоянием: ожидание противника, лечение, восстановление
- Интеграция с FightAuto через bridge'ы
- Логирование и публикация результатов
- Дедупликация боев по `AppVars.LastBoiEndLog`

#### 2️⃣ **Обработка инвентаря** (~900 строк)
- Парсинг списка предметов (метод `mainPhpInv`)
- Упаковка дублей (группировка одинаковых предметов)
- Сортировка,построение bulk-кнопок
- Интеграция с FastActionManager для быстрых действий
- Синхронизация кэша инвентаря

#### 3️⃣ **Быстрые действия** (~800 строк)
- Рыбалка и питье (auto-drink, auto-fish)
- Использование эликсиров (hp/ma recovery)
- Быстрые клики по инвентарю
- Auto-skin (автоснятие шкур)
- Управление cooldown'ами
- Парсинг результатов fast-action'ов

#### 4️⃣ **Капча и защита** (~400 строк)
- Парсинг и разрешение URL капчи
- Показ диалога капчи
- Дедупликация диалогов
- Обработка отклонённых капч

#### 5️⃣ **Утилиты и вспомогательные методы** (~2000 строк)
- Парсинг URL параметров
- Построение редиректа HTML
- Экранирование и форматирование
- Работа с JS-массивами (распаковка переменных)
- Синхронизация витальных значений (HP/MA)
- Управление состоянием завершения боя (probe-finish-confirm)
- Логирование в ChatFilter

### Публичный API (только примеры, их больше 40+)
```java
// Основные entry-points
public static void process(String html, String address, String source);

// Боевые методы
public static String mainPhpFight(String html, String address);
private static String buildWaitForTurnAutoRefreshHtml(...);
private static String buildInPlaceFightAutoRefreshHtml(...);
private static String buildDelayedRedirectHtml(...);
private static String extractFightFinishLinkFromHtml(...);

// Инвентарь
private static String mainPhpInv(String html);
private static String mainPhpInv(String html, boolean cacheOnlyMode);

// Быстрые действия
private static String mainPhpFastBlazElixir(...);
private static String mainPhpFastCurePoisonPotion(...);
// и много других для эликсиров

// Парсинг
private static String extractCaptchaUrl(String html);
private static InsHpSnapshot parseInsHpSnapshot(String html);
private static String getUrlParam(String url, String paramName);

// и еще 40+ методов...
```

### Bridge'ы и адаптеры
- `FIGHT_AUTO_HOST` — 25+ методов для делегирования в FightAuto
- `FAST_ACTION_HOST` — 11 методов для делегирования в FastActionManager
- `TREASURE_DIG_HOST` — 12 методов для делегирования в TreasureDig

### Статические поля (72+ полей!)
- Таймауты и cooldown'ы (~15 полей)
- Состояние боя и инвентаря (~20 полей)
- Кэш капч и диалогов (~10 полей)
- Дедупликационные ключи (~10 полей)
- Маркеры finish-подтверждения (~5 полей)
- Другие флаги и счетчики (~12 полей)

### Критичные правила и зависимости
- ✅ **Правило 4:** Стабильность HTML-кликов — MainPhp отвечает за приоритизацию ручных действий
- ✅ **Правило 5:** SessionManager для VCode — MainPhp должен использовать SessionManager везде (ТРЕБУЕТ АУДИТА)
- ✅ **Правило 6:** Модульная архитектура — MainPhp **НАРУШАЕТ** это правило, содержа слишком много логики
- ✅ **Правило 8:** Event-driven архитектура для боя — MainPhp частично реализует это
- ✅ **Правило 9:** Порядок вызовов при инициализации боя — MainPhp должен гарантировать markFightInProgress() перед LezFight

---

## 🔴 КРИТИЧНЫЕ ЗАДАЧИ РЕФАКТОРИНГА

### Для ChatStats.java

| # | Задача | Приоритет | Статус | Причина |
|---|--------|-----------|--------|---------|
| 1 | **Аудит использования ParseUtils** | 🟡 Средний | `[ ]` | Файл использует ParseUtils.parseLongSafe/parseDoubleSafe — проверить thread-safety |
| 2 | **Проверка UTF-8 в файлах статистики** | 🟡 Средний | `[ ]` | Убедиться что все `FileOutputStream` используют UTF-8 charset |
| 3 | **Логирование всех операций с файлом** | 🟡 Средний | `[ ]` | Добавить FileLogger.trace() для сохранения/загрузки в critical-цепочку |
| 4 | **Оптимизация synchronized блоков** | 🟢 Низкий | `[ ]` | Методы слишком мелкие, можно использовать ReentrantReadWriteLock |
| 5 | **Миграция legacy LOOT= формата** | 🟢 Низкий | `[ ]` | Завершить миграцию старых файлов и удалить поддержку legacy |

### Для MainPhp.java — КРИТИЧНОЕ

| # | Задача | Приоритет | Статус | Связь с ПК | Замечание |
|---|--------|-----------|--------|-----------|-----------|
| **1** | **Выделениеобработки боя в FightProcessor** | 🔴 КРИТИЧНЫЙ | `[ ]` | mainPhpFight + все helper'ы боя (~1500 строк) | Сложность: 9/10 |
| **2** | **Выделение парсигнера инвентаря в InventoryProcessor** | 🔴 КРИТИЧНЫЙ | `[ ]` | mainPhpInv + быстрые действия с инвом (~900 строк) | Сложность: 7/10 |
| **3** | **Выделение утилит капчи в CaptchaHandler** | 🟠 ВЫСОКИЙ | `[ ]` | Все методы resolveFightCaptchaUrl, showFightCaptchaDialogOnce (~400 строк) | Сложность: 5/10 |
| **4** | **Полный аудит VCode через SessionManager** | 🔴 КРИТИЧНЫЙ | `[ ]` | AGENTS.MD Rule 5 — требует проверки всех VCode использований | Сложность: 8/10 |
| **5** | **Выделение утилит URL/парсинга в UrlParseUtils** | 🟡 СРЕДНИЙ | `[ ]` | getUrlParam, appendOrReplaceUrlParam, extractJsArrayTokens, splitJsTopLevelCsv (~200 строк) | Сложность: 4/10 |
| **6** | **Консолидация дедупликационных логик** | 🟡 СРЕДНИЙ | `[ ]` | 10+ полей lastXxxBroadcastKey, lastXxxAtMs, lastXxxKey — выделить в отдельный DedupManager | Сложность: 5/10 |
| **7** | **Унификация HTML-генерации** | 🟡 СРЕДНИЙ | `[ ]` | buildWaitForTurnAutoRefreshHtml, buildInPlaceFightAutoRefreshHtml, buildDelayedRedirectHtml — повторяющийся код | Сложность: 4/10 |
| **8** | **Зачистка bridge'ов и переход к прямым вызовам** | 🟡 СРЕДНИЙ | `[ ]` | FIGHT_AUTO_HOST, FAST_ACTION_HOST, TREASURE_DIG_HOST — избыточная abstraction | Сложность: 6/10 |
| **9** | **Добавить FileLogger в критичные цепочки** | 🟠 ВЫСОКИЙ | `[ ]` | AGENTS.MD Rule 6 — dual logging (Log.i + FileLogger) | Сложность: 6/10 |
| **10** | **Проверить Event-Driven обработку боя** | 🔴 КРИТИЧНЫЙ | `[ ]` | AGENTS.MD Rule 8 — является ли time отклика <100ms? | Сложность: 8/10 |

---

## 📈 План рефакторинга (фазы)

### Фаза 1: Критичные аудиты (1-2 недели)
1. Полный аудит VCode в MainPhp (alle getValidVCodeForAction)
2. Проверка event-driven обработки боя (<100ms)
3. Проверка UTF-8 кодировки и файловых операций

### Фаза 2: Выделение боевой логики (2-3 недели)
1. Создать FightProcessor.java
2. Перенести mainPhpFight + все helper'ы
3. Проверить что все bridge'ы работают
4. Добавить FileLogger dual-logging

### Фаза 3: Выделение инвентаря (1-2 недели)
1. Создать InventoryProcessor.java
2. Перенести mainPhpInv + быстрые действия
3. Унифицировать работу с кэшем

### Фаза 4: Утилиты и очистка (1-2 недели)
1. Выделить UrlParseUtils
2. Выделить CaptchaHandler
3. Выделить HtmlGenerationUtils
4. Зачистить мусор и дублирование

---

## 🎯 Пример минимального рефакторинга

**Текущее состояние:**
```
MainPhp.java (6229 строк)
├─ mainPhpFight (~1500 строк)
├─ mainPhpInv (~900 строк)
├─ Быстрые действия (~800 строк)
├─ Парсинг URL (~300 строк)
├─ Утилиты капчи (~400 строк)
├─ Дедуп логика (~200 строк)
├─ Bridge'ы (~400 строк)
└─ Остаток (~500 строк)
```

**Идеальное состояние после рефакторинга:**
```
MainPhp.java (~1000 строк)
├─ process() entry-point
├─ delegateToFightProcessor()
├─ delegateToInventoryProcessor()
├─ delegateToCaptchaHandler()
├─ Простые вспомогательные методы
└─ Инициализация bridge'ов

FightProcessor.java (~1500 строк) — вся боевая логика
InventoryProcessor.java (~900 строк) — инвентарь + быстрые действия
CaptchaHandler.java (~400 строк) — капча
UrlParseUtils.java (~200 строк) — парсинг URL
HtmlGenerationUtils.java (~300 строк) — построение HTML
```

---

## 📋 Чек-лист критичных проверок

### ChatStats.java
- [ ] Все FileOutputStream используют UTF-8 charset
- [ ] Все parseXXX методы используют ParseUtils безопасно
- [ ] synchronized блоки не содержат блокирующих операций (файловый I/O)
- [ ] Логирование через FileLogger добавлено

### MainPhp.java
- [ ] Аудит: все VCode получены через SessionManager (не AppVars.VCode)
- [ ] Аудит: все null-результаты getValidVCodeForAction обработаны
- [ ] Event-driven обработка боя — измерено время отклика <100ms
- [ ] markFightInProgress() вызывается ПЕРЕД new LezFight()
- [ ] Все критичные цепочки логируются в FileLogger (Rule 6)
- [ ] Нет дублирования логики в bridge'ах

---

## 💡 Выводы

| Метрика | Статус | Рекомендация |
|---------|--------|--------------|
| **ChatStats размер** | ✅ OK (636 строк) | Оставить как есть, добавить логирование |
| **MainPhp размер** | 🔴 КРИТИЧНЫЙ (6229 строк) | **ТРЕБУЕТ СРОЧНОГО РЕФАКТОРИНГА** |
| **Модульность MainPhp** | 🔴 КРИТИЧНЫЙ (нарушает Rule 6) | Разбить на 4-5 специализированных классов |
| **VCode управление** | 🔴 КРИТИЧНЫЙ (Rule 5) | Полный аудит перед любыми коммитами |
| **Event-driven реакция** | ? НЕИЗВЕСТНО | Требуется метрика времени отклика |
| **Файловое логирование** | 🟡 НЕПОЛНО | Добавить FileLogger в критичные цепочки |

---

**Автор анализа:** GitHub Copilot  
**Классификация:** КРИТИЧНЫЙ ПРОЕКТ РЕФАКТОРИНГА
