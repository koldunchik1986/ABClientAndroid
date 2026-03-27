# Портирование `toolStripStatusLabel1` (Таймеры) в Android

## Цель
- 1:1 перенести базовый контур ПК-версии `AppTimer/AppTimerManager/FormMainTimers/FormNewTimer` в Android.
- Добавить QuickButton **«Таймеры»** с отдельным окном списка, редактором и настройками long-press.

## План и статус
- [x] Проанализировать C#-источники (`AppTimer.cs`, `AppTimerManager.cs`, `FormMainTimers.cs`, `FormNewTimer.cs`)
- [x] Проверить Android-код и точки интеграции (`QuickButtonsPanel`, `MainActivity`, `AutoModeForegroundService`)
- [x] Реализовать модель `AppTimer` (порт полей + формат строки `ToString`)
- [x] Реализовать `AppTimerManager` (хранение, сортировка, выполнение due-таймеров, persistence)
- [x] Добавить QuickActionType `TIMERS` и иконку `ic_timer`
- [x] Добавить UI списка таймеров (выбор строки + мини-кнопки изменить/удалить)
- [x] Добавить редактор таймера (простой/зелье/перемещение/комплект)
- [x] Добавить long-press настройки таймеров (звук при срабатывании)
- [x] Подключить выполнение таймеров в `MainActivity` и `AutoModeForegroundService`
- [x] Прогнать компиляцию (`:app:compileDebugJavaWithJavac`)
- [ ] Smoke-проверка на устройстве (назначение кнопки, добавление/редактирование/удаление таймера, срабатывание)

## Примечания по совместимости
- Таймеры сохраняются по профилю (`UserNick`) в `SharedPreferences`.
- Действие «зелье» запускает `FastActionManager.fastStart(...)`.
- Действие «перемещение» запускает `AutoFunctionsManager.startAutoMoving(...)`.
- Действие «комплект» ставит `AppVars.WearComplect` и делает reload `main.php?go=inf`.
