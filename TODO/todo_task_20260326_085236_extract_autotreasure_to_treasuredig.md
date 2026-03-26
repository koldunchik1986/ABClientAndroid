# Задача: Декомпозиция MainPhp — вынести Auto-Клад в TreasureDig.java

## Контекст
- Запрос: разгрузить `MainPhp.java`, вынести весь функционал `Авто-Клад` в отдельный файл.
- Цель: упростить отладку и сопровождение без изменения поведения.

## План
- [x] Создать `TreasureDig.java` в `postfilter` и перенести туда логику Auto-Клада.
- [x] Добавить подробные комментарии по правилам и зависимостям.
- [x] В `MainPhp.java` оставить только вызов нового модуля.
- [x] Реализовать bridge-адаптер из `MainPhp` в `TreasureDig` без дублирования helper-логики.
- [x] Удалить из `MainPhp.java` старые методы/константы Auto-Клада.
- [x] Проверить сборку `:app:compileDebugJavaWithJavac`.

## Реализация
- [x] Добавлен файл `app/src/main/java/ru/neverlands/abclient/postfilter/TreasureDig.java`.
- [x] Вызов Auto-Клада в `MainPhp.process(...)` переведен на `TreasureDig.maybeStopAutoTreasureOnDig(...)`.
- [x] Добавлен `TREASURE_DIG_HOST` (bridge) для использования существующих helper-методов MainPhp.
- [x] Удалены из `MainPhp.java` методы и константы, относящиеся к Auto-Кладу:
- [x] `maybeStopAutoTreasureOnDig(...)`
- [x] `maybeHandleAutoTreasureDigFlow(...)`
- [x] `continueAutoTreasureDigPreparation(...)`
- [x] `markAutoTreasureShovelReady(...)`
- [x] `buildAutoTreasureDigOpenInventoryRedirect(...)`
- [x] `buildAutoTreasureDigReturnToMapHtml(...)`
- [x] `buildAutoTreasureDigClickHtml(...)`
- [x] `resolveTreasureShovelWearLink(...)`
- [x] `isTreasureShovelEquipped(...)`
- [x] `isTreasureShovelOptionMatches(...)`
- [x] `isTreasureShovelName(...)`
- [x] `normalizeTreasureShovelOption(...)`
- [x] `notifyTreasureFoundOnCurrentCell(...)`
- [x] `playTreasureFoundSignal(...)`

## Статус
- Реализовано.
