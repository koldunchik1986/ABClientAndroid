# ЭТАП 2: Реализация правильного цикла авто-рыбалки

**Цель:** Внедрить 600+ строк улучшенных методов для FishAjaxPhp.java

**Дата начала:** 2025-01-XX
**Статус:** IN PROGRESS

## Список методов для внедрения

### Основные методы:

1. [ ] `executeFishingCycleCore()` - Главный оркестратор цикла рыбалки
   - Загружает озеро из ContentLakeHtml
   - Парсит vcode, lakeid, act
   - Вызывает selectBaitFromLakeHtmlAndroid()
   - Отправляет act=1 и act=2 с корректным vcode

2. [ ] `mainPhpAutoFishPrepareFromLakeAndroid()` - Парсинг озера
   - Извлекает vcode из формы озера
   - Извлекает lakeid (id озера)
   - Извлекает list поля для act (параметры приманок)
   - Возвращает LakeParseResult

3. [ ] `selectBaitFromLakeHtmlAndroid()` - Выбор приманки
   - Проверяет наличие приманок на складе
   - Выбирает приманку с максимальным количеством
   - Верифицирует доступность конкретного озера
   - Возвращает BaitSelectionResult (количество, идентификатор)

4. [ ] `scheduleNextFishingCycleAttempt()` - Планирование следующей попытки
   - Вычисляет задержку до следующего цикла
   - Использует ExponentialBackoff при ошибках
   - Планирует переподключение к озеру

5. [ ] `getMainActivityOrNull()` - Получение текущей Activity
   - Безопасно получает ссылку на MainActivity
   - Обрабатывает случаи когда Activity недоступна

### Вспомогательные классы (DTO):

6. [ ] `class LakeParseResult` - Результаты парсинга озера
   - String vcode
   - int lakeid
   - List<String> act_list_fields

7. [ ] `class BaitSelectionResult` - Результаты выбора приманки
   - int bait_id
   - int available_count
   - boolean isAvailable
   - String reason (если ошибка)

8. [ ] `class FishAct1State` - Парсинный статус act=1
   - int lakeid
   - int wounded
   - String vcode
   - boolean captchaRequired

9. [ ] `class FishBaitInfo` - Информация о приманке
   - int id
   - String name
   - int stock_count
   - List<Integer> available_at_lakes

## Расположение вставки

**Файл:** `app/src/main/java/ru/neverlands/abclient/postfilter/FishAjaxPhp.java`

**Вставляется:** Перед методом `processInternal()` (рядом с другими методами processFish*)

**Примерная строка:** ~200-300 (перед основной обработкой)

## Проверочные точки

- [ ] Код скомпилируется без ошибок
- [ ] Нет конфликтов с существующими методами
- [ ] Методы корректно достают данные из ContentLakeHtml
- [ ] vcode используется из озера, а не из act=1 response

## Примечания

- Все методы следуют соглашениям именования Android (camelCase)
- Используются Android-специфичные компоненты (Handler, Looper)
- Логирование через `Log.d(TAG, "AUTO_FISH_TRACE ...")`
- Обработка null-значений везде

---

**Запустить ЭТАП 2:** Запросить у Copilot внедрение методов
