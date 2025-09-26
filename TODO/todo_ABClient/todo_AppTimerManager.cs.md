# План портирования AppTimerManager.cs

Файл `AppTimerManager.cs` представляет собой статический класс для потокобезопасного управления коллекцией объектов `AppTimer`.

## Функциональность в C#

*   **Назначение**: Централизованное управление списком всех активных таймеров (запланированных событий). Обеспечивает добавление, удаление и получение списка таймеров в потокобезопасной манере.
*   **Хранение данных**: Использует `List<AppTimer>` для хранения объектов таймеров.
*   **Потокобезопасность**: Применяет `ReaderWriterLock` для синхронизации доступа к списку из разных потоков.
*   **Ключевые методы**:
    *   `AddAppTimer(AppTimer appTimer)`: Добавляет новый таймер, присваивает ему уникальный `Id` (на 1 больше максимального существующего) и вставляет его в список, сохраняя сортировку по `TriggerTime`.
    *   `SetAppTimers(IEnumerable<AppTimer> appTimers)`: Полностью заменяет текущий список таймеров новым.
    *   `GetTimers()`: Возвращает массив всех таймеров.
    *   `RemoveTimerAt(int index)`: Удаляет таймер по индексу.
    *   `RemoveTimerLastAdded()`: Находит и удаляет таймер с самым большим `Id`.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В Android-проекте нет класса, который бы управлял коллекцией таймеров.

## Решение для портирования на Android

Необходимо создать синглтон-класс `AppTimerManager.java`, который будет управлять `ArrayList` из объектов `AppTimer`. Для обеспечения потокобезопасности можно использовать `java.util.concurrent.locks.ReentrantReadWriteLock` или просто синхронизированные методы (`synchronized`), так как ожидаемая нагрузка на запись невелика.

## План реализации

- [ ] **Создать файл `AppTimerManager.java`** в пакете `ru.neverlands.abclient.manager` (или создать пакет, если его нет).

- [ ] **Реализовать паттерн Синглтон**:
    ```java
    public class AppTimerManager {
        private static final AppTimerManager instance = new AppTimerManager();
        private final List<AppTimer> timerList = new ArrayList<>();
        // ... локи или synchronized методы

        private AppTimerManager() {}

        public static AppTimerManager getInstance() {
            return instance;
        }
        // ... методы
    }
    ```

- [ ] **Портировать методы**:
    - [ ] `public synchronized void setAppTimers(List<AppTimer> appTimers)`: Очищает `timerList` и добавляет все элементы из новой коллекции.
    - [ ] `public synchronized void addAppTimer(AppTimer appTimer)`: 
        - Реализовать логику генерации нового `Id`.
        - Реализовать логику вставки `appTimer` в `timerList` с сохранением сортировки по `triggerTime`. Можно использовать `Collections.binarySearch` или ручной перебор для поиска места вставки.
    - [ ] `public synchronized List<AppTimer> getTimers()`: Возвращает копию списка, чтобы избежать `ConcurrentModificationException` при итерации извне.
    - [ ] `public synchronized void removeTimerAt(int index)`: Удаляет таймер по индексу.
    - [ ] `public synchronized void removeTimerLastAdded()`: Реализует логику поиска и удаления таймера с максимальным `Id`.

- [ ] **Обновить `todo_ABClient.md`**, отметив `AppTimerManager.cs` как проанализированный.
