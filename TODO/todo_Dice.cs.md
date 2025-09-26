# План портирования Dice.cs

Файл `Dice.cs` — это простая статическая утилита для генерации случайных чисел.

## Функциональность в C#

Класс является оберткой над системным `System.Random` и предоставляет два метода:

*   `Make(int max)`: Возвращает случайное число в диапазоне `[0, max)`.
*   `Make(int min, int max)`: Возвращает случайное число в диапазоне `[min, max)`.

## План портирования на Android

Это тривиальный класс, который легко портируется на Java с использованием `java.util.Random`.

1.  **Создать `Dice.java`** в пакете `ru.neverlands.abclient.utils`.
2.  **Реализовать класс**:
    ```java
    import java.util.Random;

    public final class Dice {
        private static final Random RAND = new Random();

        // Приватный конструктор, чтобы предотвратить создание экземпляров
        private Dice() { }

        /**
         * Возвращает случайное целое число от 0 (включительно) до max (не включая).
         */
        public static int make(int max) {
            return RAND.nextInt(max);
        }

        /**
         * Возвращает случайное целое число от min (включительно) до max (не включая).
         * Примечание: В отличие от C#, в Java Random.nextInt(min, max) появился только в API 33.
         * Для совместимости с более старыми версиями нужно использовать другую формулу.
         */
        public static int make(int min, int max) {
            if (min >= max) {
                throw new IllegalArgumentException("max must be greater than min");
            }
            return RAND.nextInt((max - min)) + min;
        }
    }
    ```

3.  **Использование**: Везде в портированном коде, где был вызов `Dice.Make(...)`, он будет заменен на `Dice.make(...)`.
