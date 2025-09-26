# План портирования ListItemBotLevelEx.cs

Файл `ListItemBotLevelEx.cs` определяет простой класс-модель для элемента списка, представляющего фильтр по уровню бота.

## Функциональность в C#

*   **Назначение**: Служит в качестве объекта-обертки для элементов, которые должны отображаться в `ComboBox` или `ListBox`. Он хранит как само значение (уровень), так и его текстовое представление для UI.
*   **Свойства (Properties)**:
    *   `BotLevelValue`: `int` - Числовое значение уровня.
    *   `BotLevel`: `string` - Отформатированная строка для отображения, например, `"[10] и слабее"`.
*   **Конструктор**: Принимает `int` и на его основе создает отформатированную строку.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В Android-проекте отсутствует такой класс.

## Решение для портирования на Android

Необходимо создать POJO-класс `ListItemBotLevelEx.java`. Этот класс будет использоваться в `ArrayAdapter` для кастомного отображения элементов в `Spinner`.

## План реализации

- [ ] **Создать файл `ListItemBotLevelEx.java`** в пакете `ru.neverlands.abclient.model`.

- [ ] **Определить поля класса**:
    - [ ] `public final int botLevelValue;`
    - [ ] `public final String botLevel;`

- [ ] **Создать конструктор**:
    ```java
    public ListItemBotLevelEx(int levelValue) {
        this.botLevelValue = levelValue;
        this.botLevel = String.format(Locale.US, "[%d] и слабее", levelValue);
    }
    ```

- [ ] **Переопределить метод `toString()`**:
    - [ ] Чтобы `ArrayAdapter` по умолчанию отображал правильный текст, нужно переопределить `toString()`.
    ```java
    @Override
    public String toString() {
        return this.botLevel;
    }
    ```

- [ ] **Обновить `todo_ABClient.md`**, отметив `ListItemBotLevelEx.cs` как проанализированный.
