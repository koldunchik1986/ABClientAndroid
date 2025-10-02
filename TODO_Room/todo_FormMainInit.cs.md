# План портирования FormMainInit.cs (часть, связанная с Room)

Файл `FormMainInit.cs` содержит код инициализации и завершения работы, связанный с `RoomManager`.

## Функциональность в C#

### Назначение
- Запуск и остановка фонового мониторинга комнаты.

### Ключевые методы
- `RestoreElements()`: Вызывает `RoomManager.StartTracing();` при запуске приложения.
- `CloseForm()`: Вызывает `RoomManager.StopTracing();` при закрытии приложения.

### Взаимодействие
- Является точкой входа для жизненного цикла `RoomManager`.

## Решение для портирования на Android

Эта логика должна быть перенесена в жизненный цикл `MainActivity`.

## План реализации

- [x] Вызывать `RoomManager.startTracing(this)` в `MainActivity.onCreate()` или `onPageFinished` для `main.php`.
- [x] Вызывать `RoomManager.stopTracing()` в `MainActivity.onDestroy()`.