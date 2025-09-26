# План портирования TSound.cs

Файл `TSound.cs` определяет простую структуру для хранения настроек звука.

## Функциональность в C#

*   **Назначение**: Хранить набор булевых флагов, которые включают или отключают воспроизведение различных звуковых событий в приложении.
*   **Реализация**: Является структурой (`struct`).
*   **Свойства**:
    *   `Enabled`: Главный переключатель звука.
    *   `DoPlayDigits`: Звук для цифр (вероятно, капча).
    *   `DoPlayAttack`: Звук атаки.
    *   `DoPlaySndMsg`: Звук нового сообщения.
    *   `DoPlayRefresh`: Звук обновления.
    *   `DoPlayAlarm`: Звук тревоги.
    *   `DoPlayTimer`: Звук таймера.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована.

## Решение для портирования на Android

Необходимо создать POJO-класс `TSound.java` и включить его в `UserConfig.java`.

## План реализации

- [ ] **Создать файл `TSound.java`** в пакете `ru.neverlands.abclient.model`.

- [ ] **Определить поля класса**:
    - [ ] `public boolean enabled;`
    - [ ] `public boolean doPlayDigits;`
    - [ ] `public boolean doPlayAttack;`
    - [ ] `public boolean doPlaySndMsg;`
    - [ ] `public boolean doPlayRefresh;`
    - [ ] `public boolean doPlayAlarm;`
    - [ ] `public boolean doPlayTimer;`

- [ ] **Создать конструктор**.

- [ ] **Интегрировать в `UserConfig.java`**:
    - [ ] Добавить новое поле: `public TSound sound = new TSound();`.

- [ ] **Обновить `todo_MyProfile.md`**, отметив `TSound.cs` как проанализированный.
