# План портирования IdleManager.cs

Файл `IdleManager.cs` - это статический класс, который работает как счетчик активных фоновых задач.

## Функциональность в C#

*   **Назначение**: Отслеживать количество одновременно выполняющихся фоновых операций (например, сетевых запросов). Это используется для двух целей: 1) показывать пользователю индикатор активности и 2) запускать ресурсоемкие задачи (такие как `ContactsManager.Pulse()`) только тогда, когда приложение находится в состоянии "простоя" (idle), то есть счетчик активных задач равен нулю.
*   **Потокобезопасность**: Использует `ReaderWriterLock` для безопасного изменения счетчика из разных потоков.
*   **Ключевые методы**:
    *   `AddActivity()`: Атомарно увеличивает счетчик и обновляет UI.
    *   `RemoveActivity()`: Атомарно уменьшает счетчик и обновляет UI. Если счетчик достигает нуля, вызывает `ContactsManager.Pulse()`.
    *   `ShowActivity()`: Вызывает метод на главной форме для обновления индикатора активности.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В Android-проекте нет централизованного счетчика фоновых задач.

## Решение для портирования на Android

Необходимо создать синглтон-класс `IdleManager.java`. Вместо `ReaderWriterLock` можно использовать `java.util.concurrent.atomic.AtomicInteger` для потокобезопасного счетчика. Логику обновления UI и вызова `ContactsManager.Pulse()` нужно будет адаптировать под Android.

## План реализации

- [ ] **Создать файл `IdleManager.java`** в пакете `ru.neverlands.abclient.manager`.

- [ ] **Реализовать класс как синглтон**:
    ```java
    public class IdleManager {
        private static final IdleManager instance = new IdleManager();
        private final AtomicInteger activeTasks = new AtomicInteger(0);

        private IdleManager() {}

        public static IdleManager getInstance() {
            return instance;
        }
        // ... методы
    }
    ```

- [ ] **Портировать методы**:
    - [ ] `public void addActivity()`:
        - Вызывает `activeTasks.incrementAndGet()`.
        - Вызывает `showActivity()`.
    - [ ] `public void removeActivity()`:
        - Вызывает `activeTasks.decrementAndGet()`.
        - Вызывает `showActivity()`.
        - Если результат `decrementAndGet` равен 0, вызывает `ContactsManager.getInstance().pulse()`.
    - [ ] `private void showActivity()`:
        - Этот метод должен будет обновить UI. Вместо прямого вызова формы, он может использовать `LiveData` или `EventBus` для отправки события в `MainActivity`, которая обновит свой индикатор активности (например, `ProgressBar`).

- [ ] **Интеграция**:
    - [ ] В коде, который выполняет фоновые сетевые запросы (например, в `ProcessAsync` из `ContactsManager` или в `RoomManager`), необходимо будет в блоках `try...finally` вызывать `IdleManager.getInstance().addActivity()` и `IdleManager.getInstance().removeActivity()`.

- [ ] **Обновить `todo_ABClient.md`**, отметив `IdleManager.cs` как проанализированный.
