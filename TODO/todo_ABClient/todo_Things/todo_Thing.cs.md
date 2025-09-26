
### 1. План портирования Thing.cs

Файл `Thing.cs` представляет собой класс данных для одного игрового предмета (вещи).

### 2. Функциональность в C#

- **Назначение:** Хранить всю информацию о предмете: имя, описание, изображение, требования и бонусы.
- **Свойства:**
    - `Img`, `Name`, `Description`: Основная информация.
    - `reqkeys`/`reqvals`, `bonkeys`/`bonvals`: Два набора параллельных массивов для хранения требований и бонусов. Это очень плохая практика, так как данные не связаны структурно.
- **Методы:** `SetReq` и `SetBon` парсят пайп-разделённую (`|`) строку и заполняют эти параллельные массивы.

### 3. Решение для портирования на Android

При портировании необходимо модернизировать структуру данных, отказавшись от параллельных массивов в пользу более подходящих коллекций, и подготовить класс для использования с базой данных Room.

- **Структура данных:** Вместо параллельных массивов следует использовать `Map<String, String>` (`Dictionary` в C#). Это свяжет ключ (например, "Сила") и значение (например, "10") в единую структуру.
- **База данных:** Так как предметов в игре много, их следует загрузить один раз при первом запуске из `abthings.xml` и сохранить в локальную базу данных Room для быстрого доступа. Класс `Thing` станет сущностью (`Entity`) для этой БД.

### 4. План реализации

1.  **Создать `data class Thing`:**
    - Объявить `data class` в Kotlin.
    - Использовать `Map<String, String>` для требований и бонусов.
    - Аннотировать класс как `@Entity` для Room.
      ```kotlin
      @Entity(tableName = "things")
      data class Thing(
          @PrimaryKey val name: String,
          val image: String,
          val description: String,
          val requirements: Map<String, String>,
          val bonuses: Map<String, String>
      )
      ```
2.  **Создать `TypeConverter` для Room:**
    - Room не может хранить `Map` напрямую. Необходимо создать `TypeConverter`, который будет преобразовывать `Map<String, String>` в строку JSON (с помощью `Gson` или `Moshi`) при записи в БД и обратно при чтении.
      ```kotlin
      class MapTypeConverter {
          @TypeConverter
          fun fromString(value: String): Map<String, String> {
              // ... Gson/Moshi logic to convert JSON string to Map
          }

          @TypeConverter
          fun fromMap(map: Map<String, String>): String {
              // ... Gson/Moshi logic to convert Map to JSON string
          }
      }
      ```
3.  **Интеграция:**
    - Этот класс будет использоваться в `ThingsRepository` (замена для `ThingsDb.cs`) и `ThingsDao` для работы с базой данных.

- [ ] Создать `data class Thing` с аннотацией `@Entity`.
- [ ] Использовать `Map<String, String>` для бонусов и требований.
- [ ] Создать `TypeConverter` для преобразования `Map` в JSON и обратно.
