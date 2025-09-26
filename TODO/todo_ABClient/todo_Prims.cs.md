# План портирования Prims.cs

Файл `Prims.cs` определяет перечисление (`enum`) с атрибутом `[Flags]`, которое используется как битовое поле для набора наживок.

## Функциональность в C#

*   **Назначение**: Представляет собой набор битовых флагов, где каждый флаг соответствует определенному типу наживки для рыбалки.
*   **Атрибут `[Flags]`**: Позволяет комбинировать несколько значений с помощью побитовых операций (например, `Prims.Bread | Prims.Worm`).
*   **Члены**: Каждому члену `enum` присвоено значение, являющееся степенью двойки (1, 2, 4, 8, ...), что необходимо для корректной работы с битовыми флагами.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В Android-проекте нет аналога этого перечисления.

## Решение для портирования на Android

В Java нет прямого аналога `enum` с атрибутом `[Flags]`. Идиоматичным способом реализации такого функционала является создание класса с набором целочисленных констант (`public static final int`). Для удобства работы с таким набором флагов в Android часто используют аннотацию `@IntDef`.

## План реализации

- [ ] **Создать файл `Prims.java`** в пакете `ru.neverlands.abclient.enums` (или `model`).

- [ ] **Определить константы и аннотацию `@IntDef`**:
    ```java
    import androidx.annotation.IntDef;
    import java.lang.annotation.Retention;
    import java.lang.annotation.RetentionPolicy;

    public final class Prims {

        @Retention(RetentionPolicy.SOURCE)
        @IntDef(flag = true, value = {
                BREAD,
                WORM,
                BIG_WORM,
                STINK,
                FLY,
                LIGHT,
                DONKA,
                MORM,
                HI_FLIGHT
        })
        public @interface PrimFlags {}

        public static final int BREAD = 1;      // 0x01
        public static final int WORM = 2;       // 0x02
        public static final int BIG_WORM = 4;   // 0x04
        public static final int STINK = 8;      // 0x08
        public static final int FLY = 16;     // 0x10
        public static final int LIGHT = 32;     // 0x20
        public static final int DONKA = 64;     // 0x40
        public static final int MORM = 128;     // 0x80
        public static final int HI_FLIGHT = 256; // 0x100

        private Prims() {}
    }
    ```
    *Использование `@IntDef` позволяет статическому анализатору Android Studio проверять корректность использования флагов, эмулируя строгую типизацию `enum`.*

- [ ] **Обновить `todo_ABClient.md`**, отметив `Prims.cs` как проанализированный.
