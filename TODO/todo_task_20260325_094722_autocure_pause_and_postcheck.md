# Задача: Авто-Клад + Авто-Лечение (Paracelsus) — пауза навигации и пост-проверка pinfo

## Контекст
- Лог: `Logs/logcat_runtime_20260325_06.txt`.
- Симптом 1: при постановке цели авто-лечения (`Paracelsus`) навигация `Авто-Клад` продолжает идти параллельно.
- Симптом 2: после лечения может быть повторная попытка, даже если у цели уже нет травм.

## Диагностика
- [x] Подтвердить в логе: `RoomManager [AUTO_CURE_TRACE] queued target` и параллельные `map_ajax/AUTO_SEARCH_BOX` шаги.
- [x] Найти точку постановки лечения: `RoomManager.enqueueRoomAutoCureTarget(...)`.
- [x] Найти точку submit лечения: `MainPhp.mainPhpExternalRequestedCureStep(...)`.
- [x] Проверить кэш pinfo-травм: `RoomManager.autoCureRoomPinfoCache` (TTL 30s).

## План фикса
- [x] Добавить runtime-флаг паузы небоевых авто-функций во время внешнего авто-лечения.
- [x] Включать паузу при `enqueue` цели лечения и снимать при `clearExternalCureRequest(...)`.
- [x] Применить паузу в `MapAjax` и ветках `AUTO_SEARCH_BOX` в `MainPhp`.
- [x] Добавить post-submit pinfo-проверку вылеченного ника с обновлением кэша травм.
- [x] Вызывать post-submit проверку из `MainPhp.mainPhpExternalRequestedCureStep(...)`.
- [x] Проверить сборку `:app:compileDebugJavaWithJavac`.

## Результат
- [x] Код обновлён.
- [x] Сборка успешна.
- [x] Готово к проверке на устройстве.
