# План портирования HelperConverters.cs

Файл `HelperConverters.cs` - это статический класс с набором утилит для преобразования данных и форматирования строк.

## Функциональность в C#

*   **Назначение**: Предоставить набор разрозненных, но полезных методов-конвертеров.
*   **Активные методы**:
    *   `TimeIntervalToNow(long tick)`: Форматирует интервал времени от заданной метки до текущего момента.
    *   `TimeSpanToString(TimeSpan ts)`: Форматирует объект `TimeSpan`.
    *   `NickEncode(string nick)` / `NickDecode(string nick)`: Кодирует/декодирует ники для URL.
    *   `AddressEncode(string address)`: Кодирует только часть URL-адреса после `pinfo.cgi?`.
    *   `MinsToStr(int mins)`: Форматирует минуты в строку `(Ч:ММ:СС)`.
*   **Закомментированные методы**: Содержит также закомментированный код для преобразования байтов в hex и парсинга чисел.

## Проверка на существующую реализацию в Android

- **Результат:** Частично реализовано.
- **Объяснение**:
    1.  В проекте существует класс `app/src/main/java/ru/neverlands/abclient/utils/ConverterUtils.java`.
    2.  В нем уже реализованы `TimeSpanToString`, `MinsToStr`, `NickEncode`, `NickDecode`.
    3.  Метод `AddressEncode` реализован не полностью (отсутствует проверка на `Resources.AddressPName`).
    4.  Метод `TimeIntervalToNow` полностью отсутствует.

## Решение для портирования на Android

Необходимо дополнить существующий класс `ConverterUtils.java` недостающими методами и недостающей логикой.

## План реализации

- [ ] **Открыть `ConverterUtils.java`**.

- [ ] **Добавить метод `timeIntervalToNow(long ticks)`**:
    - [ ] Метод должен принимать `long` (представляющий `DateTime.Ticks` из C#).
    - [ ] Так как `DateTime.Ticks` и `System.currentTimeMillis()` имеют разную точку отсчета и разрешение, прямая конвертация невозможна без получения точной разницы. Однако, если предположить, что на вход будет подаваться `System.currentTimeMillis()`, то логика будет следующей:
        ```java
        public static String timeIntervalToNow(long startTimeMillis) {
            long elapsed = System.currentTimeMillis() - startTimeMillis;
            long days = TimeUnit.MILLISECONDS.toDays(elapsed);
            long hours = TimeUnit.MILLISECONDS.toHours(elapsed) % 24;
            long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60;

            StringBuilder sb = new StringBuilder();
            if (days > 0) {
                sb.append(days).append("д ");
            }
            if (hours > 0) {
                sb.append(hours).append("ч ");
            }
            sb.append(minutes).append("мин");
            return sb.toString();
        }
        ```

- [ ] **Дополнить метод `addressEncode(String address)`**:
    - [ ] Добавить проверку на `Resources.AddressPName` (потребуется портировать эту константу в `AppConsts.java`).

- [ ] **Обновить `todo_Helpers.md`**, отметив `HelperConverters.cs` как проанализированный.
