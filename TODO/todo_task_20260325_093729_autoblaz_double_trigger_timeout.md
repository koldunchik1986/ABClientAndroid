# Задача: Авто-Клад / Блаж — двойной запуск и нестабильное применение

## Контекст
- Лог: `Logs/logcat_runtime_20260325_05.txt`.
- Симптом: при `Авто-Клад` Блаж иногда стартует 2 раза подряд, первый запуск часто падает на `inventory-fast-transition-timeout`.

## Диагностика
- [x] Проверить логи по `AUTO_SEARCH_BOX_TRACE`, `AUTO_BLAZ_DECISION`, `processMainPhpFast`.
- [x] Подтвердить цепочку: `too-tired` -> `fastStart(Блаж)` -> `retry 1..6` -> `inventory-fast-transition-timeout` -> повторный `too-tired` триггер.
- [x] Проверить текущие гейты от дублей в `MapAjax` (ветка `too-tired` обходила общий cooldown-гейт).

## План фикса
- [x] Убрать отдельный прямой trigger из ветки `too-tired`, перевести её на единый `maybeTriggerAutoDrinkBlazOnThreshold(...)`.
- [x] Увеличить лимит transitional-ретраев инвентаря (`FAST_INV_TRANSITION_MAX_RETRIES`) для медленных ответов сервера.
- [x] Проверить компиляцию `:app:compileDebugJavaWithJavac`.

## Результат
- [x] Код обновлён.
- [x] Сборка прошла.
- [x] Готово к проверке на устройстве.
