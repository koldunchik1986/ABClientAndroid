# План портирования `TInvUd.cs`

## 1) Назначение файла в ПК-версии

`ABClient/TInvUd.cs` реализует класс `ParsedDressed`, который:
- парсит экипировку персонажа из HTML (`slots_inv(...)`/`slots_pla(...)`);
- определяет, что надето в обеих руках;
- проверяет соответствие экипировки профилю (рыбалка/авто-охота);
- обновляет runtime-состояние `AppVars` для последующих шагов в `MainPhpWear.cs`/`MainPhp.cs`.

Класс используется как низкоуровневый «датчик экипировки» для авто-функций.

## 2) Функциональность в C# (детально)

### 2.1 Конструктор `ParsedDressed(string html)`
- Инициализирует флаги и очищает `AppVars.AutoFishHand*`.
- Сначала пытается распарсить `slots_inv(...)`.
- Если `slots_inv(...)` отсутствует — fallback на `slots_pla(...)`.
- Извлекает:
  - названия предметов в руках (`Hand1`, `Hand2`);
  - пустой ли слот (`Empty1`, `Empty2`);
  - долговечность предмета (`cur/max`) в `dlist`;
  - служебные параметры `Wid`/`Vcod` (нужны для `снять/надеть`).
- При успешном разборе ставит `Valid = true`.

### 2.2 `IsWear1()`
- Проверяет, надета ли первая удочка по настройке профиля:
  - `FishAutoWear == false` или `FishHandOne == "нет"` → считается ок.
  - поддерживает `"Любая удочка"` (ищет `удочка`/`спиннинг`).
  - поддерживает выбор конкретного названия.
- Побочный эффект (важно для 1:1): пишет найденный предмет в
  `AppVars.AutoFishHand1` + `AppVars.AutoFishHand1D`.
- Может выставлять `InRightSlot = true`, если нужная удочка найдена во второй руке.
- Удаляет уже использованный элемент из `slist/dlist`, чтобы `IsWear2()` работал по остаточному списку.

### 2.3 `IsWear2()`
- Аналогично `IsWear1()`, но для второй удочки (`FishHandTwo`).
- Пишет результат в `AppVars.AutoFishHand2` + `AppVars.AutoFishHand2D`.

### 2.4 `IsWearKnife()`
- Ищет нож из фиксированного списка:
  - `Малый Разделочный Нож`
  - `Охотничий Нож`
  - `Вороненый Охотничий Нож`
  - `Разделочный Топорик`
  - `Нож Мастера-охотника`
- При `Profile.SkinAuto == true` и смене текущего ножа пишет чат-сообщение о новом ноже.
- Всегда обновляет:
  - `AppVars.AutoSkinHand`
  - `AppVars.AutoSkinHandD`
- Возвращает `true`, если нож найден в руках.

## 3) Зависимости `TInvUd.cs` в C#

- `AppVars.Profile`:
  - `FishAutoWear`, `FishHandOne`, `FishHandTwo`, `SkinAuto`.
- `AppVars`:
  - `AutoFishHand1/2`, `AutoFishHand1D/2D`,
  - `AutoSkinHand`, `AutoSkinHandD`.
- UI:
  - `AppVars.MainForm.WriteChatMsg(...)` (уведомление о смене ножа).
- Потребители результата:
  - `MainPhpWear.cs` (`MainPhpArmedKinfe`, `MainPhpWearKnife`, `MainPhpWearUd`).

## 4) Проверка текущей Android-реализации

- [x] В Android нет аналога класса `ParsedDressed`.
- [x] В `AppVars.java` отсутствуют поля `AutoSkinHand`/`AutoSkinHandD`.
- [x] В `UserConfig.java` отсутствует `SkinAuto`.
- [x] Логика авто-надевания ножа для `AUTO_SKIN` пока не реализована.

## 5) Решение для портирования на Android

Портировать максимально близко к C# (без изменения семантики):
- создать `ParsedDressed.java` и оставить C#-имена полей/методов (`IsWear1/IsWear2/IsWearKnife`) с Java-стилем-обертками при необходимости;
- сохранить побочные эффекты обновления `AppVars`, т.к. на них завязан `MainPhpWear`;
- вынести список ножей в общую константу, чтобы использовать и в `MainPhpWear`.

## 6) План первичной реализации (`TInvUd`-этап)

- [x] Создать `app/src/main/java/ru/neverlands/abclient/model/ParsedDressed.java`.
- [x] Портировать разбор `slots_inv(...)` + fallback `slots_pla(...)`.
- [x] Портировать поля `Valid/Wid/Vcod/Empty1/Empty2/InRightSlot/Hand1/Hand2`.
- [x] Портировать `IsWear1()` 1:1 (включая `InRightSlot` и удаление элементов списка).
- [x] Портировать `IsWear2()` 1:1.
- [x] Портировать `IsWearKnife()` 1:1 + обновление `AppVars.AutoSkinHand*`.
- [x] Добавить недостающие поля в `AppVars.java` и `UserConfig.java`.
- [ ] Подключить класс в обработку инвентаря внутри `MainPhp` (на этапе `MainPhpWear`).

## 7) Статус

- [x] Детальный анализ `TInvUd.cs` выполнен и уточнен.
- [x] Базовый Android-порт `ParsedDressed` создан.
- [ ] Интеграция `ParsedDressed` в `MainPhpWear`/`MainPhp` ещё не выполнена.
