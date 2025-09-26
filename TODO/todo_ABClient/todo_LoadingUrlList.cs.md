# План портирования LoadingUrlList.cs

Файл `LoadingUrlList.cs` - это статический класс для управления списком активных (загружаемых в данный момент) URL.

## Функциональность в C#

*   **Назначение**: Хранить потокобезопасный список URL, которые в данный момент загружаются. Это используется для отображения информации о сетевой активности в строке состояния главного окна.
*   **Хранение данных**: Использует `Hashtable` для хранения URL в качестве ключей. `Hashtable` является потокобезопасной коллекцией в .NET Framework.
*   **Ключевые методы**:
    *   `Add(string address)`: Добавляет URL в хэш-таблицу.
    *   `Remove(string address)`: Удаляет URL из хэш-таблицы и возвращает один из оставшихся в списке URL (если список не пуст). Это позволяет строке состояния сразу показать следующую загрузку.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В Android-проекте нет класса для отслеживания активных загрузок.

## Решение для портирования на Android

Необходимо создать синглтон-класс `LoadingUrlList.java`. Вместо `Hashtable` лучше использовать более современную потокобезопасную коллекцию, например, `java.util.concurrent.ConcurrentHashMap` или `Collections.synchronizedSet(new HashSet<>())`.

## План реализации

- [ ] **Создать файл `LoadingUrlList.java`** в пакете `ru.neverlands.abclient.manager`.

- [ ] **Реализовать класс как синглтон**:
    ```java
    public class LoadingUrlList {
        private static final LoadingUrlList instance = new LoadingUrlList();
        private final Set<String> urlSet = Collections.synchronizedSet(new HashSet<>());

        private LoadingUrlList() {}

        public static LoadingUrlList getInstance() {
            return instance;
        }
        // ... методы
    }
    ```

- [ ] **Портировать методы**:
    - [ ] `public void add(String address)`: Добавляет URL в `urlSet`.
    - [ ] `public String remove(String address)`: 
        - Удаляет URL из `urlSet`.
        - Если `urlSet` не пуст, возвращает первый попавшийся элемент. Можно использовать `urlSet.iterator().next()`.
        - Если пуст, возвращает `null` или пустую строку.

- [ ] **Интеграция**:
    - [ ] В коде, который выполняет сетевые запросы (например, в `OkHttp` interceptor или в `WebViewClient`), нужно будет вызывать `LoadingUrlList.getInstance().add(url)` перед началом запроса и `LoadingUrlList.getInstance().remove(url)` после его завершения.
    - [ ] UI (например, `MainActivity`) сможет обращаться к этому менеджеру, чтобы получить список активных загрузок и отобразить их.

- [ ] **Обновить `todo_ABClient.md`**, отметив `LoadingUrlList.cs` как проанализированный.
