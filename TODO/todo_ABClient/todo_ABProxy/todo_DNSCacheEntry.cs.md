
### 1. План портирования DNSCacheEntry.cs

Файл `DNSCacheEntry.cs` представляет собой простую структуру данных для хранения одной записи в DNS-кэше.

### 2. Функциональность в C#

- **Назначение:** Хранить результат DNS-запроса (`IPHostEntry`) и время, когда этот запрос был сделан.
- **Логика:**
    - В конструкторе принимает `IPHostEntry` и сохраняет его.
    - В конструкторе также сохраняет текущее время (`Environment.TickCount`) в свойство `LastLookup`.
    - Предоставляет публичные свойства для доступа к `IPHostEntry` и `LastLookup`.

### 3. Решение для портирования на Android

Это тривиальный класс. Его можно легко реализовать на Java/Kotlin.

### 4. План реализации

- **Kotlin:** Создать `data class DNSCacheEntry(val addresses: Array<InetAddress>, val lastLookup: Long)`.
- **Java:** Создать простой POJO-класс `DNSCacheEntry` с двумя полями (`InetAddress[]` и `long`) и конструктором.

- [ ] Создать data-класс `DNSCacheEntry`.
