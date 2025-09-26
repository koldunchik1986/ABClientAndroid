# План портирования Favorites.cs

Файл `Favorites.cs` - это статический класс, который при старте приложения загружает список закладок (избранного) из XML-файла.

## Функциональность в C#

*   **Назначение**: Однократно при запуске прочитать файл `abfavorites.xml` и заполнить статическую коллекцию `List<Bookmark>` данными из него.
*   **Логика**: Вся логика находится в статическом конструкторе, что гарантирует ее выполнение только один раз.
    1.  Проверяется наличие `abfavorites.xml`.
    2.  Файл читается и парсится как XML.
    3.  Для каждого тега `<favorite>` создается объект `Bookmark`.
    4.  Атрибуты `title`, `url`, `icon` извлекаются и присваиваются свойствам объекта `Bookmark`.
    5.  Производится небольшая обработка данных (добавление `http://` к URL).
    6.  Иконка загружается из файла, указанного в атрибуте `icon`.
    7.  Готовый объект `Bookmark` добавляется в статическую `List<Bookmark> Bookmarks`.

## Проверка на существующую реализацию в Android

- **Результат:** Частично реализовано. Файл `abfavorites.xml` копируется в `assets` при первом запуске приложения (в `DataManager.java`). Однако, отсутствует класс и логика для загрузки, парсинга и использования этих данных.

## Решение для портирования на Android

Необходимо создать синглтон-класс `FavoritesManager.java`, который будет загружать и парсить `abfavorites.xml` из `assets` и предоставлять доступ к списку объектов `Bookmark`.

## План реализации

- [ ] **Создать файл `FavoritesManager.java`** в пакете `ru.neverlands.abclient.manager`.

- [ ] **Реализовать класс как синглтон**:
    ```java
    public class FavoritesManager {
        private static final FavoritesManager instance = new FavoritesManager();
        private final List<Bookmark> bookmarks = new ArrayList<>();

        private FavoritesManager() {
            // В приватном конструкторе вызвать loadFavorites()
        }

        public static FavoritesManager getInstance() {
            return instance;
        }

        public List<Bookmark> getBookmarks() {
            return bookmarks;
        }
        
        private void loadFavorites() { ... }
    }
    ```

- [ ] **Портировать логику загрузки (`loadFavorites`)**:
    - [ ] Метод должен будет прочитать файл `abfavorites.xml` из внутреннего хранилища приложения (куда он был скопирован `DataManager`).
    - [ ] Для парсинга XML использовать `XmlPullParser`.
    - [ ] В цикле парсинга для каждого тега `<favorite>` создавать новый объект `Bookmark` (который тоже нужно портировать).
    - [ ] Извлекать атрибуты `title`, `url`, `icon`.
    - [ ] Логику загрузки иконки `Image.FromFile` нужно будет заменить на получение ID ресурса из `R.drawable` по имени файла, указанному в атрибуте `icon`.
    - [ ] Добавлять созданные объекты `Bookmark` в список `bookmarks`.

- [ ] **Вызвать `FavoritesManager.getInstance()`** на раннем этапе старта приложения (например, в `ABClientApplication.java`), чтобы инициировать загрузку.

- [ ] **Обновить `todo_ABClient.md`**, отметив `Favorites.cs` как проанализированный.
