# План портирования AutoAnswerMachine.cs

Файл `AutoAnswerMachine.cs` реализует статический класс-автоответчик, который выдает предварительно заданные ответы в случайном циклическом порядке.

## Функциональность в C#

*   **Назначение**: Предоставлять по одному ответу из заранее заданного списка при каждом вызове, при этом порядок ответов должен быть случайным, и они не должны повторяться, пока не будут использованы все.
*   **Методы**:
    *   `SetAnswers(string answers)`: Принимает одну большую строку, в которой ответы разделены `[BR]`. Метод парсит эту строку в массив `string[] arrayAnswers`.
    *   `GetNextAnswer()`: Основной метод. При первом вызове он создает "перемешанную карту индексов" (`prepAutoAnswers`) и затем последовательно проходит по этой карте, возвращая ответы из `arrayAnswers` в случайном порядке. Когда все ответы были возвращены, цикл начинается заново.
*   **Рандомизация**: Для перемешивания используется `Helpers.Dice.Make`.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В Android-проекте нет класса с подобной логикой.

## Решение для портирования на Android

Необходимо создать синглтон или статический класс `AutoAnswerMachine.java`, который будет повторять логику C#-версии. Для перемешивания можно использовать `java.util.Collections.shuffle()`.

## План реализации

- [ ] **Создать файл `AutoAnswerMachine.java`** в подходящем пакете (например, `ru.neverlands.abclient.manager`).

- [ ] **Реализовать класс как синглтон или статический**:
    ```java
    public class AutoAnswerMachine {
        private static final AutoAnswerMachine instance = new AutoAnswerMachine();
        private String[] arrayAnswers;
        private List<Integer> shuffledIndexes;
        private int lastAutoAnswer = -1;

        private AutoAnswerMachine() {}

        public static AutoAnswerMachine getInstance() {
            return instance;
        }
        // ... методы
    }
    ```

- [ ] **Портировать метод `setAnswers(String answers)`**:
    - [ ] Метод должен принимать строку и разбивать ее по `AppConsts.BR` (эту константу также нужно будет портировать).
    - [ ] При установке нового списка ответов необходимо сбрасывать `shuffledIndexes` и `lastAutoAnswer`, чтобы при следующем вызове `getNextAnswer` перемешивание произошло заново.

- [ ] **Портировать метод `getNextAnswer()`**:
    - [ ] Проверять, что `arrayAnswers` не пуст.
    - [ ] Если `shuffledIndexes` не инициализирован, создать `ArrayList<Integer>`, заполнить его числами от `0` до `arrayAnswers.length - 1` и перемешать с помощью `Collections.shuffle()`.
    - [ ] Увеличивать `lastAutoAnswer` и сбрасывать его в `0`, если достигнут конец списка.
    - [ ] Возвращать `arrayAnswers[shuffledIndexes.get(lastAutoAnswer)]`.

- [ ] **Портировать зависимость `Dice.Make`**:
    - [ ] Так как мы используем `Collections.shuffle()`, прямая зависимость от `Dice.Make` не требуется, что упрощает портирование.

- [ ] **Обновить `todo_ABClient.md`**, отметив `AutoAnswerMachine.cs` как проанализированный.
