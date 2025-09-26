
### 1. План портирования Map.cs

Файл `Map.cs` - это огромный статический класс-монолит, который является центральным узлом для всей функциональности, связанной с картой. Он управляет загрузкой, хранением, отображением и утилитарными функциями карты.

### 2. Функциональность в C#

- **Хранение данных:** Содержит несколько статических `SortedDictionary` для хранения всех данных карты:
    - `Location`, `InvLocation`: Соответствие между координатами и строковыми ID регионов.
    - `Cells`: Детальная информация о каждой ячейке (из `map.xml`).
    - `AbcCells`: Дополнительная информация-справочник о ячейках (из `abcells.xml`).
    - `Teleports`: Список телепортов.
- **Инициализация:** В статическом конструкторе происходит вся магия:
    - Определяются границы регионов мира (хардкод).
    - Вызываются `LoadMap`, `LoadAbcMap`, `LoadTeleports` для загрузки данных из XML-файлов.
    - `CompareMaps` синхронизирует данные из `map.xml` и `abcells.xml`.
- **Рендеринг карты:** Методы `ShowMiniMap`, `ShowMap`, `AddCell` генерируют сложный HTML-код для отображения мини-карты, включая подсветку пути, информацию о ячейках, цвета в зависимости от стоимости и т.д.
- **Сохранение данных:** `SaveAbcMap` сохраняет пользовательские данные о карте обратно в `abcells.xml`.
- **Утилиты:** Множество вспомогательных методов для форматирования, расчетов и навигации.

### 3. Решение для портирования на Android

Прямое портирование этого класса невозможно и нецелесообразно. Требуется полный редизайн с разделением ответственности.

1.  **Хранение данных:** Вместо парсинга XML при каждом запуске, данные следует перенести в базу данных.
    - **Решение:** Использовать **Room Persistence Library**.
    - При первом запуске приложения, XML-файлы (`map.xml`, `abcells.xml`), включенные в `assets`, парсятся и их содержимое сохраняется в таблицы базы данных Room (таблицы для `Cell`, `AbcCell`, `Bot` и т.д.).
    - Создается **`MapRepository`**, который будет предоставлять `DAO` (Data Access Objects) для доступа к этим данным. Все обращения к карте в коде будут идти через этот репозиторий.

2.  **Рендеринг карты:** Генерация HTML должна быть заменена нативным UI.
    - **Решение:** Создать кастомную `View`, унаследованную от `View` или `SurfaceView`.
    - В методе `onDraw` этой `View` будет происходить отрисовка карты на `Canvas`. Ячейки, иконки, текст, пути - все будет рисоваться программно. Это обеспечит высокую производительность и отзывчивость.
    - Альтернатива - `RecyclerView` с `GridLayoutManager`, но это может быть менее гибко для интерактивной карты.

### 4. План реализации

### 4. План реализации

Поскольку в коде Android отсутствует какая-либо реализация загрузки и обработки данных карты, этот план описывает создание системы с нуля.

1.  **Создать сущности (Entities) для Room:**
    - Создать data-классы в Kotlin, которые будут представлять таблицы в базе данных. Например:
      ```kotlin
      @Entity(tableName = "cells")
      data class CellEntity(
          @PrimaryKey val cellNumber: String,
          val name: String,
          val tooltip: String,
          val cost: Int,
          val hasFish: Boolean,
          val hasWater: Boolean,
          val herbGroup: String
          // ... другие поля
      )

      @Entity(tableName = "bots")
      data class BotEntity(
          @PrimaryKey(autoGenerate = true) val id: Int = 0,
          val cellOwnerNumber: String, // Внешний ключ на CellEntity
          val name: String,
          val minLevel: Int,
          val maxLevel: Int
      )
      ```

2.  **Создать DAO (Data Access Object):**
    - Создать интерфейсы с SQL-запросами для доступа к данным.
      ```kotlin
      @Dao
      interface CellDao {
          @Query("SELECT * FROM cells WHERE cellNumber = :cellNumber")
          suspend fun getCell(cellNumber: String): CellEntity?

          @Query("SELECT * FROM bots WHERE cellOwnerNumber = :cellNumber")
          suspend fun getBotsForCell(cellNumber: String): List<BotEntity>

          @Insert(onConflict = OnConflictStrategy.REPLACE)
          suspend fun insertAllCells(cells: List<CellEntity>)

          @Insert(onConflict = OnConflictStrategy.REPLACE)
          suspend fun insertAllBots(bots: List<BotEntity>)
      }
      ```

3.  **Создать класс `MapDatabase`:**
    - Абстрактный класс, наследуемый от `RoomDatabase`, который связывает все Entities и DAO.

4.  **Реализовать парсинг и миграцию данных:**
    - Создать класс `XmlMapParser`.
    - В нем реализовать методы для парсинга `map.xml` и `abcells.xml` (можно использовать `XmlPullParser`).
    - Создать `DataManager`, который при первом запуске приложения проверит, заполнена ли база данных. Если нет, он вызовет `XmlMapParser` и сохранит данные в БД через соответствующие DAO. Этот процесс должен выполниться только один раз.

5.  **Создать `MapRepository`:**
    - Класс-синглтон, который будет единственной точкой доступа к данным карты для всего приложения. Он будет скрывать за собой, откуда берутся данные (из БД).
      ```kotlin
      class MapRepository(private val cellDao: CellDao) {
          suspend fun getCellWithBots(cellNumber: String): FullCellInfo? {
              val cell = cellDao.getCell(cellNumber) ?: return null
              val bots = cellDao.getBotsForCell(cellNumber)
              return FullCellInfo(cell, bots)
          }
      }
      ```

6.  **Создать кастомную `MapView`:**
    - Реализовать логику отрисовки карты на `Canvas`, получая данные из `MapRepository` через `ViewModel`.

- [ ] Определить полную структуру таблиц и связей для БД Room.
- [ ] Реализовать `XmlMapParser` для `map.xml` и `abcells.xml`.
- [ ] Реализовать `DataManager` для однократной миграции данных в БД.
- [ ] Реализовать `MapRepository` и `MapViewModel` для предоставления данных в UI.
- [ ] Создать кастомную `MapView` для нативной отрисовки карты.
