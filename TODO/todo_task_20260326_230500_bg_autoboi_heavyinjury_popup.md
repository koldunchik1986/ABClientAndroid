# Задача: фоновой `Авто-Бой` + реакция на popup тяжелой травмы

## Контекст
- Лог: `Logs/logcat_runtime_20260326_09.txt`.
- Симптом 1: в части сценариев `AutoModeForegroundService` слишком агрессивно пропускает `autoTurn/probe` в ветке `foreground-likely`.
- Симптом 2: server popup «тяжёлая травма» попадал в чат, но не запускал приоритетное self-лечение.

## План
- [x] Проверить текущую ветку `foreground-likely skip` в `AutoModeForegroundService`.
- [x] Проверить, где обрабатывается server popup из `MapJs` (`WebAppInterface.PostServerPopupToChat`).
- [x] Внести точечный фикс в существующую ветку skip (без новой параллельной логики).
- [x] Добавить реакцию на popup тяжелой травмы через существующий контур `MainPhp`/`AutoCure`.
- [x] Прогнать компиляцию `:app:compileDebugJavaWithJavac`.

## Реализация
- [x] `AutoModeForegroundService`:
  - skip в `foreground-likely` теперь блокирует тик только в пределах idle-интервала;
  - при истечении idle-интервала допускается `autoTurn/probe`, даже если UI помечен как foreground-likely.
- [x] `MainPhp`:
  - добавлен единый обработчик `onServerPopupMessage(...)` для popup-сигналов;
  - добавлен общий детектор тяжелой травмы и постановка self-cure (`CureNeed/CureNick/CureTravm=3`) при включенном авто-лечении;
  - включается `CurePauseNonCombatAutoFunctions` для приоритета лечения.
- [x] `WebAppInterface`:
  - `PostServerPopupToChat(...)` теперь перед отправкой в чат прокидывает popup в `MainPhp.onServerPopupMessage(...)`.

## Проверка
- [ ] Нужен новый runtime-лог с устройством в фоне/lockscreen после фикса.
- [ ] Нужен runtime-лог со сценарием popup «У Вас тяжёлая травма» и проверкой запуска self-лечения.
