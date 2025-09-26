# План портирования AppVars.cs

Файл `AppVars.cs` является статическим классом, который служит глобальным хранилищем состояния для всего приложения.

## Функциональность в C#

*   **Назначение**: Предоставляет доступ к глобальным переменным, флагам состояния, ссылкам на ключевые объекты (главная форма, профиль) и коллекциям данных из любой части приложения.
*   **Содержимое**: Содержит огромное количество статичных полей, охватывающих все аспекты приложения: состояние боя, автоматические действия (рыбалка, сбор трав), навигация, данные из парсинга страниц, состояние UI, кешированные данные и т.д.
*   **Потокобезопасность**: Сам класс не обеспечивает потокобезопасность; предполагается, что доступ к его полям синхронизируется извне, где это необходимо.

## Проверка на существующую реализацию в Android

- **Результат:** Частично реализовано. Существует файл `app/src/main/java/ru/neverlands/abclient/utils/AppVars.java`. Однако он содержит лишь небольшую часть от всех необходимых глобальных переменных. Переменные добавлялись по мере необходимости при портировании другого функционала.

## Решение для портирования на Android

Необходимо полностью дополнить существующий класс `AppVars.java`, перенеся в него все недостающие статичные поля из C#-версии. Типы данных должны быть адаптированы для Java.

## План реализации

- [ ] **Открыть `AppVars.java` и `AppVars.cs`** для сравнения.

- [ ] **Добавить все недостающие поля** в `AppVars.java`, адаптируя типы:
    - [ ] `VersionClass AppVersion` -> `public static VersionClass appVersion;` (потребуется портировать `VersionClass.cs`).
    - [ ] `Encoding Codepage` -> `public static final Charset CODEPAGE = StandardCharsets.UTF_1251;` (или аналоги).
    - [ ] `CultureInfo Culture` -> `public static final Locale CULTURE = new Locale("ru", "RU");`.
    - [ ] `WebProxy LocalProxy` -> `public static Proxy localProxy;` (использовать `java.net.Proxy`).
    - [ ] `FormMain MainForm` -> `public static MainActivity mainActivity;` (заменить ссылку на главную форму на ссылку на `MainActivity` или `Context`).
    - [ ] `DateTime` -> `public static long ...;` или `public static Date ...;`.
    - [ ] `StringCollection` -> `public static List<String> ... = new ArrayList<>();`.
    - [ ] `Dictionary<K, V>` -> `public static Map<K, V> ... = new HashMap<>();`.
    - [ ] `SortedList<K, V>` -> `public static SortedMap<K, V> ... = new TreeMap<>();`.

- [ ] **Перенести все переменные**, включая, но не ограничиваясь:
    - [ ] `FightLink`, `LastBoiLog`, `LastBoiSostav`, `LastBoiTimer` и т.д. (состояние боя).
    - [ ] `RazdelkaResultList`, `RazdelkaLevelUp`, `RazdelkaTime` (результаты разделки).
    - [ ] `LocationReal`, `LocationName` (данные о локации).
    - [ ] `AutoMoving`, `AutoMovingNextJump`, `AutoMovingDestinaton` и т.д. (состояние авто-навигации).
    - [ ] `MyCoordOld`, `MyLocOld`, `MyCharsOld`, `MyNevids`, `MyWalkers1`, `MyWalkers2` (данные для `RoomManager`).
    - [ ] `FastNeed`, `FastId`, `FastNick`, `FastCount` (флаги быстрых действий).
    - [ ] `PoisonAndWounds` (информация об отравлениях и ранениях).

- [ ] **Перенести логику из статического конструктора** `static AppVars()` в статический блок инициализации в `AppVars.java`:
    ```java
    static {
        MyCharsOld = new HashMap<>();
        MyWalkers1 = "";
        // ... и т.д.
    }
    ```

- [ ] **Обновить `todo_ABClient.md`**, отметив `AppVars.cs` как проанализированный.
