# Задача: динамическая карта ANClient при удержании и перетаскивании

## Контекст

Нужно перенести поведение из `app2`: при удержании карты и перетаскивании в сторону карта должна сдвигаться и дорисовывать соседние клетки без отдельного экрана и без нового HTTP-контура.

## Найденный существующий контур

- `ANClient\PostFilter\MapJs.cs` отдает серверному `/js/map.js` встроенный ресурс `Resources.map`.
- `Resources.map` берется из `ANClient\map.js` через `ANClient\Properties\Resources.resx`.
- В `ANClient\map.js` уже есть `loadMap(dir)`, `freeMap(dir)`, `loaded_left/right/top/bottom`, значит править нужно текущий JS-контур карты, а не создавать параллельный renderer.
- В `app2\src\main\assets\js\map.js` эталонная реализация использует `AnInstallDynamicMapPan`, `AnEnsureDynamicMapCoverage`, `an_map_pan_*` и bridge `TraceMapRuntime`.

## План реализации

- [x] Найти текущую точку отдачи desktop map.js.
- [x] Сравнить с app2 dynamic pan implementation.
- [x] Добавить в `ANClient\map.js` pan state, обработчики mouse/touch drag и suppression click после drag.
- [x] Исправить существующий `loadMap()` для правильной проверки координат дорисовываемых клеток.
- [x] Добавить bridge-лог `TraceMapRuntime` в `ANClient\ScriptManager.cs`.
- [x] Собрать `ANClient.csproj` и проверить кодировку/дифф.
- [x] Исправить клики по дорисованным клеткам: добавить fallback на `world_host`, который вычисляет клетку по координатам с учётом `cur_margin_*` и `loaded_*`.

## Риски и проверки

- Обычный клик по клетке не должен отправлять движение, если это был drag.
- `moving_status == 1` должен блокировать ручной pan, чтобы не конфликтовать с серверным движением.
- Динамическая дорисовка должна использовать существующие `loaded_*` границы и `loadMap(dir)`.
- Overlay `world_cont2` не должен блокировать запуск навигатора по дорисованным клеткам; если он перехватил клик, координата восстанавливается через `AnMapCoordsFromPoint()`.
- `ABClient/` не изменяется.
