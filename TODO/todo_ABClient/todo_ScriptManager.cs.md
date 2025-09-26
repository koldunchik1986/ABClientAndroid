# План портирования ScriptManager.cs

Файл `ScriptManager.cs` - это критически важный класс, который служит мостом между C# и JavaScript, работающим в `WebBrowser`.

## Функциональность в C#

*   **Назначение**: Предоставить JavaScript-коду, исполняемому на веб-страницах, доступ к методам и данным нативного C#-приложения. Атрибут `[ComVisible(true)]` делает публичные методы этого класса доступными из JS через `window.external`.
*   **API для JavaScript**: Класс предоставляет десятки методов, которые можно сгруппировать по назначению:
    *   **Управление UI**: `ChangeChatSize`, `ShowMiniMap`.
    *   **Запуск действий**: `MoveTo`, `AutoBoi`, `HerbCut`, `FastAttack`.
    *   **Получение данных**: `IsAutoFish`, `GetClassIdOfContact`, `GetMapScale`.
    *   **Уведомление C# о событиях**: `ChatUpdated`, `ResetLastBoiTimer`.
    *   **Генерация HTML**: `CellDivText`, `InfoToolTip`.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. Поиск по `addJavascriptInterface` не дал результатов. Это означает, что в данный момент `WebView` в Android-приложении не имеет моста для вызова Java-кода из JavaScript.

## Решение для портирования на Android

Необходимо создать аналогичный класс-мост в Java и зарегистрировать его в `WebView` с помощью `addJavascriptInterface`. Это позволит вызывать публичные методы Java-класса из JavaScript.

## План реализации

- [ ] **Создать файл `ScriptManager.java`** в пакете `ru.neverlands.abclient.bridge` (создать пакет, если его нет).

- [ ] **Реализовать класс `ScriptManager.java`**:
    - [ ] Класс не должен быть статическим. Нужно будет создать его экземпляр.
    - [ ] Перенести все публичные методы из `ScriptManager.cs` в `ScriptManager.java`.
    - [ ] Каждый метод должен быть аннотирован `@JavascriptInterface`.
    - [ ] Внутри каждого метода нужно будет вызывать соответствующую логику в Android-приложении. Например, метод `MoveTo(String dest)` в `ScriptManager.java` будет вызывать метод `moveTo(String dest)` в `MainActivity` или соответствующем менеджере.
    - [ ] Логику, которая в C# была в `FormMain`, нужно будет перенести в `MainActivity` или другие подходящие классы.

- [ ] **Зарегистрировать интерфейс в `WebView`**:
    - [ ] В `MainActivity.java`, где настраивается основной `WebView`, добавить строку:
        ```java
        // webView.getSettings().setJavaScriptEnabled(true); <-- это уже есть
        webView.addJavascriptInterface(new ScriptManager(this), "AndroidBridge");
        ```
        *(Имя `AndroidBridge` будет использоваться в JS вместо `window.external`)*.

- [ ] **Адаптировать JavaScript-код**:
    - [ ] Весь JavaScript-код, который вызывает `window.external.SomeMethod()`, нужно будет найти и изменить на `AndroidBridge.SomeMethod()`.
    - [ ] Это изменение нужно будет делать в `PostFilter`-классах, которые модифицируют HTML и JS перед отображением.

- [ ] **Обновить `todo_ABClient.md`**, отметив `ScriptManager.cs` как проанализированный.
