# План портирования ChatUsersManager.cs

Файл `ChatUsersManager.cs` представляет собой статический класс для управления кэшем данных о пользователях чата.

## Функциональность в C#

*   **Назначение**: Хранить и управлять локальным кэшем информации о пользователях (`ChatUser`), чтобы минимизировать сетевые запросы за информацией о персонажах. Обеспечивает сохранение и загрузку этого кэша на диск.
*   **Хранение данных**: Использует `SortedDictionary<string, ChatUser>` для хранения данных в памяти, где ключ - ник в нижнем регистре.
*   **Потокобезопасность**: Использует `ReaderWriterLock` для синхронизации доступа к словарю.
*   **Ключевые методы**:
    *   `AddUser(ChatUser chatUser)`: Добавляет или обновляет пользователя в кэше.
    *   `Exists(string userNick)`: Проверяет наличие пользователя в кэше. Если его нет, пытается загрузить информацию через `NeverApi.GetAll()` и добавить в кэш.
    *   `GetUserData(string userNick)`: Получает данные пользователя из кэша.
    *   `Save()`: Сериализует всю коллекцию пользователей в `chatusers.xml`.
    *   `Load()`: Загружает и парсит `chatusers.xml`, отбрасывая записи старше одного дня.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В Android-проекте нет класса, который бы управлял кэшем пользователей чата.

## Решение для портирования на Android

Необходимо создать синглтон-класс `ChatUsersManager.java`, который будет управлять `Map<String, ChatUser>`. Вместо XML-сериализации для сохранения кэша на диск лучше использовать JSON с библиотекой `Gson`, которая уже есть в проекте.

## План реализации

- [ ] **Создать файл `ChatUsersManager.java`** в пакете `ru.neverlands.abclient.manager`.

- [ ] **Реализовать паттерн Синглтон**:
    ```java
    public class ChatUsersManager {
        private static final ChatUsersManager instance = new ChatUsersManager();
        private final Map<String, ChatUser> userMap = new ConcurrentHashMap<>(); // Используем ConcurrentHashMap для потокобезопасности

        private ChatUsersManager() {}

        public static ChatUsersManager getInstance() {
            return instance;
        }
        // ... методы
    }
    ```

- [ ] **Портировать методы**:
    - [ ] `public void addUser(ChatUser chatUser)`: Добавляет/обновляет пользователя в `userMap`.
    - [ ] `public boolean exists(String userNick)`: Проверяет наличие в `userMap`. Логику с `NeverApi.GetAll()` нужно будет портировать отдельно, включая сам `NeverApi`.
    - [ ] `public ChatUser getUserData(String userNick)`: Получает пользователя из `userMap`.

- [ ] **Реализовать `save()` и `load()` с использованием JSON**:
    - [ ] `public void save(Context context)`:
        - Создать объект `Gson`.
        - Сериализовать `userMap` в JSON-строку: `String json = gson.toJson(userMap);`.
        - Сохранить JSON-строку в `SharedPreferences` или в файл во внутреннем хранилище приложения.
    - [ ] `public void load(Context context)`:
        - Загрузить JSON-строку из хранилища.
        - Десериализовать JSON обратно в `Map<String, ChatUser>`: `Type type = new TypeToken<ConcurrentHashMap<String, ChatUser>>() {}.getType(); userMap = gson.fromJson(json, type);`.
        - Реализовать логику удаления устаревших записей (старше 1 дня).

- [ ] **Обновить `todo_ABClient.md`**, отметив `ChatUsersManager.cs` как проанализированный.
