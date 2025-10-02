# План портирования FormMain.cs (часть, связанная с Room)

Файл `FormMain.cs` содержит методы для обновления пользовательского интерфейса на основе данных, полученных от `RoomManager`.

## Функциональность в C#

### Назначение
Обновление UI-элементов, связанных с комнатой: выпадающие списки ПВ и травмированных.

### Ключевые методы
- `UpdateRoom(ToolStripItem[] tsmi, string trtext, ToolStripItem[] tstr)`:
  - Очищает `dropdownPv.DropDownItems` и `dropdownTravm.DropDownItems`.
  - Устанавливает текст `dropdownPv.Text = "ПВ: " + tsmi.Length`.
  - Устанавливает текст `dropdownTravm.Text = trtext`.
  - Добавляет элементы в выпадающие списки.
  - Включает/отключает элементы в зависимости от наличия данных.

### Взаимодействие
- Вызывается из `RoomManager.FilterProcRoom` через `AppVars.MainForm.BeginInvoke`.

## Решение для портирования на Android

Эта функциональность должна быть реализована в `MainActivity.java`.

## План реализации

- [x] Создать в `MainActivity.java` метод `public void updateRoom(List<RoomManager.MenuItem> pvList, String travmText, List<RoomManager.MenuItem> travmList)`.
- [x] Реализовать логику обновления `Spinner` или другого UI-компонента.
- [x] Убедиться, что `RoomManager.java` вызывает этот метод через `runOnUiThread`.