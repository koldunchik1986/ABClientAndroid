# План портирования ChRoomPhp.cs

Файл `ChRoomPhp.cs` является частью системы пост-фильтрации HTTP-ответов. Он обрабатывает HTML-страницу `ch.php?lo=1`, которая отображает список игроков в текущей игровой локации (комнате).

## Функциональность в C#

### Назначение
- Вызывает `RoomManager.Process(html)` для обработки HTML-кода страницы комнаты.
- Возвращает модифицированный HTML-код страницы комнаты.

### Логика
- Метод `Process(string address, byte[] array)` проверяет, что адрес начинается с `http://www.neverlands.ru/ch.php?lo=`.
- Если условие выполнено, вызывается статический метод `RoomManager.Process(html)`.
- Результат возвращается как `byte[]`.

### Взаимодействие
- Напрямую зависит от `RoomManager.cs`.
- Используется в общей цепочке фильтрации `Filter.cs`.

## Решение для портирования на Android

Функционал должен быть реализован в классе `ru.neverlands.abclient.postfilter.ChRoomPhp`.

## План реализации

- [x] Создать класс `ChRoomPhp.java`.
- [ ] Реализовать метод `public static byte[] process(byte[] array)`, который:
    - Декодирует `array` в строку через `Russian.getString()`.
    - Вызывает `RoomManager.process(html)`.
    - Кодирует результат обратно через `Russian.getBytes()`.
- [ ] Убедиться, что `Filter.java` вызывает `ChRoomPhp.process()` для адресов `ch.php?lo=`.