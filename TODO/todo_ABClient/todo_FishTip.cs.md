# План портирования FishTip.cs

Файл `FishTip.cs` определяет класс-модель данных для "совета по рыбалке".

## Функциональность в C#

*   **Назначение**: Хранить в виде одного объекта всю информацию, касающуюся одного совета или одного места для рыбалки.
*   **Свойства (Properties)**:
    *   `Money`: `int` - Вероятно, прибыльность места.
    *   `FishUm`: `int` - Требуемое или получаемое умение рыбалки.
    *   `Location`: `string` - Название локации.
    *   `MaxBotLevel`: `int` - Максимальный уровень ботов в локации.
    *   `BotDescription`: `string` - Описание ботов.
    *   `Tip`: `string` - Текст самого совета.
*   **Интерфейс `IComparable`**: Класс реализует этот интерфейс для возможности сортировки. Метод `CompareTo` сортирует объекты по полю `Money` в порядке убывания (от самого прибыльного к наименее прибыльному).

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В Android-проекте есть поле `FishUm` в `UserConfig`, но отсутствует класс-модель `FishTip`.

## Решение для портирования на Android

Необходимо создать POJO-класс `FishTip.java`, который будет содержать все поля из C#-версии и реализовывать интерфейс `Comparable` для обеспечения сортировки.

## План реализации

- [ ] **Создать файл `FishTip.java`** в пакете `ru.neverlands.abclient.model`.

- [ ] **Определить поля класса**:
    - [ ] `public final int money;`
    - [ ] `public final int fishUm;`
    - [ ] `public final String location;`
    - [ ] `public final int maxBotLevel;`
    - [ ] `public final String botDescription;`
    - [ ] `public final String tip;`

- [ ] **Создать конструктор** для инициализации всех полей.

- [ ] **Реализовать интерфейс `Comparable<FishTip>`**:
    ```java
    @Override
    public int compareTo(FishTip other) {
        // Сортировка в порядке убывания
        return Integer.compare(other.money, this.money);
    }
    ```

- [ ] **Обновить `todo_ABClient.md`**, отметив `FishTip.cs` как проанализированный.
