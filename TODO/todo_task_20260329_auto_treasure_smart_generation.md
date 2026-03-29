# Задача: Авто-Клад — «Умная система генерации»

## Цель
- Добавить в настройки `Авто-Клада` новую галочку: `Умная система генерации`.
- При включенной галочке не тратить Блаж на повторную проверку недавно посещённых клеток.
- Сохранить существующий контур поиска (`MapAjax.findNextDestForBox`) без дублирования логики.

## Реализация
- [x] В `AutoFunctionsManager` добавлен новый pref:
  - `PREF_AUTO_TREASURE_SMART_GENERATION`
  - `isAutoTreasureSmartGenerationEnabled()`
  - `setAutoTreasureSmartGenerationEnabled(boolean)`
- [x] В `QuickButtonsPanel.showAutoTreasureSettingsDialog()` добавлен чекбокс
  `Умная система генерации` с сохранением в `AutoFunctionsManager`.
- [x] В `MapAjax.findBaseSearchBoxDestination(...)` добавлен smart-guard:
  - для fallback-цели (`oldest visited`) проверяется минимальный возраст метки;
  - если клетка «слишком свежая», fallback не выбирается и шаг не выполняется;
  - порог: `2ч + 50м` (`SEARCH_BOX_SMART_RECHECK_MIN_AGE_MS`), как анти-спам повторов.
- [x] Добавлено trace-логирование `AUTO_SEARCH_BOX_TRACE smart-generation: ...`
  для отладки решений выбора/пропуска fallback.

## Проверки
- [ ] При выключенной галочке поведение `Авто-Клада` как раньше.
- [ ] При включенной галочке нет повторных шагов по «свежим» уже проверенным клеткам.
- [ ] Лог показывает `smart-generation: skip recent fallback` при блокировке повторного шага.
- [ ] Сборка проходит: `:app:compileDebugJavaWithJavac`.

## Примечание
- Логика не создаёт новый контур движения: только дорабатывает существующий fallback,
  чтобы исключить нецелесообразные повторы при новой модели генерации кладов.
