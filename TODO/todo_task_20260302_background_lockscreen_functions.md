# TODO по функциям: lockscreen/background (Android)

## Назначение
- [ ] Разбить задачу фоновой остановки авто-режимов на конкретные функции.
- [ ] Для каждой функции зафиксировать: текущую роль, зависимости, риск при `screen off`, план диагностики и план правки.

## Сводная таблица

| Компонент | Детальный TODO | Статус |
| --- | --- | --- |
| `MainActivity.java` | `TODO/Debug/todo_MainActivity_lockscreen_functions_20260302.md` | `[x]` Проанализирован (статически) |
| `AutoFunctionsManager.java` | `TODO/Debug/todo_AutoFunctionsManager_lockscreen_functions_20260302.md` | `[x]` Проанализирован (статически) |
| `RoomManager.java` | `TODO/Debug/todo_RoomManager_lockscreen_functions_20260302.md` | `[x]` Проанализирован (статически) |
| `WebAppInterface.java` | `TODO/Debug/todo_WebAppInterface_lockscreen_functions_20260302.md` | `[x]` Проанализирован (статически) |
| `FightViewModel.java` | `TODO/Debug/todo_FightViewModel_lockscreen_functions_20260302.md` | `[x]` Проанализирован (статически) |
| `AndroidManifest.xml` + runtime power constraints | `TODO/Debug/todo_BackgroundInfra_lockscreen_20260302.md` | `[x]` Проанализирован (статически) |

## Порядок выполнения
- [x] 1) `MainActivity` (lifecycle + polling).
- [x] 2) `AutoFunctionsManager` (флаги/переключатели и запуск `location tracking`).
- [x] 3) `RoomManager` (триггер авто-нападения при room ticks).
- [x] 4) `WebAppInterface` + `FightViewModel` (мост JS/UI и боевые submit-цепочки).
- [x] 5) Infra (`Manifest`, foreground-service/wakelock strategy).
