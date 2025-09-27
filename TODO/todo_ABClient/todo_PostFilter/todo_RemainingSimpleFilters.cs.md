
### 1. План портирования оставшихся простых фильтров

Этот документ описывает план портирования группы оставшихся, в основном простых, фильтров из папки `PostFilter`.

### 2. Анализ функциональности

- **`ArenaJs.cs`:** Заменяет скрипт на локальный ресурс `Resources.arena_v04` и, если `ChatKeepMoving`=true, удаляет `top.clr_chat()`.
- **`BuildingJs.cs`:** Если `ChatKeepMoving`=true, удаляет `parent.clr_chat()`.
- **`CastleJs.cs`, `OutpostJs.cs`, `TarenaJs.cs`, `TowerJs.cs`:** Добавляет в начало скрипта содержимое `json2.js`.
- **`ChZero.cs`, `LogsJs.cs`, `PinfonewJs.cs`, `SlotsJs.cs`:** Являются заглушками. Ничего не делают.
- **`CounterJs.cs`:** Полностью заменяет скрипт на пустую функцию `function counterview(referr){}`.
- **`ForumTopicJs.cs`:** Если `LightForum`=true, вырезает из скрипта теги `<img>` с аватарами.
- **`HpmpJs.cs`:** Полностью заменяет скрипт на кастомную реализацию таймера и отображения регенерации HP/MP.
- **`NlPinfo.cs`:** Внедряет хук `window.external.InfoToolTip` для кастомизации всплывающих подсказок.
- **`PvJs.cs`:** Выполняет простую замену `'%clan% '` на `'%clan%'`.
- **`ShopJs.cs`:** Внедряет хук `window.external.BulkSellOldArg` для реализации массовой продажи.
- **`SvitokJs.cs`:** Внедряет хук `window.external.TraceDrinkPotion` для отслеживания использования свитков.
- **`TopJs.cs`:** Заменяет функцию, чтобы она возвращала пустую строку `return '';`.
- **`json2.js`:** Является файлом библиотеки, который нужно просто скопировать.

### 3. План реализации

1.  **Скопировать `json2.js`:**
    - [ ] Скопировать `ABClient\PostFilter\json2.js` в `app\src\main\assets\js\json2.js`.
2.  **Создать Java-классы фильтров:**
    - [ ] Для каждого `.cs` файла создать соответствующий `.java` класс в `ru.neverlands.abclient.postfilter`.
    - [ ] Реализовать в каждом классе статический метод `process`, портировав простую логику замен или возврата локальных ресурсов.
3.  **Обновить `WebAppInterface.java`:**
    - [ ] Добавить методы-заглушки: `InfoToolTip`, `BulkSellOldArg1`, `BulkSellOldArg2`, `TraceDrinkPotion`.
4.  **Обновить `Filter.java`:**
    - [ ] Добавить `if-else if` блоки для вызова `process` методов всех новых фильтров.
5.  **Обновить `todo_PostFilter.md`:**
    - [ ] Пометить все перечисленные файлы как `[x]` проанализированные.
