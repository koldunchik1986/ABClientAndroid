# Портирование `BuildingJs.cs` в `BuildingJs.java`

## Анализ `BuildingJs.cs` (ПК версия)

Файл `BuildingJs.cs` в оригинальном C# клиенте отвечает за обработку JavaScript-содержимого, загружаемого из `/js/building` (предположительно, `building.js`).

**Ключевая особенность:** Метод `BuildingJs(byte[] array)` в C# версии выполняет условную модификацию входного JavaScript-кода.

Если флаг `AppVars.Profile.ChatKeepMoving` установлен в `true`, метод выполняет замену строки `parent.clr_chat();` на пустую строку. Это, вероятно, отключает очистку чата в контексте страниц, связанных со зданиями.

## Портирование в `BuildingJs.java` (Android версия)

Логика из `BuildingJs.cs` была перенесена в статический метод `process` класса `BuildingJs.java` в пакете `ru.neverlands.abclient.postfilter`.

### Особенности реализации в Java:

*   **Кодировка:** Для преобразования массива байт в строку и обратно используется `Russian.Codepage.getString()` и `Russian.Codepage.getBytes()`, обеспечивая корректную работу с кодировкой `windows-1251`.
*   **Условная замена:** Логика замены `parent.clr_chat();` на пустую строку реализована с проверкой `AppVars.Profile.ChatKeepMoving`. Поле `ChatKeepMoving` уже добавлено в `UserConfig.java`.

### Интеграция с `Filter.java`:

Метод `processBuildingJs` в `ru.neverlands.abclient.postfilter.Filter.java` был изменен для вызова `BuildingJs.process(array)`, что позволяет централизованному фильтру делегировать обработку `/js/building` специализированному классу.

## Вывод

Портирование `BuildingJs.cs` в `BuildingJs.java` обеспечивает корректную обработку и условную модификацию JavaScript-файла, связанного со зданиями, как это реализовано в оригинальном C# клиенте. Это позволит управлять поведением очистки чата в этом контексте.