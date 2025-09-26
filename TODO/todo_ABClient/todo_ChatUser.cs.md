# План портирования ChatUser.cs

Файл `ChatUser.cs` определяет простой класс-модель данных для пользователя в чате или списке игроков.

## Функциональность в C#

*   **Назначение**: Хранить основную информацию о персонаже, видимом в данный момент.
*   **Свойства (Properties)**:
    *   `Nick`: `string` - Ник персонажа.
    *   `Level`: `string` - Уровень.
    *   `Sign`: `string` - Иконка клана.
    *   `Status`: `string` - Текстовый статус (название клана и т.д.).
    *   `LastUpdated`: `DateTime` - Временная метка, когда информация была обновлена.
*   **Конструктор**: Принимает все основные данные и устанавливает `LastUpdated` на текущее время.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В Android-проекте отсутствует единый класс-модель для хранения этой информации.

## Решение для портирования на Android

Необходимо создать POJO-класс `ChatUser.java`, который будет содержать все поля из C#-версии.

## План реализации

- [ ] **Создать файл `ChatUser.java`** в пакете `ru.neverlands.abclient.model`.

- [ ] **Определить поля класса**:
    - [ ] `public final String nick;`
    - [ ] `public final String level;`
    - [ ] `public final String sign;`
    - [ ] `public final String status;`
    - [ ] `public final long lastUpdated;` (использовать `long` для `System.currentTimeMillis()`).

- [ ] **Создать конструктор**:
    ```java
    public ChatUser(String nick, String level, String sign, String status) {
        this.nick = nick;
        this.level = level;
        this.sign = "none".equalsIgnoreCase(sign) ? "" : sign;
        this.status = status;
        this.lastUpdated = System.currentTimeMillis();
    }
    ```

- [ ] **Обновить `todo_ABClient.md`**, отметив `ChatUser.cs` как проанализированный.
