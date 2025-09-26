# План портирования UnhandledExceptionManager.cs

Файл `UnhandledExceptionManager.cs` - это статический класс для установки глобального обработчика необработанных исключений.

## Функциональность в C#

*   **Назначение**: Перехватывать все исключения, которые не были пойманы в `try-catch` блоках, чтобы предотвратить стандартный креш приложения Windows. Вместо этого показывается кастомная форма с информацией об ошибке, после чего приложение принудительно завершается.
*   **Реализация**: Подписывается на события `Application.ThreadException` (для UI потока) и `AppDomain.CurrentDomain.UnhandledException` (для всех остальных потоков).
*   **Обработка**: В обработчике `GenericExceptionHandler` собирается информация об исключении, показывается форма `FormAutoTrap`, и затем вызывается `Process.GetCurrentProcess().Kill()`.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В Android-проекте нет глобального обработчика исключений.

## Решение для портирования на Android

Необходимо создать класс, который реализует `Thread.UncaughtExceptionHandler`. Экземпляр этого класса затем устанавливается как обработчик по умолчанию с помощью `Thread.setDefaultUncaughtExceptionHandler`. Это нужно сделать как можно раньше, в `onCreate` метода класса `Application`.

## План реализации

- [ ] **Создать файл `MyExceptionHandler.java`** в пакете `ru.neverlands.abclient.utils`.
    - [ ] Класс должен реализовывать `Thread.UncaughtExceptionHandler`.
    - [ ] В конструкторе он должен сохранять оригинальный обработчик по умолчанию: `this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();`.
    - [ ] Реализовать метод `uncaughtException(Thread t, Throwable e)`:
        1.  Собрать информацию об устройстве (`Build.MODEL`, `Build.VERSION.RELEASE`) и об ошибке (`Log.getStackTraceString(e)`).
        2.  Записать эту информацию в лог-файл с помощью `AppLogger`.
        3.  **Показать диалог с ошибкой**. Это может быть сложно сделать из глобального обработчика. Надежный способ - запустить новую `Activity`, которая выглядит как диалог и отображает информацию об ошибке. В `Intent` для этой `Activity` нужно добавить флаги `FLAG_ACTIVITY_NEW_TASK` и `FLAG_ACTIVITY_CLEAR_TASK`.
        4.  После показа диалога, можно либо передать управление стандартному обработчику (`defaultUEH.uncaughtException(t, e)`), либо принудительно завершить процесс: `android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10);`.

- [ ] **Создать `ErrorActivity.java`**:
    - [ ] Создать `Activity`, которая будет выглядеть как диалог и отображать текст ошибки, переданный через `Intent`.

- [ ] **Интеграция в `ABClientApplication.java`**:
    - [ ] В методе `onCreate`, добавить строку:
        ```java
        Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler(this));
        ```

- [ ] **Обновить `todo_ABClient.md`**, отметив `UnhandledExceptionManager.cs` как проанализированный.
