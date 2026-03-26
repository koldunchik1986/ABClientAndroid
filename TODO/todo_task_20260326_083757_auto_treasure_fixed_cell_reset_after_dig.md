# Задача: Авто-Клад — сброс режима "Клад точно здесь" после выкопки

## Контекст
- Источник: `Logs/logcat_runtime_20260326_01_klad.txt`.
- Симптом: после выкопки режим фиксированной клетки (`Клад точно здесь`) оставался активным и мог продолжать цикл по той же клетке.
- Ожидание: после подтвержденного результата копки автоматически отключать fixed-cell режим и продолжать обычный поиск клада.

## План
- [x] Проверить текущую точку детекции результата копки (`MapActAjaxPhp`).
- [x] Добавить явный детектор финального результата копки.
- [x] При финальном результате:
- [x] выключить `AutoTreasureFixedCellEnabled`;
- [x] очистить `AutoTreasureFixedCellRegNum`;
- [x] вывести уведомление в чат о сбросе fixed-cell режима.
- [x] Сохранить поведение без отключения `Авто-Клад`.

## Реализация
- [x] Обновлен `app/src/main/java/ru/neverlands/abclient/postfilter/MapActAjaxPhp.java`:
- [x] добавлен `isTreasureDigCompletedMessage(...)`;
- [x] добавлен вызов `disableFixedTreasureCellAfterDig()` в `process(...)`;
- [x] добавлен `disableFixedTreasureCellAfterDig()` со сбросом настроек через `AutoFunctionsManager`.

## Статус
- Реализовано.
