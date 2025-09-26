# План портирования FightJs.cs и логики Автобоя

Этот документ описывает подробный план портирования `FightJs.cs` и связанной с ним логики автобоя. `FightJs.cs` — это интерфейсная часть, которая внедряет элементы управления в страницу боя и связывает их с "мозгом" автобоя в основном коде.

## 1. Архитектурные проблемы и решения

*   **`window.external`**: Весь функционал построен на вызовах `window.external.Method()`. В Android это необходимо заменить на [JavaScript Interface](https://developer.android.com/develop/ui/views/layout/webapps/webview#java-js-interop). Мы создадим класс-мост (например, `WebAppInterface`), который будет доступен в JS под именем `AndroidBridge`.
*   **Монолитная логика**: Логика автобоя в C# разбросана по разным частям кода. При портировании ее следует инкапсулировать в отдельный класс-менеджер, например, `AutoboiManager.java`.
*   **Асинхронность**: Взаимодействие между JS и Java асинхронно. Это нужно учитывать при проектировании.

## 2. Поэтапный план реализации

### Фаза 1: Создание "заглушки" (текущий этап)

Цель — не ломать бой, пока логика автобоя не реализована.

*   **`FightJs.java`**: Создать класс.
*   **`public static byte[] process(byte[] array)`**: В этом методе **просто возвращать `array` без изменений**. Это оставит стандартный интерфейс боя без кастомных кнопок.
*   **`Filter.java`**: Убедиться, что `processFightJs` вызывает `FightJs.process`.

### Фаза 2: Портирование интерфейса (`FightJs.java`)

Цель — добавить в HTML-страницу боя кастомные кнопки и JS-функции.

1.  **Реализовать `process` метод в `FightJs.java`**:
    *   Перенести все замены (`sb.Replace`) из `FightJs.cs`.
    *   Все вызовы `window.external.Method()` заменить на `AndroidBridge.Method()`.
2.  **Создать JS-функции**: Убедиться, что в итоговый HTML добавляются JS-функции из C#-исходника:
    *   `AutoSubmit(result)`
    *   `AutoSelect()`
    *   `AutoTurn()`
    *   `AutoBoi()`
    *   `ResetCure()`
    *   `myStartAct()`
    *   `xodtimerproc()` (таймер хода)

### Фаза 3: Создание моста Java-JS

Цель — обеспечить связь между JS и Java.

1.  **Создать `WebAppInterface.java`**:
    *   Добавить в него методы с аннотацией `@JavascriptInterface` для каждого вызова из JS:
        *   `@JavascriptInterface public void autoSelect()`
        *   `@JavascriptInterface public void autoTurn()`
        *   `@JavascriptInterface public void autoBoi()`
        *   `@JavascriptInterface public void resetCure()`
        *   `@JavascriptInterface public void resetLastBoiTimer()`
        *   `@JavascriptInterface public String getXodButtonElapsedTime()`
2.  **Подключить интерфейс к WebView**: В коде, управляющем WebView, добавить `webView.addJavascriptInterface(new WebAppInterface(context), "AndroidBridge");`.

### Фаза 4: Портирование логики Автобоя

Это самая сложная часть, требующая анализа других C#-файлов (например, `FormMainAutoBoi.cs`).

1.  **Создать `AutoboiManager.java`**:
    *   Это будет синглтон или класс, управляемый через DI.
    *   Он будет хранить состояние автобоя (`isAutoboiOn`, `isAutoTurnOn` и т.д.).
2.  **Реализовать методы, вызываемые из `WebAppInterface`**:
    *   `autoBoi()`: Будет переключать флаг полного автобоя.
    *   `autoTurn()`: Будет устанавливать флаг на совершение одного автоматического хода.
    *   `autoSelect()`: **Ключевой метод**. Должен:
        1.  Получить актуальный HTML боя (например, из `AppVars.ContentMainPhp`).
        2.  Распарсить его, чтобы понять состояние боя: кто противник, сколько у него HP, какие удары/блоки доступны.
        3.  На основе заложенных алгоритмов (например, "бить в незащищенную точку") принять решение о ходе.
        4.  Сформировать строку `result` в формате, который ожидает `AutoSubmit` (например, `vcode|enemy_id|group|...`).
        5.  Вызвать JS-функцию `AutoSubmit` в WebView, передав ей эту строку.
3.  **Реализовать отправку хода в JS**:
    *   Создать в `AutoboiManager` метод `makeMove(String result)`.
    *   Этот метод будет вызывать JavaScript в WebView:
        ```java
        // Выполняется в UI-потоке
        webView.evaluateJavascript("javascript:AutoSubmit('" + result + "');", null);
        ```

### Фаза 5: Интеграция и тестирование

*   Убедиться, что все компоненты (`FightJs.java`, `WebAppInterface.java`, `AutoboiManager.java`) корректно взаимодействуют.
*   Протестировать логику принятия решений в различных боевых ситуациях.
